/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_PARTIALARRAYTASKSTEPPER_INLINE_HPP
#define SHARE_GC_SHARED_PARTIALARRAYTASKSTEPPER_INLINE_HPP

#include "gc/shared/partialArrayTaskStepper.hpp"

#include "gc/shared/partialArrayState.hpp"
#include "utilities/checkedCast.hpp"
#include "utilities/debug.hpp"

size_t PartialArrayTaskStepper::chunk_size() const {
  return _chunk_size;
}

PartialArrayTaskStepper::Step
PartialArrayTaskStepper::start(size_t length) const {
  size_t end = length % _chunk_size; // End of initial chunk.
  // If the initial chunk is the complete array, then don't need any partial
  // tasks.  Otherwise, start with just one partial task; see new task
  // calculation in next().
  return Step{ end, (length > end) ? 1u : 0u };
}

PartialArrayTaskStepper::Step
PartialArrayTaskStepper::next_impl(size_t length, Atomic<size_t>* index_addr) const {
  // The start of the next task is in the state's index.
  // Atomically increment by the chunk size to claim the associated chunk.
  // Because we limit the number of enqueued tasks to being no more than the
  // number of remaining chunks to process, we can use an atomic add for the
  // claim, rather than a CAS loop.
  size_t start = index_addr->fetch_then_add(_chunk_size, memory_order_relaxed);

  assert(start < length, "invariant: start %zu, length %zu", start, length);
  assert(((length - start) % _chunk_size) == 0,
         "invariant: start %zu, length %zu, chunk size %zu",
         start, length, _chunk_size);

  // Determine the number of new tasks to create.
  // Zero-based index for this partial task.  The initial task isn't counted.
  uint task_num = checked_cast<uint>(start / _chunk_size);
  // Number of tasks left to process, including this one.
  uint remaining_tasks = checked_cast<uint>((length - start) / _chunk_size);
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
  uint max_pending = (_task_fanout - 1) * task_num + 1;

  // The actual pending may be less than that.  Bound by remaining_tasks to
  // not overrun.  Also bound by _task_limit to avoid spawning an excessive
  // number of tasks for a large array.  The +1 is to replace the current
  // task with a new task when _task_limit limited.  The pending value may
  // not be what's actually in the queues, because of concurrent task
  // processing.  That's okay; we just need to determine the correct number
  // of tasks to add for this task.
  uint pending = MIN3(max_pending, remaining_tasks, _task_limit);
  uint ncreate = MIN2(_task_fanout, MIN2(remaining_tasks, _task_limit + 1) - pending);
  return Step{ start, ncreate };
}

PartialArrayTaskStepper::Step
PartialArrayTaskStepper::next(PartialArrayState* state) const {
  return next_impl(state->length(), state->index_addr());
}

#endif // SHARE_GC_SHARED_PARTIALARRAYTASKSTEPPER_INLINE_HPP
