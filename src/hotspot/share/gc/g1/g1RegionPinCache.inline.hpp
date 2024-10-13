/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1REGIONPINCACHE_INLINE_HPP
#define SHARE_GC_G1_G1REGIONPINCACHE_INLINE_HPP

#include "gc/g1/g1RegionPinCache.hpp"

#include "gc/g1/g1CollectedHeap.inline.hpp"

inline void G1RegionPinCache::inc_count(uint region_idx) {
  if (region_idx == _region_idx) {
    ++_count;
  } else {
    flush_and_set(region_idx, (size_t)1);
  }
}

inline void G1RegionPinCache::dec_count(uint region_idx) {
  if (region_idx == _region_idx) {
    --_count;
  } else {
    flush_and_set(region_idx, ~(size_t)0);
  }
}

inline void G1RegionPinCache::flush_and_set(uint new_region_idx, size_t new_count) {
  if (_count != 0) {
    G1CollectedHeap::heap()->region_at(_region_idx)->add_pinned_object_count(_count);
  }
  _region_idx = new_region_idx;
  _count = new_count;
}

inline void G1RegionPinCache::flush() {
  flush_and_set(G1_NO_HRM_INDEX, 0);
}

#endif /* SHARE_GC_G1_G1REGIONPINCACHE_INLINE_HPP */
