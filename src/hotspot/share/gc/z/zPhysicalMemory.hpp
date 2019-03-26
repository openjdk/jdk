/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZPHYSICALMEMORY_HPP
#define SHARE_GC_Z_ZPHYSICALMEMORY_HPP

#include "memory/allocation.hpp"
#include OS_CPU_HEADER(gc/z/zPhysicalMemoryBacking)

class ZPhysicalMemorySegment {
private:
  uintptr_t _start;
  uintptr_t _end;

public:
  ZPhysicalMemorySegment(uintptr_t start, size_t size);

  uintptr_t start() const;
  uintptr_t end() const;
  size_t size() const;

  void expand(size_t size);
  ZPhysicalMemorySegment split(size_t size);
};

class ZPhysicalMemory {
private:
  size_t                  _nsegments;
  ZPhysicalMemorySegment* _segments;

public:
  ZPhysicalMemory();
  ZPhysicalMemory(size_t size);
  ZPhysicalMemory(const ZPhysicalMemorySegment& segment);

  bool is_null() const;
  size_t size() const;

  size_t nsegments() const;
  ZPhysicalMemorySegment segment(size_t index) const;
  void add_segment(ZPhysicalMemorySegment segment);

  ZPhysicalMemory split(size_t size);
  void clear();
};

class ZPhysicalMemoryManager {
  friend class VMStructs;

private:
  ZPhysicalMemoryBacking _backing;
  const size_t           _max_capacity;
  size_t                 _current_max_capacity;
  size_t                 _capacity;
  size_t                 _used;

  void nmt_commit(ZPhysicalMemory pmem, uintptr_t offset);
  void nmt_uncommit(ZPhysicalMemory pmem, uintptr_t offset);

public:
  ZPhysicalMemoryManager(size_t max_capacity);

  bool is_initialized() const;

  size_t max_capacity() const;
  size_t current_max_capacity() const;
  size_t capacity() const;
  size_t unused_capacity() const;

  void try_ensure_unused_capacity(size_t size);

  ZPhysicalMemory alloc(size_t size);
  void free(ZPhysicalMemory pmem);

  void map(ZPhysicalMemory pmem, uintptr_t offset);
  void unmap(ZPhysicalMemory pmem, uintptr_t offset);

  void debug_map(ZPhysicalMemory pmem, uintptr_t offset);
  void debug_unmap(ZPhysicalMemory pmem, uintptr_t offset);
};

#endif // SHARE_GC_Z_ZPHYSICALMEMORY_HPP
