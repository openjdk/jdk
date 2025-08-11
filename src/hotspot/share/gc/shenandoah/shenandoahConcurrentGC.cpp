/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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


#include "gc/shared/barrierSetNMethod.hpp"
#include "gc/shared/collectorCounters.hpp"
#include "gc/shared/continuationGCSupport.inline.hpp"
#include "gc/shenandoah/shenandoahBreakpoint.hpp"
#include "gc/shenandoah/shenandoahClosures.inline.hpp"
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahConcurrentGC.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/shenandoahGenerationalHeap.hpp"
#include "gc/shenandoah/shenandoahLock.hpp"
#include "gc/shenandoah/shenandoahMark.inline.hpp"
#include "gc/shenandoah/shenandoahMonitoringSupport.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shenandoah/shenandoahReferenceProcessor.hpp"
#include "gc/shenandoah/shenandoahRootProcessor.inline.hpp"
#include "gc/shenandoah/shenandoahStackWatermark.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "gc/shenandoah/shenandoahVerifier.hpp"
#include "gc/shenandoah/shenandoahVMOperations.hpp"
#include "gc/shenandoah/shenandoahWorkerPolicy.hpp"
#include "gc/shenandoah/shenandoahWorkGroup.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"
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
  _generation(generation),
  _degen_point(ShenandoahDegenPoint::_degenerated_unset),
  _abbreviated(false),
  _do_old_gc_bootstrap(do_old_gc_bootstrap) {
}

ShenandoahGC::ShenandoahDegenPoint ShenandoahConcurrentGC::degen_point() const {
  return _degen_point;
}

void ShenandoahConcurrentGC::entry_concurrent_update_refs_prepare(ShenandoahHeap* const heap) {
  TraceCollectorStats tcs(heap->monitoring_support()->concurrent_collection_counters());
  const char* msg = conc_init_update_refs_event_message();
  ShenandoahConcurrentPhase gc_phase(msg, ShenandoahPhaseTimings::conc_update_refs_prepare);
  EventMark em("%s", msg);

  // Evacuation is complete, retire gc labs and change gc state
  heap->concurrent_prepare_for_update_refs();
}

bool ShenandoahConcurrentGC::collect(GCCause::Cause cause) {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();

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

  // If the GC was cancelled before final mark, nothing happens on the safepoint. We are still
  // in the marking phase and must resume the degenerated cycle from there. If the GC was cancelled
  // after final mark, then we've entered the evacuation phase and must resume the degenerated cycle
  // from that phase.
  if (_generation->is_concurrent_mark_in_progress()) {
    bool cancelled = check_cancellation_and_abort(ShenandoahDegenPoint::_degenerated_mark);
    assert(cancelled, "GC must have been cancelled between concurrent and final mark");
    return false;
  }

  assert(heap->is_concurrent_weak_root_in_progress(), "Must be doing weak roots now");

  // Concurrent stack processing
  if (heap->is_evacuation_in_progress()) {
    entry_thread_roots();
  }

  // Process weak roots that might still point to regions that would be broken by cleanup.
  // We cannot recycle regions because weak roots need to know what is marked in trashed regions.
  entry_weak_refs();
  entry_weak_roots();

  // Perform concurrent class unloading before any regions get recycled. Class unloading may
  // need to inspect unmarked objects in trashed regions.
  if (heap->unload_classes()) {
    entry_class_unloading();
  }

  // Final mark might have reclaimed some immediate garbage, kick cleanup to reclaim
  // the space. This would be the last action if there is nothing to evacuate.  Note that
  // we will not age young-gen objects in the case that we skip evacuation.
  entry_cleanup_early();

  heap->free_set()->log_status_under_lock();

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

    entry_concurrent_update_refs_prepare(heap);

    // Perform update-refs phase.
    if (ShenandoahVerify) {
      vmop_entry_init_update_refs();
    }

    entry_update_refs();
    if (check_cancellation_and_abort(ShenandoahDegenPoint::_degenerated_update_refs)) {
      return false;
    }

    // Concurrent update thread roots
    entry_update_thread_roots();
    if (check_cancellation_and_abort(ShenandoahDegenPoint::_degenerated_update_refs)) {
      return false;
    }

    vmop_entry_final_update_refs();

    // Update references freed up collection set, kick the cleanup to reclaim the space.
    entry_cleanup_complete();
  } else {
    if (!entry_final_roots()) {
      assert(_degen_point != _degenerated_unset, "Need to know where to start degenerated cycle");
      return false;
    }

    if (VerifyAfterGC) {
      vmop_entry_verify_final_roots();
    }
    _abbreviated = true;
  }

  // We defer generation resizing actions until after cset regions have been recycled.  We do this even following an
  // abbreviated cycle.
  if (heap->mode()->is_generational()) {
    ShenandoahGenerationalHeap::heap()->complete_concurrent_cycle();
  }

  // Instead of always resetting immediately before the start of a new GC, we can often reset at the end of the
  // previous GC. This allows us to start the next GC cycle more quickly after a trigger condition is detected,
  // reducing the likelihood that GC will degenerate.
  entry_reset_after_collect();

  return true;
}

