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

ShenandoahMark::ShenandoahMark(ShenandoahGeneration* generation, bool bootstrapping) :
  _heap(ShenandoahHeap::heap()),
  _generation(generation),
  _task_queues(generation->task_queues()),
  _old_gen_task_queues(generation->old_gen_task_queues()),
  _string_dedup(StringDedup::is_enabled()),
  _bootstrapping(bootstrapping) {
}

template<ShenandoahGenerationType GENERATION, bool CANCELLABLE>
void ShenandoahMark::mark_loop(uint worker_id, TaskTerminator* terminator) {
  ShenandoahLiveData* ld = _heap->get_liveness_cache(worker_id);

  if (_string_dedup) {
    StringDedup::Requests req;
    if (UseCompressedOops) {
      mark_loop_work<narrowOop, GENERATION, CANCELLABLE, true>(worker_id, terminator, ld, &req);
    } else {
      mark_loop_work<oop, GENERATION, CANCELLABLE, true>(worker_id, terminator, ld, &req);
    }
  } else {
    if (UseCompressedOops) {
      mark_loop_work<narrowOop, GENERATION, CANCELLABLE, false>(worker_id, terminator, ld, nullptr);
    } else {
      mark_loop_work<oop, GENERATION, CANCELLABLE, false>(worker_id, terminator, ld, nullptr);
    }
  }

  _heap->flush_liveness_cache(worker_id);
}

template void ShenandoahMark::mark_loop<NON_GEN, true>(uint worker_id, TaskTerminator* terminator);
template void ShenandoahMark::mark_loop<GLOBAL,  true>(uint worker_id, TaskTerminator* terminator);
template void ShenandoahMark::mark_loop<YOUNG,   true>(uint worker_id, TaskTerminator* terminator);
template void ShenandoahMark::mark_loop<OLD,     true>(uint worker_id, TaskTerminator* terminator);

template void ShenandoahMark::mark_loop<NON_GEN, false>(uint worker_id, TaskTerminator* terminator);
template void ShenandoahMark::mark_loop<GLOBAL,  false>(uint worker_id, TaskTerminator* terminator);
template void ShenandoahMark::mark_loop<YOUNG,   false>(uint worker_id, TaskTerminator* terminator);
template void ShenandoahMark::mark_loop<OLD,     false>(uint worker_id, TaskTerminator* terminator);

template <class OT, ShenandoahGenerationType GENERATION, bool CANCELLABLE, bool STRING_DEDUP>
void ShenandoahMark::mark_loop_work(uint worker_id, TaskTerminator* terminator, ShenandoahLiveData* live_data, StringDedup::Requests* const req) {
  assert(_generation->type() == GENERATION, "Sanity: %d != %d", _generation->type(), GENERATION);

  const uintx stride = ShenandoahMarkLoopStride;

  ShenandoahObjToScanQueueSet* queues = task_queues();
  ShenandoahObjToScanQueue* q = get_queue(worker_id);
  ShenandoahObjToScanQueue* old_q = _bootstrapping ? get_old_queue(worker_id) : nullptr;
  ShenandoahObjToScanQueue* old_q_satb = get_old_queue(worker_id);

  SATBMarkQueueSet& satb_mq_set = ShenandoahBarrierSet::satb_mark_queue_set();
  ShenandoahSATBBufferClosure<GENERATION> drain_satb(q, old_q_satb);

  ShenandoahReferenceProcessor* rp = _generation->ref_processor();

  using MarkClosure = ShenandoahMarkRefsClosure<GENERATION>;
  MarkClosure cl(q, rp, old_q);
  rp->set_mark_closure(worker_id, &cl);

  ShenandoahMarkTask t;

  while (true) {
    if (CANCELLABLE && _heap->check_cancelled_gc_and_yield()) {
      return;
    }

    while (satb_mq_set.completed_buffers_num() > 0) {
      satb_mq_set.apply_closure_to_completed_buffer(&drain_satb);
    }

    bool no_work = true;
    for (uint i = 0; i < stride; i++) {
      if (q->pop(t) || queues->steal(worker_id, t)) {
        do_task<MarkClosure, OT, GENERATION, STRING_DEDUP>(q, &cl, live_data, req, &t, worker_id);
        no_work = false;
      } else {
        break;
      }
    }

    if (no_work) {
      // No work encountered in current stride, try to terminate.
      // Need to leave the STS here otherwise it might block safepoints.
      SuspendibleThreadSetLeaver stsl(CANCELLABLE);
      ShenandoahTerminatorTerminator tt(_heap);
      if (terminator->offer_termination(&tt)) return;
    }
  }
}
