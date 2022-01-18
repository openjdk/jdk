/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1SEGMENTEDARRAYFREEPOOL_HPP
#define SHARE_GC_G1_G1SEGMENTEDARRAYFREEPOOL_HPP

#include "gc/g1/g1CardSet.hpp"
#include "gc/g1/g1SegmentedArray.hpp"
#include "utilities/growableArray.hpp"

// A set of free lists holding freed segments for use by G1SegmentedArray,
// e.g. G1CardSetAllocators::SegmentedArray
template<MEMFLAGS flag, typename Configuration>
class G1SegmentedArrayFreePool {
  // The global free pool.
  static G1SegmentedArrayFreePool _freelist_pool;
  static constexpr uint NUM = Configuration::num_mem_object_types();

  G1SegmentedArrayFreeList<flag>* _free_lists;

  G1SegmentedArrayFreePool();
  ~G1SegmentedArrayFreePool();

public:

  class G1ReturnMemoryProcessor;
  typedef GrowableArrayCHeap<G1ReturnMemoryProcessor*, mtGC> G1ReturnMemoryProcessorSet;
  class G1SegmentedArrayMemoryStats;

  static G1SegmentedArrayFreePool* free_list_pool() { return &_freelist_pool; }
  static G1SegmentedArrayMemoryStats free_list_sizes() { return _freelist_pool.memory_sizes(); }

  static void update_unlink_processors(G1ReturnMemoryProcessorSet* unlink_processors);

  G1SegmentedArrayFreeList<flag>* free_list(uint i) {
    assert(i < NUM, "must be");
    return &_free_lists[i];
  }

  G1SegmentedArrayMemoryStats memory_sizes() const;
  size_t mem_size() const;

  void print_on(outputStream* out);
};

// Statistics for a segmented array. Contains the number of segments and memory
// used for each. Note that statistics are typically not taken atomically so there
// can be inconsistencies. The user must be prepared for them.
template<MEMFLAGS flag, typename Configuration>
class G1SegmentedArrayFreePool<flag, Configuration>::G1SegmentedArrayMemoryStats {
  static constexpr uint NUM = Configuration::num_mem_object_types();

public:

  size_t _num_mem_sizes[NUM];
  size_t _num_segments[NUM];

  // Returns all-zero statistics.
  G1SegmentedArrayMemoryStats();

  void add(G1SegmentedArrayMemoryStats const other) {
    STATIC_ASSERT(ARRAY_SIZE(_num_segments) == ARRAY_SIZE(_num_mem_sizes));
    for (uint i = 0; i < ARRAY_SIZE(_num_mem_sizes); i++) {
      _num_mem_sizes[i] += other._num_mem_sizes[i];
      _num_segments[i] += other._num_segments[i];
    }
  }

  void clear();
};

// Data structure containing current in-progress state for returning memory to the
// operating system for a single G1SegmentedArrayFreeList.
template<MEMFLAGS flag, typename Configuration>
class G1SegmentedArrayFreePool<flag, Configuration>::G1ReturnMemoryProcessor : public CHeapObj<mtGC> {
  G1SegmentedArrayFreeList<flag>* _source;
  size_t _return_to_vm_size;

  G1SegmentedArraySegment<flag>* _first;
  size_t _unlinked_bytes;
  size_t _num_unlinked;

public:
  explicit G1ReturnMemoryProcessor(size_t return_to_vm) :
    _source(nullptr), _return_to_vm_size(return_to_vm), _first(nullptr), _unlinked_bytes(0), _num_unlinked(0) {
  }

  // Updates the instance members about the given free list for
  // the purpose of giving back memory. Only necessary members are updated,
  // e.g. if there is nothing to return to the VM, do not set the source list.
  void visit_free_list(G1SegmentedArrayFreeList<flag>* source);

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

#endif //SHARE_GC_G1_G1SEGMENTEDARRAYFREEPOOL_HPP
