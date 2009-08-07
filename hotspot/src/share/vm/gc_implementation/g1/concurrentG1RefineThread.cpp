/*
 * Copyright 2001-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_concurrentG1RefineThread.cpp.incl"

// ======= Concurrent Mark Thread ========

// The CM thread is created when the G1 garbage collector is used

ConcurrentG1RefineThread::
ConcurrentG1RefineThread(ConcurrentG1Refine* cg1r, ConcurrentG1RefineThread *next,
                         int worker_id_offset, int worker_id) :
  ConcurrentGCThread(),
  _worker_id_offset(worker_id_offset),
  _worker_id(worker_id),
  _active(false),
  _next(next),
  _cg1r(cg1r),
  _vtime_accum(0.0),
  _co_tracker(G1CRGroup),
  _interval_ms(5.0)
{
  create_and_start();
}

void ConcurrentG1RefineThread::sample_young_list_rs_lengths() {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  G1CollectorPolicy* g1p = g1h->g1_policy();
  if (g1p->adaptive_young_list_length()) {
    int regions_visited = 0;

    g1h->young_list_rs_length_sampling_init();
    while (g1h->young_list_rs_length_sampling_more()) {
      g1h->young_list_rs_length_sampling_next();
      ++regions_visited;

      // we try to yield every time we visit 10 regions
      if (regions_visited == 10) {
        if (_sts.should_yield()) {
          _sts.yield("G1 refine");
          // we just abandon the iteration
          break;
        }
        regions_visited = 0;
      }
    }

    g1p->check_prediction_validity();
  }
}

void ConcurrentG1RefineThread::run() {
  initialize_in_thread();
  _vtime_start = os::elapsedVTime();
  wait_for_universe_init();

  _co_tracker.enable();
  _co_tracker.start();

  while (!_should_terminate) {
    DirtyCardQueueSet& dcqs = JavaThread::dirty_card_queue_set();
    // Wait for completed log buffers to exist.
    {
      MutexLockerEx x(DirtyCardQ_CBL_mon, Mutex::_no_safepoint_check_flag);
      while (((_worker_id == 0 && !dcqs.process_completed_buffers()) ||
              (_worker_id > 0 && !is_active())) &&
             !_should_terminate) {
         DirtyCardQ_CBL_mon->wait(Mutex::_no_safepoint_check_flag);
      }
    }

    if (_should_terminate) {
      return;
    }

    // Now we take them off (this doesn't hold locks while it applies
    // closures.)  (If we did a full collection, then we'll do a full
    // traversal.
    _sts.join();
    int n_logs = 0;
    int lower_limit = 0;
    double start_vtime_sec; // only used when G1SmoothConcRefine is on
    int prev_buffer_num; // only used when G1SmoothConcRefine is on
    // This thread activation threshold
    int threshold = DCQBarrierProcessCompletedThreshold * _worker_id;
    // Next thread activation threshold
    int next_threshold = threshold + DCQBarrierProcessCompletedThreshold;
    int deactivation_threshold = MAX2<int>(threshold - DCQBarrierProcessCompletedThreshold / 2, 0);

    if (G1SmoothConcRefine) {
      lower_limit = 0;
      start_vtime_sec = os::elapsedVTime();
      prev_buffer_num = (int) dcqs.completed_buffers_num();
    } else {
      lower_limit = DCQBarrierProcessCompletedThreshold / 4; // For now.
    }
    while (dcqs.apply_closure_to_completed_buffer(_worker_id + _worker_id_offset, lower_limit)) {
      double end_vtime_sec;
      double elapsed_vtime_sec;
      int elapsed_vtime_ms;
      int curr_buffer_num = (int) dcqs.completed_buffers_num();

      if (G1SmoothConcRefine) {
        end_vtime_sec = os::elapsedVTime();
        elapsed_vtime_sec = end_vtime_sec - start_vtime_sec;
        elapsed_vtime_ms = (int) (elapsed_vtime_sec * 1000.0);

        if (curr_buffer_num > prev_buffer_num ||
            curr_buffer_num > next_threshold) {
          decreaseInterval(elapsed_vtime_ms);
        } else if (curr_buffer_num < prev_buffer_num) {
          increaseInterval(elapsed_vtime_ms);
        }
      }
      if (_worker_id == 0) {
        sample_young_list_rs_lengths();
      } else if (curr_buffer_num < deactivation_threshold) {
        // If the number of the buffer has fallen below our threshold
        // we should deactivate. The predecessor will reactivate this
        // thread should the number of the buffers cross the threshold again.
        MutexLockerEx x(DirtyCardQ_CBL_mon, Mutex::_no_safepoint_check_flag);
        deactivate();
        if (G1TraceConcurrentRefinement) {
          gclog_or_tty->print_cr("G1-Refine-deactivated worker %d", _worker_id);
        }
        break;
      }
      _co_tracker.update(false);

      // Check if we need to activate the next thread.
      if (curr_buffer_num > next_threshold && _next != NULL && !_next->is_active()) {
        MutexLockerEx x(DirtyCardQ_CBL_mon, Mutex::_no_safepoint_check_flag);
        _next->activate();
        DirtyCardQ_CBL_mon->notify_all();
        if (G1TraceConcurrentRefinement) {
          gclog_or_tty->print_cr("G1-Refine-activated worker %d", _next->_worker_id);
        }
      }

      if (G1SmoothConcRefine) {
        prev_buffer_num = curr_buffer_num;
        _sts.leave();
        os::sleep(Thread::current(), (jlong) _interval_ms, false);
        _sts.join();
        start_vtime_sec = os::elapsedVTime();
      }
      n_logs++;
    }
    _co_tracker.update(false);
    _sts.leave();

    if (os::supports_vtime()) {
      _vtime_accum = (os::elapsedVTime() - _vtime_start);
    } else {
      _vtime_accum = 0.0;
    }
  }
  _sts.join();
  _co_tracker.update(true);
  _sts.leave();
  assert(_should_terminate, "just checking");

  terminate();
}


void ConcurrentG1RefineThread::yield() {
  if (G1TraceConcurrentRefinement) gclog_or_tty->print_cr("G1-Refine-yield");
  _sts.yield("G1 refine");
  if (G1TraceConcurrentRefinement) gclog_or_tty->print_cr("G1-Refine-yield-end");
}

void ConcurrentG1RefineThread::stop() {
  // it is ok to take late safepoints here, if needed
  {
    MutexLockerEx mu(Terminator_lock);
    _should_terminate = true;
  }

  {
    MutexLockerEx x(DirtyCardQ_CBL_mon, Mutex::_no_safepoint_check_flag);
    DirtyCardQ_CBL_mon->notify_all();
  }

  {
    MutexLockerEx mu(Terminator_lock);
    while (!_has_terminated) {
      Terminator_lock->wait();
    }
  }
  if (G1TraceConcurrentRefinement) gclog_or_tty->print_cr("G1-Refine-stop");
}

void ConcurrentG1RefineThread::print() {
  gclog_or_tty->print("\"Concurrent G1 Refinement Thread\" ");
  Thread::print();
  gclog_or_tty->cr();
}
