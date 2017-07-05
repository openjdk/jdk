/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/cms/yieldingWorkgroup.hpp"
#include "utilities/macros.hpp"

YieldingFlexibleGangWorker::YieldingFlexibleGangWorker(YieldingFlexibleWorkGang* gang, int id)
    : AbstractGangWorker(gang, id) {}

YieldingFlexibleWorkGang::YieldingFlexibleWorkGang(
    const char* name, uint workers, bool are_GC_task_threads) :
         AbstractWorkGang(name, workers, are_GC_task_threads, false),
         _yielded_workers(0),
         _started_workers(0),
         _finished_workers(0),
         _sequence_number(0),
         _task(NULL) {

  // Other initialization.
  _monitor = new Monitor(/* priority */       Mutex::leaf,
                         /* name */           "WorkGroup monitor",
                         /* allow_vm_block */ are_GC_task_threads,
                                              Monitor::_safepoint_check_sometimes);

  assert(monitor() != NULL, "Failed to allocate monitor");
}

AbstractGangWorker* YieldingFlexibleWorkGang::allocate_worker(uint which) {
  return new YieldingFlexibleGangWorker(this, which);
}

void YieldingFlexibleWorkGang::internal_worker_poll(YieldingWorkData* data) const {
  assert(data != NULL, "worker data is null");
  data->set_task(task());
  data->set_sequence_number(sequence_number());
}

void YieldingFlexibleWorkGang::internal_note_start() {
  assert(monitor()->owned_by_self(), "note_finish is an internal method");
  _started_workers += 1;
}

void YieldingFlexibleWorkGang::internal_note_finish() {
  assert(monitor()->owned_by_self(), "note_finish is an internal method");
  _finished_workers += 1;
}

// Run a task; returns when the task is done, or the workers yield,
// or the task is aborted.
// A task that has been yielded can be continued via this interface
// by using the same task repeatedly as the argument to the call.
// It is expected that the YieldingFlexibleGangTask carries the appropriate
// continuation information used by workers to continue the task
// from its last yield point. Thus, a completed task will return
// immediately with no actual work having been done by the workers.
/////////////////////
// Implementatiuon notes: remove before checking XXX
/*
Each gang is working on a task at a certain time.
Some subset of workers may have yielded and some may
have finished their quota of work. Until this task has
been completed, the workers are bound to that task.
Once the task has been completed, the gang unbounds
itself from the task.

The yielding work gang thus exports two invokation
interfaces: run_task() and continue_task(). The
first is used to initiate a new task and bind it
to the workers; the second is used to continue an
already bound task that has yielded. Upon completion
the binding is released and a new binding may be
created.

The shape of a yielding work gang is as follows:

Overseer invokes run_task(*task).
   Lock gang monitor
   Check that there is no existing binding for the gang
   If so, abort with an error
   Else, create a new binding of this gang to the given task
   Set number of active workers (as asked)
   Notify workers that work is ready to be done
     [the requisite # workers would then start up
      and do the task]
   Wait on the monitor until either
     all work is completed or the task has yielded
     -- this is normally done through
        yielded + completed == active
        [completed workers are rest to idle state by overseer?]
   return appropriate status to caller

Overseer invokes continue_task(*task),
   Lock gang monitor
   Check that task is the same as current binding
   If not, abort with an error
   Else, set the number of active workers as requested?
   Notify workers that they can continue from yield points
    New workers can also start up as required
      while satisfying the constraint that
         active + yielded does not exceed required number
   Wait (as above).

NOTE: In the above, for simplicity in a first iteration
  our gangs will be of fixed population and will not
  therefore be flexible work gangs, just yielding work
  gangs. Once this works well, we will in a second
  iteration.refinement introduce flexibility into
  the work gang.

NOTE: we can always create a new gang per each iteration
  in order to get the flexibility, but we will for now
  desist that simplified route.

 */
