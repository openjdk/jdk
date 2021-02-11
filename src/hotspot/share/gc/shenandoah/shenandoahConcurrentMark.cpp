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

#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "code/codeCache.hpp"

#include "gc/shared/weakProcessor.inline.hpp"
#include "gc/shared/gcTimer.hpp"
#include "gc/shared/gcTrace.hpp"
#include "gc/shared/strongRootsScope.hpp"

#include "gc/shenandoah/shenandoahBarrierSet.inline.hpp"
#include "gc/shenandoah/shenandoahClosures.inline.hpp"
#include "gc/shenandoah/shenandoahConcurrentMark.hpp"
#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahMark.inline.hpp"
#include "gc/shenandoah/shenandoahReferenceProcessor.hpp"
#include "gc/shenandoah/shenandoahRootProcessor.inline.hpp"
#include "gc/shenandoah/shenandoahOopClosures.inline.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shenandoah/shenandoahStringDedup.hpp"
#include "gc/shenandoah/shenandoahTaskqueue.inline.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "gc/shenandoah/shenandoahScanRemembered.inline.hpp"

#include "memory/iterator.inline.hpp"
#include "memory/metaspace.hpp"
#include "memory/resourceArea.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/handles.inline.hpp"


class ShenandoahUpdateRootsTask : public AbstractGangTask {
private:
  ShenandoahRootUpdater*  _root_updater;
  bool                    _check_alive;
public:
  ShenandoahUpdateRootsTask(ShenandoahRootUpdater* root_updater, bool check_alive) :
    AbstractGangTask("Shenandoah Update Roots"),
    _root_updater(root_updater),
    _check_alive(check_alive){
  }

  void work(uint worker_id) {
    assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Must be at a safepoint");
    ShenandoahParallelWorkerSession worker_session(worker_id);

    ShenandoahHeap* heap = ShenandoahHeap::heap();
    ShenandoahUpdateRefsClosure cl;
    if (_check_alive) {
      ShenandoahForwardedIsAliveClosure is_alive;
      _root_updater->roots_do<ShenandoahForwardedIsAliveClosure, ShenandoahUpdateRefsClosure>(worker_id, &is_alive, &cl);
    } else {
      AlwaysTrueClosure always_true;;
      _root_updater->roots_do<AlwaysTrueClosure, ShenandoahUpdateRefsClosure>(worker_id, &always_true, &cl);
    }
  }
};

template <GenerationMode GENERATION>
class ShenandoahConcurrentMarkingTask : public AbstractGangTask {
private:
  ShenandoahConcurrentMark* const _cm;
  TaskTerminator* const           _terminator;

public:
  ShenandoahConcurrentMarkingTask(ShenandoahConcurrentMark* cm, TaskTerminator* terminator) :
    AbstractGangTask("Shenandoah Concurrent Mark"), _cm(cm), _terminator(terminator) {
  }

  void work(uint worker_id) {
    ShenandoahHeap* heap = ShenandoahHeap::heap();
    ShenandoahConcurrentWorkerSession worker_session(worker_id);
    ShenandoahSuspendibleThreadSetJoiner stsj(ShenandoahSuspendibleWorkers);
    ShenandoahObjToScanQueue* q = _cm->get_queue(worker_id);
    ShenandoahReferenceProcessor* rp = heap->ref_processor();
    assert(rp != NULL, "need reference processor");
    _cm->mark_loop(GENERATION, worker_id, _terminator, rp,
                   true, // cancellable
                   ShenandoahStringDedup::is_enabled()); // perform string dedup
  }
};

template <GenerationMode GENERATION>
class ShenandoahSATBAndRemarkCodeRootsThreadsClosure : public ThreadClosure {
private:
  ShenandoahSATBBufferClosure<GENERATION>* _satb_cl;
  OopClosure*            const _cl;
  MarkingCodeBlobClosure*      _code_cl;
  uintx _claim_token;

public:
  ShenandoahSATBAndRemarkCodeRootsThreadsClosure(ShenandoahSATBBufferClosure<GENERATION>* satb_cl, OopClosure* cl, MarkingCodeBlobClosure* code_cl) :
    _satb_cl(satb_cl), _cl(cl), _code_cl(code_cl),
    _claim_token(Threads::thread_claim_token()) {}

