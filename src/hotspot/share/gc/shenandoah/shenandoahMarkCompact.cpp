/*
 * Copyright (c) 2014, 2019, Red Hat, Inc. All rights reserved.
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

#include "code/codeCache.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shenandoah/shenandoahBrooksPointer.hpp"
#include "gc/shenandoah/shenandoahConcurrentMark.inline.hpp"
#include "gc/shenandoah/shenandoahCollectionSet.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shenandoah/shenandoahMarkCompact.hpp"
#include "gc/shenandoah/shenandoahHeapRegionSet.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeuristics.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.inline.hpp"
#include "gc/shenandoah/shenandoahRootProcessor.inline.hpp"
#include "gc/shenandoah/shenandoahTraversalGC.hpp"
#include "gc/shenandoah/shenandoahTaskqueue.inline.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "gc/shenandoah/shenandoahVerifier.hpp"
#include "gc/shenandoah/shenandoahVMOperations.hpp"
#include "gc/shenandoah/shenandoahWorkerPolicy.hpp"
#include "memory/metaspace.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/thread.hpp"
#include "utilities/copy.hpp"
#include "utilities/growableArray.hpp"
#include "gc/shared/workgroup.hpp"

void ShenandoahMarkCompact::initialize(GCTimer* gc_timer) {
  _gc_timer = gc_timer;
}

void ShenandoahMarkCompact::do_it(GCCause::Cause gc_cause) {
  ShenandoahHeap* heap = ShenandoahHeap::heap();

  if (ShenandoahVerify) {
    heap->verifier()->verify_before_fullgc();
  }

  if (VerifyBeforeGC) {
    Universe::verify();
  }

  heap->set_full_gc_in_progress(true);

  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "must be at a safepoint");
  assert(Thread::current()->is_VM_thread(), "Do full GC only while world is stopped");

  {
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::full_gc_heapdumps);
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

    // a3. Cancel concurrent traversal GC, if in progress
    if (heap->is_concurrent_traversal_in_progress()) {
      heap->traversal_gc()->reset();
      heap->set_concurrent_traversal_in_progress(false);
    }

    // b. Cancel concurrent mark, if in progress
    if (heap->is_concurrent_mark_in_progress()) {
      heap->concurrent_mark()->cancel();
      heap->stop_concurrent_marking();
    }
    assert(!heap->is_concurrent_mark_in_progress(), "sanity");

    // c. Reset the bitmaps for new marking
    heap->reset_mark_bitmap();
    assert(heap->marking_context()->is_bitmap_clear(), "sanity");
    assert(!heap->marking_context()->is_complete(), "sanity");

    // d. Abandon reference discovery and clear all discovered references.
    ReferenceProcessor* rp = heap->ref_processor();
    rp->disable_discovery();
    rp->abandon_partial_discovery();
    rp->verify_no_references_recorded();

    // e. Set back forwarded objects bit back, in case some steps above dropped it.
    heap->set_has_forwarded_objects(has_forwarded_objects);
  }

  heap->make_parsable(true);

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
  }

  // Resize metaspace
  MetaspaceGC::compute_new_size();

  // Free worker slices
  for (uint i = 0; i < heap->max_workers(); i++) {
    delete worker_slices[i];
  }
  FREE_C_HEAP_ARRAY(ShenandoahHeapRegionSet*, worker_slices);

  JvmtiExport::gc_epilogue();

  heap->set_full_gc_move_in_progress(false);
  heap->set_full_gc_in_progress(false);

  if (ShenandoahVerify) {
    heap->verifier()->verify_after_fullgc();
  }

  if (VerifyAfterGC) {
    Universe::verify();
  }

  {
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::full_gc_heapdumps);
    heap->post_full_gc_dump(_gc_timer);
  }
}

class ShenandoahPrepareForMarkClosure: public ShenandoahHeapRegionClosure {
private:
  ShenandoahMarkingContext* const _ctx;

public:
  ShenandoahPrepareForMarkClosure() : _ctx(ShenandoahHeap::heap()->marking_context()) {}

  void heap_region_do(ShenandoahHeapRegion *r) {
    _ctx->capture_top_at_mark_start(r);
    r->clear_live_data();
    r->set_concurrent_iteration_safe_limit(r->top());
  }
};

void ShenandoahMarkCompact::phase1_mark_heap() {
  GCTraceTime(Info, gc, phases) time("Phase 1: Mark live objects", _gc_timer);
  ShenandoahGCPhase mark_phase(ShenandoahPhaseTimings::full_gc_mark);

  ShenandoahHeap* heap = ShenandoahHeap::heap();

  ShenandoahPrepareForMarkClosure cl;
  heap->heap_region_iterate(&cl);

  ShenandoahConcurrentMark* cm = heap->concurrent_mark();

  heap->set_process_references(heap->heuristics()->can_process_references());
  heap->set_unload_classes(heap->heuristics()->can_unload_classes());

  ReferenceProcessor* rp = heap->ref_processor();
  // enable ("weak") refs discovery
  rp->enable_discovery(true /*verify_no_refs*/);
  rp->setup_policy(true); // forcefully purge all soft references
  rp->set_active_mt_degree(heap->workers()->active_workers());

  cm->update_roots(ShenandoahPhaseTimings::full_gc_roots);
  cm->mark_roots(ShenandoahPhaseTimings::full_gc_roots);
  cm->finish_mark_from_roots(/* full_gc = */ true);

  heap->mark_complete_marking_context();
}

