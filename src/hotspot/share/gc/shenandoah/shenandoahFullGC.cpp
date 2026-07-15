/*
 * Copyright (c) 2014, 2021, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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


#include "classfile/systemDictionary.hpp"
#include "compiler/oopMap.hpp"
#include "gc/shared/classUnloadingContext.hpp"
#include "gc/shared/continuationGCSupport.hpp"
#include "gc/shared/fullGCForwarding.inline.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/preservedMarks.inline.hpp"
#include "gc/shared/tlab_globals.hpp"
#include "gc/shared/workerThread.hpp"
#include "gc/shenandoah/heuristics/shenandoahHeuristics.hpp"
#include "gc/shenandoah/shenandoahClosures.inline.hpp"
#include "gc/shenandoah/shenandoahCollectionSet.hpp"
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahConcurrentGC.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahFullGC.hpp"
#include "gc/shenandoah/shenandoahGenerationalFullGC.hpp"
#include "gc/shenandoah/shenandoahGlobalGeneration.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegionClosures.hpp"
#include "gc/shenandoah/shenandoahHeapRegionSet.hpp"
#include "gc/shenandoah/shenandoahMark.inline.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.inline.hpp"
#include "gc/shenandoah/shenandoahMetrics.hpp"
#include "gc/shenandoah/shenandoahMonitoringSupport.hpp"
#include "gc/shenandoah/shenandoahParallelCleaning.inline.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shenandoah/shenandoahReferenceProcessor.hpp"
#include "gc/shenandoah/shenandoahRootProcessor.inline.hpp"
#include "gc/shenandoah/shenandoahSTWMark.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "gc/shenandoah/shenandoahVerifier.hpp"
#include "gc/shenandoah/shenandoahVMOperations.hpp"
#include "gc/shenandoah/shenandoahWorkerPolicy.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"
#include "memory/metaspaceUtils.hpp"
#include "memory/universe.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/copy.hpp"
#include "utilities/events.hpp"
#include "utilities/growableArray.hpp"

ShenandoahFullGC::ShenandoahFullGC() :
  ShenandoahGC(ShenandoahHeap::heap()->global_generation()),
  _heap(ShenandoahHeap::heap()),
  _gc_timer(_heap->gc_timer()),
  _preserved_marks(new PreservedMarksSet(true)) {}

ShenandoahFullGC::~ShenandoahFullGC() {
  delete _preserved_marks;
}

bool ShenandoahFullGC::collect(GCCause::Cause cause) {
  vmop_entry_full(cause);
  // Always success
  return true;
}

void ShenandoahFullGC::vmop_entry_full(GCCause::Cause cause) {
  TraceCollectorStats tcs(_heap->monitoring_support()->full_stw_collection_counters());
  ShenandoahTimingsTracker timing(ShenandoahPhaseTimings::full_gc_gross);

  _heap->try_inject_alloc_failure();
  VM_ShenandoahFullGC op(cause, this);
  VMThread::execute(&op);
}

void ShenandoahFullGC::entry_full(GCCause::Cause cause) {
  static const char* msg = "Pause Full";
  ShenandoahPausePhase gc_phase(msg, ShenandoahPhaseTimings::full_gc, true /* log_heap_usage */);
  EventMark em("%s", msg);

  ShenandoahWorkerScope scope(ShenandoahHeap::heap()->workers(),
                              ShenandoahWorkerPolicy::calc_workers_for_fullgc(),
                              "full gc");

  op_full(cause);
}

void ShenandoahFullGC::op_full(GCCause::Cause cause) {
  ShenandoahMetricsSnapshot metrics(_heap->free_set());

  // Perform full GC
  do_it();

  if (_heap->mode()->is_generational()) {
    ShenandoahGenerationalFullGC::handle_completion(_heap);
  }

  if (metrics.is_good_progress()) {
    _heap->notify_gc_progress();
  } else {
    // Nothing to do. Tell the allocation path that we have failed to make
    // progress, and it can finally fail.
    _heap->notify_gc_no_progress();
  }

  // Regardless if progress was made, we record that we completed a full GC.
  if (_heap->mode()->is_generational()) {
    // In generational mode, the young heuristics are responsible for this failure
    _heap->young_generation()->heuristics()->record_full_gc(cause);
  } else {
    _generation->heuristics()->record_full_gc(cause);
  }

  _heap->shenandoah_policy()->record_success_full();

  {
    ShenandoahTimingsTracker timing(ShenandoahPhaseTimings::full_gc_propagate_gc_state);
    _heap->propagate_gc_state_to_all_threads();
  }
}

