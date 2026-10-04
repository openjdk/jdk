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
#include "gc/shenandoah/shenandoahMarkingContext.inline.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahPartitionAllocator.hpp"
#include "gc/shenandoah/shenandoahThreadLocalData.hpp"
#include "logging/log.hpp"

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
                                                                                 const uint32_t count) {
  assert(count < _alloc_region_count, "Must be");
  if (HEAP_LOCKED) {
    shenandoah_assert_heaplocked();
  }
  uint32_t i = start_slot & _alloc_region_slot_mask;
  for (uint32_t n = 0; n < count; n++) {
    ShenandoahHeapRegion* r = HEAP_LOCKED ? _alloc_regions[i].load_relaxed() : _alloc_regions[i].load_acquire();
    if (r != nullptr) {
      HeapWord* obj = try_atomic_allocate_in(r, req);
      if (HEAP_LOCKED &&
          (r->free_relaxed() >> LogHeapWordSize) < ShenandoahHeap::plab_min_size()) {
        uninstall_alloc_region(i, r);
      }
      if (obj != nullptr) {
        in_new_region = false;
        return obj;
      }
    }
    i = (i + 1) & _alloc_region_slot_mask;
  }
  return nullptr;
}

template<ShenandoahFreeSetPartitionId PARTITION>
void ShenandoahPartitionAllocator<PARTITION>::uninstall_alloc_region(const uint32_t slot, ShenandoahHeapRegion* occupant) {
  assert(occupant != nullptr && _alloc_regions[slot].load_relaxed() == occupant, "Must be sane");
  _alloc_regions[slot].release_store(nullptr);

  bool unset = occupant->unset_active_alloc_region();
  assert(unset, "Should always succeed");
  if (PARTITION != ShenandoahFreeSetPartitionId::Mutator) {
    occupant->set_update_watermark(occupant->plain_top());
    occupant->set_gc_alloc_region(false);
  }
}

template<ShenandoahFreeSetPartitionId PARTITION>
bool ShenandoahPartitionAllocator<PARTITION>::try_install_alloc_region(const uint32_t slot,
                                                                       ShenandoahHeapRegion* occupant,
                                                                       ShenandoahHeapRegion* new_region) {
  shenandoah_assert_heaplocked();
  assert(!new_region->is_atomic_alloc_region(), "Fresh region must not already be an active alloc region");
  assert(_alloc_regions[slot].load_relaxed() == occupant, "Must be same");

  // Only install if the occupant is exhausted (or slot is empty).
  if (occupant != nullptr && (occupant->free_relaxed() >> LogHeapWordSize) >= ShenandoahHeap::plab_min_size()) {
    return false;
  }

  constexpr ShenandoahAffiliation affiliation =
    (PARTITION == ShenandoahFreeSetPartitionId::OldCollector) ? OLD_GENERATION : YOUNG_GENERATION;
  assert(new_region->affiliation() == affiliation, "Region affiliation must be established before install");
  assert(new_region->is_regular_or_regular_pinned(),
         "Region must be made regular (or left pinned) by allocate_in before install");
  size_t remnant_bytes = _free_set->retire_region(PARTITION, new_region->index(), new_region->used());
  assert(remnant_bytes == _free_set->alloc_capacity(new_region), "Sanity check");
  // gc_alloc_region flag must be visible before set_active_alloc_region publishes the region.
  if (PARTITION != ShenandoahFreeSetPartitionId::Mutator) {
    new_region->set_gc_alloc_region(true);
  }
  new_region->set_active_alloc_region();

  _alloc_regions[slot].release_store(new_region);

  if (occupant != nullptr) {
    bool unset = occupant->unset_active_alloc_region();
    assert(unset, "Should always succeed");
    if (PARTITION != ShenandoahFreeSetPartitionId::Mutator) {
      occupant->set_update_watermark(occupant->plain_top());
      occupant->set_gc_alloc_region(false);
    }
  }
  return true;
}

