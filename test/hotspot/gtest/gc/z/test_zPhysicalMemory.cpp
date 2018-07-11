/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zPhysicalMemory.inline.hpp"
#include "utilities/debug.hpp"
#include "unittest.hpp"

#if defined(AMD64)

TEST(ZPhysicalMemorySegmentTest, split) {
  const size_t SegmentSize = 2 * M;

  ZPhysicalMemorySegment seg(0, 10 * SegmentSize);

  ZPhysicalMemorySegment seg_split0 = seg.split(0 * SegmentSize);
  EXPECT_EQ(seg_split0.size(),  0 * SegmentSize);
  EXPECT_EQ(       seg.size(), 10 * SegmentSize);

  ZPhysicalMemorySegment seg_split1 = seg.split(5 * SegmentSize);
  EXPECT_EQ(seg_split1.size(),  5 * SegmentSize);
  EXPECT_EQ(       seg.size(),  5 * SegmentSize);

  ZPhysicalMemorySegment seg_split2 = seg.split(5 * SegmentSize);
  EXPECT_EQ(seg_split2.size(),  5 * SegmentSize);
  EXPECT_EQ(       seg.size(),  0 * SegmentSize);

  ZPhysicalMemorySegment seg_split3 = seg.split(0 * SegmentSize);
  EXPECT_EQ(seg_split3.size(),  0 * SegmentSize);
  EXPECT_EQ(       seg.size(),  0 * SegmentSize);
}

TEST(ZPhysicalMemoryTest, split) {
  const size_t SegmentSize = 2 * M;

  ZPhysicalMemoryManager pmem_manager(10 * SegmentSize, SegmentSize);

  pmem_manager.try_ensure_unused_capacity(10 * SegmentSize);
  EXPECT_EQ(pmem_manager.unused_capacity(), 10 * SegmentSize);

  ZPhysicalMemory pmem = pmem_manager.alloc(8 * SegmentSize);
  EXPECT_EQ(pmem.nsegments(), 1u) << "wrong number of segments";

  ZPhysicalMemory split0_pmem = pmem.split(SegmentSize);
  EXPECT_EQ(split0_pmem.nsegments(), 1u);
  EXPECT_EQ(       pmem.nsegments(), 1u);
  EXPECT_EQ(split0_pmem.size(), 1 * SegmentSize);
  EXPECT_EQ(       pmem.size(), 7 * SegmentSize);

  ZPhysicalMemory split1_pmem = pmem.split(2 * SegmentSize);
  EXPECT_EQ(split1_pmem.nsegments(), 1u);
  EXPECT_EQ(       pmem.nsegments(), 1u);
  EXPECT_EQ(split1_pmem.size(), 2 * SegmentSize);
  EXPECT_EQ(       pmem.size(), 5 * SegmentSize);

  ZPhysicalMemory split2_pmem = pmem.split(5 * SegmentSize);
  EXPECT_EQ(split2_pmem.nsegments(), 1u);
  EXPECT_EQ(       pmem.nsegments(), 1u);
  EXPECT_EQ(split2_pmem.size(), 5 * SegmentSize);
  EXPECT_EQ(       pmem.size(), 0 * SegmentSize);
}

#endif
