/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shenandoah/shenandoahFreeSet.hpp"

#include "utilities/ostream.hpp"

#include "utilities/vmassert_uninstall.hpp"
#include <iostream>
#include "utilities/vmassert_reinstall.hpp"

#include "unittest.hpp"

#define SMALL_BITMAP_SIZE  128
#define LARGE_BITMAP_SIZE 4096

class ShenandoahSimpleBitMapTest: public ::testing::Test {
protected:
  ShenandoahSimpleBitMap bm_small(SMALL_BITMAP_SIZE);
  ShenandoahSimpleBitMap bm_large(LARGE_BITMAP_SIZE);
};

class BasicShenandoahSimpleBitMapTest: public ShenandoahSimpleBitMapTest {
protected:
  BasicShenandoahSimpleBitMapTest() {
    // Initial state of each bitmap is all bits are clear.  Confirm this:
    ASSERT_EQ(bm_small.number_of_bits(), SMALL_BITMAP_SIZE);
    ASSERT_EQ(bm_large.number_of_bits(), LARGE_BITMAP_SIZE);

    // Check that is_set(idx) for every possible idx
    for (ssize_t i = 0; i < SMALL_BITMAP_SIZE; i++) {
      bool is_set = bm_small.is_set(i);
      ASSERT_TRUE(!is_set);
    }

    for (ssize_t i = 0; i < LARGE_BITMAP_SIZE; i++) {
      bool is_set = bm_large.is_set(i);
      ASSERT_TRUE(!is_set);
    }

    // Check that bits_at(array_idx) is zero for every valid array_idx value
    size_t alignment = bm_small.alignment();
    size_t small_words = SMALL_BITMAP_SIZE / alignment;
    size_t large_words = LARGE_BITMAP_SIZE / alignment;
    for (ssize_t i = 0; i < small_words; i += alignment) {
      size_t bits = bm_small.bits_at(i);
      ASSERT_EQ(bits, (size_t) 0);
    }

    for (ssize_t i = 0; i < large_words; i += alignment) {
      size_t bits = bm_large.bits_at(i);
      ASSERT_EQ(bits, (size_t) 0);
    }

    // Confirm that find_next_set_bit(idx) returns _num_bits and find_next_set_bit(idx, boundary_idx) returns boundary_idx
    //  for all legal values of idx
    for (ssize_t i = 0; i < SMALL_BITMAP_SIZE; i++) {
      ssize_t result = bm_small.find_next_set_bit(i);
      // Expect number_of_bits result since set bit should not be found.
      ASSERT_EQ(result, SMALL_BITMAP_SIZE);
    }

    for (ssize_t i = LARGE_BITMAP_SIZE / 4; i < 3 * LARGE_BITMAP_SIZE/ 4; i++) {
      ssize_t result = bm_large.find_next_set_bit(i, 3 * LARGE_BITMAP_SIZE / 4);
      // Expect number_of_bits result since set bit should not be found.
      ASSERT_EQ(result, LARGE_BITMAP_SIZE);
    }

    for (ssize_t i = 0; i < SMALL_BITMAP_SIZE; i++) {
      ssize_t result = bm_small.find_next_set_bit(i);
      // Expect number_of_bits result since set bit should not be found.
      ASSERT_EQ(result, SMALL_BITMAP_SIZE);
    }




    // Confirm that find_prev_set_bit(idx) returns -1 and find_prev_set_bit(idx, boundary_idx) returns boundary idx
    //  for all legal values of idx

    // Confirm that find_next_consecutive_bits(1..8, idx, boundary_idx) returns boundary_idx for all legal values of idx

    // Confirm that find_next_consecutive_bits(1..8, idx) returns _num_bits for all legal values of idx

    // Confirm that find_prev_consecutive_bits(1..8, idx, boundary_idx) returns boundary_idx for all legal values of idx

    // Confirm that find_prev_consecutive_bits(1..8, idx) returns -1 for all legal values of idx
    
    // Execute clear_all(), then set bits 8, 31, 63, 68, 127
    // Run the same tests


    // clear_bits 31 and 68 and run the same tests

    // Execute clear_all, set two bits out of every 32 bits
    // Run the same tests

    // Execute clear_all, set 3 bits out of every 32 bits
    // Run the same tests

    // Execute clear_all, set 7 bits out of every 32 bits
    // Run the same tests

    // Selectively clear every other bit at the 64-bit offsets
    // Run the same tests

    // Execute clear_all, set 8 bits out of every 32 bits
    // Run the same tests

    // Clear all 8 bits at each 64-bit offset
    // Run the same tests

  }
};

TEST_VM_F(BasicShenandoahSimpleBitMapTest, maximum_test) {
  EXPECT_EQ(seq.maximum(), 100);
}

TEST_VM_F(BasicShenandoahNumberSeqTest, minimum_test) {
  EXPECT_EQ(0, seq.percentile(0));
}

TEST_VM_F(BasicShenandoahNumberSeqTest, percentile_test) {
  EXPECT_NEAR(0, seq.percentile(10), err);
  EXPECT_NEAR(1, seq.percentile(20), err);
  EXPECT_NEAR(10, seq.percentile(30), err);
  EXPECT_NEAR(100, seq.percentile(40), err);
  EXPECT_NEAR(100, seq.percentile(50), err);
  EXPECT_NEAR(100, seq.percentile(75), err);
  EXPECT_NEAR(100, seq.percentile(90), err);
  EXPECT_NEAR(100, seq.percentile(100), err);
}