void ShenandoahFullGC::do_it() {
  _heap->release_injected_pins();
  // A full GC must be entered directly, though it is possible for concurrent marking to be
  // in progress.

  if (_heap->mode()->is_generational()) {
    ShenandoahGenerationalFullGC::prepare();
  }

#ifdef ASSERT
  assert(_heap->is_idle(), "Full GC should not be running from incomplete cycle");
  assert(!_heap->has_forwarded_objects(), "Concurrent cycle should have finished update references");
  assert(!_heap->is_concurrent_mark_in_progress(), "Cannot be marking now");
  assert(!_heap->has_self_forwarded_objects(), "Self forwarded objects should be cleared by concurrent cycle.");
  for (size_t i = 0, n = _heap->num_regions(); i < n; ++i) {
    ShenandoahHeapRegion* region = _heap->get_region(i);
    assert(!region->has_self_forwards(), "Region %zu should not have self forwarded objects here.", i);
  }
#endif

  if (ShenandoahVerify) {
    _heap->verifier()->verify_before_fullgc(_generation);
  }

  if (VerifyBeforeGC) {
    Universe::verify();
  }

  _heap->set_full_gc_in_progress(true);

  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "must be at a safepoint");
  assert(Thread::current()->is_VM_thread(), "Do full GC only while world is stopped");

  {
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::full_gc_heapdump_pre);
    _heap->pre_full_gc_dump(_gc_timer);
  }

  {
    ShenandoahGCPhase prepare_phase(ShenandoahPhaseTimings::full_gc_prepare);

    // Sync pinned region status from the CP marks
    _heap->sync_pinned_region_status();

    if (_heap->mode()->is_generational()) {
      ShenandoahGenerationalFullGC::restore_top_before_promote(_heap);
    }

    // The rest of prologue:
    _preserved_marks->init(_heap->workers()->active_workers());
  }

  if (UseTLAB) {
    // Note: PLABs are also retired with GCLABs in generational mode.
    _heap->gclabs_retire(ResizeTLAB);
    _heap->tlabs_retire(ResizeTLAB);
  }

  OrderAccess::fence();

  phase1_mark_heap();

  _heap->set_full_gc_move_in_progress(true);

  // Setup workers for the rest
  OrderAccess::fence();

  // Initialize worker slices
  ShenandoahHeapRegionSet** worker_slices = NEW_C_HEAP_ARRAY(ShenandoahHeapRegionSet*, _heap->max_workers(), mtGC);
  for (uint i = 0; i < _heap->max_workers(); i++) {
    worker_slices[i] = new ShenandoahHeapRegionSet();
  }

  {
    // The rest of code performs region moves, where region status is undefined
    // until all phases run together.
    ShenandoahHeapLocker lock(_heap->lock());

    phase2_calculate_target_addresses(worker_slices);

    OrderAccess::fence();

    phase3_update_references();

    phase4_compact_objects(worker_slices);

    phase5_epilog();
  }
  _heap->start_idle_span();

  // Resize metaspace
  MetaspaceGC::compute_new_size();

  // Free worker slices
  for (uint i = 0; i < _heap->max_workers(); i++) {
    delete worker_slices[i];
  }
  FREE_C_HEAP_ARRAY(worker_slices);

  _heap->set_full_gc_move_in_progress(false);
  _heap->set_full_gc_in_progress(false);

  DEBUG_ONLY(_heap->assert_no_self_forwards());

  if (ShenandoahVerify) {
    _heap->verifier()->verify_after_fullgc(_generation);
  }

  if (VerifyAfterGC) {
    Universe::verify();
  }

  {
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::full_gc_heapdump_post);
    _heap->post_full_gc_dump(_gc_timer);
  }
}

void ShenandoahFullGC::phase1_mark_heap() {
  GCTraceTime(Info, gc, phases) time("Phase 1: Mark live objects", _gc_timer);
  ShenandoahGCPhase mark_phase(ShenandoahPhaseTimings::full_gc_mark);

  _generation->reset_mark_bitmap<true, true>();
  assert(_heap->marking_context()->is_bitmap_clear(), "sanity");
  assert(!_generation->is_mark_complete(), "sanity");

  _heap->set_unload_classes(_generation->heuristics()->can_unload_classes());

  ShenandoahReferenceProcessor* rp = _generation->ref_processor();
  // enable ("weak") refs discovery
  rp->set_soft_reference_policy(true); // forcefully purge all soft references

  ShenandoahSTWMark mark(_generation);
  mark.mark();
  parallel_cleaning(_generation);

  if (_heap->mode()->is_generational()) {
    ShenandoahGenerationalFullGC::log_live_in_old(_heap);
  }
}

void ShenandoahFullGC::parallel_cleaning(ShenandoahGeneration* generation) const {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at a safepoint");
  assert(ShenandoahHeap::heap()->is_stw_gc_in_progress(), "Only for Full GC");
  ShenandoahGCPhase phase(ShenandoahPhaseTimings::full_gc_purge);
  stw_weak_refs(generation);
  stw_process_weak_roots();
  stw_unload_classes();
}

void ShenandoahFullGC::stw_weak_refs(ShenandoahGeneration* generation) {
  // Weak refs processing
  ShenandoahPhaseTimings::Phase phase = ShenandoahPhaseTimings::full_gc_weakrefs;
  ShenandoahTimingsTracker t(phase);
  ShenandoahGCWorkerPhase worker_phase(phase);
  generation->ref_processor()->process_references(phase, ShenandoahHeap::heap()->workers(), false /* concurrent */);
}

// Weak roots are either pre-evacuated (final mark) or updated (final update refs),
// so they should not have forwarded oops.
// However, we do need to "null" dead oops in the roots, if it cannot be done
// in concurrent cycles.
void ShenandoahFullGC::stw_process_weak_roots() const {
  uint num_workers = _heap->workers()->active_workers();
  ShenandoahPhaseTimings::Phase timing_phase = ShenandoahPhaseTimings::full_gc_purge_weak_par;
  ShenandoahGCPhase phase(timing_phase);
  ShenandoahGCWorkerPhase worker_phase(timing_phase);
  // Cleanup weak roots
  ShenandoahIsAliveClosure is_alive;
#ifdef ASSERT
  ShenandoahAssertNotForwardedClosure verify_cl;
  ShenandoahParallelWeakRootsCleaningTask cleaning_task(timing_phase, &is_alive, &verify_cl, num_workers);
#else
  ShenandoahParallelWeakRootsCleaningTask cleaning_task(timing_phase, &is_alive, &do_nothing_cl, num_workers);
#endif
  _heap->workers()->run_task(&cleaning_task);
}

