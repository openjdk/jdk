/*
 * Copyright (c) 2001, 2011, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/g1/g1RemSet.hpp"
#include "gc_implementation/g1/heapRegion.hpp"
#include "gc_implementation/parNew/parGCAllocBuffer.hpp"
#include "memory/barrierSet.hpp"
#include "memory/memRegion.hpp"
#include "memory/sharedHeap.hpp"

// A "G1CollectedHeap" is an implementation of a java heap for HotSpot.
// It uses the "Garbage First" heap organization and algorithm, which
// may combine concurrent marking with parallel, incremental compaction of
// heap subsets that will yield large amounts of garbage.

class HeapRegion;
class HeapRegionSeq;
class PermanentGenerationSpec;
class GenerationSpec;
class OopsInHeapRegionClosure;
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
class ConcurrentZFThread;

typedef OverflowTaskQueue<StarTask>         RefToScanQueue;
typedef GenericTaskQueueSet<RefToScanQueue> RefToScanQueueSet;

typedef int RegionIdx_t;   // needs to hold [ 0..max_regions() )
typedef int CardIdx_t;     // needs to hold [ 0..CardsPerRegion )

enum G1GCThreadGroups {
  G1CRGroup = 0,
  G1ZFGroup = 1,
  G1CMGroup = 2,
  G1CLGroup = 3
};

enum GCAllocPurpose {
  GCAllocForTenured,
  GCAllocForSurvived,
  GCAllocPurposeCount
};

class YoungList : public CHeapObj {
private:
  G1CollectedHeap* _g1h;

  HeapRegion* _head;

  HeapRegion* _survivor_head;
  HeapRegion* _survivor_tail;

  HeapRegion* _curr;

  size_t      _length;
  size_t      _survivor_length;

  size_t      _last_sampled_rs_lengths;
  size_t      _sampled_rs_lengths;

  void         empty_list(HeapRegion* list);

public:
  YoungList(G1CollectedHeap* g1h);

  void         push_region(HeapRegion* hr);
  void         add_survivor_region(HeapRegion* hr);

  void         empty_list();
  bool         is_empty() { return _length == 0; }
  size_t       length() { return _length; }
  size_t       survivor_length() { return _survivor_length; }

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

class RefineCardTableEntryClosure;
class G1CollectedHeap : public SharedHeap {
  friend class VM_G1CollectForAllocation;
  friend class VM_GenCollectForPermanentAllocation;
  friend class VM_G1CollectFull;
  friend class VM_G1IncCollectionPause;
  friend class VMStructs;

  // Closures used in implementation.
  friend class G1ParCopyHelper;
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
  friend class CountRCClosure;
  friend class EvacPopObjClosure;
  friend class G1ParCleanupCTTask;

  // Other related classes.
  friend class G1MarkSweep;

private:
  // The one and only G1CollectedHeap, so static functions can find it.
  static G1CollectedHeap* _g1h;

  static size_t _humongous_object_threshold_in_words;

  // Storage for the G1 heap (excludes the permanent generation).
  VirtualSpace _g1_storage;
  MemRegion    _g1_reserved;

  // The part of _g1_storage that is currently committed.
  MemRegion _g1_committed;

  // The maximum part of _g1_storage that has ever been committed.
  MemRegion _g1_max_committed;

  // The number of regions that are completely free.
  size_t _free_regions;

  // The number of regions we could create by expansion.
  size_t _expansion_regions;

  // Return the number of free regions in the heap (by direct counting.)
  size_t count_free_regions();
  // Return the number of free regions on the free and unclean lists.
  size_t count_free_regions_list();

  // The block offset table for the G1 heap.
  G1BlockOffsetSharedArray* _bot_shared;

  // Move all of the regions off the free lists, then rebuild those free
  // lists, before and after full GC.
  void tear_down_region_lists();
  void rebuild_region_lists();
  // This sets all non-empty regions to need zero-fill (which they will if
  // they are empty after full collection.)
  void set_used_regions_to_need_zero_fill();

  // The sequence of all heap regions in the heap.
  HeapRegionSeq* _hrs;

  // The region from which normal-sized objects are currently being
  // allocated.  May be NULL.
  HeapRegion* _cur_alloc_region;

  // Postcondition: cur_alloc_region == NULL.
  void abandon_cur_alloc_region();
  void abandon_gc_alloc_regions();

  // The to-space memory regions into which objects are being copied during
  // a GC.
  HeapRegion* _gc_alloc_regions[GCAllocPurposeCount];
  size_t _gc_alloc_region_counts[GCAllocPurposeCount];
  // These are the regions, one per GCAllocPurpose, that are half-full
  // at the end of a collection and that we want to reuse during the
  // next collection.
  HeapRegion* _retained_gc_alloc_regions[GCAllocPurposeCount];
  // This specifies whether we will keep the last half-full region at
  // the end of a collection so that it can be reused during the next
  // collection (this is specified per GCAllocPurpose)
  bool _retain_gc_alloc_region[GCAllocPurposeCount];

  // A list of the regions that have been set to be alloc regions in the
  // current collection.
  HeapRegion* _gc_alloc_region_list;

  // Determines PLAB size for a particular allocation purpose.
  static size_t desired_plab_sz(GCAllocPurpose purpose);

  // When called by par thread, require par_alloc_during_gc_lock() to be held.
  void push_gc_alloc_region(HeapRegion* hr);

  // This should only be called single-threaded.  Undeclares all GC alloc
  // regions.
  void forget_alloc_region_list();

  // Should be used to set an alloc region, because there's other
  // associated bookkeeping.
  void set_gc_alloc_region(int purpose, HeapRegion* r);

  // Check well-formedness of alloc region list.
  bool check_gc_alloc_regions();

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
  size_t _in_cset_fast_test_length;

  volatile unsigned _gc_time_stamp;

  size_t* _surviving_young_words;

  void setup_surviving_young_words();
  void update_surviving_young_words(size_t* surv_young_words);
  void cleanup_surviving_young_words();

  // It decides whether an explicit GC should start a concurrent cycle
  // instead of doing a STW GC. Currently, a concurrent cycle is
  // explicitly started if:
  // (a) cause == _gc_locker and +GCLockerInvokesConcurrent, or
  // (b) cause == _java_lang_system_gc and +ExplicitGCInvokesConcurrent.
  bool should_do_concurrent_full_gc(GCCause::Cause cause);

  // Keeps track of how many "full collections" (i.e., Full GCs or
  // concurrent cycles) we have completed. The number of them we have
  // started is maintained in _total_full_collections in CollectedHeap.
  volatile unsigned int _full_collections_completed;

  // These are macros so that, if the assert fires, we get the correct
  // line number, file, etc.

#define heap_locking_asserts_err_msg(__extra_message)                         \
  err_msg("%s : Heap_lock %slocked, %sat a safepoint",                        \
          (__extra_message),                                                  \
          (!Heap_lock->owned_by_self()) ? "NOT " : "",                        \
          (!SafepointSynchronize::is_at_safepoint()) ? "NOT " : "")

#define assert_heap_locked()                                                  \
  do {                                                                        \
    assert(Heap_lock->owned_by_self(),                                        \
           heap_locking_asserts_err_msg("should be holding the Heap_lock"));  \
  } while (0)

#define assert_heap_locked_or_at_safepoint()                                  \
  do {                                                                        \
    assert(Heap_lock->owned_by_self() ||                                      \
                                     SafepointSynchronize::is_at_safepoint(), \
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

#define assert_at_safepoint()                                                 \
  do {                                                                        \
    assert(SafepointSynchronize::is_at_safepoint(),                           \
           heap_locking_asserts_err_msg("should be at a safepoint"));         \
  } while (0)

#define assert_not_at_safepoint()                                             \
  do {                                                                        \
    assert(!SafepointSynchronize::is_at_safepoint(),                          \
           heap_locking_asserts_err_msg("should not be at a safepoint"));     \
  } while (0)

protected:

  // Returns "true" iff none of the gc alloc regions have any allocations
  // since the last call to "save_marks".
  bool all_alloc_regions_no_allocs_since_save_marks();
  // Perform finalization stuff on all allocation regions.
  void retire_all_alloc_regions();

  // The number of regions allocated to hold humongous objects.
  int         _num_humongous_regions;
  YoungList*  _young_list;

  // The current policy object for the collector.
  G1CollectorPolicy* _g1_policy;

  // Parallel allocation lock to protect the current allocation region.
  Mutex  _par_alloc_during_gc_lock;
  Mutex* par_alloc_during_gc_lock() { return &_par_alloc_during_gc_lock; }

  // If possible/desirable, allocate a new HeapRegion for normal object
  // allocation sufficient for an allocation of the given "word_size".
  // If "do_expand" is true, will attempt to expand the heap if necessary
  // to to satisfy the request.  If "zero_filled" is true, requires a
  // zero-filled region.
  // (Returning NULL will trigger a GC.)
  virtual HeapRegion* newAllocRegion_work(size_t word_size,
                                          bool do_expand,
                                          bool zero_filled);

  virtual HeapRegion* newAllocRegion(size_t word_size,
                                     bool zero_filled = true) {
    return newAllocRegion_work(word_size, false, zero_filled);
  }
  virtual HeapRegion* newAllocRegionWithExpansion(int purpose,
                                                  size_t word_size,
                                                  bool zero_filled = true);

  // Attempt to allocate an object of the given (very large) "word_size".
  // Returns "NULL" on failure.
  virtual HeapWord* humongous_obj_allocate(size_t word_size);

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
  // * All non-TLAB allocation requests should go to mem_allocate()
  //   and mem_allocate() should never be called with is_tlab == true.
  //
  // * If the GC locker is active we currently stall until we can
  //   allocate a new young region. This will be changed in the
  //   near future (see CR 6994056).
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
                                 bool   is_noref,
                                 bool   is_tlab, /* expected to be false */
                                 bool*  gc_overhead_limit_was_exceeded);

  // The following methods, allocate_from_cur_allocation_region(),
  // attempt_allocation(), attempt_allocation_locked(),
  // replace_cur_alloc_region_and_allocate(),
  // attempt_allocation_slow(), and attempt_allocation_humongous()
  // have very awkward pre- and post-conditions with respect to
  // locking:
  //
  // If they are called outside a safepoint they assume the caller
  // holds the Heap_lock when it calls them. However, on exit they
  // will release the Heap_lock if they return a non-NULL result, but
  // keep holding the Heap_lock if they return a NULL result. The
  // reason for this is that we need to dirty the cards that span
  // allocated blocks on young regions to avoid having to take the
  // slow path of the write barrier (for performance reasons we don't
  // update RSets for references whose source is a young region, so we
  // don't need to look at dirty cards on young regions). But, doing
  // this card dirtying while holding the Heap_lock can be a
  // scalability bottleneck, especially given that some allocation
  // requests might be of non-trivial size (and the larger the region
  // size is, the fewer allocations requests will be considered
  // humongous, as the humongous size limit is a fraction of the
  // region size). So, when one of these calls succeeds in allocating
  // a block it does the card dirtying after it releases the Heap_lock
  // which is why it will return without holding it.
  //
  // The above assymetry is the reason why locking / unlocking is done
  // explicitly (i.e., with Heap_lock->lock() and
  // Heap_lock->unlocked()) instead of using MutexLocker and
  // MutexUnlocker objects. The latter would ensure that the lock is
  // unlocked / re-locked at every possible exit out of the basic
  // block. However, we only want that action to happen in selected
  // places.
  //
  // Further, if the above methods are called during a safepoint, then
  // naturally there's no assumption about the Heap_lock being held or
  // there's no attempt to unlock it. The parameter at_safepoint
  // indicates whether the call is made during a safepoint or not (as
  // an optimization, to avoid reading the global flag with
  // SafepointSynchronize::is_at_safepoint()).
  //
  // The methods share these parameters:
  //
  // * word_size     : the size of the allocation request in words
  // * at_safepoint  : whether the call is done at a safepoint; this
  //                   also determines whether a GC is permitted
  //                   (at_safepoint == false) or not (at_safepoint == true)
  // * do_dirtying   : whether the method should dirty the allocated
  //                   block before returning
  //
  // They all return either the address of the block, if they
  // successfully manage to allocate it, or NULL.

  // It tries to satisfy an allocation request out of the current
  // alloc region, which is passed as a parameter. It assumes that the
  // caller has checked that the current alloc region is not NULL.
  // Given that the caller has to check the current alloc region for
  // at least NULL, it might as well pass it as the first parameter so
  // that the method doesn't have to read it from the
  // _cur_alloc_region field again. It is called from both
  // attempt_allocation() and attempt_allocation_locked() and the
  // with_heap_lock parameter indicates whether the caller was holding
  // the heap lock when it called it or not.
  inline HeapWord* allocate_from_cur_alloc_region(HeapRegion* cur_alloc_region,
                                                  size_t word_size,
                                                  bool with_heap_lock);

  // First-level of allocation slow path: it attempts to allocate out
  // of the current alloc region in a lock-free manner using a CAS. If
  // that fails it takes the Heap_lock and calls
  // attempt_allocation_locked() for the second-level slow path.
  inline HeapWord* attempt_allocation(size_t word_size);

  // Second-level of allocation slow path: while holding the Heap_lock
  // it tries to allocate out of the current alloc region and, if that
  // fails, tries to allocate out of a new current alloc region.
  inline HeapWord* attempt_allocation_locked(size_t word_size);

  // It assumes that the current alloc region has been retired and
  // tries to allocate a new one. If it's successful, it performs the
  // allocation out of the new current alloc region and updates
  // _cur_alloc_region. Normally, it would try to allocate a new
  // region if the young gen is not full, unless can_expand is true in
  // which case it would always try to allocate a new region.
  HeapWord* replace_cur_alloc_region_and_allocate(size_t word_size,
                                                  bool at_safepoint,
                                                  bool do_dirtying,
                                                  bool can_expand);

  // Third-level of allocation slow path: when we are unable to
  // allocate a new current alloc region to satisfy an allocation
  // request (i.e., when attempt_allocation_locked() fails). It will
  // try to do an evacuation pause, which might stall due to the GC
  // locker, and retry the allocation attempt when appropriate.
  HeapWord* attempt_allocation_slow(size_t word_size);

  // The method that tries to satisfy a humongous allocation
  // request. If it cannot satisfy it it will try to do an evacuation
  // pause to perhaps reclaim enough space to be able to satisfy the
  // allocation request afterwards.
  HeapWord* attempt_allocation_humongous(size_t word_size,
                                         bool at_safepoint);

  // It does the common work when we are retiring the current alloc region.
  inline void retire_cur_alloc_region_common(HeapRegion* cur_alloc_region);

  // It retires the current alloc region, which is passed as a
  // parameter (since, typically, the caller is already holding on to
  // it). It sets _cur_alloc_region to NULL.
  void retire_cur_alloc_region(HeapRegion* cur_alloc_region);

  // It attempts to do an allocation immediately before or after an
  // evacuation pause and can only be called by the VM thread. It has
  // slightly different assumptions that the ones before (i.e.,
  // assumes that the current alloc region has been retired).
  HeapWord* attempt_allocation_at_safepoint(size_t word_size,
                                            bool expect_null_cur_alloc_region);

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

  HeapWord* allocate_during_gc_slow(GCAllocPurpose purpose,
                                    HeapRegion*    alloc_region,
                                    bool           par,
                                    size_t         word_size);

  // Ensure that no further allocations can happen in "r", bearing in mind
  // that parallel threads might be attempting allocations.
  void par_allocate_remaining_space(HeapRegion* r);

  // Retires an allocation region when it is full or at the end of a
  // GC pause.
  void  retire_alloc_region(HeapRegion* alloc_region, bool par);

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
  void do_full_collection(bool clear_all_soft_refs);

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

