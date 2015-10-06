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

#ifndef SHARE_VM_GC_PARALLEL_GCTASKMANAGER_HPP
#define SHARE_VM_GC_PARALLEL_GCTASKMANAGER_HPP

#include "runtime/mutex.hpp"
#include "utilities/growableArray.hpp"

//
// The GCTaskManager is a queue of GCTasks, and accessors
// to allow the queue to be accessed from many threads.
//

// Forward declarations of types defined in this file.
class GCTask;
class GCTaskQueue;
class SynchronizedGCTaskQueue;
class GCTaskManager;
// Some useful subclasses of GCTask.  You can also make up your own.
class NoopGCTask;
class WaitForBarrierGCTask;
class IdleGCTask;
// A free list of Monitor*'s.
class MonitorSupply;

// Forward declarations of classes referenced in this file via pointer.
class GCTaskThread;
class Mutex;
class Monitor;
class ThreadClosure;

// The abstract base GCTask.
class GCTask : public ResourceObj {
public:
  // Known kinds of GCTasks, for predicates.
  class Kind : AllStatic {
  public:
    enum kind {
      unknown_task,
      ordinary_task,
      wait_for_barrier_task,
      noop_task,
      idle_task
    };
    static const char* to_string(kind value);
  };
private:
  // Instance state.
  Kind::kind       _kind;               // For runtime type checking.
  uint             _affinity;           // Which worker should run task.
  GCTask*          _newer;              // Tasks are on doubly-linked ...
  GCTask*          _older;              // ... lists.
  uint             _gc_id;              // GC Id to use for the thread that executes this task
public:
  virtual char* name() { return (char *)"task"; }

  uint gc_id() { return _gc_id; }

  // Abstract do_it method
  virtual void do_it(GCTaskManager* manager, uint which) = 0;
  // Accessors
  Kind::kind kind() const {
    return _kind;
  }
  uint affinity() const {
    return _affinity;
  }
  GCTask* newer() const {
    return _newer;
  }
  void set_newer(GCTask* n) {
    _newer = n;
  }
  GCTask* older() const {
    return _older;
  }
  void set_older(GCTask* p) {
    _older = p;
  }
  // Predicates.
  bool is_ordinary_task() const {
    return kind()==Kind::ordinary_task;
  }
  bool is_barrier_task() const {
    return kind()==Kind::wait_for_barrier_task;
  }
  bool is_noop_task() const {
    return kind()==Kind::noop_task;
  }
  bool is_idle_task() const {
    return kind()==Kind::idle_task;
  }
  void print(const char* message) const PRODUCT_RETURN;
protected:
  // Constructors: Only create subclasses.
  //     An ordinary GCTask.
  GCTask();
  //     A GCTask of a particular kind, usually barrier or noop.
  GCTask(Kind::kind kind);
  GCTask(Kind::kind kind, uint gc_id);
  // We want a virtual destructor because virtual methods,
  // but since ResourceObj's don't have their destructors
  // called, we don't have one at all.  Instead we have
  // this method, which gets called by subclasses to clean up.
  virtual void destruct();
  // Methods.
  void initialize(Kind::kind kind, uint gc_id);
};

