/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, Google and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_REVERSE_BITS_HPP
#define SHARE_UTILITIES_REVERSE_BITS_HPP

#include "metaprogramming/enableIf.hpp"
#include "utilities/byteswap.hpp"
#include "utilities/globalDefinitions.hpp"

#include <cstddef>
#include <cstdint>
#include <type_traits>

template <typename T, size_t N = sizeof(T)>
struct ReverseBitsImpl;

// T reverse_bits<T>(T)
//
// Reverses the bits in the integral value of type T.
template <typename T, ENABLE_IF(std::is_integral<T>::value)>
inline T reverse_bits(T x) {
  using U = std::make_unsigned_t<T>;
  return static_cast<T>(ReverseBitsImpl<U>{}(static_cast<U>(x)));
}

/*****************************************************************************
 * Fallback
 *****************************************************************************/

template <typename T>
struct ReverseBitsFallbackImpl {
 private:
  // The unsigned integral type for calculations.
  using U = std::conditional_t<sizeof(T) <= 4, uint32_t, uint64_t>;

  static constexpr U rep_5555 = static_cast<U>(UINT64_C(0x5555555555555555));
  static constexpr U rep_3333 = static_cast<U>(UINT64_C(0x3333333333333333));
  static constexpr U rep_0F0F = static_cast<U>(UINT64_C(0x0F0F0F0F0F0F0F0F));

 public:
  inline T operator()(T v) const {
    // Based on Hacker's Delight Section 7-1
    U x = static_cast<U>(v);
    x = ((x & rep_5555) << 1) | ((x >> 1) & rep_5555);
    x = ((x & rep_3333) << 2) | ((x >> 2) & rep_3333);
    x = ((x & rep_0F0F) << 4) | ((x >> 4) & rep_0F0F);
    return byteswap(static_cast<T>(x));
  }
};


/*****************************************************************************
 * GCC and compatible (including Clang)
 *****************************************************************************/
#if defined(TARGET_COMPILER_gcc) || defined(TARGET_COMPILER_xlc)

// Default implementation for GCC-like compilers is the fallback. At the time of writing GCC does
// not have intrinsics for bit reversal while Clang does.

template <typename T, size_t N>
struct ReverseBitsImpl : public ReverseBitsFallbackImpl<T> {};

#ifdef __has_builtin

#if __has_builtin(__builtin_bitreverse8)

template <typename T>
struct ReverseBitsImpl<T, 1> {
  inline uint8_t operator()(uint8_t x) const {
    return __builtin_bitreverse8(x);
  }
};

#endif // __has_builtin(__builtin_bitreverse8)

#if __has_builtin(__builtin_bitreverse16)

template <typename T>
struct ReverseBitsImpl<T, 2> {
  inline uint16_t operator()(uint16_t x) const {
    return __builtin_bitreverse16(x);
  }
};

#endif // __has_builtin(__builtin_bitreverse16)

#if __has_builtin(__builtin_bitreverse32)

template <typename T>
struct ReverseBitsImpl<T, 4> {
  inline uint32_t operator()(uint32_t x) const {
    return __builtin_bitreverse32(x);
  }
};

#endif // __has_builtin(__builtin_bitreverse32)

#if __has_builtin(__builtin_bitreverse64)

template <typename T>
struct ReverseBitsImpl<T, 8> {
  inline uint64_t operator()(uint64_t x) const {
    return __builtin_bitreverse64(x);
  }
};

#endif // __has_builtin(__builtin_bitreverse64)

#endif // __has_builtin

/*****************************************************************************
 * Microsoft Visual Studio
 *****************************************************************************/
#elif defined(TARGET_COMPILER_visCPP)

template <typename T, size_t N>
struct ReverseBitsImpl : public ReverseBitsFallbackImpl<T> {};

/*****************************************************************************
 * Unknown toolchain
 *****************************************************************************/
#else

#error Unknown toolchain.

#endif

#endif // SHARE_UTILITIES_REVERSE_BITS_HPP