public:
  // Expand the garbage-first heap by at least the given size (in bytes!).
  // (Rounds up to a HeapRegion boundary.)
  virtual void expand(size_t expand_bytes);

  // Do anything common to GC's.
  virtual void gc_prologue(bool full);
  virtual void gc_epilogue(bool full);

  // We register a region with the fast "in collection set" test. We
  // simply set to true the array slot corresponding to this region.
  void register_region_with_in_cset_fast_test(HeapRegion* r) {
    assert(_in_cset_fast_test_base != NULL, "sanity");
    assert(r->in_collection_set(), "invariant");
    int index = r->hrs_index();
    assert(0 <= index && (size_t) index < _in_cset_fast_test_length, "invariant");
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
      size_t index = ((size_t) obj) >> HeapRegion::LogOfHRGrainBytes;
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
        _in_cset_fast_test_length * sizeof(bool));
  }

  // This is called at the end of either a concurrent cycle or a Full
  // GC to update the number of full collections completed. Those two
  // can happen in a nested fashion, i.e., we start a concurrent
  // cycle, a Full GC happens half-way through it which ends first,
  // and then the cycle notices that a Full GC happened and ends
  // too. The concurrent parameter is a boolean to help us do a bit
  // tighter consistency checking in the method. If concurrent is
  // false, the caller is the inner caller in the nesting (i.e., the
  // Full GC). If concurrent is true, the caller is the outer caller
  // in this nesting (i.e., the concurrent cycle). Further nesting is
  // not currently supported. The end of the this call also notifies
  // the FullGCCount_lock in case a Java thread is waiting for a full
  // GC to happen (e.g., it called System.gc() with
  // +ExplicitGCInvokesConcurrent).
  void increment_full_collections_completed(bool concurrent);

  unsigned int full_collections_completed() {
    return _full_collections_completed;
  }

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
  void evacuate_collection_set();

  // The g1 remembered set of the heap.
  G1RemSet* _g1_rem_set;
  // And it's mod ref barrier set, used to track updates for the above.
  ModRefBarrierSet* _mr_bs;

  // A set of cards that cover the objects for which the Rsets should be updated
  // concurrently after the collection.
  DirtyCardQueueSet _dirty_card_queue_set;

  // The Heap Region Rem Set Iterator.
  HeapRegionRemSetIterator** _rem_set_iterator;

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
  void free_collection_set(HeapRegion* cs_head);

  // Abandon the current collection set without recording policy
  // statistics or updating free lists.
  void abandon_collection_set(HeapRegion* cs_head);

  // Applies "scan_non_heap_roots" to roots outside the heap,
  // "scan_rs" to roots inside the heap (having done "set_region" to
  // indicate the region in which the root resides), and does "scan_perm"
  // (setting the generation to the perm generation.)  If "scan_rs" is
  // NULL, then this step is skipped.  The "worker_i"
  // param is for use with parallel roots processing, and should be
  // the "i" of the calling parallel worker thread's work(i) function.
  // In the sequential case this param will be ignored.
  void g1_process_strong_roots(bool collecting_perm_gen,
                               SharedHeap::ScanningOption so,
                               OopClosure* scan_non_heap_roots,
                               OopsInHeapRegionClosure* scan_rs,
                               OopsInGenClosure* scan_perm,
                               int worker_i);

  // Apply "blk" to all the weak roots of the system.  These include
  // JNI weak roots, the code cache, system dictionary, symbol table,
  // string table, and referents of reachable weak refs.
  void g1_process_weak_roots(OopClosure* root_closure,
                             OopClosure* non_root_closure);

  // Invoke "save_marks" on all heap regions.
  void save_marks();

  // Free a heap region.
  void free_region(HeapRegion* hr);
  // A component of "free_region", exposed for 'batching'.
  // All the params after "hr" are out params: the used bytes of the freed
  // region(s), the number of H regions cleared, the number of regions
  // freed, and pointers to the head and tail of a list of freed contig
  // regions, linked throught the "next_on_unclean_list" field.
  void free_region_work(HeapRegion* hr,
                        size_t& pre_used,
                        size_t& cleared_h,
                        size_t& freed_regions,
                        UncleanRegionList* list,
                        bool par = false);


  // The concurrent marker (and the thread it runs in.)
  ConcurrentMark* _cm;
  ConcurrentMarkThread* _cmThread;
  bool _mark_in_progress;

  // The concurrent refiner.
  ConcurrentG1Refine* _cg1r;

  // The concurrent zero-fill thread.
  ConcurrentZFThread* _czft;

  // The parallel task queues
  RefToScanQueueSet *_task_queues;

  // True iff a evacuation has failed in the current collection.
  bool _evacuation_failed;

  // Set the attribute indicating whether evacuation has failed in the
  // current collection.
  void set_evacuation_failed(bool b) { _evacuation_failed = b; }

  // Failed evacuations cause some logical from-space objects to have
  // forwarding pointers to themselves.  Reset them.
  void remove_self_forwarding_pointers();

  // When one is non-null, so is the other.  Together, they each pair is
  // an object with a preserved mark, and its mark value.
  GrowableArray<oop>*     _objs_with_preserved_marks;
  GrowableArray<markOop>* _preserved_marks_of_objs;

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
  oop handle_evacuation_failure_par(OopsInHeapRegionClosure* cl, oop obj);
  void handle_evacuation_failure_common(oop obj, markOop m);


  // Ensure that the relevant gc_alloc regions are set.
  void get_gc_alloc_regions();
  // We're done with GC alloc regions. We are going to tear down the
  // gc alloc list and remove the gc alloc tag from all the regions on
  // that list. However, we will also retain the last (i.e., the one
  // that is half-full) GC alloc region, per GCAllocPurpose, for
  // possible reuse during the next collection, provided
  // _retain_gc_alloc_region[] indicates that it should be the
  // case. Said regions are kept in the _retained_gc_alloc_regions[]
  // array. If the parameter totally is set, we will not retain any
  // regions, irrespective of what _retain_gc_alloc_region[]
  // indicates.
  void release_gc_alloc_regions(bool totally);
