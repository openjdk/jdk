/*
 * Copyright (c) 2021, 2022, Red Hat, Inc. All rights reserved.
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

#include "gc/shared/barrierSetNMethod.hpp"
#include "gc/shared/collectorCounters.hpp"
#include "gc/shared/continuationGCSupport.inline.hpp"
#include "gc/shenandoah/shenandoahBreakpoint.hpp"
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahConcurrentGC.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"
#include "gc/shenandoah/shenandoahLock.hpp"
#include "gc/shenandoah/shenandoahMark.inline.hpp"
#include "gc/shenandoah/shenandoahMonitoringSupport.hpp"
#include "gc/shenandoah/shenandoahOopClosures.inline.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shenandoah/shenandoahReferenceProcessor.hpp"
#include "gc/shenandoah/shenandoahRootProcessor.inline.hpp"
#include "gc/shenandoah/shenandoahStackWatermark.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "gc/shenandoah/shenandoahVerifier.hpp"
#include "gc/shenandoah/shenandoahVMOperations.hpp"
#include "gc/shenandoah/shenandoahWorkGroup.hpp"
#include "gc/shenandoah/shenandoahWorkerPolicy.hpp"
#include "memory/allocation.hpp"
#include "prims/jvmtiTagMap.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/events.hpp"

// Breakpoint support
class ShenandoahBreakpointGCScope : public StackObj {
private:
  const GCCause::Cause _cause;
public:
  ShenandoahBreakpointGCScope(GCCause::Cause cause) : _cause(cause) {
    if (cause == GCCause::_wb_breakpoint) {
      ShenandoahBreakpoint::start_gc();
      ShenandoahBreakpoint::at_before_gc();
    }
  }

  ~ShenandoahBreakpointGCScope() {
    if (_cause == GCCause::_wb_breakpoint) {
      ShenandoahBreakpoint::at_after_gc();
    }
  }
};

class ShenandoahBreakpointMarkScope : public StackObj {
private:
  const GCCause::Cause _cause;
public:
  ShenandoahBreakpointMarkScope(GCCause::Cause cause) : _cause(cause) {
    if (_cause == GCCause::_wb_breakpoint) {
      ShenandoahBreakpoint::at_after_marking_started();
    }
  }

  ~ShenandoahBreakpointMarkScope() {
    if (_cause == GCCause::_wb_breakpoint) {
      ShenandoahBreakpoint::at_before_marking_completed();
    }
  }
};

ShenandoahConcurrentGC::ShenandoahConcurrentGC(ShenandoahGeneration* generation, bool do_old_gc_bootstrap) :
  _mark(generation),
  _degen_point(ShenandoahDegenPoint::_degenerated_unset),
  _abbreviated(false),
  _do_old_gc_bootstrap(do_old_gc_bootstrap),
  _generation(generation) {
}

ShenandoahGC::ShenandoahDegenPoint ShenandoahConcurrentGC::degen_point() const {
  return _degen_point;
}

bool ShenandoahConcurrentGC::collect(GCCause::Cause cause) {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  heap->start_conc_gc();

  ShenandoahBreakpointGCScope breakpoint_gc_scope(cause);

  // Reset for upcoming marking
  entry_reset();

  // Start initial mark under STW
  vmop_entry_init_mark();

  {
    ShenandoahBreakpointMarkScope breakpoint_mark_scope(cause);

    // Reset task queue stats here, rather than in mark_concurrent_roots,
    // because remembered set scan will `push` oops into the queues and
    // resetting after this happens will lose those counts.
    TASKQUEUE_STATS_ONLY(_mark.task_queues()->reset_taskqueue_stats());

    // Concurrent remembered set scanning
    entry_scan_remembered_set();
    // TODO: When RS scanning yields, we will need a check_cancellation_and_abort() degeneration point here.

    // Concurrent mark roots
    entry_mark_roots();
    if (check_cancellation_and_abort(ShenandoahDegenPoint::_degenerated_roots)) {
      return false;
    }

    // Continue concurrent mark
    entry_mark();
    if (check_cancellation_and_abort(ShenandoahDegenPoint::_degenerated_mark)) {
      return false;
    }
  }

  // Complete marking under STW, and start evacuation
  vmop_entry_final_mark();

  // If GC was cancelled before final mark, then the safepoint operation will do nothing
  // and the concurrent mark will still be in progress. In this case it is safe to resume
  // the degenerated cycle from the marking phase. On the other hand, if the GC is cancelled
  // after final mark (but before this check), then the final mark safepoint operation
  // will have finished the mark (setting concurrent mark in progress to false). Final mark
  // will also have setup state (in concurrent stack processing) that will not be safe to
  // resume from the marking phase in the degenerated cycle. That is, if the cancellation
  // occurred after final mark, we must resume the degenerated cycle after the marking phase.
  if (_generation->is_concurrent_mark_in_progress() && check_cancellation_and_abort(ShenandoahDegenPoint::_degenerated_mark)) {
    assert(!heap->is_concurrent_weak_root_in_progress(), "Weak roots should not be in progress when concurrent mark is in progress");
    return false;
  }

  // Concurrent stack processing
  if (heap->is_evacuation_in_progress()) {
    entry_thread_roots();
  }

  // Process weak roots that might still point to regions that would be broken by cleanup
  if (heap->is_concurrent_weak_root_in_progress()) {
    entry_weak_refs();
    entry_weak_roots();
  }

  // Final mark might have reclaimed some immediate garbage, kick cleanup to reclaim
  // the space. This would be the last action if there is nothing to evacuate.  Note that
  // we will not age young-gen objects in the case that we skip evacuation.
  entry_cleanup_early();

  {
    ShenandoahHeapLocker locker(heap->lock());
    heap->free_set()->log_status();
  }

  // Perform concurrent class unloading
  if (heap->unload_classes() &&
      heap->is_concurrent_weak_root_in_progress()) {
    entry_class_unloading();
  }

  // Processing strong roots
  // This may be skipped if there is nothing to update/evacuate.
  // If so, strong_root_in_progress would be unset.
  if (heap->is_concurrent_strong_root_in_progress()) {
    entry_strong_roots();
  }

  // Continue the cycle with evacuation and optional update-refs.
  // This may be skipped if there is nothing to evacuate.
  // If so, evac_in_progress would be unset by collection set preparation code.
  if (heap->is_evacuation_in_progress()) {
    // Concurrently evacuate
    entry_evacuate();
    if (check_cancellation_and_abort(ShenandoahDegenPoint::_degenerated_evac)) {
      return false;
    }
  }

  if (heap->has_forwarded_objects()) {
    // Perform update-refs phase.
    vmop_entry_init_updaterefs();
    entry_updaterefs();
    if (check_cancellation_and_abort(ShenandoahDegenPoint::_degenerated_updaterefs)) {
      return false;
    }

    // Concurrent update thread roots
    entry_update_thread_roots();
    if (check_cancellation_and_abort(ShenandoahDegenPoint::_degenerated_updaterefs)) {
      return false;
    }

    vmop_entry_final_updaterefs();

    // Update references freed up collection set, kick the cleanup to reclaim the space.
    entry_cleanup_complete();
  } else {
    // We chose not to evacuate because we found sufficient immediate garbage. Note that we
    // do not check for cancellation here because, at this point, the cycle is effectively
    // complete. If the cycle has been cancelled here, the control thread will detect it
    // on its next iteration and run a degenerated young cycle.
    vmop_entry_final_roots();
    _abbreviated = true;
  }

  // We defer generation resizing actions until after cset regions have been recycled.  We do this even following an
  // abbreviated cycle.
  if (heap->mode()->is_generational()) {
    bool success;
    size_t region_xfer;
    const char* region_destination;
    ShenandoahYoungGeneration* young_gen = heap->young_generation();
    ShenandoahGeneration* old_gen = heap->old_generation();
    {
      ShenandoahHeapLocker locker(heap->lock());

      size_t old_region_surplus = heap->get_old_region_surplus();
      size_t old_region_deficit = heap->get_old_region_deficit();
      if (old_region_surplus) {
        success = heap->generation_sizer()->transfer_to_young(old_region_surplus);
        region_destination = "young";
        region_xfer = old_region_surplus;
      } else if (old_region_deficit) {
        success = heap->generation_sizer()->transfer_to_old(old_region_deficit);
        region_destination = "old";
        region_xfer = old_region_deficit;
        if (!success) {
          ((ShenandoahOldHeuristics *) old_gen->heuristics())->trigger_cannot_expand();
        }
      } else {
        region_destination = "none";
        region_xfer = 0;
        success = true;
      }
      heap->set_old_region_surplus(0);
      heap->set_old_region_deficit(0);
      heap->set_young_evac_reserve(0);
      heap->set_old_evac_reserve(0);
      heap->set_promoted_reserve(0);
    }

    // Report outside the heap lock
    size_t young_available = young_gen->available();
    size_t old_available = old_gen->available();
    log_info(gc, ergo)("After cleanup, %s " SIZE_FORMAT " regions to %s to prepare for next gc, old available: "
                       SIZE_FORMAT "%s, young_available: " SIZE_FORMAT "%s",
                       success? "successfully transferred": "failed to transfer", region_xfer, region_destination,
                       byte_size_in_proper_unit(old_available), proper_unit_for_byte_size(old_available),
                       byte_size_in_proper_unit(young_available), proper_unit_for_byte_size(young_available));
  }
  return true;
}

void ShenandoahConcurrentGC::vmop_entry_init_mark() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  TraceCollectorStats tcs(heap->monitoring_support()->stw_collection_counters());
  ShenandoahTimingsTracker timing(ShenandoahPhaseTimings::init_mark_gross);

  heap->try_inject_alloc_failure();
  VM_ShenandoahInitMark op(this);
  VMThread::execute(&op); // jump to entry_init_mark() under safepoint
}

void ShenandoahConcurrentGC::vmop_entry_final_mark() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  TraceCollectorStats tcs(heap->monitoring_support()->stw_collection_counters());
  ShenandoahTimingsTracker timing(ShenandoahPhaseTimings::final_mark_gross);

  heap->try_inject_alloc_failure();
  VM_ShenandoahFinalMarkStartEvac op(this);
  VMThread::execute(&op); // jump to entry_final_mark under safepoint
}

void ShenandoahConcurrentGC::vmop_entry_init_updaterefs() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  TraceCollectorStats tcs(heap->monitoring_support()->stw_collection_counters());
  ShenandoahTimingsTracker timing(ShenandoahPhaseTimings::init_update_refs_gross);

  heap->try_inject_alloc_failure();
  VM_ShenandoahInitUpdateRefs op(this);
  VMThread::execute(&op);
}

void ShenandoahConcurrentGC::vmop_entry_final_updaterefs() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  TraceCollectorStats tcs(heap->monitoring_support()->stw_collection_counters());
  ShenandoahTimingsTracker timing(ShenandoahPhaseTimings::final_update_refs_gross);

  heap->try_inject_alloc_failure();
  VM_ShenandoahFinalUpdateRefs op(this);
  VMThread::execute(&op);
}

void ShenandoahConcurrentGC::vmop_entry_final_roots() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  TraceCollectorStats tcs(heap->monitoring_support()->stw_collection_counters());
  ShenandoahTimingsTracker timing(ShenandoahPhaseTimings::final_roots_gross);

  // This phase does not use workers, no need for setup
  heap->try_inject_alloc_failure();
  VM_ShenandoahFinalRoots op(this);
  VMThread::execute(&op);
}

void ShenandoahConcurrentGC::entry_init_mark() {
  const char* msg = init_mark_event_message();
  ShenandoahPausePhase gc_phase(msg, ShenandoahPhaseTimings::init_mark);
  EventMark em("%s", msg);

  ShenandoahWorkerScope scope(ShenandoahHeap::heap()->workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_init_marking(),
                              "init marking");

  op_init_mark();
}

void ShenandoahConcurrentGC::entry_final_mark() {
  const char* msg = final_mark_event_message();
  ShenandoahPausePhase gc_phase(msg, ShenandoahPhaseTimings::final_mark);
  EventMark em("%s", msg);

  ShenandoahWorkerScope scope(ShenandoahHeap::heap()->workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_final_marking(),
                              "final marking");

  op_final_mark();
}

void ShenandoahConcurrentGC::entry_init_updaterefs() {
  static const char* msg = "Pause Init Update Refs";
  ShenandoahPausePhase gc_phase(msg, ShenandoahPhaseTimings::init_update_refs);
  EventMark em("%s", msg);

  // No workers used in this phase, no setup required
  op_init_updaterefs();
}

void ShenandoahConcurrentGC::entry_final_updaterefs() {
  static const char* msg = "Pause Final Update Refs";
  ShenandoahPausePhase gc_phase(msg, ShenandoahPhaseTimings::final_update_refs);
  EventMark em("%s", msg);

  ShenandoahWorkerScope scope(ShenandoahHeap::heap()->workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_final_update_ref(),
                              "final reference update");

  op_final_updaterefs();
}

void ShenandoahConcurrentGC::entry_final_roots() {
  static const char* msg = "Pause Final Roots";
  ShenandoahPausePhase gc_phase(msg, ShenandoahPhaseTimings::final_roots);
  EventMark em("%s", msg);

  op_final_roots();
}

void ShenandoahConcurrentGC::entry_reset() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  TraceCollectorStats tcs(heap->monitoring_support()->concurrent_collection_counters());
  static const char* msg = "Concurrent reset";
  ShenandoahConcurrentPhase gc_phase(msg, ShenandoahPhaseTimings::conc_reset);
  EventMark em("%s", msg);

  ShenandoahWorkerScope scope(heap->workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_conc_reset(),
                              "concurrent reset");

  heap->try_inject_alloc_failure();
  op_reset();
}

void ShenandoahConcurrentGC::entry_scan_remembered_set() {
  if (_generation->is_young()) {
    ShenandoahHeap* const heap = ShenandoahHeap::heap();
    TraceCollectorStats tcs(heap->monitoring_support()->concurrent_collection_counters());
    const char* msg = "Concurrent remembered set scanning";
    ShenandoahConcurrentPhase gc_phase(msg, ShenandoahPhaseTimings::init_scan_rset);
    EventMark em("%s", msg);

    ShenandoahWorkerScope scope(heap->workers(),
                                ShenandoahWorkerPolicy::calc_workers_for_rs_scanning(),
                                msg);

    heap->try_inject_alloc_failure();
    _generation->scan_remembered_set(true /* is_concurrent */);
  }
}

