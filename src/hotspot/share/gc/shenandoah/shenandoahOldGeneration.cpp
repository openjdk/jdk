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
#include "gc/shenandoah/shenandoahAsserts.hpp"
#include "gc/shenandoah/shenandoahCardTable.hpp"
#include "gc/shenandoah/shenandoahClosures.inline.hpp"
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahGenerationalHeap.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahHeapRegionClosures.hpp"
#include "gc/shenandoah/shenandoahMonitoringSupport.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahReferenceProcessor.hpp"
#include "gc/shenandoah/shenandoahScanRemembered.inline.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "gc/shenandoah/shenandoahWorkerPolicy.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"
#include "runtime/threads.hpp"
#include "utilities/events.hpp"

class ShenandoahFlushAllSATB : public ThreadClosure {
private:
  SATBMarkQueueSet& _satb_qset;

public:
  explicit ShenandoahFlushAllSATB(SATBMarkQueueSet& satb_qset) :
    _satb_qset(satb_qset) {}

  void do_thread(Thread* thread) override {
    // Transfer any partial buffer to the qset for completed buffer processing.
    _satb_qset.flush_queue(ShenandoahThreadLocalData::satb_mark_queue(thread));
  }
};

class ShenandoahProcessOldSATB : public SATBBufferClosure {
private:
  ShenandoahObjToScanQueue*       _queue;
  ShenandoahHeap*                 _heap;
  ShenandoahMarkingContext* const _mark_context;
  size_t                          _trashed_oops;

public:
  explicit ShenandoahProcessOldSATB(ShenandoahObjToScanQueue* q) :
    _queue(q),
    _heap(ShenandoahHeap::heap()),
    _mark_context(_heap->marking_context()),
    _trashed_oops(0) {}

  void do_buffer(void** buffer, size_t size) override {
    assert(size == 0 || !_heap->has_forwarded_objects() || _heap->is_concurrent_old_mark_in_progress(), "Forwarded objects are not expected here");
    for (size_t i = 0; i < size; ++i) {
      oop *p = (oop *) &buffer[i];
      ShenandoahHeapRegion* region = _heap->heap_region_containing(*p);
      if (region->is_old() && region->is_active()) {
          ShenandoahMark::mark_through_ref<oop, OLD>(p, _queue, nullptr, _mark_context, false);
      } else {
        _trashed_oops++;
      }
    }
  }

  size_t trashed_oops() const {
    return _trashed_oops;
  }
};

class ShenandoahPurgeSATBTask : public WorkerTask {
private:
  ShenandoahObjToScanQueueSet* _mark_queues;
  // Keep track of the number of oops that are not transferred to mark queues.
  // This is volatile because workers update it, but the vm thread reads it.
  volatile size_t             _trashed_oops;

public:
  explicit ShenandoahPurgeSATBTask(ShenandoahObjToScanQueueSet* queues) :
    WorkerTask("Purge SATB"),
    _mark_queues(queues),
    _trashed_oops(0) {
    Threads::change_thread_claim_token();
  }

  ~ShenandoahPurgeSATBTask() {
    if (_trashed_oops > 0) {
      log_debug(gc)("Purged %zu oops from old generation SATB buffers", _trashed_oops);
    }
  }

  void work(uint worker_id) override {
    ShenandoahParallelWorkerSession worker_session(worker_id);
    ShenandoahSATBMarkQueueSet &satb_queues = ShenandoahBarrierSet::satb_mark_queue_set();
    ShenandoahFlushAllSATB flusher(satb_queues);
    Threads::possibly_parallel_threads_do(true /* is_par */, &flusher);

    ShenandoahObjToScanQueue* mark_queue = _mark_queues->queue(worker_id);
    ShenandoahProcessOldSATB processor(mark_queue);
    while (satb_queues.apply_closure_to_completed_buffer(&processor)) {}

    Atomic::add(&_trashed_oops, processor.trashed_oops());
  }
};

class ShenandoahTransferOldSATBTask : public WorkerTask {
  ShenandoahSATBMarkQueueSet&  _satb_queues;
  ShenandoahObjToScanQueueSet* _mark_queues;
  // Keep track of the number of oops that are not transferred to mark queues.
  // This is volatile because workers update it, but the control thread reads it.
  volatile size_t              _trashed_oops;

public:
  explicit ShenandoahTransferOldSATBTask(ShenandoahSATBMarkQueueSet& satb_queues, ShenandoahObjToScanQueueSet* mark_queues) :
    WorkerTask("Transfer SATB"),
    _satb_queues(satb_queues),
    _mark_queues(mark_queues),
    _trashed_oops(0) {}

