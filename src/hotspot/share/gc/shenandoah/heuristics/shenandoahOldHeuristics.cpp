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

ShenandoahOldHeuristics::ShenandoahOldHeuristics(ShenandoahGeneration* generation) :
    ShenandoahHeuristics(generation),
    _old_collection_candidates(0),
    _next_old_collection_candidate(0),
    _hidden_old_collection_candidates(0),
    _hidden_next_old_collection_candidate(0),
    _old_coalesce_and_fill_candidates(0),
    _first_coalesce_and_fill_candidate(0)
{
}

void ShenandoahOldHeuristics::prime_collection_set(ShenandoahCollectionSet* collection_set) {
  uint included_old_regions = 0;
  size_t evacuated_old_bytes = 0;

  // TODO:
  // The max_old_evacuation_bytes and promotion_budget_bytes constants represent a first
  // approximation to desired operating parameters.  Eventually, these values should be determined
  // by heuristics and should adjust dynamically based on most current execution behavior.  In the
  // interrim, we may choose to offer command-line options to set the values of these configuration
  // parameters.

  // max_old_evacuation_bytes represents an "arbitrary" bound on how much evacuation effort is dedicated
  // to old-gen regions.
  const size_t max_old_evacuation_bytes = (ShenandoahHeapRegion::region_size_bytes() * 8);

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
    log_info(gc)("Old-gen piggyback evac (%llu regions, %llu bytes)",
                 (unsigned long long) included_old_regions,
                 (unsigned long long) evacuated_old_bytes);
  }
}


void ShenandoahOldHeuristics::choose_collection_set(ShenandoahCollectionSet* collection_set, ShenandoahOldHeuristics* old_heuristics) {
  assert(collection_set->count() == 0, "Must be empty");

  // Old-gen doesn't actually choose a collection set to be evacuated by its own gang of worker tasks.
  // Instead, it computes the set of regions to be evacuated by subsequent young-gen evacuation passes.
  prepare_for_old_collections();
}

