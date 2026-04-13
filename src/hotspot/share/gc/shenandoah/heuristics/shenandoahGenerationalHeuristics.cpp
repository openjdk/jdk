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
#include "gc/shenandoah/shenandoahInPlacePromoter.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahTrace.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"
#include "logging/log.hpp"
#include "utilities/quickSort.hpp"

using idx_t = ShenandoahSimpleBitMap::idx_t;

static int compare_by_aged_live(AgedRegionData a, AgedRegionData b) {
  if (a._live_data < b._live_data)
    return -1;
  if (a._live_data > b._live_data)
    return 1;
  return 0;
}

void ShenandoahGenerationalHeuristics::post_initialize() {
  ShenandoahHeuristics::post_initialize();
  _free_set = ShenandoahHeap::heap()->free_set();
  compute_headroom_adjustment();
}

inline void assert_no_in_place_promotions() {
#ifdef ASSERT
  class ShenandoahNoInPlacePromotions : public ShenandoahHeapRegionClosure {
  public:
    void heap_region_do(ShenandoahHeapRegion *r) override {
      assert(r->get_top_before_promote() == nullptr,
             "Region %zu should not be ready for in-place promotion", r->index());
    }
  } cl;
  ShenandoahHeap::heap()->heap_region_iterate(&cl);
#endif
}

ShenandoahGenerationalHeuristics::ShenandoahGenerationalHeuristics(ShenandoahGeneration* generation)
        : ShenandoahAdaptiveHeuristics(generation), _generation(generation), _add_regions_to_old(0) {
}

void ShenandoahGenerationalHeuristics::choose_collection_set(ShenandoahCollectionSet* collection_set) {
  ShenandoahGenerationalHeap* heap = ShenandoahGenerationalHeap::heap();

  assert(collection_set->is_empty(), "Collection set must be empty here");

  _add_regions_to_old = 0;

  // Choose the collection set
  filter_regions(collection_set);

  if (_generation->is_global()) {
    // We have just chosen a collection set for a global cycle. The mark bitmap covering old regions is complete, so
    // the remembered set scan can use that to avoid walking into garbage. When the next old mark begins, we will
    // use the mark bitmap to make the old regions parsable by coalescing and filling any unmarked objects. Thus,
    // we prepare for old collections by remembering which regions are old at this time. Note that any objects
    // promoted into old regions will be above TAMS, and so will be considered marked. However, free regions that
    // become old after this point will not be covered correctly by the mark bitmap, so we must be careful not to
    // coalesce those regions. Only the old regions which are not part of the collection set at this point are
    // eligible for coalescing. As implemented now, this has the side effect of possibly initiating mixed-evacuations
    // after a global cycle for old regions that were not included in this collection set.
    heap->old_generation()->transition_old_generation_after_global_gc();
  }
}

