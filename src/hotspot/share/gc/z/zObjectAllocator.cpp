/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "gc/z/zDeferredConstructed.inline.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zHeuristics.hpp"
#include "gc/z/zLock.inline.hpp"
#include "gc/z/zObjectAllocator.hpp"
#include "gc/z/zPage.inline.hpp"
#include "gc/z/zPageAge.inline.hpp"
#include "gc/z/zPageType.hpp"
#include "gc/z/zValue.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/thread.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"

ZObjectAllocator::PerAge::PerAge(ZPageAge age)
  : _age(age),
    _use_per_cpu_shared_small_pages(ZHeuristics::use_per_cpu_shared_small_pages()),
    _shared_small_page(nullptr),
    _shared_medium_page(nullptr),
    _medium_page_alloc_lock() {}

ZPage** ZObjectAllocator::PerAge::shared_small_page_addr() {
  return _use_per_cpu_shared_small_pages ? _shared_small_page.addr() : _shared_small_page.addr(0);
}

ZPage* const* ZObjectAllocator::PerAge::shared_small_page_addr() const {
  return _use_per_cpu_shared_small_pages ? _shared_small_page.addr() : _shared_small_page.addr(0);
}

ZPage* ZObjectAllocator::PerAge::alloc_page(ZPageType type, size_t size, ZAllocationFlags flags) {
  return ZHeap::heap()->alloc_page(type, size, flags, _age);
}

void ZObjectAllocator::PerAge::undo_alloc_page(ZPage* page) {
  ZHeap::heap()->undo_alloc_page(page);
}

zaddress ZObjectAllocator::PerAge::alloc_object_in_shared_page(ZPage** shared_page,
                                                               ZPageType page_type,
                                                               size_t page_size,
                                                               size_t size,
                                                               ZAllocationFlags flags) {
  zaddress addr = zaddress::null;
  ZPage* page = Atomic::load_acquire(shared_page);

  if (page != nullptr) {
    addr = page->alloc_object_atomic(size);
  }

  if (is_null(addr)) {
    // Allocate new page
    ZPage* const new_page = alloc_page(page_type, page_size, flags);
    if (new_page != nullptr) {
      // Allocate object before installing the new page
      addr = new_page->alloc_object(size);

    retry:
      // Install new page
      ZPage* const prev_page = Atomic::cmpxchg(shared_page, page, new_page);
      if (prev_page != page) {
        if (prev_page == nullptr) {
          // Previous page was retired, retry installing the new page
          page = prev_page;
          goto retry;
        }

        // Another page already installed, try allocation there first
        const zaddress prev_addr = prev_page->alloc_object_atomic(size);
        if (is_null(prev_addr)) {
          // Allocation failed, retry installing the new page
          page = prev_page;
          goto retry;
        }

        // Allocation succeeded in already installed page
        addr = prev_addr;

        // Undo new page allocation
        undo_alloc_page(new_page);
      }
    }
  }

  return addr;
}

zaddress ZObjectAllocator::PerAge::alloc_object_in_medium_page(size_t size,
                                                               ZAllocationFlags flags) {
  zaddress addr = zaddress::null;
  ZPage** shared_medium_page = _shared_medium_page.addr();
  ZPage* page = Atomic::load_acquire(shared_medium_page);

  if (page != nullptr) {
    addr = page->alloc_object_atomic(size);
  }

  if (is_null(addr)) {
    // When a new medium page is required, we synchronize the allocation of the
    // new page using a lock. This is to avoid having multiple threads allocate
    // medium pages when we know only one of them will succeed in installing
    // the page at this layer.
    ZLocker<ZLock> locker(&_medium_page_alloc_lock);

    // When holding the lock we can't allow the page allocator to stall,
    // which in the common case it won't. The page allocation is thus done
    // in a non-blocking fashion and only if this fails we below (while not
    // holding the lock) do the blocking page allocation.
    ZAllocationFlags non_blocking_flags = flags;
    non_blocking_flags.set_non_blocking();

    if (ZPageSizeMediumMin != ZPageSizeMediumMax) {
      assert(ZPageSizeMediumEnabled, "must be enabled");
      // We attempt a fast medium allocations first. Which will only succeed
      // if a page in the range [ZPageSizeMediumMin, ZPageSizeMediumMax] can
      // be allocated without any expensive syscalls, directly from the cache.
      ZAllocationFlags fast_medium_flags = non_blocking_flags;
      fast_medium_flags.set_fast_medium();
      addr = alloc_object_in_shared_page(shared_medium_page, ZPageType::medium, ZPageSizeMediumMax, size, fast_medium_flags);
    }

    if (is_null(addr)) {
      addr = alloc_object_in_shared_page(shared_medium_page, ZPageType::medium, ZPageSizeMediumMax, size, non_blocking_flags);
    }

  }

  if (is_null(addr) && !flags.non_blocking()) {
    // The above allocation attempts failed and this allocation should stall
    // until memory is available. Redo the allocation with blocking enabled.
    addr = alloc_object_in_shared_page(shared_medium_page, ZPageType::medium, ZPageSizeMediumMax, size, flags);
  }

  return addr;
}

