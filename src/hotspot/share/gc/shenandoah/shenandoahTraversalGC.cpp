/*
 * Copyright (c) 2018, 2019, Red Hat, Inc. All rights reserved.
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

#include "classfile/classLoaderData.hpp"
#include "classfile/classLoaderDataGraph.hpp"
#include "gc/shared/referenceProcessor.hpp"
#include "gc/shared/referenceProcessorPhaseTimes.hpp"
#include "gc/shared/workgroup.hpp"
#include "gc/shared/weakProcessor.inline.hpp"
#include "gc/shenandoah/shenandoahBarrierSet.hpp"
#include "gc/shenandoah/shenandoahCodeRoots.hpp"
#include "gc/shenandoah/shenandoahCollectionSet.hpp"
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegionSet.inline.hpp"
#include "gc/shenandoah/shenandoahHeuristics.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.inline.hpp"
#include "gc/shenandoah/shenandoahOopClosures.inline.hpp"
#include "gc/shenandoah/shenandoahRootProcessor.inline.hpp"
#include "gc/shenandoah/shenandoahStringDedup.hpp"
#include "gc/shenandoah/shenandoahTaskqueue.inline.hpp"
#include "gc/shenandoah/shenandoahTimingTracker.hpp"
#include "gc/shenandoah/shenandoahTraversalGC.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "gc/shenandoah/shenandoahVerifier.hpp"

#include "memory/iterator.hpp"
#include "memory/metaspace.hpp"
#include "memory/resourceArea.hpp"

/**
 * NOTE: We are using the SATB buffer in thread.hpp and satbMarkQueue.hpp, however, it is not an SATB algorithm.
 * We're using the buffer as generic oop buffer to enqueue new values in concurrent oop stores, IOW, the algorithm
 * is incremental-update-based.
 *
 * NOTE on interaction with TAMS: we want to avoid traversing new objects for
 * several reasons:
 * - We will not reclaim them in this cycle anyway, because they are not in the
 *   cset
 * - It makes up for the bulk of work during final-pause
 * - It also shortens the concurrent cycle because we don't need to
 *   pointlessly traverse through newly allocated objects.
 * - As a nice side-effect, it solves the I-U termination problem (mutators
 *   cannot outrun the GC by allocating like crazy)
 * - It is an easy way to achieve MWF. What MWF does is to also enqueue the
 *   target object of stores if it's new. Treating new objects live implicitely
 *   achieves the same, but without extra barriers. I think the effect of
 *   shortened final-pause (mentioned above) is the main advantage of MWF. In
 *   particular, we will not see the head of a completely new long linked list
 *   in final-pause and end up traversing huge chunks of the heap there.
 * - We don't need to see/update the fields of new objects either, because they
 *   are either still null, or anything that's been stored into them has been
 *   evacuated+enqueued before (and will thus be treated later).
 *
 * We achieve this by setting TAMS for each region, and everything allocated
 * beyond TAMS will be 'implicitely marked'.
 *
 * Gotchas:
 * - While we want new objects to be implicitely marked, we don't want to count
 *   them alive. Otherwise the next cycle wouldn't pick them up and consider
 *   them for cset. This means that we need to protect such regions from
 *   getting accidentally thrashed at the end of traversal cycle. This is why I
 *   keep track of alloc-regions and check is_alloc_region() in the trashing
 *   code.
 * - We *need* to traverse through evacuated objects. Those objects are
 *   pre-existing, and any references in them point to interesting objects that
 *   we need to see. We also want to count them as live, because we just
 *   determined that they are alive :-) I achieve this by upping TAMS
 *   concurrently for every gclab/gc-shared alloc before publishing the
 *   evacuated object. This way, the GC threads will not consider such objects
 *   implictely marked, and traverse through them as normal.
 */
class ShenandoahTraversalSATBBufferClosure : public SATBBufferClosure {
private:
  ShenandoahObjToScanQueue* _queue;
  ShenandoahTraversalGC* _traversal_gc;
  ShenandoahHeap* const _heap;

public:
  ShenandoahTraversalSATBBufferClosure(ShenandoahObjToScanQueue* q) :
    _queue(q),
    _heap(ShenandoahHeap::heap())
 { }

  void do_buffer(void** buffer, size_t size) {
    for (size_t i = 0; i < size; ++i) {
      oop* p = (oop*) &buffer[i];
      oop obj = RawAccess<>::oop_load(p);
      shenandoah_assert_not_forwarded(p, obj);
      if (_heap->marking_context()->mark(obj)) {
        _queue->push(ShenandoahMarkTask(obj));
      }
    }
  }
};

class ShenandoahTraversalSATBThreadsClosure : public ThreadClosure {
private:
  ShenandoahTraversalSATBBufferClosure* _satb_cl;

public:
  ShenandoahTraversalSATBThreadsClosure(ShenandoahTraversalSATBBufferClosure* satb_cl) :
    _satb_cl(satb_cl) {}

  void do_thread(Thread* thread) {
    ShenandoahThreadLocalData::satb_mark_queue(thread).apply_closure_and_empty(_satb_cl);
  }
};

// Like CLDToOopClosure, but clears has_modified_oops, so that we can record modified CLDs during traversal
// and remark them later during final-traversal.
class ShenandoahMarkCLDClosure : public CLDClosure {
private:
  OopClosure* _cl;
public:
  ShenandoahMarkCLDClosure(OopClosure* cl) : _cl(cl) {}
  void do_cld(ClassLoaderData* cld) {
    cld->oops_do(_cl, true, true);
  }
};

// Like CLDToOopClosure, but only process modified CLDs
class ShenandoahRemarkCLDClosure : public CLDClosure {
private:
  OopClosure* _cl;
public:
  ShenandoahRemarkCLDClosure(OopClosure* cl) : _cl(cl) {}
  void do_cld(ClassLoaderData* cld) {
    if (cld->has_modified_oops()) {
      cld->oops_do(_cl, true, true);
    }
  }
};

