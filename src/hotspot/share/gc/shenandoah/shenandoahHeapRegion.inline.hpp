/*
 * Copyright (c) 2015, 2019, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

HeapWord* ShenandoahHeapRegion::allocate_fill(size_t size) {
  shenandoah_assert_heaplocked_or_safepoint();
  assert(is_object_aligned(size), "alloc size breaks alignment: %zu", size);
  assert(size >= ShenandoahHeap::min_fill_size(), "Cannot fill unless min fill size");

  HeapWord* obj = stable_top();
  HeapWord* new_top = obj + size;
  ShenandoahHeap::fill_with_object(obj, size);
  set_top(new_top);

  assert(is_object_aligned(new_top), "new top breaks alignment: " PTR_FORMAT, p2i(new_top));
  assert(is_object_aligned(obj),     "obj is not aligned: "       PTR_FORMAT, p2i(obj));

  return obj;
}


HeapWord* ShenandoahHeapRegion::allocate(size_t size, const ShenandoahAllocRequest& req) {
  shenandoah_assert_heaplocked_or_safepoint();
  assert(!is_atomic_alloc_region(), "Must not");
  assert(is_object_aligned(size), "alloc size breaks alignment: %zu", size);

  HeapWord* obj = stable_top();
  if (pointer_delta(end(), obj) >= size) {
    make_regular_allocation(req.affiliation());
    adjust_alloc_metadata(req, size);

    HeapWord* new_top = obj + size;
    set_top(new_top);

    assert(is_object_aligned(new_top), "new top breaks alignment: " PTR_FORMAT, p2i(new_top));
    assert(is_object_aligned(obj),     "obj is not aligned: "       PTR_FORMAT, p2i(obj));

    return obj;
  } else {
    return nullptr;
  }
}

HeapWord* ShenandoahHeapRegion::allocate_atomic(const ShenandoahAllocRequest& req, bool &ready_for_replenish) {
  const size_t size = req.size();
  assert(is_object_aligned(size), "alloc size breaks alignment: %zu", size);

  // Relaxed read: the value is only the expected operand of try_allocate's release CAS, which
  // validates it. See atomic_top_relaxed().
  HeapWord* obj = atomic_top_relaxed();
  if (obj == nullptr) {
    // _atomic_top has been updated to nullptr, it is not allowed to do atomic alloc
    return nullptr;
  }

  // The loop always returns from within its body.
  for (;;) {
    size_t free_words = pointer_delta(end(), obj);
    if (free_words >= size) {
      if (try_allocate(obj /*value*/, size, obj /*reference*/)) {
        adjust_alloc_metadata(req, size);
        ready_for_replenish = (free_words - size) < ShenandoahHeap::plab_min_size();
        return obj;
      }
      if (obj == nullptr) {
        // _atomic_top has been updated to nullptr, it is not allowed to retry atomic alloc
        return nullptr;
      }
    } else {
      // Region cannot satisfy this request. Mark it for replenish only when it has no room for any
      // minimum LAB (truly full); otherwise leave it, since a smaller future request may still fit, and
      // retiring it here would discard usable capacity to one oversized request. Note free_words < size
      // here, so do not compute free_words - size (unsigned underflow).
      ready_for_replenish = free_words < ShenandoahHeap::plab_min_size();
      return nullptr;
    }
    SpinPause(); // Spin pause on contention.
  }
}

HeapWord* ShenandoahHeapRegion::allocate_lab_atomic(const ShenandoahAllocRequest& req, size_t &actual_size, bool &ready_for_replenish) {
  assert(req.is_lab_alloc(), "Only lab alloc");

  const size_t req_size = req.size();
  const size_t min_size = req.min_size();
  // Relaxed read: the value is only the expected operand of try_allocate's release CAS, which
  // validates it. See atomic_top_relaxed().
  HeapWord* obj = atomic_top_relaxed();
  if (obj == nullptr) {
    // _atomic_top has been updated to nullptr, it is not allowed to do atomic alloc
    return nullptr;
  }
  // The loop always returns from within its body.
  for (;;) {
    size_t adjusted_size = req_size;
    size_t free_words = pointer_delta(end(), obj);
    size_t aligned_free_words = align_down(free_words, MinObjAlignment);
    if (adjusted_size > aligned_free_words) {
      adjusted_size = aligned_free_words;
    }
    if (adjusted_size >= min_size) {
      if (try_allocate(obj /*value*/, adjusted_size, obj /*reference*/)) {
        actual_size = adjusted_size;
        adjust_alloc_metadata(req, adjusted_size);
        ready_for_replenish = free_words - adjusted_size < ShenandoahHeap::plab_min_size();
        return obj;
      }

      if (obj == nullptr) {
        // _atomic_top has been updated to nullptr, it is not allowed to retry atomic alloc
        return nullptr;
      }
    } else {
      log_trace(gc, free)("Failed to shrink TLAB or GCLAB request (%zu) in region %zu to %zu"
                          " because min_size() is %zu", req_size, index(), adjusted_size, min_size);
      // Region cannot satisfy even the minimum LAB. Mark for replenish only when it has no room for
      // any minimum LAB (truly full); free_words is the region's actual remaining capacity here.
      ready_for_replenish = free_words < ShenandoahHeap::plab_min_size();
      return nullptr;
    }
    SpinPause(); // Spin pause on contention.
  }
}

