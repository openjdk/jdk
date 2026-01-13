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

#include "gc/shenandoah/shenandoahAllocRequest.hpp"
#include "gc/shenandoah/shenandoahFreeSetPartitionId.hpp"
#include "gc/shenandoah/shenandoahPadding.hpp"
#include "gc/shenandoah/shenandoahThreadLocalData.hpp"
#include "memory/allocation.hpp"
#include "memory/padded.hpp"
#include "runtime/thread.hpp"
#include "utilities/globalDefinitions.hpp"

class ShenandoahFreeSet;
class ShenandoahRegionPartitions;
class ShenandoahHeapRegion;

template <ShenandoahFreeSetPartitionId ALLOC_PARTITION>
class ShenandoahAllocator : public CHeapObj<mtGC> {
protected:
  struct ShenandoahAllocRegion {
    ShenandoahHeapRegion* volatile  address;
    int                             alloc_region_index;
  };

  PaddedEnd<ShenandoahAllocRegion>*  _alloc_regions;
  uint  const                        _alloc_region_count;
  ShenandoahFreeSet* const           _free_set;
  const char*                        _alloc_partition_name;
  bool                               _yield_to_safepoint = false;
  shenandoah_padding(0);
  volatile uint32_t                  _epoch_id = 0u; // epoch id of _alloc_regions, increase by 1 whenever refresh _alloc_regions.
  shenandoah_padding(1);


  // Start index of the shared alloc regions where the allocation will start from.
  // The alloc start index is stored in thread local data, each thread has it own start index to reduce contention.
  // The value is determined when this function is called at the first time from a thread.
  // For GC workers, the started index is calculated using worker id as: WorkerThread::worker_id() % _alloc_region_count;
  // for non GC worker, the value is calculated with random like: abs(os::random()) % _alloc_region_count.
  uint alloc_start_index();

  // Attempt to allocate memory to satisfy non-humongous allocation.
  // The function is the main entry point of non-humongous allocation work, it tries fast-path allocation calling function
  // attempt_allocation_in_alloc_regions to directly allocate from shared alloc regions.
  // When fast-path allocation fails, it will call attempt_allocation_slow which acquires heap lock.
  // When fast-path allocation succeeds while it also determines >=50% of alloc regions are ready to retire, it calls
  // refresh_alloc_regions to eagerly retire and refill those alloc regions.
  HeapWord* attempt_allocation(ShenandoahAllocRequest& req, bool& in_new_region);

  // Slow path of non-humongous allocation work.
  // When the allocator fails to allocate memory with fast-path w/o holding heap lock, it calls this function
  // to try the slow-path allocation. The function always acquires heap lock and then handle the slow-path allocation as:
  // 1. if _epoch_id has changed by any other thread, try the fast-path again immediately since alloc regions have already been refreshed;
  // 2. if any region of alloc regions is ready for refresh(retire), call refresh_alloc_regions to refresh;
  // 3. if allocation is still not satisfied in above steps, call attempt_allocation_from_free_set to find a region
  //    in free set w/ sufficient free space and allocate in the region;
  // 4. update accounting if needed;
  // Caller must not hold heap lock.
  HeapWord* attempt_allocation_slow(ShenandoahAllocRequest& req, bool& in_new_region, uint regions_ready_for_refresh, uint32_t old_epoch_id);

  // Attempt to allocate from a region in free set, rather than from any of shared alloc regions, it might be called in the conditions below:
  // 1. All the shared alloc regions are not ready to retire, nor have enough space for allocation;
  // 2. There are regions can be retired, but the new regions reserved from free set don't have enough free space to satisfy the allocation.
  // Caller must hold the heap lock.
  HeapWord* attempt_allocation_from_free_set(ShenandoahAllocRequest& req, bool& in_new_region);

  // Attempt to allocate in a shared alloc region using atomic operation without holding the heap lock.
  // Returns nullptr and overwrites regions_ready_for_refresh with the number of shared alloc regions that are ready
  // to be retired if it is unable to satisfy the allocation request from the existing shared alloc regions.
  template<bool HOLDING_HEAP_LOCK = false>
  HeapWord* attempt_allocation_in_alloc_regions(ShenandoahAllocRequest& req, bool& in_new_region, uint const alloc_start_index, uint &regions_ready_for_refresh);

