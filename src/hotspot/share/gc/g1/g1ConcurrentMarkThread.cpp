/*
 * Copyright (c) 2001, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/handles.inline.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/debug.hpp"

// ======= Concurrent Mark Thread ========

G1ConcurrentMarkThread::G1ConcurrentMarkThread(G1ConcurrentMark* cm) :
  ConcurrentGCThread(),
  _vtime_start(0.0),
  _vtime_accum(0.0),
  _vtime_mark_accum(0.0),
  _cm(cm),
  _state(Idle)
{
  set_name("G1 Main Marker");
  create_and_start();
}

class CMRemark : public VoidClosure {
  G1ConcurrentMark* _cm;
public:
  CMRemark(G1ConcurrentMark* cm) : _cm(cm) {}

  void do_void(){
    _cm->remark();
  }
};

class CMCleanup : public VoidClosure {
  G1ConcurrentMark* _cm;
public:
  CMCleanup(G1ConcurrentMark* cm) : _cm(cm) {}

  void do_void(){
    _cm->cleanup();
  }
};

double G1ConcurrentMarkThread::mmu_delay_end(G1Policy* g1_policy, bool remark) {
  // There are 3 reasons to use SuspendibleThreadSetJoiner.
  // 1. To avoid concurrency problem.
  //    - G1MMUTracker::add_pause(), when_sec() and its variation(when_ms() etc..) can be called
  //      concurrently from ConcurrentMarkThread and VMThread.
  // 2. If currently a gc is running, but it has not yet updated the MMU,
  //    we will not forget to consider that pause in the MMU calculation.
  // 3. If currently a gc is running, ConcurrentMarkThread will wait it to be finished.
  //    And then sleep for predicted amount of time by delay_to_keep_mmu().
  SuspendibleThreadSetJoiner sts_join;

  const G1Analytics* analytics = g1_policy->analytics();
  double prediction_ms = remark ? analytics->predict_remark_time_ms()
                                : analytics->predict_cleanup_time_ms();
  double prediction = prediction_ms / MILLIUNITS;
  G1MMUTracker *mmu_tracker = g1_policy->mmu_tracker();
  double now = os::elapsedTime();
  return now + mmu_tracker->when_sec(now, prediction);
}

void G1ConcurrentMarkThread::delay_to_keep_mmu(G1Policy* g1_policy, bool remark) {
  if (g1_policy->use_adaptive_young_list_length()) {
    double delay_end_sec = mmu_delay_end(g1_policy, remark);
    // Wait for timeout or thread termination request.
    MonitorLocker ml(CGC_lock, Monitor::_no_safepoint_check_flag);
    while (!_cm->has_aborted()) {
      double sleep_time_sec = (delay_end_sec - os::elapsedTime());
      jlong sleep_time_ms = ceil(sleep_time_sec * MILLIUNITS);
      if (sleep_time_ms <= 0) {
        break;                  // Passed end time.
      } else if (ml.wait(sleep_time_ms, Monitor::_no_safepoint_check_flag)) {
        break;                  // Timeout => reached end time.
      } else if (should_terminate()) {
        break;                  // Wakeup for pending termination request.
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
  _vtime_start = os::elapsedVTime();

  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  G1Policy* policy = g1h->policy();

  while (!should_terminate()) {
    // wait until started is set.
    sleep_before_next_cycle();
    if (should_terminate()) {
      break;
    }

    GCIdMark gc_id_mark;

    _cm->concurrent_cycle_start();

    GCTraceConcTime(Info, gc) tt("Concurrent Cycle");
    {
      ResourceMark rm;
      HandleMark   hm;
      double cycle_start = os::elapsedVTime();

      {
        G1ConcPhaseTimer p(_cm, "Concurrent Clear Claimed Marks");
        ClassLoaderDataGraph::clear_claimed_marks();
      }

      // We have to ensure that we finish scanning the root regions
      // before the next GC takes place. To ensure this we have to
      // make sure that we do not join the STS until the root regions
      // have been scanned. If we did then it's possible that a
      // subsequent GC could block us from joining the STS and proceed
      // without the root regions have been scanned which would be a
      // correctness issue.

      {
        G1ConcPhaseTimer p(_cm, "Concurrent Scan Root Regions");
        _cm->scan_root_regions();
      }

      // Note: ConcurrentGCBreakpoints before here risk deadlock,
      // because a young GC must wait for root region scanning.

      // It would be nice to use the G1ConcPhaseTimer class here but
      // the "end" logging is inside the loop and not at the end of
      // a scope. Also, the timer doesn't support nesting.
      // Mimicking the same log output instead.
      jlong mark_start = os::elapsed_counter();
      log_info(gc, marking)("Concurrent Mark (%.3fs)",
                            TimeHelper::counter_to_seconds(mark_start));
      for (uint iter = 1; !_cm->has_aborted(); ++iter) {
        // Concurrent marking.
        {
          ConcurrentGCBreakpoints::at("AFTER MARKING STARTED");
          G1ConcPhaseTimer p(_cm, "Concurrent Mark From Roots");
          _cm->mark_from_roots();
        }
        if (_cm->has_aborted()) {
          break;
        }

        if (G1UseReferencePrecleaning) {
          G1ConcPhaseTimer p(_cm, "Concurrent Preclean");
          _cm->preclean();
        }
        if (_cm->has_aborted()) {
          break;
        }

        // Delay remark pause for MMU.
        double mark_end_time = os::elapsedVTime();
        jlong mark_end = os::elapsed_counter();
        _vtime_mark_accum += (mark_end_time - cycle_start);
        delay_to_keep_mmu(policy, true /* remark */);
        if (_cm->has_aborted()) {
          break;
        }

        // Pause Remark.
        ConcurrentGCBreakpoints::at("BEFORE MARKING COMPLETED");
        log_info(gc, marking)("Concurrent Mark (%.3fs, %.3fs) %.3fms",
                              TimeHelper::counter_to_seconds(mark_start),
                              TimeHelper::counter_to_seconds(mark_end),
                              TimeHelper::counter_to_millis(mark_end - mark_start));
        CMRemark cl(_cm);
        VM_G1Concurrent op(&cl, "Pause Remark");
        VMThread::execute(&op);
        if (_cm->has_aborted()) {
          break;
        } else if (!_cm->restart_for_overflow()) {
          break;                // Exit loop if no restart requested.
        } else {
          // Loop to restart for overflow.
          log_info(gc, marking)("Concurrent Mark Restart for Mark Stack Overflow (iteration #%u)",
                                iter);
        }
      }

      if (!_cm->has_aborted()) {
        G1ConcPhaseTimer p(_cm, "Concurrent Rebuild Remembered Sets");
        _cm->rebuild_rem_set_concurrently();
      }

      double end_time = os::elapsedVTime();
      // Update the total virtual time before doing this, since it will try
      // to measure it to get the vtime for this marking.
      _vtime_accum = (end_time - _vtime_start);

      if (!_cm->has_aborted()) {
        delay_to_keep_mmu(policy, false /* cleanup */);
      }

      if (!_cm->has_aborted()) {
        CMCleanup cl_cl(_cm);
        VM_G1Concurrent op(&cl_cl, "Pause Cleanup");
        VMThread::execute(&op);
      }

      // We now want to allow clearing of the marking bitmap to be
      // suspended by a collection pause.
      // We may have aborted just before the remark. Do not bother clearing the
      // bitmap then, as it has been done during mark abort.
      if (!_cm->has_aborted()) {
        G1ConcPhaseTimer p(_cm, "Concurrent Cleanup for Next Mark");
        _cm->cleanup_for_next_mark();
      }
    }

    // Update the number of full collections that have been
    // completed. This will also notify the G1OldGCCount_lock in case a
    // Java thread is waiting for a full GC to happen (e.g., it
    // called System.gc() with +ExplicitGCInvokesConcurrent).
    {
      SuspendibleThreadSetJoiner sts_join;
      g1h->increment_old_marking_cycles_completed(true /* concurrent */);

      _cm->concurrent_cycle_end();
      ConcurrentGCBreakpoints::notify_active_to_idle();
    }
  }
  _cm->root_regions()->cancel_scan();
}

void G1ConcurrentMarkThread::stop_service() {
  MutexLocker ml(CGC_lock, Mutex::_no_safepoint_check_flag);
  CGC_lock->notify_all();
}


void G1ConcurrentMarkThread::sleep_before_next_cycle() {
  // We join here because we don't want to do the "shouldConcurrentMark()"
  // below while the world is otherwise stopped.
  assert(!in_progress(), "should have been cleared");

  MonitorLocker ml(CGC_lock, Mutex::_no_safepoint_check_flag);
  while (!started() && !should_terminate()) {
    ml.wait();
  }

  if (started()) {
    set_in_progress();
  }
}