class ShenandoahPrepareForCompactionObjectClosure : public ObjectClosure {
private:
  ShenandoahHeap*          const _heap;
  GrowableArray<ShenandoahHeapRegion*>& _empty_regions;
  int _empty_regions_pos;
  ShenandoahHeapRegion*          _to_region;
  ShenandoahHeapRegion*          _from_region;
  HeapWord* _compact_point;

public:
  ShenandoahPrepareForCompactionObjectClosure(GrowableArray<ShenandoahHeapRegion*>& empty_regions, ShenandoahHeapRegion* to_region) :
    _heap(ShenandoahHeap::heap()),
    _empty_regions(empty_regions),
    _empty_regions_pos(0),
    _to_region(to_region),
    _from_region(NULL),
    _compact_point(to_region->bottom()) {}

  void set_from_region(ShenandoahHeapRegion* from_region) {
    _from_region = from_region;
  }

  void finish_region() {
    assert(_to_region != NULL, "should not happen");
    _to_region->set_new_top(_compact_point);
  }

  bool is_compact_same_region() {
    return _from_region == _to_region;
  }

  int empty_regions_pos() {
    return _empty_regions_pos;
  }

  void do_object(oop p) {
    assert(_from_region != NULL, "must set before work");
    assert(_heap->complete_marking_context()->is_marked(p), "must be marked");
    assert(!_heap->complete_marking_context()->allocated_after_mark_start((HeapWord*) p), "must be truly marked");

    size_t obj_size = p->size() + ShenandoahBrooksPointer::word_size();
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
      assert(new_to_region != NULL, "must not be NULL");
      _to_region = new_to_region;
      _compact_point = _to_region->bottom();
    }

    // Object fits into current region, record new location:
    assert(_compact_point + obj_size <= _to_region->end(), "must fit");
    shenandoah_assert_not_forwarded(NULL, p);
    ShenandoahBrooksPointer::set_raw(p, _compact_point + ShenandoahBrooksPointer::word_size());
    _compact_point += obj_size;
  }
};

class ShenandoahPrepareForCompactionTask : public AbstractGangTask {
private:
  ShenandoahHeap*           const _heap;
  ShenandoahHeapRegionSet** const _worker_slices;
  ShenandoahRegionIterator        _heap_regions;

  ShenandoahHeapRegion* next_from_region(ShenandoahHeapRegionSet* slice) {
    ShenandoahHeapRegion* from_region = _heap_regions.next();

    while (from_region != NULL && (!from_region->is_move_allowed() || from_region->is_humongous())) {
      from_region = _heap_regions.next();
    }

    if (from_region != NULL) {
      assert(slice != NULL, "sanity");
      assert(!from_region->is_humongous(), "this path cannot handle humongous regions");
      assert(from_region->is_move_allowed(), "only regions that can be moved in mark-compact");
      slice->add_region(from_region);
    }

    return from_region;
  }

public:
  ShenandoahPrepareForCompactionTask(ShenandoahHeapRegionSet** worker_slices) :
    AbstractGangTask("Shenandoah Prepare For Compaction Task"),
    _heap(ShenandoahHeap::heap()), _worker_slices(worker_slices) {
  }

