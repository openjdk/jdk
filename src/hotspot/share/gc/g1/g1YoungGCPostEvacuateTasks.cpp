/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

#include "compiler/oopMap.hpp"
#include "gc/g1/g1CardSetMemory.hpp"
#include "gc/g1/g1CardTableEntryClosure.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1CollectionSetCandidates.hpp"
#include "gc/g1/g1ConcurrentMark.inline.hpp"
#include "gc/g1/g1EvacStats.inline.hpp"
#include "gc/g1/g1EvacInfo.hpp"
#include "gc/g1/g1ParScanThreadState.hpp"
#include "gc/g1/g1RemSet.hpp"
#include "gc/g1/g1YoungGCPostEvacuateTasks.hpp"
#include "gc/shared/preservedMarks.inline.hpp"
#include "jfr/jfrEvents.hpp"
#include "utilities/ticks.hpp"

class G1PostEvacuateCollectionSetCleanupTask1::MergePssTask : public G1AbstractSubTask {
  G1ParScanThreadStateSet* _per_thread_states;

public:
  MergePssTask(G1ParScanThreadStateSet* per_thread_states) :
    G1AbstractSubTask(G1GCPhaseTimes::MergePSS),
    _per_thread_states(per_thread_states) { }

  double worker_cost() const override { return 1.0; }

  void do_work(uint worker_id) override { _per_thread_states->flush(); }
};

class G1PostEvacuateCollectionSetCleanupTask1::RecalculateUsedTask : public G1AbstractSubTask {
  bool _evacuation_failed;

public:
  RecalculateUsedTask(bool evacuation_failed) : G1AbstractSubTask(G1GCPhaseTimes::RecalculateUsed), _evacuation_failed(evacuation_failed) { }

  double worker_cost() const override {
    // If there is no evacuation failure, the work to perform is minimal.
    return _evacuation_failed ? 1.0 : AlmostNoWork;
  }

  void do_work(uint worker_id) override { G1CollectedHeap::heap()->update_used_after_gc(_evacuation_failed); }
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

    class G1SampleCollectionSetCandidatesClosure : public HeapRegionClosure {
    public:
      G1CardSetMemoryStats _total;

      bool do_heap_region(HeapRegion* r) override {
        _total.add(r->rem_set()->card_set_memory_stats());
        return false;
      }
    } cl;

    G1CollectedHeap* g1h = G1CollectedHeap::heap();

    g1h->collection_set()->candidates()->iterate(&cl);
    g1h->set_collection_set_candidates_stats(cl._total);
  }
};

class G1PostEvacuateCollectionSetCleanupTask1::RemoveSelfForwardPtrsTask : public G1AbstractSubTask {
  G1ParRemoveSelfForwardPtrsTask _task;
  G1EvacFailureRegions* _evac_failure_regions;

public:
  RemoveSelfForwardPtrsTask(G1EvacFailureRegions* evac_failure_regions) :
    G1AbstractSubTask(G1GCPhaseTimes::RemoveSelfForwardingPtr),
    _task(evac_failure_regions),
    _evac_failure_regions(evac_failure_regions) { }

  double worker_cost() const override {
    assert(_evac_failure_regions->evacuation_failed(), "Should not call this if not executed");
    return _evac_failure_regions->num_regions_failed_evacuation();
  }

  void do_work(uint worker_id) override {
    _task.work(worker_id);
  }
};

G1PostEvacuateCollectionSetCleanupTask1::G1PostEvacuateCollectionSetCleanupTask1(G1ParScanThreadStateSet* per_thread_states,
                                                                                 G1EvacFailureRegions* evac_failure_regions) :
  G1BatchedTask("Post Evacuate Cleanup 1", G1CollectedHeap::heap()->phase_times())
{
  bool evacuation_failed = evac_failure_regions->evacuation_failed();

  add_serial_task(new MergePssTask(per_thread_states));
  add_serial_task(new RecalculateUsedTask(evacuation_failed));
  if (SampleCollectionSetCandidatesTask::should_execute()) {
    add_serial_task(new SampleCollectionSetCandidatesTask());
  }
  if (evacuation_failed) {
    add_parallel_task(new RemoveSelfForwardPtrsTask(evac_failure_regions));
  }
  add_parallel_task(G1CollectedHeap::heap()->rem_set()->create_cleanup_after_scan_heap_roots_task());
}

class G1FreeHumongousRegionClosure : public HeapRegionClosure {
  uint _humongous_objects_reclaimed;
  uint _humongous_regions_reclaimed;
  size_t _freed_bytes;

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
  // considerable effort for cleaning up the the remembered sets. This is
  // required because stale remembered sets might reference locations that
  // are currently allocated into.
  bool is_reclaimable(uint region_idx) const {
    return G1CollectedHeap::heap()->is_humongous_reclaim_candidate(region_idx);
  }

public:
  G1FreeHumongousRegionClosure() :
    _humongous_objects_reclaimed(0),
    _humongous_regions_reclaimed(0),
    _freed_bytes(0) {
  }

