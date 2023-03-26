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

#include <vector>

#include "unittest.hpp"
#include "utilities/javaArithmetic.hpp"
#include "utilities/powerOfTwo.hpp"

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

template <class T>
static void test_divide(T dividend, T divisor) {}

template <>
void test_divide<jint>(jint dividend, jint divisor) {
  if (divisor == 0 || divisor == 1 || divisor == -1 || divisor == min_jint) {
    return;
  }

  jint expected = divisor == -1 ? java_subtract(0, dividend) : (dividend / divisor);

  jint abs_divisor = divisor > 0 ? divisor : java_subtract(0, divisor);
  if (is_power_of_2(abs_divisor)) {
    jint l = log2i_exact(abs_divisor);
    if (dividend > 0 || (dividend & (abs_divisor - 1)) == 0) {
      jint result = java_shift_right(dividend, l);
      ASSERT_EQ(expected, divisor > 0 ? result : java_subtract(0, result));
    }
    jint rounded_dividend = java_add(dividend, java_shift_right_unsigned(java_shift_right(dividend, 31), 32 - l));
    jint result = java_shift_right(rounded_dividend, l);
    ASSERT_EQ(expected, divisor > 0 ? result : java_subtract(0, result));
  }

  jlong magic_const;
  jint shift;
  magic_int_divide_constants(abs_divisor, magic_const, shift);
  jint result = jint(java_shift_right(java_multiply(jlong(dividend), magic_const), shift + 32));
  if (divisor < 0) {
    result = java_subtract(java_shift_right(dividend, 31), result);
  } else {
    result = java_subtract(result, java_shift_right(dividend, 31));
  }
  ASSERT_EQ(expected, result);
}

template <>
void test_divide<juint>(juint dividend, juint divisor) {
  if (divisor == 0 || divisor == 1) {
    return;
  }

  juint expected = dividend / divisor;

  if (is_power_of_2(divisor)) {
    jint l = log2i_exact(divisor);
    juint result = java_shift_right_unsigned(jint(dividend), l);
    ASSERT_EQ(expected, result);
  }

  jlong magic_const;
  jint shift;
  magic_int_unsigned_divide_constants_down(divisor, magic_const, shift);
  if (julong(magic_const) <= max_julong / dividend) {
    if (shift == 32) {
      juint result = 0;
      ASSERT_EQ(expected, result);
    } else {
      juint result = java_shift_right_unsigned(java_multiply(jlong(dividend), magic_const), shift + 32);
      ASSERT_EQ(expected, result);
    }
  }
  if (magic_const > max_juint) {
    magic_int_unsigned_divide_constants_up(divisor, magic_const, shift);
    // This case guarantee shift < 32 so we do not need to special case like above
    juint result = java_shift_right_unsigned(java_multiply(jlong(dividend) + 1, magic_const), shift + 32);
    ASSERT_EQ(expected, result);
  }
}

template <>
void test_divide<jlong>(jlong dividend, jlong divisor) {
  if (divisor == 0 || divisor == -1 || divisor == 1 || divisor == min_jlong) {
    return;
  }

  jlong expected = divisor == -1 ? java_subtract(jlong(0), dividend) : (dividend / divisor);

  jlong abs_divisor = divisor > 0 ? divisor : java_subtract(jlong(0), divisor);
  if (abs_divisor > 0 && is_power_of_2(abs_divisor)) {
    jint l = log2i_exact(abs_divisor);
    if (dividend > 0 || (dividend & (abs_divisor - 1)) == 0) {
      jlong result = java_shift_right(dividend, l);
      ASSERT_EQ(expected, divisor > 0 ? result : java_subtract(jlong(0), result));
    }
    jlong rounded_dividend = java_add(dividend, java_shift_right_unsigned(java_shift_right(dividend, 63), 64 - l));
    jlong result = java_shift_right(rounded_dividend, l);
    ASSERT_EQ(expected, divisor > 0 ? result : java_subtract(jlong(0), result));
  }

  jlong magic_const;
  jint shift;
  magic_long_divide_constants(abs_divisor, magic_const, shift);
  jlong result = multiply_high_signed(dividend, magic_const);
  if (magic_const < 0) {
    result += dividend;
  }
  result = java_shift_right(result, shift);
  if (divisor < 0) {
    result = java_subtract(java_shift_right(dividend, 63), result);
  } else {
    result = java_subtract(result, java_shift_right(dividend, 63));
  }
  ASSERT_EQ(expected, result);
}

template <>
void test_divide<julong>(julong dividend, julong divisor) {
  if (divisor == 0 || divisor == 1) {
    return;
  }

  julong expected = dividend / divisor;

  if (is_power_of_2(divisor)) {
    jint l = log2i_exact(divisor);
    julong result = java_shift_right_unsigned(jlong(dividend), l);
    ASSERT_EQ(expected, result);
  }

  jlong magic_const;
  jint shift;
  bool magic_const_ovf;
  magic_long_unsigned_divide_constants(divisor, magic_const, shift, magic_const_ovf);
  jlong mul_hi = multiply_high_unsigned(dividend, magic_const);
  if (!magic_const_ovf) {
    julong result = java_shift_right_unsigned(mul_hi, shift);
    ASSERT_EQ(expected, result);
  } else {
    if (dividend <= julong(min_jlong) || shift == 0) {
      if (shift == 64) {
        julong result = 0;
        ASSERT_EQ(expected, result);
      } else {
        jlong mul_hi_corrected = java_add(mul_hi, dividend);
        julong result = java_shift_right_unsigned(mul_hi_corrected, shift);
        ASSERT_EQ(expected, result);
      }
    }

    jlong diff = java_subtract(dividend, mul_hi);
    diff = java_shift_right_unsigned(diff, 1);
    diff = java_add(diff, mul_hi);
    // shift <= 64 so we do not need to special case like above
    julong result = java_shift_right_unsigned(diff, shift - 1);
    ASSERT_EQ(expected, result);
  }
  
}

static void test_hardcoded_coefs() {
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

template <class T>
static void test_division() {
  using U = std::make_unsigned_t<T>;
  constexpr T min_value = std::numeric_limits<T>::min();
  constexpr T max_value = std::numeric_limits<T>::max();
  std::vector<T> operands;

  operands.push_back(0);
  operands.push_back(1); operands.push_back(2); operands.push_back(3);
  operands.push_back(-1); operands.push_back(-2); operands.push_back(-3);
  operands.push_back(min_value); operands.push_back(min_value + 1); operands.push_back(min_value + 2);
  operands.push_back(max_value); operands.push_back(max_value - 1); operands.push_back(max_value - 2);

  for (juint i = 2; i < sizeof(T) * 8 - 2; i += 4) {
    T twoPowI = java_shift_left(T(1), i);
    operands.push_back(twoPowI);
    operands.push_back(twoPowI + 1);
    operands.push_back(twoPowI - 1);
  }

  juint current_size = juint(operands.size());
  for (juint i = 0; i < current_size; i++) {
    for (juint j = 0; j <= i; j++) {
      operands.push_back(java_multiply(operands.at(i), operands.at(j)));
    }
  }

  for (juint i = 0; i < operands.size(); i++) {
    for (juint j = 0; j < operands.size(); j++) {
      T dividend = operands.at(i);
      T divisor = operands.at(j);
      test_divide<T>(dividend, divisor);
      test_divide<U>(dividend, divisor);
    }
  }
}

TEST(opto, divide_by_constants) {
  test_hardcoded_coefs();
  test_division<jint>();
  test_division<jlong>();
}
