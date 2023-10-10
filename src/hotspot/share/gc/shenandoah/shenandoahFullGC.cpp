/*
 * Copyright (c) 2014, 2021, Red Hat, Inc. All rights reserved.
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

#include "compiler/oopMap.hpp"
#include "gc/shared/continuationGCSupport.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/preservedMarks.inline.hpp"
#include "gc/shared/tlab_globals.hpp"
#include "gc/shared/workerThread.hpp"
#include "gc/shenandoah/heuristics/shenandoahHeuristics.hpp"
#include "gc/shenandoah/shenandoahConcurrentGC.hpp"
#include "gc/shenandoah/shenandoahCollectionSet.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahFullGC.hpp"
#include "gc/shenandoah/shenandoahGlobalGeneration.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shenandoah/shenandoahMark.inline.hpp"
#include "gc/shenandoah/shenandoahMonitoringSupport.hpp"
#include "gc/shenandoah/shenandoahHeapRegionSet.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.inline.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.inline.hpp"
#include "gc/shenandoah/shenandoahMetrics.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahOopClosures.inline.hpp"
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
#include "runtime/javaThread.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/copy.hpp"
#include "utilities/events.hpp"
#include "utilities/growableArray.hpp"

// After Full GC is done, reconstruct the remembered set by iterating over OLD regions,
// registering all objects between bottom() and top(), and setting remembered set cards to
// DIRTY if they hold interesting pointers.
class ShenandoahReconstructRememberedSetTask : public WorkerTask {
private:
  ShenandoahRegionIterator _regions;

public:
  ShenandoahReconstructRememberedSetTask() :
    WorkerTask("Shenandoah Reset Bitmap") { }

  void work(uint worker_id) {
    ShenandoahParallelWorkerSession worker_session(worker_id);
    ShenandoahHeapRegion* r = _regions.next();
    ShenandoahHeap* heap = ShenandoahHeap::heap();
    RememberedScanner* scanner = heap->card_scan();
    ShenandoahSetRememberedCardsToDirtyClosure dirty_cards_for_interesting_pointers;

    while (r != nullptr) {
      if (r->is_old() && r->is_active()) {
        HeapWord* obj_addr = r->bottom();
        if (r->is_humongous_start()) {
          // First, clear the remembered set
          oop obj = cast_to_oop(obj_addr);
          size_t size = obj->size();

          // First, clear the remembered set for all spanned humongous regions
          size_t num_regions = ShenandoahHeapRegion::required_regions(size * HeapWordSize);
          size_t region_span = num_regions * ShenandoahHeapRegion::region_size_words();
          scanner->reset_remset(r->bottom(), region_span);
          size_t region_index = r->index();
          ShenandoahHeapRegion* humongous_region = heap->get_region(region_index);
          while (num_regions-- != 0) {
            scanner->reset_object_range(humongous_region->bottom(), humongous_region->end());
            region_index++;
            humongous_region = heap->get_region(region_index);
          }

          // Then register the humongous object and DIRTY relevant remembered set cards
          scanner->register_object_without_lock(obj_addr);
          obj->oop_iterate(&dirty_cards_for_interesting_pointers);
        } else if (!r->is_humongous()) {
          // First, clear the remembered set
          scanner->reset_remset(r->bottom(), ShenandoahHeapRegion::region_size_words());
          scanner->reset_object_range(r->bottom(), r->end());

          // Then iterate over all objects, registering object and DIRTYing relevant remembered set cards
          HeapWord* t = r->top();
          while (obj_addr < t) {
            oop obj = cast_to_oop(obj_addr);
            size_t size = obj->size();
            scanner->register_object_without_lock(obj_addr);
            obj_addr += obj->oop_iterate_size(&dirty_cards_for_interesting_pointers);
          }
        } // else, ignore humongous continuation region
      }
      // else, this region is FREE or YOUNG or inactive and we can ignore it.
      // TODO: Assert this.
      r = _regions.next();
    }
  }
};

ShenandoahFullGC::ShenandoahFullGC() :
  _gc_timer(ShenandoahHeap::heap()->gc_timer()),
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
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  TraceCollectorStats tcs(heap->monitoring_support()->full_stw_collection_counters());
  ShenandoahTimingsTracker timing(ShenandoahPhaseTimings::full_gc_gross);

  heap->try_inject_alloc_failure();
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
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  ShenandoahMetricsSnapshot metrics;
  metrics.snap_before();

  // Perform full GC
  do_it(cause);

  metrics.snap_after();
  if (heap->mode()->is_generational()) {
    heap->mmu_tracker()->record_full(heap->global_generation(), GCId::current());
    heap->log_heap_status("At end of Full GC");

    // Since we allow temporary violation of these constraints during Full GC, we want to enforce that the assertions are
    // made valid by the time Full GC completes.
    assert(heap->old_generation()->used_regions_size() <= heap->old_generation()->max_capacity(),
           "Old generation affiliated regions must be less than capacity");
    assert(heap->young_generation()->used_regions_size() <= heap->young_generation()->max_capacity(),
           "Young generation affiliated regions must be less than capacity");

    assert((heap->young_generation()->used() + heap->young_generation()->get_humongous_waste())
           <= heap->young_generation()->used_regions_size(), "Young consumed can be no larger than span of affiliated regions");
    assert((heap->old_generation()->used() + heap->old_generation()->get_humongous_waste())
           <= heap->old_generation()->used_regions_size(), "Old consumed can be no larger than span of affiliated regions");

  }
  if (metrics.is_good_progress()) {
    ShenandoahHeap::heap()->notify_gc_progress();
  } else {
    // Nothing to do. Tell the allocation path that we have failed to make
    // progress, and it can finally fail.
    ShenandoahHeap::heap()->notify_gc_no_progress();
  }
}

void ShenandoahFullGC::do_it(GCCause::Cause gc_cause) {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  // Since we may arrive here from degenerated GC failure of either young or old, establish generation as GLOBAL.
  heap->set_gc_generation(heap->global_generation());

  if (heap->mode()->is_generational()) {
    // No need for old_gen->increase_used() as this was done when plabs were allocated.
    heap->set_young_evac_reserve(0);
    heap->set_old_evac_reserve(0);
    heap->set_promoted_reserve(0);

    // Full GC supersedes any marking or coalescing in old generation.
    heap->cancel_old_gc();
  }

  if (ShenandoahVerify) {
    heap->verifier()->verify_before_fullgc();
  }

  if (VerifyBeforeGC) {
    Universe::verify();
  }

  // Degenerated GC may carry concurrent root flags when upgrading to
  // full GC. We need to reset it before mutators resume.
  heap->set_concurrent_strong_root_in_progress(false);
  heap->set_concurrent_weak_root_in_progress(false);

  heap->set_full_gc_in_progress(true);

  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "must be at a safepoint");
  assert(Thread::current()->is_VM_thread(), "Do full GC only while world is stopped");

  {
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::full_gc_heapdump_pre);
    heap->pre_full_gc_dump(_gc_timer);
  }

  {
    ShenandoahGCPhase prepare_phase(ShenandoahPhaseTimings::full_gc_prepare);
    // Full GC is supposed to recover from any GC state:

    // a0. Remember if we have forwarded objects
    bool has_forwarded_objects = heap->has_forwarded_objects();

    // a1. Cancel evacuation, if in progress
    if (heap->is_evacuation_in_progress()) {
      heap->set_evacuation_in_progress(false);
    }
    assert(!heap->is_evacuation_in_progress(), "sanity");

    // a2. Cancel update-refs, if in progress
    if (heap->is_update_refs_in_progress()) {
      heap->set_update_refs_in_progress(false);
    }
    assert(!heap->is_update_refs_in_progress(), "sanity");

    // b. Cancel all concurrent marks, if in progress
    if (heap->is_concurrent_mark_in_progress()) {
      heap->cancel_concurrent_mark();
    }
    assert(!heap->is_concurrent_mark_in_progress(), "sanity");

    // c. Update roots if this full GC is due to evac-oom, which may carry from-space pointers in roots.
    if (has_forwarded_objects) {
      update_roots(true /*full_gc*/);
    }

    // d. Reset the bitmaps for new marking
    heap->global_generation()->reset_mark_bitmap();
    assert(heap->marking_context()->is_bitmap_clear(), "sanity");
    assert(!heap->global_generation()->is_mark_complete(), "sanity");

    // e. Abandon reference discovery and clear all discovered references.
    ShenandoahReferenceProcessor* rp = heap->global_generation()->ref_processor();
    rp->abandon_partial_discovery();

    // f. Sync pinned region status from the CP marks
    heap->sync_pinned_region_status();

    if (heap->mode()->is_generational()) {
      for (size_t i = 0; i < heap->num_regions(); i++) {
        ShenandoahHeapRegion* r = heap->get_region(i);
        if (r->get_top_before_promote() != nullptr) {
          r->restore_top_before_promote();
        }
      }
    }

    // The rest of prologue:
    _preserved_marks->init(heap->workers()->active_workers());

    assert(heap->has_forwarded_objects() == has_forwarded_objects, "This should not change");
  }

  if (UseTLAB) {
    // TODO: Do we need to explicitly retire PLABs?
    heap->gclabs_retire(ResizeTLAB);
    heap->tlabs_retire(ResizeTLAB);
  }

  OrderAccess::fence();

  phase1_mark_heap();

  // Once marking is done, which may have fixed up forwarded objects, we can drop it.
  // Coming out of Full GC, we would not have any forwarded objects.
  // This also prevents resolves with fwdptr from kicking in while adjusting pointers in phase3.
  heap->set_has_forwarded_objects(false);

  heap->set_full_gc_move_in_progress(true);

  // Setup workers for the rest
  OrderAccess::fence();

  // Initialize worker slices
  ShenandoahHeapRegionSet** worker_slices = NEW_C_HEAP_ARRAY(ShenandoahHeapRegionSet*, heap->max_workers(), mtGC);
  for (uint i = 0; i < heap->max_workers(); i++) {
    worker_slices[i] = new ShenandoahHeapRegionSet();
  }

  {
    // The rest of code performs region moves, where region status is undefined
    // until all phases run together.
    ShenandoahHeapLocker lock(heap->lock());

    phase2_calculate_target_addresses(worker_slices);

    OrderAccess::fence();

    phase3_update_references();

    phase4_compact_objects(worker_slices);

    phase5_epilog();
  }

  {
    // Epilogue
    // TODO: Merge with phase5_epilog?
    _preserved_marks->restore(heap->workers());
    _preserved_marks->reclaim();

    if (heap->mode()->is_generational()) {
      ShenandoahGCPhase phase(ShenandoahPhaseTimings::full_gc_reconstruct_remembered_set);
      ShenandoahReconstructRememberedSetTask task;
      heap->workers()->run_task(&task);
    }
  }

  // Resize metaspace
  MetaspaceGC::compute_new_size();

  // Free worker slices
  for (uint i = 0; i < heap->max_workers(); i++) {
    delete worker_slices[i];
  }
  FREE_C_HEAP_ARRAY(ShenandoahHeapRegionSet*, worker_slices);

  heap->set_full_gc_move_in_progress(false);
  heap->set_full_gc_in_progress(false);

  if (ShenandoahVerify) {
    heap->verifier()->verify_after_fullgc();
  }

  // Humongous regions are promoted on demand and are accounted for by normal Full GC mechanisms.
  if (VerifyAfterGC) {
    Universe::verify();
  }

  {
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::full_gc_heapdump_post);
    heap->post_full_gc_dump(_gc_timer);
  }
}

