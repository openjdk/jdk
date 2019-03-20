/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZGRANULEMAP_INLINE_HPP
#define SHARE_GC_Z_ZGRANULEMAP_INLINE_HPP

#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zGranuleMap.hpp"
#include "memory/allocation.inline.hpp"

template <typename T>
inline ZGranuleMap<T>::ZGranuleMap() :
    _map(MmapArrayAllocator<T>::allocate(size(), mtGC)) {}

template <typename T>
inline ZGranuleMap<T>::~ZGranuleMap() {
  MmapArrayAllocator<T>::free(_map, size());
}

template <typename T>
inline size_t ZGranuleMap<T>::index_for_addr(uintptr_t addr) const {
  assert(!ZAddress::is_null(addr), "Invalid address");

  const size_t index = ZAddress::offset(addr) >> ZGranuleSizeShift;
  assert(index < size(), "Invalid index");

  return index;
}

template <typename T>
inline size_t ZGranuleMap<T>::size() const {
  return ZAddressOffsetMax >> ZGranuleSizeShift;
}

template <typename T>
inline T ZGranuleMap<T>::get(uintptr_t addr) const {
  const size_t index = index_for_addr(addr);
  return _map[index];
}

template <typename T>
inline void ZGranuleMap<T>::put(uintptr_t addr, T value) {
  const size_t index = index_for_addr(addr);
  _map[index] = value;
}

template <typename T>
inline void ZGranuleMap<T>::put(uintptr_t addr, size_t size, T value) {
  assert(is_aligned(size, ZGranuleSize), "Misaligned");

  const size_t start_index = index_for_addr(addr);
  const size_t end_index = start_index + (size >> ZGranuleSizeShift);
  for (size_t index = start_index; index < end_index; index++) {
    _map[index] = value;
  }
}

template <typename T>
inline ZGranuleMapIterator<T>::ZGranuleMapIterator(const ZGranuleMap<T>* map) :
    _map(map),
    _next(0) {}

template <typename T>
inline bool ZGranuleMapIterator<T>::next(T* value) {
  if (_next < _map->size()) {
    *value = _map->_map[_next++];
    return true;
  }

  // End of map
  return false;
}

template <typename T>
inline bool ZGranuleMapIterator<T>::next(T** value) {
  if (_next < _map->size()) {
    *value = _map->_map + _next++;
    return true;
  }

  // End of map
  return false;
}

#endif // SHARE_GC_Z_ZGRANULEMAP_INLINE_HPP
