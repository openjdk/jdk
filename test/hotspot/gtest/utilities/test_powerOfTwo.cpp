/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "precompiled.hpp"

#include "utilities/globalDefinitions.hpp"
#include "utilities/powerOfTwo.hpp"
#include "unittest.hpp"

template <typename T> T max_pow2() {
  T max_val = max_value<T>();
  return max_val - (max_val >> 1);
}

template <typename T> static void test_is_power_of_2() {
  EXPECT_FALSE(is_power_of_2(T(0)));
  EXPECT_FALSE(is_power_of_2(~T(0)));

  if (IsSigned<T>::value) {
    EXPECT_FALSE(is_power_of_2(std::numeric_limits<T>::min()));
  }

  // Test true
  for (T i = max_pow2<T>(); i > 0; i = (i >> 1)) {
    EXPECT_TRUE(is_power_of_2(i)) << "value = " << T(i);
  }

  // Test one less
  for (T i = max_pow2<T>(); i > 2; i = (i >> 1)) {
    EXPECT_FALSE(is_power_of_2(i - 1)) << "value = " << T(i - 1);
  }

  // Test one more
  for (T i = max_pow2<T>(); i > 1; i = (i >> 1)) {
    EXPECT_FALSE(is_power_of_2(i + 1)) << "value = " << T(i + 1);
  }
}

TEST(power_of_2, is_power_of_2) {
  test_is_power_of_2<int8_t>();
  test_is_power_of_2<int16_t>();
  test_is_power_of_2<int32_t>();
  test_is_power_of_2<int64_t>();
  test_is_power_of_2<int8_t>();
  test_is_power_of_2<int16_t>();
  test_is_power_of_2<int32_t>();
  test_is_power_of_2<int64_t>();

  test_is_power_of_2<jint>();
  test_is_power_of_2<jlong>();
}