bool ShenandoahConcurrentGC::complete_abbreviated_cycle() {
  shenandoah_assert_generational();

  ShenandoahGenerationalHeap* const heap = ShenandoahGenerationalHeap::heap();

  // We chose not to evacuate because we found sufficient immediate garbage.
  // However, there may still be regions to promote in place, so do that now.
  if (heap->old_generation()->has_in_place_promotions()) {
    entry_promote_in_place();

    // If the promote-in-place operation was cancelled, we can have the degenerated
    // cycle complete the operation. It will see that no evacuations are in progress,
    // and that there are regions wanting promotion. The risk with not handling the
    // cancellation would be failing to restore top for these regions and leaving
    // them unable to serve allocations for the old generation.This will leave the weak
    // roots flag set (the degenerated cycle will unset it).
    if (check_cancellation_and_abort(ShenandoahDegenPoint::_degenerated_evac)) {
      return false;
    }
  }

  // At this point, the cycle is effectively complete. If the cycle has been cancelled here,
  // the control thread will detect it on its next iteration and run a degenerated young cycle.
  if (!_generation->is_old()) {
    heap->update_region_ages(_generation->complete_marking_context());
  }

  if (!heap->is_concurrent_old_mark_in_progress()) {
    heap->concurrent_final_roots();
  } else {
    // Since the cycle was shortened for having enough immediate garbage, this will be
    // the last phase before concurrent marking of old resumes. We must be sure
    // that old mark threads don't see any pointers to garbage in the SATB queues. Even
    // though nothing was evacuated, overwriting unreachable weak roots with null may still
    // put pointers to regions that become trash in the SATB queues. The following will
    // piggyback flushing the thread local SATB queues on the same handshake that propagates
    // the gc state change.
    ShenandoahSATBMarkQueueSet& satb_queues = ShenandoahBarrierSet::satb_mark_queue_set();
    ShenandoahFlushSATBHandshakeClosure complete_thread_local_satb_buffers(satb_queues);
    heap->concurrent_final_roots(&complete_thread_local_satb_buffers);
    heap->old_generation()->concurrent_transfer_pointers_from_satb();
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

void ShenandoahConcurrentGC::vmop_entry_init_update_refs() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  TraceCollectorStats tcs(heap->monitoring_support()->stw_collection_counters());
  ShenandoahTimingsTracker timing(ShenandoahPhaseTimings::init_update_refs_gross);

  heap->try_inject_alloc_failure();
  VM_ShenandoahInitUpdateRefs op(this);
  VMThread::execute(&op);
}

void ShenandoahConcurrentGC::vmop_entry_final_update_refs() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  TraceCollectorStats tcs(heap->monitoring_support()->stw_collection_counters());
  ShenandoahTimingsTracker timing(ShenandoahPhaseTimings::final_update_refs_gross);

  heap->try_inject_alloc_failure();
  VM_ShenandoahFinalUpdateRefs op(this);
  VMThread::execute(&op);
}