  ~ShenandoahTransferOldSATBTask() {
    if (_trashed_oops > 0) {
      log_debug(gc)("Purged %zu oops from old generation SATB buffers", _trashed_oops);
    }
  }

  void work(uint worker_id) override {
    ShenandoahObjToScanQueue* mark_queue = _mark_queues->queue(worker_id);
    ShenandoahProcessOldSATB processor(mark_queue);
    while (_satb_queues.apply_closure_to_completed_buffer(&processor)) {}

    Atomic::add(&_trashed_oops, processor.trashed_oops());
  }
};

class ShenandoahConcurrentCoalesceAndFillTask : public WorkerTask {
private:
  uint                    _nworkers;
  ShenandoahHeapRegion**  _coalesce_and_fill_region_array;
  uint                    _coalesce_and_fill_region_count;
  volatile bool           _is_preempted;

public:
  ShenandoahConcurrentCoalesceAndFillTask(uint nworkers,
                                          ShenandoahHeapRegion** coalesce_and_fill_region_array,
                                          uint region_count) :
    WorkerTask("Shenandoah Concurrent Coalesce and Fill"),
    _nworkers(nworkers),
    _coalesce_and_fill_region_array(coalesce_and_fill_region_array),
    _coalesce_and_fill_region_count(region_count),
    _is_preempted(false) {
  }

  void work(uint worker_id) override {
    ShenandoahWorkerTimingsTracker timer(ShenandoahPhaseTimings::conc_coalesce_and_fill, ShenandoahPhaseTimings::ScanClusters, worker_id);
    for (uint region_idx = worker_id; region_idx < _coalesce_and_fill_region_count; region_idx += _nworkers) {
      ShenandoahHeapRegion* r = _coalesce_and_fill_region_array[region_idx];
      if (r->is_humongous()) {
        // There is only one object in this region and it is not garbage,
        // so no need to coalesce or fill.
        continue;
      }

      if (!r->oop_coalesce_and_fill(true)) {
        // Coalesce and fill has been preempted
        Atomic::store(&_is_preempted, true);
        return;
      }
    }
  }

  // Value returned from is_completed() is only valid after all worker thread have terminated.
  bool is_completed() {
    return !Atomic::load(&_is_preempted);
  }
};

ShenandoahOldGeneration::ShenandoahOldGeneration(uint max_queues, size_t max_capacity)
  : ShenandoahGeneration(OLD, max_queues, max_capacity),
    _coalesce_and_fill_region_array(NEW_C_HEAP_ARRAY(ShenandoahHeapRegion*, ShenandoahHeap::heap()->num_regions(), mtGC)),
    _old_heuristics(nullptr),
    _region_balance(0),
    _promoted_reserve(0),
    _promoted_expended(0),
    _promotion_potential(0),
    _pad_for_promote_in_place(0),
    _promotable_humongous_regions(0),
    _promotable_regular_regions(0),
    _is_parsable(true),
    _card_scan(nullptr),
    _state(WAITING_FOR_BOOTSTRAP),
    _growth_before_compaction(INITIAL_GROWTH_BEFORE_COMPACTION),
    _min_growth_before_compaction ((ShenandoahMinOldGenGrowthPercent * FRACTIONAL_DENOMINATOR) / 100)
{
  _live_bytes_after_last_mark = ShenandoahHeap::heap()->capacity() * INITIAL_LIVE_FRACTION / FRACTIONAL_DENOMINATOR;
  // Always clear references for old generation
  ref_processor()->set_soft_reference_policy(true);

  if (ShenandoahCardBarrier) {
    ShenandoahCardTable* card_table = ShenandoahBarrierSet::barrier_set()->card_table();
    size_t card_count = card_table->cards_required(ShenandoahHeap::heap()->reserved_region().word_size());
    auto rs = new ShenandoahDirectCardMarkRememberedSet(card_table, card_count);
    _card_scan = new ShenandoahScanRemembered(rs);
  }
}

void ShenandoahOldGeneration::set_promoted_reserve(size_t new_val) {
  shenandoah_assert_heaplocked_or_safepoint();
  _promoted_reserve = new_val;
}

size_t ShenandoahOldGeneration::get_promoted_reserve() const {
  return _promoted_reserve;
}

void ShenandoahOldGeneration::augment_promoted_reserve(size_t increment) {
  shenandoah_assert_heaplocked_or_safepoint();
  _promoted_reserve += increment;
}

void ShenandoahOldGeneration::reset_promoted_expended() {
  shenandoah_assert_heaplocked_or_safepoint();
  Atomic::store(&_promoted_expended, (size_t) 0);
}