#ifndef PRODUCT
  // Useful for debugging.
  void print_gc_alloc_regions();
#endif // !PRODUCT

  // Instance of the concurrent mark is_alive closure for embedding
  // into the reference processor as the is_alive_non_header. This
  // prevents unnecessary additions to the discovered lists during
  // concurrent discovery.
  G1CMIsAliveClosure _is_alive_closure;

  // ("Weak") Reference processing support
  ReferenceProcessor* _ref_processor;

  enum G1H_process_strong_roots_tasks {
    G1H_PS_mark_stack_oops_do,
    G1H_PS_refProcessor_oops_do,
    // Leave this one last.
    G1H_PS_NumElements
  };

  SubTasksDone* _process_strong_tasks;

  // List of regions which require zero filling.
  UncleanRegionList _unclean_region_list;
  bool _unclean_regions_coming;

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
  // maximum sizes, permanent generation, and remembered and barrier sets
  // specified by the policy object.
  jint initialize();

  virtual void ref_processing_init();

  void set_par_threads(int t) {
    SharedHeap::set_par_threads(t);
    _process_strong_tasks->set_n_threads(t);
  }

  virtual CollectedHeap::Name kind() const {
    return CollectedHeap::G1CollectedHeap;
  }

  // The current policy object for the collector.
  G1CollectorPolicy* g1_policy() const { return _g1_policy; }

  // Adaptive size policy.  No such thing for g1.
  virtual AdaptiveSizePolicy* size_policy() { return NULL; }

  // The rem set and barrier set.
  G1RemSet* g1_rem_set() const { return _g1_rem_set; }
  ModRefBarrierSet* mr_bs() const { return _mr_bs; }

  // The rem set iterator.
  HeapRegionRemSetIterator* rem_set_iterator(int i) {
    return _rem_set_iterator[i];
  }

  HeapRegionRemSetIterator* rem_set_iterator() {
    return _rem_set_iterator[0];
  }

  unsigned get_gc_time_stamp() {
    return _gc_time_stamp;
  }

  void reset_gc_time_stamp() {
    _gc_time_stamp = 0;
    OrderAccess::fence();
  }

  void increment_gc_time_stamp() {
    ++_gc_time_stamp;
    OrderAccess::fence();
  }

  void iterate_dirty_card_closure(CardTableEntryClosure* cl,
                                  DirtyCardQueue* into_cset_dcq,
                                  bool concurrent, int worker_i);

  // The shared block offset table array.
  G1BlockOffsetSharedArray* bot_shared() const { return _bot_shared; }

  // Reference Processing accessor
  ReferenceProcessor* ref_processor() { return _ref_processor; }

  // Reserved (g1 only; super method includes perm), capacity and the used
  // portion in bytes.
  size_t g1_reserved_obj_bytes() const { return _g1_reserved.byte_size(); }
  virtual size_t capacity() const;
  virtual size_t used() const;
  // This should be called when we're not holding the heap lock. The
  // result might be a bit inaccurate.
  size_t used_unlocked() const;
  size_t recalculate_used() const;
