/*
 * Copyright (c) 2013, 2018, Red Hat, Inc. All rights reserved.
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

#include "gc/shenandoah/shenandoahConcurrentMark.inline.hpp"
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeuristics.hpp"
#include "gc/shenandoah/shenandoahMonitoringSupport.hpp"
#include "gc/shenandoah/shenandoahControlThread.hpp"
#include "gc/shenandoah/shenandoahTraversalGC.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "gc/shenandoah/shenandoahVMOperations.hpp"
#include "gc/shenandoah/shenandoahWorkerPolicy.hpp"
#include "memory/iterator.hpp"
#include "memory/universe.hpp"

ShenandoahControlThread::ShenandoahControlThread() :
  ConcurrentGCThread(),
  _alloc_failure_waiters_lock(Mutex::leaf, "ShenandoahAllocFailureGC_lock", true, Monitor::_safepoint_check_always),
  _gc_waiters_lock(Mutex::leaf, "ShenandoahRequestedGC_lock", true, Monitor::_safepoint_check_always),
  _periodic_task(this),
  _requested_gc_cause(GCCause::_no_cause_specified),
  _degen_point(ShenandoahHeap::_degenerated_outside_cycle),
  _allocs_seen(0) {

  create_and_start(ShenandoahCriticalControlThreadPriority ? CriticalPriority : NearMaxPriority);
  _periodic_task.enroll();
  _periodic_satb_flush_task.enroll();
}

ShenandoahControlThread::~ShenandoahControlThread() {
  // This is here so that super is called.
}

void ShenandoahPeriodicTask::task() {
  _thread->handle_force_counters_update();
  _thread->handle_counters_update();
}

void ShenandoahPeriodicSATBFlushTask::task() {
  ShenandoahHeap::heap()->force_satb_flush_all_threads();
}

void ShenandoahControlThread::run_service() {
  ShenandoahHeap* heap = ShenandoahHeap::heap();

  int sleep = ShenandoahControlIntervalMin;

  double last_shrink_time = os::elapsedTime();
  double last_sleep_adjust_time = os::elapsedTime();

  // Shrink period avoids constantly polling regions for shrinking.
  // Having a period 10x lower than the delay would mean we hit the
  // shrinking with lag of less than 1/10-th of true delay.
  // ShenandoahUncommitDelay is in msecs, but shrink_period is in seconds.
  double shrink_period = (double)ShenandoahUncommitDelay / 1000 / 10;

  ShenandoahCollectorPolicy* policy = heap->shenandoah_policy();
  ShenandoahHeuristics* heuristics = heap->heuristics();
  while (!in_graceful_shutdown() && !should_terminate()) {
    // Figure out if we have pending requests.
    bool alloc_failure_pending = _alloc_failure_gc.is_set();
    bool explicit_gc_requested = _gc_requested.is_set() &&  is_explicit_gc(_requested_gc_cause);
    bool implicit_gc_requested = _gc_requested.is_set() && !is_explicit_gc(_requested_gc_cause);

    // This control loop iteration have seen this much allocations.
    size_t allocs_seen = Atomic::xchg<size_t>(0, &_allocs_seen);

    // Choose which GC mode to run in. The block below should select a single mode.
    GCMode mode = none;
    GCCause::Cause cause = GCCause::_last_gc_cause;
    ShenandoahHeap::ShenandoahDegenPoint degen_point = ShenandoahHeap::_degenerated_unset;

    if (alloc_failure_pending) {
      // Allocation failure takes precedence: we have to deal with it first thing
      log_info(gc)("Trigger: Handle Allocation Failure");

      cause = GCCause::_allocation_failure;

      // Consume the degen point, and seed it with default value
      degen_point = _degen_point;
      _degen_point = ShenandoahHeap::_degenerated_outside_cycle;

      if (ShenandoahDegeneratedGC && heuristics->should_degenerate_cycle()) {
        heuristics->record_allocation_failure_gc();
        policy->record_alloc_failure_to_degenerated(degen_point);
        mode = stw_degenerated;
      } else {
        heuristics->record_allocation_failure_gc();
        policy->record_alloc_failure_to_full();
        mode = stw_full;
      }

    } else if (explicit_gc_requested) {
      cause = _requested_gc_cause;
      log_info(gc)("Trigger: Explicit GC request (%s)", GCCause::to_string(cause));

      heuristics->record_requested_gc();

      if (ExplicitGCInvokesConcurrent) {
        policy->record_explicit_to_concurrent();
        if (heuristics->can_do_traversal_gc()) {
          mode = concurrent_traversal;
        } else {
          mode = concurrent_normal;
        }
        // Unload and clean up everything
        heap->set_process_references(heuristics->can_process_references());
        heap->set_unload_classes(heuristics->can_unload_classes());
      } else {
        policy->record_explicit_to_full();
        mode = stw_full;
      }
    } else if (implicit_gc_requested) {
      cause = _requested_gc_cause;
      log_info(gc)("Trigger: Implicit GC request (%s)", GCCause::to_string(cause));

      heuristics->record_requested_gc();

      if (ShenandoahImplicitGCInvokesConcurrent) {
        policy->record_implicit_to_concurrent();
        if (heuristics->can_do_traversal_gc()) {
          mode = concurrent_traversal;
        } else {
          mode = concurrent_normal;
        }

        // Unload and clean up everything
        heap->set_process_references(heuristics->can_process_references());
        heap->set_unload_classes(heuristics->can_unload_classes());
      } else {
        policy->record_implicit_to_full();
        mode = stw_full;
      }
    } else {
      // Potential normal cycle: ask heuristics if it wants to act
      if (heuristics->should_start_traversal_gc()) {
        mode = concurrent_traversal;
        cause = GCCause::_shenandoah_traversal_gc;
      } else if (heuristics->should_start_normal_gc()) {
        mode = concurrent_normal;
        cause = GCCause::_shenandoah_concurrent_gc;
      }

      // Ask policy if this cycle wants to process references or unload classes
      heap->set_process_references(heuristics->should_process_references());
      heap->set_unload_classes(heuristics->should_unload_classes());
    }

    // Blow all soft references on this cycle, if handling allocation failure,
    // or we are requested to do so unconditionally.
    if (alloc_failure_pending || ShenandoahAlwaysClearSoftRefs) {
      heap->soft_ref_policy()->set_should_clear_all_soft_refs(true);
    }

    bool gc_requested = (mode != none);
    assert (!gc_requested || cause != GCCause::_last_gc_cause, "GC cause should be set");

    if (gc_requested) {
      heap->reset_bytes_allocated_since_gc_start();

      // If GC was requested, we are sampling the counters even without actual triggers
      // from allocation machinery. This captures GC phases more accurately.
      set_forced_counters_update(true);

      // If GC was requested, we better dump freeset data for performance debugging
      {
        ShenandoahHeapLocker locker(heap->lock());
        heap->free_set()->log_status();
      }
    }

    switch (mode) {
      case none:
        break;
      case concurrent_traversal:
        service_concurrent_traversal_cycle(cause);
        break;
      case concurrent_normal:
        service_concurrent_normal_cycle(cause);
        break;
      case stw_degenerated:
        service_stw_degenerated_cycle(cause, degen_point);
        break;
      case stw_full:
        service_stw_full_cycle(cause);
        break;
      default:
        ShouldNotReachHere();
    }

    if (gc_requested) {
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
        Universe::update_heap_info_at_gc();
      }

      // Disable forced counters update, and update counters one more time
      // to capture the state at the end of GC session.
      handle_force_counters_update();
      set_forced_counters_update(false);

      // Retract forceful part of soft refs policy
      heap->soft_ref_policy()->set_should_clear_all_soft_refs(false);

      // Clear metaspace oom flag, if current cycle unloaded classes
      if (heap->unload_classes()) {
        heuristics->clear_metaspace_oom();
      }

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

    if (ShenandoahUncommit && (explicit_gc_requested || (current - last_shrink_time > shrink_period))) {
      // Try to uncommit enough stale regions. Explicit GC tries to uncommit everything.
      // Regular paths uncommit only occasionally.
      double shrink_before = explicit_gc_requested ?
                             current :
                             current - (ShenandoahUncommitDelay / 1000.0);
      service_uncommit(shrink_before);
      last_shrink_time = current;
    }

    // Wait before performing the next action. If allocation happened during this wait,
    // we exit sooner, to let heuristics re-evaluate new conditions. If we are at idle,
    // back off exponentially.
    if (_heap_changed.try_unset()) {
      sleep = ShenandoahControlIntervalMin;
    } else if ((current - last_sleep_adjust_time) * 1000 > ShenandoahControlIntervalAdjustPeriod){
      sleep = MIN2<int>(ShenandoahControlIntervalMax, MAX2(1, sleep * 2));
      last_sleep_adjust_time = current;
    }
    os::naked_short_sleep(sleep);
  }

  // Wait for the actual stop(), can't leave run_service() earlier.
  while (!should_terminate()) {
    os::naked_short_sleep(ShenandoahControlIntervalMin);
  }
}

void ShenandoahControlThread::service_concurrent_traversal_cycle(GCCause::Cause cause) {
  GCIdMark gc_id_mark;
  ShenandoahGCSession session(cause);

  ShenandoahHeap* heap = ShenandoahHeap::heap();
  TraceCollectorStats tcs(heap->monitoring_support()->concurrent_collection_counters());

  // Reset for upcoming cycle
  heap->entry_reset();

  heap->vmop_entry_init_traversal();

  if (check_cancellation_or_degen(ShenandoahHeap::_degenerated_traversal)) return;

  heap->entry_traversal();
  if (check_cancellation_or_degen(ShenandoahHeap::_degenerated_traversal)) return;

  heap->vmop_entry_final_traversal();

  heap->entry_cleanup();

  heap->heuristics()->record_success_concurrent();
  heap->shenandoah_policy()->record_success_concurrent();
}

void ShenandoahControlThread::service_concurrent_normal_cycle(GCCause::Cause cause) {
  // Normal cycle goes via all concurrent phases. If allocation failure (af) happens during
  // any of the concurrent phases, it first degrades to Degenerated GC and completes GC there.
  // If second allocation failure happens during Degenerated GC cycle (for example, when GC
  // tries to evac something and no memory is available), cycle degrades to Full GC.
  //
  // There are also two shortcuts through the normal cycle: a) immediate garbage shortcut, when
  // heuristics says there are no regions to compact, and all the collection comes from immediately
  // reclaimable regions; b) coalesced UR shortcut, when heuristics decides to coalesce UR with the
  // mark from the next cycle.
  //
  // ................................................................................................
  //
  //                                    (immediate garbage shortcut)                Concurrent GC
  //                             /-------------------------------------------\
  //                             |                       (coalesced UR)      v
  //                             |                  /----------------------->o
  //                             |                  |                        |
  //                             |                  |                        v
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
  ShenandoahHeap* heap = ShenandoahHeap::heap();

  if (check_cancellation_or_degen(ShenandoahHeap::_degenerated_outside_cycle)) return;

  GCIdMark gc_id_mark;
  ShenandoahGCSession session(cause);

  TraceCollectorStats tcs(heap->monitoring_support()->concurrent_collection_counters());

  // Reset for upcoming marking
  heap->entry_reset();

  // Start initial mark under STW
  heap->vmop_entry_init_mark();

  // Continue concurrent mark
  heap->entry_mark();
  if (check_cancellation_or_degen(ShenandoahHeap::_degenerated_mark)) return;

  // If not cancelled, can try to concurrently pre-clean
  heap->entry_preclean();

  // Complete marking under STW, and start evacuation
  heap->vmop_entry_final_mark();

  // Final mark might have reclaimed some immediate garbage, kick cleanup to reclaim
  // the space. This would be the last action if there is nothing to evacuate.
  heap->entry_cleanup();

  {
    ShenandoahHeapLocker locker(heap->lock());
    heap->free_set()->log_status();
  }

  // Continue the cycle with evacuation and optional update-refs.
  // This may be skipped if there is nothing to evacuate.
  // If so, evac_in_progress would be unset by collection set preparation code.
  if (heap->is_evacuation_in_progress()) {
    // Concurrently evacuate
    heap->entry_evac();
    if (check_cancellation_or_degen(ShenandoahHeap::_degenerated_evac)) return;

    // Perform update-refs phase, if required. This phase can be skipped if heuristics
    // decides to piggy-back the update-refs on the next marking cycle. On either path,
    // we need to turn off evacuation: either in init-update-refs, or in final-evac.
    if (heap->heuristics()->should_start_update_refs()) {
      heap->vmop_entry_init_updaterefs();
      heap->entry_updaterefs();
      if (check_cancellation_or_degen(ShenandoahHeap::_degenerated_updaterefs)) return;

      heap->vmop_entry_final_updaterefs();

      // Update references freed up collection set, kick the cleanup to reclaim the space.
      heap->entry_cleanup();

    } else {
      heap->vmop_entry_final_evac();
    }
  }

  // Cycle is complete
  heap->heuristics()->record_success_concurrent();
  heap->shenandoah_policy()->record_success_concurrent();
}

bool ShenandoahControlThread::check_cancellation_or_degen(ShenandoahHeap::ShenandoahDegenPoint point) {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  if (heap->cancelled_gc()) {
    assert (is_alloc_failure_gc() || in_graceful_shutdown(), "Cancel GC either for alloc failure GC, or gracefully exiting");
    if (!in_graceful_shutdown()) {
      assert (_degen_point == ShenandoahHeap::_degenerated_outside_cycle,
              "Should not be set yet: %s", ShenandoahHeap::degen_point_to_string(_degen_point));
      _degen_point = point;
    }
    return true;
  }
  return false;
}

void ShenandoahControlThread::stop_service() {
  // Nothing to do here.
}

void ShenandoahControlThread::service_stw_full_cycle(GCCause::Cause cause) {
  GCIdMark gc_id_mark;
  ShenandoahGCSession session(cause);

  ShenandoahHeap* heap = ShenandoahHeap::heap();
  heap->vmop_entry_full(cause);

  heap->heuristics()->record_success_full();
  heap->shenandoah_policy()->record_success_full();
}

void ShenandoahControlThread::service_stw_degenerated_cycle(GCCause::Cause cause, ShenandoahHeap::ShenandoahDegenPoint point) {
  assert (point != ShenandoahHeap::_degenerated_unset, "Degenerated point should be set");

  GCIdMark gc_id_mark;
  ShenandoahGCSession session(cause);

  ShenandoahHeap* heap = ShenandoahHeap::heap();
  heap->vmop_degenerated(point);

  heap->heuristics()->record_success_degenerated();
  heap->shenandoah_policy()->record_success_degenerated();
}

void ShenandoahControlThread::service_uncommit(double shrink_before) {
  ShenandoahHeap* heap = ShenandoahHeap::heap();

  // Determine if there is work to do. This avoids taking heap lock if there is
  // no work available, avoids spamming logs with superfluous logging messages,
  // and minimises the amount of work while locks are taken.

  if (heap->committed() <= heap->min_capacity()) return;

  bool has_work = false;
  for (size_t i = 0; i < heap->num_regions(); i++) {
    ShenandoahHeapRegion *r = heap->get_region(i);
    if (r->is_empty_committed() && (r->empty_time() < shrink_before)) {
      has_work = true;
      break;
    }
  }

  if (has_work) {
    heap->entry_uncommit(shrink_before);
  }
}

bool ShenandoahControlThread::is_explicit_gc(GCCause::Cause cause) const {
  return GCCause::is_user_requested_gc(cause) ||
         GCCause::is_serviceability_requested_gc(cause);
}

void ShenandoahControlThread::request_gc(GCCause::Cause cause) {
  assert(GCCause::is_user_requested_gc(cause) ||
         GCCause::is_serviceability_requested_gc(cause) ||
         cause == GCCause::_metadata_GC_clear_soft_refs ||
         cause == GCCause::_full_gc_alot ||
         cause == GCCause::_wb_full_gc ||
         cause == GCCause::_scavenge_alot,
         "only requested GCs here");

  if (is_explicit_gc(cause)) {
    if (!DisableExplicitGC) {
      handle_requested_gc(cause);
    }
  } else {
    handle_requested_gc(cause);
  }
}

void ShenandoahControlThread::handle_requested_gc(GCCause::Cause cause) {
  _requested_gc_cause = cause;
  _gc_requested.set();
  MonitorLockerEx ml(&_gc_waiters_lock);
  while (_gc_requested.is_set()) {
    ml.wait();
  }
}

void ShenandoahControlThread::handle_alloc_failure(size_t words) {
  ShenandoahHeap* heap = ShenandoahHeap::heap();

  assert(current()->is_Java_thread(), "expect Java thread here");

  if (try_set_alloc_failure_gc()) {
    // Only report the first allocation failure
    log_info(gc)("Failed to allocate " SIZE_FORMAT "%s",
                 byte_size_in_proper_unit(words * HeapWordSize), proper_unit_for_byte_size(words * HeapWordSize));

    // Now that alloc failure GC is scheduled, we can abort everything else
    heap->cancel_gc(GCCause::_allocation_failure);
  }

  MonitorLockerEx ml(&_alloc_failure_waiters_lock);
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
  MonitorLockerEx ml(&_alloc_failure_waiters_lock);
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
  MonitorLockerEx ml(&_gc_waiters_lock);
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
  // Notify that something had changed.
  if (_heap_changed.is_unset()) {
    _heap_changed.set();
  }
}

void ShenandoahControlThread::pacing_notify_alloc(size_t words) {
  assert(ShenandoahPacing, "should only call when pacing is enabled");
  Atomic::add(words, &_allocs_seen);
}

void ShenandoahControlThread::set_forced_counters_update(bool value) {
  _force_counters_update.set_cond(value);
}

void ShenandoahControlThread::print() const {
  print_on(tty);
}

void ShenandoahControlThread::print_on(outputStream* st) const {
  st->print("Shenandoah Concurrent Thread");
  Thread::print_on(st);
  st->cr();
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
