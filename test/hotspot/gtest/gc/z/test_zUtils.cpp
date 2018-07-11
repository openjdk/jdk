/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zUtils.inline.hpp"
#include "unittest.hpp"

#include <limits>

template <typename T>
static T max_alignment() {
  T max = std::numeric_limits<T>::max();
  return max ^ (max >> 1);
}

TEST(ZUtilsTest, round_up_power_of_2) {
  EXPECT_EQ(ZUtils::round_up_power_of_2(1u), 1u);
  EXPECT_EQ(ZUtils::round_up_power_of_2(2u), 2u);
  EXPECT_EQ(ZUtils::round_up_power_of_2(3u), 4u);
  EXPECT_EQ(ZUtils::round_up_power_of_2(4u), 4u);
  EXPECT_EQ(ZUtils::round_up_power_of_2(5u), 8u);
  EXPECT_EQ(ZUtils::round_up_power_of_2(6u), 8u);
  EXPECT_EQ(ZUtils::round_up_power_of_2(7u), 8u);
  EXPECT_EQ(ZUtils::round_up_power_of_2(8u), 8u);
  EXPECT_EQ(ZUtils::round_up_power_of_2(9u), 16u);
  EXPECT_EQ(ZUtils::round_up_power_of_2(10u), 16u);
  EXPECT_EQ(ZUtils::round_up_power_of_2(1023u), 1024u);
  EXPECT_EQ(ZUtils::round_up_power_of_2(1024u), 1024u);
  EXPECT_EQ(ZUtils::round_up_power_of_2(1025u), 2048u);

  const size_t max = max_alignment<size_t>();
  EXPECT_EQ(ZUtils::round_up_power_of_2(max - 1), max);
  EXPECT_EQ(ZUtils::round_up_power_of_2(max), max);
}

TEST(ZUtilsTest, round_down_power_of_2) {
  EXPECT_EQ(ZUtils::round_down_power_of_2(1u), 1u);
  EXPECT_EQ(ZUtils::round_down_power_of_2(2u), 2u);
  EXPECT_EQ(ZUtils::round_down_power_of_2(3u), 2u);
  EXPECT_EQ(ZUtils::round_down_power_of_2(4u), 4u);
  EXPECT_EQ(ZUtils::round_down_power_of_2(5u), 4u);
  EXPECT_EQ(ZUtils::round_down_power_of_2(6u), 4u);
  EXPECT_EQ(ZUtils::round_down_power_of_2(7u), 4u);
  EXPECT_EQ(ZUtils::round_down_power_of_2(8u), 8u);
  EXPECT_EQ(ZUtils::round_down_power_of_2(9u), 8u);
  EXPECT_EQ(ZUtils::round_down_power_of_2(10u), 8u);
  EXPECT_EQ(ZUtils::round_down_power_of_2(1023u), 512u);
  EXPECT_EQ(ZUtils::round_down_power_of_2(1024u), 1024u);
  EXPECT_EQ(ZUtils::round_down_power_of_2(1025u), 1024u);

  const size_t max = max_alignment<size_t>();
  EXPECT_EQ(ZUtils::round_down_power_of_2(max), max);
  EXPECT_EQ(ZUtils::round_down_power_of_2(max - 1), max / 2);
}
