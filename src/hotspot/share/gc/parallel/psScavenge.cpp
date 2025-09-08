/*
 * Copyright (c) 2002, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/stringTable.hpp"
#include "code/codeCache.hpp"
#include "compiler/oopMap.hpp"
#include "gc/parallel/parallelScavengeHeap.hpp"
#include "gc/parallel/psAdaptiveSizePolicy.hpp"
#include "gc/parallel/psClosure.inline.hpp"
#include "gc/parallel/psCompactionManager.hpp"
#include "gc/parallel/psParallelCompact.inline.hpp"
#include "gc/parallel/psPromotionManager.inline.hpp"
#include "gc/parallel/psRootType.hpp"
#include "gc/parallel/psScavenge.inline.hpp"
#include "gc/shared/gcCause.hpp"
#include "gc/shared/gcHeapSummary.hpp"
#include "gc/shared/gcId.hpp"
#include "gc/shared/gcLocker.hpp"
#include "gc/shared/gcTimer.hpp"
#include "gc/shared/gcTrace.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/gcVMOperations.hpp"
#include "gc/shared/isGCActiveMark.hpp"
#include "gc/shared/oopStorage.inline.hpp"
#include "gc/shared/oopStorageParState.inline.hpp"
#include "gc/shared/oopStorageSetParState.inline.hpp"
#include "gc/shared/referencePolicy.hpp"
#include "gc/shared/referenceProcessor.hpp"
#include "gc/shared/referenceProcessorPhaseTimes.hpp"
#include "gc/shared/scavengableNMethods.hpp"
#include "gc/shared/spaceDecorator.hpp"
#include "gc/shared/strongRootsScope.hpp"
#include "gc/shared/taskTerminator.hpp"
#include "gc/shared/weakProcessor.inline.hpp"
#include "gc/shared/workerPolicy.hpp"
#include "gc/shared/workerThread.hpp"
#include "gc/shared/workerUtils.hpp"
#include "logging/log.hpp"
#include "memory/iterator.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/access.inline.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/threads.hpp"
#include "runtime/vmOperations.hpp"
#include "runtime/vmThread.hpp"
#include "services/memoryService.hpp"
#include "utilities/stack.inline.hpp"

SpanSubjectToDiscoveryClosure PSScavenge::_span_based_discoverer;
ReferenceProcessor*           PSScavenge::_ref_processor = nullptr;
PSCardTable*                  PSScavenge::_card_table = nullptr;
bool                          PSScavenge::_survivor_overflow = false;
uint                          PSScavenge::_tenuring_threshold = 0;
HeapWord*                     PSScavenge::_young_generation_boundary = nullptr;
uintptr_t                     PSScavenge::_young_generation_boundary_compressed = 0;
elapsedTimer                  PSScavenge::_accumulated_time;
STWGCTimer                    PSScavenge::_gc_timer;
ParallelScavengeTracer        PSScavenge::_gc_tracer;
CollectorCounters*            PSScavenge::_counters = nullptr;

static void scavenge_roots_work(ParallelRootType::Value root_type, uint worker_id) {
  assert(ParallelScavengeHeap::heap()->is_stw_gc_active(), "called outside gc");

  PSPromotionManager* pm = PSPromotionManager::gc_thread_promotion_manager(worker_id);
  PSPromoteRootsClosure  roots_to_old_closure(pm);

  switch (root_type) {
    case ParallelRootType::class_loader_data:
      {
        PSScavengeCLDClosure cld_closure(pm);
        ClassLoaderDataGraph::cld_do(&cld_closure);
      }
      break;

    case ParallelRootType::code_cache:
      {
        MarkingNMethodClosure code_closure(&roots_to_old_closure, NMethodToOopClosure::FixRelocations, false /* keepalive nmethods */);
        ScavengableNMethods::nmethods_do(&code_closure);
      }
      break;

    case ParallelRootType::sentinel:
    DEBUG_ONLY(default:) // DEBUG_ONLY hack will create compile error on release builds (-Wswitch) and runtime check on debug builds
      fatal("Bad enumeration value: %u", root_type);
      break;
  }

  // Do the real work
  pm->drain_stacks(false);
}

static void steal_work(TaskTerminator& terminator, uint worker_id) {
  assert(ParallelScavengeHeap::heap()->is_stw_gc_active(), "called outside gc");

  PSPromotionManager* pm =
    PSPromotionManager::gc_thread_promotion_manager(worker_id);
  pm->drain_stacks(true);
  guarantee(pm->stacks_empty(),
            "stacks should be empty at this point");

  while (true) {
    ScannerTask task;
    if (PSPromotionManager::steal_depth(worker_id, task)) {
      pm->process_popped_location_depth(task, true);
      pm->drain_stacks_depth(true);
    } else {
      if (terminator.offer_termination()) {
        break;
      }
    }
  }
  guarantee(pm->stacks_empty(), "stacks should be empty at this point");
}

