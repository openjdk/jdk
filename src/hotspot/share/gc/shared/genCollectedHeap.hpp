/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_GENCOLLECTEDHEAP_HPP
#define SHARE_GC_SHARED_GENCOLLECTEDHEAP_HPP

#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/generation.hpp"
#include "gc/shared/oopStorageParState.hpp"
#include "gc/shared/preGCValues.hpp"
#include "gc/shared/softRefPolicy.hpp"

class AdaptiveSizePolicy;
class CardTableRS;
class GCPolicyCounters;
class GenerationSpec;
class StrongRootsScope;
class SubTasksDone;
class WorkerThreads;

// A "GenCollectedHeap" is a CollectedHeap that uses generational
// collection.  It has two generations, young and old.
class GenCollectedHeap : public CollectedHeap {
  friend class Generation;
  friend class DefNewGeneration;
  friend class TenuredGeneration;
  friend class GenMarkSweep;
  friend class VM_GenCollectForAllocation;
  friend class VM_GenCollectFull;
  friend class VM_GC_HeapInspection;
  friend class VM_HeapDumper;
  friend class HeapInspection;
  friend class GCCauseSetter;
  friend class VMStructs;
public:
  friend class VM_PopulateDumpSharedSpace;

  enum GenerationType {
    YoungGen,
    OldGen
  };

protected:
  Generation* _young_gen;
  Generation* _old_gen;

private:
  GenerationSpec* _young_gen_spec;
  GenerationSpec* _old_gen_spec;

  // The singleton CardTable Remembered Set.
  CardTableRS* _rem_set;

  SoftRefPolicy _soft_ref_policy;

  GCPolicyCounters* _gc_policy_counters;

  // Indicates that the most recent previous incremental collection failed.
  // The flag is cleared when an action is taken that might clear the
  // condition that caused that incremental collection to fail.
  bool _incremental_collection_failed;

  // In support of ExplicitGCInvokesConcurrent functionality
  unsigned int _full_collections_completed;

  // Collects the given generation.
  void collect_generation(Generation* gen, bool full, size_t size, bool is_tlab,
                          bool run_verification, bool clear_soft_refs);

  // Reserve aligned space for the heap as needed by the contained generations.
  ReservedHeapSpace allocate(size_t alignment);

  PreGenGCValues get_pre_gc_values() const;

protected:

  GCMemoryManager* _young_manager;
  GCMemoryManager* _old_manager;

  // Helper functions for allocation
  HeapWord* attempt_allocation(size_t size,
                               bool   is_tlab,
                               bool   first_only);

  // Helper function for two callbacks below.
  // Considers collection of the first max_level+1 generations.
  void do_collection(bool           full,
                     bool           clear_all_soft_refs,
                     size_t         size,
                     bool           is_tlab,
                     GenerationType max_generation);

  // Callback from VM_GenCollectForAllocation operation.
  // This function does everything necessary/possible to satisfy an
  // allocation request that failed in the youngest generation that should
  // have handled it (including collection, expansion, etc.)
  HeapWord* satisfy_failed_allocation(size_t size, bool is_tlab);

  // Callback from VM_GenCollectFull operation.
  // Perform a full collection of the first max_level+1 generations.
  void do_full_collection(bool clear_all_soft_refs) override;
  void do_full_collection(bool clear_all_soft_refs, GenerationType max_generation);

  // Does the "cause" of GC indicate that
  // we absolutely __must__ clear soft refs?
  bool must_clear_all_soft_refs();

  GenCollectedHeap(Generation::Name young,
                   Generation::Name old,
                   const char* policy_counters_name);

public:

  // Returns JNI_OK on success
  jint initialize() override;
  virtual CardTableRS* create_rem_set(const MemRegion& reserved_region);

  // Does operations required after initialization has been done.
  void post_initialize() override;

  Generation* young_gen() const { return _young_gen; }
  Generation* old_gen()   const { return _old_gen; }

  bool is_young_gen(const Generation* gen) const { return gen == _young_gen; }
  bool is_old_gen(const Generation* gen) const { return gen == _old_gen; }

  MemRegion reserved_region() const { return _reserved; }
  bool is_in_reserved(const void* addr) const { return _reserved.contains(addr); }

  GenerationSpec* young_gen_spec() const;
  GenerationSpec* old_gen_spec() const;

  SoftRefPolicy* soft_ref_policy() override { return &_soft_ref_policy; }

  // Performance Counter support
  GCPolicyCounters* counters()     { return _gc_policy_counters; }

  size_t capacity() const override;
  size_t used() const override;

  // Save the "used_region" for both generations.
  void save_used_regions();

  size_t max_capacity() const override;

  HeapWord* mem_allocate(size_t size, bool*  gc_overhead_limit_was_exceeded) override;

  // Perform a full collection of the heap; intended for use in implementing
  // "System.gc". This implies as full a collection as the CollectedHeap
  // supports. Caller does not hold the Heap_lock on entry.
  void collect(GCCause::Cause cause) override;

  // Returns "TRUE" iff "p" points into the committed areas of the heap.
  // The methods is_in() and is_in_youngest() may be expensive to compute
  // in general, so, to prevent their inadvertent use in product jvm's, we
  // restrict their use to assertion checking or verification only.
  bool is_in(const void* p) const override;

