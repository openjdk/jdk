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

// Single entry point for heap allocations. Humongous requests go directly to
// ShenandoahFreeSet under the heap lock; all others route to a per-partition
// CAS allocator (mutator, collector, or old-collector).
class ShenandoahAllocator : public CHeapObj<mtGC> {
  friend class VMStructs;
private:
  ShenandoahFreeSet*                  _free_set;
  ShenandoahMutatorAllocator          _mutator_allocator;
  ShenandoahCollectorAllocator        _collector_allocator;
  ShenandoahOldCollectorAllocator     _old_collector_allocator;

public:
  ShenandoahAllocator(ShenandoahFreeSet* free_set);

  HeapWord* allocate(ShenandoahAllocRequest& req, bool& in_new_region);

  // Release collector (and old-collector) cached alloc regions at GC phase boundaries.
  void release_collector_alloc_regions();

  void release_collector_alloc_regions_under_lock();

  void release_mutator_alloc_regions_under_lock();

  void reserve_collector_alloc_regions_under_lock();

  size_t unsafe_max_tlab_alloc(Thread* thread) {
    return _mutator_allocator.unsafe_max_tlab_alloc(thread);
  }

  // Pre-charged but unconsumed bytes in cached alloc regions (accounting correction).
  size_t remnant_bytes(ShenandoahFreeSetPartitionId partition) const;
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHALLOCATOR_HPP
