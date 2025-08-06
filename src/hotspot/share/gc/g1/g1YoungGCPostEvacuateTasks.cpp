/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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


#include "compiler/oopMap.hpp"
#include "gc/g1/g1CardSetMemory.hpp"
#include "gc/g1/g1CardTableEntryClosure.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1CollectionSetCandidates.inline.hpp"
#include "gc/g1/g1CollectorState.hpp"
#include "gc/g1/g1ConcurrentMark.inline.hpp"
#include "gc/g1/g1EvacFailureRegions.inline.hpp"
#include "gc/g1/g1EvacInfo.hpp"
#include "gc/g1/g1EvacStats.inline.hpp"
#include "gc/g1/g1HeapRegion.inline.hpp"
#include "gc/g1/g1HeapRegionPrinter.hpp"
#include "gc/g1/g1HeapRegionRemSet.inline.hpp"
#include "gc/g1/g1OopClosures.inline.hpp"
#include "gc/g1/g1ParScanThreadState.hpp"
#include "gc/g1/g1RemSet.hpp"
#include "gc/g1/g1YoungGCPostEvacuateTasks.hpp"
#include "gc/shared/bufferNode.hpp"
#include "gc/shared/partialArrayState.hpp"
#include "jfr/jfrEvents.hpp"
#include "oops/access.inline.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/prefetch.hpp"
#include "runtime/threads.hpp"
#include "runtime/threadSMR.hpp"
#include "utilities/bitMap.inline.hpp"
#include "utilities/ticks.hpp"

class G1PostEvacuateCollectionSetCleanupTask1::MergePssTask : public G1AbstractSubTask {
  G1ParScanThreadStateSet* _per_thread_states;

public:
  MergePssTask(G1ParScanThreadStateSet* per_thread_states) :
    G1AbstractSubTask(G1GCPhaseTimes::MergePSS),
    _per_thread_states(per_thread_states) { }

  double worker_cost() const override { return 1.0; }

  void do_work(uint worker_id) override { _per_thread_states->flush_stats(); }
};

class G1PostEvacuateCollectionSetCleanupTask1::RecalculateUsedTask : public G1AbstractSubTask {
  bool _evacuation_failed;
  bool _allocation_failed;

public:
  RecalculateUsedTask(bool evacuation_failed, bool allocation_failed) :
    G1AbstractSubTask(G1GCPhaseTimes::RecalculateUsed),
    _evacuation_failed(evacuation_failed),
    _allocation_failed(allocation_failed) { }

  double worker_cost() const override {
    // If there is no evacuation failure, the work to perform is minimal.
    return _evacuation_failed ? 1.0 : AlmostNoWork;
  }

  void do_work(uint worker_id) override {
    G1CollectedHeap::heap()->update_used_after_gc(_evacuation_failed);
    if (_allocation_failed) {
      // Reset the G1GCAllocationFailureALot counters and flags
      G1CollectedHeap::heap()->allocation_failure_injector()->reset();
    }
  }
};

class G1PostEvacuateCollectionSetCleanupTask1::SampleCollectionSetCandidatesTask : public G1AbstractSubTask {
public:
  SampleCollectionSetCandidatesTask() : G1AbstractSubTask(G1GCPhaseTimes::SampleCollectionSetCandidates) { }

  static bool should_execute() {
    return G1CollectedHeap::heap()->should_sample_collection_set_candidates();
  }

  double worker_cost() const override {
    return should_execute() ? 1.0 : AlmostNoWork;
  }

  void do_work(uint worker_id) override {
    G1CollectedHeap* g1h = G1CollectedHeap::heap();

    G1MonotonicArenaMemoryStats _total;
    G1CollectionSetCandidates* candidates = g1h->collection_set()->candidates();
    for (G1CSetCandidateGroup* gr : candidates->from_marking_groups()) {
      _total.add(gr->card_set_memory_stats());
    }

    for (G1CSetCandidateGroup* gr : candidates->retained_groups()) {
      _total.add(gr->card_set_memory_stats());
    }
    g1h->set_collection_set_candidates_stats(_total);
  }
};

class G1PostEvacuateCollectionSetCleanupTask1::RestoreEvacFailureRegionsTask : public G1AbstractSubTask {
  G1CollectedHeap* _g1h;
  G1ConcurrentMark* _cm;

  G1EvacFailureRegions* _evac_failure_regions;
  CHeapBitMap _chunk_bitmap;

  uint _num_chunks_per_region;
  uint _num_evac_fail_regions;
  size_t _chunk_size;

  class PhaseTimesStat {
    static constexpr G1GCPhaseTimes::GCParPhases phase_name =
      G1GCPhaseTimes::RemoveSelfForwards;

    G1GCPhaseTimes* _phase_times;
    uint _worker_id;
    Ticks _start;

  public:
    PhaseTimesStat(G1GCPhaseTimes* phase_times, uint worker_id) :
      _phase_times(phase_times),
      _worker_id(worker_id),
      _start(Ticks::now()) { }

