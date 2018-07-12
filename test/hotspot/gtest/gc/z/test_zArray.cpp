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
