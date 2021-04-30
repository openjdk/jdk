/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef SHARE_GC_Z_ZBITMAP_INLINE_HPP
#define SHARE_GC_Z_ZBITMAP_INLINE_HPP

#include "gc/z/zBitMap.hpp"

#include "runtime/atomic.hpp"
#include "utilities/bitMap.inline.hpp"
#include "utilities/debug.hpp"

inline ZMovableBitMap::ZMovableBitMap() :
    CHeapBitMap(mtGC) {}

inline ZMovableBitMap::ZMovableBitMap(ZMovableBitMap&& bitmap) :
    CHeapBitMap(mtGC) {
  update(bitmap.map(), bitmap.size());
  bitmap.update(nullptr, 0);
}

inline ZBitMap::ZBitMap(idx_t size_in_bits) :
    CHeapBitMap(size_in_bits, mtGC, false /* clear */) {}

inline ZBitMap::ZBitMap(const ZBitMap& other) :
    CHeapBitMap(other.size(), mtGC, false /* clear */) {
  memcpy(map(), other.map(), size_in_bytes());
}

inline BitMap::bm_word_t ZBitMap::bit_mask_pair(idx_t bit) {
  assert(bit_in_word(bit) < BitsPerWord - 1, "Invalid bit index");
  return (bm_word_t)3 << bit_in_word(bit);
}

inline bool ZBitMap::par_set_bit_pair_finalizable(idx_t bit, bool& inc_live) {
  inc_live = par_set_bit(bit);
  return inc_live;
}

inline bool ZBitMap::par_set_bit_pair_strong(idx_t bit, bool& inc_live) {
  verify_index(bit);
  volatile bm_word_t* const addr = word_addr(bit);
  const bm_word_t pair_mask = bit_mask_pair(bit);
  bm_word_t old_val = *addr;

  do {
    const bm_word_t new_val = old_val | pair_mask;
    if (new_val == old_val) {
      // Someone else beat us to it
      inc_live = false;
      return false;
    }
    const bm_word_t cur_val = Atomic::cmpxchg(addr, old_val, new_val);
    if (cur_val == old_val) {
      // Success
      const bm_word_t marked_mask = bit_mask(bit);
      inc_live = !(old_val & marked_mask);
      return true;
    }

    // The value changed, retry
    old_val = cur_val;
  } while (true);
}

inline bool ZBitMap::par_set_bit_pair(idx_t bit, bool finalizable, bool& inc_live) {
  if (finalizable) {
    return par_set_bit_pair_finalizable(bit, inc_live);
  } else {
    return par_set_bit_pair_strong(bit, inc_live);
  }
}

inline ZBitMap::ReverseIterator::ReverseIterator(BitMap* bitmap) :
    ZBitMap::ReverseIterator(bitmap, 0, bitmap->size()) {}

inline ZBitMap::ReverseIterator::ReverseIterator(BitMap* bitmap, BitMap::idx_t beg, BitMap::idx_t end) :
    _bitmap(bitmap),
    _beg(beg),
    _end(end) {}

inline void ZBitMap::ReverseIterator::reset(BitMap::idx_t beg, BitMap::idx_t end) {
  assert(beg < _bitmap->size(), "beg index out of bounds");
  assert(end >= beg && end <= _bitmap->size(), "end index out of bounds");
  _beg = beg;
  _end = end;
}

inline void ZBitMap::ReverseIterator::reset(BitMap::idx_t end) {
  assert(end >= _beg && end <= _bitmap->size(), "end index out of bounds");
  _end = end;
}

inline bool ZBitMap::ReverseIterator::next(BitMap::idx_t *index) {
  BitMap::ReverseIterator iter(*_bitmap, _beg, _end);
  if (iter.is_empty()) {
    return false;
  }

  *index = _end = iter.index();
  return true;
}

#endif // SHARE_GC_Z_ZBITMAP_INLINE_HPP
