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

#include "gc/shenandoah/mode/shenandoahMode.hpp"
#include "gc/shenandoah/shenandoahAsserts.hpp"
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahConcurrentGC.hpp"
#include "gc/shenandoah/shenandoahGenerationalControlThread.hpp"
#include "gc/shenandoah/shenandoahDegeneratedGC.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahFullGC.hpp"
#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/shenandoahGenerationalHeap.hpp"
#include "gc/shenandoah/shenandoahOldGC.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahMonitoringSupport.hpp"
#include "gc/shenandoah/shenandoahPacer.inline.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"
#include "logging/log.hpp"
#include "memory/metaspaceUtils.hpp"
#include "memory/metaspaceStats.hpp"
#include "runtime/atomic.hpp"

ShenandoahGenerationalControlThread::ShenandoahGenerationalControlThread() :
  ShenandoahController(),
  _control_lock(Mutex::nosafepoint - 2, "ShenandoahControlGC_lock", true),
  _regulator_lock(Mutex::nosafepoint - 2, "ShenandoahRegulatorGC_lock", true),
  _requested_gc_cause(GCCause::_no_gc),
  _requested_generation(GLOBAL),
  _degen_point(ShenandoahGC::_degenerated_outside_cycle),
  _degen_generation(nullptr),
  _mode(none) {
  shenandoah_assert_generational();
  set_name("Shenandoah Control Thread");
  create_and_start();
}

