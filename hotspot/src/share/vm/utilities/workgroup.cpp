/*
 * Copyright 2001-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
# include "incls/_workgroup.cpp.incl"

// Definitions of WorkGang methods.

AbstractWorkGang::AbstractWorkGang(const char* name,
                                   bool  are_GC_threads) :
  _name(name),
  _are_GC_threads(are_GC_threads) {
  // Other initialization.
  _monitor = new Monitor(/* priority */       Mutex::leaf,
                         /* name */           "WorkGroup monitor",
                         /* allow_vm_block */ are_GC_threads);
  assert(monitor() != NULL, "Failed to allocate monitor");
  _terminate = false;
  _task = NULL;
  _sequence_number = 0;
  _started_workers = 0;
  _finished_workers = 0;
}

WorkGang::WorkGang(const char* name,
                   int           workers,
                   bool          are_GC_threads) :
  AbstractWorkGang(name, are_GC_threads) {
  // Save arguments.
  _total_workers = workers;
  if (TraceWorkGang) {
    tty->print_cr("Constructing work gang %s with %d threads", name, workers);
  }
  _gang_workers = NEW_C_HEAP_ARRAY(GangWorker*, workers);
  assert(gang_workers() != NULL, "Failed to allocate gang workers");
  for (int worker = 0; worker < total_workers(); worker += 1) {
    GangWorker* new_worker = new GangWorker(this, worker);
    assert(new_worker != NULL, "Failed to allocate GangWorker");
    _gang_workers[worker] = new_worker;
    if (new_worker == NULL || !os::create_thread(new_worker, os::pgc_thread))
      vm_exit_out_of_memory(0, "Cannot create worker GC thread. Out of system resources.");
    if (!DisableStartThread) {
      os::start_thread(new_worker);
    }
  }
}

AbstractWorkGang::~AbstractWorkGang() {
  if (TraceWorkGang) {
    tty->print_cr("Destructing work gang %s", name());
  }
  stop();   // stop all the workers
  for (int worker = 0; worker < total_workers(); worker += 1) {
    delete gang_worker(worker);
  }
  delete gang_workers();
  delete monitor();
}

GangWorker* AbstractWorkGang::gang_worker(int i) const {
  // Array index bounds checking.
  GangWorker* result = NULL;
  assert(gang_workers() != NULL, "No workers for indexing");
  assert(((i >= 0) && (i < total_workers())), "Worker index out of bounds");
  result = _gang_workers[i];
  assert(result != NULL, "Indexing to null worker");
  return result;
}

void WorkGang::run_task(AbstractGangTask* task) {
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
  while (finished_workers() < total_workers()) {
    if (TraceWorkGang) {
      tty->print_cr("Waiting in work gang %s: %d/%d finished sequence %d",
                    name(), finished_workers(), total_workers(),
                    _sequence_number);
    }
    monitor()->wait(/* no_safepoint_check */ true);
  }
  _task = NULL;
  if (TraceWorkGang) {
    tty->print_cr("/nFinished work gang %s: %d/%d sequence %d",
                  name(), finished_workers(), total_workers(),
                  _sequence_number);
    }
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
  while (finished_workers() < total_workers()) {
    if (TraceWorkGang) {
      tty->print_cr("Waiting in work gang %s: %d/%d finished",
                    name(), finished_workers(), total_workers());
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
          gang()->internal_note_start();
          gang_monitor->notify_all();
          part = gang()->started_workers() - 1;
          break;
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
  return gang()->are_GC_threads();
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

// *** WorkGangBarrierSync

WorkGangBarrierSync::WorkGangBarrierSync()
  : _monitor(Mutex::safepoint, "work gang barrier sync", true),
    _n_workers(0), _n_completed(0) {
}

WorkGangBarrierSync::WorkGangBarrierSync(int n_workers, const char* name)
  : _monitor(Mutex::safepoint, name, true),
    _n_workers(n_workers), _n_completed(0) {
}

void WorkGangBarrierSync::set_n_workers(int n_workers) {
  _n_workers   = n_workers;
  _n_completed = 0;
}

void WorkGangBarrierSync::enter() {
  MutexLockerEx x(monitor(), Mutex::_no_safepoint_check_flag);
  inc_completed();
  if (n_completed() == n_workers()) {
    monitor()->notify_all();
  }
  else {
    while (n_completed() != n_workers()) {
      monitor()->wait(/* no_safepoint_check */ true);
    }
  }
}

// SubTasksDone functions.

SubTasksDone::SubTasksDone(int n) :
  _n_tasks(n), _n_threads(1), _tasks(NULL) {
  _tasks = NEW_C_HEAP_ARRAY(jint, n);
  guarantee(_tasks != NULL, "alloc failure");
  clear();
}

bool SubTasksDone::valid() {
  return _tasks != NULL;
}

void SubTasksDone::set_par_threads(int t) {
#ifdef ASSERT
  assert(_claimed == 0 || _threads_completed == _n_threads,
         "should not be called while tasks are being processed!");
#endif
  _n_threads = (t == 0 ? 1 : t);
}

void SubTasksDone::clear() {
  for (int i = 0; i < _n_tasks; i++) {
    _tasks[i] = 0;
  }
  _threads_completed = 0;
#ifdef ASSERT
  _claimed = 0;
#endif
}

bool SubTasksDone::is_task_claimed(int t) {
  assert(0 <= t && t < _n_tasks, "bad task id.");
  jint old = _tasks[t];
  if (old == 0) {
    old = Atomic::cmpxchg(1, &_tasks[t], 0);
  }
  assert(_tasks[t] == 1, "What else?");
  bool res = old != 0;
#ifdef ASSERT
  if (!res) {
    assert(_claimed < _n_tasks, "Too many tasks claimed; missing clear?");
    Atomic::inc(&_claimed);
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
  if (observed+1 == _n_threads) clear();
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

bool SequentialSubTasksDone::is_task_claimed(int& t) {
  jint* n_claimed_ptr = &_n_claimed;
  t = *n_claimed_ptr;
  while (t < _n_tasks) {
    jint res = Atomic::cmpxchg(t+1, n_claimed_ptr, t);
    if (res == t) {
      return false;
    }
    t = *n_claimed_ptr;
  }
  return true;
}

bool SequentialSubTasksDone::all_tasks_completed() {
  jint* n_completed_ptr = &_n_completed;
  jint  complete        = *n_completed_ptr;
  while (true) {
    jint res = Atomic::cmpxchg(complete+1, n_completed_ptr, complete);
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
