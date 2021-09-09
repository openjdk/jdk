/*
 * Copyright (c) 2021, Amazon.com, Inc. or its affiliates. All rights reserved.
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

#include "gc/shared/strongRootsScope.hpp"
#include "gc/shenandoah/shenandoahAsserts.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahMarkClosures.hpp"
#include "gc/shenandoah/shenandoahMark.inline.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahOopClosures.inline.hpp"
#include "gc/shenandoah/shenandoahReferenceProcessor.hpp"
#include "gc/shenandoah/shenandoahStringDedup.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"

class ShenandoahFlushAllSATB : public ThreadClosure {
 private:
  SATBMarkQueueSet& _satb_qset;
  uintx _claim_token;

 public:
  explicit ShenandoahFlushAllSATB(SATBMarkQueueSet& satb_qset) :
    _satb_qset(satb_qset),
    _claim_token(Threads::thread_claim_token()) { }

  void do_thread(Thread* thread) {
    if (thread->claim_threads_do(true, _claim_token)) {
      // Transfer any partial buffer to the qset for completed buffer processing.
      _satb_qset.flush_queue(ShenandoahThreadLocalData::satb_mark_queue(thread));
    }
  }
};

class ShenandoahProcessOldSATB : public SATBBufferClosure {
 private:
  ShenandoahObjToScanQueue* _queue;
  ShenandoahHeap* _heap;
  ShenandoahMarkingContext* const _mark_context;

 public:
  size_t _trashed_oops;

  explicit ShenandoahProcessOldSATB(ShenandoahObjToScanQueue* q) :
    _queue(q),
    _heap(ShenandoahHeap::heap()),
    _mark_context(_heap->marking_context()),
    _trashed_oops(0) {}

  void do_buffer(void **buffer, size_t size) {
    assert(size == 0 || !_heap->has_forwarded_objects() || _heap->is_concurrent_old_mark_in_progress(), "Forwarded objects are not expected here");
    for (size_t i = 0; i < size; ++i) {
      oop *p = (oop *) &buffer[i];
      ShenandoahHeapRegion* region = _heap->heap_region_containing(*p);
      if (region->is_old()) {
        if (!region->is_trash()) {
          ShenandoahMark::mark_through_ref<oop, OLD>(p, _queue, NULL, _mark_context, false);
        } else {
          ++_trashed_oops;
        }
      }
    }
  }
};

class ShenandoahPurgeSATBTask : public AbstractGangTask {
private:
  ShenandoahObjToScanQueueSet* _mark_queues;

public:
  volatile size_t _trashed_oops;

  explicit ShenandoahPurgeSATBTask(ShenandoahObjToScanQueueSet* queues) :
    AbstractGangTask("Purge SATB"),
    _mark_queues(queues),
    _trashed_oops(0) {
    Threads::change_thread_claim_token();
  }

  ~ShenandoahPurgeSATBTask() {
    if (_trashed_oops > 0) {
      log_info(gc)("Purged " SIZE_FORMAT " oops from old generation SATB buffers.", _trashed_oops);
    }
  }

  void work(uint worker_id) {
    ShenandoahParallelWorkerSession worker_session(worker_id);
    ShenandoahSATBMarkQueueSet &satb_queues = ShenandoahBarrierSet::satb_mark_queue_set();
    ShenandoahFlushAllSATB flusher(satb_queues);
    Threads::threads_do(&flusher);

    ShenandoahObjToScanQueue* mark_queue = _mark_queues->queue(worker_id);
    ShenandoahProcessOldSATB processor(mark_queue);
    while (satb_queues.apply_closure_to_completed_buffer(&processor)) {}

    Atomic::add(&_trashed_oops, processor._trashed_oops);
  }
};

ShenandoahOldGeneration::ShenandoahOldGeneration(uint max_queues, size_t max_capacity, size_t soft_max_capacity)
  : ShenandoahGeneration(OLD, max_queues, max_capacity, soft_max_capacity) {
  // Always clear references for old generation
  ref_processor()->set_soft_reference_policy(true);
}

const char* ShenandoahOldGeneration::name() const {
  return "OLD";
}

bool ShenandoahOldGeneration::contains(ShenandoahHeapRegion* region) const {
  return region->affiliation() != YOUNG_GENERATION;
}

void ShenandoahOldGeneration::parallel_heap_region_iterate(ShenandoahHeapRegionClosure* cl) {
  ShenandoahGenerationRegionClosure<OLD> old_regions(cl);
  ShenandoahHeap::heap()->parallel_heap_region_iterate(&old_regions);
}

void ShenandoahOldGeneration::heap_region_iterate(ShenandoahHeapRegionClosure* cl) {
  ShenandoahGenerationRegionClosure<OLD> old_regions(cl);
  ShenandoahHeap::heap()->heap_region_iterate(&old_regions);
}

void ShenandoahOldGeneration::set_concurrent_mark_in_progress(bool in_progress) {
  ShenandoahHeap::heap()->set_concurrent_old_mark_in_progress(in_progress);
}

bool ShenandoahOldGeneration::is_concurrent_mark_in_progress() {
  return ShenandoahHeap::heap()->is_concurrent_old_mark_in_progress();
}

void ShenandoahOldGeneration::purge_satb_buffers(bool abandon) {
  ShenandoahHeap *heap = ShenandoahHeap::heap();
  shenandoah_assert_safepoint();
  assert(heap->is_concurrent_old_mark_in_progress(), "Only necessary during old marking.");

  if (abandon) {
    ShenandoahBarrierSet::satb_mark_queue_set().abandon_partial_marking();
  } else {
    uint nworkers = heap->workers()->active_workers();
    StrongRootsScope scope(nworkers);

    ShenandoahPurgeSATBTask purge_satb_task(task_queues());
    heap->workers()->run_task(&purge_satb_task);
  }
}

bool ShenandoahOldGeneration::contains(oop obj) const {
  return ShenandoahHeap::heap()->is_in_old(obj);
}

bool ShenandoahOldGeneration::prepare_regions_and_collection_set(bool concurrent) {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  assert(!heap->is_full_gc_in_progress(), "Only for concurrent and degenerated GC");

  {
    ShenandoahGCPhase phase(concurrent ? ShenandoahPhaseTimings::final_update_region_states : ShenandoahPhaseTimings::degen_gc_final_update_region_states);
    ShenandoahFinalMarkUpdateRegionStateClosure cl(complete_marking_context());

    parallel_heap_region_iterate(&cl);
    heap->assert_pinned_region_status();
  }

  {
    ShenandoahGCPhase phase(concurrent ? ShenandoahPhaseTimings::choose_cset : ShenandoahPhaseTimings::degen_gc_choose_cset);
    ShenandoahHeapLocker locker(heap->lock());
    heuristics()->choose_collection_set(nullptr, nullptr);
  }

  {
    ShenandoahGCPhase phase(concurrent ? ShenandoahPhaseTimings::final_rebuild_freeset : ShenandoahPhaseTimings::degen_gc_final_rebuild_freeset);
    ShenandoahHeapLocker locker(heap->lock());
    heap->free_set()->rebuild();
  }
  return false;
}
