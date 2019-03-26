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
#include "classfile/classLoaderDataGraph.hpp"
#include "gc/g1/g1Analytics.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1ConcurrentMark.inline.hpp"
#include "gc/g1/g1ConcurrentMarkThread.inline.hpp"
#include "gc/g1/g1MMUTracker.hpp"
#include "gc/g1/g1Policy.hpp"
#include "gc/g1/g1RemSet.hpp"
#include "gc/g1/g1VMOperations.hpp"
#include "gc/shared/concurrentGCPhaseManager.hpp"
#include "gc/shared/gcId.hpp"
#include "gc/shared/gcTrace.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/debug.hpp"

// ======= Concurrent Mark Thread ========

// Check order in EXPAND_CURRENT_PHASES
STATIC_ASSERT(ConcurrentGCPhaseManager::UNCONSTRAINED_PHASE <
              ConcurrentGCPhaseManager::IDLE_PHASE);

#define EXPAND_CONCURRENT_PHASES(expander)                                 \
  expander(ANY, = ConcurrentGCPhaseManager::UNCONSTRAINED_PHASE, NULL)     \
  expander(IDLE, = ConcurrentGCPhaseManager::IDLE_PHASE, NULL)             \
  expander(CONCURRENT_CYCLE,, "Concurrent Cycle")                          \
  expander(CLEAR_CLAIMED_MARKS,, "Concurrent Clear Claimed Marks")         \
  expander(SCAN_ROOT_REGIONS,, "Concurrent Scan Root Regions")             \
  expander(CONCURRENT_MARK,, "Concurrent Mark")                            \
  expander(MARK_FROM_ROOTS,, "Concurrent Mark From Roots")                 \
  expander(PRECLEAN,, "Concurrent Preclean")                               \
  expander(BEFORE_REMARK,, NULL)                                           \
  expander(REMARK,, NULL)                                                  \
  expander(REBUILD_REMEMBERED_SETS,, "Concurrent Rebuild Remembered Sets") \
  expander(CLEANUP_FOR_NEXT_MARK,, "Concurrent Cleanup for Next Mark")     \
  /* */

class G1ConcurrentPhase : public AllStatic {
public:
  enum {
#define CONCURRENT_PHASE_ENUM(tag, value, ignore_title) tag value,
    EXPAND_CONCURRENT_PHASES(CONCURRENT_PHASE_ENUM)
#undef CONCURRENT_PHASE_ENUM
    PHASE_ID_LIMIT
  };
};