void ShenandoahConcurrentGC::vmop_entry_verify_final_roots() {
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

void ShenandoahConcurrentGC::entry_init_update_refs() {
  static const char* msg = "Pause Init Update Refs";
  ShenandoahPausePhase gc_phase(msg, ShenandoahPhaseTimings::init_update_refs);
  EventMark em("%s", msg);

  // No workers used in this phase, no setup required
  op_init_update_refs();
}

void ShenandoahConcurrentGC::entry_final_update_refs() {
  static const char* msg = "Pause Final Update Refs";
  ShenandoahPausePhase gc_phase(msg, ShenandoahPhaseTimings::final_update_refs);
  EventMark em("%s", msg);

  ShenandoahWorkerScope scope(ShenandoahHeap::heap()->workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_final_update_ref(),
                              "final reference update");

  op_final_update_refs();
}

void ShenandoahConcurrentGC::entry_verify_final_roots() {
  const char* msg = verify_final_roots_event_message();
  ShenandoahPausePhase gc_phase(msg, ShenandoahPhaseTimings::final_roots);
  EventMark em("%s", msg);

  op_verify_final_roots();
}

void ShenandoahConcurrentGC::entry_reset() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  heap->try_inject_alloc_failure();

  TraceCollectorStats tcs(heap->monitoring_support()->concurrent_collection_counters());
  {
    const char* msg = conc_reset_event_message();
    ShenandoahConcurrentPhase gc_phase(msg, ShenandoahPhaseTimings::conc_reset);
    EventMark em("%s", msg);

    ShenandoahWorkerScope scope(heap->workers(),
                                ShenandoahWorkerPolicy::calc_workers_for_conc_reset(),
                                msg);
    op_reset();
  }
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
  const char* msg = conc_weak_refs_event_message();
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
  const char* msg = conc_weak_roots_event_message();
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
  const char* msg = conc_cleanup_event_message();
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

void ShenandoahConcurrentGC::entry_promote_in_place() const {
  shenandoah_assert_generational();

  ShenandoahTimingsTracker timing(ShenandoahPhaseTimings::promote_in_place);
  ShenandoahGCWorkerPhase worker_phase(ShenandoahPhaseTimings::promote_in_place);
  EventMark em("%s", "Promote in place");

  ShenandoahGenerationalHeap::heap()->promote_regions_in_place(true);
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

void ShenandoahConcurrentGC::entry_update_refs() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  TraceCollectorStats tcs(heap->monitoring_support()->concurrent_collection_counters());
  static const char* msg = "Concurrent update references";
  ShenandoahConcurrentPhase gc_phase(msg, ShenandoahPhaseTimings::conc_update_refs);
  EventMark em("%s", msg);

  ShenandoahWorkerScope scope(heap->workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_conc_update_ref(),
                              "concurrent reference update");

  heap->try_inject_alloc_failure();
  op_update_refs();
}

void ShenandoahConcurrentGC::entry_cleanup_complete() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  TraceCollectorStats tcs(heap->monitoring_support()->concurrent_collection_counters());
  const char* msg = conc_cleanup_event_message();
  ShenandoahConcurrentPhase gc_phase(msg, ShenandoahPhaseTimings::conc_cleanup_complete, true /* log_heap_usage */);
  EventMark em("%s", msg);

  // This phase does not use workers, no need for setup
  heap->try_inject_alloc_failure();
  op_cleanup_complete();
}