class ShenandoahInitTraversalCollectionTask : public AbstractGangTask {
private:
  ShenandoahRootProcessor* _rp;
  ShenandoahHeap* _heap;
  ShenandoahCsetCodeRootsIterator* _cset_coderoots;
public:
  ShenandoahInitTraversalCollectionTask(ShenandoahRootProcessor* rp, ShenandoahCsetCodeRootsIterator* cset_coderoots) :
    AbstractGangTask("Shenandoah Init Traversal Collection"),
    _rp(rp),
    _heap(ShenandoahHeap::heap()),
    _cset_coderoots(cset_coderoots) {}

  void work(uint worker_id) {
    ShenandoahParallelWorkerSession worker_session(worker_id);

    ShenandoahEvacOOMScope oom_evac_scope;
    ShenandoahObjToScanQueueSet* queues = _heap->traversal_gc()->task_queues();
    ShenandoahObjToScanQueue* q = queues->queue(worker_id);

    bool process_refs = _heap->process_references();
    bool unload_classes = _heap->unload_classes();
    ReferenceProcessor* rp = NULL;
    if (process_refs) {
      rp = _heap->ref_processor();
    }

    // Step 1: Process ordinary GC roots.
    {
      ShenandoahTraversalClosure roots_cl(q, rp);
      ShenandoahMarkCLDClosure cld_cl(&roots_cl);
      MarkingCodeBlobClosure code_cl(&roots_cl, CodeBlobToOopClosure::FixRelocations);
      if (unload_classes) {
        _rp->process_strong_roots(&roots_cl, &cld_cl, NULL, NULL, worker_id);
        // Need to pre-evac code roots here. Otherwise we might see from-space constants.
        ShenandoahWorkerTimings* worker_times = _heap->phase_timings()->worker_times();
        ShenandoahWorkerTimingsTracker timer(worker_times, ShenandoahPhaseTimings::CodeCacheRoots, worker_id);
        _cset_coderoots->possibly_parallel_blobs_do(&code_cl);
      } else {
        _rp->process_all_roots(&roots_cl, &cld_cl, &code_cl, NULL, worker_id);
      }
    }
  }
};

class ShenandoahConcurrentTraversalCollectionTask : public AbstractGangTask {
private:
  ShenandoahTaskTerminator* _terminator;
  ShenandoahHeap* _heap;
public:
  ShenandoahConcurrentTraversalCollectionTask(ShenandoahTaskTerminator* terminator) :
    AbstractGangTask("Shenandoah Concurrent Traversal Collection"),
    _terminator(terminator),
    _heap(ShenandoahHeap::heap()) {}

  void work(uint worker_id) {
    ShenandoahConcurrentWorkerSession worker_session(worker_id);
    ShenandoahSuspendibleThreadSetJoiner stsj(ShenandoahSuspendibleWorkers);
    ShenandoahEvacOOMScope oom_evac_scope;
    ShenandoahTraversalGC* traversal_gc = _heap->traversal_gc();

    // Drain all outstanding work in queues.
    traversal_gc->main_loop(worker_id, _terminator, true);
  }
};

class ShenandoahFinalTraversalCollectionTask : public AbstractGangTask {
private:
  ShenandoahRootProcessor* _rp;
  ShenandoahTaskTerminator* _terminator;
  ShenandoahHeap* _heap;
public:
  ShenandoahFinalTraversalCollectionTask(ShenandoahRootProcessor* rp, ShenandoahTaskTerminator* terminator) :
    AbstractGangTask("Shenandoah Final Traversal Collection"),
    _rp(rp),
    _terminator(terminator),
    _heap(ShenandoahHeap::heap()) {}

  void work(uint worker_id) {
    ShenandoahParallelWorkerSession worker_session(worker_id);

    ShenandoahEvacOOMScope oom_evac_scope;
    ShenandoahTraversalGC* traversal_gc = _heap->traversal_gc();

    ShenandoahObjToScanQueueSet* queues = traversal_gc->task_queues();
    ShenandoahObjToScanQueue* q = queues->queue(worker_id);

    bool process_refs = _heap->process_references();
    bool unload_classes = _heap->unload_classes();
    ReferenceProcessor* rp = NULL;
    if (process_refs) {
      rp = _heap->ref_processor();
    }

    // Step 0: Drain outstanding SATB queues.
    // NOTE: we piggy-back draining of remaining thread SATB buffers on the final root scan below.
    ShenandoahTraversalSATBBufferClosure satb_cl(q);
    {
      // Process remaining finished SATB buffers.
      SATBMarkQueueSet& satb_mq_set = ShenandoahBarrierSet::satb_mark_queue_set();
      while (satb_mq_set.apply_closure_to_completed_buffer(&satb_cl));
      // Process remaining threads SATB buffers below.
    }

    // Step 1: Process GC roots.
    // For oops in code roots, they are marked, evacuated, enqueued for further traversal,
    // and the references to the oops are updated during init pause. New nmethods are handled
    // in similar way during nmethod-register process. Therefore, we don't need to rescan code
    // roots here.
    if (!_heap->is_degenerated_gc_in_progress()) {
      ShenandoahTraversalClosure roots_cl(q, rp);
      CLDToOopClosure cld_cl(&roots_cl, ClassLoaderData::_claim_strong);
      ShenandoahTraversalSATBThreadsClosure tc(&satb_cl);
      if (unload_classes) {
        ShenandoahRemarkCLDClosure remark_cld_cl(&roots_cl);
        _rp->process_strong_roots(&roots_cl, &remark_cld_cl, NULL, &tc, worker_id);
      } else {
        _rp->process_all_roots(&roots_cl, &cld_cl, NULL, &tc, worker_id);
      }
    } else {
      ShenandoahTraversalDegenClosure roots_cl(q, rp);
      CLDToOopClosure cld_cl(&roots_cl, ClassLoaderData::_claim_strong);
      ShenandoahTraversalSATBThreadsClosure tc(&satb_cl);
      if (unload_classes) {
        ShenandoahRemarkCLDClosure remark_cld_cl(&roots_cl);
        _rp->process_strong_roots(&roots_cl, &remark_cld_cl, NULL, &tc, worker_id);
      } else {
        _rp->process_all_roots(&roots_cl, &cld_cl, NULL, &tc, worker_id);
      }
    }

    {
      ShenandoahWorkerTimings *worker_times = _heap->phase_timings()->worker_times();
      ShenandoahWorkerTimingsTracker timer(worker_times, ShenandoahPhaseTimings::FinishQueues, worker_id);

      // Step 3: Finally drain all outstanding work in queues.
      traversal_gc->main_loop(worker_id, _terminator, false);
    }

  }
};

