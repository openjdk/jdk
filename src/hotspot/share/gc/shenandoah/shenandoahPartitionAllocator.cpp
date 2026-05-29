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
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahPartitionAllocator.hpp"
#include "logging/log.hpp"

template<ShenandoahFreeSetPartitionId PARTITION>
ShenandoahPartitionAllocator<PARTITION>::ShenandoahPartitionAllocator(ShenandoahFreeSet* free_set)
  : _free_set(free_set),
    _retained_region(nullptr) {}

template<ShenandoahFreeSetPartitionId PARTITION>
HeapWord* ShenandoahPartitionAllocator<PARTITION>::allocate(ShenandoahAllocRequest& req, bool& in_new_region) {
  // Mutator allocations may yield to safepoint; GC allocations cannot.
  ShenandoahHeapLocker locker(ShenandoahHeap::heap()->lock(), req.is_mutator_alloc());

  // OldCollector: verify old generation has room before attempting allocation.
  if constexpr (PARTITION == ShenandoahFreeSetPartitionId::OldCollector) {
    if (!ShenandoahHeap::heap()->old_generation()->can_allocate(req)) {
      return nullptr;
    }
  }

  // Fast path: try the retained region first.
  if (_retained_region != nullptr) {
    size_t min_size = req.is_lab_alloc() ? req.min_size() : req.size();
    if (_free_set->alloc_capacity(_retained_region) >= min_size * HeapWordSize) {
      HeapWord* result = try_allocate_in(_retained_region, req, in_new_region);
      if (result != nullptr) {
        return result;
      }
    }
    _retained_region = nullptr;
  }

  // Ask FreeSet to find a suitable region.
  ShenandoahAffiliation affiliation = (PARTITION == ShenandoahFreeSetPartitionId::OldCollector)
                                      ? OLD_GENERATION : YOUNG_GENERATION;
  size_t min_size_words = req.is_lab_alloc() ? req.min_size() : req.size();
  ShenandoahHeapRegion* r = _free_set->find_region_for_alloc(PARTITION, min_size_words, affiliation, in_new_region);

  if (r != nullptr) {
    HeapWord* result = try_allocate_in(r, req, in_new_region);
    if (result != nullptr) {
      return result;
    }
  }

  // Collector partitions can overflow into Mutator partition.
  if constexpr (PARTITION != ShenandoahFreeSetPartitionId::Mutator) {
    if (ShenandoahEvacReserveOverflow) {
      ShenandoahHeapRegion* stolen = _free_set->steal_from_mutator(PARTITION, req, in_new_region);
      if (stolen != nullptr) {
        return try_allocate_in(stolen, req, in_new_region);
      }
    }
  }

  return nullptr;
}

template<ShenandoahFreeSetPartitionId PARTITION>
HeapWord* ShenandoahPartitionAllocator<PARTITION>::try_allocate_in(ShenandoahHeapRegion* r, ShenandoahAllocRequest& req, bool& in_new_region) {
  assert(_free_set->alloc_capacity(r) > 0, "Performance: should avoid full regions on this path: %zu", r->index());

  HeapWord* result = nullptr;

  // Perform the actual allocation: LABs may be shrunk to fit.
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
    if constexpr (PARTITION == ShenandoahFreeSetPartitionId::Mutator) {
      assert(req.is_young(), "Mutator allocations always come from young generation.");
      _free_set->increase_partition_used(PARTITION, req.actual_size() * HeapWordSize);
    } else {
      assert(req.is_gc_alloc(), "Should be gc_alloc since req wasn't mutator alloc");
      // GC allocations set update_watermark so relocated objects aren't re-updated during update-refs.
      r->set_update_watermark(r->top());
      _free_set->increase_partition_used(PARTITION, (req.actual_size() + req.waste()) * HeapWordSize);
    }
  }

  bool boundary_changed = false;
  if ((result != nullptr) && in_new_region) {
    _free_set->mark_region_used(PARTITION);
    boundary_changed = true;
  }

  // Retire the region if remaining capacity is too small for any future PLAB.
  if (_free_set->alloc_capacity(r) < PLAB::min_size() * HeapWordSize) {
    size_t idx = r->index();
    size_t waste_bytes = _free_set->retire_region(PARTITION, idx, r->used());
    boundary_changed = true;
    if constexpr (PARTITION == ShenandoahFreeSetPartitionId::Mutator) {
      if (waste_bytes > 0) {
        req.set_waste(waste_bytes / HeapWordSize);
      }
    }
    if (_retained_region == r) {
      _retained_region = nullptr;
    }
  } else if (result != nullptr) {
    // Region still has usable capacity — retain for next allocation.
    _retained_region = r;
  }

  // Recompute generation used/affiliated totals and validate bounds if changed.
  _free_set->notify_allocation(PARTITION, in_new_region, boundary_changed);
  return result;
}

// Explicit template instantiations for all partitions.
template class ShenandoahPartitionAllocator<ShenandoahFreeSetPartitionId::Mutator>;
template class ShenandoahPartitionAllocator<ShenandoahFreeSetPartitionId::Collector>;
template class ShenandoahPartitionAllocator<ShenandoahFreeSetPartitionId::OldCollector>;
