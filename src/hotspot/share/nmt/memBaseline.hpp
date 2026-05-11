/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_NMT_MEMBASELINE_HPP
#define SHARE_NMT_MEMBASELINE_HPP

#include "memory/metaspaceStats.hpp"
#include "nmt/mallocSiteTable.hpp"
#include "nmt/mallocTracker.hpp"
#include "nmt/nmtCommon.hpp"
#include "nmt/nmtHashTable.hpp"
#include "nmt/virtualMemoryTracker.hpp"
#include "runtime/mutex.hpp"

/*
 * Baseline a memory snapshot
 */
class MemBaseline {
 public:

  enum BaselineType {
    Not_baselined,
    Summary_baselined,
    Detail_baselined
  };

  enum SortingOrder {
    by_address,       // by memory address
    by_size,          // by memory size
    by_site,          // by call site where the memory is allocated from
    by_site_and_tag,  // by call site and memory tag
    _count
  };

 private:
  // Summary information
  MallocMemorySnapshot   _malloc_memory_snapshot;
  VirtualMemorySnapshot  _virtual_memory_snapshot;
  MetaspaceCombinedStats _metaspace_stats;

  size_t                 _instance_class_count;
  size_t                 _array_class_count;
  size_t                 _thread_count;

  // Allocation sites information
  // Malloc allocation sites
  MallocSite* _malloc_sites;
  int         _malloc_sites_length;

  // All virtual memory allocations
  RegionsTree* _vma_allocations;

  // Virtual memory allocations by allocation sites, always in by_address
  // order
  VirtualMemoryAllocationSite* _virtual_memory_sites;
  int                          _virtual_memory_sites_length;

  SortingOrder         _malloc_sites_order;
  SortingOrder         _virtual_memory_sites_order;

  BaselineType         _baseline_type;

 public:
  // create a memory baseline
  MemBaseline():
    _instance_class_count(0), _array_class_count(0), _thread_count(0),
    _malloc_sites(nullptr), _malloc_sites_length(0),
    _vma_allocations(nullptr),
    _virtual_memory_sites(nullptr), _virtual_memory_sites_length(0),
    _baseline_type(Not_baselined) {
  }

  ~MemBaseline() {
    os::free(_malloc_sites);
    os::free(_virtual_memory_sites);
    delete _vma_allocations;
  }

  void baseline(bool summaryOnly = true);

  BaselineType baseline_type() const { return _baseline_type; }

  MallocMemorySnapshot* malloc_memory_snapshot() {
    return &_malloc_memory_snapshot;
  }

  VirtualMemorySnapshot* virtual_memory_snapshot() {
    return &_virtual_memory_snapshot;
  }

  const MetaspaceCombinedStats& metaspace_stats() const {
    return _metaspace_stats;
  }

  void sort_malloc_sites(SortingOrder order);
  void sort_virtual_memory_sites(SortingOrder order);

  MallocSite* malloc_sites() { return _malloc_sites; }
  int malloc_sites_length()  { return _malloc_sites_length; }

  VirtualMemoryAllocationSite* virtual_memory_sites()        { return _virtual_memory_sites; }
  int                          virtual_memory_sites_length() { return _virtual_memory_sites_length; }

  // Virtual memory allocation iterator always returns in virtual memory
  // base address order.
  RegionsTree* virtual_memory_allocations() {
    assert(_vma_allocations != nullptr, "Not detail baseline");
    return _vma_allocations;
  }

  // Total reserved memory = total malloc'd memory + total reserved virtual
  // memory
  size_t total_reserved_memory() const {
    assert(baseline_type() != Not_baselined, "Not yet baselined");
    size_t amount = _malloc_memory_snapshot.total() +
           _virtual_memory_snapshot.total_reserved();
    return amount;
  }

  // Total committed memory = total malloc'd memory + total committed
  // virtual memory
  size_t total_committed_memory() const {
    assert(baseline_type() != Not_baselined, "Not yet baselined");
    size_t amount = _malloc_memory_snapshot.total() +
           _virtual_memory_snapshot.total_committed();
    return amount;
  }

  size_t total_arena_memory() const {
    assert(baseline_type() != Not_baselined, "Not yet baselined");
    return _malloc_memory_snapshot.total_arena();
  }

  size_t malloc_tracking_overhead() const {
    assert(baseline_type() != Not_baselined, "Not yet baselined");
    MemBaseline* bl = const_cast<MemBaseline*>(this);
    return bl->_malloc_memory_snapshot.malloc_overhead();
  }

  MallocMemory* malloc_memory(MemTag mem_tag) {
    assert(baseline_type() != Not_baselined, "Not yet baselined");
    return _malloc_memory_snapshot.by_tag(mem_tag);
  }

  VirtualMemory* virtual_memory(MemTag mem_tag) {
    assert(baseline_type() != Not_baselined, "Not yet baselined");
    return _virtual_memory_snapshot.by_tag(mem_tag);
  }


  size_t class_count() const {
    assert(baseline_type() != Not_baselined, "Not yet baselined");
    return _instance_class_count + _array_class_count;
  }

  size_t instance_class_count() const {
    assert(baseline_type() != Not_baselined, "Not yet baselined");
    return _instance_class_count;
  }

  size_t array_class_count() const {
    assert(baseline_type() != Not_baselined, "Not yet baselined");
    return _array_class_count;
  }

  size_t thread_count() const {
    assert(baseline_type() != Not_baselined, "Not yet baselined");
    return _thread_count;
  }

  // reset the baseline for reuse
  void reset() {
    _baseline_type = Not_baselined;
    // _malloc_memory_snapshot and _virtual_memory_snapshot are copied over.
    _instance_class_count  = 0;
    _array_class_count = 0;
    _thread_count = 0;

    os::free(_malloc_sites);
    _malloc_sites_length = 0;
    os::free(_virtual_memory_sites);
    _virtual_memory_sites_length = 0;
    delete _vma_allocations;
    _vma_allocations = nullptr;
  }

 private:
  // Baseline summary information
  void baseline_summary();

  // Baseline allocation sites (detail tracking only)
  bool baseline_allocation_sites();

  // Aggregate virtual memory allocation by allocation sites
  bool aggregate_virtual_memory_allocation_sites();
};

#endif // SHARE_NMT_MEMBASELINE_HPP
