/*
 * Copyright (c) 2005, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_PARALLEL_PCTASKS_HPP
#define SHARE_VM_GC_PARALLEL_PCTASKS_HPP

#include "gc/parallel/gcTaskManager.hpp"
#include "gc/parallel/psParallelCompact.hpp"
#include "gc/parallel/psTasks.hpp"


// Tasks for parallel compaction of the old generation
//
// Tasks are created and enqueued on a task queue. The
// tasks for parallel old collector for marking objects
// are MarkFromRootsTask and ThreadRootsMarkingTask.
//
// MarkFromRootsTask's are created
// with a root group (e.g., jni_handles) and when the do_it()
// method of a MarkFromRootsTask is executed, it starts marking
// form it's root group.
//
// ThreadRootsMarkingTask's are created for each Java thread.  When
// the do_it() method of a ThreadRootsMarkingTask is executed, it
// starts marking from the thread's roots.
//
// The enqueueing of the MarkFromRootsTask and ThreadRootsMarkingTask
// do little more than create the task and put it on a queue.  The
// queue is a GCTaskQueue and threads steal tasks from this GCTaskQueue.
//
// In addition to the MarkFromRootsTask and ThreadRootsMarkingTask
// tasks there are StealMarkingTask tasks.  The StealMarkingTask's
// steal a reference from the marking stack of another
// thread and transitively marks the object of the reference
// and internal references.  After successfully stealing a reference
// and marking it, the StealMarkingTask drains its marking stack
// stack before attempting another steal.
//
// ThreadRootsMarkingTask
//
// This task marks from the roots of a single thread. This task
// enables marking of thread roots in parallel.
//

class ParallelTaskTerminator;

class ThreadRootsMarkingTask : public GCTask {
 private:
  Thread* _thread;

 public:
  ThreadRootsMarkingTask(Thread* root) : _thread(root) {}

  char* name() { return (char *)"thread-roots-marking-task"; }

  virtual void do_it(GCTaskManager* manager, uint which);
};


//
// MarkFromRootsTask
//
// This task marks from all the roots to all live
// objects.
//
//

class MarkFromRootsTask : public GCTask {
 public:
  enum RootType {
    universe              = 1,
    jni_handles           = 2,
    threads               = 3,
    object_synchronizer   = 4,
    management            = 5,
    jvmti                 = 6,
    system_dictionary     = 7,
    class_loader_data     = 8,
    code_cache            = 9
  };
 private:
  RootType _root_type;
 public:
  MarkFromRootsTask(RootType value) : _root_type(value) {}

  char* name() { return (char *)"mark-from-roots-task"; }

  virtual void do_it(GCTaskManager* manager, uint which);
};

//
// RefProcTaskProxy
//
// This task is used as a proxy to parallel reference processing tasks .
//

class RefProcTaskProxy : public GCTask {
  typedef AbstractRefProcTaskExecutor::ProcessTask ProcessTask;
  ProcessTask & _rp_task;
  uint          _work_id;
public:
  RefProcTaskProxy(ProcessTask & rp_task, uint work_id)
    : _rp_task(rp_task),
      _work_id(work_id)
  { }

private:
  virtual char* name() { return (char *)"Process referents by policy in parallel"; }

  virtual void do_it(GCTaskManager* manager, uint which);
};


//
// RefProcTaskExecutor
//
// Task executor is an interface for the reference processor to run
// tasks using GCTaskManager.
//

class RefProcTaskExecutor: public AbstractRefProcTaskExecutor {
  virtual void execute(ProcessTask& task, uint ergo_workers);
};


//
// StealMarkingTask
//
// This task is used to distribute work to idle threads.
//

class StealMarkingTask : public GCTask {
 private:
   ParallelTaskTerminator* const _terminator;
 private:

 public:
  char* name() { return (char *)"steal-marking-task"; }

  StealMarkingTask(ParallelTaskTerminator* t);

  ParallelTaskTerminator* terminator() { return _terminator; }

  virtual void do_it(GCTaskManager* manager, uint which);
};

//
// CompactionWithStealingTask
//
// This task is used to distribute work to idle threads.
//

class CompactionWithStealingTask : public GCTask {
 private:
   ParallelTaskTerminator* const _terminator;
 public:
  CompactionWithStealingTask(ParallelTaskTerminator* t);

  char* name() { return (char *)"steal-region-task"; }
  ParallelTaskTerminator* terminator() { return _terminator; }

  virtual void do_it(GCTaskManager* manager, uint which);
};

//
// UpdateDensePrefixTask
//
// This task is used to update the dense prefix
// of a space.
//

class UpdateDensePrefixTask : public GCTask {
 private:
  PSParallelCompact::SpaceId _space_id;
  size_t _region_index_start;
  size_t _region_index_end;

 public:
  char* name() { return (char *)"update-dense_prefix-task"; }

  UpdateDensePrefixTask(PSParallelCompact::SpaceId space_id,
                        size_t region_index_start,
                        size_t region_index_end);

  virtual void do_it(GCTaskManager* manager, uint which);
};
#endif // SHARE_VM_GC_PARALLEL_PCTASKS_HPP
