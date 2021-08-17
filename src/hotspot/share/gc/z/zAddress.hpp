/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZADDRESS_HPP
#define SHARE_GC_Z_ZADDRESS_HPP

#include "memory/allocation.hpp"

// Offsets
// - Virtual address range offsets
// - Physical memory offsets
enum class zoffset         : uintptr_t {};

// Colored oop
enum class zpointer        : uintptr_t { null = 0 };

// Uncolored oop - safe to dereference
enum class zaddress        : uintptr_t { null = 0 };

// Uncolored oop - not safe to dereference
enum class zaddress_unsafe : uintptr_t { null = 0 };

// Pointer part of address
extern size_t    ZAddressOffsetBits;
const  size_t    ZAddressOffsetShift = 0;
extern uintptr_t ZAddressOffsetMask;
extern zoffset   ZAddressOffsetMax;
extern size_t    ZAddressOffsetMaxSize;

class ZOffset : public AllStatic {
public:
  static zaddress address(zoffset offset);
  static zaddress_unsafe address_unsafe(zoffset offset);
};

class ZPointer : public AllStatic {
public:
  static zaddress uncolor(zpointer ptr);
  static zaddress uncolor_store_good(zpointer ptr);
  static zaddress_unsafe uncolor_unsafe(zpointer ptr);
  static zpointer set_remset_bits(zpointer ptr);

  static bool is_load_bad(zpointer ptr);
  static bool is_load_good(zpointer ptr);
  static bool is_load_good_or_null(zpointer ptr);

  static bool is_major_load_good(zpointer ptr);
  static bool is_minor_load_good(zpointer ptr);

  static bool is_mark_bad(zpointer ptr);
  static bool is_mark_good(zpointer ptr);
  static bool is_mark_good_or_null(zpointer ptr);

  static bool is_store_bad(zpointer ptr);
  static bool is_store_good(zpointer ptr);
  static bool is_store_good_or_null(zpointer ptr);

  static bool is_marked_finalizable(zpointer ptr);
  static bool is_marked_major(zpointer ptr);
  static bool is_marked_minor(zpointer ptr);
  static bool is_marked_any_major(zpointer ptr);
  static bool is_remapped(zpointer ptr);
  static bool is_remembered_exact(zpointer ptr);

  static constexpr int load_shift_lookup_index(uintptr_t value);
  static constexpr int load_shift_lookup(uintptr_t value);
};

class ZAddress : public AllStatic {
public:
  static zpointer color(zaddress addr, uintptr_t color);

  static zoffset offset(zaddress addr);
  static zoffset offset(zaddress_unsafe addr);

  static zpointer load_good(zaddress addr, zpointer prev);
  static zpointer finalizable_good(zaddress addr, zpointer prev);
  static zpointer mark_good(zaddress addr, zpointer prev);
  static zpointer mark_major_good(zaddress addr, zpointer prev);
  static zpointer mark_minor_good(zaddress addr, zpointer prev);
  static zpointer store_good(zaddress addr);
  static zpointer store_good_or_null(zaddress addr);
};

class ZGlobalsPointers : public AllStatic {
  friend class ZAddressTest;

private:
  static void set_good_masks();

public:
  static void initialize();

  static void flip_minor_mark_start();
  static void flip_minor_relocate_start();
  static void flip_major_mark_start();
  static void flip_major_relocate_start();
};

#endif // SHARE_GC_Z_ZADDRESS_HPP
