/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/z/zAllocationFlags.hpp"
#include "gc/z/zArray.hpp"
#include "gc/z/zGeneration.hpp"
#include "gc/z/zObjectAllocator.hpp"
#include "gc/z/zPageAge.hpp"
#include "gc/z/zPageAllocator.hpp"
#include "gc/z/zPageTable.hpp"
#include "gc/z/zPageType.hpp"
#include "gc/z/zServiceability.hpp"
#include "gc/z/zTLABUsage.hpp"

class OopFieldClosure;

class ZHeap {
  friend class ZForwardingTest;
  friend class ZLiveMapTest;
  friend class VMStructs;

private:
  static ZHeap*    _heap;

  ZPageAllocator   _page_allocator;
  ZPageTable       _page_table;

  ZObjectAllocator _object_allocator;

  ZServiceability  _serviceability;

  ZGenerationOld   _old;
  ZGenerationYoung _young;

  ZTLABUsage       _tlab_usage;

  bool             _initialized;

  // Page allocation accounting
  void account_alloc_page(ZPage* page);
  void account_undo_alloc_page(ZPage* page);

public:
  static ZHeap* heap();

  ZHeap();

  bool is_initialized() const;

  void out_of_memory() const;

  // Heap metrics
  size_t min_capacity() const;
  size_t max_capacity() const;
  size_t soft_max_capacity() const;
  size_t capacity() const;
  size_t used() const;
  size_t used_generation(ZGenerationId id) const;
  size_t used_young() const;
  size_t used_old() const;
  size_t unused() const;

  size_t tlab_capacity() const;
  size_t tlab_used() const;
  size_t max_tlab_size() const;
  size_t unsafe_max_tlab_alloc() const;
  void reset_tlab_used();

  bool is_in(uintptr_t addr) const;
  bool is_in_page_relaxed(const ZPage* page, zaddress addr) const;

  bool is_young(zaddress addr) const;
  bool is_young(volatile zpointer* ptr) const;

  bool is_old(zaddress addr) const;
  bool is_old(volatile zpointer* ptr) const;

  ZPage* page(zaddress addr) const;
  ZPage* page(volatile zpointer* addr) const;

  // Liveness
  bool is_object_live(zaddress addr) const;
  bool is_object_strongly_live(zaddress addr) const;
  void keep_alive(oop obj);
  void mark_flush(Thread* thread);

  // Page allocation
  ZPage* alloc_page(ZPageType type, size_t size, ZAllocationFlags flags, ZPageAge age);
  void undo_alloc_page(ZPage* page);
  void free_page(ZPage* page);
  size_t free_empty_pages(ZGenerationId id, const ZArray<ZPage*>* pages);
  void retire_allocating_pages(ZPageAgeRange range);

  // Object allocation
  zaddress alloc_object(size_t size);
  zaddress alloc_tlab(size_t size);
  zaddress alloc_object_for_relocation(size_t size, ZPageAge age);
  void undo_alloc_object_for_relocation(zaddress addr, size_t size);

  bool is_alloc_stalling() const;
  bool is_alloc_stalling_for_old() const;
  void handle_alloc_stalling_for_young();
  void handle_alloc_stalling_for_old(bool cleared_soft_refs);

  // Continuations
  bool is_allocating(zaddress addr) const;

  // Iteration
  void object_iterate(ObjectClosure* object_cl, bool visit_weaks);
  void object_and_field_iterate_for_verify(ObjectClosure* object_cl, OopFieldClosure* field_cl, bool visit_weaks);
  ParallelObjectIteratorImpl* parallel_object_iterator(uint nworkers, bool visit_weaks);

  void threads_do(ThreadClosure* tc) const;

  // Serviceability
  void serviceability_initialize();
  GCMemoryManager* serviceability_cycle_memory_manager(bool minor);
  GCMemoryManager* serviceability_pause_memory_manager(bool minor);
  MemoryPool* serviceability_memory_pool(ZGenerationId id);
  ZServiceabilityCounters* serviceability_counters();

  // Printing
  void print_usage_on(outputStream* st) const;
  void print_gc_on(outputStream* st) const;
  void print_globals_on(outputStream* st) const;
  void print_page_table_on(outputStream* st) const;
  bool print_location(outputStream* st, uintptr_t addr) const;
  bool print_location(outputStream* st, zaddress addr) const;
  bool print_location(outputStream* st, zpointer ptr) const;

  // Verification
  bool is_oop(uintptr_t addr) const;
};

#endif // SHARE_GC_Z_ZHEAP_HPP
