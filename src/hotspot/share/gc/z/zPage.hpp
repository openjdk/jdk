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

#ifndef SHARE_GC_Z_ZPAGE_HPP
#define SHARE_GC_Z_ZPAGE_HPP

#include "gc/z/zList.hpp"
#include "gc/z/zLiveMap.hpp"
#include "gc/z/zPhysicalMemory.hpp"
#include "gc/z/zVirtualMemory.hpp"
#include "memory/allocation.hpp"

class ZPage : public CHeapObj<mtGC> {
  friend class VMStructs;
  friend class ZList<ZPage>;

private:
  // Always hot
  const uint8_t        _type;             // Page type
  uint8_t              _numa_id;          // NUMA node affinity
  uint32_t             _seqnum;           // Allocation sequence number
  const ZVirtualMemory _virtual;          // Virtual start/end address
  volatile uintptr_t   _top;              // Virtual top address
  ZLiveMap             _livemap;          // Live map

  // Hot when relocated and cached
  ZPhysicalMemory      _physical;         // Physical memory for page
  ZListNode<ZPage>     _node;             // Page list node

  const char* type_to_string() const;

  bool is_object_marked(uintptr_t addr) const;
  bool is_object_strongly_marked(uintptr_t addr) const;

public:
  ZPage(uint8_t type, ZVirtualMemory vmem, ZPhysicalMemory pmem);
  ~ZPage();

  uint32_t object_max_count() const;
  size_t object_alignment_shift() const;
  size_t object_alignment() const;

  uint8_t type() const;
  uintptr_t start() const;
  uintptr_t end() const;
  size_t size() const;
  uintptr_t top() const;
  size_t remaining() const;

  uint8_t numa_id();

  ZPhysicalMemory& physical_memory();
  const ZVirtualMemory& virtual_memory() const;

  void reset();

  bool is_in(uintptr_t addr) const;

  uintptr_t block_start(uintptr_t addr) const;
  bool block_is_obj(uintptr_t addr) const;

  bool is_allocating() const;
  bool is_relocatable() const;

  bool is_mapped() const;
  void set_pre_mapped();

  bool is_marked() const;
  bool is_object_live(uintptr_t addr) const;
  bool is_object_strongly_live(uintptr_t addr) const;
  bool mark_object(uintptr_t addr, bool finalizable, bool& inc_live);

  void inc_live_atomic(uint32_t objects, size_t bytes);
  uint32_t live_objects() const;
  size_t live_bytes() const;

  void object_iterate(ObjectClosure* cl);

  uintptr_t alloc_object(size_t size);
  uintptr_t alloc_object_atomic(size_t size);

  bool undo_alloc_object(uintptr_t addr, size_t size);
  bool undo_alloc_object_atomic(uintptr_t addr, size_t size);

  void print_on(outputStream* out) const;
  void print() const;
};

#endif // SHARE_GC_Z_ZPAGE_HPP
