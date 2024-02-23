#include "precompiled.hpp"

#include "memory/virtualspace.hpp"
#include "nmt/memTracker.hpp"
#include "nmt/virtualMemoryTracker.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include "unittest.hpp"

#include <stdio.h>

class VirtualMemoryViewTest : public testing::Test {
  using VMV = VirtualMemoryView;
public:
  static VirtualMemoryView vmv;
  static VMV::PhysicalMemorySpace space;

  VirtualMemoryViewTest() {
    space = VirtualMemoryView::PhysicalMemorySpace{0};
    VirtualMemoryView::OffsetRegionStorage to_copy_mapped{};
    VirtualMemoryView::RegionStorage to_copy_committed{};
    VirtualMemorySnapshot to_copy_snapshot{};

    vmv._virt_mem.mapped_regions.at_put_grow(space.id, to_copy_mapped);
    vmv._virt_mem.committed_regions.at_put_grow(space.id, to_copy_committed);
    vmv._virt_mem.summary.at_put_grow(space.id, to_copy_snapshot);
  }

  static address addr(size_t x) {
    return (address)x;
  }

  static void sort_n_merge() {
    auto& reserved_ranges = vmv._virt_mem.reserved_regions;
    vmv.merge_memregions(reserved_ranges);
    vmv.sort_regions(reserved_ranges);
    for (VMV::Id space_id = 0; space_id < VMV::PhysicalMemorySpace::unique_id; space_id++) {
      auto& mapped_ranges = vmv._virt_mem.mapped_regions.at(space_id);
      auto& committed_ranges = vmv._virt_mem.committed_regions.at(space_id);
      vmv.sort_regions(mapped_ranges);
      vmv.sort_regions(committed_ranges);
      vmv.merge_memregions(committed_ranges);
      vmv.merge_mapped(mapped_ranges);
    }
  }

  static void clear() {
    vmv._virt_mem.reserved_regions.clear();
    vmv._virt_mem.committed_regions.at(space.id).clear();
    vmv._virt_mem.mapped_regions.at(space.id).clear();
  }

  static void r(size_t address, size_t size, MEMFLAGS f = mtTest, const NativeCallStack& stack = CURRENT_PC) {
    vmv.reserve_memory(addr(address), size, f, stack);
  }
  static void c(size_t address, size_t size, const NativeCallStack& stack = CURRENT_PC) {
    vmv.commit_memory_into_space(space, addr(address), size, stack);
  }

  static void v(size_t address, size_t size, size_t offs, MEMFLAGS flag = mtTest, const NativeCallStack& stack = CURRENT_PC) {
    vmv.add_view_into_space(space, addr(address), size, addr(offs), flag, stack);
  }

  static void test_summary_computation() {
    clear();
    VirtualMemorySnapshot& snap = vmv._virt_mem.summary.at(space.id);
    r(0, 100);
    c(0, 25);
    // Outside of reserved zone => shouldn't be accounted for
    c(100, 25);
    vmv.compute_summary_snapshot(vmv._virt_mem);
    EXPECT_EQ(snap.by_type(mtTest)->committed(), (size_t)25);
    EXPECT_EQ(snap.by_type(mtTest)->reserved(), (size_t)100);
    // Map the reserved memory to an uncommitted place and re-compute the summary snapshot
    v(0, 100, 200);
    vmv.compute_summary_snapshot(vmv._virt_mem);
    EXPECT_EQ(snap.by_type(mtTest)->reserved(), (size_t)100);
    EXPECT_EQ(snap.by_type(mtTest)->committed(), (size_t)0);
    EXPECT_EQ(snap.by_type(mtTest)->peak_size(), (size_t)25);
  }

  static void test_reserve_commit_release() {
    clear();
    auto& reserved_ranges = vmv._virt_mem.reserved_regions;
    auto range_has = [&](int idx, address addr, size_t sz, MEMFLAGS flag = mtNone) {
      auto& rng = reserved_ranges.at(idx);
      ASSERT_TRUE(rng.size == sz);
      if (flag != mtNone) {
        ASSERT_TRUE(rng.flag == flag);
      }
      ASSERT_TRUE(rng.start == addr);
    };
    // Two adjacent ranges should be merged after sort+merge
    r(0, 100);
    r(100, 100);
    sort_n_merge();
    ASSERT_TRUE(reserved_ranges.length() == 1);
    range_has(0, 0, 200, mtTest);
    // Two identical ranges but with differing memflags are both kept
    r(0, 200, mtArguments);
    sort_n_merge();
    ASSERT_EQ(reserved_ranges.length(), 2);
    range_has(0, 0, 200);
    range_has(1, 0, 200);
    EXPECT_TRUE((reserved_ranges.at(0).flag == mtTest && reserved_ranges.at(1).flag == mtArguments)
                || (reserved_ranges.at(0).flag == mtArguments && reserved_ranges.at(1).flag == mtTest));

    // Should release both regions
    vmv.release_memory(addr(0), 200);
    ASSERT_TRUE(reserved_ranges.length() == 0);

    // Should be split into two
    r(0, 100, mtTest, CURRENT_PC);
    vmv.release_memory(addr(50), 1);
    ASSERT_EQ(reserved_ranges.length(), 2);
    ASSERT_EQ(reserved_ranges.at(0).size, (size_t)50);
    ASSERT_EQ(reserved_ranges.at(1).size, (size_t)49);
    // Should remove both
    vmv.release_memory(addr(0), 100);
    ASSERT_EQ(reserved_ranges.length(), 0);
  }
};
VirtualMemoryView VirtualMemoryViewTest::vmv{false /*is_detailed_mode*/};
VirtualMemoryView::PhysicalMemorySpace VirtualMemoryViewTest::space{0}; // Doesn't matter, will make new one during construction

TEST_VM_F(VirtualMemoryViewTest, TestReserveCommitRelease) {
  VirtualMemoryViewTest::test_reserve_commit_release();
}
TEST_VM_F(VirtualMemoryViewTest, TestSummaryComputation) {
  VirtualMemoryViewTest::test_summary_computation();
}
