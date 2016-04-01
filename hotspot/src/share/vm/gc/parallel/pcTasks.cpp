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
#include "code/codeCache.hpp"
#include "gc/parallel/parallelScavengeHeap.hpp"
#include "gc/parallel/pcTasks.hpp"
#include "gc/parallel/psCompactionManager.inline.hpp"
#include "gc/parallel/psParallelCompact.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/gcTimer.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/objArrayKlass.inline.hpp"
#include "oops/oop.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/fprofiler.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/thread.hpp"
#include "runtime/vmThread.hpp"
#include "services/management.hpp"
#include "utilities/stack.inline.hpp"

//
// ThreadRootsMarkingTask
//

void ThreadRootsMarkingTask::do_it(GCTaskManager* manager, uint which) {
  assert(ParallelScavengeHeap::heap()->is_gc_active(), "called outside gc");

  ResourceMark rm;

  ParCompactionManager* cm =
    ParCompactionManager::gc_thread_compaction_manager(which);

  ParCompactionManager::MarkAndPushClosure mark_and_push_closure(cm);
  MarkingCodeBlobClosure mark_and_push_in_blobs(&mark_and_push_closure, !CodeBlobToOopClosure::FixRelocations);

  if (_java_thread != NULL)
    _java_thread->oops_do(
        &mark_and_push_closure,
        &mark_and_push_in_blobs);

  if (_vm_thread != NULL)
    _vm_thread->oops_do(
        &mark_and_push_closure,
        &mark_and_push_in_blobs);

  // Do the real work
  cm->follow_marking_stacks();
}


void MarkFromRootsTask::do_it(GCTaskManager* manager, uint which) {
  assert(ParallelScavengeHeap::heap()->is_gc_active(), "called outside gc");

  ParCompactionManager* cm =
    ParCompactionManager::gc_thread_compaction_manager(which);
  ParCompactionManager::MarkAndPushClosure mark_and_push_closure(cm);
  ParCompactionManager::FollowKlassClosure follow_klass_closure(&mark_and_push_closure);

  switch (_root_type) {
    case universe:
      Universe::oops_do(&mark_and_push_closure);
      break;

    case jni_handles:
      JNIHandles::oops_do(&mark_and_push_closure);
      break;

    case threads:
    {
      ResourceMark rm;
      MarkingCodeBlobClosure each_active_code_blob(&mark_and_push_closure, !CodeBlobToOopClosure::FixRelocations);
      Threads::oops_do(&mark_and_push_closure, &each_active_code_blob);
    }
    break;

    case object_synchronizer:
      ObjectSynchronizer::oops_do(&mark_and_push_closure);
      break;

    case flat_profiler:
      FlatProfiler::oops_do(&mark_and_push_closure);
      break;

    case management:
      Management::oops_do(&mark_and_push_closure);
      break;

    case jvmti:
      JvmtiExport::oops_do(&mark_and_push_closure);
      break;

    case system_dictionary:
      SystemDictionary::always_strong_oops_do(&mark_and_push_closure);
      break;

    case class_loader_data:
      ClassLoaderDataGraph::always_strong_oops_do(&mark_and_push_closure, &follow_klass_closure, true);
      break;

    case code_cache:
      // Do not treat nmethods as strong roots for mark/sweep, since we can unload them.
      //CodeCache::scavenge_root_nmethods_do(CodeBlobToOopClosure(&mark_and_push_closure));
      break;

    default:
      fatal("Unknown root type");
  }

  // Do the real work
  cm->follow_marking_stacks();
}


//
// RefProcTaskProxy
//

void RefProcTaskProxy::do_it(GCTaskManager* manager, uint which)
{
  assert(ParallelScavengeHeap::heap()->is_gc_active(), "called outside gc");

  ParCompactionManager* cm =
    ParCompactionManager::gc_thread_compaction_manager(which);
  ParCompactionManager::MarkAndPushClosure mark_and_push_closure(cm);
  ParCompactionManager::FollowStackClosure follow_stack_closure(cm);
  _rp_task.work(_work_id, *PSParallelCompact::is_alive_closure(),
                mark_and_push_closure, follow_stack_closure);
}