void ShenandoahFullGC::stw_unload_classes() const {
  if (!_heap->unload_classes()) return;
  ClassUnloadingContext ctx(_heap->workers()->active_workers(),
                            true /* unregister_nmethods_during_purge */,
                            false /* lock_nmethod_free_separately */);

  // Unload classes and purge SystemDictionary.
  {
    ShenandoahPhaseTimings::Phase phase = ShenandoahPhaseTimings::full_gc_purge_class_unload;
    ShenandoahIsAliveSelector is_alive;
    {
      CodeCache::UnlinkingScope scope(is_alive.is_alive_closure());
      ShenandoahGCPhase gc_phase(phase);
      ShenandoahGCWorkerPhase worker_phase(phase);
      bool unloading_occurred = SystemDictionary::do_unloading(_gc_timer);

      ShenandoahClassUnloadingTask unlink_task(phase, unloading_occurred);
      _heap->workers()->run_task(&unlink_task);
    }
    // Release unloaded nmethods's memory.
    ClassUnloadingContext::context()->purge_and_free_nmethods();
  }

  {
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::full_gc_purge_cldg);
    ClassLoaderDataGraph::purge(true /* at_safepoint */);
  }
  // Resize and verify metaspace
  MetaspaceGC::compute_new_size();

  if (_heap->mode()->is_generational()) {
    _heap->old_generation()->set_parsable(false);
  }

  DEBUG_ONLY(MetaspaceUtils::verify();)
}

class ShenandoahPrepareForCompactionObjectClosure : public ObjectClosure {
private:
  PreservedMarks*          const _preserved_marks;
  ShenandoahHeap*          const _heap;
  GrowableArray<ShenandoahHeapRegion*>& _empty_regions;
  int _empty_regions_pos;
  ShenandoahHeapRegion*          _to_region;
  ShenandoahHeapRegion*          _from_region;
  HeapWord* _compact_point;

public:
  ShenandoahPrepareForCompactionObjectClosure(PreservedMarks* preserved_marks,
                                              GrowableArray<ShenandoahHeapRegion*>& empty_regions,
                                              ShenandoahHeapRegion* to_region) :
    _preserved_marks(preserved_marks),
    _heap(ShenandoahHeap::heap()),
    _empty_regions(empty_regions),
    _empty_regions_pos(0),
    _to_region(to_region),
    _from_region(nullptr),
    _compact_point(to_region->bottom()) {}

  void set_from_region(ShenandoahHeapRegion* from_region) {
    _from_region = from_region;
  }

  void finish() {
    assert(_to_region != nullptr, "should not happen");
    _to_region->set_new_top(_compact_point);
  }

  bool is_compact_same_region() {
    return _from_region == _to_region;
  }

  int empty_regions_pos() {
    return _empty_regions_pos;
  }

  void do_object(oop p) override {
    shenandoah_assert_mark_complete(cast_from_oop<HeapWord*>(p));
    assert(_from_region != nullptr, "must set before work");
    assert(_heap->global_generation()->is_mark_complete(), "marking must be finished");
    assert(_heap->marking_context()->is_marked(p), "must be marked");
    assert(!_heap->marking_context()->allocated_after_mark_start(p), "must be truly marked");

    size_t obj_size = p->size();
    if (_compact_point + obj_size > _to_region->end()) {
      finish();

      // Object doesn't fit. Pick next empty region and start compacting there.
      ShenandoahHeapRegion* new_to_region;
      if (_empty_regions_pos < _empty_regions.length()) {
        new_to_region = _empty_regions.at(_empty_regions_pos);
        _empty_regions_pos++;
      } else {
        // Out of empty region? Compact within the same region.
        new_to_region = _from_region;
      }

      assert(new_to_region != _to_region, "must not reuse same to-region");
      assert(new_to_region != nullptr, "must not be null");
      _to_region = new_to_region;
      _compact_point = _to_region->bottom();
    }

    // Object fits into current region, record new location, if object does not move:
    assert(_compact_point + obj_size <= _to_region->end(), "must fit");
    shenandoah_assert_not_forwarded(nullptr, p);
    if (_compact_point != cast_from_oop<HeapWord*>(p)) {
      _preserved_marks->push_if_necessary(p, p->mark());
      FullGCForwarding::forward_to(p, cast_to_oop(_compact_point));
    }
    _compact_point += obj_size;
  }
};

class ShenandoahPrepareForCompactionTask : public WorkerTask {
private:
  PreservedMarksSet*        const _preserved_marks;
  ShenandoahHeap*           const _heap;
  ShenandoahHeapRegionSet** const _worker_slices;

public:
  ShenandoahPrepareForCompactionTask(PreservedMarksSet *preserved_marks, ShenandoahHeapRegionSet **worker_slices) :
    WorkerTask("Shenandoah Prepare For Compaction"),
    _preserved_marks(preserved_marks),
    _heap(ShenandoahHeap::heap()), _worker_slices(worker_slices) {
  }

  static bool is_candidate_region(ShenandoahHeapRegion* r) {
    // Empty region: get it into the slice to defragment the slice itself.
    // We could have skipped this without violating correctness, but we really
    // want to compact all live regions to the start of the heap, which sometimes
    // means moving them into the fully empty regions.
    if (r->is_empty()) return true;

    // Can move the region, and this is not the humongous region. Humongous
    // moves are special cased here, because their moves are handled separately.
    return r->is_stw_move_allowed() && !r->is_humongous();
  }

  void work(uint worker_id) override;
private:
  template<typename ClosureType>
  void prepare_for_compaction(ClosureType& cl,
                              GrowableArray<ShenandoahHeapRegion*>& empty_regions,
                              ShenandoahHeapRegionSetIterator& it,
                              ShenandoahHeapRegion* from_region);
};

