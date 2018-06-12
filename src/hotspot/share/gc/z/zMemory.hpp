/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/z/zList.hpp"
#include "memory/allocation.hpp"

class ZMemory : public CHeapObj<mtGC> {
  friend class ZList<ZMemory>;

private:
  uintptr_t          _start;
  uintptr_t          _end;
  ZListNode<ZMemory> _node;

public:
  ZMemory(uintptr_t start, size_t size);

  uintptr_t start() const;
  uintptr_t end() const;
  size_t size() const;

  void shrink_from_front(size_t size);
  void shrink_from_back(size_t size);
  void grow_from_front(size_t size);
  void grow_from_back(size_t size);
};

class ZMemoryManager {
private:
  ZList<ZMemory> _freelist;

public:
  uintptr_t alloc_from_front(size_t size);
  uintptr_t alloc_from_back(size_t size);
  void free(uintptr_t start, size_t size);
};

#endif // SHARE_GC_Z_ZMEMORY_HPP
