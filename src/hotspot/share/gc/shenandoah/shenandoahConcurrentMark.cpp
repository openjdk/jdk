/*
 * Copyright (c) 2013, 2020, Red Hat, Inc. All rights reserved.
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
#include "gc/shared/referenceProcessor.hpp"
#include "gc/shared/referenceProcessorPhaseTimes.hpp"
#include "gc/shared/strongRootsScope.hpp"

#include "gc/shenandoah/shenandoahBarrierSet.inline.hpp"
#include "gc/shenandoah/shenandoahClosures.inline.hpp"
#include "gc/shenandoah/shenandoahConcurrentMark.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahMark.inline.hpp"
#include "gc/shenandoah/shenandoahRootProcessor.inline.hpp"
#include "gc/shenandoah/shenandoahOopClosures.inline.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shenandoah/shenandoahStringDedup.hpp"
#include "gc/shenandoah/shenandoahTaskqueue.inline.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"

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
    ReferenceProcessor* rp;
    if (heap->process_references()) {
      rp = heap->ref_processor();
      shenandoah_assert_rp_isalive_installed();
    } else {
      rp = NULL;
    }

    _cm->mark_loop(worker_id, _terminator, rp,
                   true, // cancellable
                   ShenandoahStringDedup::is_enabled()); // perform string dedup
  }
};

class ShenandoahSATBAndRemarkCodeRootsThreadsClosure : public ThreadClosure {
private:
  ShenandoahSATBBufferClosure* _satb_cl;
  OopClosure*            const _cl;
  MarkingCodeBlobClosure*      _code_cl;
  uintx _claim_token;

public:
  ShenandoahSATBAndRemarkCodeRootsThreadsClosure(ShenandoahSATBBufferClosure* satb_cl, OopClosure* cl, MarkingCodeBlobClosure* code_cl) :
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
    ReferenceProcessor* rp;
    if (heap->process_references()) {
      rp = heap->ref_processor();
      shenandoah_assert_rp_isalive_installed();
    } else {
      rp = NULL;
    }

    // First drain remaining SATB buffers.
    // Notice that this is not strictly necessary for mark-compact. But since
    // it requires a StrongRootsScope around the task, we need to claim the
    // threads, and performance-wise it doesn't really matter. Adds about 1ms to
    // full-gc.
    {
      ShenandoahObjToScanQueue* q = _cm->get_queue(worker_id);

      ShenandoahSATBBufferClosure cl(q);
      SATBMarkQueueSet& satb_mq_set = ShenandoahBarrierSet::satb_mark_queue_set();
      while (satb_mq_set.apply_closure_to_completed_buffer(&cl));
      bool do_nmethods = heap->unload_classes() && !ShenandoahConcurrentRoots::can_do_concurrent_class_unloading();
      if (heap->has_forwarded_objects()) {
        ShenandoahMarkResolveRefsClosure resolve_mark_cl(q, rp);
        MarkingCodeBlobClosure blobsCl(&resolve_mark_cl, !CodeBlobToOopClosure::FixRelocations);
        ShenandoahSATBAndRemarkCodeRootsThreadsClosure tc(&cl,
                                                          ShenandoahStoreValEnqueueBarrier ? &resolve_mark_cl : NULL,
                                                          do_nmethods ? &blobsCl : NULL);
        Threads::threads_do(&tc);
      } else {
        ShenandoahMarkRefsClosure mark_cl(q, rp);
        MarkingCodeBlobClosure blobsCl(&mark_cl, !CodeBlobToOopClosure::FixRelocations);
        ShenandoahSATBAndRemarkCodeRootsThreadsClosure tc(&cl,
                                                          ShenandoahStoreValEnqueueBarrier ? &mark_cl : NULL,
                                                          do_nmethods ? &blobsCl : NULL);
        Threads::threads_do(&tc);
      }
    }

    _cm->mark_loop(worker_id, _terminator, rp,
                   false, // not cancellable
                   _dedup_string);

    assert(_cm->task_queues()->is_empty(), "Should be empty");
  }
};

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
    ShenandoahInitMarkRootsClosure mark_cl(q);
    _root_scanner.roots_do(worker_id, &mark_cl);
  }
};

ShenandoahConcurrentMark::ShenandoahConcurrentMark() :
  ShenandoahMark() {}

void ShenandoahConcurrentMark::mark_stw_roots() {
  assert(Thread::current()->is_VM_thread(), "Can only do this in VMThread");
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Must be at a safepoint");
  assert(!_heap->has_forwarded_objects(), "Not expected");

  ShenandoahGCPhase phase(ShenandoahPhaseTimings::scan_roots);

  WorkGang* workers = ShenandoahHeap::heap()->workers();
  uint nworkers = workers->active_workers();

  assert(nworkers <= task_queues()->size(), "Just check");

  TASKQUEUE_STATS_ONLY(task_queues()->reset_taskqueue_stats());
  task_queues()->reserve(nworkers);

  ShenandoahInitMarkRootsTask mark_roots(nworkers, task_queues());
  workers->run_task(&mark_roots);
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
class ShenandoahMarkConcurrentRootsTask : public AbstractGangTask {
private:
  SuspendibleThreadSetJoiner         _sts_joiner;
  ShenandoahConcurrentRootScanner    _root_scanner;
  ShenandoahObjToScanQueueSet* const _queue_set;

public:
  ShenandoahMarkConcurrentRootsTask(ShenandoahObjToScanQueueSet* qs,
                                    uint nworkers);
  void work(uint worker_id);
};

ShenandoahMarkConcurrentRootsTask::ShenandoahMarkConcurrentRootsTask(ShenandoahObjToScanQueueSet* qs,
                                                                     uint nworkers) :
  AbstractGangTask("Shenandoah Concurrent Mark Roots"),
  _root_scanner(nworkers, ShenandoahPhaseTimings::conc_mark_roots),
  _queue_set(qs) {
  assert(!ShenandoahHeap::heap()->has_forwarded_objects(), "Not expected");
}

void ShenandoahMarkConcurrentRootsTask::work(uint worker_id) {
  ShenandoahConcurrentWorkerSession worker_session(worker_id);
  ShenandoahObjToScanQueue* q = _queue_set->queue(worker_id);
  ShenandoahInitMarkRootsClosure cl(q);
  _root_scanner.roots_do(&cl, worker_id);
}

void ShenandoahConcurrentMark::mark_concurrent_roots() {
  assert(!_heap->has_forwarded_objects(), "Not expected");

  WorkGang* workers = heap()->workers();
  ShenandoahMarkConcurrentRootsTask task(task_queues(), workers->active_workers());
  workers->run_task(&task);
}

void ShenandoahConcurrentMark::concurrent_mark() {
  WorkGang* workers = heap()->workers();
  uint nworkers = workers->active_workers();
  task_queues()->reserve(nworkers);
  TaskTerminator terminator(nworkers, task_queues());

  ReferenceProcessor* rp = NULL;
  if (heap()->process_references()) {
    rp = heap()->ref_processor();
    rp->set_active_mt_degree(nworkers);

    // enable ("weak") refs discovery
    rp->enable_discovery(true /*verify_no_refs*/);
    rp->setup_policy(_heap->soft_ref_policy()->should_clear_all_soft_refs());

    shenandoah_assert_rp_isalive_not_installed();
    ShenandoahIsAliveSelector is_alive;
    ReferenceProcessorIsAliveMutator fix_isalive(rp, is_alive.is_alive_closure());
    ShenandoahConcurrentMarkingTask task(this, &terminator);
    workers->run_task(&task);
  } else {
    ShenandoahConcurrentMarkingTask task(this, &terminator);
    workers->run_task(&task);
  }
  assert(task_queues()->is_empty() || heap()->cancelled_gc(), "Should be empty when not cancelled");
}

