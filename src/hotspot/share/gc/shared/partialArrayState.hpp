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
#include "memory/arena.hpp"
#include "oops/oopsHierarchy.hpp"
#include "runtime/atomic.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

class PartialArrayStateAllocator;

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
// Each full-sized chunk reserves a reference to the state at allocation,
// including chunks whose tasks have not yet been published.  After a task has
// claimed its chunk, published any continuations, and captured everything needed
// for scanning, decrement the remaining chunk count by one.  The worker that
// decrements it to zero recycles the state on its own allocator for later reuse.
class PartialArrayState {
  objArrayOop _array;
  size_t _length;
  size_t _chunk_size;
  Atomic<size_t> _index;
  Atomic<size_t> _num_remaining_chunks;

  friend class PartialArrayStateAllocator;
  friend class PartialArraySplitter;

  PartialArrayState(objArrayOop array,
                    size_t index, size_t length,
                    size_t chunk_size);

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
// Each per-thread allocator owns an arena for allocating states.  This allows
// the entire set of allocated states to be discarded without the need to keep
// track of or find them under some circumstances.
// For example, if G1 concurrent marking is aborted and needs to restart
// because of a full marking queue, the queue doesn't need to be searched for
// tasks referring to states to allow releasing them.  Instead the queue
// contents can just be discarded, and the memory for the no longer referenced
// states will eventually be reclaimed when the allocator is reset or destroyed.
//
// The allocators each provide a free-list of states.  When a state is
// released and its remaining chunk count has reached zero, it is added to the
// allocator's free-list, for use by future allocation requests.  This causes
// the maximum number of allocated states to be based on the number of
// in-progress arrays, rather than the total number of arrays that need to be
// processed.
//
// A state may be allocated from one allocator's arena and recycled on another
// allocator's free-list after work stealing.  Reclamation follows the owning
// splitter's lifetime contract and is coordinated by the collector.
// Before reclaiming any arena, all workers that can exchange partial-array tasks
// must have stopped using states.  All their allocators must be reset/destroyed
// before processing resumes.  Cleanup does not traverse free-lists, whose
// entries may belong to an already reclaimed arena.
//
// An allocator object is not thread-safe.
class PartialArrayStateAllocator : public CHeapObj<mtGC> {
  friend class PartialArraySplitter;

  class FreeListEntry;

  FreeListEntry* _free_list;
  Arena _arena;

  // Reclaim states and reconstruct the arena.  Called only by the owning
  // splitter after all workers have stopped using states.
  void reset();

public:
  PartialArrayStateAllocator();
  ~PartialArrayStateAllocator() = default;

  NONCOPYABLE(PartialArrayStateAllocator);

  PartialArrayState* allocate(objArrayOop array,
                              size_t index, size_t length,
                              size_t chunk_size);

  // Decrement the state's remaining chunk count by one.  If it reaches zero,
  // recycle on this allocator's free-list.  The caller must have finished
  // accessing the state.
  void release(PartialArrayState* state);
};

#endif // SHARE_GC_SHARED_PARTIALARRAYSTATE_HPP
