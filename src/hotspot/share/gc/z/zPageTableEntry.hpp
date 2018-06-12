/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZPAGETABLEENTRY_HPP
#define SHARE_GC_Z_ZPAGETABLEENTRY_HPP

#include "gc/z/zBitField.hpp"
#include "memory/allocation.hpp"

//
// Page table entry layout
// -----------------------
//
//   6
//   3                                                                    1 0
//  +----------------------------------------------------------------------+-+
//  |11111111 11111111 11111111 11111111 11111111 11111111 11111111 1111111|1|
//  +----------------------------------------------------------------------+-+
//  |                                                                      |
//  |                                          0-0 Relocating Flag (1-bit) *
//  |
//  |
//  |
//  * 63-1 Page address (63-bits)
//

class ZPage;

class ZPageTableEntry {
private:
  typedef ZBitField<uint64_t, bool,   0, 1>     field_relocating;
  typedef ZBitField<uint64_t, ZPage*, 1, 63, 1> field_page;

  uint64_t _entry;

public:
  ZPageTableEntry() :
      _entry(0) {}

  ZPageTableEntry(ZPage* page, bool relocating) :
      _entry(field_page::encode(page) |
             field_relocating::encode(relocating)) {}

  bool relocating() const {
    return field_relocating::decode(_entry);
  }

  ZPage* page() const {
    return field_page::decode(_entry);
  }
};

#endif // SHARE_GC_Z_ZPAGETABLEENTRY_HPP
