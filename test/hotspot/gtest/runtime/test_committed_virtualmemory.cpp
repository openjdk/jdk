/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "nmt/memTracker.hpp"
#include "nmt/regionsTree.hpp"
#include "nmt/regionsTree.inline.hpp"
#include "runtime/thread.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include "unittest.hpp"

class CommittedVirtualMemoryTest {
public:
  static void test() {
    Thread* thr = Thread::current();
    address stack_end = thr->stack_end();
    size_t  stack_size = thr->stack_size();

    MemTracker::record_thread_stack(stack_end, stack_size);

    {
      MemTracker::NmtVirtualMemoryLocker nvml;
      VirtualMemoryTracker::Instance::add_reserved_region(stack_end, stack_size, CALLER_PC, mtThreadStack);
      // snapshot current stack usage
      VirtualMemoryTracker::Instance::snapshot_thread_stacks();
    }

    VirtualMemoryRegion rgn_found;
    {
      MemTracker::NmtVirtualMemoryLocker vml;
      rgn_found = VirtualMemoryTracker::Instance::tree()->find_reserved_region(stack_end);
    }

    ASSERT_TRUE(rgn_found.is_valid());
    ASSERT_EQ(rgn_found.base(), stack_end);

    int i = 0;
    address i_addr = (address)&i;
    bool found_i_addr = false;

    // Stack grows downward.
    address stack_top = stack_end + stack_size;
    {
      MemTracker::NmtVirtualMemoryLocker vml;
      // For thread stacks, this historically named API visits resident ranges.
      // Not all committed pages have to be resident.
      VirtualMemoryTracker::Instance::tree()->visit_committed_regions(rgn_found, [&](const VirtualMemoryRegion& rgn) {
        EXPECT_GE(rgn.base(), stack_end);
        EXPECT_LE(rgn.end(), stack_top);
        if (i_addr < stack_top && i_addr >= rgn.base()) {
          found_i_addr = true;
        }
        i++;
        return true;
      });
    }

    // Stack and guard pages may be contiguous as one region.
    ASSERT_TRUE(i >= 1);
    ASSERT_TRUE(found_i_addr);
  }

  static const int PAGE_CONTAINED_IN_RANGE_TAG = -1;
  static bool is_page_in_committed_region(int a) { return (a == PAGE_CONTAINED_IN_RANGE_TAG); }
  static void set_page_as_contained_in_committed_region(int &a) { a = PAGE_CONTAINED_IN_RANGE_TAG; }

  static void check_covered_pages(address addr, size_t size, address base, size_t touch_pages, int* page_num) {
    const size_t page_sz = os::vm_page_size();
    size_t index;
    for (index = 0; index < touch_pages; index ++) {
      if (is_page_in_committed_region(page_num[index])) { // Already tagged?
        continue;
      }
      address page_addr = base + page_num[index] * page_sz;
      // The range covers this page, marks the page
      if (page_addr >= addr && page_addr < addr + size) {
        set_page_as_contained_in_committed_region(page_num[index]);
      }
    }
  }

  static void test_committed_region_impl(size_t num_pages, size_t touch_pages, int* page_num) {
    const size_t page_sz = os::vm_page_size();
    const size_t size = num_pages * page_sz;
    char* base = os::reserve_memory(size, mtThreadStack);
    bool result = os::commit_memory(base, size, !ExecMem);
    size_t index;
    ASSERT_NE(base, (char*)nullptr);
    for (index = 0; index < touch_pages; index ++) {
      char* touch_addr = base + page_sz * page_num[index];
      *touch_addr = 'a';
    }

    // trigger the test
    VirtualMemoryRegion rgn_found;
    {
      MemTracker::NmtVirtualMemoryLocker nvml;
      VirtualMemoryTracker::Instance::snapshot_thread_stacks();
      rgn_found = VirtualMemoryTracker::Instance::tree()->find_reserved_region((address)base);
    }
    ASSERT_TRUE(rgn_found.is_valid());
    ASSERT_EQ(rgn_found.base(), (address)base);


    bool precise_tracking_supported = false;
    {
      MemTracker::NmtVirtualMemoryLocker nvml;
      VirtualMemoryTracker::Instance::tree()->visit_committed_regions(rgn_found, [&](const VirtualMemoryRegion& rgn){
        if (rgn.size() == size) {
          return false;
        } else {
          precise_tracking_supported = true;
          check_covered_pages(rgn.base(), rgn.size(), (address)base, touch_pages, page_num);
        }
        return true;
      });
    }

    if (precise_tracking_supported) {
      // All touched pages should be committed
      for (size_t index = 0; index < touch_pages; index ++) {
        ASSERT_TRUE(is_page_in_committed_region(page_num[index]));
      }
    }

    // Cleanup
    os::disclaim_memory(base, size);
    {
      MemTracker::NmtVirtualMemoryLocker nvml;
      VirtualMemoryTracker::Instance::remove_released_region((address)base, size);
      rgn_found = VirtualMemoryTracker::Instance::tree()->find_reserved_region((address)base);
    }
    ASSERT_TRUE(!rgn_found.is_valid());
  }

