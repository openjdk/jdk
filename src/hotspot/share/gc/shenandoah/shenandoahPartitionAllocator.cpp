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

#include "gc/shared/workerThread.hpp"
#include "gc/shenandoah/shenandoahAllocRequest.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahPartitionAllocator.hpp"
#include "gc/shenandoah/shenandoahThreadLocalData.hpp"

template<ShenandoahFreeSetPartitionId PARTITION>
ShenandoahPartitionAllocator<PARTITION>::ShenandoahPartitionAllocator(ShenandoahFreeSet* free_set, uint32_t alloc_region_count)
  : _free_set(free_set),
    _alloc_region_count(clamped_alloc_region_count(alloc_region_count)),
    _alloc_region_slot_mask(_alloc_region_count - 1u) {
  for (uint32_t i = 0; i < MAX_ALLOC_REGIONS; i++) {
    _alloc_regions[i].store_relaxed(nullptr);
  }
}

template<ShenandoahFreeSetPartitionId PARTITION>
uint32_t ShenandoahPartitionAllocator<PARTITION>::alloc_region_slot(Thread* thread) {
  if (_alloc_region_count <= 1u) {
    return 0u;
  }

  if constexpr (PARTITION != ShenandoahFreeSetPartitionId::Mutator) {
    const uint worker_id = WorkerThread::worker_id();
    if (worker_id != UINT_MAX) {
      return checked_cast<uint32_t>(worker_id) & _alloc_region_slot_mask;
    }
  }

  const uint32_t slot = ShenandoahThreadLocalData::round_robin_probe(thread) & _alloc_region_slot_mask;
  assert(slot < _alloc_region_count, "slot in range");
  return slot;
}

template<ShenandoahFreeSetPartitionId PARTITION>
template<bool HEAP_LOCKED>
HeapWord* ShenandoahPartitionAllocator<PARTITION>::try_allocate_in_alloc_regions(ShenandoahAllocRequest& req,
                                                                                 bool& in_new_region,
                                                                                 const uint32_t start_slot,
                                                                                 const uint32_t count,
                                                                                 uint32_t& slots_ready_to_replenish) {
  assert(count <= _alloc_region_count, "Must be");
  if (HEAP_LOCKED) {
    shenandoah_assert_heaplocked();
  }
  uint32_t i = start_slot & _alloc_region_slot_mask;
  for (uint32_t n = 0; n < count; n++) {
    ShenandoahHeapRegion* r = HEAP_LOCKED ? _alloc_regions[i].load_relaxed() : _alloc_regions[i].load_acquire();
    if (r != nullptr) {
      bool ready_to_replenish = false;
      HeapWord* obj = try_atomic_allocate_in(r, req, in_new_region, ready_to_replenish);
      if (ready_to_replenish) {
        slots_ready_to_replenish++;
        // The last thread successfully allocating in the region needs to uninstall the region
        if (obj != nullptr) {
          uninstall_alloc_region(i, r);
        }
      }
      if (obj != nullptr) {
        return obj;
      }
    } else {
      slots_ready_to_replenish++;
    }
    i = (i + 1) & _alloc_region_slot_mask;
  }
  return nullptr;
}

template<ShenandoahFreeSetPartitionId PARTITION>
bool ShenandoahPartitionAllocator<PARTITION>::uninstall_alloc_region(const uint32_t slot, ShenandoahHeapRegion* occupant) {
  assert(occupant != nullptr, "Must not be nullptr");
  if (_alloc_regions[slot].compare_exchange(occupant, nullptr, memory_order_release) == occupant) {
    occupant->unset_active_alloc_region();
    return true;
  }
  return false;
}

