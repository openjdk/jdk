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

#include "unittest.hpp"
#include "gc/shenandoah/heuristics/shenandoahGlobalHeuristics.hpp"

// Region size of 256K is the minimum Shenandoah region size.
static const size_t REGION_SIZE = 256 * 1024;

// Waste factors matching the default JVM flag values.
static const double OLD_EVAC_WASTE   = ShenandoahOldEvacWaste;
static const double PROMO_EVAC_WASTE = ShenandoahPromoEvacWaste;
static const double YOUNG_EVAC_WASTE = ShenandoahEvacWaste;

// Default thresholds as percentages of region size.
static const size_t GARBAGE_THRESHOLD = REGION_SIZE * 25 / 100;
static const size_t IGNORE_THRESHOLD  = REGION_SIZE * 5 / 100;

static ShenandoahGlobalCSetBudget make_budget(size_t shared_reserves,
                                              size_t min_garbage = 0,
                                              size_t young_evac_reserve = 0,
                                              size_t old_evac_reserve = 0,
                                              size_t promo_reserve = 0) {
  return ShenandoahGlobalCSetBudget(REGION_SIZE, shared_reserves,
                                    GARBAGE_THRESHOLD, IGNORE_THRESHOLD, min_garbage,
                                    young_evac_reserve, YOUNG_EVAC_WASTE,
                                    old_evac_reserve, OLD_EVAC_WASTE,
                                    promo_reserve, PROMO_EVAC_WASTE);
}

// ---- Threshold tests ----

// A region whose garbage is 1 byte below the 25% garbage threshold should be
// skipped, even when there is plenty of shared reserve available.
TEST(ShenandoahGlobalCSet, skip_below_garbage_threshold) {
  auto budget = make_budget(10 * REGION_SIZE);
  ShenandoahGlobalRegionAttributes region = {
    GARBAGE_THRESHOLD - 1, REGION_SIZE - (GARBAGE_THRESHOLD - 1), 0, false, false
  };
  EXPECT_EQ(budget.try_add_region(region), ShenandoahGlobalRegionDisposition::SKIP);
}

// When the cumulative garbage collected so far is below min_garbage, regions
// with garbage above the ignore threshold (5%) but below the garbage threshold
// (25%) are added anyway ("add_regardless" path). This young non-tenurable
// region should be accepted as a young evacuation.
TEST(ShenandoahGlobalCSet, add_regardless_when_below_min_garbage) {
  auto budget = make_budget(10 * REGION_SIZE, /*min_garbage=*/REGION_SIZE);
  ShenandoahGlobalRegionAttributes region = {
    IGNORE_THRESHOLD + 1, 1000, 0, false, false
  };
  EXPECT_EQ(budget.try_add_region(region), ShenandoahGlobalRegionDisposition::ADD_YOUNG_EVAC);
}

// A region whose garbage is exactly at the ignore threshold (not above it)
// should be skipped even when min_garbage has not been met, because the
// add_regardless condition requires garbage strictly above the ignore threshold.
TEST(ShenandoahGlobalCSet, skip_at_ignore_threshold) {
  auto budget = make_budget(10 * REGION_SIZE, /*min_garbage=*/REGION_SIZE);
  ShenandoahGlobalRegionAttributes region = {
    IGNORE_THRESHOLD, 1000, 0, false, false
  };
  EXPECT_EQ(budget.try_add_region(region), ShenandoahGlobalRegionDisposition::SKIP);
}

// ---- Old region evacuation ----

// An old region at the garbage threshold with 50K live and 10K free, backed by
// ample shared reserves. Should be accepted as old evacuation. The old_evac
// budget consumes 50K * 1.4 = 70K for evacuation, and the promo budget absorbs
// the 10K of free bytes that are lost when this region enters the collection set.
TEST(ShenandoahGlobalCSet, old_region_accepted) {
  auto budget = make_budget(5 * REGION_SIZE);
  ShenandoahGlobalRegionAttributes region = {
    GARBAGE_THRESHOLD, 50000, 10000, true, false
  };

  EXPECT_EQ(budget.try_add_region(region), ShenandoahGlobalRegionDisposition::ADD_OLD_EVAC);
  EXPECT_EQ(budget.old_evac.region_count(), (size_t)1);
  EXPECT_EQ(budget.old_evac.live_bytes(), (size_t)50000);
  EXPECT_EQ(budget.old_evac.consumed(), (size_t)(50000 * OLD_EVAC_WASTE));
  EXPECT_EQ(budget.promo.consumed(), (size_t)10000);
  EXPECT_EQ(budget.cur_garbage(), GARBAGE_THRESHOLD);
}

