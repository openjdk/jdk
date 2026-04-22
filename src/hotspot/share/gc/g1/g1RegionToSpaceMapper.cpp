/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1BiasedArray.hpp"
#include "gc/g1/g1NUMA.hpp"
#include "gc/g1/g1RegionToSpaceMapper.hpp"
#include "gc/shared/gc_globals.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/reservedSpace.hpp"
#include "nmt/memTracker.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/align.hpp"
#include "utilities/bitMap.inline.hpp"
#include "utilities/powerOfTwo.hpp"

G1RegionToSpaceMapper::G1RegionToSpaceMapper(ReservedSpace rs,
                                             size_t used_size,
                                             size_t page_size,
                                             size_t region_granularity,
                                             size_t commit_factor,
                                             MemTag mem_tag) :
  _listener(nullptr),
  _storage(rs, used_size, page_size),
  _region_commit_map(rs.size() * commit_factor / region_granularity, mtGC),
  _memory_tag(mem_tag) {
  guarantee(is_power_of_2(page_size), "must be");
  guarantee(is_power_of_2(region_granularity), "must be");

  MemTracker::record_virtual_memory_tag(rs, mem_tag);
}

// Used to manually signal a mapper to handle a set of regions as committed.
// Setting the 'zero_filled' parameter to false signals the mapper that the
// regions have not been cleared by the OS and that they need to be clear
// explicitly.
void G1RegionToSpaceMapper::signal_mapping_changed(uint start_idx, size_t num_regions) {
  fire_on_commit(start_idx, num_regions, false);
}

// G1RegionToSpaceMapper implementation where the region granularity is larger than
// or the same as the commit granularity.
// Basically, the space corresponding to one region region spans several OS pages.
class G1RegionsLargerThanCommitSizeMapper : public G1RegionToSpaceMapper {
 private:
  size_t _pages_per_region;

 public:
  G1RegionsLargerThanCommitSizeMapper(ReservedSpace rs,
                                      size_t actual_size,
                                      size_t page_size,
                                      size_t alloc_granularity,
                                      size_t commit_factor,
                                      MemTag mem_tag) :
    G1RegionToSpaceMapper(rs, actual_size, page_size, alloc_granularity, commit_factor, mem_tag),
    _pages_per_region(alloc_granularity / (page_size * commit_factor)) {

    guarantee(alloc_granularity >= page_size, "allocation granularity smaller than commit granularity");
  }

  bool is_range_committed(uint start_idx, size_t num_regions) {
    BitMap::idx_t end = start_idx + num_regions;
    return _region_commit_map.find_first_clear_bit(start_idx, end) == end;
  }

  bool is_range_uncommitted(uint start_idx, size_t num_regions) {
    BitMap::idx_t end = start_idx + num_regions;
    return _region_commit_map.find_first_set_bit(start_idx, end) == end;
  }

  virtual void commit_regions(uint start_idx, size_t num_regions, WorkerThreads* pretouch_workers) {
    guarantee(is_range_uncommitted(start_idx, num_regions),
              "Range not uncommitted, start: %u, num_regions: %zu",
              start_idx, num_regions);

    const size_t start_page = (size_t)start_idx * _pages_per_region;
    const size_t size_in_pages = num_regions * _pages_per_region;
    bool zero_filled = _storage.commit(start_page, size_in_pages);

    if (should_distribute_across_numa_nodes()) {
      for (uint region_index = start_idx; region_index < start_idx + num_regions; region_index++ ) {
        void* address = _storage.page_start(region_index * _pages_per_region);
        size_t size_in_bytes = _storage.page_size() * _pages_per_region;
        G1NUMA::numa()->request_memory_on_node(address, size_in_bytes, region_index);
      }
    }

    if (AlwaysPreTouch) {
      _storage.pretouch(start_page, size_in_pages, pretouch_workers);
    }
    _region_commit_map.par_set_range(start_idx, start_idx + num_regions, BitMap::unknown_range);
    fire_on_commit(start_idx, num_regions, zero_filled);
  }

