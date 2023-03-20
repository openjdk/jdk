/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_COUNT_TRAILING_ZEROS_HPP
#define SHARE_UTILITIES_COUNT_TRAILING_ZEROS_HPP

#include "metaprogramming/enableIf.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

#include <type_traits>

template <typename T>
struct CountTrailingZerosImpl;

// unsigned count_trailing_zeros<T>(T)
//
// Precondition: x != 0.
//
// Count the number of trailing (starting from the LSB) zero bits in an unsigned integer. Also known
// as the zero-based index of the first set least significant bit.
template <typename T, ENABLE_IF(std::is_integral<T>::value)>
inline unsigned count_trailing_zeros(T x) {
  precond(x != 0);
  using U = std::make_unsigned_t<T>;
  using P = std::conditional_t<(sizeof(U) < sizeof(unsigned int)), unsigned int, U>;
  return CountTrailingZerosImpl<U>{}(static_cast<P>(static_cast<U>(x)));
}

/*****************************************************************************
 * GCC and compatible (including Clang)
 *****************************************************************************/
#if defined(TARGET_COMPILER_gcc) || defined(TARGET_COMPILER_xlc)

template <>
struct CountTrailingZerosImpl<unsigned int> {
  inline unsigned operator()(unsigned int x) const {
    return __builtin_ctz(x);
  }
};

template <>
struct CountTrailingZerosImpl<unsigned long> {
  inline unsigned operator()(unsigned long x) const {
    return __builtin_ctzl(x);
  }
};

template <>
struct CountTrailingZerosImpl<unsigned long long> {
  inline unsigned operator()(unsigned long long x) const {
    return __builtin_ctzll(x);
  }
};

/*****************************************************************************
 * Microsoft Visual Studio
 *****************************************************************************/
#elif defined(TARGET_COMPILER_visCPP)

#include <intrin.h>

#pragma intrinsic(_BitScanForward)
#ifdef _LP64
#pragma intrinsic(_BitScanForward64)
#endif

template <>
struct CountTrailingZerosImpl<unsigned int> {
  inline unsigned operator()(unsigned int x) const {
    return CountTrailingZerosImpl<unsigned long>{}(x);
  }
};

template <>
struct CountTrailingZerosImpl<unsigned long> {
  inline unsigned operator()(unsigned long x) const {
    unsigned long index;
    unsigned char result = _BitScanForward(&index, x);
    postcond(result != 0);
    return static_cast<unsigned>(index);
  }
};

template <>
struct CountTrailingZerosImpl<unsigned long long> {
  inline unsigned operator()(unsigned long long x) const {
#ifdef _LP64
    unsigned long index;
    unsigned char result = _BitScanForward64(&index, x);
    postcond(result != 0);
    return static_cast<unsigned>(index);
#else
    unsigned long index;
    unsigned long low = static_cast<unsigned long>(x);
    if (low != 0ul) {
      unsigned char result = _BitScanForward(&index, low);
      postcond(result != 0);
    } else {
      unsigned char result = _BitScanForward(&index, static_cast<unsigned long>(x >> 32));
      postcond(result != 0);
      index += 32ul;
    }
    return static_cast<unsigned>(index);
#endif
  }
};

/*****************************************************************************
 * Unknown toolchain
 *****************************************************************************/
#else

#error Unknown toolchain.

#endif

#endif // SHARE_UTILITIES_COUNT_TRAILING_ZEROS_HPP
