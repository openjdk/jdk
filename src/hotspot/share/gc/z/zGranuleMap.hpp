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

#ifndef SHARE_GC_Z_ZGRANULEMAP_HPP
#define SHARE_GC_Z_ZGRANULEMAP_HPP

#include "memory/allocation.hpp"

template<typename T>
class ZGranuleMapIterator;

template <typename T>
class ZGranuleMap {
  friend class VMStructs;
  friend class ZGranuleMapIterator<T>;

private:
  T* const _map;

  size_t index_for_addr(uintptr_t addr) const;
  size_t size() const;

public:
  ZGranuleMap();
  ~ZGranuleMap();

  T get(uintptr_t addr) const;
  void put(uintptr_t addr, T value);
  void put(uintptr_t addr, size_t size, T value);
};

template <typename T>
class ZGranuleMapIterator : public StackObj {
public:
  const ZGranuleMap<T>* const _map;
  size_t                      _next;

public:
  ZGranuleMapIterator(const ZGranuleMap<T>* map);

  bool next(T* value);
  bool next(T** value);
};

#endif // SHARE_GC_Z_ZGRANULEMAP_HPP
