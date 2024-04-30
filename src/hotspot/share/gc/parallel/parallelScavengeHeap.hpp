/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_PARALLEL_PARALLELSCAVENGEHEAP_HPP
#define SHARE_GC_PARALLEL_PARALLELSCAVENGEHEAP_HPP

#include "gc/parallel/objectStartArray.hpp"
#include "gc/parallel/psGCAdaptivePolicyCounters.hpp"
#include "gc/parallel/psOldGen.hpp"
#include "gc/parallel/psYoungGen.hpp"
#include "gc/shared/cardTableBarrierSet.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/gcPolicyCounters.hpp"
#include "gc/shared/gcWhen.hpp"
#include "gc/shared/preGCValues.hpp"
#include "gc/shared/referenceProcessor.hpp"
#include "gc/shared/softRefPolicy.hpp"
#include "gc/shared/strongRootsScope.hpp"
#include "gc/shared/workerThread.hpp"
#include "logging/log.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/ostream.hpp"

class GCHeapSummary;
class HeapBlockClaimer;
class MemoryManager;
class MemoryPool;
class PSAdaptiveSizePolicy;
class PSCardTable;
class PSHeapSummary;

// ParallelScavengeHeap is the implementation of CollectedHeap for Parallel GC.
//
// The heap is reserved up-front in a single contiguous block, split into two
// parts, the old and young generation. The old generation resides at lower
// addresses, the young generation at higher addresses. The boundary address
// between the generations is fixed. Within a generation, committed memory
// grows towards higher addresses.
//
//
// low                                                                high
//
//                          +-- generation boundary (fixed after startup)
//                          |
// |<- old gen (reserved) ->|<-       young gen (reserved)             ->|
// +---------------+--------+-----------------+--------+--------+--------+
// |      old      |        |       eden      |  from  |   to   |        |
// |               |        |                 |  (to)  | (from) |        |
// +---------------+--------+-----------------+--------+--------+--------+
// |<- committed ->|        |<-          committed            ->|
//
class ParallelScavengeHeap : public CollectedHeap {
  friend class VMStructs;
 private:
  static PSYoungGen* _young_gen;
  static PSOldGen*   _old_gen;

  // Sizing policy for entire heap
  static PSAdaptiveSizePolicy*       _size_policy;
  static PSGCAdaptivePolicyCounters* _gc_policy_counters;

  unsigned int _death_march_count;

  GCMemoryManager* _young_manager;
  GCMemoryManager* _old_manager;

  MemoryPool* _eden_pool;
  MemoryPool* _survivor_pool;
  MemoryPool* _old_pool;

  WorkerThreads _workers;

  void initialize_serviceability() override;

  void trace_actual_reserved_page_size(const size_t reserved_heap_size, const ReservedSpace rs);
  void trace_heap(GCWhen::Type when, const GCTracer* tracer) override;

  // Allocate in oldgen and record the allocation with the size_policy.
  HeapWord* allocate_old_gen_and_record(size_t word_size);

  void update_parallel_worker_threads_cpu_time();

 protected:
  HeapWord* allocate_new_tlab(size_t min_size, size_t requested_size, size_t* actual_size) override;

  inline bool should_alloc_in_eden(size_t size) const;
  inline void death_march_check(HeapWord* const result, size_t size);
  HeapWord* mem_allocate_old_gen(size_t size);

 public:
  ParallelScavengeHeap() :
    CollectedHeap(),
    _death_march_count(0),
    _young_manager(nullptr),
    _old_manager(nullptr),
    _eden_pool(nullptr),
    _survivor_pool(nullptr),
    _old_pool(nullptr),
    _workers("GC Thread", ParallelGCThreads) { }

  Name kind() const override {
    return CollectedHeap::Parallel;
  }

  const char* name() const override {
    return "Parallel";
  }

  GrowableArray<GCMemoryManager*> memory_managers() override;
  GrowableArray<MemoryPool*> memory_pools() override;

  static PSYoungGen* young_gen() { return _young_gen; }
  static PSOldGen* old_gen()     { return _old_gen; }

  PSAdaptiveSizePolicy* size_policy() { return _size_policy; }

  static PSGCAdaptivePolicyCounters* gc_policy_counters() { return _gc_policy_counters; }

  static ParallelScavengeHeap* heap() {
    return named_heap<ParallelScavengeHeap>(CollectedHeap::Parallel);
  }

  CardTableBarrierSet* barrier_set();
  PSCardTable* card_table();

  // Returns JNI_OK on success
  jint initialize() override;

  void safepoint_synchronize_begin() override;
  void safepoint_synchronize_end() override;

  void post_initialize() override;
  void update_counters();

  size_t capacity() const override;
  size_t used() const override;

