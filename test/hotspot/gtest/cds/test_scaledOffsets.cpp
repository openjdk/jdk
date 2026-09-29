/*
 * Copyright (c) 2026 salesforce.com, inc. All Rights Reserved
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

#include "cds/aotCompressedPointers.hpp"
#include "unittest.hpp"
#include "utilities/globalDefinitions.hpp"

#include <cstdint>

TEST_VM(ScaledOffsetsTest, constants) {
#ifdef _LP64
  ASSERT_EQ((size_t)3, AOTCompressedPointers::MetadataOffsetShift);
  ASSERT_TRUE(is_aligned(AOTCompressedPointers::MaxMetadataOffsetBytes, (size_t)1 << AOTCompressedPointers::MetadataOffsetShift));
  ASSERT_EQ((size_t)(3584ULL * M), AOTCompressedPointers::MaxMetadataOffsetBytes);
#else
  ASSERT_EQ((size_t)0, AOTCompressedPointers::MetadataOffsetShift);
  ASSERT_EQ((size_t)0x7FFFFFFF, AOTCompressedPointers::MaxMetadataOffsetBytes);
#endif
}

TEST_VM(ScaledOffsetsTest, encode_decode_roundtrip) {
  // Test that encoding and decoding via get_byte_offset produces correct results
  const size_t unit = (size_t)1 << AOTCompressedPointers::MetadataOffsetShift;

  // Test that get_byte_offset correctly applies the shift
  // Note: We can't directly test encode_byte_offset as it's private, but we can verify
  // the shift value is applied correctly in get_byte_offset
  AOTCompressedPointers::narrowPtr np1 = static_cast<AOTCompressedPointers::narrowPtr>(1);
  ASSERT_EQ(unit, AOTCompressedPointers::get_byte_offset(np1));

  AOTCompressedPointers::narrowPtr np2 = static_cast<AOTCompressedPointers::narrowPtr>(2);
  ASSERT_EQ(2 * unit, AOTCompressedPointers::get_byte_offset(np2));

  AOTCompressedPointers::narrowPtr np1024 = static_cast<AOTCompressedPointers::narrowPtr>(1024);
  ASSERT_EQ(1024 * unit, AOTCompressedPointers::get_byte_offset(np1024));

#ifdef _LP64
  const uint64_t max_units = (uint64_t)UINT32_MAX;
  AOTCompressedPointers::narrowPtr np_max = static_cast<AOTCompressedPointers::narrowPtr>(UINT32_MAX);
  const uint64_t max_bytes = max_units << AOTCompressedPointers::MetadataOffsetShift;
  ASSERT_EQ(max_bytes, AOTCompressedPointers::get_byte_offset(np_max));
  ASSERT_GE(max_bytes, AOTCompressedPointers::MaxMetadataOffsetBytes - unit);
#endif
}

TEST_VM(ScaledOffsetsTest, null_handling) {
  // Test that null() returns 0
  ASSERT_EQ(static_cast<AOTCompressedPointers::narrowPtr>(0), AOTCompressedPointers::null());
  ASSERT_EQ((size_t)0, AOTCompressedPointers::get_byte_offset(AOTCompressedPointers::null()));
}