  // Allocate in a region, use atomic operations if template parameter ATOMIC is true.
  // When template parameter ATOMIC is false, heap lock is required.
  template <bool ATOMIC>
  HeapWord* allocate_in(ShenandoahHeapRegion* region, bool is_alloc_region, ShenandoahAllocRequest &req, bool &in_new_region, bool &ready_for_retire);

  // Refresh new alloc regions, allocate the object in the new alloc region before making the new alloc region visible to other mutators.
  int refresh_alloc_regions(ShenandoahAllocRequest* req = nullptr, bool* in_new_region = nullptr, HeapWord** obj = nullptr);

#ifdef ASSERT
  void verify(ShenandoahAllocRequest& req) {
    switch (ALLOC_PARTITION) {
      case ShenandoahFreeSetPartitionId::Mutator:
        assert(req.is_mutator_alloc(), "Must be mutator alloc request");
        assert(Thread::current()->is_Java_thread(), "Must be Java thread");
        break;
      case ShenandoahFreeSetPartitionId::Collector:
        assert(req.is_gc_alloc() && req.affiliation() == YOUNG_GENERATION, "Must be gc alloc request in young gen");
        break;
      case ShenandoahFreeSetPartitionId::OldCollector:
        assert(req.is_gc_alloc() && req.affiliation() == OLD_GENERATION, "Must be gc alloc request in old gen");
        break;
      default:
        assert(false, "Should not be here");
    }
  }
#endif

public:
  static constexpr uint             MAX_ALLOC_REGION_COUNT = 128;

  ShenandoahAllocator(uint alloc_region_count, ShenandoahFreeSet* free_set);
  virtual ~ShenandoahAllocator() { }

  // Handle the allocation request - it is the entry point of memory allocation, including humongous allocation:
  // 1. for humongous allocation, it delegates to function ShenandoahFreeSet::allocate_contiguous;
  // 2. for others allocations, it calls function attempt_allocation.
  // Caller does not hold the heap lock.
  virtual HeapWord* allocate(ShenandoahAllocRequest& req, bool& in_new_region);

  // Caller must hold the heap lock at safepoint. This causes all directly allocatable regions to be placed into
  // the appropriate ShenandoahFreeSet partition.
  // Collector calls this in preparation for choosing a collection set and/or rebuilding the freeset.
  virtual void release_alloc_regions();

  // Caller must hold the heap lock at safepoint. This causes us to set aside N regions as directly allocatable
  // by removing these regions from the relevant ShenandoahFreeSet partitions.
  // Collector calls this after rebuilding the freeset.
  virtual void reserve_alloc_regions();
};

// Allocator impl for mutator:
// 1. _yield_to_safepoint is set to true,
// 1. _alloc_region_count is configured by flag ShenandoahMutatorAllocRegions
class ShenandoahMutatorAllocator : public ShenandoahAllocator<ShenandoahFreeSetPartitionId::Mutator> {
public:
  ShenandoahMutatorAllocator(ShenandoahFreeSet* free_set);
};

// Allocator impl for collector:
// 1. _yield_to_safepoint is set to false,
// 1. _alloc_region_count is configured by flag ShenandoahCollectorAllocRegions
class ShenandoahCollectorAllocator : public ShenandoahAllocator<ShenandoahFreeSetPartitionId::Collector> {
public:
  ShenandoahCollectorAllocator(ShenandoahFreeSet* free_set);
};

// Currently ShenandoahOldCollectorAllocator delegate allocation handling to ShenandoahFreeSet,
// because of the complexity in plab allocation where we have specialized logic to handle card table size alignment.
// We will make ShenandoahOldCollectorAllocator use compare-and-swap/atomic operation later.
class ShenandoahOldCollectorAllocator : public ShenandoahAllocator<ShenandoahFreeSetPartitionId::OldCollector> {
public:
  ShenandoahOldCollectorAllocator(ShenandoahFreeSet* free_set);
  // Overrides ShenandoahAllocator::allocate function for OldCollector partition.
  // It delegates allocation work to ShenandoahFreeSet::allocate_for_collector,
  // in addition, after allocation it handles plab and remembered set related works which are needed only for old gen.
  HeapWord* allocate(ShenandoahAllocRequest& req, bool& in_new_region) override;
};

#endif //SHARE_GC_SHENANDOAH_SHENANDOAHALLOCATOR_HPP