G1ConcurrentMarkThread::G1ConcurrentMarkThread(G1ConcurrentMark* cm) :
  ConcurrentGCThread(),
  _vtime_start(0.0),
  _vtime_accum(0.0),
  _vtime_mark_accum(0.0),
  _cm(cm),
  _state(Idle),
  _phase_manager_stack() {

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

double G1ConcurrentMarkThread::mmu_sleep_time(G1Policy* g1_policy, bool remark) {
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
  double now = os::elapsedTime();
  double prediction_ms = remark ? analytics->predict_remark_time_ms()
                                : analytics->predict_cleanup_time_ms();
  G1MMUTracker *mmu_tracker = g1_policy->mmu_tracker();
  return mmu_tracker->when_ms(now, prediction_ms);
}

void G1ConcurrentMarkThread::delay_to_keep_mmu(G1Policy* g1_policy, bool remark) {
  if (g1_policy->adaptive_young_list_length()) {
    jlong sleep_time_ms = mmu_sleep_time(g1_policy, remark);
    if (!_cm->has_aborted() && sleep_time_ms > 0) {
      os::sleep(this, sleep_time_ms, false);
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

static const char* const concurrent_phase_names[] = {
#define CONCURRENT_PHASE_NAME(tag, ignore_value, ignore_title) XSTR(tag),
  EXPAND_CONCURRENT_PHASES(CONCURRENT_PHASE_NAME)
#undef CONCURRENT_PHASE_NAME
  NULL                          // terminator
};
// Verify dense enum assumption.  +1 for terminator.
STATIC_ASSERT(G1ConcurrentPhase::PHASE_ID_LIMIT + 1 ==
              ARRAY_SIZE(concurrent_phase_names));

// Returns the phase number for name, or a negative value if unknown.
static int lookup_concurrent_phase(const char* name) {
  const char* const* names = concurrent_phase_names;
  for (uint i = 0; names[i] != NULL; ++i) {
    if (strcmp(name, names[i]) == 0) {
      return static_cast<int>(i);
    }
  }
  return -1;
}

// The phase must be valid and must have a title.
static const char* lookup_concurrent_phase_title(int phase) {
  static const char* const titles[] = {
#define CONCURRENT_PHASE_TITLE(ignore_tag, ignore_value, title) title,
    EXPAND_CONCURRENT_PHASES(CONCURRENT_PHASE_TITLE)
#undef CONCURRENT_PHASE_TITLE
  };
  // Verify dense enum assumption.
  STATIC_ASSERT(G1ConcurrentPhase::PHASE_ID_LIMIT == ARRAY_SIZE(titles));

  assert(0 <= phase, "precondition");
  assert((uint)phase < ARRAY_SIZE(titles), "precondition");
  const char* title = titles[phase];
  assert(title != NULL, "precondition");
  return title;
}

class G1ConcPhaseManager : public StackObj {
  G1ConcurrentMark* _cm;
  ConcurrentGCPhaseManager _manager;

public:
  G1ConcPhaseManager(int phase, G1ConcurrentMarkThread* thread) :
    _cm(thread->cm()),
    _manager(phase, thread->phase_manager_stack())
  { }

  ~G1ConcPhaseManager() {
    // Deactivate the manager if marking aborted, to avoid blocking on
    // phase exit when the phase has been requested.
    if (_cm->has_aborted()) {
      _manager.deactivate();
    }
  }

  void set_phase(int phase, bool force) {
    _manager.set_phase(phase, force);
  }
};

// Combine phase management and timing into one convenient utility.
class G1ConcPhase : public StackObj {
  G1ConcPhaseTimer _timer;
  G1ConcPhaseManager _manager;

public:
  G1ConcPhase(int phase, G1ConcurrentMarkThread* thread) :
    _timer(thread->cm(), lookup_concurrent_phase_title(phase)),
    _manager(phase, thread)
  { }
};

bool G1ConcurrentMarkThread::request_concurrent_phase(const char* phase_name) {
  int phase = lookup_concurrent_phase(phase_name);
  if (phase < 0) return false;

  while (!ConcurrentGCPhaseManager::wait_for_phase(phase,
                                                   phase_manager_stack())) {
    assert(phase != G1ConcurrentPhase::ANY, "Wait for ANY phase must succeed");
    if ((phase != G1ConcurrentPhase::IDLE) && !during_cycle()) {
      // If idle and the goal is !idle, start a collection.
      G1CollectedHeap::heap()->collect(GCCause::_wb_conc_mark);
    }
  }
  return true;
}

void G1ConcurrentMarkThread::run_service() {
  _vtime_start = os::elapsedVTime();

  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  G1Policy* policy = g1h->policy();

  G1ConcPhaseManager cpmanager(G1ConcurrentPhase::IDLE, this);

  while (!should_terminate()) {
    // wait until started is set.
    sleep_before_next_cycle();
    if (should_terminate()) {
      break;
    }

    cpmanager.set_phase(G1ConcurrentPhase::CONCURRENT_CYCLE, false /* force */);

    GCIdMark gc_id_mark;

    _cm->concurrent_cycle_start();

    GCTraceConcTime(Info, gc) tt("Concurrent Cycle");
    {
      ResourceMark rm;
      HandleMark   hm;
      double cycle_start = os::elapsedVTime();

      {
        G1ConcPhase p(G1ConcurrentPhase::CLEAR_CLAIMED_MARKS, this);
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
        G1ConcPhase p(G1ConcurrentPhase::SCAN_ROOT_REGIONS, this);
        _cm->scan_root_regions();
      }

      // It would be nice to use the G1ConcPhase class here but
      // the "end" logging is inside the loop and not at the end of
      // a scope. Also, the timer doesn't support nesting.
      // Mimicking the same log output instead.
      {
        G1ConcPhaseManager mark_manager(G1ConcurrentPhase::CONCURRENT_MARK, this);
        jlong mark_start = os::elapsed_counter();
        const char* cm_title = lookup_concurrent_phase_title(G1ConcurrentPhase::CONCURRENT_MARK);
        log_info(gc, marking)("%s (%.3fs)",
                              cm_title,
                              TimeHelper::counter_to_seconds(mark_start));
        for (uint iter = 1; !_cm->has_aborted(); ++iter) {
          // Concurrent marking.
          {
            G1ConcPhase p(G1ConcurrentPhase::MARK_FROM_ROOTS, this);
            _cm->mark_from_roots();
          }
          if (_cm->has_aborted()) {
            break;
          }

          if (G1UseReferencePrecleaning) {
            G1ConcPhase p(G1ConcurrentPhase::PRECLEAN, this);
            _cm->preclean();
          }

          // Provide a control point before remark.
          {
            G1ConcPhaseManager p(G1ConcurrentPhase::BEFORE_REMARK, this);
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
          log_info(gc, marking)("%s (%.3fs, %.3fs) %.3fms",
                                cm_title,
                                TimeHelper::counter_to_seconds(mark_start),
                                TimeHelper::counter_to_seconds(mark_end),
                                TimeHelper::counter_to_millis(mark_end - mark_start));
          mark_manager.set_phase(G1ConcurrentPhase::REMARK, false);
          CMRemark cl(_cm);
          VM_G1Concurrent op(&cl, "Pause Remark");
          VMThread::execute(&op);
          if (_cm->has_aborted()) {
            break;
          } else if (!_cm->restart_for_overflow()) {
            break;              // Exit loop if no restart requested.
          } else {
            // Loop to restart for overflow.
            mark_manager.set_phase(G1ConcurrentPhase::CONCURRENT_MARK, false);
            log_info(gc, marking)("%s Restart for Mark Stack Overflow (iteration #%u)",
                                  cm_title, iter);
          }
        }
      }

      if (!_cm->has_aborted()) {
        G1ConcPhase p(G1ConcurrentPhase::REBUILD_REMEMBERED_SETS, this);
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
        G1ConcPhase p(G1ConcurrentPhase::CLEANUP_FOR_NEXT_MARK, this);
        _cm->cleanup_for_next_mark();
      }
    }

    // Update the number of full collections that have been
    // completed. This will also notify the FullGCCount_lock in case a
    // Java thread is waiting for a full GC to happen (e.g., it
    // called System.gc() with +ExplicitGCInvokesConcurrent).
    {
      SuspendibleThreadSetJoiner sts_join;
      g1h->increment_old_marking_cycles_completed(true /* concurrent */);

      _cm->concurrent_cycle_end();
    }

    cpmanager.set_phase(G1ConcurrentPhase::IDLE, _cm->has_aborted() /* force */);
  }
  _cm->root_regions()->cancel_scan();
}

void G1ConcurrentMarkThread::stop_service() {
  MutexLockerEx ml(CGC_lock, Mutex::_no_safepoint_check_flag);
  CGC_lock->notify_all();
}


void G1ConcurrentMarkThread::sleep_before_next_cycle() {
  // We join here because we don't want to do the "shouldConcurrentMark()"
  // below while the world is otherwise stopped.
  assert(!in_progress(), "should have been cleared");

  MutexLockerEx x(CGC_lock, Mutex::_no_safepoint_check_flag);
  while (!started() && !should_terminate()) {
    CGC_lock->wait(Mutex::_no_safepoint_check_flag);
  }

  if (started()) {
    set_in_progress();
  }
}
