/*
 * Copyright (c) 2016, 2021, Red Hat, Inc. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/shenandoah/shenandoahSimpleBitMap.hpp"

size_t ShenandoahSimpleBitMap::count_leading_ones(ssize_t start_idx) const {
  assert((start_idx >= 0) && (start_idx < _num_bits), "precondition");
  size_t array_idx = start_idx >> LogBitsPerWord;
  uintx element_bits = _bitmap[array_idx];
  uintx bit_number = start_idx & right_n_bits(LogBitsPerWord);
  uintx omit_mask = right_n_bits(bit_number);
  uintx mask = ((uintx) 0 - 1) & ~omit_mask;
  if ((element_bits & mask) == mask) {
    size_t counted_ones = BitsPerWord - bit_number;
    return counted_ones + count_leading_ones(start_idx - counted_ones);
  } else {
    // Return number of consecutive ones starting with the_bit and including more significant bits.
    uintx aligned = element_bits >> bit_number;
    uintx complement = ~aligned;;
    return count_trailing_zeros<uintx>(complement);
  }
}

size_t ShenandoahSimpleBitMap::count_trailing_ones(ssize_t last_idx) const {
  assert((last_idx >= 0) && (last_idx < _num_bits), "precondition");
  size_t array_idx = last_idx >> LogBitsPerWord;
  uintx element_bits = _bitmap[array_idx];
  uintx bit_number = last_idx & right_n_bits(LogBitsPerWord);
  // All ones from bit 0 to the_bit
  uintx mask = right_n_bits(bit_number + 1);
  if ((element_bits & mask) == mask) {
    size_t counted_ones = bit_number + 1;
    return counted_ones + count_trailing_ones(last_idx - counted_ones);
  } else {
    // Return number of consecutive ones starting with the_bit and including less significant bits
    uintx aligned = element_bits << (BitsPerWord - (bit_number + 1));
    uintx complement = ~aligned;
    return count_leading_zeros<uintx>(complement);
  }
}

bool ShenandoahSimpleBitMap::is_forward_consecutive_ones(ssize_t start_idx, ssize_t count) const {
  assert((start_idx >= 0) && (start_idx < _num_bits), "precondition: start_idx: " SSIZE_FORMAT ", count: " SSIZE_FORMAT,
         start_idx, count);
  assert(start_idx + count <= (ssize_t) _num_bits, "precondition");
  size_t array_idx = start_idx >> LogBitsPerWord;
  uintx bit_number = start_idx & right_n_bits(LogBitsPerWord);
  uintx element_bits = _bitmap[array_idx];
  uintx bits_to_examine  = BitsPerWord - bit_number;
  element_bits >>= bit_number;
  uintx complement = ~element_bits;
  uintx trailing_ones;
  if (complement) {
    trailing_ones = count_trailing_zeros<uintx>(complement);
  } else {
    trailing_ones = bits_to_examine;
  }
  if (trailing_ones >= (uintx) count) {
    return true;
  } else if (trailing_ones == bits_to_examine) {
     // Tail recursion
    return is_forward_consecutive_ones(start_idx + bits_to_examine, count - bits_to_examine);
  } else {
    return false;
  }
}

bool ShenandoahSimpleBitMap::is_backward_consecutive_ones(ssize_t last_idx, ssize_t count) const {
  assert((last_idx >= 0) && (last_idx < _num_bits), "precondition");
  assert(last_idx - count >= -1, "precondition");
  size_t array_idx = last_idx >> LogBitsPerWord;
  uintx bit_number = last_idx & right_n_bits(LogBitsPerWord);
  uintx element_bits = _bitmap[array_idx];
  uintx bits_to_examine = bit_number + 1;
  element_bits <<= (BitsPerWord - bits_to_examine);
  uintx complement = ~element_bits;
  uintx leading_ones;
  if (complement) {
    leading_ones = count_leading_zeros<uintx>(complement);
  } else {
    leading_ones = bits_to_examine;
  }
  if (leading_ones >= (uintx) count) {
    return true;
  } else if (leading_ones == bits_to_examine) {
    // Tail recursion
    return is_backward_consecutive_ones(last_idx - leading_ones, count - leading_ones);
  } else {
    return false;
  }
}

ssize_t ShenandoahSimpleBitMap::find_next_consecutive_bits(size_t num_bits, ssize_t start_idx, ssize_t boundary_idx) const {
  assert((start_idx >= 0) && (start_idx < _num_bits), "precondition");

  // Stop looking if there are not num_bits remaining in probe space.
  ssize_t start_boundary = boundary_idx - num_bits;
  if (start_idx > start_boundary) {
    return boundary_idx;
  }
  uintx array_idx = start_idx >> LogBitsPerWord;
  uintx bit_number = start_idx & right_n_bits(LogBitsPerWord);
  uintx element_bits = _bitmap[array_idx];
  if (bit_number > 0) {
    uintx mask_out = right_n_bits(bit_number);
    element_bits &= ~mask_out;
  }
  while (true) {
    if (!element_bits) {
      // move to the next element
      start_idx += BitsPerWord - bit_number;
      if (start_idx > start_boundary) {
        // No match found.
        return boundary_idx;
      }
      array_idx++;
      bit_number = 0;
      element_bits = _bitmap[array_idx];
    } else if (is_forward_consecutive_ones(start_idx, num_bits)) {
      return start_idx;
    } else {
      // There is at least one non-zero bit within the masked element_bits.  Find it.
      uintx next_set_bit = count_trailing_zeros<uintx>(element_bits);
      uintx next_start_candidate_1 = (array_idx << LogBitsPerWord) + next_set_bit;

      // There is at least one zero bit in this span.  Align the next probe at the start of trailing ones for probed span.
      size_t trailing_ones = count_trailing_ones(start_idx + num_bits - 1);
      uintx next_start_candidate_2 = start_idx + num_bits - trailing_ones;

      start_idx = MAX2(next_start_candidate_1, next_start_candidate_2);
      if (start_idx > start_boundary) {
        // No match found.
        return boundary_idx;
      }
      array_idx = start_idx >> LogBitsPerWord;
      element_bits = _bitmap[array_idx];
      bit_number = start_idx & right_n_bits(LogBitsPerWord);
      if (bit_number > 0) {
        size_t mask_out = right_n_bits(bit_number);
        element_bits &= ~mask_out;
      }
    }
  }
}

ssize_t ShenandoahSimpleBitMap::find_prev_consecutive_bits(
  const size_t num_bits, ssize_t last_idx, const ssize_t boundary_idx) const {
  assert((last_idx >= 0) && (last_idx < _num_bits), "precondition");

  // Stop looking if there are not num_bits remaining in probe space.
  ssize_t last_boundary = boundary_idx + num_bits;
  if (last_idx < last_boundary) {
    return boundary_idx;
  }

  size_t array_idx = last_idx >> LogBitsPerWord;
  uintx bit_number = last_idx & right_n_bits(LogBitsPerWord);
  uintx element_bits = _bitmap[array_idx];
  if (bit_number < BitsPerWord - 1) {
    uintx mask_in = right_n_bits(bit_number + 1);
    element_bits &= mask_in;
  }
  while (true) {
    if (!element_bits) {
      // move to the previous element
      last_idx -= bit_number + 1;
      if (last_idx < last_boundary) {
        // No match found.
        return boundary_idx;
      }
      array_idx--;
      bit_number = BitsPerWord - 1;
      element_bits = _bitmap[array_idx];
    } else if (is_backward_consecutive_ones(last_idx, num_bits)) {
      return last_idx + 1 - num_bits;
    } else {
      // There is at least one non-zero bit within the masked element_bits.  Find it.
      uintx next_set_bit = BitsPerWord - (1 + count_leading_zeros<uintx>(element_bits));
      uintx next_last_candidate_1 = (array_idx << LogBitsPerWord) + next_set_bit;

      // There is at least one zero bit in this span.  Align the next probe at the end of leading ones for probed span.
      size_t leading_ones = count_leading_ones(last_idx - (num_bits - 1));
      uintx next_last_candidate_2 = last_idx - (num_bits - leading_ones);

      last_idx = MIN2(next_last_candidate_1, next_last_candidate_2);
      if (last_idx < last_boundary) {
        // No match found.
        return boundary_idx;
      }
      array_idx = last_idx >> LogBitsPerWord;
      bit_number = last_idx & right_n_bits(LogBitsPerWord);
      element_bits = _bitmap[array_idx];
      if (bit_number < BitsPerWord - 1){
        size_t mask_in = right_n_bits(bit_number + 1);
        element_bits &= mask_in;
      }
    }
  }
}
