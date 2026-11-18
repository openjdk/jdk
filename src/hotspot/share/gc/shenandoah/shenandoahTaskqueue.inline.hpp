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
    _qs->push_shared(_chunk);
    _chunk = _qs->pop_free();
    bool succ = _chunk->push(task);
    assert(succ, "Must succeed");
  }
}

bool ShenandoahTaskQueue::pop(ShenandoahTask& task) {
  if (_chunk->pop(task)) {
    return true;
  }

  auto chunk = _qs->pop_shared();
  if (chunk != nullptr) {
    _qs->push_free(_chunk);
    _chunk = chunk;
    return _chunk->pop(task);
  }

  return false;
}

inline void ShenandoahTaskQueue::share_work() {
  if (_chunk->is_sliceable()) {
    auto top_chunk = _qs->pop_free();
    _chunk->slice_top(top_chunk);
    _qs->push_shared(_chunk);
    _chunk = top_chunk;
  }
}

inline bool ShenandoahTaskQueueSet::is_shared_empty() {
  return _shared_count.load_relaxed() == 0;
}

inline size_t ShenandoahTaskQueueSet::allocated() {
  return _chunks_allocated.load_relaxed();
}

inline ShenandoahTaskQueueChunk* ShenandoahTaskQueueSet::pop_shared() {
  if (_shared_count.load_relaxed() == 0) {
    return nullptr;
  }
  size_t idx = find_and_claim(_shared_bm);
  if (idx == _max_chunks) {
    return nullptr;
  }
  _shared_count.fetch_then_sub((size_t)1, memory_order_seq_cst);
  return chunk_at(idx);
}

inline ShenandoahTaskQueueChunk* ShenandoahTaskQueueSet::pop_free() {
  size_t idx = find_and_claim(_free_bm);
  if (idx == _max_chunks) {
    return allocate_chunk();
  }
  return chunk_at(idx);
}

inline void ShenandoahTaskQueueSet::push_shared(ShenandoahTaskQueueChunk* chunk) {
  assert(!chunk->is_empty(), "Do not share empty chunks");
  // For the sake of proper termination, we need to bump shared work count
  // before publishing the chunk.
  _shared_count.fetch_then_add((size_t)1, memory_order_seq_cst);
  size_t idx = index_of(chunk);
  bool succ = _shared_bm.par_set_bit(idx, memory_order_release);
  assert(succ, "Must succeed");
}

inline void ShenandoahTaskQueueSet::push_free(ShenandoahTaskQueueChunk* chunk) {
  assert(chunk->is_empty(), "Do not free non-empty chunks");
  size_t idx = index_of(chunk);
  bool succ = _free_bm.par_set_bit(idx, memory_order_release);
  assert(succ, "Must succeed");
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

inline ShenandoahTaskQueueChunk* ShenandoahTaskQueueSet::chunk_at(size_t idx) {
  return reinterpret_cast<ShenandoahTaskQueueChunk*>(_chunks_base + idx * CHUNK_SIZE);
}

inline size_t ShenandoahTaskQueueSet::index_of(ShenandoahTaskQueueChunk* chunk) {
  size_t off = pointer_delta((char*)chunk, _chunks_base, 1);
  assert(is_aligned(off, CHUNK_SIZE), "Sanity: alignment");
  return off / CHUNK_SIZE;
}

inline size_t ShenandoahTaskQueueSet::find_and_claim(BitMapView& bm) {
  size_t end = chunks_end();
  for (size_t idx = bm.find_first_set_bit(0, end); idx < end;
              idx = bm.find_first_set_bit(idx + 1, end)) {
    if (bm.par_clear_bit(idx, memory_order_acq_rel)) {
      return idx;
    }
  }
  return _max_chunks;
}

inline size_t ShenandoahTaskQueueSet::chunks_end() {
  return MIN2(_chunks_allocated.load_relaxed(), _max_chunks);
}

inline ShenandoahTaskQueueChunk* ShenandoahTaskQueueSet::allocate_chunk() {
  size_t idx = _chunks_allocated.fetch_then_add((size_t)1, memory_order_relaxed);
  if (idx >= _chunks_committed.load_acquire()) {
    expand_to(idx);
  }
  assert(idx < _chunks_committed.load_relaxed(), "Sanity");
  return new (chunk_at(idx)) ShenandoahTaskQueueChunk();
}

inline bool ShenandoahTaskTerminator::has_waiters() {
  return _active_workers.load_relaxed() < _total_workers;
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHTASKQUEUE_INLINE_HPP
