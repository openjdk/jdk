/*
 * Copyright (c) 2019, 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_POPULATION_COUNT_HPP
#define SHARE_UTILITIES_POPULATION_COUNT_HPP

// Population counting for 8-bit, 16-bit, 32-bit, and 64-bit unsigned integers. Population counting
// is the number of set bits in an integer.

// unsigned population_count<T>(T)
//
// Counts the number of set bits in the value of unsigned integer type T.

#include "metaprogramming/enableIf.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

#include <type_traits>

template <typename T>
using CanPopulationCountImpl = std::integral_constant<bool, (std::is_integral<T>::value &&
                                                             std::is_unsigned<T>::value &&
                                                             sizeof(T) <= 8)>;

template <typename T>
struct PopulationCountImpl;

template <typename T, ENABLE_IF(CanPopulationCountImpl<T>::value)>
ALWAYSINLINE unsigned population_count(T x) {
  return PopulationCountImpl<T>{}(x);
}

/*****************************************************************************
 * Fallback
 *****************************************************************************/

template <typename T>
struct PopulationCountFallbackImpl {
  STATIC_ASSERT(CanPopulationCountImpl<T>::value);

  // Adapted from Hacker's Delight, 2nd Edition, Figure 5-2 and the text that
  // follows.
  ALWAYSINLINE unsigned operator()(T x) const {
    // We need to take care with implicit integer promotion when dealing with
    // integers < 32-bit. We chose to do this by explicitly widening constants
    // to unsigned
    using P = std::conditional_t<(sizeof(T) < sizeof(unsigned)), unsigned, T>;
    const T all = ~T(0);           // 0xFF..FF
    const P fives = all/3;         // 0x55..55
    const P threes = (all/15) * 3; // 0x33..33
    const P z_ones = all/255;      // 0x0101..01
    const P z_effs = z_ones * 15;  // 0x0F0F..0F
    P r = x;
    r -= ((r >> 1) & fives);
    r = (r & threes) + ((r >> 2) & threes);
    r = ((r + (r >> 4)) & z_effs) * z_ones;
    // The preceding multiply by z_ones is the only place where the intermediate
    // calculations can exceed the range of T. We need to discard any such excess
    // before the right-shift, hence the conversion back to T.
    return static_cast<T>(r) >> (((sizeof(T) - 1) * BitsPerByte));
  }
};

/*****************************************************************************
 * GCC and compatible (including Clang)
 *****************************************************************************/
#if defined(TARGET_COMPILER_gcc)

#if defined(__clang__) || defined(ASSERT)

// Unlike GCC, Clang is willing to inline the generic implementation of __builtin_popcount when
// architecture support is unavailable in -O2. This ensures we avoid the function call to libgcc.
// Clang is able to recognize the fallback implementation as byteswapping, but not on every
// architecture unlike GCC. This suggests the optimization pass for GCC that recognizes byteswapping
// is architecture agnostic, while for Clang it is not.

template <typename T>
struct PopulationCountImpl final {
  STATIC_ASSERT(CanPopulationCountImpl<T>::value);

  // Smaller integer types will be handled via integer promotion to unsigned int.

  ALWAYSINLINE unsigned operator()(unsigned int x) const {
    STATIC_ASSERT(sizeof(T) <= sizeof(unsigned int));
    return __builtin_popcount(x);
  }

  ALWAYSINLINE unsigned operator()(unsigned long x) const {
    STATIC_ASSERT(sizeof(T) == sizeof(unsigned long));
    return __builtin_popcountl(x);
  }

  ALWAYSINLINE unsigned operator()(unsigned long long x) const {
    STATIC_ASSERT(sizeof(T) == sizeof(unsigned long long));
    return __builtin_popcountll(x);
  }
};

#else

// We do not use __builtin_popcount and friends for GCC in release builds. Unfortunately on
// architectures that do not have a popcount instruction, GCC emits a function call to libgcc
// regardless of optimization options, even when the generic implementation is, for example, less
// than 20 instructions. GCC is however able to recognize the fallback as popcount regardless of
// architecture and appropriately replaces the code in -O2 with the appropriate
// architecture-specific byteswap instruction, if available. If it is not available, GCC emits the
// exact same implementation that underpins its __builtin_bswap in libgcc as there is really only
// one way to implement it, as we have in fallback.

template <typename T>
struct PopulationCountImpl final : public PopulationCountFallbackImpl<T> {};

#endif

/*****************************************************************************
 * Microsoft Visual Studio
 *****************************************************************************/
#elif defined(TARGET_COMPILER_visCPP)

#if defined(AARCH64)

#include <intrin.h>

#pragma intrinsic(_CountOneBits)
#pragma intrinsic(_CountOneBits64)

template <typename T>
struct PopulationCountImpl final {
  STATIC_ASSERT(CanPopulationCountImpl<T>::value);

  // Smaller integer types will be handled via integer promotion to unsigned long.

  ALWAYSINLINE unsigned operator()(unsigned long x) const {
    STATIC_ASSERT(sizeof(T) <= sizeof(unsigned long));
    return _CountOneBits(x);
  }

  ALWAYSINLINE unsigned operator()(unsigned __int64 x) const {
    STATIC_ASSERT(sizeof(T) == sizeof(unsigned __int64));
    return _CountOneBits64(x);
  }
};

#else

template <typename T>
struct PopulationCountImpl final : public PopulationCountFallbackImpl<T> {};

#endif

/*****************************************************************************
 * IBM XL C/C++
 *****************************************************************************/
#elif defined(TARGET_TOOLCHAIN_xlc)

#include <builtins.h>

template <typename T>
struct PopulationCountImpl final {
  STATIC_ASSERT(CanPopulationCountImpl<T>::value);

  // Smaller integer types will be handled via integer promotion to unsigned int.

  ALWAYSINLINE unsigned operator()(unsigned int x) const {
    STATIC_ASSERT(sizeof(T) <= sizeof(unsigned int));
    return __popcnt4(x);
  }

  ALWAYSINLINE unsigned operator()(unsigned long x) const {
    STATIC_ASSERT(sizeof(T) == sizeof(unsigned long));
    return sizeof(unsigned long) == sizeof(unsigned int) ? __popcnt4(static_cast<unsigned int>(x)) :
                                                           __popcnt8(x);
  }

  ALWAYSINLINE unsigned operator()(unsigned long long x) const {
    STATIC_ASSERT(sizeof(T) == sizeof(unsigned long long));
    return __popcnt8(x);
  }
};

/*****************************************************************************
 * Unknown toolchain
 *****************************************************************************/
#else

#error Unknown toolchain.

#endif

#endif // SHARE_UTILITIES_POPULATION_COUNT_HPP
