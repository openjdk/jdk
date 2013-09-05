/*
 * Copyright (c) 2000, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_MEMORY_GENCOLLECTEDHEAP_HPP
#define SHARE_VM_MEMORY_GENCOLLECTEDHEAP_HPP

#include "gc_implementation/shared/adaptiveSizePolicy.hpp"
#include "memory/collectorPolicy.hpp"
#include "memory/generation.hpp"
#include "memory/sharedHeap.hpp"

class SubTasksDone;

// A "GenCollectedHeap" is a SharedHeap that uses generational
// collection.  It is represented with a sequence of Generation's.
class GenCollectedHeap : public SharedHeap {
  friend class GenCollectorPolicy;
  friend class Generation;
  friend class DefNewGeneration;
  friend class TenuredGeneration;
  friend class ConcurrentMarkSweepGeneration;
  friend class CMSCollector;
  friend class GenMarkSweep;
  friend class VM_GenCollectForAllocation;
  friend class VM_GenCollectFull;
  friend class VM_GenCollectFullConcurrent;
  friend class VM_GC_HeapInspection;
  friend class VM_HeapDumper;
  friend class HeapInspection;
  friend class GCCauseSetter;
  friend class VMStructs;
public:
  enum SomeConstants {
    max_gens = 10
  };

  friend class VM_PopulateDumpSharedSpace;

 protected:
  // Fields:
  static GenCollectedHeap* _gch;

 private:
  int _n_gens;
  Generation* _gens[max_gens];
  GenerationSpec** _gen_specs;

  // The generational collector policy.
  GenCollectorPolicy* _gen_policy;

  // Indicates that the most recent previous incremental collection failed.
  // The flag is cleared when an action is taken that might clear the
  // condition that caused that incremental collection to fail.
  bool _incremental_collection_failed;

  // In support of ExplicitGCInvokesConcurrent functionality
  unsigned int _full_collections_completed;

  // Data structure for claiming the (potentially) parallel tasks in
  // (gen-specific) strong roots processing.
  SubTasksDone* _gen_process_strong_tasks;
  SubTasksDone* gen_process_strong_tasks() { return _gen_process_strong_tasks; }

  // In block contents verification, the number of header words to skip
  NOT_PRODUCT(static size_t _skip_header_HeapWords;)

protected:
  // Directs each generation up to and including "collectedGen" to recompute
  // its desired size.
  void compute_new_generation_sizes(int collectedGen);

  // Helper functions for allocation
  HeapWord* attempt_allocation(size_t size,
                               bool   is_tlab,
                               bool   first_only);

  // Helper function for two callbacks below.
  // Considers collection of the first max_level+1 generations.
  void do_collection(bool   full,
                     bool   clear_all_soft_refs,
                     size_t size,
                     bool   is_tlab,
                     int    max_level);

  // Callback from VM_GenCollectForAllocation operation.
  // This function does everything necessary/possible to satisfy an
  // allocation request that failed in the youngest generation that should
  // have handled it (including collection, expansion, etc.)
  HeapWord* satisfy_failed_allocation(size_t size, bool is_tlab);

  // Callback from VM_GenCollectFull operation.
  // Perform a full collection of the first max_level+1 generations.
  virtual void do_full_collection(bool clear_all_soft_refs);
  void do_full_collection(bool clear_all_soft_refs, int max_level);

  // Does the "cause" of GC indicate that
  // we absolutely __must__ clear soft refs?
  bool must_clear_all_soft_refs();

public:
  GenCollectedHeap(GenCollectorPolicy *policy);

  GCStats* gc_stats(int level) const;

  // Returns JNI_OK on success
  virtual jint initialize();
  char* allocate(size_t alignment,
                 size_t* _total_reserved, int* _n_covered_regions,
                 ReservedSpace* heap_rs);

  // Does operations required after initialization has been done.
  void post_initialize();

  // Initialize ("weak") refs processing support
  virtual void ref_processing_init();

  virtual CollectedHeap::Name kind() const {
    return CollectedHeap::GenCollectedHeap;
  }

  // The generational collector policy.
  GenCollectorPolicy* gen_policy() const { return _gen_policy; }
  virtual CollectorPolicy* collector_policy() const { return (CollectorPolicy*) gen_policy(); }

  // Adaptive size policy
  virtual AdaptiveSizePolicy* size_policy() {
    return gen_policy()->size_policy();
  }

  size_t capacity() const;
  size_t used() const;

  // Save the "used_region" for generations level and lower.
  void save_used_regions(int level);

  size_t max_capacity() const;

  HeapWord* mem_allocate(size_t size,
                         bool*  gc_overhead_limit_was_exceeded);

  // We may support a shared contiguous allocation area, if the youngest
  // generation does.
  bool supports_inline_contig_alloc() const;
  HeapWord** top_addr() const;
  HeapWord** end_addr() const;

  // Return an estimate of the maximum allocation that could be performed
  // without triggering any collection activity.  In a generational
  // collector, for example, this is probably the largest allocation that
  // could be supported in the youngest generation.  It is "unsafe" because
  // no locks are taken; the result should be treated as an approximation,
  // not a guarantee.
  size_t unsafe_max_alloc();

  // Does this heap support heap inspection? (+PrintClassHistogram)
  virtual bool supports_heap_inspection() const { return true; }

  // Perform a full collection of the heap; intended for use in implementing
  // "System.gc". This implies as full a collection as the CollectedHeap
  // supports. Caller does not hold the Heap_lock on entry.
  void collect(GCCause::Cause cause);

  // The same as above but assume that the caller holds the Heap_lock.
  void collect_locked(GCCause::Cause cause);

  // Perform a full collection of the first max_level+1 generations.
  // Mostly used for testing purposes. Caller does not hold the Heap_lock on entry.
  void collect(GCCause::Cause cause, int max_level);

  // Returns "TRUE" iff "p" points into the committed areas of the heap.
  // The methods is_in(), is_in_closed_subset() and is_in_youngest() may
  // be expensive to compute in general, so, to prevent
  // their inadvertent use in product jvm's, we restrict their use to
  // assertion checking or verification only.
  bool is_in(const void* p) const;

  // override
  bool is_in_closed_subset(const void* p) const {
    if (UseConcMarkSweepGC) {
      return is_in_reserved(p);
    } else {
      return is_in(p);
    }
  }

  // Returns true if the reference is to an object in the reserved space
  // for the young generation.
  // Assumes the the young gen address range is less than that of the old gen.
  bool is_in_young(oop p);

#ifdef ASSERT
  virtual bool is_in_partial_collection(const void* p);
#endif

  virtual bool is_scavengable(const void* addr) {
    return is_in_young((oop)addr);
  }

  // Iteration functions.
  void oop_iterate(ExtendedOopClosure* cl);
  void oop_iterate(MemRegion mr, ExtendedOopClosure* cl);
  void object_iterate(ObjectClosure* cl);
  void safe_object_iterate(ObjectClosure* cl);
  Space* space_containing(const void* addr) const;

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
  // of the active area of the heap. Assumes (and verifies in non-product
  // builds) that addr is in the allocated part of the heap and is
  // the start of a chunk.
  virtual size_t block_size(const HeapWord* addr) const;

  // Requires "addr" to be the start of a block, and returns "TRUE" iff
  // the block is an object. Assumes (and verifies in non-product
  // builds) that addr is in the allocated part of the heap and is
  // the start of a chunk.
  virtual bool block_is_obj(const HeapWord* addr) const;

  // Section on TLAB's.
  virtual bool supports_tlab_allocation() const;
  virtual size_t tlab_capacity(Thread* thr) const;
  virtual size_t unsafe_max_tlab_alloc(Thread* thr) const;
  virtual HeapWord* allocate_new_tlab(size_t size);

  // Can a compiler initialize a new object without store barriers?
  // This permission only extends from the creation of a new object
  // via a TLAB up to the first subsequent safepoint.
  virtual bool can_elide_tlab_store_barriers() const {
    return true;
  }

  virtual bool card_mark_must_follow_store() const {
    return UseConcMarkSweepGC;
  }

  // We don't need barriers for stores to objects in the
  // young gen and, a fortiori, for initializing stores to
  // objects therein. This applies to {DefNew,ParNew}+{Tenured,CMS}
  // only and may need to be re-examined in case other
  // kinds of collectors are implemented in the future.
  virtual bool can_elide_initializing_store_barrier(oop new_obj) {
    // We wanted to assert that:-
    // assert(UseParNewGC || UseSerialGC || UseConcMarkSweepGC,
    //       "Check can_elide_initializing_store_barrier() for this collector");
    // but unfortunately the flag UseSerialGC need not necessarily always
    // be set when DefNew+Tenured are being used.
    return is_in_young(new_obj);
  }

  // The "requestor" generation is performing some garbage collection
  // action for which it would be useful to have scratch space.  The
  // requestor promises to allocate no more than "max_alloc_words" in any
  // older generation (via promotion say.)   Any blocks of space that can
  // be provided are returned as a list of ScratchBlocks, sorted by
  // decreasing size.
  ScratchBlock* gather_scratch(Generation* requestor, size_t max_alloc_words);
  // Allow each generation to reset any scratch space that it has
  // contributed as it needs.
  void release_scratch();

  // Ensure parsability: override
  virtual void ensure_parsability(bool retire_tlabs);

  // Time in ms since the longest time a collector ran in
  // in any generation.
  virtual jlong millis_since_last_gc();

  // Total number of full collections completed.
  unsigned int total_full_collections_completed() {
    assert(_full_collections_completed <= _total_full_collections,
           "Can't complete more collections than were started");
    return _full_collections_completed;
  }

  // Update above counter, as appropriate, at the end of a stop-world GC cycle
  unsigned int update_full_collections_completed();
  // Update above counter, as appropriate, at the end of a concurrent GC cycle
  unsigned int update_full_collections_completed(unsigned int count);

  // Update "time of last gc" for all constituent generations
  // to "now".
  void update_time_of_last_gc(jlong now) {
    for (int i = 0; i < _n_gens; i++) {
      _gens[i]->update_time_of_last_gc(now);
    }
  }

  // Update the gc statistics for each generation.
  // "level" is the level of the lastest collection
  void update_gc_stats(int current_level, bool full) {
    for (int i = 0; i < _n_gens; i++) {
      _gens[i]->update_gc_stats(current_level, full);
    }
  }

  // Override.
  bool no_gc_in_progress() { return !is_gc_active(); }

  // Override.
  void prepare_for_verify();

  // Override.
  void verify(bool silent, VerifyOption option);

  // Override.
  virtual void print_on(outputStream* st) const;
  virtual void print_gc_threads_on(outputStream* st) const;
  virtual void gc_threads_do(ThreadClosure* tc) const;
  virtual void print_tracing_info() const;
  virtual void print_on_error(outputStream* st) const;

  // PrintGC, PrintGCDetails support
  void print_heap_change(size_t prev_used) const;

  // The functions below are helper functions that a subclass of
  // "CollectedHeap" can use in the implementation of its virtual
  // functions.

  class GenClosure : public StackObj {
   public:
    virtual void do_generation(Generation* gen) = 0;
  };

  // Apply "cl.do_generation" to all generations in the heap
  // If "old_to_young" determines the order.
  void generation_iterate(GenClosure* cl, bool old_to_young);

  void space_iterate(SpaceClosure* cl);

  // Return "true" if all generations have reached the
  // maximal committed limit that they can reach, without a garbage
  // collection.
  virtual bool is_maximal_no_gc() const;

  // Return the generation before "gen".
  Generation* prev_gen(Generation* gen) const {
    int l = gen->level();
    guarantee(l > 0, "Out of bounds");
    return _gens[l-1];
  }

  // Return the generation after "gen".
  Generation* next_gen(Generation* gen) const {
    int l = gen->level() + 1;
    guarantee(l < _n_gens, "Out of bounds");
    return _gens[l];
  }

  Generation* get_gen(int i) const {
    guarantee(i >= 0 && i < _n_gens, "Out of bounds");
    return _gens[i];
  }

  int n_gens() const {
    assert(_n_gens == gen_policy()->number_of_generations(), "Sanity");
    return _n_gens;
  }

  // Convenience function to be used in situations where the heap type can be
  // asserted to be this type.
  static GenCollectedHeap* heap();

  void set_par_threads(uint t);

  // Invoke the "do_oop" method of one of the closures "not_older_gens"
  // or "older_gens" on root locations for the generation at
  // "level".  (The "older_gens" closure is used for scanning references
  // from older generations; "not_older_gens" is used everywhere else.)
  // If "younger_gens_as_roots" is false, younger generations are
  // not scanned as roots; in this case, the caller must be arranging to
  // scan the younger generations itself.  (For example, a generation might
  // explicitly mark reachable objects in younger generations, to avoid
  // excess storage retention.)
  // The "so" argument determines which of the roots
  // the closure is applied to:
  // "SO_None" does none;
  // "SO_AllClasses" applies the closure to all entries in the SystemDictionary;
  // "SO_SystemClasses" to all the "system" classes and loaders;
  // "SO_Strings" applies the closure to all entries in the StringTable.
  void gen_process_strong_roots(int level,
                                bool younger_gens_as_roots,
                                // The remaining arguments are in an order
                                // consistent with SharedHeap::process_strong_roots:
                                bool activate_scope,
                                bool is_scavenging,
                                SharedHeap::ScanningOption so,
                                OopsInGenClosure* not_older_gens,
                                bool do_code_roots,
                                OopsInGenClosure* older_gens,
                                KlassClosure* klass_closure);

  // Apply "blk" to all the weak roots of the system.  These include
  // JNI weak roots, the code cache, system dictionary, symbol table,
  // string table, and referents of reachable weak refs.
  void gen_process_weak_roots(OopClosure* root_closure,
                              CodeBlobClosure* code_roots);

  // Set the saved marks of generations, if that makes sense.
  // In particular, if any generation might iterate over the oops
  // in other generations, it should call this method.
  void save_marks();

  // Apply "cur->do_oop" or "older->do_oop" to all the oops in objects
  // allocated since the last call to save_marks in generations at or above
  // "level".  The "cur" closure is
  // applied to references in the generation at "level", and the "older"
  // closure to older generations.
#define GCH_SINCE_SAVE_MARKS_ITERATE_DECL(OopClosureType, nv_suffix)    \
  void oop_since_save_marks_iterate(int level,                          \
                                    OopClosureType* cur,                \
                                    OopClosureType* older);

  ALL_SINCE_SAVE_MARKS_CLOSURES(GCH_SINCE_SAVE_MARKS_ITERATE_DECL)

#undef GCH_SINCE_SAVE_MARKS_ITERATE_DECL

  // Returns "true" iff no allocations have occurred in any generation at
  // "level" or above since the last
  // call to "save_marks".
  bool no_allocs_since_save_marks(int level);

  // Returns true if an incremental collection is likely to fail.
  // We optionally consult the young gen, if asked to do so;
  // otherwise we base our answer on whether the previous incremental
  // collection attempt failed with no corrective action as of yet.
  bool incremental_collection_will_fail(bool consult_young) {
    // Assumes a 2-generation system; the first disjunct remembers if an
    // incremental collection failed, even when we thought (second disjunct)
    // that it would not.
    assert(heap()->collector_policy()->is_two_generation_policy(),
           "the following definition may not be suitable for an n(>2)-generation system");
    return incremental_collection_failed() ||
           (consult_young && !get_gen(0)->collection_attempt_is_safe());
  }

  // If a generation bails out of an incremental collection,
  // it sets this flag.
  bool incremental_collection_failed() const {
    return _incremental_collection_failed;
  }
  void set_incremental_collection_failed() {
    _incremental_collection_failed = true;
  }
  void clear_incremental_collection_failed() {
    _incremental_collection_failed = false;
  }

  // Promotion of obj into gen failed.  Try to promote obj to higher
  // gens in ascending order; return the new location of obj if successful.
  // Otherwise, try expand-and-allocate for obj in both the young and old
  // generation; return the new location of obj if successful.  Otherwise, return NULL.
  oop handle_failed_promotion(Generation* old_gen,
                              oop obj,
                              size_t obj_size);

private:
  // Accessor for memory state verification support
  NOT_PRODUCT(
    static size_t skip_header_HeapWords() { return _skip_header_HeapWords; }
  )

  // Override
  void check_for_non_bad_heap_word_value(HeapWord* addr,
    size_t size) PRODUCT_RETURN;

  // For use by mark-sweep.  As implemented, mark-sweep-compact is global
  // in an essential way: compaction is performed across generations, by
  // iterating over spaces.
  void prepare_for_compaction();

  // Perform a full collection of the first max_level+1 generations.
  // This is the low level interface used by the public versions of
  // collect() and collect_locked(). Caller holds the Heap_lock on entry.
  void collect_locked(GCCause::Cause cause, int max_level);

  // Returns success or failure.
  bool create_cms_collector();

  // In support of ExplicitGCInvokesConcurrent functionality
  bool should_do_concurrent_full_gc(GCCause::Cause cause);
  void collect_mostly_concurrent(GCCause::Cause cause);

  // Save the tops of the spaces in all generations
  void record_gen_tops_before_GC() PRODUCT_RETURN;

protected:
  virtual void gc_prologue(bool full);
  virtual void gc_epilogue(bool full);
};

#endif // SHARE_VM_MEMORY_GENCOLLECTEDHEAP_HPP