template<ShenandoahFreeSetPartitionId PARTITION>
HeapWord* ShenandoahPartitionAllocator<PARTITION>::allocate(ShenandoahAllocRequest& req, bool& in_new_region) {
  Thread* const thread = Thread::current();
  uint32_t const slot = alloc_region_slot(thread);

  uint32_t slots_ready_to_replenish = 0;
  // Fast path: lock-free CAS bump, try all alloc regions starting from this thread's slot.
  HeapWord* obj = try_allocate_in_alloc_regions<false>(req, in_new_region, slot, _alloc_region_count, slots_ready_to_replenish);
  if (obj != nullptr) {
    return obj;
  }

  // All slots exhausted or empty — enter slow path to batch replenish.
  {
    ShenandoahHeap* const heap = ShenandoahHeap::heap();
    ShenandoahHeapLocker heap_locker(heap->lock(), req.is_mutator_alloc());

    // Another thread may have replenished while we waited for the lock; rescan the slots.
    slots_ready_to_replenish = 0;
    obj = try_allocate_in_alloc_regions<true>(req, in_new_region, slot, _alloc_region_count, slots_ready_to_replenish);
    if (obj != nullptr) {
      return obj;
    }

    if constexpr (PARTITION == ShenandoahFreeSetPartitionId::OldCollector) {
      if (!req.is_promotion() && !heap->old_generation()->can_allocate(req)) {
        return nullptr;
      }
    }

    // Batch replenish empty/exhausted slots, then retry.
    uint32_t replenished = 0;
    if (slots_ready_to_replenish > 0) {
      replenished = replenish_alloc_regions<true>(slots_ready_to_replenish, &req, &obj, &in_new_region);
      if (obj != nullptr) {
        return obj;
      }
      if constexpr (PARTITION == ShenandoahFreeSetPartitionId::Mutator) {
        // Did not find any region from free set for replenishment, no memory for mutator alloc
        if (replenished == 0 && slots_ready_to_replenish > 0) {
          return nullptr;
        }
      }
    }

    ShenandoahHeapRegion* alloc_region = nullptr;
    if (slots_ready_to_replenish == 0 || replenished > 0) {
      // All slots still have capacity but none could satisfy this request — try a fresh region.
      alloc_region = _free_set->find_region_for_alloc<PARTITION>((req.is_lab_alloc() ? req.min_size() : req.size()), in_new_region);
    }
    if constexpr (PARTITION != ShenandoahFreeSetPartitionId::Mutator) {
      if (alloc_region == nullptr && ShenandoahEvacReserveOverflow) {
        alloc_region = _free_set->steal_from_mutator(PARTITION, req);
        if (alloc_region != nullptr) {
          assert(alloc_region->is_empty(), "Stolen region must be empty");
          in_new_region = true;
        }
      }
    }

    if (alloc_region != nullptr) {
      bool retired_after_alloc = false;
      HeapWord* result = allocate_in(alloc_region, req, retired_after_alloc);
      assert(result != nullptr, "Sanity check - allocate_in should always succeed");
      if (in_new_region) {
        _free_set->mark_region_used(PARTITION);
      }
      bool boundary_changed = in_new_region || retired_after_alloc;
      _free_set->notify_allocation(PARTITION, in_new_region, boundary_changed);
      return result;
    }
    return nullptr;
  }
}

