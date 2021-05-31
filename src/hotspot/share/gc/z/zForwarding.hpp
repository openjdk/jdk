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

#ifndef SHARE_GC_Z_ZFORWARDING_HPP
#define SHARE_GC_Z_ZFORWARDING_HPP

#include "gc/z/zAttachedArray.hpp"
#include "gc/z/zForwardingEntry.hpp"
#include "gc/z/zGenerationId.hpp"
#include "gc/z/zLock.hpp"
#include "gc/z/zVirtualMemory.hpp"

class ObjectClosure;
class ZForwardingAllocator;
class ZPage;

typedef size_t ZForwardingCursor;

class ZForwarding {
  friend class VMStructs;
  friend class ZForwardingTest;

private:
  typedef ZAttachedArray<ZForwarding, ZForwardingEntry> AttachedArray;

  const ZVirtualMemory   _virtual;
  const size_t           _object_alignment_shift;
  const AttachedArray    _entries;
  ZPage*                 _page;
  ZGenerationId          _generation_id;
  volatile bool          _claimed;
  mutable ZConditionLock _ref_lock;
  volatile int32_t       _ref_count;
  bool                   _ref_abort;
  bool                   _in_place;
  bool                   _remset_scanned;

  // Debugging
  volatile Thread*       _in_place_thread;
  zoffset                _in_place_old_top;
  ZPage*                 _detached_page;

  ZForwardingEntry* entries() const;
  ZForwardingEntry at(ZForwardingCursor* cursor) const;
  ZForwardingEntry first(uintptr_t from_index, ZForwardingCursor* cursor) const;
  ZForwardingEntry next(ZForwardingCursor* cursor) const;

  template <typename Function>
  void object_iterate_forwarded_via_livemap(Function function);
  template <typename Function>
  void object_iterate_forwarded_via_table(Function function);

  ZForwarding(ZPage* page, size_t nentries);

public:
  static uint32_t nentries(const ZPage* page);
  static ZForwarding* alloc(ZForwardingAllocator* allocator, ZPage* page);

  uint8_t type() const;
  ZGenerationId generation_id() const;
  zoffset start() const;
  size_t size() const;
  size_t object_alignment_shift() const;

  // Visit from-objects
  template <typename Function>
  void object_iterate(Function function);

  // Visit to-objects
  template <typename Function>
  void object_iterate_forwarded(Function function);

  template <typename Function>
  void oops_do_in_forwarded(Function function);

  template <typename Function>
  void oops_do_in_forwarded_via_table(Function function);

  bool claim();

  void set_in_place_relocation();
  void clear_in_place_relocation();
  bool is_below_in_place_relocation_top(zoffset addr) const;

  bool retain_page();
  ZPage* claim_page_for_in_place_relocation();
  void release_page();
  bool wait_page_released() const;
  ZPage* detach_page();
  ZPage* page();
  void abort_page();

  bool in_place() const;

  zaddress find(zaddress_unsafe addr);

  ZForwardingEntry find(uintptr_t from_index, ZForwardingCursor* cursor) const;
  zoffset insert(uintptr_t from_index, zoffset to_offset, ZForwardingCursor* cursor);

  void verify() const;

  bool get_and_set_remset_scanned();
};

#endif // SHARE_GC_Z_ZFORWARDING_HPP
