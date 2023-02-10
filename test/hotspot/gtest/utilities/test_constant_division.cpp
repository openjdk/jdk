/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "unittest.hpp"
#include "utilities/javaArithmetic.hpp"

static void test_magic_int_divide_coefs(jint divisor, jlong expected_magic_const, jint expected_shift) {
  jlong magic_const;
  jint shift;
  magic_int_divide_constants(divisor, magic_const, shift);
  ASSERT_EQ(expected_magic_const, magic_const);
  ASSERT_EQ(expected_shift, shift);
}

static void test_magic_int_unsigned_divide_down_coefs(juint divisor, jlong expected_magic_const, jint expected_shift) {
  jlong magic_const;
  jint shift;
  magic_int_unsigned_divide_constants_down(divisor, magic_const, shift);
  ASSERT_EQ(expected_magic_const, magic_const);
  ASSERT_EQ(expected_shift, shift);
}

static void test_magic_int_unsigned_divide_up_coefs(juint divisor, jlong expected_magic_const, jint expected_shift) {
  jlong magic_const;
  jint shift;
  magic_int_unsigned_divide_constants_up(divisor, magic_const, shift);
  ASSERT_EQ(expected_magic_const, magic_const);
  ASSERT_EQ(expected_shift, shift);
}

static void test_magic_long_divide_coefs(jlong divisor, jlong expected_magic_const, jint expected_shift) {
  jlong magic_const;
  jint shift;
  magic_long_divide_constants(divisor, magic_const, shift);
  ASSERT_EQ(expected_magic_const, magic_const);
  ASSERT_EQ(expected_shift, shift);
}

static void test_magic_long_unsigned_divide_coefs(julong divisor, jlong expected_magic_const, jint expected_shift, bool expected_ovf) {
  jlong magic_const;
  jint shift;
  bool ovf;
  magic_long_unsigned_divide_constants(divisor, magic_const, shift, ovf);
  ASSERT_EQ(expected_magic_const, magic_const);
  ASSERT_EQ(expected_shift, shift);
  ASSERT_EQ(expected_ovf, ovf);
}

static void test_magic_int_divide(jint dividend, jint divisor) {
  jlong magic_const;
  jint shift;
  magic_int_divide_constants(divisor, magic_const, shift);
  ASSERT_EQ(dividend / divisor,
            java_shift_right(java_multiply(dividend, magic_const), shift) & 0xFFFFFFFFL
              - (dividend < 0 ? -1L : 0L));
}

static void test_magic_int_unsigned_divide_down(juint dividend, juint divisor) {
  jlong magic_const;
  jint shift;
  magic_int_unsigned_divide_constants_down(divisor, magic_const, shift);
  ASSERT_EQ(dividend / divisor,
            java_shift_right_unsigned(java_multiply(dividend, magic_const), shift) & 0xFFFFFFFFL);
}

static void test_magic_int_unsigned_divide_up(juint dividend, juint divisor) {
  jlong magic_const;
  jint shift;
  magic_int_unsigned_divide_constants_up(divisor, magic_const, shift);
  ASSERT_EQ(dividend / divisor,
            java_shift_right_unsigned(java_multiply(dividend + 1L, magic_const), shift) & 0xFFFFFFFFL);
}

