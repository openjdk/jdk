/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/z/zForwardingTableEntry.hpp"
#include "memory/allocation.hpp"

typedef size_t ZForwardingTableCursor;

class ZForwardingTable {
  friend class VMStructs;
  friend class ZForwardingTableTest;

private:
  ZForwardingTableEntry* _table;
  size_t                 _size;

  ZForwardingTableEntry at(ZForwardingTableCursor* cursor) const;
  ZForwardingTableEntry first(uintptr_t from_index, ZForwardingTableCursor* cursor) const;
  ZForwardingTableEntry next(ZForwardingTableCursor* cursor) const;

public:
  ZForwardingTable();
  ~ZForwardingTable();

  bool is_null() const;
  void setup(size_t live_objects);
  void reset();

  ZForwardingTableEntry find(uintptr_t from_index) const;
  ZForwardingTableEntry find(uintptr_t from_index, ZForwardingTableCursor* cursor) const;
  uintptr_t insert(uintptr_t from_index, uintptr_t to_offset, ZForwardingTableCursor* cursor);

  void verify(size_t object_max_count, size_t live_objects) const;
};

#endif // SHARE_GC_Z_ZFORWARDING_HPP