  void work(uint worker_id) {
    ShenandoahHeapRegionSet* slice = _worker_slices[worker_id];
    ShenandoahHeapRegion* from_region = next_from_region(slice);
    // No work?
    if (from_region == NULL) {
      return;
    }

    // Sliding compaction. Walk all regions in the slice, and compact them.
    // Remember empty regions and reuse them as needed.
    ResourceMark rm;
    GrowableArray<ShenandoahHeapRegion*> empty_regions((int)_heap->num_regions());
    ShenandoahPrepareForCompactionObjectClosure cl(empty_regions, from_region);
    while (from_region != NULL) {
      cl.set_from_region(from_region);
      if (from_region->has_live()) {
        _heap->marked_object_iterate(from_region, &cl);
      }

      // Compacted the region to somewhere else? From-region is empty then.
      if (!cl.is_compact_same_region()) {
        empty_regions.append(from_region);
      }
      from_region = next_from_region(slice);
    }
    cl.finish_region();

    // Mark all remaining regions as empty
    for (int pos = cl.empty_regions_pos(); pos < empty_regions.length(); ++pos) {
      ShenandoahHeapRegion* r = empty_regions.at(pos);
      r->set_new_top(r->bottom());
    }
  }
};

void ShenandoahMarkCompact::calculate_target_humongous_objects() {
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

  for (size_t c = heap->num_regions() - 1; c > 0; c--) {
    ShenandoahHeapRegion *r = heap->get_region(c);
    if (r->is_humongous_continuation() || (r->new_top() == r->bottom())) {
      // To-region candidate: record this, and continue scan
      to_begin = r->region_number();
      continue;
    }

    if (r->is_humongous_start() && r->is_move_allowed()) {
      // From-region candidate: movable humongous region
      oop old_obj = oop(r->bottom() + ShenandoahBrooksPointer::word_size());
      size_t words_size = old_obj->size() + ShenandoahBrooksPointer::word_size();
      size_t num_regions = ShenandoahHeapRegion::required_regions(words_size * HeapWordSize);

      size_t start = to_end - num_regions;

      if (start >= to_begin && start != r->region_number()) {
        // Fits into current window, and the move is non-trivial. Record the move then, and continue scan.
        ShenandoahBrooksPointer::set_raw(old_obj, heap->get_region(start)->bottom() + ShenandoahBrooksPointer::word_size());
        to_end = start;
        continue;
      }
    }

    // Failed to fit. Scan starting from current region.
    to_begin = r->region_number();
    to_end = r->region_number();
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
      r->make_regular_bypass();
    }
    if (r->is_empty_uncommitted()) {
      r->make_committed_bypass();
    }
    assert (r->is_committed(), "only committed regions in heap now, see region " SIZE_FORMAT, r->region_number());

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
    if (r->is_humongous_start()) {
      oop humongous_obj = oop(r->bottom() + ShenandoahBrooksPointer::word_size());
      if (!_ctx->is_marked(humongous_obj)) {
        assert(!r->has_live(),
               "Region " SIZE_FORMAT " is not marked, should not have live", r->region_number());
        _heap->trash_humongous_region_at(r);
      } else {
        assert(r->has_live(),
               "Region " SIZE_FORMAT " should have live", r->region_number());
      }
    } else if (r->is_humongous_continuation()) {
      // If we hit continuation, the non-live humongous starts should have been trashed already
      assert(r->humongous_start_region()->has_live(),
             "Region " SIZE_FORMAT " should have live", r->region_number());
    } else if (r->is_regular()) {
      if (!r->has_live()) {
        r->make_trash_immediate();
      }
    }
  }
};