  virtual void uncommit_regions(uint start_idx, size_t num_regions) {
    guarantee(is_range_committed(start_idx, num_regions),
             "Range not committed, start: %u, num_regions: %zu",
              start_idx, num_regions);

    _storage.uncommit((size_t)start_idx * _pages_per_region, num_regions * _pages_per_region);
    _region_commit_map.par_clear_range(start_idx, start_idx + num_regions, BitMap::unknown_range);
  }
};

// G1RegionToSpaceMapper implementation where the region granularity is smaller
// than the commit granularity.
// Basically, the contents of one OS page spans several regions.
class G1RegionsSmallerThanCommitSizeMapper : public G1RegionToSpaceMapper {
  size_t _regions_per_page;
  // Lock to prevent bitmap updates and the actual underlying
  // commit to get out of order. This can happen in the cases
  // where one thread is expanding the heap during a humongous
  // allocation and at the same time the service thread is
  // doing uncommit. These operations will not operate on the
  // same regions, but they might operate on regions sharing
  // an underlying OS page. So we need to make sure that both
  // those resources are in sync:
  // - G1RegionToSpaceMapper::_region_commit_map;
  // - G1PageBasedVirtualSpace::_committed (_storage.commit())
  Mutex _lock;

  size_t region_idx_to_page_idx(uint region_idx) const {
    return region_idx / _regions_per_page;
  }

  bool is_page_committed(size_t page_idx) {
    size_t region = page_idx * _regions_per_page;
    size_t region_limit = region + _regions_per_page;
    // Committed if there is a bit set in the range.
    return _region_commit_map.find_first_set_bit(region, region_limit) != region_limit;
  }

  bool commit_pages(size_t start_page, size_t size_in_pages) {
    bool result = _storage.commit(start_page, size_in_pages);

    if (should_distribute_across_numa_nodes()) {
      for (size_t page = start_page; page < start_page + size_in_pages; page++) {
        uint region = checked_cast<uint>(page * _regions_per_page);
        void* address = _storage.page_start(page);
        size_t size_in_bytes = _storage.page_size();
        G1NUMA::numa()->request_memory_on_node(address, size_in_bytes, region);
      }
    }
    return result;
  }

 public:
  G1RegionsSmallerThanCommitSizeMapper(ReservedSpace rs,
                                       size_t actual_size,
                                       size_t page_size,
                                       size_t alloc_granularity,
                                       size_t commit_factor,
                                       MemTag mem_tag) :
    G1RegionToSpaceMapper(rs, actual_size, page_size, alloc_granularity, commit_factor, mem_tag),
    _regions_per_page((page_size * commit_factor) / alloc_granularity),
    _lock(Mutex::service-3, "G1Mapper_lock") {

    guarantee((page_size * commit_factor) >= alloc_granularity, "allocation granularity smaller than commit granularity");
  }

  size_t find_first_uncommitted(size_t page, size_t end) {
    assert(page < end, "must be");
    while (page < end && is_page_committed(page)) {
      page++;
    }
    return page;
  }

  size_t find_first_committed(size_t page, size_t end) {
    while (page < end && !is_page_committed(page)) {
      page++;
    }
    return MIN2(page, end);
  }

