/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

PartialArrayState::PartialArrayState(oop src, oop dst,
                                     size_t index, size_t length,
                                     size_t initial_refcount)
  : _source(src),
    _destination(dst),
    _length(length),
    _index(index),
    _refcount(initial_refcount)
{
  assert(index <= length, "precondition");
}

void PartialArrayState::add_references(size_t count) {
  size_t new_count = _refcount.add_then_fetch(count, memory_order_relaxed);
  assert(new_count >= count, "reference count overflow");
}

class PartialArrayStateAllocator::FreeListEntry {
public:
  FreeListEntry* _next;

  FreeListEntry(FreeListEntry* next) : _next(next) {}
  ~FreeListEntry() = default;

  NONCOPYABLE(FreeListEntry);
};

PartialArrayStateAllocator::PartialArrayStateAllocator(PartialArrayStateManager* manager)
  : _manager(manager),
    _free_list(),
    _arena(manager->register_allocator())
{}

PartialArrayStateAllocator::~PartialArrayStateAllocator() {
  // We don't need to clean up the free list.  Deallocating the entries
  // does nothing, since we're using arena allocation.  Instead, leave it
  // to the manager to release the memory.
  // Inform the manager that an allocator is no longer in use.
  _manager->release_allocator();
}

PartialArrayState* PartialArrayStateAllocator::allocate(oop src, oop dst,
                                                        size_t index,
                                                        size_t length,
                                                        size_t initial_refcount) {
  void* p;
  FreeListEntry* head = _free_list;
  if (head == nullptr) {
    p = NEW_ARENA_OBJ(_arena, PartialArrayState);
  } else {
    _free_list = head->_next;
    head->~FreeListEntry();
    p = head;
  }
  return ::new (p) PartialArrayState(src, dst, index, length, initial_refcount);
}

void PartialArrayStateAllocator::release(PartialArrayState* state) {
  size_t refcount = state->_refcount.sub_then_fetch(1u, memory_order_release);
  if (refcount != 0) {
    assert(refcount + 1 != 0, "refcount underflow");
  } else {
    OrderAccess::acquire();
    // Don't need to call destructor; can't if not destructible.
    static_assert(!std::is_destructible<PartialArrayState>::value, "expected");
    _free_list = ::new (state) FreeListEntry(_free_list);
  }
}

PartialArrayStateManager::PartialArrayStateManager(uint max_allocators)
  : _arenas(NEW_C_HEAP_ARRAY(Arena, max_allocators, mtGC)),
    _max_allocators(max_allocators),
    _registered_allocators(0)
    DEBUG_ONLY(COMMA _released_allocators(0))
{}

PartialArrayStateManager::~PartialArrayStateManager() {
  reset();
  FREE_C_HEAP_ARRAY(Arena, _arenas);
}

Arena* PartialArrayStateManager::register_allocator() {
  uint idx = _registered_allocators.fetch_then_add(1u, memory_order_relaxed);
  assert(idx < _max_allocators, "exceeded configured max number of allocators");
  return ::new (&_arenas[idx]) Arena(mtGC);
}

#ifdef ASSERT
void PartialArrayStateManager::release_allocator() {
  uint old = _released_allocators.fetch_then_add(1u, memory_order_relaxed);
  assert(old < _registered_allocators.load_relaxed(), "too many releases");
}
#endif // ASSERT

void PartialArrayStateManager::reset() {
  uint count = _registered_allocators.load_relaxed();
  assert(count == _released_allocators.load_relaxed(),
         "some allocators still active");
  for (uint i = 0; i < count; ++i) {
    _arenas[i].~Arena();
  }
  _registered_allocators.store_relaxed(0u);
  DEBUG_ONLY(_released_allocators.store_relaxed(0u);)
}
