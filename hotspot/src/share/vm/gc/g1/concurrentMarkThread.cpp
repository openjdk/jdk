/*
 * Copyright (c) 2001, 2017, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoaderData.hpp"
#include "gc/g1/concurrentMarkThread.inline.hpp"
#include "gc/g1/g1Analytics.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1ConcurrentMark.inline.hpp"
#include "gc/g1/g1MMUTracker.hpp"
#include "gc/g1/g1Policy.hpp"
#include "gc/g1/suspendibleThreadSet.hpp"
#include "gc/g1/vm_operations_g1.hpp"
#include "gc/shared/concurrentGCPhaseManager.hpp"
#include "gc/shared/gcId.hpp"
#include "gc/shared/gcTrace.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/debug.hpp"

// ======= Concurrent Mark Thread ========

// Check order in EXPAND_CURRENT_PHASES
STATIC_ASSERT(ConcurrentGCPhaseManager::UNCONSTRAINED_PHASE <
              ConcurrentGCPhaseManager::IDLE_PHASE);

#define EXPAND_CONCURRENT_PHASES(expander)                              \
  expander(ANY, = ConcurrentGCPhaseManager::UNCONSTRAINED_PHASE, NULL)  \
  expander(IDLE, = ConcurrentGCPhaseManager::IDLE_PHASE, NULL)          \
  expander(CONCURRENT_CYCLE,, "Concurrent Cycle")                       \
  expander(CLEAR_CLAIMED_MARKS,, "Concurrent Clear Claimed Marks")      \
  expander(SCAN_ROOT_REGIONS,, "Concurrent Scan Root Regions")          \
  expander(CONCURRENT_MARK,, "Concurrent Mark")                         \
  expander(MARK_FROM_ROOTS,, "Concurrent Mark From Roots")              \
  expander(BEFORE_REMARK,, NULL)                                        \
  expander(REMARK,, NULL)                                               \
  expander(CREATE_LIVE_DATA,, "Concurrent Create Live Data")            \
  expander(COMPLETE_CLEANUP,, "Concurrent Complete Cleanup")            \
  expander(CLEANUP_FOR_NEXT_MARK,, "Concurrent Cleanup for Next Mark")  \
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

// The CM thread is created when the G1 garbage collector is used

ConcurrentMarkThread::ConcurrentMarkThread(G1ConcurrentMark* cm) :
  ConcurrentGCThread(),
  _cm(cm),
  _state(Idle),
  _phase_manager_stack(),
  _vtime_accum(0.0),
  _vtime_mark_accum(0.0) {

  set_name("G1 Main Marker");
  create_and_start();
}

class CMCheckpointRootsFinalClosure: public VoidClosure {

  G1ConcurrentMark* _cm;
public:

  CMCheckpointRootsFinalClosure(G1ConcurrentMark* cm) :
    _cm(cm) {}

  void do_void(){
    _cm->checkpointRootsFinal(false); // !clear_all_soft_refs
  }
};

class CMCleanUp: public VoidClosure {
  G1ConcurrentMark* _cm;
public:

  CMCleanUp(G1ConcurrentMark* cm) :
    _cm(cm) {}

  void do_void(){
    _cm->cleanup();
  }
};

// Marking pauses can be scheduled flexibly, so we might delay marking to meet MMU.
void ConcurrentMarkThread::delay_to_keep_mmu(G1Policy* g1_policy, bool remark) {
  const G1Analytics* analytics = g1_policy->analytics();
  if (g1_policy->adaptive_young_list_length()) {
    double now = os::elapsedTime();
    double prediction_ms = remark ? analytics->predict_remark_time_ms()
                                  : analytics->predict_cleanup_time_ms();
    G1MMUTracker *mmu_tracker = g1_policy->mmu_tracker();
    jlong sleep_time_ms = mmu_tracker->when_ms(now, prediction_ms);
    os::sleep(this, sleep_time_ms, false);
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
  G1ConcPhaseManager(int phase, ConcurrentMarkThread* thread) :
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
  G1ConcPhase(int phase, ConcurrentMarkThread* thread) :
    _timer(thread->cm(), lookup_concurrent_phase_title(phase)),
    _manager(phase, thread)
  { }
};

const char* const* ConcurrentMarkThread::concurrent_phases() const {
  return concurrent_phase_names;
}

bool ConcurrentMarkThread::request_concurrent_phase(const char* phase_name) {
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

void ConcurrentMarkThread::run_service() {
  _vtime_start = os::elapsedVTime();

  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  G1Policy* g1_policy = g1h->g1_policy();

  G1ConcPhaseManager cpmanager(G1ConcurrentPhase::IDLE, this);

  while (!should_terminate()) {
    // wait until started is set.
    sleepBeforeNextCycle();
    if (should_terminate()) {
      break;
    }

    cpmanager.set_phase(G1ConcurrentPhase::CONCURRENT_CYCLE, false /* force */);

    GCIdMark gc_id_mark;

    cm()->concurrent_cycle_start();

    assert(GCId::current() != GCId::undefined(), "GC id should have been set up by the initial mark GC.");

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
        const char* cm_title =
          lookup_concurrent_phase_title(G1ConcurrentPhase::CONCURRENT_MARK);
        log_info(gc, marking)("%s (%.3fs)",
                              cm_title,
                              TimeHelper::counter_to_seconds(mark_start));
        for (uint iter = 1; !cm()->has_aborted(); ++iter) {
          // Concurrent marking.
          {
            G1ConcPhase p(G1ConcurrentPhase::MARK_FROM_ROOTS, this);
            _cm->mark_from_roots();
          }
          if (cm()->has_aborted()) break;

          // Provide a control point after mark_from_roots.
          {
            G1ConcPhaseManager p(G1ConcurrentPhase::BEFORE_REMARK, this);
          }
          if (cm()->has_aborted()) break;

          // Delay remark pause for MMU.
          double mark_end_time = os::elapsedVTime();
          jlong mark_end = os::elapsed_counter();
          _vtime_mark_accum += (mark_end_time - cycle_start);
          delay_to_keep_mmu(g1_policy, true /* remark */);
          if (cm()->has_aborted()) break;

          // Pause Remark.
          log_info(gc, marking)("%s (%.3fs, %.3fs) %.3fms",
                                cm_title,
                                TimeHelper::counter_to_seconds(mark_start),
                                TimeHelper::counter_to_seconds(mark_end),
                                TimeHelper::counter_to_millis(mark_end - mark_start));
          mark_manager.set_phase(G1ConcurrentPhase::REMARK, false);
          CMCheckpointRootsFinalClosure final_cl(_cm);
          VM_CGC_Operation op(&final_cl, "Pause Remark");
          VMThread::execute(&op);
          if (cm()->has_aborted()) {
            break;
          } else if (!cm()->restart_for_overflow()) {
            break;              // Exit loop if no restart requested.
          } else {
            // Loop to restart for overflow.
            mark_manager.set_phase(G1ConcurrentPhase::CONCURRENT_MARK, false);
            log_info(gc, marking)("%s Restart for Mark Stack Overflow (iteration #%u)",
                                  cm_title, iter);
          }
        }
      }

      if (!cm()->has_aborted()) {
        G1ConcPhase p(G1ConcurrentPhase::CREATE_LIVE_DATA, this);
        cm()->create_live_data();
      }

      double end_time = os::elapsedVTime();
      // Update the total virtual time before doing this, since it will try
      // to measure it to get the vtime for this marking.  We purposely
      // neglect the presumably-short "completeCleanup" phase here.
      _vtime_accum = (end_time - _vtime_start);

      if (!cm()->has_aborted()) {
        delay_to_keep_mmu(g1_policy, false /* cleanup */);

        CMCleanUp cl_cl(_cm);
        VM_CGC_Operation op(&cl_cl, "Pause Cleanup");
        VMThread::execute(&op);
      } else {
        // We don't want to update the marking status if a GC pause
        // is already underway.
        SuspendibleThreadSetJoiner sts_join;
        g1h->collector_state()->set_mark_in_progress(false);
      }

      // Check if cleanup set the free_regions_coming flag. If it
      // hasn't, we can just skip the next step.
      if (g1h->free_regions_coming()) {
        // The following will finish freeing up any regions that we
        // found to be empty during cleanup. We'll do this part
        // without joining the suspendible set. If an evacuation pause
        // takes place, then we would carry on freeing regions in
        // case they are needed by the pause. If a Full GC takes
        // place, it would wait for us to process the regions
        // reclaimed by cleanup.

        // Now do the concurrent cleanup operation.
        G1ConcPhase p(G1ConcurrentPhase::COMPLETE_CLEANUP, this);
        _cm->complete_cleanup();

        // Notify anyone who's waiting that there are no more free
        // regions coming. We have to do this before we join the STS
        // (in fact, we should not attempt to join the STS in the
        // interval between finishing the cleanup pause and clearing
        // the free_regions_coming flag) otherwise we might deadlock:
        // a GC worker could be blocked waiting for the notification
        // whereas this thread will be blocked for the pause to finish
        // while it's trying to join the STS, which is conditional on
        // the GC workers finishing.
        g1h->reset_free_regions_coming();
      }
      guarantee(cm()->cleanup_list_is_empty(),
                "at this point there should be no regions on the cleanup list");

      // There is a tricky race before recording that the concurrent
      // cleanup has completed and a potential Full GC starting around
      // the same time. We want to make sure that the Full GC calls
      // abort() on concurrent mark after
      // record_concurrent_mark_cleanup_completed(), since abort() is
      // the method that will reset the concurrent mark state. If we
      // end up calling record_concurrent_mark_cleanup_completed()
      // after abort() then we might incorrectly undo some of the work
      // abort() did. Checking the has_aborted() flag after joining
      // the STS allows the correct ordering of the two methods. There
      // are two scenarios:
      //
      // a) If we reach here before the Full GC, the fact that we have
      // joined the STS means that the Full GC cannot start until we
      // leave the STS, so record_concurrent_mark_cleanup_completed()
      // will complete before abort() is called.
      //
      // b) If we reach here during the Full GC, we'll be held up from
      // joining the STS until the Full GC is done, which means that
      // abort() will have completed and has_aborted() will return
      // true to prevent us from calling
      // record_concurrent_mark_cleanup_completed() (and, in fact, it's
      // not needed any more as the concurrent mark state has been
      // already reset).
      {
        SuspendibleThreadSetJoiner sts_join;
        if (!cm()->has_aborted()) {
          g1_policy->record_concurrent_mark_cleanup_completed();
        } else {
          log_info(gc, marking)("Concurrent Mark Abort");
        }
      }

      // We now want to allow clearing of the marking bitmap to be
      // suspended by a collection pause.
      // We may have aborted just before the remark. Do not bother clearing the
      // bitmap then, as it has been done during mark abort.
      if (!cm()->has_aborted()) {
        G1ConcPhase p(G1ConcurrentPhase::CLEANUP_FOR_NEXT_MARK, this);
        _cm->cleanup_for_next_mark();
      } else {
        assert(!G1VerifyBitmaps || _cm->nextMarkBitmapIsClear(), "Next mark bitmap must be clear");
      }
    }

    // Update the number of full collections that have been
    // completed. This will also notify the FullGCCount_lock in case a
    // Java thread is waiting for a full GC to happen (e.g., it
    // called System.gc() with +ExplicitGCInvokesConcurrent).
    {
      SuspendibleThreadSetJoiner sts_join;
      g1h->increment_old_marking_cycles_completed(true /* concurrent */);

      cm()->concurrent_cycle_end();
    }

    cpmanager.set_phase(G1ConcurrentPhase::IDLE, cm()->has_aborted() /* force */);
  }
  _cm->root_regions()->cancel_scan();
}

void ConcurrentMarkThread::stop_service() {
  MutexLockerEx ml(CGC_lock, Mutex::_no_safepoint_check_flag);
  CGC_lock->notify_all();
}

void ConcurrentMarkThread::sleepBeforeNextCycle() {
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
