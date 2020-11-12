/*
 * Copyright (c) 2020, Red Hat, Inc. All rights reserved.
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

#include "gc/shenandoah/shenandoahClosures.inline.hpp"
#include "gc/shenandoah/shenandoahCodeRoots.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahStackWatermark.hpp"

#include "runtime/safepointVerifiers.hpp"

ShenandoahOnStackCodeBlobClosure::ShenandoahOnStackCodeBlobClosure() :
    _bs_nm(BarrierSet::barrier_set()->barrier_set_nmethod()) {}

void ShenandoahOnStackCodeBlobClosure::do_code_blob(CodeBlob* cb) {
  nmethod* const nm = cb->as_nmethod_or_null();
  if (nm != NULL) {
    const bool result = _bs_nm->nmethod_entry_barrier(nm);
    assert(result, "NMethod on-stack must be alive");
  }
}

ThreadLocalAllocStats& ShenandoahStackWatermark::stats() {
  return _stats;
}

uint32_t ShenandoahStackWatermark::epoch_id() const {
  return (uint32_t)ShenandoahCodeRoots::disarmed_value();
}

ShenandoahStackWatermark::ShenandoahStackWatermark(JavaThread* jt) :
  StackWatermark(jt, StackWatermarkKind::gc, (uint32_t)ShenandoahCodeRoots::disarmed_value()),
  _heap(ShenandoahHeap::heap()),
  _stats(),
  _keep_alive_cl(),
  _evac_update_oop_cl(),
  _cb_cl() {}

OopClosure* ShenandoahStackWatermark::closure_from_context(void* context) {
  if (context != NULL) {
    assert(_heap->is_concurrent_weak_root_in_progress() ||
           _heap->is_concurrent_mark_in_progress(),
           "Only these two phases");
    assert(Thread::current()->is_Worker_thread(), "Unexpected thread passing in context: " PTR_FORMAT, p2i(context));
    return reinterpret_cast<OopClosure*>(context);
  } else {
    if (_heap->is_concurrent_mark_in_progress()) {
      return &_keep_alive_cl;
    } else if (_heap->is_concurrent_weak_root_in_progress()) {
      return &_evac_update_oop_cl;
    } else {
      ShouldNotReachHere();
      return NULL;
    }
  }
}

void ShenandoahStackWatermark::start_processing_impl(void* context) {
  NoSafepointVerifier nsv;
  ShenandoahHeap* const heap = ShenandoahHeap::heap();

  // Process the non-frame part of the thread
  if (heap->is_concurrent_weak_root_in_progress()) {
    // Retire the TLABs, which will force threads to reacquire their TLABs.
    // This is needed for two reasons. Strong one: new allocations would be with new freeset,
    // which would be outside the collection set, so no cset writes would happen there.
    // Weaker one: new allocations would happen past update watermark, and so less work would
    // be needed for reference updates (would update the large filler instead).
    retire_tlab();

    ShenandoahEvacOOMScope oom;
    _jt->oops_do_no_frames(closure_from_context(context), &_cb_cl);
  } else if (heap->is_concurrent_mark_in_progress()) {
    // We need to reset all TLABs because they might be below the TAMS, and we need to mark
    // the objects in them. Do not let mutators allocate any new objects in their current TLABs.
    // It is also a good place to resize the TLAB sizes for future allocations.
    retire_tlab();

    _jt->oops_do_no_frames(closure_from_context(context), &_cb_cl);
  }

  // Publishes the processing start to concurrent threads
  StackWatermark::start_processing_impl(context);
}

void ShenandoahStackWatermark::retire_tlab() {
  // Retire TLAB
  if (UseTLAB) {
    _stats.reset();
    _jt->tlab().retire(&_stats);
    if (ResizeTLAB) {
      _jt->tlab().resize();
    }
  }
}

void ShenandoahStackWatermark::process(const frame& fr, RegisterMap& register_map, void* context) {
  OopClosure* oops = closure_from_context(context);
  assert(oops != NULL, "Should not get to here");
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  if (heap->is_concurrent_weak_root_in_progress()) {
    ShenandoahEvacOOMScope oom;
    fr.oops_do(oops, &_cb_cl, &register_map, DerivedPointerIterationMode::_directly);
  } else {
    assert(heap->is_concurrent_mark_in_progress(), "What else?");
    fr.oops_do(oops, &_cb_cl, &register_map, DerivedPointerIterationMode::_directly);
  }
}

