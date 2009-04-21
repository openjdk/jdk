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

#include "incls/_precompiled.incl"
#include "incls/_concurrentG1RefineThread.cpp.incl"

// ======= Concurrent Mark Thread ========

// The CM thread is created when the G1 garbage collector is used

ConcurrentG1RefineThread::
ConcurrentG1RefineThread(ConcurrentG1Refine* cg1r) :
  ConcurrentGCThread(),
  _cg1r(cg1r),
  _started(false),
  _in_progress(false),
  _do_traversal(false),
  _vtime_accum(0.0),
  _co_tracker(G1CRGroup),
  _interval_ms(5.0)
{
  create_and_start();
}

const long timeout = 200; // ms.

void ConcurrentG1RefineThread::traversalBasedRefinement() {
  _cg1r->wait_for_ConcurrentG1Refine_enabled();
  MutexLocker x(G1ConcRefine_mon);
  while (_cg1r->enabled()) {
    MutexUnlocker ux(G1ConcRefine_mon);
    ResourceMark rm;
    HandleMark   hm;

    if (TraceG1Refine) gclog_or_tty->print_cr("G1-Refine starting pass");
    _sts.join();
    bool no_sleep = _cg1r->refine();
    _sts.leave();
    if (!no_sleep) {
      MutexLockerEx x(CGC_lock, Mutex::_no_safepoint_check_flag);
      // We do this only for the timeout; we don't expect this to be signalled.
      CGC_lock->wait(Mutex::_no_safepoint_check_flag, timeout);
    }
  }
}

void ConcurrentG1RefineThread::queueBasedRefinement() {
  DirtyCardQueueSet& dcqs = JavaThread::dirty_card_queue_set();
  // Wait for completed log buffers to exist.
  {
    MutexLockerEx x(DirtyCardQ_CBL_mon, Mutex::_no_safepoint_check_flag);
    while (!_do_traversal && !dcqs.process_completed_buffers() &&
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
  if (_do_traversal) {
    (void)_cg1r->refine();
    switch (_cg1r->get_last_pya()) {
    case PYA_cancel: case PYA_continue:
      // Continue was caught and handled inside "refine".  If it's still
      // "continue" when we get here, we're done.
      _do_traversal = false;
      break;
    case PYA_restart:
      assert(_do_traversal, "Because of Full GC.");
      break;
    }
  } else {
    int n_logs = 0;
    int lower_limit = 0;
    double start_vtime_sec; // only used when G1SmoothConcRefine is on
    int prev_buffer_num; // only used when G1SmoothConcRefine is on

    if (G1SmoothConcRefine) {
      lower_limit = 0;
      start_vtime_sec = os::elapsedVTime();
      prev_buffer_num = (int) dcqs.completed_buffers_num();
    } else {
      lower_limit = DCQBarrierProcessCompletedThreshold / 4; // For now.
    }
    while (dcqs.apply_closure_to_completed_buffer(0, lower_limit)) {
      double end_vtime_sec;
      double elapsed_vtime_sec;
      int elapsed_vtime_ms;
      int curr_buffer_num;

      if (G1SmoothConcRefine) {
        end_vtime_sec = os::elapsedVTime();
        elapsed_vtime_sec = end_vtime_sec - start_vtime_sec;
        elapsed_vtime_ms = (int) (elapsed_vtime_sec * 1000.0);
        curr_buffer_num = (int) dcqs.completed_buffers_num();

        if (curr_buffer_num > prev_buffer_num ||
            curr_buffer_num > DCQBarrierProcessCompletedThreshold) {
          decreaseInterval(elapsed_vtime_ms);
        } else if (curr_buffer_num < prev_buffer_num) {
          increaseInterval(elapsed_vtime_ms);
        }
      }

      sample_young_list_rs_lengths();
      _co_tracker.update(false);

      if (G1SmoothConcRefine) {
        prev_buffer_num = curr_buffer_num;
        _sts.leave();
        os::sleep(Thread::current(), (jlong) _interval_ms, false);
        _sts.join();
        start_vtime_sec = os::elapsedVTime();
      }
      n_logs++;
    }
    // Make sure we harvest the PYA, if any.
    (void)_cg1r->get_pya();
  }
  _sts.leave();
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
    // wait until started is set.
    if (G1RSBarrierUseQueue) {
      queueBasedRefinement();
    } else {
      traversalBasedRefinement();
    }
    _sts.join();
    _co_tracker.update();
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
  if (TraceG1Refine) gclog_or_tty->print_cr("G1-Refine-yield");
  _sts.yield("G1 refine");
  if (TraceG1Refine) gclog_or_tty->print_cr("G1-Refine-yield-end");
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
  if (TraceG1Refine) gclog_or_tty->print_cr("G1-Refine-stop");
}

void ConcurrentG1RefineThread::print() {
  gclog_or_tty->print("\"Concurrent G1 Refinement Thread\" ");
  Thread::print();
  gclog_or_tty->cr();
}

void ConcurrentG1RefineThread::set_do_traversal(bool b) {
  _do_traversal = b;
}
