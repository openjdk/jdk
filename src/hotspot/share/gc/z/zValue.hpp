/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZVALUE_HPP
#define SHARE_GC_Z_ZVALUE_HPP

#include "memory/allocation.hpp"
#include "gc/z/zCPU.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zNUMA.hpp"
#include "gc/z/zThread.hpp"
#include "gc/z/zUtils.hpp"
#include "utilities/align.hpp"

template <typename S>
class ZValueStorage : public AllStatic {
private:
  static uintptr_t _top;
  static uintptr_t _end;

public:
  static const size_t offset = 4 * K;

  static uintptr_t alloc(size_t size) {
    guarantee(size <= offset, "Allocation too large");

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
    _top = ZUtils::alloc_aligned(block_alignment, block_size);
    _end = _top + offset;

    // Retry allocation
    return alloc(size);
  }
};

template <typename T> uintptr_t ZValueStorage<T>::_end = 0;
template <typename T> uintptr_t ZValueStorage<T>::_top = 0;

class ZContendedStorage : public ZValueStorage<ZContendedStorage> {
public:
  static size_t alignment() {
    return ZCacheLineSize;
  }

  static uint32_t count() {
    return 1;
  }

  static uint32_t id() {
    return 0;
  }
};

class ZPerCPUStorage : public ZValueStorage<ZPerCPUStorage> {
public:
  static size_t alignment() {
    return sizeof(uintptr_t);
  }

  static uint32_t count() {
    return ZCPU::count();
  }

  static uint32_t id() {
    return ZCPU::id();
  }
};

class ZPerNUMAStorage : public ZValueStorage<ZPerNUMAStorage> {
public:
  static size_t alignment() {
    return sizeof(uintptr_t);
  }

  static uint32_t count() {
    return ZNUMA::count();
  }

  static uint32_t id() {
    return ZNUMA::id();
  }
};

class ZPerWorkerStorage : public ZValueStorage<ZPerWorkerStorage> {
public:
  static size_t alignment() {
    return sizeof(uintptr_t);
  }

  static uint32_t count() {
    return MAX2(ParallelGCThreads, ConcGCThreads);
  }

  static uint32_t id() {
    return ZThread::worker_id();
  }
};

template <typename S, typename T>
class ZValueIterator;

template <typename S, typename T>
class ZValue : public CHeapObj<mtGC> {
private:
  const uintptr_t _addr;

  uintptr_t value_addr(uint32_t value_id) const {
    return _addr + (value_id * S::offset);
  }

public:
  ZValue() :
      _addr(S::alloc(sizeof(T))) {
    // Initialize all instances
    ZValueIterator<S, T> iter(this);
    for (T* addr; iter.next(&addr);) {
      ::new (addr) T;
    }
  }

  ZValue(const T& value) :
      _addr(S::alloc(sizeof(T))) {
    // Initialize all instances
    ZValueIterator<S, T> iter(this);
    for (T* addr; iter.next(&addr);) {
      ::new (addr) T(value);
    }
  }

  // Not implemented
  ZValue(const ZValue<S, T>& value);
  ZValue<S, T>& operator=(const ZValue<S, T>& value);

  const T* addr(uint32_t value_id = S::id()) const {
    return reinterpret_cast<const T*>(value_addr(value_id));
  }

  T* addr(uint32_t value_id = S::id()) {
    return reinterpret_cast<T*>(value_addr(value_id));
  }

  const T& get(uint32_t value_id = S::id()) const {
    return *addr(value_id);
  }

  T& get(uint32_t value_id = S::id()) {
    return *addr(value_id);
  }

  void set(const T& value, uint32_t value_id = S::id()) {
    get(value_id) = value;
  }

  void set_all(const T& value) {
    ZValueIterator<S, T> iter(this);
    for (T* addr; iter.next(&addr);) {
      *addr = value;
    }
  }
};

