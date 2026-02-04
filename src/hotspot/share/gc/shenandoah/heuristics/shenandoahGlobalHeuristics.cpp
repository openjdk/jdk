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

ShenandoahGlobalHeuristics::ShenandoahGlobalHeuristics(ShenandoahGlobalGeneration* generation)
        : ShenandoahGenerationalHeuristics(generation) {
}


void ShenandoahGlobalHeuristics::choose_collection_set_from_regiondata(ShenandoahCollectionSet* cset,
                                                                       RegionData* data, size_t size,
                                                                       size_t actual_free) {
  // Better select garbage-first regions
  QuickSort::sort<RegionData>(data, size, compare_by_garbage);

  choose_global_collection_set(cset, data, size, actual_free, 0 /* cur_young_garbage */);
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
    size_t delta_regions = young_evac_reserve / region_size_bytes;
    shared_reserve_regions += delta_regions;
  }
  young_evac_reserve = 0;
  size_t total_old_reserve = old_evac_reserve + old_promo_reserve;
  if (total_old_reserve > unaffiliated_old_memory) {
    // Give all the unaffiliated memory to the shared reserves.  Leave the rest for promo reserve.
    shared_reserve_regions += unaffiliated_old_regions;
    old_promo_reserve = total_old_reserve - unaffiliated_old_memory;
  } else {
    size_t delta_regions = old_evac_reserve / region_size_bytes;
    shared_reserve_regions += delta_regions;
  }
  old_evac_reserve = 0;
  assert(shared_reserve_regions <=
         (heap->young_generation()->free_unaffiliated_regions() + heap->old_generation()->free_unaffiliated_regions()),
         "simple math");

  size_t shared_reserves = shared_reserve_regions * region_size_bytes;
  size_t committed_from_shared_reserves = 0;

  size_t promo_bytes = 0;
  size_t old_evac_bytes = 0;
  size_t young_evac_bytes = 0;

  size_t consumed_by_promo = 0;        // promo_bytes * ShenandoahPromoEvacWaste
  size_t consumed_by_old_evac = 0;     // old_evac_bytes * ShenandoahOldEvacWaste
  size_t consumed_by_young_evac = 0;   // young_evac_bytes * ShenandoahEvacWaste

  // Of the memory reclaimed by GC, some of this will need to be reserved for the next GC collection.  Use the current
  // young reserve as an approximation of the future Collector reserve requirement.  Try to end with at least
  // (capacity * ShenandoahMinFreeThreshold) / 100 bytes available to the mutator.
  size_t free_target = (capacity * ShenandoahMinFreeThreshold) / 100 + original_young_evac_reserve;
  size_t min_garbage = (free_target > actual_free) ? (free_target - actual_free) : 0;

  size_t aged_regions_promoted = 0;
  size_t young_regions_evacuated = 0;
  size_t old_regions_evacuated = 0;

  log_info(gc, ergo)("Adaptive CSet Selection for GLOBAL. Discretionary evacuation budget (for either old or young): %zu%s"
                     ", Actual Free: %zu%s.",
                      byte_size_in_proper_unit(shared_reserves), proper_unit_for_byte_size(shared_reserves),
                      byte_size_in_proper_unit(actual_free), proper_unit_for_byte_size(actual_free));

  size_t cur_garbage = cur_young_garbage;
  for (size_t idx = 0; idx < size; idx++) {
    ShenandoahHeapRegion* r = data[idx].get_region();
    assert(!cset->is_preselected(r->index()), "There should be no preselected regions during GLOBAL GC");
    bool add_region = false;
    size_t region_garbage = r->garbage();
    size_t new_garbage = cur_garbage + region_garbage;
    bool add_regardless = (region_garbage > ignore_threshold) && (new_garbage < min_garbage);
    size_t live_bytes = r->get_live_data_bytes();
    if (add_regardless || (region_garbage >= garbage_threshold)) {
      if (r->is_old()) {
        size_t anticipated_consumption = (size_t) (live_bytes * ShenandoahOldEvacWaste);
        size_t new_old_consumption = consumed_by_old_evac + anticipated_consumption;
        size_t new_old_evac_reserve = old_evac_reserve;
        size_t proposed_old_region_expansion = 0;
        while ((new_old_consumption > new_old_evac_reserve) && (committed_from_shared_reserves < shared_reserves)) {
          committed_from_shared_reserves += region_size_bytes;
          proposed_old_region_expansion++;
          new_old_evac_reserve += region_size_bytes;
        }
        // If this region has free memory and we choose to place it in the collection set, its free memory is no longer
        // available to hold promotion results.  So we behave as if its free memory is consumed within the promotion reserve.
        size_t anticipated_loss_from_promo_reserve = r->free();
        size_t new_promo_consumption = consumed_by_promo + anticipated_loss_from_promo_reserve;
        size_t new_promo_reserve = old_promo_reserve;
        while ((new_promo_consumption > new_promo_reserve) && (committed_from_shared_reserves < shared_reserves)) {
          committed_from_shared_reserves += region_size_bytes;
          proposed_old_region_expansion++;
          new_promo_reserve += region_size_bytes;
        }
        if ((new_old_consumption <= new_old_evac_reserve) && (new_promo_consumption <= new_promo_reserve)) {
          add_region = true;
          old_evac_reserve = new_old_evac_reserve;
          old_promo_reserve = new_promo_reserve;
          old_evac_bytes += live_bytes;
          consumed_by_old_evac = new_old_consumption;
          consumed_by_promo = new_promo_consumption;
          cur_garbage = new_garbage;
          old_regions_evacuated++;
        } else {
          // We failed to sufficiently expand old so unwind proposed expansion
          committed_from_shared_reserves -= proposed_old_region_expansion * region_size_bytes;
        }
      } else if (heap->is_tenurable(r)) {
        size_t anticipated_consumption = (size_t) (live_bytes * ShenandoahPromoEvacWaste);
        size_t new_promo_consumption = consumed_by_promo + anticipated_consumption;
        size_t new_promo_reserve = old_promo_reserve;
        size_t proposed_old_region_expansion = 0;
        while ((new_promo_consumption > new_promo_reserve) && (committed_from_shared_reserves < shared_reserves)) {
          committed_from_shared_reserves += region_size_bytes;
          proposed_old_region_expansion++;
          new_promo_reserve += region_size_bytes;
        }
        if (new_promo_consumption <= new_promo_reserve) {
          add_region = true;
          old_promo_reserve = new_promo_reserve;
          promo_bytes += live_bytes;
          consumed_by_promo = new_promo_consumption;
          cur_garbage = new_garbage;
          aged_regions_promoted++;
        } else {
          // We failed to sufficiently expand old so unwind proposed expansion
          committed_from_shared_reserves -= proposed_old_region_expansion * region_size_bytes;
        }
      } else {
        assert(r->is_young() && !heap->is_tenurable(r), "DeMorgan's law (assuming r->is_affiliated)");
        size_t anticipated_consumption = (size_t) (live_bytes * ShenandoahEvacWaste);
        size_t new_young_evac_consumption = consumed_by_young_evac + anticipated_consumption;
        size_t new_young_evac_reserve = young_evac_reserve;
        size_t proposed_young_region_expansion = 0;
        while ((new_young_evac_consumption > new_young_evac_reserve) && (committed_from_shared_reserves < shared_reserves)) {
          committed_from_shared_reserves += region_size_bytes;
          proposed_young_region_expansion++;
          new_young_evac_reserve += region_size_bytes;
        }
        if (new_young_evac_consumption <= new_young_evac_reserve) {
          add_region = true;
          young_evac_reserve = new_young_evac_reserve;
          young_evac_bytes += live_bytes;
          consumed_by_young_evac = new_young_evac_consumption;
          cur_garbage = new_garbage;
          young_regions_evacuated++;
        } else {
          // We failed to sufficiently expand old so unwind proposed expansion
          committed_from_shared_reserves -= proposed_young_region_expansion * region_size_bytes;
        }
      }
    }
    if (add_region) {
      cset->add_region(r);
    }
  }

  if (committed_from_shared_reserves < shared_reserves) {
    // Give all the rest to promotion
    old_promo_reserve += (shared_reserves - committed_from_shared_reserves);
    // dead code: committed_from_shared_reserves = shared_reserves;
  }

  // Consider the effects of round-off:
  //  1. We know that the sum over each evacuation mutiplied by Evacuation Waste is <= total evacuation reserve
  //  2. However, the reserve for each individual evacuation may be rounded down.  In the worst case, we will be over budget
  //     by the number of regions evacuated, since each region's reserve might be under-estimated by at most 1
  //  3. Likewise, if we take the sum of bytes evacuated and multiply this by the Evacuation Waste and then round down
  //     to nearest integer, the calculated reserve will underestimate the true reserve needs by at most 1.
  //  4. This explains the adjustments to subtotals in the assert statements below.
  assert(young_evac_bytes * ShenandoahEvacWaste <= young_evac_reserve + young_regions_evacuated,
         "budget: %zu <= %zu", (size_t) (young_evac_bytes * ShenandoahEvacWaste), young_evac_reserve);
  assert(old_evac_bytes * ShenandoahOldEvacWaste <= old_evac_reserve + old_regions_evacuated,
         "budget: %zu <= %zu", (size_t) (old_evac_bytes * ShenandoahOldEvacWaste), old_evac_reserve);
  assert(promo_bytes * ShenandoahPromoEvacWaste <= old_promo_reserve + aged_regions_promoted,
         "budget: %zu <= %zu", (size_t) (promo_bytes * ShenandoahPromoEvacWaste), old_promo_reserve);
  assert(young_evac_reserve + old_evac_reserve + old_promo_reserve <=
         heap->young_generation()->get_evacuation_reserve() + heap->old_generation()->get_evacuation_reserve() +
         heap->old_generation()->get_promoted_reserve(), "Exceeded budget");

  if (heap->young_generation()->get_evacuation_reserve() < young_evac_reserve) {
    size_t delta_bytes = young_evac_reserve - heap->young_generation()->get_evacuation_reserve();
    size_t delta_regions = delta_bytes / region_size_bytes;
    size_t regions_to_transfer = MIN2(unaffiliated_old_regions, delta_regions);
    log_info(gc)("Global GC moves %zu unaffiliated regions from old collector to young collector reserves", regions_to_transfer);
    ssize_t negated_regions = -regions_to_transfer;
    heap->free_set()->move_unaffiliated_regions_from_collector_to_old_collector(negated_regions);
  } else if (heap->young_generation()->get_evacuation_reserve() > young_evac_reserve) {
    size_t delta_bytes = heap->young_generation()->get_evacuation_reserve() - young_evac_reserve;
    size_t delta_regions = delta_bytes / region_size_bytes;
    size_t regions_to_transfer = MIN2(unaffiliated_young_regions, delta_regions);
    log_info(gc)("Global GC moves %zu unaffiliated regions from young collector to old collector reserves", regions_to_transfer);
    heap->free_set()->move_unaffiliated_regions_from_collector_to_old_collector(regions_to_transfer);
  }

  heap->young_generation()->set_evacuation_reserve(young_evac_reserve);
  heap->old_generation()->set_evacuation_reserve(old_evac_reserve);
  heap->old_generation()->set_promoted_reserve(old_promo_reserve);
}
