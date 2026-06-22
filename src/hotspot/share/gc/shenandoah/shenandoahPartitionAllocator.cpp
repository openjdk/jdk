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
    _alloc_region_count(MIN2(MAX2(alloc_region_count, 1u), MAX_ALLOC_REGIONS)) {
  for (uint i = 0; i < MAX_ALLOC_REGIONS; i++) {
    _alloc_regions[i].store_relaxed(nullptr);
  }
}

template<ShenandoahFreeSetPartitionId PARTITION>
uint ShenandoahPartitionAllocator<PARTITION>::alloc_region_start_index(Thread* thread) {
  if (_alloc_region_count <= 1u) {
    return 0u;
  }
  // Mutator and (old-)collector partitions keep separate per-thread start slots. The collector
  // and old-collector partitions share one slot since a given worker only allocates in one of them.
  uint index = (PARTITION == ShenandoahFreeSetPartitionId::Mutator)
                 ? ShenandoahThreadLocalData::mutator_alloc_region_start_index(thread)
                 : ShenandoahThreadLocalData::collector_alloc_region_start_index(thread);
  if (index == UINT_MAX) {
    // Assign a stable per-thread start slot. GC workers stripe by worker id; other threads by a
    // pseudo-random value. (Math/Date intrinsics are unavailable here, os::random is fine.)
    if (PARTITION != ShenandoahFreeSetPartitionId::Mutator && thread->is_Worker_thread()) {
      index = WorkerThread::worker_id() % _alloc_region_count;
    } else {
      index = (uint)(os::random() & 0x7fffffff) % _alloc_region_count;
    }
    if (PARTITION == ShenandoahFreeSetPartitionId::Mutator) {
      ShenandoahThreadLocalData::set_mutator_alloc_region_start_index(thread, index);
    } else {
      ShenandoahThreadLocalData::set_collector_alloc_region_start_index(thread, index);
    }
  }
  assert(index < _alloc_region_count, "start index in range");
  return index;
}

template<ShenandoahFreeSetPartitionId PARTITION>
HeapWord* ShenandoahPartitionAllocator<PARTITION>::try_allocate_in_alloc_regions(ShenandoahAllocRequest& req,
                                                                                 bool& in_new_region,
                                                                                 uint start_index) {
  uint i = start_index;
  do {
    ShenandoahHeapRegion* r = _alloc_regions[i].load_acquire();
    if (r != nullptr) {
      HeapWord* obj = try_atomic_allocate_in(i, r, req);
      if (obj != nullptr) {
        in_new_region = false;
        return obj;
      }
    }
    if (++i == _alloc_region_count) {
      i = 0u;
    }
  } while (i != start_index);
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
  // remaining capacity than the occupant (an empty slot always installs). new_region has already
  // served the current request (allocate_in ran before this), so new_region->free() is its post-
  // allocation remnant; the caller only calls us when that remnant is still >= PLAB::min_size, so we
  // never cache a near-empty region. Keeping the larger region maximizes future fast-path hits and
  // un-wedges a slot stuck on a low-capacity region that keeps failing larger requests. Conversely,
  // when the occupant still has more room than new_region, we leave it -- evicting it would strand
  // its larger remnant in the free set (reachable only via the slow path).
  //
  // occupant->free() is read while the occupant is still an active alloc region, so it is a
  // concurrent snapshot that may only grow until the region is retired; an occasionally-stale value
  // only affects which region we keep (a heuristic), never correctness.
  if (occupant != nullptr && occupant->free() >= new_region->free()) {
    return false;
  }

  // Prepare new_region as an active alloc region. Transition it to the regular-allocation state
  // before publishing: the lock-free CAS path (allocate_atomic / allocate_lab_atomic) only bumps
  // _atomic_top and never changes region state, so a freshly reserved region must be made regular
  // here, under the heap lock, or it would receive objects while still in an empty state.
  // make_regular_allocation is idempotent and requires affiliation to be set, which
  // find_region_for_alloc / steal_from_mutator guarantee.
  constexpr ShenandoahAffiliation affiliation =
    (PARTITION == ShenandoahFreeSetPartitionId::OldCollector) ? OLD_GENERATION : YOUNG_GENERATION;
  assert(new_region->affiliation() == affiliation, "Region affiliation must be established before install");
  new_region->make_regular_allocation(affiliation);
  size_t remnant_bytes = _free_set->retire_region(PARTITION, new_region->index(), new_region->used());
  assert(remnant_bytes == _free_set->alloc_capacity(new_region), "Sanity check");
  // The flag must be set BEFORE the region becomes an active alloc region, so any thread that can
  // observe the region via _atomic_top also observes the flag as true.
  if (PARTITION != ShenandoahFreeSetPartitionId::Mutator) {
    new_region->set_collector_allocator_reserved(true);
  }
  new_region->set_active_alloc_region();

  // Publish new_region into the slot. We hold the heap lock, so the ONLY concurrent mutation
  // possible is the lock-free fast path retiring a full occupant (occupant -> nullptr); it never
  // writes a non-null value, and every other writer holds the lock we own. So the slot is either
  // still `occupant` or has become nullptr.
  if (_alloc_regions[index].compare_exchange(occupant, new_region) == occupant) {
    // Won against the observed occupant, atomically claiming it for retirement. If it was a live
    // (full) region, we now exclusively own it: only this thread may run unset_active_alloc_region()
    // on it (the fast path's competing retire CAS, if any, failed).
    if (occupant != nullptr) {
      bool unset = occupant->unset_active_alloc_region();
      assert(unset, "Winner of the slot CAS is the unique retirer of the displaced region");
      if (PARTITION != ShenandoahFreeSetPartitionId::Mutator) {
        occupant->set_update_watermark(occupant->stable_top());
        occupant->set_collector_allocator_reserved(false);
      }
      if (occupant->free() >> LogHeapWordSize >= PLAB::min_size()) {
        _free_set->unretire_alloc_region(PARTITION, occupant);
      }
    }
    return true;
  }

  // The CAS failed, so the fast path retired the full occupant to nullptr between our load and now.
  // (An empty slot, occupant == nullptr, can never be concurrently mutated to non-null, so the CAS
  // above would have won.) The fast-path winner already deactivated the occupant. The slot is now
  // stable at nullptr -- only an installer writes a non-null value and only one installer runs at a
  // time under the heap lock, which we hold -- so we can simply publish new_region with a plain
  // release store; no further CAS is needed.
  assert(occupant != nullptr, "An empty slot cannot be concurrently mutated; the first CAS must win");
  assert(_alloc_regions[index].load_relaxed() == nullptr, "Slot must be stable at nullptr under the heap lock");
  _alloc_regions[index].release_store(new_region);
  return true;
}

