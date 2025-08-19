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
    constexpr size_t initial_population = MinimumPopulationSize * 1000;

    // Simulate the census for the first epoch with populations that will produce the
    // expected mortality rate when presented with the populations for the subsequent epoch
    size_t population = initial_population;
    for (size_t i = 0; i < cohorts; i++) {
      population = population * (1.0 - mortality_rates[i]);
      census.add(i + 1, 0, 0, population, 0);
    }

    census.update_census(initial_population);
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

TEST_F(ShenandoahAgeCensusTest, find_high_mortality_rate) {
  ShenandoahAgeCensus census(4);
  constexpr double mortality_rates[] = {
    0.9, 0.7, 0.5, 0.3, 0.1, 0.0, 0.0, 0.0,
    0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0
  };
  size_t mortality_rates_count = sizeof(mortality_rates) / sizeof(double);

  // Initial threshold, no data
  EXPECT_EQ(16u, census.tenuring_threshold());

  // No deaths in previous data, everybody seems to survive, set threshold to 1 (tenure everything).
  build_mortality_rate_curve(census, mortality_rates, mortality_rates_count);
  EXPECT_EQ(1u, census.tenuring_threshold());

  // mr = 0.7 from 1 -> 2, above mr threshold of 0.1
  build_mortality_rate_curve(census, mortality_rates, mortality_rates_count);
  EXPECT_EQ(2u, census.tenuring_threshold());

  // mr = 0.5 from 2 -> 3, above mr threshold of 0.1
  build_mortality_rate_curve(census, mortality_rates, mortality_rates_count);
  EXPECT_EQ(3u, census.tenuring_threshold());

  // mr = 0.3 from 3 -> 4, above mr threshold of 0.1
  build_mortality_rate_curve(census, mortality_rates, mortality_rates_count);
  EXPECT_EQ(4u, census.tenuring_threshold());

  // mr = 0.1 from 4 -> 5, not above mr threshold of 0.1, stay at 4
  build_mortality_rate_curve(census, mortality_rates, mortality_rates_count);
  EXPECT_EQ(4u, census.tenuring_threshold());
}