  void do_thread(Thread* thread) {
    if (thread->claim_threads_do(true, _claim_token)) {
      ShenandoahThreadLocalData::satb_mark_queue(thread).apply_closure_and_empty(_satb_cl);
      if (thread->is_Java_thread()) {
        if (_cl != NULL) {
          ResourceMark rm;
          thread->oops_do(_cl, _code_cl);
        } else if (_code_cl != NULL) {
          // In theory it should not be neccessary to explicitly walk the nmethods to find roots for concurrent marking
          // however the liveness of oops reachable from nmethods have very complex lifecycles:
          // * Alive if on the stack of an executing method
          // * Weakly reachable otherwise
          // Some objects reachable from nmethods, such as the class loader (or klass_holder) of the receiver should be
          // live by the SATB invariant but other oops recorded in nmethods may behave differently.
          thread->as_Java_thread()->nmethods_do(_code_cl);
        }
      }
    }
  }
};

template<GenerationMode GENERATION>
class ShenandoahFinalMarkingTask : public AbstractGangTask {
private:
  ShenandoahConcurrentMark* _cm;
  TaskTerminator*           _terminator;
  bool                      _dedup_string;

public:
  ShenandoahFinalMarkingTask(ShenandoahConcurrentMark* cm, TaskTerminator* terminator, bool dedup_string) :
    AbstractGangTask("Shenandoah Final Mark"), _cm(cm), _terminator(terminator), _dedup_string(dedup_string) {
  }

  void work(uint worker_id) {
    ShenandoahHeap* heap = ShenandoahHeap::heap();

    ShenandoahParallelWorkerSession worker_session(worker_id);
    ShenandoahReferenceProcessor* rp = heap->ref_processor();

    // First drain remaining SATB buffers.
    {
      ShenandoahObjToScanQueue* q = _cm->get_queue(worker_id);

      ShenandoahSATBBufferClosure<GENERATION> cl(q);
      SATBMarkQueueSet& satb_mq_set = ShenandoahBarrierSet::satb_mark_queue_set();
      while (satb_mq_set.apply_closure_to_completed_buffer(&cl)) {}
      assert(!heap->has_forwarded_objects(), "Not expected");

      bool do_nmethods = heap->unload_classes() && !ShenandoahConcurrentRoots::can_do_concurrent_class_unloading();
      ShenandoahMarkRefsClosure<GENERATION> mark_cl(q, rp);
      MarkingCodeBlobClosure blobsCl(&mark_cl, !CodeBlobToOopClosure::FixRelocations);
      ShenandoahSATBAndRemarkCodeRootsThreadsClosure<GENERATION> tc(&cl,
                                                                    ShenandoahStoreValEnqueueBarrier ? &mark_cl : NULL,
                                                                    do_nmethods ? &blobsCl : NULL);
      Threads::threads_do(&tc);
    }

    _cm->mark_loop(GENERATION, worker_id, _terminator, rp,
                   false, // not cancellable
                   _dedup_string);

    assert(_cm->task_queues()->is_empty(), "Should be empty");
  }
};

template<GenerationMode GENERATION>
class ShenandoahInitMarkRootsTask : public AbstractGangTask {
private:
  ShenandoahRootScanner              _root_scanner;
  ShenandoahObjToScanQueueSet* const _task_queues;
public:
  ShenandoahInitMarkRootsTask(uint n_workers, ShenandoahObjToScanQueueSet* task_queues) :
    AbstractGangTask("Shenandoah Init Mark Roots"),
    _root_scanner(n_workers, ShenandoahPhaseTimings::scan_roots),
    _task_queues(task_queues) {
    assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Must be at a safepoint");
  }

  void work(uint worker_id) {
    ShenandoahParallelWorkerSession worker_session(worker_id);
    assert(_task_queues->get_reserved() > worker_id, "Queue has not been reserved for worker id: %d", worker_id);

    ShenandoahObjToScanQueue* q = _task_queues->queue(worker_id);
    ShenandoahInitMarkRootsClosure<GENERATION> mark_cl(q);
    _root_scanner.roots_do(worker_id, &mark_cl);
  }
};

ShenandoahConcurrentMark::ShenandoahConcurrentMark() :
  ShenandoahMark() {}

