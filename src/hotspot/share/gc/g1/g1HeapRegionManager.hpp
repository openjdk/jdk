/*
 * Copyright (c) 2001, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1HEAPREGIONMANAGER_HPP
#define SHARE_GC_G1_G1HEAPREGIONMANAGER_HPP

#include "gc/g1/g1BiasedArray.hpp"
#include "gc/g1/g1CommittedRegionMap.hpp"
#include "gc/g1/g1HeapRegionSet.hpp"
#include "gc/g1/g1RegionToSpaceMapper.hpp"
#include "memory/allocation.hpp"
#include "runtime/atomic.hpp"
#include "services/memoryUsage.hpp"

class G1HeapRegion;
class G1HeapRegionClaimer;
class G1HeapRegionClosure;
class G1FreeRegionList;
class WorkerThreads;

class G1HeapRegionTable : public G1BiasedMappedArray<G1HeapRegion*> {
 protected:
  virtual G1HeapRegion* default_value() const { return nullptr; }
};

// This class keeps track of the actual heap memory, auxiliary data
// and its metadata (i.e., G1HeapRegion instances) and the list of free regions.
//
// This allows maximum flexibility for deciding what to commit or uncommit given
// a request from outside.
//
// G1HeapRegions are kept in the _regions array in address order. A region's
// index in the array corresponds to its index in the heap (i.e., 0 is the
// region at the bottom of the heap, 1 is the one after it, etc.). Two
// regions that are consecutive in the array should also be adjacent in the
// address space (i.e., region(i).end() == region(i+1).bottom().
//
// We create a G1HeapRegion when we commit the region's address space
// for the first time. When we uncommit the address space of a
// region we retain the G1HeapRegion to be able to re-use it in the
// future (in case we recommit it).
//
// We keep track of four lengths:
//
// * _num_committed (returned by length()) is the number of currently
//   committed regions. These may not be contiguous.
// * _next_highest_used_hrm_index (not exposed outside this class) is the
//   highest heap region index +1 for which we have G1HeapRegions.
// * max_num_regions() returns the maximum number of regions the heap has reserved.
//

class G1HeapRegionManager: public CHeapObj<mtGC> {
  friend class VMStructs;
  friend class G1HeapRegionClaimer;

  G1RegionToSpaceMapper* _bot_mapper;
  G1RegionToSpaceMapper* _card_table_mapper;
  G1RegionToSpaceMapper* _refinement_table_mapper;

  // Keeps track of the currently committed regions in the heap. The committed regions
  // can either be active (ready for use) or inactive (ready for uncommit).
  G1CommittedRegionMap _committed_map;

  // Internal only. The highest heap region index +1 we allocated a G1HeapRegion instance for.
  uint _next_highest_used_hrm_index;

  HeapWord* heap_bottom() const { return _regions.bottom_address_mapped(); }
  HeapWord* heap_end() const {return _regions.end_address_mapped(); }

  // Pass down commit calls to the VirtualSpace.
  void commit_regions(uint index, size_t num_regions = 1, WorkerThreads* pretouch_workers = nullptr);

  // Initialize the G1HeapRegions in the range and put them on the free list.
  void initialize_regions(uint start, uint num_regions);

  // Find a contiguous set of empty or uncommitted regions of length num_regions and return
  // the index of the first region or G1_NO_HRM_INDEX if the search was unsuccessful.
  // Start and end defines the range to seek in, policy is first-fit.
  uint find_contiguous_in_range(uint start, uint end, uint num_regions);
  // Find a contiguous set of empty regions of length num_regions. Returns the start index
  // of that set, or G1_NO_HRM_INDEX.
  uint find_contiguous_in_free_list(uint num_regions);
  // Find a contiguous set of empty or unavailable regions of length num_regions. Returns the
  // start index of that set, or G1_NO_HRM_INDEX.
  uint find_contiguous_allow_expand(uint num_regions);

  void assert_contiguous_range(uint start, uint num_regions) NOT_DEBUG_RETURN;

  // Finds the next sequence of empty regions starting from start_idx (exclusive), going backwards in
  // the heap. Returns the length of the sequence found. If this value is zero, no
  // sequence could be found, otherwise res_idx contains the start index of this range.
  uint find_empty_from_idx_reverse(uint start_idx, uint* res_idx) const;

  // Checks the G1MemoryNodeManager to see if this region is on the preferred node.
  bool is_on_preferred_index(uint region_index, uint preferred_node_index);

  // Clear the auxiliary data structures by notifying them that the mapping has
  // changed. The structures that needs to be cleared will than clear. This is
  // used to allow reuse regions scheduled for uncommit without uncommitting and
  // then committing them.
  void clear_auxiliary_data_structures(uint start, uint num_regions);

  G1HeapRegionTable _regions;
  G1RegionToSpaceMapper* _heap_mapper;
  G1RegionToSpaceMapper* _bitmap_mapper;
  G1FreeRegionList _free_list;

  void expand(uint index, uint num_regions, WorkerThreads* pretouch_workers = nullptr);

  // G1RegionCommittedMap helpers. These functions do the work that comes with
  // the state changes tracked by G1CommittedRegionMap. To make sure this is
  // safe from a multi-threading point of view there are two lock protocols in
  // G1RegionCommittedMap::guarantee_mt_safety_* that are enforced. The lock
  // needed should have been acquired before calling these functions.
  void activate_regions(uint index, uint num_regions);
  void deactivate_regions(uint start, uint num_regions);
  void reactivate_regions(uint start, uint num_regions);
  void uncommit_regions(uint start, uint num_regions);

  // Allocate a new G1HeapRegion for the given index.
  G1HeapRegion* new_heap_region(uint hrm_index);

  // Humongous allocation helpers
  G1HeapRegion* allocate_humongous_from_free_list(uint num_regions);
  G1HeapRegion* allocate_humongous_allow_expand(uint num_regions);

  // Expand helper for cases when the regions to expand are well defined.
  void expand_exact(uint start, uint num_regions, WorkerThreads* pretouch_workers);
  // Expand helper activating inactive regions rather than committing new ones.
  uint expand_inactive(uint num_regions);
  // Expand helper finding new regions to commit.
  uint expand_any(uint num_regions, WorkerThreads* pretouch_workers);

#ifdef ASSERT
public:
  bool is_free(G1HeapRegion* hr) const;
#endif
public:
  // Empty constructor, we'll initialize it with the initialize() method.
  G1HeapRegionManager();

  void initialize(G1RegionToSpaceMapper* heap_storage,
                  G1RegionToSpaceMapper* bitmap,
                  G1RegionToSpaceMapper* bot,
                  G1RegionToSpaceMapper* card_table,
                  G1RegionToSpaceMapper* refinement_table);

  // Return the "dummy" region used for G1AllocRegion. This is currently a hardwired
  // new G1HeapRegion that owns G1HeapRegion at index 0. Since at the moment we commit
  // the heap from the lowest address, this region (and its associated data
  // structures) are available and we do not need to check further.
  G1HeapRegion* get_dummy_region() { return new_heap_region(0); }

  // Return the G1HeapRegion at the given index. Assume that the index
  // is valid.
  inline G1HeapRegion* at(uint index) const;

  // Return the G1HeapRegion at the given index, null if the index
  // is for an unavailable region.
  inline G1HeapRegion* at_or_null(uint index) const;

  // Returns whether the given region is available for allocation.
  inline bool is_available(uint region) const;

  // Return the next region (by index) that is part of the same
  // humongous object that hr is part of.
  inline G1HeapRegion* next_region_in_humongous(G1HeapRegion* hr) const;

  // If addr is within the committed space return its corresponding
  // G1HeapRegion, otherwise return null.
  inline G1HeapRegion* addr_to_region(HeapWord* addr) const;

  // Insert the given region into the free region list.
  inline void insert_into_free_list(G1HeapRegion* hr);

  // Rebuild the free region list from scratch.
  void rebuild_free_list(WorkerThreads* workers);

  // Insert the given region list into the global free region list.
  void insert_list_into_free_list(G1FreeRegionList* list) {
    _free_list.add_ordered(list);
  }

  // Allocate a free region with specific node index. If fails allocate with next node index.
  G1HeapRegion* allocate_free_region(G1HeapRegionType type, uint requested_node_index);

  // Allocate a humongous object from the free list
  G1HeapRegion* allocate_humongous(uint num_regions);

  // Allocate a humongous object by expanding the heap
  G1HeapRegion* expand_and_allocate_humongous(uint num_regions);

  inline G1HeapRegion* allocate_free_regions_starting_at(uint first, uint num_regions);

  // Remove all regions from the free list.
  void remove_all_free_regions() {
    _free_list.remove_all();
  }

  // Return the number of committed free regions in the heap.
  uint num_free_regions() const {
    return _free_list.length();
  }

  uint num_used_regions() const { return num_committed_regions() - num_free_regions(); }

  uint num_free_regions(uint node_index) const {
    return _free_list.length(node_index);
  }

  size_t total_free_bytes() const {
    return num_free_regions() * G1HeapRegion::GrainBytes;
  }

  // Return the number of regions uncommitted or ready to be uncommitted.
  uint num_inactive_regions() const { return max_num_regions() - num_committed_regions(); }

  // Return the number of regions currently active and available for use.
  uint num_committed_regions() const { return _committed_map.num_active(); }

  // The number of regions reserved for the heap.
  uint max_num_regions() const { return (uint)_regions.length(); }

  MemoryUsage get_auxiliary_data_memory_usage() const;

  MemRegion reserved() const { return MemRegion(heap_bottom(), heap_end()); }

  // Expand the sequence to reflect that the heap has grown. Either create new
  // G1HeapRegions, or re-use existing ones. Returns the number of regions the
  // sequence was expanded by. If a G1HeapRegion allocation fails, the resulting
  // number of regions might be smaller than what's desired.
  uint expand_by(uint num_regions, WorkerThreads* pretouch_workers);

  // Try to expand on the given node index, returning the index of the new region.
  uint expand_on_preferred_node(uint node_index);

  G1HeapRegion* next_region_in_heap(const G1HeapRegion* r) const;

  // Allocate the regions that contain the address range specified, committing the
  // regions if necessary. Return false if any of the regions is already committed
  // and not free, and return the number of regions newly committed in commit_count.
  bool allocate_containing_regions(MemRegion range, size_t* commit_count, WorkerThreads* pretouch_workers);

  // Apply blk->do_heap_region() on all committed regions in address order,
  // terminating the iteration early if do_heap_region() returns true.
  void iterate(G1HeapRegionClosure* blk) const;
  void iterate(G1HeapRegionIndexClosure* blk) const;

  void par_iterate(G1HeapRegionClosure* blk, G1HeapRegionClaimer* hrclaimer, const uint start_index) const;

  // Uncommit up to num_regions_to_remove regions that are completely free.
  // Return the actual number of uncommitted regions.
  uint shrink_by(uint num_regions_to_remove);

  // Remove a number of regions starting at the specified index, which must be available,
  // empty, and free. The regions are marked inactive and can later be uncommitted.
  void shrink_at(uint index, size_t num_regions);

  // Check if there are any inactive regions that can be uncommitted.
  bool has_inactive_regions() const;

  // Uncommit inactive regions. Limit the number of regions to uncommit and return
  // actual number uncommitted.
  uint uncommit_inactive_regions(uint limit);

  void verify();

  // Do some sanity checking.
  void verify_optional() PRODUCT_RETURN;
};

// The G1HeapRegionClaimer is used during parallel iteration over heap regions,
// allowing workers to claim heap regions, gaining exclusive rights to these regions.
class G1HeapRegionClaimer : public StackObj {
  uint           _n_workers;
  uint           _n_regions;
  Atomic<uint>*  _claims;

  static const uint Unclaimed = 0;
  static const uint Claimed   = 1;

 public:
  G1HeapRegionClaimer(uint n_workers);
  ~G1HeapRegionClaimer();

  inline uint n_regions() const {
    return _n_regions;
  }

  void set_n_workers(uint n_workers) {
    assert(_n_workers == 0, "already set");
    assert(n_workers > 0, "must be");
    _n_workers = n_workers;
  }
  // Return a start offset given a worker id.
  uint offset_for_worker(uint worker_id) const;

  // Check if region has been claimed with this HRClaimer.
  bool is_region_claimed(uint region_index) const;

  // Claim the given region, returns true if successfully claimed.
  bool claim_region(uint region_index);
};
#endif // SHARE_GC_G1_G1HEAPREGIONMANAGER_HPP