void ShenandoahGenerationalHeuristics::compute_evacuation_budgets(ShenandoahInPlacePromotionPlanner& in_place_promotions,
                                                                  ShenandoahHeap* const heap) {
  shenandoah_assert_generational();

  ShenandoahOldGeneration* const old_generation = heap->old_generation();
  ShenandoahYoungGeneration* const young_generation = heap->young_generation();
  const size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();

  // During initialization and phase changes, it is more likely that fewer objects die young and old-gen
  // memory is not yet full (or is in the process of being replaced).  During these times especially, it
  // is beneficial to loan memory from old-gen to young-gen during the evacuation and update-refs phases
  // of execution.

  // Calculate EvacuationReserve before PromotionReserve.  Evacuation is more critical than promotion.
  // If we cannot evacuate old-gen, we will not be able to reclaim old-gen memory.  Promotions are less
  // critical.  If we cannot promote, there may be degradation of young-gen memory because old objects
  // accumulate there until they can be promoted.  This increases the young-gen marking and evacuation work.

  // First priority is to reclaim the easy garbage out of young-gen.

  // maximum_young_evacuation_reserve is upper bound on memory to be evacuated into young Collector Reserve.  This is
  // bounded at the end of previous GC cycle, based on available memory and balancing of evacuation to old and young.
  size_t maximum_young_evacuation_reserve = young_generation->get_evacuation_reserve();

  // maximum_old_evacuation_reserve is an upper bound on memory evacuated from old and evacuated to old (promoted),
  // clamped by the old generation space available.
  //
  // Here's the algebra.
  // Let SOEP = ShenandoahOldEvacPercent,
  //     OE = old evac,
  //     YE = young evac, and
  //     TE = total evac = OE + YE
  // By definition:
  //            SOEP/100 = OE/TE
  //                     = OE/(OE+YE)
  //  => SOEP/(100-SOEP) = OE/((OE+YE)-OE)         // componendo-dividendo: If a/b = c/d, then a/(b-a) = c/(d-c)
  //                     = OE/YE
  //  =>              OE = YE*SOEP/(100-SOEP)

  // We have to be careful in the event that SOEP is set to 100 by the user.
  assert(ShenandoahOldEvacPercent <= 100, "Error");
  const size_t old_available = old_generation->available();
  const size_t maximum_old_evacuation_reserve = (ShenandoahOldEvacPercent == 100) ?
    old_available : MIN2((maximum_young_evacuation_reserve * ShenandoahOldEvacPercent) / (100 - ShenandoahOldEvacPercent),
                          old_available);

  // In some cases, maximum_old_reserve < old_available (when limited by ShenandoahOldEvacPercent)
  // This limit affects mixed evacuations, but does not affect promotions.

  // Second priority is to reclaim garbage out of old-gen if there are old-gen collection candidates.  Third priority
  // is to promote as much as we have room to promote.  However, if old-gen memory is in short supply, this means young
  // GC is operating under "duress" and was unable to transfer the memory that we would normally expect.  In this case,
  // old-gen will refrain from compacting itself in order to allow a quicker young-gen cycle (by avoiding the update-refs
  // through ALL of old-gen).  If there is some memory available in old-gen, we will use this for promotions as promotions
  // do not add to the update-refs burden of GC.

  size_t old_evacuation_reserve, old_promo_reserve;
  if (_generation->is_global()) {
    // Global GC is typically triggered by user invocation of System.gc(), and typically indicates that there is lots
    // of garbage to be reclaimed because we are starting a new phase of execution.  Marking for global GC may take
    // significantly longer than typical young marking because we must mark through all old objects.  To expedite
    // evacuation and update-refs, we give emphasis to reclaiming garbage first, wherever that garbage is found.
    // Global GC will adjust generation sizes to accommodate the collection set it chooses.

    // Use remnant of old_available to hold promotions.
    old_promo_reserve = old_available - maximum_old_evacuation_reserve;

    // Dedicate all available old memory to old_evacuation reserve.  This may be small, because old-gen is only
    // expanded based on an existing mixed evacuation workload at the end of the previous GC cycle.  We'll expand
    // the budget for evacuation of old during GLOBAL cset selection.
    old_evacuation_reserve = maximum_old_evacuation_reserve;
  } else if (old_generation->has_unprocessed_collection_candidates()) {
    // We reserved all old-gen memory at end of previous GC to hold anticipated evacuations to old-gen.  If this is
    // mixed evacuation, reserve all of this memory for compaction of old-gen and do not promote.  Prioritize compaction
    // over promotion in order to defragment OLD so that it will be better prepared to efficiently receive promoted memory.
    old_evacuation_reserve = maximum_old_evacuation_reserve;
    old_promo_reserve = old_available - maximum_old_evacuation_reserve;
  } else {
    // Make all old-evacuation memory for promotion, but if we can't use it all for promotion, we'll allow some evacuation.
    old_evacuation_reserve = old_available - maximum_old_evacuation_reserve;
    old_promo_reserve = maximum_old_evacuation_reserve;
  }
  assert(old_evacuation_reserve <= old_available, "Error");


  // We see too many old-evacuation failures if we force ourselves to evacuate into regions that are not initially empty.
  // So we limit the old-evacuation reserve to unfragmented memory.  Even so, old-evacuation is free to fill in nooks and
  // crannies within existing partially used regions and it generally tries to do so.
  const size_t old_free_unfragmented = old_generation->free_unaffiliated_regions() * region_size_bytes;
  if (old_evacuation_reserve > old_free_unfragmented) {
    const size_t delta = old_evacuation_reserve - old_free_unfragmented;
    old_evacuation_reserve -= delta;
    // Let promo consume fragments of old-gen memory
    old_promo_reserve += delta;
  }

  // If is_global(), we let garbage-first heuristic determine cset membership.  Otherwise, we give priority
  // to tenurable regions by preselecting regions for promotion by evacuation (obtaining the live data to seed promoted_reserve).
  // This also identifies regions that will be promoted in place. These use the tenuring threshold.
  const size_t consumed_by_advance_promotion = select_aged_regions(in_place_promotions, _generation->is_global()? 0: old_promo_reserve);
  assert(consumed_by_advance_promotion <= old_promo_reserve, "Do not promote more than budgeted");

  // The young evacuation reserve can be no larger than young_unaffiliated.  Planning to evacuate into partially consumed
  // young regions is doomed to failure if any of those partially consumed regions is selected for the collection set.
  size_t young_unaffiliated = young_generation->free_unaffiliated_regions() * region_size_bytes;

  // If any regions have been selected for promotion in place, this has the effect of decreasing available within mutator
  // and collector partitions, due to padding of remnant memory within each promoted in place region.  This will affect
  // young_evacuation_reserve but not old_evacuation_reserve or consumed_by_advance_promotion.  So recompute.
  size_t young_evacuation_reserve = MIN2(maximum_young_evacuation_reserve, young_unaffiliated);

  // Note that unused old_promo_reserve might not be entirely consumed_by_advance_promotion.  Do not transfer this
  // to old_evacuation_reserve because this memory is likely very fragmented, and we do not want to increase the likelihood
  // of old evacuation failure.  Leave this memory in the promoted reserve as it may be targeted by opportunistic
  // promotions (found during evacuation of young regions).
  young_generation->set_evacuation_reserve(young_evacuation_reserve);
  old_generation->set_evacuation_reserve(old_evacuation_reserve);
  old_generation->set_promoted_reserve(old_promo_reserve);

  // There is no need to expand OLD because all memory used here was set aside at end of previous GC, except in the
  // case of a GLOBAL gc.  During choose_collection_set() of GLOBAL, old will be expanded on demand.
}

