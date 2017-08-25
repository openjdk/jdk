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
 *
 */

#include "precompiled.hpp"
#include "utilities/count_trailing_zeros.hpp"
#include "utilities/globalDefinitions.hpp"
#include "unittest.hpp"

TEST(count_trailing_zeros, one_or_two_set_bits) {
  unsigned i = 0;               // Position of a set bit.
  for (uintx ix = 1; ix != 0; ix <<= 1, ++i) {
    unsigned j = 0;             // Position of a set bit.
    for (uintx jx = 1; jx != 0; jx <<= 1, ++j) {
      uintx value = ix | jx;
      EXPECT_EQ(MIN2(i, j), count_trailing_zeros(value))
        << "value = " << value;
    }
  }
}

TEST(count_trailing_zeros, all_ones_followed_by_all_zeros) {
  unsigned i = BitsPerWord - 1; // Index of most significant set bit.
  uintx value = ~(uintx)0;
  for ( ; value != 0; value >>= 1, --i) {
    EXPECT_EQ(0u, count_trailing_zeros(value))
      << "value = " << value;
  }
}

TEST(count_trailing_zeros, all_zeros_followed_by_all_ones) {
  unsigned i = 0;               // Index of least significant set bit.
  uintx value = ~(uintx)0;
  for ( ; value != 0; value <<= 1, ++i) {
    EXPECT_EQ(i, count_trailing_zeros(value))
      << "value = " << value;
  }
}