    ~PhaseTimesStat() {
      _phase_times->record_or_add_time_secs(phase_name,
                                            _worker_id,
                                            (Ticks::now() - _start).seconds());
    }

    void register_empty_chunk() {
      _phase_times->record_or_add_thread_work_item(phase_name,
                                                   _worker_id,
                                                   1,
                                                   G1GCPhaseTimes::RemoveSelfForwardEmptyChunksNum);
    }

    void register_nonempty_chunk() {
      _phase_times->record_or_add_thread_work_item(phase_name,
                                                   _worker_id,
                                                   1,
                                                   G1GCPhaseTimes::RemoveSelfForwardChunksNum);
    }

    void register_objects_count_and_size(size_t num_marked_obj, size_t marked_words) {
      _phase_times->record_or_add_thread_work_item(phase_name,
                                                   _worker_id,
                                                   num_marked_obj,
                                                   G1GCPhaseTimes::RemoveSelfForwardObjectsNum);

      size_t marked_bytes = marked_words * HeapWordSize;
      _phase_times->record_or_add_thread_work_item(phase_name,
                                                   _worker_id,
                                                   marked_bytes,
                                                   G1GCPhaseTimes::RemoveSelfForwardObjectsBytes);
    }
  };

  // Fill the memory area from start to end with filler objects, and update the BOT
  // accordingly. Since we clear and use the bitmap for marking objects that failed
  // evacuation, there is no other work to be done there.
  static size_t zap_dead_objects(G1HeapRegion* hr, HeapWord* start, HeapWord* end) {
    assert(start <= end, "precondition");
    if (start == end) {
      return 0;
    }

    hr->fill_range_with_dead_objects(start, end);
    return pointer_delta(end, start);
  }

  static void update_garbage_words_in_hr(G1HeapRegion* hr, size_t garbage_words) {
    if (garbage_words != 0) {
      hr->note_self_forward_chunk_done(garbage_words * HeapWordSize);
    }
  }

  static void prefetch_obj(HeapWord* obj_addr) {
    Prefetch::write(obj_addr, PrefetchScanIntervalInBytes);
  }

  bool claim_chunk(uint chunk_idx) {
    return _chunk_bitmap.par_set_bit(chunk_idx);
  }

  void process_chunk(uint worker_id, uint chunk_idx) {
    PhaseTimesStat stat(_g1h->phase_times(), worker_id);

    G1CMBitMap* bitmap = _cm->mark_bitmap();
    const uint region_idx = _evac_failure_regions->get_region_idx(chunk_idx / _num_chunks_per_region);
    G1HeapRegion* hr = _g1h->region_at(region_idx);

    HeapWord* hr_bottom = hr->bottom();
    HeapWord* hr_top = hr->top();
    HeapWord* chunk_start = hr_bottom + (chunk_idx % _num_chunks_per_region) * _chunk_size;

    assert(chunk_start < hr->end(), "inv");
    if (chunk_start >= hr_top) {
      return;
    }

    HeapWord* chunk_end = MIN2(chunk_start + _chunk_size, hr_top);
    HeapWord* first_marked_addr = bitmap->get_next_marked_addr(chunk_start, hr_top);

    size_t garbage_words = 0;

    if (chunk_start == hr_bottom) {
      // This is the bottom-most chunk in this region; zap [bottom, first_marked_addr).
      garbage_words += zap_dead_objects(hr, hr_bottom, first_marked_addr);
    }

    if (first_marked_addr >= chunk_end) {
      stat.register_empty_chunk();
      update_garbage_words_in_hr(hr, garbage_words);
      return;
    }

    stat.register_nonempty_chunk();

    size_t num_marked_objs = 0;
    size_t marked_words = 0;

    HeapWord* obj_addr = first_marked_addr;
    assert(chunk_start <= obj_addr && obj_addr < chunk_end,
           "object " PTR_FORMAT " must be within chunk [" PTR_FORMAT ", " PTR_FORMAT "[",
           p2i(obj_addr), p2i(chunk_start), p2i(chunk_end));
    do {
      assert(bitmap->is_marked(obj_addr), "inv");
      prefetch_obj(obj_addr);

      oop obj = cast_to_oop(obj_addr);
      const size_t obj_size = obj->size();
      HeapWord* const obj_end_addr = obj_addr + obj_size;

      {
        // Process marked object.
        assert(obj->is_self_forwarded(), "must be self-forwarded");
        obj->unset_self_forwarded();
        hr->update_bot_for_block(obj_addr, obj_end_addr);

        // Statistics
        num_marked_objs++;
        marked_words += obj_size;
      }

      assert(obj_end_addr <= hr_top, "inv");
      // Use hr_top as the limit so that we zap dead ranges up to the next
      // marked obj or hr_top.
      HeapWord* next_marked_obj_addr = bitmap->get_next_marked_addr(obj_end_addr, hr_top);
      garbage_words += zap_dead_objects(hr, obj_end_addr, next_marked_obj_addr);
      obj_addr = next_marked_obj_addr;
    } while (obj_addr < chunk_end);

    assert(marked_words > 0 && num_marked_objs > 0, "inv");

    stat.register_objects_count_and_size(num_marked_objs, marked_words);

    update_garbage_words_in_hr(hr, garbage_words);
  }

public:
  RestoreEvacFailureRegionsTask(G1EvacFailureRegions* evac_failure_regions) :
    G1AbstractSubTask(G1GCPhaseTimes::RestoreEvacuationFailedRegions),
    _g1h(G1CollectedHeap::heap()),
    _cm(_g1h->concurrent_mark()),
    _evac_failure_regions(evac_failure_regions),
    _chunk_bitmap(mtGC) {

    _num_evac_fail_regions = _evac_failure_regions->num_regions_evac_failed();
    _num_chunks_per_region = G1CollectedHeap::get_chunks_per_region();

    _chunk_size = static_cast<uint>(G1HeapRegion::GrainWords / _num_chunks_per_region);

    log_debug(gc, ergo)("Initializing removing self forwards with %u chunks per region",
                        _num_chunks_per_region);

    _chunk_bitmap.resize(_num_chunks_per_region * _num_evac_fail_regions);
  }

