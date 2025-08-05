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

#define check(rmr, regions) check_inner((rmr), (regions), ARRAY_SIZE(regions), __FILE__, __LINE__)

#define check_empty(rmr)                              \
  do {                                                \
    check_inner((rmr), nullptr, 0, __FILE__, __LINE__);  \
  } while (false)

static void diagnostic_print(const ReservedMemoryRegion& rmr) {
  LOG("In reserved region " PTR_FORMAT ", size %X:", p2i(rmr.base()), rmr.size());
  VirtualMemoryTracker::Instance::tree()->visit_committed_regions(rmr, [&](CommittedMemoryRegion& region) {
    LOG("   committed region: " PTR_FORMAT ", size %X", p2i(region.base()), region.size());
    return true;
  });
}

static void check_inner(const ReservedMemoryRegion& rmr, R* regions, size_t regions_size, const char* file, int line) {
  size_t i = 0;
  size_t size = 0;

  // Helpful log
  diagnostic_print(rmr);

#define WHERE " from " << file << ":" << line

  VirtualMemoryTracker::Instance::tree()->visit_committed_regions(rmr, [&](CommittedMemoryRegion& region) {
    EXPECT_LT(i, regions_size) << WHERE;
    EXPECT_EQ(region.base(), regions[i]._addr) << WHERE;
    EXPECT_EQ(region.size(), regions[i]._size) << WHERE;
    size += region.size();
    i++;
    return true;
  });

  EXPECT_EQ(i, regions_size) << WHERE;
  EXPECT_EQ(size, rmr.committed_size()) << WHERE;
}

class VirtualMemoryTrackerTest {
public:
  static void test_add_committed_region_adjacent() {
    size_t size  = 0x01000000;
    ReservedSpace rs = MemoryReserver::reserve(size, mtTest);

    address addr = (address)rs.base();

    address frame1 = (address)0x1234;
    address frame2 = (address)0x1235;

    NativeCallStack stack(&frame1, 1);
    NativeCallStack stack2(&frame2, 1);

    // Fetch the added RMR for the space
    RegionsTree* rtree = VirtualMemoryTracker::Instance::tree();
    MemTracker::NmtVirtualMemoryLocker nvml;

    ReservedMemoryRegion rmr = rtree->find_reserved_region(addr);

    ASSERT_EQ(rmr.size(), size);
    ASSERT_EQ(rmr.base(), addr);

    // Commit Size Granularity
    const size_t cs = 0x1000;

    // Commit adjacent regions with same stack

    { // Commit one region
      rtree->commit_region(addr + cs, cs, stack);
      R r[] = { {addr + cs, cs} };
      check(rmr, r);
    }

    { // Commit adjacent - lower address
      rtree->commit_region(addr, cs, stack);
      R r[] = { {addr, 2 * cs} };
      check(rmr, r);
    }

    { // Commit adjacent - higher address
      rtree->commit_region(addr + 2 * cs, cs, stack);
      R r[] = { {addr, 3 * cs} };
      check(rmr, r);
    }

    // Cleanup
    rtree->uncommit_region(addr, 3 * cs);
    ASSERT_EQ(rmr.committed_size(), 0u);


    // Commit adjacent regions with different stacks

    { // Commit one region
      rtree->commit_region(addr + cs, cs, stack);
      R r[] = { {addr + cs, cs} };
      check(rmr, r);
    }

    { // Commit adjacent - lower address
      rtree->commit_region(addr, cs, stack2);
      R r[] = { {addr,      cs},
                {addr + cs, cs} };
      check(rmr, r);
    }

    { // Commit adjacent - higher address
      rtree->commit_region(addr + 2 * cs, cs, stack2);
      R r[] = { {addr,          cs},
                {addr +     cs, cs},
                {addr + 2 * cs, cs} };
      check(rmr, r);
    }

    // Cleanup
    rtree->uncommit_region(addr, 3 * cs);
    ASSERT_EQ(rmr.committed_size(), 0u);

    rtree->tree().remove_all();
  }

  static void test_add_committed_region_adjacent_overlapping() {
    size_t size  = 0x01000000;
    ReservedSpace rs = MemoryReserver::reserve(size, mtTest);

    RegionsTree* rtree = VirtualMemoryTracker::Instance::tree();
    MemTracker::NmtVirtualMemoryLocker nvml;

    address addr = (address)rs.base();

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
      rtree->commit_region(addr, 2 * cs, stack);
      rtree->commit_region(addr + 3 * cs, 2 * cs, stack);
      R r[] = { {addr,          2 * cs},
                {addr + 3 * cs, 2 * cs} };
      check(rmr, r);
    }

    { // Commit adjacent and overlapping
      rtree->commit_region(addr + 2 * cs, 2 * cs, stack);
      R r[] = { {addr, 5 * cs} };
      check(rmr, r);
    }