// An old region with zero shared reserves and no per-category reserves. Both
// the old_evac and promo reservations will fail, so the region is skipped.
// Verify that all budget state remains at zero (clean rollback).
TEST(ShenandoahGlobalCSet, old_region_rejected_no_budget) {
  auto budget = make_budget(0);
  ShenandoahGlobalRegionAttributes region = {
    GARBAGE_THRESHOLD, 50000, 10000, true, false
  };

  EXPECT_EQ(budget.try_add_region(region), ShenandoahGlobalRegionDisposition::SKIP);
  EXPECT_EQ(budget.old_evac.region_count(), (size_t)0);
  EXPECT_EQ(budget.committed_from_shared(), (size_t)0);
  EXPECT_EQ(budget.old_evac.consumed(), (size_t)0);
  EXPECT_EQ(budget.promo.consumed(), (size_t)0);
}

// An old region with no per-category reserves but sufficient shared reserves.
// The old_evac budget must expand from the shared pool to accommodate the
// evacuation. Since old_evac starts at zero, all of its reserve comes from
// shared, so committed_from_shared should equal old_evac.reserve().
TEST(ShenandoahGlobalCSet, old_region_expands_shared) {
  auto budget = make_budget(5 * REGION_SIZE);
  ShenandoahGlobalRegionAttributes region = {
    GARBAGE_THRESHOLD, 50000, 0, true, false
  };

  EXPECT_EQ(budget.try_add_region(region), ShenandoahGlobalRegionDisposition::ADD_OLD_EVAC);
  EXPECT_GT(budget.committed_from_shared(), (size_t)0);
  EXPECT_EQ(budget.committed_from_shared(), budget.old_evac.reserve());
  EXPECT_GE(budget.old_evac.reserve(), budget.old_evac.consumed());
}

// An old region with enough old_evac reserve for the live data but zero promo
// reserve, and a full region's worth of free bytes. The free bytes represent
// promotion capacity that is lost when this region enters the cset, so the
// promo budget must expand from shared reserves to absorb that loss.
TEST(ShenandoahGlobalCSet, old_region_free_bytes_consume_promo_reserve) {
  size_t live_data = 10000;
  size_t evac_needed = (size_t)(live_data * OLD_EVAC_WASTE);
  auto budget = make_budget(5 * REGION_SIZE, 0, 0, evac_needed, 0);
  ShenandoahGlobalRegionAttributes region = {
    GARBAGE_THRESHOLD, live_data, REGION_SIZE, true, false
  };

  EXPECT_EQ(budget.try_add_region(region), ShenandoahGlobalRegionDisposition::ADD_OLD_EVAC);
  EXPECT_GT(budget.committed_from_shared(), (size_t)0);
  EXPECT_GE(budget.promo.reserve(), (size_t)REGION_SIZE);
  EXPECT_EQ(budget.promo.consumed(), (size_t)REGION_SIZE);
}

// ---- Promotion ----

// A young tenurable region at the garbage threshold with 40K live data and
// ample shared reserves. Should be accepted as a promotion. The promo budget
// consumes 40K * 1.2 = 48K.
TEST(ShenandoahGlobalCSet, tenurable_region_promoted) {
  auto budget = make_budget(5 * REGION_SIZE);
  ShenandoahGlobalRegionAttributes region = {
    GARBAGE_THRESHOLD, 40000, 0, false, true
  };

  EXPECT_EQ(budget.try_add_region(region), ShenandoahGlobalRegionDisposition::ADD_PROMO);
  EXPECT_EQ(budget.promo.region_count(), (size_t)1);
  EXPECT_EQ(budget.promo.live_bytes(), (size_t)40000);
  EXPECT_EQ(budget.promo.consumed(), (size_t)(40000 * PROMO_EVAC_WASTE));
}

