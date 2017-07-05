/*
 * Copyright (c) 2001, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/gcId.hpp"
#include "gc/shared/workgroup.hpp"
#include "memory/allocation.hpp"
#include "memory/allocation.inline.hpp"
#include "runtime/atomic.inline.hpp"
#include "runtime/os.hpp"
#include "runtime/semaphore.hpp"
#include "runtime/thread.inline.hpp"

// Definitions of WorkGang methods.

// The current implementation will exit if the allocation
// of any worker fails.  Still, return a boolean so that
// a future implementation can possibly do a partial
// initialization of the workers and report such to the
// caller.
bool AbstractWorkGang::initialize_workers() {

  if (TraceWorkGang) {
    tty->print_cr("Constructing work gang %s with %d threads",
                  name(),
                  total_workers());
  }
  _workers = NEW_C_HEAP_ARRAY(AbstractGangWorker*, total_workers(), mtInternal);
  if (_workers == NULL) {
    vm_exit_out_of_memory(0, OOM_MALLOC_ERROR, "Cannot create GangWorker array.");
    return false;
  }
  os::ThreadType worker_type;
  if (are_ConcurrentGC_threads()) {
    worker_type = os::cgc_thread;
  } else {
    worker_type = os::pgc_thread;
  }
  for (uint worker = 0; worker < total_workers(); worker += 1) {
    AbstractGangWorker* new_worker = allocate_worker(worker);
    assert(new_worker != NULL, "Failed to allocate GangWorker");
    _workers[worker] = new_worker;
    if (new_worker == NULL || !os::create_thread(new_worker, worker_type)) {
      vm_exit_out_of_memory(0, OOM_MALLOC_ERROR,
              "Cannot create worker GC thread. Out of system resources.");
      return false;
    }
    if (!DisableStartThread) {
      os::start_thread(new_worker);
    }
  }
  return true;
}

AbstractGangWorker* AbstractWorkGang::worker(uint i) const {
  // Array index bounds checking.
  AbstractGangWorker* result = NULL;
  assert(_workers != NULL, "No workers for indexing");
  assert(i < total_workers(), "Worker index out of bounds");
  result = _workers[i];
  assert(result != NULL, "Indexing to null worker");
  return result;
}

void AbstractWorkGang::print_worker_threads_on(outputStream* st) const {
  uint workers = total_workers();
  for (uint i = 0; i < workers; i++) {
    worker(i)->print_on(st);
    st->cr();
  }
}

void AbstractWorkGang::threads_do(ThreadClosure* tc) const {
  assert(tc != NULL, "Null ThreadClosure");
  uint workers = total_workers();
  for (uint i = 0; i < workers; i++) {
    tc->do_thread(worker(i));
  }
}

// WorkGang dispatcher implemented with semaphores.
//
// Semaphores don't require the worker threads to re-claim the lock when they wake up.
// This helps lowering the latency when starting and stopping the worker threads.
class SemaphoreGangTaskDispatcher : public GangTaskDispatcher {
  // The task currently being dispatched to the GangWorkers.
  AbstractGangTask* _task;

  volatile uint _started;
  volatile uint _not_finished;

  // Semaphore used to start the GangWorkers.
  Semaphore* _start_semaphore;
  // Semaphore used to notify the coordinator that all workers are done.
  Semaphore* _end_semaphore;

public:
  SemaphoreGangTaskDispatcher() :
      _task(NULL),
      _started(0),
      _not_finished(0),
      _start_semaphore(new Semaphore()),
      _end_semaphore(new Semaphore())
{ }

  ~SemaphoreGangTaskDispatcher() {
    delete _start_semaphore;
    delete _end_semaphore;
  }

  void coordinator_execute_on_workers(AbstractGangTask* task, uint num_workers) {
    // No workers are allowed to read the state variables until they have been signaled.
    _task         = task;
    _not_finished = num_workers;

    // Dispatch 'num_workers' number of tasks.
    _start_semaphore->signal(num_workers);

    // Wait for the last worker to signal the coordinator.
    _end_semaphore->wait();

    // No workers are allowed to read the state variables after the coordinator has been signaled.
    assert(_not_finished == 0, "%d not finished workers?", _not_finished);
    _task    = NULL;
    _started = 0;

  }

  WorkData worker_wait_for_task() {
    // Wait for the coordinator to dispatch a task.
    _start_semaphore->wait();

    uint num_started = (uint) Atomic::add(1, (volatile jint*)&_started);

    // Subtract one to get a zero-indexed worker id.
    uint worker_id = num_started - 1;

    return WorkData(_task, worker_id);
  }

  void worker_done_with_task() {
    // Mark that the worker is done with the task.
    // The worker is not allowed to read the state variables after this line.
    uint not_finished = (uint) Atomic::add(-1, (volatile jint*)&_not_finished);

    // The last worker signals to the coordinator that all work is completed.
    if (not_finished == 0) {
      _end_semaphore->signal();
    }
  }
};

class MutexGangTaskDispatcher : public GangTaskDispatcher {
  AbstractGangTask* _task;

  volatile uint _started;
  volatile uint _finished;
  volatile uint _num_workers;

  Monitor* _monitor;

 public:
  MutexGangTaskDispatcher()
      : _task(NULL),
        _monitor(new Monitor(Monitor::leaf, "WorkGang dispatcher lock", false, Monitor::_safepoint_check_never)),
        _started(0),
        _finished(0),
        _num_workers(0) {}

  ~MutexGangTaskDispatcher() {
    delete _monitor;
  }

  void coordinator_execute_on_workers(AbstractGangTask* task, uint num_workers) {
    MutexLockerEx ml(_monitor, Mutex::_no_safepoint_check_flag);

    _task        = task;
    _num_workers = num_workers;

    // Tell the workers to get to work.
    _monitor->notify_all();

    // Wait for them to finish.
    while (_finished < _num_workers) {
      _monitor->wait(/* no_safepoint_check */ true);
    }

    _task        = NULL;
    _num_workers = 0;
    _started     = 0;
    _finished    = 0;
  }

  WorkData worker_wait_for_task() {
    MonitorLockerEx ml(_monitor, Mutex::_no_safepoint_check_flag);

    while (_num_workers == 0 || _started == _num_workers) {
      _monitor->wait(/* no_safepoint_check */ true);
    }

    _started++;

    // Subtract one to get a zero-indexed worker id.
    uint worker_id = _started - 1;

    return WorkData(_task, worker_id);
  }

  void worker_done_with_task() {
    MonitorLockerEx ml(_monitor, Mutex::_no_safepoint_check_flag);

    _finished++;

    if (_finished == _num_workers) {
      // This will wake up all workers and not only the coordinator.
      _monitor->notify_all();
    }
  }
};

