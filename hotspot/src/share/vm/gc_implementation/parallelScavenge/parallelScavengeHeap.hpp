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

#ifndef SHARE_VM_GC_IMPLEMENTATION_PARALLELSCAVENGE_PARALLELSCAVENGEHEAP_HPP
#define SHARE_VM_GC_IMPLEMENTATION_PARALLELSCAVENGE_PARALLELSCAVENGEHEAP_HPP

#include "gc_implementation/parallelScavenge/generationSizer.hpp"
#include "gc_implementation/parallelScavenge/objectStartArray.hpp"
#include "gc_implementation/parallelScavenge/psGCAdaptivePolicyCounters.hpp"
#include "gc_implementation/parallelScavenge/psOldGen.hpp"
#include "gc_implementation/parallelScavenge/psYoungGen.hpp"
#include "gc_implementation/shared/gcPolicyCounters.hpp"
#include "gc_implementation/shared/gcWhen.hpp"
#include "gc_interface/collectedHeap.inline.hpp"
#include "memory/collectorPolicy.hpp"
#include "utilities/ostream.hpp"

class AdjoiningGenerations;
class GCHeapSummary;
class GCTaskManager;
class PSAdaptiveSizePolicy;
class PSHeapSummary;

class ParallelScavengeHeap : public CollectedHeap {
  friend class VMStructs;
 private:
  static PSYoungGen* _young_gen;
  static PSOldGen*   _old_gen;

  // Sizing policy for entire heap
  static PSAdaptiveSizePolicy*       _size_policy;
  static PSGCAdaptivePolicyCounters* _gc_policy_counters;

  static ParallelScavengeHeap* _psh;

  GenerationSizer* _collector_policy;

  // Collection of generations that are adjacent in the
  // space reserved for the heap.
  AdjoiningGenerations* _gens;
  unsigned int _death_march_count;

  // The task manager
  static GCTaskManager* _gc_task_manager;

  void trace_heap(GCWhen::Type when, GCTracer* tracer);

 protected:
  static inline size_t total_invocations();
  HeapWord* allocate_new_tlab(size_t size);

  inline bool should_alloc_in_eden(size_t size) const;
  inline void death_march_check(HeapWord* const result, size_t size);
  HeapWord* mem_allocate_old_gen(size_t size);

 public:
  ParallelScavengeHeap() : CollectedHeap(), _death_march_count(0) { }

  // For use by VM operations
  enum CollectionType {
    Scavenge,
    MarkSweep
  };

  ParallelScavengeHeap::Name kind() const {
    return CollectedHeap::ParallelScavengeHeap;
  }

  virtual CollectorPolicy* collector_policy() const { return (CollectorPolicy*) _collector_policy; }

  static PSYoungGen* young_gen() { return _young_gen; }
  static PSOldGen* old_gen()     { return _old_gen; }

  virtual PSAdaptiveSizePolicy* size_policy() { return _size_policy; }

  static PSGCAdaptivePolicyCounters* gc_policy_counters() { return _gc_policy_counters; }

  static ParallelScavengeHeap* heap();

  static GCTaskManager* const gc_task_manager() { return _gc_task_manager; }

  AdjoiningGenerations* gens() { return _gens; }

  // Returns JNI_OK on success
  virtual jint initialize();

  void post_initialize();
  void update_counters();

  // The alignment used for the various areas
  size_t space_alignment()      { return _collector_policy->space_alignment(); }
  size_t generation_alignment() { return _collector_policy->gen_alignment(); }

  // Return the (conservative) maximum heap alignment
  static size_t conservative_max_heap_alignment() {
    return CollectorPolicy::compute_heap_alignment();
  }

  size_t capacity() const;
  size_t used() const;

  // Return "true" if all generations have reached the
  // maximal committed limit that they can reach, without a garbage
  // collection.
  virtual bool is_maximal_no_gc() const;

  // Return true if the reference points to an object that
  // can be moved in a partial collection.  For currently implemented
  // generational collectors that means during a collection of
  // the young gen.
  virtual bool is_scavengable(const void* addr);

  // Does this heap support heap inspection? (+PrintClassHistogram)
  bool supports_heap_inspection() const { return true; }

  size_t max_capacity() const;

  // Whether p is in the allocated part of the heap
  bool is_in(const void* p) const;

