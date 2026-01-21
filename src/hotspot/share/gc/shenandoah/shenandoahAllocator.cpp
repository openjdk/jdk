/*
* Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shenandoah/shenandoahAllocator.hpp"
#include "gc/shenandoah/shenandoahAllocRequest.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "memory/padded.inline.hpp"
#include "runtime/atomicAccess.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "utilities/globalDefinitions.hpp"

template <ShenandoahFreeSetPartitionId ALLOC_PARTITION>
ShenandoahAllocator<ALLOC_PARTITION>::ShenandoahAllocator(uint const alloc_region_count, ShenandoahFreeSet* free_set, bool yield_to_safepoint):
  _free_set(free_set), _alloc_partition_name(ShenandoahRegionPartitions::partition_name(ALLOC_PARTITION)), _alloc_region_count(alloc_region_count), _yield_to_safepoint(yield_to_safepoint) {
  if (alloc_region_count > 0) {
    for (uint i = 0; i < alloc_region_count; i++) {
      _alloc_regions[i].address = nullptr;
      _alloc_regions[i].alloc_region_index = i;
    }
  }
}

class ShenandoahHeapAccountingUpdater : StackObj {
public:
  ShenandoahFreeSet* _free_set;
  ShenandoahFreeSetPartitionId _partition;
  bool _need_update = false;

  ShenandoahHeapAccountingUpdater(ShenandoahFreeSet* free_set, ShenandoahFreeSetPartitionId partition): _free_set(free_set), _partition(partition) { }

  ~ShenandoahHeapAccountingUpdater() {
    if (_need_update) {
      switch (_partition) {
        case ShenandoahFreeSetPartitionId::Mutator:
          _free_set->recompute_total_used</* UsedByMutatorChanged */ true,
                                          /* UsedByCollectorChanged */ true, /* UsedByOldCollectorChanged */ true>();
          _free_set->recompute_total_affiliated</* MutatorEmptiesChanged */ true, /* CollectorEmptiesChanged */ false,
                                                /* OldCollectorEmptiesChanged */ false, /* MutatorSizeChanged */ false,
                                                /* CollectorSizeChanged */ false, /* OldCollectorSizeChanged */ false,
                                                /* AffiliatedChangesAreYoungNeutral */ false, /* AffiliatedChangesAreGlobalNeutral */ false,
                                                /* UnaffiliatedChangesAreYoungNeutral */ false>();

          break;
        case ShenandoahFreeSetPartitionId::Collector:
          _free_set->recompute_total_used</* UsedByMutatorChanged */ true,
                                          /* UsedByCollectorChanged */ true, /* UsedByOldCollectorChanged */ true>();
          _free_set->recompute_total_affiliated</* MutatorEmptiesChanged */ true, /* CollectorEmptiesChanged */ true,
                                                /* OldCollectorEmptiesChanged */ false, /* MutatorSizeChanged */ true,
                                                /* CollectorSizeChanged */ true, /* OldCollectorSizeChanged */ false,
                                                /* AffiliatedChangesAreYoungNeutral */ false, /* AffiliatedChangesAreGlobalNeutral */ false,
                                                /* UnaffiliatedChangesAreYoungNeutral */ false>();
          break;
        case ShenandoahFreeSetPartitionId::OldCollector:
          _free_set->recompute_total_used</* UsedByMutatorChanged */ true,
                                          /* UsedByCollectorChanged */ true, /* UsedByOldCollectorChanged */ true>();
          _free_set->recompute_total_affiliated</* MutatorEmptiesChanged */ true, /* CollectorEmptiesChanged */ false,
                                                /* OldCollectorEmptiesChanged */ true, /* MutatorSizeChanged */ true,
                                                /* CollectorSizeChanged */ false, /* OldCollectorSizeChanged */ true,
                                                /* AffiliatedChangesAreYoungNeutral */ false, /* AffiliatedChangesAreGlobalNeutral */ false,
                                                /* UnaffiliatedChangesAreYoungNeutral */ false>();
          break;
        case ShenandoahFreeSetPartitionId::NotFree:
        default:
          assert(false, "won't happen");
      }
    }
  }
};

