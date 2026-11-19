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
#include "memory/memoryReserver.hpp"
#include "utilities/spinYield.hpp"

bool ShenandoahTaskQueueChunk::is_empty() {
  return _top == 0;
}

void ShenandoahTaskQueueChunk::clear() {
  _top = 0;
}

ShenandoahTaskQueue::ShenandoahTaskQueue(ShenandoahTaskQueueSet* qs) :
  _qs(qs) {
  _chunk = _qs->flip_from_free();
}

ShenandoahTaskQueue::~ShenandoahTaskQueue() {
  _chunk->clear();
  _qs->flip_to_free(_chunk);
}

bool ShenandoahTaskQueue::is_empty() {
  return _chunk->is_empty();
}

void ShenandoahTaskQueue::clear() {
  _chunk->clear();
}

void ShenandoahTaskQueue::flush_to_shared() {
  if (!_chunk->is_empty()) {
    _qs->flip_to_shared(_chunk);
    _chunk = _qs->flip_from_free();
  }
}

ShenandoahTaskQueueSegment::ShenandoahTaskQueueSegment() : _free_bm(0), _shared_bm(0) {
}

ShenandoahTaskQueueSet::ShenandoahTaskQueueSet(uint queue_count) :
  _queue_count(queue_count),
  _shared_count(0) {

  // Size up for the absolute worst case: one 8-byte task per smallest 8-byte object
  size_t alignment = MAX3(CHUNK_SIZE, os::vm_allocation_granularity(), os::vm_page_size());
  _max_segments = align_up(MaxHeapSize, alignment) / CHUNK_SIZE / ShenandoahTaskQueueSegment::SEGMENT_SIZE;
  _segments = NEW_C_HEAP_ARRAY(ShenandoahTaskQueueSegment, _max_segments, mtGC);

  _queues = NEW_C_HEAP_ARRAY(ShenandoahTaskQueue*, _queue_count, mtGC);
  for (size_t i = 0; i < _queue_count; i++) {
    _queues[i] = new ShenandoahTaskQueue(this);
  }
}

ShenandoahTaskQueueSet::~ShenandoahTaskQueueSet() {
  for (size_t i = 0; i < _queue_count; i++) {
    delete _queues[i];
  }
  FREE_C_HEAP_ARRAY(_queues);
  FREE_C_HEAP_ARRAY(_segments);
}

bool ShenandoahTaskQueueSet::is_empty() {
  for (size_t i = 0; i < _queue_count; i++) {
    ShenandoahTaskQueue* q = queue(i);
    assert(q != nullptr, "Sanity");
    if (!q->is_empty()) {
      return false;
    }
  }
  return is_shared_empty();
}

void ShenandoahTaskQueueSet::clear() {
  for (size_t i = 0; i < _queue_count; i++) {
    ShenandoahTaskQueue* q = queue(i);
    assert(q != nullptr, "Sanity");
    q->clear();
  }

  size_t end = chunks_end();
  for (size_t idx = _shared_bm.find_first_set_bit(0, end); idx < end;
              idx = _shared_bm.find_first_set_bit(idx + 1, end)) {
    chunk_at(idx)->clear();
    _shared_bm.clear_bit(idx);
    _free_bm.set_bit(idx);
  }
  _shared_count.store_relaxed(0);
}

void ShenandoahTaskQueueSet::flush_to_shared() {
  for (size_t i = 0; i < _queue_count; i++) {
    ShenandoahTaskQueue* q = queue(i);
    assert(q != nullptr, "Sanity");
    q->flush_to_shared();
  }
}

ShenandoahTaskTerminator::ShenandoahTaskTerminator(int workers, ShenandoahTaskQueueSet *queues) :
  _heap(ShenandoahHeap::heap()), _queues(queues), _active_workers(workers), _total_workers(workers) {}

bool ShenandoahTaskTerminator::offer_termination(bool cancellable) {
  _active_workers.fetch_then_sub((size_t)1, memory_order_relaxed);
  SpinYield spin;
  while (true) {
    if (_active_workers.load_relaxed() == 0) {
      // Consensus: everyone terminated
      assert(_queues->is_shared_empty(), "No more shared work");
      return true;
    }
    if (cancellable && _heap->cancelled_gc()) {
      // Requested termination
      return true;
    }
    if (!_queues->is_shared_empty()) {
      // New work appeared, no termination
      _active_workers.fetch_then_add((size_t)1, memory_order_relaxed);
      return false;
    }
    spin.wait();
  }
}

