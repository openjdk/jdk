/*
 * Copyright (c) 2001, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1BiasedArray.hpp"
#include "gc/g1/g1RegionToSpaceMapper.hpp"
#include "logging/log.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/virtualspace.hpp"
#include "runtime/java.hpp"
#include "runtime/os.inline.hpp"
#include "services/memTracker.hpp"
#include "utilities/align.hpp"
#include "utilities/bitMap.inline.hpp"
#include "utilities/formatBuffer.hpp"

G1RegionToSpaceMapper::G1RegionToSpaceMapper(ReservedSpace rs,
                                             size_t used_size,
                                             size_t page_size,
                                             size_t region_granularity,
                                             size_t commit_factor,
                                             MemoryType type) :
  _listener(NULL),
  _storage(rs, used_size, page_size),
  _region_granularity(region_granularity),
  _commit_map(rs.size() * commit_factor / region_granularity, mtGC) {
  guarantee(is_power_of_2(page_size), "must be");
  guarantee(is_power_of_2(region_granularity), "must be");

  MemTracker::record_virtual_memory_type((address)rs.base(), type);
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
                                      MemoryType type) :
    G1RegionToSpaceMapper(rs, actual_size, page_size, alloc_granularity, commit_factor, type),
    _pages_per_region(alloc_granularity / (page_size * commit_factor)) {

    guarantee(alloc_granularity >= page_size, "allocation granularity smaller than commit granularity");
  }

  virtual void commit_regions(uint start_idx, size_t num_regions, WorkGang* pretouch_gang) {
    size_t const start_page = (size_t)start_idx * _pages_per_region;
    bool zero_filled = _storage.commit(start_page, num_regions * _pages_per_region);
    if (AlwaysPreTouch) {
      _storage.pretouch(start_page, num_regions * _pages_per_region, pretouch_gang);
    }
    _commit_map.set_range(start_idx, start_idx + num_regions);
    fire_on_commit(start_idx, num_regions, zero_filled);
  }

  virtual void uncommit_regions(uint start_idx, size_t num_regions) {
    _storage.uncommit((size_t)start_idx * _pages_per_region, num_regions * _pages_per_region);
    _commit_map.clear_range(start_idx, start_idx + num_regions);
  }
};

// G1RegionToSpaceMapper implementation where the region granularity is smaller
// than the commit granularity.
// Basically, the contents of one OS page span several regions.
class G1RegionsSmallerThanCommitSizeMapper : public G1RegionToSpaceMapper {
 private:
  class CommitRefcountArray : public G1BiasedMappedArray<uint> {
   protected:
     virtual uint default_value() const { return 0; }
  };

  size_t _regions_per_page;

  CommitRefcountArray _refcounts;

  uintptr_t region_idx_to_page_idx(uint region) const {
    return region / _regions_per_page;
  }

 public:
  G1RegionsSmallerThanCommitSizeMapper(ReservedSpace rs,
                                       size_t actual_size,
                                       size_t page_size,
                                       size_t alloc_granularity,
                                       size_t commit_factor,
                                       MemoryType type) :
    G1RegionToSpaceMapper(rs, actual_size, page_size, alloc_granularity, commit_factor, type),
    _regions_per_page((page_size * commit_factor) / alloc_granularity), _refcounts() {

    guarantee((page_size * commit_factor) >= alloc_granularity, "allocation granularity smaller than commit granularity");
    _refcounts.initialize((HeapWord*)rs.base(), (HeapWord*)(rs.base() + align_up(rs.size(), page_size)), page_size);
  }

  virtual void commit_regions(uint start_idx, size_t num_regions, WorkGang* pretouch_gang) {
    size_t const NoPage = ~(size_t)0;

    size_t first_committed = NoPage;
    size_t num_committed = 0;

    bool all_zero_filled = true;

    for (uint i = start_idx; i < start_idx + num_regions; i++) {
      assert(!_commit_map.at(i), "Trying to commit storage at region %u that is already committed", i);
      size_t idx = region_idx_to_page_idx(i);
      uint old_refcount = _refcounts.get_by_index(idx);

      bool zero_filled = false;
      if (old_refcount == 0) {
        if (first_committed == NoPage) {
          first_committed = idx;
          num_committed = 1;
        } else {
          num_committed++;
        }
        zero_filled = _storage.commit(idx, 1);
      }
      all_zero_filled &= zero_filled;

      _refcounts.set_by_index(idx, old_refcount + 1);
      _commit_map.set_bit(i);
    }
    if (AlwaysPreTouch && num_committed > 0) {
      _storage.pretouch(first_committed, num_committed, pretouch_gang);
    }
    fire_on_commit(start_idx, num_regions, all_zero_filled);
  }

  virtual void uncommit_regions(uint start_idx, size_t num_regions) {
    for (uint i = start_idx; i < start_idx + num_regions; i++) {
      assert(_commit_map.at(i), "Trying to uncommit storage at region %u that is not committed", i);
      size_t idx = region_idx_to_page_idx(i);
      uint old_refcount = _refcounts.get_by_index(idx);
      assert(old_refcount > 0, "must be");
      if (old_refcount == 1) {
        _storage.uncommit(idx, 1);
      }
      _refcounts.set_by_index(idx, old_refcount - 1);
      _commit_map.clear_bit(i);
    }
  }
};

void G1RegionToSpaceMapper::fire_on_commit(uint start_idx, size_t num_regions, bool zero_filled) {
  if (_listener != NULL) {
    _listener->on_commit(start_idx, num_regions, zero_filled);
  }
}

static bool map_nvdimm_space(ReservedSpace rs) {
  assert(AllocateOldGenAt != NULL, "");
  int _backing_fd = os::create_file_for_heap(AllocateOldGenAt);
  if (_backing_fd == -1) {
    log_error(gc, init)("Could not create file for Old generation at location %s", AllocateOldGenAt);
    return false;
  }
  // commit this memory in nv-dimm
  char* ret = os::attempt_reserve_memory_at(rs.size(), rs.base(), _backing_fd);

  if (ret != rs.base()) {
    if (ret != NULL) {
      os::unmap_memory(rs.base(), rs.size());
    }
    log_error(gc, init)("Error in mapping Old Gen to given AllocateOldGenAt = %s", AllocateOldGenAt);
    os::close(_backing_fd);
    return false;
  }

  os::close(_backing_fd);
  return true;
}

G1RegionToHeteroSpaceMapper::G1RegionToHeteroSpaceMapper(ReservedSpace rs,
                                                         size_t actual_size,
                                                         size_t page_size,
                                                         size_t alloc_granularity,
                                                         size_t commit_factor,
                                                         MemoryType type) :
  G1RegionToSpaceMapper(rs, actual_size, page_size, alloc_granularity, commit_factor, type),
  _rs(rs),
  _dram_mapper(NULL),
  _num_committed_dram(0),
  _num_committed_nvdimm(0),
  _start_index_of_dram(0),
  _page_size(page_size),
  _commit_factor(commit_factor),
  _type(type) {
  assert(actual_size == 2 * MaxHeapSize, "For 2-way heterogenuous heap, reserved space is two times MaxHeapSize");
}

bool G1RegionToHeteroSpaceMapper::initialize() {
  // Since we need to re-map the reserved space - 'Xmx' to nv-dimm and 'Xmx' to dram, we need to release the reserved memory first.
  // Because on some OSes (e.g. Windows) you cannot do a file mapping on memory reserved with regular mapping.
  os::release_memory(_rs.base(), _rs.size());
  // First half of size Xmx is for nv-dimm.
  ReservedSpace rs_nvdimm = _rs.first_part(MaxHeapSize);
  assert(rs_nvdimm.base() == _rs.base(), "We should get the same base address");

  // Second half of reserved memory is mapped to dram.
  ReservedSpace rs_dram = _rs.last_part(MaxHeapSize);

  assert(rs_dram.size() == rs_nvdimm.size() && rs_nvdimm.size() == MaxHeapSize, "They all should be same");

  // Reserve dram memory
  char* base = os::attempt_reserve_memory_at(rs_dram.size(), rs_dram.base());
  if (base != rs_dram.base()) {
    if (base != NULL) {
      os::release_memory(base, rs_dram.size());
    }
    log_error(gc, init)("Error in re-mapping memory on dram during G1 heterogenous memory initialization");
    return false;
  }

  // We reserve and commit this entire space to NV-DIMM.
  if (!map_nvdimm_space(rs_nvdimm)) {
    log_error(gc, init)("Error in re-mapping memory to nv-dimm during G1 heterogenous memory initialization");
    return false;
  }

  if (_region_granularity >= (_page_size * _commit_factor)) {
    _dram_mapper = new G1RegionsLargerThanCommitSizeMapper(rs_dram, rs_dram.size(), _page_size, _region_granularity, _commit_factor, _type);
  } else {
    _dram_mapper = new G1RegionsSmallerThanCommitSizeMapper(rs_dram, rs_dram.size(), _page_size, _region_granularity, _commit_factor, _type);
  }

  _start_index_of_dram = (uint)(rs_nvdimm.size() / _region_granularity);
  return true;
}

void G1RegionToHeteroSpaceMapper::commit_regions(uint start_idx, size_t num_regions, WorkGang* pretouch_gang) {
  uint end_idx = (start_idx + (uint)num_regions - 1);

  uint num_dram = end_idx >= _start_index_of_dram ? MIN2((end_idx - _start_index_of_dram + 1), (uint)num_regions) : 0;
  uint num_nvdimm = (uint)num_regions - num_dram;

  if (num_nvdimm > 0) {
    // We do not need to commit nv-dimm regions, since they are committed in the beginning.
    _num_committed_nvdimm += num_nvdimm;
  }
  if (num_dram > 0) {
    _dram_mapper->commit_regions(start_idx > _start_index_of_dram ? (start_idx - _start_index_of_dram) : 0, num_dram, pretouch_gang);
    _num_committed_dram += num_dram;
  }
}

void G1RegionToHeteroSpaceMapper::uncommit_regions(uint start_idx, size_t num_regions) {
  uint end_idx = (start_idx + (uint)num_regions - 1);
  uint num_dram = end_idx >= _start_index_of_dram ? MIN2((end_idx - _start_index_of_dram + 1), (uint)num_regions) : 0;
  uint num_nvdimm = (uint)num_regions - num_dram;

  if (num_nvdimm > 0) {
    // We do not uncommit memory for nv-dimm regions.
    _num_committed_nvdimm -= num_nvdimm;
  }

  if (num_dram > 0) {
    _dram_mapper->uncommit_regions(start_idx > _start_index_of_dram ? (start_idx - _start_index_of_dram) : 0, num_dram);
    _num_committed_dram -= num_dram;
  }
}

uint G1RegionToHeteroSpaceMapper::num_committed_dram() const {
  return _num_committed_dram;
}

uint G1RegionToHeteroSpaceMapper::num_committed_nvdimm() const {
  return _num_committed_nvdimm;
}

G1RegionToSpaceMapper* G1RegionToSpaceMapper::create_heap_mapper(ReservedSpace rs,
                                                                 size_t actual_size,
                                                                 size_t page_size,
                                                                 size_t region_granularity,
                                                                 size_t commit_factor,
                                                                 MemoryType type) {
  if (AllocateOldGenAt != NULL) {
    G1RegionToHeteroSpaceMapper* mapper = new G1RegionToHeteroSpaceMapper(rs, actual_size, page_size, region_granularity, commit_factor, type);
    if (!mapper->initialize()) {
      delete mapper;
      return NULL;
    }
    return (G1RegionToSpaceMapper*)mapper;
  } else {
    return create_mapper(rs, actual_size, page_size, region_granularity, commit_factor, type);
  }
}

G1RegionToSpaceMapper* G1RegionToSpaceMapper::create_mapper(ReservedSpace rs,
                                                            size_t actual_size,
                                                            size_t page_size,
                                                            size_t region_granularity,
                                                            size_t commit_factor,
                                                            MemoryType type) {
  if (region_granularity >= (page_size * commit_factor)) {
    return new G1RegionsLargerThanCommitSizeMapper(rs, actual_size, page_size, region_granularity, commit_factor, type);
  } else {
    return new G1RegionsSmallerThanCommitSizeMapper(rs, actual_size, page_size, region_granularity, commit_factor, type);
  }
}

void G1RegionToSpaceMapper::commit_and_set_special() {
  _storage.commit_and_set_special();
}
