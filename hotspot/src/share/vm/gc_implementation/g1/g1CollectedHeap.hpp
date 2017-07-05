/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_G1COLLECTEDHEAP_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_G1COLLECTEDHEAP_HPP

#include "gc_implementation/g1/concurrentMark.hpp"
#include "gc_implementation/g1/evacuationInfo.hpp"
#include "gc_implementation/g1/g1AllocRegion.hpp"
#include "gc_implementation/g1/g1HRPrinter.hpp"
#include "gc_implementation/g1/g1MonitoringSupport.hpp"
#include "gc_implementation/g1/g1RemSet.hpp"
#include "gc_implementation/g1/g1YCTypes.hpp"
#include "gc_implementation/g1/heapRegionSeq.hpp"
#include "gc_implementation/g1/heapRegionSets.hpp"
#include "gc_implementation/shared/hSpaceCounters.hpp"
#include "gc_implementation/shared/parGCAllocBuffer.hpp"
#include "memory/barrierSet.hpp"
#include "memory/memRegion.hpp"
#include "memory/sharedHeap.hpp"
#include "utilities/stack.hpp"

// A "G1CollectedHeap" is an implementation of a java heap for HotSpot.
// It uses the "Garbage First" heap organization and algorithm, which
// may combine concurrent marking with parallel, incremental compaction of
// heap subsets that will yield large amounts of garbage.

class HeapRegion;
class HRRSCleanupTask;
class GenerationSpec;
class OopsInHeapRegionClosure;
class G1KlassScanClosure;
class G1ScanHeapEvacClosure;
class ObjectClosure;
class SpaceClosure;
class CompactibleSpaceClosure;
class Space;
class G1CollectorPolicy;
class GenRemSet;
class G1RemSet;
class HeapRegionRemSetIterator;
class ConcurrentMark;
class ConcurrentMarkThread;
class ConcurrentG1Refine;
class ConcurrentGCTimer;
class GenerationCounters;
class STWGCTimer;
class G1NewTracer;
class G1OldTracer;
class EvacuationFailedInfo;

typedef OverflowTaskQueue<StarTask, mtGC>         RefToScanQueue;
typedef GenericTaskQueueSet<RefToScanQueue, mtGC> RefToScanQueueSet;

typedef int RegionIdx_t;   // needs to hold [ 0..max_regions() )
typedef int CardIdx_t;     // needs to hold [ 0..CardsPerRegion )

enum GCAllocPurpose {
  GCAllocForTenured,
  GCAllocForSurvived,
  GCAllocPurposeCount
};

class YoungList : public CHeapObj<mtGC> {
private:
  G1CollectedHeap* _g1h;

  HeapRegion* _head;

  HeapRegion* _survivor_head;
  HeapRegion* _survivor_tail;

  HeapRegion* _curr;

  uint        _length;
  uint        _survivor_length;

  size_t      _last_sampled_rs_lengths;
  size_t      _sampled_rs_lengths;

  void         empty_list(HeapRegion* list);

public:
  YoungList(G1CollectedHeap* g1h);

  void         push_region(HeapRegion* hr);
  void         add_survivor_region(HeapRegion* hr);

  void         empty_list();
  bool         is_empty() { return _length == 0; }
  uint         length() { return _length; }
  uint         survivor_length() { return _survivor_length; }

  // Currently we do not keep track of the used byte sum for the
  // young list and the survivors and it'd be quite a lot of work to
  // do so. When we'll eventually replace the young list with
  // instances of HeapRegionLinkedList we'll get that for free. So,
  // we'll report the more accurate information then.
  size_t       eden_used_bytes() {
    assert(length() >= survivor_length(), "invariant");
    return (size_t) (length() - survivor_length()) * HeapRegion::GrainBytes;
  }
  size_t       survivor_used_bytes() {
    return (size_t) survivor_length() * HeapRegion::GrainBytes;
  }

  void rs_length_sampling_init();
  bool rs_length_sampling_more();
  void rs_length_sampling_next();

  void reset_sampled_info() {
    _last_sampled_rs_lengths =   0;
  }
  size_t sampled_rs_lengths() { return _last_sampled_rs_lengths; }

  // for development purposes
  void reset_auxilary_lists();
  void clear() { _head = NULL; _length = 0; }

  void clear_survivors() {
    _survivor_head    = NULL;
    _survivor_tail    = NULL;
    _survivor_length  = 0;
  }

  HeapRegion* first_region() { return _head; }
  HeapRegion* first_survivor_region() { return _survivor_head; }
  HeapRegion* last_survivor_region() { return _survivor_tail; }

  // debugging
  bool          check_list_well_formed();
  bool          check_list_empty(bool check_sample = true);
  void          print();
};

class MutatorAllocRegion : public G1AllocRegion {
protected:
  virtual HeapRegion* allocate_new_region(size_t word_size, bool force);
  virtual void retire_region(HeapRegion* alloc_region, size_t allocated_bytes);
public:
  MutatorAllocRegion()
    : G1AllocRegion("Mutator Alloc Region", false /* bot_updates */) { }
};

// The G1 STW is alive closure.
// An instance is embedded into the G1CH and used as the
// (optional) _is_alive_non_header closure in the STW
// reference processor. It is also extensively used during
// reference processing during STW evacuation pauses.
class G1STWIsAliveClosure: public BoolObjectClosure {
  G1CollectedHeap* _g1;
public:
  G1STWIsAliveClosure(G1CollectedHeap* g1) : _g1(g1) {}
  bool do_object_b(oop p);
};

class SurvivorGCAllocRegion : public G1AllocRegion {
protected:
  virtual HeapRegion* allocate_new_region(size_t word_size, bool force);
  virtual void retire_region(HeapRegion* alloc_region, size_t allocated_bytes);
public:
  SurvivorGCAllocRegion()
  : G1AllocRegion("Survivor GC Alloc Region", false /* bot_updates */) { }
};

class OldGCAllocRegion : public G1AllocRegion {
protected:
  virtual HeapRegion* allocate_new_region(size_t word_size, bool force);
  virtual void retire_region(HeapRegion* alloc_region, size_t allocated_bytes);
public:
  OldGCAllocRegion()
  : G1AllocRegion("Old GC Alloc Region", true /* bot_updates */) { }
};

class RefineCardTableEntryClosure;

class G1CollectedHeap : public SharedHeap {
  friend class VM_G1CollectForAllocation;
  friend class VM_G1CollectFull;
  friend class VM_G1IncCollectionPause;
  friend class VMStructs;
  friend class MutatorAllocRegion;
  friend class SurvivorGCAllocRegion;
  friend class OldGCAllocRegion;

  // Closures used in implementation.
  template <bool do_gen_barrier, G1Barrier barrier, bool do_mark_object>
  friend class G1ParCopyClosure;
  friend class G1IsAliveClosure;
  friend class G1EvacuateFollowersClosure;
  friend class G1ParScanThreadState;
  friend class G1ParScanClosureSuper;
  friend class G1ParEvacuateFollowersClosure;
  friend class G1ParTask;
  friend class G1FreeGarbageRegionClosure;
  friend class RefineCardTableEntryClosure;
  friend class G1PrepareCompactClosure;
  friend class RegionSorter;
  friend class RegionResetter;
  friend class CountRCClosure;
  friend class EvacPopObjClosure;
  friend class G1ParCleanupCTTask;

  // Other related classes.
  friend class G1MarkSweep;

private:
  // The one and only G1CollectedHeap, so static functions can find it.
  static G1CollectedHeap* _g1h;

  static size_t _humongous_object_threshold_in_words;

  // Storage for the G1 heap.
  VirtualSpace _g1_storage;
  MemRegion    _g1_reserved;

  // The part of _g1_storage that is currently committed.
  MemRegion _g1_committed;

  // The master free list. It will satisfy all new region allocations.
  MasterFreeRegionList      _free_list;

  // The secondary free list which contains regions that have been
  // freed up during the cleanup process. This will be appended to the
  // master free list when appropriate.
  SecondaryFreeRegionList   _secondary_free_list;

  // It keeps track of the old regions.
  MasterOldRegionSet        _old_set;

  // It keeps track of the humongous regions.
  MasterHumongousRegionSet  _humongous_set;

  // The number of regions we could create by expansion.
  uint _expansion_regions;

  // The block offset table for the G1 heap.
  G1BlockOffsetSharedArray* _bot_shared;

  // Tears down the region sets / lists so that they are empty and the
  // regions on the heap do not belong to a region set / list. The
  // only exception is the humongous set which we leave unaltered. If
  // free_list_only is true, it will only tear down the master free
  // list. It is called before a Full GC (free_list_only == false) or
  // before heap shrinking (free_list_only == true).
  void tear_down_region_sets(bool free_list_only);

  // Rebuilds the region sets / lists so that they are repopulated to
  // reflect the contents of the heap. The only exception is the
  // humongous set which was not torn down in the first place. If
  // free_list_only is true, it will only rebuild the master free
  // list. It is called after a Full GC (free_list_only == false) or
  // after heap shrinking (free_list_only == true).
  void rebuild_region_sets(bool free_list_only);

