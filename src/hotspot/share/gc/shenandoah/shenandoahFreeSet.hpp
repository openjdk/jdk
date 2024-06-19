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
#include "gc/shenandoah/shenandoahSimpleBitMap.hpp"

// Each ShenandoahHeapRegion is associated with a ShenandoahFreeSetPartitionId.
enum class ShenandoahFreeSetPartitionId : uint8_t {
  Mutator,                      // Region is in the Mutator free set: available memory is available to mutators.
  Collector,                    // Region is in the Collector free set: available memory is reserved for evacuations.
  NotFree                       // Region is in no free set: it has no available memory
};

// We do not maintain counts, capacity, or used for regions that are not free.  Informally, if a region is NotFree, it is
// in no partition.  NumPartitions represents the size of an array that may be indexed by Mutator or Collector.
#define NumPartitions           (ShenandoahFreeSetPartitionId::NotFree)
#define IntNumPartitions     int(ShenandoahFreeSetPartitionId::NotFree)
#define UIntNumPartitions   uint(ShenandoahFreeSetPartitionId::NotFree)

// ShenandoahRegionPartitions provides an abstraction to help organize the implementation of ShenandoahFreeSet.  This
// class implements partitioning of regions into distinct sets.  Each ShenandoahHeapRegion is either in the Mutator free set,
// the Collector free set, or in neither free set (NotFree).  When we speak of a "free partition", we mean partitions that
// for which the ShenandoahFreeSetPartitionId is not equal to NotFree.
class ShenandoahRegionPartitions {

private:
  const ssize_t _max;           // The maximum number of heap regions
  const size_t _region_size_bytes;
  const ShenandoahFreeSet* _free_set;
  // For each partition, we maintain a bitmap of which regions are affiliated with his partition.
  ShenandoahSimpleBitMap _membership[UIntNumPartitions];

  // For each partition, we track an interval outside of which a region affiliated with that partition is guaranteed
  // not to be found. This makes searches for free space more efficient.  For each partition p, _leftmosts[p]
  // represents its least index, and its _rightmosts[p] its greatest index. Empty intervals are indicated by the
  // canonical [_max, -1].
  ssize_t _leftmosts[UIntNumPartitions];
  ssize_t _rightmosts[UIntNumPartitions];

  // Allocation for humongous objects needs to find regions that are entirely empty.  For each partion p, _leftmosts_empty[p]
  // represents the first region belonging to this partition that is completely empty and _rightmosts_empty[p] represents the
  // last region that is completely empty.  If there is no completely empty region in this partition, this is represented
  // by the canonical [_max, -1].
  ssize_t _leftmosts_empty[UIntNumPartitions];
  ssize_t _rightmosts_empty[UIntNumPartitions];

  // For each partition p, _capacity[p] represents the total amount of memory within the partition at the time
  // of the most recent rebuild, _used[p] represents the total amount of memory that has been allocated within this
  // partition (either already allocated as of the rebuild, or allocated since the rebuild).  _capacity[p] and _used[p]
  // are denoted in bytes.  Note that some regions that had been assigned to a particular partition at rebuild time
  // may have been retired following the rebuild.  The tallies for these regions are still reflected in _capacity[p]
  // and _used[p], even though the region may have been removed from the free set.
  size_t _capacity[UIntNumPartitions];
  size_t _used[UIntNumPartitions];
  size_t _region_counts[UIntNumPartitions];

  // Shrink the intervals associated with partition when region idx is removed from this free set
  inline void shrink_interval_if_boundary_modified(ShenandoahFreeSetPartitionId partition, ssize_t idx);

  // Shrink the intervals associated with partition when regions low_idx through high_idx inclusive are removed from this free set
  inline void shrink_interval_if_range_modifies_either_boundary(ShenandoahFreeSetPartitionId partition,
                                                                ssize_t low_idx, ssize_t high_idx);
  inline void expand_interval_if_boundary_modified(ShenandoahFreeSetPartitionId partition, ssize_t idx, size_t capacity);

#ifndef PRODUCT
  void dump_bitmap_row(ssize_t region_idx) const;
  void dump_bitmap_range(ssize_t start_region_idx, ssize_t end_region_idx) const;
  void dump_bitmap() const;
#endif
public:
  ShenandoahRegionPartitions(size_t max_regions, ShenandoahFreeSet* free_set);
  ~ShenandoahRegionPartitions() {}

  // Remove all regions from all partitions and reset all bounds
  void make_all_regions_unavailable();

  // Set the partition id for a particular region without adjusting interval bounds or usage/capacity tallies
  inline void raw_assign_membership(size_t idx, ShenandoahFreeSetPartitionId p) {
    _membership[int(p)].set_bit(idx);
  }

