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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHPARTITIONALLOCATOR_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHPARTITIONALLOCATOR_HPP

#include "gc/shenandoah/shenandoahAffiliation.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "memory/allocation.hpp"

class ShenandoahAllocRequest;
class ShenandoahHeap;
class ShenandoahHeapRegion;
class ShenandoahSerialAllocator;

// ShenandoahPartitionAllocator handles allocation within a single free-set partition.
// Templated on partition ID so that partition-specific behavior (bias for Mutator,
// affiliation preference for Collector/OldCollector) is resolved at compile time.
template<ShenandoahFreeSetPartitionId PARTITION>
class ShenandoahPartitionAllocator : public CHeapObj<mtGC> {
  friend class ShenandoahSerialAllocator;

private:
  ShenandoahFreeSet* const _free_set;
  ShenandoahHeap* const    _heap;

  // Last region that had remaining capacity after allocation. Checked first on next request
  // to avoid scanning the partition bitmap. Cleared on free-set rebuild.
  ShenandoahHeapRegion*    _retained_region;

  // Allocation direction alternates to pack allocations tightly. Only used for Mutator.
  ssize_t _alloc_bias_weight;
  static const ssize_t INITIAL_ALLOC_BIAS_WEIGHT = 256;

  // Attempt allocation within a single region. Handles LAB sizing, PLAB card-alignment,
  // affiliation checks, and updates partition accounting via ShenandoahFreeSet.
  // Retires the region if remaining capacity falls below PLAB::min_size().
  HeapWord* try_allocate_in(ShenandoahHeapRegion* r, ShenandoahAllocRequest& req, bool& in_new_region);

  // Re-evaluate left-to-right vs right-to-left bias. Only meaningful for Mutator partition.
  void update_allocation_bias();

  // Scan regions using the given iterator, attempt allocation in each.
  template<typename Iter>
  HeapWord* allocate_from_regions(Iter& iterator, ShenandoahAllocRequest& req, bool& in_new_region);

  // For collector partitions: prefer regions matching the requested affiliation, fall back to FREE.
  template<typename Iter>
  HeapWord* allocate_with_affiliation(Iter& iterator,
                                      ShenandoahAffiliation affiliation,
                                      ShenandoahAllocRequest& req,
                                      bool& in_new_region);

  // Flip an empty region from Mutator partition to this collector partition, then allocate in it.
  HeapWord* try_allocate_from_mutator(ShenandoahAllocRequest& req, bool& in_new_region);

public:
  ShenandoahPartitionAllocator(ShenandoahFreeSet* free_set);

  // Allocate from this partition. Returns nullptr if partition cannot satisfy the request.
  HeapWord* allocate(ShenandoahAllocRequest& req, bool& in_new_region);

  // Must be called when the free set is rebuilt to invalidate retained regions.
  void clear_retained_regions() { _retained_region = nullptr; }
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHPARTITIONALLOCATOR_HPP
