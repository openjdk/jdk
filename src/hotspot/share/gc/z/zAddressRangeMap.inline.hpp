/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZADDRESSRANGEMAP_INLINE_HPP
#define SHARE_GC_Z_ZADDRESSRANGEMAP_INLINE_HPP

#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zAddressRangeMap.hpp"
#include "gc/z/zGlobals.hpp"
#include "memory/allocation.inline.hpp"

template <typename T, size_t AddressRangeShift>
ZAddressRangeMap<T, AddressRangeShift>::ZAddressRangeMap() :
    _map(MmapArrayAllocator<T>::allocate(size(), mtGC)) {}

template <typename T, size_t AddressRangeShift>
ZAddressRangeMap<T, AddressRangeShift>::~ZAddressRangeMap() {
  MmapArrayAllocator<T>::free(_map, size());
}

template <typename T, size_t AddressRangeShift>
size_t ZAddressRangeMap<T, AddressRangeShift>::index_for_addr(uintptr_t addr) const {
  assert(!ZAddress::is_null(addr), "Invalid address");

  const size_t index = ZAddress::offset(addr) >> AddressRangeShift;
  assert(index < size(), "Invalid index");

  return index;
}

template <typename T, size_t AddressRangeShift>
size_t ZAddressRangeMap<T, AddressRangeShift>::size() const {
  return ZAddressOffsetMax >> AddressRangeShift;
}

template <typename T, size_t AddressRangeShift>
T ZAddressRangeMap<T, AddressRangeShift>::get(uintptr_t addr) const {
  const uintptr_t index = index_for_addr(addr);
  return _map[index];
}

template <typename T, size_t AddressRangeShift>
void ZAddressRangeMap<T, AddressRangeShift>::put(uintptr_t addr, T value) {
  const uintptr_t index = index_for_addr(addr);
  _map[index] = value;
}

template <typename T, size_t AddressRangeShift>
inline ZAddressRangeMapIterator<T, AddressRangeShift>::ZAddressRangeMapIterator(const ZAddressRangeMap<T, AddressRangeShift>* map) :
    _map(map),
    _next(0) {}

template <typename T, size_t AddressRangeShift>
inline bool ZAddressRangeMapIterator<T, AddressRangeShift>::next(T* value) {
  if (_next < _map->size()) {
    *value = _map->_map[_next++];
    return true;
  }

  // End of map
  return false;
}

#endif // SHARE_GC_Z_ZADDRESSRANGEMAP_INLINE_HPP
