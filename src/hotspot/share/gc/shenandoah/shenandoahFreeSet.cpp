/*
 * Copyright (c) 2016, 2021, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/tlab_globals.hpp"
#include "gc/shenandoah/shenandoahAffiliation.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegionSet.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.inline.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahSimpleBitMap.inline.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"
#include "logging/logStream.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/orderAccess.hpp"

static const char* partition_name(ShenandoahFreeSetPartitionId t) {
  switch (t) {
    case ShenandoahFreeSetPartitionId::NotFree: return "NotFree";
    case ShenandoahFreeSetPartitionId::Mutator: return "Mutator";
    case ShenandoahFreeSetPartitionId::Collector: return "Collector";
    case ShenandoahFreeSetPartitionId::OldCollector: return "OldCollector";
    default:
      ShouldNotReachHere();
      return "Unrecognized";
  }
}

class ShenandoahLeftRightIterator {
private:
  idx_t _idx;
  idx_t _end;
  ShenandoahRegionPartitions* _partitions;
  ShenandoahFreeSetPartitionId _partition;
public:
  explicit ShenandoahLeftRightIterator(ShenandoahRegionPartitions* partitions,
                                       ShenandoahFreeSetPartitionId partition, bool use_empty = false)
    : _idx(0), _end(0), _partitions(partitions), _partition(partition) {
    _idx = use_empty ? _partitions->leftmost_empty(_partition) : _partitions->leftmost(_partition);
    _end = use_empty ? _partitions->rightmost_empty(_partition) : _partitions->rightmost(_partition);
  }

  bool has_next() const {
    if (_idx <= _end) {
      assert(_partitions->in_free_set(_partition, _idx), "Boundaries or find_last_set_bit failed: %zd", _idx);
      return true;
    }
    return false;
  }

  idx_t current() const {
    return _idx;
  }

  idx_t next() {
    _idx = _partitions->find_index_of_next_available_region(_partition, _idx + 1);
    return current();
  }
};

class ShenandoahRightLeftIterator {
private:
  idx_t _idx;
  idx_t _end;
  ShenandoahRegionPartitions* _partitions;
  ShenandoahFreeSetPartitionId _partition;
public:
  explicit ShenandoahRightLeftIterator(ShenandoahRegionPartitions* partitions,
                                       ShenandoahFreeSetPartitionId partition, bool use_empty = false)
    : _idx(0), _end(0), _partitions(partitions), _partition(partition) {
    _idx = use_empty ? _partitions->rightmost_empty(_partition) : _partitions->rightmost(_partition);
    _end = use_empty ? _partitions->leftmost_empty(_partition) : _partitions->leftmost(_partition);
  }

  bool has_next() const {
    if (_idx >= _end) {
      assert(_partitions->in_free_set(_partition, _idx), "Boundaries or find_last_set_bit failed: %zd", _idx);
      return true;
    }
    return false;
  }

  idx_t current() const {
    return _idx;
  }

  idx_t next() {
    _idx = _partitions->find_index_of_previous_available_region(_partition, _idx - 1);
    return current();
  }
};

#ifndef PRODUCT
void ShenandoahRegionPartitions::dump_bitmap() const {
  log_debug(gc)("Mutator range [%zd, %zd], Collector range [%zd, %zd"
               "], Old Collector range [%zd, %zd]",
               _leftmosts[int(ShenandoahFreeSetPartitionId::Mutator)],
               _rightmosts[int(ShenandoahFreeSetPartitionId::Mutator)],
               _leftmosts[int(ShenandoahFreeSetPartitionId::Collector)],
               _rightmosts[int(ShenandoahFreeSetPartitionId::Collector)],
               _leftmosts[int(ShenandoahFreeSetPartitionId::OldCollector)],
               _rightmosts[int(ShenandoahFreeSetPartitionId::OldCollector)]);
  log_debug(gc)("Empty Mutator range [%zd, %zd"
               "], Empty Collector range [%zd, %zd"
               "], Empty Old Collecto range [%zd, %zd]",
               _leftmosts_empty[int(ShenandoahFreeSetPartitionId::Mutator)],
               _rightmosts_empty[int(ShenandoahFreeSetPartitionId::Mutator)],
               _leftmosts_empty[int(ShenandoahFreeSetPartitionId::Collector)],
               _rightmosts_empty[int(ShenandoahFreeSetPartitionId::Collector)],
               _leftmosts_empty[int(ShenandoahFreeSetPartitionId::OldCollector)],
               _rightmosts_empty[int(ShenandoahFreeSetPartitionId::OldCollector)]);

  log_debug(gc)("%6s: %18s %18s %18s %18s", "index", "Mutator Bits", "Collector Bits", "Old Collector Bits", "NotFree Bits");
  dump_bitmap_range(0, _max-1);
}

void ShenandoahRegionPartitions::dump_bitmap_range(idx_t start_region_idx, idx_t end_region_idx) const {
  assert((start_region_idx >= 0) && (start_region_idx < (idx_t) _max), "precondition");
  assert((end_region_idx >= 0) && (end_region_idx < (idx_t) _max), "precondition");
  idx_t aligned_start = _membership[int(ShenandoahFreeSetPartitionId::Mutator)].aligned_index(start_region_idx);
  idx_t aligned_end = _membership[int(ShenandoahFreeSetPartitionId::Mutator)].aligned_index(end_region_idx);
  idx_t alignment = _membership[int(ShenandoahFreeSetPartitionId::Mutator)].alignment();
  while (aligned_start <= aligned_end) {
    dump_bitmap_row(aligned_start);
    aligned_start += alignment;
  }
}

void ShenandoahRegionPartitions::dump_bitmap_row(idx_t region_idx) const {
  assert((region_idx >= 0) && (region_idx < (idx_t) _max), "precondition");
  idx_t aligned_idx = _membership[int(ShenandoahFreeSetPartitionId::Mutator)].aligned_index(region_idx);
  uintx mutator_bits = _membership[int(ShenandoahFreeSetPartitionId::Mutator)].bits_at(aligned_idx);
  uintx collector_bits = _membership[int(ShenandoahFreeSetPartitionId::Collector)].bits_at(aligned_idx);
  uintx old_collector_bits = _membership[int(ShenandoahFreeSetPartitionId::OldCollector)].bits_at(aligned_idx);
  uintx free_bits = mutator_bits | collector_bits | old_collector_bits;
  uintx notfree_bits =  ~free_bits;
  log_debug(gc)("%6zd : " SIZE_FORMAT_X_0 " 0x" SIZE_FORMAT_X_0 " 0x" SIZE_FORMAT_X_0 " 0x" SIZE_FORMAT_X_0,
               aligned_idx, mutator_bits, collector_bits, old_collector_bits, notfree_bits);
}
#endif

ShenandoahRegionPartitions::ShenandoahRegionPartitions(size_t max_regions, ShenandoahFreeSet* free_set) :
    _max(max_regions),
    _region_size_bytes(ShenandoahHeapRegion::region_size_bytes()),
    _free_set(free_set),
    _membership{ ShenandoahSimpleBitMap(max_regions), ShenandoahSimpleBitMap(max_regions) , ShenandoahSimpleBitMap(max_regions) }
{
  initialize_old_collector();
  make_all_regions_unavailable();
}

void ShenandoahFreeSet::account_for_pip_regions(size_t mutator_regions, size_t mutator_bytes,
                                                size_t collector_regions, size_t collector_bytes) {
  shenandoah_assert_heaplocked();

  // We have removed all of these regions from their respective partition. Each pip region is "in" the NotFree partition.
  // We want to account for all pip pad memory as if it had been consumed from within the Mutator partition.
  //
  // After we finish promote in place, the pad memory will be deallocated and made available within the OldCollector
  // region.  At that time, we will transfer the used memory from the Mutator partition to the OldCollector parttion,
  // and then we will unallocate the pad memory.


  _partitions.decrease_region_counts(ShenandoahFreeSetPartitionId::Mutator, mutator_regions);
  _partitions.decrease_region_counts(ShenandoahFreeSetPartitionId::Collector, collector_regions);

  // Increase used by remnant fill objects placed in both Mutator and Collector partitions
  _partitions.increase_used(ShenandoahFreeSetPartitionId::Mutator, mutator_bytes);
  _partitions.increase_used(ShenandoahFreeSetPartitionId::Collector, collector_bytes);

  // Now transfer all of the memory contained within Collector pip regions from the Collector to the Mutator.
  // Each of these regions is treated as fully used, even though some of the region's memory may be artifically used,
  // to be recycled and put into allocatable OldCollector partition after the region has been promoted in place.
  _partitions.transfer_used_capacity_from_to(ShenandoahFreeSetPartitionId::Collector, ShenandoahFreeSetPartitionId::Mutator,
                                             collector_regions);

  // Conservatively, act as if we've promoted from both Mutator and Collector partitions
  recompute_total_affiliated</* MutatorEmptiesChanged */ false, /* CollectorEmptiesChanged */ false,
                             /* OldCollectorEmptiesChanged */ false, /* MutatorSizeChanged */ true,
                             /* CollectorSizeChanged */ true, /* OldCollectorSizeChanged */ false,
                             /* AffiliatedChangesAreYoungNeutral */ true, /* AffiliatedChangesAreGlobalNeutral */ true,
                             /* UnaffiliatedChangesAreYoungNeutral */ false>();
  recompute_total_young_used</* UsedByMutatorChanged */ true, /*UsedByCollectorChanged */ true>();
  recompute_total_global_used</* UsedByMutatorChanged */ true, /* UsedByCollectorChanged */ true,
                              /* UsedByOldCollectorChanged */ false>();
}

ShenandoahFreeSetPartitionId ShenandoahFreeSet::prepare_to_promote_in_place(size_t idx, size_t bytes) {
  shenandoah_assert_heaplocked();
  size_t min_remnant_size = PLAB::min_size() * HeapWordSize;
  ShenandoahFreeSetPartitionId p =  _partitions.membership(idx);
  if (bytes >= min_remnant_size) {
    assert((p == ShenandoahFreeSetPartitionId::Mutator) || (p == ShenandoahFreeSetPartitionId::Collector),
           "PIP region must be associated with young");
    _partitions.raw_clear_membership(idx, p);
  } else {
    assert(p == ShenandoahFreeSetPartitionId::NotFree, "We did not fill this region and do not need to adjust used");
  }
  return p;
}

inline bool ShenandoahFreeSet::can_allocate_from(ShenandoahHeapRegion *r) const {
  return r->is_empty() || (r->is_trash() && !_heap->is_concurrent_weak_root_in_progress());
}

inline bool ShenandoahFreeSet::can_allocate_from(size_t idx) const {
  ShenandoahHeapRegion* r = _heap->get_region(idx);
  return can_allocate_from(r);
}

inline size_t ShenandoahFreeSet::alloc_capacity(ShenandoahHeapRegion *r) const {
  if (r->is_trash()) {
    // This would be recycled on allocation path
    return ShenandoahHeapRegion::region_size_bytes();
  } else {
    return r->free();
  }
}

inline size_t ShenandoahFreeSet::alloc_capacity(size_t idx) const {
  ShenandoahHeapRegion* r = _heap->get_region(idx);
  return alloc_capacity(r);
}

inline bool ShenandoahFreeSet::has_alloc_capacity(ShenandoahHeapRegion *r) const {
  return alloc_capacity(r) > 0;
}

// This is used for unit testing.  Do not use in production code.
void ShenandoahFreeSet::resize_old_collector_capacity(size_t regions) {
  shenandoah_assert_heaplocked();
  size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();
  size_t original_old_regions = _partitions.get_capacity(ShenandoahFreeSetPartitionId::OldCollector) / region_size_bytes;
  size_t unaffiliated_mutator_regions = _partitions.get_empty_region_counts(ShenandoahFreeSetPartitionId::Mutator);
  size_t unaffiliated_collector_regions = _partitions.get_empty_region_counts(ShenandoahFreeSetPartitionId::Collector);
  size_t unaffiliated_old_collector_regions = _partitions.get_empty_region_counts(ShenandoahFreeSetPartitionId::OldCollector);
  if (regions > original_old_regions) {
    size_t regions_to_transfer = regions - original_old_regions;
    if (regions_to_transfer <= unaffiliated_mutator_regions + unaffiliated_collector_regions) {
      size_t regions_from_mutator =
        (regions_to_transfer > unaffiliated_mutator_regions)? unaffiliated_mutator_regions: regions_to_transfer;
      regions_to_transfer -= regions_from_mutator;
      size_t regions_from_collector = regions_to_transfer;
      if (regions_from_mutator > 0) {
        transfer_empty_regions_from_to(ShenandoahFreeSetPartitionId::Mutator, ShenandoahFreeSetPartitionId::OldCollector,
                                       regions_from_mutator);
      }
      if (regions_from_collector > 0) {
        transfer_empty_regions_from_to(ShenandoahFreeSetPartitionId::Collector, ShenandoahFreeSetPartitionId::OldCollector,
                                       regions_from_mutator);
      }
    } else {
      fatal("Could not resize old for unit test");
    }
  } else if (regions < original_old_regions) {
    size_t regions_to_transfer = original_old_regions - regions;
    if (regions_to_transfer <= unaffiliated_old_collector_regions) {
      transfer_empty_regions_from_to(ShenandoahFreeSetPartitionId::OldCollector, ShenandoahFreeSetPartitionId::Mutator,
                                     regions_to_transfer);
    } else {
      fatal("Could not resize old for unit test");
    }
  }
  // else, old generation is already appropriately sized
}

void ShenandoahFreeSet::reset_bytes_allocated_since_gc_start(size_t initial_bytes_allocated) {
  shenandoah_assert_heaplocked();
  _mutator_bytes_allocated_since_gc_start = initial_bytes_allocated;
}

void ShenandoahFreeSet::increase_bytes_allocated(size_t bytes) {
  shenandoah_assert_heaplocked();
  _mutator_bytes_allocated_since_gc_start += bytes;
}

inline idx_t ShenandoahRegionPartitions::leftmost(ShenandoahFreeSetPartitionId which_partition) const {
  assert (which_partition < NumPartitions, "selected free partition must be valid");
  idx_t idx = _leftmosts[int(which_partition)];
  if (idx >= _max) {
    return _max;
  } else {
    // Cannot assert that membership[which_partition.is_set(idx) because this helper method may be used
    // to query the original value of leftmost when leftmost must be adjusted because the interval representing
    // which_partition is shrinking after the region that used to be leftmost is retired.
    return idx;
  }
}

inline idx_t ShenandoahRegionPartitions::rightmost(ShenandoahFreeSetPartitionId which_partition) const {
  assert (which_partition < NumPartitions, "selected free partition must be valid");
  idx_t idx = _rightmosts[int(which_partition)];
  // Cannot assert that membership[which_partition.is_set(idx) because this helper method may be used
  // to query the original value of leftmost when leftmost must be adjusted because the interval representing
  // which_partition is shrinking after the region that used to be leftmost is retired.
  return idx;
}

void ShenandoahRegionPartitions::initialize_old_collector() {
  _capacity[int(ShenandoahFreeSetPartitionId::OldCollector)] = 0;
  _region_counts[int(ShenandoahFreeSetPartitionId::OldCollector)] = 0;
  _empty_region_counts[int(ShenandoahFreeSetPartitionId::OldCollector)] = 0;
}

void ShenandoahRegionPartitions::make_all_regions_unavailable() {
  shenandoah_assert_heaplocked_or_safepoint();
  for (size_t partition_id = 0; partition_id < IntNumPartitions; partition_id++) {
    _membership[partition_id].clear_all();
    _leftmosts[partition_id] = _max;
    _rightmosts[partition_id] = -1;
    _leftmosts_empty[partition_id] = _max;
    _rightmosts_empty[partition_id] = -1;;
    _capacity[partition_id] = 0;
    _region_counts[partition_id] = 0;
    _empty_region_counts[partition_id] = 0;
    _used[partition_id] = 0;
    _humongous_waste[partition_id] = 0;
    _available[partition_id] = 0;
  }
}

void ShenandoahRegionPartitions::establish_mutator_intervals(idx_t mutator_leftmost, idx_t mutator_rightmost,
                                                             idx_t mutator_leftmost_empty, idx_t mutator_rightmost_empty,
                                                             size_t total_mutator_regions, size_t empty_mutator_regions,
                                                             size_t mutator_region_count, size_t mutator_used,
                                                             size_t mutator_humongous_waste_bytes) {
  shenandoah_assert_heaplocked();

  _leftmosts[int(ShenandoahFreeSetPartitionId::Mutator)] = mutator_leftmost;
  _rightmosts[int(ShenandoahFreeSetPartitionId::Mutator)] = mutator_rightmost;
  _leftmosts_empty[int(ShenandoahFreeSetPartitionId::Mutator)] = mutator_leftmost_empty;
  _rightmosts_empty[int(ShenandoahFreeSetPartitionId::Mutator)] = mutator_rightmost_empty;

  _region_counts[int(ShenandoahFreeSetPartitionId::Mutator)] = mutator_region_count;
  _used[int(ShenandoahFreeSetPartitionId::Mutator)] = mutator_used;
  _capacity[int(ShenandoahFreeSetPartitionId::Mutator)] = total_mutator_regions * _region_size_bytes;
  _humongous_waste[int(ShenandoahFreeSetPartitionId::Mutator)] = mutator_humongous_waste_bytes;
  _available[int(ShenandoahFreeSetPartitionId::Mutator)] =
    _capacity[int(ShenandoahFreeSetPartitionId::Mutator)] - _used[int(ShenandoahFreeSetPartitionId::Mutator)];

  _empty_region_counts[int(ShenandoahFreeSetPartitionId::Mutator)] = empty_mutator_regions;

  _leftmosts[int(ShenandoahFreeSetPartitionId::Collector)] = _max;
  _rightmosts[int(ShenandoahFreeSetPartitionId::Collector)] = -1;
  _leftmosts_empty[int(ShenandoahFreeSetPartitionId::Collector)] = _max;
  _rightmosts_empty[int(ShenandoahFreeSetPartitionId::Collector)] = -1;

  _region_counts[int(ShenandoahFreeSetPartitionId::Collector)] = 0;
  _used[int(ShenandoahFreeSetPartitionId::Collector)] = 0;
  _capacity[int(ShenandoahFreeSetPartitionId::Collector)] = 0;
  _humongous_waste[int(ShenandoahFreeSetPartitionId::Collector)] = 0;
  _available[int(ShenandoahFreeSetPartitionId::Collector)] = 0;

  _empty_region_counts[int(ShenandoahFreeSetPartitionId::Collector)] = 0;
}

void ShenandoahRegionPartitions::establish_old_collector_intervals(idx_t old_collector_leftmost,
                                                                   idx_t old_collector_rightmost,
                                                                   idx_t old_collector_leftmost_empty,
                                                                   idx_t old_collector_rightmost_empty,
                                                                   size_t total_old_collector_region_count,
                                                                   size_t old_collector_empty, size_t old_collector_regions,
                                                                   size_t old_collector_used,
                                                                   size_t old_collector_humongous_waste_bytes) {
  shenandoah_assert_heaplocked();

  _leftmosts[int(ShenandoahFreeSetPartitionId::OldCollector)] = old_collector_leftmost;
  _rightmosts[int(ShenandoahFreeSetPartitionId::OldCollector)] = old_collector_rightmost;
  _leftmosts_empty[int(ShenandoahFreeSetPartitionId::OldCollector)] = old_collector_leftmost_empty;
  _rightmosts_empty[int(ShenandoahFreeSetPartitionId::OldCollector)] = old_collector_rightmost_empty;

  _region_counts[int(ShenandoahFreeSetPartitionId::OldCollector)] = old_collector_regions;
  _used[int(ShenandoahFreeSetPartitionId::OldCollector)] = old_collector_used;
  _capacity[int(ShenandoahFreeSetPartitionId::OldCollector)] = total_old_collector_region_count * _region_size_bytes;
  _humongous_waste[int(ShenandoahFreeSetPartitionId::OldCollector)] = old_collector_humongous_waste_bytes;
  _available[int(ShenandoahFreeSetPartitionId::OldCollector)] =
    _capacity[int(ShenandoahFreeSetPartitionId::OldCollector)] - _used[int(ShenandoahFreeSetPartitionId::OldCollector)];

  _empty_region_counts[int(ShenandoahFreeSetPartitionId::OldCollector)] = old_collector_empty;
}

void ShenandoahRegionPartitions::increase_used(ShenandoahFreeSetPartitionId which_partition, size_t bytes) {
  shenandoah_assert_heaplocked();
  assert (which_partition < NumPartitions, "Partition must be valid");

  _used[int(which_partition)] += bytes;
  _available[int(which_partition)] -= bytes;
  assert (_used[int(which_partition)] <= _capacity[int(which_partition)],
          "Must not use (%zu) more than capacity (%zu) after increase by %zu",
          _used[int(which_partition)], _capacity[int(which_partition)], bytes);
}

void ShenandoahRegionPartitions::decrease_used(ShenandoahFreeSetPartitionId which_partition, size_t bytes) {
  shenandoah_assert_heaplocked();
  assert (which_partition < NumPartitions, "Partition must be valid");
  assert (_used[int(which_partition)] >= bytes, "Must not use less than zero after decrease");
  _used[int(which_partition)] -= bytes;
  _available[int(which_partition)] += bytes;
}

void ShenandoahRegionPartitions::increase_humongous_waste(ShenandoahFreeSetPartitionId which_partition, size_t bytes) {
  shenandoah_assert_heaplocked();
  assert (which_partition < NumPartitions, "Partition must be valid");
  _humongous_waste[int(which_partition)] += bytes;
}

