/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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


#include "classfile/classLoaderDataGraph.inline.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "compiler/oopMap.hpp"
#include "gc/g1/g1Allocator.hpp"
#include "gc/g1/g1CardSetMemory.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1CollectionSetCandidates.inline.hpp"
#include "gc/g1/g1CollectorState.hpp"
#include "gc/g1/g1ConcurrentMark.hpp"
#include "gc/g1/g1EvacFailureRegions.inline.hpp"
#include "gc/g1/g1EvacInfo.hpp"
#include "gc/g1/g1GCPhaseTimes.hpp"
#include "gc/g1/g1HeapRegionPrinter.hpp"
#include "gc/g1/g1MonitoringSupport.hpp"
#include "gc/g1/g1ParScanThreadState.inline.hpp"
#include "gc/g1/g1Policy.hpp"
#include "gc/g1/g1RedirtyCardsQueue.hpp"
#include "gc/g1/g1RegionPinCache.inline.hpp"
#include "gc/g1/g1RemSet.hpp"
#include "gc/g1/g1RootProcessor.hpp"
#include "gc/g1/g1Trace.hpp"
#include "gc/g1/g1YoungCollector.hpp"
#include "gc/g1/g1YoungGCAllocationFailureInjector.hpp"
#include "gc/g1/g1YoungGCPostEvacuateTasks.hpp"
#include "gc/g1/g1YoungGCPreEvacuateTasks.hpp"
#include "gc/shared/concurrentGCBreakpoints.hpp"
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/gcTimer.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/referenceProcessor.hpp"
#include "gc/shared/weakProcessor.inline.hpp"
#include "gc/shared/workerPolicy.hpp"
#include "gc/shared/workerThread.hpp"
#include "jfr/jfrEvents.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/threads.hpp"
#include "utilities/ticks.hpp"

// GCTraceTime wrapper that constructs the message according to GC pause type and
// GC cause.
// The code relies on the fact that GCTraceTimeWrapper stores the string passed
// initially as a reference only, so that we can modify it as needed.
class G1YoungGCTraceTime {
  G1YoungCollector* _collector;

  G1GCPauseType _pause_type;
  GCCause::Cause _pause_cause;

  static const uint MaxYoungGCNameLength = 128;
  char _young_gc_name_data[MaxYoungGCNameLength];

  GCTraceTime(Info, gc) _tt;

  const char* update_young_gc_name() {
    char evacuation_failed_string[48];
    evacuation_failed_string[0] = '\0';

    if (_collector->evacuation_failed()) {
      snprintf(evacuation_failed_string,
               ARRAY_SIZE(evacuation_failed_string),
               " (Evacuation Failure: %s%s%s)",
               _collector->evacuation_alloc_failed() ? "Allocation" : "",
               _collector->evacuation_alloc_failed() && _collector->evacuation_pinned() ? " / " : "",
               _collector->evacuation_pinned() ? "Pinned" : "");
    }
    snprintf(_young_gc_name_data,
             MaxYoungGCNameLength,
             "Pause Young (%s) (%s)%s",
             G1GCPauseTypeHelper::to_string(_pause_type),
             GCCause::to_string(_pause_cause),
             evacuation_failed_string);
    return _young_gc_name_data;
  }

public:
  G1YoungGCTraceTime(G1YoungCollector* collector, GCCause::Cause cause) :
    _collector(collector),
    // Take snapshot of current pause type at start as it may be modified during gc.
    // The strings for all Concurrent Start pauses are the same, so the parameter
    // does not matter here.
    _pause_type(_collector->collector_state()->young_gc_pause_type(false /* concurrent_operation_is_full_mark */)),
    _pause_cause(cause),
    // Fake a "no cause" and manually add the correct string in update_young_gc_name()
    // to make the string look more natural.
    _tt(update_young_gc_name(), nullptr, GCCause::_no_gc, true) {
  }

  ~G1YoungGCTraceTime() {
    update_young_gc_name();
  }
};

class G1YoungGCNotifyPauseMark : public StackObj {
  G1YoungCollector* _collector;

public:
  G1YoungGCNotifyPauseMark(G1YoungCollector* collector) : _collector(collector) {
    G1CollectedHeap::heap()->policy()->record_young_gc_pause_start();
  }

  ~G1YoungGCNotifyPauseMark() {
    G1CollectedHeap::heap()->policy()->record_young_gc_pause_end(_collector->evacuation_failed());
  }
};

class G1YoungGCJFRTracerMark : public G1JFRTracerMark {
  G1EvacInfo _evacuation_info;

  G1NewTracer* tracer() const { return (G1NewTracer*)_tracer; }

public:

  G1EvacInfo* evacuation_info() { return &_evacuation_info; }

  G1YoungGCJFRTracerMark(STWGCTimer* gc_timer_stw, G1NewTracer* gc_tracer_stw, GCCause::Cause cause) :
    G1JFRTracerMark(gc_timer_stw, gc_tracer_stw), _evacuation_info() { }

  void report_pause_type(G1GCPauseType type) {
    tracer()->report_young_gc_pause(type);
  }

  ~G1YoungGCJFRTracerMark() {
    G1CollectedHeap* g1h = G1CollectedHeap::heap();

    tracer()->report_evacuation_info(&_evacuation_info);
    tracer()->report_tenuring_threshold(g1h->policy()->tenuring_threshold());
  }
};

class G1YoungGCVerifierMark : public StackObj {
  G1YoungCollector* _collector;
  G1HeapVerifier::G1VerifyType _type;

