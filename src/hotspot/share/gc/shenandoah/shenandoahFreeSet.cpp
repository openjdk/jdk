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

ShenandoahRegionPartitions::ShenandoahRegionPartitions(size_t max_regions, ShenandoahFreeSet* free_set) :
    _max(max_regions),
    _region_size_bytes(ShenandoahHeapRegion::region_size_bytes()),
    _free_set(free_set),
    _membership(NEW_C_HEAP_ARRAY(ShenandoahFreeSetPartitionId, max_regions, mtGC))
{
  make_all_regions_unavailable();
}

ShenandoahRegionPartitions::~ShenandoahRegionPartitions() {
  FREE_C_HEAP_ARRAY(ShenandoahFreeSetPartitionId, _membership);
}

// Returns true iff this region is entirely available, either because it is empty() or because it has been found to represent
// immediate trash and we'll be able to immediately recycle it.  Note that we cannot recycle immediate trash if
// concurrent weak root processing is in progress.
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

void ShenandoahRegionPartitions::make_all_regions_unavailable() {
  for (size_t idx = 0; idx < _max; idx++) {
    _membership[idx] = NotFree;
  }

  for (size_t partition_id = 0; partition_id < NumPartitions; partition_id++) {
    _leftmosts[partition_id] = _max;
    _rightmosts[partition_id] = 0;
    _leftmosts_empty[partition_id] = _max;
    _rightmosts_empty[partition_id] = 0;
    _capacity[partition_id] = 0;
    _used[partition_id] = 0;
  }

  _region_counts[Mutator] = _region_counts[Collector] = 0;
}

