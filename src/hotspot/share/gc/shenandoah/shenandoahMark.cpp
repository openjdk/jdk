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
  _generation(generation),
  _task_queues(generation->task_queues()),
  _old_gen_task_queues(generation->old_gen_task_queues()),
  _remembered_set_scanner(ShenandoahHeap::heap()),
  _string_dedup(StringDedup::is_enabled()),
  _bootstrapping(bootstrapping) {
}

template <ShenandoahGenerationType GENERATION, bool CANCELLABLE, bool STRING_DEDUP>
void ShenandoahMark::mark_loop_prework(uint w, TaskTerminator *t, StringDedup::Requests* const req) {
  ShenandoahObjToScanQueue* q = get_queue(w);
  ShenandoahObjToScanQueue* old_q = _bootstrapping ? get_old_queue(w) : nullptr;
  ShenandoahReferenceProcessor *rp = _generation->ref_processor();
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  ShenandoahLiveData* ld = heap->get_liveness_cache(w);

  // TODO: We can clean up this if we figure out how to do templated oop closures that
  // play nice with specialized_oop_iterators.
  using Closure = ShenandoahMarkRefsClosure<GENERATION>;
  Closure cl(q, rp, old_q);
  if (UseCompressedOops) {
    mark_loop_work<Closure, narrowOop, GENERATION, CANCELLABLE, STRING_DEDUP>(&cl, ld, w, t, req);
  } else {
    mark_loop_work<Closure, oop, GENERATION, CANCELLABLE, STRING_DEDUP>(&cl, ld, w, t, req);
  }

  heap->flush_liveness_cache(w);
}

template<ShenandoahGenerationType GENERATION, bool CANCELLABLE>
void ShenandoahMark::mark_loop(uint worker_id, TaskTerminator* terminator) {
  if (_string_dedup) {
    StringDedup::Requests req;
    mark_loop_prework<GENERATION, CANCELLABLE, true>(worker_id, terminator, &req);
  } else {
    mark_loop_prework<GENERATION, CANCELLABLE, false>(worker_id, terminator, nullptr);
  }
}

bool ShenandoahMark::try_process_remembered_set_chunks(uint worker_id, ShenandoahHeap* heap, ShenandoahObjToScanQueue* q) {
  ShenandoahRedirtyCardsMarkClosure rset_closure(q, _generation->ref_processor());
  _generation->ref_processor()->set_mark_closure(worker_id, &rset_closure);

  uintx chunks_scanned = 0;
  do {
    // Always try to scan at least one chunk. Continue scanning until there is sufficient
    // mark work, or until we have scanned too many chunks.
    if (!try_process_remembered_set_chunk(worker_id, heap, &rset_closure)) {
      return false;
    }
    ++chunks_scanned;
  } while (q->full_size() < ShenandoahMarkLoopStride && chunks_scanned < ShenandoahRememberedSetLoopStride);
  return true;
}

template <typename ClosureType>
bool ShenandoahMark::try_process_remembered_set_chunk(uint worker_id, ShenandoahHeap* heap, ClosureType* rset_closure) {
  ShenandoahRegionChunk assignment;
  if (!_remembered_set_scanner.next(&assignment)) {
    return false;
  }

  ShenandoahScanRemembered* scanner = heap->old_generation()->card_scan();
  scanner->process_chunk_for_mark(assignment, rset_closure, worker_id);
  return true;
}

template <class T, class OT, ShenandoahGenerationType GENERATION, bool CANCELLABLE, bool STRING_DEDUP>
void ShenandoahMark::mark_loop_work(T* cl, ShenandoahLiveData* live_data, uint worker_id, TaskTerminator *terminator, StringDedup::Requests* const req) {
  const uintx stride = ShenandoahMarkLoopStride;

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
    if (GENERATION == YOUNG) {
      if (try_process_remembered_set_chunks(worker_id, heap, q)) {
        // This counts as work even if the scanned chunk did not put any oops in our queue.
        // We do not want this thread to enter termination just because there were no interesting
        // pointers on the scanned chunk.
        work++;
      }
      _generation->ref_processor()->set_mark_closure(worker_id, cl);
    }

    for (uint i = 0; i < stride; i++) {
      if (q->pop(t) || queues->steal(worker_id, t)) {
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

template void ShenandoahMark::mark_loop<YOUNG, true>(uint worker_id, TaskTerminator* terminator);
template void ShenandoahMark::mark_loop<OLD, true>(uint worker_id, TaskTerminator* terminator);
template void ShenandoahMark::mark_loop<GLOBAL, true>(uint worker_id, TaskTerminator* terminator);
template void ShenandoahMark::mark_loop<NON_GEN, true>(uint worker_id, TaskTerminator* terminator);
template void ShenandoahMark::mark_loop<YOUNG, false>(uint worker_id, TaskTerminator* terminator);
template void ShenandoahMark::mark_loop<OLD, false>(uint worker_id, TaskTerminator* terminator);
template void ShenandoahMark::mark_loop<GLOBAL, false>(uint worker_id, TaskTerminator* terminator);
template void ShenandoahMark::mark_loop<NON_GEN, false>(uint worker_id, TaskTerminator* terminator);