// Define before use
class PSIsAliveClosure: public BoolObjectClosure {
public:
  bool do_object_b(oop p) {
    return (!PSScavenge::is_obj_in_young(p)) || p->is_forwarded();
  }
};

PSIsAliveClosure PSScavenge::_is_alive_closure;

class PSKeepAliveClosure: public OopClosure {
protected:
  MutableSpace* _to_space;
  PSPromotionManager* _promotion_manager;

public:
  PSKeepAliveClosure(PSPromotionManager* pm) : _promotion_manager(pm) {
    ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
    _to_space = heap->young_gen()->to_space();

    assert(_promotion_manager != nullptr, "Sanity");
  }

  template <class T> void do_oop_work(T* p) {
#ifdef ASSERT
    // Referent must be non-null and in from-space
    oop obj = RawAccess<IS_NOT_NULL>::oop_load(p);
    assert(oopDesc::is_oop(obj), "referent must be an oop");
    assert(PSScavenge::is_obj_in_young(obj), "must be in young-gen");
    assert(!PSScavenge::is_obj_in_to_space(obj), "must be in from-space");
#endif

    _promotion_manager->copy_and_push_safe_barrier</*promote_immediately=*/false>(p);
  }
  virtual void do_oop(oop* p)       { PSKeepAliveClosure::do_oop_work(p); }
  virtual void do_oop(narrowOop* p) { PSKeepAliveClosure::do_oop_work(p); }
};

class PSEvacuateFollowersClosure: public VoidClosure {
 private:
  PSPromotionManager* _promotion_manager;
  TaskTerminator* _terminator;
  uint _worker_id;

 public:
  PSEvacuateFollowersClosure(PSPromotionManager* pm, TaskTerminator* terminator, uint worker_id)
    : _promotion_manager(pm), _terminator(terminator), _worker_id(worker_id) {}

  virtual void do_void() {
    assert(_promotion_manager != nullptr, "Sanity");
    _promotion_manager->drain_stacks(true);
    guarantee(_promotion_manager->stacks_empty(),
              "stacks should be empty at this point");

    if (_terminator != nullptr) {
      steal_work(*_terminator, _worker_id);
    }
  }
};

class ParallelScavengeRefProcProxyTask : public RefProcProxyTask {
  TaskTerminator _terminator;

public:
  ParallelScavengeRefProcProxyTask(uint max_workers)
    : RefProcProxyTask("ParallelScavengeRefProcProxyTask", max_workers),
      _terminator(max_workers, ParCompactionManager::marking_stacks()) {}

  void work(uint worker_id) override {
    assert(worker_id < _max_workers, "sanity");
    PSPromotionManager* promotion_manager = (_tm == RefProcThreadModel::Single) ? PSPromotionManager::vm_thread_promotion_manager() : PSPromotionManager::gc_thread_promotion_manager(worker_id);
    PSIsAliveClosure is_alive;
    PSKeepAliveClosure keep_alive(promotion_manager);
    BarrierEnqueueDiscoveredFieldClosure enqueue;
    PSEvacuateFollowersClosure complete_gc(promotion_manager, (_marks_oops_alive && _tm == RefProcThreadModel::Multi) ? &_terminator : nullptr, worker_id);;
    _rp_task->rp_work(worker_id, &is_alive, &keep_alive, &enqueue, &complete_gc);
  }

  void prepare_run_task_hook() override {
    _terminator.reset_for_reuse(_queue_count);
  }
};

class PSThreadRootsTaskClosure : public ThreadClosure {
  uint _worker_id;
public:
  PSThreadRootsTaskClosure(uint worker_id) : _worker_id(worker_id) { }
  virtual void do_thread(Thread* thread) {
    assert(ParallelScavengeHeap::heap()->is_stw_gc_active(), "called outside gc");

    PSPromotionManager* pm = PSPromotionManager::gc_thread_promotion_manager(_worker_id);
    PSScavengeRootsClosure roots_closure(pm);

    // No need to visit nmethods, because they are handled by ScavengableNMethods.
    thread->oops_do(&roots_closure, nullptr);

    // Do the real work
    pm->drain_stacks(false);
  }
};