size_t ShenandoahRegionPartitions::get_humongous_waste(ShenandoahFreeSetPartitionId which_partition) {
  assert (which_partition < NumPartitions, "Partition must be valid");
  return _humongous_waste[int(which_partition)];;
}

void ShenandoahRegionPartitions::set_capacity_of(ShenandoahFreeSetPartitionId which_partition, size_t value) {
  shenandoah_assert_heaplocked();
  assert (which_partition < NumPartitions, "selected free set must be valid");
  _capacity[int(which_partition)] = value;
  _available[int(which_partition)] = value - _used[int(which_partition)];
}

void ShenandoahRegionPartitions::set_used_by(ShenandoahFreeSetPartitionId which_partition, size_t value) {
  shenandoah_assert_heaplocked();
  assert (which_partition < NumPartitions, "selected free set must be valid");
  _used[int(which_partition)] = value;
  _available[int(which_partition)] = _capacity[int(which_partition)] - value;
}


void ShenandoahRegionPartitions::increase_capacity(ShenandoahFreeSetPartitionId which_partition, size_t bytes) {
  shenandoah_assert_heaplocked();
  assert (which_partition < NumPartitions, "Partition must be valid");
  _capacity[int(which_partition)] += bytes;
  _available[int(which_partition)] += bytes;
}

void ShenandoahRegionPartitions::transfer_used_capacity_from_to(ShenandoahFreeSetPartitionId from_partition,
                                                                ShenandoahFreeSetPartitionId to_partition, size_t regions) {
  shenandoah_assert_heaplocked();
  size_t bytes = regions * ShenandoahHeapRegion::region_size_bytes();
  assert (from_partition < NumPartitions, "Partition must be valid");
  assert (to_partition < NumPartitions, "Partition must be valid");
  assert(_capacity[int(from_partition)] >= bytes, "Cannot remove more capacity bytes than are present");
  assert(_used[int(from_partition)] >= bytes, "Cannot transfer used bytes that are not used");

  // available is unaffected by transfer
  _capacity[int(from_partition)] -= bytes;
  _used[int(from_partition)] -= bytes;
  _capacity[int(to_partition)] += bytes;
  _used[int(to_partition)] += bytes;
}

void ShenandoahRegionPartitions::decrease_capacity(ShenandoahFreeSetPartitionId which_partition, size_t bytes) {
  shenandoah_assert_heaplocked();
  assert (which_partition < NumPartitions, "Partition must be valid");
  assert(_capacity[int(which_partition)] >= bytes, "Cannot remove more capacity bytes than are present");
  assert(_available[int(which_partition)] >= bytes, "Cannot shrink capacity unless capacity is unused");
  _capacity[int(which_partition)] -= bytes;
  _available[int(which_partition)] -= bytes;
}

void ShenandoahRegionPartitions::increase_available(ShenandoahFreeSetPartitionId which_partition, size_t bytes) {
  shenandoah_assert_heaplocked();
  assert (which_partition < NumPartitions, "Partition must be valid");
  _available[int(which_partition)] += bytes;
}

void ShenandoahRegionPartitions::decrease_available(ShenandoahFreeSetPartitionId which_partition, size_t bytes) {
  shenandoah_assert_heaplocked();
  assert (which_partition < NumPartitions, "Partition must be valid");
  assert(_available[int(which_partition)] >= bytes, "Cannot remove more available bytes than are present");
  _available[int(which_partition)] -= bytes;
}

size_t ShenandoahRegionPartitions::get_available(ShenandoahFreeSetPartitionId which_partition) {
  assert (which_partition < NumPartitions, "Partition must be valid");
  return _available[int(which_partition)];;
}

void ShenandoahRegionPartitions::increase_region_counts(ShenandoahFreeSetPartitionId which_partition, size_t regions) {
  _region_counts[int(which_partition)] += regions;
}

void ShenandoahRegionPartitions::decrease_region_counts(ShenandoahFreeSetPartitionId which_partition, size_t regions) {
  assert(_region_counts[int(which_partition)] >= regions, "Cannot remove more regions than are present");
  _region_counts[int(which_partition)] -= regions;
}

void ShenandoahRegionPartitions::increase_empty_region_counts(ShenandoahFreeSetPartitionId which_partition, size_t regions) {
  _empty_region_counts[int(which_partition)] += regions;
}

void ShenandoahRegionPartitions::decrease_empty_region_counts(ShenandoahFreeSetPartitionId which_partition, size_t regions) {
  assert(_empty_region_counts[int(which_partition)] >= regions, "Cannot remove more regions than are present");
  _empty_region_counts[int(which_partition)] -= regions;
}

void ShenandoahRegionPartitions::one_region_is_no_longer_empty(ShenandoahFreeSetPartitionId partition) {
  decrease_empty_region_counts(partition, (size_t) 1);
}

// All members of partition between low_idx and high_idx inclusive have been removed.
void ShenandoahRegionPartitions::shrink_interval_if_range_modifies_either_boundary(
  ShenandoahFreeSetPartitionId partition, idx_t low_idx, idx_t high_idx, size_t num_regions) {
  assert((low_idx <= high_idx) && (low_idx >= 0) && (high_idx < _max), "Range must span legal index values");
  size_t span = high_idx + 1 - low_idx;
  bool regions_are_contiguous = (span == num_regions);
  if (low_idx == leftmost(partition)) {
    assert (!_membership[int(partition)].is_set(low_idx), "Do not shrink interval if region not removed");
    if (high_idx + 1 == _max) {
      if (regions_are_contiguous) {
        _leftmosts[int(partition)] = _max;
      } else {
        _leftmosts[int(partition)] = find_index_of_next_available_region(partition, low_idx + 1);
      }
    } else {
      if (regions_are_contiguous) {
        _leftmosts[int(partition)] = find_index_of_next_available_region(partition, high_idx + 1);
      } else {
        _leftmosts[int(partition)] = find_index_of_next_available_region(partition, low_idx + 1);
      }
    }
    if (_leftmosts_empty[int(partition)] < _leftmosts[int(partition)]) {
      // This gets us closer to where we need to be; we'll scan further when leftmosts_empty is requested.
      _leftmosts_empty[int(partition)] = _leftmosts[int(partition)];
    }
  }
  if (high_idx == _rightmosts[int(partition)]) {
    assert (!_membership[int(partition)].is_set(high_idx), "Do not shrink interval if region not removed");
    if (low_idx == 0) {
      if (regions_are_contiguous) {
        _rightmosts[int(partition)] = -1;
      } else {
        _rightmosts[int(partition)] = find_index_of_previous_available_region(partition, high_idx - 1);
      }
    } else {
      if (regions_are_contiguous) {
        _rightmosts[int(partition)] = find_index_of_previous_available_region(partition, low_idx - 1);
      } else {
        _rightmosts[int(partition)] = find_index_of_previous_available_region(partition, high_idx - 1);
      }
    }
    if (_rightmosts_empty[int(partition)] > _rightmosts[int(partition)]) {
      // This gets us closer to where we need to be; we'll scan further when rightmosts_empty is requested.
      _rightmosts_empty[int(partition)] = _rightmosts[int(partition)];
    }
  }
  if (_leftmosts[int(partition)] > _rightmosts[int(partition)]) {
    _leftmosts[int(partition)] = _max;
    _rightmosts[int(partition)] = -1;
    _leftmosts_empty[int(partition)] = _max;
    _rightmosts_empty[int(partition)] = -1;
  }
}

void ShenandoahRegionPartitions::establish_interval(ShenandoahFreeSetPartitionId partition, idx_t low_idx,
                                                    idx_t high_idx, idx_t low_empty_idx, idx_t high_empty_idx) {
#ifdef ASSERT
  assert (partition < NumPartitions, "invalid partition");
  if (low_idx != max()) {
    assert((low_idx <= high_idx) && (low_idx >= 0) && (high_idx < _max), "Range must span legal index values");
    assert (in_free_set(partition, low_idx), "Must be in partition of established interval");
    assert (in_free_set(partition, high_idx), "Must be in partition of established interval");
  }
  if (low_empty_idx != max()) {
    ShenandoahHeapRegion* r = ShenandoahHeap::heap()->get_region(low_empty_idx);
    assert (in_free_set(partition, low_empty_idx) && (r->is_trash() || r->free() == _region_size_bytes),
            "Must be empty and in partition of established interval");
    r = ShenandoahHeap::heap()->get_region(high_empty_idx);
    assert (in_free_set(partition, high_empty_idx), "Must be in partition of established interval");
  }
#endif

  _leftmosts[int(partition)] = low_idx;
  _rightmosts[int(partition)] = high_idx;
  _leftmosts_empty[int(partition)] = low_empty_idx;
  _rightmosts_empty[int(partition)] = high_empty_idx;
}

inline void ShenandoahRegionPartitions::shrink_interval_if_boundary_modified(ShenandoahFreeSetPartitionId partition,
                                                                             idx_t idx) {
  shrink_interval_if_range_modifies_either_boundary(partition, idx, idx, 1);
}

// Some members of partition between low_idx and high_idx inclusive have been added.
void ShenandoahRegionPartitions::
expand_interval_if_range_modifies_either_boundary(ShenandoahFreeSetPartitionId partition, idx_t low_idx, idx_t high_idx,
                                                  idx_t low_empty_idx, idx_t high_empty_idx) {
  if (_leftmosts[int(partition)] > low_idx) {
    _leftmosts[int(partition)] = low_idx;
  }
  if (_rightmosts[int(partition)] < high_idx) {
    _rightmosts[int(partition)] = high_idx;
  }
  if (_leftmosts_empty[int(partition)] > low_empty_idx) {
    _leftmosts_empty[int(partition)] = low_empty_idx;
  }
  if (_rightmosts_empty[int(partition)] < high_empty_idx) {
    _rightmosts_empty[int(partition)] = high_empty_idx;
  }
}

void ShenandoahRegionPartitions::expand_interval_if_boundary_modified(ShenandoahFreeSetPartitionId partition,
                                                                      idx_t idx, size_t region_available) {
  if (_leftmosts[int(partition)] > idx) {
    _leftmosts[int(partition)] = idx;
  }
  if (_rightmosts[int(partition)] < idx) {
    _rightmosts[int(partition)] = idx;
  }
  if (region_available == _region_size_bytes) {
    if (_leftmosts_empty[int(partition)] > idx) {
      _leftmosts_empty[int(partition)] = idx;
    }
    if (_rightmosts_empty[int(partition)] < idx) {
      _rightmosts_empty[int(partition)] = idx;
    }
  }
}

void ShenandoahRegionPartitions::retire_range_from_partition(
  ShenandoahFreeSetPartitionId partition, idx_t low_idx, idx_t high_idx) {

  // Note: we may remove from free partition even if region is not entirely full, such as when available < PLAB::min_size()
  assert ((low_idx < _max) && (high_idx < _max), "Both indices are sane: %zu and %zu < %zu",
          low_idx, high_idx, _max);
  assert (partition < NumPartitions, "Cannot remove from free partitions if not already free");

  for (idx_t idx = low_idx; idx <= high_idx; idx++) {
#ifdef ASSERT
    ShenandoahHeapRegion* r = ShenandoahHeap::heap()->get_region(idx);
    assert (in_free_set(partition, idx), "Must be in partition to remove from partition");
    assert(r->is_empty() || r->is_trash(), "Region must be empty or trash");
#endif
    _membership[int(partition)].clear_bit(idx);
  }
  size_t num_regions = high_idx + 1 - low_idx;
  decrease_region_counts(partition, num_regions);
  decrease_empty_region_counts(partition, num_regions);
  shrink_interval_if_range_modifies_either_boundary(partition, low_idx, high_idx, num_regions);
}

size_t ShenandoahRegionPartitions::retire_from_partition(ShenandoahFreeSetPartitionId partition,
                                                         idx_t idx, size_t used_bytes) {

  size_t waste_bytes = 0;
  // Note: we may remove from free partition even if region is not entirely full, such as when available < PLAB::min_size()
  assert (idx < _max, "index is sane: %zu < %zu", idx, _max);
  assert (partition < NumPartitions, "Cannot remove from free partitions if not already free");
  assert (in_free_set(partition, idx), "Must be in partition to remove from partition");

  if (used_bytes < _region_size_bytes) {
    // Count the alignment pad remnant of memory as used when we retire this region
    size_t fill_padding = _region_size_bytes - used_bytes;
    waste_bytes = fill_padding;
    increase_used(partition, fill_padding);
  }
  _membership[int(partition)].clear_bit(idx);
  decrease_region_counts(partition, 1);
  shrink_interval_if_boundary_modified(partition, idx);

  // This region is fully used, whether or not top() equals end().  It
  // is retired and no more memory will be allocated from within it.

  return waste_bytes;
}

void ShenandoahRegionPartitions::unretire_to_partition(ShenandoahHeapRegion* r, ShenandoahFreeSetPartitionId which_partition) {
  shenandoah_assert_heaplocked();
  make_free(r->index(), which_partition, r->free());
}


// The caller is responsible for increasing capacity and available and used in which_partition, and decreasing the
// same quantities for the original partition
void ShenandoahRegionPartitions::make_free(idx_t idx, ShenandoahFreeSetPartitionId which_partition, size_t available) {
  shenandoah_assert_heaplocked();
  assert (idx < _max, "index is sane: %zu < %zu", idx, _max);
  assert (membership(idx) == ShenandoahFreeSetPartitionId::NotFree, "Cannot make free if already free");
  assert (which_partition < NumPartitions, "selected free partition must be valid");
  assert (available <= _region_size_bytes, "Available cannot exceed region size");

  _membership[int(which_partition)].set_bit(idx);
  expand_interval_if_boundary_modified(which_partition, idx, available);
}

bool ShenandoahRegionPartitions::is_mutator_partition(ShenandoahFreeSetPartitionId p) {
  return (p == ShenandoahFreeSetPartitionId::Mutator);
}

bool ShenandoahRegionPartitions::is_young_collector_partition(ShenandoahFreeSetPartitionId p) {
  return (p == ShenandoahFreeSetPartitionId::Collector);
}

bool ShenandoahRegionPartitions::is_old_collector_partition(ShenandoahFreeSetPartitionId p) {
  return (p == ShenandoahFreeSetPartitionId::OldCollector);
}

bool ShenandoahRegionPartitions::available_implies_empty(size_t available_in_region) {
  return (available_in_region == _region_size_bytes);
}

// Do not adjust capacities, available, or used.  Return used delta.
size_t ShenandoahRegionPartitions::
move_from_partition_to_partition_with_deferred_accounting(idx_t idx, ShenandoahFreeSetPartitionId orig_partition,
                                                          ShenandoahFreeSetPartitionId new_partition, size_t available) {
  ShenandoahHeapRegion* r = ShenandoahHeap::heap()->get_region(idx);
  shenandoah_assert_heaplocked();
  assert (idx < _max, "index is sane: %zu < %zu", idx, _max);
  assert (orig_partition < NumPartitions, "Original partition must be valid");
  assert (new_partition < NumPartitions, "New partition must be valid");
  assert (available <= _region_size_bytes, "Available cannot exceed region size");
  assert (_membership[int(orig_partition)].is_set(idx), "Cannot move from partition unless in partition");
  assert ((r != nullptr) && ((r->is_trash() && (available == _region_size_bytes)) ||
                             (r->used() + available == _region_size_bytes)),
          "Used: %zu + available: %zu should equal region size: %zu",
          ShenandoahHeap::heap()->get_region(idx)->used(), available, _region_size_bytes);

  // Expected transitions:
  //  During rebuild:         Mutator => Collector
  //                          Mutator empty => Collector
  //                          Mutator empty => OldCollector
  //  During flip_to_gc:      Mutator empty => Collector
  //                          Mutator empty => OldCollector
  // At start of update refs: Collector => Mutator
  //                          OldCollector Empty => Mutator
  assert ((is_mutator_partition(orig_partition) && is_young_collector_partition(new_partition)) ||
          (is_mutator_partition(orig_partition) &&
           available_implies_empty(available) && is_old_collector_partition(new_partition)) ||
          (is_young_collector_partition(orig_partition) && is_mutator_partition(new_partition)) ||
          (is_old_collector_partition(orig_partition)
           && available_implies_empty(available) && is_mutator_partition(new_partition)),
          "Unexpected movement between partitions, available: %zu, _region_size_bytes: %zu"
          ", orig_partition: %s, new_partition: %s",
          available, _region_size_bytes, partition_name(orig_partition), partition_name(new_partition));

  size_t used = _region_size_bytes - available;
  assert (_used[int(orig_partition)] >= used,
          "Orig partition used: %zu must exceed moved used: %zu within region %zd",
          _used[int(orig_partition)], used, idx);

  _membership[int(orig_partition)].clear_bit(idx);
  _membership[int(new_partition)].set_bit(idx);
  return used;
}

void ShenandoahRegionPartitions::move_from_partition_to_partition(idx_t idx, ShenandoahFreeSetPartitionId orig_partition,
                                                                  ShenandoahFreeSetPartitionId new_partition, size_t available) {
  size_t used = move_from_partition_to_partition_with_deferred_accounting(idx, orig_partition, new_partition, available);

  // We decreased used, which increases available, but then we decrease available by full region size below
  decrease_used(orig_partition, used);
  _region_counts[int(orig_partition)]--;
  _capacity[int(orig_partition)] -= _region_size_bytes;
  _available[int(orig_partition)] -= _region_size_bytes;
  shrink_interval_if_boundary_modified(orig_partition, idx);

  _capacity[int(new_partition)] += _region_size_bytes;
  _available[int(new_partition)] += _region_size_bytes;
  _region_counts[int(new_partition)]++;
  // We increased availableby full region size above, but decrease it by used within this region now.
  increase_used(new_partition, used);
  expand_interval_if_boundary_modified(new_partition, idx, available);

  if (available == _region_size_bytes) {
    _empty_region_counts[int(orig_partition)]--;
    _empty_region_counts[int(new_partition)]++;
  }
}

const char* ShenandoahRegionPartitions::partition_membership_name(idx_t idx) const {
  return partition_name(membership(idx));
}

#ifdef ASSERT
inline bool ShenandoahRegionPartitions::partition_id_matches(idx_t idx, ShenandoahFreeSetPartitionId test_partition) const {
  assert (idx < _max, "index is sane: %zu < %zu", idx, _max);
  assert (test_partition < ShenandoahFreeSetPartitionId::NotFree, "must be a valid partition");

  return membership(idx) == test_partition;
}
#endif

inline bool ShenandoahRegionPartitions::is_empty(ShenandoahFreeSetPartitionId which_partition) const {
  assert (which_partition < NumPartitions, "selected free partition must be valid");
  return (leftmost(which_partition) > rightmost(which_partition));
}

inline idx_t ShenandoahRegionPartitions::find_index_of_next_available_region(
  ShenandoahFreeSetPartitionId which_partition, idx_t start_index) const {
  idx_t rightmost_idx = rightmost(which_partition);
  idx_t leftmost_idx = leftmost(which_partition);
  if ((rightmost_idx < leftmost_idx) || (start_index > rightmost_idx)) return _max;
  if (start_index < leftmost_idx) {
    start_index = leftmost_idx;
  }
  idx_t result = _membership[int(which_partition)].find_first_set_bit(start_index, rightmost_idx + 1);
  if (result > rightmost_idx) {
    result = _max;
  }
  assert (result >= start_index, "Requires progress");
  return result;
}

inline idx_t ShenandoahRegionPartitions::find_index_of_previous_available_region(
  ShenandoahFreeSetPartitionId which_partition, idx_t last_index) const {
  idx_t rightmost_idx = rightmost(which_partition);
  idx_t leftmost_idx = leftmost(which_partition);
  // if (leftmost_idx == max) then (last_index < leftmost_idx)
  if (last_index < leftmost_idx) return -1;
  if (last_index > rightmost_idx) {
    last_index = rightmost_idx;
  }
  idx_t result = _membership[int(which_partition)].find_last_set_bit(-1, last_index);
  if (result < leftmost_idx) {
    result = -1;
  }
  assert (result <= last_index, "Requires progress");
  return result;
}

inline idx_t ShenandoahRegionPartitions::find_index_of_next_available_cluster_of_regions(
  ShenandoahFreeSetPartitionId which_partition, idx_t start_index, size_t cluster_size) const {
  idx_t rightmost_idx = rightmost(which_partition);
  idx_t leftmost_idx = leftmost(which_partition);
  if ((rightmost_idx < leftmost_idx) || (start_index > rightmost_idx)) return _max;
  idx_t result =
    _membership[int(which_partition)].find_first_consecutive_set_bits(start_index, rightmost_idx + 1, cluster_size);
  if (result > rightmost_idx) {
    result = _max;
  }
  assert (result >= start_index, "Requires progress");
  return result;
}

inline idx_t ShenandoahRegionPartitions::find_index_of_previous_available_cluster_of_regions(
  ShenandoahFreeSetPartitionId which_partition, idx_t last_index, size_t cluster_size) const {
  idx_t leftmost_idx = leftmost(which_partition);
  // if (leftmost_idx == max) then (last_index < leftmost_idx)
  if (last_index < leftmost_idx) return -1;
  idx_t result = _membership[int(which_partition)].find_last_consecutive_set_bits(leftmost_idx - 1, last_index, cluster_size);
  if (result <= leftmost_idx) {
    result = -1;
  }
  assert (result <= last_index, "Requires progress");
  return result;
}