size_t ShenandoahOldGeneration::expend_promoted(size_t increment) {
  shenandoah_assert_heaplocked_or_safepoint();
  assert(get_promoted_expended() + increment <= get_promoted_reserve(), "Do not expend more promotion than budgeted");
  return Atomic::add(&_promoted_expended, increment);
}

size_t ShenandoahOldGeneration::unexpend_promoted(size_t decrement) {
  return Atomic::sub(&_promoted_expended, decrement);
}

size_t ShenandoahOldGeneration::get_promoted_expended() const {
  return Atomic::load(&_promoted_expended);
}

bool ShenandoahOldGeneration::can_allocate(const ShenandoahAllocRequest &req) const {
  assert(req.type() != ShenandoahAllocRequest::_alloc_gclab, "GCLAB pertains only to young-gen memory");

  const size_t requested_bytes = req.size() * HeapWordSize;
  // The promotion reserve may also be used for evacuations. If we can promote this object,
  // then we can also evacuate it.
  if (can_promote(requested_bytes)) {
    // The promotion reserve should be able to accommodate this request. The request
    // might still fail if alignment with the card table increases the size. The request
    // may also fail if the heap is badly fragmented and the free set cannot find room for it.
    return true;
  }

  if (req.type() == ShenandoahAllocRequest::_alloc_plab) {
    // The promotion reserve cannot accommodate this plab request. Check if we still have room for
    // evacuations. Note that we cannot really know how much of the plab will be used for evacuations,
    // so here we only check that some evacuation reserve still exists.
    return get_evacuation_reserve() > 0;
  }

  // This is a shared allocation request. We've already checked that it can't be promoted, so if
  // it is a promotion, we return false. Otherwise, it is a shared evacuation request, and we allow
  // the allocation to proceed.
  return !req.is_promotion();
}

void
ShenandoahOldGeneration::configure_plab_for_current_thread(const ShenandoahAllocRequest &req) {
  // Note: Even when a mutator is performing a promotion outside a LAB, we use a 'shared_gc' request.
  if (req.is_gc_alloc()) {
    const size_t actual_size = req.actual_size() * HeapWordSize;
    if (req.type() ==  ShenandoahAllocRequest::_alloc_plab) {
      // We've created a new plab. Now we configure it whether it will be used for promotions
      // and evacuations - or just evacuations.
      Thread* thread = Thread::current();
      ShenandoahThreadLocalData::reset_plab_promoted(thread);

      // The actual size of the allocation may be larger than the requested bytes (due to alignment on card boundaries).
      // If this puts us over our promotion budget, we need to disable future PLAB promotions for this thread.
      if (can_promote(actual_size)) {
        // Assume the entirety of this PLAB will be used for promotion.  This prevents promotion from overreach.
        // When we retire this plab, we'll unexpend what we don't really use.
        expend_promoted(actual_size);
        ShenandoahThreadLocalData::enable_plab_promotions(thread);
        ShenandoahThreadLocalData::set_plab_actual_size(thread, actual_size);
      } else {
        // Disable promotions in this thread because entirety of this PLAB must be available to hold old-gen evacuations.
        ShenandoahThreadLocalData::disable_plab_promotions(thread);
        ShenandoahThreadLocalData::set_plab_actual_size(thread, 0);
      }
    } else if (req.is_promotion()) {
      // Shared promotion.
      expend_promoted(actual_size);
    }
  }
}

size_t ShenandoahOldGeneration::get_live_bytes_after_last_mark() const {
  return _live_bytes_after_last_mark;
}

void ShenandoahOldGeneration::set_live_bytes_after_last_mark(size_t bytes) {
  if (bytes == 0) {
    // Restart search for best old-gen size to the initial state
    _live_bytes_after_last_mark = ShenandoahHeap::heap()->capacity() * INITIAL_LIVE_FRACTION / FRACTIONAL_DENOMINATOR;
    _growth_before_compaction = INITIAL_GROWTH_BEFORE_COMPACTION;
  } else {
    _live_bytes_after_last_mark = bytes;
    _growth_before_compaction /= 2;
    if (_growth_before_compaction < _min_growth_before_compaction) {
      _growth_before_compaction = _min_growth_before_compaction;
    }
  }
}

void ShenandoahOldGeneration::handle_failed_transfer() {
  _old_heuristics->trigger_cannot_expand();
}

size_t ShenandoahOldGeneration::usage_trigger_threshold() const {
  size_t result = _live_bytes_after_last_mark + (_live_bytes_after_last_mark * _growth_before_compaction) / FRACTIONAL_DENOMINATOR;
  return result;
}

bool ShenandoahOldGeneration::contains(ShenandoahAffiliation affiliation) const {
  return affiliation == OLD_GENERATION;
}
bool ShenandoahOldGeneration::contains(ShenandoahHeapRegion* region) const {
  return region->is_old();
}

