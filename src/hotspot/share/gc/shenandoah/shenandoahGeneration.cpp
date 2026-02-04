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

#include "gc/shenandoah/heuristics/shenandoahHeuristics.hpp"
#include "gc/shenandoah/shenandoahCollectionSetPreselector.hpp"
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/shenandoahGenerationalHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegionClosures.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahReferenceProcessor.hpp"
#include "gc/shenandoah/shenandoahScanRemembered.inline.hpp"
#include "gc/shenandoah/shenandoahTaskqueue.inline.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "gc/shenandoah/shenandoahVerifier.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"
#include "utilities/quickSort.hpp"

using idx_t = ShenandoahSimpleBitMap::idx_t;

template <bool PREPARE_FOR_CURRENT_CYCLE, bool FULL_GC = false>
class ShenandoahResetBitmapClosure final : public ShenandoahHeapRegionClosure {
private:
  ShenandoahHeap*           _heap;
  ShenandoahMarkingContext* _ctx;

public:
  explicit ShenandoahResetBitmapClosure() :
    ShenandoahHeapRegionClosure(), _heap(ShenandoahHeap::heap()), _ctx(_heap->marking_context()) {}

  void heap_region_do(ShenandoahHeapRegion* region) override {
    assert(!_heap->is_uncommit_in_progress(), "Cannot uncommit bitmaps while resetting them.");
    if (PREPARE_FOR_CURRENT_CYCLE) {
      if (region->need_bitmap_reset() && _heap->is_bitmap_slice_committed(region)) {
        _ctx->clear_bitmap(region);
      } else {
        region->set_needs_bitmap_reset();
      }
      // Capture Top At Mark Start for this generation.
      if (FULL_GC || region->is_active()) {
        // Reset live data and set TAMS optimistically. We would recheck these under the pause
        // anyway to capture any updates that happened since now.
        _ctx->capture_top_at_mark_start(region);
        region->clear_live_data();
      }
    } else {
      if (_heap->is_bitmap_slice_committed(region)) {
        _ctx->clear_bitmap(region);
        region->unset_needs_bitmap_reset();
      } else {
        region->set_needs_bitmap_reset();
      }
    }
  }

  // Bitmap reset task is heavy-weight and benefits from much smaller tasks than the default.
  size_t parallel_region_stride() override { return 8; }

  bool is_thread_safe() override { return true; }
};

// Copy the write-version of the card-table into the read-version, clearing the
// write-copy.
class ShenandoahMergeWriteTable: public ShenandoahHeapRegionClosure {
private:
  ShenandoahScanRemembered* _scanner;
public:
  ShenandoahMergeWriteTable(ShenandoahScanRemembered* scanner) : _scanner(scanner) {}

  void heap_region_do(ShenandoahHeapRegion* r) override {
    assert(r->is_old(), "Don't waste time doing this for non-old regions");
    _scanner->merge_write_table(r->bottom(), ShenandoahHeapRegion::region_size_words());
  }

  bool is_thread_safe() override {
    return true;
  }
};

// Add [TAMS, top) volume over young regions. Used to correct age 0 cohort census
// for adaptive tenuring when census is taken during marking.
// In non-product builds, for the purposes of verification, we also collect the total
// live objects in young regions as well.
class ShenandoahUpdateCensusZeroCohortClosure : public ShenandoahHeapRegionClosure {
private:
  ShenandoahMarkingContext* const _ctx;
  // Population size units are words (not bytes)
  size_t _age0_pop;                // running tally of age0 population size
  size_t _total_pop;               // total live population size
public:
  explicit ShenandoahUpdateCensusZeroCohortClosure(ShenandoahMarkingContext* ctx)
    : _ctx(ctx), _age0_pop(0), _total_pop(0) {}