  bool is_in_reserved(const void* p) const;

#ifdef ASSERT
  virtual bool is_in_partial_collection(const void *p);
#endif

  bool is_in_young(oop p);  // reserved part
  bool is_in_old(oop p);    // reserved part

  // Memory allocation.   "gc_time_limit_was_exceeded" will
  // be set to true if the adaptive size policy determine that
  // an excessive amount of time is being spent doing collections
  // and caused a NULL to be returned.  If a NULL is not returned,
  // "gc_time_limit_was_exceeded" has an undefined meaning.
  HeapWord* mem_allocate(size_t size, bool* gc_overhead_limit_was_exceeded);

  // Allocation attempt(s) during a safepoint. It should never be called
  // to allocate a new TLAB as this allocation might be satisfied out
  // of the old generation.
  HeapWord* failed_mem_allocate(size_t size);

  // Support for System.gc()
  void collect(GCCause::Cause cause);

  // These also should be called by the vm thread at a safepoint (e.g., from a
  // VM operation).
  //
  // The first collects the young generation only, unless the scavenge fails; it
  // will then attempt a full gc.  The second collects the entire heap; if
  // maximum_compaction is true, it will compact everything and clear all soft
  // references.
  inline void invoke_scavenge();

  // Perform a full collection
  virtual void do_full_collection(bool clear_all_soft_refs);

  bool supports_inline_contig_alloc() const { return !UseNUMA; }

  HeapWord** top_addr() const { return !UseNUMA ? young_gen()->top_addr() : (HeapWord**)-1; }
  HeapWord** end_addr() const { return !UseNUMA ? young_gen()->end_addr() : (HeapWord**)-1; }

  void ensure_parsability(bool retire_tlabs);
  void accumulate_statistics_all_tlabs();
  void resize_all_tlabs();

  size_t unsafe_max_alloc();

  bool supports_tlab_allocation() const { return true; }

  size_t tlab_capacity(Thread* thr) const;
  size_t unsafe_max_tlab_alloc(Thread* thr) const;

  // Can a compiler initialize a new object without store barriers?
  // This permission only extends from the creation of a new object
  // via a TLAB up to the first subsequent safepoint.
  virtual bool can_elide_tlab_store_barriers() const {
    return true;
  }

  virtual bool card_mark_must_follow_store() const {
    return false;
  }

  // Return true if we don't we need a store barrier for
  // initializing stores to an object at this address.
  virtual bool can_elide_initializing_store_barrier(oop new_obj);

  void oop_iterate(ExtendedOopClosure* cl);
  void object_iterate(ObjectClosure* cl);
  void safe_object_iterate(ObjectClosure* cl) { object_iterate(cl); }

  HeapWord* block_start(const void* addr) const;
  size_t block_size(const HeapWord* addr) const;
  bool block_is_obj(const HeapWord* addr) const;

  jlong millis_since_last_gc();

  void prepare_for_verify();
  PSHeapSummary create_ps_heap_summary();
  virtual void print_on(outputStream* st) const;
  virtual void print_on_error(outputStream* st) const;
  virtual void print_gc_threads_on(outputStream* st) const;
  virtual void gc_threads_do(ThreadClosure* tc) const;
  virtual void print_tracing_info() const;

  void verify(bool silent, VerifyOption option /* ignored */);

  void print_heap_change(size_t prev_used);

  // Resize the young generation.  The reserved space for the
  // generation may be expanded in preparation for the resize.
  void resize_young_gen(size_t eden_size, size_t survivor_size);

  // Resize the old generation.  The reserved space for the
  // generation may be expanded in preparation for the resize.
  void resize_old_gen(size_t desired_free_space);

  // Save the tops of the spaces in all generations
  void record_gen_tops_before_GC() PRODUCT_RETURN;

  // Mangle the unused parts of all spaces in the heap
  void gen_mangle_unused_area() PRODUCT_RETURN;

  // Call these in sequential code around the processing of strong roots.
  class ParStrongRootsScope : public MarkingCodeBlobClosure::MarkScope {
   public:
    ParStrongRootsScope();
    ~ParStrongRootsScope();
  };
};

#endif // SHARE_VM_GC_IMPLEMENTATION_PARALLELSCAVENGE_PARALLELSCAVENGEHEAP_HPP
