/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_MEMORY_RESOURCEAREA_HPP
#define SHARE_VM_MEMORY_RESOURCEAREA_HPP

#include "memory/allocation.hpp"
#ifdef TARGET_OS_FAMILY_linux
# include "thread_linux.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_solaris
# include "thread_solaris.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_windows
# include "thread_windows.inline.hpp"
#endif

// The resource area holds temporary data structures in the VM.
// The actual allocation areas are thread local. Typical usage:
//
//   ...
//   {
//     ResourceMark rm;
//     int foo[] = NEW_RESOURCE_ARRAY(int, 64);
//     ...
//   }
//   ...

//------------------------------ResourceArea-----------------------------------
// A ResourceArea is an Arena that supports safe usage of ResourceMark.
class ResourceArea: public Arena {
  friend class ResourceMark;
  friend class DeoptResourceMark;
  debug_only(int _nesting;)             // current # of nested ResourceMarks
  debug_only(static int _warned;)       // to suppress multiple warnings

public:
  ResourceArea() {
    debug_only(_nesting = 0;)
  }

  ResourceArea(size_t init_size) : Arena(init_size) {
    debug_only(_nesting = 0;);
  }

  char* allocate_bytes(size_t size) {
#ifdef ASSERT
    if (_nesting < 1 && !_warned++)
      fatal("memory leak: allocating without ResourceMark");
    if (UseMallocOnly) {
      // use malloc, but save pointer in res. area for later freeing
      char** save = (char**)internal_malloc_4(sizeof(char*));
      return (*save = (char*)os::malloc(size));
    }
#endif
    return (char*)Amalloc(size);
  }

  debug_only(int nesting() const { return _nesting; });
};


//------------------------------ResourceMark-----------------------------------
// A resource mark releases all resources allocated after it was constructed
// when the destructor is called.  Typically used as a local variable.
class ResourceMark: public StackObj {
protected:
  ResourceArea *_area;          // Resource area to stack allocate
  Chunk *_chunk;                // saved arena chunk
  char *_hwm, *_max;
  NOT_PRODUCT(size_t _size_in_bytes;)

  void initialize(Thread *thread) {
    _area = thread->resource_area();
    _chunk = _area->_chunk;
    _hwm = _area->_hwm;
    _max= _area->_max;
    NOT_PRODUCT(_size_in_bytes = _area->size_in_bytes();)
    debug_only(_area->_nesting++;)
    assert( _area->_nesting > 0, "must stack allocate RMs" );
  }

 public:

#ifndef ASSERT
  ResourceMark(Thread *thread) {
    assert(thread == Thread::current(), "not the current thread");
    initialize(thread);
  }
#else
  ResourceMark(Thread *thread);
#endif // ASSERT

  ResourceMark()               { initialize(Thread::current()); }

  ResourceMark( ResourceArea *r ) :
    _area(r), _chunk(r->_chunk), _hwm(r->_hwm), _max(r->_max) {
    NOT_PRODUCT(_size_in_bytes = _area->size_in_bytes();)
    debug_only(_area->_nesting++;)
    assert( _area->_nesting > 0, "must stack allocate RMs" );
  }

  void reset_to_mark() {
    if (UseMallocOnly) free_malloced_objects();

    if( _chunk->next() )        // Delete later chunks
      _chunk->next_chop();
    _area->_chunk = _chunk;     // Roll back arena to saved chunk
    _area->_hwm = _hwm;
    _area->_max = _max;

    // clear out this chunk (to detect allocation bugs)
    if (ZapResourceArea) memset(_hwm, badResourceValue, _max - _hwm);
    _area->set_size_in_bytes(size_in_bytes());
  }

  ~ResourceMark() {
    assert( _area->_nesting > 0, "must stack allocate RMs" );
    debug_only(_area->_nesting--;)
    reset_to_mark();
  }


 private:
  void free_malloced_objects()                                         PRODUCT_RETURN;
  size_t size_in_bytes()       NOT_PRODUCT({ return _size_in_bytes; }) PRODUCT_RETURN0;
};

