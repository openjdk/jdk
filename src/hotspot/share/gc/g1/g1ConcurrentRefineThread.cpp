/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1BarrierSet.hpp"
#include "gc/g1/g1ConcurrentRefine.hpp"
#include "gc/g1/g1ConcurrentRefineStats.hpp"
#include "gc/g1/g1ConcurrentRefineThread.hpp"
#include "gc/g1/g1DirtyCardQueue.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "logging/log.hpp"
#include "runtime/cpuTimeCounters.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"
#include "runtime/thread.hpp"
#include "utilities/debug.hpp"
#include "utilities/formatBuffer.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ticks.hpp"

G1ConcurrentRefineThread::G1ConcurrentRefineThread(G1ConcurrentRefine* cr, uint worker_id) :
  ConcurrentGCThread(),
  _notifier(Mutex::nosafepoint, FormatBuffer<>("G1 Refine#%d", worker_id), true),
  _requested_active(false),
  _refinement_stats(),
  _worker_id(worker_id),
  _cr(cr)
{
  // set name
  set_name("G1 Refine#%d", worker_id);
}

void G1ConcurrentRefineThread::run_service() {
  while (wait_for_completed_buffers()) {
    SuspendibleThreadSetJoiner sts_join;
    G1ConcurrentRefineStats active_stats_start = _refinement_stats;
    report_active("Activated");
    while (!should_terminate()) {
      if (sts_join.should_yield()) {
        report_inactive("Paused", _refinement_stats - active_stats_start);
        sts_join.yield();
        // Reset after yield rather than accumulating across yields, else a
        // very long running thread could overflow.
        active_stats_start = _refinement_stats;
        report_active("Resumed");
      } else if (maybe_deactivate()) {
        break;
      } else {
        do_refinement_step();
      }
    }
    report_inactive("Deactivated", _refinement_stats - active_stats_start);
    update_perf_counter_cpu_time();
  }

  log_debug(gc, refine)("Stopping %d", _worker_id);
}

void G1ConcurrentRefineThread::report_active(const char* reason) const {
  log_trace(gc, refine)("%s worker %u, current: %zu",
                        reason,
                        _worker_id,
                        G1BarrierSet::dirty_card_queue_set().num_cards());
}

void G1ConcurrentRefineThread::report_inactive(const char* reason,
                                               const G1ConcurrentRefineStats& stats) const {
  log_trace(gc, refine)
           ("%s worker %u, cards: %zu, refined %zu, rate %1.2fc/ms",
            reason,
            _worker_id,
            G1BarrierSet::dirty_card_queue_set().num_cards(),
            stats.refined_cards(),
            stats.refinement_rate_ms());
}

void G1ConcurrentRefineThread::activate() {
  assert(this != Thread::current(), "precondition");
  MonitorLocker ml(&_notifier, Mutex::_no_safepoint_check_flag);
  if (!_requested_active || should_terminate()) {
    _requested_active = true;
    ml.notify();
  }
}

bool G1ConcurrentRefineThread::maybe_deactivate() {
  assert(this == Thread::current(), "precondition");
  if (cr()->is_thread_wanted(_worker_id)) {
    return false;
  } else {
    MutexLocker ml(&_notifier, Mutex::_no_safepoint_check_flag);
    bool requested = _requested_active;
    _requested_active = false;
    return !requested;  // Deactivate only if not recently requested active.
  }
}

bool G1ConcurrentRefineThread::try_refinement_step(size_t stop_at) {
  assert(this == Thread::current(), "precondition");
  return _cr->try_refinement_step(_worker_id, stop_at, &_refinement_stats);
}

void G1ConcurrentRefineThread::stop_service() {
  activate();
}

jlong G1ConcurrentRefineThread::cpu_time() {
  return os::thread_cpu_time(this);
}

// The (single) primary thread drives the controller for the refinement threads.
class G1PrimaryConcurrentRefineThread final : public G1ConcurrentRefineThread {
  bool wait_for_completed_buffers() override;
  bool maybe_deactivate() override;
  void do_refinement_step() override;
  // Updates jstat cpu usage for all refinement threads.
  void update_perf_counter_cpu_time() override;

public:
  G1PrimaryConcurrentRefineThread(G1ConcurrentRefine* cr) :
    G1ConcurrentRefineThread(cr, 0)
  {}
};

