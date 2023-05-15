/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/x/xArray.inline.hpp"
#include "gc/x/xCollectedHeap.hpp"
#include "gc/x/xFuture.inline.hpp"
#include "gc/x/xGlobals.hpp"
#include "gc/x/xLock.inline.hpp"
#include "gc/x/xPage.inline.hpp"
#include "gc/x/xPageAllocator.inline.hpp"
#include "gc/x/xPageCache.hpp"
#include "gc/x/xSafeDelete.inline.hpp"
#include "gc/x/xStat.hpp"
#include "gc/x/xTask.hpp"
#include "gc/x/xUncommitter.hpp"
#include "gc/x/xUnmapper.hpp"
#include "gc/x/xWorkers.hpp"
#include "jfr/jfrEvents.hpp"
#include "logging/log.hpp"
#include "runtime/globals.hpp"
#include "runtime/init.hpp"
#include "runtime/java.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

static const XStatCounter       XCounterAllocationRate("Memory", "Allocation Rate", XStatUnitBytesPerSecond);
static const XStatCounter       XCounterPageCacheFlush("Memory", "Page Cache Flush", XStatUnitBytesPerSecond);
static const XStatCounter       XCounterDefragment("Memory", "Defragment", XStatUnitOpsPerSecond);
static const XStatCriticalPhase XCriticalPhaseAllocationStall("Allocation Stall");

enum XPageAllocationStall {
  XPageAllocationStallSuccess,
  XPageAllocationStallFailed,
  XPageAllocationStallStartGC
};

class XPageAllocation : public StackObj {
  friend class XList<XPageAllocation>;

private:
  const uint8_t                 _type;
  const size_t                  _size;
  const XAllocationFlags        _flags;
  const uint32_t                _seqnum;
  size_t                        _flushed;
  size_t                        _committed;
  XList<XPage>                  _pages;
  XListNode<XPageAllocation>    _node;
  XFuture<XPageAllocationStall> _stall_result;

public:
  XPageAllocation(uint8_t type, size_t size, XAllocationFlags flags) :
      _type(type),
      _size(size),
      _flags(flags),
      _seqnum(XGlobalSeqNum),
      _flushed(0),
      _committed(0),
      _pages(),
      _node(),
      _stall_result() {}

  uint8_t type() const {
    return _type;
  }

  size_t size() const {
    return _size;
  }

  XAllocationFlags flags() const {
    return _flags;
  }

  uint32_t seqnum() const {
    return _seqnum;
  }

  size_t flushed() const {
    return _flushed;
  }

  void set_flushed(size_t flushed) {
    _flushed = flushed;
  }

  size_t committed() const {
    return _committed;
  }

  void set_committed(size_t committed) {
    _committed = committed;
  }

  XPageAllocationStall wait() {
    return _stall_result.get();
  }

  XList<XPage>* pages() {
    return &_pages;
  }

  void satisfy(XPageAllocationStall result) {
    _stall_result.set(result);
  }
};

XPageAllocator::XPageAllocator(XWorkers* workers,
                               size_t min_capacity,
                               size_t initial_capacity,
                               size_t max_capacity) :
    _lock(),
    _cache(),
    _virtual(max_capacity),
    _physical(max_capacity),
    _min_capacity(min_capacity),
    _max_capacity(max_capacity),
    _current_max_capacity(max_capacity),
    _capacity(0),
    _claimed(0),
    _used(0),
    _used_high(0),
    _used_low(0),
    _reclaimed(0),
    _stalled(),
    _nstalled(0),
    _satisfied(),
    _unmapper(new XUnmapper(this)),
    _uncommitter(new XUncommitter(this)),
    _safe_delete(),
    _initialized(false) {

  if (!_virtual.is_initialized() || !_physical.is_initialized()) {
    return;
  }

  log_info_p(gc, init)("Min Capacity: " SIZE_FORMAT "M", min_capacity / M);
  log_info_p(gc, init)("Initial Capacity: " SIZE_FORMAT "M", initial_capacity / M);
  log_info_p(gc, init)("Max Capacity: " SIZE_FORMAT "M", max_capacity / M);
  if (XPageSizeMedium > 0) {
    log_info_p(gc, init)("Medium Page Size: " SIZE_FORMAT "M", XPageSizeMedium / M);
  } else {
    log_info_p(gc, init)("Medium Page Size: N/A");
  }
  log_info_p(gc, init)("Pre-touch: %s", AlwaysPreTouch ? "Enabled" : "Disabled");

  // Warn if system limits could stop us from reaching max capacity
  _physical.warn_commit_limits(max_capacity);

  // Check if uncommit should and can be enabled
  _physical.try_enable_uncommit(min_capacity, max_capacity);

  // Pre-map initial capacity
  if (!prime_cache(workers, initial_capacity)) {
    log_error_p(gc)("Failed to allocate initial Java heap (" SIZE_FORMAT "M)", initial_capacity / M);
    return;
  }

  // Successfully initialized
  _initialized = true;
}

