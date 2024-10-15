/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "unittest.hpp"

class ZAddressOffsetMaxSetter {
private:
  const size_t _old_max;
  const size_t _old_mask;

public:
  ZAddressOffsetMaxSetter()
    : _old_max(ZAddressOffsetMax),
      _old_mask(ZAddressOffsetMask) {
    ZAddressOffsetMax = size_t(16) * G * 1024;
    ZAddressOffsetMask = ZAddressOffsetMax - 1;
  }
  ~ZAddressOffsetMaxSetter() {
    ZAddressOffsetMax = _old_max;
    ZAddressOffsetMask = _old_mask;
  }
};

TEST(ZPhysicalMemoryTest, copy) {
  ZAddressOffsetMaxSetter setter;

  const ZPhysicalMemorySegment seg0(zoffset(0), 100, true);
  const ZPhysicalMemorySegment seg1(zoffset(200), 100, true);

  ZPhysicalMemory pmem0;
  pmem0.add_segment(seg0);
  EXPECT_EQ(pmem0.nsegments(), 1);
  EXPECT_EQ(pmem0.segment(0).size(), 100u);

  ZPhysicalMemory pmem1;
  pmem1.add_segment(seg0);
  pmem1.add_segment(seg1);
  EXPECT_EQ(pmem1.nsegments(), 2);
  EXPECT_EQ(pmem1.segment(0).size(), 100u);
  EXPECT_EQ(pmem1.segment(1).size(), 100u);

  ZPhysicalMemory pmem2(pmem0);
  EXPECT_EQ(pmem2.nsegments(), 1);
  EXPECT_EQ(pmem2.segment(0).size(), 100u);

  pmem2 = pmem1;
  EXPECT_EQ(pmem2.nsegments(), 2);
  EXPECT_EQ(pmem2.segment(0).size(), 100u);
  EXPECT_EQ(pmem2.segment(1).size(), 100u);
}

TEST(ZPhysicalMemoryTest, add) {
  ZAddressOffsetMaxSetter setter;

  const ZPhysicalMemorySegment seg0(zoffset(0), 1, true);
  const ZPhysicalMemorySegment seg1(zoffset(1), 1, true);
  const ZPhysicalMemorySegment seg2(zoffset(2), 1, true);
  const ZPhysicalMemorySegment seg3(zoffset(3), 1, true);
  const ZPhysicalMemorySegment seg4(zoffset(4), 1, true);
  const ZPhysicalMemorySegment seg5(zoffset(5), 1, true);
  const ZPhysicalMemorySegment seg6(zoffset(6), 1, true);

  ZPhysicalMemory pmem0;
  EXPECT_EQ(pmem0.nsegments(), 0);
  EXPECT_EQ(pmem0.is_null(), true);

  ZPhysicalMemory pmem1;
  pmem1.add_segment(seg0);
  pmem1.add_segment(seg1);
  pmem1.add_segment(seg2);
  pmem1.add_segment(seg3);
  pmem1.add_segment(seg4);
  pmem1.add_segment(seg5);
  pmem1.add_segment(seg6);
  EXPECT_EQ(pmem1.nsegments(), 1);
  EXPECT_EQ(pmem1.segment(0).size(), 7u);
  EXPECT_EQ(pmem1.is_null(), false);

  ZPhysicalMemory pmem2;
  pmem2.add_segment(seg0);
  pmem2.add_segment(seg1);
  pmem2.add_segment(seg2);
  pmem2.add_segment(seg4);
  pmem2.add_segment(seg5);
  pmem2.add_segment(seg6);
  EXPECT_EQ(pmem2.nsegments(), 2);
  EXPECT_EQ(pmem2.segment(0).size(), 3u);
  EXPECT_EQ(pmem2.segment(1).size(), 3u);
  EXPECT_EQ(pmem2.is_null(), false);

  ZPhysicalMemory pmem3;
  pmem3.add_segment(seg0);
  pmem3.add_segment(seg2);
  pmem3.add_segment(seg3);
  pmem3.add_segment(seg4);
  pmem3.add_segment(seg6);
  EXPECT_EQ(pmem3.nsegments(), 3);
  EXPECT_EQ(pmem3.segment(0).size(), 1u);
  EXPECT_EQ(pmem3.segment(1).size(), 3u);
  EXPECT_EQ(pmem3.segment(2).size(), 1u);
  EXPECT_EQ(pmem3.is_null(), false);

  ZPhysicalMemory pmem4;
  pmem4.add_segment(seg0);
  pmem4.add_segment(seg2);
  pmem4.add_segment(seg4);
  pmem4.add_segment(seg6);
  EXPECT_EQ(pmem4.nsegments(), 4);
  EXPECT_EQ(pmem4.segment(0).size(), 1u);
  EXPECT_EQ(pmem4.segment(1).size(), 1u);
  EXPECT_EQ(pmem4.segment(2).size(), 1u);
  EXPECT_EQ(pmem4.segment(3).size(), 1u);
  EXPECT_EQ(pmem4.is_null(), false);
}

