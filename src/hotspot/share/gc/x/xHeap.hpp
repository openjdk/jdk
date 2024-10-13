/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_X_XHEAP_HPP
#define SHARE_GC_X_XHEAP_HPP

#include "gc/x/xAllocationFlags.hpp"
#include "gc/x/xArray.hpp"
#include "gc/x/xForwardingTable.hpp"
#include "gc/x/xMark.hpp"
#include "gc/x/xObjectAllocator.hpp"
#include "gc/x/xPageAllocator.hpp"
#include "gc/x/xPageTable.hpp"
#include "gc/x/xReferenceProcessor.hpp"
#include "gc/x/xRelocate.hpp"
#include "gc/x/xRelocationSet.hpp"
#include "gc/x/xWeakRootsProcessor.hpp"
#include "gc/x/xServiceability.hpp"
#include "gc/x/xUnload.hpp"
#include "gc/x/xWorkers.hpp"

class ThreadClosure;
class VMStructs;
class XPage;
class XRelocationSetSelector;

class XHeap {
  friend class ::VMStructs;

private:
  static XHeap*       _heap;

  XWorkers            _workers;
  XObjectAllocator    _object_allocator;
  XPageAllocator      _page_allocator;
  XPageTable          _page_table;
  XForwardingTable    _forwarding_table;
  XMark               _mark;
  XReferenceProcessor _reference_processor;
  XWeakRootsProcessor _weak_roots_processor;
  XRelocate           _relocate;
  XRelocationSet      _relocation_set;
  XUnload             _unload;
  XServiceability     _serviceability;

  void flip_to_marked();
  void flip_to_remapped();

  void free_empty_pages(XRelocationSetSelector* selector, int bulk);

  void out_of_memory();

public:
  static XHeap* heap();

  XHeap();

  bool is_initialized() const;

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

  // Threads
  uint active_workers() const;
  void set_active_workers(uint nworkers);
  void threads_do(ThreadClosure* tc) const;

  // Reference processing
  ReferenceDiscoverer* reference_discoverer();
  void set_soft_reference_policy(bool clear);

  // Non-strong reference processing
  void process_non_strong_references();

  // Page allocation
  XPage* alloc_page(uint8_t type, size_t size, XAllocationFlags flags);
  void undo_alloc_page(XPage* page);
  void free_page(XPage* page, bool reclaimed);
  void free_pages(const XArray<XPage*>* pages, bool reclaimed);

  // Object allocation
  uintptr_t alloc_tlab(size_t size);
  uintptr_t alloc_object(size_t size);
  uintptr_t alloc_object_for_relocation(size_t size);
  void undo_alloc_object_for_relocation(uintptr_t addr, size_t size);
  bool has_alloc_stalled() const;
  void check_out_of_memory();

  // Marking
  bool is_object_live(uintptr_t addr) const;
  bool is_object_strongly_live(uintptr_t addr) const;
  template <bool gc_thread, bool follow, bool finalizable, bool publish> void mark_object(uintptr_t addr);
  void mark_start();
  void mark(bool initial);
  void mark_flush_and_free(Thread* thread);
  bool mark_end();
  void mark_free();
  void keep_alive(oop obj);

  // Relocation set
  void select_relocation_set();
  void reset_relocation_set();

  // Relocation
  void relocate_start();
  uintptr_t relocate_object(uintptr_t addr);
  uintptr_t remap_object(uintptr_t addr);
  void relocate();

  // Continuations
  bool is_allocating(uintptr_t addr) const;

  // Iteration
  void object_iterate(ObjectClosure* cl, bool visit_weaks);
  ParallelObjectIteratorImpl* parallel_object_iterator(uint nworkers, bool visit_weaks);
  void pages_do(XPageClosure* cl);

  // Serviceability
  void serviceability_initialize();
  GCMemoryManager* serviceability_cycle_memory_manager();
  GCMemoryManager* serviceability_pause_memory_manager();
  MemoryPool* serviceability_memory_pool();
  XServiceabilityCounters* serviceability_counters();

  // Printing
  void print_on(outputStream* st) const;
  void print_extended_on(outputStream* st) const;
  bool print_location(outputStream* st, uintptr_t addr) const;

  // Verification
  bool is_oop(uintptr_t addr) const;
  void verify();
};

#endif // SHARE_GC_X_XHEAP_HPP
