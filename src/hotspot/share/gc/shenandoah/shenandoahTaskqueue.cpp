/*
 * Copyright (c) 2016, 2021, Red Hat, Inc. All rights reserved.
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


#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahTaskqueue.inline.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/resourceArea.hpp"
#include "utilities/stack.inline.hpp"

void ShenandoahObjToScanQueueSet::clear() {
  uint size = GenericTaskQueueSet<ShenandoahObjToScanQueue, mtGC>::size();
  for (uint index = 0; index < size; index ++) {
    ShenandoahObjToScanQueue* q = queue(index);
    assert(q != nullptr, "Sanity");
    q->clear();
  }
}

bool ShenandoahObjToScanQueueSet::is_empty() {
  uint size = GenericTaskQueueSet<ShenandoahObjToScanQueue, mtGC>::size();
  for (uint index = 0; index < size; index ++) {
    ShenandoahObjToScanQueue* q = queue(index);
    assert(q != nullptr, "Sanity");
    if (!q->is_empty()) {
      return false;
    }
  }
  return true;
}

void ShenandoahObjToScanQueueSet::rebalance(size_t target_queues) {
  assert(target_queues > 0 && target_queues <= size(), "Should be in bounds: %zu", target_queues);

  // Figure out the population target.
  size_t total = 0;
  for (uint i = 0; i < GenericTaskQueueSet::size(); i++) {
    ShenandoahObjToScanQueue* q = queue(i);
    assert(q != nullptr, "Sanity");
    total += q->full_size();
  }
  if (total == 0) {
    // Nothing to do.
    return;
  }

  size_t target_size = total / target_queues;

  // Redistribute the work between queues.
  // Do two passes to make sure all queues had a chance to push and pop.
  Stack<ShenandoahMarkTask, mtGC> ts;
  ShenandoahMarkTask t;

  for (int p = 0; p < 2; p++) {
    for (uint i = 0; i < GenericTaskQueueSet::size(); i++) {
      ShenandoahObjToScanQueue* q = queue(i);

      // Push and pop targets relative to expected average size.
      size_t q_size = q->full_size();
      size_t to_pop  = (q_size > target_size) ? (q_size - target_size) : 0;
      size_t to_push = (q_size < target_size) ? MIN2(ts.size(), target_size - q_size) : 0;

      if (i >= target_queues) {
        // Queue must be emptied out.
        to_pop = q_size;
        to_push = 0;
      }

      for (size_t c = 0; c < to_pop; c++) {
        bool succ = q->pop(t);
        assert(succ, "Must succeed");
        ts.push(t);
      }
      assert(q->full_size() <= target_size,
             "After pops, queue size (%zu) must fit the target (%zu)",
             q->full_size(), target_size);

      for (size_t c = 0; c < to_push; c++) {
        assert(!ts.is_empty(), "Must not be empty");
        bool succ = q->push(ts.pop());
        assert(succ, "Must succeed");
      }
    }
  }

  // Round-robin the remaining elements.
  assert(ts.size() <= target_queues,
        "Only a small tail (%zu) should remain for %zu queues",
        ts.size(), target_queues);
  uint rr_idx = 0;
  while (!ts.is_empty()) {
    queue(rr_idx)->push(ts.pop());
    if (++rr_idx >= target_queues) {
      rr_idx = 0;
    }
  }

  // Every queue is now balanced population-wise. Balance the split between overflow
  // stack and local queue. This exposes tasks to work-stealing. Avoid filling out
  // the local queue completely: leave some space for local pushes.
  for (uint i = 0; i < target_queues; i++) {
    ShenandoahObjToScanQueue* q = queue(i);
    size_t q_limit = q->capacity() / 4 * 3;
    size_t q_free  = (q_limit > q->size()) ? (q_limit - q->size()) : 0;
    size_t to_balance = MIN2<size_t>(q->overflow_stack()->size(), q_free);
    for (size_t c = 0; c < to_balance; c++) {
      bool succ_pop = q->pop_overflow(t);
      assert(succ_pop, "Must succeed");
      bool succ_push = q->push(t);
      assert(succ_push, "Must succeed");
    }
  }

#ifdef ASSERT
  // Final checks.
  assert(ts.is_empty(), "Must be empty");
  size_t total_after = 0;
  for (uint i = 0; i < GenericTaskQueueSet::size(); i++) {
    ShenandoahObjToScanQueue* q = queue(i);
    if (i < target_queues) {
      size_t full_size = q->full_size();
      assert((target_size <= full_size + 1) && (full_size <= target_size + 1),
             "Queue size (%zu) is off the target (%zu)",
              full_size, target_size);
      total_after += full_size;
    } else {
      assert(q->is_empty(), "Queue must be empty");
    }
  }
  assert(total == total_after, "Lost elements: %zu vs %zu", total, total_after);
#endif
}

bool ShenandoahTerminatorTerminator::should_exit_termination() {
  return _heap->cancelled_gc();
}
