/*
 * Copyright 2001-2010 Sun Microsystems, Inc.  All Rights Reserved.
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

ConcurrentG1RefineThread::
ConcurrentG1RefineThread(ConcurrentG1Refine* cg1r, ConcurrentG1RefineThread *next,
                         int worker_id_offset, int worker_id) :
  ConcurrentGCThread(),
  _worker_id_offset(worker_id_offset),
  _worker_id(worker_id),
  _active(false),
  _next(next),
  _monitor(NULL),
  _cg1r(cg1r),
  _vtime_accum(0.0)
{

  // Each thread has its own monitor. The i-th thread is responsible for signalling
  // to thread i+1 if the number of buffers in the queue exceeds a threashold for this
  // thread. Monitors are also used to wake up the threads during termination.
  // The 0th worker in notified by mutator threads and has a special monitor.
  // The last worker is used for young gen rset size sampling.
  if (worker_id > 0) {
    _monitor = new Monitor(Mutex::nonleaf, "Refinement monitor", true);
  } else {
    _monitor = DirtyCardQ_CBL_mon;
  }
  initialize();
  create_and_start();
}

void ConcurrentG1RefineThread::initialize() {
  if (_worker_id < cg1r()->worker_thread_num()) {
    // Current thread activation threshold
    _threshold = MIN2<int>(cg1r()->thread_threshold_step() * (_worker_id + 1) + cg1r()->green_zone(),
                           cg1r()->yellow_zone());
    // A thread deactivates once the number of buffer reached a deactivation threshold
    _deactivation_threshold = MAX2<int>(_threshold - cg1r()->thread_threshold_step(), cg1r()->green_zone());
  } else {
    set_active(true);
  }
}

void ConcurrentG1RefineThread::sample_young_list_rs_lengths() {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  G1CollectorPolicy* g1p = g1h->g1_policy();
  if (g1p->adaptive_young_list_length()) {
    int regions_visited = 0;
    g1h->young_list()->rs_length_sampling_init();
    while (g1h->young_list()->rs_length_sampling_more()) {
      g1h->young_list()->rs_length_sampling_next();
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

void ConcurrentG1RefineThread::run_young_rs_sampling() {
  DirtyCardQueueSet& dcqs = JavaThread::dirty_card_queue_set();
  _vtime_start = os::elapsedVTime();
  while(!_should_terminate) {
    _sts.join();
    sample_young_list_rs_lengths();
    _sts.leave();

    if (os::supports_vtime()) {
      _vtime_accum = (os::elapsedVTime() - _vtime_start);
    } else {
      _vtime_accum = 0.0;
    }

    MutexLockerEx x(_monitor, Mutex::_no_safepoint_check_flag);
    if (_should_terminate) {
      break;
    }
    _monitor->wait(Mutex::_no_safepoint_check_flag, G1ConcRefinementServiceIntervalMillis);
  }
}

void ConcurrentG1RefineThread::wait_for_completed_buffers() {
  DirtyCardQueueSet& dcqs = JavaThread::dirty_card_queue_set();
  MutexLockerEx x(_monitor, Mutex::_no_safepoint_check_flag);
  while (!_should_terminate && !is_active()) {
    _monitor->wait(Mutex::_no_safepoint_check_flag);
  }
}

bool ConcurrentG1RefineThread::is_active() {
  DirtyCardQueueSet& dcqs = JavaThread::dirty_card_queue_set();
  return _worker_id > 0 ? _active : dcqs.process_completed_buffers();
}

void ConcurrentG1RefineThread::activate() {
  MutexLockerEx x(_monitor, Mutex::_no_safepoint_check_flag);
  if (_worker_id > 0) {
    if (G1TraceConcRefinement) {
      DirtyCardQueueSet& dcqs = JavaThread::dirty_card_queue_set();
      gclog_or_tty->print_cr("G1-Refine-activated worker %d, on threshold %d, current %d",
                             _worker_id, _threshold, (int)dcqs.completed_buffers_num());
    }
    set_active(true);
  } else {
    DirtyCardQueueSet& dcqs = JavaThread::dirty_card_queue_set();
    dcqs.set_process_completed(true);
  }
  _monitor->notify();
}

void ConcurrentG1RefineThread::deactivate() {
  MutexLockerEx x(_monitor, Mutex::_no_safepoint_check_flag);
  if (_worker_id > 0) {
    if (G1TraceConcRefinement) {
      DirtyCardQueueSet& dcqs = JavaThread::dirty_card_queue_set();
      gclog_or_tty->print_cr("G1-Refine-deactivated worker %d, off threshold %d, current %d",
                             _worker_id, _deactivation_threshold, (int)dcqs.completed_buffers_num());
    }
    set_active(false);
  } else {
    DirtyCardQueueSet& dcqs = JavaThread::dirty_card_queue_set();
    dcqs.set_process_completed(false);
  }
}

void ConcurrentG1RefineThread::run() {
  initialize_in_thread();
  wait_for_universe_init();

  if (_worker_id >= cg1r()->worker_thread_num()) {
    run_young_rs_sampling();
    terminate();
    return;
  }

  _vtime_start = os::elapsedVTime();
  while (!_should_terminate) {
    DirtyCardQueueSet& dcqs = JavaThread::dirty_card_queue_set();

    // Wait for work
    wait_for_completed_buffers();

    if (_should_terminate) {
      break;
    }

    _sts.join();

    do {
      int curr_buffer_num = (int)dcqs.completed_buffers_num();
      // If the number of the buffers falls down into the yellow zone,
      // that means that the transition period after the evacuation pause has ended.
      if (dcqs.completed_queue_padding() > 0 && curr_buffer_num <= cg1r()->yellow_zone()) {
        dcqs.set_completed_queue_padding(0);
      }

      if (_worker_id > 0 && curr_buffer_num <= _deactivation_threshold) {
        // If the number of the buffer has fallen below our threshold
        // we should deactivate. The predecessor will reactivate this
        // thread should the number of the buffers cross the threshold again.
        deactivate();
        break;
      }

      // Check if we need to activate the next thread.
      if (_next != NULL && !_next->is_active() && curr_buffer_num > _next->_threshold) {
        _next->activate();
      }
    } while (dcqs.apply_closure_to_completed_buffer(_worker_id + _worker_id_offset, cg1r()->green_zone()));

    // We can exit the loop above while being active if there was a yield request.
    if (is_active()) {
      deactivate();
    }

    _sts.leave();

    if (os::supports_vtime()) {
      _vtime_accum = (os::elapsedVTime() - _vtime_start);
    } else {
      _vtime_accum = 0.0;
    }
  }
  assert(_should_terminate, "just checking");
  terminate();
}


void ConcurrentG1RefineThread::yield() {
  if (G1TraceConcRefinement) {
    gclog_or_tty->print_cr("G1-Refine-yield");
  }
  _sts.yield("G1 refine");
  if (G1TraceConcRefinement) {
    gclog_or_tty->print_cr("G1-Refine-yield-end");
  }
}

void ConcurrentG1RefineThread::stop() {
  // it is ok to take late safepoints here, if needed
  {
    MutexLockerEx mu(Terminator_lock);
    _should_terminate = true;
  }

  {
    MutexLockerEx x(_monitor, Mutex::_no_safepoint_check_flag);
    _monitor->notify();
  }

  {
    MutexLockerEx mu(Terminator_lock);
    while (!_has_terminated) {
      Terminator_lock->wait();
    }
  }
  if (G1TraceConcRefinement) {
    gclog_or_tty->print_cr("G1-Refine-stop");
  }
}

void ConcurrentG1RefineThread::print() const {
  print_on(tty);
}

void ConcurrentG1RefineThread::print_on(outputStream* st) const {
  st->print("\"G1 Concurrent Refinement Thread#%d\" ", _worker_id);
  Thread::print_on(st);
  st->cr();
}
