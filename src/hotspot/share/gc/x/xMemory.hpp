/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_X_XMEMORY_HPP
#define SHARE_GC_X_XMEMORY_HPP

#include "gc/x/xList.hpp"
#include "gc/x/xLock.hpp"
#include "memory/allocation.hpp"

class XMemory : public CHeapObj<mtGC> {
  friend class XList<XMemory>;

private:
  uintptr_t          _start;
  uintptr_t          _end;
  XListNode<XMemory> _node;

public:
  XMemory(uintptr_t start, size_t size);

  uintptr_t start() const;
  uintptr_t end() const;
  size_t size() const;

  void shrink_from_front(size_t size);
  void shrink_from_back(size_t size);
  void grow_from_front(size_t size);
  void grow_from_back(size_t size);
};

class XMemoryManager {
public:
  typedef void (*CreateDestroyCallback)(const XMemory* area);
  typedef void (*ResizeCallback)(const XMemory* area, size_t size);

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
  mutable XLock  _lock;
  XList<XMemory> _freelist;
  Callbacks      _callbacks;

  XMemory* create(uintptr_t start, size_t size);
  void destroy(XMemory* area);
  void shrink_from_front(XMemory* area, size_t size);
  void shrink_from_back(XMemory* area, size_t size);
  void grow_from_front(XMemory* area, size_t size);
  void grow_from_back(XMemory* area, size_t size);

public:
  XMemoryManager();

  void register_callbacks(const Callbacks& callbacks);

  uintptr_t peek_low_address() const;
  uintptr_t alloc_low_address(size_t size);
  uintptr_t alloc_low_address_at_most(size_t size, size_t* allocated);
  uintptr_t alloc_high_address(size_t size);

  void free(uintptr_t start, size_t size);
};

#endif // SHARE_GC_X_XMEMORY_HPP
