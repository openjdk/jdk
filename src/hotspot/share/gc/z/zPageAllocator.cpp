/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/gcLogPrecious.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zCollectedHeap.hpp"
#include "gc/z/zFuture.inline.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zLock.inline.hpp"
#include "gc/z/zPage.inline.hpp"
#include "gc/z/zPageAllocator.hpp"
#include "gc/z/zPageCache.inline.hpp"
#include "gc/z/zSafeDelete.inline.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zTask.hpp"
#include "gc/z/zTracer.inline.hpp"
#include "gc/z/zWorkers.hpp"
#include "jfr/jfrEvents.hpp"
#include "runtime/globals.hpp"
#include "runtime/init.hpp"
#include "runtime/java.hpp"
#include "utilities/debug.hpp"

static const ZStatCounter       ZCounterAllocationRate("Memory", "Allocation Rate", ZStatUnitBytesPerSecond);
static const ZStatCounter       ZCounterPageCacheFlush("Memory", "Page Cache Flush", ZStatUnitBytesPerSecond);
static const ZStatCounter       ZCounterUncommit("Memory", "Uncommit", ZStatUnitBytesPerSecond);
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
      _total_collections(total_collections),
      _node(),
      _result() {}

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

  ZPage* peek() {
    return _result.peek();
  }

  ZPage* wait() {
    return _result.get();
  }

  void satisfy(ZPage* page) {
    _result.set(page);
  }
};

ZPage* const ZPageAllocator::gc_marker = (ZPage*)-1;

ZPageAllocator::ZPageAllocator(ZWorkers* workers,
                               size_t min_capacity,
                               size_t initial_capacity,
                               size_t max_capacity,
                               size_t max_reserve) :
    _lock(),
    _virtual(max_capacity),
    _physical(max_capacity),
    _cache(),
    _min_capacity(min_capacity),
    _max_capacity(max_capacity),
    _max_reserve(max_reserve),
    _current_max_capacity(max_capacity),
    _capacity(0),
    _used_high(0),
    _used_low(0),
    _used(0),
    _allocated(0),
    _reclaimed(0),
    _queue(),
    _satisfied(),
    _safe_delete(),
    _uncommit(false),
    _initialized(false) {

  if (!_virtual.is_initialized() || !_physical.is_initialized()) {
    return;
  }

  log_info_p(gc, init)("Min Capacity: " SIZE_FORMAT "M", min_capacity / M);
  log_info_p(gc, init)("Initial Capacity: " SIZE_FORMAT "M", initial_capacity / M);
  log_info_p(gc, init)("Max Capacity: " SIZE_FORMAT "M", max_capacity / M);
  log_info_p(gc, init)("Max Reserve: " SIZE_FORMAT "M", max_reserve / M);
  log_info_p(gc, init)("Pre-touch: %s", AlwaysPreTouch ? "Enabled" : "Disabled");

  // Warn if system limits could stop us from reaching max capacity
  _physical.warn_commit_limits(max_capacity);

  // Commit initial capacity
  _capacity = _physical.commit(initial_capacity);
  if (_capacity != initial_capacity) {
    log_error_p(gc)("Failed to allocate initial Java heap (" SIZE_FORMAT "M)", initial_capacity / M);
    return;
  }

  // If uncommit is not explicitly disabled, max capacity is greater than
  // min capacity, and uncommit is supported by the platform, then we will
  // try to uncommit unused memory.
  _uncommit = ZUncommit && (max_capacity > min_capacity) && _physical.supports_uncommit();
  if (_uncommit) {
    log_info(gc, init)("Uncommit: Enabled, Delay: " UINTX_FORMAT "s", ZUncommitDelay);
  } else {
    log_info(gc, init)("Uncommit: Disabled");
  }

  // Pre-map initial capacity
  prime_cache(workers, initial_capacity);

  // Successfully initialized
  _initialized = true;
}

class ZPreTouchTask : public ZTask {
private:
  const ZPhysicalMemoryManager* const _physical;
  volatile uintptr_t                  _start;
  const uintptr_t                     _end;

public:
  ZPreTouchTask(const ZPhysicalMemoryManager* physical, uintptr_t start, uintptr_t end) :
      ZTask("ZPreTouchTask"),
      _physical(physical),
      _start(start),
      _end(end) {}

  virtual void work() {
    for (;;) {
      // Get granule offset
      const size_t size = ZGranuleSize;
      const uintptr_t offset = Atomic::fetch_and_add(&_start, size);
      if (offset >= _end) {
        // Done
        break;
      }

      // Pre-touch granule
      _physical->pretouch(offset, size);
    }
  }
};

