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

#include "gc/shenandoah/shenandoahAsserts.hpp"
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahConcurrentGC.hpp"
#include "gc/shenandoah/shenandoahDegeneratedGC.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahFullGC.hpp"
#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/shenandoahGenerationalControlThread.hpp"
#include "gc/shenandoah/shenandoahGenerationalHeap.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahMonitoringSupport.hpp"
#include "gc/shenandoah/shenandoahOldGC.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"
#include "logging/log.hpp"
#include "memory/metaspaceStats.hpp"
#include "memory/metaspaceUtils.hpp"
#include "runtime/atomic.hpp"
#include "utilities/events.hpp"

ShenandoahGenerationalControlThread::ShenandoahGenerationalControlThread() :
  _control_lock(Mutex::nosafepoint - 2, "ShenandoahGCRequest_lock", true),
  _requested_gc_cause(GCCause::_no_gc),
  _requested_generation(nullptr),
  _gc_mode(none),
  _degen_point(ShenandoahGC::_degenerated_unset),
  _heap(ShenandoahGenerationalHeap::heap()),
  _age_period(0) {
  shenandoah_assert_generational();
  set_name("Shenandoah Control Thread");
  create_and_start();
}

void ShenandoahGenerationalControlThread::run_service() {

  ShenandoahGCRequest request;
  while (!should_terminate()) {

    // Figure out if we have pending requests.
    check_for_request(request);

    if (request.cause == GCCause::_shenandoah_stop_vm) {
      break;
    }

    if (request.cause != GCCause::_no_gc) {
      run_gc_cycle(request);
    }

    // If the cycle was cancelled, continue the next iteration to deal with it. Otherwise,
    // if there was no other cycle requested, cleanup and wait for the next request.
    if (!_heap->cancelled_gc()) {
      MonitorLocker ml(&_control_lock, Mutex::_no_safepoint_check_flag);
      if (_requested_gc_cause == GCCause::_no_gc) {
        set_gc_mode(ml, none);
        ml.wait();
      }
    }
  }

  // In case any threads are waiting for a cycle to happen, notify them so they observe the shutdown.
  notify_gc_waiters();
  notify_alloc_failure_waiters();
  set_gc_mode(stopped);
}

void ShenandoahGenerationalControlThread::stop_service() {
  log_debug(gc, thread)("Stopping control thread");
  MonitorLocker ml(&_control_lock, Mutex::_no_safepoint_check_flag);
  _heap->cancel_gc(GCCause::_shenandoah_stop_vm);
  _requested_gc_cause = GCCause::_shenandoah_stop_vm;
  notify_cancellation(ml, GCCause::_shenandoah_stop_vm);
  // We can't wait here because it may interfere with the active cycle's ability
  // to reach a safepoint (this runs on a java thread).
}

void ShenandoahGenerationalControlThread::check_for_request(ShenandoahGCRequest& request) {
  // Hold the lock while we read request cause and generation
  MonitorLocker ml(&_control_lock, Mutex::_no_safepoint_check_flag);
  if (_heap->cancelled_gc()) {
    // The previous request was cancelled. Either it was cancelled for an allocation
    // failure (degenerated cycle), or old marking was cancelled to run a young collection.
    // In either case, the correct generation for the next cycle can be determined by
    // the cancellation cause.
    request.cause = _heap->cancelled_cause();
    if (request.cause == GCCause::_shenandoah_concurrent_gc) {
      request.generation = _heap->young_generation();
      _heap->clear_cancelled_gc(false);
    }
  } else {
    request.cause = _requested_gc_cause;
    request.generation = _requested_generation;

    // Only clear these if we made a request from them. In the case of a cancelled gc,
    // we do not want to inadvertently lose this pending request.
    _requested_gc_cause = GCCause::_no_gc;
    _requested_generation = nullptr;
  }

  if (request.cause == GCCause::_no_gc || request.cause == GCCause::_shenandoah_stop_vm) {
    return;
  }

  GCMode mode;
  if (ShenandoahCollectorPolicy::is_allocation_failure(request.cause)) {
    mode = prepare_for_allocation_failure_gc(request);
  } else if (ShenandoahCollectorPolicy::is_explicit_gc(request.cause)) {
    mode = prepare_for_explicit_gc(request);
  } else {
    mode = prepare_for_concurrent_gc(request);
  }
  set_gc_mode(ml, mode);
}

ShenandoahGenerationalControlThread::GCMode ShenandoahGenerationalControlThread::prepare_for_allocation_failure_gc(ShenandoahGCRequest &request) {

  if (_degen_point == ShenandoahGC::_degenerated_unset) {
    _degen_point = ShenandoahGC::_degenerated_outside_cycle;
    request.generation = _heap->young_generation();
  } else if (request.generation->is_old()) {
    // This means we degenerated during the young bootstrap for the old generation
    // cycle. The following degenerated cycle should therefore also be young.
    request.generation = _heap->young_generation();
  }

  ShenandoahHeuristics* heuristics = request.generation->heuristics();
  bool old_gen_evacuation_failed = _heap->old_generation()->clear_failed_evacuation();

  heuristics->log_trigger("Handle Allocation Failure");

  // Do not bother with degenerated cycle if old generation evacuation failed or if humongous allocation failed
  if (ShenandoahDegeneratedGC && heuristics->should_degenerate_cycle() &&
      !old_gen_evacuation_failed && request.cause != GCCause::_shenandoah_humongous_allocation_failure) {
    heuristics->record_allocation_failure_gc();
    _heap->shenandoah_policy()->record_alloc_failure_to_degenerated(_degen_point);
    return stw_degenerated;
  } else {
    heuristics->record_allocation_failure_gc();
    _heap->shenandoah_policy()->record_alloc_failure_to_full();
    request.generation = _heap->global_generation();
    return stw_full;
  }
}

ShenandoahGenerationalControlThread::GCMode ShenandoahGenerationalControlThread::prepare_for_explicit_gc(ShenandoahGCRequest &request) const {
  ShenandoahHeuristics* global_heuristics = _heap->global_generation()->heuristics();
  request.generation = _heap->global_generation();
  global_heuristics->log_trigger("GC request (%s)", GCCause::to_string(request.cause));
  global_heuristics->record_requested_gc();

  if (ShenandoahCollectorPolicy::should_run_full_gc(request.cause)) {
    return stw_full;;
  } else {
    // Unload and clean up everything. Note that this is an _explicit_ request and so does not use
    // the same `should_unload_classes` call as the regulator's concurrent gc request.
    _heap->set_unload_classes(global_heuristics->can_unload_classes());
    return concurrent_normal;
  }
}

ShenandoahGenerationalControlThread::GCMode ShenandoahGenerationalControlThread::prepare_for_concurrent_gc(const ShenandoahGCRequest &request) const {
  assert(!(request.generation->is_old() && _heap->old_generation()->is_doing_mixed_evacuations()),
             "Old heuristic should not request cycles while it waits for mixed evacuations");

  if (request.generation->is_global()) {
    ShenandoahHeuristics* global_heuristics = _heap->global_generation()->heuristics();
    _heap->set_unload_classes(global_heuristics->should_unload_classes());
  } else {
    _heap->set_unload_classes(false);
  }

  // preemption was requested or this is a regular cycle
  return request.generation->is_old() ? servicing_old : concurrent_normal;
}

void ShenandoahGenerationalControlThread::maybe_set_aging_cycle() {
  if (_age_period-- == 0) {
    _heap->set_aging_cycle(true);
    _age_period = ShenandoahAgingCyclePeriod - 1;
  } else {
    _heap->set_aging_cycle(false);
  }
}

