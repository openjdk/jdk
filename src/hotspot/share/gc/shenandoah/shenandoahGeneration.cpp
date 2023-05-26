/*
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
  ShenandoahHeap* _heap;
  ShenandoahMarkingContext* const _ctx;
 public:
  ShenandoahResetUpdateRegionStateClosure() :
    _heap(ShenandoahHeap::heap()),
    _ctx(_heap->marking_context()) {}

  void heap_region_do(ShenandoahHeapRegion* r) override {
    if (_heap->is_bitmap_slice_committed(r)) {
      _ctx->clear_bitmap(r);
    }

    if (r->is_active()) {
      // Reset live data and set TAMS optimistically. We would recheck these under the pause
      // anyway to capture any updates that happened since now.
      _ctx->capture_top_at_mark_start(r);
      r->clear_live_data();
    }
  }

  bool is_thread_safe() override { return true; }
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
  return Atomic::load(&_bytes_allocated_since_gc_start);
}

void ShenandoahGeneration::reset_bytes_allocated_since_gc_start() {
  Atomic::store(&_bytes_allocated_since_gc_start, (size_t)0);
}

void ShenandoahGeneration::increase_allocated(size_t bytes) {
  Atomic::add(&_bytes_allocated_since_gc_start, bytes, memory_order_relaxed);
}

void ShenandoahGeneration::log_status(const char *msg) const {
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
  size_t v_humongous_waste = get_humongous_waste();
  LogGcInfo::print("%s: %s generation used: " SIZE_FORMAT "%s, used regions: " SIZE_FORMAT "%s, "
                   "humongous waste: " SIZE_FORMAT "%s, soft capacity: " SIZE_FORMAT "%s, max capacity: " SIZE_FORMAT "%s, "
                   "available: " SIZE_FORMAT "%s", msg, name(),
                   byte_size_in_proper_unit(v_used),              proper_unit_for_byte_size(v_used),
                   byte_size_in_proper_unit(v_used_regions),      proper_unit_for_byte_size(v_used_regions),
                   byte_size_in_proper_unit(v_humongous_waste),   proper_unit_for_byte_size(v_humongous_waste),
                   byte_size_in_proper_unit(v_soft_max_capacity), proper_unit_for_byte_size(v_soft_max_capacity),
                   byte_size_in_proper_unit(v_max_capacity),      proper_unit_for_byte_size(v_max_capacity),
                   byte_size_in_proper_unit(v_available),         proper_unit_for_byte_size(v_available));
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
  // Invalidate the marking context
  set_mark_incomplete();

  // Capture Top At Mark Start for this generation (typically young) and reset mark bitmap.
  ShenandoahResetUpdateRegionStateClosure cl;
  parallel_heap_region_iterate(&cl);
}

void ShenandoahGeneration::compute_evacuation_budgets(ShenandoahHeap* heap, bool* preselected_regions,
                                                      ShenandoahCollectionSet* collection_set,
                                                      size_t &consumed_by_advance_promotion) {
  size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();
  size_t regions_available_to_loan = 0;
  size_t minimum_evacuation_reserve = ShenandoahOldCompactionReserve * region_size_bytes;
  size_t old_regions_loaned_for_young_evac = 0;
  consumed_by_advance_promotion = 0;

  ShenandoahGeneration* old_generation = heap->old_generation();
  ShenandoahYoungGeneration* young_generation = heap->young_generation();
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
  size_t young_evac_reserve_max = 0;

  // First priority is to reclaim the easy garbage out of young-gen.

  // maximum_young_evacuation_reserve is upper bound on memory to be evacuated out of young
  size_t maximum_young_evacuation_reserve = (young_generation->max_capacity() * ShenandoahEvacReserve) / 100;
  size_t young_evacuation_reserve = maximum_young_evacuation_reserve;
  size_t excess_young;
  if (young_generation->available() > young_evacuation_reserve) {
    excess_young = young_generation->available() - young_evacuation_reserve;
  } else {
    young_evacuation_reserve = young_generation->available();
    excess_young = 0;
  }
  size_t unaffiliated_young = young_generation->free_unaffiliated_regions() * region_size_bytes;
  if (excess_young > unaffiliated_young) {
    excess_young = unaffiliated_young;
  } else {
    // round down to multiple of region size
    excess_young /= region_size_bytes;
    excess_young *= region_size_bytes;
  }
  // excess_young is available to be transferred to OLD.  Assume that OLD will not request any more than had
  // already been set aside for its promotion and evacuation needs at the end of previous GC.  No need to
  // hold back memory for allocation runway.

  ShenandoahOldHeuristics* old_heuristics = heap->old_heuristics();

  // maximum_old_evacuation_reserve is an upper bound on memory evacuated from old and evacuated to old (promoted).
  size_t maximum_old_evacuation_reserve =
    maximum_young_evacuation_reserve * ShenandoahOldEvacRatioPercent / (100 - ShenandoahOldEvacRatioPercent);
  // Here's the algebra:
  //  TotalEvacuation = OldEvacuation + YoungEvacuation
  //  OldEvacuation = TotalEvacuation * (ShenandoahOldEvacRatioPercent/100)
  //  OldEvacuation = YoungEvacuation * (ShenandoahOldEvacRatioPercent/100)/(1 - ShenandoahOldEvacRatioPercent/100)
  //  OldEvacuation = YoungEvacuation * ShenandoahOldEvacRatioPercent/(100 - ShenandoahOldEvacRatioPercent)

  if (maximum_old_evacuation_reserve > old_generation->available()) {
    maximum_old_evacuation_reserve = old_generation->available();
  }

  // Second priority is to reclaim garbage out of old-gen if there are old-gen collection candidates.  Third priority
  // is to promote as much as we have room to promote.  However, if old-gen memory is in short supply, this means young
  // GC is operating under "duress" and was unable to transfer the memory that we would normally expect.  In this case,
  // old-gen will refrain from compacting itself in order to allow a quicker young-gen cycle (by avoiding the update-refs
  // through ALL of old-gen).  If there is some memory available in old-gen, we will use this for promotions as promotions
  // do not add to the update-refs burden of GC.

  size_t old_promo_reserve;
  if (old_heuristics->unprocessed_old_collection_candidates() > 0) {
    // We reserved all old-gen memory at end of previous GC to hold anticipated evacuations to old-gen.  If this is
    // mixed evacuation, reserve all of this memory for compaction of old-gen and do not promote.  Prioritize compaction
    // over promotion in order to defragment OLD so that it will be better prepared to efficiently receive promoted memory.
    old_evacuation_reserve = maximum_old_evacuation_reserve;
    old_promo_reserve = 0;
  } else {
    // Make all old-evacuation memory for promotion, but if we can't use it all for promotion, we'll allow some evacuation.
    old_evacuation_reserve = 0;
    old_promo_reserve = maximum_old_evacuation_reserve;
  }

  // We see too many old-evacuation failures if we force ourselves to evacuate into regions that are not initially empty.
  // So we limit the old-evacuation reserve to unfragmented memory.  Even so, old-evacuation is free to fill in nooks and
  // crannies within existing partially used regions and it generally tries to do so.
  size_t old_free_regions = old_generation->free_unaffiliated_regions();
  size_t old_free_unfragmented = old_free_regions * region_size_bytes;
  if (old_evacuation_reserve > old_free_unfragmented) {
    size_t delta = old_evacuation_reserve - old_free_unfragmented;
    old_evacuation_reserve -= delta;

    // Let promo consume fragments of old-gen memory.
    old_promo_reserve += delta;
  }
  collection_set->establish_preselected(preselected_regions);
  consumed_by_advance_promotion = _heuristics->select_aged_regions(old_promo_reserve, num_regions, preselected_regions);
  assert(consumed_by_advance_promotion <= maximum_old_evacuation_reserve, "Cannot promote more than available old-gen memory");
  if (consumed_by_advance_promotion < old_promo_reserve) {
    // If we're in a global collection, this memory can be used for old evacuations
    old_evacuation_reserve += old_promo_reserve - consumed_by_advance_promotion;
  }
  heap->set_young_evac_reserve(young_evacuation_reserve);
  heap->set_old_evac_reserve(old_evacuation_reserve);
  heap->set_promoted_reserve(consumed_by_advance_promotion);

  // There is no need to expand OLD because all memory used here was set aside at end of previous GC
}

// Having chosen the collection set, adjust the budgets for generational mode based on its composition.  Note
// that young_generation->available() now knows about recently discovered immediate garbage.

void ShenandoahGeneration::adjust_evacuation_budgets(ShenandoahHeap* heap, ShenandoahCollectionSet* collection_set,
                                                     size_t consumed_by_advance_promotion) {
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

  size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();
  ShenandoahOldGeneration* old_generation = heap->old_generation();
  ShenandoahYoungGeneration* young_generation = heap->young_generation();

  // Preselected regions have been inserted into the collection set, so we no longer need the preselected array.
  collection_set->abandon_preselected();

  size_t old_evacuated = collection_set->get_old_bytes_reserved_for_evacuation();
  size_t old_evacuated_committed = (size_t) (ShenandoahOldEvacWaste * old_evacuated);
  size_t old_evacuation_reserve = heap->get_old_evac_reserve();

  if (old_evacuated_committed > old_evacuation_reserve) {
    // This should only happen due to round-off errors when enforcing ShenandoahOldEvacWaste
    assert(old_evacuated_committed <= (33 * old_evacuation_reserve) / 32,
           "Round-off errors should be less than 3.125%%, committed: " SIZE_FORMAT ", reserved: " SIZE_FORMAT,
           old_evacuated_committed, old_evacuation_reserve);
    old_evacuated_committed = old_evacuation_reserve;
    // Leave old_evac_reserve as previously configured
  } else if (old_evacuated_committed < old_evacuation_reserve) {
    // This happens if the old-gen collection consumes less than full budget.
    old_evacuation_reserve = old_evacuated_committed;
    heap->set_old_evac_reserve(old_evacuation_reserve);
  }

  size_t young_advance_promoted = collection_set->get_young_bytes_to_be_promoted();
  size_t young_advance_promoted_reserve_used = (size_t) (ShenandoahPromoEvacWaste * young_advance_promoted);

  size_t young_evacuated = collection_set->get_young_bytes_reserved_for_evacuation();
  size_t young_evacuated_reserve_used = (size_t) (ShenandoahEvacWaste * young_evacuated);

  assert(young_evacuated_reserve_used <= young_generation->available(), "Cannot evacuate more than is available in young");
  heap->set_young_evac_reserve(young_evacuated_reserve_used);

  size_t old_available = old_generation->available();
  // Now that we've established the collection set, we know how much memory is really required by old-gen for evacuation
  // and promotion reserves.  Try shrinking OLD now in case that gives us a bit more runway for mutator allocations during
  // evac and update phases.
  size_t old_consumed = old_evacuated_committed + young_advance_promoted_reserve_used;
  assert(old_available >= old_consumed, "Cannot consume more than is available");
  size_t excess_old = old_available - old_consumed;
  size_t unaffiliated_old_regions = old_generation->free_unaffiliated_regions();
  size_t unaffiliated_old = unaffiliated_old_regions * region_size_bytes;
  assert(old_available >= unaffiliated_old, "Unaffiliated old is a subset of old available");

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
  // runway during evacuation and update-refs.
  size_t regions_to_xfer = 0;
  if (excess_old > unaffiliated_old) {
    // we can give back unaffiliated_old (all of unaffiliated is excess)
    if (unaffiliated_old_regions > 0) {
      regions_to_xfer = unaffiliated_old_regions;
    }
  } else if (unaffiliated_old_regions > 0) {
    // excess_old < unaffiliated old: we can give back MIN(excess_old/region_size_bytes, unaffiliated_old_regions)
    size_t excess_regions = excess_old / region_size_bytes;
    size_t regions_to_xfer = MIN2(excess_regions, unaffiliated_old_regions);
  }

  if (regions_to_xfer > 0) {
    bool result = heap->generation_sizer()->transfer_to_young(regions_to_xfer);
    assert(excess_old > regions_to_xfer * region_size_bytes, "Cannot xfer more than excess old");
    excess_old -= regions_to_xfer * region_size_bytes;
    log_info(gc, ergo)("%s transferred " SIZE_FORMAT " excess regions to young before start of evacuation",
                       result? "Successfully": "Unsuccessfully", regions_to_xfer);
  }

  // Add in the excess_old memory to hold unanticipated promotions, if any.  If there are more unanticipated
  // promotions than fit in reserved memory, they will be deferred until a future GC pass.
  size_t total_promotion_reserve = young_advance_promoted_reserve_used + excess_old;
  heap->set_promoted_reserve(total_promotion_reserve);
  heap->reset_promoted_expended();
}

void ShenandoahGeneration::prepare_regions_and_collection_set(bool concurrent) {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  ShenandoahCollectionSet* collection_set = heap->collection_set();

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

  {
    ShenandoahGCPhase phase(concurrent ? ShenandoahPhaseTimings::choose_cset :
                            ShenandoahPhaseTimings::degen_gc_choose_cset);

    collection_set->clear();
    ShenandoahHeapLocker locker(heap->lock());
    if (heap->mode()->is_generational()) {
      size_t consumed_by_advance_promotion;
      bool* preselected_regions = (bool*) alloca(heap->num_regions() * sizeof(bool));
      for (unsigned int i = 0; i < heap->num_regions(); i++) {
        preselected_regions[i] = false;
      }

      // TODO: young_available can include available (between top() and end()) within each young region that is not
      // part of the collection set.  Making this memory available to the young_evacuation_reserve allows a larger
      // young collection set to be chosen when available memory is under extreme pressure.  Implementing this "improvement"
      // is tricky, because the incremental construction of the collection set actually changes the amount of memory
      // available to hold evacuated young-gen objects.  As currently implemented, the memory that is available within
      // non-empty regions that are not selected as part of the collection set can be allocated by the mutator while
      // GC is evacuating and updating references.

      // Budgeting parameters to compute_evacuation_budgets are passed by reference.
      compute_evacuation_budgets(heap, preselected_regions, collection_set, consumed_by_advance_promotion);
      _heuristics->choose_collection_set(collection_set, heap->old_heuristics());
      if (!collection_set->is_empty()) {
        // only make use of evacuation budgets when we are evacuating
        adjust_evacuation_budgets(heap, collection_set, consumed_by_advance_promotion);
      }
    } else {
      _heuristics->choose_collection_set(collection_set, heap->old_heuristics());
    }
  }

  // Freeset construction uses reserve quantities if they are valid
  heap->set_evacuation_reserve_quantities(true);
  {
    ShenandoahGCPhase phase(concurrent ? ShenandoahPhaseTimings::final_rebuild_freeset :
                            ShenandoahPhaseTimings::degen_gc_final_rebuild_freeset);
    ShenandoahHeapLocker locker(heap->lock());
    size_t young_cset_regions, old_cset_regions;

    // We are preparing for evacuation.  At this time, we ignore cset region tallies.
    heap->free_set()->prepare_to_rebuild(young_cset_regions, old_cset_regions);
    heap->free_set()->rebuild(young_cset_regions, old_cset_regions);
  }
  heap->set_evacuation_reserve_quantities(false);
}

bool ShenandoahGeneration::is_bitmap_clear() {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  ShenandoahMarkingContext* context = heap->marking_context();
  size_t num_regions = heap->num_regions();
  for (size_t idx = 0; idx < num_regions; idx++) {
    ShenandoahHeapRegion* r = heap->get_region(idx);
    if (contains(r) && r->is_affiliated()) {
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

ShenandoahGeneration::ShenandoahGeneration(ShenandoahGenerationType type,
                                           uint max_workers,
                                           size_t max_capacity,
                                           size_t soft_max_capacity) :
  _type(type),
  _task_queues(new ShenandoahObjToScanQueueSet(max_workers)),
  _ref_processor(new ShenandoahReferenceProcessor(MAX2(max_workers, 1U))),
  _collection_thread_time_s(0.0),
  _affiliated_region_count(0), _humongous_waste(0), _used(0), _bytes_allocated_since_gc_start(0),
  _max_capacity(max_capacity), _soft_max_capacity(soft_max_capacity),
  _heuristics(nullptr) {
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
  assert(is_young(), "Should only scan remembered set for young generation.");

  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  uint nworkers = heap->workers()->active_workers();
  reserve_task_queues(nworkers);

  ShenandoahReferenceProcessor* rp = ref_processor();
  ShenandoahRegionChunkIterator work_list(nworkers);
  ShenandoahScanRememberedTask task(task_queues(), old_gen_task_queues(), rp, &work_list, is_concurrent);
  heap->assert_gc_workers(nworkers);
  heap->workers()->run_task(&task);
  if (ShenandoahEnableCardStats) {
    assert(heap->card_scan() != nullptr, "Not generational");
    heap->card_scan()->log_card_stats(nworkers, CARD_STAT_SCAN_RS);
  }
}

size_t ShenandoahGeneration::increment_affiliated_region_count() {
  shenandoah_assert_heaplocked_or_fullgc_safepoint();
  // During full gc, multiple GC worker threads may change region affiliations without a lock.  No lock is enforced
  // on read and write of _affiliated_region_count.  At the end of full gc, a single thread overwrites the count with
  // a coherent value.
  _affiliated_region_count++;
  return _affiliated_region_count;
}

size_t ShenandoahGeneration::decrement_affiliated_region_count() {
  shenandoah_assert_heaplocked_or_fullgc_safepoint();
  // During full gc, multiple GC worker threads may change region affiliations without a lock.  No lock is enforced
  // on read and write of _affiliated_region_count.  At the end of full gc, a single thread overwrites the count with
  // a coherent value.
  _affiliated_region_count--;
  // TODO: REMOVE IS_GLOBAL() QUALIFIER AFTER WE FIX GLOBAL AFFILIATED REGION ACCOUNTING
  assert(is_global() || ShenandoahHeap::heap()->is_full_gc_in_progress() ||
         (_used + _humongous_waste <= _affiliated_region_count * ShenandoahHeapRegion::region_size_bytes()),
         "used + humongous cannot exceed regions");
  return _affiliated_region_count;
}

size_t ShenandoahGeneration::increase_affiliated_region_count(size_t delta) {
  shenandoah_assert_heaplocked_or_fullgc_safepoint();
  _affiliated_region_count += delta;
  return _affiliated_region_count;
}

size_t ShenandoahGeneration::decrease_affiliated_region_count(size_t delta) {
  shenandoah_assert_heaplocked_or_fullgc_safepoint();
  assert(_affiliated_region_count > delta, "Affiliated region count cannot be negative");

  _affiliated_region_count -= delta;
  // TODO: REMOVE IS_GLOBAL() QUALIFIER AFTER WE FIX GLOBAL AFFILIATED REGION ACCOUNTING
  assert(is_global() || ShenandoahHeap::heap()->is_full_gc_in_progress() ||
         (_used + _humongous_waste <= _affiliated_region_count * ShenandoahHeapRegion::region_size_bytes()),
         "used + humongous cannot exceed regions");
  return _affiliated_region_count;
}

void ShenandoahGeneration::establish_usage(size_t num_regions, size_t num_bytes, size_t humongous_waste) {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "must be at a safepoint");
  _affiliated_region_count = num_regions;
  _used = num_bytes;
  _humongous_waste = humongous_waste;
}

void ShenandoahGeneration::increase_used(size_t bytes) {
  Atomic::add(&_used, bytes);
  // This detects arithmetic wraparound on _used.  Non-generational mode does not keep track of _affiliated_region_count
  // TODO: REMOVE IS_GLOBAL() QUALIFIER AFTER WE FIX GLOBAL AFFILIATED REGION ACCOUNTING
  assert(is_global() || ShenandoahHeap::heap()->is_full_gc_in_progress() ||
         (_used + _humongous_waste <= _affiliated_region_count * ShenandoahHeapRegion::region_size_bytes()),
         "used cannot exceed regions");
}

void ShenandoahGeneration::increase_humongous_waste(size_t bytes) {
  if (bytes > 0) {
    Atomic::add(&_humongous_waste, bytes);
  }
}

void ShenandoahGeneration::decrease_humongous_waste(size_t bytes) {
  if (bytes > 0) {
    assert(ShenandoahHeap::heap()->is_full_gc_in_progress() || (_humongous_waste >= bytes),
           "Waste (" SIZE_FORMAT ") cannot be negative (after subtracting " SIZE_FORMAT ")", _humongous_waste, bytes);
    Atomic::sub(&_humongous_waste, bytes);
  }
}

void ShenandoahGeneration::decrease_used(size_t bytes) {
  // TODO: REMOVE IS_GLOBAL() QUALIFIER AFTER WE FIX GLOBAL AFFILIATED REGION ACCOUNTING
  assert(is_global() || ShenandoahHeap::heap()->is_full_gc_in_progress() ||
         (_used >= bytes), "cannot reduce bytes used by generation below zero");
  Atomic::sub(&_used, bytes);

  // Non-generational mode does not maintain affiliated region counts
  // TODO: REMOVE IS_GLOBAL() QUALIFIER AFTER WE FIX GLOBAL AFFILIATED REGION ACCOUNTING
  assert(is_global() || ShenandoahHeap::heap()->is_full_gc_in_progress() ||
         (_affiliated_region_count * ShenandoahHeapRegion::region_size_bytes() >= _used),
         "Affiliated regions must hold more than what is currently used");
}

size_t ShenandoahGeneration::used_regions() const {
  return _affiliated_region_count;
}

size_t ShenandoahGeneration::free_unaffiliated_regions() const {
  size_t result = max_capacity() / ShenandoahHeapRegion::region_size_bytes();
  if (_affiliated_region_count > result) {
    result = 0;
  } else {
    result -= _affiliated_region_count;
  }
  return result;
}

size_t ShenandoahGeneration::used_regions_size() const {
  return _affiliated_region_count * ShenandoahHeapRegion::region_size_bytes();
}

size_t ShenandoahGeneration::available() const {
  size_t in_use = used() + get_humongous_waste();
  size_t capacity = max_capacity();
  return in_use > capacity ? 0 : capacity - in_use;
}

size_t ShenandoahGeneration::soft_available() const {
  size_t in_use = used() + get_humongous_waste();
  size_t soft_capacity = soft_max_capacity();
  return in_use > soft_capacity ? 0 : soft_capacity - in_use;
}

void ShenandoahGeneration::increase_capacity(size_t increment) {
  shenandoah_assert_heaplocked_or_safepoint();

  // We do not enforce that new capacity >= heap->max_size_for(this).  The maximum generation size is treated as a rule of thumb
  // which may be violated during certain transitions, such as when we are forcing transfers for the purpose of promoting regions
  // in place.
  // TODO: REMOVE IS_GLOBAL() QUALIFIER AFTER WE FIX GLOBAL AFFILIATED REGION ACCOUNTING
  assert(is_global() || ShenandoahHeap::heap()->is_full_gc_in_progress() ||
         (_max_capacity + increment <= ShenandoahHeap::heap()->max_capacity()), "Generation cannot be larger than heap size");
  assert(increment % ShenandoahHeapRegion::region_size_bytes() == 0, "Generation capacity must be multiple of region size");
  _max_capacity += increment;

  // This detects arithmetic wraparound on _used
  // TODO: REMOVE IS_GLOBAL() QUALIFIER AFTER WE FIX GLOBAL AFFILIATED REGION ACCOUNTING
  assert(is_global() || ShenandoahHeap::heap()->is_full_gc_in_progress() ||
         (_affiliated_region_count * ShenandoahHeapRegion::region_size_bytes() >= _used),
         "Affiliated regions must hold more than what is currently used");
}

void ShenandoahGeneration::decrease_capacity(size_t decrement) {
  shenandoah_assert_heaplocked_or_safepoint();

  // We do not enforce that new capacity >= heap->min_size_for(this).  The minimum generation size is treated as a rule of thumb
  // which may be violated during certain transitions, such as when we are forcing transfers for the purpose of promoting regions
  // in place.
  assert(decrement % ShenandoahHeapRegion::region_size_bytes() == 0, "Generation capacity must be multiple of region size");
  assert(_max_capacity >= decrement, "Generation capacity cannot be negative");

  _max_capacity -= decrement;

  // This detects arithmetic wraparound on _used
  // TODO: REMOVE IS_GLOBAL() QUALIFIER AFTER WE FIX GLOBAL AFFILIATED REGION ACCOUNTING
  assert(is_global() || ShenandoahHeap::heap()->is_full_gc_in_progress() ||
         (_affiliated_region_count * ShenandoahHeapRegion::region_size_bytes() >= _used),
         "Affiliated regions must hold more than what is currently used");
  // TODO: REMOVE IS_GLOBAL() QUALIFIER AFTER WE FIX GLOBAL AFFILIATED REGION ACCOUNTING
  assert(is_global() || ShenandoahHeap::heap()->is_full_gc_in_progress() ||
         (_used <= _max_capacity), "Cannot use more than capacity");
  // TODO: REMOVE IS_GLOBAL() QUALIFIER AFTER WE FIX GLOBAL AFFILIATED REGION ACCOUNTING
  assert(is_global() || ShenandoahHeap::heap()->is_full_gc_in_progress() ||
         (_affiliated_region_count * ShenandoahHeapRegion::region_size_bytes() <= _max_capacity),
         "Cannot use more than capacity");
}

void ShenandoahGeneration::record_success_concurrent(bool abbreviated) {
  heuristics()->record_success_concurrent(abbreviated);
  ShenandoahHeap::heap()->shenandoah_policy()->record_success_concurrent();
}

void ShenandoahGeneration::record_success_degenerated() {
  heuristics()->record_success_degenerated();
  ShenandoahHeap::heap()->shenandoah_policy()->record_success_degenerated();
}

void ShenandoahGeneration::add_collection_time(double time_seconds) {
  shenandoah_assert_control_or_vm_thread();
  _collection_thread_time_s += time_seconds;
}

double ShenandoahGeneration::reset_collection_time() {
  double t = _collection_thread_time_s;
  _collection_thread_time_s = 0.0;
  return t;
}

