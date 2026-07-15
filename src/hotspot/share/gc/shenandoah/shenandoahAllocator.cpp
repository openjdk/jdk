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

#include "gc/shenandoah/shenandoahAllocator.hpp"
#include "gc/shenandoah/shenandoahAllocRequest.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "jfr/utilities/jfrNode.hpp"
#include "runtime/os.hpp"

// Derive the number of CAS alloc-region stripe slots for the mutator allocator. Striping spreads
// lock-free allocation contention, so it is bounded by the available parallelism (no benefit in
// more slots than CPUs the process may run on). It is also bounded by heap size: each slot holds a
// reserved region whose remaining capacity is pre-charged to used, so too many slots on a small
// heap would pin most of the heap as partially-filled tails and starve whole-region consumers
// (evacuation reserve, humongous/contiguous allocations). We scale the heap bound to ~1/256 of the
// regions. The min of the two bounds handles both edge cases (many cores + tiny heap, and few
// threads + many cores) without a tuning flag. Always at least 1; capped at MAX_ALLOC_REGIONS (128).
// Collector allocators are sized separately (ShenandoahCollectorAllocRegions): their regions come
// from the bounded evacuation reserve, so neither bound applies to them.
//
// ShenandoahMutatorAllocRegions overrides the derived value when set to a non-zero count; 0 means
// "derive" (whether left at the default or passed explicitly as =0, so the behavior matches the
// flag's help text). An explicit value is still capped at MAX_ALLOC_REGIONS.
static uint mutator_alloc_regions() {
  if (ShenandoahMutatorAllocRegions != 0) {
    return MIN2((uint) ShenandoahMutatorAllocRegions, ShenandoahMutatorAllocator::MAX_ALLOC_REGIONS);
  }
  const uint cpu_bound = (uint) MAX2(os::initial_active_processor_count(), 1);
  const uint heap_bound = (uint) MAX2(ShenandoahHeapRegion::region_count() / 256, (size_t) 1);
  return MIN3(cpu_bound, heap_bound, ShenandoahMutatorAllocator::MAX_ALLOC_REGIONS);
}

ShenandoahAllocator::ShenandoahAllocator(ShenandoahFreeSet* free_set)
  : _free_set(free_set),
    _mutator_allocator(free_set, mutator_alloc_regions()),
    _collector_allocator(free_set, ShenandoahCollectorAllocRegions),
    _old_collector_allocator(free_set, ShenandoahCollectorAllocRegions) {}

HeapWord* ShenandoahAllocator::allocate(ShenandoahAllocRequest& req, bool& in_new_region) {
  if (ShenandoahHeapRegion::requires_humongous(req.size())) {
    ShenandoahHeapLocker locker(ShenandoahHeap::heap()->lock(), req.is_mutator_alloc());
    switch (req.type()) {
      case ShenandoahAllocRequest::_alloc_shared:
      case ShenandoahAllocRequest::_alloc_shared_gc:
        in_new_region = true;
        return _free_set->allocate_contiguous(req, /* is_humongous = */ true);
      case ShenandoahAllocRequest::_alloc_cds:
        in_new_region = true;
        return _free_set->allocate_contiguous(req, /* is_humongous = */ false);
      default:
        assert(false, "Should not reach here");
        in_new_region = false;
        return nullptr;
    }
  }

  // Route to the appropriate per-partition allocator.
  switch(req.type()) {
    case ShenandoahAllocRequest::_alloc_shared:
    case ShenandoahAllocRequest::_alloc_tlab:
    case ShenandoahAllocRequest::_alloc_cds:
      return _mutator_allocator.allocate(req, in_new_region);
    case ShenandoahAllocRequest::_alloc_gclab:
    case ShenandoahAllocRequest::_alloc_shared_gc:
      return _collector_allocator.allocate(req, in_new_region);
    case ShenandoahAllocRequest::_alloc_shared_gc_old:
    case ShenandoahAllocRequest::_alloc_shared_gc_promotion:
    case ShenandoahAllocRequest::_alloc_plab:
      return _old_collector_allocator.allocate(req, in_new_region);
    default:
      assert(false, "Should not reach here");
      return nullptr;
  }
}

void ShenandoahAllocator::release_alloc_regions() {
  _mutator_allocator.release_alloc_regions();
  _collector_allocator.release_alloc_regions();
  _old_collector_allocator.release_alloc_regions();
}

void ShenandoahAllocator::release_collector_alloc_regions() {
  _collector_allocator.release_alloc_regions();
  _old_collector_allocator.release_alloc_regions();
}

void ShenandoahAllocator::set_collector_alloc_region_count(uint workers) {
  _collector_allocator.set_alloc_region_count(workers);
  _old_collector_allocator.set_alloc_region_count(workers);
}

void ShenandoahAllocator::grow_collector_alloc_region_count(uint workers) {
  _collector_allocator.grow_alloc_region_count(workers);
  _old_collector_allocator.grow_alloc_region_count(workers);
}

size_t ShenandoahAllocator::remnant_bytes(ShenandoahFreeSetPartitionId partition) const {
  switch (partition) {
    case ShenandoahFreeSetPartitionId::Mutator:      return _mutator_allocator.remnant_bytes();
    case ShenandoahFreeSetPartitionId::Collector:    return _collector_allocator.remnant_bytes();
    case ShenandoahFreeSetPartitionId::OldCollector: return _old_collector_allocator.remnant_bytes();
    default:
      ShouldNotReachHere();
      return 0;
  }
}
