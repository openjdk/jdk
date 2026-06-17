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
    _alloc_region(nullptr) {}

template<ShenandoahFreeSetPartitionId PARTITION>
HeapWord* ShenandoahPartitionAllocator<PARTITION>::allocate(ShenandoahAllocRequest& req, bool& in_new_region) {
  ShenandoahHeapRegion* cas_alloc_region = _alloc_region.load_acquire();
  HeapWord* obj = nullptr;
  if (cas_alloc_region != nullptr) {
    // Fast-path: try CAS allocation using the cached alloc region
    obj = try_atomic_allocate_in(cas_alloc_region, req);
    if (obj != nullptr) {
      in_new_region = false;
      return obj;
    }
  }

  // Slow-path with heap lock
  {
    // Mutator allocations may yield to safepoint; GC allocations cannot
    ShenandoahHeapLocker locker(ShenandoahHeap::heap()->lock(), req.is_mutator_alloc());
    // First check whether the cached alloc region has changed
    ShenandoahHeapRegion* r = _alloc_region.load_acquire();
    if (r != nullptr && r != cas_alloc_region) {
      obj = try_atomic_allocate_in(r, req);
      if (obj != nullptr) {
        in_new_region = false;
        return obj;
      }
    }

    // OldCollector: verify old generation has room before attempting allocation
    if constexpr (PARTITION == ShenandoahFreeSetPartitionId::OldCollector) {
      if (!req.is_promotion() && !ShenandoahHeap::heap()->old_generation()->can_allocate(req)) {
        return nullptr;
      }
    }

    bool boundary_changed = false;
    size_t min_req_words = req.is_lab_alloc() ? req.min_size() : req.size();
    // Ask FreeSet to find a suitable region
     r = _free_set->find_region_for_alloc<PARTITION>(min_req_words, in_new_region);
    // Collector partitions can overflow into Mutator partition
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
      assert(result != nullptr, "Sanity check - allocate_in should always succeed");
      if (in_new_region) {
        _free_set->mark_region_used(PARTITION);
        boundary_changed = true;
      }
      if (_alloc_region.load_acquire() == nullptr && _free_set->alloc_capacity(r) >> LogHeapWordSize >= PLAB::min_size()) {
        size_t remnant_bytes = _free_set->retire_region(PARTITION, r->index(), r->used());
        assert(remnant_bytes == _free_set->alloc_capacity(r), "Sanity check");
        // The flag must be set BEFORE the region becomes an active alloc region, so any
        // thread that can observe the region via _atomic_top also observes the flag as true.
        if (PARTITION != ShenandoahFreeSetPartitionId::Mutator) {
          r->set_collector_allocator_reserved(true);
        }
        r->set_active_alloc_region();
        _alloc_region.release_store(r);
        boundary_changed = true;
      }
      _free_set->notify_allocation(PARTITION, in_new_region, boundary_changed);
      return result;
    }

    return nullptr;
  }
}

template<ShenandoahFreeSetPartitionId PARTITION>
HeapWord* ShenandoahPartitionAllocator<PARTITION>::allocate_in(ShenandoahHeapRegion* r, ShenandoahAllocRequest& req, bool& boundary_changed) {
  assert(!r->is_atomic_alloc_region(), "Must not be an atomic alloc region.");

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
  }
  return result;
}

template<ShenandoahFreeSetPartitionId PARTITION>
HeapWord* ShenandoahPartitionAllocator<PARTITION>::try_atomic_allocate_in(ShenandoahHeapRegion* r, ShenandoahAllocRequest& req) {
  size_t actual_size;
  bool ready_for_replenish = false;
  HeapWord* obj = nullptr;
  if (req.is_lab_alloc()) {
    obj = r->allocate_lab_atomic(req, actual_size, ready_for_replenish);
  } else {
    actual_size = req.size();
    obj = r->allocate_atomic(req, ready_for_replenish);
  }

  if (obj != nullptr) {
    req.set_actual_size(actual_size);
    if (ready_for_replenish) {
      // Claim the region for retirement with a CAS on _alloc_region. Multiple threads may
      // observe ready_for_replenish concurrently on this lock-free path, but only the thread
      // that wins this CAS may retire it: unset_active_alloc_region() is not safe to run
      // concurrently on the same region (two unsetters can clobber each other's _top sync).
      if (_alloc_region.compare_exchange(r, (ShenandoahHeapRegion*) nullptr) == r) {
        bool unset = r->unset_active_alloc_region();
        assert(unset, "Winner of the retire CAS must deactivate the region");
        if (PARTITION != ShenandoahFreeSetPartitionId::Mutator) {
          // The region is now full of evacuated objects. Advance the update watermark to its
          // final top so update-refs covers them, and clear the reserved flag so the barrier
          // stops forcing bulk updates over the region. unset_active_alloc_region() has already
          // synced _atomic_top to _top, so stable_top() is the authoritative final top.
          r->set_update_watermark(r->stable_top());
          r->set_collector_allocator_reserved(false);
        }
      }
    }
  }
  return obj;
}

template<ShenandoahFreeSetPartitionId PARTITION>
void ShenandoahPartitionAllocator<PARTITION>::release_alloc_region() {
  shenandoah_assert_heaplocked();
  ShenandoahHeapRegion* alloc_region = _alloc_region.load_acquire();
  if (alloc_region == nullptr) {
    return;
  }
  // Claim the region for retirement with a CAS. Even while holding the heap lock, the
  // lock-free fast path (try_atomic_allocate_in) may concurrently retire the cached region;
  // only the thread that wins this CAS may call unset_active_alloc_region(), which is not
  // safe to run concurrently on the same region.
  if (_alloc_region.compare_exchange(alloc_region, (ShenandoahHeapRegion*) nullptr) != alloc_region) {
    return;
  }
  // Sync _atomic_top back to _top and deactivate CAS allocation. unset_active_alloc_region()
  // also resets the region age if it received any allocation while active.
  if (!alloc_region->unset_active_alloc_region()) {
    return;
  }
  if (PARTITION != ShenandoahFreeSetPartitionId::Mutator) {
    // Cover the objects evacuated into this region so update-refs processes them, then
    // clear the reserved flag so the barrier stops forcing bulk updates over the region.
    alloc_region->set_update_watermark(alloc_region->stable_top());
    alloc_region->set_collector_allocator_reserved(false);
  }
  if (alloc_region->free() >> LogHeapWordSize >= PLAB::min_size()) {
    // Region is still allocatable: return its unconsumed remnant to the partition and
    // make it a free-set member again.
    _free_set->unretire_alloc_region(PARTITION, alloc_region);
  }
  // Otherwise the region is effectively full; it stays retired and remains fully charged as used.
}

// Explicit template instantiations for all partitions.
template class ShenandoahPartitionAllocator<ShenandoahFreeSetPartitionId::Mutator>;
template class ShenandoahPartitionAllocator<ShenandoahFreeSetPartitionId::Collector>;
template class ShenandoahPartitionAllocator<ShenandoahFreeSetPartitionId::OldCollector>;