  double worker_cost() const override {
    assert(_evac_failure_regions->has_regions_evac_failed(), "Should not call this if there were no evacuation failures");

    double workers_per_region = (double)G1CollectedHeap::get_chunks_per_region() / G1RestoreRetainedRegionChunksPerWorker;
    return workers_per_region * _evac_failure_regions->num_regions_evac_failed();
  }

  void do_work(uint worker_id) override {
    const uint total_workers = G1CollectedHeap::heap()->workers()->active_workers();
    const uint total_chunks = _num_chunks_per_region * _num_evac_fail_regions;
    const uint start_chunk_idx = worker_id * total_chunks / total_workers;

    for (uint i = 0; i < total_chunks; i++) {
      const uint chunk_idx = (start_chunk_idx + i) % total_chunks;
      if (claim_chunk(chunk_idx)) {
        process_chunk(worker_id, chunk_idx);
      }
    }
  }
};

G1PostEvacuateCollectionSetCleanupTask1::G1PostEvacuateCollectionSetCleanupTask1(G1ParScanThreadStateSet* per_thread_states,
                                                                                 G1EvacFailureRegions* evac_failure_regions) :
  G1BatchedTask("Post Evacuate Cleanup 1", G1CollectedHeap::heap()->phase_times())
{
  bool evac_failed = evac_failure_regions->has_regions_evac_failed();
  bool alloc_failed = evac_failure_regions->has_regions_alloc_failed();

  add_serial_task(new MergePssTask(per_thread_states));
  add_serial_task(new RecalculateUsedTask(evac_failed, alloc_failed));
  if (SampleCollectionSetCandidatesTask::should_execute()) {
    add_serial_task(new SampleCollectionSetCandidatesTask());
  }
  add_parallel_task(G1CollectedHeap::heap()->rem_set()->create_cleanup_after_scan_heap_roots_task());
  if (evac_failed) {
    add_parallel_task(new RestoreEvacFailureRegionsTask(evac_failure_regions));
  }
}

class G1FreeHumongousRegionClosure : public G1HeapRegionIndexClosure {
  uint _humongous_objects_reclaimed;
  uint _humongous_regions_reclaimed;
  size_t _freed_bytes;
  G1CollectedHeap* _g1h;

  // Returns whether the given humongous object defined by the start region index
  // is reclaimable.
  //
  // At this point in the garbage collection, checking whether the humongous object
  // is still a candidate is sufficient because:
  //
  // - if it has not been a candidate at the start of collection, it will never
  // changed to be a candidate during the gc (and live).
  // - any found outstanding (i.e. in the DCQ, or in its remembered set)
  // references will set the candidate state to false.
  // - there can be no references from within humongous starts regions referencing
  // the object because we never allocate other objects into them.
  // (I.e. there can be no intra-region references)
  //
  // It is not required to check whether the object has been found dead by marking
  // or not, in fact it would prevent reclamation within a concurrent cycle, as
  // all objects allocated during that time are considered live.
  // SATB marking is even more conservative than the remembered set.
  // So if at this point in the collection we did not find a reference during gc
  // (or it had enough references to not be a candidate, having many remembered
  // set entries), nobody has a reference to it.
  // At the start of collection we flush all refinement logs, and remembered sets
  // are completely up-to-date wrt to references to the humongous object.
  //
  // So there is no need to re-check remembered set size of the humongous region.
  //
  // Other implementation considerations:
  // - never consider object arrays at this time because they would pose
  // considerable effort for cleaning up the remembered sets. This is
  // required because stale remembered sets might reference locations that
  // are currently allocated into.
  bool is_reclaimable(uint region_idx) const {
    return G1CollectedHeap::heap()->is_humongous_reclaim_candidate(region_idx);
  }

public:
  G1FreeHumongousRegionClosure() :
    _humongous_objects_reclaimed(0),
    _humongous_regions_reclaimed(0),
    _freed_bytes(0),
    _g1h(G1CollectedHeap::heap())
  {}

