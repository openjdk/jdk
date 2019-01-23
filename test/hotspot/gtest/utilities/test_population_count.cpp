/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "libadt/vectset.hpp"
#include "runtime/os.hpp"
#include "utilities/population_count.hpp"
#include "utilities/globalDefinitions.hpp"
#include "unittest.hpp"


TEST(population_count, sparse) {
  extern uint8_t bitsInByte[BITS_IN_BYTE_ARRAY_SIZE];
  // Step through the entire input range from a random starting point,
  // verify population_count return values against the lookup table
  // approach used historically
  uint32_t step = 4711;
  for (uint32_t value = os::random() % step; value < UINT_MAX - step; value += step) {
    uint32_t lookup = bitsInByte[(value >> 24) & 0xff] +
                      bitsInByte[(value >> 16) & 0xff] +
                      bitsInByte[(value >> 8)  & 0xff] +
                      bitsInByte[ value        & 0xff];

    EXPECT_EQ(lookup, population_count(value))
        << "value = " << value;
  }

  // Test a few edge cases
  EXPECT_EQ(0u, population_count(0u))
      << "value = " << 0;
  EXPECT_EQ(1u, population_count(1u))
      << "value = " << 1;
  EXPECT_EQ(1u, population_count(2u))
      << "value = " << 2;
  EXPECT_EQ(32u, population_count(UINT_MAX))
      << "value = " << UINT_MAX;
  EXPECT_EQ(31u, population_count(UINT_MAX - 1))
      << "value = " << (UINT_MAX - 1);
}
