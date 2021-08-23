/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zCollector.hpp"
#include "gc/z/zGeneration.hpp"
#include "gc/z/zPageAge.hpp"
#include "gc/z/zPageAllocator.hpp"
#include "gc/z/zPageTable.hpp"
#include "gc/z/zServiceability.hpp"

class OopFieldClosure;

class ZHeap {
  friend class VMStructs;
  friend class ZForwardingTest;
  friend class ZLiveMapTest;

private:
  static ZHeap*       _heap;

  ZPageAllocator      _page_allocator;
  ZPageTable          _page_table;
  ZServiceability     _serviceability;

  ZYoungGeneration    _young_generation;
  ZOldGeneration      _old_generation;

  ZMinorCollector     _minor_collector;
  ZMajorCollector     _major_collector;

  bool                _initialized;

public:
  static ZHeap* heap();

  ZHeap();

  bool is_initialized() const;

  void out_of_memory();

  // Generations
  ZGeneration* get_generation(ZCollectorId id);
  ZGeneration* get_generation(ZGenerationId id);
  ZYoungGeneration* young_generation();
  ZOldGeneration* old_generation();

  ZCollector* collector(ZCollectorId id);
  ZCollector* collector(ZGenerationId id);
  ZMinorCollector* minor_collector();
  ZMajorCollector* major_collector();

  // Heap metrics
  size_t min_capacity() const;
  size_t max_capacity() const;
  size_t soft_max_capacity() const;
  size_t capacity() const;
  size_t used() const;
  size_t unused() const;

  size_t tlab_capacity() const;
  size_t tlab_used() const;
  size_t max_tlab_size() const;
  size_t unsafe_max_tlab_alloc() const;

  bool is_in(uintptr_t addr) const;
  bool is_in_page_relaxed(const ZPage* page, zaddress addr) const;
  uint32_t hash_oop(zaddress addr) const;

  bool is_young(zaddress addr) const;
  bool is_old(zaddress addr) const;

  // Marking
  ZPage* page(zaddress addr) const;
  bool is_object_live(zaddress addr) const;
  bool is_object_strongly_live(zaddress addr) const;
  template <bool resurrect, bool gc_thread, bool follow, bool finalizable, bool publish>
  void mark_object(zaddress addr);
  template <bool follow, bool publish>
  void mark_minor_object(zaddress addr);
  void mark_follow_invisible_root(zaddress addr, size_t size);
  void mark_flush_and_free(Thread* thread);
  void keep_alive(oop obj);

  // Relocating
  ZCollector* remap_collector(zpointer ptr);

  // Remembering
  void remember(volatile zpointer* p);
  void remember_filtered(volatile zpointer* p);
  void remember_fields(zaddress addr);

  // Page allocation
  ZPage* alloc_page(uint8_t type, size_t size, ZAllocationFlags flags, ZCollector* collector, ZGenerationId generation, ZPageAge age);
  void undo_alloc_page(ZPage* page);
  void free_page(ZPage* page, ZCollector* collector);
  void free_pages(const ZArray<ZPage*>* pages, ZCollector* collector);
  void safe_destroy_page(ZPage* page);
  void recycle_page(ZPage* page);

  // Object allocation
  bool has_alloc_stalled() const;
  void check_out_of_memory();

  // Iteration
  void object_iterate(ObjectClosure* object_cl, bool visit_weaks);
  void object_and_field_iterate(ObjectClosure* object_cl, OopFieldClosure* field_cl, bool visit_weaks);
  ParallelObjectIterator* parallel_object_iterator(uint nworkers, bool visit_weaks);

  void threads_do(ThreadClosure* tc) const;

  // Serviceability
  void serviceability_initialize();
  GCMemoryManager* serviceability_cycle_memory_manager();
  GCMemoryManager* serviceability_pause_memory_manager();
  MemoryPool* serviceability_memory_pool();
  ZServiceabilityCounters* serviceability_counters();

  // Printing
  void print_on(outputStream* st) const;
  void print_extended_on(outputStream* st) const;
  bool print_location(outputStream* st, uintptr_t addr) const;

  // Verification
  bool is_oop(uintptr_t addr) const;
  bool is_remembered(volatile zpointer* p);
  void verify();
};

#endif // SHARE_GC_Z_ZHEAP_HPP
