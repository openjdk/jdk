/*
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHSIMPLEBITMAP_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHSIMPLEBITMAP_HPP

#include "gc/shenandoah/shenandoahAsserts.hpp"

#include <cstddef>

// TODO: Merge the enhanced capabilities of ShenandoahSimpleBitMap into src/hotspot/share/utilities/bitMap.hpp
//       and deprecate ShenandoahSimpleBitMap.  The key enhanced capabilities to be integrated include:
//
//   1. Allow searches from high to low memory (when biasing allocations towards the top of the heap)
//   2. Allow searches for clusters of contiguous set bits (to expedite allocation for humongous objects)
//
// idx_t is defined here as ssize_t.  In src/hotspot/share/utiliities/bitMap.hpp, idx is defined as size_t.
// This is a significant incompatibility.
//
// The API and internal implementation of ShenandoahSimpleBitMap and ShenandoahRegionPartitions use idx_t to
// represent index, even though index is "inherently" unsigned.  There are two reasons for this choice:
//  1. We use -1 as a sentinel value to represent empty partitions.  This same value may be used to represent
//     failure to find a previous set bit or previous range of set bits.
//  2. Certain loops are written most naturally if the iterator, which may hold the sentinel -1 value, can be
//     declared as signed and the terminating condition can be < 0.

typedef ssize_t idx_t;

// ShenandoahSimpleBitMap resembles CHeapBitMap but adds missing support for find_first_consecutive_set_bits() and
// find_last_consecutive_set_bits.  An alternative refactoring of code would subclass CHeapBitMap, but this might
// break abstraction rules, because efficient implementation requires assumptions about superclass internals that
// might be violated through future software maintenance.
class ShenandoahSimpleBitMap {
  const idx_t _num_bits;
  const size_t _num_words;
  uintx* const _bitmap;

public:
  ShenandoahSimpleBitMap(size_t num_bits);

  ~ShenandoahSimpleBitMap();

  void clear_all() {
    for (size_t i = 0; i < _num_words; i++) {
      _bitmap[i] = 0;
    }
  }

private:

  // Count consecutive ones in forward order, starting from start_idx.  Requires that there is at least one zero
  // between start_idx and index value (_num_bits - 1), inclusive.
  size_t count_leading_ones(idx_t start_idx) const;

  // Count consecutive ones in reverse order, starting from last_idx.  Requires that there is at least one zero
  // between last_idx and index value zero, inclusive.
  size_t count_trailing_ones(idx_t last_idx) const;

  bool is_forward_consecutive_ones(idx_t start_idx, idx_t count) const;
  bool is_backward_consecutive_ones(idx_t last_idx, idx_t count) const;

  static inline uintx tail_mask(uintx bit_number);

public:

  inline idx_t aligned_index(idx_t idx) const {
    assert((idx >= 0) && (idx < _num_bits), "precondition");
    idx_t array_idx = idx & ~(BitsPerWord - 1);
    return array_idx;
  }

  inline constexpr idx_t alignment() const {
    return BitsPerWord;
  }

  // For testing
  inline idx_t size() const {
    return _num_bits;
  }

  // Return the word that holds idx bit and its neighboring bits.
  inline uintx bits_at(idx_t idx) const {
    assert((idx >= 0) && (idx < _num_bits), "precondition");
    idx_t array_idx = idx >> LogBitsPerWord;
    return _bitmap[array_idx];
  }

  inline void set_bit(idx_t idx) {
    assert((idx >= 0) && (idx < _num_bits), "precondition");
    size_t array_idx = idx >> LogBitsPerWord;
    uintx bit_number = idx & (BitsPerWord - 1);
    uintx the_bit = nth_bit(bit_number);
    _bitmap[array_idx] |= the_bit;
  }

  inline void clear_bit(idx_t idx) {
    assert((idx >= 0) && (idx < _num_bits), "precondition");
    assert(idx >= 0, "precondition");
    size_t array_idx = idx >> LogBitsPerWord;
    uintx bit_number = idx & (BitsPerWord - 1);
    uintx the_bit = nth_bit(bit_number);
    _bitmap[array_idx] &= ~the_bit;
  }

  inline bool is_set(idx_t idx) const {
    assert((idx >= 0) && (idx < _num_bits), "precondition");
    assert(idx >= 0, "precondition");
    size_t array_idx = idx >> LogBitsPerWord;
    uintx bit_number = idx & (BitsPerWord - 1);
    uintx the_bit = nth_bit(bit_number);
    return (_bitmap[array_idx] & the_bit) != 0;
  }

  // Return the index of the first set bit in the range [beg, size()), or size() if none found.
  // precondition: beg and end form a valid range for the bitmap.
  inline idx_t find_first_set_bit(idx_t beg) const;

  // Return the index of the first set bit in the range [beg, end), or end if none found.
  // precondition: beg and end form a valid range for the bitmap.
  inline idx_t find_first_set_bit(idx_t beg, idx_t end) const;

  // Return the index of the last set bit in the range (-1, end], or -1 if none found.
  // precondition: beg and end form a valid range for the bitmap.
  inline idx_t find_last_set_bit(idx_t end) const;

  // Return the index of the last set bit in the range (beg, end], or beg if none found.
  // precondition: beg and end form a valid range for the bitmap.
  inline idx_t find_last_set_bit(idx_t beg, idx_t end) const;

  // Return the start index of the first run of <num_bits> consecutive set bits for which the first set bit is within
  //   the range [beg, size()), or size() if the run of <num_bits> is not found within this range.
  // precondition: beg is within the valid range for the bitmap.
  inline idx_t find_first_consecutive_set_bits(idx_t beg, size_t num_bits) const;

  // Return the start index of the first run of <num_bits> consecutive set bits for which the first set bit is within
  //   the range [beg, end), or end if the run of <num_bits> is not found within this range.
  // precondition: beg and end form a valid range for the bitmap.
  idx_t find_first_consecutive_set_bits(idx_t beg, idx_t end, size_t num_bits) const;

  // Return the start index of the last run of <num_bits> consecutive set bits for which the entire run of set bits is within
  // the range (-1, end], or -1 if the run of <num_bits> is not found within this range.
  // precondition: end is within the valid range for the bitmap.
  inline idx_t find_last_consecutive_set_bits(idx_t end, size_t num_bits) const;

  // Return the start index of the first run of <num_bits> consecutive set bits for which the entire run of set bits is within
  // the range (beg, end], or beg if the run of <num_bits> is not found within this range.
  // precondition: beg and end form a valid range for the bitmap.
  idx_t find_last_consecutive_set_bits(idx_t beg, idx_t end, size_t num_bits) const;
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHSIMPLEBITMAP_HPP
