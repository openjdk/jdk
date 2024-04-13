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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHSIMPLEBITMAP_INLINE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHSIMPLEBITMAP_INLINE_HPP

#include "gc/shenandoah/shenandoahSimpleBitMap.hpp"

inline ssize_t ShenandoahSimpleBitMap::find_next_set_bit(ssize_t start_idx, ssize_t boundary_idx) const {
  assert((start_idx >= 0) && (start_idx < _num_bits), "precondition");
  assert((boundary_idx > start_idx) && (boundary_idx <= _num_bits), "precondition");
  do {
    size_t array_idx = start_idx >> LogBitsPerWord;
    uintx bit_number = start_idx & right_n_bits(LogBitsPerWord);
    uintx element_bits = _bitmap[array_idx];
    if (bit_number > 0) {
      uintx mask_out = right_n_bits(bit_number);
      element_bits &= ~mask_out;
    }
    if (element_bits) {
      // The next set bit is here.  Find first set bit >= bit_number;
      uintx aligned = element_bits >> bit_number;
      uintx first_set_bit = count_trailing_zeros<uintx>(aligned);
      ssize_t candidate_result = (array_idx * BitsPerWord) + bit_number + first_set_bit;
      return (candidate_result < boundary_idx)? candidate_result: boundary_idx;
    } else {
      // Next bit is not here.  Try the next array element
      start_idx += BitsPerWord - bit_number;
    }
  } while (start_idx < boundary_idx);
  return boundary_idx;
}

inline ssize_t ShenandoahSimpleBitMap::find_next_set_bit(ssize_t start_idx) const {
  assert((start_idx >= 0) && (start_idx < _num_bits), "precondition");
  return find_next_set_bit(start_idx, _num_bits);
}

inline ssize_t ShenandoahSimpleBitMap::find_prev_set_bit(ssize_t last_idx, ssize_t boundary_idx) const {
  assert((last_idx >= 0) && (last_idx < _num_bits), "precondition");
  assert((boundary_idx >= -1) && (boundary_idx < last_idx), "precondition");
  do {
    ssize_t array_idx = last_idx >> LogBitsPerWord;
    uintx bit_number = last_idx & right_n_bits(LogBitsPerWord);
    uintx element_bits = _bitmap[array_idx];
    if (bit_number < BitsPerWord - 1){
      uintx mask_in = right_n_bits(bit_number + 1);
      element_bits &= mask_in;
    }
    if (element_bits) {
      // The prev set bit is here.  Find the first set bit <= bit_number
      uintx aligned = element_bits << (BitsPerWord - (bit_number + 1));
      uintx first_set_bit = count_leading_zeros<uintx>(aligned);
      ssize_t candidate_result = array_idx * BitsPerWord + (bit_number - first_set_bit);
      return (candidate_result > boundary_idx)? candidate_result: boundary_idx;
    } else {
      // Next bit is not here.  Try the previous array element
      last_idx -= (bit_number + 1);
    }
  } while (last_idx > boundary_idx);
  return boundary_idx;
}

inline ssize_t ShenandoahSimpleBitMap::find_prev_set_bit(ssize_t last_idx) const {
  assert((last_idx >= 0) && (last_idx < _num_bits), "precondition");
  return find_prev_set_bit(last_idx, -1);
}

inline ssize_t ShenandoahSimpleBitMap::find_next_consecutive_bits(size_t num_bits, ssize_t start_idx) const {
  assert((start_idx >= 0) && (start_idx < _num_bits), "precondition");
  return find_next_consecutive_bits(num_bits, start_idx, _num_bits);
}

inline ssize_t ShenandoahSimpleBitMap::find_prev_consecutive_bits(size_t num_bits, ssize_t last_idx) const {
  assert((last_idx >= 0) && (last_idx < _num_bits), "precondition");
  return find_prev_consecutive_bits(num_bits, last_idx, (ssize_t) -1);
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHSIMPLEBITMAP_INLINE_HPP
