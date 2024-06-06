/*
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

#include "gc/shared/gc_globals.hpp"
#include "gc/shenandoah/shenandoahController.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"

void ShenandoahController::pacing_notify_alloc(size_t words) {
  assert(ShenandoahPacing, "should only call when pacing is enabled");
  Atomic::add(&_allocs_seen, words, memory_order_relaxed);
}

size_t ShenandoahController::reset_allocs_seen() {
  return Atomic::xchg(&_allocs_seen, (size_t)0, memory_order_relaxed);
}

void ShenandoahController::prepare_for_graceful_shutdown() {
  _graceful_shutdown.set();
}

bool ShenandoahController::in_graceful_shutdown() {
  return _graceful_shutdown.is_set();
}

void ShenandoahController::update_gc_id() {
  Atomic::inc(&_gc_id);
}

size_t ShenandoahController::get_gc_id() {
  return Atomic::load(&_gc_id);
}

void ShenandoahController::handle_alloc_failure(ShenandoahAllocRequest& req, bool block) {
  ShenandoahHeap* heap = ShenandoahHeap::heap();

  assert(current()->is_Java_thread(), "expect Java thread here");
  bool is_humongous = req.size() > ShenandoahHeapRegion::humongous_threshold_words();

  if (try_set_alloc_failure_gc(is_humongous)) {
    // Only report the first allocation failure
    log_info(gc)("Failed to allocate %s, " SIZE_FORMAT "%s",
                 req.type_string(),
                 byte_size_in_proper_unit(req.size() * HeapWordSize), proper_unit_for_byte_size(req.size() * HeapWordSize));

    // Now that alloc failure GC is scheduled, we can abort everything else
    heap->cancel_gc(GCCause::_allocation_failure);
  }


  if (block) {
    MonitorLocker ml(&_alloc_failure_waiters_lock);
    while (is_alloc_failure_gc()) {
      ml.wait();
    }
  }
}

void ShenandoahController::handle_alloc_failure_evac(size_t words) {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  bool is_humongous = (words > ShenandoahHeapRegion::region_size_words());

  if (try_set_alloc_failure_gc(is_humongous)) {
    // Only report the first allocation failure
    log_info(gc)("Failed to allocate " SIZE_FORMAT "%s for evacuation",
                 byte_size_in_proper_unit(words * HeapWordSize), proper_unit_for_byte_size(words * HeapWordSize));
  }

  // Forcefully report allocation failure
  heap->cancel_gc(GCCause::_shenandoah_allocation_failure_evac);
}

void ShenandoahController::notify_alloc_failure_waiters() {
  _alloc_failure_gc.unset();
  _humongous_alloc_failure_gc.unset();
  MonitorLocker ml(&_alloc_failure_waiters_lock);
  ml.notify_all();
}

bool ShenandoahController::try_set_alloc_failure_gc(bool is_humongous) {
  if (is_humongous) {
    _humongous_alloc_failure_gc.try_set();
  }
  return _alloc_failure_gc.try_set();
}

bool ShenandoahController::is_alloc_failure_gc() {
  return _alloc_failure_gc.is_set();
}

