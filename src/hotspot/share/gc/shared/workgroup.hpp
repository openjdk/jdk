/*
 * Copyright (c) 2002, 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_WORKGROUP_HPP
#define SHARE_GC_SHARED_WORKGROUP_HPP

#include "memory/allocation.hpp"
#include "metaprogramming/enableIf.hpp"
#include "metaprogramming/logical.hpp"
#include "runtime/globals.hpp"
#include "runtime/nonJavaThread.hpp"
#include "runtime/thread.hpp"
#include "gc/shared/gcId.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

// Task class hierarchy:
//   WorkerTask
//
// Gang/Group class hierarchy:
//   WorkerThreads

// Forward declarations of classes defined here

class WorkerThread;
class Semaphore;
class ThreadClosure;
class WorkerTaskDispatcher;

// An abstract task to be worked on by a gang.
// You subclass this to supply your own work() method
class WorkerTask : public CHeapObj<mtInternal> {
  const char* _name;
  const uint _gc_id;

 public:
  explicit WorkerTask(const char* name) :
    _name(name),
    _gc_id(GCId::current_or_undefined())
  {}

  // The abstract work method.
  // The argument tells you which member of the gang you are.
  virtual void work(uint worker_id) = 0;

  // Debugging accessor for the name.
  const char* name() const { return _name; }
  const uint gc_id() const { return _gc_id; }
};

struct WorkData {
  WorkerTask* _task;
  uint              _worker_id;
  WorkData(WorkerTask* task, uint worker_id) : _task(task), _worker_id(worker_id) {}
};

// The work gang is the collection of workers to execute tasks.
// The number of workers run for a task is "_active_workers"
// while "_total_workers" is the number of available workers.
class WorkerThreads : public CHeapObj<mtInternal> {
  // The array of worker threads for this gang.
  WorkerThread** _workers;
  // The count of the number of workers in the gang.
  uint _total_workers;
  // The currently active workers in this gang.
  uint _active_workers;
  // The count of created workers in the gang.
  uint _created_workers;
  // Printing support.
  const char* _name;

  // To get access to the WorkerTaskDispatcher instance.
  friend class WorkerThread;
  WorkerTaskDispatcher* const _dispatcher;

  WorkerTaskDispatcher* dispatcher() const { return _dispatcher; }

 public:
  WorkerThreads(const char* name, uint workers);

  ~WorkerThreads();

  // Initialize workers in the gang.  Return true if initialization succeeded.
  void initialize_workers();

  uint total_workers() const { return _total_workers; }

  uint created_workers() const {
    return _created_workers;
  }

  uint active_workers() const {
    assert(_active_workers != 0, "zero active workers");
    assert(_active_workers <= _total_workers,
           "_active_workers: %u > _total_workers: %u", _active_workers, _total_workers);
    return _active_workers;
  }

  uint update_active_workers(uint num_workers);

  // Return the Ith worker.
  WorkerThread* worker(uint i) const;

  // Base name (without worker id #) of threads.
  const char* group_name() { return name(); }

  void threads_do(ThreadClosure* tc) const;

  virtual WorkerThread* create_worker(uint id);

  // Debugging.
  const char* name() const { return _name; }

  // Run a task using the current active number of workers, returns when the task is done.
  void run_task(WorkerTask* task);

  // Run a task with the given number of workers, returns
  // when the task is done. The number of workers must be at most the number of
  // active workers.  Additional workers may be created if an insufficient
  // number currently exists.
  void run_task(WorkerTask* task, uint num_workers);
};

// Temporarily try to set the number of active workers.
// It's not guaranteed that it succeeds, and users need to
// query the number of active workers.
class WithActiveWorkers : public StackObj {
private:
  WorkerThreads* const _gang;
  const uint              _old_active_workers;

public:
  WithActiveWorkers(WorkerThreads* gang, uint requested_num_workers) :
      _gang(gang),
      _old_active_workers(gang->active_workers()) {
    uint capped_num_workers = MIN2(requested_num_workers, gang->total_workers());
    gang->update_active_workers(capped_num_workers);
  }

  ~WithActiveWorkers() {
    _gang->update_active_workers(_old_active_workers);
  }
};

class WorkerThread : public NamedThread {
private:
  uint _id;
  WorkerThreads* _gang;

  void initialize();
  void loop();

  WorkerThreads* gang() const { return _gang; }

  WorkData wait_for_task();
  void run_task(WorkData work);
  void signal_task_done();

public:
  static WorkerThread* current() {
    return WorkerThread::cast(Thread::current());
  }

  static WorkerThread* cast(Thread* t) {
    assert(t->is_Worker_thread(), "incorrect cast to WorkerThread");
    return static_cast<WorkerThread*>(t);
  }

  WorkerThread(WorkerThreads* gang, uint id);
  virtual bool is_Worker_thread() const { return true; }

  void set_id(uint work_id)             { _id = work_id; }
  uint id() const                       { return _id; }

  virtual const char* type_name() const { return "WorkerThread"; }

  void run() override;
};

#endif // SHARE_GC_SHARED_WORKGROUP_HPP
