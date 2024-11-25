/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/partialArrayState.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/arena.hpp"
#include "nmt/memTag.hpp"
#include "oops/oopsHierarchy.hpp"
#include "runtime/atomic.hpp"
#include "runtime/orderAccess.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include <new>

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
  size_t new_count = Atomic::add(&_refcount, count, memory_order_relaxed);
  assert(new_count >= count, "reference count overflow");
}

class PartialArrayStateAllocator::Impl : public CHeapObj<mtGC> {
  struct FreeListEntry;

  Arena* _arenas;
  FreeListEntry** _free_lists;
  uint _num_workers;

public:
  Impl(uint num_workers);
  ~Impl();

  NONCOPYABLE(Impl);

  PartialArrayState* allocate(uint worker_id,
                              oop src, oop dst,
                              size_t index, size_t length,
                              size_t initial_refcount);
  void release(uint worker_id, PartialArrayState* state);
};

struct PartialArrayStateAllocator::Impl::FreeListEntry {
  FreeListEntry* _next;

  FreeListEntry(FreeListEntry* next) : _next(next) {}
  ~FreeListEntry() = default;

  NONCOPYABLE(FreeListEntry);
};

PartialArrayStateAllocator::Impl::Impl(uint num_workers)
  : _arenas(NEW_C_HEAP_ARRAY(Arena, num_workers, mtGC)),
    _free_lists(NEW_C_HEAP_ARRAY(FreeListEntry*, num_workers, mtGC)),
    _num_workers(num_workers)
{
  for (uint i = 0; i < _num_workers; ++i) {
    ::new (&_arenas[i]) Arena(mtGC);
    _free_lists[i] = nullptr;
  }
}

PartialArrayStateAllocator::Impl::~Impl() {
  // We don't need to clean up the free lists.  Deallocating the entries
  // does nothing, since we're using arena allocation.  Instead, leave it
  // to the arena destructor to release the memory.
  FREE_C_HEAP_ARRAY(FreeListEntry*, _free_lists);
  for (uint i = 0; i < _num_workers; ++i) {
    _arenas[i].~Arena();
  }
  FREE_C_HEAP_ARRAY(Arena*, _arenas);
}

PartialArrayState* PartialArrayStateAllocator::Impl::allocate(uint worker_id,
                                                              oop src, oop dst,
                                                              size_t index,
                                                              size_t length,
                                                              size_t initial_refcount) {
  void* p;
  FreeListEntry* head = _free_lists[worker_id];
  if (head == nullptr) {
    p = NEW_ARENA_OBJ(&_arenas[worker_id], PartialArrayState);
  } else {
    _free_lists[worker_id] = head->_next;
    head->~FreeListEntry();
    p = head;
  }
  return ::new (p) PartialArrayState(src, dst, index, length, initial_refcount);
}

void PartialArrayStateAllocator::Impl::release(uint worker_id, PartialArrayState* state) {
  size_t refcount = Atomic::sub(&state->_refcount, size_t(1), memory_order_release);
  if (refcount != 0) {
    assert(refcount + 1 != 0, "refcount underflow");
  } else {
    OrderAccess::acquire();
    state->~PartialArrayState();
    _free_lists[worker_id] = ::new (state) FreeListEntry(_free_lists[worker_id]);
  }
}

PartialArrayStateAllocator::PartialArrayStateAllocator(uint num_workers)
  : _impl(new Impl(num_workers))
{}

PartialArrayStateAllocator::~PartialArrayStateAllocator() {
  delete _impl;
}

PartialArrayState* PartialArrayStateAllocator::allocate(uint worker_id,
                                                        oop src, oop dst,
                                                        size_t index,
                                                        size_t length,
                                                        size_t initial_refcount) {
  return _impl->allocate(worker_id, src, dst, index, length, initial_refcount);
}

void PartialArrayStateAllocator::release(uint worker_id, PartialArrayState* state) {
  _impl->release(worker_id, state);
}

