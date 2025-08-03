/*
 * Copyright (c) 2015, 2019, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHHEAPREGION_INLINE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHHEAPREGION_INLINE_HPP

#include "gc/shenandoah/shenandoahHeapRegion.hpp"

#include "gc/shenandoah/shenandoahGenerationalHeap.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "runtime/atomic.hpp"

HeapWord* ShenandoahHeapRegion::allocate_aligned(size_t size, ShenandoahAllocRequest &req, size_t alignment_in_bytes) {
  shenandoah_assert_heaplocked_or_safepoint();
  assert(req.is_lab_alloc(), "allocate_aligned() only applies to LAB allocations");
  assert(is_object_aligned(size), "alloc size breaks alignment: %zu", size);
  assert(is_old(), "aligned allocations are only taken from OLD regions to support PLABs");
  assert(is_aligned(alignment_in_bytes, HeapWordSize), "Expect heap word alignment");

  HeapWord* orig_top = top();
  size_t alignment_in_words = alignment_in_bytes / HeapWordSize;

  // unalignment_words is the amount by which current top() exceeds the desired alignment point.  We subtract this amount
  // from alignment_in_words to determine padding required to next alignment point.

  HeapWord* aligned_obj = (HeapWord*) align_up(orig_top, alignment_in_bytes);
  size_t pad_words = aligned_obj - orig_top;
  if ((pad_words > 0) && (pad_words < ShenandoahHeap::min_fill_size())) {
    pad_words += alignment_in_words;
    aligned_obj += alignment_in_words;
  }

  if (pointer_delta(end(), aligned_obj) < size) {
    // Shrink size to fit within available space and align it
    size = pointer_delta(end(), aligned_obj);
    size = align_down(size, alignment_in_words);
  }

  // Both originally requested size and adjusted size must be properly aligned
  assert (is_aligned(size, alignment_in_words), "Size must be multiple of alignment constraint");
  if (size >= req.min_size()) {
    // Even if req.min_size() may not be a multiple of card size, we know that size is.
    if (pad_words > 0) {
      assert(pad_words >= ShenandoahHeap::min_fill_size(), "pad_words expanded above to meet size constraint");
      ShenandoahHeap::fill_with_object(orig_top, pad_words);
      ShenandoahGenerationalHeap::heap()->old_generation()->card_scan()->register_object(orig_top);
    }

    make_regular_allocation(req.affiliation());
    adjust_alloc_metadata(req.type(), size);

    HeapWord* new_top = aligned_obj + size;
    assert(new_top <= end(), "PLAB cannot span end of heap region");
    set_top(new_top);
    // We do not req.set_actual_size() here.  The caller sets it.
    req.set_waste(pad_words);
    assert(is_object_aligned(new_top), "new top breaks alignment: " PTR_FORMAT, p2i(new_top));
    assert(is_aligned(aligned_obj, alignment_in_bytes), "obj is not aligned: " PTR_FORMAT, p2i(aligned_obj));
    return aligned_obj;
  } else {
    // The aligned size that fits in this region is smaller than min_size, so don't align top and don't allocate.  Return failure.
    return nullptr;
  }
}

HeapWord* ShenandoahHeapRegion::allocate(size_t size, const ShenandoahAllocRequest& req) {
  shenandoah_assert_heaplocked_or_safepoint();
  assert(is_object_aligned(size), "alloc size breaks alignment: %zu", size);

  HeapWord* obj = top();
  if (pointer_delta(end(), obj) >= size) {
    make_regular_allocation(req.affiliation());
    adjust_alloc_metadata(req.type(), size);

    HeapWord* new_top = obj + size;
    set_top(new_top);

    assert(is_object_aligned(new_top), "new top breaks alignment: " PTR_FORMAT, p2i(new_top));
    assert(is_object_aligned(obj),     "obj is not aligned: "       PTR_FORMAT, p2i(obj));

    return obj;
  } else {
    return nullptr;
  }
}

inline void ShenandoahHeapRegion::adjust_alloc_metadata(ShenandoahAllocRequest::Type type, size_t size) {
  switch (type) {
    case ShenandoahAllocRequest::_alloc_shared:
    case ShenandoahAllocRequest::_alloc_shared_gc:
      // Counted implicitly by tlab/gclab allocs
      break;
    case ShenandoahAllocRequest::_alloc_tlab:
      _tlab_allocs += size;
      break;
    case ShenandoahAllocRequest::_alloc_gclab:
      _gclab_allocs += size;
      break;
    case ShenandoahAllocRequest::_alloc_plab:
      _plab_allocs += size;
      break;
    default:
      ShouldNotReachHere();
  }
}

inline void ShenandoahHeapRegion::increase_live_data_alloc_words(size_t s) {
  internal_increase_live_data(s);
}

inline void ShenandoahHeapRegion::increase_live_data_gc_words(size_t s) {
  internal_increase_live_data(s);
}

inline void ShenandoahHeapRegion::internal_increase_live_data(size_t s) {
  size_t new_live_data = Atomic::add(&_live_data, s, memory_order_relaxed);
}

inline void ShenandoahHeapRegion::clear_live_data() {
  Atomic::store(&_live_data, (size_t)0);
}

inline size_t ShenandoahHeapRegion::get_live_data_words() const {
  return Atomic::load(&_live_data);
}

inline size_t ShenandoahHeapRegion::get_live_data_bytes() const {
  return get_live_data_words() * HeapWordSize;
}

inline bool ShenandoahHeapRegion::has_live() const {
  return get_live_data_words() != 0;
}

inline size_t ShenandoahHeapRegion::garbage() const {
  assert(used() >= get_live_data_bytes(),
         "Live Data must be a subset of used() live: %zu used: %zu",
         get_live_data_bytes(), used());

  size_t result = used() - get_live_data_bytes();
  return result;
}

inline size_t ShenandoahHeapRegion::garbage_before_padded_for_promote() const {
  assert(get_top_before_promote() != nullptr, "top before promote should not equal null");
  size_t used_before_promote = byte_size(bottom(), get_top_before_promote());
  assert(used_before_promote >= get_live_data_bytes(),
         "Live Data must be a subset of used before promotion live: %zu used: %zu",
         get_live_data_bytes(), used_before_promote);
  size_t result = used_before_promote - get_live_data_bytes();
  return result;

}

inline HeapWord* ShenandoahHeapRegion::get_update_watermark() const {
  HeapWord* watermark = Atomic::load_acquire(&_update_watermark);
  assert(bottom() <= watermark && watermark <= top(), "within bounds");
  return watermark;
}

inline void ShenandoahHeapRegion::set_update_watermark(HeapWord* w) {
  assert(bottom() <= w && w <= top(), "within bounds");
  Atomic::release_store(&_update_watermark, w);
}

// Fast version that avoids synchronization, only to be used at safepoints.
inline void ShenandoahHeapRegion::set_update_watermark_at_safepoint(HeapWord* w) {
  assert(bottom() <= w && w <= top(), "within bounds");
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at Shenandoah safepoint");
  _update_watermark = w;
}

inline ShenandoahAffiliation ShenandoahHeapRegion::affiliation() const {
  return ShenandoahHeap::heap()->region_affiliation(this);
}

inline const char* ShenandoahHeapRegion::affiliation_name() const {
  return shenandoah_affiliation_name(affiliation());
}

inline bool ShenandoahHeapRegion::is_young() const {
  return affiliation() == YOUNG_GENERATION;
}

inline bool ShenandoahHeapRegion::is_old() const {
  return affiliation() == OLD_GENERATION;
}

inline bool ShenandoahHeapRegion::is_affiliated() const {
  return affiliation() != FREE;
}

inline void ShenandoahHeapRegion::save_top_before_promote() {
  _top_before_promoted = _top;
}

inline void ShenandoahHeapRegion::restore_top_before_promote() {
  _top = _top_before_promoted;
  _top_before_promoted = nullptr;
 }


#endif // SHARE_GC_SHENANDOAH_SHENANDOAHHEAPREGION_INLINE_HPP
