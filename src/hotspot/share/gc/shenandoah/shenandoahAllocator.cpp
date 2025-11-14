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
#include "runtime/atomicAccess.hpp"
#include "runtime/os.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/growableArray.hpp"

ShenandoahAllocator::ShenandoahAllocator(uint alloc_region_count, ShenandoahFreeSet* free_set, ShenandoahFreeSetPartitionId alloc_partition_id):
  _alloc_region_count(alloc_region_count), _free_set(free_set), _alloc_partition_id(alloc_partition_id) {
  _alloc_regions = PaddedArray<ShenandoahAllocRegion, mtGC>::create_unfreeable(alloc_region_count);
  for (uint i = 0; i < alloc_region_count; i++) {
    _alloc_regions[i]._address = nullptr;
  }
}

HeapWord* ShenandoahAllocator::attempt_allocation(ShenandoahAllocRequest& req, bool& in_new_region) {
  // Fast path, try to allocate in alloc regions w/o taking heap lock.
  uint start_index = alloc_start_index();

retry:
  HeapWord* obj = attempt_allocation_in_alloc_regions(req, in_new_region, start_index);
  if (obj != nullptr) {
    return obj;
  }
  {
    ShenandoahHeapLocker locker(ShenandoahHeap::heap()->lock(), _yield_to_safepoint);
    uint new_start_index = _alloc_region_count;
    obj = new_alloc_regions_and_allocate(&req, &in_new_region, new_start_index);
    if (obj != nullptr) {
      return obj;
    }
    // Not able to allocate in a new alloc region, but it found other region with enough free space,
    // it could happen when other thread already took heap lock and refreshed the alloc regions before current thread.
    if (new_start_index != _alloc_region_count) {
      start_index = new_start_index;
      goto retry;
    }
    // bail out, we are out of heap regions with enough space for the allocation reqeust.
    return nullptr;
  }
}

HeapWord* ShenandoahAllocator::attempt_allocation_in_alloc_regions(ShenandoahAllocRequest &req,
                                                                 bool &in_new_region,
                                                                 uint const alloc_start_index) {
  HeapWord *obj = nullptr;
  uint i = 0u;
  while (i < _alloc_region_count) {
    uint idx = (alloc_start_index + i) % _alloc_region_count;
    ShenandoahHeapRegion* r = nullptr;
    // Intentionally not using AtomicAccess::load, if a mutator see a stale region it will fail to allocate anyway.
    if ((r = _alloc_regions[idx]._address) != nullptr && r->is_active_alloc_region()) {
      obj = atomic_allocate_in(r, req, in_new_region);
      if (obj != nullptr) {
        return obj;
      }
    }
    i++;
  }
  return nullptr;
}

HeapWord* ShenandoahAllocator::atomic_allocate_in(ShenandoahHeapRegion* region, ShenandoahAllocRequest &req, bool &in_new_region) {
  HeapWord* obj = nullptr;
  size_t actual_size = req.size();
  if (req.is_lab_alloc()) {
    obj = region->allocate_lab_atomic(req, actual_size);
  } else {
    obj = region->allocate_atomic(actual_size, req);
  }
  if (obj != nullptr) {
    assert(actual_size > 0, "Must be");
    req.set_actual_size(actual_size);
    if (pointer_delta(obj, region->bottom()) == actual_size) {
      // Set to true if it is the first object/tlab allocated in the region.
      in_new_region = true;
    }
  }
  return obj;
}


HeapWord* ShenandoahAllocator::new_alloc_regions_and_allocate(ShenandoahAllocRequest* req,
                                                              bool* in_new_region,
                                                              uint &new_alloc_start_index) {
  ResourceMark rm;
  shenandoah_assert_heaplocked();
  assert(new_alloc_start_index == _alloc_region_count, "Must be.");
  GrowableArray<ShenandoahAllocRegion*> ready_for_refresh_alloc_regions;
  uint start_index = alloc_start_index();
  size_t min_alloc_size = req != nullptr ? req->is_lab_alloc() ? req->min_size() : req->size() : SIZE_MAX;;
  // Step 1: find out the alloc regions which are ready to refresh.
  for (uint i = 0; i < _alloc_region_count; i++) {
    uint idx = (start_index + i) % _alloc_region_count;
    ShenandoahAllocRegion* alloc_region = &_alloc_regions[idx];
    size_t free_words = 0;
    if (alloc_region->_address == nullptr || (free_words = alloc_region->_address->free() / HeapWordSize) < PLAB::min_size()) {
      ready_for_refresh_alloc_regions.append(alloc_region);
    } else if (new_alloc_start_index == _alloc_region_count && free_words >= min_alloc_size) {
      new_alloc_start_index = idx;
      // Instead of proceed with new alloc regions, we found one alloc region w/ enough spce for the reqeust,
      // Return and let it retry from the new index.
      return nullptr;
    }
  }

  // Step 2: allocate region from FreeSets to fill the alloc regions or satisfy the alloc request.
  GrowableArray<ShenandoahHeapRegion*> new_alloc_regions(ready_for_refresh_alloc_regions.length() + 1);
  HeapWord* obj = _free_set->reserve_alloc_regions_and_allocate(_alloc_partition_id, ready_for_refresh_alloc_regions.length(), new_alloc_regions, req, in_new_region);

  // Step 3: Install the new reserved alloc regions
  if (new_alloc_regions.length() > 0) {
    for (int i = 0; i < new_alloc_regions.length(); i++) {
      ShenandoahAllocRegion* alloc_region = ready_for_refresh_alloc_regions.at(i);
      if (alloc_region->_address != nullptr) {
        alloc_region->_address->unset_active_alloc_region();
      }
      AtomicAccess::store(&alloc_region->_address, new_alloc_regions.at(i));
    }
  }

  return obj;
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
  uint dummy = _alloc_region_count;
  new_alloc_regions_and_allocate(nullptr, nullptr, dummy);
  assert(dummy == _alloc_region_count, "Sanity check");
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
#endif // ASSERT
