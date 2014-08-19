/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/g1/g1BiasedArray.hpp"
#include "gc_implementation/g1/g1RegionToSpaceMapper.hpp"
#include "runtime/virtualspace.hpp"
#include "services/memTracker.hpp"
#include "utilities/bitMap.inline.hpp"

G1RegionToSpaceMapper::G1RegionToSpaceMapper(ReservedSpace rs,
                                             size_t commit_granularity,
                                             size_t region_granularity,
                                             MemoryType type) :
  _storage(),
  _commit_granularity(commit_granularity),
  _region_granularity(region_granularity),
  _listener(NULL),
  _commit_map() {
  guarantee(is_power_of_2(commit_granularity), "must be");
  guarantee(is_power_of_2(region_granularity), "must be");
  _storage.initialize_with_granularity(rs, commit_granularity);

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
                                      size_t os_commit_granularity,
                                      size_t alloc_granularity,
                                      size_t commit_factor,
                                      MemoryType type) :
     G1RegionToSpaceMapper(rs, os_commit_granularity, alloc_granularity, type),
    _pages_per_region(alloc_granularity / (os_commit_granularity * commit_factor)) {

    guarantee(alloc_granularity >= os_commit_granularity, "allocation granularity smaller than commit granularity");
    _commit_map.resize(rs.size() * commit_factor / alloc_granularity, /* in_resource_area */ false);
  }

  virtual void commit_regions(uintptr_t start_idx, size_t num_regions) {
    _storage.commit(start_idx * _pages_per_region, num_regions * _pages_per_region);
    _commit_map.set_range(start_idx, start_idx + num_regions);
    fire_on_commit(start_idx, num_regions);
  }

  virtual void uncommit_regions(uintptr_t start_idx, size_t num_regions) {
    _storage.uncommit(start_idx * _pages_per_region, num_regions * _pages_per_region);
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
                                       size_t os_commit_granularity,
                                       size_t alloc_granularity,
                                       size_t commit_factor,
                                       MemoryType type) :
     G1RegionToSpaceMapper(rs, os_commit_granularity, alloc_granularity, type),
    _regions_per_page((os_commit_granularity * commit_factor) / alloc_granularity), _refcounts() {

    guarantee((os_commit_granularity * commit_factor) >= alloc_granularity, "allocation granularity smaller than commit granularity");
    _refcounts.initialize((HeapWord*)rs.base(), (HeapWord*)(rs.base() + rs.size()), os_commit_granularity);
    _commit_map.resize(rs.size() * commit_factor / alloc_granularity, /* in_resource_area */ false);
  }

  virtual void commit_regions(uintptr_t start_idx, size_t num_regions) {
    for (uintptr_t i = start_idx; i < start_idx + num_regions; i++) {
      assert(!_commit_map.at(i), err_msg("Trying to commit storage at region "INTPTR_FORMAT" that is already committed", i));
      uintptr_t idx = region_idx_to_page_idx(i);
      uint old_refcount = _refcounts.get_by_index(idx);
      if (old_refcount == 0) {
        _storage.commit(idx, 1);
      }
      _refcounts.set_by_index(idx, old_refcount + 1);
      _commit_map.set_bit(i);
      fire_on_commit(i, 1);
    }
  }

  virtual void uncommit_regions(uintptr_t start_idx, size_t num_regions) {
    for (uintptr_t i = start_idx; i < start_idx + num_regions; i++) {
      assert(_commit_map.at(i), err_msg("Trying to uncommit storage at region "INTPTR_FORMAT" that is not committed", i));
      uintptr_t idx = region_idx_to_page_idx(i);
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

void G1RegionToSpaceMapper::fire_on_commit(uint start_idx, size_t num_regions) {
  if (_listener != NULL) {
    _listener->on_commit(start_idx, num_regions);
  }
}

G1RegionToSpaceMapper* G1RegionToSpaceMapper::create_mapper(ReservedSpace rs,
                                                            size_t os_commit_granularity,
                                                            size_t region_granularity,
                                                            size_t commit_factor,
                                                            MemoryType type) {

  if (region_granularity >= (os_commit_granularity * commit_factor)) {
    return new G1RegionsLargerThanCommitSizeMapper(rs, os_commit_granularity, region_granularity, commit_factor, type);
  } else {
    return new G1RegionsSmallerThanCommitSizeMapper(rs, os_commit_granularity, region_granularity, commit_factor, type);
  }
}
