/*
 * Copyright (c) 2021, Amazon.com, Inc. or its affiliates. All rights reserved.
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

#include "gc/shenandoah/heuristics/shenandoahOldHeuristics.hpp"
#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"
#include "utilities/quickSort.hpp"

ShenandoahOldHeuristics::ShenandoahOldHeuristics(ShenandoahGeneration* generation, ShenandoahHeuristics* trigger_heuristic) :
    ShenandoahHeuristics(generation),
    _old_collection_candidates(0),
    _next_old_collection_candidate(0),
    _hidden_old_collection_candidates(0),
    _hidden_next_old_collection_candidate(0),
    _old_coalesce_and_fill_candidates(0),
    _first_coalesce_and_fill_candidate(0),
    _trigger_heuristic(trigger_heuristic),
    _promotion_failed(false)
{
}

bool ShenandoahOldHeuristics::prime_collection_set(ShenandoahCollectionSet* collection_set) {
  if (unprocessed_old_collection_candidates() == 0) {
    return false;
  }

  ShenandoahHeap* heap = ShenandoahHeap::heap();
  uint included_old_regions = 0;
  size_t evacuated_old_bytes = 0;

  // TODO:
  // The max_old_evacuation_bytes and promotion_budget_bytes constants represent a first
  // approximation to desired operating parameters.  Eventually, these values should be determined
  // by heuristics and should adjust dynamically based on most current execution behavior.  In the
  // interim, we offer command-line options to set the values of these configuration parameters.

  // max_old_evacuation_bytes represents a bound on how much evacuation effort is dedicated
  // to old-gen regions.
  size_t max_old_evacuation_bytes = (heap->old_generation()->soft_max_capacity() * ShenandoahOldEvacReserve) / 100;
  const size_t young_evacuation_bytes = (heap->young_generation()->soft_max_capacity() * ShenandoahEvacReserve) / 100;
  const size_t ratio_bound_on_old_evac_bytes = (young_evacuation_bytes * ShenandoahOldEvacRatioPercent) / 100;
  if (max_old_evacuation_bytes > ratio_bound_on_old_evac_bytes) {
    max_old_evacuation_bytes = ratio_bound_on_old_evac_bytes;
  }

  // Usually, old-evacuation is limited by the CPU bounds on effort.  However, it can also be bounded by available
  // memory within old-gen to hold the results of evacuation.  When we are bound by memory availability, we need
  // to account below for the loss of available memory from within each region that is added to the old-gen collection
  // set.
  size_t old_available = heap->old_generation()->available();
  size_t excess_old_capacity_for_evacuation;
  if (max_old_evacuation_bytes > old_available) {
    max_old_evacuation_bytes = old_available;
    excess_old_capacity_for_evacuation = 0;
  } else {
    excess_old_capacity_for_evacuation = old_available - max_old_evacuation_bytes;
  }

  // promotion_budget_bytes represents an "arbitrary" bound on how many bytes can be consumed by young-gen
  // objects promoted into old-gen memory.  We need to avoid a scenario under which promotion of objects
  // depletes old-gen available memory to the point that there is insufficient memory to hold old-gen objects
  // that need to be evacuated from within the old-gen collection set.
  //
  // Key idea: if there is not sufficient memory within old-gen to hold an object that wants to be promoted, defer
  // promotion until a subsequent evacuation pass.  Enforcement is provided at the time PLABs and shared allocations
  // in old-gen memory are requested.

  const size_t promotion_budget_bytes = heap->get_promotion_reserve();

  // old_evacuation_budget is an upper bound on the amount of live memory that can be evacuated.
  //
  // If a region is put into the collection set, then this region's free (not yet used) bytes are no longer
  // "available" to hold the results of other evacuations.  This may cause a decrease in the remaining amount
  // of memory that can still be evacuated.  We address this by reducing the evacuation budget by the amount
  // of live memory in that region and by the amount of unallocated memory in that region if the evacuation
  // budget is constrained by availability of free memory.  See remaining_old_evacuation_budget below.

  size_t old_evacuation_budget = (size_t) (max_old_evacuation_bytes / ShenandoahEvacWaste);

  log_info(gc)("Choose old regions for mixed collection: old evacuation budget: " SIZE_FORMAT "%s",
                byte_size_in_proper_unit(old_evacuation_budget), proper_unit_for_byte_size(old_evacuation_budget));

  size_t remaining_old_evacuation_budget = old_evacuation_budget;
  size_t lost_evacuation_capacity = 0;

  // The number of old-gen regions that were selected as candidates for collection at the end of the most recent
  // old-gen concurrent marking phase and have not yet been collected is represented by
  // unprocessed_old_collection_candidates()
  while (unprocessed_old_collection_candidates() > 0) {
    // Old collection candidates are sorted in order of decreasing garbage contained therein.
    ShenandoahHeapRegion* r = next_old_collection_candidate();


    // If we choose region r to be collected, then we need to decrease the capacity to hold other evacuations by
    // the size of r's free memory.
    if ((r->get_live_data_bytes() <= remaining_old_evacuation_budget) &&
        ((lost_evacuation_capacity + r->free() <= excess_old_capacity_for_evacuation)
         || (r->get_live_data_bytes() + r->free() <= remaining_old_evacuation_budget))) {

      // Decrement remaining evacuation budget by bytes that will be copied.  If the cumulative loss of free memory from
      // regions that are to be collected exceeds excess_old_capacity_for_evacuation,  decrease
      // remaining_old_evacuation_budget by this loss as well.
      lost_evacuation_capacity += r->free();
      remaining_old_evacuation_budget -= r->get_live_data_bytes();
      if (lost_evacuation_capacity > excess_old_capacity_for_evacuation) {
        // This is slightly conservative because we really only need to remove from the remaining evacuation budget
        // the amount by which lost_evacution_capacity exceeds excess_old_capacity_for_evacuation, but this is relatively
        // rare event and current thought is to be a bit conservative rather than mess up the math on code that is so
        // difficult to test and maintain...

        // Once we have crossed the threshold of lost_evacuation_capacity exceeding excess_old_capacity_for_evacuation,
        // every subsequent iteration of this loop will further decrease remaining_old_evacuation_budget.
        remaining_old_evacuation_budget -= r->free();
      }
      collection_set->add_region(r);
      included_old_regions++;
      evacuated_old_bytes += r->get_live_data_bytes();
      consume_old_collection_candidate();
    } else {
      break;
    }
  }

  if (included_old_regions > 0) {
    log_info(gc)("Old-gen piggyback evac (" UINT32_FORMAT " regions, " SIZE_FORMAT " %s)",
                 included_old_regions, byte_size_in_proper_unit(evacuated_old_bytes), proper_unit_for_byte_size(evacuated_old_bytes));
  }
  return (included_old_regions > 0);
}

// Both arguments are don't cares for old-gen collections
void ShenandoahOldHeuristics::choose_collection_set(ShenandoahCollectionSet* collection_set,
                                                    ShenandoahOldHeuristics* old_heuristics) {
  assert((collection_set == nullptr) && (old_heuristics == nullptr),
         "Expect null arguments in ShenandoahOldHeuristics::choose_collection_set()");
  // Old-gen doesn't actually choose a collection set to be evacuated by its own gang of worker tasks.
  // Instead, it computes the set of regions to be evacuated by subsequent young-gen evacuation passes.
  prepare_for_old_collections();
}

void ShenandoahOldHeuristics::prepare_for_old_collections() {
  assert(_generation->generation_mode() == OLD, "This service only available for old-gc heuristics");
  ShenandoahHeap* heap = ShenandoahHeap::heap();

  size_t cand_idx = 0;
  size_t total_garbage = 0;
  size_t num_regions = heap->num_regions();
  size_t immediate_garbage = 0;
  size_t immediate_regions = 0;

  RegionData* candidates = _region_data;
  for (size_t i = 0; i < num_regions; i++) {
    ShenandoahHeapRegion* region = heap->get_region(i);
    if (!in_generation(region)) {
      continue;
    }

    size_t garbage = region->garbage();
    total_garbage += garbage;

    if (region->is_regular()) {
      if (!region->has_live()) {
        region->make_trash_immediate();
        immediate_regions++;
        immediate_garbage += garbage;
      } else {
        region->begin_preemptible_coalesce_and_fill();
        candidates[cand_idx]._region = region;
        candidates[cand_idx]._garbage = garbage;
        cand_idx++;
      }
    } else if (region->is_humongous_start()) {
      if (!region->has_live()) {
        // The humongous object is dead, we can just return this region and the continuations
        // immediately to the freeset - no evacuations are necessary here. The continuations
        // will be made into trash by this method, so they'll be skipped by the 'is_regular'
        // check above.
        size_t region_count = heap->trash_humongous_region_at(region);
        log_debug(gc)("Trashed " SIZE_FORMAT " regions for humongous object.", region_count);
      }
    } else if (region->is_trash()) {
      // Count humongous objects made into trash here.
      immediate_regions++;
      immediate_garbage += garbage;
    }
  }

  // TODO: Consider not running mixed collects if we recovered some threshold percentage of memory from immediate garbage.
  // This would be similar to young and global collections shortcutting evacuation, though we'd probably want a separate
  // threshold for the old generation.

  // Prioritize regions to select garbage-first regions
  QuickSort::sort<RegionData>(candidates, cand_idx, compare_by_garbage, false);

  // Any old-gen region that contains (ShenandoahOldGarbageThreshold (default value 25))% garbage or more is to
  // be evacuated.
  //
  // TODO: allow ShenandoahOldGarbageThreshold to be determined adaptively, by heuristics.

  const size_t garbage_threshold = ShenandoahHeapRegion::region_size_bytes() * ShenandoahOldGarbageThreshold / 100;
  size_t candidates_garbage = 0;
  for (size_t i = 0; i < cand_idx; i++) {
    candidates_garbage += candidates[i]._garbage;
    if (candidates[i]._garbage < garbage_threshold) {
      // Candidates are sorted in decreasing order of garbage, so no regions after this will be above the threshold
      _hidden_next_old_collection_candidate = 0;
      _hidden_old_collection_candidates = (uint)i;
      _first_coalesce_and_fill_candidate = (uint)i;
      _old_coalesce_and_fill_candidates = (uint)(cand_idx - i);

      // Note that we do not coalesce and fill occupied humongous regions
      // HR: humongous regions, RR: regular regions, CF: coalesce and fill regions
      log_info(gc)("Old-gen mark evac (" UINT32_FORMAT " RR, " UINT32_FORMAT " CF)",
                   _hidden_old_collection_candidates, _old_coalesce_and_fill_candidates);
      return;
    }
  }

  // If we reach here, all of non-humogous old-gen regions are candidates for collection set.
  _hidden_next_old_collection_candidate = 0;
  _hidden_old_collection_candidates = (uint)cand_idx;
  _first_coalesce_and_fill_candidate = 0;
  _old_coalesce_and_fill_candidates = 0;

  // Note that we do not coalesce and fill occupied humongous regions
  // HR: humongous regions, RR: regular regions, CF: coalesce and fill regions
  size_t collectable_garbage = immediate_garbage + candidates_garbage;
  log_info(gc)("Old-gen mark evac (" UINT32_FORMAT " RR, " UINT32_FORMAT " CF), "
               "Collectable Garbage: " SIZE_FORMAT "%s, "
               "Immediate Garbage: " SIZE_FORMAT "%s",
               _hidden_old_collection_candidates, _old_coalesce_and_fill_candidates,
               byte_size_in_proper_unit(collectable_garbage), proper_unit_for_byte_size(collectable_garbage),
               byte_size_in_proper_unit(immediate_garbage), proper_unit_for_byte_size(immediate_garbage));
}

void ShenandoahOldHeuristics::start_old_evacuations() {
  assert(_generation->generation_mode() == OLD, "This service only available for old-gc heuristics");

  _old_collection_candidates = _hidden_old_collection_candidates;
  _next_old_collection_candidate = _hidden_next_old_collection_candidate;

  _hidden_old_collection_candidates = 0;
}

uint ShenandoahOldHeuristics::unprocessed_old_or_hidden_collection_candidates() {
  assert(_generation->generation_mode() == OLD, "This service only available for old-gc heuristics");
  return _old_collection_candidates + _hidden_old_collection_candidates;
}

uint ShenandoahOldHeuristics::unprocessed_old_collection_candidates() {
  assert(_generation->generation_mode() == OLD, "This service only available for old-gc heuristics");
  return _old_collection_candidates;
}

ShenandoahHeapRegion* ShenandoahOldHeuristics::next_old_collection_candidate() {
  assert(_generation->generation_mode() == OLD, "This service only available for old-gc heuristics");
  return _region_data[_next_old_collection_candidate]._region;
}

void ShenandoahOldHeuristics::consume_old_collection_candidate() {
  assert(_generation->generation_mode() == OLD, "This service only available for old-gc heuristics");
  _next_old_collection_candidate++;
  _old_collection_candidates--;
}

uint ShenandoahOldHeuristics::old_coalesce_and_fill_candidates() {
  assert(_generation->generation_mode() == OLD, "This service only available for old-gc heuristics");
  return _old_coalesce_and_fill_candidates;
}

void ShenandoahOldHeuristics::get_coalesce_and_fill_candidates(ShenandoahHeapRegion** buffer) {
  assert(_generation->generation_mode() == OLD, "This service only available for old-gc heuristics");
  uint count = _old_coalesce_and_fill_candidates;
  int index = _first_coalesce_and_fill_candidate;
  while (count-- > 0) {
    *buffer++ = _region_data[index++]._region;
  }
}

void ShenandoahOldHeuristics::abandon_collection_candidates() {
  _old_collection_candidates = 0;
  _next_old_collection_candidate = 0;
  _hidden_old_collection_candidates = 0;
  _hidden_next_old_collection_candidate = 0;
  _old_coalesce_and_fill_candidates = 0;
  _first_coalesce_and_fill_candidate = 0;
}

void ShenandoahOldHeuristics::handle_promotion_failure() {
  _promotion_failed = true;
}

void ShenandoahOldHeuristics::record_cycle_start() {
  _promotion_failed = false;
  _trigger_heuristic->record_cycle_start();
}

void ShenandoahOldHeuristics::record_cycle_end() {
  _trigger_heuristic->record_cycle_end();
}

bool ShenandoahOldHeuristics::should_start_gc() {
  // Cannot start a new old-gen GC until previous one has finished.
  //
  // Future refinement: under certain circumstances, we might be more sophisticated about this choice.
  // For example, we could choose to abandon the previous old collection before it has completed evacuations,
  // but this would require that we coalesce and fill all garbage within unevacuated collection-set regions.
  if (unprocessed_old_or_hidden_collection_candidates() > 0) {
    return false;
  }

  // If there's been a promotion failure (and we don't have regions already scheduled for evacuation),
  // start a new old generation collection.
  if (_promotion_failed) {
    log_info(gc)("Trigger: Promotion Failure");
    return true;
  }

  // Otherwise, defer to configured heuristic for gc trigger.
  return _trigger_heuristic->should_start_gc();
}

bool ShenandoahOldHeuristics::should_degenerate_cycle() {
  return _trigger_heuristic->should_degenerate_cycle();
}

void ShenandoahOldHeuristics::record_success_concurrent() {
  _trigger_heuristic->record_success_concurrent();
}

void ShenandoahOldHeuristics::record_success_degenerated() {
  _trigger_heuristic->record_success_degenerated();
}

void ShenandoahOldHeuristics::record_success_full() {
  _trigger_heuristic->record_success_full();
}

void ShenandoahOldHeuristics::record_allocation_failure_gc() {
  _trigger_heuristic->record_allocation_failure_gc();
}

void ShenandoahOldHeuristics::record_requested_gc() {
  _trigger_heuristic->record_requested_gc();
}

bool ShenandoahOldHeuristics::can_unload_classes() {
  return _trigger_heuristic->can_unload_classes();
}

bool ShenandoahOldHeuristics::can_unload_classes_normal() {
  return _trigger_heuristic->can_unload_classes_normal();
}

bool ShenandoahOldHeuristics::should_unload_classes() {
  return _trigger_heuristic->should_unload_classes();
}

const char* ShenandoahOldHeuristics::name() {
  static char name[128];
  jio_snprintf(name, sizeof(name), "%s (OLD)", _trigger_heuristic->name());
  return name;
}

bool ShenandoahOldHeuristics::is_diagnostic() {
  return false;
}

bool ShenandoahOldHeuristics::is_experimental() {
  return true;
}

void ShenandoahOldHeuristics::choose_collection_set_from_regiondata(ShenandoahCollectionSet* set,
                                                                    ShenandoahHeuristics::RegionData* data,
                                                                    size_t data_size, size_t free) {
  ShouldNotReachHere();
}