class ShenandoahPrepareForMarkClosure: public ShenandoahHeapRegionClosure {
private:
  ShenandoahMarkingContext* const _ctx;

public:
  ShenandoahPrepareForMarkClosure() : _ctx(ShenandoahHeap::heap()->marking_context()) {}

  void heap_region_do(ShenandoahHeapRegion *r) {
    if (r->affiliation() != FREE) {
      _ctx->capture_top_at_mark_start(r);
      r->clear_live_data();
    }
  }

  bool is_thread_safe() { return true; }
};

void ShenandoahFullGC::phase1_mark_heap() {
  GCTraceTime(Info, gc, phases) time("Phase 1: Mark live objects", _gc_timer);
  ShenandoahGCPhase mark_phase(ShenandoahPhaseTimings::full_gc_mark);

  ShenandoahHeap* heap = ShenandoahHeap::heap();

  ShenandoahPrepareForMarkClosure cl;
  heap->parallel_heap_region_iterate(&cl);

  heap->set_unload_classes(heap->global_generation()->heuristics()->can_unload_classes());

  ShenandoahReferenceProcessor* rp = heap->global_generation()->ref_processor();
  // enable ("weak") refs discovery
  rp->set_soft_reference_policy(true); // forcefully purge all soft references

  ShenandoahSTWMark mark(heap->global_generation(), true /*full_gc*/);
  mark.mark();
  heap->parallel_cleaning(true /* full_gc */);

  size_t live_bytes_in_old = 0;
  for (size_t i = 0; i < heap->num_regions(); i++) {
    ShenandoahHeapRegion* r = heap->get_region(i);
    if (r->is_old()) {
      live_bytes_in_old += r->get_live_data_bytes();
    }
  }
  log_info(gc)("Live bytes in old after STW mark: " PROPERFMT, PROPERFMTARGS(live_bytes_in_old));
  heap->old_generation()->set_live_bytes_after_last_mark(live_bytes_in_old);
}

