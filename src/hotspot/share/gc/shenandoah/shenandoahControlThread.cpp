/*
 * Copyright (c) 2013, 2021, Red Hat, Inc. All rights reserved.
 * Copyright (C) 2022 THL A29 Limited, a Tencent company. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahConcurrentGC.hpp"
#include "gc/shenandoah/shenandoahControlThread.hpp"
#include "gc/shenandoah/shenandoahDegeneratedGC.hpp"
#include "gc/shenandoah/shenandoahEvacTracker.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahFullGC.hpp"
#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/shenandoahGlobalGeneration.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahMark.inline.hpp"
#include "gc/shenandoah/shenandoahMonitoringSupport.hpp"
#include "gc/shenandoah/shenandoahOopClosures.inline.hpp"
#include "gc/shenandoah/shenandoahOldGC.hpp"
#include "gc/shenandoah/shenandoahRootProcessor.inline.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "gc/shenandoah/shenandoahVMOperations.hpp"
#include "gc/shenandoah/shenandoahWorkerPolicy.hpp"
#include "gc/shenandoah/heuristics/shenandoahHeuristics.hpp"
#include "gc/shenandoah/mode/shenandoahMode.hpp"
#include "logging/log.hpp"
#include "memory/iterator.hpp"
#include "memory/metaspaceUtils.hpp"
#include "memory/metaspaceStats.hpp"
#include "memory/universe.hpp"
#include "runtime/atomic.hpp"

ShenandoahControlThread::ShenandoahControlThread() :
  ConcurrentGCThread(),
  _alloc_failure_waiters_lock(Mutex::safepoint - 2, "ShenandoahAllocFailureGC_lock", true),
  _gc_waiters_lock(Mutex::safepoint - 2, "ShenandoahRequestedGC_lock", true),
  _control_lock(Mutex::nosafepoint - 2, "ShenandoahControlGC_lock", true),
  _regulator_lock(Mutex::nosafepoint - 2, "ShenandoahRegulatorGC_lock", true),
  _periodic_task(this),
  _requested_gc_cause(GCCause::_no_cause_specified),
  _requested_generation(select_global_generation()),
  _degen_point(ShenandoahGC::_degenerated_outside_cycle),
  _degen_generation(nullptr),
  _allocs_seen(0),
  _mode(none) {
  set_name("Shenandoah Control Thread");
  reset_gc_id();
  create_and_start();
  _periodic_task.enroll();
  if (ShenandoahPacing) {
    _periodic_pacer_notify_task.enroll();
  }
}

ShenandoahControlThread::~ShenandoahControlThread() {
  // This is here so that super is called.
}

void ShenandoahPeriodicTask::task() {
  _thread->handle_force_counters_update();
  _thread->handle_counters_update();
}

void ShenandoahPeriodicPacerNotify::task() {
  assert(ShenandoahPacing, "Should not be here otherwise");
  ShenandoahHeap::heap()->pacer()->notify_waiters();
}

void ShenandoahControlThread::run_service() {
  ShenandoahHeap* heap = ShenandoahHeap::heap();

  GCMode default_mode = concurrent_normal;
  ShenandoahGenerationType generation = select_global_generation();
  GCCause::Cause default_cause = GCCause::_shenandoah_concurrent_gc;

  double last_shrink_time = os::elapsedTime();
  uint age_period = 0;

  // Shrink period avoids constantly polling regions for shrinking.
  // Having a period 10x lower than the delay would mean we hit the
  // shrinking with lag of less than 1/10-th of true delay.
  // ShenandoahUncommitDelay is in msecs, but shrink_period is in seconds.
  double shrink_period = (double)ShenandoahUncommitDelay / 1000 / 10;

  ShenandoahCollectorPolicy* policy = heap->shenandoah_policy();

  // Heuristics are notified of allocation failures here and other outcomes
  // of the cycle. They're also used here to control whether the Nth consecutive
  // degenerated cycle should be 'promoted' to a full cycle. The decision to
  // trigger a cycle or not is evaluated on the regulator thread.
  ShenandoahHeuristics* global_heuristics = heap->global_generation()->heuristics();
  while (!in_graceful_shutdown() && !should_terminate()) {
    // Figure out if we have pending requests.
    bool alloc_failure_pending = _alloc_failure_gc.is_set();
    bool is_gc_requested = _gc_requested.is_set();
    GCCause::Cause requested_gc_cause = _requested_gc_cause;
    bool explicit_gc_requested = is_gc_requested && is_explicit_gc(requested_gc_cause);
    bool implicit_gc_requested = is_gc_requested && is_implicit_gc(requested_gc_cause);

    // This control loop iteration have seen this much allocations.
    size_t allocs_seen = Atomic::xchg(&_allocs_seen, (size_t)0, memory_order_relaxed);

    // Check if we have seen a new target for soft max heap size.
    bool soft_max_changed = check_soft_max_changed();

    // Choose which GC mode to run in. The block below should select a single mode.
    set_gc_mode(none);
    GCCause::Cause cause = GCCause::_last_gc_cause;
    ShenandoahGC::ShenandoahDegenPoint degen_point = ShenandoahGC::_degenerated_unset;

    if (alloc_failure_pending) {
      // Allocation failure takes precedence: we have to deal with it first thing
      log_info(gc)("Trigger: Handle Allocation Failure");

      cause = GCCause::_allocation_failure;

      // Consume the degen point, and seed it with default value
      degen_point = _degen_point;
      _degen_point = ShenandoahGC::_degenerated_outside_cycle;

      if (degen_point == ShenandoahGC::_degenerated_outside_cycle) {
        _degen_generation = heap->mode()->is_generational() ?
                heap->young_generation() : heap->global_generation();
      } else {
        assert(_degen_generation != nullptr, "Need to know which generation to resume");
      }

      ShenandoahHeuristics* heuristics = _degen_generation->heuristics();
      generation = _degen_generation->type();
      bool old_gen_evacuation_failed = heap->clear_old_evacuation_failure();

      // Do not bother with degenerated cycle if old generation evacuation failed
      if (ShenandoahDegeneratedGC && heuristics->should_degenerate_cycle() && !old_gen_evacuation_failed) {
        heuristics->record_allocation_failure_gc();
        policy->record_alloc_failure_to_degenerated(degen_point);
        set_gc_mode(stw_degenerated);
      } else {
        heuristics->record_allocation_failure_gc();
        policy->record_alloc_failure_to_full();
        generation = select_global_generation();
        set_gc_mode(stw_full);
      }
    } else if (explicit_gc_requested) {
      cause = requested_gc_cause;
      generation = select_global_generation();
      log_info(gc)("Trigger: Explicit GC request (%s)", GCCause::to_string(cause));

      global_heuristics->record_requested_gc();

      if (ExplicitGCInvokesConcurrent) {
        policy->record_explicit_to_concurrent();
        set_gc_mode(default_mode);
        // Unload and clean up everything
        heap->set_unload_classes(global_heuristics->can_unload_classes());
      } else {
        policy->record_explicit_to_full();
        set_gc_mode(stw_full);
      }
    } else if (implicit_gc_requested) {
      cause = requested_gc_cause;
      generation = select_global_generation();
      log_info(gc)("Trigger: Implicit GC request (%s)", GCCause::to_string(cause));

      global_heuristics->record_requested_gc();

      if (ShenandoahImplicitGCInvokesConcurrent) {
        policy->record_implicit_to_concurrent();
        set_gc_mode(default_mode);

        // Unload and clean up everything
        heap->set_unload_classes(global_heuristics->can_unload_classes());
      } else {
        policy->record_implicit_to_full();
        set_gc_mode(stw_full);
      }
    } else {
      // We should only be here if the regulator requested a cycle or if
      // there is an old generation mark in progress.
      if (_requested_gc_cause == GCCause::_shenandoah_concurrent_gc) {
        if (_requested_generation == OLD && heap->doing_mixed_evacuations()) {
          // If a request to start an old cycle arrived while an old cycle was running, but _before_
          // it chose any regions for evacuation we don't want to start a new old cycle. Rather, we want
          // the heuristic to run a young collection so that we can evacuate some old regions.
          assert(!heap->is_concurrent_old_mark_in_progress(), "Should not be running mixed collections and concurrent marking");
          generation = YOUNG;
        } else {
          generation = _requested_generation;
        }
        // preemption was requested or this is a regular cycle
        cause = GCCause::_shenandoah_concurrent_gc;
        set_gc_mode(default_mode);

        // Don't start a new old marking if there is one already in progress
        if (generation == OLD && heap->is_concurrent_old_mark_in_progress()) {
          set_gc_mode(servicing_old);
        }

        if (generation == select_global_generation()) {
          heap->set_unload_classes(global_heuristics->should_unload_classes());
        } else {
          heap->set_unload_classes(false);
        }

        // Don't want to spin in this loop and start a cycle every time, so
        // clear requested gc cause. This creates a race with callers of the
        // blocking 'request_gc' method, but there it loops and resets the
        // '_requested_gc_cause' until a full cycle is completed.
        _requested_gc_cause = GCCause::_no_gc;
      } else if (heap->is_concurrent_old_mark_in_progress() || heap->is_prepare_for_old_mark_in_progress()) {
        // Nobody asked us to do anything, but we have an old-generation mark or old-generation preparation for
        // mixed evacuation in progress, so resume working on that.
        log_info(gc)("Resume old GC: marking is%s in progress, preparing is%s in progress",
                     heap->is_concurrent_old_mark_in_progress() ? "" : " NOT",
                     heap->is_prepare_for_old_mark_in_progress() ? "" : " NOT");

        cause = GCCause::_shenandoah_concurrent_gc;
        generation = OLD;
        set_gc_mode(servicing_old);
      }
    }

    // Blow all soft references on this cycle, if handling allocation failure,
    // either implicit or explicit GC request, or we are requested to do so unconditionally.
    if (generation == select_global_generation() && (alloc_failure_pending || implicit_gc_requested || explicit_gc_requested || ShenandoahAlwaysClearSoftRefs)) {
      heap->soft_ref_policy()->set_should_clear_all_soft_refs(true);
    }

    bool gc_requested = (gc_mode() != none);
    assert (!gc_requested || cause != GCCause::_last_gc_cause, "GC cause should be set");

    if (gc_requested) {
      // GC is starting, bump the internal ID
      update_gc_id();

      heap->reset_bytes_allocated_since_gc_start();

      MetaspaceCombinedStats meta_sizes = MetaspaceUtils::get_combined_statistics();

      // If GC was requested, we are sampling the counters even without actual triggers
      // from allocation machinery. This captures GC phases more accurately.
      set_forced_counters_update(true);

      // If GC was requested, we better dump freeset data for performance debugging
      {
        ShenandoahHeapLocker locker(heap->lock());
        heap->free_set()->log_status();
      }
      // In case this is a degenerated cycle, remember whether original cycle was aging.
      bool was_aging_cycle = heap->is_aging_cycle();
      heap->set_aging_cycle(false);

      switch (gc_mode()) {
        case concurrent_normal: {
          // At this point:
          //  if (generation == YOUNG), this is a normal YOUNG cycle
          //  if (generation == OLD), this is a bootstrap OLD cycle
          //  if (generation == GLOBAL), this is a GLOBAL cycle triggered by System.gc()
          // In all three cases, we want to age old objects if this is an aging cycle
          if (age_period-- == 0) {
             heap->set_aging_cycle(true);
             age_period = ShenandoahAgingCyclePeriod - 1;
          }
          service_concurrent_normal_cycle(heap, generation, cause);
          break;
        }
        case stw_degenerated: {
          heap->set_aging_cycle(was_aging_cycle);
          if (!service_stw_degenerated_cycle(cause, degen_point)) {
            // The degenerated GC was upgraded to a Full GC
            generation = select_global_generation();
          }
          break;
        }
        case stw_full: {
          if (age_period-- == 0) {
            heap->set_aging_cycle(true);
            age_period = ShenandoahAgingCyclePeriod - 1;
          }
          service_stw_full_cycle(cause);
          break;
        }
        case servicing_old: {
          assert(generation == OLD, "Expected old generation here");
          GCIdMark gc_id_mark;
          service_concurrent_old_cycle(heap, cause);
          break;
        }
        default:
          ShouldNotReachHere();
      }

      // If this was the requested GC cycle, notify waiters about it
      if (explicit_gc_requested || implicit_gc_requested) {
        notify_gc_waiters();
      }

      // If this was the allocation failure GC cycle, notify waiters about it
      if (alloc_failure_pending) {
        notify_alloc_failure_waiters();
      }

      // Report current free set state at the end of cycle, whether
      // it is a normal completion, or the abort.
      {
        ShenandoahHeapLocker locker(heap->lock());
        heap->free_set()->log_status();

        // Notify Universe about new heap usage. This has implications for
        // global soft refs policy, and we better report it every time heap
        // usage goes down.
        Universe::heap()->update_capacity_and_used_at_gc();

        // Signal that we have completed a visit to all live objects.
        Universe::heap()->record_whole_heap_examined_timestamp();
      }

      // Disable forced counters update, and update counters one more time
      // to capture the state at the end of GC session.
      handle_force_counters_update();
      set_forced_counters_update(false);

      // Retract forceful part of soft refs policy
      heap->soft_ref_policy()->set_should_clear_all_soft_refs(false);

      // Clear metaspace oom flag, if current cycle unloaded classes
      if (heap->unload_classes()) {
        assert(generation == select_global_generation(), "Only unload classes during GLOBAL cycle");
        global_heuristics->clear_metaspace_oom();
      }

      process_phase_timings(heap);

      // Print Metaspace change following GC (if logging is enabled).
      MetaspaceUtils::print_metaspace_change(meta_sizes);

      // GC is over, we are at idle now
      if (ShenandoahPacing) {
        heap->pacer()->setup_for_idle();
      }
    } else {
      // Allow allocators to know we have seen this much regions
      if (ShenandoahPacing && (allocs_seen > 0)) {
        heap->pacer()->report_alloc(allocs_seen);
      }
    }

    double current = os::elapsedTime();

    if (ShenandoahUncommit && (explicit_gc_requested || soft_max_changed || (current - last_shrink_time > shrink_period))) {
      // Explicit GC tries to uncommit everything down to min capacity.
      // Soft max change tries to uncommit everything down to target capacity.
      // Periodic uncommit tries to uncommit suitable regions down to min capacity.

      double shrink_before = (explicit_gc_requested || soft_max_changed) ?
                             current :
                             current - (ShenandoahUncommitDelay / 1000.0);

      size_t shrink_until = soft_max_changed ?
                             heap->soft_max_capacity() :
                             heap->min_capacity();

      service_uncommit(shrink_before, shrink_until);
      heap->phase_timings()->flush_cycle_to_global();
      last_shrink_time = current;
    }

    // Don't wait around if there was an allocation failure - start the next cycle immediately.
    if (!is_alloc_failure_gc()) {
      // The timed wait is necessary because this thread has a responsibility to send
      // 'alloc_words' to the pacer when it does not perform a GC.
      MonitorLocker lock(&_control_lock, Mutex::_no_safepoint_check_flag);
      lock.wait(ShenandoahControlIntervalMax);
    }
  }

  // Wait for the actual stop(), can't leave run_service() earlier.
  while (!should_terminate()) {
    os::naked_short_sleep(ShenandoahControlIntervalMin);
  }
}

void ShenandoahControlThread::process_phase_timings(const ShenandoahHeap* heap) {
  // Commit worker statistics to cycle data
  heap->phase_timings()->flush_par_workers_to_cycle();
  if (ShenandoahPacing) {
    heap->pacer()->flush_stats_to_cycle();
  }

  ShenandoahEvacuationTracker* evac_tracker = heap->evac_tracker();
  ShenandoahCycleStats         evac_stats   = evac_tracker->flush_cycle_to_global();

  // Print GC stats for current cycle
  {
    LogTarget(Info, gc, stats) lt;
    if (lt.is_enabled()) {
      ResourceMark rm;
      LogStream ls(lt);
      heap->phase_timings()->print_cycle_on(&ls);
      evac_tracker->print_evacuations_on(&ls, &evac_stats.workers,
                                              &evac_stats.mutators);
      if (ShenandoahPacing) {
        heap->pacer()->print_cycle_on(&ls);
      }
    }
  }

  // Commit statistics to globals
  heap->phase_timings()->flush_cycle_to_global();
}

// Young and old concurrent cycles are initiated by the regulator. Implicit
// and explicit GC requests are handled by the controller thread and always
// run a global cycle (which is concurrent by default, but may be overridden
// by command line options). Old cycles always degenerate to a global cycle.
// Young cycles are degenerated to complete the young cycle.  Young
// and old degen may upgrade to Full GC.  Full GC may also be
// triggered directly by a System.gc() invocation.
//
//
//      +-----+ Idle +-----+-----------+---------------------+
//      |         +        |           |                     |
//      |         |        |           |                     |
//      |         |        v           |                     |
//      |         |  Bootstrap Old +-- | ------------+       |
//      |         |   +                |             |       |
//      |         |   |                |             |       |
//      |         v   v                v             v       |
//      |    Resume Old <----------+ Young +--> Young Degen  |
//      |     +  +   ^                            +  +       |
//      v     |  |   |                            |  |       |
//   Global <-+  |   +----------------------------+  |       |
//      +        |                                   |       |
//      |        v                                   v       |
//      +--->  Global Degen +--------------------> Full <----+
//
void ShenandoahControlThread::service_concurrent_normal_cycle(ShenandoahHeap* heap,
                                                              const ShenandoahGenerationType generation,
                                                              GCCause::Cause cause) {
  GCIdMark gc_id_mark;
  ShenandoahGeneration* the_generation = nullptr;
  switch (generation) {
    case YOUNG: {
      // Run a young cycle. This might or might not, have interrupted an ongoing
      // concurrent mark in the old generation. We need to think about promotions
      // in this case. Promoted objects should be above the TAMS in the old regions
      // they end up in, but we have to be sure we don't promote into any regions
      // that are in the cset.
      log_info(gc, ergo)("Start GC cycle (YOUNG)");
      the_generation = heap->young_generation();
      service_concurrent_cycle(the_generation, cause, false);
      break;
    }
    case OLD: {
      log_info(gc, ergo)("Start GC cycle (OLD)");
      the_generation = heap->old_generation();
      service_concurrent_old_cycle(heap, cause);
      break;
    }
    case GLOBAL_GEN: {
      log_info(gc, ergo)("Start GC cycle (GLOBAL)");
      the_generation = heap->global_generation();
      service_concurrent_cycle(the_generation, cause, false);
      break;
    }
    case GLOBAL_NON_GEN: {
      log_info(gc, ergo)("Start GC cycle");
      the_generation = heap->global_generation();
      service_concurrent_cycle(the_generation, cause, false);
      break;
    }
    default:
      ShouldNotReachHere();
  }
}

void ShenandoahControlThread::service_concurrent_old_cycle(ShenandoahHeap* heap, GCCause::Cause &cause) {
  ShenandoahOldGeneration* old_generation = heap->old_generation();
  ShenandoahYoungGeneration* young_generation = heap->young_generation();
  ShenandoahOldGeneration::State original_state = old_generation->state();

  TraceCollectorStats tcs(heap->monitoring_support()->concurrent_collection_counters());

  switch (original_state) {
    case ShenandoahOldGeneration::WAITING_FOR_FILL:
    case ShenandoahOldGeneration::IDLE: {
      assert(!heap->is_concurrent_old_mark_in_progress(), "Old already in progress");
      assert(old_generation->task_queues()->is_empty(), "Old mark queues should be empty");
    }
    case ShenandoahOldGeneration::FILLING: {
      _allow_old_preemption.set();
      ShenandoahGCSession session(cause, old_generation);
      old_generation->prepare_gc();
      _allow_old_preemption.unset();

      if (heap->is_prepare_for_old_mark_in_progress()) {
        // Coalescing threads detected the cancellation request and aborted. Stay
        // in this state so control thread may resume the coalescing work.
        assert(old_generation->state() == ShenandoahOldGeneration::FILLING, "Prepare for mark should be in progress");
        assert(heap->cancelled_gc(), "Preparation for GC is not complete, expected cancellation");
      }

      // Before bootstrapping begins, we must acknowledge any cancellation request.
      // If the gc has not been cancelled, this does nothing. If it has been cancelled,
      // this will clear the cancellation request and exit before starting the bootstrap
      // phase. This will allow the young GC cycle to proceed normally. If we do not
      // acknowledge the cancellation request, the subsequent young cycle will observe
      // the request and essentially cancel itself.
      if (check_cancellation_or_degen(ShenandoahGC::_degenerated_outside_cycle)) {
        log_info(gc)("Preparation for old generation cycle was cancelled");
        return;
      }

      // Coalescing threads completed and nothing was cancelled. it is safe to transition
      // to the bootstrapping state now.
      old_generation->transition_to(ShenandoahOldGeneration::BOOTSTRAPPING);
    }
    case ShenandoahOldGeneration::BOOTSTRAPPING: {
      // Configure the young generation's concurrent mark to put objects in
      // old regions into the concurrent mark queues associated with the old
      // generation. The young cycle will run as normal except that rather than
      // ignore old references it will mark and enqueue them in the old concurrent
      // task queues but it will not traverse them.
      set_gc_mode(bootstrapping_old);
      young_generation->set_old_gen_task_queues(old_generation->task_queues());
      ShenandoahGCSession session(cause, young_generation);
      service_concurrent_cycle(heap, young_generation, cause, true);
      process_phase_timings(heap);
      if (heap->cancelled_gc()) {
        // Young generation bootstrap cycle has failed. Concurrent mark for old generation
        // is going to resume after degenerated bootstrap cycle completes.
        log_info(gc)("Bootstrap cycle for old generation was cancelled");
        return;
      }

      // Reset the degenerated point. Normally this would happen at the top
      // of the control loop, but here we have just completed a young cycle
      // which has bootstrapped the old concurrent marking.
      _degen_point = ShenandoahGC::_degenerated_outside_cycle;

      // From here we will 'resume' the old concurrent mark. This will skip reset
      // and init mark for the concurrent mark. All of that work will have been
      // done by the bootstrapping young cycle.
      set_gc_mode(servicing_old);
      old_generation->transition_to(ShenandoahOldGeneration::MARKING);
    }
    case ShenandoahOldGeneration::MARKING: {
      ShenandoahGCSession session(cause, old_generation);
      bool marking_complete = resume_concurrent_old_cycle(old_generation, cause);
      if (marking_complete) {
        assert(old_generation->state() != ShenandoahOldGeneration::MARKING, "Should not still be marking");
        if (original_state == ShenandoahOldGeneration::MARKING) {
          heap->mmu_tracker()->record_old_marking_increment(old_generation, GCId::current(), true,
                                                            heap->collection_set()->has_old_regions());
          heap->log_heap_status("At end of Concurrent Old Marking finishing increment");
        }
      } else if (original_state == ShenandoahOldGeneration::MARKING) {
        heap->mmu_tracker()->record_old_marking_increment(old_generation, GCId::current(), false,
                                                          heap->collection_set()->has_old_regions());
        heap->log_heap_status("At end of Concurrent Old Marking increment");
      }
      break;
    }
    default:
      fatal("Unexpected state for old GC: %s", ShenandoahOldGeneration::state_name(old_generation->state()));
  }
}

bool ShenandoahControlThread::resume_concurrent_old_cycle(ShenandoahGeneration* generation, GCCause::Cause cause) {
  assert(ShenandoahHeap::heap()->is_concurrent_old_mark_in_progress(), "Old mark should be in progress");
  log_debug(gc)("Resuming old generation with " UINT32_FORMAT " marking tasks queued", generation->task_queues()->tasks());

  ShenandoahHeap* heap = ShenandoahHeap::heap();

  // We can only tolerate being cancelled during concurrent marking or during preparation for mixed
  // evacuation. This flag here (passed by reference) is used to control precisely where the regulator
  // is allowed to cancel a GC.
  ShenandoahOldGC gc(generation, _allow_old_preemption);
  if (gc.collect(cause)) {
    generation->record_success_concurrent(false);
  }

  if (heap->cancelled_gc()) {
    // It's possible the gc cycle was cancelled after the last time
    // the collection checked for cancellation. In which case, the
    // old gc cycle is still completed, and we have to deal with this
    // cancellation. We set the degeneration point to be outside
    // the cycle because if this is an allocation failure, that is
    // what must be done (there is no degenerated old cycle). If the
    // cancellation was due to a heuristic wanting to start a young
    // cycle, then we are not actually going to a degenerated cycle,
    // so the degenerated point doesn't matter here.
    check_cancellation_or_degen(ShenandoahGC::_degenerated_outside_cycle);
    if (_requested_gc_cause == GCCause::_shenandoah_concurrent_gc) {
      heap->shenandoah_policy()->record_interrupted_old();
    }
    return false;
  }
  return true;
}

bool ShenandoahControlThread::check_soft_max_changed() const {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  size_t new_soft_max = Atomic::load(&SoftMaxHeapSize);
  size_t old_soft_max = heap->soft_max_capacity();
  if (new_soft_max != old_soft_max) {
    new_soft_max = MAX2(heap->min_capacity(), new_soft_max);
    new_soft_max = MIN2(heap->max_capacity(), new_soft_max);
    if (new_soft_max != old_soft_max) {
      log_info(gc)("Soft Max Heap Size: " SIZE_FORMAT "%s -> " SIZE_FORMAT "%s",
                   byte_size_in_proper_unit(old_soft_max), proper_unit_for_byte_size(old_soft_max),
                   byte_size_in_proper_unit(new_soft_max), proper_unit_for_byte_size(new_soft_max)
      );
      heap->set_soft_max_capacity(new_soft_max);
      return true;
    }
  }
  return false;
}

void ShenandoahControlThread::service_concurrent_cycle(ShenandoahGeneration* generation, GCCause::Cause cause, bool do_old_gc_bootstrap) {
  // Normal cycle goes via all concurrent phases. If allocation failure (af) happens during
  // any of the concurrent phases, it first degrades to Degenerated GC and completes GC there.
  // If second allocation failure happens during Degenerated GC cycle (for example, when GC
  // tries to evac something and no memory is available), cycle degrades to Full GC.
  //
  // There are also a shortcut through the normal cycle: immediate garbage shortcut, when
  // heuristics says there are no regions to compact, and all the collection comes from immediately
  // reclaimable regions.
  //
  // ................................................................................................
  //
  //                                    (immediate garbage shortcut)                Concurrent GC
  //                             /-------------------------------------------\
  //                             |                                           |
  //                             |                                           |
  //                             |                                           |
  //                             |                                           v
  // [START] ----> Conc Mark ----o----> Conc Evac --o--> Conc Update-Refs ---o----> [END]
  //                   |                    |                 |              ^
  //                   | (af)               | (af)            | (af)         |
  // ..................|....................|.................|..............|.......................
  //                   |                    |                 |              |
  //                   |                    |                 |              |      Degenerated GC
  //                   v                    v                 v              |
  //               STW Mark ----------> STW Evac ----> STW Update-Refs ----->o
  //                   |                    |                 |              ^
  //                   | (af)               | (af)            | (af)         |
  // ..................|....................|.................|..............|.......................
  //                   |                    |                 |              |
  //                   |                    v                 |              |      Full GC
  //                   \------------------->o<----------------/              |
  //                                        |                                |
  //                                        v                                |
  //                                      Full GC  --------------------------/
  //
  if (check_cancellation_or_degen(ShenandoahGC::_degenerated_outside_cycle)) return;

  ShenandoahHeap* heap = ShenandoahHeap::heap();
  ShenandoahGCSession session(cause, generation);
  TraceCollectorStats tcs(heap->monitoring_support()->concurrent_collection_counters());

  service_concurrent_cycle(heap, generation, cause, do_old_gc_bootstrap);
}

void ShenandoahControlThread::service_concurrent_cycle(ShenandoahHeap* heap,
                                                       ShenandoahGeneration* generation,
                                                       GCCause::Cause& cause,
                                                       bool do_old_gc_bootstrap) {
  ShenandoahConcurrentGC gc(generation, do_old_gc_bootstrap);
  if (gc.collect(cause)) {
    // Cycle is complete
    generation->record_success_concurrent(gc.abbreviated());
  } else {
    assert(heap->cancelled_gc(), "Must have been cancelled");
    check_cancellation_or_degen(gc.degen_point());
    assert(!generation->is_old(), "Old GC takes a different control path");
    // Concurrent young-gen collection degenerates to young
    // collection.  Same for global collections.
    _degen_generation = generation;
  }
  const char* msg;
  if (heap->mode()->is_generational()) {
    ShenandoahMmuTracker* mmu_tracker = heap->mmu_tracker();
    if (generation->is_young()) {
      if (heap->cancelled_gc()) {
        msg = (do_old_gc_bootstrap) ? "At end of Interrupted Concurrent Bootstrap GC":
                                      "At end of Interrupted Concurrent Young GC";
      } else {
        // We only record GC results if GC was successful
        msg = (do_old_gc_bootstrap) ? "At end of Concurrent Bootstrap GC":
                                      "At end of Concurrent Young GC";
        if (heap->collection_set()->has_old_regions()) {
          bool mixed_is_done = (heap->old_heuristics()->unprocessed_old_collection_candidates() == 0);
          mmu_tracker->record_mixed(generation, get_gc_id(), mixed_is_done);
        } else if (do_old_gc_bootstrap) {
          mmu_tracker->record_bootstrap(generation, get_gc_id(), heap->collection_set()->has_old_regions());
        } else {
          mmu_tracker->record_young(generation, get_gc_id());
        }
      }
    } else {
      assert(generation->is_global(), "If not young, must be GLOBAL");
      assert(!do_old_gc_bootstrap, "Do not bootstrap with GLOBAL GC");
      if (heap->cancelled_gc()) {
        msg = "At end of Interrupted Concurrent GLOBAL GC";
      } else {
        // We only record GC results if GC was successful
        msg = "At end of Concurrent Global GC";
        mmu_tracker->record_global(generation, get_gc_id());
      }
    }
  } else {
    msg = heap->cancelled_gc() ? "At end of cancelled GC" :
                                 "At end of GC";
  }
  heap->log_heap_status(msg);
}

bool ShenandoahControlThread::check_cancellation_or_degen(ShenandoahGC::ShenandoahDegenPoint point) {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  if (!heap->cancelled_gc()) {
    return false;
  }

  if (in_graceful_shutdown()) {
    return true;
  }

  assert(_degen_point == ShenandoahGC::_degenerated_outside_cycle,
         "Should not be set yet: %s", ShenandoahGC::degen_point_to_string(_degen_point));

  if (is_alloc_failure_gc()) {
    _degen_point = point;
    _preemption_requested.unset();
    return true;
  }

  if (_preemption_requested.is_set()) {
    assert(_requested_generation == YOUNG, "Only young GCs may preempt old.");
    _preemption_requested.unset();

    // Old generation marking is only cancellable during concurrent marking.
    // Once final mark is complete, the code does not check again for cancellation.
    // If old generation was cancelled for an allocation failure, we wouldn't
    // make it to this case. The calling code is responsible for forcing a
    // cancellation due to allocation failure into a degenerated cycle.
    _degen_point = point;
    heap->clear_cancelled_gc(false /* clear oom handler */);
    return true;
  }

  fatal("Cancel GC either for alloc failure GC, or gracefully exiting, or to pause old generation marking");
  return false;
}