  void heap_region_do(ShenandoahHeapRegion* r) override {
    if (_ctx != nullptr && r->is_active()) {
      assert(r->is_young(), "Young regions only");
      HeapWord* tams = _ctx->top_at_mark_start(r);
      HeapWord* top  = r->top();
      if (top > tams) {
        _age0_pop += pointer_delta(top, tams);
      }
      // TODO: check significance of _ctx != nullptr above, can that
      // spoof _total_pop in some corner cases?
      NOT_PRODUCT(_total_pop += r->get_live_data_words();)
    }
  }

  size_t get_age0_population()  const { return _age0_pop; }
  size_t get_total_population() const { return _total_pop; }
};

void ShenandoahGeneration::confirm_heuristics_mode() {
  if (_heuristics->is_diagnostic() && !UnlockDiagnosticVMOptions) {
    vm_exit_during_initialization(
            err_msg("Heuristics \"%s\" is diagnostic, and must be enabled via -XX:+UnlockDiagnosticVMOptions.",
                    _heuristics->name()));
  }
  if (_heuristics->is_experimental() && !UnlockExperimentalVMOptions) {
    vm_exit_during_initialization(
            err_msg("Heuristics \"%s\" is experimental, and must be enabled via -XX:+UnlockExperimentalVMOptions.",
                    _heuristics->name()));
  }
}

ShenandoahHeuristics* ShenandoahGeneration::initialize_heuristics(ShenandoahMode* gc_mode) {
  _heuristics = gc_mode->initialize_heuristics(this);
  _heuristics->set_guaranteed_gc_interval(ShenandoahGuaranteedGCInterval);
  confirm_heuristics_mode();
  return _heuristics;
}

void ShenandoahGeneration::set_evacuation_reserve(size_t new_val) {
  shenandoah_assert_heaplocked();
  _evacuation_reserve = new_val;
}

size_t ShenandoahGeneration::get_evacuation_reserve() const {
  return _evacuation_reserve;
}

void ShenandoahGeneration::augment_evacuation_reserve(size_t increment) {
  _evacuation_reserve += increment;
}

void ShenandoahGeneration::log_status(const char *msg) const {
  typedef LogTarget(Info, gc, ergo) LogGcInfo;

  if (!LogGcInfo::is_enabled()) {
    return;
  }

  // Not under a lock here, so read each of these once to make sure
  // byte size in proper unit and proper unit for byte size are consistent.
  const size_t v_used = used();
  const size_t v_used_regions = used_regions_size();
  const size_t v_soft_max_capacity = ShenandoahHeap::heap()->soft_max_capacity();
  const size_t v_max_capacity = max_capacity();
  const size_t v_available = available();
  const size_t v_humongous_waste = get_humongous_waste();

  const LogGcInfo target;
  LogStream ls(target);
  ls.print("%s: ", msg);
  if (_type != NON_GEN) {
    ls.print("%s generation ", name());
  }

  ls.print_cr("used: " PROPERFMT ", used regions: " PROPERFMT ", humongous waste: " PROPERFMT
              ", soft capacity: " PROPERFMT ", max capacity: " PROPERFMT ", available: " PROPERFMT,
              PROPERFMTARGS(v_used), PROPERFMTARGS(v_used_regions), PROPERFMTARGS(v_humongous_waste),
              PROPERFMTARGS(v_soft_max_capacity), PROPERFMTARGS(v_max_capacity), PROPERFMTARGS(v_available));
}

template <bool PREPARE_FOR_CURRENT_CYCLE, bool FULL_GC>
void ShenandoahGeneration::reset_mark_bitmap() {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  heap->assert_gc_workers(heap->workers()->active_workers());

  set_mark_incomplete();

  ShenandoahResetBitmapClosure<PREPARE_FOR_CURRENT_CYCLE, FULL_GC> closure;
  parallel_heap_region_iterate_free(&closure);
}
// Explicit specializations
template void ShenandoahGeneration::reset_mark_bitmap<true, false>();
template void ShenandoahGeneration::reset_mark_bitmap<true, true>();
template void ShenandoahGeneration::reset_mark_bitmap<false, false>();

