/*
 * Copyright (c) 2002, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/stringTable.hpp"
#include "code/codeCache.hpp"
#include "gc/parallel/cardTableExtension.hpp"
#include "gc/parallel/gcTaskManager.hpp"
#include "gc/parallel/parallelScavengeHeap.hpp"
#include "gc/parallel/psAdaptiveSizePolicy.hpp"
#include "gc/parallel/psMarkSweep.hpp"
#include "gc/parallel/psParallelCompact.hpp"
#include "gc/parallel/psScavenge.inline.hpp"
#include "gc/parallel/psTasks.hpp"
#include "gc/shared/collectorPolicy.hpp"
#include "gc/shared/gcCause.hpp"
#include "gc/shared/gcHeapSummary.hpp"
#include "gc/shared/gcId.hpp"
#include "gc/shared/gcLocker.inline.hpp"
#include "gc/shared/gcTimer.hpp"
#include "gc/shared/gcTrace.hpp"
#include "gc/shared/gcTraceTime.hpp"
#include "gc/shared/isGCActiveMark.hpp"
#include "gc/shared/referencePolicy.hpp"
#include "gc/shared/referenceProcessor.hpp"
#include "gc/shared/spaceDecorator.hpp"
#include "memory/resourceArea.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/biasedLocking.hpp"
#include "runtime/fprofiler.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/threadCritical.hpp"
#include "runtime/vmThread.hpp"
#include "runtime/vm_operations.hpp"
#include "services/memoryService.hpp"
#include "utilities/stack.inline.hpp"

HeapWord*                  PSScavenge::_to_space_top_before_gc = NULL;
int                        PSScavenge::_consecutive_skipped_scavenges = 0;
ReferenceProcessor*        PSScavenge::_ref_processor = NULL;
CardTableExtension*        PSScavenge::_card_table = NULL;
bool                       PSScavenge::_survivor_overflow = false;
uint                       PSScavenge::_tenuring_threshold = 0;
HeapWord*                  PSScavenge::_young_generation_boundary = NULL;
uintptr_t                  PSScavenge::_young_generation_boundary_compressed = 0;
elapsedTimer               PSScavenge::_accumulated_time;
STWGCTimer                 PSScavenge::_gc_timer;
ParallelScavengeTracer     PSScavenge::_gc_tracer;
Stack<markOop, mtGC>       PSScavenge::_preserved_mark_stack;
Stack<oop, mtGC>           PSScavenge::_preserved_oop_stack;
CollectorCounters*         PSScavenge::_counters = NULL;

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

    assert(_promotion_manager != NULL, "Sanity");
  }

  template <class T> void do_oop_work(T* p) {
    assert (!oopDesc::is_null(*p), "expected non-null ref");
    assert ((oopDesc::load_decode_heap_oop_not_null(p))->is_oop(),
            "expected an oop while scanning weak refs");

    // Weak refs may be visited more than once.
    if (PSScavenge::should_scavenge(p, _to_space)) {
      _promotion_manager->copy_and_push_safe_barrier<T, /*promote_immediately=*/false>(p);
    }
  }
  virtual void do_oop(oop* p)       { PSKeepAliveClosure::do_oop_work(p); }
  virtual void do_oop(narrowOop* p) { PSKeepAliveClosure::do_oop_work(p); }
};

class PSEvacuateFollowersClosure: public VoidClosure {
 private:
  PSPromotionManager* _promotion_manager;
 public:
  PSEvacuateFollowersClosure(PSPromotionManager* pm) : _promotion_manager(pm) {}

  virtual void do_void() {
    assert(_promotion_manager != NULL, "Sanity");
    _promotion_manager->drain_stacks(true);
    guarantee(_promotion_manager->stacks_empty(),
              "stacks should be empty at this point");
  }
};

class PSPromotionFailedClosure : public ObjectClosure {
  virtual void do_object(oop obj) {
    if (obj->is_forwarded()) {
      obj->init_mark();
    }
  }
};