// A young tenurable region with zero reserves. The promo reservation fails,
// so the region is skipped.
TEST(ShenandoahGlobalCSet, tenurable_region_rejected) {
  auto budget = make_budget(0);
  ShenandoahGlobalRegionAttributes region = {
    GARBAGE_THRESHOLD, 40000, 0, false, true
  };
  EXPECT_EQ(budget.try_add_region(region), ShenandoahGlobalRegionDisposition::SKIP);
}

// ---- Young evacuation ----

// A young non-tenurable region at the garbage threshold with 30K live data.
// Should be accepted as a young evacuation. The young_evac budget consumes
// 30K * 1.2 = 36K.
TEST(ShenandoahGlobalCSet, young_region_evacuated) {
  auto budget = make_budget(5 * REGION_SIZE);
  ShenandoahGlobalRegionAttributes region = {
    GARBAGE_THRESHOLD, 30000, 0, false, false
  };

  EXPECT_EQ(budget.try_add_region(region), ShenandoahGlobalRegionDisposition::ADD_YOUNG_EVAC);
  EXPECT_EQ(budget.young_evac.region_count(), (size_t)1);
  EXPECT_EQ(budget.young_evac.consumed(), (size_t)(30000 * YOUNG_EVAC_WASTE));
}

// A young non-tenurable region with zero reserves. The young_evac reservation
// fails, so the region is skipped.
TEST(ShenandoahGlobalCSet, young_region_rejected) {
  auto budget = make_budget(0);
  ShenandoahGlobalRegionAttributes region = {
    GARBAGE_THRESHOLD, 30000, 0, false, false
  };
  EXPECT_EQ(budget.try_add_region(region), ShenandoahGlobalRegionDisposition::SKIP);
}

// ---- Multi-region and budget interaction tests ----

// Evaluate two identical young regions against a budget with only 1 region's
// worth of shared reserves. Each region has 150K live data, so anticipated
// consumption is 150K * 1.2 = 180K per region. The first region expands the
// young_evac reserve by one region (256K) from shared, consuming 180K of it.
// The second region needs another 180K, but only 76K remains in the reserve
// and the shared pool is exhausted, so it must be skipped.
TEST(ShenandoahGlobalCSet, shared_exhausted_across_regions) {
  auto budget = make_budget(1 * REGION_SIZE);
  ShenandoahGlobalRegionAttributes region = {
    GARBAGE_THRESHOLD, 150000, 0, false, false
  };

  EXPECT_EQ(budget.try_add_region(region), ShenandoahGlobalRegionDisposition::ADD_YOUNG_EVAC);
  EXPECT_EQ(budget.try_add_region(region), ShenandoahGlobalRegionDisposition::SKIP);
  EXPECT_EQ(budget.young_evac.region_count(), (size_t)1);
}

// An old region where the old_evac reserve is pre-sized to exactly cover the
// evacuation need, and the promo reserve covers the free bytes loss. No shared
// reserves should be drawn because the per-category reserves are sufficient.
TEST(ShenandoahGlobalCSet, existing_reserve_used_before_shared) {
  size_t live_data = 50000;
  size_t evac_needed = (size_t)(live_data * OLD_EVAC_WASTE);
  auto budget = make_budget(5 * REGION_SIZE, 0, 0, evac_needed, REGION_SIZE);
  ShenandoahGlobalRegionAttributes region = {
    GARBAGE_THRESHOLD, live_data, 1000, true, false
  };

  EXPECT_EQ(budget.try_add_region(region), ShenandoahGlobalRegionDisposition::ADD_OLD_EVAC);
  EXPECT_EQ(budget.committed_from_shared(), (size_t)0);
}

// Evaluate two identical young regions and verify that cur_garbage accumulates
// the garbage from each accepted region.
TEST(ShenandoahGlobalCSet, garbage_accumulates) {
  auto budget = make_budget(10 * REGION_SIZE);
  ShenandoahGlobalRegionAttributes region = {
    GARBAGE_THRESHOLD, 10000, 0, false, false
  };

  budget.try_add_region(region);
  EXPECT_EQ(budget.cur_garbage(), GARBAGE_THRESHOLD);
  budget.try_add_region(region);
  EXPECT_EQ(budget.cur_garbage(), 2 * GARBAGE_THRESHOLD);
}