void ShenandoahConcurrentGC::entry_mark_roots() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  TraceCollectorStats tcs(heap->monitoring_support()->concurrent_collection_counters());
  const char* msg = "Concurrent marking roots";
  ShenandoahConcurrentPhase gc_phase(msg, ShenandoahPhaseTimings::conc_mark_roots);
  EventMark em("%s", msg);

  ShenandoahWorkerScope scope(heap->workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_conc_marking(),
                              "concurrent marking roots");

  heap->try_inject_alloc_failure();
  op_mark_roots();
}

void ShenandoahConcurrentGC::entry_mark() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  TraceCollectorStats tcs(heap->monitoring_support()->concurrent_collection_counters());
  const char* msg = conc_mark_event_message();
  ShenandoahConcurrentPhase gc_phase(msg, ShenandoahPhaseTimings::conc_mark);
  EventMark em("%s", msg);

  ShenandoahWorkerScope scope(heap->workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_conc_marking(),
                              "concurrent marking");

  heap->try_inject_alloc_failure();
  op_mark();
}

void ShenandoahConcurrentGC::entry_thread_roots() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  static const char* msg = "Concurrent thread roots";
  ShenandoahConcurrentPhase gc_phase(msg, ShenandoahPhaseTimings::conc_thread_roots);
  EventMark em("%s", msg);

  ShenandoahWorkerScope scope(heap->workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_conc_root_processing(),
                              msg);

  heap->try_inject_alloc_failure();
  op_thread_roots();
}

