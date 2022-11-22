/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1MONOTONICARENAFREEPOOL_HPP
#define SHARE_GC_G1_G1MONOTONICARENAFREEPOOL_HPP

#include "gc/g1/g1CardSet.hpp"
#include "gc/g1/g1MonotonicArena.hpp"
#include "utilities/growableArray.hpp"

// Statistics for a monotonic arena. Contains the number of segments and memory
// used for each. Note that statistics are typically not taken atomically so there
// can be inconsistencies. The user must be prepared for them.
class G1MonotonicArenaMemoryStats {
public:

  size_t _num_mem_sizes[G1CardSetConfiguration::num_mem_object_types()];
  size_t _num_segments[G1CardSetConfiguration::num_mem_object_types()];

  // Returns all-zero statistics.
  G1MonotonicArenaMemoryStats();

  void add(G1MonotonicArenaMemoryStats const other) {
    STATIC_ASSERT(ARRAY_SIZE(_num_segments) == ARRAY_SIZE(_num_mem_sizes));
    for (uint i = 0; i < ARRAY_SIZE(_num_mem_sizes); i++) {
      _num_mem_sizes[i] += other._num_mem_sizes[i];
      _num_segments[i] += other._num_segments[i];
    }
  }

  void clear();

  uint num_pools() const { return G1CardSetConfiguration::num_mem_object_types(); }
};

// A set of free lists holding freed segments for use by G1MonotonicArena,
// e.g. G1CardSetAllocators::_arena
class G1MonotonicArenaFreePool {
  using SegmentFreeList = G1MonotonicArena::SegmentFreeList;
  // The global free pool.
  static G1MonotonicArenaFreePool _freelist_pool;

  const uint _num_free_lists;
  SegmentFreeList* _free_lists;

public:
  static G1MonotonicArenaFreePool* free_list_pool() { return &_freelist_pool; }
  static G1MonotonicArenaMemoryStats free_list_sizes() { return _freelist_pool.memory_sizes(); }

  class G1ReturnMemoryProcessor;
  typedef GrowableArrayCHeap<G1ReturnMemoryProcessor*, mtGC> G1ReturnMemoryProcessorSet;

  static void update_unlink_processors(G1ReturnMemoryProcessorSet* unlink_processors);

  explicit G1MonotonicArenaFreePool(uint num_free_lists);
  ~G1MonotonicArenaFreePool();

  SegmentFreeList* free_list(uint i) {
    assert(i < _num_free_lists, "must be");
    return &_free_lists[i];
  }

  uint num_free_lists() const { return _num_free_lists; }

  G1MonotonicArenaMemoryStats memory_sizes() const;
  size_t mem_size() const;

  void print_on(outputStream* out);
};

// Data structure containing current in-progress state for returning memory to the
// operating system for a single G1SegmentFreeList.
class G1MonotonicArenaFreePool::G1ReturnMemoryProcessor : public CHeapObj<mtGC> {
  using SegmentFreeList = G1MonotonicArena::SegmentFreeList;
  using Segment = G1MonotonicArena::Segment;
  SegmentFreeList* _source;
  size_t _return_to_vm_size;

  Segment* _first;
  size_t _unlinked_bytes;
  size_t _num_unlinked;

public:
  explicit G1ReturnMemoryProcessor(size_t return_to_vm) :
    _source(nullptr), _return_to_vm_size(return_to_vm), _first(nullptr), _unlinked_bytes(0), _num_unlinked(0) {
  }

  // Updates the instance members about the given free list for
  // the purpose of giving back memory. Only necessary members are updated,
  // e.g. if there is nothing to return to the VM, do not set the source list.
  void visit_free_list(SegmentFreeList* source);

  bool finished_return_to_vm() const { return _return_to_vm_size == 0; }
  bool finished_return_to_os() const { return _first == nullptr; }

  // Returns memory to the VM until the given deadline expires. Returns true if
  // there is no more work. Guarantees forward progress, i.e. at least one segment
  // has been processed after returning.
  // return_to_vm() re-adds segments to the respective free list.
  bool return_to_vm(jlong deadline);
  // Returns memory to the VM until the given deadline expires. Returns true if
  // there is no more work. Guarantees forward progress, i.e. at least one segment
  // has been processed after returning.
  // return_to_os() gives back segments to the OS.
  bool return_to_os(jlong deadline);
};

#endif //SHARE_GC_G1_G1MONOTONICARENAFREEPOOL_HPP
