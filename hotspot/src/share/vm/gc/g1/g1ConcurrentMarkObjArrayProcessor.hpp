/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_G1CONCURRENTMARKOBJARRAYPROCESSOR_HPP
#define SHARE_VM_GC_G1_G1CONCURRENTMARKOBJARRAYPROCESSOR_HPP

#include "oops/oopsHierarchy.hpp"
#include "memory/allocation.hpp"

class G1CMTask;

// Helper class to mark through large objArrays during marking in an efficient way.
// Instead of pushing large object arrays, we push continuations onto the
// mark stack. These continuations are identified by having their LSB set.
// This allows incremental processing of large objects.
class G1CMObjArrayProcessor VALUE_OBJ_CLASS_SPEC {
private:
  // The bit mask for the continuation indicator of elements on the mark stack.
  static const size_t ArraySliceBit = 1;

  // Reference to the task for doing the actual work.
  G1CMTask* _task;

  // Encodes the given address as a continuation "oop".
  oop encode_array_slice(HeapWord* addr);
  // Remove the continuation marker from the given oop from the mark stack.
  HeapWord* decode_array_slice(oop value);

  // Push the continuation at the given address onto the mark stack.
  void push_array_slice(HeapWord* addr);

  // Process (apply the closure) on the given continuation of the given objArray.
  size_t process_array_slice(objArrayOop const obj, HeapWord* start_from, size_t remaining);
public:
  static bool is_array_slice(void* obj) { return ((uintptr_t)obj & ArraySliceBit) != 0; }

  static bool should_be_sliced(oop obj);

  G1CMObjArrayProcessor(G1CMTask* task) : _task(task) {
  }

  // Process the given continuation "oop". Returns the number of words scanned.
  size_t process_slice(oop obj);
  // Start processing the given objArrayOop by scanning the header and pushing its
  // continuation.
  size_t process_obj(oop obj);
};

#endif /* SHARE_VM_GC_G1_G1CONCURRENTMARKOBJARRAYPROCESSOR_HPP */
