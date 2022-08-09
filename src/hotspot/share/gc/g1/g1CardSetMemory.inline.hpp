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

#ifndef SHARE_GC_G1_G1CARDSETMEMORY_INLINE_HPP
#define SHARE_GC_G1_G1CARDSETMEMORY_INLINE_HPP

#include "gc/g1/g1CardSetMemory.hpp"
#include "gc/g1/g1CardSetContainers.inline.hpp"
#include "gc/g1/g1SegmentedArray.inline.hpp"
#include "utilities/globalCounter.inline.hpp"
#include "utilities/ostream.hpp"

inline void* G1CardSetAllocator::allocate() {
  void* slot = _free_slots_list.allocate();
  assert(slot != nullptr, "must be");
  return slot;
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

#endif // SHARE_GC_G1_G1CARDSETMEMORY_INLINE_HPP