void ShenandoahGenerationalControlThread::run_service() {
  ShenandoahGenerationalHeap* const heap = ShenandoahGenerationalHeap::heap();

  const GCMode default_mode = concurrent_normal;
  ShenandoahGenerationType generation = GLOBAL;

  uint age_period = 0;

  ShenandoahCollectorPolicy* const policy = heap->shenandoah_policy();

  // Heuristics are notified of allocation failures here and other outcomes
  // of the cycle. They're also used here to control whether the Nth consecutive
  // degenerated cycle should be 'promoted' to a full cycle. The decision to
  // trigger a cycle or not is evaluated on the regulator thread.
  ShenandoahHeuristics* global_heuristics = heap->global_generation()->heuristics();
  while (!in_graceful_shutdown() && !should_terminate()) {
    // Figure out if we have pending requests.
    const bool alloc_failure_pending = _alloc_failure_gc.is_set();
    const bool humongous_alloc_failure_pending = _humongous_alloc_failure_gc.is_set();

    GCCause::Cause cause = Atomic::xchg(&_requested_gc_cause, GCCause::_no_gc);

    const bool is_gc_requested = ShenandoahCollectorPolicy::is_requested_gc(cause);

    // This control loop iteration has seen this much allocation.
    const size_t allocs_seen = reset_allocs_seen();

    // Check if we have seen a new target for soft max heap size.
    const bool soft_max_changed = heap->check_soft_max_changed();

    // Choose which GC mode to run in. The block below should select a single mode.
    set_gc_mode(none);
    ShenandoahGC::ShenandoahDegenPoint degen_point = ShenandoahGC::_degenerated_unset;

    if (alloc_failure_pending) {
      // Allocation failure takes precedence: we have to deal with it first thing
      cause = GCCause::_allocation_failure;

      // Consume the degen point, and seed it with default value
      degen_point = _degen_point;
      _degen_point = ShenandoahGC::_degenerated_outside_cycle;

      if (degen_point == ShenandoahGC::_degenerated_outside_cycle) {
        _degen_generation = heap->young_generation();
      } else {
        assert(_degen_generation != nullptr, "Need to know which generation to resume");
      }

      ShenandoahHeuristics* heuristics = _degen_generation->heuristics();
      generation = _degen_generation->type();
      bool old_gen_evacuation_failed = heap->old_generation()->clear_failed_evacuation();

      heuristics->log_trigger("Handle Allocation Failure");

      // Do not bother with degenerated cycle if old generation evacuation failed or if humongous allocation failed
      if (ShenandoahDegeneratedGC && heuristics->should_degenerate_cycle() &&
          !old_gen_evacuation_failed && !humongous_alloc_failure_pending) {
        heuristics->record_allocation_failure_gc();
        policy->record_alloc_failure_to_degenerated(degen_point);
        set_gc_mode(stw_degenerated);
      } else {
        heuristics->record_allocation_failure_gc();
        policy->record_alloc_failure_to_full();
        generation = GLOBAL;
        set_gc_mode(stw_full);
      }
    } else if (is_gc_requested) {
      generation = GLOBAL;
      global_heuristics->log_trigger("GC request (%s)", GCCause::to_string(cause));
      global_heuristics->record_requested_gc();

      if (ShenandoahCollectorPolicy::should_run_full_gc(cause)) {
        set_gc_mode(stw_full);
      } else {
        set_gc_mode(default_mode);
        // Unload and clean up everything
        heap->set_unload_classes(global_heuristics->can_unload_classes());
      }
    } else {
      // We should only be here if the regulator requested a cycle or if
      // there is an old generation mark in progress.
      if (cause == GCCause::_shenandoah_concurrent_gc) {
        if (_requested_generation == OLD && heap->old_generation()->is_doing_mixed_evacuations()) {
          // If a request to start an old cycle arrived while an old cycle was running, but _before_
          // it chose any regions for evacuation we don't want to start a new old cycle. Rather, we want
          // the heuristic to run a young collection so that we can evacuate some old regions.
          assert(!heap->is_concurrent_old_mark_in_progress(), "Should not be running mixed collections and concurrent marking");
          generation = YOUNG;
        } else {
          generation = _requested_generation;
        }

        // preemption was requested or this is a regular cycle
        set_gc_mode(default_mode);

        // Don't start a new old marking if there is one already in progress
        if (generation == OLD && heap->is_concurrent_old_mark_in_progress()) {
          set_gc_mode(servicing_old);
        }

        if (generation == GLOBAL) {
          heap->set_unload_classes(global_heuristics->should_unload_classes());
        } else {
          heap->set_unload_classes(false);
        }
      } else if (heap->is_concurrent_old_mark_in_progress() || heap->is_prepare_for_old_mark_in_progress()) {
        // Nobody asked us to do anything, but we have an old-generation mark or old-generation preparation for
        // mixed evacuation in progress, so resume working on that.
        log_info(gc)("Resume old GC: marking is%s in progress, preparing is%s in progress",
                     heap->is_concurrent_old_mark_in_progress() ? "" : " NOT",
                     heap->is_prepare_for_old_mark_in_progress() ? "" : " NOT");

        cause = GCCause::_shenandoah_concurrent_gc;
        generation = OLD;
        set_gc_mode(servicing_old);
        heap->set_unload_classes(false);
      }
    }

    const bool gc_requested = (gc_mode() != none);
    assert (!gc_requested || cause != GCCause::_no_gc, "GC cause should be set");

    if (gc_requested) {
      // Cannot uncommit bitmap slices during concurrent reset
      ShenandoahNoUncommitMark forbid_region_uncommit(heap);

      // Blow away all soft references on this cycle, if handling allocation failure,
      // either implicit or explicit GC request, or we are requested to do so unconditionally.
      if (generation == GLOBAL && (alloc_failure_pending || is_gc_requested || ShenandoahAlwaysClearSoftRefs)) {
        heap->soft_ref_policy()->set_should_clear_all_soft_refs(true);
      }

      // GC is starting, bump the internal ID
      update_gc_id();

      heap->reset_bytes_allocated_since_gc_start();

      MetaspaceCombinedStats meta_sizes = MetaspaceUtils::get_combined_statistics();

      // If GC was requested, we are sampling the counters even without actual triggers
      // from allocation machinery. This captures GC phases more accurately.
      heap->set_forced_counters_update(true);

      // If GC was requested, we better dump freeset data for performance debugging
      heap->free_set()->log_status_under_lock();

      // In case this is a degenerated cycle, remember whether original cycle was aging.
      const bool was_aging_cycle = heap->is_aging_cycle();
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
          service_stw_degenerated_cycle(cause, degen_point);
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
      if (is_gc_requested) {
        notify_gc_waiters();
      }

      // If this was the allocation failure GC cycle, notify waiters about it
      if (alloc_failure_pending) {
        notify_alloc_failure_waiters();
      }

      // Report current free set state at the end of cycle, whether
      // it is a normal completion, or the abort.
      heap->free_set()->log_status_under_lock();

      // Notify Universe about new heap usage. This has implications for
      // global soft refs policy, and we better report it every time heap
      // usage goes down.
      heap->update_capacity_and_used_at_gc();

      // Signal that we have completed a visit to all live objects.
      heap->record_whole_heap_examined_timestamp();

      // Disable forced counters update, and update counters one more time
      // to capture the state at the end of GC session.
      heap->handle_force_counters_update();
      heap->set_forced_counters_update(false);

      // Retract forceful part of soft refs policy
      heap->soft_ref_policy()->set_should_clear_all_soft_refs(false);

      // Clear metaspace oom flag, if current cycle unloaded classes
      if (heap->unload_classes()) {
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
      // Report to pacer that we have seen this many words allocated
      if (ShenandoahPacing && (allocs_seen > 0)) {
        heap->pacer()->report_alloc(allocs_seen);
      }
    }

    // Check if we have seen a new target for soft max heap size or if a gc was requested.
    // Either of these conditions will attempt to uncommit regions.
    if (ShenandoahUncommit) {
      if (heap->check_soft_max_changed()) {
        heap->notify_soft_max_changed();
      } else if (is_gc_requested) {
        heap->notify_explicit_gc_requested();
      }
    }

    // Wait for ShenandoahControlIntervalMax unless there was an allocation failure or another request was made mid-cycle.
    if (!is_alloc_failure_gc() && _requested_gc_cause == GCCause::_no_gc) {
      // The timed wait is necessary because this thread has a responsibility to send
      // 'alloc_words' to the pacer when it does not perform a GC.
      MonitorLocker lock(&_control_lock, Mutex::_no_safepoint_check_flag);
      lock.wait(ShenandoahControlIntervalMax);
    }
  }

  set_gc_mode(stopped);

  // Wait for the actual stop(), can't leave run_service() earlier.
  while (!should_terminate()) {
    os::naked_short_sleep(ShenandoahControlIntervalMin);
  }
}

void ShenandoahGenerationalControlThread::process_phase_timings(const ShenandoahGenerationalHeap* heap) {
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
void ShenandoahGenerationalControlThread::service_concurrent_normal_cycle(ShenandoahGenerationalHeap* heap,
                                                                          const ShenandoahGenerationType generation,
                                                                          GCCause::Cause cause) {
  GCIdMark gc_id_mark;
  switch (generation) {
    case YOUNG: {
      // Run a young cycle. This might or might not, have interrupted an ongoing
      // concurrent mark in the old generation. We need to think about promotions
      // in this case. Promoted objects should be above the TAMS in the old regions
      // they end up in, but we have to be sure we don't promote into any regions
      // that are in the cset.
      log_info(gc, ergo)("Start GC cycle (Young)");
      service_concurrent_cycle(heap->young_generation(), cause, false);
      break;
    }
    case OLD: {
      log_info(gc, ergo)("Start GC cycle (Old)");
      service_concurrent_old_cycle(heap, cause);
      break;
    }
    case GLOBAL: {
      log_info(gc, ergo)("Start GC cycle (Global)");
      service_concurrent_cycle(heap->global_generation(), cause, false);
      break;
    }
    default:
      ShouldNotReachHere();
  }
}

void ShenandoahGenerationalControlThread::service_concurrent_old_cycle(ShenandoahGenerationalHeap* heap, GCCause::Cause &cause) {
  ShenandoahOldGeneration* old_generation = heap->old_generation();
  ShenandoahYoungGeneration* young_generation = heap->young_generation();
  ShenandoahOldGeneration::State original_state = old_generation->state();

  TraceCollectorStats tcs(heap->monitoring_support()->concurrent_collection_counters());

  switch (original_state) {
    case ShenandoahOldGeneration::FILLING: {
      ShenandoahGCSession session(cause, old_generation);
      _allow_old_preemption.set();
      old_generation->entry_coalesce_and_fill();
      _allow_old_preemption.unset();

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

      // Coalescing threads completed and nothing was cancelled. it is safe to transition from this state.
      old_generation->transition_to(ShenandoahOldGeneration::WAITING_FOR_BOOTSTRAP);
      return;
    }
    case ShenandoahOldGeneration::WAITING_FOR_BOOTSTRAP:
      old_generation->transition_to(ShenandoahOldGeneration::BOOTSTRAPPING);
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
          heap->mmu_tracker()->record_old_marking_increment(true);
          heap->log_heap_status("At end of Concurrent Old Marking finishing increment");
        }
      } else if (original_state == ShenandoahOldGeneration::MARKING) {
        heap->mmu_tracker()->record_old_marking_increment(false);
        heap->log_heap_status("At end of Concurrent Old Marking increment");
      }
      break;
    }
    default:
      fatal("Unexpected state for old GC: %s", ShenandoahOldGeneration::state_name(old_generation->state()));
  }
}

