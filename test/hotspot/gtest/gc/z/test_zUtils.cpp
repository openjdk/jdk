/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL.  Use is subject to license terms.
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
