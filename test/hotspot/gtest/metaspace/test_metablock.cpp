/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "memory/metaspace/metablock.inline.hpp"
//#define LOG_PLEASE
#include "metaspaceGtestCommon.hpp"

using metaspace::MetaBlock;


#define CHECK_BLOCK_EMPTY(block) { \
  EXPECT_TRUE(block.is_empty()); \
  DEBUG_ONLY(block.verify()); \
}

#define CHECK_BLOCK(block, expected_base, expected_size) { \
    EXPECT_EQ(block.base(), (MetaWord*)expected_base); \
    EXPECT_EQ((size_t)expected_size, block.word_size()); \
    EXPECT_EQ(block.end(), expected_base + expected_size); \
    DEBUG_ONLY(block.verify()); \
}

static constexpr uintptr_t large_pointer = NOT_LP64(0x99999990) LP64_ONLY(0x9999999999999990ULL);

TEST(metaspace, MetaBlock_1) {
  MetaBlock bl;
  CHECK_BLOCK_EMPTY(bl);
}

TEST(metaspace, MetaBlock_2) {
  MetaWord* const p = (MetaWord*)large_pointer;
  constexpr size_t s = G;
  MetaBlock bl(p, s);
  CHECK_BLOCK(bl, p, s);
}

TEST(metaspace, MetaBlock_3) {
  MetaWord* const p = (MetaWord*)large_pointer;
  MetaBlock bl(p, 0);
  CHECK_BLOCK_EMPTY(bl);
}

TEST_VM(metaspace, MetaBlock_4) {
  MetaWord* const p = (MetaWord*)large_pointer;
  MetaBlock bl(p, G);
  CHECK_BLOCK(bl, p, G);

  MetaBlock bl_copy = bl, bl2;

  bl2 = bl.split_off_tail(M);
  CHECK_BLOCK(bl, p, G - M);
  CHECK_BLOCK(bl2, p + G - M, M);

  bl = bl_copy;

bl.print_on(tty);
bl2.print_on(tty);
  bl2 = bl.split_off_tail(G);
  bl.print_on(tty);
  bl2.print_on(tty);

  ASSERT_EQ(bl2, bl_copy);
  ASSERT_TRUE(bl.is_empty());

  bl = bl_copy;

  bl2 = bl.split_off_tail(0);
  ASSERT_EQ(bl, bl_copy);
  ASSERT_TRUE(bl2.is_empty());

  MetaBlock empty;
  bl = empty.split_off_tail(0);
  ASSERT_TRUE(bl.is_empty());
}
