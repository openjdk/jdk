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

ShenandoahTaskQueueSegment* ShenandoahTaskQueueSegment::create(size_t seg_idx) {
  size_t align = MAX2(os::vm_allocation_granularity(), os::vm_page_size());
  size_t bytes = align_up(sizeof(ShenandoahTaskQueueSegment), align);
  ReservedSpace rs = MemoryReserver::reserve(bytes, align, os::vm_page_size(), mtGC);
  if (!rs.is_reserved()) {
    vm_exit_out_of_memory(bytes, OOM_MMAP_ERROR, "Cannot reserve task queue segment");
  }
  os::commit_memory_or_exit(rs.base(), rs.size(), !ExecMem, "");

  return new (rs.base()) ShenandoahTaskQueueSegment(seg_idx, rs);
}

void ShenandoahTaskQueueSegment::destroy(ShenandoahTaskQueueSegment* seg) {
  ReservedSpace rs = seg->_rs;
  seg->~ShenandoahTaskQueueSegment();
  MemoryReserver::release(rs);
}

ShenandoahTaskQueueSegment::ShenandoahTaskQueueSegment(size_t seg_idx, ReservedSpace& rs) :
  _free_bm(right_n_bits(SEGMENT_SIZE)), _shared_bm(0), _rs(rs), _segment_index(seg_idx) {
  for (size_t i = 0; i < SEGMENT_SIZE; i++) {
    ::new (&_stacks[i]) ShenandoahTaskQueueStack((seg_idx << SEGMENT_SIZE_SHIFT) + i);
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

bool ShenandoahTaskQueueSegment::are_all_free() const {
  return _shared_bm.load_relaxed() == 0 &&
         _free_bm.load_relaxed() == (uint64_t)right_n_bits(SEGMENT_SIZE);
}

ShenandoahTaskQueueSet::ShenandoahTaskQueueSet(uint queue_count) :
  _queue_count(queue_count), _num_segments(0), _peak_segments(0), _shared_count(0) {

  // Size up for the absolute worst case: one 8-byte task per smallest 8-byte object
  size_t alignment = MAX3(STACK_SIZE, os::vm_allocation_granularity(), os::vm_page_size());
  _max_segments = align_up(MaxHeapSize, alignment) / STACK_SIZE / ShenandoahTaskQueueSegment::SEGMENT_SIZE;
  _segments = NEW_C_HEAP_ARRAY(ShenandoahTaskQueueSegment*, _max_segments, mtGC);
  memset(_segments, 0, _max_segments * sizeof(ShenandoahTaskQueueSegment*));

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

  for (size_t s = 0; s < _num_segments.load_relaxed(); s++) {
    ShenandoahTaskQueueSegment::destroy(_segments[s]);
  }
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

  for (size_t s = 0; s < _num_segments.load_relaxed(); s++) {
    _segments[s]->clear();
  }
  _shared_count.store_relaxed(0);
}

void ShenandoahTaskQueueSet::trim() {
  // Rearrange the chunks to the left-most segments.
  for (size_t i = 0; i < _queue_count; i++) {
    queue(i)->compact();
  }

  // Delete free segments from the tail. Simple hysteresis: only trim to half peak.
  _peak_segments = MAX2(_peak_segments / 2, _num_segments.load_relaxed());
  size_t min_for_queues = align_up(_queue_count, ShenandoahTaskQueueSegment::SEGMENT_SIZE) / ShenandoahTaskQueueSegment::SEGMENT_SIZE + 1;
  size_t keep = MAX2(_peak_segments, min_for_queues);

  size_t s = _num_segments.load_relaxed() - 1;
  while (s > keep && _segments[s]->are_all_free()) {
    ShenandoahTaskQueueSegment::destroy(_segments[s]);
    AtomicAccess::store(&_segments[s], (ShenandoahTaskQueueSegment*)nullptr);
    _num_segments.fetch_then_sub((size_t)1);
    s--;
  }
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

