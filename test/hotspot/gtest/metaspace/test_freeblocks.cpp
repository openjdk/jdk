/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 SAP SE. All rights reserved.
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

#include "memory/metaspace/counters.hpp"
#include "memory/metaspace/freeBlocks.hpp"
#include "memory/metaspace/metablock.hpp"

//#define LOG_PLEASE
#include "metaspaceGtestCommon.hpp"

using metaspace::FreeBlocks;
using metaspace::MetaBlock;
using metaspace::SizeCounter;

#define CHECK_CONTENT(fb, num_blocks_expected, word_size_expected) \
{ \
  if (word_size_expected > 0) { \
    EXPECT_FALSE(fb.is_empty()); \
  } else { \
    EXPECT_TRUE(fb.is_empty()); \
  } \
  EXPECT_EQ(fb.total_size(), (size_t)word_size_expected); \
  EXPECT_EQ(fb.count(), (int)num_blocks_expected); \
}

TEST_VM(metaspace, freeblocks_basics) {

  FreeBlocks fbl;
  MetaWord tmp[1024];
  CHECK_CONTENT(fbl, 0, 0);

  MetaBlock bl(tmp, 1024);
  fbl.add_block(bl);
  DEBUG_ONLY(fbl.verify();)
  ASSERT_FALSE(fbl.is_empty());
  CHECK_CONTENT(fbl, 1, 1024);

  MetaBlock bl2 = fbl.remove_block(1024);
  ASSERT_EQ(bl, bl2);
  DEBUG_ONLY(fbl.verify();)
  CHECK_CONTENT(fbl, 0, 0);

}