  // The sequence of all heap regions in the heap.
  HeapRegionSeq _hrs;

  // Alloc region used to satisfy mutator allocation requests.
  MutatorAllocRegion _mutator_alloc_region;

  // Alloc region used to satisfy allocation requests by the GC for
  // survivor objects.
  SurvivorGCAllocRegion _survivor_gc_alloc_region;

  // PLAB sizing policy for survivors.
  PLABStats _survivor_plab_stats;

  // Alloc region used to satisfy allocation requests by the GC for
  // old objects.
  OldGCAllocRegion _old_gc_alloc_region;

  // PLAB sizing policy for tenured objects.
  PLABStats _old_plab_stats;

  PLABStats* stats_for_purpose(GCAllocPurpose purpose) {
    PLABStats* stats = NULL;

    switch (purpose) {
    case GCAllocForSurvived:
      stats = &_survivor_plab_stats;
      break;
    case GCAllocForTenured:
      stats = &_old_plab_stats;
      break;
    default:
      assert(false, "unrecognized GCAllocPurpose");
    }

    return stats;
  }

  // The last old region we allocated to during the last GC.
  // Typically, it is not full so we should re-use it during the next GC.
  HeapRegion* _retained_old_gc_alloc_region;

  // It specifies whether we should attempt to expand the heap after a
  // region allocation failure. If heap expansion fails we set this to
  // false so that we don't re-attempt the heap expansion (it's likely
  // that subsequent expansion attempts will also fail if one fails).
  // Currently, it is only consulted during GC and it's reset at the
  // start of each GC.
  bool _expand_heap_after_alloc_failure;

  // It resets the mutator alloc region before new allocations can take place.
  void init_mutator_alloc_region();

  // It releases the mutator alloc region.
  void release_mutator_alloc_region();

  // It initializes the GC alloc regions at the start of a GC.
  void init_gc_alloc_regions(EvacuationInfo& evacuation_info);

  // It releases the GC alloc regions at the end of a GC.
  void release_gc_alloc_regions(uint no_of_gc_workers, EvacuationInfo& evacuation_info);

  // It does any cleanup that needs to be done on the GC alloc regions
  // before a Full GC.
  void abandon_gc_alloc_regions();

  // Helper for monitoring and management support.
  G1MonitoringSupport* _g1mm;

  // Determines PLAB size for a particular allocation purpose.
  size_t desired_plab_sz(GCAllocPurpose purpose);

  // Outside of GC pauses, the number of bytes used in all regions other
  // than the current allocation region.
  size_t _summary_bytes_used;

  // This is used for a quick test on whether a reference points into
  // the collection set or not. Basically, we have an array, with one
  // byte per region, and that byte denotes whether the corresponding
  // region is in the collection set or not. The entry corresponding
  // the bottom of the heap, i.e., region 0, is pointed to by
  // _in_cset_fast_test_base.  The _in_cset_fast_test field has been
  // biased so that it actually points to address 0 of the address
  // space, to make the test as fast as possible (we can simply shift
  // the address to address into it, instead of having to subtract the
  // bottom of the heap from the address before shifting it; basically
  // it works in the same way the card table works).
  bool* _in_cset_fast_test;

  // The allocated array used for the fast test on whether a reference
  // points into the collection set or not. This field is also used to
  // free the array.
  bool* _in_cset_fast_test_base;

  // The length of the _in_cset_fast_test_base array.
  uint _in_cset_fast_test_length;

  volatile unsigned _gc_time_stamp;

  size_t* _surviving_young_words;

  G1HRPrinter _hr_printer;

  void setup_surviving_young_words();
  void update_surviving_young_words(size_t* surv_young_words);
  void cleanup_surviving_young_words();

  // It decides whether an explicit GC should start a concurrent cycle
  // instead of doing a STW GC. Currently, a concurrent cycle is
  // explicitly started if:
  // (a) cause == _gc_locker and +GCLockerInvokesConcurrent, or
  // (b) cause == _java_lang_system_gc and +ExplicitGCInvokesConcurrent.
  // (c) cause == _g1_humongous_allocation
  bool should_do_concurrent_full_gc(GCCause::Cause cause);

  // Keeps track of how many "old marking cycles" (i.e., Full GCs or
  // concurrent cycles) we have started.
  volatile unsigned int _old_marking_cycles_started;

  // Keeps track of how many "old marking cycles" (i.e., Full GCs or
  // concurrent cycles) we have completed.
  volatile unsigned int _old_marking_cycles_completed;

  bool _concurrent_cycle_started;

  // This is a non-product method that is helpful for testing. It is
  // called at the end of a GC and artificially expands the heap by
  // allocating a number of dead regions. This way we can induce very
  // frequent marking cycles and stress the cleanup / concurrent
  // cleanup code more (as all the regions that will be allocated by
  // this method will be found dead by the marking cycle).
  void allocate_dummy_regions() PRODUCT_RETURN;

  // Clear RSets after a compaction. It also resets the GC time stamps.
  void clear_rsets_post_compaction();

  // If the HR printer is active, dump the state of the regions in the
  // heap after a compaction.
  void print_hrs_post_compaction();

  double verify(bool guard, const char* msg);
  void verify_before_gc();
  void verify_after_gc();

  void log_gc_header();
  void log_gc_footer(double pause_time_sec);

  // These are macros so that, if the assert fires, we get the correct
  // line number, file, etc.

#define heap_locking_asserts_err_msg(_extra_message_)                         \
  err_msg("%s : Heap_lock locked: %s, at safepoint: %s, is VM thread: %s",    \
          (_extra_message_),                                                  \
          BOOL_TO_STR(Heap_lock->owned_by_self()),                            \
          BOOL_TO_STR(SafepointSynchronize::is_at_safepoint()),               \
          BOOL_TO_STR(Thread::current()->is_VM_thread()))

#define assert_heap_locked()                                                  \
  do {                                                                        \
    assert(Heap_lock->owned_by_self(),                                        \
           heap_locking_asserts_err_msg("should be holding the Heap_lock"));  \
  } while (0)

#define assert_heap_locked_or_at_safepoint(_should_be_vm_thread_)             \
  do {                                                                        \
    assert(Heap_lock->owned_by_self() ||                                      \
           (SafepointSynchronize::is_at_safepoint() &&                        \
             ((_should_be_vm_thread_) == Thread::current()->is_VM_thread())), \
           heap_locking_asserts_err_msg("should be holding the Heap_lock or " \
                                        "should be at a safepoint"));         \
  } while (0)

#define assert_heap_locked_and_not_at_safepoint()                             \
  do {                                                                        \
    assert(Heap_lock->owned_by_self() &&                                      \
                                    !SafepointSynchronize::is_at_safepoint(), \
          heap_locking_asserts_err_msg("should be holding the Heap_lock and " \
                                       "should not be at a safepoint"));      \
  } while (0)

#define assert_heap_not_locked()                                              \
  do {                                                                        \
    assert(!Heap_lock->owned_by_self(),                                       \
        heap_locking_asserts_err_msg("should not be holding the Heap_lock")); \
  } while (0)

#define assert_heap_not_locked_and_not_at_safepoint()                         \
  do {                                                                        \
    assert(!Heap_lock->owned_by_self() &&                                     \
                                    !SafepointSynchronize::is_at_safepoint(), \
      heap_locking_asserts_err_msg("should not be holding the Heap_lock and " \
                                   "should not be at a safepoint"));          \
  } while (0)

#define assert_at_safepoint(_should_be_vm_thread_)                            \
  do {                                                                        \
    assert(SafepointSynchronize::is_at_safepoint() &&                         \
              ((_should_be_vm_thread_) == Thread::current()->is_VM_thread()), \
           heap_locking_asserts_err_msg("should be at a safepoint"));         \
  } while (0)

#define assert_not_at_safepoint()                                             \
  do {                                                                        \
    assert(!SafepointSynchronize::is_at_safepoint(),                          \
           heap_locking_asserts_err_msg("should not be at a safepoint"));     \
  } while (0)

protected:

  // The young region list.
  YoungList*  _young_list;

  // The current policy object for the collector.
  G1CollectorPolicy* _g1_policy;

  // This is the second level of trying to allocate a new region. If
  // new_region() didn't find a region on the free_list, this call will
  // check whether there's anything available on the
  // secondary_free_list and/or wait for more regions to appear on
  // that list, if _free_regions_coming is set.
  HeapRegion* new_region_try_secondary_free_list();

  // Try to allocate a single non-humongous HeapRegion sufficient for
  // an allocation of the given word_size. If do_expand is true,
  // attempt to expand the heap if necessary to satisfy the allocation
  // request.
  HeapRegion* new_region(size_t word_size, bool do_expand);

  // Attempt to satisfy a humongous allocation request of the given
  // size by finding a contiguous set of free regions of num_regions
  // length and remove them from the master free list. Return the
  // index of the first region or G1_NULL_HRS_INDEX if the search
  // was unsuccessful.
  uint humongous_obj_allocate_find_first(uint num_regions,
                                         size_t word_size);

  // Initialize a contiguous set of free regions of length num_regions
  // and starting at index first so that they appear as a single
  // humongous region.
  HeapWord* humongous_obj_allocate_initialize_regions(uint first,
                                                      uint num_regions,
                                                      size_t word_size);