idx_t ShenandoahRegionPartitions::leftmost_empty(ShenandoahFreeSetPartitionId which_partition) {
  assert (which_partition < NumPartitions, "selected free partition must be valid");
  idx_t max_regions = _max;
  if (_leftmosts_empty[int(which_partition)] == _max) {
    return _max;
  }
  for (idx_t idx = find_index_of_next_available_region(which_partition, _leftmosts_empty[int(which_partition)]);
       idx < max_regions; ) {
    assert(in_free_set(which_partition, idx), "Boundaries or find_last_set_bit failed: %zd", idx);
    if (_free_set->alloc_capacity(idx) == _region_size_bytes) {
      _leftmosts_empty[int(which_partition)] = idx;
      return idx;
    }
    idx = find_index_of_next_available_region(which_partition, idx + 1);
  }
  _leftmosts_empty[int(which_partition)] = _max;
  _rightmosts_empty[int(which_partition)] = -1;
  return _max;
}

idx_t ShenandoahRegionPartitions::rightmost_empty(ShenandoahFreeSetPartitionId which_partition) {
  assert (which_partition < NumPartitions, "selected free partition must be valid");
  if (_rightmosts_empty[int(which_partition)] < 0) {
    return -1;
  }
  for (idx_t idx = find_index_of_previous_available_region(which_partition, _rightmosts_empty[int(which_partition)]);
       idx >= 0; ) {
    assert(in_free_set(which_partition, idx), "Boundaries or find_last_set_bit failed: %zd", idx);
    if (_free_set->alloc_capacity(idx) == _region_size_bytes) {
      _rightmosts_empty[int(which_partition)] = idx;
      return idx;
    }
    idx = find_index_of_previous_available_region(which_partition, idx - 1);
  }
  _leftmosts_empty[int(which_partition)] = _max;
  _rightmosts_empty[int(which_partition)] = -1;
  return -1;
}


#ifdef ASSERT
void ShenandoahRegionPartitions::assert_bounds() {

  size_t capacities[UIntNumPartitions];
  size_t used[UIntNumPartitions];
  size_t regions[UIntNumPartitions];
  size_t humongous_waste[UIntNumPartitions];

  // We don't know whether young retired regions belonged to Mutator or Collector before they were retired.
  // We just tally the total, and divide it to make matches work if possible.
  size_t young_retired_regions = 0;
  size_t young_retired_used = 0;
  size_t young_retired_capacity = 0;
  size_t young_humongous_waste = 0;

  idx_t leftmosts[UIntNumPartitions];
  idx_t rightmosts[UIntNumPartitions];
  idx_t empty_leftmosts[UIntNumPartitions];
  idx_t empty_rightmosts[UIntNumPartitions];

  for (uint i = 0; i < UIntNumPartitions; i++) {
    leftmosts[i] = _max;
    empty_leftmosts[i] = _max;
    rightmosts[i] = -1;
    empty_rightmosts[i] = -1;
    capacities[i] = 0;
    used[i] = 0;
    regions[i] = 0;
    humongous_waste[i] = 0;
  }

  for (idx_t i = 0; i < _max; i++) {
    ShenandoahFreeSetPartitionId partition = membership(i);
    size_t capacity = _free_set->alloc_capacity(i);
    switch (partition) {
      case ShenandoahFreeSetPartitionId::NotFree:
      {
        assert(capacity != _region_size_bytes, "Should not be retired if empty");
        ShenandoahHeapRegion* r = ShenandoahHeap::heap()->get_region(i);
        if (r->is_humongous()) {
          if (r->is_old()) {
            regions[int(ShenandoahFreeSetPartitionId::OldCollector)]++;
            used[int(ShenandoahFreeSetPartitionId::OldCollector)] += _region_size_bytes;
            capacities[int(ShenandoahFreeSetPartitionId::OldCollector)] += _region_size_bytes;
            humongous_waste[int(ShenandoahFreeSetPartitionId::OldCollector)] += capacity;
          } else {
            assert(r->is_young(), "Must be young if not old");
            young_retired_regions++;
            // Count entire region as used even if there is some waste.
            young_retired_used += _region_size_bytes;
            young_retired_capacity += _region_size_bytes;
            young_humongous_waste += capacity;
          }
        } else {
          assert(r->is_cset() || (capacity < PLAB::min_size() * HeapWordSize),
                 "Expect retired remnant size to be smaller than min plab size");
          // This region has been retired already or it is in the cset.  In either case, we set capacity to zero
          // so that the entire region will be counted as used.  We count young cset regions as "retired".
          capacity = 0;
          if (r->is_old()) {
            regions[int(ShenandoahFreeSetPartitionId::OldCollector)]++;
            used[int(ShenandoahFreeSetPartitionId::OldCollector)] += _region_size_bytes - capacity;
            capacities[int(ShenandoahFreeSetPartitionId::OldCollector)] += _region_size_bytes;
          } else {
            assert(r->is_young(), "Must be young if not old");
            young_retired_regions++;
            young_retired_used += _region_size_bytes - capacity;
            young_retired_capacity += _region_size_bytes;
          }
        }
      }
      break;

      case ShenandoahFreeSetPartitionId::Mutator:
      case ShenandoahFreeSetPartitionId::Collector:
      case ShenandoahFreeSetPartitionId::OldCollector:
      {
        ShenandoahHeapRegion* r = ShenandoahHeap::heap()->get_region(i);
        assert(capacity > 0, "free regions must have allocation capacity");
        bool is_empty = (capacity == _region_size_bytes);
        regions[int(partition)]++;
        used[int(partition)] += _region_size_bytes - capacity;
        capacities[int(partition)] += _region_size_bytes;
        if (i < leftmosts[int(partition)]) {
          leftmosts[int(partition)] = i;
        }
        if (is_empty && (i < empty_leftmosts[int(partition)])) {
          empty_leftmosts[int(partition)] = i;
        }
        if (i > rightmosts[int(partition)]) {
          rightmosts[int(partition)] = i;
        }
        if (is_empty && (i > empty_rightmosts[int(partition)])) {
          empty_rightmosts[int(partition)] = i;
        }
        break;
      }

      default:
        ShouldNotReachHere();
    }
  }

  // Performance invariants. Failing these would not break the free partition, but performance would suffer.
  assert (leftmost(ShenandoahFreeSetPartitionId::Mutator) <= _max,
          "leftmost in bounds: %zd < %zd", leftmost(ShenandoahFreeSetPartitionId::Mutator),  _max);
  assert (rightmost(ShenandoahFreeSetPartitionId::Mutator) < _max,
          "rightmost in bounds: %zd < %zd", rightmost(ShenandoahFreeSetPartitionId::Mutator),  _max);

  assert (leftmost(ShenandoahFreeSetPartitionId::Mutator) == _max
          || partition_id_matches(leftmost(ShenandoahFreeSetPartitionId::Mutator), ShenandoahFreeSetPartitionId::Mutator),
          "leftmost region should be free: %zd",  leftmost(ShenandoahFreeSetPartitionId::Mutator));
  assert (leftmost(ShenandoahFreeSetPartitionId::Mutator) == _max
          || partition_id_matches(rightmost(ShenandoahFreeSetPartitionId::Mutator), ShenandoahFreeSetPartitionId::Mutator),
          "rightmost region should be free: %zd", rightmost(ShenandoahFreeSetPartitionId::Mutator));

  // If Mutator partition is empty, leftmosts will both equal max, rightmosts will both equal zero.
  // Likewise for empty region partitions.
  idx_t beg_off = leftmosts[int(ShenandoahFreeSetPartitionId::Mutator)];
  idx_t end_off = rightmosts[int(ShenandoahFreeSetPartitionId::Mutator)];
  assert (beg_off >= leftmost(ShenandoahFreeSetPartitionId::Mutator),
          "Mutator free region before the leftmost: %zd, bound %zd",
          beg_off, leftmost(ShenandoahFreeSetPartitionId::Mutator));
  assert (end_off <= rightmost(ShenandoahFreeSetPartitionId::Mutator),
          "Mutator free region past the rightmost: %zd, bound %zd",
          end_off, rightmost(ShenandoahFreeSetPartitionId::Mutator));

  beg_off = empty_leftmosts[int(ShenandoahFreeSetPartitionId::Mutator)];
  end_off = empty_rightmosts[int(ShenandoahFreeSetPartitionId::Mutator)];
  assert (beg_off >= _leftmosts_empty[int(ShenandoahFreeSetPartitionId::Mutator)],
          "free empty region (%zd) before the leftmost bound %zd",
          beg_off, _leftmosts_empty[int(ShenandoahFreeSetPartitionId::Mutator)]);
  assert (end_off <= _rightmosts_empty[int(ShenandoahFreeSetPartitionId::Mutator)],
          "free empty region (%zd) past the rightmost bound %zd",
          end_off, _rightmosts_empty[int(ShenandoahFreeSetPartitionId::Mutator)]);

  // Performance invariants. Failing these would not break the free partition, but performance would suffer.
  assert (leftmost(ShenandoahFreeSetPartitionId::Collector) <= _max, "leftmost in bounds: %zd < %zd",
          leftmost(ShenandoahFreeSetPartitionId::Collector),  _max);
  assert (rightmost(ShenandoahFreeSetPartitionId::Collector) < _max, "rightmost in bounds: %zd < %zd",
          rightmost(ShenandoahFreeSetPartitionId::Collector),  _max);

  assert (leftmost(ShenandoahFreeSetPartitionId::Collector) == _max
          || partition_id_matches(leftmost(ShenandoahFreeSetPartitionId::Collector), ShenandoahFreeSetPartitionId::Collector),
          "Collector leftmost region should be free: %zd",  leftmost(ShenandoahFreeSetPartitionId::Collector));
  assert (leftmost(ShenandoahFreeSetPartitionId::Collector) == _max
          || partition_id_matches(rightmost(ShenandoahFreeSetPartitionId::Collector), ShenandoahFreeSetPartitionId::Collector),
          "Collector rightmost region should be free: %zd", rightmost(ShenandoahFreeSetPartitionId::Collector));

  // If Collector partition is empty, leftmosts will both equal max, rightmosts will both equal zero.
  // Likewise for empty region partitions.
  beg_off = leftmosts[int(ShenandoahFreeSetPartitionId::Collector)];
  end_off = rightmosts[int(ShenandoahFreeSetPartitionId::Collector)];
  assert (beg_off >= leftmost(ShenandoahFreeSetPartitionId::Collector),
          "Collector free region before the leftmost: %zd, bound %zd",
          beg_off, leftmost(ShenandoahFreeSetPartitionId::Collector));
  assert (end_off <= rightmost(ShenandoahFreeSetPartitionId::Collector),
          "Collector free region past the rightmost: %zd, bound %zd",
          end_off, rightmost(ShenandoahFreeSetPartitionId::Collector));

  beg_off = empty_leftmosts[int(ShenandoahFreeSetPartitionId::Collector)];
  end_off = empty_rightmosts[int(ShenandoahFreeSetPartitionId::Collector)];
  assert (beg_off >= _leftmosts_empty[int(ShenandoahFreeSetPartitionId::Collector)],
          "Collector free empty region before the leftmost: %zd, bound %zd",
          beg_off, _leftmosts_empty[int(ShenandoahFreeSetPartitionId::Collector)]);
  assert (end_off <= _rightmosts_empty[int(ShenandoahFreeSetPartitionId::Collector)],
          "Collector free empty region past the rightmost: %zd, bound %zd",
          end_off, _rightmosts_empty[int(ShenandoahFreeSetPartitionId::Collector)]);

  // Performance invariants. Failing these would not break the free partition, but performance would suffer.
  assert (leftmost(ShenandoahFreeSetPartitionId::OldCollector) <= _max, "OldCollector leftmost in bounds: %zd < %zd",
          leftmost(ShenandoahFreeSetPartitionId::OldCollector),  _max);
  assert (rightmost(ShenandoahFreeSetPartitionId::OldCollector) < _max, "OldCollector rightmost in bounds: %zd < %zd",
          rightmost(ShenandoahFreeSetPartitionId::OldCollector),  _max);

  assert (leftmost(ShenandoahFreeSetPartitionId::OldCollector) == _max
          || partition_id_matches(leftmost(ShenandoahFreeSetPartitionId::OldCollector),
                                  ShenandoahFreeSetPartitionId::OldCollector),
          "OldCollector leftmost region should be free: %zd",  leftmost(ShenandoahFreeSetPartitionId::OldCollector));
  assert (leftmost(ShenandoahFreeSetPartitionId::OldCollector) == _max
          || partition_id_matches(rightmost(ShenandoahFreeSetPartitionId::OldCollector),
                                  ShenandoahFreeSetPartitionId::OldCollector),
          "OldCollector rightmost region should be free: %zd", rightmost(ShenandoahFreeSetPartitionId::OldCollector));

  // Concurrent recycling of trash recycles a region (changing its state from is_trash to is_empty without the heap lock),

  // If OldCollector partition is empty, leftmosts will both equal max, rightmosts will both equal zero.
  // Likewise for empty region partitions.
  beg_off = leftmosts[int(ShenandoahFreeSetPartitionId::OldCollector)];
  end_off = rightmosts[int(ShenandoahFreeSetPartitionId::OldCollector)];
  assert (beg_off >= leftmost(ShenandoahFreeSetPartitionId::OldCollector), "free regions before the leftmost: %zd, bound %zd",
          beg_off, leftmost(ShenandoahFreeSetPartitionId::OldCollector));
  assert (end_off <= rightmost(ShenandoahFreeSetPartitionId::OldCollector), "free regions past the rightmost: %zd, bound %zd",
          end_off, rightmost(ShenandoahFreeSetPartitionId::OldCollector));

  beg_off = empty_leftmosts[int(ShenandoahFreeSetPartitionId::OldCollector)];
  end_off = empty_rightmosts[int(ShenandoahFreeSetPartitionId::OldCollector)];
  assert (beg_off >= _leftmosts_empty[int(ShenandoahFreeSetPartitionId::OldCollector)],
          "free empty region (%zd) before the leftmost bound %zd, region %s trash",
          beg_off, _leftmosts_empty[int(ShenandoahFreeSetPartitionId::OldCollector)],
          ((beg_off >= _max)? "out of bounds is not":
           (ShenandoahHeap::heap()->get_region(_leftmosts_empty[int(ShenandoahFreeSetPartitionId::OldCollector)])->is_trash()?
            "is": "is not")));
  assert (end_off <= _rightmosts_empty[int(ShenandoahFreeSetPartitionId::OldCollector)],
          "free empty region (%zd) past the rightmost bound %zd, region %s trash",
          end_off, _rightmosts_empty[int(ShenandoahFreeSetPartitionId::OldCollector)],
          ((end_off < 0)? "out of bounds is not" :
           (ShenandoahHeap::heap()->get_region(_rightmosts_empty[int(ShenandoahFreeSetPartitionId::OldCollector)])->is_trash()?
            "is": "is not")));

  // young_retired_regions need to be added to either Mutator or Collector partitions, 100% used.
  // Give enough of young_retired_regions, young_retired_capacity, young_retired_user
  //  to the Mutator partition to top it off so that it matches the running totals.
  //
  // Give any remnants to the Collector partition.  After topping off the Collector partition, its values
  //  should also match running totals.
  assert(young_retired_regions * _region_size_bytes == young_retired_capacity, "sanity");
  assert(young_retired_capacity == young_retired_used, "sanity");

  assert(capacities[int(ShenandoahFreeSetPartitionId::OldCollector)]
         == _capacity[int(ShenandoahFreeSetPartitionId::OldCollector)], "Old collector capacities must match (%zu != %zu)",
         capacities[int(ShenandoahFreeSetPartitionId::OldCollector)],
         _capacity[int(ShenandoahFreeSetPartitionId::OldCollector)]);
  assert(used[int(ShenandoahFreeSetPartitionId::OldCollector)]
         == _used[int(ShenandoahFreeSetPartitionId::OldCollector)], "Old collector used must match");
  assert(regions[int(ShenandoahFreeSetPartitionId::OldCollector)]
         == _capacity[int(ShenandoahFreeSetPartitionId::OldCollector)] / _region_size_bytes, "Old collector regions must match");
  assert(_capacity[int(ShenandoahFreeSetPartitionId::OldCollector)]
         >= _used[int(ShenandoahFreeSetPartitionId::OldCollector)], "Old Collector capacity must be >= used");
  assert(_available[int(ShenandoahFreeSetPartitionId::OldCollector)] ==
         (_capacity[int(ShenandoahFreeSetPartitionId::OldCollector)] - _used[int(ShenandoahFreeSetPartitionId::OldCollector)]),
         "Old Collector available must equal capacity minus used");
  assert(_humongous_waste[int(ShenandoahFreeSetPartitionId::OldCollector)] ==
         humongous_waste[int(ShenandoahFreeSetPartitionId::OldCollector)], "Old Collector humongous waste must match");

  assert(_capacity[int(ShenandoahFreeSetPartitionId::Mutator)] >= capacities[int(ShenandoahFreeSetPartitionId::Mutator)],
         "Capacity total must be >= counted tally");
  size_t mutator_capacity_shortfall =
    _capacity[int(ShenandoahFreeSetPartitionId::Mutator)] - capacities[int(ShenandoahFreeSetPartitionId::Mutator)];
  assert(mutator_capacity_shortfall <= young_retired_capacity, "sanity");
  capacities[int(ShenandoahFreeSetPartitionId::Mutator)] += mutator_capacity_shortfall;
  young_retired_capacity -= mutator_capacity_shortfall;
  capacities[int(ShenandoahFreeSetPartitionId::Collector)] += young_retired_capacity;

  assert(_used[int(ShenandoahFreeSetPartitionId::Mutator)] >= used[int(ShenandoahFreeSetPartitionId::Mutator)],
         "Used total must be >= counted tally");
  size_t mutator_used_shortfall =
    _used[int(ShenandoahFreeSetPartitionId::Mutator)] - used[int(ShenandoahFreeSetPartitionId::Mutator)];
  assert(mutator_used_shortfall <= young_retired_used, "sanity");
  used[int(ShenandoahFreeSetPartitionId::Mutator)] += mutator_used_shortfall;
  young_retired_used -= mutator_used_shortfall;
  used[int(ShenandoahFreeSetPartitionId::Collector)] += young_retired_used;

  assert(_capacity[int(ShenandoahFreeSetPartitionId::Mutator)] / _region_size_bytes
         >= regions[int(ShenandoahFreeSetPartitionId::Mutator)], "Region total must be >= counted tally");
  size_t mutator_regions_shortfall = (_capacity[int(ShenandoahFreeSetPartitionId::Mutator)] / _region_size_bytes
                                      - regions[int(ShenandoahFreeSetPartitionId::Mutator)]);
  assert(mutator_regions_shortfall <= young_retired_regions, "sanity");
  regions[int(ShenandoahFreeSetPartitionId::Mutator)] += mutator_regions_shortfall;
  young_retired_regions -= mutator_regions_shortfall;
  regions[int(ShenandoahFreeSetPartitionId::Collector)] += young_retired_regions;

  assert(capacities[int(ShenandoahFreeSetPartitionId::Collector)] == _capacity[int(ShenandoahFreeSetPartitionId::Collector)],
         "Collector capacities must match");
  assert(used[int(ShenandoahFreeSetPartitionId::Collector)] == _used[int(ShenandoahFreeSetPartitionId::Collector)],
         "Collector used must match");
  assert(regions[int(ShenandoahFreeSetPartitionId::Collector)]
         == _capacity[int(ShenandoahFreeSetPartitionId::Collector)] / _region_size_bytes, "Collector regions must match");
  assert(_capacity[int(ShenandoahFreeSetPartitionId::Collector)] >= _used[int(ShenandoahFreeSetPartitionId::Collector)],
         "Collector Capacity must be >= used");
  assert(_available[int(ShenandoahFreeSetPartitionId::Collector)] ==
         (_capacity[int(ShenandoahFreeSetPartitionId::Collector)] - _used[int(ShenandoahFreeSetPartitionId::Collector)]),
         "Collector Available must equal capacity minus used");

  assert(capacities[int(ShenandoahFreeSetPartitionId::Mutator)] == _capacity[int(ShenandoahFreeSetPartitionId::Mutator)],
         "Mutator capacities must match");
  assert(used[int(ShenandoahFreeSetPartitionId::Mutator)] == _used[int(ShenandoahFreeSetPartitionId::Mutator)],
         "Mutator used must match");
  assert(regions[int(ShenandoahFreeSetPartitionId::Mutator)]
         == _capacity[int(ShenandoahFreeSetPartitionId::Mutator)] / _region_size_bytes, "Mutator regions must match");
  assert(_capacity[int(ShenandoahFreeSetPartitionId::Mutator)] >= _used[int(ShenandoahFreeSetPartitionId::Mutator)],
         "Mutator capacity must be >= used");
  assert(_available[int(ShenandoahFreeSetPartitionId::Mutator)] ==
         (_capacity[int(ShenandoahFreeSetPartitionId::Mutator)] - _used[int(ShenandoahFreeSetPartitionId::Mutator)]),
         "Mutator available must equal capacity minus used");
  assert(_humongous_waste[int(ShenandoahFreeSetPartitionId::Mutator)] == young_humongous_waste,
         "Mutator humongous waste must match");
}
#endif

