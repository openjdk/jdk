/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#include "gc/shenandoah/heuristics/shenandoahGlobalHeuristics.hpp"
#include "gc/shenandoah/shenandoahAsserts.hpp"
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahGenerationalHeap.inline.hpp"
#include "gc/shenandoah/shenandoahGlobalGeneration.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"
#include "utilities/quickSort.hpp"

bool ShenandoahEvacuationBudget::try_reserve(size_t bytes) {
  size_t new_consumption = _consumed + bytes;
  if (new_consumption <= _reserve) {
    return true;
  }
  // Try expanding from shared pool
  size_t new_reserve = _reserve;
  size_t new_committed = _shared->committed;
  while ((new_consumption > new_reserve) && (new_committed < _shared->limit)) {
    new_committed += _region_size_bytes;
    new_reserve += _region_size_bytes;
  }
  if (new_consumption <= new_reserve) {
    _reserve = new_reserve;
    _shared->committed = new_committed;
    return true;
  }
  return false;
}

void ShenandoahEvacuationBudget::commit(size_t consumption, size_t live) {
  _consumed += consumption;
  _live_bytes += live;
  _region_count++;
}

ShenandoahGlobalRegionDisposition ShenandoahGlobalCSetBudget::try_add_region(
    const ShenandoahGlobalRegionAttributes& region) {

  size_t region_garbage = region.garbage;
  size_t new_garbage = _cur_garbage + region_garbage;
  bool add_regardless = (region_garbage > _ignore_threshold) && (new_garbage < _min_garbage);

  if (!add_regardless && (region_garbage < _garbage_threshold)) {
    return ShenandoahGlobalRegionDisposition::SKIP;
  }

  size_t live_bytes = region.live_data_bytes;

  if (region.is_old) {
    size_t evac_need = old_evac.anticipated_consumption(live_bytes);
    size_t promo_loss = region.free_bytes;

    // Snapshot state for rollback — old branch does two reservations
    size_t saved_committed = _shared.committed;
    size_t saved_old_reserve = old_evac.reserve();
    size_t saved_promo_reserve = promo.reserve();

    if (old_evac.try_reserve(evac_need) && promo.try_reserve(promo_loss)) {
      old_evac.commit(evac_need, live_bytes);
      promo.commit_raw(promo_loss);
      _cur_garbage = new_garbage;
      return ShenandoahGlobalRegionDisposition::ADD_OLD_EVAC;
    }
    _shared.committed = saved_committed;
    old_evac.set_reserve(saved_old_reserve);
    promo.set_reserve(saved_promo_reserve);
    return ShenandoahGlobalRegionDisposition::SKIP;
  } else if (region.is_tenurable) {
    size_t promo_need = promo.anticipated_consumption(live_bytes);
    if (promo.try_reserve(promo_need)) {
      promo.commit(promo_need, live_bytes);
      _cur_garbage = new_garbage;
      return ShenandoahGlobalRegionDisposition::ADD_PROMO;
    }
    return ShenandoahGlobalRegionDisposition::SKIP;
  } else {
    size_t evac_need = young_evac.anticipated_consumption(live_bytes);
    if (young_evac.try_reserve(evac_need)) {
      young_evac.commit(evac_need, live_bytes);
      _cur_garbage = new_garbage;
      return ShenandoahGlobalRegionDisposition::ADD_YOUNG_EVAC;
    }
    return ShenandoahGlobalRegionDisposition::SKIP;
  }
}

ShenandoahGlobalHeuristics::ShenandoahGlobalHeuristics(ShenandoahGlobalGeneration* generation)
        : ShenandoahGenerationalHeuristics(generation) {
}

void ShenandoahGlobalHeuristics::select_collection_set_regions(ShenandoahCollectionSet* cset,
                                                               RegionData* data, size_t size,
                                                               size_t actual_free) {
  QuickSort::sort<RegionData>(data, size, compare_by_garbage);
  choose_global_collection_set(cset, data, size, actual_free, 0);
}

