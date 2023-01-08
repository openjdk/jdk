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
#include "utilities/globalDefinitions.hpp"
#include <type_traits>

template <typename T>
class ReverseBitsImpl {
  static const size_t NB = sizeof(T) * BitsPerByte;

  static_assert((NB == 8) || (NB == 16) || (NB == 32) || (NB == 64),
                "unsupported size");

  // The unsigned integral type for calculations.
  using I = typename Conditional<NB <= 32, uint32_t, uint64_t>::type;

  static const I rep_5555 = static_cast<I>(UCONST64(0x5555555555555555));
  static const I rep_3333 = static_cast<I>(UCONST64(0x3333333333333333));
  static const I rep_0F0F = static_cast<I>(UCONST64(0x0F0F0F0F0F0F0F0F));
  static const I rep_00FF = static_cast<I>(UCONST64(0x00FF00FF00FF00FF));
  static const I rep_FFFF = static_cast<I>(UCONST64(0x0000FFFF0000FFFF));

public:

  static constexpr T reverse_bits_in_bytes(T v) {
    // Based on Hacker's Delight Section 7-1
    auto x = static_cast<I>(v);
    x = ((x & rep_5555) << 1) | ((x >> 1) & rep_5555);
    x = ((x & rep_3333) << 2) | ((x >> 2) & rep_3333);
    x = ((x & rep_0F0F) << 4) | ((x >> 4) & rep_0F0F);
    return static_cast<T>(x);
  }

  static constexpr T reverse_bytes(T v) {
    // Based on Hacker's Delight Section 7-1
    // NB: Compilers are good at recognizing byte-swap code and transforming
    // it into platform-specific instructions like x86 bswap.
    auto x = static_cast<I>(v);
    switch (NB) {
    case 64:
      // The use of NB/2 rather than 32 avoids a warning in dead code when
      // I is uint32_t, because shifting a 32bit type by 32 is UB.
      x = (x << (NB/2)) | (x >> (NB/2));
    case 32:                    // fallthrough
      x = ((x & rep_FFFF) << 16) | ((x >> 16) & rep_FFFF);
    case 16:                    // fallthrough
      x = ((x & rep_00FF) << 8)  | ((x >> 8)  & rep_00FF);
    default:                    // fallthrough
      return static_cast<T>(x);
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
