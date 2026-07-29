/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHREGIONPINCACHE_INLINE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHREGIONPINCACHE_INLINE_HPP

#include "gc/shenandoah/shenandoahRegionPinCache.hpp"

#include "gc/shenandoah/shenandoahHeap.inline.hpp"

inline void ShenandoahRegionPinCache::inc_count(size_t region_idx) {
  if (region_idx == _region_idx) {
    ++_count;
  } else {
    flush_and_set(region_idx, (size_t)1);
  }
}

inline void ShenandoahRegionPinCache::dec_count(size_t region_idx) {
  if (region_idx == _region_idx) {
    --_count;
  } else {
    flush_and_set(region_idx, ~(size_t)0);
  }
}

inline void ShenandoahRegionPinCache::flush_and_set(size_t new_region_idx, size_t new_count) {
  if (_count != 0) {
    ShenandoahHeapRegion* r = ShenandoahHeap::heap()->get_region(_region_idx);
    assert(r != nullptr, "Region %zu must exist", _region_idx);
    r->add_pinned_object_count(_count);
  }
  _region_idx = new_region_idx;
  _count = new_count;
}

inline void ShenandoahRegionPinCache::flush() {
  flush_and_set(SIZE_MAX, 0);
}

#endif /* SHARE_GC_SHENANDOAH_SHENANDOAHREGIONPINCACHE_INLINE_HPP */
