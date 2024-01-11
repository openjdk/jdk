/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_X_XVALUE_INLINE_HPP
#define SHARE_GC_X_XVALUE_INLINE_HPP

#include "gc/x/xValue.hpp"

#include "gc/shared/gc_globals.hpp"
#include "gc/x/xCPU.inline.hpp"
#include "gc/x/xGlobals.hpp"
#include "gc/x/xNUMA.hpp"
#include "gc/x/xThread.inline.hpp"
#include "gc/x/xUtils.hpp"
#include "runtime/globals.hpp"
#include "utilities/align.hpp"

//
// Storage
//

template <typename T> uintptr_t XValueStorage<T>::_end = 0;
template <typename T> uintptr_t XValueStorage<T>::_top = 0;

template <typename S>
uintptr_t XValueStorage<S>::alloc(size_t size) {
  assert(size <= offset, "Allocation too large");

  // Allocate entry in existing memory block
  const uintptr_t addr = align_up(_top, S::alignment());
  _top = addr + size;

  if (_top < _end) {
    // Success
    return addr;
  }

  // Allocate new block of memory
  const size_t block_alignment = offset;
  const size_t block_size = offset * S::count();
  _top = XUtils::alloc_aligned(block_alignment, block_size);
  _end = _top + offset;

  // Retry allocation
  return alloc(size);
}

inline size_t XContendedStorage::alignment() {
  return XCacheLineSize;
}

inline uint32_t XContendedStorage::count() {
  return 1;
}

inline uint32_t XContendedStorage::id() {
  return 0;
}

inline size_t XPerCPUStorage::alignment() {
  return sizeof(uintptr_t);
}

inline uint32_t XPerCPUStorage::count() {
  return XCPU::count();
}

inline uint32_t XPerCPUStorage::id() {
  return XCPU::id();
}

inline size_t XPerNUMAStorage::alignment() {
  return sizeof(uintptr_t);
}

inline uint32_t XPerNUMAStorage::count() {
  return XNUMA::count();
}

inline uint32_t XPerNUMAStorage::id() {
  return XNUMA::id();
}

inline size_t XPerWorkerStorage::alignment() {
  return sizeof(uintptr_t);
}

inline uint32_t XPerWorkerStorage::count() {
  return UseDynamicNumberOfGCThreads ? ConcGCThreads : MAX2(ConcGCThreads, ParallelGCThreads);
}

inline uint32_t XPerWorkerStorage::id() {
  return XThread::worker_id();
}

//
// Value
//

template <typename S, typename T>
inline uintptr_t XValue<S, T>::value_addr(uint32_t value_id) const {
  return _addr + (value_id * S::offset);
}

template <typename S, typename T>
inline XValue<S, T>::XValue() :
    _addr(S::alloc(sizeof(T))) {
  // Initialize all instances
  XValueIterator<S, T> iter(this);
  for (T* addr; iter.next(&addr);) {
    ::new (addr) T;
  }
}

template <typename S, typename T>
inline XValue<S, T>::XValue(const T& value) :
    _addr(S::alloc(sizeof(T))) {
  // Initialize all instances
  XValueIterator<S, T> iter(this);
  for (T* addr; iter.next(&addr);) {
    ::new (addr) T(value);
  }
}

template <typename S, typename T>
inline const T* XValue<S, T>::addr(uint32_t value_id) const {
  return reinterpret_cast<const T*>(value_addr(value_id));
}

template <typename S, typename T>
inline T* XValue<S, T>::addr(uint32_t value_id) {
  return reinterpret_cast<T*>(value_addr(value_id));
}

template <typename S, typename T>
inline const T& XValue<S, T>::get(uint32_t value_id) const {
  return *addr(value_id);
}

template <typename S, typename T>
inline T& XValue<S, T>::get(uint32_t value_id) {
  return *addr(value_id);
}

template <typename S, typename T>
inline void XValue<S, T>::set(const T& value, uint32_t value_id) {
  get(value_id) = value;
}

template <typename S, typename T>
inline void XValue<S, T>::set_all(const T& value) {
  XValueIterator<S, T> iter(this);
  for (T* addr; iter.next(&addr);) {
    *addr = value;
  }
}

//
// Iterator
//

template <typename S, typename T>
inline XValueIterator<S, T>::XValueIterator(XValue<S, T>* value) :
    _value(value),
    _value_id(0) {}

template <typename S, typename T>
inline bool XValueIterator<S, T>::next(T** value) {
  if (_value_id < S::count()) {
    *value = _value->addr(_value_id++);
    return true;
  }
  return false;
}

template <typename S, typename T>
inline XValueConstIterator<S, T>::XValueConstIterator(const XValue<S, T>* value) :
    _value(value),
    _value_id(0) {}

template <typename S, typename T>
inline bool XValueConstIterator<S, T>::next(const T** value) {
  if (_value_id < S::count()) {
    *value = _value->addr(_value_id++);
    return true;
  }
  return false;
}

#endif // SHARE_GC_X_XVALUE_INLINE_HPP