void ShenandoahMarkCompact::phase2_calculate_target_addresses(ShenandoahHeapRegionSet** worker_slices) {
  GCTraceTime(Info, gc, phases) time("Phase 2: Compute new object addresses", _gc_timer);
  ShenandoahGCPhase calculate_address_phase(ShenandoahPhaseTimings::full_gc_calculate_addresses);

  ShenandoahHeap* heap = ShenandoahHeap::heap();

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
    ShenandoahPrepareForCompactionTask prepare_task(worker_slices);
    heap->workers()->run_task(&prepare_task);
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
      oop forw = oop(ShenandoahBrooksPointer::get_raw(obj));
      RawAccess<IS_NOT_NULL>::oop_store(p, forw);
    }
  }

public:
  ShenandoahAdjustPointersClosure() :
    _heap(ShenandoahHeap::heap()),
    _ctx(ShenandoahHeap::heap()->complete_marking_context()) {}

  void do_oop(oop* p)       { do_oop_work(p); }
  void do_oop(narrowOop* p) { do_oop_work(p); }
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
    HeapWord* forw = ShenandoahBrooksPointer::get_raw(p);
    p->oop_iterate(&_cl);
  }
};

class ShenandoahAdjustPointersTask : public AbstractGangTask {
private:
  ShenandoahHeap*          const _heap;
  ShenandoahRegionIterator       _regions;

public:
  ShenandoahAdjustPointersTask() :
    AbstractGangTask("Shenandoah Adjust Pointers Task"),
    _heap(ShenandoahHeap::heap()) {
  }

  void work(uint worker_id) {
    ShenandoahAdjustPointersObjectClosure obj_cl;
    ShenandoahHeapRegion* r = _regions.next();
    while (r != NULL) {
      if (!r->is_humongous_continuation() && r->has_live()) {
        _heap->marked_object_iterate(r, &obj_cl);
      }
      r = _regions.next();
    }
  }
};

class ShenandoahAdjustRootPointersTask : public AbstractGangTask {
private:
  ShenandoahRootProcessor* _rp;

public:
  ShenandoahAdjustRootPointersTask(ShenandoahRootProcessor* rp) :
    AbstractGangTask("Shenandoah Adjust Root Pointers Task"),
    _rp(rp) {}

  void work(uint worker_id) {
    ShenandoahAdjustPointersClosure cl;
    CLDToOopClosure adjust_cld_closure(&cl, ClassLoaderData::_claim_strong);
    MarkingCodeBlobClosure adjust_code_closure(&cl,
                                             CodeBlobToOopClosure::FixRelocations);

    _rp->update_all_roots<AlwaysTrueClosure>(&cl,
                                             &adjust_cld_closure,
                                             &adjust_code_closure, NULL, worker_id);
  }
};

void ShenandoahMarkCompact::phase3_update_references() {
  GCTraceTime(Info, gc, phases) time("Phase 3: Adjust pointers", _gc_timer);
  ShenandoahGCPhase adjust_pointer_phase(ShenandoahPhaseTimings::full_gc_adjust_pointers);

  ShenandoahHeap* heap = ShenandoahHeap::heap();

  WorkGang* workers = heap->workers();
  uint nworkers = workers->active_workers();
  {
#if COMPILER2_OR_JVMCI
    DerivedPointerTable::clear();
#endif
    ShenandoahRootProcessor rp(heap, nworkers, ShenandoahPhaseTimings::full_gc_roots);
    ShenandoahAdjustRootPointersTask task(&rp);
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
    size_t size = (size_t)p->size();
    HeapWord* compact_to = ShenandoahBrooksPointer::get_raw(p);
    HeapWord* compact_from = (HeapWord*) p;
    if (compact_from != compact_to) {
      Copy::aligned_conjoint_words(compact_from, compact_to, size);
    }
    oop new_obj = oop(compact_to);
    ShenandoahBrooksPointer::initialize(new_obj);
  }
};

class ShenandoahCompactObjectsTask : public AbstractGangTask {
private:
  ShenandoahHeap* const _heap;
  ShenandoahHeapRegionSet** const _worker_slices;

public:
  ShenandoahCompactObjectsTask(ShenandoahHeapRegionSet** worker_slices) :
    AbstractGangTask("Shenandoah Compact Objects Task"),
    _heap(ShenandoahHeap::heap()),
    _worker_slices(worker_slices) {
  }

