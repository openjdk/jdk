/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/thread.inline.hpp"
#include "utilities/growableArray.hpp"

#ifdef ASSERT
void GenericGrowableArray::set_nesting() {
  if (on_stack()) {
    _nesting = Thread::current()->resource_area()->nesting();
  }
}

void GenericGrowableArray::check_nesting() {
  // Check for insidious allocation bug: if a GrowableArray overflows, the
  // grown array must be allocated under the same ResourceMark as the original.
  // Otherwise, the _data array will be deallocated too early.
  if (on_stack() &&
      _nesting != Thread::current()->resource_area()->nesting()) {
    fatal("allocation bug: GrowableArray could grow within nested ResourceMark");
  }
}
#endif

void* GenericGrowableArray::raw_allocate(int elementSize) {
  assert(_max >= 0, "integer overflow");
  size_t byte_size = elementSize * (size_t) _max;
  if (on_stack()) {
    return (void*)resource_allocate_bytes(byte_size);
  } else if (on_C_heap()) {
    return (void*)AllocateHeap(byte_size, _memflags);
  } else {
    return _arena->Amalloc(byte_size);
  }
}

void GenericGrowableArray::free_C_heap(void* elements) {
  FreeHeap(elements);
}
