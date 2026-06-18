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
#include "runtime/os.hpp"

template<ShenandoahFreeSetPartitionId PARTITION>
ShenandoahPartitionAllocator<PARTITION>::ShenandoahPartitionAllocator(ShenandoahFreeSet* free_set, uint alloc_region_count)
  : _free_set(free_set),
    _alloc_region_count(MIN2(MAX2(alloc_region_count, 1u), MAX_ALLOC_REGIONS)),
    _epoch_id(0u) {
  for (uint i = 0; i < MAX_ALLOC_REGIONS; i++) {
    _alloc_regions[i].store_relaxed(nullptr);
  }
}

template<ShenandoahFreeSetPartitionId PARTITION>
uint ShenandoahPartitionAllocator<PARTITION>::alloc_region_start_index() {
  if (_alloc_region_count <= 1u) {
    return 0u;
  }
  // Mutator and (old-)collector partitions keep separate per-thread start slots. The collector
  // and old-collector partitions share one slot since a given worker only allocates in one of them.
  uint index = (PARTITION == ShenandoahFreeSetPartitionId::Mutator)
                 ? ShenandoahThreadLocalData::mutator_alloc_region_start_index()
                 : ShenandoahThreadLocalData::collector_alloc_region_start_index();
  if (index == UINT_MAX) {
    // Assign a stable per-thread start slot. GC workers stripe by worker id; other threads by a
    // pseudo-random value. (Math/Date intrinsics are unavailable here, os::random is fine.)
    Thread* current = Thread::current();
    if (PARTITION != ShenandoahFreeSetPartitionId::Mutator && current->is_Worker_thread()) {
      index = WorkerThread::worker_id() % _alloc_region_count;
    } else {
      index = (uint)(os::random() & 0x7fffffff) % _alloc_region_count;
    }
    if (PARTITION == ShenandoahFreeSetPartitionId::Mutator) {
      ShenandoahThreadLocalData::set_mutator_alloc_region_start_index(index);
    } else {
      ShenandoahThreadLocalData::set_collector_alloc_region_start_index(index);
    }
  }
  assert(index < _alloc_region_count, "start index in range");
  return index;
}

template<ShenandoahFreeSetPartitionId PARTITION>
HeapWord* ShenandoahPartitionAllocator<PARTITION>::try_allocate_in_alloc_regions(ShenandoahAllocRequest& req,
                                                                                 bool& in_new_region,
                                                                                 uint start_index,
                                                                                 uint& ready_for_replenish) {
  ready_for_replenish = 0u;
  uint i = start_index;
  do {
    ShenandoahHeapRegion* r = _alloc_regions[i].load_acquire();
    if (r != nullptr) {
      bool slot_ready = false;
      HeapWord* obj = try_atomic_allocate_in(i, r, req, slot_ready);
      if (obj != nullptr) {
        in_new_region = false;
        return obj;
      }
      if (slot_ready) {
        ready_for_replenish++;
      }
    } else {
      // An empty slot is always a candidate for replenishment.
      ready_for_replenish++;
    }
    if (++i == _alloc_region_count) {
      i = 0u;
    }
  } while (i != start_index);
  return nullptr;
}

// Publish a region that reserve_alloc_regions already prepared (made regular, retired from its
// partition, and activated as a CAS alloc region with the reserved flag set) into an empty stripe
// slot. Only stores the pointer; does NOT touch region state or free-set accounting. Heap lock held.
template<ShenandoahFreeSetPartitionId PARTITION>
void ShenandoahPartitionAllocator<PARTITION>::publish_alloc_region(uint index, ShenandoahHeapRegion* r) {
  shenandoah_assert_heaplocked();
  assert(_alloc_regions[index].load_relaxed() == nullptr, "Slot must be empty before publish");
  assert(r->is_atomic_alloc_region(), "Region must already be an active alloc region");
  _alloc_regions[index].release_store(r);
}

