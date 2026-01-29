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

#include "classfile/classLoaderDataGraph.hpp"
#include "gc/g1/g1Analytics.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1ConcurrentMark.inline.hpp"
#include "gc/g1/g1ConcurrentMarkThread.inline.hpp"
#include "gc/g1/g1MMUTracker.hpp"
#include "gc/g1/g1Policy.hpp"
#include "gc/g1/g1RemSet.hpp"
#include "gc/g1/g1Trace.hpp"
#include "gc/g1/g1VMOperations.hpp"
#include "gc/shared/concurrentGCBreakpoints.hpp"
#include "gc/shared/gcId.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/cpuTimeCounters.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/debug.hpp"
#include "utilities/formatBuffer.hpp"
#include "utilities/ticks.hpp"

G1ConcurrentMarkThread::G1ConcurrentMarkThread(G1ConcurrentMark* cm) :
  ConcurrentGCThread(),
  _cm(cm),
  _state(Idle)
{
  set_name("G1 Main Marker");
  create_and_start();
}

double G1ConcurrentMarkThread::mmu_delay_end(G1Policy* policy, bool remark) {
  // There are 3 reasons to use SuspendibleThreadSetJoiner.
  // 1. To avoid concurrency problem.
  //    - G1MMUTracker::add_pause(), when_sec() and when_max_gc_sec() can be called
  //      concurrently from ConcurrentMarkThread and VMThread.
  // 2. If currently a gc is running, but it has not yet updated the MMU,
  //    we will not forget to consider that pause in the MMU calculation.
  // 3. If currently a gc is running, ConcurrentMarkThread will wait it to be finished.
  //    And then sleep for predicted amount of time by delay_to_keep_mmu().
  SuspendibleThreadSetJoiner sts_join;

  const G1Analytics* analytics = policy->analytics();
  double prediction_ms = remark ? analytics->predict_remark_time_ms()
                                : analytics->predict_cleanup_time_ms();
  double prediction = prediction_ms / MILLIUNITS;
  G1MMUTracker *mmu_tracker = policy->mmu_tracker();
  double now = os::elapsedTime();
  return now + mmu_tracker->when_sec(now, prediction);
}

void G1ConcurrentMarkThread::delay_to_keep_mmu(bool remark) {
  G1Policy* policy = G1CollectedHeap::heap()->policy();

  if (policy->use_adaptive_young_list_length()) {
    double delay_end_sec = mmu_delay_end(policy, remark);
    // Wait for timeout or thread termination request.
    MonitorLocker ml(G1CGC_lock, Monitor::_no_safepoint_check_flag);
    while (!_cm->has_aborted() && !should_terminate()) {
      double sleep_time_sec = (delay_end_sec - os::elapsedTime());
      jlong sleep_time_ms = ceil(sleep_time_sec * MILLIUNITS);
      if (sleep_time_ms <= 0) {
        break;                  // Passed end time.
      } else if (ml.wait(sleep_time_ms)) {
        break;                  // Timeout => reached end time.
      }
      // Other (possibly spurious) wakeup.  Retry with updated sleep time.
    }
  }
}

class G1ConcPhaseTimer : public GCTraceConcTimeImpl<LogLevel::Info, LOG_TAGS(gc, marking)> {
  G1ConcurrentMark* _cm;

 public:
  G1ConcPhaseTimer(G1ConcurrentMark* cm, const char* title) :
    GCTraceConcTimeImpl<LogLevel::Info,  LogTag::_gc, LogTag::_marking>(title),
    _cm(cm)
  {
    _cm->gc_timer_cm()->register_gc_concurrent_start(title);
  }

  ~G1ConcPhaseTimer() {
    _cm->gc_timer_cm()->register_gc_concurrent_end();
  }
};

void G1ConcurrentMarkThread::run_service() {
  while (wait_for_next_cycle()) {
    assert(in_progress(), "must be");

    GCIdMark gc_id_mark;
    FormatBuffer<128> title("Concurrent %s Cycle", _state == FullMark ? "Mark" : "Undo");
    GCTraceConcTime(Info, gc) tt(title);

    concurrent_cycle_start();

    if (_state == FullMark) {
      concurrent_mark_cycle_do();
    } else {
      assert(_state == UndoMark, "Must do undo mark but is %d", _state);
      concurrent_undo_cycle_do();
    }

    concurrent_cycle_end(_state == FullMark && !_cm->has_aborted());

    update_perf_counter_cpu_time();
  }
  _cm->root_regions()->cancel_scan();
}

