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

#include "precompiled.hpp"

#include "gc/g1/g1CardSetContainers.inline.hpp"
#include "gc/g1/g1CardSetMemory.inline.hpp"
#include "gc/g1/g1SegmentedArray.inline.hpp"
#include "runtime/atomic.hpp"
#include "utilities/ostream.hpp"

G1CardSetAllocator::G1CardSetAllocator(const char* name,
                                       const G1CardSetAllocOptions* alloc_options,
                                       G1CardSetFreeList* free_segment_list) :
  _segmented_array(alloc_options, free_segment_list),
  _free_slots_list(name, &_segmented_array)
{
  uint slot_size = _segmented_array.slot_size();
  assert(slot_size >= sizeof(G1CardSetContainer), "Slot instance size %u for allocator %s too small", slot_size, name);
}

G1CardSetAllocator::~G1CardSetAllocator() {
  drop_all();
}

void G1CardSetAllocator::free(void* slot) {
  assert(slot != nullptr, "precondition");
  _free_slots_list.release(slot);
}

void G1CardSetAllocator::drop_all() {
  _free_slots_list.reset();
  _segmented_array.drop_all();
}

size_t G1CardSetAllocator::mem_size() const {
  return sizeof(*this) +
         _segmented_array.num_segments() * sizeof(G1CardSetSegment) +
         _segmented_array.num_available_slots() * _segmented_array.slot_size();
}

size_t G1CardSetAllocator::wasted_mem_size() const {
  uint num_wasted_slots = _segmented_array.num_available_slots() -
                          _segmented_array.num_allocated_slots() -
                          (uint)_free_slots_list.pending_count();
  return num_wasted_slots * _segmented_array.slot_size();
}

uint G1CardSetAllocator::num_segments() const {
  return _segmented_array.num_segments();
}

void G1CardSetAllocator::print(outputStream* os) {
  uint num_allocated_slots = _segmented_array.num_allocated_slots();
  uint num_available_slots = _segmented_array.num_available_slots();
  uint highest = _segmented_array.first_array_segment() != nullptr
               ? _segmented_array.first_array_segment()->num_slots()
               : 0;
  uint num_segments = _segmented_array.num_segments();
  uint num_pending_slots = (uint)_free_slots_list.pending_count();
  os->print("MA " PTR_FORMAT ": %u slots pending (allocated %u available %u) used %.3f highest %u segments %u size %zu ",
            p2i(this),
            num_pending_slots,
            num_allocated_slots,
            num_available_slots,
            percent_of(num_allocated_slots - num_pending_slots, num_available_slots),
            highest,
            num_segments,
            mem_size());
}

G1CardSetMemoryManager::G1CardSetMemoryManager(G1CardSetConfiguration* config,
                                               G1CardSetFreePool* free_list_pool) : _config(config) {

  _allocators = NEW_C_HEAP_ARRAY(G1CardSetAllocator,
                                 _config->num_mem_object_types(),
                                 mtGC);
  for (uint i = 0; i < num_mem_object_types(); i++) {
    new (&_allocators[i]) G1CardSetAllocator(_config->mem_object_type_name_str(i),
                                             _config->mem_object_alloc_options(i),
                                             free_list_pool->free_list(i));
  }
}

uint G1CardSetMemoryManager::num_mem_object_types() const {
  return _config->num_mem_object_types();
}


G1CardSetMemoryManager::~G1CardSetMemoryManager() {
  for (uint i = 0; i < num_mem_object_types(); i++) {
    _allocators[i].~G1CardSetAllocator();
  }
  FREE_C_HEAP_ARRAY(G1CardSetAllocator<G1CardSetContainer>, _allocators);
}

void G1CardSetMemoryManager::free(uint type, void* value) {
  assert(type < num_mem_object_types(), "must be");
  _allocators[type].free(value);
}

void G1CardSetMemoryManager::flush() {
  for (uint i = 0; i < num_mem_object_types(); i++) {
    _allocators[i].drop_all();
  }
}

void G1CardSetMemoryManager::print(outputStream* os) {
  os->print_cr("MM " PTR_FORMAT " size %zu", p2i(this), sizeof(*this));
  for (uint i = 0; i < num_mem_object_types(); i++) {
    _allocators[i].print(os);
  }
}

size_t G1CardSetMemoryManager::mem_size() const {
  size_t result = 0;
  for (uint i = 0; i < num_mem_object_types(); i++) {
    result += _allocators[i].mem_size();
  }
  return sizeof(*this) + result -
    (sizeof(G1CardSetAllocator) * num_mem_object_types());
}

size_t G1CardSetMemoryManager::wasted_mem_size() const {
  size_t result = 0;
  for (uint i = 0; i < num_mem_object_types(); i++) {
    result += _allocators[i].wasted_mem_size();
  }
  return result;
}

G1SegmentedArrayMemoryStats G1CardSetMemoryManager::memory_stats() const {
  G1SegmentedArrayMemoryStats result;
  for (uint i = 0; i < num_mem_object_types(); i++) {
    result._num_mem_sizes[i] += _allocators[i].mem_size();
    result._num_segments[i] += _allocators[i].num_segments();
  }
  return result;
}
