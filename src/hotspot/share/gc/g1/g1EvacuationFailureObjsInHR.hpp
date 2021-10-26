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

#ifndef SHARE_GC_G1_G1EVACUATIONFAILUREOBJSINHR_HPP
#define SHARE_GC_G1_G1EVACUATIONFAILUREOBJSINHR_HPP

#include "gc/g1/g1SegmentedArray.hpp"
#include "memory/iterator.hpp"
#include "oops/oop.hpp"

// This class
//   1. records the objects per region which have failed to evacuate.
//   2. speeds up removing self forwarded ptrs in post evacuation phase.
//
class G1EvacuationFailureObjsInHR {

public:
  typedef uint Elem;

private:
  static const uint BufferLength = 256;
  static const uint MaxBufferLength;
  static const uint Alignment = 4;

  static const G1SegmentedArrayAllocOptions _alloc_options;
  // This free list is shared among evacuation failure process in all regions.
  static G1SegmentedArrayBufferList<mtGC> _free_buffer_list;

  const Elem _max_offset;
  const uint _region_idx;
  const HeapWord* _bottom;

  // To improve space efficiency, elements are offset rather than raw addr
  G1SegmentedArray<Elem, mtGC> _nodes_array;
  // Local array contains the _nodes_array data in flat layout
  Elem* _offset_array;
  uint _objs_num;

private:
  oop cast_from_offset(Elem offset) {
    return cast_to_oop(_bottom + offset);
  }
  Elem cast_from_oop_addr(oop obj) {
    const HeapWord* o = cast_from_oop<const HeapWord*>(obj);
    size_t offset = pointer_delta(o, _bottom);
    return static_cast<Elem>(offset);
  }

  // Copy buffers' data to local array, must be called at safepoint
  void compact();
  void sort();
  void clear_array();
  // Iterate through evac failure objects in local array
  void iterate_internal(ObjectClosure* closure);

public:
  G1EvacuationFailureObjsInHR(uint region_idx, HeapWord* bottom);
  ~G1EvacuationFailureObjsInHR();

  // Record an evac failure object
  void record(oop obj);
  // Iterate through evac failure objects
  void iterate(ObjectClosure* closure);

  // Copy a buffer data to local array
  void visit_buffer(G1SegmentedArrayBuffer<mtGC>* node, uint limit);

  // Verify elements in the buffer
  void visit_elem(void* elem);
};


#endif //SHARE_GC_G1_G1EVACUATIONFAILUREOBJSINHR_HPP