void ShenandoahConcurrentGC::entry_weak_refs() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  static const char* msg = "Concurrent weak references";
  ShenandoahConcurrentPhase gc_phase(msg, ShenandoahPhaseTimings::conc_weak_refs);
  EventMark em("%s", msg);

  ShenandoahWorkerScope scope(heap->workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_conc_refs_processing(),
                              "concurrent weak references");

  heap->try_inject_alloc_failure();
  op_weak_refs();
}

void ShenandoahConcurrentGC::entry_weak_roots() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  TraceCollectorStats tcs(heap->monitoring_support()->concurrent_collection_counters());
  static const char* msg = "Concurrent weak roots";
  ShenandoahConcurrentPhase gc_phase(msg, ShenandoahPhaseTimings::conc_weak_roots);
  EventMark em("%s", msg);

  ShenandoahWorkerScope scope(heap->workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_conc_root_processing(),
                              "concurrent weak root");

  heap->try_inject_alloc_failure();
  op_weak_roots();
}

void ShenandoahConcurrentGC::entry_class_unloading() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  TraceCollectorStats tcs(heap->monitoring_support()->concurrent_collection_counters());
  static const char* msg = "Concurrent class unloading";
  ShenandoahConcurrentPhase gc_phase(msg, ShenandoahPhaseTimings::conc_class_unload);
  EventMark em("%s", msg);

  ShenandoahWorkerScope scope(heap->workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_conc_root_processing(),
                              "concurrent class unloading");

  heap->try_inject_alloc_failure();
  op_class_unloading();
}

void ShenandoahConcurrentGC::entry_strong_roots() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  TraceCollectorStats tcs(heap->monitoring_support()->concurrent_collection_counters());
  static const char* msg = "Concurrent strong roots";
  ShenandoahConcurrentPhase gc_phase(msg, ShenandoahPhaseTimings::conc_strong_roots);
  EventMark em("%s", msg);

  ShenandoahGCWorkerPhase worker_phase(ShenandoahPhaseTimings::conc_strong_roots);

  ShenandoahWorkerScope scope(heap->workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_conc_root_processing(),
                              "concurrent strong root");

  heap->try_inject_alloc_failure();
  op_strong_roots();
}

void ShenandoahConcurrentGC::entry_cleanup_early() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  TraceCollectorStats tcs(heap->monitoring_support()->concurrent_collection_counters());
  static const char* msg = "Concurrent cleanup";
  ShenandoahConcurrentPhase gc_phase(msg, ShenandoahPhaseTimings::conc_cleanup_early, true /* log_heap_usage */);
  EventMark em("%s", msg);

  // This phase does not use workers, no need for setup
  heap->try_inject_alloc_failure();
  op_cleanup_early();
}