  bool do_heap_region_index(uint region_index) override {
    if (!is_reclaimable(region_index)) {
      return false;
    }

    G1HeapRegion* r = _g1h->region_at(region_index);

    oop obj = cast_to_oop(r->bottom());
    guarantee(obj->is_typeArray(),
              "Only eagerly reclaiming type arrays is supported, but the object "
              PTR_FORMAT " is not.", p2i(r->bottom()));

    log_debug(gc, humongous)("Reclaimed humongous region %u (object size %zu @ " PTR_FORMAT ")",
                             region_index,
                             obj->size() * HeapWordSize,
                             p2i(r->bottom())
                            );

    G1ConcurrentMark* const cm = _g1h->concurrent_mark();
    cm->humongous_object_eagerly_reclaimed(r);
    assert(!cm->is_marked_in_bitmap(obj),
           "Eagerly reclaimed humongous region %u should not be marked at all but is in bitmap %s",
           region_index,
           BOOL_TO_STR(cm->is_marked_in_bitmap(obj)));
    _humongous_objects_reclaimed++;

    auto free_humongous_region = [&] (G1HeapRegion* r) {
      _freed_bytes += r->used();
      r->set_containing_set(nullptr);
      _humongous_regions_reclaimed++;
      G1HeapRegionPrinter::eager_reclaim(r);
      _g1h->free_humongous_region(r, nullptr);
    };

    _g1h->humongous_obj_regions_iterate(r, free_humongous_region);

    return false;
  }

  uint humongous_objects_reclaimed() {
    return _humongous_objects_reclaimed;
  }

  uint humongous_regions_reclaimed() {
    return _humongous_regions_reclaimed;
  }

  size_t bytes_freed() const {
    return _freed_bytes;
  }
};

#if COMPILER2_OR_JVMCI
class G1PostEvacuateCollectionSetCleanupTask2::UpdateDerivedPointersTask : public G1AbstractSubTask {
public:
  UpdateDerivedPointersTask() : G1AbstractSubTask(G1GCPhaseTimes::UpdateDerivedPointers) { }

  double worker_cost() const override { return 1.0; }
  void do_work(uint worker_id) override {   DerivedPointerTable::update_pointers(); }
};
#endif

class G1PostEvacuateCollectionSetCleanupTask2::EagerlyReclaimHumongousObjectsTask : public G1AbstractSubTask {
  uint _humongous_regions_reclaimed;
  size_t _bytes_freed;

public:
  EagerlyReclaimHumongousObjectsTask() :
    G1AbstractSubTask(G1GCPhaseTimes::EagerlyReclaimHumongousObjects),
    _humongous_regions_reclaimed(0),
    _bytes_freed(0) { }

  virtual ~EagerlyReclaimHumongousObjectsTask() {
    G1CollectedHeap* g1h = G1CollectedHeap::heap();

    g1h->remove_from_old_gen_sets(0, _humongous_regions_reclaimed);
    g1h->decrement_summary_bytes(_bytes_freed);
  }

  double worker_cost() const override { return 1.0; }
  void do_work(uint worker_id) override {
    G1CollectedHeap* g1h = G1CollectedHeap::heap();

    G1FreeHumongousRegionClosure cl;
    g1h->heap_region_iterate(&cl);

    record_work_item(worker_id, G1GCPhaseTimes::EagerlyReclaimNumTotal, g1h->num_humongous_objects());
    record_work_item(worker_id, G1GCPhaseTimes::EagerlyReclaimNumCandidates, g1h->num_humongous_reclaim_candidates());
    record_work_item(worker_id, G1GCPhaseTimes::EagerlyReclaimNumReclaimed, cl.humongous_objects_reclaimed());

    _humongous_regions_reclaimed = cl.humongous_regions_reclaimed();
    _bytes_freed = cl.bytes_freed();
  }
};

class RedirtyLoggedCardTableEntryClosure : public G1CardTableEntryClosure {
  size_t _num_dirtied;
  G1CollectedHeap* _g1h;
  G1CardTable* _g1_ct;
  G1EvacFailureRegions* _evac_failure_regions;

  G1HeapRegion* region_for_card(CardValue* card_ptr) const {
    return _g1h->heap_region_containing(_g1_ct->addr_for(card_ptr));
  }