void ShenandoahConcurrentGC::entry_reset_after_collect() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  TraceCollectorStats tcs(heap->monitoring_support()->concurrent_collection_counters());
  const char* msg = conc_reset_after_collect_event_message();
  ShenandoahConcurrentPhase gc_phase(msg, ShenandoahPhaseTimings::conc_reset_after_collect);
  EventMark em("%s", msg);

  op_reset_after_collect();
}

void ShenandoahConcurrentGC::op_reset() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();

  // If it is old GC bootstrap cycle, always clear bitmap for global gen
  // to ensure bitmap for old gen is clear for old GC cycle after this.
  if (_do_old_gc_bootstrap) {
    assert(!heap->is_prepare_for_old_mark_in_progress(), "Cannot reset old without making it parsable");
    heap->global_generation()->prepare_gc();
  } else {
    _generation->prepare_gc();
  }

  if (heap->mode()->is_generational()) {
    heap->old_generation()->card_scan()->mark_read_table_as_clean();
  }
}

class ShenandoahInitMarkUpdateRegionStateClosure : public ShenandoahHeapRegionClosure {
private:
  ShenandoahMarkingContext* const _ctx;
public:
  ShenandoahInitMarkUpdateRegionStateClosure() : _ctx(ShenandoahHeap::heap()->marking_context()) {}

  void heap_region_do(ShenandoahHeapRegion* r) {
    assert(!r->has_live(), "Region %zu should have no live data", r->index());
    if (r->is_active()) {
      // Check if region needs updating its TAMS. We have updated it already during concurrent
      // reset, so it is very likely we don't need to do another write here.  Since most regions
      // are not "active", this path is relatively rare.
      if (_ctx->top_at_mark_start(r) != r->top()) {
        _ctx->capture_top_at_mark_start(r);
      }
    } else {
      assert(_ctx->top_at_mark_start(r) == r->top(),
             "Region %zu should already have correct TAMS", r->index());
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

    if (_generation->is_global()) {
      heap->old_generation()->cancel_gc();
    } else if (heap->is_concurrent_old_mark_in_progress()) {
      // Purge the SATB buffers, transferring any valid, old pointers to the
      // old generation mark queue. Any pointers in a young region will be
      // abandoned.
      ShenandoahGCPhase phase(ShenandoahPhaseTimings::init_transfer_satb);
      heap->old_generation()->transfer_pointers_from_satb();
    }
    {
      // After we swap card table below, the write-table is all clean, and the read table holds
      // cards dirty prior to the start of GC. Young and bootstrap collection will update
      // the write card table as a side effect of remembered set scanning. Global collection will
      // update the card table as a side effect of global marking of old objects.
      ShenandoahGCPhase phase(ShenandoahPhaseTimings::init_swap_rset);
      _generation->swap_card_tables();
    }
  }

  if (ShenandoahVerify) {
    ShenandoahTimingsTracker v(ShenandoahPhaseTimings::init_mark_verify);
    heap->verifier()->verify_before_concmark();
  }

  if (VerifyBeforeGC) {
    Universe::verify();
  }

  _generation->set_concurrent_mark_in_progress(true);

  start_mark();

  if (_do_old_gc_bootstrap) {
    shenandoah_assert_generational();
    // Update region state for both young and old regions
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::init_update_region_states);
    ShenandoahInitMarkUpdateRegionStateClosure cl;
    heap->parallel_heap_region_iterate(&cl);
    heap->old_generation()->ref_processor()->reset_thread_locals();
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

  {
    ShenandoahTimingsTracker timing(ShenandoahPhaseTimings::init_propagate_gc_state);
    heap->propagate_gc_state_to_all_threads();
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

    // The collection set is chosen by prepare_regions_and_collection_set(). Additionally, certain parameters have been
    // established to govern the evacuation efforts that are about to begin.  Refer to comments on reserve members in
    // ShenandoahGeneration and ShenandoahOldGeneration for more detail.
    _generation->prepare_regions_and_collection_set(true /*concurrent*/);

    // Has to be done after cset selection
    heap->prepare_concurrent_roots();

    if (!heap->collection_set()->is_empty()) {
      LogTarget(Debug, gc, cset) lt;
      if (lt.is_enabled()) {
        ResourceMark rm;
        LogStream ls(lt);
        heap->collection_set()->print_on(&ls);
      }

      if (ShenandoahVerify) {
        ShenandoahTimingsTracker v(ShenandoahPhaseTimings::final_mark_verify);
        heap->verifier()->verify_before_evacuation();
      }

      heap->set_evacuation_in_progress(true);
      // From here on, we need to update references.
      heap->set_has_forwarded_objects(true);

      // Arm nmethods/stack for concurrent processing
      ShenandoahCodeRoots::arm_nmethods_for_evac();
      ShenandoahStackWatermark::change_epoch_id();

    } else {
      if (ShenandoahVerify) {
        ShenandoahTimingsTracker v(ShenandoahPhaseTimings::final_mark_verify);
        if (has_in_place_promotions(heap)) {
          heap->verifier()->verify_after_concmark_with_promotions();
        } else {
          heap->verifier()->verify_after_concmark();
        }
      }
    }
  }

  {
    ShenandoahTimingsTracker timing(ShenandoahPhaseTimings::final_mark_propagate_gc_state);
    heap->propagate_gc_state_to_all_threads();
  }
}

bool ShenandoahConcurrentGC::has_in_place_promotions(ShenandoahHeap* heap) {
  return heap->mode()->is_generational() && heap->old_generation()->has_in_place_promotions();
}

template<bool GENERATIONAL>
class ShenandoahConcurrentEvacThreadClosure : public ThreadClosure {
private:
  OopClosure* const _oops;
public:
  explicit ShenandoahConcurrentEvacThreadClosure(OopClosure* oops) : _oops(oops) {}

