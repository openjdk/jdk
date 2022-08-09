/*
 * Copyright (c) 2020, 2021 Amazon.com, Inc. and/or its affiliates. All rights reserved.
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
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahMarkClosures.hpp"
#include "gc/shenandoah/shenandoahMonitoringSupport.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahReferenceProcessor.hpp"
#include "gc/shenandoah/shenandoahTaskqueue.inline.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "gc/shenandoah/shenandoahVerifier.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"
#include "gc/shenandoah/heuristics/shenandoahHeuristics.hpp"

class ShenandoahResetUpdateRegionStateClosure : public ShenandoahHeapRegionClosure {
 private:
  ShenandoahMarkingContext* const _ctx;
 public:
  ShenandoahResetUpdateRegionStateClosure() :
    _ctx(ShenandoahHeap::heap()->marking_context()) {}

  void heap_region_do(ShenandoahHeapRegion* r) {
    if (r->is_active()) {
      // Reset live data and set TAMS optimistically. We would recheck these under the pause
      // anyway to capture any updates that happened since now.
      _ctx->capture_top_at_mark_start(r);
      r->clear_live_data();
    }
  }

  bool is_thread_safe() { return true; }
};

class ShenandoahResetBitmapTask : public ShenandoahHeapRegionClosure {
 private:
  ShenandoahHeap* _heap;
  ShenandoahMarkingContext* const _ctx;
 public:
  ShenandoahResetBitmapTask() :
    _heap(ShenandoahHeap::heap()),
    _ctx(_heap->marking_context()) {}

  void heap_region_do(ShenandoahHeapRegion* region) {
    if (_heap->is_bitmap_slice_committed(region)) {
      _ctx->clear_bitmap(region);
    }
  }

  bool is_thread_safe() { return true; }
};

class ShenandoahMergeWriteTable: public ShenandoahHeapRegionClosure {
 private:
  ShenandoahHeap* _heap;
  RememberedScanner* _scanner;
 public:
  ShenandoahMergeWriteTable() : _heap(ShenandoahHeap::heap()), _scanner(_heap->card_scan()) {}

  virtual void heap_region_do(ShenandoahHeapRegion* r) override {
    if (r->is_old()) {
      _scanner->merge_write_table(r->bottom(), ShenandoahHeapRegion::region_size_words());
    }
  }

  virtual bool is_thread_safe() override {
    return true;
  }
};

class ShenandoahSquirrelAwayCardTable: public ShenandoahHeapRegionClosure {
 private:
  ShenandoahHeap* _heap;
  RememberedScanner* _scanner;
 public:
  ShenandoahSquirrelAwayCardTable() :
    _heap(ShenandoahHeap::heap()),
    _scanner(_heap->card_scan()) {}

  void heap_region_do(ShenandoahHeapRegion* region) {
    if (region->is_old()) {
      _scanner->reset_remset(region->bottom(), ShenandoahHeapRegion::region_size_words());
    }
  }

  bool is_thread_safe() { return true; }
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

size_t ShenandoahGeneration::bytes_allocated_since_gc_start() {
  return Atomic::load(&_bytes_allocated_since_gc_start);;
}

void ShenandoahGeneration::reset_bytes_allocated_since_gc_start() {
  Atomic::store(&_bytes_allocated_since_gc_start, (size_t)0);
}

void ShenandoahGeneration::increase_allocated(size_t bytes) {
  Atomic::add(&_bytes_allocated_since_gc_start, bytes, memory_order_relaxed);
}

void ShenandoahGeneration::log_status() const {
  typedef LogTarget(Info, gc, ergo) LogGcInfo;

  if (!LogGcInfo::is_enabled()) {
    return;
  }

  // Not under a lock here, so read each of these once to make sure
  // byte size in proper unit and proper unit for byte size are consistent.
  size_t v_used = used();
  size_t v_used_regions = used_regions_size();
  size_t v_soft_max_capacity = soft_max_capacity();
  size_t v_max_capacity = max_capacity();
  size_t v_available = available();
  LogGcInfo::print("%s generation used: " SIZE_FORMAT "%s, used regions: " SIZE_FORMAT "%s, "
                   "soft capacity: " SIZE_FORMAT "%s, max capacity: " SIZE_FORMAT " %s, available: " SIZE_FORMAT " %s",
                   name(),
                   byte_size_in_proper_unit(v_used), proper_unit_for_byte_size(v_used),
                   byte_size_in_proper_unit(v_used_regions), proper_unit_for_byte_size(v_used_regions),
                   byte_size_in_proper_unit(v_soft_max_capacity), proper_unit_for_byte_size(v_soft_max_capacity),
                   byte_size_in_proper_unit(v_max_capacity), proper_unit_for_byte_size(v_max_capacity),
                   byte_size_in_proper_unit(v_available), proper_unit_for_byte_size(v_available));
}

void ShenandoahGeneration::reset_mark_bitmap() {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  heap->assert_gc_workers(heap->workers()->active_workers());

  set_mark_incomplete();

  ShenandoahResetBitmapTask task;
  parallel_heap_region_iterate(&task);
}

// The ideal is to swap the remembered set so the safepoint effort is no more than a few pointer manipulations.
// However, limitations in the implementation of the mutator write-barrier make it difficult to simply change the
// location of the card table.  So the interim implementation of swap_remembered_set will copy the write-table
// onto the read-table and will then clear the write-table.
void ShenandoahGeneration::swap_remembered_set() {
  // Must be sure that marking is complete before we swap remembered set.
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  heap->assert_gc_workers(heap->workers()->active_workers());
  shenandoah_assert_safepoint();

  // TODO: Eventually, we want replace this with a constant-time exchange of pointers.
  ShenandoahSquirrelAwayCardTable task;
  heap->old_generation()->parallel_heap_region_iterate(&task);
}

// If a concurrent cycle fails _after_ the card table has been swapped we need to update the read card
// table with any writes that have occurred during the transition to the degenerated cycle. Without this,
// newly created objects which are only referenced by old objects could be lost when the remembered set
// is scanned during the degenerated mark.
void ShenandoahGeneration::merge_write_table() {
  // This should only happen for degenerated cycles
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  heap->assert_gc_workers(heap->workers()->active_workers());
  shenandoah_assert_safepoint();

  ShenandoahMergeWriteTable task;
  heap->old_generation()->parallel_heap_region_iterate(&task);
}

void ShenandoahGeneration::prepare_gc() {
  // Reset mark bitmap for this generation (typically young)
  reset_mark_bitmap();
  // Capture Top At Mark Start for this generation (typically young)
  ShenandoahResetUpdateRegionStateClosure cl;
  parallel_heap_region_iterate(&cl);
}

void ShenandoahGeneration::compute_evacuation_budgets(ShenandoahHeap* heap, bool* preselected_regions,
                                                      ShenandoahCollectionSet* collection_set,
                                                      size_t &old_regions_loaned_for_young_evac, size_t &regions_available_to_loan,
                                                      size_t &minimum_evacuation_reserve, size_t &consumed_by_advance_promotion) {
  size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();
  minimum_evacuation_reserve = ShenandoahOldCompactionReserve * region_size_bytes;
  old_regions_loaned_for_young_evac = 0;
  regions_available_to_loan = 0;
  consumed_by_advance_promotion = 0;
  if (heap->mode()->is_generational()) {
    ShenandoahGeneration* old_generation = heap->old_generation();
    ShenandoahYoungGeneration* young_generation = heap->young_generation();
    size_t avail_evac_reserve_for_loan_to_young_gen = 0;
    size_t old_evacuation_reserve = 0;
    size_t num_regions = heap->num_regions();

    // During initialization and phase changes, it is more likely that fewer objects die young and old-gen
    // memory is not yet full (or is in the process of being replaced).  During these times especially, it
    // is beneficial to loan memory from old-gen to young-gen during the evacuation and update-refs phases
    // of execution.

    // Calculate EvacuationReserve before PromotionReserve.  Evacuation is more critical than promotion.
    // If we cannot evacuate old-gen, we will not be able to reclaim old-gen memory.  Promotions are less
    // critical.  If we cannot promote, there may be degradation of young-gen memory because old objects
    // accumulate there until they can be promoted.  This increases the young-gen marking and evacuation work.

    // Do not fill up old-gen memory with promotions.  Reserve some amount of memory for compaction purposes.
    ShenandoahOldHeuristics* old_heuristics = heap->old_heuristics();
    size_t young_evac_reserve_max = 0;
    if (old_heuristics->unprocessed_old_collection_candidates() > 0) {
      // Compute old_evacuation_reserve: how much memory are we reserving to hold the results of
      // evacuating old-gen heap regions?  In order to sustain a consistent pace of young-gen collections,
      // the goal is to maintain a consistent value for this parameter (when the candidate set is not
      // empty).  This value is the minimum of:
      //   1. old_gen->available()
      //   2. old-gen->capacity() * ShenandoahOldEvacReserve) / 100
      //       (e.g. old evacuation should be no larger than 5% of old_gen capacity)
      //   3. ((young_gen->capacity * ShenandoahEvacReserve / 100) * ShenandoahOldEvacRatioPercent) / 100
      //       (e.g. old evacuation should be no larger than 12% of young-gen evacuation)
      old_evacuation_reserve = old_generation->available();
      assert(old_evacuation_reserve > minimum_evacuation_reserve, "Old-gen available has not been preserved!");
      size_t old_evac_reserve_max = old_generation->soft_max_capacity() * ShenandoahOldEvacReserve / 100;
      if (old_evac_reserve_max < old_evacuation_reserve) {
        old_evacuation_reserve = old_evac_reserve_max;
      }
      young_evac_reserve_max =
        (((young_generation->soft_max_capacity() * ShenandoahEvacReserve) / 100) * ShenandoahOldEvacRatioPercent) / 100;
      if (young_evac_reserve_max < old_evacuation_reserve) {
        old_evacuation_reserve = young_evac_reserve_max;
      }
    }

    if (minimum_evacuation_reserve > old_generation->available()) {
      // Due to round-off errors during enforcement of minimum_evacuation_reserve during previous GC passes,
      // there can be slight discrepancies here.
      minimum_evacuation_reserve = old_generation->available();
    }
    if (old_evacuation_reserve < minimum_evacuation_reserve) {
      // Even if there's nothing to be evacuated on this cycle, we still need to reserve this memory for future
      // evacuations.  It is ok to loan this memory to young-gen if we don't need it for evacuation on this pass.
      avail_evac_reserve_for_loan_to_young_gen = minimum_evacuation_reserve - old_evacuation_reserve;
      old_evacuation_reserve = minimum_evacuation_reserve;
    }

    heap->set_old_evac_reserve(old_evacuation_reserve);
    heap->reset_old_evac_expended();

    // Compute the young evauation reserve: This is how much memory is available for evacuating young-gen objects.
    // We ignore the possible effect of promotions, which reduce demand for young-gen evacuation memory.
    //
    // TODO: We could give special treatment to the regions that have reached promotion age, because we know their
    // live data is entirely eligible for promotion.  This knowledge can feed both into calculations of young-gen
    // evacuation reserve and promotion reserve.
    //
    //  young_evacuation_reserve for young generation: how much memory are we reserving to hold the results
    //  of evacuating young collection set regions?  This is typically smaller than the total amount
    //  of available memory, and is also smaller than the total amount of marked live memory within
    //  young-gen.  This value is the smaller of
    //
    //    1. (young_gen->capacity() * ShenandoahEvacReserve) / 100
    //    2. (young_gen->available() + old_gen_memory_available_to_be_loaned
    //
    //  ShenandoahEvacReserve represents the configured taget size of the evacuation region.  We can only honor
    //  this target if there is memory available to hold the evacuations.  Memory is available if it is already
    //  free within young gen, or if it can be borrowed from old gen.  Since we have not yet chosen the collection
    //  sets, we do not yet know the exact accounting of how many regions will be freed by this collection pass.
    //  What we do know is that there will be at least one evacuated young-gen region for each old-gen region that
    //  is loaned to the evacuation effort (because regions to be collected consume more memory than the compacted
    //  regions that will replace them).  In summary, if there are old-gen regions that are available to hold the
    //  results of young-gen evacuations, it is safe to loan them for this purpose.  At this point, we have not yet
    //  established a promoted_reserve.  We'll do that after we choose the collection set and analyze its impact
    //  on available memory.
    //
    // We do not know the evacuation_supplement until after we have computed the collection set.  It is not always
    // the case that young-regions inserted into the collection set will result in net decrease of in-use regions
    // because ShenandoahEvacWaste times multiplied by memory within the region may be larger than the region size.
    // The problem is especially relevant to regions that have been inserted into the collection set because they have
    // reached tenure age.  These regions tend to have much higher utilization (e.g. 95%).  These regions also offer
    // a unique opportunity because we know that every live object contained within the region is elgible to be
    // promoted.  Thus, the following implementation treats these regions specially:
    //
    //  1. Before beginning collection set selection, we tally the total amount of live memory held within regions
    //     that are known to have reached tenure age.  If this memory times ShenandoahEvacWaste is available within
    //     old-gen memory, establish an advance promotion reserve to hold all or some percentage of these objects.
    //     This advance promotion reserve is excluded from memory available for holding old-gen evacuations and cannot
    //     be "loaned" to young gen.
    //
    //  2. Tenure-aged regions are included in the collection set iff their evacuation size * ShenandoahEvacWaste fits
    //     within the advance promotion reserve.  It is counter productive to evacuate these regions if they cannot be
    //     evacuated directly into old-gen memory.  So if there is not sufficient memory to hold copies of their
    //     live data right now, we'll just let these regions remain in young for now, to be evacuated by a subsequent
    //     evacuation pass.
    //
    //  3. Next, we calculate a young-gen evacuation budget, which is the smaller of the two quantities mentioned
    //     above.  old_gen_memory_available_to_be_loaned is calculated as:
    //       old_gen->available - (advance-promotion-reserve + old-gen_evacuation_reserve)
    //
    //  4. When choosing the collection set, special care is taken to assure that the amount of loaned memory required to
    //     hold the results of evacuation is smaller than the total memory occupied by the regions added to the collection
    //     set.  We need to take these precautions because we do not know how much memory will be reclaimed by evacuation
    //     until after the collection set has been constructed.  The algorithm is as follows:
    //
    //     a. We feed into the algorithm (i) young available at the start of evacuation and (ii) the amount of memory
    //        loaned from old-gen that is available to hold the results of evacuation.
    //     b. As candidate regions are added into the young-gen collection set, we maintain accumulations of the amount
    //        of memory spanned by the collection set regions and the amount of memory that must be reserved to hold
    //        evacuation results (by multiplying live-data size by ShenandoahEvacWaste).  We process candidate regions
    //        in order of decreasing amounts of garbage.  We skip over (and do not include into the collection set) any
    //        regions that do not satisfy all of the following conditions:
    //
    //          i. The amount of live data within the region as scaled by ShenandoahEvacWaste must fit within the
    //             relevant evacuation reserve (live data of old-gen regions must fit within the old-evac-reserve, live
    //             data of young-gen tenure-aged regions must fit within the advance promotion reserve, live data within
    //             other young-gen regions must fit within the youn-gen evacuation reserve).
    //         ii. The accumulation of memory consumed by evacuation must not exceed the accumulation of memory reclaimed
    //             through evacuation by more than young-gen available.
    //        iii. Other conditions may be enforced as appropriate for specific heuristics.
    //
    //       Note that regions are considered for inclusion in the selection set in order of decreasing amounts of garbage.
    //       It is possible that a region with a larger amount of garbage will be rejected because it also has a larger
    //       amount of live data and some region that follows this region in candidate order is included in the collection
    //       set (because it has less live data and thus can fit within the evacuation limits even though it has less
    //       garbage).

    size_t young_evacuation_reserve = (young_generation->max_capacity() * ShenandoahEvacReserve) / 100;
    // old evacuation can pack into existing partially used regions.  young evacuation and loans for young allocations
    // need to target regions that do not already hold any old-gen objects.  Round down.
    regions_available_to_loan = old_generation->free_unaffiliated_regions();
    consumed_by_advance_promotion = _heuristics->select_aged_regions(old_generation->available() - old_evacuation_reserve,
                                                                     num_regions, preselected_regions);
    size_t net_available_old_regions =
      (old_generation->available() - old_evacuation_reserve - consumed_by_advance_promotion) / region_size_bytes;

    if (regions_available_to_loan > net_available_old_regions) {
      regions_available_to_loan = net_available_old_regions;
    }

    // Otherwise, regions_available_to_loan is less than net_available_old_regions because available memory is
    // scattered between multiple partially used regions.

    if (young_evacuation_reserve > young_generation->available()) {
      size_t short_fall = young_evacuation_reserve - young_generation->available();
      if (regions_available_to_loan * region_size_bytes >= short_fall) {
        old_regions_loaned_for_young_evac = (short_fall + region_size_bytes - 1) / region_size_bytes;
        regions_available_to_loan -= old_regions_loaned_for_young_evac;
      } else {
        old_regions_loaned_for_young_evac = regions_available_to_loan;
        regions_available_to_loan = 0;
        young_evacuation_reserve = young_generation->available() + old_regions_loaned_for_young_evac * region_size_bytes;
        // In this case, there's no memory available for new allocations while evacuating and updating, unless we
        // find more old-gen memory to borrow below.
      }
    } else {
      old_regions_loaned_for_young_evac = 0;
    }
    // In generational mode, we may end up choosing a young collection set that contains so many promotable objects
    // that there is not sufficient space in old generation to hold the promoted objects.  That is ok because we have
    // assured there is sufficient space in young generation to hold the rejected promotion candidates.  These rejected
    // promotion candidates will presumably be promoted in a future evacuation cycle.
    heap->set_young_evac_reserve(young_evacuation_reserve);
    collection_set->establish_preselected(preselected_regions);
  } else {
    // Not generational mode: limit young evac reserve by young available; no need to establish old_evac_reserve.
    ShenandoahYoungGeneration* young_generation = heap->young_generation();
    size_t young_evac_reserve = (young_generation->soft_max_capacity() * ShenandoahEvacReserve) / 100;
    if (young_evac_reserve > young_generation->available()) {
      young_evac_reserve = young_generation->available();
    }
    heap->set_young_evac_reserve(young_evac_reserve);
  }
}

// Having chosen the collection set, adjust the budgets for generatioal mode based on its composition.  Note
// that young_generation->available() now knows about recently discovered immediate garbage.
void ShenandoahGeneration::adjust_evacuation_budgets(ShenandoahHeap* heap, ShenandoahCollectionSet* collection_set,
                                                     size_t old_regions_loaned_for_young_evac, size_t regions_available_to_loan,
                                                     size_t minimum_evacuation_reserve, size_t consumed_by_advance_promotion) {
  if (heap->mode()->is_generational()) {
    size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();
    ShenandoahOldGeneration* old_generation = heap->old_generation();
    ShenandoahYoungGeneration* young_generation = heap->young_generation();
    size_t old_evacuated = collection_set->get_old_bytes_reserved_for_evacuation();
    size_t old_evacuated_committed = (size_t) (ShenandoahEvacWaste * old_evacuated);
    size_t old_evacuation_reserve = heap->get_old_evac_reserve();
    // Immediate garbage found during choose_collection_set() is all young
    size_t immediate_garbage = collection_set->get_immediate_trash();
    size_t old_available = old_generation->available();
    size_t young_available = young_generation->available() + immediate_garbage;

    assert(consumed_by_advance_promotion >= collection_set->get_young_bytes_to_be_promoted() * ShenandoahEvacWaste,
           "Advance promotion should be at least young_bytes_to_be_promoted * ShenandoahEvacWaste");

    assert(consumed_by_advance_promotion <= (collection_set->get_young_bytes_to_be_promoted() * ShenandoahEvacWaste * 33) / 32,
           "Round-off errors should be less than 3.125%%, consumed by advance: " SIZE_FORMAT ", promoted: " SIZE_FORMAT,
           consumed_by_advance_promotion, (size_t) (collection_set->get_young_bytes_to_be_promoted() * ShenandoahEvacWaste));

    collection_set->abandon_preselected();
    if (old_evacuated_committed > old_evacuation_reserve) {
      // This should only happen due to round-off errors when enforcing ShenandoahEvacWaste
      assert(old_evacuated_committed <= (33 * old_evacuation_reserve) / 32,
             "Round-off errors should be less than 3.125%%, committed: " SIZE_FORMAT ", reserved: " SIZE_FORMAT,
             old_evacuated_committed, old_evacuation_reserve);
      old_evacuated_committed = old_evacuation_reserve;
    } else if (old_evacuated_committed < old_evacuation_reserve) {
      // This may happen if the old-gen collection consumes less than full budget.
      old_evacuation_reserve = old_evacuated_committed;
      heap->set_old_evac_reserve(old_evacuation_reserve);
    }

    // Recompute old_regions_loaned_for_young_evac because young-gen collection set may not need all the memory
    // originally reserved.
    size_t young_promoted = collection_set->get_young_bytes_to_be_promoted();
    size_t young_promoted_reserve_used = (size_t) (ShenandoahEvacWaste * young_promoted);

    size_t young_evacuated = collection_set->get_young_bytes_reserved_for_evacuation() - young_promoted;
    size_t young_evacuated_reserve_used = (size_t) (ShenandoahEvacWaste * young_evacuated);

    heap->set_young_evac_reserve(young_evacuated_reserve_used);

    // Adjust old_regions_loaned_for_young_evac to feed into calculations of promoted_reserve
    if (young_evacuated_reserve_used > young_available) {
      size_t short_fall = young_evacuated_reserve_used - young_available;

      // region_size_bytes is a power of 2.  loan an integral number of regions.
      size_t revised_loan_for_young_evacuation = (short_fall + region_size_bytes - 1) / region_size_bytes;

      // Undo the previous loan
      regions_available_to_loan += old_regions_loaned_for_young_evac;
      old_regions_loaned_for_young_evac = revised_loan_for_young_evacuation;
      // And make a new loan
      assert(regions_available_to_loan > old_regions_loaned_for_young_evac, "Cannot loan regions that we do not have");
      regions_available_to_loan -= old_regions_loaned_for_young_evac;
    } else {
      // Undo the prevous loan
      regions_available_to_loan += old_regions_loaned_for_young_evac;
      old_regions_loaned_for_young_evac = 0;
    }

    size_t old_bytes_loaned_for_young_evac = old_regions_loaned_for_young_evac * region_size_bytes;
    size_t old_bytes_reserved_for_alloc_supplement = 0;
    size_t old_regions_reserved_for_alloc_supplement = 0;
    // Need to enforce that old_evacuated_committed + old_bytes_loaned_for_young_evac >= minimum_evacuation_reserve
    // in order to prevent promotion reserve from violating minimum evacuation reserve.
    if (old_evacuated_committed + old_bytes_loaned_for_young_evac < minimum_evacuation_reserve) {
      // Reserve some of the regions available to loan for use as allocation supplement to assure memory not consumed by promotion
      size_t excess_bytes = minimum_evacuation_reserve - (old_evacuated_committed + old_bytes_loaned_for_young_evac);
      size_t excess_regions = (excess_bytes - 1 + region_size_bytes) / region_size_bytes;
      if (regions_available_to_loan <= excess_regions) {
        excess_regions = regions_available_to_loan;
        // Since we can't reserve entire excess for alloc supplement, pretend more is consumed by old-evacuation
        old_evacuated_committed =
          minimum_evacuation_reserve - old_bytes_loaned_for_young_evac - excess_regions * region_size_bytes;
      }
      regions_available_to_loan -= excess_regions;
      old_bytes_reserved_for_alloc_supplement = excess_regions * region_size_bytes;
      old_regions_reserved_for_alloc_supplement = excess_regions;
    }

    // Limit promoted_reserve so that we can set aside memory to be loaned from old-gen to young-gen.  This
    // value is not "critical".  If we underestimate, certain promotions will simply be deferred.  If we put
    // "all the rest" of old-gen memory into the promotion reserve, we'll have nothing left to loan to young-gen
    // during the evac and update phases of GC.  So we "limit" the sizes of the promotion budget to be the smaller of:
    //
    //  1. old_gen->available - (old_evacuation_committed + old_bytes_loaned_for_young_evac + consumed_by_advance_promotion)
    //  2. young bytes reserved for evacuation

    assert(old_available > old_evacuated_committed, "Cannot evacuate more than available");
    assert(old_available > old_evacuated_committed + old_bytes_loaned_for_young_evac,
           "Cannot loan young evac more than available");
    assert(old_available > old_evacuated_committed + old_bytes_loaned_for_young_evac + consumed_by_advance_promotion,
           "Cannot promote more than available");
    assert(old_available > (old_evacuated_committed + old_bytes_loaned_for_young_evac +
                                          consumed_by_advance_promotion + old_bytes_reserved_for_alloc_supplement),
           "Cannot loan for alloc supplement more than available");

    size_t promotion_reserve = old_available - (old_evacuated_committed + consumed_by_advance_promotion +
                                                old_bytes_loaned_for_young_evac + old_bytes_reserved_for_alloc_supplement);

    // We experimented with constraining promoted_reserve to be no larger than 4 times the size of previously_promoted,
    // but this constraint was too limiting, resulting in failure of legitimate promotions.  This was tried before we
    // had special handling in place for advance promotion.  We should retry now that advance promotion is handled
    // specially.

    // We had also experimented with constraining promoted_reserve to be no more than young_evacuation_committed
    // divided by promotion_divisor, where:
    //  size_t promotion_divisor = (0x02 << InitialTenuringThreshold) - 1;
    // This also was found to be too limiting, resulting in failure of legitimate promotions.
    //
    // Both experiments were conducted in the presence of other bugs which could have been the root cause for
    // the failures identified above as being "too limiting".  TODO: conduct new experiments with the more limiting
    // values of young_evacuation_reserved_used.

    // young_evacuation_reserve_used already excludes bytes known to be promoted, which equals consumed_by_advance_promotion
    if (young_evacuated_reserve_used < promotion_reserve) {
      // Shrink promotion_reserve if it is larger than the memory to be consumed by evacuating all young objects in
      // collection set, including anticipated waste.  There's no benefit in using a larger promotion_reserve.
      // young_evacuation_reserve_used does not include live memory within tenure-aged regions.
      promotion_reserve = young_evacuated_reserve_used;
    }

    assert(old_available >= (promotion_reserve + old_evacuated_committed + old_bytes_loaned_for_young_evac +
                             consumed_by_advance_promotion + old_bytes_reserved_for_alloc_supplement),
           "Budget exceeds available old-gen memory");
    log_debug(gc)("Old available: " SIZE_FORMAT ", Original promotion reserve: " SIZE_FORMAT ", Old evacuation reserve: "
                  SIZE_FORMAT ", Advance promotion reserve supplement: " SIZE_FORMAT
                  ", Old loaned for young evacuation: " SIZE_FORMAT ", Old reserved for alloc supplement: " SIZE_FORMAT,
                  old_available, promotion_reserve, old_evacuated_committed, consumed_by_advance_promotion,
                  old_regions_loaned_for_young_evac * region_size_bytes, old_bytes_reserved_for_alloc_supplement);

    promotion_reserve += consumed_by_advance_promotion;
    heap->set_promoted_reserve(promotion_reserve);
    heap->reset_promoted_expended();
    if (collection_set->get_old_bytes_reserved_for_evacuation() == 0) {
      // Setting old evacuation reserve to zero denotes that there is no old-gen evacuation in this pass.
      heap->set_old_evac_reserve(0);
    }

    size_t old_gen_usage_base = old_generation->used() - collection_set->get_old_garbage();
    heap->capture_old_usage(old_gen_usage_base);

    // Compute the evacuation supplement, which is extra memory borrowed from old-gen that can be allocated
    // by mutators while GC is working on evacuation and update-refs.  This memory can be temporarily borrowed
    // from old-gen allotment, then repaid at the end of update-refs from the recycled collection set.  After
    // we have computed the collection set based on the parameters established above, we can make additional
    // loans based on our knowledge of the collection set to determine how much allocation we can allow
    // during the evacuation and update-refs phases of execution.  The total available supplement is the smaller of:
    //
    //   1. old_gen->available() -
    //        (promotion_reserve + old_evacuation_commitment + old_bytes_loaned_for_young_evac)
    //   2. The replenishment budget (number of regions in collection set - the number of regions already
    //         under lien for the young_evacuation_reserve)
    //

    // Regardless of how many regions may be available to be loaned, we can loan no more regions than
    // the total number of young regions to be evacuated.  Call this the regions_for_runway.

    size_t young_regions_evacuated = collection_set->get_young_region_count();
    size_t regions_for_runway = 0;
    size_t already_loaned_regions = old_regions_loaned_for_young_evac + old_regions_reserved_for_alloc_supplement;
    if (already_loaned_regions == 0) {
      regions_for_runway = young_regions_evacuated;
    } else if (young_regions_evacuated > already_loaned_regions) {
      regions_for_runway = young_regions_evacuated - already_loaned_regions;
    } else {
      regions_for_runway = 0;
    }

    if (regions_available_to_loan > regions_for_runway) {
      regions_available_to_loan -= regions_for_runway;
    } else {
      regions_for_runway = regions_available_to_loan;
      regions_available_to_loan = 0;
    }

    size_t allocation_supplement = regions_for_runway * region_size_bytes + old_bytes_reserved_for_alloc_supplement;
    heap->set_alloc_supplement_reserve(allocation_supplement);

    // TODO: young_available, which feeds into alloc_budget_evac_and_update is lacking memory available within
    // existing young-gen regions that were not selected for the collection set.  Add this in and adjust the
    // log message (where it says "empty-region allocation budget").

    log_debug(gc)("Memory reserved for young evacuation: " SIZE_FORMAT "%s for evacuating " SIZE_FORMAT
                  "%s out of young available: " SIZE_FORMAT "%s",
                  byte_size_in_proper_unit(young_evacuated_reserve_used),
                  proper_unit_for_byte_size(young_evacuated_reserve_used),
                  byte_size_in_proper_unit(young_evacuated), proper_unit_for_byte_size(young_evacuated),
                  byte_size_in_proper_unit(young_available), proper_unit_for_byte_size(young_available));

    log_debug(gc)("Memory reserved for old evacuation: " SIZE_FORMAT "%s for evacuating " SIZE_FORMAT
                  "%s out of old available: " SIZE_FORMAT "%s",
                  byte_size_in_proper_unit(old_evacuated), proper_unit_for_byte_size(old_evacuated),
                  byte_size_in_proper_unit(old_evacuated), proper_unit_for_byte_size(old_evacuated),
                  byte_size_in_proper_unit(old_available), proper_unit_for_byte_size(old_available));

    assert(old_available > old_evacuation_reserve + promotion_reserve + old_bytes_loaned_for_young_evac + allocation_supplement,
           "old_available must be larger than accumulated reserves");

    size_t regular_promotion = promotion_reserve - consumed_by_advance_promotion;
    size_t excess =
      old_available - (old_evacuation_reserve + promotion_reserve + old_bytes_loaned_for_young_evac + allocation_supplement);
    log_info(gc, ergo)("Old available: " SIZE_FORMAT "%s is partitioned into old evacuation budget: " SIZE_FORMAT
                       "%s, aged region promotion budget: " SIZE_FORMAT
                       "%s, regular region promotion budget: " SIZE_FORMAT
                       "%s, loaned for young evacuation: " SIZE_FORMAT
                       "%s, loaned for young allocations: " SIZE_FORMAT
                       "%s, excess: " SIZE_FORMAT "%s",
                       byte_size_in_proper_unit(old_available), proper_unit_for_byte_size(old_available),
                       byte_size_in_proper_unit(old_evacuation_reserve), proper_unit_for_byte_size(old_evacuation_reserve),
                       byte_size_in_proper_unit(consumed_by_advance_promotion),
                       proper_unit_for_byte_size(consumed_by_advance_promotion),
                       byte_size_in_proper_unit(regular_promotion), proper_unit_for_byte_size(regular_promotion),
                       byte_size_in_proper_unit(old_bytes_loaned_for_young_evac),
                       proper_unit_for_byte_size(old_bytes_loaned_for_young_evac),
                       byte_size_in_proper_unit(allocation_supplement), proper_unit_for_byte_size(allocation_supplement),
                       byte_size_in_proper_unit(excess), proper_unit_for_byte_size(excess));
  }
  // else, not generational: no evacuation budget adjustments required
}

void ShenandoahGeneration::prepare_regions_and_collection_set(bool concurrent) {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  ShenandoahCollectionSet* collection_set = heap->collection_set();
  size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();

  assert(!heap->is_full_gc_in_progress(), "Only for concurrent and degenerated GC");
  assert(generation_mode() != OLD, "Only YOUNG and GLOBAL GC perform evacuations");
  {
    ShenandoahGCPhase phase(concurrent ? ShenandoahPhaseTimings::final_update_region_states :
                            ShenandoahPhaseTimings::degen_gc_final_update_region_states);
    ShenandoahFinalMarkUpdateRegionStateClosure cl(complete_marking_context());
    parallel_heap_region_iterate(&cl);

    if (generation_mode() == YOUNG) {
      // We always need to update the watermark for old regions. If there
      // are mixed collections pending, we also need to synchronize the
      // pinned status for old regions. Since we are already visiting every
      // old region here, go ahead and sync the pin status too.
      ShenandoahFinalMarkUpdateRegionStateClosure old_cl(nullptr);
      heap->old_generation()->parallel_heap_region_iterate(&old_cl);
    }

    heap->assert_pinned_region_status();
  }

  {
    size_t old_regions_loaned_for_young_evac, regions_available_to_loan, minimum_evacuation_reserve, consumed_by_advance_promotion;
    bool* preselected_regions = nullptr;
    if (heap->mode()->is_generational()) {
      preselected_regions = (bool*) alloca(heap->num_regions() * sizeof(bool));
      for (unsigned int i = 0; i < heap->num_regions(); i++) {
        preselected_regions[i] = false;
      }
    }

    ShenandoahGCPhase phase(concurrent ? ShenandoahPhaseTimings::choose_cset :
                            ShenandoahPhaseTimings::degen_gc_choose_cset);

    ShenandoahHeapLocker locker(heap->lock());
    collection_set->clear();
    // TODO: young_available can include available (between top() and end()) within each young region that is not
    // part of the collection set.  Making this memory available to the young_evacuation_reserve allows a larger
    // young collection set to be chosen when available memory is under extreme pressure.  Implementing this "improvement"
    // is tricky, because the incremental construction of the collection set actually changes the amount of memory
    // available to hold evacuated young-gen objects.  As currently implemented, the memory that is available within
    // non-empty regions that are not selected as part of the collection set can be allocated by the mutator while
    // GC is evacuating and updating references.

    // Budgeting parameters to compute_evacuation_budgets are passed by reference.
    compute_evacuation_budgets(heap, preselected_regions, collection_set, old_regions_loaned_for_young_evac,
                               regions_available_to_loan, minimum_evacuation_reserve, consumed_by_advance_promotion);
    _heuristics->choose_collection_set(collection_set, heap->old_heuristics());
    if (!collection_set->is_empty()) {
      adjust_evacuation_budgets(heap, collection_set, old_regions_loaned_for_young_evac, regions_available_to_loan,
                                minimum_evacuation_reserve, consumed_by_advance_promotion);
    }
    // otherwise, this is an abbreviated cycle and we make no use of evacuation budgets.
  }

  {
    ShenandoahGCPhase phase(concurrent ? ShenandoahPhaseTimings::final_rebuild_freeset :
                            ShenandoahPhaseTimings::degen_gc_final_rebuild_freeset);
    ShenandoahHeapLocker locker(heap->lock());
    heap->free_set()->rebuild();
  }
}

bool ShenandoahGeneration::is_bitmap_clear() {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  ShenandoahMarkingContext* context = heap->marking_context();
  size_t num_regions = heap->num_regions();
  for (size_t idx = 0; idx < num_regions; idx++) {
    ShenandoahHeapRegion* r = heap->get_region(idx);
    if (contains(r) && (r->affiliation() != FREE)) {
      if (heap->is_bitmap_slice_committed(r) && (context->top_at_mark_start(r) > r->bottom()) &&
          !context->is_bitmap_clear_range(r->bottom(), r->end())) {
        return false;
      }
    }
  }
  return true;
}

bool ShenandoahGeneration::is_mark_complete() {
  return _is_marking_complete.is_set();
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

ShenandoahGeneration::ShenandoahGeneration(GenerationMode generation_mode,
                                           uint max_workers,
                                           size_t max_capacity,
                                           size_t soft_max_capacity) :
  _generation_mode(generation_mode),
  _task_queues(new ShenandoahObjToScanQueueSet(max_workers)),
  _ref_processor(new ShenandoahReferenceProcessor(MAX2(max_workers, 1U))),
  _affiliated_region_count(0), _used(0), _bytes_allocated_since_gc_start(0),
  _max_capacity(max_capacity), _soft_max_capacity(soft_max_capacity),
  _adjusted_capacity(soft_max_capacity), _heuristics(nullptr) {
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

void ShenandoahGeneration::reserve_task_queues(uint workers) {
  _task_queues->reserve(workers);
}

ShenandoahObjToScanQueueSet* ShenandoahGeneration::old_gen_task_queues() const {
  return nullptr;
}

void ShenandoahGeneration::scan_remembered_set(bool is_concurrent) {
  assert(generation_mode() == YOUNG, "Should only scan remembered set for young generation.");

  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  uint nworkers = heap->workers()->active_workers();
  reserve_task_queues(nworkers);

  ShenandoahReferenceProcessor* rp = ref_processor();
  ShenandoahRegionChunkIterator work_list(nworkers);
  ShenandoahScanRememberedTask task(task_queues(), old_gen_task_queues(), rp, &work_list, is_concurrent);
  heap->workers()->run_task(&task);
}

void ShenandoahGeneration::increment_affiliated_region_count() {
  _affiliated_region_count++;
}

void ShenandoahGeneration::decrement_affiliated_region_count() {
  _affiliated_region_count--;
}

void ShenandoahGeneration::clear_used() {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "must be at a safepoint");
  // Do this atomically to assure visibility to other threads, even though these other threads may be idle "right now"..
  Atomic::store(&_used, (size_t)0);
}

void ShenandoahGeneration::increase_used(size_t bytes) {
  Atomic::add(&_used, bytes);
}

void ShenandoahGeneration::decrease_used(size_t bytes) {
  assert(_used >= bytes, "cannot reduce bytes used by generation below zero");
  Atomic::sub(&_used, bytes);
}

size_t ShenandoahGeneration::used_regions() const {
  return _affiliated_region_count;
}

size_t ShenandoahGeneration::free_unaffiliated_regions() const {
  size_t result = soft_max_capacity() / ShenandoahHeapRegion::region_size_bytes();
  if (_affiliated_region_count > result) {
    result = 0;                 // If old-gen is loaning regions to young-gen, affiliated regions may exceed capacity temporarily.
  } else {
    result -= _affiliated_region_count;
  }
  return result;
}

size_t ShenandoahGeneration::used_regions_size() const {
  return _affiliated_region_count * ShenandoahHeapRegion::region_size_bytes();
}

size_t ShenandoahGeneration::available() const {
  size_t in_use = used();
  size_t soft_capacity = soft_max_capacity();
  return in_use > soft_capacity ? 0 : soft_capacity - in_use;
}

size_t ShenandoahGeneration::adjust_available(intptr_t adjustment) {
  _adjusted_capacity = soft_max_capacity() + adjustment;
  return _adjusted_capacity;
}

size_t ShenandoahGeneration::unadjust_available() {
  _adjusted_capacity = soft_max_capacity();
  return _adjusted_capacity;
}

size_t ShenandoahGeneration::adjusted_available() const {
  size_t in_use = used();
  size_t capacity = _adjusted_capacity;
  return in_use > capacity ? 0 : capacity - in_use;
}

void ShenandoahGeneration::record_success_concurrent(bool abbreviated) {
  heuristics()->record_success_concurrent(abbreviated);
  ShenandoahHeap::heap()->shenandoah_policy()->record_success_concurrent();
}

void ShenandoahGeneration::record_success_degenerated() {
  heuristics()->record_success_degenerated();
  ShenandoahHeap::heap()->shenandoah_policy()->record_success_degenerated();
}