TEST(ZPhysicalMemoryTest, remove) {
  ZAddressOffsetMaxSetter setter;

  ZPhysicalMemory pmem;

  pmem.add_segment(ZPhysicalMemorySegment(zoffset(10), 10, true));
  pmem.add_segment(ZPhysicalMemorySegment(zoffset(30), 10, true));
  pmem.add_segment(ZPhysicalMemorySegment(zoffset(50), 10, true));
  EXPECT_EQ(pmem.nsegments(), 3);
  EXPECT_EQ(pmem.size(), 30u);
  EXPECT_FALSE(pmem.is_null());

  pmem.remove_segments();
  EXPECT_EQ(pmem.nsegments(), 0);
  EXPECT_EQ(pmem.size(), 0u);
  EXPECT_TRUE(pmem.is_null());
}

TEST(ZPhysicalMemoryTest, split) {
  ZAddressOffsetMaxSetter setter;

  ZPhysicalMemory pmem;

  pmem.add_segment(ZPhysicalMemorySegment(zoffset(0), 10, true));
  pmem.add_segment(ZPhysicalMemorySegment(zoffset(10), 10, true));
  pmem.add_segment(ZPhysicalMemorySegment(zoffset(30), 10, true));
  EXPECT_EQ(pmem.nsegments(), 2);
  EXPECT_EQ(pmem.size(), 30u);

  ZPhysicalMemory pmem0 = pmem.split(1);
  EXPECT_EQ(pmem0.nsegments(), 1);
  EXPECT_EQ(pmem0.size(), 1u);
  EXPECT_EQ(pmem.nsegments(), 2);
  EXPECT_EQ(pmem.size(), 29u);

  ZPhysicalMemory pmem1 = pmem.split(25);
  EXPECT_EQ(pmem1.nsegments(), 2);
  EXPECT_EQ(pmem1.size(), 25u);
  EXPECT_EQ(pmem.nsegments(), 1);
  EXPECT_EQ(pmem.size(), 4u);

  ZPhysicalMemory pmem2 = pmem.split(4);
  EXPECT_EQ(pmem2.nsegments(), 1);
  EXPECT_EQ(pmem2.size(), 4u);
  EXPECT_EQ(pmem.nsegments(), 0);
  EXPECT_EQ(pmem.size(), 0u);
}

TEST(ZPhysicalMemoryTest, split_committed) {
  ZAddressOffsetMaxSetter setter;

  ZPhysicalMemory pmem0;
  pmem0.add_segment(ZPhysicalMemorySegment(zoffset(0), 10, true));
  pmem0.add_segment(ZPhysicalMemorySegment(zoffset(10), 10, false));
  pmem0.add_segment(ZPhysicalMemorySegment(zoffset(20), 10, true));
  pmem0.add_segment(ZPhysicalMemorySegment(zoffset(30), 10, false));
  EXPECT_EQ(pmem0.nsegments(), 4);
  EXPECT_EQ(pmem0.size(), 40u);

  ZPhysicalMemory pmem1 = pmem0.split_committed();
  EXPECT_EQ(pmem0.nsegments(), 2);
  EXPECT_EQ(pmem0.size(), 20u);
  EXPECT_EQ(pmem1.nsegments(), 2);
  EXPECT_EQ(pmem1.size(), 20u);
}

TEST(ZPhysicalMemoryTest, limits) {
  ZAddressOffsetMaxSetter setter;

  const size_t HalfZAddressOffsetMax = ZAddressOffsetMax >> 1;
  ZPhysicalMemory pmem0;
  pmem0.add_segment(ZPhysicalMemorySegment(zoffset(0), HalfZAddressOffsetMax, true));
  pmem0.add_segment(ZPhysicalMemorySegment(zoffset(HalfZAddressOffsetMax), HalfZAddressOffsetMax, false));
  EXPECT_EQ(pmem0.nsegments(), 2);
  EXPECT_EQ(pmem0.size(), ZAddressOffsetMax);

  ZPhysicalMemory pmem1 = pmem0.split_committed();
  EXPECT_EQ(pmem0.nsegments(), 1);
  EXPECT_EQ(pmem0.size(), HalfZAddressOffsetMax);
  EXPECT_EQ(pmem1.nsegments(), 1);
  EXPECT_EQ(pmem1.size(), HalfZAddressOffsetMax);
}
