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

#include "utilities/intn_t.hpp"
#include "unittest.hpp"

// Sanity tests for off-by-one errors
static_assert(intn_t<1>::min == -1 && intn_t<1>::max == 0, "");
static_assert(intn_t<2>::min == -2 && intn_t<2>::max == 1, "");
static_assert(intn_t<3>::min == -4 && intn_t<3>::max == 3, "");
static_assert(uintn_t<1>::max == 1, "");
static_assert(uintn_t<2>::max == 3, "");
static_assert(uintn_t<3>::max == 7, "");

template <unsigned int nbits>
static void test_intn_t() {
  static_assert(std::numeric_limits<intn_t<nbits>>::min() <= intn_t<nbits>(-1) &&
                intn_t<nbits>(-1) < intn_t<nbits>(0) &&
                intn_t<nbits>(0) <= std::numeric_limits<intn_t<nbits>>::max(), "basic sanity");
  constexpr int period = intn_t<nbits>::max - intn_t<nbits>::min + 1;
  for (int i = std::numeric_limits<signed char>::min(); i < std::numeric_limits<signed char>::max(); i++) {
    ASSERT_EQ(intn_t<nbits>(i), intn_t<nbits>(i + period));
    ASSERT_EQ(int(intn_t<nbits>(i)), int(intn_t<nbits>(i + period)));
  }
  for (int i = intn_t<nbits>::min; i <= intn_t<nbits>::max; i++) {
    ASSERT_EQ(i, int(intn_t<nbits>(i)));
    if (i > intn_t<nbits>::min) {
      ASSERT_TRUE(intn_t<nbits>(i - 1) < intn_t<nbits>(i));
    } else {
      ASSERT_TRUE(intn_t<nbits>(i - 1) > intn_t<nbits>(i));
    }
    if (i < intn_t<nbits>::max) {
      ASSERT_TRUE(intn_t<nbits>(i) < intn_t<nbits>(i + 1));
    } else {
      ASSERT_TRUE(intn_t<nbits>(i) > intn_t<nbits>(i + 1));
    }
  }
}

TEST(utilities, intn_t) {
  test_intn_t<1>();
  test_intn_t<2>();
  test_intn_t<3>();
  test_intn_t<4>();
  test_intn_t<5>();
  test_intn_t<6>();
  test_intn_t<7>();
  test_intn_t<8>();
}
