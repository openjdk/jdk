/*
 * Copyright (c) 2001, 2009, Oracle and/or its affiliates. All rights reserved.
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

// Classes in support of keeping track of promotions into a non-Contiguous
// space, in this case a CompactibleFreeListSpace.

// Forward declarations
class CompactibleFreeListSpace;
class BlkClosure;
class BlkClosureCareful;
class UpwardsObjectClosure;
class ObjectClosureCareful;
class Klass;

class LinearAllocBlock VALUE_OBJ_CLASS_SPEC {
 public:
  LinearAllocBlock() : _ptr(0), _word_size(0), _refillSize(0),
    _allocation_size_limit(0) {}
  void set(HeapWord* ptr, size_t word_size, size_t refill_size,
    size_t allocation_size_limit) {
    _ptr = ptr;
    _word_size = word_size;
    _refillSize = refill_size;
    _allocation_size_limit = allocation_size_limit;
  }
  HeapWord* _ptr;
  size_t    _word_size;
  size_t    _refillSize;
  size_t    _allocation_size_limit;  // largest size that will be allocated
};

// Concrete subclass of CompactibleSpace that implements
// a free list space, such as used in the concurrent mark sweep
// generation.

class CompactibleFreeListSpace: public CompactibleSpace {
  friend class VMStructs;
  friend class ConcurrentMarkSweepGeneration;
  friend class ASConcurrentMarkSweepGeneration;
  friend class CMSCollector;
  friend class CMSPermGenGen;
  // Local alloc buffer for promotion into this space.
  friend class CFLS_LAB;

  // "Size" of chunks of work (executed during parallel remark phases
  // of CMS collection); this probably belongs in CMSCollector, although
  // it's cached here because it's used in
  // initialize_sequential_subtasks_for_rescan() which modifies
  // par_seq_tasks which also lives in Space. XXX
  const size_t _rescan_task_size;
  const size_t _marking_task_size;

  // Yet another sequential tasks done structure. This supports
  // CMS GC, where we have threads dynamically
  // claiming sub-tasks from a larger parallel task.
  SequentialSubTasksDone _conc_par_seq_tasks;

  BlockOffsetArrayNonContigSpace _bt;

  CMSCollector* _collector;
  ConcurrentMarkSweepGeneration* _gen;

  // Data structures for free blocks (used during allocation/sweeping)

  // Allocation is done linearly from two different blocks depending on
  // whether the request is small or large, in an effort to reduce
  // fragmentation. We assume that any locking for allocation is done
  // by the containing generation. Thus, none of the methods in this
  // space are re-entrant.
  enum SomeConstants {
    SmallForLinearAlloc = 16,        // size < this then use _sLAB
    SmallForDictionary  = 257,       // size < this then use _indexedFreeList
    IndexSetSize        = SmallForDictionary  // keep this odd-sized
  };
  static int IndexSetStart;
  static int IndexSetStride;

 private:
  enum FitStrategyOptions {
    FreeBlockStrategyNone = 0,
    FreeBlockBestFitFirst
  };

  PromotionInfo _promoInfo;

  // helps to impose a global total order on freelistLock ranks;
  // assumes that CFLSpace's are allocated in global total order
  static int   _lockRank;

  // a lock protecting the free lists and free blocks;
  // mutable because of ubiquity of locking even for otherwise const methods
  mutable Mutex _freelistLock;
  // locking verifier convenience function
  void assert_locked() const PRODUCT_RETURN;
  void assert_locked(const Mutex* lock) const PRODUCT_RETURN;

  // Linear allocation blocks
  LinearAllocBlock _smallLinearAllocBlock;

  FreeBlockDictionary::DictionaryChoice _dictionaryChoice;
  FreeBlockDictionary* _dictionary;    // ptr to dictionary for large size blocks

  FreeList _indexedFreeList[IndexSetSize];
                                       // indexed array for small size blocks
  // allocation stategy
  bool       _fitStrategy;      // Use best fit strategy.
  bool       _adaptive_freelists; // Use adaptive freelists

  // This is an address close to the largest free chunk in the heap.
  // It is currently assumed to be at the end of the heap.  Free
  // chunks with addresses greater than nearLargestChunk are coalesced
  // in an effort to maintain a large chunk at the end of the heap.
  HeapWord*  _nearLargestChunk;

  // Used to keep track of limit of sweep for the space
  HeapWord* _sweep_limit;

  // Support for compacting cms
  HeapWord* cross_threshold(HeapWord* start, HeapWord* end);
  HeapWord* forward(oop q, size_t size, CompactPoint* cp, HeapWord* compact_top);

  // Initialization helpers.
  void initializeIndexedFreeListArray();

  // Extra stuff to manage promotion parallelism.

  // a lock protecting the dictionary during par promotion allocation.
  mutable Mutex _parDictionaryAllocLock;
  Mutex* parDictionaryAllocLock() const { return &_parDictionaryAllocLock; }

  // Locks protecting the exact lists during par promotion allocation.
  Mutex* _indexedFreeListParLocks[IndexSetSize];

  // Attempt to obtain up to "n" blocks of the size "word_sz" (which is
  // required to be smaller than "IndexSetSize".)  If successful,
  // adds them to "fl", which is required to be an empty free list.
  // If the count of "fl" is negative, it's absolute value indicates a
  // number of free chunks that had been previously "borrowed" from global
  // list of size "word_sz", and must now be decremented.
  void par_get_chunk_of_blocks(size_t word_sz, size_t n, FreeList* fl);

  // Allocation helper functions
  // Allocate using a strategy that takes from the indexed free lists
  // first.  This allocation strategy assumes a companion sweeping
  // strategy that attempts to keep the needed number of chunks in each
  // indexed free lists.
  HeapWord* allocate_adaptive_freelists(size_t size);
  // Allocate from the linear allocation buffers first.  This allocation
  // strategy assumes maximal coalescing can maintain chunks large enough
  // to be used as linear allocation buffers.
  HeapWord* allocate_non_adaptive_freelists(size_t size);

  // Gets a chunk from the linear allocation block (LinAB).  If there
  // is not enough space in the LinAB, refills it.
  HeapWord*  getChunkFromLinearAllocBlock(LinearAllocBlock* blk, size_t size);
  HeapWord*  getChunkFromSmallLinearAllocBlock(size_t size);
  // Get a chunk from the space remaining in the linear allocation block.  Do
  // not attempt to refill if the space is not available, return NULL.  Do the
  // repairs on the linear allocation block as appropriate.
  HeapWord*  getChunkFromLinearAllocBlockRemainder(LinearAllocBlock* blk, size_t size);
  inline HeapWord*  getChunkFromSmallLinearAllocBlockRemainder(size_t size);

  // Helper function for getChunkFromIndexedFreeList.
  // Replenish the indexed free list for this "size".  Do not take from an
  // underpopulated size.
  FreeChunk*  getChunkFromIndexedFreeListHelper(size_t size, bool replenish = true);

  // Get a chunk from the indexed free list.  If the indexed free list
  // does not have a free chunk, try to replenish the indexed free list
  // then get the free chunk from the replenished indexed free list.
  inline FreeChunk* getChunkFromIndexedFreeList(size_t size);

  // The returned chunk may be larger than requested (or null).
  FreeChunk* getChunkFromDictionary(size_t size);
  // The returned chunk is the exact size requested (or null).
  FreeChunk* getChunkFromDictionaryExact(size_t size);

  // Find a chunk in the indexed free list that is the best
  // fit for size "numWords".
  FreeChunk* bestFitSmall(size_t numWords);
  // For free list "fl" of chunks of size > numWords,
  // remove a chunk, split off a chunk of size numWords
  // and return it.  The split off remainder is returned to
  // the free lists.  The old name for getFromListGreater
  // was lookInListGreater.
  FreeChunk* getFromListGreater(FreeList* fl, size_t numWords);
  // Get a chunk in the indexed free list or dictionary,
  // by considering a larger chunk and splitting it.
  FreeChunk* getChunkFromGreater(size_t numWords);
  //  Verify that the given chunk is in the indexed free lists.
  bool verifyChunkInIndexedFreeLists(FreeChunk* fc) const;
  // Remove the specified chunk from the indexed free lists.
  void       removeChunkFromIndexedFreeList(FreeChunk* fc);
  // Remove the specified chunk from the dictionary.
  void       removeChunkFromDictionary(FreeChunk* fc);
  // Split a free chunk into a smaller free chunk of size "new_size".
  // Return the smaller free chunk and return the remainder to the
  // free lists.
  FreeChunk* splitChunkAndReturnRemainder(FreeChunk* chunk, size_t new_size);
  // Add a chunk to the free lists.
  void       addChunkToFreeLists(HeapWord* chunk, size_t size);
  // Add a chunk to the free lists, preferring to suffix it
  // to the last free chunk at end of space if possible, and
  // updating the block census stats as well as block offset table.
  // Take any locks as appropriate if we are multithreaded.
  void       addChunkToFreeListsAtEndRecordingStats(HeapWord* chunk, size_t size);
  // Add a free chunk to the indexed free lists.
  void       returnChunkToFreeList(FreeChunk* chunk);
  // Add a free chunk to the dictionary.
  void       returnChunkToDictionary(FreeChunk* chunk);

  // Functions for maintaining the linear allocation buffers (LinAB).
  // Repairing a linear allocation block refers to operations
  // performed on the remainder of a LinAB after an allocation
  // has been made from it.
  void       repairLinearAllocationBlocks();
  void       repairLinearAllocBlock(LinearAllocBlock* blk);
  void       refillLinearAllocBlock(LinearAllocBlock* blk);
  void       refillLinearAllocBlockIfNeeded(LinearAllocBlock* blk);
  void       refillLinearAllocBlocksIfNeeded();

  void       verify_objects_initialized() const;

  // Statistics reporting helper functions
  void       reportFreeListStatistics() const;
  void       reportIndexedFreeListStatistics() const;
  size_t     maxChunkSizeInIndexedFreeLists() const;
  size_t     numFreeBlocksInIndexedFreeLists() const;
  // Accessor
  HeapWord* unallocated_block() const {
    HeapWord* ub = _bt.unallocated_block();
    assert(ub >= bottom() &&
           ub <= end(), "space invariant");
    return ub;
  }
  void freed(HeapWord* start, size_t size) {
    _bt.freed(start, size);
  }

 protected:
  // reset the indexed free list to its initial empty condition.
  void resetIndexedFreeListArray();
  // reset to an initial state with a single free block described
  // by the MemRegion parameter.
  void reset(MemRegion mr);
  // Return the total number of words in the indexed free lists.
  size_t     totalSizeInIndexedFreeLists() const;

 public:
  // Constructor...
  CompactibleFreeListSpace(BlockOffsetSharedArray* bs, MemRegion mr,
                           bool use_adaptive_freelists,
                           FreeBlockDictionary::DictionaryChoice);
  // accessors
  bool bestFitFirst() { return _fitStrategy == FreeBlockBestFitFirst; }
  FreeBlockDictionary* dictionary() const { return _dictionary; }
  HeapWord* nearLargestChunk() const { return _nearLargestChunk; }
  void set_nearLargestChunk(HeapWord* v) { _nearLargestChunk = v; }

  // Set CMS global values
  static void set_cms_values();

  // Return the free chunk at the end of the space.  If no such
  // chunk exists, return NULL.
  FreeChunk* find_chunk_at_end();

  bool adaptive_freelists() const { return _adaptive_freelists; }

  void set_collector(CMSCollector* collector) { _collector = collector; }

  // Support for parallelization of rescan and marking
  const size_t rescan_task_size()  const { return _rescan_task_size;  }
  const size_t marking_task_size() const { return _marking_task_size; }
  SequentialSubTasksDone* conc_par_seq_tasks() {return &_conc_par_seq_tasks; }
  void initialize_sequential_subtasks_for_rescan(int n_threads);
  void initialize_sequential_subtasks_for_marking(int n_threads,
         HeapWord* low = NULL);

  // Space enquiries
  size_t used() const;
  size_t free() const;
  size_t max_alloc_in_words() const;
  // XXX: should have a less conservative used_region() than that of
  // Space; we could consider keeping track of highest allocated
  // address and correcting that at each sweep, as the sweeper
  // goes through the entire allocated part of the generation. We
  // could also use that information to keep the sweeper from
  // sweeping more than is necessary. The allocator and sweeper will
  // of course need to synchronize on this, since the sweeper will
  // try to bump down the address and the allocator will try to bump it up.
  // For now, however, we'll just use the default used_region()
  // which overestimates the region by returning the entire
  // committed region (this is safe, but inefficient).

  // Returns a subregion of the space containing all the objects in
  // the space.
  MemRegion used_region() const {
    return MemRegion(bottom(),
                     BlockOffsetArrayUseUnallocatedBlock ?
                     unallocated_block() : end());
  }

  // This is needed because the default implementation uses block_start()
  // which can;t be used at certain times (for example phase 3 of mark-sweep).
  // A better fix is to change the assertions in phase 3 of mark-sweep to
  // use is_in_reserved(), but that is deferred since the is_in() assertions
  // are buried through several layers of callers and are used elsewhere
  // as well.
  bool is_in(const void* p) const {
    return used_region().contains(p);
  }

  virtual bool is_free_block(const HeapWord* p) const;

  // Resizing support
  void set_end(HeapWord* value);  // override

  // mutual exclusion support
  Mutex* freelistLock() const { return &_freelistLock; }

  // Iteration support
  void oop_iterate(MemRegion mr, OopClosure* cl);
  void oop_iterate(OopClosure* cl);

  void object_iterate(ObjectClosure* blk);
  // Apply the closure to each object in the space whose references
  // point to objects in the heap.  The usage of CompactibleFreeListSpace
  // by the ConcurrentMarkSweepGeneration for concurrent GC's allows
  // objects in the space with references to objects that are no longer
  // valid.  For example, an object may reference another object
  // that has already been sweep up (collected).  This method uses
  // obj_is_alive() to determine whether it is safe to iterate of
  // an object.
  void safe_object_iterate(ObjectClosure* blk);
  void object_iterate_mem(MemRegion mr, UpwardsObjectClosure* cl);

  // Requires that "mr" be entirely within the space.
  // Apply "cl->do_object" to all objects that intersect with "mr".
  // If the iteration encounters an unparseable portion of the region,
  // terminate the iteration and return the address of the start of the
  // subregion that isn't done.  Return of "NULL" indicates that the
  // interation completed.
  virtual HeapWord*
       object_iterate_careful_m(MemRegion mr,
                                ObjectClosureCareful* cl);
  virtual HeapWord*
       object_iterate_careful(ObjectClosureCareful* cl);

  // Override: provides a DCTO_CL specific to this kind of space.
  DirtyCardToOopClosure* new_dcto_cl(OopClosure* cl,
                                     CardTableModRefBS::PrecisionStyle precision,
                                     HeapWord* boundary);

  void blk_iterate(BlkClosure* cl);
  void blk_iterate_careful(BlkClosureCareful* cl);
  HeapWord* block_start_const(const void* p) const;
  HeapWord* block_start_careful(const void* p) const;
  size_t block_size(const HeapWord* p) const;
  size_t block_size_no_stall(HeapWord* p, const CMSCollector* c) const;
  bool block_is_obj(const HeapWord* p) const;
  bool obj_is_alive(const HeapWord* p) const;
  size_t block_size_nopar(const HeapWord* p) const;
  bool block_is_obj_nopar(const HeapWord* p) const;

  // iteration support for promotion
  void save_marks();
  bool no_allocs_since_save_marks();
  void object_iterate_since_last_GC(ObjectClosure* cl);

  // iteration support for sweeping
  void save_sweep_limit() {
    _sweep_limit = BlockOffsetArrayUseUnallocatedBlock ?
                   unallocated_block() : end();
  }
  NOT_PRODUCT(
    void clear_sweep_limit() { _sweep_limit = NULL; }
  )
  HeapWord* sweep_limit() { return _sweep_limit; }

  // Apply "blk->do_oop" to the addresses of all reference fields in objects
  // promoted into this generation since the most recent save_marks() call.
  // Fields in objects allocated by applications of the closure
  // *are* included in the iteration. Thus, when the iteration completes
  // there should be no further such objects remaining.
  #define CFLS_OOP_SINCE_SAVE_MARKS_DECL(OopClosureType, nv_suffix)  \
    void oop_since_save_marks_iterate##nv_suffix(OopClosureType* blk);
  ALL_SINCE_SAVE_MARKS_CLOSURES(CFLS_OOP_SINCE_SAVE_MARKS_DECL)
  #undef CFLS_OOP_SINCE_SAVE_MARKS_DECL

  // Allocation support
  HeapWord* allocate(size_t size);
  HeapWord* par_allocate(size_t size);

  oop       promote(oop obj, size_t obj_size);
  void      gc_prologue();
  void      gc_epilogue();

  // This call is used by a containing CMS generation / collector
  // to inform the CFLS space that a sweep has been completed
  // and that the space can do any related house-keeping functions.
  void      sweep_completed();

  // For an object in this space, the mark-word's two
  // LSB's having the value [11] indicates that it has been
  // promoted since the most recent call to save_marks() on
  // this generation and has not subsequently been iterated
  // over (using oop_since_save_marks_iterate() above).
  // This property holds only for single-threaded collections,
  // and is typically used for Cheney scans; for MT scavenges,
  // the property holds for all objects promoted during that
  // scavenge for the duration of the scavenge and is used
  // by card-scanning to avoid scanning objects (being) promoted
  // during that scavenge.
  bool obj_allocated_since_save_marks(const oop obj) const {
    assert(is_in_reserved(obj), "Wrong space?");
    return ((PromotedObject*)obj)->hasPromotedMark();
  }

  // A worst-case estimate of the space required (in HeapWords) to expand the
  // heap when promoting an obj of size obj_size.
  size_t expansionSpaceRequired(size_t obj_size) const;

  FreeChunk* allocateScratch(size_t size);

  // returns true if either the small or large linear allocation buffer is empty.
  bool       linearAllocationWouldFail() const;

  // Adjust the chunk for the minimum size.  This version is called in
  // most cases in CompactibleFreeListSpace methods.
  inline static size_t adjustObjectSize(size_t size) {
    return (size_t) align_object_size(MAX2(size, (size_t)MinChunkSize));
  }
  // This is a virtual version of adjustObjectSize() that is called
  // only occasionally when the compaction space changes and the type
  // of the new compaction space is is only known to be CompactibleSpace.
  size_t adjust_object_size_v(size_t size) const {
    return adjustObjectSize(size);
  }
  // Minimum size of a free block.
  virtual size_t minimum_free_block_size() const { return MinChunkSize; }
  void      removeFreeChunkFromFreeLists(FreeChunk* chunk);
  void      addChunkAndRepairOffsetTable(HeapWord* chunk, size_t size,
              bool coalesced);

  // Support for decisions regarding concurrent collection policy
  bool should_concurrent_collect() const;

  // Support for compaction
  void prepare_for_compaction(CompactPoint* cp);
  void adjust_pointers();
  void compact();
  // reset the space to reflect the fact that a compaction of the
  // space has been done.
  virtual void reset_after_compaction();

  // Debugging support
  void print()                            const;
  void prepare_for_verify();
  void verify(bool allow_dirty)           const;
  void verifyFreeLists()                  const PRODUCT_RETURN;
  void verifyIndexedFreeLists()           const;
  void verifyIndexedFreeList(size_t size) const;
  // verify that the given chunk is in the free lists.
  bool verifyChunkInFreeLists(FreeChunk* fc) const;
  // Do some basic checks on the the free lists.
  void checkFreeListConsistency()         const PRODUCT_RETURN;

  // Printing support
  void dump_at_safepoint_with_locks(CMSCollector* c, outputStream* st);
  void print_indexed_free_lists(outputStream* st) const;
  void print_dictionary_free_lists(outputStream* st) const;
  void print_promo_info_blocks(outputStream* st) const;

  NOT_PRODUCT (
    void initializeIndexedFreeListArrayReturnedBytes();
    size_t sumIndexedFreeListArrayReturnedBytes();
    // Return the total number of chunks in the indexed free lists.
    size_t totalCountInIndexedFreeLists() const;
    // Return the total numberof chunks in the space.
    size_t totalCount();
  )

  // The census consists of counts of the quantities such as
  // the current count of the free chunks, number of chunks
  // created as a result of the split of a larger chunk or
  // coalescing of smaller chucks, etc.  The counts in the
  // census is used to make decisions on splitting and
  // coalescing of chunks during the sweep of garbage.

  // Print the statistics for the free lists.
  void printFLCensus(size_t sweep_count) const;

  // Statistics functions
  // Initialize census for lists before the sweep.
  void beginSweepFLCensus(float inter_sweep_current,
                          float inter_sweep_estimate,
                          float intra_sweep_estimate);
  // Set the surplus for each of the free lists.
  void setFLSurplus();
  // Set the hint for each of the free lists.
  void setFLHints();
  // Clear the census for each of the free lists.
  void clearFLCensus();
  // Perform functions for the census after the end of the sweep.
  void endSweepFLCensus(size_t sweep_count);
  // Return true if the count of free chunks is greater
  // than the desired number of free chunks.
  bool coalOverPopulated(size_t size);

// Record (for each size):
//
//   split-births = #chunks added due to splits in (prev-sweep-end,
//      this-sweep-start)
//   split-deaths = #chunks removed for splits in (prev-sweep-end,
//      this-sweep-start)
//   num-curr     = #chunks at start of this sweep
//   num-prev     = #chunks at end of previous sweep
//
// The above are quantities that are measured. Now define:
//
//   num-desired := num-prev + split-births - split-deaths - num-curr
//
// Roughly, num-prev + split-births is the supply,
// split-deaths is demand due to other sizes
// and num-curr is what we have left.
//
// Thus, num-desired is roughly speaking the "legitimate demand"
// for blocks of this size and what we are striving to reach at the
// end of the current sweep.
//
// For a given list, let num-len be its current population.
// Define, for a free list of a given size:
//
//   coal-overpopulated := num-len >= num-desired * coal-surplus
// (coal-surplus is set to 1.05, i.e. we allow a little slop when
// coalescing -- we do not coalesce unless we think that the current
// supply has exceeded the estimated demand by more than 5%).
//
// For the set of sizes in the binary tree, which is neither dense nor
// closed, it may be the case that for a particular size we have never
// had, or do not now have, or did not have at the previous sweep,
// chunks of that size. We need to extend the definition of
// coal-overpopulated to such sizes as well:
//
//   For a chunk in/not in the binary tree, extend coal-overpopulated
//   defined above to include all sizes as follows:
//
//   . a size that is non-existent is coal-overpopulated
//   . a size that has a num-desired <= 0 as defined above is
//     coal-overpopulated.
//
// Also define, for a chunk heap-offset C and mountain heap-offset M:
//
//   close-to-mountain := C >= 0.99 * M
//
// Now, the coalescing strategy is:
//
//    Coalesce left-hand chunk with right-hand chunk if and
//    only if:
//
//      EITHER
//        . left-hand chunk is of a size that is coal-overpopulated
//      OR
//        . right-hand chunk is close-to-mountain
  void smallCoalBirth(size_t size);
  void smallCoalDeath(size_t size);
  void coalBirth(size_t size);
  void coalDeath(size_t size);
  void smallSplitBirth(size_t size);
  void smallSplitDeath(size_t size);
  void splitBirth(size_t size);
  void splitDeath(size_t size);
  void split(size_t from, size_t to1);

  double flsFrag() const;
};

// A parallel-GC-thread-local allocation buffer for allocation into a
// CompactibleFreeListSpace.
class CFLS_LAB : public CHeapObj {
  // The space that this buffer allocates into.
  CompactibleFreeListSpace* _cfls;

  // Our local free lists.
  FreeList _indexedFreeList[CompactibleFreeListSpace::IndexSetSize];

  // Initialized from a command-line arg.

  // Allocation statistics in support of dynamic adjustment of
  // #blocks to claim per get_from_global_pool() call below.
  static AdaptiveWeightedAverage
                 _blocks_to_claim  [CompactibleFreeListSpace::IndexSetSize];
  static size_t _global_num_blocks [CompactibleFreeListSpace::IndexSetSize];
  static int    _global_num_workers[CompactibleFreeListSpace::IndexSetSize];
  size_t        _num_blocks        [CompactibleFreeListSpace::IndexSetSize];

  // Internal work method
  void get_from_global_pool(size_t word_sz, FreeList* fl);

public:
  CFLS_LAB(CompactibleFreeListSpace* cfls);

  // Allocate and return a block of the given size, or else return NULL.
  HeapWord* alloc(size_t word_sz);

  // Return any unused portions of the buffer to the global pool.
  void retire(int tid);

  // Dynamic OldPLABSize sizing
  static void compute_desired_plab_size();
  // When the settings are modified from default static initialization
  static void modify_initialization(size_t n, unsigned wt);
};

size_t PromotionInfo::refillSize() const {
  const size_t CMSSpoolBlockSize = 256;
  const size_t sz = heap_word_size(sizeof(SpoolBlock) + sizeof(markOop)
                                   * CMSSpoolBlockSize);
  return CompactibleFreeListSpace::adjustObjectSize(sz);
}