  void do_thread(Thread* thread) override {
    JavaThread* const jt = JavaThread::cast(thread);
    StackWatermarkSet::finish_processing(jt, _oops, StackWatermarkKind::gc);
    if (GENERATIONAL) {
      ShenandoahThreadLocalData::enable_plab_promotions(thread);
    }
  }
};

template<bool GENERATIONAL>
class ShenandoahConcurrentEvacUpdateThreadTask : public WorkerTask {
private:
  ShenandoahJavaThreadsIterator _java_threads;

public:
  explicit ShenandoahConcurrentEvacUpdateThreadTask(uint n_workers) :
    WorkerTask("Shenandoah Evacuate/Update Concurrent Thread Roots"),
    _java_threads(ShenandoahPhaseTimings::conc_thread_roots, n_workers) {
  }

  void work(uint worker_id) override {
    if (GENERATIONAL) {
      Thread* worker_thread = Thread::current();
      ShenandoahThreadLocalData::enable_plab_promotions(worker_thread);
    }

    // ShenandoahEvacOOMScope has to be setup by ShenandoahContextEvacuateUpdateRootsClosure.
    // Otherwise, may deadlock with watermark lock
    ShenandoahContextEvacuateUpdateRootsClosure oops_cl;
    ShenandoahConcurrentEvacThreadClosure<GENERATIONAL> thr_cl(&oops_cl);
    _java_threads.threads_do(&thr_cl, worker_id);
  }
};

void ShenandoahConcurrentGC::op_thread_roots() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  assert(heap->is_evacuation_in_progress(), "Checked by caller");
  ShenandoahGCWorkerPhase worker_phase(ShenandoahPhaseTimings::conc_thread_roots);
  if (heap->mode()->is_generational()) {
    ShenandoahConcurrentEvacUpdateThreadTask<true> task(heap->workers()->active_workers());
    heap->workers()->run_task(&task);
  } else {
    ShenandoahConcurrentEvacUpdateThreadTask<false> task(heap->workers()->active_workers());
    heap->workers()->run_task(&task);
  }
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
      shenandoah_assert_generations_reconciled();
      if (_heap->is_in_active_generation(obj)) {
        // Note: The obj is dead here. Do not touch it, just clear.
        ShenandoahHeap::atomic_clear_oop(p, obj);
      }
    } else if (_evac_in_progress && _heap->in_collection_set(obj)) {
      oop resolved = ShenandoahBarrierSet::resolve_forwarded_not_null(obj);
      if (resolved == obj) {
        resolved = _heap->evacuate_object(obj, _thread);
      }
      shenandoah_assert_not_in_cset_except(p, resolved, _heap->cancelled_gc());
      ShenandoahHeap::atomic_update_oop(resolved, p, obj);
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
    _phase(phase) {}

  ~ShenandoahConcurrentWeakRootsEvacUpdateTask() {
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
    // clean up the weak oops in CLD and determine nmethod's unloading state, so that we
    // can clean up immediate garbage sooner.
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
  {
    // Concurrent weak root processing
    ShenandoahTimingsTracker t(ShenandoahPhaseTimings::conc_weak_roots_work);
    ShenandoahGCWorkerPhase worker_phase(ShenandoahPhaseTimings::conc_weak_roots_work);
    ShenandoahConcurrentWeakRootsEvacUpdateTask task(ShenandoahPhaseTimings::conc_weak_roots_work);
    heap->workers()->run_task(&task);
  }

  {
    // It is possible for mutators executing the load reference barrier to have
    // loaded an oop through a weak handle that has since been nulled out by
    // weak root processing. Handshaking here forces them to complete the
    // barrier before the GC cycle continues and does something that would
    // change the evaluation of the barrier (for example, resetting the TAMS
    // on trashed regions could make an oop appear to be marked _after_ the
    // region has been recycled).
    ShenandoahTimingsTracker t(ShenandoahPhaseTimings::conc_weak_roots_rendezvous);
    heap->rendezvous_threads("Shenandoah Concurrent Weak Roots");
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
    _nmethod_itr(ShenandoahCodeRoots::table()) {}

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
  ShenandoahWorkerScope scope(ShenandoahHeap::heap()->workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_conc_cleanup(),
                              "cleanup early.");
  ShenandoahHeap::heap()->recycle_trash();
}

void ShenandoahConcurrentGC::op_evacuate() {
  ShenandoahHeap::heap()->evacuate_collection_set(true /*concurrent*/);
}

void ShenandoahConcurrentGC::op_init_update_refs() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  if (ShenandoahVerify) {
    ShenandoahTimingsTracker v(ShenandoahPhaseTimings::init_update_refs_verify);
    heap->verifier()->verify_before_update_refs();
  }
}

void ShenandoahConcurrentGC::op_update_refs() {
  ShenandoahHeap::heap()->update_heap_references(true /*concurrent*/);
}

class ShenandoahUpdateThreadHandshakeClosure : public HandshakeClosure {
private:
  // This closure runs when thread is stopped for handshake, which means
  // we can use non-concurrent closure here, as long as it only updates
  // locations modified by the thread itself, i.e. stack locations.
  ShenandoahNonConcUpdateRefsClosure _cl;
public:
  ShenandoahUpdateThreadHandshakeClosure();
  void do_thread(Thread* thread);
};

ShenandoahUpdateThreadHandshakeClosure::ShenandoahUpdateThreadHandshakeClosure() :
  HandshakeClosure("Shenandoah Update Thread Roots") {
}

void ShenandoahUpdateThreadHandshakeClosure::do_thread(Thread* thread) {
  if (thread->is_Java_thread()) {
    JavaThread* jt = JavaThread::cast(thread);
    ResourceMark rm;
    jt->oops_do(&_cl, nullptr);
  }
}

void ShenandoahConcurrentGC::op_update_thread_roots() {
  ShenandoahUpdateThreadHandshakeClosure cl;
  Handshake::execute(&cl);
}

void ShenandoahConcurrentGC::op_final_update_refs() {
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

  // If we are running in generational mode and this is an aging cycle, this will also age active
  // regions that haven't been used for allocation.
  heap->update_heap_region_states(true /*concurrent*/);

  heap->set_update_refs_in_progress(false);
  heap->set_has_forwarded_objects(false);

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
    heap->old_generation()->transfer_pointers_from_satb();

    // Aging_cycle is only relevant during evacuation cycle for individual objects and during final mark for
    // entire regions.  Both of these relevant operations occur before final update refs.
    ShenandoahGenerationalHeap::heap()->set_aging_cycle(false);
  }

  if (ShenandoahVerify) {
    ShenandoahTimingsTracker v(ShenandoahPhaseTimings::final_update_refs_verify);
    heap->verifier()->verify_after_update_refs();
  }

  if (VerifyAfterGC) {
    Universe::verify();
  }

  heap->rebuild_free_set(true /*concurrent*/);

  {
    ShenandoahTimingsTracker timing(ShenandoahPhaseTimings::final_update_refs_propagate_gc_state);
    heap->propagate_gc_state_to_all_threads();
  }
}

