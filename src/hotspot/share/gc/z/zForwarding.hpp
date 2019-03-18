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

#ifndef SHARE_GC_Z_ZFORWARDING_HPP
#define SHARE_GC_Z_ZFORWARDING_HPP

#include "gc/z/zForwardingEntry.hpp"

typedef uint32_t ZForwardingCursor;

class ZForwarding {
  friend class VMStructs;
  friend class ZForwardingTest;

private:
  const uintptr_t   _start;
  const size_t      _object_alignment_shift;
  const uint32_t    _nentries;
  volatile uint32_t _refcount;
  volatile bool     _pinned;

  ZForwardingEntry* entries() const;
  ZForwardingEntry at(ZForwardingCursor* cursor) const;
  ZForwardingEntry first(uintptr_t from_index, ZForwardingCursor* cursor) const;
  ZForwardingEntry next(ZForwardingCursor* cursor) const;

  ZForwarding(uintptr_t start, size_t object_alignment_shift, uint32_t nentries);

public:
  static ZForwarding* create(uintptr_t start, size_t object_alignment_shift, uint32_t live_objects);
  static void destroy(ZForwarding* forwarding);

  uintptr_t start() const;
  size_t object_alignment_shift() const;

  bool inc_refcount();
  bool dec_refcount();

  bool is_pinned() const;
  void set_pinned();

  ZForwardingEntry find(uintptr_t from_index) const;
  ZForwardingEntry find(uintptr_t from_index, ZForwardingCursor* cursor) const;
  uintptr_t insert(uintptr_t from_index, uintptr_t to_offset, ZForwardingCursor* cursor);

  void verify(uint32_t object_max_count, uint32_t live_objects) const;
};

#endif // SHARE_GC_Z_ZFORWARDING_HPP