void ShenandoahPrepareForCompactionTask::work(uint worker_id) {
  ShenandoahParallelWorkerSession worker_session(worker_id);
  ShenandoahHeapRegionSet* slice = _worker_slices[worker_id];
  ShenandoahHeapRegionSetIterator it(slice);
  ShenandoahHeapRegion* from_region = it.next();
  // No work?
  if (from_region == nullptr) {
    return;
  }

  // Sliding compaction. Walk all regions in the slice, and compact them.
  // Remember empty regions and reuse them as needed.
  ResourceMark rm;

  GrowableArray<ShenandoahHeapRegion*> empty_regions((int)_heap->num_regions());

  if (_heap->mode()->is_generational()) {
    ShenandoahPrepareForGenerationalCompactionObjectClosure cl(_preserved_marks->get(worker_id),
                                                               empty_regions, from_region, worker_id);
    prepare_for_compaction(cl, empty_regions, it, from_region);
  } else {
    ShenandoahPrepareForCompactionObjectClosure cl(_preserved_marks->get(worker_id), empty_regions, from_region);
    prepare_for_compaction(cl, empty_regions, it, from_region);
  }
}

template<typename ClosureType>
void ShenandoahPrepareForCompactionTask::prepare_for_compaction(ClosureType& cl,
                                                                GrowableArray<ShenandoahHeapRegion*>& empty_regions,
                                                                ShenandoahHeapRegionSetIterator& it,
                                                                ShenandoahHeapRegion* from_region) {
  while (from_region != nullptr) {
    assert(is_candidate_region(from_region), "Sanity");
    cl.set_from_region(from_region);
    if (from_region->has_live()) {
      _heap->marked_object_iterate(from_region, &cl);
    }

    // Compacted the region to somewhere else? From-region is empty then.
    if (!cl.is_compact_same_region()) {
      empty_regions.append(from_region);
    }
    from_region = it.next();
  }
  cl.finish();

  // Mark all remaining regions as empty
  for (int pos = cl.empty_regions_pos(); pos < empty_regions.length(); ++pos) {
    ShenandoahHeapRegion* r = empty_regions.at(pos);
    r->set_new_top(r->bottom());
  }
}

void ShenandoahFullGC::calculate_target_humongous_objects() {
  // Compute the new addresses for humongous objects. We need to do this after addresses
  // for regular objects are calculated, and we know what regions in heap suffix are
  // available for humongous moves.
  //
  // Scan the heap backwards, because we are compacting humongous regions towards the end.
  // Maintain the contiguous compaction window in [to_begin; to_end), so that we can slide
  // humongous start there.
  //
  // The complication is potential non-movable regions during the scan. If such region is
  // detected, then sliding restarts towards that non-movable region.

  size_t to_begin = _heap->num_regions();
  size_t to_end = _heap->num_regions();

  log_debug(gc)("Full GC calculating target humongous objects from end %zu", to_end);
  for (size_t c = _heap->num_regions(); c > 0; c--) {
    ShenandoahHeapRegion *r = _heap->get_region(c - 1);
    if (r->is_humongous_continuation() || (r->new_top() == r->bottom())) {
      // To-region candidate: record this, and continue scan
      to_begin = r->index();
      continue;
    }

    if (r->is_humongous_start() && r->is_stw_move_allowed()) {
      // From-region candidate: movable humongous region
      oop old_obj = cast_to_oop(r->bottom());
      size_t words_size = old_obj->size();
      size_t num_regions = ShenandoahHeapRegion::required_regions(words_size * HeapWordSize);

      size_t start = to_end - num_regions;

      if (start >= to_begin && start != r->index()) {
        // Fits into current window, and the move is non-trivial. Record the move then, and continue scan.
        _preserved_marks->get(0)->push_if_necessary(old_obj, old_obj->mark());
        FullGCForwarding::forward_to(old_obj, cast_to_oop(_heap->get_region(start)->bottom()));
        to_end = start;
        continue;
      }
    }

    // Failed to fit. Scan starting from current region.
    to_begin = r->index();
    to_end = r->index();
  }
}

class ShenandoahEnsureHeapActiveClosure: public ShenandoahHeapRegionClosure {
public:
  void heap_region_do(ShenandoahHeapRegion* r) override {
    if (r->is_trash()) {
      r->try_recycle_under_lock();
      // No need to adjust_interval_for_recycled_old_region.  That will be taken care of during freeset rebuild.
    }
    if (r->is_cset()) {
      // Leave affiliation unchanged
      r->make_regular_bypass();
    }
    if (r->is_empty_uncommitted()) {
      r->make_committed_bypass();
    }
    assert (r->is_committed(), "only committed regions in heap now, see region %zu", r->index());

    // Record current region occupancy: this communicates empty regions are free
    // to the rest of Full GC code.
    r->set_new_top(r->top());
  }
};

class ShenandoahTrashImmediateGarbageClosure: public ShenandoahHeapRegionClosure {
private:
  ShenandoahHeap* const _heap;
  ShenandoahMarkingContext* const _ctx;

public:
  ShenandoahTrashImmediateGarbageClosure() :
    _heap(ShenandoahHeap::heap()),
    _ctx(ShenandoahHeap::heap()->global_generation()->complete_marking_context()) {}

  void heap_region_do(ShenandoahHeapRegion* r) override {
    if (r->is_humongous_start()) {
      oop humongous_obj = cast_to_oop(r->bottom());
      if (!_ctx->is_marked(humongous_obj)) {
        assert(!r->has_live(), "Region %zu is not marked, should not have live", r->index());
        _heap->trash_humongous_region_at(r);
      } else {
        assert(r->has_live(), "Region %zu should have live", r->index());
      }
    } else if (r->is_humongous_continuation()) {
      // If we hit continuation, the non-live humongous starts should have been trashed already
      assert(r->humongous_start_region()->has_live(), "Region %zu should have live", r->index());
    } else if (r->is_regular()) {
      if (!r->has_live()) {
        r->make_trash_immediate();
      }
    }
  }
};

