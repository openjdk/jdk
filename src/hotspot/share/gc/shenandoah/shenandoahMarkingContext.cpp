/*
 * Copyright (c) 2018, 2021, Red Hat, Inc. All rights reserved.
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

#include "gc/shared/markBitMap.inline.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.hpp"
#include "shenandoahGlobalGeneration.hpp"

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
    if (r->is_affiliated() && heap->is_bitmap_slice_committed(r)
        && !is_bitmap_range_within_region_clear(r->bottom(), r->end())) {
      return false;
    }
  }
  return true;
}

bool ShenandoahMarkingContext::is_bitmap_range_within_region_clear(const HeapWord* start, const HeapWord* end) const {
  assert(start <= end, "Invalid start " PTR_FORMAT " and end " PTR_FORMAT, p2i(start), p2i(end));
  if (start < end) {
    ShenandoahHeap* heap = ShenandoahHeap::heap();
    size_t start_idx = heap->heap_region_index_containing(start);
#ifdef ASSERT
    size_t end_idx = heap->heap_region_index_containing(end - 1);
    assert(start_idx == end_idx, "Expected range to be within same region (%zu, %zu)", start_idx, end_idx);
#endif
    ShenandoahHeapRegion* r = heap->get_region(start_idx);
    if (!heap->is_bitmap_slice_committed(r)) {
      return true;
    }
  }
  return _mark_bit_map.is_bitmap_clear_range(start, end);
}

void ShenandoahMarkingContext::initialize_top_at_mark_start(ShenandoahHeapRegion* r) {
  size_t idx = r->index();
  HeapWord *bottom = r->bottom();

  _top_at_mark_starts_base[idx] = bottom;
  _top_bitmaps[idx] = bottom;

  log_debug(gc)("SMC:initialize_top_at_mark_start for Region %zu, TAMS: " PTR_FORMAT ", TopOfBitMap: " PTR_FORMAT,
                r->index(), p2i(bottom), p2i(r->end()));
}

HeapWord* ShenandoahMarkingContext::top_bitmap(ShenandoahHeapRegion* r) {
  return _top_bitmaps[r->index()];
}

void ShenandoahMarkingContext::clear_bitmap(ShenandoahHeapRegion* r) {
  HeapWord* bottom = r->bottom();
  HeapWord* top_bitmap = _top_bitmaps[r->index()];

  log_debug(gc)("SMC:clear_bitmap for %s Region %zu, top_bitmap: " PTR_FORMAT,
                r->affiliation_name(), r->index(), p2i(top_bitmap));

  if (top_bitmap > bottom) {
    _mark_bit_map.clear_range_large(MemRegion(bottom, top_bitmap));
    _top_bitmaps[r->index()] = bottom;
  }

  assert(is_bitmap_range_within_region_clear(bottom, r->end()),
         "Region %zu should have no marks in bitmap", r->index());
}
