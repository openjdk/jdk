/*
 * Copyright 1997-2005 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */
# include "incls/_precompiled.incl"
# include "incls/_growableArray.cpp.incl"

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
    return (void*)AllocateHeap(byte_size, "GrET in " __FILE__);
  } else {
    return _arena->Amalloc(byte_size);
  }
}