  static G1HeapVerifier::G1VerifyType young_collection_verify_type() {
    G1CollectorState* state = G1CollectedHeap::heap()->collector_state();
    if (state->in_concurrent_start_gc()) {
      return G1HeapVerifier::G1VerifyConcurrentStart;
    } else if (state->in_young_only_phase()) {
      return G1HeapVerifier::G1VerifyYoungNormal;
    } else {
      return G1HeapVerifier::G1VerifyMixed;
    }
  }

public:
  G1YoungGCVerifierMark(G1YoungCollector* collector) : _collector(collector), _type(young_collection_verify_type()) {
    G1CollectedHeap::heap()->verify_before_young_collection(_type);
  }

  ~G1YoungGCVerifierMark() {
    // Inject evacuation failure tag into type if needed.
    G1HeapVerifier::G1VerifyType type = _type;
    if (_collector->evacuation_failed()) {
      type = (G1HeapVerifier::G1VerifyType)(type | G1HeapVerifier::G1VerifyYoungEvacFail);
    }
    G1CollectedHeap::heap()->verify_after_young_collection(type);
  }
};

G1Allocator* G1YoungCollector::allocator() const {
  return _g1h->allocator();
}

G1CollectionSet* G1YoungCollector::collection_set() const {
  return _g1h->collection_set();
}

G1CollectorState* G1YoungCollector::collector_state() const {
  return _g1h->collector_state();
}

G1ConcurrentMark* G1YoungCollector::concurrent_mark() const {
  return _g1h->concurrent_mark();
}

STWGCTimer* G1YoungCollector::gc_timer_stw() const {
  return _g1h->gc_timer_stw();
}

G1NewTracer* G1YoungCollector::gc_tracer_stw() const {
  return _g1h->gc_tracer_stw();
}

G1Policy* G1YoungCollector::policy() const {
  return _g1h->policy();
}

G1GCPhaseTimes* G1YoungCollector::phase_times() const {
  return _g1h->phase_times();
}

G1MonitoringSupport* G1YoungCollector::monitoring_support() const {
  return _g1h->monitoring_support();
}

G1RemSet* G1YoungCollector::rem_set() const {
  return _g1h->rem_set();
}

G1ScannerTasksQueueSet* G1YoungCollector::task_queues() const {
  return _g1h->task_queues();
}

G1SurvivorRegions* G1YoungCollector::survivor_regions() const {
  return _g1h->survivor();
}

ReferenceProcessor* G1YoungCollector::ref_processor_stw() const {
  return _g1h->ref_processor_stw();
}

WorkerThreads* G1YoungCollector::workers() const {
  return _g1h->workers();
}

G1YoungGCAllocationFailureInjector* G1YoungCollector::allocation_failure_injector() const {
  return _g1h->allocation_failure_injector();
}


void G1YoungCollector::wait_for_root_region_scanning() {
  Ticks start = Ticks::now();
  // We have to wait until the CM threads finish scanning the
  // root regions as it's the only way to ensure that all the
  // objects on them have been correctly scanned before we start
  // moving them during the GC.
  bool waited = concurrent_mark()->wait_until_root_region_scan_finished();
  Tickspan wait_time;
  if (waited) {
    wait_time = (Ticks::now() - start);
  }
  phase_times()->record_root_region_scan_wait_time(wait_time.seconds() * MILLIUNITS);
}

class G1PrintCollectionSetClosure : public G1HeapRegionClosure {
public:
  virtual bool do_heap_region(G1HeapRegion* r) {
    G1HeapRegionPrinter::cset(r);
    return false;
  }
};

void G1YoungCollector::calculate_collection_set(G1EvacInfo* evacuation_info, double target_pause_time_ms) {
  // Forget the current allocation region (we might even choose it to be part
  // of the collection set!) before finalizing the collection set.
  allocator()->release_mutator_alloc_regions();

  collection_set()->finalize_initial_collection_set(target_pause_time_ms, survivor_regions());
  evacuation_info->set_collection_set_regions(collection_set()->region_length() +
                                              collection_set()->num_optional_regions());

  concurrent_mark()->verify_no_collection_set_oops();

  if (G1HeapRegionPrinter::is_active()) {
    G1PrintCollectionSetClosure cl;
    collection_set()->iterate(&cl);
    collection_set()->iterate_optional(&cl);
  }
}

class G1PrepareEvacuationTask : public WorkerTask {
  class G1PrepareRegionsClosure : public G1HeapRegionClosure {
    G1CollectedHeap* _g1h;
    G1PrepareEvacuationTask* _parent_task;
    uint _worker_humongous_total;
    uint _worker_humongous_candidates;

    G1MonotonicArenaMemoryStats _card_set_stats;

    void sample_card_set_size(G1HeapRegion* hr) {
      // Sample card set sizes for humongous before GC: this makes the policy to give
      // back memory to the OS keep the most recent amount of memory for these regions.
      if (hr->is_starts_humongous()) {
        _card_set_stats.add(hr->rem_set()->card_set_memory_stats());
      }
    }

