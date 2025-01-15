/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
#include "gc/shenandoah/shenandoahSimpleBitMap.hpp"
#include "gc/shenandoah/shenandoahSimpleBitMap.inline.hpp"

#include <iostream>
#include "utilities/ostream.hpp"
#include "utilities/vmassert_uninstall.hpp"
#include "utilities/vmassert_reinstall.hpp"
#include "unittest.hpp"

static bool _success;
static size_t _assertion_failures;

#define BitMapAssertEqual(a, b)  ASSERT_EQ((a), (b)); if ((a) != (b)) { _assertion_failures++; }

class ShenandoahSimpleBitMapTest: public ::testing::Test {
protected:

  static const ssize_t SMALL_BITMAP_SIZE =  512;
  static const ssize_t LARGE_BITMAP_SIZE = 4096;

  // set_bits[] is an array of indexes holding bits that are supposed to be set, in increasing order.
  static void verifyBitMapState(ShenandoahSimpleBitMap& bm, ssize_t size, ssize_t set_bits[], ssize_t num_set_bits) {
    // Verify number of bits
    BitMapAssertEqual(bm.size(), size);

    ssize_t set_bit_index = 0;
    // Check that is_set(idx) for every possible idx
    for (ssize_t i = 0; i < size; i++) {
      bool is_set = bm.is_set(i);
      bool intended_value = false;;
      if (set_bit_index < num_set_bits) {
        if (set_bits[set_bit_index] == i) {
          intended_value = true;
          set_bit_index++;
        }
      } else {
        // If we've exhausted set_bits array, there should be no more set_bits
        BitMapAssertEqual(is_set, false);
        BitMapAssertEqual(set_bit_index, num_set_bits);
      }
      BitMapAssertEqual(is_set, intended_value);
    }
    BitMapAssertEqual(set_bit_index, num_set_bits);

    // Check that bits_at(array_idx) matches intended value for every valid array_idx value
    set_bit_index = 0;
    ssize_t alignment = bm.alignment();
    for (ssize_t i = 0; i < size; i += alignment) {
      size_t bits = bm.bits_at(i);
      for (ssize_t b = 0; b < alignment; b++) {
        ssize_t bit_value = i + b;
        bool intended_value = false;;
        if (set_bit_index < num_set_bits) {
          if (set_bits[set_bit_index] == bit_value) {
            intended_value = true;
            set_bit_index++;
          }
        }
        size_t bit_mask = ((size_t) 0x01) << b;
        bool is_set = (bits & bit_mask) != 0;
        BitMapAssertEqual(is_set, intended_value);
      }
    }

    // Make sure find_first_set_bit() works correctly
    ssize_t probe_point = 0;
    for (ssize_t i = 0; i < num_set_bits; i++) {
      ssize_t next_expected_bit = set_bits[i];
      probe_point = bm.find_first_set_bit(probe_point);
      BitMapAssertEqual(probe_point, next_expected_bit);
      probe_point++;            // Prepare to look beyond the most recent bit.
    }
    if (probe_point < size) {
      probe_point = bm.find_first_set_bit(probe_point);
      BitMapAssertEqual(probe_point, size); // Verify that last failed search returns sentinel value: num bits in bit map
    }

    // Confirm that find_first_set_bit() with a bounded search space works correctly
    // Limit this search to the first 3/4 of the full bit map
    ssize_t boundary_idx = 3 * size / 4;
    probe_point = 0;
    for (ssize_t i = 0; i < num_set_bits; i++) {
      ssize_t next_expected_bit = set_bits[i];
      if (next_expected_bit >= boundary_idx) {
        break;
      } else {
        probe_point = bm.find_first_set_bit(probe_point, boundary_idx);
        BitMapAssertEqual(probe_point, next_expected_bit);
        probe_point++;            // Prepare to look beyond the most recent bit.
      }
    }
    if (probe_point < boundary_idx) {
      // In case there are no set bits in the last 1/4 of bit map, confirm that last failed search returns sentinel: boundary_idx
      probe_point = bm.find_first_set_bit(probe_point, boundary_idx);
      BitMapAssertEqual(probe_point, boundary_idx);
    }

    // Make sure find_last_set_bit() works correctly
    probe_point = size - 1;
    for (ssize_t i = num_set_bits - 1; i >= 0; i--) {
      ssize_t next_expected_bit = set_bits[i];
      probe_point = bm.find_last_set_bit(probe_point);
      BitMapAssertEqual(probe_point, next_expected_bit);
      probe_point--;            // Prepare to look before the most recent bit.
    }
    if (probe_point >= 0) {
      probe_point = bm.find_last_set_bit(probe_point);
      BitMapAssertEqual(probe_point, (ssize_t) -1); // Verify that last failed search returns sentinel value: -1
    }

    // Confirm that find_last_set_bit() with a bounded search space works correctly
    // Limit this search to the last 3/4 of the full bit map
    boundary_idx = size / 4;
    probe_point = size - 1;
    for (ssize_t i = num_set_bits - 1; i >= 0; i--) {
      ssize_t next_expected_bit = set_bits[i];
      if (next_expected_bit > boundary_idx) {
        probe_point = bm.find_last_set_bit(boundary_idx, probe_point);
        BitMapAssertEqual(probe_point, next_expected_bit);
        probe_point--;
      } else {
        break;
      }
    }
    if (probe_point > boundary_idx) {
      probe_point = bm.find_last_set_bit(boundary_idx, probe_point);
        // Verify that last failed search returns sentinel value: boundary_idx
      BitMapAssertEqual(probe_point, boundary_idx);
    }

    // What's the longest cluster of consecutive bits
    ssize_t previous_value = -2;
    ssize_t longest_run = 0;
    ssize_t current_run = 0;
    for (ssize_t i = 0; i < num_set_bits; i++) {
      ssize_t next_expected_bit = set_bits[i];
      if (next_expected_bit == previous_value + 1) {
        current_run++;
      } else {
        previous_value = next_expected_bit;
        current_run = 1;
      }
      if (current_run > longest_run) {
        longest_run = current_run;
      }
      previous_value = next_expected_bit;
    }

    // Confirm that find_first_consecutive_set_bits() works for each cluster size known to have at least one match
    for (ssize_t cluster_size = 1; cluster_size <= longest_run; cluster_size++) {
      // Verify that find_first_consecutive_set_bits() works
      ssize_t bit_idx = 0;
      ssize_t probe_point = 0;
      while ((probe_point <= size - cluster_size) && (bit_idx <= num_set_bits - cluster_size)) {
        bool cluster_found = false;
        while (!cluster_found && (bit_idx + cluster_size <= num_set_bits)) {
          cluster_found = true;
          for (ssize_t i = 1; i < cluster_size; i++) {
            if (set_bits[bit_idx] + i != set_bits[bit_idx + i]) {
              cluster_found = false;
              bit_idx++;
              break;
            }
          }
        }
        if (cluster_found) {
          ssize_t next_expected_cluster = set_bits[bit_idx];
          ssize_t orig_probe_point = probe_point;
          probe_point = bm.find_first_consecutive_set_bits(orig_probe_point, cluster_size);
          BitMapAssertEqual(next_expected_cluster, probe_point);
          probe_point++;
          bit_idx++;
        } else {
          bit_idx++;
          break;
        }
      }
      if (probe_point < size) {
        // Confirm that the last request, which fails to find a cluster, returns sentinel value: num_bits
        probe_point = bm.find_first_consecutive_set_bits(probe_point, cluster_size);
        BitMapAssertEqual(probe_point, size);
      }

      // Repeat the above experiment, using 3/4 size as the search boundary_idx
      bit_idx = 0;
      probe_point = 0;
      boundary_idx = 4 * size / 4;
      while ((probe_point <= boundary_idx - cluster_size) && (bit_idx <= num_set_bits - cluster_size)) {
        bool cluster_found = false;
        while (!cluster_found && (bit_idx + cluster_size <= num_set_bits)) {
          cluster_found = true;
          for (int i = 1; i < cluster_size; i++) {
            if (set_bits[bit_idx] + i != set_bits[bit_idx + i]) {
              cluster_found = false;
              bit_idx++;
              break;
            }
          }
        }
        if (cluster_found) {
          ssize_t next_expected_cluster = set_bits[bit_idx];
          probe_point = bm.find_first_consecutive_set_bits(probe_point, boundary_idx, cluster_size);
          BitMapAssertEqual(next_expected_cluster, probe_point);
          probe_point++;
          bit_idx++;
        } else {
          bit_idx++;
        }
      }
      if (probe_point < boundary_idx) {
        // Confirm that the last request, which fails to find a cluster, returns sentinel value: boundary_idx
        probe_point = bm.find_first_consecutive_set_bits(probe_point, boundary_idx, cluster_size);
        BitMapAssertEqual(probe_point, boundary_idx);
      }

      // Verify that find_last_consecutive_set_bits() works
      bit_idx = num_set_bits - 1;
      probe_point = size - 1;
      // Iterate over all set bits in reverse order
      while (bit_idx + 1 >= cluster_size) {
        bool cluster_found = true;
        for (int i = 1; i < cluster_size; i++) {
          if (set_bits[bit_idx] - i != set_bits[bit_idx - i]) {
            cluster_found = false;
            break;
          }
        }
        if (cluster_found) {
          ssize_t next_expected_cluster = set_bits[bit_idx] + 1 - cluster_size;
          probe_point = bm.find_last_consecutive_set_bits(probe_point, cluster_size);
          BitMapAssertEqual(next_expected_cluster, probe_point);
          probe_point = probe_point + cluster_size - 2;
          bit_idx--;
        } else {
          bit_idx--;
        }
      }
      if (probe_point >= 0) {
        // Confirm that the last request, which fails to find a cluster, returns sentinel value: boundary_idx
        probe_point = bm.find_last_consecutive_set_bits(boundary_idx, probe_point, cluster_size);
        BitMapAssertEqual(probe_point, (ssize_t) boundary_idx);
      }

      // Verify that find_last_consecutive_set_bits() works with the search range bounded at 1/4 size
      bit_idx = num_set_bits - 1;
      probe_point = size - 1;
      boundary_idx = size / 4;
      while (bit_idx + 1 >= cluster_size) {
        bool cluster_found = true;
        for (int i = 1; i < cluster_size; i++) {
          if (set_bits[bit_idx] - i != set_bits[bit_idx - i]) {
            cluster_found = false;
            break;
          }
        }
        if (cluster_found && (set_bits[bit_idx] + 1 - cluster_size > boundary_idx)) {
          ssize_t next_expected_cluster = set_bits[bit_idx] + 1 - cluster_size;
          probe_point = bm.find_last_consecutive_set_bits(boundary_idx, probe_point, cluster_size);
          BitMapAssertEqual(next_expected_cluster, probe_point);
          probe_point = probe_point + cluster_size - 2;
          bit_idx--;
        } else if (set_bits[bit_idx] + 1 - cluster_size <= boundary_idx) {
          break;
        } else {
          bit_idx--;
        }
      }
      if (probe_point > boundary_idx) {
        // Confirm that the last request, which fails to find a cluster, returns sentinel value: boundary_idx
        probe_point = bm.find_last_consecutive_set_bits(boundary_idx, probe_point, cluster_size);
        BitMapAssertEqual(probe_point, boundary_idx);
      }
    }

    // Confirm that find_first_consecutive_set_bits() works for a cluster size known not to have any matches
    probe_point = bm.find_first_consecutive_set_bits(0, longest_run + 1);
    BitMapAssertEqual(probe_point, size);  // Confirm: failed search returns sentinel: size

    probe_point = bm.find_last_consecutive_set_bits(size - 1, longest_run + 1);
    BitMapAssertEqual(probe_point, (ssize_t) -1);    // Confirm: failed search returns sentinel: -1

    boundary_idx = 3 * size / 4;
    probe_point = bm.find_first_consecutive_set_bits(0, boundary_idx, longest_run + 1);
    BitMapAssertEqual(probe_point, boundary_idx); // Confirm: failed search returns sentinel: boundary_idx

    boundary_idx = size / 4;
    probe_point = bm.find_last_consecutive_set_bits(boundary_idx, size - 1, longest_run + 1);
    BitMapAssertEqual(probe_point, boundary_idx);           // Confirm: failed search returns sentinel: boundary_idx
  }

public:

