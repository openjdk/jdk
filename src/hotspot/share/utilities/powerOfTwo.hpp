/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_POWEROFTWO_HPP
#define SHARE_UTILITIES_POWEROFTWO_HPP

#include "metaprogramming/enableIf.hpp"
#include "metaprogramming/isIntegral.hpp"
#include "metaprogramming/isSigned.hpp"
#include "utilities/count_leading_zeros.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

// Power of two convenience library.

// Round down to the closest power of two greater to or equal to the given
// value.

// Signed version: 0 is an invalid input, negative values are invalid
template <typename T>
inline typename EnableIf<IsSigned<T>::value, T>::type round_down_power_of_2(T value) {
  STATIC_ASSERT(IsIntegral<T>::value);
  assert(value > 0, "Invalid value");
  uint32_t lz = count_leading_zeros(value);
  assert(lz < sizeof(T) * BitsPerByte, "Sanity");
  return T(1) << (sizeof(T) * BitsPerByte - 1 - lz);
}

// Unsigned version: 0 is an invalid input
template <typename T>
inline typename EnableIf<!IsSigned<T>::value, T>::type round_down_power_of_2(T value) {
  STATIC_ASSERT(IsIntegral<T>::value);
  assert(value != 0, "Invalid value");
  uint32_t lz = count_leading_zeros(value);
  assert(lz < sizeof(T) * BitsPerByte, "Sanity");
  return T(1) << (sizeof(T) * BitsPerByte - 1 - lz);
}

// Round up to the closest power of two greater to or equal to
// the given value.

// Signed version: 0 is an invalid input, negative values are invalid,
// overflows with assert if value is larger than 2^30 or 2^62 for 32- and
// 64-bit integers, respectively
template <typename T>
inline typename EnableIf<IsSigned<T>::value, T>::type round_up_power_of_2(T value) {
  STATIC_ASSERT(IsIntegral<T>::value);
  STATIC_ASSERT(IsSigned<T>::value);
  assert(value > 0, "Invalid value");
  if (is_power_of_2(value)) {
    return value;
  }
  uint32_t lz = count_leading_zeros(value);
  assert(lz < sizeof(T) * BitsPerByte, "Sanity");
  assert(lz > 1, "Will overflow");
  return T(1) << (sizeof(T) * BitsPerByte - lz);
}

// Unsigned version: 0 is an invalid input, overflows with assert if value
// is larger than 2^31 or 2^63 for 32- and 64-bit integers, respectively
template <typename T>
inline typename EnableIf<!IsSigned<T>::value, T>::type round_up_power_of_2(T value) {
  STATIC_ASSERT(IsIntegral<T>::value);
  STATIC_ASSERT(!IsSigned<T>::value);
  assert(value != 0, "Invalid value");
  if (is_power_of_2(value)) {
    return value;
  }
  uint32_t lz = count_leading_zeros(value);
  assert(lz < sizeof(T) * BitsPerByte, "Sanity");
  assert(lz > 0, "Will overflow");
  return T(1) << (sizeof(T) * BitsPerByte - lz);
}

// Helper function to get the maximum positive value. Implemented here
// since using std::numeric_limits<T>::max() seems problematic on some
// platforms.

template <typename T> T max_value() {
  if (IsSigned<T>::value) {
    // Highest positive power of two expressible in the type
    uint64_t val = static_cast<T>(1) << (sizeof(T) * BitsPerByte - 2);
    // Fill lower bits with ones
    val |= val >> 1;
    val |= val >> 2;
    val |= val >> 4;
    if (sizeof(T) >= 2)  val |= val >> 8;
    if (sizeof(T) >= 4)  val |= val >> 16;
    if (sizeof(T) == 8)  val |= val >> 32;
    return (T)val;
  } else {
    return ~(static_cast<T>(0));
  }
}

// Calculate the next power of two greater than the given value.

// Accepts 0 (returns 1), overflows with assert if value is larger than
// or equal to 2^31 (signed: 2^30) or 2^63 (signed: 2^62), for 32-
// and 64-bit integers, respectively
template <typename T>
inline T next_power_of_2(T value)  {
  assert(value != max_value<T>(), "Overflow");
  return round_up_power_of_2(value + 1);
}

#endif // SHARE_UTILITIES_POWEROFTWO_HPP
