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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHALLOCATOR_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHALLOCATOR_HPP

#include "memory/allocation.hpp"

class ShenandoahAllocRequest;
class ShenandoahFreeSet;
class ShenandoahHeapRegion;

// Base class for per-partition allocation strategies.
// Subclasses implement different mechanisms (serial under lock, CAS-based).
class ShenandoahPartitionAllocatorBase : public CHeapObj<mtGC> {
public:
  virtual ~ShenandoahPartitionAllocatorBase() = default;
  virtual HeapWord* allocate(ShenandoahAllocRequest& req, bool& in_new_region) = 0;
  virtual void clear_retained_regions() = 0;
};

// ShenandoahAllocator routes allocation requests to per-partition allocators
// and handles humongous allocations directly via ShenandoahFreeSet.
class ShenandoahAllocator : public CHeapObj<mtGC> {
private:
  ShenandoahFreeSet* const            _free_set;
  ShenandoahPartitionAllocatorBase*   _mutator_alloc;
  ShenandoahPartitionAllocatorBase*   _collector_alloc;
  ShenandoahPartitionAllocatorBase*   _old_collector_alloc;

public:
  ShenandoahAllocator(ShenandoahFreeSet* free_set,
                      ShenandoahPartitionAllocatorBase* mutator_allocator,
                      ShenandoahPartitionAllocatorBase* collector_allocator,
                      ShenandoahPartitionAllocatorBase* old_collector_allocator);

  // Route allocation to the appropriate partition allocator.
  // Humongous allocations are handled directly under heap lock.
  HeapWord* allocate(ShenandoahAllocRequest& req, bool& in_new_region);

  // Invalidate cached state in all partition allocators.
  void clear_retained_regions();
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHALLOCATOR_HPP
