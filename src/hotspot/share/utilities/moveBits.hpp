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

inline uint32_t reverse_bits_in_bytes_int(uint32_t x) {
  // Based on Hacker's Delight Section 7-1
  x = (x & 0x55555555) << 1 | (x & 0xAAAAAAAA) >> 1;
  x = (x & 0x33333333) << 2 | (x & 0xCCCCCCCC) >> 2;
  x = (x & 0x0F0F0F0F) << 4 | (x & 0xF0F0F0F0) >> 4;
  return x;
}

inline uint32_t reverse_bytes_int(uint32_t x, size_t bw) {
  assert(bw == 16 || bw == 32, "");
  if (bw == 32) {
    // Based on Hacker's Delight Section 7-1
    return (x << 24) | ((x & 0xFF00) << 8) | ((x >> 8) & 0xFF00) | (x >> 24);
  } else {
    return (x & 0x00FF00FF) << 8 | (x & 0xFF00FF00) >> 8;
  }
}

inline uint64_t reverse_bits_in_bytes_long(uint64_t x) {
  // Based on Hacker's Delight Section 7-1
  x = (x & CONST64(0x5555555555555555)) << 1 | (x & CONST64(0xAAAAAAAAAAAAAAAA)) >> 1;
  x = (x & CONST64(0x3333333333333333)) << 2 | (x & CONST64(0xCCCCCCCCCCCCCCCC)) >> 2;
  x = (x & CONST64(0x0F0F0F0F0F0F0F0F)) << 4 | (x & CONST64(0xF0F0F0F0F0F0F0F0)) >> 4;
  return x;
}

inline uint64_t reverse_bytes_long(uint64_t x) {
  x = (x & CONST64(0x00FF00FF00FF00FF)) << 8 | (x >> 8) & CONST64(0x00FF00FF00FF00FF);
  return (x << 48) | ((x & 0xFFFF0000) << 16) | ((x >> 16) & 0xFFFF0000) | (x >> 48);
}

template <typename T, size_t S> struct ReverseBitsImpl {};

template <typename T> struct ReverseBitsImpl<T, 1> {
  static T doit(T v) {
    return reverse_bits_in_bytes_int(v);
  }
};

/*****************************************************************************
 * GCC and compatible (including Clang)
 *****************************************************************************/
#if defined(TARGET_COMPILER_gcc)
template <typename T> struct ReverseBitsImpl<T, 2> {
  static T doit(T v) {
    v = reverse_bits_in_bytes_int(v);
    return __builtin_bswap16(v);
  }
};

template <typename T> struct ReverseBitsImpl<T, 4> {
  static T doit(T v) {
    v = reverse_bits_in_bytes_int(v);
    return __builtin_bswap32(v);
  }
};

template <typename T> struct ReverseBitsImpl<T, 8> {
  static T doit(T v) {
    v = reverse_bits_in_bytes_long(v);
    return __builtin_bswap64(v);
  }
};

/*****************************************************************************
 * Fallback
 *****************************************************************************/
#else
template <typename T> struct ReverseBitsImpl<T, 2> {
  static T doit(T v) {
    v = reverse_bits_in_bytes_int(v);
    return reverse_bytes_int(r, 16);
  }
};

template <typename T> struct ReverseBitsImpl<T, 4> {
  static T doit(T v) {
    v = reverse_bits_in_bytes(v);
    return reverse_bytes_int(v, 32);
  }
};

template <typename T> struct ReverseBitsImpl<T, 8> {
  static T doit(T v) {
    v = reverse_bits_in_bytes(v);
    return reverse_bytes_long(r);
  }
};
#endif

// Performs bit reversal of a multi-byte type, we implement and support
// variants for 8, 16, 32 and 64 bit integral types.
template <typename T, ENABLE_IF(std::is_integral<T>::value)> inline T reverse_bits(T v) {
  return ReverseBitsImpl<T, sizeof(T)>::doit(v);
}

#endif // SHARE_UTILITIES_MOVE_BITS_HPP
