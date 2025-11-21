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
#include "gc/shared/workerThread.hpp"
#include "memory/padded.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/atomicAccess.hpp"
#include "runtime/os.hpp"
#include "utilities/globalDefinitions.hpp"

ShenandoahAllocator::ShenandoahAllocator(uint const alloc_region_count, ShenandoahFreeSet* free_set, ShenandoahFreeSetPartitionId alloc_partition_id):
  _alloc_region_count(alloc_region_count), _free_set(free_set), _alloc_partition_id(alloc_partition_id), _alloc_partition_name(ShenandoahRegionPartitions::partition_name(alloc_partition_id)) {
  if (alloc_region_count > 0) {
    _alloc_regions = PaddedArray<ShenandoahAllocRegion, mtGC>::create_unfreeable(alloc_region_count);
    for (uint i = 0; i < alloc_region_count; i++) {
      _alloc_regions[i]._address = nullptr;
      _alloc_regions[i]._alloc_region_index = i;
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

HeapWord* ShenandoahAllocator::attempt_allocation(ShenandoahAllocRequest& req, bool& in_new_region) {
  if (_alloc_region_count == 0u) {
    ShenandoahHeapLocker locker(ShenandoahHeap::heap()->lock(), _yield_to_safepoint);
    ShenandoahHeapAccountingUpdater accounting_updater(_free_set, _alloc_partition_id);
    HeapWord* obj = attempt_allocation_from_free_set(req, in_new_region);
    if (obj != nullptr) {
      accounting_updater._need_update = true;
    }
    return obj;
  }

  uint dummy = 0;
  // Fast path: start the attempt to allocate in alloc regions right away
  HeapWord* obj = attempt_allocation_in_alloc_regions(req, in_new_region, alloc_start_index(), dummy);
  if (obj != nullptr) {
    return obj;
  }
  // Slow path under heap lock
  return attempt_allocation_slow(req, in_new_region);
}

HeapWord* ShenandoahAllocator::attempt_allocation_slow(ShenandoahAllocRequest& req, bool& in_new_region) {
  ShenandoahHeapLocker locker(ShenandoahHeap::heap()->lock(), _yield_to_safepoint);
  ShenandoahHeapAccountingUpdater accounting_updater(_free_set, _alloc_partition_id);
  uint regions_ready_for_refresh = 0u;
  HeapWord* obj = attempt_allocation_in_alloc_regions(req, in_new_region, alloc_start_index(), regions_ready_for_refresh);
  if (obj != nullptr) {
    return obj;
  }

  if (regions_ready_for_refresh > 0u) {
    int refreshed = refresh_alloc_regions(&req, &in_new_region, &obj);
    if (refreshed > 0) {
      accounting_updater._need_update = true;
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

HeapWord* ShenandoahAllocator::attempt_allocation_from_free_set(ShenandoahAllocRequest& req, bool& in_new_region) {
  HeapWord* obj;
  size_t min_free_words = req.is_lab_alloc() ? req.min_size() : req.size();
  ShenandoahHeapRegion* r = _free_set->find_heap_region_for_allocation(_alloc_partition_id, min_free_words, req.is_lab_alloc(), in_new_region);
  // The region returned by find_heap_region_for_allocation must have sufficient free space for the allocation it if it is not nullptr
  if (r != nullptr) {
    bool ready_for_retire = false;
    obj = atomic_allocate_in(r, false, req, in_new_region, ready_for_retire);
    assert(obj != nullptr, "Should always succeed.");

    _free_set->partitions()->increase_used(_alloc_partition_id, (req.actual_size() + req.waste()) * HeapWordSize);
    if (_alloc_partition_id == ShenandoahFreeSetPartitionId::Mutator) {
      _free_set->increase_bytes_allocated(req.actual_size() * HeapWordSize);
    }

    if (ready_for_retire) {
      assert(r->free_words() < PLAB::min_size(), "Must be");
      size_t waste_bytes = _free_set->partitions()->retire_from_partition(_alloc_partition_id, r->index(), r->used());
      if (_alloc_partition_id == ShenandoahFreeSetPartitionId::Mutator && (waste_bytes > 0)) {
        _free_set->increase_bytes_allocated(waste_bytes);
      }
    }
    return obj;
  }
  log_debug(gc, alloc)("%sAllocator: Didn't find one region with at least %lu free words to satisfy the alloc request, request size: %lu",
                       _alloc_partition_name, min_free_words, req.size());
  return nullptr;
}

HeapWord* ShenandoahAllocator::attempt_allocation_in_alloc_regions(ShenandoahAllocRequest &req,
                                                                   bool &in_new_region,
                                                                   uint const alloc_start_index,
                                                                   uint &regions_ready_for_refresh) {
  assert(regions_ready_for_refresh == 0u && in_new_region == false, "Sanity check");
  HeapWord *obj = nullptr;
  uint i = 0u;
  while (i < _alloc_region_count) {
    uint idx = (alloc_start_index + i) % _alloc_region_count;
    ShenandoahHeapRegion* r =  nullptr;
    if ((r = AtomicAccess::load_acquire(&_alloc_regions[idx]._address)) != nullptr && r->is_active_alloc_region()) {
      bool ready_for_retire = false;
      obj = atomic_allocate_in(r, true, req, in_new_region, ready_for_retire);
      if (ready_for_retire) {
        regions_ready_for_refresh++;
      }
      if (obj != nullptr) {
        return obj;
      }
    } else if (r == nullptr) {
      regions_ready_for_refresh++;
    }
    i++;
  }
  return nullptr;
}

inline HeapWord* ShenandoahAllocator::atomic_allocate_in(ShenandoahHeapRegion* region, bool const is_alloc_region, ShenandoahAllocRequest &req, bool &in_new_region, bool &ready_for_retire) {
  assert(ready_for_retire == false, "Sanity check");
  HeapWord* obj = nullptr;
  size_t actual_size = req.size();
  if (req.is_lab_alloc()) {
    obj = region->allocate_lab_atomic(req, actual_size, ready_for_retire);
  } else {
    obj = region->allocate_atomic(actual_size, req, ready_for_retire);
  }
  if (obj != nullptr) {
    assert(actual_size > 0, "Must be");
    log_debug(gc, alloc)("%sAllocator: Allocated %lu bytes from heap region %lu, request size: %lu, alloc region: %s, remnant: %lu",
      _alloc_partition_name, actual_size * HeapWordSize, region->index(), req.size() * HeapWordSize, is_alloc_region ? "true" : "false", region->free());
    req.set_actual_size(actual_size);
    in_new_region = obj == region->bottom(); // is in new region when the allocated object is at the bottom of the region.
    if (req.is_gc_alloc()) {
      // For GC allocations, we advance update_watermark because the objects relocated into this memory during
      // evacuation are not updated during evacuation.  For both young and old regions r, it is essential that all
      // PLABs be made parsable at the end of evacuation.  This is enabled by retiring all plabs at end of evacuation.
      region->concurrent_set_update_watermark(region->top());
    }
  }
  return obj;
}

int ShenandoahAllocator::refresh_alloc_regions(ShenandoahAllocRequest* req, bool* in_new_region, HeapWord** obj) {
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
    ShenandoahHeapRegion* region = AtomicAccess::load(&alloc_region->_address);
    size_t free_bytes = region == nullptr ? 0 : region->free();
    if (region == nullptr || free_bytes / HeapWordSize < PLAB::min_size()) {
      if (region != nullptr) {
        region->unset_active_alloc_region();
        if (_alloc_partition_id == ShenandoahFreeSetPartitionId::Mutator) {
          if (free_bytes > 0) {
            _free_set->increase_bytes_allocated(free_bytes);
          }
        }
        log_debug(gc, alloc)("%sAllocator: Removing heap region %li from alloc region %i.",
          _alloc_partition_name, region->index(), alloc_region->_alloc_region_index);
        AtomicAccess::release_store(&alloc_region->_address, static_cast<ShenandoahHeapRegion*>(nullptr));
      }
      log_debug(gc, alloc)("%sAllocator: Adding alloc region %i to refreshable.",
        _alloc_partition_name, alloc_region->_alloc_region_index);
      refreshable[refreshable_alloc_regions++] = alloc_region;
    }
  }

  if (refreshable_alloc_regions > 0) {
    // Step 2: allocate region from FreeSets to fill the alloc regions or satisfy the alloc request.
    ShenandoahHeapRegion* reserved[MAX_ALLOC_REGION_COUNT];
    int reserved_regions = _free_set->reserve_alloc_regions(_alloc_partition_id, refreshable_alloc_regions, reserved);
    assert(reserved_regions <= refreshable_alloc_regions, "Sanity check");
    log_debug(gc, alloc)("%sAllocator: Reserved %i regions for allocation.", _alloc_partition_name, reserved_regions);

    ShenandoahAffiliation affiliation = _alloc_partition_id == ShenandoahFreeSetPartitionId::OldCollector ? OLD_GENERATION : YOUNG_GENERATION;
    // Step 3: Install the new reserved alloc regions
    if (reserved_regions > 0) {
      for (int i = 0; i < reserved_regions; i++) {
        assert(reserved[i]->affiliation() == affiliation, "Affiliation of reserved region must match, invalid affiliation: %s", shenandoah_affiliation_name(reserved[i]->affiliation()));
        assert(_free_set->membership(reserved[i]->index()) == ShenandoahFreeSetPartitionId::NotFree, "Reserved heap region must have been retired from free set.");
        if (satisfy_alloc_req_first && reserved[i]->free_words() >= min_req_size) {
          bool ready_for_retire = false;
          *obj = atomic_allocate_in(reserved[i], true, *req, *in_new_region, ready_for_retire);
          satisfy_alloc_req_first = *obj == nullptr;
          if (ready_for_retire && reserved[i]->free_words() == 0) {
            log_debug(gc, alloc)("%sAllocator: heap region %li has no space left after satisfying alloc req.",
              _alloc_partition_name, reserved[i]->index());
            reserved[i]->unset_active_alloc_region();
            continue;
          }
        }
        log_debug(gc, alloc)("%sAllocator: Storing heap region %li to alloc region %i",
          _alloc_partition_name, reserved[i]->index(), refreshable[i]->_alloc_region_index);
        AtomicAccess::release_store(&refreshable[i]->_address, reserved[i]);
      }
    }
    return reserved_regions;
  }

  return 0;
}

HeapWord* ShenandoahAllocator::allocate(ShenandoahAllocRequest &req, bool &in_new_region) {
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

void ShenandoahAllocator::release_alloc_regions() {
  assert_at_safepoint();
  shenandoah_assert_heaplocked();
  log_debug(gc, alloc)("%sAllocator: Releasing all alloc regions", _alloc_partition_name);

  ShenandoahHeapAccountingUpdater accounting_updater(_free_set, _alloc_partition_id);
  size_t total_free_bytes = 0;
  size_t total_regions_to_unretire = 0;

  for (uint i = 0; i < _alloc_region_count; i++) {
    ShenandoahAllocRegion& alloc_region = _alloc_regions[i];
    ShenandoahHeapRegion* r = AtomicAccess::load(&alloc_region._address);
    if (r != nullptr) {
      assert(r->is_active_alloc_region(), "Must be");
      log_debug(gc, alloc)("%sAllocator: Releasing heap region %li from alloc region %i",
        _alloc_partition_name, r->index(), i);
      r->unset_active_alloc_region();
      AtomicAccess::store(&alloc_region._address, static_cast<ShenandoahHeapRegion*>(nullptr));
      size_t free_bytes = r->free();
      if (free_bytes >= PLAB::min_size_bytes()) {
        total_free_bytes += free_bytes;
        total_regions_to_unretire++;
        _free_set->partitions()->unretire_to_partition(r, _alloc_partition_id);
        if (!r->has_allocs()) {
          log_debug(gc, alloc)("%sAllocator: Reverting heap region %li to FREE due to no alloc in the region",
            _alloc_partition_name, r->index());
          r->make_empty();
          r->set_affiliation(FREE);
          _free_set->partitions()->increase_empty_region_counts(_alloc_partition_id, 1);
        }
      }
    }
    assert(AtomicAccess::load(&alloc_region._address) == nullptr, "Alloc region is set to nullptr after release");
  }
  _free_set->partitions()->decrease_used(_alloc_partition_id, total_free_bytes);
  _free_set->partitions()->increase_region_counts(_alloc_partition_id, total_regions_to_unretire);
  accounting_updater._need_update = true;
}

void ShenandoahAllocator::reserve_alloc_regions() {
  shenandoah_assert_heaplocked();
  ShenandoahHeapAccountingUpdater accounting_updater(_free_set, _alloc_partition_id);
  if (refresh_alloc_regions() > 0) {
    accounting_updater._need_update = true;
  }
}

THREAD_LOCAL uint ShenandoahMutatorAllocator::_alloc_start_index = UINT_MAX;

ShenandoahMutatorAllocator::ShenandoahMutatorAllocator(ShenandoahFreeSet* free_set) :
  ShenandoahAllocator((uint) ShenandoahMutatorAllocRegions, free_set, ShenandoahFreeSetPartitionId::Mutator) {
  _yield_to_safepoint = true;
}

uint ShenandoahMutatorAllocator::alloc_start_index() {
  if (_alloc_start_index == UINT_MAX) {
    if (_alloc_region_count <= 1u) {
      _alloc_start_index = 0u;
    } else {
      _alloc_start_index = abs(os::random()) % _alloc_region_count;
      assert(_alloc_start_index < _alloc_region_count, "alloc_start_index out of range");
    }
  }
  return _alloc_start_index;
}

#ifdef ASSERT
void ShenandoahMutatorAllocator::verify(ShenandoahAllocRequest& req) {
  assert(req.is_mutator_alloc(), "Must be mutator alloc request.");
}

void ShenandoahCollectorAllocator::verify(ShenandoahAllocRequest& req) {
  assert(req.is_gc_alloc() && req.affiliation() == YOUNG_GENERATION, "Must be gc alloc request in young gen.");
}

void ShenandoahOldCollectorAllocator::verify(ShenandoahAllocRequest& req) {
  assert(req.is_gc_alloc() && req.affiliation() == OLD_GENERATION, "Must be gc alloc request in young gen.");
}
#endif // ASSERT


ShenandoahCollectorAllocator::ShenandoahCollectorAllocator(ShenandoahFreeSet* free_set) :
  ShenandoahAllocator((uint) ShenandoahCollectorAllocRegions, free_set, ShenandoahFreeSetPartitionId::Collector) {
  _yield_to_safepoint = false;
}

uint ShenandoahCollectorAllocator::alloc_start_index() {
  return Thread::current()->is_Worker_thread() ? WorkerThread::worker_id() % _alloc_region_count : 0u;
}

ShenandoahOldCollectorAllocator::ShenandoahOldCollectorAllocator(ShenandoahFreeSet* free_set) :
  ShenandoahAllocator(0u, free_set, ShenandoahFreeSetPartitionId::OldCollector) {
  _yield_to_safepoint = false;
}

uint ShenandoahOldCollectorAllocator::alloc_start_index() {
  return Thread::current()->is_Worker_thread() ? WorkerThread::worker_id() % _alloc_region_count : 0u;
}

HeapWord* ShenandoahOldCollectorAllocator::allocate(ShenandoahAllocRequest& req, bool& in_new_region) {
  shenandoah_assert_not_heaplocked();
#ifdef ASSERT
  verify(req);
#endif // ASSERT
  ShenandoahHeapLocker locker(ShenandoahHeap::heap()->lock(), _yield_to_safepoint);
  // Make sure the old generation has room for either evacuations or promotions before trying to allocate.
  auto old_gen = ShenandoahHeap::heap()->old_generation();
  if (req.is_old() && !old_gen->can_allocate(req)) {
    return nullptr;
  }

  HeapWord* obj = _free_set->allocate_for_collector(req, in_new_region);
  // Record the plab configuration for this result and register the object.
  if (obj != nullptr) {
    old_gen->configure_plab_for_current_thread(req);
    if (req.type() == ShenandoahAllocRequest::_alloc_shared_gc) {
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
    }

    return obj;
  }

  return nullptr;
}