void ShenandoahGenerationalControlThread::run_gc_cycle(const ShenandoahGCRequest& request) {

  log_debug(gc, thread)("Starting GC (%s): %s, %s", gc_mode_name(gc_mode()), GCCause::to_string(request.cause), request.generation->name());
  assert(gc_mode() != none, "GC mode cannot be none here");

  // Blow away all soft references on this cycle, if handling allocation failure,
  // either implicit or explicit GC request, or we are requested to do so unconditionally.
  if (request.generation->is_global() && (ShenandoahCollectorPolicy::is_allocation_failure(request.cause) || ShenandoahCollectorPolicy::is_explicit_gc(request.cause) || ShenandoahAlwaysClearSoftRefs)) {
    _heap->soft_ref_policy()->set_should_clear_all_soft_refs(true);
  }

  // GC is starting, bump the internal ID
  update_gc_id();

  GCIdMark gc_id_mark;

  _heap->reset_bytes_allocated_since_gc_start();

  MetaspaceCombinedStats meta_sizes = MetaspaceUtils::get_combined_statistics();

  // If GC was requested, we are sampling the counters even without actual triggers
  // from allocation machinery. This captures GC phases more accurately.
  _heap->set_forced_counters_update(true);

  // If GC was requested, we better dump freeset data for performance debugging
  _heap->free_set()->log_status_under_lock();

  {
    // Cannot uncommit bitmap slices during concurrent reset
    ShenandoahNoUncommitMark forbid_region_uncommit(_heap);

    _heap->print_before_gc();
    switch (gc_mode()) {
      case concurrent_normal: {
        service_concurrent_normal_cycle(request);
        break;
      }
      case stw_degenerated: {
        service_stw_degenerated_cycle(request);
        break;
      }
      case stw_full: {
        service_stw_full_cycle(request.cause);
        break;
      }
      case servicing_old: {
        assert(request.generation->is_old(), "Expected old generation here");
        service_concurrent_old_cycle(request);
        break;
      }
      default:
        ShouldNotReachHere();
    }
    _heap->print_after_gc();
  }

  // If this cycle completed successfully, notify threads waiting for gc
  if (!_heap->cancelled_gc()) {
    notify_gc_waiters();
    notify_alloc_failure_waiters();
  }

  // Report current free set state at the end of cycle, whether
  // it is a normal completion, or the abort.
  _heap->free_set()->log_status_under_lock();

  // Notify Universe about new heap usage. This has implications for
  // global soft refs policy, and we better report it every time heap
  // usage goes down.
  _heap->update_capacity_and_used_at_gc();

  // Signal that we have completed a visit to all live objects.
  _heap->record_whole_heap_examined_timestamp();

  // Disable forced counters update, and update counters one more time
  // to capture the state at the end of GC session.
  _heap->handle_force_counters_update();
  _heap->set_forced_counters_update(false);

  // Retract forceful part of soft refs policy
  _heap->soft_ref_policy()->set_should_clear_all_soft_refs(false);

  // Clear metaspace oom flag, if current cycle unloaded classes
  if (_heap->unload_classes()) {
    _heap->global_generation()->heuristics()->clear_metaspace_oom();
  }

  process_phase_timings();

  // Print Metaspace change following GC (if logging is enabled).
  MetaspaceUtils::print_metaspace_change(meta_sizes);

  // Check if we have seen a new target for soft max heap size or if a gc was requested.
  // Either of these conditions will attempt to uncommit regions.
  if (ShenandoahUncommit) {
    if (_heap->check_soft_max_changed()) {
      _heap->notify_soft_max_changed();
    } else if (ShenandoahCollectorPolicy::is_explicit_gc(request.cause)) {
      _heap->notify_explicit_gc_requested();
    }
  }

  log_debug(gc, thread)("Completed GC (%s): %s, %s, cancelled: %s",
    gc_mode_name(gc_mode()), GCCause::to_string(request.cause), request.generation->name(), GCCause::to_string(_heap->cancelled_cause()));
}

