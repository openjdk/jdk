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
#include "gc/shared/strongRootsScope.hpp"

#include "gc/shenandoah/shenandoahBarrierSet.inline.hpp"
#include "gc/shenandoah/shenandoahClosures.inline.hpp"
#include "gc/shenandoah/shenandoahConcurrentMark.inline.hpp"
#include "gc/shenandoah/shenandoahMarkCompact.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahReferenceProcessor.hpp"
#include "gc/shenandoah/shenandoahRootProcessor.inline.hpp"
#include "gc/shenandoah/shenandoahOopClosures.inline.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shenandoah/shenandoahTaskqueue.inline.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "gc/shenandoah/shenandoahScanRemembered.inline.hpp"

#include "memory/iterator.inline.hpp"
#include "memory/metaspace.hpp"
#include "memory/resourceArea.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/handles.inline.hpp"

template<GenerationMode GENERATION>
class ShenandoahInitMarkRootsClosure : public OopClosure {
private:
  ShenandoahObjToScanQueue* _queue;
  ShenandoahHeap* _heap;
  ShenandoahMarkingContext* const _mark_context;

  template <class T>
  inline void do_oop_work(T* p) {
    ShenandoahConcurrentMark::mark_through_ref<T, GENERATION, NONE, NO_DEDUP>(p, _heap, _queue, _mark_context, false);
  }

public:
  ShenandoahInitMarkRootsClosure(ShenandoahObjToScanQueue* q) :
    _queue(q),
    _heap(ShenandoahHeap::heap()),
    _mark_context(_heap->marking_context()) {};

  void do_oop(narrowOop* p) { do_oop_work(p); }
  void do_oop(oop* p)       { do_oop_work(p); }
};

ShenandoahMarkRefsSuperClosure::ShenandoahMarkRefsSuperClosure(ShenandoahObjToScanQueue* q, ShenandoahReferenceProcessor* rp) :
  MetadataVisitingOopIterateClosure(rp),
  _queue(q),
  _heap(ShenandoahHeap::heap()),
  _mark_context(_heap->marking_context()),
  _weak(false)
{ }

class ShenandoahInitMarkRootsTask : public AbstractGangTask {
private:
  ShenandoahConcurrentMark* const _scm;
  ShenandoahRootScanner* const _rp;
  uint const _workers;
public:

  ShenandoahInitMarkRootsTask(ShenandoahConcurrentMark* scm, ShenandoahRootScanner* rp, uint worker_count) :
    AbstractGangTask("Shenandoah Init Mark Roots"),
    _scm(scm),
    _rp(rp),
    _workers(worker_count) {
  }

