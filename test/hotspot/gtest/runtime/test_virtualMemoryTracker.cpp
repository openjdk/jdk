/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

// Included early because the NMT flags don't include it.
#include "utilities/macros.hpp"

#if INCLUDE_NMT

#include "services/memTracker.hpp"
#include "services/virtualMemoryTracker.hpp"
#include "utilities/globalDefinitions.hpp"
#include "unittest.hpp"

namespace {
  struct R {
    address _addr;
    size_t  _size;
  };
}

#define check(rmr, regions) check_inner((rmr), (regions), ARRAY_SIZE(regions), __FILE__, __LINE__)

#define check_empty(rmr)                              \
  do {                                                \
    check_inner((rmr), NULL, 0, __FILE__, __LINE__);  \
  } while (false)

static void check_inner(ReservedMemoryRegion* rmr, R* regions, size_t regions_size, const char* file, int line) {
  CommittedRegionIterator iter = rmr->iterate_committed_regions();
  size_t i = 0;
  size_t size = 0;

#define WHERE " from " << file << ":" << line

  for (const CommittedMemoryRegion* region = iter.next(); region != NULL; region = iter.next()) {
    EXPECT_LT(i, regions_size) << WHERE;
    EXPECT_EQ(region->base(), regions[i]._addr) << WHERE;
    EXPECT_EQ(region->size(), regions[i]._size) << WHERE;
    size += region->size();
    i++;
  }

  EXPECT_EQ(i, regions_size) << WHERE;
  EXPECT_EQ(size, rmr->committed_size()) << WHERE;
}

class VirtualMemoryTrackerTest {
public:
  static void test_add_committed_region_adjacent() {
    VirtualMemoryTracker::initialize(NMT_detail);
    VirtualMemoryTracker::late_initialize(NMT_detail);

    address addr = (address)0x10000000;
    size_t size  = 0x01000000;

    address frame1 = (address)0x1234;
    address frame2 = (address)0x1235;

    NativeCallStack stack(&frame1, 1);
    NativeCallStack stack2(&frame2, 1);

    // Add the reserved memory
    VirtualMemoryTracker::add_reserved_region(addr, size, stack, mtTest);

    // Fetch the added RMR added above
    ReservedMemoryRegion* rmr = VirtualMemoryTracker::_reserved_regions->find(ReservedMemoryRegion(addr, size));

    ASSERT_EQ(rmr->size(), size);
    ASSERT_EQ(rmr->base(), addr);

    // Commit Size Granularity
    const size_t cs = 0x1000;

    // Commit adjacent regions with same stack

    { // Commit one region
      rmr->add_committed_region(addr + cs, cs, stack);
      R r[] = { {addr + cs, cs} };
      check(rmr, r);
    }

    { // Commit adjacent - lower address
      rmr->add_committed_region(addr, cs, stack);
      R r[] = { {addr, 2 * cs} };
      check(rmr, r);
    }

    { // Commit adjacent - higher address
      rmr->add_committed_region(addr + 2 * cs, cs, stack);
      R r[] = { {addr, 3 * cs} };
      check(rmr, r);
    }

    // Cleanup
    rmr->remove_uncommitted_region(addr, 3 * cs);
    ASSERT_EQ(rmr->committed_size(), 0u);


    // Commit adjacent regions with different stacks

    { // Commit one region
      rmr->add_committed_region(addr + cs, cs, stack);
      R r[] = { {addr + cs, cs} };
      check(rmr, r);
    }

    { // Commit adjacent - lower address
      rmr->add_committed_region(addr, cs, stack2);
      R r[] = { {addr,      cs},
                {addr + cs, cs} };
      check(rmr, r);
    }

    { // Commit adjacent - higher address
      rmr->add_committed_region(addr + 2 * cs, cs, stack2);
      R r[] = { {addr,          cs},
                {addr +     cs, cs},
                {addr + 2 * cs, cs} };
      check(rmr, r);
    }

    // Cleanup
    rmr->remove_uncommitted_region(addr, 3 * cs);
    ASSERT_EQ(rmr->committed_size(), 0u);
  }

