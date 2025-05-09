/*
 * Copyright (c) 2018, 2019, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHMARKINGCONTEXT_INLINE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHMARKINGCONTEXT_INLINE_HPP

#include "gc/shenandoah/shenandoahMarkingContext.hpp"

#include "gc/shenandoah/shenandoahMarkBitMap.inline.hpp"
#include "logging/log.hpp"

inline bool ShenandoahMarkingContext::mark_strong(oop obj, bool& was_upgraded) {
  return !allocated_after_mark_start(obj) && _mark_bit_map.mark_strong(cast_from_oop<HeapWord*>(obj), was_upgraded);
}

inline bool ShenandoahMarkingContext::mark_weak(oop obj) {
  return !allocated_after_mark_start(obj) && _mark_bit_map.mark_weak(cast_from_oop<HeapWord *>(obj));
}

inline bool ShenandoahMarkingContext::is_marked(oop obj) const {
  return is_marked(cast_from_oop<HeapWord*>(obj));
}

inline bool ShenandoahMarkingContext::is_marked(HeapWord* raw_obj) const {
  return allocated_after_mark_start(raw_obj) || _mark_bit_map.is_marked(raw_obj);
}

inline bool ShenandoahMarkingContext::is_marked_strong(oop obj) const {
  return is_marked_strong(cast_from_oop<HeapWord*>(obj));
}

inline bool ShenandoahMarkingContext::is_marked_strong(HeapWord* raw_obj) const {
  return allocated_after_mark_start(raw_obj) || _mark_bit_map.is_marked_strong(raw_obj);
}

inline bool ShenandoahMarkingContext::is_marked_weak(oop obj) const {
  return allocated_after_mark_start(obj) || _mark_bit_map.is_marked_weak(cast_from_oop<HeapWord *>(obj));
}

inline bool ShenandoahMarkingContext::is_marked_or_old(oop obj) const {
  return is_marked(obj) || ShenandoahHeap::heap()->is_in_old_during_young_collection(obj);
}

inline bool ShenandoahMarkingContext::is_marked_strong_or_old(oop obj) const {
  return is_marked_strong(obj) || ShenandoahHeap::heap()->is_in_old_during_young_collection(obj);
}

inline HeapWord* ShenandoahMarkingContext::get_next_marked_addr(const HeapWord* start, const HeapWord* limit) const {
  return _mark_bit_map.get_next_marked_addr(start, limit);
}

inline bool ShenandoahMarkingContext::allocated_after_mark_start(oop obj) const {
  const HeapWord* addr = cast_from_oop<HeapWord*>(obj);
  return allocated_after_mark_start(addr);
}

inline bool ShenandoahMarkingContext::allocated_after_mark_start(const HeapWord* addr) const {
  uintx index = ((uintx) addr) >> ShenandoahHeapRegion::region_size_bytes_shift();
  HeapWord* top_at_mark_start = _top_at_mark_starts[index];
  const bool alloc_after_mark_start = addr >= top_at_mark_start;
  return alloc_after_mark_start;
}

inline void ShenandoahMarkingContext::capture_top_at_mark_start(ShenandoahHeapRegion *r) {
  if (!r->is_affiliated()) {
    // Non-affiliated regions do not need their TAMS updated
    return;
  }

  size_t idx = r->index();
  HeapWord* old_tams = _top_at_mark_starts_base[idx];
  HeapWord* new_tams = r->top();

  assert(new_tams >= old_tams,
         "Region %zu, TAMS updates should be monotonic: " PTR_FORMAT " -> " PTR_FORMAT,
         idx, p2i(old_tams), p2i(new_tams));
  assert((new_tams == r->bottom()) || (old_tams == r->bottom()) || (new_tams >= _top_bitmaps[idx]),
         "Region %zu, top_bitmaps updates should be monotonic: " PTR_FORMAT " -> " PTR_FORMAT,
         idx, p2i(_top_bitmaps[idx]), p2i(new_tams));
  assert(old_tams == r->bottom() || is_bitmap_range_within_region_clear(old_tams, new_tams),
         "Region %zu, bitmap should be clear while adjusting TAMS: " PTR_FORMAT " -> " PTR_FORMAT,
         idx, p2i(old_tams), p2i(new_tams));

  log_debug(gc)("Capturing TAMS for %s Region %zu, was: " PTR_FORMAT ", now: " PTR_FORMAT,
                r->affiliation_name(), idx, p2i(old_tams), p2i(new_tams));

  _top_at_mark_starts_base[idx] = new_tams;
  _top_bitmaps[idx] = new_tams;
}

inline void ShenandoahMarkingContext::reset_top_at_mark_start(ShenandoahHeapRegion* r) {
  _top_at_mark_starts_base[r->index()] = r->bottom();
}

inline HeapWord* ShenandoahMarkingContext::top_at_mark_start(const ShenandoahHeapRegion* r) const {
  return _top_at_mark_starts_base[r->index()];
}

inline void ShenandoahMarkingContext::reset_top_bitmap(ShenandoahHeapRegion* r) {
  assert(is_bitmap_range_within_region_clear(r->bottom(), r->end()),
         "Region %zu should have no marks in bitmap", r->index());
  _top_bitmaps[r->index()] = r->bottom();
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHMARKINGCONTEXT_INLINE_HPP