  // Returns true if p points into the reserved space for the young generation.
  // Assumes the young gen address range is less than that of the old gen.
  bool is_in_young(const void* p) const;

  bool requires_barriers(stackChunkOop obj) const override;

#ifdef ASSERT
  bool is_in_partial_collection(const void* p);
#endif

  // Optimized nmethod scanning support routines
  void register_nmethod(nmethod* nm) override;
  void unregister_nmethod(nmethod* nm) override;
  void verify_nmethod(nmethod* nm) override;

  void prune_scavengable_nmethods();

  // Iteration functions.
  void oop_iterate(OopIterateClosure* cl);
  void object_iterate(ObjectClosure* cl) override;
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
  HeapWord* block_start(const void* addr) const;

  // Requires "addr" to be the start of a block, and returns "TRUE" iff
  // the block is an object. Assumes (and verifies in non-product
  // builds) that addr is in the allocated part of the heap and is
  // the start of a chunk.
  bool block_is_obj(const HeapWord* addr) const;

  // Section on TLAB's.
  size_t tlab_capacity(Thread* thr) const override;
  size_t tlab_used(Thread* thr) const override;
  size_t unsafe_max_tlab_alloc(Thread* thr) const override;
  HeapWord* allocate_new_tlab(size_t min_size,
                              size_t requested_size,
                              size_t* actual_size) override;

  // Total number of full collections completed.
  unsigned int total_full_collections_completed() {
    assert(_full_collections_completed <= _total_full_collections,
           "Can't complete more collections than were started");
    return _full_collections_completed;
  }

  // Update above counter, as appropriate, at the end of a stop-world GC cycle
  unsigned int update_full_collections_completed();

  // Update the gc statistics for each generation.
  void update_gc_stats(Generation* current_generation, bool full) {
    _old_gen->update_gc_stats(current_generation, full);
  }

  bool no_gc_in_progress() { return !is_gc_active(); }

  void prepare_for_verify() override;
  void verify(VerifyOption option) override;

  void print_on(outputStream* st) const override;
  void gc_threads_do(ThreadClosure* tc) const override;
  void print_tracing_info() const override;

  // Used to print information about locations in the hs_err file.
  bool print_location(outputStream* st, void* addr) const override;

  void print_heap_change(const PreGenGCValues& pre_gc_values) const;

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

  // Return "true" if all generations have reached the
  // maximal committed limit that they can reach, without a garbage
  // collection.
  virtual bool is_maximal_no_gc() const override;

  // This function returns the CardTableRS object that allows us to scan
  // generations in a fully generational heap.
  CardTableRS* rem_set() { return _rem_set; }

  // Convenience function to be used in situations where the heap type can be
  // asserted to be this type.
  static GenCollectedHeap* heap();

  // The ScanningOption determines which of the roots
  // the closure is applied to:
  // "SO_None" does none;
  enum ScanningOption {
    SO_None                =  0x0,
    SO_AllCodeCache        =  0x8,
    SO_ScavengeCodeCache   = 0x10
  };

 protected:
  virtual void gc_prologue(bool full);
  virtual void gc_epilogue(bool full);

 public:
  // Apply closures on various roots in Young GC or marking/adjust phases of Full GC.
  void process_roots(ScanningOption so,
                     OopClosure* strong_roots,
                     CLDClosure* strong_cld_closure,
                     CLDClosure* weak_cld_closure,
                     CodeBlobToOopClosure* code_roots);

  // Apply "root_closure" to all the weak roots of the system.
  // These include JNI weak roots, string table,
  // and referents of reachable weak refs.
  void gen_process_weak_roots(OopClosure* root_closure);

  // Set the saved marks of generations, if that makes sense.
  // In particular, if any generation might iterate over the oops
  // in other generations, it should call this method.
  void save_marks();

  // Returns "true" iff no allocations have occurred since the last
  // call to "save_marks".
  bool no_allocs_since_save_marks();

  // Returns true if an incremental collection is likely to fail.
  // We optionally consult the young gen, if asked to do so;
  // otherwise we base our answer on whether the previous incremental
  // collection attempt failed with no corrective action as of yet.
  bool incremental_collection_will_fail(bool consult_young) {
    // The first disjunct remembers if an incremental collection failed, even
    // when we thought (second disjunct) that it would not.
    return incremental_collection_failed() ||
           (consult_young && !_young_gen->collection_attempt_is_safe());
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

private:
  // Return true if an allocation should be attempted in the older generation
  // if it fails in the younger generation.  Return false, otherwise.
  bool should_try_older_generation_allocation(size_t word_size) const;

  // Try to allocate space by expanding the heap.
  HeapWord* expand_heap_and_allocate(size_t size, bool is_tlab);

  HeapWord* mem_allocate_work(size_t size,
                              bool is_tlab);

#if INCLUDE_SERIALGC
  // For use by mark-sweep.  As implemented, mark-sweep-compact is global
  // in an essential way: compaction is performed across generations, by
  // iterating over spaces.
  void prepare_for_compaction();
#endif

  // Save the tops of the spaces in all generations
  void record_gen_tops_before_GC() PRODUCT_RETURN;

  // Return true if we need to perform full collection.
  bool should_do_full_collection(size_t size, bool full,
                                 bool is_tlab, GenerationType max_gen) const;
};

#endif // SHARE_GC_SHARED_GENCOLLECTEDHEAP_HPP