template<ShenandoahFreeSetPartitionId PARTITION>
HeapWord* ShenandoahPartitionAllocator<PARTITION>::allocate(ShenandoahAllocRequest& req, bool& in_new_region) {
  Thread* const thread = Thread::current();
  uint32_t const slot = alloc_region_slot(thread);

  // Fast path: lock-free CAS bump in this thread's stripe slot.
  ShenandoahHeapRegion* shared_region = _alloc_regions[slot].load_acquire();
  if (shared_region != nullptr) {
    HeapWord* obj = try_atomic_allocate_in(shared_region, req);
    if (obj != nullptr) {
      in_new_region = false;
      return obj;
    }
  }

  // Slow path
  {
    ShenandoahHeap* const heap = ShenandoahHeap::heap();
    ShenandoahHeapLocker heap_locker(heap->lock(), req.is_mutator_alloc());
    shared_region = _alloc_regions[slot].load_relaxed();
    if (shared_region != nullptr) {
      HeapWord* obj = try_atomic_allocate_in(shared_region, req);
      if (shared_region->free_relaxed() >> LogHeapWordSize < ShenandoahHeap::plab_min_size()) {
        uninstall_alloc_region(slot, shared_region);
        shared_region = nullptr;
      }
      if (obj != nullptr) {
        in_new_region = false;
        return obj;
      }
    }

    if constexpr (PARTITION == ShenandoahFreeSetPartitionId::OldCollector) {
      if (!req.is_promotion() && !heap->old_generation()->can_allocate(req)) {
        return nullptr;
      }
    }

    size_t min_req_words = req.is_lab_alloc() ? req.min_size() : req.size();
    ShenandoahHeapRegion* fresh = _free_set->find_region_for_alloc<PARTITION>(min_req_words, in_new_region);
    // Collectors try sibling slots before stealing from mutator.
    if constexpr (PARTITION != ShenandoahFreeSetPartitionId::Mutator) {
      if (fresh == nullptr) {
        if (_alloc_region_count > 1) {
          HeapWord* obj = try_allocate_in_alloc_regions<true>(req, in_new_region, slot + 1, _alloc_region_count - 1);
          if (obj != nullptr) {
            return obj;
          }
        }
        if (ShenandoahEvacReserveOverflow) {
          fresh = _free_set->steal_from_mutator(PARTITION);
          if (fresh != nullptr) {
            assert(fresh->is_empty(), "Stolen region must be empty");
            in_new_region = true;
          }
        }
      }
    }

    if (fresh != nullptr) {
      bool retired_after_alloc = false;
      HeapWord* result = allocate_in(fresh, req, retired_after_alloc);
      assert(result != nullptr, "Sanity check - allocate_in should always succeed");
      if (in_new_region) {
        _free_set->mark_region_used(PARTITION);
      }

      bool boundary_changed = in_new_region || retired_after_alloc;
      if (!retired_after_alloc && try_install_alloc_region(slot, shared_region, fresh)) {
        boundary_changed = true;
      }
      _free_set->notify_allocation(PARTITION, in_new_region, boundary_changed);
      return result;
    }

    if constexpr (PARTITION == ShenandoahFreeSetPartitionId::Mutator) {
      // Last resort: try sibling slots before giving up.
      if (_alloc_region_count > 1) {
        HeapWord* obj = try_allocate_in_alloc_regions<true>(req, in_new_region, slot + 1, _alloc_region_count - 1);
        if (obj != nullptr) {
          return obj;
        }
      }
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
    size_t free = align_down((r->free_relaxed() >> LogHeapWordSize), MinObjAlignment);
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
    r->free_relaxed(), req.is_lab_alloc() ? req.min_size() : req.size());

  if constexpr (PARTITION == ShenandoahFreeSetPartitionId::Mutator) {
    assert(req.is_young(), "Mutator allocations always come from young generation.");
    _free_set->increase_partition_used(PARTITION, req.actual_size() * HeapWordSize);
  } else {
    assert(req.is_gc_alloc(), "Should be gc_alloc since req wasn't mutator alloc");
    r->set_update_watermark(r->plain_top());
    _free_set->increase_partition_used(PARTITION, (req.actual_size() + req.waste()) * HeapWordSize);
  }

  if ((r->free_relaxed() >> LogHeapWordSize) < ShenandoahHeap::plab_min_size()) {
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
                                                                          ShenandoahAllocRequest& req) {
  size_t actual_size;
  HeapWord* obj = nullptr;
  if (req.is_lab_alloc()) {
    obj = r->allocate_lab_atomic(req, actual_size);
  } else {
    actual_size = req.size();
    obj = r->allocate_atomic(req);
  }

  if (obj != nullptr) {
    req.set_actual_size(actual_size);
  }
  return obj;
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

  uint32_t empty_slots[MAX_ALLOC_REGIONS];
  uint32_t empty_slot_count = 0;
  for (uint32_t i = 0; i < _alloc_region_count; i++) {
    if (_alloc_regions[i].load_relaxed() == nullptr) {
      empty_slots[empty_slot_count++] = i;
    }
  }
  if (empty_slot_count == 0) {
    return;
  }

  const size_t min_free_words = ShenandoahHeap::plab_min_size();
  ShenandoahHeapRegion* reserved[MAX_ALLOC_REGIONS];
  int reserved_count = _free_set->reserve_alloc_regions<PARTITION>(checked_cast<int>(empty_slot_count),
                                                                   min_free_words, reserved);
  assert(reserved_count <= checked_cast<int>(empty_slot_count), "Cannot reserve more regions than empty slots");

  for (int i = 0; i < reserved_count; i++) {
    const uint32_t slot = empty_slots[i];
    assert(_alloc_regions[slot].load_relaxed() == nullptr, "Slot must remain empty under the heap lock");
    assert(reserved[i]->is_atomic_alloc_region(), "Reserved region must be active before publication");
    _alloc_regions[slot].release_store(reserved[i]);
  }
}

template<ShenandoahFreeSetPartitionId PARTITION>
void ShenandoahPartitionAllocator<PARTITION>::release_alloc_region(uint32_t slot) {
  shenandoah_assert_heaplocked();
  ShenandoahHeapRegion* alloc_region = _alloc_regions[slot].load_relaxed();
  if (alloc_region == nullptr) {
    return;
  }

  uninstall_alloc_region(slot, alloc_region);
  if ((alloc_region->free_relaxed() >> LogHeapWordSize) >= ShenandoahHeap::plab_min_size()) {
    _free_set->unretire_alloc_region(PARTITION, alloc_region);
  }
}

template class ShenandoahPartitionAllocator<ShenandoahFreeSetPartitionId::Mutator>;
template class ShenandoahPartitionAllocator<ShenandoahFreeSetPartitionId::Collector>;
template class ShenandoahPartitionAllocator<ShenandoahFreeSetPartitionId::OldCollector>;