template <typename T>
class ZContended : public ZValue<ZContendedStorage, T> {
public:
  ZContended() :
      ZValue<ZContendedStorage, T>() {}

  ZContended(const T& value) :
      ZValue<ZContendedStorage, T>(value) {}

  using ZValue<ZContendedStorage, T>::operator=;
};

template <typename T>
class ZPerCPU : public ZValue<ZPerCPUStorage, T> {
public:
  ZPerCPU() :
      ZValue<ZPerCPUStorage, T>() {}

  ZPerCPU(const T& value) :
      ZValue<ZPerCPUStorage, T>(value) {}

  using ZValue<ZPerCPUStorage, T>::operator=;
};

template <typename T>
class ZPerNUMA : public ZValue<ZPerNUMAStorage, T> {
public:
  ZPerNUMA() :
      ZValue<ZPerNUMAStorage, T>() {}

  ZPerNUMA(const T& value) :
      ZValue<ZPerNUMAStorage, T>(value) {}

  using ZValue<ZPerNUMAStorage, T>::operator=;
};

template <typename T>
class ZPerWorker : public ZValue<ZPerWorkerStorage, T> {
public:
  ZPerWorker() :
      ZValue<ZPerWorkerStorage, T>() {}

  ZPerWorker(const T& value) :
      ZValue<ZPerWorkerStorage, T>(value) {}

  using ZValue<ZPerWorkerStorage, T>::operator=;
};

template <typename S, typename T>
class ZValueIterator {
private:
  ZValue<S, T>* const _value;
  uint32_t            _value_id;

public:
  ZValueIterator(ZValue<S, T>* value) :
      _value(value),
      _value_id(0) {}

  bool next(T** value) {
    if (_value_id < S::count()) {
      *value = _value->addr(_value_id++);
      return true;
    }
    return false;
  }
};

template <typename T>
class ZPerCPUIterator : public ZValueIterator<ZPerCPUStorage, T> {
public:
  ZPerCPUIterator(ZPerCPU<T>* value) :
      ZValueIterator<ZPerCPUStorage, T>(value) {}
};

template <typename T>
class ZPerNUMAIterator : public ZValueIterator<ZPerNUMAStorage, T> {
public:
  ZPerNUMAIterator(ZPerNUMA<T>* value) :
      ZValueIterator<ZPerNUMAStorage, T>(value) {}
};

template <typename T>
class ZPerWorkerIterator : public ZValueIterator<ZPerWorkerStorage, T> {
public:
  ZPerWorkerIterator(ZPerWorker<T>* value) :
      ZValueIterator<ZPerWorkerStorage, T>(value) {}
};

template <typename S, typename T>
class ZValueConstIterator {
private:
  const ZValue<S, T>* const _value;
  uint32_t                  _value_id;

public:
  ZValueConstIterator(const ZValue<S, T>* value) :
      _value(value),
      _value_id(0) {}

  bool next(const T** value) {
    if (_value_id < S::count()) {
      *value = _value->addr(_value_id++);
      return true;
    }
    return false;
  }
};

template <typename T>
class ZPerCPUConstIterator : public ZValueConstIterator<ZPerCPUStorage, T> {
public:
  ZPerCPUConstIterator(const ZPerCPU<T>* value) :
      ZValueConstIterator<ZPerCPUStorage, T>(value) {}
};

template <typename T>
class ZPerNUMAConstIterator : public ZValueConstIterator<ZPerNUMAStorage, T> {
public:
  ZPerNUMAConstIterator(const ZPerNUMA<T>* value) :
      ZValueConstIterator<ZPerNUMAStorage, T>(value) {}
};

template <typename T>
class ZPerWorkerConstIterator : public ZValueConstIterator<ZPerWorkerStorage, T> {
public:
  ZPerWorkerConstIterator(const ZPerWorker<T>* value) :
      ZValueConstIterator<ZPerWorkerStorage, T>(value) {}
};

#endif // SHARE_GC_Z_ZVALUE_HPP