ShenandoahTraversalGC::ShenandoahTraversalGC(ShenandoahHeap* heap, size_t num_regions) :
  _heap(heap),
  _task_queues(new ShenandoahObjToScanQueueSet(heap->max_workers())),
  _traversal_set(ShenandoahHeapRegionSet()) {

  uint num_queues = heap->max_workers();
  for (uint i = 0; i < num_queues; ++i) {
    ShenandoahObjToScanQueue* task_queue = new ShenandoahObjToScanQueue();
    task_queue->initialize();
    _task_queues->register_queue(i, task_queue);
  }
}

ShenandoahTraversalGC::~ShenandoahTraversalGC() {
}

void ShenandoahTraversalGC::prepare_regions() {
  size_t num_regions = _heap->num_regions();
  ShenandoahMarkingContext* const ctx = _heap->marking_context();
  for (size_t i = 0; i < num_regions; i++) {
    ShenandoahHeapRegion* region = _heap->get_region(i);
    if (_heap->is_bitmap_slice_committed(region)) {
      if (_traversal_set.is_in(i)) {
        ctx->capture_top_at_mark_start(region);
        region->clear_live_data();
        assert(ctx->is_bitmap_clear_range(region->bottom(), region->end()), "bitmap for traversal regions must be cleared");
      } else {
        // Everything outside the traversal set is always considered live.
        ctx->reset_top_at_mark_start(region);
      }
    } else {
      // FreeSet may contain uncommitted empty regions, once they are recommitted,
      // their TAMS may have old values, so reset them here.
      ctx->reset_top_at_mark_start(region);
    }
  }
}

void ShenandoahTraversalGC::prepare() {
  _heap->collection_set()->clear();
  assert(_heap->collection_set()->count() == 0, "collection set not clear");

  {
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::traversal_gc_make_parsable);
    _heap->make_parsable(true);
  }

  if (UseTLAB) {
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::traversal_gc_resize_tlabs);
    _heap->resize_tlabs();
  }

  assert(_heap->marking_context()->is_bitmap_clear(), "need clean mark bitmap");
  assert(!_heap->marking_context()->is_complete(), "should not be complete");

  ShenandoahFreeSet* free_set = _heap->free_set();
  ShenandoahCollectionSet* collection_set = _heap->collection_set();

  // Find collection set
  _heap->heuristics()->choose_collection_set(collection_set);
  prepare_regions();

  // Rebuild free set
  free_set->rebuild();

  log_info(gc, ergo)("Collectable Garbage: " SIZE_FORMAT "M, " SIZE_FORMAT "M CSet, " SIZE_FORMAT " CSet regions",
                     collection_set->garbage() / M, collection_set->live_data() / M, collection_set->count());
}

void ShenandoahTraversalGC::init_traversal_collection() {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "STW traversal GC");

  if (ShenandoahVerify) {
    _heap->verifier()->verify_before_traversal();
  }

  if (VerifyBeforeGC) {
    Universe::verify();
  }

  {
    ShenandoahGCPhase phase_prepare(ShenandoahPhaseTimings::traversal_gc_prepare);
    ShenandoahHeapLocker lock(_heap->lock());
    prepare();
  }

  _heap->set_concurrent_traversal_in_progress(true);

  bool process_refs = _heap->process_references();
  if (process_refs) {
    ReferenceProcessor* rp = _heap->ref_processor();
    rp->enable_discovery(true /*verify_no_refs*/);
    rp->setup_policy(_heap->soft_ref_policy()->should_clear_all_soft_refs());
  }

  {
    ShenandoahGCPhase phase_work(ShenandoahPhaseTimings::init_traversal_gc_work);
    assert(_task_queues->is_empty(), "queues must be empty before traversal GC");
    TASKQUEUE_STATS_ONLY(_task_queues->reset_taskqueue_stats());

#if defined(COMPILER2) || INCLUDE_JVMCI
    DerivedPointerTable::clear();
#endif

    {
      uint nworkers = _heap->workers()->active_workers();
      task_queues()->reserve(nworkers);
      ShenandoahRootProcessor rp(_heap, nworkers, ShenandoahPhaseTimings::init_traversal_gc_work);

      ShenandoahCsetCodeRootsIterator cset_coderoots = ShenandoahCodeRoots::cset_iterator();

      ShenandoahInitTraversalCollectionTask traversal_task(&rp, &cset_coderoots);
      _heap->workers()->run_task(&traversal_task);
    }

#if defined(COMPILER2) || INCLUDE_JVMCI
    DerivedPointerTable::update_pointers();
#endif
  }

  if (ShenandoahPacing) {
    _heap->pacer()->setup_for_traversal();
  }
}