#ifndef PRODUCT
  size_t recalculate_used_regions() const;
#endif // PRODUCT

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
  size_t n_regions();

  // The number of regions that are completely free.
  size_t max_regions();

  // The number of regions that are completely free.
  size_t free_regions();

  // The number of regions that are not completely free.
  size_t used_regions() { return n_regions() - free_regions(); }

  // True iff the ZF thread should run.
  bool should_zf();

  // The number of regions available for "regular" expansion.
  size_t expansion_regions() { return _expansion_regions; }

#ifndef PRODUCT
  bool regions_accounted_for();
  bool print_region_accounting_info();
  void print_region_counts();
#endif

  HeapRegion* alloc_region_from_unclean_list(bool zero_filled);
  HeapRegion* alloc_region_from_unclean_list_locked(bool zero_filled);

  void put_region_on_unclean_list(HeapRegion* r);
  void put_region_on_unclean_list_locked(HeapRegion* r);

  void prepend_region_list_on_unclean_list(UncleanRegionList* list);
  void prepend_region_list_on_unclean_list_locked(UncleanRegionList* list);

  void set_unclean_regions_coming(bool b);
  void set_unclean_regions_coming_locked(bool b);
  // Wait for cleanup to be complete.
  void wait_for_cleanup_complete();
  // Like above, but assumes that the calling thread owns the Heap_lock.
  void wait_for_cleanup_complete_locked();

  // Return the head of the unclean list.
  HeapRegion* peek_unclean_region_list_locked();
  // Remove and return the head of the unclean list.
  HeapRegion* pop_unclean_region_list_locked();

  // List of regions which are zero filled and ready for allocation.
  HeapRegion* _free_region_list;
  // Number of elements on the free list.
  size_t _free_region_list_size;

  // If the head of the unclean list is ZeroFilled, move it to the free
  // list.
  bool move_cleaned_region_to_free_list_locked();
  bool move_cleaned_region_to_free_list();

  void put_free_region_on_list_locked(HeapRegion* r);
  void put_free_region_on_list(HeapRegion* r);

  // Remove and return the head element of the free list.
  HeapRegion* pop_free_region_list_locked();

  // If "zero_filled" is true, we first try the free list, then we try the
  // unclean list, zero-filling the result.  If "zero_filled" is false, we
  // first try the unclean list, then the zero-filled list.
  HeapRegion* alloc_free_region_from_lists(bool zero_filled);

  // Verify the integrity of the region lists.
  void remove_allocated_regions_from_lists();
  bool verify_region_lists();
  bool verify_region_lists_locked();
  size_t unclean_region_list_length();
  size_t free_region_list_length();

  // Perform a collection of the heap; intended for use in implementing
  // "System.gc".  This probably implies as full a collection as the
  // "CollectedHeap" supports.
  virtual void collect(GCCause::Cause cause);

  // The same as above but assume that the caller holds the Heap_lock.
  void collect_locked(GCCause::Cause cause);

  // This interface assumes that it's being called by the
  // vm thread. It collects the heap assuming that the
  // heap lock is already held and that we are executing in
  // the context of the vm thread.
  virtual void collect_as_vm_thread(GCCause::Cause cause);

  // True iff a evacuation has failed in the most-recent collection.
  bool evacuation_failed() { return _evacuation_failed; }

  // Free a region if it is totally full of garbage.  Returns the number of
  // bytes freed (0 ==> didn't free it).
  size_t free_region_if_totally_empty(HeapRegion *hr);
  void free_region_if_totally_empty_work(HeapRegion *hr,
                                         size_t& pre_used,
                                         size_t& cleared_h_regions,
                                         size_t& freed_regions,
                                         UncleanRegionList* list,
                                         bool par = false);

  // If we've done free region work that yields the given changes, update
  // the relevant global variables.
  void finish_free_region_work(size_t pre_used,
                               size_t cleared_h_regions,
                               size_t freed_regions,
                               UncleanRegionList* list);


  // Returns "TRUE" iff "p" points into the allocated area of the heap.
  virtual bool is_in(const void* p) const;

  // Return "TRUE" iff the given object address is within the collection
  // set.
  inline bool obj_in_cs(oop obj);

  // Return "TRUE" iff the given object address is in the reserved
  // region of g1 (excluding the permanent generation).
  bool is_in_g1_reserved(const void* p) const {
    return _g1_reserved.contains(p);
  }

  // Returns a MemRegion that corresponds to the space that  has been
  // committed in the heap
  MemRegion g1_committed() {
    return _g1_committed;
  }

  NOT_PRODUCT(bool is_in_closed_subset(const void* p) const;)

  // Dirty card table entries covering a list of young regions.
  void dirtyCardsForYoungRegions(CardTableModRefBS* ct_bs, HeapRegion* list);

  // This resets the card table to all zeros.  It is used after
  // a collection pause which used the card table to claim cards.
  void cleanUpCardTable();

  // Iteration functions.

  // Iterate over all the ref-containing fields of all objects, calling
  // "cl.do_oop" on each.
  virtual void oop_iterate(OopClosure* cl) {
    oop_iterate(cl, true);
  }
  void oop_iterate(OopClosure* cl, bool do_perm);

  // Same as above, restricted to a memory region.
  virtual void oop_iterate(MemRegion mr, OopClosure* cl) {
    oop_iterate(mr, cl, true);
  }
  void oop_iterate(MemRegion mr, OopClosure* cl, bool do_perm);

  // Iterate over all objects, calling "cl.do_object" on each.
  virtual void object_iterate(ObjectClosure* cl) {
    object_iterate(cl, true);
  }
  virtual void safe_object_iterate(ObjectClosure* cl) {
    object_iterate(cl, true);
  }
  void object_iterate(ObjectClosure* cl, bool do_perm);

  // Iterate over all objects allocated since the last collection, calling
  // "cl.do_object" on each.  The heap must have been initialized properly
  // to support this function, or else this call will fail.
  virtual void object_iterate_since_last_GC(ObjectClosure* cl);

  // Iterate over all spaces in use in the heap, in ascending address order.
  virtual void space_iterate(SpaceClosure* cl);

  // Iterate over heap regions, in address order, terminating the
  // iteration early if the "doHeapRegion" method returns "true".
  void heap_region_iterate(HeapRegionClosure* blk);

  // Iterate over heap regions starting with r (or the first region if "r"
  // is NULL), in address order, terminating early if the "doHeapRegion"
  // method returns "true".
  void heap_region_iterate_from(HeapRegion* r, HeapRegionClosure* blk);

  // As above but starting from the region at index idx.
  void heap_region_iterate_from(int idx, HeapRegionClosure* blk);

  HeapRegion* region_at(size_t idx);

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
                                       int worker,
                                       jint claim_value);

  // It resets all the region claim values to the default.
  void reset_heap_region_claim_values();