void ShenandoahConcurrentMark::finish_mark() {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Must be at a safepoint");
  assert(Thread::current()->is_VM_thread(), "Must by VM Thread");
  if (heap()->process_references()) {
    shenandoah_assert_rp_isalive_not_installed();
    ShenandoahIsAliveSelector is_alive;
    ReferenceProcessorIsAliveMutator fix_isalive(_heap->ref_processor(), is_alive.is_alive_closure());
    finish_mark_work();
    // When we're done marking everything, we process weak references.
    process_weak_refs(false /*full_gc*/);
  } else {
    finish_mark_work();
  }

  assert(task_queues()->is_empty(), "Should be empty");
  TASKQUEUE_STATS_ONLY(task_queues()->print_taskqueue_stats());
  TASKQUEUE_STATS_ONLY(task_queues()->reset_taskqueue_stats());
}

void ShenandoahConcurrentMark::finish_mark_work() {
  // Finally mark everything else we've got in our queues during the previous steps.
  // It does two different things for concurrent vs. mark-compact GC:
  // - For concurrent GC, it starts with empty task queues, drains the remaining
  //   SATB buffers, and then completes the marking closure.
  // - For mark-compact GC, it starts out with the task queues seeded by initial
  //   root scan, and completes the closure, thus marking through all live objects
  // The implementation is the same, so it's shared here.
  ShenandoahGCPhase phase(ShenandoahPhaseTimings::finish_queues);
  uint nworkers = _heap->workers()->active_workers();
  task_queues()->reserve(nworkers);

  StrongRootsScope scope(nworkers);
  TaskTerminator terminator(nworkers, task_queues());
  ShenandoahFinalMarkingTask task(this, &terminator, ShenandoahStringDedup::is_enabled());
  heap()->workers()->run_task(&task);

  assert(task_queues()->is_empty(), "Should be empty");
}