class PSRefProcTaskProxy: public GCTask {
  typedef AbstractRefProcTaskExecutor::ProcessTask ProcessTask;
  ProcessTask & _rp_task;
  uint          _work_id;
public:
  PSRefProcTaskProxy(ProcessTask & rp_task, uint work_id)
    : _rp_task(rp_task),
      _work_id(work_id)
  { }

private:
  virtual char* name() { return (char *)"Process referents by policy in parallel"; }
  virtual void do_it(GCTaskManager* manager, uint which);
};

void PSRefProcTaskProxy::do_it(GCTaskManager* manager, uint which)
{
  PSPromotionManager* promotion_manager =
    PSPromotionManager::gc_thread_promotion_manager(which);
  assert(promotion_manager != NULL, "sanity check");
  PSKeepAliveClosure keep_alive(promotion_manager);
  PSEvacuateFollowersClosure evac_followers(promotion_manager);
  PSIsAliveClosure is_alive;
  _rp_task.work(_work_id, is_alive, keep_alive, evac_followers);
}

class PSRefEnqueueTaskProxy: public GCTask {
  typedef AbstractRefProcTaskExecutor::EnqueueTask EnqueueTask;
  EnqueueTask& _enq_task;
  uint         _work_id;

public:
  PSRefEnqueueTaskProxy(EnqueueTask& enq_task, uint work_id)
    : _enq_task(enq_task),
      _work_id(work_id)
  { }

  virtual char* name() { return (char *)"Enqueue reference objects in parallel"; }
  virtual void do_it(GCTaskManager* manager, uint which)
  {
    _enq_task.work(_work_id);
  }
};

class PSRefProcTaskExecutor: public AbstractRefProcTaskExecutor {
  virtual void execute(ProcessTask& task);
  virtual void execute(EnqueueTask& task);
};

void PSRefProcTaskExecutor::execute(ProcessTask& task)
{
  GCTaskQueue* q = GCTaskQueue::create();
  GCTaskManager* manager = ParallelScavengeHeap::gc_task_manager();
  for(uint i=0; i < manager->active_workers(); i++) {
    q->enqueue(new PSRefProcTaskProxy(task, i));
  }
  ParallelTaskTerminator terminator(manager->active_workers(),
                 (TaskQueueSetSuper*) PSPromotionManager::stack_array_depth());
  if (task.marks_oops_alive() && manager->active_workers() > 1) {
    for (uint j = 0; j < manager->active_workers(); j++) {
      q->enqueue(new StealTask(&terminator));
    }
  }
  manager->execute_and_wait(q);
}


void PSRefProcTaskExecutor::execute(EnqueueTask& task)
{
  GCTaskQueue* q = GCTaskQueue::create();
  GCTaskManager* manager = ParallelScavengeHeap::gc_task_manager();
  for(uint i=0; i < manager->active_workers(); i++) {
    q->enqueue(new PSRefEnqueueTaskProxy(task, i));
  }
  manager->execute_and_wait(q);
}

// This method contains all heap specific policy for invoking scavenge.
// PSScavenge::invoke_no_policy() will do nothing but attempt to
// scavenge. It will not clean up after failed promotions, bail out if
// we've exceeded policy time limits, or any other special behavior.
// All such policy should be placed here.
//
// Note that this method should only be called from the vm_thread while
// at a safepoint!
bool PSScavenge::invoke() {
  assert(SafepointSynchronize::is_at_safepoint(), "should be at safepoint");
  assert(Thread::current() == (Thread*)VMThread::vm_thread(), "should be in vm thread");
  assert(!ParallelScavengeHeap::heap()->is_gc_active(), "not reentrant");

  ParallelScavengeHeap* const heap = ParallelScavengeHeap::heap();
  PSAdaptiveSizePolicy* policy = heap->size_policy();
  IsGCActiveMark mark;

  const bool scavenge_done = PSScavenge::invoke_no_policy();
  const bool need_full_gc = !scavenge_done ||
    policy->should_full_GC(heap->old_gen()->free_in_bytes());
  bool full_gc_done = false;

  if (UsePerfData) {
    PSGCAdaptivePolicyCounters* const counters = heap->gc_policy_counters();
    const int ffs_val = need_full_gc ? full_follows_scavenge : not_skipped;
    counters->update_full_follows_scavenge(ffs_val);
  }

  if (need_full_gc) {
    GCCauseSetter gccs(heap, GCCause::_adaptive_size_policy);
    CollectorPolicy* cp = heap->collector_policy();
    const bool clear_all_softrefs = cp->should_clear_all_soft_refs();

    if (UseParallelOldGC) {
      full_gc_done = PSParallelCompact::invoke_no_policy(clear_all_softrefs);
    } else {
      full_gc_done = PSMarkSweep::invoke_no_policy(clear_all_softrefs);
    }
  }

  return full_gc_done;
}

