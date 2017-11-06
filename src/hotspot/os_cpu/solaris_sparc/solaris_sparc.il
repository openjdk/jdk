//
// Copyright (c) 2002, 2017, Oracle and/or its affiliates. All rights reserved.
// DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
//
// This code is free software; you can redistribute it and/or modify it
// under the terms of the GNU General Public License version 2 only, as
// published by the Free Software Foundation.
//
// This code is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
// version 2 for more details (a copy is included in the LICENSE file that
// accompanied this code).
//
// You should have received a copy of the GNU General Public License version
// 2 along with this work; if not, write to the Free Software Foundation,
// Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
//
// Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
// or visit www.oracle.com if you need additional information or have any
// questions.
//
//

  // Get the raw thread ID from %g7

       .inline  _raw_thread_id, 0
       .register %g7,#scratch
       .volatile
       mov     %g7, %o0
       .nonvolatile
       .end


  // Support for jlong Atomic::load and Atomic::store on v9.
  //
  // void _Atomic_move_long_v9(volatile jlong* src, volatile jlong* dst)
  //
  // Arguments:
  //      src:  O0
  //      dest: O1
  //
  // Overwrites O2

        .inline _Atomic_move_long_v9,2
        .volatile
        ldx     [%o0], %o2
        stx     %o2, [%o1]
        .nonvolatile
        .end

  // Support for void Prefetch::read(void *loc, intx interval)
  //
  // Prefetch for several reads.

        .inline _Prefetch_read, 2
        .volatile
        prefetch [%o0+%o1], 0
        .nonvolatile
        .end


  // Support for void Prefetch::write(void *loc, intx interval)
  //
  // Prefetch for several writes.

        .inline _Prefetch_write, 2
        .volatile
        prefetch [%o0+%o1], 2
        .nonvolatile
        .end


  // Support for void Copy::conjoint_jlongs_atomic(jlong* from, jlong* to, size_t count)
  //
  // 32-bit
  //
  // Arguments:
  //      from:  O0
  //      to:    O1
  //      count: O2 treated as signed
  //
  // Clobbers:
  //      long_value: O2, O3
  //      count:      O4
  //
  // if (from > to) {
  //   while (--count >= 0) {
  //     *to++ = *from++;
  //   }
  // } else {
  //   while (--count >= 0) {
  //     to[count] = from[count];
  //   }
  // }
        .inline _Copy_conjoint_jlongs_atomic, 3
        .volatile
        cmp     %o0, %o1
        bleu    4f
        sll     %o2, 3, %o4
        ba      2f
    1:
        subcc   %o4, 8, %o4
        std     %o2, [%o1]
        add     %o0, 8, %o0
        add     %o1, 8, %o1
    2:
        bge,a   1b
        ldd     [%o0], %o2
        ba      5f
        nop
    3:
        std     %o2, [%o1+%o4]
    4:
        subcc   %o4, 8, %o4
        bge,a   3b
        ldd     [%o0+%o4], %o2
    5:
        .nonvolatile
        .end