template <ShenandoahFreeSetPartitionId ALLOC_PARTITION>
uint ShenandoahAllocator<ALLOC_PARTITION>::alloc_start_index() {
  uint alloc_start_index = 0u;
  switch (ALLOC_PARTITION) {
    case ShenandoahFreeSetPartitionId::Mutator:
      alloc_start_index = ShenandoahThreadLocalData::mutator_allocator_start_index();
      break;
    case ShenandoahFreeSetPartitionId::Collector:
      alloc_start_index = ShenandoahThreadLocalData::collector_allocator_start_index();
      break;
    default:
      break;
  }
  if (alloc_start_index == UINT_MAX) {
    if (_alloc_region_count <= 1u) {
      alloc_start_index = 0u;
    } else {
      if (ALLOC_PARTITION == ShenandoahFreeSetPartitionId::Mutator) {
        alloc_start_index = abs(os::random()) % _alloc_region_count;
      } else {
        alloc_start_index = (Thread::current()->is_Worker_thread() ? WorkerThread::worker_id() : abs(os::random())) % _alloc_region_count;
      }
    }
    switch (ALLOC_PARTITION) {
      case ShenandoahFreeSetPartitionId::Mutator:
        ShenandoahThreadLocalData::set_mutator_allocator_start_index(alloc_start_index);
        break;
      case ShenandoahFreeSetPartitionId::Collector:
        ShenandoahThreadLocalData::set_collector_allocator_start_index(alloc_start_index);
        break;
      default:
        break;
    }
  }
  return alloc_start_index;
}

template <ShenandoahFreeSetPartitionId ALLOC_PARTITION>
HeapWord* ShenandoahAllocator<ALLOC_PARTITION>::attempt_allocation(ShenandoahAllocRequest& req, bool& in_new_region) {
  uint regions_ready_for_refresh = 0u;
  uint32_t old_epoch_id = AtomicAccess::load(&_epoch_id);
  // Fast path: start the attempt to allocate in alloc regions right away
  HeapWord* obj = attempt_allocation_in_alloc_regions(req, in_new_region, alloc_start_index(), regions_ready_for_refresh);
  if (obj != nullptr && regions_ready_for_refresh < _alloc_region_count / 2) {
    return obj;
  }
  if (obj == nullptr) {
    // Slow path under heap lock
    obj = attempt_allocation_slow(req, in_new_region, regions_ready_for_refresh, old_epoch_id);
  } else {
    // Eagerly refresh alloc regions if there are 50% or more of alloc regions ready for retire.
    // While holding uninitialized new object, the thread MUST NOT yield to safepoint.
    ShenandoahHeapLocker locker(ShenandoahHeap::heap()->lock(), false);
    ShenandoahHeapAccountingUpdater accounting_updater(_free_set, ALLOC_PARTITION);
    if (_epoch_id == old_epoch_id) {
      accounting_updater._need_update = refresh_alloc_regions() > 0;
    }
  }
  return obj;
}

template <ShenandoahFreeSetPartitionId ALLOC_PARTITION>
HeapWord* ShenandoahAllocator<ALLOC_PARTITION>::attempt_allocation_slow(ShenandoahAllocRequest& req, bool& in_new_region, uint regions_ready_for_refresh, uint32_t old_epoch_id) {
  ShenandoahHeapLocker locker(ShenandoahHeap::heap()->lock(), _yield_to_safepoint);
  HeapWord* obj = nullptr;
  if (old_epoch_id != _epoch_id) {
    // After taking heap lock, attempt to allocate in shared alloc regions again
    // if alloc regions have been refreshed by other thread while current thread waits to take heap lock.
    regions_ready_for_refresh = 0u; //reset regions_ready_for_refresh to 0.
    obj = attempt_allocation_in_alloc_regions<true /*holding heap lock*/>(req, in_new_region, alloc_start_index(), regions_ready_for_refresh);
    if (obj != nullptr && regions_ready_for_refresh == 0) {
      return obj;
    }
  }

  ShenandoahHeapAccountingUpdater accounting_updater(_free_set, ALLOC_PARTITION);
  // Eagerly refresh alloc regions if any is ready for refresh since it is already holding the heap lock.
  if (regions_ready_for_refresh > 0u) {
    if (obj == nullptr) {
      if (const int refreshed = refresh_alloc_regions(&req, &in_new_region, &obj); refreshed > 0 || obj != nullptr) {
        accounting_updater._need_update = true;
      }
    } else {
      accounting_updater._need_update = refresh_alloc_regions() > 0;
    }
    if (obj != nullptr) {
      return obj;
    }
  }

  obj = attempt_allocation_from_free_set(req, in_new_region);
  if (obj != nullptr) {
    accounting_updater._need_update = true;
    return obj;
  }

  log_debug(gc, alloc)("%sAllocator: Failed to allocate satisfy the alloc request, reqeust size: %lu",
    _alloc_partition_name, req.size());
  return nullptr;
}

