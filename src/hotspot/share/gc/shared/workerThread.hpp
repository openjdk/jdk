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

#ifndef SHARE_GC_SHARED_WORKERTHREAD_HPP
#define SHARE_GC_SHARED_WORKERTHREAD_HPP

#include "gc/shared/gcId.hpp"
#include "memory/allocation.hpp"
#include "runtime/nonJavaThread.hpp"
#include "runtime/semaphore.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

class ThreadClosure;
class WorkerTaskDispatcher;
class WorkerThread;

// An task to be worked on by worker threads
class WorkerTask : public CHeapObj<mtInternal> {
private:
  const char* _name;
  const uint _gc_id;

 public:
  explicit WorkerTask(const char* name) :
    _name(name),
    _gc_id(GCId::current_or_undefined()) {}

  const char* name() const { return _name; }
  const uint gc_id() const { return _gc_id; }

  virtual void work(uint worker_id) = 0;
};

// WorkerThreads dispatcher implemented with semaphores
class WorkerTaskDispatcher {
  // The task currently being dispatched to the WorkerThreads.
  WorkerTask* _task;

  volatile uint _started;
  volatile uint _not_finished;

  // Semaphore used to start the WorkerThreads.
  Semaphore _start_semaphore;
  // Semaphore used to notify the coordinator that all workers are done.
  Semaphore _end_semaphore;

public:
  WorkerTaskDispatcher();

  // Coordinator API.

  // Distributes the task out to num_workers workers.
  // Returns when the task has been completed by all workers.
  void coordinator_distribute_task(WorkerTask* task, uint num_workers);

  // Worker API.

  // Waits for a task to become available to the worker and runs it.
  void worker_run_task();
};

// A set of worker threads to execute tasks
class WorkerThreads : public CHeapObj<mtInternal> {
private:
  const char* const    _name;
  WorkerThread**       _workers;
  const uint           _max_workers;
  uint                 _created_workers;
  uint                 _active_workers;
  WorkerTaskDispatcher _dispatcher;

  WorkerThread* create_worker(uint name_suffix);

protected:
  virtual void on_create_worker(WorkerThread* worker) {}

public:
  WorkerThreads(const char* name, uint max_workers);

  void initialize_workers();

  uint max_workers() const     { return _max_workers; }
  uint created_workers() const { return _created_workers; }
  uint active_workers() const  { return _active_workers; }

  uint set_active_workers(uint num_workers);

  void threads_do(ThreadClosure* tc) const;

  const char* name() const { return _name; }

  // Run a task using the current active number of workers, returns when the task is done.
  void run_task(WorkerTask* task);

  // Run a task with the given number of workers, returns when the task is done.
  void run_task(WorkerTask* task, uint num_workers);
};

class WorkerThread : public NamedThread {
  friend class WorkerTaskDispatcher;

private:
  static THREAD_LOCAL uint _worker_id;

  WorkerTaskDispatcher* const _dispatcher;

  static void set_worker_id(uint worker_id) { _worker_id = worker_id; }

public:
  static uint worker_id() { return _worker_id; }

  WorkerThread(const char* name_prefix, uint which, WorkerTaskDispatcher* dispatcher);

  bool is_Worker_thread() const override { return true; }
  const char* type_name() const override { return "WorkerThread"; }

  void run() override;
};

// Temporarily try to set the number of active workers.
// It's not guaranteed that it succeeds, and users need to
// query the number of active workers.
class WithActiveWorkers : public StackObj {
private:
  WorkerThreads* const _workers;
  const uint           _prev_active_workers;

public:
  WithActiveWorkers(WorkerThreads* workers, uint num_workers) :
      _workers(workers),
      _prev_active_workers(workers->active_workers()) {
    _workers->set_active_workers(num_workers);
  }

  ~WithActiveWorkers() {
    _workers->set_active_workers(_prev_active_workers);
  }
};

#endif // SHARE_GC_SHARED_WORKERTHREAD_HPP