    bool humongous_region_is_candidate(G1HeapRegion* region) const {
      assert(region->is_starts_humongous(), "Must start a humongous object");

      oop obj = cast_to_oop(region->bottom());

      // Dead objects cannot be eager reclaim candidates. Due to class
      // unloading it is unsafe to query their classes so we return early.
      if (_g1h->is_obj_dead(obj, region)) {
        return false;
      }

      // If we do not have a complete remembered set for the region, then we can
      // not be sure that we have all references to it.
      if (!region->rem_set()->is_complete()) {
        return false;
      }
      // We also cannot collect the humongous object if it is pinned.
      if (region->has_pinned_objects()) {
        return false;
      }
      // Candidate selection must satisfy the following constraints
      // while concurrent marking is in progress:
      //
      // * In order to maintain SATB invariants, an object must not be
      // reclaimed if it was allocated before the start of marking and
      // has not had its references scanned.  Such an object must have
      // its references (including type metadata) scanned to ensure no
      // live objects are missed by the marking process.  Objects
      // allocated after the start of concurrent marking don't need to
      // be scanned.
      //
      // * An object must not be reclaimed if it is on the concurrent
      // mark stack.  Objects allocated after the start of concurrent
      // marking are never pushed on the mark stack.
      //
      // Nominating only objects allocated after the start of concurrent
      // marking is sufficient to meet both constraints.  This may miss
      // some objects that satisfy the constraints, but the marking data
      // structures don't support efficiently performing the needed
      // additional tests or scrubbing of the mark stack.
      //
      // However, we presently only nominate is_typeArray() objects.
      // A humongous object containing references induces remembered
      // set entries on other regions.  In order to reclaim such an
      // object, those remembered sets would need to be cleaned up.
      //
      // We also treat is_typeArray() objects specially, allowing them
      // to be reclaimed even if allocated before the start of
      // concurrent mark.  For this we rely on mark stack insertion to
      // exclude is_typeArray() objects, preventing reclaiming an object
      // that is in the mark stack.  We also rely on the metadata for
      // such objects to be built-in and so ensured to be kept live.
      // Frequent allocation and drop of large binary blobs is an
      // important use case for eager reclaim, and this special handling
      // may reduce needed headroom.

      return obj->is_typeArray() &&
             _g1h->is_potential_eager_reclaim_candidate(region);
    }

  public:
    G1PrepareRegionsClosure(G1CollectedHeap* g1h, G1PrepareEvacuationTask* parent_task) :
      _g1h(g1h),
      _parent_task(parent_task),
      _worker_humongous_total(0),
      _worker_humongous_candidates(0) { }

    ~G1PrepareRegionsClosure() {
      _parent_task->add_humongous_candidates(_worker_humongous_candidates);
      _parent_task->add_humongous_total(_worker_humongous_total);
    }

    virtual bool do_heap_region(G1HeapRegion* hr) {
      // First prepare the region for scanning
      _g1h->rem_set()->prepare_region_for_scan(hr);

      sample_card_set_size(hr);

      // Now check if region is a humongous candidate
      if (!hr->is_starts_humongous()) {
        _g1h->register_region_with_region_attr(hr);
        return false;
      }

      uint index = hr->hrm_index();
      if (humongous_region_is_candidate(hr)) {
        _g1h->register_humongous_candidate_region_with_region_attr(index);
        _worker_humongous_candidates++;
        // We will later handle the remembered sets of these regions.
      } else {
        _g1h->register_region_with_region_attr(hr);
      }
      log_debug(gc, humongous)("Humongous region %u (object size %zu @ " PTR_FORMAT ") remset %zu code roots %zu "
                               "marked %d pinned count %zu reclaim candidate %d type array %d",
                               index,
                               cast_to_oop(hr->bottom())->size() * HeapWordSize,
                               p2i(hr->bottom()),
                               hr->rem_set()->occupied(),
                               hr->rem_set()->code_roots_list_length(),
                               _g1h->concurrent_mark()->mark_bitmap()->is_marked(hr->bottom()),
                               hr->pinned_count(),
                               _g1h->is_humongous_reclaim_candidate(index),
                               cast_to_oop(hr->bottom())->is_typeArray()
                              );
      _worker_humongous_total++;

      return false;
    }

    G1MonotonicArenaMemoryStats card_set_stats() const {
      return _card_set_stats;
    }
  };

  G1CollectedHeap* _g1h;
  G1HeapRegionClaimer _claimer;
  volatile uint _humongous_total;
  volatile uint _humongous_candidates;

  G1MonotonicArenaMemoryStats _all_card_set_stats;

public:
  G1PrepareEvacuationTask(G1CollectedHeap* g1h) :
    WorkerTask("Prepare Evacuation"),
    _g1h(g1h),
    _claimer(_g1h->workers()->active_workers()),
    _humongous_total(0),
    _humongous_candidates(0) { }

  void work(uint worker_id) {
    G1PrepareRegionsClosure cl(_g1h, this);
    _g1h->heap_region_par_iterate_from_worker_offset(&cl, &_claimer, worker_id);

    MutexLocker x(G1RareEvent_lock, Mutex::_no_safepoint_check_flag);
    _all_card_set_stats.add(cl.card_set_stats());
  }

  void add_humongous_candidates(uint candidates) {
    Atomic::add(&_humongous_candidates, candidates);
  }

  void add_humongous_total(uint total) {
    Atomic::add(&_humongous_total, total);
  }

  uint humongous_candidates() {
    return _humongous_candidates;
  }

  uint humongous_total() {
    return _humongous_total;
  }

  const G1MonotonicArenaMemoryStats all_card_set_stats() const {
    return _all_card_set_stats;
  }
};

Tickspan G1YoungCollector::run_task_timed(WorkerTask* task) {
  Ticks start = Ticks::now();
  workers()->run_task(task);
  return Ticks::now() - start;
}

void G1YoungCollector::set_young_collection_default_active_worker_threads(){
  uint active_workers = WorkerPolicy::calc_active_workers(workers()->max_workers(),
                                                          workers()->active_workers(),
                                                          Threads::number_of_non_daemon_threads());
  active_workers = workers()->set_active_workers(active_workers);
  log_info(gc,task)("Using %u workers of %u for evacuation", active_workers, workers()->max_workers());
}

