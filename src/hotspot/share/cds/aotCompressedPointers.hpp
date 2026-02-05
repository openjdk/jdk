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

#include "memory/allStatic.hpp"
#include "utilities/align.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

class AOTCompressedPointers: public AllStatic {
#if 0
  static address _encoding_base;
  static address _encoding_top;
#endif

  static size_t compute_byte_offset(address p);

public:
  // For space saving, we can encode the location of metadata objects in the "rw" and "ro"
  // regions using a 32-bit offset from the bottom of the "rw" region.
  // Currently we allow only up to 2GB total size in the rw and ro regions (which are
  // contiguous to each other).
  typedef u4 narrowPtr;
  static constexpr uintx MaxMetadataOffsetBytes = 0x7FFFFFFF;

  // Encoding
  static void set_encoding_range(address encoding_base, address encoding_top);

  static narrowPtr encode_byte_offset(size_t offset) {
    assert(offset != 0, "offset 0 is in protection zone");
    precond(offset <= MaxMetadataOffsetBytes);
    return checked_cast<narrowPtr>(offset);
  }

  // ptr can point to one of the following
  // - an object in the ArchiveBuilder's buffer.
  // - an object in the currently mapped AOT cache.
  // - an object that has been copied into the ArchiveBuilder's buffer.
  template <typename T>
  static narrowPtr encode_not_null(T ptr) {
    address p = reinterpret_cast<address>(ptr);
    return encode_byte_offset(compute_byte_offset(p));
  }

  template <typename T>
  static narrowPtr encode(T ptr) {
    if (ptr == nullptr) {
      return 0;
    } else {
      return encode_not_null(ptr);
    }
  }
};

#endif // SHARE_CDS_AOTCOMPRESSEDPOINTERS_HPP