bool ShenandoahConcurrentGC::entry_final_roots() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  TraceCollectorStats tcs(heap->monitoring_support()->concurrent_collection_counters());


  const char* msg = conc_final_roots_event_message();
  ShenandoahConcurrentPhase gc_phase(msg, ShenandoahPhaseTimings::conc_final_roots);
  EventMark em("%s", msg);
  ShenandoahWorkerScope scope(heap->workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_conc_evac(),
                              msg);

  if (!heap->mode()->is_generational()) {
    heap->concurrent_final_roots();
  } else {
    if (!complete_abbreviated_cycle()) {
      return false;
    }
  }
  return true;
}

void ShenandoahConcurrentGC::op_verify_final_roots() {
  if (VerifyAfterGC) {
    Universe::verify();
  }
}

void ShenandoahConcurrentGC::op_cleanup_complete() {
  ShenandoahWorkerScope scope(ShenandoahHeap::heap()->workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_conc_cleanup(),
                              "cleanup complete.");
  ShenandoahHeap::heap()->recycle_trash();
}

void ShenandoahConcurrentGC::op_reset_after_collect() {
  ShenandoahWorkerScope scope(ShenandoahHeap::heap()->workers(),
                          ShenandoahWorkerPolicy::calc_workers_for_conc_reset(),
                          "reset after collection.");

  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  if (heap->mode()->is_generational()) {
    // If we are in the midst of an old gc bootstrap or an old marking, we want to leave the mark bit map of
    // the young generation intact. In particular, reference processing in the old generation may potentially
    // need the reachability of a young generation referent of a Reference object in the old generation.
    if (!_do_old_gc_bootstrap && !heap->is_concurrent_old_mark_in_progress()) {
      heap->young_generation()->reset_mark_bitmap<false>();
    }
  } else {
    _generation->reset_mark_bitmap<false>();
  }
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
    SHENANDOAH_RETURN_EVENT_MESSAGE(_generation->type(), "Pause Init Mark", " (unload classes)");
  } else {
    SHENANDOAH_RETURN_EVENT_MESSAGE(_generation->type(), "Pause Init Mark", "");
  }
}

