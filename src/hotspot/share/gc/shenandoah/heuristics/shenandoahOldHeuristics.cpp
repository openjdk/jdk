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
#include "gc/shenandoah/shenandoahCollectionSet.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"
#include "utilities/quickSort.hpp"

uint ShenandoahOldHeuristics::NOT_FOUND = -1U;

ShenandoahOldHeuristics::ShenandoahOldHeuristics(ShenandoahOldGeneration* generation, ShenandoahHeuristics* trigger_heuristic) :
  ShenandoahHeuristics(generation),
  _first_pinned_candidate(NOT_FOUND),
  _last_old_collection_candidate(0),
  _next_old_collection_candidate(0),
  _last_old_region(0),
  _trigger_heuristic(trigger_heuristic),
  _promotion_failed(false),
  _old_generation(generation)
{
  assert(_generation->generation_mode() == OLD, "This service only available for old-gc heuristics");
}

bool ShenandoahOldHeuristics::prime_collection_set(ShenandoahCollectionSet* collection_set) {
  if (unprocessed_old_collection_candidates() == 0) {
    return false;
  }

  _first_pinned_candidate = NOT_FOUND;

  ShenandoahHeap* heap = ShenandoahHeap::heap();
  uint included_old_regions = 0;
  size_t evacuated_old_bytes = 0;
  size_t collected_old_bytes = 0;

  // If a region is put into the collection set, then this region's free (not yet used) bytes are no longer
  // "available" to hold the results of other evacuations.  This may cause a decrease in the remaining amount
  // of memory that can still be evacuated.  We address this by reducing the evacuation budget by the amount
  // of live memory in that region and by the amount of unallocated memory in that region if the evacuation
  // budget is constrained by availability of free memory.
  size_t old_evacuation_budget = (size_t) ((double) heap->get_old_evac_reserve() / ShenandoahEvacWaste);
  size_t remaining_old_evacuation_budget = old_evacuation_budget;
  size_t lost_evacuation_capacity = 0;
  log_info(gc)("Choose old regions for mixed collection: old evacuation budget: " SIZE_FORMAT "%s, candidates: %u",
               byte_size_in_proper_unit(old_evacuation_budget), proper_unit_for_byte_size(old_evacuation_budget),
               unprocessed_old_collection_candidates());

  // The number of old-gen regions that were selected as candidates for collection at the end of the most recent old-gen
  // concurrent marking phase and have not yet been collected is represented by unprocessed_old_collection_candidates()
  while (unprocessed_old_collection_candidates() > 0) {
    // Old collection candidates are sorted in order of decreasing garbage contained therein.
    ShenandoahHeapRegion* r = next_old_collection_candidate();
    if (r == nullptr) {
      break;
    }

    // If we choose region r to be collected, then we need to decrease the capacity to hold other evacuations by
    // the size of r's free memory.

    // It's probably overkill to compensate with lost_evacuation_capacity.  But it's the safe thing to do and
    //  has minimal impact on content of primed collection set.
    if (r->get_live_data_bytes() + lost_evacuation_capacity <= remaining_old_evacuation_budget) {
      // Decrement remaining evacuation budget by bytes that will be copied.
      lost_evacuation_capacity += r->free();
      remaining_old_evacuation_budget -= r->get_live_data_bytes();
      collection_set->add_region(r);
      included_old_regions++;
      evacuated_old_bytes += r->get_live_data_bytes();
      collected_old_bytes += r->garbage();
      consume_old_collection_candidate();
    } else {
      break;
    }
  }

  if (_first_pinned_candidate != NOT_FOUND) {
    // Need to deal with pinned regions
    slide_pinned_regions_to_front();
  }

  if (included_old_regions > 0) {
    log_info(gc)("Old-gen piggyback evac (" UINT32_FORMAT " regions, evacuating " SIZE_FORMAT "%s, reclaiming: " SIZE_FORMAT "%s)",
                 included_old_regions,
                 byte_size_in_proper_unit(evacuated_old_bytes), proper_unit_for_byte_size(evacuated_old_bytes),
                 byte_size_in_proper_unit(collected_old_bytes), proper_unit_for_byte_size(collected_old_bytes));
  }

  if (unprocessed_old_collection_candidates() == 0) {
    _old_generation->transition_to(ShenandoahOldGeneration::IDLE);
  }

  return (included_old_regions > 0);
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
    ShenandoahHeapRegion* region = _region_data[search]._region;
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
  for (int32_t search = write_index - 1; search >= (int32_t)_first_pinned_candidate; --search) {
    RegionData& skipped = _region_data[search];
    if (skipped._region->is_pinned()) {
      RegionData& available_slot = _region_data[write_index];
      available_slot._region = skipped._region;
      available_slot._garbage = skipped._garbage;
      --write_index;
    }
  }

  // Update to read from the leftmost pinned region. Plus one here because we decremented
  // the write index to hold the next found pinned region. We are just moving it back now
  // to point to the first pinned region.
  _next_old_collection_candidate = write_index + 1;
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

    if (region->is_regular() || region->is_pinned()) {
      if (!region->has_live()) {
        assert(!region->is_pinned(), "Pinned region should have live (pinned) objects.");
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
        // check above, but we still need to count the start region.
        immediate_regions++;
        immediate_garbage += garbage;
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
  _last_old_region = (uint)cand_idx;
  _last_old_collection_candidate = (uint)cand_idx;
  _next_old_collection_candidate = 0;

  for (size_t i = 0; i < cand_idx; i++) {
    if (candidates[i]._garbage < garbage_threshold) {
      // Candidates are sorted in decreasing order of garbage, so no regions after this will be above the threshold
      _last_old_collection_candidate = (uint)i;
      break;
    }
    candidates_garbage += candidates[i]._garbage;
  }

  // Note that we do not coalesce and fill occupied humongous regions
  // HR: humongous regions, RR: regular regions, CF: coalesce and fill regions
  size_t collectable_garbage = immediate_garbage + candidates_garbage;
  log_info(gc)("Old-Gen Collectable Garbage: " SIZE_FORMAT "%s over " UINT32_FORMAT " regions, "
               "Old-Gen Immediate Garbage: " SIZE_FORMAT "%s over " SIZE_FORMAT " regions.",
               byte_size_in_proper_unit(collectable_garbage), proper_unit_for_byte_size(collectable_garbage), _last_old_collection_candidate,
               byte_size_in_proper_unit(immediate_garbage), proper_unit_for_byte_size(immediate_garbage), immediate_regions);

  if (unprocessed_old_collection_candidates() == 0) {
    _old_generation->transition_to(ShenandoahOldGeneration::IDLE);
  } else {
    _old_generation->transition_to(ShenandoahOldGeneration::WAITING);
  }
}

uint ShenandoahOldHeuristics::last_old_collection_candidate_index() {
  return _last_old_collection_candidate;
}

uint ShenandoahOldHeuristics::unprocessed_old_collection_candidates() {
  return _last_old_collection_candidate - _next_old_collection_candidate;
}

ShenandoahHeapRegion* ShenandoahOldHeuristics::next_old_collection_candidate() {
  while (_next_old_collection_candidate < _last_old_collection_candidate) {
    ShenandoahHeapRegion* next = _region_data[_next_old_collection_candidate]._region;
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

uint ShenandoahOldHeuristics::last_old_region_index() const {
  return _last_old_region;
}

unsigned int ShenandoahOldHeuristics::get_coalesce_and_fill_candidates(ShenandoahHeapRegion** buffer) {
  uint end = _last_old_region;
  uint index = _next_old_collection_candidate;
  while (index < end) {
    *buffer++ = _region_data[index++]._region;
  }
  return _last_old_region - _next_old_collection_candidate;
}

void ShenandoahOldHeuristics::abandon_collection_candidates() {
  _last_old_collection_candidate = 0;
  _next_old_collection_candidate = 0;
  _last_old_region = 0;
}

void ShenandoahOldHeuristics::handle_promotion_failure() {
  if (!_promotion_failed) {
    if (ShenandoahHeap::heap()->generation_sizer()->transfer_capacity(_old_generation)) {
      log_info(gc)("Increased size of old generation due to promotion failure.");
    }
    // TODO: Increase tenuring threshold to push back on promotions.
  }
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
  // For example, we could choose to abandon the previous old collection before it has completed evacuations.
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

void ShenandoahOldHeuristics::record_success_concurrent(bool abbreviated) {
  _trigger_heuristic->record_success_concurrent(abbreviated);
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

void ShenandoahOldHeuristics::reset_gc_learning() {
  _trigger_heuristic->reset_gc_learning();
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


