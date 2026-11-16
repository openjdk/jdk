/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "cppstdlib/new.hpp"
#include "gc/shared/partialArrayState.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/arena.hpp"
#include "nmt/memTag.hpp"
#include "oops/oopsHierarchy.hpp"
#include "runtime/orderAccess.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

PartialArrayState::PartialArrayState(objArrayOop array,
                                     size_t index, size_t length,
                                     size_t chunk_size)
  : _array(array),
    _length(length),
    _chunk_size(chunk_size),
    _index(index),
    _num_remaining_chunks((length - index) / chunk_size)
{
  assert(index < length, "precondition");
  assert(chunk_size > 0, "precondition");
  assert((length - index) % chunk_size == 0, "whole chunks remain");
}

class PartialArrayStateAllocator::FreeListEntry {
public:
  FreeListEntry* _next;

  FreeListEntry(FreeListEntry* next) : _next(next) {}
  ~FreeListEntry() = default;

  NONCOPYABLE(FreeListEntry);
};

PartialArrayStateAllocator::PartialArrayStateAllocator()
  : _free_list(),
    _arena(mtGC)
{}

void PartialArrayStateAllocator::reset() {
  _free_list = nullptr;
  // Restore the arena to its freshly constructed state, i.e. destructor-then-constructor.
  _arena.~Arena();
  ::new (&_arena) Arena(mtGC);
}

PartialArrayState* PartialArrayStateAllocator::allocate(objArrayOop array,
                                                        size_t index,
                                                        size_t length,
                                                        size_t chunk_size) {
  void* p;
  FreeListEntry* head = _free_list;
  if (head == nullptr) {
    p = NEW_ARENA_OBJ(&_arena, PartialArrayState);
  } else {
    _free_list = head->_next;
    head->~FreeListEntry();
    p = head;
  }
  return ::new (p) PartialArrayState(array, index, length, chunk_size);
}

void PartialArrayStateAllocator::release(PartialArrayState* state) {
  size_t num_remaining_chunks = state->_num_remaining_chunks.sub_then_fetch(1u, memory_order_release);
  if (num_remaining_chunks != 0) {
    assert(num_remaining_chunks + 1 != 0, "remaining chunk count underflow");
  } else {
    OrderAccess::acquire();
    // Don't need to call destructor; can't if not destructible.
    static_assert(!std::is_destructible<PartialArrayState>::value);
    _free_list = ::new (state) FreeListEntry(_free_list);
  }
}
