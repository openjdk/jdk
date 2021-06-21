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
 */

#include "precompiled.hpp"
#include "unittest.hpp"
#include "utilities/hashtable.hpp"

// Count how many keys in KVHashtable
template <class K, class V>
class CountIterator {
  int _sz = {0};

 public:
  bool do_entry(K key, V* val) {
    _sz++;
    return true;
  }

  int size() const {
    return _sz;
  }
};

TEST_VM(Hashtable, kvhashtable_remove) {
  KVHashtable<int, int, mtTest> map(137/*table_size*/);
  typedef CountIterator<int, int> Iter;
  const int SZ = 1000;

  for (int i = 0; i < SZ; ++i) {
    int* v = map.add(i, i);
    EXPECT_EQ(i, *v);
  }

  for (int i = 0; i < SZ; ++i) {
    EXPECT_TRUE(map.remove(i));
    EXPECT_FALSE(map.remove(i));
    EXPECT_EQ(NULL, map.lookup(i));

    Iter it;
    map.iterate(&it);
    EXPECT_EQ(SZ - (i + 1), it.size());
  }

  for (int i = 0; i < SZ; ++i) {
    int* v = map.add(i, i);
    EXPECT_EQ(i, *v);
  }
  // 2nd round: reverse order
  for (int i = SZ - 1; i >= 0; --i) {
    EXPECT_TRUE(map.remove(i));
    EXPECT_FALSE(map.remove(i));
    EXPECT_EQ(NULL, map.lookup(i));

    Iter it;
    map.iterate(&it);
    EXPECT_EQ(i, it.size());
  }

  for (int i = 0; i < SZ; ++i) {
    int* v = map.add(i, i);
    EXPECT_EQ(i, *v);
  }

  // 3rd round: start in middle
  int Mid = SZ / 2;
  for (int i = 0; i < SZ; ++i) {
    int j = i + Mid;
    if (j >= SZ) {
      j -= SZ;
    }
    //fprintf(stderr, "executing: %d %d \n", i, j);
    ASSERT_TRUE(map.remove(j));
    ASSERT_FALSE(map.remove(j));
    EXPECT_EQ(NULL, map.lookup(j));

    Iter it;
    map.iterate(&it);
    EXPECT_EQ(SZ - (i + 1), it.size());
  }
}
