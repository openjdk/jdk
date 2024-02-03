
/*
 * Copyright (c) 2016, 2019, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHFREESET_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHFREESET_HPP

#include "gc/shenandoah/shenandoahHeapRegionSet.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"

// Each ShenandoahHeapRegion is associated with a ShenandoahFreeSetPartitionId.
enum ShenandoahFreeSetPartitionId : uint8_t {
  NotFree,                      // Region has been retired and is not in any free set: there is no available memory.
  Mutator,                      // Region is in the Mutator free set: available memory is available to mutators.
  Collector,                    // Region is in the Collector free set: available memory is reserved for evacuations.

  NumPartitions                 // This value represents the size of an array that may be indexed by NotFree, Mutator, Collector.
};


// This class implements partitioning of regions into distinct sets.  Each ShenandoahHeapRegion is either in the Mutator free set,
// the Collector free set, or in neither free set (NotFree).
class ShenandoahRegionPartitions {

private:
  const size_t _max;            // The maximum number of heap regions
  const size_t _region_size_bytes;
  const ShenandoahFreeSet* _free_set;
  ShenandoahFreeSetPartitionId* const _membership;

  // For each type, we track an interval outside of which a region affiliated with that partition is guaranteed
  // not to be found. This makes searches for free space more efficient.  For each partition p, _leftmosts[p]
  // represents its least index, and its _rightmosts[p] its greatest index. Empty intervals are indicated by the
  // canonical [_max, 0].
  size_t _leftmosts[NumPartitions];
  size_t _rightmosts[NumPartitions];

  // Allocation for humongous objects needs to find regions that are entirely empty.  For each partion p, _leftmosts_empty[p]
  // represents the first region belonging to this partition that is completely empty and _rightmosts_empty[p] represents the
  // last region that is completely empty.  If there is no completely empty region in this partition, this is represented
  // by the canonical [_max, 0].
  size_t _leftmosts_empty[NumPartitions];
  size_t _rightmosts_empty[NumPartitions];

  // For each partition p, _capacity[p] represents the total amount of memory within the partition at the time
  // of the most recent rebuild, _used[p] represents the total amount of memory that has been allocated within this
  // partition (either already allocated as of the rebuild, or allocated since the rebuild).  _capacity[p] and _used[p]
  // are denoted in bytes.  Note that some regions that had been assigned to a particular partition at rebuild time
  // may have been retired following the rebuild.  The tallies for these regions are still reflected in _capacity[p]
  // and _used[p], even though the region may have been removed from the free set.
  size_t _capacity[NumPartitions];
  size_t _used[NumPartitions];
  size_t _region_counts[NumPartitions];

  inline void shrink_interval_if_boundary_modified(ShenandoahFreeSetPartitionId partition, size_t idx);
  inline void expand_interval_if_boundary_modified(ShenandoahFreeSetPartitionId partition, size_t idx, size_t capacity);

public:
  ShenandoahRegionPartitions(size_t max_regions, ShenandoahFreeSet* free_set);
  ~ShenandoahRegionPartitions();

  // Make all regions NotFree and reset all bounds
  void make_all_regions_unavailable();

  // Retire region idx from within its partition.  Requires that region idx is in in Mutator or Collector partitions.
  // Moves this region to the NotFree partition.  Any remnant of available memory at the time of retirement is added to the
  // original partition's total of used bytes.
  void retire_within_partition(size_t idx, size_t used_bytes);

  // Place region idx into free set which_partition.  Requires that idx is currently NotFree.
  void make_free(size_t idx, ShenandoahFreeSetPartitionId which_partition, size_t region_capacity);

  // Place region idx into free partition new_partition.  Requires that idx is currently not NotFree.
  void move_to_partition(size_t idx, ShenandoahFreeSetPartitionId new_partition, size_t region_capacity);

  // Returns the ShenandoahFreeSetPartitionId affiliation of region idx, NotFree if this region is not currently free.
  // This does not enforce that free_set membership implies allocation capacity.
  inline ShenandoahFreeSetPartitionId membership(size_t idx) const;

  // Returns true iff region idx is in the test_set free_set.  Before returning true, asserts that the free
  // set is not empty.  Requires that test_set != NotFree or NumPartitions.
  inline bool in_partition(size_t idx, ShenandoahFreeSetPartitionId which_partition) const;

  // The following four methods return the left-most and right-most bounds on ranges of regions representing
  // the requested set.  The _empty variants represent bounds on the range that holds completely empty
  // regions, which are required for humongous allocations and desired for "very large" allocations.
  //   if the requested which_partition is empty:
  //     leftmost() and leftmost_empty() return _max, rightmost() and rightmost_empty() return 0
  //   otherwise, expect the following:
  //     0 <= leftmost <= leftmost_empty <= rightmost_empty <= rightmost < _max
  inline size_t leftmost(ShenandoahFreeSetPartitionId which_partition) const;
  inline size_t rightmost(ShenandoahFreeSetPartitionId which_partition) const;
  size_t leftmost_empty(ShenandoahFreeSetPartitionId which_partition);
  size_t rightmost_empty(ShenandoahFreeSetPartitionId which_partition);

  inline bool is_empty(ShenandoahFreeSetPartitionId which_partition) const;

  inline void increase_used(ShenandoahFreeSetPartitionId which_partition, size_t bytes);

  inline size_t capacity_of(ShenandoahFreeSetPartitionId which_partition) const {
    assert (which_partition > NotFree && which_partition < NumPartitions, "selected free set must be valid");
    return _capacity[which_partition];
  }

  inline size_t used_by(ShenandoahFreeSetPartitionId which_partition) const {
    assert (which_partition > NotFree && which_partition < NumPartitions, "selected free set must be valid");
    return _used[which_partition];
  }

  inline void set_capacity_of(ShenandoahFreeSetPartitionId which_partition, size_t value) {
    assert (which_partition > NotFree && which_partition < NumPartitions, "selected free set must be valid");
    _capacity[which_partition] = value;
  }

  inline void set_used_by(ShenandoahFreeSetPartitionId which_partition, size_t value) {
    assert (which_partition > NotFree && which_partition < NumPartitions, "selected free set must be valid");
    _used[which_partition] = value;
  }

  inline size_t max() const { return _max; }

  inline size_t count(ShenandoahFreeSetPartitionId which_partition) const { return _region_counts[which_partition]; }

  // Assure leftmost, rightmost, leftmost_empty, and rightmost_empty bounds are valid for all free sets.
  // Valid bounds honor all of the following (where max is the number of heap regions):
  //   if the set is empty, leftmost equals max and rightmost equals 0
  //   Otherwise (the set is not empty):
  //     0 <= leftmost < max and 0 <= rightmost < max
  //     the region at leftmost is in the set
  //     the region at rightmost is in the set
  //     rightmost >= leftmost
  //     for every idx that is in the set {
  //       idx >= leftmost &&
  //       idx <= rightmost
  //     }
  //   if the set has no empty regions, leftmost_empty equals max and rightmost_empty equals 0
  //   Otherwise (the region has empty regions):
  //     0 <= lefmost_empty < max and 0 <= rightmost_empty < max
  //     rightmost_empty >= leftmost_empty
  //     for every idx that is in the set and is empty {
  //       idx >= leftmost &&
  //       idx <= rightmost
  //     }
  void assert_bounds() NOT_DEBUG_RETURN;
};

class ShenandoahFreeSet : public CHeapObj<mtGC> {
private:
  ShenandoahHeap* const _heap;
  ShenandoahRegionPartitions _partitions;

  HeapWord* try_allocate_in(ShenandoahHeapRegion* region, ShenandoahAllocRequest& req, bool& in_new_region);

  // While holding the heap lock, allocate memory for a single object or LAB  which is to be entirely contained
  // within a single HeapRegion as characterized by req.
  //
  // Precondition: req.size() <= ShenandoahHeapRegion::humongous_threshold_words().
  HeapWord* allocate_single(ShenandoahAllocRequest& req, bool& in_new_region);

  // While holding the heap lock, allocate memory for a humongous object which will span multiple contiguous heap
  // regions.
  //
  // Precondition: req.size() > ShenandoahHeapRegion::humongous_threshold_words().
  HeapWord* allocate_contiguous(ShenandoahAllocRequest& req);

  void flip_to_gc(ShenandoahHeapRegion* r);
  void clear_internal();

  void try_recycle_trashed(ShenandoahHeapRegion *r);

  inline bool can_allocate_from(ShenandoahHeapRegion *r) const;
  inline bool can_allocate_from(size_t idx) const;

  inline bool has_alloc_capacity(ShenandoahHeapRegion *r) const;

  void find_regions_with_alloc_capacity(size_t &cset_regions);
  void reserve_regions(size_t to_reserve);

  void prepare_to_rebuild(size_t &cset_regions);
  void finish_rebuild(size_t cset_regions);

public:
  ShenandoahFreeSet(ShenandoahHeap* heap, size_t max_regions);

  // Public because ShenandoahRegionPartitions assertions require access.
  inline size_t alloc_capacity(ShenandoahHeapRegion *r) const;
  inline size_t alloc_capacity(size_t idx) const;

  void clear();
  void rebuild();

  // After we have finished evacuation, we no longer need to hold regions in reserve for the Collector.
  // Call this method at the start of update refs to make more memory available to the Mutator.  This
  // benefits workloads that do not allocate all of the evacuation waste reserve.
  //
  // Note that we plan to replenish the Collector reserve at the end of update refs, at which time all
  // of the regions recycled from the collection set will be available.
  void move_regions_from_collector_to_mutator_partition(size_t cset_regions);

  void recycle_trash();
  void log_status();

  inline size_t capacity()  const { return _partitions.capacity_of(Mutator); }
  inline size_t used()      const { return _partitions.used_by(Mutator);     }
  inline size_t available() const {
    assert(used() <= capacity(), "must use less than capacity");
    return capacity() - used();
  }

  HeapWord* allocate(ShenandoahAllocRequest& req, bool& in_new_region);
  size_t unsafe_peek_free() const;

  double internal_fragmentation();
  double external_fragmentation();

  void print_on(outputStream* out) const;
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHFREESET_HPP