//------------------------------DeoptResourceMark-----------------------------------
// A deopt resource mark releases all resources allocated after it was constructed
// when the destructor is called.  Typically used as a local variable. It differs
// from a typical resource more in that it is C-Heap allocated so that deoptimization
// can use data structures that are arena based but are not amenable to vanilla
// ResourceMarks because deoptimization can not use a stack allocated mark. During
// deoptimization we go thru the following steps:
//
// 0: start in assembly stub and call either uncommon_trap/fetch_unroll_info
// 1: create the vframeArray (contains pointers to Resource allocated structures)
//   This allocates the DeoptResourceMark.
// 2: return to assembly stub and remove stub frame and deoptee frame and create
//    the new skeletal frames.
// 3: push new stub frame and call unpack_frames
// 4: retrieve information from the vframeArray to populate the skeletal frames
// 5: release the DeoptResourceMark
// 6: return to stub and eventually to interpreter
//
// With old style eager deoptimization the vframeArray was created by the vmThread there
// was no way for the vframeArray to contain resource allocated objects and so
// a complex set of data structures to simulate an array of vframes in CHeap memory
// was used. With new style lazy deoptimization the vframeArray is created in the
// the thread that will use it and we can use a much simpler scheme for the vframeArray
// leveraging existing data structures if we simply create a way to manage this one
// special need for a ResourceMark. If ResourceMark simply inherited from CHeapObj
// then existing ResourceMarks would work fine since no one use new to allocate them
// and they would be stack allocated. This leaves open the possibilty of accidental
// misuse so we simple duplicate the ResourceMark functionality here.

class DeoptResourceMark: public CHeapObj {
protected:
  ResourceArea *_area;          // Resource area to stack allocate
  Chunk *_chunk;                // saved arena chunk
  char *_hwm, *_max;
  NOT_PRODUCT(size_t _size_in_bytes;)

  void initialize(Thread *thread) {
    _area = thread->resource_area();
    _chunk = _area->_chunk;
    _hwm = _area->_hwm;
    _max= _area->_max;
    NOT_PRODUCT(_size_in_bytes = _area->size_in_bytes();)
    debug_only(_area->_nesting++;)
    assert( _area->_nesting > 0, "must stack allocate RMs" );
  }

 public:

#ifndef ASSERT
  DeoptResourceMark(Thread *thread) {
    assert(thread == Thread::current(), "not the current thread");
    initialize(thread);
  }
#else
  DeoptResourceMark(Thread *thread);
#endif // ASSERT

  DeoptResourceMark()               { initialize(Thread::current()); }

  DeoptResourceMark( ResourceArea *r ) :
    _area(r), _chunk(r->_chunk), _hwm(r->_hwm), _max(r->_max) {
    NOT_PRODUCT(_size_in_bytes = _area->size_in_bytes();)
    debug_only(_area->_nesting++;)
    assert( _area->_nesting > 0, "must stack allocate RMs" );
  }

  void reset_to_mark() {
    if (UseMallocOnly) free_malloced_objects();

    if( _chunk->next() )        // Delete later chunks
      _chunk->next_chop();
    _area->_chunk = _chunk;     // Roll back arena to saved chunk
    _area->_hwm = _hwm;
    _area->_max = _max;

    // clear out this chunk (to detect allocation bugs)
    if (ZapResourceArea) memset(_hwm, badResourceValue, _max - _hwm);
    _area->set_size_in_bytes(size_in_bytes());
  }

  ~DeoptResourceMark() {
    assert( _area->_nesting > 0, "must stack allocate RMs" );
    debug_only(_area->_nesting--;)
    reset_to_mark();
  }


 private:
  void free_malloced_objects()                                         PRODUCT_RETURN;
  size_t size_in_bytes()       NOT_PRODUCT({ return _size_in_bytes; }) PRODUCT_RETURN0;
};

#endif // SHARE_VM_MEMORY_RESOURCEAREA_HPP