void ShenandoahConcurrentGC::entry_evacuate() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  TraceCollectorStats tcs(heap->monitoring_support()->concurrent_collection_counters());

  static const char* msg = "Concurrent evacuation";
  ShenandoahConcurrentPhase gc_phase(msg, ShenandoahPhaseTimings::conc_evac);
  EventMark em("%s", msg);

  ShenandoahWorkerScope scope(heap->workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_conc_evac(),
                              "concurrent evacuation");

  heap->try_inject_alloc_failure();
  op_evacuate();
}

void ShenandoahConcurrentGC::entry_update_thread_roots() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  TraceCollectorStats tcs(heap->monitoring_support()->concurrent_collection_counters());

  static const char* msg = "Concurrent update thread roots";
  ShenandoahConcurrentPhase gc_phase(msg, ShenandoahPhaseTimings::conc_update_thread_roots);
  EventMark em("%s", msg);

  // No workers used in this phase, no setup required
  heap->try_inject_alloc_failure();
  op_update_thread_roots();
}

void ShenandoahConcurrentGC::entry_updaterefs() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  TraceCollectorStats tcs(heap->monitoring_support()->concurrent_collection_counters());
  static const char* msg = "Concurrent update references";
  ShenandoahConcurrentPhase gc_phase(msg, ShenandoahPhaseTimings::conc_update_refs);
  EventMark em("%s", msg);

  ShenandoahWorkerScope scope(heap->workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_conc_update_ref(),
                              "concurrent reference update");

  heap->try_inject_alloc_failure();
  op_updaterefs();
}

void ShenandoahConcurrentGC::entry_cleanup_complete() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  TraceCollectorStats tcs(heap->monitoring_support()->concurrent_collection_counters());
  static const char* msg = "Concurrent cleanup";
  ShenandoahConcurrentPhase gc_phase(msg, ShenandoahPhaseTimings::conc_cleanup_complete, true /* log_heap_usage */);
  EventMark em("%s", msg);

  // This phase does not use workers, no need for setup
  heap->try_inject_alloc_failure();
  op_cleanup_complete();
}

void ShenandoahConcurrentGC::op_reset() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  if (ShenandoahPacing) {
    heap->pacer()->setup_for_reset();
  }
  _generation->prepare_gc();
}

class ShenandoahInitMarkUpdateRegionStateClosure : public ShenandoahHeapRegionClosure {
private:
  ShenandoahMarkingContext* const _ctx;
public:
  ShenandoahInitMarkUpdateRegionStateClosure() : _ctx(ShenandoahHeap::heap()->marking_context()) {}

  void heap_region_do(ShenandoahHeapRegion* r) {
    assert(!r->has_live(), "Region " SIZE_FORMAT " should have no live data", r->index());
    if (r->is_active()) {
      // Check if region needs updating its TAMS. We have updated it already during concurrent
      // reset, so it is very likely we don't need to do another write here.  Since most regions
      // are not "active", this path is relatively rare.
      if (_ctx->top_at_mark_start(r) != r->top()) {
        _ctx->capture_top_at_mark_start(r);
      }
    } else {
      assert(_ctx->top_at_mark_start(r) == r->top(),
             "Region " SIZE_FORMAT " should already have correct TAMS", r->index());
    }
  }

  bool is_thread_safe() { return true; }
};

void ShenandoahConcurrentGC::start_mark() {
  _mark.start_mark();
}

void ShenandoahConcurrentGC::op_init_mark() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Should be at safepoint");
  assert(Thread::current()->is_VM_thread(), "can only do this in VMThread");

  assert(_generation->is_bitmap_clear(), "need clear marking bitmap");
  assert(!_generation->is_mark_complete(), "should not be complete");
  assert(!heap->has_forwarded_objects(), "No forwarded objects on this path");


  if (heap->mode()->is_generational()) {
    if (_generation->is_young() || (_generation->is_global() && ShenandoahVerify)) {
      // The current implementation of swap_remembered_set() copies the write-card-table
      // to the read-card-table. The remembered sets are also swapped for GLOBAL collections
      // so that the verifier works with the correct copy of the card table when verifying.
      // TODO: This path should not really depend on ShenandoahVerify.
      ShenandoahGCPhase phase(ShenandoahPhaseTimings::init_swap_rset);
      _generation->swap_remembered_set();
    }

    if (_generation->is_global()) {
      heap->cancel_old_gc();
    } else if (heap->is_concurrent_old_mark_in_progress()) {
      // Purge the SATB buffers, transferring any valid, old pointers to the
      // old generation mark queue. Any pointers in a young region will be
      // abandoned.
      ShenandoahGCPhase phase(ShenandoahPhaseTimings::init_transfer_satb);
      heap->transfer_old_pointers_from_satb();
    }
  }

  if (ShenandoahVerify) {
    heap->verifier()->verify_before_concmark();
  }

  if (VerifyBeforeGC) {
    Universe::verify();
  }

  _generation->set_concurrent_mark_in_progress(true);

  start_mark();

  if (_do_old_gc_bootstrap) {
    // Update region state for both young and old regions
    // TODO: We should be able to pull this out of the safepoint for the bootstrap
    // cycle. The top of an old region will only move when a GC cycle evacuates
    // objects into it. When we start an old cycle, we know that nothing can touch
    // the top of old regions.
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::init_update_region_states);
    ShenandoahInitMarkUpdateRegionStateClosure cl;
    heap->parallel_heap_region_iterate(&cl);
  } else {
    // Update region state for only young regions
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::init_update_region_states);
    ShenandoahInitMarkUpdateRegionStateClosure cl;
    _generation->parallel_heap_region_iterate(&cl);
  }

  // Weak reference processing
  ShenandoahReferenceProcessor* rp = _generation->ref_processor();
  rp->reset_thread_locals();
  rp->set_soft_reference_policy(heap->soft_ref_policy()->should_clear_all_soft_refs());

  // Make above changes visible to worker threads
  OrderAccess::fence();

  // Arm nmethods for concurrent mark
  ShenandoahCodeRoots::arm_nmethods_for_mark();

  ShenandoahStackWatermark::change_epoch_id();
  if (ShenandoahPacing) {
    heap->pacer()->setup_for_mark();
  }
}

void ShenandoahConcurrentGC::op_mark_roots() {
  _mark.mark_concurrent_roots();
}

void ShenandoahConcurrentGC::op_mark() {
  _mark.concurrent_mark();
}