/////////////////////
void YieldingFlexibleWorkGang::start_task(YieldingFlexibleGangTask* new_task) {
  MutexLockerEx ml(monitor(), Mutex::_no_safepoint_check_flag);
  assert(task() == NULL, "Gang currently tied to a task");
  assert(new_task != NULL, "Null task");
  // Bind task to gang
  _task = new_task;
  new_task->set_gang(this);  // Establish 2-way binding to support yielding
  _sequence_number++;

  uint requested_size = new_task->requested_size();
  if (requested_size != 0) {
    _active_workers = MIN2(requested_size, total_workers());
  } else {
    _active_workers = active_workers();
  }
  new_task->set_actual_size(_active_workers);
  new_task->set_for_termination(_active_workers);

  assert(_started_workers == 0, "Tabula rasa non");
  assert(_finished_workers == 0, "Tabula rasa non");
  assert(_yielded_workers == 0, "Tabula rasa non");
  yielding_task()->set_status(ACTIVE);

  // Wake up all the workers, the first few will get to work,
  // and the rest will go back to sleep
  monitor()->notify_all();
  wait_for_gang();
}

void YieldingFlexibleWorkGang::wait_for_gang() {

  assert(monitor()->owned_by_self(), "Data race");
  // Wait for task to complete or yield
  for (Status status = yielding_task()->status();
       status != COMPLETED && status != YIELDED && status != ABORTED;
       status = yielding_task()->status()) {
    assert(started_workers() <= active_workers(), "invariant");
    assert(finished_workers() <= active_workers(), "invariant");
    assert(yielded_workers() <= active_workers(), "invariant");
    monitor()->wait(Mutex::_no_safepoint_check_flag);
  }
  switch (yielding_task()->status()) {
    case COMPLETED:
    case ABORTED: {
      assert(finished_workers() == active_workers(), "Inconsistent status");
      assert(yielded_workers() == 0, "Invariant");
      reset();   // for next task; gang<->task binding released
      break;
    }
    case YIELDED: {
      assert(yielded_workers() > 0, "Invariant");
      assert(yielded_workers() + finished_workers() == active_workers(),
             "Inconsistent counts");
      break;
    }
    case ACTIVE:
    case INACTIVE:
    case COMPLETING:
    case YIELDING:
    case ABORTING:
    default:
      ShouldNotReachHere();
  }
}

void YieldingFlexibleWorkGang::continue_task(
  YieldingFlexibleGangTask* gang_task) {

  MutexLockerEx ml(monitor(), Mutex::_no_safepoint_check_flag);
  assert(task() != NULL && task() == gang_task, "Incorrect usage");
  assert(_started_workers == _active_workers, "Precondition");
  assert(_yielded_workers > 0 && yielding_task()->status() == YIELDED,
         "Else why are we calling continue_task()");
  // Restart the yielded gang workers
  yielding_task()->set_status(ACTIVE);
  monitor()->notify_all();
  wait_for_gang();
}

void YieldingFlexibleWorkGang::reset() {
  _started_workers  = 0;
  _finished_workers = 0;
  yielding_task()->set_gang(NULL);
  _task = NULL;    // unbind gang from task
}

void YieldingFlexibleWorkGang::yield() {
  assert(task() != NULL, "Inconsistency; should have task binding");
  MutexLockerEx ml(monitor(), Mutex::_no_safepoint_check_flag);
  assert(yielded_workers() < active_workers(), "Consistency check");
  if (yielding_task()->status() == ABORTING) {
    // Do not yield; we need to abort as soon as possible
    // XXX NOTE: This can cause a performance pathology in the
    // current implementation in Mustang, as of today, and
    // pre-Mustang in that as soon as an overflow occurs,
    // yields will not be honoured. The right way to proceed
    // of course is to fix bug # TBF, so that abort's cause
    // us to return at each potential yield point.
    return;
  }
  if (++_yielded_workers + finished_workers() == active_workers()) {
    yielding_task()->set_status(YIELDED);
    monitor()->notify_all();
  } else {
    yielding_task()->set_status(YIELDING);
  }

  while (true) {
    switch (yielding_task()->status()) {
      case YIELDING:
      case YIELDED: {
        monitor()->wait(Mutex::_no_safepoint_check_flag);
        break;  // from switch
      }
      case ACTIVE:
      case ABORTING:
      case COMPLETING: {
        assert(_yielded_workers > 0, "Else why am i here?");
        _yielded_workers--;
        return;
      }
      case INACTIVE:
      case ABORTED:
      case COMPLETED:
      default: {
        ShouldNotReachHere();
      }
    }
  }
  // Only return is from inside switch statement above
  ShouldNotReachHere();
}