    // revert to two non-adjacent regions
    rtree->uncommit_region(addr + 2 * cs, cs);
    ASSERT_EQ(rmr.committed_size(), 4 * cs);

    { // Commit overlapping and adjacent
      rtree->commit_region(addr + cs, 2 * cs, stack);
      R r[] = { {addr, 5 * cs} };
      check(rmr, r);
    }

    // Cleanup
    rtree->uncommit_region(addr, 5 * cs);
    ASSERT_EQ(rmr.committed_size(), 0u);


    // Commit adjacent and overlapping regions with different stacks

    { // Commit two non-adjacent regions
      rtree->commit_region(addr, 2 * cs, stack);
      rtree->commit_region(addr + 3 * cs, 2 * cs, stack);
      R r[] = { {addr,          2 * cs},
                {addr + 3 * cs, 2 * cs} };
      check(rmr, r);
    }

    { // Commit adjacent and overlapping
      rtree->commit_region(addr + 2 * cs, 2 * cs, stack2);
      R r[] = { {addr,          2 * cs},
                {addr + 2 * cs, 2 * cs},
                {addr + 4 * cs,     cs} };
      check(rmr, r);
    }

    // revert to two non-adjacent regions
    rtree->commit_region(addr, 5 * cs, stack);
    rtree->uncommit_region(addr + 2 * cs, cs);
    ASSERT_EQ(rmr.committed_size(), 4 * cs);

    { // Commit overlapping and adjacent
      rtree->commit_region(addr + cs, 2 * cs, stack2);
      R r[] = { {addr,              cs},
                {addr +     cs, 2 * cs},
                {addr + 3 * cs, 2 * cs} };
      check(rmr, r);
    }

