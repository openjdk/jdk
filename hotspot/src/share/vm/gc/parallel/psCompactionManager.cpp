/*
 * Copyright (c) 2005, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

#include "precompiled.hpp"
#include "classfile/systemDictionary.hpp"
#include "gc/parallel/gcTaskManager.hpp"
#include "gc/parallel/objectStartArray.hpp"
#include "gc/parallel/parMarkBitMap.hpp"
#include "gc/parallel/parallelScavengeHeap.hpp"
#include "gc/parallel/psCompactionManager.inline.hpp"
#include "gc/parallel/psOldGen.hpp"
#include "gc/parallel/psParallelCompact.inline.hpp"
#include "gc/shared/taskqueue.inline.hpp"
#include "logging/log.hpp"
#include "memory/iterator.inline.hpp"
#include "oops/instanceKlass.inline.hpp"
#include "oops/instanceMirrorKlass.inline.hpp"
#include "oops/objArrayKlass.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.inline.hpp"

PSOldGen*            ParCompactionManager::_old_gen = NULL;
ParCompactionManager**  ParCompactionManager::_manager_array = NULL;

OopTaskQueueSet*     ParCompactionManager::_stack_array = NULL;
ParCompactionManager::ObjArrayTaskQueueSet*
  ParCompactionManager::_objarray_queues = NULL;
ObjectStartArray*    ParCompactionManager::_start_array = NULL;
ParMarkBitMap*       ParCompactionManager::_mark_bitmap = NULL;
RegionTaskQueueSet*  ParCompactionManager::_region_array = NULL;

ParCompactionManager::ParCompactionManager() :
    _action(CopyAndUpdate) {

  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();

  _old_gen = heap->old_gen();
  _start_array = old_gen()->start_array();

  marking_stack()->initialize();
  _objarray_stack.initialize();
  _region_stack.initialize();

  reset_bitmap_query_cache();
}

void ParCompactionManager::initialize(ParMarkBitMap* mbm) {
  assert(PSParallelCompact::gc_task_manager() != NULL,
    "Needed for initialization");

  _mark_bitmap = mbm;

  uint parallel_gc_threads = PSParallelCompact::gc_task_manager()->workers();

  assert(_manager_array == NULL, "Attempt to initialize twice");
  _manager_array = NEW_C_HEAP_ARRAY(ParCompactionManager*, parallel_gc_threads+1, mtGC);
  guarantee(_manager_array != NULL, "Could not allocate manager_array");

  _stack_array = new OopTaskQueueSet(parallel_gc_threads);
  guarantee(_stack_array != NULL, "Could not allocate stack_array");
  _objarray_queues = new ObjArrayTaskQueueSet(parallel_gc_threads);
  guarantee(_objarray_queues != NULL, "Could not allocate objarray_queues");
  _region_array = new RegionTaskQueueSet(parallel_gc_threads);
  guarantee(_region_array != NULL, "Could not allocate region_array");

  // Create and register the ParCompactionManager(s) for the worker threads.
  for(uint i=0; i<parallel_gc_threads; i++) {
    _manager_array[i] = new ParCompactionManager();
    guarantee(_manager_array[i] != NULL, "Could not create ParCompactionManager");
    stack_array()->register_queue(i, _manager_array[i]->marking_stack());
    _objarray_queues->register_queue(i, &_manager_array[i]->_objarray_stack);
    region_array()->register_queue(i, _manager_array[i]->region_stack());
  }

  // The VMThread gets its own ParCompactionManager, which is not available
  // for work stealing.
  _manager_array[parallel_gc_threads] = new ParCompactionManager();
  guarantee(_manager_array[parallel_gc_threads] != NULL,
    "Could not create ParCompactionManager");
  assert(PSParallelCompact::gc_task_manager()->workers() != 0,
    "Not initialized?");
}

void ParCompactionManager::reset_all_bitmap_query_caches() {
  uint parallel_gc_threads = PSParallelCompact::gc_task_manager()->workers();
  for (uint i=0; i<=parallel_gc_threads; i++) {
    _manager_array[i]->reset_bitmap_query_cache();
  }
}

bool ParCompactionManager::should_update() {
  assert(action() != NotValid, "Action is not set");
  return (action() == ParCompactionManager::Update) ||
         (action() == ParCompactionManager::CopyAndUpdate) ||
         (action() == ParCompactionManager::UpdateAndCopy);
}

bool ParCompactionManager::should_copy() {
  assert(action() != NotValid, "Action is not set");
  return (action() == ParCompactionManager::Copy) ||
         (action() == ParCompactionManager::CopyAndUpdate) ||
         (action() == ParCompactionManager::UpdateAndCopy);
}

ParCompactionManager*
ParCompactionManager::gc_thread_compaction_manager(uint index) {
  assert(index < ParallelGCThreads, "index out of range");
  assert(_manager_array != NULL, "Sanity");
  return _manager_array[index];
}

void InstanceKlass::oop_pc_follow_contents(oop obj, ParCompactionManager* cm) {
  assert(obj != NULL, "can't follow the content of NULL object");

  cm->follow_klass(this);
  // Only mark the header and let the scan of the meta-data mark
  // everything else.

  ParCompactionManager::MarkAndPushClosure cl(cm);
  InstanceKlass::oop_oop_iterate_oop_maps<true>(obj, &cl);
}

void InstanceMirrorKlass::oop_pc_follow_contents(oop obj, ParCompactionManager* cm) {
  InstanceKlass::oop_pc_follow_contents(obj, cm);

  // Follow the klass field in the mirror.
  Klass* klass = java_lang_Class::as_Klass(obj);
  if (klass != NULL) {
    // An anonymous class doesn't have its own class loader, so the call
    // to follow_klass will mark and push its java mirror instead of the
    // class loader. When handling the java mirror for an anonymous class
    // we need to make sure its class loader data is claimed, this is done
    // by calling follow_class_loader explicitly. For non-anonymous classes
    // the call to follow_class_loader is made when the class loader itself
    // is handled.
    if (klass->is_instance_klass() && InstanceKlass::cast(klass)->is_anonymous()) {
      cm->follow_class_loader(klass->class_loader_data());
    } else {
      cm->follow_klass(klass);
    }
  } else {
    // If klass is NULL then this a mirror for a primitive type.
    // We don't have to follow them, since they are handled as strong
    // roots in Universe::oops_do.
    assert(java_lang_Class::is_primitive(obj), "Sanity check");
  }

  ParCompactionManager::MarkAndPushClosure cl(cm);
  oop_oop_iterate_statics<true>(obj, &cl);
}

void InstanceClassLoaderKlass::oop_pc_follow_contents(oop obj, ParCompactionManager* cm) {
  InstanceKlass::oop_pc_follow_contents(obj, cm);

  ClassLoaderData * const loader_data = java_lang_ClassLoader::loader_data(obj);
  if (loader_data != NULL) {
    cm->follow_class_loader(loader_data);
  }
}

template <class T>
static void oop_pc_follow_contents_specialized(InstanceRefKlass* klass, oop obj, ParCompactionManager* cm) {
  T* referent_addr = (T*)java_lang_ref_Reference::referent_addr(obj);
  T heap_oop = oopDesc::load_heap_oop(referent_addr);
  log_develop_trace(gc, ref)("InstanceRefKlass::oop_pc_follow_contents " PTR_FORMAT, p2i(obj));
  if (!oopDesc::is_null(heap_oop)) {
    oop referent = oopDesc::decode_heap_oop_not_null(heap_oop);
    if (PSParallelCompact::mark_bitmap()->is_unmarked(referent) &&
        PSParallelCompact::ref_processor()->discover_reference(obj, klass->reference_type())) {
      // reference already enqueued, referent will be traversed later
      klass->InstanceKlass::oop_pc_follow_contents(obj, cm);
      log_develop_trace(gc, ref)("       Non NULL enqueued " PTR_FORMAT, p2i(obj));
      return;
    } else {
      // treat referent as normal oop
      log_develop_trace(gc, ref)("       Non NULL normal " PTR_FORMAT, p2i(obj));
      cm->mark_and_push(referent_addr);
    }
  }
  T* next_addr = (T*)java_lang_ref_Reference::next_addr(obj);
  // Treat discovered as normal oop, if ref is not "active",
  // i.e. if next is non-NULL.
  T  next_oop = oopDesc::load_heap_oop(next_addr);
  if (!oopDesc::is_null(next_oop)) { // i.e. ref is not "active"
    T* discovered_addr = (T*)java_lang_ref_Reference::discovered_addr(obj);
    log_develop_trace(gc, ref)("   Process discovered as normal " PTR_FORMAT, p2i(discovered_addr));
    cm->mark_and_push(discovered_addr);
  }
  cm->mark_and_push(next_addr);
  klass->InstanceKlass::oop_pc_follow_contents(obj, cm);
}


void InstanceRefKlass::oop_pc_follow_contents(oop obj, ParCompactionManager* cm) {
  if (UseCompressedOops) {
    oop_pc_follow_contents_specialized<narrowOop>(this, obj, cm);
  } else {
    oop_pc_follow_contents_specialized<oop>(this, obj, cm);
  }
}

void ObjArrayKlass::oop_pc_follow_contents(oop obj, ParCompactionManager* cm) {
  cm->follow_klass(this);

  if (UseCompressedOops) {
    oop_pc_follow_contents_specialized<narrowOop>(objArrayOop(obj), 0, cm);
  } else {
    oop_pc_follow_contents_specialized<oop>(objArrayOop(obj), 0, cm);
  }
}

void TypeArrayKlass::oop_pc_follow_contents(oop obj, ParCompactionManager* cm) {
  assert(obj->is_typeArray(),"must be a type array");
  // Performance tweak: We skip iterating over the klass pointer since we
  // know that Universe::TypeArrayKlass never moves.
}

void ParCompactionManager::follow_marking_stacks() {
  do {
    // Drain the overflow stack first, to allow stealing from the marking stack.
    oop obj;
    while (marking_stack()->pop_overflow(obj)) {
      follow_contents(obj);
    }
    while (marking_stack()->pop_local(obj)) {
      follow_contents(obj);
    }

    // Process ObjArrays one at a time to avoid marking stack bloat.
    ObjArrayTask task;
    if (_objarray_stack.pop_overflow(task) || _objarray_stack.pop_local(task)) {
      follow_contents((objArrayOop)task.obj(), task.index());
    }
  } while (!marking_stacks_empty());

  assert(marking_stacks_empty(), "Sanity");
}

void ParCompactionManager::drain_region_stacks() {
  do {
    // Drain overflow stack first so other threads can steal.
    size_t region_index;
    while (region_stack()->pop_overflow(region_index)) {
      PSParallelCompact::fill_and_update_region(this, region_index);
    }

    while (region_stack()->pop_local(region_index)) {
      PSParallelCompact::fill_and_update_region(this, region_index);
    }
  } while (!region_stack()->is_empty());
}