  bool will_become_free(G1HeapRegion* hr) const {
    // A region will be freed by during the FreeCollectionSet phase if the region is in the
    // collection set and has not had an evacuation failure.
    return _g1h->is_in_cset(hr) && !_evac_failure_regions->contains(hr->hrm_index());
  }

public:
  RedirtyLoggedCardTableEntryClosure(G1CollectedHeap* g1h, G1EvacFailureRegions* evac_failure_regions) :
    G1CardTableEntryClosure(),
    _num_dirtied(0),
    _g1h(g1h),
    _g1_ct(g1h->card_table()),
    _evac_failure_regions(evac_failure_regions) { }

  void do_card_ptr(CardValue* card_ptr) override {
    G1HeapRegion* hr = region_for_card(card_ptr);

    // Should only dirty cards in regions that won't be freed.
    if (!will_become_free(hr)) {
      *card_ptr = G1CardTable::dirty_card_val();
      _num_dirtied++;
    }
  }

  size_t num_dirtied()   const { return _num_dirtied; }
};

class G1PostEvacuateCollectionSetCleanupTask2::ProcessEvacuationFailedRegionsTask : public G1AbstractSubTask {
  G1EvacFailureRegions* _evac_failure_regions;
  G1HeapRegionClaimer _claimer;

  class ProcessEvacuationFailedRegionsClosure : public G1HeapRegionClosure {
  public:

    bool do_heap_region(G1HeapRegion* r) override {
      G1CollectedHeap* g1h = G1CollectedHeap::heap();
      G1ConcurrentMark* cm = g1h->concurrent_mark();

      HeapWord* top_at_mark_start = cm->top_at_mark_start(r);
      assert(top_at_mark_start == r->bottom(), "TAMS must not have been set for region %u", r->hrm_index());
      assert(cm->live_bytes(r->hrm_index()) == 0, "Marking live bytes must not be set for region %u", r->hrm_index());

      // Concurrent mark does not mark through regions that we retain (they are root
      // regions wrt to marking), so we must clear their mark data (tams, bitmap, ...)
      // set eagerly or during evacuation failure.
      bool clear_mark_data = !g1h->collector_state()->in_concurrent_start_gc() ||
                             g1h->policy()->should_retain_evac_failed_region(r);

      if (clear_mark_data) {
        g1h->clear_bitmap_for_region(r);
      } else {
        // This evacuation failed region is going to be marked through. Update mark data.
        cm->update_top_at_mark_start(r);
        cm->set_live_bytes(r->hrm_index(), r->live_bytes());
        assert(cm->mark_bitmap()->get_next_marked_addr(r->bottom(), cm->top_at_mark_start(r)) != cm->top_at_mark_start(r),
               "Marks must be on bitmap for region %u", r->hrm_index());
      }
      return false;
    }
  };

public:
  ProcessEvacuationFailedRegionsTask(G1EvacFailureRegions* evac_failure_regions) :
    G1AbstractSubTask(G1GCPhaseTimes::ProcessEvacuationFailedRegions),
    _evac_failure_regions(evac_failure_regions),
    _claimer(0) {
  }

  void set_max_workers(uint max_workers) override {
    _claimer.set_n_workers(max_workers);
  }

  double worker_cost() const override {
    return _evac_failure_regions->num_regions_evac_failed();
  }

  void do_work(uint worker_id) override {
    ProcessEvacuationFailedRegionsClosure cl;
    _evac_failure_regions->par_iterate(&cl, &_claimer, worker_id);
  }
};

class G1PostEvacuateCollectionSetCleanupTask2::RedirtyLoggedCardsTask : public G1AbstractSubTask {
  BufferNodeList* _rdc_buffers;
  uint _num_buffer_lists;
  G1EvacFailureRegions* _evac_failure_regions;

public:
  RedirtyLoggedCardsTask(G1EvacFailureRegions* evac_failure_regions, BufferNodeList* rdc_buffers, uint num_buffer_lists) :
    G1AbstractSubTask(G1GCPhaseTimes::RedirtyCards),
    _rdc_buffers(rdc_buffers),
    _num_buffer_lists(num_buffer_lists),
    _evac_failure_regions(evac_failure_regions) { }

  double worker_cost() const override {
    // Needs more investigation.
    return G1CollectedHeap::heap()->workers()->active_workers();
  }

  void do_work(uint worker_id) override {
    RedirtyLoggedCardTableEntryClosure cl(G1CollectedHeap::heap(), _evac_failure_regions);

    uint start = worker_id;
    for (uint i = 0; i < _num_buffer_lists; i++) {
      uint index = (start + i) % _num_buffer_lists;

      BufferNode* next = Atomic::load(&_rdc_buffers[index]._head);
      BufferNode* tail = Atomic::load(&_rdc_buffers[index]._tail);

      while (next != nullptr) {
        BufferNode* node = next;
        next = Atomic::cmpxchg(&_rdc_buffers[index]._head, node, (node != tail ) ? node->next() : nullptr);
        if (next == node) {
          cl.apply_to_buffer(node, worker_id);
          next = (node != tail ) ? node->next() : nullptr;
        } else {
          break; // If there is contention, move to the next BufferNodeList
        }
      }
    }
    record_work_item(worker_id, 0, cl.num_dirtied());
  }
};