  static bool run_test() {

    _success = false;
    _assertion_failures = 0;

    ShenandoahSimpleBitMap bm_small(SMALL_BITMAP_SIZE);
    ShenandoahSimpleBitMap bm_large(LARGE_BITMAP_SIZE);

    // Initial state of each bitmap is all bits are clear.  Confirm this:
    ssize_t set_bits_0[1] = { 0 };
    verifyBitMapState(bm_small, SMALL_BITMAP_SIZE, set_bits_0, 0);
    verifyBitMapState(bm_large, LARGE_BITMAP_SIZE, set_bits_0, 0);

    bm_small.set_bit(5);
    bm_small.set_bit(63);
    bm_small.set_bit(128);
    ssize_t set_bits_1[3] = { 5, 63, 128 };
    verifyBitMapState(bm_small, SMALL_BITMAP_SIZE, set_bits_1, 3);

    bm_large.set_bit(5);
    bm_large.set_bit(63);
    bm_large.set_bit(128);
    verifyBitMapState(bm_large, LARGE_BITMAP_SIZE, set_bits_1, 3);

    // Test some consecutive bits
    bm_small.set_bit(140);
    bm_small.set_bit(141);
    bm_small.set_bit(142);

    bm_small.set_bit(253);
    bm_small.set_bit(254);
    bm_small.set_bit(255);

    bm_small.set_bit(271);
    bm_small.set_bit(272);

    bm_small.set_bit(320);
    bm_small.set_bit(321);
    bm_small.set_bit(322);

    bm_small.set_bit(361);

    ssize_t set_bits_2[15] = { 5, 63, 128, 140, 141, 142, 253, 254, 255, 271, 272, 320, 321, 322, 361 };
    verifyBitMapState(bm_small, SMALL_BITMAP_SIZE, set_bits_2, 15);

    bm_large.set_bit(140);
    bm_large.set_bit(141);
    bm_large.set_bit(142);

    bm_large.set_bit(1021);
    bm_large.set_bit(1022);
    bm_large.set_bit(1023);

    bm_large.set_bit(1051);

    bm_large.set_bit(1280);
    bm_large.set_bit(1281);
    bm_large.set_bit(1282);

    bm_large.set_bit(1300);
    bm_large.set_bit(1301);
    bm_large.set_bit(1302);

    ssize_t set_bits_3[16] = { 5, 63, 128, 140, 141, 142, 1021, 1022, 1023, 1051, 1280, 1281, 1282, 1300, 1301, 1302 };
    verifyBitMapState(bm_large, LARGE_BITMAP_SIZE, set_bits_3, 16);

    // Test clear_bit
    bm_small.clear_bit(141);
    bm_small.clear_bit(253);
    ssize_t set_bits_4[13] = { 5, 63, 128, 140, 142, 254, 255, 271, 272, 320, 321, 322, 361 };
    verifyBitMapState(bm_small, SMALL_BITMAP_SIZE, set_bits_4, 13);

    bm_large.clear_bit(5);
    bm_large.clear_bit(63);
    bm_large.clear_bit(128);
    bm_large.clear_bit(141);
    ssize_t set_bits_5[12] = { 140, 142, 1021, 1022, 1023, 1051, 1280, 1281, 1282, 1300, 1301, 1302 };
    verifyBitMapState(bm_large, LARGE_BITMAP_SIZE, set_bits_5, 12);

    // Look for large island of contiguous surrounded by smaller islands of contiguous
    bm_large.set_bit(1024);
    bm_large.set_bit(1025);  // size-5 island from 1021 to 1025
    bm_large.set_bit(1027);
    bm_large.set_bit(1028);
    bm_large.set_bit(1029);
    bm_large.set_bit(1030);
    bm_large.set_bit(1031);
    bm_large.set_bit(1032);  // size-6 island from 1027 to 1032
    bm_large.set_bit(1034);
    bm_large.set_bit(1035);
    bm_large.set_bit(1036);  // size-3 island from 1034 to 1036
    ssize_t set_bits_6[23] = {  140,  142, 1021, 1022, 1023, 1024, 1025, 1027, 1028, 1029, 1030,
                               1031, 1032, 1034, 1035, 1036, 1051, 1280, 1281, 1282, 1300, 1301, 1302 };
    verifyBitMapState(bm_large, LARGE_BITMAP_SIZE, set_bits_6, 23);

    // Test that entire bitmap word (from 1024 to 1088) is 1's
    ssize_t set_bits_7[76];
    set_bits_7[0] = 140;
    set_bits_7[1] = 142;
    set_bits_7[2] = 1021;
    set_bits_7[3] = 1022;
    set_bits_7[4] = 1023;
    size_t bit_idx = 5;
    for (ssize_t i = 1024; i <= 1088; i++) {
      bm_large.set_bit(i);
      set_bits_7[bit_idx++] = i;
    }
    set_bits_7[bit_idx++] = 1280;
    set_bits_7[bit_idx++] = 1281;
    set_bits_7[bit_idx++] = 1282;
    set_bits_7[bit_idx++] = 1300;
    set_bits_7[bit_idx++] = 1301;
    set_bits_7[bit_idx++] = 1302;
    verifyBitMapState(bm_large, LARGE_BITMAP_SIZE, set_bits_7, bit_idx);

    // Test clear_all()
    bm_small.clear_all();
    bm_large.clear_all();

    verifyBitMapState(bm_small, SMALL_BITMAP_SIZE, set_bits_0, 0);
    verifyBitMapState(bm_large, LARGE_BITMAP_SIZE, set_bits_0, 0);

    _success = true;
    return true;
  }

};

TEST(BasicShenandoahSimpleBitMapTest, minimum_test) {

  bool result = ShenandoahSimpleBitMapTest::run_test();
  ASSERT_EQ(result, true);
  ASSERT_EQ(_success, true);
  ASSERT_EQ(_assertion_failures, (size_t) 0);
}