void ShenandoahFullGC::distribute_slices(ShenandoahHeapRegionSet** worker_slices) {
  uint n_workers = _heap->workers()->active_workers();
  size_t n_regions = _heap->num_regions();

  // What we want to accomplish: have the dense prefix of data, while still balancing
  // out the parallel work.
  //
  // Assuming the amount of work is driven by the live data that needs moving, we can slice
  // the entire heap into equal-live-sized prefix slices, and compact into them. So, each
  // thread takes all regions in its prefix subset, and then it takes some regions from
  // the tail.
  //
  // Tail region selection becomes interesting.
  //
  // First, we want to distribute the regions fairly between the workers, and those regions
  // might have different amount of live data. So, until we sure no workers need live data,
  // we need to only take what the worker needs.
  //
  // Second, since we slide everything to the left in each slice, the most busy regions
  // would be the ones on the left. Which means we want to have all workers have their after-tail
  // regions as close to the left as possible.
  //
  // The easiest way to do this is to distribute after-tail regions in round-robin between
  // workers that still need live data.
  //
  // Consider parallel workers A, B, C, then the target slice layout would be:
  //
  //  AAAAAAAABBBBBBBBCCCCCCCC|ABCABCABCABCABCABCABCABABABABABABABABABABAAAAA
  //
  //  (.....dense-prefix.....) (.....................tail...................)
  //  [all regions fully live] [left-most regions are fuller that right-most]
  //

  // Compute how much live data is there. This would approximate the size of dense prefix
  // we target to create.
  size_t total_live = 0;
  for (size_t idx = 0; idx < n_regions; idx++) {
    ShenandoahHeapRegion *r = _heap->get_region(idx);
    if (ShenandoahPrepareForCompactionTask::is_candidate_region(r)) {
      total_live += r->get_live_data_words();
    }
  }

  // Estimate the size for the dense prefix. Note that we specifically count only the
  // "full" regions, so there would be some non-full regions in the slice tail.
  size_t live_per_worker = total_live / n_workers;
  size_t prefix_regions_per_worker = live_per_worker / ShenandoahHeapRegion::region_size_words();
  size_t prefix_regions_total = prefix_regions_per_worker * n_workers;
  prefix_regions_total = MIN2(prefix_regions_total, n_regions);
  assert(prefix_regions_total <= n_regions, "Sanity");

  // There might be non-candidate regions in the prefix. To compute where the tail actually
  // ends up being, we need to account those as well.
  size_t prefix_end = prefix_regions_total;
  for (size_t idx = 0; idx < prefix_regions_total; idx++) {
    ShenandoahHeapRegion *r = _heap->get_region(idx);
    if (!ShenandoahPrepareForCompactionTask::is_candidate_region(r)) {
      prefix_end++;
    }
  }
  prefix_end = MIN2(prefix_end, n_regions);
  assert(prefix_end <= n_regions, "Sanity");

  // Distribute prefix regions per worker: each thread definitely gets its own same-sized
  // subset of dense prefix.
  size_t prefix_idx = 0;

  size_t* live = NEW_C_HEAP_ARRAY(size_t, n_workers, mtGC);

  for (size_t wid = 0; wid < n_workers; wid++) {
    ShenandoahHeapRegionSet* slice = worker_slices[wid];

    live[wid] = 0;
    size_t regs = 0;

    // Add all prefix regions for this worker
    while (prefix_idx < prefix_end && regs < prefix_regions_per_worker) {
      ShenandoahHeapRegion *r = _heap->get_region(prefix_idx);
      if (ShenandoahPrepareForCompactionTask::is_candidate_region(r)) {
        slice->add_region(r);
        live[wid] += r->get_live_data_words();
        regs++;
      }
      prefix_idx++;
    }
  }

  // Distribute the tail among workers in round-robin fashion.
  size_t wid = n_workers - 1;

  for (size_t tail_idx = prefix_end; tail_idx < n_regions; tail_idx++) {
    ShenandoahHeapRegion *r = _heap->get_region(tail_idx);
    if (ShenandoahPrepareForCompactionTask::is_candidate_region(r)) {
      assert(wid < n_workers, "Sanity");

      size_t live_region = r->get_live_data_words();

      // Select next worker that still needs live data.
      size_t old_wid = wid;
      do {
        wid++;
        if (wid == n_workers) wid = 0;
      } while (live[wid] + live_region >= live_per_worker && old_wid != wid);

      if (old_wid == wid) {
        // Circled back to the same worker? This means liveness data was
        // miscalculated. Bump the live_per_worker limit so that
        // everyone gets a piece of the leftover work.
        live_per_worker += ShenandoahHeapRegion::region_size_words();
      }

      worker_slices[wid]->add_region(r);
      live[wid] += live_region;
    }
  }

  FREE_C_HEAP_ARRAY(live);

#ifdef ASSERT
  ResourceBitMap map(n_regions);
  for (size_t wid = 0; wid < n_workers; wid++) {
    ShenandoahHeapRegionSetIterator it(worker_slices[wid]);
    ShenandoahHeapRegion* r = it.next();
    while (r != nullptr) {
      size_t idx = r->index();
      assert(ShenandoahPrepareForCompactionTask::is_candidate_region(r), "Sanity: %zu", idx);
      assert(!map.at(idx), "No region distributed twice: %zu", idx);
      map.at_put(idx, true);
      r = it.next();
    }
  }

  for (size_t rid = 0; rid < n_regions; rid++) {
    bool is_candidate = ShenandoahPrepareForCompactionTask::is_candidate_region(_heap->get_region(rid));
    bool is_distributed = map.at(rid);
    assert(is_distributed || !is_candidate, "All candidates are distributed: %zu", rid);
  }
#endif
}

