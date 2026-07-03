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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHALLOCATOR_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHALLOCATOR_HPP

#include "gc/shenandoah/shenandoahAllocRequest.hpp"
#include "gc/shenandoah/shenandoahPartitionAllocator.hpp"
#include "memory/allocation.hpp"

typedef ShenandoahPartitionAllocator<ShenandoahFreeSetPartitionId::Mutator>      ShenandoahMutatorAllocator;
typedef ShenandoahPartitionAllocator<ShenandoahFreeSetPartitionId::Collector>    ShenandoahCollectorAllocator;
typedef ShenandoahPartitionAllocator<ShenandoahFreeSetPartitionId::OldCollector> ShenandoahOldCollectorAllocator;

// ShenandoahAllocator is the single entry point for memory allocations. Humongous
// requests are served directly via ShenandoahFreeSet; all other requests are routed
// to the appropriate per-partition allocator (mutator, collector, or old-collector).
// Both paths run under the heap lock.
class ShenandoahAllocator : public CHeapObj<mtGC> {
  friend class VMStructs;
private:
  ShenandoahFreeSet*                  _free_set;
  ShenandoahMutatorAllocator          _mutator_allocator;
  ShenandoahCollectorAllocator        _collector_allocator;
  ShenandoahOldCollectorAllocator     _old_collector_allocator;

public:
  ShenandoahAllocator(ShenandoahFreeSet* free_set);

  // Allocate memory from heap for a request. Humongous requests are served directly via
  // ShenandoahFreeSet; all other requests are routed to the mutator, collector, or
  // old-collector partition allocator based on request type. The heap lock is taken
  // on both paths (here for humongous, inside the partition allocator otherwise).
  // Returns nullptr if the request cannot be satisfied. Sets in_new_region to indicate
  // whether the returned address is the first allocation in a freshly acquired region.
  HeapWord* allocate(ShenandoahAllocRequest& req, bool& in_new_region);

  // Release the cached alloc regions in every partition allocator. Call before the
  // free set is rebuilt, since rebuild may reclassify region affiliation/membership.
  void release_alloc_regions();

  // Release the cached alloc region of the collector and old-collector partition allocators
  // only, leaving the mutator allocator untouched. Call at the evacuation/update-refs boundary
  // so that regions holding evacuated objects sync their _atomic_top to _top and advance their
  // update watermark before update-refs iterates the heap, while mutators keep allocating.
  void release_collector_alloc_regions();

  // Size the collector and old-collector stripe slots to the number of evacuation workers, so the
  // slot count tracks actual evac contention instead of a fixed default. Call at a safepoint before
  // evacuation begins, while the collector alloc regions are released (they are, at the
  // evac-enabling safepoint). See ShenandoahPartitionAllocator::set_alloc_region_count.
  void set_collector_alloc_region_count(uint workers);

  // Grow-only variant for a degenerated cycle that escalates to more workers than the in-flight
  // concurrent evacuation was sized for. Safe to call while collector alloc regions are still
  // occupied. See ShenandoahPartitionAllocator::grow_alloc_region_count.
  void grow_collector_alloc_region_count(uint workers);

  // Read-time accounting correction term for the given partition's cached alloc region: the
  // bytes that were pre-charged to the partition's used at reserve time but are not yet
  // actually consumed (the region's current free()). Returns 0 if no region is cached.
  // See ShenandoahPartitionAllocator::remnant_bytes.
  size_t remnant_bytes(ShenandoahFreeSetPartitionId partition) const;
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHALLOCATOR_HPP
