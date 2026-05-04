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
#include "gc/g1/g1CardTableClaimTable.inline.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1ConcurrentRefine.hpp"
#include "gc/g1/g1ConcurrentRefineStats.inline.hpp"
#include "gc/g1/g1ConcurrentRefineSweepTask.hpp"
#include "gc/g1/g1ConcurrentRefineThread.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
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

G1ConcurrentRefineThread::G1ConcurrentRefineThread(G1ConcurrentRefine* cr) :
  ConcurrentGCThread(),
  _notifier(Mutex::nosafepoint, "G1 Refine Control", true),
  _requested_active(false),
  _cr(cr)
{
  set_name("G1 Refine Control");
}

void G1ConcurrentRefineThread::run_service() {
  while (wait_for_work()) {
    SuspendibleThreadSetJoiner sts_join;
    report_active("Activated");
    while (!should_terminate()) {
      if (sts_join.should_yield()) {
        report_inactive("Paused");
        sts_join.yield();
        // Reset after yield rather than accumulating across yields, else a
        // very long running thread could overflow.
        report_active("Resumed");
      }
      // Look if we want to do refinement. If we don't then don't do any refinement
      // this. This thread may have just woken up but no threads are currently
      // needed, which is common.  In this case we want to just go back to
      // waiting, with a minimum of fuss; in particular, don't do any "premature"
      // refinement.  However, adjustment may be pending but temporarily
      // blocked. In that case we wait for adjustment to succeed.
      Ticks adjust_start = Ticks::now();
      if (cr()->adjust_num_threads_periodically()) {
        GCTraceTime(Info, gc, refine) tm("Concurrent Refine Cycle");
        do_refinement();
      } else {
        log_debug(gc, refine)("Concurrent Refine Adjust Only (#threads wanted: %u adjustment_needed: %s wait_for_heap_lock: %s) %.2fms",
                              cr()->num_threads_wanted(),
                              BOOL_TO_STR(cr()->is_thread_adjustment_needed()),
                              BOOL_TO_STR(cr()->heap_was_locked()),
                              (Ticks::now() - adjust_start).seconds() * MILLIUNITS);

        deactivate();
        break;
      }
    }
    report_inactive("Deactivated");
    update_perf_counter_cpu_time();
  }

  log_debug(gc, refine)("Stopping %s", name());
}

void G1ConcurrentRefineThread::report_active(const char* reason) const {
  log_trace(gc, refine)("%s active (%s)", name(), reason);
}

void G1ConcurrentRefineThread::report_inactive(const char* reason) const {
  log_trace(gc, refine)("%s inactive (%s)", name(), reason);
}

void G1ConcurrentRefineThread::activate() {
  assert(this != Thread::current(), "precondition");
  MonitorLocker ml(&_notifier, Mutex::_no_safepoint_check_flag);
  if (!_requested_active || should_terminate()) {
    _requested_active = true;
    ml.notify();
  }
}

bool G1ConcurrentRefineThread::deactivate() {
  assert(this == Thread::current(), "precondition");
  MutexLocker ml(&_notifier, Mutex::_no_safepoint_check_flag);
  bool requested = _requested_active;
  _requested_active = false;
  return !requested;  // Deactivate only if not recently requested active.
}

void G1ConcurrentRefineThread::stop_service() {
  activate();
}

jlong G1ConcurrentRefineThread::cpu_time() {
  return os::thread_cpu_time(this);
}

