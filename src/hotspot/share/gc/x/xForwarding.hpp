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

#ifndef SHARE_GC_X_XFORWARDING_HPP
#define SHARE_GC_X_XFORWARDING_HPP

#include "gc/x/xAttachedArray.hpp"
#include "gc/x/xForwardingEntry.hpp"
#include "gc/x/xLock.hpp"
#include "gc/x/xVirtualMemory.hpp"

class ObjectClosure;
class VMStructs;
class XForwardingAllocator;
class XPage;

typedef size_t XForwardingCursor;

class XForwarding {
  friend class ::VMStructs;
  friend class XForwardingTest;

private:
  typedef XAttachedArray<XForwarding, XForwardingEntry> AttachedArray;

  const XVirtualMemory   _virtual;
  const size_t           _object_alignment_shift;
  const AttachedArray    _entries;
  XPage*                 _page;
  mutable XConditionLock _ref_lock;
  volatile int32_t       _ref_count;
  bool                   _ref_abort;
  bool                   _in_place;

  XForwardingEntry* entries() const;
  XForwardingEntry at(XForwardingCursor* cursor) const;
  XForwardingEntry first(uintptr_t from_index, XForwardingCursor* cursor) const;
  XForwardingEntry next(XForwardingCursor* cursor) const;

  XForwarding(XPage* page, size_t nentries);

public:
  static uint32_t nentries(const XPage* page);
  static XForwarding* alloc(XForwardingAllocator* allocator, XPage* page);

  uint8_t type() const;
  uintptr_t start() const;
  size_t size() const;
  size_t object_alignment_shift() const;
  void object_iterate(ObjectClosure *cl);

  bool retain_page();
  XPage* claim_page();
  void release_page();
  bool wait_page_released() const;
  XPage* detach_page();
  void abort_page();

  void set_in_place();
  bool in_place() const;

  XForwardingEntry find(uintptr_t from_index, XForwardingCursor* cursor) const;
  uintptr_t insert(uintptr_t from_index, uintptr_t to_offset, XForwardingCursor* cursor);

  void verify() const;
};

#endif // SHARE_GC_X_XFORWARDING_HPP
