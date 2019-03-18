/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZHEAP_HPP
#define SHARE_GC_Z_ZHEAP_HPP

#include "gc/shared/gcTimer.hpp"
#include "gc/z/zAllocationFlags.hpp"
#include "gc/z/zArray.hpp"
#include "gc/z/zForwardingTable.hpp"
#include "gc/z/zList.hpp"
#include "gc/z/zLock.hpp"
#include "gc/z/zMark.hpp"
#include "gc/z/zObjectAllocator.hpp"
#include "gc/z/zPage.hpp"
#include "gc/z/zPageAllocator.hpp"
#include "gc/z/zPageTable.hpp"
#include "gc/z/zReferenceProcessor.hpp"
#include "gc/z/zRelocate.hpp"
#include "gc/z/zRelocationSet.hpp"
#include "gc/z/zRelocationSetSelector.hpp"
#include "gc/z/zRootsIterator.hpp"
#include "gc/z/zWeakRootsProcessor.hpp"
#include "gc/z/zServiceability.hpp"
#include "gc/z/zUnload.hpp"
#include "gc/z/zWorkers.hpp"
#include "memory/allocation.hpp"

class ZHeap {
  friend class VMStructs;

private:
  static ZHeap*       _heap;

  ZWorkers            _workers;
  ZObjectAllocator    _object_allocator;
  ZPageAllocator      _page_allocator;
  ZPageTable          _page_table;
  ZForwardingTable    _forwarding_table;
  ZMark               _mark;
  ZReferenceProcessor _reference_processor;
  ZWeakRootsProcessor _weak_roots_processor;
  ZRelocate           _relocate;
  ZRelocationSet      _relocation_set;
  ZUnload             _unload;
  ZServiceability     _serviceability;

  size_t heap_min_size() const;
  size_t heap_max_size() const;
  size_t heap_max_reserve_size() const;

  void before_flip();
  void after_flip();

  void flip_to_marked();
  void flip_to_remapped();

  void out_of_memory();
  void fixup_partial_loads();

public:
  static ZHeap* heap();

  ZHeap();

  bool is_initialized() const;

  // Heap metrics
  size_t min_capacity() const;
  size_t max_capacity() const;
  size_t current_max_capacity() const;
  size_t capacity() const;
  size_t max_reserve() const;
  size_t used_high() const;
  size_t used_low() const;
  size_t used() const;
  size_t allocated() const;
  size_t reclaimed() const;

  size_t tlab_capacity() const;
  size_t tlab_used() const;
  size_t max_tlab_size() const;
  size_t unsafe_max_tlab_alloc() const;

  bool is_in(uintptr_t addr) const;

  // Block
  uintptr_t block_start(uintptr_t addr) const;
  bool block_is_obj(uintptr_t addr) const;

  // Workers
  uint nconcurrent_worker_threads() const;
  uint nconcurrent_no_boost_worker_threads() const;
  void set_boost_worker_threads(bool boost);
  void worker_threads_do(ThreadClosure* tc) const;
  void print_worker_threads_on(outputStream* st) const;

  // Reference processing
  ReferenceDiscoverer* reference_discoverer();
  void set_soft_reference_policy(bool clear);

  // Non-strong reference processing
  void process_non_strong_references();

  // Page allocation
  ZPage* alloc_page(uint8_t type, size_t size, ZAllocationFlags flags);
  void undo_alloc_page(ZPage* page);
  void free_page(ZPage* page, bool reclaimed);

  // Object allocation
  uintptr_t alloc_tlab(size_t size);
  uintptr_t alloc_object(size_t size);
  uintptr_t alloc_object_for_relocation(size_t size);
  void undo_alloc_object_for_relocation(uintptr_t addr, size_t size);
  bool is_alloc_stalled() const;
  void check_out_of_memory();

  // Marking
  bool is_object_live(uintptr_t addr) const;
  bool is_object_strongly_live(uintptr_t addr) const;
  template <bool finalizable, bool publish> void mark_object(uintptr_t addr);
  void mark_start();
  void mark(bool initial);
  void mark_flush_and_free(Thread* thread);
  bool mark_end();

  // Relocation set
  void select_relocation_set();
  void reset_relocation_set();

  // Relocation
  void relocate_start();
  uintptr_t relocate_object(uintptr_t addr);
  uintptr_t remap_object(uintptr_t addr);
  void relocate();

  // Iteration
  void object_iterate(ObjectClosure* cl, bool visit_referents);

  // Serviceability
  void serviceability_initialize();
  GCMemoryManager* serviceability_memory_manager();
  MemoryPool* serviceability_memory_pool();
  ZServiceabilityCounters* serviceability_counters();

  // Printing
  void print_on(outputStream* st) const;
  void print_extended_on(outputStream* st) const;

  // Verification
  bool is_oop(oop object) const;
  void verify();
};

#endif // SHARE_GC_Z_ZHEAP_HPP
