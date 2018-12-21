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
 *
 */

#ifndef SHARE_VM_GC_G1_HETEROGENEOUSHEAPREGIONMANAGER_HPP
#define SHARE_VM_GC_G1_HETEROGENEOUSHEAPREGIONMANAGER_HPP

#include "gc/g1/heapRegionManager.hpp"

// This class manages heap regions on heterogenous memory comprising of dram and nv-dimm.
// Regions in dram (dram_set) are used for young objects and archive regions (CDS).
// Regions in nv-dimm (nvdimm_set) are used for old objects and humongous objects.
// At any point there are some regions committed on dram and some on nv-dimm with the following guarantees:
//   1. The total number of regions committed in dram and nv-dimm equals the current size of heap.
//   2. Consequently, total number of regions committed is less than or equal to Xmx.
//   3. To maintain the guarantee stated by 1., whenever one set grows (new regions committed), the other set shrinks (regions un-committed).
//      3a. If more dram regions are needed (young generation expansion), corresponding number of regions in nv-dimm are un-committed.
//      3b. When old generation or humongous set grows, and new regions need to be committed to nv-dimm, corresponding number of regions
//            are un-committed in dram.
class HeterogeneousHeapRegionManager : public HeapRegionManager {
  const uint _max_regions;
  uint _max_dram_regions;
  uint _max_nvdimm_regions;
  uint _start_index_of_nvdimm;
  uint _total_commited_before_full_gc;
  uint _no_borrowed_regions;

  uint total_regions_committed() const;
  uint num_committed_dram() const;
  uint num_committed_nvdimm() const;

  // Similar to find_unavailable_from_idx() function from base class, difference is this function searches in range [start, end].
  uint find_unavailable_in_range(uint start_idx, uint end_idx, uint* res_idx) const;

  // Expand into dram. Maintains the invariant that total number of committed regions is less than current heap size.
  uint expand_dram(uint num_regions, WorkGang* pretouch_workers);

  // Expand into nv-dimm.
  uint expand_nvdimm(uint num_regions, WorkGang* pretouch_workers);

  // Expand by finding unavailable regions in [start, end] range.
  uint expand_in_range(uint start, uint end, uint num_regions, WorkGang* pretouch_workers);

  // Shrink dram set of regions.
  uint shrink_dram(uint num_regions, bool update_free_list = true);

  // Shrink nv-dimm set of regions.
  uint shrink_nvdimm(uint num_regions, bool update_free_list = true);

  // Shrink regions from [start, end] range.
  uint shrink_in_range(uint start, uint end, uint num_regions, bool update_free_list = true);

  // Similar to find_empty_from_idx_reverse() in base class. Only here it searches in a range.
  uint find_empty_in_range_reverse(uint start_idx, uint end_idx, uint* res_idx);

  // Similar to find_contiguous() in base class, with [start, end] range
  uint find_contiguous(size_t start, size_t end, size_t num, bool empty_only);

  // This function is called when there are no free nv-dimm regions.
  // It borrows a region from the set of unavailable regions in nv-dimm for GC purpose.
  HeapRegion* borrow_old_region_for_gc();

  uint free_list_dram_length() const;
  uint free_list_nvdimm_length() const;

  // is region with given index in nv-dimm?
  bool is_in_nvdimm(uint index) const;
  bool is_in_dram(uint index) const;

public:

  // Empty constructor, we'll initialize it with the initialize() method.
  HeterogeneousHeapRegionManager(uint num_regions) : _max_regions(num_regions), _max_dram_regions(0),
                                                     _max_nvdimm_regions(0), _start_index_of_nvdimm(0),
                                                     _total_commited_before_full_gc(0), _no_borrowed_regions(0)
  {}

  static HeterogeneousHeapRegionManager* manager();

  virtual void initialize(G1RegionToSpaceMapper* heap_storage,
                          G1RegionToSpaceMapper* prev_bitmap,
                          G1RegionToSpaceMapper* next_bitmap,
                          G1RegionToSpaceMapper* bot,
                          G1RegionToSpaceMapper* cardtable,
                          G1RegionToSpaceMapper* card_counts);

  uint start_index_of_nvdimm() const;
  uint start_index_of_dram() const;
  uint end_index_of_nvdimm() const;
  uint end_index_of_dram() const;

  // Override.
  HeapRegion* get_dummy_region();

  // Adjust dram_set to provision 'expected_num_regions' regions.
  void adjust_dram_regions(uint expected_num_regions, WorkGang* pretouch_workers);

  // Prepare heap regions before and after full collection.
  void prepare_for_full_collection_start();
  void prepare_for_full_collection_end();

  virtual HeapRegion* allocate_free_region(HeapRegionType type);

  // Return maximum number of regions that heap can expand to.
  uint max_expandable_length() const;

  // Override. Expand in nv-dimm.
  uint expand_by(uint num_regions, WorkGang* pretouch_workers);

  // Override.
  uint expand_at(uint start, uint num_regions, WorkGang* pretouch_workers);

  // Override. This function is called for humongous allocation, so we need to find empty regions in nv-dimm.
  uint find_contiguous_only_empty(size_t num);

  // Override. This function is called for humongous allocation, so we need to find empty or unavailable regions in nv-dimm.
  uint find_contiguous_empty_or_unavailable(size_t num);

  // Overrides base class implementation to find highest free region in dram.
  uint find_highest_free(bool* expanded);

  // Override. This fuction is called to shrink the heap, we shrink in dram first then in nv-dimm.
  uint shrink_by(uint num_regions_to_remove);

  bool has_borrowed_regions() const;

  void verify();
};

#endif // SHARE_VM_GC_G1_HETEROGENEOUSHEAPREGIONMANAGER_HPP