void G1YoungCollector::pre_evacuate_collection_set(G1EvacInfo* evacuation_info) {
  // Flush various data in thread-local buffers to be able to determine the collection
  // set
  {
    Ticks start = Ticks::now();
    G1PreEvacuateCollectionSetBatchTask cl;
    G1CollectedHeap::heap()->run_batch_task(&cl);
    phase_times()->record_pre_evacuate_prepare_time_ms((Ticks::now() - start).seconds() * 1000.0);
  }

  // Needs log buffers flushed.
  calculate_collection_set(evacuation_info, policy()->max_pause_time_ms());

  if (collector_state()->in_concurrent_start_gc()) {
    Ticks start = Ticks::now();
    concurrent_mark()->pre_concurrent_start(_gc_cause);
    phase_times()->record_prepare_concurrent_task_time_ms((Ticks::now() - start).seconds() * 1000.0);
  }

  // Please see comment in g1CollectedHeap.hpp and
  // G1CollectedHeap::ref_processing_init() to see how
  // reference processing currently works in G1.
  ref_processor_stw()->start_discovery(false /* always_clear */);

  _evac_failure_regions.pre_collection(_g1h->max_num_regions());

  _g1h->gc_prologue(false);

  // Initialize the GC alloc regions.
  allocator()->init_gc_alloc_regions(evacuation_info);

  {
    Ticks start = Ticks::now();
    rem_set()->prepare_for_scan_heap_roots();

    _g1h->prepare_group_cardsets_for_scan();

    phase_times()->record_prepare_heap_roots_time_ms((Ticks::now() - start).seconds() * 1000.0);
  }

  {
    G1PrepareEvacuationTask g1_prep_task(_g1h);
    Tickspan task_time = run_task_timed(&g1_prep_task);

    G1MonotonicArenaMemoryStats sampled_card_set_stats = g1_prep_task.all_card_set_stats();
    sampled_card_set_stats.add(_g1h->young_regions_card_set_memory_stats());
    _g1h->set_young_gen_card_set_stats(sampled_card_set_stats);

    _g1h->set_humongous_stats(g1_prep_task.humongous_total(), g1_prep_task.humongous_candidates());

    phase_times()->record_register_regions(task_time.seconds() * 1000.0);
  }

  assert(_g1h->verifier()->check_region_attr_table(), "Inconsistency in the region attributes table.");

#if COMPILER2_OR_JVMCI
  DerivedPointerTable::clear();
#endif

  allocation_failure_injector()->arm_if_needed();
}

class G1ParEvacuateFollowersClosure : public VoidClosure {
  double _start_term;
  double _term_time;
  size_t _term_attempts;

  void start_term_time() { _term_attempts++; _start_term = os::elapsedTime(); }
  void end_term_time() { _term_time += (os::elapsedTime() - _start_term); }

  G1CollectedHeap*              _g1h;
  G1ParScanThreadState*         _par_scan_state;
  G1ScannerTasksQueueSet*       _queues;
  TaskTerminator*               _terminator;
  G1GCPhaseTimes::GCParPhases   _phase;

  G1ParScanThreadState*   par_scan_state() { return _par_scan_state; }
  G1ScannerTasksQueueSet* queues()         { return _queues; }
  TaskTerminator*         terminator()     { return _terminator; }

  inline bool offer_termination() {
    EventGCPhaseParallel event;
    G1ParScanThreadState* const pss = par_scan_state();
    start_term_time();
    const bool res = (terminator() == nullptr) ? true : terminator()->offer_termination();
    end_term_time();
    event.commit(GCId::current(), pss->worker_id(), G1GCPhaseTimes::phase_name(G1GCPhaseTimes::Termination));
    return res;
  }

public:
  G1ParEvacuateFollowersClosure(G1CollectedHeap* g1h,
                                G1ParScanThreadState* par_scan_state,
                                G1ScannerTasksQueueSet* queues,
                                TaskTerminator* terminator,
                                G1GCPhaseTimes::GCParPhases phase)
    : _start_term(0.0), _term_time(0.0), _term_attempts(0),
      _g1h(g1h), _par_scan_state(par_scan_state),
      _queues(queues), _terminator(terminator), _phase(phase) {}

  void do_void() {
    EventGCPhaseParallel event;
    G1ParScanThreadState* const pss = par_scan_state();
    pss->trim_queue();
    event.commit(GCId::current(), pss->worker_id(), G1GCPhaseTimes::phase_name(_phase));
    do {
      EventGCPhaseParallel event;
      pss->steal_and_trim_queue(queues());
      event.commit(GCId::current(), pss->worker_id(), G1GCPhaseTimes::phase_name(_phase));
    } while (!offer_termination());
  }

  double term_time() const { return _term_time; }
  size_t term_attempts() const { return _term_attempts; }
};

class G1EvacuateRegionsBaseTask : public WorkerTask {

  // All pinned regions in the collection set must be registered as failed
  // regions as there is no guarantee that there is a reference reachable by
  // Java code (i.e. only by native code) that adds it to the evacuation failed
  // regions.
  void record_pinned_regions(G1ParScanThreadState* pss, uint worker_id) {
    class RecordPinnedRegionClosure : public G1HeapRegionClosure {
      G1ParScanThreadState* _pss;
      uint _worker_id;

    public:
      RecordPinnedRegionClosure(G1ParScanThreadState* pss, uint worker_id) : _pss(pss), _worker_id(worker_id) { }

      bool do_heap_region(G1HeapRegion* r) {
        if (r->has_pinned_objects()) {
          _pss->record_evacuation_failed_region(r, _worker_id, true /* cause_pinned */);
        }
        return false;
      }
    } cl(pss, worker_id);

    _g1h->collection_set_iterate_increment_from(&cl, worker_id);
  }

protected:
  G1CollectedHeap* _g1h;
  G1ParScanThreadStateSet* _per_thread_states;

  G1ScannerTasksQueueSet* _task_queues;
  TaskTerminator _terminator;

