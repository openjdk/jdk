/*
 * Copyright (c) 2018, 2020, Red Hat, Inc. All rights reserved.
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
#include "utilities/quickSort.hpp"

inline void assert_no_in_place_promotions() {
#ifdef ASSERT
  class ShenandoahNoInPlacePromotions : public ShenandoahHeapRegionClosure {
  public:
    void heap_region_do(ShenandoahHeapRegion *r) override {
      assert(r->get_top_before_promote() == nullptr,
             "Region " SIZE_FORMAT " should not be ready for in-place promotion", r->index());
    }
  } cl;
  ShenandoahHeap::heap()->heap_region_iterate(&cl);
#endif
}


// sort by decreasing garbage (so most garbage comes first)
int ShenandoahHeuristics::compare_by_garbage(RegionData a, RegionData b) {
  if (a._u._garbage > b._u._garbage)
    return -1;
  else if (a._u._garbage < b._u._garbage)
    return 1;
  else return 0;
}

// sort by increasing live (so least live comes first)
int ShenandoahHeuristics::compare_by_live(RegionData a, RegionData b) {
  if (a._u._live_data < b._u._live_data)
    return -1;
  else if (a._u._live_data > b._u._live_data)
    return 1;
  else return 0;
}

ShenandoahHeuristics::ShenandoahHeuristics(ShenandoahGeneration* generation) :
  _generation(generation),
  _region_data(nullptr),
  _degenerated_cycles_in_a_row(0),
  _successful_cycles_in_a_row(0),
  _guaranteed_gc_interval(0),
  _cycle_start(os::elapsedTime()),
  _last_cycle_end(0),
  _gc_times_learned(0),
  _gc_time_penalties(0),
  _gc_cycle_time_history(new TruncatedSeq(Moving_Average_Samples, ShenandoahAdaptiveDecayFactor)),
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

typedef struct {
  ShenandoahHeapRegion* _region;
  size_t _live_data;
} AgedRegionData;

static int compare_by_aged_live(AgedRegionData a, AgedRegionData b) {
  if (a._live_data < b._live_data)
    return -1;
  else if (a._live_data > b._live_data)
    return 1;
  else return 0;
}

// Preselect for inclusion into the collection set regions whose age is at or above tenure age which contain more than
// ShenandoahOldGarbageThreshold amounts of garbage.  We identify these regions by setting the appropriate entry of
// candidate_regions_for_promotion_by_copy[] to true.  All entries are initialized to false before calling this
// function.
//
// During the subsequent selection of the collection set, we give priority to these promotion set candidates.
// Without this prioritization, we found that the aged regions tend to be ignored because they typically have
// much less garbage and much more live data than the recently allocated "eden" regions.  When aged regions are
// repeatedly excluded from the collection set, the amount of live memory within the young generation tends to
// accumulate and this has the undesirable side effect of causing young-generation collections to require much more
// CPU and wall-clock time.
//
// A second benefit of treating aged regions differently than other regions during collection set selection is
// that this allows us to more accurately budget memory to hold the results of evacuation.  Memory for evacuation
// of aged regions must be reserved in the old generations.  Memory for evacuation of all other regions must be
// reserved in the young generation.
//
// A side effect performed by this function is to tally up the number of regions and the number of live bytes
// that we plan to promote-in-place during the current GC cycle.  This information, which is stored with
// an invocation of heap->set_promotion_in_place_potential(), feeds into subsequent decisions about when to
// trigger the next GC and may identify special work to be done during this GC cycle if we choose to abbreviate it.
//
// Returns bytes of old-gen memory consumed by selected aged regions
size_t ShenandoahHeuristics::select_aged_regions(size_t old_available, size_t num_regions,
                                                 bool candidate_regions_for_promotion_by_copy[]) {

  // There should be no regions configured for subsequent in-place-promotions carried over from the previous cycle.
  assert_no_in_place_promotions();

  ShenandoahHeap* heap = ShenandoahHeap::heap();
  assert(heap->mode()->is_generational(), "Only in generational mode");
  ShenandoahMarkingContext* const ctx = heap->marking_context();
  size_t old_consumed = 0;
  size_t promo_potential = 0;
  size_t anticipated_promote_in_place_live = 0;

  heap->clear_promotion_in_place_potential();
  heap->clear_promotion_potential();
  size_t candidates = 0;
  size_t candidates_live = 0;
  size_t old_garbage_threshold = (ShenandoahHeapRegion::region_size_bytes() * ShenandoahOldGarbageThreshold) / 100;
  size_t promote_in_place_regions = 0;
  size_t promote_in_place_live = 0;
  size_t promote_in_place_pad = 0;
  size_t anticipated_candidates = 0;
  size_t anticipated_promote_in_place_regions = 0;

  // Sort the promotion-eligible regions according to live-data-bytes so that we can first reclaim regions that require
  // less evacuation effort.  This prioritizes garbage first, expanding the allocation pool before we begin the work of
  // reclaiming regions that require more effort.
  AgedRegionData* sorted_regions = (AgedRegionData*) alloca(num_regions * sizeof(AgedRegionData));
  for (size_t i = 0; i < num_regions; i++) {
    ShenandoahHeapRegion* r = heap->get_region(i);
    if (r->is_empty() || !r->has_live() || !r->is_young() || !r->is_regular()) {
      continue;
    }
    if (r->age() >= InitialTenuringThreshold) {
      if ((r->garbage() < old_garbage_threshold)) {
        HeapWord* tams = ctx->top_at_mark_start(r);
        HeapWord* original_top = r->top();
        if (tams == original_top) {
          // No allocations from this region have been made during concurrent mark. It meets all the criteria
          // for in-place-promotion. Though we only need the value of top when we fill the end of the region,
          // we use this field to indicate that this region should be promoted in place during the evacuation
          // phase.
          r->save_top_before_promote();

          size_t remnant_size = r->free() / HeapWordSize;
          if (remnant_size > ShenandoahHeap::min_fill_size()) {
            ShenandoahHeap::fill_with_object(original_top, remnant_size);
            // Fill the remnant memory within this region to assure no allocations prior to promote in place.  Otherwise,
            // newly allocated objects will not be parseable when promote in place tries to register them.  Furthermore, any
            // new allocations would not necessarily be eligible for promotion.  This addresses both issues.
            r->set_top(r->end());
            promote_in_place_pad += remnant_size * HeapWordSize;
          } else {
            // Since the remnant is so small that it cannot be filled, we don't have to worry about any accidental
            // allocations occurring within this region before the region is promoted in place.
          }
          promote_in_place_regions++;
          promote_in_place_live += r->get_live_data_bytes();
        }
        // Else, we do not promote this region (either in place or by copy) because it has received new allocations.

        // During evacuation, we exclude from promotion regions for which age > tenure threshold, garbage < garbage-threshold,
        //  and get_top_before_promote() != tams
      } else {
        // After sorting and selecting best candidates below, we may decide to exclude this promotion-eligible region
        // from the current collection sets.  If this happens, we will consider this region as part of the anticipated
        // promotion potential for the next GC pass.
        size_t live_data = r->get_live_data_bytes();
        candidates_live += live_data;
        sorted_regions[candidates]._region = r;
        sorted_regions[candidates++]._live_data = live_data;
      }
    } else {
      // We only anticipate to promote regular regions if garbage() is above threshold.  Tenure-aged regions with less
      // garbage are promoted in place.  These take a different path to old-gen.  Note that certain regions that are
      // excluded from anticipated promotion because their garbage content is too low (causing us to anticipate that
      // the region would be promoted in place) may be eligible for evacuation promotion by the time promotion takes
      // place during a subsequent GC pass because more garbage is found within the region between now and then.  This
      // should not happen if we are properly adapting the tenure age.  The theory behind adaptive tenuring threshold
      // is to choose the youngest age that demonstrates no "significant" futher loss of population since the previous
      // age.  If not this, we expect the tenure age to demonstrate linear population decay for at least two population
      // samples, whereas we expect to observe exponetial population decay for ages younger than the tenure age.
      //
      // In the case that certain regions which were anticipated to be promoted in place need to be promoted by
      // evacuation, it may be the case that there is not sufficient reserve within old-gen to hold evacuation of
      // these regions.  The likely outcome is that these regions will not be selected for evacuation or promotion
      // in the current cycle and we will anticipate that they will be promoted in the next cycle.  This will cause
      // us to reserve more old-gen memory so that these objects can be promoted in the subsequent cycle.
      //
      // TODO:
      //   If we are auto-tuning the tenure age and regions that were anticipated to be promoted in place end up
      //   being promoted by evacuation, this event should feed into the tenure-age-selection heuristic so that
      //   the tenure age can be increased.
      if (heap->is_aging_cycle() && (r->age() + 1 == InitialTenuringThreshold)) {
        if (r->garbage() >= old_garbage_threshold) {
          anticipated_candidates++;
          promo_potential += r->get_live_data_bytes();
        }
        else {
          anticipated_promote_in_place_regions++;
          anticipated_promote_in_place_live += r->get_live_data_bytes();
        }
      }
    }
    // Note that we keep going even if one region is excluded from selection.
    // Subsequent regions may be selected if they have smaller live data.
  }
  // Sort in increasing order according to live data bytes.  Note that candidates represents the number of regions
  // that qualify to be promoted by evacuation.
  if (candidates > 0) {
    QuickSort::sort<AgedRegionData>(sorted_regions, candidates, compare_by_aged_live, false);
    for (size_t i = 0; i < candidates; i++) {
      size_t region_live_data = sorted_regions[i]._live_data;
      size_t promotion_need = (size_t) (region_live_data * ShenandoahPromoEvacWaste);
      if (old_consumed + promotion_need <= old_available) {
        ShenandoahHeapRegion* region = sorted_regions[i]._region;
        old_consumed += promotion_need;
        candidate_regions_for_promotion_by_copy[region->index()] = true;
      } else {
        // We rejected this promotable region from the collection set because we had no room to hold its copy.
        // Add this region to promo potential for next GC.
        promo_potential += region_live_data;
      }
      // We keep going even if one region is excluded from selection because we need to accumulate all eligible
      // regions that are not preselected into promo_potential
    }
  }
  heap->set_pad_for_promote_in_place(promote_in_place_pad);
  heap->set_promotion_potential(promo_potential);
  heap->set_promotion_in_place_potential(anticipated_promote_in_place_live);
  return old_consumed;
}

void ShenandoahHeuristics::choose_collection_set(ShenandoahCollectionSet* collection_set, ShenandoahOldHeuristics* old_heuristics) {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  bool is_generational = heap->mode()->is_generational();
  size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();

  assert(collection_set->count() == 0, "Must be empty");
  assert(!is_generational || !_generation->is_old(), "Old GC invokes ShenandoahOldHeuristics::choose_collection_set()");

  // Check all pinned regions have updated status before choosing the collection set.
  heap->assert_pinned_region_status();

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

  size_t old_garbage_threshold = (region_size_bytes * ShenandoahOldGarbageThreshold) / 100;
  // This counts number of humongous regions that we intend to promote in this cycle.
  size_t humongous_regions_promoted = 0;
  // This counts bytes of memory used by hunongous regions to be promoted in place.
  size_t humongous_bytes_promoted = 0;
  // This counts number of regular regions that will be promoted in place.
  size_t regular_regions_promoted_in_place = 0;
  // This counts bytes of memory used by regular regions to be promoted in place.
  size_t regular_regions_promoted_usage = 0;

  for (size_t i = 0; i < num_regions; i++) {
    ShenandoahHeapRegion* region = heap->get_region(i);
    if (is_generational && !in_generation(region)) {
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
        assert(!_generation->is_old(), "OLD is handled elsewhere");
        bool is_candidate;
        // This is our candidate for later consideration.
        if (is_generational && collection_set->is_preselected(i)) {
          // If !is_generational, we cannot ask if is_preselected.  If is_preselected, we know
          //   region->age() >= InitialTenuringThreshold).
          is_candidate = true;
          preselected_candidates++;
          // Set garbage value to maximum value to force this into the sorted collection set.
          garbage = region_size_bytes;
        } else if (is_generational && region->is_young() && (region->age() >= InitialTenuringThreshold)) {
          // Note that for GLOBAL GC, region may be OLD, and OLD regions do not qualify for pre-selection

          // This region is old enough to be promoted but it was not preselected, either because its garbage is below
          // ShenandoahOldGarbageThreshold so it will be promoted in place, or because there is not sufficient room
          // in old gen to hold the evacuated copies of this region's live data.  In both cases, we choose not to
          // place this region into the collection set.
          if (region->get_top_before_promote() != nullptr) {
            regular_regions_promoted_in_place++;
            regular_regions_promoted_usage += region->used_before_promote();
          }
          is_candidate = false;
        } else {
          is_candidate = true;
        }
        if (is_candidate) {
          candidates[cand_idx]._region = region;
          candidates[cand_idx]._u._garbage = garbage;
          cand_idx++;
        }
      }
    } else if (region->is_humongous_start()) {
      // Reclaim humongous regions here, and count them as the immediate garbage
#ifdef ASSERT
      bool reg_live = region->has_live();
      bool bm_live = heap->complete_marking_context()->is_marked(cast_to_oop(region->bottom()));
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
        if (region->is_young() && region->age() >= InitialTenuringThreshold) {
          oop obj = cast_to_oop(region->bottom());
          size_t humongous_regions = ShenandoahHeapRegion::required_regions(obj->size() * HeapWordSize);
          humongous_regions_promoted += humongous_regions;
          humongous_bytes_promoted += obj->size() * HeapWordSize;
        }
      }
    } else if (region->is_trash()) {
      // Count in just trashed collection set, during coalesced CM-with-UR
      immediate_regions++;
      immediate_garbage += garbage;
    }
  }
  heap->reserve_promotable_humongous_regions(humongous_regions_promoted);
  heap->reserve_promotable_humongous_usage(humongous_bytes_promoted);
  heap->reserve_promotable_regular_regions(regular_regions_promoted_in_place);
  heap->reserve_promotable_regular_usage(regular_regions_promoted_usage);
  log_info(gc, ergo)("Planning to promote in place " SIZE_FORMAT " humongous regions and " SIZE_FORMAT
                     " regular regions, spanning a total of " SIZE_FORMAT " used bytes",
                     humongous_regions_promoted, regular_regions_promoted_in_place,
                     humongous_regions_promoted * ShenandoahHeapRegion::region_size_bytes() + regular_regions_promoted_usage);

  // Step 2. Look back at garbage statistics, and decide if we want to collect anything,
  // given the amount of immediately reclaimable garbage. If we do, figure out the collection set.

  assert (immediate_garbage <= total_garbage,
          "Cannot have more immediate garbage than total garbage: " SIZE_FORMAT "%s vs " SIZE_FORMAT "%s",
          byte_size_in_proper_unit(immediate_garbage), proper_unit_for_byte_size(immediate_garbage),
          byte_size_in_proper_unit(total_garbage),     proper_unit_for_byte_size(total_garbage));

  size_t immediate_percent = (total_garbage == 0) ? 0 : (immediate_garbage * 100 / total_garbage);
  collection_set->set_immediate_trash(immediate_garbage);

  ShenandoahGeneration* young_gen = heap->young_generation();
  bool doing_promote_in_place = (humongous_regions_promoted + regular_regions_promoted_in_place > 0);
  if (doing_promote_in_place || (preselected_candidates > 0) || (immediate_percent <= ShenandoahImmediateThreshold)) {
    if (old_heuristics != nullptr) {
      old_heuristics->prime_collection_set(collection_set);
    } else {
      // This is a global collection and does not need to prime cset
      assert(_generation->is_global(), "Expected global collection here");
    }

    // Call the subclasses to add young-gen regions into the collection set.
    choose_collection_set_from_regiondata(collection_set, candidates, cand_idx, immediate_garbage + free);
  } else {
    // We are going to skip evacuation and update refs because we reclaimed
    // sufficient amounts of immediate garbage.
    heap->shenandoah_policy()->record_abbreviated_cycle();
  }

  if (collection_set->has_old_regions()) {
    heap->shenandoah_policy()->record_mixed_cycle();
  }

  size_t cset_percent = (total_garbage == 0) ? 0 : (collection_set->garbage() * 100 / total_garbage);
  size_t collectable_garbage = collection_set->garbage() + immediate_garbage;
  size_t collectable_garbage_percent = (total_garbage == 0) ? 0 : (collectable_garbage * 100 / total_garbage);

  log_info(gc, ergo)("Collectable Garbage: " SIZE_FORMAT "%s (" SIZE_FORMAT "%%), "
                     "Immediate: " SIZE_FORMAT "%s (" SIZE_FORMAT "%%), " SIZE_FORMAT " regions, "
                     "CSet: " SIZE_FORMAT "%s (" SIZE_FORMAT "%%), " SIZE_FORMAT " regions",

                     byte_size_in_proper_unit(collectable_garbage),
                     proper_unit_for_byte_size(collectable_garbage),
                     collectable_garbage_percent,

                     byte_size_in_proper_unit(immediate_garbage),
                     proper_unit_for_byte_size(immediate_garbage),
                     immediate_percent,
                     immediate_regions,

                     byte_size_in_proper_unit(collection_set->garbage()),
                     proper_unit_for_byte_size(collection_set->garbage()),
                     cset_percent,
                     collection_set->count());

  if (collection_set->garbage() > 0) {
    size_t young_evac_bytes   = collection_set->get_young_bytes_reserved_for_evacuation();
    size_t promote_evac_bytes = collection_set->get_young_bytes_to_be_promoted();
    size_t old_evac_bytes     = collection_set->get_old_bytes_reserved_for_evacuation();
    size_t total_evac_bytes   = young_evac_bytes + promote_evac_bytes + old_evac_bytes;
    log_info(gc, ergo)("Evacuation Targets: YOUNG: " SIZE_FORMAT "%s, "
                       "PROMOTE: " SIZE_FORMAT "%s, "
                       "OLD: " SIZE_FORMAT "%s, "
                       "TOTAL: " SIZE_FORMAT "%s",
                       byte_size_in_proper_unit(young_evac_bytes),   proper_unit_for_byte_size(young_evac_bytes),
                       byte_size_in_proper_unit(promote_evac_bytes), proper_unit_for_byte_size(promote_evac_bytes),
                       byte_size_in_proper_unit(old_evac_bytes),     proper_unit_for_byte_size(old_evac_bytes),
                       byte_size_in_proper_unit(total_evac_bytes),   proper_unit_for_byte_size(total_evac_bytes));
  }
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

void ShenandoahHeuristics::record_success_concurrent(bool abbreviated) {
  _degenerated_cycles_in_a_row = 0;
  _successful_cycles_in_a_row++;

  if (!(abbreviated && ShenandoahAdaptiveIgnoreShortCycles)) {
    _gc_cycle_time_history->add(elapsed_cycle_time());
    _gc_times_learned++;
  }

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
  reset_gc_learning();
}

void ShenandoahHeuristics::reset_gc_learning() {
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

size_t ShenandoahHeuristics::bytes_of_allocation_runway_before_gc_trigger(size_t young_regions_to_be_recycled) {
  assert(false, "Only implemented for young Adaptive Heuristics");
  return 0;
}


double ShenandoahHeuristics::elapsed_cycle_time() const {
  return os::elapsedTime() - _cycle_start;
}

bool ShenandoahHeuristics::in_generation(ShenandoahHeapRegion* region) {
  return _generation->is_global()
          || (_generation->is_young() && region->is_young())
          || (_generation->is_old()   && region->is_old());
}

size_t ShenandoahHeuristics::min_free_threshold() {
  assert(!_generation->is_old(), "min_free_threshold is only relevant to young GC");
  size_t min_free_threshold = ShenandoahMinFreeThreshold;
  // Note that soft_max_capacity() / 100 * min_free_threshold is smaller than max_capacity() / 100 * min_free_threshold.
  // We want to behave conservatively here, so use max_capacity().  By returning a larger value, we cause the GC to
  // trigger when the remaining amount of free shrinks below the larger threshold.
  return _generation->max_capacity() / 100 * min_free_threshold;
}
