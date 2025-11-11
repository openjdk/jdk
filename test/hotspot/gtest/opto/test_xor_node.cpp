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

#include "unittest.hpp"
#include "opto/utilities/xor.hpp"
#include "utilities/globalDefinitions.hpp" // For jint, juint

jint test_calc_max(const jint hi_0, const jint hi_1) {
  return xor_upper_bound_for_ranges<jint, juint>(hi_0, hi_1);
}

jlong test_calc_max(const jlong hi_0, const jlong hi_1) {
  return xor_upper_bound_for_ranges<jlong, julong>(hi_0, hi_1);
}

template <class S>
void test_xor_bounds(S hi_0, S hi_1, S val_0, S val_1) {
  ASSERT_GE(hi_0, 0);
  ASSERT_GE(hi_1, 0);

  // Skip out-of-bounds values for convenience
  if (val_0 > hi_0 || val_0 < S(0) || val_1 > hi_1 || val_1 < S(0)) {
    return;
  }

  S v = val_0 ^ val_1;
  S max = test_calc_max(hi_0, hi_1);
  EXPECT_LE(v, max);
}

template <class S>
void test_sample_values(S hi_0, S hi_1) {
  for (S i = 0; i <= 3; i++) {
    for (S j = 0; j <= 3; j++) {
      // Some bit combinations near the low and high ends of the range
      test_xor_bounds(hi_0, hi_1, i, j);
      test_xor_bounds(hi_0, hi_1, hi_0 - i, hi_1 - j);
    }
  }
}

template <class S>
void test_in_ranges(S lo, S hi){
  ASSERT_GE(lo, 0);
  ASSERT_LE(lo, hi);

  for (S hi_0 = lo; hi_0 <= hi; hi_0++) {
    for (S hi_1 = hi_0; hi_1 <=hi; hi_1++) {
      test_sample_values(hi_0, hi_1);
    }
  }
}

template <class S>
void test_exhaustive(S limit) {
  for (S hi_0 = 0; hi_0 <= limit; hi_0++) {
    for (S hi_1 = hi_0; hi_1 <= limit; hi_1++) {
      for (S val_0 = 0; val_0 <= hi_0; val_0++) {
        for (S val_1 = val_0; val_1 <= hi_1; val_1++) {
          test_xor_bounds(hi_0, hi_1, val_0, val_1);
        }
      }
    }
  }
}

template <class S>
void exec_tests() {
  S top_bit = max_power_of_2<S>();
  S prev_bit = top_bit >> 1;

  test_exhaustive<S>(15);

  test_in_ranges<S>(top_bit - 1, top_bit);
  test_in_ranges<S>(prev_bit - 1, prev_bit);
}

TEST_VM(opto, xor_max) {
  exec_tests<jint>();
  exec_tests<jlong>();
}