// Swap the read and write card table pointers prior to the next remset scan.
// This avoids the need to synchronize reads of the table by the GC workers
// doing remset scanning, on the one hand, with the dirtying of the table by
// mutators on the other.
void ShenandoahGeneration::swap_card_tables() {
  // Must be sure that marking is complete before we swap remembered set.
  ShenandoahGenerationalHeap* heap = ShenandoahGenerationalHeap::heap();
  heap->assert_gc_workers(heap->workers()->active_workers());
  shenandoah_assert_safepoint();

  ShenandoahOldGeneration* old_generation = heap->old_generation();
  old_generation->card_scan()->swap_card_tables();
}

// Copy the write-version of the card-table into the read-version, clearing the
// write-version. The work is done at a safepoint and in parallel by the GC
// worker threads.
void ShenandoahGeneration::merge_write_table() {
  // This should only happen for degenerated cycles
  ShenandoahGenerationalHeap* heap = ShenandoahGenerationalHeap::heap();
  heap->assert_gc_workers(heap->workers()->active_workers());
  shenandoah_assert_safepoint();

  ShenandoahOldGeneration* old_generation = heap->old_generation();
  ShenandoahMergeWriteTable task(old_generation->card_scan());
  old_generation->parallel_heap_region_iterate(&task);
}

void ShenandoahGeneration::prepare_gc() {
  reset_mark_bitmap<true>();
}

void ShenandoahGeneration::parallel_heap_region_iterate_free(ShenandoahHeapRegionClosure* cl) {
  ShenandoahHeap::heap()->parallel_heap_region_iterate(cl);
}

