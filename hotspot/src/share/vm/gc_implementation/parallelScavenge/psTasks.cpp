/*
 * Copyright (c) 2002, 2010, Oracle and/or its affiliates. All rights reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_psTasks.cpp.incl"

//
// ScavengeRootsTask
//

// Define before use
class PSScavengeRootsClosure: public OopClosure {
 private:
  PSPromotionManager* _promotion_manager;

 protected:
  template <class T> void do_oop_work(T *p) {
    if (PSScavenge::should_scavenge(p)) {
      // We never card mark roots, maybe call a func without test?
      PSScavenge::copy_and_push_safe_barrier(_promotion_manager, p);
    }
  }
 public:
  PSScavengeRootsClosure(PSPromotionManager* pm) : _promotion_manager(pm) { }
  void do_oop(oop* p)       { PSScavengeRootsClosure::do_oop_work(p); }
  void do_oop(narrowOop* p) { PSScavengeRootsClosure::do_oop_work(p); }
};

void ScavengeRootsTask::do_it(GCTaskManager* manager, uint which) {
  assert(Universe::heap()->is_gc_active(), "called outside gc");

  PSPromotionManager* pm = PSPromotionManager::gc_thread_promotion_manager(which);
  PSScavengeRootsClosure roots_closure(pm);

  switch (_root_type) {
    case universe:
      Universe::oops_do(&roots_closure);
      ReferenceProcessor::oops_do(&roots_closure);
      break;

    case jni_handles:
      JNIHandles::oops_do(&roots_closure);
      break;

    case threads:
    {
      ResourceMark rm;
      Threads::oops_do(&roots_closure, NULL);
    }
    break;

    case object_synchronizer:
      ObjectSynchronizer::oops_do(&roots_closure);
      break;

    case flat_profiler:
      FlatProfiler::oops_do(&roots_closure);
      break;

    case system_dictionary:
      SystemDictionary::oops_do(&roots_closure);
      break;

    case management:
      Management::oops_do(&roots_closure);
      break;

    case jvmti:
      JvmtiExport::oops_do(&roots_closure);
      break;


    case code_cache:
      {
        CodeBlobToOopClosure each_scavengable_code_blob(&roots_closure, /*do_marking=*/ true);
        CodeCache::scavenge_root_nmethods_do(&each_scavengable_code_blob);
      }
      break;

    default:
      fatal("Unknown root type");
  }

  // Do the real work
  pm->drain_stacks(false);
}

//
// ThreadRootsTask
//

void ThreadRootsTask::do_it(GCTaskManager* manager, uint which) {
  assert(Universe::heap()->is_gc_active(), "called outside gc");

  PSPromotionManager* pm = PSPromotionManager::gc_thread_promotion_manager(which);
  PSScavengeRootsClosure roots_closure(pm);
  CodeBlobToOopClosure roots_in_blobs(&roots_closure, /*do_marking=*/ true);

  if (_java_thread != NULL)
    _java_thread->oops_do(&roots_closure, &roots_in_blobs);

  if (_vm_thread != NULL)
    _vm_thread->oops_do(&roots_closure, &roots_in_blobs);

  // Do the real work
  pm->drain_stacks(false);
}

//
// StealTask
//

StealTask::StealTask(ParallelTaskTerminator* t) :
  _terminator(t) {}

void StealTask::do_it(GCTaskManager* manager, uint which) {
  assert(Universe::heap()->is_gc_active(), "called outside gc");

  PSPromotionManager* pm =
    PSPromotionManager::gc_thread_promotion_manager(which);
  pm->drain_stacks(true);
  guarantee(pm->stacks_empty(),
            "stacks should be empty at this point");

  int random_seed = 17;
  if (pm->depth_first()) {
    while(true) {
      StarTask p;
      if (PSPromotionManager::steal_depth(which, &random_seed, p)) {
        TASKQUEUE_STATS_ONLY(pm->record_steal(p));
        pm->process_popped_location_depth(p);
        pm->drain_stacks_depth(true);
      } else {
        if (terminator()->offer_termination()) {
          break;
        }
      }
    }
  } else {
    while(true) {
      oop obj;
      if (PSPromotionManager::steal_breadth(which, &random_seed, obj)) {
        obj->copy_contents(pm);
        pm->drain_stacks_breadth(true);
      } else {
        if (terminator()->offer_termination()) {
          break;
        }
      }
    }
  }
  guarantee(pm->stacks_empty(), "stacks should be empty at this point");
}

//
// SerialOldToYoungRootsTask
//

void SerialOldToYoungRootsTask::do_it(GCTaskManager* manager, uint which) {
  assert(_gen != NULL, "Sanity");
  assert(_gen->object_space()->contains(_gen_top) || _gen_top == _gen->object_space()->top(), "Sanity");

  {
    PSPromotionManager* pm = PSPromotionManager::gc_thread_promotion_manager(which);

    assert(Universe::heap()->kind() == CollectedHeap::ParallelScavengeHeap, "Sanity");
    CardTableExtension* card_table = (CardTableExtension *)Universe::heap()->barrier_set();
    // FIX ME! Assert that card_table is the type we believe it to be.

    card_table->scavenge_contents(_gen->start_array(),
                                  _gen->object_space(),
                                  _gen_top,
                                  pm);

    // Do the real work
    pm->drain_stacks(false);
  }
}

//
// OldToYoungRootsTask
//

void OldToYoungRootsTask::do_it(GCTaskManager* manager, uint which) {
  assert(_gen != NULL, "Sanity");
  assert(_gen->object_space()->contains(_gen_top) || _gen_top == _gen->object_space()->top(), "Sanity");
  assert(_stripe_number < ParallelGCThreads, "Sanity");

  {
    PSPromotionManager* pm = PSPromotionManager::gc_thread_promotion_manager(which);

    assert(Universe::heap()->kind() == CollectedHeap::ParallelScavengeHeap, "Sanity");
    CardTableExtension* card_table = (CardTableExtension *)Universe::heap()->barrier_set();
    // FIX ME! Assert that card_table is the type we believe it to be.

    card_table->scavenge_contents_parallel(_gen->start_array(),
                                           _gen->object_space(),
                                           _gen_top,
                                           pm,
                                           _stripe_number);

    // Do the real work
    pm->drain_stacks(false);
  }
}