const char* ShenandoahConcurrentGC::final_mark_event_message() const {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  assert(!heap->has_forwarded_objects() || heap->is_concurrent_old_mark_in_progress(),
         "Should not have forwarded objects during final mark, unless old gen concurrent mark is running");

  if (heap->unload_classes()) {
    SHENANDOAH_RETURN_EVENT_MESSAGE(_generation->type(), "Pause Final Mark", " (unload classes)");
  } else {
    SHENANDOAH_RETURN_EVENT_MESSAGE(_generation->type(), "Pause Final Mark", "");
  }
}

const char* ShenandoahConcurrentGC::conc_mark_event_message() const {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  assert(!heap->has_forwarded_objects() || heap->is_concurrent_old_mark_in_progress(),
         "Should not have forwarded objects concurrent mark, unless old gen concurrent mark is running");
  if (heap->unload_classes()) {
    SHENANDOAH_RETURN_EVENT_MESSAGE(_generation->type(), "Concurrent marking", " (unload classes)");
  } else {
    SHENANDOAH_RETURN_EVENT_MESSAGE(_generation->type(), "Concurrent marking", "");
  }
}

const char* ShenandoahConcurrentGC::conc_reset_event_message() const {
  if (ShenandoahHeap::heap()->unload_classes()) {
    SHENANDOAH_RETURN_EVENT_MESSAGE(_generation->type(), "Concurrent reset", " (unload classes)");
  } else {
    SHENANDOAH_RETURN_EVENT_MESSAGE(_generation->type(), "Concurrent reset", "");
  }
}