// When inactive, the primary thread periodically wakes up and requests
// adjustment of the number of active refinement threads.
bool G1PrimaryConcurrentRefineThread::wait_for_completed_buffers() {
  assert(this == Thread::current(), "precondition");
  MonitorLocker ml(notifier(), Mutex::_no_safepoint_check_flag);
  if (!requested_active() && !should_terminate()) {
    // Rather than trying to be smart about spurious wakeups, we just treat
    // them as timeouts.
    ml.wait(cr()->adjust_threads_wait_ms());
  }
  // Record adjustment needed whenever reactivating.
  cr()->record_thread_adjustment_needed();
  return !should_terminate();
}

bool G1PrimaryConcurrentRefineThread::maybe_deactivate() {
  // Don't deactivate while needing to adjust the number of active threads.
  return !cr()->is_thread_adjustment_needed() &&
         G1ConcurrentRefineThread::maybe_deactivate();
}

void G1PrimaryConcurrentRefineThread::do_refinement_step() {
  // Try adjustment first.  If it succeeds then don't do any refinement this
  // round.  This thread may have just woken up but no threads are currently
  // needed, which is common.  In this case we want to just go back to
  // waiting, with a minimum of fuss; in particular, don't do any "premature"
  // refinement.  However, adjustment may be pending but temporarily
  // blocked. In that case we *do* try refinement, rather than possibly
  // uselessly spinning while waiting for adjustment to succeed.
  if (!cr()->adjust_threads_periodically()) {
    // No adjustment, so try refinement, with the target as a cuttoff.
    if (!try_refinement_step(cr()->pending_cards_target())) {
      // Refinement was cut off, so proceed with fewer threads.
      cr()->reduce_threads_wanted();
    }
  }
}

void G1PrimaryConcurrentRefineThread::update_perf_counter_cpu_time() {
  if (UsePerfData) {
    ThreadTotalCPUTimeClosure tttc(CPUTimeGroups::CPUTimeType::gc_conc_refine);
    cr()->threads_do(&tttc);
  }
}

class G1SecondaryConcurrentRefineThread final : public G1ConcurrentRefineThread {
  bool wait_for_completed_buffers() override;
  void do_refinement_step() override;
  void update_perf_counter_cpu_time() override { /* Nothing to do. The primary thread does all the work. */ }

public:
  G1SecondaryConcurrentRefineThread(G1ConcurrentRefine* cr, uint worker_id) :
    G1ConcurrentRefineThread(cr, worker_id)
  {
    assert(worker_id > 0, "precondition");
  }
};

bool G1SecondaryConcurrentRefineThread::wait_for_completed_buffers() {
  assert(this == Thread::current(), "precondition");
  MonitorLocker ml(notifier(), Mutex::_no_safepoint_check_flag);
  while (!requested_active() && !should_terminate()) {
    ml.wait();
  }
  return !should_terminate();
}

void G1SecondaryConcurrentRefineThread::do_refinement_step() {
  assert(this == Thread::current(), "precondition");
  // Secondary threads ignore the target and just drive the number of pending
  // dirty cards down.  The primary thread is responsible for noticing the
  // target has been reached and reducing the number of wanted threads.  This
  // makes the control of wanted threads all under the primary, while avoiding
  // useless spinning by secondary threads until the primary thread notices.
  // (Useless spinning is still possible if there are no pending cards, but
  // that should rarely happen.)
  try_refinement_step(0);
}

G1ConcurrentRefineThread*
G1ConcurrentRefineThread::create(G1ConcurrentRefine* cr, uint worker_id) {
  G1ConcurrentRefineThread* crt;
  if (worker_id == 0) {
    crt = new (std::nothrow) G1PrimaryConcurrentRefineThread(cr);
  } else {
    crt = new (std::nothrow) G1SecondaryConcurrentRefineThread(cr, worker_id);
  }
  if (crt != nullptr) {
    crt->create_and_start();
  }
  return crt;
}