void ShenandoahTraversalGC::main_loop(uint w, ShenandoahTaskTerminator* t, bool sts_yield) {
  ShenandoahObjToScanQueue* q = task_queues()->queue(w);

  // Initialize live data.
  jushort* ld = _heap->get_liveness_cache(w);

  ReferenceProcessor* rp = NULL;
  if (_heap->process_references()) {
    rp = _heap->ref_processor();
  }
  {
    if (!_heap->is_degenerated_gc_in_progress()) {
      if (_heap->unload_classes()) {
        if (ShenandoahStringDedup::is_enabled()) {
          ShenandoahTraversalMetadataDedupClosure cl(q, rp);
          main_loop_work<ShenandoahTraversalMetadataDedupClosure>(&cl, ld, w, t, sts_yield);
        } else {
          ShenandoahTraversalMetadataClosure cl(q, rp);
          main_loop_work<ShenandoahTraversalMetadataClosure>(&cl, ld, w, t, sts_yield);
        }
      } else {
        if (ShenandoahStringDedup::is_enabled()) {
          ShenandoahTraversalDedupClosure cl(q, rp);
          main_loop_work<ShenandoahTraversalDedupClosure>(&cl, ld, w, t, sts_yield);
        } else {
          ShenandoahTraversalClosure cl(q, rp);
          main_loop_work<ShenandoahTraversalClosure>(&cl, ld, w, t, sts_yield);
        }
      }
    } else {
      if (_heap->unload_classes()) {
        if (ShenandoahStringDedup::is_enabled()) {
          ShenandoahTraversalMetadataDedupDegenClosure cl(q, rp);
          main_loop_work<ShenandoahTraversalMetadataDedupDegenClosure>(&cl, ld, w, t, sts_yield);
        } else {
          ShenandoahTraversalMetadataDegenClosure cl(q, rp);
          main_loop_work<ShenandoahTraversalMetadataDegenClosure>(&cl, ld, w, t, sts_yield);
        }
      } else {
        if (ShenandoahStringDedup::is_enabled()) {
          ShenandoahTraversalDedupDegenClosure cl(q, rp);
          main_loop_work<ShenandoahTraversalDedupDegenClosure>(&cl, ld, w, t, sts_yield);
        } else {
          ShenandoahTraversalDegenClosure cl(q, rp);
          main_loop_work<ShenandoahTraversalDegenClosure>(&cl, ld, w, t, sts_yield);
        }
      }
    }
  }

  _heap->flush_liveness_cache(w);
}

template <class T>
void ShenandoahTraversalGC::main_loop_work(T* cl, jushort* live_data, uint worker_id, ShenandoahTaskTerminator* terminator, bool sts_yield) {
  ShenandoahObjToScanQueueSet* queues = task_queues();
  ShenandoahObjToScanQueue* q = queues->queue(worker_id);
  ShenandoahConcurrentMark* conc_mark = _heap->concurrent_mark();

  uintx stride = ShenandoahMarkLoopStride;

  ShenandoahMarkTask task;

  // Process outstanding queues, if any.
  q = queues->claim_next();
  while (q != NULL) {
    if (_heap->check_cancelled_gc_and_yield(sts_yield)) {
      return;
    }

    for (uint i = 0; i < stride; i++) {
      if (q->pop(task)) {
        conc_mark->do_task<T>(q, cl, live_data, &task);
      } else {
        assert(q->is_empty(), "Must be empty");
        q = queues->claim_next();
        break;
      }
    }
  }

  if (check_and_handle_cancelled_gc(terminator, sts_yield)) return;

  // Normal loop.
  q = queues->queue(worker_id);

  ShenandoahTraversalSATBBufferClosure drain_satb(q);
  SATBMarkQueueSet& satb_mq_set = ShenandoahBarrierSet::satb_mark_queue_set();

  while (true) {
    if (check_and_handle_cancelled_gc(terminator, sts_yield)) return;

    while (satb_mq_set.completed_buffers_num() > 0) {
      satb_mq_set.apply_closure_to_completed_buffer(&drain_satb);
    }

    uint work = 0;
    for (uint i = 0; i < stride; i++) {
      if (q->pop(task) ||
          queues->steal(worker_id, task)) {
        conc_mark->do_task<T>(q, cl, live_data, &task);
        work++;
      } else {
        break;
      }
    }

    if (work == 0) {
      // No more work, try to terminate
      ShenandoahEvacOOMScopeLeaver oom_scope_leaver;
      ShenandoahSuspendibleThreadSetLeaver stsl(sts_yield && ShenandoahSuspendibleWorkers);
      ShenandoahTerminationTimingsTracker term_tracker(worker_id);
      ShenandoahTerminatorTerminator tt(_heap);

      if (terminator->offer_termination(&tt)) return;
    }
  }
}

bool ShenandoahTraversalGC::check_and_handle_cancelled_gc(ShenandoahTaskTerminator* terminator, bool sts_yield) {
  if (_heap->cancelled_gc()) {
    return true;
  }
  return false;
}

void ShenandoahTraversalGC::concurrent_traversal_collection() {
  ClassLoaderDataGraph::clear_claimed_marks();

  ShenandoahGCPhase phase_work(ShenandoahPhaseTimings::conc_traversal);
  if (!_heap->cancelled_gc()) {
    uint nworkers = _heap->workers()->active_workers();
    task_queues()->reserve(nworkers);
    ShenandoahTerminationTracker tracker(ShenandoahPhaseTimings::conc_traversal_termination);

    ShenandoahTaskTerminator terminator(nworkers, task_queues());
    ShenandoahConcurrentTraversalCollectionTask task(&terminator);
    _heap->workers()->run_task(&task);
  }

  if (!_heap->cancelled_gc() && ShenandoahPreclean && _heap->process_references()) {
    preclean_weak_refs();
  }
}

