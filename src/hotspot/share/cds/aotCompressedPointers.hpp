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

#ifndef SHARE_CDS_AOTCOMPRESSEDPOINTERS_HPP
#define SHARE_CDS_AOTCOMPRESSEDPOINTERS_HPP

#include "cds/cds_globals.hpp"
#include "memory/allStatic.hpp"
#include "memory/metaspace.hpp"
#include "metaprogramming/enableIf.hpp"
#include "utilities/align.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

class AOTCompressedPointers: public AllStatic {
public:
  // For space saving, we can encode the location of metadata objects in the "rw" and "ro"
  // regions using a 32-bit offset from the bottom of the mapped AOT metaspace.
  // Currently we allow only up to 2GB total size in the rw and ro regions (which are
  // contiguous to each other).
  enum class narrowPtr : u4;
  static constexpr size_t MaxMetadataOffsetBytes = 0x7FFFFFFF;

  // In the future, this could return a different numerical value than
  // narrowp if the encoding contains shifts.
  inline static size_t get_byte_offset(narrowPtr narrowp) {
    return checked_cast<size_t>(narrowp);
  }

  inline static narrowPtr null() {
    return static_cast<narrowPtr>(0);
  }

  // Encoding ------

  // ptr can point to one of the following
  // - an object in the ArchiveBuilder's buffer.
  // - an object in the currently mapped AOT cache rw/ro regions.
  // - an object that has been copied into the ArchiveBuilder's buffer.
  template <typename T>
  static narrowPtr encode_not_null(T ptr) {
    address p = reinterpret_cast<address>(ptr);
    return encode_byte_offset(compute_byte_offset(p));
  }

  template <typename T>
  static narrowPtr encode(T ptr) { // may be null
    if (ptr == nullptr) {
      return null();
    } else {
      return encode_not_null(ptr);
    }
  }

  // ptr must be in the currently mapped AOT cache rw/ro regions.
  template <typename T>
  static narrowPtr encode_address_in_cache(T ptr) {
    assert(Metaspace::in_aot_cache(ptr), "must be");
    address p = reinterpret_cast<address>(ptr);
    address base = reinterpret_cast<address>(SharedBaseAddress);
    return encode_byte_offset(pointer_delta(p, base, 1));
  }

  template <typename T>
  static narrowPtr encode_address_in_cache_or_null(T ptr) {
    if (ptr == nullptr) {
      return null();
    } else {
      return encode_address_in_cache<T>(ptr);
    }
  }

  // Decoding -----

  // If base_address is null, decode an address within the mapped aot cache range.
  template <typename T>
  static T decode_not_null(narrowPtr narrowp, address base_address = nullptr) {
    assert(narrowp != null(), "sanity");
    if (base_address == nullptr) {
      T p = reinterpret_cast<T>(reinterpret_cast<address>(SharedBaseAddress) + get_byte_offset(narrowp));
      assert(Metaspace::in_aot_cache(p), "must be");
      return p;
    } else {
      // This is usually called before the cache is fully mapped.
      return reinterpret_cast<T>(base_address + get_byte_offset(narrowp));
    }
  }

  template <typename T>
  static T decode(narrowPtr narrowp, address base_address = nullptr) { // may be null
    if (narrowp == null()) {
      return nullptr;
    } else {
      return decode_not_null<T>(narrowp, base_address);
    }
  }

private:
  static size_t compute_byte_offset(address p);

  static narrowPtr encode_byte_offset(size_t offset) {
    assert(offset != 0, "offset 0 is in protection zone");
    precond(offset <= MaxMetadataOffsetBytes);
    return checked_cast<narrowPtr>(offset);
  }
};

// Type casts -- declared as global functions to save a few keystrokes

// A simple type cast. No change in numerical value.
inline AOTCompressedPointers::narrowPtr cast_from_u4(u4 narrowp) {
  return checked_cast<AOTCompressedPointers::narrowPtr>(narrowp);
}

// A simple type cast. No change in numerical value.
// !!!DO NOT CALL THIS if you want a byte offset!!!
inline u4 cast_to_u4(AOTCompressedPointers::narrowPtr narrowp) {
  return checked_cast<u4>(narrowp);
}

#endif // SHARE_CDS_AOTCOMPRESSEDPOINTERS_HPP