void ShenandoahFullGC::phase2_calculate_target_addresses(ShenandoahHeapRegionSet** worker_slices) {
  GCTraceTime(Info, gc, phases) time("Phase 2: Compute new object addresses", _gc_timer);
  ShenandoahGCPhase calculate_address_phase(ShenandoahPhaseTimings::full_gc_calculate_addresses);

  // About to figure out which regions can be compacted, make sure pinning status
  // had been updated in GC prologue.
  _heap->assert_pinned_region_status();

  {
    // Trash the immediately collectible regions before computing addresses
    ShenandoahTrashImmediateGarbageClosure trash_immediate_garbage;
    ShenandoahExcludeRegionClosure<FREE> cl(&trash_immediate_garbage);
    _heap->heap_region_iterate(&cl);

    // Make sure regions are in good state: committed, active, clean.
    // This is needed because we are potentially sliding the data through them.
    ShenandoahEnsureHeapActiveClosure ecl;
    _heap->heap_region_iterate(&ecl);
  }

  // Compute the new addresses for regular objects
  {
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::full_gc_calculate_addresses_regular);

    distribute_slices(worker_slices);

    ShenandoahPrepareForCompactionTask task(_preserved_marks, worker_slices);
    _heap->workers()->run_task(&task);
  }

  // Compute the new addresses for humongous objects
  {
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::full_gc_calculate_addresses_humong);
    calculate_target_humongous_objects();
  }
}

class ShenandoahAdjustPointersClosure : public MetadataVisitingOopIterateClosure {
private:
  ShenandoahMarkingContext* const _ctx;

  template <class T>
  inline void do_oop_work(T* p) {
    T o = RawAccess<>::oop_load(p);
    if (!CompressedOops::is_null(o)) {
      oop obj = CompressedOops::decode_not_null(o);
      assert(_ctx->is_marked(obj), "must be marked");
      if (FullGCForwarding::is_forwarded(obj)) {
        oop forw = FullGCForwarding::forwardee(obj);
        RawAccess<IS_NOT_NULL>::oop_store(p, forw);
      }
    }
  }

public:
  ShenandoahAdjustPointersClosure() :
    _ctx(ShenandoahHeap::heap()->global_generation()->complete_marking_context()) {}

  void do_oop(oop* p)       { do_oop_work(p); }
  void do_oop(narrowOop* p) { do_oop_work(p); }
  void do_method(Method* m) {}
  void do_nmethod(nmethod* nm) {}
};

class ShenandoahAdjustPointersObjectClosure : public ObjectClosure {
private:
  ShenandoahAdjustPointersClosure _cl;

public:
  void do_object(oop p) override {
    assert(ShenandoahHeap::heap()->global_generation()->is_mark_complete(), "marking must be complete");
    assert(ShenandoahHeap::heap()->marking_context()->is_marked(p), "must be marked");
    p->oop_iterate(&_cl);
  }
};

class ShenandoahAdjustPointersTask : public WorkerTask {
private:
  ShenandoahHeap*          const _heap;
  ShenandoahRegionIterator       _regions;

public:
  ShenandoahAdjustPointersTask() :
    WorkerTask("Shenandoah Adjust Pointers"),
    _heap(ShenandoahHeap::heap()) {
  }

  void work(uint worker_id) override {
    ShenandoahParallelWorkerSession worker_session(worker_id);
    ShenandoahAdjustPointersObjectClosure obj_cl;
    ShenandoahHeapRegion* r = _regions.next();
    while (r != nullptr) {
      if (!r->is_humongous_continuation() && r->has_live()) {
        _heap->marked_object_iterate(r, &obj_cl);
      }
      if (_heap->mode()->is_generational()) {
        ShenandoahGenerationalFullGC::maybe_coalesce_and_fill_region(r);
      }
      r = _regions.next();
    }
  }
};

class ShenandoahAdjustRootPointersTask : public WorkerTask {
private:
  ShenandoahRootAdjuster* _rp;
  PreservedMarksSet* _preserved_marks;
public:
  ShenandoahAdjustRootPointersTask(ShenandoahRootAdjuster* rp, PreservedMarksSet* preserved_marks) :
    WorkerTask("Shenandoah Adjust Root Pointers"),
    _rp(rp),
    _preserved_marks(preserved_marks) {}

  void work(uint worker_id) override {
    ShenandoahParallelWorkerSession worker_session(worker_id);
    ShenandoahAdjustPointersClosure cl;
    _rp->roots_do(worker_id, &cl);
    _preserved_marks->get(worker_id)->adjust_during_full_gc();
  }
};

void ShenandoahFullGC::phase3_update_references() {
  GCTraceTime(Info, gc, phases) time("Phase 3: Adjust pointers", _gc_timer);
  ShenandoahGCPhase adjust_pointer_phase(ShenandoahPhaseTimings::full_gc_adjust_pointers);

  WorkerThreads* workers = _heap->workers();
  uint nworkers = workers->active_workers();
  {
#ifdef COMPILER2
    DerivedPointerTable::clear();
#endif // COMPILER2
    ShenandoahRootAdjuster rp(nworkers, ShenandoahPhaseTimings::full_gc_adjust_roots);
    ShenandoahAdjustRootPointersTask task(&rp, _preserved_marks);
    workers->run_task(&task);
#ifdef COMPILER2
    DerivedPointerTable::update_pointers();
#endif // COMPILER2
  }

  ShenandoahAdjustPointersTask adjust_pointers_task;
  workers->run_task(&adjust_pointers_task);
}