class XPreTouchTask : public XTask {
private:
  const XPhysicalMemoryManager* const _physical;
  volatile uintptr_t                  _start;
  const uintptr_t                     _end;

public:
  XPreTouchTask(const XPhysicalMemoryManager* physical, uintptr_t start, uintptr_t end) :
      XTask("XPreTouchTask"),
      _physical(physical),
      _start(start),
      _end(end) {}

  virtual void work() {
    for (;;) {
      // Get granule offset
      const size_t size = XGranuleSize;
      const uintptr_t offset = Atomic::fetch_then_add(&_start, size);
      if (offset >= _end) {
        // Done
        break;
      }

      // Pre-touch granule
      _physical->pretouch(offset, size);
    }
  }
};

bool XPageAllocator::prime_cache(XWorkers* workers, size_t size) {
  XAllocationFlags flags;

  flags.set_non_blocking();
  flags.set_low_address();

  XPage* const page = alloc_page(XPageTypeLarge, size, flags);
  if (page == NULL) {
    return false;
  }

  if (AlwaysPreTouch) {
    // Pre-touch page
    XPreTouchTask task(&_physical, page->start(), page->end());
    workers->run_all(&task);
  }

  free_page(page, false /* reclaimed */);

  return true;
}

bool XPageAllocator::is_initialized() const {
  return _initialized;
}

size_t XPageAllocator::min_capacity() const {
  return _min_capacity;
}

size_t XPageAllocator::max_capacity() const {
  return _max_capacity;
}

size_t XPageAllocator::soft_max_capacity() const {
  // Note that SoftMaxHeapSize is a manageable flag
  const size_t soft_max_capacity = Atomic::load(&SoftMaxHeapSize);
  const size_t current_max_capacity = Atomic::load(&_current_max_capacity);
  return MIN2(soft_max_capacity, current_max_capacity);
}

size_t XPageAllocator::capacity() const {
  return Atomic::load(&_capacity);
}

size_t XPageAllocator::used() const {
  return Atomic::load(&_used);
}

size_t XPageAllocator::unused() const {
  const ssize_t capacity = (ssize_t)Atomic::load(&_capacity);
  const ssize_t used = (ssize_t)Atomic::load(&_used);
  const ssize_t claimed = (ssize_t)Atomic::load(&_claimed);
  const ssize_t unused = capacity - used - claimed;
  return unused > 0 ? (size_t)unused : 0;
}

XPageAllocatorStats XPageAllocator::stats() const {
  XLocker<XLock> locker(&_lock);
  return XPageAllocatorStats(_min_capacity,
                             _max_capacity,
                             soft_max_capacity(),
                             _capacity,
                             _used,
                             _used_high,
                             _used_low,
                             _reclaimed);
}

void XPageAllocator::reset_statistics() {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");
  _reclaimed = 0;
  _used_high = _used_low = _used;
  _nstalled = 0;
}

size_t XPageAllocator::increase_capacity(size_t size) {
  const size_t increased = MIN2(size, _current_max_capacity - _capacity);

  if (increased > 0) {
    // Update atomically since we have concurrent readers
    Atomic::add(&_capacity, increased);

    // Record time of last commit. When allocation, we prefer increasing
    // the capacity over flushing the cache. That means there could be
    // expired pages in the cache at this time. However, since we are
    // increasing the capacity we are obviously in need of committed
    // memory and should therefore not be uncommitting memory.
    _cache.set_last_commit();
  }

  return increased;
}

