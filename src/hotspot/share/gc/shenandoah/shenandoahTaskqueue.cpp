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

bool ShenandoahTaskQueueStack::is_empty() {
  return _top == 0;
}

void ShenandoahTaskQueueStack::clear() {
  _top = 0;
}

ShenandoahTaskQueue::ShenandoahTaskQueue(ShenandoahTaskQueueSet* qs) :
  _qs(qs) {
  _stack = _qs->pick_free();
}

ShenandoahTaskQueue::~ShenandoahTaskQueue() {
  _stack->clear();
  _qs->mark_free(_stack);
}

bool ShenandoahTaskQueue::is_empty() {
  return _stack->is_empty();
}

void ShenandoahTaskQueue::clear() {
  _stack->clear();
}

void ShenandoahTaskQueue::flush_to_shared() {
  if (!_stack->is_empty()) {
    _qs->mark_shared(_stack);
    _stack = _qs->pick_free();
  }
}

void ShenandoahTaskQueue::compact() {
  if (_stack->is_empty()) {
    _qs->mark_free(_stack);
    _stack = _qs->pick_free();
  }
}

ShenandoahTaskQueueSegment* ShenandoahTaskQueueSegment::create() {
  size_t align = MAX2(os::vm_allocation_granularity(), os::vm_page_size());
  size_t bytes = align_up(sizeof(ShenandoahTaskQueueSegment), align);
  ReservedSpace rs = MemoryReserver::reserve(bytes, align, os::vm_page_size(), mtGC);
  if (!rs.is_reserved()) {
    vm_exit_out_of_memory(bytes, OOM_MMAP_ERROR, "Cannot reserve task queue segment");
  }
  os::commit_memory_or_exit(rs.base(), rs.size(), !ExecMem, "");

  return new (rs.base()) ShenandoahTaskQueueSegment(rs);
}

void ShenandoahTaskQueueSegment::destroy(ShenandoahTaskQueueSegment* seg) {
  ReservedSpace rs = seg->_rs;
  seg->~ShenandoahTaskQueueSegment();
  MemoryReserver::release(rs);
}

ShenandoahTaskQueueSegment::ShenandoahTaskQueueSegment(ReservedSpace& rs) :
  _free_bm(right_n_bits(SEGMENT_SIZE)), _shared_bm(0), _next(nullptr), _rs(rs) {
  for (size_t i = 0; i < SEGMENT_SIZE; i++) {
    ::new (&_stacks[i]) ShenandoahTaskQueueStack(this, i);
  }
}

void ShenandoahTaskQueueSegment::clear() {
  uint64_t shared = _shared_bm.load_relaxed();
  for (uint64_t m = shared; m != 0; m &= m - 1) {
    _stacks[count_trailing_zeros(m)].clear();
  }
  _shared_bm.store_relaxed(0);
  _free_bm.store_relaxed(_free_bm.load_relaxed() | shared);
}

bool ShenandoahTaskQueueSegment::all_are_free() const {
  return _shared_bm.load_relaxed() == 0 &&
         _free_bm.load_relaxed() == (uint64_t)right_n_bits(SEGMENT_SIZE);
}

ShenandoahTaskQueueSet::ShenandoahTaskQueueSet(uint queue_count) :
  _queue_count(queue_count), _num_segments(0), _peak_segments(0), _shared_count(0) {

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

  ShenandoahTaskQueueSegment* cur = _segments_head.load_acquire();
  while (cur != nullptr) {
    ShenandoahTaskQueueSegment* next = cur->next();
    ShenandoahTaskQueueSegment::destroy(cur);
    cur = next;
  }
}

bool ShenandoahTaskQueueSet::is_empty() {
  for (uint i = 0; i < _queue_count; i++) {
    ShenandoahTaskQueue* q = queue(i);
    assert(q != nullptr, "Sanity");
    if (!q->is_empty()) {
      return false;
    }
  }
  return is_shared_empty();
}

void ShenandoahTaskQueueSet::clear() {
  for (uint i = 0; i < _queue_count; i++) {
    ShenandoahTaskQueue* q = queue(i);
    assert(q != nullptr, "Sanity");
    q->clear();
  }

  ShenandoahTaskQueueSegment* cur = _segments_head.load_acquire();
  while (cur != nullptr) {
    cur->clear();
    cur = cur->next();
  }
  _shared_count.store_relaxed(0);
}

void ShenandoahTaskQueueSet::trim() {
  size_t num_segments = _num_segments.load_relaxed();
  if (num_segments == 0) {
    return;
  }

  // Make queues reacquire their chunks from the head.
  for (uint i = 0; i < _queue_count; i++) {
    queue(i)->compact();
  }

  // Simple hysteresis: trim to half the excess segments.
  size_t min_for_queues = align_up(_queue_count, ShenandoahTaskQueueSegment::SEGMENT_SIZE) / ShenandoahTaskQueueSegment::SEGMENT_SIZE;
  size_t peak = _peak_segments.load_relaxed();
  size_t keep = MAX2((peak + num_segments) / 2, min_for_queues);
  _peak_segments.store_relaxed(0);
  size_t s = num_segments;

  // Delete free segments.
  ShenandoahTaskQueueSegment* prev = nullptr;
  ShenandoahTaskQueueSegment* cur = _segments_head.load_acquire();
  while (s > keep && cur != nullptr) {
    ShenandoahTaskQueueSegment* next = cur->next();
    if (cur->all_are_free()) {
      if (prev == nullptr) {
        _segments_head.release_store(next);
      } else {
        prev->set_next(next);
      }
      ShenandoahTaskQueueSegment::destroy(cur);
      _num_segments.fetch_then_sub((size_t)1);
      s--;
    } else {
      prev = cur;
    }
    cur = next;
  }
}

void ShenandoahTaskQueueSet::flush_to_shared() {
  for (uint i = 0; i < _queue_count; i++) {
    ShenandoahTaskQueue* q = queue(i);
    assert(q != nullptr, "Sanity");
    q->flush_to_shared();
  }
}

ShenandoahTaskTerminator::ShenandoahTaskTerminator(int workers, ShenandoahTaskQueueSet *queues) :
  _heap(ShenandoahHeap::heap()), _queues(queues), _active_workers(workers), _total_workers(workers) {}

bool ShenandoahTaskTerminator::offer_termination(bool cancellable) {
  _active_workers.fetch_then_sub((size_t)1, memory_order_seq_cst);
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
      _active_workers.fetch_then_add((size_t)1, memory_order_seq_cst);
      return false;
    }
    spin.wait();
  }
}

