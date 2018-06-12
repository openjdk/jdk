/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef SHARE_GC_Z_ZARRAY_INLINE_HPP
#define SHARE_GC_Z_ZARRAY_INLINE_HPP

#include "gc/z/zArray.hpp"
#include "memory/allocation.inline.hpp"
#include "runtime/atomic.hpp"

template <typename T>
inline ZArray<T>::ZArray() :
    _array(NULL),
    _size(0),
    _capacity(0) {}

template <typename T>
inline ZArray<T>::~ZArray() {
  if (_array != NULL) {
    FREE_C_HEAP_ARRAY(T, _array);
  }
}

template <typename T>
inline size_t ZArray<T>::size() const {
  return _size;
}

template <typename T>
inline bool ZArray<T>::is_empty() const {
  return size() == 0;
}

template <typename T>
inline T ZArray<T>::at(size_t index) const {
  assert(index < _size, "Index out of bounds");
  return _array[index];
}

template <typename T>
inline void ZArray<T>::expand(size_t new_capacity) {
  T* new_array = NEW_C_HEAP_ARRAY(T, new_capacity, mtGC);
  if (_array != NULL) {
    memcpy(new_array, _array, sizeof(T) * _capacity);
    FREE_C_HEAP_ARRAY(T, _array);
  }

  _array = new_array;
  _capacity = new_capacity;
}

template <typename T>
inline void ZArray<T>::add(T value) {
  if (_size == _capacity) {
    const size_t new_capacity = (_capacity > 0) ? _capacity * 2 : initial_capacity;
    expand(new_capacity);
  }

  _array[_size++] = value;
}

template <typename T>
inline void ZArray<T>::clear() {
  _size = 0;
}

template <typename T, bool parallel>
inline ZArrayIteratorImpl<T, parallel>::ZArrayIteratorImpl(ZArray<T>* array) :
    _array(array),
    _next(0) {}

template <typename T, bool parallel>
inline bool ZArrayIteratorImpl<T, parallel>::next(T* elem) {
  if (parallel) {
    const size_t next = Atomic::add(1u, &_next) - 1u;
    if (next < _array->size()) {
      *elem = _array->at(next);
      return true;
    }
  } else {
    if (_next < _array->size()) {
      *elem = _array->at(_next++);
      return true;
    }
  }

  // No more elements
  return false;
}

#endif // SHARE_GC_Z_ZARRAY_INLINE_HPP
