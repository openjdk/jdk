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

#include "precompiled.hpp"
#include "gc/shenandoah/shenandoahSimpleBitMap.hpp"

ShenandoahSimpleBitMap::ShenandoahSimpleBitMap(size_t num_bits) :
    _num_bits(num_bits),
    _num_words(align_up(num_bits, BitsPerWord) / BitsPerWord),
    _bitmap(NEW_C_HEAP_ARRAY(uintx, _num_words, mtGC))
{
  clear_all();
}

ShenandoahSimpleBitMap::~ShenandoahSimpleBitMap() {
  if (_bitmap != nullptr) {
    FREE_C_HEAP_ARRAY(uintx, _bitmap);
  }
}

size_t ShenandoahSimpleBitMap::count_leading_ones(idx_t start_idx) const {
  assert((start_idx >= 0) && (start_idx < _num_bits), "precondition");
  size_t array_idx = start_idx >> LogBitsPerWord;
  uintx element_bits = _bitmap[array_idx];
  uintx bit_number = start_idx & right_n_bits(LogBitsPerWord);
  uintx mask = ~right_n_bits(bit_number);
  size_t counted_ones = 0;
  while ((element_bits & mask) == mask) {
    // All bits numbered >= bit_number are set
    size_t found_ones = BitsPerWord - bit_number;
    counted_ones += found_ones;
    // Dead code: do not need to compute: start_idx += found_ones;
    // Strength reduction:                array_idx = (start_idx >> LogBitsPerWord)
    array_idx++;
    element_bits = _bitmap[array_idx];
    // Constant folding:                  bit_number = start_idx & right_n_bits(LogBitsPerWord);
    bit_number = 0;
    // Constant folding:                  mask = ~right_n_bits(bit_number);
    mask = ~0;
  }

  // Add in number of consecutive ones starting with the_bit and including more significant bits and return result
  uintx aligned = element_bits >> bit_number;
  uintx complement = ~aligned;
  return counted_ones + count_trailing_zeros<uintx>(complement);
}

size_t ShenandoahSimpleBitMap::count_trailing_ones(idx_t last_idx) const {
  assert((last_idx >= 0) && (last_idx < _num_bits), "precondition");
  size_t array_idx = last_idx >> LogBitsPerWord;
  uintx element_bits = _bitmap[array_idx];
  uintx bit_number = last_idx & right_n_bits(LogBitsPerWord);
  // All ones from bit 0 to the_bit
  uintx mask = right_n_bits(bit_number + 1);
  size_t counted_ones = 0;
  while ((element_bits & mask) == mask) {
    // All bits numbered <= bit_number are set
    size_t found_ones = bit_number + 1;
    counted_ones += found_ones;
    // Dead code: do not need to compute: last_idx -= found_ones;
    array_idx--;
    element_bits = _bitmap[array_idx];
    // Constant folding:                  bit_number = last_idx & right_n_bits(LogBitsPerWord);
    bit_number = BitsPerWord - 1;
    // Constant folding:                  mask = right_n_bits(bit_number + 1);
    mask = ~0;
  }

  // Add in number of consecutive ones starting with the_bit and including less significant bits and return result
  uintx aligned = element_bits << (BitsPerWord - (bit_number + 1));
  uintx complement = ~aligned;
  return counted_ones + count_leading_zeros<uintx>(complement);
}

bool ShenandoahSimpleBitMap::is_forward_consecutive_ones(idx_t start_idx, idx_t count) const {
  while (count > 0) {
    assert((start_idx >= 0) && (start_idx < _num_bits), "precondition: start_idx: " SSIZE_FORMAT ", count: " SSIZE_FORMAT,
           start_idx, count);
    assert(start_idx + count <= (idx_t) _num_bits, "precondition");
    size_t array_idx = start_idx >> LogBitsPerWord;
    uintx bit_number = start_idx & right_n_bits(LogBitsPerWord);
    uintx element_bits = _bitmap[array_idx];
    uintx bits_to_examine  = BitsPerWord - bit_number;
    element_bits >>= bit_number;
    uintx complement = ~element_bits;
    uintx trailing_ones;
    if (complement != 0) {
      trailing_ones = count_trailing_zeros<uintx>(complement);
    } else {
      trailing_ones = bits_to_examine;
    }
    if (trailing_ones >= (uintx) count) {
      return true;
    } else if (trailing_ones == bits_to_examine) {
      start_idx += bits_to_examine;
      count -= bits_to_examine;
      // Repeat search with smaller goal
    } else {
      return false;
    }
  }
  return true;
}

bool ShenandoahSimpleBitMap::is_backward_consecutive_ones(idx_t last_idx, idx_t count) const {
  while (count > 0) {
    assert((last_idx >= 0) && (last_idx < _num_bits), "precondition");
    assert(last_idx - count >= -1, "precondition");
    size_t array_idx = last_idx >> LogBitsPerWord;
    uintx bit_number = last_idx & right_n_bits(LogBitsPerWord);
    uintx element_bits = _bitmap[array_idx];
    uintx bits_to_examine = bit_number + 1;
    element_bits <<= (BitsPerWord - bits_to_examine);
    uintx complement = ~element_bits;
    uintx leading_ones;
    if (complement != 0) {
      leading_ones = count_leading_zeros<uintx>(complement);
    } else {
      leading_ones = bits_to_examine;
    }
    if (leading_ones >= (uintx) count) {
      return true;
    } else if (leading_ones == bits_to_examine) {
      last_idx -= leading_ones;
      count -= leading_ones;
      // Repeat search with smaller goal
    } else {
      return false;
    }
  }
  return true;
}

