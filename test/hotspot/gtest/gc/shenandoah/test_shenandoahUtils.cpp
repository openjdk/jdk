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
#include <cmath>
#include <limits>

#include "unittest.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "utilities/globalDefinitions.hpp"

TEST(ShenandoahUtilsTest, format_byte_double) {
  ShenandoahSignedSize s = ShenandoahSignedSize::get(1024.0);
  EXPECT_EQ(s.value, 1024.0);
  EXPECT_EQ(s.unit, "B");
}

TEST(ShenandoahUtilsTest, format_kilo_double) {
  ShenandoahSignedSize s = ShenandoahSignedSize::get(K * 900.0);
  EXPECT_EQ(s.value, 900.0);
  EXPECT_EQ(s.unit, "K");
}

TEST(ShenandoahUtilsTest, format_signed_mega_double) {
  ShenandoahSignedSize s = ShenandoahSignedSize::get(M * -900.0);
  EXPECT_EQ(s.value, -900.0);
  EXPECT_EQ(s.unit, "M");
}

TEST(ShenandoahUtilsTest, format_signed_giga_double) {
  ShenandoahSignedSize s = ShenandoahSignedSize::get(G * -900.0);
  EXPECT_EQ(s.value, -900.0);
  EXPECT_EQ(s.unit, "G");
}

TEST(ShenandoahUtilsTest, format_infinite_double) {
  ShenandoahSignedSize s = ShenandoahSignedSize::get(std::numeric_limits<double>::infinity());
  EXPECT_TRUE(std::isinf(s.value));
  EXPECT_EQ(s.unit, "B");
}

TEST(ShenandoahUtilsTest, format_nan_double) {
  ShenandoahSignedSize s = ShenandoahSignedSize::get(std::numeric_limits<double>::quiet_NaN());
  EXPECT_TRUE(std::isnan(s.value));
  EXPECT_EQ(s.unit, "B");
}