// This method contains no policy. You should probably
// be calling invoke() instead.
bool PSScavenge::invoke_no_policy() {
  assert(SafepointSynchronize::is_at_safepoint(), "should be at safepoint");
  assert(Thread::current() == (Thread*)VMThread::vm_thread(), "should be in vm thread");

  assert(_preserved_mark_stack.is_empty(), "should be empty");
  assert(_preserved_oop_stack.is_empty(), "should be empty");

  _gc_timer.register_gc_start();

  TimeStamp scavenge_entry;
  TimeStamp scavenge_midpoint;
  TimeStamp scavenge_exit;

  scavenge_entry.update();

  if (GC_locker::check_active_before_gc()) {
    return false;
  }

  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
  GCCause::Cause gc_cause = heap->gc_cause();

  // Check for potential problems.
  if (!should_attempt_scavenge()) {
    return false;
  }

  GCIdMark gc_id_mark;
  _gc_tracer.report_gc_start(heap->gc_cause(), _gc_timer.gc_start());

  bool promotion_failure_occurred = false;

  PSYoungGen* young_gen = heap->young_gen();
  PSOldGen* old_gen = heap->old_gen();
  PSAdaptiveSizePolicy* size_policy = heap->size_policy();

  heap->increment_total_collections();

  AdaptiveSizePolicyOutput(size_policy, heap->total_collections());

  if (AdaptiveSizePolicy::should_update_eden_stats(gc_cause)) {
    // Gather the feedback data for eden occupancy.
    young_gen->eden_space()->accumulate_statistics();
  }

  if (ZapUnusedHeapArea) {
    // Save information needed to minimize mangling
    heap->record_gen_tops_before_GC();
  }

  heap->print_heap_before_gc();
  heap->trace_heap_before_gc(&_gc_tracer);

  assert(!NeverTenure || _tenuring_threshold == markOopDesc::max_age + 1, "Sanity");
  assert(!AlwaysTenure || _tenuring_threshold == 0, "Sanity");

  size_t prev_used = heap->used();

  // Fill in TLABs
  heap->accumulate_statistics_all_tlabs();
  heap->ensure_parsability(true);  // retire TLABs

  if (VerifyBeforeGC && heap->total_collections() >= VerifyGCStartAt) {
    HandleMark hm;  // Discard invalid handles created during verification
    Universe::verify(" VerifyBeforeGC:");
  }

  {
    ResourceMark rm;
    HandleMark hm;

    TraceCPUTime tcpu(PrintGCDetails, true, gclog_or_tty);
    GCTraceTime t1(GCCauseString("GC", gc_cause), PrintGC, !PrintGCDetails, NULL);
    TraceCollectorStats tcs(counters());
    TraceMemoryManagerStats tms(false /* not full GC */,gc_cause);

    if (TraceYoungGenTime) accumulated_time()->start();

    // Let the size policy know we're starting
    size_policy->minor_collection_begin();

    // Verify the object start arrays.
    if (VerifyObjectStartArray &&
        VerifyBeforeGC) {
      old_gen->verify_object_start_array();
    }

    // Verify no unmarked old->young roots
    if (VerifyRememberedSets) {
      CardTableExtension::verify_all_young_refs_imprecise();
    }

    if (!ScavengeWithObjectsInToSpace) {
      assert(young_gen->to_space()->is_empty(),
             "Attempt to scavenge with live objects in to_space");
      young_gen->to_space()->clear(SpaceDecorator::Mangle);
    } else if (ZapUnusedHeapArea) {
      young_gen->to_space()->mangle_unused_area();
    }
    save_to_space_top_before_gc();

    COMPILER2_PRESENT(DerivedPointerTable::clear());

    reference_processor()->enable_discovery();
    reference_processor()->setup_policy(false);

    // We track how much was promoted to the next generation for
    // the AdaptiveSizePolicy.
    size_t old_gen_used_before = old_gen->used_in_bytes();

    // For PrintGCDetails
    size_t young_gen_used_before = young_gen->used_in_bytes();

    // Reset our survivor overflow.
    set_survivor_overflow(false);

    // We need to save the old top values before
    // creating the promotion_manager. We pass the top
    // values to the card_table, to prevent it from
    // straying into the promotion labs.
    HeapWord* old_top = old_gen->object_space()->top();

    // Release all previously held resources
    gc_task_manager()->release_all_resources();

    // Set the number of GC threads to be used in this collection
    gc_task_manager()->set_active_gang();
    gc_task_manager()->task_idle_workers();
    // Get the active number of workers here and use that value
    // throughout the methods.
    uint active_workers = gc_task_manager()->active_workers();

    PSPromotionManager::pre_scavenge();

    // We'll use the promotion manager again later.
    PSPromotionManager* promotion_manager = PSPromotionManager::vm_thread_promotion_manager();
    {
      GCTraceTime tm("Scavenge", false, false, &_gc_timer);
      ParallelScavengeHeap::ParStrongRootsScope psrs;

      GCTaskQueue* q = GCTaskQueue::create();

      if (!old_gen->object_space()->is_empty()) {
        // There are only old-to-young pointers if there are objects
        // in the old gen.
        uint stripe_total = active_workers;
        for(uint i=0; i < stripe_total; i++) {
          q->enqueue(new OldToYoungRootsTask(old_gen, old_top, i, stripe_total));
        }
      }

      q->enqueue(new ScavengeRootsTask(ScavengeRootsTask::universe));
      q->enqueue(new ScavengeRootsTask(ScavengeRootsTask::jni_handles));
      // We scan the thread roots in parallel
      Threads::create_thread_roots_tasks(q);
      q->enqueue(new ScavengeRootsTask(ScavengeRootsTask::object_synchronizer));
      q->enqueue(new ScavengeRootsTask(ScavengeRootsTask::flat_profiler));
      q->enqueue(new ScavengeRootsTask(ScavengeRootsTask::management));
      q->enqueue(new ScavengeRootsTask(ScavengeRootsTask::system_dictionary));
      q->enqueue(new ScavengeRootsTask(ScavengeRootsTask::class_loader_data));
      q->enqueue(new ScavengeRootsTask(ScavengeRootsTask::jvmti));
      q->enqueue(new ScavengeRootsTask(ScavengeRootsTask::code_cache));

      ParallelTaskTerminator terminator(
        active_workers,
                  (TaskQueueSetSuper*) promotion_manager->stack_array_depth());
      if (active_workers > 1) {
        for (uint j = 0; j < active_workers; j++) {
          q->enqueue(new StealTask(&terminator));
        }
      }

      gc_task_manager()->execute_and_wait(q);
    }

    scavenge_midpoint.update();

    // Process reference objects discovered during scavenge
    {
      GCTraceTime tm("References", false, false, &_gc_timer);

      reference_processor()->setup_policy(false); // not always_clear
      reference_processor()->set_active_mt_degree(active_workers);
      PSKeepAliveClosure keep_alive(promotion_manager);
      PSEvacuateFollowersClosure evac_followers(promotion_manager);
      ReferenceProcessorStats stats;
      if (reference_processor()->processing_is_mt()) {
        PSRefProcTaskExecutor task_executor;
        stats = reference_processor()->process_discovered_references(
          &_is_alive_closure, &keep_alive, &evac_followers, &task_executor,
          &_gc_timer);
      } else {
        stats = reference_processor()->process_discovered_references(
          &_is_alive_closure, &keep_alive, &evac_followers, NULL, &_gc_timer);
      }

      _gc_tracer.report_gc_reference_stats(stats);

      // Enqueue reference objects discovered during scavenge.
      if (reference_processor()->processing_is_mt()) {
        PSRefProcTaskExecutor task_executor;
        reference_processor()->enqueue_discovered_references(&task_executor);
      } else {
        reference_processor()->enqueue_discovered_references(NULL);
      }
    }

    {
      GCTraceTime tm("StringTable", false, false, &_gc_timer);
      // Unlink any dead interned Strings and process the remaining live ones.
      PSScavengeRootsClosure root_closure(promotion_manager);
      StringTable::unlink_or_oops_do(&_is_alive_closure, &root_closure);
    }

    // Finally, flush the promotion_manager's labs, and deallocate its stacks.
    promotion_failure_occurred = PSPromotionManager::post_scavenge(_gc_tracer);
    if (promotion_failure_occurred) {
      clean_up_failed_promotion();
      if (PrintGC) {
        gclog_or_tty->print("--");
      }
    }

    // Let the size policy know we're done.  Note that we count promotion
    // failure cleanup time as part of the collection (otherwise, we're
    // implicitly saying it's mutator time).
    size_policy->minor_collection_end(gc_cause);

    if (!promotion_failure_occurred) {
      // Swap the survivor spaces.
      young_gen->eden_space()->clear(SpaceDecorator::Mangle);
      young_gen->from_space()->clear(SpaceDecorator::Mangle);
      young_gen->swap_spaces();

      size_t survived = young_gen->from_space()->used_in_bytes();
      size_t promoted = old_gen->used_in_bytes() - old_gen_used_before;
      size_policy->update_averages(_survivor_overflow, survived, promoted);

      // A successful scavenge should restart the GC time limit count which is
      // for full GC's.
      size_policy->reset_gc_overhead_limit_count();
      if (UseAdaptiveSizePolicy) {
        // Calculate the new survivor size and tenuring threshold

        if (PrintAdaptiveSizePolicy) {
          gclog_or_tty->print("AdaptiveSizeStart: ");
          gclog_or_tty->stamp();
          gclog_or_tty->print_cr(" collection: %d ",
                         heap->total_collections());

          if (Verbose) {
            gclog_or_tty->print("old_gen_capacity: " SIZE_FORMAT
              " young_gen_capacity: " SIZE_FORMAT,
              old_gen->capacity_in_bytes(), young_gen->capacity_in_bytes());
          }
        }


        if (UsePerfData) {
          PSGCAdaptivePolicyCounters* counters = heap->gc_policy_counters();
          counters->update_old_eden_size(
            size_policy->calculated_eden_size_in_bytes());
          counters->update_old_promo_size(
            size_policy->calculated_promo_size_in_bytes());
          counters->update_old_capacity(old_gen->capacity_in_bytes());
          counters->update_young_capacity(young_gen->capacity_in_bytes());
          counters->update_survived(survived);
          counters->update_promoted(promoted);
          counters->update_survivor_overflowed(_survivor_overflow);
        }

        size_t max_young_size = young_gen->max_size();

        // Deciding a free ratio in the young generation is tricky, so if
        // MinHeapFreeRatio or MaxHeapFreeRatio are in use (implicating
        // that the old generation size may have been limited because of them) we
        // should then limit our young generation size using NewRatio to have it
        // follow the old generation size.
        if (MinHeapFreeRatio != 0 || MaxHeapFreeRatio != 100) {
          max_young_size = MIN2(old_gen->capacity_in_bytes() / NewRatio, young_gen->max_size());
        }

        size_t survivor_limit =
          size_policy->max_survivor_size(max_young_size);
        _tenuring_threshold =
          size_policy->compute_survivor_space_size_and_threshold(
                                                           _survivor_overflow,
                                                           _tenuring_threshold,
                                                           survivor_limit);

       if (PrintTenuringDistribution) {
         gclog_or_tty->cr();
         gclog_or_tty->print_cr("Desired survivor size " SIZE_FORMAT " bytes, new threshold %u"
                                " (max threshold " UINTX_FORMAT ")",
                                size_policy->calculated_survivor_size_in_bytes(),
                                _tenuring_threshold, MaxTenuringThreshold);
       }

        if (UsePerfData) {
          PSGCAdaptivePolicyCounters* counters = heap->gc_policy_counters();
          counters->update_tenuring_threshold(_tenuring_threshold);
          counters->update_survivor_size_counters();
        }

        // Do call at minor collections?
        // Don't check if the size_policy is ready at this
        // level.  Let the size_policy check that internally.
        if (UseAdaptiveGenerationSizePolicyAtMinorCollection &&
            (AdaptiveSizePolicy::should_update_eden_stats(gc_cause))) {
          // Calculate optimal free space amounts
          assert(young_gen->max_size() >
            young_gen->from_space()->capacity_in_bytes() +
            young_gen->to_space()->capacity_in_bytes(),
            "Sizes of space in young gen are out-of-bounds");

          size_t young_live = young_gen->used_in_bytes();
          size_t eden_live = young_gen->eden_space()->used_in_bytes();
          size_t cur_eden = young_gen->eden_space()->capacity_in_bytes();
          size_t max_old_gen_size = old_gen->max_gen_size();
          size_t max_eden_size = max_young_size -
            young_gen->from_space()->capacity_in_bytes() -
            young_gen->to_space()->capacity_in_bytes();

          // Used for diagnostics
          size_policy->clear_generation_free_space_flags();

          size_policy->compute_eden_space_size(young_live,
                                               eden_live,
                                               cur_eden,
                                               max_eden_size,
                                               false /* not full gc*/);

          size_policy->check_gc_overhead_limit(young_live,
                                               eden_live,
                                               max_old_gen_size,
                                               max_eden_size,
                                               false /* not full gc*/,
                                               gc_cause,
                                               heap->collector_policy());

          size_policy->decay_supplemental_growth(false /* not full gc*/);
        }
        // Resize the young generation at every collection
        // even if new sizes have not been calculated.  This is
        // to allow resizes that may have been inhibited by the
        // relative location of the "to" and "from" spaces.

        // Resizing the old gen at young collections can cause increases
        // that don't feed back to the generation sizing policy until
        // a full collection.  Don't resize the old gen here.

        heap->resize_young_gen(size_policy->calculated_eden_size_in_bytes(),
                        size_policy->calculated_survivor_size_in_bytes());

        if (PrintAdaptiveSizePolicy) {
          gclog_or_tty->print_cr("AdaptiveSizeStop: collection: %d ",
                         heap->total_collections());
        }
      }

      // Update the structure of the eden. With NUMA-eden CPU hotplugging or offlining can
      // cause the change of the heap layout. Make sure eden is reshaped if that's the case.
      // Also update() will case adaptive NUMA chunk resizing.
      assert(young_gen->eden_space()->is_empty(), "eden space should be empty now");
      young_gen->eden_space()->update();

      heap->gc_policy_counters()->update_counters();

      heap->resize_all_tlabs();

      assert(young_gen->to_space()->is_empty(), "to space should be empty now");
    }

    COMPILER2_PRESENT(DerivedPointerTable::update_pointers());

    NOT_PRODUCT(reference_processor()->verify_no_references_recorded());

    {
      GCTraceTime tm("Prune Scavenge Root Methods", false, false, &_gc_timer);

      CodeCache::prune_scavenge_root_nmethods();
    }

    // Re-verify object start arrays
    if (VerifyObjectStartArray &&
        VerifyAfterGC) {
      old_gen->verify_object_start_array();
    }

    // Verify all old -> young cards are now precise
    if (VerifyRememberedSets) {
      // Precise verification will give false positives. Until this is fixed,
      // use imprecise verification.
      // CardTableExtension::verify_all_young_refs_precise();
      CardTableExtension::verify_all_young_refs_imprecise();
    }

    if (TraceYoungGenTime) accumulated_time()->stop();

    if (PrintGC) {
      if (PrintGCDetails) {
        // Don't print a GC timestamp here.  This is after the GC so
        // would be confusing.
        young_gen->print_used_change(young_gen_used_before);
      }
      heap->print_heap_change(prev_used);
    }

    // Track memory usage and detect low memory
    MemoryService::track_memory_usage();
    heap->update_counters();

    gc_task_manager()->release_idle_workers();
  }

  if (VerifyAfterGC && heap->total_collections() >= VerifyGCStartAt) {
    HandleMark hm;  // Discard invalid handles created during verification
    Universe::verify(" VerifyAfterGC:");
  }

  heap->print_heap_after_gc();
  heap->trace_heap_after_gc(&_gc_tracer);
  _gc_tracer.report_tenuring_threshold(tenuring_threshold());

  if (ZapUnusedHeapArea) {
    young_gen->eden_space()->check_mangled_unused_area_complete();
    young_gen->from_space()->check_mangled_unused_area_complete();
    young_gen->to_space()->check_mangled_unused_area_complete();
  }

  scavenge_exit.update();

  if (PrintGCTaskTimeStamps) {
    tty->print_cr("VM-Thread " JLONG_FORMAT " " JLONG_FORMAT " " JLONG_FORMAT,
                  scavenge_entry.ticks(), scavenge_midpoint.ticks(),
                  scavenge_exit.ticks());
    gc_task_manager()->print_task_time_stamps();
  }

#ifdef TRACESPINNING
  ParallelTaskTerminator::print_termination_counts();
#endif


  _gc_timer.register_gc_end();

  _gc_tracer.report_gc_end(_gc_timer.gc_end(), _gc_timer.time_partitions());

  return !promotion_failure_occurred;
}

