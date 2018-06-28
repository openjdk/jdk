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

#ifndef SHARE_GC_Z_ZFORWARDING_INLINE_HPP
#define SHARE_GC_Z_ZFORWARDING_INLINE_HPP

#include "gc/z/zForwardingTable.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zHash.inline.hpp"
#include "runtime/atomic.hpp"
#include "utilities/debug.hpp"

inline ZForwardingTable::ZForwardingTable() :
    _table(NULL),
    _size(0) {}

inline ZForwardingTable::~ZForwardingTable() {
  assert(is_null(), "Should be empty");
}

inline ZForwardingTableEntry ZForwardingTable::at(ZForwardingTableCursor* cursor) const {
  return _table[*cursor];
}

inline ZForwardingTableEntry ZForwardingTable::first(uintptr_t from_index, ZForwardingTableCursor* cursor) const {
  const size_t mask = _size - 1;
  const size_t hash = ZHash::uint32_to_uint32((uint32_t)from_index);
  *cursor = hash & mask;
  return at(cursor);
}

inline ZForwardingTableEntry ZForwardingTable::next(ZForwardingTableCursor* cursor) const {
  const size_t mask = _size - 1;
  *cursor = (*cursor + 1) & mask;
  return at(cursor);
}

inline bool ZForwardingTable::is_null() const {
  return _table == NULL;
}

inline ZForwardingTableEntry ZForwardingTable::find(uintptr_t from_index) const {
  ZForwardingTableCursor dummy;
  return find(from_index, &dummy);
}

inline ZForwardingTableEntry ZForwardingTable::find(uintptr_t from_index, ZForwardingTableCursor* cursor) const {
  // Reading entries in the table races with the atomic CAS done for
  // insertion into the table. This is safe because each entry is at
  // most updated once (from -1 to something else).
  ZForwardingTableEntry entry = first(from_index, cursor);
  while (!entry.is_empty()) {
    if (entry.from_index() == from_index) {
      // Match found, return matching entry
      return entry;
    }

    entry = next(cursor);
  }

  // Match not found, return empty entry
  return entry;
}

inline uintptr_t ZForwardingTable::insert(uintptr_t from_index, uintptr_t to_offset, ZForwardingTableCursor* cursor) {
  const ZForwardingTableEntry new_entry(from_index, to_offset);
  const ZForwardingTableEntry old_entry; // empty

  for (;;) {
    const ZForwardingTableEntry prev_entry = Atomic::cmpxchg(new_entry, _table + *cursor, old_entry);
    if (prev_entry.is_empty()) {
      // Success
      return to_offset;
    }

    // Find next empty or matching entry
    ZForwardingTableEntry entry = at(cursor);
    while (!entry.is_empty()) {
      if (entry.from_index() == from_index) {
        // Match found, return already inserted address
        return entry.to_offset();
      }

      entry = next(cursor);
    }
  }
}

#endif // SHARE_GC_Z_ZFORWARDING_INLINE_HPP