void ZPageAllocator::prime_cache(ZWorkers* workers, size_t size) {
  // Allocate physical memory
  const ZPhysicalMemory pmem = _physical.alloc(size);
  guarantee(!pmem.is_null(), "Invalid size");

  // Allocate virtual memory
  const ZVirtualMemory vmem = _virtual.alloc(size, true /* alloc_from_front */);
  guarantee(!vmem.is_null(), "Invalid size");

  // Allocate page
  ZPage* const page = new ZPage(vmem, pmem);

  // Map page
  map_page(page);
  page->set_pre_mapped();

  if (AlwaysPreTouch) {
    // Pre-touch page
    ZPreTouchTask task(&_physical, page->start(), page->end());
    workers->run_parallel(&task);
  }

  // Add page to cache
  page->set_last_used();
  _cache.free_page(page);
}

bool ZPageAllocator::is_initialized() const {
  return _initialized;
}

size_t ZPageAllocator::min_capacity() const {
  return _min_capacity;
}

size_t ZPageAllocator::max_capacity() const {
  return _max_capacity;
}

size_t ZPageAllocator::soft_max_capacity() const {
  // Note that SoftMaxHeapSize is a manageable flag
  return MIN2(SoftMaxHeapSize, _current_max_capacity);
}

size_t ZPageAllocator::capacity() const {
  return _capacity;
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

size_t ZPageAllocator::unused() const {
  const ssize_t unused = (ssize_t)_capacity - (ssize_t)_used - (ssize_t)_max_reserve;
  return unused > 0 ? (size_t)unused : 0;
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
  // Only pages explicitly released with the reclaimed flag set
  // counts as reclaimed bytes. This flag is true when we release
  // a page after relocation, and is false when we release a page
  // to undo an allocation.
  if (reclaimed) {
    _reclaimed += size;
  } else {
    _allocated -= size;
  }
  _used -= size;
  if (_used < _used_low) {
    _used_low = _used;
  }
}

ZPage* ZPageAllocator::create_page(uint8_t type, size_t size) {
  // Allocate virtual memory
  const ZVirtualMemory vmem = _virtual.alloc(size);
  if (vmem.is_null()) {
    // Out of address space
    return NULL;
  }

  // Allocate physical memory
  const ZPhysicalMemory pmem = _physical.alloc(size);
  assert(!pmem.is_null(), "Invalid size");

  // Allocate page
  return new ZPage(type, vmem, pmem);
}

void ZPageAllocator::destroy_page(ZPage* page) {
  const ZVirtualMemory& vmem = page->virtual_memory();
  const ZPhysicalMemory& pmem = page->physical_memory();

  // Unmap memory
  _physical.unmap(pmem, vmem.start());

  // Free physical memory
  _physical.free(pmem);

  // Free virtual memory
  _virtual.free(vmem);

  // Delete page safely
  _safe_delete(page);
}

void ZPageAllocator::map_page(const ZPage* page) const {
  // Map physical memory
  _physical.map(page->physical_memory(), page->start());
}

size_t ZPageAllocator::max_available(bool no_reserve) const {
  size_t available = _current_max_capacity - _used;

  if (no_reserve) {
    // The reserve should not be considered available
    available -= MIN2(available, _max_reserve);
  }

  return available;
}

bool ZPageAllocator::ensure_available(size_t size, bool no_reserve) {
  if (max_available(no_reserve) < size) {
    // Not enough free memory
    return false;
  }

  // We add the max_reserve to the requested size to avoid losing
  // the reserve because of failure to increase capacity before
  // reaching max capacity.
  size += _max_reserve;

  // Don't try to increase capacity if enough unused capacity
  // is available or if current max capacity has been reached.
  const size_t available = _capacity - _used;
  if (available < size && _capacity < _current_max_capacity) {
    // Try to increase capacity
    const size_t commit = MIN2(size - available, _current_max_capacity - _capacity);
    const size_t committed = _physical.commit(commit);
    _capacity += committed;

    log_trace(gc, heap)("Make Available: Size: " SIZE_FORMAT "M, NoReserve: %s, "
                        "Available: " SIZE_FORMAT "M, Commit: " SIZE_FORMAT "M, "
                        "Committed: " SIZE_FORMAT "M, Capacity: " SIZE_FORMAT "M",
                        size / M, no_reserve ? "True" : "False", available / M,
                        commit / M, committed / M, _capacity / M);

    if (committed != commit) {
      // Failed, or partly failed, to increase capacity. Adjust current
      // max capacity to avoid further attempts to increase capacity.
      log_error_p(gc)("Forced to lower max Java heap size from "
                      SIZE_FORMAT "M(%.0f%%) to " SIZE_FORMAT "M(%.0f%%)",
                      _current_max_capacity / M, percent_of(_current_max_capacity, _max_capacity),
                      _capacity / M, percent_of(_capacity, _max_capacity));

      _current_max_capacity = _capacity;
    }
  }

  if (!no_reserve) {
    size -= _max_reserve;
  }

  const size_t new_available = _capacity - _used;
  return new_available >= size;
}

void ZPageAllocator::ensure_uncached_available(size_t size) {
  assert(_capacity - _used >= size, "Invalid size");
  const size_t uncached_available = _capacity - _used - _cache.available();
  if (size > uncached_available) {
    flush_cache_for_allocation(size - uncached_available);
  }
}

ZPage* ZPageAllocator::alloc_page_common_inner(uint8_t type, size_t size, bool no_reserve) {
  if (!ensure_available(size, no_reserve)) {
    // Not enough free memory
    return NULL;
  }

  // Try allocate page from the cache
  ZPage* const page = _cache.alloc_page(type, size);
  if (page != NULL) {
    return page;
  }

  // Try flush pages from the cache
  ensure_uncached_available(size);

  // Create new page
  return create_page(type, size);
}

ZPage* ZPageAllocator::alloc_page_common(uint8_t type, size_t size, ZAllocationFlags flags) {
  EventZPageAllocation event;

  ZPage* const page = alloc_page_common_inner(type, size, flags.no_reserve());
  if (page == NULL) {
    // Out of memory
    return NULL;
  }

  // Update used statistics
  increase_used(size, flags.relocation());

  // Send trace event
  event.commit(type, size, _used, max_available(flags.no_reserve()),
               _cache.available(), flags.non_blocking(), flags.no_reserve());

  return page;
}

void ZPageAllocator::check_out_of_memory_during_initialization() {
  if (!is_init_completed()) {
    vm_exit_during_initialization("java.lang.OutOfMemoryError", "Java heap too small");
  }
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
    EventZAllocationStall event;

    // We can only block if VM is fully initialized
    check_out_of_memory_during_initialization();

    do {
      // Start asynchronous GC
      ZCollectedHeap::heap()->collect(GCCause::_z_allocation_stall);

      // Wait for allocation to complete or fail
      page = request.wait();
    } while (page == gc_marker);

    {
      //
      // We grab the lock here for two different reasons:
      //
      // 1) Guard deletion of underlying semaphore. This is a workaround for
      // a bug in sem_post() in glibc < 2.21, where it's not safe to destroy
      // the semaphore immediately after returning from sem_wait(). The
      // reason is that sem_post() can touch the semaphore after a waiting
      // thread have returned from sem_wait(). To avoid this race we are
      // forcing the waiting thread to acquire/release the lock held by the
      // posting thread. https://sourceware.org/bugzilla/show_bug.cgi?id=12674
      //
      // 2) Guard the list of satisfied pages.
      //
      ZLocker<ZLock> locker(&_lock);
      _satisfied.remove(&request);
    }

    event.commit(type, size);
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
    _satisfied.insert_first(request);
    request->satisfy(page);
  }
}