void ShenandoahOldGeneration::parallel_heap_region_iterate(ShenandoahHeapRegionClosure* cl) {
  ShenandoahIncludeRegionClosure<OLD_GENERATION> old_regions_cl(cl);
  ShenandoahHeap::heap()->parallel_heap_region_iterate(&old_regions_cl);
}

void ShenandoahOldGeneration::heap_region_iterate(ShenandoahHeapRegionClosure* cl) {
  ShenandoahIncludeRegionClosure<OLD_GENERATION> old_regions_cl(cl);
  ShenandoahHeap::heap()->heap_region_iterate(&old_regions_cl);
}

void ShenandoahOldGeneration::set_concurrent_mark_in_progress(bool in_progress) {
  ShenandoahHeap::heap()->set_concurrent_old_mark_in_progress(in_progress);
}

bool ShenandoahOldGeneration::is_concurrent_mark_in_progress() {
  return ShenandoahHeap::heap()->is_concurrent_old_mark_in_progress();
}

void ShenandoahOldGeneration::cancel_marking() {
  if (is_concurrent_mark_in_progress()) {
    log_debug(gc)("Abandon SATB buffers");
    ShenandoahBarrierSet::satb_mark_queue_set().abandon_partial_marking();
  }

  ShenandoahGeneration::cancel_marking();
}

void ShenandoahOldGeneration::cancel_gc() {
  shenandoah_assert_safepoint();
  if (is_idle()) {
#ifdef ASSERT
    validate_waiting_for_bootstrap();
#endif
  } else {
    log_info(gc)("Terminating old gc cycle.");
    // Stop marking
    cancel_marking();
    // Stop tracking old regions
    abandon_collection_candidates();
    // Remove old generation access to young generation mark queues
    ShenandoahHeap::heap()->young_generation()->set_old_gen_task_queues(nullptr);
    // Transition to IDLE now.
    transition_to(ShenandoahOldGeneration::WAITING_FOR_BOOTSTRAP);
  }
}

void ShenandoahOldGeneration::prepare_gc() {
  // Now that we have made the old generation parsable, it is safe to reset the mark bitmap.
  assert(state() != FILLING, "Cannot reset old without making it parsable");

  ShenandoahGeneration::prepare_gc();
}

bool ShenandoahOldGeneration::entry_coalesce_and_fill() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();

  static const char* msg = "Coalescing and filling (Old)";
  ShenandoahConcurrentPhase gc_phase(msg, ShenandoahPhaseTimings::conc_coalesce_and_fill);

  TraceCollectorStats tcs(heap->monitoring_support()->concurrent_collection_counters());
  EventMark em("%s", msg);
  ShenandoahWorkerScope scope(heap->workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_conc_marking(),
                              msg);

  return coalesce_and_fill();
}

// Make the old generation regions parsable, so they can be safely
// scanned when looking for objects in memory indicated by dirty cards.
bool ShenandoahOldGeneration::coalesce_and_fill() {
  transition_to(FILLING);

  // This code will see the same set of regions to fill on each resumption as it did
  // on the initial run. That's okay because each region keeps track of its own coalesce
  // and fill state. Regions that were filled on a prior attempt will not try to fill again.
  uint coalesce_and_fill_regions_count = _old_heuristics->get_coalesce_and_fill_candidates(_coalesce_and_fill_region_array);
  assert(coalesce_and_fill_regions_count <= ShenandoahHeap::heap()->num_regions(), "Sanity");
  if (coalesce_and_fill_regions_count == 0) {
    // No regions need to be filled.
    abandon_collection_candidates();
    return true;
  }

  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  WorkerThreads* workers = heap->workers();
  uint nworkers = workers->active_workers();
  ShenandoahConcurrentCoalesceAndFillTask task(nworkers, _coalesce_and_fill_region_array, coalesce_and_fill_regions_count);

  log_debug(gc)("Starting (or resuming) coalesce-and-fill of " UINT32_FORMAT " old heap regions", coalesce_and_fill_regions_count);
  workers->run_task(&task);
  if (task.is_completed()) {
    // We no longer need to track regions that need to be coalesced and filled.
    abandon_collection_candidates();
    return true;
  } else {
    // Coalesce-and-fill has been preempted. We'll finish that effort in the future.  Do not invoke
    // ShenandoahGeneration::prepare_gc() until coalesce-and-fill is done because it resets the mark bitmap
    // and invokes set_mark_incomplete().  Coalesce-and-fill depends on the mark bitmap.
    log_debug(gc)("Suspending coalesce-and-fill of old heap regions");
    return false;
  }
}

