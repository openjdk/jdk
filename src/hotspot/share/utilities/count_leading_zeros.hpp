/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_COUNT_LEADING_ZEROS_HPP
#define SHARE_UTILITIES_COUNT_LEADING_ZEROS_HPP

#include "metaprogramming/enableIf.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

#include <type_traits>

template <typename T>
struct CountLeadingZerosImpl;

// unsigned count_leading_zeros<T>(T)
//
// Precondition: x != 0.
//
// Count the number of leading (starting from the MSB) zero bits in an unsigned integer. Also known
// as the zero-based index of the first set most significant bit.
template <typename T, ENABLE_IF(std::is_integral<T>::value)>
inline unsigned count_leading_zeros(T x) {
  precond(x != 0);
  using U = std::make_unsigned_t<T>;
  using P = std::conditional_t<(sizeof(U) < sizeof(unsigned int)), unsigned int, U>;
  return CountLeadingZerosImpl<U>{}(static_cast<P>(static_cast<U>(x)));
}

/*****************************************************************************
 * GCC and compatible (including Clang)
 *****************************************************************************/
#if defined(TARGET_COMPILER_gcc) || defined(TARGET_COMPILER_xlc)

template <typename T>
struct CountLeadingZerosImpl {
  inline unsigned operator()(unsigned int x) const {
    return __builtin_clz(x) - ((sizeof(unsigned int) - sizeof(T)) * 8);
  }

  inline unsigned operator()(unsigned long x) const {
    return __builtin_clzl(x);
  }

  inline unsigned operator()(unsigned long long x) const {
    return __builtin_clzll(x);
  }
};

/*****************************************************************************
 * Microsoft Visual Studio
 *****************************************************************************/
#elif defined(TARGET_COMPILER_visCPP)

#include <intrin.h>

#if defined(AARCH64)

#pragma intrinsic(_CountLeadingZeros)
#pragma intrinsic(_CountLeadingZeros64)

template <typename T>
struct CountLeadingZerosImpl {
  inline unsigned operator()(unsigned int x) const {
    return (*this)(static_cast<unsigned long>(x));
  }

  inline unsigned operator()(unsigned long x) const {
    return _CountLeadingZeros(x) - ((sizeof(unsigned long) - sizeof(T)) * 8);
  }

  inline unsigned operator()(unsigned __int64 x) const {
    return _CountLeadingZeros64(x);
  }
};

#else

#pragma intrinsic(_BitScanReverse)
#ifdef _LP64
#pragma intrinsic(_BitScanReverse64)
#endif

template <typename T>
struct CountLeadingZerosImpl {
  inline unsigned operator()(unsigned int x) const {
    return (*this)(static_cast<unsigned long>(x));
  }

  inline unsigned operator()(unsigned long x) const {
    unsigned long index;
    unsigned char result = _BitScanReverse(&index, x);
    postcond(result != 0);
    return static_cast<unsigned>((sizeof(T) * 8 - 1) - index);
  }

  inline unsigned operator()(unsigned __int64 x) const {
#ifdef _LP64
    unsigned long index;
    unsigned char result = _BitScanReverse64(&index, x);
    postcond(result != 0);
    return static_cast<unsigned>(63ul - index);
#else
    unsigned long index;
    unsigned long high = static_cast<unsigned long>(x >> 32);
    if (high != 0ul) {
      unsigned char result = _BitScanReverse(&index, high);
      postcond(result != 0);
      index += 31ul;
    } else {
      unsigned char result = _BitScanReverse(&index, static_cast<unsigned long>(x));
      postcond(result != 0);
    }
    return static_cast<unsigned>(63ul - index);
#endif
  }
};

#endif

/*****************************************************************************
 * Unknown toolchain
 *****************************************************************************/
#else

#error Unknown compiler.

#endif

#endif // SHARE_UTILITIES_COUNT_LEADING_ZEROS_HPP
