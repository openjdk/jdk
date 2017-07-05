/*
 * Copyright (c) 2002, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_UTILITIES_WORKGROUP_HPP
#define SHARE_VM_UTILITIES_WORKGROUP_HPP

#include "runtime/thread.inline.hpp"
#include "utilities/taskqueue.hpp"

// Task class hierarchy:
//   AbstractGangTask
//     AbstractGangTaskWOopQueues
//
// Gang/Group class hierarchy:
//   AbstractWorkGang
//     WorkGang
//       FlexibleWorkGang
//         YieldingFlexibleWorkGang (defined in another file)
//
// Worker class hierarchy:
//   GangWorker (subclass of WorkerThread)
//     YieldingFlexibleGangWorker   (defined in another file)

// Forward declarations of classes defined here

class WorkGang;
class GangWorker;
class YieldingFlexibleGangWorker;
class YieldingFlexibleGangTask;
class WorkData;
class AbstractWorkGang;

// An abstract task to be worked on by a gang.
// You subclass this to supply your own work() method
class AbstractGangTask VALUE_OBJ_CLASS_SPEC {
public:
  // The abstract work method.
  // The argument tells you which member of the gang you are.
  virtual void work(uint worker_id) = 0;

  // This method configures the task for proper termination.
  // Some tasks do not have any requirements on termination
  // and may inherit this method that does nothing.  Some
  // tasks do some coordination on termination and override
  // this method to implement that coordination.
  virtual void set_for_termination(int active_workers) {};

  // Debugging accessor for the name.
  const char* name() const PRODUCT_RETURN_(return NULL;);
  int counter() { return _counter; }
  void set_counter(int value) { _counter = value; }
  int *address_of_counter() { return &_counter; }

  // RTTI
  NOT_PRODUCT(virtual bool is_YieldingFlexibleGang_task() const {
    return false;
  })

private:
  NOT_PRODUCT(const char* _name;)
  // ??? Should a task have a priority associated with it?
  // ??? Or can the run method adjust priority as needed?
  int _counter;

protected:
  // Constructor and desctructor: only construct subclasses.
  AbstractGangTask(const char* name)
  {
    NOT_PRODUCT(_name = name);
    _counter = 0;
  }
  ~AbstractGangTask() { }

public:
};

class AbstractGangTaskWOopQueues : public AbstractGangTask {
  OopTaskQueueSet*       _queues;
  ParallelTaskTerminator _terminator;
 public:
  AbstractGangTaskWOopQueues(const char* name, OopTaskQueueSet* queues) :
    AbstractGangTask(name), _queues(queues), _terminator(0, _queues) {}
  ParallelTaskTerminator* terminator() { return &_terminator; }
  virtual void set_for_termination(int active_workers) {
    terminator()->reset_for_reuse(active_workers);
  }
  OopTaskQueueSet* queues() { return _queues; }
};


// Class AbstractWorkGang:
// An abstract class representing a gang of workers.
// You subclass this to supply an implementation of run_task().
class AbstractWorkGang: public CHeapObj<mtInternal> {
  // Here's the public interface to this class.
public:
  // Constructor and destructor.
  AbstractWorkGang(const char* name, bool are_GC_task_threads,
                   bool are_ConcurrentGC_threads);
  ~AbstractWorkGang();
  // Run a task, returns when the task is done (or terminated).
  virtual void run_task(AbstractGangTask* task) = 0;
  // Stop and terminate all workers.
  virtual void stop();
  // Return true if more workers should be applied to the task.
  virtual bool needs_more_workers() const { return true; }
public:
  // Debugging.
  const char* name() const;
protected:
  // Initialize only instance data.
  const bool _are_GC_task_threads;
  const bool _are_ConcurrentGC_threads;
  // Printing support.
  const char* _name;
  // The monitor which protects these data,
  // and notifies of changes in it.
  Monitor*  _monitor;
  // The count of the number of workers in the gang.
  uint _total_workers;
  // Whether the workers should terminate.
  bool _terminate;
  // The array of worker threads for this gang.
  // This is only needed for cleaning up.
  GangWorker** _gang_workers;
  // The task for this gang.
  AbstractGangTask* _task;
  // A sequence number for the current task.
  int _sequence_number;
  // The number of started workers.
  uint _started_workers;
  // The number of finished workers.
  uint _finished_workers;
public:
  // Accessors for fields
  Monitor* monitor() const {
    return _monitor;
  }
  uint total_workers() const {
    return _total_workers;
  }
  virtual uint active_workers() const {
    return _total_workers;
  }
  bool terminate() const {
    return _terminate;
  }
  GangWorker** gang_workers() const {
    return _gang_workers;
  }
  AbstractGangTask* task() const {
    return _task;
  }
  int sequence_number() const {
    return _sequence_number;
  }
  uint started_workers() const {
    return _started_workers;
  }
  uint finished_workers() const {
    return _finished_workers;
  }
  bool are_GC_task_threads() const {
    return _are_GC_task_threads;
  }
  bool are_ConcurrentGC_threads() const {
    return _are_ConcurrentGC_threads;
  }
  // Predicates.
  bool is_idle() const {
    return (task() == NULL);
  }
  // Return the Ith gang worker.
  GangWorker* gang_worker(uint i) const;

  void threads_do(ThreadClosure* tc) const;

  // Printing
  void print_worker_threads_on(outputStream *st) const;
  void print_worker_threads() const {
    print_worker_threads_on(tty);
  }

protected:
  friend class GangWorker;
  friend class YieldingFlexibleGangWorker;
  // Note activation and deactivation of workers.
  // These methods should only be called with the mutex held.
  void internal_worker_poll(WorkData* data) const;
  void internal_note_start();
  void internal_note_finish();
};

class WorkData: public StackObj {
  // This would be a struct, but I want accessor methods.
private:
  bool              _terminate;
  AbstractGangTask* _task;
  int               _sequence_number;
public:
  // Constructor and destructor
  WorkData() {
    _terminate       = false;
    _task            = NULL;
    _sequence_number = 0;
  }
  ~WorkData() {
  }
  // Accessors and modifiers
  bool terminate()                       const { return _terminate;  }
  void set_terminate(bool value)               { _terminate = value; }
  AbstractGangTask* task()               const { return _task; }
  void set_task(AbstractGangTask* value)       { _task = value; }
  int sequence_number()                  const { return _sequence_number; }
  void set_sequence_number(int value)          { _sequence_number = value; }

  YieldingFlexibleGangTask* yf_task()    const {
    return (YieldingFlexibleGangTask*)_task;
  }
};

// Class WorkGang:
class WorkGang: public AbstractWorkGang {
public:
  // Constructor
  WorkGang(const char* name, uint workers,
           bool are_GC_task_threads, bool are_ConcurrentGC_threads);
  // Run a task, returns when the task is done (or terminated).
  virtual void run_task(AbstractGangTask* task);
  void run_task(AbstractGangTask* task, uint no_of_parallel_workers);
  // Allocate a worker and return a pointer to it.
  virtual GangWorker* allocate_worker(uint which);
  // Initialize workers in the gang.  Return true if initialization
  // succeeded. The type of the worker can be overridden in a derived
  // class with the appropriate implementation of allocate_worker().
  bool initialize_workers();
};

// Class GangWorker:
//   Several instances of this class run in parallel as workers for a gang.
class GangWorker: public WorkerThread {
public:
  // Constructors and destructor.
  GangWorker(AbstractWorkGang* gang, uint id);

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
  virtual void loop();

public:
  AbstractWorkGang* gang() const { return _gang; }
};

// Dynamic number of worker threads
//
// This type of work gang is used to run different numbers of
// worker threads at different times.  The
// number of workers run for a task is "_active_workers"
// instead of "_total_workers" in a WorkGang.  The method
// "needs_more_workers()" returns true until "_active_workers"
// have been started and returns false afterwards.  The
// implementation of "needs_more_workers()" in WorkGang always
// returns true so that all workers are started.  The method
// "loop()" in GangWorker was modified to ask "needs_more_workers()"
// in its loop to decide if it should start working on a task.
// A worker in "loop()" waits for notification on the WorkGang
// monitor and execution of each worker as it checks for work
// is serialized via the same monitor.  The "needs_more_workers()"
// call is serialized and additionally the calculation for the
// "part" (effectively the worker id for executing the task) is
// serialized to give each worker a unique "part".  Workers that
// are not needed for this tasks (i.e., "_active_workers" have
// been started before it, continue to wait for work.

class FlexibleWorkGang: public WorkGang {
  // The currently active workers in this gang.
  // This is a number that is dynamically adjusted
  // and checked in the run_task() method at each invocation.
  // As described above _active_workers determines the number
  // of threads started on a task.  It must also be used to
  // determine completion.

 protected:
  uint _active_workers;
 public:
  // Constructor and destructor.
  // Initialize active_workers to a minimum value.  Setting it to
  // the parameter "workers" will initialize it to a maximum
  // value which is not desirable.
  FlexibleWorkGang(const char* name, uint workers,
                   bool are_GC_task_threads,
                   bool  are_ConcurrentGC_threads) :
    WorkGang(name, workers, are_GC_task_threads, are_ConcurrentGC_threads),
    _active_workers(UseDynamicNumberOfGCThreads ? 1U : ParallelGCThreads) {}
  // Accessors for fields
  virtual uint active_workers() const { return _active_workers; }
  void set_active_workers(uint v) {
    assert(v <= _total_workers,
           "Trying to set more workers active than there are");
    _active_workers = MIN2(v, _total_workers);
    assert(v != 0, "Trying to set active workers to 0");
    _active_workers = MAX2(1U, _active_workers);
    assert(UseDynamicNumberOfGCThreads || _active_workers == _total_workers,
           "Unless dynamic should use total workers");
  }
  virtual void run_task(AbstractGangTask* task);
  virtual bool needs_more_workers() const {
    return _started_workers < _active_workers;
  }
};

// Work gangs in garbage collectors: 2009-06-10
//
// SharedHeap - work gang for stop-the-world parallel collection.
//   Used by
//     ParNewGeneration
//     CMSParRemarkTask
//     CMSRefProcTaskExecutor
//     G1CollectedHeap
//     G1ParFinalCountTask
// ConcurrentMark
// CMSCollector

// A class that acts as a synchronisation barrier. Workers enter
// the barrier and must wait until all other workers have entered
// before any of them may leave.

class WorkGangBarrierSync : public StackObj {
protected:
  Monitor _monitor;
  uint     _n_workers;
  uint     _n_completed;
  bool    _should_reset;

  Monitor* monitor()        { return &_monitor; }
  uint     n_workers()      { return _n_workers; }
  uint     n_completed()    { return _n_completed; }
  bool     should_reset()   { return _should_reset; }

  void     zero_completed() { _n_completed = 0; }
  void     inc_completed()  { _n_completed++; }

  void     set_should_reset(bool v) { _should_reset = v; }

public:
  WorkGangBarrierSync();
  WorkGangBarrierSync(uint n_workers, const char* name);

  // Set the number of workers that will use the barrier.
  // Must be called before any of the workers start running.
  void set_n_workers(uint n_workers);

  // Enter the barrier. A worker that enters the barrier will
  // not be allowed to leave until all other threads have
  // also entered the barrier.
  void enter();
};

// A class to manage claiming of subtasks within a group of tasks.  The
// subtasks will be identified by integer indices, usually elements of an
// enumeration type.

class SubTasksDone: public CHeapObj<mtInternal> {
  uint* _tasks;
  uint _n_tasks;
  // _n_threads is used to determine when a sub task is done.
  // It does not control how many threads will execute the subtask
  // but must be initialized to the number that do execute the task
  // in order to correctly decide when the subtask is done (all the
  // threads working on the task have finished).
  uint _n_threads;
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

  // Get/set the number of parallel threads doing the tasks to "t".  Can only
  // be called before tasks start or after they are complete.
  uint n_threads() { return _n_threads; }
  void set_n_threads(uint t);

  // Returns "false" if the task "t" is unclaimed, and ensures that task is
  // claimed.  The task "t" is required to be within the range of "this".
  bool is_task_claimed(uint t);

  // The calling thread asserts that it has attempted to claim all the
  // tasks that it will try to claim.  Every thread in the parallel task
  // must execute this.  (When the last thread does so, the task array is
  // cleared.)
  void all_tasks_completed();

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

// Represents a set of free small integer ids.
class FreeIdSet : public CHeapObj<mtInternal> {
  enum {
    end_of_list = -1,
    claimed = -2
  };

  int _sz;
  Monitor* _mon;

  int* _ids;
  int _hd;
  int _waiters;
  int _claimed;

  static bool _safepoint;
  typedef FreeIdSet* FreeIdSetPtr;
  static const int NSets = 10;
  static FreeIdSetPtr _sets[NSets];
  static bool _stat_init;
  int _index;

public:
  FreeIdSet(int sz, Monitor* mon);
  ~FreeIdSet();

  static void set_safepoint(bool b);

  // Attempt to claim the given id permanently.  Returns "true" iff
  // successful.
  bool claim_perm_id(int i);

  // Returns an unclaimed parallel id (waiting for one to be released if
  // necessary).  Returns "-1" if a GC wakes up a wait for an id.
  int claim_par_id();

  void release_par_id(int id);
};

#endif // SHARE_VM_UTILITIES_WORKGROUP_HPP
