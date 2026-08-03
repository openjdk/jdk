/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/z/zLock.inline.hpp"

template <typename T>
ZArraySlice<T>::ZArraySlice(T* data, int len)
  : GrowableArrayView<T>(data, len, len) {}

template <typename T>
ZArraySlice<T> ZArraySlice<T>::slice_front(int end) {
  return slice(0, end);
}

template <typename T>
ZArraySlice<const T> ZArraySlice<T>::slice_front(int end) const {
  return slice(0, end);
}

template <typename T>
ZArraySlice<T> ZArraySlice<T>::slice_back(int start) {
  return slice(start, this->_len);
}

template <typename T>
ZArraySlice<const T> ZArraySlice<T>::slice_back(int start) const {
  return slice(start, this->_len);
}

template <typename T>
ZArraySlice<T> ZArraySlice<T>::slice(int start, int end) {
  assert(0 <= start && start <= end && end <= this->_len,
         "slice called with invalid range (%d, %d) for length %d", start, end, this->_len);
  return ZArraySlice<T>(this->_data + start, end - start);
}

template <typename T>
ZArraySlice<const T> ZArraySlice<T>::slice(int start, int end) const {
  assert(0 <= start && start <= end && end <= this->_len,
         "slice called with invalid range (%d, %d) for length %d", start, end, this->_len);
  return ZArraySlice<const T>(this->_data + start, end - start);
}

template <typename T>
ZArraySlice<T>::operator ZArraySlice<const T>() const {
  return slice(0, this->_len);
}

template <typename T>
ZArraySlice<T> ZArray<T>::slice_front(int end) {
  return slice(0, end);
}

template <typename T>
ZArraySlice<const T> ZArray<T>::slice_front(int end) const {
  return slice(0, end);
}

template <typename T>
ZArraySlice<T> ZArray<T>::slice_back(int start) {
  return slice(start, this->_len);
}

template <typename T>
ZArraySlice<const T> ZArray<T>::slice_back(int start) const {
  return slice(start, this->_len);
}

template <typename T>
ZArraySlice<T> ZArray<T>::slice(int start, int end) {
  assert(0 <= start && start <= end && end <= this->_len,
         "slice called with invalid range (%d, %d) for length %d", start, end, this->_len);
  return ZArraySlice<T>(this->_data + start, end - start);
}

template <typename T>
ZArraySlice<const T> ZArray<T>::slice(int start, int end) const {
  assert(0 <= start && start <= end && end <= this->_len,
         "slice called with invalid range (%d, %d) for length %d", start, end, this->_len);
  return ZArraySlice<const T>(this->_data + start, end - start);
}

template <typename T>
ZArray<T>::operator ZArraySlice<T>() {
  return slice(0, this->_len);
}

template <typename T>
ZArray<T>::operator ZArraySlice<const T>() const {
  return slice(0, this->_len);
}

template <typename T, bool Parallel>
inline bool ZArrayIteratorImpl<T, Parallel>::next_serial(size_t* index) {
  if (_next == _end) {
    return false;
  }

  *index = _next;
  _next++;

  return true;
}

template <typename T, bool Parallel>
inline bool ZArrayIteratorImpl<T, Parallel>::next_parallel(size_t* index) {
  const size_t claimed_index = _next.fetch_then_add(1u, memory_order_relaxed);

  if (claimed_index < _end) {
    *index = claimed_index;
    return true;
  }

  return false;
}

template <typename T, bool Parallel>
inline ZArrayIteratorImpl<T, Parallel>::ZArrayIteratorImpl(const T* array, size_t length)
  : _next(0),
    _end(length),
    _array(array) {}

template <typename T, bool Parallel>
inline ZArrayIteratorImpl<T, Parallel>::ZArrayIteratorImpl(const ZArray<T>* array)
  : ZArrayIteratorImpl<T, Parallel>(array->is_empty() ? nullptr : array->adr_at(0), (size_t)array->length()) {}

template <typename T, bool Parallel>
inline bool ZArrayIteratorImpl<T, Parallel>::next(T* elem) {
  size_t index;
  if (next_index(&index)) {
    *elem = index_to_elem(index);
    return true;
  }

  return false;
}

template <typename T, bool Parallel>
template <typename Function, typename... Args>
inline bool ZArrayIteratorImpl<T, Parallel>::next_if(T* elem, Function predicate, Args&&... args) {
  size_t index;
  while (next_index(&index)) {
    if (predicate(index_to_elem(index), args...)) {
      *elem = index_to_elem(index);
      return true;
    }
  }

  return false;
}

template <typename T, bool Parallel>
inline bool ZArrayIteratorImpl<T, Parallel>::next_index(size_t* index) {
  if constexpr (Parallel) {
    return next_parallel(index);
  } else {
    return next_serial(index);
  }
}

template <typename T, bool Parallel>
inline T ZArrayIteratorImpl<T, Parallel>::index_to_elem(size_t index) {
  assert(index < _end, "Out of bounds");
  return _array[index];
}

template <typename T>
ZActivatedArray<T>::ZActivatedArray(bool locked)
  : _lock(locked ? new ZLock() : nullptr),
    _count(0),
    _array() {}

template <typename T>
ZActivatedArray<T>::~ZActivatedArray() {
  FreeHeap(_lock);
}

template <typename T>
bool ZActivatedArray<T>::is_activated() const {
  ZLocker<ZLock> locker(_lock);
  return _count > 0;
}

template <typename T>
bool ZActivatedArray<T>::add_if_activated(ItemT* item) {
  ZLocker<ZLock> locker(_lock);
  if (_count > 0) {
    _array.append(item);
    return true;
  }

  return false;
}

template <typename T>
void ZActivatedArray<T>::activate() {
  ZLocker<ZLock> locker(_lock);
  _count++;
}

template <typename T>
template <typename Function>
void ZActivatedArray<T>::deactivate_and_apply(Function function) {
  ZArray<ItemT*> array;

  {
    ZLocker<ZLock> locker(_lock);
    assert(_count > 0, "Invalid state");
    if (--_count == 0u) {
      // Fully deactivated - remove all elements
      array.swap(&_array);
    }
  }

  // Apply function to all elements - if fully deactivated
  ZArrayIterator<ItemT*> iter(&array);
  for (ItemT* item; iter.next(&item);) {
    function(item);
  }
}

#endif // SHARE_GC_Z_ZARRAY_INLINE_HPP