void YieldingFlexibleWorkGang::abort() {
  assert(task() != NULL, "Inconsistency; should have task binding");
  MutexLockerEx ml(monitor(), Mutex::_no_safepoint_check_flag);
  assert(yielded_workers() < active_workers(), "Consistency check");
  #ifndef PRODUCT
    switch (yielding_task()->status()) {
      // allowed states
      case ACTIVE:
      case ABORTING:
      case COMPLETING:
      case YIELDING:
        break;
      // not allowed states
      case INACTIVE:
      case ABORTED:
      case COMPLETED:
      case YIELDED:
      default:
        ShouldNotReachHere();
    }
  #endif // !PRODUCT
  Status prev_status = yielding_task()->status();
  yielding_task()->set_status(ABORTING);
  if (prev_status == YIELDING) {
    assert(yielded_workers() > 0, "Inconsistency");
    // At least one thread has yielded, wake it up
    // so it can go back to waiting stations ASAP.
    monitor()->notify_all();
  }
}

///////////////////////////////
// YieldingFlexibleGangTask
///////////////////////////////
void YieldingFlexibleGangTask::yield() {
  assert(gang() != NULL, "No gang to signal");
  gang()->yield();
}

void YieldingFlexibleGangTask::abort() {
  assert(gang() != NULL, "No gang to signal");
  gang()->abort();
}

///////////////////////////////
// YieldingFlexibleGangWorker
///////////////////////////////
void YieldingFlexibleGangWorker::loop() {
  int previous_sequence_number = 0;
  Monitor* gang_monitor = yf_gang()->monitor();
  MutexLockerEx ml(gang_monitor, Mutex::_no_safepoint_check_flag);
  YieldingWorkData data;
  int id;
  while (true) {
    // Check if there is work to do.
    yf_gang()->internal_worker_poll(&data);
    if (data.task() != NULL && data.sequence_number() != previous_sequence_number) {
      // There is work to be done.
      // First check if we need to become active or if there
      // are already the requisite number of workers
      if (yf_gang()->started_workers() == yf_gang()->active_workers()) {
        // There are already enough workers, we do not need to
        // to run; fall through and wait on monitor.
      } else {
        // We need to pitch in and do the work.
        assert(yf_gang()->started_workers() < yf_gang()->active_workers(),
               "Unexpected state");
        id = yf_gang()->started_workers();
        yf_gang()->internal_note_start();
        // Now, release the gang mutex and do the work.
        {
          MutexUnlockerEx mul(gang_monitor, Mutex::_no_safepoint_check_flag);
          data.task()->work(id);   // This might include yielding
        }
        // Reacquire monitor and note completion of this worker
        yf_gang()->internal_note_finish();
        // Update status of task based on whether all workers have
        // finished or some have yielded
        assert(data.task() == yf_gang()->task(), "Confused task binding");
        if (yf_gang()->finished_workers() == yf_gang()->active_workers()) {
          switch (data.yf_task()->status()) {
            case ABORTING: {
              data.yf_task()->set_status(ABORTED);
              break;
            }
            case ACTIVE:
            case COMPLETING: {
              data.yf_task()->set_status(COMPLETED);
              break;
            }
            default:
              ShouldNotReachHere();
          }
          gang_monitor->notify_all();  // Notify overseer
        } else { // at least one worker is still working or yielded
          assert(yf_gang()->finished_workers() < yf_gang()->active_workers(),
                 "Counts inconsistent");
          switch (data.yf_task()->status()) {
            case ACTIVE: {
              // first, but not only thread to complete
              data.yf_task()->set_status(COMPLETING);
              break;
            }
            case YIELDING: {
              if (yf_gang()->finished_workers() + yf_gang()->yielded_workers()
                  == yf_gang()->active_workers()) {
                data.yf_task()->set_status(YIELDED);
                gang_monitor->notify_all();  // notify overseer
              }
              break;
            }
            case ABORTING:
            case COMPLETING: {
              break; // nothing to do
            }
            default: // everything else: INACTIVE, YIELDED, ABORTED, COMPLETED
              ShouldNotReachHere();
          }
        }
      }
    }
    // Remember the sequence number
    previous_sequence_number = data.sequence_number();
    // Wait for more work
    gang_monitor->wait(Mutex::_no_safepoint_check_flag);
  }
}
