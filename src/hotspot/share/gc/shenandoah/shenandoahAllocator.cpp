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

#include "gc/shenandoah/shenandoahAllocator.hpp"
#include "gc/shenandoah/shenandoahAllocRequest.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"

ShenandoahAllocator::ShenandoahAllocator(ShenandoahFreeSet* free_set,
                                         ShenandoahPartitionAllocatorBase* mutator_allocator,
                                         ShenandoahPartitionAllocatorBase* collector_allocator,
                                         ShenandoahPartitionAllocatorBase* old_collector_allocator)
  : _free_set(free_set),
    _mutator_alloc(mutator_allocator),
    _collector_alloc(collector_allocator),
    _old_collector_alloc(old_collector_allocator) {}

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
      case ShenandoahAllocRequest::_alloc_plab:
      case ShenandoahAllocRequest::_alloc_gclab:
      case ShenandoahAllocRequest::_alloc_tlab:
        in_new_region = false;
        assert(false, "Trying to allocate TLAB in humongous region: %zu", req.size());
        return nullptr;
      default:
        ShouldNotReachHere();
        return nullptr;
    }
  }

  // Route to the appropriate per-partition allocator.
  if (req.is_mutator_alloc()) {
    return _mutator_alloc->allocate(req, in_new_region);
  } else if (req.is_old()) {
    return _old_collector_alloc->allocate(req, in_new_region);
  } else {
    return _collector_alloc->allocate(req, in_new_region);
  }
}

void ShenandoahAllocator::clear_retained_regions() {
  _mutator_alloc->clear_retained_regions();
  _collector_alloc->clear_retained_regions();
  _old_collector_alloc->clear_retained_regions();
}
