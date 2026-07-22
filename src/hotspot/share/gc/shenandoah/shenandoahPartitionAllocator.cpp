/*
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

#include "gc/shared/plab.hpp"
#include "gc/shenandoah/shenandoahAllocRequest.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.inline.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahPartitionAllocator.hpp"
#include "logging/log.hpp"

template<ShenandoahFreeSetPartitionId PARTITION>
ShenandoahPartitionAllocator<PARTITION>::ShenandoahPartitionAllocator(ShenandoahFreeSet* free_set)
  : _free_set(free_set),
    _alloc_region(nullptr) {}

template<ShenandoahFreeSetPartitionId PARTITION>
HeapWord* ShenandoahPartitionAllocator<PARTITION>::allocate(ShenandoahAllocRequest& req, bool& in_new_region) {
  // Mutator allocations may yield to safepoint; GC allocations cannot.
  ShenandoahHeapLocker locker(ShenandoahHeap::heap()->lock(), req.is_mutator_alloc());

  // OldCollector: verify old generation has room before attempting allocation.
  if constexpr (PARTITION == ShenandoahFreeSetPartitionId::OldCollector) {
    if (!req.is_promotion() && !ShenandoahHeap::heap()->old_generation()->can_allocate(req)) {
      return nullptr;
    }
  }

  bool boundary_changed = false;
  size_t min_req_words = req.is_lab_alloc() ? req.min_size() : req.size();
  // Fast path: try the cached alloc region first.
  if (_alloc_region != nullptr) {
    constexpr ShenandoahAffiliation affiliation =
      (PARTITION == ShenandoahFreeSetPartitionId::OldCollector) ? OLD_GENERATION : YOUNG_GENERATION;
    assert(!_alloc_region->is_trash() && _alloc_region->affiliation() == affiliation &&
           _free_set->membership(_alloc_region->index()) == PARTITION,
           "Cached alloc region %zu must remain a non-trash member of this partition until the free set is rebuilt",
           _alloc_region->index());
    HeapWord* result = nullptr;
    size_t ac_words = _alloc_region->free() >> LogHeapWordSize;
    // A region is only ever cached while it has at least PLAB::min_size of capacity, and its
    // free space shrinks only via allocate_in (which retires and clears it below that threshold).
    // So the cached alloc region always has usable capacity here: use it when it can satisfy this
    // request, otherwise keep it cached for a smaller future request and fall through.
    assert(ac_words >= PLAB::min_size(),
           "Cached alloc region %zu must keep at least PLAB::min_size capacity, has %zu words",
           _alloc_region->index(), ac_words);
    if (ac_words >= min_req_words) {
      result = allocate_in(_alloc_region, req, boundary_changed);
    }
    if (result != nullptr) {
      in_new_region = false;
      _free_set->notify_allocation(PARTITION, false, boundary_changed);
      return result;
    }
  }

  // Ask FreeSet to find a suitable region.
  ShenandoahHeapRegion* r = _free_set->find_region_for_alloc<PARTITION>(min_req_words, in_new_region);
  // Collector partitions can overflow into Mutator partition.
  if constexpr (PARTITION != ShenandoahFreeSetPartitionId::Mutator) {
    if (r == nullptr && ShenandoahEvacReserveOverflow) {
      r = _free_set->steal_from_mutator(PARTITION, req);
      if (r != nullptr) {
        assert(r->is_empty(), "Stolen region must be empty");
        in_new_region = true;
      }
    }
  }

  if (r != nullptr) {
    HeapWord* result = allocate_in(r, req, boundary_changed);
    if (in_new_region) {
      _free_set->mark_region_used(PARTITION);
      boundary_changed = true;
    }
    _free_set->notify_allocation(PARTITION, in_new_region, boundary_changed);
    return result;
  }

  // Every path that mutates a partition boundary (allocate_in retire, new region, steal) returns
  // above, so reaching here means no allocation and no boundary change.
  return nullptr;
}

template<ShenandoahFreeSetPartitionId PARTITION>
HeapWord* ShenandoahPartitionAllocator<PARTITION>::allocate_in(ShenandoahHeapRegion* r, ShenandoahAllocRequest& req, bool& boundary_changed) {
  assert(_free_set->alloc_capacity(r) > 0, "Performance: should avoid full regions on this path: %zu", r->index());

  HeapWord* result = nullptr;

  // Perform the actual allocation: LABs may be shrunk to fit.
  if (req.is_lab_alloc()) {
    size_t adjusted_size = req.size();
    size_t free = align_down(r->free() >> LogHeapWordSize, MinObjAlignment);
    if (adjusted_size > free) {
      adjusted_size = free;
    }
    assert(adjusted_size >= req.min_size(),
           "Caller must ensure region has at least min_size capacity: free=%zu, min_size=%zu",
           free, req.min_size());
    result = r->allocate(adjusted_size, req);
    req.set_actual_size(adjusted_size);
  } else {
    size_t size = req.size();
    result = r->allocate(size, req);
    req.set_actual_size(size);
  }
  assert(result != nullptr, "Allocation must succeed, region free: %zu, request minimal size: %zu",
    r->free(), req.is_lab_alloc() ? req.min_size() : req.size());

  // Update partition used bytes after allocation
  if constexpr (PARTITION == ShenandoahFreeSetPartitionId::Mutator) {
    assert(req.is_young(), "Mutator allocations always come from young generation.");
    _free_set->increase_partition_used(PARTITION, req.actual_size() * HeapWordSize);
  } else {
    assert(req.is_gc_alloc(), "Should be gc_alloc since req wasn't mutator alloc");
    // For GC allocations, we advance update_watermark because the objects relocated into this memory during
    // evacuation are not updated during evacuation. For both young and old regions, it is essential that all
    // PLABs be made parsable at the end of evacuation. This is enabled by retiring all plabs at end of evacuation.
    r->set_update_watermark(r->top());
    _free_set->increase_partition_used(PARTITION, (req.actual_size() + req.waste()) * HeapWordSize);
  }

  // Retire the region if remaining capacity is too small for any future PLAB.
  if ((r->free() >> LogHeapWordSize) < PLAB::min_size()) {
    size_t idx = r->index();
    size_t waste_bytes = _free_set->retire_region(PARTITION, idx, r->used());
    boundary_changed = true;
    if constexpr (PARTITION == ShenandoahFreeSetPartitionId::Mutator) {
      if (waste_bytes > 0) {
        req.set_waste(waste_bytes / HeapWordSize);
      }
    }
    if (_alloc_region == r) {
      _alloc_region = nullptr;
    }
  } else if (_alloc_region == nullptr) {
    // Region still has usable capacity — cache it for next allocation.
    _alloc_region = r;
  }

  return result;
}

// Explicit template instantiations for all partitions.
template class ShenandoahPartitionAllocator<ShenandoahFreeSetPartitionId::Mutator>;
template class ShenandoahPartitionAllocator<ShenandoahFreeSetPartitionId::Collector>;
template class ShenandoahPartitionAllocator<ShenandoahFreeSetPartitionId::OldCollector>;