    rtree->tree().remove_all();
  }

  static void test_add_committed_region_overlapping() {
    size_t size  = 0x01000000;
    ReservedSpace rs = MemoryReserver::reserve(size, mtTest);

    RegionsTree* rtree = VirtualMemoryTracker::Instance::tree();
    MemTracker::NmtVirtualMemoryLocker nvml;

    address addr = (address)rs.base();

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
      rtree->commit_region(addr, cs, stack);
      R r[] = { {addr, cs} };
      check(rmr, r);
    }

    { // Commit the same region
      rtree->commit_region(addr, cs, stack);
      R r[] = { {addr, cs} };
      check(rmr, r);
    }

    { // Commit a succeeding region
      rtree->commit_region(addr + cs, cs, stack);
      R r[] = { {addr, 2 * cs} };
      check(rmr, r);
    }

    { // Commit  over two regions
      rtree->commit_region(addr, 2 * cs, stack);
      R r[] = { {addr, 2 * cs} };
      check(rmr, r);
    }

    {// Commit first part of a region
      rtree->commit_region(addr, cs, stack);
      R r[] = { {addr, 2 * cs} };
      check(rmr, r);
    }

    { // Commit second part of a region
      rtree->commit_region(addr + cs, cs, stack);
      R r[] = { {addr, 2 * cs} };
      check(rmr, r);
    }

    { // Commit a third part
      rtree->commit_region(addr + 2 * cs, cs, stack);
      R r[] = { {addr, 3 * cs} };
      check(rmr, r);
    }

    { // Commit in the middle of a region
      rtree->commit_region(addr + 1 * cs, cs, stack);
      R r[] = { {addr, 3 * cs} };
      check(rmr, r);
    }

    // Cleanup
    rtree->uncommit_region(addr, 3 * cs);
    ASSERT_EQ(rmr.committed_size(), 0u);

    // With preceding region

    rtree->commit_region(addr,              cs, stack);
    rtree->commit_region(addr + 2 * cs, 3 * cs, stack);

    rtree->commit_region(addr + 2 * cs,     cs, stack);
    {
      R r[] = { {addr,              cs},
                {addr + 2 * cs, 3 * cs} };
      check(rmr, r);
    }

    rtree->commit_region(addr + 3 * cs,     cs, stack);
    {
      R r[] = { {addr,              cs},
                {addr + 2 * cs, 3 * cs} };
      check(rmr, r);
    }

    rtree->commit_region(addr + 4 * cs,     cs, stack);
    {
      R r[] = { {addr,              cs},
                {addr + 2 * cs, 3 * cs} };
      check(rmr, r);
    }

    // Cleanup
    rtree->uncommit_region(addr, 5 * cs);
    ASSERT_EQ(rmr.committed_size(), 0u);

    // With different stacks

    { // Commit one region
      rtree->commit_region(addr, cs, stack);
      R r[] = { {addr, cs} };
      check(rmr, r);
    }

    { // Commit the same region
      rtree->commit_region(addr, cs, stack2);
      R r[] = { {addr, cs} };
      check(rmr, r);
    }

    { // Commit a succeeding region
      rtree->commit_region(addr + cs, cs, stack);
      R r[] = { {addr,      cs},
                {addr + cs, cs} };
      check(rmr, r);
    }

    { // Commit  over two regions
      rtree->commit_region(addr, 2 * cs, stack);
      R r[] = { {addr, 2 * cs} };
      check(rmr, r);
    }

    {// Commit first part of a region
      rtree->commit_region(addr, cs, stack2);
      R r[] = { {addr,      cs},
                {addr + cs, cs} };
      check(rmr, r);
    }

    { // Commit second part of a region
      rtree->commit_region(addr + cs, cs, stack2);
      R r[] = { {addr, 2 * cs} };
      check(rmr, r);
    }

    { // Commit a third part
      rtree->commit_region(addr + 2 * cs, cs, stack2);
      R r[] = { {addr, 3 * cs} };
      check(rmr, r);
    }

    { // Commit in the middle of a region
      rtree->commit_region(addr + 1 * cs, cs, stack);
      R r[] = { {addr,          cs},
                {addr +     cs, cs},
                {addr + 2 * cs, cs} };
      check(rmr, r);
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
    size_t size  = 0x01000000;
    ReservedSpace rs = MemoryReserver::reserve(size, mtTest);

    RegionsTree* rtree = VirtualMemoryTracker::Instance::tree();
    MemTracker::NmtVirtualMemoryLocker nvml;

    address addr = (address)rs.base();

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
      rtree->commit_region(addr, 3 * cs, stack);
      R r[] = { {addr, 3 * cs} };
      check(rmr, r);

      // Remove only existing
      rtree->uncommit_region(addr, 3 * cs);
      check_empty(rmr);
    }

    {
      rtree->commit_region(addr + 0 * cs, cs, stack);
      rtree->commit_region(addr + 2 * cs, cs, stack);
      rtree->commit_region(addr + 4 * cs, cs, stack);

      { // Remove first
        rtree->uncommit_region(addr, cs);
        R r[] = { {addr + 2 * cs, cs},
                  {addr + 4 * cs, cs} };
        check(rmr, r);
      }

      // add back
      rtree->commit_region(addr,          cs, stack);

      { // Remove middle
        rtree->uncommit_region(addr + 2 * cs, cs);
        R r[] = { {addr + 0 * cs, cs},
                  {addr + 4 * cs, cs} };
        check(rmr, r);
      }

      // add back
      rtree->commit_region(addr + 2 * cs, cs, stack);

      { // Remove end
        rtree->uncommit_region(addr + 4 * cs, cs);
        R r[] = { {addr + 0 * cs, cs},
                  {addr + 2 * cs, cs} };
        check(rmr, r);
      }

      rtree->uncommit_region(addr, 5 * cs);
      check_empty(rmr);
    }

    { // Remove larger region
      rtree->commit_region(addr + 1 * cs, cs, stack);
      rtree->uncommit_region(addr, 3 * cs);
      check_empty(rmr);
    }

    { // Remove smaller region - in the middle
      rtree->commit_region(addr, 3 * cs, stack);
      rtree->uncommit_region(addr + 1 * cs, cs);
      R r[] = { { addr + 0 * cs, cs},
                { addr + 2 * cs, cs} };
      check(rmr, r);

      rtree->uncommit_region(addr, 3 * cs);
      check_empty(rmr);
    }

    { // Remove smaller region - at the beginning
      rtree->commit_region(addr, 3 * cs, stack);
      rtree->uncommit_region(addr + 0 * cs, cs);
      R r[] = { { addr + 1 * cs, 2 * cs} };
      check(rmr, r);

      rtree->uncommit_region(addr, 3 * cs);
      check_empty(rmr);
    }

    { // Remove smaller region - at the end
      rtree->commit_region(addr, 3 * cs, stack);
      rtree->uncommit_region(addr + 2 * cs, cs);
      R r[] = { { addr, 2 * cs} };
      check(rmr, r);

      rtree->uncommit_region(addr, 3 * cs);
      check_empty(rmr);
    }

    { // Remove smaller, overlapping region - at the beginning
      rtree->commit_region(addr + 1 * cs, 4 * cs, stack);
      rtree->uncommit_region(addr, 2 * cs);
      R r[] = { { addr + 2 * cs, 3 * cs} };
      check(rmr, r);

      rtree->uncommit_region(addr + 1 * cs, 4 * cs);
      check_empty(rmr);
    }

    { // Remove smaller, overlapping region - at the end
      rtree->commit_region(addr, 3 * cs, stack);
      rtree->uncommit_region(addr + 2 * cs, 2 * cs);
      R r[] = { { addr, 2 * cs} };
      check(rmr, r);

      rtree->uncommit_region(addr, 3 * cs);
      check_empty(rmr);
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
