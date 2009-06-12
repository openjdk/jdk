/*
 * Copyright 2001-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

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

// If want to accumulate detailed statistics on work queues
// turn this on.
#define G1_DETAILED_STATS 0

#if G1_DETAILED_STATS
#  define IF_G1_DETAILED_STATS(code) code
#else
#  define IF_G1_DETAILED_STATS(code)
#endif

typedef GenericTaskQueue<oop*>    RefToScanQueue;
typedef GenericTaskQueueSet<oop*> RefToScanQueueSet;

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

  HeapRegion* _scan_only_head;
  HeapRegion* _scan_only_tail;
  size_t      _length;
  size_t      _scan_only_length;

  size_t      _last_sampled_rs_lengths;
  size_t      _sampled_rs_lengths;
  HeapRegion* _curr;
  HeapRegion* _curr_scan_only;

  HeapRegion* _survivor_head;
  HeapRegion* _survivor_tail;
  size_t      _survivor_length;

  void          empty_list(HeapRegion* list);

public:
  YoungList(G1CollectedHeap* g1h);

  void          push_region(HeapRegion* hr);
  void          add_survivor_region(HeapRegion* hr);
  HeapRegion*   pop_region();
  void          empty_list();
  bool          is_empty() { return _length == 0; }
  size_t        length() { return _length; }
  size_t        scan_only_length() { return _scan_only_length; }
  size_t        survivor_length() { return _survivor_length; }

  void rs_length_sampling_init();
  bool rs_length_sampling_more();
  void rs_length_sampling_next();

  void reset_sampled_info() {
    _last_sampled_rs_lengths =   0;
  }
  size_t sampled_rs_lengths() { return _last_sampled_rs_lengths; }

  // for development purposes
  void reset_auxilary_lists();
  HeapRegion* first_region() { return _head; }
  HeapRegion* first_scan_only_region() { return _scan_only_head; }
  HeapRegion* first_survivor_region() { return _survivor_head; }
  HeapRegion* last_survivor_region() { return _survivor_tail; }
  HeapRegion* par_get_next_scan_only_region() {
    MutexLockerEx x(ParGCRareEvent_lock, Mutex::_no_safepoint_check_flag);
    HeapRegion* ret = _curr_scan_only;
    if (ret != NULL)
      _curr_scan_only = ret->get_next_young_region();
    return ret;
  }

  // debugging
  bool          check_list_well_formed();
  bool          check_list_empty(bool ignore_scan_only_list,
                                 bool check_sample = true);
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
  enum SomePrivateConstants {
    VeryLargeInBytes = HeapRegion::GrainBytes/2,
    VeryLargeInWords = VeryLargeInBytes/HeapWordSize,
    MinHeapDeltaBytes = 10 * HeapRegion::GrainBytes,      // FIXME
    NumAPIs = HeapRegion::MaxAge
  };

  // The one and only G1CollectedHeap, so static functions can find it.
  static G1CollectedHeap* _g1h;

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
  virtual HeapWord* humongousObjAllocate(size_t word_size);

  // If possible, allocate a block of the given word_size, else return "NULL".
  // Returning NULL will trigger GC or heap expansion.
  // These two methods have rather awkward pre- and
  // post-conditions. If they are called outside a safepoint, then
  // they assume that the caller is holding the heap lock. Upon return
  // they release the heap lock, if they are returning a non-NULL
  // value. attempt_allocation_slow() also dirties the cards of a
  // newly-allocated young region after it releases the heap
  // lock. This change in interface was the neatest way to achieve
  // this card dirtying without affecting mem_allocate(), which is a
  // more frequently called method. We tried two or three different
  // approaches, but they were even more hacky.
  HeapWord* attempt_allocation(size_t word_size,
                               bool permit_collection_pause = true);

  HeapWord* attempt_allocation_slow(size_t word_size,
                                    bool permit_collection_pause = true);

  // Allocate blocks during garbage collection. Will ensure an
  // allocation region, either by picking one or expanding the
  // heap, and then allocate a block of the given size. The block
  // may not be a humongous - it must fit into a single heap region.
  HeapWord* allocate_during_gc(GCAllocPurpose purpose, size_t word_size);
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

  // Helper function for two callbacks below.
  // "full", if true, indicates that the GC is for a System.gc() request,
  // and should collect the entire heap.  If "clear_all_soft_refs" is true,
  // all soft references are cleared during the GC.  If "full" is false,
  // "word_size" describes the allocation that the GC should
  // attempt (at least) to satisfy.
  void do_collection(bool full, bool clear_all_soft_refs,
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
  HeapWord* satisfy_failed_allocation(size_t word_size);

  // Attempting to expand the heap sufficiently
  // to support an allocation of the given "word_size".  If
  // successful, perform the allocation and return the address of the
  // allocated block, or else "NULL".
  virtual HeapWord* expand_and_allocate(size_t word_size);

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
    assert(0 <= (size_t) index && (size_t) index < _in_cset_fast_test_length,
           "invariant");
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

protected:

  // Shrink the garbage-first heap by at most the given size (in bytes!).
  // (Rounds down to a HeapRegion boundary.)
  virtual void shrink(size_t expand_bytes);
  void shrink_helper(size_t expand_bytes);

  // Do an incremental collection: identify a collection set, and evacuate
  // its live objects elsewhere.
  virtual void do_collection_pause();

  // The guts of the incremental collection pause, executed by the vm
  // thread.
  virtual void do_collection_pause_at_safepoint();

  // Actually do the work of evacuating the collection set.
  virtual void evacuate_collection_set();

  // If this is an appropriate right time, do a collection pause.
  // The "word_size" argument, if non-zero, indicates the size of an
  // allocation request that is prompting this query.
  void do_collection_pause_if_appropriate(size_t word_size);

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

  // After a collection pause, make the regions in the CS into free
  // regions.
  void free_collection_set(HeapRegion* cs_head);

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
                               OopsInHeapRegionClosure* scan_so,
                               OopsInGenClosure* scan_perm,
                               int worker_i);

  void scan_scan_only_set(OopsInHeapRegionClosure* oc,
                          int worker_i);
  void scan_scan_only_region(HeapRegion* hr,
                             OopsInHeapRegionClosure* oc,
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
  void handle_evacuation_failure(oop obj);
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
  void set_refine_cte_cl_concurrency(bool concurrent);

  RefToScanQueue *task_queue(int i);

  // A set of cards where updates happened during the GC
  DirtyCardQueueSet& dirty_card_queue_set() { return _dirty_card_queue_set; }

  // Create a G1CollectedHeap with the specified policy.
  // Must call the initialize method afterwards.
  // May not return if something goes wrong.
  G1CollectedHeap(G1CollectorPolicy* policy);

  // Initialize the G1CollectedHeap to have the initial and
  // maximum sizes, permanent generation, and remembered and barrier sets
  // specified by the policy object.
  jint initialize();

  void ref_processing_init();

  void set_par_threads(int t) {
    SharedHeap::set_par_threads(t);
    _process_strong_tasks->set_par_threads(t);
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

  void iterate_dirty_card_closure(bool concurrent, int worker_i);

  // The shared block offset table array.
  G1BlockOffsetSharedArray* bot_shared() const { return _bot_shared; }

  // Reference Processing accessor
  ReferenceProcessor* ref_processor() { return _ref_processor; }

  // Reserved (g1 only; super method includes perm), capacity and the used
  // portion in bytes.
  size_t g1_reserved_obj_bytes() { return _g1_reserved.byte_size(); }
  virtual size_t capacity() const;
  virtual size_t used() const;
  size_t recalculate_used() const;
#ifndef PRODUCT
  size_t recalculate_used_regions() const;
#endif // PRODUCT

  // These virtual functions do the actual allocation.
  virtual HeapWord* mem_allocate(size_t word_size,
                                 bool   is_noref,
                                 bool   is_tlab,
                                 bool* gc_overhead_limit_was_exceeded);

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

  NOT_PRODUCT( bool is_in_closed_subset(const void* p) const; )

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
  virtual HeapWord* allocate_new_tlab(size_t size);

  // Can a compiler initialize a new object without store barriers?
  // This permission only extends from the creation of a new object
  // via a TLAB up to the first subsequent safepoint.
  virtual bool can_elide_tlab_store_barriers() const {
    // Since G1's TLAB's may, on occasion, come from non-young regions
    // as well. (Is there a flag controlling that? XXX)
    return false;
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
    return word_size >= VeryLargeInWords;
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
  virtual void verify(bool allow_dirty, bool silent);
  virtual void print() const;
  virtual void print_on(outputStream* st) const;

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
  bool should_set_young_locked();

  void set_region_short_lived_locked(HeapRegion* hr);
  // add appropriate methods for any other surv rate groups

  void young_list_rs_length_sampling_init() {
    _young_list->rs_length_sampling_init();
  }
  bool young_list_rs_length_sampling_more() {
    return _young_list->rs_length_sampling_more();
  }
  void young_list_rs_length_sampling_next() {
    _young_list->rs_length_sampling_next();
  }
  size_t young_list_sampled_rs_lengths() {
    return _young_list->sampled_rs_lengths();
  }

  size_t young_list_length()   { return _young_list->length(); }
  size_t young_list_scan_only_length() {
                                      return _young_list->scan_only_length(); }

  HeapRegion* pop_region_from_young_list() {
    return _young_list->pop_region();
  }

  HeapRegion* young_list_first_region() {
    return _young_list->first_region();
  }

  // debugging
  bool check_young_list_well_formed() {
    return _young_list->check_list_well_formed();
  }
  bool check_young_list_empty(bool ignore_scan_only_list,
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

  bool is_obj_dead(oop obj) {
    HeapRegion* hr = heap_region_containing(obj);
    if (hr == NULL) {
      if (Universe::heap()->is_in_permanent(obj))
        return false;
      else if (obj == NULL) return false;
      else return true;
    }
    else return is_obj_dead(obj, hr);
  }

  bool is_obj_ill(oop obj) {
    HeapRegion* hr = heap_region_containing(obj);
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

//  debug_only(static void check_for_valid_allocation_state();)

public:
  // Temporary: call to mark things unimplemented for the G1 heap (e.g.,
  // MemoryService).  In productization, we can make this assert false
  // to catch such places (as well as searching for calls to this...)
  static void g1_unimplemented();

};

// Local Variables: ***
// c-indentation-style: gnu ***
// End: ***
