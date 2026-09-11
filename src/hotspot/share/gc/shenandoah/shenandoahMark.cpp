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
  _old_gen_task_queues(generation->old_gen_task_queues()),
  _string_dedup(StringDedup::is_enabled()) {
}

template <ShenandoahGenerationType GENERATION, bool CANCELLABLE, bool STRING_DEDUP>
void ShenandoahMark::mark_loop_prework(uint w, TaskTerminator *t, StringDedup::Requests* const req, bool update_refs) {
  ShenandoahObjToScanQueueSet* queues = task_queues();
  ShenandoahObjToScanQueue* q = get_queue(w);
  ShenandoahObjToScanQueue* old_q = get_old_queue(w);
  ShenandoahReferenceProcessor *rp = _generation->ref_processor();
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  ShenandoahLiveData* ld = heap->get_liveness_cache(w);

  // Take outstanding work from queues not covered by current workers.
  // We expect there is little work in those queues.
  mark_drain_extra_queues<CANCELLABLE>(queues, q);

  // TODO: We can clean up this if we figure out how to do templated oop closures that
  // play nice with specialized_oop_iterators.
  if (update_refs) {
    using Closure = ShenandoahMarkUpdateRefsClosure<GENERATION>;
    Closure cl(q, rp, old_q);
    if (UseCompressedOops) {
      mark_loop_work<Closure, narrowOop, GENERATION, CANCELLABLE, STRING_DEDUP>(&cl, ld, w, t, req);
    } else {
      mark_loop_work<Closure, oop, GENERATION, CANCELLABLE, STRING_DEDUP>(&cl, ld, w, t, req);
    }
  } else {
    using Closure = ShenandoahMarkRefsClosure<GENERATION>;
    Closure cl(q, rp, old_q);
    if (UseCompressedOops) {
      mark_loop_work<Closure, narrowOop, GENERATION, CANCELLABLE, STRING_DEDUP>(&cl, ld, w, t, req);
    } else {
      mark_loop_work<Closure, oop, GENERATION, CANCELLABLE, STRING_DEDUP>(&cl, ld, w, t, req);
    }
  }

  heap->flush_liveness_cache(w);
}

template<bool CANCELLABLE, bool STRING_DEDUP>
void ShenandoahMark::mark_loop(uint worker_id, TaskTerminator* terminator,
                               ShenandoahGenerationType generation_type, StringDedup::Requests* const req) {
  bool update_refs = ShenandoahHeap::heap()->has_forwarded_objects();
  switch (generation_type) {
    case YOUNG:
      mark_loop_prework<YOUNG, CANCELLABLE, STRING_DEDUP>(worker_id, terminator, req, update_refs);
      break;
    case OLD:
      // Old generation collection only performs marking, it should not update references.
      mark_loop_prework<OLD, CANCELLABLE, STRING_DEDUP>(worker_id, terminator, req, false);
      break;
    case GLOBAL:
      mark_loop_prework<GLOBAL, CANCELLABLE, STRING_DEDUP>(worker_id, terminator, req, update_refs);
      break;
    case NON_GEN:
      mark_loop_prework<NON_GEN, CANCELLABLE, STRING_DEDUP>(worker_id, terminator, req, update_refs);
      break;
    default:
      ShouldNotReachHere();
      break;
  }
}

void ShenandoahMark::mark_loop(uint worker_id, TaskTerminator* terminator, ShenandoahGenerationType generation_type,
                               bool cancellable) {
  if (_string_dedup) {
    StringDedup::Requests req;
    if (cancellable) {
      mark_loop<true, true>(worker_id, terminator, generation_type, &req);
    } else {
      mark_loop<false, true>(worker_id, terminator, generation_type, &req);
    }
  } else {
    if (cancellable) {
      mark_loop<true, false>(worker_id, terminator, generation_type, nullptr);
    } else {
      mark_loop<false, false>(worker_id, terminator, generation_type, nullptr);
    }
  }
}

template <bool CANCELLABLE>
void ShenandoahMark::mark_drain_extra_queues(ShenandoahObjToScanQueueSet* queues, ShenandoahObjToScanQueue* local_q) {
  uintx stride = ShenandoahMarkLoopStride;

  ShenandoahHeap* heap = ShenandoahHeap::heap();
  ShenandoahMarkTask t;

  assert(queues->get_reserved() == heap->workers()->active_workers(),
         "Safety: claimable queues do not intersect with worker queues: %u == %u",
         queues->get_reserved(), heap->workers()->active_workers());

  ShenandoahObjToScanQueue* q = queues->claim_next();
  while (q != nullptr) {
    while (!q->is_empty()) {
      if (CANCELLABLE && heap->check_cancelled_gc_and_yield()) {
        return;
      }
      for (uint i = 0; i < stride; i++) {
        if (q->pop(t)) {
          local_q->push(t);
        } else {
          break;
        }
      }
    }
    q = queues->claim_next();
  }
}

template <class T, class OT, ShenandoahGenerationType GENERATION, bool CANCELLABLE, bool STRING_DEDUP>
void ShenandoahMark::mark_loop_work(T* cl, ShenandoahLiveData* live_data, uint worker_id, TaskTerminator *terminator, StringDedup::Requests* const req) {
  uintx stride = ShenandoahMarkLoopStride;

  ShenandoahHeap* heap = ShenandoahHeap::heap();
  ShenandoahObjToScanQueueSet* queues = task_queues();
  ShenandoahObjToScanQueue* q = get_queue(worker_id);
  ShenandoahObjToScanQueue* old_q = get_old_queue(worker_id);
  ShenandoahMarkTask t;

  assert(_generation->type() == GENERATION, "Sanity: %d != %d", _generation->type(), GENERATION);
  _generation->ref_processor()->set_mark_closure(worker_id, cl);

  ShenandoahSATBBufferClosure<GENERATION> drain_satb(q, old_q);
  SATBMarkQueueSet& satb_mq_set = ShenandoahBarrierSet::satb_mark_queue_set();

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
        do_task<T, OT, GENERATION, STRING_DEDUP>(q, cl, live_data, req, &t, worker_id);
        work++;
      } else {
        break;
      }
    }

    if (work == 0) {
      // No work encountered in current stride, try to terminate.
      // Need to leave the STS here otherwise it might block safepoints.
      SuspendibleThreadSetLeaver stsl(CANCELLABLE);
      ShenandoahTerminatorTerminator tt(heap);
      if (terminator->offer_termination(&tt)) return;
    }
  }
}
