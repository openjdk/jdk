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
#include "utilities/quickSort.hpp"

ShenandoahOldHeuristics::ShenandoahOldHeuristics(ShenandoahGeneration* generation, ShenandoahHeuristics* trigger_heuristic) :
    ShenandoahHeuristics(generation),
    _old_collection_candidates(0),
    _next_old_collection_candidate(0),
    _hidden_old_collection_candidates(0),
    _hidden_next_old_collection_candidate(0),
    _old_coalesce_and_fill_candidates(0),
    _first_coalesce_and_fill_candidate(0),
    _trigger_heuristic(trigger_heuristic)
{
}

bool ShenandoahOldHeuristics::prime_collection_set(ShenandoahCollectionSet* collection_set) {
  if (unprocessed_old_collection_candidates() == 0) {
    // no candidates for inclusion in collection set.
    return false;
  }

  uint included_old_regions = 0;
  size_t evacuated_old_bytes = 0;

  // TODO:
  // The max_old_evacuation_bytes and promotion_budget_bytes constants represent a first
  // approximation to desired operating parameters.  Eventually, these values should be determined
  // by heuristics and should adjust dynamically based on most current execution behavior.  In the
  // interim, we offer command-line options to set the values of these configuration parameters.

  // max_old_evacuation_bytes represents an "arbitrary" bound on how much evacuation effort is dedicated
  // to old-gen regions.
  const size_t max_old_evacuation_bytes = (size_t) ((double)_generation->soft_max_capacity() / 100 * ShenandoahOldEvacReserve);

  // promotion_budget_bytes represents an "arbitrary" bound on how many bytes can be consumed by young-gen
  // objects promoted into old-gen memory.  We need to avoid a scenario under which promotion of objects
  // depletes old-gen available memory to the point that there is insufficient memory to hold old-gen objects
  // that need to be evacuated from within the old-gen collection set.
  //
  // TODO We should probably enforce this, but there is no enforcement currently.  Key idea: if there is not
  // sufficient memory within old-gen to hold an object that wants to be promoted, defer promotion until a
  // subsequent evacuation pass.  Since enforcement may be expensive, requiring frequent synchronization
  // between mutator and GC threads, here's an alternative "greedy" mitigation strategy: Set the parameter's
  // value so overflow is "very rare".  In the case that we experience overflow, evacuate what we can from
  // within the old collection set, but don't evacuate everything.  At the end of evacuation, any collection
  // set region that was not fully evacuated cannot be recycled.  It becomes a prime candidate for the next
  // collection set selection.  Here, we'd rather fall back to this contingent behavior than force a full STW
  // collection.
  const size_t promotion_budget_bytes = (ShenandoahHeapRegion::region_size_bytes() / 2);

  // old_evacuation_budget is an upper bound on the amount of live memory that can be evacuated.
  //
  // If a region is put into the collection set, then this region's free (not yet used) bytes are no longer
  // "available" to hold the results of other evacuations.  This may cause a decrease in the remaining amount
  // of memory that can still be evacuated.  We address this by reducing the evacuation budget by the amount
  // of live memory in that region and by the amount of unallocated memory in that region if the evacuation
  // budget is constrained by availability of free memory.  See remaining_old_evacuation_budget below.

  // Allow no more evacuation than exists free-space within old-gen memory
  size_t old_evacuation_budget = ((_generation->available() > promotion_budget_bytes)
                                  ? _generation->available() - promotion_budget_bytes: 0);

  // But if the amount of available free space in old-gen memory exceeds the pacing bound on how much old-gen
  // memory can be evacuated during each evacuation pass, then cut the old-gen evacuation further.  The pacing
  // bound is designed to assure that old-gen evacuations to not excessively slow the evacuation pass in order
  // to assure that young-gen GC cadence is not disrupted.

  // excess_free_capacity represents availability of memory to hold evacuations beyond what is required to hold
  // planned evacuations.  It may go negative if we choose to collect regions with large amounts of free memory.
  long long excess_free_capacity;
  if (old_evacuation_budget > max_old_evacuation_bytes) {
    excess_free_capacity = old_evacuation_budget - max_old_evacuation_bytes;
    old_evacuation_budget = max_old_evacuation_bytes;
  } else
    excess_free_capacity = 0;

  log_info(gc)("Choose old regions for mixed collection: excess capacity: " SIZE_FORMAT "%s, evacuation budget: " SIZE_FORMAT "%s",
                byte_size_in_proper_unit((size_t) excess_free_capacity), proper_unit_for_byte_size(excess_free_capacity),
                byte_size_in_proper_unit(old_evacuation_budget), proper_unit_for_byte_size(old_evacuation_budget));

  size_t remaining_old_evacuation_budget = old_evacuation_budget;

  // The number of old-gen regions that were selected as candidates for collection at the end of the most recent
  // old-gen concurrent marking phase and have not yet been collected is represented by
  // unprocessed_old_collection_candidates()
  while (unprocessed_old_collection_candidates() > 0) {
    // Old collection candidates are sorted in order of decreasing garbage contained therein.
    ShenandoahHeapRegion* r = next_old_collection_candidate();

    // Assuming region r is added to the collection set, what will be the remaining_old_evacuation_budget after
    // accounting for the loss of region r's free() memory.
    size_t adjusted_remaining_old_evacuation_budget;

    // If we choose region r to be collected, then we need to decrease the capacity to hold other evacuations by
    // the size of r's free memory.
    excess_free_capacity -= r->free();
    // If subtracting r->free from excess_free_capacity() makes it go negative, that means we are going to have
    // to decrease the evacuation budget.
    if (excess_free_capacity < 0) {
      if (remaining_old_evacuation_budget < (size_t) -excess_free_capacity) {
        // By setting adjusted_remaining_old_evacuation_budget to 0, we prevent further additions to the old-gen
        // collection set, unless the region has zero live data bytes.
        adjusted_remaining_old_evacuation_budget = 0;
      } else {
        // Adding negative excess_free_capacity decreases the adjusted_remaining_old_evacuation_budget
        adjusted_remaining_old_evacuation_budget = remaining_old_evacuation_budget + excess_free_capacity;
      }
    } else {
      adjusted_remaining_old_evacuation_budget = remaining_old_evacuation_budget;
    }

    if (r->get_live_data_bytes() > adjusted_remaining_old_evacuation_budget) {
      break;
    }
    collection_set->add_region(r);
    included_old_regions++;
    evacuated_old_bytes += r->get_live_data_bytes();
    consume_old_collection_candidate();
    remaining_old_evacuation_budget = adjusted_remaining_old_evacuation_budget - r->get_live_data_bytes();
  }

  if (included_old_regions > 0) {
    log_info(gc)("Old-gen piggyback evac (" UINT32_FORMAT " regions, " SIZE_FORMAT " %s)",
                 included_old_regions, byte_size_in_proper_unit(evacuated_old_bytes), proper_unit_for_byte_size(evacuated_old_bytes));
  }
  return (included_old_regions > 0);
}

// Both arguments are don't cares for old-gen collections
bool ShenandoahOldHeuristics::choose_collection_set(ShenandoahCollectionSet* collection_set, ShenandoahOldHeuristics* old_heuristics) {
  // Old-gen doesn't actually choose a collection set to be evacuated by its own gang of worker tasks.
  // Instead, it computes the set of regions to be evacuated by subsequent young-gen evacuation passes.
  prepare_for_old_collections();
  return false;
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


uint ShenandoahOldHeuristics::unprocessed_old_collection_candidates() {
  assert(_generation->generation_mode() == OLD, "This service only available for old-gc heuristics");
  return _old_collection_candidates + _hidden_old_collection_candidates;
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
  if (unprocessed_old_collection_candidates() > 0) {
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

