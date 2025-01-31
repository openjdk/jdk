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

#include <iostream>
#include <climits>

#include "opto/addnode.hpp"
#include "unittest.hpp"

void test_xor_bounds(jlong hi_0, jlong hi_1, jlong val_0, jlong val_1) {

  // Skip out-of-bounds values for convenience
  if(val_0> hi_0 || val_0 < 0 || val_1 > hi_1 || val_1< 0) {
    return;
  }

  EXPECT_LE(val_0 ^ val_1, XorLNode::calc_xor_max(hi_0, hi_1));

  // check ints when in range
  if(hi_0 <= INT_MAX && hi_1 <= INT_MAX) {
    EXPECT_LE(val_0 ^ val_1, XorINode::calc_xor_max(hi_0, hi_1));
  }
}

void test_exhaustive_values(jlong hi_0, jlong hi_1){

  jlong fail_val_0, fail_val_1;

  bool hit_bound=false;
  for(jlong val_0 = 0; val_0 <= hi_0; val_0++){
    for(jlong val_1 = val_0; val_1 <= hi_1; val_1++){
      test_xor_bounds(hi_0, hi_1, val_0, val_1);
    }
  }
}

void test_sample_values(jlong hi_0, jlong hi_1){

  jlong fail_val_0, fail_val_1;

  for(int i=0; i<=3; i++){
    for(int j=0; j<=3; j++){
      // Some bit combinations near the low and high ends of the range
      test_xor_bounds(hi_0, hi_1, i, j);
      test_xor_bounds(hi_0, hi_1, hi_0-i, hi_1-j);
    }
  }
}

void test_exhaustive_values_with_bounds_in_range(jlong lo, jlong hi){
  for(jlong hi_0 = lo; hi_0 <= hi; hi_0++){
    for(jlong hi_1 = hi_0; hi_1 <=hi; hi_1++){
      test_exhaustive_values(hi_0, hi_1);
    }
  }
}

void test_sample_values_with_bounds_in_range(jlong lo, jlong hi){
  for(jlong hi_0 = lo; hi_0 <= hi; hi_0++){
    for(jlong hi_1 = hi_0; hi_1 <=hi; hi_1++){
      test_sample_values(hi_0, hi_1);
    }
  }
}

TEST_VM(opto, xor_max) {
  test_exhaustive_values_with_bounds_in_range(0, 15);
  test_sample_values_with_bounds_in_range(INT_MAX - 1, INT_MAX);
  test_sample_values_with_bounds_in_range((1 << 30) - 1, 1 << 30);
  test_sample_values_with_bounds_in_range(LONG_MAX - 1, LONG_MAX);
  test_sample_values_with_bounds_in_range((1L << 62) - 1, 1L << 62);
}
