/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/count_trailing_zeros.hpp"

#if defined(TARGET_COMPILER_visCPP)
#include <intrin.h>
#pragma intrinsic(_BitScanReverse)
#elif defined(TARGET_COMPILER_xlc)
#include <builtins.h>
#endif

// uint32_t count_leading_zeros(uint32_t x)
// Return the number of leading zeros in x, e.g. the zero-based index
// of the most significant set bit in x.  Undefined for 0.
inline uint32_t count_leading_zeros(uint32_t x) {
  assert(x != 0, "precondition");
#if defined(TARGET_COMPILER_gcc)
  return __builtin_clz(x);
#elif defined(TARGET_COMPILER_visCPP)
  unsigned long index;
  _BitScanReverse(&index, x);
  return index ^ 31u;
#elif defined(TARGET_COMPILER_xlc)
  return __cntlz4(x);
#else
  // Efficient and portable fallback implementation:
  // http://graphics.stanford.edu/~seander/bithacks.html#IntegerLogDeBruijn
  // - with positions xor'd by 31 to get number of leading zeros
  // rather than position of highest bit.
  static const int MultiplyDeBruijnBitPosition[32] = {
      31, 22, 30, 21, 18, 10, 29,  2, 20, 17, 15, 13, 9,  6, 28, 1,
      23, 19, 11,  3, 16, 14,  7, 24, 12,  4,  8, 25, 5, 26, 27, 0
  };

  x |= x >> 1; // first round down to one less than a power of 2
  x |= x >> 2;
  x |= x >> 4;
  x |= x >> 8;
  x |= x >> 16;
  return MultiplyDeBruijnBitPosition[(uint32_t)( x * 0x07c4acddu ) >> 27];
#endif
}

#endif // SHARE_UTILITIES_COUNT_LEADING_ZEROS_HPP