// Helper class to keep statistics for the collection set freeing
class FreeCSetStats {
  size_t _before_used_bytes;   // Usage in regions successfully evacuate
  size_t _after_used_bytes;    // Usage in regions failing evacuation
  size_t _bytes_allocated_in_old_since_last_gc; // Size of young regions turned into old
  size_t _failure_used_words;  // Live size in failed regions
  size_t _failure_waste_words; // Wasted size in failed regions
  uint _regions_freed;         // Number of regions freed

public:
  FreeCSetStats() :
      _before_used_bytes(0),
      _after_used_bytes(0),
      _bytes_allocated_in_old_since_last_gc(0),
      _failure_used_words(0),
      _failure_waste_words(0),
      _regions_freed(0) { }

  void merge_stats(FreeCSetStats* other) {
    assert(other != nullptr, "invariant");
    _before_used_bytes += other->_before_used_bytes;
    _after_used_bytes += other->_after_used_bytes;
    _bytes_allocated_in_old_since_last_gc += other->_bytes_allocated_in_old_since_last_gc;
    _failure_used_words += other->_failure_used_words;
    _failure_waste_words += other->_failure_waste_words;
    _regions_freed += other->_regions_freed;
  }

  void report(G1CollectedHeap* g1h, G1EvacInfo* evacuation_info) {
    evacuation_info->set_regions_freed(_regions_freed);
    evacuation_info->set_collection_set_used_before(_before_used_bytes + _after_used_bytes);
    evacuation_info->increment_collection_set_used_after(_after_used_bytes);

    g1h->decrement_summary_bytes(_before_used_bytes);
    g1h->alloc_buffer_stats(G1HeapRegionAttr::Old)->add_failure_used_and_waste(_failure_used_words, _failure_waste_words);

    G1Policy *policy = g1h->policy();
    policy->old_gen_alloc_tracker()->add_allocated_bytes_since_last_gc(_bytes_allocated_in_old_since_last_gc);

    policy->cset_regions_freed();
  }

  void account_failed_region(G1HeapRegion* r) {
    size_t used_words = r->live_bytes() / HeapWordSize;
    _failure_used_words += used_words;
    _failure_waste_words += G1HeapRegion::GrainWords - used_words;
    _after_used_bytes += r->used();

    // When moving a young gen region to old gen, we "allocate" that whole
    // region there. This is in addition to any already evacuated objects.
    // Notify the policy about that. Old gen regions do not cause an
    // additional allocation: both the objects still in the region and the
    // ones already moved are accounted for elsewhere.
    if (r->is_young()) {
      _bytes_allocated_in_old_since_last_gc += G1HeapRegion::GrainBytes;
    }
  }

  void account_evacuated_region(G1HeapRegion* r) {
    size_t used = r->used();
    assert(used > 0, "region %u %s zero used", r->hrm_index(), r->get_short_type_str());
    _before_used_bytes += used;
    _regions_freed += 1;
  }
};

// Closure applied to all regions in the collection set.
class FreeCSetClosure : public G1HeapRegionClosure {
  // Helper to send JFR events for regions.
  class JFREventForRegion {
    EventGCPhaseParallel _event;

  public:
    JFREventForRegion(G1HeapRegion* region, uint worker_id) : _event() {
      _event.set_gcId(GCId::current());
      _event.set_gcWorkerId(worker_id);
      if (region->is_young()) {
        _event.set_name(G1GCPhaseTimes::phase_name(G1GCPhaseTimes::YoungFreeCSet));
      } else {
        _event.set_name(G1GCPhaseTimes::phase_name(G1GCPhaseTimes::NonYoungFreeCSet));
      }
    }

    ~JFREventForRegion() {
      _event.commit();
    }
  };

  // Helper to do timing for region work.
  class TimerForRegion {
    Tickspan& _time;
    Ticks     _start_time;
  public:
    TimerForRegion(Tickspan& time) : _time(time), _start_time(Ticks::now()) { }
    ~TimerForRegion() {
      _time += Ticks::now() - _start_time;
    }
  };

  // FreeCSetClosure members
  G1CollectedHeap* _g1h;
  const size_t*    _surviving_young_words;
  uint             _worker_id;
  Tickspan         _young_time;
  Tickspan         _non_young_time;
  FreeCSetStats*   _stats;
  G1EvacFailureRegions* _evac_failure_regions;
  uint             _num_retained_regions;

  void assert_tracks_surviving_words(G1HeapRegion* r) {
    assert(r->young_index_in_cset() != 0 &&
           (uint)r->young_index_in_cset() <= _g1h->collection_set()->young_region_length(),
           "Young index %u is wrong for region %u of type %s with %u young regions",
           r->young_index_in_cset(), r->hrm_index(), r->get_type_str(), _g1h->collection_set()->young_region_length());
  }

