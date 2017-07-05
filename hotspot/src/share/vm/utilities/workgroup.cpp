/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/allocation.hpp"
#include "memory/allocation.inline.hpp"
#include "runtime/os.hpp"
#include "utilities/workgroup.hpp"

// Definitions of WorkGang methods.

AbstractWorkGang::AbstractWorkGang(const char* name,
                                   bool  are_GC_task_threads,
                                   bool  are_ConcurrentGC_threads) :
  _name(name),
  _are_GC_task_threads(are_GC_task_threads),
  _are_ConcurrentGC_threads(are_ConcurrentGC_threads) {

  assert(!(are_GC_task_threads && are_ConcurrentGC_threads),
         "They cannot both be STW GC and Concurrent threads" );

  // Other initialization.
  _monitor = new Monitor(/* priority */       Mutex::leaf,
                         /* name */           "WorkGroup monitor",
                         /* allow_vm_block */ are_GC_task_threads);
  assert(monitor() != NULL, "Failed to allocate monitor");
  _terminate = false;
  _task = NULL;
  _sequence_number = 0;
  _started_workers = 0;
  _finished_workers = 0;
}

WorkGang::WorkGang(const char* name,
                   uint        workers,
                   bool        are_GC_task_threads,
                   bool        are_ConcurrentGC_threads) :
  AbstractWorkGang(name, are_GC_task_threads, are_ConcurrentGC_threads) {
  _total_workers = workers;
}

GangWorker* WorkGang::allocate_worker(uint which) {
  GangWorker* new_worker = new GangWorker(this, which);
  return new_worker;
}

