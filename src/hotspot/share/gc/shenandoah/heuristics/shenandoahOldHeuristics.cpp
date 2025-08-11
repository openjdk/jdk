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

#include "gc/shenandoah/heuristics/shenandoahOldHeuristics.hpp"
#include "gc/shenandoah/shenandoahCollectionSet.hpp"
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahGenerationalHeap.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "logging/log.hpp"
#include "utilities/quickSort.hpp"

uint ShenandoahOldHeuristics::NOT_FOUND = -1U;

// sort by increasing live (so least live comes first)
int ShenandoahOldHeuristics::compare_by_live(RegionData a, RegionData b) {
  if (a.get_livedata() < b.get_livedata()) {
    return -1;
  } else if (a.get_livedata() > b.get_livedata()) {
    return 1;
  } else {
    return 0;
  }
}

// sort by increasing index
int ShenandoahOldHeuristics::compare_by_index(RegionData a, RegionData b) {
  if (a.get_region()->index() < b.get_region()->index()) {
    return -1;
  } else if (a.get_region()->index() > b.get_region()->index()) {
    return 1;
  } else {
    // quicksort may compare to self during search for pivot
    return 0;
  }
}

ShenandoahOldHeuristics::ShenandoahOldHeuristics(ShenandoahOldGeneration* generation, ShenandoahGenerationalHeap* gen_heap) :
  ShenandoahHeuristics(generation),
  _heap(gen_heap),
  _first_pinned_candidate(NOT_FOUND),
  _last_old_collection_candidate(0),
  _next_old_collection_candidate(0),
  _last_old_region(0),
  _live_bytes_in_unprocessed_candidates(0),
  _old_generation(generation),
  _cannot_expand_trigger(false),
  _fragmentation_trigger(false),
  _growth_trigger(false),
  _fragmentation_density(0.0),
  _fragmentation_first_old_region(0),
  _fragmentation_last_old_region(0)
{
}