class ShenandoahPrepareForCompactionTask : public WorkerTask {
private:
  PreservedMarksSet*        const _preserved_marks;
  ShenandoahHeap*           const _heap;
  ShenandoahHeapRegionSet** const _worker_slices;
  size_t                    const _num_workers;

public:
  ShenandoahPrepareForCompactionTask(PreservedMarksSet *preserved_marks,
                                     ShenandoahHeapRegionSet **worker_slices,
                                     size_t num_workers);

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

  void work(uint worker_id);
};

class ShenandoahPrepareForGenerationalCompactionObjectClosure : public ObjectClosure {
private:
  PreservedMarks*          const _preserved_marks;
  ShenandoahHeap*          const _heap;
  uint                           _tenuring_threshold;

  // _empty_regions is a thread-local list of heap regions that have been completely emptied by this worker thread's
  // compaction efforts.  The worker thread that drives these efforts adds compacted regions to this list if the
  // region has not been compacted onto itself.
  GrowableArray<ShenandoahHeapRegion*>& _empty_regions;
  int _empty_regions_pos;
  ShenandoahHeapRegion*          _old_to_region;
  ShenandoahHeapRegion*          _young_to_region;
  ShenandoahHeapRegion*          _from_region;
  ShenandoahAffiliation          _from_affiliation;
  HeapWord*                      _old_compact_point;
  HeapWord*                      _young_compact_point;
  uint                           _worker_id;

public:
  ShenandoahPrepareForGenerationalCompactionObjectClosure(PreservedMarks* preserved_marks,
                                                          GrowableArray<ShenandoahHeapRegion*>& empty_regions,
                                                          ShenandoahHeapRegion* old_to_region,
                                                          ShenandoahHeapRegion* young_to_region, uint worker_id) :
      _preserved_marks(preserved_marks),
      _heap(ShenandoahHeap::heap()),
      _tenuring_threshold(0),
      _empty_regions(empty_regions),
      _empty_regions_pos(0),
      _old_to_region(old_to_region),
      _young_to_region(young_to_region),
      _from_region(nullptr),
      _old_compact_point((old_to_region != nullptr)? old_to_region->bottom(): nullptr),
      _young_compact_point((young_to_region != nullptr)? young_to_region->bottom(): nullptr),
      _worker_id(worker_id) {
    if (_heap->mode()->is_generational()) {
      _tenuring_threshold = _heap->age_census()->tenuring_threshold();
    }
  }

  void set_from_region(ShenandoahHeapRegion* from_region) {
    _from_region = from_region;
    _from_affiliation = from_region->affiliation();
    if (_from_region->has_live()) {
      if (_from_affiliation == ShenandoahAffiliation::OLD_GENERATION) {
        if (_old_to_region == nullptr) {
          _old_to_region = from_region;
          _old_compact_point = from_region->bottom();
        }
      } else {
        assert(_from_affiliation == ShenandoahAffiliation::YOUNG_GENERATION, "from_region must be OLD or YOUNG");
        if (_young_to_region == nullptr) {
          _young_to_region = from_region;
          _young_compact_point = from_region->bottom();
        }
      }
    } // else, we won't iterate over this _from_region so we don't need to set up to region to hold copies
  }

  void finish() {
    finish_old_region();
    finish_young_region();
  }

  void finish_old_region() {
    if (_old_to_region != nullptr) {
      log_debug(gc)("Planned compaction into Old Region " SIZE_FORMAT ", used: " SIZE_FORMAT " tabulated by worker %u",
                    _old_to_region->index(), _old_compact_point - _old_to_region->bottom(), _worker_id);
      _old_to_region->set_new_top(_old_compact_point);
      _old_to_region = nullptr;
    }
  }

  void finish_young_region() {
    if (_young_to_region != nullptr) {
      log_debug(gc)("Worker %u planned compaction into Young Region " SIZE_FORMAT ", used: " SIZE_FORMAT,
                    _worker_id, _young_to_region->index(), _young_compact_point - _young_to_region->bottom());
      _young_to_region->set_new_top(_young_compact_point);
      _young_to_region = nullptr;
    }
  }

  bool is_compact_same_region() {
    return (_from_region == _old_to_region) || (_from_region == _young_to_region);
  }

  int empty_regions_pos() {
    return _empty_regions_pos;
  }

