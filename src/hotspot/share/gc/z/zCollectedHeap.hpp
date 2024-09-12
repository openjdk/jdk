/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef SHARE_GC_Z_ZCOLLECTEDHEAP_HPP
#define SHARE_GC_Z_ZCOLLECTEDHEAP_HPP

#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/softRefPolicy.hpp"
#include "gc/z/zBarrierSet.hpp"
#include "gc/z/zHeap.hpp"
#include "gc/z/zInitialize.hpp"
#include "gc/z/zRuntimeWorkers.hpp"
#include "memory/metaspace.hpp"
#include "services/memoryUsage.hpp"

class ZDirector;
class ZDriverMajor;
class ZDriverMinor;
class ZStat;

class ZCollectedHeap : public CollectedHeap {
  friend class VMStructs;

private:
  ZBarrierSet       _barrier_set;
  ZInitialize       _initialize;
  ZHeap             _heap;
  ZDriverMinor*     _driver_minor;
  ZDriverMajor*     _driver_major;
  ZDirector*        _director;
  ZStat*            _stat;
  ZRuntimeWorkers   _runtime_workers;

  HeapWord* allocate_new_tlab(size_t min_size,
                              size_t requested_size,
                              size_t* actual_size) override;

public:
  static ZCollectedHeap* heap();

  ZCollectedHeap();
  Name kind() const override;
  const char* name() const override;
  jint initialize() override;
  void initialize_serviceability() override;
  void stop() override;

  size_t max_capacity() const override;
  size_t capacity() const override;
  size_t used() const override;
  size_t unused() const override;

  bool is_maximal_no_gc() const override;
  bool is_in(const void* p) const override;
  bool requires_barriers(stackChunkOop obj) const override;

  oop array_allocate(Klass* klass, size_t size, int length, bool do_zero, TRAPS) override;
  HeapWord* mem_allocate(size_t size, bool* gc_overhead_limit_was_exceeded) override;
  MetaWord* satisfy_failed_metadata_allocation(ClassLoaderData* loader_data,
                                               size_t size,
                                               Metaspace::MetadataType mdtype) override;
  void collect(GCCause::Cause cause) override;
  void collect_as_vm_thread(GCCause::Cause cause) override;
  void do_full_collection(bool clear_all_soft_refs) override;

  size_t tlab_capacity(Thread* thr) const override;
  size_t tlab_used(Thread* thr) const override;
  size_t max_tlab_size() const override;
  size_t unsafe_max_tlab_alloc(Thread* thr) const override;

  MemoryUsage memory_usage() override;
  GrowableArray<GCMemoryManager*> memory_managers() override;
  GrowableArray<MemoryPool*> memory_pools() override;

  void object_iterate(ObjectClosure* cl) override;
  ParallelObjectIteratorImpl* parallel_object_iterator(uint nworkers) override;

  void keep_alive(oop obj) override;

  void register_nmethod(nmethod* nm) override;
  void unregister_nmethod(nmethod* nm) override;
  void verify_nmethod(nmethod* nmethod) override;

  WorkerThreads* safepoint_workers() override;

  void gc_threads_do(ThreadClosure* tc) const override;

  VirtualSpaceSummary create_heap_space_summary() override;

  bool contains_null(const oop* p) const override;

  void safepoint_synchronize_begin() override;
  void safepoint_synchronize_end() override;

  void pin_object(JavaThread* thread, oop obj) override;
  void unpin_object(JavaThread* thread, oop obj) override;

  void print_on(outputStream* st) const override;
  void print_on_error(outputStream* st) const override;
  void print_extended_on(outputStream* st) const override;
  void print_tracing_info() const override;
  bool print_location(outputStream* st, void* addr) const override;

  void prepare_for_verify() override;
  void verify(VerifyOption option /* ignored */) override;
  bool is_oop(oop object) const override;
  bool supports_concurrent_gc_breakpoints() const override;
};

#endif // SHARE_GC_Z_ZCOLLECTEDHEAP_HPP
