/*
 * Copyright (c) 2015, 2019, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHHEAPREGION_INLINE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHHEAPREGION_INLINE_HPP

#include "gc/shenandoah/shenandoahHeapRegion.hpp"

#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahPacer.inline.hpp"
#include "runtime/atomic.hpp"

// If next available memory is not aligned on address that is multiple of alignment, fill the empty space
// so that returned object is aligned on an address that is a multiple of alignment_in_words.  Requested
// size is in words.  It is assumed that this->is_old().  A pad object is allocated, filled, and registered
// if necessary to assure the new allocation is properly aligned.
HeapWord* ShenandoahHeapRegion::allocate_aligned(size_t size, ShenandoahAllocRequest &req, size_t alignment_in_bytes) {
  shenandoah_assert_heaplocked_or_safepoint();
  assert(req.is_lab_alloc(), "allocate_aligned() only applies to LAB allocations");
  assert(is_object_aligned(size), "alloc size breaks alignment: " SIZE_FORMAT, size);
  assert(is_old(), "aligned allocations are only taken from OLD regions to support PLABs");

  HeapWord* orig_top = top();
  size_t addr_as_int = (uintptr_t) orig_top;

  // unalignment_bytes is the amount by which current top() exceeds the desired alignment point.  We subtract this amount
  // from alignment_in_bytes to determine padding required to next alignment point.

  // top is HeapWord-aligned so unalignment_bytes is a multiple of HeapWordSize
  size_t unalignment_bytes = addr_as_int % alignment_in_bytes;
  size_t unalignment_words = unalignment_bytes / HeapWordSize;

  size_t pad_words;
  HeapWord* aligned_obj;
  if (unalignment_words > 0) {
    pad_words = (alignment_in_bytes / HeapWordSize) - unalignment_words;
    if (pad_words < ShenandoahHeap::min_fill_size()) {
      pad_words += (alignment_in_bytes / HeapWordSize);
    }
    aligned_obj = orig_top + pad_words;
  } else {
    pad_words = 0;
    aligned_obj = orig_top;
  }

  if (pointer_delta(end(), aligned_obj) < size) {
    size = pointer_delta(end(), aligned_obj);
    // Force size to align on multiple of alignment_in_bytes
    size_t byte_size = size * HeapWordSize;
    size_t excess_bytes = byte_size % alignment_in_bytes;
    // Note: excess_bytes is a multiple of HeapWordSize because it is the difference of HeapWord-aligned end
    //       and proposed HeapWord-aligned object address.
    if (excess_bytes > 0) {
      size -= excess_bytes / HeapWordSize;
    }
  }

  // Both originally requested size and adjusted size must be properly aligned
  assert ((size * HeapWordSize) % alignment_in_bytes == 0, "Size must be multiple of alignment constraint");
  if (size >= req.min_size()) {
    // Even if req.min_size() is not a multiple of card size, we know that size is.
    if (pad_words > 0) {
      assert(pad_words >= ShenandoahHeap::min_fill_size(), "pad_words expanded above to meet size constraint");
      ShenandoahHeap::fill_with_object(orig_top, pad_words);
      ShenandoahHeap::heap()->card_scan()->register_object(orig_top);
    }

    make_regular_allocation(req.affiliation());
    adjust_alloc_metadata(req.type(), size);

    HeapWord* new_top = aligned_obj + size;
    assert(new_top <= end(), "PLAB cannot span end of heap region");
    set_top(new_top);
    req.set_actual_size(size);
    req.set_waste(pad_words);
    assert(is_object_aligned(new_top), "new top breaks alignment: " PTR_FORMAT, p2i(new_top));
    assert(is_aligned(aligned_obj, alignment_in_bytes), "obj is not aligned: " PTR_FORMAT, p2i(aligned_obj));
    return aligned_obj;
  } else {
    return nullptr;
  }
}

HeapWord* ShenandoahHeapRegion::allocate(size_t size, ShenandoahAllocRequest req) {
  shenandoah_assert_heaplocked_or_safepoint();
  assert(is_object_aligned(size), "alloc size breaks alignment: " SIZE_FORMAT, size);

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
  if (ShenandoahPacing) {
    ShenandoahHeap::heap()->pacer()->report_mark(s);
  }
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
         "Live Data must be a subset of used() live: " SIZE_FORMAT " used: " SIZE_FORMAT,
         get_live_data_bytes(), used());

  size_t result = used() - get_live_data_bytes();
  return result;
}

inline size_t ShenandoahHeapRegion::garbage_before_padded_for_promote() const {
  assert(get_top_before_promote() != nullptr, "top before promote should not equal null");
  size_t used_before_promote = byte_size(bottom(), get_top_before_promote());
  assert(used_before_promote >= get_live_data_bytes(),
         "Live Data must be a subset of used before promotion live: " SIZE_FORMAT " used: " SIZE_FORMAT,
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