class ShenandoahCompactObjectsClosure : public ObjectClosure {
private:
  uint const _worker_id;

public:
  explicit ShenandoahCompactObjectsClosure(uint worker_id) :
    _worker_id(worker_id) {}

  void do_object(oop p) override {
    assert(ShenandoahHeap::heap()->global_generation()->is_mark_complete(), "marking must be finished");
    assert(ShenandoahHeap::heap()->marking_context()->is_marked(p), "must be marked");
    size_t size = p->size();
    if (FullGCForwarding::is_forwarded(p)) {
      HeapWord* compact_from = cast_from_oop<HeapWord*>(p);
      HeapWord* compact_to = cast_from_oop<HeapWord*>(FullGCForwarding::forwardee(p));
      assert(compact_from != compact_to, "Forwarded object should move");
      Copy::aligned_conjoint_words(compact_from, compact_to, size);
      oop new_obj = cast_to_oop(compact_to);

      // Restore the mark word before relativizing the stack chunk. The copy's
      // mark word contains the full GC forwarding encoding, which would cause
      // is_stackChunk() to read garbage (especially with compact headers).
      new_obj->init_mark();
      ContinuationGCSupport::relativize_stack_chunk(new_obj);
    }
  }
};

class ShenandoahCompactObjectsTask : public WorkerTask {
private:
  ShenandoahHeap* const _heap;
  ShenandoahHeapRegionSet** const _worker_slices;

public:
  ShenandoahCompactObjectsTask(ShenandoahHeapRegionSet** worker_slices) :
    WorkerTask("Shenandoah Compact Objects"),
    _heap(ShenandoahHeap::heap()),
    _worker_slices(worker_slices) {
  }

  void work(uint worker_id) override {
    ShenandoahParallelWorkerSession worker_session(worker_id);
    ShenandoahHeapRegionSetIterator slice(_worker_slices[worker_id]);

    ShenandoahCompactObjectsClosure cl(worker_id);
    ShenandoahHeapRegion* r = slice.next();
    while (r != nullptr) {
      assert(!r->is_humongous(), "must not get humongous regions here");
      if (r->has_live()) {
        _heap->marked_object_iterate(r, &cl);
      }
      r->set_top(r->new_top());
      r = slice.next();
    }
  }
};

class ShenandoahPostCompactClosure : public ShenandoahHeapRegionClosure {
private:
  ShenandoahHeap* const _heap;
  bool _is_generational;
  size_t _young_regions, _young_usage, _young_humongous_waste;
  size_t _old_regions, _old_usage, _old_humongous_waste;

public:
  ShenandoahPostCompactClosure() : _heap(ShenandoahHeap::heap()),
                                   _is_generational(_heap->mode()->is_generational()),
                                   _young_regions(0),
                                   _young_usage(0),
                                   _young_humongous_waste(0),
                                   _old_regions(0),
                                   _old_usage(0),
                                   _old_humongous_waste(0)
  {
    _heap->free_set()->clear();
  }

  void heap_region_do(ShenandoahHeapRegion* r) override {
    assert (!r->is_cset(), "cset regions should have been demoted already");

    // Need to reset the complete-top-at-mark-start pointer here because
    // the complete marking bitmap is no longer valid. This ensures
    // size-based iteration in marked_object_iterate().
    // NOTE: See blurb at ShenandoahMCResetCompleteBitmapTask on why we need to skip
    // pinned regions.
    if (!r->is_pinned()) {
      _heap->marking_context()->reset_top_at_mark_start(r);
    }

    size_t live = r->used();

    // Make empty regions that have been allocated into regular
    if (r->is_empty() && live > 0) {
      if (!_is_generational) {
        r->make_affiliated_maybe();
      }
      // else, generational mode compaction has already established affiliation.
      r->make_regular_bypass();
      if (ZapUnusedHeapArea) {
        SpaceMangler::mangle_region(MemRegion(r->top(), r->end()));
      }
    }

    // Reclaim regular regions that became empty
    if (r->is_regular() && live == 0) {
      r->make_trash();
    }

    // Recycle all trash regions
    if (r->is_trash()) {
      live = 0;
      r->try_recycle_under_lock();
      // No need to adjust_interval_for_recycled_old_region.  That will be taken care of during freeset rebuild.
    } else {
      if (r->is_old()) {
        ShenandoahGenerationalFullGC::account_for_region(r, _old_regions, _old_usage, _old_humongous_waste);
      } else if (r->is_young()) {
        ShenandoahGenerationalFullGC::account_for_region(r, _young_regions, _young_usage, _young_humongous_waste);
      }
    }
    r->set_live_data(live);
    r->reset_alloc_metadata();
  }
};