// A doubly-linked list of GCTasks.
// The list is not synchronized, because sometimes we want to
// build up a list and then make it available to other threads.
// See also: SynchronizedGCTaskQueue.
class GCTaskQueue : public ResourceObj {
private:
  // Instance state.
  GCTask*    _insert_end;               // Tasks are enqueued at this end.
  GCTask*    _remove_end;               // Tasks are dequeued from this end.
  uint       _length;                   // The current length of the queue.
  const bool _is_c_heap_obj;            // Is this a CHeapObj?
public:
  // Factory create and destroy methods.
  //     Create as ResourceObj.
  static GCTaskQueue* create();
  //     Create as CHeapObj.
  static GCTaskQueue* create_on_c_heap();
  //     Destroyer.
  static void destroy(GCTaskQueue* that);
  // Accessors.
  //     These just examine the state of the queue.
  bool is_empty() const {
    assert(((insert_end() == NULL && remove_end() == NULL) ||
            (insert_end() != NULL && remove_end() != NULL)),
           "insert_end and remove_end don't match");
    assert((insert_end() != NULL) || (_length == 0), "Not empty");
    return insert_end() == NULL;
  }
  uint length() const {
    return _length;
  }
  // Methods.
  //     Enqueue one task.
  void enqueue(GCTask* task);
  //     Enqueue a list of tasks.  Empties the argument list.
  void enqueue(GCTaskQueue* list);
  //     Dequeue one task.
  GCTask* dequeue();
  //     Dequeue one task, preferring one with affinity.
  GCTask* dequeue(uint affinity);
protected:
  // Constructor. Clients use factory, but there might be subclasses.
  GCTaskQueue(bool on_c_heap);
  // Destructor-like method.
  // Because ResourceMark doesn't call destructors.
  // This method cleans up like one.
  virtual void destruct();
  // Accessors.
  GCTask* insert_end() const {
    return _insert_end;
  }
  void set_insert_end(GCTask* value) {
    _insert_end = value;
  }
  GCTask* remove_end() const {
    return _remove_end;
  }
  void set_remove_end(GCTask* value) {
    _remove_end = value;
  }
  void increment_length() {
    _length += 1;
  }
  void decrement_length() {
    _length -= 1;
  }
  void set_length(uint value) {
    _length = value;
  }
  bool is_c_heap_obj() const {
    return _is_c_heap_obj;
  }
  // Methods.
  void initialize();
  GCTask* remove();                     // Remove from remove end.
  GCTask* remove(GCTask* task);         // Remove from the middle.
  void print(const char* message) const PRODUCT_RETURN;
  // Debug support
  void verify_length() const PRODUCT_RETURN;
};

// A GCTaskQueue that can be synchronized.
// This "has-a" GCTaskQueue and a mutex to do the exclusion.
class SynchronizedGCTaskQueue : public CHeapObj<mtGC> {
private:
  // Instance state.
  GCTaskQueue* _unsynchronized_queue;   // Has-a unsynchronized queue.
  Monitor *    _lock;                   // Lock to control access.
public:
  // Factory create and destroy methods.
  static SynchronizedGCTaskQueue* create(GCTaskQueue* queue, Monitor * lock) {
    return new SynchronizedGCTaskQueue(queue, lock);
  }
  static void destroy(SynchronizedGCTaskQueue* that) {
    if (that != NULL) {
      delete that;
    }
  }
  // Accessors
  GCTaskQueue* unsynchronized_queue() const {
    return _unsynchronized_queue;
  }
  Monitor * lock() const {
    return _lock;
  }
  // GCTaskQueue wrapper methods.
  // These check that you hold the lock
  // and then call the method on the queue.
  bool is_empty() const {
    guarantee(own_lock(), "don't own the lock");
    return unsynchronized_queue()->is_empty();
  }
  void enqueue(GCTask* task) {
    guarantee(own_lock(), "don't own the lock");
    unsynchronized_queue()->enqueue(task);
  }
  void enqueue(GCTaskQueue* list) {
    guarantee(own_lock(), "don't own the lock");
    unsynchronized_queue()->enqueue(list);
  }
  GCTask* dequeue() {
    guarantee(own_lock(), "don't own the lock");
    return unsynchronized_queue()->dequeue();
  }
  GCTask* dequeue(uint affinity) {
    guarantee(own_lock(), "don't own the lock");
    return unsynchronized_queue()->dequeue(affinity);
  }
  uint length() const {
    guarantee(own_lock(), "don't own the lock");
    return unsynchronized_queue()->length();
  }
  // For guarantees.
  bool own_lock() const {
    return lock()->owned_by_self();
  }
protected:
  // Constructor.  Clients use factory, but there might be subclasses.
  SynchronizedGCTaskQueue(GCTaskQueue* queue, Monitor * lock);
  // Destructor.  Not virtual because no virtuals.
  ~SynchronizedGCTaskQueue();
};

class WaitHelper VALUE_OBJ_CLASS_SPEC {
 private:
  Monitor*      _monitor;
  volatile bool _should_wait;
 public:
  WaitHelper();
  ~WaitHelper();
  void wait_for(bool reset);
  void notify();
  void set_should_wait(bool value) {
    _should_wait = value;
  }