template <ShenandoahFreeSetPartitionId ALLOC_PARTITION>
HeapWord* ShenandoahAllocator<ALLOC_PARTITION>::attempt_allocation_from_free_set(ShenandoahAllocRequest& req, bool& in_new_region) {
  size_t min_free_words = req.is_lab_alloc() ? req.min_size() : req.size();
  ShenandoahHeapRegion* r = _free_set->find_heap_region_for_allocation(ALLOC_PARTITION, min_free_words, req.is_lab_alloc(), in_new_region);
  // The region returned by find_heap_region_for_allocation must have sufficient free space for the allocation if it is not nullptr
  if (r != nullptr) {
    bool ready_for_retire = false;
    HeapWord *obj = allocate_in<false>(r, false, req, in_new_region, ready_for_retire);
    assert(obj != nullptr, "Should always succeed.");

    _free_set->partitions()->increase_used(ALLOC_PARTITION, (req.actual_size() + req.waste()) * HeapWordSize);
    if (ALLOC_PARTITION == ShenandoahFreeSetPartitionId::Mutator) {
      _free_set->increase_bytes_allocated(req.actual_size() * HeapWordSize);
    }

    if (ready_for_retire) {
      assert(r->free_words() < PLAB::min_size(), "Must be");
      size_t waste_bytes = _free_set->partitions()->retire_from_partition(ALLOC_PARTITION, r->index(), r->used());
      if (ALLOC_PARTITION == ShenandoahFreeSetPartitionId::Mutator && (waste_bytes > 0)) {
        _free_set->increase_bytes_allocated(waste_bytes);
      }
    }
    return obj;
  }
  log_debug(gc, alloc)("%sAllocator: Didn't find one region with at least %lu free words to satisfy the alloc request, request size: %lu",
                       _alloc_partition_name, min_free_words, req.size());
  return nullptr;
}

template <ShenandoahFreeSetPartitionId ALLOC_PARTITION>
template<bool HOLDING_HEAP_LOCK>
HeapWord* ShenandoahAllocator<ALLOC_PARTITION>::attempt_allocation_in_alloc_regions(ShenandoahAllocRequest &req,
                                                                                    bool &in_new_region,
                                                                                    uint const alloc_start_index,
                                                                                    uint &regions_ready_for_refresh) {
  assert(regions_ready_for_refresh == 0u && in_new_region == false && alloc_start_index < _alloc_region_count, "Sanity check");
  uint i = alloc_start_index;
  do {
    if (ShenandoahHeapRegion* r = nullptr; (r = HOLDING_HEAP_LOCK ? _alloc_regions[i].address : AtomicAccess::load(&_alloc_regions[i].address)) != nullptr) {
      bool ready_for_retire = false;
      HeapWord* obj = allocate_in<true>(r, true, req, in_new_region, ready_for_retire);
      if (ready_for_retire) {
        regions_ready_for_refresh++;
      }
      if (obj != nullptr) {
        return obj;
      }
    } else {
      // Empty shared alloc region slot is always ready for refresh
      regions_ready_for_refresh++;
    }
    if (++i == _alloc_region_count) {
      i = 0u;
    }
  } while (i != alloc_start_index);

  return nullptr;
}

