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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHALLOCATOR_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHALLOCATOR_HPP

#include "memory/allocation.hpp"
#include "utilities/globalDefinitions.hpp"

class ShenandoahFreeSet;
class ShenandoahRegionPartitions;
enum class ShenandoahFreeSetPartitionId : uint8_t;
class ShenandoahHeapRegion;
class ShenandoahAllocRequest;

class ShenandoahAllocator : public CHeapObj<mtGC> {
protected:
  struct ShenandoahAllocRegion {
    ShenandoahHeapRegion* volatile _address;
    int                            _alloc_region_index;
  };

  static constexpr uint             MAX_ALLOC_REGION_COUNT = 128;

  PaddedEnd<ShenandoahAllocRegion>* _alloc_regions;
  uint  const                       _alloc_region_count;
  ShenandoahFreeSet*                _free_set;
  ShenandoahFreeSetPartitionId      _alloc_partition_id;
  const char*                       _alloc_partition_name;
  bool                              _yield_to_safepoint = false;

  // start index of the shared alloc regions where the allocation will start from.
  virtual uint alloc_start_index() { return 0u; }

  // Attempt to allocate
  // It will try to allocate in alloc regions first, if fails it will try to get new alloc regions from free-set
  // and allocate with in the region got from free-set.
  HeapWord* attempt_allocation(ShenandoahAllocRequest& req, bool& in_new_region);

  // Slow path of allocation attempt, it will handle the allocation with heap lock held.
  HeapWord* attempt_allocation_slow(ShenandoahAllocRequest& req, bool& in_new_region);

  // Attempt to allocate from a region in free set, rather than from any of alloc regions.
  // Caller have to hold heap lock.
  HeapWord* attempt_allocation_from_free_set(ShenandoahAllocRequest& req, bool& in_new_region);

  // Attempt to allocate in shared alloc regions, the allocation attempt is done with atomic operation w/o
  // holding heap lock.
  HeapWord* attempt_allocation_in_alloc_regions(ShenandoahAllocRequest& req, bool& in_new_region, uint const alloc_start_index, uint &regions_ready_for_refresh);

  // Allocate in a region with atomic.
  inline HeapWord* atomic_allocate_in(ShenandoahHeapRegion* region, bool is_alloc_region, ShenandoahAllocRequest &req, bool &in_new_region, bool &ready_for_retire);

  // Refresh new alloc regions, allocate the object in the new alloc region.
  int refresh_alloc_regions(ShenandoahAllocRequest* req = nullptr, bool* in_new_region = nullptr, HeapWord** obj = nullptr);
#ifdef ASSERT
  virtual void verify(ShenandoahAllocRequest& req) { }
#endif

public:
  ShenandoahAllocator(uint alloc_region_count, ShenandoahFreeSet* free_set, ShenandoahFreeSetPartitionId alloc_partition_id);
  virtual ~ShenandoahAllocator() { }

  // Handle the allocation request.
  virtual HeapWord* allocate(ShenandoahAllocRequest& req, bool& in_new_region);
  virtual void release_alloc_regions();
  virtual void reserve_alloc_regions();
};

/*
 * Allocator impl for mutator
 */
class ShenandoahMutatorAllocator : public ShenandoahAllocator {
  static THREAD_LOCAL uint _alloc_start_index;
  uint alloc_start_index() override;
#ifdef ASSERT
  void verify(ShenandoahAllocRequest& req) override;
#endif // ASSERT

public:
  ShenandoahMutatorAllocator(ShenandoahFreeSet* free_set);
};

class ShenandoahCollectorAllocator : public ShenandoahAllocator {
  uint alloc_start_index() override;
#ifdef ASSERT
  void verify(ShenandoahAllocRequest& req) override;
#endif // ASSERT
public:
  ShenandoahCollectorAllocator(ShenandoahFreeSet* free_set);
};

// Currently ShenandoahOldCollectorAllocator delegate allocation handling to ShenandoahFreeSet,
// because of the complexity in plab allocation where we have specialised logic to handle card table size alignment.
// We will make ShenandoahOldCollectorAllocator use compare-and-swap/atomic operation later.
class ShenandoahOldCollectorAllocator : public ShenandoahAllocator {
  uint alloc_start_index() override;
#ifdef ASSERT
  void verify(ShenandoahAllocRequest& req) override;
#endif // ASSERT
public:
  ShenandoahOldCollectorAllocator(ShenandoahFreeSet* free_set);
  HeapWord* allocate(ShenandoahAllocRequest& req, bool& in_new_region) override;
  void release_alloc_regions() override { /* nothing to release*/ }
  void reserve_alloc_regions() override { /* no need to reserve any alloc region*/}
};

#endif //SHARE_GC_SHENANDOAH_SHENANDOAHALLOCATOR_HPP
