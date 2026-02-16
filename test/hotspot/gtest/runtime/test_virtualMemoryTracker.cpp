/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

// Tests here test the VM-global NMT facility.
//  The tests must *not* modify global state! E.g. switch NMT on or off. Instead, they
//  should work passively with whatever setting the gtestlauncher had been started with
//  - if NMT is enabled, test NMT, otherwise do whatever minimal tests make sense if NMT
//  is off.
//
// The gtestLauncher then are called with various levels of -XX:NativeMemoryTracking during
//  jtreg-controlled gtests (see test/hotspot/jtreg/gtest/NMTGtests.java)

#include "memory/memoryReserver.hpp"
#include "nmt/memTracker.hpp"
#include "nmt/regionsTree.inline.hpp"
#include "nmt/virtualMemoryTracker.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include "unittest.hpp"

#include <stdio.h>

// #define LOG(...) printf(__VA_ARGS__); printf("\n"); fflush(stdout);
#define LOG(...)

namespace {
  struct R {
    address _addr;
    size_t  _size;
  };
}

#define check(vmt, rmr, regions) check_inner((vmt), (rmr), (regions), ARRAY_SIZE(regions), __FILE__, __LINE__)

#define check_empty(vmt, rmr)                              \
  do {                                                \
    check_inner((vmt), (rmr), nullptr, 0, __FILE__, __LINE__);  \
  } while (false)

static void diagnostic_print(VirtualMemoryTracker& vmt, const ReservedMemoryRegion& rmr) {
  LOG("In reserved region " PTR_FORMAT ", size %X:", p2i(rmr.base()), rmr.size());
  vmt.tree()->visit_committed_regions(rmr, [&](CommittedMemoryRegion& region) {
    LOG("   committed region: " PTR_FORMAT ", size %X", p2i(region.base()), region.size());
    return true;
  });
}

static void check_inner(VirtualMemoryTracker& vmt, const ReservedMemoryRegion& rmr, R* regions, size_t regions_size, const char* file, int line) {
  size_t i = 0;
  size_t size = 0;

  // Helpful log
  diagnostic_print(vmt, rmr);

#define WHERE " from " << file << ":" << line

  vmt.tree()->visit_committed_regions(rmr, [&](CommittedMemoryRegion& region) {
    EXPECT_LT(i, regions_size) << WHERE;
    EXPECT_EQ(region.base(), regions[i]._addr) << WHERE;
    EXPECT_EQ(region.size(), regions[i]._size) << WHERE;
    size += region.size();
    i++;
    return true;
  });

  EXPECT_EQ(i, regions_size) << WHERE;
  EXPECT_EQ(size, vmt.committed_size(&rmr)) << WHERE;
}

class VirtualMemoryTrackerTest {
public:
  static void test_add_committed_region_adjacent() {
    VirtualMemoryTracker vmt(true);
    RegionsTree* rtree = vmt.tree();
    size_t size  = 0x01000000;
    const address addr = (address)0x0000A000;
    VMATree::SummaryDiff diff;

    vmt.add_reserved_region(addr, size, CALLER_PC, mtTest);

    address frame1 = (address)0x1234;
    address frame2 = (address)0x1235;

    NativeCallStack stack(&frame1, 1);
    NativeCallStack stack2(&frame2, 1);

    // Fetch the added RMR for the space
    ReservedMemoryRegion rmr = rtree->find_reserved_region(addr);

    ASSERT_EQ(rmr.size(), size);
    ASSERT_EQ(rmr.base(), addr);

    // Commit Size Granularity
    const size_t cs = 0x1000;

    // Commit adjacent regions with same stack

    { // Commit one region
      rtree->commit_region(addr + cs, cs, stack, diff);
      R r[] = { {addr + cs, cs} };
      check(vmt, rmr, r);
    }

    { // Commit adjacent - lower address
      rtree->commit_region(addr, cs, stack, diff);
      R r[] = { {addr, 2 * cs} };
      check(vmt, rmr, r);
    }

    { // Commit adjacent - higher address
      rtree->commit_region(addr + 2 * cs, cs, stack, diff);
      R r[] = { {addr, 3 * cs} };
      check(vmt,rmr, r);
    }

    // Cleanup
    rtree->uncommit_region(addr, 3 * cs, diff);
    ASSERT_EQ(vmt.committed_size(&rmr), 0u);


    // Commit adjacent regions with different stacks

    { // Commit one region
      rtree->commit_region(addr + cs, cs, stack, diff);
      R r[] = { {addr + cs, cs} };
      check(vmt, rmr, r);
    }

    { // Commit adjacent - lower address
      rtree->commit_region(addr, cs, stack2, diff);
      R r[] = { {addr,      cs},
                {addr + cs, cs} };
      check(vmt, rmr, r);
    }

    { // Commit adjacent - higher address
      rtree->commit_region(addr + 2 * cs, cs, stack2, diff);
      R r[] = { {addr,          cs},
                {addr +     cs, cs},
                {addr + 2 * cs, cs} };
      check(vmt, rmr, r);
    }

    // Cleanup
    rtree->uncommit_region(addr, 3 * cs, diff);
    ASSERT_EQ(vmt.committed_size(&rmr), 0u);
  }