void ShenandoahControlThread::stop_service() {
  // Nothing to do here.
}

void ShenandoahControlThread::service_stw_full_cycle(GCCause::Cause cause) {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();

  GCIdMark gc_id_mark;
  ShenandoahGCSession session(cause, heap->global_generation());

  ShenandoahFullGC gc;
  gc.collect(cause);

  heap->global_generation()->heuristics()->record_success_full();
  heap->shenandoah_policy()->record_success_full();
}

bool ShenandoahControlThread::service_stw_degenerated_cycle(GCCause::Cause cause,
                                                            ShenandoahGC::ShenandoahDegenPoint point) {
  assert(point != ShenandoahGC::_degenerated_unset, "Degenerated point should be set");
  ShenandoahHeap* const heap = ShenandoahHeap::heap();

  GCIdMark gc_id_mark;
  ShenandoahGCSession session(cause, _degen_generation);

  ShenandoahDegenGC gc(point, _degen_generation);
  gc.collect(cause);

  assert(heap->young_generation()->task_queues()->is_empty(), "Unexpected young generation marking tasks");
  if (_degen_generation->is_global()) {
    assert(heap->old_generation()->task_queues()->is_empty(), "Unexpected old generation marking tasks");
    assert(heap->global_generation()->task_queues()->is_empty(), "Unexpected global generation marking tasks");
  } else {
    assert(_degen_generation->is_young(), "Expected degenerated young cycle, if not global.");
    ShenandoahOldGeneration* old = heap->old_generation();
    if (old->state() == ShenandoahOldGeneration::BOOTSTRAPPING && !gc.upgraded_to_full()) {
      old->transition_to(ShenandoahOldGeneration::MARKING);
    }
  }

  _degen_generation->heuristics()->record_success_degenerated();
  heap->shenandoah_policy()->record_success_degenerated(_degen_generation->is_young());
  return !gc.upgraded_to_full();
}