void ShenandoahGenerationalControlThread::process_phase_timings() const {
  // Commit worker statistics to cycle data
  _heap->phase_timings()->flush_par_workers_to_cycle();

  ShenandoahEvacuationTracker* evac_tracker = _heap->evac_tracker();
  ShenandoahCycleStats         evac_stats   = evac_tracker->flush_cycle_to_global();

  // Print GC stats for current cycle
  {
    LogTarget(Info, gc, stats) lt;
    if (lt.is_enabled()) {
      ResourceMark rm;
      LogStream ls(lt);
      _heap->phase_timings()->print_cycle_on(&ls);
      evac_tracker->print_evacuations_on(&ls, &evac_stats.workers,
                                              &evac_stats.mutators);
    }
  }

  // Commit statistics to globals
  _heap->phase_timings()->flush_cycle_to_global();
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
void ShenandoahGenerationalControlThread::service_concurrent_normal_cycle(const ShenandoahGCRequest& request) {
  log_info(gc, ergo)("Start GC cycle (%s)", request.generation->name());
  if (request.generation->is_old()) {
    service_concurrent_old_cycle(request);
  } else {
    service_concurrent_cycle(request.generation, request.cause, false);
  }
}

void ShenandoahGenerationalControlThread::service_concurrent_old_cycle(const ShenandoahGCRequest& request) {
  ShenandoahOldGeneration* old_generation = _heap->old_generation();
  ShenandoahYoungGeneration* young_generation = _heap->young_generation();
  ShenandoahOldGeneration::State original_state = old_generation->state();

  TraceCollectorStats tcs(_heap->monitoring_support()->concurrent_collection_counters());

  _heap->increment_total_collections(false);

  switch (original_state) {
    case ShenandoahOldGeneration::FILLING: {
      ShenandoahGCSession session(request.cause, old_generation);
      assert(gc_mode() == servicing_old, "Filling should be servicing old");
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
        log_info(gc, thread)("Preparation for old generation cycle was cancelled");
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
      service_concurrent_cycle(young_generation, request.cause, true);
      process_phase_timings();
      if (_heap->cancelled_gc()) {
        // Young generation bootstrap cycle has failed. Concurrent mark for old generation
        // is going to resume after degenerated bootstrap cycle completes.
        log_info(gc)("Bootstrap cycle for old generation was cancelled");
        return;
      }

      assert(_degen_point == ShenandoahGC::_degenerated_unset, "Degen point should not be set if gc wasn't cancelled");

      // From here we will 'resume' the old concurrent mark. This will skip reset
      // and init mark for the concurrent mark. All of that work will have been
      // done by the bootstrapping young cycle.
      set_gc_mode(servicing_old);
      old_generation->transition_to(ShenandoahOldGeneration::MARKING);
    }
    case ShenandoahOldGeneration::MARKING: {
      ShenandoahGCSession session(request.cause, old_generation);
      bool marking_complete = resume_concurrent_old_cycle(old_generation, request.cause);
      if (marking_complete) {
        assert(old_generation->state() != ShenandoahOldGeneration::MARKING, "Should not still be marking");
        if (original_state == ShenandoahOldGeneration::MARKING) {
          _heap->mmu_tracker()->record_old_marking_increment(true);
          _heap->log_heap_status("At end of Concurrent Old Marking finishing increment");
        }
      } else if (original_state == ShenandoahOldGeneration::MARKING) {
        _heap->mmu_tracker()->record_old_marking_increment(false);
        _heap->log_heap_status("At end of Concurrent Old Marking increment");
      }
      break;
    }
    default:
      fatal("Unexpected state for old GC: %s", ShenandoahOldGeneration::state_name(old_generation->state()));
  }
}

bool ShenandoahGenerationalControlThread::resume_concurrent_old_cycle(ShenandoahOldGeneration* generation, GCCause::Cause cause) {
  assert(_heap->is_concurrent_old_mark_in_progress(), "Old mark should be in progress");
  log_debug(gc)("Resuming old generation with " UINT32_FORMAT " marking tasks queued", generation->task_queues()->tasks());

  // We can only tolerate being cancelled during concurrent marking or during preparation for mixed
  // evacuation. This flag here (passed by reference) is used to control precisely where the regulator
  // is allowed to cancel a GC.
  ShenandoahOldGC gc(generation, _allow_old_preemption);
  if (gc.collect(cause)) {
    _heap->notify_gc_progress();
    generation->record_success_concurrent(false);
  }

  if (_heap->cancelled_gc()) {
    // It's possible the gc cycle was cancelled after the last time the collection checked for cancellation. In which
    // case, the old gc cycle is still completed, and we have to deal with this cancellation. We set the degeneration
    // point to be outside the cycle because if this is an allocation failure, that is what must be done (there is no
    // degenerated old cycle). If the cancellation was due to a heuristic wanting to start a young cycle, then we are
    // not actually going to a degenerated cycle, so don't set the degeneration point here.
    if (ShenandoahCollectorPolicy::is_allocation_failure(cause)) {
      check_cancellation_or_degen(ShenandoahGC::_degenerated_outside_cycle);
    } else if (cause == GCCause::_shenandoah_concurrent_gc) {
      _heap->shenandoah_policy()->record_interrupted_old();
    }
    return false;
  }
  return true;
}

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
void ShenandoahGenerationalControlThread::service_concurrent_cycle(ShenandoahGeneration* generation,
                                                                   GCCause::Cause cause,
                                                                   bool do_old_gc_bootstrap) {
  // At this point:
  //  if (generation == YOUNG), this is a normal young cycle or a bootstrap cycle
  //  if (generation == GLOBAL), this is a GLOBAL cycle
  // In either case, we want to age old objects if this is an aging cycle
  maybe_set_aging_cycle();

  ShenandoahGCSession session(cause, generation);
  TraceCollectorStats tcs(_heap->monitoring_support()->concurrent_collection_counters());

  assert(!generation->is_old(), "Old GC takes a different control path");

  ShenandoahConcurrentGC gc(generation, do_old_gc_bootstrap);
  _heap->increment_total_collections(false);
  if (gc.collect(cause)) {
    // Cycle is complete
    _heap->notify_gc_progress();
    generation->record_success_concurrent(gc.abbreviated());
  } else {
    assert(_heap->cancelled_gc(), "Must have been cancelled");
    check_cancellation_or_degen(gc.degen_point());
  }

  const char* msg;
  ShenandoahMmuTracker* mmu_tracker = _heap->mmu_tracker();
  if (generation->is_young()) {
    if (_heap->cancelled_gc()) {
      msg = (do_old_gc_bootstrap) ? "At end of Interrupted Concurrent Bootstrap GC" :
            "At end of Interrupted Concurrent Young GC";
    } else {
      // We only record GC results if GC was successful
      msg = (do_old_gc_bootstrap) ? "At end of Concurrent Bootstrap GC" :
            "At end of Concurrent Young GC";
      if (_heap->collection_set()->has_old_regions()) {
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
    if (_heap->cancelled_gc()) {
      msg = "At end of Interrupted Concurrent GLOBAL GC";
    } else {
      // We only record GC results if GC was successful
      msg = "At end of Concurrent Global GC";
      mmu_tracker->record_global(get_gc_id());
    }
  }
  _heap->log_heap_status(msg);
}

bool ShenandoahGenerationalControlThread::check_cancellation_or_degen(ShenandoahGC::ShenandoahDegenPoint point) {
  if (!_heap->cancelled_gc()) {
    return false;
  }

  if (_heap->cancelled_cause() == GCCause::_shenandoah_stop_vm
    || _heap->cancelled_cause() == GCCause::_shenandoah_concurrent_gc) {
    log_debug(gc, thread)("Cancellation detected, reason: %s", GCCause::to_string(_heap->cancelled_cause()));
    return true;
  }

  if (ShenandoahCollectorPolicy::is_allocation_failure(_heap->cancelled_cause())) {
    assert(_degen_point == ShenandoahGC::_degenerated_unset,
           "Should not be set yet: %s", ShenandoahGC::degen_point_to_string(_degen_point));
    _degen_point = point;
    log_debug(gc, thread)("Cancellation detected:, reason: %s, degen point: %s",
                          GCCause::to_string(_heap->cancelled_cause()),
                          ShenandoahGC::degen_point_to_string(_degen_point));
    return true;
  }

  fatal("Cancel GC either for alloc failure GC, or gracefully exiting, or to pause old generation marking");
  return false;
}

void ShenandoahGenerationalControlThread::service_stw_full_cycle(GCCause::Cause cause) {
  _heap->increment_total_collections(true);
  ShenandoahGCSession session(cause, _heap->global_generation());
  maybe_set_aging_cycle();
  ShenandoahFullGC gc;
  gc.collect(cause);
  _degen_point = ShenandoahGC::_degenerated_unset;
}

void ShenandoahGenerationalControlThread::service_stw_degenerated_cycle(const ShenandoahGCRequest& request) {
  assert(_degen_point != ShenandoahGC::_degenerated_unset, "Degenerated point should be set");
  _heap->increment_total_collections(false);

  ShenandoahGCSession session(request.cause, request.generation);

  ShenandoahDegenGC gc(_degen_point, request.generation);
  gc.collect(request.cause);
  _degen_point = ShenandoahGC::_degenerated_unset;

  assert(_heap->young_generation()->task_queues()->is_empty(), "Unexpected young generation marking tasks");
  if (request.generation->is_global()) {
    assert(_heap->old_generation()->task_queues()->is_empty(), "Unexpected old generation marking tasks");
    assert(_heap->global_generation()->task_queues()->is_empty(), "Unexpected global generation marking tasks");
  } else {
    assert(request.generation->is_young(), "Expected degenerated young cycle, if not global.");
    ShenandoahOldGeneration* old = _heap->old_generation();
    if (old->is_bootstrapping()) {
      old->transition_to(ShenandoahOldGeneration::MARKING);
    }
  }
}

void ShenandoahGenerationalControlThread::request_gc(GCCause::Cause cause) {
  if (ShenandoahCollectorPolicy::is_allocation_failure(cause)) {
    // GC should already be cancelled. Here we are just notifying the control thread to
    // wake up and handle the cancellation request, so we don't need to set _requested_gc_cause.
    notify_cancellation(cause);
  } else if (ShenandoahCollectorPolicy::should_handle_requested_gc(cause)) {
    handle_requested_gc(cause);
  }
}

bool ShenandoahGenerationalControlThread::request_concurrent_gc(ShenandoahGeneration* generation) {
  if (_heap->cancelled_gc()) {
    // Ignore subsequent requests from the heuristics
    log_debug(gc, thread)("Reject request for concurrent gc: gc_requested: %s, gc_cancelled: %s",
                          GCCause::to_string(_requested_gc_cause),
                          BOOL_TO_STR(_heap->cancelled_gc()));
    return false;
  }

  MonitorLocker ml(&_control_lock, Mutex::_no_safepoint_check_flag);
  if (gc_mode() == servicing_old) {
    if (!preempt_old_marking(generation)) {
      log_debug(gc, thread)("Cannot start young, old collection is not preemptible");
      return false;
    }

    // Cancel the old GC and wait for the control thread to start servicing the new request.
    log_info(gc)("Preempting old generation mark to allow %s GC", generation->name());
    while (gc_mode() == servicing_old) {
      ShenandoahHeap::heap()->cancel_gc(GCCause::_shenandoah_concurrent_gc);
      notify_cancellation(ml, GCCause::_shenandoah_concurrent_gc);
      ml.wait();
    }
    return true;
  }

  if (gc_mode() == none) {
    const size_t current_gc_id = get_gc_id();
    while (gc_mode() == none && current_gc_id == get_gc_id()) {
      if (_requested_gc_cause != GCCause::_no_gc) {
        log_debug(gc, thread)("Reject request for concurrent gc because another gc is pending: %s", GCCause::to_string(_requested_gc_cause));
        return false;
      }

      notify_control_thread(ml, GCCause::_shenandoah_concurrent_gc, generation);
      ml.wait();
    }
    return true;
  }


  log_debug(gc, thread)("Reject request for concurrent gc: mode: %s, allow_old_preemption: %s",
                        gc_mode_name(gc_mode()),
                        BOOL_TO_STR(_allow_old_preemption.is_set()));
  return false;
}

void ShenandoahGenerationalControlThread::notify_control_thread(GCCause::Cause cause, ShenandoahGeneration* generation) {
  MonitorLocker ml(&_control_lock, Mutex::_no_safepoint_check_flag);
  notify_control_thread(ml, cause, generation);
}

void ShenandoahGenerationalControlThread::notify_control_thread(MonitorLocker& ml, GCCause::Cause cause, ShenandoahGeneration* generation) {
  assert(_control_lock.is_locked(), "Request lock must be held here");
  log_debug(gc, thread)("Notify control (%s): %s, %s", gc_mode_name(gc_mode()), GCCause::to_string(cause), generation->name());
  _requested_gc_cause = cause;
  _requested_generation = generation;
  ml.notify();
}

void ShenandoahGenerationalControlThread::notify_cancellation(GCCause::Cause cause) {
  MonitorLocker ml(&_control_lock, Mutex::_no_safepoint_check_flag);
  notify_cancellation(ml, cause);
}

void ShenandoahGenerationalControlThread::notify_cancellation(MonitorLocker& ml, GCCause::Cause cause) {
  assert(_heap->cancelled_gc(), "GC should already be cancelled");
  log_debug(gc,thread)("Notify control (%s): %s", gc_mode_name(gc_mode()), GCCause::to_string(cause));
  ml.notify();
}

bool ShenandoahGenerationalControlThread::preempt_old_marking(ShenandoahGeneration* generation) {
  return generation->is_young() && _allow_old_preemption.try_unset();
}

void ShenandoahGenerationalControlThread::handle_requested_gc(GCCause::Cause cause) {
  // For normal requested GCs (System.gc) we want to block the caller. However,
  // for whitebox requested GC, we want to initiate the GC and return immediately.
  // The whitebox caller thread will arrange for itself to wait until the GC notifies
  // it that has reached the requested breakpoint (phase in the GC).
  if (cause == GCCause::_wb_breakpoint) {
    notify_control_thread(cause, ShenandoahHeap::heap()->global_generation());
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
  const size_t required_gc_id = current_gc_id + 1;
  while (current_gc_id < required_gc_id && !should_terminate()) {
    // Make requests to run a global cycle until at least one is completed
    notify_control_thread(cause, ShenandoahHeap::heap()->global_generation());
    ml.wait();
    current_gc_id = get_gc_id();
  }
}

void ShenandoahGenerationalControlThread::notify_gc_waiters() {
  MonitorLocker ml(&_gc_waiters_lock);
  ml.notify_all();
}

const char* ShenandoahGenerationalControlThread::gc_mode_name(GCMode mode) {
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

void ShenandoahGenerationalControlThread::set_gc_mode(GCMode new_mode) {
  MonitorLocker ml(&_control_lock, Mutex::_no_safepoint_check_flag);
  set_gc_mode(ml, new_mode);
}

void ShenandoahGenerationalControlThread::set_gc_mode(MonitorLocker& ml, GCMode new_mode) {
  if (_gc_mode != new_mode) {
    log_debug(gc, thread)("Transition from: %s to: %s", gc_mode_name(_gc_mode), gc_mode_name(new_mode));
    EventMark event("Control thread transition from: %s, to %s", gc_mode_name(_gc_mode), gc_mode_name(new_mode));
    _gc_mode = new_mode;
    ml.notify_all();
  }
}