void ShenandoahTraversalGC::final_traversal_collection() {
  _heap->make_parsable(true);

  if (!_heap->cancelled_gc()) {
#if defined(COMPILER2) || INCLUDE_JVMCI
    DerivedPointerTable::clear();
#endif
    ShenandoahGCPhase phase_work(ShenandoahPhaseTimings::final_traversal_gc_work);
    uint nworkers = _heap->workers()->active_workers();
    task_queues()->reserve(nworkers);

    // Finish traversal
    ShenandoahRootProcessor rp(_heap, nworkers, ShenandoahPhaseTimings::final_traversal_gc_work);
    ShenandoahTerminationTracker term(ShenandoahPhaseTimings::final_traversal_gc_termination);

    ShenandoahTaskTerminator terminator(nworkers, task_queues());
    ShenandoahFinalTraversalCollectionTask task(&rp, &terminator);
    _heap->workers()->run_task(&task);
#if defined(COMPILER2) || INCLUDE_JVMCI
    DerivedPointerTable::update_pointers();
#endif
  }

  if (!_heap->cancelled_gc() && _heap->process_references()) {
    weak_refs_work();
  }

  if (!_heap->cancelled_gc()) {
    if (_heap->unload_classes()) {
      _heap->unload_classes_and_cleanup_tables(false);
    }

    fixup_roots();
  }

  if (!_heap->cancelled_gc()) {
    assert(_task_queues->is_empty(), "queues must be empty after traversal GC");
    TASKQUEUE_STATS_ONLY(_task_queues->print_taskqueue_stats());
    TASKQUEUE_STATS_ONLY(_task_queues->reset_taskqueue_stats());

    // No more marking expected
    _heap->mark_complete_marking_context();

    // Resize metaspace
    MetaspaceGC::compute_new_size();

    // Still good? We can now trash the cset, and make final verification
    {
      ShenandoahGCPhase phase_cleanup(ShenandoahPhaseTimings::traversal_gc_cleanup);
      ShenandoahHeapLocker lock(_heap->lock());

      // Trash everything
      // Clear immediate garbage regions.
      size_t num_regions = _heap->num_regions();

      ShenandoahHeapRegionSet* traversal_regions = traversal_set();
      ShenandoahFreeSet* free_regions = _heap->free_set();
      ShenandoahMarkingContext* const ctx = _heap->marking_context();
      free_regions->clear();
      for (size_t i = 0; i < num_regions; i++) {
        ShenandoahHeapRegion* r = _heap->get_region(i);
        bool not_allocated = ctx->top_at_mark_start(r) == r->top();

        bool candidate = traversal_regions->is_in(r) && !r->has_live() && not_allocated;
        if (r->is_humongous_start() && candidate) {
          // Trash humongous.
          HeapWord* humongous_obj = r->bottom() + ShenandoahBrooksPointer::word_size();
          assert(!ctx->is_marked(oop(humongous_obj)), "must not be marked");
          r->make_trash_immediate();
          while (i + 1 < num_regions && _heap->get_region(i + 1)->is_humongous_continuation()) {
            i++;
            r = _heap->get_region(i);
            assert(r->is_humongous_continuation(), "must be humongous continuation");
            r->make_trash_immediate();
          }
        } else if (!r->is_empty() && candidate) {
          // Trash regular.
          assert(!r->is_humongous(), "handled above");
          assert(!r->is_trash(), "must not already be trashed");
          r->make_trash_immediate();
        }
      }
      _heap->collection_set()->clear();
      _heap->free_set()->rebuild();
      reset();
    }

    assert(_task_queues->is_empty(), "queues must be empty after traversal GC");
    _heap->set_concurrent_traversal_in_progress(false);
    assert(!_heap->cancelled_gc(), "must not be cancelled when getting out here");

    if (ShenandoahVerify) {
      _heap->verifier()->verify_after_traversal();
    }

    if (VerifyAfterGC) {
      Universe::verify();
    }
  }
}

class ShenandoahTraversalFixRootsClosure : public OopClosure {
private:
  template <class T>
  inline void do_oop_work(T* p) {
    T o = RawAccess<>::oop_load(p);
    if (!CompressedOops::is_null(o)) {
      oop obj = CompressedOops::decode_not_null(o);
      oop forw = ShenandoahBarrierSet::resolve_forwarded_not_null(obj);
      if (!oopDesc::equals_raw(obj, forw)) {
        RawAccess<IS_NOT_NULL>::oop_store(p, forw);
      }
    }
  }

public:
  inline void do_oop(oop* p) { do_oop_work(p); }
  inline void do_oop(narrowOop* p) { do_oop_work(p); }
};

class ShenandoahTraversalFixRootsTask : public AbstractGangTask {
private:
  ShenandoahRootProcessor* _rp;

public:
  ShenandoahTraversalFixRootsTask(ShenandoahRootProcessor* rp) :
    AbstractGangTask("Shenandoah traversal fix roots"),
    _rp(rp) {
    assert(ShenandoahHeap::heap()->has_forwarded_objects(), "Must be");
  }

  void work(uint worker_id) {
    ShenandoahParallelWorkerSession worker_session(worker_id);
    ShenandoahTraversalFixRootsClosure cl;
    MarkingCodeBlobClosure blobsCl(&cl, CodeBlobToOopClosure::FixRelocations);
    CLDToOopClosure cldCl(&cl, ClassLoaderData::_claim_strong);
    _rp->update_all_roots<ShenandoahForwardedIsAliveClosure>(&cl, &cldCl, &blobsCl, NULL, worker_id);
  }
};

