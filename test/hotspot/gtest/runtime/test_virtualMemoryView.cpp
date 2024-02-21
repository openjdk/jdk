#include "precompiled.hpp"

#include "memory/virtualspace.hpp"
#include "nmt/memTracker.hpp"
#include "nmt/virtualMemoryTracker.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include "unittest.hpp"

#include <stdio.h>

class VirtualMemoryViewTest {
  using vmv = VirtualMemoryView;
public:
  static address addr(size_t x) {
    return (address)x;
  }
  static void sort_n_merge() {
    for (vmv::Id space_id = 0; space_id < vmv::PhysicalMemorySpace::unique_id; space_id++) {
      auto& reserved_ranges = vmv::_virt_mem->reserved_regions.at(space_id);
      auto& mapped_ranges = vmv::_virt_mem->mapped_regions.at(space_id);
      auto& committed_ranges = vmv::_virt_mem->committed_regions.at(space_id);
      vmv::sort_regions(reserved_ranges);
      vmv::sort_regions(mapped_ranges);
      vmv::sort_regions(committed_ranges);
      vmv::merge_memregions(reserved_ranges);
      vmv::merge_memregions(committed_ranges);
      vmv::merge_mapped(mapped_ranges);
    }
  }

  static void test_merging() {
    auto& reserved_ranges = vmv::_virt_mem->reserved_regions.at(vmv::heap.id);
    auto range_has = [&](int idx, address addr, size_t sz, MEMFLAGS flag = mtNone) {
      auto& rng = reserved_ranges.at(idx);
      ASSERT_TRUE(rng.size == sz);
      if (flag != mtNone) {
        ASSERT_TRUE(rng.flag == flag);
      }
      ASSERT_TRUE(rng.start == addr);
    };
    // Two adjacent ranges should be merged after sort+merge
    vmv::reserve_memory(addr(0), 100, MEMFLAGS::mtTest, CURRENT_PC);
    vmv::reserve_memory(addr(100), 100, MEMFLAGS::mtTest, CURRENT_PC);
    sort_n_merge();
    ASSERT_TRUE(reserved_ranges.length() == 1);
    range_has(0, 0, 200, mtTest);
    // Two identical ranges but with differing memflags are both kept
    vmv::reserve_memory(addr(0), 200, MEMFLAGS::mtArguments, CURRENT_PC);
    sort_n_merge();
    ASSERT_TRUE(reserved_ranges.length() == 2);
    range_has(0, 0, 200);
    range_has(1, 0, 200);
    ASSERT_TRUE((reserved_ranges.at(0).flag == mtTest && reserved_ranges.at(1).flag == mtArguments)
                || (reserved_ranges.at(0).flag == mtArguments && reserved_ranges.at(1).flag == mtTest));
  }
};