void ShenandoahGlobalHeuristics::choose_global_collection_set(ShenandoahCollectionSet* cset,
                                                              const ShenandoahHeuristics::RegionData* data,
                                                              size_t size, size_t actual_free,
                                                              size_t cur_young_garbage) const {
  shenandoah_assert_heaplocked_or_safepoint();
  auto heap = ShenandoahGenerationalHeap::heap();
  auto free_set = heap->free_set();
  size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();
  size_t capacity = heap->soft_max_capacity();

  size_t garbage_threshold = region_size_bytes * ShenandoahGarbageThreshold / 100;
  size_t ignore_threshold = region_size_bytes * ShenandoahIgnoreGarbageThreshold / 100;

  size_t young_evac_reserve = heap->young_generation()->get_evacuation_reserve();
  size_t original_young_evac_reserve = young_evac_reserve;
  size_t old_evac_reserve = heap->old_generation()->get_evacuation_reserve();
  size_t old_promo_reserve = heap->old_generation()->get_promoted_reserve();

  size_t unaffiliated_young_regions = free_set->collector_unaffiliated_regions();
  size_t unaffiliated_young_memory = unaffiliated_young_regions * region_size_bytes;
  size_t unaffiliated_old_regions = free_set->old_collector_unaffiliated_regions();
  size_t unaffiliated_old_memory = unaffiliated_old_regions * region_size_bytes;

  // Figure out how many unaffiliated regions are dedicated to Collector and OldCollector reserves.  Let these
  // be shuffled between young and old generations in order to expedite evacuation of whichever regions have the
  // most garbage, regardless of whether these garbage-first regions reside in young or old generation.
  // Excess reserves will be transferred back to the mutator after collection set has been chosen.  At the end
  // of evacuation, any reserves not consumed by evacuation will also be transferred to the mutator free set.

  // Truncate reserves to only target unaffiliated memory
  size_t shared_reserve_regions = 0;
  if (young_evac_reserve > unaffiliated_young_memory) {
    shared_reserve_regions += unaffiliated_young_regions;
  } else {
    shared_reserve_regions += young_evac_reserve / region_size_bytes;
  }
  young_evac_reserve = 0;
  size_t total_old_reserve = old_evac_reserve + old_promo_reserve;
  if (total_old_reserve > unaffiliated_old_memory) {
    // Give all the unaffiliated memory to the shared reserves.  Leave the rest for promo reserve.
    shared_reserve_regions += unaffiliated_old_regions;
    old_promo_reserve = total_old_reserve - unaffiliated_old_memory;
  } else {
    shared_reserve_regions += old_evac_reserve / region_size_bytes;
  }
  old_evac_reserve = 0;
  assert(shared_reserve_regions <=
         (heap->young_generation()->free_unaffiliated_regions() + heap->old_generation()->free_unaffiliated_regions()),
         "Shared reserve regions (%zu) should not exceed total unaffiliated regions (young: %zu, old: %zu)",
         shared_reserve_regions,
         heap->young_generation()->free_unaffiliated_regions(),
         heap->old_generation()->free_unaffiliated_regions());

  // Of the memory reclaimed by GC, some of this will need to be reserved for the next GC collection.  Use the current
  // young reserve as an approximation of the future Collector reserve requirement.  Try to end with at least
  // (capacity * ShenandoahMinFreeThreshold) / 100 bytes available to the mutator.
  size_t free_target = (capacity * ShenandoahMinFreeThreshold) / 100 + original_young_evac_reserve;
  size_t min_garbage = (free_target > actual_free) ? (free_target - actual_free) : 0;

  ShenandoahGlobalCSetBudget budget(region_size_bytes,
                                    shared_reserve_regions * region_size_bytes,
                                    garbage_threshold, ignore_threshold, min_garbage,
                                    young_evac_reserve, ShenandoahEvacWaste,
                                    old_evac_reserve, ShenandoahOldEvacWaste,
                                    old_promo_reserve, ShenandoahPromoEvacWaste);
  budget.set_cur_garbage(cur_young_garbage);

  log_info(gc, ergo)("Adaptive CSet Selection for global cycle. Discretionary evacuation budget (for either old or young): " PROPERFMT ", Actual Free: " PROPERFMT,
                     PROPERFMTARGS(budget.shared_reserves()), PROPERFMTARGS(actual_free));

  for (size_t idx = 0; idx < size; idx++) {
    ShenandoahHeapRegion* r = data[idx].get_region();
    if (cset->is_in(r) || r->get_top_before_promote() != nullptr) {
      assert(heap->is_tenurable(r), "Region %zu already selected for promotion must be tenurable", idx);
      continue;
    }

    ShenandoahGlobalRegionAttributes attrs;
    attrs.garbage = r->garbage();
    attrs.live_data_bytes = r->get_live_data_bytes();
    attrs.free_bytes = r->free();
    attrs.is_old = r->is_old();
    attrs.is_tenurable = !r->is_old() && heap->is_tenurable(r);

    if (budget.try_add_region(attrs) != ShenandoahGlobalRegionDisposition::SKIP) {
      cset->add_region(r);
    }
  }

  budget.finish();

  DEBUG_ONLY(budget.assert_budget_constraints_hold(
      heap->young_generation()->get_evacuation_reserve() +
      heap->old_generation()->get_evacuation_reserve() +
      heap->old_generation()->get_promoted_reserve()));

  if (heap->young_generation()->get_evacuation_reserve() < budget.young_evac.reserve()) {
    size_t delta_bytes = budget.young_evac.reserve() - heap->young_generation()->get_evacuation_reserve();
    size_t delta_regions = delta_bytes / region_size_bytes;
    size_t regions_to_transfer = MIN2(unaffiliated_old_regions, delta_regions);
    log_info(gc)("Global GC moves %zu unaffiliated regions from old collector to young collector reserves", regions_to_transfer);
    ssize_t negated_regions = -regions_to_transfer;
    heap->free_set()->move_unaffiliated_regions_from_collector_to_old_collector(negated_regions);
  } else if (heap->young_generation()->get_evacuation_reserve() > budget.young_evac.reserve()) {
    size_t delta_bytes = heap->young_generation()->get_evacuation_reserve() - budget.young_evac.reserve();
    size_t delta_regions = delta_bytes / region_size_bytes;
    size_t regions_to_transfer = MIN2(unaffiliated_young_regions, delta_regions);
    log_info(gc)("Global GC moves %zu unaffiliated regions from young collector to old collector reserves", regions_to_transfer);
    heap->free_set()->move_unaffiliated_regions_from_collector_to_old_collector(regions_to_transfer);
  }

  heap->young_generation()->set_evacuation_reserve(budget.young_evac.reserve());
  heap->old_generation()->set_evacuation_reserve(budget.old_evac.reserve());
  heap->old_generation()->set_promoted_reserve(budget.promo.reserve());
}