void ZPageAllocator::free_page(ZPage* page, bool reclaimed) {
  ZLocker<ZLock> locker(&_lock);

  // Update used statistics
  decrease_used(page->size(), reclaimed);

  // Set time when last used
  page->set_last_used();

  // Cache page
  _cache.free_page(page);

  // Try satisfy blocked allocations
  satisfy_alloc_queue();
}

size_t ZPageAllocator::flush_cache(ZPageCacheFlushClosure* cl, bool for_allocation) {
  EventZPageCacheFlush event;

  ZList<ZPage> list;

  // Flush pages
  _cache.flush(cl, &list);

  const size_t overflushed = cl->overflushed();
  if (overflushed > 0) {
    // Overflushed, keep part of last page
    ZPage* const page = list.last()->split(overflushed);
    _cache.free_page(page);
  }

  // Destroy pages
  size_t flushed = 0;
  for (ZPage* page = list.remove_first(); page != NULL; page = list.remove_first()) {
    flushed += page->size();
    destroy_page(page);
  }

  // Send event
  event.commit(flushed, for_allocation);

  return flushed;
}

class ZPageCacheFlushForAllocationClosure : public ZPageCacheFlushClosure {
public:
  ZPageCacheFlushForAllocationClosure(size_t requested) :
      ZPageCacheFlushClosure(requested) {}

  virtual bool do_page(const ZPage* page) {
    if (_flushed < _requested) {
      // Flush page
      _flushed += page->size();
      return true;
    }

    // Don't flush page
    return false;
  }
};

