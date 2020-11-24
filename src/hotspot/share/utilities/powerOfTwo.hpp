/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/count_leading_zeros.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include <limits>
#include <type_traits>

// Power of two convenience library.

template <typename T, ENABLE_IF(std::is_integral<T>::value)>
constexpr T max_power_of_2() {
  T max_val = std::numeric_limits<T>::max();
  return max_val - (max_val >> 1);
}

// Returns true iff there exists integer i such that (T(1) << i) == x.
template <typename T, ENABLE_IF(std::is_integral<T>::value)>
constexpr bool is_power_of_2(T x) {
  return (x > T(0)) && ((x & (x - 1)) == T(0));
}

// Log2 of a power of 2
inline int exact_log2(intptr_t x) {
  assert(is_power_of_2((uintptr_t)x), "x must be a power of 2: " INTPTR_FORMAT, x);

  const int bits = sizeof x * BitsPerByte;
  return bits - count_leading_zeros(x) - 1;
}

// Log2 of a power of 2
inline int exact_log2_long(jlong x) {
  assert(is_power_of_2((julong)x), "x must be a power of 2: " JLONG_FORMAT, x);

  const int bits = sizeof x * BitsPerByte;
  return bits - count_leading_zeros(x) - 1;
}

// Round down to the closest power of two less than or equal to the given value.
// precondition: value > 0.
template<typename T, ENABLE_IF(std::is_integral<T>::value)>
inline T round_down_power_of_2(T value) {
  assert(value > 0, "Invalid value");
  uint32_t lz = count_leading_zeros(value);
  return T(1) << (sizeof(T) * BitsPerByte - 1 - lz);
}

// Round up to the closest power of two greater to or equal to the given value.
// precondition: value > 0.
// precondition: value <= maximum power of two representable by T.
template<typename T, ENABLE_IF(std::is_integral<T>::value)>
inline T round_up_power_of_2(T value) {
  assert(value > 0, "Invalid value");
  assert(value <= max_power_of_2<T>(), "Overflow");
  if (is_power_of_2(value)) {
    return value;
  }
  uint32_t lz = count_leading_zeros(value);
  return T(1) << (sizeof(T) * BitsPerByte - lz);
}

// Calculate the next power of two greater than the given value.
// precondition: if signed, value >= 0.
// precondition: value < maximum power of two representable by T.
template <typename T, ENABLE_IF(std::is_integral<T>::value)>
inline T next_power_of_2(T value)  {
  assert(value < std::numeric_limits<T>::max(), "Overflow");
  return round_up_power_of_2(value + 1);
}

#endif // SHARE_UTILITIES_POWEROFTWO_HPP
