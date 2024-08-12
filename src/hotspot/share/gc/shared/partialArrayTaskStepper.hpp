/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_PARTIALARRAYTASKSTEPPER_HPP
#define SHARE_GC_SHARED_PARTIALARRAYTASKSTEPPER_HPP

#include "oops/arrayOop.hpp"
#include "utilities/globalDefinitions.hpp"

class PartialArrayState;

// Helper for partial array chunking tasks.
//
// When an array is large, we want to split it up into chunks that can be
// processed in parallel.  Each task (implicitly) represents such a chunk.  We
// can enqueue multiple tasks at the same time.  We want to enqueue enough
// tasks to benefit from the available parallelism, while not so many as to
// substantially expand the task queues.
class PartialArrayTaskStepper {
public:
  PartialArrayTaskStepper(uint n_workers, size_t chunk_size);

  struct Step {
    size_t _index;              // Array index for the step.
    uint _ncreate;              // Number of new tasks to create.
  };

  // Called with the length of the array to be processed.  Returns a Step with
  // _index being the end of the initial chunk, which the caller should
  // process.  This is also the starting index for the next chunk to process.
  // The _ncreate is the number of tasks to enqueue to continue processing the
  // array.  If _ncreate is zero then _index will be length.
  inline Step start(size_t length) const;

  // Atomically increment state's index by chunk_size() to claim the next
  // chunk.  Returns a Step with _index being the starting index of the
  // claimed chunk and _ncreate being the number of additional partial tasks
  // to enqueue.
  inline Step next(PartialArrayState* state) const;

  // The size of chunks to claim for each task.
  inline size_t chunk_size() const;

  class TestSupport;            // For unit tests

private:
  // Size (number of elements) of a chunk to process.
  size_t _chunk_size;
  // Limit on the number of partial array tasks to create for a given array.
  uint _task_limit;
  // Maximum number of new tasks to create when processing an existing task.
  uint _task_fanout;

  // For unit tests.
  inline Step next_impl(size_t length, volatile size_t* index_addr) const;
};

#endif // SHARE_GC_SHARED_PARTIALARRAYTASKSTEPPER_HPP