// When no regions are evaluated, finish() should donate the entire shared
// reserve pool to the promo budget.
TEST(ShenandoahGlobalCSet, finish_donates_remaining_to_promo) {
  auto budget = make_budget(5 * REGION_SIZE);
  size_t promo_before = budget.promo.reserve();
  budget.finish();
  EXPECT_EQ(budget.promo.reserve(), promo_before + 5 * REGION_SIZE);
}

// After evaluating one region that draws from the shared pool, finish() should
// donate only the remaining (unconsumed) shared reserves to promo.
TEST(ShenandoahGlobalCSet, finish_donates_remainder_after_use) {
  auto budget = make_budget(5 * REGION_SIZE);
  ShenandoahGlobalRegionAttributes region = {
    GARBAGE_THRESHOLD, 10000, 0, false, false
  };
  budget.try_add_region(region);
  size_t shared_used = budget.committed_from_shared();
  size_t promo_before = budget.promo.reserve();
  budget.finish();
  EXPECT_EQ(budget.promo.reserve(), promo_before + (5 * REGION_SIZE - shared_used));
}

// ---- ShenandoahEvacuationBudget unit tests ----

// A reservation that fits entirely within the existing reserve should succeed
// without drawing from the shared pool.
TEST(ShenandoahEvacuationBudget, try_reserve_fits_without_expansion) {
  ShenandoahSharedEvacReserve shared(5 * REGION_SIZE);
  ShenandoahEvacuationBudget evac_budget(REGION_SIZE, 1.0, REGION_SIZE, &shared);
  EXPECT_TRUE(evac_budget.try_reserve(REGION_SIZE));
  EXPECT_EQ(shared.committed, (size_t)0);
  EXPECT_EQ(evac_budget.reserve(), (size_t)REGION_SIZE);
}

// A reservation of REGION_SIZE+1 bytes starting from a zero reserve requires
// two region-sized expansions from the shared pool (one region isn't enough).
TEST(ShenandoahEvacuationBudget, try_reserve_expands_from_shared) {
  ShenandoahSharedEvacReserve shared(5 * REGION_SIZE);
  ShenandoahEvacuationBudget evac_budget(0, 1.0, REGION_SIZE, &shared);
  EXPECT_TRUE(evac_budget.try_reserve(REGION_SIZE + 1));
  EXPECT_EQ(shared.committed, 2 * REGION_SIZE);
  EXPECT_EQ(evac_budget.reserve(), 2 * REGION_SIZE);
}

// A reservation with an empty shared pool should fail, leaving both the
// budget's reserve and the shared pool's committed count unchanged.
TEST(ShenandoahEvacuationBudget, try_reserve_fails_no_shared) {
  ShenandoahSharedEvacReserve shared(0);
  ShenandoahEvacuationBudget evac_budget(0, 1.0, REGION_SIZE, &shared);
  EXPECT_FALSE(evac_budget.try_reserve(REGION_SIZE));
  EXPECT_EQ(shared.committed, (size_t)0);
  EXPECT_EQ(evac_budget.reserve(), (size_t)0);
}

// A reservation of 3 regions against a shared pool of only 2 regions should
// fail. On failure, neither the budget's reserve nor the shared pool's
// committed count should be modified.
TEST(ShenandoahEvacuationBudget, try_reserve_fails_insufficient_shared) {
  ShenandoahSharedEvacReserve shared(2 * REGION_SIZE);
  ShenandoahEvacuationBudget evac_budget(0, 1.0, REGION_SIZE, &shared);
  EXPECT_FALSE(evac_budget.try_reserve(3 * REGION_SIZE));
  EXPECT_EQ(shared.committed, (size_t)0);
  EXPECT_EQ(evac_budget.reserve(), (size_t)0);
}

// Committing a consumption of 1200 bytes with 1000 live bytes should update
// all three tracking fields: consumed, live_bytes, and region_count.
TEST(ShenandoahEvacuationBudget, commit_updates_fields) {
  ShenandoahSharedEvacReserve shared(5 * REGION_SIZE);
  ShenandoahEvacuationBudget evac_budget(REGION_SIZE, 1.2, REGION_SIZE, &shared);
  evac_budget.commit(1200, 1000);
  EXPECT_EQ(evac_budget.consumed(), (size_t)1200);
  EXPECT_EQ(evac_budget.live_bytes(), (size_t)1000);
  EXPECT_EQ(evac_budget.region_count(), (size_t)1);
}