void ShenandoahOldGeneration::concurrent_transfer_pointers_from_satb() const {
  const ShenandoahHeap* heap = ShenandoahHeap::heap();
  assert(heap->is_concurrent_old_mark_in_progress(), "Only necessary during old marking.");
  log_debug(gc)("Transfer SATB buffers");

  // Step 1. All threads need to 'complete' partially filled, thread local SATB buffers. This
  // is accomplished in ShenandoahConcurrentGC::complete_abbreviated_cycle using a Handshake
  // operation.
  // Step 2. Use worker threads to transfer oops from old, active regions in the completed
  // SATB buffers to old generation mark queues.
  ShenandoahSATBMarkQueueSet& satb_queues = ShenandoahBarrierSet::satb_mark_queue_set();
  ShenandoahTransferOldSATBTask transfer_task(satb_queues, task_queues());
  heap->workers()->run_task(&transfer_task);
}

void ShenandoahOldGeneration::transfer_pointers_from_satb() const {
  const ShenandoahHeap* heap = ShenandoahHeap::heap();
  assert(heap->is_concurrent_old_mark_in_progress(), "Only necessary during old marking.");
  log_debug(gc)("Transfer SATB buffers");
  ShenandoahPurgeSATBTask purge_satb_task(task_queues());
  heap->workers()->run_task(&purge_satb_task);
}

bool ShenandoahOldGeneration::contains(oop obj) const {
  return ShenandoahHeap::heap()->is_in_old(obj);
}

void ShenandoahOldGeneration::prepare_regions_and_collection_set(bool concurrent) {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  assert(!heap->is_full_gc_in_progress(), "Only for concurrent and degenerated GC");

  {
    ShenandoahGCPhase phase(concurrent ?
        ShenandoahPhaseTimings::final_update_region_states :
        ShenandoahPhaseTimings::degen_gc_final_update_region_states);
    ShenandoahFinalMarkUpdateRegionStateClosure cl(complete_marking_context());

    parallel_heap_region_iterate(&cl);
    heap->assert_pinned_region_status();
  }

  {
    // This doesn't actually choose a collection set, but prepares a list of
    // regions as 'candidates' for inclusion in a mixed collection.
    ShenandoahGCPhase phase(concurrent ?
        ShenandoahPhaseTimings::choose_cset :
        ShenandoahPhaseTimings::degen_gc_choose_cset);
    ShenandoahHeapLocker locker(heap->lock());
    _old_heuristics->prepare_for_old_collections();
  }

  {
    // Though we did not choose a collection set above, we still may have
    // freed up immediate garbage regions so proceed with rebuilding the free set.
    ShenandoahGCPhase phase(concurrent ?
        ShenandoahPhaseTimings::final_rebuild_freeset :
        ShenandoahPhaseTimings::degen_gc_final_rebuild_freeset);
    ShenandoahHeapLocker locker(heap->lock());
    size_t cset_young_regions, cset_old_regions;
    size_t first_old, last_old, num_old;
    heap->free_set()->prepare_to_rebuild(cset_young_regions, cset_old_regions, first_old, last_old, num_old);
    // This is just old-gen completion.  No future budgeting required here.  The only reason to rebuild the freeset here
    // is in case there was any immediate old garbage identified.
    heap->free_set()->finish_rebuild(cset_young_regions, cset_old_regions, num_old);
  }
}

const char* ShenandoahOldGeneration::state_name(State state) {
  switch (state) {
    case WAITING_FOR_BOOTSTRAP:   return "Waiting for Bootstrap";
    case FILLING:                 return "Coalescing";
    case BOOTSTRAPPING:           return "Bootstrapping";
    case MARKING:                 return "Marking";
    case EVACUATING:              return "Evacuating";
    case EVACUATING_AFTER_GLOBAL: return "Evacuating (G)";
    default:
      ShouldNotReachHere();
      return "Unknown";
  }
}

void ShenandoahOldGeneration::transition_to(State new_state) {
  if (_state != new_state) {
    log_debug(gc, thread)("Old generation transition from %s to %s", state_name(_state), state_name(new_state));
    EventMark event("Old was %s, now is %s", state_name(_state), state_name(new_state));
    validate_transition(new_state);
    _state = new_state;
  }
}