bool ShenandoahOldHeuristics::prime_collection_set(ShenandoahCollectionSet* collection_set) {
  if (unprocessed_old_collection_candidates() == 0) {
    return false;
  }

  if (_old_generation->is_preparing_for_mark()) {
    // We have unprocessed old collection candidates, but the heuristic has given up on evacuating them.
    // This is most likely because they were _all_ pinned at the time of the last mixed evacuation (and
    // this in turn is most likely because there are just one or two candidate regions remaining).
    log_info(gc, ergo)("Remaining " UINT32_FORMAT " old regions are being coalesced and filled", unprocessed_old_collection_candidates());
    return false;
  }

  _first_pinned_candidate = NOT_FOUND;

  uint included_old_regions = 0;
  size_t evacuated_old_bytes = 0;
  size_t collected_old_bytes = 0;

  // If a region is put into the collection set, then this region's free (not yet used) bytes are no longer
  // "available" to hold the results of other evacuations.  This may cause a decrease in the remaining amount
  // of memory that can still be evacuated.  We address this by reducing the evacuation budget by the amount
  // of live memory in that region and by the amount of unallocated memory in that region if the evacuation
  // budget is constrained by availability of free memory.
  const size_t old_evacuation_reserve = _old_generation->get_evacuation_reserve();
  const size_t old_evacuation_budget = (size_t) ((double) old_evacuation_reserve / ShenandoahOldEvacWaste);
  size_t unfragmented_available = _old_generation->free_unaffiliated_regions() * ShenandoahHeapRegion::region_size_bytes();
  size_t fragmented_available;
  size_t excess_fragmented_available;

  if (unfragmented_available > old_evacuation_budget) {
    unfragmented_available = old_evacuation_budget;
    fragmented_available = 0;
    excess_fragmented_available = 0;
  } else {
    assert(_old_generation->available() >= old_evacuation_budget, "Cannot budget more than is available");
    fragmented_available = _old_generation->available() - unfragmented_available;
    assert(fragmented_available + unfragmented_available >= old_evacuation_budget, "Budgets do not add up");
    if (fragmented_available + unfragmented_available > old_evacuation_budget) {
      excess_fragmented_available = (fragmented_available + unfragmented_available) - old_evacuation_budget;
      fragmented_available -= excess_fragmented_available;
    }
  }

  size_t remaining_old_evacuation_budget = old_evacuation_budget;
  log_debug(gc)("Choose old regions for mixed collection: old evacuation budget: %zu%s, candidates: %u",
                byte_size_in_proper_unit(old_evacuation_budget), proper_unit_for_byte_size(old_evacuation_budget),
                unprocessed_old_collection_candidates());

  size_t lost_evacuation_capacity = 0;

  // The number of old-gen regions that were selected as candidates for collection at the end of the most recent old-gen
  // concurrent marking phase and have not yet been collected is represented by unprocessed_old_collection_candidates().
  // Candidate regions are ordered according to increasing amount of live data.  If there is not sufficient room to
  // evacuate region N, then there is no need to even consider evacuating region N+1.
  while (unprocessed_old_collection_candidates() > 0) {
    // Old collection candidates are sorted in order of decreasing garbage contained therein.
    ShenandoahHeapRegion* r = next_old_collection_candidate();
    if (r == nullptr) {
      break;
    }
    assert(r->is_regular(), "There should be no humongous regions in the set of mixed-evac candidates");

    // If region r is evacuated to fragmented memory (to free memory within a partially used region), then we need
    // to decrease the capacity of the fragmented memory by the scaled loss.

    size_t live_data_for_evacuation = r->get_live_data_bytes();
    size_t lost_available = r->free();

    if ((lost_available > 0) && (excess_fragmented_available > 0)) {
      if (lost_available < excess_fragmented_available) {
        excess_fragmented_available -= lost_available;
        lost_evacuation_capacity -= lost_available;
        lost_available  = 0;
      } else {
        lost_available -= excess_fragmented_available;
        lost_evacuation_capacity -= excess_fragmented_available;
        excess_fragmented_available = 0;
      }
    }
    size_t scaled_loss = (size_t) ((double) lost_available / ShenandoahOldEvacWaste);
    if ((lost_available > 0) && (fragmented_available > 0)) {
      if (scaled_loss + live_data_for_evacuation < fragmented_available) {
        fragmented_available -= scaled_loss;
        scaled_loss = 0;
      } else {
        // We will have to allocate this region's evacuation memory from unfragmented memory, so don't bother
        // to decrement scaled_loss
      }
    }
    if (scaled_loss > 0) {
      // We were not able to account for the lost free memory within fragmented memory, so we need to take this
      // allocation out of unfragmented memory.  Unfragmented memory does not need to account for loss of free.
      if (live_data_for_evacuation > unfragmented_available) {
        // There is not room to evacuate this region or any that come after it in within the candidates array.
        break;
      } else {
        unfragmented_available -= live_data_for_evacuation;
      }
    } else {
      // Since scaled_loss == 0, we have accounted for the loss of free memory, so we can allocate from either
      // fragmented or unfragmented available memory.  Use up the fragmented memory budget first.
      size_t evacuation_need = live_data_for_evacuation;

      if (evacuation_need > fragmented_available) {
        evacuation_need -= fragmented_available;
        fragmented_available = 0;
      } else {
        fragmented_available -= evacuation_need;
        evacuation_need = 0;
      }
      if (evacuation_need > unfragmented_available) {
        // There is not room to evacuate this region or any that come after it in within the candidates array.
        break;
      } else {
        unfragmented_available -= evacuation_need;
        // dead code: evacuation_need == 0;
      }
    }
    collection_set->add_region(r);
    included_old_regions++;
    evacuated_old_bytes += live_data_for_evacuation;
    collected_old_bytes += r->garbage();
    consume_old_collection_candidate();
  }

  if (_first_pinned_candidate != NOT_FOUND) {
    // Need to deal with pinned regions
    slide_pinned_regions_to_front();
  }
  decrease_unprocessed_old_collection_candidates_live_memory(evacuated_old_bytes);
  if (included_old_regions > 0) {
    log_info(gc, ergo)("Old-gen piggyback evac (" UINT32_FORMAT " regions, evacuating " PROPERFMT ", reclaiming: " PROPERFMT ")",
                  included_old_regions, PROPERFMTARGS(evacuated_old_bytes), PROPERFMTARGS(collected_old_bytes));
  }

  if (unprocessed_old_collection_candidates() == 0) {
    // We have added the last of our collection candidates to a mixed collection.
    // Any triggers that occurred during mixed evacuations may no longer be valid.  They can retrigger if appropriate.
    clear_triggers();

    _old_generation->complete_mixed_evacuations();
  } else if (included_old_regions == 0) {
    // We have candidates, but none were included for evacuation - are they all pinned?
    // or did we just not have enough room for any of them in this collection set?
    // We don't want a region with a stuck pin to prevent subsequent old collections, so
    // if they are all pinned we transition to a state that will allow us to make these uncollected
    // (pinned) regions parsable.
    if (all_candidates_are_pinned()) {
      log_info(gc, ergo)("All candidate regions " UINT32_FORMAT " are pinned", unprocessed_old_collection_candidates());
      _old_generation->abandon_mixed_evacuations();
    } else {
      log_info(gc, ergo)("No regions selected for mixed collection. "
                         "Old evacuation budget: " PROPERFMT ", Remaining evacuation budget: " PROPERFMT
                         ", Lost capacity: " PROPERFMT
                         ", Next candidate: " UINT32_FORMAT ", Last candidate: " UINT32_FORMAT,
                         PROPERFMTARGS(old_evacuation_reserve),
                         PROPERFMTARGS(remaining_old_evacuation_budget),
                         PROPERFMTARGS(lost_evacuation_capacity),
                         _next_old_collection_candidate, _last_old_collection_candidate);
    }
  }

  return (included_old_regions > 0);
}

