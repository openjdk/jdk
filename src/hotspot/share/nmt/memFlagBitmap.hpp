/*
 * Copyright (c) 2023, 2024, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_NMT_MEMFLAGBITMAP_HPP
#define SHARE_NMT_MEMFLAGBITMAP_HPP

#include "nmt/memflags.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

class MemFlagBitmap {
  uint32_t _v;
  STATIC_ASSERT(sizeof(_v) * BitsPerByte >= mt_number_of_types);

public:
  MemFlagBitmap(uint32_t v = 0) : _v(v) {}
  MemFlagBitmap(const MemFlagBitmap& o) : _v(o._v) {}

  uint32_t raw_value() const { return _v; }

  void set_flag(MEMFLAGS f) {
    const int bitno = (int)f;
    _v |= nth_bit(bitno);
  }

  bool has_flag(MEMFLAGS f) const {
    const int bitno = (int)f;
    return _v & nth_bit(bitno);
  }

  bool has_any() const { return _v > 0; }
};

#endif // SHARE_NMT_NMTUSAGE_HPP