  // Attempt to allocate a humongous object of the given size. Return
  // NULL if unsuccessful.
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
  //   this fails, they will attempt to do an evacuation pause and
  //   retry the allocation.
  //
  // * If all allocation attempts fail, even after trying to schedule
  //   an evacuation pause, allocate_new_tlab() will return NULL,
  //   whereas mem_allocate() will attempt a heap expansion and/or
  //   schedule a Full GC.
  //
  // * We do not allow humongous-sized TLABs. So, allocate_new_tlab
  //   should never be called with word_size being humongous. All
  //   humongous allocation requests should go to mem_allocate() which
  //   will satisfy them with a special path.

  virtual HeapWord* allocate_new_tlab(size_t word_size);

  virtual HeapWord* mem_allocate(size_t word_size,
                                 bool*  gc_overhead_limit_was_exceeded);

  // The following three methods take a gc_count_before_ret
  // parameter which is used to return the GC count if the method
  // returns NULL. Given that we are required to read the GC count
  // while holding the Heap_lock, and these paths will take the
  // Heap_lock at some point, it's easier to get them to read the GC
  // count while holding the Heap_lock before they return NULL instead
  // of the caller (namely: mem_allocate()) having to also take the
  // Heap_lock just to read the GC count.

  // First-level mutator allocation attempt: try to allocate out of
  // the mutator alloc region without taking the Heap_lock. This
  // should only be used for non-humongous allocations.
  inline HeapWord* attempt_allocation(size_t word_size,
                                      unsigned int* gc_count_before_ret,
                                      int* gclocker_retry_count_ret);

  // Second-level mutator allocation attempt: take the Heap_lock and
  // retry the allocation attempt, potentially scheduling a GC
  // pause. This should only be used for non-humongous allocations.
  HeapWord* attempt_allocation_slow(size_t word_size,
                                    unsigned int* gc_count_before_ret,
                                    int* gclocker_retry_count_ret);

  // Takes the Heap_lock and attempts a humongous allocation. It can
  // potentially schedule a GC pause.
  HeapWord* attempt_allocation_humongous(size_t word_size,
                                         unsigned int* gc_count_before_ret,
                                         int* gclocker_retry_count_ret);

  // Allocation attempt that should be called during safepoints (e.g.,
  // at the end of a successful GC). expect_null_mutator_alloc_region
  // specifies whether the mutator alloc region is expected to be NULL
  // or not.
  HeapWord* attempt_allocation_at_safepoint(size_t word_size,
                                       bool expect_null_mutator_alloc_region);

  // It dirties the cards that cover the block so that so that the post
  // write barrier never queues anything when updating objects on this
  // block. It is assumed (and in fact we assert) that the block
  // belongs to a young region.
  inline void dirty_young_block(HeapWord* start, size_t word_size);

  // Allocate blocks during garbage collection. Will ensure an
  // allocation region, either by picking one or expanding the
  // heap, and then allocate a block of the given size. The block
  // may not be a humongous - it must fit into a single heap region.
  HeapWord* par_allocate_during_gc(GCAllocPurpose purpose, size_t word_size);

  // Ensure that no further allocations can happen in "r", bearing in mind
  // that parallel threads might be attempting allocations.
  void par_allocate_remaining_space(HeapRegion* r);

  // Allocation attempt during GC for a survivor object / PLAB.
  inline HeapWord* survivor_attempt_allocation(size_t word_size);

  // Allocation attempt during GC for an old object / PLAB.
  inline HeapWord* old_attempt_allocation(size_t word_size);

  // These methods are the "callbacks" from the G1AllocRegion class.

  // For mutator alloc regions.
  HeapRegion* new_mutator_alloc_region(size_t word_size, bool force);
  void retire_mutator_alloc_region(HeapRegion* alloc_region,
                                   size_t allocated_bytes);

  // For GC alloc regions.
  HeapRegion* new_gc_alloc_region(size_t word_size, uint count,
                                  GCAllocPurpose ap);
  void retire_gc_alloc_region(HeapRegion* alloc_region,
                              size_t allocated_bytes, GCAllocPurpose ap);

  // - if explicit_gc is true, the GC is for a System.gc() or a heap
  //   inspection request and should collect the entire heap
  // - if clear_all_soft_refs is true, all soft references should be
  //   cleared during the GC
  // - if explicit_gc is false, word_size describes the allocation that
  //   the GC should attempt (at least) to satisfy
  // - it returns false if it is unable to do the collection due to the
  //   GC locker being active, true otherwise
  bool do_collection(bool explicit_gc,
                     bool clear_all_soft_refs,
                     size_t word_size);

  // Callback from VM_G1CollectFull operation.
  // Perform a full collection.
  virtual void do_full_collection(bool clear_all_soft_refs);

  // Resize the heap if necessary after a full collection.  If this is
  // after a collect-for allocation, "word_size" is the allocation size,
  // and will be considered part of the used portion of the heap.
  void resize_if_necessary_after_full_collection(size_t word_size);

  // Callback from VM_G1CollectForAllocation operation.
  // This function does everything necessary/possible to satisfy a
  // failed allocation request (including collection, expansion, etc.)
  HeapWord* satisfy_failed_allocation(size_t word_size, bool* succeeded);

  // Attempting to expand the heap sufficiently
  // to support an allocation of the given "word_size".  If
  // successful, perform the allocation and return the address of the
  // allocated block, or else "NULL".
  HeapWord* expand_and_allocate(size_t word_size);

  // Process any reference objects discovered during
  // an incremental evacuation pause.
  void process_discovered_references(uint no_of_gc_workers);

  // Enqueue any remaining discovered references
  // after processing.
  void enqueue_discovered_references(uint no_of_gc_workers);

public:

  G1MonitoringSupport* g1mm() {
    assert(_g1mm != NULL, "should have been initialized");
    return _g1mm;
  }

  // Expand the garbage-first heap by at least the given size (in bytes!).
  // Returns true if the heap was expanded by the requested amount;
  // false otherwise.
  // (Rounds up to a HeapRegion boundary.)
  bool expand(size_t expand_bytes);

  // Do anything common to GC's.
  virtual void gc_prologue(bool full);
  virtual void gc_epilogue(bool full);

  // We register a region with the fast "in collection set" test. We
  // simply set to true the array slot corresponding to this region.
  void register_region_with_in_cset_fast_test(HeapRegion* r) {
    assert(_in_cset_fast_test_base != NULL, "sanity");
    assert(r->in_collection_set(), "invariant");
    uint index = r->hrs_index();
    assert(index < _in_cset_fast_test_length, "invariant");
    assert(!_in_cset_fast_test_base[index], "invariant");
    _in_cset_fast_test_base[index] = true;
  }

  // This is a fast test on whether a reference points into the
  // collection set or not. It does not assume that the reference
  // points into the heap; if it doesn't, it will return false.
  bool in_cset_fast_test(oop obj) {
    assert(_in_cset_fast_test != NULL, "sanity");
    if (_g1_committed.contains((HeapWord*) obj)) {
      // no need to subtract the bottom of the heap from obj,
      // _in_cset_fast_test is biased
      uintx index = (uintx) obj >> HeapRegion::LogOfHRGrainBytes;
      bool ret = _in_cset_fast_test[index];
      // let's make sure the result is consistent with what the slower
      // test returns
      assert( ret || !obj_in_cs(obj), "sanity");
      assert(!ret ||  obj_in_cs(obj), "sanity");
      return ret;
    } else {
      return false;
    }
  }

  void clear_cset_fast_test() {
    assert(_in_cset_fast_test_base != NULL, "sanity");
    memset(_in_cset_fast_test_base, false,
           (size_t) _in_cset_fast_test_length * sizeof(bool));
  }

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
  // the FullGCCount_lock in case a Java thread is waiting for a full
  // GC to happen (e.g., it called System.gc() with
  // +ExplicitGCInvokesConcurrent).
  void increment_old_marking_cycles_completed(bool concurrent);

  unsigned int old_marking_cycles_completed() {
    return _old_marking_cycles_completed;
  }

  void register_concurrent_cycle_start(jlong start_time);
  void register_concurrent_cycle_end();
  void trace_heap_after_concurrent_cycle();

  G1YCType yc_type();

  G1HRPrinter* hr_printer() { return &_hr_printer; }

protected:

  // Shrink the garbage-first heap by at most the given size (in bytes!).
  // (Rounds down to a HeapRegion boundary.)
  virtual void shrink(size_t expand_bytes);
  void shrink_helper(size_t expand_bytes);

  #if TASKQUEUE_STATS
  static void print_taskqueue_stats_hdr(outputStream* const st = gclog_or_tty);
  void print_taskqueue_stats(outputStream* const st = gclog_or_tty) const;
  void reset_taskqueue_stats();
  #endif // TASKQUEUE_STATS

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
  HeapWord* do_collection_pause(size_t       word_size,
                                unsigned int gc_count_before,
                                bool*        succeeded);

