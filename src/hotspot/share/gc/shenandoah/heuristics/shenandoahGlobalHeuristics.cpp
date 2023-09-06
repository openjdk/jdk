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

#include "precompiled.hpp"

#include "gc/shenandoah/heuristics/shenandoahGlobalHeuristics.hpp"
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahGlobalGeneration.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"

#include "utilities/quickSort.hpp"

ShenandoahGlobalHeuristics::ShenandoahGlobalHeuristics(ShenandoahGlobalGeneration* generation)
        : ShenandoahGenerationalHeuristics(generation) {
}


void ShenandoahGlobalHeuristics::choose_collection_set_from_regiondata(ShenandoahCollectionSet* cset,
                                                                       RegionData* data, size_t size,
                                                                       size_t actual_free) {
  // The logic for cset selection in adaptive is as follows:
  //
  //   1. We cannot get cset larger than available free space. Otherwise we guarantee OOME
  //      during evacuation, and thus guarantee full GC. In practice, we also want to let
  //      application to allocate something. This is why we limit CSet to some fraction of
  //      available space. In non-overloaded heap, max_cset would contain all plausible candidates
  //      over garbage threshold.
  //
  //   2. We should not get cset too low so that free threshold would not be met right
  //      after the cycle. Otherwise we get back-to-back cycles for no reason if heap is
  //      too fragmented. In non-overloaded non-fragmented heap min_garbage would be around zero.
  //
  // Therefore, we start by sorting the regions by garbage. Then we unconditionally add the best candidates
  // before we meet min_garbage. Then we add all candidates that fit with a garbage threshold before
  // we hit max_cset. When max_cset is hit, we terminate the cset selection. Note that in this scheme,
  // ShenandoahGarbageThreshold is the soft threshold which would be ignored until min_garbage is hit.

  // In generational mode, the sort order within the data array is not strictly descending amounts of garbage.  In
  // particular, regions that have reached tenure age will be sorted into this array before younger regions that contain
  // more garbage.  This represents one of the reasons why we keep looking at regions even after we decide, for example,
  // to exclude one of the regions because it might require evacuation of too much live data.



  // Better select garbage-first regions
  QuickSort::sort<RegionData>(data, (int) size, compare_by_garbage, false);

  size_t cur_young_garbage = add_preselected_regions_to_collection_set(cset, data, size);

  choose_global_collection_set(cset, data, size, actual_free, cur_young_garbage);

  log_cset_composition(cset);
}