void ShenandoahConcurrentGC::op_final_mark() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Should be at safepoint");
  assert(!heap->has_forwarded_objects(), "No forwarded objects on this path");

  if (ShenandoahVerify) {
    heap->verifier()->verify_roots_no_forwarded();
  }

  if (!heap->cancelled_gc()) {
    _mark.finish_mark();
    assert(!heap->cancelled_gc(), "STW mark cannot OOM");

    // Notify JVMTI that the tagmap table will need cleaning.
    JvmtiTagMap::set_needs_cleaning();

    // The collection set is chosen by prepare_regions_and_collection_set().
    //
    // TODO: Under severe memory overload conditions that can be checked here, we may want to limit
    // the inclusion of old-gen candidates within the collection set.  This would allow us to prioritize efforts on
    // evacuating young-gen,  This remediation is most appropriate when old-gen availability is very high (so there
    // are negligible negative impacts from delaying completion of old-gen evacuation) and when young-gen collections
    // are "under duress" (as signalled by very low availability of memory within young-gen, indicating that/ young-gen
    // collections are not triggering frequently enough).
    _generation->prepare_regions_and_collection_set(true /*concurrent*/);

    // Upon return from prepare_regions_and_collection_set(), certain parameters have been established to govern the
    // evacuation efforts that are about to begin.  In particular:
    //
    // heap->get_promoted_reserve() represents the amount of memory within old-gen's available memory that has
    //   been set aside to hold objects promoted from young-gen memory.  This represents an estimated percentage
    //   of the live young-gen memory within the collection set.  If there is more data ready to be promoted than
    //   can fit within this reserve, the promotion of some objects will be deferred until a subsequent evacuation
    //   pass.
    //
    // heap->get_old_evac_reserve() represents the amount of memory within old-gen's available memory that has been
    //  set aside to hold objects evacuated from the old-gen collection set.
    //
    // heap->get_young_evac_reserve() represents the amount of memory within young-gen's available memory that has
    //  been set aside to hold objects evacuated from the young-gen collection set.  Conservatively, this value
    //  equals the entire amount of live young-gen memory within the collection set, even though some of this memory
    //  will likely be promoted.

    // Has to be done after cset selection
    heap->prepare_concurrent_roots();

    if (heap->mode()->is_generational()) {
      size_t humongous_regions_promoted = heap->get_promotable_humongous_regions();
      size_t regular_regions_promoted_in_place = heap->get_regular_regions_promoted_in_place();
      if (!heap->collection_set()->is_empty() || (humongous_regions_promoted + regular_regions_promoted_in_place > 0)) {
        // Even if the collection set is empty, we need to do evacuation if there are regions to be promoted in place.
        // Concurrent evacuation takes responsibility for registering objects and setting the remembered set cards to dirty.

        LogTarget(Debug, gc, cset) lt;
        if (lt.is_enabled()) {
          ResourceMark rm;
          LogStream ls(lt);
          heap->collection_set()->print_on(&ls);
        }

        if (ShenandoahVerify) {
          heap->verifier()->verify_before_evacuation();
        }

        heap->set_evacuation_in_progress(true);

        // Verify before arming for concurrent processing.
        // Otherwise, verification can trigger stack processing.
        if (ShenandoahVerify) {
          heap->verifier()->verify_during_evacuation();
        }

        // Generational mode may promote objects in place during the evacuation phase.
        // If that is the only reason we are evacuating, we don't need to update references
        // and there will be no forwarded objects on the heap.
        heap->set_has_forwarded_objects(!heap->collection_set()->is_empty());

        // Arm nmethods/stack for concurrent processing
        if (!heap->collection_set()->is_empty()) {
          // Iff objects will be evaluated, arm the nmethod barriers. These will be disarmed
          // under the same condition (established in prepare_concurrent_roots) after strong
          // root evacuation has completed (see op_strong_roots).
          ShenandoahCodeRoots::arm_nmethods_for_evac();
          ShenandoahStackWatermark::change_epoch_id();
        }

        if (ShenandoahPacing) {
          heap->pacer()->setup_for_evac();
        }
      } else {
        if (ShenandoahVerify) {
          heap->verifier()->verify_after_concmark();
        }

        if (VerifyAfterGC) {
          Universe::verify();
        }
      }
    } else {
      // Not is_generational()
      if (!heap->collection_set()->is_empty()) {
        LogTarget(Info, gc, ergo) lt;
        if (lt.is_enabled()) {
          ResourceMark rm;
          LogStream ls(lt);
          heap->collection_set()->print_on(&ls);
        }

        if (ShenandoahVerify) {
          heap->verifier()->verify_before_evacuation();
        }

        heap->set_evacuation_in_progress(true);

        // Verify before arming for concurrent processing.
        // Otherwise, verification can trigger stack processing.
        if (ShenandoahVerify) {
          heap->verifier()->verify_during_evacuation();
        }

        // From here on, we need to update references.
        heap->set_has_forwarded_objects(true);

        // Arm nmethods/stack for concurrent processing
        ShenandoahCodeRoots::arm_nmethods_for_evac();
        ShenandoahStackWatermark::change_epoch_id();

        if (ShenandoahPacing) {
          heap->pacer()->setup_for_evac();
        }
      } else {
        if (ShenandoahVerify) {
          heap->verifier()->verify_after_concmark();
        }

        if (VerifyAfterGC) {
          Universe::verify();
        }
      }
    }
  }
}

class ShenandoahConcurrentEvacThreadClosure : public ThreadClosure {
private:
  OopClosure* const _oops;

public:
  ShenandoahConcurrentEvacThreadClosure(OopClosure* oops);
  void do_thread(Thread* thread);
};

ShenandoahConcurrentEvacThreadClosure::ShenandoahConcurrentEvacThreadClosure(OopClosure* oops) :
  _oops(oops) {
}

void ShenandoahConcurrentEvacThreadClosure::do_thread(Thread* thread) {
  JavaThread* const jt = JavaThread::cast(thread);
  StackWatermarkSet::finish_processing(jt, _oops, StackWatermarkKind::gc);
  ShenandoahThreadLocalData::enable_plab_promotions(thread);
}