bool ShenandoahOldHeuristics::all_candidates_are_pinned() {
#ifdef ASSERT
  if (uint(os::random()) % 100 < ShenandoahCoalesceChance) {
    return true;
  }
#endif

  for (uint i = _next_old_collection_candidate; i < _last_old_collection_candidate; ++i) {
    ShenandoahHeapRegion* region = _region_data[i].get_region();
    if (!region->is_pinned()) {
      return false;
    }
  }
  return true;
}

void ShenandoahOldHeuristics::slide_pinned_regions_to_front() {
  // Find the first unpinned region to the left of the next region that
  // will be added to the collection set. These regions will have been
  // added to the cset, so we can use them to hold pointers to regions
  // that were pinned when the cset was chosen.
  // [ r p r p p p r r ]
  //     ^         ^ ^
  //     |         | | pointer to next region to add to a mixed collection is here.
  //     |         | first r to the left should be in the collection set now.
  //     | first pinned region, we don't need to look past this
  uint write_index = NOT_FOUND;
  for (uint search = _next_old_collection_candidate - 1; search > _first_pinned_candidate; --search) {
    ShenandoahHeapRegion* region = _region_data[search].get_region();
    if (!region->is_pinned()) {
      write_index = search;
      assert(region->is_cset(), "Expected unpinned region to be added to the collection set.");
      break;
    }
  }

  // If we could not find an unpinned region, it means there are no slots available
  // to move up the pinned regions. In this case, we just reset our next index in the
  // hopes that some of these regions will become unpinned before the next mixed
  // collection. We may want to bailout of here instead, as it should be quite
  // rare to have so many pinned regions and may indicate something is wrong.
  if (write_index == NOT_FOUND) {
    assert(_first_pinned_candidate != NOT_FOUND, "Should only be here if there are pinned regions.");
    _next_old_collection_candidate = _first_pinned_candidate;
    return;
  }

  // Find pinned regions to the left and move their pointer into a slot
  // that was pointing at a region that has been added to the cset (or was pointing
  // to a pinned region that we've already moved up). We are done when the leftmost
  // pinned region has been slid up.
  // [ r p r x p p p r ]
  //         ^       ^
  //         |       | next region for mixed collections
  //         | Write pointer is here. We know this region is already in the cset
  //         | so we can clobber it with the next pinned region we find.
  for (int32_t search = (int32_t)write_index - 1; search >= (int32_t)_first_pinned_candidate; --search) {
    RegionData& skipped = _region_data[search];
    if (skipped.get_region()->is_pinned()) {
      RegionData& available_slot = _region_data[write_index];
      available_slot.set_region_and_livedata(skipped.get_region(), skipped.get_livedata());
      --write_index;
    }
  }

  // Update to read from the leftmost pinned region. Plus one here because we decremented
  // the write index to hold the next found pinned region. We are just moving it back now
  // to point to the first pinned region.
  _next_old_collection_candidate = write_index + 1;
}

