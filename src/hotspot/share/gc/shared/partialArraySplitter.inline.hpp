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
#include "gc/shared/taskqueue.inline.hpp"
#include "oops/oop.hpp"
#include "utilities/checkedCast.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

inline uint PartialArraySplitter::continuation_tasks(size_t start, size_t length, size_t chunk_size) const {
  precond(chunk_size > 0);
  assert(start < length, "invariant: start %zu, length %zu", start, length);
  assert(((length - start) % chunk_size) == 0,
         "invariant: start %zu, length %zu, chunk size %zu",
         start, length, chunk_size);

  // Determine the number of new tasks to create.
  // Zero-based index for this partial task.  The initial task isn't counted.
  uint task_num = checked_cast<uint>(start / chunk_size);
  // Number of tasks left to process, including this one.
  uint remaining_tasks = checked_cast<uint>((length - start) / chunk_size);
  assert(remaining_tasks > 0, "invariant");
  // Compute number of pending tasks, including this one.  The maximum number
  // of tasks is a function of task_num (N) and _task_fanout (F).
  //   1    : current task
  //   N    : number of preceding tasks
  //   F*N  : maximum created for preceding tasks
  // => F*N - N + 1 : maximum number of tasks
  // => (F-1)*N + 1
  assert(_task_limit > 0, "precondition");
  assert(_task_fanout > 0, "precondition");
  uint64_t max_pending = (uint64_t)(_task_fanout - 1) * task_num + 1;

  // The actual pending may be less than that.  Bound by remaining_tasks to
  // not overrun.  Also bound by _task_limit to avoid spawning an excessive
  // number of tasks for a large array.  The +1 is to replace the current
  // task with a new task when _task_limit limited.  The pending value may
  // not be what's actually in the queues, because of concurrent task
  // processing.  That's okay; we just need to determine the correct number
  // of tasks to add for this task.
  uint pending = (uint)MIN3<uint64_t>(max_pending, remaining_tasks, _task_limit);
  uint ncreate = MIN2(_task_fanout, MIN2(remaining_tasks, _task_limit + 1) - pending);
  return ncreate;
}

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
    PartialArrayState* state = allocate_state(array, end, length, chunk_size);
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
  uint count = continuation_tasks(start, state->_length, chunk_size);
  if (count > 0) {
    enqueue(queue, state, count);
  }
  Claim result{state->array(), start, start + chunk_size};
  // Capture everything needed for scanning before decrementing the state's
  // remaining chunk count.
  release_state(state);
  return result;
}

#endif // SHARE_GC_SHARED_PARTIALARRAYSPLITTER_INLINE_HPP