  void handle_evacuated_region(G1HeapRegion* r) {
    assert(!r->is_empty(), "Region %u is an empty region in the collection set.", r->hrm_index());
    stats()->account_evacuated_region(r);

    G1HeapRegionPrinter::evac_reclaim(r);
    // Free the region and its remembered set.
    _g1h->free_region(r, nullptr);
  }

  void handle_failed_region(G1HeapRegion* r) {
    // Do some allocation statistics accounting. Regions that failed evacuation
    // are always made old, so there is no need to update anything in the young
    // gen statistics, but we need to update old gen statistics.
    stats()->account_failed_region(r);

    G1GCPhaseTimes* p = _g1h->phase_times();
    assert(r->in_collection_set(), "Failed evacuation of region %u not in collection set", r->hrm_index());

    p->record_or_add_thread_work_item(G1GCPhaseTimes::RestoreEvacuationFailedRegions,
                                      _worker_id,
                                      1,
                                      G1GCPhaseTimes::RestoreEvacFailureRegionsEvacFailedNum);

    bool retain_region = _g1h->policy()->should_retain_evac_failed_region(r);
    // Update the region state due to the failed evacuation.
    r->handle_evacuation_failure(retain_region);
    assert(r->is_old(), "must already be relabelled as old");

    if (retain_region) {
      _g1h->retain_region(r);
      _num_retained_regions++;
    }
    assert(retain_region == r->rem_set()->is_tracked(), "When retaining a region, remembered set should be kept.");

    // Add region to old set, need to hold lock.
    MutexLocker x(G1OldSets_lock, Mutex::_no_safepoint_check_flag);
    _g1h->old_set_add(r);
  }

  Tickspan& timer_for_region(G1HeapRegion* r) {
    return r->is_young() ? _young_time : _non_young_time;
  }

  FreeCSetStats* stats() {
    return _stats;
  }

public:
  FreeCSetClosure(const size_t* surviving_young_words,
                  uint worker_id,
                  FreeCSetStats* stats,
                  G1EvacFailureRegions* evac_failure_regions) :
      G1HeapRegionClosure(),
      _g1h(G1CollectedHeap::heap()),
      _surviving_young_words(surviving_young_words),
      _worker_id(worker_id),
      _young_time(),
      _non_young_time(),
      _stats(stats),
      _evac_failure_regions(evac_failure_regions),
      _num_retained_regions(0) { }

  virtual bool do_heap_region(G1HeapRegion* r) {
    assert(r->in_collection_set(), "Invariant: %u missing from CSet", r->hrm_index());
    JFREventForRegion event(r, _worker_id);
    TimerForRegion timer(timer_for_region(r));


    if (r->is_young()) {
      assert_tracks_surviving_words(r);
      r->record_surv_words_in_group(_surviving_young_words[r->young_index_in_cset()]);
    }

    if (_evac_failure_regions->contains(r->hrm_index())) {
      handle_failed_region(r);
    } else {
      handle_evacuated_region(r);
    }
    assert(!_g1h->is_on_master_free_list(r), "sanity");

    return false;
  }

  void report_timing() {
    G1GCPhaseTimes* pt = _g1h->phase_times();
    if (_young_time.value() > 0) {
      pt->record_time_secs(G1GCPhaseTimes::YoungFreeCSet, _worker_id, _young_time.seconds());
    }
    if (_non_young_time.value() > 0) {
      pt->record_time_secs(G1GCPhaseTimes::NonYoungFreeCSet, _worker_id, _non_young_time.seconds());
    }
  }

  bool num_retained_regions() const { return _num_retained_regions; }
};

class G1PostEvacuateCollectionSetCleanupTask2::FreeCollectionSetTask : public G1AbstractSubTask {
  G1CollectedHeap*    _g1h;
  G1EvacInfo*         _evacuation_info;
  FreeCSetStats*      _worker_stats;
  G1HeapRegionClaimer _claimer;
  const size_t*       _surviving_young_words;
  uint                _active_workers;
  G1EvacFailureRegions* _evac_failure_regions;
  volatile uint       _num_retained_regions;

  FreeCSetStats* worker_stats(uint worker) {
    return &_worker_stats[worker];
  }

  void report_statistics() {
    // Merge the accounting
    FreeCSetStats total_stats;
    for (uint worker = 0; worker < _active_workers; worker++) {
      total_stats.merge_stats(worker_stats(worker));
    }
    total_stats.report(_g1h, _evacuation_info);
  }

public:
  FreeCollectionSetTask(G1EvacInfo* evacuation_info,
                        const size_t* surviving_young_words,
                        G1EvacFailureRegions* evac_failure_regions) :
    G1AbstractSubTask(G1GCPhaseTimes::FreeCollectionSet),
    _g1h(G1CollectedHeap::heap()),
    _evacuation_info(evacuation_info),
    _worker_stats(nullptr),
    _claimer(0),
    _surviving_young_words(surviving_young_words),
    _active_workers(0),
    _evac_failure_regions(evac_failure_regions),
    _num_retained_regions(0) {

    _g1h->clear_eden();
  }

