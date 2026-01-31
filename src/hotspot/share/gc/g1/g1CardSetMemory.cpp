/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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


#include "gc/g1/g1CardSetContainers.inline.hpp"
#include "gc/g1/g1CardSetMemory.inline.hpp"
#include "gc/g1/g1MonotonicArena.inline.hpp"
#include "utilities/ostream.hpp"

G1CardSetAllocator::G1CardSetAllocator(const char* name,
                                       const G1CardSetAllocOptions* alloc_options,
                                       SegmentFreeList* segment_free_list) :
  _arena(alloc_options, segment_free_list),
  _free_slots_list(name, &_arena)
{
  uint slot_size = _arena.slot_size();
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
  _arena.drop_all();
}

size_t G1CardSetAllocator::mem_size() const {
  return sizeof(*this) +
         num_segments() * sizeof(Segment) +
         _arena.num_total_slots() * _arena.slot_size();
}

size_t G1CardSetAllocator::unused_mem_size() const {
  uint num_unused_slots = (_arena.num_total_slots() - _arena.num_allocated_slots()) +
                          (uint)_free_slots_list.free_count();
  return num_unused_slots * _arena.slot_size();
}

uint G1CardSetAllocator::num_segments() const {
  return _arena.num_segments();
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

size_t G1CardSetMemoryManager::mem_size() const {
  size_t result = 0;
  for (uint i = 0; i < num_mem_object_types(); i++) {
    result += _allocators[i].mem_size();
  }
  return sizeof(*this) + result -
    (sizeof(G1CardSetAllocator) * num_mem_object_types());
}

size_t G1CardSetMemoryManager::unused_mem_size() const {
  size_t result = 0;
  for (uint i = 0; i < num_mem_object_types(); i++) {
    result += _allocators[i].unused_mem_size();
  }
  return result;
}

G1MonotonicArenaMemoryStats G1CardSetMemoryManager::memory_stats() const {
  G1MonotonicArenaMemoryStats result;
  for (uint i = 0; i < num_mem_object_types(); i++) {
    result._num_mem_sizes[i] += _allocators[i].mem_size();
    result._num_segments[i] += _allocators[i].num_segments();
  }
  return result;
}
