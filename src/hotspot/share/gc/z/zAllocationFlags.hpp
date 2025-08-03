/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZALLOCATIONFLAGS_HPP
#define SHARE_GC_Z_ZALLOCATIONFLAGS_HPP

#include "gc/z/zBitField.hpp"
#include "memory/allocation.hpp"

//
// Allocation flags layout
// -----------------------
//
//   7     2 1 0
//  +-----+-+-+-+
//  |00000|1|1|1|
//  +-----+-+-+-+
//  |      | | |
//  |      | | * 0-0 Non-Blocking Flag (1-bit)
//  |      | |
//  |      | * 1-1 GC Relocation Flag (1-bit)
//  |      |
//  |      * 2-2 Fast Medium Flag (1-bit)
//  |
//  * 7-3 Unused (5-bits)
//

class ZAllocationFlags {
private:
  typedef ZBitField<uint8_t, bool, 0, 1> field_non_blocking;
  typedef ZBitField<uint8_t, bool, 1, 1> field_gc_relocation;
  typedef ZBitField<uint8_t, bool, 2, 1> field_fast_medium;

  uint8_t _flags;

public:
  ZAllocationFlags()
    : _flags(0) {}

  void set_non_blocking() {
    _flags |= field_non_blocking::encode(true);
  }

  void set_gc_relocation() {
    _flags |= field_gc_relocation::encode(true);
  }

  void set_fast_medium() {
    _flags |= field_fast_medium::encode(true);
  }

  bool non_blocking() const {
    return field_non_blocking::decode(_flags);
  }

  bool gc_relocation() const {
    return field_gc_relocation::decode(_flags);
  }

  bool fast_medium() const {
    return field_fast_medium::decode(_flags);
  }
};

#endif // SHARE_GC_Z_ZALLOCATIONFLAGS_HPP