idx_t ShenandoahSimpleBitMap::find_first_consecutive_set_bits(idx_t beg, idx_t end, size_t num_bits) const {
  assert((beg >= 0) && (beg < _num_bits), "precondition");

  // Stop looking if there are not num_bits remaining in probe space.
  idx_t start_boundary = end - num_bits;
  if (beg > start_boundary) {
    return end;
  }
  uintx array_idx = beg >> LogBitsPerWord;
  uintx bit_number = beg & right_n_bits(LogBitsPerWord);
  uintx element_bits = _bitmap[array_idx];
  if (bit_number > 0) {
    uintx mask_out = right_n_bits(bit_number);
    element_bits &= ~mask_out;
  }

  // The following loop minimizes the number of spans probed in order to find num_bits consecutive bits.
  // For example, if bit_number = beg = 0, num_bits = 8, and element bits equals 00111111_11000000_00000000_10011000B,
  // we need only 3 probes to find the match at bit offset 22.
  //
  // Let beg = 0
  // element_bits = 00111111_11000000_00000000_10011000B;
  //                                           ________   (the searched span)
  //                                           ^   ^  ^- bit_number = beg = 0
  //                                           |   +-- next_start_candidate_1 (where next 1 is found)
  //                                           +------ next_start_candidate_2 (start of the trailing 1s within span)
  // Let beg = 7
  // element_bits = 00111111_11000000_00000000_10011000B;
  //                          ^       ^_________   (the searched span)
  //                          |       |        ^- bit_number = beg = 7
  //                          |       +---------- next_start_candidate_2 (there are no trailing 1s within span)
  //                          +------------------ next_start_candidate_1 (where next 1 is found)
  // Let beg = 22
  // Let beg = 22
  // element_bits = 00111111_11000001_11111100_10011000B;
  //                  _________   (the searched span)
  //                          ^- bit_number = beg = 18
  // Here, is_forward_consecutive_ones(22, 8) succeeds and we report the match

  while (true) {
    if (element_bits == 0) {
      // move to the next element
      beg += BitsPerWord - bit_number;
      if (beg > start_boundary) {
        // No match found.
        return end;
      }
      array_idx++;
      bit_number = 0;
      element_bits = _bitmap[array_idx];
    } else if (is_forward_consecutive_ones(beg, num_bits)) {
      return beg;
    } else {
      // There is at least one non-zero bit within the masked element_bits. Arrange to skip over bits that
      // cannot be part of a consecutive-ones match.
      uintx next_set_bit = count_trailing_zeros<uintx>(element_bits);
      uintx next_start_candidate_1 = (array_idx << LogBitsPerWord) + next_set_bit;

      // There is at least one zero bit in this span. Align the next probe at the start of trailing ones for probed span,
      // or align at end of span if this span has no trailing ones.
      size_t trailing_ones = count_trailing_ones(beg + num_bits - 1);
      uintx next_start_candidate_2 = beg + num_bits - trailing_ones;

      beg = MAX2(next_start_candidate_1, next_start_candidate_2);
      if (beg > start_boundary) {
        // No match found.
        return end;
      }
      array_idx = beg >> LogBitsPerWord;
      element_bits = _bitmap[array_idx];
      bit_number = beg & right_n_bits(LogBitsPerWord);
      if (bit_number > 0) {
        size_t mask_out = right_n_bits(bit_number);
        element_bits &= ~mask_out;
      }
    }
  }
}

idx_t ShenandoahSimpleBitMap::find_last_consecutive_set_bits(const idx_t beg, idx_t end, const size_t num_bits) const {

  assert((end >= 0) && (end < _num_bits), "precondition");

  // Stop looking if there are not num_bits remaining in probe space.
  idx_t last_boundary = beg + num_bits;
  if (end < last_boundary) {
    return beg;
  }

  size_t array_idx = end >> LogBitsPerWord;
  uintx bit_number = end & right_n_bits(LogBitsPerWord);
  uintx element_bits = _bitmap[array_idx];
  if (bit_number < BitsPerWord - 1) {
    uintx mask_in = right_n_bits(bit_number + 1);
    element_bits &= mask_in;
  }

  // See comment in find_first_consecutive_set_bits to understand how this loop works.
  while (true) {
    if (element_bits == 0) {
      // move to the previous element
      end -= bit_number + 1;
      if (end < last_boundary) {
        // No match found.
        return beg;
      }
      array_idx--;
      bit_number = BitsPerWord - 1;
      element_bits = _bitmap[array_idx];
    } else if (is_backward_consecutive_ones(end, num_bits)) {
      return end + 1 - num_bits;
    } else {
      // There is at least one non-zero bit within the masked element_bits. Arrange to skip over bits that
      // cannot be part of a consecutive-ones match.
      uintx next_set_bit = BitsPerWord - (1 + count_leading_zeros<uintx>(element_bits));
      uintx next_last_candidate_1 = (array_idx << LogBitsPerWord) + next_set_bit;

      // There is at least one zero bit in this span.  Align the next probe at the end of leading ones for probed span,
      // or align before start of span if this span has no leading ones.
      size_t leading_ones = count_leading_ones(end - (num_bits - 1));
      uintx next_last_candidate_2 = end - (num_bits - leading_ones);

      end = MIN2(next_last_candidate_1, next_last_candidate_2);
      if (end < last_boundary) {
        // No match found.
        return beg;
      }
      array_idx = end >> LogBitsPerWord;
      bit_number = end & right_n_bits(LogBitsPerWord);
      element_bits = _bitmap[array_idx];
      if (bit_number < BitsPerWord - 1){
        size_t mask_in = right_n_bits(bit_number + 1);
        element_bits &= mask_in;
      }
    }
  }
}