ShenandoahFreeSet::ShenandoahFreeSet(ShenandoahHeap* heap, size_t max_regions) :
  _heap(heap),
  _partitions(max_regions, this),
  _total_humongous_waste(0),
  _alloc_bias_weight(0),
  _total_young_used(0),
  _total_old_used(0),
  _total_global_used(0),
  _young_affiliated_regions(0),
  _old_affiliated_regions(0),
  _global_affiliated_regions(0),
  _young_unaffiliated_regions(0),
  _global_unaffiliated_regions(0),
  _total_young_regions(0),
  _total_global_regions(0),
  _mutator_bytes_allocated_since_gc_start(0)
{
  clear_internal();
}

void ShenandoahFreeSet::move_unaffiliated_regions_from_collector_to_old_collector(ssize_t count) {
  shenandoah_assert_heaplocked();
  size_t region_size_bytes =  ShenandoahHeapRegion::region_size_bytes();

  size_t old_capacity = _partitions.get_capacity(ShenandoahFreeSetPartitionId::OldCollector);
  size_t collector_capacity = _partitions.get_capacity(ShenandoahFreeSetPartitionId::Collector);
  if (count > 0) {
    size_t ucount = count;
    size_t bytes_moved = ucount * region_size_bytes;
    assert(collector_capacity >= bytes_moved, "Cannot transfer");
    assert(_partitions.get_empty_region_counts(ShenandoahFreeSetPartitionId::Collector) >= ucount,
           "Cannot transfer %zu of %zu", ucount, _partitions.get_empty_region_counts(ShenandoahFreeSetPartitionId::Collector));
    _partitions.decrease_empty_region_counts(ShenandoahFreeSetPartitionId::Collector, ucount);
    _partitions.set_capacity_of(ShenandoahFreeSetPartitionId::Collector, collector_capacity - bytes_moved);
    _partitions.set_capacity_of(ShenandoahFreeSetPartitionId::OldCollector, old_capacity + bytes_moved);
    _partitions.increase_empty_region_counts(ShenandoahFreeSetPartitionId::OldCollector, ucount);
  } else if (count < 0) {
    size_t ucount = -count;
    size_t bytes_moved = ucount * region_size_bytes;
    assert(old_capacity >= bytes_moved, "Cannot transfer");
    assert(_partitions.get_empty_region_counts(ShenandoahFreeSetPartitionId::OldCollector) >= ucount,
           "Cannot transfer %zu of %zu", ucount, _partitions.get_empty_region_counts(ShenandoahFreeSetPartitionId::OldCollector));
    _partitions.decrease_empty_region_counts(ShenandoahFreeSetPartitionId::OldCollector, ucount);
    _partitions.set_capacity_of(ShenandoahFreeSetPartitionId::OldCollector, old_capacity - bytes_moved);
    _partitions.set_capacity_of(ShenandoahFreeSetPartitionId::Collector, collector_capacity + bytes_moved);
    _partitions.increase_empty_region_counts(ShenandoahFreeSetPartitionId::Collector, ucount);
  }
  // else, do nothing
}

// was pip_pad_bytes
void ShenandoahFreeSet::add_promoted_in_place_region_to_old_collector(ShenandoahHeapRegion* region) {
  shenandoah_assert_heaplocked();
  size_t plab_min_size_in_bytes = ShenandoahGenerationalHeap::heap()->plab_min_size() * HeapWordSize;
  size_t region_size_bytes =  ShenandoahHeapRegion::region_size_bytes();
  size_t available_in_region = alloc_capacity(region);
  size_t region_index = region->index();
  ShenandoahFreeSetPartitionId p = _partitions.membership(region_index);
  assert(_partitions.membership(region_index) == ShenandoahFreeSetPartitionId::NotFree,
         "Regions promoted in place should have been excluded from Mutator partition");

  // If region had been retired, its end-of-region alignment pad had been counted as used within the Mutator partition
  size_t used_while_awaiting_pip = region_size_bytes;
  size_t used_after_pip = region_size_bytes;
  if (available_in_region >= plab_min_size_in_bytes) {
    used_after_pip -= available_in_region;
  } else {
    if (available_in_region >= ShenandoahHeap::min_fill_size() * HeapWordSize) {
      size_t fill_words = available_in_region / HeapWordSize;
      ShenandoahHeap::heap()->old_generation()->card_scan()->register_object(region->top());
      region->allocate_fill(fill_words);
    }
    available_in_region = 0;
  }

  assert(p == ShenandoahFreeSetPartitionId::NotFree, "pip region must be NotFree");
  assert(region->is_young(), "pip region must be young");

  // Though this region may have been promoted in place from the Collector region, its usage is now accounted within
  // the Mutator partition.
  _partitions.decrease_used(ShenandoahFreeSetPartitionId::Mutator, used_while_awaiting_pip);

  // decrease capacity adjusts available
  _partitions.decrease_capacity(ShenandoahFreeSetPartitionId::Mutator, region_size_bytes);
  _partitions.increase_capacity(ShenandoahFreeSetPartitionId::OldCollector, region_size_bytes);
  _partitions.increase_used(ShenandoahFreeSetPartitionId::OldCollector, used_after_pip);
  region->set_affiliation(ShenandoahAffiliation::OLD_GENERATION);
  if (available_in_region > 0) {
    assert(available_in_region >= plab_min_size_in_bytes, "enforced above");
    _partitions.increase_region_counts(ShenandoahFreeSetPartitionId::OldCollector, 1);
    // make_free() adjusts bounds for OldCollector partition
    _partitions.make_free(region_index, ShenandoahFreeSetPartitionId::OldCollector, available_in_region);
    _heap->old_generation()->augment_promoted_reserve(available_in_region);
    assert(available_in_region != region_size_bytes, "Nothing to promote in place");
  }
  // else, leave this region as NotFree

  recompute_total_used</* UsedByMutatorChanged */ true,
                       /* UsedByCollectorChanged */ false, /* UsedByOldCollectorChanged */ true>();
  // Conservatively, assume that pip regions came from both Mutator and Collector
  recompute_total_affiliated</* MutatorEmptiesChanged */ false, /* CollectorEmptiesChanged */ false,
                             /* OldCollectorEmptiesChanged */ false, /* MutatorSizeChanged */ true,
                             /* CollectorSizeChanged */ true, /* OldCollectorSizeChanged */ true,
                             /* AffiliatedChangesAreYoungNeutral */ false, /* AffiliatedChangesAreGlobalNeutral */ true,
                             /* UnaffiliatedChangesAreYoungNeutral */ true>();
  _partitions.assert_bounds();
}

template<typename Iter>
HeapWord* ShenandoahFreeSet::allocate_with_affiliation(Iter& iterator,
                                                       ShenandoahAffiliation affiliation,
                                                       ShenandoahAllocRequest& req,
                                                       bool& in_new_region) {
  assert(affiliation != ShenandoahAffiliation::FREE, "Must not");
  ShenandoahHeapRegion* free_region = nullptr;
  for (idx_t idx = iterator.current(); iterator.has_next(); idx = iterator.next()) {
    ShenandoahHeapRegion* r = _heap->get_region(idx);
    if (r->affiliation() == affiliation) {
      HeapWord* result = try_allocate_in(r, req, in_new_region);
      if (result != nullptr) {
        return result;
      }
    } else if (free_region == nullptr && r->affiliation() == FREE) {
      free_region = r;
    }
  }
  // Failed to allocate within any affiliated region, try the first free region in the partition.
  if (free_region != nullptr) {
    HeapWord* result = try_allocate_in(free_region, req, in_new_region);
    assert(result != nullptr, "Allocate in free region in the partition always succeed.");
    return result;
  }
  log_debug(gc, free)("Could not allocate collector region with affiliation: %s for request " PTR_FORMAT,
                      shenandoah_affiliation_name(affiliation), p2i(&req));
  return nullptr;
}

HeapWord* ShenandoahFreeSet::allocate_single(ShenandoahAllocRequest& req, bool& in_new_region) {
  shenandoah_assert_heaplocked();

  // Scan the bitmap looking for a first fit.
  //
  // Leftmost and rightmost bounds provide enough caching to walk bitmap efficiently. Normally,
  // we would find the region to allocate at right away.
  //
  // Allocations are biased: GC allocations are taken from the high end of the heap.  Regular (and TLAB)
  // mutator allocations are taken from the middle of heap, below the memory reserved for Collector.
  // Humongous mutator allocations are taken from the bottom of the heap.
  //
  // Free set maintains mutator and collector partitions.  Normally, each allocates only from its partition,
  // except in special cases when the collector steals regions from the mutator partition.

  // Overwrite with non-zero (non-null) values only if necessary for allocation bookkeeping.

  if (req.is_mutator_alloc()) {
    return allocate_for_mutator(req, in_new_region);
  } else {
    return allocate_for_collector(req, in_new_region);
  }
}

HeapWord* ShenandoahFreeSet::allocate_for_mutator(ShenandoahAllocRequest &req, bool &in_new_region) {
  update_allocation_bias();

  if (_partitions.is_empty(ShenandoahFreeSetPartitionId::Mutator)) {
    // There is no recovery. Mutator does not touch collector view at all.
    return nullptr;
  }

  // Try to allocate in the mutator view
  if (_partitions.alloc_from_left_bias(ShenandoahFreeSetPartitionId::Mutator)) {
    // Allocate from low to high memory.  This keeps the range of fully empty regions more tightly packed.
    // Note that the most recently allocated regions tend not to be evacuated in a given GC cycle.  So this
    // tends to accumulate "fragmented" uncollected regions in high memory.
    ShenandoahLeftRightIterator iterator(&_partitions, ShenandoahFreeSetPartitionId::Mutator);
    return allocate_from_regions(iterator, req, in_new_region);
  }

  // Allocate from high to low memory. This preserves low memory for humongous allocations.
  ShenandoahRightLeftIterator iterator(&_partitions, ShenandoahFreeSetPartitionId::Mutator);
  return allocate_from_regions(iterator, req, in_new_region);
}

void ShenandoahFreeSet::update_allocation_bias() {
  if (_alloc_bias_weight-- <= 0) {
    // We have observed that regions not collected in previous GC cycle tend to congregate at one end or the other
    // of the heap.  Typically, these are the more recently engaged regions and the objects in these regions have not
    // yet had a chance to die (and/or are treated as floating garbage).  If we use the same allocation bias on each
    // GC pass, these "most recently" engaged regions for GC pass N will also be the "most recently" engaged regions
    // for GC pass N+1, and the relatively large amount of live data and/or floating garbage introduced
    // during the most recent GC pass may once again prevent the region from being collected.  We have found that
    // alternating the allocation behavior between GC passes improves evacuation performance by 3-7% on certain
    // benchmarks.  In the best case, this has the effect of consuming these partially consumed regions before
    // the start of the next mark cycle so all of their garbage can be efficiently reclaimed.
    //
    // First, finish consuming regions that are already partially consumed so as to more tightly limit ranges of
    // available regions.  Other potential benefits:
    //  1. Eventual collection set has fewer regions because we have packed newly allocated objects into fewer regions
    //  2. We preserve the "empty" regions longer into the GC cycle, reducing likelihood of allocation failures
    //     late in the GC cycle.
    idx_t non_empty_on_left = (_partitions.leftmost_empty(ShenandoahFreeSetPartitionId::Mutator)
                               - _partitions.leftmost(ShenandoahFreeSetPartitionId::Mutator));
    idx_t non_empty_on_right = (_partitions.rightmost(ShenandoahFreeSetPartitionId::Mutator)
                                - _partitions.rightmost_empty(ShenandoahFreeSetPartitionId::Mutator));
    _partitions.set_bias_from_left_to_right(ShenandoahFreeSetPartitionId::Mutator, (non_empty_on_right < non_empty_on_left));
    _alloc_bias_weight = INITIAL_ALLOC_BIAS_WEIGHT;
  }
}

template<typename Iter>
HeapWord* ShenandoahFreeSet::allocate_from_regions(Iter& iterator, ShenandoahAllocRequest &req, bool &in_new_region) {
  for (idx_t idx = iterator.current(); iterator.has_next(); idx = iterator.next()) {
    ShenandoahHeapRegion* r = _heap->get_region(idx);
    size_t min_size = req.is_lab_alloc() ? req.min_size() : req.size();
    if (alloc_capacity(r) >= min_size * HeapWordSize) {
      HeapWord* result = try_allocate_in(r, req, in_new_region);
      if (result != nullptr) {
        return result;
      }
    }
  }
  return nullptr;
}

HeapWord* ShenandoahFreeSet::allocate_for_collector(ShenandoahAllocRequest &req, bool &in_new_region) {
  shenandoah_assert_heaplocked();
  ShenandoahFreeSetPartitionId which_partition = req.is_old()? ShenandoahFreeSetPartitionId::OldCollector: ShenandoahFreeSetPartitionId::Collector;
  HeapWord* result = nullptr;
  if (_partitions.alloc_from_left_bias(which_partition)) {
    ShenandoahLeftRightIterator iterator(&_partitions, which_partition);
    result = allocate_with_affiliation(iterator, req.affiliation(), req, in_new_region);
  } else {
    ShenandoahRightLeftIterator iterator(&_partitions, which_partition);
    result = allocate_with_affiliation(iterator, req.affiliation(), req, in_new_region);
  }

  if (result != nullptr) {
    return result;
  }

  // No dice. Can we borrow space from mutator view?
  if (!ShenandoahEvacReserveOverflow) {
    return nullptr;
  }

  if (_partitions.get_empty_region_counts(ShenandoahFreeSetPartitionId::Mutator) > 0) {
    // Try to steal an empty region from the mutator view.
    result = try_allocate_from_mutator(req, in_new_region);
  }

  // This is it. Do not try to mix mutator and GC allocations, because adjusting region UWM
  // due to GC allocations would expose unparsable mutator allocations.
  return result;
}

HeapWord* ShenandoahFreeSet::try_allocate_from_mutator(ShenandoahAllocRequest& req, bool& in_new_region) {
  // The collector prefers to keep longer lived regions toward the right side of the heap, so it always
  // searches for regions from right to left here.
  ShenandoahRightLeftIterator iterator(&_partitions, ShenandoahFreeSetPartitionId::Mutator, true);
  for (idx_t idx = iterator.current(); iterator.has_next(); idx = iterator.next()) {
    ShenandoahHeapRegion* r = _heap->get_region(idx);
    if (can_allocate_from(r)) {
      if (req.is_old()) {
        if (!flip_to_old_gc(r)) {
          continue;
        }
      } else {
        flip_to_gc(r);
      }
      // Region r is entirely empty.  If try_allocate_in fails on region r, something else is really wrong.
      // Don't bother to retry with other regions.
      log_debug(gc, free)("Flipped region %zu to gc for request: " PTR_FORMAT, idx, p2i(&req));
      return try_allocate_in(r, req, in_new_region);
    }
  }

  return nullptr;
}

// This work method takes an argument corresponding to the number of bytes
// free in a region, and returns the largest amount in heapwords that can be allocated
// such that both of the following conditions are satisfied:
//
// 1. it is a multiple of card size
// 2. any remaining shard may be filled with a filler object
//
// The idea is that the allocation starts and ends at card boundaries. Because
// a region ('s end) is card-aligned, the remainder shard that must be filled is
// at the start of the free space.
//
// This is merely a helper method to use for the purpose of such a calculation.
size_t ShenandoahFreeSet::get_usable_free_words(size_t free_bytes) const {
  // e.g. card_size is 512, card_shift is 9, min_fill_size() is 8
  //      free is 514
  //      usable_free is 512, which is decreased to 0
  size_t usable_free = (free_bytes / CardTable::card_size()) << CardTable::card_shift();
  assert(usable_free <= free_bytes, "Sanity check");
  if ((free_bytes != usable_free) && (free_bytes - usable_free < ShenandoahHeap::min_fill_size() * HeapWordSize)) {
    // After aligning to card multiples, the remainder would be smaller than
    // the minimum filler object, so we'll need to take away another card's
    // worth to construct a filler object.
    if (usable_free >= CardTable::card_size()) {
      usable_free -= CardTable::card_size();
    } else {
      assert(usable_free == 0, "usable_free is a multiple of card_size and card_size > min_fill_size");
    }
  }

  return usable_free / HeapWordSize;
}

// Given a size argument, which is a multiple of card size, a request struct
// for a PLAB, and an old region, return a pointer to the allocated space for
// a PLAB which is card-aligned and where any remaining shard in the region
// has been suitably filled by a filler object.
// It is assumed (and assertion-checked) that such an allocation is always possible.
HeapWord* ShenandoahFreeSet::allocate_aligned_plab(size_t size, ShenandoahAllocRequest& req, ShenandoahHeapRegion* r) {
  assert(_heap->mode()->is_generational(), "PLABs are only for generational mode");
  assert(r->is_old(), "All PLABs reside in old-gen");
  assert(!req.is_mutator_alloc(), "PLABs should not be allocated by mutators.");
  assert(is_aligned(size, CardTable::card_size_in_words()), "Align by design");

  HeapWord* result = r->allocate_aligned(size, req, CardTable::card_size());
  assert(result != nullptr, "Allocation cannot fail");
  assert(r->top() <= r->end(), "Allocation cannot span end of region");
  assert(is_aligned(result, CardTable::card_size_in_words()), "Align by design");
  return result;
}