class ShenandoahConcurrentEvacUpdateThreadTask : public WorkerTask {
private:
  ShenandoahJavaThreadsIterator _java_threads;

public:
  ShenandoahConcurrentEvacUpdateThreadTask(uint n_workers) :
    WorkerTask("Shenandoah Evacuate/Update Concurrent Thread Roots"),
    _java_threads(ShenandoahPhaseTimings::conc_thread_roots, n_workers) {
  }

  void work(uint worker_id) {
    Thread* worker_thread = Thread::current();
    ShenandoahThreadLocalData::enable_plab_promotions(worker_thread);

    // ShenandoahEvacOOMScope has to be setup by ShenandoahContextEvacuateUpdateRootsClosure.
    // Otherwise, may deadlock with watermark lock
    ShenandoahContextEvacuateUpdateRootsClosure oops_cl;
    ShenandoahConcurrentEvacThreadClosure thr_cl(&oops_cl);
    _java_threads.threads_do(&thr_cl, worker_id);
  }
};

void ShenandoahConcurrentGC::op_thread_roots() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  assert(heap->is_evacuation_in_progress(), "Checked by caller");
  ShenandoahGCWorkerPhase worker_phase(ShenandoahPhaseTimings::conc_thread_roots);
  ShenandoahConcurrentEvacUpdateThreadTask task(heap->workers()->active_workers());
  heap->workers()->run_task(&task);
}

void ShenandoahConcurrentGC::op_weak_refs() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  assert(heap->is_concurrent_weak_root_in_progress(), "Only during this phase");
  // Concurrent weak refs processing
  ShenandoahGCWorkerPhase worker_phase(ShenandoahPhaseTimings::conc_weak_refs);
  if (heap->gc_cause() == GCCause::_wb_breakpoint) {
    ShenandoahBreakpoint::at_after_reference_processing_started();
  }
  _generation->ref_processor()->process_references(ShenandoahPhaseTimings::conc_weak_refs, heap->workers(), true /* concurrent */);
}

class ShenandoahEvacUpdateCleanupOopStorageRootsClosure : public BasicOopIterateClosure {
private:
  ShenandoahHeap* const _heap;
  ShenandoahMarkingContext* const _mark_context;
  bool  _evac_in_progress;
  Thread* const _thread;

public:
  ShenandoahEvacUpdateCleanupOopStorageRootsClosure();
  void do_oop(oop* p);
  void do_oop(narrowOop* p);
};

ShenandoahEvacUpdateCleanupOopStorageRootsClosure::ShenandoahEvacUpdateCleanupOopStorageRootsClosure() :
  _heap(ShenandoahHeap::heap()),
  _mark_context(ShenandoahHeap::heap()->marking_context()),
  _evac_in_progress(ShenandoahHeap::heap()->is_evacuation_in_progress()),
  _thread(Thread::current()) {
}

void ShenandoahEvacUpdateCleanupOopStorageRootsClosure::do_oop(oop* p) {
  const oop obj = RawAccess<>::oop_load(p);
  if (!CompressedOops::is_null(obj)) {
    if (!_mark_context->is_marked(obj)) {
      if (_heap->is_in_active_generation(obj)) {
        // TODO: This worries me. Here we are asserting that an unmarked from-space object is 'correct'.
        // Normally, I would call this a bogus assert, but there seems to be a legitimate use-case for
        // accessing from-space objects during class unloading. However, the from-space object may have
        // been "filled". We've made no effort to prevent old generation classes being unloaded by young
        // gen (and vice-versa).
        shenandoah_assert_correct(p, obj);
        ShenandoahHeap::atomic_clear_oop(p, obj);
      }
    } else if (_evac_in_progress && _heap->in_collection_set(obj)) {
      oop resolved = ShenandoahBarrierSet::resolve_forwarded_not_null(obj);
      if (resolved == obj) {
        resolved = _heap->evacuate_object(obj, _thread);
      }
      ShenandoahHeap::atomic_update_oop(resolved, p, obj);
      assert(_heap->cancelled_gc() ||
             _mark_context->is_marked(resolved) && !_heap->in_collection_set(resolved),
             "Sanity");
    }
  }
}

void ShenandoahEvacUpdateCleanupOopStorageRootsClosure::do_oop(narrowOop* p) {
  ShouldNotReachHere();
}

class ShenandoahIsCLDAliveClosure : public CLDClosure {
public:
  void do_cld(ClassLoaderData* cld) {
    cld->is_alive();
  }
};

class ShenandoahIsNMethodAliveClosure: public NMethodClosure {
public:
  void do_nmethod(nmethod* n) {
    n->is_unloading();
  }
};

// This task not only evacuates/updates marked weak roots, but also "null"
// dead weak roots.
class ShenandoahConcurrentWeakRootsEvacUpdateTask : public WorkerTask {
private:
  ShenandoahVMWeakRoots<true /*concurrent*/> _vm_roots;

  // Roots related to concurrent class unloading
  ShenandoahClassLoaderDataRoots<true /* concurrent */>
                                             _cld_roots;
  ShenandoahConcurrentNMethodIterator        _nmethod_itr;
  ShenandoahPhaseTimings::Phase              _phase;

public:
  ShenandoahConcurrentWeakRootsEvacUpdateTask(ShenandoahPhaseTimings::Phase phase) :
    WorkerTask("Shenandoah Evacuate/Update Concurrent Weak Roots"),
    _vm_roots(phase),
    _cld_roots(phase, ShenandoahHeap::heap()->workers()->active_workers(), false /*heap iteration*/),
    _nmethod_itr(ShenandoahCodeRoots::table()),
    _phase(phase) {
    if (ShenandoahHeap::heap()->unload_classes()) {
      MutexLocker mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
      _nmethod_itr.nmethods_do_begin();
    }
  }

  ~ShenandoahConcurrentWeakRootsEvacUpdateTask() {
    if (ShenandoahHeap::heap()->unload_classes()) {
      MutexLocker mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
      _nmethod_itr.nmethods_do_end();
    }
    // Notify runtime data structures of potentially dead oops
    _vm_roots.report_num_dead();
  }