template <ShenandoahFreeSetPartitionId ALLOC_PARTITION>
template <bool IS_SHARED_ALLOC_REGION>
HeapWord* ShenandoahAllocator<ALLOC_PARTITION>::allocate_in(ShenandoahHeapRegion* region, bool const is_alloc_region, ShenandoahAllocRequest &req, bool &in_new_region, bool &ready_for_retire) {
  assert(ready_for_retire == false, "Sanity check");
  if (!IS_SHARED_ALLOC_REGION) {
    shenandoah_assert_heaplocked();
    assert(!region->is_active_alloc_region(), "Must not");
  }
  HeapWord* obj = nullptr;
  size_t actual_size = req.size();
  if (req.is_lab_alloc()) {
    obj = IS_SHARED_ALLOC_REGION ? region->allocate_lab_atomic(req, actual_size, ready_for_retire) : region->allocate_lab(req, actual_size);
  } else {
    obj = IS_SHARED_ALLOC_REGION ? region->allocate_atomic(actual_size, req, ready_for_retire) : region->allocate(req.size(), req);
  }
  if (obj != nullptr) {
    assert(actual_size > 0, "Must be");
    log_debug(gc, alloc)("%sAllocator: Allocated %lu bytes from heap region %lu, request size: %lu, alloc region: %s, remnant: %lu",
      _alloc_partition_name, actual_size * HeapWordSize, region->index(), req.size() * HeapWordSize, is_alloc_region ? "true" : "false", region->free());
    req.set_actual_size(actual_size);
    in_new_region = obj == region->bottom(); // is in new region when the allocated object is at the bottom of the region.
    if (ALLOC_PARTITION != ShenandoahFreeSetPartitionId::Mutator) {
      // For GC allocations, we advance update_watermark because the objects relocated into this memory during
      // evacuation are not updated during evacuation.  For both young and old regions r, it is essential that all
      // PLABs be made parsable at the end of evacuation.  This is enabled by retiring all plabs at end of evacuation.
      if (IS_SHARED_ALLOC_REGION) {
        region->concurrent_set_update_watermark(region->top<true>());
      } else {
        region->set_update_watermark(region->top<false>());
      }
    }

    if (!IS_SHARED_ALLOC_REGION && region->free_words() < PLAB::min_size()) {
      ready_for_retire = true;
    }
  }
  return obj;
}