  Monitor* monitor() const {
    return _monitor;
  }
  bool should_wait() const {
    return _should_wait;
  }
  void release_monitor();
};

// Dynamic number of GC threads
//
//  GC threads wait in get_task() for work (i.e., a task) to perform.
// When the number of GC threads was static, the number of tasks
// created to do a job was equal to or greater than the maximum
// number of GC threads (ParallelGCThreads).  The job might be divided
// into a number of tasks greater than the number of GC threads for
// load balancing (i.e., over partitioning).  The last task to be
// executed by a GC thread in a job is a work stealing task.  A
// GC  thread that gets a work stealing task continues to execute
// that task until the job is done.  In the static number of GC threads
// case, tasks are added to a queue (FIFO).  The work stealing tasks are
// the last to be added.  Once the tasks are added, the GC threads grab
// a task and go.  A single thread can do all the non-work stealing tasks
// and then execute a work stealing and wait for all the other GC threads
// to execute their work stealing task.
//  In the dynamic number of GC threads implementation, idle-tasks are
// created to occupy the non-participating or "inactive" threads.  An
// idle-task makes the GC thread wait on a barrier that is part of the
// GCTaskManager.  The GC threads that have been "idled" in a IdleGCTask
// are released once all the active GC threads have finished their work
// stealing tasks.  The GCTaskManager does not wait for all the "idled"
// GC threads to resume execution. When those GC threads do resume
// execution in the course of the thread scheduling, they call get_tasks()
// as all the other GC threads do.  Because all the "idled" threads are
// not required to execute in order to finish a job, it is possible for
// a GC thread to still be "idled" when the next job is started.  Such
// a thread stays "idled" for the next job.  This can result in a new
// job not having all the expected active workers.  For example if on
// job requests 4 active workers out of a total of 10 workers so the
// remaining 6 are "idled", if the next job requests 6 active workers
// but all 6 of the "idled" workers are still idle, then the next job
// will only get 4 active workers.
//  The implementation for the parallel old compaction phase has an
// added complication.  In the static case parold partitions the chunks
// ready to be filled into stacks, one for each GC thread.  A GC thread
// executing a draining task (drains the stack of ready chunks)
// claims a stack according to it's id (the unique ordinal value assigned
// to each GC thread).  In the dynamic case not all GC threads will
// actively participate so stacks with ready to fill chunks can only be
// given to the active threads.  An initial implementation chose stacks
// number 1-n to get the ready chunks and required that GC threads
// 1-n be the active workers.  This was undesirable because it required
// certain threads to participate.  In the final implementation a
// list of stacks equal in number to the active workers are filled
// with ready chunks.  GC threads that participate get a stack from
// the task (DrainStacksCompactionTask), empty the stack, and then add it to a
// recycling list at the end of the task.  If the same GC thread gets
// a second task, it gets a second stack to drain and returns it.  The
// stacks are added to a recycling list so that later stealing tasks
// for this tasks can get a stack from the recycling list.  Stealing tasks
// use the stacks in its work in a way similar to the draining tasks.
// A thread is not guaranteed to get anything but a stealing task and
// a thread that only gets a stealing task has to get a stack. A failed
// implementation tried to have the GC threads keep the stack they used
// during a draining task for later use in the stealing task but that didn't
// work because as noted a thread is not guaranteed to get a draining task.
//
// For PSScavenge and ParCompactionManager the GC threads are
// held in the GCTaskThread** _thread array in GCTaskManager.