void XPageAllocator::decrease_capacity(size_t size, bool set_max_capacity) {
  // Update atomically since we have concurrent readers
  Atomic::sub(&_capacity, size);

  if (set_max_capacity) {
    // Adjust current max capacity to avoid further attempts to increase capacity
    log_error_p(gc)("Forced to lower max Java heap size from "
                    SIZE_FORMAT "M(%.0f%%) to " SIZE_FORMAT "M(%.0f%%)",
                    _current_max_capacity / M, percent_of(_current_max_capacity, _max_capacity),
                    _capacity / M, percent_of(_capacity, _max_capacity));

    // Update atomically since we have concurrent readers
    Atomic::store(&_current_max_capacity, _capacity);
  }
}

void XPageAllocator::increase_used(size_t size, bool worker_relocation) {
  if (worker_relocation) {
    // Allocating a page for the purpose of worker relocation has
    // a negative contribution to the number of reclaimed bytes.
    _reclaimed -= size;
  }

  // Update atomically since we have concurrent readers
  const size_t used = Atomic::add(&_used, size);
  if (used > _used_high) {
    _used_high = used;
  }
}

void XPageAllocator::decrease_used(size_t size, bool reclaimed) {
  // Only pages explicitly released with the reclaimed flag set
  // counts as reclaimed bytes. This flag is true when we release
  // a page after relocation, and is false when we release a page
  // to undo an allocation.
  if (reclaimed) {
    _reclaimed += size;
  }

  // Update atomically since we have concurrent readers
  const size_t used = Atomic::sub(&_used, size);
  if (used < _used_low) {
    _used_low = used;
  }
}

bool XPageAllocator::commit_page(XPage* page) {
  // Commit physical memory
  return _physical.commit(page->physical_memory());
}

void XPageAllocator::uncommit_page(XPage* page) {
  if (!ZUncommit) {
    return;
  }

  // Uncommit physical memory
  _physical.uncommit(page->physical_memory());
}

void XPageAllocator::map_page(const XPage* page) const {
  // Map physical memory
  _physical.map(page->start(), page->physical_memory());
}

void XPageAllocator::unmap_page(const XPage* page) const {
  // Unmap physical memory
  _physical.unmap(page->start(), page->size());
}

void XPageAllocator::destroy_page(XPage* page) {
  // Free virtual memory
  _virtual.free(page->virtual_memory());

  // Free physical memory
  _physical.free(page->physical_memory());

  // Delete page safely
  _safe_delete(page);
}

bool XPageAllocator::is_alloc_allowed(size_t size) const {
  const size_t available = _current_max_capacity - _used - _claimed;
  return available >= size;
}

bool XPageAllocator::alloc_page_common_inner(uint8_t type, size_t size, XList<XPage>* pages) {
  if (!is_alloc_allowed(size)) {
    // Out of memory
    return false;
  }

  // Try allocate from the page cache
  XPage* const page = _cache.alloc_page(type, size);
  if (page != NULL) {
    // Success
    pages->insert_last(page);
    return true;
  }

  // Try increase capacity
  const size_t increased = increase_capacity(size);
  if (increased < size) {
    // Could not increase capacity enough to satisfy the allocation
    // completely. Flush the page cache to satisfy the remainder.
    const size_t remaining = size - increased;
    _cache.flush_for_allocation(remaining, pages);
  }

  // Success
  return true;
}

bool XPageAllocator::alloc_page_common(XPageAllocation* allocation) {
  const uint8_t type = allocation->type();
  const size_t size = allocation->size();
  const XAllocationFlags flags = allocation->flags();
  XList<XPage>* const pages = allocation->pages();

  if (!alloc_page_common_inner(type, size, pages)) {
    // Out of memory
    return false;
  }

  // Updated used statistics
  increase_used(size, flags.worker_relocation());

  // Success
  return true;
}

static void check_out_of_memory_during_initialization() {
  if (!is_init_completed()) {
    vm_exit_during_initialization("java.lang.OutOfMemoryError", "Java heap too small");
  }
}

