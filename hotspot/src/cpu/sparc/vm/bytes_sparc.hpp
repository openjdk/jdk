/*
 * Copyright (c) 1997, 2002, Oracle and/or its affiliates. All rights reserved.
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

class Bytes: AllStatic {
 public:
  // Efficient reading and writing of unaligned unsigned data in platform-specific byte ordering
  // Sparc needs to check for alignment.

  // can I count on address always being a pointer to an unsigned char? Yes

  // Returns true, if the byte ordering used by Java is different from the nativ byte ordering
  // of the underlying machine. For example, true for Intel x86, False, for Solaris on Sparc.
  static inline bool is_Java_byte_ordering_different() { return false; }

  // Thus, a swap between native and Java ordering is always a no-op:
  static inline u2   swap_u2(u2 x)  { return x; }
  static inline u4   swap_u4(u4 x)  { return x; }
  static inline u8   swap_u8(u8 x)  { return x; }

  static inline u2   get_native_u2(address p){
    return (intptr_t(p) & 1) == 0
             ?   *(u2*)p
             :   ( u2(p[0]) << 8 )
               | ( u2(p[1])      );
  }

  static inline u4   get_native_u4(address p) {
    switch (intptr_t(p) & 3) {
     case 0:  return *(u4*)p;

     case 2:  return (  u4( ((u2*)p)[0] ) << 16  )
                   | (  u4( ((u2*)p)[1] )                  );

    default:  return ( u4(p[0]) << 24 )
                   | ( u4(p[1]) << 16 )
                   | ( u4(p[2]) <<  8 )
                   |   u4(p[3]);
    }
  }

  static inline u8   get_native_u8(address p) {
    switch (intptr_t(p) & 7) {
      case 0:  return *(u8*)p;

      case 4:  return (  u8( ((u4*)p)[0] ) << 32  )
                    | (  u8( ((u4*)p)[1] )        );

      case 2:  return (  u8( ((u2*)p)[0] ) << 48  )
                    | (  u8( ((u2*)p)[1] ) << 32  )
                    | (  u8( ((u2*)p)[2] ) << 16  )
                    | (  u8( ((u2*)p)[3] )        );

     default:  return ( u8(p[0]) << 56 )
                    | ( u8(p[1]) << 48 )
                    | ( u8(p[2]) << 40 )
                    | ( u8(p[3]) << 32 )
                    | ( u8(p[4]) << 24 )
                    | ( u8(p[5]) << 16 )
                    | ( u8(p[6]) <<  8 )
                    |   u8(p[7]);
    }
  }



  static inline void put_native_u2(address p, u2 x)   {
    if ( (intptr_t(p) & 1) == 0 )  *(u2*)p = x;
    else {
      p[0] = x >> 8;
      p[1] = x;
    }
  }

  static inline void put_native_u4(address p, u4 x) {
    switch ( intptr_t(p) & 3 ) {
    case 0:  *(u4*)p = x;
              break;

    case 2:  ((u2*)p)[0] = x >> 16;
             ((u2*)p)[1] = x;
             break;

    default: ((u1*)p)[0] = x >> 24;
             ((u1*)p)[1] = x >> 16;
             ((u1*)p)[2] = x >>  8;
             ((u1*)p)[3] = x;
             break;
    }
  }

  static inline void put_native_u8(address p, u8 x) {
    switch ( intptr_t(p) & 7 ) {
    case 0:  *(u8*)p = x;
             break;

    case 4:  ((u4*)p)[0] = x >> 32;
             ((u4*)p)[1] = x;
             break;

    case 2:  ((u2*)p)[0] = x >> 48;
             ((u2*)p)[1] = x >> 32;
             ((u2*)p)[2] = x >> 16;
             ((u2*)p)[3] = x;
             break;

    default: ((u1*)p)[0] = x >> 56;
             ((u1*)p)[1] = x >> 48;
             ((u1*)p)[2] = x >> 40;
             ((u1*)p)[3] = x >> 32;
             ((u1*)p)[4] = x >> 24;
             ((u1*)p)[5] = x >> 16;
             ((u1*)p)[6] = x >>  8;
             ((u1*)p)[7] = x;
    }
  }


  // Efficient reading and writing of unaligned unsigned data in Java byte ordering (i.e. big-endian ordering)
  // (no byte-order reversal is needed since SPARC CPUs are big-endian oriented)
  static inline u2   get_Java_u2(address p) { return get_native_u2(p); }
  static inline u4   get_Java_u4(address p) { return get_native_u4(p); }
  static inline u8   get_Java_u8(address p) { return get_native_u8(p); }

  static inline void put_Java_u2(address p, u2 x)     { put_native_u2(p, x); }
  static inline void put_Java_u4(address p, u4 x)     { put_native_u4(p, x); }
  static inline void put_Java_u8(address p, u8 x)     { put_native_u8(p, x); }
};

//Reconciliation History
// 1.7 98/02/24 10:18:41 bytes_i486.hpp
// 1.10 98/04/08 18:47:57 bytes_i486.hpp
// 1.13 98/07/15 17:10:03 bytes_i486.hpp
// 1.14 98/08/13 10:38:23 bytes_i486.hpp
// 1.15 98/10/05 16:30:21 bytes_i486.hpp
// 1.17 99/06/22 16:37:35 bytes_i486.hpp
//End