  // Set the Mutator intervals, usage, and capacity according to arguments.  Reset the Collector intervals, used, capacity
  // to represent empty Collector free set.  We use this at the end of rebuild_free_set() to avoid the overhead of making
  // many redundant incremental adjustments to the mutator intervals as the free set is being rebuilt.
  void establish_mutator_intervals(ssize_t mutator_leftmost, ssize_t mutator_rightmost,
                                   ssize_t mutator_leftmost_empty, ssize_t mutator_rightmost_empty,
                                   size_t mutator_region_count, size_t mutator_used);

  // Retire region idx from within partition, , leaving its capacity and used as part of the original free partition's totals.
  // Requires that region idx is in in the Mutator or Collector partitions.  Hereafter, identifies this region as NotFree.
  // Any remnant of available memory at the time of retirement is added to the original partition's total of used bytes.
  void retire_from_partition(ShenandoahFreeSetPartitionId p, ssize_t idx, size_t used_bytes);

  // Retire all regions between low_idx and high_idx inclusive from within partition.  Requires that each region idx is
  // in the same Mutator or Collector partition.  Hereafter, identifies each region as NotFree.   Assumes that each region
  // is now considered fully used, since the region is presumably used to represent a humongous object.
  void retire_range_from_partition(ShenandoahFreeSetPartitionId partition, ssize_t low_idx, ssize_t high_idx);

  // Place region idx into free set which_partition.  Requires that idx is currently NotFree.
  void make_free(ssize_t idx, ShenandoahFreeSetPartitionId which_partition, size_t region_capacity);

  // Place region idx into free partition new_partition, adjusting used and capacity totals for the original and new partition
  // given that available bytes can still be allocated within this region.  Requires that idx is currently not NotFree.
  void move_from_partition_to_partition(ssize_t idx, ShenandoahFreeSetPartitionId orig_partition,
                                        ShenandoahFreeSetPartitionId new_partition, size_t available);

  const char* partition_membership_name(ssize_t idx) const;

  // Return the index of the next available region >= start_index, or maximum_regions if not found.
  inline ssize_t find_index_of_next_available_region(ShenandoahFreeSetPartitionId which_partition, ssize_t start_index) const;

  // Return the index of the previous available region <= last_index, or -1 if not found.
  inline ssize_t find_index_of_previous_available_region(ShenandoahFreeSetPartitionId which_partition, ssize_t last_index) const;

  // Return the index of the next available cluster of cluster_size regions >= start_index, or maximum_regions if not found.
  inline ssize_t find_index_of_next_available_cluster_of_regions(ShenandoahFreeSetPartitionId which_partition,
                                                                 ssize_t start_index, size_t cluster_size) const;

  // Return the index of the previous available cluster of cluster_size regions <= last_index, or -1 if not found.
  inline ssize_t find_index_of_previous_available_cluster_of_regions(ShenandoahFreeSetPartitionId which_partition,
                                                                     ssize_t last_index, size_t cluster_size) const;

  inline bool in_free_set(ShenandoahFreeSetPartitionId which_partition, ssize_t idx) const {
    return _membership[int(which_partition)].is_set(idx);
  }

  // Returns the ShenandoahFreeSetPartitionId affiliation of region idx, NotFree if this region is not currently in any partition.
  // This does not enforce that free_set membership implies allocation capacity.
  inline ShenandoahFreeSetPartitionId membership(ssize_t idx) const;

#ifdef ASSERT
  // Returns true iff region idx's membership is which_partition.  If which_partition represents a free set, asserts
  // that the region has allocation capacity.
  inline bool partition_id_matches(ssize_t idx, ShenandoahFreeSetPartitionId which_partition) const;
#endif

  inline size_t max_regions() const { return _max; }

  inline size_t region_size_bytes() const { return _region_size_bytes; };

  // The following four methods return the left-most and right-most bounds on ranges of regions representing
  // the requested set.  The _empty variants represent bounds on the range that holds completely empty
  // regions, which are required for humongous allocations and desired for "very large" allocations.
  //   if the requested which_partition is empty:
  //     leftmost() and leftmost_empty() return _max, rightmost() and rightmost_empty() return 0
  //   otherwise, expect the following:
  //     0 <= leftmost <= leftmost_empty <= rightmost_empty <= rightmost < _max
  inline ssize_t leftmost(ShenandoahFreeSetPartitionId which_partition) const;
  inline ssize_t rightmost(ShenandoahFreeSetPartitionId which_partition) const;
  ssize_t leftmost_empty(ShenandoahFreeSetPartitionId which_partition);
  ssize_t rightmost_empty(ShenandoahFreeSetPartitionId which_partition);

  inline bool is_empty(ShenandoahFreeSetPartitionId which_partition) const;

  inline void increase_used(ShenandoahFreeSetPartitionId which_partition, size_t bytes);