const char* ShenandoahConcurrentGC::conc_reset_after_collect_event_message() const {
  if (ShenandoahHeap::heap()->unload_classes()) {
    SHENANDOAH_RETURN_EVENT_MESSAGE(_generation->type(), "Concurrent reset after collect", " (unload classes)");
  } else {
    SHENANDOAH_RETURN_EVENT_MESSAGE(_generation->type(), "Concurrent reset after collect", "");
  }
}

const char* ShenandoahConcurrentGC::verify_final_roots_event_message() const {
  if (ShenandoahHeap::heap()->unload_classes()) {
    SHENANDOAH_RETURN_EVENT_MESSAGE(_generation->type(), "Pause Verify Final Roots", " (unload classes)");
  } else {
    SHENANDOAH_RETURN_EVENT_MESSAGE(_generation->type(), "Pause Verify Final Roots", "");
  }
}

const char* ShenandoahConcurrentGC::conc_final_roots_event_message() const {
  if (ShenandoahHeap::heap()->unload_classes()) {
    SHENANDOAH_RETURN_EVENT_MESSAGE(_generation->type(), "Concurrent Final Roots", " (unload classes)");
  } else {
    SHENANDOAH_RETURN_EVENT_MESSAGE(_generation->type(), "Concurrent Final Roots", "");
  }
}

const char* ShenandoahConcurrentGC::conc_weak_refs_event_message() const {
  if (ShenandoahHeap::heap()->unload_classes()) {
    SHENANDOAH_RETURN_EVENT_MESSAGE(_generation->type(), "Concurrent weak references", " (unload classes)");
  } else {
    SHENANDOAH_RETURN_EVENT_MESSAGE(_generation->type(), "Concurrent weak references", "");
  }
}

const char* ShenandoahConcurrentGC::conc_weak_roots_event_message() const {
  if (ShenandoahHeap::heap()->unload_classes()) {
    SHENANDOAH_RETURN_EVENT_MESSAGE(_generation->type(), "Concurrent weak roots", " (unload classes)");
  } else {
    SHENANDOAH_RETURN_EVENT_MESSAGE(_generation->type(), "Concurrent weak roots", "");
  }
}

const char* ShenandoahConcurrentGC::conc_cleanup_event_message() const {
  if (ShenandoahHeap::heap()->unload_classes()) {
    SHENANDOAH_RETURN_EVENT_MESSAGE(_generation->type(), "Concurrent cleanup", " (unload classes)");
  } else {
    SHENANDOAH_RETURN_EVENT_MESSAGE(_generation->type(), "Concurrent cleanup", "");
  }
}

const char* ShenandoahConcurrentGC::conc_init_update_refs_event_message() const {
  if (ShenandoahHeap::heap()->unload_classes()) {
    SHENANDOAH_RETURN_EVENT_MESSAGE(_generation->type(), "Concurrent Init Update Refs", " (unload classes)");
  } else {
    SHENANDOAH_RETURN_EVENT_MESSAGE(_generation->type(), "Concurrent Init Update Refs", "");
  }
}