  void evacuate_live_objects(G1ParScanThreadState* pss,
                             uint worker_id,
                             G1GCPhaseTimes::GCParPhases objcopy_phase,
                             G1GCPhaseTimes::GCParPhases termination_phase) {
    G1GCPhaseTimes* p = _g1h->phase_times();

    Ticks start = Ticks::now();
    G1ParEvacuateFollowersClosure cl(_g1h, pss, _task_queues, &_terminator, objcopy_phase);
    cl.do_void();

    assert(pss->queue_is_empty(), "should be empty");

    Tickspan evac_time = (Ticks::now() - start);
    p->record_or_add_time_secs(objcopy_phase, worker_id, evac_time.seconds() - cl.term_time());

    if (termination_phase == G1GCPhaseTimes::Termination) {
      p->record_time_secs(termination_phase, worker_id, cl.term_time());
      p->record_thread_work_item(termination_phase, worker_id, cl.term_attempts());
    } else {
      p->record_or_add_time_secs(termination_phase, worker_id, cl.term_time());
      p->record_or_add_thread_work_item(termination_phase, worker_id, cl.term_attempts());
    }
    assert(pss->trim_ticks().value() == 0,
           "Unexpected partial trimming during evacuation value " JLONG_FORMAT,
           pss->trim_ticks().value());
  }

  virtual void start_work(uint worker_id) { }

  virtual void end_work(uint worker_id) { }

  virtual void scan_roots(G1ParScanThreadState* pss, uint worker_id) = 0;

  virtual void evacuate_live_objects(G1ParScanThreadState* pss, uint worker_id) = 0;

private:
  volatile bool _pinned_regions_recorded;

public:
  G1EvacuateRegionsBaseTask(const char* name,
                            G1ParScanThreadStateSet* per_thread_states,
                            G1ScannerTasksQueueSet* task_queues,
                            uint num_workers) :
    WorkerTask(name),
    _g1h(G1CollectedHeap::heap()),
    _per_thread_states(per_thread_states),
    _task_queues(task_queues),
    _terminator(num_workers, _task_queues),
    _pinned_regions_recorded(false)
  { }

  void work(uint worker_id) {
    start_work(worker_id);

    {
      ResourceMark rm;

      G1ParScanThreadState* pss = _per_thread_states->state_for_worker(worker_id);
      pss->set_ref_discoverer(_g1h->ref_processor_stw());

      if (!Atomic::cmpxchg(&_pinned_regions_recorded, false, true)) {
        record_pinned_regions(pss, worker_id);
      }
      scan_roots(pss, worker_id);
      evacuate_live_objects(pss, worker_id);
    }

    end_work(worker_id);
  }
};

class G1EvacuateRegionsTask : public G1EvacuateRegionsBaseTask {
  G1RootProcessor* _root_processor;
  bool _has_optional_evacuation_work;

  void scan_roots(G1ParScanThreadState* pss, uint worker_id) {
    _root_processor->evacuate_roots(pss, worker_id);
    _g1h->rem_set()->scan_heap_roots(pss, worker_id, G1GCPhaseTimes::ScanHR, G1GCPhaseTimes::ObjCopy, _has_optional_evacuation_work);
    _g1h->rem_set()->scan_collection_set_code_roots(pss, worker_id, G1GCPhaseTimes::CodeRoots, G1GCPhaseTimes::ObjCopy);
    // There are no optional roots to scan right now.
#ifdef ASSERT
    class VerifyOptionalCollectionSetRootsEmptyClosure : public G1HeapRegionClosure {
      G1ParScanThreadState* _pss;

    public:
      VerifyOptionalCollectionSetRootsEmptyClosure(G1ParScanThreadState* pss) : _pss(pss) { }

      bool do_heap_region(G1HeapRegion* r) override {
        assert(!r->has_index_in_opt_cset(), "must be");
        return false;
      }
    } cl(pss);
    _g1h->collection_set_iterate_increment_from(&cl, worker_id);
#endif
  }

  void evacuate_live_objects(G1ParScanThreadState* pss, uint worker_id) {
    G1EvacuateRegionsBaseTask::evacuate_live_objects(pss, worker_id, G1GCPhaseTimes::ObjCopy, G1GCPhaseTimes::Termination);
  }

  void start_work(uint worker_id) {
    _g1h->phase_times()->record_time_secs(G1GCPhaseTimes::GCWorkerStart, worker_id, Ticks::now().seconds());
  }

  void end_work(uint worker_id) {
    _g1h->phase_times()->record_time_secs(G1GCPhaseTimes::GCWorkerEnd, worker_id, Ticks::now().seconds());
  }

public:
  G1EvacuateRegionsTask(G1CollectedHeap* g1h,
                        G1ParScanThreadStateSet* per_thread_states,
                        G1ScannerTasksQueueSet* task_queues,
                        G1RootProcessor* root_processor,
                        uint num_workers,
                        bool has_optional_evacuation_work) :
    G1EvacuateRegionsBaseTask("G1 Evacuate Regions", per_thread_states, task_queues, num_workers),
    _root_processor(root_processor),
    _has_optional_evacuation_work(has_optional_evacuation_work)
  { }
};

