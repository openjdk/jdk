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

#include "precompiled.hpp"
#include "gc/z/zForwardingTable.inline.hpp"
#include "gc/z/zUtils.inline.hpp"
#include "memory/allocation.inline.hpp"
#include "utilities/debug.hpp"

void ZForwardingTable::setup(size_t live_objects) {
  assert(is_null(), "Should be empty");
  assert(live_objects > 0, "Invalid size");

  // Allocate table for linear probing. The size of the table must be
  // a power of two to allow for quick and inexpensive indexing/masking.
  // The table is sized to have a load factor of 50%, i.e. sized to have
  // double the number of entries actually inserted.
  _size = ZUtils::round_up_power_of_2(live_objects * 2);
  _table = MallocArrayAllocator<ZForwardingTableEntry>::allocate(_size, mtGC);

  // Construct table entries
  for (size_t i = 0; i < _size; i++) {
    ::new (_table + i) ZForwardingTableEntry();
  }
}

void ZForwardingTable::reset() {
  // Destruct table entries
  for (size_t i = 0; i < _size; i++) {
    (_table + i)->~ZForwardingTableEntry();
  }

  // Free table
  MallocArrayAllocator<ZForwardingTableEntry>::free(_table);
  _table = NULL;
  _size = 0;
}

void ZForwardingTable::verify(size_t object_max_count, size_t live_objects) const {
  size_t count = 0;

  for (size_t i = 0; i < _size; i++) {
    const ZForwardingTableEntry entry = _table[i];
    if (entry.is_empty()) {
      // Skip empty entries
      continue;
    }

    // Check from index
    guarantee(entry.from_index() < object_max_count, "Invalid from index");

    // Check for duplicates
    for (size_t j = i + 1; j < _size; j++) {
      const ZForwardingTableEntry other = _table[j];
      guarantee(entry.from_index() != other.from_index(), "Duplicate from");
      guarantee(entry.to_offset() != other.to_offset(), "Duplicate to");
    }

    count++;
  }

  // Check number of non-null entries
  guarantee(live_objects == count, "Count mismatch");
}