void ShenandoahControlThread::service_uncommit(double shrink_before, size_t shrink_until) {
  ShenandoahHeap* heap = ShenandoahHeap::heap();

  // Determine if there is work to do. This avoids taking heap lock if there is
  // no work available, avoids spamming logs with superfluous logging messages,
  // and minimises the amount of work while locks are taken.

  if (heap->committed() <= shrink_until) return;

  bool has_work = false;
  for (size_t i = 0; i < heap->num_regions(); i++) {
    ShenandoahHeapRegion *r = heap->get_region(i);
    if (r->is_empty_committed() && (r->empty_time() < shrink_before)) {
      has_work = true;
      break;
    }
  }

  if (has_work) {
    heap->entry_uncommit(shrink_before, shrink_until);
  }
}

bool ShenandoahControlThread::is_explicit_gc(GCCause::Cause cause) const {
  return GCCause::is_user_requested_gc(cause) ||
         GCCause::is_serviceability_requested_gc(cause);
}

bool ShenandoahControlThread::is_implicit_gc(GCCause::Cause cause) const {
  return !is_explicit_gc(cause)
      && cause != GCCause::_shenandoah_concurrent_gc
      && cause != GCCause::_no_gc;
}

void ShenandoahControlThread::request_gc(GCCause::Cause cause) {
  assert(GCCause::is_user_requested_gc(cause) ||
         GCCause::is_serviceability_requested_gc(cause) ||
         cause == GCCause::_metadata_GC_clear_soft_refs ||
         cause == GCCause::_codecache_GC_aggressive ||
         cause == GCCause::_codecache_GC_threshold ||
         cause == GCCause::_full_gc_alot ||
         cause == GCCause::_wb_young_gc ||
         cause == GCCause::_wb_full_gc ||
         cause == GCCause::_wb_breakpoint ||
         cause == GCCause::_scavenge_alot,
         "only requested GCs here: %s", GCCause::to_string(cause));

  if (is_explicit_gc(cause)) {
    if (!DisableExplicitGC) {
      handle_requested_gc(cause);
    }
  } else {
    handle_requested_gc(cause);
  }
}

