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
  size_t local_idx = checked_cast<size_t>(stack->index() & (SEGMENT_SIZE - 1));
  uint64_t mask = nth_bit<uint64_t>(checked_cast<int>(local_idx));

#ifdef ASSERT
  size_t seg_idx = checked_cast<size_t>(stack->index() >> SEGMENT_SIZE_SHIFT);
  assert(seg_idx == _segment_index, "Chunk belongs to this segment");
  assert((_shared_bm.load_relaxed() & mask) == 0, "Must not be shared");
  assert((_free_bm.load_relaxed() & mask) == 0, "Mut not be free");
#endif

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

inline ShenandoahTaskQueueStack* ShenandoahTaskQueueSet::pick_shared() {
  if (_shared_count.load_relaxed() == 0) {
    return nullptr;
  }
  size_t n = _num_segments.load_acquire();
  for (size_t s = 0; s < n; s++) {
    auto stack = _segments[s]->pick_shared();
    if (stack != nullptr) {
      _shared_count.fetch_then_sub((size_t)1, memory_order_seq_cst);
      return stack;
    }
  }
  return nullptr;
}

inline ShenandoahTaskQueueStack* ShenandoahTaskQueueSet::pick_free() {
  while (true) {
    size_t n = _num_segments.load_acquire();
    for (size_t s = 0; s < n; s++) {
      auto stack = _segments[s]->pick_free();
      if (stack != nullptr) {
        return stack;
      }
    }
    try_grow(n);
  }
}

inline void ShenandoahTaskQueueSet::mark_shared(ShenandoahTaskQueueStack* stack) {
  assert(!stack->is_empty(), "Do not share empty stacks");
  // For the sake of proper termination, we need to bump shared work count
  // before publishing the stack.
  _shared_count.fetch_then_add((size_t)1, memory_order_seq_cst);
  size_t sub_idx = stack->index() >> ShenandoahTaskQueueSegment::SEGMENT_SIZE_SHIFT;
  auto seg = _segments[sub_idx];
  assert(seg != nullptr, "Segment should be allocated");
  seg->mark_shared(stack);
}

inline void ShenandoahTaskQueueSet::mark_free(ShenandoahTaskQueueStack* stack) {
  assert(stack->is_empty(), "Do not free non-empty stacks");
  size_t sub_idx = stack->index() >> ShenandoahTaskQueueSegment::SEGMENT_SIZE_SHIFT;
  auto seg = _segments[sub_idx];
  assert(seg != nullptr, "Segment should be allocated");
  seg->mark_free(stack);
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

ShenandoahTaskQueueSegment* ShenandoahTaskQueueSet::try_grow(size_t n) {
  if (n >= _max_segments) {
    vm_exit_out_of_memory(sizeof(ShenandoahTaskQueueSegment), OOM_MALLOC_ERROR, "ShenandoahTaskQueueSet pool exhausted");
  }

  ShenandoahTaskQueueSegment* cand = nullptr;
  if (AtomicAccess::load_acquire(&_segments[n]) == nullptr) {
    cand = ShenandoahTaskQueueSegment::create(n);
    if (AtomicAccess::cmpxchg(&_segments[n], (ShenandoahTaskQueueSegment*)nullptr, cand) != nullptr) {
      // Someone else won the installation race.
      ShenandoahTaskQueueSegment::destroy(cand);
      cand = nullptr;
    }
  }

  // Whatever happens, the slot[n] is populated, bump the segment count.
  _num_segments.compare_exchange(n, n + 1);
  return cand;
}

inline bool ShenandoahTaskTerminator::has_waiters() {
  return _active_workers.load_relaxed() < _total_workers;
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHTASKQUEUE_INLINE_HPP