void G1ConcurrentMarkThread::stop_service() {
  if (in_progress()) {
    // We are not allowed to abort the marking threads during root region scan.
    // Needs to be done separately.
    _cm->root_region_scan_abort_and_wait();

    _cm->abort_marking_threads();
  }

  MutexLocker ml(G1CGC_lock, Mutex::_no_safepoint_check_flag);
  G1CGC_lock->notify_all();
}

bool G1ConcurrentMarkThread::wait_for_next_cycle() {
  MonitorLocker ml(G1CGC_lock, Mutex::_no_safepoint_check_flag);
  while (!in_progress() && !should_terminate()) {
    ml.wait();
  }

  return !should_terminate();
}

bool G1ConcurrentMarkThread::phase_clear_cld_claimed_marks() {
  G1ConcPhaseTimer p(_cm, "Concurrent Clear Claimed Marks");
  ClassLoaderDataGraph::clear_claimed_marks();
  return _cm->has_aborted();
}

bool G1ConcurrentMarkThread::phase_scan_root_regions() {
  G1ConcPhaseTimer p(_cm, "Concurrent Scan Root Regions");
  _cm->scan_root_regions();
  update_perf_counter_cpu_time();
  return _cm->has_aborted();
}

bool G1ConcurrentMarkThread::phase_mark_loop() {
  Ticks mark_start = Ticks::now();
  log_info(gc, marking)("Concurrent Mark");

  for (uint iter = 1; true; ++iter) {
    // Subphase 1: Mark From Roots.
    if (subphase_mark_from_roots()) return true;

    // Subphase 2: Preclean (optional)
    if (G1UseReferencePrecleaning) {
      if (subphase_preclean()) return true;
    }

    // Subphase 3: Wait for Remark.
    if (subphase_delay_to_keep_mmu_before_remark()) return true;

    // Subphase 4: Remark pause
    if (subphase_remark()) return true;

    // Check if we need to restart the marking loop.
    if (!mark_loop_needs_restart()) break;

    log_info(gc, marking)("Concurrent Mark Restart for Mark Stack Overflow (iteration #%u)",
                          iter);
  }

  log_info(gc, marking)("Concurrent Mark %.3fms",
                        (Ticks::now() - mark_start).seconds() * 1000.0);

  return false;
}

bool G1ConcurrentMarkThread::mark_loop_needs_restart() const {
  return _cm->has_overflown();
}

bool G1ConcurrentMarkThread::subphase_mark_from_roots() {
  ConcurrentGCBreakpoints::at("AFTER MARKING STARTED");
  G1ConcPhaseTimer p(_cm, "Concurrent Mark From Roots");
  _cm->mark_from_roots();
  return _cm->has_aborted();
}

bool G1ConcurrentMarkThread::subphase_preclean() {
  G1ConcPhaseTimer p(_cm, "Concurrent Preclean");
  _cm->preclean();
  return _cm->has_aborted();
}

bool G1ConcurrentMarkThread::subphase_delay_to_keep_mmu_before_remark() {
  delay_to_keep_mmu(true /* remark */);
  return _cm->has_aborted();
}

bool G1ConcurrentMarkThread::subphase_remark() {
  ConcurrentGCBreakpoints::at("BEFORE MARKING COMPLETED");
  update_perf_counter_cpu_time();
  VM_G1PauseRemark op;
  VMThread::execute(&op);
  return _cm->has_aborted();
}

bool G1ConcurrentMarkThread::phase_rebuild_and_scrub() {
  ConcurrentGCBreakpoints::at("AFTER REBUILD STARTED");
  G1ConcPhaseTimer p(_cm, "Concurrent Rebuild Remembered Sets and Scrub Regions");
  _cm->rebuild_and_scrub();
  update_perf_counter_cpu_time();
  return _cm->has_aborted();
}