class ShenandoahCancelledGCYieldClosure : public YieldClosure {
private:
  ShenandoahHeap* const _heap;
public:
  ShenandoahCancelledGCYieldClosure() : _heap(ShenandoahHeap::heap()) {};
  virtual bool should_return() { return _heap->cancelled_gc(); }
};

class ShenandoahPrecleanCompleteGCClosure : public VoidClosure {
private:
  ShenandoahConcurrentMark* const _mark;
public:
  ShenandoahPrecleanCompleteGCClosure(ShenandoahConcurrentMark* mark) :
    _mark(mark) { }

  void do_void() {
    ShenandoahHeap* const sh = ShenandoahHeap::heap();
    assert(sh->process_references(), "why else would we be here?");
    TaskTerminator terminator(1, _mark->task_queues());

    ReferenceProcessor* rp = sh->ref_processor();
    shenandoah_assert_rp_isalive_installed();

    _mark->mark_loop(0, &terminator, rp,
                   false, // not cancellable
                   false); // do not do strdedup
  }
};

class ShenandoahPrecleanTask : public AbstractGangTask {
private:
  ShenandoahConcurrentMark* const _mark;
  ReferenceProcessor* const       _rp;

public:
  ShenandoahPrecleanTask(ShenandoahConcurrentMark* mark, ReferenceProcessor* rp) :
          AbstractGangTask("Shenandoah Precleaning"),
          _mark(mark),
          _rp(rp) {}

  void work(uint worker_id) {
    assert(worker_id == 0, "The code below is single-threaded, only one worker is expected");
    ShenandoahParallelWorkerSession worker_session(worker_id);

    assert(!ShenandoahHeap::heap()->has_forwarded_objects(), "No forwarded objects expected here");

    ShenandoahObjToScanQueue* q = _mark->get_queue(worker_id);

    ShenandoahCancelledGCYieldClosure yield;
    ShenandoahPrecleanCompleteGCClosure complete_gc(_mark);

    ShenandoahIsAliveClosure is_alive;
    ShenandoahCMKeepAliveClosure keep_alive(q);
    ResourceMark rm;
    _rp->preclean_discovered_references(&is_alive, &keep_alive,
                                        &complete_gc, &yield,
                                        NULL);
  }
};

void ShenandoahConcurrentMark::preclean_weak_refs() {
  if (!_heap->process_references()) {
    return;
  }
  // Pre-cleaning weak references before diving into STW makes sense at the
  // end of concurrent mark. This will filter out the references which referents
  // are alive. Note that ReferenceProcessor already filters out these on reference
  // discovery, and the bulk of work is done here. This phase processes leftovers
  // that missed the initial filtering, i.e. when referent was marked alive after
  // reference was discovered by RP.

  // Shortcut if no references were discovered to avoid winding up threads.
  ReferenceProcessor* rp = _heap->ref_processor();
  if (!rp->has_discovered_references()) {
    return;
  }

  assert(task_queues()->is_empty(), "Should be empty");

  ReferenceProcessorMTDiscoveryMutator fix_mt_discovery(rp, false);

  shenandoah_assert_rp_isalive_not_installed();
  ShenandoahIsAliveSelector is_alive;
  ReferenceProcessorIsAliveMutator fix_isalive(rp, is_alive.is_alive_closure());

  // Execute precleaning in the worker thread: it will give us GCLABs, String dedup
  // queues and other goodies. When upstream ReferenceProcessor starts supporting
  // parallel precleans, we can extend this to more threads.
  WorkGang* workers = _heap->workers();
  uint nworkers = workers->active_workers();
  assert(nworkers == 1, "This code uses only a single worker");
  task_queues()->reserve(nworkers);

  ShenandoahPrecleanTask task(this, rp);
  workers->run_task(&task);

  assert(task_queues()->is_empty(), "Should be empty");
}

void ShenandoahConcurrentMark::cancel() {
  clear();
  ShenandoahHeap* const heap = ShenandoahHeap::heap();

  if (heap->process_references()) {
    // Abandon reference processing right away: pre-cleaning must have failed.
    ReferenceProcessor *rp = heap->ref_processor();
    rp->disable_discovery();
    rp->abandon_partial_discovery();
    rp->verify_no_references_recorded();
  }
}
