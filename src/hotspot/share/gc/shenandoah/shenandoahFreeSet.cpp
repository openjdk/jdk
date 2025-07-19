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
#include "gc/shenandoah/shenandoahSimpleBitMap.hpp"
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
  explicit ShenandoahLeftRightIterator(ShenandoahRegionPartitions* partitions, ShenandoahFreeSetPartitionId partition, bool use_empty = false)
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
  explicit ShenandoahRightLeftIterator(ShenandoahRegionPartitions* partitions, ShenandoahFreeSetPartitionId partition, bool use_empty = false)
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
  make_all_regions_unavailable();
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

void ShenandoahRegionPartitions::make_all_regions_unavailable() {
  shenandoah_assert_heaplocked();
  for (size_t partition_id = 0; partition_id < IntNumPartitions; partition_id++) {
    _membership[partition_id].clear_all();
    _leftmosts[partition_id] = _max;
    _rightmosts[partition_id] = -1;
    _leftmosts_empty[partition_id] = _max;
    _rightmosts_empty[partition_id] = -1;;
    _capacity[partition_id] = 0;
    _used[partition_id] = 0;
    _available[partition_id] = FreeSetUnderConstruction;
  }
  _region_counts[int(ShenandoahFreeSetPartitionId::Mutator)] = _region_counts[int(ShenandoahFreeSetPartitionId::Collector)] = 0;
}

void ShenandoahRegionPartitions::establish_mutator_intervals(idx_t mutator_leftmost, idx_t mutator_rightmost,
                                                             idx_t mutator_leftmost_empty, idx_t mutator_rightmost_empty,
                                                             size_t mutator_region_count, size_t mutator_used) {
  shenandoah_assert_heaplocked();

  _leftmosts[int(ShenandoahFreeSetPartitionId::Mutator)] = mutator_leftmost;
  _rightmosts[int(ShenandoahFreeSetPartitionId::Mutator)] = mutator_rightmost;
  _leftmosts_empty[int(ShenandoahFreeSetPartitionId::Mutator)] = mutator_leftmost_empty;
  _rightmosts_empty[int(ShenandoahFreeSetPartitionId::Mutator)] = mutator_rightmost_empty;

  _region_counts[int(ShenandoahFreeSetPartitionId::Mutator)] = mutator_region_count;
  _used[int(ShenandoahFreeSetPartitionId::Mutator)] = mutator_used;
  _capacity[int(ShenandoahFreeSetPartitionId::Mutator)] = mutator_region_count * _region_size_bytes;
  _available[int(ShenandoahFreeSetPartitionId::Mutator)] =
    _capacity[int(ShenandoahFreeSetPartitionId::Mutator)] - _used[int(ShenandoahFreeSetPartitionId::Mutator)];

  _leftmosts[int(ShenandoahFreeSetPartitionId::Collector)] = _max;
  _rightmosts[int(ShenandoahFreeSetPartitionId::Collector)] = -1;
  _leftmosts_empty[int(ShenandoahFreeSetPartitionId::Collector)] = _max;
  _rightmosts_empty[int(ShenandoahFreeSetPartitionId::Collector)] = -1;

  _region_counts[int(ShenandoahFreeSetPartitionId::Collector)] = 0;
  _used[int(ShenandoahFreeSetPartitionId::Collector)] = 0;
  _capacity[int(ShenandoahFreeSetPartitionId::Collector)] = 0;
  _available[int(ShenandoahFreeSetPartitionId::Collector)] = 0;
}

