/*
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/parallelScavenge/gcTaskManager.hpp"
#include "gc_implementation/parallelScavenge/objectStartArray.hpp"
#include "gc_implementation/parallelScavenge/parMarkBitMap.hpp"
#include "gc_implementation/parallelScavenge/parallelScavengeHeap.hpp"
#include "gc_implementation/parallelScavenge/psCompactionManager.hpp"
#include "gc_implementation/parallelScavenge/psOldGen.hpp"
#include "gc_implementation/parallelScavenge/psParallelCompact.hpp"
#include "oops/objArrayKlass.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oop.pcgc.inline.hpp"
#include "utilities/stack.inline.hpp"

PSOldGen*            ParCompactionManager::_old_gen = NULL;
ParCompactionManager**  ParCompactionManager::_manager_array = NULL;

RegionTaskQueue**              ParCompactionManager::_region_list = NULL;

OopTaskQueueSet*     ParCompactionManager::_stack_array = NULL;
ParCompactionManager::ObjArrayTaskQueueSet*
  ParCompactionManager::_objarray_queues = NULL;
ObjectStartArray*    ParCompactionManager::_start_array = NULL;
ParMarkBitMap*       ParCompactionManager::_mark_bitmap = NULL;
RegionTaskQueueSet*  ParCompactionManager::_region_array = NULL;

uint*                 ParCompactionManager::_recycled_stack_index = NULL;
int                   ParCompactionManager::_recycled_top = -1;
int                   ParCompactionManager::_recycled_bottom = -1;

ParCompactionManager::ParCompactionManager() :
    _action(CopyAndUpdate),
    _region_stack(NULL),
    _region_stack_index((uint)max_uintx) {

  ParallelScavengeHeap* heap = (ParallelScavengeHeap*)Universe::heap();
  assert(heap->kind() == CollectedHeap::ParallelScavengeHeap, "Sanity");

  _old_gen = heap->old_gen();
  _start_array = old_gen()->start_array();

  marking_stack()->initialize();
  _objarray_stack.initialize();
}

ParCompactionManager::~ParCompactionManager() {
  delete _recycled_stack_index;
}

void ParCompactionManager::initialize(ParMarkBitMap* mbm) {
  assert(PSParallelCompact::gc_task_manager() != NULL,
    "Needed for initialization");

  _mark_bitmap = mbm;

  uint parallel_gc_threads = PSParallelCompact::gc_task_manager()->workers();

  assert(_manager_array == NULL, "Attempt to initialize twice");
  _manager_array = NEW_C_HEAP_ARRAY(ParCompactionManager*, parallel_gc_threads+1, mtGC);
  guarantee(_manager_array != NULL, "Could not allocate manager_array");

  _region_list = NEW_C_HEAP_ARRAY(RegionTaskQueue*,
                         parallel_gc_threads+1, mtGC);
  guarantee(_region_list != NULL, "Could not initialize promotion manager");

  _recycled_stack_index = NEW_C_HEAP_ARRAY(uint, parallel_gc_threads, mtGC);

  // parallel_gc-threads + 1 to be consistent with the number of
  // compaction managers.
  for(uint i=0; i<parallel_gc_threads + 1; i++) {
    _region_list[i] = new RegionTaskQueue();
    region_list(i)->initialize();
  }

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
    region_array()->register_queue(i, region_list(i));
  }

  // The VMThread gets its own ParCompactionManager, which is not available
  // for work stealing.
  _manager_array[parallel_gc_threads] = new ParCompactionManager();
  guarantee(_manager_array[parallel_gc_threads] != NULL,
    "Could not create ParCompactionManager");
  assert(PSParallelCompact::gc_task_manager()->workers() != 0,
    "Not initialized?");
}

int ParCompactionManager::pop_recycled_stack_index() {
  assert(_recycled_bottom <= _recycled_top, "list is empty");
  // Get the next available index
  if (_recycled_bottom < _recycled_top) {
    uint cur, next, last;
    do {
      cur = _recycled_bottom;
      next = cur + 1;
      last = Atomic::cmpxchg(next, &_recycled_bottom, cur);
    } while (cur != last);
    return _recycled_stack_index[next];
  } else {
    return -1;
  }
}

void ParCompactionManager::push_recycled_stack_index(uint v) {
  // Get the next available index
  int cur = Atomic::add(1, &_recycled_top);
  _recycled_stack_index[cur] = v;
  assert(_recycled_bottom <= _recycled_top, "list top and bottom are wrong");
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

void ParCompactionManager::region_list_push(uint list_index,
                                            size_t region_index) {
  region_list(list_index)->push(region_index);
}

void ParCompactionManager::verify_region_list_empty(uint list_index) {
  assert(region_list(list_index)->is_empty(), "Not empty");
}

ParCompactionManager*
ParCompactionManager::gc_thread_compaction_manager(int index) {
  assert(index >= 0 && index < (int)ParallelGCThreads, "index out of range");
  assert(_manager_array != NULL, "Sanity");
  return _manager_array[index];
}

void ParCompactionManager::follow_marking_stacks() {
  do {
    // Drain the overflow stack first, to allow stealing from the marking stack.
    oop obj;
    while (marking_stack()->pop_overflow(obj)) {
      obj->follow_contents(this);
    }
    while (marking_stack()->pop_local(obj)) {
      obj->follow_contents(this);
    }

    // Process ObjArrays one at a time to avoid marking stack bloat.
    ObjArrayTask task;
    if (_objarray_stack.pop_overflow(task) || _objarray_stack.pop_local(task)) {
      ObjArrayKlass* k = (ObjArrayKlass*)task.obj()->klass();
      k->oop_follow_contents(this, task.obj(), task.index());
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