#ifdef ASSERT
  bool check_heap_region_claim_values(jint claim_value);
#endif // ASSERT

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
  HeapRegion* heap_region_containing(const void* addr) const;

  // Like the above, but requires "addr" to be in the heap (to avoid a
  // null-check), and unlike the above, may return an continuing humongous
  // region.
  HeapRegion* heap_region_containing_raw(const void* addr) const;

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
    // 6920090: Temporarily disabled, because of lingering
    // instabilities related to RICM with G1. In the
    // interim, the option ReduceInitialCardMarksForG1
    // below is left solely as a debugging device at least
    // until 6920109 fixes the instabilities.
    return ReduceInitialCardMarksForG1;
  }

  virtual bool card_mark_must_follow_store() const {
    return true;
  }

  bool is_in_young(oop obj) {
    HeapRegion* hr = heap_region_containing(obj);
    return hr != NULL && hr->is_young();
  }

  // We don't need barriers for initializing stores to objects
  // in the young gen: for the SATB pre-barrier, there is no
  // pre-value that needs to be remembered; for the remembered-set
  // update logging post-barrier, we don't maintain remembered set
  // information for young gen objects. Note that non-generational
  // G1 does not have any "young" objects, should not elide
  // the rs logging barrier and so should always answer false below.
  // However, non-generational G1 (-XX:-G1Gen) appears to have
  // bit-rotted so was not tested below.
  virtual bool can_elide_initializing_store_barrier(oop new_obj) {
    // Re 6920090, 6920109 above.
    assert(ReduceInitialCardMarksForG1, "Else cannot be here");
    assert(G1Gen || !is_in_young(new_obj),
           "Non-generational G1 should never return true below");
    return is_in_young(new_obj);
  }

  // Can a compiler elide a store barrier when it writes
  // a permanent oop into the heap?  Applies when the compiler
  // is storing x to the heap, where x->is_perm() is true.
  virtual bool can_elide_permanent_oop_store_barriers() const {
    // At least until perm gen collection is also G1-ified, at
    // which point this should return false.
    return true;
  }

  virtual bool allocs_are_zero_filled();

  // The boundary between a "large" and "small" array of primitives, in
  // words.
  virtual size_t large_typearray_limit();

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

  // use_prev_marking == true  -> use "prev" marking information,
  // use_prev_marking == false -> use "next" marking information
  // NOTE: Only the "prev" marking information is guaranteed to be
  // consistent most of the time, so most calls to this should use
  // use_prev_marking == true. Currently, there is only one case where
  // this is called with use_prev_marking == false, which is to verify
  // the "next" marking information at the end of remark.
  void verify(bool allow_dirty, bool silent, bool use_prev_marking);

  // Override; it uses the "prev" marking information
  virtual void verify(bool allow_dirty, bool silent);
  // Default behavior by calling print(tty);
  virtual void print() const;
  // This calls print_on(st, PrintHeapAtGCExtended).
  virtual void print_on(outputStream* st) const;
  // If extended is true, it will print out information for all
  // regions in the heap by calling print_on_extended(st).
  virtual void print_on(outputStream* st, bool extended) const;
  virtual void print_on_extended(outputStream* st) const;

  virtual void print_gc_threads_on(outputStream* st) const;
  virtual void gc_threads_do(ThreadClosure* tc) const;

  // Override
  void print_tracing_info() const;

  // If "addr" is a pointer into the (reserved?) heap, returns a positive
  // number indicating the "arena" within the heap in which "addr" falls.
  // Or else returns 0.
  virtual int addr_to_arena_id(void* addr) const;

  // Convenience function to be used in situations where the heap type can be
  // asserted to be this type.
  static G1CollectedHeap* heap();

  void empty_young_list();

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

  // This is called from the marksweep collector which then does
  // a concurrent mark and verifies that the results agree with
  // the stop the world marking.
  void checkConcurrentMark();
  void do_sync_mark();

  bool isMarkedPrev(oop obj) const;
  bool isMarkedNext(oop obj) const;

  // use_prev_marking == true  -> use "prev" marking information,
  // use_prev_marking == false -> use "next" marking information
  bool is_obj_dead_cond(const oop obj,
                        const HeapRegion* hr,
                        const bool use_prev_marking) const {
    if (use_prev_marking) {
      return is_obj_dead(obj, hr);
    } else {
      return is_obj_ill(obj, hr);
    }
  }

  // Determine if an object is dead, given the object and also
  // the region to which the object belongs. An object is dead
  // iff a) it was not allocated since the last mark and b) it
  // is not marked.

  bool is_obj_dead(const oop obj, const HeapRegion* hr) const {
    return
      !hr->obj_allocated_since_prev_marking(obj) &&
      !isMarkedPrev(obj);
  }

  // This is used when copying an object to survivor space.
  // If the object is marked live, then we mark the copy live.
  // If the object is allocated since the start of this mark
  // cycle, then we mark the copy live.
  // If the object has been around since the previous mark
  // phase, and hasn't been marked yet during this phase,
  // then we don't mark it, we just wait for the
  // current marking cycle to get to it.

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

  // Added if it is in permanent gen it isn't dead.
  // Added if it is NULL it isn't dead.

  // use_prev_marking == true  -> use "prev" marking information,
  // use_prev_marking == false -> use "next" marking information
  bool is_obj_dead_cond(const oop obj,
                        const bool use_prev_marking) {
    if (use_prev_marking) {
      return is_obj_dead(obj);
    } else {
      return is_obj_ill(obj);
    }
  }

  bool is_obj_dead(const oop obj) {
    const HeapRegion* hr = heap_region_containing(obj);
    if (hr == NULL) {
      if (Universe::heap()->is_in_permanent(obj))
        return false;
      else if (obj == NULL) return false;
      else return true;
    }
    else return is_obj_dead(obj, hr);
  }

  bool is_obj_ill(const oop obj) {
    const HeapRegion* hr = heap_region_containing(obj);
    if (hr == NULL) {
      if (Universe::heap()->is_in_permanent(obj))
        return false;
      else if (obj == NULL) return false;
      else return true;
    }
    else return is_obj_ill(obj, hr);
  }

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

  // <NEW PREDICTION>

  double predict_region_elapsed_time_ms(HeapRegion* hr, bool young);
  void check_if_region_is_too_expensive(double predicted_time_ms);
  size_t pending_card_num();
  size_t max_pending_card_num();
  size_t cards_scanned();

  // </NEW PREDICTION>