#ifdef ASSERT
// This diagram depicts the expected state transitions for marking the old generation
// and preparing for old collections. When a young generation cycle executes, the
// remembered set scan must visit objects in old regions. Visiting an object which
// has become dead on previous old cycles will result in crashes. To avoid visiting
// such objects, the remembered set scan will use the old generation mark bitmap when
// possible. It is _not_ possible to use the old generation bitmap when old marking
// is active (bitmap is not complete). For this reason, the old regions are made
// parsable _before_ the old generation bitmap is reset. The diagram does not depict
// cancellation of old collections by global or full collections.
//
// When a global collection supersedes an old collection, the global mark still
// "completes" the old mark bitmap. Subsequent remembered set scans may use the
// old generation mark bitmap, but any uncollected old regions must still be made parsable
// before the next old generation cycle begins. For this reason, a global collection may
// create mixed collection candidates and coalesce and fill candidates and will put
// the old generation in the respective states (EVACUATING or FILLING). After a Full GC,
// the mark bitmaps are all reset, all regions are parsable and the mark context will
// not be "complete". After a Full GC, remembered set scans will _not_ use the mark bitmap
// and we expect the old generation to be waiting for bootstrap.
//
//                              +-----------------+
//               +------------> |     FILLING     | <---+
//               |   +--------> |                 |     |
//               |   |          +-----------------+     |
//               |   |            |                     |
//               |   |            | Filling Complete    | <-> A global collection may
//               |   |            v                     |     move the old generation
//               |   |          +-----------------+     |     directly from waiting for
//           +-- |-- |--------> |     WAITING     |     |     bootstrap to filling or
//           |   |   |    +---- |  FOR BOOTSTRAP  | ----+     evacuating. It may also
//           |   |   |    |     +-----------------+           move from filling to waiting
//           |   |   |    |       |                           for bootstrap.
//           |   |   |    |       | Reset Bitmap
//           |   |   |    |       v
//           |   |   |    |     +-----------------+     +----------------------+
//           |   |   |    |     |    BOOTSTRAP    | <-> |       YOUNG GC       |
//           |   |   |    |     |                 |     | (RSet Parses Region) |
//           |   |   |    |     +-----------------+     +----------------------+
//           |   |   |    |       |
//           |   |   |    |       | Old Marking
//           |   |   |    |       v
//           |   |   |    |     +-----------------+     +----------------------+
//           |   |   |    |     |     MARKING     | <-> |       YOUNG GC       |
//           |   |   +--------- |                 |     | (RSet Parses Region) |
//           |   |        |     +-----------------+     +----------------------+
//           |   |        |       |
//           |   |        |       | Has Evacuation Candidates
//           |   |        |       v
//           |   |        |     +-----------------+     +--------------------+
//           |   |        +---> |    EVACUATING   | <-> |      YOUNG GC      |
//           |   +------------- |                 |     | (RSet Uses Bitmap) |
//           |                  +-----------------+     +--------------------+
//           |                    |
//           |                    | Global Cycle Coalesces and Fills Old Regions
//           |                    v
//           |                  +-----------------+     +--------------------+
//           +----------------- |    EVACUATING   | <-> |      YOUNG GC      |
//                              |   AFTER GLOBAL  |     | (RSet Uses Bitmap) |
//                              +-----------------+     +--------------------+
//
//
void ShenandoahOldGeneration::validate_transition(State new_state) {
  ShenandoahGenerationalHeap* heap = ShenandoahGenerationalHeap::heap();
  switch (new_state) {
    case FILLING:
      assert(_state != BOOTSTRAPPING, "Cannot begin making old regions parsable after bootstrapping");
      assert(is_mark_complete(), "Cannot begin filling without first completing marking, state is '%s'", state_name(_state));
      assert(_old_heuristics->has_coalesce_and_fill_candidates(), "Cannot begin filling without something to fill.");
      break;
    case WAITING_FOR_BOOTSTRAP:
      // GC cancellation can send us back here from any state.
      validate_waiting_for_bootstrap();
      break;
    case BOOTSTRAPPING:
      assert(_state == WAITING_FOR_BOOTSTRAP, "Cannot reset bitmap without making old regions parsable, state is '%s'", state_name(_state));
      assert(_old_heuristics->unprocessed_old_collection_candidates() == 0, "Cannot bootstrap with mixed collection candidates");
      assert(!heap->is_prepare_for_old_mark_in_progress(), "Cannot still be making old regions parsable.");
      break;
    case MARKING:
      assert(_state == BOOTSTRAPPING, "Must have finished bootstrapping before marking, state is '%s'", state_name(_state));
      assert(heap->young_generation()->old_gen_task_queues() != nullptr, "Young generation needs old mark queues.");
      assert(heap->is_concurrent_old_mark_in_progress(), "Should be marking old now.");
      break;
    case EVACUATING_AFTER_GLOBAL:
      assert(_state == EVACUATING, "Must have been evacuating, state is '%s'", state_name(_state));
      break;
    case EVACUATING:
      assert(_state == WAITING_FOR_BOOTSTRAP || _state == MARKING, "Cannot have old collection candidates without first marking, state is '%s'", state_name(_state));
      assert(_old_heuristics->unprocessed_old_collection_candidates() > 0, "Must have collection candidates here.");
      break;
    default:
      fatal("Unknown new state");
  }
}

