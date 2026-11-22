/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "jfr/leakprofiler/checkpoint/logicalEdgeStore.hpp"

LogicalEdge::LogicalEdge(u8 parent_identity, Kind kind,
                         const InstanceKlass* field_holder, int index,
                         const Klass* value_klass) :
  _parent_identity(parent_identity), _kind(kind), _field_holder(field_holder),
  _index(index), _value_klass(value_klass) {
  assert(parent_identity != 0, "invariant");
  assert(value_klass != nullptr, "invariant");
  assert(kind == Field || kind == ArrayElement, "invariant");
  assert((kind == Field) == (field_holder != nullptr), "invariant");
  assert(index >= 0, "invariant");
}

uintptr_t LogicalEdge::hash() const {
  u8 hash = _parent_identity;
  hash = 31 * hash + static_cast<u8>(_kind);
  hash = 31 * hash + reinterpret_cast<uintptr_t>(_field_holder);
  hash = 31 * hash + static_cast<u8>(_index);
  // Include the prefix in the hash on 32-bit platforms as well.
  return static_cast<uintptr_t>(hash ^ (hash >> 32));
}

bool LogicalEdge::equals(const LogicalEdge& other) const {
  return _parent_identity == other._parent_identity &&
         _kind == other._kind &&
         _field_holder == other._field_holder &&
         _index == other._index;
}

// Allocates logical IDs in the BASED range first, then CODED, skipping aligned
// identities so they cannot conflict with real oop addresses.
static constexpr u8 based_base = UCONST64(0x401BA5ED00000000);
static constexpr u8 coded_base = UCONST64(0x401C0DED00000000);
static constexpr u8 relative_id_mask = UCONST64(0xFFFFFFFF);

u8 LogicalEdgeStore::_last_id = based_base;

LogicalEdgeStore::LogicalEdgeStore() : _lookup(nullptr), _table(this) {
  assert((_last_id & ~relative_id_mask) == based_base ||
         (_last_id & ~relative_id_mask) == coded_base, "invariant");
}

u8 LogicalEdgeStore::next_id() const {
  const u8 base = _last_id & ~relative_id_mask;
  const u8 relative_id = _last_id & relative_id_mask;
  // Switch ranges explicitly.
  // Make sure to never carry a relative ID into its prefix.
  if (relative_id == relative_id_mask) {
    return base == based_base ? coded_base + 1 : 0;
  }
  u8 id = _last_id + 1;
  // Real decoded oop addresses are aligned, including when they are stored as compressed oops.
  // Therefore our logical id scheme excludes aligned addresses and maps only onto unaligned ones.
  if ((id & MinObjAlignmentInBytesMask) == 0) {
    ++id;
  }
  assert((id & ~relative_id_mask) == base, "invariant");
  return id;
}

void LogicalEdgeStore::on_link(Entry* entry) {
  assert(entry != nullptr, "invariant");
  assert(entry->id() == 0, "invariant");
  const u8 id = next_id();
  assert(id != 0, "invariant");
  entry->set_id(id);
  _last_id = id;
}

bool LogicalEdgeStore::on_equals(uintptr_t hash, const Entry* entry) {
  assert(_lookup != nullptr, "invariant");
  assert(entry->hash() == hash, "invariant");
  return entry->value_addr()->equals(*_lookup);
}

void LogicalEdgeStore::on_unlink(Entry* entry) {
  // Nothing to do.
}

u8 LogicalEdgeStore::get_id(const LogicalEdge& edge) {
  assert(_lookup == nullptr, "invariant");
  _lookup = &edge;
  const uintptr_t hash = edge.hash();
  Entry* entry = _table.lookup_only(hash);
  if (entry == nullptr) {
    if (next_id() == 0) {
      _lookup = nullptr;
      return 0;
    }
    entry = &_table.put(hash, edge);
  }
  _lookup = nullptr;
  assert(entry->value_addr()->value_klass() == edge.value_klass(), "invariant");
  return entry->id();
}
