/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "memory/arena.hpp"
#include "nmt/memTag.hpp"
#include "unittest.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/unorderedMap.hpp"

template <class Key>
uint64_t degenerate_hash(const Key& key) {
  return 1;
}

template <auto HASH = primitive_hash<int>, auto KEY_EQUAL = primitive_equals<int>>
void test_basic() {
  Arena arena(mtTest);
  FlatHashTableArena<int, int, HASH, KEY_EQUAL> map(&arena);
  ASSERT_EQ(0U, arena.used());

  for (int i = 0; i < 10; i++) {
    ASSERT_EQ(0U, map.size());
    ASSERT_EQ(nullptr, map.get(0));
    ASSERT_EQ(nullptr, map.get(1));
    ASSERT_FALSE(map.remove(0));
    ASSERT_FALSE(map.remove(1));
    ASSERT_EQ(0U, map.size());

    ASSERT_TRUE(map.put(1, 2));
    ASSERT_EQ(1U, map.size());
    ASSERT_EQ(nullptr, map.get(0));
    int* v = map.get(1);
    ASSERT_NE(nullptr, v);
    ASSERT_EQ(2, *v);
    ASSERT_FALSE(map.remove(0));
    ASSERT_FALSE(map.put(1, 1));
    v = map.get(1);
    ASSERT_NE(nullptr, v);
    ASSERT_EQ(1, *v);
    ASSERT_FALSE(map.put_if_absent(1, 2));
    v = map.get(1);
    ASSERT_NE(nullptr, v);
    ASSERT_EQ(1, *v);
    ASSERT_EQ(1U, map.size());

    ASSERT_TRUE(map.put_if_absent(0, 1));
    ASSERT_EQ(2U, map.size());
    v = map.get(0);
    ASSERT_NE(nullptr, v);
    ASSERT_EQ(1, *v);
    v = map.get(1);
    ASSERT_NE(nullptr, v);
    ASSERT_EQ(1, *v);
    ASSERT_FALSE(map.put(0, 0));
    v = map.get(0);
    ASSERT_NE(nullptr, v);
    ASSERT_EQ(0, *v);
    ASSERT_FALSE(map.put_if_absent(0, 1));
    v = map.get(0);
    ASSERT_NE(nullptr, v);
    ASSERT_EQ(0, *v);
    ASSERT_EQ(2U, map.size());

    ASSERT_TRUE(map.remove(1));
    ASSERT_EQ(1U, map.size());
    v = map.get(0);
    ASSERT_NE(nullptr, v);
    ASSERT_EQ(0, *v);
    ASSERT_EQ(nullptr, map.get(1));
    ASSERT_FALSE(map.remove(1));
    ASSERT_FALSE(map.put_if_absent(0, 1));
    v = map.get(0);
    ASSERT_NE(nullptr, v);
    ASSERT_EQ(0, *v);
    ASSERT_EQ(1U, map.size());

    ASSERT_TRUE(map.remove(0));
    ASSERT_EQ(0U, map.size());
    ASSERT_EQ(nullptr, map.get(0));
    ASSERT_EQ(nullptr, map.get(1));
    ASSERT_FALSE(map.remove(0));
    ASSERT_FALSE(map.remove(1));
    ASSERT_EQ(0U, map.size());
  }
}

template <auto HASH = primitive_hash<int>, auto KEY_EQUAL = primitive_equals<int>>
void test_large_map() {
  constexpr int iterations = 10000;
  FlatHashTableCHeap<int, int, mtTest, HASH, KEY_EQUAL> map;
  for (int outer_idx = 0; outer_idx < 3; outer_idx++) {
    ASSERT_EQ(0U, map.size());
    for (int i = 0; i < iterations; i++) {
      ASSERT_EQ(nullptr, map.get(i));
    }
    for (int i = 0; i < iterations; i++) {
      ASSERT_TRUE(map.put(i, i + 1));
    }
    ASSERT_EQ(size_t(iterations), map.size());
    for (int i = 0; i < iterations; i++) {
      int* v = map.get(i);
      ASSERT_NE(nullptr, v);
      ASSERT_EQ(i + 1, *v);
    }
    for (int i = 0; i < iterations; i++) {
      ASSERT_FALSE(map.put(i, i));
    }
    ASSERT_EQ(size_t(iterations), map.size());
    for (int i = 0; i < iterations; i++) {
      int* v = map.get(i);
      ASSERT_NE(nullptr, v);
      ASSERT_EQ(i, *v);
    }
    for (int i = 0; i < iterations; i++) {
      ASSERT_FALSE(map.put_if_absent(i, i + 1));
    }
    ASSERT_EQ(size_t(iterations), map.size());
    for (int i = 0; i < iterations; i++) {
      int* v = map.get(i);
      ASSERT_NE(nullptr, v);
      ASSERT_EQ(i, *v);
    }
    for (int i = 0; i < iterations; i++) {
      ASSERT_TRUE(map.remove(i));
    }
  }
}

TEST_VM(utilities, unorderedMap) {
  test_basic();
  test_large_map();
  test_basic<degenerate_hash<int>>();
  test_large_map<degenerate_hash<int>>();
}
