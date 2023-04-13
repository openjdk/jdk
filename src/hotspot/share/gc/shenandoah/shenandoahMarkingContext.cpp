/*
 * Copyright (c) 2018, 2021, Red Hat, Inc. All rights reserved.
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
#include "gc/shared/markBitMap.inline.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.hpp"

ShenandoahMarkingContext::ShenandoahMarkingContext(MemRegion heap_region, MemRegion bitmap_region, size_t num_regions) :
  _mark_bit_map(heap_region, bitmap_region),
  _top_bitmaps(NEW_C_HEAP_ARRAY(HeapWord*, num_regions, mtGC)),
  _top_at_mark_starts_base(NEW_C_HEAP_ARRAY(HeapWord*, num_regions, mtGC)),
  _top_at_mark_starts(_top_at_mark_starts_base -
                      ((uintx) heap_region.start() >> ShenandoahHeapRegion::region_size_bytes_shift())) {
}

bool ShenandoahMarkingContext::is_bitmap_clear() const {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  size_t num_regions = heap->num_regions();
  for (size_t idx = 0; idx < num_regions; idx++) {
    ShenandoahHeapRegion* r = heap->get_region(idx);
    if (r->is_affiliated() && heap->is_bitmap_slice_committed(r) && !is_bitmap_clear_range(r->bottom(), r->end())) {
      return false;
    }
  }
  return true;
}

bool ShenandoahMarkingContext::is_bitmap_clear_range(const HeapWord* start, const HeapWord* end) const {
  if (start < end) {
    ShenandoahHeap* heap = ShenandoahHeap::heap();
    size_t start_idx = heap->heap_region_index_containing(start);
    size_t end_idx = heap->heap_region_index_containing(end - 1);
    while (start_idx <= end_idx) {
      ShenandoahHeapRegion* r = heap->get_region(start_idx);
      if (!heap->is_bitmap_slice_committed(r)) {
        return true;
      }
      start_idx++;
    }
  }
  return _mark_bit_map.is_bitmap_clear_range(start, end);
}

void ShenandoahMarkingContext::initialize_top_at_mark_start(ShenandoahHeapRegion* r) {
  size_t idx = r->index();
  HeapWord *bottom = r->bottom();

  _top_at_mark_starts_base[idx] = bottom;
  _top_bitmaps[idx] = bottom;

  log_debug(gc)("SMC:initialize_top_at_mark_start for Region " SIZE_FORMAT ", TAMS: " PTR_FORMAT ", TopOfBitMap: " PTR_FORMAT,
                r->index(), p2i(bottom), p2i(r->end()));
}

HeapWord* ShenandoahMarkingContext::top_bitmap(ShenandoahHeapRegion* r) {
  return _top_bitmaps[r->index()];
}

void ShenandoahMarkingContext::clear_bitmap(ShenandoahHeapRegion* r) {
  if (!r->is_affiliated()) {
    // Heap iterators include FREE regions, which don't need to be cleared.
    // TODO: would be better for certain iterators to not include FREE regions.
    return;
  }

  HeapWord* bottom = r->bottom();
  HeapWord* top_bitmap = _top_bitmaps[r->index()];

  log_debug(gc)("SMC:clear_bitmap for %s Region " SIZE_FORMAT ", top_bitmap: " PTR_FORMAT,
                r->affiliation_name(), r->index(), p2i(top_bitmap));

  if (top_bitmap > bottom) {
    _mark_bit_map.clear_range_large(MemRegion(bottom, top_bitmap));
    _top_bitmaps[r->index()] = bottom;
  }

  // TODO: Why is clear_live_data here?
  r->clear_live_data();

  assert(is_bitmap_clear_range(bottom, r->end()),
         "Region " SIZE_FORMAT " should have no marks in bitmap", r->index());
}

bool ShenandoahMarkingContext::is_complete() {
  return _is_complete.is_set();
}

void ShenandoahMarkingContext::mark_complete() {
  _is_complete.set();
}

void ShenandoahMarkingContext::mark_incomplete() {
  _is_complete.unset();
}
