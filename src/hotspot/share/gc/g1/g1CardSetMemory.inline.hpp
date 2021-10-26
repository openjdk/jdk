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

#ifndef SHARE_GC_G1_G1CARDSETMEMORY_INLINE_HPP
#define SHARE_GC_G1_G1CARDSETMEMORY_INLINE_HPP

#include "gc/g1/g1CardSetMemory.hpp"
#include "gc/g1/g1CardSetContainers.hpp"
#include "gc/g1/g1SegmentedArray.inline.hpp"
#include "utilities/ostream.hpp"

#include "gc/g1/g1CardSetContainers.inline.hpp"
#include "utilities/globalCounter.inline.hpp"

template <class Elem>
G1CardSetContainer* volatile* G1CardSetAllocator<Elem>::next_ptr(G1CardSetContainer& node) {
  return node.next_addr();
}

template <class Elem>
Elem* G1CardSetAllocator<Elem>::allocate() {
  assert(_segmented_array.elem_size() > 0, "instance size not set.");

  if (num_free_elems() > 0) {
    // Pop under critical section to deal with ABA problem
    // Other solutions to the same problem are more complicated (ref counting, HP)
    GlobalCounter::CriticalSection cs(Thread::current());

    G1CardSetContainer* node = _free_nodes_list.pop();
    if (node != nullptr) {
      Elem* elem = reinterpret_cast<Elem*>(reinterpret_cast<char*>(node));
      Atomic::sub(&_num_free_nodes, 1u);
      guarantee(is_aligned(elem, 8), "result " PTR_FORMAT " not aligned", p2i(elem));
      return elem;
    }
  }

  Elem* elem = _segmented_array.allocate();
  assert(elem != nullptr, "must be");
  return elem;
}

inline uint8_t* G1CardSetMemoryManager::allocate(uint type) {
  assert(type < num_mem_object_types(), "must be");
  return (uint8_t*)_allocators[type].allocate();
}

inline uint8_t* G1CardSetMemoryManager::allocate_node() {
  return allocate(0);
}

inline void G1CardSetMemoryManager::free_node(void* value) {
  free(0, value);
}

template <class Elem>
inline uint G1CardSetAllocator<Elem>::num_free_elems() const {
  return Atomic::load(&_num_free_nodes);
}

#endif // SHARE_GC_G1_G1CARDSETMEMORY_INLINE_HPP