void ShenandoahFullGC::compact_humongous_objects() {
  // Compact humongous regions, based on their fwdptr objects.
  //
  // This code is serial, because doing the in-slice parallel sliding is tricky. In most cases,
  // humongous regions are already compacted, and do not require further moves, which alleviates
  // sliding costs. We may consider doing this in parallel in the future.

  for (size_t c = _heap->num_regions(); c > 0; c--) {
    ShenandoahHeapRegion* r = _heap->get_region(c - 1);
    if (r->is_humongous_start()) {
      oop old_obj = cast_to_oop(r->bottom());
      if (!FullGCForwarding::is_forwarded(old_obj)) {
        // No need to move the object, it stays at the same slot
        continue;
      }
      size_t words_size = old_obj->size();
      size_t num_regions = ShenandoahHeapRegion::required_regions(words_size * HeapWordSize);

      size_t old_start = r->index();
      size_t old_end   = old_start + num_regions - 1;
      size_t new_start = _heap->heap_region_index_containing(FullGCForwarding::forwardee(old_obj));
      size_t new_end   = new_start + num_regions - 1;
      assert(old_start != new_start, "must be real move");
      assert(r->is_stw_move_allowed(), "Region %zu should be movable", r->index());

      log_debug(gc)("Full GC compaction moves humongous object from region %zu to region %zu", old_start, new_start);
      Copy::aligned_conjoint_words(r->bottom(), _heap->get_region(new_start)->bottom(), words_size);
      ContinuationGCSupport::relativize_stack_chunk(cast_to_oop<HeapWord*>(r->bottom()));

      oop new_obj = cast_to_oop(_heap->get_region(new_start)->bottom());
      new_obj->init_mark();

      {
        ShenandoahAffiliation original_affiliation = r->affiliation();
        for (size_t c = old_start; c <= old_end; c++) {
          ShenandoahHeapRegion* r = _heap->get_region(c);
          // Leave humongous region affiliation unchanged.
          r->make_regular_bypass();
          r->set_top(r->bottom());
        }

        for (size_t c = new_start; c <= new_end; c++) {
          ShenandoahHeapRegion* r = _heap->get_region(c);
          if (c == new_start) {
            r->make_humongous_start_bypass(original_affiliation);
          } else {
            r->make_humongous_cont_bypass(original_affiliation);
          }

          // Trailing region may be non-full, record the remainder there
          size_t remainder = words_size & ShenandoahHeapRegion::region_size_words_mask();
          if ((c == new_end) && (remainder != 0)) {
            r->set_top(r->bottom() + remainder);
          } else {
            r->set_top(r->end());
          }

          r->reset_alloc_metadata();
        }
      }
    }
  }
}

// This is slightly different to ShHeap::reset_next_mark_bitmap:
// we need to remain able to walk pinned regions.
// Since pinned region do not move and don't get compacted, we will get holes with
// unreachable objects in them (which may have pointers to unloaded Klasses and thus
// cannot be iterated over using oop->size()). The only way to safely iterate over those is using
// a valid marking bitmap and valid TAMS pointer. This class only resets marking
// bitmaps for un-pinned regions, and later we only reset TAMS for unpinned regions.
class ShenandoahMCResetCompleteBitmapTask : public WorkerTask {
private:
  ShenandoahRegionIterator _regions;

public:
  ShenandoahMCResetCompleteBitmapTask() :
    WorkerTask("Shenandoah Reset Bitmap") {
  }

  void work(uint worker_id) override {
    ShenandoahParallelWorkerSession worker_session(worker_id);
    ShenandoahHeapRegion* region = _regions.next();
    ShenandoahHeap* heap = ShenandoahHeap::heap();
    ShenandoahMarkingContext* const ctx = heap->marking_context();
    assert(heap->global_generation()->is_mark_complete(), "Marking must be complete");
    while (region != nullptr) {
      if (heap->is_bitmap_slice_committed(region) && !region->is_pinned() && region->has_live()) {
        ctx->clear_bitmap(region);
      }
      region = _regions.next();
    }
  }
};

void ShenandoahFullGC::phase4_compact_objects(ShenandoahHeapRegionSet** worker_slices) {
  GCTraceTime(Info, gc, phases) time("Phase 4: Move objects", _gc_timer);
  ShenandoahGCPhase compaction_phase(ShenandoahPhaseTimings::full_gc_copy_objects);

  // Compact regular objects first
  {
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::full_gc_copy_objects_regular);
    ShenandoahCompactObjectsTask compact_task(worker_slices);
    _heap->workers()->run_task(&compact_task);
  }

  // Compact humongous objects after regular object moves
  {
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::full_gc_copy_objects_humong);
    compact_humongous_objects();
  }
}

void ShenandoahFullGC::phase5_epilog() {
  GCTraceTime(Info, gc, phases) time("Phase 5: Full GC epilog", _gc_timer);
  // Reset complete bitmap. We're about to reset the complete-top-at-mark-start pointer
  // and must ensure the bitmap is in sync.
  {
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::full_gc_copy_objects_reset_complete);
    ShenandoahMCResetCompleteBitmapTask task;
    _heap->workers()->run_task(&task);
  }

  // Bring regions in proper states after the collection, and set heap properties.
  {
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::full_gc_copy_objects_rebuild);
    ShenandoahPostCompactClosure post_compact;
    _heap->heap_region_iterate(&post_compact);
    _heap->collection_set()->clear();
    {
      ShenandoahFreeSet* free_set = _heap->free_set();
      size_t young_trashed_regions, old_trashed_regions, first_old, last_old, num_old;
      free_set->prepare_to_rebuild(young_trashed_regions, old_trashed_regions, first_old, last_old, num_old);
      // We also do not expand old generation size following Full GC because we have scrambled age populations and
      // no longer have objects separated by age into distinct regions.
      if (_heap->mode()->is_generational()) {
        ShenandoahGenerationalFullGC::compute_balances();
      }
      _heap->free_set()->finish_rebuild(young_trashed_regions, old_trashed_regions, num_old);
    }

    // Set mark incomplete because the marking bitmaps have been reset except pinned regions.
    _generation->set_mark_incomplete();

    _heap->clear_cancelled_gc();
  }

  _preserved_marks->restore(_heap->workers());
  _preserved_marks->reclaim();

  if (_heap->mode()->is_generational()) {
    ShenandoahGenerationalFullGC::rebuild_remembered_set(_heap);
  }
}
