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

#include "cds/archiveBuilder.hpp"
#include "cds/archiveUtils.hpp"
#include "unittest.hpp"
#include "utilities/globalDefinitions.hpp"

#include <cstdint>

TEST_VM(ScaledOffsetsTest, constants) {
  ASSERT_EQ(3, ArchiveUtils::OFFSET_SHIFT);
  ASSERT_EQ(ArchiveUtils::MAX_SHARED_DELTA, ArchiveBuilder::MAX_SHARED_DELTA);

#ifdef _LP64
  ASSERT_TRUE(is_aligned(ArchiveUtils::MAX_SHARED_DELTA, (uintx)1 << ArchiveUtils::OFFSET_SHIFT));
  ASSERT_EQ((uintx)(32ULL * G), ArchiveUtils::MAX_SHARED_DELTA);
#else
  ASSERT_EQ((uintx)0x7FFFFFFF, ArchiveUtils::MAX_SHARED_DELTA);
#endif
}

TEST_VM(ScaledOffsetsTest, to_offset_u4) {
  const uintx unit = (uintx)1 << ArchiveUtils::OFFSET_SHIFT;

  ASSERT_EQ((u4)0, ArchiveBuilder::to_offset_u4(0));
  ASSERT_EQ((u4)1, ArchiveBuilder::to_offset_u4(unit));
  ASSERT_EQ((u4)2, ArchiveBuilder::to_offset_u4(2 * unit));
  ASSERT_EQ((u4)1024, ArchiveBuilder::to_offset_u4(1024 * unit));

#ifdef _LP64
  const uint64_t max_units = (uint64_t)UINT32_MAX;
  const uint64_t max_bytes = max_units << ArchiveUtils::OFFSET_SHIFT;
  ASSERT_EQ((u4)UINT32_MAX, ArchiveBuilder::to_offset_u4((uintx)max_bytes));
  ASSERT_GE((uintx)max_bytes, ArchiveUtils::MAX_SHARED_DELTA - unit);
#endif
}

#ifdef ASSERT
TEST_VM_ASSERT_MSG(ScaledOffsetsTest, to_offset_u4_unaligned,
                   ".*offset not aligned for scaled encoding.*") {
  ArchiveBuilder::to_offset_u4(1);
}

TEST_VM_ASSERT_MSG(ScaledOffsetsTest, to_offset_u4_too_large,
                   ".*offset units must fit in u4.*") {
  const uintx offset_units = (uintx)UINT32_MAX + 1;
  const uintx offset_bytes = offset_units << ArchiveUtils::OFFSET_SHIFT;
  ArchiveBuilder::to_offset_u4(offset_bytes);
}
#endif // ASSERT
