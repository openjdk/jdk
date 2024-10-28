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

#ifndef SHARE_GC_SHARED_PARTIALARRAYSTATE_HPP
#define SHARE_GC_SHARED_PARTIALARRAYSTATE_HPP

#include "memory/allocation.hpp"
#include "oops/oopsHierarchy.hpp"
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
// Processing a state involves using the state to claim a segment of the
// array, and processing that segment.  Claiming is done by atomically
// incrementing the index, thereby claiming the segment from the old to new
// index values.  New tasks should also be added as needed to ensure the
// entire array will be processed.  A PartialArrayTaskStepper can be used to
// help with this.
//
// States are allocated and released using a PartialArrayStateAllocator.
// States are reference counted to aid in that management.  Each task
// referring to a given state that is added to a taskqueue must increase the
// reference count by one.  When the processing of a task referring to a state
// is complete, the reference count must be decreased by one.  When the
// reference count reaches zero the state should be released to the allocator
// for later reuse.
class PartialArrayState {
  oop _source;
  oop _destination;
  size_t _length;
  volatile size_t _index;
  volatile size_t _refcount;

  friend class PartialArrayStateAllocator;

  PartialArrayState(oop src, oop dst,
                    size_t index, size_t length,
                    size_t initial_refcount);
  ~PartialArrayState() = default;

  NONCOPYABLE(PartialArrayState);

public:
  // Add count references, one per referring task being added to a taskqueue.
  void add_references(size_t count);

  // The source array oop.
  oop source() const { return _source; }

  // The destination array oop.  In some circumstances the source and
  // destination may be the same.
  oop destination() const { return _destination; }

  // The length of the array oop.
  size_t length() const { return _length; }

  // A pointer to the start index for the next segment to process, for atomic
  // update.
  volatile size_t* index_addr() { return &_index; }
};

// This class provides memory management for PartialArrayStates.
//
// States are initially allocated from a set of arenas owned by the allocator.
// This allows the entire set of allocated states to be discarded without the
// need to keep track of or find them under some circumstances.  For example,
// if G1 concurrent marking is aborted and needs to restart because of a full
// marking queue, the queue doesn't need to be searched for tasks referring to
// states to allow releasing them.  Instead the queue contents can just be
// discarded, and the memory for the no longer referenced states will
// eventually be reclaimed when the arenas are reset.
//
// A set of free-lists is placed in front of the arena allocators.  This
// causes the maximum number of allocated states to be based on the number of
// in-progress arrays, rather than the total number of arrays that need to be
// processed.  The use of free-list allocators is the reason for reference
// counting states.
//
// The arena and free-list to use for an allocation operation is designated by
// the worker_id used in the operation.  This avoids locking and such on those
// data structures, at the cost of possibly doing more total arena allocation
// that would be needed with a single shared arena and free-list.
class PartialArrayStateAllocator : public CHeapObj<mtGC> {
  class Impl;
  Impl* _impl;

public:
  PartialArrayStateAllocator(uint num_workers);
  ~PartialArrayStateAllocator();

  NONCOPYABLE(PartialArrayStateAllocator);

  // Create a new state, obtaining the memory for it from the free-list or
  // arena associated with worker_id.
  PartialArrayState* allocate(uint worker_id,
                              oop src, oop dst,
                              size_t index, size_t length,
                              size_t initial_refcount);

  // Decrement the state's refcount.  If the new refcount is zero, add the
  // state to the free-list associated with worker_id.  The state must have
  // been allocated by this allocator, but that allocation doesn't need to
  // have been associated with worker_id.
  void release(uint worker_id, PartialArrayState* state);
};

#endif // SHARE_GC_SHARED_PARTIALARRAYSTATE_HPP
