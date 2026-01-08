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

#include "gc/shenandoah/heuristics/shenandoahGenerationalHeuristics.hpp"
#include "gc/shenandoah/shenandoahCollectionSet.hpp"
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/shenandoahGenerationalHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahTrace.hpp"
#include "logging/log.hpp"

ShenandoahGenerationalHeuristics::ShenandoahGenerationalHeuristics(ShenandoahGeneration* generation)
        : ShenandoahAdaptiveHeuristics(generation), _generation(generation) {
}

size_t ShenandoahGenerationalHeuristics::choose_collection_set(ShenandoahCollectionSet* collection_set) {
  assert(collection_set->is_empty(), "Must be empty");

  auto heap = ShenandoahGenerationalHeap::heap();
  size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();


  // Check all pinned regions have updated status before choosing the collection set.
  heap->assert_pinned_region_status(_generation);

  // Step 1. Build up the region candidates we care about, rejecting losers and accepting winners right away.

  size_t num_regions = heap->num_regions();

  RegionData* candidates = _region_data;

  size_t cand_idx = 0;
  size_t preselected_candidates = 0;

  size_t total_garbage = 0;

  size_t immediate_garbage = 0;
  size_t immediate_regions = 0;

  size_t free = 0;
  size_t free_regions = 0;

  // This counts number of humongous regions that we intend to promote in this cycle.
  size_t humongous_regions_promoted = 0;
  // This counts number of regular regions that will be promoted in place.
  size_t regular_regions_promoted_in_place = 0;
  // This counts bytes of memory used by regular regions to be promoted in place.
  size_t regular_regions_promoted_usage = 0;
  // This counts bytes of memory free in regular regions to be promoted in place.
  size_t regular_regions_promoted_free = 0;
  // This counts bytes of garbage memory in regular regions to be promoted in place.
  size_t regular_regions_promoted_garbage = 0;

  for (size_t i = 0; i < num_regions; i++) {
    ShenandoahHeapRegion* region = heap->get_region(i);
    if (!_generation->contains(region)) {
      continue;
    }
    size_t garbage = region->garbage();
    total_garbage += garbage;
    if (region->is_empty()) {
      free_regions++;
      free += region_size_bytes;
    } else if (region->is_regular()) {
      if (!region->has_live()) {
        // We can recycle it right away and put it in the free set.
        immediate_regions++;
        immediate_garbage += garbage;
        region->make_trash_immediate();
      } else {
        bool is_candidate;
        // This is our candidate for later consideration.
        if (collection_set->is_preselected(i)) {
          assert(heap->is_tenurable(region), "Preselection filter");
          is_candidate = true;
          preselected_candidates++;
          // Set garbage value to maximum value to force this into the sorted collection set.
          garbage = region_size_bytes;
        } else if (region->is_young() && heap->is_tenurable(region)) {
          // Note that for GLOBAL GC, region may be OLD, and OLD regions do not qualify for pre-selection

          // This region is old enough to be promoted but it was not preselected, either because its garbage is below
          // old garbage threshold so it will be promoted in place, or because there is not sufficient room
          // in old gen to hold the evacuated copies of this region's live data.  In both cases, we choose not to
          // place this region into the collection set.
          if (region->get_top_before_promote() != nullptr) {
            // Region was included for promotion-in-place
            regular_regions_promoted_in_place++;
            regular_regions_promoted_usage += region->used_before_promote();
            regular_regions_promoted_free += region->free();
            regular_regions_promoted_garbage += region->garbage();
          }
          is_candidate = false;
        } else {
          is_candidate = true;
        }
        if (is_candidate) {
          candidates[cand_idx].set_region_and_garbage(region, garbage);
          cand_idx++;
        }
      }
    } else if (region->is_humongous_start()) {
      // Reclaim humongous regions here, and count them as the immediate garbage
#ifdef ASSERT
      bool reg_live = region->has_live();
      bool bm_live = _generation->complete_marking_context()->is_marked(cast_to_oop(region->bottom()));
      assert(reg_live == bm_live,
             "Humongous liveness and marks should agree. Region live: %s; Bitmap live: %s; Region Live Words: %zu",
             BOOL_TO_STR(reg_live), BOOL_TO_STR(bm_live), region->get_live_data_words());
#endif
      if (!region->has_live()) {
        heap->trash_humongous_region_at(region);

        // Count only the start. Continuations would be counted on "trash" path
        immediate_regions++;
        immediate_garbage += garbage;
      } else {
        if (region->is_young() && heap->is_tenurable(region)) {
          oop obj = cast_to_oop(region->bottom());
          size_t humongous_regions = ShenandoahHeapRegion::required_regions(obj->size() * HeapWordSize);
          humongous_regions_promoted += humongous_regions;
        }
      }
    } else if (region->is_trash()) {
      // Count in just trashed collection set, during coalesced CM-with-UR
      immediate_regions++;
      immediate_garbage += garbage;
    }
  }
  heap->old_generation()->set_expected_humongous_region_promotions(humongous_regions_promoted);
  heap->old_generation()->set_expected_regular_region_promotions(regular_regions_promoted_in_place);
  log_info(gc, ergo)("Planning to promote in place %zu humongous regions and %zu"
                     " regular regions, spanning a total of %zu used bytes",
                     humongous_regions_promoted, regular_regions_promoted_in_place,
                     humongous_regions_promoted * ShenandoahHeapRegion::region_size_bytes() +
                     regular_regions_promoted_usage);

  // Step 2. Look back at garbage statistics, and decide if we want to collect anything,
  // given the amount of immediately reclaimable garbage. If we do, figure out the collection set.

  assert (immediate_garbage <= total_garbage,
          "Cannot have more immediate garbage than total garbage: %zu%s vs %zu%s",
          byte_size_in_proper_unit(immediate_garbage), proper_unit_for_byte_size(immediate_garbage),
          byte_size_in_proper_unit(total_garbage), proper_unit_for_byte_size(total_garbage));

  size_t immediate_percent = (total_garbage == 0) ? 0 : (immediate_garbage * 100 / total_garbage);
  bool doing_promote_in_place = (humongous_regions_promoted + regular_regions_promoted_in_place > 0);

  size_t add_regions_to_old = 0;
  if (doing_promote_in_place || (preselected_candidates > 0) || (immediate_percent <= ShenandoahImmediateThreshold)) {
    // Call the subclasses to add young-gen regions into the collection set.
    add_regions_to_old = choose_collection_set_from_regiondata(collection_set, candidates, cand_idx, immediate_garbage + free);
  }

  if (collection_set->has_old_regions()) {
    heap->shenandoah_policy()->record_mixed_cycle();
  }

  collection_set->summarize(total_garbage, immediate_garbage, immediate_regions);

  ShenandoahTracer::report_evacuation_info(collection_set,
                                           free_regions,
                                           humongous_regions_promoted,
                                           regular_regions_promoted_in_place,
                                           regular_regions_promoted_garbage,
                                           regular_regions_promoted_free,
                                           immediate_regions,
                                           immediate_garbage);
  return add_regions_to_old;
}


size_t ShenandoahGenerationalHeuristics::add_preselected_regions_to_collection_set(ShenandoahCollectionSet* cset,
                                                                                   const RegionData* data,
                                                                                   size_t size) const {
  // cur_young_garbage represents the amount of memory to be reclaimed from young-gen.  In the case that live objects
  // are known to be promoted out of young-gen, we count this as cur_young_garbage because this memory is reclaimed
  // from young-gen and becomes available to serve future young-gen allocation requests.
  size_t cur_young_garbage = 0;
  for (size_t idx = 0; idx < size; idx++) {
    ShenandoahHeapRegion* r = data[idx].get_region();
    if (cset->is_preselected(r->index())) {
      assert(ShenandoahGenerationalHeap::heap()->is_tenurable(r), "Preselected regions must have tenure age");
      // Entire region will be promoted, This region does not impact young-gen or old-gen evacuation reserve.
      // This region has been pre-selected and its impact on promotion reserve is already accounted for.
      cur_young_garbage += r->garbage();
      cset->add_region(r);
    }
  }
  return cur_young_garbage;
}

