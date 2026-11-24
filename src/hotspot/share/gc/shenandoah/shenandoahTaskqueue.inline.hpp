/*
 * Copyright (c) 2016, 2019, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHTASKQUEUE_INLINE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHTASKQUEUE_INLINE_HPP

#include "gc/shenandoah/shenandoahTaskqueue.hpp"

void ShenandoahTaskQueue::push(ShenandoahTask task) {
  if (!_stack->push(task)) {
    _qs->mark_shared(_stack);
    _stack = _qs->pick_free();
    bool succ = _stack->push(task);
    assert(succ, "Must succeed");
  }
}

bool ShenandoahTaskQueue::pop(ShenandoahTask& task) {
  if (_stack->pop(task)) {
    return true;
  }

  auto stack = _qs->pick_shared();
  if (stack != nullptr) {
    _qs->mark_free(_stack);
    _stack = stack;
    return _stack->pop(task);
  }

  return false;
}

inline void ShenandoahTaskQueue::share_work() {
  if (_stack->is_sliceable()) {
    auto top_stack = _qs->pick_free();
    _stack->slice_top(top_stack);
    _qs->mark_shared(_stack);
    _stack = top_stack;
  }
}

inline ShenandoahTaskQueueStack* ShenandoahTaskQueueSegment::pick(Atomic<uint64_t>* bm) {
  uint64_t cur = bm->load_relaxed();
  while (cur != 0) {
    int idx = count_trailing_zeros(cur);
    uint64_t prev = bm->compare_exchange(cur, cur & ~nth_bit(idx), memory_order_seq_cst);
    if (prev == cur) {
      return &_stacks[idx];
    }
    cur = prev;
  }
  return nullptr;
}

inline void ShenandoahTaskQueueSegment::mark(ShenandoahTaskQueueStack* stack, Atomic<uint64_t>* bm) {
  uint64_t mask = nth_bit<uint64_t>(checked_cast<int>(stack->index()));
  assert((_shared_bm.load_relaxed() & mask) == 0, "Must not be shared");
  assert((_free_bm.load_relaxed() & mask) == 0, "Mut not be free");
  bm->fetch_then_or(mask, memory_order_release);
}

ShenandoahTaskQueueStack* ShenandoahTaskQueueSegment::pick_shared() {
  return pick(&_shared_bm);
}

ShenandoahTaskQueueStack* ShenandoahTaskQueueSegment::pick_free() {
  return pick(&_free_bm);
}

void ShenandoahTaskQueueSegment::mark_shared(ShenandoahTaskQueueStack* stack) {
  mark(stack, &_shared_bm);
}

void ShenandoahTaskQueueSegment::mark_free(ShenandoahTaskQueueStack* stack) {
  mark(stack, &_free_bm);
}

inline bool ShenandoahTaskQueueSet::is_shared_empty() {
  return _shared_count.load_relaxed() == 0;
}

inline size_t ShenandoahTaskQueueSet::shared_count() {
  return _shared_count.load_relaxed();
}

inline ShenandoahTaskQueueStack* ShenandoahTaskQueueSet::pick_shared() {
  if (_shared_count.load_relaxed() == 0) {
    return nullptr;
  }
  ShenandoahTaskQueueSegment* cur = _segments_head.load_acquire();
  while (cur != nullptr) {
    auto stack = cur->pick_shared();
    if (stack != nullptr) {
      _shared_count.fetch_then_sub((size_t)1, memory_order_seq_cst);
      return stack;
    }
    cur = cur->next();
  }
  return nullptr;
}

inline ShenandoahTaskQueueStack* ShenandoahTaskQueueSet::pick_free() {
  while (true) {
    ShenandoahTaskQueueSegment* head = _segments_head.load_acquire();
    ShenandoahTaskQueueSegment* cur = head;
    size_t depth = 1;
    while (cur != nullptr) {
      auto stack = cur->pick_free();
      if (stack != nullptr) {
        size_t current_peak;
        while (depth > (current_peak = _peak_segments.load_relaxed())) {
          _peak_segments.compare_set(current_peak, depth, memory_order_relaxed);
        }
        return stack;
      }
      cur = cur->next();
      depth++;
    }
    try_grow(head);
  }
}

inline void ShenandoahTaskQueueSet::mark_shared(ShenandoahTaskQueueStack* stack) {
  assert(!stack->is_empty(), "Do not share empty stacks");
  // For the sake of proper termination, we need to bump shared work count
  // before publishing the stack.
  _shared_count.fetch_then_add((size_t)1, memory_order_seq_cst);
  stack->segment()->mark_shared(stack);
}

inline void ShenandoahTaskQueueSet::mark_free(ShenandoahTaskQueueStack* stack) {
  assert(stack->is_empty(), "Do not free non-empty stacks");
  stack->segment()->mark_free(stack);
}

bool ShenandoahTaskQueueStack::push(ShenandoahTask t) {
  if (_top < MAX_SIZE) {
    _tasks[_top++] = t;
    return true;
  }
  return false;
}

bool ShenandoahTaskQueueStack::pop(ShenandoahTask& t) {
  if (_top > 0) {
    t = _tasks[--_top];
    return true;
  }
  return false;
}

inline void ShenandoahTaskQueueStack::slice_top(ShenandoahTaskQueueStack* to_stack) {
  assert(is_sliceable(), "Filtered by caller");
  assert(to_stack->is_empty(), "Must be empty");
  size_t slice = _top / 2;
  _top -= slice;
  to_stack->_top = slice;
  memcpy(to_stack->_tasks, &_tasks[_top], slice * sizeof(ShenandoahTask));
}

inline bool ShenandoahTaskQueueStack::is_sliceable() {
  return _top >= MIN_SIZE * 2;
}

void ShenandoahTaskQueueSet::try_grow(ShenandoahTaskQueueSegment* seen_head) {
  if (_segments_head.load_acquire() != seen_head) {
    // Head already moved, let caller rescan before creating segments.
    return;
  }

  // Create the segment and link it. Under contention, several threads may succeed
  // to install their segments. This is a tradeoff for avoiding mmap churn.
  // The temporary spike under contention would be managed by trimming code later.
  ShenandoahTaskQueueSegment* cand = ShenandoahTaskQueueSegment::create();
  while (true) {
    auto head = _segments_head.load_acquire();
    cand->set_next(head);
    if (_segments_head.compare_set(head, cand, memory_order_seq_cst)) {
      _num_segments.fetch_then_add((size_t)1, memory_order_seq_cst);
      break;
    }
  }
}

inline int ShenandoahTaskTerminator::waiters() {
  size_t active = _active_workers.load_relaxed();
  if (_total_workers > active) {
    return _total_workers - active;
  }
  return 0;
}

inline bool ShenandoahTaskTerminator::has_waiters() {
  return waiters() > 0;
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHTASKQUEUE_INLINE_HPP
