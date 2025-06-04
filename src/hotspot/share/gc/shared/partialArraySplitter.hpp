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

#ifndef SHARE_GC_SHARED_PARTIALARRAYSPLITTER_HPP
#define SHARE_GC_SHARED_PARTIALARRAYSPLITTER_HPP

#include "gc/shared/partialArrayState.hpp"
#include "gc/shared/partialArrayTaskStats.hpp"
#include "gc/shared/partialArrayTaskStepper.hpp"
#include "oops/oop.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

class outputStream;

// Helper class for splitting the processing of a large objArray into multiple
// tasks, to permit multiple threads to work on different pieces of the array
// in parallel.
class PartialArraySplitter {
  PartialArrayStateAllocator _allocator;
  PartialArrayTaskStepper _stepper;
  TASKQUEUE_STATS_ONLY(PartialArrayTaskStats _stats;)

public:
  PartialArraySplitter(PartialArrayStateManager* manager,
                       uint num_workers,
                       size_t chunk_size);
  ~PartialArraySplitter() = default;

  NONCOPYABLE(PartialArraySplitter);

  // Setup to process an objArray in chunks.
  //
  // from_array is the array found by the collector that needs processing.  It
  // may be null if to_array contains everything needed for processing.
  //
  // to_array is an unprocessed (possibly partial) copy of from_array, or null
  // if a copy of from_array is not required.
  //
  // length is their length in elements.
  //
  // If t is a ScannerTask, queue->push(t) must be a valid expression.  The
  // result of that expression is ignored.
  //
  // Returns the size of the initial chunk that is to be processed by the
  // caller.
  //
  // Adds PartialArrayState ScannerTasks to the queue if needed to process the
  // array in chunks. This permits other workers to steal and process them
  // even while the caller is processing the initial chunk.  If length doesn't
  // exceed the chunk size then the result will be length, indicating the
  // caller is to process the entire array.  In this case, no tasks will have
  // been added to the queue.
  template<typename Queue>
  size_t start(Queue* queue,
               objArrayOop from_array,
               objArrayOop to_array,
               size_t length);

  // Result type for claim(), carrying multiple values.  Provides the claimed
  // chunk's start and end array indices.
  struct Claim {
    size_t _start;
    size_t _end;
  };

  // Claims a chunk from state, returning the index range for that chunk.  The
  // caller is expected to process that chunk.  Adds more state-based tasks to
  // the queue if needed, permitting other workers to steal and process them
  // even while the caller is processing this claim.
  //
  // Releases the state. Callers must not use state after the call to this
  // function. The state may have been recycled and reused.
  //
  // The queue has the same requirements as for start().
  //
  // stolen indicates whether the state task was obtained from this queue or
  // stolen from some other queue.
  template<typename Queue>
  Claim claim(PartialArrayState* state, Queue* queue, bool stolen);

  TASKQUEUE_STATS_ONLY(PartialArrayTaskStats* stats();)
};

#endif // SHARE_GC_SHARED_PARTIALARRAYSPLITTER_HPP