void ShenandoahOldHeuristics::prepare_for_old_collections() {
  ShenandoahHeap* heap = ShenandoahHeap::heap();

  const size_t num_regions = heap->num_regions();
  size_t cand_idx = 0;
  size_t immediate_garbage = 0;
  size_t immediate_regions = 0;
  size_t live_data = 0;

  RegionData* candidates = _region_data;
  for (size_t i = 0; i < num_regions; i++) {
    ShenandoahHeapRegion* region = heap->get_region(i);
    if (!region->is_old()) {
      continue;
    }

    size_t garbage = region->garbage();
    size_t live_bytes = region->get_live_data_bytes();
    live_data += live_bytes;

    if (region->is_regular() || region->is_regular_pinned()) {
        // Only place regular or pinned regions with live data into the candidate set.
        // Pinned regions cannot be evacuated, but we are not actually choosing candidates
        // for the collection set here. That happens later during the next young GC cycle,
        // by which time, the pinned region may no longer be pinned.
      if (!region->has_live()) {
        assert(!region->is_pinned(), "Pinned region should have live (pinned) objects.");
        region->make_trash_immediate();
        immediate_regions++;
        immediate_garbage += garbage;
      } else {
        region->begin_preemptible_coalesce_and_fill();
        candidates[cand_idx].set_region_and_livedata(region, live_bytes);
        cand_idx++;
      }
    } else if (region->is_humongous_start()) {
      // This will handle humongous start regions whether they are also pinned, or not.
      // If they are pinned, we expect them to hold live data, so they will not be
      // turned into immediate garbage.
      if (!region->has_live()) {
        assert(!region->is_pinned(), "Pinned region should have live (pinned) objects.");
        // The humongous object is dead, we can just return this region and the continuations
        // immediately to the freeset - no evacuations are necessary here. The continuations
        // will be made into trash by this method, so they'll be skipped by the 'is_regular'
        // check above, but we still need to count the start region.
        immediate_regions++;
        immediate_garbage += garbage;
        size_t region_count = heap->trash_humongous_region_at(region);
        log_debug(gc)("Trashed %zu regions for humongous object.", region_count);
      }
    } else if (region->is_trash()) {
      // Count humongous objects made into trash here.
      immediate_regions++;
      immediate_garbage += garbage;
    }
  }

  _old_generation->set_live_bytes_after_last_mark(live_data);

  // Unlike young, we are more interested in efficiently packing OLD-gen than in reclaiming garbage first.  We sort by live-data.
  // Some regular regions may have been promoted in place with no garbage but also with very little live data.  When we "compact"
  // old-gen, we want to pack these underutilized regions together so we can have more unaffiliated (unfragmented) free regions
  // in old-gen.

  QuickSort::sort<RegionData>(candidates, cand_idx, compare_by_live);

  const size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();

  // The convention is to collect regions that have more than this amount of garbage.
  const size_t garbage_threshold = region_size_bytes * ShenandoahOldGarbageThreshold / 100;

  // Enlightened interpretation: collect regions that have less than this amount of live.
  const size_t live_threshold = region_size_bytes - garbage_threshold;

  _last_old_region = (uint)cand_idx;
  _last_old_collection_candidate = (uint)cand_idx;
  _next_old_collection_candidate = 0;

  size_t unfragmented = 0;
  size_t candidates_garbage = 0;

  for (size_t i = 0; i < cand_idx; i++) {
    size_t live = candidates[i].get_livedata();
    if (live > live_threshold) {
      // Candidates are sorted in increasing order of live data, so no regions after this will be below the threshold.
      _last_old_collection_candidate = (uint)i;
      break;
    }
    ShenandoahHeapRegion* r = candidates[i].get_region();
    size_t region_garbage = r->garbage();
    size_t region_free = r->free();
    candidates_garbage += region_garbage;
    unfragmented += region_free;
  }

  // defrag_count represents regions that are placed into the old collection set in order to defragment the memory
  // that we try to "reserve" for humongous allocations.
  size_t defrag_count = 0;
  size_t total_uncollected_old_regions = _last_old_region - _last_old_collection_candidate;

  if (cand_idx > _last_old_collection_candidate) {
    // Above, we have added into the set of mixed-evacuation candidates all old-gen regions for which the live memory
    // that they contain is below a particular old-garbage threshold.  Regions that were not selected for the collection
    // set hold enough live memory that it is not considered efficient (by "garbage-first standards") to compact these
    // at the current time.
    //
    // However, if any of these regions that were rejected from the collection set reside within areas of memory that
    // might interfere with future humongous allocation requests, we will prioritize them for evacuation at this time.
    // Humongous allocations target the bottom of the heap.  We want old-gen regions to congregate at the top of the
    // heap.
    //
    // Sort the regions that were initially rejected from the collection set in order of index.  This allows us to
    // focus our attention on the regions that have low index value (i.e. the old-gen regions at the bottom of the heap).
    QuickSort::sort<RegionData>(candidates + _last_old_collection_candidate, cand_idx - _last_old_collection_candidate,
                                compare_by_index);

    const size_t first_unselected_old_region = candidates[_last_old_collection_candidate].get_region()->index();
    const size_t last_unselected_old_region = candidates[cand_idx - 1].get_region()->index();
    size_t span_of_uncollected_regions = 1 + last_unselected_old_region - first_unselected_old_region;

    // Add no more than 1/8 of the existing old-gen regions to the set of mixed evacuation candidates.
    const int MAX_FRACTION_OF_HUMONGOUS_DEFRAG_REGIONS = 8;
    const size_t bound_on_additional_regions = cand_idx / MAX_FRACTION_OF_HUMONGOUS_DEFRAG_REGIONS;

    // The heuristic old_is_fragmented trigger may be seeking to achieve up to 75% density.  Allow ourselves to overshoot
    // that target (at 7/8) so we will not have to do another defragmenting old collection right away.
    while ((defrag_count < bound_on_additional_regions) &&
           (total_uncollected_old_regions < 7 * span_of_uncollected_regions / 8)) {
      ShenandoahHeapRegion* r = candidates[_last_old_collection_candidate].get_region();
      assert(r->is_regular() || r->is_regular_pinned(), "Region %zu has wrong state for collection: %s",
             r->index(), ShenandoahHeapRegion::region_state_to_string(r->state()));
      const size_t region_garbage = r->garbage();
      const size_t region_free = r->free();
      candidates_garbage += region_garbage;
      unfragmented += region_free;
      defrag_count++;
      _last_old_collection_candidate++;

      // We now have one fewer uncollected regions, and our uncollected span shrinks because we have removed its first region.
      total_uncollected_old_regions--;
      span_of_uncollected_regions =
        1 + last_unselected_old_region - candidates[_last_old_collection_candidate].get_region()->index();
    }
  }

  // Note that we do not coalesce and fill occupied humongous regions
  // HR: humongous regions, RR: regular regions, CF: coalesce and fill regions
  const size_t collectable_garbage = immediate_garbage + candidates_garbage;
  const size_t old_candidates = _last_old_collection_candidate;
  const size_t mixed_evac_live = old_candidates * region_size_bytes - (candidates_garbage + unfragmented);
  set_unprocessed_old_collection_candidates_live_memory(mixed_evac_live);

  log_info(gc, ergo)("Old-Gen Collectable Garbage: " PROPERFMT " consolidated with free: " PROPERFMT ", over %zu regions",
                     PROPERFMTARGS(collectable_garbage), PROPERFMTARGS(unfragmented), old_candidates);
  log_info(gc, ergo)("Old-Gen Immediate Garbage: " PROPERFMT " over %zu regions",
                     PROPERFMTARGS(immediate_garbage), immediate_regions);
  log_info(gc, ergo)("Old regions selected for defragmentation: %zu", defrag_count);
  log_info(gc, ergo)("Old regions not selected: %zu", total_uncollected_old_regions);

  if (unprocessed_old_collection_candidates() > 0) {
    _old_generation->transition_to(ShenandoahOldGeneration::EVACUATING);
  } else if (has_coalesce_and_fill_candidates()) {
    _old_generation->transition_to(ShenandoahOldGeneration::FILLING);
  } else {
    _old_generation->transition_to(ShenandoahOldGeneration::WAITING_FOR_BOOTSTRAP);
  }
}

