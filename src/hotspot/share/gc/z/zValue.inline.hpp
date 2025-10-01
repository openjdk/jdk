/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZVALUE_INLINE_HPP
#define SHARE_GC_Z_ZVALUE_INLINE_HPP

#include "gc/z/zValue.hpp"

#include "gc/shared/gc_globals.hpp"
#include "gc/shared/workerThread.hpp"
#include "gc/z/zCPU.inline.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zNUMA.inline.hpp"
#include "gc/z/zUtils.inline.hpp"
#include "runtime/globals.hpp"
#include "utilities/align.hpp"

//
// Storage
//

template <typename T> uintptr_t ZValueStorage<T>::_end = 0;
template <typename T> uintptr_t ZValueStorage<T>::_top = 0;

template <typename S>
uintptr_t ZValueStorage<S>::alloc(size_t size) {
  assert(size <= Offset, "Allocation too large");

  // Allocate entry in existing memory block
  const uintptr_t addr = align_up(_top, S::alignment());
  _top = addr + size;

  if (_top < _end) {
    // Success
    return addr;
  }

  // Allocate new block of memory
  const size_t block_alignment = Offset;
  const size_t block_size = Offset * S::count();
  _top = ZUtils::alloc_aligned_unfreeable(block_alignment, block_size);
  _end = _top + Offset;

  // Retry allocation
  return alloc(size);
}

inline size_t ZContendedStorage::alignment() {
  return ZCacheLineSize;
}

inline uint32_t ZContendedStorage::count() {
  return 1;
}

inline uint32_t ZContendedStorage::id() {
  return 0;
}

inline size_t ZPerCPUStorage::alignment() {
  return sizeof(uintptr_t);
}

inline uint32_t ZPerCPUStorage::count() {
  return ZCPU::count();
}

inline uint32_t ZPerCPUStorage::id() {
  return ZCPU::id();
}

inline size_t ZPerNUMAStorage::alignment() {
  return sizeof(uintptr_t);
}

inline uint32_t ZPerNUMAStorage::count() {
  return ZNUMA::count();
}

inline uint32_t ZPerNUMAStorage::id() {
  return ZNUMA::id();
}

inline size_t ZPerWorkerStorage::alignment() {
  return sizeof(uintptr_t);
}

inline uint32_t ZPerWorkerStorage::count() {
  return ConcGCThreads;
}

inline uint32_t ZPerWorkerStorage::id() {
  return WorkerThread::worker_id();
}

//
// Value
//

template <typename S, typename T>
inline uintptr_t ZValue<S, T>::value_addr(uint32_t value_id) const {
  return _addr + (value_id * S::Offset);
}

template <typename S, typename T>
inline ZValue<S, T>::ZValue()
  : _addr(S::alloc(sizeof(T))) {
  // Initialize all instances
  ZValueIterator<S, T> iter(this);
  for (T* addr; iter.next(&addr);) {
    ::new (addr) T;
  }
}

template <typename S, typename T>
inline ZValue<S, T>::ZValue(const T& value)
  : _addr(S::alloc(sizeof(T))) {
  // Initialize all instances
  ZValueIterator<S, T> iter(this);
  for (T* addr; iter.next(&addr);) {
    ::new (addr) T(value);
  }
}

template <typename S, typename T>
template <typename... Args>
inline ZValue<S, T>::ZValue(ZValueIdTagType, Args&&... args)
  : _addr(S::alloc(sizeof(T))) {
  // Initialize all instances
  uint32_t value_id;
  ZValueIterator<S, T> iter(this);
  for (T* addr; iter.next(&addr, &value_id);) {
    ::new (addr) T(value_id, args...);
  }
}

template <typename S, typename T>
inline const T* ZValue<S, T>::addr(uint32_t value_id) const {
  return reinterpret_cast<const T*>(value_addr(value_id));
}

template <typename S, typename T>
inline T* ZValue<S, T>::addr(uint32_t value_id) {
  return reinterpret_cast<T*>(value_addr(value_id));
}

template <typename S, typename T>
inline const T& ZValue<S, T>::get(uint32_t value_id) const {
  return *addr(value_id);
}

template <typename S, typename T>
inline T& ZValue<S, T>::get(uint32_t value_id) {
  return *addr(value_id);
}

template <typename S, typename T>
inline void ZValue<S, T>::set(const T& value, uint32_t value_id) {
  get(value_id) = value;
}

template <typename S, typename T>
inline void ZValue<S, T>::set_all(const T& value) {
  ZValueIterator<S, T> iter(this);
  for (T* addr; iter.next(&addr);) {
    *addr = value;
  }
}

template <typename S, typename T>
uint32_t ZValue<S, T>::count() const {
  return S::count();
}

//
// Iterator
//

template <typename S, typename T>
inline ZValueIterator<S, T>::ZValueIterator(ZValue<S, T>* value)
  : _value(value),
    _value_id(0) {}

template <typename S, typename T>
inline bool ZValueIterator<S, T>::next(T** value) {
  if (_value_id < S::count()) {
    *value = _value->addr(_value_id++);
    return true;
  }
  return false;
}
template <typename S, typename T>
inline bool ZValueIterator<S, T>::next(T** value, uint32_t* value_id) {
  if (_value_id < S::count()) {
    *value_id = _value_id;
    *value = _value->addr(_value_id++);
    return true;
  }
  return false;
}

template <typename S, typename T>
inline ZValueConstIterator<S, T>::ZValueConstIterator(const ZValue<S, T>* value)
  : _value(value),
    _value_id(0) {}

template <typename S, typename T>
inline ZValueConstIterator<S, T>::ZValueConstIterator(const ZValueIterator<S, T>& other)
  : _value(other._value),
    _value_id(other._value_id) {}

template <typename S, typename T>
inline bool ZValueConstIterator<S, T>::next(const T** value) {
  if (_value_id < S::count()) {
    *value = _value->addr(_value_id++);
    return true;
  }
  return false;
}


#endif // SHARE_GC_Z_ZVALUE_INLINE_HPP
