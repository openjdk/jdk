/*
 * Copyright (c) 2001, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1BarrierSet.hpp"
#include "gc/g1/g1ConcurrentRefine.hpp"
#include "gc/g1/g1ConcurrentRefineThread.hpp"
#include "gc/g1/g1DirtyCardQueue.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/mutexLocker.hpp"

G1ConcurrentRefineThread::G1ConcurrentRefineThread(G1ConcurrentRefine* cr, uint worker_id) :
  ConcurrentGCThread(),
  _vtime_start(0.0),
  _vtime_accum(0.0),
  _worker_id(worker_id),
  _active(false),
  _monitor(NULL),
  _cr(cr)
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

  // set name
  set_name("G1 Refine#%d", worker_id);
  create_and_start();
}

void G1ConcurrentRefineThread::wait_for_completed_buffers() {
  MutexLockerEx x(_monitor, Mutex::_no_safepoint_check_flag);
  while (!should_terminate() && !is_active()) {
    _monitor->wait(Mutex::_no_safepoint_check_flag);
  }
}

bool G1ConcurrentRefineThread::is_active() {
  G1DirtyCardQueueSet& dcqs = G1BarrierSet::dirty_card_queue_set();
  return is_primary() ? dcqs.process_completed_buffers() : _active;
}

void G1ConcurrentRefineThread::activate() {
  MutexLockerEx x(_monitor, Mutex::_no_safepoint_check_flag);
  if (!is_primary()) {
    set_active(true);
  } else {
    G1DirtyCardQueueSet& dcqs = G1BarrierSet::dirty_card_queue_set();
    dcqs.set_process_completed_buffers(true);
  }
  _monitor->notify();
}

void G1ConcurrentRefineThread::deactivate() {
  MutexLockerEx x(_monitor, Mutex::_no_safepoint_check_flag);
  if (!is_primary()) {
    set_active(false);
  } else {
    G1DirtyCardQueueSet& dcqs = G1BarrierSet::dirty_card_queue_set();
    dcqs.set_process_completed_buffers(false);
  }
}

void G1ConcurrentRefineThread::run_service() {
  _vtime_start = os::elapsedVTime();

  while (!should_terminate()) {
    // Wait for work
    wait_for_completed_buffers();
    if (should_terminate()) {
      break;
    }

    size_t buffers_processed = 0;
    log_debug(gc, refine)("Activated worker %d, on threshold: " SIZE_FORMAT ", current: " SIZE_FORMAT,
                          _worker_id, _cr->activation_threshold(_worker_id),
                           G1BarrierSet::dirty_card_queue_set().completed_buffers_num());

    {
      SuspendibleThreadSetJoiner sts_join;

      while (!should_terminate()) {
        if (sts_join.should_yield()) {
          sts_join.yield();
          continue;             // Re-check for termination after yield delay.
        }

        if (!_cr->do_refinement_step(_worker_id)) {
          break;
        }
        ++buffers_processed;
      }
    }

    deactivate();
    log_debug(gc, refine)("Deactivated worker %d, off threshold: " SIZE_FORMAT
                          ", current: " SIZE_FORMAT ", processed: " SIZE_FORMAT,
                          _worker_id, _cr->deactivation_threshold(_worker_id),
                          G1BarrierSet::dirty_card_queue_set().completed_buffers_num(),
                          buffers_processed);

    if (os::supports_vtime()) {
      _vtime_accum = (os::elapsedVTime() - _vtime_start);
    } else {
      _vtime_accum = 0.0;
    }
  }

  log_debug(gc, refine)("Stopping %d", _worker_id);
}

void G1ConcurrentRefineThread::stop_service() {
  MutexLockerEx x(_monitor, Mutex::_no_safepoint_check_flag);
  _monitor->notify();
}
