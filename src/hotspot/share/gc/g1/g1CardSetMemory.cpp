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

#include "precompiled.hpp"

#include "gc/g1/g1CardSetContainers.inline.hpp"
#include "gc/g1/g1CardSetMemory.inline.hpp"
#include "gc/g1/g1SegmentedArray.inline.hpp"
#include "runtime/atomic.hpp"
#include "utilities/ostream.hpp"


template <class Elem>
G1CardSetAllocator<Elem>::G1CardSetAllocator(const char* name,
                                             const G1CardSetAllocOptions* buffer_options,
                                             G1CardSetBufferList* free_buffer_list) :
  _segmented_array(buffer_options, free_buffer_list),
  _transfer_lock(false),
  _free_nodes_list(),
  _pending_nodes_list(),
  _num_pending_nodes(0),
  _num_free_nodes(0)
{
  uint elem_size = _segmented_array.elem_size();
  assert(elem_size >= sizeof(G1CardSetContainer), "Element instance size %u for allocator %s too small", elem_size, name);
}

template <class Elem>
G1CardSetAllocator<Elem>::~G1CardSetAllocator() {
  drop_all();
}

template <class Elem>
bool G1CardSetAllocator<Elem>::try_transfer_pending() {
  // Attempt to claim the lock.
  if (Atomic::load_acquire(&_transfer_lock) || // Skip CAS if likely to fail.
      Atomic::cmpxchg(&_transfer_lock, false, true)) {
    return false;
  }
  // Have the lock; perform the transfer.

  // Claim all the pending nodes.
  G1CardSetContainer* first = _pending_nodes_list.pop_all();

  if (first != nullptr) {
    // Prepare to add the claimed nodes, and update _num_pending_nodes.
    G1CardSetContainer* last = first;
    Atomic::load_acquire(&_num_pending_nodes);

    uint count = 1;
    for (G1CardSetContainer* next = first->next(); next != nullptr; next = next->next()) {
      last = next;
      ++count;
    }

    Atomic::sub(&_num_pending_nodes, count);

    // Wait for any in-progress pops to avoid ABA for them.
    GlobalCounter::write_synchronize();
    // Add synchronized nodes to _free_node_list.
    // Update count first so there can be no underflow in allocate().
    Atomic::add(&_num_free_nodes, count);
    _free_nodes_list.prepend(*first, *last);
  }
  Atomic::release_store(&_transfer_lock, false);
  return true;
}

template <class Elem>
void G1CardSetAllocator<Elem>::free(Elem* elem) {
  assert(elem != nullptr, "precondition");
  // Desired minimum transfer batch size.  There is relatively little
  // importance to the specific number.  It shouldn't be too big, else
  // we're wasting space when the release rate is low.  If the release
  // rate is high, we might accumulate more than this before being
  // able to start a new transfer, but that's okay.  Also note that
  // the allocation rate and the release rate are going to be fairly
  // similar, due to how the buffers are used. - kbarret
  uint const trigger_transfer = 10;

  uint pending_count = Atomic::add(&_num_pending_nodes, 1u, memory_order_relaxed);

  G1CardSetContainer* node =  reinterpret_cast<G1CardSetContainer*>(reinterpret_cast<char*>(elem));

  node->set_next(nullptr);
  assert(node->next() == nullptr, "precondition");

  _pending_nodes_list.push(*node);

  if (pending_count > trigger_transfer) {
    try_transfer_pending();
  }
}

template <class Elem>
void G1CardSetAllocator<Elem>::drop_all() {
  _free_nodes_list.pop_all();
  _pending_nodes_list.pop_all();
  _num_pending_nodes = 0;
  _num_free_nodes = 0;
  _segmented_array.drop_all();
}

template <class Elem>
void G1CardSetAllocator<Elem>::print(outputStream* os) {
  uint num_allocated_nodes = _segmented_array.num_allocated_nodes();
  uint num_available_nodes = _segmented_array.num_available_nodes();
  uint highest = _segmented_array.first_array_buffer() != nullptr
               ? _segmented_array.first_array_buffer()->num_elems()
               : 0;
  uint num_buffers = _segmented_array.num_buffers();
  os->print("MA " PTR_FORMAT ": %u elems pending (allocated %u available %u) used %.3f highest %u buffers %u size %zu ",
            p2i(this),
            _num_pending_nodes,
            num_allocated_nodes,
            num_available_nodes,
            percent_of(num_allocated_nodes - _num_pending_nodes, num_available_nodes),
            highest,
            num_buffers,
            mem_size());
}

G1CardSetMemoryManager::G1CardSetMemoryManager(G1CardSetConfiguration* config,
                                               G1CardSetFreePool* free_list_pool) : _config(config) {

  _allocators = NEW_C_HEAP_ARRAY(G1CardSetAllocator<G1CardSetContainer>,
                                 _config->num_mem_object_types(),
                                 mtGC);
  for (uint i = 0; i < num_mem_object_types(); i++) {
    new (&_allocators[i]) G1CardSetAllocator<G1CardSetContainer>(_config->mem_object_type_name_str(i),
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
  _allocators[type].free((G1CardSetContainer*)value);
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
  return sizeof(*this) -
    (sizeof(G1CardSetAllocator<G1CardSetContainer>) * num_mem_object_types()) +
    result;
}

size_t G1CardSetMemoryManager::wasted_mem_size() const {
  size_t result = 0;
  for (uint i = 0; i < num_mem_object_types(); i++) {
    result += _allocators[i].wasted_mem_size();
  }
  return result;
}

G1CardSetMemoryStats G1CardSetMemoryManager::memory_stats() const {
  G1CardSetMemoryStats result;
  for (uint i = 0; i < num_mem_object_types(); i++) {
    result._num_mem_sizes[i] += _allocators[i].mem_size();
    result._num_buffers[i] += _allocators[i].num_buffers();
  }
  return result;
}
