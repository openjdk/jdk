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
#include "runtime/atomicAccess.hpp"
#include "runtime/os.hpp"
#include "utilities/globalDefinitions.hpp"

ShenandoahAllocator::ShenandoahAllocator(uint const alloc_region_count, ShenandoahFreeSet* free_set, ShenandoahFreeSetPartitionId alloc_partition_id):
  _alloc_region_count(alloc_region_count), _free_set(free_set), _alloc_partition_id(alloc_partition_id) {
  _alloc_regions = PaddedArray<ShenandoahAllocRegion, mtGC>::create_unfreeable(alloc_region_count);
  for (uint i = 0; i < alloc_region_count; i++) {
    _alloc_regions[i]._address = nullptr;
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
                                        /* UsedByCollectorChanged */ false, /* UsedByOldCollectorChanged */ false>();
        _free_set->recompute_total_affiliated</* MutatorEmptiesChanged */ true, /* CollectorEmptiesChanged */ false,
                                              /* OldCollectorEmptiesChanged */ false, /* MutatorSizeChanged */ false,
                                              /* CollectorSizeChanged */ false, /* OldCollectorSizeChanged */ false,
                                              /* AffiliatedChangesAreYoungNeutral */ false, /* AffiliatedChangesAreGlobalNeutral */ false,
                                              /* UnaffiliatedChangesAreYoungNeutral */ false>();

        break;
      case ShenandoahFreeSetPartitionId::Collector:
        _free_set->recompute_total_used</* UsedByMutatorChanged */ false,
                                        /* UsedByCollectorChanged */ true, /* UsedByOldCollectorChanged */ false>();
        _free_set->recompute_total_affiliated</* MutatorEmptiesChanged */ true, /* CollectorEmptiesChanged */ true,
                                              /* OldCollectorEmptiesChanged */ false, /* MutatorSizeChanged */ true,
                                              /* CollectorSizeChanged */ true, /* OldCollectorSizeChanged */ false,
                                              /* AffiliatedChangesAreYoungNeutral */ false, /* AffiliatedChangesAreGlobalNeutral */ false,
                                              /* UnaffiliatedChangesAreYoungNeutral */ false>();
        break;
      case ShenandoahFreeSetPartitionId::OldCollector:
        _free_set->recompute_total_used</* UsedByMutatorChanged */ false,
                                        /* UsedByCollectorChanged */ false, /* UsedByOldCollectorChanged */ true>();
        _free_set->recompute_total_affiliated</* MutatorEmptiesChanged */ true, /* CollectorEmptiesChanged */ false,
                                              /* OldCollectorEmptiesChanged */ true, /* MutatorSizeChanged */ true,
                                              /* CollectorSizeChanged */ false, /* OldCollectorSizeChanged */ true,
                                              /* AffiliatedChangesAreYoungNeutral */ true, /* AffiliatedChangesAreGlobalNeutral */ false,
                                              /* UnaffiliatedChangesAreYoungNeutral */ true>();
        break;
      case ShenandoahFreeSetPartitionId::NotFree:
      default:
        assert(false, "won't happen");
    }
    }
  }

};