bool ShenandoahOldGeneration::validate_waiting_for_bootstrap() {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  assert(!heap->is_concurrent_old_mark_in_progress(), "Cannot become ready for bootstrap during old mark.");
  assert(heap->young_generation()->old_gen_task_queues() == nullptr, "Cannot become ready for bootstrap when still setup for bootstrapping.");
  assert(!is_concurrent_mark_in_progress(), "Cannot be marking in IDLE");
  assert(!heap->young_generation()->is_bootstrap_cycle(), "Cannot have old mark queues if IDLE");
  assert(!_old_heuristics->has_coalesce_and_fill_candidates(), "Cannot have coalesce and fill candidates in IDLE");
  assert(_old_heuristics->unprocessed_old_collection_candidates() == 0, "Cannot have mixed collection candidates in IDLE");
  return true;
}
#endif

ShenandoahHeuristics* ShenandoahOldGeneration::initialize_heuristics(ShenandoahMode* gc_mode) {
  _old_heuristics = new ShenandoahOldHeuristics(this, ShenandoahGenerationalHeap::heap());
  _old_heuristics->set_guaranteed_gc_interval(ShenandoahGuaranteedOldGCInterval);
  _heuristics = _old_heuristics;
  return _heuristics;
}

void ShenandoahOldGeneration::record_success_concurrent(bool abbreviated) {
  heuristics()->record_success_concurrent();
  ShenandoahHeap::heap()->shenandoah_policy()->record_success_old();
}

void ShenandoahOldGeneration::handle_failed_evacuation() {
  if (_failed_evacuation.try_set()) {
    log_debug(gc)("Old gen evac failure.");
  }
}

void ShenandoahOldGeneration::handle_failed_promotion(Thread* thread, size_t size) {
  // We squelch excessive reports to reduce noise in logs.
  const size_t MaxReportsPerEpoch = 4;
  static size_t last_report_epoch = 0;
  static size_t epoch_report_count = 0;
  auto heap = ShenandoahGenerationalHeap::heap();

  size_t promotion_reserve;
  size_t promotion_expended;

  const size_t gc_id = heap->control_thread()->get_gc_id();

  if ((gc_id != last_report_epoch) || (epoch_report_count++ < MaxReportsPerEpoch)) {
    {
      // Promotion failures should be very rare.  Invest in providing useful diagnostic info.
      ShenandoahHeapLocker locker(heap->lock());
      promotion_reserve = get_promoted_reserve();
      promotion_expended = get_promoted_expended();
    }
    PLAB* const plab = ShenandoahThreadLocalData::plab(thread);
    const size_t words_remaining = (plab == nullptr)? 0: plab->words_remaining();
    const char* promote_enabled = ShenandoahThreadLocalData::allow_plab_promotions(thread)? "enabled": "disabled";

    log_info(gc, ergo)("Promotion failed, size %zu, has plab? %s, PLAB remaining: %zu"
                       ", plab promotions %s, promotion reserve: %zu, promotion expended: %zu"
                       ", old capacity: %zu, old_used: %zu, old unaffiliated regions: %zu",
                       size * HeapWordSize, plab == nullptr? "no": "yes",
                       words_remaining * HeapWordSize, promote_enabled, promotion_reserve, promotion_expended,
                       max_capacity(), used(), free_unaffiliated_regions());

    if ((gc_id == last_report_epoch) && (epoch_report_count >= MaxReportsPerEpoch)) {
      log_debug(gc, ergo)("Squelching additional promotion failure reports for current epoch");
    } else if (gc_id != last_report_epoch) {
      last_report_epoch = gc_id;
      epoch_report_count = 1;
    }
  }
}

void ShenandoahOldGeneration::handle_evacuation(HeapWord* obj, size_t words, bool promotion) {
  // Only register the copy of the object that won the evacuation race.
  _card_scan->register_object_without_lock(obj);

  // Mark the entire range of the evacuated object as dirty.  At next remembered set scan,
  // we will clear dirty bits that do not hold interesting pointers.  It's more efficient to
  // do this in batch, in a background GC thread than to try to carefully dirty only cards
  // that hold interesting pointers right now.
  _card_scan->mark_range_as_dirty(obj, words);

  if (promotion) {
    // This evacuation was a promotion, track this as allocation against old gen
    increase_allocated(words * HeapWordSize);
  }
}

bool ShenandoahOldGeneration::has_unprocessed_collection_candidates() {
  return _old_heuristics->unprocessed_old_collection_candidates() > 0;
}