HeapWord* ShenandoahFreeSet::try_allocate_in(ShenandoahHeapRegion* r, ShenandoahAllocRequest& req, bool& in_new_region) {
  assert (has_alloc_capacity(r), "Performance: should avoid full regions on this path: %zu", r->index());
  if (_heap->is_concurrent_weak_root_in_progress() && r->is_trash()) {
    // We cannot use this region for allocation when weak roots are in progress because the collector may need
    // to reference unmarked oops during concurrent classunloading. The collector also needs accurate marking
    // information to determine which weak handles need to be null'd out. If the region is recycled before weak
    // roots processing has finished, weak root processing may fail to null out a handle into a trashed region.
    // This turns the handle into a dangling pointer and will crash or corrupt the heap.
    return nullptr;
  }
  HeapWord* result = nullptr;
  // We must call try_recycle_under_lock() even if !r->is_trash().  The reason is that if r is being recycled at this
  // moment by a GC worker thread, it may appear to be not trash even though it has not yet been fully recycled.  If
  // we proceed without waiting for the worker to finish recycling the region, the worker thread may overwrite the
  // region's affiliation with FREE after we set the region's affiliation to req.afiliation() below
  r->try_recycle_under_lock();
  in_new_region = r->is_empty();
  if (in_new_region) {
    log_debug(gc, free)("Using new region (%zu) for %s (" PTR_FORMAT ").",
                        r->index(), req.type_string(), p2i(&req));
    assert(!r->is_affiliated(), "New region %zu should be unaffiliated", r->index());
    r->set_affiliation(req.affiliation());
    if (r->is_old()) {
      // Any OLD region allocated during concurrent coalesce-and-fill does not need to be coalesced and filled because
      // all objects allocated within this region are above TAMS (and thus are implicitly marked).  In case this is an
      // OLD region and concurrent preparation for mixed evacuations visits this region before the start of the next
      // old-gen concurrent mark (i.e. this region is allocated following the start of old-gen concurrent mark but before
      // concurrent preparations for mixed evacuations are completed), we mark this region as not requiring any
      // coalesce-and-fill processing.
      r->end_preemptible_coalesce_and_fill();
      _heap->old_generation()->clear_cards_for(r);
    }
#ifdef ASSERT
    ShenandoahMarkingContext* const ctx = _heap->marking_context();
    assert(ctx->top_at_mark_start(r) == r->bottom(), "Newly established allocation region starts with TAMS equal to bottom");
    assert(ctx->is_bitmap_range_within_region_clear(ctx->top_bitmap(r), r->end()), "Bitmap above top_bitmap() must be clear");
#endif
    log_debug(gc, free)("Using new region (%zu) for %s (" PTR_FORMAT ").",
                        r->index(), req.type_string(), p2i(&req));
  } else {
    assert(r->is_affiliated(), "Region %zu that is not new should be affiliated", r->index());
    if (r->affiliation() != req.affiliation()) {
      assert(_heap->mode()->is_generational(), "Request for %s from %s region should only happen in generational mode.",
             req.affiliation_name(), r->affiliation_name());
      return nullptr;
    }
  }

  // req.size() is in words, r->free() is in bytes.
  if (req.is_lab_alloc()) {
    size_t adjusted_size = req.size();
    size_t free = r->free();    // free represents bytes available within region r
    if (req.is_old()) {
      // This is a PLAB allocation(lab alloc in old gen)
      assert(_heap->mode()->is_generational(), "PLABs are only for generational mode");
      assert(_partitions.in_free_set(ShenandoahFreeSetPartitionId::OldCollector, r->index()),
             "PLABS must be allocated in old_collector_free regions");

      // Need to assure that plabs are aligned on multiple of card region
      // Convert free from unaligned bytes to aligned number of words
      size_t usable_free = get_usable_free_words(free);
      if (adjusted_size > usable_free) {
        adjusted_size = usable_free;
      }
      adjusted_size = align_down(adjusted_size, CardTable::card_size_in_words());
      if (adjusted_size >= req.min_size()) {
        result = allocate_aligned_plab(adjusted_size, req, r);
        assert(result != nullptr, "allocate must succeed");
        req.set_actual_size(adjusted_size);
      } else {
        // Otherwise, leave result == nullptr because the adjusted size is smaller than min size.
        log_trace(gc, free)("Failed to shrink PLAB request (%zu) in region %zu to %zu"
                            " because min_size() is %zu", req.size(), r->index(), adjusted_size, req.min_size());
      }
    } else {
      // This is a GCLAB or a TLAB allocation
      // Convert free from unaligned bytes to aligned number of words
      free = align_down(free >> LogHeapWordSize, MinObjAlignment);
      if (adjusted_size > free) {
        adjusted_size = free;
      }
      if (adjusted_size >= req.min_size()) {
        result = r->allocate(adjusted_size, req);
        assert (result != nullptr, "Allocation must succeed: free %zu, actual %zu", free, adjusted_size);
        req.set_actual_size(adjusted_size);
      } else {
        log_trace(gc, free)("Failed to shrink TLAB or GCLAB request (%zu) in region %zu to %zu"
                            " because min_size() is %zu", req.size(), r->index(), adjusted_size, req.min_size());
      }
    }
  } else {
    size_t size = req.size();
    result = r->allocate(size, req);
    if (result != nullptr) {
      // Record actual allocation size
      req.set_actual_size(size);
    }
  }

  if (result != nullptr) {
    // Allocation successful, bump stats:
    if (req.is_mutator_alloc()) {
      assert(req.is_young(), "Mutator allocations always come from young generation.");
      _partitions.increase_used(ShenandoahFreeSetPartitionId::Mutator, req.actual_size() * HeapWordSize);
      increase_bytes_allocated(req.actual_size() * HeapWordSize);
    } else {
      assert(req.is_gc_alloc(), "Should be gc_alloc since req wasn't mutator alloc");

      // For GC allocations, we advance update_watermark because the objects relocated into this memory during
      // evacuation are not updated during evacuation.  For both young and old regions r, it is essential that all
      // PLABs be made parsable at the end of evacuation.  This is enabled by retiring all plabs at end of evacuation.
      r->set_update_watermark(r->top());
      if (r->is_old()) {
        _partitions.increase_used(ShenandoahFreeSetPartitionId::OldCollector, (req.actual_size() + req.waste()) * HeapWordSize);
      } else {
        _partitions.increase_used(ShenandoahFreeSetPartitionId::Collector, (req.actual_size() + req.waste()) * HeapWordSize);
      }
    }
  }

  ShenandoahFreeSetPartitionId orig_partition;
  if (req.is_mutator_alloc()) {
    orig_partition = ShenandoahFreeSetPartitionId::Mutator;
  } else if (req.is_old()) {
    orig_partition = ShenandoahFreeSetPartitionId::OldCollector;
  } else {
    // Not old collector alloc, so this is a young collector gclab or shared allocation
    orig_partition = ShenandoahFreeSetPartitionId::Collector;
  }
  if (alloc_capacity(r) < PLAB::min_size() * HeapWordSize) {
    // Regardless of whether this allocation succeeded, if the remaining memory is less than PLAB:min_size(), retire this region.
    // Note that retire_from_partition() increases used to account for waste.

    // Also, if this allocation request failed and the consumed within this region * ShenandoahEvacWaste > region size,
    // then retire the region so that subsequent searches can find available memory more quickly.

    size_t idx = r->index();
    if ((result != nullptr) && in_new_region) {
      _partitions.one_region_is_no_longer_empty(orig_partition);
    }
    size_t waste_bytes = _partitions.retire_from_partition(orig_partition, idx, r->used());
    if (req.is_mutator_alloc() && (waste_bytes > 0)) {
      increase_bytes_allocated(waste_bytes);
    }
  } else if ((result != nullptr) && in_new_region) {
    _partitions.one_region_is_no_longer_empty(orig_partition);
  }

  switch (orig_partition) {
  case ShenandoahFreeSetPartitionId::Mutator:
    recompute_total_used</* UsedByMutatorChanged */ true,
                         /* UsedByCollectorChanged */ false, /* UsedByOldCollectorChanged */ false>();
    if (in_new_region) {
      recompute_total_affiliated</* MutatorEmptiesChanged */ true, /* CollectorEmptiesChanged */ false,
                                 /* OldCollectorEmptiesChanged */ false, /* MutatorSizeChanged */ false,
                                 /* CollectorSizeChanged */ false, /* OldCollectorSizeChanged */ false,
                                 /* AffiliatedChangesAreYoungNeutral */ false, /* AffiliatedChangesAreGlobalNeutral */ false,
                                 /* UnaffiliatedChangesAreYoungNeutral */ false>();
    }
    break;
  case ShenandoahFreeSetPartitionId::Collector:
    recompute_total_used</* UsedByMutatorChanged */ false,
                         /* UsedByCollectorChanged */ true, /* UsedByOldCollectorChanged */ false>();
    if (in_new_region) {
      recompute_total_affiliated</* MutatorEmptiesChanged */ false, /* CollectorEmptiesChanged */ true,
                                 /* OldCollectorEmptiesChanged */ false, /* MutatorSizeChanged */ false,
                                 /* CollectorSizeChanged */ false, /* OldCollectorSizeChanged */ false,
                                 /* AffiliatedChangesAreYoungNeutral */ false, /* AffiliatedChangesAreGlobalNeutral */ false,
                                 /* UnaffiliatedChangesAreYoungNeutral */ false>();
    }
    break;
  case ShenandoahFreeSetPartitionId::OldCollector:
    recompute_total_used</* UsedByMutatorChanged */ false,
                         /* UsedByCollectorChanged */ false, /* UsedByOldCollectorChanged */ true>();
    if (in_new_region) {
      recompute_total_affiliated</* MutatorEmptiesChanged */ false, /* CollectorEmptiesChanged */ false,
                                 /* OldCollectorEmptiesChanged */ true, /* MutatorSizeChanged */ false,
                                 /* CollectorSizeChanged */ false, /* OldCollectorSizeChanged */ false,
                                 /* AffiliatedChangesAreYoungNeutral */ true, /* AffiliatedChangesAreGlobalNeutral */ false,
                                 /* UnaffiliatedChangesAreYoungNeutral */ true>();
    }
    break;
  case ShenandoahFreeSetPartitionId::NotFree:
  default:
    assert(false, "won't happen");
  }
  _partitions.assert_bounds();
  return result;
}

HeapWord* ShenandoahFreeSet::allocate_contiguous(ShenandoahAllocRequest& req, bool is_humongous) {
  assert(req.is_mutator_alloc(), "All contiguous allocations are performed by mutator");
  shenandoah_assert_heaplocked();

  size_t words_size = req.size();
  idx_t num = ShenandoahHeapRegion::required_regions(words_size * HeapWordSize);

  assert(req.is_young(), "Humongous regions always allocated in YOUNG");

  // Check if there are enough regions left to satisfy allocation.
  if (num > (idx_t) _partitions.count(ShenandoahFreeSetPartitionId::Mutator)) {
    return nullptr;
  }

  idx_t start_range = _partitions.leftmost_empty(ShenandoahFreeSetPartitionId::Mutator);
  idx_t end_range = _partitions.rightmost_empty(ShenandoahFreeSetPartitionId::Mutator) + 1;
  idx_t last_possible_start = end_range - num;

  // Find the continuous interval of $num regions, starting from $beg and ending in $end,
  // inclusive. Contiguous allocations are biased to the beginning.
  idx_t beg = _partitions.find_index_of_next_available_cluster_of_regions(ShenandoahFreeSetPartitionId::Mutator,
                                                                          start_range, num);
  if (beg > last_possible_start) {
    // Hit the end, goodbye
    return nullptr;
  }
  idx_t end = beg;

  while (true) {
    // We've confirmed num contiguous regions belonging to Mutator partition, so no need to confirm membership.
    // If region is not completely free, the current [beg; end] is useless, and we may fast-forward.  If we can extend
    // the existing range, we can exploit that certain regions are already known to be in the Mutator free set.
    while (!can_allocate_from(_heap->get_region(end))) {
      // region[end] is not empty, so we restart our search after region[end]
      idx_t slide_delta = end + 1 - beg;
      if (beg + slide_delta > last_possible_start) {
        // no room to slide
        return nullptr;
      }
      for (idx_t span_end = beg + num; slide_delta > 0; slide_delta--) {
        if (!_partitions.in_free_set(ShenandoahFreeSetPartitionId::Mutator, span_end)) {
          beg = _partitions.find_index_of_next_available_cluster_of_regions(ShenandoahFreeSetPartitionId::Mutator,
                                                                            span_end + 1, num);
          break;
        } else {
          beg++;
          span_end++;
        }
      }
      // Here, either beg identifies a range of num regions all of which are in the Mutator free set, or beg > last_possible_start
      if (beg > last_possible_start) {
        // Hit the end, goodbye
        return nullptr;
      }
      end = beg;
    }

    if ((end - beg + 1) == num) {
      // found the match
      break;
    }

    end++;
  }

  size_t total_used = 0;
  const size_t used_words_in_last_region = words_size & ShenandoahHeapRegion::region_size_words_mask();
  size_t waste_bytes;
  // Retire regions from free partition and initialize them.
  if (is_humongous) {
    // Humongous allocation retires all regions at once: no allocation is possible anymore.
    // retire_range_from_partition() will adjust bounds on Mutator free set if appropriate and will recompute affiliated.
    _partitions.retire_range_from_partition(ShenandoahFreeSetPartitionId::Mutator, beg, end);
    for (idx_t i = beg; i <= end; i++) {
      ShenandoahHeapRegion* r = _heap->get_region(i);
      assert(i == beg || _heap->get_region(i - 1)->index() + 1 == r->index(), "Should be contiguous");
      r->try_recycle_under_lock();
      assert(r->is_empty(), "Should be empty");
      r->set_affiliation(req.affiliation());
      if (i == beg) {
        r->make_humongous_start();
      } else {
        r->make_humongous_cont();
      }
      if ((i == end) && (used_words_in_last_region > 0)) {
        r->set_top(r->bottom() + used_words_in_last_region);
      } else {
        // if used_words_in_last_region is zero, then the end region is fully consumed.
        r->set_top(r->end());
      }
      r->set_update_watermark(r->bottom());
    }
    total_used = ShenandoahHeapRegion::region_size_bytes() * num;
    waste_bytes =
      (used_words_in_last_region == 0)? 0: ShenandoahHeapRegion::region_size_bytes() - used_words_in_last_region * HeapWordSize;
  } else {
    // Non-humongous allocation retires only the regions that cannot be used for allocation anymore.
    waste_bytes = 0;
    for (idx_t i = beg; i <= end; i++) {
      ShenandoahHeapRegion* r = _heap->get_region(i);
      assert(i == beg || _heap->get_region(i - 1)->index() + 1 == r->index(), "Should be contiguous");
      assert(r->is_empty(), "Should be empty");
      r->try_recycle_under_lock();
      r->set_affiliation(req.affiliation());
      r->make_regular_allocation(req.affiliation());
      if ((i == end) && (used_words_in_last_region > 0)) {
        r->set_top(r->bottom() + used_words_in_last_region);
      } else {
        // if used_words_in_last_region is zero, then the end region is fully consumed.
        r->set_top(r->end());
      }
      r->set_update_watermark(r->bottom());
      total_used += r->used();
      if  (r->free() < PLAB::min_size() * HeapWordSize) {
        // retire_from_partition() will adjust bounds on Mutator free set if appropriate and will recompute affiliated.
        // It also increases used for the waste bytes, which includes bytes filled at retirement and bytes too small
        // to be filled.  Only the last iteration may have non-zero waste_bytes.
        waste_bytes += _partitions.retire_from_partition(ShenandoahFreeSetPartitionId::Mutator, i, r->used());
      }
    }
    _partitions.decrease_empty_region_counts(ShenandoahFreeSetPartitionId::Mutator, num);
    if (waste_bytes > 0) {
      // For humongous allocations, waste_bytes are included in total_used.  Since this is not humongous,
      // we need to account separately for the waste_bytes.
      increase_bytes_allocated(waste_bytes);
    }
  }

  _partitions.increase_used(ShenandoahFreeSetPartitionId::Mutator, total_used);
  increase_bytes_allocated(total_used);
  req.set_actual_size(words_size);
  // If !is_humongous, the "waste" is made availabe for new allocation
  if (waste_bytes > 0) {
    req.set_waste(waste_bytes / HeapWordSize);
    if (is_humongous) {
      _partitions.increase_humongous_waste(ShenandoahFreeSetPartitionId::Mutator, waste_bytes);
      _total_humongous_waste += waste_bytes;
    }
  }

  recompute_total_young_used</* UsedByMutatorChanged */ true, /*UsedByCollectorChanged */ false>();
  recompute_total_global_used</* UsedByMutatorChanged */ true, /*UsedByCollectorChanged */ false,
                              /* UsedByOldCollectorChanged */ true>();
  recompute_total_affiliated</* MutatorEmptiesChanged */ true, /* CollectorEmptiesChanged */ false,
                             /* OldCollectorEmptiesChanged */ false, /* MutatorSizeChanged */ false,
                             /* CollectorSizeChanged */ false, /* OldCollectorSizeChanged */ false,
                             /* AffiliatedChangesAreYoungNeutral */ false, /* AffiliatedChangesAreGlobalNeutral */ false,
                             /* UnaffiliatedChangesAreYoungNeutral */ false>();
  _partitions.assert_bounds();
  return _heap->get_region(beg)->bottom();
}

class ShenandoahRecycleTrashedRegionClosure final : public ShenandoahHeapRegionClosure {
public:
  void heap_region_do(ShenandoahHeapRegion* r) {
    if (r->is_trash()) {
      r->try_recycle();
    }
  }

  bool is_thread_safe() {
    return true;
  }
};

void ShenandoahFreeSet::recycle_trash() {
  // lock is not non-reentrant, check we don't have it
  shenandoah_assert_not_heaplocked();

  ShenandoahHeap* heap = ShenandoahHeap::heap();
  heap->assert_gc_workers(heap->workers()->active_workers());

  ShenandoahRecycleTrashedRegionClosure closure;
  heap->parallel_heap_region_iterate(&closure);
}

bool ShenandoahFreeSet::transfer_one_region_from_mutator_to_old_collector(size_t idx, size_t alloc_capacity) {
  ShenandoahGenerationalHeap* gen_heap = ShenandoahGenerationalHeap::heap();
  size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();
  assert(alloc_capacity == region_size_bytes, "Region must be empty");
  if (young_unaffiliated_regions() > 0) {
    _partitions.move_from_partition_to_partition(idx, ShenandoahFreeSetPartitionId::Mutator,
                                                 ShenandoahFreeSetPartitionId::OldCollector, alloc_capacity);
    gen_heap->old_generation()->augment_evacuation_reserve(alloc_capacity);
    recompute_total_used</* UsedByMutatorChanged */ true,
                         /* UsedByCollectorChanged */ false, /* UsedByOldCollectorChanged */ true>();
    // Transferred region is unaffilliated, empty
    recompute_total_affiliated</* MutatorEmptiesChanged */ true, /* CollectorEmptiesChanged */ false,
                               /* OldCollectorEmptiesChanged */ true, /* MutatorSizeChanged */ true,
                               /* CollectorSizeChanged */ false, /* OldCollectorSizeChanged */ true,
                               /* AffiliatedChangesAreYoungNeutral */ true, /* AffiliatedChangesAreGlobalNeutral */ true,
                               /* UnaffiliatedChangesAreYoungNeutral */ false>();
    _partitions.assert_bounds();
    return true;
  } else {
    return false;
  }
}

bool ShenandoahFreeSet::flip_to_old_gc(ShenandoahHeapRegion* r) {
  const size_t idx = r->index();

  assert(_partitions.partition_id_matches(idx, ShenandoahFreeSetPartitionId::Mutator), "Should be in mutator view");
  assert(can_allocate_from(r), "Should not be allocated");

  const size_t region_alloc_capacity = alloc_capacity(r);

  if (transfer_one_region_from_mutator_to_old_collector(idx, region_alloc_capacity)) {
    return true;
  }

  if (_heap->young_generation()->free_unaffiliated_regions() == 0 && _heap->old_generation()->free_unaffiliated_regions() > 0) {
    // Old has free unaffiliated regions, but it couldn't use them for allocation (likely because they
    // are trash and weak roots are in process). In this scenario, we aren't really stealing from the
    // mutator (they have nothing to steal), but they do have a usable region in their partition. What
    // we want to do here is swap that region from the mutator partition with one from the old collector
    // partition.
    // 1. Find a temporarily unusable trash region in the old collector partition
    ShenandoahRightLeftIterator iterator(&_partitions, ShenandoahFreeSetPartitionId::OldCollector, true);
    idx_t unusable_trash = -1;
    for (unusable_trash = iterator.current(); iterator.has_next(); unusable_trash = iterator.next()) {
      const ShenandoahHeapRegion* region = _heap->get_region(unusable_trash);
      if (region->is_trash() && _heap->is_concurrent_weak_root_in_progress()) {
        break;
      }
    }

    if (unusable_trash != -1) {
      const size_t unusable_capacity = alloc_capacity(unusable_trash);
      // 2. Move the (temporarily) unusable trash region we found to the mutator partition
      _partitions.move_from_partition_to_partition(unusable_trash,
                                                   ShenandoahFreeSetPartitionId::OldCollector,
                                                   ShenandoahFreeSetPartitionId::Mutator, unusable_capacity);

      // 3. Move this usable region from the mutator partition to the old collector partition
      _partitions.move_from_partition_to_partition(idx,
                                                   ShenandoahFreeSetPartitionId::Mutator,
                                                   ShenandoahFreeSetPartitionId::OldCollector, region_alloc_capacity);
      // Should have no effect on used, since flipped regions are trashed: zero used */
      // Transferred regions are not affiliated, because they are empty (trash)
      recompute_total_affiliated</* MutatorEmptiesChanged */ true, /* CollectorEmptiesChanged */ false,
                                 /* OldCollectorEmptiesChanged */ true, /* MutatorSizeChanged */ true,
                                 /* CollectorSizeChanged */ false, /* OldCollectorSizeChanged */ true,
                                 /* AffiliatedChangesAreYoungNeutral */ true, /* AffiliatedChangesAreGlobalNeutral */ true,
                                 /* UnaffiliatedChangesAreYoungNeutral */ false>();
      _partitions.assert_bounds();
      // 4. Do not adjust capacities for generations, we just swapped the regions that have already
      // been accounted for. However, we should adjust the evacuation reserves as those may have changed.
      shenandoah_assert_heaplocked();
      const size_t reserve = _heap->old_generation()->get_evacuation_reserve();
      _heap->old_generation()->set_evacuation_reserve(reserve - unusable_capacity + region_alloc_capacity);
      return true;
    }
  }

  // We can't take this region young because it has no free unaffiliated regions (transfer failed).
  return false;
}

void ShenandoahFreeSet::flip_to_gc(ShenandoahHeapRegion* r) {
  size_t idx = r->index();

  assert(_partitions.partition_id_matches(idx, ShenandoahFreeSetPartitionId::Mutator), "Should be in mutator view");
  assert(can_allocate_from(r), "Should not be allocated");

  size_t ac = alloc_capacity(r);
  _partitions.move_from_partition_to_partition(idx, ShenandoahFreeSetPartitionId::Mutator,
                                               ShenandoahFreeSetPartitionId::Collector, ac);
  recompute_total_used</* UsedByMutatorChanged */ true,
                       /* UsedByCollectorChanged */ false, /* UsedByOldCollectorChanged */ true>();
  // Transfer only affects unaffiliated regions, which stay in young
  recompute_total_affiliated</* MutatorEmptiesChanged */ true, /* CollectorEmptiesChanged */ true,
                             /* OldCollectorEmptiesChanged */ false, /* MutatorSizeChanged */ true,
                             /* CollectorSizeChanged */ true, /* OldCollectorSizeChanged */ false,
                             /* AffiliatedChangesAreYoungNeutral */ true, /* AffiliatedChangesAreGlobalNeutral */ true,
                             /* UnaffiliatedChangesAreYoungNeutral */ true>();
  _partitions.assert_bounds();
  // We do not ensure that the region is no longer trash, relying on try_allocate_in(), which always comes next,
  // to recycle trash before attempting to allocate anything in the region.
}

void ShenandoahFreeSet::clear() {
  clear_internal();
}

void ShenandoahFreeSet::clear_internal() {
  shenandoah_assert_heaplocked();
  _partitions.make_all_regions_unavailable();
  recompute_total_used</* UsedByMutatorChanged */ true,
                       /* UsedByCollectorChanged */ true, /* UsedByOldCollectorChanged */ true>();
  recompute_total_affiliated</* MutatorEmptiesChanged */ true, /* CollectorEmptiesChanged */ true,
                             /* OldCollectorEmptiesChanged */ true, /* MutatorSizeChanged */ true,
                             /* CollectorSizeChanged */ true, /* OldCollectorSizeChanged */ true,
                             /* AffiliatedChangesAreYoungNeutral */ true, /* AffiliatedChangesAreGlobalNeutral */ true,
                             /* UnaffiliatedChangesAreYoungNeutral */ true>();
  _alloc_bias_weight = 0;
  _partitions.set_bias_from_left_to_right(ShenandoahFreeSetPartitionId::Mutator, true);
  _partitions.set_bias_from_left_to_right(ShenandoahFreeSetPartitionId::Collector, false);
  _partitions.set_bias_from_left_to_right(ShenandoahFreeSetPartitionId::OldCollector, false);
}

