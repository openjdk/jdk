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

#include "gc/g1/g1EvacFailureParScanTask.hpp"
#include "gc/g1/g1SegmentedArray.hpp"
#include "memory/iterator.hpp"
#include "oops/oop.hpp"
#include "runtime/atomic.hpp"

class HeapRegion;

// This class collects addresses of objects that failed evacuation in a specific
// heap region.
// Provides sorted iteration of these elements for processing during the remove
// self forwards phase.
class G1EvacFailureObjectsSet {
  // Storage type of an object that failed evacuation within a region. Given
  // heap region size and possible object locations within a region, it is
  // sufficient to use an uint here to save some space instead of full pointers.
  typedef uint OffsetInRegion;

  // Helper class to join, sort and iterate over the previously collected segmented
  // array of objects that failed evacuation.
  class G1EvacFailureObjectsIterationHelper {
    typedef G1EvacFailureObjectsSet::OffsetInRegion OffsetInRegion;

    static const uint TASK_LIMIT = 1000;

    G1EvacFailureObjectsSet* _objects_set;
    const G1SegmentedArray<OffsetInRegion, mtGC>* _segments;
    OffsetInRegion* _offset_array;
    uint _array_length;
    uint _region_idx;

    static int order_oop(OffsetInRegion a, OffsetInRegion b);

    void join_and_sort();

    HeapWord* previous_object_end(HeapRegion* region, uint start_idx);

    void insert_queue(G1EvacFailureParScanTasksQueue* queue);

  public:
    G1EvacFailureObjectsIterationHelper(G1EvacFailureObjectsSet* collector, uint region_idx);

    void prepare(G1EvacFailureParScanTasksQueue* queue);
    void iterate(ObjectClosure* closure, G1EvacFailureParScanTask& task);
    void reset();

    // Callback of G1SegmentedArray::iterate_nodes
    void do_buffer(G1SegmentedArrayBuffer<mtGC>* node, uint length);

  };

  static const uint BufferLength = 256;
  static const uint Alignment = 4;

  static const G1SegmentedArrayAllocOptions _alloc_options;

  // This free list is shared among evacuation failure process in all regions.
  static G1SegmentedArrayBufferList<mtGC> _free_buffer_list;

  const uint _region_idx;

  // Region bottom
  const HeapWord* _bottom;

  // Offsets within region containing objects that failed evacuation.
  G1SegmentedArray<OffsetInRegion, mtGC> _offsets;

  G1EvacFailureObjectsIterationHelper _helper;

  // Live words in the evacuation failure region.
  volatile size_t _word_size;

  void assert_is_valid_offset(size_t offset) const NOT_DEBUG_RETURN;
  // Converts between an offset within a region and an oop address.
  oop from_offset(OffsetInRegion offset) const;
  OffsetInRegion to_offset(oop obj) const;

public:
  G1EvacFailureObjectsSet(uint region_idx, HeapWord* bottom);

  // Record an object that failed evacuation.
  inline void record(oop obj, size_t word_size);

  // Prepare parallel iteration by building and sorting list of evacuation
  // failure objects, and constructing parallelizable tasks.
  // Return live bytes in the evacuation failure region.
  size_t pre_iteration(G1EvacFailureParScanTasksQueue* queue);
  // Apply the given ObjectClosure to all previously recorded objects in the task
  // that failed evacuation in ascending address order.
  void iterate(ObjectClosure* closure, G1EvacFailureParScanTask& task);
  // Empty the list of evacuation failure objects.
  // Reset live words in the evacuation failure region.
  void post_iteration();
};


#endif //SHARE_GC_G1_G1EVACUATIONFAILUREOBJSINHR_HPP
