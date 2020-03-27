/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_SPARC_C2_MACROASSEMBLER_SPARC_HPP
#define CPU_SPARC_C2_MACROASSEMBLER_SPARC_HPP

// C2_MacroAssembler contains high-level macros for C2

 public:
  // Compress char[] to byte[] by compressing 16 bytes at once. Return 0 on failure.
  void string_compress_16(Register src, Register dst, Register cnt, Register result,
                          Register tmp1, Register tmp2, Register tmp3, Register tmp4,
                          FloatRegister ftmp1, FloatRegister ftmp2, FloatRegister ftmp3, Label& Ldone);

  // Compress char[] to byte[]. Return 0 on failure.
  void string_compress(Register src, Register dst, Register cnt, Register tmp, Register result, Label& Ldone);

  // Inflate byte[] to char[] by inflating 16 bytes at once.
  void string_inflate_16(Register src, Register dst, Register cnt, Register tmp,
                         FloatRegister ftmp1, FloatRegister ftmp2, FloatRegister ftmp3, FloatRegister ftmp4, Label& Ldone);

  // Inflate byte[] to char[].
  void string_inflate(Register src, Register dst, Register cnt, Register tmp, Label& Ldone);

  void string_compare(Register str1, Register str2,
                      Register cnt1, Register cnt2,
                      Register tmp1, Register tmp2,
                      Register result, int ae);

  void array_equals(bool is_array_equ, Register ary1, Register ary2,
                    Register limit, Register tmp, Register result, bool is_byte);
  // test for negative bytes in input string of a given size, result 0 if none
  void has_negatives(Register inp, Register size, Register result,
                     Register t2, Register t3, Register t4,
                     Register t5);

#endif // CPU_SPARC_C2_MACROASSEMBLER_SPARC_HPP