bool ShenandoahControlThread::request_concurrent_gc(ShenandoahGenerationType generation) {
  if (_preemption_requested.is_set() || _gc_requested.is_set() || ShenandoahHeap::heap()->cancelled_gc()) {
    // Ignore subsequent requests from the heuristics
    log_debug(gc, thread)("Reject request for concurrent gc: preemption_requested: %s, gc_requested: %s, gc_cancelled: %s",
                          BOOL_TO_STR(_preemption_requested.is_set()),
                          BOOL_TO_STR(_gc_requested.is_set()),
                          BOOL_TO_STR(ShenandoahHeap::heap()->cancelled_gc()));
    return false;
  }

  if (gc_mode() == none) {
    _requested_gc_cause = GCCause::_shenandoah_concurrent_gc;
    _requested_generation = generation;
    notify_control_thread();

    MonitorLocker ml(&_regulator_lock, Mutex::_no_safepoint_check_flag);
    while (gc_mode() == none) {
      ml.wait();
    }
    return true;
  }

  if (preempt_old_marking(generation)) {
    log_info(gc)("Preempting old generation mark to allow %s GC", shenandoah_generation_name(generation));
    assert(gc_mode() == servicing_old, "Expected to be servicing old, but was: %s.", gc_mode_name(gc_mode()));
    _requested_gc_cause = GCCause::_shenandoah_concurrent_gc;
    _requested_generation = generation;
    _preemption_requested.set();
    ShenandoahHeap::heap()->cancel_gc(GCCause::_shenandoah_concurrent_gc);
    notify_control_thread();

    MonitorLocker ml(&_regulator_lock, Mutex::_no_safepoint_check_flag);
    while (gc_mode() == servicing_old) {
      ml.wait();
    }
    return true;
  }

  log_debug(gc, thread)("Reject request for concurrent gc: mode: %s, allow_old_preemption: %s",
                        gc_mode_name(gc_mode()),
                        BOOL_TO_STR(_allow_old_preemption.is_set()));
  return false;
}