  void work(uint worker_id) {
    ShenandoahHeapRegionSetIterator slice(_worker_slices[worker_id]);

    ShenandoahCompactObjectsClosure cl(worker_id);
    ShenandoahHeapRegion* r = slice.next();
    while (r != NULL) {
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
  size_t _live;

public:
  ShenandoahPostCompactClosure() : _heap(ShenandoahHeap::heap()), _live(0) {
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
    }

    r->set_live_data(live);
    r->reset_alloc_metadata_to_shared();
    _live += live;
  }

  size_t get_live() {
    return _live;
  }
};

void ShenandoahMarkCompact::compact_humongous_objects() {
  // Compact humongous regions, based on their fwdptr objects.
  //
  // This code is serial, because doing the in-slice parallel sliding is tricky. In most cases,
  // humongous regions are already compacted, and do not require further moves, which alleviates
  // sliding costs. We may consider doing this in parallel in future.

  ShenandoahHeap* heap = ShenandoahHeap::heap();

  for (size_t c = heap->num_regions() - 1; c > 0; c--) {
    ShenandoahHeapRegion* r = heap->get_region(c);
    if (r->is_humongous_start()) {
      oop old_obj = oop(r->bottom() + ShenandoahBrooksPointer::word_size());
      size_t words_size = old_obj->size() + ShenandoahBrooksPointer::word_size();
      size_t num_regions = ShenandoahHeapRegion::required_regions(words_size * HeapWordSize);

      size_t old_start = r->region_number();
      size_t old_end   = old_start + num_regions - 1;
      size_t new_start = heap->heap_region_index_containing(ShenandoahBrooksPointer::get_raw(old_obj));
      size_t new_end   = new_start + num_regions - 1;

      if (old_start == new_start) {
        // No need to move the object, it stays at the same slot
        continue;
      }

      assert (r->is_move_allowed(), "should be movable");

      Copy::aligned_conjoint_words(heap->get_region(old_start)->bottom(),
                                   heap->get_region(new_start)->bottom(),
                                   ShenandoahHeapRegion::region_size_words()*num_regions);

      oop new_obj = oop(heap->get_region(new_start)->bottom() + ShenandoahBrooksPointer::word_size());
      ShenandoahBrooksPointer::initialize(new_obj);

      {
        for (size_t c = old_start; c <= old_end; c++) {
          ShenandoahHeapRegion* r = heap->get_region(c);
          r->make_regular_bypass();
          r->set_top(r->bottom());
        }

        for (size_t c = new_start; c <= new_end; c++) {
          ShenandoahHeapRegion* r = heap->get_region(c);
          if (c == new_start) {
            r->make_humongous_start_bypass();
          } else {
            r->make_humongous_cont_bypass();
          }

          // Trailing region may be non-full, record the remainder there
          size_t remainder = words_size & ShenandoahHeapRegion::region_size_words_mask();
          if ((c == new_end) && (remainder != 0)) {
            r->set_top(r->bottom() + remainder);
          } else {
            r->set_top(r->end());
          }

          r->reset_alloc_metadata_to_shared();
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
class ShenandoahMCResetCompleteBitmapTask : public AbstractGangTask {
private:
  ShenandoahRegionIterator _regions;

public:
  ShenandoahMCResetCompleteBitmapTask() :
    AbstractGangTask("Parallel Reset Bitmap Task") {
  }

  void work(uint worker_id) {
    ShenandoahHeapRegion* region = _regions.next();
    ShenandoahHeap* heap = ShenandoahHeap::heap();
    ShenandoahMarkingContext* const ctx = heap->complete_marking_context();
    while (region != NULL) {
      if (heap->is_bitmap_slice_committed(region) && !region->is_pinned() && region->has_live()) {
        ctx->clear_bitmap(region);
      }
      region = _regions.next();
    }
  }
};

void ShenandoahMarkCompact::phase4_compact_objects(ShenandoahHeapRegionSet** worker_slices) {
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
    heap->set_used(post_compact.get_live());

    heap->collection_set()->clear();
    heap->free_set()->rebuild();
  }

  heap->clear_cancelled_gc();
}