void ZPageAllocator::flush_cache_for_allocation(size_t requested) {
  assert(requested <= _cache.available(), "Invalid request");

  // Flush pages
  ZPageCacheFlushForAllocationClosure cl(requested);
  const size_t flushed = flush_cache(&cl, true /* for_allocation */);

  assert(requested == flushed, "Failed to flush");

  const size_t cached_after = _cache.available();
  const size_t cached_before = cached_after + flushed;

  log_info(gc, heap)("Page Cache: " SIZE_FORMAT "M(%.0f%%)->" SIZE_FORMAT "M(%.0f%%), "
                     "Flushed: " SIZE_FORMAT "M",
                     cached_before / M, percent_of(cached_before, max_capacity()),
                     cached_after / M, percent_of(cached_after, max_capacity()),
                     flushed / M);

  // Update statistics
  ZStatInc(ZCounterPageCacheFlush, flushed);
}

class ZPageCacheFlushForUncommitClosure : public ZPageCacheFlushClosure {
private:
  const uint64_t _now;
  const uint64_t _delay;
  uint64_t       _timeout;

public:
  ZPageCacheFlushForUncommitClosure(size_t requested, uint64_t delay) :
      ZPageCacheFlushClosure(requested),
      _now(os::elapsedTime()),
      _delay(delay),
      _timeout(_delay) {}

  virtual bool do_page(const ZPage* page) {
    const uint64_t expires = page->last_used() + _delay;
    const uint64_t timeout = expires - MIN2(expires, _now);

    if (_flushed < _requested && timeout == 0) {
      // Flush page
      _flushed += page->size();
      return true;
    }

    // Record shortest non-expired timeout
    _timeout = MIN2(_timeout, timeout);

    // Don't flush page
    return false;
  }

  uint64_t timeout() const {
    return _timeout;
  }
};

uint64_t ZPageAllocator::uncommit(uint64_t delay) {
  // Set the default timeout, when no pages are found in the
  // cache or when uncommit is disabled, equal to the delay.
  uint64_t timeout = delay;

  if (!_uncommit) {
    // Disabled
    return timeout;
  }

  EventZUncommit event;
  size_t capacity_before;
  size_t capacity_after;
  size_t uncommitted;

  {
    SuspendibleThreadSetJoiner joiner;
    ZLocker<ZLock> locker(&_lock);

    // Don't flush more than we will uncommit. Never uncommit
    // the reserve, and never uncommit below min capacity.
    const size_t needed = MIN2(_used + _max_reserve, _current_max_capacity);
    const size_t guarded = MAX2(needed, _min_capacity);
    const size_t uncommittable = _capacity - guarded;
    const size_t uncached_available = _capacity - _used - _cache.available();
    size_t uncommit = MIN2(uncommittable, uncached_available);
    const size_t flush = uncommittable - uncommit;

    if (flush > 0) {
      // Flush pages to uncommit
      ZPageCacheFlushForUncommitClosure cl(flush, delay);
      uncommit += flush_cache(&cl, false /* for_allocation */);
      timeout = cl.timeout();
    }

    // Uncommit
    uncommitted = _physical.uncommit(uncommit);
    _capacity -= uncommitted;

    capacity_after = _capacity;
    capacity_before = capacity_after + uncommitted;
  }

  if (uncommitted > 0) {
    log_info(gc, heap)("Capacity: " SIZE_FORMAT "M(%.0f%%)->" SIZE_FORMAT "M(%.0f%%), "
                       "Uncommitted: " SIZE_FORMAT "M",
                       capacity_before / M, percent_of(capacity_before, max_capacity()),
                       capacity_after / M, percent_of(capacity_after, max_capacity()),
                       uncommitted / M);

    // Send event
    event.commit(capacity_before, capacity_after, uncommitted);

    // Update statistics
    ZStatInc(ZCounterUncommit, uncommitted);
  }

  return timeout;
}

void ZPageAllocator::enable_deferred_delete() const {
  _safe_delete.enable_deferred_delete();
}

void ZPageAllocator::disable_deferred_delete() const {
  _safe_delete.disable_deferred_delete();
}

void ZPageAllocator::debug_map_page(const ZPage* page) const {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");
  _physical.debug_map(page->physical_memory(), page->start());
}

void ZPageAllocator::debug_unmap_page(const ZPage* page) const {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");
  _physical.debug_unmap(page->physical_memory(), page->start());
}

void ZPageAllocator::pages_do(ZPageClosure* cl) const {
  ZListIterator<ZPageAllocRequest> iter(&_satisfied);
  for (ZPageAllocRequest* request; iter.next(&request);) {
    const ZPage* const page = request->peek();
    if (page != NULL) {
      cl->do_page(page);
    }
  }

  _cache.pages_do(cl);
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
    _queue.remove(request);
    _satisfied.insert_first(request);
    request->satisfy(NULL);
  }
}