bool XPageAllocator::alloc_page_stall(XPageAllocation* allocation) {
  XStatTimer timer(XCriticalPhaseAllocationStall);
  EventZAllocationStall event;
  XPageAllocationStall result;

  // We can only block if the VM is fully initialized
  check_out_of_memory_during_initialization();

  // Increment stalled counter
  Atomic::inc(&_nstalled);

  do {
    // Start asynchronous GC
    XCollectedHeap::heap()->collect(GCCause::_z_allocation_stall);

    // Wait for allocation to complete, fail or request a GC
    result = allocation->wait();
  } while (result == XPageAllocationStallStartGC);

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
    XLocker<XLock> locker(&_lock);
    _satisfied.remove(allocation);
  }

  // Send event
  event.commit(allocation->type(), allocation->size());

  return (result == XPageAllocationStallSuccess);
}

bool XPageAllocator::alloc_page_or_stall(XPageAllocation* allocation) {
  {
    XLocker<XLock> locker(&_lock);

    if (alloc_page_common(allocation)) {
      // Success
      return true;
    }

    // Failed
    if (allocation->flags().non_blocking()) {
      // Don't stall
      return false;
    }

    // Enqueue allocation request
    _stalled.insert_last(allocation);
  }

  // Stall
  return alloc_page_stall(allocation);
}

XPage* XPageAllocator::alloc_page_create(XPageAllocation* allocation) {
  const size_t size = allocation->size();

  // Allocate virtual memory. To make error handling a lot more straight
  // forward, we allocate virtual memory before destroying flushed pages.
  // Flushed pages are also unmapped and destroyed asynchronously, so we
  // can't immediately reuse that part of the address space anyway.
  const XVirtualMemory vmem = _virtual.alloc(size, allocation->flags().low_address());
  if (vmem.is_null()) {
    log_error(gc)("Out of address space");
    return NULL;
  }

  XPhysicalMemory pmem;
  size_t flushed = 0;

  // Harvest physical memory from flushed pages
  XListRemoveIterator<XPage> iter(allocation->pages());
  for (XPage* page; iter.next(&page);) {
    flushed += page->size();

    // Harvest flushed physical memory
    XPhysicalMemory& fmem = page->physical_memory();
    pmem.add_segments(fmem);
    fmem.remove_segments();

    // Unmap and destroy page
    _unmapper->unmap_and_destroy_page(page);
  }

  if (flushed > 0) {
    allocation->set_flushed(flushed);

    // Update statistics
    XStatInc(XCounterPageCacheFlush, flushed);
    log_debug(gc, heap)("Page Cache Flushed: " SIZE_FORMAT "M", flushed / M);
  }

  // Allocate any remaining physical memory. Capacity and used has
  // already been adjusted, we just need to fetch the memory, which
  // is guaranteed to succeed.
  if (flushed < size) {
    const size_t remaining = size - flushed;
    allocation->set_committed(remaining);
    _physical.alloc(pmem, remaining);
  }

  // Create new page
  return new XPage(allocation->type(), vmem, pmem);
}

bool XPageAllocator::should_defragment(const XPage* page) const {
  // A small page can end up at a high address (second half of the address space)
  // if we've split a larger page or we have a constrained address space. To help
  // fight address space fragmentation we remap such pages to a lower address, if
  // a lower address is available.
  return page->type() == XPageTypeSmall &&
         page->start() >= _virtual.reserved() / 2 &&
         page->start() > _virtual.lowest_available_address();
}

bool XPageAllocator::is_alloc_satisfied(XPageAllocation* allocation) const {
  // The allocation is immediately satisfied if the list of pages contains
  // exactly one page, with the type and size that was requested. However,
  // even if the allocation is immediately satisfied we might still want to
  // return false here to force the page to be remapped to fight address
  // space fragmentation.

  if (allocation->pages()->size() != 1) {
    // Not a single page
    return false;
  }

  const XPage* const page = allocation->pages()->first();
  if (page->type() != allocation->type() ||
      page->size() != allocation->size()) {
    // Wrong type or size
    return false;
  }

  if (should_defragment(page)) {
    // Defragment address space
    XStatInc(XCounterDefragment);
    return false;
  }

  // Allocation immediately satisfied
  return true;
}