void ShenandoahFreeSet::find_regions_with_alloc_capacity(size_t &young_trashed_regions, size_t &old_trashed_regions,
                                                         size_t &first_old_region, size_t &last_old_region,
                                                         size_t &old_region_count) {
  // This resets all state information, removing all regions from all sets.
  clear_internal();

  first_old_region = _heap->num_regions();
  last_old_region = 0;
  old_region_count = 0;
  old_trashed_regions = 0;
  young_trashed_regions = 0;

  size_t old_cset_regions = 0;
  size_t young_cset_regions = 0;

  size_t region_size_bytes = _partitions.region_size_bytes();
  size_t max_regions = _partitions.max();

  size_t mutator_leftmost = max_regions;
  size_t mutator_rightmost = 0;
  size_t mutator_leftmost_empty = max_regions;
  size_t mutator_rightmost_empty = 0;

  size_t old_collector_leftmost = max_regions;
  size_t old_collector_rightmost = 0;
  size_t old_collector_leftmost_empty = max_regions;
  size_t old_collector_rightmost_empty = 0;

  size_t mutator_empty = 0;
  size_t old_collector_empty = 0;

  // These two variables represent the total used within each partition, including humongous waste and retired regions
  size_t mutator_used = 0;
  size_t old_collector_used = 0;

  // These two variables represent memory that is wasted within humongous regions due to alignment padding
  size_t mutator_humongous_waste = 0;
  size_t old_collector_humongous_waste = 0;

  // These two variables track regions that have allocatable memory
  size_t mutator_regions = 0;
  size_t old_collector_regions = 0;

  // These two variables track regions that are not empty within each partition
  size_t affiliated_mutator_regions = 0;
  size_t affiliated_old_collector_regions = 0;

  // These two variables represent the total capacity of each partition, including retired regions
  size_t total_mutator_regions = 0;
  size_t total_old_collector_regions = 0;

  size_t num_regions = _heap->num_regions();
  for (size_t idx = 0; idx < num_regions; idx++) {
    ShenandoahHeapRegion* region = _heap->get_region(idx);
    if (region->is_trash()) {
      // Trashed regions represent regions that had been in the collection set (or may have been identified as immediate garbage)
      // but have not yet been "cleaned up".  The cset regions are not "trashed" until we have finished update refs.
      if (region->is_old()) {
        // We're going to place this region into the Mutator set.  We increment old_trashed_regions because this count represents
        // regions that the old generation is entitled to without any transfer from young.  We do not place this region into
        // the OldCollector partition at this time.  Instead, we let reserve_regions() decide whether to place this region
        // into the OldCollector partition.  Deferring the decision allows reserve_regions() to more effectively pack the
        // OldCollector regions into high-address memory.  We do not adjust capacities of old and young generations at this
        // time.  At the end of finish_rebuild(), the capacities are adjusted based on the results of reserve_regions().
        old_trashed_regions++;
      } else {
        assert(region->is_young(), "Trashed region should be old or young");
        young_trashed_regions++;
      }
    } else if (region->is_old()) {
      // We count humongous and regular regions as "old regions".  We do not count trashed regions that are old.  Those
      // are counted (above) as old_trashed_regions.
      old_region_count++;
      if (first_old_region > idx) {
        first_old_region = idx;
      }
      last_old_region = idx;
    }
    if (region->is_alloc_allowed() || region->is_trash()) {
      assert(!region->is_cset(), "Shouldn't be adding cset regions to the free set");

      // Do not add regions that would almost surely fail allocation
      size_t ac = alloc_capacity(region);
      if (ac >= PLAB::min_size() * HeapWordSize) {
        if (region->is_trash() || !region->is_old()) {
          // Both young and old (possibly immediately) collected regions (trashed) are placed into the Mutator set
          _partitions.raw_assign_membership(idx, ShenandoahFreeSetPartitionId::Mutator);
          if (idx < mutator_leftmost) {
            mutator_leftmost = idx;
          }
          if (idx > mutator_rightmost) {
            mutator_rightmost = idx;
          }
          if (ac == region_size_bytes) {
            mutator_empty++;
            if (idx < mutator_leftmost_empty) {
              mutator_leftmost_empty = idx;
            }
            if (idx > mutator_rightmost_empty) {
              mutator_rightmost_empty = idx;
            }
          } else {
            affiliated_mutator_regions++;
          }
          mutator_regions++;
          total_mutator_regions++;
          mutator_used += (region_size_bytes - ac);
        } else {
          // !region->is_trash() && region is_old()
          _partitions.raw_assign_membership(idx, ShenandoahFreeSetPartitionId::OldCollector);
          if (idx < old_collector_leftmost) {
            old_collector_leftmost = idx;
          }
          if (idx > old_collector_rightmost) {
            old_collector_rightmost = idx;
          }
          assert(ac != region_size_bytes, "Empty regions should be in mutator partition");
          affiliated_old_collector_regions++;
          old_collector_regions++;
          total_old_collector_regions++;
          old_collector_used += region_size_bytes - ac;
        }
      } else {
        // This region does not have enough free to be part of the free set.  Count all of its memory as used.
        assert(_partitions.membership(idx) == ShenandoahFreeSetPartitionId::NotFree, "Region should have been retired");
        if (region->is_old()) {
          old_collector_used += region_size_bytes;
          total_old_collector_regions++;
          affiliated_old_collector_regions++;
        } else {
          mutator_used += region_size_bytes;
          total_mutator_regions++;
          affiliated_mutator_regions++;
        }
      }
    } else {
      // This region does not allow allocation (it is retired or is humongous or is in cset).
      // Retired and humongous regions generally have no alloc capacity, but cset regions may have large alloc capacity.
      if (region->is_cset()) {
        if (region->is_old()) {
          old_cset_regions++;
        } else {
          young_cset_regions++;
        }
      } else {
        assert(_partitions.membership(idx) == ShenandoahFreeSetPartitionId::NotFree, "Region should have been retired");
        size_t humongous_waste_bytes = 0;
        if (region->is_humongous_start()) {
          // Since rebuild does not necessarily happen at a safepoint, a newly allocated humongous object may not have been
          // fully initialized.  Therefore, we cannot safely consult its header.
          ShenandoahHeapRegion* last_of_humongous_continuation = region;
          size_t next_idx;
          for (next_idx = idx + 1; next_idx < num_regions; next_idx++) {
            ShenandoahHeapRegion* humongous_cont_candidate = _heap->get_region(next_idx);
            if (!humongous_cont_candidate->is_humongous_continuation()) {
              break;
            }
            last_of_humongous_continuation = humongous_cont_candidate;
          }
          // For humongous regions, used() is established while holding the global heap lock so it is reliable here
          humongous_waste_bytes = ShenandoahHeapRegion::region_size_bytes() - last_of_humongous_continuation->used();
        }
        if (region->is_old()) {
          old_collector_used += region_size_bytes;
          total_old_collector_regions++;
          old_collector_humongous_waste += humongous_waste_bytes;
          affiliated_old_collector_regions++;
        } else {
          mutator_used += region_size_bytes;
          total_mutator_regions++;
          mutator_humongous_waste += humongous_waste_bytes;
          affiliated_mutator_regions++;
        }
      }
    }
  }
  // At the start of evacuation, the cset regions are not counted as part of Mutator or OldCollector partitions.

  // At the end of GC, when we rebuild rebuild freeset (which happens before we have recycled the collection set), we treat
  // all cset regions as part of capacity, as fully available, as unaffiliated.  We place trashed regions into the Mutator
  // partition.

  // No need to update generation sizes here.  These are the sizes already recognized by the generations.  These
  // adjustments allow the freeset tallies to match the generation tallies.

  log_debug(gc, free)("  At end of prep_to_rebuild, mutator_leftmost: %zu"
                      ", mutator_rightmost: %zu"
                      ", mutator_leftmost_empty: %zu"
                      ", mutator_rightmost_empty: %zu"
                      ", mutator_regions: %zu"
                      ", mutator_used: %zu",
                      mutator_leftmost, mutator_rightmost, mutator_leftmost_empty, mutator_rightmost_empty,
                      mutator_regions, mutator_used);
  log_debug(gc, free)("  old_collector_leftmost: %zu"
                      ", old_collector_rightmost: %zu"
                      ", old_collector_leftmost_empty: %zu"
                      ", old_collector_rightmost_empty: %zu"
                      ", old_collector_regions: %zu"
                      ", old_collector_used: %zu",
                      old_collector_leftmost, old_collector_rightmost, old_collector_leftmost_empty, old_collector_rightmost_empty,
                      old_collector_regions, old_collector_used);
  log_debug(gc, free)("  total_mutator_regions: %zu, total_old_collector_regions: %zu"
                      ", mutator_empty: %zu, old_collector_empty: %zu",
                      total_mutator_regions, total_old_collector_regions, mutator_empty, old_collector_empty);

  idx_t rightmost_idx = (mutator_leftmost == max_regions)? -1: (idx_t) mutator_rightmost;
  idx_t rightmost_empty_idx = (mutator_leftmost_empty == max_regions)? -1: (idx_t) mutator_rightmost_empty;

  _partitions.establish_mutator_intervals(mutator_leftmost, rightmost_idx, mutator_leftmost_empty, rightmost_empty_idx,
                                          total_mutator_regions + young_cset_regions, mutator_empty, mutator_regions,
                                          mutator_used + young_cset_regions * region_size_bytes, mutator_humongous_waste);
  rightmost_idx = (old_collector_leftmost == max_regions)? -1: (idx_t) old_collector_rightmost;
  rightmost_empty_idx = (old_collector_leftmost_empty == max_regions)? -1: (idx_t) old_collector_rightmost_empty;
  _partitions.establish_old_collector_intervals(old_collector_leftmost, rightmost_idx,
                                                old_collector_leftmost_empty, rightmost_empty_idx,
                                                total_old_collector_regions + old_cset_regions,
                                                old_collector_empty, old_collector_regions,
                                                old_collector_used + old_cset_regions * region_size_bytes,
                                                old_collector_humongous_waste);
  _total_humongous_waste = mutator_humongous_waste + old_collector_humongous_waste;
  _total_young_regions = total_mutator_regions + young_cset_regions;
  _total_global_regions = _total_young_regions + total_old_collector_regions + old_cset_regions;
  recompute_total_used</* UsedByMutatorChanged */ true,
                       /* UsedByCollectorChanged */ true, /* UsedByOldCollectorChanged */ true>();
  recompute_total_affiliated</* MutatorEmptiesChanged */ true, /* CollectorEmptiesChanged */ true,
                             /* OldCollectorEmptiesChanged */ true, /* MutatorSizeChanged */ true,
                             /* CollectorSizeChanged */ true, /* OldCollectorSizeChanged */ true,
                             /* AffiliatedChangesAreYoungNeutral */ false, /* AffiliatedChangesAreGlobalNeutral */ false,
                             /* UnaffiliatedChangesAreYoungNeutral */ false>();
  _partitions.assert_bounds();
#ifdef ASSERT
  if (_heap->mode()->is_generational()) {
    assert(young_affiliated_regions() == _heap->young_generation()->get_affiliated_region_count(), "sanity");
  } else {
    assert(young_affiliated_regions() == _heap->global_generation()->get_affiliated_region_count(), "sanity");
  }
#endif
  log_debug(gc, free)("  After find_regions_with_alloc_capacity(), Mutator range [%zd, %zd],"
                      "  Old Collector range [%zd, %zd]",
                      _partitions.leftmost(ShenandoahFreeSetPartitionId::Mutator),
                      _partitions.rightmost(ShenandoahFreeSetPartitionId::Mutator),
                      _partitions.leftmost(ShenandoahFreeSetPartitionId::OldCollector),
                      _partitions.rightmost(ShenandoahFreeSetPartitionId::OldCollector));
}

void ShenandoahFreeSet::transfer_humongous_regions_from_mutator_to_old_collector(size_t xfer_regions,
                                                                                 size_t humongous_waste_bytes) {
  shenandoah_assert_heaplocked();
  size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();

  _partitions.decrease_humongous_waste(ShenandoahFreeSetPartitionId::Mutator, humongous_waste_bytes);
  _partitions.decrease_used(ShenandoahFreeSetPartitionId::Mutator, xfer_regions * region_size_bytes);
  _partitions.decrease_capacity(ShenandoahFreeSetPartitionId::Mutator, xfer_regions * region_size_bytes);

  _partitions.increase_capacity(ShenandoahFreeSetPartitionId::OldCollector, xfer_regions * region_size_bytes);
  _partitions.increase_humongous_waste(ShenandoahFreeSetPartitionId::OldCollector, humongous_waste_bytes);
  _partitions.increase_used(ShenandoahFreeSetPartitionId::OldCollector, xfer_regions * region_size_bytes);

  // _total_humongous_waste, _total_global_regions are unaffected by transfer
  _total_young_regions -= xfer_regions;
  recompute_total_young_used</* UsedByMutatorChanged */ true, /* UsedByCollectorChanged */ false>();
  recompute_total_old_used</* UsedByOldCollectorChanged */ true>();
  recompute_total_affiliated</* MutatorEmptiesChanged */ false, /* CollectorEmptiesChanged */ false,
                             /* OldCollectorEmptiesChanged */ false, /* MutatorSizeChanged */ true,
                             /* CollectorSizeChanged */ false, /* OldCollectorSizeChanged */ true,
                             /* AffiliatedChangesAreYoungNeutral */ false, /* AffiliatedChangesAreGlobalNeutral */ true,
                             /* UnaffiliatedChangesAreYoungNeutral */ true>();
  _partitions.assert_bounds();
  // global_used is unaffected by this transfer

  // No need to adjust ranges because humongous regions are not allocatable
}

void ShenandoahFreeSet::transfer_empty_regions_from_to(ShenandoahFreeSetPartitionId source,
                                                       ShenandoahFreeSetPartitionId dest,
                                                       size_t num_regions) {
  assert(dest != source, "precondition");
  shenandoah_assert_heaplocked();
  const size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();
  size_t transferred_regions = 0;
  size_t used_transfer = 0;
  idx_t source_low_idx = _partitions.max();
  idx_t source_high_idx = -1;
  idx_t dest_low_idx = _partitions.max();
  idx_t dest_high_idx = -1;
  ShenandoahLeftRightIterator iterator(&_partitions, source, true);
  for (idx_t idx = iterator.current(); transferred_regions < num_regions && iterator.has_next(); idx = iterator.next()) {
    // Note: can_allocate_from() denotes that region is entirely empty
    if (can_allocate_from(idx)) {
      if (idx < source_low_idx) {
        source_low_idx = idx;
      }
      if (idx > source_high_idx) {
        source_high_idx = idx;
      }
      if (idx < dest_low_idx) {
        dest_low_idx = idx;
      }
      if (idx > dest_high_idx) {
        dest_high_idx = idx;
      }
      used_transfer += _partitions.move_from_partition_to_partition_with_deferred_accounting(idx, source, dest, region_size_bytes);
      transferred_regions++;
    }
  }

  // All transferred regions are empty.
  assert(used_transfer == 0, "empty regions should have no used");
  _partitions.expand_interval_if_range_modifies_either_boundary(dest, dest_low_idx,
                                                                dest_high_idx, dest_low_idx, dest_high_idx);
  _partitions.shrink_interval_if_range_modifies_either_boundary(source, source_low_idx, source_high_idx,
                                                                transferred_regions);

  _partitions.decrease_region_counts(source, transferred_regions);
  _partitions.decrease_empty_region_counts(source, transferred_regions);
  _partitions.decrease_capacity(source, transferred_regions * region_size_bytes);

  _partitions.increase_capacity(dest, transferred_regions * region_size_bytes);
  _partitions.increase_region_counts(dest, transferred_regions);
  _partitions.increase_empty_region_counts(dest, transferred_regions);

  // Since only empty regions are transferred, no need to recompute_total_used()
  if (source == ShenandoahFreeSetPartitionId::OldCollector) {
    assert((dest == ShenandoahFreeSetPartitionId::Collector) || (dest == ShenandoahFreeSetPartitionId::Mutator), "sanity");
    _total_young_regions += transferred_regions;
    recompute_total_affiliated</* MutatorEmptiesChanged */ true, /* CollectorEmptiesChanged */ true,
                               /* OldCollectorEmptiesChanged */ true, /* MutatorSizeChanged */ true,
                               /* CollectorSizeChanged */ true, /* OldCollectorSizeChanged */ true,
                               /* AffiliatedChangesAreYoungNeutral */ true, /* AffiliatedChangesAreGlobalNeutral */ true,
                               /* UnaffiliatedChangesAreYoungNeutral */ false>();
  } else {
    assert((source == ShenandoahFreeSetPartitionId::Collector) || (source == ShenandoahFreeSetPartitionId::Mutator), "sanity");
    if (dest == ShenandoahFreeSetPartitionId::OldCollector) {
      _total_young_regions -= transferred_regions;
      recompute_total_affiliated</* MutatorEmptiesChanged */ true, /* CollectorEmptiesChanged */ true,
                                 /* OldCollectorEmptiesChanged */ true, /* MutatorSizeChanged */ true,
                                 /* CollectorSizeChanged */ true, /* OldCollectorSizeChanged */ true,
                                 /* AffiliatedChangesAreYoungNeutral */ true, /* AffiliatedChangesAreGlobalNeutral */ true,
                                 /* UnaffiliatedChangesAreYoungNeutral */ false>();
    } else {
      assert((dest == ShenandoahFreeSetPartitionId::Collector) || (dest == ShenandoahFreeSetPartitionId::Mutator), "sanity");
      // No adjustments to total_young_regions if transferring within young
      recompute_total_affiliated</* MutatorEmptiesChanged */ true, /* CollectorEmptiesChanged */ true,
                                 /* OldCollectorEmptiesChanged */ false, /* MutatorSizeChanged */ true,
                                 /* CollectorSizeChanged */ true, /* OldCollectorSizeChanged */ false,
                                 /* AffiliatedChangesAreYoungNeutral */ true, /* AffiliatedChangesAreGlobalNeutral */ true,
                                 /* UnaffiliatedChangesAreYoungNeutral */ true>();
    }
  }
  _partitions.assert_bounds();
}

// Returns number of regions transferred, adds transferred bytes to var argument bytes_transferred
size_t ShenandoahFreeSet::transfer_empty_regions_from_collector_set_to_mutator_set(ShenandoahFreeSetPartitionId which_collector,
                                                                                   size_t max_xfer_regions,
                                                                                   size_t& bytes_transferred) {
  shenandoah_assert_heaplocked();
  const size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();
  size_t transferred_regions = 0;
  size_t used_transfer = 0;
  idx_t collector_low_idx = _partitions.max();
  idx_t collector_high_idx = -1;
  idx_t mutator_low_idx = _partitions.max();
  idx_t mutator_high_idx = -1;
  ShenandoahLeftRightIterator iterator(&_partitions, which_collector, true);
  for (idx_t idx = iterator.current(); transferred_regions < max_xfer_regions && iterator.has_next(); idx = iterator.next()) {
    // Note: can_allocate_from() denotes that region is entirely empty
    if (can_allocate_from(idx)) {
      if (idx < collector_low_idx) {
        collector_low_idx = idx;
      }
      if (idx > collector_high_idx) {
        collector_high_idx = idx;
      }
      if (idx < mutator_low_idx) {
        mutator_low_idx = idx;
      }
      if (idx > mutator_high_idx) {
        mutator_high_idx = idx;
      }
      used_transfer += _partitions.move_from_partition_to_partition_with_deferred_accounting(idx, which_collector,
                                                                                             ShenandoahFreeSetPartitionId::Mutator,
                                                                                             region_size_bytes);
      transferred_regions++;
      bytes_transferred += region_size_bytes;
    }
  }
  // All transferred regions are empty.
  assert(used_transfer == 0, "empty regions should have no used");
  _partitions.expand_interval_if_range_modifies_either_boundary(ShenandoahFreeSetPartitionId::Mutator, mutator_low_idx,
                                                                mutator_high_idx, mutator_low_idx, mutator_high_idx);
  _partitions.shrink_interval_if_range_modifies_either_boundary(which_collector, collector_low_idx, collector_high_idx,
                                                                transferred_regions);

  _partitions.decrease_region_counts(which_collector, transferred_regions);
  _partitions.decrease_empty_region_counts(which_collector, transferred_regions);
  _partitions.decrease_capacity(which_collector, transferred_regions * region_size_bytes);

  _partitions.increase_capacity(ShenandoahFreeSetPartitionId::Mutator, transferred_regions * region_size_bytes);
  _partitions.increase_region_counts(ShenandoahFreeSetPartitionId::Mutator, transferred_regions);
  _partitions.increase_empty_region_counts(ShenandoahFreeSetPartitionId::Mutator, transferred_regions);

  if (which_collector == ShenandoahFreeSetPartitionId::OldCollector) {
    _total_young_regions += transferred_regions;
    recompute_total_affiliated</* MutatorEmptiesChanged */ true, /* CollectorEmptiesChanged */ false,
                               /* OldCollectorEmptiesChanged */ true, /* MutatorSizeChanged */ true,
                               /* CollectorSizeChanged */ false, /* OldCollectorSizeChanged */ true,
                               /* AffiliatedChangesAreYoungNeutral */ true, /* AffiliatedChangesAreGlobalNeutral */ true,
                               /* UnaffiliatedChangesAreYoungNeutral */ false>();
  } else {
    recompute_total_affiliated</* MutatorEmptiesChanged */ true, /* CollectorEmptiesChanged */ true,
                               /* OldCollectorEmptiesChanged */ false, /* MutatorSizeChanged */ true,
                               /* CollectorSizeChanged */ true, /* OldCollectorSizeChanged */ false,
                               /* AffiliatedChangesAreYoungNeutral */ true, /* AffiliatedChangesAreGlobalNeutral */ true,
                               /* UnaffiliatedChangesAreYoungNeutral */ true>();
  }
  _partitions.assert_bounds();
  return transferred_regions;
}

