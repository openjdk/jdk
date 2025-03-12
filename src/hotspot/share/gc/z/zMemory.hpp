/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZMEMORY_HPP
#define SHARE_GC_Z_ZMEMORY_HPP

#include "gc/z/zAddress.hpp"
#include "gc/z/zList.hpp"
#include "gc/z/zLock.hpp"
#include "memory/allocation.hpp"

class ZMemory : public CHeapObj<mtGC> {
  friend class ZList<ZMemory>;

private:
  zoffset            _start;
  zoffset_end        _end;
  ZListNode<ZMemory> _node;

public:
  ZMemory(zoffset start, size_t size);

  zoffset start() const;
  zoffset_end end() const;
  size_t size() const;

  void shrink_from_front(size_t size);
  void shrink_from_back(size_t size);
  void grow_from_front(size_t size);
  void grow_from_back(size_t size);
};

class ZMemoryManager {
public:
  typedef void (*CreateDestroyCallback)(const ZMemory* area);
  typedef void (*ResizeCallback)(const ZMemory* area, size_t size);

  struct Callbacks {
    CreateDestroyCallback _create;
    CreateDestroyCallback _destroy;
    ResizeCallback        _shrink_from_front;
    ResizeCallback        _shrink_from_back;
    ResizeCallback        _grow_from_front;
    ResizeCallback        _grow_from_back;

    Callbacks();
  };

private:
  mutable ZLock  _lock;
  ZList<ZMemory> _freelist;
  Callbacks      _callbacks;

  ZMemory* create(zoffset start, size_t size);
  void destroy(ZMemory* area);
  void shrink_from_front(ZMemory* area, size_t size);
  void shrink_from_back(ZMemory* area, size_t size);
  void grow_from_front(ZMemory* area, size_t size);
  void grow_from_back(ZMemory* area, size_t size);

public:
  ZMemoryManager();

  bool free_is_contiguous() const;

  void register_callbacks(const Callbacks& callbacks);

  zoffset peek_low_address() const;
  zoffset_end peak_high_address_end() const;
  zoffset alloc_low_address(size_t size);
  zoffset alloc_low_address_at_most(size_t size, size_t* allocated);
  zoffset alloc_high_address(size_t size);

  void free(zoffset start, size_t size);
};

#endif // SHARE_GC_Z_ZMEMORY_HPP
