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
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahGenerationalHeap.hpp"
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
  QuickSort::sort<RegionData>(data, (int) size, compare_by_garbage);

  choose_global_collection_set(cset, data, size, actual_free, 0 /* cur_young_garbage */);

  log_cset_composition(cset);
}


void ShenandoahGlobalHeuristics::choose_global_collection_set(ShenandoahCollectionSet* cset,
                                                              const ShenandoahHeuristics::RegionData* data,
                                                              size_t size, size_t actual_free,
                                                              size_t cur_young_garbage) const {
  auto heap = ShenandoahGenerationalHeap::heap();
  size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();
  size_t young_capacity = heap->young_generation()->max_capacity();
  size_t old_capacity = heap->old_generation()->max_capacity();
  size_t garbage_threshold = region_size_bytes * ShenandoahGarbageThreshold / 100;
  size_t ignore_threshold = region_size_bytes * ShenandoahIgnoreGarbageThreshold / 100;
  const uint tenuring_threshold = heap->age_census()->tenuring_threshold();

  size_t young_evac_reserve = heap->young_generation()->get_evacuation_reserve();
  size_t old_evac_reserve = heap->old_generation()->get_evacuation_reserve();

  size_t unaffiliated_young_regions = heap->young_generation()->free_unaffiliated_regions();
  size_t unaffiliated_young_memory = unaffiliated_young_regions * region_size_bytes;
  size_t unaffiliated_old_regions = heap->old_generation()->free_unaffiliated_regions();
  size_t unaffiliated_old_memory = unaffiliated_old_regions * region_size_bytes;

  // Figure out how many unaffiliated regions are dedicated to Collector and OldCollector reserves.  Let these
  // be shuffled between young and old generations in order to expedite evacuation of whichever regions have the
  // most garbage, regardless of whether these garbage-first regions reside in young or old generation.
  // Excess reserves will be transferred back to the mutator after collection set has been chosen.  At the end
  // of evacuation, any reserves not consumed by evacuation will also be transferred to the mutator free set.
  size_t shared_reserve_regions = 0;
  if (young_evac_reserve > unaffiliated_young_memory) {
    young_evac_reserve -= unaffiliated_young_memory;
    shared_reserve_regions += unaffiliated_young_memory / region_size_bytes;
  } else {
    size_t delta_regions = young_evac_reserve / region_size_bytes;
    shared_reserve_regions += delta_regions;
    young_evac_reserve -= delta_regions * region_size_bytes;
  }
  if (old_evac_reserve > unaffiliated_old_memory) {
    old_evac_reserve -= unaffiliated_old_memory;
    shared_reserve_regions += unaffiliated_old_memory / region_size_bytes;
  } else {
    size_t delta_regions = old_evac_reserve / region_size_bytes;
    shared_reserve_regions += delta_regions;
    old_evac_reserve -= delta_regions * region_size_bytes;
  }

  size_t shared_reserves = shared_reserve_regions * region_size_bytes;
  size_t committed_from_shared_reserves = 0;
  size_t max_young_cset = (size_t) (young_evac_reserve / ShenandoahEvacWaste);
  size_t young_cur_cset = 0;
  size_t max_old_cset = (size_t) (old_evac_reserve / ShenandoahOldEvacWaste);
  size_t old_cur_cset = 0;

  size_t promo_bytes = 0;
  size_t old_evac_bytes = 0;
  size_t young_evac_bytes = 0;

  size_t max_total_cset = (max_young_cset + max_old_cset +
                           (size_t) (shared_reserve_regions * region_size_bytes) / ShenandoahOldEvacWaste);
  size_t free_target = ((young_capacity + old_capacity) * ShenandoahMinFreeThreshold) / 100 + max_total_cset;
  size_t min_garbage = (free_target > actual_free) ? (free_target - actual_free) : 0;

  log_info(gc, ergo)("Adaptive CSet Selection for GLOBAL. Max Young Evacuation: %zu"
                     "%s, Max Old Evacuation: %zu%s, Discretionary additional evacuation: %zu%s, Actual Free: %zu%s.",
                     byte_size_in_proper_unit(max_young_cset), proper_unit_for_byte_size(max_young_cset),
                     byte_size_in_proper_unit(max_old_cset), proper_unit_for_byte_size(max_old_cset),
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
    if (r->is_old() || (r->age() >= tenuring_threshold)) {
      if (add_regardless || (region_garbage > garbage_threshold)) {
        size_t live_bytes = r->get_live_data_bytes();
        size_t new_cset = old_cur_cset + r->get_live_data_bytes();
        // May need multiple reserve regions to evacuate a single region, depending on live data bytes and ShenandoahOldEvacWaste
        size_t orig_max_old_cset = max_old_cset;
        size_t proposed_old_region_consumption = 0;
        while ((new_cset > max_old_cset) && (committed_from_shared_reserves < shared_reserves)) {
          committed_from_shared_reserves += region_size_bytes;
          proposed_old_region_consumption++;
          max_old_cset += region_size_bytes / ShenandoahOldEvacWaste;
        }
        // We already know: add_regardless || region_garbage > garbage_threshold
        if (new_cset <= max_old_cset) {
          add_region = true;
          old_cur_cset = new_cset;
          cur_garbage = new_garbage;
          if (r->is_old()) {
            old_evac_bytes += live_bytes;
          } else {
            promo_bytes += live_bytes;
          }
        } else {
          // We failed to sufficiently expand old, so unwind proposed expansion
          max_old_cset = orig_max_old_cset;
          committed_from_shared_reserves -= proposed_old_region_consumption * region_size_bytes;
        }
      }
    } else {
      assert(r->is_young() && (r->age() < tenuring_threshold), "DeMorgan's law (assuming r->is_affiliated)");
      if (add_regardless || (region_garbage > garbage_threshold)) {
        size_t live_bytes = r->get_live_data_bytes();
        size_t new_cset = young_cur_cset + live_bytes;
        // May need multiple reserve regions to evacuate a single region, depending on live data bytes and ShenandoahEvacWaste
        size_t orig_max_young_cset = max_young_cset;
        size_t proposed_young_region_consumption = 0;
        while ((new_cset > max_young_cset) && (committed_from_shared_reserves < shared_reserves)) {
          committed_from_shared_reserves += region_size_bytes;
          proposed_young_region_consumption++;
          max_young_cset += region_size_bytes / ShenandoahEvacWaste;
        }
        // We already know: add_regardless || region_garbage > garbage_threshold
        if (new_cset <= max_young_cset) {
          add_region = true;
          young_cur_cset = new_cset;
          cur_garbage = new_garbage;
          young_evac_bytes += live_bytes;
        } else {
          // We failed to sufficiently expand young, so unwind proposed expansion
          max_young_cset = orig_max_young_cset;
          committed_from_shared_reserves -= proposed_young_region_consumption * region_size_bytes;
        }
      }
    }
    if (add_region) {
      cset->add_region(r);
    }
  }

  heap->young_generation()->set_evacuation_reserve((size_t) (young_evac_bytes * ShenandoahEvacWaste));
  heap->old_generation()->set_evacuation_reserve((size_t) (old_evac_bytes * ShenandoahOldEvacWaste));
  heap->old_generation()->set_promoted_reserve((size_t) (promo_bytes * ShenandoahPromoEvacWaste));
}
