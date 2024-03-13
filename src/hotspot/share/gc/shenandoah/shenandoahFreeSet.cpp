/*
 * Copyright (c) 2016, 2021, Red Hat, Inc. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/shared/tlab_globals.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahFreeSet.inline.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegionSet.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.inline.hpp"
#include "logging/logStream.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/orderAccess.hpp"

static const char* partition_name(ShenandoahFreeSetPartitionId t) {
  switch (t) {
    case NotFree: return "NotFree";
    case Mutator: return "Mutator";
    case Collector: return "Collector";
    default: return "Unrecognized";
  }
}

size_t ShenandoahSimpleBitMap::count_leading_ones(ssize_t start_idx) const {
  assert((start_idx >= 0) && (start_idx < _num_bits), "precondition");
  size_t array_idx = start_idx >> LogBitsPerWord;
  size_t element_bits = _bitmap[array_idx];
  size_t bit_number = start_idx & right_n_bits(LogBitsPerWord);
  size_t the_bit = nth_bit(bit_number);
  size_t omit_mask = right_n_bits(bit_number);
  size_t mask = right_n_bits(BitsPerWord) & ~omit_mask;

  if ((element_bits & mask) == mask) {
    size_t counted_ones = BitsPerWord - bit_number;
    return counted_ones + count_leading_ones(start_idx - counted_ones);
  } else {
    size_t counted_ones;
    for (counted_ones = 0; element_bits & the_bit; counted_ones++) {
      the_bit <<= 1;
    }
    return counted_ones;
  }
}

size_t ShenandoahSimpleBitMap::count_trailing_ones(ssize_t last_idx) const {
  assert((last_idx >= 0) && (last_idx < _num_bits), "precondition");
  size_t array_idx = last_idx >> LogBitsPerWord;
  size_t element_bits = _bitmap[array_idx];
  size_t bit_number = last_idx & right_n_bits(LogBitsPerWord);
  size_t the_bit = nth_bit(bit_number);

  // All ones from bit 0 to the_bit
  size_t mask = right_n_bits(bit_number + 1);
  if ((element_bits & mask) == mask) {
    size_t counted_ones = bit_number + 1;
    return counted_ones + count_trailing_ones(last_idx - counted_ones);
  } else {
    size_t counted_ones;
    for (counted_ones = 0; element_bits & the_bit; counted_ones++) {
      the_bit >>= 1;
    }
    return counted_ones;
  }
}

bool ShenandoahSimpleBitMap::is_forward_consecutive_ones(ssize_t start_idx, ssize_t count) const {
  assert((start_idx >= 0) && (start_idx < _num_bits), "precondition: start_idx: " SSIZE_FORMAT ", count: " SSIZE_FORMAT,
         start_idx, count);
  assert(start_idx + count <= (ssize_t) _num_bits, "precondition");
  size_t array_idx = start_idx >> LogBitsPerWord;
  size_t bit_number = start_idx & right_n_bits(LogBitsPerWord);
  size_t the_bit = nth_bit(bit_number);
  size_t element_bits = _bitmap[array_idx];
  if ((ssize_t) (bit_number + count <= BitsPerWord)) {
    // All relevant bits reside within this array element
    size_t relevant_bits = bit_number + count;
    size_t overreach_mask = right_n_bits(relevant_bits);
    size_t exclude_mask = right_n_bits(bit_number);
    size_t exact_mask = overreach_mask & ~exclude_mask;
    return (element_bits & exact_mask) == exact_mask? true: false;
  } else {
    // Need to exactly match all relevant bits of this array element, plus relevant bits of following array elements
    size_t overreach_mask = right_n_bits(BitsPerWord);
    size_t exclude_mask = right_n_bits(bit_number);
    size_t exact_mask = overreach_mask & ~exclude_mask;
    if ((element_bits & exact_mask) == exact_mask) {
      size_t matched_bits = BitsPerWord - bit_number;
      return is_forward_consecutive_ones(start_idx + matched_bits, count - matched_bits);
    } else {
      return false;
    }
  }
}

bool ShenandoahSimpleBitMap::is_backward_consecutive_ones(ssize_t last_idx, ssize_t count) const {
  assert((last_idx >= 0) && (last_idx < _num_bits), "precondition");
  assert(last_idx - count >= -1, "precondition");
  size_t array_idx = last_idx >> LogBitsPerWord;
  size_t bit_number = last_idx & right_n_bits(LogBitsPerWord);
  size_t the_bit = nth_bit(bit_number);
  size_t element_bits = _bitmap[array_idx];
  if ((ssize_t) (bit_number + 1) >= count) {
    // All relevant bits reside within this array element
    size_t overreach_mask = right_n_bits(bit_number + 1);
    size_t exclude_mask = right_n_bits(bit_number + 1 - count);
    size_t exact_mask = overreach_mask & ~exclude_mask;
    return (element_bits & exact_mask) == exact_mask? true: false;
  } else {
    // Need to exactly match all relevant bits of this array element, plus relevant bits of following array elements
    size_t exact_mask = right_n_bits(bit_number + 1);
    if ((element_bits & exact_mask) == exact_mask) {
      size_t matched_bits = bit_number + 1;
      return is_backward_consecutive_ones(last_idx - matched_bits, count - matched_bits);
    } else {
      return false;
    }
  }
}

ssize_t ShenandoahSimpleBitMap::find_next_consecutive_bits(size_t num_bits, ssize_t start_idx, ssize_t boundary_idx) const {
  assert((start_idx >= 0) && (start_idx < _num_bits), "precondition");

  // Stop looking if there are not num_bits remaining in probe space.
  ssize_t start_boundary = boundary_idx - num_bits;
  size_t array_idx = start_idx >> LogBitsPerWord;
  size_t bit_number = start_idx & right_n_bits(LogBitsPerWord);
  size_t element_bits = _bitmap[array_idx];
  if (bit_number > 0) {
    size_t mask_out = right_n_bits(bit_number);
    element_bits &= ~mask_out;
  }

  while (start_idx <= start_boundary) {
    if (!element_bits) {
      // move to the next element
      start_idx += BitsPerWord - bit_number;
      array_idx++;
      bit_number = 0;
      element_bits = _bitmap[array_idx];
    } else if (is_forward_consecutive_ones(start_idx, num_bits)) {
      return start_idx;
    } else {
      // There is at least one zero bit in this span.  Align the next probe at the start of trailing ones for probed span.
      size_t trailing_ones = count_trailing_ones(start_idx + num_bits - 1);
      start_idx += num_bits - trailing_ones;
      array_idx = start_idx >> LogBitsPerWord;
      element_bits = _bitmap[array_idx];
      bit_number = start_idx & right_n_bits(LogBitsPerWord);
      if (bit_number > 0) {
        size_t mask_out = right_n_bits(bit_number);
        element_bits &= ~mask_out;
      }
    }
  }
  // No match found.
  return boundary_idx;
}

ssize_t ShenandoahSimpleBitMap::find_prev_consecutive_bits(
  const size_t num_bits, ssize_t last_idx, const ssize_t boundary_idx) const {
  assert((last_idx >= 0) && (last_idx < _num_bits), "precondition");

  // Stop looking if there are not num_bits remaining in probe space.
  ssize_t last_boundary = boundary_idx + num_bits;
  ssize_t array_idx = last_idx >> LogBitsPerWord;
  size_t bit_number = last_idx & right_n_bits(LogBitsPerWord);
  size_t element_bits = _bitmap[array_idx];
  if (bit_number < BitsPerWord - 1) {
    size_t mask_in = right_n_bits(bit_number + 1);
    element_bits &= mask_in;
  }
  while (last_idx >= last_boundary) {
    if (!element_bits) {
      // move to the previous element
      last_idx -= bit_number + 1;
      array_idx--;
      bit_number = BitsPerWord - 1;
      element_bits = _bitmap[array_idx];
    } else if (is_backward_consecutive_ones(last_idx, num_bits)) {
      return last_idx + 1 - num_bits;
    } else {
      // There is at least one zero bit in this span.  Align the next probe at the end of leading ones for probed span.
      size_t leading_ones = count_leading_ones(last_idx - (num_bits - 1));
      last_idx -= num_bits - leading_ones;
      array_idx = last_idx >> LogBitsPerWord;
      bit_number = last_idx & right_n_bits(LogBitsPerWord);
      element_bits = _bitmap[array_idx];
      if (bit_number < BitsPerWord - 1){
        size_t mask_in = right_n_bits(bit_number + 1);
        element_bits &= mask_in;
      }
    }
  }
  // No match found.
  return boundary_idx;
}

void ShenandoahRegionPartitions::dump_bitmap_all() const {
  printf("Mutator range [" SSIZE_FORMAT ", " SSIZE_FORMAT "], Collector range [" SSIZE_FORMAT ", " SSIZE_FORMAT "]",
               _leftmosts[Mutator], _rightmosts[Mutator], _leftmosts[Collector], _rightmosts[Collector]);
  printf("Empty Mutator range [" SSIZE_FORMAT ", " SSIZE_FORMAT
               "], Empty Collector range [" SSIZE_FORMAT ", " SSIZE_FORMAT "]",
               _leftmosts_empty[Mutator], _rightmosts_empty[Mutator],
               _leftmosts_empty[Collector], _rightmosts_empty[Collector]);

#ifdef _LP64
  printf("%6s: %18s %18s %18s", "index", "Mutator Bits", "Collector Bits", "NotFree Bits");
#else
  printf("%6s: %10s %10s %10s", "index", "Mutator Bits", "Collector Bits", "NotFree Bits");
#endif
  dump_bitmap_range(0, _max-1);
}

void ShenandoahRegionPartitions::dump_bitmap_range(ssize_t start_idx, ssize_t end_idx) const {
  assert((start_idx >= 0) && (start_idx < (ssize_t) _max), "precondition");
  assert((end_idx >= 0) && (end_idx < (ssize_t) _max), "precondition");
  ssize_t aligned_start = _membership[Mutator].aligned_index(start_idx);
  ssize_t aligned_end = _membership[Mutator].aligned_index(end_idx);
  ssize_t alignment = _membership[Mutator].alignment();
  while (aligned_start <= aligned_end) {
    dump_bitmap_row(aligned_start);
    aligned_start += alignment;
  }
}

void ShenandoahRegionPartitions::dump_bitmap_row(ssize_t idx) const {
  assert((idx >= 0) && (idx < (ssize_t) _max), "precondition");
  ssize_t aligned_idx = _membership[Mutator].aligned_index(idx);
  size_t mutator_bits = _membership[Mutator].bits_at(aligned_idx);
  size_t collector_bits = _membership[Collector].bits_at(aligned_idx);
  size_t free_bits = mutator_bits | collector_bits;
  size_t notfree_bits =  ~free_bits;
  log_info(gc)(SSIZE_FORMAT_W(6) ": " SIZE_FORMAT_X_0 " 0x" SIZE_FORMAT_X_0 " 0x" SIZE_FORMAT_X_0,
               aligned_idx, mutator_bits, collector_bits, notfree_bits);
}

ShenandoahRegionPartitions::ShenandoahRegionPartitions(size_t max_regions, ShenandoahFreeSet* free_set) :
    _max(max_regions),
    _region_size_bytes(ShenandoahHeapRegion::region_size_bytes()),
    _free_set(free_set),
    _membership{ ShenandoahSimpleBitMap(max_regions), ShenandoahSimpleBitMap(max_regions) }
{
  make_all_regions_unavailable();
}

ShenandoahRegionPartitions::~ShenandoahRegionPartitions() {
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

inline ssize_t ShenandoahRegionPartitions:: leftmost(ShenandoahFreeSetPartitionId which_partition) const {
  assert (which_partition < NumPartitions, "selected free partition must be valid");
  ssize_t idx = _leftmosts[which_partition];
  if (idx >= _max) {
    return _max;
  } else {
    // _membership[which_partition].is_set(idx) may not be true if we are shrinking the interval
    return idx;
  }
}

inline ssize_t ShenandoahRegionPartitions::rightmost(ShenandoahFreeSetPartitionId which_partition) const {
  assert (which_partition < NumPartitions, "selected free partition must be valid");
  ssize_t idx = _rightmosts[which_partition];
  // _membership[which_partition].is_set(idx) may not be true if we are shrinking the interval
  return idx;
}

void ShenandoahRegionPartitions::make_all_regions_unavailable() {
  for (size_t partition_id = 0; partition_id < NumPartitions; partition_id++) {
    _membership[partition_id].clear_all();
    _leftmosts[partition_id] = _max;
    _rightmosts[partition_id] = -1;
    _leftmosts_empty[partition_id] = _max;
    _rightmosts_empty[partition_id] = -1;;
    _capacity[partition_id] = 0;
    _used[partition_id] = 0;
  }
  _region_counts[Mutator] = _region_counts[Collector] = 0;
}

void ShenandoahRegionPartitions::establish_intervals(ssize_t mutator_leftmost, ssize_t mutator_rightmost,
                                                     ssize_t mutator_leftmost_empty, ssize_t mutator_rightmost_empty,
                                                     size_t mutator_region_count, size_t mutator_used) {
  _region_counts[Mutator] = mutator_region_count;
  _leftmosts[Mutator] = mutator_leftmost;
  _rightmosts[Mutator] = mutator_rightmost;
  _leftmosts_empty[Mutator] = mutator_leftmost_empty;
  _rightmosts_empty[Mutator] = mutator_rightmost_empty;

  _region_counts[Mutator] = mutator_region_count;
  _used[Mutator] = mutator_used;
  _capacity[Mutator] = mutator_region_count * _region_size_bytes;

  _leftmosts[Collector] = _max;
  _rightmosts[Collector] = -1;
  _leftmosts_empty[Collector] = _max;
  _rightmosts_empty[Collector] = -1;

  _region_counts[Collector] = 0;
  _used[Collector] = 0;
  _capacity[Collector] = 0;
}

void ShenandoahRegionPartitions::increase_used(ShenandoahFreeSetPartitionId which_partition, size_t bytes) {
  assert (which_partition < NumPartitions, "Partition must be valid");
  _used[which_partition] += bytes;
  assert (_used[which_partition] <= _capacity[which_partition],
          "Must not use (" SIZE_FORMAT ") more than capacity (" SIZE_FORMAT ") after increase by " SIZE_FORMAT,
          _used[which_partition], _capacity[which_partition], bytes);
}

inline void ShenandoahRegionPartitions::shrink_interval_if_range_modifies_either_boundary(
  ShenandoahFreeSetPartitionId partition, ssize_t low_idx, ssize_t high_idx) {
  assert((low_idx <= high_idx) && (low_idx >= 0) && (high_idx < _max), "Range must span legal index values");
  if (low_idx == leftmost(partition)) {
    assert (!_membership[partition].is_set(low_idx), "Do not shrink interval if region not removed");
    if (high_idx + 1 == _max) {
      _leftmosts[partition] = _max;
    } else {
      _leftmosts[partition] = find_index_of_next_available_region(partition, high_idx + 1);
    }
    if (leftmost_empty(partition) < leftmost(partition)) {
      // This gets us closer to where we need to be; we'll scan further when leftmosts_empty is requested.
      _leftmosts_empty[partition] = leftmost(partition);
    }
  }
  if (high_idx == rightmost(partition)) {
    assert (!_membership[partition].is_set(high_idx), "Do not shrink interval if region not removed");
    if (low_idx == 0) {
      _rightmosts[partition] = -1;
    } else {
      _rightmosts[partition] = find_index_of_previous_available_region(partition, low_idx - 1);
    }
    if (rightmost_empty(partition) > rightmost(partition)) {
      // This gets us closer to where we need to be; we'll scan further when rightmosts_empty is requested.
      _rightmosts_empty[partition] = rightmost(partition);
    }
  }
  if (leftmost(partition) > rightmost(partition)) {
    _leftmosts[partition] = _max;
    _rightmosts[partition] = -1;
    _leftmosts_empty[partition] = _max;
    _rightmosts_empty[partition] = -1;
  }
}

inline void ShenandoahRegionPartitions::shrink_interval_if_boundary_modified(ShenandoahFreeSetPartitionId partition, ssize_t idx) {
  assert((idx >= 0) && (idx < _max), "Range must span legal index values");
  if (idx == leftmost(partition)) {
    assert (!_membership[partition].is_set(idx), "Do not shrink interval if region not removed");
    if (idx + 1 == _max) {
      _leftmosts[partition] = _max;
    } else {
      _leftmosts[partition] = find_index_of_next_available_region(partition, idx + 1);
    }
    if (leftmost_empty(partition) < leftmost(partition)) {
      // This gets us closer to where we need to be; we'll scan further when leftmosts_empty is requested.
      _leftmosts_empty[partition] = leftmost(partition);
    }
  }
  if (idx == rightmost(partition)) {
    assert (!_membership[partition].is_set(idx), "Do not shrink interval if region not removed");
    if (idx == 0) {
      _rightmosts[partition] = -1;
    } else {
      _rightmosts[partition] = find_index_of_previous_available_region(partition, idx - 1);
    }
    if (rightmost_empty(partition) > rightmost(partition)) {
      // This gets us closer to where we need to be; we'll scan further when rightmosts_empty is requested.
      _rightmosts_empty[partition] = rightmost(partition);
    }
  }
  if (leftmost(partition) > rightmost(partition)) {
    _leftmosts[partition] = _max;
    _rightmosts[partition] = -1;
    _leftmosts_empty[partition] = _max;
    _rightmosts_empty[partition] = -1;
  }
}

inline void ShenandoahRegionPartitions::expand_interval_if_boundary_modified(ShenandoahFreeSetPartitionId partition,
                                                                             ssize_t idx, size_t region_available) {
  if (leftmost(partition) > idx) {
    _leftmosts[partition] = idx;
  }
  if (rightmost(partition) < idx) {
    _rightmosts[partition] = idx;
  }
  if (region_available == _region_size_bytes) {
    if (leftmost_empty(partition) > idx) {
      _leftmosts_empty[partition] = idx;
    }
    if (rightmost_empty(partition) < idx) {
      _rightmosts_empty[partition] = idx;
    }
  }
}

void ShenandoahRegionPartitions::retire_range_from_partition(
  ShenandoahFreeSetPartitionId partition, ssize_t low_idx, ssize_t high_idx) {

  // Note: we may remove from free partition even if region is not entirely full, such as when available < PLAB::min_size()
  assert ((low_idx < _max) && (high_idx < _max), "Both indices are sane: " SIZE_FORMAT " and " SIZE_FORMAT " < " SIZE_FORMAT,
          low_idx, high_idx, _max);
  assert (partition < NumPartitions, "Cannot remove from free partitions if not already free");

  for (ssize_t idx = low_idx; idx <= high_idx; idx++) {
    assert (in_free_set(partition, idx), "Must be in partition to remove from partition");
    _membership[partition].clear_bit(idx);
  }
  _region_counts[partition] -= high_idx + 1 - low_idx;
  shrink_interval_if_range_modifies_either_boundary(partition, low_idx, high_idx);
}

void ShenandoahRegionPartitions::retire_from_partition(ShenandoahFreeSetPartitionId partition, ssize_t idx, size_t used_bytes) {

  // Note: we may remove from free partition even if region is not entirely full, such as when available < PLAB::min_size()
  assert (idx < _max, "index is sane: " SIZE_FORMAT " < " SIZE_FORMAT, idx, _max);
  assert (partition < NumPartitions, "Cannot remove from free partitions if not already free");
  assert (in_free_set(partition, idx), "Must be in partition to remove from partition");

  if (used_bytes < _region_size_bytes) {
    // Count the alignment pad remnant of memory as used when we retire this region
    increase_used(partition, _region_size_bytes - used_bytes);
  }
  _membership[partition].clear_bit(idx);
  shrink_interval_if_boundary_modified(partition, idx);
  _region_counts[partition]--;
}

void ShenandoahRegionPartitions::make_free(ssize_t idx, ShenandoahFreeSetPartitionId which_partition, size_t available) {
  assert (idx < _max, "index is sane: " SIZE_FORMAT " < " SIZE_FORMAT, idx, _max);
  assert (membership(idx) == NotFree, "Cannot make free if already free");
  assert (which_partition < NumPartitions, "selected free partition must be valid");
  assert (available <= _region_size_bytes, "Available cannot exceed region size");

  _membership[which_partition].set_bit(idx);
  _capacity[which_partition] += _region_size_bytes;
  _used[which_partition] += _region_size_bytes - available;
  expand_interval_if_boundary_modified(which_partition, idx, available);

  _region_counts[which_partition]++;
}

void ShenandoahRegionPartitions::move_from_partition_to_partition(ssize_t idx, ShenandoahFreeSetPartitionId orig_partition,
                                                                  ShenandoahFreeSetPartitionId new_partition, size_t available) {
  assert (idx < _max, "index is sane: " SIZE_FORMAT " < " SIZE_FORMAT, idx, _max);
  assert (orig_partition < NumPartitions, "Original partition must be valid");
  assert (new_partition < NumPartitions, "New partition must be valid");
  assert (available <= _region_size_bytes, "Available cannot exceed region size");

  // Expected transitions:
  //  During rebuild:         Mutator => Collector
  //  During flip_to_gc:      Mutator empty => Collector
  // At start of update refs: Collector => Mutator
  assert (((available <= _region_size_bytes) &&
           (((orig_partition == Mutator) && (new_partition == Collector)) ||
            ((orig_partition == Collector) && (new_partition == Mutator)))) ||
          ((available == _region_size_bytes) &&
           ((orig_partition == Mutator) && (new_partition == Collector))), "Unexpected movement between partitions");


  size_t used = _region_size_bytes - available;

  _membership[orig_partition].clear_bit(idx);
  _membership[new_partition].set_bit(idx);

  _capacity[orig_partition] -= _region_size_bytes;
  _used[orig_partition] -= used;
  shrink_interval_if_boundary_modified(orig_partition, idx);

  _capacity[new_partition] += _region_size_bytes;;
  _used[new_partition] += used;
  expand_interval_if_boundary_modified(new_partition, idx, available);

  _region_counts[orig_partition]--;
  _region_counts[new_partition]++;
}

const char* ShenandoahRegionPartitions::partition_membership_name(ssize_t idx) const {
  assert (idx < _max, "index is sane: " SIZE_FORMAT " < " SIZE_FORMAT, idx, _max);
  ShenandoahFreeSetPartitionId result = NotFree;
  for (uint partition_id = 0; partition_id < NumPartitions; partition_id++) {
    if (_membership[partition_id].is_set(idx)) {
      assert(result == NotFree, "Region should reside in only one partition");
      result = (ShenandoahFreeSetPartitionId) partition_id;
    }
  }
  return partition_name(result);
}


#ifdef ASSERT
inline ShenandoahFreeSetPartitionId ShenandoahRegionPartitions::membership(ssize_t idx) const {
  assert (idx < _max, "index is sane: " SIZE_FORMAT " < " SIZE_FORMAT, idx, _max);
  ShenandoahFreeSetPartitionId result = NotFree;
  for (uint partition_id = 0; partition_id < NumPartitions; partition_id++) {
    if (_membership[partition_id].is_set(idx)) {
      assert(result == NotFree, "Region should reside in only one partition");
      result = (ShenandoahFreeSetPartitionId) partition_id;
    }
  }
  return result;
}

inline bool ShenandoahRegionPartitions::partition_id_matches(ssize_t idx, ShenandoahFreeSetPartitionId test_partition) const {
  assert (idx < _max, "index is sane: " SIZE_FORMAT " < " SIZE_FORMAT, idx, _max);
  assert (test_partition < NotFree, "must be a valid partition");

  return membership(idx) == test_partition;
}
#endif

inline bool ShenandoahRegionPartitions::is_empty(ShenandoahFreeSetPartitionId which_partition) const {
  assert (which_partition < NumPartitions, "selected free partition must be valid");
  return (leftmost(which_partition) > rightmost(which_partition));
}

inline ssize_t ShenandoahRegionPartitions::find_index_of_next_available_region(
  ShenandoahFreeSetPartitionId which_partition, ssize_t start_index) const {
  ssize_t rightmost_idx = rightmost(which_partition);
  ssize_t leftmost_idx = leftmost(which_partition);
  if ((rightmost_idx < leftmost_idx) || (start_index > rightmost_idx)) return _max;
  if (start_index < leftmost_idx) {
    start_index = leftmost_idx;
  }
  ssize_t result = _membership[which_partition].find_next_set_bit(start_index, rightmost_idx + 1);
  return (result > rightmost_idx)? _max: result;
}

inline ssize_t ShenandoahRegionPartitions::find_index_of_previous_available_region(
  ShenandoahFreeSetPartitionId which_partition, ssize_t last_index) const {
  ssize_t rightmost_idx = rightmost(which_partition);
  ssize_t leftmost_idx = leftmost(which_partition);
  // if (leftmost_idx == max) then (last_index < leftmost_idx)
  if (last_index < leftmost_idx) return -1;
  if (last_index > rightmost_idx) {
    last_index = rightmost_idx;
  }
  ssize_t result = _membership[which_partition].find_prev_set_bit(last_index, -1);
  return (result < leftmost_idx)? -1: result;
}

inline ssize_t ShenandoahRegionPartitions::find_index_of_next_available_cluster_of_regions(
  ShenandoahFreeSetPartitionId which_partition, ssize_t start_index, size_t cluster_size) const {
  ssize_t rightmost_idx = rightmost(which_partition);
  ssize_t leftmost_idx = leftmost(which_partition);
  if ((rightmost_idx < leftmost_idx) || (start_index > rightmost_idx)) return _max;
  ssize_t result = _membership[which_partition].find_next_consecutive_bits(cluster_size, start_index, rightmost_idx + 1);
  return (result > rightmost_idx)? _max: result;
}

inline ssize_t ShenandoahRegionPartitions::find_index_of_previous_available_cluster_of_regions(
  ShenandoahFreeSetPartitionId which_partition, ssize_t last_index, size_t cluster_size) const {
  ssize_t leftmost_idx = leftmost(which_partition);
  // if (leftmost_idx == max) then (last_index < leftmost_idx)
  if (last_index < leftmost_idx) return -1;
  ssize_t result = _membership[which_partition].find_prev_consecutive_bits(cluster_size, last_index, leftmost_idx - 1);
  return (result <= leftmost_idx)? -1: result;
}

ssize_t ShenandoahRegionPartitions::leftmost_empty(ShenandoahFreeSetPartitionId which_partition) {
  assert (which_partition < NumPartitions, "selected free partition must be valid");
  ssize_t max_regions = _max;
  if (_leftmosts_empty[which_partition] == _max) {
    return _max;
  }
  for (ssize_t idx = find_index_of_next_available_region(which_partition, _leftmosts_empty[which_partition]);
       idx < max_regions; ) {
    assert(in_free_set(which_partition, idx), "Boundaries or find_prev_set_bit failed: " SSIZE_FORMAT, idx);
    if (_free_set->alloc_capacity(idx) == _region_size_bytes) {
      _leftmosts_empty[which_partition] = idx;
      return idx;
    }
    idx = find_index_of_next_available_region(which_partition, idx + 1);
  }
  _leftmosts_empty[which_partition] = _max;
  _rightmosts_empty[which_partition] = -1;
  return _max;
}

ssize_t ShenandoahRegionPartitions::rightmost_empty(ShenandoahFreeSetPartitionId which_partition) {
  assert (which_partition < NumPartitions, "selected free partition must be valid");
  if (_rightmosts_empty[which_partition] < 0) {
    return -1;
  }
  for (ssize_t idx = find_index_of_previous_available_region(which_partition, _rightmosts_empty[which_partition]); idx >= 0; ) {
    assert(in_free_set(which_partition, idx), "Boundaries or find_prev_set_bit failed: " SSIZE_FORMAT, idx);
    if (_free_set->alloc_capacity(idx) == _region_size_bytes) {
      _rightmosts_empty[which_partition] = idx;
      return idx;
    }
    idx = find_index_of_previous_available_region(which_partition, idx - 1);
  }
  _leftmosts_empty[which_partition] = _max;
  _rightmosts_empty[which_partition] = -1;
  return -1;
}


#ifdef ASSERT
void ShenandoahRegionPartitions::assert_bounds() {

  ssize_t leftmosts[NumPartitions];
  ssize_t rightmosts[NumPartitions];
  ssize_t empty_leftmosts[NumPartitions];
  ssize_t empty_rightmosts[NumPartitions];

  for (int i = 0; i < NumPartitions; i++) {
    leftmosts[i] = _max;
    empty_leftmosts[i] = _max;
    rightmosts[i] = -1;
    empty_rightmosts[i] = -1;
  }

  for (ssize_t i = 0; i < _max; i++) {
    ShenandoahFreeSetPartitionId partition = membership(i);
    switch (partition) {
      case NotFree:
        break;

      case Mutator:
      case Collector:
      {
        size_t capacity = _free_set->alloc_capacity(i);
        bool is_empty = (capacity == _region_size_bytes);
        assert(capacity > 0, "free regions must have allocation capacity");
        if (i < leftmosts[partition]) {
          leftmosts[partition] = i;
        }
        if (is_empty && (i < empty_leftmosts[partition])) {
          empty_leftmosts[partition] = i;
        }
        if (i > rightmosts[partition]) {
          rightmosts[partition] = i;
        }
        if (is_empty && (i > empty_rightmosts[partition])) {
          empty_rightmosts[partition] = i;
        }
        break;
      }

      default:
        ShouldNotReachHere();
    }
  }

  // Performance invariants. Failing these would not break the free partition, but performance would suffer.
  assert (leftmost(Mutator) <= _max, "leftmost in bounds: "  SSIZE_FORMAT " < " SSIZE_FORMAT, leftmost(Mutator),  _max);
  assert (rightmost(Mutator) < _max, "rightmost in bounds: "  SSIZE_FORMAT " < " SSIZE_FORMAT, rightmost(Mutator),  _max);

  assert (leftmost(Mutator) == _max || partition_id_matches(leftmost(Mutator), Mutator),
          "leftmost region should be free: " SSIZE_FORMAT,  leftmost(Mutator));
  assert (leftmost(Mutator) == _max || partition_id_matches(rightmost(Mutator), Mutator),
          "rightmost region should be free: " SSIZE_FORMAT, rightmost(Mutator));

  // If Mutator partition is empty, leftmosts will both equal max, rightmosts will both equal zero.
  // Likewise for empty region partitions.
  ssize_t beg_off = leftmosts[Mutator];
  ssize_t end_off = rightmosts[Mutator];
  assert (beg_off >= leftmost(Mutator),
          "free regions before the leftmost: " SSIZE_FORMAT ", bound " SSIZE_FORMAT, beg_off, leftmost(Mutator));
  assert (end_off <= rightmost(Mutator),
          "free regions past the rightmost: " SSIZE_FORMAT ", bound " SSIZE_FORMAT,  end_off, rightmost(Mutator));

  beg_off = empty_leftmosts[Mutator];
  end_off = empty_rightmosts[Mutator];
  assert (beg_off >= leftmost_empty(Mutator),
          "free empty regions before the leftmost: " SSIZE_FORMAT ", bound " SSIZE_FORMAT, beg_off, leftmost_empty(Mutator));
  assert (end_off <= rightmost_empty(Mutator),
          "free empty regions past the rightmost: " SSIZE_FORMAT ", bound " SSIZE_FORMAT,  end_off, rightmost_empty(Mutator));

  // Performance invariants. Failing these would not break the free partition, but performance would suffer.
  assert (leftmost(Collector) <= _max, "leftmost in bounds: "  SSIZE_FORMAT " < " SSIZE_FORMAT, leftmost(Collector),  _max);
  assert (rightmost(Collector) < _max, "rightmost in bounds: "  SSIZE_FORMAT " < " SSIZE_FORMAT, rightmost(Collector),  _max);

  assert (leftmost(Collector) == _max || partition_id_matches(leftmost(Collector), Collector),
          "leftmost region should be free: " SSIZE_FORMAT,  leftmost(Collector));
  assert (leftmost(Collector) == _max || partition_id_matches(rightmost(Collector), Collector),
          "rightmost region should be free: " SSIZE_FORMAT, rightmost(Collector));

  // If Collector partition is empty, leftmosts will both equal max, rightmosts will both equal zero.
  // Likewise for empty region partitions.
  beg_off = leftmosts[Collector];
  end_off = rightmosts[Collector];
  assert (beg_off >= leftmost(Collector),
          "free regions before the leftmost: " SSIZE_FORMAT ", bound " SSIZE_FORMAT, beg_off, leftmost(Collector));
  assert (end_off <= rightmost(Collector),
          "free regions past the rightmost: " SSIZE_FORMAT ", bound " SSIZE_FORMAT,  end_off, rightmost(Collector));

  beg_off = empty_leftmosts[Collector];
  end_off = empty_rightmosts[Collector];
  assert (beg_off >= _leftmosts_empty[Collector],
          "free empty regions before the leftmost: " SSIZE_FORMAT ", bound " SSIZE_FORMAT, beg_off, leftmost_empty(Collector));
  assert (end_off <= _rightmosts_empty[Collector],
          "free empty regions past the rightmost: " SSIZE_FORMAT ", bound " SSIZE_FORMAT,  end_off, rightmost_empty(Collector));
}
#endif

ShenandoahFreeSet::ShenandoahFreeSet(ShenandoahHeap* heap, size_t max_regions) :
  _heap(heap),
  _partitions(max_regions, this),
  _right_to_left_bias(false),
  _alloc_bias_weight(0)
{
  clear_internal();
}

HeapWord* ShenandoahFreeSet::allocate_single(ShenandoahAllocRequest& req, bool& in_new_region) {
  shenandoah_assert_heaplocked();

  // Scan the bitmap looking for a first fit.
  //
  // Leftmost and rightmost bounds provide enough caching to quickly find a region from which to allocate.
  //
  // Allocations are biased: GC allocations are taken from the high end of the heap.  Regular (and TLAB)
  // mutator allocations are taken from the middle of heap, below the memory reserved for Collector.
  // Humongous mutator allocations are taken from the bottom of the heap.
  //
  // Free set maintains mutator and collector partitions.  Mutator can only allocate from the
  // Mutator partition.  Collector prefers to allocate from the Collector partition, but may steal
  // regions from the Mutator partition if the Collector partition has been depleted.

  switch (req.type()) {
    case ShenandoahAllocRequest::_alloc_tlab:
    case ShenandoahAllocRequest::_alloc_shared: {
      // Try to allocate in the mutator view
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
        ssize_t non_empty_on_left = _partitions.leftmost_empty(Mutator) - _partitions.leftmost(Mutator);
        ssize_t non_empty_on_right = _partitions.rightmost(Mutator) - _partitions.rightmost_empty(Mutator);
        _right_to_left_bias = (non_empty_on_right > non_empty_on_left);
        _alloc_bias_weight = _InitialAllocBiasWeight;
      }
      if (_right_to_left_bias) {
        // Allocate within mutator free from high memory to low so as to preserve low memory for humongous allocations
        if (!_partitions.is_empty(Mutator)) {
          // Use signed idx.  Otherwise, loop will never terminate.
          ssize_t leftmost = _partitions.leftmost(Mutator);
          for (ssize_t idx = _partitions.rightmost(Mutator); idx >= leftmost; ) {
            assert(_partitions.in_free_set(Mutator, idx), "Boundaries or find_prev_set_bit failed: " SSIZE_FORMAT, idx);
            ShenandoahHeapRegion* r = _heap->get_region(idx);
            // try_allocate_in() increases used if the allocation is successful.
            HeapWord* result;
            size_t min_size = (req.type() == ShenandoahAllocRequest::_alloc_tlab)? req.min_size(): req.size();
            if ((alloc_capacity(r) >= min_size) && ((result = try_allocate_in(r, req, in_new_region)) != nullptr)) {
              return result;
            }
            idx = _partitions.find_index_of_previous_available_region(Mutator, idx - 1);
          }
        }
      } else {
        // Allocate from low to high memory.  This keeps the range of fully empty regions more tightly packed.
        // Note that the most recently allocated regions tend not to be evacuated in a given GC cycle.  So this
        // tends to accumulate "fragmented" uncollected regions in high memory.
        if (!_partitions.is_empty(Mutator)) {
          // Use signed idx.  Otherwise, loop will never terminate.
          ssize_t rightmost = _partitions.rightmost(Mutator);
          for (ssize_t idx = _partitions.leftmost(Mutator); idx <= rightmost; ) {
            assert(_partitions.in_free_set(Mutator, idx), "Boundaries or find_prev_set_bit failed: " SSIZE_FORMAT, idx);
            ShenandoahHeapRegion* r = _heap->get_region(idx);
            // try_allocate_in() increases used if the allocation is successful.
            HeapWord* result;
            size_t min_size = (req.type() == ShenandoahAllocRequest::_alloc_tlab)? req.min_size(): req.size();
            if ((alloc_capacity(r) >= min_size) && ((result = try_allocate_in(r, req, in_new_region)) != nullptr)) {
              return result;
            }
            idx = _partitions.find_index_of_next_available_region(Mutator, idx + 1);
          }
        }
      }
      // There is no recovery. Mutator does not touch collector view at all.
      break;
    }
    case ShenandoahAllocRequest::_alloc_gclab:
      // GCLABs are for evacuation so we must be in evacuation phase.

    case ShenandoahAllocRequest::_alloc_shared_gc: {
      // Fast-path: try to allocate in the collector view first
      ssize_t leftmost_collector = _partitions.leftmost(Collector);
      for (ssize_t idx = _partitions.rightmost(Collector); idx >= leftmost_collector; ) {
        assert(_partitions.in_free_set(Collector, idx), "Boundaries or find_prev_set_bit failed: " SSIZE_FORMAT, idx);
        HeapWord* result = try_allocate_in(_heap->get_region(idx), req, in_new_region);
        if (result != nullptr) {
          return result;
        }
        idx = _partitions.find_index_of_previous_available_region(Collector, idx - 1);
      }

      // No dice. Can we borrow space from mutator view?
      if (!ShenandoahEvacReserveOverflow) {
        return nullptr;
      }

      // Try to steal an empty region from the mutator view.
      ssize_t leftmost_mutator_empty = _partitions.leftmost_empty(Mutator);
      for (ssize_t idx = _partitions.rightmost_empty(Mutator); idx >= leftmost_mutator_empty; ) {
        assert(_partitions.in_free_set(Mutator, idx), "Boundaries or find_prev_set_bit failed: " SSIZE_FORMAT, idx);
        ShenandoahHeapRegion* r = _heap->get_region(idx);
        if (can_allocate_from(r)) {
          flip_to_gc(r);
          HeapWord *result = try_allocate_in(r, req, in_new_region);
          if (result != nullptr) {
            log_debug(gc)("Flipped region " SIZE_FORMAT " to gc for request: " PTR_FORMAT, idx, p2i(&req));
            return result;
          }
        }
        idx = _partitions.find_index_of_previous_available_region(Mutator, idx - 1);
      }

      // No dice. Do not try to mix mutator and GC allocations, because adjusting region UWM
      // due to GC allocations would expose unparsable mutator allocations.
      break;
    }
    default:
      ShouldNotReachHere();
  }
  return nullptr;
}

HeapWord* ShenandoahFreeSet::try_allocate_in(ShenandoahHeapRegion* r, ShenandoahAllocRequest& req, bool& in_new_region) {
  assert (has_alloc_capacity(r), "Performance: should avoid full regions on this path: " SIZE_FORMAT, r->index());
  if (_heap->is_concurrent_weak_root_in_progress() && r->is_trash()) {
    return nullptr;
  }

  HeapWord* result = nullptr;
  try_recycle_trashed(r);
  in_new_region = r->is_empty();

  if (in_new_region) {
    log_debug(gc)("Using new region (" SIZE_FORMAT ") for %s (" PTR_FORMAT ").",
                       r->index(), ShenandoahAllocRequest::alloc_type_to_string(req.type()), p2i(&req));
  }

  // req.size() is in words, r->free() is in bytes.
  if (req.is_lab_alloc()) {
    // This is a GCLAB or a TLAB allocation
    size_t adjusted_size = req.size();
    size_t free = align_down(r->free() >> LogHeapWordSize, MinObjAlignment);
    if (adjusted_size > free) {
      adjusted_size = free;
    }
    if (adjusted_size >= req.min_size()) {
      result = r->allocate(adjusted_size, req.type());
      log_debug(gc)("Allocated " SIZE_FORMAT " words (adjusted from " SIZE_FORMAT ") for %s @" PTR_FORMAT
                          " from %s region " SIZE_FORMAT ", free bytes remaining: " SIZE_FORMAT,
                          adjusted_size, req.size(), ShenandoahAllocRequest::alloc_type_to_string(req.type()), p2i(result),
                          _partitions.partition_membership_name(r->index()), r->index(), r->free());
      assert (result != nullptr, "Allocation must succeed: free " SIZE_FORMAT ", actual " SIZE_FORMAT, free, adjusted_size);
      req.set_actual_size(adjusted_size);
    } else {
      log_trace(gc, free)("Failed to shrink TLAB or GCLAB request (" SIZE_FORMAT ") in region " SIZE_FORMAT " to " SIZE_FORMAT
                          " because min_size() is " SIZE_FORMAT, req.size(), r->index(), adjusted_size, req.min_size());
    }
  } else {
    size_t size = req.size();
    result = r->allocate(size, req.type());
    if (result != nullptr) {
      // Record actual allocation size
      log_debug(gc)("Allocated " SIZE_FORMAT " words for %s @" PTR_FORMAT
                          " from %s region " SIZE_FORMAT ", free bytes remaining: " SIZE_FORMAT,
                          size, ShenandoahAllocRequest::alloc_type_to_string(req.type()), p2i(result),
                          _partitions.partition_membership_name(r->index()),  r->index(), r->free());
      req.set_actual_size(size);
    }
  }

  if (result != nullptr) {
    // Allocation successful, bump stats:
    if (req.is_mutator_alloc()) {
      _partitions.increase_used(Mutator, req.actual_size() * HeapWordSize);
    } else {
      assert(req.is_gc_alloc(), "Should be gc_alloc since req wasn't mutator alloc");

      // For GC allocations, we advance update_watermark because the objects relocated into this memory during
      // evacuation are not updated during evacuation.
      r->set_update_watermark(r->top());
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
    _partitions.retire_from_partition(req.is_mutator_alloc()? Mutator: Collector, idx, r->used());
    _partitions.assert_bounds();
  }
  return result;
}

HeapWord* ShenandoahFreeSet::allocate_contiguous(ShenandoahAllocRequest& req) {
  assert(req.is_mutator_alloc(), "All humongous allocations are performed by mutator");
  shenandoah_assert_heaplocked();

  size_t words_size = req.size();
  ssize_t num = ShenandoahHeapRegion::required_regions(words_size * HeapWordSize);

  // Check if there are enough regions left to satisfy allocation.
  if (num > (ssize_t) _partitions.count(Mutator)) {
    return nullptr;
  }

  ssize_t start_range = _partitions.leftmost_empty(Mutator);
  ssize_t end_range = _partitions.rightmost_empty(Mutator) + 1;
  ssize_t last_possible_start = end_range - num;

  // Find the continuous interval of $num regions, starting from $beg and ending in $end,
  // inclusive. Contiguous allocations are biased to the beginning.
  ssize_t beg = _partitions.find_index_of_next_available_cluster_of_regions(Mutator, start_range, num);
  if (beg > last_possible_start) {
    // Hit the end, goodbye
    return nullptr;
  }
  ssize_t end = beg;

  while (true) {
    // We've confirmed num contiguous regions belonging to Mutator partition, so no need to confirm membership.
    // If region is not completely free, the current [beg; end] is useless, and we may fast-forward.  If we can extend
    // the existing range, we can exploit that certain regions are already known to be in the Mutator free set.
    while (!can_allocate_from(_heap->get_region(end))) {
      // region[end] is not empty, so we restart our search after region[end]
      ssize_t slide_delta = end + 1 - beg;
      if (beg + slide_delta > last_possible_start) {
        // no room to slide
        return nullptr;
      }
      for (ssize_t span_end = beg + num; slide_delta > 0; slide_delta--) {
        if (!_partitions.in_free_set(Mutator, span_end)) {
          beg = _partitions.find_index_of_next_available_cluster_of_regions(Mutator, span_end + 1, num);
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
  for (ssize_t i = beg; i <= end; i++) {
    ShenandoahHeapRegion* r = _heap->get_region(i);
    try_recycle_trashed(r);

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

    r->set_update_watermark(r->bottom());
    r->set_top(r->bottom() + used_words);
  }
  _partitions.retire_range_from_partition(Mutator, beg, end);

  size_t total_humongous_size = ShenandoahHeapRegion::region_size_bytes() * num;
  _partitions.increase_used(Mutator, total_humongous_size);
  _partitions.assert_bounds();
  req.set_actual_size(words_size);
  return _heap->get_region(beg)->bottom();
}

void ShenandoahFreeSet::try_recycle_trashed(ShenandoahHeapRegion *r) {
  if (r->is_trash()) {
    _heap->decrease_used(r->used());
    r->recycle();
  }
}

void ShenandoahFreeSet::recycle_trash() {
  // lock is not reentrable, check we don't have it
  shenandoah_assert_not_heaplocked();

  for (size_t i = 0; i < _heap->num_regions(); i++) {
    ShenandoahHeapRegion* r = _heap->get_region(i);
    if (r->is_trash()) {
      ShenandoahHeapLocker locker(_heap->lock());
      try_recycle_trashed(r);
    }
    SpinPause(); // allow allocators to take the lock
  }
}

void ShenandoahFreeSet::flip_to_gc(ShenandoahHeapRegion* r) {
  size_t idx = r->index();

  assert(_partitions.partition_id_matches(idx, Mutator), "Should be in mutator view");
  assert(can_allocate_from(r), "Should not be allocated");

  size_t ac = alloc_capacity(r);
  _partitions.move_from_partition_to_partition(idx, Mutator, Collector, ac);
  _partitions.assert_bounds();

  // We do not ensure that the region is no longer trash, relying on try_allocate_in(), which always comes next,
  // to recycle trash before attempting to allocate anything in the region.
}

void ShenandoahFreeSet::clear() {
  shenandoah_assert_heaplocked();
  clear_internal();
}

void ShenandoahFreeSet::clear_internal() {
  _partitions.make_all_regions_unavailable();
}

void ShenandoahFreeSet::find_regions_with_alloc_capacity(size_t &cset_regions) {

  cset_regions = 0;
  clear_internal();
  size_t region_size_bytes = _partitions.region_size_bytes();
  size_t max_regions = _partitions.max_regions();

  size_t mutator_leftmost = max_regions;
  size_t mutator_rightmost = 0;
  size_t mutator_leftmost_empty = max_regions;
  size_t mutator_rightmost_empty = 0;

  size_t mutator_regions = 0;
  size_t mutator_used = 0;

  for (size_t idx = 0; idx < _heap->num_regions(); idx++) {
    ShenandoahHeapRegion* region = _heap->get_region(idx);
    if (region->is_trash()) {
      // Trashed regions represent regions that had been in the collection partition but have not yet been "cleaned up".
      // The cset regions are not "trashed" until we have finished update refs.
      cset_regions++;
    }
    if (region->is_alloc_allowed() || region->is_trash()) {

      // Do not add regions that would almost surely fail allocation
      size_t ac = alloc_capacity(region);
      if (ac > PLAB::min_size() * HeapWordSize) {
        _partitions.raw_set_membership(idx, Mutator);

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

        log_debug(gc)(
          "  Adding Region " SIZE_FORMAT " (Free: " SIZE_FORMAT "%s, Used: " SIZE_FORMAT "%s) to mutator partition",
          idx, byte_size_in_proper_unit(region->free()), proper_unit_for_byte_size(region->free()),
          byte_size_in_proper_unit(region->used()), proper_unit_for_byte_size(region->used()));
      }
    }
  }
  _partitions.establish_intervals(mutator_leftmost, mutator_rightmost, mutator_leftmost_empty, mutator_rightmost_empty,
                                  mutator_regions, mutator_used);
}

void ShenandoahFreeSet::move_regions_from_collector_to_mutator(size_t max_xfer_regions) {
  size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();
  size_t collector_empty_xfer = 0;
  size_t collector_not_empty_xfer = 0;

  // Process empty regions within the Collector free partition
  if ((max_xfer_regions > 0) && (_partitions.leftmost_empty(Collector) <= _partitions.rightmost_empty(Collector))) {
    ShenandoahHeapLocker locker(_heap->lock());
    ssize_t rightmost = _partitions.rightmost_empty(Collector);
    for (ssize_t idx = _partitions.leftmost_empty(Collector); (max_xfer_regions > 0) && (idx <= rightmost); ) {
      assert(_partitions.in_free_set(Collector, idx), "Boundaries or find_next_set_bit failed: " SSIZE_FORMAT, idx);
      // Note: can_allocate_from() denotes that region is entirely empty
      if (can_allocate_from(idx)) {
        _partitions.move_from_partition_to_partition(idx, Collector, Mutator, region_size_bytes);
        max_xfer_regions--;
        collector_empty_xfer += region_size_bytes;
      }
      idx = _partitions.find_index_of_next_available_region(Collector, idx + 1);
    }
  }

  // If there are any non-empty regions within Collector partition, we can also move them to the Mutator free partition
  if ((max_xfer_regions > 0) && (_partitions.leftmost(Collector) <= _partitions.rightmost(Collector))) {
    ShenandoahHeapLocker locker(_heap->lock());
    ssize_t rightmost = _partitions.rightmost(Collector);
    for (ssize_t idx = _partitions.leftmost(Collector); (max_xfer_regions > 0) && (idx <= rightmost); ) {
      assert(_partitions.in_free_set(Collector, idx), "Boundaries or find_next_set_bit failed: " SSIZE_FORMAT, idx);
      size_t ac = alloc_capacity(idx);
      if (ac > 0) {
        _partitions.move_from_partition_to_partition(idx, Collector, Mutator, ac);
        max_xfer_regions--;
        collector_not_empty_xfer += ac;
      }
      idx = _partitions.find_index_of_next_available_region(Collector, idx + 1);
    }
  }

  size_t collector_xfer = collector_empty_xfer + collector_not_empty_xfer;
  log_info(gc)("At start of update refs, moving " SIZE_FORMAT "%s to Mutator free partition from Collector Reserve",
               byte_size_in_proper_unit(collector_xfer), proper_unit_for_byte_size(collector_xfer));
}

void ShenandoahFreeSet::prepare_to_rebuild(size_t &cset_regions) {
  shenandoah_assert_heaplocked();

  log_debug(gc)("Rebuilding FreeSet");

  // This places regions that have alloc_capacity into the mutator partition.
  find_regions_with_alloc_capacity(cset_regions);
}

void ShenandoahFreeSet::finish_rebuild(size_t cset_regions) {
  shenandoah_assert_heaplocked();

  // Our desire is to reserve this much memory for future evacuation.  We may end up reserving less, if
  // memory is in short supply.

  size_t reserve = _heap->max_capacity() * ShenandoahEvacReserve / 100;
  size_t available_in_collector_partition = _partitions.capacity_of(Collector) - _partitions.used_by(Collector);
  size_t additional_reserve;
  if (available_in_collector_partition < reserve) {
    additional_reserve = reserve - available_in_collector_partition;
  } else {
    additional_reserve = 0;
  }

  reserve_regions(reserve);
  _partitions.assert_bounds();
  log_status();
}

void ShenandoahFreeSet::rebuild() {
  size_t cset_regions;
  prepare_to_rebuild(cset_regions);
  finish_rebuild(cset_regions);
}

void ShenandoahFreeSet::reserve_regions(size_t to_reserve) {
  for (size_t i = _heap->num_regions(); i > 0; i--) {
    size_t idx = i - 1;
    ShenandoahHeapRegion* r = _heap->get_region(idx);

    if (!_partitions.in_free_set(Mutator, idx)) {
      continue;
    }

    size_t ac = alloc_capacity(r);
    assert (ac > 0, "Membership in free partition implies has capacity");

    bool move_to_collector = _partitions.available_in(Collector) < to_reserve;
    if (!move_to_collector) {
      // We've satisfied to_reserve
      break;
    }

    if (move_to_collector) {
      // Note: In a previous implementation, regions were only placed into the survivor space (collector_is_free) if
      // they were entirely empty.  I'm not sure I understand the rationale for that.  That alternative behavior would
      // tend to mix survivor objects with ephemeral objects, making it more difficult to reclaim the memory for the
      // ephemeral objects.
      _partitions.move_from_partition_to_partition(idx, Mutator, Collector, ac);
      log_debug(gc)("  Shifting region " SIZE_FORMAT " from mutator_free to collector_free", idx);
    }
  }

  if (LogTarget(Info, gc, free)::is_enabled()) {
    size_t reserve = _partitions.capacity_of(Collector);
    if (reserve < to_reserve) {
      log_debug(gc)("Wanted " PROPERFMT " for young reserve, but only reserved: " PROPERFMT,
                    PROPERFMTARGS(to_reserve), PROPERFMTARGS(reserve));
    }
  }
}

void ShenandoahFreeSet::log_status() {
  shenandoah_assert_heaplocked();

#ifdef ASSERT
  // Dump of the FreeSet details is only enabled if assertions are enabled
  if (LogTarget(Debug, gc, free)::is_enabled()) {
#define BUFFER_SIZE 80
    size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();
    size_t consumed_collector = 0;
    size_t available_collector = 0;
    size_t consumed_mutator = 0;
    size_t available_mutator = 0;

    char buffer[BUFFER_SIZE];
    for (uint i = 0; i < BUFFER_SIZE; i++) {
      buffer[i] = '\0';
    }
    log_debug(gc)("FreeSet map legend:"
                       " M:mutator_free C:collector_free H:humongous _:retired");
    log_debug(gc)(" mutator free range [" SIZE_FORMAT ".." SIZE_FORMAT "], "
                        " collector free range [" SIZE_FORMAT ".." SIZE_FORMAT "]",
                        _partitions.leftmost(Mutator), _partitions.rightmost(Mutator),
                        _partitions.leftmost(Collector), _partitions.rightmost(Collector));

    for (uint i = 0; i < _heap->num_regions(); i++) {
      ShenandoahHeapRegion *r = _heap->get_region(i);
      uint idx = i % 64;
      if ((i != 0) && (idx == 0)) {
        log_debug(gc)(" %6u: %s", i-64, buffer);
      }
      if (_partitions.in_free_set(Mutator, i)) {
        size_t capacity = alloc_capacity(r);
        available_mutator += capacity;
        consumed_mutator += region_size_bytes - capacity;
        buffer[idx] = (capacity == region_size_bytes)? 'M': 'm';
      } else if (_partitions.in_free_set(Collector, i)) {
        size_t capacity = alloc_capacity(r);
        available_collector += capacity;
        consumed_collector += region_size_bytes - capacity;
        buffer[idx] = (capacity == region_size_bytes)? 'C': 'c';
      } else if (r->is_humongous()) {
        buffer[idx] = 'h';
      } else {
        buffer[idx] = '_';
      }
    }
    uint remnant = _heap->num_regions() % 64;
    if (remnant > 0) {
      buffer[remnant] = '\0';
    } else {
      remnant = 64;
    }
    log_debug(gc)(" %6u: %s", (uint) (_heap->num_regions() - remnant), buffer);
  }
#endif

  LogTarget(Info, gc, free) lt;
  if (lt.is_enabled()) {
    ResourceMark rm;
    LogStream ls(lt);

    {
      ssize_t last_idx = 0;
      size_t max = 0;
      size_t max_contig = 0;
      size_t empty_contig = 0;

      size_t total_used = 0;
      size_t total_free = 0;
      size_t total_free_ext = 0;

      for (ssize_t idx = _partitions.leftmost(Mutator); idx <= _partitions.rightmost(Mutator); idx++) {
        if (_partitions.in_free_set(Mutator, idx)) {
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

      ls.print("Free: " SIZE_FORMAT "%s, Max: " SIZE_FORMAT "%s regular, " SIZE_FORMAT "%s humongous, ",
               byte_size_in_proper_unit(free),          proper_unit_for_byte_size(free),
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
      ls.print(SIZE_FORMAT "%% external, ", frag_ext);

      size_t frag_int;
      if (_partitions.count(Mutator) > 0) {
        frag_int = (100 * (total_used / _partitions.count(Mutator)) / ShenandoahHeapRegion::region_size_bytes());
      } else {
        frag_int = 0;
      }
      ls.print(SIZE_FORMAT "%% internal; ", frag_int);
      ls.print("Used: " SIZE_FORMAT "%s, Mutator Free: " SIZE_FORMAT,
               byte_size_in_proper_unit(total_used), proper_unit_for_byte_size(total_used), _partitions.count(Mutator));
    }

    {
      size_t max = 0;
      size_t total_free = 0;
      size_t total_used = 0;

      for (ssize_t idx = _partitions.leftmost(Collector); idx <= _partitions.rightmost(Collector); idx++) {
        if (_partitions.in_free_set(Collector, idx)) {
          ShenandoahHeapRegion *r = _heap->get_region(idx);
          size_t free = alloc_capacity(r);
          max = MAX2(max, free);
          total_free += free;
          total_used += r->used();
        }
      }
      ls.print(" Collector Reserve: " SIZE_FORMAT "%s, Max: " SIZE_FORMAT "%s; Used: " SIZE_FORMAT "%s",
               byte_size_in_proper_unit(total_free), proper_unit_for_byte_size(total_free),
               byte_size_in_proper_unit(max),        proper_unit_for_byte_size(max),
               byte_size_in_proper_unit(total_used), proper_unit_for_byte_size(total_used));
    }
  }
}

HeapWord* ShenandoahFreeSet::allocate(ShenandoahAllocRequest& req, bool& in_new_region) {
  shenandoah_assert_heaplocked();

  // Allocation request is known to satisfy all memory budgeting constraints.
  if (req.size() > ShenandoahHeapRegion::humongous_threshold_words()) {
    switch (req.type()) {
      case ShenandoahAllocRequest::_alloc_shared:
      case ShenandoahAllocRequest::_alloc_shared_gc:
        in_new_region = true;
        return allocate_contiguous(req);
      case ShenandoahAllocRequest::_alloc_gclab:
      case ShenandoahAllocRequest::_alloc_tlab:
        in_new_region = false;
        assert(false, "Trying to allocate TLAB larger than the humongous threshold: " SIZE_FORMAT " > " SIZE_FORMAT,
               req.size(), ShenandoahHeapRegion::humongous_threshold_words());
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
  out->print_cr("Mutator Free Set: " SIZE_FORMAT "", _partitions.count(Mutator));
  ssize_t rightmost = _partitions.rightmost(Mutator);
  for (ssize_t index = _partitions.leftmost(Mutator); index <= rightmost; ) {
    assert(_partitions.in_free_set(Mutator, index), "Boundaries or find_next_set_bit failed: " SSIZE_FORMAT, index);
    _heap->get_region(index)->print_on(out);
    index = _partitions.find_index_of_next_available_region(Mutator, index + 1);
  }
  out->print_cr("Collector Free Set: " SIZE_FORMAT "", _partitions.count(Collector));
  rightmost = _partitions.rightmost(Collector);
  for (ssize_t index = _partitions.leftmost(Collector); index <= rightmost; ) {
    assert(_partitions.in_free_set(Collector, index), "Boundaries or find_next_set_bit failed: " SSIZE_FORMAT, index);
    _heap->get_region(index)->print_on(out);
    index = _partitions.find_index_of_next_available_region(Collector, index + 1);
  }
}

double ShenandoahFreeSet::internal_fragmentation() {
  double squared = 0;
  double linear = 0;
  int count = 0;

  ssize_t rightmost = _partitions.rightmost(Mutator);
  for (ssize_t index = _partitions.leftmost(Mutator); index <= rightmost; ) {
    assert(_partitions.in_free_set(Mutator, index), "Boundaries or find_next_set_bit failed: " SSIZE_FORMAT, index);
    ShenandoahHeapRegion* r = _heap->get_region(index);
    size_t used = r->used();
    squared += used * used;
    linear += used;
    count++;
    index = _partitions.find_index_of_next_available_region(Mutator, index + 1);
  }

  if (count > 0) {
    double s = squared / (ShenandoahHeapRegion::region_size_bytes() * linear);
    return 1 - s;
  } else {
    return 0;
  }
}

double ShenandoahFreeSet::external_fragmentation() {
  ssize_t last_idx = 0;
  size_t max_contig = 0;
  size_t empty_contig = 0;

  size_t free = 0;

  ssize_t rightmost = _partitions.rightmost(Mutator);
  for (ssize_t index = _partitions.leftmost(Mutator); index <= rightmost; ) {
    assert(_partitions.in_free_set(Mutator, index), "Boundaries or find_next_set_bit failed: " SSIZE_FORMAT, index);
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
    index = _partitions.find_index_of_next_available_region(Mutator, index + 1);
  }

  if (free > 0) {
    return 1 - (1.0 * max_contig * ShenandoahHeapRegion::region_size_bytes() / free);
  } else {
    return 0;
  }
}