  inline size_t capacity_of(ShenandoahFreeSetPartitionId which_partition) const {
    assert (which_partition < NumPartitions, "selected free set must be valid");
    return _capacity[int(which_partition)];
  }

  inline size_t used_by(ShenandoahFreeSetPartitionId which_partition) const {
    assert (which_partition < NumPartitions, "selected free set must be valid");
    return _used[int(which_partition)];
  }

  inline size_t available_in(ShenandoahFreeSetPartitionId which_partition) const {
    assert (which_partition < NumPartitions, "selected free set must be valid");
    return _capacity[int(which_partition)] - _used[int(which_partition)];
  }

  inline void set_capacity_of(ShenandoahFreeSetPartitionId which_partition, size_t value) {
    assert (which_partition < NumPartitions, "selected free set must be valid");
    _capacity[int(which_partition)] = value;
  }

  inline void set_used_by(ShenandoahFreeSetPartitionId which_partition, size_t value) {
    assert (which_partition < NumPartitions, "selected free set must be valid");
    _used[int(which_partition)] = value;
  }

  inline size_t count(ShenandoahFreeSetPartitionId which_partition) const { return _region_counts[int(which_partition)]; }

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
  //     0 <= leftmost_empty < max and 0 <= rightmost_empty < max
  //     rightmost_empty >= leftmost_empty
  //     for every idx that is in the set and is empty {
  //       idx >= leftmost &&
  //       idx <= rightmost
  //     }
  void assert_bounds() NOT_DEBUG_RETURN;
};

// Publicly, ShenandoahFreeSet represents memory that is available to mutator threads.  The public capacity(), used(),
// and available() methods represent this public notion of memory that is under control of the mutator.  Separately,
// ShenandoahFreeSet also represents memory available to garbage collection activities for compaction purposes.
//
// The Shenandoah garbage collector evacuates live objects out of specific regions that are identified as members of the
// collection set (cset).
//
// The ShenandoahFreeSet endeavors to congregrate survivor objects (objects that have been evacuated at least once) at the
// high end of memory.  New mutator allocations are taken from the low end of memory.  Within the mutator's range of regions,
// humongous allocations are taken from the lowest addresses, and LAB (local allocation buffers) and regular shared allocations
// are taken from the higher address of the mutator's range of regions.  This approach allows longer lasting survivor regions
// to congregate at the top of the heap and longer lasting humongous regions to congregate at the bottom of the heap, with
// short-lived frequently evacuated regions occupying the middle of the heap.
//
// Mutator and garbage collection activities tend to scramble the content of regions.  Twice, during each GC pass, we rebuild
// the free set in an effort to restore the efficient segregation of Collector and Mutator regions:
//
//  1. At the start of evacuation, we know exactly how much memory is going to be evacuated, and this guides our
//     sizing of the Collector free set.
//
//  2. At the end of GC, we have reclaimed all of the memory that was spanned by the cset.  We rebuild here to make
//     sure there is enough memory reserved at the high end of memory to hold the objects that might need to be evacuated
//     during the next GC pass.

class ShenandoahFreeSet : public CHeapObj<mtGC> {
private:
  ShenandoahHeap* const _heap;
  ShenandoahRegionPartitions _partitions;

  // Mutator allocations are biased from left-to-right or from right-to-left based on which end of mutator range
  // is most likely to hold partially used regions.  In general, we want to finish consuming partially used
  // regions and retire them in order to reduce the regions that must be searched for each allocation request.
  bool _right_to_left_bias;

  // We re-evaluate the left-to-right allocation bias whenever _alloc_bias_weight is less than zero.  Each time
  // we allocate an object, we decrement the count of this value.  Each time we re-evaluate whether to allocate
  // from right-to-left or left-to-right, we reset the value of this counter to _InitialAllocBiasWeight.
  ssize_t _alloc_bias_weight;

  const ssize_t _InitialAllocBiasWeight = 256;

  HeapWord* try_allocate_in(ShenandoahHeapRegion* region, ShenandoahAllocRequest& req, bool& in_new_region);

  // While holding the heap lock, allocate memory for a single object or LAB  which is to be entirely contained
  // within a single HeapRegion as characterized by req.
  //
  // Precondition: req.size() <= ShenandoahHeapRegion::humongous_threshold_words().
  HeapWord* allocate_single(ShenandoahAllocRequest& req, bool& in_new_region);

  // While holding the heap lock, allocate memory for a humongous object which spans one or more regions that
  // were previously empty.  Regions that represent humongous objects are entirely dedicated to the humongous
  // object.  No other objects are packed into these regions.
  //
  // Precondition: req.size() > ShenandoahHeapRegion::humongous_threshold_words().
  HeapWord* allocate_contiguous(ShenandoahAllocRequest& req);

