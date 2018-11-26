/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zCollectedHeap.hpp"
#include "gc/z/zFuture.inline.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zLock.inline.hpp"
#include "gc/z/zPage.inline.hpp"
#include "gc/z/zPageAllocator.hpp"
#include "gc/z/zPageCache.inline.hpp"
#include "gc/z/zPreMappedMemory.inline.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zTracer.inline.hpp"
#include "runtime/init.hpp"

static const ZStatCounter       ZCounterAllocationRate("Memory", "Allocation Rate", ZStatUnitBytesPerSecond);
static const ZStatCriticalPhase ZCriticalPhaseAllocationStall("Allocation Stall");

class ZPageAllocRequest : public StackObj {
  friend class ZList<ZPageAllocRequest>;

private:
  const uint8_t                _type;
  const size_t                 _size;
  const ZAllocationFlags       _flags;
  const unsigned int           _total_collections;
  ZListNode<ZPageAllocRequest> _node;
  ZFuture<ZPage*>              _result;

public:
  ZPageAllocRequest(uint8_t type, size_t size, ZAllocationFlags flags, unsigned int total_collections) :
      _type(type),
      _size(size),
      _flags(flags),
      _total_collections(total_collections) {}

  uint8_t type() const {
    return _type;
  }

  size_t size() const {
    return _size;
  }

  ZAllocationFlags flags() const {
    return _flags;
  }

  unsigned int total_collections() const {
    return _total_collections;
  }

  ZPage* wait() {
    return _result.get();
  }

  void satisfy(ZPage* page) {
    _result.set(page);
  }
};

ZPage* const ZPageAllocator::gc_marker = (ZPage*)-1;

ZPageAllocator::ZPageAllocator(size_t min_capacity, size_t max_capacity, size_t max_reserve) :
    _lock(),
    _virtual(),
    _physical(max_capacity, ZPageSizeMin),
    _cache(),
    _max_reserve(max_reserve),
    _pre_mapped(_virtual, _physical, try_ensure_unused_for_pre_mapped(min_capacity)),
    _used_high(0),
    _used_low(0),
    _used(0),
    _allocated(0),
    _reclaimed(0),
    _queue(),
    _detached() {}

bool ZPageAllocator::is_initialized() const {
  return _physical.is_initialized() &&
         _virtual.is_initialized() &&
         _pre_mapped.is_initialized();
}

size_t ZPageAllocator::max_capacity() const {
  return _physical.max_capacity();
}

size_t ZPageAllocator::current_max_capacity() const {
  return _physical.current_max_capacity();
}

size_t ZPageAllocator::capacity() const {
  return _physical.capacity();
}

size_t ZPageAllocator::max_reserve() const {
  return _max_reserve;
}

size_t ZPageAllocator::used_high() const {
  return _used_high;
}

size_t ZPageAllocator::used_low() const {
  return _used_low;
}

size_t ZPageAllocator::used() const {
  return _used;
}

size_t ZPageAllocator::allocated() const {
  return _allocated;
}

size_t ZPageAllocator::reclaimed() const {
  return _reclaimed > 0 ? (size_t)_reclaimed : 0;
}

void ZPageAllocator::reset_statistics() {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");
  _allocated = 0;
  _reclaimed = 0;
  _used_high = _used_low = _used;
}

void ZPageAllocator::increase_used(size_t size, bool relocation) {
  if (relocation) {
    // Allocating a page for the purpose of relocation has a
    // negative contribution to the number of reclaimed bytes.
    _reclaimed -= size;
  }
  _allocated += size;
  _used += size;
  if (_used > _used_high) {
    _used_high = _used;
  }
}

void ZPageAllocator::decrease_used(size_t size, bool reclaimed) {
  if (reclaimed) {
    // Only pages explicitly released with the reclaimed flag set
    // counts as reclaimed bytes. This flag is typically true when
    // a worker releases a page after relocation, and is typically
    // false when we release a page to undo an allocation.
    _reclaimed += size;
  }
  _used -= size;
  if (_used < _used_low) {
    _used_low = _used;
  }
}

size_t ZPageAllocator::max_available(bool no_reserve) const {
  size_t available = current_max_capacity() - used();

  if (no_reserve) {
    // The reserve should not be considered available
    available -= MIN2(available, max_reserve());
  }

  return available;
}

size_t ZPageAllocator::try_ensure_unused(size_t size, bool no_reserve) {
  // Ensure that we always have space available for the reserve. This
  // is needed to avoid losing the reserve because of failure to map
  // more memory before reaching max capacity.
  _physical.try_ensure_unused_capacity(size + max_reserve());

  size_t unused = _physical.unused_capacity();

  if (no_reserve) {
    // The reserve should not be considered unused
    unused -= MIN2(unused, max_reserve());
  }

  return MIN2(size, unused);
}