TEST_VM(utilities, javaArithmetic) {
  // These numbers are taken from the output of gcc 12.2 or msvc 19.33
  test_magic_int_divide_coefs(3, 1431655766, 0);
  test_magic_int_divide_coefs(5, 1717986919, 1);
  test_magic_int_divide_coefs(6, 715827883, 0);
  test_magic_int_divide_coefs(7, 2454267027, 2);
  test_magic_int_divide_coefs(9, 954437177, 1);
  test_magic_int_divide_coefs(14, 2454267027, 3);
  test_magic_int_divide_coefs(101, 680390859, 4);
  test_magic_int_divide_coefs(1000, 274877907, 6);
  test_magic_int_divide_coefs(1000000, 1125899907, 18);
  test_magic_int_divide_coefs(1000000000, 1152921505, 28);
  test_magic_int_divide_coefs(2147483647, 1073741825, 29);

  // These numbers are taken from the output of gcc 12.2 or msvc 19.33
  test_magic_int_unsigned_divide_down_coefs(3, 2863311531, 1);
  test_magic_int_unsigned_divide_down_coefs(5, 3435973837, 2);
  test_magic_int_unsigned_divide_down_coefs(6, 2863311531, 2);
  test_magic_int_unsigned_divide_down_coefs(7, 4908534053, 3);
  test_magic_int_unsigned_divide_down_coefs(9, 954437177, 1);
  test_magic_int_unsigned_divide_down_coefs(14, 4908534053, 4);
  test_magic_int_unsigned_divide_down_coefs(101, 5443126871, 7);
  test_magic_int_unsigned_divide_down_coefs(1000, 274877907, 6);
  test_magic_int_unsigned_divide_down_coefs(1000000, 1125899907, 18);
  test_magic_int_unsigned_divide_down_coefs(1000000000, 4611686019, 30);
  test_magic_int_unsigned_divide_down_coefs(2147483647, 4294967299, 31);

  // These numbers are calculated manually according to
  // N-Bit Unsigned Division Via N-Bit Multiply-Add by Arch D. Robison
  // shift = floor(log(2, divisor))
  // magic_const = floor(2^(shift + 32) / divisor)
  test_magic_int_unsigned_divide_up_coefs(7, 2454267026, 2);
  test_magic_int_unsigned_divide_up_coefs(14, 2454267026, 3);
  test_magic_int_unsigned_divide_up_coefs(101, 2721563435, 6);
  test_magic_int_unsigned_divide_up_coefs(1000000000, 2305843009, 29);
  test_magic_int_unsigned_divide_up_coefs(2147483647, 2147483649, 30);

  // These numbers are taken from the output of gcc 12.2 or msvc 19.33
  test_magic_long_divide_coefs(3, 6148914691236517206, 0);
  test_magic_long_divide_coefs(5, 7378697629483820647, 1);
  test_magic_long_divide_coefs(6, 3074457345618258603, 0);
  test_magic_long_divide_coefs(7, 5270498306774157605, 1);
  test_magic_long_divide_coefs(9, 2049638230412172402, 0);
  test_magic_long_divide_coefs(14, 5270498306774157605, 2);
  test_magic_long_divide_coefs(101, -6757718126012409997, 6);
  test_magic_long_divide_coefs(1000, 2361183241434822607, 7);
  test_magic_long_divide_coefs(1000000, 4835703278458516699, 18);
  test_magic_long_divide_coefs(1000000000, 1237940039285380275, 26);
  test_magic_long_divide_coefs(2147483647, -9223372032559808509, 30);
  test_magic_long_divide_coefs(2147483649, 4611686016279904257, 29);
  test_magic_long_divide_coefs(4294967295, -9223372034707292159, 31);
  test_magic_long_divide_coefs(4294967297, 9223372034707292161, 31);
  test_magic_long_divide_coefs(9223372036854775807, 4611686018427387905, 61);

  // These numbers are taken from the output of gcc 12.2 or or msvc 19.33
  test_magic_long_unsigned_divide_coefs(3, -6148914691236517205, 1, false);
  test_magic_long_unsigned_divide_coefs(5, -3689348814741910323, 2, false);
  test_magic_long_unsigned_divide_coefs(6, -6148914691236517205, 2, false);
  test_magic_long_unsigned_divide_coefs(7, 2635249153387078803, 3, true);
  test_magic_long_unsigned_divide_coefs(9, -2049638230412172401, 3, false);
  test_magic_long_unsigned_divide_coefs(14, 2635249153387078803, 4, true);
  test_magic_long_unsigned_divide_coefs(101, 4931307821684731621, 7, true);
  test_magic_long_unsigned_divide_coefs(1000, 442721857769029239, 10, true);
  test_magic_long_unsigned_divide_coefs(1000000, 4835703278458516699, 18, false);
  test_magic_long_unsigned_divide_coefs(1000000000, 1360296554856532783, 30, true);
  test_magic_long_unsigned_divide_coefs(2147483647, 8589934597, 31, true);
  test_magic_long_unsigned_divide_coefs(2147483649, 4611686016279904257, 29, false);
  test_magic_long_unsigned_divide_coefs(4294967295, -9223372034707292159, 31, false);
  test_magic_long_unsigned_divide_coefs(4292967297, 8593932156542825, 32, true);
  test_magic_long_unsigned_divide_coefs(9223372036854775807, 3, 63, true);
}