  virtual bool do_heap_region(HeapRegion* r) {
    if (!r->is_starts_humongous()) {
      return false;
    }

    uint region_idx = r->hrm_index();
    if (!is_reclaimable(region_idx)) {
      return false;
    }

    oop obj = cast_to_oop(r->bottom());
    guarantee(obj->is_typeArray(),
              "Only eagerly reclaiming type arrays is supported, but the object "
              PTR_FORMAT " is not.", p2i(r->bottom()));

    log_debug(gc, humongous)("Reclaimed humongous region %u (object size " SIZE_FORMAT " @ " PTR_FORMAT ")",
                             region_idx,
                             obj->size() * HeapWordSize,
                             p2i(r->bottom())
                            );

    G1CollectedHeap* g1h = G1CollectedHeap::heap();
    G1ConcurrentMark* const cm = g1h->concurrent_mark();
    cm->humongous_object_eagerly_reclaimed(r);
    assert(!cm->is_marked_in_prev_bitmap(obj) && !cm->is_marked_in_next_bitmap(obj),
           "Eagerly reclaimed humongous region %u should not be marked at all but is in prev %s next %s",
           region_idx,
           BOOL_TO_STR(cm->is_marked_in_prev_bitmap(obj)),
           BOOL_TO_STR(cm->is_marked_in_next_bitmap(obj)));
    _humongous_objects_reclaimed++;
    do {
      HeapRegion* next = g1h->next_region_in_humongous(r);
      _freed_bytes += r->used();
      r->set_containing_set(nullptr);
      _humongous_regions_reclaimed++;
      g1h->free_humongous_region(r, nullptr);
      g1h->hr_printer()->cleanup(r);
      r = next;
    } while (r != nullptr);

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

class G1PostEvacuateCollectionSetCleanupTask2::ResetHotCardCacheTask : public G1AbstractSubTask {
public:
  ResetHotCardCacheTask() : G1AbstractSubTask(G1GCPhaseTimes::ResetHotCardCache) { }

  double worker_cost() const override { return 0.5; }
  void do_work(uint worker_id) override { G1CollectedHeap::heap()->reset_hot_card_cache(); }
};

class G1PostEvacuateCollectionSetCleanupTask2::PurgeCodeRootsTask : public G1AbstractSubTask {
public:
  PurgeCodeRootsTask() : G1AbstractSubTask(G1GCPhaseTimes::PurgeCodeRoots) { }

  double worker_cost() const override { return 1.0; }
  void do_work(uint worker_id) override { G1CollectedHeap::heap()->purge_code_root_memory(); }
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

    g1h->remove_from_old_gen_sets(0, 0, _humongous_regions_reclaimed);
    g1h->decrement_summary_bytes(_bytes_freed);
  }

  static bool should_execute() {   return G1CollectedHeap::heap()->should_do_eager_reclaim(); }

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

class G1PostEvacuateCollectionSetCleanupTask2::RestorePreservedMarksTask : public G1AbstractSubTask {
  PreservedMarksSet* _preserved_marks;
  WorkerTask* _task;

public:
  RestorePreservedMarksTask(PreservedMarksSet* preserved_marks) :
    G1AbstractSubTask(G1GCPhaseTimes::RestorePreservedMarks),
    _preserved_marks(preserved_marks),
    _task(preserved_marks->create_task()) { }

  virtual ~RestorePreservedMarksTask() {
    delete _task;
  }

  double worker_cost() const override {
    return _preserved_marks->num();
  }

  void do_work(uint worker_id) override { _task->work(worker_id); }
};

class RedirtyLoggedCardTableEntryClosure : public G1CardTableEntryClosure {
 private:
  size_t _num_dirtied;
  G1CollectedHeap* _g1h;
  G1CardTable* _g1_ct;
  G1EvacFailureRegions* _evac_failure_regions;

  HeapRegion* region_for_card(CardValue* card_ptr) const {
    return _g1h->heap_region_containing(_g1_ct->addr_for(card_ptr));
  }

  bool will_become_free(HeapRegion* hr) const {
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

  void do_card_ptr(CardValue* card_ptr, uint worker_id) {
    HeapRegion* hr = region_for_card(card_ptr);

    // Should only dirty cards in regions that won't be freed.
    if (!will_become_free(hr)) {
      *card_ptr = G1CardTable::dirty_card_val();
      _num_dirtied++;
    }
  }

  size_t num_dirtied()   const { return _num_dirtied; }
};

class G1PostEvacuateCollectionSetCleanupTask2::RedirtyLoggedCardsTask : public G1AbstractSubTask {
  G1RedirtyCardsQueueSet* _rdcqs;
  BufferNode* volatile _nodes;
  G1EvacFailureRegions* _evac_failure_regions;

public:
  RedirtyLoggedCardsTask(G1RedirtyCardsQueueSet* rdcqs, G1EvacFailureRegions* evac_failure_regions) :
    G1AbstractSubTask(G1GCPhaseTimes::RedirtyCards),
    _rdcqs(rdcqs),
    _nodes(rdcqs->all_completed_buffers()),
    _evac_failure_regions(evac_failure_regions) { }

  virtual ~RedirtyLoggedCardsTask() {
    G1DirtyCardQueueSet& dcq = G1BarrierSet::dirty_card_queue_set();
    dcq.merge_bufferlists(_rdcqs);
    _rdcqs->verify_empty();
  }

  double worker_cost() const override {
    // Needs more investigation.
    return G1CollectedHeap::heap()->workers()->active_workers();
  }

  void do_work(uint worker_id) override {
    RedirtyLoggedCardTableEntryClosure cl(G1CollectedHeap::heap(), _evac_failure_regions);
    const size_t buffer_size = _rdcqs->buffer_size();
    BufferNode* next = Atomic::load(&_nodes);
    while (next != nullptr) {
      BufferNode* node = next;
      next = Atomic::cmpxchg(&_nodes, node, node->next());
      if (next == node) {
        cl.apply_to_buffer(node, buffer_size, worker_id);
        next = node->next();
      }
    }
    record_work_item(worker_id, 0, cl.num_dirtied());
  }
};

// Helper class to keep statistics for the collection set freeing
class FreeCSetStats {
  size_t _before_used_bytes;   // Usage in regions successfully evacutate
  size_t _after_used_bytes;    // Usage in regions failing evacuation
  size_t _bytes_allocated_in_old_since_last_gc; // Size of young regions turned into old
  size_t _failure_used_words;  // Live size in failed regions
  size_t _failure_waste_words; // Wasted size in failed regions
  size_t _rs_length;           // Remembered set size
  uint _regions_freed;         // Number of regions freed

public:
  FreeCSetStats() :
      _before_used_bytes(0),
      _after_used_bytes(0),
      _bytes_allocated_in_old_since_last_gc(0),
      _failure_used_words(0),
      _failure_waste_words(0),
      _rs_length(0),
      _regions_freed(0) { }

  void merge_stats(FreeCSetStats* other) {
    assert(other != nullptr, "invariant");
    _before_used_bytes += other->_before_used_bytes;
    _after_used_bytes += other->_after_used_bytes;
    _bytes_allocated_in_old_since_last_gc += other->_bytes_allocated_in_old_since_last_gc;
    _failure_used_words += other->_failure_used_words;
    _failure_waste_words += other->_failure_waste_words;
    _rs_length += other->_rs_length;
    _regions_freed += other->_regions_freed;
  }

  void report(G1CollectedHeap* g1h, G1EvacInfo* evacuation_info) {
    evacuation_info->set_regions_freed(_regions_freed);
    evacuation_info->increment_collectionset_used_after(_after_used_bytes);

    g1h->decrement_summary_bytes(_before_used_bytes);
    g1h->alloc_buffer_stats(G1HeapRegionAttr::Old)->add_failure_used_and_waste(_failure_used_words, _failure_waste_words);

    G1Policy *policy = g1h->policy();
    policy->old_gen_alloc_tracker()->add_allocated_bytes_since_last_gc(_bytes_allocated_in_old_since_last_gc);
    policy->record_rs_length(_rs_length);
    policy->cset_regions_freed();
  }

  void account_failed_region(HeapRegion* r) {
    size_t used_words = r->marked_bytes() / HeapWordSize;
    _failure_used_words += used_words;
    _failure_waste_words += HeapRegion::GrainWords - used_words;
    _after_used_bytes += r->used();

    // When moving a young gen region to old gen, we "allocate" that whole
    // region there. This is in addition to any already evacuated objects.
    // Notify the policy about that. Old gen regions do not cause an
    // additional allocation: both the objects still in the region and the
    // ones already moved are accounted for elsewhere.
    if (r->is_young()) {
      _bytes_allocated_in_old_since_last_gc += HeapRegion::GrainBytes;
    }
  }

  void account_evacuated_region(HeapRegion* r) {
    size_t used = r->used();
    assert(used > 0, "region %u %s zero used", r->hrm_index(), r->get_short_type_str());
    _before_used_bytes += used;
    _regions_freed += 1;
  }

  void account_rs_length(HeapRegion* r) {
    _rs_length += r->rem_set()->occupied();
  }
};

// Closure applied to all regions in the collection set.
class FreeCSetClosure : public HeapRegionClosure {
  // Helper to send JFR events for regions.
  class JFREventForRegion {
    EventGCPhaseParallel _event;

  public:
    JFREventForRegion(HeapRegion* region, uint worker_id) : _event() {
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

  void assert_tracks_surviving_words(HeapRegion* r) {
    assert(r->young_index_in_cset() != 0 &&
           (uint)r->young_index_in_cset() <= _g1h->collection_set()->young_region_length(),
           "Young index %u is wrong for region %u of type %s with %u young regions",
           r->young_index_in_cset(), r->hrm_index(), r->get_type_str(), _g1h->collection_set()->young_region_length());
  }

  void handle_evacuated_region(HeapRegion* r) {
    assert(!r->is_empty(), "Region %u is an empty region in the collection set.", r->hrm_index());
    stats()->account_evacuated_region(r);

    // Free the region and and its remembered set.
    _g1h->free_region(r, nullptr);
    _g1h->hr_printer()->cleanup(r);
  }

  void handle_failed_region(HeapRegion* r) {
    // Do some allocation statistics accounting. Regions that failed evacuation
    // are always made old, so there is no need to update anything in the young
    // gen statistics, but we need to update old gen statistics.
    stats()->account_failed_region(r);

    // Update the region state due to the failed evacuation.
    r->handle_evacuation_failure();

    // Add region to old set, need to hold lock.
    MutexLocker x(OldSets_lock, Mutex::_no_safepoint_check_flag);
    _g1h->old_set_add(r);
  }

  Tickspan& timer_for_region(HeapRegion* r) {
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
      HeapRegionClosure(),
      _g1h(G1CollectedHeap::heap()),
      _surviving_young_words(surviving_young_words),
      _worker_id(worker_id),
      _young_time(),
      _non_young_time(),
      _stats(stats),
      _evac_failure_regions(evac_failure_regions) { }

  virtual bool do_heap_region(HeapRegion* r) {
    assert(r->in_collection_set(), "Invariant: %u missing from CSet", r->hrm_index());
    JFREventForRegion event(r, _worker_id);
    TimerForRegion timer(timer_for_region(r));

    stats()->account_rs_length(r);

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
};

class G1PostEvacuateCollectionSetCleanupTask2::FreeCollectionSetTask : public G1AbstractSubTask {
  G1CollectedHeap*  _g1h;
  G1EvacInfo* _evacuation_info;
  FreeCSetStats*    _worker_stats;
  HeapRegionClaimer _claimer;
  const size_t*     _surviving_young_words;
  uint              _active_workers;
  G1EvacFailureRegions* _evac_failure_regions;

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
    _evac_failure_regions(evac_failure_regions) {

    _g1h->clear_eden();
  }

  virtual ~FreeCollectionSetTask() {
    Ticks serial_time = Ticks::now();
    report_statistics();
    for (uint worker = 0; worker < _active_workers; worker++) {
      _worker_stats[worker].~FreeCSetStats();
    }
    FREE_C_HEAP_ARRAY(FreeCSetStats, _worker_stats);
    _g1h->phase_times()->record_serial_free_cset_time_ms((Ticks::now() - serial_time).seconds() * 1000.0);
    _g1h->clear_collection_set();
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
  }
};

G1PostEvacuateCollectionSetCleanupTask2::G1PostEvacuateCollectionSetCleanupTask2(G1ParScanThreadStateSet* per_thread_states,
                                                                                 G1EvacInfo* evacuation_info,
                                                                                 G1EvacFailureRegions* evac_failure_regions) :
  G1BatchedTask("Post Evacuate Cleanup 2", G1CollectedHeap::heap()->phase_times())
{
  add_serial_task(new ResetHotCardCacheTask());
  add_serial_task(new PurgeCodeRootsTask());
#if COMPILER2_OR_JVMCI
  add_serial_task(new UpdateDerivedPointersTask());
#endif
  if (EagerlyReclaimHumongousObjectsTask::should_execute()) {
    add_serial_task(new EagerlyReclaimHumongousObjectsTask());
  }

  if (evac_failure_regions->evacuation_failed()) {
    add_parallel_task(new RestorePreservedMarksTask(per_thread_states->preserved_marks_set()));
  }
  add_parallel_task(new RedirtyLoggedCardsTask(per_thread_states->rdcqs(), evac_failure_regions));
  add_parallel_task(new FreeCollectionSetTask(evacuation_info,
                                                   per_thread_states->surviving_young_words(),
                                                   evac_failure_regions));
}
