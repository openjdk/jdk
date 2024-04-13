/*
 * Copyright (c) 2016, 2019, Red Hat, Inc. All rights reserved.
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

// The API and internal implementation of ShenandoahSimpleBitMap and ShenandoahRegionPartitions use ssize_t to
// represent index, even though index is "inherently" unsigned.  There are two reasons for this choice:
//  1. We use -1 as a sentinel value to represent empty partitions.  This same value may be used to represent
//     failure to find a previous set bit or previous range of set bits.
//  2. Certain loops are written most naturally if the iterator, which may hold the sentinel -1 value, can be
//     declared as signed and the terminating condition can be < 0.

// ShenandoahSimpleBitMap resembles CHeapBitMap but adds missing support for find_next_consecutive_bits() and
// find_prev_contiguous_bits.  An alternative refactoring of code would subclass CHeapBitMap, but this might
// break abstraction rules, because efficient implementation requires assumptions about superclass internals that
// might be violatee through future software maintenance.
class ShenandoahSimpleBitMap {
  const ssize_t _num_bits;
  const size_t _num_words;
  uintx* const _bitmap;

public:
  ShenandoahSimpleBitMap(size_t num_bits) :
      _num_bits(num_bits),
      _num_words((num_bits + (BitsPerWord - 1)) / BitsPerWord),
      _bitmap(NEW_C_HEAP_ARRAY(uintx, _num_words, mtGC))
  {
    clear_all();
  }

  ~ShenandoahSimpleBitMap() {
    if (_bitmap != nullptr) {
      FREE_C_HEAP_ARRAY(uintx, _bitmap);
    }
  }
  void clear_all() {
    for (size_t i = 0; i < _num_words; i++) {
      _bitmap[i] = 0;
    }
  }

private:

  // Count consecutive ones in forward order, starting from start_idx.  Requires that there is at least one zero
  // between start_idx and index value (_num_bits - 1), inclusive.
  size_t count_leading_ones(ssize_t start_idx) const;

  // Count consecutive ones in reverse order, starting from last_idx.  Requires that there is at least one zero
  // between last_idx and index value zero, inclusive.
  size_t count_trailing_ones(ssize_t last_idx) const;

  bool is_forward_consecutive_ones(ssize_t start_idx, ssize_t count) const;
  bool is_backward_consecutive_ones(ssize_t last_idx, ssize_t count) const;

public:

  inline ssize_t aligned_index(ssize_t idx) const {
    assert((idx >= 0) && (idx < _num_bits), "precondition");
    ssize_t array_idx = idx & ~right_n_bits(LogBitsPerWord);
    return array_idx;
  }

  inline ssize_t alignment() const {
    return BitsPerWord;
  }

  // For testing
  inline ssize_t number_of_bits() const {
    return _num_bits;
  }

  inline uintx bits_at(ssize_t idx) const {
    assert((idx >= 0) && (idx < _num_bits), "precondition");
    ssize_t array_idx = idx >> LogBitsPerWord;
    return _bitmap[array_idx];
  }

  inline void set_bit(ssize_t idx) {
    assert((idx >= 0) && (idx < _num_bits), "precondition");
    size_t array_idx = idx >> LogBitsPerWord;
    uintx bit_number = idx & right_n_bits(LogBitsPerWord);
    uintx the_bit = nth_bit(bit_number);
    _bitmap[array_idx] |= the_bit;
  }

  inline void clear_bit(ssize_t idx) {
    assert((idx >= 0) && (idx < _num_bits), "precondition");
    assert(idx >= 0, "precondition");
    size_t array_idx = idx >> LogBitsPerWord;
    uintx bit_number = idx & right_n_bits(LogBitsPerWord);
    uintx the_bit = nth_bit(bit_number);
    _bitmap[array_idx] &= ~the_bit;
  }

  inline bool is_set(ssize_t idx) const {
    assert((idx >= 0) && (idx < _num_bits), "precondition");
    assert(idx >= 0, "precondition");
    size_t array_idx = idx >> LogBitsPerWord;
    uintx bit_number = idx & right_n_bits(LogBitsPerWord);
    uintx the_bit = nth_bit(bit_number);
    return (_bitmap[array_idx] & the_bit)? true: false;
  }

  // Return the index of the first set bit which is greater or equal to start_idx.  If not found, return _num_bits.
  inline ssize_t find_next_set_bit(ssize_t start_idx) const;

  // Return the index of the first set bit which is greater or equal to start_idx and less than boundary_idx.
  // If not found, return boundary_idx
  inline ssize_t find_next_set_bit(ssize_t start_idx, ssize_t boundary_idx) const;

  // Return the index of the last set bit which is less or equal to start_idx.  If not found, return -1.
  inline ssize_t find_prev_set_bit(ssize_t last_idx) const;

  // Return the index of the last set bit which is less or equal to start_idx and greater than boundary_idx.
  // If not found, return boundary_idx.
  inline ssize_t find_prev_set_bit(ssize_t last_idx, ssize_t boundary_idx) const;

  // Return the smallest index at which a run of num_bits consecutive ones is found, where return value is >= start_idx
  // and return value < _num_bits.  If no run of num_bits consecutive ones is found within the target range, return _num_bits.
  inline ssize_t find_next_consecutive_bits(size_t num_bits, ssize_t start_idx) const;

  // Return the smallest index at which a run of num_bits consecutive ones is found, where return value is >= start_idx
  // and return value < boundary_idx.  If no run of num_bits consecutive ones is found within the target range,
  // return boundary_idx.
  ssize_t find_next_consecutive_bits(size_t num_bits, ssize_t start_idx, ssize_t boundary_idx) const;

  // Return the largest index at which a run of num_bits consecutive ones is found, where return value is <= last_idx and > -1.
  // If no run of num_bits consecutive ones is found within the target range, return -1.
  inline ssize_t find_prev_consecutive_bits(size_t num_bits, ssize_t last_idx) const;

  // Return the largest index at which a run of num_bits consecutive ones is found, where return value is <= last_idx and > -1.
  // If no run of num_bits consecutive ones is found within the target range, return -1.
  ssize_t find_prev_consecutive_bits(size_t num_bits, ssize_t last_idx, ssize_t boundary_idx) const;
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHSIMPLEBITMAP_HPP