template<ShenandoahFreeSetPartitionId PARTITION>
void ShenandoahPartitionAllocator<PARTITION>::install_alloc_region(uint index, ShenandoahHeapRegion* r) {
  shenandoah_assert_heaplocked();
  assert(_alloc_regions[index].load_relaxed() == nullptr, "Slot must be empty before install");
  // Transition the region to the regular-allocation state before publishing it. The lock-free CAS
  // path (allocate_atomic / allocate_lab_atomic) only bumps _atomic_top and never changes region
  // state, so a freshly reserved empty region must be made regular here, while we hold the heap
  // lock, or it would receive objects while still in an empty state. make_regular_allocation is
  // idempotent (a region already _regular stays _regular) and requires affiliation to be set,
  // which find_region_for_alloc / steal_from_mutator guarantee.
  constexpr ShenandoahAffiliation affiliation =
    (PARTITION == ShenandoahFreeSetPartitionId::OldCollector) ? OLD_GENERATION : YOUNG_GENERATION;
  assert(r->affiliation() == affiliation, "Region affiliation must be established before install");
  r->make_regular_allocation(affiliation);
  size_t remnant_bytes = _free_set->retire_region(PARTITION, r->index(), r->used());
  assert(remnant_bytes == _free_set->alloc_capacity(r), "Sanity check");
  // The flag must be set BEFORE the region becomes an active alloc region, so any
  // thread that can observe the region via _atomic_top also observes the flag as true.
  if (PARTITION != ShenandoahFreeSetPartitionId::Mutator) {
    r->set_collector_allocator_reserved(true);
  }
  r->set_active_alloc_region();
  _alloc_regions[index].release_store(r);
}

template<ShenandoahFreeSetPartitionId PARTITION>
HeapWord* ShenandoahPartitionAllocator<PARTITION>::allocate(ShenandoahAllocRequest& req, bool& in_new_region) {
  uint start_index = alloc_region_start_index();
  uint ready_for_replenish = 0u;
  uint32_t old_epoch_id = _epoch_id.load_acquire();

  // Fast path: lock-free CAS allocation across the stripe slots.
  HeapWord* obj = try_allocate_in_alloc_regions(req, in_new_region, start_index, ready_for_replenish);
  if (obj != nullptr) {
    return obj;
  }

  // Slow-path with heap lock
  {
    // Mutator allocations may yield to safepoint; GC allocations cannot
    ShenandoahHeapLocker locker(ShenandoahHeap::heap()->lock(), req.is_mutator_alloc());

    // If the stripe slots were replenished by another thread while we waited for the lock, the
    // epoch changed; retry the fast path against the fresh slots before reserving anything.
    if (old_epoch_id != _epoch_id.load_acquire()) {
      ready_for_replenish = 0u;
      obj = try_allocate_in_alloc_regions(req, in_new_region, start_index, ready_for_replenish);
      if (obj != nullptr) {
        return obj;
      }
    }

    // OldCollector: verify old generation has room before attempting allocation
    if constexpr (PARTITION == ShenandoahFreeSetPartitionId::OldCollector) {
      if (!req.is_promotion() && !ShenandoahHeap::heap()->old_generation()->can_allocate(req)) {
        return nullptr;
      }
    }

    // Eagerly replenish when at least half the stripe slots are empty or ready to retire. This
    // refills several slots in one locked visit, amortizing the lock cost under contention; we
    // then retry the lock-free fast path against the freshly installed slots.
    if (ready_for_replenish * 2 >= _alloc_region_count) {
      int replenished = replenish_alloc_regions(req, in_new_region, nullptr);
      if (replenished > 0) {
        obj = try_allocate_in_alloc_regions(req, in_new_region, start_index, ready_for_replenish);
        if (obj != nullptr) {
          return obj;
        }
      }
    }

    bool boundary_changed = false;
    size_t min_req_words = req.is_lab_alloc() ? req.min_size() : req.size();
    // Ask FreeSet to find a suitable region
    ShenandoahHeapRegion* r = _free_set->find_region_for_alloc<PARTITION>(min_req_words, in_new_region);
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
      // If the region still has usable capacity, install it into an empty stripe slot (if any) as
      // an active alloc region so subsequent allocations can use the lock-free fast path.
      if (_free_set->alloc_capacity(r) >> LogHeapWordSize >= PLAB::min_size() &&
          _alloc_regions[start_index].load_acquire() == nullptr) {
        install_alloc_region(start_index, r);
        _epoch_id.fetch_then_add(1u, memory_order_release);
        boundary_changed = true;
      }
      _free_set->notify_allocation(PARTITION, in_new_region, boundary_changed);
      return result;
    }

    return nullptr;
  }
}

