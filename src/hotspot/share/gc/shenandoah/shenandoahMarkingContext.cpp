/*
 * Copyright (c) 2018, Red Hat, Inc. All rights reserved.
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
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.hpp"

ShenandoahMarkingContext::ShenandoahMarkingContext(MemRegion heap_region, MemRegion bitmap_region, size_t num_regions) :
  _top_bitmaps(NEW_C_HEAP_ARRAY(HeapWord*, num_regions, mtGC)),
  _top_at_mark_starts_base(NEW_C_HEAP_ARRAY(HeapWord*, num_regions, mtGC)),
  _top_at_mark_starts(_top_at_mark_starts_base -
                      ((uintx) heap_region.start() >> ShenandoahHeapRegion::region_size_bytes_shift())) {
  _mark_bit_map.initialize(heap_region, bitmap_region);
}

bool ShenandoahMarkingContext::is_bitmap_clear() const {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  size_t num_regions = heap->num_regions();
  for (size_t idx = 0; idx < num_regions; idx++) {
    ShenandoahHeapRegion* r = heap->get_region(idx);
    if (heap->is_bitmap_slice_committed(r) && !is_bitmap_clear_range(r->bottom(), r->end())) {
      return false;
    }
  }
  return true;
}

bool ShenandoahMarkingContext::is_bitmap_clear_range(HeapWord* start, HeapWord* end) const {
  return _mark_bit_map.get_next_marked_addr(start, end) == end;
}

void ShenandoahMarkingContext::initialize_top_at_mark_start(ShenandoahHeapRegion* r) {
  size_t idx = r->region_number();
  HeapWord *bottom = r->bottom();
  _top_at_mark_starts_base[idx] = bottom;
  _top_bitmaps[idx] = bottom;
}

void ShenandoahMarkingContext::capture_top_at_mark_start(ShenandoahHeapRegion *r) {
  size_t region_number = r->region_number();
  HeapWord* old_tams = _top_at_mark_starts_base[region_number];
  HeapWord* new_tams = r->top();

  assert(new_tams >= old_tams,
         "Region " SIZE_FORMAT", TAMS updates should be monotonic: " PTR_FORMAT " -> " PTR_FORMAT,
         region_number, p2i(old_tams), p2i(new_tams));
  assert(is_bitmap_clear_range(old_tams, new_tams),
         "Region " SIZE_FORMAT ", bitmap should be clear while adjusting TAMS: " PTR_FORMAT " -> " PTR_FORMAT,
         region_number, p2i(old_tams), p2i(new_tams));

  _top_at_mark_starts_base[region_number] = new_tams;
  _top_bitmaps[region_number] = new_tams;
}

void ShenandoahMarkingContext::reset_top_at_mark_start(ShenandoahHeapRegion* r) {
  _top_at_mark_starts_base[r->region_number()] = r->bottom();
}

HeapWord* ShenandoahMarkingContext::top_at_mark_start(ShenandoahHeapRegion* r) const {
  return _top_at_mark_starts_base[r->region_number()];
}

void ShenandoahMarkingContext::reset_top_bitmap(ShenandoahHeapRegion* r) {
  assert(is_bitmap_clear_range(r->bottom(), r->end()),
         "Region " SIZE_FORMAT " should have no marks in bitmap", r->region_number());
  _top_bitmaps[r->region_number()] = r->bottom();
}

void ShenandoahMarkingContext::clear_bitmap(ShenandoahHeapRegion* r) {
  HeapWord* bottom = r->bottom();
  HeapWord* top_bitmap = _top_bitmaps[r->region_number()];
  if (top_bitmap > bottom) {
    _mark_bit_map.clear_range_large(MemRegion(bottom, top_bitmap));
    _top_bitmaps[r->region_number()] = bottom;
  }
  assert(is_bitmap_clear_range(bottom, r->end()),
         "Region " SIZE_FORMAT " should have no marks in bitmap", r->region_number());
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
