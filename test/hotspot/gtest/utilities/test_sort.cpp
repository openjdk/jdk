/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "runtime/os.hpp"
#include "utilities/growableArray.hpp"
#include <utilities/globalDefinitions.hpp>
#include <utilities/sort.hpp>
#include "unittest.hpp"

constexpr size_t SIZE = 128;

class TwoInt {
public:
  int val;
  int idx;

  TwoInt() : val(0), idx(0) {}
  TwoInt(int val, int idx) : val(val), idx(idx) {}
};

int ARRAY0[SIZE];
TwoInt ARRAY1[SIZE];

// Verify that the sort is correct, i.e. a[i] <= a[i + 1]
void test_insertion_sort() {
  for (size_t i = 0; i < SIZE; i++) {
    ARRAY0[i] = os::random();
  }
  GrowableArrayFromArray<int> view(ARRAY0, SIZE);
  InsertionSort::sort(view.ncbegin(), view.ncend(), [](int a, int b) {
    return a < b;
  });
  for (size_t i = 0; i < SIZE - 1; i++) {
    ASSERT_TRUE(ARRAY0[i] <= ARRAY0[i + 1]);
  }
}

// Verify that the sort is stable. Since there are 128 elements but the keys can only take 16
// values, there will inevitably be a lot of elements with the same key. We then verify that if the
// keys of 2 elements are the same, then the element that has the smaller idx will be ordered
// before the one with the larger idx.
void test_insertion_sort_stable() {
  for (size_t i = 0; i < SIZE; i++) {
    ARRAY1[i] = TwoInt(os::random() & 15, i);
  }
  GrowableArrayFromArray<TwoInt> view(ARRAY1, SIZE);
  InsertionSort::sort(view.ncbegin(), view.ncend(), [](TwoInt a, TwoInt b) {
    return a.val < b.val;
  });
  for (size_t i = 0; i < SIZE - 1; i++) {
    TwoInt a = ARRAY1[i];
    TwoInt b = ARRAY1[i + 1];
    ASSERT_TRUE(a.val <= b.val);
    if (a.val == b.val) {
      ASSERT_TRUE(a.idx < b.idx);
    }
  }
}

TEST(utilities, insertion_sort) {
  for (int i = 0; i < 100; i++) {
    test_insertion_sort();
    test_insertion_sort_stable();
  }
}
