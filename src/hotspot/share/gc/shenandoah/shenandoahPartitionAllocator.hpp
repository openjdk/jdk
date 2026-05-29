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

#include "gc/shenandoah/shenandoahAllocator.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"

class ShenandoahAllocRequest;
class ShenandoahHeapRegion;

// ShenandoahPartitionAllocator is the serial (lock-based) partition allocator.
// It uses ShenandoahFreeSet APIs to find regions and performs allocation within them
// under the heap lock. Templated on partition ID so that partition-specific behavior
// (overflow stealing for Collector/OldCollector) is resolved at compile time.
template<ShenandoahFreeSetPartitionId PARTITION>
class ShenandoahPartitionAllocator : public ShenandoahPartitionAllocatorBase {

private:
  ShenandoahFreeSet* const _free_set;

  // Last region that had remaining capacity after allocation. Checked first on next request
  // to avoid asking FreeSet to scan for a region. Cleared on free-set rebuild.
  ShenandoahHeapRegion* _retained_region;

  // Attempt allocation within a single region. Handles LAB sizing, updates partition
  // accounting via ShenandoahFreeSet, and retires the region if capacity drops below PLAB::min_size().
  HeapWord* try_allocate_in(ShenandoahHeapRegion* r, ShenandoahAllocRequest& req, bool& in_new_region);

public:
  ShenandoahPartitionAllocator(ShenandoahFreeSet* free_set);

  // Allocate from this partition. Returns nullptr if partition cannot satisfy the request.
  HeapWord* allocate(ShenandoahAllocRequest& req, bool& in_new_region) override;

  // Must be called when the free set is rebuilt to invalidate retained regions.
  void clear_retained_regions() override { _retained_region = nullptr; }
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHPARTITIONALLOCATOR_HPP
