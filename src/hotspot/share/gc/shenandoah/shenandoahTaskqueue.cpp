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
  // Figure out the population target.
  size_t total = 0;
  for (uint i = 0; i < GenericTaskQueueSet::size(); i++) {
    ShenandoahObjToScanQueue *q = queue(i);
    assert(q != nullptr, "Sanity");
    total += q->full_size();
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
      size_t to_push = (q_size < target_size) ? MIN2(ts.size(), target_size - q_size): 0;

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
        q->push(ts.pop());
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

#ifdef ASSERT
  // Final checks.
  assert(ts.is_empty(), "Must be empty");
  for (uint i = 0; i < GenericTaskQueueSet::size(); i++) {
    ShenandoahObjToScanQueue* q = queue(i);
    if (i < target_queues) {
      assert((target_size <= q->full_size() + 1) && (q->full_size() <= target_size + 1),
             "Queue size (%zu) is off the target (%zu)",
              q->full_size(), target_size);
    } else {
      assert(q->is_empty(), "Queue must be empty");
    }
  }
#endif
}

bool ShenandoahTerminatorTerminator::should_exit_termination() {
  return _heap->cancelled_gc();
}
