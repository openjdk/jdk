/*
 * Copyright (c) 2018, 2019, Red Hat, Inc. All rights reserved.
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

#include "gc/shared/owstTaskTerminator.hpp"
#include "logging/log.hpp"

bool OWSTTaskTerminator::exit_termination(size_t tasks, TerminatorTerminator* terminator) {
  return tasks > 0 || (terminator != NULL && terminator->should_exit_termination());
}

bool OWSTTaskTerminator::offer_termination(TerminatorTerminator* terminator) {
  assert(_n_threads > 0, "Initialization is incorrect");
  assert(_offered_termination < _n_threads, "Invariant");
  assert(_blocker != NULL, "Invariant");

  // Single worker, done
  if (_n_threads == 1) {
    _offered_termination = 1;
    assert(!peek_in_queue_set(), "Precondition");
    return true;
  }

  _blocker->lock_without_safepoint_check();
  _offered_termination++;
  // All arrived, done
  if (_offered_termination == _n_threads) {
    _blocker->notify_all();
    _blocker->unlock();
    assert(!peek_in_queue_set(), "Precondition");
    return true;
  }

  Thread* the_thread = Thread::current();
  while (true) {
    if (_spin_master == NULL) {
      _spin_master = the_thread;

      _blocker->unlock();

      if (do_spin_master_work(terminator)) {
        assert(_offered_termination == _n_threads, "termination condition");
        assert(!peek_in_queue_set(), "Precondition");
        return true;
      } else {
        _blocker->lock_without_safepoint_check();
        // There is possibility that termination is reached between dropping the lock
        // before returning from do_spin_master_work() and acquiring lock above.
        if (_offered_termination == _n_threads) {
          _blocker->unlock();
          assert(!peek_in_queue_set(), "Precondition");
          return true;
        }
      }
    } else {
      _blocker->wait(true, WorkStealingSleepMillis);

      if (_offered_termination == _n_threads) {
        _blocker->unlock();
        assert(!peek_in_queue_set(), "Precondition");
        return true;
      }
    }

    size_t tasks = tasks_in_queue_set();
    if (exit_termination(tasks, terminator)) {
      assert_lock_strong(_blocker);
      _offered_termination--;
      _blocker->unlock();
      return false;
    }
  }
}

bool OWSTTaskTerminator::do_spin_master_work(TerminatorTerminator* terminator) {
  uint yield_count = 0;
  // Number of hard spin loops done since last yield
  uint hard_spin_count = 0;
  // Number of iterations in the hard spin loop.
  uint hard_spin_limit = WorkStealingHardSpins;

  // If WorkStealingSpinToYieldRatio is 0, no hard spinning is done.
  // If it is greater than 0, then start with a small number
  // of spins and increase number with each turn at spinning until
  // the count of hard spins exceeds WorkStealingSpinToYieldRatio.
  // Then do a yield() call and start spinning afresh.
  if (WorkStealingSpinToYieldRatio > 0) {
    hard_spin_limit = WorkStealingHardSpins >> WorkStealingSpinToYieldRatio;
    hard_spin_limit = MAX2(hard_spin_limit, 1U);
  }
  // Remember the initial spin limit.
  uint hard_spin_start = hard_spin_limit;

  // Loop waiting for all threads to offer termination or
  // more work.
  while (true) {
    // Look for more work.
    // Periodically sleep() instead of yield() to give threads
    // waiting on the cores the chance to grab this code
    if (yield_count <= WorkStealingYieldsBeforeSleep) {
      // Do a yield or hardspin.  For purposes of deciding whether
      // to sleep, count this as a yield.
      yield_count++;

      // Periodically call yield() instead spinning
      // After WorkStealingSpinToYieldRatio spins, do a yield() call
      // and reset the counts and starting limit.
      if (hard_spin_count > WorkStealingSpinToYieldRatio) {
        yield();
        hard_spin_count = 0;
        hard_spin_limit = hard_spin_start;
#ifdef TRACESPINNING
        _total_yields++;
#endif
      } else {
        // Hard spin this time
        // Increase the hard spinning period but only up to a limit.
        hard_spin_limit = MIN2(2*hard_spin_limit,
                               (uint) WorkStealingHardSpins);
        for (uint j = 0; j < hard_spin_limit; j++) {
          SpinPause();
        }
        hard_spin_count++;
#ifdef TRACESPINNING
        _total_spins++;
#endif
      }
    } else {
      log_develop_trace(gc, task)("OWSTTaskTerminator::do_spin_master_work() thread " PTR_FORMAT " sleeps after %u yields",
                                  p2i(Thread::current()), yield_count);
      yield_count = 0;

      MonitorLockerEx locker(_blocker, Mutex::_no_safepoint_check_flag);
      _spin_master = NULL;
      locker.wait(Mutex::_no_safepoint_check_flag, WorkStealingSleepMillis);
      if (_spin_master == NULL) {
        _spin_master = Thread::current();
      } else {
        return false;
      }
    }

#ifdef TRACESPINNING
      _total_peeks++;
#endif
    size_t tasks = tasks_in_queue_set();
    bool exit = exit_termination(tasks, terminator);
    {
      MonitorLockerEx locker(_blocker, Mutex::_no_safepoint_check_flag);
      // Termination condition reached
      if (_offered_termination == _n_threads) {
        _spin_master = NULL;
        return true;
      } else if (exit) {
        if (tasks >= _offered_termination - 1) {
          locker.notify_all();
        } else {
          for (; tasks > 1; tasks--) {
            locker.notify();
          }
        }
        _spin_master = NULL;
        return false;
      }
    }
  }
}
