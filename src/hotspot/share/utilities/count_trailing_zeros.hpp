/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

#include "metaprogramming/conditional.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

// unsigned count_trailing_zeros(T x)

// Return the number of trailing zeros in x, e.g. the zero-based index
// of the least significant set bit in x.
// Precondition: x != 0.

// We implement and support variants for 8, 16, 32 and 64 bit integral types.
template <typename T, size_t n> struct CountTrailingZerosImpl;

// Dispatch on toolchain to select implementation.

template <typename T> unsigned count_trailing_zeros(T v) {
  assert(v != 0, "precondition");

  // Widen subword types to uint32_t
  typedef typename Conditional<(sizeof(T) < sizeof(uint32_t)), uint32_t, T>::type P;
  return CountTrailingZerosImpl<T, sizeof(P)>::doit(static_cast<P>(v));
}

/*****************************************************************************
 * GCC and compatible (including Clang)
 *****************************************************************************/
#if defined(TARGET_COMPILER_gcc)

template <typename T> struct CountTrailingZerosImpl<T, 4> {
  static unsigned doit(T v) {
    return __builtin_ctz((uint32_t)v);
  }
};

template <typename T> struct CountTrailingZerosImpl<T, 8> {
  static unsigned doit(T v) {
    return __builtin_ctzll((uint64_t)v);
  }
};

/*****************************************************************************
 * Microsoft Visual Studio
 *****************************************************************************/
#elif defined(TARGET_COMPILER_visCPP)

#include <intrin.h>

#ifdef _LP64
#pragma intrinsic(_BitScanForward64)
#else
#pragma intrinsic(_BitScanForward)
#endif

template <typename T> struct CountTrailingZerosImpl<T, 4> {
  static unsigned doit(T v) {
    unsigned long index;
    _BitScanForward(&index, v);
    return index;
  }
};

template <typename T> struct CountTrailingZerosImpl<T, 8> {
  static unsigned doit(T v) {
    unsigned long index;
#ifdef _LP64
    _BitScanForward64(&index, v);
#else
    _BitScanForward(&index, (uint32_t)v);
    if (index == 0) {
      _BitScanForward(&index, (uint32_t)(v >> 32));
      if (index > 0) {
        // bit found in high word, adjust
        index += 32;
      }
    }
#endif
    return index;
  }
};

/*****************************************************************************
 * IBM XL C/C++
 *****************************************************************************/
#elif defined(TARGET_COMPILER_xlc)

#include <builtins.h>

template <typename T> struct CountTrailingZerosImpl<T, 4> {
  static unsigned doit(T v) {
    return __cnttz4((uint32_t)v);
  }
};

template <typename T> struct CountTrailingZerosImpl<T, 8> {
  static unsigned doit(T v) {
    return __cnttz8((uint64_t)v);
  }
};

/*****************************************************************************
 * Unknown toolchain
 *****************************************************************************/
#else
#error Unknown TARGET_COMPILER

#endif // Toolchain dispatch

#endif // SHARE_UTILITIES_COUNT_TRAILING_ZEROS_HPP
