/*
 * Copyright (c) 2021, Red Hat, Inc. All rights reserved.
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

#include "gc/shenandoah/shenandoahBarrierSet.hpp"
#include "gc/shenandoah/shenandoahClosures.inline.hpp"
#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/shenandoahMark.inline.hpp"
#include "gc/shenandoah/shenandoahOopClosures.inline.hpp"
#include "gc/shenandoah/shenandoahReferenceProcessor.hpp"
#include "gc/shenandoah/shenandoahTaskqueue.inline.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "gc/shenandoah/shenandoahVerifier.hpp"

ShenandoahMarkRefsSuperClosure::ShenandoahMarkRefsSuperClosure(ShenandoahObjToScanQueue* q,  ShenandoahReferenceProcessor* rp, ShenandoahObjToScanQueue* old_q) :
  MetadataVisitingOopIterateClosure(rp),
  _queue(q),
  _old_queue(old_q),
  _mark_context(ShenandoahHeap::heap()->marking_context()),
  _weak(false)
{ }

ShenandoahMark::ShenandoahMark(ShenandoahGeneration* generation) :
  _generation(generation),
  _task_queues(generation->task_queues()),
  _old_gen_task_queues(generation->old_gen_task_queues()) {
}

template <GenerationMode GENERATION, bool CANCELLABLE>
void ShenandoahMark::mark_loop_prework(uint w, TaskTerminator *t, ShenandoahReferenceProcessor *rp,
                                       bool strdedup, bool update_refs) {
  ShenandoahObjToScanQueue* q = get_queue(w);
  ShenandoahObjToScanQueue* old = get_old_queue(w);

  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  ShenandoahLiveData* ld = heap->get_liveness_cache(w);

  // TODO: We can clean up this if we figure out how to do templated oop closures that
  // play nice with specialized_oop_iterators.
  if (heap->unload_classes()) {
    if (update_refs) {
      if (strdedup) {
        ShenandoahMarkUpdateRefsMetadataDedupClosure<GENERATION> cl(q, rp, old);
        mark_loop_work<ShenandoahMarkUpdateRefsMetadataDedupClosure<GENERATION>, GENERATION, CANCELLABLE>(&cl, ld, w, t);
      } else {
        ShenandoahMarkUpdateRefsMetadataClosure<GENERATION> cl(q, rp, old);
        mark_loop_work<ShenandoahMarkUpdateRefsMetadataClosure<GENERATION>, GENERATION, CANCELLABLE>(&cl, ld, w, t);
      }
    } else {
      if (strdedup) {
        ShenandoahMarkRefsMetadataDedupClosure<GENERATION> cl(q, rp, old);
        mark_loop_work<ShenandoahMarkRefsMetadataDedupClosure<GENERATION>, GENERATION, CANCELLABLE>(&cl, ld, w, t);
      } else {
        ShenandoahMarkRefsMetadataClosure<GENERATION> cl(q, rp, old);
        mark_loop_work<ShenandoahMarkRefsMetadataClosure<GENERATION>, GENERATION, CANCELLABLE>(&cl, ld, w, t);
      }
    }
  } else {
    if (update_refs) {
      if (strdedup) {
        ShenandoahMarkUpdateRefsDedupClosure<GENERATION> cl(q, rp, old);
        mark_loop_work<ShenandoahMarkUpdateRefsDedupClosure<GENERATION>, GENERATION, CANCELLABLE>(&cl, ld, w, t);
      } else {
        ShenandoahMarkUpdateRefsClosure<GENERATION> cl(q, rp, old);
        mark_loop_work<ShenandoahMarkUpdateRefsClosure<GENERATION>, GENERATION, CANCELLABLE>(&cl, ld, w, t);
      }
    } else {
      if (strdedup) {
        ShenandoahMarkRefsDedupClosure<GENERATION> cl(q, rp, old);
        mark_loop_work<ShenandoahMarkRefsDedupClosure<GENERATION>, GENERATION, CANCELLABLE>(&cl, ld, w, t);
      } else {
        ShenandoahMarkRefsClosure<GENERATION> cl(q, rp, old);
        mark_loop_work<ShenandoahMarkRefsClosure<GENERATION>, GENERATION, CANCELLABLE>(&cl, ld, w, t);
      }
    }
  }

  heap->flush_liveness_cache(w);
}

template <class T, GenerationMode GENERATION, bool CANCELLABLE>
void ShenandoahMark::mark_loop_work(T* cl, ShenandoahLiveData* live_data, uint worker_id, TaskTerminator *terminator) {
  uintx stride = ShenandoahMarkLoopStride;

  ShenandoahHeap* heap = ShenandoahHeap::heap();
  ShenandoahObjToScanQueueSet* queues = task_queues();
  ShenandoahObjToScanQueue* q;
  ShenandoahMarkTask t;

  heap->ref_processor()->set_mark_closure(worker_id, cl);

  /*
   * Process outstanding queues, if any.
   *
   * There can be more queues than workers. To deal with the imbalance, we claim
   * extra queues first. Since marking can push new tasks into the queue associated
   * with this worker id, we come back to process this queue in the normal loop.
   */
  assert(queues->get_reserved() == heap->workers()->active_workers(),
         "Need to reserve proper number of queues: reserved: %u, active: %u", queues->get_reserved(), heap->workers()->active_workers());

  q = queues->claim_next();
  while (q != NULL) {
    if (CANCELLABLE && heap->check_cancelled_gc_and_yield()) {
      return;
    }

    for (uint i = 0; i < stride; i++) {
      if (q->pop(t)) {
        do_task<T>(q, cl, live_data, &t);
      } else {
        assert(q->is_empty(), "Must be empty");
        q = queues->claim_next();
        break;
      }
    }
  }
  q = get_queue(worker_id);
  ShenandoahObjToScanQueue* old = get_old_queue(worker_id);

  ShenandoahSATBBufferClosure<GENERATION> drain_satb(q, old);
  SATBMarkQueueSet& satb_mq_set = ShenandoahBarrierSet::satb_mark_queue_set();

  /*
   * Normal marking loop:
   */
  while (true) {
    if (CANCELLABLE && heap->check_cancelled_gc_and_yield()) {
      return;
    }

    while (satb_mq_set.completed_buffers_num() > 0) {
      satb_mq_set.apply_closure_to_completed_buffer(&drain_satb);
    }

    uint work = 0;
    for (uint i = 0; i < stride; i++) {
      if (q->pop(t) ||
          queues->steal(worker_id, t)) {
        do_task<T>(q, cl, live_data, &t);
        work++;
      } else {
        break;
      }
    }

    if (work == 0) {
      // No work encountered in current stride, try to terminate.
      // Need to leave the STS here otherwise it might block safepoints.
      ShenandoahSuspendibleThreadSetLeaver stsl(CANCELLABLE && ShenandoahSuspendibleWorkers);
      ShenandoahTerminatorTerminator tt(heap);
      if (terminator->offer_termination(&tt)) return;
    }
  }
}

