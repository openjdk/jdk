/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_UNSIGNED5_INLINE_HPP
#define SHARE_UTILITIES_UNSIGNED5_INLINE_HPP

#include "utilities/unsigned5.hpp"

/// Extra-complicated implementation details.  Moved here to reduce clutter.

// The functions are inline to encourage constant folding.

// Returns the leading "YX" word used to encode a pair of ints, as
// read by read_uint_pair.  If X is big, the Y field is biased by +1
// so that in the worst case (big X, big Y), where all bits are
// saturated to 1, the stored value ((Y+1)<<(32-S))+X will be close
// to zero, probably requiring only a single byte.  Note also that
// if Y is big, the X field is forced to be all 1-bits, so that the
// transmission of a separate Y is always preceded by a separate X.
inline int UNSIGNED5::encoded_pair_lead(int first_width, uint32_t first, uint32_t second) {
  assert(first_width >= 0 && first_width <= 31, "");
  const uint32_t xmask = right_n_bits(first_width &= 31);
  const bool badx = (first >= xmask);
  const bool bady = (second > right_n_bits(32-first_width));
  const uint32_t yx = bady ? xmask : ((second << first_width)
                                      + (badx ? xmask*2+1 : first));
  // (If second = -1, bady is false but yx will make bady appear to
  // be true.  That is by design.  Doing it this way allows us to
  // compare first against a bound that does not depend on badx.)
  return yx;
}

inline int UNSIGNED5::encoded_pair_count(int first_width, uint32_t pair_lead_xy) {
  const uint32_t xmask = right_n_bits(first_width &= 31);
  const uint32_t testyx = xmask ^ pair_lead_xy;
  return (testyx == 0 && first_width != 0 ? 3 : (testyx & xmask) == 0 ? 2 : 1);
}

inline int UNSIGNED5::encoded_pair_length(int first_width, uint32_t first, uint32_t second) {
  const uint32_t yx = encoded_pair_lead(first_width, first, second);
  const int      n  = encoded_pair_count(first_width, first, second);
  return (encoded_length(yx)
          + (n < 2 ? 0 : encoded_length(first)
             + ( n < 3 ? 0 : encoded_length(second))));
}

inline uint32_t UNSIGNED5::encode_multi_sign(int sign_bits, int32_t value) {
  assert(sign_bits >= 0 && sign_bits < 16, "");
  const uint32_t sign_mask = right_n_bits(sign_bits &= 15);
  switch (sign_bits) {
  case 0: return value;                // straight cast to unsigned
  case 1: return encode_sign(value);   // symmetric sign encoding
  }
  const uint32_t v = value;
  const bool has_negative_code = (v >= ((uint32_t)-1 << (32-sign_bits)));
  // check alternative formula:
  assert(has_negative_code ==
         (value < 0 && value >= (int32_t)INT32_MIN / (1<<(sign_bits-1))), "");
  uint32_t r;
  if (has_negative_code) {
    r = (~v << sign_bits) + sign_mask;
    assert(r == v * (-1<<sign_bits) - 1, "");  // check alternate formula
  } else {
    r = value;
    r += v / sign_mask;
    // Division by a non-constant sign mask is going to the most expensive step.
    // But most callers supply a constant argument, which allows this inline function
    // to strength-reduce the division to a multiplication.
  }
  // Test for a bijection at this point:
  DEBUG_ONLY(uint32_t v2 = decode_multi_sign(sign_bits, r));
  assert(v == v2, "round trip failed: %x => %x => %x", value, r, v2);
  return r;
}

int32_t UNSIGNED5::decode_multi_sign(int sign_bits, uint32_t value) {
  switch (sign_bits) {
  case 0: return value;       // straight cast to unsigned
  case 1: return decode_sign(value);  // symmetric sign encoding
  }
  const uint32_t v = value;
  uint32_t sign_mask = right_n_bits(sign_bits);
  int32_t r;
  if ((v & sign_mask) == sign_mask) {
    r = ~(v >> sign_bits);
  } else {
    r = v;
    r -= v >> sign_bits;
  }
  return r;
}

template<typename READ_UINT>
inline int UNSIGNED5::read_uint_pair(int first_width,
                                     uint32_t& first_result,
                                     uint32_t& second_result,
                                     READ_UINT read_uint) {
  assert(first_width >= 0 && first_width <= 31, "");
  first_width &= 31;
  const uint32_t yx = read_uint();  // get the pair lead, then decide n
  const int      n  = encoded_pair_count(first_width, yx);
  uint32_t x = yx & right_n_bits(first_width);
  uint32_t y = yx >> first_width;
  // X and Y usually fit in YX, if the workload cooperates
  if (n > 1) {   // oops, X did not fit
    // second most common case: Y fits in YX but not X (or X=0)
    x = read_uint();
    y -= 1;  // when x was big, y bitfield was incremented mod 2^(32-S)
    if (n > 2) {
      // third case: Y does not fit in YX (or X=Y=0)
      y = read_uint();
    }
  }
  first_result = x;
  second_result = y;
  return n;
}

template<typename WRITE_UINT>
inline int UNSIGNED5::write_uint_pair(int first_width,
                                      uint32_t first,
                                      uint32_t second,
                                      WRITE_UINT write_uint) {
  assert(first_width >= 0 && first_width <= 31, "");
  first_width &= 31;
  const uint32_t yx = encoded_pair_lead(first_width, first, second);
  const int      n  = encoded_pair_count(first_width, first, second);
  write_uint(yx);
  if (n > 1) {
    write_uint(first);
    if (n > 2) {
      write_uint(second);
    }
  }
  return n;
}

#endif // SHARE_UTILITIES_UNSIGNED5_INLINE_HPP