class ScavengeRootsTask : public WorkerTask {
  StrongRootsScope _strong_roots_scope; // needed for Threads::possibly_parallel_threads_do
  OopStorageSetStrongParState<false /* concurrent */, false /* is_const */> _oop_storage_strong_par_state;
  SequentialSubTasksDone _subtasks;
  PSOldGen* _old_gen;
  HeapWord* _gen_top;
  uint _active_workers;
  bool _is_old_gen_empty;
  TaskTerminator _terminator;

public:
  ScavengeRootsTask(PSOldGen* old_gen,
                    uint active_workers) :
    WorkerTask("ScavengeRootsTask"),
    _strong_roots_scope(active_workers),
    _subtasks(ParallelRootType::sentinel),
    _old_gen(old_gen),
    _gen_top(old_gen->object_space()->top()),
    _active_workers(active_workers),
    _is_old_gen_empty(old_gen->object_space()->is_empty()),
    _terminator(active_workers, PSPromotionManager::vm_thread_promotion_manager()->stack_array_depth()) {
    if (!_is_old_gen_empty) {
      PSCardTable* card_table = ParallelScavengeHeap::heap()->card_table();
      card_table->pre_scavenge(active_workers);
    }
  }

  virtual void work(uint worker_id) {
    assert(worker_id < _active_workers, "Sanity");
    ResourceMark rm;

    if (!_is_old_gen_empty) {
      // There are only old-to-young pointers if there are objects
      // in the old gen.
      {
        PSPromotionManager* pm = PSPromotionManager::gc_thread_promotion_manager(worker_id);
        PSCardTable* card_table = ParallelScavengeHeap::heap()->card_table();

        // The top of the old gen changes during scavenge when objects are promoted.
        card_table->scavenge_contents_parallel(_old_gen->start_array(),
                                               _old_gen->object_space()->bottom(),
                                               _gen_top,
                                               pm,
                                               worker_id,
                                               _active_workers);

        // Do the real work
        pm->drain_stacks(false);
      }
    }

    for (uint root_type = 0; _subtasks.try_claim_task(root_type); /* empty */ ) {
      scavenge_roots_work(static_cast<ParallelRootType::Value>(root_type), worker_id);
    }

    PSThreadRootsTaskClosure closure(worker_id);
    Threads::possibly_parallel_threads_do(_active_workers > 1 /* is_par */, &closure);

    // Scavenge OopStorages
    {
      PSPromotionManager* pm = PSPromotionManager::gc_thread_promotion_manager(worker_id);
      PSScavengeRootsClosure closure(pm);
      _oop_storage_strong_par_state.oops_do(&closure);
      // Do the real work
      pm->drain_stacks(false);
    }

    // If active_workers can exceed 1, add a steal_work().
    // PSPromotionManager::drain_stacks_depth() does not fully drain its
    // stacks and expects a steal_work() to complete the draining if
    // ParallelGCThreads is > 1.

    if (_active_workers > 1) {
      steal_work(_terminator, worker_id);
    }
  }
};

