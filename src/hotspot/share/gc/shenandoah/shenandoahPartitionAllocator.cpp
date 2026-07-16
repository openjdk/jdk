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
ShenandoahPartitionAllocator<PARTITION>::ShenandoahPartitionAllocator(ShenandoahFreeSet* free_set, uint alloc_region_count)
  : _free_set(free_set),
    _alloc_region_count(MIN2(MAX2(alloc_region_count, 1u), MAX_ALLOC_REGIONS)) {
  for (uint i = 0; i < MAX_ALLOC_REGIONS; i++) {
    _alloc_regions[i].store_relaxed(nullptr);
  }
}

template<ShenandoahFreeSetPartitionId PARTITION>
uint ShenandoahPartitionAllocator<PARTITION>::alloc_region_slot(Thread* thread) {
  if (_alloc_region_count <= 1u) {
    return 0u;
  }

  if constexpr (PARTITION != ShenandoahFreeSetPartitionId::Mutator) {
    // Returns the current task-local worker ID, or UINT_MAX if this thread
    // has never been assigned a worker task.
    const uint worker_id = WorkerThread::worker_id();
    if (worker_id != UINT_MAX) {
      return worker_id % _alloc_region_count;
    }
  }

  // Mutators and rare non-worker collector allocations share one stable raw per-thread ticket.
  // Reduce it here rather than caching a consumer-specific slot so other striped structures can
  // independently map the same ticket into differently-sized arrays.
  const uint slot = ShenandoahThreadLocalData::round_robin_probe(thread) % _alloc_region_count;
  assert(slot < _alloc_region_count, "slot in range");
  return slot;
}

template<ShenandoahFreeSetPartitionId PARTITION>
HeapWord* ShenandoahPartitionAllocator<PARTITION>::try_allocate_in_alloc_regions(ShenandoahAllocRequest& req,
                                                                                 bool& in_new_region,
                                                                                 uint start_index,
                                                                                 uint end_index) {
  assert(end_index < _alloc_region_count, "Must be");
  uint i = start_index;
  while (i != end_index) {
    ShenandoahHeapRegion* r = _alloc_regions[i].load_acquire();
    if (r != nullptr) {
      HeapWord* obj = try_atomic_allocate_in(r, req);
      if (obj != nullptr) {
        in_new_region = false;
        return obj;
      }
    }
    if (++i == _alloc_region_count) {
      i = 0u;
    }
  }
  return nullptr;
}

template<ShenandoahFreeSetPartitionId PARTITION>
bool ShenandoahPartitionAllocator<PARTITION>::try_install_alloc_region(uint index,
                                                                       ShenandoahHeapRegion* occupant,
                                                                       ShenandoahHeapRegion* new_region) {
  shenandoah_assert_heaplocked();
  assert(!new_region->is_atomic_alloc_region(), "Fresh region must not already be an active alloc region");
  assert(occupant == nullptr || occupant == _alloc_regions[index].load_acquire() ||
         _alloc_regions[index].load_acquire() == nullptr,
         "Under the lock the slot can only change from occupant to nullptr (fast-path retire)");

  // Replace the slot only when new_region is the better region to cache, i.e. it has strictly more
  // remaining capacity than the occupant (an empty slot always installs).
  if (occupant != nullptr && occupant->free() >> LogHeapWordSize >= ShenandoahHeap::plab_min_size()) {
    return false;
  }

  constexpr ShenandoahAffiliation affiliation =
    (PARTITION == ShenandoahFreeSetPartitionId::OldCollector) ? OLD_GENERATION : YOUNG_GENERATION;
  assert(new_region->affiliation() == affiliation, "Region affiliation must be established before install");
  assert(new_region->is_regular_or_regular_pinned(),
         "Region must be made regular (or left pinned) by allocate_in before install");
  size_t remnant_bytes = _free_set->retire_region(PARTITION, new_region->index(), new_region->used());
  assert(remnant_bytes == _free_set->alloc_capacity(new_region), "Sanity check");
  // The flag must be set BEFORE the region becomes an active alloc region, so any thread that can
  // observe the region via _atomic_top also observes the flag as true.
  if (PARTITION != ShenandoahFreeSetPartitionId::Mutator) {
    new_region->set_collector_allocator_reserved(true);
  }
  new_region->set_active_alloc_region();

  _alloc_regions[index].release_store(new_region);

  if (occupant != nullptr) {
    bool unset = occupant->unset_active_alloc_region();
    assert(unset, "Should always succeed");
    if (PARTITION != ShenandoahFreeSetPartitionId::Mutator) {
      occupant->set_update_watermark(occupant->stable_top());
      occupant->set_collector_allocator_reserved(false);
    }
  }
  return true;
}