bool ShenandoahGenerationalControlThread::resume_concurrent_old_cycle(ShenandoahOldGeneration* generation, GCCause::Cause cause) {
  assert(ShenandoahHeap::heap()->is_concurrent_old_mark_in_progress(), "Old mark should be in progress");
  log_debug(gc)("Resuming old generation with " UINT32_FORMAT " marking tasks queued", generation->task_queues()->tasks());

  ShenandoahHeap* heap = ShenandoahHeap::heap();

  // We can only tolerate being cancelled during concurrent marking or during preparation for mixed
  // evacuation. This flag here (passed by reference) is used to control precisely where the regulator
  // is allowed to cancel a GC.
  ShenandoahOldGC gc(generation, _allow_old_preemption);
  if (gc.collect(cause)) {
    heap->notify_gc_progress();
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

void ShenandoahGenerationalControlThread::service_concurrent_cycle(ShenandoahGeneration* generation, GCCause::Cause cause, bool do_old_gc_bootstrap) {
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

void ShenandoahGenerationalControlThread::service_concurrent_cycle(ShenandoahHeap* heap,
                                                       ShenandoahGeneration* generation,
                                                       GCCause::Cause& cause,
                                                       bool do_old_gc_bootstrap) {
  assert(!generation->is_old(), "Old GC takes a different control path");

  ShenandoahConcurrentGC gc(generation, do_old_gc_bootstrap);
  if (gc.collect(cause)) {
    // Cycle is complete
    heap->notify_gc_progress();
    generation->record_success_concurrent(gc.abbreviated());
  } else {
    assert(heap->cancelled_gc(), "Must have been cancelled");
    check_cancellation_or_degen(gc.degen_point());

    // Concurrent young-gen collection degenerates to young
    // collection.  Same for global collections.
    _degen_generation = generation;
  }
  const char* msg;
  ShenandoahMmuTracker* mmu_tracker = heap->mmu_tracker();
  if (generation->is_young()) {
    if (heap->cancelled_gc()) {
      msg = (do_old_gc_bootstrap) ? "At end of Interrupted Concurrent Bootstrap GC" :
            "At end of Interrupted Concurrent Young GC";
    } else {
      // We only record GC results if GC was successful
      msg = (do_old_gc_bootstrap) ? "At end of Concurrent Bootstrap GC" :
            "At end of Concurrent Young GC";
      if (heap->collection_set()->has_old_regions()) {
        mmu_tracker->record_mixed(get_gc_id());
      } else if (do_old_gc_bootstrap) {
        mmu_tracker->record_bootstrap(get_gc_id());
      } else {
        mmu_tracker->record_young(get_gc_id());
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
      mmu_tracker->record_global(get_gc_id());
    }
  }
  heap->log_heap_status(msg);
}

bool ShenandoahGenerationalControlThread::check_cancellation_or_degen(ShenandoahGC::ShenandoahDegenPoint point) {
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

void ShenandoahGenerationalControlThread::stop_service() {
  // Nothing to do here.
}

void ShenandoahGenerationalControlThread::service_stw_full_cycle(GCCause::Cause cause) {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();

  GCIdMark gc_id_mark;
  ShenandoahGCSession session(cause, heap->global_generation());

  ShenandoahFullGC gc;
  gc.collect(cause);
}

void ShenandoahGenerationalControlThread::service_stw_degenerated_cycle(GCCause::Cause cause,
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
    if (old->is_bootstrapping()) {
      old->transition_to(ShenandoahOldGeneration::MARKING);
    }
  }
}

void ShenandoahGenerationalControlThread::request_gc(GCCause::Cause cause) {
  if (ShenandoahCollectorPolicy::should_handle_requested_gc(cause)) {
    handle_requested_gc(cause);
  }
}

bool ShenandoahGenerationalControlThread::request_concurrent_gc(ShenandoahGenerationType generation) {
  if (_preemption_requested.is_set() || _requested_gc_cause != GCCause::_no_gc || ShenandoahHeap::heap()->cancelled_gc()) {
    // Ignore subsequent requests from the heuristics
    log_debug(gc, thread)("Reject request for concurrent gc: preemption_requested: %s, gc_requested: %s, gc_cancelled: %s",
                          BOOL_TO_STR(_preemption_requested.is_set()),
                          GCCause::to_string(_requested_gc_cause),
                          BOOL_TO_STR(ShenandoahHeap::heap()->cancelled_gc()));
    return false;
  }

  if (gc_mode() == none) {
    GCCause::Cause existing = Atomic::cmpxchg(&_requested_gc_cause, GCCause::_no_gc, GCCause::_shenandoah_concurrent_gc);
    if (existing != GCCause::_no_gc) {
      log_debug(gc, thread)("Reject request for concurrent gc because another gc is pending: %s", GCCause::to_string(existing));
      return false;
    }

    _requested_generation = generation;
    notify_control_thread();

    MonitorLocker ml(&_regulator_lock, Mutex::_no_safepoint_check_flag);
    while (gc_mode() == none) {
      ml.wait();
    }
    return true;
  }

  if (preempt_old_marking(generation)) {
    assert(gc_mode() == servicing_old, "Expected to be servicing old, but was: %s.", gc_mode_name(gc_mode()));
    GCCause::Cause existing = Atomic::cmpxchg(&_requested_gc_cause, GCCause::_no_gc, GCCause::_shenandoah_concurrent_gc);
    if (existing != GCCause::_no_gc) {
      log_debug(gc, thread)("Reject request to interrupt old gc because another gc is pending: %s", GCCause::to_string(existing));
      return false;
    }

    log_info(gc)("Preempting old generation mark to allow %s GC", shenandoah_generation_name(generation));
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

void ShenandoahGenerationalControlThread::notify_control_thread() {
  MonitorLocker locker(&_control_lock, Mutex::_no_safepoint_check_flag);
  _control_lock.notify();
}

bool ShenandoahGenerationalControlThread::preempt_old_marking(ShenandoahGenerationType generation) {
  return (generation == YOUNG) && _allow_old_preemption.try_unset();
}

void ShenandoahGenerationalControlThread::handle_requested_gc(GCCause::Cause cause) {
  // For normal requested GCs (System.gc) we want to block the caller. However,
  // for whitebox requested GC, we want to initiate the GC and return immediately.
  // The whitebox caller thread will arrange for itself to wait until the GC notifies
  // it that has reached the requested breakpoint (phase in the GC).
  if (cause == GCCause::_wb_breakpoint) {
    Atomic::xchg(&_requested_gc_cause, cause);
    notify_control_thread();
    return;
  }

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
    // This races with the regulator thread to start a concurrent gc and the
    // control thread to clear it at the start of a cycle. Threads here are
    // allowed to escalate a heuristic's request for concurrent gc.
    GCCause::Cause existing = Atomic::xchg(&_requested_gc_cause, cause);
    if (existing != GCCause::_no_gc) {
      log_debug(gc, thread)("GC request supersedes existing request: %s", GCCause::to_string(existing));
    }

    notify_control_thread();
    ml.wait();
    current_gc_id = get_gc_id();
  }
}

void ShenandoahGenerationalControlThread::notify_gc_waiters() {
  MonitorLocker ml(&_gc_waiters_lock);
  ml.notify_all();
}

const char* ShenandoahGenerationalControlThread::gc_mode_name(ShenandoahGenerationalControlThread::GCMode mode) {
  switch (mode) {
    case none:              return "idle";
    case concurrent_normal: return "normal";
    case stw_degenerated:   return "degenerated";
    case stw_full:          return "full";
    case servicing_old:     return "old";
    case bootstrapping_old: return "bootstrap";
    case stopped:           return "stopped";
    default:                return "unknown";
  }
}

void ShenandoahGenerationalControlThread::set_gc_mode(ShenandoahGenerationalControlThread::GCMode new_mode) {
  if (_mode != new_mode) {
    log_debug(gc)("Transition from: %s to: %s", gc_mode_name(_mode), gc_mode_name(new_mode));
    MonitorLocker ml(&_regulator_lock, Mutex::_no_safepoint_check_flag);
    _mode = new_mode;
    ml.notify_all();
  }
}
