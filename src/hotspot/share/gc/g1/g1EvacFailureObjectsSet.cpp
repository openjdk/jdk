/*
 * Copyright (c) 2021, Huawei and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/g1/g1EvacFailureObjectsSet.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1SegmentedArray.inline.hpp"
#include "gc/g1/heapRegion.hpp"
#include "gc/shared/taskqueue.inline.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/quickSort.hpp"

const G1SegmentedArrayAllocOptions G1EvacFailureObjectsSet::_alloc_options =
  G1SegmentedArrayAllocOptions((uint)sizeof(OffsetInRegion), BufferLength, UINT_MAX, Alignment);

G1SegmentedArrayBufferList<mtGC> G1EvacFailureObjectsSet::_free_buffer_list;

#ifdef ASSERT
void G1EvacFailureObjectsSet::assert_is_valid_offset(size_t offset) const {
  const uint max_offset = 1u << (HeapRegion::LogOfHRGrainBytes - LogHeapWordSize);
  assert(offset < max_offset, "must be, but is " SIZE_FORMAT, offset);
}
#endif

oop G1EvacFailureObjectsSet::from_offset(OffsetInRegion offset) const {
  assert_is_valid_offset(offset);
  return cast_to_oop(_bottom + offset);
}

G1EvacFailureObjectsSet::OffsetInRegion G1EvacFailureObjectsSet::to_offset(oop obj) const {
  const HeapWord* o = cast_from_oop<const HeapWord*>(obj);
  size_t offset = pointer_delta(o, _bottom);
  assert(obj == from_offset(static_cast<OffsetInRegion>(offset)), "must be");
  return static_cast<OffsetInRegion>(offset);
}

G1EvacFailureObjectsSet::G1EvacFailureObjectsSet(uint region_idx, HeapWord* bottom) :
  _region_idx(region_idx),
  _bottom(bottom),
  _offsets(&_alloc_options, &_free_buffer_list),
  _helper(this, _region_idx),
  _word_size(0) {
  assert(HeapRegion::LogOfHRGrainBytes < 32, "must be");
}

int G1EvacFailureObjectsSet::G1EvacFailureObjectsIterationHelper::order_oop(OffsetInRegion a, OffsetInRegion b) {
  return static_cast<int>(a-b);
}

void G1EvacFailureObjectsSet::G1EvacFailureObjectsIterationHelper::join_and_sort() {
  _segments->iterate_nodes(*this);

  QuickSort::sort(_offset_array, _array_length, order_oop, true);
}

HeapWord* G1EvacFailureObjectsSet::G1EvacFailureObjectsIterationHelper::previous_object_end(HeapRegion* region, uint start_idx) {
  if (start_idx == 0) {
    return region->bottom();
  }
  oop obj = _objects_set->from_offset(_offset_array[start_idx - 1]);
  HeapWord* obj_addr = cast_from_oop<HeapWord*>(obj);
  size_t obj_size = obj->size();
  HeapWord* obj_end = obj_addr + obj_size;
  return obj_end;
}

// TODO: fix, make it const or configurable as vm option.
static const uint TASK_LIMIT = 1000;

void G1EvacFailureObjectsSet::G1EvacFailureObjectsIterationHelper::insert_queue(G1EvacFailureParScanTasksQueue* queue) {
  assert(_array_length > 0, "must be");
  uint i = 1;
  uint start_idx = -1;
  HeapWord* prev_end = nullptr;
  HeapRegion* region = G1CollectedHeap::heap()->region_at(_region_idx);
  for (; i*TASK_LIMIT < _array_length; i++) {
    start_idx = (i-1)*TASK_LIMIT;
    prev_end = previous_object_end(region, start_idx);
    queue->push(G1EvacFailureParScanTask(region, prev_end, start_idx, i * TASK_LIMIT));
  }
  start_idx = (i-1)*TASK_LIMIT;
  prev_end = previous_object_end(region, start_idx);
  queue->push(G1EvacFailureParScanTask(region, prev_end, start_idx, _array_length, true));
}

G1EvacFailureObjectsSet::G1EvacFailureObjectsIterationHelper::G1EvacFailureObjectsIterationHelper(G1EvacFailureObjectsSet* collector, uint region_idx) :
  _objects_set(collector),
  _segments(&_objects_set->_offsets),
  _offset_array(nullptr),
  _array_length(0),
  _region_idx(region_idx) { }

void G1EvacFailureObjectsSet::G1EvacFailureObjectsIterationHelper::prepare(G1EvacFailureParScanTasksQueue* queue) {
  uint num = _segments->num_allocated_nodes();
  _offset_array = NEW_C_HEAP_ARRAY(OffsetInRegion, num, mtGC);

  join_and_sort();
  assert(_array_length == num, "must be %u, %u", _array_length, num);

  insert_queue(queue);
}

void G1EvacFailureObjectsSet::G1EvacFailureObjectsIterationHelper::iterate(ObjectClosure* closure, G1EvacFailureParScanTask& task) {
  assert(_region_idx == task.region()->hrm_index(), "must be");
  for (uint i = task.start(); i < task.end(); i++) {
    oop cur = _objects_set->from_offset(_offset_array[i]);
    closure->do_object(cur);
  }
}

void G1EvacFailureObjectsSet::G1EvacFailureObjectsIterationHelper::reset() {
  FREE_C_HEAP_ARRAY(OffsetInRegion, _offset_array);
  _offset_array = nullptr;
  _array_length = 0;
}

// Callback of G1SegmentedArray::iterate_nodes
void G1EvacFailureObjectsSet::G1EvacFailureObjectsIterationHelper::do_buffer(G1SegmentedArrayBuffer<mtGC>* node, uint length) {
  node->copy_to(&_offset_array[_array_length]);
  _array_length += length;
}

size_t G1EvacFailureObjectsSet::pre_iteration(G1EvacFailureParScanTasksQueue* queue) {
  assert_at_safepoint();

  _helper.prepare(queue);

  return Atomic::load(&_word_size) * HeapWordSize;
}

void G1EvacFailureObjectsSet::iterate(ObjectClosure* closure, G1EvacFailureParScanTask& task) {
  _helper.iterate(closure, task);
}

void G1EvacFailureObjectsSet::post_iteration() {
  _helper.reset();
  _offsets.drop_all();
  Atomic::store(&_word_size, size_t(0));
}
