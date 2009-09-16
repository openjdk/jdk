/*
 * Copyright 2005-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

# include "incls/_precompiled.incl"
# include "incls/_pcTasks.cpp.incl"

//
// ThreadRootsMarkingTask
//

void ThreadRootsMarkingTask::do_it(GCTaskManager* manager, uint which) {
  assert(Universe::heap()->is_gc_active(), "called outside gc");

  ResourceMark rm;

  NOT_PRODUCT(TraceTime tm("ThreadRootsMarkingTask",
    PrintGCDetails && TraceParallelOldGCTasks, true, gclog_or_tty));
  ParCompactionManager* cm =
    ParCompactionManager::gc_thread_compaction_manager(which);
  PSParallelCompact::MarkAndPushClosure mark_and_push_closure(cm);
  CodeBlobToOopClosure mark_and_push_in_blobs(&mark_and_push_closure, /*do_marking=*/ true);

  if (_java_thread != NULL)
    _java_thread->oops_do(&mark_and_push_closure, &mark_and_push_in_blobs);

  if (_vm_thread != NULL)
    _vm_thread->oops_do(&mark_and_push_closure, &mark_and_push_in_blobs);

  // Do the real work
  cm->drain_marking_stacks(&mark_and_push_closure);
}


void MarkFromRootsTask::do_it(GCTaskManager* manager, uint which) {
  assert(Universe::heap()->is_gc_active(), "called outside gc");

  NOT_PRODUCT(TraceTime tm("MarkFromRootsTask",
    PrintGCDetails && TraceParallelOldGCTasks, true, gclog_or_tty));
  ParCompactionManager* cm =
    ParCompactionManager::gc_thread_compaction_manager(which);
  // cm->allocate_stacks();
  assert(cm->stacks_have_been_allocated(),
    "Stack space has not been allocated");
  PSParallelCompact::MarkAndPushClosure mark_and_push_closure(cm);

  switch (_root_type) {
    case universe:
      Universe::oops_do(&mark_and_push_closure);
      break;

    case reference_processing:
      ReferenceProcessor::oops_do(&mark_and_push_closure);
      break;

    case jni_handles:
      JNIHandles::oops_do(&mark_and_push_closure);
      break;

    case threads:
    {
      ResourceMark rm;
      CodeBlobToOopClosure each_active_code_blob(&mark_and_push_closure, /*do_marking=*/ true);
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

    case vm_symbols:
      vmSymbols::oops_do(&mark_and_push_closure);
      break;

    case code_cache:
      // Do not treat nmethods as strong roots for mark/sweep, since we can unload them.
      //CodeCache::scavenge_root_nmethods_do(CodeBlobToOopClosure(&mark_and_push_closure));
      break;

    default:
      fatal("Unknown root type");
  }

  // Do the real work
  cm->drain_marking_stacks(&mark_and_push_closure);
  // cm->deallocate_stacks();
}


//
// RefProcTaskProxy
//

void RefProcTaskProxy::do_it(GCTaskManager* manager, uint which)
{
  assert(Universe::heap()->is_gc_active(), "called outside gc");

  NOT_PRODUCT(TraceTime tm("RefProcTask",
    PrintGCDetails && TraceParallelOldGCTasks, true, gclog_or_tty));
  ParCompactionManager* cm =
    ParCompactionManager::gc_thread_compaction_manager(which);
  // cm->allocate_stacks();
  assert(cm->stacks_have_been_allocated(),
    "Stack space has not been allocated");
  PSParallelCompact::MarkAndPushClosure mark_and_push_closure(cm);
  PSParallelCompact::FollowStackClosure follow_stack_closure(cm);
  _rp_task.work(_work_id, *PSParallelCompact::is_alive_closure(),
                mark_and_push_closure, follow_stack_closure);
}

//
// RefProcTaskExecutor
//

void RefProcTaskExecutor::execute(ProcessTask& task)
{
  ParallelScavengeHeap* heap = PSParallelCompact::gc_heap();
  uint parallel_gc_threads = heap->gc_task_manager()->workers();
  RegionTaskQueueSet* qset = ParCompactionManager::region_array();
  ParallelTaskTerminator terminator(parallel_gc_threads, qset);
  GCTaskQueue* q = GCTaskQueue::create();
  for(uint i=0; i<parallel_gc_threads; i++) {
    q->enqueue(new RefProcTaskProxy(task, i));
  }
  if (task.marks_oops_alive()) {
    if (parallel_gc_threads>1) {
      for (uint j=0; j<parallel_gc_threads; j++) {
        q->enqueue(new StealMarkingTask(&terminator));
      }
    }
  }
  PSParallelCompact::gc_task_manager()->execute_and_wait(q);
}

void RefProcTaskExecutor::execute(EnqueueTask& task)
{
  ParallelScavengeHeap* heap = PSParallelCompact::gc_heap();
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
  assert(Universe::heap()->is_gc_active(), "called outside gc");

  NOT_PRODUCT(TraceTime tm("StealMarkingTask",
    PrintGCDetails && TraceParallelOldGCTasks, true, gclog_or_tty));

  ParCompactionManager* cm =
    ParCompactionManager::gc_thread_compaction_manager(which);
  PSParallelCompact::MarkAndPushClosure mark_and_push_closure(cm);

  oop obj = NULL;
  int random_seed = 17;
  while(true) {
    if (ParCompactionManager::steal(which, &random_seed, obj)) {
      obj->follow_contents(cm);
      cm->drain_marking_stacks(&mark_and_push_closure);
    } else {
      if (terminator()->offer_termination()) {
        break;
      }
    }
  }
}

//
// StealRegionCompactionTask
//


StealRegionCompactionTask::StealRegionCompactionTask(ParallelTaskTerminator* t):
  _terminator(t) {}

void StealRegionCompactionTask::do_it(GCTaskManager* manager, uint which) {
  assert(Universe::heap()->is_gc_active(), "called outside gc");

  NOT_PRODUCT(TraceTime tm("StealRegionCompactionTask",
    PrintGCDetails && TraceParallelOldGCTasks, true, gclog_or_tty));

  ParCompactionManager* cm =
    ParCompactionManager::gc_thread_compaction_manager(which);

  // Has to drain stacks first because there may be regions on
  // preloaded onto the stack and this thread may never have
  // done a draining task.  Are the draining tasks needed?

  cm->drain_region_stacks();

  size_t region_index = 0;
  int random_seed = 17;

  // If we're the termination task, try 10 rounds of stealing before
  // setting the termination flag

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

  NOT_PRODUCT(TraceTime tm("UpdateDensePrefixTask",
    PrintGCDetails && TraceParallelOldGCTasks, true, gclog_or_tty));

  ParCompactionManager* cm =
    ParCompactionManager::gc_thread_compaction_manager(which);

  PSParallelCompact::update_and_deadwood_in_dense_prefix(cm,
                                                         _space_id,
                                                         _region_index_start,
                                                         _region_index_end);
}

void DrainStacksCompactionTask::do_it(GCTaskManager* manager, uint which) {
  assert(Universe::heap()->is_gc_active(), "called outside gc");

  NOT_PRODUCT(TraceTime tm("DrainStacksCompactionTask",
    PrintGCDetails && TraceParallelOldGCTasks, true, gclog_or_tty));

  ParCompactionManager* cm =
    ParCompactionManager::gc_thread_compaction_manager(which);

  // Process any regions already in the compaction managers stacks.
  cm->drain_region_stacks();
}