  static void test_add_committed_region_adjacent_overlapping() {
    VirtualMemoryTracker vmt(true);
    VMATree::SummaryDiff diff;
    RegionsTree* rtree = vmt.tree();
    size_t size  = 0x01000000;
    const address addr = (address)0x0000A000;

    vmt.add_reserved_region(addr, size, CALLER_PC, mtTest);
    address frame1 = (address)0x1234;
    address frame2 = (address)0x1235;

    NativeCallStack stack(&frame1, 1);
    NativeCallStack stack2(&frame2, 1);

    // Fetch the added RMR for the space
    ReservedMemoryRegion rmr = rtree->find_reserved_region(addr);

    ASSERT_EQ(rmr.size(), size);
    ASSERT_EQ(rmr.base(), addr);

    // Commit Size Granularity
    const size_t cs = 0x1000;

    // Commit adjacent and overlapping regions with same stack

    { // Commit two non-adjacent regions
      rtree->commit_region(addr, 2 * cs, stack, diff);
      rtree->commit_region(addr + 3 * cs, 2 * cs, stack, diff);
      R r[] = { {addr,          2 * cs},
                {addr + 3 * cs, 2 * cs} };
      check(vmt, rmr, r);
    }

    { // Commit adjacent and overlapping
      rtree->commit_region(addr + 2 * cs, 2 * cs, stack, diff);
      R r[] = { {addr, 5 * cs} };
      check(vmt, rmr, r);
    }

    // revert to two non-adjacent regions
    rtree->uncommit_region(addr + 2 * cs, cs, diff);
    ASSERT_EQ(vmt.committed_size(&rmr), 4 * cs);

    { // Commit overlapping and adjacent
      rtree->commit_region(addr + cs, 2 * cs, stack, diff);
      R r[] = { {addr, 5 * cs} };
      check(vmt, rmr, r);
    }

    // Cleanup
    rtree->uncommit_region(addr, 5 * cs, diff);
    ASSERT_EQ(vmt.committed_size(&rmr), 0u);


    // Commit adjacent and overlapping regions with different stacks

    { // Commit two non-adjacent regions
      rtree->commit_region(addr, 2 * cs, stack, diff);
      rtree->commit_region(addr + 3 * cs, 2 * cs, stack, diff);
      R r[] = { {addr,          2 * cs},
                {addr + 3 * cs, 2 * cs} };
      check(vmt, rmr, r);
    }

    { // Commit adjacent and overlapping
      rtree->commit_region(addr + 2 * cs, 2 * cs, stack2, diff);
      R r[] = { {addr,          2 * cs},
                {addr + 2 * cs, 2 * cs},
                {addr + 4 * cs,     cs} };
      check(vmt, rmr, r);
    }

    // revert to two non-adjacent regions
    rtree->commit_region(addr, 5 * cs, stack, diff);
    rtree->uncommit_region(addr + 2 * cs, cs, diff);
    ASSERT_EQ(vmt.committed_size(&rmr), 4 * cs);

    { // Commit overlapping and adjacent
      rtree->commit_region(addr + cs, 2 * cs, stack2, diff);
      R r[] = { {addr,              cs},
                {addr +     cs, 2 * cs},
                {addr + 3 * cs, 2 * cs} };
      check(vmt, rmr, r);
    }

    rtree->tree().remove_all();
  }

