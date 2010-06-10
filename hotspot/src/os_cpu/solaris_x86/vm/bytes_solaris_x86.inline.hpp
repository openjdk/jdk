/*
 * Copyright (c) 1998, 2007, Oracle and/or its affiliates. All rights reserved.
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

// For Sun Studio - implementation is in solaris_i486.il.
// For gcc - implementation is just below.
extern "C" u2 _raw_swap_u2(u2 x);
extern "C" u4 _raw_swap_u4(u4 x);
#ifdef AMD64
extern "C" u8 _raw_swap_u8(u8 x);
#else
extern "C" u8 _raw_swap_u8(u4 x, u4 y);
#endif // AMD64

// Efficient swapping of data bytes from Java byte
// ordering to native byte ordering and vice versa.
inline u2   Bytes::swap_u2(u2 x) {
  return _raw_swap_u2(x);
}

inline u4   Bytes::swap_u4(u4 x) {
  return _raw_swap_u4(x);
}

inline u8   Bytes::swap_u8(u8 x) {
#ifdef AMD64
  return _raw_swap_u8(x);
#else
  return swap_u8_base(*(u4*)&x, *(((u4*)&x)+1));
#endif // AMD64

}

#ifndef AMD64
// Helper function for swap_u8
inline u8   Bytes::swap_u8_base(u4 x, u4 y) {
  return _raw_swap_u8(x, y);
}
#endif // !AMD64


#ifdef _GNU_SOURCE

extern "C" {
#ifdef AMD64
  inline u2 _raw_swap_u2(u2 x) {
    register unsigned short int __dest;
    __asm__ ("rorw $8, %w0": "=r" (__dest): "0" (x): "cc");
    return __dest;
  }
  inline u4 _raw_swap_u4(u4 x) {
    register unsigned int __dest;
    __asm__ ("bswap %0" : "=r" (__dest) : "0" (x));
    return __dest;
  }
  inline u8 _raw_swap_u8(u8 x) {
    register unsigned long  __dest;
    __asm__ ("bswap %q0" : "=r" (__dest) : "0" (x));
    return __dest;
  }
#else
  inline u2 _raw_swap_u2(u2 x) {
    u2 ret;
    __asm__ __volatile__ (
      "movw %0, %%ax;"
      "xchg %%al, %%ah;"
      "movw %%ax, %0"
      :"=r" (ret)      // output : register 0 => ret
      :"0"  (x)        // input  : x => register 0
      :"ax", "0"       // clobbered registers
    );
    return ret;
  }

  inline u4 _raw_swap_u4(u4 x) {
    u4 ret;
    __asm__ __volatile__ (
      "bswap %0"
      :"=r" (ret)      // output : register 0 => ret
      :"0"  (x)        // input  : x => register 0
      :"0"             // clobbered register
    );
    return ret;
  }

  inline u8 _raw_swap_u8(u4 x, u4 y) {
    return (((u8)_raw_swap_u4(x))<<32) | _raw_swap_u4(y);
  }
#endif // AMD64
}
#endif  //_GNU_SOURCE
