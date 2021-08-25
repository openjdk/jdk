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
#include "g1EvacuationFailureObjsInHR.hpp"
#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/heapRegion.hpp"
#include "gc/g1/heapRegion.inline.hpp"
#include "utilities/quickSort.hpp"



// === G1EvacuationFailureObjsInHR ===

void G1EvacuationFailureObjsInHR::visit(Elem elem) {
  uint32_t offset = elem;
  _offset_array[_objs_num++] = offset;
}

void G1EvacuationFailureObjsInHR::visit(Array<NODE_LENGTH, Elem>::NODE_XXX* node, uint32_t limit) {
  ::memcpy(&_offset_array[_objs_num], node->_oop_offsets, limit * sizeof(Elem));
  _objs_num += limit;
}

void G1EvacuationFailureObjsInHR::compact() {
  assert(_offset_array == NULL, "must be");
  _offset_array = NEW_C_HEAP_ARRAY(Elem, _nodes_array.objs_num(), mtGC);
  // _nodes_array.iterate_elements(this);
  _nodes_array.iterate_nodes(this);
  uint expected = _nodes_array.objs_num();
  assert(_objs_num == expected, "must be %u, %u", _objs_num, expected);
  _nodes_array.reset();
}

static int32_t order_oop(G1EvacuationFailureObjsInHR::Elem a,
                         G1EvacuationFailureObjsInHR::Elem b) {
  // assert(a != b, "must be");
  int r = a-b;
  return r;
}

void G1EvacuationFailureObjsInHR::sort() {
  QuickSort::sort(_offset_array, _objs_num, order_oop, true);
}

void G1EvacuationFailureObjsInHR::clear_array() {
  FREE_C_HEAP_ARRAY(oop, _offset_array);
  _offset_array = NULL;
  _objs_num = 0;
}

void G1EvacuationFailureObjsInHR::iterate_internal(ObjectClosure* closure) {
  Elem prev = 0;
  for (uint i = 0; i < _objs_num; i++) {
    assert(prev < _offset_array[i], "must be");
    closure->do_object(cast_from_offset(prev = _offset_array[i]));
  }
  clear_array();
}

G1EvacuationFailureObjsInHR::G1EvacuationFailureObjsInHR(uint region_idx, HeapWord* bottom) :
  offset_mask((1l << HeapRegion::LogOfHRGrainBytes) - 1),
  _region_idx(region_idx),
  _bottom(bottom),
  _nodes_array(HeapRegion::GrainWords / NODE_LENGTH + 1),
  _offset_array(NULL),
  _objs_num(0) {
}

G1EvacuationFailureObjsInHR::~G1EvacuationFailureObjsInHR() {
  clear_array();
}

void G1EvacuationFailureObjsInHR::record(oop obj) {
  assert(obj != NULL, "must be");
  assert(G1CollectedHeap::heap()->heap_region_containing(obj)->hrm_index() == _region_idx, "must be");
  Elem offset = cast_from_oop_addr(obj);
  assert(obj == cast_from_offset(offset), "must be");
  assert(offset < (1<<25), "must be");
  _nodes_array.add(offset);
}

void G1EvacuationFailureObjsInHR::iterate(ObjectClosure* closure) {
  compact();
  sort();
  iterate_internal(closure);
}