void G1YoungCollector::evacuate_initial_collection_set(G1ParScanThreadStateSet* per_thread_states,
                                                      bool has_optional_evacuation_work) {
  G1GCPhaseTimes* p = phase_times();

  rem_set()->merge_heap_roots(true /* initial_evacuation */);

  Tickspan task_time;
  const uint num_workers = workers()->active_workers();

  Ticks start_processing = Ticks::now();
  {
    G1RootProcessor root_processor(_g1h, num_workers);
    G1EvacuateRegionsTask g1_par_task(_g1h,
                                      per_thread_states,
                                      task_queues(),
                                      &root_processor,
                                      num_workers,
                                      has_optional_evacuation_work);
    task_time = run_task_timed(&g1_par_task);
    // Closing the inner scope will execute the destructor for the
    // G1RootProcessor object. By subtracting the WorkerThreads task from the total
    // time of this scope, we get the "NMethod List Cleanup" time. This list is
    // constructed during "STW two-phase nmethod root processing", see more in
    // nmethod.hpp
  }
  Tickspan total_processing = Ticks::now() - start_processing;

  p->record_initial_evac_time(task_time.seconds() * 1000.0);
  p->record_or_add_nmethod_list_cleanup_time((total_processing - task_time).seconds() * 1000.0);

  rem_set()->complete_evac_phase(has_optional_evacuation_work);
}

class G1EvacuateOptionalRegionsTask : public G1EvacuateRegionsBaseTask {

  void scan_roots(G1ParScanThreadState* pss, uint worker_id) {
    _g1h->rem_set()->scan_heap_roots(pss, worker_id, G1GCPhaseTimes::OptScanHR, G1GCPhaseTimes::OptObjCopy, true /* remember_already_scanned_cards */);
    _g1h->rem_set()->scan_collection_set_code_roots(pss, worker_id, G1GCPhaseTimes::OptCodeRoots, G1GCPhaseTimes::OptObjCopy);
    _g1h->rem_set()->scan_collection_set_optional_roots(pss, worker_id, G1GCPhaseTimes::OptScanHR, G1GCPhaseTimes::ObjCopy);
  }

  void evacuate_live_objects(G1ParScanThreadState* pss, uint worker_id) {
    G1EvacuateRegionsBaseTask::evacuate_live_objects(pss, worker_id, G1GCPhaseTimes::OptObjCopy, G1GCPhaseTimes::OptTermination);
  }

public:
  G1EvacuateOptionalRegionsTask(G1ParScanThreadStateSet* per_thread_states,
                                G1ScannerTasksQueueSet* queues,
                                uint num_workers) :
    G1EvacuateRegionsBaseTask("G1 Evacuate Optional Regions", per_thread_states, queues, num_workers) {
  }
};

void G1YoungCollector::evacuate_next_optional_regions(G1ParScanThreadStateSet* per_thread_states) {
  // To access the protected constructor/destructor
  class G1MarkScope : public MarkScope { };

  Tickspan task_time;

  Ticks start_processing = Ticks::now();
  {
    G1MarkScope code_mark_scope;
    G1EvacuateOptionalRegionsTask task(per_thread_states, task_queues(), workers()->active_workers());
    task_time = run_task_timed(&task);
    // See comment in evacuate_initial_collection_set() for the reason of the scope.
  }
  Tickspan total_processing = Ticks::now() - start_processing;

  G1GCPhaseTimes* p = phase_times();
  p->record_or_add_optional_evac_time(task_time.seconds() * 1000.0);
  p->record_or_add_nmethod_list_cleanup_time((total_processing - task_time).seconds() * 1000.0);
}

void G1YoungCollector::evacuate_optional_collection_set(G1ParScanThreadStateSet* per_thread_states) {
  const double pause_start_time_ms = policy()->cur_pause_start_sec() * 1000.0;

  while (!evacuation_alloc_failed() && collection_set()->num_optional_regions() > 0) {

    double time_used_ms = os::elapsedTime() * 1000.0 - pause_start_time_ms;
    double time_left_ms = MaxGCPauseMillis - time_used_ms;

    if (time_left_ms < 0 ||
        !collection_set()->finalize_optional_for_evacuation(time_left_ms * policy()->optional_evacuation_fraction())) {
      log_trace(gc, ergo, cset)("Skipping evacuation of %u optional regions, no more regions can be evacuated in %.3fms",
                                collection_set()->num_optional_regions(), time_left_ms);
      break;
    }

    rem_set()->merge_heap_roots(false /* initial_evacuation */);

    evacuate_next_optional_regions(per_thread_states);

    rem_set()->complete_evac_phase(true /* has_more_than_one_evacuation_phase */);
  }

  collection_set()->abandon_optional_collection_set(per_thread_states);
}

// Non Copying Keep Alive closure
class G1KeepAliveClosure: public OopClosure {
  G1CollectedHeap*_g1h;
public:
  G1KeepAliveClosure(G1CollectedHeap* g1h) :_g1h(g1h) {}
  void do_oop(narrowOop* p) { guarantee(false, "Not needed"); }
  void do_oop(oop* p) {
    oop obj = *p;
    assert(obj != nullptr, "the caller should have filtered out null values");

    const G1HeapRegionAttr region_attr =_g1h->region_attr(obj);
    if (!region_attr.is_in_cset_or_humongous_candidate()) {
      return;
    }
    if (region_attr.is_in_cset()) {
      assert(obj->is_forwarded(), "invariant" );
      *p = obj->forwardee();
    } else {
      assert(!obj->is_forwarded(), "invariant" );
      assert(region_attr.is_humongous_candidate(),
             "Only allowed G1HeapRegionAttr state is IsHumongous, but is %d", region_attr.type());
     _g1h->set_humongous_is_live(obj);
    }
  }
};

// Copying Keep Alive closure - can be called from both
// serial and parallel code as long as different worker
// threads utilize different G1ParScanThreadState instances
// and different queues.
class G1CopyingKeepAliveClosure: public OopClosure {
  G1CollectedHeap* _g1h;
  G1ParScanThreadState*    _par_scan_state;

public:
  G1CopyingKeepAliveClosure(G1CollectedHeap* g1h,
                            G1ParScanThreadState* pss):
    _g1h(g1h),
    _par_scan_state(pss)
  {}

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(      oop* p) { do_oop_work(p); }

