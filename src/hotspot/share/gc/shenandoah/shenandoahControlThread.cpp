/*
 * Copyright (c) 2013, 2021, Red Hat, Inc. All rights reserved.
 * Copyright (C) 2022, Tencent. All rights reserved.
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

#include "gc/shared/allocTracer.hpp"
#include "gc/shenandoah/heuristics/shenandoahHeuristics.hpp"
#include "gc/shenandoah/mode/shenandoahMode.hpp"
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahConcurrentGC.hpp"
#include "gc/shenandoah/shenandoahControlThread.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahFullGC.hpp"
#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahMonitoringSupport.hpp"
#include "gc/shenandoah/shenandoahReferenceProcessor.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "logging/log.hpp"
#include "memory/metaspaceStats.hpp"
#include "memory/metaspaceUtils.hpp"

ShenandoahControlThread::ShenandoahControlThread() :
  _requested_gc_cause(GCCause::_no_gc),
  _control_lock(CONTROL_LOCK_RANK, "ShenandoahControl_lock", true) {
  set_name("ShenControl");
  create_and_start();
}

void ShenandoahControlThread::run_service() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  const GCMode default_mode = concurrent_normal;
  const GCCause::Cause default_cause = GCCause::_shenandoah_concurrent_gc;
  int sleep = ShenandoahControlIntervalMin;

  double last_sleep_adjust_time = os::elapsedTime();

  ShenandoahHeuristics* const heuristics = heap->heuristics();
  double most_recent_wake_time = os::elapsedTime();
  while (!should_terminate()) {
    const GCCause::Cause cancelled_cause = heap->cancelled_cause();
    if (cancelled_cause == GCCause::_shenandoah_stop_vm) {
      break;
    }
    assert(cancelled_cause == GCCause::_no_gc, "Cannot be cancelled for: %s", GCCause::to_string(cancelled_cause));

    // Figure out if we have pending requests.
    const bool is_gc_requested = _gc_requested.try_unset();
    const GCCause::Cause requested_gc_cause = _requested_gc_cause;

    // Choose which GC mode to run in. The block below should select a single mode.
    GCMode mode = none;
    GCCause::Cause cause = GCCause::_last_gc_cause;

    if (is_gc_requested) {
      cause = requested_gc_cause;
      heuristics->log_trigger("GC request (%s)", GCCause::to_string(cause));
      heuristics->record_requested_gc();

      if (ShenandoahCollectorPolicy::should_run_full_gc(cause)) {
        mode = stw_full;
      } else {
        mode = default_mode;
        // Unload and clean up everything
        heap->set_unload_classes(heuristics->can_unload_classes());
      }
    } else {
      // Potential normal cycle: ask heuristics if it wants to act
      if (heuristics->should_start_gc()) {
        mode = default_mode;
        cause = default_cause;
      }

      // Ask policy if this cycle wants to process references or unload classes
      heap->set_unload_classes(heuristics->should_unload_classes());
    }

    // Blow all soft references on this cycle, if handling allocation failure,
    // either implicit or explicit GC request,  or we are requested to do so unconditionally.
    if (is_gc_requested || ShenandoahAlwaysClearSoftRefs) {
      heap->global_generation()->ref_processor()->set_soft_reference_policy(true);
    }

    const bool gc_requested = (mode != none);
    assert (!gc_requested || cause != GCCause::_last_gc_cause, "GC cause should be set");

    if (gc_requested) {
      // Cannot uncommit bitmap slices during concurrent reset
      ShenandoahNoUncommitMark forbid_region_uncommit(heap);

      // GC is starting, bump the internal ID
      update_gc_id();

      GCIdMark gc_id_mark;

      heuristics->cancel_trigger_request();

      MetaspaceCombinedStats meta_sizes = MetaspaceUtils::get_combined_statistics();

      // If GC was requested, we are sampling the counters even without actual triggers
      // from allocation machinery. This captures GC phases more accurately.
      heap->set_forced_counters_update(true);

      // If GC was requested, we better dump freeset data for performance debugging
      heap->free_set()->log_status_under_lock();

      heap->print_before_gc();
      switch (mode) {
        case concurrent_normal:
          service_concurrent_normal_cycle(cause);
          break;
        case stw_full:
          service_stw_full_cycle(cause);
          break;
        default:
          ShouldNotReachHere();
      }
      heap->print_after_gc();

      // Try to reduce concurrent worker count
      decrease_concurrent_worker_count();

      // Notify waiters that a cycle is completed. They'll decide for themselves to continue waiting or not.
      notify_gc_waiters();
      notify_alloc_waiters();

      // Report current free set state at the end of cycle, whether
      // it is a normal completion, or the abort.
      heap->free_set()->log_status_under_lock();

      {
        // Notify Universe about new heap usage. This has implications for
        // global soft refs policy, and we better report it every time heap
        // usage goes down.
        ShenandoahHeapLocker locker(heap->lock());
        heap->update_capacity_and_used_at_gc();
      }

      // Signal that we have completed a visit to all live objects.
      heap->record_whole_heap_examined_timestamp();

      // Disable forced counters update, and update counters one more time
      // to capture the state at the end of GC session.
      heap->handle_force_counters_update();
      heap->set_forced_counters_update(false);

      // Retract forceful part of soft refs policy
      heap->global_generation()->ref_processor()->set_soft_reference_policy(false);

      // Clear metaspace oom flag, if current cycle unloaded classes
      if (heap->unload_classes()) {
        heuristics->clear_metaspace_oom();
      }

      // Manage and print gc stats
      heap->process_gc_stats();

      // Print Metaspace change following GC (if logging is enabled).
      MetaspaceUtils::print_metaspace_change(meta_sizes);
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

    // Wait before performing the next action. If allocation happened during this wait,
    // we exit sooner, to let heuristics re-evaluate new conditions. If we are at idle,
    // back off exponentially.
    if (heap->has_changed()) {
      sleep = ShenandoahControlIntervalMin;
    } else if ((most_recent_wake_time - last_sleep_adjust_time) * 1000 > ShenandoahControlIntervalAdjustPeriod){
      sleep = MIN2<int>(ShenandoahControlIntervalMax, MAX2(1, sleep * 2));
      last_sleep_adjust_time = most_recent_wake_time;
    }
    MonitorLocker ml(&_control_lock, Mutex::_no_safepoint_check_flag);
    const double before_sleep_time = os::elapsedTime();
    ml.wait(sleep);
    most_recent_wake_time = os::elapsedTime();
    // Record a conservative estimate of the longest anticipated sleep duration until we sample again.
    double planned_sleep_interval = MIN2<int>(ShenandoahControlIntervalMax, MAX2(1, sleep * 2)) / 1000.0;
    heuristics->update_should_start_query_times(most_recent_wake_time, planned_sleep_interval);
    if (LogTarget(Debug, gc, thread)::is_enabled()) {
      double elapsed = most_recent_wake_time - before_sleep_time;
      double hiccup = elapsed - double(sleep) / 1000.0;
      if (hiccup > 0.001) {
        log_debug(gc, thread)("Control Thread hiccup time: %.3fs", hiccup);
      }
    }
  }

  // In case any threads are waiting for a cycle to happen, notify them so they observe the shutdown.
  notify_gc_waiters();
  notify_alloc_waiters();
}

void ShenandoahControlThread::service_concurrent_normal_cycle(GCCause::Cause cause) {
  // Normal cycle goes via all concurrent phases. If allocation failure (af) happens during
  // any of the concurrent phases, the allocating thread will block until the concurrent
  // cycle completes.
  //
  // There is also a shortcut through the normal cycle: immediate garbage shortcut. When
  // heuristics say there are no regions to compact, and all the collection comes from immediately
  // reclaimable regions, Shenandoah can skip the evacuation phase.
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  if (check_cancellation()) {
    log_info(gc)("Cancelled");
    return;
  }
  heap->increment_total_collections(false);

  ShenandoahGCSession session(cause, heap->global_generation());

  TraceCollectorStats tcs(heap->monitoring_support()->concurrent_collection_counters());

  ShenandoahConcurrentGC gc(this, heap->global_generation(), false);
  if (gc.collect(cause)) {
    heap->notify_gc_progress();
    heap->global_generation()->heuristics()->record_concurrent_completion();
    heap->shenandoah_policy()->record_success_concurrent(false, gc.abbreviated());
    heap->log_heap_status("At end of GC");
  } else {
    assert(heap->cancelled_gc(), "Must have been cancelled");
    check_cancellation();
    heap->log_heap_status("At end of cancelled GC");
  }
}

bool ShenandoahControlThread::check_cancellation() {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  if (heap->cancelled_gc()) {
    if (heap->cancelled_cause() == GCCause::_shenandoah_stop_vm) {
      return true;
    }

    fatal("Unexpected reason for cancellation: %s", GCCause::to_string(heap->cancelled_cause()));
  }
  return false;
}

void ShenandoahControlThread::stop_service() {
  ShenandoahHeap::heap()->cancel_gc(GCCause::_shenandoah_stop_vm);
}

void ShenandoahControlThread::service_stw_full_cycle(GCCause::Cause cause) {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  ShenandoahGCSession session(cause, heap->global_generation());

  heap->increment_total_collections(true);

  ShenandoahFullGC gc;
  gc.collect(cause);
}

void ShenandoahControlThread::notify_control_thread(GCCause::Cause cause, ShenandoahGeneration* ignored) {
  // Although setting gc request is under _controller_lock, the read side (run_service())
  // does not take the lock. We need to enforce following order, so that read side sees
  // latest requested gc cause when the flag is set.
  MonitorLocker controller(&_control_lock, Mutex::_no_safepoint_check_flag);
  _requested_gc_cause = cause;
  _gc_requested.set();
  controller.notify();
}

ShenandoahGeneration* ShenandoahControlThread::alloc_failure_generation() {
  return ShenandoahHeap::heap()->global_generation();
}
