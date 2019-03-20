/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZFORWARDINGENTRY_HPP
#define SHARE_GC_Z_ZFORWARDINGENTRY_HPP

#include "gc/z/zBitField.hpp"
#include "memory/allocation.hpp"
#include "metaprogramming/primitiveConversions.hpp"

//
// Forwarding entry layout
// -----------------------
//
//   6                      4 4                                             0
//   3                      2 1                                             0
//  +------------------------+-----------------------------------------------+
//  |11111111 11111111 111111|11 11111111 11111111 11111111 11111111 11111111|
//  +------------------------+-----------------------------------------------+
//  |                        |
//  |                        * 41-0 To Object Offset (42-bits)
//  |
//  * 63-42 From Object Index (22-bits)
//

class ZForwardingEntry {
  friend struct PrimitiveConversions;

private:
  typedef ZBitField<uint64_t, size_t, 0,  42> field_to_offset;
  typedef ZBitField<uint64_t, size_t, 42, 22> field_from_index;

  uint64_t _entry;

  static uintptr_t empty() {
    return (uintptr_t)-1;
  }

public:
  ZForwardingEntry() :
      _entry(empty()) {}

  ZForwardingEntry(size_t from_index, size_t to_offset) :
      _entry(field_from_index::encode(from_index) |
             field_to_offset::encode(to_offset)) {}

  bool is_empty() const {
    return _entry == empty();
  }

  size_t to_offset() const {
    return field_to_offset::decode(_entry);
  }

  size_t from_index() const {
    return field_from_index::decode(_entry);
  }
};

// Needed to allow atomic operations on ZForwardingEntry
template <>
struct PrimitiveConversions::Translate<ZForwardingEntry> : public TrueType {
  typedef ZForwardingEntry Value;
  typedef uint64_t         Decayed;

  static Decayed decay(Value v) {
    return v._entry;
  }

  static Value recover(Decayed d) {
    ZForwardingEntry entry;
    entry._entry = d;
    return entry;
  }
};

#endif // SHARE_GC_Z_ZFORWARDINGENTRY_HPP