HeapWord* ShenandoahAllocator::attempt_allocation(ShenandoahAllocRequest& req, bool& in_new_region) {
  uint regions_ready_for_refresh = 0;
  // Fast path: Start
  HeapWord* obj = attempt_allocation_in_alloc_regions(req, in_new_region, alloc_start_index(), regions_ready_for_refresh);
  if (obj != nullptr && regions_ready_for_refresh < 3) {
    return obj;
  }

  {
    ShenandoahHeapLocker locker(ShenandoahHeap::heap()->lock(), _yield_to_safepoint);
    ShenandoahHeapAccountingUpdater accounting_updater(_free_set, _alloc_partition_id);
    // We may run to here to take heap lock for different reasons:
    // 1. attempt_allocation_in_alloc_regions was not able to allocate the object, obj is nullptr.
    // 2. attempt_allocation_in_alloc_regions was able to allocate the object, but determined that there are 3 or more regions were ready to retire.
    // For #1, it will retry attempt_allocation_in_alloc_regions right away after taking heap lock,
    // if retry on attempt_allocation_in_alloc_regions succeeded, it means one of other threads successfully just refreshed alloc regions, it will return immediately.
    if (obj == nullptr) {
      regions_ready_for_refresh = 0;
      obj = attempt_allocation_in_alloc_regions(req, in_new_region, alloc_start_index(), regions_ready_for_refresh);
      if (obj != nullptr) {
        return obj;
      }
    }

    if (obj == nullptr) {
      size_t min_free_words = req.is_lab_alloc() ? req.min_size() : req.size();
      ShenandoahHeapRegion* r = _free_set->find_heap_region_for_allocation(_alloc_partition_id, min_free_words, req.is_lab_alloc(), in_new_region);
      // We know there is no region with capacity more than min_free_words in partition,
      // short-cut here if min_free_words is smaller than PLAB::max_size(), since we only reserve region as alloc region
      // if the region has at least PLAB::max_size() capacity.
      if (r == nullptr && min_free_words < PLAB::max_size()) {
        return nullptr;
      }
      if (r != nullptr) {
        bool dummy;
        bool ready_for_retire = false;
        obj = atomic_allocate_in(r, req, dummy, ready_for_retire);
        assert(obj != nullptr, "Should always succeed.");

        accounting_updater._need_update = true;
        if (_alloc_partition_id == ShenandoahFreeSetPartitionId::Mutator) {
          _free_set->partitions()->increase_used(ShenandoahFreeSetPartitionId::Mutator, req.actual_size() * HeapWordSize);
          _free_set->increase_bytes_allocated(req.actual_size() * HeapWordSize);
        } else {
          _free_set->partitions()->increase_used(_alloc_partition_id, (req.actual_size() + req.waste()) * HeapWordSize);
          r->set_update_watermark(r->top());
        }

        if (ready_for_retire) {
          size_t waste_bytes = _free_set->partitions()->retire_from_partition(_alloc_partition_id, r->index(), r->used());
          if (_alloc_partition_id == ShenandoahFreeSetPartitionId::Mutator && (waste_bytes > 0)) {
            _free_set->increase_bytes_allocated(waste_bytes);
          }
        }

        if (regions_ready_for_refresh == 0) {
          return obj;
        }
      }
    }

    if (regions_ready_for_refresh > 0) {
      int refreshed = refresh_alloc_regions();
      if (refreshed > 0) {
        accounting_updater._need_update = true;
      }
    }

    return obj;
  }
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
    ShenandoahHeapRegion* r = nullptr;
    // Intentionally not using AtomicAccess::load, if a mutator see a stale region it will fail to allocate anyway.
    if ((r = _alloc_regions[idx]._address) != nullptr && r->is_active_alloc_region()) {
      bool ready_for_retire = false;
      obj = atomic_allocate_in(r, req, in_new_region, ready_for_retire);
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

HeapWord* ShenandoahAllocator::atomic_allocate_in(ShenandoahHeapRegion* region, ShenandoahAllocRequest &req, bool &in_new_region, bool &ready_for_retire) {
  HeapWord* obj = nullptr;
  size_t actual_size = req.size();
  if (req.is_lab_alloc()) {
    obj = region->allocate_lab_atomic(req, actual_size, ready_for_retire);
  } else {
    obj = region->allocate_atomic(actual_size, req, ready_for_retire);
  }
  if (obj != nullptr) {
    assert(actual_size > 0, "Must be");
    req.set_actual_size(actual_size);
    if (pointer_delta(obj, region->bottom()) == actual_size) {
      // Set to true if it is the first object/tlab allocated in the region.
      in_new_region = true;
    }
    if (req.is_gc_alloc()) {
      // For GC allocations, we advance update_watermark because the objects relocated into this memory during
      // evacuation are not updated during evacuation.  For both young and old regions r, it is essential that all
      // PLABs be made parsable at the end of evacuation.  This is enabled by retiring all plabs at end of evacuation.
      // TODO double check if race condition here could cause problem?
      region->set_update_watermark(region->top());
    }
  }
  return obj;
}


int ShenandoahAllocator::refresh_alloc_regions() {
  ResourceMark rm;
  shenandoah_assert_heaplocked();
  int refreshable_alloc_regions = 0;
  ShenandoahAllocRegion* refreshable[MAX_ALLOC_REGION_COUNT];
  // Step 1: find out the alloc regions which are ready to refresh.
  for (uint i = 0; i < _alloc_region_count; i++) {
    ShenandoahAllocRegion* alloc_region = &_alloc_regions[i];
    ShenandoahHeapRegion* region = alloc_region->_address;
    size_t free_bytes = region == nullptr ? 0 : region->free();
    if (region != nullptr && free_bytes / HeapWordSize < PLAB::min_size()) {
      region->unset_active_alloc_region();
      if (_alloc_partition_id == ShenandoahFreeSetPartitionId::Mutator) {
        if (free_bytes > 0) {
          _free_set->increase_bytes_allocated(free_bytes);
        }
      }
      AtomicAccess::store(&alloc_region->_address, static_cast<ShenandoahHeapRegion*>(nullptr));
      refreshable[refreshable_alloc_regions++] = alloc_region;
    }
  }

  // Step 2: allocate region from FreeSets to fill the alloc regions or satisfy the alloc request.
  ShenandoahHeapRegion* reserved[MAX_ALLOC_REGION_COUNT];
  int reserved_regions = _free_set->reserve_alloc_regions(_alloc_partition_id, refreshable_alloc_regions, reserved);
  assert(reserved_regions <= refreshable_alloc_regions, "Sanity check");

  // Step 3: Install the new reserved alloc regions
  if (reserved_regions > 0) {
    for (int i = 0; i < reserved_regions; i++) {
      AtomicAccess::store(&refreshable[i]->_address, reserved[i]);
    }
  }
  return reserved_regions;
}

HeapWord* ShenandoahAllocator::allocate(ShenandoahAllocRequest &req, bool &in_new_region) {
#ifdef ASSERT
  verify(req);
#endif // ASSERT
  if (ShenandoahHeapRegion::requires_humongous(req.size())) {
    in_new_region = true;
    ShenandoahHeapLocker locker(ShenandoahHeap::heap()->lock(), _yield_to_safepoint);
    return _free_set->allocate_contiguous(req, req.type() != ShenandoahAllocRequest::_alloc_cds /*is_humongous*/);
  } else {
    return attempt_allocation(req, in_new_region);
  }
  return nullptr;
}

void ShenandoahAllocator::release_alloc_regions() {
  assert_at_safepoint();
  shenandoah_assert_heaplocked();
  for (uint i = 0; i < _alloc_region_count; i++) {
    ShenandoahAllocRegion& alloc_region = _alloc_regions[i];
    ShenandoahHeapRegion* r = AtomicAccess::load(&alloc_region._address);
    if (r != nullptr) {
      assert(r->is_active_alloc_region(), "Must be");
      AtomicAccess::store(&alloc_region._address, static_cast<ShenandoahHeapRegion*>(nullptr));
      r->unset_active_alloc_region();
      size_t free_bytes = r->free();
      if (free_bytes == ShenandoahHeapRegion::region_size_bytes()) {
        r->make_empty();
        r->set_affiliation(FREE);
        _free_set->partitions()->decrease_used(_alloc_partition_id, free_bytes);
        _free_set->partitions()->unretire_to_partition(r, _alloc_partition_id);
      } else if (free_bytes >= PLAB::min_size_bytes()) {
        _free_set->partitions()->decrease_used(_alloc_partition_id, free_bytes);
        _free_set->partitions()->unretire_to_partition(r, _alloc_partition_id);
      }
    }
  }
}

void ShenandoahAllocator::reserve_alloc_regions() {
  shenandoah_assert_heaplocked();
  refresh_alloc_regions();
}

THREAD_LOCAL uint ShenandoahMutatorAllocator::_alloc_start_index = UINT_MAX;

ShenandoahMutatorAllocator::ShenandoahMutatorAllocator(ShenandoahFreeSet* free_set) :
  ShenandoahAllocator((uint) ShenandoahMutatorAllocRegionCount, free_set, ShenandoahFreeSetPartitionId::Mutator) {
  _yield_to_safepoint = true;
}

uint ShenandoahMutatorAllocator::alloc_start_index() {
  if (_alloc_start_index == UINT_MAX) {
    _alloc_start_index = abs(os::random()) % _alloc_region_count;
    assert(_alloc_start_index < _alloc_region_count, "alloc_start_index out of range");
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
  ShenandoahAllocator((uint) ParallelGCThreads, free_set, ShenandoahFreeSetPartitionId::Collector) {
  _yield_to_safepoint = false;
}

uint ShenandoahCollectorAllocator::alloc_start_index() {
  return Thread::current()->is_Worker_thread() ? WorkerThread::worker_id() % _alloc_region_count : 0u;
}

ShenandoahOldCollectorAllocator::ShenandoahOldCollectorAllocator(ShenandoahFreeSet* free_set) :
  ShenandoahAllocator((uint) ParallelGCThreads, free_set, ShenandoahFreeSetPartitionId::OldCollector) {
  _yield_to_safepoint = false;
}

uint ShenandoahOldCollectorAllocator::alloc_start_index() {
  return Thread::current()->is_Worker_thread() ? WorkerThread::worker_id() % _alloc_region_count : 0u;
}