/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

#include "endian.hpp"
#include "inttypes.hpp"

// Most modern compilers optimize the bswap routines to native instructions.
inline static u2 bswap_16(u2 x) {
  return ((x & 0xFF) << 8) |
         ((x >> 8) & 0xFF);
}

inline static u4 bswap_32(u4 x) {
  return ((x & 0xFF) << 24) |
       ((x & 0xFF00) << 8) |
       ((x >> 8) & 0xFF00) |
       ((x >> 24) & 0xFF);
}

inline static u8 bswap_64(u8 x) {
  return (u8)bswap_32((u4)x) << 32 |
         (u8)bswap_32((u4)(x >> 32));
}

u2 NativeEndian::get(u2 x) { return x; }
u4 NativeEndian::get(u4 x) { return x; }
u8 NativeEndian::get(u8 x) { return x; }
s2 NativeEndian::get(s2 x) { return x; }
s4 NativeEndian::get(s4 x) { return x; }
s8 NativeEndian::get(s8 x) { return x; }

void NativeEndian::set(u2& x, u2 y) { x = y; }
void NativeEndian::set(u4& x, u4 y) { x = y; }
void NativeEndian::set(u8& x, u8 y) { x = y; }
void NativeEndian::set(s2& x, s2 y) { x = y; }
void NativeEndian::set(s4& x, s4 y) { x = y; }
void NativeEndian::set(s8& x, s8 y) { x = y; }

NativeEndian NativeEndian::_native;

u2 SwappingEndian::get(u2 x) { return bswap_16(x); }
u4 SwappingEndian::get(u4 x) { return bswap_32(x); }
u8 SwappingEndian::get(u8 x) { return bswap_64(x); }
s2 SwappingEndian::get(s2 x) { return bswap_16(x); }
s4 SwappingEndian::get(s4 x) { return bswap_32(x); }
s8 SwappingEndian::get(s8 x) { return bswap_64(x); }

void SwappingEndian::set(u2& x, u2 y) { x = bswap_16(y); }
void SwappingEndian::set(u4& x, u4 y) { x = bswap_32(y); }
void SwappingEndian::set(u8& x, u8 y) { x = bswap_64(y); }
void SwappingEndian::set(s2& x, s2 y) { x = bswap_16(y); }
void SwappingEndian::set(s4& x, s4 y) { x = bswap_32(y); }
void SwappingEndian::set(s8& x, s8 y) { x = bswap_64(y); }

SwappingEndian SwappingEndian::_swapping;

Endian* Endian::get_handler(bool big_endian) {
  // If requesting little endian on a little endian machine or
  // big endian on a big endian machine use native handler
  if (big_endian == is_big_endian()) {
    return NativeEndian::get_native();
  } else {
    // Use swapping handler.
    return SwappingEndian::get_swapping();
  }
}

// Return a platform u2 from an array in which Big Endian is applied.
u2 Endian::get_java(u1* x) {
  return (u2) (x[0]<<8 | x[1]);
}

// Add a platform u2 to the array as a Big Endian u2
void Endian::set_java(u1* p, u2 x) {
  p[0] = (x >> 8) & 0xff;
  p[1] = x & 0xff;
}

Endian* Endian::get_native_handler() {
  return NativeEndian::get_native();
}
