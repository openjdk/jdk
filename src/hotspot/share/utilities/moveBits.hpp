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

#ifndef SHARE_UTILITIES_MOVE_BITS_HPP
#define SHARE_UTILITIES_MOVE_BITS_HPP

#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

inline uint64_t bit_reverse(uint64_t x) {
  x = (x & 0x5555555555555555L) << 1 | (x & 0xAAAAAAAAAAAAAAAAL) >> 1;
  x = (x & 0x3333333333333333L) << 2 | (x & 0xCCCCCCCCCCCCCCCCL) >> 2;
  x = (x & 0x0F0F0F0F0F0F0F0FL) << 4 | (x & 0xF0F0F0F0F0F0F0F0L) >> 4;
  return x;
}

inline uint64_t byte_reverse(uint64_t x, uint8_t bw) {
  switch(bw) {
    case 64:
      x = (x & 0x00000000FFFFFFFFL) << 32 | (x & 0xFFFFFFFF00000000L) >> 32;
    case 32:
      x = (x & 0x0000FFFF0000FFFFL) << 16 | (x & 0xFFFF0000FFFF0000L) >> 16;
    case 16:
      x = (x & 0x00FF00FF00FF00FFL) << 8 | (x & 0xFF00FF00FF00FF00L) >> 8;
    default:
      break;
  }
  return x;
}

template <typename T, uint8_t S> struct ReverseBitsImpl {};


template <typename T> struct ReverseBitsImpl<T, 1> {
  static T doit(T v) {
    return bit_reverse((uint64_t)v);
  }
};

/*****************************************************************************
 * GCC and compatible (including Clang)
 *****************************************************************************/
#if defined(TARGET_COMPILER_gcc)
template <typename T> struct ReverseBitsImpl<T, 2> {
  static T doit(T v) {
    uint64_t r = bit_reverse((uint64_t)v);
    return __builtin_bswap16((uint16_t)r);
  }
};

template <typename T> struct ReverseBitsImpl<T, 4> {
  static T doit(T v) {
    uint64_t r = bit_reverse((uint64_t)v);
    return __builtin_bswap32((uint32_t)r);
  }
};

template <typename T> struct ReverseBitsImpl<T, 8> {
  static T doit(T v) {
    uint64_t r = bit_reverse((uint64_t)v);
    return __builtin_bswap64(r);
  }
};

/*****************************************************************************
 * Fallback
 *****************************************************************************/
#else
template <typename T> struct ReverseBitsImpl<T, 2> {
  static T doit(T v) {
    uint64_t r = bit_reverse((uint64_t)v);
    return byte_reverse(r, 16);
  }
};

template <typename T> struct ReverseBitsImpl<T, 4> {
  static T doit(T v) {
    uint64_t r = bit_reverse((uint64_t)v);
    return byte_reverse(r, 32);
  }
};

template <typename T> struct ReverseBitsImpl<T, 8> {
  static T doit(T v) {
    uint64_t r = bit_reverse((uint64_t)v);
    return byte_reverse(r, 64);
  }
};
#endif

// Performs bit reversal of a multi-byte type, we implement and support
// variants for 8, 16, 32 and 64 bit integral types.
template <typename T> inline T reverse_bits(T v) {
  return ReverseBitsImpl<T, sizeof(T)>::doit(v);
}

#endif // SHARE_UTILITIES_MOVE_BITS_HPP
