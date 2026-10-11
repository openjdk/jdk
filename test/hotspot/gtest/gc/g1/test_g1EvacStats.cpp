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

#include "gc/g1/g1EvacStats.inline.hpp"
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/plab.inline.hpp"
#include "utilities/autoRestore.hpp"

#include "unittest.hpp"

class G1EvacStatsTest {

public:
  static void compute_desired_plab_size_bounds(uint target_plab_waste_percent, double last_plab_average_occupancy);
  static void compute_desired_plab_size_zero_used(uint target_plab_waste_percent, double last_plab_average_occupancy);
  static void compute_desired_plab_size_larger_waste(uint target_plab_waste_percent, double last_plab_average_occupancy);
  static void compute_desired_plab_size_check_overflow(size_t allocation_words);
};

void G1EvacStatsTest::compute_desired_plab_size_bounds(uint target_plab_waste_percent, double last_plab_average_occupancy) {
  UIntFlagSetting fl1(TargetPLABWastePct, target_plab_waste_percent);
  AutoModifyRestore<double> fl2(G1LastPLABAverageOccupancy, last_plab_average_occupancy);

  G1EvacStats stats("Test-Dummy-Old", OldPLABSize, PLABWeight);
  stats.add_allocated(600000);

  size_t expected = stats.used() + stats.used() * TargetPLABWastePct / 100;
  size_t actual = stats.compute_desired_plab_size();
  ASSERT_GT(actual, size_t(0));
  ASSERT_LE(actual, expected);
}

void G1EvacStatsTest::compute_desired_plab_size_zero_used(uint target_plab_waste_percent, double last_plab_average_occupancy) {
  UIntFlagSetting fl1(TargetPLABWastePct, target_plab_waste_percent);
  AutoModifyRestore<double> fl2(G1LastPLABAverageOccupancy, last_plab_average_occupancy);

  G1EvacStats stats("Test-Dummy-Old", OldPLABSize, PLABWeight);
  stats.add_allocated(0);

  size_t actual = stats.compute_desired_plab_size();
  ASSERT_EQ(actual, size_t(0));
}

void G1EvacStatsTest::compute_desired_plab_size_larger_waste(uint target_plab_waste_percent, double last_plab_average_occupancy) {
  UIntFlagSetting fl1(TargetPLABWastePct, target_plab_waste_percent);
  AutoModifyRestore<double> fl2(G1LastPLABAverageOccupancy, last_plab_average_occupancy);

  G1EvacStats stats("Test-Dummy-Old", OldPLABSize, PLABWeight);
  stats.add_allocated(500000);
  stats.add_region_end_waste(500001);

  size_t actual = stats.compute_desired_plab_size();
  ASSERT_EQ(actual, size_t(0));

  stats.reset();

  stats.add_allocated(500000);
  stats.add_region_end_waste(500000);

  actual = stats.compute_desired_plab_size();
  ASSERT_EQ(actual, size_t(0));
}

void G1EvacStatsTest::compute_desired_plab_size_check_overflow(size_t allocation_words) {
  UIntFlagSetting fl1(TargetPLABWastePct, 100);
  AutoModifyRestore<double> fl2(G1LastPLABAverageOccupancy, 50.0);

  G1EvacStats stats("Test-Dummy-Old", OldPLABSize, PLABWeight);
  stats.add_allocated(allocation_words);

  size_t actual = stats.compute_desired_plab_size();
  ASSERT_GE(actual, allocation_words);
}

TEST_VM(G1EvacStats, compute_desired_plab_size_bounds) {
  G1EvacStatsTest::compute_desired_plab_size_bounds(1, 0.0);
  G1EvacStatsTest::compute_desired_plab_size_bounds(1, 50.0);
  G1EvacStatsTest::compute_desired_plab_size_bounds(1, 99.0);
  G1EvacStatsTest::compute_desired_plab_size_bounds(1, 99.999);

  G1EvacStatsTest::compute_desired_plab_size_bounds(10, 0.0);
  G1EvacStatsTest::compute_desired_plab_size_bounds(10, 50.0);
  G1EvacStatsTest::compute_desired_plab_size_bounds(10, 99.0);
  G1EvacStatsTest::compute_desired_plab_size_bounds(10, 99.999);

  G1EvacStatsTest::compute_desired_plab_size_bounds(100, 0.0);
  G1EvacStatsTest::compute_desired_plab_size_bounds(100, 50.0);
  G1EvacStatsTest::compute_desired_plab_size_bounds(100, 99.0);
  G1EvacStatsTest::compute_desired_plab_size_bounds(100, 99.999);
}


TEST_VM(G1EvacStats, compute_desired_plab_size_zero_used) {
  G1EvacStatsTest::compute_desired_plab_size_zero_used(1, 0.0);
  G1EvacStatsTest::compute_desired_plab_size_zero_used(10, 50.0);
  G1EvacStatsTest::compute_desired_plab_size_zero_used(100, 99.999);
}

TEST_VM(G1EvacStats, compute_desired_plab_size_larger_waste) {
  G1EvacStatsTest::compute_desired_plab_size_larger_waste(1, 0.0);
  G1EvacStatsTest::compute_desired_plab_size_larger_waste(10, 50.0);
  G1EvacStatsTest::compute_desired_plab_size_larger_waste(100, 99.999);
}

TEST_VM(G1EvacStats, compute_desired_plab_size_check_overflow) {
  G1EvacStatsTest::compute_desired_plab_size_check_overflow(SIZE_MAX / 100 - 1);
  G1EvacStatsTest::compute_desired_plab_size_check_overflow(SIZE_MAX / 100);
  G1EvacStatsTest::compute_desired_plab_size_check_overflow(SIZE_MAX / 100 + 1);
}