  void work(uint worker_id) {
    ShenandoahConcurrentWorkerSession worker_session(worker_id);
    ShenandoahSuspendibleThreadSetJoiner sts_join;
    {
      ShenandoahEvacOOMScope oom;
      // jni_roots and weak_roots are OopStorage backed roots, concurrent iteration
      // may race against OopStorage::release() calls.
      ShenandoahEvacUpdateCleanupOopStorageRootsClosure cl;
      _vm_roots.oops_do(&cl, worker_id);
    }

    // If we are going to perform concurrent class unloading later on, we need to
    // cleanup the weak oops in CLD and determinate nmethod's unloading state, so that we
    // can cleanup immediate garbage sooner.
    if (ShenandoahHeap::heap()->unload_classes()) {
      // Applies ShenandoahIsCLDAlive closure to CLDs, native barrier will either null the
      // CLD's holder or evacuate it.
      {
        ShenandoahIsCLDAliveClosure is_cld_alive;
        _cld_roots.cld_do(&is_cld_alive, worker_id);
      }

      // Applies ShenandoahIsNMethodAliveClosure to registered nmethods.
      // The closure calls nmethod->is_unloading(). The is_unloading
      // state is cached, therefore, during concurrent class unloading phase,
      // we will not touch the metadata of unloading nmethods
      {
        ShenandoahWorkerTimingsTracker timer(_phase, ShenandoahPhaseTimings::CodeCacheRoots, worker_id);
        ShenandoahIsNMethodAliveClosure is_nmethod_alive;
        _nmethod_itr.nmethods_do(&is_nmethod_alive);
      }
    }
  }
};

void ShenandoahConcurrentGC::op_weak_roots() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  assert(heap->is_concurrent_weak_root_in_progress(), "Only during this phase");
  // Concurrent weak root processing
  {
    ShenandoahTimingsTracker t(ShenandoahPhaseTimings::conc_weak_roots_work);
    ShenandoahGCWorkerPhase worker_phase(ShenandoahPhaseTimings::conc_weak_roots_work);
    ShenandoahConcurrentWeakRootsEvacUpdateTask task(ShenandoahPhaseTimings::conc_weak_roots_work);
    heap->workers()->run_task(&task);
  }

  // Perform handshake to flush out dead oops
  {
    ShenandoahTimingsTracker t(ShenandoahPhaseTimings::conc_weak_roots_rendezvous);
    heap->rendezvous_threads();
  }
}

void ShenandoahConcurrentGC::op_class_unloading() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  assert (heap->is_concurrent_weak_root_in_progress() &&
          heap->unload_classes(),
          "Checked by caller");
  heap->do_class_unloading();
}

class ShenandoahEvacUpdateCodeCacheClosure : public NMethodClosure {
private:
  BarrierSetNMethod* const                  _bs;
  ShenandoahEvacuateUpdateMetadataClosure   _cl;

public:
  ShenandoahEvacUpdateCodeCacheClosure() :
    _bs(BarrierSet::barrier_set()->barrier_set_nmethod()),
    _cl() {
  }

  void do_nmethod(nmethod* n) {
    ShenandoahNMethod* data = ShenandoahNMethod::gc_data(n);
    ShenandoahReentrantLocker locker(data->lock());
    // Setup EvacOOM scope below reentrant lock to avoid deadlock with
    // nmethod_entry_barrier
    ShenandoahEvacOOMScope oom;
    data->oops_do(&_cl, true/*fix relocation*/);
    _bs->disarm(n);
  }
};

class ShenandoahConcurrentRootsEvacUpdateTask : public WorkerTask {
private:
  ShenandoahPhaseTimings::Phase                 _phase;
  ShenandoahVMRoots<true /*concurrent*/>        _vm_roots;
  ShenandoahClassLoaderDataRoots<true /*concurrent*/>
                                                _cld_roots;
  ShenandoahConcurrentNMethodIterator           _nmethod_itr;

public:
  ShenandoahConcurrentRootsEvacUpdateTask(ShenandoahPhaseTimings::Phase phase) :
    WorkerTask("Shenandoah Evacuate/Update Concurrent Strong Roots"),
    _phase(phase),
    _vm_roots(phase),
    _cld_roots(phase, ShenandoahHeap::heap()->workers()->active_workers(), false /*heap iteration*/),
    _nmethod_itr(ShenandoahCodeRoots::table()) {
    if (!ShenandoahHeap::heap()->unload_classes()) {
      MutexLocker mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
      _nmethod_itr.nmethods_do_begin();
    }
  }

  ~ShenandoahConcurrentRootsEvacUpdateTask() {
    if (!ShenandoahHeap::heap()->unload_classes()) {
      MutexLocker mu(CodeCache_lock, Mutex::_no_safepoint_check_flag);
      _nmethod_itr.nmethods_do_end();
    }
  }

  void work(uint worker_id) {
    ShenandoahConcurrentWorkerSession worker_session(worker_id);
    {
      ShenandoahEvacOOMScope oom;
      {
        // vm_roots and weak_roots are OopStorage backed roots, concurrent iteration
        // may race against OopStorage::release() calls.
        ShenandoahContextEvacuateUpdateRootsClosure cl;
        _vm_roots.oops_do<ShenandoahContextEvacuateUpdateRootsClosure>(&cl, worker_id);
      }

      {
        ShenandoahEvacuateUpdateMetadataClosure cl;
        CLDToOopClosure clds(&cl, ClassLoaderData::_claim_strong);
        _cld_roots.cld_do(&clds, worker_id);
      }
    }

    // Cannot setup ShenandoahEvacOOMScope here, due to potential deadlock with nmethod_entry_barrier.
    if (!ShenandoahHeap::heap()->unload_classes()) {
      ShenandoahWorkerTimingsTracker timer(_phase, ShenandoahPhaseTimings::CodeCacheRoots, worker_id);
      ShenandoahEvacUpdateCodeCacheClosure cl;
      _nmethod_itr.nmethods_do(&cl);
    }
  }
};

void ShenandoahConcurrentGC::op_strong_roots() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  assert(heap->is_concurrent_strong_root_in_progress(), "Checked by caller");
  ShenandoahConcurrentRootsEvacUpdateTask task(ShenandoahPhaseTimings::conc_strong_roots);
  heap->workers()->run_task(&task);
  heap->set_concurrent_strong_root_in_progress(false);
}

void ShenandoahConcurrentGC::op_cleanup_early() {
  ShenandoahHeap::heap()->free_set()->recycle_trash();
}

void ShenandoahConcurrentGC::op_evacuate() {
  ShenandoahHeap::heap()->evacuate_collection_set(true /*concurrent*/);
}

