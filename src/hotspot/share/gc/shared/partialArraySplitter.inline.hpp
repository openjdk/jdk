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

#ifndef SHARE_GC_SHARED_PARTIALARRAYSPLITTER_INLINE_HPP
#define SHARE_GC_SHARED_PARTIALARRAYSPLITTER_INLINE_HPP

#include "gc/shared/partialArraySplitter.hpp"

#include "gc/shared/partialArrayTaskStats.hpp"
#include "gc/shared/partialArrayTaskStepper.inline.hpp"
#include "gc/shared/taskqueue.inline.hpp"
#include "oops/oop.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

template<typename Queue>
void PartialArraySplitter::enqueue(Queue* queue, PartialArrayState* state, uint count) {
  TASKQUEUE_STATS_ONLY(_stats.inc_pushed(count);)
  for (uint i = 0; i < count; ++i) {
    queue->push(ScannerTask(state));
  }
}

template<typename Queue>
PartialArraySplitter::Claim
PartialArraySplitter::start(Queue* queue,
                            objArrayOop array,
                            size_t length,
                            size_t chunk_size) {
  precond(chunk_size > 0);
  size_t end = length % chunk_size;
  if (end < length) {
    TASKQUEUE_STATS_ONLY(_stats.inc_split();)
    PartialArrayState* state =
      _allocator.allocate(array, end, length, chunk_size, 1);
    enqueue(queue, state, 1);
  }
  return Claim{array, 0, end};
}

template<typename Queue>
PartialArraySplitter::Claim
PartialArraySplitter::claim(PartialArrayState* state, Queue* queue, bool stolen) {
#if TASKQUEUE_STATS
  if (stolen) _stats.inc_stolen();
  _stats.inc_processed();
#endif // TASKQUEUE_STATS

  size_t start = state->claim_next();
  size_t chunk_size = state->_chunk_size;
  uint count = _stepper.continuation_tasks(start, state->_length, chunk_size);
  if (count > 0) {
    // Increment the ref-count for all new tasks before publication.  A thief
    // may immediately claim and release any task we push.
    state->add_references(count);
    enqueue(queue, state, count);
  }
  Claim result{state->array(), start, start + chunk_size};
  // Capture everything needed for scanning before decrementing the state's
  // ref-count.
  _allocator.release(state);
  return result;
}

#endif // SHARE_GC_SHARED_PARTIALARRAYSPLITTER_INLINE_HPP
