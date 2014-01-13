/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_HANDLES_INLINE_HPP
#define SHARE_VM_RUNTIME_HANDLES_INLINE_HPP

#include "runtime/handles.hpp"
#include "runtime/thread.inline.hpp"

// these inline functions are in a separate file to break an include cycle
// between Thread and Handle

inline Handle::Handle(oop obj) {
  if (obj == NULL) {
    _handle = NULL;
  } else {
    _handle = Thread::current()->handle_area()->allocate_handle(obj);
  }
}


#ifndef ASSERT
inline Handle::Handle(Thread* thread, oop obj) {
  assert(thread == Thread::current(), "sanity check");
  if (obj == NULL) {
    _handle = NULL;
  } else {
    _handle = thread->handle_area()->allocate_handle(obj);
  }
}
#endif // ASSERT

// Constructors for metadata handles
#define DEF_METADATA_HANDLE_FN(name, type) \
inline name##Handle::name##Handle(type* obj) : _value(obj), _thread(NULL) {       \
  if (obj != NULL) {                                                   \
    assert(((Metadata*)obj)->is_valid(), "obj is valid");              \
    _thread = Thread::current();                                       \
    assert (_thread->is_in_stack((address)this), "not on stack?");     \
    _thread->metadata_handles()->push((Metadata*)obj);                 \
  }                                                                    \
}                                                                      \
inline name##Handle::name##Handle(Thread* thread, type* obj) : _value(obj), _thread(thread) { \
  if (obj != NULL) {                                                   \
    assert(((Metadata*)obj)->is_valid(), "obj is valid");              \
    assert(_thread == Thread::current(), "thread must be current");    \
    assert (_thread->is_in_stack((address)this), "not on stack?");     \
    _thread->metadata_handles()->push((Metadata*)obj);                 \
  }                                                                    \
}                                                                      \
inline name##Handle::name##Handle(const name##Handle &h) {             \
  _value = h._value;                                                   \
  if (_value != NULL) {                                                \
    assert(_value->is_valid(), "obj is valid");                        \
    if (h._thread != NULL) {                                           \
      assert(h._thread == Thread::current(), "thread must be current");\
      _thread = h._thread;                                             \
    } else {                                                           \
      _thread = Thread::current();                                     \
    }                                                                  \
    assert (_thread->is_in_stack((address)this), "not on stack?");     \
    _thread->metadata_handles()->push((Metadata*)_value);              \
  } else {                                                             \
    _thread = NULL;                                                    \
  }                                                                    \
}                                                                      \
inline name##Handle& name##Handle::operator=(const name##Handle &s) {  \
  remove();                                                            \
  _value = s._value;                                                   \
  if (_value != NULL) {                                                \
    assert(_value->is_valid(), "obj is valid");                        \
    if (s._thread != NULL) {                                           \
      assert(s._thread == Thread::current(), "thread must be current");\
      _thread = s._thread;                                             \
    } else {                                                           \
      _thread = Thread::current();                                     \
    }                                                                  \
    assert (_thread->is_in_stack((address)this), "not on stack?");     \
    _thread->metadata_handles()->push((Metadata*)_value);              \
  } else {                                                             \
    _thread = NULL;                                                    \
  }                                                                    \
  return *this;                                                        \
}                                                                      \
inline void name##Handle::remove() {                                   \
  if (_value != NULL) {                                                \
    int i = _thread->metadata_handles()->find_from_end((Metadata*)_value); \
    assert(i!=-1, "not in metadata_handles list");                     \
    _thread->metadata_handles()->remove_at(i);                         \
  }                                                                    \
}                                                                      \
inline name##Handle::~name##Handle () { remove(); }                    \

DEF_METADATA_HANDLE_FN(method, Method)
DEF_METADATA_HANDLE_FN(constantPool, ConstantPool)

inline HandleMark::HandleMark() {
  initialize(Thread::current());
}


inline void HandleMark::push() {
  // This is intentionally a NOP. pop_and_restore will reset
  // values to the HandleMark further down the stack, typically
  // in JavaCalls::call_helper.
  debug_only(_area->_handle_mark_nesting++);
}

inline void HandleMark::pop_and_restore() {
  HandleArea* area = _area;   // help compilers with poor alias analysis
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
  debug_only(area->_handle_mark_nesting--);
}

#endif // SHARE_VM_RUNTIME_HANDLES_INLINE_HPP
