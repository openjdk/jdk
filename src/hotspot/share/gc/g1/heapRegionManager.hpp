/*
 * Copyright (c) 2001, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_HEAPREGIONMANAGER_HPP
#define SHARE_VM_GC_G1_HEAPREGIONMANAGER_HPP

#include "gc/g1/g1BiasedArray.hpp"
#include "gc/g1/g1CollectorPolicy.hpp"
#include "gc/g1/g1RegionToSpaceMapper.hpp"
#include "gc/g1/heapRegionSet.hpp"
#include "gc/shared/collectorPolicy.hpp"
#include "services/memoryUsage.hpp"

class HeapRegion;
class HeapRegionClosure;
class HeapRegionClaimer;
class FreeRegionList;
class WorkGang;

class G1HeapRegionTable : public G1BiasedMappedArray<HeapRegion*> {
 protected:
  virtual HeapRegion* default_value() const { return NULL; }
};

// This class keeps track of the actual heap memory, auxiliary data
// and its metadata (i.e., HeapRegion instances) and the list of free regions.
//
// This allows maximum flexibility for deciding what to commit or uncommit given
// a request from outside.
//
// HeapRegions are kept in the _regions array in address order. A region's
// index in the array corresponds to its index in the heap (i.e., 0 is the
// region at the bottom of the heap, 1 is the one after it, etc.). Two
// regions that are consecutive in the array should also be adjacent in the
// address space (i.e., region(i).end() == region(i+1).bottom().
//
// We create a HeapRegion when we commit the region's address space
// for the first time. When we uncommit the address space of a
// region we retain the HeapRegion to be able to re-use it in the
// future (in case we recommit it).
//
// We keep track of three lengths:
//
// * _num_committed (returned by length()) is the number of currently
//   committed regions. These may not be contiguous.
// * _allocated_heapregions_length (not exposed outside this class) is the
//   number of regions+1 for which we have HeapRegions.
// * max_length() returns the maximum number of regions the heap can have.
//

class HeapRegionManager: public CHeapObj<mtGC> {
  friend class VMStructs;
  friend class HeapRegionClaimer;

  G1RegionToSpaceMapper* _bot_mapper;
  G1RegionToSpaceMapper* _cardtable_mapper;
  G1RegionToSpaceMapper* _card_counts_mapper;

  // Each bit in this bitmap indicates that the corresponding region is available
  // for allocation.
  CHeapBitMap _available_map;

   // The number of regions committed in the heap.
  uint _num_committed;

  // Internal only. The highest heap region +1 we allocated a HeapRegion instance for.
  uint _allocated_heapregions_length;

  HeapWord* heap_bottom() const { return _regions.bottom_address_mapped(); }
  HeapWord* heap_end() const {return _regions.end_address_mapped(); }

  // Pass down commit calls to the VirtualSpace.
  void commit_regions(uint index, size_t num_regions = 1, WorkGang* pretouch_gang = NULL);

  // Notify other data structures about change in the heap layout.
  void update_committed_space(HeapWord* old_end, HeapWord* new_end);

  // Find a contiguous set of empty or uncommitted regions of length num and return
  // the index of the first region or G1_NO_HRM_INDEX if the search was unsuccessful.
  // If only_empty is true, only empty regions are considered.
  // Searches from bottom to top of the heap, doing a first-fit.
  uint find_contiguous(size_t num, bool only_empty);
  // Finds the next sequence of unavailable regions starting from start_idx. Returns the
  // length of the sequence found. If this result is zero, no such sequence could be found,
  // otherwise res_idx indicates the start index of these regions.
  uint find_unavailable_from_idx(uint start_idx, uint* res_idx) const;
  // Finds the next sequence of empty regions starting from start_idx, going backwards in
  // the heap. Returns the length of the sequence found. If this value is zero, no
  // sequence could be found, otherwise res_idx contains the start index of this range.
  uint find_empty_from_idx_reverse(uint start_idx, uint* res_idx) const;

protected:
  G1HeapRegionTable _regions;
  G1RegionToSpaceMapper* _heap_mapper;
  G1RegionToSpaceMapper* _prev_bitmap_mapper;
  G1RegionToSpaceMapper* _next_bitmap_mapper;
  FreeRegionList _free_list;

  void make_regions_available(uint index, uint num_regions = 1, WorkGang* pretouch_gang = NULL);
  void uncommit_regions(uint index, size_t num_regions = 1);
  // Allocate a new HeapRegion for the given index.
  HeapRegion* new_heap_region(uint hrm_index);
#ifdef ASSERT
public:
  bool is_free(HeapRegion* hr) const;
#endif
public:
  // Empty constructor, we'll initialize it with the initialize() method.
  HeapRegionManager();

  static HeapRegionManager* create_manager(G1CollectedHeap* heap, G1CollectorPolicy* policy);

  virtual void initialize(G1RegionToSpaceMapper* heap_storage,
                          G1RegionToSpaceMapper* prev_bitmap,
                          G1RegionToSpaceMapper* next_bitmap,
                          G1RegionToSpaceMapper* bot,
                          G1RegionToSpaceMapper* cardtable,
                          G1RegionToSpaceMapper* card_counts);

  // Prepare heap regions before and after full collection.
  // Nothing to be done in this class.
  virtual void prepare_for_full_collection_start() {}
  virtual void prepare_for_full_collection_end() {}

  // Return the "dummy" region used for G1AllocRegion. This is currently a hardwired
  // new HeapRegion that owns HeapRegion at index 0. Since at the moment we commit
  // the heap from the lowest address, this region (and its associated data
  // structures) are available and we do not need to check further.
  virtual HeapRegion* get_dummy_region() { return new_heap_region(0); }

  // Return the HeapRegion at the given index. Assume that the index
  // is valid.
  inline HeapRegion* at(uint index) const;

  // Return the HeapRegion at the given index, NULL if the index
  // is for an unavailable region.
  inline HeapRegion* at_or_null(uint index) const;

  // Returns whether the given region is available for allocation.
  bool is_available(uint region) const;

  // Return the next region (by index) that is part of the same
  // humongous object that hr is part of.
  inline HeapRegion* next_region_in_humongous(HeapRegion* hr) const;

  // If addr is within the committed space return its corresponding
  // HeapRegion, otherwise return NULL.
  inline HeapRegion* addr_to_region(HeapWord* addr) const;

  // Insert the given region into the free region list.
  inline void insert_into_free_list(HeapRegion* hr);

  // Insert the given region list into the global free region list.
  void insert_list_into_free_list(FreeRegionList* list) {
    _free_list.add_ordered(list);
  }

  virtual HeapRegion* allocate_free_region(HeapRegionType type) {
    HeapRegion* hr = _free_list.remove_region(!type.is_young());

    if (hr != NULL) {
      assert(hr->next() == NULL, "Single region should not have next");
      assert(is_available(hr->hrm_index()), "Must be committed");
    }
    return hr;
  }

  inline void allocate_free_regions_starting_at(uint first, uint num_regions);

  // Remove all regions from the free list.
  void remove_all_free_regions() {
    _free_list.remove_all();
  }

  // Return the number of committed free regions in the heap.
  uint num_free_regions() const {
    return _free_list.length();
  }

  size_t total_free_bytes() const {
    return num_free_regions() * HeapRegion::GrainBytes;
  }

  // Return the number of available (uncommitted) regions.
  uint available() const { return max_length() - length(); }

  // Return the number of regions that have been committed in the heap.
  uint length() const { return _num_committed; }

  // Return the maximum number of regions in the heap.
  uint max_length() const { return (uint)_regions.length(); }

  // Return maximum number of regions that heap can expand to.
  virtual uint max_expandable_length() const { return (uint)_regions.length(); }

  MemoryUsage get_auxiliary_data_memory_usage() const;

  MemRegion reserved() const { return MemRegion(heap_bottom(), heap_end()); }

  // Expand the sequence to reflect that the heap has grown. Either create new
  // HeapRegions, or re-use existing ones. Returns the number of regions the
  // sequence was expanded by. If a HeapRegion allocation fails, the resulting
  // number of regions might be smaller than what's desired.
  virtual uint expand_by(uint num_regions, WorkGang* pretouch_workers);

  // Makes sure that the regions from start to start+num_regions-1 are available
  // for allocation. Returns the number of regions that were committed to achieve
  // this.
  virtual uint expand_at(uint start, uint num_regions, WorkGang* pretouch_workers);

  // Find a contiguous set of empty regions of length num. Returns the start index of
  // that set, or G1_NO_HRM_INDEX.
  virtual uint find_contiguous_only_empty(size_t num) { return find_contiguous(num, true); }
  // Find a contiguous set of empty or unavailable regions of length num. Returns the
  // start index of that set, or G1_NO_HRM_INDEX.
  virtual uint find_contiguous_empty_or_unavailable(size_t num) { return find_contiguous(num, false); }

  HeapRegion* next_region_in_heap(const HeapRegion* r) const;

  // Find the highest free or uncommitted region in the reserved heap,
  // and if uncommitted, commit it. If none are available, return G1_NO_HRM_INDEX.
  // Set the 'expanded' boolean true if a new region was committed.
  virtual uint find_highest_free(bool* expanded);

  // Allocate the regions that contain the address range specified, committing the
  // regions if necessary. Return false if any of the regions is already committed
  // and not free, and return the number of regions newly committed in commit_count.
  bool allocate_containing_regions(MemRegion range, size_t* commit_count, WorkGang* pretouch_workers);

  // Apply blk->do_heap_region() on all committed regions in address order,
  // terminating the iteration early if do_heap_region() returns true.
  void iterate(HeapRegionClosure* blk) const;

  void par_iterate(HeapRegionClosure* blk, HeapRegionClaimer* hrclaimer, const uint start_index) const;

  // Uncommit up to num_regions_to_remove regions that are completely free.
  // Return the actual number of uncommitted regions.
  virtual uint shrink_by(uint num_regions_to_remove);

  // Uncommit a number of regions starting at the specified index, which must be available,
  // empty, and free.
  void shrink_at(uint index, size_t num_regions);

  virtual void verify();

  // Do some sanity checking.
  void verify_optional() PRODUCT_RETURN;
};

// The HeapRegionClaimer is used during parallel iteration over heap regions,
// allowing workers to claim heap regions, gaining exclusive rights to these regions.
class HeapRegionClaimer : public StackObj {
  uint           _n_workers;
  uint           _n_regions;
  volatile uint* _claims;

  static const uint Unclaimed = 0;
  static const uint Claimed   = 1;

 public:
  HeapRegionClaimer(uint n_workers);
  ~HeapRegionClaimer();

  inline uint n_regions() const {
    return _n_regions;
  }

  // Return a start offset given a worker id.
  uint offset_for_worker(uint worker_id) const;

  // Check if region has been claimed with this HRClaimer.
  bool is_region_claimed(uint region_index) const;

  // Claim the given region, returns true if successfully claimed.
  bool claim_region(uint region_index);
};
#endif // SHARE_VM_GC_G1_HEAPREGIONMANAGER_HPP
