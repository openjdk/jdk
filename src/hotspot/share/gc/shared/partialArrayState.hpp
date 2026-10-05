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

#ifndef SHARE_GC_SHARED_PARTIALARRAYSTATE_HPP
#define SHARE_GC_SHARED_PARTIALARRAYSTATE_HPP

#include "memory/allocation.hpp"
#include "oops/oopsHierarchy.hpp"
#include "runtime/atomic.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

class Arena;
class PartialArrayStateAllocator;
class PartialArrayStateManager;

// Instances of this class are used to represent processing progress for an
// array task in a taskqueue.  When a sufficiently large array needs to be
// processed, such that it is desirable to split up the processing into
// parallelizable subtasks, a state object is allocated for the array.
// Multiple tasks referring to the state can then be added to the taskqueue
// for later processing, either by the current thread or by some other thread
// that steals one of those tasks.
//
// PartialArraySplitter atomically claims disjoint chunks and publishes enough
// continuation tasks for parallel processing.  The state is only needed while
// claiming a chunk, not while scanning it.
//
// States are allocated and released using a PartialArrayStateAllocator.
// Each queued or currently claiming task contributes one to the state's
// ref-count.  Increment the ref-count before publishing continuation tasks.
// After a task has claimed its chunk and published any continuations, decrement
// the ref-count by one.  The worker that decrements it to zero recycles the
// state on its own allocator for later reuse.
class PartialArrayState {
  objArrayOop _array;
  size_t _length;
  size_t _chunk_size;
  Atomic<size_t> _index;
  Atomic<size_t> _refcount;

  friend class PartialArrayStateAllocator;
  friend class PartialArraySplitter;

  PartialArrayState(objArrayOop array,
                    size_t index, size_t length,
                    size_t chunk_size, size_t initial_refcount);

  // Increment the ref-count by count before publishing that many tasks to a queue.
  void add_references(size_t count);

  size_t claim_next() {
    size_t start = _index.fetch_then_add(_chunk_size, memory_order_relaxed);
    assert(start < _length, "every task must have a chunk to claim");
    assert((_length - start) % _chunk_size == 0, "whole chunks remain");
    return start;
  }

public:
  // Deleted to require management by allocator object.
  ~PartialArrayState() = delete;

  NONCOPYABLE(PartialArrayState);

  objArrayOop array() const { return _array; }
};

// This class provides memory management for PartialArrayStates.
//
// States are initially arena allocated from the manager, using a per-thread
// allocator.  This allows the entire set of allocated states to be discarded
// without the need to keep track of or find them under some circumstances.
// For example, if G1 concurrent marking is aborted and needs to restart
// because of a full marking queue, the queue doesn't need to be searched for
// tasks referring to states to allow releasing them.  Instead the queue
// contents can just be discarded, and the memory for the no longer referenced
// states will eventually be reclaimed when the arena is reset.
//
// The allocators each provide a free-list of states.  When a state is
// released and its reference count has reached zero, it is added to the
// allocator's free-list, for use by future allocation requests.  This causes
// the maximum number of allocated states to be based on the number of
// in-progress arrays, rather than the total number of arrays that need to be
// processed.
//
// An allocator object is not thread-safe.
class PartialArrayStateAllocator : public CHeapObj<mtGC> {
  class FreeListEntry;

  PartialArrayStateManager* _manager;
  FreeListEntry* _free_list;
  Arena* _arena;

public:
  explicit PartialArrayStateAllocator(PartialArrayStateManager* manager);
  ~PartialArrayStateAllocator();

  NONCOPYABLE(PartialArrayStateAllocator);

  PartialArrayState* allocate(objArrayOop array,
                              size_t index, size_t length,
                              size_t chunk_size, size_t initial_refcount);

  // Decrement the state's ref-count by one.  If it reaches zero, recycle on
  // this allocator's free-list.  The state must belong to the same manager.
  void release(PartialArrayState* state);
};

// This class provides memory management for PartialArrayStates.
//
// States are allocated using an allocator object. Those allocators in turn
// may request memory for a state from their associated manager. The manager
// is responsible for obtaining and releasing memory used for states by the
// associated allocators.
//
// A state may be allocated by one allocator, but end up on the free-list of a
// different allocator.  This can happen because a task referring to the state
// may be stolen from the queue where it was initially added.  This is permitted
// because a state's memory won't be reclaimed until all of the allocators
// associated with the manager that is ultimately providing the memory have
// been deleted and the manager is reset.
//
// A manager is used in two distinct and non-overlapping phases.
//
// - allocating: This is the initial phase.  During this phase, new allocators
// may be created, and allocators may request memory from the manager.
//
// - releasing: When an allocator is destroyed the manager transitions to this
// phase.  It remains in this phase until all extent allocators associated with
// this manager have been destroyed.  During this phase, new allocators may not
// be created, nor may extent allocators request memory from this manager.
//
// Once all the associated allocators have been destroyed the releasing phase
// ends and the manager may be reset or deleted.  Resetting transitions back
// to the allocating phase.
class PartialArrayStateManager : public CHeapObj<mtGC> {
  friend class PartialArrayStateAllocator;

  // Use an arena for each allocator, for thread-safe concurrent allocation by
  // different allocators.
  Arena* _arenas;

  // Limit on the number of allocators this manager supports.
  uint _max_allocators;

  // The number of allocators that have been registered/released.
  // Atomic to support concurrent registration, and concurrent release.
  // Phasing restriction forbids registration concurrent with release.
  Atomic<uint> _registered_allocators;
  DEBUG_ONLY(Atomic<uint> _released_allocators;)

  // These are all for sole use of the befriended allocator class.
  Arena* register_allocator();
  void release_allocator() NOT_DEBUG_RETURN;

public:
  explicit PartialArrayStateManager(uint max_allocators);

  // Release the memory that has been requested by allocators associated with
  // this manager.
  // precondition: all associated allocators have been deleted.
  ~PartialArrayStateManager();

  NONCOPYABLE(PartialArrayStateManager);

  // Recycle the memory that has been requested by allocators associated with
  // this manager.
  // precondition: all associated allocators have been deleted.
  void reset();
};

#endif // SHARE_GC_SHARED_PARTIALARRAYSTATE_HPP