// The current implementation will exit if the allocation
// of any worker fails.  Still, return a boolean so that
// a future implementation can possibly do a partial
// initialization of the workers and report such to the
// caller.
bool WorkGang::initialize_workers() {

  if (TraceWorkGang) {
    tty->print_cr("Constructing work gang %s with %d threads",
                  name(),
                  total_workers());
  }
  _gang_workers = NEW_C_HEAP_ARRAY(GangWorker*, total_workers(), mtInternal);
  if (gang_workers() == NULL) {
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
    GangWorker* new_worker = allocate_worker(worker);
    assert(new_worker != NULL, "Failed to allocate GangWorker");
    _gang_workers[worker] = new_worker;
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

AbstractWorkGang::~AbstractWorkGang() {
  if (TraceWorkGang) {
    tty->print_cr("Destructing work gang %s", name());
  }
  stop();   // stop all the workers
  for (uint worker = 0; worker < total_workers(); worker += 1) {
    delete gang_worker(worker);
  }
  delete gang_workers();
  delete monitor();
}

GangWorker* AbstractWorkGang::gang_worker(uint i) const {
  // Array index bounds checking.
  GangWorker* result = NULL;
  assert(gang_workers() != NULL, "No workers for indexing");
  assert(((i >= 0) && (i < total_workers())), "Worker index out of bounds");
  result = _gang_workers[i];
  assert(result != NULL, "Indexing to null worker");
  return result;
}

void WorkGang::run_task(AbstractGangTask* task) {
  run_task(task, total_workers());
}

void WorkGang::run_task(AbstractGangTask* task, uint no_of_parallel_workers) {
  task->set_for_termination(no_of_parallel_workers);

  // This thread is executed by the VM thread which does not block
  // on ordinary MutexLocker's.
  MutexLockerEx ml(monitor(), Mutex::_no_safepoint_check_flag);
  if (TraceWorkGang) {
    tty->print_cr("Running work gang %s task %s", name(), task->name());
  }
  // Tell all the workers to run a task.
  assert(task != NULL, "Running a null task");
  // Initialize.
  _task = task;
  _sequence_number += 1;
  _started_workers = 0;
  _finished_workers = 0;
  // Tell the workers to get to work.
  monitor()->notify_all();
  // Wait for them to be finished
  while (finished_workers() < no_of_parallel_workers) {
    if (TraceWorkGang) {
      tty->print_cr("Waiting in work gang %s: %d/%d finished sequence %d",
                    name(), finished_workers(), no_of_parallel_workers,
                    _sequence_number);
    }
    monitor()->wait(/* no_safepoint_check */ true);
  }
  _task = NULL;
  if (TraceWorkGang) {
    tty->print_cr("\nFinished work gang %s: %d/%d sequence %d",
                  name(), finished_workers(), no_of_parallel_workers,
                  _sequence_number);
    Thread* me = Thread::current();
    tty->print_cr("  T: 0x%x  VM_thread: %d", me, me->is_VM_thread());
  }
}

void FlexibleWorkGang::run_task(AbstractGangTask* task) {
  // If active_workers() is passed, _finished_workers
  // must only be incremented for workers that find non_null
  // work (as opposed to all those that just check that the
  // task is not null).
  WorkGang::run_task(task, (uint) active_workers());
}

void AbstractWorkGang::stop() {
  // Tell all workers to terminate, then wait for them to become inactive.
  MutexLockerEx ml(monitor(), Mutex::_no_safepoint_check_flag);
  if (TraceWorkGang) {
    tty->print_cr("Stopping work gang %s task %s", name(), task()->name());
  }
  _task = NULL;
  _terminate = true;
  monitor()->notify_all();
  while (finished_workers() < active_workers()) {
    if (TraceWorkGang) {
      tty->print_cr("Waiting in work gang %s: %d/%d finished",
                    name(), finished_workers(), active_workers());
    }
    monitor()->wait(/* no_safepoint_check */ true);
  }
}

void AbstractWorkGang::internal_worker_poll(WorkData* data) const {
  assert(monitor()->owned_by_self(), "worker_poll is an internal method");
  assert(data != NULL, "worker data is null");
  data->set_terminate(terminate());
  data->set_task(task());
  data->set_sequence_number(sequence_number());
}

void AbstractWorkGang::internal_note_start() {
  assert(monitor()->owned_by_self(), "note_finish is an internal method");
  _started_workers += 1;
}

void AbstractWorkGang::internal_note_finish() {
  assert(monitor()->owned_by_self(), "note_finish is an internal method");
  _finished_workers += 1;
}

void AbstractWorkGang::print_worker_threads_on(outputStream* st) const {
  uint    num_thr = total_workers();
  for (uint i = 0; i < num_thr; i++) {
    gang_worker(i)->print_on(st);
    st->cr();
  }
}

void AbstractWorkGang::threads_do(ThreadClosure* tc) const {
  assert(tc != NULL, "Null ThreadClosure");
  uint num_thr = total_workers();
  for (uint i = 0; i < num_thr; i++) {
    tc->do_thread(gang_worker(i));
  }
}

// GangWorker methods.

GangWorker::GangWorker(AbstractWorkGang* gang, uint id) {
  _gang = gang;
  set_id(id);
  set_name("Gang worker#%d (%s)", id, gang->name());
}

void GangWorker::run() {
  initialize();
  loop();
}

void GangWorker::initialize() {
  this->initialize_thread_local_storage();
  this->record_stack_base_and_size();
  assert(_gang != NULL, "No gang to run in");
  os::set_priority(this, NearMaxPriority);
  if (TraceWorkGang) {
    tty->print_cr("Running gang worker for gang %s id %d",
                  gang()->name(), id());
  }
  // The VM thread should not execute here because MutexLocker's are used
  // as (opposed to MutexLockerEx's).
  assert(!Thread::current()->is_VM_thread(), "VM thread should not be part"
         " of a work gang");
}

void GangWorker::loop() {
  int previous_sequence_number = 0;
  Monitor* gang_monitor = gang()->monitor();
  for ( ; /* !terminate() */; ) {
    WorkData data;
    int part;  // Initialized below.
    {
      // Grab the gang mutex.
      MutexLocker ml(gang_monitor);
      // Wait for something to do.
      // Polling outside the while { wait } avoids missed notifies
      // in the outer loop.
      gang()->internal_worker_poll(&data);
      if (TraceWorkGang) {
        tty->print("Polled outside for work in gang %s worker %d",
                   gang()->name(), id());
        tty->print("  terminate: %s",
                   data.terminate() ? "true" : "false");
        tty->print("  sequence: %d (prev: %d)",
                   data.sequence_number(), previous_sequence_number);
        if (data.task() != NULL) {
          tty->print("  task: %s", data.task()->name());
        } else {
          tty->print("  task: NULL");
        }
        tty->cr();
      }
      for ( ; /* break or return */; ) {
        // Terminate if requested.
        if (data.terminate()) {
          gang()->internal_note_finish();
          gang_monitor->notify_all();
          return;
        }
        // Check for new work.
        if ((data.task() != NULL) &&
            (data.sequence_number() != previous_sequence_number)) {
          if (gang()->needs_more_workers()) {
            gang()->internal_note_start();
            gang_monitor->notify_all();
            part = gang()->started_workers() - 1;
            break;
          }
        }
        // Nothing to do.
        gang_monitor->wait(/* no_safepoint_check */ true);
        gang()->internal_worker_poll(&data);
        if (TraceWorkGang) {
          tty->print("Polled inside for work in gang %s worker %d",
                     gang()->name(), id());
          tty->print("  terminate: %s",
                     data.terminate() ? "true" : "false");
          tty->print("  sequence: %d (prev: %d)",
                     data.sequence_number(), previous_sequence_number);
          if (data.task() != NULL) {
            tty->print("  task: %s", data.task()->name());
          } else {
            tty->print("  task: NULL");
          }
          tty->cr();
        }
      }
      // Drop gang mutex.
    }
    if (TraceWorkGang) {
      tty->print("Work for work gang %s id %d task %s part %d",
                 gang()->name(), id(), data.task()->name(), part);
    }
    assert(data.task() != NULL, "Got null task");
    data.task()->work(part);
    {
      if (TraceWorkGang) {
        tty->print("Finish for work gang %s id %d task %s part %d",
                   gang()->name(), id(), data.task()->name(), part);
      }
      // Grab the gang mutex.
      MutexLocker ml(gang_monitor);
      gang()->internal_note_finish();
      // Tell the gang you are done.
      gang_monitor->notify_all();
      // Drop the gang mutex.
    }
    previous_sequence_number = data.sequence_number();
  }
}

bool GangWorker::is_GC_task_thread() const {
  return gang()->are_GC_task_threads();
}

bool GangWorker::is_ConcurrentGC_thread() const {
  return gang()->are_ConcurrentGC_threads();
}

void GangWorker::print_on(outputStream* st) const {
  st->print("\"%s\" ", name());
  Thread::print_on(st);
  st->cr();
}

// Printing methods

const char* AbstractWorkGang::name() const {
  return _name;
}

#ifndef PRODUCT

const char* AbstractGangTask::name() const {
  return _name;
}

#endif /* PRODUCT */

// FlexibleWorkGang


// *** WorkGangBarrierSync

WorkGangBarrierSync::WorkGangBarrierSync()
  : _monitor(Mutex::safepoint, "work gang barrier sync", true),
    _n_workers(0), _n_completed(0), _should_reset(false) {
}

WorkGangBarrierSync::WorkGangBarrierSync(uint n_workers, const char* name)
  : _monitor(Mutex::safepoint, name, true),
    _n_workers(n_workers), _n_completed(0), _should_reset(false) {
}

void WorkGangBarrierSync::set_n_workers(uint n_workers) {
  _n_workers   = n_workers;
  _n_completed = 0;
  _should_reset = false;
}

void WorkGangBarrierSync::enter() {
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
    while (n_completed() != n_workers()) {
      monitor()->wait(/* no_safepoint_check */ true);
    }
  }
}

// SubTasksDone functions.

SubTasksDone::SubTasksDone(uint n) :
  _n_tasks(n), _n_threads(1), _tasks(NULL) {
  _tasks = NEW_C_HEAP_ARRAY(uint, n, mtInternal);
  guarantee(_tasks != NULL, "alloc failure");
  clear();
}

bool SubTasksDone::valid() {
  return _tasks != NULL;
}

void SubTasksDone::set_n_threads(uint t) {
  assert(_claimed == 0 || _threads_completed == _n_threads,
         "should not be called while tasks are being processed!");
  _n_threads = (t == 0 ? 1 : t);
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
  assert(0 <= t && t < _n_tasks, "bad task id.");
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

void SubTasksDone::all_tasks_completed() {
  jint observed = _threads_completed;
  jint old;
  do {
    old = observed;
    observed = Atomic::cmpxchg(old+1, &_threads_completed, old);
  } while (observed != old);
  // If this was the last thread checking in, clear the tasks.
  if (observed+1 == (jint)_n_threads) clear();
}


SubTasksDone::~SubTasksDone() {
  if (_tasks != NULL) FREE_C_HEAP_ARRAY(jint, _tasks, mtInternal);
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
  FREE_C_HEAP_ARRAY(int, _ids, mtInternal);
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