protected:
  size_t _max_heap_capacity;

public:
  // Temporary: call to mark things unimplemented for the G1 heap (e.g.,
  // MemoryService).  In productization, we can make this assert false
  // to catch such places (as well as searching for calls to this...)
  static void g1_unimplemented();

};

#define use_local_bitmaps         1
#define verify_local_bitmaps      0
#define oop_buffer_length       256

#ifndef PRODUCT
class GCLabBitMap;
class GCLabBitMapClosure: public BitMapClosure {
private:
  ConcurrentMark* _cm;
  GCLabBitMap*    _bitmap;

public:
  GCLabBitMapClosure(ConcurrentMark* cm,
                     GCLabBitMap* bitmap) {
    _cm     = cm;
    _bitmap = bitmap;
  }

  virtual bool do_bit(size_t offset);
};
#endif // !PRODUCT

class GCLabBitMap: public BitMap {
private:
  ConcurrentMark* _cm;

  int       _shifter;
  size_t    _bitmap_word_covers_words;

  // beginning of the heap
  HeapWord* _heap_start;

  // this is the actual start of the GCLab
  HeapWord* _real_start_word;

  // this is the actual end of the GCLab
  HeapWord* _real_end_word;

  // this is the first word, possibly located before the actual start
  // of the GCLab, that corresponds to the first bit of the bitmap
  HeapWord* _start_word;