  static void test_committed_region() {
    // On Linux, we scan 1024 pages at a time.
    // Here, we test scenario that scans < 1024 pages.
    int small_range[] = {3, 9, 46};
    int mid_range[] = {0, 45, 100, 399, 400, 1000, 1031};
    int large_range[] = {100, 301, 1024, 2047, 2048, 2049, 2050, 3000};

    test_committed_region_impl(47, 3, small_range);
    test_committed_region_impl(1088, 5, mid_range);
    test_committed_region_impl(3074, 8, large_range);
  }

  static void test_partial_region() {
    bool   result;
    size_t resident_size;
    address resident_start;
    size_t index;

    const size_t page_sz = os::vm_page_size();
    const size_t num_pages = 4;
    const size_t size = num_pages * page_sz;
    char* base = os::reserve_memory(size, mtTest);
    ASSERT_NE(base, (char*)nullptr);
    result = os::commit_memory(base, size, !ExecMem);

    ASSERT_TRUE(result);
    // touch all pages
    for (index = 0; index < num_pages; index ++) {
      *(base + index * page_sz) = 'a';
    }

    // Test whole range
    result = os::first_resident_in_range((address)base, size, resident_start, resident_size);
    ASSERT_TRUE(result);
    ASSERT_EQ(num_pages * page_sz, resident_size);
    ASSERT_EQ(resident_start, (address)base);

    // Test beginning of the range
    result = os::first_resident_in_range((address)base, 2 * page_sz, resident_start, resident_size);
    ASSERT_TRUE(result);
    ASSERT_EQ(2 * page_sz, resident_size);
    ASSERT_EQ(resident_start, (address)base);

    // Test end of the range
    result = os::first_resident_in_range((address)(base + page_sz), 3 * page_sz, resident_start, resident_size);
    ASSERT_TRUE(result);
    ASSERT_EQ(3 * page_sz, resident_size);
    ASSERT_EQ(resident_start, (address)(base + page_sz));

    // Test middle of the range
    result = os::first_resident_in_range((address)(base + page_sz), 2 * page_sz, resident_start, resident_size);
    ASSERT_TRUE(result);
    ASSERT_EQ(2 * page_sz, resident_size);
    ASSERT_EQ(resident_start, (address)(base + page_sz));

    os::release_memory(base, size);
  }

  static void test_first_resident_in_range(size_t num_pages, size_t pages_to_touch) {
    bool result;
    size_t resident_size;
    address resident_start;
    size_t index;

    const size_t page_sz = os::vm_page_size();
    const size_t size = num_pages * page_sz;

    char* base = os::reserve_memory(size, mtTest);
    ASSERT_NE(base, (char*)nullptr);

    result = os::commit_memory(base, size, !ExecMem);
    ASSERT_TRUE(result);

    result = os::first_resident_in_range((address)base, size, resident_start, resident_size);
    ASSERT_FALSE(result);

    // Touch pages
    for (index = 0; index < pages_to_touch; index ++) {
      base[index * page_sz] = 'a';
    }

    result = os::first_resident_in_range((address)base, size, resident_start, resident_size);
    ASSERT_TRUE(result);
    ASSERT_EQ(pages_to_touch * page_sz, resident_size);
    ASSERT_EQ(resident_start, (address)base);

    os::uncommit_memory(base, size, false);

    result = os::first_resident_in_range((address)base, size, resident_start, resident_size);
    ASSERT_FALSE(result);

    os::release_memory(base, size);
  }
};

TEST_VM(NMTCommittedVirtualMemoryTracker, test_committed_virtualmemory_region) {
  //  This tests the VM-global NMT facility. The test must *not* modify global state,
  //  since that interferes with other tests!
  // The gtestLauncher are called with and without -XX:NativeMemoryTracking during jtreg-controlled
  //  gtests.

  if (MemTracker::tracking_level() >= NMT_detail) {
    CommittedVirtualMemoryTest::test();
    CommittedVirtualMemoryTest::test_committed_region();
    CommittedVirtualMemoryTest::test_partial_region();
  } else {
    tty->print_cr("skipped.");
  }

}

