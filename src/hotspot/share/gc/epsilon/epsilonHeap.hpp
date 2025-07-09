/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2017, 2022, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_EPSILON_EPSILONHEAP_HPP
#define SHARE_GC_EPSILON_EPSILONHEAP_HPP

#include "gc/epsilon/epsilonBarrierSet.hpp"
#include "gc/epsilon/epsilonMonitoringSupport.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/softRefPolicy.hpp"
#include "gc/shared/space.hpp"
#include "memory/virtualspace.hpp"
#include "services/memoryManager.hpp"

class EpsilonHeap : public CollectedHeap {
  friend class VMStructs;
private:
  EpsilonMonitoringSupport* _monitoring_support;
  MemoryPool* _pool;
  GCMemoryManager _memory_manager;
  ContiguousSpace* _space;
  VirtualSpace _virtual_space;
  size_t _max_tlab_size;
  size_t _step_counter_update;
  size_t _step_heap_print;
  int64_t _decay_time_ns;
  volatile size_t _last_counter_update;
  volatile size_t _last_heap_print;

  void print_tracing_info() const override;
  void stop() override {};

public:
  static EpsilonHeap* heap();

  EpsilonHeap() :
          _memory_manager("Epsilon Heap"),
          _space(nullptr) {};

  Name kind() const override {
    return CollectedHeap::Epsilon;
  }

  const char* name() const override {
    return "Epsilon";
  }

  jint initialize() override;
  void initialize_serviceability() override;

  GrowableArray<GCMemoryManager*> memory_managers() override;
  GrowableArray<MemoryPool*> memory_pools() override;

  size_t max_capacity() const override { return _virtual_space.reserved_size();  }
  size_t capacity()     const override { return _virtual_space.committed_size(); }
  size_t used()         const override { return _space->used(); }

  bool is_in(const void* p) const override {
    return _space->is_in(p);
  }

  bool requires_barriers(stackChunkOop obj) const override { return false; }

  // Allocation
  HeapWord* allocate_work(size_t size, bool verbose = true);
  HeapWord* mem_allocate(size_t size, bool* gc_overhead_limit_was_exceeded) override;
  HeapWord* allocate_new_tlab(size_t min_size,
                              size_t requested_size,
                              size_t* actual_size) override;

  // TLAB allocation
  size_t tlab_capacity(Thread* thr)         const override { return capacity();     }
  size_t tlab_used(Thread* thr)             const override { return used();         }
  size_t max_tlab_size()                    const override { return _max_tlab_size; }
  size_t unsafe_max_tlab_alloc(Thread* thr) const override;

  void collect(GCCause::Cause cause) override;
  void do_full_collection(bool clear_all_soft_refs) override;

  // Heap walking support
  void object_iterate(ObjectClosure* cl) override;

  // Object pinning support: every object is implicitly pinned
  void pin_object(JavaThread* thread, oop obj) override { }
  void unpin_object(JavaThread* thread, oop obj) override { }

  // No support for block parsing.
  HeapWord* block_start(const void* addr) const { return nullptr;  }
  bool block_is_obj(const HeapWord* addr) const { return false; }

  // No GC threads
  void gc_threads_do(ThreadClosure* tc) const override {}

  // No nmethod handling
  void register_nmethod(nmethod* nm) override {}
  void unregister_nmethod(nmethod* nm) override {}
  void verify_nmethod(nmethod* nm) override {}

  // No heap verification
  void prepare_for_verify() override {}
  void verify(VerifyOption option) override {}

  MemRegion reserved_region() const { return _reserved; }
  bool is_in_reserved(const void* addr) const { return _reserved.contains(addr); }

  // Support for loading objects from CDS archive into the heap
  bool can_load_archived_objects() const override { return true; }
  HeapWord* allocate_loaded_archive_space(size_t size) override;

  void print_heap_on(outputStream* st) const override;
  void print_gc_on(outputStream* st) const override {}
  bool print_location(outputStream* st, void* addr) const override;

private:
  void print_heap_info(size_t used) const;
  void print_metaspace_info() const;

};

#endif // SHARE_GC_EPSILON_EPSILONHEAP_HPP
