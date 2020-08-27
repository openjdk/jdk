/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
    GrowableArrayCHeap<T, mtGC>(0) {}

template <typename T>
inline void ZArray<T>::transfer(ZArray<T>* from) {
  assert(this->_data == NULL, "Should be empty");
  this->_data = from->_data;
  this->_len = from->_len;
  this->_max = from->_max;
  from->_data = NULL;
  from->_len = 0;
  from->_max = 0;
}

template <typename T, bool parallel>
inline ZArrayIteratorImpl<T, parallel>::ZArrayIteratorImpl(ZArray<T>* array) :
    _array(array),
    _next(0) {}

template <typename T, bool parallel>
inline bool ZArrayIteratorImpl<T, parallel>::next(T* elem) {
  if (parallel) {
    const int next = Atomic::fetch_and_add(&_next, 1);
    if (next < _array->length()) {
      *elem = _array->at(next);
      return true;
    }
  } else {
    if (_next < _array->length()) {
      *elem = _array->at(_next++);
      return true;
    }
  }

  // No more elements
  return false;
}

#endif // SHARE_GC_Z_ZARRAY_INLINE_HPP