  void work(uint worker_id) {
    assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Must be at a safepoint");
    ShenandoahParallelWorkerSession worker_session(worker_id);

    ShenandoahHeap* heap = ShenandoahHeap::heap();
    ShenandoahObjToScanQueueSet* queues = _scm->task_queues();
    assert(queues->get_reserved() > worker_id, "Queue has not been reserved for worker id: %d", worker_id);

    ShenandoahObjToScanQueue* q = queues->queue(worker_id);

    switch (_scm->generation_mode()) {
      case YOUNG: {
        ShenandoahInitMarkRootsClosure<YOUNG> mark_cl(q);

        // Do the remembered set scanning before the root scanning as the current implementation of remembered set scanning
        // does not do workload balancing.  If certain worker threads end up with disproportionate amounts of remembered set
        // scanning effort, the subsequent root scanning effort will balance workload to even effort between threads.
        uint32_t r;
        RememberedScanner *rs = heap->card_scan();
        ShenandoahReferenceProcessor* rp = heap->ref_processor();
        unsigned int total_regions = heap->num_regions();

        for (r = worker_id % _workers; r < total_regions; r += _workers) {
          ShenandoahHeapRegion *region = heap->get_region(r);
          if (region->affiliation() == OLD_GENERATION) {
            uint32_t start_cluster_no = rs->cluster_for_addr(region->bottom());

            // region->end() represents the end of memory spanned by this region, but not all of this
            //   memory is eligible to be scanned because some of this memory has not yet been allocated.
            //
            // region->top() represents the end of allocated memory within this region.  Any addresses
            //   beyond region->top() should not be scanned as that memory does not hold valid objects.
            HeapWord *end_of_range = region->top();
            uint32_t stop_cluster_no  = rs->cluster_for_addr(end_of_range);
            rs->process_clusters<ShenandoahInitMarkRootsClosure<YOUNG>>(worker_id, rp, _scm, start_cluster_no,
                                                                                     stop_cluster_no + 1 - start_cluster_no,
                                                                                     end_of_range, &mark_cl);
          }
        }
        do_work(heap, &mark_cl, worker_id);
        break;
      }
      case GLOBAL: {
        ShenandoahInitMarkRootsClosure<GLOBAL> mark_cl(q);
        do_work(heap, &mark_cl, worker_id);
        break;
      }
      default: {
        ShouldNotReachHere();
        break;
      }
    }
  }

private:
  void do_work(ShenandoahHeap* heap, OopClosure* oops, uint worker_id) {
    _rp->roots_do(worker_id, oops);
  }
};

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
  ShenandoahConcurrentMark* _cm;
  TaskTerminator* _terminator;

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
    _cm->mark_loop(worker_id, _terminator, rp,
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

// Process concurrent roots at safepoints
template <typename T>
class ShenandoahProcessConcurrentRootsTask : public AbstractGangTask {
private:
  ShenandoahConcurrentRootScanner<false /* concurrent */> _rs;
  ShenandoahConcurrentMark* const _cm;
  ShenandoahReferenceProcessor*   _rp;
public:

  ShenandoahProcessConcurrentRootsTask(ShenandoahConcurrentMark* cm,
                                       ShenandoahPhaseTimings::Phase phase,
                                       uint nworkers);
  void work(uint worker_id);
};

template <typename T>
ShenandoahProcessConcurrentRootsTask<T>::ShenandoahProcessConcurrentRootsTask(ShenandoahConcurrentMark* cm,
                                                                              ShenandoahPhaseTimings::Phase phase,
                                                                              uint nworkers) :
  AbstractGangTask("Shenandoah Process Concurrent Roots"),
  _rs(nworkers, phase),
  _cm(cm),
  _rp(ShenandoahHeap::heap()->ref_processor()) {
}

template <typename T>
void ShenandoahProcessConcurrentRootsTask<T>::work(uint worker_id) {
  ShenandoahParallelWorkerSession worker_session(worker_id);
  ShenandoahObjToScanQueue* q = _cm->task_queues()->queue(worker_id);
  T cl(q, _rp);
  _rs.oops_do(&cl, worker_id);
}

class ShenandoahClaimThreadClosure : public ThreadClosure {
private:
  const uintx _claim_token;
public:
  ShenandoahClaimThreadClosure() :
   _claim_token(Threads::thread_claim_token()) {}

  virtual void do_thread(Thread* thread) {
    thread->claim_threads_do(false /*is_par*/, _claim_token);
  }
};

template <GenerationMode GENERATION>
class ShenandoahFinalMarkingTask : public AbstractGangTask {
private:
  ShenandoahConcurrentMark* _cm;
  TaskTerminator*           _terminator;
  bool                      _dedup_string;

public:
  ShenandoahFinalMarkingTask(ShenandoahConcurrentMark* cm, TaskTerminator* terminator, bool dedup_string) :
    AbstractGangTask("Shenandoah Final Mark"), _cm(cm), _terminator(terminator), _dedup_string(dedup_string) {

    // Full GC does not need to remark threads and drain SATB buffers, but we need to claim the
    // threads - it requires a StrongRootsScope around the task.
    if (ShenandoahHeap::heap()->is_full_gc_in_progress()) {
      ShenandoahClaimThreadClosure tc;
      Threads::threads_do(&tc);
    }
  }

  void work(uint worker_id) {
    ShenandoahHeap* heap = ShenandoahHeap::heap();

    ShenandoahParallelWorkerSession worker_session(worker_id);
    ShenandoahReferenceProcessor* rp = heap->ref_processor();

    if (!heap->is_full_gc_in_progress()) {
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

    _cm->mark_loop(worker_id, _terminator, rp,
                   false, // not cancellable
                   _dedup_string);

    assert(_cm->task_queues()->is_empty(), "Should be empty");
  }
};

void ShenandoahConcurrentMark::mark_roots(ShenandoahPhaseTimings::Phase root_phase) {
  assert(Thread::current()->is_VM_thread(), "can only do this in VMThread");
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Must be at a safepoint");

  ShenandoahHeap* heap = ShenandoahHeap::heap();
  ShenandoahGCPhase phase(root_phase);

  ShenandoahReferenceProcessor* ref_processor = heap->ref_processor();
  ref_processor->reset_thread_locals();
  ref_processor->set_soft_reference_policy(_heap->soft_ref_policy()->should_clear_all_soft_refs());

  WorkGang* workers = heap->workers();
  uint nworkers = workers->active_workers();

  assert(nworkers <= task_queues()->size(), "Just check");

  ShenandoahRootScanner root_proc(nworkers, root_phase);
  TASKQUEUE_STATS_ONLY(task_queues()->reset_taskqueue_stats());
  task_queues()->reserve(nworkers);

  ShenandoahInitMarkRootsTask mark_roots(this, &root_proc, nworkers);
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

  uint nworkers = _heap->workers()->active_workers();

  ShenandoahRootUpdater root_updater(nworkers, root_phase);
  ShenandoahUpdateRootsTask update_roots(&root_updater, check_alive);
  _heap->workers()->run_task(&update_roots);

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

  WorkGang* workers = _heap->workers();
  bool is_par = workers->active_workers() > 1;

  ShenandoahUpdateThreadRootsTask task(is_par, root_phase);
  workers->run_task(&task);

#if COMPILER2_OR_JVMCI
  DerivedPointerTable::update_pointers();
#endif
}

void ShenandoahConcurrentMark::initialize(uint workers) {
  _heap = ShenandoahHeap::heap();

  uint num_queues = MAX2(workers, 1U);

  _task_queues = new ShenandoahObjToScanQueueSet((int) num_queues);

  for (uint i = 0; i < num_queues; ++i) {
    ShenandoahObjToScanQueue* task_queue = new ShenandoahObjToScanQueue();
    task_queue->initialize();
    _task_queues->register_queue(i, task_queue);
  }
}

// Mark concurrent roots during concurrent phases
class ShenandoahMarkConcurrentRootsTask : public AbstractGangTask {
private:
  ShenandoahConcurrentMark*           _scm;
  SuspendibleThreadSetJoiner          _sts_joiner;
  ShenandoahConcurrentRootScanner<true /* concurrent */> _rs;
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

ShenandoahMarkConcurrentRootsTask::ShenandoahMarkConcurrentRootsTask(ShenandoahConcurrentMark* scm,
                                                                     ShenandoahObjToScanQueueSet* qs,
                                                                     ShenandoahReferenceProcessor* rp,
                                                                     ShenandoahPhaseTimings::Phase phase,
                                                                     uint nworkers) :
  AbstractGangTask("Shenandoah Concurrent Mark Roots"),
  _scm(scm),
  _rs(nworkers, phase),
  _queue_set(qs),
  _rp(rp) {
  assert(!ShenandoahHeap::heap()->has_forwarded_objects(), "Not expected");
}

void ShenandoahMarkConcurrentRootsTask::work(uint worker_id) {
  ShenandoahConcurrentWorkerSession worker_session(worker_id);
  ShenandoahObjToScanQueue* q = _queue_set->queue(worker_id);
  switch (_scm->generation_mode()) {
    case YOUNG: {
      ShenandoahMarkRefsClosure<YOUNG> cl(q, _rp);
      _rs.oops_do(&cl, worker_id);
      break;
    }
    case GLOBAL: {
      ShenandoahMarkRefsClosure<GLOBAL> cl(q, _rp);
      _rs.oops_do(&cl, worker_id);
      break;
    }
    default: {
      ShouldNotReachHere();
      break;
    }
  }
}

void ShenandoahConcurrentMark::mark_from_roots() {
  WorkGang* workers = _heap->workers();
  uint nworkers = workers->active_workers();

  ShenandoahReferenceProcessor* rp = _heap->ref_processor();

  task_queues()->reserve(nworkers);

  {
    ShenandoahGCPhase phase(ShenandoahPhaseTimings::conc_mark_roots);
    // Use separate task to mark concurrent roots, since it may hold ClassLoaderData_lock and CodeCache_lock
    ShenandoahMarkConcurrentRootsTask task(this, task_queues(), rp, ShenandoahPhaseTimings::conc_mark_roots, nworkers);
    workers->run_task(&task);
  }

  {
    TaskTerminator terminator(nworkers, task_queues());
    ShenandoahConcurrentMarkingTask task(this, &terminator);
    workers->run_task(&task);
  }

  assert(task_queues()->is_empty() || _heap->cancelled_gc(), "Should be empty when not cancelled");
}

void ShenandoahConcurrentMark::finish_mark_from_roots(bool full_gc) {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Must be at a safepoint");

  uint nworkers = _heap->workers()->active_workers();

  {
    // Full GC does not execute concurrent cycle. Degenerated cycle may bypass concurrent cycle.
    // In those cases, concurrent roots might not be scanned, scan them here. Ideally, this
    // should piggyback to ShenandoahFinalMarkingTask, but it makes time tracking very hard.
    // Given full GC and degenerated GC are rare, use a separate task.
    if (_heap->is_degenerated_gc_in_progress() || _heap->is_full_gc_in_progress()) {
      ShenandoahPhaseTimings::Phase phase = _heap->is_full_gc_in_progress() ?
                                            ShenandoahPhaseTimings::full_gc_scan_conc_roots :
                                            ShenandoahPhaseTimings::degen_gc_scan_conc_roots;
      ShenandoahGCPhase gc_phase(phase);
      switch (generation_mode()) {
         case YOUNG: {
           ShenandoahProcessConcurrentRootsTask<ShenandoahMarkRefsClosure<YOUNG>> task(this, phase, nworkers);
           _heap->workers()->run_task(&task);
           break;
         }
         case GLOBAL: {
           ShenandoahProcessConcurrentRootsTask<ShenandoahMarkRefsClosure<GLOBAL>> task(this, phase, nworkers);
           _heap->workers()->run_task(&task);
           break;
         }
         default: {
           ShouldNotReachHere();
           break;
         }
      }
    }

    // Finally mark everything else we've got in our queues during the previous steps.
    // It does two different things for concurrent vs. mark-compact GC:
    // - For concurrent GC, it starts with empty task queues, drains the remaining
    //   SATB buffers, and then completes the marking closure.
    // - For mark-compact GC, it starts out with the task queues seeded by initial
    //   root scan, and completes the closure, thus marking through all live objects
    // The implementation is the same, so it's shared here.
    {
      ShenandoahGCPhase phase(full_gc ?
                              ShenandoahPhaseTimings::full_gc_mark_finish_queues :
                              ShenandoahPhaseTimings::finish_queues);
      task_queues()->reserve(nworkers);

      StrongRootsScope scope(nworkers);
      TaskTerminator terminator(nworkers, task_queues());
      switch (generation_mode()) {
        case YOUNG: {
          ShenandoahFinalMarkingTask<YOUNG> task(this, &terminator, ShenandoahStringDedup::is_enabled());
          _heap->workers()->run_task(&task);
          break;
        }
        case GLOBAL: {
          ShenandoahFinalMarkingTask<GLOBAL> task(this, &terminator, ShenandoahStringDedup::is_enabled());
          _heap->workers()->run_task(&task);
          break;
        }
        default: {
          ShouldNotReachHere();
          break;
        }
      }
    }

    assert(task_queues()->is_empty(), "Should be empty");
  }

  assert(task_queues()->is_empty(), "Should be empty");
  TASKQUEUE_STATS_ONLY(task_queues()->print_taskqueue_stats());
  TASKQUEUE_STATS_ONLY(task_queues()->reset_taskqueue_stats());
}

void ShenandoahConcurrentMark::cancel() {
  // Clean up marking stacks.
  ShenandoahObjToScanQueueSet* queues = task_queues();
  queues->clear();

  // Cancel SATB buffers.
  ShenandoahBarrierSet::satb_mark_queue_set().abandon_partial_marking();
}

ShenandoahObjToScanQueue* ShenandoahConcurrentMark::get_queue(uint worker_id) {
  assert(task_queues()->get_reserved() > worker_id, "No reserved queue for worker id: %d", worker_id);
  return _task_queues->queue(worker_id);
}

template <GenerationMode GENERATION, bool CANCELLABLE>
void ShenandoahConcurrentMark::mark_loop_prework(uint w, TaskTerminator *t, ShenandoahReferenceProcessor* rp,
                                                 bool strdedup) {
  ShenandoahObjToScanQueue* q = get_queue(w);

  ShenandoahLiveData* ld = _heap->get_liveness_cache(w);

  // TODO: We can clean up this if we figure out how to do templated oop closures that
  // play nice with specialized_oop_iterators.
  if (_heap->unload_classes()) {
    if (_heap->has_forwarded_objects()) {
      if (strdedup) {
        ShenandoahMarkUpdateRefsMetadataDedupClosure<GENERATION> cl(q, rp);
        mark_loop_work<ShenandoahMarkUpdateRefsMetadataDedupClosure<GENERATION>, GENERATION, CANCELLABLE>(&cl, ld, w, t);
      } else {
        ShenandoahMarkUpdateRefsMetadataClosure<GENERATION> cl(q, rp);
        mark_loop_work<ShenandoahMarkUpdateRefsMetadataClosure<GENERATION>, GENERATION, CANCELLABLE>(&cl, ld, w, t);
      }
    } else {
      if (strdedup) {
        ShenandoahMarkRefsMetadataDedupClosure<GENERATION> cl(q, rp);
        mark_loop_work<ShenandoahMarkRefsMetadataDedupClosure<GENERATION>, GENERATION, CANCELLABLE>(&cl, ld, w, t);
      } else {
        ShenandoahMarkRefsMetadataClosure<GENERATION> cl(q, rp);
        mark_loop_work<ShenandoahMarkRefsMetadataClosure<GENERATION>, GENERATION, CANCELLABLE>(&cl, ld, w, t);
      }
    }
  } else {
    if (_heap->has_forwarded_objects()) {
      if (strdedup) {
        ShenandoahMarkUpdateRefsDedupClosure<GENERATION> cl(q, rp);
        mark_loop_work<ShenandoahMarkUpdateRefsDedupClosure<GENERATION>, GENERATION, CANCELLABLE>(&cl, ld, w, t);
      } else {
        ShenandoahMarkUpdateRefsClosure<GENERATION> cl(q, rp);
        mark_loop_work<ShenandoahMarkUpdateRefsClosure<GENERATION>, GENERATION, CANCELLABLE>(&cl, ld, w, t);
      }
    } else {
      if (strdedup) {
        ShenandoahMarkRefsDedupClosure<GENERATION> cl(q, rp);
        mark_loop_work<ShenandoahMarkRefsDedupClosure<GENERATION>, GENERATION, CANCELLABLE>(&cl, ld, w, t);
      } else {
        ShenandoahMarkRefsClosure<GENERATION> cl(q, rp);
        mark_loop_work<ShenandoahMarkRefsClosure<GENERATION>, GENERATION, CANCELLABLE>(&cl, ld, w, t);
      }
    }
  }

  _heap->flush_liveness_cache(w);
}

template <class T, GenerationMode GENERATION, bool CANCELLABLE>
void ShenandoahConcurrentMark::mark_loop_work(T* cl, ShenandoahLiveData* live_data, uint worker_id, TaskTerminator *terminator) {
  uintx stride = ShenandoahMarkLoopStride;

  ShenandoahHeap* heap = ShenandoahHeap::heap();
  ShenandoahObjToScanQueueSet* queues = task_queues();
  ShenandoahObjToScanQueue* q;
  ShenandoahMarkTask t;

  _heap->ref_processor()->set_mark_closure(worker_id, cl);

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

  ShenandoahSATBBufferClosure<GENERATION> drain_satb(q);
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
