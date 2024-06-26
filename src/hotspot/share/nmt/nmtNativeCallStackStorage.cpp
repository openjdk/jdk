/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "memory/allocation.hpp"
#include "nmt/nmtNativeCallStackStorage.hpp"

NativeCallStackStorage::StackIndex NativeCallStackStorage::put(const NativeCallStack& value) {
  int bucket = value.calculate_hash() % _table_size;
  TableEntryIndex link = _table[bucket];
  while (link != TableEntryStorage::nil) {
    TableEntry& l = _entry_storage.at(link);
    if (value.equals(get(l.stack))) {
      return l.stack;
    }
    link = l.next;
  }
  int idx = _stacks.append(value);
  StackIndex si{idx};
  TableEntryIndex new_link = _entry_storage.allocate(_table[bucket], si);
  _table[bucket] = new_link;
  return si;
}
NativeCallStackStorage::NativeCallStackStorage(bool is_detailed_mode, int table_size)
  : _table_size(table_size),
    _table(nullptr),
    _stacks(),
    _is_detailed_mode(is_detailed_mode),
    _fake_stack() {
  if (_is_detailed_mode) {
    _table = NEW_C_HEAP_ARRAY(TableEntryIndex, _table_size, mtNMT);
    for (int i = 0; i < _table_size; i++) {
      _table[i] = TableEntryStorage::nil;
    }
  }
}
NativeCallStackStorage::~NativeCallStackStorage() {
  FREE_C_HEAP_ARRAY(LinkPtr, _table);
}
