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
  static constexpr size_t InitialPopulationSize = MinimumPopulationSize * 1000;

  size_t _cohorts_count = ShenandoahAgeCensus::MAX_COHORTS;
  double _mortality_rates[ShenandoahAgeCensus::MAX_COHORTS];
  size_t _cohort_populations[ShenandoahAgeCensus::MAX_COHORTS];

  ShenandoahAgeCensusTest()
  : _mortality_rates{0.9, 0.7, 0.5, 0.3, 0.09, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0}
  {
    build_cohort_populations(_mortality_rates, _cohort_populations, _cohorts_count);
  }

  static void add_population(ShenandoahAgeCensus& census, const uint age, const size_t population_words) {
    CENSUS_NOISE(census.add(age, 0, 0, population_words, 0));
    NO_CENSUS_NOISE(census.add(age, 0, population_words, 0));
  }

  void update(ShenandoahAgeCensus& census, size_t cohorts) const {
    for (uint i = 1; i < cohorts; i++) {
      add_population(census, i, _cohort_populations[i]);
    }
    census.update_census(_cohort_populations[0]);
  }

  void update(ShenandoahAgeCensus& census) const {
    update(census, _cohorts_count);
  }

  size_t get_total_population_older_than(const size_t min_cohort_age) const {
    size_t total = 0;
    for (size_t i = 0; i < _cohorts_count; i++) {
      if (i >= min_cohort_age) {
        total += _cohort_populations[i];
      }
    }
    return total;
  }

  void promote_all_tenurable(const size_t tenuring_threshold) {
    for (size_t i = 0; i < _cohorts_count; i++) {
      if (i > tenuring_threshold) {
        _cohort_populations[i] = 0;
      }
    }
  }

  static void build_cohort_populations(const double mortality_rates[], size_t cohort_populations[], const size_t cohorts) {
    cohort_populations[0] = InitialPopulationSize;
    for (size_t i = 1; i < cohorts; i++) {
      cohort_populations[i] = cohort_populations[i - 1] * (1.0 - mortality_rates[i - 1]);
    }
  }
};

TEST_F(ShenandoahAgeCensusTest, initialize) {
  const ShenandoahAgeCensus census(1);
  EXPECT_EQ(census.tenuring_threshold(), ShenandoahAgeCensus::MAX_COHORTS);
}

TEST_F(ShenandoahAgeCensusTest, ignore_small_populations) {
  // Small populations are ignored so we do not return early before reaching the youngest cohort.
  ShenandoahAgeCensus census(1);
  add_population(census,1, 32);
  add_population(census,1, 32);
  census.update_census(64);
  EXPECT_EQ(1u, census.tenuring_threshold());
}

TEST_F(ShenandoahAgeCensusTest, find_high_mortality_rate) {
  ShenandoahAgeCensus census(1);

  // Initial threshold, no data
  EXPECT_EQ(16u, census.tenuring_threshold());

  // Provide population data for 1st cohort. Previous epoch has no population data so our
  // algorithm skips over all cohorts, leaving tenuring threshold at 1.
  update(census, 1);
  EXPECT_EQ(1u, census.tenuring_threshold());

  // Mortality rate of 1st cohort at age 1 is 0.9, we don't want to promote here. Move threshold to 2.
  update(census, 2);
  EXPECT_EQ(2u, census.tenuring_threshold());

  // Mortality rate of 1st cohort at age 2 is 0.7, we don't want to promote here. Move threshold to 3.
  update(census, 3);
  EXPECT_EQ(3u, census.tenuring_threshold());

  // Mortality rate of 1st cohort at age 3 is 0.5, we don't want to promote here. Move threshold to 4.
  update(census, 4);
  EXPECT_EQ(4u, census.tenuring_threshold());

  // Mortality rate of 1st cohort at age 4 is 0.3, we don't want to promote here. Move threshold to 5.
  update(census, 5);
  EXPECT_EQ(5u, census.tenuring_threshold());

  // Mortality rate of 1st cohort at age 5 is 0.09, this is less than the mortality rate threshold. It
  // is okay to tenure objects older than 5 now. Keep threshold at 5.
  update(census, 6);
  EXPECT_EQ(5u, census.tenuring_threshold());

  // Mortality rate at this age is 0. Keep tenuring threshold at 5.
  update(census, 7);
  EXPECT_EQ(5u, census.tenuring_threshold());
}

TEST_F(ShenandoahAgeCensusTest, ignore_mortality_caused_by_promotions) {
  ShenandoahAgeCensus census(1);

  // Simulate a sequence of censuses with the same mortality rate. Each one will see a
  // mortality rate above the tenuring threshold and raise the tenuring threshold by one.
  update(census, 1);
  update(census, 2);
  update(census, 3);
  update(census, 4);
  update(census, 5);

  EXPECT_EQ(5u, census.tenuring_threshold());

  // Simulate the effect of promoting all objects above the tenuring threshold
  // out of the young generation. This will look like a very high (100%) mortality
  // rate for these cohorts. However, we do _not_ want to raise the threshold in
  // this case because these objects haven't really "died", they have just been
  // tenured.
  promote_all_tenurable(census.tenuring_threshold());
  update(census);

  // We want this to stay at 5 - the mortality in 1st cohort at age 6 was caused by expected promotions.
  EXPECT_EQ(5u, census.tenuring_threshold());
}
