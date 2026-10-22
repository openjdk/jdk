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

#include "gc/shared/partialArrayState.hpp"
#include "gc/shared/partialArrayTaskStats.hpp"
#include "gc/shared/partialArrayTaskStepper.hpp"
#include "oops/oop.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

// Each worker uses its own splitter to scan arrays in parallel.
// Queued tasks share an atomic progress record; each claim returns a
// disjoint, self-contained chunk and decrements the record's ref-count.
class PartialArraySplitter {
  PartialArrayStateAllocator _allocator;
  PartialArrayTaskStepper _stepper;
  TASKQUEUE_STATS_ONLY(PartialArrayTaskStats _stats;)

  // The state's ref-count must already include these tasks before publication.
  template<typename Queue>
  void enqueue(Queue* queue, PartialArrayState* state, uint count);

public:
  PartialArraySplitter(PartialArrayStateManager* manager,
                       uint num_workers);
  ~PartialArraySplitter() = default;

  NONCOPYABLE(PartialArraySplitter);

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
  // Decrements the state's ref-count for the current task.  Callers must not
  // use state after this call; it may have been recycled and reused.
  //
  // stolen indicates whether the state task was obtained from this queue or
  // stolen from some other queue.
  template<typename Queue>
  Claim claim(PartialArrayState* state, Queue* queue, bool stolen);

  TASKQUEUE_STATS_ONLY(PartialArrayTaskStats* stats();)
};

#endif // SHARE_GC_SHARED_PARTIALARRAYSPLITTER_HPP