class GCTaskManager : public CHeapObj<mtGC> {
 friend class ParCompactionManager;
 friend class PSParallelCompact;
 friend class PSScavenge;
 friend class PSRefProcTaskExecutor;
 friend class RefProcTaskExecutor;
 friend class GCTaskThread;
 friend class IdleGCTask;
private:
  // Instance state.
  const uint                _workers;           // Number of workers.
  Monitor*                  _monitor;           // Notification of changes.
  SynchronizedGCTaskQueue*  _queue;             // Queue of tasks.
  GCTaskThread**            _thread;            // Array of worker threads.
  uint                      _active_workers;    // Number of active workers.
  uint                      _busy_workers;      // Number of busy workers.
  uint                      _blocking_worker;   // The worker that's blocking.
  bool*                     _resource_flag;     // Array of flag per threads.
  uint                      _delivered_tasks;   // Count of delivered tasks.
  uint                      _completed_tasks;   // Count of completed tasks.
  uint                      _barriers;          // Count of barrier tasks.
  uint                      _emptied_queue;     // Times we emptied the queue.
  NoopGCTask*               _noop_task;         // The NoopGCTask instance.
  WaitHelper                _wait_helper;       // Used by inactive worker
  volatile uint             _idle_workers;      // Number of idled workers
public:
  // Factory create and destroy methods.
  static GCTaskManager* create(uint workers) {
    return new GCTaskManager(workers);
  }
  static void destroy(GCTaskManager* that) {
    if (that != NULL) {
      delete that;
    }
  }
  // Accessors.
  uint busy_workers() const {
    return _busy_workers;
  }
  volatile uint idle_workers() const {
    return _idle_workers;
  }
  //     Pun between Monitor* and Mutex*
  Monitor* monitor() const {
    return _monitor;
  }
  Monitor * lock() const {
    return _monitor;
  }
  WaitHelper* wait_helper() {
    return &_wait_helper;
  }
  // Methods.
  //     Add the argument task to be run.
  void add_task(GCTask* task);
  //     Add a list of tasks.  Removes task from the argument list.
  void add_list(GCTaskQueue* list);
  //     Claim a task for argument worker.
  GCTask* get_task(uint which);
  //     Note the completion of a task by the argument worker.
  void note_completion(uint which);
  //     Is the queue blocked from handing out new tasks?
  bool is_blocked() const {
    return (blocking_worker() != sentinel_worker());
  }
  //     Request that all workers release their resources.
  void release_all_resources();
  //     Ask if a particular worker should release its resources.
  bool should_release_resources(uint which); // Predicate.
  //     Note the release of resources by the argument worker.
  void note_release(uint which);
  //     Create IdleGCTasks for inactive workers and start workers
  void task_idle_workers();
  //     Release the workers in IdleGCTasks
  void release_idle_workers();
  // Constants.
  //     A sentinel worker identifier.
  static uint sentinel_worker() {
    return (uint) -1;                   // Why isn't there a max_uint?
  }

  //     Execute the task queue and wait for the completion.
  void execute_and_wait(GCTaskQueue* list);

  void print_task_time_stamps();
  void print_threads_on(outputStream* st);
  void threads_do(ThreadClosure* tc);

protected:
  // Constructors.  Clients use factory, but there might be subclasses.
  //     Create a GCTaskManager with the appropriate number of workers.
  GCTaskManager(uint workers);
  //     Make virtual if necessary.
  ~GCTaskManager();
  // Accessors.
  uint workers() const {
    return _workers;
  }
  void set_active_workers(uint v) {
    assert(v <= _workers, "Trying to set more workers active than there are");
    _active_workers = MIN2(v, _workers);
    assert(v != 0, "Trying to set active workers to 0");
    _active_workers = MAX2(1U, _active_workers);
  }
  // Sets the number of threads that will be used in a collection
  void set_active_gang();

  SynchronizedGCTaskQueue* queue() const {
    return _queue;
  }
  NoopGCTask* noop_task() const {
    return _noop_task;
  }
  //     Bounds-checking per-thread data accessors.
  GCTaskThread* thread(uint which);
  void set_thread(uint which, GCTaskThread* value);
  bool resource_flag(uint which);
  void set_resource_flag(uint which, bool value);
  // Modifier methods with some semantics.
  //     Is any worker blocking handing out new tasks?
  uint blocking_worker() const {
    return _blocking_worker;
  }
  void set_blocking_worker(uint value) {
    _blocking_worker = value;
  }
  void set_unblocked() {
    set_blocking_worker(sentinel_worker());
  }
  //     Count of busy workers.
  void reset_busy_workers() {
    _busy_workers = 0;
  }
  uint increment_busy_workers();
  uint decrement_busy_workers();
  //     Count of tasks delivered to workers.
  uint delivered_tasks() const {
    return _delivered_tasks;
  }
  void increment_delivered_tasks() {
    _delivered_tasks += 1;
  }
  void reset_delivered_tasks() {
    _delivered_tasks = 0;
  }
  //     Count of tasks completed by workers.
  uint completed_tasks() const {
    return _completed_tasks;
  }
  void increment_completed_tasks() {
    _completed_tasks += 1;
  }
  void reset_completed_tasks() {
    _completed_tasks = 0;
  }
  //     Count of barrier tasks completed.
  uint barriers() const {
    return _barriers;
  }
  void increment_barriers() {
    _barriers += 1;
  }
  void reset_barriers() {
    _barriers = 0;
  }
  //     Count of how many times the queue has emptied.
  uint emptied_queue() const {
    return _emptied_queue;
  }
  void increment_emptied_queue() {
    _emptied_queue += 1;
  }
  void reset_emptied_queue() {
    _emptied_queue = 0;
  }
  void increment_idle_workers() {
    _idle_workers++;
  }
  void decrement_idle_workers() {
    _idle_workers--;
  }
  // Other methods.
  void initialize();