template<ShenandoahFreeSetPartitionId PARTITION>
HeapWord* ShenandoahPartitionAllocator<PARTITION>::allocate(ShenandoahAllocRequest& req, bool& in_new_region) {
  // Resolve the current thread once and pass it to alloc_region_slot() instead of having that
  // helper call Thread::current() again on the hot path.
  Thread* const thread = Thread::current();
  uint const slot = alloc_region_slot(thread);

  // Fast path: lock-free CAS allocation in THIS thread's stripe slot only (no cross-stripe scan).
  // If the attempt fails but the slot has meanwhile been replaced with a DIFFERENT region (e.g. a
  // slow-path thread installed a fresh region with more capacity), retry the lock-free path against
  // the new region before falling back to the locked slow path. We stop retrying once the slot is
  // unchanged (same region we just failed on, whose free only shrinks, so a retry would fail again)
  // or empty. This cannot livelock: a retry requires the slot to have changed to a new non-null
  // region, and slots are only filled under the heap lock, so retries are rate-limited by other
  // threads' (rare) slow-path installs.
  ShenandoahHeapRegion* shared_region = _alloc_regions[slot].load_acquire();
  if (shared_region != nullptr) {
    HeapWord* obj = try_atomic_allocate_in(shared_region, req);
    if (obj != nullptr) {
      in_new_region = false;
      return obj;
    }
  }
  // Slow-path
  {
    ShenandoahHeap* const heap = ShenandoahHeap::heap();
    ShenandoahHeapLocker locker(heap->lock(), req.is_mutator_alloc());
    ShenandoahHeapRegion* const reloaded = _alloc_regions[slot].load_relaxed();
    if (reloaded != nullptr && reloaded != shared_region) {
      HeapWord* obj = try_atomic_allocate_in(reloaded, req);
      if (obj != nullptr) {
        in_new_region = false;
        return obj;
      }
    }
    shared_region = reloaded;

    // OldCollector: verify old generation has room before attempting allocation
    if constexpr (PARTITION == ShenandoahFreeSetPartitionId::OldCollector) {
      if (!req.is_promotion() && !heap->old_generation()->can_allocate(req)) {
        return nullptr;
      }
    }

    size_t min_req_words = req.is_lab_alloc() ? req.min_size() : req.size();
    // Take a fresh region from the free set, allocate from it, then install it into our slot for
    // subsequent lock-free use (retiring whatever now-full region was there).
    ShenandoahHeapRegion* fresh = _free_set->find_region_for_alloc<PARTITION>(min_req_words, in_new_region);
    // Collector partitions, when the free set has no region of their own to hand out, try the
    // sibling stripe slots BEFORE stealing a region from the mutator.
    if constexpr (PARTITION != ShenandoahFreeSetPartitionId::Mutator) {
      if (fresh == nullptr) {
        if (_alloc_region_count > 1) {
          HeapWord* obj = try_allocate_in_alloc_regions(req, in_new_region, (slot + 1) % _alloc_region_count, slot);
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
      bool boundary_changed = false;
      HeapWord* result = allocate_in(fresh, req, boundary_changed);
      assert(result != nullptr, "Sanity check - allocate_in should always succeed");
      if (in_new_region) {
        _free_set->mark_region_used(PARTITION);
        boundary_changed = true;
      }
      // If the region still has usable capacity, try to install it into our stripe slot as an active
      // alloc region so subsequent allocations use the lock-free fast path. try_install_alloc_region
      // installs only when fresh is the better region to cache (slot empty, or fresh has more room
      // than the occupant); otherwise fresh simply remains an ordinary free-set member (already
      // accounted by allocate_in). Either way the partition boundary moved.
      if (_free_set->alloc_capacity(fresh) >> LogHeapWordSize >= ShenandoahHeap::plab_min_size()) {
        // shared_region is the slot value loaded above under the lock; find_region_for_alloc does
        // not touch the slots, so it is still a valid CAS expected value for the install.
        if (try_install_alloc_region(slot, shared_region, fresh)) {
          boundary_changed = true;
        }
      }
      _free_set->notify_allocation(PARTITION, in_new_region, boundary_changed);
      return result;
    }

    if constexpr (PARTITION == ShenandoahFreeSetPartitionId::Mutator) {
      // Free set is exhausted. LAST RESORT: a sibling stripe slot may still have room even though the
      // free set has no region to hand out. Scan all slots under the lock before giving up; this is
      // what keeps a full own-slot from causing a spurious allocation failure.
      if (_alloc_region_count > 1) {
        HeapWord* obj = try_allocate_in_alloc_regions(req, in_new_region, (slot + 1) % _alloc_region_count, slot);
        if (obj != nullptr) {
          return obj;
        }
      }
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
  if ((r->free() >> LogHeapWordSize) < ShenandoahHeap::plab_min_size()) {
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
  for (uint i = 0; i < _alloc_region_count; i++) {
    release_alloc_region(i);
  }
}

template<ShenandoahFreeSetPartitionId PARTITION>
void ShenandoahPartitionAllocator<PARTITION>::reserve_alloc_regions() {
  shenandoah_assert_heaplocked();

  uint empty_slots[MAX_ALLOC_REGIONS];
  uint empty_slot_count = 0;
  for (uint i = 0; i < _alloc_region_count; i++) {
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

  // reserve_alloc_regions() has already prepared, retired, and activated each region. Publish the
  // pointers only after the batch accounting reconciliation has completed.
  for (int i = 0; i < reserved_count; i++) {
    const uint slot = empty_slots[i];
    assert(_alloc_regions[slot].load_relaxed() == nullptr, "Slot must remain empty under the heap lock");
    assert(reserved[i]->is_atomic_alloc_region(), "Reserved region must be active before publication");
    _alloc_regions[slot].release_store(reserved[i]);
  }
}

template<ShenandoahFreeSetPartitionId PARTITION>
void ShenandoahPartitionAllocator<PARTITION>::set_alloc_region_count(uint count) {
  shenandoah_assert_heaplocked_or_safepoint();
  count = MIN2(MAX2(count, 1u), MAX_ALLOC_REGIONS);
#ifdef ASSERT
  // Lowering the count would strand any occupied slot at index >= count (never scanned, never
  // retired), so all slots must be released before we resize. Callers guarantee this.
  for (uint i = 0; i < MAX_ALLOC_REGIONS; i++) {
    assert(_alloc_regions[i].load_relaxed() == nullptr, "All slots must be released before resizing");
  }
#endif
  _alloc_region_count = count;
}

template<ShenandoahFreeSetPartitionId PARTITION>
void ShenandoahPartitionAllocator<PARTITION>::grow_alloc_region_count(uint count) {
  shenandoah_assert_heaplocked_or_safepoint();
  const uint new_count = MAX2(_alloc_region_count, MIN2(count, MAX_ALLOC_REGIONS));
#ifdef ASSERT
  // The slots being newly exposed (index in [_alloc_region_count, new_count)) must be empty: they
  // were outside the active range, so no allocation could have installed a region there. A non-null
  // slot here would mean a region was stranded above the old count and is about to become reachable
  // with stale accounting.
  for (uint i = _alloc_region_count; i < new_count; i++) {
    assert(_alloc_regions[i].load_relaxed() == nullptr, "Newly exposed slot %u must be empty", i);
  }
#endif
  // Grow only: existing occupied slots keep serving; only higher-indexed slots become newly
  // reachable. Never lower the count here, which would strand an occupied slot.
  _alloc_region_count = new_count;
}

template<ShenandoahFreeSetPartitionId PARTITION>
void ShenandoahPartitionAllocator<PARTITION>::release_alloc_region(uint slot) {
  shenandoah_assert_heaplocked();
  ShenandoahHeapRegion* alloc_region = _alloc_regions[slot].load_acquire();
  if (alloc_region == nullptr) {
    return;
  }
  _alloc_regions[slot].release_store(nullptr);

  bool unset = alloc_region->unset_active_alloc_region();
  assert(unset, "Should always succeed");
  if (PARTITION != ShenandoahFreeSetPartitionId::Mutator) {
    // Cover the objects evacuated into this region so update-refs processes them, then
    // clear the reserved flag so the barrier stops forcing bulk updates over the region.
    alloc_region->set_update_watermark(alloc_region->stable_top());
    alloc_region->set_collector_allocator_reserved(false);
  }
  if (alloc_region->free() >> LogHeapWordSize >= ShenandoahHeap::plab_min_size()) {
    // Region is still allocatable: return its unconsumed remnant to the partition and
    // make it a free-set member again.
    _free_set->unretire_alloc_region(PARTITION, alloc_region);
  }
}

// Explicit template instantiations for all partitions.
template class ShenandoahPartitionAllocator<ShenandoahFreeSetPartitionId::Mutator>;
template class ShenandoahPartitionAllocator<ShenandoahFreeSetPartitionId::Collector>;
template class ShenandoahPartitionAllocator<ShenandoahFreeSetPartitionId::OldCollector>;
