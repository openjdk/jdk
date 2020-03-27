/*
 * Copyright (c) 2015, 2019, Red Hat, Inc. All rights reserved.
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

#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahPacer.inline.hpp"
#include "runtime/atomic.hpp"

HeapWord* ShenandoahHeapRegion::allocate(size_t size, ShenandoahAllocRequest::Type type) {
  shenandoah_assert_heaplocked_or_safepoint();
  assert(is_object_aligned(size), "alloc size breaks alignment: " SIZE_FORMAT, size);

  HeapWord* obj = top();
  if (pointer_delta(end(), obj) >= size) {
    make_regular_allocation();
    adjust_alloc_metadata(type, size);

    HeapWord* new_top = obj + size;
    set_top(new_top);

    assert(is_object_aligned(new_top), "new top breaks alignment: " PTR_FORMAT, p2i(new_top));
    assert(is_object_aligned(obj),     "obj is not aligned: "       PTR_FORMAT, p2i(obj));

    return obj;
  } else {
    return NULL;
  }
}

inline void ShenandoahHeapRegion::adjust_alloc_metadata(ShenandoahAllocRequest::Type type, size_t size) {
  switch (type) {
    case ShenandoahAllocRequest::_alloc_shared:
    case ShenandoahAllocRequest::_alloc_shared_gc:
      _shared_allocs += size;
      break;
    case ShenandoahAllocRequest::_alloc_tlab:
      _tlab_allocs += size;
      break;
    case ShenandoahAllocRequest::_alloc_gclab:
      _gclab_allocs += size;
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
  size_t new_live_data = Atomic::add(&_live_data, s);
#ifdef ASSERT
  size_t live_bytes = new_live_data * HeapWordSize;
  size_t used_bytes = used();
  assert(live_bytes <= used_bytes,
         "can't have more live data than used: " SIZE_FORMAT ", " SIZE_FORMAT, live_bytes, used_bytes);
#endif
}

inline uint64_t ShenandoahHeapRegion::seqnum_last_alloc_mutator() const {
  assert(ShenandoahHeap::heap()->is_traversal_mode(), "Sanity");
  return _seqnum_last_alloc_mutator;
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHHEAPREGION_INLINE_HPP
