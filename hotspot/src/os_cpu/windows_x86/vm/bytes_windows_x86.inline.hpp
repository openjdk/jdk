/*
 * Copyright (c) 1998, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_CPU_WINDOWS_X86_VM_BYTES_WINDOWS_X86_INLINE_HPP
#define OS_CPU_WINDOWS_X86_VM_BYTES_WINDOWS_X86_INLINE_HPP

#pragma warning(disable: 4035) // Disable warning 4035: no return value

// Efficient swapping of data bytes from Java byte
// ordering to native byte ordering and vice versa.
inline u2 Bytes::swap_u2(u2 x) {
#ifdef AMD64
  address p = (address) &x;
  return  ( (u2(p[0]) << 8 ) | ( u2(p[1])) );
#else
  __asm {
    mov ax, x
    xchg al, ah
  }
  // no return statement needed, result is already in ax
  // compiler warning C4035 disabled via warning pragma
#endif // AMD64
}

inline u4 Bytes::swap_u4(u4 x) {
#ifdef AMD64
  address p = (address) &x;
  return ( (u4(p[0]) << 24) | (u4(p[1]) << 16) | (u4(p[2]) << 8) | u4(p[3])) ;
#else
  __asm {
    mov eax, x
    bswap eax
  }
  // no return statement needed, result is already in eax
  // compiler warning C4035 disabled via warning pragma
#endif // AMD64
}

#ifdef AMD64
inline u8 Bytes::swap_u8(u8 x) {
  address p = (address) &x;
  return ( (u8(p[0]) << 56) | (u8(p[1]) << 48) | (u8(p[2]) << 40) | (u8(p[3]) << 32) |
           (u8(p[4]) << 24) | (u8(p[5]) << 16) | (u8(p[6]) << 8)  | u8(p[7])) ;
}

#else
// Helper function for swap_u8
inline u8 Bytes::swap_u8_base(u4 x, u4 y) {
  __asm {
    mov eax, y
    mov edx, x
    bswap eax
    bswap edx
  }
  // no return statement needed, result is already in edx:eax
  // compiler warning C4035 disabled via warning pragma
}

inline u8 Bytes::swap_u8(u8 x) {
  return swap_u8_base(*(u4*)&x, *(((u4*)&x)+1));
}
#endif // AMD64

#pragma warning(default: 4035) // Enable warning 4035: no return value

#endif // OS_CPU_WINDOWS_X86_VM_BYTES_WINDOWS_X86_INLINE_HPP
