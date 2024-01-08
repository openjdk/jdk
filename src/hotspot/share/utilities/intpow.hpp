/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

template <typename T, T v, unsigned p, bool no_overflow = false,
          typename = std::enable_if_t<std::is_integral<T>::value && std::is_unsigned<T>::value>>
struct intpow {
  static_assert(v || p, "0^0 is not defined");

 private:
  // We use exponentiation by squaring to calculate the required power.
  static const T _a = intpow<T, v, p / 2, no_overflow>::value;
  static const T _b = (p % 2) ? v : 1;

  static_assert(!no_overflow || _a <= std::numeric_limits<T>::max() / _a, "Integer overflow");
  static_assert(!no_overflow || _a * _a <= std::numeric_limits<T>::max() / _b, "Integer overflow");

 public:
  static const T value = _a * _a * _b;
};

template <typename T, T v, bool no_overflow>
struct intpow<T, v, 0, no_overflow> {
  static const T value = 1;
};

template <typename T, T v, bool no_overflow>
struct intpow<T, v, 1, no_overflow> {
  static const T value = v;
};
#endif // SHARE_UTILITIES_INTPOW_HPP