void ShenandoahControlThread::notify_control_thread() {
  MonitorLocker locker(&_control_lock, Mutex::_no_safepoint_check_flag);
  _control_lock.notify();
}

bool ShenandoahControlThread::preempt_old_marking(ShenandoahGenerationType generation) {
  return (generation == YOUNG) && _allow_old_preemption.try_unset();
}

void ShenandoahControlThread::handle_requested_gc(GCCause::Cause cause) {
  // Make sure we have at least one complete GC cycle before unblocking
  // from the explicit GC request.
  //
  // This is especially important for weak references cleanup and/or native
  // resources (e.g. DirectByteBuffers) machinery: when explicit GC request
  // comes very late in the already running cycle, it would miss lots of new
  // opportunities for cleanup that were made available before the caller
  // requested the GC.

  MonitorLocker ml(&_gc_waiters_lock);
  size_t current_gc_id = get_gc_id();
  size_t required_gc_id = current_gc_id + 1;
  while (current_gc_id < required_gc_id) {
    // Although setting gc request is under _gc_waiters_lock, but read side (run_service())
    // does not take the lock. We need to enforce following order, so that read side sees
    // latest requested gc cause when the flag is set.
    _requested_gc_cause = cause;
    _gc_requested.set();
    notify_control_thread();
    if (cause != GCCause::_wb_breakpoint) {
      ml.wait();
    }
    current_gc_id = get_gc_id();
  }
}

