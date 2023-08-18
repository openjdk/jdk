/*
 * Copyright (c) 2021, Red Hat, Inc. All rights reserved.
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

#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahMarkClosures.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.hpp"
#include "gc/shenandoah/shenandoahSharedVariables.hpp"


ShenandoahFinalMarkUpdateRegionStateClosure::ShenandoahFinalMarkUpdateRegionStateClosure(
  ShenandoahMarkingContext *ctx) :
  _ctx(ctx), _lock(ShenandoahHeap::heap()->lock()) {}

void ShenandoahFinalMarkUpdateRegionStateClosure::heap_region_do(ShenandoahHeapRegion* r) {
  if (r->is_active()) {
    if (_ctx != nullptr) {
      // _ctx may be null when this closure is used to sync only the pin status
      // update the watermark of old regions. For old regions we cannot reset
      // the TAMS because we rely on that to keep promoted objects alive after
      // old marking is complete.

      // All allocations past TAMS are implicitly live, adjust the region data.
      // Bitmaps/TAMS are swapped at this point, so we need to poll complete bitmap.
      HeapWord *tams = _ctx->top_at_mark_start(r);
      HeapWord *top = r->top();
      if (top > tams) {
        r->increase_live_data_alloc_words(pointer_delta(top, tams));
      }
    }

    // We are about to select the collection set, make sure it knows about
    // current pinning status. Also, this allows trashing more regions that
    // now have their pinning status dropped.
    if (r->is_pinned()) {
      if (r->pin_count() == 0) {
        ShenandoahHeapLocker locker(_lock);
        r->make_unpinned();
      }
    } else {
      if (r->pin_count() > 0) {
        ShenandoahHeapLocker locker(_lock);
        r->make_pinned();
      }
    }

    // Remember limit for updating refs. It's guaranteed that we get no
    // from-space-refs written from here on.
    r->set_update_watermark_at_safepoint(r->top());
  } else {
    assert(!r->has_live(), "Region " SIZE_FORMAT " should have no live data", r->index());
    assert(_ctx == nullptr || _ctx->top_at_mark_start(r) == r->top(),
             "Region " SIZE_FORMAT " should have correct TAMS", r->index());
  }
}


ShenandoahUpdateCensusZeroCohortClosure::ShenandoahUpdateCensusZeroCohortClosure(
  ShenandoahMarkingContext *ctx) :
  _ctx(ctx), _pop(0) {}

void ShenandoahUpdateCensusZeroCohortClosure::heap_region_do(ShenandoahHeapRegion* r) {
  if (_ctx != nullptr && r->is_active()) {
    assert(r->is_young(), "Young regions only");
    HeapWord* tams = _ctx->top_at_mark_start(r);
    HeapWord* top  = r->top();
    if (top > tams) {
      _pop += pointer_delta(top, tams);
    }
  }
}