  template <class T> void do_oop_work(T* p) {
    oop obj = RawAccess<>::oop_load(p);

    if (_g1h->is_in_cset_or_humongous_candidate(obj)) {
      // If the referent object has been forwarded (either copied
      // to a new location or to itself in the event of an
      // evacuation failure) then we need to update the reference
      // field and, if both reference and referent are in the G1
      // heap, update the RSet for the referent.
      //
      // If the referent has not been forwarded then we have to keep
      // it alive by policy. Therefore we have copy the referent.
      //
      // When the queue is drained (after each phase of reference processing)
      // the object and it's followers will be copied, the reference field set
      // to point to the new location, and the RSet updated.
      _par_scan_state->push_on_queue(ScannerTask(p));
    }
  }
};

class G1STWRefProcProxyTask : public RefProcProxyTask {
  G1CollectedHeap& _g1h;
  G1ParScanThreadStateSet& _pss;
  TaskTerminator _terminator;
  G1ScannerTasksQueueSet& _task_queues;

  // Special closure for enqueuing discovered fields: during enqueue the card table
  // may not be in shape to properly handle normal barrier calls (e.g. card marks
  // in regions that failed evacuation, scribbling of various values by card table
  // scan code). Additionally the regular barrier enqueues into the "global"
  // DCQS, but during GC we need these to-be-refined entries in the GC local queue
  // so that after clearing the card table, the redirty cards phase will properly
  // mark all dirty cards to be picked up by refinement.
  class G1EnqueueDiscoveredFieldClosure : public EnqueueDiscoveredFieldClosure {
    G1CollectedHeap* _g1h;
    G1ParScanThreadState* _pss;

  public:
    G1EnqueueDiscoveredFieldClosure(G1CollectedHeap* g1h, G1ParScanThreadState* pss) : _g1h(g1h), _pss(pss) { }

    void enqueue(HeapWord* discovered_field_addr, oop value) override {
      assert(_g1h->is_in(discovered_field_addr), PTR_FORMAT " is not in heap ", p2i(discovered_field_addr));
      // Store the value first, whatever it is.
      RawAccess<>::oop_store(discovered_field_addr, value);
      if (value == nullptr) {
        return;
      }
      _pss->write_ref_field_post(discovered_field_addr, value);
    }
  };

public:
  G1STWRefProcProxyTask(uint max_workers, G1CollectedHeap& g1h, G1ParScanThreadStateSet& pss, G1ScannerTasksQueueSet& task_queues)
    : RefProcProxyTask("G1STWRefProcProxyTask", max_workers),
      _g1h(g1h),
      _pss(pss),
      _terminator(max_workers, &task_queues),
      _task_queues(task_queues) {}

  void work(uint worker_id) override {
    assert(worker_id < _max_workers, "sanity");
    uint index = (_tm == RefProcThreadModel::Single) ? 0 : worker_id;

    G1ParScanThreadState* pss = _pss.state_for_worker(index);
    pss->set_ref_discoverer(nullptr);

    G1STWIsAliveClosure is_alive(&_g1h);
    G1CopyingKeepAliveClosure keep_alive(&_g1h, pss);
    G1EnqueueDiscoveredFieldClosure enqueue(&_g1h, pss);
    G1ParEvacuateFollowersClosure complete_gc(&_g1h, pss, &_task_queues, _tm == RefProcThreadModel::Single ? nullptr : &_terminator, G1GCPhaseTimes::ObjCopy);
    _rp_task->rp_work(worker_id, &is_alive, &keep_alive, &enqueue, &complete_gc);

    // We have completed copying any necessary live referent objects.
    assert(pss->queue_is_empty(), "both queue and overflow should be empty");
  }

  void prepare_run_task_hook() override {
    _terminator.reset_for_reuse(_queue_count);
  }
};

void G1YoungCollector::process_discovered_references(G1ParScanThreadStateSet* per_thread_states) {
  Ticks start = Ticks::now();

  ReferenceProcessor* rp = ref_processor_stw();
  assert(rp->discovery_enabled(), "should have been enabled");

  G1STWRefProcProxyTask task(rp->max_num_queues(), *_g1h, *per_thread_states, *task_queues());
  ReferenceProcessorPhaseTimes& pt = *phase_times()->ref_phase_times();
  ReferenceProcessorStats stats = rp->process_discovered_references(task, _g1h->workers(), pt);

  gc_tracer_stw()->report_gc_reference_stats(stats);

  _g1h->make_pending_list_reachable();

  phase_times()->record_ref_proc_time((Ticks::now() - start).seconds() * MILLIUNITS);
}

void G1YoungCollector::post_evacuate_cleanup_1(G1ParScanThreadStateSet* per_thread_states) {
  Ticks start = Ticks::now();
  {
    G1PostEvacuateCollectionSetCleanupTask1 cl(per_thread_states, &_evac_failure_regions);
    _g1h->run_batch_task(&cl);
  }
  phase_times()->record_post_evacuate_cleanup_task_1_time((Ticks::now() - start).seconds() * 1000.0);
}

void G1YoungCollector::post_evacuate_cleanup_2(G1ParScanThreadStateSet* per_thread_states,
                                               G1EvacInfo* evacuation_info) {
  Ticks start = Ticks::now();
  {
    G1PostEvacuateCollectionSetCleanupTask2 cl(per_thread_states, evacuation_info, &_evac_failure_regions);
    _g1h->run_batch_task(&cl);
  }
  phase_times()->record_post_evacuate_cleanup_task_2_time((Ticks::now() - start).seconds() * 1000.0);
}