size_t ZPageAllocator::try_ensure_unused_for_pre_mapped(size_t size) {
  // This function is called during construction, where the
  // physical memory manager might have failed to initialied.
  if (!_physical.is_initialized()) {
    return 0;
  }

  return try_ensure_unused(size, true /* no_reserve */);
}

ZPage* ZPageAllocator::create_page(uint8_t type, size_t size) {
  // Allocate physical memory
  const ZPhysicalMemory pmem = _physical.alloc(size);
  if (pmem.is_null()) {
    // Out of memory
    return NULL;
  }

  // Allocate virtual memory
  const ZVirtualMemory vmem = _virtual.alloc(size);
  if (vmem.is_null()) {
    // Out of address space
    _physical.free(pmem);
    return NULL;
  }

  // Allocate page
  return new ZPage(type, vmem, pmem);
}

void ZPageAllocator::flush_pre_mapped() {
  if (_pre_mapped.available() == 0) {
    return;
  }

  // Detach the memory mapping.
  detach_memory(_pre_mapped.virtual_memory(), _pre_mapped.physical_memory());

  _pre_mapped.clear();
}

void ZPageAllocator::map_page(ZPage* page) {
  // Map physical memory
  _physical.map(page->physical_memory(), page->start());
}

void ZPageAllocator::detach_page(ZPage* page) {
  // Detach the memory mapping.
  detach_memory(page->virtual_memory(), page->physical_memory());

  // Add to list of detached pages
  _detached.insert_last(page);
}

void ZPageAllocator::destroy_page(ZPage* page) {
  assert(page->is_detached(), "Invalid page state");

  // Free virtual memory
  {
    ZLocker<ZLock> locker(&_lock);
    _virtual.free(page->virtual_memory());
  }

  delete page;
}

void ZPageAllocator::flush_detached_pages(ZList<ZPage>* list) {
  ZLocker<ZLock> locker(&_lock);
  list->transfer(&_detached);
}

void ZPageAllocator::flush_cache(size_t size) {
  ZList<ZPage> list;

  _cache.flush(&list, size);

  for (ZPage* page = list.remove_first(); page != NULL; page = list.remove_first()) {
    detach_page(page);
  }
}

void ZPageAllocator::check_out_of_memory_during_initialization() {
  if (!is_init_completed()) {
    vm_exit_during_initialization("java.lang.OutOfMemoryError", "Java heap too small");
  }
}

ZPage* ZPageAllocator::alloc_page_common_inner(uint8_t type, size_t size, ZAllocationFlags flags) {
  const size_t max = max_available(flags.no_reserve());
  if (max < size) {
    // Not enough free memory
    return NULL;
  }

  // Try allocating from the page cache
  ZPage* const cached_page = _cache.alloc_page(type, size);
  if (cached_page != NULL) {
    return cached_page;
  }

  // Try allocate from the pre-mapped memory
  ZPage* const pre_mapped_page = _pre_mapped.alloc_page(type, size);
  if (pre_mapped_page != NULL) {
    return pre_mapped_page;
  }

  // Flush any remaining pre-mapped memory so that
  // subsequent allocations can use the physical memory.
  flush_pre_mapped();

  // Try ensure that physical memory is available
  const size_t unused = try_ensure_unused(size, flags.no_reserve());
  if (unused < size) {
    // Flush cache to free up more physical memory
    flush_cache(size - unused);
  }

  // Create new page and allocate physical memory
  return create_page(type, size);
}

ZPage* ZPageAllocator::alloc_page_common(uint8_t type, size_t size, ZAllocationFlags flags) {
  ZPage* const page = alloc_page_common_inner(type, size, flags);
  if (page == NULL) {
    // Out of memory
    return NULL;
  }

  // Update used statistics
  increase_used(size, flags.relocation());

  // Send trace event
  ZTracer::tracer()->report_page_alloc(size, used(), max_available(flags.no_reserve()), _cache.available(), flags);

  return page;
}