void ShenandoahGenerationalHeuristics::filter_regions(ShenandoahCollectionSet* collection_set) {
  auto heap = ShenandoahGenerationalHeap::heap();
  const size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();

  // Check all pinned regions have updated status before choosing the collection set.
  heap->assert_pinned_region_status(_generation);

  // Step 1. Build up the region candidates we care about, rejecting losers and accepting winners right away.

  const size_t num_regions = heap->num_regions();

  RegionData* candidates = _region_data;

  size_t cand_idx = 0;

  size_t total_garbage = 0;

  size_t immediate_garbage = 0;
  size_t immediate_regions = 0;

  size_t free = 0;
  size_t free_regions = 0;

  for (size_t i = 0; i < num_regions; i++) {
    ShenandoahHeapRegion* region = heap->get_region(i);
    if (!_generation->contains(region)) {
      continue;
    }
    const size_t garbage = region->garbage();
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
        // This is our candidate for later consideration. Note that this region
        // could still be promoted in place and may not necessarily end up in the
        // collection set.
        assert(region->get_top_before_promote() == nullptr, "Cannot add region %zu scheduled for in-place-promotion to the collection set", i);
        candidates[cand_idx].set_region_and_garbage(region, garbage);
        cand_idx++;
      }
    } else if (region->is_humongous_start()) {
      // Reclaim humongous regions here, and count them as the immediate garbage
      DEBUG_ONLY(assert_humongous_mark_consistency(region));
      if (!region->has_live()) {
        heap->trash_humongous_region_at(region);

        // Count only the start. Continuations would be counted on "trash" path
        immediate_regions++;
        immediate_garbage += garbage;
      }
    } else if (region->is_trash()) {
      // Count in just trashed humongous continuation regions
      immediate_regions++;
      immediate_garbage += garbage;
    }
  }

  // Step 2. Look back at garbage statistics, and decide if we want to collect anything,
  // given the amount of immediately reclaimable garbage. If we do, figure out the collection set.
  assert(immediate_garbage <= total_garbage,
         "Cannot have more immediate garbage than total garbage: " PROPERFMT " vs " PROPERFMT,
         PROPERFMTARGS(immediate_garbage), PROPERFMTARGS(total_garbage));

  const size_t immediate_percent = (total_garbage == 0) ? 0 : (immediate_garbage * 100 / total_garbage);
  ShenandoahInPlacePromotionPlanner in_place_promotions(heap);
  if (immediate_percent <= ShenandoahImmediateThreshold) {

    // Find the amount that will be promoted, regions that will be promoted in
    // place, and preselected older regions that will be promoted by evacuation.
    compute_evacuation_budgets(in_place_promotions, heap);

    // Call the subclasses to add young-gen regions into the collection set.
    choose_collection_set_from_regiondata(collection_set, candidates, cand_idx, immediate_garbage + free);

    // Even if collection_set->is_empty(), we want to adjust budgets, making reserves available to mutator.
    adjust_evacuation_budgets(heap, collection_set);

    if (collection_set->has_old_regions()) {
      heap->shenandoah_policy()->record_mixed_cycle();
    }
  }

  collection_set->summarize(total_garbage, immediate_garbage, immediate_regions);
  ShenandoahTracer::report_evacuation_info(collection_set,
                                           free_regions,
                                           in_place_promotions.humongous_region_stats().count,
                                           in_place_promotions.regular_region_stats().count,
                                           in_place_promotions.regular_region_stats().garbage,
                                           in_place_promotions.regular_region_stats().free,
                                           immediate_regions,
                                           immediate_garbage);
}