  virtual void commit_regions(uint start_idx, size_t num_regions, WorkerThreads* pretouch_workers) {
    uint region_limit = (uint)(start_idx + num_regions);
    assert(num_regions > 0, "Must commit at least one region");
    assert(_region_commit_map.find_first_set_bit(start_idx, region_limit) == region_limit,
           "Should be no committed regions in the range [%u, %u)", start_idx, region_limit);

    size_t const NoPage = SIZE_MAX;

    size_t first_newly_committed = NoPage;
    size_t num_committed_pages = 0;

    size_t const start_page = region_idx_to_page_idx(start_idx);
    size_t const end_page = region_idx_to_page_idx(region_limit - 1) + 1;

    bool all_zero_filled = true;

    // Concurrent operations might operate on regions sharing the same
    // underlying OS page. See lock declaration for more details.
    {
      MutexLocker ml(&_lock, Mutex::_no_safepoint_check_flag);

      size_t uncommitted_l = find_first_uncommitted(start_page, end_page);
      size_t uncommitted_r = find_first_committed(uncommitted_l + 1, end_page);

      first_newly_committed = uncommitted_l;
      num_committed_pages = uncommitted_r - uncommitted_l;

      if (num_committed_pages > 0 &&
          !commit_pages(first_newly_committed, num_committed_pages)) {
        all_zero_filled = false;
      }

      all_zero_filled &= (uncommitted_l == start_page) && (uncommitted_r == end_page);

      // Update the commit map for the given range. Not using the par_set_range
      // since updates to _region_commit_map for this mapper is protected by _lock.
      _region_commit_map.set_range(start_idx, region_limit, BitMap::unknown_range);
    }

    if (AlwaysPreTouch && num_committed_pages > 0) {
      _storage.pretouch(first_newly_committed, num_committed_pages, pretouch_workers);
    }

    fire_on_commit(start_idx, num_regions, all_zero_filled);
  }

  virtual void uncommit_regions(uint start_idx, size_t num_regions) {
    uint region_limit = (uint)(start_idx + num_regions);
    assert(num_regions > 0, "Must uncommit at least one region");
    assert(_region_commit_map.find_first_clear_bit(start_idx, region_limit) == region_limit,
           "Should only be committed regions in the range [%u, %u)", start_idx, region_limit);

    size_t const start_page = region_idx_to_page_idx(start_idx);
    size_t const end_page = region_idx_to_page_idx(region_limit - 1) + 1;

    // Concurrent operations might operate on regions sharing the same
    // underlying OS page. See lock declaration for more details.
    MutexLocker ml(&_lock, Mutex::_no_safepoint_check_flag);
    // Clear commit map for the given range. Not using the par_clear_range since
    // updates to _region_commit_map for this mapper is protected by _lock.
    _region_commit_map.clear_range(start_idx, region_limit, BitMap::unknown_range);

    // We know all pages were committed before clearing the map. If the
    // the page is still marked as committed after the clear we should
    // not uncommit it.
    size_t uncommitted_l = find_first_uncommitted(start_page, end_page);
    size_t uncommitted_r = find_first_committed(uncommitted_l + 1, end_page);

    size_t num_uncommitted_pages_found = uncommitted_r - uncommitted_l;

    if (num_uncommitted_pages_found > 0) {
      _storage.uncommit(uncommitted_l, num_uncommitted_pages_found);
    }
  }
};

void G1RegionToSpaceMapper::fire_on_commit(uint start_idx, size_t num_regions, bool zero_filled) {
  if (_listener != nullptr) {
    _listener->on_commit(start_idx, num_regions, zero_filled);
  }
}

bool G1RegionToSpaceMapper::should_distribute_across_numa_nodes() const {
  return _memory_tag == mtJavaHeap && G1NUMA::numa()->is_enabled();
}

G1RegionToSpaceMapper* G1RegionToSpaceMapper::create_mapper(ReservedSpace rs,
                                                            size_t actual_size,
                                                            size_t page_size,
                                                            size_t region_granularity,
                                                            size_t commit_factor,
                                                            MemTag mem_tag) {
  if (region_granularity >= (page_size * commit_factor)) {
    return new G1RegionsLargerThanCommitSizeMapper(rs, actual_size, page_size, region_granularity, commit_factor, mem_tag);
  } else {
    return new G1RegionsSmallerThanCommitSizeMapper(rs, actual_size, page_size, region_granularity, commit_factor, mem_tag);
  }
}
