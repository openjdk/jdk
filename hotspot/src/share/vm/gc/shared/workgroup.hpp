/*
 * Copyright (c) 2002, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_SHARED_WORKGROUP_HPP
#define SHARE_VM_GC_SHARED_WORKGROUP_HPP

#include "memory/allocation.hpp"
#include "runtime/globals.hpp"
#include "runtime/thread.hpp"
#include "gc/shared/gcId.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

// Task class hierarchy:
//   AbstractGangTask
//
// Gang/Group class hierarchy:
//   AbstractWorkGang
//     WorkGang
//     YieldingFlexibleWorkGang (defined in another file)
//
// Worker class hierarchy:
//   AbstractGangWorker (subclass of WorkerThread)
//     GangWorker
//     YieldingFlexibleGangWorker   (defined in another file)

// Forward declarations of classes defined here

class AbstractGangWorker;
class Semaphore;
class WorkGang;

// An abstract task to be worked on by a gang.
// You subclass this to supply your own work() method
class AbstractGangTask VALUE_OBJ_CLASS_SPEC {
  const char* _name;
  const uint _gc_id;

 public:
  AbstractGangTask(const char* name) :
    _name(name),
    _gc_id(GCId::current_raw())
 {}

  // The abstract work method.
  // The argument tells you which member of the gang you are.
  virtual void work(uint worker_id) = 0;

  // Debugging accessor for the name.
  const char* name() const { return _name; }
  const uint gc_id() const { return _gc_id; }
};

struct WorkData {
  AbstractGangTask* _task;
  uint              _worker_id;
  WorkData(AbstractGangTask* task, uint worker_id) : _task(task), _worker_id(worker_id) {}
};

// Interface to handle the synchronization between the coordinator thread and the worker threads,
// when a task is dispatched out to the worker threads.
class GangTaskDispatcher : public CHeapObj<mtGC> {
 public:
  virtual ~GangTaskDispatcher() {}

  // Coordinator API.

  // Distributes the task out to num_workers workers.
  // Returns when the task has been completed by all workers.
  virtual void coordinator_execute_on_workers(AbstractGangTask* task, uint num_workers) = 0;

  // Worker API.

  // Waits for a task to become available to the worker.
  // Returns when the worker has been assigned a task.
  virtual WorkData worker_wait_for_task() = 0;

  // Signal to the coordinator that the worker is done with the assigned task.
  virtual void     worker_done_with_task() = 0;
};

// The work gang is the collection of workers to execute tasks.
// The number of workers run for a task is "_active_workers"
// while "_total_workers" is the number of available of workers.
class AbstractWorkGang : public CHeapObj<mtInternal> {
 protected:
  // The array of worker threads for this gang.
  AbstractGangWorker** _workers;
  // The count of the number of workers in the gang.
  uint _total_workers;
  // The currently active workers in this gang.
  uint _active_workers;
  // Printing support.
  const char* _name;

 private:
  // Initialize only instance data.
  const bool _are_GC_task_threads;
  const bool _are_ConcurrentGC_threads;

 public:
  AbstractWorkGang(const char* name, uint workers, bool are_GC_task_threads, bool are_ConcurrentGC_threads) :
      _name(name),
      _total_workers(workers),
      _active_workers(UseDynamicNumberOfGCThreads ? 1U : workers),
      _are_GC_task_threads(are_GC_task_threads),
      _are_ConcurrentGC_threads(are_ConcurrentGC_threads)
  { }

  // Initialize workers in the gang.  Return true if initialization succeeded.
  bool initialize_workers();

  bool are_GC_task_threads()      const { return _are_GC_task_threads; }
  bool are_ConcurrentGC_threads() const { return _are_ConcurrentGC_threads; }

  uint total_workers() const { return _total_workers; }

  virtual uint active_workers() const {
    assert(_active_workers <= _total_workers,
           "_active_workers: %u > _total_workers: %u", _active_workers, _total_workers);
    assert(UseDynamicNumberOfGCThreads || _active_workers == _total_workers,
           "Unless dynamic should use total workers");
    return _active_workers;
  }
  void set_active_workers(uint v) {
    assert(v <= _total_workers,
           "Trying to set more workers active than there are");
    _active_workers = MIN2(v, _total_workers);
    assert(v != 0, "Trying to set active workers to 0");
    _active_workers = MAX2(1U, _active_workers);
    assert(UseDynamicNumberOfGCThreads || _active_workers == _total_workers,
           "Unless dynamic should use total workers");
  }

  // Return the Ith worker.
  AbstractGangWorker* worker(uint i) const;

  void threads_do(ThreadClosure* tc) const;

  // Debugging.
  const char* name() const { return _name; }

  // Printing
  void print_worker_threads_on(outputStream *st) const;
  void print_worker_threads() const {
    print_worker_threads_on(tty);
  }

 protected:
  virtual AbstractGangWorker* allocate_worker(uint which) = 0;
};

// An class representing a gang of workers.
class WorkGang: public AbstractWorkGang {
  // To get access to the GangTaskDispatcher instance.
  friend class GangWorker;

  // Never deleted.
  ~WorkGang();

  GangTaskDispatcher* const _dispatcher;
  GangTaskDispatcher* dispatcher() const {
    return _dispatcher;
  }

public:
  WorkGang(const char* name,
           uint workers,
           bool are_GC_task_threads,
           bool are_ConcurrentGC_threads);

  // Run a task, returns when the task is done.
  virtual void run_task(AbstractGangTask* task);

protected:
  virtual AbstractGangWorker* allocate_worker(uint which);
};

// Several instances of this class run in parallel as workers for a gang.
class AbstractGangWorker: public WorkerThread {
public:
  AbstractGangWorker(AbstractWorkGang* gang, uint id);

  // The only real method: run a task for the gang.
  virtual void run();
  // Predicate for Thread
  virtual bool is_GC_task_thread() const;
  virtual bool is_ConcurrentGC_thread() const;
  // Printing
  void print_on(outputStream* st) const;
  virtual void print() const { print_on(tty); }

protected:
  AbstractWorkGang* _gang;

  virtual void initialize();
  virtual void loop() = 0;

  AbstractWorkGang* gang() const { return _gang; }
};

class GangWorker: public AbstractGangWorker {
public:
  GangWorker(WorkGang* gang, uint id) : AbstractGangWorker(gang, id) {}

protected:
  virtual void loop();

private:
  WorkData wait_for_task();
  void run_task(WorkData work);
  void signal_task_done();

  void print_task_started(WorkData data);
  void print_task_done(WorkData data);

  WorkGang* gang() const { return (WorkGang*)_gang; }
};

// A class that acts as a synchronisation barrier. Workers enter
// the barrier and must wait until all other workers have entered
// before any of them may leave.

class WorkGangBarrierSync : public StackObj {
protected:
  Monitor _monitor;
  uint    _n_workers;
  uint    _n_completed;
  bool    _should_reset;
  bool    _aborted;

  Monitor* monitor()        { return &_monitor; }
  uint     n_workers()      { return _n_workers; }
  uint     n_completed()    { return _n_completed; }
  bool     should_reset()   { return _should_reset; }
  bool     aborted()        { return _aborted; }

  void     zero_completed() { _n_completed = 0; }
  void     inc_completed()  { _n_completed++; }
  void     set_aborted()    { _aborted = true; }
  void     set_should_reset(bool v) { _should_reset = v; }

public:
  WorkGangBarrierSync();
  WorkGangBarrierSync(uint n_workers, const char* name);

  // Set the number of workers that will use the barrier.
  // Must be called before any of the workers start running.
  void set_n_workers(uint n_workers);

  // Enter the barrier. A worker that enters the barrier will
  // not be allowed to leave until all other threads have
  // also entered the barrier or the barrier is aborted.
  // Returns false if the barrier was aborted.
  bool enter();

  // Aborts the barrier and wakes up any threads waiting for
  // the barrier to complete. The barrier will remain in the
  // aborted state until the next call to set_n_workers().
  void abort();
};

// A class to manage claiming of subtasks within a group of tasks.  The
// subtasks will be identified by integer indices, usually elements of an
// enumeration type.

class SubTasksDone: public CHeapObj<mtInternal> {
  uint* _tasks;
  uint _n_tasks;
  uint _threads_completed;
#ifdef ASSERT
  volatile uint _claimed;
#endif

  // Set all tasks to unclaimed.
  void clear();

public:
  // Initializes "this" to a state in which there are "n" tasks to be
  // processed, none of the which are originally claimed.  The number of
  // threads doing the tasks is initialized 1.
  SubTasksDone(uint n);

  // True iff the object is in a valid state.
  bool valid();

  // Returns "false" if the task "t" is unclaimed, and ensures that task is
  // claimed.  The task "t" is required to be within the range of "this".
  bool is_task_claimed(uint t);

  // The calling thread asserts that it has attempted to claim all the
  // tasks that it will try to claim.  Every thread in the parallel task
  // must execute this.  (When the last thread does so, the task array is
  // cleared.)
  //
  // n_threads - Number of threads executing the sub-tasks.
  void all_tasks_completed(uint n_threads);

  // Destructor.
  ~SubTasksDone();
};

// As above, but for sequential tasks, i.e. instead of claiming
// sub-tasks from a set (possibly an enumeration), claim sub-tasks
// in sequential order. This is ideal for claiming dynamically
// partitioned tasks (like striding in the parallel remembered
// set scanning). Note that unlike the above class this is
// a stack object - is there any reason for it not to be?

class SequentialSubTasksDone : public StackObj {
protected:
  uint _n_tasks;     // Total number of tasks available.
  uint _n_claimed;   // Number of tasks claimed.
  // _n_threads is used to determine when a sub task is done.
  // See comments on SubTasksDone::_n_threads
  uint _n_threads;   // Total number of parallel threads.
  uint _n_completed; // Number of completed threads.

  void clear();

public:
  SequentialSubTasksDone() {
    clear();
  }
  ~SequentialSubTasksDone() {}

  // True iff the object is in a valid state.
  bool valid();

  // number of tasks
  uint n_tasks() const { return _n_tasks; }

  // Get/set the number of parallel threads doing the tasks to t.
  // Should be called before the task starts but it is safe
  // to call this once a task is running provided that all
  // threads agree on the number of threads.
  uint n_threads() { return _n_threads; }
  void set_n_threads(uint t) { _n_threads = t; }

  // Set the number of tasks to be claimed to t. As above,
  // should be called before the tasks start but it is safe
  // to call this once a task is running provided all threads
  // agree on the number of tasks.
  void set_n_tasks(uint t) { _n_tasks = t; }

  // Returns false if the next task in the sequence is unclaimed,
  // and ensures that it is claimed. Will set t to be the index
  // of the claimed task in the sequence. Will return true if
  // the task cannot be claimed and there are none left to claim.
  bool is_task_claimed(uint& t);

  // The calling thread asserts that it has attempted to claim
  // all the tasks it possibly can in the sequence. Every thread
  // claiming tasks must promise call this. Returns true if this
  // is the last thread to complete so that the thread can perform
  // cleanup if necessary.
  bool all_tasks_completed();
};

#endif // SHARE_VM_GC_SHARED_WORKGROUP_HPP