void ShenandoahGenerationalHeuristics::add_tenured_regions_to_collection_set(const size_t old_promotion_reserve,
                                                                               ShenandoahGenerationalHeap *const heap,
                                                                               size_t candidates, AgedRegionData* sorted_regions) {
  size_t old_consumed = 0;
  if (candidates > 0) {
    // Sort in increasing order according to live data bytes.  Note that
    // candidates represents the number of regions that qualify to be promoted
    // by evacuation.
    QuickSort::sort<AgedRegionData>(sorted_regions, candidates,
                                    compare_by_aged_live);

    size_t selected_regions = 0;
    size_t selected_live = 0;
    for (size_t i = 0; i < candidates; i++) {
      ShenandoahHeapRegion *const region = sorted_regions[i]._region;
      const size_t region_live_data = sorted_regions[i]._live_data;
      const size_t promotion_need = (size_t)(region_live_data * ShenandoahPromoEvacWaste);
      if (old_consumed + promotion_need > old_promotion_reserve) {
        // We rejected the remaining promotable regions from the collection set
        // because we have no room to hold their evacuees. We do not need to
        // iterate the remaining regions to estimate the amount we expect to
        // promote because we know it directly form the census we computed
        // during the preceding mark phase.
        break;
      }

      old_consumed += promotion_need;
      heap->collection_set()->add_region(region);
      selected_regions++;
      selected_live += region_live_data;
    }
    log_debug(gc, ergo)( "Preselected %zu regions containing " PROPERFMT " live data,"
                        " consuming: " PROPERFMT " of budgeted: " PROPERFMT,
                        selected_regions, PROPERFMTARGS(selected_live),
                        PROPERFMTARGS(old_consumed), PROPERFMTARGS(old_promotion_reserve));
  }
}