XPage* XPageAllocator::alloc_page_finalize(XPageAllocation* allocation) {
  // Fast path
  if (is_alloc_satisfied(allocation)) {
    return allocation->pages()->remove_first();
  }

  // Slow path
  XPage* const page = alloc_page_create(allocation);
  if (page == NULL) {
    // Out of address space
    return NULL;
  }

  // Commit page
  if (commit_page(page)) {
    // Success
    map_page(page);
    return page;
  }

  // Failed or partially failed. Split of any successfully committed
  // part of the page into a new page and insert it into list of pages,
  // so that it will be re-inserted into the page cache.
  XPage* const committed_page = page->split_committed();
  destroy_page(page);

  if (committed_page != NULL) {
    map_page(committed_page);
    allocation->pages()->insert_last(committed_page);
  }

  return NULL;
}

void XPageAllocator::alloc_page_failed(XPageAllocation* allocation) {
  XLocker<XLock> locker(&_lock);

  size_t freed = 0;

  // Free any allocated/flushed pages
  XListRemoveIterator<XPage> iter(allocation->pages());
  for (XPage* page; iter.next(&page);) {
    freed += page->size();
    free_page_inner(page, false /* reclaimed */);
  }

  // Adjust capacity and used to reflect the failed capacity increase
  const size_t remaining = allocation->size() - freed;
  decrease_used(remaining, false /* reclaimed */);
  decrease_capacity(remaining, true /* set_max_capacity */);

  // Try satisfy stalled allocations
  satisfy_stalled();
}

XPage* XPageAllocator::alloc_page(uint8_t type, size_t size, XAllocationFlags flags) {
  EventZPageAllocation event;

retry:
  XPageAllocation allocation(type, size, flags);

  // Allocate one or more pages from the page cache. If the allocation
  // succeeds but the returned pages don't cover the complete allocation,
  // then finalize phase is allowed to allocate the remaining memory
  // directly from the physical memory manager. Note that this call might
  // block in a safepoint if the non-blocking flag is not set.
  if (!alloc_page_or_stall(&allocation)) {
    // Out of memory
    return NULL;
  }

  XPage* const page = alloc_page_finalize(&allocation);
  if (page == NULL) {
    // Failed to commit or map. Clean up and retry, in the hope that
    // we can still allocate by flushing the page cache (more aggressively).
    alloc_page_failed(&allocation);
    goto retry;
  }

  // Reset page. This updates the page's sequence number and must
  // be done after we potentially blocked in a safepoint (stalled)
  // where the global sequence number was updated.
  page->reset();

  // Update allocation statistics. Exclude worker relocations to avoid
  // artificial inflation of the allocation rate during relocation.
  if (!flags.worker_relocation() && is_init_completed()) {
    // Note that there are two allocation rate counters, which have
    // different purposes and are sampled at different frequencies.
    const size_t bytes = page->size();
    XStatInc(XCounterAllocationRate, bytes);
    XStatInc(XStatAllocRate::counter(), bytes);
  }

  // Send event
  event.commit(type, size, allocation.flushed(), allocation.committed(),
               page->physical_memory().nsegments(), flags.non_blocking());

  return page;
}

void XPageAllocator::satisfy_stalled() {
  for (;;) {
    XPageAllocation* const allocation = _stalled.first();
    if (allocation == NULL) {
      // Allocation queue is empty
      return;
    }

    if (!alloc_page_common(allocation)) {
      // Allocation could not be satisfied, give up
      return;
    }

    // Allocation succeeded, dequeue and satisfy allocation request.
    // Note that we must dequeue the allocation request first, since
    // it will immediately be deallocated once it has been satisfied.
    _stalled.remove(allocation);
    _satisfied.insert_last(allocation);
    allocation->satisfy(XPageAllocationStallSuccess);
  }
}

void XPageAllocator::free_page_inner(XPage* page, bool reclaimed) {
  // Update used statistics
  decrease_used(page->size(), reclaimed);

  // Set time when last used
  page->set_last_used();

  // Cache page
  _cache.free_page(page);
}

void XPageAllocator::free_page(XPage* page, bool reclaimed) {
  XLocker<XLock> locker(&_lock);

  // Free page
  free_page_inner(page, reclaimed);

  // Try satisfy stalled allocations
  satisfy_stalled();
}