template <ShenandoahFreeSetPartitionId ALLOC_PARTITION>
int ShenandoahAllocator<ALLOC_PARTITION>::refresh_alloc_regions(ShenandoahAllocRequest* req, bool* in_new_region, HeapWord** obj) {
  ResourceMark rm;
  shenandoah_assert_heaplocked();
  bool satisfy_alloc_req_first = (req != nullptr && obj != nullptr && *obj == nullptr);
  size_t min_req_size = 0;
  if (satisfy_alloc_req_first) {
    assert(in_new_region != nullptr && *in_new_region == false, "Sanity check");
    min_req_size = req->is_lab_alloc() ? req->min_size() : req->size();
  }

  int refreshable_alloc_regions = 0;
  ShenandoahAllocRegion* refreshable[MAX_ALLOC_REGION_COUNT];
  // Step 1: find out the alloc regions which are ready to refresh.
  for (uint i = 0; i < _alloc_region_count; i++) {
    ShenandoahAllocRegion* alloc_region = &_alloc_regions[i];
    ShenandoahHeapRegion* region = alloc_region->address;
    const size_t free_bytes = region == nullptr ? 0 : region->free_bytes_for_atomic_alloc();
    if (region == nullptr || free_bytes / HeapWordSize < PLAB::min_size()) {
      if (region != nullptr) {
        region->unset_active_alloc_region();
        log_debug(gc, alloc)("%sAllocator: Removing heap region %li from alloc region %i.",
          _alloc_partition_name, region->index(), alloc_region->alloc_region_index);
        AtomicAccess::store(&alloc_region->address, static_cast<ShenandoahHeapRegion*>(nullptr));
      }
      log_debug(gc, alloc)("%sAllocator: Adding alloc region %i to refreshable.",
        _alloc_partition_name, alloc_region->alloc_region_index);
      refreshable[refreshable_alloc_regions++] = alloc_region;
    }
  }

  if (refreshable_alloc_regions > 0) {
    // Step 2: allocate region from FreeSets to fill the alloc regions or satisfy the alloc request.
    ShenandoahHeapRegion* reserved[MAX_ALLOC_REGION_COUNT];
    int reserved_regions = _free_set->reserve_alloc_regions(ALLOC_PARTITION, refreshable_alloc_regions, PLAB::min_size(), reserved);
    assert(reserved_regions <= refreshable_alloc_regions, "Sanity check");
    log_debug(gc, alloc)("%sAllocator: Reserved %i regions for allocation.", _alloc_partition_name, reserved_regions);

    ShenandoahAffiliation affiliation = ALLOC_PARTITION == ShenandoahFreeSetPartitionId::OldCollector ? OLD_GENERATION : YOUNG_GENERATION;
    // Step 3: Install the new reserved alloc regions
    if (reserved_regions > 0) {
      for (int i = 0; i < reserved_regions; i++) {
        assert(reserved[i]->affiliation() == affiliation, "Affiliation of reserved region must match, invalid affiliation: %s", shenandoah_affiliation_name(reserved[i]->affiliation()));
        assert(_free_set->membership(reserved[i]->index()) == ShenandoahFreeSetPartitionId::NotFree, "Reserved heap region must have been retired from free set.");
        if (satisfy_alloc_req_first && reserved[i]->free_words() >= min_req_size) {
          bool ready_for_retire = false;
          *obj = allocate_in<false>(reserved[i], true, *req, *in_new_region, ready_for_retire);
          assert(*obj != nullptr, "Should always succeed");
          satisfy_alloc_req_first = false;
        }
        reserved[i]->set_active_alloc_region();
        // Enforce order here,
        // set_active_alloc_region must be executed before storing the region to the shared address
        OrderAccess::fence();
        log_debug(gc, alloc)("%sAllocator: Storing heap region %li to alloc region %i",
          _alloc_partition_name, reserved[i]->index(), refreshable[i]->alloc_region_index);
        AtomicAccess::store(&refreshable[i]->address, reserved[i]);
      }

      // Increase _epoch_id by 1 when any of alloc regions has been refreshed.
      AtomicAccess::inc(&_epoch_id);
    }
    return reserved_regions;
  }

  return 0;
}

template <ShenandoahFreeSetPartitionId ALLOC_PARTITION>
HeapWord* ShenandoahAllocator<ALLOC_PARTITION>::allocate(ShenandoahAllocRequest &req, bool &in_new_region) {
#ifdef ASSERT
  verify(req);
#endif // ASSERT
  if (ShenandoahHeapRegion::requires_humongous(req.size())) {
    in_new_region = true;
    ShenandoahHeapLocker locker(ShenandoahHeap::heap()->lock(), _yield_to_safepoint);
    return _free_set->allocate_contiguous(req, req.type() != ShenandoahAllocRequest::_alloc_cds /*is_humongous*/);
  }
  return attempt_allocation(req, in_new_region);
}

template <ShenandoahFreeSetPartitionId ALLOC_PARTITION>
void ShenandoahAllocator<ALLOC_PARTITION>::release_alloc_regions() {
  assert_at_safepoint();
  shenandoah_assert_heaplocked();

  log_debug(gc, alloc)("%sAllocator: Releasing all alloc regions", _alloc_partition_name);
  ShenandoahHeapAccountingUpdater accounting_updater(_free_set, ALLOC_PARTITION);
  size_t total_free_bytes = 0;
  size_t total_regions_to_unretire = 0;

  for (uint i = 0; i < _alloc_region_count; i++) {
    ShenandoahAllocRegion& alloc_region = _alloc_regions[i];
    ShenandoahHeapRegion* r = alloc_region.address;
    if (r != nullptr) {
      log_debug(gc, alloc)("%sAllocator: Releasing heap region %li from alloc region %i",
        _alloc_partition_name, r->index(), i);
      r->unset_active_alloc_region();
      alloc_region.address = nullptr;
      size_t free_bytes = r->free();
      if (free_bytes >= PLAB::min_size_bytes()) {
        total_free_bytes += free_bytes;
        total_regions_to_unretire++;
        _free_set->partitions()->unretire_to_partition(r, ALLOC_PARTITION);
      }
    }
    assert(alloc_region.address == nullptr, "Alloc region is set to nullptr after release");
  }
  _free_set->partitions()->decrease_used(ALLOC_PARTITION, total_free_bytes);
  _free_set->partitions()->increase_region_counts(ALLOC_PARTITION, total_regions_to_unretire);
  accounting_updater._need_update = true;
}