// Select for inclusion into the collection set all regions whose age is at or
// above tenure age and for which the
// garbage percentage exceeds a dynamically adjusted threshold (known as the old-garbage threshold percentage).
//
// Without this prioritization, we found that the aged regions tend to be ignored because they typically have
// much less garbage and much more live data than the recently allocated "eden" regions.  When aged regions are
// repeatedly excluded from the collection set, the amount of live memory within the young generation tends to
// accumulate and this has the undesirable side effect of causing young-generation collections to require much more
// CPU and wall-clock time.
//
// A second benefit of treating aged regions differently than other regions during collection set selection is
// that this allows us to more accurately budget memory to hold the results of evacuation. Memory for evacuation
// of aged regions must be reserved in the old generation. Memory for evacuation of all other regions must be
// reserved in the young generation.
size_t ShenandoahGenerationalHeuristics::select_aged_regions(ShenandoahInPlacePromotionPlanner& in_place_promotions,
                                                             const size_t old_promotion_reserve) {

  // There should be no regions configured for subsequent in-place-promotions carried over from the previous cycle.
  assert_no_in_place_promotions();

  auto const heap = ShenandoahGenerationalHeap::heap();

  size_t candidates = 0;

  // Sort the promotion-eligible regions in order of increasing live-data-bytes so that we can first reclaim regions that require
  // less evacuation effort.  This prioritizes garbage first, expanding the allocation pool early before we reclaim regions that
  // have more live data.
  const idx_t num_regions = heap->num_regions();

  ResourceMark rm;
  AgedRegionData* sorted_regions = NEW_RESOURCE_ARRAY(AgedRegionData, num_regions);

  for (idx_t i = 0; i < num_regions; i++) {
    ShenandoahHeapRegion* const r = heap->get_region(i);
    if (r->is_empty() || !r->has_live() || !r->is_young()) {
      // skip over regions that aren't young with some live data
      continue;
    }

    if (!r->is_regular()) {
      if (r->is_humongous_start() && heap->is_tenurable(r)) {
        in_place_promotions.prepare(r);
      }
      // Nothing else to be done for humongous regions
      continue;
    }

    if (heap->is_tenurable(r)) {
      if (in_place_promotions.is_eligible(r)) {
        // We prefer to promote this region in place because it has a small amount of garbage and a large usage.
        // Note that if this region has been used recently for allocation, it will not be promoted and it will
        // not be selected for promotion by evacuation.
        in_place_promotions.prepare(r);
      } else {
        // Record this promotion-eligible candidate region. After sorting and selecting the best candidates below,
        // we may still decide to exclude this promotion-eligible region from the current collection set.  If this
        // happens, we will consider this region as part of the anticipated promotion potential for the next GC
        // pass; see further below.
        sorted_regions[candidates]._region = r;
        sorted_regions[candidates]._live_data = r->get_live_data_bytes();
        candidates++;
      }
    }
  }

  in_place_promotions.complete_planning();

  add_tenured_regions_to_collection_set(old_promotion_reserve, heap, candidates, sorted_regions);

  const uint tenuring_threshold = heap->age_census()->tenuring_threshold();
  const size_t tenurable_this_cycle = heap->age_census()->get_tenurable_bytes(tenuring_threshold);
  const size_t tenurable_next_cycle = heap->age_census()->get_tenurable_bytes(tenuring_threshold - 1);
  assert(tenurable_next_cycle >= tenurable_this_cycle,
          "Tenurable next cycle (" PROPERFMT ") should include tenurable this cycle (" PROPERFMT ")",
          PROPERFMTARGS(tenurable_next_cycle), PROPERFMTARGS(tenurable_this_cycle));

  const size_t max_promotions = tenurable_this_cycle * ShenandoahPromoEvacWaste;
  const size_t old_consumed = MIN2(max_promotions, old_promotion_reserve);

  // Don't include the bytes we expect to promote in this cycle in the next cycle
  const size_t promo_potential = (tenurable_next_cycle - tenurable_this_cycle) * ShenandoahPromoEvacWaste;
  heap->old_generation()->set_promotion_potential(promo_potential);
  log_info(gc, ergo)("Promotion potential of aged regions with sufficient garbage: " PROPERFMT, PROPERFMTARGS(promo_potential));

  return old_consumed;
}