// This method iterates over all objects in the young generation,
// unforwarding markOops. It then restores any preserved mark oops,
// and clears the _preserved_mark_stack.
void PSScavenge::clean_up_failed_promotion() {
  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
  PSYoungGen* young_gen = heap->young_gen();

  {
    ResourceMark rm;

    // Unforward all pointers in the young gen.
    PSPromotionFailedClosure unforward_closure;
    young_gen->object_iterate(&unforward_closure);

    if (PrintGC && Verbose) {
      gclog_or_tty->print_cr("Restoring " SIZE_FORMAT " marks", _preserved_oop_stack.size());
    }

    // Restore any saved marks.
    while (!_preserved_oop_stack.is_empty()) {
      oop obj      = _preserved_oop_stack.pop();
      markOop mark = _preserved_mark_stack.pop();
      obj->set_mark(mark);
    }

    // Clear the preserved mark and oop stack caches.
    _preserved_mark_stack.clear(true);
    _preserved_oop_stack.clear(true);
  }

  // Reset the PromotionFailureALot counters.
  NOT_PRODUCT(heap->reset_promotion_should_fail();)
}

// This method is called whenever an attempt to promote an object
// fails. Some markOops will need preservation, some will not. Note
// that the entire eden is traversed after a failed promotion, with
// all forwarded headers replaced by the default markOop. This means
// it is not necessary to preserve most markOops.
void PSScavenge::oop_promotion_failed(oop obj, markOop obj_mark) {
  if (obj_mark->must_be_preserved_for_promotion_failure(obj)) {
    // Should use per-worker private stacks here rather than
    // locking a common pair of stacks.
    ThreadCritical tc;
    _preserved_oop_stack.push(obj);
    _preserved_mark_stack.push(obj_mark);
  }
}

