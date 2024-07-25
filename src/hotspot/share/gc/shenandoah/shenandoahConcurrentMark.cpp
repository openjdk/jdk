/*
 * Copyright (c) 2013, 2021, Red Hat, Inc. All rights reserved.
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

#include "gc/shared/satbMarkQueue.hpp"
#include "gc/shared/strongRootsScope.hpp"
#include "gc/shared/taskTerminator.hpp"
#include "gc/shenandoah/shenandoahBarrierSet.inline.hpp"
#include "gc/shenandoah/shenandoahClosures.inline.hpp"
#include "gc/shenandoah/shenandoahConcurrentMark.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahMark.inline.hpp"
#include "gc/shenandoah/shenandoahReferenceProcessor.hpp"
#include "gc/shenandoah/shenandoahRootProcessor.inline.hpp"
#include "gc/shenandoah/shenandoahOopClosures.inline.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shenandoah/shenandoahStringDedup.hpp"
#include "gc/shenandoah/shenandoahTaskqueue.inline.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/continuation.hpp"
#include "runtime/threads.hpp"

template <ShenandoahGenerationType GENERATION>
class ShenandoahConcurrentMarkingTask : public WorkerTask {
private:
  ShenandoahConcurrentMark* const _cm;
  TaskTerminator* const           _terminator;

public:
  ShenandoahConcurrentMarkingTask(ShenandoahConcurrentMark* cm, TaskTerminator* terminator) :
    WorkerTask("Shenandoah Concurrent Mark"), _cm(cm), _terminator(terminator) {
  }

  void work(uint worker_id) {
    ShenandoahHeap* heap = ShenandoahHeap::heap();
    ShenandoahConcurrentWorkerSession worker_session(worker_id);
    ShenandoahSuspendibleThreadSetJoiner stsj;
    ShenandoahReferenceProcessor* rp = heap->ref_processor();
    assert(rp != nullptr, "need reference processor");
    StringDedup::Requests requests;
    _cm->mark_loop(worker_id, _terminator, rp, GENERATION, true /*cancellable*/,
                   ShenandoahStringDedup::is_enabled() ? ENQUEUE_DEDUP : NO_DEDUP,
                   &requests);
  }
};

class ShenandoahSATBAndRemarkThreadsClosure : public ThreadClosure {
private:
  SATBMarkQueueSet& _satb_qset;

public:
  explicit ShenandoahSATBAndRemarkThreadsClosure(SATBMarkQueueSet& satb_qset) :
    _satb_qset(satb_qset) {}

  void do_thread(Thread* thread) override {
    // Transfer any partial buffer to the qset for completed buffer processing.
    _satb_qset.flush_queue(ShenandoahThreadLocalData::satb_mark_queue(thread));
  }
};

template <ShenandoahGenerationType GENERATION>
class ShenandoahFinalMarkingTask : public WorkerTask {
private:
  ShenandoahConcurrentMark* _cm;
  TaskTerminator*           _terminator;
  bool                      _dedup_string;

public:
  ShenandoahFinalMarkingTask(ShenandoahConcurrentMark* cm, TaskTerminator* terminator, bool dedup_string) :
    WorkerTask("Shenandoah Final Mark"), _cm(cm), _terminator(terminator), _dedup_string(dedup_string) {
  }

  void work(uint worker_id) {
    ShenandoahHeap* heap = ShenandoahHeap::heap();

    ShenandoahParallelWorkerSession worker_session(worker_id);
    ShenandoahReferenceProcessor* rp = heap->ref_processor();
    StringDedup::Requests requests;

    // First drain remaining SATB buffers.
    {
      ShenandoahObjToScanQueue* q = _cm->get_queue(worker_id);

      ShenandoahSATBBufferClosure<GENERATION> cl(q);
      SATBMarkQueueSet& satb_mq_set = ShenandoahBarrierSet::satb_mark_queue_set();
      while (satb_mq_set.apply_closure_to_completed_buffer(&cl)) {}
      assert(!heap->has_forwarded_objects(), "Not expected");

      ShenandoahSATBAndRemarkThreadsClosure tc(satb_mq_set);
      Threads::possibly_parallel_threads_do(true /* is_par */, &tc);
    }
    _cm->mark_loop(worker_id, _terminator, rp, GENERATION, false /*not cancellable*/,
                   _dedup_string ? ENQUEUE_DEDUP : NO_DEDUP,
                   &requests);
    assert(_cm->task_queues()->is_empty(), "Should be empty");
  }
};

ShenandoahConcurrentMark::ShenandoahConcurrentMark() :
  ShenandoahMark() {}

// Mark concurrent roots during concurrent phases
template <ShenandoahGenerationType GENERATION>
class ShenandoahMarkConcurrentRootsTask : public WorkerTask {
private:
  SuspendibleThreadSetJoiner          _sts_joiner;
  ShenandoahConcurrentRootScanner     _root_scanner;
  ShenandoahObjToScanQueueSet* const  _queue_set;
  ShenandoahReferenceProcessor* const _rp;

public:
  ShenandoahMarkConcurrentRootsTask(ShenandoahObjToScanQueueSet* qs,
                                    ShenandoahReferenceProcessor* rp,
                                    ShenandoahPhaseTimings::Phase phase,
                                    uint nworkers);
  void work(uint worker_id);
};

template <ShenandoahGenerationType GENERATION>
ShenandoahMarkConcurrentRootsTask<GENERATION>::ShenandoahMarkConcurrentRootsTask(ShenandoahObjToScanQueueSet* qs,
                                                                     ShenandoahReferenceProcessor* rp,
                                                                     ShenandoahPhaseTimings::Phase phase,
                                                                     uint nworkers) :
  WorkerTask("Shenandoah Concurrent Mark Roots"),
  _root_scanner(nworkers, phase),
  _queue_set(qs),
  _rp(rp) {
  assert(!ShenandoahHeap::heap()->has_forwarded_objects(), "Not expected");
}

template <ShenandoahGenerationType GENERATION>
void ShenandoahMarkConcurrentRootsTask<GENERATION>::work(uint worker_id) {
  ShenandoahConcurrentWorkerSession worker_session(worker_id);
  ShenandoahObjToScanQueue* q = _queue_set->queue(worker_id);
  ShenandoahMarkRefsClosure<GENERATION> cl(q, _rp);
  _root_scanner.roots_do(&cl, worker_id);
}

void ShenandoahConcurrentMark::mark_concurrent_roots() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  assert(!heap->has_forwarded_objects(), "Not expected");

  TASKQUEUE_STATS_ONLY(task_queues()->reset_taskqueue_stats());

  WorkerThreads* workers = heap->workers();
  ShenandoahReferenceProcessor* rp = heap->ref_processor();
  task_queues()->reserve(workers->active_workers());
  ShenandoahMarkConcurrentRootsTask<NON_GEN> task(task_queues(), rp, ShenandoahPhaseTimings::conc_mark_roots, workers->active_workers());

  workers->run_task(&task);
}

class ShenandoahFlushSATBHandshakeClosure : public HandshakeClosure {
private:
  SATBMarkQueueSet& _qset;
public:
  ShenandoahFlushSATBHandshakeClosure(SATBMarkQueueSet& qset) :
    HandshakeClosure("Shenandoah Flush SATB Handshake"),
    _qset(qset) {}

  void do_thread(Thread* thread) {
    _qset.flush_queue(ShenandoahThreadLocalData::satb_mark_queue(thread));
  }
};

void ShenandoahConcurrentMark::concurrent_mark() {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  WorkerThreads* workers = heap->workers();
  uint nworkers = workers->active_workers();
  task_queues()->reserve(nworkers);

  ShenandoahSATBMarkQueueSet& qset = ShenandoahBarrierSet::satb_mark_queue_set();
  ShenandoahFlushSATBHandshakeClosure flush_satb(qset);
  for (uint flushes = 0; flushes < ShenandoahMaxSATBBufferFlushes; flushes++) {
    TaskTerminator terminator(nworkers, task_queues());
    ShenandoahConcurrentMarkingTask<NON_GEN> task(this, &terminator);
    workers->run_task(&task);

    if (heap->cancelled_gc()) {
      // GC is cancelled, break out.
      break;
    }

    size_t before = qset.completed_buffers_num();
    Handshake::execute(&flush_satb);
    size_t after = qset.completed_buffers_num();

    if (before == after) {
      // No more retries needed, break out.
      break;
    }
  }
  assert(task_queues()->is_empty() || heap->cancelled_gc(), "Should be empty when not cancelled");
}

void ShenandoahConcurrentMark::finish_mark() {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Must be at a safepoint");
  assert(Thread::current()->is_VM_thread(), "Must by VM Thread");
  finish_mark_work();
  assert(task_queues()->is_empty(), "Should be empty");
  TASKQUEUE_STATS_ONLY(task_queues()->print_taskqueue_stats());
  TASKQUEUE_STATS_ONLY(task_queues()->reset_taskqueue_stats());

  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  heap->set_concurrent_mark_in_progress(false);
  heap->mark_complete_marking_context();

  end_mark();
}

void ShenandoahConcurrentMark::finish_mark_work() {
  // Finally mark everything else we've got in our queues during the previous steps.
  // It does two different things for concurrent vs. mark-compact GC:
  // - For concurrent GC, it starts with empty task queues, drains the remaining
  //   SATB buffers, and then completes the marking closure.
  // - For mark-compact GC, it starts out with the task queues seeded by initial
  //   root scan, and completes the closure, thus marking through all live objects
  // The implementation is the same, so it's shared here.
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  ShenandoahGCPhase phase(ShenandoahPhaseTimings::finish_mark);
  uint nworkers = heap->workers()->active_workers();
  task_queues()->reserve(nworkers);

  StrongRootsScope scope(nworkers);
  TaskTerminator terminator(nworkers, task_queues());
  ShenandoahFinalMarkingTask<NON_GEN> task(this, &terminator, ShenandoahStringDedup::is_enabled());
  heap->workers()->run_task(&task);

  assert(task_queues()->is_empty(), "Should be empty");
}


void ShenandoahConcurrentMark::cancel() {
  clear();
  ShenandoahReferenceProcessor* rp = ShenandoahHeap::heap()->ref_processor();
  rp->abandon_partial_discovery();
}