  // Return "true" if all generations have reached the
  // maximal committed limit that they can reach, without a garbage
  // collection.
  bool is_maximal_no_gc() const override;

  void register_nmethod(nmethod* nm) override;
  void unregister_nmethod(nmethod* nm) override;
  void verify_nmethod(nmethod* nm) override;

  void prune_scavengable_nmethods();
  void prune_unlinked_nmethods();

  size_t max_capacity() const override;

  // Whether p is in the allocated part of the heap
  bool is_in(const void* p) const override;

  bool is_in_reserved(const void* p) const;

  bool is_in_young(const void* p) const;

  bool requires_barriers(stackChunkOop obj) const override;

  MemRegion reserved_region() const { return _reserved; }
  HeapWord* base() const { return _reserved.start(); }

  // Memory allocation.   "gc_time_limit_was_exceeded" will
  // be set to true if the adaptive size policy determine that
  // an excessive amount of time is being spent doing collections
  // and caused a null to be returned.  If a null is not returned,
  // "gc_time_limit_was_exceeded" has an undefined meaning.
  HeapWord* mem_allocate(size_t size, bool* gc_overhead_limit_was_exceeded) override;

  // Allocation attempt(s) during a safepoint. It should never be called
  // to allocate a new TLAB as this allocation might be satisfied out
  // of the old generation.
  HeapWord* failed_mem_allocate(size_t size);

  // Support for System.gc()
  void collect(GCCause::Cause cause) override;

  // These also should be called by the vm thread at a safepoint (e.g., from a
  // VM operation).
  //
  // The first collects the young generation only, unless the scavenge fails; it
  // will then attempt a full gc.  The second collects the entire heap; if
  // maximum_compaction is true, it will compact everything and clear all soft
  // references.
  inline bool invoke_scavenge();

  // Perform a full collection
  void do_full_collection(bool clear_all_soft_refs) override;

  void ensure_parsability(bool retire_tlabs) override;
  void resize_all_tlabs() override;

  size_t tlab_capacity(Thread* thr) const override;
  size_t tlab_used(Thread* thr) const override;
  size_t unsafe_max_tlab_alloc(Thread* thr) const override;

  void object_iterate(ObjectClosure* cl) override;
  void object_iterate_parallel(ObjectClosure* cl, HeapBlockClaimer* claimer);
  ParallelObjectIteratorImpl* parallel_object_iterator(uint thread_num) override;

  HeapWord* block_start(const void* addr) const;
  bool block_is_obj(const HeapWord* addr) const;

  void prepare_for_verify() override;
  PSHeapSummary create_ps_heap_summary();
  void print_on(outputStream* st) const override;
  void print_on_error(outputStream* st) const override;
  void gc_threads_do(ThreadClosure* tc) const override;
  void print_tracing_info() const override;

  WorkerThreads* safepoint_workers() override { return &_workers; }

  PreGenGCValues get_pre_gc_values() const;
  void print_heap_change(const PreGenGCValues& pre_gc_values) const;

  // Used to print information about locations in the hs_err file.
  bool print_location(outputStream* st, void* addr) const override;

  void verify(VerifyOption option /* ignored */) override;

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

  GCMemoryManager* old_gc_manager() const { return _old_manager; }
  GCMemoryManager* young_gc_manager() const { return _young_manager; }

  WorkerThreads& workers() {
    return _workers;
  }

  // Support for loading objects from CDS archive into the heap
  bool can_load_archived_objects() const override { return UseCompressedOops; }
  HeapWord* allocate_loaded_archive_space(size_t size) override;
  void complete_loaded_archive_space(MemRegion archive_space) override;

  void pin_object(JavaThread* thread, oop obj) override;
  void unpin_object(JavaThread* thread, oop obj) override;
};

// Class that can be used to print information about the
// adaptive size policy at intervals specified by
// AdaptiveSizePolicyOutputInterval.  Only print information
// if an adaptive size policy is in use.
class AdaptiveSizePolicyOutput : AllStatic {
  static bool enabled() {
    return UseParallelGC &&
           UseAdaptiveSizePolicy &&
           log_is_enabled(Debug, gc, ergo);
  }
 public:
  static void print() {
    if (enabled()) {
      ParallelScavengeHeap::heap()->size_policy()->print();
    }
  }

  static void print(AdaptiveSizePolicy* size_policy, uint count) {
    bool do_print =
        enabled() &&
        (AdaptiveSizePolicyOutputInterval > 0) &&
        (count % AdaptiveSizePolicyOutputInterval) == 0;

    if (do_print) {
      size_policy->print();
    }
  }
};

#endif // SHARE_GC_PARALLEL_PARALLELSCAVENGEHEAP_HPP
