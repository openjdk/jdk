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

#ifndef SHARE_GC_SHARED_PARTIALARRAYSPLITTER_HPP
#define SHARE_GC_SHARED_PARTIALARRAYSPLITTER_HPP

#include "gc/shared/partialArrayTaskStats.hpp"
#include "memory/arena.hpp"
#include "oops/oop.hpp"
#include "oops/oopsHierarchy.hpp"
#include "runtime/atomic.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

// Partial array processing.
//
// Marking or evacuating a large object array on one worker can dominate a
// collection.  Split the array into chunks that any worker can claim, so the
// array is processed in parallel.  A claim yields a self-contained range that
// is scanned without holding on to shared bookkeeping, and workers can steal
// tasks from each other.
//
// Implementation
//
// A PartialArrayState is the progress record for one split array: the next
// index to claim and the number of chunks outstanding.  ScannerTasks refer to
// it, so any worker can steal one, claim a disjoint chunk atomically, and
// publish continuation tasks.
//
// Each splitter allocates states from its own arena, so discarding the queue
// contents of an aborted collection also frees the states they referred to.  A
// state whose last chunk has been claimed is recycled on the releasing worker's
// free-list, which may belong to a different splitter.  Free-lists are not
// traversable during cleanup, so splitters must be reset or destroyed only while
// no worker is using states.

// Progress record for one split array, shared by the tasks claiming its chunks.
// The splitter that allocates a state also owns its storage.
class PartialArrayState {
  objArrayOop _array;
  size_t _length;
  size_t _chunk_size;
  Atomic<size_t> _index;
  Atomic<size_t> _num_remaining_chunks;

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
  // Deleted; states are managed by the allocating splitter.
  ~PartialArrayState() = delete;

  NONCOPYABLE(PartialArrayState);

  objArrayOop array() const { return _array; }
};

// Per-worker partial array processing.  Claims chunks, publishes continuation
// tasks, and owns the arena and free-list for the states it allocates.  Not
// thread-safe; each worker uses its own splitter.
class PartialArraySplitter {
  friend class PartialArraySplitterTestAccess;

  // Overlay for a state on this splitter's free-list.  Written only when the
  // state's remaining chunk count reaches zero, i.e. once no task can still
  // refer to it.
  class FreeListEntry;

  FreeListEntry* _free_list;
  Arena _arena;

  // Limit on pending tasks for one array.
  uint _task_limit;
  // Maximum number of new tasks to create when processing an existing task.
  uint _task_fanout;

  TASKQUEUE_STATS_ONLY(PartialArrayTaskStats _stats;)

  // Allocate the state for array[index, length), reusing a free-list entry if
  // available.
  PartialArrayState* allocate_state(objArrayOop array,
                                    size_t index, size_t length,
                                    size_t chunk_size);

  // Drop one reference to the state, recycling it if it was the last one.  The
  // caller must have finished accessing the state.
  void release_state(PartialArrayState* state);

  // Number of continuation tasks to publish after claiming the chunk starting
  // at start.  Every claim must publish exactly this many, even if other claims
  // have advanced the shared index in the meantime.
  uint continuation_tasks(size_t start, size_t length, size_t chunk_size) const;

  // References for all chunks are reserved when the state is allocated.
  template<typename Queue>
  void enqueue(Queue* queue, PartialArrayState* state, uint count);

public:
  explicit PartialArraySplitter(uint num_workers);
  ~PartialArraySplitter() = default;

  NONCOPYABLE(PartialArraySplitter);

  // Reclaim states and reset statistics.  Requires the worker quiescence and
  // coordinated cleanup described above.
  void reset();

  // A chunk remains valid after its progress record has been recycled.  It
  // does not own or keep the array alive; that is the collector's responsibility.
  struct Claim {
    objArrayOop _array;
    size_t _start;
    size_t _end;
  };

  // Start processing array[0, length).  Return the initial chunk and enqueue
  // a continuation if any full-sized chunks remain.  Process the remainder
  // first so every queued task claims exactly chunk_size elements.
  template<typename Queue>
  Claim start(Queue* queue,
              objArrayOop array,
              size_t length,
              size_t chunk_size);

  // Claims a chunk from state, returning the index range for that chunk.  The
  // caller is expected to process that chunk.  Adds more state-based tasks to
  // the queue if needed, permitting other workers to steal and process them
  // even while the caller is processing this claim.
  //
  // Callers must not use state after this call; it may have been recycled and
  // reused.
  //
  // stolen indicates whether the state task was obtained from this queue or
  // stolen from some other queue.
  template<typename Queue>
  Claim claim(PartialArrayState* state, Queue* queue, bool stolen);

  TASKQUEUE_STATS_ONLY(PartialArrayTaskStats* stats();)
};

#endif // SHARE_GC_SHARED_PARTIALARRAYSPLITTER_HPP
