/*
 * Copyright (c) 2018, 2021, Red Hat, Inc. All rights reserved.
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
#include "gc/shared/gcCause.hpp"
#include "gc/shenandoah/shenandoahAllocRequest.hpp"
#include "gc/shenandoah/shenandoahCollectionSet.inline.hpp"
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.inline.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"
#include "gc/shenandoah/heuristics/shenandoahHeuristics.hpp"
#include "gc/shenandoah/mode/shenandoahMode.hpp"
#include "logging/log.hpp"
#include "logging/logTag.hpp"
#include "runtime/globals_extension.hpp"

int ShenandoahHeuristics::compare_by_garbage(RegionData a, RegionData b) {
  if (a._garbage > b._garbage)
    return -1;
  else if (a._garbage < b._garbage)
    return 1;
  else return 0;
}

ShenandoahHeuristics::ShenandoahHeuristics(ShenandoahGeneration* generation) :
  _generation(generation),
  _region_data(NULL),
  _degenerated_cycles_in_a_row(0),
  _successful_cycles_in_a_row(0),
  _guaranteed_gc_interval(0),
  _cycle_start(os::elapsedTime()),
  _last_cycle_end(0),
  _gc_times_learned(0),
  _gc_time_penalties(0),
  _gc_time_history(new TruncatedSeq(10, ShenandoahAdaptiveDecayFactor)),
  _live_memory_last_cycle(0),
  _live_memory_penultimate_cycle(0),
  _metaspace_oom()
{
  // No unloading during concurrent mark? Communicate that to heuristics
  if (!ClassUnloadingWithConcurrentMark) {
    FLAG_SET_DEFAULT(ShenandoahUnloadClassesFrequency, 0);
  }

  size_t num_regions = ShenandoahHeap::heap()->num_regions();
  assert(num_regions > 0, "Sanity");

  _region_data = NEW_C_HEAP_ARRAY(RegionData, num_regions, mtGC);
}

ShenandoahHeuristics::~ShenandoahHeuristics() {
  FREE_C_HEAP_ARRAY(RegionGarbage, _region_data);
}

// Returns true iff the chosen collection set includes old-gen regions
bool ShenandoahHeuristics::choose_collection_set(ShenandoahCollectionSet* collection_set, ShenandoahOldHeuristics* old_heuristics) {
  bool result = false;
  ShenandoahHeap* heap = ShenandoahHeap::heap();

  assert(collection_set->count() == 0, "Must be empty");
  assert(_generation->generation_mode() != OLD, "Old GC invokes ShenandoahOldHeuristics::choose_collection_set()");

  // Check all pinned regions have updated status before choosing the collection set.
  heap->assert_pinned_region_status();

  // Step 1. Build up the region candidates we care about, rejecting losers and accepting winners right away.

  size_t num_regions = heap->num_regions();

  RegionData* candidates = _region_data;

  size_t cand_idx = 0;

  size_t total_garbage = 0;

  size_t immediate_garbage = 0;
  size_t immediate_regions = 0;

  size_t free = 0;
  size_t free_regions = 0;
  size_t live_memory = 0;

  ShenandoahMarkingContext* const ctx = _generation->complete_marking_context();

  size_t remnant_available = 0;
  for (size_t i = 0; i < num_regions; i++) {
    ShenandoahHeapRegion* region = heap->get_region(i);
    if (!in_generation(region)) {
      continue;
    }

    size_t garbage = region->garbage();
    total_garbage += garbage;

    if (region->is_empty()) {
      free_regions++;
      free += ShenandoahHeapRegion::region_size_bytes();
    } else if (region->is_regular()) {
      if (!region->has_live()) {
        // We can recycle it right away and put it in the free set.
        immediate_regions++;
        immediate_garbage += garbage;
        region->make_trash_immediate();
      } else {
        assert (_generation->generation_mode() != OLD, "OLD is handled elsewhere");

        live_memory += region->get_live_data_bytes();
        // This is our candidate for later consideration.
        candidates[cand_idx]._region = region;
        if (heap->mode()->is_generational() && (region->age() >= InitialTenuringThreshold)) {
          // Bias selection of regions that have reached tenure age
          for (uint j = region->age() - InitialTenuringThreshold; j > 0; j--) {
            garbage = (garbage + ShenandoahTenuredRegionUsageBias) * ShenandoahTenuredRegionUsageBias;
          }
        }
        candidates[cand_idx]._garbage = garbage;
        cand_idx++;
      }
    } else if (region->is_humongous_start()) {

      // Reclaim humongous regions here, and count them as the immediate garbage
#ifdef ASSERT
      bool reg_live = region->has_live();
      bool bm_live = ctx->is_marked(cast_to_oop(region->bottom()));
      assert(reg_live == bm_live,
             "Humongous liveness and marks should agree. Region live: %s; Bitmap live: %s; Region Live Words: " SIZE_FORMAT,
             BOOL_TO_STR(reg_live), BOOL_TO_STR(bm_live), region->get_live_data_words());
#endif
      if (!region->has_live()) {
        heap->trash_humongous_region_at(region);

        // Count only the start. Continuations would be counted on "trash" path
        immediate_regions++;
        immediate_garbage += garbage;
      } else {
        live_memory += region->get_live_data_bytes();
      }
    } else if (region->is_trash()) {
      // Count in just trashed collection set, during coalesced CM-with-UR
      immediate_regions++;
      immediate_garbage += garbage;
    } else {                      // region->is_humongous_cont() and !region->is_trash()
      live_memory += region->get_live_data_bytes();
    }
  }

  save_last_live_memory(live_memory);

  // Step 2. Look back at garbage statistics, and decide if we want to collect anything,
  // given the amount of immediately reclaimable garbage. If we do, figure out the collection set.

  assert (immediate_garbage <= total_garbage,
          "Cannot have more immediate garbage than total garbage: " SIZE_FORMAT "%s vs " SIZE_FORMAT "%s",
          byte_size_in_proper_unit(immediate_garbage), proper_unit_for_byte_size(immediate_garbage),
          byte_size_in_proper_unit(total_garbage),     proper_unit_for_byte_size(total_garbage));

  size_t immediate_percent = (total_garbage == 0) ? 0 : (immediate_garbage * 100 / total_garbage);

  if (immediate_percent <= ShenandoahImmediateThreshold) {

    if (old_heuristics != NULL) {
      if (old_heuristics->prime_collection_set(collection_set)) {
        result = true;
      }

      size_t bytes_reserved_for_old_evacuation = collection_set->get_old_bytes_reserved_for_evacuation();
      if (bytes_reserved_for_old_evacuation * ShenandoahEvacWaste < heap->get_old_evac_reserve()) {
        size_t old_evac_reserve = (size_t) (bytes_reserved_for_old_evacuation * ShenandoahEvacWaste);
        heap->set_old_evac_reserve(old_evac_reserve);
      }
    }
    // else, this is global collection and doesn't need to prime_collection_set

    ShenandoahYoungGeneration* young_generation = heap->young_generation();
    size_t young_evacuation_reserve = (young_generation->soft_max_capacity() * ShenandoahEvacReserve) / 100;

    // At this point, young_generation->available() does not know about recently discovered immediate garbage.
    // What memory it does think to be available is not entirely trustworthy because any available memory associated
    // with a region that is placed into the collection set becomes unavailable when the region is chosen
    // for the collection set.  We'll compute an approximation of young available.  If young_available is zero,
    // we'll need to borrow from old-gen in order to evacuate.  If there's nothing to borrow, we're going to
    // degenerate to full GC.

    // TODO: younng_available can include available (between top() and end()) within each young region that is not
    // part of the collection set.  Making this memory available to the young_evacuation_reserve allows a larger
    // young collection set to be chosen when available memory is under extreme pressure.  Implementing this "improvement"
    // is tricky, because the incremental construction of the collection set actually changes the amount of memory
    // available to hold evacuated young-gen objects.  As currently implemented, the memory that is available within
    // non-empty regions that are not selected as part of the collection set can be allocated by the mutator while
    // GC is evacuating and updating references.

    size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();
    size_t free_affiliated_regions = immediate_regions + free_regions;
    size_t young_available = (free_affiliated_regions + young_generation->free_unaffiliated_regions()) * region_size_bytes;

    size_t regions_available_to_loan = 0;

    if (heap->mode()->is_generational()) {
      //  Now that we've primed the collection set, we can figure out how much memory to reserve for evacuation
      //  of young-gen objects.
      //
      //  YoungEvacuationReserve for young generation: how much memory are we reserving to hold the results
      //     of evacuating young collection set regions?  This is typically smaller than the total amount
      //     of available memory, and is also smaller than the total amount of marked live memory within
      //     young-gen.  This value is the minimum of:
      //       1. young_gen->available() + (old_gen->available - (OldEvacuationReserve + PromotionReserve))
      //       2. young_gen->capacity() * ShenandoahEvacReserve
      //
      //     Note that any region added to the collection set will be completely evacuated and its memory will
      //     be completely recycled at the end of GC.  The recycled memory will be at least as great as the
      //     memory borrowed from old-gen.  Enforce that the amount borrowed from old-gen for YoungEvacuationReserve
      //     is an integral number of entire heap regions.
      //
      young_evacuation_reserve -= heap->get_old_evac_reserve();

      // Though we cannot know the evacuation_supplement until after we have computed the collection set, we do
      // know that every young-gen region added to the collection set will have a net positive impact on available
      // memory within young-gen, since each contributes a positive amount of garbage to available.  Thus, even
      // without knowing the exact composition of the collection set, we can allow young_evacuation_reserve to
      // exceed young_available if there are empty regions available within old-gen to hold the results of evacuation.

      ShenandoahGeneration* old_generation = heap->old_generation();

      // Not all of what is currently available within young-gen can be reserved to hold the results of young-gen
      // evacuation.  This is because memory available within any heap region that is placed into the collection set
      // is not available to be allocated during evacuation.  To be safe, we assure that all memory required for evacuation
      // is available within "virgin" heap regions.

      const size_t available_young_regions = free_regions + immediate_regions + young_generation->free_unaffiliated_regions();
      const size_t available_old_regions = old_generation->free_unaffiliated_regions();
      size_t already_reserved_old_bytes = heap->get_old_evac_reserve() + heap->get_promotion_reserve();
      size_t regions_reserved_for_evac_and_promotion = (already_reserved_old_bytes + region_size_bytes - 1) / region_size_bytes;
      regions_available_to_loan = available_old_regions - regions_reserved_for_evac_and_promotion;

      if (available_young_regions * region_size_bytes < young_evacuation_reserve) {
        // Try to borrow old-gen regions in order to avoid shrinking young_evacuation_reserve
        size_t loan_request = young_evacuation_reserve - available_young_regions * region_size_bytes;
        size_t loaned_region_request = (loan_request + region_size_bytes - 1) / region_size_bytes;
        if (loaned_region_request > regions_available_to_loan) {
          // Scale back young_evacuation_reserve to consume all available young and old regions.  After the
          // collection set is chosen, we may get some of this memory back for pacing allocations during evacuation
          // and update refs.
          loaned_region_request = regions_available_to_loan;
          young_evacuation_reserve = (available_young_regions + loaned_region_request) * region_size_bytes;
        } else {
          // No need to scale back young_evacuation_reserve.
        }
      } else {
        // No need scale back young_evacuation_reserve and no need to borrow from old-gen.  We may even have some
        // available_young_regions to support allocation pacing.
      }

    } else if (young_evacuation_reserve > young_available) {
      // In non-generational mode, there's no old-gen memory to borrow from
      young_evacuation_reserve = young_available;
    }

    heap->set_young_evac_reserve(young_evacuation_reserve);

    // Add young-gen regions into the collection set.  This is a virtual call, implemented differently by each
    // of the heuristics subclasses.
    choose_collection_set_from_regiondata(collection_set, candidates, cand_idx, immediate_garbage + free);

    // Now compute the evacuation supplement, which is extra memory borrowed from old-gen that can be allocated
    // by mutators while GC is working on evacuation and update-refs.

    // During evacuation and update refs, we will be able to allocate any memory that is currently available
    // plus any memory that can be borrowed on the collateral of the current collection set, reserving a certain
    // percentage of the anticipated replenishment from collection set memory to be allocated during the subsequent
    // concurrent marking effort.  This is how much I can repay.
    size_t potential_supplement_regions = collection_set->get_young_region_count();

    // Though I can repay potential_supplement_regions, I can't borrow them unless they are available in old-gen.
    if (potential_supplement_regions > regions_available_to_loan) {
      potential_supplement_regions = regions_available_to_loan;
    }

    size_t potential_evac_supplement;

    // How much of the potential_supplement_regions will be consumed by young_evacuation_reserve: borrowed_evac_regions.
    const size_t available_unaffiliated_young_regions = young_generation->free_unaffiliated_regions();
    const size_t available_affiliated_regions = free_regions + immediate_regions;
    const size_t available_young_regions = available_unaffiliated_young_regions + available_affiliated_regions;
    size_t young_evac_regions = (young_evacuation_reserve + region_size_bytes - 1) / region_size_bytes;
    size_t borrowed_evac_regions = (young_evac_regions > available_young_regions)? young_evac_regions - available_young_regions: 0;

    potential_supplement_regions -= borrowed_evac_regions;
    potential_evac_supplement = potential_supplement_regions * region_size_bytes;

    // Leave some allocation runway for subsequent concurrent mark phase.
    potential_evac_supplement = (potential_evac_supplement * ShenandoahBorrowPercent) / 100;

    heap->set_alloc_supplement_reserve(potential_evac_supplement);

    size_t promotion_budget = heap->get_promotion_reserve();
    size_t old_evac_budget = heap->get_old_evac_reserve();
    size_t alloc_budget_evac_and_update = potential_evac_supplement + young_available;

    // TODO: young_available, which feeds into alloc_budget_evac_and_update is lacking memory available within
    // existing young-gen regions that were not selected for the collection set.  Add this in and adjust the
    // log message (where it says "empty-region allocation budget").

    log_info(gc, ergo)("Memory reserved for evacuation and update-refs includes promotion budget: " SIZE_FORMAT
                       "%s, young evacuation budget: " SIZE_FORMAT "%s, old evacuation budget: " SIZE_FORMAT
                       "%s, empty-region allocation budget: " SIZE_FORMAT "%s, including supplement: " SIZE_FORMAT "%s",
                       byte_size_in_proper_unit(promotion_budget), proper_unit_for_byte_size(promotion_budget),
                       byte_size_in_proper_unit(young_evacuation_reserve), proper_unit_for_byte_size(young_evacuation_reserve),
                       byte_size_in_proper_unit(old_evac_budget), proper_unit_for_byte_size(old_evac_budget),
                       byte_size_in_proper_unit(alloc_budget_evac_and_update),
                       proper_unit_for_byte_size(alloc_budget_evac_and_update),
                       byte_size_in_proper_unit(potential_evac_supplement), proper_unit_for_byte_size(potential_evac_supplement));
  }
  // else, we're going to skip evacuation and update refs because we reclaimed sufficient amounts of immediate garbage.

  size_t cset_percent = (total_garbage == 0) ? 0 : (collection_set->garbage() * 100 / total_garbage);
  size_t collectable_garbage = collection_set->garbage() + immediate_garbage;
  size_t collectable_garbage_percent = (total_garbage == 0) ? 0 : (collectable_garbage * 100 / total_garbage);

  log_info(gc, ergo)("Collectable Garbage: " SIZE_FORMAT "%s (" SIZE_FORMAT "%%), "
                     "Immediate: " SIZE_FORMAT "%s (" SIZE_FORMAT "%%), "
                     "CSet: " SIZE_FORMAT "%s (" SIZE_FORMAT "%%)",

                     byte_size_in_proper_unit(collectable_garbage),
                     proper_unit_for_byte_size(collectable_garbage),
                     collectable_garbage_percent,

                     byte_size_in_proper_unit(immediate_garbage),
                     proper_unit_for_byte_size(immediate_garbage),
                     immediate_percent,

                     byte_size_in_proper_unit(collection_set->garbage()),
                     proper_unit_for_byte_size(collection_set->garbage()),
                     cset_percent);
  return result;
}

void ShenandoahHeuristics::record_cycle_start() {
  _cycle_start = os::elapsedTime();
}

void ShenandoahHeuristics::record_cycle_end() {
  _last_cycle_end = os::elapsedTime();
}

bool ShenandoahHeuristics::should_start_gc() {
  // Perform GC to cleanup metaspace
  if (has_metaspace_oom()) {
    // Some of vmTestbase/metaspace tests depend on following line to count GC cycles
    log_info(gc)("Trigger: %s", GCCause::to_string(GCCause::_metadata_GC_threshold));
    return true;
  }

  if (_guaranteed_gc_interval > 0) {
    double last_time_ms = (os::elapsedTime() - _last_cycle_end) * 1000;
    if (last_time_ms > _guaranteed_gc_interval) {
      log_info(gc)("Trigger (%s): Time since last GC (%.0f ms) is larger than guaranteed interval (" UINTX_FORMAT " ms)",
                   _generation->name(), last_time_ms, _guaranteed_gc_interval);
      return true;
    }
  }

  return false;
}

bool ShenandoahHeuristics::should_degenerate_cycle() {
  return _degenerated_cycles_in_a_row <= ShenandoahFullGCThreshold;
}

void ShenandoahHeuristics::adjust_penalty(intx step) {
  assert(0 <= _gc_time_penalties && _gc_time_penalties <= 100,
         "In range before adjustment: " INTX_FORMAT, _gc_time_penalties);

  intx new_val = _gc_time_penalties + step;
  if (new_val < 0) {
    new_val = 0;
  }
  if (new_val > 100) {
    new_val = 100;
  }
  _gc_time_penalties = new_val;

  assert(0 <= _gc_time_penalties && _gc_time_penalties <= 100,
         "In range after adjustment: " INTX_FORMAT, _gc_time_penalties);
}

void ShenandoahHeuristics::record_success_concurrent() {
  _degenerated_cycles_in_a_row = 0;
  _successful_cycles_in_a_row++;

  _gc_time_history->add(time_since_last_gc());
  _gc_times_learned++;

  adjust_penalty(Concurrent_Adjust);
}

void ShenandoahHeuristics::record_success_degenerated() {
  _degenerated_cycles_in_a_row++;
  _successful_cycles_in_a_row = 0;

  adjust_penalty(Degenerated_Penalty);
}

void ShenandoahHeuristics::record_success_full() {
  _degenerated_cycles_in_a_row = 0;
  _successful_cycles_in_a_row++;

  adjust_penalty(Full_Penalty);
}

void ShenandoahHeuristics::record_allocation_failure_gc() {
  // Do nothing.
}

void ShenandoahHeuristics::record_requested_gc() {
  // Assume users call System.gc() when external state changes significantly,
  // which forces us to re-learn the GC timings and allocation rates.
  _gc_times_learned = 0;
}

bool ShenandoahHeuristics::can_unload_classes() {
  if (!ClassUnloading) return false;
  return true;
}

bool ShenandoahHeuristics::can_unload_classes_normal() {
  if (!can_unload_classes()) return false;
  if (has_metaspace_oom()) return true;
  if (!ClassUnloadingWithConcurrentMark) return false;
  if (ShenandoahUnloadClassesFrequency == 0) return false;
  return true;
}

bool ShenandoahHeuristics::should_unload_classes() {
  if (!can_unload_classes_normal()) return false;
  if (has_metaspace_oom()) return true;
  size_t cycle = ShenandoahHeap::heap()->shenandoah_policy()->cycle_counter();
  // Unload classes every Nth GC cycle.
  // This should not happen in the same cycle as process_references to amortize costs.
  // Offsetting by one is enough to break the rendezvous when periods are equal.
  // When periods are not equal, offsetting by one is just as good as any other guess.
  return (cycle + 1) % ShenandoahUnloadClassesFrequency == 0;
}

void ShenandoahHeuristics::initialize() {
  // Nothing to do by default.
}

double ShenandoahHeuristics::time_since_last_gc() const {
  return os::elapsedTime() - _cycle_start;
}

bool ShenandoahHeuristics::in_generation(ShenandoahHeapRegion* region) {
  return ((_generation->generation_mode() == GLOBAL)
          || (_generation->generation_mode() == YOUNG && region->affiliation() == YOUNG_GENERATION)
          || (_generation->generation_mode() == OLD && region->affiliation() == OLD_GENERATION));
}

void ShenandoahHeuristics::save_last_live_memory(size_t live_memory) {
  _live_memory_penultimate_cycle = _live_memory_last_cycle;
  _live_memory_last_cycle = live_memory;
}

size_t ShenandoahHeuristics::get_last_live_memory() {
  return _live_memory_last_cycle;
}

size_t ShenandoahHeuristics::get_penultimate_live_memory() {
  return _live_memory_penultimate_cycle;
}