// When inactive, the control thread periodically wakes up to check if there is
// refinement work pending.
bool G1ConcurrentRefineThread::wait_for_work() {
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

void G1ConcurrentRefineThread::do_refinement() {
  G1ConcurrentRefineSweepState& state = _cr->sweep_state();

  state.start_work();

  // Swap card tables.

  // 1. Global card table
  if (!state.swap_global_card_table()) {
    log_debug(gc, refine)("GC pause after Global Card Table Swap");
    return;
  }

  // 2. Java threads
  if (!state.swap_java_threads_ct()) {
    log_debug(gc, refine)("GC pause after Java Thread CT swap");
    return;
  }

  // 3. GC threads
  if (!state.swap_gc_threads_ct()) {
    log_debug(gc, refine)("GC pause after GC Thread CT swap");
    return;
  }

  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  jlong epoch_yield_duration = g1h->yield_duration_in_refinement_epoch();
  jlong next_epoch_start = os::elapsed_counter();

  jlong total_yield_during_sweep_duration = 0;

  // 4. Snapshot heap.
  state.snapshot_heap();

  // 5. Sweep refinement table until done
  bool interrupted_by_gc = false;

  log_info(gc, task)("Concurrent Refine Sweep Using %u of %u Workers", _cr->num_threads_wanted(), _cr->max_num_threads());

  state.sweep_refinement_table_start();
  while (true) {
    bool completed = state.sweep_refinement_table_step();

    if (completed) {
      break;
    }

    if (SuspendibleThreadSet::should_yield()) {
      jlong yield_during_sweep_start = os::elapsed_counter();
      SuspendibleThreadSet::yield();

      // The yielding may have completed the task, check.
      if (!state.is_in_progress()) {
        log_debug(gc, refine)("GC completed sweeping, aborting concurrent operation");
        interrupted_by_gc = true;
        break;
      } else {
        jlong yield_during_sweep_duration = os::elapsed_counter() - yield_during_sweep_start;
        log_debug(gc, refine)("Yielded from card table sweeping for %.2fms, no GC inbetween, continue",
                              TimeHelper::counter_to_millis(yield_during_sweep_duration));
        total_yield_during_sweep_duration += yield_during_sweep_duration;
      }
    }
  }

  if (!interrupted_by_gc) {
    GCTraceTime(Info, gc, refine) tm("Concurrent Refine Complete Work");

    state.add_yield_during_sweep_duration(total_yield_during_sweep_duration);

    state.complete_work(true);

    G1CollectedHeap* g1h = G1CollectedHeap::heap();
    G1Policy* policy = g1h->policy();
    G1ConcurrentRefineStats* stats = state.stats();
    policy->record_refinement_stats(stats);

    {
      // The young gen revising mechanism reads the predictor and the values set
      // here. Avoid inconsistencies by locking.
      MutexLocker x(G1ReviseYoungLength_lock, Mutex::_no_safepoint_check_flag);
      policy->record_dirtying_stats(TimeHelper::counter_to_millis(G1CollectedHeap::heap()->last_refinement_epoch_start()),
                                    TimeHelper::counter_to_millis(next_epoch_start),
                                    stats->cards_pending(),
                                    TimeHelper::counter_to_millis(epoch_yield_duration),
                                    0 /* pending_cards_from_gc */,
                                    stats->cards_to_cset());
      G1CollectedHeap::heap()->set_last_refinement_epoch_start(next_epoch_start, epoch_yield_duration);
    }
    stats->reset();
  }
}

void G1ConcurrentRefineThread::update_perf_counter_cpu_time() {
  // The control thread is responsible for updating the CPU time for all workers.
  if (UsePerfData) {
    {
      ThreadTotalCPUTimeClosure tttc(CPUTimeGroups::CPUTimeType::gc_conc_refine);
      cr()->worker_threads_do(&tttc);
    }
    {
      ThreadTotalCPUTimeClosure tttc(CPUTimeGroups::CPUTimeType::gc_conc_refine_control);
      cr()->control_thread_do(&tttc);
    }
  }
}

G1ConcurrentRefineThread* G1ConcurrentRefineThread::create(G1ConcurrentRefine* cr) {
  G1ConcurrentRefineThread* crt = new (std::nothrow) G1ConcurrentRefineThread(cr);
  if (crt != nullptr) {
    crt->create_and_start();
  }
  return crt;
}