  void do_object(oop p) {
    assert(_from_region != nullptr, "must set before work");
    assert((_from_region->bottom() <= cast_from_oop<HeapWord*>(p)) && (cast_from_oop<HeapWord*>(p) < _from_region->top()),
           "Object must reside in _from_region");
    assert(_heap->complete_marking_context()->is_marked(p), "must be marked");
    assert(!_heap->complete_marking_context()->allocated_after_mark_start(p), "must be truly marked");

    size_t obj_size = p->size();
    uint from_region_age = _from_region->age();
    uint object_age = p->age();

    bool promote_object = false;
    if ((_from_affiliation == ShenandoahAffiliation::YOUNG_GENERATION) &&
        (from_region_age + object_age >= _tenuring_threshold)) {
      if ((_old_to_region != nullptr) && (_old_compact_point + obj_size > _old_to_region->end())) {
        finish_old_region();
        _old_to_region = nullptr;
      }
      if (_old_to_region == nullptr) {
        if (_empty_regions_pos < _empty_regions.length()) {
          ShenandoahHeapRegion* new_to_region = _empty_regions.at(_empty_regions_pos);
          _empty_regions_pos++;
          new_to_region->set_affiliation(OLD_GENERATION);
          _old_to_region = new_to_region;
          _old_compact_point = _old_to_region->bottom();
          promote_object = true;
        }
        // Else this worker thread does not yet have any empty regions into which this aged object can be promoted so
        // we leave promote_object as false, deferring the promotion.
      } else {
        promote_object = true;
      }
    }

    if (promote_object || (_from_affiliation == ShenandoahAffiliation::OLD_GENERATION)) {
      assert(_old_to_region != nullptr, "_old_to_region should not be nullptr when evacuating to OLD region");
      if (_old_compact_point + obj_size > _old_to_region->end()) {
        ShenandoahHeapRegion* new_to_region;

        log_debug(gc)("Worker %u finishing old region " SIZE_FORMAT ", compact_point: " PTR_FORMAT ", obj_size: " SIZE_FORMAT
                      ", &compact_point[obj_size]: " PTR_FORMAT ", region end: " PTR_FORMAT,  _worker_id, _old_to_region->index(),
                      p2i(_old_compact_point), obj_size, p2i(_old_compact_point + obj_size), p2i(_old_to_region->end()));

        // Object does not fit.  Get a new _old_to_region.
        finish_old_region();
        if (_empty_regions_pos < _empty_regions.length()) {
          new_to_region = _empty_regions.at(_empty_regions_pos);
          _empty_regions_pos++;
          new_to_region->set_affiliation(OLD_GENERATION);
        } else {
          // If we've exhausted the previously selected _old_to_region, we know that the _old_to_region is distinct
          // from _from_region.  That's because there is always room for _from_region to be compacted into itself.
          // Since we're out of empty regions, let's use _from_region to hold the results of its own compaction.
          new_to_region = _from_region;
        }

        assert(new_to_region != _old_to_region, "must not reuse same OLD to-region");
        assert(new_to_region != nullptr, "must not be nullptr");
        _old_to_region = new_to_region;
        _old_compact_point = _old_to_region->bottom();
      }

      // Object fits into current region, record new location:
      assert(_old_compact_point + obj_size <= _old_to_region->end(), "must fit");
      shenandoah_assert_not_forwarded(nullptr, p);
      _preserved_marks->push_if_necessary(p, p->mark());
      p->forward_to(cast_to_oop(_old_compact_point));
      _old_compact_point += obj_size;
    } else {
      assert(_from_affiliation == ShenandoahAffiliation::YOUNG_GENERATION,
             "_from_region must be OLD_GENERATION or YOUNG_GENERATION");
      assert(_young_to_region != nullptr, "_young_to_region should not be nullptr when compacting YOUNG _from_region");

      // After full gc compaction, all regions have age 0.  Embed the region's age into the object's age in order to preserve
      // tenuring progress.
      if (_heap->is_aging_cycle()) {
        _heap->increase_object_age(p, from_region_age + 1);
      } else {
        _heap->increase_object_age(p, from_region_age);
      }

      if (_young_compact_point + obj_size > _young_to_region->end()) {
        ShenandoahHeapRegion* new_to_region;

        log_debug(gc)("Worker %u finishing young region " SIZE_FORMAT ", compact_point: " PTR_FORMAT ", obj_size: " SIZE_FORMAT
                      ", &compact_point[obj_size]: " PTR_FORMAT ", region end: " PTR_FORMAT,  _worker_id, _young_to_region->index(),
                      p2i(_young_compact_point), obj_size, p2i(_young_compact_point + obj_size), p2i(_young_to_region->end()));

        // Object does not fit.  Get a new _young_to_region.
        finish_young_region();
        if (_empty_regions_pos < _empty_regions.length()) {
          new_to_region = _empty_regions.at(_empty_regions_pos);
          _empty_regions_pos++;
          new_to_region->set_affiliation(YOUNG_GENERATION);
        } else {
          // If we've exhausted the previously selected _young_to_region, we know that the _young_to_region is distinct
          // from _from_region.  That's because there is always room for _from_region to be compacted into itself.
          // Since we're out of empty regions, let's use _from_region to hold the results of its own compaction.
          new_to_region = _from_region;
        }

        assert(new_to_region != _young_to_region, "must not reuse same OLD to-region");
        assert(new_to_region != nullptr, "must not be nullptr");
        _young_to_region = new_to_region;
        _young_compact_point = _young_to_region->bottom();
      }

      // Object fits into current region, record new location:
      assert(_young_compact_point + obj_size <= _young_to_region->end(), "must fit");
      shenandoah_assert_not_forwarded(nullptr, p);
      _preserved_marks->push_if_necessary(p, p->mark());
      p->forward_to(cast_to_oop(_young_compact_point));
      _young_compact_point += obj_size;
    }
  }
};


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

  void finish_region() {
    assert(_to_region != nullptr, "should not happen");
    assert(!_heap->mode()->is_generational(), "Generational GC should use different Closure");
    _to_region->set_new_top(_compact_point);
  }

  bool is_compact_same_region() {
    return _from_region == _to_region;
  }

  int empty_regions_pos() {
    return _empty_regions_pos;
  }

  void do_object(oop p) {
    assert(_from_region != nullptr, "must set before work");
    assert(_heap->complete_marking_context()->is_marked(p), "must be marked");
    assert(!_heap->complete_marking_context()->allocated_after_mark_start(p), "must be truly marked");

    size_t obj_size = p->size();
    if (_compact_point + obj_size > _to_region->end()) {
      finish_region();

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

    // Object fits into current region, record new location:
    assert(_compact_point + obj_size <= _to_region->end(), "must fit");
    shenandoah_assert_not_forwarded(nullptr, p);
    _preserved_marks->push_if_necessary(p, p->mark());
    p->forward_to(cast_to_oop(_compact_point));
    _compact_point += obj_size;
  }
};


