/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_X_XVALUE_HPP
#define SHARE_GC_X_XVALUE_HPP

#include "memory/allStatic.hpp"
#include "utilities/globalDefinitions.hpp"

//
// Storage
//

template <typename S>
class XValueStorage : public AllStatic {
private:
  static uintptr_t _top;
  static uintptr_t _end;

public:
  static const size_t offset = 4 * K;

  static uintptr_t alloc(size_t size);
};

class XContendedStorage : public XValueStorage<XContendedStorage> {
public:
  static size_t alignment();
  static uint32_t count();
  static uint32_t id();
};

class XPerCPUStorage : public XValueStorage<XPerCPUStorage> {
public:
  static size_t alignment();
  static uint32_t count();
  static uint32_t id();
};

class XPerNUMAStorage : public XValueStorage<XPerNUMAStorage> {
public:
  static size_t alignment();
  static uint32_t count();
  static uint32_t id();
};

class XPerWorkerStorage : public XValueStorage<XPerWorkerStorage> {
public:
  static size_t alignment();
  static uint32_t count();
  static uint32_t id();
};

//
// Value
//

template <typename S, typename T>
class XValue : public CHeapObj<mtGC> {
private:
  const uintptr_t _addr;

  uintptr_t value_addr(uint32_t value_id) const;

public:
  XValue();
  XValue(const T& value);

  const T* addr(uint32_t value_id = S::id()) const;
  T* addr(uint32_t value_id = S::id());

  const T& get(uint32_t value_id = S::id()) const;
  T& get(uint32_t value_id = S::id());

  void set(const T& value, uint32_t value_id = S::id());
  void set_all(const T& value);
};

template <typename T> using XContended = XValue<XContendedStorage, T>;
template <typename T> using XPerCPU = XValue<XPerCPUStorage, T>;
template <typename T> using XPerNUMA = XValue<XPerNUMAStorage, T>;
template <typename T> using XPerWorker = XValue<XPerWorkerStorage, T>;

//
// Iterator
//

template <typename S, typename T>
class XValueIterator {
private:
  XValue<S, T>* const _value;
  uint32_t            _value_id;

public:
  XValueIterator(XValue<S, T>* value);

  bool next(T** value);
};

template <typename T> using XPerCPUIterator = XValueIterator<XPerCPUStorage, T>;
template <typename T> using XPerNUMAIterator = XValueIterator<XPerNUMAStorage, T>;
template <typename T> using XPerWorkerIterator = XValueIterator<XPerWorkerStorage, T>;

template <typename S, typename T>
class XValueConstIterator {
private:
  const XValue<S, T>* const _value;
  uint32_t                  _value_id;

public:
  XValueConstIterator(const XValue<S, T>* value);

  bool next(const T** value);
};

template <typename T> using XPerCPUConstIterator = XValueConstIterator<XPerCPUStorage, T>;
template <typename T> using XPerNUMAConstIterator = XValueConstIterator<XPerNUMAStorage, T>;
template <typename T> using XPerWorkerConstIterator = XValueConstIterator<XPerWorkerStorage, T>;

#endif // SHARE_GC_X_XVALUE_HPP
