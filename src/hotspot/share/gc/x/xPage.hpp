/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_X_XPAGE_HPP
#define SHARE_GC_X_XPAGE_HPP

#include "gc/x/xList.hpp"
#include "gc/x/xLiveMap.hpp"
#include "gc/x/xPhysicalMemory.hpp"
#include "gc/x/xVirtualMemory.hpp"
#include "memory/allocation.hpp"

class VMStructs;

class XPage : public CHeapObj<mtGC> {
  friend class ::VMStructs;
  friend class XList<XPage>;

private:
  uint8_t            _type;
  uint8_t            _numa_id;
  uint32_t           _seqnum;
  XVirtualMemory     _virtual;
  volatile uintptr_t _top;
  XLiveMap           _livemap;
  uint64_t           _last_used;
  XPhysicalMemory    _physical;
  XListNode<XPage>   _node;

  void assert_initialized() const;

  uint8_t type_from_size(size_t size) const;
  const char* type_to_string() const;

  bool is_object_marked(uintptr_t addr) const;
  bool is_object_strongly_marked(uintptr_t addr) const;

public:
  XPage(const XVirtualMemory& vmem, const XPhysicalMemory& pmem);
  XPage(uint8_t type, const XVirtualMemory& vmem, const XPhysicalMemory& pmem);
  ~XPage();

  uint32_t object_max_count() const;
  size_t object_alignment_shift() const;
  size_t object_alignment() const;

  uint8_t type() const;
  uintptr_t start() const;
  uintptr_t end() const;
  size_t size() const;
  uintptr_t top() const;
  size_t remaining() const;

  const XVirtualMemory& virtual_memory() const;
  const XPhysicalMemory& physical_memory() const;
  XPhysicalMemory& physical_memory();

  uint8_t numa_id();

  bool is_allocating() const;
  bool is_relocatable() const;

  uint64_t last_used() const;
  void set_last_used();

  void reset();
  void reset_for_in_place_relocation();

  XPage* retype(uint8_t type);
  XPage* split(size_t size);
  XPage* split(uint8_t type, size_t size);
  XPage* split_committed();

  bool is_in(uintptr_t addr) const;

  bool is_marked() const;
  template <bool finalizable> bool is_object_marked(uintptr_t addr) const;
  bool is_object_live(uintptr_t addr) const;
  bool is_object_strongly_live(uintptr_t addr) const;
  bool mark_object(uintptr_t addr, bool finalizable, bool& inc_live);

  void inc_live(uint32_t objects, size_t bytes);
  uint32_t live_objects() const;
  size_t live_bytes() const;

  void object_iterate(ObjectClosure* cl);

  uintptr_t alloc_object(size_t size);
  uintptr_t alloc_object_atomic(size_t size);

  bool undo_alloc_object(uintptr_t addr, size_t size);
  bool undo_alloc_object_atomic(uintptr_t addr, size_t size);

  void print_on(outputStream* out) const;
  void print() const;

  void verify_live(uint32_t live_objects, size_t live_bytes) const;
};

class XPageClosure {
public:
  virtual void do_page(const XPage* page) = 0;
};

#endif // SHARE_GC_X_XPAGE_HPP
