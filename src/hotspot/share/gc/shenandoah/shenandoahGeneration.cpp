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

#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahMarkClosures.hpp"
#include "gc/shenandoah/shenandoahMonitoringSupport.hpp"
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

void ShenandoahGeneration::prepare_gc(bool do_old_gc_bootstrap) {
  // Reset mark bitmap for this generation (typically young)
  reset_mark_bitmap();
  if (do_old_gc_bootstrap) {
    // Reset mark bitmap for old regions also.  Note that do_old_gc_bootstrap is only true if this generation is YOUNG.
    ShenandoahHeap::heap()->old_generation()->reset_mark_bitmap();
  }

  // Capture Top At Mark Start for this generation (typically young)
  ShenandoahResetUpdateRegionStateClosure cl;
  parallel_heap_region_iterate(&cl);
  if (do_old_gc_bootstrap) {
    // Capture top at mark start for both old-gen regions also.  Note that do_old_gc_bootstrap is only true if generation is YOUNG.
    ShenandoahHeap::heap()->old_generation()->parallel_heap_region_iterate(&cl);
  }
}

// Returns true iff the chosen collection set includes a mix of young-gen and old-gen regions.
bool ShenandoahGeneration::prepare_regions_and_collection_set(bool concurrent) {
  bool result;
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  assert(!heap->is_full_gc_in_progress(), "Only for concurrent and degenerated GC");
  assert(generation_mode() != OLD, "Only YOUNG and GLOBAL GC perform evacuations");
  {
    ShenandoahGCPhase phase(concurrent ? ShenandoahPhaseTimings::final_update_region_states :
                                         ShenandoahPhaseTimings::degen_gc_final_update_region_states);
    ShenandoahFinalMarkUpdateRegionStateClosure cl(complete_marking_context());

    parallel_heap_region_iterate(&cl);
    heap->assert_pinned_region_status();

    if (generation_mode() == YOUNG) {
      // Also capture update_watermark for old-gen regions.
      ShenandoahCaptureUpdateWaterMarkForOld old_cl(complete_marking_context());
      heap->old_generation()->parallel_heap_region_iterate(&old_cl);
    }
  }

  {
    ShenandoahGCPhase phase(concurrent ? ShenandoahPhaseTimings::choose_cset :
                            ShenandoahPhaseTimings::degen_gc_choose_cset);
    ShenandoahHeapLocker locker(heap->lock());
    heap->collection_set()->clear();


    if (heap->mode()->is_generational()) {

      // During initialization and phase changes, it is more likely that fewer objects die young and old-gen
      // memory is not yet full (or is in the process of being replaced).  During these tiems especially, it
      // is beneficial to loan memory from old-gen to young-gen during the evacuation and update-refs phases
      // of execution.

      //  PromotionReserve for old generation: how much memory are we reserving to hold the results of
      //     promoting young-gen objects that have reached tenure age?  This value is not "critical".  If we
      //     underestimate, certain promotions will simply be deferred.  The basis of this estimate is
      //     historical precedent.  Conservatively, budget this value to be twice the amount of memory
      //     promoted in previous GC pass.  Whenever the amount promoted during previous GC is zero,
      //     including initial passes before any objects have reached tenure age, use live memory within
      //     young-gen memory divided by (ShenandoahTenureAge multiplied by InitialTenuringThreshold) as the
      //     the very conservative value of this parameter.  Note that during initialization, there is
      //     typically plentiful old-gen memory so it's ok to be conservative with the initial estimates
      //     of this value.  But PromotionReserve can be no larger than available memory.  In summary, we
      //     compute PromotionReserve as the smaller of:
      //      1. old_gen->available
      //      2. young_gen->capacity() * ShenandoahEvacReserve
      //      3. (bytes promoted by previous promotion) * 2 if (bytes promoted by previous promotion) is not zero
      //      4. if (bytes promoted by previous promotion) is zero, divide young_gen->used()
      //         by (ShenandoahTenureAge * InitialTenuringThreshold)
      //
      //     We don't yet know how much live memory.  Inside choose_collection_set(), after it computes live memory,
      //     the PromotionReserve may be further reduced.
      //
      //      5. live bytes in young-gen divided by (ShenandoahTenureAge * InitialTenuringThreshold
      //         if the number of bytes promoted by previous promotion is zero
      //
      ShenandoahGeneration* old_generation = heap->old_generation();
      ShenandoahYoungGeneration* young_generation = heap->young_generation();
      size_t promotion_reserve = old_generation->available();

      size_t max_young_evacuation = (young_generation->soft_max_capacity() * ShenandoahOldEvacReserve) / 100;
      if (max_young_evacuation < promotion_reserve) {
        promotion_reserve = max_young_evacuation;
      }

      size_t previously_promoted = heap->get_previous_promotion();
      if (previously_promoted == 0) {
        // Very conservatively, assume linear population decay (rather than more typical exponential) and assume all of
        // used is live.
        size_t proposed_reserve = young_generation->used() / (ShenandoahAgingCyclePeriod * InitialTenuringThreshold);
        if (promotion_reserve > proposed_reserve) {
          promotion_reserve = proposed_reserve;
        }
      } else if (previously_promoted * 2 < promotion_reserve) {
        promotion_reserve = previously_promoted * 2;
      }

      heap->set_promotion_reserve(promotion_reserve);
      heap->capture_old_usage(old_generation->used());

      //  OldEvacuationReserve for old generation: how much memory are we reserving to hold the results of
      //     evacuating old-gen heap regions?  In order to sustain a consistent pace of young-gen collections,
      //     the goal is to maintain a consistent value for this parameter (when the candidate set is not
      //     empty).  This value is the minimum of:
      //       1. old_gen->available() - PromotionReserve
      //       2. (young_gen->capacity() scaled by ShenandoahEvacReserve) scaled by ShenandoahOldEvacRatioPercent

      // Don't reserve for old_evac any more than the memory that is available in old_gen.
      size_t old_evacuation_reserve = old_generation->available() - promotion_reserve;

      // Make sure old evacuation is no more than ShenandoahOldEvacRatioPercent of the total evacuation budget.
      size_t max_total_evac = (young_generation->soft_max_capacity() * ShenandoahEvacReserve) / 100;
      size_t max_old_evac_portion = (max_total_evac * ShenandoahOldEvacRatioPercent) / 100;

      if (old_evacuation_reserve > max_old_evac_portion) {
        old_evacuation_reserve = max_old_evac_portion;
      }

      heap->set_old_evac_reserve(old_evacuation_reserve);
      heap->reset_old_evac_expended();

      // Compute YoungEvacuationReserve after we prime the collection set with old-gen candidates.  This depends
      // on how much memory old-gen wants to evacuate.  This is done within _heuristics->choose_collection_set().

      // There's no need to pass this information to ShenandoahFreeSet::rebuild().  The GC allocator automatically borrows
      // memory from mutator regions when necessary.
    }

    // The heuristics may consult and/or change the values of PromotionReserved, OldEvacuationReserved, and
    // YoungEvacuationReserved, all of which are represented in the shared ShenandoahHeap data structure.
    result = _heuristics->choose_collection_set(heap->collection_set(), heap->old_heuristics());

    //  EvacuationAllocationSupplement: This represents memory that can be allocated in excess of young_gen->available()
    //     during evacuation and update-refs.  This memory can be temporarily borrowed from old-gen allotment, then
    //     repaid at the end of update-refs from the recycled collection set.  After we have computed the collection set
    //     based on the parameters established above, we can make additional calculates based on our knowledge of the
    //     collection set to determine how much allocation we can allow during the evacuation and update-refs phases
    //     of execution.  With full awareness of collection set, we can shrink the values of PromotionReserve,
    //     OldEvacuationReserve, and YoungEvacuationReserve.  Then, we can compute EvacuationAllocationReserve as the
    //     minimum of:
    //       1. old_gen->available - (PromotionReserve + OldEvacuationReserve)
    //       2. The replenishment budget (number of regions in collection set - the number of regions already
    //          under lien for the YoungEvacuationReserve)
    //

    // The possibly revised values are also consulted by the ShenandoahPacer when it establishes pacing parameters
    // for evacuation and update-refs.

  }

  {
    ShenandoahGCPhase phase(concurrent ? ShenandoahPhaseTimings::final_rebuild_freeset :
                            ShenandoahPhaseTimings::degen_gc_final_rebuild_freeset);
    ShenandoahHeapLocker locker(heap->lock());
    heap->free_set()->rebuild();
  }
  return result;
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

void ShenandoahGeneration::scan_remembered_set() {
  assert(generation_mode() == YOUNG, "Should only scan remembered set for young generation.");

  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  uint nworkers = heap->workers()->active_workers();
  reserve_task_queues(nworkers);

  ShenandoahReferenceProcessor* rp = ref_processor();
  ShenandoahRegionIterator regions;
  ShenandoahScanRememberedTask task(task_queues(), old_gen_task_queues(), rp, &regions);
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