void ShenandoahTraversalGC::fixup_roots() {
#if defined(COMPILER2) || INCLUDE_JVMCI
  DerivedPointerTable::clear();
#endif
  ShenandoahRootProcessor rp(_heap, _heap->workers()->active_workers(), ShenandoahPhaseTimings::final_traversal_update_roots);
  ShenandoahTraversalFixRootsTask update_roots_task(&rp);
  _heap->workers()->run_task(&update_roots_task);
#if defined(COMPILER2) || INCLUDE_JVMCI
  DerivedPointerTable::update_pointers();
#endif
}

void ShenandoahTraversalGC::reset() {
  _task_queues->clear();
}

ShenandoahObjToScanQueueSet* ShenandoahTraversalGC::task_queues() {
  return _task_queues;
}

class ShenandoahTraversalCancelledGCYieldClosure : public YieldClosure {
private:
  ShenandoahHeap* const _heap;
public:
  ShenandoahTraversalCancelledGCYieldClosure() : _heap(ShenandoahHeap::heap()) {};
  virtual bool should_return() { return _heap->cancelled_gc(); }
};

class ShenandoahTraversalPrecleanCompleteGCClosure : public VoidClosure {
public:
  void do_void() {
    ShenandoahHeap* sh = ShenandoahHeap::heap();
    ShenandoahTraversalGC* traversal_gc = sh->traversal_gc();
    assert(sh->process_references(), "why else would we be here?");
    ShenandoahTaskTerminator terminator(1, traversal_gc->task_queues());
    shenandoah_assert_rp_isalive_installed();
    traversal_gc->main_loop((uint) 0, &terminator, true);
  }
};

class ShenandoahTraversalKeepAliveUpdateClosure : public OopClosure {
private:
  ShenandoahObjToScanQueue* _queue;
  Thread* _thread;
  ShenandoahTraversalGC* _traversal_gc;
  ShenandoahMarkingContext* const _mark_context;

  template <class T>
  inline void do_oop_work(T* p) {
    _traversal_gc->process_oop<T, false /* string dedup */, false /* degen */>(p, _thread, _queue, _mark_context);
  }

public:
  ShenandoahTraversalKeepAliveUpdateClosure(ShenandoahObjToScanQueue* q) :
    _queue(q), _thread(Thread::current()),
    _traversal_gc(ShenandoahHeap::heap()->traversal_gc()),
    _mark_context(ShenandoahHeap::heap()->marking_context()) {}

  void do_oop(narrowOop* p) { do_oop_work(p); }
  void do_oop(oop* p)       { do_oop_work(p); }
};

class ShenandoahTraversalWeakUpdateClosure : public OopClosure {
private:
  template <class T>
  inline void do_oop_work(T* p) {
    // Cannot call maybe_update_with_forwarded, because on traversal-degen
    // path the collection set is already dropped. Instead, do the unguarded store.
    // TODO: This can be fixed after degen-traversal stops dropping cset.
    T o = RawAccess<>::oop_load(p);
    if (!CompressedOops::is_null(o)) {
      oop obj = CompressedOops::decode_not_null(o);
      obj = ShenandoahBarrierSet::resolve_forwarded_not_null(obj);
      shenandoah_assert_marked(p, obj);
      RawAccess<IS_NOT_NULL>::oop_store(p, obj);
    }
  }

public:
  ShenandoahTraversalWeakUpdateClosure() {}

  void do_oop(narrowOop* p) { do_oop_work(p); }
  void do_oop(oop* p)       { do_oop_work(p); }
};

class ShenandoahTraversalKeepAliveUpdateDegenClosure : public OopClosure {
private:
  ShenandoahObjToScanQueue* _queue;
  Thread* _thread;
  ShenandoahTraversalGC* _traversal_gc;
  ShenandoahMarkingContext* const _mark_context;

  template <class T>
  inline void do_oop_work(T* p) {
    _traversal_gc->process_oop<T, false /* string dedup */, true /* degen */>(p, _thread, _queue, _mark_context);
  }

public:
  ShenandoahTraversalKeepAliveUpdateDegenClosure(ShenandoahObjToScanQueue* q) :
          _queue(q), _thread(Thread::current()),
          _traversal_gc(ShenandoahHeap::heap()->traversal_gc()),
          _mark_context(ShenandoahHeap::heap()->marking_context()) {}

  void do_oop(narrowOop* p) { do_oop_work(p); }
  void do_oop(oop* p)       { do_oop_work(p); }
};

class ShenandoahTraversalSingleThreadKeepAliveUpdateClosure : public OopClosure {
private:
  ShenandoahObjToScanQueue* _queue;
  Thread* _thread;
  ShenandoahTraversalGC* _traversal_gc;
  ShenandoahMarkingContext* const _mark_context;

  template <class T>
  inline void do_oop_work(T* p) {
    ShenandoahEvacOOMScope evac_scope;
    _traversal_gc->process_oop<T, false /* string dedup */, false /* degen */>(p, _thread, _queue, _mark_context);
  }

public:
  ShenandoahTraversalSingleThreadKeepAliveUpdateClosure(ShenandoahObjToScanQueue* q) :
          _queue(q), _thread(Thread::current()),
          _traversal_gc(ShenandoahHeap::heap()->traversal_gc()),
          _mark_context(ShenandoahHeap::heap()->marking_context()) {}

  void do_oop(narrowOop* p) { do_oop_work(p); }
  void do_oop(oop* p)       { do_oop_work(p); }
};

class ShenandoahTraversalSingleThreadKeepAliveUpdateDegenClosure : public OopClosure {
private:
  ShenandoahObjToScanQueue* _queue;
  Thread* _thread;
  ShenandoahTraversalGC* _traversal_gc;
  ShenandoahMarkingContext* const _mark_context;

