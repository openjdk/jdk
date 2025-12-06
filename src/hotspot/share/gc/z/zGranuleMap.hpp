/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZGRANULEMAP_HPP
#define SHARE_GC_Z_ZGRANULEMAP_HPP

#include "gc/z/zAddress.hpp"
#include "gc/z/zArray.hpp"
#include "memory/allocation.hpp"

template <typename T>
class ZGranuleMap {
  friend class VMStructs;
  template <typename, bool> friend class ZGranuleMapIterator;
  friend class ZForwardingTable;
  friend class ZPageTable;
  friend class ZRemsetTableIterator;

private:
  const size_t _size;
  T* const     _map;

  size_t index_for_offset(zoffset offset) const;

  T at(size_t index) const;

public:
  ZGranuleMap(size_t max_offset);
  ~ZGranuleMap();

  T get(zoffset offset) const;
  void put(zoffset offset, T value);
  void put(zoffset offset, size_t size, T value);

  T get_acquire(zoffset offset) const;
  void release_put(zoffset offset, T value);
  void release_put(zoffset offset, size_t size, T value);

  const T* addr(zoffset offset) const;
  T* addr(zoffset offset);
};

template <typename T, bool Parallel>
class ZGranuleMapIterator : public ZArrayIteratorImpl<T, Parallel> {
public:
  ZGranuleMapIterator(const ZGranuleMap<T>* granule_map);
};

#endif // SHARE_GC_Z_ZGRANULEMAP_HPP
