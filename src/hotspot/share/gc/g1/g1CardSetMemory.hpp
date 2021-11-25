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

#ifndef SHARE_GC_G1_G1CARDSETMEMORY_HPP
#define SHARE_GC_G1_G1CARDSETMEMORY_HPP

#include "gc/g1/g1CardSet.hpp"
#include "gc/g1/g1CardSetContainers.hpp"
#include "gc/g1/g1SegmentedArray.hpp"
#include "gc/g1/g1SegmentedArrayFreePool.hpp"
#include "memory/allocation.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/lockFreeStack.hpp"

class G1CardSetConfiguration;
class outputStream;

// Collects G1CardSetAllocator options/heuristics. Called by G1CardSetAllocator
// to determine the next size of the allocated G1CardSetBuffer.
class G1CardSetAllocOptions : public G1SegmentedArrayAllocOptions {
  static const uint MinimumBufferSize = 8;
  static const uint MaximumBufferSize =  UINT_MAX / 2;

  uint exponential_expand(uint prev_num_elems) const {
    return clamp(prev_num_elems * 2, _initial_num_elems, _max_num_elems);
  }

public:
  static const uint BufferAlignment = 8;

  G1CardSetAllocOptions(uint elem_size, uint initial_num_elems = MinimumBufferSize, uint max_num_elems = MaximumBufferSize) :
    G1SegmentedArrayAllocOptions(align_up(elem_size, BufferAlignment), initial_num_elems, max_num_elems, BufferAlignment) {
  }

  virtual uint next_num_elems(uint prev_num_elems) const override {
    return exponential_expand(prev_num_elems);
  }
};

typedef G1SegmentedArrayBuffer<mtGCCardSet> G1CardSetBuffer;

typedef G1SegmentedArrayBufferList<mtGCCardSet> G1CardSetBufferList;

// Arena-like allocator for (card set) heap memory objects (Elem elements).
//
// Allocation and deallocation in the first phase on G1CardSetContainer basis
// may occur by multiple threads at once.
//
// Allocation occurs from an internal free list of G1CardSetContainers first,
// only then trying to bump-allocate from the current G1CardSetBuffer. If there is
// none, this class allocates a new G1CardSetBuffer (allocated from the C heap,
// asking the G1CardSetAllocOptions instance about sizes etc) and uses that one.
//
// The NodeStack free list is a linked list of G1CardSetContainers
// within all G1CardSetBuffer instances allocated so far. It uses a separate
// pending list and global synchronization to avoid the ABA problem when the
// user frees a memory object.
//
// The class also manages a few counters for statistics using atomic operations.
// Their values are only consistent within each other with extra global
// synchronization.
//
// Since it is expected that every CardSet (and in extension each region) has its
// own set of allocators, there is intentionally no padding between them to save
// memory.
template <class Elem>
class G1CardSetAllocator {
  // G1CardSetBuffer management.

  typedef G1SegmentedArray<Elem, mtGCCardSet> SegmentedArray;
  // G1CardSetContainer node management within the G1CardSetBuffers allocated
  // by this allocator.
  static G1CardSetContainer* volatile* next_ptr(G1CardSetContainer& node);
  typedef LockFreeStack<G1CardSetContainer, &G1CardSetAllocator::next_ptr> NodeStack;

  SegmentedArray _segmented_array;
  volatile bool _transfer_lock;
  NodeStack _free_nodes_list;
  NodeStack _pending_nodes_list;

  volatile uint _num_pending_nodes;   // Number of nodes in the pending list.
  volatile uint _num_free_nodes;      // Number of nodes in the free list.

  // Try to transfer nodes from _pending_nodes_list to _free_nodes_list, with a
  // synchronization delay for any in-progress pops from the _free_nodes_list
  // to solve ABA here.
  bool try_transfer_pending();

  uint num_free_elems() const;

public:
  G1CardSetAllocator(const char* name,
                     const G1CardSetAllocOptions* buffer_options,
                     G1CardSetBufferList* free_buffer_list);
  ~G1CardSetAllocator();

  Elem* allocate();
  void free(Elem* elem);

  // Deallocate all buffers to the free buffer list and reset this allocator. Must
  // be called in a globally synchronized area.
  void drop_all();

  size_t mem_size() const {
    return sizeof(*this) +
      _segmented_array.num_buffers() * sizeof(G1CardSetBuffer) + _segmented_array.num_available_nodes() * _segmented_array.elem_size();
  }

  size_t wasted_mem_size() const {
    return (_segmented_array.num_available_nodes() - (_segmented_array.num_allocated_nodes() - _num_pending_nodes)) * _segmented_array.elem_size();
  }

  inline uint num_buffers() { return _segmented_array.num_buffers(); }

  void print(outputStream* os);
};

class G1CardSetMemoryManager : public CHeapObj<mtGCCardSet> {
  G1CardSetConfiguration* _config;

  G1CardSetAllocator<G1CardSetContainer>* _allocators;

  uint num_mem_object_types() const;
public:
  G1CardSetMemoryManager(G1CardSetConfiguration* config,
                         G1CardSetFreePool* free_list_pool);

  virtual ~G1CardSetMemoryManager();

  // Allocate and free a memory object of given type.
  inline uint8_t* allocate(uint type);
  void free(uint type, void* value);

  // Allocate and free a hash table node.
  inline uint8_t* allocate_node();
  inline void free_node(void* value);

  void flush();

  void print(outputStream* os);

  size_t mem_size() const;
  size_t wasted_mem_size() const;

  G1CardSetMemoryStats memory_stats() const;
};

#endif // SHARE_GC_G1_G1CARDSETMEMORY_HPP