// Returns number of regions transferred, adds transferred bytes to var argument bytes_transferred
size_t ShenandoahFreeSet::
transfer_non_empty_regions_from_collector_set_to_mutator_set(ShenandoahFreeSetPartitionId which_collector,
                                                             size_t max_xfer_regions, size_t& bytes_transferred) {
  shenandoah_assert_heaplocked();
  size_t region_size_bytes = _partitions.region_size_bytes();
  size_t transferred_regions = 0;
  size_t used_transfer = 0;
  idx_t collector_low_idx = _partitions.max();
  idx_t collector_high_idx = -1;
  idx_t mutator_low_idx = _partitions.max();
  idx_t mutator_high_idx = -1;

  ShenandoahLeftRightIterator iterator(&_partitions, which_collector, false);
  for (idx_t idx = iterator.current(); transferred_regions < max_xfer_regions && iterator.has_next(); idx = iterator.next()) {
    size_t ac = alloc_capacity(idx);
    if (ac > 0) {
      if (idx < collector_low_idx) {
        collector_low_idx = idx;
      }
      if (idx > collector_high_idx) {
        collector_high_idx = idx;
      }
      if (idx < mutator_low_idx) {
        mutator_low_idx = idx;
      }
      if (idx > mutator_high_idx) {
        mutator_high_idx = idx;
      }
      assert (ac < region_size_bytes, "Move empty regions with different function");
      used_transfer += _partitions.move_from_partition_to_partition_with_deferred_accounting(idx, which_collector,
                                                                                             ShenandoahFreeSetPartitionId::Mutator,
                                                                                             ac);
      transferred_regions++;
      bytes_transferred += ac;
    }
  }
  // _empty_region_counts is unaffected, because we transfer only non-empty regions here.

  _partitions.decrease_used(which_collector, used_transfer);
  _partitions.expand_interval_if_range_modifies_either_boundary(ShenandoahFreeSetPartitionId::Mutator,
                                                                mutator_low_idx, mutator_high_idx, _partitions.max(), -1);
  _partitions.shrink_interval_if_range_modifies_either_boundary(which_collector, collector_low_idx, collector_high_idx,
                                                                transferred_regions);

  _partitions.decrease_region_counts(which_collector, transferred_regions);
  _partitions.decrease_capacity(which_collector, transferred_regions * region_size_bytes);
  _partitions.increase_capacity(ShenandoahFreeSetPartitionId::Mutator, transferred_regions * region_size_bytes);
  _partitions.increase_region_counts(ShenandoahFreeSetPartitionId::Mutator, transferred_regions);
  _partitions.increase_used(ShenandoahFreeSetPartitionId::Mutator, used_transfer);

  if (which_collector == ShenandoahFreeSetPartitionId::OldCollector) {
    _total_young_regions += transferred_regions;
  }
  // _total_global_regions unaffected by transfer
  recompute_total_used</* UsedByMutatorChanged */ true,
                       /* UsedByCollectorChanged */ true, /* UsedByOldCollectorChanged */ true>();
  // All transfers are affiliated
  if (which_collector == ShenandoahFreeSetPartitionId::OldCollector) {
    recompute_total_affiliated</* MutatorEmptiesChanged */ true, /* CollectorEmptiesChanged */ false,
                               /* OldCollectorEmptiesChanged */ true, /* MutatorSizeChanged */ true,
                               /* CollectorSizeChanged */ false, /* OldCollectorSizeChanged */ true,
                               /* AffiliatedChangesAreYoungNeutral */ false, /* AffiliatedChangesAreGlobalNeutral */ true,
                               /* UnaffiliatedChangesAreYoungNeutral */ true>();
  } else {
    recompute_total_affiliated</* MutatorEmptiesChanged */ true, /* CollecteorEmptiesChanged */true,
                               /* OldCollectorEmptiesChanged */ false, /* MutatorSizeChanged */ true,
                               /* CollectorSizeChanged */ true, /* OldCollectorSizeChanged */ false,
                               /* AffiliatedChangesAreYoungNeutral */ true, /* AffiliatedChangesAreGlobalNeutral */ true,
                               /* UnaffiliatedChangesAreYoungNeutral */ true>();
  }
  _partitions.assert_bounds();
  return transferred_regions;
}

void ShenandoahFreeSet::move_regions_from_collector_to_mutator(size_t max_xfer_regions) {
  size_t collector_xfer = 0;
  size_t old_collector_xfer = 0;

  // Process empty regions within the Collector free partition
  if ((max_xfer_regions > 0) &&
      (_partitions.leftmost_empty(ShenandoahFreeSetPartitionId::Collector)
       <= _partitions.rightmost_empty(ShenandoahFreeSetPartitionId::Collector))) {
    ShenandoahHeapLocker locker(_heap->lock());
    max_xfer_regions -=
      transfer_empty_regions_from_collector_set_to_mutator_set(ShenandoahFreeSetPartitionId::Collector, max_xfer_regions,
                                                               collector_xfer);
  }

  // Process empty regions within the OldCollector free partition
  if ((max_xfer_regions > 0) &&
      (_partitions.leftmost_empty(ShenandoahFreeSetPartitionId::OldCollector)
       <= _partitions.rightmost_empty(ShenandoahFreeSetPartitionId::OldCollector))) {
    ShenandoahHeapLocker locker(_heap->lock());
    size_t old_collector_regions =
      transfer_empty_regions_from_collector_set_to_mutator_set(ShenandoahFreeSetPartitionId::OldCollector, max_xfer_regions,
                                                               old_collector_xfer);
    max_xfer_regions -= old_collector_regions;
  }

  // If there are any non-empty regions within Collector partition, we can also move them to the Mutator free partition
  if ((max_xfer_regions > 0) && (_partitions.leftmost(ShenandoahFreeSetPartitionId::Collector)
                                 <= _partitions.rightmost(ShenandoahFreeSetPartitionId::Collector))) {
    ShenandoahHeapLocker locker(_heap->lock());
    max_xfer_regions -=
      transfer_non_empty_regions_from_collector_set_to_mutator_set(ShenandoahFreeSetPartitionId::Collector, max_xfer_regions,
                                                                   collector_xfer);
  }

  size_t total_xfer = collector_xfer + old_collector_xfer;
  log_info(gc, ergo)("At start of update refs, moving %zu%s to Mutator free set from Collector Reserve ("
                     "%zu%s) and from Old Collector Reserve (%zu%s)",
                     byte_size_in_proper_unit(total_xfer), proper_unit_for_byte_size(total_xfer),
                     byte_size_in_proper_unit(collector_xfer), proper_unit_for_byte_size(collector_xfer),
                     byte_size_in_proper_unit(old_collector_xfer), proper_unit_for_byte_size(old_collector_xfer));
}

// Overwrite arguments to represent the amount of memory in each generation that is about to be recycled
void ShenandoahFreeSet::prepare_to_rebuild(size_t &young_trashed_regions, size_t &old_trashed_regions,
                                           size_t &first_old_region, size_t &last_old_region, size_t &old_region_count) {
  shenandoah_assert_heaplocked();
  assert(rebuild_lock() != nullptr, "sanity");
  rebuild_lock()->lock(false);
  // This resets all state information, removing all regions from all sets.
  clear();
  log_debug(gc, free)("Rebuilding FreeSet");

  // This places regions that have alloc_capacity into the old_collector set if they identify as is_old() or the
  // mutator set otherwise.  All trashed (cset) regions are affiliated young and placed in mutator set.
  find_regions_with_alloc_capacity(young_trashed_regions, old_trashed_regions,
                                   first_old_region, last_old_region, old_region_count);
}


void ShenandoahFreeSet::finish_rebuild(size_t young_cset_regions, size_t old_cset_regions, size_t old_region_count) {
  shenandoah_assert_heaplocked();
  size_t young_reserve(0), old_reserve(0);

  if (_heap->mode()->is_generational()) {
    compute_young_and_old_reserves(young_cset_regions, old_cset_regions, young_reserve, old_reserve);
  } else {
    young_reserve = (_heap->max_capacity() / 100) * ShenandoahEvacReserve;
    old_reserve = 0;
  }

  // Move some of the mutator regions into the Collector and OldCollector partitions in order to satisfy
  // young_reserve and old_reserve.
  size_t young_used_regions, old_used_regions, young_used_bytes, old_used_bytes;
  reserve_regions(young_reserve, old_reserve, old_region_count, young_used_regions, old_used_regions,
                  young_used_bytes, old_used_bytes);
  _total_young_regions = _heap->num_regions() - old_region_count;
  _total_global_regions = _heap->num_regions();
  establish_old_collector_alloc_bias();

  // Release the rebuild lock now.  What remains in this function is read-only
  rebuild_lock()->unlock();
  _partitions.assert_bounds();
  log_status();
  if (_heap->mode()->is_generational()) {
    // Clear the region balance until it is adjusted in preparation for a subsequent GC cycle.
    _heap->old_generation()->set_region_balance(0);
  }
}


// Reduce old reserve (when there are insufficient resources to satisfy the original request).
void ShenandoahFreeSet::reduce_old_reserve(size_t adjusted_old_reserve, size_t requested_old_reserve) {
  ShenandoahOldGeneration* const old_generation = _heap->old_generation();
  size_t requested_promoted_reserve = old_generation->get_promoted_reserve();
  size_t requested_old_evac_reserve = old_generation->get_evacuation_reserve();
  assert(adjusted_old_reserve < requested_old_reserve, "Only allow reduction");
  assert(requested_promoted_reserve + requested_old_evac_reserve >= adjusted_old_reserve, "Sanity");
  size_t delta = requested_old_reserve - adjusted_old_reserve;

  if (requested_promoted_reserve >= delta) {
    requested_promoted_reserve -= delta;
    old_generation->set_promoted_reserve(requested_promoted_reserve);
  } else {
    delta -= requested_promoted_reserve;
    requested_promoted_reserve = 0;
    requested_old_evac_reserve -= delta;
    old_generation->set_promoted_reserve(requested_promoted_reserve);
    old_generation->set_evacuation_reserve(requested_old_evac_reserve);
  }
}

// Reduce young reserve (when there are insufficient resources to satisfy the original request).
void ShenandoahFreeSet::reduce_young_reserve(size_t adjusted_young_reserve, size_t requested_young_reserve) {
  ShenandoahYoungGeneration* const young_generation = _heap->young_generation();
  assert(adjusted_young_reserve < requested_young_reserve, "Only allow reduction");
  young_generation->set_evacuation_reserve(adjusted_young_reserve);
}

/**
 * Set young_reserve_result and old_reserve_result to the number of bytes that we desire to set aside to hold the
 * results of evacuation to young and old collector spaces respectively during the next evacuation phase.  Overwrite
 * old_generation region balance in case the original value is incompatible with the current reality.
 *
 * These values are determined by how much memory is currently available within each generation, which is
 * represented by:
 *  1. Memory currently available within old and young
 *  2. Trashed regions currently residing in young and old, which will become available momentarily
 *  3. The value of old_generation->get_region_balance() which represents the number of regions that we plan
 *     to transfer from old generation to young generation.  Prior to each invocation of compute_young_and_old_reserves(),
 *     this value should computed by ShenandoahGenerationalHeap::compute_old_generation_balance().
 */
void ShenandoahFreeSet::compute_young_and_old_reserves(size_t young_trashed_regions, size_t old_trashed_regions,
                                                       size_t& young_reserve_result, size_t& old_reserve_result) const {
  shenandoah_assert_generational();
  shenandoah_assert_heaplocked();
  const size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();
  ShenandoahOldGeneration* const old_generation = _heap->old_generation();
  size_t old_available = old_generation->available();
  size_t old_unaffiliated_regions = old_generation->free_unaffiliated_regions();
  ShenandoahYoungGeneration* const young_generation = _heap->young_generation();
  size_t young_capacity = young_generation->max_capacity();
  size_t young_unaffiliated_regions = young_generation->free_unaffiliated_regions();

  // Add in the regions we anticipate to be freed by evacuation of the collection set
  old_unaffiliated_regions += old_trashed_regions;
  old_available += old_trashed_regions * region_size_bytes;
  young_unaffiliated_regions += young_trashed_regions;

  assert(young_capacity >= young_generation->used(),
         "Young capacity (%zu) must exceed used (%zu)", young_capacity, young_generation->used());

  size_t young_available = young_capacity - young_generation->used();
  young_available += young_trashed_regions * region_size_bytes;

  assert(young_available >= young_unaffiliated_regions * region_size_bytes, "sanity");
  assert(old_available >= old_unaffiliated_regions * region_size_bytes, "sanity");

  // Consult old-region balance to make adjustments to current generation capacities and availability.
  // The generation region transfers take place after we rebuild.  old_region_balance represents number of regions
  // to transfer from old to young.
  ssize_t old_region_balance = old_generation->get_region_balance();
  if (old_region_balance != 0) {
#ifdef ASSERT
    if (old_region_balance > 0) {
      assert(old_region_balance <= checked_cast<ssize_t>(old_unaffiliated_regions),
             "Cannot transfer %zd regions that are affiliated (old_trashed: %zu, old_unaffiliated: %zu)",
             old_region_balance, old_trashed_regions, old_unaffiliated_regions);
    } else {
      assert(0 - old_region_balance <= checked_cast<ssize_t>(young_unaffiliated_regions),
             "Cannot transfer regions that are affiliated");
    }
#endif

    ssize_t xfer_bytes = old_region_balance * checked_cast<ssize_t>(region_size_bytes);
    old_available -= xfer_bytes;
    old_unaffiliated_regions -= old_region_balance;
    young_available += xfer_bytes;
    young_capacity += xfer_bytes;
    young_unaffiliated_regions += old_region_balance;
  }

  // All allocations taken from the old collector set are performed by GC, generally using PLABs for both
  // promotions and evacuations.  The partition between which old memory is reserved for evacuation and
  // which is reserved for promotion is enforced using thread-local variables that prescribe intentions for
  // each PLAB's available memory.
  const size_t promoted_reserve = old_generation->get_promoted_reserve();
  const size_t old_evac_reserve = old_generation->get_evacuation_reserve();
  young_reserve_result = young_generation->get_evacuation_reserve();
  old_reserve_result = promoted_reserve + old_evac_reserve;
  assert(old_reserve_result + young_reserve_result <= old_available + young_available,
         "Cannot reserve (%zu + %zu + %zu) more than is available: %zu + %zu",
         promoted_reserve, old_evac_reserve, young_reserve_result, old_available, young_available);

  // Old available regions that have less than PLAB::min_size() of available memory are not placed into the OldCollector
  // free set.  Because of this, old_available may not have enough memory to represent the intended reserve.  Adjust
  // the reserve downward to account for this possibility. This loss is part of the reason why the original budget
  // was adjusted with ShenandoahOldEvacWaste and ShenandoahOldPromoWaste multipliers.
  if (old_reserve_result >
      _partitions.available_in(ShenandoahFreeSetPartitionId::OldCollector) + old_unaffiliated_regions * region_size_bytes) {
    old_reserve_result =
      _partitions.available_in(ShenandoahFreeSetPartitionId::OldCollector) + old_unaffiliated_regions * region_size_bytes;
  }

  if (young_reserve_result > young_unaffiliated_regions * region_size_bytes) {
    young_reserve_result = young_unaffiliated_regions * region_size_bytes;
  }
}

