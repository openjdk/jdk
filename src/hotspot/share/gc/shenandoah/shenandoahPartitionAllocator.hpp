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

#include "gc/shenandoah/shenandoahAllocRequest.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "memory/allocation.hpp"

// ShenandoahPartitionAllocator allocates memory for one free-set partition. The fast path is
// lock-free: it caches a small set of "alloc regions" (a stripe array) and bump-allocates within
// them using CAS on the region's atomic top. To spread CAS contention, each thread starts its scan
// of the stripe array at a per-thread slot (stored in thread-local data). When the fast path fails,
// it falls back to a heap-locked slow path that reserves a fresh region from the free set.
// Templated on partition ID so partition-specific behavior is resolved at compile time.
template<ShenandoahFreeSetPartitionId PARTITION>
class ShenandoahPartitionAllocator : public CHeapObj<mtGC> {

public:
  static constexpr uint MAX_ALLOC_REGIONS = 128;

private:
  ShenandoahFreeSet* const _free_set;

  // Number of alloc-region stripe slots for this partition (>= 1). Fixed for the allocator's life.
  const uint _alloc_region_count;

  // Stripe array of cached alloc regions. Each slot holds a region with remaining capacity that is
  // bump-allocated lock-free via CAS, or nullptr when the slot is empty. A slot is cleared when its
  // region is retired (by try_atomic_allocate_in when it fills, or by release_alloc_region).
  Atomic<ShenandoahHeapRegion*> _alloc_regions[MAX_ALLOC_REGIONS];

  // Return this thread's start slot for the stripe scan, assigning a stable per-thread slot on
  // first use so different threads begin at different alloc regions.
  uint alloc_region_start_index();

  // Try the lock-free CAS fast path across all stripe slots, starting at start_index and wrapping.
  // Returns the allocation, or nullptr if no slot could satisfy the request.
  HeapWord* try_allocate_in_alloc_regions(ShenandoahAllocRequest& req, bool& in_new_region, uint start_index);

  // Install r into stripe slot at index as an active alloc region (heap lock held).
  void install_alloc_region(uint index, ShenandoahHeapRegion* r);

  // Allocate within a single region; the caller must guarantee the region has enough free
  // capacity for the request. Handles LAB sizing, updates partition accounting via
  // ShenandoahFreeSet, and retires the region if remaining capacity drops below PLAB::min_size().
  // boundary_changed is set to true if the region is retired or otherwise mutates the partition
  // boundary; it is never reset to false.
  HeapWord* allocate_in(ShenandoahHeapRegion* r, ShenandoahAllocRequest& req, bool& boundary_changed);

  // Try the lock-free CAS allocation in slot `index`'s region r; retires the slot if it fills.
  HeapWord* try_atomic_allocate_in(uint index, ShenandoahHeapRegion* r, ShenandoahAllocRequest& req);

  // Retire (deactivate + reconcile) the region in stripe slot `index`; heap lock held.
  void release_alloc_region(uint index);

public:
  ShenandoahPartitionAllocator(ShenandoahFreeSet* free_set, uint alloc_region_count);

  // Allocate from this partition. Returns nullptr if partition cannot satisfy the request.
  HeapWord* allocate(ShenandoahAllocRequest& req, bool& in_new_region);

  // Drop all cached alloc regions. Must be called before the free set is rebuilt,
  // since rebuild can change region affiliation/membership and invalidate the cache.
  void release_alloc_regions();

  // Read-time accounting correction for the cached alloc regions.
  //
  // When a region is reserved as an alloc region, retire_region() pre-charges its entire remaining
  // capacity to the partition's used bytes (and drops it from the free-region count). Subsequent CAS
  // allocations consume that capacity without touching any partition counter, so while a region is
  // active the partition's used is over-counted, and available under-counted, by exactly that
  // region's current free(). This returns the sum of that correction term across all stripe slots
  // so accounting readers can compensate.
  size_t active_alloc_region_free() const {
    size_t total = 0;
    for (uint i = 0; i < _alloc_region_count; i++) {
      ShenandoahHeapRegion* r = _alloc_regions[i].load_acquire();
      if (r != nullptr) {
        total += r->free();
      }
    }
    return total;
  }
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHPARTITIONALLOCATOR_HPP
