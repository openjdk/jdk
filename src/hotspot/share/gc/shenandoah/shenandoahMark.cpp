/*
 * Copyright (c) 2021, 2022, Red Hat, Inc. All rights reserved.
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

#include "gc/shenandoah/shenandoahBarrierSet.hpp"
#include "gc/shenandoah/shenandoahClosures.inline.hpp"
#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/shenandoahMark.inline.hpp"
#include "gc/shenandoah/shenandoahReferenceProcessor.hpp"
#include "gc/shenandoah/shenandoahTaskqueue.inline.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "gc/shenandoah/shenandoahVerifier.hpp"

void ShenandoahMark::start_mark() {
  if (!CodeCache::is_gc_marking_cycle_active()) {
    CodeCache::on_gc_marking_cycle_start();
  }
}

void ShenandoahMark::end_mark() {
  // Unlike other GCs, we do not arm the nmethods
  // when marking terminates.
  if (!ShenandoahHeap::heap()->is_concurrent_old_mark_in_progress()) {
    CodeCache::on_gc_marking_cycle_finish();
  }
}

ShenandoahMark::ShenandoahMark(ShenandoahGeneration* generation) :
  _generation(generation),
  _task_queues(generation->task_queues()),
  _old_gen_task_queues(generation->old_gen_task_queues()) {
}

template <ShenandoahGenerationType GENERATION, bool CANCELLABLE, StringDedupMode STRING_DEDUP>
void ShenandoahMark::mark_loop_prework(uint w, TaskTerminator *t, ShenandoahReferenceProcessor *rp, StringDedup::Requests* const req, bool update_refs) {
  ShenandoahObjToScanQueue* q = get_queue(w);
  ShenandoahObjToScanQueue* old_q = get_old_queue(w);

  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  ShenandoahLiveData* ld = heap->get_liveness_cache(w);

  // TODO: We can clean up this if we figure out how to do templated oop closures that
  // play nice with specialized_oop_iterators.
  if (update_refs) {
    using Closure = ShenandoahMarkUpdateRefsClosure<GENERATION>;
    Closure cl(q, rp, old_q);
    mark_loop_work<Closure, GENERATION, CANCELLABLE, STRING_DEDUP>(&cl, ld, w, t, req);
  } else {
    using Closure = ShenandoahMarkRefsClosure<GENERATION>;
    Closure cl(q, rp, old_q);
    mark_loop_work<Closure, GENERATION, CANCELLABLE, STRING_DEDUP>(&cl, ld, w, t, req);
  }

  heap->flush_liveness_cache(w);
}

template<bool CANCELLABLE, StringDedupMode STRING_DEDUP>
void ShenandoahMark::mark_loop(uint worker_id, TaskTerminator* terminator, ShenandoahReferenceProcessor *rp,
                               ShenandoahGenerationType generation, StringDedup::Requests* const req) {
  bool update_refs = ShenandoahHeap::heap()->has_forwarded_objects();
  switch (generation) {
    case YOUNG:
      mark_loop_prework<YOUNG, CANCELLABLE, STRING_DEDUP>(worker_id, terminator, rp, req, update_refs);
      break;
    case OLD:
      // Old generation collection only performs marking, it should not update references.
      mark_loop_prework<OLD, CANCELLABLE, STRING_DEDUP>(worker_id, terminator, rp, req, false);
      break;
    case GLOBAL:
      mark_loop_prework<GLOBAL, CANCELLABLE, STRING_DEDUP>(worker_id, terminator, rp, req, update_refs);
      break;
    case NON_GEN:
      mark_loop_prework<NON_GEN, CANCELLABLE, STRING_DEDUP>(worker_id, terminator, rp, req, update_refs);
      break;
    default:
      ShouldNotReachHere();
      break;
  }
}

void ShenandoahMark::mark_loop(uint worker_id, TaskTerminator* terminator, ShenandoahReferenceProcessor *rp,
                               ShenandoahGenerationType generation, bool cancellable, StringDedupMode dedup_mode, StringDedup::Requests* const req) {
  if (cancellable) {
    switch(dedup_mode) {
      case NO_DEDUP:
        mark_loop<true, NO_DEDUP>(worker_id, terminator, rp, generation, req);
        break;
      case ENQUEUE_DEDUP:
        mark_loop<true, ENQUEUE_DEDUP>(worker_id, terminator, rp, generation, req);
        break;
      case ALWAYS_DEDUP:
        mark_loop<true, ALWAYS_DEDUP>(worker_id, terminator, rp, generation, req);
        break;
    }
  } else {
    switch(dedup_mode) {
      case NO_DEDUP:
        mark_loop<false, NO_DEDUP>(worker_id, terminator, rp, generation, req);
        break;
      case ENQUEUE_DEDUP:
        mark_loop<false, ENQUEUE_DEDUP>(worker_id, terminator, rp, generation, req);
        break;
      case ALWAYS_DEDUP:
        mark_loop<false, ALWAYS_DEDUP>(worker_id, terminator, rp, generation, req);
        break;
    }
  }
}

template <class T, ShenandoahGenerationType GENERATION, bool CANCELLABLE, StringDedupMode STRING_DEDUP>
void ShenandoahMark::mark_loop_work(T* cl, ShenandoahLiveData* live_data, uint worker_id, TaskTerminator *terminator, StringDedup::Requests* const req) {
  uintx stride = ShenandoahMarkLoopStride;

  ShenandoahHeap* heap = ShenandoahHeap::heap();
  ShenandoahObjToScanQueueSet* queues = task_queues();
  ShenandoahObjToScanQueue* q;
  ShenandoahMarkTask t;

  // Do not use active_generation() : we must use the gc_generation() set by
  // ShenandoahGCScope on the ControllerThread's stack; no safepoint may
  // intervene to update active_generation, so we can't
  // shenandoah_assert_generations_reconciled() here.
  assert(heap->gc_generation()->type() == GENERATION, "Sanity: %d != %d", heap->gc_generation()->type(), GENERATION);
  heap->gc_generation()->ref_processor()->set_mark_closure(worker_id, cl);

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
  while (q != nullptr) {
    if (CANCELLABLE && heap->check_cancelled_gc_and_yield()) {
      return;
    }

    for (uint i = 0; i < stride; i++) {
      if (q->pop(t)) {
        do_task<T, GENERATION, STRING_DEDUP>(q, cl, live_data, req, &t, worker_id);
      } else {
        assert(q->is_empty(), "Must be empty");
        q = queues->claim_next();
        break;
      }
    }
  }
  q = get_queue(worker_id);
  ShenandoahObjToScanQueue* old_q = get_old_queue(worker_id);

  ShenandoahSATBBufferClosure<GENERATION> drain_satb(q, old_q);
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
        do_task<T, GENERATION, STRING_DEDUP>(q, cl, live_data, req, &t, worker_id);
        work++;
      } else {
        break;
      }
    }

    if (work == 0) {
      // No work encountered in current stride, try to terminate.
      // Need to leave the STS here otherwise it might block safepoints.
      ShenandoahSuspendibleThreadSetLeaver stsl(CANCELLABLE);
      ShenandoahTerminatorTerminator tt(heap);
      if (terminator->offer_termination(&tt)) return;
    }
  }
}