  // size of a GCLab in words
  size_t _gclab_word_size;

  static int shifter() {
    return MinObjAlignment - 1;
  }

  // how many heap words does a single bitmap word corresponds to?
  static size_t bitmap_word_covers_words() {
    return BitsPerWord << shifter();
  }

  size_t gclab_word_size() const {
    return _gclab_word_size;
  }

  // Calculates actual GCLab size in words
  size_t gclab_real_word_size() const {
    return bitmap_size_in_bits(pointer_delta(_real_end_word, _start_word))
           / BitsPerWord;
  }

  static size_t bitmap_size_in_bits(size_t gclab_word_size) {
    size_t bits_in_bitmap = gclab_word_size >> shifter();
    // We are going to ensure that the beginning of a word in this
    // bitmap also corresponds to the beginning of a word in the
    // global marking bitmap. To handle the case where a GCLab
    // starts from the middle of the bitmap, we need to add enough
    // space (i.e. up to a bitmap word) to ensure that we have
    // enough bits in the bitmap.
    return bits_in_bitmap + BitsPerWord - 1;
  }
public:
  GCLabBitMap(HeapWord* heap_start, size_t gclab_word_size)
    : BitMap(bitmap_size_in_bits(gclab_word_size)),
      _cm(G1CollectedHeap::heap()->concurrent_mark()),
      _shifter(shifter()),
      _bitmap_word_covers_words(bitmap_word_covers_words()),
      _heap_start(heap_start),
      _gclab_word_size(gclab_word_size),
      _real_start_word(NULL),
      _real_end_word(NULL),
      _start_word(NULL)
  {
    guarantee( size_in_words() >= bitmap_size_in_words(),
               "just making sure");
  }

  inline unsigned heapWordToOffset(HeapWord* addr) {
    unsigned offset = (unsigned) pointer_delta(addr, _start_word) >> _shifter;
    assert(offset < size(), "offset should be within bounds");
    return offset;
  }

  inline HeapWord* offsetToHeapWord(size_t offset) {
    HeapWord* addr =  _start_word + (offset << _shifter);
    assert(_real_start_word <= addr && addr < _real_end_word, "invariant");
    return addr;
  }

