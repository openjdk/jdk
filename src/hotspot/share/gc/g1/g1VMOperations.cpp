/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1ConcurrentMarkThread.inline.hpp"
#include "gc/g1/g1Policy.hpp"
#include "gc/g1/g1VMOperations.hpp"
#include "gc/g1/g1Trace.hpp"
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
  _gc_succeeded = g1h->do_full_collection(false /* clear_all_soft_refs */,
                                          false /* do_maximal_compaction */);
}

VM_G1TryInitiateConcMark::VM_G1TryInitiateConcMark(uint gc_count_before,
                                                   GCCause::Cause gc_cause) :
  VM_GC_Operation(gc_count_before, gc_cause),
  _transient_failure(false),
  _cycle_already_in_progress(false),
  _whitebox_attached(false),
  _terminating(false),
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

  // Record for handling by caller.
  _terminating = g1h->concurrent_mark_is_terminating();

  if (_terminating && GCCause::is_user_requested_gc(_gc_cause)) {
    // When terminating, the request to initiate a concurrent cycle will be
    // ignored by do_collection_pause_at_safepoint; instead it will just do
    // a young-only or mixed GC (depending on phase).  For a user request
    // there's no point in even doing that much, so done.  For some non-user
    // requests the alternative GC might still be needed.
  } else if (!g1h->policy()->force_concurrent_start_if_outside_cycle(_gc_cause)) {
    // Failure to force the next GC pause to be a concurrent start indicates
    // there is already a concurrent marking cycle in progress.  Set flag
    // to notify the caller and return immediately.
    _cycle_already_in_progress = true;
  } else if ((_gc_cause != GCCause::_wb_breakpoint) &&
             ConcurrentGCBreakpoints::is_controlled()) {
    // WhiteBox wants to be in control of concurrent cycles, so don't try to
    // start one.  This check is after the force_concurrent_start_xxx so that a
    // request will be remembered for a later partial collection, even though
    // we've rejected this request.
    _whitebox_attached = true;
  } else {
    _gc_succeeded = g1h->do_collection_pause_at_safepoint();
    assert(_gc_succeeded, "No reason to fail");
  }
}

VM_G1CollectForAllocation::VM_G1CollectForAllocation(size_t         word_size,
                                                     uint           gc_count_before,
                                                     GCCause::Cause gc_cause) :
  VM_CollectForAllocation(word_size, gc_count_before, gc_cause),
  _gc_succeeded(false) {}

void VM_G1CollectForAllocation::doit() {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();

  GCCauseSetter x(g1h, _gc_cause);
  // Try a partial collection of some kind.
  _gc_succeeded = g1h->do_collection_pause_at_safepoint();
  assert(_gc_succeeded, "no reason to fail");

  if (_word_size > 0) {
    // An allocation had been requested. Do it, eventually trying a stronger
    // kind of GC.
    _result = g1h->satisfy_failed_allocation(_word_size, &_gc_succeeded);
  } else if (g1h->should_upgrade_to_full_gc()) {
    // There has been a request to perform a GC to free some space. We have no
    // information on how much memory has been asked for. In case there are
    // absolutely no regions left to allocate into, do a full compaction.
    _gc_succeeded = g1h->upgrade_to_full_collection();
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
  IsGCActiveMark x;

  work();
}

bool VM_G1PauseConcurrent::doit_prologue() {
  Heap_lock->lock();
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