void ShenandoahControlThread::handle_alloc_failure(ShenandoahAllocRequest& req) {
  ShenandoahHeap* heap = ShenandoahHeap::heap();

  assert(current()->is_Java_thread(), "expect Java thread here");

  if (try_set_alloc_failure_gc()) {
    // Only report the first allocation failure
    log_info(gc)("Failed to allocate %s, " SIZE_FORMAT "%s",
                 req.type_string(),
                 byte_size_in_proper_unit(req.size() * HeapWordSize), proper_unit_for_byte_size(req.size() * HeapWordSize));
    // Now that alloc failure GC is scheduled, we can abort everything else
    heap->cancel_gc(GCCause::_allocation_failure);
  }

  MonitorLocker ml(&_alloc_failure_waiters_lock);
  while (is_alloc_failure_gc()) {
    ml.wait();
  }
}

void ShenandoahControlThread::handle_alloc_failure_evac(size_t words) {
  ShenandoahHeap* heap = ShenandoahHeap::heap();

  if (try_set_alloc_failure_gc()) {
    // Only report the first allocation failure
    log_info(gc)("Failed to allocate " SIZE_FORMAT "%s for evacuation",
                 byte_size_in_proper_unit(words * HeapWordSize), proper_unit_for_byte_size(words * HeapWordSize));
  }

  // Forcefully report allocation failure
  heap->cancel_gc(GCCause::_shenandoah_allocation_failure_evac);
}

