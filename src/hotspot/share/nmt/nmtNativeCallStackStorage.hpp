/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

#ifndef SHARE_NMT_NMTNATIVECALLSTACKSTORAGE_HPP
#define SHARE_NMT_NMTNATIVECALLSTACKSTORAGE_HPP

#include "nmt/arrayWithFreeList.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/nativeCallStack.hpp"
#include <limits>

// Virtual memory regions that are tracked by NMT also have their NativeCallStack (NCS) tracked.
// NCS:s are:
// - Fairly large
// - Regularly compared for equality
// - Read a lot when a detailed report is printed
// Therefore we'd like:
// - To not store duplicates
// - Have fast comparisons
// - Have constant time access
// We achieve this by using a closed hashtable for finding previously existing NCS:s and referring to them by an index that's smaller than a pointer.
class NativeCallStackStorage : public CHeapObjBase {
public:
  using StackIndex = int;
  constexpr static const StackIndex invalid = std::numeric_limits<StackIndex>::max() - 1;

  static bool equals(const StackIndex a, const StackIndex b) {
    return a == b;
  }

  static bool is_invalid(StackIndex a) {
    return a == invalid;
  }

private:
  struct TableEntry;
  using TableEntryStorage = ArrayWithFreeList<TableEntry, mtNMT>;
  using TableEntryIndex = typename TableEntryStorage::I;

  TableEntryStorage _entry_storage;

  struct TableEntry {
    TableEntryIndex next;
    StackIndex stack;
  };

  StackIndex put(const NativeCallStack& value);

  // Pick a prime number of buckets.
  // 4099 gives a 50% probability of collisions at 76 stacks (as per birthday problem).
  static const constexpr int default_table_size = 4099;
  const int _table_size;
  TableEntryIndex* _table;
  GrowableArrayCHeap<NativeCallStack, mtNMT> _stacks;
  const bool _is_detailed_mode;

  const NativeCallStack _fake_stack;
public:

  StackIndex push(const NativeCallStack& stack) {
    // Not in detailed mode, so not tracking stacks.
    if (!_is_detailed_mode) {
      return invalid;
    }
    return put(stack);
  }

  const inline NativeCallStack& get(StackIndex si) {
    if (is_invalid(si)) {
      return _fake_stack;
    }
    return _stacks.at(si);
  }

  NativeCallStackStorage(bool is_detailed_mode, int table_size = default_table_size);

  ~NativeCallStackStorage();
};

#endif // SHARE_NMT_NMTNATIVECALLSTACKSTORAGE_HPP