//
// RefProcTaskExecutor
//

void RefProcTaskExecutor::execute(ProcessTask& task)
{
  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
  uint parallel_gc_threads = heap->gc_task_manager()->workers();
  uint active_gc_threads = heap->gc_task_manager()->active_workers();
  OopTaskQueueSet* qset = ParCompactionManager::stack_array();
  ParallelTaskTerminator terminator(active_gc_threads, qset);
  GCTaskQueue* q = GCTaskQueue::create();
  for(uint i=0; i<parallel_gc_threads; i++) {
    q->enqueue(new RefProcTaskProxy(task, i));
  }
  if (task.marks_oops_alive()) {
    if (parallel_gc_threads>1) {
      for (uint j=0; j<active_gc_threads; j++) {
        q->enqueue(new StealMarkingTask(&terminator));
      }
    }
  }
  PSParallelCompact::gc_task_manager()->execute_and_wait(q);
}

void RefProcTaskExecutor::execute(EnqueueTask& task)
{
  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
  uint parallel_gc_threads = heap->gc_task_manager()->workers();
  GCTaskQueue* q = GCTaskQueue::create();
  for(uint i=0; i<parallel_gc_threads; i++) {
    q->enqueue(new RefEnqueueTaskProxy(task, i));
  }
  PSParallelCompact::gc_task_manager()->execute_and_wait(q);
}

//
// StealMarkingTask
//

StealMarkingTask::StealMarkingTask(ParallelTaskTerminator* t) :
  _terminator(t) {}

void StealMarkingTask::do_it(GCTaskManager* manager, uint which) {
  assert(ParallelScavengeHeap::heap()->is_gc_active(), "called outside gc");

  ParCompactionManager* cm =
    ParCompactionManager::gc_thread_compaction_manager(which);
  ParCompactionManager::MarkAndPushClosure mark_and_push_closure(cm);

  oop obj = NULL;
  ObjArrayTask task;
  int random_seed = 17;
  do {
    while (ParCompactionManager::steal_objarray(which, &random_seed, task)) {
      cm->follow_contents((objArrayOop)task.obj(), task.index());
      cm->follow_marking_stacks();
    }
    while (ParCompactionManager::steal(which, &random_seed, obj)) {
      cm->follow_contents(obj);
      cm->follow_marking_stacks();
    }
  } while (!terminator()->offer_termination());
}

//
// StealRegionCompactionTask
//

StealRegionCompactionTask::StealRegionCompactionTask(ParallelTaskTerminator* t):
  _terminator(t) {}

void StealRegionCompactionTask::do_it(GCTaskManager* manager, uint which) {
  assert(ParallelScavengeHeap::heap()->is_gc_active(), "called outside gc");

  ParCompactionManager* cm =
    ParCompactionManager::gc_thread_compaction_manager(which);

  // Drain the stacks that have been preloaded with regions
  // that are ready to fill.

  cm->drain_region_stacks();

  guarantee(cm->region_stack()->is_empty(), "Not empty");

  size_t region_index = 0;
  int random_seed = 17;

  while(true) {
    if (ParCompactionManager::steal(which, &random_seed, region_index)) {
      PSParallelCompact::fill_and_update_region(cm, region_index);
      cm->drain_region_stacks();
    } else {
      if (terminator()->offer_termination()) {
        break;
      }
      // Go around again.
    }
  }
  return;
}

UpdateDensePrefixTask::UpdateDensePrefixTask(
                                   PSParallelCompact::SpaceId space_id,
                                   size_t region_index_start,
                                   size_t region_index_end) :
  _space_id(space_id), _region_index_start(region_index_start),
  _region_index_end(region_index_end) {}

void UpdateDensePrefixTask::do_it(GCTaskManager* manager, uint which) {

  ParCompactionManager* cm =
    ParCompactionManager::gc_thread_compaction_manager(which);

  PSParallelCompact::update_and_deadwood_in_dense_prefix(cm,
                                                         _space_id,
                                                         _region_index_start,
                                                         _region_index_end);
}
