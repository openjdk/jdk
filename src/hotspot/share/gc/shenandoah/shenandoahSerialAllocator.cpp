/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * DO NOT ALTER OR REMOVE THIS COPYRIGHT NOTICE OR THIS FILE HEADER.
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

#include "gc/shared/plab.hpp"
#include "gc/shenandoah/shenandoahAllocRequest.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.inline.hpp"
#include "gc/shenandoah/shenandoahSerialAllocator.hpp"
#include "logging/log.hpp"

using idx_t = ShenandoahSimpleBitMap::idx_t;

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

ShenandoahSerialAllocator::ShenandoahSerialAllocator(ShenandoahFreeSet* free_set)
  : ShenandoahAllocator(free_set),
    _heap(ShenandoahHeap::heap()),
    _alloc_bias_weight(INITIAL_ALLOC_BIAS_WEIGHT) {}

HeapWord* ShenandoahSerialAllocator::allocate(ShenandoahAllocRequest& req, bool& in_new_region) {
  shenandoah_assert_heaplocked();
  if (ShenandoahHeapRegion::requires_humongous(req.size())) {
    switch (req.type()) {
      case ShenandoahAllocRequest::_alloc_shared:
      case ShenandoahAllocRequest::_alloc_shared_gc:
        in_new_region = true;
        return _free_set->allocate_contiguous(req, /* is_humongous = */ true);
      case ShenandoahAllocRequest::_alloc_cds:
        in_new_region = true;
        return _free_set->allocate_contiguous(req, /* is_humongous = */ false);
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

HeapWord* ShenandoahSerialAllocator::allocate_single(ShenandoahAllocRequest& req, bool& in_new_region) {
  shenandoah_assert_heaplocked();

  if (req.is_mutator_alloc()) {
    return allocate_for_mutator(req, in_new_region);
  } else {
    return allocate_for_collector(req, in_new_region);
  }
}

HeapWord* ShenandoahSerialAllocator::allocate_for_mutator(ShenandoahAllocRequest& req, bool& in_new_region) {
  update_allocation_bias();

  ShenandoahRegionPartitions& partitions = _free_set->_partitions;
  if (partitions.is_empty(ShenandoahFreeSetPartitionId::Mutator)) {
    return nullptr;
  }

  if (partitions.alloc_from_left_bias(ShenandoahFreeSetPartitionId::Mutator)) {
    ShenandoahLeftRightIterator iterator(&partitions, ShenandoahFreeSetPartitionId::Mutator);
    return allocate_from_regions(iterator, req, in_new_region);
  }

  ShenandoahRightLeftIterator iterator(&partitions, ShenandoahFreeSetPartitionId::Mutator);
  return allocate_from_regions(iterator, req, in_new_region);
}

// Alternating allocation direction between GC passes improves evacuation performance by
// consuming partially-used regions before they become uncollectable floating garbage.
// We bias toward the side with fewer non-empty regions to pack allocations tightly.
void ShenandoahSerialAllocator::update_allocation_bias() {
  if (_alloc_bias_weight-- <= 0) {
    ShenandoahRegionPartitions& partitions = _free_set->_partitions;

    idx_t non_empty_on_left = (partitions.leftmost_empty(ShenandoahFreeSetPartitionId::Mutator)
                               - partitions.leftmost(ShenandoahFreeSetPartitionId::Mutator));
    idx_t non_empty_on_right = (partitions.rightmost(ShenandoahFreeSetPartitionId::Mutator)
                                - partitions.rightmost_empty(ShenandoahFreeSetPartitionId::Mutator));
    partitions.set_bias_from_left_to_right(ShenandoahFreeSetPartitionId::Mutator, (non_empty_on_right < non_empty_on_left));
    _alloc_bias_weight = INITIAL_ALLOC_BIAS_WEIGHT;
  }
}

template<typename Iter>
HeapWord* ShenandoahSerialAllocator::allocate_from_regions(Iter& iterator, ShenandoahAllocRequest& req, bool& in_new_region) {
  for (idx_t idx = iterator.current(); iterator.has_next(); idx = iterator.next()) {
    ShenandoahHeapRegion* r = _heap->get_region(idx);
    size_t min_size = req.is_lab_alloc() ? req.min_size() : req.size();
    if (_free_set->alloc_capacity(r) >= min_size * HeapWordSize) {
      HeapWord* result = try_allocate_in(r, req, in_new_region);
      if (result != nullptr) {
        return result;
      }
    }
  }
  return nullptr;
}

// Collector allocation: first try the reserved Collector/OldCollector partition,
// preferring regions with matching affiliation. If that fails and ShenandoahEvacReserveOverflow
// is enabled, steal an empty region from the Mutator partition.
HeapWord* ShenandoahSerialAllocator::allocate_for_collector(ShenandoahAllocRequest& req, bool& in_new_region) {
  shenandoah_assert_heaplocked();
  ShenandoahRegionPartitions& partitions = _free_set->_partitions;
  ShenandoahFreeSetPartitionId which_partition = req.is_old() ? ShenandoahFreeSetPartitionId::OldCollector
                                                              : ShenandoahFreeSetPartitionId::Collector;
  HeapWord* result = nullptr;
  if (partitions.alloc_from_left_bias(which_partition)) {
    ShenandoahLeftRightIterator iterator(&partitions, which_partition);
    result = allocate_with_affiliation(iterator, req.affiliation(), req, in_new_region);
  } else {
    ShenandoahRightLeftIterator iterator(&partitions, which_partition);
    result = allocate_with_affiliation(iterator, req.affiliation(), req, in_new_region);
  }

  if (result != nullptr) {
    return result;
  }

  if (!ShenandoahEvacReserveOverflow) {
    return nullptr;
  }

  // Overflow: steal empty region from mutator partition for collector use.
  if (partitions.get_empty_region_counts(ShenandoahFreeSetPartitionId::Mutator) > 0) {
    result = try_allocate_from_mutator(req, in_new_region);
  }

  return result;
}

template<typename Iter>
HeapWord* ShenandoahSerialAllocator::allocate_with_affiliation(Iter& iterator,
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
  if (free_region != nullptr) {
    HeapWord* result = try_allocate_in(free_region, req, in_new_region);
    assert(result != nullptr, "Allocate in free region in the partition always succeed.");
    return result;
  }
  log_debug(gc, free)("Could not allocate collector region with affiliation: %s for request " PTR_FORMAT,
                      shenandoah_affiliation_name(affiliation), p2i(&req));
  return nullptr;
}

// Flip an empty region from Mutator to Collector/OldCollector partition, then allocate in it.
// Searches from right to left to keep longer-lived collector regions at high addresses.
HeapWord* ShenandoahSerialAllocator::try_allocate_from_mutator(ShenandoahAllocRequest& req, bool& in_new_region) {
  ShenandoahRegionPartitions& partitions = _free_set->_partitions;
  ShenandoahRightLeftIterator iterator(&partitions, ShenandoahFreeSetPartitionId::Mutator, true);
  for (idx_t idx = iterator.current(); iterator.has_next(); idx = iterator.next()) {
    ShenandoahHeapRegion* r = _heap->get_region(idx);
    if (_free_set->can_allocate_from(r)) {
      if (req.is_old()) {
        if (!_free_set->flip_to_old_gc(r)) {
          continue;
        }
      } else {
        _free_set->flip_to_gc(r);
      }
      log_debug(gc, free)("Flipped region %zu to gc for request: " PTR_FORMAT, idx, p2i(&req));
      return try_allocate_in(r, req, in_new_region);
    }
  }

  return nullptr;
}

// Allocate within region r for the given request. This handles:
// 1. Region recycling and affiliation setup for new (empty) regions
// 2. LAB sizing (TLAB/GCLAB/PLAB shrink-to-fit)
// 3. Partition accounting (used, empty counts, retirement)
HeapWord* ShenandoahSerialAllocator::try_allocate_in(ShenandoahHeapRegion* r, ShenandoahAllocRequest& req, bool& in_new_region) {
  assert(_free_set->has_alloc_capacity(r), "Performance: should avoid full regions on this path: %zu", r->index());

  ShenandoahHeap* heap = ShenandoahHeap::heap();
  // Trash regions cannot be used while weak roots processing needs accurate marking info.
  if (heap->is_concurrent_weak_root_in_progress() && r->is_trash()) {
    return nullptr;
  }

  HeapWord* result = nullptr;
  r->try_recycle_under_lock();
  in_new_region = r->is_empty();
  if (in_new_region) {
    log_debug(gc, free)("Using new region (%zu) for %s (" PTR_FORMAT ").",
                        r->index(), req.type_string(), p2i(&req));
    assert(!r->is_affiliated(), "New region %zu should be unaffiliated", r->index());
    r->set_affiliation(req.affiliation());
    if (r->is_old()) {
      r->end_preemptible_coalesce_and_fill();
    }
#ifdef ASSERT
    ShenandoahMarkingContext* const ctx = heap->marking_context();
    assert(ctx->top_at_mark_start(r) == r->bottom(), "Newly established allocation region starts with TAMS equal to bottom");
    assert(ctx->is_bitmap_range_within_region_clear(ctx->top_bitmap(r), r->end()), "Bitmap above top_bitmap() must be clear");
#endif
  } else {
    assert(r->is_affiliated(), "Region %zu that is not new should be affiliated", r->index());
    if (r->affiliation() != req.affiliation()) {
      assert(heap->mode()->is_generational(), "Request for %s from %s region should only happen in generational mode.",
             req.affiliation_name(), r->affiliation_name());
      return nullptr;
    }
  }

  // Perform the actual allocation: LABs (TLAB/GCLAB/PLAB) may be shrunk to fit.
  if (req.is_lab_alloc()) {
    size_t adjusted_size = req.size();
    size_t free = align_down(r->free() >> LogHeapWordSize, MinObjAlignment);
    if (adjusted_size > free) {
      adjusted_size = free;
    }
    if (adjusted_size >= req.min_size()) {
      result = r->allocate(adjusted_size, req);
      assert(result != nullptr, "Allocation must succeed: free %zu, actual %zu", free, adjusted_size);
      req.set_actual_size(adjusted_size);
    } else {
      log_trace(gc, free)("Failed to shrink LAB request (%zu) in region %zu to %zu"
                          " because min_size() is %zu", req.size(), r->index(), adjusted_size, req.min_size());
    }
  } else {
    size_t size = req.size();
    result = r->allocate(size, req);
    if (result != nullptr) {
      req.set_actual_size(size);
    }
  }

  // Update partition used bytes on success.
  if (result != nullptr) {
    if (req.is_mutator_alloc()) {
      assert(req.is_young(), "Mutator allocations always come from young generation.");
      _free_set->_partitions.increase_used(ShenandoahFreeSetPartitionId::Mutator, req.actual_size() * HeapWordSize);
    } else {
      assert(req.is_gc_alloc(), "Should be gc_alloc since req wasn't mutator alloc");
      // GC allocations set update_watermark so relocated objects aren't re-updated during update-refs.
      r->set_update_watermark(r->top());
      if (r->is_old()) {
        _free_set->_partitions.increase_used(ShenandoahFreeSetPartitionId::OldCollector, (req.actual_size() + req.waste()) * HeapWordSize);
      } else {
        _free_set->_partitions.increase_used(ShenandoahFreeSetPartitionId::Collector, (req.actual_size() + req.waste()) * HeapWordSize);
      }
    }
  }

  ShenandoahFreeSetPartitionId orig_partition;
  if (req.is_mutator_alloc()) {
    orig_partition = ShenandoahFreeSetPartitionId::Mutator;
  } else if (req.is_old()) {
    orig_partition = ShenandoahFreeSetPartitionId::OldCollector;
  } else {
    orig_partition = ShenandoahFreeSetPartitionId::Collector;
  }

  DEBUG_ONLY(bool boundary_changed = false;)
  if ((result != nullptr) && in_new_region) {
    _free_set->_partitions.one_region_is_no_longer_empty(orig_partition);
    DEBUG_ONLY(boundary_changed = true;)
  }

  // Retire the region if remaining capacity is too small for any future PLAB.
  if (_free_set->alloc_capacity(r) < PLAB::min_size() * HeapWordSize) {
    size_t idx = r->index();
    size_t waste_bytes = _free_set->_partitions.retire_from_partition(orig_partition, idx, r->used());
    DEBUG_ONLY(boundary_changed = true;)
    if (req.is_mutator_alloc() && (waste_bytes > 0)) {
      req.set_waste(waste_bytes / HeapWordSize);
    }
  }

  // Recompute generation used/affiliated totals.
  _free_set->notify_allocation(orig_partition, in_new_region);

#ifdef ASSERT
  if (boundary_changed) {
    _free_set->_partitions.assert_bounds();
  } else {
    _free_set->_partitions.assert_bounds_sanity();
  }
#endif
  return result;
}
