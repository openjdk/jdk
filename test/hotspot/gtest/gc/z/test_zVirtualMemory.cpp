/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