zaddress ZObjectAllocator::PerAge::alloc_large_object(size_t size, ZAllocationFlags flags) {
  zaddress addr = zaddress::null;

  // Allocate new large page
  const size_t page_size = align_up(size, ZGranuleSize);
  ZPage* const page = alloc_page(ZPageType::large, page_size, flags);
  if (page != nullptr) {
    // Allocate the object
    addr = page->alloc_object(size);
  }

  return addr;
}

zaddress ZObjectAllocator::PerAge::alloc_medium_object(size_t size, ZAllocationFlags flags) {
  return alloc_object_in_medium_page(size, flags);
}

zaddress ZObjectAllocator::PerAge::alloc_small_object(size_t size, ZAllocationFlags flags) {
  return alloc_object_in_shared_page(shared_small_page_addr(), ZPageType::small, ZPageSizeSmall, size, flags);
}

zaddress ZObjectAllocator::PerAge::alloc_object(size_t size, ZAllocationFlags flags) {
  if (size <= ZObjectSizeLimitSmall) {
    // Small
    return alloc_small_object(size, flags);
  } else if (size <= ZObjectSizeLimitMedium) {
    // Medium
    return alloc_medium_object(size, flags);
  } else {
    // Large
    return alloc_large_object(size, flags);
  }
}

void ZObjectAllocator::PerAge::retire_pages() {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");

  // Reset allocation pages
  _shared_medium_page.set(nullptr);
  _shared_small_page.set_all(nullptr);
}

ZObjectAllocator::ZObjectAllocator()
  : _allocators() {

  for (ZPageAge age : ZPageAgeRange()) {
    _allocators[untype(age)].initialize(age);
  }
}

ZObjectAllocator::PerAge* ZObjectAllocator::allocator(ZPageAge age) {
  return _allocators[untype(age)].get();
}

const ZObjectAllocator::PerAge* ZObjectAllocator::allocator(ZPageAge age) const {
  return _allocators[untype(age)].get();
}

void ZObjectAllocator::retire_pages(ZPageAgeRange range) {
  for (ZPageAge age : range) {
    allocator(age)->retire_pages();
  }
}

size_t ZObjectAllocator::fast_available(ZPageAge age) const {
  assert(Thread::current()->is_Java_thread(), "Should be a Java thread");

  ZPage* const* const shared_addr = allocator(age)->shared_small_page_addr();
  const ZPage* const page = Atomic::load_acquire(shared_addr);
  if (page != nullptr) {
    return page->remaining();
  }

  return 0;
}

zaddress ZObjectAllocator::alloc(size_t size) {
  ZAllocationFlags flags;
  return allocator(ZPageAge::eden)->alloc_object(size, flags);
}

zaddress ZObjectAllocator::alloc_for_relocation(size_t size, ZPageAge age) {
  ZAllocationFlags flags;

  // Object allocation for relocation should not block
  flags.set_non_blocking();

  return allocator(age)->alloc_object(size, flags);
}