ShenandoahPrepareForCompactionTask::ShenandoahPrepareForCompactionTask(PreservedMarksSet *preserved_marks,
                                                                       ShenandoahHeapRegionSet **worker_slices,
                                                                       size_t num_workers) :
    WorkerTask("Shenandoah Prepare For Compaction"),
    _preserved_marks(preserved_marks), _heap(ShenandoahHeap::heap()),
    _worker_slices(worker_slices), _num_workers(num_workers) { }


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
    ShenandoahHeapRegion* old_to_region = (from_region->is_old())? from_region: nullptr;
    ShenandoahHeapRegion* young_to_region = (from_region->is_young())? from_region: nullptr;
    ShenandoahPrepareForGenerationalCompactionObjectClosure cl(_preserved_marks->get(worker_id),
                                                               empty_regions,
                                                               old_to_region, young_to_region,
                                                               worker_id);
    while (from_region != nullptr) {
      assert(is_candidate_region(from_region), "Sanity");
      log_debug(gc)("Worker %u compacting %s Region " SIZE_FORMAT " which had used " SIZE_FORMAT " and %s live",
                    worker_id, from_region->affiliation_name(),
                    from_region->index(), from_region->used(), from_region->has_live()? "has": "does not have");
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
  } else {
    ShenandoahPrepareForCompactionObjectClosure cl(_preserved_marks->get(worker_id), empty_regions, from_region);
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
    cl.finish_region();

    // Mark all remaining regions as empty
    for (int pos = cl.empty_regions_pos(); pos < empty_regions.length(); ++pos) {
      ShenandoahHeapRegion* r = empty_regions.at(pos);
      r->set_new_top(r->bottom());
    }
  }
}

void ShenandoahFullGC::calculate_target_humongous_objects() {
  ShenandoahHeap* heap = ShenandoahHeap::heap();

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

  size_t to_begin = heap->num_regions();
  size_t to_end = heap->num_regions();

  log_debug(gc)("Full GC calculating target humongous objects from end " SIZE_FORMAT, to_end);
  for (size_t c = heap->num_regions(); c > 0; c--) {
    ShenandoahHeapRegion *r = heap->get_region(c - 1);
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
        old_obj->forward_to(cast_to_oop(heap->get_region(start)->bottom()));
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
private:
  ShenandoahHeap* const _heap;

public:
  ShenandoahEnsureHeapActiveClosure() : _heap(ShenandoahHeap::heap()) {}
  void heap_region_do(ShenandoahHeapRegion* r) {
    if (r->is_trash()) {
      r->recycle();
    }
    if (r->is_cset()) {
      // Leave affiliation unchanged
      r->make_regular_bypass();
    }
    if (r->is_empty_uncommitted()) {
      r->make_committed_bypass();
    }
    assert (r->is_committed(), "only committed regions in heap now, see region " SIZE_FORMAT, r->index());

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
    _ctx(ShenandoahHeap::heap()->complete_marking_context()) {}

  void heap_region_do(ShenandoahHeapRegion* r) {
    if (!r->is_affiliated()) {
      // Ignore free regions
      // TODO: change iterators so they do not process FREE regions.
      return;
    }

    if (r->is_humongous_start()) {
      oop humongous_obj = cast_to_oop(r->bottom());
      if (!_ctx->is_marked(humongous_obj)) {
        assert(!r->has_live(),
               "Humongous Start %s Region " SIZE_FORMAT " is not marked, should not have live",
               r->affiliation_name(),  r->index());
        log_debug(gc)("Trashing immediate humongous region " SIZE_FORMAT " because not marked", r->index());
        _heap->trash_humongous_region_at(r);
      } else {
        assert(r->has_live(),
               "Humongous Start %s Region " SIZE_FORMAT " should have live", r->affiliation_name(),  r->index());
      }
    } else if (r->is_humongous_continuation()) {
      // If we hit continuation, the non-live humongous starts should have been trashed already
      assert(r->humongous_start_region()->has_live(),
             "Humongous Continuation %s Region " SIZE_FORMAT " should have live", r->affiliation_name(),  r->index());
    } else if (r->is_regular()) {
      if (!r->has_live()) {
        log_debug(gc)("Trashing immediate regular region " SIZE_FORMAT " because has no live", r->index());
        r->make_trash_immediate();
      }
    }
  }
};

void ShenandoahFullGC::distribute_slices(ShenandoahHeapRegionSet** worker_slices) {
  ShenandoahHeap* heap = ShenandoahHeap::heap();

  uint n_workers = heap->workers()->active_workers();
  size_t n_regions = heap->num_regions();

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
    ShenandoahHeapRegion *r = heap->get_region(idx);
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
    ShenandoahHeapRegion *r = heap->get_region(idx);
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
      ShenandoahHeapRegion *r = heap->get_region(prefix_idx);
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
    ShenandoahHeapRegion *r = heap->get_region(tail_idx);
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

  FREE_C_HEAP_ARRAY(size_t, live);

#ifdef ASSERT
  ResourceBitMap map(n_regions);
  for (size_t wid = 0; wid < n_workers; wid++) {
    ShenandoahHeapRegionSetIterator it(worker_slices[wid]);
    ShenandoahHeapRegion* r = it.next();
    while (r != nullptr) {
      size_t idx = r->index();
      assert(ShenandoahPrepareForCompactionTask::is_candidate_region(r), "Sanity: " SIZE_FORMAT, idx);
      assert(!map.at(idx), "No region distributed twice: " SIZE_FORMAT, idx);
      map.at_put(idx, true);
      r = it.next();
    }
  }

  for (size_t rid = 0; rid < n_regions; rid++) {
    bool is_candidate = ShenandoahPrepareForCompactionTask::is_candidate_region(heap->get_region(rid));
    bool is_distributed = map.at(rid);
    assert(is_distributed || !is_candidate, "All candidates are distributed: " SIZE_FORMAT, rid);
  }
#endif
}

// TODO:
//  Consider compacting old-gen objects toward the high end of memory and young-gen objects towards the low-end
//  of memory.  As currently implemented, all regions are compacted toward the low-end of memory.  This creates more
//  fragmentation of the heap, because old-gen regions get scattered among low-address regions such that it becomes
//  more difficult to find contiguous regions for humongous objects.
void ShenandoahFullGC::phase2_calculate_target_addresses(ShenandoahHeapRegionSet** worker_slices) {
  GCTraceTime(Info, gc, phases) time("Phase 2: Compute new object addresses", _gc_timer);
  ShenandoahGCPhase calculate_address_phase(ShenandoahPhaseTimings::full_gc_calculate_addresses);

  ShenandoahHeap* heap = ShenandoahHeap::heap();

  // About to figure out which regions can be compacted, make sure pinning status
  // had been updated in GC prologue.
  heap->assert_pinned_region_status();

  {
    // Trash the immediately collectible regions before computing addresses
    ShenandoahTrashImmediateGarbageClosure tigcl;
    heap->heap_region_iterate(&tigcl);

    // Make sure regions are in good state: committed, active, clean.
    // This is needed because we are potentially sliding the data through them.
    ShenandoahEnsureHeapActiveClosure ecl;
    heap->heap_region_iterate(&ecl);
  }

  // Compute the new addresses for regular objects
  {
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::full_gc_calculate_addresses_regular);

    distribute_slices(worker_slices);

    size_t num_workers = heap->max_workers();

    ResourceMark rm;
    ShenandoahPrepareForCompactionTask task(_preserved_marks, worker_slices, num_workers);
    heap->workers()->run_task(&task);
  }

  // Compute the new addresses for humongous objects
  {
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::full_gc_calculate_addresses_humong);
    calculate_target_humongous_objects();
  }
}