  bool fields_well_formed() {
    bool ret1 = (_real_start_word == NULL) &&
                (_real_end_word == NULL) &&
                (_start_word == NULL);
    if (ret1)
      return true;

    bool ret2 = _real_start_word >= _start_word &&
      _start_word < _real_end_word &&
      (_real_start_word + _gclab_word_size) == _real_end_word &&
      (_start_word + _gclab_word_size + _bitmap_word_covers_words)
                                                              > _real_end_word;
    return ret2;
  }

  inline bool mark(HeapWord* addr) {
    guarantee(use_local_bitmaps, "invariant");
    assert(fields_well_formed(), "invariant");

    if (addr >= _real_start_word && addr < _real_end_word) {
      assert(!isMarked(addr), "should not have already been marked");

      // first mark it on the bitmap
      at_put(heapWordToOffset(addr), true);

      return true;
    } else {
      return false;
    }
  }

  inline bool isMarked(HeapWord* addr) {
    guarantee(use_local_bitmaps, "invariant");
    assert(fields_well_formed(), "invariant");

    return at(heapWordToOffset(addr));
  }

  void set_buffer(HeapWord* start) {
    guarantee(use_local_bitmaps, "invariant");
    clear();

    assert(start != NULL, "invariant");
    _real_start_word = start;
    _real_end_word   = start + _gclab_word_size;

    size_t diff =
      pointer_delta(start, _heap_start) % _bitmap_word_covers_words;
    _start_word = start - diff;

    assert(fields_well_formed(), "invariant");
  }

#ifndef PRODUCT
  void verify() {
    // verify that the marks have been propagated
    GCLabBitMapClosure cl(_cm, this);
    iterate(&cl);
  }
#endif // PRODUCT

  void retire() {
    guarantee(use_local_bitmaps, "invariant");
    assert(fields_well_formed(), "invariant");

    if (_start_word != NULL) {
      CMBitMap*       mark_bitmap = _cm->nextMarkBitMap();

      // this means that the bitmap was set up for the GCLab
      assert(_real_start_word != NULL && _real_end_word != NULL, "invariant");

      mark_bitmap->mostly_disjoint_range_union(this,
                                0, // always start from the start of the bitmap
                                _start_word,
                                gclab_real_word_size());
      _cm->grayRegionIfNecessary(MemRegion(_real_start_word, _real_end_word));

#ifndef PRODUCT
      if (use_local_bitmaps && verify_local_bitmaps)
        verify();
#endif // PRODUCT
    } else {
      assert(_real_start_word == NULL && _real_end_word == NULL, "invariant");
    }
  }

  size_t bitmap_size_in_words() const {
    return (bitmap_size_in_bits(gclab_word_size()) + BitsPerWord - 1) / BitsPerWord;
  }

};

class G1ParGCAllocBuffer: public ParGCAllocBuffer {
private:
  bool        _retired;
  bool        _during_marking;
  GCLabBitMap _bitmap;

public:
  G1ParGCAllocBuffer(size_t gclab_word_size) :
    ParGCAllocBuffer(gclab_word_size),
    _during_marking(G1CollectedHeap::heap()->mark_in_progress()),
    _bitmap(G1CollectedHeap::heap()->reserved_region().start(), gclab_word_size),
    _retired(false)
  { }

  inline bool mark(HeapWord* addr) {
    guarantee(use_local_bitmaps, "invariant");
    assert(_during_marking, "invariant");
    return _bitmap.mark(addr);
  }

  inline void set_buf(HeapWord* buf) {
    if (use_local_bitmaps && _during_marking)
      _bitmap.set_buffer(buf);
    ParGCAllocBuffer::set_buf(buf);
    _retired = false;
  }

  inline void retire(bool end_of_gc, bool retain) {
    if (_retired)
      return;
    if (use_local_bitmaps && _during_marking) {
      _bitmap.retire();
    }
    ParGCAllocBuffer::retire(end_of_gc, retain);
    _retired = true;
  }
};

class G1ParScanThreadState : public StackObj {
protected:
  G1CollectedHeap* _g1h;
  RefToScanQueue*  _refs;
  DirtyCardQueue   _dcq;
  CardTableModRefBS* _ct_bs;
  G1RemSet* _g1_rem;

  G1ParGCAllocBuffer  _surviving_alloc_buffer;
  G1ParGCAllocBuffer  _tenured_alloc_buffer;
  G1ParGCAllocBuffer* _alloc_buffers[GCAllocPurposeCount];
  ageTable            _age_table;

  size_t           _alloc_buffer_waste;
  size_t           _undo_waste;

  OopsInHeapRegionClosure*      _evac_failure_cl;
  G1ParScanHeapEvacClosure*     _evac_cl;
  G1ParScanPartialArrayClosure* _partial_scan_cl;

  int _hash_seed;
  int _queue_num;

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
  G1ParScanThreadState(G1CollectedHeap* g1h, int queue_num);

  ~G1ParScanThreadState() {
    FREE_C_HEAP_ARRAY(size_t, _surviving_young_words_base);
  }

  RefToScanQueue*   refs()            { return _refs;             }
  ageTable*         age_table()       { return &_age_table;       }

  G1ParGCAllocBuffer* alloc_buffer(GCAllocPurpose purpose) {
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
      G1ParGCAllocBuffer* alloc_buf = alloc_buffer(purpose);
      assert(gclab_word_size == alloc_buf->word_sz(),
             "dynamic resizing is not supported");
      add_to_alloc_buffer_waste(alloc_buf->words_remaining());
      alloc_buf->retire(false, false);

      HeapWord* buf = _g1h->par_allocate_during_gc(purpose, gclab_word_size);
      if (buf == NULL) return NULL; // Let caller handle allocation failure.
      // Otherwise.
      alloc_buf->set_buf(buf);

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
  int  queue_num() { return _queue_num; }

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
      _alloc_buffers[ap]->retire(true, false);
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

public:
  void trim_queue();
};

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_G1COLLECTEDHEAP_HPP