void ShenandoahRegionPartitions::establish_intervals(size_t mutator_leftmost, size_t mutator_rightmost,
                                                     size_t mutator_leftmost_empty, size_t mutator_rightmost_empty,
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
  _rightmosts[Collector] = 0;
  _leftmosts_empty[Collector] = _max;
  _rightmosts_empty[Collector] = 0;

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

inline void ShenandoahRegionPartitions::shrink_interval_if_boundary_modified(ShenandoahFreeSetPartitionId partition, size_t idx) {
  if (idx == _leftmosts[partition]) {
    while ((_leftmosts[partition] < _max) && !partition_id_matches(_leftmosts[partition], partition)) {
      _leftmosts[partition]++;
    }
    if (_leftmosts_empty[partition] < _leftmosts[partition]) {
      // This gets us closer to where we need to be; we'll scan further when leftmosts_empty is requested.
      _leftmosts_empty[partition] = _leftmosts[partition];
    }
  }
  if (idx == _rightmosts[partition]) {
    while (_rightmosts[partition] > 0 && !partition_id_matches(_rightmosts[partition], partition)) {
      _rightmosts[partition]--;
    }
    if (_rightmosts_empty[partition] > _rightmosts[partition]) {
      // This gets us closer to where we need to be; we'll scan further when rightmosts_empty is requested.
      _rightmosts_empty[partition] = _rightmosts[partition];
    }
  }
}

inline void ShenandoahRegionPartitions::expand_interval_if_boundary_modified(ShenandoahFreeSetPartitionId partition,
                                                           size_t idx, size_t region_available) {
  if (region_available == _region_size_bytes) {
    if (_leftmosts_empty[partition] > idx) {
      _leftmosts_empty[partition] = idx;
    }
    if (_rightmosts_empty[partition] < idx) {
      _rightmosts_empty[partition] = idx;
    }
  }
  if (_leftmosts[partition] > idx) {
    _leftmosts[partition] = idx;
  }
  if (_rightmosts[partition] < idx) {
    _rightmosts[partition] = idx;
  }
}

// Remove this region from its free partition, but leave its capacity and used as part of the original free partition's totals.
// When retiring a region, add any remnant of available memory within the region to the used total for the original free partition.
void ShenandoahRegionPartitions::retire_from_partition(size_t idx, size_t used_bytes) {
  ShenandoahFreeSetPartitionId orig_partition = membership(idx);

  // Note: we may remove from free partition even if region is not entirely full, such as when available < PLAB::min_size()
  assert (idx < _max, "index is sane: " SIZE_FORMAT " < " SIZE_FORMAT, idx, _max);
  assert (orig_partition < NumPartitions, "Cannot remove from free partitions if not already free");

  if (used_bytes < _region_size_bytes) {
    // Count the alignment pad remnant of memory as used when we retire this region
    increase_used(orig_partition, _region_size_bytes - used_bytes);
  }

  _membership[idx] = NotFree;
  shrink_interval_if_boundary_modified(orig_partition, idx);

  _region_counts[orig_partition]--;
}

void ShenandoahRegionPartitions::make_free(size_t idx, ShenandoahFreeSetPartitionId which_partition, size_t available) {
  assert (idx < _max, "index is sane: " SIZE_FORMAT " < " SIZE_FORMAT, idx, _max);
  assert (_membership[idx] == NotFree, "Cannot make free if already free");
  assert (which_partition < NumPartitions, "selected free partition must be valid");
  assert (available <= _region_size_bytes, "Available cannot exceed region size");

  _membership[idx] = which_partition;
  _capacity[which_partition] += _region_size_bytes;
  _used[which_partition] += _region_size_bytes - available;
  expand_interval_if_boundary_modified(which_partition, idx, available);

  _region_counts[which_partition]++;
}

void ShenandoahRegionPartitions::move_to_partition(size_t idx, ShenandoahFreeSetPartitionId new_partition, size_t available) {
  assert (idx < _max, "index is sane: " SIZE_FORMAT " < " SIZE_FORMAT, idx, _max);
  assert (new_partition < NumPartitions, "New partition must be valid");
  assert (available <= _region_size_bytes, "Available cannot exceed region size");

  ShenandoahFreeSetPartitionId orig_partition = _membership[idx];
  assert (orig_partition < NumPartitions, "Cannot move free unless already free");

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
  _membership[idx] = new_partition;
  _capacity[orig_partition] -= _region_size_bytes;
  _used[orig_partition] -= used;
  shrink_interval_if_boundary_modified(orig_partition, idx);

  _capacity[new_partition] += _region_size_bytes;;
  _used[new_partition] += used;
  expand_interval_if_boundary_modified(new_partition, idx, available);

  _region_counts[orig_partition]--;
  _region_counts[new_partition]++;
}

inline ShenandoahFreeSetPartitionId ShenandoahRegionPartitions::membership(size_t idx) const {
  assert (idx < _max, "index is sane: " SIZE_FORMAT " < " SIZE_FORMAT, idx, _max);
  return _membership[idx];
}

  // Returns true iff region idx is in the test_partition free_partition.
inline bool ShenandoahRegionPartitions::partition_id_matches(size_t idx, ShenandoahFreeSetPartitionId test_partition) const {
  assert (idx < _max, "index is sane: " SIZE_FORMAT " < " SIZE_FORMAT, idx, _max);
  if (_membership[idx] == test_partition) {
    assert ((test_partition == NotFree) || (_free_set->alloc_capacity(idx) > 0),
            "Free region " SIZE_FORMAT ", belonging to %s free partition, must have alloc capacity",
            idx, partition_name(test_partition));
    return true;
  } else {
    return false;
  }
}

inline size_t ShenandoahRegionPartitions::leftmost(ShenandoahFreeSetPartitionId which_partition) const {
  assert (which_partition < NumPartitions, "selected free partition must be valid");
  size_t idx = _leftmosts[which_partition];
  if (idx >= _max) {
    return _max;
  } else {
    assert (partition_id_matches(idx, which_partition), "left-most region must be free");
    return idx;
  }
}

inline size_t ShenandoahRegionPartitions::rightmost(ShenandoahFreeSetPartitionId which_partition) const {
  assert (which_partition < NumPartitions, "selected free partition must be valid");
  size_t idx = _rightmosts[which_partition];
  assert ((_leftmosts[which_partition] == _max) || partition_id_matches(idx, which_partition), "right-most region must be free");
  return idx;
}

inline bool ShenandoahRegionPartitions::is_empty(ShenandoahFreeSetPartitionId which_partition) const {
  assert (which_partition < NumPartitions, "selected free partition must be valid");
  return (leftmost(which_partition) > rightmost(which_partition));
}

size_t ShenandoahRegionPartitions::leftmost_empty(ShenandoahFreeSetPartitionId which_partition) {
  assert (which_partition < NumPartitions, "selected free partition must be valid");
  for (size_t idx = _leftmosts_empty[which_partition]; idx < _max; idx++) {
    if ((membership(idx) == which_partition) && (_free_set->alloc_capacity(idx) == _region_size_bytes)) {
      _leftmosts_empty[which_partition] = idx;
      return idx;
    }
  }
  _leftmosts_empty[which_partition] = _max;
  _rightmosts_empty[which_partition] = 0;
  return _max;
}

inline size_t ShenandoahRegionPartitions::rightmost_empty(ShenandoahFreeSetPartitionId which_partition) {
  assert (which_partition < NumPartitions, "selected free partition must be valid");
  for (intptr_t idx = _rightmosts_empty[which_partition]; idx >= 0; idx--) {
    if ((membership(idx) == which_partition) && (_free_set->alloc_capacity(idx) == _region_size_bytes)) {
      _rightmosts_empty[which_partition] = idx;
      return idx;
    }
  }
  _leftmosts_empty[which_partition] = _max;
  _rightmosts_empty[which_partition] = 0;
  return 0;
}

#ifdef ASSERT
void ShenandoahRegionPartitions::assert_bounds() {

  size_t leftmosts[NumPartitions];
  size_t rightmosts[NumPartitions];
  size_t empty_leftmosts[NumPartitions];
  size_t empty_rightmosts[NumPartitions];

  for (int i = 0; i < NumPartitions; i++) {
    leftmosts[i] = _max;
    empty_leftmosts[i] = _max;
    rightmosts[i] = 0;
    empty_rightmosts[i] = 0;
  }

  for (size_t i = 0; i < _max; i++) {
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
  assert (leftmost(Mutator) <= _max, "leftmost in bounds: "  SIZE_FORMAT " < " SIZE_FORMAT, leftmost(Mutator),  _max);
  assert (rightmost(Mutator) < _max, "rightmost in bounds: "  SIZE_FORMAT " < " SIZE_FORMAT, rightmost(Mutator),  _max);

  assert (leftmost(Mutator) == _max || partition_id_matches(leftmost(Mutator), Mutator),
          "leftmost region should be free: " SIZE_FORMAT,  leftmost(Mutator));
  assert (leftmost(Mutator) == _max || partition_id_matches(rightmost(Mutator), Mutator),
          "rightmost region should be free: " SIZE_FORMAT, rightmost(Mutator));

  // If Mutator partition is empty, leftmosts will both equal max, rightmosts will both equal zero.
  // Likewise for empty region partitions.
  size_t beg_off = leftmosts[Mutator];
  size_t end_off = rightmosts[Mutator];
  assert (beg_off >= leftmost(Mutator),
          "free regions before the leftmost: " SIZE_FORMAT ", bound " SIZE_FORMAT, beg_off, leftmost(Mutator));
  assert (end_off <= rightmost(Mutator),
          "free regions past the rightmost: " SIZE_FORMAT ", bound " SIZE_FORMAT,  end_off, rightmost(Mutator));

  beg_off = empty_leftmosts[Mutator];
  end_off = empty_rightmosts[Mutator];
  assert (beg_off >= leftmost_empty(Mutator),
          "free empty regions before the leftmost: " SIZE_FORMAT ", bound " SIZE_FORMAT, beg_off, leftmost_empty(Mutator));
  assert (end_off <= rightmost_empty(Mutator),
          "free empty regions past the rightmost: " SIZE_FORMAT ", bound " SIZE_FORMAT,  end_off, rightmost_empty(Mutator));

  // Performance invariants. Failing these would not break the free partition, but performance would suffer.
  assert (leftmost(Collector) <= _max, "leftmost in bounds: "  SIZE_FORMAT " < " SIZE_FORMAT, leftmost(Collector),  _max);
  assert (rightmost(Collector) < _max, "rightmost in bounds: "  SIZE_FORMAT " < " SIZE_FORMAT, rightmost(Collector),  _max);

  assert (leftmost(Collector) == _max || partition_id_matches(leftmost(Collector), Collector),
          "leftmost region should be free: " SIZE_FORMAT,  leftmost(Collector));
  assert (leftmost(Collector) == _max || partition_id_matches(rightmost(Collector), Collector),
          "rightmost region should be free: " SIZE_FORMAT, rightmost(Collector));

  // If Collector partition is empty, leftmosts will both equal max, rightmosts will both equal zero.
  // Likewise for empty region partitions.
  beg_off = leftmosts[Collector];
  end_off = rightmosts[Collector];
  assert (beg_off >= leftmost(Collector),
          "free regions before the leftmost: " SIZE_FORMAT ", bound " SIZE_FORMAT, beg_off, leftmost(Collector));
  assert (end_off <= rightmost(Collector),
          "free regions past the rightmost: " SIZE_FORMAT ", bound " SIZE_FORMAT,  end_off, rightmost(Collector));

  beg_off = empty_leftmosts[Collector];
  end_off = empty_rightmosts[Collector];
  assert (beg_off >= leftmost_empty(Collector),
          "free empty regions before the leftmost: " SIZE_FORMAT ", bound " SIZE_FORMAT, beg_off, leftmost_empty(Collector));
  assert (end_off <= rightmost_empty(Collector),
          "free empty regions past the rightmost: " SIZE_FORMAT ", bound " SIZE_FORMAT,  end_off, rightmost_empty(Collector));
}
#endif

ShenandoahFreeSet::ShenandoahFreeSet(ShenandoahHeap* heap, size_t max_regions) :
  _heap(heap),
  _partitions(max_regions, this)
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
      // Allocate within mutator free from high memory to low so as to preserve low memory for humongous allocations
      if (!_partitions.is_empty(Mutator)) {
        // Use signed idx.  Otherwise, loop will never terminate.
        ssize_t leftmost = _partitions.leftmost(Mutator);
        for (ssize_t idx = _partitions.rightmost(Mutator); idx >= leftmost; idx--) {
          ShenandoahHeapRegion* r = _heap->get_region(idx);
          if (_partitions.partition_id_matches(idx, Mutator)) {
            // try_allocate_in() increases used if the allocation is successful.
            HeapWord* result;
            size_t min_size = (req.type() == ShenandoahAllocRequest::_alloc_tlab)? req.min_size(): req.size();
            if ((alloc_capacity(r) >= min_size) && ((result = try_allocate_in(r, req, in_new_region)) != nullptr)) {
              return result;
            }
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
      for (ssize_t idx = _partitions.rightmost(Collector); idx >= leftmost_collector; idx--) {
        if (_partitions.partition_id_matches(idx, Collector)) {
          HeapWord* result = try_allocate_in(_heap->get_region(idx), req, in_new_region);
          if (result != nullptr) {
            return result;
          }
        }
      }

      // No dice. Can we borrow space from mutator view?
      if (!ShenandoahEvacReserveOverflow) {
        return nullptr;
      }

      // Try to steal an empty region from the mutator view.
      ssize_t leftmost_mutator_empty = _partitions.leftmost_empty(Mutator);
      for (ssize_t idx = _partitions.rightmost_empty(Mutator); idx >= leftmost_mutator_empty; idx--) {
        if (_partitions.partition_id_matches(idx, Mutator)) {
          ShenandoahHeapRegion* r = _heap->get_region(idx);
          if (can_allocate_from(r)) {
            flip_to_gc(r);
            HeapWord *result = try_allocate_in(r, req, in_new_region);
            if (result != nullptr) {
              log_debug(gc, free)("Flipped region " SIZE_FORMAT " to gc for request: " PTR_FORMAT, idx, p2i(&req));
              return result;
            }
          }
        }
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
    log_debug(gc, free)("Using new region (" SIZE_FORMAT ") for %s (" PTR_FORMAT ").",
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
      log_debug(gc, free)("Allocated " SIZE_FORMAT " words (adjusted from " SIZE_FORMAT ") for %s @" PTR_FORMAT
                          " from %s region " SIZE_FORMAT ", free bytes remaining: " SIZE_FORMAT,
                          adjusted_size, req.size(), ShenandoahAllocRequest::alloc_type_to_string(req.type()), p2i(result),
                          partition_name(_partitions.membership(r->index())),  r->index(), r->free());
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
      log_debug(gc, free)("Allocated " SIZE_FORMAT " words for %s @" PTR_FORMAT
                          " from %s region " SIZE_FORMAT ", free bytes remaining: " SIZE_FORMAT,
                          size, ShenandoahAllocRequest::alloc_type_to_string(req.type()), p2i(result),
                          partition_name(_partitions.membership(r->index())),  r->index(), r->free());
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

  if ((!ShenandoahPackEvacTightly && result == nullptr) || (alloc_capacity(r) < PLAB::min_size() * HeapWordSize)) {
    // Regardless of whether this allocation succeeded, if the remaining memory is less than PLAB:min_size(), retire this region.
    // Note that retire_from_partition() increases used to account for waste.

    // Note that a previous implementation of this function would retire a region following any failure to
    // allocate within.  This was observed to result in large amounts of available memory being ignored
    // following a failed shared allocation request.  In the current implementation, we only retire a region
    // if the remaining capacity is less than PLAB::min_size() or if !ShenandoahPackEvacTightly.  Note that TLAB
    // requests will generally downsize to absorb all memory available within the region even if the remaining
    // memory is less than the desired size.
    size_t idx = r->index();
    _partitions.retire_from_partition(idx, r->used());
    _partitions.assert_bounds();
  }
  return result;
}

HeapWord* ShenandoahFreeSet::allocate_contiguous(ShenandoahAllocRequest& req) {
  shenandoah_assert_heaplocked();

  size_t words_size = req.size();
  size_t num = ShenandoahHeapRegion::required_regions(words_size * HeapWordSize);

  // Check if there are enough regions left to satisfy allocation.
  if (num > _partitions.count(Mutator)) {
    return nullptr;
  }

  // Find the continuous interval of $num regions, starting from $beg and ending in $end,
  // inclusive. Contiguous allocations are biased to the beginning.

  size_t beg = _partitions.leftmost_empty(Mutator);
  size_t end = beg;

  while (true) {
    if (end > _partitions.rightmost_empty(Mutator)) {
      // Hit the end, goodbye
      return nullptr;
    }

    // If regions are not adjacent, then current [beg; end] is useless, and we may fast-forward.
    // If region is not completely free, the current [beg; end] is useless, and we may fast-forward.
    if (!_partitions.partition_id_matches(end, Mutator) || !can_allocate_from(_heap->get_region(end))) {
      end++;
      beg = end;
      continue;
    }

    if ((end - beg + 1) == num) {
      // found the match
      break;
    }

    end++;
  }

  size_t remainder = words_size & ShenandoahHeapRegion::region_size_words_mask();

  // Initialize regions:
  for (size_t i = beg; i <= end; i++) {
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

    // While individual regions report their true use, all humongous regions are marked used in the free partition.
    _partitions.retire_from_partition(r->index(), ShenandoahHeapRegion::region_size_bytes());
  }

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
  _partitions.move_to_partition(idx, Collector, ac);
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

// This function places all regions that have allocation capacity into the mutator_partition, identifying regions
// that have no allocation capacity as NotFree.  Subsequently, we will move some of the mutator regions into the
// collector partition with the intent of packing collector memory into the highest (rightmost) addresses of the
// heap, with mutator memory consuming the lowest addresses of the heap.
void ShenandoahFreeSet::find_regions_with_alloc_capacity(size_t &cset_regions) {
  cset_regions = 0;

  size_t mutator_regions = 0;
  size_t mutator_used = 0;

  size_t max_regions = _partitions.max_regions();
  size_t region_size_bytes = _partitions.region_size_bytes();

  size_t mutator_leftmost = max_regions;
  size_t mutator_rightmost = 0;
  size_t mutator_leftmost_empty = max_regions;
  size_t mutator_rightmost_empty = 0;

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

        log_debug(gc, free)(
          "  Adding Region " SIZE_FORMAT " (Free: " SIZE_FORMAT "%s, Used: " SIZE_FORMAT "%s) to mutator partition",
          idx, byte_size_in_proper_unit(region->free()), proper_unit_for_byte_size(region->free()),
          byte_size_in_proper_unit(region->used()), proper_unit_for_byte_size(region->used()));
      } else {
        // Region has some capacity, but it's too small to be useful.
        _partitions.raw_set_membership(idx, NotFree);
      }
    } else {
      // Region has no capacity.
      _partitions.raw_set_membership(idx, NotFree);
    }
  }

  _partitions.establish_intervals(mutator_leftmost, mutator_rightmost, mutator_leftmost_empty, mutator_rightmost_empty,
                                  mutator_regions, mutator_used);
}

// Move no more than max_xfer_regions from the existing Collector partition to the Mutator partition.
//
// This is called from outside the heap lock at the start of update refs.  At this point, we no longer
// need to reserve memory for evacuation.  (We will create a new reserve after update refs finishes,
// setting aside some of the memory that was reclaimed by the most recent GC.  This new reserve will satisfy
// the evacuation needs of the next GC pass.)
void ShenandoahFreeSet::move_regions_from_collector_to_mutator(size_t max_xfer_regions) {
  size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();
  size_t collector_empty_xfer = 0;
  size_t collector_not_empty_xfer = 0;

  // Process empty regions within the Collector free partition
  if ((max_xfer_regions > 0) && (_partitions.leftmost_empty(Collector) <= _partitions.rightmost_empty(Collector))) {
    ShenandoahHeapLocker locker(_heap->lock());
    for (size_t idx = _partitions.leftmost_empty(Collector);
         (max_xfer_regions > 0) && (idx <= _partitions.rightmost_empty(Collector)); idx++) {
      // Note: can_allocate_from() denotes that region is entirely empty
      if (_partitions.partition_id_matches(idx, Collector) && can_allocate_from(idx)) {
        _partitions.move_to_partition(idx, Mutator, region_size_bytes);
        max_xfer_regions--;
        collector_empty_xfer += region_size_bytes;
      }
    }
  }

  // If there are any non-empty regions within Collector partition, we can also move them to the Mutator free partition
  if ((max_xfer_regions > 0) && (_partitions.leftmost(Collector) <= _partitions.rightmost(Collector))) {
    ShenandoahHeapLocker locker(_heap->lock());
    for (size_t idx = _partitions.leftmost(Collector);
         (max_xfer_regions > 0) && (idx <= _partitions.rightmost(Collector)); idx++) {
      size_t ac = alloc_capacity(idx);
      if (_partitions.partition_id_matches(idx, Collector) && (ac > 0)) {
        _partitions.move_to_partition(idx, Mutator, ac);
        max_xfer_regions--;
        collector_not_empty_xfer += ac;
      }
    }
  }

  size_t collector_xfer = collector_empty_xfer + collector_not_empty_xfer;
  log_info(gc, free)("At start of update refs, moving " SIZE_FORMAT "%s to Mutator free partition from Collector Reserve",
                     byte_size_in_proper_unit(collector_xfer), proper_unit_for_byte_size(collector_xfer));
}


// Overwrite arguments to represent the number of regions to be reclaimed from the cset
void ShenandoahFreeSet::prepare_to_rebuild(size_t &cset_regions) {
  shenandoah_assert_heaplocked();

  log_debug(gc, free)("Rebuilding FreeSet");

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

// Having placed all regions that have allocation capacity into the mutator partition, move some of these regions from
// the mutator partition into the collector partition in order to assure that the memory available for allocations within
// the collector partition is at least to_reserve.
void ShenandoahFreeSet::reserve_regions(size_t to_reserve) {
  size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();

  for (size_t i = _heap->num_regions(); i > 0; i--) {
    size_t idx = i - 1;
    ShenandoahHeapRegion* r = _heap->get_region(idx);
    if (!_partitions.partition_id_matches(idx, Mutator)) {
      continue;
    }

    size_t ac = alloc_capacity(r);
    if (!ShenandoahPackEvacTightly && (ac != region_size_bytes)) {
      // Only use fully empty regions for Collector reserve if !ShenandoahPackEvacTightly
      continue;
    }

    assert (ac > 0, "Membership in free partition implies has capacity");

    bool move_to_collector = _partitions.capacity_of(Collector) < to_reserve;
    if (!move_to_collector) {
      // We've satisfied to_reserve
      break;
    }

    if (move_to_collector) {
      // Note: In a previous implementation, regions were only placed into the survivor space (collector_is_free) if
      // they were entirely empty.  I'm not sure I understand the rationale for that.  That alternative behavior would
      // tend to mix survivor objects with ephemeral objects, making it more difficult to reclaim the memory for the
      // ephemeral objects.
      _partitions.move_to_partition(idx, Collector, ac);
      log_debug(gc,free)("  Shifting region " SIZE_FORMAT " from mutator_free to collector_free", idx);
    }
  }

  if (LogTarget(Info, gc, free)::is_enabled()) {
    size_t reserve = _partitions.capacity_of(Collector);
    if (reserve < to_reserve) {
      log_info(gc, free)("Wanted " PROPERFMT " for young reserve, but only reserved: " PROPERFMT,
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
    log_debug(gc, free)("FreeSet map legend:"
                       " M:mutator_free C:collector_free H:humongous _:retired");
    log_debug(gc, free)(" mutator free range [" SIZE_FORMAT ".." SIZE_FORMAT "], "
                        " collector free range [" SIZE_FORMAT ".." SIZE_FORMAT "]",
                        _partitions.leftmost(Mutator), _partitions.rightmost(Mutator),
                        _partitions.leftmost(Collector), _partitions.rightmost(Collector));

    for (uint i = 0; i < _heap->num_regions(); i++) {
      ShenandoahHeapRegion *r = _heap->get_region(i);
      uint idx = i % 64;
      if ((i != 0) && (idx == 0)) {
        log_debug(gc, free)(" %6u: %s", i-64, buffer);
      }
      if (_partitions.partition_id_matches(i, Mutator)) {
        size_t capacity = alloc_capacity(r);
        available_mutator += capacity;
        consumed_mutator += region_size_bytes - capacity;
        buffer[idx] = (capacity == region_size_bytes)? 'M': 'm';
      } else if (_partitions.partition_id_matches(i, Collector)) {
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
    log_debug(gc, free)(" %6u: %s", (uint) (_heap->num_regions() - remnant), buffer);
  }
#endif

  LogTarget(Info, gc, free) lt;
  if (lt.is_enabled()) {
    ResourceMark rm;
    LogStream ls(lt);

    {
      size_t last_idx = 0;
      size_t max = 0;
      size_t max_contig = 0;
      size_t empty_contig = 0;

      size_t total_used = 0;
      size_t total_free = 0;
      size_t total_free_ext = 0;

      for (size_t idx = _partitions.leftmost(Mutator); idx <= _partitions.rightmost(Mutator); idx++) {
        if (_partitions.partition_id_matches(idx, Mutator)) {
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

      for (size_t idx = _partitions.leftmost(Collector); idx <= _partitions.rightmost(Collector); idx++) {
        if (_partitions.partition_id_matches(idx, Collector)) {
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

size_t ShenandoahFreeSet::unsafe_peek_free() const {
  // Deliberately not locked, this method is unsafe when free partition is modified.

  for (size_t index = _partitions.leftmost(Mutator); index <= _partitions.rightmost(Mutator); index++) {
    if (index < _partitions.max() && _partitions.partition_id_matches(index, Mutator)) {
      ShenandoahHeapRegion* r = _heap->get_region(index);
      if (r->free() >= MinTLABSize) {
        return r->free();
      }
    }
  }

  // It appears that no regions left
  return 0;
}

void ShenandoahFreeSet::print_on(outputStream* out) const {
  out->print_cr("Mutator Free Set: " SIZE_FORMAT "", _partitions.count(Mutator));
  for (size_t index = _partitions.leftmost(Mutator); index <= _partitions.rightmost(Mutator); index++) {
    if (_partitions.partition_id_matches(index, Mutator)) {
      _heap->get_region(index)->print_on(out);
    }
  }
  out->print_cr("Collector Free Set: " SIZE_FORMAT "", _partitions.count(Collector));
  for (size_t index = _partitions.leftmost(Collector); index <= _partitions.rightmost(Collector); index++) {
    if (_partitions.partition_id_matches(index, Collector)) {
      _heap->get_region(index)->print_on(out);
    }
  }
}

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
double ShenandoahFreeSet::internal_fragmentation() {
  double squared = 0;
  double linear = 0;
  int count = 0;

  for (size_t index = _partitions.leftmost(Mutator); index <= _partitions.rightmost(Mutator); index++) {
    if (_partitions.partition_id_matches(index, Mutator)) {
      ShenandoahHeapRegion* r = _heap->get_region(index);
      size_t used = r->used();
      squared += used * used;
      linear += used;
      count++;
    }
  }

  if (count > 0) {
    double s = squared / (ShenandoahHeapRegion::region_size_bytes() * linear);
    return 1 - s;
  } else {
    return 0;
  }
}

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
double ShenandoahFreeSet::external_fragmentation() {
  size_t last_idx = 0;
  size_t max_contig = 0;
  size_t empty_contig = 0;

  size_t free = 0;

  for (size_t index = _partitions.leftmost(Mutator); index <= _partitions.rightmost(Mutator); index++) {
    if (_partitions.partition_id_matches(index, Mutator)) {
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
  }

  if (free > 0) {
    return 1 - (1.0 * max_contig * ShenandoahHeapRegion::region_size_bytes() / free);
  } else {
    return 0;
  }
}

