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
#include "memory/metaspace/binList.hpp"
#include "memory/metaspace/counters.hpp"
//#define LOG_PLEASE
#include "metaspaceGtestCommon.hpp"

using metaspace::BinList32;
using metaspace::BinListImpl;
using metaspace::MemRangeCounter;

#define CHECK_BL_CONTENT(bl, expected_num, expected_size) { \
  EXPECT_EQ(bl.count(), (unsigned)expected_num); \
  EXPECT_EQ(bl.total_size(), (size_t)expected_size); \
  if (expected_num == 0) { \
    EXPECT_TRUE(bl.is_empty()); \
  } else { \
    EXPECT_FALSE(bl.is_empty()); \
  } \
}

template <class BINLISTTYPE>
struct BinListBasicTest {

  static const size_t maxws;

  static void basic_test() {

    BINLISTTYPE bl;

    CHECK_BL_CONTENT(bl, 0, 0);

    MetaWord arr[1000];

    size_t innocous_size = MAX2((size_t)1, maxws / 2);

    // Try to get a block from an empty list.
    size_t real_size = 4711;
    MetaWord* p = bl.remove_block(innocous_size, &real_size);
    EXPECT_EQ(p, (MetaWord*)nullptr);
    EXPECT_EQ((size_t)0, real_size);

    // Add a block...
    bl.add_block(arr, innocous_size);
    CHECK_BL_CONTENT(bl, 1, innocous_size);
    DEBUG_ONLY(bl.verify();)

    // And retrieve it.
    real_size = 4711;
    p = bl.remove_block(innocous_size, &real_size);
    EXPECT_EQ(p, arr);
    EXPECT_EQ((size_t)innocous_size, real_size);
    CHECK_BL_CONTENT(bl, 0, 0);
    DEBUG_ONLY(bl.verify();)

  }

  static void basic_test_2() {

    BINLISTTYPE bl;

    CHECK_BL_CONTENT(bl, 0, 0);

    MetaWord arr[1000];

    for (size_t s1 = 1; s1 <= maxws; s1++) {
      for (size_t s2 = 1; s2 <= maxws; s2++) {

        bl.add_block(arr, s1);
        CHECK_BL_CONTENT(bl, 1, s1);
        DEBUG_ONLY(bl.verify();)

        size_t real_size = 4711;
        MetaWord* p = bl.remove_block(s2, &real_size);
        if (s1 >= s2) {
          EXPECT_EQ(p, arr);
          EXPECT_EQ((size_t)s1, real_size);
          CHECK_BL_CONTENT(bl, 0, 0);
          DEBUG_ONLY(bl.verify();)
        } else {
          EXPECT_EQ(p, (MetaWord*)nullptr);
          EXPECT_EQ((size_t)0, real_size);
          CHECK_BL_CONTENT(bl, 1, s1);
          DEBUG_ONLY(bl.verify();)
          // drain bl
          p = bl.remove_block(1, &real_size);
          EXPECT_EQ(p, arr);
          EXPECT_EQ((size_t)s1, real_size);
          CHECK_BL_CONTENT(bl, 0, 0);
        }
      }
    }
  }

  static void random_test() {

    BINLISTTYPE bl[2];
    MemRangeCounter cnt[2];

#define CHECK_COUNTERS \
  ASSERT_EQ(cnt[0].count(), bl[0].count()); \
  ASSERT_EQ(cnt[1].count(), bl[1].count()); \
  ASSERT_EQ(cnt[0].total_size(), bl[0].total_size()); \
  ASSERT_EQ(cnt[1].total_size(), bl[1].total_size());

    FeederBuffer fb(1024);
    RandSizeGenerator rgen(1, maxws + 1);

    // feed all
    int which = 0;
    for (;;) {
      size_t s = rgen.get();
      MetaWord* p = fb.get(s);
      if (p != nullptr) {
        bl[which].add_block(p, s);
        cnt[which].add(s);
        which = which == 0 ? 1 : 0;
      } else {
        break;
      }
    }

    CHECK_COUNTERS;
    DEBUG_ONLY(bl[0].verify();)
    DEBUG_ONLY(bl[1].verify();)

    // play pingpong
    for (int iter = 0; iter < 1000; iter++) {
      size_t s = rgen.get();
      int taker = iter % 2;
      int giver = taker == 0 ? 1 : 0;

      size_t real_size = 4711;
      MetaWord* p = bl[giver].remove_block(s, &real_size);
      if (p != nullptr) {

        ASSERT_TRUE(fb.is_valid_range(p, real_size));
        ASSERT_GE(real_size, s);
        cnt[giver].sub(real_size);

        bl[taker].add_block(p, real_size);
        cnt[taker].add(real_size);

      } else {
        ASSERT_EQ(real_size, (size_t)nullptr);
      }

      CHECK_COUNTERS;

    }

    CHECK_COUNTERS;
    DEBUG_ONLY(bl[0].verify();)
    DEBUG_ONLY(bl[1].verify();)

    // drain both lists.
    for (int which = 0; which < 2; which++) {
      size_t last_size = 0;
      while (bl[which].is_empty() == false) {

        size_t real_size = 4711;
        MetaWord* p = bl[which].remove_block(1, &real_size);

        ASSERT_NE(p, (MetaWord*) nullptr);
        ASSERT_GE(real_size, (size_t)1);
        ASSERT_TRUE(fb.is_valid_range(p, real_size));

        // This must hold true since we always return the smallest fit.
        ASSERT_GE(real_size, last_size);
        if (real_size > last_size) {
          last_size = real_size;
        }

        cnt[which].sub(real_size);

        CHECK_COUNTERS;
      }
    }

  }
};

template <typename BINLISTTYPE> const size_t BinListBasicTest<BINLISTTYPE>::maxws = BINLISTTYPE::MaxWordSize;

TEST_VM(metaspace, BinList_basic_1)     { BinListBasicTest< BinListImpl<1> >::basic_test(); }
TEST_VM(metaspace, BinList_basic_8)     { BinListBasicTest< BinListImpl<8> >::basic_test(); }
TEST_VM(metaspace, BinList_basic_32)    { BinListBasicTest<BinList32>::basic_test(); }

TEST_VM(metaspace, BinList_basic_2_1)     { BinListBasicTest< BinListImpl<1> >::basic_test_2(); }
TEST_VM(metaspace, BinList_basic_2_8)     { BinListBasicTest< BinListImpl<8> >::basic_test_2(); }
TEST_VM(metaspace, BinList_basic_2_32)    { BinListBasicTest<BinList32>::basic_test_2(); }

TEST_VM(metaspace, BinList_basic_rand_1)     { BinListBasicTest< BinListImpl<1> >::random_test(); }
TEST_VM(metaspace, BinList_basic_rand_8)     { BinListBasicTest< BinListImpl<8> >::random_test(); }
TEST_VM(metaspace, BinList_basic_rand_32)    { BinListBasicTest<BinList32>::random_test(); }