// Eagerly retire empty/ready stripe slots and refill them with fresh regions from the free set.
// Heap lock held. Pure refill: this installs fresh alloc regions but does NOT itself satisfy req;
// the caller retries the lock-free fast path against the freshly installed slots. The `req`/`obj`
// parameters are reserved for a future satisfy-first optimization and currently unused beyond the
// request size used to size the reserved regions. Returns the number of slots refilled and bumps
// _epoch_id when > 0.
template<ShenandoahFreeSetPartitionId PARTITION>
int ShenandoahPartitionAllocator<PARTITION>::replenish_alloc_regions(ShenandoahAllocRequest& req,
                                                                     bool& in_new_region,
                                                                     HeapWord** obj) {
  shenandoah_assert_heaplocked();
  size_t min_req_words = req.is_lab_alloc() ? req.min_size() : req.size();

  // Collect the empty/ready slots that need a fresh region, retiring their stale regions first.
  uint empty_slots[MAX_ALLOC_REGIONS];
  uint empty_slot_count = 0;
  for (uint i = 0; i < _alloc_region_count; i++) {
    ShenandoahHeapRegion* current = _alloc_regions[i].load_acquire();
    if (current != nullptr && _free_set->alloc_capacity(current) >> LogHeapWordSize >= PLAB::min_size()) {
      // Slot still has usable capacity; leave it.
      continue;
    }
    release_alloc_region(i);
    assert(_alloc_regions[i].load_relaxed() == nullptr, "Slot must be empty after release");
    empty_slots[empty_slot_count++] = i;
  }
  if (empty_slot_count == 0) {
    return 0;
  }

  // Reserve a batch of fresh regions in one freeset pass (single deferred accounting recompute).
  ShenandoahHeapRegion* reserved[MAX_ALLOC_REGIONS];
  int reserved_count = _free_set->reserve_alloc_regions<PARTITION>(req, (int) empty_slot_count,
                                                                   min_req_words, reserved);
  assert(reserved_count <= (int) empty_slot_count, "Sanity check");

  // Publish each reserved region into an empty slot. The regions are already retired from the
  // partition and made regular by reserve_alloc_regions, so we only flip them to active here.
  for (int i = 0; i < reserved_count; i++) {
    publish_alloc_region(empty_slots[i], reserved[i]);
  }

  if (reserved_count > 0) {
    _epoch_id.fetch_then_add(1u, memory_order_release);
  }
  return reserved_count;
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
HeapWord* ShenandoahPartitionAllocator<PARTITION>::try_atomic_allocate_in(uint index, ShenandoahHeapRegion* r,
                                                                          ShenandoahAllocRequest& req,
                                                                          bool& ready_for_replenish) {
  size_t actual_size;
  ready_for_replenish = false;
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
      // Claim the slot for retirement with a CAS on its stripe entry. Multiple threads may
      // observe ready_for_replenish concurrently on this lock-free path, but only the thread
      // that wins this CAS may retire it: unset_active_alloc_region() is not safe to run
      // concurrently on the same region (two unsetters can clobber each other's _top sync).
      if (_alloc_regions[index].compare_exchange(r, (ShenandoahHeapRegion*) nullptr) == r) {
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
  } else {
    // Could not allocate from this region. Report the slot as ready for replenish if the region
    // no longer has room for a minimum LAB (it is effectively full and should be retired/refilled).
    ready_for_replenish = (r->free() >> LogHeapWordSize) < PLAB::min_size();
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
void ShenandoahPartitionAllocator<PARTITION>::release_alloc_region(uint index) {
  shenandoah_assert_heaplocked();
  ShenandoahHeapRegion* alloc_region = _alloc_regions[index].load_acquire();
  if (alloc_region == nullptr) {
    return;
  }
  // Claim the region for retirement with a CAS. Even while holding the heap lock, the
  // lock-free fast path (try_atomic_allocate_in) may concurrently retire the cached region;
  // only the thread that wins this CAS may call unset_active_alloc_region(), which is not
  // safe to run concurrently on the same region.
  if (_alloc_regions[index].compare_exchange(alloc_region, (ShenandoahHeapRegion*) nullptr) != alloc_region) {
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