void ShenandoahControlThread::notify_alloc_failure_waiters() {
  _alloc_failure_gc.unset();
  MonitorLocker ml(&_alloc_failure_waiters_lock);
  ml.notify_all();
}

bool ShenandoahControlThread::try_set_alloc_failure_gc() {
  return _alloc_failure_gc.try_set();
}

bool ShenandoahControlThread::is_alloc_failure_gc() {
  return _alloc_failure_gc.is_set();
}

void ShenandoahControlThread::notify_gc_waiters() {
  _gc_requested.unset();
  MonitorLocker ml(&_gc_waiters_lock);
  ml.notify_all();
}

void ShenandoahControlThread::handle_counters_update() {
  if (_do_counters_update.is_set()) {
    _do_counters_update.unset();
    ShenandoahHeap::heap()->monitoring_support()->update_counters();
  }
}

void ShenandoahControlThread::handle_force_counters_update() {
  if (_force_counters_update.is_set()) {
    _do_counters_update.unset(); // reset these too, we do update now!
    ShenandoahHeap::heap()->monitoring_support()->update_counters();
  }
}

void ShenandoahControlThread::notify_heap_changed() {
  // This is called from allocation path, and thus should be fast.

  // Update monitoring counters when we took a new region. This amortizes the
  // update costs on slow path.
  if (_do_counters_update.is_unset()) {
    _do_counters_update.set();
  }
}