void ShenandoahRegionPartitions::establish_old_collector_intervals(idx_t old_collector_leftmost, idx_t old_collector_rightmost,
                                                                   idx_t old_collector_leftmost_empty,
                                                                   idx_t old_collector_rightmost_empty,
                                                                   size_t old_collector_region_count, size_t old_collector_used) {
  shenandoah_assert_heaplocked();

  _leftmosts[int(ShenandoahFreeSetPartitionId::OldCollector)] = old_collector_leftmost;
  _rightmosts[int(ShenandoahFreeSetPartitionId::OldCollector)] = old_collector_rightmost;
  _leftmosts_empty[int(ShenandoahFreeSetPartitionId::OldCollector)] = old_collector_leftmost_empty;
  _rightmosts_empty[int(ShenandoahFreeSetPartitionId::OldCollector)] = old_collector_rightmost_empty;

  _region_counts[int(ShenandoahFreeSetPartitionId::OldCollector)] = old_collector_region_count;
  _used[int(ShenandoahFreeSetPartitionId::OldCollector)] = old_collector_used;
  _capacity[int(ShenandoahFreeSetPartitionId::OldCollector)] = old_collector_region_count * _region_size_bytes;
  _available[int(ShenandoahFreeSetPartitionId::OldCollector)] =
    _capacity[int(ShenandoahFreeSetPartitionId::OldCollector)] - _used[int(ShenandoahFreeSetPartitionId::OldCollector)];
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

inline void ShenandoahRegionPartitions::shrink_interval_if_range_modifies_either_boundary(
  ShenandoahFreeSetPartitionId partition, idx_t low_idx, idx_t high_idx) {
  assert((low_idx <= high_idx) && (low_idx >= 0) && (high_idx < _max), "Range must span legal index values");
  if (low_idx == leftmost(partition)) {
    assert (!_membership[int(partition)].is_set(low_idx), "Do not shrink interval if region not removed");
    if (high_idx + 1 == _max) {
      _leftmosts[int(partition)] = _max;
    } else {
      _leftmosts[int(partition)] = find_index_of_next_available_region(partition, high_idx + 1);
    }
    if (_leftmosts_empty[int(partition)] < _leftmosts[int(partition)]) {
      // This gets us closer to where we need to be; we'll scan further when leftmosts_empty is requested.
      _leftmosts_empty[int(partition)] = _leftmosts[int(partition)];
    }
  }
  if (high_idx == _rightmosts[int(partition)]) {
    assert (!_membership[int(partition)].is_set(high_idx), "Do not shrink interval if region not removed");
    if (low_idx == 0) {
      _rightmosts[int(partition)] = -1;
    } else {
      _rightmosts[int(partition)] = find_index_of_previous_available_region(partition, low_idx - 1);
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

inline void ShenandoahRegionPartitions::shrink_interval_if_boundary_modified(ShenandoahFreeSetPartitionId partition, idx_t idx) {
  shrink_interval_if_range_modifies_either_boundary(partition, idx, idx);
}

inline void ShenandoahRegionPartitions::expand_interval_if_boundary_modified(ShenandoahFreeSetPartitionId partition,
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
    assert (in_free_set(partition, idx), "Must be in partition to remove from partition");
    _membership[int(partition)].clear_bit(idx);
  }
  _region_counts[int(partition)] -= high_idx + 1 - low_idx;
  shrink_interval_if_range_modifies_either_boundary(partition, low_idx, high_idx);
}

void ShenandoahRegionPartitions::retire_from_partition(ShenandoahFreeSetPartitionId partition, idx_t idx, size_t used_bytes) {

  // Note: we may remove from free partition even if region is not entirely full, such as when available < PLAB::min_size()
  assert (idx < _max, "index is sane: %zu < %zu", idx, _max);
  assert (partition < NumPartitions, "Cannot remove from free partitions if not already free");
  assert (in_free_set(partition, idx), "Must be in partition to remove from partition");

  if (used_bytes < _region_size_bytes) {
    // Count the alignment pad remnant of memory as used when we retire this region
    increase_used(partition, _region_size_bytes - used_bytes);
  }
  _membership[int(partition)].clear_bit(idx);
  shrink_interval_if_boundary_modified(partition, idx);
  _region_counts[int(partition)]--;
}

void ShenandoahRegionPartitions::make_free(idx_t idx, ShenandoahFreeSetPartitionId which_partition, size_t available) {
  shenandoah_assert_heaplocked();
  assert (idx < _max, "index is sane: %zu < %zu", idx, _max);
  assert (membership(idx) == ShenandoahFreeSetPartitionId::NotFree, "Cannot make free if already free");
  assert (which_partition < NumPartitions, "selected free partition must be valid");
  assert (available <= _region_size_bytes, "Available cannot exceed region size");

  _membership[int(which_partition)].set_bit(idx);
  _capacity[int(which_partition)] += _region_size_bytes;
  _used[int(which_partition)] += _region_size_bytes - available;
  _available[int(which_partition)] += available;
  expand_interval_if_boundary_modified(which_partition, idx, available);
  _region_counts[int(which_partition)]++;
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


void ShenandoahRegionPartitions::move_from_partition_to_partition(idx_t idx, ShenandoahFreeSetPartitionId orig_partition,
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

  _capacity[int(orig_partition)] -= _region_size_bytes;
  _used[int(orig_partition)] -= used;
  _available[int(orig_partition)] -= available;
  shrink_interval_if_boundary_modified(orig_partition, idx);

  _capacity[int(new_partition)] += _region_size_bytes;;
  _used[int(new_partition)] += used;
  _available[int(new_partition)] += available;
  expand_interval_if_boundary_modified(new_partition, idx, available);

  _region_counts[int(orig_partition)]--;
  _region_counts[int(new_partition)]++;
}

const char* ShenandoahRegionPartitions::partition_membership_name(idx_t idx) const {
  return partition_name(membership(idx));
}

inline ShenandoahFreeSetPartitionId ShenandoahRegionPartitions::membership(idx_t idx) const {
  assert (idx < _max, "index is sane: %zu < %zu", idx, _max);
  ShenandoahFreeSetPartitionId result = ShenandoahFreeSetPartitionId::NotFree;
  for (uint partition_id = 0; partition_id < UIntNumPartitions; partition_id++) {
    if (_membership[partition_id].is_set(idx)) {
      assert(result == ShenandoahFreeSetPartitionId::NotFree, "Region should reside in only one partition");
      result = (ShenandoahFreeSetPartitionId) partition_id;
    }
  }
  return result;
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
  idx_t result = _membership[int(which_partition)].find_first_consecutive_set_bits(start_index, rightmost_idx + 1, cluster_size);
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

  idx_t leftmosts[UIntNumPartitions];
  idx_t rightmosts[UIntNumPartitions];
  idx_t empty_leftmosts[UIntNumPartitions];
  idx_t empty_rightmosts[UIntNumPartitions];

  for (uint i = 0; i < UIntNumPartitions; i++) {
    leftmosts[i] = _max;
    empty_leftmosts[i] = _max;
    rightmosts[i] = -1;
    empty_rightmosts[i] = -1;
  }

  for (idx_t i = 0; i < _max; i++) {
    ShenandoahFreeSetPartitionId partition = membership(i);
    switch (partition) {
      case ShenandoahFreeSetPartitionId::NotFree:
        break;

      case ShenandoahFreeSetPartitionId::Mutator:
      case ShenandoahFreeSetPartitionId::Collector:
      case ShenandoahFreeSetPartitionId::OldCollector:
      {
        size_t capacity = _free_set->alloc_capacity(i);
        bool is_empty = (capacity == _region_size_bytes);
        assert(capacity > 0, "free regions must have allocation capacity");
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
          "free regions before the leftmost: %zd, bound %zd",
          beg_off, leftmost(ShenandoahFreeSetPartitionId::Mutator));
  assert (end_off <= rightmost(ShenandoahFreeSetPartitionId::Mutator),
          "free regions past the rightmost: %zd, bound %zd",
          end_off, rightmost(ShenandoahFreeSetPartitionId::Mutator));

  beg_off = empty_leftmosts[int(ShenandoahFreeSetPartitionId::Mutator)];
  end_off = empty_rightmosts[int(ShenandoahFreeSetPartitionId::Mutator)];
  assert (beg_off >= leftmost_empty(ShenandoahFreeSetPartitionId::Mutator),
          "free empty regions before the leftmost: %zd, bound %zd",
          beg_off, leftmost_empty(ShenandoahFreeSetPartitionId::Mutator));
  assert (end_off <= rightmost_empty(ShenandoahFreeSetPartitionId::Mutator),
          "free empty regions past the rightmost: %zd, bound %zd",
          end_off, rightmost_empty(ShenandoahFreeSetPartitionId::Mutator));

  // Performance invariants. Failing these would not break the free partition, but performance would suffer.
  assert (leftmost(ShenandoahFreeSetPartitionId::Collector) <= _max, "leftmost in bounds: %zd < %zd",
          leftmost(ShenandoahFreeSetPartitionId::Collector),  _max);
  assert (rightmost(ShenandoahFreeSetPartitionId::Collector) < _max, "rightmost in bounds: %zd < %zd",
          rightmost(ShenandoahFreeSetPartitionId::Collector),  _max);

  assert (leftmost(ShenandoahFreeSetPartitionId::Collector) == _max
          || partition_id_matches(leftmost(ShenandoahFreeSetPartitionId::Collector), ShenandoahFreeSetPartitionId::Collector),
          "leftmost region should be free: %zd",  leftmost(ShenandoahFreeSetPartitionId::Collector));
  assert (leftmost(ShenandoahFreeSetPartitionId::Collector) == _max
          || partition_id_matches(rightmost(ShenandoahFreeSetPartitionId::Collector), ShenandoahFreeSetPartitionId::Collector),
          "rightmost region should be free: %zd", rightmost(ShenandoahFreeSetPartitionId::Collector));

  // If Collector partition is empty, leftmosts will both equal max, rightmosts will both equal zero.
  // Likewise for empty region partitions.
  beg_off = leftmosts[int(ShenandoahFreeSetPartitionId::Collector)];
  end_off = rightmosts[int(ShenandoahFreeSetPartitionId::Collector)];
  assert (beg_off >= leftmost(ShenandoahFreeSetPartitionId::Collector),
          "free regions before the leftmost: %zd, bound %zd",
          beg_off, leftmost(ShenandoahFreeSetPartitionId::Collector));
  assert (end_off <= rightmost(ShenandoahFreeSetPartitionId::Collector),
          "free regions past the rightmost: %zd, bound %zd",
          end_off, rightmost(ShenandoahFreeSetPartitionId::Collector));

  beg_off = empty_leftmosts[int(ShenandoahFreeSetPartitionId::Collector)];
  end_off = empty_rightmosts[int(ShenandoahFreeSetPartitionId::Collector)];
  assert (beg_off >= _leftmosts_empty[int(ShenandoahFreeSetPartitionId::Collector)],
          "free empty regions before the leftmost: %zd, bound %zd",
          beg_off, leftmost_empty(ShenandoahFreeSetPartitionId::Collector));
  assert (end_off <= _rightmosts_empty[int(ShenandoahFreeSetPartitionId::Collector)],
          "free empty regions past the rightmost: %zd, bound %zd",
          end_off, rightmost_empty(ShenandoahFreeSetPartitionId::Collector));

  // Performance invariants. Failing these would not break the free partition, but performance would suffer.
  assert (leftmost(ShenandoahFreeSetPartitionId::OldCollector) <= _max, "leftmost in bounds: %zd < %zd",
          leftmost(ShenandoahFreeSetPartitionId::OldCollector),  _max);
  assert (rightmost(ShenandoahFreeSetPartitionId::OldCollector) < _max, "rightmost in bounds: %zd < %zd",
          rightmost(ShenandoahFreeSetPartitionId::OldCollector),  _max);

  assert (leftmost(ShenandoahFreeSetPartitionId::OldCollector) == _max
          || partition_id_matches(leftmost(ShenandoahFreeSetPartitionId::OldCollector),
                                  ShenandoahFreeSetPartitionId::OldCollector),
          "leftmost region should be free: %zd",  leftmost(ShenandoahFreeSetPartitionId::OldCollector));
  assert (leftmost(ShenandoahFreeSetPartitionId::OldCollector) == _max
          || partition_id_matches(rightmost(ShenandoahFreeSetPartitionId::OldCollector),
                                  ShenandoahFreeSetPartitionId::OldCollector),
          "rightmost region should be free: %zd", rightmost(ShenandoahFreeSetPartitionId::OldCollector));

  // If OldCollector partition is empty, leftmosts will both equal max, rightmosts will both equal zero.
  // Likewise for empty region partitions.
  beg_off = leftmosts[int(ShenandoahFreeSetPartitionId::OldCollector)];
  end_off = rightmosts[int(ShenandoahFreeSetPartitionId::OldCollector)];
  assert (beg_off >= leftmost(ShenandoahFreeSetPartitionId::OldCollector),
          "free regions before the leftmost: %zd, bound %zd",
          beg_off, leftmost(ShenandoahFreeSetPartitionId::OldCollector));
  assert (end_off <= rightmost(ShenandoahFreeSetPartitionId::OldCollector),
          "free regions past the rightmost: %zd, bound %zd",
          end_off, rightmost(ShenandoahFreeSetPartitionId::OldCollector));

  beg_off = empty_leftmosts[int(ShenandoahFreeSetPartitionId::OldCollector)];
  end_off = empty_rightmosts[int(ShenandoahFreeSetPartitionId::OldCollector)];
  assert (beg_off >= _leftmosts_empty[int(ShenandoahFreeSetPartitionId::OldCollector)],
          "free empty regions before the leftmost: %zd, bound %zd",
          beg_off, leftmost_empty(ShenandoahFreeSetPartitionId::OldCollector));
  assert (end_off <= _rightmosts_empty[int(ShenandoahFreeSetPartitionId::OldCollector)],
          "free empty regions past the rightmost: %zd, bound %zd",
          end_off, rightmost_empty(ShenandoahFreeSetPartitionId::OldCollector));
}
#endif

ShenandoahFreeSet::ShenandoahFreeSet(ShenandoahHeap* heap, size_t max_regions) :
  _heap(heap),
  _partitions(max_regions, this),
  _alloc_bias_weight(0)
{
  clear_internal();
}

void ShenandoahFreeSet::add_promoted_in_place_region_to_old_collector(ShenandoahHeapRegion* region) {
  shenandoah_assert_heaplocked();
  size_t plab_min_size_in_bytes = ShenandoahGenerationalHeap::heap()->plab_min_size() * HeapWordSize;
  size_t idx = region->index();
  size_t capacity = alloc_capacity(region);
  assert(_partitions.membership(idx) == ShenandoahFreeSetPartitionId::NotFree,
         "Regions promoted in place should have been excluded from Mutator partition");
  if (capacity >= plab_min_size_in_bytes) {
    _partitions.make_free(idx, ShenandoahFreeSetPartitionId::OldCollector, capacity);
    _heap->old_generation()->augment_promoted_reserve(capacity);
  }
}

HeapWord* ShenandoahFreeSet::allocate_from_partition_with_affiliation(ShenandoahAffiliation affiliation,
                                                                      ShenandoahAllocRequest& req, bool& in_new_region) {

  shenandoah_assert_heaplocked();
  ShenandoahFreeSetPartitionId which_partition = req.is_old()? ShenandoahFreeSetPartitionId::OldCollector: ShenandoahFreeSetPartitionId::Collector;
  if (_partitions.alloc_from_left_bias(which_partition)) {
    ShenandoahLeftRightIterator iterator(&_partitions, which_partition, affiliation == ShenandoahAffiliation::FREE);
    return allocate_with_affiliation(iterator, affiliation, req, in_new_region);
  } else {
    ShenandoahRightLeftIterator iterator(&_partitions, which_partition, affiliation == ShenandoahAffiliation::FREE);
    return allocate_with_affiliation(iterator, affiliation, req, in_new_region);
  }
}

template<typename Iter>
HeapWord* ShenandoahFreeSet::allocate_with_affiliation(Iter& iterator, ShenandoahAffiliation affiliation, ShenandoahAllocRequest& req, bool& in_new_region) {
  for (idx_t idx = iterator.current(); iterator.has_next(); idx = iterator.next()) {
    ShenandoahHeapRegion* r = _heap->get_region(idx);
    if (r->affiliation() == affiliation) {
      HeapWord* result = try_allocate_in(r, req, in_new_region);
      if (result != nullptr) {
        return result;
      }
    }
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

  switch (req.type()) {
    case ShenandoahAllocRequest::_alloc_tlab:
    case ShenandoahAllocRequest::_alloc_shared:
      return allocate_for_mutator(req, in_new_region);
    case ShenandoahAllocRequest::_alloc_gclab:
    case ShenandoahAllocRequest::_alloc_plab:
    case ShenandoahAllocRequest::_alloc_shared_gc:
      return allocate_for_collector(req, in_new_region);
    default:
      ShouldNotReachHere();
  }
  return nullptr;
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
    size_t min_size = (req.type() == ShenandoahAllocRequest::_alloc_tlab) ? req.min_size() : req.size();
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
  // Fast-path: try to allocate in the collector view first
  HeapWord* result;
  result = allocate_from_partition_with_affiliation(req.affiliation(), req, in_new_region);
  if (result != nullptr) {
    return result;
  }

  bool allow_new_region = can_allocate_in_new_region(req);
  if (allow_new_region) {
    // Try a free region that is dedicated to GC allocations.
    result = allocate_from_partition_with_affiliation(ShenandoahAffiliation::FREE, req, in_new_region);
    if (result != nullptr) {
      return result;
    }
  }

  // No dice. Can we borrow space from mutator view?
  if (!ShenandoahEvacReserveOverflow) {
    return nullptr;
  }

  if (!allow_new_region && req.is_old() && (_heap->young_generation()->free_unaffiliated_regions() > 0)) {
    // This allows us to flip a mutator region to old_collector
    allow_new_region = true;
  }

  // We should expand old-gen if this can prevent an old-gen evacuation failure.  We don't care so much about
  // promotion failures since they can be mitigated in a subsequent GC pass.  Would be nice to know if this
  // allocation request is for evacuation or promotion.  Individual threads limit their use of PLAB memory for
  // promotions, so we already have an assurance that any additional memory set aside for old-gen will be used
  // only for old-gen evacuations.
  if (allow_new_region) {
    // Try to steal an empty region from the mutator view.
    result = try_allocate_from_mutator(req, in_new_region);
  }

  // This is it. Do not try to mix mutator and GC allocations, because adjusting region UWM
  // due to GC allocations would expose unparsable mutator allocations.
  return result;
}

bool ShenandoahFreeSet::can_allocate_in_new_region(const ShenandoahAllocRequest& req) {
  if (!_heap->mode()->is_generational()) {
    return true;
  }

  assert(req.is_old() || req.is_young(), "Should request affiliation");
  return (req.is_old() && _heap->old_generation()->free_unaffiliated_regions() > 0)
         || (req.is_young() && _heap->young_generation()->free_unaffiliated_regions() > 0);
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
  r->try_recycle_under_lock();
  in_new_region = r->is_empty();

  if (in_new_region) {
    log_debug(gc, free)("Using new region (%zu) for %s (" PTR_FORMAT ").",
                        r->index(), ShenandoahAllocRequest::alloc_type_to_string(req.type()), p2i(&req));
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
    _heap->generation_for(r->affiliation())->increment_affiliated_region_count();

#ifdef ASSERT
    ShenandoahMarkingContext* const ctx = _heap->marking_context();
    assert(ctx->top_at_mark_start(r) == r->bottom(), "Newly established allocation region starts with TAMS equal to bottom");
    assert(ctx->is_bitmap_range_within_region_clear(ctx->top_bitmap(r), r->end()), "Bitmap above top_bitmap() must be clear");
#endif
    log_debug(gc, free)("Using new region (%zu) for %s (" PTR_FORMAT ").",
                        r->index(), ShenandoahAllocRequest::alloc_type_to_string(req.type()), p2i(&req));
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
    if (req.type() == ShenandoahAllocRequest::_alloc_plab) {
      // This is a PLAB allocation
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
    } else {
      assert(req.is_gc_alloc(), "Should be gc_alloc since req wasn't mutator alloc");

      // For GC allocations, we advance update_watermark because the objects relocated into this memory during
      // evacuation are not updated during evacuation.  For both young and old regions r, it is essential that all
      // PLABs be made parsable at the end of evacuation.  This is enabled by retiring all plabs at end of evacuation.
      r->set_update_watermark(r->top());
      if (r->is_old()) {
        _partitions.increase_used(ShenandoahFreeSetPartitionId::OldCollector, req.actual_size() * HeapWordSize);
        assert(req.type() != ShenandoahAllocRequest::_alloc_gclab, "old-gen allocations use PLAB or shared allocation");
        // for plabs, we'll sort the difference between evac and promotion usage when we retire the plab
      } else {
        _partitions.increase_used(ShenandoahFreeSetPartitionId::Collector, req.actual_size() * HeapWordSize);
      }
    }
  }

  static const size_t min_capacity = (size_t) (ShenandoahHeapRegion::region_size_bytes() * (1.0 - 1.0 / ShenandoahEvacWaste));
  size_t ac = alloc_capacity(r);

  if (((result == nullptr) && (ac < min_capacity)) || (alloc_capacity(r) < PLAB::min_size() * HeapWordSize)) {
    // Regardless of whether this allocation succeeded, if the remaining memory is less than PLAB:min_size(), retire this region.
    // Note that retire_from_partition() increases used to account for waste.

    // Also, if this allocation request failed and the consumed within this region * ShenandoahEvacWaste > region size,
    // then retire the region so that subsequent searches can find available memory more quickly.

    size_t idx = r->index();
    ShenandoahFreeSetPartitionId orig_partition;
    if (req.is_mutator_alloc()) {
      orig_partition = ShenandoahFreeSetPartitionId::Mutator;
    } else if (req.type() == ShenandoahAllocRequest::_alloc_gclab) {
      orig_partition = ShenandoahFreeSetPartitionId::Collector;
    } else if (req.type() == ShenandoahAllocRequest::_alloc_plab) {
      orig_partition = ShenandoahFreeSetPartitionId::OldCollector;
    } else {
      assert(req.type() == ShenandoahAllocRequest::_alloc_shared_gc, "Unexpected allocation type");
      if (req.is_old()) {
        orig_partition = ShenandoahFreeSetPartitionId::OldCollector;
      } else {
        orig_partition = ShenandoahFreeSetPartitionId::Collector;
      }
    }
    _partitions.retire_from_partition(orig_partition, idx, r->used());
    _partitions.assert_bounds();
  }
  return result;
}

HeapWord* ShenandoahFreeSet::allocate_contiguous(ShenandoahAllocRequest& req) {
  assert(req.is_mutator_alloc(), "All humongous allocations are performed by mutator");
  shenandoah_assert_heaplocked();

  size_t words_size = req.size();
  idx_t num = ShenandoahHeapRegion::required_regions(words_size * HeapWordSize);

  assert(req.is_young(), "Humongous regions always allocated in YOUNG");
  ShenandoahGeneration* generation = _heap->generation_for(req.affiliation());

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

  size_t remainder = words_size & ShenandoahHeapRegion::region_size_words_mask();
  // Initialize regions:
  for (idx_t i = beg; i <= end; i++) {
    ShenandoahHeapRegion* r = _heap->get_region(i);
    r->try_recycle_under_lock();

    assert(i == beg || _heap->get_region(i - 1)->index() + 1 == r->index(), "Should be contiguous");
    assert(r->is_empty(), "Should be empty");

    if (i == beg) {
      r->make_humongous_start();
    } else {
      r->make_humongous_cont();
    }

    // Trailing region may be non-full, record the remainder there
    size_t used_words;
    if ((i == end) && (remainder != 0)) {
      used_words = remainder;
    } else {
      used_words = ShenandoahHeapRegion::region_size_words();
    }

    r->set_affiliation(req.affiliation());
    r->set_update_watermark(r->bottom());
    r->set_top(r->bottom() + used_words);
  }
  generation->increase_affiliated_region_count(num);

  // retire_range_from_partition() will adjust bounds on Mutator free set if appropriate
  _partitions.retire_range_from_partition(ShenandoahFreeSetPartitionId::Mutator, beg, end);

  size_t total_humongous_size = ShenandoahHeapRegion::region_size_bytes() * num;
  _partitions.increase_used(ShenandoahFreeSetPartitionId::Mutator, total_humongous_size);
  _partitions.assert_bounds();
  req.set_actual_size(words_size);
  if (remainder != 0) {
    req.set_waste(ShenandoahHeapRegion::region_size_words() - remainder);
  }
  return _heap->get_region(beg)->bottom();
}

class ShenandoahRecycleTrashedRegionClosure final : public ShenandoahHeapRegionClosure {
public:
  ShenandoahRecycleTrashedRegionClosure(): ShenandoahHeapRegionClosure() {}

  void heap_region_do(ShenandoahHeapRegion* r) {
    r->try_recycle();
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

bool ShenandoahFreeSet::flip_to_old_gc(ShenandoahHeapRegion* r) {
  const size_t idx = r->index();

  assert(_partitions.partition_id_matches(idx, ShenandoahFreeSetPartitionId::Mutator), "Should be in mutator view");
  assert(can_allocate_from(r), "Should not be allocated");

  ShenandoahGenerationalHeap* gen_heap = ShenandoahGenerationalHeap::heap();
  const size_t region_capacity = alloc_capacity(r);

  bool transferred = gen_heap->generation_sizer()->transfer_to_old(1);
  if (transferred) {
    _partitions.move_from_partition_to_partition(idx, ShenandoahFreeSetPartitionId::Mutator,
                                                 ShenandoahFreeSetPartitionId::OldCollector, region_capacity);
    _partitions.assert_bounds();
    _heap->old_generation()->augment_evacuation_reserve(region_capacity);
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
                                                   ShenandoahFreeSetPartitionId::OldCollector, region_capacity);

      _partitions.assert_bounds();

      // 4. Do not adjust capacities for generations, we just swapped the regions that have already
      // been accounted for. However, we should adjust the evacuation reserves as those may have changed.
      shenandoah_assert_heaplocked();
      const size_t reserve = _heap->old_generation()->get_evacuation_reserve();
      _heap->old_generation()->set_evacuation_reserve(reserve - unusable_capacity + region_capacity);
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

  _alloc_bias_weight = 0;
  _partitions.set_bias_from_left_to_right(ShenandoahFreeSetPartitionId::Mutator, true);
  _partitions.set_bias_from_left_to_right(ShenandoahFreeSetPartitionId::Collector, false);
  _partitions.set_bias_from_left_to_right(ShenandoahFreeSetPartitionId::OldCollector, false);
}

void ShenandoahFreeSet::find_regions_with_alloc_capacity(size_t &young_cset_regions, size_t &old_cset_regions,
                                                         size_t &first_old_region, size_t &last_old_region,
                                                         size_t &old_region_count) {
  clear_internal();

  first_old_region = _heap->num_regions();
  last_old_region = 0;
  old_region_count = 0;
  old_cset_regions = 0;
  young_cset_regions = 0;

  size_t region_size_bytes = _partitions.region_size_bytes();
  size_t max_regions = _partitions.max_regions();

  size_t mutator_leftmost = max_regions;
  size_t mutator_rightmost = 0;
  size_t mutator_leftmost_empty = max_regions;
  size_t mutator_rightmost_empty = 0;
  size_t mutator_regions = 0;
  size_t mutator_used = 0;

  size_t old_collector_leftmost = max_regions;
  size_t old_collector_rightmost = 0;
  size_t old_collector_leftmost_empty = max_regions;
  size_t old_collector_rightmost_empty = 0;
  size_t old_collector_regions = 0;
  size_t old_collector_used = 0;

  size_t num_regions = _heap->num_regions();
  for (size_t idx = 0; idx < num_regions; idx++) {
    ShenandoahHeapRegion* region = _heap->get_region(idx);
    if (region->is_trash()) {
      // Trashed regions represent regions that had been in the collection partition but have not yet been "cleaned up".
      // The cset regions are not "trashed" until we have finished update refs.
      if (region->is_old()) {
        old_cset_regions++;
      } else {
        assert(region->is_young(), "Trashed region should be old or young");
        young_cset_regions++;
      }
    } else if (region->is_old()) {
      // count both humongous and regular regions, but don't count trash (cset) regions.
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
      if (ac > PLAB::min_size() * HeapWordSize) {
        if (region->is_trash() || !region->is_old()) {
          // Both young and old collected regions (trashed) are placed into the Mutator set
          _partitions.raw_assign_membership(idx, ShenandoahFreeSetPartitionId::Mutator);
          if (idx < mutator_leftmost) {
            mutator_leftmost = idx;
          }
          if (idx > mutator_rightmost) {
            mutator_rightmost = idx;
          }
          if (ac == region_size_bytes) {
            if (idx < mutator_leftmost_empty) {
              mutator_leftmost_empty = idx;
            }
            if (idx > mutator_rightmost_empty) {
              mutator_rightmost_empty = idx;
            }
          }
          mutator_regions++;
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
          if (ac == region_size_bytes) {
            if (idx < old_collector_leftmost_empty) {
              old_collector_leftmost_empty = idx;
            }
            if (idx > old_collector_rightmost_empty) {
              old_collector_rightmost_empty = idx;
            }
          }
          old_collector_regions++;
          old_collector_used += (region_size_bytes - ac);
        }
      }
    }
  }
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


  idx_t rightmost_idx = (mutator_leftmost == max_regions)? -1: (idx_t) mutator_rightmost;
  idx_t rightmost_empty_idx = (mutator_leftmost_empty == max_regions)? -1: (idx_t) mutator_rightmost_empty;
  _partitions.establish_mutator_intervals(mutator_leftmost, rightmost_idx, mutator_leftmost_empty, rightmost_empty_idx,
                                          mutator_regions, mutator_used);
  rightmost_idx = (old_collector_leftmost == max_regions)? -1: (idx_t) old_collector_rightmost;
  rightmost_empty_idx = (old_collector_leftmost_empty == max_regions)? -1: (idx_t) old_collector_rightmost_empty;
  _partitions.establish_old_collector_intervals(old_collector_leftmost, rightmost_idx, old_collector_leftmost_empty,
                                                rightmost_empty_idx, old_collector_regions, old_collector_used);
  log_debug(gc, free)("  After find_regions_with_alloc_capacity(), Mutator range [%zd, %zd],"
                      "  Old Collector range [%zd, %zd]",
                      _partitions.leftmost(ShenandoahFreeSetPartitionId::Mutator),
                      _partitions.rightmost(ShenandoahFreeSetPartitionId::Mutator),
                      _partitions.leftmost(ShenandoahFreeSetPartitionId::OldCollector),
                      _partitions.rightmost(ShenandoahFreeSetPartitionId::OldCollector));
}

// Returns number of regions transferred, adds transferred bytes to var argument bytes_transferred
size_t ShenandoahFreeSet::transfer_empty_regions_from_collector_set_to_mutator_set(ShenandoahFreeSetPartitionId which_collector,
                                                                                   size_t max_xfer_regions,
                                                                                   size_t& bytes_transferred) {
  shenandoah_assert_heaplocked();
  const size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();
  size_t transferred_regions = 0;
  ShenandoahLeftRightIterator iterator(&_partitions, which_collector, true);
  for (idx_t idx = iterator.current(); transferred_regions < max_xfer_regions && iterator.has_next(); idx = iterator.next()) {
    // Note: can_allocate_from() denotes that region is entirely empty
    if (can_allocate_from(idx)) {
      _partitions.move_from_partition_to_partition(idx, which_collector, ShenandoahFreeSetPartitionId::Mutator, region_size_bytes);
      transferred_regions++;
      bytes_transferred += region_size_bytes;
    }
  }
  return transferred_regions;
}

// Returns number of regions transferred, adds transferred bytes to var argument bytes_transferred
size_t ShenandoahFreeSet::transfer_non_empty_regions_from_collector_set_to_mutator_set(ShenandoahFreeSetPartitionId which_collector,
                                                                                       size_t max_xfer_regions,
                                                                                       size_t& bytes_transferred) {
  shenandoah_assert_heaplocked();
  size_t transferred_regions = 0;
  ShenandoahLeftRightIterator iterator(&_partitions, which_collector, false);
  for (idx_t idx = iterator.current(); transferred_regions < max_xfer_regions && iterator.has_next(); idx = iterator.next()) {
    size_t ac = alloc_capacity(idx);
    if (ac > 0) {
      _partitions.move_from_partition_to_partition(idx, which_collector, ShenandoahFreeSetPartitionId::Mutator, ac);
      transferred_regions++;
      bytes_transferred += ac;
    }
  }
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
    if (old_collector_regions > 0) {
      ShenandoahGenerationalHeap::cast(_heap)->generation_sizer()->transfer_to_young(old_collector_regions);
    }
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
void ShenandoahFreeSet::prepare_to_rebuild(size_t &young_cset_regions, size_t &old_cset_regions,
                                           size_t &first_old_region, size_t &last_old_region, size_t &old_region_count) {
  shenandoah_assert_heaplocked();
  // This resets all state information, removing all regions from all sets.
  clear();
  log_debug(gc, free)("Rebuilding FreeSet");

  // This places regions that have alloc_capacity into the old_collector set if they identify as is_old() or the
  // mutator set otherwise.  All trashed (cset) regions are affiliated young and placed in mutator set.
  find_regions_with_alloc_capacity(young_cset_regions, old_cset_regions, first_old_region, last_old_region, old_region_count);
}

void ShenandoahFreeSet::establish_generation_sizes(size_t young_region_count, size_t old_region_count) {
  assert(young_region_count + old_region_count == ShenandoahHeap::heap()->num_regions(), "Sanity");
  if (ShenandoahHeap::heap()->mode()->is_generational()) {
    ShenandoahGenerationalHeap* heap = ShenandoahGenerationalHeap::heap();
    ShenandoahOldGeneration* old_gen = heap->old_generation();
    ShenandoahYoungGeneration* young_gen = heap->young_generation();
    size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();

    size_t original_old_capacity = old_gen->max_capacity();
    size_t new_old_capacity = old_region_count * region_size_bytes;
    size_t new_young_capacity = young_region_count * region_size_bytes;
    old_gen->set_capacity(new_old_capacity);
    young_gen->set_capacity(new_young_capacity);

    if (new_old_capacity > original_old_capacity) {
      size_t region_count = (new_old_capacity - original_old_capacity) / region_size_bytes;
      log_info(gc, ergo)("Transfer %zu region(s) from %s to %s, yielding increased size: " PROPERFMT,
                         region_count, young_gen->name(), old_gen->name(), PROPERFMTARGS(new_old_capacity));
    } else if (new_old_capacity < original_old_capacity) {
      size_t region_count = (original_old_capacity - new_old_capacity) / region_size_bytes;
      log_info(gc, ergo)("Transfer %zu region(s) from %s to %s, yielding increased size: " PROPERFMT,
                         region_count, old_gen->name(), young_gen->name(), PROPERFMTARGS(new_young_capacity));
    }
    // This balances generations, so clear any pending request to balance.
    old_gen->set_region_balance(0);
  }
}

void ShenandoahFreeSet::finish_rebuild(size_t young_cset_regions, size_t old_cset_regions, size_t old_region_count,
                                       bool have_evacuation_reserves) {
  shenandoah_assert_heaplocked();
  size_t young_reserve(0), old_reserve(0);

  if (_heap->mode()->is_generational()) {
    compute_young_and_old_reserves(young_cset_regions, old_cset_regions, have_evacuation_reserves,
                                   young_reserve, old_reserve);
  } else {
    young_reserve = (_heap->max_capacity() / 100) * ShenandoahEvacReserve;
    old_reserve = 0;
  }

  // Move some of the mutator regions in the Collector and OldCollector partitions in order to satisfy
  // young_reserve and old_reserve.
  reserve_regions(young_reserve, old_reserve, old_region_count);
  size_t young_region_count = _heap->num_regions() - old_region_count;
  establish_generation_sizes(young_region_count, old_region_count);
  establish_old_collector_alloc_bias();
  _partitions.assert_bounds();
  log_status();
}

void ShenandoahFreeSet::compute_young_and_old_reserves(size_t young_cset_regions, size_t old_cset_regions,
                                                       bool have_evacuation_reserves,
                                                       size_t& young_reserve_result, size_t& old_reserve_result) const {
  shenandoah_assert_generational();
  const size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();

  ShenandoahOldGeneration* const old_generation = _heap->old_generation();
  size_t old_available = old_generation->available();
  size_t old_unaffiliated_regions = old_generation->free_unaffiliated_regions();
  ShenandoahYoungGeneration* const young_generation = _heap->young_generation();
  size_t young_capacity = young_generation->max_capacity();
  size_t young_unaffiliated_regions = young_generation->free_unaffiliated_regions();

  // Add in the regions we anticipate to be freed by evacuation of the collection set
  old_unaffiliated_regions += old_cset_regions;
  young_unaffiliated_regions += young_cset_regions;

  // Consult old-region balance to make adjustments to current generation capacities and availability.
  // The generation region transfers take place after we rebuild.
  const ssize_t old_region_balance = old_generation->get_region_balance();
  if (old_region_balance != 0) {
#ifdef ASSERT
    if (old_region_balance > 0) {
      assert(old_region_balance <= checked_cast<ssize_t>(old_unaffiliated_regions), "Cannot transfer regions that are affiliated");
    } else {
      assert(0 - old_region_balance <= checked_cast<ssize_t>(young_unaffiliated_regions), "Cannot transfer regions that are affiliated");
    }
#endif

    ssize_t xfer_bytes = old_region_balance * checked_cast<ssize_t>(region_size_bytes);
    old_available -= xfer_bytes;
    old_unaffiliated_regions -= old_region_balance;
    young_capacity += xfer_bytes;
    young_unaffiliated_regions += old_region_balance;
  }

  // All allocations taken from the old collector set are performed by GC, generally using PLABs for both
  // promotions and evacuations.  The partition between which old memory is reserved for evacuation and
  // which is reserved for promotion is enforced using thread-local variables that prescribe intentions for
  // each PLAB's available memory.
  if (have_evacuation_reserves) {
    // We are rebuilding at the end of final mark, having already established evacuation budgets for this GC pass.
    const size_t promoted_reserve = old_generation->get_promoted_reserve();
    const size_t old_evac_reserve = old_generation->get_evacuation_reserve();
    young_reserve_result = young_generation->get_evacuation_reserve();
    old_reserve_result = promoted_reserve + old_evac_reserve;
    assert(old_reserve_result <= old_available,
           "Cannot reserve (%zu + %zu) more OLD than is available: %zu",
           promoted_reserve, old_evac_reserve, old_available);
  } else {
    // We are rebuilding at end of GC, so we set aside budgets specified on command line (or defaults)
    young_reserve_result = (young_capacity * ShenandoahEvacReserve) / 100;
    // The auto-sizer has already made old-gen large enough to hold all anticipated evacuations and promotions.
    // Affiliated old-gen regions are already in the OldCollector free set.  Add in the relevant number of
    // unaffiliated regions.
    old_reserve_result = old_available;
  }

  // Old available regions that have less than PLAB::min_size() of available memory are not placed into the OldCollector
  // free set.  Because of this, old_available may not have enough memory to represent the intended reserve.  Adjust
  // the reserve downward to account for this possibility. This loss is part of the reason why the original budget
  // was adjusted with ShenandoahOldEvacWaste and ShenandoahOldPromoWaste multipliers.
  if (old_reserve_result >
      _partitions.capacity_of(ShenandoahFreeSetPartitionId::OldCollector) + old_unaffiliated_regions * region_size_bytes) {
    old_reserve_result =
      _partitions.capacity_of(ShenandoahFreeSetPartitionId::OldCollector) + old_unaffiliated_regions * region_size_bytes;
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
void ShenandoahFreeSet::reserve_regions(size_t to_reserve, size_t to_reserve_old, size_t &old_region_count) {
  for (size_t i = _heap->num_regions(); i > 0; i--) {
    size_t idx = i - 1;
    ShenandoahHeapRegion* r = _heap->get_region(idx);
    if (!_partitions.in_free_set(ShenandoahFreeSetPartitionId::Mutator, idx)) {
      continue;
    }

    size_t ac = alloc_capacity(r);
    assert (ac > 0, "Membership in free set implies has capacity");
    assert (!r->is_old() || r->is_trash(), "Except for trash, mutator_is_free regions should not be affiliated OLD");

    bool move_to_old_collector = _partitions.available_in(ShenandoahFreeSetPartitionId::OldCollector) < to_reserve_old;
    bool move_to_collector = _partitions.available_in(ShenandoahFreeSetPartitionId::Collector) < to_reserve;

    if (!move_to_collector && !move_to_old_collector) {
      // We've satisfied both to_reserve and to_reserved_old
      break;
    }

    if (move_to_old_collector) {
      // We give priority to OldCollector partition because we desire to pack OldCollector regions into higher
      // addresses than Collector regions.  Presumably, OldCollector regions are more "stable" and less likely to
      // be collected in the near future.
      if (r->is_trash() || !r->is_affiliated()) {
        // OLD regions that have available memory are already in the old_collector free set.
        _partitions.move_from_partition_to_partition(idx, ShenandoahFreeSetPartitionId::Mutator,
                                                     ShenandoahFreeSetPartitionId::OldCollector, ac);
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
      _partitions.move_from_partition_to_partition(idx, ShenandoahFreeSetPartitionId::Mutator,
                                                   ShenandoahFreeSetPartitionId::Collector, ac);
      log_trace(gc, free)("  Shifting region %zu from mutator_free to collector_free", idx);
      log_trace(gc, free)("  Shifted Mutator range [%zd, %zd],"
                          "  Collector range [%zd, %zd]",
                          _partitions.leftmost(ShenandoahFreeSetPartitionId::Mutator),
                          _partitions.rightmost(ShenandoahFreeSetPartitionId::Mutator),
                          _partitions.leftmost(ShenandoahFreeSetPartitionId::Collector),
                          _partitions.rightmost(ShenandoahFreeSetPartitionId::Collector));

    }
  }

  if (LogTarget(Info, gc, free)::is_enabled()) {
    size_t old_reserve = _partitions.available_in(ShenandoahFreeSetPartitionId::OldCollector);
    if (old_reserve < to_reserve_old) {
      log_info(gc, free)("Wanted " PROPERFMT " for old reserve, but only reserved: " PROPERFMT,
                         PROPERFMTARGS(to_reserve_old), PROPERFMTARGS(old_reserve));
    }
    size_t reserve = _partitions.available_in(ShenandoahFreeSetPartitionId::Collector);
    if (reserve < to_reserve) {
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
          if (r->is_empty()) {
            total_free_ext += free;
            if (last_idx + 1 == idx) {
              empty_contig++;
            } else {
              empty_contig = 1;
            }
          } else {
            empty_contig = 0;
          }
          total_used += r->used();
          total_free += free;
          max_contig = MAX2(max_contig, empty_contig);
          last_idx = idx;
        }
      }

      size_t max_humongous = max_contig * ShenandoahHeapRegion::region_size_bytes();
      size_t free = capacity() - used();

      // Since certain regions that belonged to the Mutator free partition at the time of most recent rebuild may have been
      // retired, the sum of used and capacities within regions that are still in the Mutator free partition may not match
      // my internally tracked values of used() and free().
      assert(free == total_free, "Free memory should match");
      ls.print("Free: %zu%s, Max: %zu%s regular, %zu%s humongous, ",
               byte_size_in_proper_unit(total_free),    proper_unit_for_byte_size(total_free),
               byte_size_in_proper_unit(max),           proper_unit_for_byte_size(max),
               byte_size_in_proper_unit(max_humongous), proper_unit_for_byte_size(max_humongous)
      );

      ls.print("Frag: ");
      size_t frag_ext;
      if (total_free_ext > 0) {
        frag_ext = 100 - (100 * max_humongous / total_free_ext);
      } else {
        frag_ext = 0;
      }
      ls.print("%zu%% external, ", frag_ext);

      size_t frag_int;
      if (_partitions.count(ShenandoahFreeSetPartitionId::Mutator) > 0) {
        frag_int = (100 * (total_used / _partitions.count(ShenandoahFreeSetPartitionId::Mutator))
                    / ShenandoahHeapRegion::region_size_bytes());
      } else {
        frag_int = 0;
      }
      ls.print("%zu%% internal; ", frag_int);
      ls.print("Used: %zu%s, Mutator Free: %zu",
               byte_size_in_proper_unit(total_used), proper_unit_for_byte_size(total_used),
               _partitions.count(ShenandoahFreeSetPartitionId::Mutator));
    }

    {
      size_t max = 0;
      size_t total_free = 0;
      size_t total_used = 0;

      for (idx_t idx = _partitions.leftmost(ShenandoahFreeSetPartitionId::Collector);
           idx <= _partitions.rightmost(ShenandoahFreeSetPartitionId::Collector); idx++) {
        if (_partitions.in_free_set(ShenandoahFreeSetPartitionId::Collector, idx)) {
          ShenandoahHeapRegion *r = _heap->get_region(idx);
          size_t free = alloc_capacity(r);
          max = MAX2(max, free);
          total_free += free;
          total_used += r->used();
        }
      }
      ls.print(" Collector Reserve: %zu%s, Max: %zu%s; Used: %zu%s",
               byte_size_in_proper_unit(total_free), proper_unit_for_byte_size(total_free),
               byte_size_in_proper_unit(max),        proper_unit_for_byte_size(max),
               byte_size_in_proper_unit(total_used), proper_unit_for_byte_size(total_used));
    }

    if (_heap->mode()->is_generational()) {
      size_t max = 0;
      size_t total_free = 0;
      size_t total_used = 0;

      for (idx_t idx = _partitions.leftmost(ShenandoahFreeSetPartitionId::OldCollector);
           idx <= _partitions.rightmost(ShenandoahFreeSetPartitionId::OldCollector); idx++) {
        if (_partitions.in_free_set(ShenandoahFreeSetPartitionId::OldCollector, idx)) {
          ShenandoahHeapRegion *r = _heap->get_region(idx);
          size_t free = alloc_capacity(r);
          max = MAX2(max, free);
          total_free += free;
          total_used += r->used();
        }
      }
      ls.print_cr(" Old Collector Reserve: %zu%s, Max: %zu%s; Used: %zu%s",
                  byte_size_in_proper_unit(total_free), proper_unit_for_byte_size(total_free),
                  byte_size_in_proper_unit(max),        proper_unit_for_byte_size(max),
                  byte_size_in_proper_unit(total_used), proper_unit_for_byte_size(total_used));
    }
  }
}

HeapWord* ShenandoahFreeSet::allocate(ShenandoahAllocRequest& req, bool& in_new_region) {
  shenandoah_assert_heaplocked();
  if (ShenandoahHeapRegion::requires_humongous(req.size())) {
    switch (req.type()) {
      case ShenandoahAllocRequest::_alloc_shared:
      case ShenandoahAllocRequest::_alloc_shared_gc:
        in_new_region = true;
        return allocate_contiguous(req);
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