template<ShenandoahFreeSetPartitionId PARTITION>
HeapWord* ShenandoahPartitionAllocator<PARTITION>::allocate_in(ShenandoahHeapRegion* r,
                                                               ShenandoahAllocRequest& req,
                                                               bool& retired_after_alloc) {
  assert(!r->is_atomic_alloc_region(), "Must not be an atomic alloc region.");
  assert(!retired_after_alloc, "Initial value must be false");

  HeapWord* result = nullptr;
  if (req.is_lab_alloc()) {
    size_t adjusted_size = req.size();
    size_t free = align_down(r->free_words(), MinObjAlignment);
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

  if constexpr (PARTITION == ShenandoahFreeSetPartitionId::Mutator) {
    assert(req.is_young(), "Mutator allocations always come from young generation.");
    _free_set->increase_partition_used(PARTITION, req.actual_size() * HeapWordSize);
  } else {
    assert(req.is_gc_alloc(), "Should be gc_alloc since req wasn't mutator alloc");
    r->set_update_watermark(r->top());
    _free_set->increase_partition_used(PARTITION, (req.actual_size() + req.waste()) * HeapWordSize);
  }

  if (r->free_words() < ShenandoahHeap::plab_min_size()) {
    size_t idx = r->index();
    size_t waste_bytes = _free_set->retire_region(PARTITION, idx, r->used());
    if constexpr (PARTITION == ShenandoahFreeSetPartitionId::Mutator) {
      if (waste_bytes > 0) {
        req.set_waste(waste_bytes / HeapWordSize);
      }
    }
    retired_after_alloc = true;
  }
  return result;
}

template<ShenandoahFreeSetPartitionId PARTITION>
HeapWord* ShenandoahPartitionAllocator<PARTITION>::try_atomic_allocate_in(ShenandoahHeapRegion* r,
                                                                          ShenandoahAllocRequest& req,
                                                                          bool &in_new_region,
                                                                          bool& ready_to_replenish) {
  HeapWord* obj = nullptr;
  if (req.is_lab_alloc()) {
    obj = r->allocate_lab_atomic(req, ready_to_replenish);
  } else {
    obj = r->allocate_atomic(req, ready_to_replenish);
  }
  if (obj != nullptr && obj == r->bottom()) {
    in_new_region = true;
  }
  return obj;
}

template<ShenandoahFreeSetPartitionId PARTITION>
template<bool HAS_PENDING_ALLOC_REQ>
uint32_t ShenandoahPartitionAllocator<PARTITION>::replenish_alloc_regions(uint32_t& empty_slots_count,
  ShenandoahAllocRequest* req, HeapWord** obj, bool* in_new_region) {
  shenandoah_assert_heaplocked();
  assert((HAS_PENDING_ALLOC_REQ && req != nullptr && obj != nullptr && in_new_region != nullptr) ||
         (!HAS_PENDING_ALLOC_REQ && req == nullptr && obj == nullptr && in_new_region == nullptr), "Sanity");

  uint32_t empty_slots[MAX_ALLOC_REGIONS];
  empty_slots_count = 0u;
  // Prepare exhausted slots for replenishment.
  for (uint32_t i = 0; i < _alloc_region_count; i++) {
    ShenandoahHeapRegion* const r = _alloc_regions[i].load_relaxed();
    if (r == nullptr) {
      empty_slots[empty_slots_count++] = i;
    } else if (r->free_words() < ShenandoahHeap::plab_min_size()) {
      bool uninstalled = uninstall_alloc_region(i, r);
      assert(uninstalled || _alloc_regions[i].load_relaxed() == nullptr, "Only the lock-free path may clear a slot");
      empty_slots[empty_slots_count++] = i;
    }
  }
  if (empty_slots_count == 0u) {
    return 0u;
  }

  ShenandoahHeapRegion* reserved[MAX_ALLOC_REGIONS];
  uint32_t reserved_count = _free_set->reserve_alloc_regions<PARTITION>(empty_slots_count,
                                                                        ShenandoahHeap::plab_min_size(),
                                                                        reserved);
  assert(reserved_count <= empty_slots_count, "Cannot reserve more regions than empty slots");

  if (reserved_count == 0) {
    return  0;
  }

  uint32_t replenished_count = 0u;
  for (uint32_t i = 0; i < reserved_count; i++) {
    ShenandoahHeapRegion* const r = reserved[i];
    const uint32_t slot = empty_slots[i];

    assert(_alloc_regions[slot].load_relaxed() == nullptr, "Slot must remain empty under the heap lock");
    assert(r->is_atomic_alloc_region(), "Reserved region must be active before publication");

    if constexpr (HAS_PENDING_ALLOC_REQ) {
      if (*obj == nullptr) {
        bool ready_to_replenish = false;
        *obj = try_atomic_allocate_in(r, *req, *in_new_region, ready_to_replenish);
        if (*obj == nullptr || !ready_to_replenish) {
          _alloc_regions[slot].release_store(reserved[i]);
          replenished_count++;
        } else {
          assert(r->free_words() < ShenandoahHeap::plab_min_size(), "Must be");
          r->unset_active_alloc_region();
        }
      } else {
        _alloc_regions[slot].release_store(r);
        replenished_count++;
      }
    } else {
      _alloc_regions[slot].release_store(r);
      replenished_count++;
    }
  }
  return replenished_count;
}

template<ShenandoahFreeSetPartitionId PARTITION>
void ShenandoahPartitionAllocator<PARTITION>::release_alloc_regions() {
  shenandoah_assert_heaplocked();
  for (uint32_t i = 0; i < _alloc_region_count; i++) {
    release_alloc_region(i);
  }
}

template<ShenandoahFreeSetPartitionId PARTITION>
void ShenandoahPartitionAllocator<PARTITION>::reserve_alloc_regions() {
  shenandoah_assert_heaplocked();
#ifdef ASSERT
  for (uint32_t i = 0; i < _alloc_region_count; i++) {
    assert(_alloc_regions[i].load_relaxed() == nullptr, "alloc region slot must be cleared");
  }
#endif
  uint32_t empty_slots_count = 0;
  replenish_alloc_regions(empty_slots_count);

  assert(empty_slots_count == alloc_region_count(), "Sanity");
}

// Returning a region with at least PLAB::min_size() free to the free set is only safe when no
// thread can still allocate from it through a stale reference (e.g. at a safepoint, or for
// collector regions once evacuation is off and all threads have observed it). Callers must not
// release alloc regions otherwise; exhausted regions are uninstalled on the allocation path.
template<ShenandoahFreeSetPartitionId PARTITION>
void ShenandoahPartitionAllocator<PARTITION>::release_alloc_region(uint32_t slot) {
  shenandoah_assert_heaplocked();
  ShenandoahHeapRegion* alloc_region = _alloc_regions[slot].load_relaxed();
  if (alloc_region == nullptr) {
    return;
  }

  const bool uninstalled = uninstall_alloc_region(slot, alloc_region);
  assert(uninstalled, "No lock-free allocator may race release (safepoint or evac off)");
  if (alloc_region->free_words() >= ShenandoahHeap::plab_min_size()) {
    _free_set->unretire_alloc_region(PARTITION, alloc_region);
  }
}

template class ShenandoahPartitionAllocator<ShenandoahFreeSetPartitionId::Mutator>;
template class ShenandoahPartitionAllocator<ShenandoahFreeSetPartitionId::Collector>;
template class ShenandoahPartitionAllocator<ShenandoahFreeSetPartitionId::OldCollector>;
