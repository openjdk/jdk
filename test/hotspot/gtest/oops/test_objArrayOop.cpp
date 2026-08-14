/*
 * Copyright Amazon.com Inc. or its affiliates. All rights reserved.
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
 */

#include "oops/objArrayOop.hpp"
#include "oops/refArrayOop.hpp"
#include "oops/flatArrayOop.hpp"
#include "unittest.hpp"
#include "utilities/globalDefinitions.hpp"

TEST_VM(objArrayOop, osize_refarray) {
  struct ObjArrayStruct {
    int objal;  // Object alignment in bytes
    bool coops; // UseCompressedOops
    bool coh;   // UseCompactObjectHeaders
    int result; // Expected size in heap words
  };

  static const ObjArrayStruct refCases[] = {
#ifdef _LP64
    { 8,          false, false,   3 },  // 16 byte header, 8 byte oops
    { 8,          true,  false,   3 },  // 16 byte header, 4 byte oops
    { 8,          false, true,    3 },  // 12 byte header, 8 byte oops
    { 8,          true,  true,    2 },  // 12 byte header, 4 byte oops
    { 16,         false, false,   4 },  // 16 byte header, 8 byte oops, 16-byte align
    { 16,         true,  false,   4 },  // 16 byte header, 4 byte oops, 16-byte align
    { 16,         false, true,    4 },  // 12 byte header, 8 byte oops, 16-byte align
    { 16,         true,  true,    2 },  // 12 byte header, 4 byte oops, 16-byte align
    { 256,        false, false,  32 }, // 16 byte header, 8 byte oops, 256-byte align
    { 256,        true,  false,  32 }, // 16 byte header, 4 byte oops, 256-byte align
    { 256,        false, true,   32 }, // 12 byte header, 8 byte oops, 256-byte align
    { 256,        true,  true,   32 }, // 12 byte header, 4 byte oops, 256-byte align
#else
    { 8,          false, false,   4 }, // 12 byte header, 4 byte oops, wordsize 4
#endif
  };

  for (const ObjArrayStruct c : refCases) {
    if (c.objal == (int)ObjectAlignmentInBytes && c.coops == UseCompressedOops &&
        c.coh == UseCompactObjectHeaders) {
      EXPECT_EQ(refArrayOopDesc::object_size(1), (size_t)c.result);
    }
  }
}

static int make_flat_array_layout_helper(int payload_size_bytes, bool null_free) {
  BasicType etype = T_FLAT_ELEMENT;
  int esize = log2i_exact(round_up_power_of_2(payload_size_bytes));
  int hsize = arrayOopDesc::base_offset_in_bytes(etype);
  return Klass::array_layout_helper(Klass::_lh_array_tag_flat_value, null_free, hsize, etype, esize);
}

TEST_VM(objArrayOop, osize_flatarray) {
  struct FlatArraySizeCase {
    int objal;        // Object alignment in bytes
    int payload_size; // Raw inline payload size
    bool null_free;   // Null free
    int result;       // Expected size in heap words
  };

  static const FlatArraySizeCase flatCases[] = {
#ifdef _LP64
 {8,   4, false, 3},
 {8,   8, false, 3},
 {8,   4, true,  3},
 {8,   8, true,  3},
 {16,  4, false, 4},
 {16,  8, false, 4},
 {16,  4, true,  4},
 {16,  8, true,  4},
 {256, 4, false, 32},
 {256, 8, false, 32},
 {256, 4, true,  32},
 {256, 8, true,  32},
#endif
  };

  for (const FlatArraySizeCase c : flatCases) {
    if (c.objal == (int)ObjectAlignmentInBytes) {
      int lh = make_flat_array_layout_helper(c.payload_size, c.null_free);
      EXPECT_EQ(flatArrayOopDesc::object_size(lh, 1), c.result);
    }
  }
}