void ShenandoahGeneration::compute_evacuation_budgets(ShenandoahHeap* const heap) {
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
  if (is_global()) {
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
  const size_t consumed_by_advance_promotion = select_aged_regions(is_global()? 0: old_promo_reserve);
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

// Having chosen the collection set, adjust the budgets for generational mode based on its composition.  Note
// that young_generation->available() now knows about recently discovered immediate garbage.
void ShenandoahGeneration::adjust_evacuation_budgets(ShenandoahHeap* const heap,
                                                     ShenandoahCollectionSet* const collection_set, size_t add_regions_to_old) {
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

  size_t total_young_available = young_generation->available_with_reserve() - add_regions_to_old * region_size_bytes;;
  assert(young_evacuated_reserve_used <= total_young_available, "Cannot evacuate (%zu) more than is available in young (%zu)",
         young_evacuated_reserve_used, total_young_available);
  young_generation->set_evacuation_reserve(young_evacuated_reserve_used);

  // We have not yet rebuilt the free set.  Some of the memory that is thought to be avaiable within old may no
  // longer be available if that memory had been free within regions that were selected for the collection set.
  // Make the necessary adjustments to old_available.
  size_t old_available =
    old_generation->available() + add_regions_to_old * region_size_bytes - collection_set->get_old_available_bytes_collected();

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
  size_t unaffiliated_old_regions = old_generation->free_unaffiliated_regions() + add_regions_to_old;
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

// Preselect for inclusion into the collection set all regions whose age is at or above tenure age and for which the
// garbage percentage exceeds a dynamically adjusted threshold (known as the old-garbage threshold percentage).  We
// identify these regions by setting the appropriate entry of the collection set's preselected regions array to true.
// All entries are initialized to false before calling this function.
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
// of aged regions must be reserved in the old generation.  Memory for evacuation of all other regions must be
// reserved in the young generation.
size_t ShenandoahGeneration::select_aged_regions(const size_t old_promotion_reserve) {

  // There should be no regions configured for subsequent in-place-promotions carried over from the previous cycle.
  assert_no_in_place_promotions();

  auto const heap = ShenandoahGenerationalHeap::heap();
  ShenandoahFreeSet* free_set = heap->free_set();
  bool* const candidate_regions_for_promotion_by_copy = heap->collection_set()->preselected_regions();
  ShenandoahMarkingContext* const ctx = heap->marking_context();

  const size_t old_garbage_threshold =
    (ShenandoahHeapRegion::region_size_bytes() * heap->old_generation()->heuristics()->get_old_garbage_threshold()) / 100;

  const size_t pip_used_threshold = (ShenandoahHeapRegion::region_size_bytes() * ShenandoahGenerationalMinPIPUsage) / 100;

  size_t promo_potential = 0;
  size_t candidates = 0;

  // Tracks the padding of space above top in regions eligible for promotion in place
  size_t promote_in_place_pad = 0;

  // Sort the promotion-eligible regions in order of increasing live-data-bytes so that we can first reclaim regions that require
  // less evacuation effort.  This prioritizes garbage first, expanding the allocation pool early before we reclaim regions that
  // have more live data.
  const idx_t num_regions = heap->num_regions();

  ResourceMark rm;
  AgedRegionData* sorted_regions = NEW_RESOURCE_ARRAY(AgedRegionData, num_regions);

  ShenandoahFreeSet* freeset = heap->free_set();

  // Any region that is to be promoted in place needs to be retired from its Collector or Mutator partition.
  idx_t pip_low_collector_idx = freeset->max_regions();
  idx_t pip_high_collector_idx = -1;
  idx_t pip_low_mutator_idx = freeset->max_regions();
  idx_t pip_high_mutator_idx = -1;
  size_t collector_regions_to_pip = 0;
  size_t mutator_regions_to_pip = 0;

  size_t pip_mutator_regions = 0;
  size_t pip_collector_regions = 0;
  size_t pip_mutator_bytes = 0;
  size_t pip_collector_bytes = 0;

  for (idx_t i = 0; i < num_regions; i++) {
    ShenandoahHeapRegion* const r = heap->get_region(i);
    if (r->is_empty() || !r->has_live() || !r->is_young() || !r->is_regular()) {
      // skip over regions that aren't regular young with some live data
      continue;
    }
    if (heap->is_tenurable(r)) {
      if ((r->garbage() < old_garbage_threshold) && (r->used() > pip_used_threshold)) {
        // We prefer to promote this region in place because it has a small amount of garbage and a large usage.
        HeapWord* tams = ctx->top_at_mark_start(r);
        HeapWord* original_top = r->top();
        if (!heap->is_concurrent_old_mark_in_progress() && tams == original_top) {
          // No allocations from this region have been made during concurrent mark. It meets all the criteria
          // for in-place-promotion. Though we only need the value of top when we fill the end of the region,
          // we use this field to indicate that this region should be promoted in place during the evacuation
          // phase.
          r->save_top_before_promote();
          size_t remnant_bytes = r->free();
          size_t remnant_words = remnant_bytes / HeapWordSize;
          assert(ShenandoahHeap::min_fill_size() <= PLAB::min_size(), "Implementation makes invalid assumptions");
          if (remnant_words >= ShenandoahHeap::min_fill_size()) {
            ShenandoahHeap::fill_with_object(original_top, remnant_words);
            // Fill the remnant memory within this region to assure no allocations prior to promote in place.  Otherwise,
            // newly allocated objects will not be parsable when promote in place tries to register them.  Furthermore, any
            // new allocations would not necessarily be eligible for promotion.  This addresses both issues.
            r->set_top(r->end());
            // The region r is either in the Mutator or Collector partition if remnant_words > heap()->plab_min_size.
            // Otherwise, the region is in the NotFree partition.
            ShenandoahFreeSetPartitionId p = free_set->membership(i);
            if (p == ShenandoahFreeSetPartitionId::Mutator) {
              mutator_regions_to_pip++;
              if (i < pip_low_mutator_idx) {
                pip_low_mutator_idx = i;
              }
              if (i > pip_high_mutator_idx) {
                pip_high_mutator_idx = i;
              }
              pip_mutator_regions++;
              pip_mutator_bytes += remnant_bytes;
            } else if (p == ShenandoahFreeSetPartitionId::Collector) {
              collector_regions_to_pip++;
              if (i < pip_low_collector_idx) {
                pip_low_collector_idx = i;
              }
              if (i > pip_high_collector_idx) {
                pip_high_collector_idx = i;
              }
              pip_collector_regions++;
              pip_collector_bytes += remnant_bytes;
            } else {
              assert((p == ShenandoahFreeSetPartitionId::NotFree) && (remnant_words < heap->plab_min_size()),
                     "Should be NotFree if not in Collector or Mutator partitions");
              // In this case, the memory is already counted as used and the region has already been retired.  There is
              // no need for further adjustments to used.  Further, the remnant memory for this region will not be
              // unallocated or made available to OldCollector after pip.
              remnant_bytes = 0;
            }
            promote_in_place_pad += remnant_bytes;
            free_set->prepare_to_promote_in_place(i, remnant_bytes);
          } else {
            // Since the remnant is so small that this region has already been retired, we don't have to worry about any
            // accidental allocations occurring within this region before the region is promoted in place.

            // This region was already not in the Collector or Mutator set, so no need to remove it.
            assert(free_set->membership(i) == ShenandoahFreeSetPartitionId::NotFree, "sanity");
          }
        }
        // Else, we do not promote this region (either in place or by copy) because it has received new allocations.

        // During evacuation, we exclude from promotion regions for which age > tenure threshold, garbage < garbage-threshold,
        //  used > pip_used_threshold, and get_top_before_promote() != tams
      } else {
        // Record this promotion-eligible candidate region. After sorting and selecting the best candidates below,
        // we may still decide to exclude this promotion-eligible region from the current collection set.  If this
        // happens, we will consider this region as part of the anticipated promotion potential for the next GC
        // pass; see further below.
        sorted_regions[candidates]._region = r;
        sorted_regions[candidates++]._live_data = r->get_live_data_bytes();
      }
    } else {
      // We only evacuate & promote objects from regular regions whose garbage() is above old-garbage-threshold.
      // Objects in tenure-worthy regions with less garbage are promoted in place. These take a different path to
      // old-gen.  Regions excluded from promotion because their garbage content is too low (causing us to anticipate that
      // the region would be promoted in place) may be eligible for evacuation promotion by the time promotion takes
      // place during a subsequent GC pass because more garbage is found within the region between now and then.  This
      // should not happen if we are properly adapting the tenure age.  The theory behind adaptive tenuring threshold
      // is to choose the youngest age that demonstrates no "significant" further loss of population since the previous
      // age.  If not this, we expect the tenure age to demonstrate linear population decay for at least two population
      // samples, whereas we expect to observe exponential population decay for ages younger than the tenure age.
      //
      // In the case that certain regions which were anticipated to be promoted in place need to be promoted by
      // evacuation, it may be the case that there is not sufficient reserve within old-gen to hold evacuation of
      // these regions.  The likely outcome is that these regions will not be selected for evacuation or promotion
      // in the current cycle and we will anticipate that they will be promoted in the next cycle.  This will cause
      // us to reserve more old-gen memory so that these objects can be promoted in the subsequent cycle.
      if (heap->is_aging_cycle() && heap->age_census()->is_tenurable(r->age() + 1)) {
        if (r->garbage() >= old_garbage_threshold) {
          promo_potential += r->get_live_data_bytes();
        }
      }
    }
    // Note that we keep going even if one region is excluded from selection.
    // Subsequent regions may be selected if they have smaller live data.
  }

  if (pip_mutator_regions + pip_collector_regions > 0) {
    freeset->account_for_pip_regions(pip_mutator_regions, pip_mutator_bytes, pip_collector_regions, pip_collector_bytes);
  }

  // Retire any regions that have been selected for promote in place
  if (collector_regions_to_pip > 0) {
    freeset->shrink_interval_if_range_modifies_either_boundary(ShenandoahFreeSetPartitionId::Collector,
                                                               pip_low_collector_idx, pip_high_collector_idx,
                                                               collector_regions_to_pip);
  }
  if (mutator_regions_to_pip > 0) {
    freeset->shrink_interval_if_range_modifies_either_boundary(ShenandoahFreeSetPartitionId::Mutator,
                                                               pip_low_mutator_idx, pip_high_mutator_idx,
                                                               mutator_regions_to_pip);
  }

  // Sort in increasing order according to live data bytes.  Note that candidates represents the number of regions
  // that qualify to be promoted by evacuation.
  size_t old_consumed = 0;
  if (candidates > 0) {
    size_t selected_regions = 0;
    size_t selected_live = 0;
    QuickSort::sort<AgedRegionData>(sorted_regions, candidates, compare_by_aged_live);
    for (size_t i = 0; i < candidates; i++) {
      ShenandoahHeapRegion* const region = sorted_regions[i]._region;
      const size_t region_live_data = sorted_regions[i]._live_data;
      const size_t promotion_need = (size_t) (region_live_data * ShenandoahPromoEvacWaste);
      if (old_consumed + promotion_need <= old_promotion_reserve) {
        old_consumed += promotion_need;
        candidate_regions_for_promotion_by_copy[region->index()] = true;
        selected_regions++;
        selected_live += region_live_data;
      } else {
        // We rejected this promotable region from the collection set because we had no room to hold its copy.
        // Add this region to promo potential for next GC.
        promo_potential += region_live_data;
        assert(!candidate_regions_for_promotion_by_copy[region->index()], "Shouldn't be selected");
      }
      // We keep going even if one region is excluded from selection because we need to accumulate all eligible
      // regions that are not preselected into promo_potential
    }
    log_debug(gc, ergo)("Preselected %zu regions containing " PROPERFMT " live data,"
                        " consuming: " PROPERFMT " of budgeted: " PROPERFMT,
                        selected_regions, PROPERFMTARGS(selected_live), PROPERFMTARGS(old_consumed), PROPERFMTARGS(old_promotion_reserve));
  }

  log_info(gc, ergo)("Promotion potential of aged regions with sufficient garbage: " PROPERFMT, PROPERFMTARGS(promo_potential));

  heap->old_generation()->set_pad_for_promote_in_place(promote_in_place_pad);
  heap->old_generation()->set_promotion_potential(promo_potential);
  return old_consumed;
}

void ShenandoahGeneration::prepare_regions_and_collection_set(bool concurrent) {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  ShenandoahCollectionSet* collection_set = heap->collection_set();
  bool is_generational = heap->mode()->is_generational();

  assert(!heap->is_full_gc_in_progress(), "Only for concurrent and degenerated GC");
  assert(!is_old(), "Only YOUNG and GLOBAL GC perform evacuations");
  {
    ShenandoahGCPhase phase(concurrent ? ShenandoahPhaseTimings::final_update_region_states :
                            ShenandoahPhaseTimings::degen_gc_final_update_region_states);
    ShenandoahFinalMarkUpdateRegionStateClosure cl(complete_marking_context());
    parallel_heap_region_iterate(&cl);

    if (is_young()) {
      // We always need to update the watermark for old regions. If there
      // are mixed collections pending, we also need to synchronize the
      // pinned status for old regions. Since we are already visiting every
      // old region here, go ahead and sync the pin status too.
      ShenandoahFinalMarkUpdateRegionStateClosure old_cl(nullptr);
      heap->old_generation()->parallel_heap_region_iterate(&old_cl);
    }
  }

  // Tally the census counts and compute the adaptive tenuring threshold
  if (is_generational && ShenandoahGenerationalAdaptiveTenuring) {
    // Objects above TAMS weren't included in the age census. Since they were all
    // allocated in this cycle they belong in the age 0 cohort. We walk over all
    // young regions and sum the volume of objects between TAMS and top.
    ShenandoahUpdateCensusZeroCohortClosure age0_cl(complete_marking_context());
    heap->young_generation()->heap_region_iterate(&age0_cl);
    size_t age0_pop = age0_cl.get_age0_population();

    // Update the global census, including the missed age 0 cohort above,
    // along with the census done during marking, and compute the tenuring threshold.
    ShenandoahAgeCensus* census = ShenandoahGenerationalHeap::heap()->age_census();
    census->update_census(age0_pop);
#ifndef PRODUCT
    size_t total_pop = age0_cl.get_total_population();
    size_t total_census = census->get_total();
    // Usually total_pop > total_census, but not by too much.
    // We use integer division so anything up to just less than 2 is considered
    // reasonable, and the "+1" is to avoid divide-by-zero.
    assert((total_pop+1)/(total_census+1) ==  1, "Extreme divergence: "
           "%zu/%zu", total_pop, total_census);
#endif
  }

  {
    ShenandoahGCPhase phase(concurrent ? ShenandoahPhaseTimings::choose_cset :
                            ShenandoahPhaseTimings::degen_gc_choose_cset);

    collection_set->clear();
    ShenandoahHeapLocker locker(heap->lock());
    if (is_generational) {
      // Seed the collection set with resource area-allocated
      // preselected regions, which are removed when we exit this scope.
      ShenandoahCollectionSetPreselector preselector(collection_set, heap->num_regions());

      // Find the amount that will be promoted, regions that will be promoted in
      // place, and preselected older regions that will be promoted by evacuation.
      compute_evacuation_budgets(heap);

      // Choose the collection set, including the regions preselected above for promotion into the old generation.
      size_t add_regions_to_old = _heuristics->choose_collection_set(collection_set);
      // Even if collection_set->is_empty(), we want to adjust budgets, making reserves available to mutator.
      adjust_evacuation_budgets(heap, collection_set, add_regions_to_old);
      if (is_global()) {
        // We have just chosen a collection set for a global cycle. The mark bitmap covering old regions is complete, so
        // the remembered set scan can use that to avoid walking into garbage. When the next old mark begins, we will
        // use the mark bitmap to make the old regions parsable by coalescing and filling any unmarked objects. Thus,
        // we prepare for old collections by remembering which regions are old at this time. Note that any objects
        // promoted into old regions will be above TAMS, and so will be considered marked. However, free regions that
        // become old after this point will not be covered correctly by the mark bitmap, so we must be careful not to
        // coalesce those regions. Only the old regions which are not part of the collection set at this point are
        // eligible for coalescing. As implemented now, this has the side effect of possibly initiating mixed-evacuations
        // after a global cycle for old regions that were not included in this collection set.
        heap->old_generation()->prepare_for_mixed_collections_after_global_gc();
      }
    } else {
      _heuristics->choose_collection_set(collection_set);
    }
  }


  {
    ShenandoahGCPhase phase(concurrent ? ShenandoahPhaseTimings::final_rebuild_freeset :
                            ShenandoahPhaseTimings::degen_gc_final_rebuild_freeset);
    ShenandoahHeapLocker locker(heap->lock());

    // We are preparing for evacuation.
    size_t young_trashed_regions, old_trashed_regions, first_old, last_old, num_old;
    _free_set->prepare_to_rebuild(young_trashed_regions, old_trashed_regions, first_old, last_old, num_old);
    if (heap->mode()->is_generational()) {
      ShenandoahGenerationalHeap* gen_heap = ShenandoahGenerationalHeap::heap();
    size_t allocation_runway =
      gen_heap->young_generation()->heuristics()->bytes_of_allocation_runway_before_gc_trigger(young_trashed_regions);
      gen_heap->compute_old_generation_balance(allocation_runway, old_trashed_regions, young_trashed_regions);
    }
    _free_set->finish_rebuild(young_trashed_regions, old_trashed_regions, num_old);
  }
}

bool ShenandoahGeneration::is_bitmap_clear() {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  ShenandoahMarkingContext* context = heap->marking_context();
  const size_t num_regions = heap->num_regions();
  for (size_t idx = 0; idx < num_regions; idx++) {
    ShenandoahHeapRegion* r = heap->get_region(idx);
    if (contains(r) && r->is_affiliated()) {
      if (heap->is_bitmap_slice_committed(r) && (context->top_at_mark_start(r) > r->bottom()) &&
          !context->is_bitmap_range_within_region_clear(r->bottom(), r->end())) {
        return false;
      }
    }
  }
  return true;
}

void ShenandoahGeneration::set_mark_complete() {
  _is_marking_complete.set();
}

void ShenandoahGeneration::set_mark_incomplete() {
  _is_marking_complete.unset();
}

ShenandoahMarkingContext* ShenandoahGeneration::complete_marking_context() {
  assert(is_mark_complete(), "Marking must be completed.");
  return ShenandoahHeap::heap()->marking_context();
}

void ShenandoahGeneration::cancel_marking() {
  log_info(gc)("Cancel marking: %s", name());
  if (is_concurrent_mark_in_progress()) {
    set_mark_incomplete();
  }
  _task_queues->clear();
  ref_processor()->abandon_partial_discovery();
  set_concurrent_mark_in_progress(false);
}

ShenandoahGeneration::ShenandoahGeneration(ShenandoahGenerationType type,
                                           uint max_workers) :
  _type(type),
  _task_queues(new ShenandoahObjToScanQueueSet(max_workers)),
  _ref_processor(new ShenandoahReferenceProcessor(this, MAX2(max_workers, 1U))),
  _evacuation_reserve(0),
  _free_set(nullptr),
  _heuristics(nullptr)
{
  _is_marking_complete.set();
  assert(max_workers > 0, "At least one queue");
  for (uint i = 0; i < max_workers; ++i) {
    ShenandoahObjToScanQueue* task_queue = new ShenandoahObjToScanQueue();
    _task_queues->register_queue(i, task_queue);
  }
}

ShenandoahGeneration::~ShenandoahGeneration() {
  for (uint i = 0; i < _task_queues->size(); ++i) {
    ShenandoahObjToScanQueue* q = _task_queues->queue(i);
    delete q;
  }
  delete _task_queues;
}

void ShenandoahGeneration::post_initialize(ShenandoahHeap* heap) {
  _free_set = heap->free_set();
  assert(_free_set != nullptr, "bad initialization order");
}

void ShenandoahGeneration::reserve_task_queues(uint workers) {
  _task_queues->reserve(workers);
}

ShenandoahObjToScanQueueSet* ShenandoahGeneration::old_gen_task_queues() const {
  return nullptr;
}

void ShenandoahGeneration::scan_remembered_set(bool is_concurrent) {
  assert(is_young(), "Should only scan remembered set for young generation.");

  ShenandoahGenerationalHeap* const heap = ShenandoahGenerationalHeap::heap();
  uint nworkers = heap->workers()->active_workers();
  reserve_task_queues(nworkers);

  ShenandoahReferenceProcessor* rp = ref_processor();
  ShenandoahRegionChunkIterator work_list(nworkers);
  ShenandoahScanRememberedTask task(task_queues(), old_gen_task_queues(), rp, &work_list, is_concurrent);
  heap->assert_gc_workers(nworkers);
  heap->workers()->run_task(&task);
  if (ShenandoahEnableCardStats) {
    ShenandoahScanRemembered* scanner = heap->old_generation()->card_scan();
    assert(scanner != nullptr, "Not generational");
    scanner->log_card_stats(nworkers, CARD_STAT_SCAN_RS);
  }
}

size_t ShenandoahGeneration::available() const {
  size_t result = available(max_capacity());
  return result;
}

// For ShenandoahYoungGeneration, Include the young available that may have been reserved for the Collector.
size_t ShenandoahGeneration::available_with_reserve() const {
  size_t result = available(max_capacity());
  return result;
}

size_t ShenandoahGeneration::soft_mutator_available() const {
  size_t result = available(ShenandoahHeap::heap()->soft_max_capacity() * (100.0 - ShenandoahEvacReserve) / 100);
  return result;
}

size_t ShenandoahGeneration::available(size_t capacity) const {
  size_t in_use = used();
  size_t result = in_use > capacity ? 0 : capacity - in_use;
  return result;
}

void ShenandoahGeneration::record_success_concurrent(bool abbreviated) {
  heuristics()->record_success_concurrent();
  ShenandoahHeap::heap()->shenandoah_policy()->record_success_concurrent(is_young(), abbreviated);
}