 public:
  // Return true if all workers are currently active.
  bool all_workers_active() { return workers() == active_workers(); }
  uint active_workers() const {
    return _active_workers;
  }
};

//
// Some exemplary GCTasks.
//

// A noop task that does nothing,
// except take us around the GCTaskThread loop.
class NoopGCTask : public GCTask {
public:
  // Factory create and destroy methods.
  static NoopGCTask* create_on_c_heap();
  static void destroy(NoopGCTask* that);

  virtual char* name() { return (char *)"noop task"; }
  // Methods from GCTask.
  void do_it(GCTaskManager* manager, uint which) {
    // Nothing to do.
  }
protected:
  // Constructor.
  NoopGCTask();
  // Destructor-like method.
  void destruct();
};

// A WaitForBarrierGCTask is a GCTask
// with a method you can call to wait until
// the BarrierGCTask is done.
class WaitForBarrierGCTask : public GCTask {
  friend class GCTaskManager;
  friend class IdleGCTask;
private:
  // Instance state.
  WaitHelper    _wait_helper;
  WaitForBarrierGCTask();
public:
  virtual char* name() { return (char *) "waitfor-barrier-task"; }

  // Factory create and destroy methods.
  static WaitForBarrierGCTask* create();
  static void destroy(WaitForBarrierGCTask* that);
  // Methods.
  void     do_it(GCTaskManager* manager, uint which);
protected:
  // Destructor-like method.
  void destruct();

  // Methods.
  //     Wait for this to be the only task running.
  void do_it_internal(GCTaskManager* manager, uint which);

  void wait_for(bool reset) {
    _wait_helper.wait_for(reset);
  }
};

// Task that is used to idle a GC task when fewer than
// the maximum workers are wanted.
class IdleGCTask : public GCTask {
  const bool    _is_c_heap_obj;            // Was allocated on the heap.
 public:
  bool is_c_heap_obj() {
    return _is_c_heap_obj;
  }
  // Factory create and destroy methods.
  static IdleGCTask* create();
  static IdleGCTask* create_on_c_heap();
  static void destroy(IdleGCTask* that);

  virtual char* name() { return (char *)"idle task"; }
  // Methods from GCTask.
  virtual void do_it(GCTaskManager* manager, uint which);
protected:
  // Constructor.
  IdleGCTask(bool on_c_heap) :
    GCTask(GCTask::Kind::idle_task),
    _is_c_heap_obj(on_c_heap) {
    // Nothing to do.
  }
  // Destructor-like method.
  void destruct();
};

class MonitorSupply : public AllStatic {
private:
  // State.
  //     Control multi-threaded access.
  static Mutex*                   _lock;
  //     The list of available Monitor*'s.
  static GrowableArray<Monitor*>* _freelist;
public:
  // Reserve a Monitor*.
  static Monitor* reserve();
  // Release a Monitor*.
  static void release(Monitor* instance);
private:
  // Accessors.
  static Mutex* lock() {
    return _lock;
  }
  static GrowableArray<Monitor*>* freelist() {
    return _freelist;
  }
};

#endif // SHARE_VM_GC_PARALLEL_GCTASKMANAGER_HPP
