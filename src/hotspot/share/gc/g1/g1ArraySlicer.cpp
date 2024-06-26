/*
 * Copyright (c) 2016, 2024, Red Hat, Inc. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/g1/g1ArraySlicer.hpp"
#include "gc/g1/g1TaskQueueEntry.hpp"
#include "gc/shared/gc_globals.hpp"
#include "oops/objArrayOop.hpp"
#include "oops/oop.inline.hpp"

size_t G1ArraySlicer::process_objArray(oop obj) {
  assert(obj->is_objArray(), "precondition");

  objArrayOop array = objArrayOop(obj);
  int len = array->length();

  // Mark objArray klass metadata
  scan_metadata(array);

  if (len <= (int)ObjArrayMarkingStride * 2) {
    return scan_array(array, 0, len);
  }

  int bits = log2i_graceful(len);
  // Compensate for non-power-of-two arrays, cover the array in excess:
  if (len != (1 << bits)) bits++;

  // Only allow full slices on the queue. This frees do_sliced_array() from checking from/to
  // boundaries against array->length(), touching the array header on every slice.
  //
  // To do this, we cut the prefix in full-sized slices, and submit them on the queue.
  // If the array is not divided in slice sizes, then there would be an irregular tail,
  // which we will process separately.

  int last_idx = 0;

  int slice = 1;
  int pow = bits;

  // Handle overflow
  if (pow >= 31) {
    assert (pow == 31, "sanity");
    pow--;
    slice = 2;
    last_idx = (1 << pow);
    push_on_queue(G1TaskQueueEntry(array, 1, pow));
  }

  // Split out tasks, as suggested in G1TaskQueueEntry docs. Record the last
  // successful right boundary to figure out the irregular tail.
  while ((1 << pow) > (int)ObjArrayMarkingStride &&
         (slice * 2 < G1TaskQueueEntry::slice_size())) {
    pow--;
    int left_slice = slice * 2 - 1;
    int right_slice = slice * 2;
    int left_slice_end = left_slice * (1 << pow);
    if (left_slice_end < len) {
      push_on_queue(G1TaskQueueEntry(array, left_slice, pow));
      slice = right_slice;
      last_idx = left_slice_end;
    } else {
      slice = left_slice;
    }
  }

  // Process the irregular tail, if present
  int from = last_idx;
  if (from < len) {
    return scan_array(array, from, len);
  }
  return 0;
}

size_t G1ArraySlicer::process_slice(objArrayOop array, int slice, int pow) {
  assert (ObjArrayMarkingStride > 0, "sanity");

  // Split out tasks, as suggested in G1TaskQueueEntry docs. Avoid pushing tasks that
  // are known to start beyond the array.
  while ((1 << pow) > (int)ObjArrayMarkingStride && (slice * 2 < G1TaskQueueEntry::slice_size())) {
    pow--;
    slice *= 2;
    push_on_queue(G1TaskQueueEntry(array, slice - 1, pow));
  }

  int slice_size = 1 << pow;

  int from = (slice - 1) * slice_size;
  int to = slice * slice_size;

#ifdef ASSERT
  int len = array->length();
  assert (0 <= from && from < len, "from is sane: %d/%d", from, len);
  assert (0 < to && to <= len, "to is sane: %d/%d", to, len);
#endif

  return scan_array(array, from, to);
}