void ShenandoahConcurrentGC::op_init_updaterefs() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  heap->set_evacuation_in_progress(false);
  heap->set_concurrent_weak_root_in_progress(false);
  heap->prepare_update_heap_references(true /*concurrent*/);
  heap->set_update_refs_in_progress(true);
  if (ShenandoahVerify) {
    heap->verifier()->verify_before_updaterefs();
  }
  if (ShenandoahPacing) {
    heap->pacer()->setup_for_updaterefs();
  }
}

void ShenandoahConcurrentGC::op_updaterefs() {
  ShenandoahHeap::heap()->update_heap_references(true /*concurrent*/);
}

class ShenandoahUpdateThreadClosure : public HandshakeClosure {
private:
  ShenandoahUpdateRefsClosure _cl;
public:
  ShenandoahUpdateThreadClosure();
  void do_thread(Thread* thread);
};

ShenandoahUpdateThreadClosure::ShenandoahUpdateThreadClosure() :
  HandshakeClosure("Shenandoah Update Thread Roots") {
}

void ShenandoahUpdateThreadClosure::do_thread(Thread* thread) {
  if (thread->is_Java_thread()) {
    JavaThread* jt = JavaThread::cast(thread);
    ResourceMark rm;
    jt->oops_do(&_cl, nullptr);
  }
}

void ShenandoahConcurrentGC::op_update_thread_roots() {
  ShenandoahUpdateThreadClosure cl;
  Handshake::execute(&cl);
}

void ShenandoahConcurrentGC::op_final_updaterefs() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "must be at safepoint");
  assert(!heap->_update_refs_iterator.has_next(), "Should have finished update references");

  heap->finish_concurrent_roots();

  // Clear cancelled GC, if set. On cancellation path, the block before would handle
  // everything.
  if (heap->cancelled_gc()) {
    heap->clear_cancelled_gc(true /* clear oom handler */);
  }

  // Has to be done before cset is clear
  if (ShenandoahVerify) {
    heap->verifier()->verify_roots_in_to_space();
  }

  if (heap->mode()->is_generational() && heap->is_concurrent_old_mark_in_progress()) {
    // When the SATB barrier is left on to support concurrent old gen mark, it may pick up writes to
    // objects in the collection set. After those objects are evacuated, the pointers in the
    // SATB are no longer safe. Once we have finished update references, we are guaranteed that
    // no more writes to the collection set are possible.
    //
    // This will transfer any old pointers in _active_ regions from the SATB to the old gen
    // mark queues. All other pointers will be discarded. This would also discard any pointers
    // in old regions that were included in a mixed evacuation. We aren't using the SATB filter
    // methods here because we cannot control when they execute. If the SATB filter runs _after_
    // a region has been recycled, we will not be able to detect the bad pointer.
    //
    // We are not concerned about skipping this step in abbreviated cycles because regions
    // with no live objects cannot have been written to and so cannot have entries in the SATB
    // buffers.
    heap->transfer_old_pointers_from_satb();
  }

  heap->update_heap_region_states(true /*concurrent*/);

  heap->set_update_refs_in_progress(false);
  heap->set_has_forwarded_objects(false);

  // Aging_cycle is only relevant during evacuation cycle for individual objects and during final mark for
  // entire regions.  Both of these relevant operations occur before final update refs.
  heap->set_aging_cycle(false);

  if (ShenandoahVerify) {
    heap->verifier()->verify_after_updaterefs();
  }

  if (VerifyAfterGC) {
    Universe::verify();
  }

  heap->rebuild_free_set(true /*concurrent*/);
}

void ShenandoahConcurrentGC::op_final_roots() {

  ShenandoahHeap *heap = ShenandoahHeap::heap();
  heap->set_concurrent_weak_root_in_progress(false);
  heap->set_evacuation_in_progress(false);

  if (heap->mode()->is_generational()) {
    ShenandoahMarkingContext *ctx = heap->complete_marking_context();

    for (size_t i = 0; i < heap->num_regions(); i++) {
      ShenandoahHeapRegion *r = heap->get_region(i);
      if (r->is_active() && r->is_young()) {
        HeapWord* tams = ctx->top_at_mark_start(r);
        HeapWord* top = r->top();
        if (top > tams) {
          r->reset_age();
        } else if (heap->is_aging_cycle()) {
          r->increment_age();
        }
      }
    }
  }
}

void ShenandoahConcurrentGC::op_cleanup_complete() {
  ShenandoahHeap::heap()->free_set()->recycle_trash();
}

bool ShenandoahConcurrentGC::check_cancellation_and_abort(ShenandoahDegenPoint point) {
  if (ShenandoahHeap::heap()->cancelled_gc()) {
    _degen_point = point;
    return true;
  }
  return false;
}

const char* ShenandoahConcurrentGC::init_mark_event_message() const {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  assert(!heap->has_forwarded_objects(), "Should not have forwarded objects here");
  if (heap->unload_classes()) {
    SHENANDOAH_RETURN_EVENT_MESSAGE(heap, _generation->type(), "Pause Init Mark", " (unload classes)");
  } else {
    SHENANDOAH_RETURN_EVENT_MESSAGE(heap, _generation->type(), "Pause Init Mark", "");
  }
}

const char* ShenandoahConcurrentGC::final_mark_event_message() const {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  assert(!heap->has_forwarded_objects() || heap->is_concurrent_old_mark_in_progress(),
         "Should not have forwarded objects during final mark, unless old gen concurrent mark is running");

  if (heap->unload_classes()) {
    SHENANDOAH_RETURN_EVENT_MESSAGE(heap, _generation->type(), "Pause Final Mark", " (unload classes)");
  } else {
    SHENANDOAH_RETURN_EVENT_MESSAGE(heap, _generation->type(), "Pause Final Mark", "");
  }
}

const char* ShenandoahConcurrentGC::conc_mark_event_message() const {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  assert(!heap->has_forwarded_objects() || heap->is_concurrent_old_mark_in_progress(),
         "Should not have forwarded objects concurrent mark, unless old gen concurrent mark is running");
  if (heap->unload_classes()) {
    SHENANDOAH_RETURN_EVENT_MESSAGE(heap, _generation->type(), "Concurrent marking", " (unload classes)");
  } else {
    SHENANDOAH_RETURN_EVENT_MESSAGE(heap, _generation->type(), "Concurrent marking", "");
  }
}
