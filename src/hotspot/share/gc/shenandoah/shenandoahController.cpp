/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/gc_globals.hpp"
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahController.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"

ShenandoahController::ShenandoahController():
  _gc_id(0),
  _phase(UNSET),
  _gc_waiters_lock(WAITERS_LOCK_RANK, "ShenandoahGCWaiters_lock", true),
  _alloc_waiters_lock(WAITERS_LOCK_RANK, "ShenandoahAllocWaiters_lock", true),
  _alloc_stall_count(0),
  _concurrent_worker_count(ConcGCThreads) { }

void ShenandoahController::update_gc_id() {
  _gc_id.add_then_fetch(1UL);
}

size_t ShenandoahController::get_gc_id() const {
  return _gc_id.load_relaxed();
}

void ShenandoahController::handle_alloc_failure(const ShenandoahAllocRequest &req) {
  assert(current()->is_Java_thread(), "expect Java thread here");

  const bool is_humongous = ShenandoahHeapRegion::requires_humongous(req.size());
  const GCCause::Cause cause = is_humongous ? GCCause::_shenandoah_humongous_allocation_failure : GCCause::_allocation_failure;

  const size_t req_byte = req.size() * HeapWordSize;
  log_debug(gc)("Failed to allocate %s, " PROPERFMT, req.type_string(), PROPERFMTARGS(req_byte));
  AllocTracer::send_allocation_requiring_gc_event(req_byte, checked_cast<uint>(get_gc_id()));

  // This is the inner part of a larger retry loop, so just wait here
  MonitorLocker ml(&_alloc_waiters_lock);
  _alloc_stall_count.add_then_fetch(1UL);
  increase_concurrent_worker_count();
  ShenandoahHeap::heap()->shenandoah_policy()->record_allocation_stall(get_phase());
  notify_alloc_stall(cause);
  if (!should_terminate()) {
    ml.wait();
  }
}

void ShenandoahController::handle_alloc_failure_full() {
  if (should_terminate()) {
    log_info(gc)("Control thread is terminating, no more GCs");
    return;
  }

  // Make sure we have at least one full GC cycle before unblocking from the explicit GC request.
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  const ShenandoahCollectorPolicy* policy = heap->shenandoah_policy();
  MonitorLocker ml(&_gc_waiters_lock);
  size_t full_gc_count = policy->full_gc_count();
  const size_t required_count = full_gc_count + 1;
  while (full_gc_count < required_count && !should_terminate()) {
    notify_control_thread(GCCause::_shenandoah_upgrade_to_full_gc, heap->global_generation());
    ml.wait();
    full_gc_count = policy->full_gc_count();
  }
}

void ShenandoahController::request_gc(GCCause::Cause cause) {
  if (ShenandoahCollectorPolicy::should_handle_requested_gc(cause)) {
    handle_requested_gc(cause);
  }
}

void ShenandoahController::handle_requested_gc(GCCause::Cause cause) {

  // Requested gc's always operate on the entire heap
  ShenandoahGeneration* global = ShenandoahHeap::heap()->global_generation();

  // For normal requested GCs (System.gc) we want to block the caller. However,
  // for whitebox requested GC, we want to initiate the GC and return immediately.
  // The whitebox caller thread will arrange for itself to wait until the GC notifies
  // it that has reached the requested breakpoint (phase in the GC).
  if (cause == GCCause::_wb_breakpoint) {
    notify_control_thread(cause, global);
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
  while (current_gc_id < required_gc_id && !should_terminate()) {
    notify_control_thread(cause, global);
    ml.wait();
    current_gc_id = get_gc_id();
  }
}

void ShenandoahController::increase_concurrent_worker_count() {
  while (true) {
    const size_t workers = _concurrent_worker_count.load_relaxed();
    if (workers == ParallelGCThreads) {
      break;
    }

    const size_t new_value = MIN2(workers + 1, checked_cast<size_t>(ParallelGCThreads));
    if (_concurrent_worker_count.compare_set(workers, new_value, memory_order_relaxed)) {
      break;
    }
  }
}

void ShenandoahController::decrease_concurrent_worker_count() {
  if (_alloc_stall_count.exchange(0) == 0) {
    // There were no stalls during this cycle, try to reduce the concurrent gc workers
    while (true) {
      const size_t workers = _concurrent_worker_count.load_relaxed();
      if (workers == ConcGCThreads) {
        break;
      }

      const size_t new_value = MAX2(workers - 1, checked_cast<size_t>(ConcGCThreads));
      if (_concurrent_worker_count.compare_set(workers, new_value, memory_order_relaxed)) {
        break;
      }
    }
  }
}

void ShenandoahController::notify_gc_waiters() {
  MonitorLocker ml(&_gc_waiters_lock);
  ml.notify_all();
}

void ShenandoahController::notify_alloc_waiters() {
  MonitorLocker ml(&_alloc_waiters_lock);
  ml.notify_all();
}

const char* ShenandoahController::collector_phase_to_string(ShenandoahCollectorPhase phase) {
  switch(phase) {
    case UNSET:        return "Outside of Cycle";
    case INITIALIZING: return "Initializing";
    case ROOTS:        return "Roots";
    case MARK:         return "Mark";
    case EVAC:         return "Evacuation";
    case UPDATE_REFS:  return "Update References";
    default:
      ShouldNotReachHere();
  }
}
