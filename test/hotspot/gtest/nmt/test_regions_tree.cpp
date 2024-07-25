/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/allocation.hpp"
#include "nmt/memflags.hpp"
#include "nmt/nmtNativeCallStackStorage.hpp"
#include "nmt/regionsTree.hpp"
#include "nmt/vmatree.hpp"
#include "runtime/os.hpp"
#include "unittest.hpp"

using Tree = VMATree;
using Node = Tree::TreapNode;
using NCS = NativeCallStackStorage;
class RegionsTreeTest : public testing::Test {
 public:
  RegionsTree rt;
  RegionsTreeTest() : rt(true) { }

};

TEST_VM_F(RegionsTreeTest, find_reserved_region) {
  NativeCallStack ncs;
  rt.reserve_mapping(1000, 50, rt.make_region_data(ncs, mtTest));
  rt.reserve_mapping(1200, 50, rt.make_region_data(ncs, mtTest));
  rt.reserve_mapping(1300, 50, rt.make_region_data(ncs, mtTest));
  rt.reserve_mapping(1400, 50, rt.make_region_data(ncs, mtTest));
  ReservedMemoryRegion rmr;
  rt.find_reserved_region((address)1205, &rmr);
  EXPECT_EQ(rmr.base(), (address)1200);
  rt.find_reserved_region((address)1305, &rmr);
  EXPECT_EQ(rmr.base(), (address)1300);
  rt.find_reserved_region((address)1405, &rmr);
  EXPECT_EQ(rmr.base(), (address)1400);
  rt.find_reserved_region((address)1005, &rmr);
  EXPECT_EQ(rmr.base(), (address)1000);
}

TEST_VM_F(RegionsTreeTest, visit_reserved_regions) {
  NativeCallStack ncs;
  ResourceMark rm;
  rt.reserve_mapping(1000, 50, rt.make_region_data(ncs, mtTest));
  rt.reserve_mapping(1200, 50, rt.make_region_data(ncs, mtTest));
  rt.reserve_mapping(1300, 50, rt.make_region_data(ncs, mtTest));
  rt.reserve_mapping(1400, 50, rt.make_region_data(ncs, mtTest));
  ReservedMemoryRegion rmr;
  rt.visit_reserved_regions(&rmr, [&](ReservedMemoryRegion* rgn) {
    EXPECT_EQ((size_t)rgn->base() % 100, 0UL);
    EXPECT_EQ(rgn->size(), 50UL);
    return true;
  });
}

TEST_VM_F(RegionsTreeTest, visit_committed_regions) {
  NativeCallStack ncs;
  ResourceMark rm;
  rt.reserve_mapping(1000, 50, rt.make_region_data(ncs, mtTest));
  rt.reserve_mapping(1200, 50, rt.make_region_data(ncs, mtTest));
  rt.reserve_mapping(1300, 50, rt.make_region_data(ncs, mtTest));
  rt.reserve_mapping(1400, 50, rt.make_region_data(ncs, mtTest));

  rt.commit_region((address)1010, 5UL, ncs);
  rt.commit_region((address)1020, 5UL, ncs);
  rt.commit_region((address)1030, 5UL, ncs);
  rt.commit_region((address)1040, 5UL, ncs);
  ReservedMemoryRegion rmr((address)1000, 50);
  CommittedMemoryRegion cmr;
  size_t count = 0;
  rt.visit_committed_regions(&rmr, &cmr, [&](CommittedMemoryRegion* crgn) {
    count++;
    EXPECT_EQ((((size_t)crgn->base()) % 100) / 10, count);
    EXPECT_EQ(crgn->size(), 5UL);
    return true;
  });
  EXPECT_EQ(count, 4UL);
}