bool PSScavenge::invoke(bool clear_soft_refs) {
  assert(SafepointSynchronize::is_at_safepoint(), "should be at safepoint");
  assert(Thread::current() == (Thread*)VMThread::vm_thread(), "should be in vm thread");

  // Check for potential problems.
  if (!should_attempt_scavenge()) {
    log_info(gc, ergo)("Young-gc might fail so skipping");
    return false;
  }

  IsSTWGCActiveMark mark;

  _gc_timer.register_gc_start();

  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
  GCCause::Cause gc_cause = heap->gc_cause();

  SvcGCMarker sgcm(SvcGCMarker::MINOR);
  GCIdMark gc_id_mark;
  _gc_tracer.report_gc_start(heap->gc_cause(), _gc_timer.gc_start());

  bool promotion_failure_occurred = false;

  PSYoungGen* young_gen = heap->young_gen();
  PSOldGen* old_gen = heap->old_gen();
  PSAdaptiveSizePolicy* size_policy = heap->size_policy();

  assert(young_gen->to_space()->is_empty(),
         "Attempt to scavenge with live objects in to_space");

  heap->increment_total_collections();

  // Gather the feedback data for eden occupancy.
  young_gen->eden_space()->accumulate_statistics();

  heap->print_before_gc();
  heap->trace_heap_before_gc(&_gc_tracer);

  assert(!NeverTenure || _tenuring_threshold == markWord::max_age + 1, "Sanity");
  assert(!AlwaysTenure || _tenuring_threshold == 0, "Sanity");

  // Fill in TLABs
  heap->ensure_parsability(true);  // retire TLABs

  if (VerifyBeforeGC && heap->total_collections() >= VerifyGCStartAt) {
    Universe::verify("Before GC");
  }

  {
    ResourceMark rm;

    GCTraceCPUTime tcpu(&_gc_tracer);
    GCTraceTime(Info, gc) tm("Pause Young", nullptr, gc_cause, true);
    TraceCollectorStats tcs(counters());
    TraceMemoryManagerStats tms(heap->young_gc_manager(), gc_cause, "end of minor GC");

    if (log_is_enabled(Debug, gc, heap, exit)) {
      accumulated_time()->start();
    }

    // Let the size policy know we're starting
    size_policy->minor_collection_begin();

#if COMPILER2_OR_JVMCI
    DerivedPointerTable::clear();
#endif

    reference_processor()->start_discovery(clear_soft_refs);

    const PreGenGCValues pre_gc_values = heap->get_pre_gc_values();

    // Reset our survivor overflow.
    set_survivor_overflow(false);

    const uint active_workers =
      WorkerPolicy::calc_active_workers(ParallelScavengeHeap::heap()->workers().max_workers(),
                                        ParallelScavengeHeap::heap()->workers().active_workers(),
                                        Threads::number_of_non_daemon_threads());
    ParallelScavengeHeap::heap()->workers().set_active_workers(active_workers);

    PSPromotionManager::pre_scavenge();

    {
      GCTraceTime(Debug, gc, phases) tm("Scavenge", &_gc_timer);

      ScavengeRootsTask task(old_gen, active_workers);
      ParallelScavengeHeap::heap()->workers().run_task(&task);
    }

    // Process reference objects discovered during scavenge
    {
      GCTraceTime(Debug, gc, phases) tm("Reference Processing", &_gc_timer);

      ReferenceProcessorStats stats;
      ReferenceProcessorPhaseTimes pt(&_gc_timer, reference_processor()->max_num_queues());

      ParallelScavengeRefProcProxyTask task(reference_processor()->max_num_queues());
      stats = reference_processor()->process_discovered_references(task, &ParallelScavengeHeap::heap()->workers(), pt);

      _gc_tracer.report_gc_reference_stats(stats);
      pt.print_all_references();
    }

    {
      GCTraceTime(Debug, gc, phases) tm("Weak Processing", &_gc_timer);
      PSAdjustWeakRootsClosure root_closure;
      WeakProcessor::weak_oops_do(&heap->workers(), &_is_alive_closure, &root_closure, 1);
    }

    // Finally, flush the promotion_manager's labs, and deallocate its stacks.
    promotion_failure_occurred = PSPromotionManager::post_scavenge(_gc_tracer);
    if (promotion_failure_occurred) {
      clean_up_failed_promotion();
      log_info(gc, promotion)("Promotion failed");
    }

    _gc_tracer.report_tenuring_threshold(tenuring_threshold());

    // This is an underestimate, since it excludes time on auto-resizing. The
    // most expensive part in auto-resizing is commit/uncommit OS API calls.
    size_policy->minor_collection_end(young_gen->eden_space()->capacity_in_bytes());

    if (!promotion_failure_occurred) {
      // Swap the survivor spaces.
      young_gen->eden_space()->clear(SpaceDecorator::Mangle);
      young_gen->from_space()->clear(SpaceDecorator::Mangle);
      young_gen->swap_spaces();

      size_t survived = young_gen->from_space()->used_in_bytes();
      assert(old_gen->used_in_bytes() >= pre_gc_values.old_gen_used(), "inv");
      size_t promoted = old_gen->used_in_bytes() - pre_gc_values.old_gen_used();
      size_policy->update_averages(_survivor_overflow, survived, promoted);
      size_policy->sample_old_gen_used_bytes(old_gen->used_in_bytes());

      if (UseAdaptiveSizePolicy) {
        _tenuring_threshold = size_policy->compute_tenuring_threshold(_survivor_overflow,
                                                                      _tenuring_threshold);

        log_debug(gc, age)("New threshold %u (max threshold %u)", _tenuring_threshold, MaxTenuringThreshold);

        if (young_gen->is_from_to_layout()) {
          size_policy->print_stats(_survivor_overflow);
          heap->resize_after_young_gc(_survivor_overflow);
        }

        if (UsePerfData) {
          GCPolicyCounters* counters = ParallelScavengeHeap::gc_policy_counters();
          counters->tenuring_threshold()->set_value(_tenuring_threshold);
          counters->desired_survivor_size()->set_value(young_gen->from_space()->capacity_in_bytes());
        }

        {
          // In case the counter overflows
          uint num_minor_gcs = heap->total_collections() > heap->total_full_collections()
                                 ? heap->total_collections() - heap->total_full_collections()
                                 : 1;
          size_policy->decay_supplemental_growth(num_minor_gcs);
        }
      }

      // Update the structure of the eden. With NUMA-eden CPU hotplugging or offlining can
      // cause the change of the heap layout. Make sure eden is reshaped if that's the case.
      // Also update() will case adaptive NUMA chunk resizing.
      assert(young_gen->eden_space()->is_empty(), "eden space should be empty now");
      young_gen->eden_space()->update();

      heap->resize_all_tlabs();

      assert(young_gen->to_space()->is_empty(), "to space should be empty now");

      heap->gc_epilogue(false);
    }

#if COMPILER2_OR_JVMCI
    DerivedPointerTable::update_pointers();
#endif

    size_policy->record_gc_pause_end_instant();

    if (log_is_enabled(Debug, gc, heap, exit)) {
      accumulated_time()->stop();
    }

    heap->print_heap_change(pre_gc_values);

    // Track memory usage and detect low memory
    MemoryService::track_memory_usage();
    heap->update_counters();
  }

  if (VerifyAfterGC && heap->total_collections() >= VerifyGCStartAt) {
    Universe::verify("After GC");
  }

  heap->print_after_gc();
  heap->trace_heap_after_gc(&_gc_tracer);

  _gc_timer.register_gc_end();

  _gc_tracer.report_gc_end(_gc_timer.gc_end(), _gc_timer.time_partitions());

  return !promotion_failure_occurred;
}

