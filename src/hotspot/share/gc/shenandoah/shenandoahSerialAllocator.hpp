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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHSERIALALLOCATOR_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHSERIALALLOCATOR_HPP

#include "gc/shenandoah/shenandoahAffiliation.hpp"
#include "gc/shenandoah/shenandoahAllocator.hpp"

class ShenandoahFreeSet;
class ShenandoahHeap;
class ShenandoahHeapRegion;
class ShenandoahRegionPartitions;

// ShenandoahSerialAllocator performs all allocations serially under the heap lock.
// It selects regions from the partitioned free set:
//  - Mutator allocations: from Mutator partition with left/right bias alternation
//  - Collector allocations: from Collector/OldCollector partition with affiliation preference
//  - Overflow: collector may steal empty regions from Mutator partition
//  - Humongous: delegated to ShenandoahFreeSet::allocate_contiguous()
class ShenandoahSerialAllocator : public ShenandoahAllocator {
private:
  ShenandoahHeap* const _heap;

  // Allocation direction alternates to avoid repeatedly skipping the same uncollected regions.
  ssize_t _alloc_bias_weight;
  static const ssize_t INITIAL_ALLOC_BIAS_WEIGHT = 256;

  // Attempt allocation within a single region. Handles LAB sizing, PLAB card-alignment,
  // affiliation checks, and updates partition accounting via ShenandoahFreeSet.
  // Retires the region if remaining capacity falls below PLAB::min_size().
  HeapWord* try_allocate_in(ShenandoahHeapRegion* r, ShenandoahAllocRequest& req, bool& in_new_region);

  HeapWord* allocate_single(ShenandoahAllocRequest& req, bool& in_new_region);
  HeapWord* allocate_for_mutator(ShenandoahAllocRequest& req, bool& in_new_region);
  HeapWord* allocate_for_collector(ShenandoahAllocRequest& req, bool& in_new_region);

  // Steal an empty region from Mutator partition for collector use.
  HeapWord* try_allocate_from_mutator(ShenandoahAllocRequest& req, bool& in_new_region);

  // Re-evaluate left-to-right vs right-to-left bias for mutator allocations.
  void update_allocation_bias();

  // Scan regions using the given iterator, attempt allocation in each.
  template<typename Iter>
  HeapWord* allocate_from_regions(Iter& iterator, ShenandoahAllocRequest& req, bool& in_new_region);

  // For collector: prefer regions matching the requested affiliation, fall back to FREE regions.
  template<typename Iter>
  HeapWord* allocate_with_affiliation(Iter& iterator,
                                      ShenandoahAffiliation affiliation,
                                      ShenandoahAllocRequest& req,
                                      bool& in_new_region);

public:
  ShenandoahSerialAllocator(ShenandoahFreeSet* free_set);

  HeapWord* allocate(ShenandoahAllocRequest& req, bool& in_new_region) override;
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHSERIALALLOCATOR_HPP
