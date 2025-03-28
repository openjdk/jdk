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
size_t PartialArraySplitter::start(Queue* queue,
                                   objArrayOop source,
                                   objArrayOop destination,
                                   size_t length) {
  PartialArrayTaskStepper::Step step = _stepper.start(length);
  // Push initial partial scan tasks.
  if (step._ncreate > 0) {
    TASKQUEUE_STATS_ONLY(_stats.inc_split(););
    TASKQUEUE_STATS_ONLY(_stats.inc_pushed(step._ncreate);)
    PartialArrayState* state =
      _allocator.allocate(source, destination, step._index, length, step._ncreate);
    for (uint i = 0; i < step._ncreate; ++i) {
      queue->push(ScannerTask(state));
    }
  } else {
    assert(step._index == length, "invariant");
  }
  return step._index;
}

template<typename Queue>
PartialArraySplitter::Claim
PartialArraySplitter::claim(PartialArrayState* state, Queue* queue, bool stolen) {
#if TASKQUEUE_STATS
  if (stolen) _stats.inc_stolen();
  _stats.inc_processed();
#endif // TASKQUEUE_STATS

  // Claim a chunk and get number of additional tasks to enqueue.
  PartialArrayTaskStepper::Step step = _stepper.next(state);
  // Push additional tasks.
  if (step._ncreate > 0) {
    TASKQUEUE_STATS_ONLY(_stats.inc_pushed(step._ncreate);)
    // Adjust reference count for tasks being added to the queue.
    state->add_references(step._ncreate);
    for (uint i = 0; i < step._ncreate; ++i) {
      queue->push(ScannerTask(state));
    }
  }
  // Release state, decrementing refcount, now that we're done with it.
  _allocator.release(state);
  return Claim{step._index, step._index + _stepper.chunk_size()};
}

#endif // SHARE_GC_SHARED_PARTIALARRAYSPLITTER_INLINE_HPP