  static void test_add_committed_region_adjacent_overlapping() {
    VirtualMemoryTracker::initialize(NMT_detail);
    VirtualMemoryTracker::late_initialize(NMT_detail);

    address addr = (address)0x10000000;
    size_t size  = 0x01000000;

    address frame1 = (address)0x1234;
    address frame2 = (address)0x1235;

    NativeCallStack stack(&frame1, 1);
    NativeCallStack stack2(&frame2, 1);

    // Add the reserved memory
    VirtualMemoryTracker::add_reserved_region(addr, size, stack, mtTest);

    // Fetch the added RMR added above
    ReservedMemoryRegion* rmr = VirtualMemoryTracker::_reserved_regions->find(ReservedMemoryRegion(addr, size));

    ASSERT_EQ(rmr->size(), size);
    ASSERT_EQ(rmr->base(), addr);

    // Commit Size Granularity
    const size_t cs = 0x1000;

    // Commit adjacent and overlapping regions with same stack

    { // Commit two non-adjacent regions
      rmr->add_committed_region(addr, 2 * cs, stack);
      rmr->add_committed_region(addr + 3 * cs, 2 * cs, stack);
      R r[] = { {addr,          2 * cs},
                {addr + 3 * cs, 2 * cs} };
      check(rmr, r);
    }

    { // Commit adjacent and overlapping
      rmr->add_committed_region(addr + 2 * cs, 2 * cs, stack);
      R r[] = { {addr, 5 * cs} };
      check(rmr, r);
    }

    // revert to two non-adjacent regions
    rmr->remove_uncommitted_region(addr + 2 * cs, cs);
    ASSERT_EQ(rmr->committed_size(), 4 * cs);

    { // Commit overlapping and adjacent
      rmr->add_committed_region(addr + cs, 2 * cs, stack);
      R r[] = { {addr, 5 * cs} };
      check(rmr, r);
    }

    // Cleanup
    rmr->remove_uncommitted_region(addr, 5 * cs);
    ASSERT_EQ(rmr->committed_size(), 0u);


    // Commit adjacent and overlapping regions with different stacks

    { // Commit two non-adjacent regions
      rmr->add_committed_region(addr, 2 * cs, stack);
      rmr->add_committed_region(addr + 3 * cs, 2 * cs, stack);
      R r[] = { {addr,          2 * cs},
                {addr + 3 * cs, 2 * cs} };
      check(rmr, r);
    }

    { // Commit adjacent and overlapping
      rmr->add_committed_region(addr + 2 * cs, 2 * cs, stack2);
      R r[] = { {addr,          2 * cs},
                {addr + 2 * cs, 2 * cs},
                {addr + 4 * cs,     cs} };
      check(rmr, r);
    }

    // revert to two non-adjacent regions
    rmr->add_committed_region(addr, 5 * cs, stack);
    rmr->remove_uncommitted_region(addr + 2 * cs, cs);
    ASSERT_EQ(rmr->committed_size(), 4 * cs);

    { // Commit overlapping and adjacent
      rmr->add_committed_region(addr + cs, 2 * cs, stack2);
      R r[] = { {addr,              cs},
                {addr +     cs, 2 * cs},
                {addr + 3 * cs, 2 * cs} };
      check(rmr, r);
    }
  }

