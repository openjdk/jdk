/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#include "gc/shenandoah/shenandoahUtils.hpp"
#include "unittest.hpp"
#include "utilities/globalDefinitions.hpp"

#include <cmath>
#include <limits>

static void test_units(double value, double expected_value, const char* expected_unit) {
  ShenandoahSignedSize s = ShenandoahSignedSize::get(value);
  EXPECT_EQ(s.value, expected_value);
  EXPECT_STREQ(s.unit, expected_unit);
}

TEST(ShenandoahUtilsTest, format_byte_double) {
  test_units(1024.0, 1024.0, "B");
  test_units(-1024.0, -1024.0, "B");
}

TEST(ShenandoahUtilsTest, format_kilo_double) {
  test_units(900.0 * K, 900.0, "K");
  test_units(-900.0 * K, -900.0, "K");
}

TEST(ShenandoahUtilsTest, format_signed_mega_double) {
  test_units(900.0 * M, 900.0, "M");
  test_units(-900.0 * M, -900.0, "M");
}

TEST(ShenandoahUtilsTest, format_signed_giga_double) {
#ifdef _LP64
  test_units(900.0 * G, 900.0, "G");
  test_units(-900.0 * G, -900.0, "G");
#else
  test_units(900.0 * G, 921600.0, "M");
  test_units(-900.0 * G, -921600.0, "M");
#endif
}
TEST(ShenandoahUtilsTest, format_negative_zero_double) {
  ShenandoahSignedSize s = ShenandoahSignedSize::get(-0.0);
  EXPECT_DOUBLE_EQ(s.value, 0.0);
  EXPECT_TRUE(std::signbit(s.value));
  EXPECT_STREQ(s.unit, "B");
}

TEST(ShenandoahUtilsTest, format_infinite_double) {
  ShenandoahSignedSize s = ShenandoahSignedSize::get(std::numeric_limits<double>::infinity());
  EXPECT_TRUE(std::isinf(s.value));
  EXPECT_STREQ(s.unit, "B");
}

TEST(ShenandoahUtilsTest, format_negative_infinite_double) {
  const double ninf = -std::numeric_limits<double>::infinity();
  ShenandoahSignedSize s = ShenandoahSignedSize::get(ninf);
  EXPECT_EQ(ninf, s.value);
  EXPECT_STREQ(s.unit, "B");
}

TEST(ShenandoahUtilsTest, format_nan_double) {
  ShenandoahSignedSize s = ShenandoahSignedSize::get(std::numeric_limits<double>::quiet_NaN());
  EXPECT_TRUE(std::isnan(s.value));
  EXPECT_STREQ(s.unit, "B");
}

TEST(ShenandoahUtilsTest, format_unit_boundaries) {
  // nextafter moves _toward_ the argument, so moving to zero gets the next smallest
  // number representable as a double that is less than the value on the unit boundary.
  EXPECT_STREQ(ShenandoahSignedSize::get(std::nextafter(100.0 * K, 0.0)).unit, "B");
  EXPECT_STREQ(ShenandoahSignedSize::get(std::nextafter(100.0 * M, 0.0)).unit, "K");
#ifdef _LP64
  EXPECT_STREQ(ShenandoahSignedSize::get(std::nextafter(100.0 * G, 0.0)).unit, "M");
#endif
}