// Having chosen the collection set, adjust the budgets for generational mode based on its composition.  Note
// that young_generation->available() now knows about recently discovered immediate garbage.
void ShenandoahGenerationalHeuristics::adjust_evacuation_budgets(ShenandoahHeap* const heap,
                                                                 ShenandoahCollectionSet* const collection_set) {
  shenandoah_assert_generational();
  // We may find that old_evacuation_reserve and/or loaned_for_young_evacuation are not fully consumed, in which case we may
  //  be able to increase regions_available_to_loan

  // The role of adjust_evacuation_budgets() is to compute the correct value of regions_available_to_loan and to make
  // effective use of this memory, including the remnant memory within these regions that may result from rounding loan to
  // integral number of regions.  Excess memory that is available to be loaned is applied to an allocation supplement,
  // which allows mutators to allocate memory beyond the current capacity of young-gen on the promise that the loan
  // will be repaid as soon as we finish updating references for the recently evacuated collection set.

  // We cannot recalculate regions_available_to_loan by simply dividing old_generation->available() by region_size_bytes
  // because the available memory may be distributed between many partially occupied regions that are already holding old-gen
  // objects.  Memory in partially occupied regions is not "available" to be loaned.  Note that an increase in old-gen
  // available that results from a decrease in memory consumed by old evacuation is not necessarily available to be loaned
  // to young-gen.

  const size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();
  ShenandoahOldGeneration* const old_generation = heap->old_generation();
  ShenandoahYoungGeneration* const young_generation = heap->young_generation();

  const size_t old_evacuated = collection_set->get_live_bytes_in_old_regions();
  size_t old_evacuated_committed = (size_t) (ShenandoahOldEvacWaste * double(old_evacuated));
  size_t old_evacuation_reserve = old_generation->get_evacuation_reserve();

  if (old_evacuated_committed > old_evacuation_reserve) {
    // This should only happen due to round-off errors when enforcing ShenandoahOldEvacWaste
    assert(old_evacuated_committed <= (33 * old_evacuation_reserve) / 32,
           "Round-off errors should be less than 3.125%%, committed: %zu, reserved: %zu",
           old_evacuated_committed, old_evacuation_reserve);
    old_evacuated_committed = old_evacuation_reserve;
    // Leave old_evac_reserve as previously configured
  } else if (old_evacuated_committed < old_evacuation_reserve) {
    // This happens if the old-gen collection consumes less than full budget.
    log_debug(gc, cset)("Shrinking old evac reserve to match old_evac_commited: " PROPERFMT,
                        PROPERFMTARGS(old_evacuated_committed));
    old_evacuation_reserve = old_evacuated_committed;
    old_generation->set_evacuation_reserve(old_evacuation_reserve);
  }

  size_t young_advance_promoted = collection_set->get_live_bytes_in_tenurable_regions();
  size_t young_advance_promoted_reserve_used = (size_t) (ShenandoahPromoEvacWaste * double(young_advance_promoted));

  size_t young_evacuated = collection_set->get_live_bytes_in_untenurable_regions();
  size_t young_evacuated_reserve_used = (size_t) (ShenandoahEvacWaste * double(young_evacuated));

  // In top_off_collection_set(), we shrunk planned future reserve by _add_regions_to_old * region_size_bytes, but we
  // didn't shrink available. The current reserve is not affected by the planned future reserve. Current available is
  // larger than planned available by the planned adjustment amount.
  size_t total_young_available = young_generation->available_with_reserve() - _add_regions_to_old * region_size_bytes;

  assert(young_evacuated_reserve_used <= total_young_available, "Cannot evacuate (%zu) more than is available in young (%zu)",
         young_evacuated_reserve_used, total_young_available);
  young_generation->set_evacuation_reserve(young_evacuated_reserve_used);

  // We have not yet rebuilt the free set.  Some of the memory that is thought to be available within old may no
  // longer be available if that memory had been free within regions that were selected for the collection set.
  // Make the necessary adjustments to old_available.
  size_t old_available =
    old_generation->available() + _add_regions_to_old * region_size_bytes - collection_set->get_old_available_bytes_collected();

  // Now that we've established the collection set, we know how much memory is really required by old-gen for evacuation
  // and promotion reserves.  Try shrinking OLD now in case that gives us a bit more runway for mutator allocations during
  // evac and update phases.
  size_t old_consumed = old_evacuated_committed + young_advance_promoted_reserve_used;

  if (old_available < old_consumed) {
    // This can happen due to round-off errors when adding the results of truncated integer arithmetic.
    // We've already truncated old_evacuated_committed.  Truncate young_advance_promoted_reserve_used here.

    assert(young_advance_promoted_reserve_used <= (33 * (old_available - old_evacuated_committed)) / 32,
           "Round-off errors should be less than 3.125%%, committed: %zu, reserved: %zu",
           young_advance_promoted_reserve_used, old_available - old_evacuated_committed);
    if (old_available > old_evacuated_committed) {
      young_advance_promoted_reserve_used = old_available - old_evacuated_committed;
    } else {
      young_advance_promoted_reserve_used = 0;
      old_evacuated_committed = old_available;
    }
    // TODO: reserve for full promotion reserve, not just for advance (preselected) promotion
    old_consumed = old_evacuated_committed + young_advance_promoted_reserve_used;
  }

  assert(old_available >= old_consumed, "Cannot consume (%zu) more than is available (%zu)",
         old_consumed, old_available);
  size_t excess_old = old_available - old_consumed;
  size_t unaffiliated_old_regions = old_generation->free_unaffiliated_regions() + _add_regions_to_old;
  size_t unaffiliated_old = unaffiliated_old_regions * region_size_bytes;
  assert(unaffiliated_old >= old_evacuated_committed, "Do not evacuate (%zu) more than unaffiliated old (%zu)",
         old_evacuated_committed, unaffiliated_old);

  // Make sure old_evac_committed is unaffiliated
  if (old_evacuated_committed > 0) {
    if (unaffiliated_old > old_evacuated_committed) {
      size_t giveaway = unaffiliated_old - old_evacuated_committed;
      size_t giveaway_regions = giveaway / region_size_bytes;  // round down
      if (giveaway_regions > 0) {
        excess_old = MIN2(excess_old, giveaway_regions * region_size_bytes);
      } else {
        excess_old = 0;
      }
    } else {
      excess_old = 0;
    }
  }

  // If we find that OLD has excess regions, give them back to YOUNG now to reduce likelihood we run out of allocation
  // runway during evacuation and update-refs.  We may make further adjustments to balance.
  ssize_t add_regions_to_young = 0;
  if (excess_old > unaffiliated_old) {
    // we can give back unaffiliated_old (all of unaffiliated is excess)
    if (unaffiliated_old_regions > 0) {
      add_regions_to_young = unaffiliated_old_regions;
    }
  } else if (unaffiliated_old_regions > 0) {
    // excess_old < unaffiliated old: we can give back MIN(excess_old/region_size_bytes, unaffiliated_old_regions)
    size_t excess_regions = excess_old / region_size_bytes;
    add_regions_to_young = MIN2(excess_regions, unaffiliated_old_regions);
  }

  if (add_regions_to_young > 0) {
    assert(excess_old >= add_regions_to_young * region_size_bytes, "Cannot xfer more than excess old");
    excess_old -= add_regions_to_young * region_size_bytes;
    log_debug(gc, ergo)("Before start of evacuation, total_promotion reserve is young_advance_promoted_reserve: %zu "
                        "plus excess: old: %zu", young_advance_promoted_reserve_used, excess_old);
  }

  // Add in the excess_old memory to hold unanticipated promotions, if any.  If there are more unanticipated
  // promotions than fit in reserved memory, they will be deferred until a future GC pass.
  size_t total_promotion_reserve = young_advance_promoted_reserve_used + excess_old;

  old_generation->set_promoted_reserve(total_promotion_reserve);
  old_generation->reset_promoted_expended();
}
