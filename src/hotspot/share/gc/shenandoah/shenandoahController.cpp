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

#include "gc/shared/gc_globals.hpp"
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahController.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"


void ShenandoahController::update_gc_id() {
  Atomic::inc(&_gc_id);
}

size_t ShenandoahController::get_gc_id() {
  return Atomic::load(&_gc_id);
}

void ShenandoahController::handle_alloc_failure(const ShenandoahAllocRequest& req, bool block) {
  assert(current()->is_Java_thread(), "expect Java thread here");

  const bool is_humongous = ShenandoahHeapRegion::requires_humongous(req.size());
  const GCCause::Cause cause = is_humongous ? GCCause::_shenandoah_humongous_allocation_failure : GCCause::_allocation_failure;

  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  if (heap->cancel_gc(cause)) {
    log_info(gc)("Failed to allocate %s, " PROPERFMT, req.type_string(), PROPERFMTARGS(req.size() * HeapWordSize));
    request_gc(cause);
  }

  if (block) {
    MonitorLocker ml(&_alloc_failure_waiters_lock);
    while (!should_terminate() && ShenandoahCollectorPolicy::is_allocation_failure(heap->cancelled_cause())) {
      ml.wait();
    }
  }
}

void ShenandoahController::handle_alloc_failure_evac(size_t words) {

  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  const bool is_humongous = ShenandoahHeapRegion::requires_humongous(words);
  const GCCause::Cause cause = is_humongous ? GCCause::_shenandoah_humongous_allocation_failure : GCCause::_shenandoah_allocation_failure_evac;

  if (heap->cancel_gc(cause)) {
    log_info(gc)("Failed to allocate " PROPERFMT " for evacuation", PROPERFMTARGS(words * HeapWordSize));
  }
}

void ShenandoahController::notify_alloc_failure_waiters() {
  MonitorLocker ml(&_alloc_failure_waiters_lock);
  ml.notify_all();
}
