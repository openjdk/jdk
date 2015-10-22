/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "oops/constantPool.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/thread.inline.hpp"

#ifdef ASSERT
oop* HandleArea::allocate_handle(oop obj) {
  assert(_handle_mark_nesting > 1, "memory leak: allocating handle outside HandleMark");
  assert(_no_handle_mark_nesting == 0, "allocating handle inside NoHandleMark");
  assert(obj->is_oop(), "not an oop: " INTPTR_FORMAT, p2i(obj));
  return real_allocate_handle(obj);
}

Handle::Handle(Thread* thread, oop obj) {
  assert(thread == Thread::current(), "sanity check");
  if (obj == NULL) {
    _handle = NULL;
  } else {
    _handle = thread->handle_area()->allocate_handle(obj);
  }
}

#endif

static uintx chunk_oops_do(OopClosure* f, Chunk* chunk, char* chunk_top) {
  oop* bottom = (oop*) chunk->bottom();
  oop* top    = (oop*) chunk_top;
  uintx handles_visited = top - bottom;
  assert(top >= bottom && top <= (oop*) chunk->top(), "just checking");
  // during GC phase 3, a handle may be a forward pointer that
  // is not yet valid, so loosen the assertion
  while (bottom < top) {
    // This test can be moved up but for now check every oop.

    assert((*bottom)->is_oop(), "handle should point to oop");

    f->do_oop(bottom++);
  }
  return handles_visited;
}

// Used for debugging handle allocation.
NOT_PRODUCT(jint _nof_handlemarks  = 0;)

void HandleArea::oops_do(OopClosure* f) {
  uintx handles_visited = 0;
  // First handle the current chunk. It is filled to the high water mark.
  handles_visited += chunk_oops_do(f, _chunk, _hwm);
  // Then handle all previous chunks. They are completely filled.
  Chunk* k = _first;
  while(k != _chunk) {
    handles_visited += chunk_oops_do(f, k, k->top());
    k = k->next();
  }

  // The thread local handle areas should not get very large
  if (TraceHandleAllocation && (size_t)handles_visited > TotalHandleAllocationLimit) {
#ifdef ASSERT
    warning("%d: Visited in HandleMark : " SIZE_FORMAT, _nof_handlemarks, handles_visited);
#else
    warning("Visited in HandleMark : " SIZE_FORMAT, handles_visited);
#endif
  }
  if (_prev != NULL) _prev->oops_do(f);
}

void HandleMark::initialize(Thread* thread) {
  _thread = thread;
  // Save area
  _area  = thread->handle_area();
  // Save current top
  _chunk = _area->_chunk;
  _hwm   = _area->_hwm;
  _max   = _area->_max;
  _size_in_bytes = _area->_size_in_bytes;
  debug_only(_area->_handle_mark_nesting++);
  assert(_area->_handle_mark_nesting > 0, "must stack allocate HandleMarks");
  debug_only(Atomic::inc(&_nof_handlemarks);)

  // Link this in the thread
  set_previous_handle_mark(thread->last_handle_mark());
  thread->set_last_handle_mark(this);
}


HandleMark::~HandleMark() {
  HandleArea* area = _area;   // help compilers with poor alias analysis
  assert(area == _thread->handle_area(), "sanity check");
  assert(area->_handle_mark_nesting > 0, "must stack allocate HandleMarks" );
  debug_only(area->_handle_mark_nesting--);

  // Debug code to trace the number of handles allocated per mark/
#ifdef ASSERT
  if (TraceHandleAllocation) {
    size_t handles = 0;
    Chunk *c = _chunk->next();
    if (c == NULL) {
      handles = area->_hwm - _hwm; // no new chunk allocated
    } else {
      handles = _max - _hwm;      // add rest in first chunk
      while(c != NULL) {
        handles += c->length();
        c = c->next();
      }
      handles -= area->_max - area->_hwm; // adjust for last trunk not full
    }
    handles /= sizeof(void *); // Adjust for size of a handle
    if (handles > HandleAllocationLimit) {
      // Note: _nof_handlemarks is only set in debug mode
      warning("%d: Allocated in HandleMark : " SIZE_FORMAT, _nof_handlemarks, handles);
    }

    tty->print_cr("Handles " SIZE_FORMAT, handles);
  }
#endif

  // Delete later chunks
  if( _chunk->next() ) {
    // reset arena size before delete chunks. Otherwise, the total
    // arena size could exceed total chunk size
    assert(area->size_in_bytes() > size_in_bytes(), "Sanity check");
    area->set_size_in_bytes(size_in_bytes());
    _chunk->next_chop();
  } else {
    assert(area->size_in_bytes() == size_in_bytes(), "Sanity check");
  }
  // Roll back arena to saved top markers
  area->_chunk = _chunk;
  area->_hwm = _hwm;
  area->_max = _max;
#ifdef ASSERT
  // clear out first chunk (to detect allocation bugs)
  if (ZapVMHandleArea) {
    memset(_hwm, badHandleValue, _max - _hwm);
  }
  Atomic::dec(&_nof_handlemarks);
#endif

  // Unlink this from the thread
  _thread->set_last_handle_mark(previous_handle_mark());
}

void* HandleMark::operator new(size_t size) throw() {
  return AllocateHeap(size, mtThread);
}

void* HandleMark::operator new [] (size_t size) throw() {
  return AllocateHeap(size, mtThread);
}

void HandleMark::operator delete(void* p) {
  FreeHeap(p);
}

void HandleMark::operator delete[](void* p) {
  FreeHeap(p);
}

#ifdef ASSERT

NoHandleMark::NoHandleMark() {
  HandleArea* area = Thread::current()->handle_area();
  area->_no_handle_mark_nesting++;
  assert(area->_no_handle_mark_nesting > 0, "must stack allocate NoHandleMark" );
}


NoHandleMark::~NoHandleMark() {
  HandleArea* area = Thread::current()->handle_area();
  assert(area->_no_handle_mark_nesting > 0, "must stack allocate NoHandleMark" );
  area->_no_handle_mark_nesting--;
}


ResetNoHandleMark::ResetNoHandleMark() {
  HandleArea* area = Thread::current()->handle_area();
  _no_handle_mark_nesting = area->_no_handle_mark_nesting;
  area->_no_handle_mark_nesting = 0;
}


ResetNoHandleMark::~ResetNoHandleMark() {
  HandleArea* area = Thread::current()->handle_area();
  area->_no_handle_mark_nesting = _no_handle_mark_nesting;
}

bool instanceKlassHandle::is_instanceKlass(const Klass* k) {
  return k->oop_is_instance();
}

#endif