TEST(power_of_2, exact_log2) {
  {
    uintptr_t j = 1;
#ifdef _LP64
    for (int i = 0; i < 64; i++, j <<= 1) {
#else
    for (int i = 0; i < 32; i++, j <<= 1) {
#endif
      EXPECT_EQ(i, exact_log2(j));
    }
  }
  {
    julong j = 1;
    for (int i = 0; i < 64; i++, j <<= 1) {
      EXPECT_EQ(i, exact_log2_long(j));
    }
  }
}

template <typename T> void round_up_power_of_2() {
  EXPECT_EQ(round_up_power_of_2(T(1)), T(1)) << "value = " << T(1);
  EXPECT_EQ(round_up_power_of_2(T(2)), T(2)) << "value = " << T(2);
  EXPECT_EQ(round_up_power_of_2(T(3)), T(4)) << "value = " << T(3);
  EXPECT_EQ(round_up_power_of_2(T(4)), T(4)) << "value = " << T(4);
  EXPECT_EQ(round_up_power_of_2(T(5)), T(8)) << "value = " << T(5);
  EXPECT_EQ(round_up_power_of_2(T(6)), T(8)) << "value = " << T(6);
  EXPECT_EQ(round_up_power_of_2(T(7)), T(8)) << "value = " << T(7);
  EXPECT_EQ(round_up_power_of_2(T(8)), T(8)) << "value = " << T(8);
  EXPECT_EQ(round_up_power_of_2(T(9)), T(16)) << "value = " << T(9);
  EXPECT_EQ(round_up_power_of_2(T(10)), T(16)) << "value = " << T(10);

  T t_max_pow2 = max_pow2<T>();

  // round_up(any power of two) should return input
  for (T pow2 = T(1); pow2 < t_max_pow2; pow2 *= 2) {
    EXPECT_EQ(pow2, round_up_power_of_2(pow2))
      << "value = " << pow2;
  }
  EXPECT_EQ(round_up_power_of_2(t_max_pow2), t_max_pow2)
    << "value = " << (t_max_pow2);

  // For each pow2 gt 2, round_up(pow2 - 1) should return pow2
  for (T pow2 = T(4); pow2 < t_max_pow2; pow2 *= 2) {
    EXPECT_EQ(pow2, round_up_power_of_2(pow2 - 1))
      << "value = " << pow2;
  }
  EXPECT_EQ(round_up_power_of_2(t_max_pow2 - 1), t_max_pow2)
    << "value = " << (t_max_pow2 - 1);

}

TEST(power_of_2, round_up_power_of_2) {
  round_up_power_of_2<int8_t>();
  round_up_power_of_2<int16_t>();
  round_up_power_of_2<int32_t>();
  round_up_power_of_2<int64_t>();
  round_up_power_of_2<uint8_t>();
  round_up_power_of_2<uint16_t>();
  round_up_power_of_2<uint32_t>();
  round_up_power_of_2<uint64_t>();
}

template <typename T> void round_down_power_of_2() {
  EXPECT_EQ(round_down_power_of_2(T(1)), T(1)) << "value = " << T(1);
  EXPECT_EQ(round_down_power_of_2(T(2)), T(2)) << "value = " << T(2);
  EXPECT_EQ(round_down_power_of_2(T(3)), T(2)) << "value = " << T(3);
  EXPECT_EQ(round_down_power_of_2(T(4)), T(4)) << "value = " << T(4);
  EXPECT_EQ(round_down_power_of_2(T(5)), T(4)) << "value = " << T(5);
  EXPECT_EQ(round_down_power_of_2(T(6)), T(4)) << "value = " << T(6);
  EXPECT_EQ(round_down_power_of_2(T(7)), T(4)) << "value = " << T(7);
  EXPECT_EQ(round_down_power_of_2(T(8)), T(8)) << "value = " << T(8);
  EXPECT_EQ(round_down_power_of_2(T(9)), T(8)) << "value = " << T(9);
  EXPECT_EQ(round_down_power_of_2(T(10)), T(8)) << "value = " << T(10);

  T t_max_pow2 = max_pow2<T>();

  // For each pow2 >= 2:
  // - round_down(pow2) should return pow2
  // - round_down(pow2 + 1) should return pow2
  // - round_down(pow2 - 1) should return pow2 / 2
  for (T pow2 = T(2); pow2 < t_max_pow2; pow2 = pow2 * 2) {
    EXPECT_EQ(pow2, round_down_power_of_2(pow2))
      << "value = " << pow2;
    EXPECT_EQ(pow2, round_down_power_of_2(pow2 + 1))
      << "value = " << pow2;
    EXPECT_EQ(pow2 / 2, round_down_power_of_2(pow2 - 1))
      << "value = " << (pow2 / 2);
  }
  EXPECT_EQ(round_down_power_of_2(t_max_pow2), t_max_pow2)
    << "value = " << (t_max_pow2);
  EXPECT_EQ(round_down_power_of_2(t_max_pow2 + 1), t_max_pow2)
    << "value = " << (t_max_pow2 + 1);
  EXPECT_EQ(round_down_power_of_2(t_max_pow2 - 1), t_max_pow2 / 2)
    << "value = " << (t_max_pow2 - 1);
}

TEST(power_of_2, round_down_power_of_2) {
  round_down_power_of_2<int8_t>();
  round_down_power_of_2<int16_t>();
  round_down_power_of_2<int32_t>();
  round_down_power_of_2<int64_t>();
  round_down_power_of_2<uint8_t>();
  round_down_power_of_2<uint16_t>();
  round_down_power_of_2<uint32_t>();
  round_down_power_of_2<uint64_t>();
}

template <typename T> void next_power_of_2() {
  EXPECT_EQ(next_power_of_2(T(0)), T(1)) << "value = " << T(0);
  EXPECT_EQ(next_power_of_2(T(1)), T(2)) << "value = " << T(1);
  EXPECT_EQ(next_power_of_2(T(2)), T(4)) << "value = " << T(2);
  EXPECT_EQ(next_power_of_2(T(3)), T(4)) << "value = " << T(3);
  EXPECT_EQ(next_power_of_2(T(4)), T(8)) << "value = " << T(4);
  EXPECT_EQ(next_power_of_2(T(5)), T(8)) << "value = " << T(5);
  EXPECT_EQ(next_power_of_2(T(6)), T(8)) << "value = " << T(6);
  EXPECT_EQ(next_power_of_2(T(7)), T(8)) << "value = " << T(7);
  EXPECT_EQ(next_power_of_2(T(8)), T(16)) << "value = " << T(8);
  EXPECT_EQ(next_power_of_2(T(9)), T(16)) << "value = " << T(9);
  EXPECT_EQ(next_power_of_2(T(10)), T(16)) << "value = " << T(10);

  T t_max_pow2 = max_pow2<T>();

  // next(pow2 - 1) should return pow2
  for (T pow2 = T(1); pow2 < t_max_pow2; pow2 = pow2 * 2) {
    EXPECT_EQ(pow2, next_power_of_2(pow2 - 1))
      << "value = " << pow2 - 1;
  }
  EXPECT_EQ(next_power_of_2(t_max_pow2 - 1), t_max_pow2)
    << "value = " << (t_max_pow2 - 1);

  // next(pow2) should return pow2 * 2
  for (T pow2 = T(1); pow2 < t_max_pow2 / 2; pow2 = pow2 * 2) {
    EXPECT_EQ(pow2 * 2, next_power_of_2(pow2))
      << "value = " << pow2;
  }
}

TEST(power_of_2, next_power_of_2) {
  next_power_of_2<int8_t>();
  next_power_of_2<int16_t>();
  next_power_of_2<int32_t>();
  next_power_of_2<int64_t>();
  next_power_of_2<uint8_t>();
  next_power_of_2<uint16_t>();
  next_power_of_2<uint32_t>();
  next_power_of_2<uint64_t>();
}