size_t ShenandoahOldHeuristics::unprocessed_old_collection_candidates_live_memory() const {
  return _live_bytes_in_unprocessed_candidates;
}

void ShenandoahOldHeuristics::set_unprocessed_old_collection_candidates_live_memory(size_t initial_live) {
  _live_bytes_in_unprocessed_candidates = initial_live;
}

void ShenandoahOldHeuristics::decrease_unprocessed_old_collection_candidates_live_memory(size_t evacuated_live) {
  assert(evacuated_live <= _live_bytes_in_unprocessed_candidates, "Cannot evacuate more than was present");
  _live_bytes_in_unprocessed_candidates -= evacuated_live;
}

// Used by unit test: test_shenandoahOldHeuristic.cpp
uint ShenandoahOldHeuristics::last_old_collection_candidate_index() const {
  return _last_old_collection_candidate;
}

uint ShenandoahOldHeuristics::unprocessed_old_collection_candidates() const {
  return _last_old_collection_candidate - _next_old_collection_candidate;
}

ShenandoahHeapRegion* ShenandoahOldHeuristics::next_old_collection_candidate() {
  while (_next_old_collection_candidate < _last_old_collection_candidate) {
    ShenandoahHeapRegion* next = _region_data[_next_old_collection_candidate].get_region();
    if (!next->is_pinned()) {
      return next;
    } else {
      assert(next->is_pinned(), "sanity");
      if (_first_pinned_candidate == NOT_FOUND) {
        _first_pinned_candidate = _next_old_collection_candidate;
      }
    }

    _next_old_collection_candidate++;
  }
  return nullptr;
}