  template <class T>
  inline void do_oop_work(T* p) {
    ShenandoahEvacOOMScope evac_scope;
    _traversal_gc->process_oop<T, false /* string dedup */, true /* degen */>(p, _thread, _queue, _mark_context);
  }

public:
  ShenandoahTraversalSingleThreadKeepAliveUpdateDegenClosure(ShenandoahObjToScanQueue* q) :
          _queue(q), _thread(Thread::current()),
          _traversal_gc(ShenandoahHeap::heap()->traversal_gc()),
          _mark_context(ShenandoahHeap::heap()->marking_context()) {}

  void do_oop(narrowOop* p) { do_oop_work(p); }
  void do_oop(oop* p)       { do_oop_work(p); }
};

class ShenandoahTraversalPrecleanTask : public AbstractGangTask {
private:
  ReferenceProcessor* _rp;

public:
  ShenandoahTraversalPrecleanTask(ReferenceProcessor* rp) :
          AbstractGangTask("Precleaning task"),
          _rp(rp) {}

  void work(uint worker_id) {
    assert(worker_id == 0, "The code below is single-threaded, only one worker is expected");
    ShenandoahParallelWorkerSession worker_session(worker_id);
    ShenandoahSuspendibleThreadSetJoiner stsj(ShenandoahSuspendibleWorkers);
    ShenandoahEvacOOMScope oom_evac_scope;

    ShenandoahHeap* sh = ShenandoahHeap::heap();

    ShenandoahObjToScanQueue* q = sh->traversal_gc()->task_queues()->queue(worker_id);

    ShenandoahForwardedIsAliveClosure is_alive;
    ShenandoahTraversalCancelledGCYieldClosure yield;
    ShenandoahTraversalPrecleanCompleteGCClosure complete_gc;
    ShenandoahTraversalKeepAliveUpdateClosure keep_alive(q);
    ResourceMark rm;
    _rp->preclean_discovered_references(&is_alive, &keep_alive,
                                        &complete_gc, &yield,
                                        NULL);
  }
};

void ShenandoahTraversalGC::preclean_weak_refs() {
  // Pre-cleaning weak references before diving into STW makes sense at the
  // end of concurrent mark. This will filter out the references which referents
  // are alive. Note that ReferenceProcessor already filters out these on reference
  // discovery, and the bulk of work is done here. This phase processes leftovers
  // that missed the initial filtering, i.e. when referent was marked alive after
  // reference was discovered by RP.

  assert(_heap->process_references(), "sanity");
  assert(!_heap->is_degenerated_gc_in_progress(), "must be in concurrent non-degenerated phase");

  // Shortcut if no references were discovered to avoid winding up threads.
  ReferenceProcessor* rp = _heap->ref_processor();
  if (!rp->has_discovered_references()) {
    return;
  }

  ReferenceProcessorMTDiscoveryMutator fix_mt_discovery(rp, false);

  shenandoah_assert_rp_isalive_not_installed();
  ShenandoahForwardedIsAliveClosure is_alive;
  ReferenceProcessorIsAliveMutator fix_isalive(rp, &is_alive);

  assert(task_queues()->is_empty(), "Should be empty");

  // Execute precleaning in the worker thread: it will give us GCLABs, String dedup
  // queues and other goodies. When upstream ReferenceProcessor starts supporting
  // parallel precleans, we can extend this to more threads.
  ShenandoahPushWorkerScope scope(_heap->workers(), 1, /* check_workers = */ false);

  WorkGang* workers = _heap->workers();
  uint nworkers = workers->active_workers();
  assert(nworkers == 1, "This code uses only a single worker");
  task_queues()->reserve(nworkers);

  ShenandoahTraversalPrecleanTask task(rp);
  workers->run_task(&task);

  assert(_heap->cancelled_gc() || task_queues()->is_empty(), "Should be empty");
}

// Weak Reference Closures
class ShenandoahTraversalDrainMarkingStackClosure: public VoidClosure {
  uint _worker_id;
  ShenandoahTaskTerminator* _terminator;
  bool _reset_terminator;

public:
  ShenandoahTraversalDrainMarkingStackClosure(uint worker_id, ShenandoahTaskTerminator* t, bool reset_terminator = false):
    _worker_id(worker_id),
    _terminator(t),
    _reset_terminator(reset_terminator) {
  }

  void do_void() {
    assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Must be at a safepoint");

    ShenandoahHeap* sh = ShenandoahHeap::heap();
    ShenandoahTraversalGC* traversal_gc = sh->traversal_gc();
    assert(sh->process_references(), "why else would we be here?");
    shenandoah_assert_rp_isalive_installed();

    traversal_gc->main_loop(_worker_id, _terminator, false);

    if (_reset_terminator) {
      _terminator->reset_for_reuse();
    }
  }
};

class ShenandoahTraversalSingleThreadedDrainMarkingStackClosure: public VoidClosure {
  uint _worker_id;
  ShenandoahTaskTerminator* _terminator;
  bool _reset_terminator;

public:
  ShenandoahTraversalSingleThreadedDrainMarkingStackClosure(uint worker_id, ShenandoahTaskTerminator* t, bool reset_terminator = false):
          _worker_id(worker_id),
          _terminator(t),
          _reset_terminator(reset_terminator) {
  }

  void do_void() {
    assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Must be at a safepoint");

    ShenandoahHeap* sh = ShenandoahHeap::heap();
    ShenandoahTraversalGC* traversal_gc = sh->traversal_gc();
    assert(sh->process_references(), "why else would we be here?");
    shenandoah_assert_rp_isalive_installed();

    ShenandoahEvacOOMScope evac_scope;
    traversal_gc->main_loop(_worker_id, _terminator, false);

    if (_reset_terminator) {
      _terminator->reset_for_reuse();
    }
  }
};

