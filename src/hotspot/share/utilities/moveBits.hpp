/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_MOVEBITS_HPP
#define SHARE_UTILITIES_MOVEBITS_HPP

#include "metaprogramming/conditional.hpp"
#include "metaprogramming/enableIf.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include <type_traits>

template <typename T>
class ReverseBitsImpl {
  static const size_t S = sizeof(T);

  static_assert((S == 1) || (S == 2) || (S == 4) || (S == 8), "unsupported size");

  static const uint64_t rep_5555 = UCONST64(0x5555555555555555);
  static const uint64_t rep_3333 = UCONST64(0x3333333333333333);
  static const uint64_t rep_0F0F = UCONST64(0x0F0F0F0F0F0F0F0F);
  static const uint64_t rep_00FF = UCONST64(0x00FF00FF00FF00FF);
  static const uint64_t rep_FFFF = UCONST64(0x0000FFFF0000FFFF);

  using I = typename Conditional<S <= 4, uint32_t, uint64_t>::type;

  // Avoid 32bit shift of uint32_t that some compilers might warn about even
  // though the relevant code will never be executed.  For example, gcc warns
  // about -Wshift-count-overflow.
  static constexpr uint32_t swap64(uint32_t x) { ShouldNotReachHere(); return x; }
  static constexpr uint64_t swap64(uint64_t x) { return (x << 32) | (x >> 32); }

public:

  static constexpr T reverse_bits_in_bytes(T v) {
    // Based on Hacker's Delight Section 7-1
    auto x = static_cast<I>(v);
    x = ((x & (I)rep_5555) << 1) | ((x >> 1) & (I)rep_5555);
    x = ((x & (I)rep_3333) << 2) | ((x >> 2) & (I)rep_3333);
    x = ((x & (I)rep_0F0F) << 4) | ((x >> 4) & (I)rep_0F0F);
    return x;
  }

  static constexpr T reverse_bytes(T v) {
    // Based on Hacker's Delight Section 7-1
    // NB: Compilers are good at recognizing byte-swap code and transforming
    // it into platform-specific instructions like x86 bswap.
    auto x = static_cast<I>(v);
    switch (S) {
    case 8:
      x = swap64(x);
    case 4:                     // fallthrough
      x = ((x & (I)rep_FFFF) << 16) | ((x >> 16) & (I)rep_FFFF);
    case 2:                     // fallthrough
      x = ((x & (I)rep_00FF) << 8)  | ((x >> 8)  & (I)rep_00FF);
    default:                    // fallthrough
      return x;
    }
  }
};

// Performs byte reversal of an integral type up to 64 bits.
template <typename T, ENABLE_IF(std::is_integral<T>::value)>
constexpr T reverse_bytes(T x) {
  return ReverseBitsImpl<T>::reverse_bytes(x);
}

// Performs bytewise bit reversal of each byte of an integral
// type up to 64 bits.
template <typename T, ENABLE_IF(std::is_integral<T>::value)>
constexpr T reverse_bits_in_bytes(T x) {
  return ReverseBitsImpl<T>::reverse_bits_in_bytes(x);
}

// Performs full bit reversal an integral type up to 64 bits.
template <typename T, ENABLE_IF(std::is_integral<T>::value)>
constexpr T reverse_bits(T x) {
  return reverse_bytes(reverse_bits_in_bytes(x));
}

#endif // SHARE_UTILITIES_MOVEBITS_HPP
