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
 *
 */

#include "gc/shenandoah/shenandoahAgeCensus.hpp"
#include "unittest.hpp"

class ShenandoahAgeCensusTest : public ::testing::Test {
protected:
  static constexpr size_t MinimumPopulationSize = 4*K;
  static void build_mortality_rate_curve(ShenandoahAgeCensus& census, const double mortality_rates[], const size_t cohorts) {
    constexpr size_t current_population = MinimumPopulationSize * 10;

    // Simulate the census for the first epoch with populations that will produce the
    // expected mortality rate when presented with the populations for the subsequent epoch
    for (size_t i = 1; i <= cohorts; i++) {
      const size_t previous_population = current_population / (1.0 - mortality_rates[i]);
      census.add(i, 0, 0, previous_population, 0);
    }

    const size_t previous_population = current_population / (1.0 - mortality_rates[0]);
    census.update_census(previous_population);

    for (size_t i = 1; i <= cohorts; i++) {
      census.add(i, 0, 0, current_population, 0);
    }
    census.update_census(current_population);
  }
};

TEST_F(ShenandoahAgeCensusTest, initialize) {
  ShenandoahAgeCensus census(4);
  EXPECT_EQ(census.tenuring_threshold(), ShenandoahAgeCensus::MAX_COHORTS);
}

TEST_F(ShenandoahAgeCensusTest, ignore_small_populations) {
  // Small populations are ignored so we do not return early before reaching the youngest cohort.
  ShenandoahAgeCensus census(4);
  census.add(1, 0, 0, 32, 0);
  census.add(1, 0, 0, 32, 0);
  census.update_census(64);
  EXPECT_EQ(1u, census.tenuring_threshold());
}

TEST_VM_F(ShenandoahAgeCensusTest, find_high_mortality_rate) {
  ShenandoahAgeCensus census(4);
  constexpr double mortality_rates[] = {
    0.9, 0.8, 0.7, 0.6, 0.5, 0.4, 0.3, 0.2,
    0.1, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0
  };
  build_mortality_rate_curve(census, mortality_rates, sizeof(mortality_rates) / sizeof(double));
}