bool G1ConcurrentMarkThread::phase_delay_to_keep_mmu_before_cleanup() {
  delay_to_keep_mmu(false /* cleanup */);
  return _cm->has_aborted();
}

bool G1ConcurrentMarkThread::phase_cleanup() {
  ConcurrentGCBreakpoints::at("BEFORE REBUILD COMPLETED");
  VM_G1PauseCleanup op;
  VMThread::execute(&op);
  return _cm->has_aborted();
}

bool G1ConcurrentMarkThread::phase_clear_bitmap_for_next_mark() {
  ConcurrentGCBreakpoints::at("AFTER CLEANUP STARTED");
  G1ConcPhaseTimer p(_cm, "Concurrent Cleanup for Next Mark");
  _cm->cleanup_for_next_mark();
  return _cm->has_aborted();
}

void G1ConcurrentMarkThread::concurrent_cycle_start() {
  _cm->concurrent_cycle_start();
}

void G1ConcurrentMarkThread::concurrent_mark_cycle_do() {
  HandleMark hm(Thread::current());
  ResourceMark rm;

  // We have to ensure that we finish scanning the root regions
  // before the next GC takes place. To ensure this we have to
  // make sure that we do not join the STS until the root regions
  // have been scanned. If we did then it's possible that a
  // subsequent GC could block us from joining the STS and proceed
  // without the root regions have been scanned which would be a
  // correctness issue.
  //
  // So do not return before the scan root regions phase as a GC waits for a
  // notification from it.
  //
  // For the same reason ConcurrentGCBreakpoints (in the phase methods) before
  // here risk deadlock, because a young GC must wait for root region scanning.
  //
  // We can not easily abort before root region scan either because of the
  // reasons mentioned in G1CollectedHeap::abort_concurrent_cycle().

  // Phase 1: Scan root regions.
  if (phase_scan_root_regions()) return;

  // Phase 2: Actual mark loop.
  if (phase_mark_loop()) return;

  // Phase 3: Rebuild remembered sets and scrub dead objects.
  if (phase_rebuild_and_scrub()) return;

  // Phase 4: Wait for Cleanup.
  if (phase_delay_to_keep_mmu_before_cleanup()) return;

  // Phase 5: Cleanup pause
  if (phase_cleanup()) return;

  // Phase 6: Clear CLD claimed marks.
  if (phase_clear_cld_claimed_marks()) return;

  // Phase 7: Clear bitmap for next mark.
  phase_clear_bitmap_for_next_mark();
}

void G1ConcurrentMarkThread::concurrent_undo_cycle_do() {
  HandleMark hm(Thread::current());
  ResourceMark rm;

  // We can (and should) abort if there has been a concurrent cycle abort for
  // some reason.
  if (_cm->has_aborted()) { return; }

  _cm->flush_all_task_caches();

  // Phase 1: Clear CLD claimed marks.
  if (phase_clear_cld_claimed_marks()) return;

  // Phase 2: Clear bitmap for next mark.
  phase_clear_bitmap_for_next_mark();
}

void G1ConcurrentMarkThread::concurrent_cycle_end(bool mark_cycle_completed) {
  ConcurrentGCBreakpoints::at("BEFORE CLEANUP COMPLETED");
  // Update the number of full collections that have been
  // completed. This will also notify the G1OldGCCount_lock in case a
  // Java thread is waiting for a full GC to happen (e.g., it
  // called System.gc() with +ExplicitGCInvokesConcurrent).
  SuspendibleThreadSetJoiner sts_join;
  G1CollectedHeap::heap()->increment_old_marking_cycles_completed(true /* concurrent */,
                                                                  mark_cycle_completed /* heap_examined */);

  _cm->concurrent_cycle_end(mark_cycle_completed);
  ConcurrentGCBreakpoints::notify_active_to_idle();
}

void G1ConcurrentMarkThread::update_perf_counter_cpu_time() {
  if (!UsePerfData) {
    return;
  }
  ThreadTotalCPUTimeClosure tttc(CPUTimeGroups::CPUTimeType::gc_conc_mark);
  tttc.do_thread(this);
  _cm->threads_do(&tttc);
}