#ifdef ASSERT
void ShenandoahGlobalCSetBudget::assert_budget_constraints_hold(size_t original_total_reserves) const {
  // Consider the effects of round-off:
  //  1. We know that the sum over each evacuation multiplied by Evacuation Waste is <= total evacuation reserve
  //  2. However, the reserve for each individual evacuation may be rounded down. In the worst case, we will be
  //     over budget by the number of regions evacuated, since each region's reserve might be under-estimated by
  //     at most 1.
  //  3. Likewise, if we take the sum of bytes evacuated and multiply this by the Evacuation Waste and then round
  //     down to nearest integer, the calculated reserve will underestimate the true reserve needs by at most 1.
  //  4. This explains the adjustments to subtotals in the assert statements below.
  assert(young_evac.live_bytes() * young_evac.waste_factor() <=
         young_evac.reserve() + young_evac.region_count(),
         "Young evac consumption (%zu) exceeds reserve (%zu) + region count (%zu)",
         (size_t)(young_evac.live_bytes() * young_evac.waste_factor()),
         young_evac.reserve(), young_evac.region_count());
  assert(old_evac.live_bytes() * old_evac.waste_factor() <=
         old_evac.reserve() + old_evac.region_count(),
         "Old evac consumption (%zu) exceeds reserve (%zu) + region count (%zu)",
         (size_t)(old_evac.live_bytes() * old_evac.waste_factor()),
         old_evac.reserve(), old_evac.region_count());
  assert(promo.live_bytes() * promo.waste_factor() <=
         promo.reserve() + promo.region_count(),
         "Promo consumption (%zu) exceeds reserve (%zu) + region count (%zu)",
         (size_t)(promo.live_bytes() * promo.waste_factor()),
         promo.reserve(), promo.region_count());

  size_t total_post_reserves = young_evac.reserve() + old_evac.reserve() + promo.reserve();
  assert(total_post_reserves <= original_total_reserves,
         "Total post-cset reserves (%zu + %zu + %zu = %zu) exceed original reserves (%zu)",
         young_evac.reserve(), old_evac.reserve(), promo.reserve(),
         total_post_reserves, original_total_reserves);
}
#endif
