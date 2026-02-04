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
    _heuristics->choose_collection_set(collection_set);
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
