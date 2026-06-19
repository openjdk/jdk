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

#include "gc/shenandoah/shenandoahAllocRequest.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "memory/allocation.hpp"

// ShenandoahPartitionAllocator is the serial (lock-based) partition allocator.
// It uses ShenandoahFreeSet APIs to find regions and performs allocation within them
// under the heap lock. Templated on partition ID so that partition-specific behavior
// (overflow stealing for Collector/OldCollector) is resolved at compile time.
template<ShenandoahFreeSetPartitionId PARTITION>
class ShenandoahPartitionAllocator : public CHeapObj<mtGC> {

private:
  ShenandoahFreeSet* const _free_set;

  // Cached allocation region with remaining capacity from the last allocation in
  // this partition. Checked first on the next request to skip a FreeSet scan.
  // Cleared when retired by allocate_in or by release_alloc_region.
  ShenandoahHeapRegion* _alloc_region;

  // Allocate within a single region; the caller must guarantee the region has enough free
  // capacity for the request. Handles LAB sizing, updates partition accounting via
  // ShenandoahFreeSet, and retires the region if remaining capacity drops below PLAB::min_size().
  // boundary_changed is set to true if the region is retired or otherwise mutates the partition
  // boundary; it is never reset to false.
  HeapWord* allocate_in(ShenandoahHeapRegion* r, ShenandoahAllocRequest& req, bool& boundary_changed);

public:
  ShenandoahPartitionAllocator(ShenandoahFreeSet* free_set);

  // Allocate from this partition. Returns nullptr if partition cannot satisfy the request.
  HeapWord* allocate(ShenandoahAllocRequest& req, bool& in_new_region);

  // Drop the cached alloc region. Must be called before the free set is rebuilt,
  // since rebuild can change region affiliation/membership and invalidate the cache.
  void release_alloc_region() { _alloc_region = nullptr; }
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHPARTITIONALLOCATOR_HPP
