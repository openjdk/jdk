/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/z/zArray.inline.hpp"
#include "zunittest.hpp"

class ZArrayTest : public ZTest {};

TEST(ZArray, sanity) {
  ZArray<int> a;

  // Add elements
  for (int i = 0; i < 10; i++) {
    a.append(i);
  }

  ZArray<int> b;

  b.swap(&a);

  // Check size
  ASSERT_EQ(a.length(), 0);
  ASSERT_EQ(a.capacity(), 0);
  ASSERT_EQ(a.is_empty(), true);

  ASSERT_EQ(b.length(), 10);
  ASSERT_GE(b.capacity(), 10);
  ASSERT_EQ(b.is_empty(), false);

  // Clear elements
  a.clear();

  // Check that b is unaffected
  ASSERT_EQ(b.length(), 10);
  ASSERT_GE(b.capacity(), 10);
  ASSERT_EQ(b.is_empty(), false);

  a.append(1);

  // Check that b is unaffected
  ASSERT_EQ(b.length(), 10);
  ASSERT_GE(b.capacity(), 10);
  ASSERT_EQ(b.is_empty(), false);
}

TEST(ZArray, iterator) {
  ZArray<int> a;

  // Add elements
  for (int i = 0; i < 10; i++) {
    a.append(i);
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

TEST_F(ZArrayTest, slice) {
  ZArray<int> a0(0);
  ZArray<int> a10(10);
  ZArray<int> ar(10 + abs(random() % 10));

  // Add elements
  for (int i = 0; i < ar.capacity(); ++i) {
    const auto append = [&](ZArray<int>& a) {
      if (i < a.capacity()) {
        a.append(i);
      }
    };

    append(a0);
    append(a10);
    append(ar);
  }

  {
    const auto reverse_test = [](const ZArray<int>& original) {
      ZArray<int> a(original.capacity());
      a.appendAll(&original);

      const auto reverse = [](ZArraySlice<int> slice, auto reverse) -> ZArraySlice<int> {
        const auto swap_elements = [](ZArraySlice<int> s1, ZArraySlice<int> s2) {
          ASSERT_EQ(s1.length(), s2.length());
          for (int i = 0; i < s1.length(); ++i) {
            ::swap(s1.at(i), s2.at(i));
          }
        };

        const int length = slice.length();
        if (length > 1) {
          const int middle = length / 2;
          swap_elements(
            reverse(slice.slice_front(middle), reverse),
            reverse(slice.slice_back(length - middle), reverse)
          );
        }
        return slice;
      };

      const auto check_reversed = [](ZArraySlice<const int> original, ZArraySlice<int> reversed) {
        ASSERT_EQ(original.length(), reversed.length());
        for (int e : original) {
          ASSERT_EQ(e, reversed.pop());
        }
      };

      ZArraySlice<int> a_reversed = reverse(a, reverse);
      check_reversed(original, a_reversed);
    };

    reverse_test(a0);
    reverse_test(a10);
    reverse_test(ar);
  }

  {
    const auto sort_test = [&](const ZArray<int>& original) {
      ZArray<int> a(original.capacity());
      a.appendAll(&original);

      const auto shuffle = [&](ZArraySlice<int> slice) {
        for (int i = 1; i < slice.length(); ++i) {
          const ptrdiff_t random_index = random() % (i + 1);
          ::swap(slice.at(i), slice.at(random_index));
        }
      };

      const auto qsort = [](ZArraySlice<int> slice, auto qsort) -> void {
        const auto partition = [](ZArraySlice<int> slice) {
          const int p = slice.last();
          int pi = 0;
          for (int i = 0; i < slice.length() - 1; ++i) {
            if (slice.at(i) < p) {
              ::swap(slice.at(i), slice.at(pi++));
            }
          }
          ::swap(slice.at(pi), slice.last());
          return pi;
        };

        if (slice.length() > 1) {
          const int pi = partition(slice);
          qsort(slice.slice_front(pi), qsort);
          qsort(slice.slice_back(pi + 1), qsort);
        }
      };

      const auto verify = [](ZArraySlice<const int> slice) {
        for (int i = 0; i < slice.length(); ++i) {
          int e = slice.at(i);
          for (int l : slice.slice_front(i)) {
            ASSERT_GE(e, l);
          }
          for (int g : slice.slice_back(i)) {
            ASSERT_LE(e, g);
          }
        }
      };

      shuffle(a);
      qsort(a, qsort);
      verify(a);
    };

    sort_test(a0);
    sort_test(a10);
    sort_test(ar);
  }
}