void ShenandoahConcurrentMark::mark_stw_roots(ShenandoahGeneration* generation) {
  assert(Thread::current()->is_VM_thread(), "Can only do this in VMThread");
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Must be at a safepoint");
  assert(!ShenandoahHeap::heap()->has_forwarded_objects(), "Not expected");

  ShenandoahGCPhase phase(ShenandoahPhaseTimings::scan_roots);

  WorkGang* workers = ShenandoahHeap::heap()->workers();
  uint nworkers = workers->active_workers();

  assert(nworkers <= task_queues()->size(), "Just check");

  TASKQUEUE_STATS_ONLY(task_queues()->reset_taskqueue_stats());
  task_queues()->reserve(nworkers);

  switch (generation->generation_mode()) {
    case YOUNG: {
      ShenandoahInitMarkRootsTask<YOUNG> mark_roots(nworkers, task_queues());
      workers->run_task(&mark_roots);
      break;
    }
    case GLOBAL: {
      ShenandoahInitMarkRootsTask<GLOBAL> mark_roots(nworkers, task_queues());
      workers->run_task(&mark_roots);
      break;
    }
    default:
      ShouldNotReachHere();
  }
}

void ShenandoahConcurrentMark::update_roots(ShenandoahPhaseTimings::Phase root_phase) {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Must be at a safepoint");
  assert(root_phase == ShenandoahPhaseTimings::full_gc_update_roots ||
         root_phase == ShenandoahPhaseTimings::degen_gc_update_roots,
         "Only for these phases");

  ShenandoahGCPhase phase(root_phase);

  bool check_alive = root_phase == ShenandoahPhaseTimings::degen_gc_update_roots;

#if COMPILER2_OR_JVMCI
  DerivedPointerTable::clear();
#endif

  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  WorkGang* workers = heap->workers();
  uint nworkers = workers->active_workers();

  ShenandoahRootUpdater root_updater(nworkers, root_phase);
  ShenandoahUpdateRootsTask update_roots(&root_updater, check_alive);
  workers->run_task(&update_roots);

#if COMPILER2_OR_JVMCI
  DerivedPointerTable::update_pointers();
#endif
}

class ShenandoahUpdateThreadRootsTask : public AbstractGangTask {
private:
  ShenandoahThreadRoots           _thread_roots;
  ShenandoahPhaseTimings::Phase   _phase;
  ShenandoahGCWorkerPhase         _worker_phase;
public:
  ShenandoahUpdateThreadRootsTask(bool is_par, ShenandoahPhaseTimings::Phase phase) :
    AbstractGangTask("Shenandoah Update Thread Roots"),
    _thread_roots(phase, is_par),
    _phase(phase),
    _worker_phase(phase) {}

  void work(uint worker_id) {
    ShenandoahParallelWorkerSession worker_session(worker_id);
    ShenandoahUpdateRefsClosure cl;
    _thread_roots.oops_do(&cl, NULL, worker_id);
  }
};

void ShenandoahConcurrentMark::update_thread_roots(ShenandoahPhaseTimings::Phase root_phase) {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Must be at a safepoint");

  ShenandoahGCPhase phase(root_phase);

#if COMPILER2_OR_JVMCI
  DerivedPointerTable::clear();
#endif
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  WorkGang* workers = heap->workers();
  bool is_par = workers->active_workers() > 1;

  ShenandoahUpdateThreadRootsTask task(is_par, root_phase);
  workers->run_task(&task);

#if COMPILER2_OR_JVMCI
  DerivedPointerTable::update_pointers();
#endif
}

// Mark concurrent roots during concurrent phases
template<GenerationMode GENERATION>
class ShenandoahMarkConcurrentRootsTask : public AbstractGangTask {
private:
  ShenandoahConcurrentMark*           _scm;
  SuspendibleThreadSetJoiner          _sts_joiner;
  ShenandoahConcurrentRootScanner     _root_scanner;
  ShenandoahObjToScanQueueSet* const  _queue_set;
  ShenandoahReferenceProcessor* const _rp;

public:
  ShenandoahMarkConcurrentRootsTask(ShenandoahConcurrentMark* scm,
                                    ShenandoahObjToScanQueueSet* qs,
                                    ShenandoahReferenceProcessor* rp,
                                    ShenandoahPhaseTimings::Phase phase,
                                    uint nworkers);
  void work(uint worker_id);
};

template<GenerationMode GENERATION>
ShenandoahMarkConcurrentRootsTask<GENERATION>::ShenandoahMarkConcurrentRootsTask(ShenandoahConcurrentMark* scm,
                                                                                 ShenandoahObjToScanQueueSet* qs,
                                                                                 ShenandoahReferenceProcessor* rp,
                                                                                 ShenandoahPhaseTimings::Phase phase,
                                                                                 uint nworkers) :
  AbstractGangTask("Shenandoah Concurrent Mark Roots"),
  _root_scanner(nworkers, phase),
  _queue_set(qs),
  _rp(rp) {
  assert(!ShenandoahHeap::heap()->has_forwarded_objects(), "Not expected");
}