bool ShenandoahHeapRegion::try_allocate(HeapWord* const obj, size_t const size, HeapWord* &prior_atomic_top) {
  HeapWord* new_top = obj + size;
  if ((prior_atomic_top = _atomic_top.compare_exchange(obj, new_top, memory_order_release)) == obj) {
    assert(is_object_aligned(new_top), "new top breaks alignment: " PTR_FORMAT, p2i(new_top));
    assert(is_object_aligned(obj),     "obj is not aligned: "       PTR_FORMAT, p2i(obj));
    return true;
  }
  return false;
}

inline void ShenandoahHeapRegion::adjust_alloc_metadata(const ShenandoahAllocRequest &req, size_t size) {
  // Only need to update alloc metadata for lab alloc, shared alloc is counted implicitly by tlab/gclab allocs
  switch (req.type()) {
    case ShenandoahAllocRequest::_alloc_tlab:
      _tlab_allocs.add_then_fetch(size, memory_order_relaxed);
      break;
    case ShenandoahAllocRequest::_alloc_gclab:
      _gclab_allocs.add_then_fetch(size, memory_order_relaxed);
      break;
    case ShenandoahAllocRequest::_alloc_plab:
      _plab_allocs.add_then_fetch(size, memory_order_relaxed);
      break;
    default:
      assert(!req.is_lab_alloc(), "Unrecognized LAB allocation type");
      break;
  }
}

inline void ShenandoahHeapRegion::increase_live_data_alloc_words(size_t s) {
  internal_increase_live_data(s);
}

inline void ShenandoahHeapRegion::increase_live_data_gc_words(size_t s) {
  internal_increase_live_data(s);
}

inline void ShenandoahHeapRegion::internal_increase_live_data(size_t s) {
  _live_data.add_then_fetch(s, memory_order_relaxed);
}

inline void ShenandoahHeapRegion::clear_live_data() {
  _live_data.store_relaxed((size_t)0);
  _promoted_in_place = false;
}

inline size_t ShenandoahHeapRegion::get_live_data_words() const {
  return _live_data.load_relaxed();
}

inline size_t ShenandoahHeapRegion::get_live_data_bytes() const {
  return get_live_data_words() * HeapWordSize;
}

inline size_t ShenandoahHeapRegion::get_mixed_candidate_live_data_bytes() const {
  shenandoah_assert_heaplocked_or_safepoint();
  assert(used() >= _mixed_candidate_garbage_words * HeapWordSize, "used must exceed garbage");
  return used() - _mixed_candidate_garbage_words * HeapWordSize;
}

inline size_t ShenandoahHeapRegion::get_mixed_candidate_live_data_words() const {
  shenandoah_assert_heaplocked_or_safepoint();
  assert(used() >= _mixed_candidate_garbage_words * HeapWordSize, "used must exceed garbage");
  return used() / HeapWordSize - _mixed_candidate_garbage_words;
}

inline void ShenandoahHeapRegion::capture_mixed_candidate_garbage() {
  shenandoah_assert_heaplocked_or_safepoint();
  _mixed_candidate_garbage_words = garbage() / HeapWordSize;
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
  HeapWord* watermark = _update_watermark.load_acquire();
  assert(bottom() <= watermark && watermark <= top(), "within bounds");
  return watermark;
}

inline void ShenandoahHeapRegion::set_update_watermark(HeapWord* w) {
  assert(bottom() <= w && w <= top(), "within bounds");
  _update_watermark.release_store(w);
}

// Fast version that avoids synchronization, only to be used at safepoints.
inline void ShenandoahHeapRegion::set_update_watermark_at_safepoint(HeapWord* w) {
  assert(bottom() <= w && w <= top(), "within bounds");
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at Shenandoah safepoint");
  _update_watermark.store_relaxed(w);
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
  assert(!is_atomic_alloc_region(), "Must not");
  assert(atomic_top() == nullptr, "Must be");
  _top_before_promoted = stable_top();
}

inline void ShenandoahHeapRegion::restore_top_before_promote() {
  assert(!is_atomic_alloc_region(), "Must not");
  assert(atomic_top() == nullptr, "Must be");
  _top = _top_before_promoted;
  _top_before_promoted = nullptr;
 }


#endif // SHARE_GC_SHENANDOAH_SHENANDOAHHEAPREGION_INLINE_HPP