  // The guts of the incremental collection pause, executed by the vm
  // thread. It returns false if it is unable to do the collection due
  // to the GC locker being active, true otherwise
  bool do_collection_pause_at_safepoint(double target_pause_time_ms);

  // Actually do the work of evacuating the collection set.
  void evacuate_collection_set(EvacuationInfo& evacuation_info);

  // The g1 remembered set of the heap.
  G1RemSet* _g1_rem_set;
  // And it's mod ref barrier set, used to track updates for the above.
  ModRefBarrierSet* _mr_bs;

  // A set of cards that cover the objects for which the Rsets should be updated
  // concurrently after the collection.
  DirtyCardQueueSet _dirty_card_queue_set;

  // The closure used to refine a single card.
  RefineCardTableEntryClosure* _refine_cte_cl;

  // A function to check the consistency of dirty card logs.
  void check_ct_logs_at_safepoint();

  // A DirtyCardQueueSet that is used to hold cards that contain
  // references into the current collection set. This is used to
  // update the remembered sets of the regions in the collection
  // set in the event of an evacuation failure.
  DirtyCardQueueSet _into_cset_dirty_card_queue_set;

  // After a collection pause, make the regions in the CS into free
  // regions.
  void free_collection_set(HeapRegion* cs_head, EvacuationInfo& evacuation_info);

  // Abandon the current collection set without recording policy
  // statistics or updating free lists.
  void abandon_collection_set(HeapRegion* cs_head);

  // Applies "scan_non_heap_roots" to roots outside the heap,
  // "scan_rs" to roots inside the heap (having done "set_region" to
  // indicate the region in which the root resides),
  // and does "scan_metadata" If "scan_rs" is
  // NULL, then this step is skipped.  The "worker_i"
  // param is for use with parallel roots processing, and should be
  // the "i" of the calling parallel worker thread's work(i) function.
  // In the sequential case this param will be ignored.
  void g1_process_strong_roots(bool is_scavenging,
                               ScanningOption so,
                               OopClosure* scan_non_heap_roots,
                               OopsInHeapRegionClosure* scan_rs,
                               G1KlassScanClosure* scan_klasses,
                               int worker_i);

  // Apply "blk" to all the weak roots of the system.  These include
  // JNI weak roots, the code cache, system dictionary, symbol table,
  // string table, and referents of reachable weak refs.
  void g1_process_weak_roots(OopClosure* root_closure);

  // Frees a non-humongous region by initializing its contents and
  // adding it to the free list that's passed as a parameter (this is
  // usually a local list which will be appended to the master free
  // list later). The used bytes of freed regions are accumulated in
  // pre_used. If par is true, the region's RSet will not be freed
  // up. The assumption is that this will be done later.
  void free_region(HeapRegion* hr,
                   size_t* pre_used,
                   FreeRegionList* free_list,
                   bool par);

  // Frees a humongous region by collapsing it into individual regions
  // and calling free_region() for each of them. The freed regions
  // will be added to the free list that's passed as a parameter (this
  // is usually a local list which will be appended to the master free
  // list later). The used bytes of freed regions are accumulated in
  // pre_used. If par is true, the region's RSet will not be freed
  // up. The assumption is that this will be done later.
  void free_humongous_region(HeapRegion* hr,
                             size_t* pre_used,
                             FreeRegionList* free_list,
                             HumongousRegionSet* humongous_proxy_set,
                             bool par);

  // Notifies all the necessary spaces that the committed space has
  // been updated (either expanded or shrunk). It should be called
  // after _g1_storage is updated.
  void update_committed_space(HeapWord* old_end, HeapWord* new_end);

  // The concurrent marker (and the thread it runs in.)
  ConcurrentMark* _cm;
  ConcurrentMarkThread* _cmThread;
  bool _mark_in_progress;

  // The concurrent refiner.
  ConcurrentG1Refine* _cg1r;

  // The parallel task queues
  RefToScanQueueSet *_task_queues;

  // True iff a evacuation has failed in the current collection.
  bool _evacuation_failed;

  EvacuationFailedInfo* _evacuation_failed_info_array;

  // Failed evacuations cause some logical from-space objects to have
  // forwarding pointers to themselves.  Reset them.
  void remove_self_forwarding_pointers();

  // Together, these store an object with a preserved mark, and its mark value.
  Stack<oop, mtGC>     _objs_with_preserved_marks;
  Stack<markOop, mtGC> _preserved_marks_of_objs;

  // Preserve the mark of "obj", if necessary, in preparation for its mark
  // word being overwritten with a self-forwarding-pointer.
  void preserve_mark_if_necessary(oop obj, markOop m);

  // The stack of evac-failure objects left to be scanned.
  GrowableArray<oop>*    _evac_failure_scan_stack;
  // The closure to apply to evac-failure objects.

  OopsInHeapRegionClosure* _evac_failure_closure;
  // Set the field above.
  void
  set_evac_failure_closure(OopsInHeapRegionClosure* evac_failure_closure) {
    _evac_failure_closure = evac_failure_closure;
  }

  // Push "obj" on the scan stack.
  void push_on_evac_failure_scan_stack(oop obj);
  // Process scan stack entries until the stack is empty.
  void drain_evac_failure_scan_stack();
  // True iff an invocation of "drain_scan_stack" is in progress; to
  // prevent unnecessary recursion.
  bool _drain_in_progress;

  // Do any necessary initialization for evacuation-failure handling.
  // "cl" is the closure that will be used to process evac-failure
  // objects.
  void init_for_evac_failure(OopsInHeapRegionClosure* cl);
  // Do any necessary cleanup for evacuation-failure handling data
  // structures.
  void finalize_for_evac_failure();

  // An attempt to evacuate "obj" has failed; take necessary steps.
  oop handle_evacuation_failure_par(G1ParScanThreadState* _par_scan_state, oop obj);
  void handle_evacuation_failure_common(oop obj, markOop m);

#ifndef PRODUCT
  // Support for forcing evacuation failures. Analogous to
  // PromotionFailureALot for the other collectors.

  // Records whether G1EvacuationFailureALot should be in effect
  // for the current GC
  bool _evacuation_failure_alot_for_current_gc;

  // Used to record the GC number for interval checking when
  // determining whether G1EvaucationFailureALot is in effect
  // for the current GC.
  size_t _evacuation_failure_alot_gc_number;

  // Count of the number of evacuations between failures.
  volatile size_t _evacuation_failure_alot_count;

  // Set whether G1EvacuationFailureALot should be in effect
  // for the current GC (based upon the type of GC and which
  // command line flags are set);
  inline bool evacuation_failure_alot_for_gc_type(bool gcs_are_young,
                                                  bool during_initial_mark,
                                                  bool during_marking);

  inline void set_evacuation_failure_alot_for_current_gc();

  // Return true if it's time to cause an evacuation failure.
  inline bool evacuation_should_fail();

  // Reset the G1EvacuationFailureALot counters.  Should be called at
  // the end of an evacuation pause in which an evacuation failure occurred.
  inline void reset_evacuation_should_fail();
#endif // !PRODUCT

  // ("Weak") Reference processing support.
  //
  // G1 has 2 instances of the reference processor class. One
  // (_ref_processor_cm) handles reference object discovery
  // and subsequent processing during concurrent marking cycles.
  //
  // The other (_ref_processor_stw) handles reference object
  // discovery and processing during full GCs and incremental
  // evacuation pauses.
  //
  // During an incremental pause, reference discovery will be
  // temporarily disabled for _ref_processor_cm and will be
  // enabled for _ref_processor_stw. At the end of the evacuation
  // pause references discovered by _ref_processor_stw will be
  // processed and discovery will be disabled. The previous
  // setting for reference object discovery for _ref_processor_cm
  // will be re-instated.
  //
  // At the start of marking:
  //  * Discovery by the CM ref processor is verified to be inactive
  //    and it's discovered lists are empty.
  //  * Discovery by the CM ref processor is then enabled.
  //
  // At the end of marking:
  //  * Any references on the CM ref processor's discovered
  //    lists are processed (possibly MT).
  //
  // At the start of full GC we:
  //  * Disable discovery by the CM ref processor and
  //    empty CM ref processor's discovered lists
  //    (without processing any entries).
  //  * Verify that the STW ref processor is inactive and it's
  //    discovered lists are empty.
  //  * Temporarily set STW ref processor discovery as single threaded.
  //  * Temporarily clear the STW ref processor's _is_alive_non_header
  //    field.
  //  * Finally enable discovery by the STW ref processor.
  //
  // The STW ref processor is used to record any discovered
  // references during the full GC.
  //
  // At the end of a full GC we:
  //  * Enqueue any reference objects discovered by the STW ref processor
  //    that have non-live referents. This has the side-effect of
  //    making the STW ref processor inactive by disabling discovery.
  //  * Verify that the CM ref processor is still inactive
  //    and no references have been placed on it's discovered
  //    lists (also checked as a precondition during initial marking).

  // The (stw) reference processor...
  ReferenceProcessor* _ref_processor_stw;

  STWGCTimer* _gc_timer_stw;
  ConcurrentGCTimer* _gc_timer_cm;

  G1OldTracer* _gc_tracer_cm;
  G1NewTracer* _gc_tracer_stw;

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

