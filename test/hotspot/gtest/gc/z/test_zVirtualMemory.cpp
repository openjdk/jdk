/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/z/zGlobals.hpp"
#include "gc/z/zVirtualMemory.inline.hpp"
#include "zunittest.hpp"

TEST(ZVirtualMemory, is_null) {
  ZAddressOffsetMaxSetter setter(size_t(16) * G * 1024);

  ZVirtualMemory mem;
  EXPECT_TRUE(mem.is_null());
}

TEST(ZVirtualMemory, accessors) {
  ZAddressOffsetMaxSetter setter(size_t(16) * G * 1024);

  {
    ZVirtualMemory mem(zoffset(0), ZGranuleSize);

    EXPECT_EQ(mem.start(), zoffset(0));
    EXPECT_EQ(mem.end(), zoffset_end(ZGranuleSize));
    EXPECT_EQ(mem.size(), ZGranuleSize);
    EXPECT_EQ(mem.granule_count(), 1);
  }

  {
    ZVirtualMemory mem(zoffset(ZGranuleSize), ZGranuleSize);

    EXPECT_EQ(mem.start(), zoffset(ZGranuleSize));
    EXPECT_EQ(mem.end(), zoffset_end(ZGranuleSize + ZGranuleSize));
    EXPECT_EQ(mem.size(), ZGranuleSize);
    EXPECT_EQ(mem.granule_count(), 1);
  }

  {
    // Max area - check end boundary
    ZVirtualMemory mem(zoffset(0), ZAddressOffsetMax);

    EXPECT_EQ(mem.start(), zoffset(0));
    EXPECT_EQ(mem.end(), zoffset_end(ZAddressOffsetMax));
    EXPECT_EQ(mem.size(), ZAddressOffsetMax);
    EXPECT_EQ(mem.granule_count(), (int)(ZAddressOffsetMax >> ZGranuleSizeShift));
  }
}

TEST(ZVirtualMemory, resize) {
  ZAddressOffsetMaxSetter setter(size_t(16) * G * 1024);

  ZVirtualMemory mem(zoffset(ZGranuleSize * 2), ZGranuleSize * 2) ;

  mem.shrink_from_front(ZGranuleSize);
  EXPECT_EQ(mem.start(),   zoffset(ZGranuleSize * 3));
  EXPECT_EQ(mem.end(), zoffset_end(ZGranuleSize * 4));
  EXPECT_EQ(mem.size(),            ZGranuleSize * 1);
  mem.grow_from_front(ZGranuleSize);

  mem.shrink_from_back(ZGranuleSize);
  EXPECT_EQ(mem.start(),   zoffset(ZGranuleSize * 2));
  EXPECT_EQ(mem.end(), zoffset_end(ZGranuleSize * 3));
  EXPECT_EQ(mem.size(),            ZGranuleSize * 1);
  mem.grow_from_back(ZGranuleSize);

  mem.grow_from_front(ZGranuleSize);
  EXPECT_EQ(mem.start(),   zoffset(ZGranuleSize * 1));
  EXPECT_EQ(mem.end(), zoffset_end(ZGranuleSize * 4));
  EXPECT_EQ(mem.size(),            ZGranuleSize * 3);
  mem.shrink_from_front(ZGranuleSize);

  mem.grow_from_back(ZGranuleSize);
  EXPECT_EQ(mem.start(),   zoffset(ZGranuleSize * 2));
  EXPECT_EQ(mem.end(), zoffset_end(ZGranuleSize * 5));
  EXPECT_EQ(mem.size(),            ZGranuleSize * 3);
  mem.shrink_from_back(ZGranuleSize);
}

TEST(ZVirtualMemory, shrink_from_front) {
  ZAddressOffsetMaxSetter setter(size_t(16) * G * 1024);

  ZVirtualMemory mem(zoffset(0), ZGranuleSize * 10);

  ZVirtualMemory mem0 = mem.shrink_from_front(0);
  EXPECT_EQ(mem0.size(), 0u);
  EXPECT_EQ(mem.size(), ZGranuleSize * 10);

  ZVirtualMemory mem1 = mem.shrink_from_front(ZGranuleSize * 5);
  EXPECT_EQ(mem1.size(), ZGranuleSize * 5);
  EXPECT_EQ(mem.size(), ZGranuleSize * 5);

  ZVirtualMemory mem2 = mem.shrink_from_front(ZGranuleSize * 5);
  EXPECT_EQ(mem2.size(), ZGranuleSize * 5);
  EXPECT_EQ(mem.size(), 0u);

  ZVirtualMemory mem3 = mem.shrink_from_front(0);
  EXPECT_EQ(mem3.size(), 0u);
}

TEST(ZVirtualMemory, shrink_from_back) {
  ZAddressOffsetMaxSetter setter(size_t(16) * G * 1024);

  ZVirtualMemory mem(zoffset(0), ZGranuleSize * 10);

  ZVirtualMemory mem1 = mem.shrink_from_back(ZGranuleSize * 5);
  EXPECT_EQ(mem1.size(), ZGranuleSize * 5);
  EXPECT_EQ(mem.size(), ZGranuleSize * 5);

  ZVirtualMemory mem2 = mem.shrink_from_back(ZGranuleSize * 5);
  EXPECT_EQ(mem2.size(), ZGranuleSize * 5);
  EXPECT_EQ(mem.size(), 0u);
}

TEST(ZVirtualMemory, adjacent_to) {
  ZAddressOffsetMaxSetter setter(size_t(16) * G * 1024);

  ZVirtualMemory mem0(zoffset(0), ZGranuleSize);
  ZVirtualMemory mem1(zoffset(ZGranuleSize), ZGranuleSize);
  ZVirtualMemory mem2(zoffset(ZGranuleSize * 2), ZGranuleSize);

  EXPECT_TRUE(mem0.adjacent_to(mem1));
  EXPECT_TRUE(mem1.adjacent_to(mem0));
  EXPECT_TRUE(mem1.adjacent_to(mem2));
  EXPECT_TRUE(mem2.adjacent_to(mem1));

  EXPECT_FALSE(mem0.adjacent_to(mem2));
  EXPECT_FALSE(mem2.adjacent_to(mem0));
}