// Having placed all regions that have allocation capacity into the mutator set if they identify as is_young()
// or into the old collector set if they identify as is_old(), move some of these regions from the mutator set
// into the collector set or old collector set in order to assure that the memory available for allocations within
// the collector set is at least to_reserve and the memory available for allocations within the old collector set
// is at least to_reserve_old.
void ShenandoahFreeSet::reserve_regions(size_t to_reserve, size_t to_reserve_old, size_t &old_region_count,
                                        size_t &young_used_regions, size_t &old_used_regions,
                                        size_t &young_used_bytes, size_t &old_used_bytes) {
  const size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();

  young_used_regions = 0;
  old_used_regions = 0;
  young_used_bytes = 0;
  old_used_bytes = 0;

  idx_t mutator_low_idx = _partitions.max();
  idx_t mutator_high_idx = -1;
  idx_t mutator_empty_low_idx = _partitions.max();
  idx_t mutator_empty_high_idx = -1;

  idx_t collector_low_idx = _partitions.max();
  idx_t collector_high_idx = -1;
  idx_t collector_empty_low_idx = _partitions.max();
  idx_t collector_empty_high_idx = -1;

  idx_t old_collector_low_idx = _partitions.max();
  idx_t old_collector_high_idx = -1;
  idx_t old_collector_empty_low_idx = _partitions.max();
  idx_t old_collector_empty_high_idx = -1;

  size_t used_to_collector = 0;
  size_t used_to_old_collector = 0;
  size_t regions_to_collector = 0;
  size_t regions_to_old_collector = 0;
  size_t empty_regions_to_collector = 0;
  size_t empty_regions_to_old_collector = 0;

  size_t old_collector_available = _partitions.available_in(ShenandoahFreeSetPartitionId::OldCollector);;
  size_t collector_available = _partitions.available_in(ShenandoahFreeSetPartitionId::Collector);

  for (size_t i = _heap->num_regions(); i > 0; i--) {
    idx_t idx = i - 1;
    ShenandoahHeapRegion* r = _heap->get_region(idx);
    if (_partitions.in_free_set(ShenandoahFreeSetPartitionId::Mutator, idx)) {
      // Note: trashed regions have region_size_bytes alloc capacity.
      size_t ac = alloc_capacity(r);
      assert (ac > 0, "Membership in free set implies has capacity");
      assert (!r->is_old() || r->is_trash(), "Except for trash, mutator_is_free regions should not be affiliated OLD");

      bool move_to_old_collector = old_collector_available < to_reserve_old;
      bool move_to_collector = collector_available < to_reserve;

      if (move_to_old_collector) {
        // We give priority to OldCollector partition because we desire to pack OldCollector regions into higher
        // addresses than Collector regions.  Presumably, OldCollector regions are more "stable" and less likely to
        // be collected in the near future.
        if (r->is_trash() || !r->is_affiliated()) {
          // OLD regions that have available memory are already in the old_collector free set.
          assert(r->is_empty() || r->is_trash(), "Not affiliated implies region %zu is empty", r->index());
          if (idx < old_collector_low_idx) {
            old_collector_low_idx = idx;
          }
          if (idx > old_collector_high_idx) {
            old_collector_high_idx = idx;
          }
          if (idx < old_collector_empty_low_idx) {
            old_collector_empty_low_idx = idx;
          }
          if (idx > old_collector_empty_high_idx) {
            old_collector_empty_high_idx = idx;
          }
          used_to_old_collector +=
            _partitions.move_from_partition_to_partition_with_deferred_accounting(idx, ShenandoahFreeSetPartitionId::Mutator,
                                                                                  ShenandoahFreeSetPartitionId::OldCollector, ac);
          old_collector_available += ac;
          regions_to_old_collector++;
          empty_regions_to_old_collector++;

          log_trace(gc, free)("  Shifting region %zu from mutator_free to old_collector_free", idx);
          log_trace(gc, free)("  Shifted Mutator range [%zd, %zd],"
                              "  Old Collector range [%zd, %zd]",
                              _partitions.leftmost(ShenandoahFreeSetPartitionId::Mutator),
                              _partitions.rightmost(ShenandoahFreeSetPartitionId::Mutator),
                              _partitions.leftmost(ShenandoahFreeSetPartitionId::OldCollector),
                              _partitions.rightmost(ShenandoahFreeSetPartitionId::OldCollector));
          old_region_count++;
          continue;
        }
      }

      if (move_to_collector) {
        // Note: In a previous implementation, regions were only placed into the survivor space (collector_is_free) if
        // they were entirely empty.  This has the effect of causing new Mutator allocation to reside next to objects
        // that have already survived at least one GC, mixing ephemeral with longer-lived objects in the same region.
        // Any objects that have survived a GC are less likely to immediately become garbage, so a region that contains
        // survivor objects is less likely to be selected for the collection set.  This alternative implementation allows
        // survivor regions to continue accumulating other survivor objects, and makes it more likely that ephemeral objects
        // occupy regions comprised entirely of ephemeral objects.  These regions are highly likely to be included in the next
        // collection set, and they are easily evacuated because they have low density of live objects.
        if (idx < collector_low_idx) {
          collector_low_idx = idx;
        }
        if (idx > collector_high_idx) {
          collector_high_idx = idx;
        }
        if (ac == region_size_bytes) {
          if (idx < collector_empty_low_idx) {
            collector_empty_low_idx = idx;
          }
          if (idx > collector_empty_high_idx) {
            collector_empty_high_idx = idx;
          }
          empty_regions_to_collector++;
        }
        used_to_collector +=
          _partitions.move_from_partition_to_partition_with_deferred_accounting(idx, ShenandoahFreeSetPartitionId::Mutator,
                                                                                ShenandoahFreeSetPartitionId::Collector, ac);
        collector_available += ac;
        regions_to_collector++;
        if (ac != region_size_bytes) {
          young_used_regions++;
          young_used_bytes = region_size_bytes - ac;
        }

        log_trace(gc, free)("  Shifting region %zu from mutator_free to collector_free", idx);
        log_trace(gc, free)("  Shifted Mutator range [%zd, %zd],"
                            "  Collector range [%zd, %zd]",
                            _partitions.leftmost(ShenandoahFreeSetPartitionId::Mutator),
                            _partitions.rightmost(ShenandoahFreeSetPartitionId::Mutator),
                            _partitions.leftmost(ShenandoahFreeSetPartitionId::Collector),
                            _partitions.rightmost(ShenandoahFreeSetPartitionId::Collector));
        continue;
      }

      // Mutator region is not moved to Collector or OldCollector. Still, do the accounting.
      if (idx < mutator_low_idx) {
        mutator_low_idx = idx;
      }
      if (idx > mutator_high_idx) {
        mutator_high_idx = idx;
      }
      if ((ac == region_size_bytes) && (idx < mutator_empty_low_idx)) {
        mutator_empty_low_idx = idx;
      }
      if ((ac == region_size_bytes) && (idx > mutator_empty_high_idx)) {
        mutator_empty_high_idx = idx;
      }
      if (ac != region_size_bytes) {
        young_used_regions++;
        young_used_bytes += region_size_bytes - ac;
      }
    } else {
      // Region is not in Mutator partition. Do the accounting.
      ShenandoahFreeSetPartitionId p = _partitions.membership(idx);
      size_t ac = alloc_capacity(r);
      assert(ac != region_size_bytes, "Empty regions should be in Mutator partion at entry to reserve_regions");
      assert(p != ShenandoahFreeSetPartitionId::Collector, "Collector regions must be converted from Mutator regions");
      if (p == ShenandoahFreeSetPartitionId::OldCollector) {
        assert(!r->is_empty(), "Empty regions should be in Mutator partition at entry to reserve_regions");
        old_used_regions++;
        old_used_bytes = region_size_bytes - ac;
        // This region is within the range for OldCollector partition, as established by find_regions_with_alloc_capacity()
        assert((_partitions.leftmost(ShenandoahFreeSetPartitionId::OldCollector) <= idx) &&
               (_partitions.rightmost(ShenandoahFreeSetPartitionId::OldCollector) >= idx),
               "find_regions_with_alloc_capacity() should have established this is in range");
      } else {
        assert(p == ShenandoahFreeSetPartitionId::NotFree, "sanity");
        // This region has been retired
        if (r->is_old()) {
          old_used_regions++;
          old_used_bytes += region_size_bytes - ac;
        } else {
          assert(r->is_young(), "Retired region should be old or young");
          young_used_regions++;
          young_used_bytes += region_size_bytes - ac;
        }
      }
    }
  }

  _partitions.decrease_used(ShenandoahFreeSetPartitionId::Mutator, used_to_old_collector + used_to_collector);
  _partitions.decrease_region_counts(ShenandoahFreeSetPartitionId::Mutator, regions_to_old_collector + regions_to_collector);
  _partitions.decrease_empty_region_counts(ShenandoahFreeSetPartitionId::Mutator,
                                           empty_regions_to_old_collector + empty_regions_to_collector);
  // decrease_capacity() also decreases available
  _partitions.decrease_capacity(ShenandoahFreeSetPartitionId::Mutator,
                                (regions_to_old_collector + regions_to_collector) * region_size_bytes);
  // increase_capacity() also increases available
  _partitions.increase_capacity(ShenandoahFreeSetPartitionId::Collector, regions_to_collector * region_size_bytes);
  _partitions.increase_region_counts(ShenandoahFreeSetPartitionId::Collector, regions_to_collector);
  _partitions.increase_empty_region_counts(ShenandoahFreeSetPartitionId::Collector, empty_regions_to_collector);
  // increase_capacity() also increases available
  _partitions.increase_capacity(ShenandoahFreeSetPartitionId::OldCollector, regions_to_old_collector * region_size_bytes);
  _partitions.increase_region_counts(ShenandoahFreeSetPartitionId::OldCollector, regions_to_old_collector);
  _partitions.increase_empty_region_counts(ShenandoahFreeSetPartitionId::OldCollector, empty_regions_to_old_collector);

  if (used_to_collector > 0) {
    _partitions.increase_used(ShenandoahFreeSetPartitionId::Collector, used_to_collector);
  }

  if (used_to_old_collector > 0) {
    _partitions.increase_used(ShenandoahFreeSetPartitionId::OldCollector, used_to_old_collector);
  }

  _partitions.establish_interval(ShenandoahFreeSetPartitionId::Mutator,
                                 mutator_low_idx, mutator_high_idx, mutator_empty_low_idx, mutator_empty_high_idx);
  _partitions.establish_interval(ShenandoahFreeSetPartitionId::Collector,
                                 collector_low_idx, collector_high_idx, collector_empty_low_idx, collector_empty_high_idx);

  _partitions.expand_interval_if_range_modifies_either_boundary(ShenandoahFreeSetPartitionId::OldCollector,
                                                                old_collector_low_idx, old_collector_high_idx,
                                                                old_collector_empty_low_idx, old_collector_empty_high_idx);

  recompute_total_used</* UsedByMutatorChanged */ true,
                       /* UsedByCollectorChanged */ true, /* UsedByOldCollectorChanged */ true>();
  recompute_total_affiliated</* MutatorEmptiesChanged */ true, /* CollecteorEmptiesChanged */true,
                             /* OldCollectorEmptiesChanged */ true, /* MutatorSizeChanged */ true,
                             /* CollectorSizeChanged */ true, /* OldCollectorSizeChanged */ true,
                             /* AffiliatedChangesAreYoungNeutral */ false, /* AffiliatedChangesAreGlobalNeutral */ false,
                             /* UnaffiliatedChangesAreYoungNeutral */ false>();
  _partitions.assert_bounds();
  if (LogTarget(Info, gc, free)::is_enabled()) {
    size_t old_reserve = _partitions.available_in(ShenandoahFreeSetPartitionId::OldCollector);
    if (old_reserve < to_reserve_old) {
      log_info(gc, free)("Wanted " PROPERFMT " for old reserve, but only reserved: " PROPERFMT,
                         PROPERFMTARGS(to_reserve_old), PROPERFMTARGS(old_reserve));
      assert(_heap->mode()->is_generational(), "to_old_reserve > 0 implies generational mode");
      reduce_old_reserve(old_reserve, to_reserve_old);
    }
    size_t reserve = _partitions.available_in(ShenandoahFreeSetPartitionId::Collector);
    if (reserve < to_reserve) {
      if (_heap->mode()->is_generational()) {
        reduce_young_reserve(reserve, to_reserve);
      }
      log_info(gc, free)("Wanted " PROPERFMT " for young reserve, but only reserved: " PROPERFMT,
                         PROPERFMTARGS(to_reserve), PROPERFMTARGS(reserve));
    }
  }
}

void ShenandoahFreeSet::establish_old_collector_alloc_bias() {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  shenandoah_assert_heaplocked();

  idx_t left_idx = _partitions.leftmost(ShenandoahFreeSetPartitionId::OldCollector);
  idx_t right_idx = _partitions.rightmost(ShenandoahFreeSetPartitionId::OldCollector);
  idx_t middle = (left_idx + right_idx) / 2;
  size_t available_in_first_half = 0;
  size_t available_in_second_half = 0;

  for (idx_t index = left_idx; index < middle; index++) {
    if (_partitions.in_free_set(ShenandoahFreeSetPartitionId::OldCollector, index)) {
      ShenandoahHeapRegion* r = heap->get_region((size_t) index);
      available_in_first_half += r->free();
    }
  }
  for (idx_t index = middle; index <= right_idx; index++) {
    if (_partitions.in_free_set(ShenandoahFreeSetPartitionId::OldCollector, index)) {
      ShenandoahHeapRegion* r = heap->get_region(index);
      available_in_second_half += r->free();
    }
  }

  // We desire to first consume the sparsely distributed regions in order that the remaining regions are densely packed.
  // Densely packing regions reduces the effort to search for a region that has sufficient memory to satisfy a new allocation
  // request.  Regions become sparsely distributed following a Full GC, which tends to slide all regions to the front of the
  // heap rather than allowing survivor regions to remain at the high end of the heap where we intend for them to congregate.
  _partitions.set_bias_from_left_to_right(ShenandoahFreeSetPartitionId::OldCollector,
                                          (available_in_second_half > available_in_first_half));
}

void ShenandoahFreeSet::log_status_under_lock() {
  // Must not be heap locked, it acquires heap lock only when log is enabled
  shenandoah_assert_not_heaplocked();
  if (LogTarget(Info, gc, free)::is_enabled()
      DEBUG_ONLY(|| LogTarget(Debug, gc, free)::is_enabled())) {
    ShenandoahHeapLocker locker(_heap->lock());
    log_status();
  }
}

void ShenandoahFreeSet::log_freeset_stats(ShenandoahFreeSetPartitionId partition_id, LogStream& ls) {
  size_t max = 0;
  size_t total_free = 0;
  size_t total_used = 0;

  for (idx_t idx = _partitions.leftmost(partition_id);
        idx <= _partitions.rightmost(partition_id); idx++) {
    if (_partitions.in_free_set(partition_id, idx)) {
      ShenandoahHeapRegion *r = _heap->get_region(idx);
      size_t free = alloc_capacity(r);
      max = MAX2(max, free);
      total_free += free;
      total_used += r->used();
    }
  }

  ls.print(" %s freeset stats: Partition count: %zu, Reserved: " PROPERFMT ", Max free available in a single region: " PROPERFMT ";",
            partition_name(partition_id),
            _partitions.count(partition_id),
            PROPERFMTARGS(total_free), PROPERFMTARGS(max)
          );
}

void ShenandoahFreeSet::log_status() {
  shenandoah_assert_heaplocked();

#ifdef ASSERT
  // Dump of the FreeSet details is only enabled if assertions are enabled
  LogTarget(Debug, gc, free) debug_free;
  if (debug_free.is_enabled()) {
#define BUFFER_SIZE 80
    LogStream ls(debug_free);

    char buffer[BUFFER_SIZE];
    for (uint i = 0; i < BUFFER_SIZE; i++) {
      buffer[i] = '\0';
    }


    ls.cr();
    ls.print_cr("Mutator free range [%zd..%zd] allocating from %s",
                _partitions.leftmost(ShenandoahFreeSetPartitionId::Mutator),
                _partitions.rightmost(ShenandoahFreeSetPartitionId::Mutator),
                _partitions.alloc_from_left_bias(ShenandoahFreeSetPartitionId::Mutator)? "left to right": "right to left");

    ls.print_cr("Collector free range [%zd..%zd] allocating from %s",
                _partitions.leftmost(ShenandoahFreeSetPartitionId::Collector),
                _partitions.rightmost(ShenandoahFreeSetPartitionId::Collector),
                _partitions.alloc_from_left_bias(ShenandoahFreeSetPartitionId::Collector)? "left to right": "right to left");

    ls.print_cr("Old collector free range [%zd..%zd] allocates from %s",
                _partitions.leftmost(ShenandoahFreeSetPartitionId::OldCollector),
                _partitions.rightmost(ShenandoahFreeSetPartitionId::OldCollector),
                _partitions.alloc_from_left_bias(ShenandoahFreeSetPartitionId::OldCollector)? "left to right": "right to left");
    ls.cr();
    ls.print_cr("FreeSet map legend:");
    ls.print_cr(" M/m:mutator, C/c:collector O/o:old_collector (Empty/Occupied)");
    ls.print_cr(" H/h:humongous, X/x:no alloc capacity, ~/_:retired (Old/Young)");

    for (uint i = 0; i < _heap->num_regions(); i++) {
      ShenandoahHeapRegion *r = _heap->get_region(i);
      uint idx = i % 64;
      if ((i != 0) && (idx == 0)) {
        ls.print_cr(" %6u: %s", i-64, buffer);
      }
      if (_partitions.in_free_set(ShenandoahFreeSetPartitionId::Mutator, i)) {
        size_t capacity = alloc_capacity(r);
        assert(!r->is_old() || r->is_trash(), "Old regions except trash regions should not be in mutator_free set");
        buffer[idx] = (capacity == ShenandoahHeapRegion::region_size_bytes()) ? 'M' : 'm';
      } else if (_partitions.in_free_set(ShenandoahFreeSetPartitionId::Collector, i)) {
        size_t capacity = alloc_capacity(r);
        assert(!r->is_old() || r->is_trash(), "Old regions except trash regions should not be in collector_free set");
        buffer[idx] = (capacity == ShenandoahHeapRegion::region_size_bytes()) ? 'C' : 'c';
      } else if (_partitions.in_free_set(ShenandoahFreeSetPartitionId::OldCollector, i)) {
        size_t capacity = alloc_capacity(r);
        buffer[idx] = (capacity == ShenandoahHeapRegion::region_size_bytes()) ? 'O' : 'o';
      } else if (r->is_humongous()) {
        buffer[idx] = (r->is_old() ? 'H' : 'h');
      } else if (alloc_capacity(r) == 0) {
        buffer[idx] = (r->is_old() ? 'X' : 'x');
      } else {
        buffer[idx] = (r->is_old() ? '~' : '_');
      }
    }
    uint remnant = _heap->num_regions() % 64;
    if (remnant > 0) {
      buffer[remnant] = '\0';
    } else {
      remnant = 64;
    }
    ls.print_cr(" %6u: %s", (uint) (_heap->num_regions() - remnant), buffer);
  }
#endif

  LogTarget(Info, gc, free) lt;
  if (lt.is_enabled()) {
    ResourceMark rm;
    LogStream ls(lt);

    {
      idx_t last_idx = 0;
      size_t max = 0;
      size_t max_contig = 0;
      size_t empty_contig = 0;

      size_t total_used = 0;
      size_t total_free = 0;
      size_t total_free_ext = 0;

      for (idx_t idx = _partitions.leftmost(ShenandoahFreeSetPartitionId::Mutator);
           idx <= _partitions.rightmost(ShenandoahFreeSetPartitionId::Mutator); idx++) {
        if (_partitions.in_free_set(ShenandoahFreeSetPartitionId::Mutator, idx)) {
          ShenandoahHeapRegion *r = _heap->get_region(idx);
          size_t free = alloc_capacity(r);
          max = MAX2(max, free);
          size_t used_in_region = r->used();
          if (r->is_empty() || r->is_trash()) {
            used_in_region = 0;
            total_free_ext += free;
            if (last_idx + 1 == idx) {
              empty_contig++;
            } else {
              empty_contig = 1;
            }
          } else {
            empty_contig = 0;
          }
          total_used += used_in_region;
          total_free += free;
          max_contig = MAX2(max_contig, empty_contig);
          last_idx = idx;
        }
      }

      size_t max_humongous = max_contig * ShenandoahHeapRegion::region_size_bytes();
      // capacity() is capacity of mutator
      // used() is used of mutator
      size_t free = capacity_holding_lock() - used_holding_lock();
      // Since certain regions that belonged to the Mutator free partition at the time of most recent rebuild may have been
      // retired, the sum of used and capacities within regions that are still in the Mutator free partition may not match
      // my internally tracked values of used() and free().
      assert(free == total_free, "Free memory (%zu) should match calculated memory (%zu)", free, total_free);
      ls.print("Whole heap stats: Total free: " PROPERFMT ", Total used: " PROPERFMT ", Max free in a single region: " PROPERFMT
               ", Max humongous: " PROPERFMT "; ",
               PROPERFMTARGS(total_free), PROPERFMTARGS(total_used), PROPERFMTARGS(max), PROPERFMTARGS(max_humongous));

      ls.print("Frag stats: ");
      size_t frag_ext;
      if (total_free_ext > 0) {
        frag_ext = 100 - (100 * max_humongous / total_free_ext);
      } else {
        frag_ext = 0;
      }
      ls.print("External: %zu%%, ", frag_ext);

      size_t frag_int;
      if (_partitions.count(ShenandoahFreeSetPartitionId::Mutator) > 0) {
        frag_int = (100 * (total_used / _partitions.count(ShenandoahFreeSetPartitionId::Mutator))
                    / ShenandoahHeapRegion::region_size_bytes());
      } else {
        frag_int = 0;
      }
      ls.print("Internal: %zu%%; ", frag_int);
    }

    log_freeset_stats(ShenandoahFreeSetPartitionId::Mutator, ls);
    log_freeset_stats(ShenandoahFreeSetPartitionId::Collector, ls);
    if (_heap->mode()->is_generational()) {
      log_freeset_stats(ShenandoahFreeSetPartitionId::OldCollector, ls);
    }
  }
}

void ShenandoahFreeSet::decrease_humongous_waste_for_regular_bypass(ShenandoahHeapRegion*r, size_t waste) {
  shenandoah_assert_heaplocked();
  assert(_partitions.membership(r->index()) == ShenandoahFreeSetPartitionId::NotFree, "Humongous regions should be NotFree");
  ShenandoahFreeSetPartitionId p =
    r->is_old()? ShenandoahFreeSetPartitionId::OldCollector: ShenandoahFreeSetPartitionId::Mutator;
  _partitions.decrease_humongous_waste(p, waste);
  if (waste >= PLAB::min_size() * HeapWordSize) {
    _partitions.decrease_used(p, waste);
    _partitions.unretire_to_partition(r, p);
    if (r->is_old()) {
      recompute_total_used</* UsedByMutatorChanged */ false,
                           /* UsedByCollectorChanged */ false, /* UsedByOldCollectorChanged */ true>();
    } else {
      recompute_total_used</* UsedByMutatorChanged */ true,
                           /* UsedByCollectorChanged */ false, /* UsedByOldCollectorChanged */ false>();
    }
  }
  _total_humongous_waste -= waste;
}


HeapWord* ShenandoahFreeSet::allocate(ShenandoahAllocRequest& req, bool& in_new_region) {
  shenandoah_assert_heaplocked();
  if (ShenandoahHeapRegion::requires_humongous(req.size())) {
    switch (req.type()) {
      case ShenandoahAllocRequest::_alloc_shared:
      case ShenandoahAllocRequest::_alloc_shared_gc:
        in_new_region = true;
        return allocate_contiguous(req, /* is_humongous = */ true);
      case ShenandoahAllocRequest::_alloc_cds:
        in_new_region = true;
        return allocate_contiguous(req, /* is_humongous = */ false);
      case ShenandoahAllocRequest::_alloc_plab:
      case ShenandoahAllocRequest::_alloc_gclab:
      case ShenandoahAllocRequest::_alloc_tlab:
        in_new_region = false;
        assert(false, "Trying to allocate TLAB in humongous region: %zu", req.size());
        return nullptr;
      default:
        ShouldNotReachHere();
        return nullptr;
    }
  } else {
    return allocate_single(req, in_new_region);
  }
}

void ShenandoahFreeSet::print_on(outputStream* out) const {
  out->print_cr("Mutator Free Set: %zu", _partitions.count(ShenandoahFreeSetPartitionId::Mutator));
  ShenandoahLeftRightIterator mutator(const_cast<ShenandoahRegionPartitions*>(&_partitions), ShenandoahFreeSetPartitionId::Mutator);
  for (idx_t index = mutator.current(); mutator.has_next(); index = mutator.next()) {
    _heap->get_region(index)->print_on(out);
  }

  out->print_cr("Collector Free Set: %zu", _partitions.count(ShenandoahFreeSetPartitionId::Collector));
  ShenandoahLeftRightIterator collector(const_cast<ShenandoahRegionPartitions*>(&_partitions), ShenandoahFreeSetPartitionId::Collector);
  for (idx_t index = collector.current(); collector.has_next(); index = collector.next()) {
    _heap->get_region(index)->print_on(out);
  }

  if (_heap->mode()->is_generational()) {
    out->print_cr("Old Collector Free Set: %zu", _partitions.count(ShenandoahFreeSetPartitionId::OldCollector));
    for (idx_t index = _partitions.leftmost(ShenandoahFreeSetPartitionId::OldCollector);
         index <= _partitions.rightmost(ShenandoahFreeSetPartitionId::OldCollector); index++) {
      if (_partitions.in_free_set(ShenandoahFreeSetPartitionId::OldCollector, index)) {
        _heap->get_region(index)->print_on(out);
      }
    }
  }
}

double ShenandoahFreeSet::internal_fragmentation() {
  double squared = 0;
  double linear = 0;

  ShenandoahLeftRightIterator iterator(&_partitions, ShenandoahFreeSetPartitionId::Mutator);
  for (idx_t index = iterator.current(); iterator.has_next(); index = iterator.next()) {
    ShenandoahHeapRegion* r = _heap->get_region(index);
    size_t used = r->used();
    squared += used * used;
    linear += used;
  }

  if (linear > 0) {
    double s = squared / (ShenandoahHeapRegion::region_size_bytes() * linear);
    return 1 - s;
  } else {
    return 0;
  }
}

double ShenandoahFreeSet::external_fragmentation() {
  idx_t last_idx = 0;
  size_t max_contig = 0;
  size_t empty_contig = 0;
  size_t free = 0;

  ShenandoahLeftRightIterator iterator(&_partitions, ShenandoahFreeSetPartitionId::Mutator);
  for (idx_t index = iterator.current(); iterator.has_next(); index = iterator.next()) {
    ShenandoahHeapRegion* r = _heap->get_region(index);
    if (r->is_empty()) {
      free += ShenandoahHeapRegion::region_size_bytes();
      if (last_idx + 1 == index) {
        empty_contig++;
      } else {
        empty_contig = 1;
      }
    } else {
      empty_contig = 0;
    }
    max_contig = MAX2(max_contig, empty_contig);
    last_idx = index;
  }

  if (free > 0) {
    return 1 - (1.0 * max_contig * ShenandoahHeapRegion::region_size_bytes() / free);
  } else {
    return 0;
  }
}