  static void test_add_committed_region_overlapping() {
    VirtualMemoryTracker::initialize(NMT_detail);
    VirtualMemoryTracker::late_initialize(NMT_detail);

    address addr = (address)0x10000000;
    size_t size  = 0x01000000;

    address frame1 = (address)0x1234;
    address frame2 = (address)0x1235;

    NativeCallStack stack(&frame1, 1);
    NativeCallStack stack2(&frame2, 1);

    // Add the reserved memory
    VirtualMemoryTracker::add_reserved_region(addr, size, stack, mtTest);

    // Fetch the added RMR added above
    ReservedMemoryRegion* rmr = VirtualMemoryTracker::_reserved_regions->find(ReservedMemoryRegion(addr, size));

    ASSERT_EQ(rmr->size(), size);
    ASSERT_EQ(rmr->base(), addr);

    // Commit Size Granularity
    const size_t cs = 0x1000;

    // With same stack

    { // Commit one region
      rmr->add_committed_region(addr, cs, stack);
      R r[] = { {addr, cs} };
      check(rmr, r);
    }

    { // Commit the same region
      rmr->add_committed_region(addr, cs, stack);
      R r[] = { {addr, cs} };
      check(rmr, r);
    }

    { // Commit a succeeding region
      rmr->add_committed_region(addr + cs, cs, stack);
      R r[] = { {addr, 2 * cs} };
      check(rmr, r);
    }

    { // Commit  over two regions
      rmr->add_committed_region(addr, 2 * cs, stack);
      R r[] = { {addr, 2 * cs} };
      check(rmr, r);
    }

    {// Commit first part of a region
      rmr->add_committed_region(addr, cs, stack);
      R r[] = { {addr, 2 * cs} };
      check(rmr, r);
    }

    { // Commit second part of a region
      rmr->add_committed_region(addr + cs, cs, stack);
      R r[] = { {addr, 2 * cs} };
      check(rmr, r);
    }

    { // Commit a third part
      rmr->add_committed_region(addr + 2 * cs, cs, stack);
      R r[] = { {addr, 3 * cs} };
      check(rmr, r);
    }

    { // Commit in the middle of a region
      rmr->add_committed_region(addr + 1 * cs, cs, stack);
      R r[] = { {addr, 3 * cs} };
      check(rmr, r);
    }

    // Cleanup
    rmr->remove_uncommitted_region(addr, 3 * cs);
    ASSERT_EQ(rmr->committed_size(), 0u);

    // With preceding region

    rmr->add_committed_region(addr,              cs, stack);
    rmr->add_committed_region(addr + 2 * cs, 3 * cs, stack);

    rmr->add_committed_region(addr + 2 * cs,     cs, stack);
    {
      R r[] = { {addr,              cs},
                {addr + 2 * cs, 3 * cs} };
      check(rmr, r);
    }

    rmr->add_committed_region(addr + 3 * cs,     cs, stack);
    {
      R r[] = { {addr,              cs},
                {addr + 2 * cs, 3 * cs} };
      check(rmr, r);
    }

    rmr->add_committed_region(addr + 4 * cs,     cs, stack);
    {
      R r[] = { {addr,              cs},
                {addr + 2 * cs, 3 * cs} };
      check(rmr, r);
    }

    // Cleanup
    rmr->remove_uncommitted_region(addr, 5 * cs);
    ASSERT_EQ(rmr->committed_size(), 0u);

    // With different stacks

    { // Commit one region
      rmr->add_committed_region(addr, cs, stack);
      R r[] = { {addr, cs} };
      check(rmr, r);
    }

    { // Commit the same region
      rmr->add_committed_region(addr, cs, stack2);
      R r[] = { {addr, cs} };
      check(rmr, r);
    }

    { // Commit a succeeding region
      rmr->add_committed_region(addr + cs, cs, stack);
      R r[] = { {addr,      cs},
                {addr + cs, cs} };
      check(rmr, r);
    }

    { // Commit  over two regions
      rmr->add_committed_region(addr, 2 * cs, stack);
      R r[] = { {addr, 2 * cs} };
      check(rmr, r);
    }

    {// Commit first part of a region
      rmr->add_committed_region(addr, cs, stack2);
      R r[] = { {addr,      cs},
                {addr + cs, cs} };
      check(rmr, r);
    }

    { // Commit second part of a region
      rmr->add_committed_region(addr + cs, cs, stack2);
      R r[] = { {addr, 2 * cs} };
      check(rmr, r);
    }

    { // Commit a third part
      rmr->add_committed_region(addr + 2 * cs, cs, stack2);
      R r[] = { {addr, 3 * cs} };
      check(rmr, r);
    }

    { // Commit in the middle of a region
      rmr->add_committed_region(addr + 1 * cs, cs, stack);
      R r[] = { {addr,          cs},
                {addr +     cs, cs},
                {addr + 2 * cs, cs} };
      check(rmr, r);
    }
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
    VirtualMemoryTracker::initialize(NMT_detail);
    VirtualMemoryTracker::late_initialize(NMT_detail);

    address addr = (address)0x10000000;
    size_t size  = 0x01000000;

    address frame1 = (address)0x1234;
    address frame2 = (address)0x1235;

    NativeCallStack stack(&frame1, 1);
    NativeCallStack stack2(&frame2, 1);

    // Add the reserved memory
    VirtualMemoryTracker::add_reserved_region(addr, size, stack, mtTest);

    // Fetch the added RMR added above
    ReservedMemoryRegion* rmr = VirtualMemoryTracker::_reserved_regions->find(ReservedMemoryRegion(addr, size));

    ASSERT_EQ(rmr->size(), size);
    ASSERT_EQ(rmr->base(), addr);

    // Commit Size Granularity
    const size_t cs = 0x1000;

    { // Commit regions
      rmr->add_committed_region(addr, 3 * cs, stack);
      R r[] = { {addr, 3 * cs} };
      check(rmr, r);

      // Remove only existing
      rmr->remove_uncommitted_region(addr, 3 * cs);
      check_empty(rmr);
    }

    {
      rmr->add_committed_region(addr + 0 * cs, cs, stack);
      rmr->add_committed_region(addr + 2 * cs, cs, stack);
      rmr->add_committed_region(addr + 4 * cs, cs, stack);

      { // Remove first
        rmr->remove_uncommitted_region(addr, cs);
        R r[] = { {addr + 2 * cs, cs},
                  {addr + 4 * cs, cs} };
        check(rmr, r);
      }

      // add back
      rmr->add_committed_region(addr,          cs, stack);

      { // Remove middle
        rmr->remove_uncommitted_region(addr + 2 * cs, cs);
        R r[] = { {addr + 0 * cs, cs},
                  {addr + 4 * cs, cs} };
        check(rmr, r);
      }

      // add back
      rmr->add_committed_region(addr + 2 * cs, cs, stack);

      { // Remove end
        rmr->remove_uncommitted_region(addr + 4 * cs, cs);
        R r[] = { {addr + 0 * cs, cs},
                  {addr + 2 * cs, cs} };
        check(rmr, r);
      }

      rmr->remove_uncommitted_region(addr, 5 * cs);
      check_empty(rmr);
    }

    { // Remove larger region
      rmr->add_committed_region(addr + 1 * cs, cs, stack);
      rmr->remove_uncommitted_region(addr, 3 * cs);
      check_empty(rmr);
    }

    { // Remove smaller region - in the middle
      rmr->add_committed_region(addr, 3 * cs, stack);
      rmr->remove_uncommitted_region(addr + 1 * cs, cs);
      R r[] = { { addr + 0 * cs, cs},
                { addr + 2 * cs, cs} };
      check(rmr, r);

      rmr->remove_uncommitted_region(addr, 3 * cs);
      check_empty(rmr);
    }

    { // Remove smaller region - at the beginning
      rmr->add_committed_region(addr, 3 * cs, stack);
      rmr->remove_uncommitted_region(addr + 0 * cs, cs);
      R r[] = { { addr + 1 * cs, 2 * cs} };
      check(rmr, r);

      rmr->remove_uncommitted_region(addr, 3 * cs);
      check_empty(rmr);
    }

    { // Remove smaller region - at the end
      rmr->add_committed_region(addr, 3 * cs, stack);
      rmr->remove_uncommitted_region(addr + 2 * cs, cs);
      R r[] = { { addr, 2 * cs} };
      check(rmr, r);

      rmr->remove_uncommitted_region(addr, 3 * cs);
      check_empty(rmr);
    }

    { // Remove smaller, overlapping region - at the beginning
      rmr->add_committed_region(addr + 1 * cs, 4 * cs, stack);
      rmr->remove_uncommitted_region(addr, 2 * cs);
      R r[] = { { addr + 2 * cs, 3 * cs} };
      check(rmr, r);

      rmr->remove_uncommitted_region(addr + 1 * cs, 4 * cs);
      check_empty(rmr);
    }

    { // Remove smaller, overlapping region - at the end
      rmr->add_committed_region(addr, 3 * cs, stack);
      rmr->remove_uncommitted_region(addr + 2 * cs, 2 * cs);
      R r[] = { { addr, 2 * cs} };
      check(rmr, r);

      rmr->remove_uncommitted_region(addr, 3 * cs);
      check_empty(rmr);
    }
  }
};

TEST_VM(VirtualMemoryTracker, add_committed_region) {
  VirtualMemoryTrackerTest::test_add_committed_region();
}

TEST_VM(VirtualMemoryTracker, remove_uncommitted_region) {
  VirtualMemoryTrackerTest::test_remove_uncommitted_region();
}

#endif // INCLUDE_NMT