  // The (concurrent marking) reference processor...
  ReferenceProcessor* _ref_processor_cm;

  // Instance of the concurrent mark is_alive closure for embedding
  // into the Concurrent Marking reference processor as the
  // _is_alive_non_header field. Supplying a value for the
  // _is_alive_non_header field is optional but doing so prevents
  // unnecessary additions to the discovered lists during reference
  // discovery.
  G1CMIsAliveClosure _is_alive_closure_cm;

  // Cache used by G1CollectedHeap::start_cset_region_for_worker().
  HeapRegion** _worker_cset_start_region;

  // Time stamp to validate the regions recorded in the cache
  // used by G1CollectedHeap::start_cset_region_for_worker().
  // The heap region entry for a given worker is valid iff
  // the associated time stamp value matches the current value
  // of G1CollectedHeap::_gc_time_stamp.
  unsigned int* _worker_cset_start_region_time_stamp;

  enum G1H_process_strong_roots_tasks {
    G1H_PS_filter_satb_buffers,
    G1H_PS_refProcessor_oops_do,
    // Leave this one last.
    G1H_PS_NumElements
  };

  SubTasksDone* _process_strong_tasks;

  volatile bool _free_regions_coming;

public:

  SubTasksDone* process_strong_tasks() { return _process_strong_tasks; }

  void set_refine_cte_cl_concurrency(bool concurrent);

  RefToScanQueue *task_queue(int i) const;

  // A set of cards where updates happened during the GC
  DirtyCardQueueSet& dirty_card_queue_set() { return _dirty_card_queue_set; }

  // A DirtyCardQueueSet that is used to hold cards that contain
  // references into the current collection set. This is used to
  // update the remembered sets of the regions in the collection
  // set in the event of an evacuation failure.
  DirtyCardQueueSet& into_cset_dirty_card_queue_set()
        { return _into_cset_dirty_card_queue_set; }

  // Create a G1CollectedHeap with the specified policy.
  // Must call the initialize method afterwards.
  // May not return if something goes wrong.
  G1CollectedHeap(G1CollectorPolicy* policy);

  // Initialize the G1CollectedHeap to have the initial and
  // maximum sizes and remembered and barrier sets
  // specified by the policy object.
  jint initialize();

  // Initialize weak reference processing.
  virtual void ref_processing_init();

  void set_par_threads(uint t) {
    SharedHeap::set_par_threads(t);
    // Done in SharedHeap but oddly there are
    // two _process_strong_tasks's in a G1CollectedHeap
    // so do it here too.
    _process_strong_tasks->set_n_threads(t);
  }

  // Set _n_par_threads according to a policy TBD.
  void set_par_threads();

  void set_n_termination(int t) {
    _process_strong_tasks->set_n_threads(t);
  }

  virtual CollectedHeap::Name kind() const {
    return CollectedHeap::G1CollectedHeap;
  }

  // The current policy object for the collector.
  G1CollectorPolicy* g1_policy() const { return _g1_policy; }

  virtual CollectorPolicy* collector_policy() const { return (CollectorPolicy*) g1_policy(); }

  // Adaptive size policy.  No such thing for g1.
  virtual AdaptiveSizePolicy* size_policy() { return NULL; }

  // The rem set and barrier set.
  G1RemSet* g1_rem_set() const { return _g1_rem_set; }
  ModRefBarrierSet* mr_bs() const { return _mr_bs; }

  unsigned get_gc_time_stamp() {
    return _gc_time_stamp;
  }

  void reset_gc_time_stamp() {
    _gc_time_stamp = 0;
    OrderAccess::fence();
    // Clear the cached CSet starting regions and time stamps.
    // Their validity is dependent on the GC timestamp.
    clear_cset_start_regions();
  }

  void check_gc_time_stamps() PRODUCT_RETURN;

  void increment_gc_time_stamp() {
    ++_gc_time_stamp;
    OrderAccess::fence();
  }

  // Reset the given region's GC timestamp. If it's starts humongous,
  // also reset the GC timestamp of its corresponding
  // continues humongous regions too.
  void reset_gc_time_stamps(HeapRegion* hr);

  void iterate_dirty_card_closure(CardTableEntryClosure* cl,
                                  DirtyCardQueue* into_cset_dcq,
                                  bool concurrent, int worker_i);

  // The shared block offset table array.
  G1BlockOffsetSharedArray* bot_shared() const { return _bot_shared; }

  // Reference Processing accessors

  // The STW reference processor....
  ReferenceProcessor* ref_processor_stw() const { return _ref_processor_stw; }

  // The Concurrent Marking reference processor...
  ReferenceProcessor* ref_processor_cm() const { return _ref_processor_cm; }

  ConcurrentGCTimer* gc_timer_cm() const { return _gc_timer_cm; }
  G1OldTracer* gc_tracer_cm() const { return _gc_tracer_cm; }

  virtual size_t capacity() const;
  virtual size_t used() const;
  // This should be called when we're not holding the heap lock. The
  // result might be a bit inaccurate.
  size_t used_unlocked() const;
  size_t recalculate_used() const;

  // These virtual functions do the actual allocation.
  // Some heaps may offer a contiguous region for shared non-blocking
  // allocation, via inlined code (by exporting the address of the top and
  // end fields defining the extent of the contiguous allocation region.)
  // But G1CollectedHeap doesn't yet support this.

  // Return an estimate of the maximum allocation that could be performed
  // without triggering any collection or expansion activity.  In a
  // generational collector, for example, this is probably the largest
  // allocation that could be supported (without expansion) in the youngest
  // generation.  It is "unsafe" because no locks are taken; the result
  // should be treated as an approximation, not a guarantee, for use in
  // heuristic resizing decisions.
  virtual size_t unsafe_max_alloc();

  virtual bool is_maximal_no_gc() const {
    return _g1_storage.uncommitted_size() == 0;
  }

  // The total number of regions in the heap.
  uint n_regions() { return _hrs.length(); }

  // The max number of regions in the heap.
  uint max_regions() { return _hrs.max_length(); }

  // The number of regions that are completely free.
  uint free_regions() { return _free_list.length(); }

  // The number of regions that are not completely free.
  uint used_regions() { return n_regions() - free_regions(); }

  // The number of regions available for "regular" expansion.
  uint expansion_regions() { return _expansion_regions; }

  // Factory method for HeapRegion instances. It will return NULL if
  // the allocation fails.
  HeapRegion* new_heap_region(uint hrs_index, HeapWord* bottom);

  void verify_not_dirty_region(HeapRegion* hr) PRODUCT_RETURN;
  void verify_dirty_region(HeapRegion* hr) PRODUCT_RETURN;
  void verify_dirty_young_list(HeapRegion* head) PRODUCT_RETURN;
  void verify_dirty_young_regions() PRODUCT_RETURN;

  // verify_region_sets() performs verification over the region
  // lists. It will be compiled in the product code to be used when
  // necessary (i.e., during heap verification).
  void verify_region_sets();

  // verify_region_sets_optional() is planted in the code for
  // list verification in non-product builds (and it can be enabled in
  // product builds by defining HEAP_REGION_SET_FORCE_VERIFY to be 1).
#if HEAP_REGION_SET_FORCE_VERIFY
  void verify_region_sets_optional() {
    verify_region_sets();
  }
#else // HEAP_REGION_SET_FORCE_VERIFY
  void verify_region_sets_optional() { }
#endif // HEAP_REGION_SET_FORCE_VERIFY

#ifdef ASSERT
  bool is_on_master_free_list(HeapRegion* hr) {
    return hr->containing_set() == &_free_list;
  }

  bool is_in_humongous_set(HeapRegion* hr) {
    return hr->containing_set() == &_humongous_set;
  }
#endif // ASSERT

  // Wrapper for the region list operations that can be called from
  // methods outside this class.

  void secondary_free_list_add_as_tail(FreeRegionList* list) {
    _secondary_free_list.add_as_tail(list);
  }

  void append_secondary_free_list() {
    _free_list.add_as_head(&_secondary_free_list);
  }

  void append_secondary_free_list_if_not_empty_with_lock() {
    // If the secondary free list looks empty there's no reason to
    // take the lock and then try to append it.
    if (!_secondary_free_list.is_empty()) {
      MutexLockerEx x(SecondaryFreeList_lock, Mutex::_no_safepoint_check_flag);
      append_secondary_free_list();
    }
  }

  void old_set_remove(HeapRegion* hr) {
    _old_set.remove(hr);
  }

  size_t non_young_capacity_bytes() {
    return _old_set.total_capacity_bytes() + _humongous_set.total_capacity_bytes();
  }

  void set_free_regions_coming();
  void reset_free_regions_coming();
  bool free_regions_coming() { return _free_regions_coming; }
  void wait_while_free_regions_coming();

  // Determine whether the given region is one that we are using as an
  // old GC alloc region.
  bool is_old_gc_alloc_region(HeapRegion* hr) {
    return hr == _retained_old_gc_alloc_region;
  }

  // Perform a collection of the heap; intended for use in implementing
  // "System.gc".  This probably implies as full a collection as the
  // "CollectedHeap" supports.
  virtual void collect(GCCause::Cause cause);