template <ShenandoahFreeSetPartitionId ALLOC_PARTITION>
void ShenandoahAllocator<ALLOC_PARTITION>::reserve_alloc_regions() {
  shenandoah_assert_heaplocked();
  ShenandoahHeapAccountingUpdater accounting_updater(_free_set, ALLOC_PARTITION);
  if (refresh_alloc_regions() > 0) {
    accounting_updater._need_update = true;
  }
}

ShenandoahMutatorAllocator::ShenandoahMutatorAllocator(ShenandoahFreeSet* free_set) :
  ShenandoahAllocator(static_cast<uint>(ShenandoahMutatorAllocRegions), free_set, true) { }

ShenandoahCollectorAllocator::ShenandoahCollectorAllocator(ShenandoahFreeSet* free_set) :
  ShenandoahAllocator(static_cast<uint>(ShenandoahCollectorAllocRegions), free_set, false) { }

ShenandoahOldCollectorAllocator::ShenandoahOldCollectorAllocator(ShenandoahFreeSet* free_set) :
  ShenandoahAllocator(0u, free_set, false) { }

HeapWord* ShenandoahOldCollectorAllocator::allocate(ShenandoahAllocRequest& req, bool& in_new_region) {
  shenandoah_assert_not_heaplocked();
#ifdef ASSERT
  verify(req);
#endif // ASSERT
  ShenandoahHeapLocker locker(ShenandoahHeap::heap()->lock(), _yield_to_safepoint);
  // Make sure the old generation has room for either evacuations or promotions before trying to allocate.
  auto old_gen = ShenandoahHeap::heap()->old_generation();
  if (!old_gen->can_allocate(req)) {
    return nullptr;
  }

  HeapWord* obj = _free_set->allocate_for_collector(req, in_new_region);
  // Record the plab configuration for this result and register the object.
  if (obj != nullptr) {
    if (req.is_lab_alloc()) {
      old_gen->configure_plab_for_current_thread(req);
    } else {
      // Register the newly allocated object while we're holding the global lock since there's no synchronization
      // built in to the implementation of register_object().  There are potential races when multiple independent
      // threads are allocating objects, some of which might span the same card region.  For example, consider
      // a card table's memory region within which three objects are being allocated by three different threads:
      //
      // objects being "concurrently" allocated:
      //    [-----a------][-----b-----][--------------c------------------]
      //            [---- card table memory range --------------]
      //
      // Before any objects are allocated, this card's memory range holds no objects.  Note that allocation of object a
      // wants to set the starts-object, first-start, and last-start attributes of the preceding card region.
      // Allocation of object b wants to set the starts-object, first-start, and last-start attributes of this card region.
      // Allocation of object c also wants to set the starts-object, first-start, and last-start attributes of this
      // card region.
      //
      // The thread allocating b and the thread allocating c can "race" in various ways, resulting in confusion, such as
      // last-start representing object b while first-start represents object c.  This is why we need to require all
      // register_object() invocations to be "mutually exclusive" with respect to each card's memory range.
      old_gen->card_scan()->register_object(obj);

      if (req.is_promotion()) {
        // Shared promotion.
        const size_t actual_size = req.actual_size() * HeapWordSize;
        log_debug(gc, plab)("Expend shared promotion of %zu bytes", actual_size);
        old_gen->expend_promoted(actual_size);
      }
    }

    return obj;
  }

  return nullptr;
}