size_t ShenandoahOldGeneration::unprocessed_collection_candidates_live_memory() {
  return _old_heuristics->unprocessed_old_collection_candidates_live_memory();
}

void ShenandoahOldGeneration::abandon_collection_candidates() {
  _old_heuristics->abandon_collection_candidates();
}

void ShenandoahOldGeneration::prepare_for_mixed_collections_after_global_gc() {
  assert(is_mark_complete(), "Expected old generation mark to be complete after global cycle.");
  _old_heuristics->prepare_for_old_collections();
  log_info(gc, ergo)("After choosing global collection set, mixed candidates: " UINT32_FORMAT ", coalescing candidates: %zu",
               _old_heuristics->unprocessed_old_collection_candidates(),
               _old_heuristics->coalesce_and_fill_candidates_count());
}

void ShenandoahOldGeneration::parallel_heap_region_iterate_free(ShenandoahHeapRegionClosure* cl) {
  // Iterate over old and free regions (exclude young).
  ShenandoahExcludeRegionClosure<YOUNG_GENERATION> exclude_cl(cl);
  ShenandoahGeneration::parallel_heap_region_iterate_free(&exclude_cl);
}

void ShenandoahOldGeneration::set_parsable(bool parsable) {
  _is_parsable = parsable;
  if (_is_parsable) {
    // The current state would have been chosen during final mark of the global
    // collection, _before_ any decisions about class unloading have been made.
    //
    // After unloading classes, we have made the old generation regions parsable.
    // We can skip filling or transition to a state that knows everything has
    // already been filled.
    switch (state()) {
      case ShenandoahOldGeneration::EVACUATING:
        transition_to(ShenandoahOldGeneration::EVACUATING_AFTER_GLOBAL);
        break;
      case ShenandoahOldGeneration::FILLING:
        assert(_old_heuristics->unprocessed_old_collection_candidates() == 0, "Expected no mixed collection candidates");
        assert(_old_heuristics->coalesce_and_fill_candidates_count() > 0, "Expected coalesce and fill candidates");
        // When the heuristic put the old generation in this state, it didn't know
        // that we would unload classes and make everything parsable. But, we know
        // that now so we can override this state.
        abandon_collection_candidates();
        transition_to(ShenandoahOldGeneration::WAITING_FOR_BOOTSTRAP);
        break;
      default:
        // We can get here during a full GC. The full GC will cancel anything
        // happening in the old generation and return it to the waiting for bootstrap
        // state. The full GC will then record that the old regions are parsable
        // after rebuilding the remembered set.
        assert(is_idle(), "Unexpected state %s at end of global GC", state_name());
        break;
    }
  }
}

void ShenandoahOldGeneration::complete_mixed_evacuations() {
  assert(is_doing_mixed_evacuations(), "Mixed evacuations should be in progress");
  if (!_old_heuristics->has_coalesce_and_fill_candidates()) {
    // No candidate regions to coalesce and fill
    transition_to(ShenandoahOldGeneration::WAITING_FOR_BOOTSTRAP);
    return;
  }

  if (state() == ShenandoahOldGeneration::EVACUATING) {
    transition_to(ShenandoahOldGeneration::FILLING);
    return;
  }

  // Here, we have no more candidates for mixed collections. The candidates for coalescing
  // and filling have already been processed during the global cycle, so there is nothing
  // more to do.
  assert(state() == ShenandoahOldGeneration::EVACUATING_AFTER_GLOBAL, "Should be evacuating after a global cycle");
  abandon_collection_candidates();
  transition_to(ShenandoahOldGeneration::WAITING_FOR_BOOTSTRAP);
}

void ShenandoahOldGeneration::abandon_mixed_evacuations() {
  switch(state()) {
    case ShenandoahOldGeneration::EVACUATING:
      transition_to(ShenandoahOldGeneration::FILLING);
      break;
    case ShenandoahOldGeneration::EVACUATING_AFTER_GLOBAL:
      abandon_collection_candidates();
      transition_to(ShenandoahOldGeneration::WAITING_FOR_BOOTSTRAP);
      break;
    default:
      log_warning(gc)("Abandon mixed evacuations in unexpected state: %s", state_name(state()));
      ShouldNotReachHere();
      break;
  }
}

void ShenandoahOldGeneration::clear_cards_for(ShenandoahHeapRegion* region) {
  _card_scan->mark_range_as_empty(region->bottom(), pointer_delta(region->end(), region->bottom()));
}

void ShenandoahOldGeneration::mark_card_as_dirty(void* location) {
  _card_scan->mark_card_as_dirty((HeapWord*)location);
}