  static void test_add_committed_region_overlapping() {
    VirtualMemoryTracker vmt(true);
    VMATree::SummaryDiff diff;
    RegionsTree* rtree = vmt.tree();
    size_t size  = 0x01000000;
    const address addr = (address)0x0000A000;

    vmt.add_reserved_region(addr, size, CALLER_PC, mtTest);

    address frame1 = (address)0x1234;
    address frame2 = (address)0x1235;

    NativeCallStack stack(&frame1, 1);
    NativeCallStack stack2(&frame2, 1);

    // Fetch the added RMR for the space
    ReservedMemoryRegion rmr = rtree->find_reserved_region(addr);


    ASSERT_EQ(rmr.size(), size);
    ASSERT_EQ(rmr.base(), addr);

    // Commit Size Granularity
    const size_t cs = 0x1000;

    // With same stack

    { // Commit one region
      rtree->commit_region(addr, cs, stack, diff);
      R r[] = { {addr, cs} };
      check(vmt, rmr, r);
    }

    { // Commit the same region
      rtree->commit_region(addr, cs, stack, diff);
      R r[] = { {addr, cs} };
      check(vmt, rmr, r);
    }

    { // Commit a succeeding region
      rtree->commit_region(addr + cs, cs, stack, diff);
      R r[] = { {addr, 2 * cs} };
      check(vmt, rmr, r);
    }

    { // Commit  over two regions
      rtree->commit_region(addr, 2 * cs, stack, diff);
      R r[] = { {addr, 2 * cs} };
      check(vmt, rmr, r);
    }

    {// Commit first part of a region
      rtree->commit_region(addr, cs, stack, diff);
      R r[] = { {addr, 2 * cs} };
      check(vmt, rmr, r);
    }

    { // Commit second part of a region
      rtree->commit_region(addr + cs, cs, stack, diff);
      R r[] = { {addr, 2 * cs} };
      check(vmt, rmr, r);
    }

    { // Commit a third part
      rtree->commit_region(addr + 2 * cs, cs, stack, diff);
      R r[] = { {addr, 3 * cs} };
      check(vmt, rmr, r);
    }

    { // Commit in the middle of a region
      rtree->commit_region(addr + 1 * cs, cs, stack, diff);
      R r[] = { {addr, 3 * cs} };
      check(vmt, rmr, r);
    }

    // Cleanup
    rtree->uncommit_region(addr, 3 * cs, diff);
    ASSERT_EQ(vmt.committed_size(&rmr), 0u);

    // With preceding region

    rtree->commit_region(addr,              cs, stack, diff);
    rtree->commit_region(addr + 2 * cs, 3 * cs, stack, diff);

    rtree->commit_region(addr + 2 * cs,     cs, stack, diff);
    {
      R r[] = { {addr,              cs},
                {addr + 2 * cs, 3 * cs} };
      check(vmt, rmr, r);
    }

    rtree->commit_region(addr + 3 * cs,     cs, stack, diff);
    {
      R r[] = { {addr,              cs},
                {addr + 2 * cs, 3 * cs} };
      check(vmt, rmr, r);
    }

    rtree->commit_region(addr + 4 * cs,     cs, stack, diff);
    {
      R r[] = { {addr,              cs},
                {addr + 2 * cs, 3 * cs} };
      check(vmt, rmr, r);
    }

    // Cleanup
    rtree->uncommit_region(addr, 5 * cs, diff);
    ASSERT_EQ(vmt.committed_size(&rmr), 0u);

    // With different stacks

    { // Commit one region
      rtree->commit_region(addr, cs, stack, diff);
      R r[] = { {addr, cs} };
      check(vmt, rmr, r);
    }

    { // Commit the same region
      rtree->commit_region(addr, cs, stack2, diff);
      R r[] = { {addr, cs} };
      check(vmt, rmr, r);
    }

    { // Commit a succeeding region
      rtree->commit_region(addr + cs, cs, stack, diff);
      R r[] = { {addr,      cs},
                {addr + cs, cs} };
      check(vmt, rmr, r);
    }

    { // Commit  over two regions
      rtree->commit_region(addr, 2 * cs, stack, diff);
      R r[] = { {addr, 2 * cs} };
      check(vmt, rmr, r);
    }

    {// Commit first part of a region
      rtree->commit_region(addr, cs, stack2, diff);
      R r[] = { {addr,      cs},
                {addr + cs, cs} };
      check(vmt, rmr, r);
    }

    { // Commit second part of a region
      rtree->commit_region(addr + cs, cs, stack2, diff);
      R r[] = { {addr, 2 * cs} };
      check(vmt, rmr, r);
    }

    { // Commit a third part
      rtree->commit_region(addr + 2 * cs, cs, stack2, diff);
      R r[] = { {addr, 3 * cs} };
      check(vmt, rmr, r);
    }

    { // Commit in the middle of a region
      rtree->commit_region(addr + 1 * cs, cs, stack, diff);
      R r[] = { {addr,          cs},
                {addr +     cs, cs},
                {addr + 2 * cs, cs} };
      check(vmt, rmr, r);
    }

    rtree->tree().remove_all();
  }

  static void test_add_committed_region() {
    test_add_committed_region_adjacent();
    test_add_committed_region_adjacent_overlapping();
    test_add_committed_region_overlapping();
  }

  template <size_t S>
  static void fix(R r[S]) {

  }