  // The same as above but assume that the caller holds the Heap_lock.
  void collect_locked(GCCause::Cause cause);

  // True iff an evacuation has failed in the most-recent collection.
  bool evacuation_failed() { return _evacuation_failed; }

  // It will free a region if it has allocated objects in it that are
  // all dead. It calls either free_region() or
  // free_humongous_region() depending on the type of the region that
  // is passed to it.
  void free_region_if_empty(HeapRegion* hr,
                            size_t* pre_used,
                            FreeRegionList* free_list,
                            OldRegionSet* old_proxy_set,
                            HumongousRegionSet* humongous_proxy_set,
                            HRRSCleanupTask* hrrs_cleanup_task,
                            bool par);

  // It appends the free list to the master free list and updates the
  // master humongous list according to the contents of the proxy
  // list. It also adjusts the total used bytes according to pre_used
  // (if par is true, it will do so by taking the ParGCRareEvent_lock).
  void update_sets_after_freeing_regions(size_t pre_used,
                                       FreeRegionList* free_list,
                                       OldRegionSet* old_proxy_set,
                                       HumongousRegionSet* humongous_proxy_set,
                                       bool par);

  // Returns "TRUE" iff "p" points into the committed areas of the heap.
  virtual bool is_in(const void* p) const;

  // Return "TRUE" iff the given object address is within the collection
  // set.
  inline bool obj_in_cs(oop obj);

  // Return "TRUE" iff the given object address is in the reserved
  // region of g1.
  bool is_in_g1_reserved(const void* p) const {
    return _g1_reserved.contains(p);
  }

  // Returns a MemRegion that corresponds to the space that has been
  // reserved for the heap
  MemRegion g1_reserved() {
    return _g1_reserved;
  }

  // Returns a MemRegion that corresponds to the space that has been
  // committed in the heap
  MemRegion g1_committed() {
    return _g1_committed;
  }

  virtual bool is_in_closed_subset(const void* p) const;

  // This resets the card table to all zeros.  It is used after
  // a collection pause which used the card table to claim cards.
  void cleanUpCardTable();

  // Iteration functions.

  // Iterate over all the ref-containing fields of all objects, calling
  // "cl.do_oop" on each.
  virtual void oop_iterate(ExtendedOopClosure* cl);

  // Same as above, restricted to a memory region.
  void oop_iterate(MemRegion mr, ExtendedOopClosure* cl);

  // Iterate over all objects, calling "cl.do_object" on each.
  virtual void object_iterate(ObjectClosure* cl);

  virtual void safe_object_iterate(ObjectClosure* cl) {
    object_iterate(cl);
  }

  // Iterate over all objects allocated since the last collection, calling
  // "cl.do_object" on each.  The heap must have been initialized properly
  // to support this function, or else this call will fail.
  virtual void object_iterate_since_last_GC(ObjectClosure* cl);

  // Iterate over all spaces in use in the heap, in ascending address order.
  virtual void space_iterate(SpaceClosure* cl);

  // Iterate over heap regions, in address order, terminating the
  // iteration early if the "doHeapRegion" method returns "true".
  void heap_region_iterate(HeapRegionClosure* blk) const;

  // Return the region with the given index. It assumes the index is valid.
  HeapRegion* region_at(uint index) const { return _hrs.at(index); }

  // Divide the heap region sequence into "chunks" of some size (the number
  // of regions divided by the number of parallel threads times some
  // overpartition factor, currently 4).  Assumes that this will be called
  // in parallel by ParallelGCThreads worker threads with discinct worker
  // ids in the range [0..max(ParallelGCThreads-1, 1)], that all parallel
  // calls will use the same "claim_value", and that that claim value is
  // different from the claim_value of any heap region before the start of
  // the iteration.  Applies "blk->doHeapRegion" to each of the regions, by
  // attempting to claim the first region in each chunk, and, if
  // successful, applying the closure to each region in the chunk (and
  // setting the claim value of the second and subsequent regions of the
  // chunk.)  For now requires that "doHeapRegion" always returns "false",
  // i.e., that a closure never attempt to abort a traversal.
  void heap_region_par_iterate_chunked(HeapRegionClosure* blk,
                                       uint worker,
                                       uint no_of_par_workers,
                                       jint claim_value);

  // It resets all the region claim values to the default.
  void reset_heap_region_claim_values();

  // Resets the claim values of regions in the current
  // collection set to the default.
  void reset_cset_heap_region_claim_values();

#ifdef ASSERT
  bool check_heap_region_claim_values(jint claim_value);

  // Same as the routine above but only checks regions in the
  // current collection set.
  bool check_cset_heap_region_claim_values(jint claim_value);
#endif // ASSERT

  // Clear the cached cset start regions and (more importantly)
  // the time stamps. Called when we reset the GC time stamp.
  void clear_cset_start_regions();

  // Given the id of a worker, obtain or calculate a suitable
  // starting region for iterating over the current collection set.
  HeapRegion* start_cset_region_for_worker(int worker_i);

  // This is a convenience method that is used by the
  // HeapRegionIterator classes to calculate the starting region for
  // each worker so that they do not all start from the same region.
  HeapRegion* start_region_for_worker(uint worker_i, uint no_of_par_workers);

  // Iterate over the regions (if any) in the current collection set.
  void collection_set_iterate(HeapRegionClosure* blk);

  // As above but starting from region r
  void collection_set_iterate_from(HeapRegion* r, HeapRegionClosure *blk);

  // Returns the first (lowest address) compactible space in the heap.
  virtual CompactibleSpace* first_compactible_space();

  // A CollectedHeap will contain some number of spaces.  This finds the
  // space containing a given address, or else returns NULL.
  virtual Space* space_containing(const void* addr) const;

  // A G1CollectedHeap will contain some number of heap regions.  This
  // finds the region containing a given address, or else returns NULL.
  template <class T>
  inline HeapRegion* heap_region_containing(const T addr) const;

  // Like the above, but requires "addr" to be in the heap (to avoid a
  // null-check), and unlike the above, may return an continuing humongous
  // region.
  template <class T>
  inline HeapRegion* heap_region_containing_raw(const T addr) const;

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
  virtual HeapWord* block_start(const void* addr) const;

  // Requires "addr" to be the start of a chunk, and returns its size.
  // "addr + size" is required to be the start of a new chunk, or the end
  // of the active area of the heap.
  virtual size_t block_size(const HeapWord* addr) const;

  // Requires "addr" to be the start of a block, and returns "TRUE" iff
  // the block is an object.
  virtual bool block_is_obj(const HeapWord* addr) const;

  // Does this heap support heap inspection? (+PrintClassHistogram)
  virtual bool supports_heap_inspection() const { return true; }

  // Section on thread-local allocation buffers (TLABs)
  // See CollectedHeap for semantics.

  virtual bool supports_tlab_allocation() const;
  virtual size_t tlab_capacity(Thread* thr) const;
  virtual size_t unsafe_max_tlab_alloc(Thread* thr) const;

  // Can a compiler initialize a new object without store barriers?
  // This permission only extends from the creation of a new object
  // via a TLAB up to the first subsequent safepoint. If such permission
  // is granted for this heap type, the compiler promises to call
  // defer_store_barrier() below on any slow path allocation of
  // a new object for which such initializing store barriers will
  // have been elided. G1, like CMS, allows this, but should be
  // ready to provide a compensating write barrier as necessary
  // if that storage came out of a non-young region. The efficiency
  // of this implementation depends crucially on being able to
  // answer very efficiently in constant time whether a piece of
  // storage in the heap comes from a young region or not.
  // See ReduceInitialCardMarks.
  virtual bool can_elide_tlab_store_barriers() const {
    return true;
  }

  virtual bool card_mark_must_follow_store() const {
    return true;
  }

  bool is_in_young(const oop obj) {
    HeapRegion* hr = heap_region_containing(obj);
    return hr != NULL && hr->is_young();
  }

#ifdef ASSERT
  virtual bool is_in_partial_collection(const void* p);
#endif

  virtual bool is_scavengable(const void* addr);

  // We don't need barriers for initializing stores to objects
  // in the young gen: for the SATB pre-barrier, there is no
  // pre-value that needs to be remembered; for the remembered-set
  // update logging post-barrier, we don't maintain remembered set
  // information for young gen objects.
  virtual bool can_elide_initializing_store_barrier(oop new_obj) {
    return is_in_young(new_obj);
  }

  // Returns "true" iff the given word_size is "very large".
  static bool isHumongous(size_t word_size) {
    // Note this has to be strictly greater-than as the TLABs
    // are capped at the humongous thresold and we want to
    // ensure that we don't try to allocate a TLAB as
    // humongous and that we don't allocate a humongous
    // object in a TLAB.
    return word_size > _humongous_object_threshold_in_words;
  }

  // Update mod union table with the set of dirty cards.
  void updateModUnion();

  // Set the mod union bits corresponding to the given memRegion.  Note
  // that this is always a safe operation, since it doesn't clear any
  // bits.
  void markModUnionRange(MemRegion mr);

