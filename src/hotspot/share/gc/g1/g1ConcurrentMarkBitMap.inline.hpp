/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1CONCURRENTMARKBITMAP_INLINE_HPP
#define SHARE_GC_G1_G1CONCURRENTMARKBITMAP_INLINE_HPP

#include "gc/g1/g1ConcurrentMarkBitMap.hpp"

#include "gc/shared/markBitMap.inline.hpp"
#include "memory/memRegion.hpp"
#include "utilities/align.hpp"
#include "utilities/bitMap.inline.hpp"
#include "gc/g1/g1ConcurrentRefine.hpp"

inline void G1CMBackScanSkipTable::mark(HeapWord* const addr) {
  if (!get_by_address(addr)) {
    set_by_address(addr, true);
  }
}

inline HeapWord* G1CMBackScanSkipTable::find_marked_area(HeapWord* const bottom, HeapWord* const start) const {
  assert(bottom != nullptr, "must be");
  assert(is_aligned(bottom, mapping_granularity()), "must be");
  assert(is_aligned(start, mapping_granularity()), "must be");

  bool* cur = address_mapped_to(start - 1);
  bool* bot = address_mapped_to(bottom);
  assert(cur >= bot, "must be");

  while (cur >= bot) {
    if (*cur) {
      idx_t index = pointer_delta(cur, bot, sizeof(bool));
      HeapWord* result = bottom + index * mapping_granularity() / HeapWordSize;
      return result;
    }
    cur--;
  }
  return nullptr;
}

inline bool G1CMBitMap::is_marked(oop obj) const { return _bitmap.is_marked(obj); }
inline bool G1CMBitMap::is_marked(HeapWord* addr) const { return _bitmap.is_marked(addr); }

inline bool G1CMBitMap::iterate(MarkBitMapClosure* cl, MemRegion mr) {
  return _bitmap.iterate(cl, mr);
}

inline HeapWord* G1CMBitMap::get_next_marked_addr(const HeapWord* addr,
                                                  HeapWord* const limit) const {
  return _bitmap.get_next_marked_addr(addr, limit);
}

inline HeapWord* G1CMBitMap::get_prev_marked_addr(HeapWord* const limit,
                                                  const HeapWord* addr) const {
  const size_t BackSkipGranularity = _back_scan_skip_table.mapping_granularity();
  assert((uintptr_t)addr >= BackSkipGranularity, "must be");

  // Scan at least half of the backskip size immediately before trying to use it
  // as setup is a bit expensive and it is extremely likely that there is a marked
  // object in close vicinity.
  HeapWord* scan_until_directly = MAX2(limit,
                                       (HeapWord*)align_down((uintptr_t)addr - BackSkipGranularity / 2, BackSkipGranularity));
  HeapWord* result = _bitmap.get_prev_marked_addr(scan_until_directly, addr);
  if (result == nullptr) {
    // No previous marked object found when scanning until scan_until_directly. If scan_until_directly
    // is limit, then we are done - we could not find any mark in this region.
    if (scan_until_directly == limit) {
      return nullptr;
    }
    // Then use the back skip table to jump over large areas in the bitmap.
    HeapWord* bottom_with_mark = _back_scan_skip_table.find_marked_area(limit, scan_until_directly);
    assert(bottom_with_mark < scan_until_directly, "must scan backward");
    // If no mark found with the backskip table either, we are done.
    if (bottom_with_mark == nullptr) {
      return nullptr;
    }
    // In the current backskip area there must be a mark. Find the first on the bitmap.
    result = _bitmap.get_prev_marked_addr(bottom_with_mark, bottom_with_mark + BackSkipGranularity / HeapWordSize);
    assert(result != nullptr, "must be");
    assert(result >= bottom_with_mark, "result " PTR_FORMAT " beyond lower range " PTR_FORMAT,
           p2i(result), p2i(bottom_with_mark));
    assert(_bitmap.get_prev_marked_addr(result, addr) == result, "scanning directly must yield same result");
  }

  return result;
}

inline void G1CMBitMap::clear(HeapWord* addr) { _bitmap.clear(addr); }
inline void G1CMBitMap::clear(oop obj) { _bitmap.clear(obj); }
inline bool G1CMBitMap::par_mark(HeapWord* addr) { return _bitmap.par_mark(addr); }
inline bool G1CMBitMap::par_mark(oop obj) { return _bitmap.par_mark(obj); }

inline void G1CMBitMap::update_back_skip_table(oop obj) {
  _back_scan_skip_table.mark(cast_from_oop<HeapWord*>(obj));
}

inline void G1CMBitMap::clear_range(MemRegion mr) {
  _bitmap.clear_range(mr);
  _back_scan_skip_table.clear_range(mr);
}

#endif // SHARE_GC_G1_G1CONCURRENTMARKBITMAP_INLINE_HPP
