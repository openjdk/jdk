/*
 * Copyright (c) 2001, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1COLLECTEDHEAP_HPP
#define SHARE_GC_G1_G1COLLECTEDHEAP_HPP

#include "gc/g1/g1BarrierSet.hpp"
#include "gc/g1/g1BiasedArray.hpp"
#include "gc/g1/g1CardSet.hpp"
#include "gc/g1/g1CardTable.hpp"
#include "gc/g1/g1CollectionSet.hpp"
#include "gc/g1/g1CollectorState.hpp"
#include "gc/g1/g1ConcurrentMark.hpp"
#include "gc/g1/g1EdenRegions.hpp"
#include "gc/g1/g1EvacStats.hpp"
#include "gc/g1/g1GCPauseType.hpp"
#include "gc/g1/g1HeapRegionAttr.hpp"
#include "gc/g1/g1HeapRegionManager.hpp"
#include "gc/g1/g1HeapRegionSet.hpp"
#include "gc/g1/g1HeapTransition.hpp"
#include "gc/g1/g1HeapVerifier.hpp"
#include "gc/g1/g1MonitoringSupport.hpp"
#include "gc/g1/g1MonotonicArenaFreeMemoryTask.hpp"
#include "gc/g1/g1MonotonicArenaFreePool.hpp"
#include "gc/g1/g1NUMA.hpp"
#include "gc/g1/g1SurvivorRegions.hpp"
#include "gc/g1/g1YoungGCAllocationFailureInjector.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/gcHeapSummary.hpp"
#include "gc/shared/plab.hpp"
#include "gc/shared/taskqueue.hpp"
#include "memory/allocation.hpp"
#include "memory/iterator.hpp"
#include "memory/memRegion.hpp"
#include "runtime/atomic.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/threadSMR.hpp"
#include "utilities/bitMap.hpp"

// A "G1CollectedHeap" is an implementation of a java heap for HotSpot.
// It uses the "Garbage First" heap organization and algorithm, which
// may combine concurrent marking with parallel, incremental compaction of
// heap subsets that will yield large amounts of garbage.

// Forward declarations
class G1Allocator;
class G1BatchedTask;
class G1CardTableEntryClosure;
class G1ConcurrentMark;
class G1ConcurrentMarkThread;
class G1ConcurrentRefine;
class G1GCCounters;
class G1GCPhaseTimes;
class G1HeapSizingPolicy;
class G1NewTracer;
class G1RemSet;
class G1ReviseYoungLengthTask;
class G1ServiceTask;
class G1ServiceThread;
class GCMemoryManager;
class G1HeapRegion;
class MemoryPool;
class nmethod;
class PartialArrayStateManager;
class ReferenceProcessor;
class STWGCTimer;
class WorkerThreads;

typedef OverflowTaskQueue<ScannerTask, mtGC>           G1ScannerTasksQueue;
typedef GenericTaskQueueSet<G1ScannerTasksQueue, mtGC> G1ScannerTasksQueueSet;

typedef int RegionIdx_t;   // needs to hold [ 0..max_num_regions() )
typedef int CardIdx_t;     // needs to hold [ 0..CardsPerRegion )

// The G1 STW is alive closure.
// An instance is embedded into the G1CH and used as the
// (optional) _is_alive_non_header closure in the STW
// reference processor. It is also extensively used during
// reference processing during STW evacuation pauses.
class G1STWIsAliveClosure : public BoolObjectClosure {
  G1CollectedHeap* _g1h;
public:
  G1STWIsAliveClosure(G1CollectedHeap* g1h) : _g1h(g1h) {}
  bool do_object_b(oop p) override;
};

class G1STWSubjectToDiscoveryClosure : public BoolObjectClosure {
  G1CollectedHeap* _g1h;
public:
  G1STWSubjectToDiscoveryClosure(G1CollectedHeap* g1h) : _g1h(g1h) {}
  bool do_object_b(oop p) override;
};

class G1RegionMappingChangedListener : public G1MappingChangedListener {
 private:
  void reset_from_card_cache(uint start_idx, size_t num_regions);
 public:
  void on_commit(uint start_idx, size_t num_regions, bool zero_filled) override;
};

// Helper to claim contiguous sets of JavaThread for processing by multiple threads.
class G1JavaThreadsListClaimer : public StackObj {
  ThreadsListHandle _list;
  uint _claim_step;

  Atomic<uint> _cur_claim;

  // Attempts to claim _claim_step JavaThreads, returning an array of claimed
  // JavaThread* with count elements. Returns null (and a zero count) if there
  // are no more threads to claim.
  JavaThread* const* claim(uint& count);

public:
  G1JavaThreadsListClaimer(uint claim_step) : _list(), _claim_step(claim_step), _cur_claim(0) {
    assert(claim_step > 0, "must be");
  }

  // Executes the given closure on the elements of the JavaThread list, chunking the
  // JavaThread set in claim_step chunks for each caller to reduce parallelization
  // overhead.
  void apply(ThreadClosure* cl);

  // Total number of JavaThreads that can be claimed.
  uint length() const { return _list.length(); }
};

class G1CollectedHeap : public CollectedHeap {
  friend class VM_G1CollectForAllocation;
  friend class VM_G1CollectFull;
  friend class VM_G1TryInitiateConcMark;
  friend class VMStructs;
  friend class MutatorAllocRegion;
  friend class G1FullCollector;
  friend class G1GCAllocRegion;
  friend class G1HeapVerifier;

  friend class G1YoungGCVerifierMark;

  // Closures used in implementation.
  friend class G1EvacuateRegionsTask;
  friend class G1PLABAllocator;

  // Other related classes.
  friend class G1HeapPrinterMark;
  friend class G1HeapRegionClaimer;

  // Testing classes.
  friend class G1CheckRegionAttrTableClosure;

private:
  // GC Overhead Limit functionality related members.
  //
  // The goal is to return null for allocations prematurely (before really going
  // OOME) in case both GC CPU usage (>= GCTimeLimit) and not much available free
  // memory (<= GCHeapFreeLimit) so that applications can exit gracefully or try
  // to keep running by easing off memory.
  uintx _gc_overhead_counter;        // The number of consecutive garbage collections we were over the limits.

  void update_gc_overhead_counter();
  bool gc_overhead_limit_exceeded();

  G1ServiceThread* _service_thread;
  G1ServiceTask* _periodic_gc_task;
  G1MonotonicArenaFreeMemoryTask* _free_arena_memory_task;
  G1ReviseYoungLengthTask* _revise_young_length_task;

  WorkerThreads* _workers;

  // The current epoch for refinement, i.e. the number of times the card tables
  // have been swapped by a garbage collection.
  // Used for detecting whether concurrent refinement has been interrupted by a
  // garbage collection.
  size_t _refinement_epoch;

  // The following members are for tracking safepoint durations between garbage
  // collections.
  jlong _last_synchronized_start;

  jlong _last_refinement_epoch_start;
  jlong _yield_duration_in_refinement_epoch;       // Time spent in safepoints since beginning of last refinement epoch.
  size_t _last_safepoint_refinement_epoch;         // Refinement epoch before last safepoint.

  Ticks _collection_pause_end;

  static size_t _humongous_object_threshold_in_words;

  // These sets keep track of old and humongous regions respectively.
  G1HeapRegionSet _old_set;
  G1HeapRegionSet _humongous_set;

  // Young gen memory statistics before GC.
  G1MonotonicArenaMemoryStats _young_gen_card_set_stats;
  // Collection set candidates memory statistics after GC.
  G1MonotonicArenaMemoryStats _collection_set_candidates_card_set_stats;

  // The block offset table for the G1 heap.
  G1BlockOffsetTable* _bot;

public:
  void rebuild_free_region_list();
  // Start a new incremental collection set for the next pause.
  void start_new_collection_set();

  void prepare_region_for_full_compaction(G1HeapRegion* hr);

private:
  // Rebuilds the region sets / lists so that they are repopulated to
  // reflect the contents of the heap. The only exception is the
  // humongous set which was not torn down in the first place. If
  // free_list_only is true, it will only rebuild the free list.
  void rebuild_region_sets(bool free_list_only);

  // Callback for region mapping changed events.
  G1RegionMappingChangedListener _listener;

  // Handle G1 NUMA support.
  G1NUMA* _numa;

  // The sequence of all heap regions in the heap.
  G1HeapRegionManager _hrm;

  // Manages all allocations with regions except humongous object allocations.
  G1Allocator* _allocator;

  G1YoungGCAllocationFailureInjector _allocation_failure_injector;

  // Manages all heap verification.
  G1HeapVerifier* _verifier;

  // Outside of GC pauses, the number of bytes used in all regions other
  // than the current allocation region(s).
  volatile size_t _summary_bytes_used;

  void increase_used(size_t bytes);
  void decrease_used(size_t bytes);

  void set_used(size_t bytes);

  // Number of bytes used in all regions during GC. Typically changed when
  // retiring a GC alloc region.
  size_t _bytes_used_during_gc;

public:
  size_t bytes_used_during_gc() const { return _bytes_used_during_gc; }

private:
  // GC allocation statistics policy for survivors.
  G1EvacStats _survivor_evac_stats;

  // GC allocation statistics policy for tenured objects.
  G1EvacStats _old_evac_stats;

  // Helper for monitoring and management support.
  G1MonitoringSupport* _monitoring_support;

  uint _num_humongous_objects; // Current amount of (all) humongous objects found in the heap.
  uint _num_humongous_reclaim_candidates; // Number of humongous object eager reclaim candidates.
public:
  uint num_humongous_objects() const { return _num_humongous_objects; }
  uint num_humongous_reclaim_candidates() const { return _num_humongous_reclaim_candidates; }
  bool has_humongous_reclaim_candidates() const { return _num_humongous_reclaim_candidates > 0; }

  void set_humongous_stats(uint num_humongous_total, uint num_humongous_candidates);

  bool should_sample_collection_set_candidates() const;
  void set_collection_set_candidates_stats(G1MonotonicArenaMemoryStats& stats);
  void set_young_gen_card_set_stats(const G1MonotonicArenaMemoryStats& stats);

  void update_perf_counter_cpu_time();
private:

  // Return true if an explicit GC should start a concurrent cycle instead
  // of doing a STW full GC. A concurrent cycle should be started if:
  // (a) cause == _g1_humongous_allocation,
  // (b) cause == _java_lang_system_gc and +ExplicitGCInvokesConcurrent,
  // (c) cause == _dcmd_gc_run and +ExplicitGCInvokesConcurrent,
  // (d) cause == _wb_breakpoint,
  // (e) cause == _g1_periodic_collection and +G1PeriodicGCInvokesConcurrent.
  bool should_do_concurrent_full_gc(GCCause::Cause cause);

  // Wait until a full mark (either currently in progress or one that completed
  // after the current request) has finished. Returns whether that full mark started
  // after this request. If so, we typically do not need another one.
  bool wait_full_mark_finished(GCCause::Cause cause,
                               uint old_marking_started_before,
                               uint old_marking_started_after,
                               uint old_marking_completed_after);

  // Attempt to start a concurrent cycle with the indicated cause, for potentially
  // allocating allocation_word_size words.
  // precondition: should_do_concurrent_full_gc(cause)
  bool try_collect_concurrently(size_t allocation_word_size,
                                GCCause::Cause cause,
                                uint gc_counter,
                                uint old_marking_started_before);

  // indicates whether we are in young or mixed GC mode
  G1CollectorState _collector_state;

  // Keeps track of how many "old marking cycles" (i.e., Full GCs or
  // concurrent cycles) we have started.
  volatile uint _old_marking_cycles_started;

  // Keeps track of how many "old marking cycles" (i.e., Full GCs or
  // concurrent cycles) we have completed.
  volatile uint _old_marking_cycles_completed;

  // Create a memory mapper for auxiliary data structures of the given size and
  // translation factor.
  static G1RegionToSpaceMapper* create_aux_memory_mapper(const char* description,
                                                         size_t size,
                                                         size_t translation_factor);

  void trace_heap(GCWhen::Type when, const GCTracer* tracer) override;

  // These are macros so that, if the assert fires, we get the correct
  // line number, file, etc.

#define heap_locking_asserts_params(_extra_message_)                          \
  "%s : Heap_lock locked: %s, at safepoint: %s, is VM thread: %s",            \
  (_extra_message_),                                                          \
  BOOL_TO_STR(Heap_lock->owned_by_self()),                                    \
  BOOL_TO_STR(SafepointSynchronize::is_at_safepoint()),                       \
  BOOL_TO_STR(Thread::current()->is_VM_thread())

#define assert_heap_locked()                                                  \
  do {                                                                        \
    assert(Heap_lock->owned_by_self(),                                        \
           heap_locking_asserts_params("should be holding the Heap_lock"));   \
  } while (0)

#define assert_heap_locked_or_at_safepoint(_should_be_vm_thread_)             \
  do {                                                                        \
    assert(Heap_lock->owned_by_self() ||                                      \
           (SafepointSynchronize::is_at_safepoint() &&                        \
             ((_should_be_vm_thread_) == Thread::current()->is_VM_thread())), \
           heap_locking_asserts_params("should be holding the Heap_lock or "  \
                                        "should be at a safepoint"));         \
  } while (0)

#define assert_heap_locked_and_not_at_safepoint()                             \
  do {                                                                        \
    assert(Heap_lock->owned_by_self() &&                                      \
                                    !SafepointSynchronize::is_at_safepoint(), \
          heap_locking_asserts_params("should be holding the Heap_lock and "  \
                                       "should not be at a safepoint"));      \
  } while (0)

#define assert_heap_not_locked()                                              \
  do {                                                                        \
    assert(!Heap_lock->owned_by_self(),                                       \
        heap_locking_asserts_params("should not be holding the Heap_lock"));  \
  } while (0)

#define assert_heap_not_locked_and_not_at_safepoint()                         \
  do {                                                                        \
    assert(!Heap_lock->owned_by_self() &&                                     \
                                    !SafepointSynchronize::is_at_safepoint(), \
      heap_locking_asserts_params("should not be holding the Heap_lock and "  \
                                   "should not be at a safepoint"));          \
  } while (0)

#define assert_at_safepoint_on_vm_thread()                                        \
  do {                                                                            \
    assert_at_safepoint();                                                        \
    assert(Thread::current_or_null() != nullptr, "no current thread");            \
    assert(Thread::current()->is_VM_thread(), "current thread is not VM thread"); \
  } while (0)

#ifdef ASSERT
#define assert_used_and_recalculate_used_equal(g1h)                           \
  do {                                                                        \
    size_t cur_used_bytes = g1h->used();                                      \
    size_t recal_used_bytes = g1h->recalculate_used();                        \
    assert(cur_used_bytes == recal_used_bytes, "Used(%zu) is not" \
           " same as recalculated used(%zu).",                    \
           cur_used_bytes, recal_used_bytes);                                 \
  } while (0)
#else
#define assert_used_and_recalculate_used_equal(g1h) do {} while(0)
#endif

  // The young region list.
  G1EdenRegions _eden;
  G1SurvivorRegions _survivor;

  STWGCTimer* _gc_timer_stw;

  G1NewTracer* _gc_tracer_stw;

  // The current policy object for the collector.
  G1Policy* _policy;
  G1HeapSizingPolicy* _heap_sizing_policy;

  G1CollectionSet _collection_set;

  // Try to allocate a single non-humongous G1HeapRegion sufficient for
  // an allocation of the given word_size. If do_expand is true,
  // attempt to expand the heap if necessary to satisfy the allocation
  // request. 'type' takes the type of region to be allocated. (Use constants
  // Old, Eden, Humongous, Survivor defined in G1HeapRegionType.)
  G1HeapRegion* new_region(size_t word_size,
                           G1HeapRegionType type,
                           bool do_expand,
                           uint node_index = G1NUMA::AnyNodeIndex);

  // Initialize a contiguous set of free regions of length num_regions
  // and starting at index first so that they appear as a single
  // humongous region.
  HeapWord* humongous_obj_allocate_initialize_regions(G1HeapRegion* first_hr,
                                                      uint num_regions,
                                                      size_t word_size);

  // Attempt to allocate a humongous object of the given size. Return
  // null if unsuccessful.
  HeapWord* humongous_obj_allocate(size_t word_size);

  // The following two methods, allocate_new_tlab() and
  // mem_allocate(), are the two main entry points from the runtime
  // into the G1's allocation routines. They have the following
  // assumptions:
  //
  // * They should both be called outside safepoints.
  //
  // * They should both be called without holding the Heap_lock.
  //
  // * All allocation requests for new TLABs should go to
  //   allocate_new_tlab().
  //
  // * All non-TLAB allocation requests should go to mem_allocate().
  //
  // * If either call cannot satisfy the allocation request using the
  //   current allocating region, they will try to get a new one. If
  //   this fails, (only) mem_allocate() will attempt to do an evacuation
  //   pause and retry the allocation. Allocate_new_tlab() will return null,
  //   deferring to the following mem_allocate().
  //
  // * We do not allow humongous-sized TLABs. So, allocate_new_tlab
  //   should never be called with word_size being humongous. All
  //   humongous allocation requests should go to mem_allocate() which
  //   will satisfy them in a special path.

  HeapWord* allocate_new_tlab(size_t min_size,
                              size_t requested_size,
                              size_t* actual_size) override;

  HeapWord* mem_allocate(size_t word_size) override;

  // First-level mutator allocation attempt: try to allocate out of
  // the mutator alloc region without taking the Heap_lock. This
  // should only be used for non-humongous allocations.
  inline HeapWord* attempt_allocation(size_t min_word_size,
                                      size_t desired_word_size,
                                      size_t* actual_word_size,
                                      bool allow_gc);
  // Second-level mutator allocation attempt: take the Heap_lock and
  // retry the allocation attempt, potentially scheduling a GC
  // pause if allow_gc is set. This should only be used for non-humongous
  // allocations.
  HeapWord* attempt_allocation_slow(uint node_index, size_t word_size, bool allow_gc);

  // Takes the Heap_lock and attempts a humongous allocation. It can
  // potentially schedule a GC pause.
  HeapWord* attempt_allocation_humongous(size_t word_size);

  // Allocation attempt that should be called during safepoints (e.g.,
  // at the end of a successful GC). expect_null_mutator_alloc_region
  // specifies whether the mutator alloc region is expected to be null
  // or not.
  HeapWord* attempt_allocation_at_safepoint(size_t word_size,
                                            bool expect_null_mutator_alloc_region);

  // These methods are the "callbacks" from the G1AllocRegion class.

  // For mutator alloc regions.
  G1HeapRegion* new_mutator_alloc_region(size_t word_size, uint node_index);
  void retire_mutator_alloc_region(G1HeapRegion* alloc_region,
                                   size_t allocated_bytes);

  // For GC alloc regions.
  bool has_more_regions(G1HeapRegionAttr dest);
  G1HeapRegion* new_gc_alloc_region(size_t word_size, G1HeapRegionAttr dest, uint node_index);
  void retire_gc_alloc_region(G1HeapRegion* alloc_region,
                              size_t allocated_bytes, G1HeapRegionAttr dest);

  void resize_heap(size_t resize_bytes, bool should_expand);

  // - if clear_all_soft_refs is true, all soft references should be
  //   cleared during the GC.
  // - if do_maximal_compaction is true, full gc will do a maximally
  //   compacting collection, leaving no dead wood.
  // - if allocation_word_size is set, then this allocation size will
  //    be accounted for in case shrinking of the heap happens.
  // - it returns false if it is unable to do the collection due to the
  //   GC locker being active, true otherwise.
  void do_full_collection(size_t allocation_word_size,
                          bool clear_all_soft_refs,
                          bool do_maximal_compaction);

  // Callback from VM_G1CollectFull operation, or collect_as_vm_thread.
  void do_full_collection(bool clear_all_soft_refs) override;

  // Helper to do a full collection that clears soft references.
  void upgrade_to_full_collection();

  // Callback from VM_G1CollectForAllocation operation.
  // This function does everything necessary/possible to satisfy a
  // failed allocation request (including collection, expansion, etc.)
  HeapWord* satisfy_failed_allocation(size_t word_size);
  // Internal helpers used during full GC to split it up to
  // increase readability.
  bool abort_concurrent_cycle();
  void verify_before_full_collection();
  void prepare_heap_for_full_collection();
  void prepare_for_mutator_after_full_collection(size_t allocation_word_size);
  void abort_refinement();
  void verify_after_full_collection();
  void print_heap_after_full_collection();

  // Helper method for satisfy_failed_allocation()
  HeapWord* satisfy_failed_allocation_helper(size_t word_size,
                                             bool do_gc,
                                             bool maximal_compaction,
                                             bool expect_null_mutator_alloc_region);

  // Attempting to expand the heap sufficiently
  // to support an allocation of the given "word_size".  If
  // successful, perform the allocation and return the address of the
  // allocated block, or else null.
  HeapWord* expand_and_allocate(size_t word_size);

  void verify_numa_regions(const char* desc);

public:
  // If during a concurrent start pause we may install a pending list head which is not
  // otherwise reachable, ensure that it is marked in the bitmap for concurrent marking
  // to discover.
  void make_pending_list_reachable();

  G1ServiceThread* service_thread() const { return _service_thread; }

  WorkerThreads* workers() const { return _workers; }

  // Run the given batch task using the workers.
  void run_batch_task(G1BatchedTask* cl);

  // Return "optimal" number of chunks per region we want to use for claiming areas
  // within a region to claim during card table scanning.
  // The returned value is a trade-off between granularity of work distribution and
  // memory usage and maintenance costs of that table.
  // Testing showed that 64 for 1M/2M region, 128 for 4M/8M regions, 256 for 16/32M regions,
  // and so on seems to be such a good trade-off.
  static uint get_chunks_per_region_for_scan();
  // Return "optimal" number of chunks per region we want to use for claiming areas
  // within a region to claim during card table merging.
  // This is much smaller than for scanning as the merge work is much smaller.
  // Currently 1 for 1M regions, 2 for 2/4M regions, 4 for 8/16M regions and so on.
  static uint get_chunks_per_region_for_merge();

  G1Allocator* allocator() {
    return _allocator;
  }

  G1YoungGCAllocationFailureInjector* allocation_failure_injector() { return &_allocation_failure_injector; }

  G1HeapVerifier* verifier() {
    return _verifier;
  }

  G1MonitoringSupport* monitoring_support() {
    assert(_monitoring_support != nullptr, "should have been initialized");
    return _monitoring_support;
  }

  void pin_object(JavaThread* thread, oop obj) override;
  void unpin_object(JavaThread* thread, oop obj) override;

  void resize_heap_after_young_collection(size_t allocation_word_size);
  void resize_heap_after_full_collection(size_t allocation_word_size);

  // Check if there is memory to uncommit and if so schedule a task to do it.
  void uncommit_regions_if_necessary();
  // Immediately uncommit uncommittable regions.
  uint uncommit_regions(uint region_limit);
  bool has_uncommittable_regions();

  G1NUMA* numa() const { return _numa; }

  // Expand the garbage-first heap by at least the given size (in bytes!).
  // Returns true if the heap was expanded by the requested amount;
  // false otherwise.
  // (Rounds up to a G1HeapRegion boundary.)
  bool expand(size_t expand_bytes, WorkerThreads* pretouch_workers);
  bool expand_single_region(uint node_index);

  // Returns the PLAB statistics for a given destination.
  inline G1EvacStats* alloc_buffer_stats(G1HeapRegionAttr dest);

  // Determines PLAB size for a given destination.
  inline size_t desired_plab_sz(G1HeapRegionAttr dest);
  // Clamp the given PLAB word size to allowed values. Prevents humongous PLAB sizes
  // for two reasons:
  // * PLABs are allocated using a similar paths as oops, but should
  //   never be in a humongous region
  // * Allowing humongous PLABs needlessly churns the region free lists
  inline size_t clamp_plab_size(size_t value) const;

  // Do anything common to GC's.
  void gc_prologue(bool full);
  void gc_epilogue(bool full);

  // Does the given region fulfill remembered set based eager reclaim candidate requirements?
  bool is_potential_eager_reclaim_candidate(G1HeapRegion* r) const;

  inline bool is_humongous_reclaim_candidate(uint region);

  // Remove from the reclaim candidate set.  Also remove from the
  // collection set so that later encounters avoid the slow path.
  inline void set_humongous_is_live(oop obj);

  // Register the given region to be part of the collection set.
  inline void register_humongous_candidate_region_with_region_attr(uint index);

  void set_humongous_metadata(G1HeapRegion* first_hr,
                              uint num_regions,
                              size_t word_size,
                              bool update_remsets);

  // The following methods update the region attribute table, i.e. a compact
  // representation of per-region information that is regularly accessed
  // during GC.
  inline void register_young_region_with_region_attr(G1HeapRegion* r);
  inline void register_new_survivor_region_with_region_attr(G1HeapRegion* r);
  inline void register_old_collection_set_region_with_region_attr(G1HeapRegion* r);
  inline void register_optional_region_with_region_attr(G1HeapRegion* r);

  // Updates region state without overwriting the type in the region attribute table.
  inline void update_region_attr(G1HeapRegion* r);

  void clear_region_attr(const G1HeapRegion* hr) {
    _region_attr.clear(hr);
  }

  void clear_region_attr() {
    _region_attr.clear();
  }

  // Verify that the G1RegionAttr remset tracking corresponds to actual remset tracking
  // for all regions.
  void verify_region_attr_is_remset_tracked() PRODUCT_RETURN;

  void clear_bitmap_for_region(G1HeapRegion* hr);

  bool is_user_requested_concurrent_full_gc(GCCause::Cause cause);

  // This is called at the start of either a concurrent cycle or a Full
  // GC to update the number of old marking cycles started.
  void increment_old_marking_cycles_started();

  // This is called at the end of either a concurrent cycle or a Full
  // GC to update the number of old marking cycles completed. Those two
  // can happen in a nested fashion, i.e., we start a concurrent
  // cycle, a Full GC happens half-way through it which ends first,
  // and then the cycle notices that a Full GC happened and ends
  // too. The concurrent parameter is a boolean to help us do a bit
  // tighter consistency checking in the method. If concurrent is
  // false, the caller is the inner caller in the nesting (i.e., the
  // Full GC). If concurrent is true, the caller is the outer caller
  // in this nesting (i.e., the concurrent cycle). Further nesting is
  // not currently supported. The end of this call also notifies
  // the G1OldGCCount_lock in case a Java thread is waiting for a full
  // GC to happen (e.g., it called System.gc() with
  // +ExplicitGCInvokesConcurrent).
  // whole_heap_examined should indicate that during that old marking
  // cycle the whole heap has been examined for live objects (as opposed
  // to only parts, or aborted before completion).
  void increment_old_marking_cycles_completed(bool concurrent, bool whole_heap_examined);

  uint old_marking_cycles_started() const {
    return _old_marking_cycles_started;
  }

  uint old_marking_cycles_completed() const {
    return _old_marking_cycles_completed;
  }

  // Allocates a new heap region instance.
  G1HeapRegion* new_heap_region(uint hrs_index, MemRegion mr);

  // Frees a region by resetting its metadata and adding it to the free list
  // passed as a parameter (this is usually a local list which will be appended
  // to the master free list later or null if free list management is handled
  // in another way).
  // Callers must ensure they are the only one calling free on the given region
  // at the same time.
  void free_region(G1HeapRegion* hr, G1FreeRegionList* free_list);

  // Add the given region to the retained regions collection set candidates.
  void retain_region(G1HeapRegion* hr);

  // Frees a humongous region by collapsing it into individual regions
  // and calling free_region() for each of them. The freed regions
  // will be added to the free list that's passed as a parameter (this
  // is usually a local list which will be appended to the master free
  // list later).
  // The method assumes that only a single thread is ever calling
  // this for a particular region at once.
  void free_humongous_region(G1HeapRegion* hr,
                             G1FreeRegionList* free_list);

  // Execute func(G1HeapRegion* r, bool is_last) on every region covered by the
  // given range.
  template <typename Func>
  void iterate_regions_in_range(MemRegion range, const Func& func);

  // Commit the required number of G1 region(s) according to the size requested
  // and mark them as 'old' region(s).
  // This API is only used for allocating heap space for the archived heap objects
  // in the CDS archive.
  HeapWord* alloc_archive_region(size_t word_size);

  // Populate the G1BlockOffsetTable for archived regions with the given
  // memory range.
  void populate_archive_regions_bot(MemRegion range);

  // For the specified range, uncommit the containing G1 regions
  // which had been allocated by alloc_archive_regions. This should be called
  // at JVM init time if the archive heap's contents cannot be used (e.g., if
  // CRC check fails).
  void dealloc_archive_regions(MemRegion range);

private:

  // Shrink the garbage-first heap by at most the given size (in bytes!).
  // (Rounds down to a G1HeapRegion boundary.)
  void shrink(size_t shrink_bytes);
  void shrink_helper(size_t expand_bytes);

  // Schedule the VM operation that will do an evacuation pause to
  // satisfy an allocation request of word_size. *succeeded will
  // return whether the VM operation was successful (it did do an
  // evacuation pause) or not (another thread beat us to it or the GC
  // locker was active). Given that we should not be holding the
  // Heap_lock when we enter this method, we will pass the
  // gc_count_before (i.e., total_collections()) as a parameter since
  // it has to be read while holding the Heap_lock. Currently, both
  // methods that call do_collection_pause() release the Heap_lock
  // before the call, so it's easy to read gc_count_before just before.
  HeapWord* do_collection_pause(size_t word_size,
                                uint gc_count_before,
                                bool* succeeded,
                                GCCause::Cause gc_cause);

  // Perform an incremental collection at a safepoint, possibly followed by a
  // by-policy upgrade to a full collection.
  // The collection should expect to be followed by an allocation of allocation_word_size.
  // precondition: at safepoint on VM thread
  // precondition: !is_stw_gc_active()
  void do_collection_pause_at_safepoint(size_t allocation_word_size);

  void verify_before_young_collection(G1HeapVerifier::G1VerifyType type);
  void verify_after_young_collection(G1HeapVerifier::G1VerifyType type);

public:
  // Start a concurrent cycle.
  void start_concurrent_cycle(bool concurrent_operation_is_full_mark);

  void prepare_for_mutator_after_young_collection();

  void retire_tlabs();

  // Update all region's pin counts from the per-thread caches and resets them.
  // Must be called before any decision based on pin counts.
  void flush_region_pin_cache();

  void record_obj_copy_mem_stats();

private:
  // The g1 remembered set of the heap.
  G1RemSet* _rem_set;
  // Global card set configuration
  G1CardSetConfiguration _card_set_config;

  G1MonotonicArenaFreePool _card_set_freelist_pool;

  // Group cardsets
  G1CSetCandidateGroup _young_regions_cset_group;

public:
  G1CardSetConfiguration* card_set_config() { return &_card_set_config; }

  G1CSetCandidateGroup* young_regions_cset_group() { return &_young_regions_cset_group; }

  // After a collection pause, reset eden and the collection set.
  void clear_eden();
  void clear_collection_set();

  // Abandon the current collection set without recording policy
  // statistics or updating free lists.
  void abandon_collection_set();

  // The concurrent marker (and the thread it runs in.)
  G1ConcurrentMark* _cm;
  G1ConcurrentMarkThread* _cm_thread;

  // The concurrent refiner.
  G1ConcurrentRefine* _cr;

  // Reusable parallel task queues and partial array manager.
  G1ScannerTasksQueueSet* _task_queues;
  PartialArrayStateManager* _partial_array_state_manager;

  // ("Weak") Reference processing support.
  //
  // G1 has 2 instances of the reference processor class.
  //
  // One (_ref_processor_cm) handles reference object discovery and subsequent
  // processing during concurrent marking cycles. Discovery is enabled/disabled
  // at the start/end of a concurrent marking cycle.
  //
  // The other (_ref_processor_stw) handles reference object discovery and
  // processing during incremental evacuation pauses and full GC pauses.
  //
  // ## Incremental evacuation pauses
  //
  // STW ref processor discovery is enabled/disabled at the start/end of an
  // incremental evacuation pause. No particular handling of the CM ref
  // processor is needed, apart from treating the discovered references as
  // roots; CM discovery does not need to be temporarily disabled as all
  // marking threads are paused during incremental evacuation pauses.
  //
  // ## Full GC pauses
  //
  // We abort any ongoing concurrent marking cycle, disable CM discovery, and
  // temporarily substitute a new closure for the STW ref processor's
  // _is_alive_non_header field (old value is restored after the full GC). Then
  // STW ref processor discovery is enabled, and marking & compaction
  // commences.

  // The (stw) reference processor...
  ReferenceProcessor* _ref_processor_stw;

  // During reference object discovery, the _is_alive_non_header
  // closure (if non-null) is applied to the referent object to
  // determine whether the referent is live. If so then the
  // reference object does not need to be 'discovered' and can
  // be treated as a regular oop. This has the benefit of reducing
  // the number of 'discovered' reference objects that need to
  // be processed.
  //
  // Instance of the is_alive closure for embedding into the
  // STW reference processor as the _is_alive_non_header field.
  // Supplying a value for the _is_alive_non_header field is
  // optional but doing so prevents unnecessary additions to
  // the discovered lists during reference discovery.
  G1STWIsAliveClosure _is_alive_closure_stw;

  G1STWSubjectToDiscoveryClosure _is_subject_to_discovery_stw;

  // The (concurrent marking) reference processor...
  ReferenceProcessor* _ref_processor_cm;

  // Instance of the concurrent mark is_alive closure for embedding
  // into the Concurrent Marking reference processor as the
  // _is_alive_non_header field. Supplying a value for the
  // _is_alive_non_header field is optional but doing so prevents
  // unnecessary additions to the discovered lists during reference
  // discovery.
  G1CMIsAliveClosure _is_alive_closure_cm;

  G1CMSubjectToDiscoveryClosure _is_subject_to_discovery_cm;
public:

  G1ScannerTasksQueueSet* task_queues() const;
  G1ScannerTasksQueue* task_queue(uint i) const;

  PartialArrayStateManager* partial_array_state_manager() const;

  // Create a G1CollectedHeap.
  // Must call the initialize method afterwards.
  // May not return if something goes wrong.
  G1CollectedHeap();

private:
  jint initialize_concurrent_refinement();
  jint initialize_service_thread();

  void print_tracing_info() const override;
  void stop() override;

public:
  // Initialize the G1CollectedHeap to have the initial and
  // maximum sizes and remembered and barrier sets
  // specified by the policy object.
  jint initialize() override;

  // Returns whether concurrent mark threads (and the VM) are about to terminate.
  bool concurrent_mark_is_terminating() const;

  void safepoint_synchronize_begin() override;
  void safepoint_synchronize_end() override;

  jlong last_refinement_epoch_start() const { return _last_refinement_epoch_start; }
  void set_last_refinement_epoch_start(jlong epoch_start, jlong last_yield_duration);
  jlong yield_duration_in_refinement_epoch();

  // Does operations required after initialization has been done.
  void post_initialize() override;

  // Initialize weak reference processing.
  void ref_processing_init();

  Name kind() const override {
    return CollectedHeap::G1;
  }

  const char* name() const override {
    return "G1";
  }

  const G1CollectorState* collector_state() const { return &_collector_state; }
  G1CollectorState* collector_state() { return &_collector_state; }

  // The current policy object for the collector.
  G1Policy* policy() const { return _policy; }
  // The remembered set.
  G1RemSet* rem_set() const { return _rem_set; }

  const G1MonotonicArenaFreePool* card_set_freelist_pool() const { return &_card_set_freelist_pool; }
  G1MonotonicArenaFreePool* card_set_freelist_pool() { return &_card_set_freelist_pool; }

  inline G1GCPhaseTimes* phase_times() const;

  const G1CollectionSet* collection_set() const { return &_collection_set; }
  G1CollectionSet* collection_set() { return &_collection_set; }

  inline bool is_collection_set_candidate(const G1HeapRegion* r) const;

  void initialize_serviceability() override;
  MemoryUsage memory_usage() override;
  GrowableArray<GCMemoryManager*> memory_managers() override;
  GrowableArray<MemoryPool*> memory_pools() override;

  void fill_with_dummy_object(HeapWord* start, HeapWord* end, bool zap) override;

  static void start_codecache_marking_cycle_if_inactive(bool concurrent_mark_start);
  static void finish_codecache_marking_cycle();

  // The shared block offset table array.
  G1BlockOffsetTable* bot() const { return _bot; }

  // Reference Processing accessors

  // The STW reference processor....
  ReferenceProcessor* ref_processor_stw() const { return _ref_processor_stw; }

  G1NewTracer* gc_tracer_stw() const { return _gc_tracer_stw; }
  STWGCTimer* gc_timer_stw() const { return _gc_timer_stw; }

  // The Concurrent Marking reference processor...
  ReferenceProcessor* ref_processor_cm() const { return _ref_processor_cm; }

  size_t unused_committed_regions_in_bytes() const;

  size_t capacity() const override;
  size_t used() const override;
  // This should be called when we're not holding the heap lock. The
  // result might be a bit inaccurate.
  size_t used_unlocked() const;
  size_t recalculate_used() const;

  // These virtual functions do the actual allocation.
  // Some heaps may offer a contiguous region for shared non-blocking
  // allocation, via inlined code (by exporting the address of the top and
  // end fields defining the extent of the contiguous allocation region.)
  // But G1CollectedHeap doesn't yet support this.

  // Returns true if an incremental GC should be upgrade to a full gc. This
  // is done when there are no free regions and the heap can't be expanded.
  bool should_upgrade_to_full_gc() const {
    return num_available_regions() == 0;
  }

  // The number of inactive regions.
  uint num_inactive_regions() const { return _hrm.num_inactive_regions(); }

  // The current number of regions in the heap.
  uint num_committed_regions() const { return _hrm.num_committed_regions(); }

  // The max number of regions reserved for the heap.
  uint max_num_regions() const { return _hrm.max_num_regions(); }

  // The number of regions that are completely free.
  uint num_free_regions() const { return _hrm.num_free_regions(); }

  // The number of regions that are not completely free.
  uint num_used_regions() const { return _hrm.num_used_regions(); }

  // The number of regions that can be allocated into.
  uint num_available_regions() const { return num_free_regions() + num_inactive_regions(); }

  MemoryUsage get_auxiliary_data_memory_usage() const {
    return _hrm.get_auxiliary_data_memory_usage();
  }

#ifdef ASSERT
  bool is_on_master_free_list(G1HeapRegion* hr) {
    return _hrm.is_free(hr);
  }
#endif // ASSERT

  inline void old_set_add(G1HeapRegion* hr);
  inline void old_set_remove(G1HeapRegion* hr);

  // Returns how much memory there is assigned to non-young heap that can not be
  // allocated into any more without garbage collection after a hypothetical
  // allocation of allocation_word_size.
  size_t non_young_occupancy_after_allocation(size_t allocation_word_size);

  // Determine whether the given region is one that we are using as an
  // old GC alloc region.
  bool is_old_gc_alloc_region(G1HeapRegion* hr);

  void collect(GCCause::Cause cause) override;

  // Try to perform a collection of the heap with the given cause to allocate allocation_word_size
  // words.
  // Returns whether this collection actually executed.
  bool try_collect(size_t allocation_word_size, GCCause::Cause cause, const G1GCCounters& counters_before);

  void start_concurrent_gc_for_metadata_allocation(GCCause::Cause gc_cause);

  bool last_gc_was_periodic() { return _gc_lastcause == GCCause::_g1_periodic_collection; }

  void remove_from_old_gen_sets(const uint old_regions_removed,
                                const uint humongous_regions_removed);
  void prepend_to_freelist(G1FreeRegionList* list);
  void decrement_summary_bytes(size_t bytes);

  bool is_in(const void* p) const override;

  // Return "TRUE" iff the given object address is within the collection
  // set. Assumes that the reference points into the heap.
  inline bool is_in_cset(const G1HeapRegion* hr) const;
  inline bool is_in_cset(oop obj) const;
  inline bool is_in_cset(HeapWord* addr) const;

  inline bool is_in_cset_or_humongous_candidate(const oop obj);

 private:
  // This array is used for a quick test on whether a reference points into
  // the collection set or not. Each of the array's elements denotes whether the
  // corresponding region is in the collection set or not.
  G1HeapRegionAttrBiasedMappedArray _region_attr;

 public:

  inline G1HeapRegionAttr region_attr(const void* obj) const;
  inline G1HeapRegionAttr region_attr(uint idx) const;

  MemRegion reserved() const {
    return _hrm.reserved();
  }

  bool is_in_reserved(const void* addr) const {
    return reserved().contains(addr);
  }

  G1CardTable* card_table() const {
    return static_cast<G1CardTable*>(G1BarrierSet::g1_barrier_set()->card_table());
  }

  G1CardTable* refinement_table() const {
    return G1BarrierSet::g1_barrier_set()->refinement_table();
  }

  G1CardTable::CardValue* card_table_base() const {
    assert(card_table() != nullptr, "must be");
    return card_table()->byte_map_base();
  }

  // Iteration functions.

  void object_iterate_parallel(ObjectClosure* cl, uint worker_id, G1HeapRegionClaimer* claimer);

  // Iterate over all objects, calling "cl.do_object" on each.
  void object_iterate(ObjectClosure* cl) override;

  ParallelObjectIteratorImpl* parallel_object_iterator(uint thread_num) override;

  // Keep alive an object that was loaded with AS_NO_KEEPALIVE.
  void keep_alive(oop obj) override;

  // Iterate over heap regions, in address order, terminating the
  // iteration early if the "do_heap_region" method returns "true".
  void heap_region_iterate(G1HeapRegionClosure* blk) const;
  void heap_region_iterate(G1HeapRegionIndexClosure* blk) const;

  // Return the region with the given index. It assumes the index is valid.
  inline G1HeapRegion* region_at(uint index) const;
  inline G1HeapRegion* region_at_or_null(uint index) const;

  // Iterate over the regions that the humongous object starting at the given
  // region and apply the given method with the signature f(G1HeapRegion*) on them.
  template <typename Func>
  void humongous_obj_regions_iterate(G1HeapRegion* start, const Func& f);

  // Calculate the region index of the given address. Given address must be
  // within the heap.
  inline uint addr_to_region(const void* addr) const;

  inline HeapWord* bottom_addr_for_region(uint index) const;

  // Two functions to iterate over the heap regions in parallel. Threads
  // compete using the G1HeapRegionClaimer to claim the regions before
  // applying the closure on them.
  // The _from_worker_offset version uses the G1HeapRegionClaimer and
  // the worker id to calculate a start offset to prevent all workers to
  // start from the point.
  void heap_region_par_iterate_from_worker_offset(G1HeapRegionClosure* cl,
                                                  G1HeapRegionClaimer* hrclaimer,
                                                  uint worker_id) const;

  void heap_region_par_iterate_from_start(G1HeapRegionClosure* cl,
                                          G1HeapRegionClaimer* hrclaimer) const;

  // Iterate over all regions in the collection set in parallel.
  void collection_set_par_iterate_all(G1HeapRegionClosure* cl,
                                      G1HeapRegionClaimer* hr_claimer,
                                      uint worker_id);

  // Iterate over all regions currently in the current collection set.
  void collection_set_iterate_all(G1HeapRegionClosure* blk);

  // Iterate over the regions in the current increment of the collection set.
  // Starts the iteration so that the start regions of a given worker id over the
  // set active_workers are evenly spread across the set of collection set regions
  // to be iterated.
  // The variant with the G1HeapRegionClaimer guarantees that the closure will be
  // applied to a particular region exactly once.
  void collection_set_iterate_increment_from(G1HeapRegionClosure *blk, uint worker_id) {
    collection_set_iterate_increment_from(blk, nullptr, worker_id);
  }
  void collection_set_iterate_increment_from(G1HeapRegionClosure *blk, G1HeapRegionClaimer* hr_claimer, uint worker_id);
  // Iterate over the array of region indexes, uint regions[length], applying
  // the given G1HeapRegionClosure on each region. The worker_id will determine where
  // to start the iteration to allow for more efficient parallel iteration.
  void par_iterate_regions_array(G1HeapRegionClosure* cl,
                                 G1HeapRegionClaimer* hr_claimer,
                                 const uint regions[],
                                 size_t length,
                                 uint worker_id) const;

  // Returns the G1HeapRegion that contains addr. addr must not be null.
  inline G1HeapRegion* heap_region_containing(const void* addr) const;

  // Returns the G1HeapRegion that contains addr, or null if that is an uncommitted
  // region. addr must not be null.
  inline G1HeapRegion* heap_region_containing_or_null(const void* addr) const;

  // A CollectedHeap is divided into a dense sequence of "blocks"; that is,
  // each address in the (reserved) heap is a member of exactly
  // one block.  The defining characteristic of a block is that it is
  // possible to find its size, and thus to progress forward to the next
  // block.  (Blocks may be of different sizes.)  Thus, blocks may
  // represent Java objects, or they might be free blocks in a
  // free-list-based heap (or subheap), as long as the two kinds are
  // distinguishable and the size of each is determinable.

  // Returns the address of the start of the "block" that contains the
  // address "addr".  We say "blocks" instead of "object" since some heaps
  // may not pack objects densely; a chunk may either be an object or a
  // non-object.
  HeapWord* block_start(const void* addr) const;

  // Requires "addr" to be the start of a block, and returns "TRUE" iff
  // the block is an object.
  bool block_is_obj(const HeapWord* addr) const;

  // Section on thread-local allocation buffers (TLABs)
  // See CollectedHeap for semantics.

  size_t tlab_capacity() const override;
  size_t tlab_used() const override;
  size_t max_tlab_size() const override;
  size_t unsafe_max_tlab_alloc() const override;

  inline bool is_in_young(const oop obj) const;
  inline bool requires_barriers(stackChunkOop obj) const override;

  // Returns "true" iff the given word_size is "very large".
  static bool is_humongous(size_t word_size) {
    // Note this has to be strictly greater-than as the TLABs
    // are capped at the humongous threshold and we want to
    // ensure that we don't try to allocate a TLAB as
    // humongous and that we don't allocate a humongous
    // object in a TLAB.
    return word_size > _humongous_object_threshold_in_words;
  }

  // Returns the humongous threshold for a specific region size
  static size_t humongous_threshold_for(size_t region_size) {
    return (region_size / 2);
  }

  // Returns the number of regions the humongous object of the given word size
  // requires.
  static size_t humongous_obj_size_in_regions(size_t word_size);

  // Returns how much space in bytes an allocation of word_size will use up in the
  // heap.
  static size_t allocation_used_bytes(size_t word_size);

  // Print the maximum heap capacity.
  size_t max_capacity() const override;
  size_t min_capacity() const;

  Tickspan time_since_last_collection() const { return Ticks::now() - _collection_pause_end; }

  // Convenience function to be used in situations where the heap type can be
  // asserted to be this type.
  static G1CollectedHeap* heap() {
    return named_heap<G1CollectedHeap>(CollectedHeap::G1);
  }

  // add appropriate methods for any other surv rate groups

  G1SurvivorRegions* survivor() { return &_survivor; }

  inline uint eden_target_length() const;
  uint eden_regions_count() const { return _eden.length(); }
  uint eden_regions_count(uint node_index) const { return _eden.regions_on_node(node_index); }
  uint survivor_regions_count() const { return _survivor.length(); }
  uint survivor_regions_count(uint node_index) const { return _survivor.regions_on_node(node_index); }
  size_t eden_regions_used_bytes() const { return _eden.used_bytes(); }
  size_t survivor_regions_used_bytes() const { return _survivor.used_bytes(); }
  uint young_regions_count() const { return _eden.length() + _survivor.length(); }
  uint old_regions_count() const { return _old_set.length(); }
  uint humongous_regions_count() const { return _humongous_set.length(); }

#ifdef ASSERT
  bool check_young_list_empty();
#endif

  bool is_marked(oop obj) const;

  // Determine if an object is dead, given the object and also
  // the region to which the object belongs.
  inline bool is_obj_dead(const oop obj, const G1HeapRegion* hr) const;

  // Determine if an object is dead, given only the object itself.
  // This will find the region to which the object belongs and
  // then call the region version of the same function.
  // If obj is null it is not dead.
  inline bool is_obj_dead(const oop obj) const;

  inline bool is_obj_dead_full(const oop obj, const G1HeapRegion* hr) const;
  inline bool is_obj_dead_full(const oop obj) const;

  // Mark the live object that failed evacuation in the bitmap.
  void mark_evac_failure_object(uint worker_id, oop obj, size_t obj_size) const;

  G1ConcurrentMark* concurrent_mark() const { return _cm; }

  // Refinement

  G1ConcurrentRefine* concurrent_refine() const { return _cr; }

  // Optimized nmethod scanning support routines

  // Register the given nmethod with the G1 heap.
  void register_nmethod(nmethod* nm) override;

  // Unregister the given nmethod from the G1 heap.
  void unregister_nmethod(nmethod* nm) override;

  // No nmethod verification implemented.
  void verify_nmethod(nmethod* nm) override {}

  // Recalculate amount of used memory after GC. Must be called after all allocation
  // has finished.
  void update_used_after_gc(bool evacuation_failed);

  // Rebuild the code root lists for each region
  // after a full GC.
  void rebuild_code_roots();

  // Performs cleaning of data structures after class unloading.
  void complete_cleaning(bool class_unloading_occurred);

  void unload_classes_and_code(const char* description, BoolObjectClosure* cl, GCTimer* timer);

  void bulk_unregister_nmethods();

  // Verification

  // Perform any cleanup actions necessary before allowing a verification.
  void prepare_for_verify() override;

  // Perform verification.
  void verify(VerifyOption vo) override;

  // WhiteBox testing support.
  bool supports_concurrent_gc_breakpoints() const override;

  WorkerThreads* safepoint_workers() override { return _workers; }

  // The methods below are here for convenience and dispatch the
  // appropriate method depending on value of the given VerifyOption
  // parameter. The values for that parameter, and their meanings,
  // are the same as those above.

  bool is_obj_dead_cond(const oop obj,
                        const G1HeapRegion* hr,
                        const VerifyOption vo) const;

  bool is_obj_dead_cond(const oop obj,
                        const VerifyOption vo) const;

  G1HeapSummary create_g1_heap_summary();
  G1EvacSummary create_g1_evac_summary(G1EvacStats* stats);

  // Printing
private:
  void print_heap_regions() const;
  void print_regions_on(outputStream* st) const;

public:
  void print_heap_on(outputStream* st) const override;
  void print_extended_on(outputStream* st) const;
  void print_gc_on(outputStream* st) const override;

  void gc_threads_do(ThreadClosure* tc) const override;

  // Used to print information about locations in the hs_err file.
  bool print_location(outputStream* st, void* addr) const override;
};

// Scoped object that performs common pre- and post-gc heap printing operations.
class G1HeapPrinterMark : public StackObj {
  G1CollectedHeap* _g1h;
  G1HeapTransition _heap_transition;

public:
  G1HeapPrinterMark(G1CollectedHeap* g1h);
  ~G1HeapPrinterMark();
};

// Scoped object that performs common pre- and post-gc operations related to
// JFR events.
class G1JFRTracerMark : public StackObj {
protected:
  STWGCTimer* _timer;
  GCTracer* _tracer;

public:
  G1JFRTracerMark(STWGCTimer* timer, GCTracer* tracer);
  ~G1JFRTracerMark();
};

#endif // SHARE_GC_G1_G1COLLECTEDHEAP_HPP
