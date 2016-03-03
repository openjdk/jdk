/*
 * Copyright (c) 2001, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/concurrentG1Refine.hpp"
#include "gc/g1/concurrentG1RefineThread.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1CollectorPolicy.hpp"
#include "gc/g1/suspendibleThreadSet.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/mutexLocker.hpp"

ConcurrentG1RefineThread::
ConcurrentG1RefineThread(ConcurrentG1Refine* cg1r, ConcurrentG1RefineThread *next,
                         CardTableEntryClosure* refine_closure,
                         uint worker_id_offset, uint worker_id) :
  ConcurrentGCThread(),
  _refine_closure(refine_closure),
  _worker_id_offset(worker_id_offset),
  _worker_id(worker_id),
  _active(false),
  _next(next),
  _monitor(NULL),
  _cg1r(cg1r),
  _vtime_accum(0.0)
{

  // Each thread has its own monitor. The i-th thread is responsible for signaling
  // to thread i+1 if the number of buffers in the queue exceeds a threshold for this
  // thread. Monitors are also used to wake up the threads during termination.
  // The 0th (primary) worker is notified by mutator threads and has a special monitor.
  if (!is_primary()) {
    _monitor = new Monitor(Mutex::nonleaf, "Refinement monitor", true,
                           Monitor::_safepoint_check_never);
  } else {
    _monitor = DirtyCardQ_CBL_mon;
  }
  initialize();

  // set name
  set_name("G1 Refine#%d", worker_id);
  create_and_start();
}

void ConcurrentG1RefineThread::initialize() {
  // Current thread activation threshold
  _threshold = MIN2<int>(cg1r()->thread_threshold_step() * (_worker_id + 1) + cg1r()->green_zone(),
                         cg1r()->yellow_zone());
  // A thread deactivates once the number of buffer reached a deactivation threshold
  _deactivation_threshold = MAX2<int>(_threshold - cg1r()->thread_threshold_step(), cg1r()->green_zone());
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
  return is_primary() ? dcqs.process_completed_buffers() : _active;
}

void ConcurrentG1RefineThread::activate() {
  MutexLockerEx x(_monitor, Mutex::_no_safepoint_check_flag);
  if (!is_primary()) {
    set_active(true);
  } else {
    DirtyCardQueueSet& dcqs = JavaThread::dirty_card_queue_set();
    dcqs.set_process_completed(true);
  }
  _monitor->notify();
}

void ConcurrentG1RefineThread::deactivate() {
  MutexLockerEx x(_monitor, Mutex::_no_safepoint_check_flag);
  if (!is_primary()) {
    set_active(false);
  } else {
    DirtyCardQueueSet& dcqs = JavaThread::dirty_card_queue_set();
    dcqs.set_process_completed(false);
  }
}

void ConcurrentG1RefineThread::run() {
  initialize_in_thread();
  wait_for_universe_init();

  run_service();

  terminate();
}

void ConcurrentG1RefineThread::run_service() {
  _vtime_start = os::elapsedVTime();

  while (!_should_terminate) {
    // Wait for work
    wait_for_completed_buffers();
    if (_should_terminate) {
      break;
    }

    DirtyCardQueueSet& dcqs = JavaThread::dirty_card_queue_set();
    log_debug(gc, refine)("Activated %d, on threshold: %d, current: %d",
                          _worker_id, _threshold, dcqs.completed_buffers_num());

    {
      SuspendibleThreadSetJoiner sts_join;

      do {
        int curr_buffer_num = (int)dcqs.completed_buffers_num();
        // If the number of the buffers falls down into the yellow zone,
        // that means that the transition period after the evacuation pause has ended.
        if (dcqs.completed_queue_padding() > 0 && curr_buffer_num <= cg1r()->yellow_zone()) {
          dcqs.set_completed_queue_padding(0);
        }

        // Check if we need to activate the next thread.
        if (_next != NULL && !_next->is_active() && curr_buffer_num > _next->_threshold) {
          _next->activate();
        }
      } while (dcqs.apply_closure_to_completed_buffer(_refine_closure,
                                                      _worker_id + _worker_id_offset,
                                                      _deactivation_threshold,
                                                      false /* during_pause */));

      deactivate();
      log_debug(gc, refine)("Deactivated %d, off threshold: %d, current: %d",
                            _worker_id, _deactivation_threshold,
                            dcqs.completed_buffers_num());
    }

    if (os::supports_vtime()) {
      _vtime_accum = (os::elapsedVTime() - _vtime_start);
    } else {
      _vtime_accum = 0.0;
    }
  }

  log_debug(gc, refine)("Stopping %d", _worker_id);
}

void ConcurrentG1RefineThread::stop() {
  // it is ok to take late safepoints here, if needed
  {
    MutexLockerEx mu(Terminator_lock);
    _should_terminate = true;
  }

  stop_service();

  {
    MutexLockerEx mu(Terminator_lock);
    while (!_has_terminated) {
      Terminator_lock->wait();
    }
  }
}

void ConcurrentG1RefineThread::stop_service() {
  MutexLockerEx x(_monitor, Mutex::_no_safepoint_check_flag);
  _monitor->notify();
}