void PSScavenge::clean_up_failed_promotion() {
  PSPromotionManager::restore_preserved_marks();

  // Reset the PromotionFailureALot counters.
  NOT_PRODUCT(ParallelScavengeHeap::heap()->reset_promotion_should_fail();)
}

bool PSScavenge::should_attempt_scavenge() {
  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();

  PSYoungGen* young_gen = heap->young_gen();
  PSOldGen* old_gen = heap->old_gen();

  if (!young_gen->to_space()->is_empty()) {
    log_debug(gc, ergo)("To-space is not empty; should run full-gc instead.");
    return false;
  }

  // Test to see if the scavenge will likely fail.
  PSAdaptiveSizePolicy* policy = heap->size_policy();

  size_t avg_promoted = (size_t) policy->padded_average_promoted_in_bytes();
  size_t promotion_estimate = MIN2(avg_promoted, young_gen->used_in_bytes());
  // Total free size after possible old gen expansion
  size_t free_in_old_gen = old_gen->max_gen_size() - old_gen->used_in_bytes();
  bool result = promotion_estimate < free_in_old_gen;

  log_trace(gc, ergo)("%s scavenge: average_promoted %zu padded_average_promoted %zu free in old gen %zu",
                result ? "Do" : "Skip", (size_t) policy->average_promoted_in_bytes(),
                (size_t) policy->padded_average_promoted_in_bytes(),
                free_in_old_gen);

  return result;
}

// Adaptive size policy support.
void PSScavenge::set_young_generation_boundary(HeapWord* v) {
  _young_generation_boundary = v;
  if (UseCompressedOops) {
    _young_generation_boundary_compressed = (uintptr_t)CompressedOops::encode(cast_to_oop(v));
  }
}

void PSScavenge::initialize() {
  // Arguments must have been parsed

  if (AlwaysTenure || NeverTenure) {
    assert(MaxTenuringThreshold == 0 || MaxTenuringThreshold == markWord::max_age + 1,
           "MaxTenuringThreshold should be 0 or markWord::max_age + 1, but is %d", (int) MaxTenuringThreshold);
    _tenuring_threshold = MaxTenuringThreshold;
  } else {
    // We want to smooth out our startup times for the AdaptiveSizePolicy
    _tenuring_threshold = (UseAdaptiveSizePolicy) ? InitialTenuringThreshold :
                                                    MaxTenuringThreshold;
  }

  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
  PSYoungGen* young_gen = heap->young_gen();
  PSOldGen* old_gen = heap->old_gen();

  // Set boundary between young_gen and old_gen
  assert(old_gen->reserved().end() == young_gen->reserved().start(),
         "old above young");
  set_young_generation_boundary(young_gen->reserved().start());

  // Initialize ref handling object for scavenging.
  _span_based_discoverer.set_span(young_gen->reserved());
  _ref_processor =
    new ReferenceProcessor(&_span_based_discoverer,
                           ParallelGCThreads,          // mt processing degree
                           ParallelGCThreads,          // mt discovery degree
                           false,                      // concurrent_discovery
                           &_is_alive_closure);        // header provides liveness info

  // Cache the cardtable
  _card_table = heap->card_table();

  _counters = new CollectorCounters("Parallel young collection pauses", 0);
}