#if !defined(_AIX)
TEST_VM(NMTCommittedVirtualMemory, test_first_resident_in_range){
  CommittedVirtualMemoryTest::test_first_resident_in_range(1024, 1024);
  CommittedVirtualMemoryTest::test_first_resident_in_range(2, 1);
}
#endif

TEST_VM(NMTThreadStackTracking, test_merged_thread_stacks) {
  // Verifies that snapshot_thread_stacks() visits ALL thread stacks,
  // even when multiple stacks are merged into a single NMT vmem tree node.
  if (!MemTracker::enabled()) {
    return;
  }

  const size_t page_sz = os::vm_page_size();
  const size_t stack_size = 64 * page_sz;
  const size_t guard_size = 4 * page_sz;
  const size_t physical_per_stack = 5;
  const int num_stacks = 3;
  const size_t total_size = stack_size * num_stacks;

  // Reserve and commit a range to later subdivide into contiguous stack subregions.
  char* total_base = os::reserve_memory(total_size, mtTest);
  ASSERT_NE(total_base, (char*)nullptr);
  // Commit is only needed so that we can touch later
  bool result = os::commit_memory(total_base, total_size, !ExecMem);
  ASSERT_TRUE(result);
  // Remove it from NMT accounting while keeping the OS-level reservation.
  {
    MemTracker::NmtVirtualMemoryLocker nvml;
    VirtualMemoryTracker::Instance::remove_released_region((address)total_base, total_size);
  }

  // Simulate multiple adjacent mtThreadStack reservations, which NMT should merge into a single tree node.
  for (int i = 0; i < num_stacks; i++) {
    address stack_base = (address)(total_base + i * stack_size);
    {
      NativeCallStack ncs;
      MemTracker::NmtVirtualMemoryLocker nvml;
      VirtualMemoryTracker::Instance::add_reserved_region(stack_base, stack_size, ncs, mtThreadStack);
      // Simulated guard-page commits
      VirtualMemoryTracker::Instance::add_committed_region(stack_base, guard_size, ncs);
    }
    // Touch a few pages at the top of each fake stack.
    char* stack_top = total_base + (i + 1) * stack_size;
    for (size_t p = 1; p <= physical_per_stack; p++) {
      *(stack_top - p * page_sz) = 'a';
    }
  }

  // Double check that the fake stacks are merged into a single reserved region.
  VirtualMemoryRegion rgn_found;
  {
    MemTracker::NmtVirtualMemoryLocker nvml;
    rgn_found = VirtualMemoryTracker::Instance::tree()->find_reserved_region((address)total_base);
  }
  ASSERT_TRUE(rgn_found.is_valid());
  // We may get extra unexpected merging so just check the expected region is contained.
  EXPECT_LE(rgn_found.base(), (address)total_base);
  EXPECT_GE(rgn_found.end(), (address)(total_base + total_size));

  // We expect this to record the touched parts as NMT committed regions
  {
    MemTracker::NmtVirtualMemoryLocker nvml;
    VirtualMemoryTracker::Instance::snapshot_thread_stacks();
  }

  auto verify = [&](size_t pages) {
    for (int i = 0; i < num_stacks; i++) {
      address physical_end  = (address)(total_base + (i + 1) * stack_size);
      address physical_start = physical_end - pages * page_sz;
      bool contained = false;
      {
        MemTracker::NmtVirtualMemoryLocker nvml;
        VirtualMemoryTracker::Instance::tree()->visit_committed_regions(rgn_found, [&](const VirtualMemoryRegion& crgn) {
          if (crgn.base() <= physical_start && crgn.end() >= physical_end) {
            contained = true;
          }
          return true;
        });
      }
      EXPECT_TRUE(contained) << "Stack " << i << ": range [" << (void*)physical_start << ", " << (void*)physical_end << ") not contained by any NMT committed region";
    }
  };

  verify(physical_per_stack);

  // Simulate growing the stacks
  const size_t new_physical_per_stack = 2 * physical_per_stack;
  for (int i = 0; i < num_stacks; i++) {
    char* stack_top = total_base + (i + 1) * stack_size;
    for (size_t p = physical_per_stack + 1; p <= new_physical_per_stack; p++) {
      *(stack_top - p * page_sz) = 'a';
    }
  }

  {
    MemTracker::NmtVirtualMemoryLocker nvml;
    VirtualMemoryTracker::Instance::snapshot_thread_stacks();
  }

  verify(new_physical_per_stack);

  os::release_memory(total_base, total_size);
}