  virtual ~FreeCollectionSetTask() {
    Ticks serial_time = Ticks::now();

    bool has_new_retained_regions = Atomic::load(&_num_retained_regions) != 0;
    if (has_new_retained_regions) {
      G1CollectionSetCandidates* candidates = _g1h->collection_set()->candidates();
      candidates->sort_by_efficiency();
    }

    report_statistics();
    for (uint worker = 0; worker < _active_workers; worker++) {
      _worker_stats[worker].~FreeCSetStats();
    }
    FREE_C_HEAP_ARRAY(FreeCSetStats, _worker_stats);

    _g1h->clear_collection_set();

    G1GCPhaseTimes* p = _g1h->phase_times();
    p->record_serial_free_cset_time_ms((Ticks::now() - serial_time).seconds() * 1000.0);
  }

  double worker_cost() const override { return G1CollectedHeap::heap()->collection_set()->region_length(); }

  void set_max_workers(uint max_workers) override {
    _active_workers = max_workers;
    _worker_stats = NEW_C_HEAP_ARRAY(FreeCSetStats, max_workers, mtGC);
    for (uint worker = 0; worker < _active_workers; worker++) {
      ::new (&_worker_stats[worker]) FreeCSetStats();
    }
    _claimer.set_n_workers(_active_workers);
  }

  void do_work(uint worker_id) override {
    FreeCSetClosure cl(_surviving_young_words, worker_id, worker_stats(worker_id), _evac_failure_regions);
    _g1h->collection_set_par_iterate_all(&cl, &_claimer, worker_id);
    // Report per-region type timings.
    cl.report_timing();

    Atomic::add(&_num_retained_regions, cl.num_retained_regions(), memory_order_relaxed);
  }
};

class G1PostEvacuateCollectionSetCleanupTask2::ResizeTLABsTask : public G1AbstractSubTask {
  G1JavaThreadsListClaimer _claimer;

  // There is not much work per thread so the number of threads per worker is high.
  static const uint ThreadsPerWorker = 250;

public:
  ResizeTLABsTask() : G1AbstractSubTask(G1GCPhaseTimes::ResizeThreadLABs), _claimer(ThreadsPerWorker) { }

  void do_work(uint worker_id) override {
    class ResizeClosure : public ThreadClosure {
    public:

      void do_thread(Thread* thread) {
        static_cast<JavaThread*>(thread)->tlab().resize();
      }
    } cl;
    _claimer.apply(&cl);
  }

  double worker_cost() const override {
    return (double)_claimer.length() / ThreadsPerWorker;
  }
};

class G1PostEvacuateCollectionSetCleanupTask2::ResetPartialArrayStateManagerTask
  : public G1AbstractSubTask
{
public:
  ResetPartialArrayStateManagerTask()
    : G1AbstractSubTask(G1GCPhaseTimes::ResetPartialArrayStateManager)
  {}

  double worker_cost() const override {
    return AlmostNoWork;
  }

  void do_work(uint worker_id) override {
    // This must be in phase2 cleanup, after phase1 has destroyed all of the
    // associated allocators.
    G1CollectedHeap::heap()->partial_array_state_manager()->reset();
  }
};

G1PostEvacuateCollectionSetCleanupTask2::G1PostEvacuateCollectionSetCleanupTask2(G1ParScanThreadStateSet* per_thread_states,
                                                                                 G1EvacInfo* evacuation_info,
                                                                                 G1EvacFailureRegions* evac_failure_regions) :
  G1BatchedTask("Post Evacuate Cleanup 2", G1CollectedHeap::heap()->phase_times())
{
#if COMPILER2_OR_JVMCI
  add_serial_task(new UpdateDerivedPointersTask());
#endif
  if (G1CollectedHeap::heap()->has_humongous_reclaim_candidates()) {
    add_serial_task(new EagerlyReclaimHumongousObjectsTask());
  }
  add_serial_task(new ResetPartialArrayStateManagerTask());

  if (evac_failure_regions->has_regions_evac_failed()) {
    add_parallel_task(new ProcessEvacuationFailedRegionsTask(evac_failure_regions));
  }
  add_parallel_task(new RedirtyLoggedCardsTask(evac_failure_regions,
                                               per_thread_states->rdc_buffers(),
                                               per_thread_states->num_workers()));

  if (UseTLAB && ResizeTLAB) {
    add_parallel_task(new ResizeTLABsTask());
  }
  add_parallel_task(new FreeCollectionSetTask(evacuation_info,
                                              per_thread_states->surviving_young_words(),
                                              evac_failure_regions));
}
