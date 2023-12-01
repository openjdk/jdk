/*
 * Copyright (c) 1997, 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/thread.hpp"
#include "utilities/growableArray.hpp"

void* GrowableArrayArenaAllocator::allocate(int max, int element_size, Arena* arena) {
  assert(max >= 0, "integer overflow");
  size_t byte_size = element_size * (size_t) max;

  return arena->Amalloc(byte_size);
}

void* GrowableArrayCHeapAllocator::allocate(int max, int element_size, MEMFLAGS memflags) {
  assert(max >= 0, "integer overflow");
  size_t byte_size = element_size * (size_t) max;

  // memory type has to be specified for C heap allocation
  assert(memflags != mtNone, "memory type not specified for C heap object");
  return (void*)AllocateHeap(byte_size, memflags);
}

void GrowableArrayCHeapAllocator::deallocate(void* elements) {
  FreeHeap(elements);
}

#ifdef ASSERT
GrowableArrayNestingCheck::GrowableArrayNestingCheck(bool on_resource_area) :
    _nesting(on_resource_area ? Thread::current()->resource_area()->nesting() : 0),
    _on_resource_area(on_resource_area) {}

void GrowableArrayNestingCheck::on_allocate() const {
  // Check for insidious allocation bug: if a GrowableArray overflows, the
  // grown array must be allocated under the same ResourceMark as the original.
  // Otherwise, the _data array will be deallocated too early.
  if (_on_resource_area &&
      _nesting != Thread::current()->resource_area()->nesting()) {
    fatal("allocation bug: GrowableArray could grow within nested ResourceMark");
  }
}
#endif // ASSERT