void ShenandoahOldHeuristics::consume_old_collection_candidate() {
  _next_old_collection_candidate++;
}

unsigned int ShenandoahOldHeuristics::get_coalesce_and_fill_candidates(ShenandoahHeapRegion** buffer) {
  uint end = _last_old_region;
  uint index = _next_old_collection_candidate;
  while (index < end) {
    *buffer++ = _region_data[index++].get_region();
  }
  return (_last_old_region - _next_old_collection_candidate);
}

void ShenandoahOldHeuristics::abandon_collection_candidates() {
  _last_old_collection_candidate = 0;
  _next_old_collection_candidate = 0;
  _last_old_region = 0;
}

void ShenandoahOldHeuristics::record_cycle_end() {
  this->ShenandoahHeuristics::record_cycle_end();
  clear_triggers();
}

void ShenandoahOldHeuristics::clear_triggers() {
  // Clear any triggers that were set during mixed evacuations.  Conditions may be different now that this phase has finished.
  _cannot_expand_trigger = false;
  _fragmentation_trigger = false;
  _growth_trigger = false;
}

// This triggers old-gen collection if the number of regions "dedicated" to old generation is much larger than
// is required to represent the memory currently used within the old generation.  This trigger looks specifically
// at density of the old-gen spanned region.  A different mechanism triggers old-gen GC if the total number of
// old-gen regions (regardless of how close the regions are to one another) grows beyond an anticipated growth target.
void ShenandoahOldHeuristics::set_trigger_if_old_is_fragmented(size_t first_old_region, size_t last_old_region,
                                                               size_t old_region_count, size_t num_regions) {
  if (ShenandoahGenerationalHumongousReserve > 0) {
    // Our intent is to pack old-gen memory into the highest-numbered regions of the heap.  Count all memory
    // above first_old_region as the "span" of old generation.
    size_t old_region_span = (first_old_region <= last_old_region)? (num_regions - first_old_region): 0;
    // Given that memory at the bottom of the heap is reserved to represent humongous objects, the number of
    // regions that old_gen is "allowed" to consume is less than the total heap size.  The restriction on allowed
    // span is not strictly enforced.  This is a heuristic designed to reduce the likelihood that a humongous
    // allocation request will require a STW full GC.
    size_t allowed_old_gen_span = num_regions - (ShenandoahGenerationalHumongousReserve * num_regions) / 100;

    size_t old_available = _old_generation->available() / HeapWordSize;
    size_t region_size_words = ShenandoahHeapRegion::region_size_words();
    size_t old_unaffiliated_available = _old_generation->free_unaffiliated_regions() * region_size_words;
    assert(old_available >= old_unaffiliated_available, "sanity");
    size_t old_fragmented_available = old_available - old_unaffiliated_available;

    size_t old_words_consumed = old_region_count * region_size_words - old_fragmented_available;
    size_t old_words_spanned = old_region_span * region_size_words;
    double old_density = ((double) old_words_consumed) / old_words_spanned;

    double old_span_percent = ((double) old_region_span) / allowed_old_gen_span;
    if (old_span_percent > 0.50) {
      // Squaring old_span_percent in the denominator below allows more aggressive triggering when we are
      // above desired maximum span and less aggressive triggering when we are far below the desired maximum span.
      double old_span_percent_squared = old_span_percent * old_span_percent;
      if (old_density / old_span_percent_squared < 0.75) {
        // We trigger old defragmentation, for example, if:
        //  old_span_percent is 110% and old_density is below 90.8%, or
        //  old_span_percent is 100% and old_density is below 75.0%, or
        //  old_span_percent is  90% and old_density is below 60.8%, or
        //  old_span_percent is  80% and old_density is below 48.0%, or
        //  old_span_percent is  70% and old_density is below 36.8%, or
        //  old_span_percent is  60% and old_density is below 27.0%, or
        //  old_span_percent is  50% and old_density is below 18.8%.

        // Set the fragmentation trigger and related attributes
        _fragmentation_trigger = true;
        _fragmentation_density = old_density;
        _fragmentation_first_old_region = first_old_region;
        _fragmentation_last_old_region = last_old_region;
      }
    }
  }
}