void XPageAllocator::free_pages(const XArray<XPage*>* pages, bool reclaimed) {
  XLocker<XLock> locker(&_lock);

  // Free pages
  XArrayIterator<XPage*> iter(pages);
  for (XPage* page; iter.next(&page);) {
    free_page_inner(page, reclaimed);
  }

  // Try satisfy stalled allocations
  satisfy_stalled();
}

size_t XPageAllocator::uncommit(uint64_t* timeout) {
  // We need to join the suspendible thread set while manipulating capacity and
  // used, to make sure GC safepoints will have a consistent view. However, when
  // ZVerifyViews is enabled we need to join at a broader scope to also make sure
  // we don't change the address good mask after pages have been flushed, and
  // thereby made invisible to pages_do(), but before they have been unmapped.
  SuspendibleThreadSetJoiner joiner(ZVerifyViews);
  XList<XPage> pages;
  size_t flushed;

  {
    SuspendibleThreadSetJoiner joiner(!ZVerifyViews);
    XLocker<XLock> locker(&_lock);

    // Never uncommit below min capacity. We flush out and uncommit chunks at
    // a time (~0.8% of the max capacity, but at least one granule and at most
    // 256M), in case demand for memory increases while we are uncommitting.
    const size_t retain = MAX2(_used, _min_capacity);
    const size_t release = _capacity - retain;
    const size_t limit = MIN2(align_up(_current_max_capacity >> 7, XGranuleSize), 256 * M);
    const size_t flush = MIN2(release, limit);

    // Flush pages to uncommit
    flushed = _cache.flush_for_uncommit(flush, &pages, timeout);
    if (flushed == 0) {
      // Nothing flushed
      return 0;
    }

    // Record flushed pages as claimed
    Atomic::add(&_claimed, flushed);
  }

  // Unmap, uncommit, and destroy flushed pages
  XListRemoveIterator<XPage> iter(&pages);
  for (XPage* page; iter.next(&page);) {
    unmap_page(page);
    uncommit_page(page);
    destroy_page(page);
  }

  {
    SuspendibleThreadSetJoiner joiner(!ZVerifyViews);
    XLocker<XLock> locker(&_lock);

    // Adjust claimed and capacity to reflect the uncommit
    Atomic::sub(&_claimed, flushed);
    decrease_capacity(flushed, false /* set_max_capacity */);
  }

  return flushed;
}

void XPageAllocator::enable_deferred_delete() const {
  _safe_delete.enable_deferred_delete();
}

void XPageAllocator::disable_deferred_delete() const {
  _safe_delete.disable_deferred_delete();
}

void XPageAllocator::debug_map_page(const XPage* page) const {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");
  _physical.debug_map(page->start(), page->physical_memory());
}

void XPageAllocator::debug_unmap_page(const XPage* page) const {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");
  _physical.debug_unmap(page->start(), page->size());
}

void XPageAllocator::pages_do(XPageClosure* cl) const {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");

  XListIterator<XPageAllocation> iter_satisfied(&_satisfied);
  for (XPageAllocation* allocation; iter_satisfied.next(&allocation);) {
    XListIterator<XPage> iter_pages(allocation->pages());
    for (XPage* page; iter_pages.next(&page);) {
      cl->do_page(page);
    }
  }

  _cache.pages_do(cl);
}

bool XPageAllocator::has_alloc_stalled() const {
  return Atomic::load(&_nstalled) != 0;
}

void XPageAllocator::check_out_of_memory() {
  XLocker<XLock> locker(&_lock);

  // Fail allocation requests that were enqueued before the
  // last GC cycle started, otherwise start a new GC cycle.
  for (XPageAllocation* allocation = _stalled.first(); allocation != NULL; allocation = _stalled.first()) {
    if (allocation->seqnum() == XGlobalSeqNum) {
      // Start a new GC cycle, keep allocation requests enqueued
      allocation->satisfy(XPageAllocationStallStartGC);
      return;
    }

    // Out of memory, fail allocation request
    _stalled.remove(allocation);
    _satisfied.insert_last(allocation);
    allocation->satisfy(XPageAllocationStallFailed);
  }
}

void XPageAllocator::threads_do(ThreadClosure* tc) const {
  tc->do_thread(_unmapper);
  tc->do_thread(_uncommitter);
}
