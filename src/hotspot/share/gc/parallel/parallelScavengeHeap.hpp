/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/parallel/psAdaptiveSizePolicy.hpp"
#include "gc/parallel/psOldGen.hpp"
#include "gc/parallel/psYoungGen.hpp"
#include "gc/shared/cardTableBarrierSet.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/gcPolicyCounters.hpp"
#include "gc/shared/gcWhen.hpp"
#include "gc/shared/preGCValues.hpp"
#include "gc/shared/workerThread.hpp"
#include "logging/log.hpp"
#include "utilities/growableArray.hpp"

class GCHeapSummary;
class HeapBlockClaimer;
class MemoryManager;
class MemoryPool;
class PSAdaptiveSizePolicy;
class PSCardTable;
class PSHeapSummary;
class ReservedSpace;

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
// +---------------+--------+--------+--------+------------------+-------+
// |      old      |        |  from  |   to   |        eden      |       |
// |               |        |  (to)  | (from) |                  |       |
// +---------------+--------+--------+--------+------------------+-------+
// |<- committed ->|        |<-          committed             ->|
//
class ParallelScavengeHeap : public CollectedHeap {
  friend class VMStructs;
 private:
  static PSYoungGen* _young_gen;
  static PSOldGen*   _old_gen;

  // Sizing policy for entire heap
  static PSAdaptiveSizePolicy*       _size_policy;
  static GCPolicyCounters*           _gc_policy_counters;

  GCMemoryManager* _young_manager;
  GCMemoryManager* _old_manager;

  MemoryPool* _eden_pool;
  MemoryPool* _survivor_pool;
  MemoryPool* _old_pool;

  WorkerThreads _workers;

  uint _gc_overhead_counter;

  bool _is_heap_almost_full;

  void initialize_serviceability() override;

  void trace_actual_reserved_page_size(const size_t reserved_heap_size, const ReservedSpace rs);
  void trace_heap(GCWhen::Type when, const GCTracer* tracer) override;

  void update_parallel_worker_threads_cpu_time();

  bool must_clear_all_soft_refs();

  HeapWord* allocate_new_tlab(size_t min_size, size_t requested_size, size_t* actual_size) override;

  inline bool should_alloc_in_eden(size_t size) const;

  HeapWord* mem_allocate_work(size_t size,
                              bool is_tlab,
                              bool* gc_overhead_limit_was_exceeded);

  HeapWord* expand_heap_and_allocate(size_t size, bool is_tlab);

  void do_full_collection(bool clear_all_soft_refs) override;

  bool check_gc_overhead_limit();

  size_t calculate_desired_old_gen_capacity(size_t old_gen_live_size);

  void resize_old_gen_after_full_gc();

  void print_tracing_info() const override;
  void stop() override {};

public:
  ParallelScavengeHeap() :
    CollectedHeap(),
    _young_manager(nullptr),
    _old_manager(nullptr),
    _eden_pool(nullptr),
    _survivor_pool(nullptr),
    _old_pool(nullptr),
    _workers("GC Thread", ParallelGCThreads),
    _gc_overhead_counter(0),
    _is_heap_almost_full(false) {}

  Name kind() const override {
    return CollectedHeap::Parallel;
  }

  const char* name() const override {
    return "Parallel";
  }

  // Invoked at gc-pause-end
  void gc_epilogue(bool full);

  GrowableArray<GCMemoryManager*> memory_managers() override;
  GrowableArray<MemoryPool*> memory_pools() override;

  static PSYoungGen* young_gen() { return _young_gen; }
  static PSOldGen* old_gen()     { return _old_gen; }

  PSAdaptiveSizePolicy* size_policy() { return _size_policy; }

  static GCPolicyCounters* gc_policy_counters() { return _gc_policy_counters; }

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

  HeapWord* satisfy_failed_allocation(size_t size, bool is_tlab);

  // Support for System.gc()
  void collect(GCCause::Cause cause) override;

  void collect_at_safepoint(bool full);

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
  void print_heap_on(outputStream* st) const override;
  void print_gc_on(outputStream* st) const override;
  void gc_threads_do(ThreadClosure* tc) const override;

  WorkerThreads* safepoint_workers() override { return &_workers; }

  PreGenGCValues get_pre_gc_values() const;
  void print_heap_change(const PreGenGCValues& pre_gc_values) const;

  // Used to print information about locations in the hs_err file.
  bool print_location(outputStream* st, void* addr) const override;

  void verify(VerifyOption option /* ignored */) override;

  void resize_after_young_gc(bool is_survivor_overflowing);
  void resize_after_full_gc();

  GCMemoryManager* old_gc_manager() const { return _old_manager; }
  GCMemoryManager* young_gc_manager() const { return _young_manager; }

  WorkerThreads& workers() {
    return _workers;
  }

  // Support for loading objects from CDS archive into the heap
  bool can_load_archived_objects() const override { return true; }
  HeapWord* allocate_loaded_archive_space(size_t size) override;
  void complete_loaded_archive_space(MemRegion archive_space) override;

  void pin_object(JavaThread* thread, oop obj) override;
  void unpin_object(JavaThread* thread, oop obj) override;
};

#endif // SHARE_GC_PARALLEL_PARALLELSCAVENGEHEAP_HPP
