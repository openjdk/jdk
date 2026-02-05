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
  // regions using a 32-bit offset from the bottom of the "rw" region.
  // Currently we allow only up to 2GB total size in the rw and ro regions (which are
  // contiguous to each other).
  enum class narrowPtr : u4;
  static constexpr size_t MaxMetadataOffsetBytes = 0x7FFFFFFF;

  // Type conversion -----

  // T must be an unsigned type whose value is less than 0xFFFFFFFF.
  // A simple type cast. No change in numerical value.
  template <typename T, ENABLE_IF(!std::is_signed<T>::value)>
  static narrowPtr cast_to_narrowPtr(T narrowp) {
    return checked_cast<narrowPtr>(narrowp);
  }

  // T must be an unsigned type of at least 32 bits.
  // A simple type cast. No change in numerical value.
  template <typename T, ENABLE_IF(!std::is_signed<T>::value)>
  static T cast_from_narrowPtr(narrowPtr narrowp) {
    return checked_cast<T>(narrowp);
  }

  // Convert narrowp to a byte offset. In the future, this could return
  // a different integer than narrowp if the encoding contains right shifts.
  template <typename T, ENABLE_IF(!std::is_signed<T>::value)>
  static T get_byte_offset(narrowPtr narrowp) {
    return checked_cast<T>(narrowp);
  }

  static narrowPtr null_narrowPtr() {
    return cast_to_narrowPtr<u4>(0);
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
  static narrowPtr encode(T ptr) {
    if (ptr == nullptr) {
      return null_narrowPtr();
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
  static narrowPtr encode_null_or_address_cache(T p) {
    if (p == nullptr) {
      return null_narrowPtr();
    } else {
      return encode_address_in_cache<T>(p);
    }
  }

  // Decoding -----

  template <typename T>
  static T decode_not_null(address base_address, narrowPtr narrowp) {
    assert(narrowp != null_narrowPtr(), "sanity");
    T p = (T)(base_address + cast_from_narrowPtr<size_t>(narrowp));
    // p may not be in AOT cache as this function may be called before the
    // AOT cache is mapped.
    return p;
  }

  template <typename T>
  static T decode_not_null(narrowPtr narrowp) {
    T fullp = decode_not_null<T>(reinterpret_cast<address>(SharedBaseAddress), narrowp);
    assert(Metaspace::in_aot_cache(fullp), "must be");
    return fullp;
  }

  template <typename T>
  static T decode(narrowPtr narrowp) {
    if (narrowp == null_narrowPtr()) {
      return nullptr;
    } else {
      return decode_not_null<T>(narrowp);
    }
  }

  template <typename T>
  static T decode(address base_address, narrowPtr narrowp) {
    if (narrowp == null_narrowPtr()) {
      return nullptr;
    } else {
      return decode_not_null<T>(base_address, narrowp);
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

// Global functions to save a few keystrokes

template <typename T> AOTCompressedPointers::narrowPtr cast_to_narrowPtr(T narrowp) {
  return AOTCompressedPointers::cast_to_narrowPtr<T>(narrowp);
}

template <typename T> T cast_from_narrowPtr(AOTCompressedPointers::narrowPtr narrowp) {
  return AOTCompressedPointers::cast_from_narrowPtr<T>(narrowp);
}

inline u4 to_u4(AOTCompressedPointers::narrowPtr narrowp) {
  return cast_from_narrowPtr<u4>(narrowp);
}

#endif // SHARE_CDS_AOTCOMPRESSEDPOINTERS_HPP