template<GenerationMode GENERATION>
void ShenandoahMarkConcurrentRootsTask<GENERATION>::work(uint worker_id) {
  ShenandoahConcurrentWorkerSession worker_session(worker_id);
  ShenandoahObjToScanQueue* q = _queue_set->queue(worker_id);
  ShenandoahMarkRefsClosure<GENERATION> cl(q, _rp);
  _root_scanner.roots_do(&cl, worker_id);
}

void ShenandoahConcurrentMark::mark_concurrent_roots(ShenandoahGeneration* generation) {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  assert(!heap->has_forwarded_objects(), "Not expected");

  WorkGang* workers = heap->workers();
  ShenandoahReferenceProcessor* rp = heap->ref_processor();
  task_queues()->reserve(workers->active_workers());
  switch (generation->generation_mode()) {
    case YOUNG: {
      ShenandoahMarkConcurrentRootsTask<YOUNG> task(this, task_queues(), rp, ShenandoahPhaseTimings::conc_mark_roots, workers->active_workers());
      workers->run_task(&task);
      break;
    }
    case GLOBAL: {
      ShenandoahMarkConcurrentRootsTask<YOUNG> task(this, task_queues(), rp, ShenandoahPhaseTimings::conc_mark_roots, workers->active_workers());
      workers->run_task(&task);
      break;
    }
    default:
      ShouldNotReachHere();
  }
}

void ShenandoahConcurrentMark::concurrent_mark(ShenandoahGeneration* generation) {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  WorkGang* workers = heap->workers();
  uint nworkers = workers->active_workers();
  task_queues()->reserve(nworkers);

  {
    switch (generation->generation_mode()) {
      case YOUNG: {
        TaskTerminator terminator(nworkers, task_queues());
        ShenandoahConcurrentMarkingTask<YOUNG> task(this, &terminator);
        workers->run_task(&task);
        break;
      }
      case GLOBAL: {
        TaskTerminator terminator(nworkers, task_queues());
        ShenandoahConcurrentMarkingTask<GLOBAL> task(this, &terminator);
        workers->run_task(&task);
        break;
      }
      default:
        ShouldNotReachHere();
    }
  }

  assert(task_queues()->is_empty() || heap->cancelled_gc(), "Should be empty when not cancelled");
}

void ShenandoahConcurrentMark::finish_mark(ShenandoahGeneration* generation) {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Must be at a safepoint");
  assert(Thread::current()->is_VM_thread(), "Must by VM Thread");
  finish_mark_work(generation);
  assert(task_queues()->is_empty(), "Should be empty");
  TASKQUEUE_STATS_ONLY(task_queues()->print_taskqueue_stats());
  TASKQUEUE_STATS_ONLY(task_queues()->reset_taskqueue_stats());
}

void ShenandoahConcurrentMark::finish_mark_work(ShenandoahGeneration* generation) {
  // Finally mark everything else we've got in our queues during the previous steps.
  // It does two different things for concurrent vs. mark-compact GC:
  // - For concurrent GC, it starts with empty task queues, drains the remaining
  //   SATB buffers, and then completes the marking closure.
  // - For mark-compact GC, it starts out with the task queues seeded by initial
  //   root scan, and completes the closure, thus marking through all live objects
  // The implementation is the same, so it's shared here.
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  ShenandoahGCPhase phase(ShenandoahPhaseTimings::finish_queues);
  uint nworkers = heap->workers()->active_workers();
  task_queues()->reserve(nworkers);

  StrongRootsScope scope(nworkers);
  TaskTerminator terminator(nworkers, task_queues());

  switch (generation->generation_mode()) {
    case YOUNG:{
      ShenandoahFinalMarkingTask<YOUNG> task(this, &terminator, ShenandoahStringDedup::is_enabled());
      heap->workers()->run_task(&task);
      break;
    }
    case GLOBAL:{
      ShenandoahFinalMarkingTask<GLOBAL> task(this, &terminator, ShenandoahStringDedup::is_enabled());
      heap->workers()->run_task(&task);
      break;
    }
    default:
      ShouldNotReachHere();
  }


  assert(task_queues()->is_empty(), "Should be empty");
}


void ShenandoahConcurrentMark::cancel() {
  clear();
  ShenandoahReferenceProcessor* rp = ShenandoahHeap::heap()->ref_processor();
  rp->abandon_partial_discovery();
}