void ShenandoahGlobalHeuristics::choose_global_collection_set(ShenandoahCollectionSet* cset,
                                                              const ShenandoahHeuristics::RegionData* data,
                                                              size_t size, size_t actual_free,
                                                              size_t cur_young_garbage) const {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();
  size_t capacity = heap->young_generation()->max_capacity();
  size_t garbage_threshold = region_size_bytes * ShenandoahGarbageThreshold / 100;
  size_t ignore_threshold = region_size_bytes * ShenandoahIgnoreGarbageThreshold / 100;
  const uint tenuring_threshold = heap->age_census()->tenuring_threshold();

  size_t max_young_cset = (size_t) (heap->get_young_evac_reserve() / ShenandoahEvacWaste);
  size_t young_cur_cset = 0;
  size_t max_old_cset = (size_t) (heap->get_old_evac_reserve() / ShenandoahOldEvacWaste);
  size_t old_cur_cset = 0;

  // Figure out how many unaffiliated young regions are dedicated to mutator and to evacuator.  Allow the young
  // collector's unaffiliated regions to be transferred to old-gen if old-gen has more easily reclaimed garbage
  // than young-gen.  At the end of this cycle, any excess regions remaining in old-gen will be transferred back
  // to young.  Do not transfer the mutator's unaffiliated regions to old-gen.  Those must remain available
  // to the mutator as it needs to be able to consume this memory during concurrent GC.

  size_t unaffiliated_young_regions = heap->young_generation()->free_unaffiliated_regions();
  size_t unaffiliated_young_memory = unaffiliated_young_regions * region_size_bytes;

  if (unaffiliated_young_memory > max_young_cset) {
    size_t unaffiliated_mutator_memory = unaffiliated_young_memory - max_young_cset;
    unaffiliated_young_memory -= unaffiliated_mutator_memory;
    unaffiliated_young_regions = unaffiliated_young_memory / region_size_bytes; // round down
    unaffiliated_young_memory = unaffiliated_young_regions * region_size_bytes;
  }

  // We'll affiliate these unaffiliated regions with either old or young, depending on need.
  max_young_cset -= unaffiliated_young_memory;

  // Keep track of how many regions we plan to transfer from young to old.
  size_t regions_transferred_to_old = 0;

  size_t free_target = (capacity * ShenandoahMinFreeThreshold) / 100 + max_young_cset;
  size_t min_garbage = (free_target > actual_free) ? (free_target - actual_free) : 0;

  log_info(gc, ergo)("Adaptive CSet Selection for GLOBAL. Max Young Evacuation: " SIZE_FORMAT
                     "%s, Max Old Evacuation: " SIZE_FORMAT "%s, Actual Free: " SIZE_FORMAT "%s.",
                     byte_size_in_proper_unit(max_young_cset), proper_unit_for_byte_size(max_young_cset),
                     byte_size_in_proper_unit(max_old_cset), proper_unit_for_byte_size(max_old_cset),
                     byte_size_in_proper_unit(actual_free), proper_unit_for_byte_size(actual_free));

  for (size_t idx = 0; idx < size; idx++) {
    ShenandoahHeapRegion* r = data[idx]._region;
    if (cset->is_preselected(r->index())) {
      fatal("There should be no preselected regions during GLOBAL GC");
      continue;
    }
    bool add_region = false;
    if (r->is_old() || (r->age() >= tenuring_threshold)) {
      size_t new_cset = old_cur_cset + r->get_live_data_bytes();
      if ((r->garbage() > garbage_threshold)) {
        while ((new_cset > max_old_cset) && (unaffiliated_young_regions > 0)) {
          unaffiliated_young_regions--;
          regions_transferred_to_old++;
          max_old_cset += region_size_bytes / ShenandoahOldEvacWaste;
        }
      }
      if ((new_cset <= max_old_cset) && (r->garbage() > garbage_threshold)) {
        add_region = true;
        old_cur_cset = new_cset;
      }
    } else {
      assert(r->is_young() && (r->age() < tenuring_threshold), "DeMorgan's law (assuming r->is_affiliated)");
      size_t new_cset = young_cur_cset + r->get_live_data_bytes();
      size_t region_garbage = r->garbage();
      size_t new_garbage = cur_young_garbage + region_garbage;
      bool add_regardless = (region_garbage > ignore_threshold) && (new_garbage < min_garbage);

      if (add_regardless || (r->garbage() > garbage_threshold)) {
        while ((new_cset > max_young_cset) && (unaffiliated_young_regions > 0)) {
          unaffiliated_young_regions--;
          max_young_cset += region_size_bytes / ShenandoahEvacWaste;
        }
      }
      if ((new_cset <= max_young_cset) && (add_regardless || (region_garbage > garbage_threshold))) {
        add_region = true;
        young_cur_cset = new_cset;
        cur_young_garbage = new_garbage;
      }
    }
    if (add_region) {
      cset->add_region(r);
    }
  }

  if (regions_transferred_to_old > 0) {
    heap->generation_sizer()->force_transfer_to_old(regions_transferred_to_old);
    heap->set_young_evac_reserve(heap->get_young_evac_reserve() - regions_transferred_to_old * region_size_bytes);
    heap->set_old_evac_reserve(heap->get_old_evac_reserve() + regions_transferred_to_old * region_size_bytes);
  }
}