void ShenandoahTraversalGC::weak_refs_work() {
  assert(_heap->process_references(), "sanity");

  ShenandoahPhaseTimings::Phase phase_root = ShenandoahPhaseTimings::weakrefs;

  ShenandoahGCPhase phase(phase_root);

  ReferenceProcessor* rp = _heap->ref_processor();

  // NOTE: We cannot shortcut on has_discovered_references() here, because
  // we will miss marking JNI Weak refs then, see implementation in
  // ReferenceProcessor::process_discovered_references.
  weak_refs_work_doit();

  rp->verify_no_references_recorded();
  assert(!rp->discovery_enabled(), "Post condition");

}

class ShenandoahTraversalRefProcTaskProxy : public AbstractGangTask {
private:
  AbstractRefProcTaskExecutor::ProcessTask& _proc_task;
  ShenandoahTaskTerminator* _terminator;

public:
  ShenandoahTraversalRefProcTaskProxy(AbstractRefProcTaskExecutor::ProcessTask& proc_task,
                                      ShenandoahTaskTerminator* t) :
    AbstractGangTask("Process reference objects in parallel"),
    _proc_task(proc_task),
    _terminator(t) {
  }

  void work(uint worker_id) {
    ShenandoahEvacOOMScope oom_evac_scope;
    assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Must be at a safepoint");
    ShenandoahHeap* heap = ShenandoahHeap::heap();
    ShenandoahTraversalDrainMarkingStackClosure complete_gc(worker_id, _terminator);

    ShenandoahForwardedIsAliveClosure is_alive;
    if (!heap->is_degenerated_gc_in_progress()) {
      ShenandoahTraversalKeepAliveUpdateClosure keep_alive(heap->traversal_gc()->task_queues()->queue(worker_id));
      _proc_task.work(worker_id, is_alive, keep_alive, complete_gc);
    } else {
      ShenandoahTraversalKeepAliveUpdateDegenClosure keep_alive(heap->traversal_gc()->task_queues()->queue(worker_id));
      _proc_task.work(worker_id, is_alive, keep_alive, complete_gc);
    }
  }
};

class ShenandoahTraversalRefProcTaskExecutor : public AbstractRefProcTaskExecutor {
private:
  WorkGang* _workers;

public:
  ShenandoahTraversalRefProcTaskExecutor(WorkGang* workers) : _workers(workers) {}

  // Executes a task using worker threads.
  void execute(ProcessTask& task, uint ergo_workers) {
    assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Must be at a safepoint");

    ShenandoahHeap* heap = ShenandoahHeap::heap();
    ShenandoahTraversalGC* traversal_gc = heap->traversal_gc();
    ShenandoahPushWorkerQueuesScope scope(_workers,
                                          traversal_gc->task_queues(),
                                          ergo_workers,
                                          /* do_check = */ false);
    uint nworkers = _workers->active_workers();
    traversal_gc->task_queues()->reserve(nworkers);
    ShenandoahTaskTerminator terminator(nworkers, traversal_gc->task_queues());
    ShenandoahTraversalRefProcTaskProxy proc_task_proxy(task, &terminator);
    _workers->run_task(&proc_task_proxy);
  }
};

void ShenandoahTraversalGC::weak_refs_work_doit() {
  ReferenceProcessor* rp = _heap->ref_processor();

  ShenandoahPhaseTimings::Phase phase_process = ShenandoahPhaseTimings::weakrefs_process;

  shenandoah_assert_rp_isalive_not_installed();
  ShenandoahForwardedIsAliveClosure is_alive;
  ReferenceProcessorIsAliveMutator fix_isalive(rp, &is_alive);

  WorkGang* workers = _heap->workers();
  uint nworkers = workers->active_workers();

  rp->setup_policy(_heap->soft_ref_policy()->should_clear_all_soft_refs());
  rp->set_active_mt_degree(nworkers);

  assert(task_queues()->is_empty(), "Should be empty");

  // complete_gc and keep_alive closures instantiated here are only needed for
  // single-threaded path in RP. They share the queue 0 for tracking work, which
  // simplifies implementation. Since RP may decide to call complete_gc several
  // times, we need to be able to reuse the terminator.
  uint serial_worker_id = 0;
  ShenandoahTaskTerminator terminator(1, task_queues());
  ShenandoahTraversalSingleThreadedDrainMarkingStackClosure complete_gc(serial_worker_id, &terminator, /* reset_terminator = */ true);
  ShenandoahPushWorkerQueuesScope scope(workers, task_queues(), 1, /* do_check = */ false);

  ShenandoahTraversalRefProcTaskExecutor executor(workers);

  ReferenceProcessorPhaseTimes pt(_heap->gc_timer(), rp->num_queues());
  if (!_heap->is_degenerated_gc_in_progress()) {
    ShenandoahTraversalSingleThreadKeepAliveUpdateClosure keep_alive(task_queues()->queue(serial_worker_id));
    rp->process_discovered_references(&is_alive, &keep_alive,
                                      &complete_gc, &executor,
                                      &pt);
  } else {
    ShenandoahTraversalSingleThreadKeepAliveUpdateDegenClosure keep_alive(task_queues()->queue(serial_worker_id));
    rp->process_discovered_references(&is_alive, &keep_alive,
                                      &complete_gc, &executor,
                                      &pt);
  }

  {
    ShenandoahGCPhase phase(phase_process);
    ShenandoahTerminationTracker termination(ShenandoahPhaseTimings::weakrefs_termination);

    // Process leftover weak oops (using parallel version)
    ShenandoahTraversalWeakUpdateClosure cl;
    WeakProcessor::weak_oops_do(workers, &is_alive, &cl, 1);

    pt.print_all_references();

    assert(task_queues()->is_empty() || _heap->cancelled_gc(), "Should be empty");
  }
}
