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
 */

#include "gtestRandom.hpp"
#include "runtime/os.hpp"
#include "unittest.hpp"

static void assert_random_seeded_from_gtest() {
  unsigned int seed =
      static_cast<unsigned int>(::testing::UnitTest::GetInstance()->random_seed());

  // Advancing the VM's random state must not affect the gtest random sequence.
  for (int i = 0; i < 10; i++) {
    os::random();
    const int expected = os::next_random(seed);
    seed = static_cast<unsigned int>(expected);
    ASSERT_EQ(expected, GtestRandom::random());
  }
}

TEST(GtestRandom, seeded_from_gtest) {
  assert_random_seeded_from_gtest();
}

TEST_VM(GtestRandom, seeded_from_gtest_after_jvm_initialization) {
  assert_random_seeded_from_gtest();
}