class ShenandoahAdjustPointersClosure : public MetadataVisitingOopIterateClosure {
private:
  ShenandoahHeap* const _heap;
  ShenandoahMarkingContext* const _ctx;

  template <class T>
  inline void do_oop_work(T* p) {
    T o = RawAccess<>::oop_load(p);
    if (!CompressedOops::is_null(o)) {
      oop obj = CompressedOops::decode_not_null(o);
      assert(_ctx->is_marked(obj), "must be marked");
      if (obj->is_forwarded()) {
        oop forw = obj->forwardee();
        RawAccess<IS_NOT_NULL>::oop_store(p, forw);
      }
    }
  }

public:
  ShenandoahAdjustPointersClosure() :
    _heap(ShenandoahHeap::heap()),
    _ctx(ShenandoahHeap::heap()->complete_marking_context()) {}

  void do_oop(oop* p)       { do_oop_work(p); }
  void do_oop(narrowOop* p) { do_oop_work(p); }
  void do_method(Method* m) {}
  void do_nmethod(nmethod* nm) {}
};

class ShenandoahAdjustPointersObjectClosure : public ObjectClosure {
private:
  ShenandoahHeap* const _heap;
  ShenandoahAdjustPointersClosure _cl;

public:
  ShenandoahAdjustPointersObjectClosure() :
    _heap(ShenandoahHeap::heap()) {
  }
  void do_object(oop p) {
    assert(_heap->complete_marking_context()->is_marked(p), "must be marked");
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

  void work(uint worker_id) {
    ShenandoahParallelWorkerSession worker_session(worker_id);
    ShenandoahAdjustPointersObjectClosure obj_cl;
    ShenandoahHeapRegion* r = _regions.next();
    while (r != nullptr) {
      if (!r->is_humongous_continuation() && r->has_live()) {
        _heap->marked_object_iterate(r, &obj_cl);
      }
      if (r->is_pinned() && r->is_old() && r->is_active() && !r->is_humongous()) {
        // Pinned regions are not compacted so they may still hold unmarked objects with
        // reference to reclaimed memory. Remembered set scanning will crash if it attempts
        // to iterate the oops in these objects.
        r->begin_preemptible_coalesce_and_fill();
        r->oop_fill_and_coalesce_without_cancel();
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

  void work(uint worker_id) {
    ShenandoahParallelWorkerSession worker_session(worker_id);
    ShenandoahAdjustPointersClosure cl;
    _rp->roots_do(worker_id, &cl);
    _preserved_marks->get(worker_id)->adjust_during_full_gc();
  }
};

void ShenandoahFullGC::phase3_update_references() {
  GCTraceTime(Info, gc, phases) time("Phase 3: Adjust pointers", _gc_timer);
  ShenandoahGCPhase adjust_pointer_phase(ShenandoahPhaseTimings::full_gc_adjust_pointers);

  ShenandoahHeap* heap = ShenandoahHeap::heap();

  WorkerThreads* workers = heap->workers();
  uint nworkers = workers->active_workers();
  {
#if COMPILER2_OR_JVMCI
    DerivedPointerTable::clear();
#endif
    ShenandoahRootAdjuster rp(nworkers, ShenandoahPhaseTimings::full_gc_adjust_roots);
    ShenandoahAdjustRootPointersTask task(&rp, _preserved_marks);
    workers->run_task(&task);
#if COMPILER2_OR_JVMCI
    DerivedPointerTable::update_pointers();
#endif
  }

  ShenandoahAdjustPointersTask adjust_pointers_task;
  workers->run_task(&adjust_pointers_task);
}

class ShenandoahCompactObjectsClosure : public ObjectClosure {
private:
  ShenandoahHeap* const _heap;
  uint            const _worker_id;

public:
  ShenandoahCompactObjectsClosure(uint worker_id) :
    _heap(ShenandoahHeap::heap()), _worker_id(worker_id) {}

  void do_object(oop p) {
    assert(_heap->complete_marking_context()->is_marked(p), "must be marked");
    size_t size = p->size();
    if (p->is_forwarded()) {
      HeapWord* compact_from = cast_from_oop<HeapWord*>(p);
      HeapWord* compact_to = cast_from_oop<HeapWord*>(p->forwardee());
      Copy::aligned_conjoint_words(compact_from, compact_to, size);
      oop new_obj = cast_to_oop(compact_to);

      ContinuationGCSupport::relativize_stack_chunk(new_obj);
      new_obj->init_mark();
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

  void work(uint worker_id) {
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

static void account_for_region(ShenandoahHeapRegion* r, size_t &region_count, size_t &region_usage, size_t &humongous_waste) {
  region_count++;
  region_usage += r->used();
  if (r->is_humongous_start()) {
    // For each humongous object, we take this path once regardless of how many regions it spans.
    HeapWord* obj_addr = r->bottom();
    oop obj = cast_to_oop(obj_addr);
    size_t word_size = obj->size();
    size_t region_size_words = ShenandoahHeapRegion::region_size_words();
    size_t overreach = word_size % region_size_words;
    if (overreach != 0) {
      humongous_waste += (region_size_words - overreach) * HeapWordSize;
    }
    // else, this humongous object aligns exactly on region size, so no waste.
  }
}

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

  void heap_region_do(ShenandoahHeapRegion* r) {
    assert (!r->is_cset(), "cset regions should have been demoted already");

    // Need to reset the complete-top-at-mark-start pointer here because
    // the complete marking bitmap is no longer valid. This ensures
    // size-based iteration in marked_object_iterate().
    // NOTE: See blurb at ShenandoahMCResetCompleteBitmapTask on why we need to skip
    // pinned regions.
    if (!r->is_pinned()) {
      _heap->complete_marking_context()->reset_top_at_mark_start(r);
    }

    size_t live = r->used();

    // Make empty regions that have been allocated into regular
    if (r->is_empty() && live > 0) {
      if (!_is_generational) {
        r->make_young_maybe();
      }
      // else, generational mode compaction has already established affiliation.
      r->make_regular_bypass();
    }

    // Reclaim regular regions that became empty
    if (r->is_regular() && live == 0) {
      r->make_trash();
    }

    // Recycle all trash regions
    if (r->is_trash()) {
      live = 0;
      r->recycle();
    } else {
      if (r->is_old()) {
        account_for_region(r, _old_regions, _old_usage, _old_humongous_waste);
      } else if (r->is_young()) {
        account_for_region(r, _young_regions, _young_usage, _young_humongous_waste);
      }
    }
    r->set_live_data(live);
    r->reset_alloc_metadata();
  }

  void update_generation_usage() {
    if (_is_generational) {
      _heap->old_generation()->establish_usage(_old_regions, _old_usage, _old_humongous_waste);
      _heap->young_generation()->establish_usage(_young_regions, _young_usage, _young_humongous_waste);
    } else {
      assert(_old_regions == 0, "Old regions only expected in generational mode");
      assert(_old_usage == 0, "Old usage only expected in generational mode");
      assert(_old_humongous_waste == 0, "Old humongous waste only expected in generational mode");
    }

    // In generational mode, global usage should be the sum of young and old. This is also true
    // for non-generational modes except that there are no old regions.
    _heap->global_generation()->establish_usage(_old_regions + _young_regions,
                                                _old_usage + _young_usage,
                                                _old_humongous_waste + _young_humongous_waste);
  }
};

void ShenandoahFullGC::compact_humongous_objects() {
  // Compact humongous regions, based on their fwdptr objects.
  //
  // This code is serial, because doing the in-slice parallel sliding is tricky. In most cases,
  // humongous regions are already compacted, and do not require further moves, which alleviates
  // sliding costs. We may consider doing this in parallel in the future.

  ShenandoahHeap* heap = ShenandoahHeap::heap();

  for (size_t c = heap->num_regions(); c > 0; c--) {
    ShenandoahHeapRegion* r = heap->get_region(c - 1);
    if (r->is_humongous_start()) {
      oop old_obj = cast_to_oop(r->bottom());
      if (!old_obj->is_forwarded()) {
        // No need to move the object, it stays at the same slot
        continue;
      }
      size_t words_size = old_obj->size();
      size_t num_regions = ShenandoahHeapRegion::required_regions(words_size * HeapWordSize);

      size_t old_start = r->index();
      size_t old_end   = old_start + num_regions - 1;
      size_t new_start = heap->heap_region_index_containing(old_obj->forwardee());
      size_t new_end   = new_start + num_regions - 1;
      assert(old_start != new_start, "must be real move");
      assert(r->is_stw_move_allowed(), "Region " SIZE_FORMAT " should be movable", r->index());

      ContinuationGCSupport::relativize_stack_chunk(cast_to_oop<HeapWord*>(heap->get_region(old_start)->bottom()));
      log_debug(gc)("Full GC compaction moves humongous object from region " SIZE_FORMAT " to region " SIZE_FORMAT,
                    old_start, new_start);

      Copy::aligned_conjoint_words(heap->get_region(old_start)->bottom(),
                                   heap->get_region(new_start)->bottom(),
                                   words_size);

      oop new_obj = cast_to_oop(heap->get_region(new_start)->bottom());
      new_obj->init_mark();

      {
        ShenandoahAffiliation original_affiliation = r->affiliation();
        for (size_t c = old_start; c <= old_end; c++) {
          ShenandoahHeapRegion* r = heap->get_region(c);
          // Leave humongous region affiliation unchanged.
          r->make_regular_bypass();
          r->set_top(r->bottom());
        }

        for (size_t c = new_start; c <= new_end; c++) {
          ShenandoahHeapRegion* r = heap->get_region(c);
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
// cannot be iterated over using oop->size(). The only way to safely iterate over those is using
// a valid marking bitmap and valid TAMS pointer. This class only resets marking
// bitmaps for un-pinned regions, and later we only reset TAMS for unpinned regions.
class ShenandoahMCResetCompleteBitmapTask : public WorkerTask {
private:
  ShenandoahRegionIterator _regions;

public:
  ShenandoahMCResetCompleteBitmapTask() :
    WorkerTask("Shenandoah Reset Bitmap") {
  }

  void work(uint worker_id) {
    ShenandoahParallelWorkerSession worker_session(worker_id);
    ShenandoahHeapRegion* region = _regions.next();
    ShenandoahHeap* heap = ShenandoahHeap::heap();
    ShenandoahMarkingContext* const ctx = heap->complete_marking_context();
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

  ShenandoahHeap* heap = ShenandoahHeap::heap();

  // Compact regular objects first
  {
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::full_gc_copy_objects_regular);
    ShenandoahCompactObjectsTask compact_task(worker_slices);
    heap->workers()->run_task(&compact_task);
  }

  // Compact humongous objects after regular object moves
  {
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::full_gc_copy_objects_humong);
    compact_humongous_objects();
  }
}

void ShenandoahFullGC::phase5_epilog() {
  GCTraceTime(Info, gc, phases) time("Phase 5: Full GC epilog", _gc_timer);
  ShenandoahHeap* heap = ShenandoahHeap::heap();

  // Reset complete bitmap. We're about to reset the complete-top-at-mark-start pointer
  // and must ensure the bitmap is in sync.
  {
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::full_gc_copy_objects_reset_complete);
    ShenandoahMCResetCompleteBitmapTask task;
    heap->workers()->run_task(&task);
  }

  // Bring regions in proper states after the collection, and set heap properties.
  {
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::full_gc_copy_objects_rebuild);
    ShenandoahPostCompactClosure post_compact;
    heap->heap_region_iterate(&post_compact);
    post_compact.update_generation_usage();
    if (heap->mode()->is_generational()) {
      size_t old_usage = heap->old_generation()->used_regions_size();
      size_t old_capacity = heap->old_generation()->max_capacity();

      assert(old_usage % ShenandoahHeapRegion::region_size_bytes() == 0, "Old usage must aligh with region size");
      assert(old_capacity % ShenandoahHeapRegion::region_size_bytes() == 0, "Old capacity must aligh with region size");

      if (old_capacity > old_usage) {
        size_t excess_old_regions = (old_capacity - old_usage) / ShenandoahHeapRegion::region_size_bytes();
        heap->generation_sizer()->transfer_to_young(excess_old_regions);
      } else if (old_capacity < old_usage) {
        size_t old_regions_deficit = (old_usage - old_capacity) / ShenandoahHeapRegion::region_size_bytes();
        heap->generation_sizer()->force_transfer_to_old(old_regions_deficit);
      }

      log_info(gc)("FullGC done: young usage: " SIZE_FORMAT "%s, old usage: " SIZE_FORMAT "%s",
                   byte_size_in_proper_unit(heap->young_generation()->used()), proper_unit_for_byte_size(heap->young_generation()->used()),
                   byte_size_in_proper_unit(heap->old_generation()->used()),   proper_unit_for_byte_size(heap->old_generation()->used()));
    }
    heap->collection_set()->clear();
    size_t young_cset_regions, old_cset_regions;
    heap->free_set()->prepare_to_rebuild(young_cset_regions, old_cset_regions);

    // We also do not expand old generation size following Full GC because we have scrambled age populations and
    // no longer have objects separated by age into distinct regions.

    // TODO: Do we need to fix FullGC so that it maintains aged segregation of objects into distinct regions?
    //       A partial solution would be to remember how many objects are of tenure age following Full GC, but
    //       this is probably suboptimal, because most of these objects will not reside in a region that will be
    //       selected for the next evacuation phase.

    // In case this Full GC resulted from degeneration, clear the tally on anticipated promotion.
    heap->clear_promotion_potential();

    if (heap->mode()->is_generational()) {
      // Invoke this in case we are able to transfer memory from OLD to YOUNG.
      heap->adjust_generation_sizes_for_next_cycle(0, 0, 0);
    }
    heap->free_set()->rebuild(young_cset_regions, old_cset_regions);

    // We defer generation resizing actions until after cset regions have been recycled.  We do this even following an
    // abbreviated cycle.
    if (heap->mode()->is_generational()) {
      bool success;
      size_t region_xfer;
      const char* region_destination;
      ShenandoahYoungGeneration* young_gen = heap->young_generation();
      ShenandoahGeneration* old_gen = heap->old_generation();

      size_t old_region_surplus = heap->get_old_region_surplus();
      size_t old_region_deficit = heap->get_old_region_deficit();
      if (old_region_surplus) {
        success = heap->generation_sizer()->transfer_to_young(old_region_surplus);
        region_destination = "young";
        region_xfer = old_region_surplus;
      } else if (old_region_deficit) {
        success = heap->generation_sizer()->transfer_to_old(old_region_deficit);
        region_destination = "old";
        region_xfer = old_region_deficit;
        if (!success) {
          ((ShenandoahOldHeuristics *) old_gen->heuristics())->trigger_cannot_expand();
        }
      } else {
        region_destination = "none";
        region_xfer = 0;
        success = true;
      }
      heap->set_old_region_surplus(0);
      heap->set_old_region_deficit(0);
      size_t young_available = young_gen->available();
      size_t old_available = old_gen->available();
      log_info(gc, ergo)("After cleanup, %s " SIZE_FORMAT " regions to %s to prepare for next gc, old available: "
                         SIZE_FORMAT "%s, young_available: " SIZE_FORMAT "%s",
                         success? "successfully transferred": "failed to transfer", region_xfer, region_destination,
                         byte_size_in_proper_unit(old_available), proper_unit_for_byte_size(old_available),
                         byte_size_in_proper_unit(young_available), proper_unit_for_byte_size(young_available));
    }
    heap->clear_cancelled_gc(true /* clear oom handler */);
  }
}