static GangTaskDispatcher* create_dispatcher() {
  if (UseSemaphoreGCThreadsSynchronization) {
    return new SemaphoreGangTaskDispatcher();
  }

  return new MutexGangTaskDispatcher();
}

WorkGang::WorkGang(const char* name,
                   uint  workers,
                   bool  are_GC_task_threads,
                   bool  are_ConcurrentGC_threads) :
    AbstractWorkGang(name, workers, are_GC_task_threads, are_ConcurrentGC_threads),
    _dispatcher(create_dispatcher())
{ }

AbstractGangWorker* WorkGang::allocate_worker(uint worker_id) {
  return new GangWorker(this, worker_id);
}

void WorkGang::run_task(AbstractGangTask* task) {
  _dispatcher->coordinator_execute_on_workers(task, active_workers());
}

AbstractGangWorker::AbstractGangWorker(AbstractWorkGang* gang, uint id) {
  _gang = gang;
  set_id(id);
  set_name("%s#%d", gang->name(), id);
}

void AbstractGangWorker::run() {
  initialize();
  loop();
}

void AbstractGangWorker::initialize() {
  this->record_stack_base_and_size();
  this->initialize_named_thread();
  assert(_gang != NULL, "No gang to run in");
  os::set_priority(this, NearMaxPriority);
  if (TraceWorkGang) {
    tty->print_cr("Running gang worker for gang %s id %u",
                  gang()->name(), id());
  }
  // The VM thread should not execute here because MutexLocker's are used
  // as (opposed to MutexLockerEx's).
  assert(!Thread::current()->is_VM_thread(), "VM thread should not be part"
         " of a work gang");
}

bool AbstractGangWorker::is_GC_task_thread() const {
  return gang()->are_GC_task_threads();
}

bool AbstractGangWorker::is_ConcurrentGC_thread() const {
  return gang()->are_ConcurrentGC_threads();
}

void AbstractGangWorker::print_on(outputStream* st) const {
  st->print("\"%s\" ", name());
  Thread::print_on(st);
  st->cr();
}

WorkData GangWorker::wait_for_task() {
  return gang()->dispatcher()->worker_wait_for_task();
}

void GangWorker::signal_task_done() {
  gang()->dispatcher()->worker_done_with_task();
}

void GangWorker::print_task_started(WorkData data) {
  if (TraceWorkGang) {
    tty->print_cr("Running work gang %s task %s worker %u", name(), data._task->name(), data._worker_id);
  }
}

void GangWorker::print_task_done(WorkData data) {
  if (TraceWorkGang) {
    tty->print_cr("\nFinished work gang %s task %s worker %u", name(), data._task->name(), data._worker_id);
    Thread* me = Thread::current();
    tty->print_cr("  T: " PTR_FORMAT "  VM_thread: %d", p2i(me), me->is_VM_thread());
  }
}

