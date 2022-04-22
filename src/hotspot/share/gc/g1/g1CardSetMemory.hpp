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

#ifndef SHARE_GC_G1_G1CARDSETMEMORY_HPP
#define SHARE_GC_G1_G1CARDSETMEMORY_HPP

#include "gc/g1/g1CardSet.hpp"
#include "gc/g1/g1CardSetContainers.hpp"
#include "gc/g1/g1SegmentedArray.hpp"
#include "gc/g1/g1SegmentedArrayFreePool.hpp"
#include "gc/shared/freeListAllocator.hpp"
#include "memory/allocation.hpp"
#include "utilities/growableArray.hpp"

class G1CardSetConfiguration;
class outputStream;

// Collects G1CardSetAllocator options/heuristics. Called by G1CardSetAllocator
// to determine the next size of the allocated G1CardSetSegment.
class G1CardSetAllocOptions : public G1SegmentedArrayAllocOptions {
  static const uint MinimumNumSlots = 8;
  static const uint MaximumNumSlots = UINT_MAX / 2;

  uint exponential_expand(uint prev_num_slots) const {
    return clamp(prev_num_slots * 2, _initial_num_slots, _max_num_slots);
  }

public:
  static const uint SlotAlignment = 8;

  G1CardSetAllocOptions(uint slot_size, uint initial_num_slots = MinimumNumSlots, uint max_num_slots = MaximumNumSlots) :
    G1SegmentedArrayAllocOptions(mtGCCardSet, slot_size, initial_num_slots, max_num_slots, SlotAlignment) {
  }

  virtual uint next_num_slots(uint prev_num_slots) const override {
    return exponential_expand(prev_num_slots);
  }
};

using G1CardSetSegment = G1SegmentedArraySegment;

using G1CardSetFreeList = G1SegmentedArrayFreeList;

// Arena-like allocator for (card set) heap memory objects.
//
// Allocation occurs from an internal free list of objects first. If the free list is
// empty then tries to allocate from the G1SegmentedArray.
class G1CardSetAllocator {
  G1SegmentedArray _segmented_array;
  FreeListAllocator _free_slots_list;

public:
  G1CardSetAllocator(const char* name,
                     const G1CardSetAllocOptions* alloc_options,
                     G1CardSetFreeList* free_segment_list);
  ~G1CardSetAllocator();

  void* allocate();
  void free(void* slot);

  // Deallocate all segments to the free segment list and reset this allocator. Must
  // be called in a globally synchronized area.
  void drop_all();

  size_t mem_size() const;

  size_t wasted_mem_size() const;

  uint num_segments() const;

  void print(outputStream* os);
};

using G1CardSetFreePool = G1SegmentedArrayFreePool;

class G1CardSetMemoryManager : public CHeapObj<mtGCCardSet> {
  G1CardSetConfiguration* _config;

  G1CardSetAllocator* _allocators;

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

  G1SegmentedArrayMemoryStats memory_stats() const;
};

#endif // SHARE_GC_G1_G1CARDSETMEMORY_HPP