template<ShenandoahFreeSetPartitionId PARTITION>
HeapWord* ShenandoahPartitionAllocator<PARTITION>::allocate(ShenandoahAllocRequest& req, bool& in_new_region) {
  // Resolve the current thread once and pass it to alloc_region_start_index() instead of having that
  // helper call Thread::current() again on the hot path.
  Thread* const thread = Thread::current();
  uint start_index = alloc_region_start_index(thread);

  // Fast path: lock-free CAS allocation in THIS thread's stripe slot only (no cross-stripe scan).
  ShenandoahHeapRegion* shared_region = _alloc_regions[start_index].load_acquire();
  if (shared_region != nullptr) {
    HeapWord* obj = try_atomic_allocate_in(start_index, shared_region, req);
    if (obj != nullptr) {
      in_new_region = false;
      return obj;
    }
  }

  // Slow-path with heap lock
  {
    // Mutator allocations may yield to safepoint; GC allocations cannot
    ShenandoahHeapLocker locker(ShenandoahHeap::heap()->lock(), req.is_mutator_alloc());

    // Another thread may have installed a fresh region into our slot while we waited for the lock;
    // retry the lock-free probe of our own slot before taking a new region from the free set.
    shared_region = _alloc_regions[start_index].load_acquire();
    if (shared_region != nullptr) {
      HeapWord* obj = try_atomic_allocate_in(start_index, shared_region, req);
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
    // Take a fresh region from the free set, allocate from it, then install it into our slot for
    // subsequent lock-free use (retiring whatever now-full region was there).
    ShenandoahHeapRegion* fresh = _free_set->find_region_for_alloc<PARTITION>(min_req_words, in_new_region);
    // Collector partitions can overflow into Mutator partition
    if constexpr (PARTITION != ShenandoahFreeSetPartitionId::Mutator) {
      if (fresh == nullptr && ShenandoahEvacReserveOverflow) {
        fresh = _free_set->steal_from_mutator(PARTITION, req);
        if (fresh != nullptr) {
          assert(fresh->is_empty(), "Stolen region must be empty");
          in_new_region = true;
        }
      }
    }

    if (fresh != nullptr) {
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
      if (_free_set->alloc_capacity(fresh) >> LogHeapWordSize >= PLAB::min_size()) {
        // shared_region is the slot value loaded above under the lock; find_region_for_alloc does
        // not touch the slots, so it is still a valid CAS expected value for the install.
        try_install_alloc_region(start_index, shared_region, fresh);
        boundary_changed = true;
      }
      _free_set->notify_allocation(PARTITION, in_new_region, boundary_changed);
      return result;
    }

    // Free set is exhausted. LAST RESORT: a sibling stripe slot may still have room even though the
    // free set has no region to hand out. Scan all slots under the lock before giving up; this is
    // what keeps a full own-slot from causing a spurious allocation failure.
    HeapWord* obj = try_allocate_in_alloc_regions(req, in_new_region, start_index);
    if (obj != nullptr) {
      return obj;
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
HeapWord* ShenandoahPartitionAllocator<PARTITION>::try_atomic_allocate_in(uint index, ShenandoahHeapRegion* r,
                                                                          ShenandoahAllocRequest& req) {
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
  }

  if (ready_for_replenish) {
    // The region is now (nearly) full. Claim the slot for retirement with a CAS on its stripe
    // entry. Multiple threads may observe ready_for_replenish concurrently on this lock-free path,
    // but only the thread that wins this CAS may retire it: unset_active_alloc_region() is not
    // safe to run concurrently on the same region (two unsetters can clobber each other's _top
    // sync). The slot is left empty; the next allocator on this stripe takes the slow path and
    // installs a fresh region.
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