  // Change region r from the Mutator partition to the GC's Collector partition.  This requires that the region is entirely empty.
  // Typical usage: During evacuation, the GC may find it needs more memory than had been reserved at the start of evacuation to
  // hold evacuated objects.  If this occurs and memory is still available in the Mutator's free set, we will flip a region from
  // the Mutator free set into the Collector free set.
  void flip_to_gc(ShenandoahHeapRegion* r);
  void clear_internal();
  void try_recycle_trashed(ShenandoahHeapRegion *r);

  // Returns true iff this region is entirely available, either because it is empty() or because it has been found to represent
  // immediate trash and we'll be able to immediately recycle it.  Note that we cannot recycle immediate trash if
  // concurrent weak root processing is in progress.
  inline bool can_allocate_from(ShenandoahHeapRegion *r) const;
  inline bool can_allocate_from(size_t idx) const;

  inline bool has_alloc_capacity(ShenandoahHeapRegion *r) const;

  // This function places all regions that have allocation capacity into the mutator_partition, identifying regions
  // that have no allocation capacity as NotFree.  Subsequently, we will move some of the mutator regions into the
  // collector partition with the intent of packing collector memory into the highest (rightmost) addresses of the
  // heap, with mutator memory consuming the lowest addresses of the heap.
  void find_regions_with_alloc_capacity(size_t &cset_regions);

  // Having placed all regions that have allocation capacity into the mutator partition, move some of these regions from
  // the mutator partition into the collector partition in order to assure that the memory available for allocations within
  // the collector partition is at least to_reserve.
  void reserve_regions(size_t to_reserve);

  // Overwrite arguments to represent the number of regions to be reclaimed from the cset
  void prepare_to_rebuild(size_t &cset_regions);

  void finish_rebuild(size_t cset_regions);

public:
  ShenandoahFreeSet(ShenandoahHeap* heap, size_t max_regions);

  // Public because ShenandoahRegionPartitions assertions require access.
  inline size_t alloc_capacity(ShenandoahHeapRegion *r) const;
  inline size_t alloc_capacity(size_t idx) const;

  void clear();
  void rebuild();

  // Move up to cset_regions number of regions from being available to the collector to being available to the mutator.
  //
  // Typical usage: At the end of evacuation, when the collector no longer needs the regions that had been reserved
  // for evacuation, invoke this to make regions available for mutator allocations.
  //
  // Note that we plan to replenish the Collector reserve at the end of update refs, at which time all
  // of the regions recycled from the collection set will be available.  If the very unlikely event that there
  // are fewer regions in the collection set than remain in the collector set, we limit the transfer in order
  // to assure that the replenished Collector reserve can be sufficiently large.
  void move_regions_from_collector_to_mutator(size_t cset_regions);

  void recycle_trash();
  void log_status();

  inline size_t capacity()  const { return _partitions.capacity_of(ShenandoahFreeSetPartitionId::Mutator); }
  inline size_t used()      const { return _partitions.used_by(ShenandoahFreeSetPartitionId::Mutator);     }
  inline size_t available() const {
    assert(used() <= capacity(), "must use less than capacity");
    return capacity() - used();
  }

  HeapWord* allocate(ShenandoahAllocRequest& req, bool& in_new_region);
  size_t unsafe_peek_free() const;

  /*
   * Internal fragmentation metric: describes how fragmented the heap regions are.
   *
   * It is derived as:
   *
   *               sum(used[i]^2, i=0..k)
   *   IF = 1 - ------------------------------
   *              C * sum(used[i], i=0..k)
   *
   * ...where k is the number of regions in computation, C is the region capacity, and
   * used[i] is the used space in the region.
   *
   * The non-linearity causes IF to be lower for the cases where the same total heap
   * used is densely packed. For example:
   *   a) Heap is completely full  => IF = 0
   *   b) Heap is half full, first 50% regions are completely full => IF = 0
   *   c) Heap is half full, each region is 50% full => IF = 1/2
   *   d) Heap is quarter full, first 50% regions are completely full => IF = 0
   *   e) Heap is quarter full, each region is 25% full => IF = 3/4
   *   f) Heap has one small object per each region => IF =~ 1
   */
  double internal_fragmentation();

  /*
   * External fragmentation metric: describes how fragmented the heap is.
   *
   * It is derived as:
   *
   *   EF = 1 - largest_contiguous_free / total_free
   *
   * For example:
   *   a) Heap is completely empty => EF = 0
   *   b) Heap is completely full => EF = 0
   *   c) Heap is first-half full => EF = 1/2
   *   d) Heap is half full, full and empty regions interleave => EF =~ 1
   */
  double external_fragmentation();

  void print_on(outputStream* out) const;
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHFREESET_HPP