void ShenandoahControlThread::pacing_notify_alloc(size_t words) {
  assert(ShenandoahPacing, "should only call when pacing is enabled");
  Atomic::add(&_allocs_seen, words, memory_order_relaxed);
}

void ShenandoahControlThread::set_forced_counters_update(bool value) {
  _force_counters_update.set_cond(value);
}

void ShenandoahControlThread::reset_gc_id() {
  Atomic::store(&_gc_id, (size_t)0);
}

void ShenandoahControlThread::update_gc_id() {
  Atomic::inc(&_gc_id);
}

size_t ShenandoahControlThread::get_gc_id() {
  return Atomic::load(&_gc_id);
}

void ShenandoahControlThread::start() {
  create_and_start();
}

void ShenandoahControlThread::prepare_for_graceful_shutdown() {
  _graceful_shutdown.set();
}

bool ShenandoahControlThread::in_graceful_shutdown() {
  return _graceful_shutdown.is_set();
}

const char* ShenandoahControlThread::gc_mode_name(ShenandoahControlThread::GCMode mode) {
  switch (mode) {
    case none:              return "idle";
    case concurrent_normal: return "normal";
    case stw_degenerated:   return "degenerated";
    case stw_full:          return "full";
    case servicing_old:     return "old";
    case bootstrapping_old: return "bootstrap";
    default:                return "unknown";
  }
}

void ShenandoahControlThread::set_gc_mode(ShenandoahControlThread::GCMode new_mode) {
  if (_mode != new_mode) {
    log_info(gc)("Transition from: %s to: %s", gc_mode_name(_mode), gc_mode_name(new_mode));
    MonitorLocker ml(&_regulator_lock, Mutex::_no_safepoint_check_flag);
    _mode = new_mode;
    ml.notify_all();
  }
}

ShenandoahGenerationType ShenandoahControlThread::select_global_generation() {
  if (ShenandoahHeap::heap()->mode()->is_generational()) {
    return GLOBAL_GEN;
  } else {
    return GLOBAL_NON_GEN;
  }
}
