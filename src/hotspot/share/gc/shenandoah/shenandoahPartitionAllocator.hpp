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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHPARTITIONALLOCATOR_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHPARTITIONALLOCATOR_HPP

#include "gc/shared/tlab_globals.hpp"
#include "gc/shenandoah/shenandoahAllocRequest.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "memory/allocation.hpp"
#include "utilities/powerOfTwo.hpp"

// Per-partition lock-free allocator. Maintains a stripe of cached "alloc regions"; threads
// bump-allocate via CAS on their slot's region. When a slot is exhausted the heap-locked
// slow path replenishes it from the free set.
template<ShenandoahFreeSetPartitionId PARTITION>
class ShenandoahPartitionAllocator : public CHeapObj<mtGC> {
  friend class VMStructs;

public:
  static constexpr uint32_t MAX_ALLOC_REGIONS = 32;

private:
  ShenandoahFreeSet* const _free_set;

  // Clamp to [1, MAX_ALLOC_REGIONS] and round down to a power of 2.
  static uint32_t clamped_alloc_region_count(uint32_t alloc_region_count) {
    return round_down_power_of_2(MIN2(MAX2(alloc_region_count, 1u), MAX_ALLOC_REGIONS));
  }

  uint32_t const _alloc_region_count;       // power-of-two slot count
  uint32_t const _alloc_region_slot_mask;   // _alloc_region_count - 1

  Atomic<ShenandoahHeapRegion*> _alloc_regions[MAX_ALLOC_REGIONS];

  uint32_t alloc_region_slot(Thread* thread);

  // Scan sibling slots for remaining capacity (last resort before OOM or stealing).
  template<bool HEAP_LOCKED>
  HeapWord* try_allocate_in_alloc_regions(ShenandoahAllocRequest& req, bool& in_new_region, uint32_t start_slot, uint32_t count);

  void uninstall_alloc_region(uint32_t slot, ShenandoahHeapRegion* occupant);
  bool try_install_alloc_region(uint32_t slot, ShenandoahHeapRegion* occupant, ShenandoahHeapRegion* new_region);

  HeapWord* allocate_in(ShenandoahHeapRegion* r,
                        ShenandoahAllocRequest& req,
                        bool& retired_after_alloc);

  HeapWord* try_atomic_allocate_in(ShenandoahHeapRegion* r, ShenandoahAllocRequest& req);

  void release_alloc_region(uint32_t slot);

public:
  ShenandoahPartitionAllocator(ShenandoahFreeSet* free_set, uint32_t alloc_region_count);

  uint32_t alloc_region_count() const { return _alloc_region_count; }

  HeapWord* allocate(ShenandoahAllocRequest& req, bool& in_new_region);

  // Must be called before free set rebuild (invalidates cached regions).
  void release_alloc_regions();

  // Pre-fill empty stripe slots from the partition. Caller must hold heap lock.
  void reserve_alloc_regions();

  size_t unsafe_max_tlab_alloc(Thread* thread) {
    uint32_t slot = alloc_region_slot(thread);
    ShenandoahHeapRegion* r = _alloc_regions[slot].load_relaxed();
    if (r != nullptr) {
      size_t free_bytes = r->free_relaxed();
      if (free_bytes >= MinTLABSize) {
        return MIN2(free_bytes, ShenandoahHeapRegion::max_tlab_size_bytes());
      }
    }
    return ShenandoahHeapRegion::max_tlab_size_bytes();
  }

  // Best-effort sum of free bytes across all cached alloc regions (relaxed reads).
  size_t remnant_bytes() const {
    const size_t min_free_bytes = ShenandoahHeap::plab_min_size() * HeapWordSize;
    size_t total = 0;
    for (uint32_t i = 0; i < _alloc_region_count; i++) {
      ShenandoahHeapRegion* r = _alloc_regions[i].load_relaxed();
      if (r != nullptr) {
        size_t free_bytes = r->free_relaxed();
        if (free_bytes >= min_free_bytes) {
          total += free_bytes;
        }
      }
    }
    return total;
  }
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHPARTITIONALLOCATOR_HPP
