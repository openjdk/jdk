/*
 * Copyright (c) 2020, Red Hat, Inc. All rights reserved.
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

#include "gc/shared/gcTrace.hpp"
#include "gc/shared/referenceProcessorPhaseTimes.hpp"

#include "gc/shenandoah/shenandoahBarrierSet.hpp"
#include "gc/shenandoah/shenandoahClosures.inline.hpp"
#include "gc/shenandoah/shenandoahMark.inline.hpp"
#include "gc/shenandoah/shenandoahOopClosures.inline.hpp"
#include "gc/shenandoah/shenandoahVerifier.hpp"
#include "gc/shenandoah/shenandoahTaskqueue.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"

ShenandoahMarkRefsSuperClosure::ShenandoahMarkRefsSuperClosure(ShenandoahObjToScanQueue* q, ReferenceProcessor* rp) :
  MetadataVisitingOopIterateClosure(rp),
  _queue(q),
  _heap(ShenandoahHeap::heap()),
  _mark_context(_heap->marking_context())
{ }

ShenandoahInitMarkRootsClosure::ShenandoahInitMarkRootsClosure(ShenandoahObjToScanQueue* q) :
  _queue(q),
  _heap(ShenandoahHeap::heap()),
  _mark_context(_heap->marking_context()) {
}

ShenandoahMark::ShenandoahMark() :
  _heap(ShenandoahHeap::heap()),
  _task_queues(_heap->task_queues()) {
}

void ShenandoahMark::clear() {
  // Clean up marking stacks.
  ShenandoahObjToScanQueueSet* queues = ShenandoahHeap::heap()->task_queues();
  queues->clear();

  // Cancel SATB buffers.
  ShenandoahBarrierSet::satb_mark_queue_set().abandon_partial_marking();
}

template <bool CANCELLABLE>
void ShenandoahMark::mark_loop_prework(uint w, TaskTerminator *t, ReferenceProcessor *rp,
                                       bool strdedup) {
  ShenandoahObjToScanQueue* q = get_queue(w);

  ShenandoahLiveData* ld = _heap->get_liveness_cache(w);

  // TODO: We can clean up this if we figure out how to do templated oop closures that
  // play nice with specialized_oop_iterators.
  if (_heap->unload_classes()) {
    if (_heap->has_forwarded_objects()) {
      if (strdedup) {
        ShenandoahMarkUpdateRefsMetadataDedupClosure cl(q, rp);
        mark_loop_work<ShenandoahMarkUpdateRefsMetadataDedupClosure, CANCELLABLE>(&cl, ld, w, t);
      } else {
        ShenandoahMarkUpdateRefsMetadataClosure cl(q, rp);
        mark_loop_work<ShenandoahMarkUpdateRefsMetadataClosure, CANCELLABLE>(&cl, ld, w, t);
      }
    } else {
      if (strdedup) {
        ShenandoahMarkRefsMetadataDedupClosure cl(q, rp);
        mark_loop_work<ShenandoahMarkRefsMetadataDedupClosure, CANCELLABLE>(&cl, ld, w, t);
      } else {
        ShenandoahMarkRefsMetadataClosure cl(q, rp);
        mark_loop_work<ShenandoahMarkRefsMetadataClosure, CANCELLABLE>(&cl, ld, w, t);
      }
    }
  } else {
    if (_heap->has_forwarded_objects()) {
      if (strdedup) {
        ShenandoahMarkUpdateRefsDedupClosure cl(q, rp);
        mark_loop_work<ShenandoahMarkUpdateRefsDedupClosure, CANCELLABLE>(&cl, ld, w, t);
      } else {
        ShenandoahMarkUpdateRefsClosure cl(q, rp);
        mark_loop_work<ShenandoahMarkUpdateRefsClosure, CANCELLABLE>(&cl, ld, w, t);
      }
    } else {
      if (strdedup) {
        ShenandoahMarkRefsDedupClosure cl(q, rp);
        mark_loop_work<ShenandoahMarkRefsDedupClosure, CANCELLABLE>(&cl, ld, w, t);
      } else {
        ShenandoahMarkRefsClosure cl(q, rp);
        mark_loop_work<ShenandoahMarkRefsClosure, CANCELLABLE>(&cl, ld, w, t);
      }
    }
  }

  _heap->flush_liveness_cache(w);
}

template <class T, bool CANCELLABLE>
void ShenandoahMark::mark_loop_work(T* cl, ShenandoahLiveData* live_data, uint worker_id, TaskTerminator *terminator) {
  uintx stride = ShenandoahMarkLoopStride;

  ShenandoahHeap* heap = ShenandoahHeap::heap();
  ShenandoahObjToScanQueueSet* queues = task_queues();
  ShenandoahObjToScanQueue* q;
  ShenandoahMarkTask t;

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

  ShenandoahSATBBufferClosure drain_satb(q);
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

void ShenandoahMark::mark_loop(uint worker_id, TaskTerminator* terminator, ReferenceProcessor *rp,
               bool cancellable, bool strdedup) {
  if (cancellable) {
    mark_loop_prework<true>(worker_id, terminator, rp, strdedup);
  } else {
    mark_loop_prework<false>(worker_id, terminator, rp, strdedup);
  }
}

// Weak Reference Closures
class ShenandoahCMDrainMarkingStackClosure: public VoidClosure {
  const uint            _worker_id;
  ShenandoahMark* const _mark;
  TaskTerminator* const _terminator;
  const bool            _reset_terminator;

public:
  ShenandoahCMDrainMarkingStackClosure(uint worker_id, ShenandoahMark* mark, TaskTerminator* t, bool reset_terminator = false):
    _worker_id(worker_id),
    _mark(mark),
    _terminator(t),
    _reset_terminator(reset_terminator) {
  }

  void do_void() {
    assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Must be at a safepoint");

    ShenandoahHeap* sh = ShenandoahHeap::heap();
    assert(sh->process_references(), "why else would we be here?");
    ReferenceProcessor* rp = sh->ref_processor();
    shenandoah_assert_rp_isalive_installed();

    _mark->mark_loop(_worker_id, _terminator, rp,
                   false,   // not cancellable
                   false);  // do not do strdedup

    if (_reset_terminator) {
      _terminator->reset_for_reuse();
    }
  }
};

class ShenandoahCMKeepAliveUpdateClosure : public OopClosure {
private:
  ShenandoahObjToScanQueue* _queue;
  ShenandoahHeap* _heap;
  ShenandoahMarkingContext* const _mark_context;

  template <class T>
  inline void do_oop_work(T* p) {
    ShenandoahMark::mark_through_ref<T, SIMPLE, NO_DEDUP>(p, _heap, _queue, _mark_context);
  }

public:
  ShenandoahCMKeepAliveUpdateClosure(ShenandoahObjToScanQueue* q) :
    _queue(q),
    _heap(ShenandoahHeap::heap()),
    _mark_context(_heap->marking_context()) {}

  void do_oop(narrowOop* p) { do_oop_work(p); }
  void do_oop(oop* p)       { do_oop_work(p); }
};

class ShenandoahRefProcTaskProxy : public AbstractGangTask {
private:
  AbstractRefProcTaskExecutor::ProcessTask& _proc_task;
  ShenandoahMark* const                     _mark;
  TaskTerminator* const                     _terminator;

public:
  ShenandoahRefProcTaskProxy(AbstractRefProcTaskExecutor::ProcessTask& proc_task,
                             ShenandoahMark* mark,
                             TaskTerminator* t) :
    AbstractGangTask("Shenandoah Process Weak References"),
    _proc_task(proc_task),
    _mark(mark),
    _terminator(t) {
  }

  void work(uint worker_id) {
    Thread* current_thread = Thread::current();
    ResourceMark rm(current_thread);
    HandleMark hm(current_thread);
    assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Must be at a safepoint");
    ShenandoahHeap* heap = ShenandoahHeap::heap();
    ShenandoahParallelWorkerSession worker_session(worker_id);
    ShenandoahCMDrainMarkingStackClosure complete_gc(worker_id, _mark, _terminator);
    if (heap->has_forwarded_objects()) {
      ShenandoahForwardedIsAliveClosure is_alive;
      ShenandoahCMKeepAliveUpdateClosure keep_alive(heap->task_queues()->queue(worker_id));
      _proc_task.work(worker_id, is_alive, keep_alive, complete_gc);
    } else {
      ShenandoahIsAliveClosure is_alive;
      ShenandoahCMKeepAliveClosure keep_alive(heap->task_queues()->queue(worker_id));
      _proc_task.work(worker_id, is_alive, keep_alive, complete_gc);
    }
  }
};

class ShenandoahRefProcTaskExecutor : public AbstractRefProcTaskExecutor {
private:
  ShenandoahMark* const _mark;
  WorkGang* const       _workers;

public:
  ShenandoahRefProcTaskExecutor(ShenandoahMark* mark, WorkGang* workers) :
    _mark(mark),
    _workers(workers) {
  }

  // Executes a task using worker threads.
  void execute(ProcessTask& task, uint ergo_workers) {
    assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Must be at a safepoint");

    ShenandoahHeap* heap = ShenandoahHeap::heap();
    ShenandoahObjToScanQueueSet* task_queues = heap->task_queues();
    ShenandoahPushWorkerQueuesScope scope(_workers, task_queues,
                                          ergo_workers,
                                          /* do_check = */ false);
    uint nworkers = _workers->active_workers();
    task_queues->reserve(nworkers);
    TaskTerminator terminator(nworkers, task_queues);
    ShenandoahRefProcTaskProxy proc_task_proxy(task, _mark, &terminator);
    _workers->run_task(&proc_task_proxy);
  }
};


void ShenandoahMark::process_weak_refs(bool full_gc) {
  if (_heap->process_references()) {
    ShenandoahPhaseTimings::Phase phase_root =
            full_gc ?
            ShenandoahPhaseTimings::full_gc_weakrefs :
            ShenandoahPhaseTimings::degen_gc_weakrefs;

    ShenandoahGCPhase phase(phase_root);

    ReferenceProcessor* rp = _heap->ref_processor();

    // NOTE: We cannot shortcut on has_discovered_references() here, because
    // we will miss marking JNI Weak refs then, see implementation in
    // ReferenceProcessor::process_discovered_references.
    process_weak_refs_work(full_gc);

    rp->verify_no_references_recorded();
    assert(!rp->discovery_enabled(), "Post condition");
  }
}

void ShenandoahMark::process_weak_refs_work(bool full_gc) {
  ReferenceProcessor* rp = _heap->ref_processor();

  ShenandoahPhaseTimings::Phase phase_process =
          full_gc ?
          ShenandoahPhaseTimings::full_gc_weakrefs_process :
          ShenandoahPhaseTimings::degen_gc_weakrefs_process;

  ShenandoahIsAliveSelector is_alive;
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
  TaskTerminator terminator(1, task_queues());
  ShenandoahCMDrainMarkingStackClosure complete_gc(serial_worker_id, this, &terminator, /* reset_terminator = */ true);

  ShenandoahRefProcTaskExecutor executor(this, workers);

  ReferenceProcessorPhaseTimes pt(heap()->gc_timer(), rp->num_queues());

  {
    // Note: Don't emit JFR event for this phase, to avoid overflow nesting phase level.
    // Reference Processor emits 2 levels JFR event, that can get us over the JFR
    // event nesting level limits, in case of degenerated GC gets upgraded to
    // full GC.
    ShenandoahTimingsTracker phase_timing(phase_process);

    if (_heap->has_forwarded_objects()) {
      ShenandoahCMKeepAliveUpdateClosure keep_alive(get_queue(serial_worker_id));
      const ReferenceProcessorStats& stats =
        rp->process_discovered_references(is_alive.is_alive_closure(), &keep_alive,
                                          &complete_gc, &executor,
                                          &pt);
       _heap->tracer()->report_gc_reference_stats(stats);
    } else {
      ShenandoahCMKeepAliveClosure keep_alive(get_queue(serial_worker_id));
      const ReferenceProcessorStats& stats =
        rp->process_discovered_references(is_alive.is_alive_closure(), &keep_alive,
                                          &complete_gc, &executor,
                                          &pt);
      _heap->tracer()->report_gc_reference_stats(stats);
    }

    pt.print_all_references();

    assert(task_queues()->is_empty(), "Should be empty");
  }
}