  // Records the fact that a marking phase is no longer in progress.
  void set_marking_complete() {
    _mark_in_progress = false;
  }
  void set_marking_started() {
    _mark_in_progress = true;
  }
  bool mark_in_progress() {
    return _mark_in_progress;
  }

  // Print the maximum heap capacity.
  virtual size_t max_capacity() const;

  virtual jlong millis_since_last_gc();

  // Perform any cleanup actions necessary before allowing a verification.
  virtual void prepare_for_verify();

  // Perform verification.

  // vo == UsePrevMarking  -> use "prev" marking information,
  // vo == UseNextMarking -> use "next" marking information
  // vo == UseMarkWord    -> use the mark word in the object header
  //
  // NOTE: Only the "prev" marking information is guaranteed to be
  // consistent most of the time, so most calls to this should use
  // vo == UsePrevMarking.
  // Currently, there is only one case where this is called with
  // vo == UseNextMarking, which is to verify the "next" marking
  // information at the end of remark.
  // Currently there is only one place where this is called with
  // vo == UseMarkWord, which is to verify the marking during a
  // full GC.
  void verify(bool silent, VerifyOption vo);

  // Override; it uses the "prev" marking information
  virtual void verify(bool silent);

  virtual void print_on(outputStream* st) const;
  virtual void print_extended_on(outputStream* st) const;
  virtual void print_on_error(outputStream* st) const;

  virtual void print_gc_threads_on(outputStream* st) const;
  virtual void gc_threads_do(ThreadClosure* tc) const;

  // Override
  void print_tracing_info() const;

  // The following two methods are helpful for debugging RSet issues.
  void print_cset_rsets() PRODUCT_RETURN;
  void print_all_rsets() PRODUCT_RETURN;

  // Convenience function to be used in situations where the heap type can be
  // asserted to be this type.
  static G1CollectedHeap* heap();

  void set_region_short_lived_locked(HeapRegion* hr);
  // add appropriate methods for any other surv rate groups

  YoungList* young_list() { return _young_list; }

  // debugging
  bool check_young_list_well_formed() {
    return _young_list->check_list_well_formed();
  }

  bool check_young_list_empty(bool check_heap,
                              bool check_sample = true);

  // *** Stuff related to concurrent marking.  It's not clear to me that so
  // many of these need to be public.

  // The functions below are helper functions that a subclass of
  // "CollectedHeap" can use in the implementation of its virtual
  // functions.
  // This performs a concurrent marking of the live objects in a
  // bitmap off to the side.
  void doConcurrentMark();

  bool isMarkedPrev(oop obj) const;
  bool isMarkedNext(oop obj) const;

  // Determine if an object is dead, given the object and also
  // the region to which the object belongs. An object is dead
  // iff a) it was not allocated since the last mark and b) it
  // is not marked.

  bool is_obj_dead(const oop obj, const HeapRegion* hr) const {
    return
      !hr->obj_allocated_since_prev_marking(obj) &&
      !isMarkedPrev(obj);
  }

  // This function returns true when an object has been
  // around since the previous marking and hasn't yet
  // been marked during this marking.

  bool is_obj_ill(const oop obj, const HeapRegion* hr) const {
    return
      !hr->obj_allocated_since_next_marking(obj) &&
      !isMarkedNext(obj);
  }

  // Determine if an object is dead, given only the object itself.
  // This will find the region to which the object belongs and
  // then call the region version of the same function.

  // Added if it is NULL it isn't dead.

  bool is_obj_dead(const oop obj) const {
    const HeapRegion* hr = heap_region_containing(obj);
    if (hr == NULL) {
      if (obj == NULL) return false;
      else return true;
    }
    else return is_obj_dead(obj, hr);
  }

  bool is_obj_ill(const oop obj) const {
    const HeapRegion* hr = heap_region_containing(obj);
    if (hr == NULL) {
      if (obj == NULL) return false;
      else return true;
    }
    else return is_obj_ill(obj, hr);
  }

  // The methods below are here for convenience and dispatch the
  // appropriate method depending on value of the given VerifyOption
  // parameter. The options for that parameter are:
  //
  // vo == UsePrevMarking -> use "prev" marking information,
  // vo == UseNextMarking -> use "next" marking information,
  // vo == UseMarkWord    -> use mark word from object header

  bool is_obj_dead_cond(const oop obj,
                        const HeapRegion* hr,
                        const VerifyOption vo) const {
    switch (vo) {
    case VerifyOption_G1UsePrevMarking: return is_obj_dead(obj, hr);
    case VerifyOption_G1UseNextMarking: return is_obj_ill(obj, hr);
    case VerifyOption_G1UseMarkWord:    return !obj->is_gc_marked();
    default:                            ShouldNotReachHere();
    }
    return false; // keep some compilers happy
  }

  bool is_obj_dead_cond(const oop obj,
                        const VerifyOption vo) const {
    switch (vo) {
    case VerifyOption_G1UsePrevMarking: return is_obj_dead(obj);
    case VerifyOption_G1UseNextMarking: return is_obj_ill(obj);
    case VerifyOption_G1UseMarkWord:    return !obj->is_gc_marked();
    default:                            ShouldNotReachHere();
    }
    return false; // keep some compilers happy
  }

  bool allocated_since_marking(oop obj, HeapRegion* hr, VerifyOption vo);
  HeapWord* top_at_mark_start(HeapRegion* hr, VerifyOption vo);
  bool is_marked(oop obj, VerifyOption vo);
  const char* top_at_mark_start_str(VerifyOption vo);

  // The following is just to alert the verification code
  // that a full collection has occurred and that the
  // remembered sets are no longer up to date.
  bool _full_collection;
  void set_full_collection() { _full_collection = true;}
  void clear_full_collection() {_full_collection = false;}
  bool full_collection() {return _full_collection;}

  ConcurrentMark* concurrent_mark() const { return _cm; }
  ConcurrentG1Refine* concurrent_g1_refine() const { return _cg1r; }

  // The dirty cards region list is used to record a subset of regions
  // whose cards need clearing. The list if populated during the
  // remembered set scanning and drained during the card table
  // cleanup. Although the methods are reentrant, population/draining
  // phases must not overlap. For synchronization purposes the last
  // element on the list points to itself.
  HeapRegion* _dirty_cards_region_list;
  void push_dirty_cards_region(HeapRegion* hr);
  HeapRegion* pop_dirty_cards_region();

public:
  void stop_conc_gc_threads();

  size_t pending_card_num();
  size_t cards_scanned();

protected:
  size_t _max_heap_capacity;
};

class G1ParGCAllocBuffer: public ParGCAllocBuffer {
private:
  bool        _retired;

public:
  G1ParGCAllocBuffer(size_t gclab_word_size);

  void set_buf(HeapWord* buf) {
    ParGCAllocBuffer::set_buf(buf);
    _retired = false;
  }

  void retire(bool end_of_gc, bool retain) {
    if (_retired)
      return;
    ParGCAllocBuffer::retire(end_of_gc, retain);
    _retired = true;
  }

  bool is_retired() {
    return _retired;
  }
};

class G1ParGCAllocBufferContainer {
protected:
  static int const _priority_max = 2;
  G1ParGCAllocBuffer* _priority_buffer[_priority_max];

public:
  G1ParGCAllocBufferContainer(size_t gclab_word_size) {
    for (int pr = 0; pr < _priority_max; ++pr) {
      _priority_buffer[pr] = new G1ParGCAllocBuffer(gclab_word_size);
    }
  }

  ~G1ParGCAllocBufferContainer() {
    for (int pr = 0; pr < _priority_max; ++pr) {
      assert(_priority_buffer[pr]->is_retired(), "alloc buffers should all retire at this point.");
      delete _priority_buffer[pr];
    }
  }

  HeapWord* allocate(size_t word_sz) {
    HeapWord* obj;
    for (int pr = 0; pr < _priority_max; ++pr) {
      obj = _priority_buffer[pr]->allocate(word_sz);
      if (obj != NULL) return obj;
    }
    return obj;
  }

  bool contains(void* addr) {
    for (int pr = 0; pr < _priority_max; ++pr) {
      if (_priority_buffer[pr]->contains(addr)) return true;
    }
    return false;
  }

  void undo_allocation(HeapWord* obj, size_t word_sz) {
    bool finish_undo;
    for (int pr = 0; pr < _priority_max; ++pr) {
      if (_priority_buffer[pr]->contains(obj)) {
        _priority_buffer[pr]->undo_allocation(obj, word_sz);
        finish_undo = true;
      }
    }
    if (!finish_undo) ShouldNotReachHere();
  }

  size_t words_remaining() {
    size_t result = 0;
    for (int pr = 0; pr < _priority_max; ++pr) {
      result += _priority_buffer[pr]->words_remaining();
    }
    return result;
  }

  size_t words_remaining_in_retired_buffer() {
    G1ParGCAllocBuffer* retired = _priority_buffer[0];
    return retired->words_remaining();
  }

  void flush_stats_and_retire(PLABStats* stats, bool end_of_gc, bool retain) {
    for (int pr = 0; pr < _priority_max; ++pr) {
      _priority_buffer[pr]->flush_stats_and_retire(stats, end_of_gc, retain);
    }
  }