void ShenandoahOldHeuristics::prepare_for_old_collections() {
  assert(_generation->generation_mode() == OLD, "This service only available for old-gc heuristics");
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  uint free_regions = 0;
  size_t cand_idx = 0;
  size_t total_garbage = 0;
  size_t num_regions = heap->num_regions();

  RegionData* candidates = _region_data;
  for (size_t i = 0; i < num_regions; i++) {
    ShenandoahHeapRegion* region = heap->get_region(i);
    if (!in_generation(region)) {
      continue;
    } else {
      size_t garbage = region->garbage();
      total_garbage += garbage;

      candidates[cand_idx]._region = region;
      candidates[cand_idx]._garbage = garbage;
      cand_idx++;
    }
  }

  // Give special treatment to humongous regions.  Assume humongous regions is entirely
  // garbage or entirely non-garbage.  Assume that a head humongous region and the associated
  // humongous continuous regions are uniformly entirely garbage or entirely non-garbage.
  //
  // Sift garbage humongous regions to front, non-garbage humongous regions to end of array.
  size_t first_non_humongous_empty = 0;
  size_t first_humongous_non_empty = cand_idx;

  size_t i = 0;
  while (i < first_humongous_non_empty) {
    ShenandoahHeapRegion* region = candidates[i]._region;
    if (region->is_humongous()) {
      if (region->get_live_data_bytes() == 0) {
        // Humongous region is entirely garbage.  Reclaim it.
        if (i == first_non_humongous_empty) {
          first_non_humongous_empty++;
        } else {
          RegionData swap_tmp = candidates[i];
          candidates[i] = candidates[first_non_humongous_empty];
          candidates[first_non_humongous_empty++] = swap_tmp;
        }
        i++;
      } else {
        // Humongous region is non garbage.  Don't reclaim it.
        if (i + 1 == first_humongous_non_empty) {
          first_humongous_non_empty--;
          i++;
        } else {
          RegionData swap_tmp = candidates[i];
          candidates[i] = candidates[--first_humongous_non_empty];
          candidates[first_humongous_non_empty] = swap_tmp;
          // Do not increment i so we can revisit swapped entry on next iteration
        }
      }
    } else {
      i++;
    }
  }


  // Prioritize regions to select garbage-first regions
  QuickSort::sort<RegionData>(candidates + first_non_humongous_empty, (int)(first_humongous_non_empty - first_non_humongous_empty),
                              compare_by_garbage, false);

  // Any old-gen region that contains (ShenandoahGarbageThreshold (default value 25))% garbage or more is to
  // be evacuated.
  //
  // TODO: it probably makes sense to define old-generation collection_threshold_garbage_percent differently
  // than the young-gen ShenandoahGarbageThreshold.  So a different command-line option might allow specification
  // distinct values for each.  Even better, allow collection_threshold_garbage_percent to be determined
  // adaptively, by heuristics.

  const size_t collection_threshold_garbage_percent = ShenandoahGarbageThreshold;

  size_t region_size = ShenandoahHeapRegion::region_size_bytes();
  for (size_t i = first_non_humongous_empty; i < first_humongous_non_empty; i++) {
    // Do approximate percent to avoid floating point math
    size_t percent_garbage = candidates[i]._garbage * 100 / region_size;

    if (percent_garbage < collection_threshold_garbage_percent) {
      _hidden_next_old_collection_candidate = 0;
      _hidden_old_collection_candidates = (uint)i;
      _first_coalesce_and_fill_candidate = (uint)i;
      _old_coalesce_and_fill_candidates = (uint)(cand_idx - i);

      // Note that we do not coalesce and fill occupied humongous regions
      // HR: humongous regions, RR: regular regions, CF: coalesce and fill regions
      log_info(gc)("Old-gen mark evac (%u HR, %llu RR), %llu CF)",
                   (unsigned int) first_non_humongous_empty,
                   (unsigned long long) (_hidden_old_collection_candidates - first_non_humongous_empty),
                   (unsigned long long) _old_coalesce_and_fill_candidates);
      return;
    }
  }

  // If we reach here, all of non-humogous old-gen regions are candidates for collection set.
  _hidden_next_old_collection_candidate = 0;
  _hidden_old_collection_candidates = (uint)first_humongous_non_empty;
  _first_coalesce_and_fill_candidate = 0;
  _old_coalesce_and_fill_candidates = 0;

  // Note that we do not coalesce and fill occupied humongous regions
  // HR: humongous regions, RR: regular regions, CF: coalesce and fill regions
  log_info(gc)("Old-gen mark evac (%u HR, %llu RR), %llu CF)",
               (unsigned int) first_non_humongous_empty,
               (unsigned long long) (_hidden_old_collection_candidates - first_non_humongous_empty),
               (unsigned long long) _old_coalesce_and_fill_candidates);
}

void ShenandoahOldHeuristics::start_old_evacuations() {
  assert(_generation->generation_mode() == OLD, "This service only available for old-gc heuristics");

  _old_collection_candidates = _hidden_old_collection_candidates;
  _next_old_collection_candidate = _hidden_next_old_collection_candidate;

  _hidden_old_collection_candidates = 0;}


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

bool ShenandoahOldHeuristics::should_defer_gc() {
  if (unprocessed_old_collection_candidates() > 0) {
    // Cannot start a new old-gen GC until previous one has finished.
    //
    // Future refinement: under certain circumstances, we might be more sophisticated about this choice.
    // For example, we could choose to abandon the prevoius old collection before it has completed evacuations,
    // but this would require that we coalesce and fill all garbage within unevacuated collection-set regions.
    return true;
  }
  return false;
}

void ShenandoahOldHeuristics::abandon_collection_candidates() {
  _old_collection_candidates = 0;
  _next_old_collection_candidate = 0;
  _hidden_old_collection_candidates = 0;
  _hidden_next_old_collection_candidate = 0;
  _old_coalesce_and_fill_candidates = 0;
  _first_coalesce_and_fill_candidate = 0;
}