void GangWorker::run_task(WorkData data) {
  print_task_started(data);

  GCIdMark gc_id_mark(data._task->gc_id());
  data._task->work(data._worker_id);

  print_task_done(data);
}

void GangWorker::loop() {
  while (true) {
    WorkData data = wait_for_task();

    run_task(data);

    signal_task_done();
  }
}

// *** WorkGangBarrierSync

WorkGangBarrierSync::WorkGangBarrierSync()
  : _monitor(Mutex::safepoint, "work gang barrier sync", true,
             Monitor::_safepoint_check_never),
    _n_workers(0), _n_completed(0), _should_reset(false), _aborted(false) {
}

WorkGangBarrierSync::WorkGangBarrierSync(uint n_workers, const char* name)
  : _monitor(Mutex::safepoint, name, true, Monitor::_safepoint_check_never),
    _n_workers(n_workers), _n_completed(0), _should_reset(false), _aborted(false) {
}

void WorkGangBarrierSync::set_n_workers(uint n_workers) {
  _n_workers    = n_workers;
  _n_completed  = 0;
  _should_reset = false;
  _aborted      = false;
}

bool WorkGangBarrierSync::enter() {
  MutexLockerEx x(monitor(), Mutex::_no_safepoint_check_flag);
  if (should_reset()) {
    // The should_reset() was set and we are the first worker to enter
    // the sync barrier. We will zero the n_completed() count which
    // effectively resets the barrier.
    zero_completed();
    set_should_reset(false);
  }
  inc_completed();
  if (n_completed() == n_workers()) {
    // At this point we would like to reset the barrier to be ready in
    // case it is used again. However, we cannot set n_completed() to
    // 0, even after the notify_all(), given that some other workers
    // might still be waiting for n_completed() to become ==
    // n_workers(). So, if we set n_completed() to 0, those workers
    // will get stuck (as they will wake up, see that n_completed() !=
    // n_workers() and go back to sleep). Instead, we raise the
    // should_reset() flag and the barrier will be reset the first
    // time a worker enters it again.
    set_should_reset(true);
    monitor()->notify_all();
  } else {
    while (n_completed() != n_workers() && !aborted()) {
      monitor()->wait(/* no_safepoint_check */ true);
    }
  }
  return !aborted();
}

void WorkGangBarrierSync::abort() {
  MutexLockerEx x(monitor(), Mutex::_no_safepoint_check_flag);
  set_aborted();
  monitor()->notify_all();
}

// SubTasksDone functions.

SubTasksDone::SubTasksDone(uint n) :
  _n_tasks(n), _tasks(NULL) {
  _tasks = NEW_C_HEAP_ARRAY(uint, n, mtInternal);
  guarantee(_tasks != NULL, "alloc failure");
  clear();
}

bool SubTasksDone::valid() {
  return _tasks != NULL;
}

void SubTasksDone::clear() {
  for (uint i = 0; i < _n_tasks; i++) {
    _tasks[i] = 0;
  }
  _threads_completed = 0;
#ifdef ASSERT
  _claimed = 0;
#endif
}

bool SubTasksDone::is_task_claimed(uint t) {
  assert(t < _n_tasks, "bad task id.");
  uint old = _tasks[t];
  if (old == 0) {
    old = Atomic::cmpxchg(1, &_tasks[t], 0);
  }
  assert(_tasks[t] == 1, "What else?");
  bool res = old != 0;
#ifdef ASSERT
  if (!res) {
    assert(_claimed < _n_tasks, "Too many tasks claimed; missing clear?");
    Atomic::inc((volatile jint*) &_claimed);
  }
#endif
  return res;
}

void SubTasksDone::all_tasks_completed(uint n_threads) {
  jint observed = _threads_completed;
  jint old;
  do {
    old = observed;
    observed = Atomic::cmpxchg(old+1, &_threads_completed, old);
  } while (observed != old);
  // If this was the last thread checking in, clear the tasks.
  uint adjusted_thread_count = (n_threads == 0 ? 1 : n_threads);
  if (observed + 1 == (jint)adjusted_thread_count) {
    clear();
  }
}


SubTasksDone::~SubTasksDone() {
  if (_tasks != NULL) FREE_C_HEAP_ARRAY(jint, _tasks);
}

// *** SequentialSubTasksDone

void SequentialSubTasksDone::clear() {
  _n_tasks   = _n_claimed   = 0;
  _n_threads = _n_completed = 0;
}

bool SequentialSubTasksDone::valid() {
  return _n_threads > 0;
}

