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
#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/g1SegmentedArray.inline.hpp"
#include "gc/g1/heapRegion.hpp"
#include "gc/g1/heapRegion.inline.hpp"
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

G1EvacFailureObjectsSet::OffsetInRegion G1EvacFailureObjectsSet::cast_to_offset(oop obj) const {
  const HeapWord* o = cast_from_oop<const HeapWord*>(obj);
  size_t offset = pointer_delta(o, _bottom);
  assert_is_valid_offset(offset);
  assert(obj == from_offset(static_cast<OffsetInRegion>(offset)), "must be");
  return static_cast<OffsetInRegion>(offset);
}

G1EvacFailureObjectsSet::G1EvacFailureObjectsSet(uint region_idx, HeapWord* bottom) :
  DEBUG_ONLY(_region_idx(region_idx) COMMA)
  _bottom(bottom),
  _offsets("", &_alloc_options, &_free_buffer_list)  {
  assert(HeapRegion::LogOfHRGrainBytes < 32, "must be");
}

void G1EvacFailureObjectsSet::record(oop obj) {
  assert(obj != NULL, "must be");
  assert(_region_idx == G1CollectedHeap::heap()->heap_region_containing(obj)->hrm_index(), "must be");
  OffsetInRegion* e = _offsets.allocate();
  *e = cast_to_offset(obj);
}

// Helper class to join, sort and iterate over the previously collected segmented
// array of objects that failed evacuation.
class G1EvacFailureObjectsIterator {
  typedef G1EvacFailureObjectsSet::OffsetInRegion OffsetInRegion;
  friend class G1SegmentedArray<OffsetInRegion, mtGC>;
  friend class G1SegmentedArrayBuffer<mtGC>;

  G1EvacFailureObjectsSet* _collector;
  const G1SegmentedArray<OffsetInRegion, mtGC>* _segments;
  OffsetInRegion* _offset_array;
  uint _array_length;

  static int order_oop(OffsetInRegion a, OffsetInRegion b) {
    return static_cast<int>(a-b);
  }

  void join_and_sort() {
    uint num = _segments->num_allocated_nodes();
    _offset_array = NEW_C_HEAP_ARRAY(OffsetInRegion, num, mtGC);

    _segments->iterate_nodes(*this);
    assert(_array_length == num, "must be %u, %u", _array_length, num);

    QuickSort::sort(_offset_array, _array_length, order_oop, true);
  }

  void iterate_internal(ObjectClosure* closure) {
    for (uint i = 0; i < _array_length; i++) {
      _collector->assert_is_valid_offset(_offset_array[i]);
      oop cur = _collector->from_offset(_offset_array[i]);
      closure->do_object(cur);
    }

    FREE_C_HEAP_ARRAY(OffsetInRegion, _offset_array);
  }

  // Callback of G1SegmentedArray::iterate_nodes
  void visit_buffer(G1SegmentedArrayBuffer<mtGC>* node, uint length) {
    node->copy_to(&_offset_array[_array_length]);
    _array_length += length;

    // Verify elements in the node
    DEBUG_ONLY(node->iterate_elems(*this));
  }

#ifdef ASSERT
  // Callback of G1SegmentedArrayBuffer::iterate_elems
  // Verify a single element in a segment node
  void visit_elem(void* elem) {
    uint* ptr = (uint*)elem;
    _collector->assert_is_valid_offset(*ptr);
  }
#endif

public:
  G1EvacFailureObjectsIterator(G1EvacFailureObjectsSet* collector) :
    _collector(collector),
    _segments(&_collector->_offsets),
    _offset_array(nullptr),
    _array_length(0) { }

  ~G1EvacFailureObjectsIterator() { }

  void iterate(ObjectClosure* closure) {
    join_and_sort();
    iterate_internal(closure);
  }
};

void G1EvacFailureObjectsSet::iterate(ObjectClosure* closure) {
  assert_at_safepoint();

  G1EvacFailureObjectsIterator iterator(this);
  iterator.iterate(closure);

  _offsets.drop_all();
}
