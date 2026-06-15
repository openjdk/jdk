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
private:
  ShenandoahFreeSet*                  _free_set;
  ShenandoahMutatorAllocator          _mutator_alloc;
  ShenandoahCollectorAllocator        _collector_alloc;
  ShenandoahOldCollectorAllocator     _old_collector_alloc;

public:
  ShenandoahAllocator(ShenandoahFreeSet* free_set);

  // Allocate memory from heap for a request. Humongous requests are served directly via
  // ShenandoahFreeSet; all other requests are routed to the mutator, collector, or
  // old-collector partition allocator based on request type. The heap lock is taken
  // on both paths (here for humongous, inside the partition allocator otherwise).
  // Returns nullptr if the request cannot be satisfied. Sets in_new_region to indicate
  // whether the returned address is the first allocation in a freshly acquired region.
  HeapWord* allocate(ShenandoahAllocRequest& req, bool& in_new_region);

  // Release the cached alloc region in every partition allocator. Call before the
  // free set is rebuilt, since rebuild may reclassify region affiliation/membership.
  void release_alloc_regions();
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHALLOCATOR_HPP