bool SequentialSubTasksDone::is_task_claimed(uint& t) {
  uint* n_claimed_ptr = &_n_claimed;
  t = *n_claimed_ptr;
  while (t < _n_tasks) {
    jint res = Atomic::cmpxchg(t+1, n_claimed_ptr, t);
    if (res == (jint)t) {
      return false;
    }
    t = *n_claimed_ptr;
  }
  return true;
}

bool SequentialSubTasksDone::all_tasks_completed() {
  uint* n_completed_ptr = &_n_completed;
  uint  complete        = *n_completed_ptr;
  while (true) {
    uint res = Atomic::cmpxchg(complete+1, n_completed_ptr, complete);
    if (res == complete) {
      break;
    }
    complete = res;
  }
  if (complete+1 == _n_threads) {
    clear();
    return true;
  }
  return false;
}

bool FreeIdSet::_stat_init = false;
FreeIdSet* FreeIdSet::_sets[NSets];
bool FreeIdSet::_safepoint;

FreeIdSet::FreeIdSet(int sz, Monitor* mon) :
  _sz(sz), _mon(mon), _hd(0), _waiters(0), _index(-1), _claimed(0)
{
  _ids = NEW_C_HEAP_ARRAY(int, sz, mtInternal);
  for (int i = 0; i < sz; i++) _ids[i] = i+1;
  _ids[sz-1] = end_of_list; // end of list.
  if (_stat_init) {
    for (int j = 0; j < NSets; j++) _sets[j] = NULL;
    _stat_init = true;
  }
  // Add to sets.  (This should happen while the system is still single-threaded.)
  for (int j = 0; j < NSets; j++) {
    if (_sets[j] == NULL) {
      _sets[j] = this;
      _index = j;
      break;
    }
  }
  guarantee(_index != -1, "Too many FreeIdSets in use!");
}

FreeIdSet::~FreeIdSet() {
  _sets[_index] = NULL;
  FREE_C_HEAP_ARRAY(int, _ids);
}

void FreeIdSet::set_safepoint(bool b) {
  _safepoint = b;
  if (b) {
    for (int j = 0; j < NSets; j++) {
      if (_sets[j] != NULL && _sets[j]->_waiters > 0) {
        Monitor* mon = _sets[j]->_mon;
        mon->lock_without_safepoint_check();
        mon->notify_all();
        mon->unlock();
      }
    }
  }
}

#define FID_STATS 0

int FreeIdSet::claim_par_id() {
#if FID_STATS
  thread_t tslf = thr_self();
  tty->print("claim_par_id[%d]: sz = %d, claimed = %d\n", tslf, _sz, _claimed);
#endif
  MutexLockerEx x(_mon, Mutex::_no_safepoint_check_flag);
  while (!_safepoint && _hd == end_of_list) {
    _waiters++;
#if FID_STATS
    if (_waiters > 5) {
      tty->print("claim_par_id waiting[%d]: %d waiters, %d claimed.\n",
                 tslf, _waiters, _claimed);
    }
#endif
    _mon->wait(Mutex::_no_safepoint_check_flag);
    _waiters--;
  }
  if (_hd == end_of_list) {
#if FID_STATS
    tty->print("claim_par_id[%d]: returning EOL.\n", tslf);
#endif
    return -1;
  } else {
    int res = _hd;
    _hd = _ids[res];
    _ids[res] = claimed;  // For debugging.
    _claimed++;
#if FID_STATS
    tty->print("claim_par_id[%d]: returning %d, claimed = %d.\n",
               tslf, res, _claimed);
#endif
    return res;
  }
}

bool FreeIdSet::claim_perm_id(int i) {
  assert(0 <= i && i < _sz, "Out of range.");
  MutexLockerEx x(_mon, Mutex::_no_safepoint_check_flag);
  int prev = end_of_list;
  int cur = _hd;
  while (cur != end_of_list) {
    if (cur == i) {
      if (prev == end_of_list) {
        _hd = _ids[cur];
      } else {
        _ids[prev] = _ids[cur];
      }
      _ids[cur] = claimed;
      _claimed++;
      return true;
    } else {
      prev = cur;
      cur = _ids[cur];
    }
  }
  return false;

}

void FreeIdSet::release_par_id(int id) {
  MutexLockerEx x(_mon, Mutex::_no_safepoint_check_flag);
  assert(_ids[id] == claimed, "Precondition.");
  _ids[id] = _hd;
  _hd = id;
  _claimed--;
#if FID_STATS
  tty->print("[%d] release_par_id(%d), waiters =%d,  claimed = %d.\n",
             thr_self(), id, _waiters, _claimed);
#endif
  if (_waiters > 0)
    // Notify all would be safer, but this is OK, right?
    _mon->notify_all();
}
