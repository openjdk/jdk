/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1Allocator.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1ConcurrentMarkThread.inline.hpp"
#include "gc/g1/g1HeapSizingPolicy.hpp"
#include "gc/g1/g1Policy.hpp"
#include "gc/g1/g1Trace.hpp"
#include "gc/g1/g1VMOperations.hpp"
#include "gc/shared/concurrentGCBreakpoints.hpp"
#include "gc/shared/gcCause.hpp"
#include "gc/shared/gcId.hpp"
#include "gc/shared/gcTimer.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/isGCActiveMark.hpp"
#include "memory/universe.hpp"
#include "runtime/interfaceSupport.inline.hpp"

bool VM_G1CollectFull::skip_operation() const {
  // There is a race between the periodic collection task's checks for
  // wanting a collection and processing its request.  A collection in that
  // gap should cancel the request.
  if ((_gc_cause == GCCause::_g1_periodic_collection) &&
      (G1CollectedHeap::heap()->total_collections() != _gc_count_before)) {
    return true;
  }
  return VM_GC_Operation::skip_operation();
}

void VM_G1CollectFull::doit() {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  GCCauseSetter x(g1h, _gc_cause);
  bool clear_all_soft_refs = _gc_cause == GCCause::_metadata_GC_clear_soft_refs ||
                             _gc_cause == GCCause::_wb_full_gc;
  g1h->do_full_collection(size_t(0) /* allocation_word_size */,
                          clear_all_soft_refs,
                          false /* do_maximal_compaction */);
}

VM_G1TryInitiateConcMark::VM_G1TryInitiateConcMark(size_t allocation_word_size,
                                                   uint gc_count_before,
                                                   GCCause::Cause gc_cause) :
  VM_GC_Collect_Operation(gc_count_before, gc_cause),
  _word_size(allocation_word_size),
  _transient_failure(false),
  _mark_in_progress(false),
  _cycle_already_in_progress(false),
  _whitebox_attached(false),
  _gc_succeeded(false)
{}

bool VM_G1TryInitiateConcMark::doit_prologue() {
  bool result = VM_GC_Operation::doit_prologue();
  // The prologue can fail for a couple of reasons. The first is that another GC
  // got scheduled and prevented the scheduling of the concurrent start GC.
  // In this case we want to retry the GC so that the concurrent start pause is
  // actually scheduled.
  if (!result) _transient_failure = true;
  return result;
}

void VM_G1TryInitiateConcMark::doit() {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();

  GCCauseSetter x(g1h, _gc_cause);

  _mark_in_progress = g1h->collector_state()->mark_in_progress();
  _cycle_already_in_progress = g1h->concurrent_mark()->in_progress();

  if (!g1h->policy()->force_concurrent_start_if_outside_cycle(_gc_cause)) {
    // Failure to force the next GC pause to be a concurrent start indicates
    // there is already a concurrent marking cycle in progress. Flags to indicate
    // that were already set, so return immediately.
  } else if ((_gc_cause != GCCause::_wb_breakpoint) &&
             ConcurrentGCBreakpoints::is_controlled()) {
    // WhiteBox wants to be in control of concurrent cycles, so don't try to
    // start one.  This check is after the force_concurrent_start_xxx so that a
    // request will be remembered for a later partial collection, even though
    // we've rejected this request.
    _whitebox_attached = true;
  } else {
    g1h->do_collection_pause_at_safepoint(_word_size);
    _gc_succeeded = true;
  }
}

VM_G1CollectForAllocation::VM_G1CollectForAllocation(size_t word_size,
                                                     uint gc_count_before,
                                                     GCCause::Cause gc_cause) :
  VM_CollectForAllocation(word_size, gc_count_before, gc_cause) {}

void VM_G1CollectForAllocation::doit() {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  GCCauseSetter x(g1h, _gc_cause);
  // Try a partial collection of some kind.
  g1h->do_collection_pause_at_safepoint(_word_size);

  if (_word_size > 0) {
    // An allocation had been requested. Do it, eventually trying a stronger
    // kind of GC.
    _result = g1h->satisfy_failed_allocation(_word_size);
  } else if (g1h->should_upgrade_to_full_gc()) {
    // There has been a request to perform a GC to free some space. We have no
    // information on how much memory has been asked for. In case there are
    // absolutely no regions left to allocate into, do a full compaction.
    g1h->upgrade_to_full_collection();
  }
}

void VM_G1PauseConcurrent::doit() {
  GCIdMark gc_id_mark(_gc_id);
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  GCTraceCPUTime tcpu(g1h->concurrent_mark()->gc_tracer_cm());

  // GCTraceTime(...) only supports sub-phases, so a more verbose version
  // is needed when we report the top-level pause phase.
  GCTraceTimeLogger(Info, gc) logger(_message, GCCause::_no_gc, true);
  GCTraceTimePauseTimer       timer(_message, g1h->concurrent_mark()->gc_timer_cm());
  GCTraceTimeDriver           t(&logger, &timer);

  G1ConcGCMonitoringScope monitoring_scope(g1h->monitoring_support());
  SvcGCMarker sgcm(SvcGCMarker::CONCURRENT);
  IsSTWGCActiveMark x;

  work();
}

bool VM_G1PauseConcurrent::doit_prologue() {
  Heap_lock->lock();
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  if (g1h->is_shutting_down()) {
    Heap_lock->unlock();
    // JVM shutdown has started. This ensures that any further operations will be properly aborted
    // and will not interfere with the shutdown process.
    g1h->concurrent_mark()->abort_marking_threads();
    return false;
  }
  return true;
}

void VM_G1PauseConcurrent::doit_epilogue() {
  if (Universe::has_reference_pending_list()) {
    Heap_lock->notify_all();
  }
  Heap_lock->unlock();
}

void VM_G1PauseRemark::work() {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  g1h->concurrent_mark()->remark();
}

void VM_G1PauseCleanup::work() {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  g1h->concurrent_mark()->cleanup();
}

void VM_G1ShrinkHeap::doit() {
  // Re-evaluate candidates at safepoint since heap state may have changed
  log_debug(gc, ergo, heap)("VM_G1ShrinkHeap: re-evaluating heap state at safepoint");

  // Max regions based on original request
  uint max_regions_to_shrink = (uint)(_bytes / G1HeapRegion::GrainBytes);

  GrowableArray<G1HeapRegion*> candidates(max_regions_to_shrink);
  _g1h->heap_sizing_policy()->find_uncommit_candidates_by_time(&candidates, max_regions_to_shrink);

  if (candidates.length() == 0) {
    log_debug(gc, ergo, heap)("VM_G1ShrinkHeap: no valid candidates at safepoint, skipping shrink");
    return;
  }

  // Validate candidates are still free at safepoint
  uint valid_count = 0;
  for (int i = 0; i < candidates.length(); i++) {
    G1HeapRegion* hr = candidates.at(i);
    if (hr->is_free()) {
      valid_count++;
    } else {
      log_debug(gc, ergo, heap)("VM_G1ShrinkHeap: skipping region %u - no longer free", hr->hrm_index());
    }
  }

  if (valid_count == 0) {
    log_debug(gc, ergo, heap)("VM_G1ShrinkHeap: no regions still valid at safepoint");
    return;
  }

  size_t shrink_bytes = (size_t)valid_count * G1HeapRegion::GrainBytes;
  log_info(gc, ergo, heap)("VM_G1ShrinkHeap: executing shrink with %u regions (%zuMB) after re-evaluation",
                           valid_count, shrink_bytes / M);

  _g1h->shrink_with_time_based_selection(shrink_bytes);
}
