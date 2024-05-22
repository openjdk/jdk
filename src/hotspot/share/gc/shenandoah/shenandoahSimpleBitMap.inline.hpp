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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHSIMPLEBITMAP_INLINE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHSIMPLEBITMAP_INLINE_HPP

#include "gc/shenandoah/shenandoahSimpleBitMap.hpp"

inline idx_t ShenandoahSimpleBitMap::find_first_set_bit(idx_t beg, idx_t end) const {
  assert((beg >= 0) && (beg < _num_bits), "precondition");
  assert((end > beg) && (end <= _num_bits), "precondition");
  do {
    size_t array_idx = beg >> LogBitsPerWord;
    uintx bit_number = beg & right_n_bits(LogBitsPerWord);
    uintx element_bits = _bitmap[array_idx];
    if (bit_number > 0) {
      uintx mask_out = right_n_bits(bit_number);
      element_bits &= ~mask_out;
    }
    if (element_bits) {
      // The next set bit is here.  Find first set bit >= bit_number;
      uintx aligned = element_bits >> bit_number;
      uintx first_set_bit = count_trailing_zeros<uintx>(aligned);
      idx_t candidate_result = (array_idx * BitsPerWord) + bit_number + first_set_bit;
      return (candidate_result < end)? candidate_result: end;
    } else {
      // Next bit is not here.  Try the next array element
      beg += BitsPerWord - bit_number;
    }
  } while (beg < end);
  return end;
}

inline idx_t ShenandoahSimpleBitMap::find_first_set_bit(idx_t beg) const {
  assert((beg >= 0) && (beg < size()), "precondition");
  return find_first_set_bit(beg, size());
}

inline idx_t ShenandoahSimpleBitMap::find_last_set_bit(idx_t beg, idx_t end) const {
  assert((end >= 0) && (end < _num_bits), "precondition");
  assert((beg >= -1) && (beg < end), "precondition");
  do {
    idx_t array_idx = end >> LogBitsPerWord;
    uintx bit_number = end & right_n_bits(LogBitsPerWord);
    uintx element_bits = _bitmap[array_idx];
    if (bit_number < BitsPerWord - 1){
      uintx mask_in = right_n_bits(bit_number + 1);
      element_bits &= mask_in;
    }
    if (element_bits) {
      // The prev set bit is here.  Find the first set bit <= bit_number
      uintx aligned = element_bits << (BitsPerWord - (bit_number + 1));
      uintx first_set_bit = count_leading_zeros<uintx>(aligned);
      idx_t candidate_result = array_idx * BitsPerWord + (bit_number - first_set_bit);
      return (candidate_result > beg)? candidate_result: beg;
    } else {
      // Next bit is not here.  Try the previous array element
      end -= (bit_number + 1);
    }
  } while (end > beg);
  return beg;
}

inline idx_t ShenandoahSimpleBitMap::find_last_set_bit(idx_t end) const {
  assert((end >= 0) && (end < _num_bits), "precondition");
  return find_last_set_bit(-1, end);
}

inline idx_t ShenandoahSimpleBitMap::find_first_consecutive_set_bits(idx_t beg, size_t num_bits) const {
  assert((beg >= 0) && (beg < _num_bits), "precondition");
  return find_first_consecutive_set_bits(beg, size(), num_bits);
}

inline idx_t ShenandoahSimpleBitMap::find_last_consecutive_set_bits(idx_t end, size_t num_bits) const {
  assert((end >= 0) && (end < _num_bits), "precondition");
  return find_last_consecutive_set_bits((idx_t) -1, end, num_bits);
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHSIMPLEBITMAP_INLINE_HPP