  void update(bool end_of_gc, bool retain, HeapWord* buf, size_t word_sz) {
    G1ParGCAllocBuffer* retired_and_set = _priority_buffer[0];
    retired_and_set->retire(end_of_gc, retain);
    retired_and_set->set_buf(buf);
    retired_and_set->set_word_size(word_sz);
    adjust_priority_order();
  }

private:
  void adjust_priority_order() {
    G1ParGCAllocBuffer* retired_and_set = _priority_buffer[0];

    int last = _priority_max - 1;
    for (int pr = 0; pr < last; ++pr) {
      _priority_buffer[pr] = _priority_buffer[pr + 1];
    }
    _priority_buffer[last] = retired_and_set;
  }
};

class G1ParScanThreadState : public StackObj {
protected:
  G1CollectedHeap* _g1h;
  RefToScanQueue*  _refs;
  DirtyCardQueue   _dcq;
  CardTableModRefBS* _ct_bs;
  G1RemSet* _g1_rem;

  G1ParGCAllocBufferContainer  _surviving_alloc_buffer;
  G1ParGCAllocBufferContainer  _tenured_alloc_buffer;
  G1ParGCAllocBufferContainer* _alloc_buffers[GCAllocPurposeCount];
  ageTable            _age_table;

  size_t           _alloc_buffer_waste;
  size_t           _undo_waste;

  OopsInHeapRegionClosure*      _evac_failure_cl;
  G1ParScanHeapEvacClosure*     _evac_cl;
  G1ParScanPartialArrayClosure* _partial_scan_cl;

  int  _hash_seed;
  uint _queue_num;

  size_t _term_attempts;

  double _start;
  double _start_strong_roots;
  double _strong_roots_time;
  double _start_term;
  double _term_time;

  // Map from young-age-index (0 == not young, 1 is youngest) to
  // surviving words. base is what we get back from the malloc call
  size_t* _surviving_young_words_base;
  // this points into the array, as we use the first few entries for padding
  size_t* _surviving_young_words;

#define PADDING_ELEM_NUM (DEFAULT_CACHE_LINE_SIZE / sizeof(size_t))

  void   add_to_alloc_buffer_waste(size_t waste) { _alloc_buffer_waste += waste; }

  void   add_to_undo_waste(size_t waste)         { _undo_waste += waste; }

  DirtyCardQueue& dirty_card_queue()             { return _dcq;  }
  CardTableModRefBS* ctbs()                      { return _ct_bs; }

  template <class T> void immediate_rs_update(HeapRegion* from, T* p, int tid) {
    if (!from->is_survivor()) {
      _g1_rem->par_write_ref(from, p, tid);
    }
  }

  template <class T> void deferred_rs_update(HeapRegion* from, T* p, int tid) {
    // If the new value of the field points to the same region or
    // is the to-space, we don't need to include it in the Rset updates.
    if (!from->is_in_reserved(oopDesc::load_decode_heap_oop(p)) && !from->is_survivor()) {
      size_t card_index = ctbs()->index_for(p);
      // If the card hasn't been added to the buffer, do it.
      if (ctbs()->mark_card_deferred(card_index)) {
        dirty_card_queue().enqueue((jbyte*)ctbs()->byte_for_index(card_index));
      }
    }
  }

public:
  G1ParScanThreadState(G1CollectedHeap* g1h, uint queue_num);

  ~G1ParScanThreadState() {
    FREE_C_HEAP_ARRAY(size_t, _surviving_young_words_base, mtGC);
  }

  RefToScanQueue*   refs()            { return _refs;             }
  ageTable*         age_table()       { return &_age_table;       }

  G1ParGCAllocBufferContainer* alloc_buffer(GCAllocPurpose purpose) {
    return _alloc_buffers[purpose];
  }

  size_t alloc_buffer_waste() const              { return _alloc_buffer_waste; }
  size_t undo_waste() const                      { return _undo_waste; }

#ifdef ASSERT
  bool verify_ref(narrowOop* ref) const;
  bool verify_ref(oop* ref) const;
  bool verify_task(StarTask ref) const;
#endif // ASSERT

  template <class T> void push_on_queue(T* ref) {
    assert(verify_ref(ref), "sanity");
    refs()->push(ref);
  }

  template <class T> void update_rs(HeapRegion* from, T* p, int tid) {
    if (G1DeferredRSUpdate) {
      deferred_rs_update(from, p, tid);
    } else {
      immediate_rs_update(from, p, tid);
    }
  }

  HeapWord* allocate_slow(GCAllocPurpose purpose, size_t word_sz) {
    HeapWord* obj = NULL;
    size_t gclab_word_size = _g1h->desired_plab_sz(purpose);
    if (word_sz * 100 < gclab_word_size * ParallelGCBufferWastePct) {
      G1ParGCAllocBufferContainer* alloc_buf = alloc_buffer(purpose);

      HeapWord* buf = _g1h->par_allocate_during_gc(purpose, gclab_word_size);
      if (buf == NULL) return NULL; // Let caller handle allocation failure.

      add_to_alloc_buffer_waste(alloc_buf->words_remaining_in_retired_buffer());
      alloc_buf->update(false /* end_of_gc */, false /* retain */, buf, gclab_word_size);

      obj = alloc_buf->allocate(word_sz);
      assert(obj != NULL, "buffer was definitely big enough...");
    } else {
      obj = _g1h->par_allocate_during_gc(purpose, word_sz);
    }
    return obj;
  }

  HeapWord* allocate(GCAllocPurpose purpose, size_t word_sz) {
    HeapWord* obj = alloc_buffer(purpose)->allocate(word_sz);
    if (obj != NULL) return obj;
    return allocate_slow(purpose, word_sz);
  }

  void undo_allocation(GCAllocPurpose purpose, HeapWord* obj, size_t word_sz) {
    if (alloc_buffer(purpose)->contains(obj)) {
      assert(alloc_buffer(purpose)->contains(obj + word_sz - 1),
             "should contain whole object");
      alloc_buffer(purpose)->undo_allocation(obj, word_sz);
    } else {
      CollectedHeap::fill_with_object(obj, word_sz);
      add_to_undo_waste(word_sz);
    }
  }

  void set_evac_failure_closure(OopsInHeapRegionClosure* evac_failure_cl) {
    _evac_failure_cl = evac_failure_cl;
  }
  OopsInHeapRegionClosure* evac_failure_closure() {
    return _evac_failure_cl;
  }

  void set_evac_closure(G1ParScanHeapEvacClosure* evac_cl) {
    _evac_cl = evac_cl;
  }

  void set_partial_scan_closure(G1ParScanPartialArrayClosure* partial_scan_cl) {
    _partial_scan_cl = partial_scan_cl;
  }

  int* hash_seed() { return &_hash_seed; }
  uint queue_num() { return _queue_num; }

  size_t term_attempts() const  { return _term_attempts; }
  void note_term_attempt() { _term_attempts++; }

  void start_strong_roots() {
    _start_strong_roots = os::elapsedTime();
  }
  void end_strong_roots() {
    _strong_roots_time += (os::elapsedTime() - _start_strong_roots);
  }
  double strong_roots_time() const { return _strong_roots_time; }

  void start_term_time() {
    note_term_attempt();
    _start_term = os::elapsedTime();
  }
  void end_term_time() {
    _term_time += (os::elapsedTime() - _start_term);
  }
  double term_time() const { return _term_time; }

  double elapsed_time() const {
    return os::elapsedTime() - _start;
  }

  static void
    print_termination_stats_hdr(outputStream* const st = gclog_or_tty);
  void
    print_termination_stats(int i, outputStream* const st = gclog_or_tty) const;

  size_t* surviving_young_words() {
    // We add on to hide entry 0 which accumulates surviving words for
    // age -1 regions (i.e. non-young ones)
    return _surviving_young_words;
  }

  void retire_alloc_buffers() {
    for (int ap = 0; ap < GCAllocPurposeCount; ++ap) {
      size_t waste = _alloc_buffers[ap]->words_remaining();
      add_to_alloc_buffer_waste(waste);
      _alloc_buffers[ap]->flush_stats_and_retire(_g1h->stats_for_purpose((GCAllocPurpose)ap),
                                                 true /* end_of_gc */,
                                                 false /* retain */);
    }
  }

  template <class T> void deal_with_reference(T* ref_to_scan) {
    if (has_partial_array_mask(ref_to_scan)) {
      _partial_scan_cl->do_oop_nv(ref_to_scan);
    } else {
      // Note: we can use "raw" versions of "region_containing" because
      // "obj_to_scan" is definitely in the heap, and is not in a
      // humongous region.
      HeapRegion* r = _g1h->heap_region_containing_raw(ref_to_scan);
      _evac_cl->set_region(r);
      _evac_cl->do_oop_nv(ref_to_scan);
    }
  }

  void deal_with_reference(StarTask ref) {
    assert(verify_task(ref), "sanity");
    if (ref.is_narrow()) {
      deal_with_reference((narrowOop*)ref);
    } else {
      deal_with_reference((oop*)ref);
    }
  }

  void trim_queue();
};

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_G1COLLECTEDHEAP_HPP