void ShenandoahOldHeuristics::set_trigger_if_old_is_overgrown() {
  size_t old_used = _old_generation->used() + _old_generation->get_humongous_waste();
  size_t trigger_threshold = _old_generation->usage_trigger_threshold();
  // Detects unsigned arithmetic underflow
  assert(old_used <= _heap->capacity(),
         "Old used (%zu, %zu) must not be more than heap capacity (%zu)",
         _old_generation->used(), _old_generation->get_humongous_waste(), _heap->capacity());
  if (old_used > trigger_threshold) {
    _growth_trigger = true;
  }
}

void ShenandoahOldHeuristics::evaluate_triggers(size_t first_old_region, size_t last_old_region,
                                                size_t old_region_count, size_t num_regions) {
  set_trigger_if_old_is_fragmented(first_old_region, last_old_region, old_region_count, num_regions);
  set_trigger_if_old_is_overgrown();
}

bool ShenandoahOldHeuristics::should_resume_old_cycle() {
  // If we are preparing to mark old, or if we are already marking old, then try to continue that work.
  if (_old_generation->is_concurrent_mark_in_progress()) {
    assert(_old_generation->state() == ShenandoahOldGeneration::MARKING, "Unexpected old gen state: %s", _old_generation->state_name());
    log_trigger("Resume marking old");
    return true;
  }

  if (_old_generation->is_preparing_for_mark()) {
    assert(_old_generation->state() == ShenandoahOldGeneration::FILLING, "Unexpected old gen state: %s", _old_generation->state_name());
    log_trigger("Resume preparing to mark old");
    return true;
  }

  return false;
}

