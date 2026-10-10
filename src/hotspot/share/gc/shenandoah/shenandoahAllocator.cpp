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
#include "logging/log.hpp"
#include "runtime/os.hpp"

// Round x to the nearest power of 2.
static uint32_t round_power_of_2(const uint32_t x) {
  assert(x > 0, "Must be positive");
  if (is_power_of_2(x)) {
    return x;
  }
  const uint32_t log2 = checked_cast<uint32_t>(log2i(x));
  const uint32_t low = 1 << log2;
  const uint32_t high = low << 1;
  return (x - low < high - x) ? low : high;
}

// Mutator stripe count: min(explicit_or_cpu_bound, heap_bound, MAX_ALLOC_REGIONS).
static uint32_t mutator_alloc_regions() {
  const uint32_t heap_bound = round_power_of_2(checked_cast<uint32_t>(MAX2(ShenandoahHeapRegion::region_count() / 256, (size_t) 1)));
  if (ShenandoahMutatorAllocRegions != 0) {
    assert(is_power_of_2(ShenandoahMutatorAllocRegions), "Must be a power of 2");
    return MIN3(checked_cast<uint32_t>(ShenandoahMutatorAllocRegions), heap_bound, ShenandoahMutatorAllocator::MAX_ALLOC_REGIONS);
  }
  const uint32_t cpu_bound = checked_cast<uint32_t>(MAX2(os::initial_active_processor_count(), 1));
  return round_down_power_of_2(MIN3(cpu_bound, heap_bound, ShenandoahMutatorAllocator::MAX_ALLOC_REGIONS));
}

// Collector stripe count: like mutator but bounded by ParallelGCThreads and region_count/512.
static uint32_t collector_alloc_regions() {
  const uint32_t heap_bound = round_power_of_2(checked_cast<uint32_t>(MAX2(ShenandoahHeapRegion::region_count() / 512, (size_t) 1)));
  if (ShenandoahCollectorAllocRegions != 0) {
    assert(is_power_of_2(ShenandoahCollectorAllocRegions), "Must be a power of 2");
    return MIN3(checked_cast<uint32_t>(ShenandoahCollectorAllocRegions), heap_bound, ShenandoahCollectorAllocator::MAX_ALLOC_REGIONS);
  }
  const uint32_t worker_bound = MAX2(checked_cast<uint32_t>(ParallelGCThreads), 1u);
  return round_down_power_of_2(MIN3(worker_bound, heap_bound, ShenandoahCollectorAllocator::MAX_ALLOC_REGIONS));
}

ShenandoahAllocator::ShenandoahAllocator(ShenandoahFreeSet* free_set)
  : _free_set(free_set),
    _mutator_allocator(free_set, mutator_alloc_regions()),
    _collector_allocator(free_set, collector_alloc_regions()),
    _old_collector_allocator(free_set, collector_alloc_regions()) {
  log_info(gc, init)("CAS Alloc Regions: mutator=%u, collector=%u",
                     _mutator_allocator.alloc_region_count(),
                     _collector_allocator.alloc_region_count());
}

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

void ShenandoahAllocator::release_collector_alloc_regions() {
  _collector_allocator.release_alloc_regions();
  if (ShenandoahHeap::heap()->mode()->is_generational()) {
    _old_collector_allocator.release_alloc_regions();
  }
}

void ShenandoahAllocator::release_collector_alloc_regions_under_lock() {
  ShenandoahHeapLocker locker(ShenandoahHeap::heap()->lock());
  release_collector_alloc_regions();
}


void ShenandoahAllocator::release_mutator_alloc_regions_under_lock() {
  ShenandoahHeapLocker locker(ShenandoahHeap::heap()->lock());
  _mutator_allocator.release_alloc_regions();
}

void ShenandoahAllocator::reserve_collector_alloc_regions_under_lock() {
  ShenandoahHeapLocker locker(ShenandoahHeap::heap()->lock());
  _collector_allocator.reserve_alloc_regions();
  if (ShenandoahHeap::heap()->mode()->is_generational()) {
    _old_collector_allocator.reserve_alloc_regions();
  }
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
