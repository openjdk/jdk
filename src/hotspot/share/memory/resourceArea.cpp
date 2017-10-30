/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/mutexLocker.hpp"
#include "runtime/thread.inline.hpp"
#include "services/memTracker.hpp"

void ResourceArea::bias_to(MEMFLAGS new_flags) {
  if (new_flags != _flags) {
    MemTracker::record_arena_free(_flags);
    MemTracker::record_new_arena(new_flags);
    _flags = new_flags;
  }
}

//------------------------------ResourceMark-----------------------------------
debug_only(int ResourceArea::_warned;)      // to suppress multiple warnings

// The following routines are declared in allocation.hpp and used everywhere:

// Allocation in thread-local resource area
extern char* resource_allocate_bytes(size_t size, AllocFailType alloc_failmode) {
  return Thread::current()->resource_area()->allocate_bytes(size, alloc_failmode);
}
extern char* resource_allocate_bytes(Thread* thread, size_t size, AllocFailType alloc_failmode) {
  return thread->resource_area()->allocate_bytes(size, alloc_failmode);
}

extern char* resource_reallocate_bytes( char *old, size_t old_size, size_t new_size, AllocFailType alloc_failmode){
  return (char*)Thread::current()->resource_area()->Arealloc(old, old_size, new_size, alloc_failmode);
}

extern void resource_free_bytes( char *old, size_t size ) {
  Thread::current()->resource_area()->Afree(old, size);
}

#ifdef ASSERT
ResourceMark::ResourceMark(Thread *thread) {
  assert(thread == Thread::current(), "not the current thread");
  initialize(thread);
}

DeoptResourceMark::DeoptResourceMark(Thread *thread) {
  assert(thread == Thread::current(), "not the current thread");
  initialize(thread);
}
#endif


//-------------------------------------------------------------------------------
// Non-product code
#ifndef PRODUCT

void ResourceMark::free_malloced_objects() {
  Arena::free_malloced_objects(_chunk, _hwm, _max, _area->_hwm);
}

void DeoptResourceMark::free_malloced_objects() {
  Arena::free_malloced_objects(_chunk, _hwm, _max, _area->_hwm);
}

#endif