bool PSScavenge::should_attempt_scavenge() {
  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
  PSGCAdaptivePolicyCounters* counters = heap->gc_policy_counters();

  if (UsePerfData) {
    counters->update_scavenge_skipped(not_skipped);
  }

  PSYoungGen* young_gen = heap->young_gen();
  PSOldGen* old_gen = heap->old_gen();

  if (!ScavengeWithObjectsInToSpace) {
    // Do not attempt to promote unless to_space is empty
    if (!young_gen->to_space()->is_empty()) {
      _consecutive_skipped_scavenges++;
      if (UsePerfData) {
        counters->update_scavenge_skipped(to_space_not_empty);
      }
      return false;
    }
  }

  // Test to see if the scavenge will likely fail.
  PSAdaptiveSizePolicy* policy = heap->size_policy();

  // A similar test is done in the policy's should_full_GC().  If this is
  // changed, decide if that test should also be changed.
  size_t avg_promoted = (size_t) policy->padded_average_promoted_in_bytes();
  size_t promotion_estimate = MIN2(avg_promoted, young_gen->used_in_bytes());
  bool result = promotion_estimate < old_gen->free_in_bytes();

  if (PrintGCDetails && Verbose) {
    gclog_or_tty->print(result ? "  do scavenge: " : "  skip scavenge: ");
    gclog_or_tty->print_cr(" average_promoted " SIZE_FORMAT
      " padded_average_promoted " SIZE_FORMAT
      " free in old gen " SIZE_FORMAT,
      (size_t) policy->average_promoted_in_bytes(),
      (size_t) policy->padded_average_promoted_in_bytes(),
      old_gen->free_in_bytes());
    if (young_gen->used_in_bytes() <
        (size_t) policy->padded_average_promoted_in_bytes()) {
      gclog_or_tty->print_cr(" padded_promoted_average is greater"
        " than maximum promotion = " SIZE_FORMAT, young_gen->used_in_bytes());
    }
  }

  if (result) {
    _consecutive_skipped_scavenges = 0;
  } else {
    _consecutive_skipped_scavenges++;
    if (UsePerfData) {
      counters->update_scavenge_skipped(promoted_too_large);
    }
  }
  return result;
}

  // Used to add tasks
