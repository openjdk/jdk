/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_ROTATE_BITS_HPP
#define SHARE_UTILITIES_ROTATE_BITS_HPP

#include "utilities/globalDefinitions.hpp"

inline uint32_t rotate_right_32(uint32_t x, int distance) {
  distance = distance & 0x1F;
  if (distance > 0) {
    return (x >> distance) | (x << (32 - distance));
  } else {
    return x;
  }
}

inline uint64_t rotate_right_64(uint64_t x, int distance) {
  distance = distance & 0x3F;
  if (distance > 0) {
    return (x >> distance) | (x << (64 - distance));
  } else {
    return x;
  }
}

template<typename T,
    ENABLE_IF(std::is_integral<T>::value),
ENABLE_IF(sizeof(T) <= sizeof(uint64_t))>
inline T rotate_right(T x, int dist) {
  return (sizeof(x) <= sizeof(uint32_t)) ?
         rotate_right_32(static_cast<uint32_t>(x), dist) :
         rotate_right_64(static_cast<uint64_t>(x), dist);
}

#endif // SHARE_UTILITIES_ROTATE_BITS_HPP