void G1YoungCollector::enqueue_candidates_as_root_regions() {
  assert(collector_state()->in_concurrent_start_gc(), "must be");

  G1CollectionSetCandidates* candidates = collection_set()->candidates();
  candidates->iterate_regions([&] (G1HeapRegion* r) {
    _g1h->concurrent_mark()->add_root_region(r);
  });
}

void G1YoungCollector::post_evacuate_collection_set(G1EvacInfo* evacuation_info,
                                                    G1ParScanThreadStateSet* per_thread_states) {
  G1GCPhaseTimes* p = phase_times();

  // Process any discovered reference objects - we have
  // to do this _before_ we retire the GC alloc regions
  // as we may have to copy some 'reachable' referent
  // objects (and their reachable sub-graphs) that were
  // not copied during the pause.
  process_discovered_references(per_thread_states);

  G1STWIsAliveClosure is_alive(_g1h);
  G1KeepAliveClosure keep_alive(_g1h);

  WeakProcessor::weak_oops_do(workers(), &is_alive, &keep_alive, p->weak_phase_times());

  allocator()->release_gc_alloc_regions(evacuation_info);

#if TASKQUEUE_STATS
  // Logging uses thread states, which are deleted by cleanup, so this must
  // be done before cleanup.
  per_thread_states->print_partial_array_task_stats();
#endif // TASKQUEUE_STATS

  post_evacuate_cleanup_1(per_thread_states);

  post_evacuate_cleanup_2(per_thread_states, evacuation_info);

  // Regions in the collection set candidates are roots for the marking (they are
  // not marked through considering they are very likely to be reclaimed soon.
  // They need to be enqueued explicitly compared to survivor regions.
  if (collector_state()->in_concurrent_start_gc()) {
    enqueue_candidates_as_root_regions();
  }

  _evac_failure_regions.post_collection();

  assert_used_and_recalculate_used_equal(_g1h);

  _g1h->rebuild_free_region_list();

  _g1h->record_obj_copy_mem_stats();

  evacuation_info->set_bytes_used(_g1h->bytes_used_during_gc());

  _g1h->prepare_for_mutator_after_young_collection();

  _g1h->gc_epilogue(false);

  _g1h->resize_heap_after_young_collection(_allocation_word_size);
}

bool G1YoungCollector::evacuation_failed() const {
  return _evac_failure_regions.has_regions_evac_failed();
}

bool G1YoungCollector::evacuation_pinned() const {
  return _evac_failure_regions.has_regions_evac_pinned();
}

bool G1YoungCollector::evacuation_alloc_failed() const {
  return _evac_failure_regions.has_regions_alloc_failed();
}

G1YoungCollector::G1YoungCollector(GCCause::Cause gc_cause,
                                   size_t allocation_word_size) :
  _g1h(G1CollectedHeap::heap()),
  _gc_cause(gc_cause),
  _allocation_word_size(allocation_word_size),
  _concurrent_operation_is_full_mark(false),
  _evac_failure_regions()
{
}

void G1YoungCollector::collect() {
  // Do timing/tracing/statistics/pre- and post-logging/verification work not
  // directly related to the collection. They should not be accounted for in
  // collection work timing.

  // The G1YoungGCTraceTime message depends on collector state, so must come after
  // determining collector state.
  G1YoungGCTraceTime tm(this, _gc_cause);

  // JFR
  G1YoungGCJFRTracerMark jtm(gc_timer_stw(), gc_tracer_stw(), _gc_cause);
  // JStat/MXBeans
  G1YoungGCMonitoringScope ms(monitoring_support(),
                              !collection_set()->candidates()->is_empty() /* all_memory_pools_affected */);
  // Create the heap printer before internal pause timing to have
  // heap information printed as last part of detailed GC log.
  G1HeapPrinterMark hpm(_g1h);
  // Young GC internal pause timing
  G1YoungGCNotifyPauseMark npm(this);

  // Verification may use the workers, so they must be set up before.
  // Individual parallel phases may override this.
  set_young_collection_default_active_worker_threads();

  // Wait for root region scan here to make sure that it is done before any
  // use of the STW workers to maximize cpu use (i.e. all cores are available
  // just to do that).
  wait_for_root_region_scanning();

  G1YoungGCVerifierMark vm(this);
  {
    // Actual collection work starts and is executed (only) in this scope.

    // Young GC internal collection timing. The elapsed time recorded in the
    // policy for the collection deliberately elides verification (and some
    // other trivial setup above).
    policy()->record_young_collection_start();

    pre_evacuate_collection_set(jtm.evacuation_info());

    G1ParScanThreadStateSet per_thread_states(_g1h,
                                              workers()->active_workers(),
                                              collection_set(),
                                              &_evac_failure_regions);

    bool may_do_optional_evacuation = collection_set()->num_optional_regions() != 0;
    // Actually do the work...
    evacuate_initial_collection_set(&per_thread_states, may_do_optional_evacuation);

    if (may_do_optional_evacuation) {
      evacuate_optional_collection_set(&per_thread_states);
    }
    post_evacuate_collection_set(jtm.evacuation_info(), &per_thread_states);

    // Refine the type of a concurrent mark operation now that we did the
    // evacuation, eventually aborting it.
    _concurrent_operation_is_full_mark = policy()->concurrent_operation_is_full_mark("Revise IHOP");

    // Need to report the collection pause now since record_collection_pause_end()
    // modifies it to the next state.
    jtm.report_pause_type(collector_state()->young_gc_pause_type(_concurrent_operation_is_full_mark));

    policy()->record_young_collection_end(_concurrent_operation_is_full_mark, evacuation_alloc_failed());
  }
  TASKQUEUE_STATS_ONLY(_g1h->task_queues()->print_and_reset_taskqueue_stats("Oop Queue");)
}