  static void test_remove_uncommitted_region() {
    VirtualMemoryTracker vmt(true);
    VMATree::SummaryDiff diff;
    RegionsTree* rtree = vmt.tree();
    size_t size  = 0x01000000;
    const address addr = (address)0x0000A000;

    vmt.add_reserved_region(addr, size, CALLER_PC, mtTest);
    address frame1 = (address)0x1234;
    address frame2 = (address)0x1235;

    NativeCallStack stack(&frame1, 1);
    NativeCallStack stack2(&frame2, 1);

    // Fetch the added RMR for the space
    ReservedMemoryRegion rmr = rtree->find_reserved_region(addr);

    ASSERT_EQ(rmr.size(), size);
    ASSERT_EQ(rmr.base(), addr);

    // Commit Size Granularity
    const size_t cs = 0x1000;

    { // Commit regions
      rtree->commit_region(addr, 3 * cs, stack, diff);
      R r[] = { {addr, 3 * cs} };
      check(vmt, rmr, r);

      // Remove only existing
      rtree->uncommit_region(addr, 3 * cs, diff);
      check_empty(vmt, rmr);
    }

    {
      rtree->commit_region(addr + 0 * cs, cs, stack, diff);
      rtree->commit_region(addr + 2 * cs, cs, stack, diff);
      rtree->commit_region(addr + 4 * cs, cs, stack, diff);

      { // Remove first
        rtree->uncommit_region(addr, cs, diff);
        R r[] = { {addr + 2 * cs, cs},
                  {addr + 4 * cs, cs} };
        check(vmt, rmr, r);
      }

      // add back
      rtree->commit_region(addr,          cs, stack, diff);

      { // Remove middle
        rtree->uncommit_region(addr + 2 * cs, cs, diff);
        R r[] = { {addr + 0 * cs, cs},
                  {addr + 4 * cs, cs} };
        check(vmt, rmr, r);
      }

      // add back
      rtree->commit_region(addr + 2 * cs, cs, stack, diff);

      { // Remove end
        rtree->uncommit_region(addr + 4 * cs, cs, diff);
        R r[] = { {addr + 0 * cs, cs},
                  {addr + 2 * cs, cs} };
        check(vmt, rmr, r);
      }

      rtree->uncommit_region(addr, 5 * cs, diff);
      check_empty(vmt, rmr);
    }

    { // Remove larger region
      rtree->commit_region(addr + 1 * cs, cs, stack, diff);
      rtree->uncommit_region(addr, 3 * cs, diff);
      check_empty(vmt, rmr);
    }

    { // Remove smaller region - in the middle
      rtree->commit_region(addr, 3 * cs, stack, diff);
      rtree->uncommit_region(addr + 1 * cs, cs, diff);
      R r[] = { { addr + 0 * cs, cs},
                { addr + 2 * cs, cs} };
      check(vmt, rmr, r);

      rtree->uncommit_region(addr, 3 * cs, diff);
      check_empty(vmt, rmr);
    }

    { // Remove smaller region - at the beginning
      rtree->commit_region(addr, 3 * cs, stack, diff);
      rtree->uncommit_region(addr + 0 * cs, cs, diff);
      R r[] = { { addr + 1 * cs, 2 * cs} };
      check(vmt, rmr, r);

      rtree->uncommit_region(addr, 3 * cs, diff);
      check_empty(vmt, rmr);
    }

    { // Remove smaller region - at the end
      rtree->commit_region(addr, 3 * cs, stack, diff);
      rtree->uncommit_region(addr + 2 * cs, cs, diff);
      R r[] = { { addr, 2 * cs} };
      check(vmt, rmr, r);

      rtree->uncommit_region(addr, 3 * cs, diff);
      check_empty(vmt, rmr);
    }

    { // Remove smaller, overlapping region - at the beginning
      rtree->commit_region(addr + 1 * cs, 4 * cs, stack, diff);
      rtree->uncommit_region(addr, 2 * cs, diff);
      R r[] = { { addr + 2 * cs, 3 * cs} };
      check(vmt, rmr, r);

      rtree->uncommit_region(addr + 1 * cs, 4 * cs, diff);
      check_empty(vmt, rmr);
    }

    { // Remove smaller, overlapping region - at the end
      rtree->commit_region(addr, 3 * cs, stack, diff);
      rtree->uncommit_region(addr + 2 * cs, 2 * cs, diff);
      R r[] = { { addr, 2 * cs} };
      check(vmt, rmr, r);

      rtree->uncommit_region(addr, 3 * cs, diff);
      check_empty(vmt, rmr);
    }

    rtree->tree().remove_all();
  }
};

TEST_VM(NMT_VirtualMemoryTracker, add_committed_region) {
  if (MemTracker::tracking_level() >= NMT_detail) {
    VirtualMemoryTrackerTest::test_add_committed_region();
  } else {
    tty->print_cr("skipped.");
  }
}

TEST_VM(NMT_VirtualMemoryTracker, remove_uncommitted_region) {
  if (MemTracker::tracking_level() >= NMT_detail) {
    VirtualMemoryTrackerTest::test_remove_uncommitted_region();
  } else {
    tty->print_cr("skipped.");
  }
}
