/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "memory/allocation.hpp"
#include "nmt/memTag.hpp"
#include "nmt/nmtNativeCallStackStorage.hpp"
#include "nmt/regionsTree.inline.hpp"
#include "nmt/virtualMemoryTracker.hpp"
#include "nmt/vmatree.hpp"
#include "runtime/os.hpp"
#include "unittest.hpp"

class NMTRegionsTreeTest : public testing::Test {
 public:
  RegionsTree rt;
  NMTRegionsTreeTest() : rt(true) { }
};

TEST_VM_F(NMTRegionsTreeTest, ReserveCommitTwice) {
  NativeCallStack ncs;
  VMATree::RegionData rd = rt.make_region_data(ncs, mtTest);
  VMATree::RegionData rd2 = rt.make_region_data(ncs, mtGC);
  {
    VMATree::SummaryDiff diff;
    rt.reserve_mapping(0, 100, rd, diff);
    EXPECT_EQ(100, diff.tag[NMTUtil::tag_to_index(mtTest)].reserve);
  }
  {
    VMATree::SummaryDiff diff, not_used;
    rt.commit_region(nullptr, 50, ncs, not_used);
    rt.reserve_mapping(0, 100, rd, diff);
    EXPECT_EQ(0, diff.tag[NMTUtil::tag_to_index(mtTest)].reserve);
    EXPECT_EQ(-50, diff.tag[NMTUtil::tag_to_index(mtTest)].commit);
  }
  {
    VMATree::SummaryDiff diff;
    rt.reserve_mapping(0, 100, rd2, diff);
    EXPECT_EQ(-100, diff.tag[NMTUtil::tag_to_index(mtTest)].reserve);
    EXPECT_EQ(100, diff.tag[NMTUtil::tag_to_index(mtGC)].reserve);
  }

  {
    VMATree::SummaryDiff diff1, diff2;
    rt.commit_region(nullptr, 50, ncs, diff1);
    EXPECT_EQ(0, diff1.tag[NMTUtil::tag_to_index(mtGC)].reserve);
    EXPECT_EQ(50, diff1.tag[NMTUtil::tag_to_index(mtGC)].commit);
    rt.commit_region(nullptr, 50, ncs, diff2);
    EXPECT_EQ(0, diff2.tag[NMTUtil::tag_to_index(mtTest)].reserve);
    EXPECT_EQ(0, diff2.tag[NMTUtil::tag_to_index(mtTest)].commit);
  }
}

TEST_VM_F(NMTRegionsTreeTest, CommitUncommitRegion) {
  NativeCallStack ncs;
  VMATree::RegionData rd = rt.make_region_data(ncs, mtTest);
  VMATree::SummaryDiff not_used;
  rt.reserve_mapping(0, 100, rd, not_used);
  {
    VMATree::SummaryDiff diff;
    rt.commit_region(nullptr, 50, ncs, diff);
    EXPECT_EQ(0, diff.tag[NMTUtil::tag_to_index(mtTest)].reserve);
    EXPECT_EQ(50, diff.tag[NMTUtil::tag_to_index(mtTest)].commit);
  }
  {
    VMATree::SummaryDiff diff;
    rt.commit_region((address)60, 10, ncs, diff);
    EXPECT_EQ(0, diff.tag[NMTUtil::tag_to_index(mtTest)].reserve);
    EXPECT_EQ(10, diff.tag[NMTUtil::tag_to_index(mtTest)].commit);
  }
  {
    VMATree::SummaryDiff diff;
    rt.uncommit_region(nullptr, 50, diff);
    EXPECT_EQ(0, diff.tag[NMTUtil::tag_to_index(mtTest)].reserve);
    EXPECT_EQ(-50, diff.tag[NMTUtil::tag_to_index(mtTest)].commit);
  }
}

TEST_VM_F(NMTRegionsTreeTest, FindReservedRegion) {
  NativeCallStack ncs;
  VMATree::RegionData rd = rt.make_region_data(ncs, mtTest);
  VMATree::SummaryDiff not_used;
  rt.reserve_mapping(1000, 50, rd, not_used);
  rt.reserve_mapping(1200, 50, rd, not_used);
  rt.reserve_mapping(1300, 50, rd, not_used);
  rt.reserve_mapping(1400, 50, rd, not_used);
  ReservedMemoryRegion rmr;
  rmr = rt.find_reserved_region((address)1205);
  EXPECT_EQ(rmr.base(), (address)1200);
  rmr = rt.find_reserved_region((address)1305);
  EXPECT_EQ(rmr.base(), (address)1300);
  rmr = rt.find_reserved_region((address)1405);
  EXPECT_EQ(rmr.base(), (address)1400);
  rmr = rt.find_reserved_region((address)1005);
  EXPECT_EQ(rmr.base(), (address)1000);
}

TEST_VM_F(NMTRegionsTreeTest, VisitReservedRegions) {
  NativeCallStack ncs;
  VMATree::RegionData rd = rt.make_region_data(ncs, mtTest);
  VMATree::SummaryDiff not_used;
  rt.reserve_mapping(1000, 50, rd, not_used);
  rt.reserve_mapping(1200, 50, rd, not_used);
  rt.reserve_mapping(1300, 50, rd, not_used);
  rt.reserve_mapping(1400, 50, rd, not_used);

  rt.visit_reserved_regions([&](const ReservedMemoryRegion& rgn) {
    EXPECT_EQ(((size_t)rgn.base()) % 100, 0UL);
    EXPECT_EQ(rgn.size(), 50UL);
    return true;
  });
}

TEST_VM_F(NMTRegionsTreeTest, VisitCommittedRegions) {
  NativeCallStack ncs;
  VMATree::RegionData rd = rt.make_region_data(ncs, mtTest);
  VMATree::SummaryDiff not_used;
  rt.reserve_mapping(1000, 50, rd, not_used);
  rt.reserve_mapping(1200, 50, rd, not_used);
  rt.reserve_mapping(1300, 50, rd, not_used);
  rt.reserve_mapping(1400, 50, rd, not_used);

  rt.commit_region((address)1010, 5UL, ncs, not_used);
  rt.commit_region((address)1020, 5UL, ncs, not_used);
  rt.commit_region((address)1030, 5UL, ncs, not_used);
  rt.commit_region((address)1040, 5UL, ncs, not_used);
  ReservedMemoryRegion rmr((address)1000, 50);
  size_t count = 0;
  rt.visit_committed_regions(rmr, [&](CommittedMemoryRegion& crgn) {
    count++;
    EXPECT_EQ((((size_t)crgn.base()) % 100) / 10, count);
    EXPECT_EQ(crgn.size(), 5UL);
    return true;
  });
  EXPECT_EQ(count, 4UL);
}