void ShenandoahMark::mark_loop(GenerationMode generation, uint worker_id, TaskTerminator* terminator, ShenandoahReferenceProcessor *rp,
               bool cancellable, bool strdedup) {
  bool update_refs = ShenandoahHeap::heap()->has_forwarded_objects();
  switch (generation) {
    case YOUNG: {
      if (cancellable) {
        mark_loop_prework<YOUNG, true>(worker_id, terminator, rp, strdedup, update_refs);
      } else {
        mark_loop_prework<YOUNG, false>(worker_id, terminator, rp, strdedup, update_refs);
      }
      break;
    }
    case OLD: {
      // Old generation collection only performs marking, it should to update references.
      if (cancellable) {
        mark_loop_prework<OLD, true>(worker_id, terminator, rp, strdedup, false);
      } else {
        mark_loop_prework<OLD, false>(worker_id, terminator, rp, strdedup, false);
      }
      break;
    }
    case GLOBAL: {
      if (cancellable) {
        mark_loop_prework<GLOBAL, true>(worker_id, terminator, rp, strdedup, update_refs);
      } else {
        mark_loop_prework<GLOBAL, false>(worker_id, terminator, rp, strdedup, update_refs);
      }
      break;
    }
    default:
      ShouldNotReachHere();
      break;
  }
}

template<>
bool ShenandoahMark::in_generation<YOUNG>(oop obj) {
  return ShenandoahHeap::heap()->is_in_young(obj);
}

template<>
bool ShenandoahMark::in_generation<OLD>(oop obj) {
  return ShenandoahHeap::heap()->is_in_old(obj);
}

template<>
bool ShenandoahMark::in_generation<GLOBAL>(oop obj) {
  return true;
}