GCTaskManager* const PSScavenge::gc_task_manager() {
  assert(ParallelScavengeHeap::gc_task_manager() != NULL,
   "shouldn't return NULL");
  return ParallelScavengeHeap::gc_task_manager();
}

void PSScavenge::initialize() {
  // Arguments must have been parsed

  if (AlwaysTenure || NeverTenure) {
    assert(MaxTenuringThreshold == 0 || MaxTenuringThreshold == markOopDesc::max_age + 1,
           "MaxTenuringThreshold should be 0 or markOopDesc::max_age + 1, but is %d", (int) MaxTenuringThreshold);
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
  assert(old_gen->reserved().end() <= young_gen->eden_space()->bottom(),
         "old above young");
  set_young_generation_boundary(young_gen->eden_space()->bottom());

  // Initialize ref handling object for scavenging.
  MemRegion mr = young_gen->reserved();

  _ref_processor =
    new ReferenceProcessor(mr,                         // span
                           ParallelRefProcEnabled && (ParallelGCThreads > 1), // mt processing
                           ParallelGCThreads,          // mt processing degree
                           true,                       // mt discovery
                           ParallelGCThreads,          // mt discovery degree
                           true,                       // atomic_discovery
                           NULL);                      // header provides liveness info

  // Cache the cardtable
  _card_table = barrier_set_cast<CardTableExtension>(heap->barrier_set());

  _counters = new CollectorCounters("PSScavenge", 0);
}