ZPage* ZPageAllocator::alloc_page_blocking(uint8_t type, size_t size, ZAllocationFlags flags) {
  // Prepare to block
  ZPageAllocRequest request(type, size, flags, ZCollectedHeap::heap()->total_collections());

  _lock.lock();

  // Try non-blocking allocation
  ZPage* page = alloc_page_common(type, size, flags);
  if (page == NULL) {
    // Allocation failed, enqueue request
    _queue.insert_last(&request);
  }

  _lock.unlock();

  if (page == NULL) {
    // Allocation failed
    ZStatTimer timer(ZCriticalPhaseAllocationStall);

    // We can only block if VM is fully initialized
    check_out_of_memory_during_initialization();

    do {
      // Start asynchronous GC
      ZCollectedHeap::heap()->collect(GCCause::_z_allocation_stall);

      // Wait for allocation to complete or fail
      page = request.wait();
    } while (page == gc_marker);

    {
      // Guard deletion of underlying semaphore. This is a workaround for a
      // bug in sem_post() in glibc < 2.21, where it's not safe to destroy
      // the semaphore immediately after returning from sem_wait(). The
      // reason is that sem_post() can touch the semaphore after a waiting
      // thread have returned from sem_wait(). To avoid this race we are
      // forcing the waiting thread to acquire/release the lock held by the
      // posting thread. https://sourceware.org/bugzilla/show_bug.cgi?id=12674
      ZLocker<ZLock> locker(&_lock);
    }
  }

  return page;
}

ZPage* ZPageAllocator::alloc_page_nonblocking(uint8_t type, size_t size, ZAllocationFlags flags) {
  ZLocker<ZLock> locker(&_lock);
  return alloc_page_common(type, size, flags);
}

ZPage* ZPageAllocator::alloc_page(uint8_t type, size_t size, ZAllocationFlags flags) {
  ZPage* const page = flags.non_blocking()
                      ? alloc_page_nonblocking(type, size, flags)
                      : alloc_page_blocking(type, size, flags);
  if (page == NULL) {
    // Out of memory
    return NULL;
  }

  // Map page if needed
  if (!page->is_mapped()) {
    map_page(page);
  }

  // Reset page. This updates the page's sequence number and must
  // be done after page allocation, which potentially blocked in
  // a safepoint where the global sequence number was updated.
  page->reset();

  // Update allocation statistics. Exclude worker threads to avoid
  // artificial inflation of the allocation rate due to relocation.
  if (!flags.worker_thread()) {
    // Note that there are two allocation rate counters, which have
    // different purposes and are sampled at different frequencies.
    const size_t bytes = page->size();
    ZStatInc(ZCounterAllocationRate, bytes);
    ZStatInc(ZStatAllocRate::counter(), bytes);
  }

  return page;
}

void ZPageAllocator::satisfy_alloc_queue() {
  for (;;) {
    ZPageAllocRequest* const request = _queue.first();
    if (request == NULL) {
      // Allocation queue is empty
      return;
    }

    ZPage* const page = alloc_page_common(request->type(), request->size(), request->flags());
    if (page == NULL) {
      // Allocation could not be satisfied, give up
      return;
    }

    // Allocation succeeded, dequeue and satisfy request. Note that
    // the dequeue operation must happen first, since the request
    // will immediately be deallocated once it has been satisfied.
    _queue.remove(request);
    request->satisfy(page);
  }
}

void ZPageAllocator::detach_memory(const ZVirtualMemory& vmem, ZPhysicalMemory& pmem) {
  const uintptr_t addr = vmem.start();

  // Unmap physical memory
  _physical.unmap(pmem, addr);

  // Free physical memory
  _physical.free(pmem);

  // Clear physical mapping
  pmem.clear();
}

void ZPageAllocator::flip_page(ZPage* page) {
  const ZPhysicalMemory& pmem = page->physical_memory();
  const uintptr_t addr = page->start();

  // Flip physical mapping
  _physical.flip(pmem, addr);
}

void ZPageAllocator::flip_pre_mapped() {
  if (_pre_mapped.available() == 0) {
    // Nothing to flip
    return;
  }

  const ZPhysicalMemory& pmem = _pre_mapped.physical_memory();
  const ZVirtualMemory& vmem = _pre_mapped.virtual_memory();

  // Flip physical mapping
  _physical.flip(pmem, vmem.start());
}

void ZPageAllocator::free_page(ZPage* page, bool reclaimed) {
  ZLocker<ZLock> locker(&_lock);

  // Update used statistics
  decrease_used(page->size(), reclaimed);

  // Cache page
  _cache.free_page(page);

  // Try satisfy blocked allocations
  satisfy_alloc_queue();
}

bool ZPageAllocator::is_alloc_stalled() const {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");
  return !_queue.is_empty();
}

void ZPageAllocator::check_out_of_memory() {
  ZLocker<ZLock> locker(&_lock);

  // Fail allocation requests that were enqueued before the
  // last GC cycle started, otherwise start a new GC cycle.
  for (ZPageAllocRequest* request = _queue.first(); request != NULL; request = _queue.first()) {
    if (request->total_collections() == ZCollectedHeap::heap()->total_collections()) {
      // Start a new GC cycle, keep allocation requests enqueued
      request->satisfy(gc_marker);
      return;
    }

    // Out of memory, fail allocation request
    _queue.remove_first();
    request->satisfy(NULL);
  }
}