bool ShenandoahOldHeuristics::should_start_gc() {

  const ShenandoahHeap* heap = ShenandoahHeap::heap();
  if (!_old_generation->is_idle()) {
    // Do not try to start an old cycle if old-gen is marking, doing mixed evacuations, or coalescing and filling.
    log_debug(gc)("Not starting an old cycle because old gen is busy");
    return false;
  }

  if (_cannot_expand_trigger) {
    const size_t old_gen_capacity = _old_generation->max_capacity();
    const size_t heap_capacity = heap->capacity();
    const double percent = percent_of(old_gen_capacity, heap_capacity);
    log_trigger("Expansion failure, current size: %zu%s which is %.1f%% of total heap size",
                 byte_size_in_proper_unit(old_gen_capacity), proper_unit_for_byte_size(old_gen_capacity), percent);
    return true;
  }

  if (_fragmentation_trigger) {
    const size_t used = _old_generation->used();
    const size_t used_regions_size = _old_generation->used_regions_size();

    // used_regions includes humongous regions
    const size_t used_regions = _old_generation->used_regions();
    assert(used_regions_size > used_regions, "Cannot have more used than used regions");

    size_t first_old_region, last_old_region;
    double density;
    get_fragmentation_trigger_reason_for_log_message(density, first_old_region, last_old_region);
    const size_t span_of_old_regions = (last_old_region >= first_old_region)? last_old_region + 1 - first_old_region: 0;
    const size_t fragmented_free = used_regions_size - used;

    log_trigger("Old has become fragmented: "
                "%zu%s available bytes spread between range spanned from "
                "%zu to %zu (%zu), density: %.1f%%",
                byte_size_in_proper_unit(fragmented_free), proper_unit_for_byte_size(fragmented_free),
                first_old_region, last_old_region, span_of_old_regions, density * 100);
    return true;
  }

  if (_growth_trigger) {
    // Growth may be falsely triggered during mixed evacuations, before the mixed-evacuation candidates have been
    // evacuated.  Before acting on a false trigger, we check to confirm the trigger condition is still satisfied.
    const size_t current_usage = _old_generation->used() + _old_generation->get_humongous_waste();
    const size_t trigger_threshold = _old_generation->usage_trigger_threshold();
    const size_t heap_size = heap->capacity();
    const size_t ignore_threshold = (ShenandoahIgnoreOldGrowthBelowPercentage * heap_size) / 100;
    size_t consecutive_young_cycles;
    if ((current_usage < ignore_threshold) &&
        ((consecutive_young_cycles = heap->shenandoah_policy()->consecutive_young_gc_count())
         < ShenandoahDoNotIgnoreGrowthAfterYoungCycles)) {
      log_debug(gc)("Ignoring Trigger: Old has overgrown: usage (%zu%s) is below threshold ("
                    "%zu%s) after %zu consecutive completed young GCs",
                    byte_size_in_proper_unit(current_usage), proper_unit_for_byte_size(current_usage),
                    byte_size_in_proper_unit(ignore_threshold), proper_unit_for_byte_size(ignore_threshold),
                    consecutive_young_cycles);
      _growth_trigger = false;
    } else if (current_usage > trigger_threshold) {
      const size_t live_at_previous_old = _old_generation->get_live_bytes_after_last_mark();
      const double percent_growth = percent_of(current_usage - live_at_previous_old, live_at_previous_old);
      log_trigger("Old has overgrown, live at end of previous OLD marking: "
                  "%zu%s, current usage: %zu%s, percent growth: %.1f%%",
                  byte_size_in_proper_unit(live_at_previous_old), proper_unit_for_byte_size(live_at_previous_old),
                  byte_size_in_proper_unit(current_usage), proper_unit_for_byte_size(current_usage), percent_growth);
      return true;
    } else {
      // Mixed evacuations have decreased current_usage such that old-growth trigger is no longer relevant.
      _growth_trigger = false;
    }
  }

  // Otherwise, defer to inherited heuristic for gc trigger.
  return this->ShenandoahHeuristics::should_start_gc();
}

void ShenandoahOldHeuristics::record_success_concurrent() {
  // Forget any triggers that occurred while OLD GC was ongoing.  If we really need to start another, it will retrigger.
  clear_triggers();
  this->ShenandoahHeuristics::record_success_concurrent();
}

void ShenandoahOldHeuristics::record_success_degenerated() {
  // Forget any triggers that occurred while OLD GC was ongoing.  If we really need to start another, it will retrigger.
  clear_triggers();
  this->ShenandoahHeuristics::record_success_degenerated();
}

void ShenandoahOldHeuristics::record_success_full() {
  // Forget any triggers that occurred while OLD GC was ongoing.  If we really need to start another, it will retrigger.
  clear_triggers();
  this->ShenandoahHeuristics::record_success_full();
}

const char* ShenandoahOldHeuristics::name() {
  return "Old";
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
