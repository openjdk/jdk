/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL.  Use is subject to license terms.
 */

#include "precompiled.hpp"
#include "gc/z/zVirtualMemory.inline.hpp"
#include "utilities/debug.hpp"
#include "unittest.hpp"

TEST(ZVirtualMemory, split) {
  const size_t PageSize = 2 * M;

  ZVirtualMemory mem(0, 10 * PageSize);

  ZVirtualMemory mem_split0 = mem.split(0 * PageSize);
  EXPECT_EQ(mem_split0.size(),  0 * PageSize);
  EXPECT_EQ(       mem.size(), 10 * PageSize);

  ZVirtualMemory mem_split1 = mem.split(5u * PageSize);
  EXPECT_EQ(mem_split1.size(),  5 * PageSize);
  EXPECT_EQ(       mem.size(),  5 * PageSize);

  ZVirtualMemory mem_split2 = mem.split(5u * PageSize);
  EXPECT_EQ(mem_split2.size(),  5 * PageSize);
  EXPECT_EQ(       mem.size(),  0 * PageSize);

  ZVirtualMemory mem_split3 = mem.split(0 * PageSize);
  EXPECT_EQ(mem_split3.size(),  0 * PageSize);
}
