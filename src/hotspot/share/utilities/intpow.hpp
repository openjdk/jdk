/*
 * Copyright (c) 2024, Arm Limited. All rights reserved.
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

#ifndef SHARE_UTILITIES_INTPOW_HPP
#define SHARE_UTILITIES_INTPOW_HPP

#include "metaprogramming/enableIf.hpp"

#include <limits>
#include <type_traits>

// Raise v to the power p mod 2**N, where N is the width of the type T.
template <typename T, ENABLE_IF(std::is_integral<T>::value && std::is_unsigned<T>::value)>
static constexpr T intpow(T v, unsigned p) {
  if (p == 0) {
    return 1;
  }

  // We use exponentiation by squaring to calculate the required power.
  T a = intpow(v, p / 2);
  T b = (p % 2) ? v : 1;

  return a * a * b;
}

#endif // SHARE_UTILITIES_INTPOW_HPP
