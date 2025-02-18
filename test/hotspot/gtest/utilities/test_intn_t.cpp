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

template <unsigned int n>
static void test_intn_t() {
  static_assert(std::numeric_limits<intn_t<n>>::min() <= intn_t<n>(-1) && intn_t<n>(-1) < intn_t<n>(0) && intn_t<n>(0) <= std::numeric_limits<intn_t<n>>::max(), "basic sanity");
  for (int i = intn_t<n>::min; i <= intn_t<n>::max; i++) {
    ASSERT_EQ(i, int(intn_t<n>(i)));
    if (i > intn_t<n>::min) {
      ASSERT_TRUE(intn_t<n>(i - 1) < intn_t<n>(i));
    }
    if (i < intn_t<n>::max) {
      ASSERT_TRUE(intn_t<n>(i) < intn_t<n>(i + 1));
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
