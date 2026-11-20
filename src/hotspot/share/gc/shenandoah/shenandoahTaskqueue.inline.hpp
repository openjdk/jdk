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
  if (!_chunk->push(task)) {
    _qs->flip_to_shared(_chunk);
    _chunk = _qs->flip_from_free();
    bool succ = _chunk->push(task);
    assert(succ, "Must succeed");
  }
}

bool ShenandoahTaskQueue::pop(ShenandoahTask& task) {
  if (_chunk->pop(task)) {
    return true;
  }

  auto chunk = _qs->flip_from_shared();
  if (chunk != nullptr) {
    _qs->flip_to_free(_chunk);
    _chunk = chunk;
    return _chunk->pop(task);
  }

  return false;
}

inline void ShenandoahTaskQueue::share_work() {
  if (_chunk->is_sliceable()) {
    auto top_chunk = _qs->flip_from_free();
    _chunk->slice_top(top_chunk);
    _qs->flip_to_shared(_chunk);
    _chunk = top_chunk;
  }
}

inline ShenandoahTaskQueueChunk* ShenandoahTaskQueueSegment::flip_from(Atomic<uint64_t>* bm) {
  uint64_t cur = bm->load_relaxed();
  while (cur != 0) {
    int idx = count_trailing_zeros(cur);
    uint64_t prev = bm->compare_exchange(cur, cur & ~nth_bit(idx));
    if (prev == cur) {
      return &_chunks[idx];
    }
    cur = prev;
  }
  return nullptr;
}

inline void ShenandoahTaskQueueSegment::flip_to(ShenandoahTaskQueueChunk* chunk, Atomic<uint64_t>* bm) {
  int local_idx = checked_cast<int>(chunk->index() & (SEGMENT_SIZE - 1));
  uint64_t mask = nth_bit<uint64_t>(local_idx);
  uint64_t prev = bm->fetch_then_or(mask);
  assert((prev & mask) == 0, "Must not have been set");
}

ShenandoahTaskQueueChunk* ShenandoahTaskQueueSegment::flip_from_shared() {
  return flip_from(&_shared_bm);
}

ShenandoahTaskQueueChunk* ShenandoahTaskQueueSegment::flip_from_free() {
  return flip_from(&_free_bm);
}

void ShenandoahTaskQueueSegment::flip_to_shared(ShenandoahTaskQueueChunk* chunk) {
  flip_to(chunk, &_shared_bm);
}

void ShenandoahTaskQueueSegment::flip_to_free(ShenandoahTaskQueueChunk* chunk) {
  flip_to(chunk, &_free_bm);
}

inline bool ShenandoahTaskQueueSet::is_shared_empty() {
  return _shared_count.load_relaxed() == 0;
}

inline ShenandoahTaskQueueChunk* ShenandoahTaskQueueSet::flip_from_shared() {
  if (_shared_count.load_relaxed() == 0) {
    return nullptr;
  }
  size_t n = _num_segments.load_acquire();
  for (size_t s = 0; s < n; s++) {
    auto chunk = _segments[s]->flip_from_shared();
    if (chunk != nullptr) {
      _shared_count.fetch_then_sub((size_t)1, memory_order_seq_cst);
      return chunk;
    }
  }
  return nullptr;
}

inline ShenandoahTaskQueueChunk* ShenandoahTaskQueueSet::flip_from_free() {
  while (true) {
    size_t n = _num_segments.load_acquire();
    for (size_t s = 0; s < n; s++) {
      auto chunk = _segments[s]->flip_from_free();
      if (chunk != nullptr) {
        return chunk;
      }
    }
    try_grow(n);
  }
}

inline void ShenandoahTaskQueueSet::flip_to_shared(ShenandoahTaskQueueChunk* chunk) {
  assert(!chunk->is_empty(), "Do not share empty chunks");
  // For the sake of proper termination, we need to bump shared work count
  // before publishing the chunk.
  _shared_count.fetch_then_add((size_t)1, memory_order_seq_cst);
  size_t sub_idx = chunk->index() >> ShenandoahTaskQueueSegment::SEGMENT_SIZE_SHIFT;
  auto seg = _segments[sub_idx];
  assert(seg != nullptr, "Segment should be allocated");
  seg->flip_to_shared(chunk);
}

inline void ShenandoahTaskQueueSet::flip_to_free(ShenandoahTaskQueueChunk* chunk) {
  assert(chunk->is_empty(), "Do not free non-empty chunks");
  size_t sub_idx = chunk->index() >> ShenandoahTaskQueueSegment::SEGMENT_SIZE_SHIFT;
  auto seg = _segments[sub_idx];
  assert(seg != nullptr, "Segment should be allocated");
  seg->flip_to_free(chunk);
}

bool ShenandoahTaskQueueChunk::push(ShenandoahTask t) {
  if (_top < MAX_SIZE) {
    _tasks[_top++] = t;
    return true;
  }
  return false;
}

bool ShenandoahTaskQueueChunk::pop(ShenandoahTask& t) {
  if (_top > 0) {
    t = _tasks[--_top];
    return true;
  }
  return false;
}

inline void ShenandoahTaskQueueChunk::slice_top(ShenandoahTaskQueueChunk* to_chunk) {
  assert(is_sliceable(), "Filtered by caller");
  assert(to_chunk->is_empty(), "Must be empty");
  size_t slice = _top / 2;
  _top -= slice;
  to_chunk->_top = slice;
  memcpy(to_chunk->_tasks, &_tasks[_top], slice * sizeof(ShenandoahTask));
}

inline bool ShenandoahTaskQueueChunk::is_sliceable() {
  return _top >= MIN_SIZE * 2;
}

ShenandoahTaskQueueSegment* ShenandoahTaskQueueSet::try_grow(size_t n) {
  if (n >= _max_segments) {
    vm_exit_out_of_memory(sizeof(ShenandoahTaskQueueSegment), OOM_MALLOC_ERROR, "ShenandoahTaskQueueSet pool exhausted");
  }

  ShenandoahTaskQueueSegment* cand = nullptr;
  if (AtomicAccess::load_acquire(&_segments[n]) == nullptr) {
    cand = new ShenandoahTaskQueueSegment(n);
    if (AtomicAccess::cmpxchg(&_segments[n], (ShenandoahTaskQueueSegment*)nullptr, cand) != nullptr) {
      // Someone else won the installation race.
      delete cand;
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
