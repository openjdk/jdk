/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL.  Use is subject to license terms.
 */

#include "precompiled.hpp"
#include "gc/z/zArray.inline.hpp"
#include "unittest.hpp"

TEST(ZArrayTest, test_add) {
  ZArray<int> a;

  // Add elements
  for (int i = 0; i < 10; i++) {
    a.add(i);
  }

  // Check size
  ASSERT_EQ(a.size(), 10u);

  // Check elements
  for (int i = 0; i < 10; i++) {
    EXPECT_EQ(a.at(i), i);
  }
}

TEST(ZArrayTest, test_clear) {
  ZArray<int> a;

  // Add elements
  for (int i = 0; i < 10; i++) {
    a.add(i);
  }

  // Check size
  ASSERT_EQ(a.size(), 10u);
  ASSERT_EQ(a.is_empty(), false);

  // Clear elements
  a.clear();

  // Check size
  ASSERT_EQ(a.size(), 0u);
  ASSERT_EQ(a.is_empty(), true);

  // Add element
  a.add(11);

  // Check size
  ASSERT_EQ(a.size(), 1u);
  ASSERT_EQ(a.is_empty(), false);

  // Clear elements
  a.clear();

  // Check size
  ASSERT_EQ(a.size(), 0u);
  ASSERT_EQ(a.is_empty(), true);
}

TEST(ZArrayTest, test_iterator) {
  ZArray<int> a;

  // Add elements
  for (int i = 0; i < 10; i++) {
    a.add(i);
  }

  // Iterate
  int count = 0;
  ZArrayIterator<int> iter(&a);
  for (int value; iter.next(&value);) {
    ASSERT_EQ(a.at(count), count);
    count++;
  }

  // Check count
  ASSERT_EQ(count, 10);
}
