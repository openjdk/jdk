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
#include "gc/shenandoah/shenandoahController.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"

ShenandoahController::ShenandoahController():
  _gc_id(0),
  _phase(UNSET),
  _gc_waiters_lock(WAITERS_LOCK_RANK, "ShenandoahGCWaiters_lock", true),
  _alloc_waiters_count(0),
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
  log_info(gc)("Failed to allocate %s, " PROPERFMT, req.type_string(), PROPERFMTARGS(req_byte));
  AllocTracer::send_allocation_requiring_gc_event(req_byte, checked_cast<uint>(get_gc_id()));
  request_gc(cause);
}

void ShenandoahController::adjust_concurrent_worker_count() {
  const size_t stalls = _alloc_waiters_count.load_relaxed();
  if (stalls > 0) {
    _concurrent_worker_count = MIN2(stalls, checked_cast<size_t>(ParallelGCThreads));
  } else {
    // no stalls, backoff slowly. Making this a little 'sticky' allows us to use
    // more threads during a mark phase that follows a cycle which had allocation stalls.
    _concurrent_worker_count = MAX2(_concurrent_worker_count - 1, checked_cast<size_t>(ConcGCThreads));
  }
}

void ShenandoahController::notify_gc_waiters() {
  adjust_concurrent_worker_count();

  MonitorLocker ml(&_gc_waiters_lock);
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
