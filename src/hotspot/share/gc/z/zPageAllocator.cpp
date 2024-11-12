/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zArray.inline.hpp"
#include "gc/z/zDriver.hpp"
#include "gc/z/zFuture.inline.hpp"
#include "gc/z/zGeneration.inline.hpp"
#include "gc/z/zGenerationId.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zLargePages.inline.hpp"
#include "gc/z/zLock.inline.hpp"
#include "gc/z/zPage.inline.hpp"
#include "gc/z/zPageAge.hpp"
#include "gc/z/zPageAllocator.inline.hpp"
#include "gc/z/zPageCache.hpp"
#include "gc/z/zSafeDelete.inline.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zTask.hpp"
#include "gc/z/zUncommitter.hpp"
#include "gc/z/zUnmapper.hpp"
#include "gc/z/zWorkers.hpp"
#include "jfr/jfrEvents.hpp"
#include "logging/log.hpp"
#include "runtime/globals.hpp"
#include "runtime/init.hpp"
#include "runtime/java.hpp"
#include "runtime/os.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

static const ZStatCounter       ZCounterMutatorAllocationRate("Memory", "Allocation Rate", ZStatUnitBytesPerSecond);
static const ZStatCounter       ZCounterPageCacheFlush("Memory", "Page Cache Flush", ZStatUnitBytesPerSecond);
static const ZStatCounter       ZCounterDefragment("Memory", "Defragment", ZStatUnitOpsPerSecond);
static const ZStatCriticalPhase ZCriticalPhaseAllocationStall("Allocation Stall");

ZSafePageRecycle::ZSafePageRecycle(ZPageAllocator* page_allocator)
  : _page_allocator(page_allocator),
    _unsafe_to_recycle() {}

void ZSafePageRecycle::activate() {
  _unsafe_to_recycle.activate();
}

void ZSafePageRecycle::deactivate() {
  auto delete_function = [&](ZPage* page) {
    _page_allocator->safe_destroy_page(page);
  };

  _unsafe_to_recycle.deactivate_and_apply(delete_function);
}

ZPage* ZSafePageRecycle::register_and_clone_if_activated(ZPage* page) {
  if (!_unsafe_to_recycle.is_activated()) {
    // The page has no concurrent readers.
    // Recycle original page.
    return page;
  }

  // The page could have concurrent readers.
  // It would be unsafe to recycle this page at this point.

  // As soon as the page is added to _unsafe_to_recycle, it
  // must not be used again. Hence, the extra double-checked
  // locking to only clone the page if it is believed to be
  // unsafe to recycle the page.
  ZPage* const cloned_page = page->clone_limited();
  if (!_unsafe_to_recycle.add_if_activated(page)) {
    // It became safe to recycle the page after the is_activated check
    delete cloned_page;
    return page;
  }

  // The original page has been registered to be deleted by another thread.
  // Recycle the cloned page.
  return cloned_page;
}

class ZPageAllocation : public StackObj {
  friend class ZList<ZPageAllocation>;

private:
  const ZPageType            _type;
  const size_t               _size;
  const ZAllocationFlags     _flags;
  const uint32_t             _young_seqnum;
  const uint32_t             _old_seqnum;
  size_t                     _flushed;
  size_t                     _committed;
  ZList<ZPage>               _pages;
  ZListNode<ZPageAllocation> _node;
  ZFuture<bool>              _stall_result;

public:
  ZPageAllocation(ZPageType type, size_t size, ZAllocationFlags flags)
    : _type(type),
      _size(size),
      _flags(flags),
      _young_seqnum(ZGeneration::young()->seqnum()),
      _old_seqnum(ZGeneration::old()->seqnum()),
      _flushed(0),
      _committed(0),
      _pages(),
      _node(),
      _stall_result() {}

  ZPageType type() const {
    return _type;
  }

  size_t size() const {
    return _size;
  }

  ZAllocationFlags flags() const {
    return _flags;
  }

  uint32_t young_seqnum() const {
    return _young_seqnum;
  }

  uint32_t old_seqnum() const {
    return _old_seqnum;
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

  bool wait() {
    return _stall_result.get();
  }

  ZList<ZPage>* pages() {
    return &_pages;
  }

  void satisfy(bool result) {
    _stall_result.set(result);
  }

  bool gc_relocation() const {
    return _flags.gc_relocation();
  }
};

ZPageAllocator::ZPageAllocator(size_t min_capacity,
                               size_t initial_capacity,
                               size_t soft_max_capacity,
                               size_t max_capacity)
  : _lock(),
    _cache(),
    _virtual(max_capacity),
    _physical(max_capacity),
    _min_capacity(min_capacity),
    _initial_capacity(initial_capacity),
    _max_capacity(max_capacity),
    _current_max_capacity(max_capacity),
    _capacity(0),
    _claimed(0),
    _used(0),
    _used_generations{0, 0},
    _collection_stats{{0, 0}, {0, 0}},
    _stalled(),
    _unmapper(new ZUnmapper(this)),
    _uncommitter(new ZUncommitter(this)),
    _safe_destroy(),
    _safe_recycle(this),
    _initialized(false) {

  if (!_virtual.is_initialized() || !_physical.is_initialized()) {
    return;
  }

  log_info_p(gc, init)("Min Capacity: " SIZE_FORMAT "M", min_capacity / M);
  log_info_p(gc, init)("Initial Capacity: " SIZE_FORMAT "M", initial_capacity / M);
  log_info_p(gc, init)("Max Capacity: " SIZE_FORMAT "M", max_capacity / M);
  log_info_p(gc, init)("Soft Max Capacity: " SIZE_FORMAT "M", soft_max_capacity / M);
  if (ZPageSizeMedium > 0) {
    log_info_p(gc, init)("Medium Page Size: " SIZE_FORMAT "M", ZPageSizeMedium / M);
  } else {
    log_info_p(gc, init)("Medium Page Size: N/A");
  }
  log_info_p(gc, init)("Pre-touch: %s", AlwaysPreTouch ? "Enabled" : "Disabled");

  // Warn if system limits could stop us from reaching max capacity
  _physical.warn_commit_limits(max_capacity);

  // Check if uncommit should and can be enabled
  _physical.try_enable_uncommit(min_capacity, max_capacity);

  // Successfully initialized
  _initialized = true;
}

bool ZPageAllocator::is_initialized() const {
  return _initialized;
}

class ZPreTouchTask : public ZTask {
private:
  volatile uintptr_t _current;
  const uintptr_t    _end;

  static void pretouch(zaddress zaddr, size_t size) {
    const uintptr_t addr = untype(zaddr);
    const size_t page_size = ZLargePages::is_explicit() ? ZGranuleSize : os::vm_page_size();
    os::pretouch_memory((void*)addr, (void*)(addr + size), page_size);
  }

public:
  ZPreTouchTask(zoffset start, zoffset_end end)
    : ZTask("ZPreTouchTask"),
      _current(untype(start)),
      _end(untype(end)) {}

  virtual void work() {
    const size_t size = ZGranuleSize;

    for (;;) {
      // Claim an offset for this thread
      const uintptr_t claimed = Atomic::fetch_then_add(&_current, size);
      if (claimed >= _end) {
        // Done
        break;
      }

      // At this point we know that we have a valid zoffset / zaddress.
      const zoffset offset = to_zoffset(claimed);
      const zaddress addr = ZOffset::address(offset);

      // Pre-touch the granule
      pretouch(addr, size);
    }
  }
};

bool ZPageAllocator::prime_cache(ZWorkers* workers, size_t size) {
  ZAllocationFlags flags;
  flags.set_non_blocking();
  flags.set_low_address();

  ZPage* const page = alloc_page(ZPageType::large, size, flags, ZPageAge::eden);
  if (page == nullptr) {
    return false;
  }

  if (AlwaysPreTouch) {
    // Pre-touch page
    ZPreTouchTask task(page->start(), page->end());
    workers->run_all(&task);
  }

  free_page(page, false /* allow_defragment */);

  return true;
}

size_t ZPageAllocator::initial_capacity() const {
  return _initial_capacity;
}

size_t ZPageAllocator::min_capacity() const {
  return _min_capacity;
}

size_t ZPageAllocator::max_capacity() const {
  return _max_capacity;
}

size_t ZPageAllocator::soft_max_capacity() const {
  // Note that SoftMaxHeapSize is a manageable flag
  const size_t soft_max_capacity = Atomic::load(&SoftMaxHeapSize);
  const size_t current_max_capacity = Atomic::load(&_current_max_capacity);
  return MIN2(soft_max_capacity, current_max_capacity);
}

size_t ZPageAllocator::capacity() const {
  return Atomic::load(&_capacity);
}

size_t ZPageAllocator::used() const {
  return Atomic::load(&_used);
}

size_t ZPageAllocator::used_generation(ZGenerationId id) const {
  return Atomic::load(&_used_generations[(int)id]);
}

size_t ZPageAllocator::unused() const {
  const ssize_t capacity = (ssize_t)Atomic::load(&_capacity);
  const ssize_t used = (ssize_t)Atomic::load(&_used);
  const ssize_t claimed = (ssize_t)Atomic::load(&_claimed);
  const ssize_t unused = capacity - used - claimed;
  return unused > 0 ? (size_t)unused : 0;
}

ZPageAllocatorStats ZPageAllocator::stats(ZGeneration* generation) const {
  ZLocker<ZLock> locker(&_lock);
  return ZPageAllocatorStats(_min_capacity,
                             _max_capacity,
                             soft_max_capacity(),
                             _capacity,
                             _used,
                             _collection_stats[(int)generation->id()]._used_high,
                             _collection_stats[(int)generation->id()]._used_low,
                             used_generation(generation->id()),
                             generation->freed(),
                             generation->promoted(),
                             generation->compacted(),
                             _stalled.size());
}

void ZPageAllocator::reset_statistics(ZGenerationId id) {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");
  _collection_stats[(int)id]._used_high = _used;
  _collection_stats[(int)id]._used_low = _used;
}

size_t ZPageAllocator::increase_capacity(size_t size) {
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

void ZPageAllocator::decrease_capacity(size_t size, bool set_max_capacity) {
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

void ZPageAllocator::increase_used(size_t size) {
  // We don't track generation usage here because this page
  // could be allocated by a thread that satisfies a stalling
  // allocation. The stalled thread can wake up and potentially
  // realize that the page alloc should be undone. If the alloc
  // and the undo gets separated by a safepoint, the generation
  // statistics could se a decreasing used value between mark
  // start and mark end.

  // Update atomically since we have concurrent readers
  const size_t used = Atomic::add(&_used, size);

  // Update used high
  for (auto& stats : _collection_stats) {
    if (used > stats._used_high) {
      stats._used_high = used;
    }
  }
}

void ZPageAllocator::decrease_used(size_t size) {
  // Update atomically since we have concurrent readers
  const size_t used = Atomic::sub(&_used, size);

  // Update used low
  for (auto& stats : _collection_stats) {
    if (used < stats._used_low) {
      stats._used_low = used;
    }
  }
}

void ZPageAllocator::increase_used_generation(ZGenerationId id, size_t size) {
  // Update atomically since we have concurrent readers
  Atomic::add(&_used_generations[(int)id], size, memory_order_relaxed);
}

void ZPageAllocator::decrease_used_generation(ZGenerationId id, size_t size) {
  // Update atomically since we have concurrent readers
  Atomic::sub(&_used_generations[(int)id], size, memory_order_relaxed);
}

void ZPageAllocator::promote_used(size_t size) {
  decrease_used_generation(ZGenerationId::young, size);
  increase_used_generation(ZGenerationId::old, size);
}

bool ZPageAllocator::commit_page(ZPage* page) {
  // Commit physical memory
  return _physical.commit(page->physical_memory());
}

void ZPageAllocator::uncommit_page(ZPage* page) {
  if (!ZUncommit) {
    return;
  }

  // Uncommit physical memory
  _physical.uncommit(page->physical_memory());
}

void ZPageAllocator::map_page(const ZPage* page) const {
  // Map physical memory
  _physical.map(page->start(), page->physical_memory());
}

void ZPageAllocator::unmap_page(const ZPage* page) const {
  // Unmap physical memory
  _physical.unmap(page->start(), page->size());
}

void ZPageAllocator::safe_destroy_page(ZPage* page) {
  // Destroy page safely
  _safe_destroy.schedule_delete(page);
}

void ZPageAllocator::destroy_page(ZPage* page) {
  // Free virtual memory
  _virtual.free(page->virtual_memory());

  // Free physical memory
  _physical.free(page->physical_memory());

  // Destroy page safely
  safe_destroy_page(page);
}

bool ZPageAllocator::should_defragment(const ZPage* page) const {
  // A small page can end up at a high address (second half of the address space)
  // if we've split a larger page or we have a constrained address space. To help
  // fight address space fragmentation we remap such pages to a lower address, if
  // a lower address is available.
  return page->type() == ZPageType::small &&
         page->start() >= to_zoffset(_virtual.reserved() / 2) &&
         page->start() > _virtual.lowest_available_address();
}

ZPage* ZPageAllocator::defragment_page(ZPage* page) {
  // Harvest the physical memory (which is committed)
  ZPhysicalMemory pmem;
  ZPhysicalMemory& old_pmem = page->physical_memory();
  pmem.add_segments(old_pmem);
  old_pmem.remove_segments();

  _unmapper->unmap_and_destroy_page(page);

  // Allocate new virtual memory at a low address
  const ZVirtualMemory vmem = _virtual.alloc(pmem.size(), true /* force_low_address */);

  // Create the new page and map it
  ZPage* new_page = new ZPage(ZPageType::small, vmem, pmem);
  map_page(new_page);

  // Update statistics
  ZStatInc(ZCounterDefragment);

  return new_page;
}

bool ZPageAllocator::is_alloc_allowed(size_t size) const {
  const size_t available = _current_max_capacity - _used - _claimed;
  return available >= size;
}

bool ZPageAllocator::alloc_page_common_inner(ZPageType type, size_t size, ZList<ZPage>* pages) {
  if (!is_alloc_allowed(size)) {
    // Out of memory
    return false;
  }

  // Try allocate from the page cache
  ZPage* const page = _cache.alloc_page(type, size);
  if (page != nullptr) {
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

bool ZPageAllocator::alloc_page_common(ZPageAllocation* allocation) {
  const ZPageType type = allocation->type();
  const size_t size = allocation->size();
  const ZAllocationFlags flags = allocation->flags();
  ZList<ZPage>* const pages = allocation->pages();

  if (!alloc_page_common_inner(type, size, pages)) {
    // Out of memory
    return false;
  }

  // Updated used statistics
  increase_used(size);

  // Success
  return true;
}

static void check_out_of_memory_during_initialization() {
  if (!is_init_completed()) {
    vm_exit_during_initialization("java.lang.OutOfMemoryError", "Java heap too small");
  }
}

bool ZPageAllocator::alloc_page_stall(ZPageAllocation* allocation) {
  ZStatTimer timer(ZCriticalPhaseAllocationStall);
  EventZAllocationStall event;

  // We can only block if the VM is fully initialized
  check_out_of_memory_during_initialization();

  // Start asynchronous minor GC
  const ZDriverRequest request(GCCause::_z_allocation_stall, ZYoungGCThreads, 0);
  ZDriver::minor()->collect(request);

  // Wait for allocation to complete or fail
  const bool result = allocation->wait();

  {
    // Guard deletion of underlying semaphore. This is a workaround for
    // a bug in sem_post() in glibc < 2.21, where it's not safe to destroy
    // the semaphore immediately after returning from sem_wait(). The
    // reason is that sem_post() can touch the semaphore after a waiting
    // thread have returned from sem_wait(). To avoid this race we are
    // forcing the waiting thread to acquire/release the lock held by the
    // posting thread. https://sourceware.org/bugzilla/show_bug.cgi?id=12674
    ZLocker<ZLock> locker(&_lock);
  }

  // Send event
  event.commit((u8)allocation->type(), allocation->size());

  return result;
}

bool ZPageAllocator::alloc_page_or_stall(ZPageAllocation* allocation) {
  {
    ZLocker<ZLock> locker(&_lock);

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

ZPage* ZPageAllocator::alloc_page_create(ZPageAllocation* allocation) {
  const size_t size = allocation->size();

  // Allocate virtual memory. To make error handling a lot more straight
  // forward, we allocate virtual memory before destroying flushed pages.
  // Flushed pages are also unmapped and destroyed asynchronously, so we
  // can't immediately reuse that part of the address space anyway.
  const ZVirtualMemory vmem = _virtual.alloc(size, allocation->flags().low_address());
  if (vmem.is_null()) {
    log_error(gc)("Out of address space");
    return nullptr;
  }

  ZPhysicalMemory pmem;
  size_t flushed = 0;

  // Harvest physical memory from flushed pages
  ZListRemoveIterator<ZPage> iter(allocation->pages());
  for (ZPage* page; iter.next(&page);) {
    flushed += page->size();

    // Harvest flushed physical memory
    ZPhysicalMemory& fmem = page->physical_memory();
    pmem.add_segments(fmem);
    fmem.remove_segments();

    // Unmap and destroy page
    _unmapper->unmap_and_destroy_page(page);
  }

  if (flushed > 0) {
    allocation->set_flushed(flushed);

    // Update statistics
    ZStatInc(ZCounterPageCacheFlush, flushed);
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
  return new ZPage(allocation->type(), vmem, pmem);
}

bool ZPageAllocator::is_alloc_satisfied(ZPageAllocation* allocation) const {
  // The allocation is immediately satisfied if the list of pages contains
  // exactly one page, with the type and size that was requested. However,
  // even if the allocation is immediately satisfied we might still want to
  // return false here to force the page to be remapped to fight address
  // space fragmentation.

  if (allocation->pages()->size() != 1) {
    // Not a single page
    return false;
  }

  const ZPage* const page = allocation->pages()->first();
  if (page->type() != allocation->type() ||
      page->size() != allocation->size()) {
    // Wrong type or size
    return false;
  }

  // Allocation immediately satisfied
  return true;
}

ZPage* ZPageAllocator::alloc_page_finalize(ZPageAllocation* allocation) {
  // Fast path
  if (is_alloc_satisfied(allocation)) {
    return allocation->pages()->remove_first();
  }

  // Slow path
  ZPage* const page = alloc_page_create(allocation);
  if (page == nullptr) {
    // Out of address space
    return nullptr;
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
  ZPage* const committed_page = page->split_committed();
  destroy_page(page);

  if (committed_page != nullptr) {
    map_page(committed_page);
    allocation->pages()->insert_last(committed_page);
  }

  return nullptr;
}

ZPage* ZPageAllocator::alloc_page(ZPageType type, size_t size, ZAllocationFlags flags, ZPageAge age) {
  EventZPageAllocation event;

retry:
  ZPageAllocation allocation(type, size, flags);

  // Allocate one or more pages from the page cache. If the allocation
  // succeeds but the returned pages don't cover the complete allocation,
  // then finalize phase is allowed to allocate the remaining memory
  // directly from the physical memory manager. Note that this call might
  // block in a safepoint if the non-blocking flag is not set.
  if (!alloc_page_or_stall(&allocation)) {
    // Out of memory
    return nullptr;
  }

  ZPage* const page = alloc_page_finalize(&allocation);
  if (page == nullptr) {
    // Failed to commit or map. Clean up and retry, in the hope that
    // we can still allocate by flushing the page cache (more aggressively).
    free_pages_alloc_failed(&allocation);
    goto retry;
  }

  // The generation's used is tracked here when the page is handed out
  // to the allocating thread. The overall heap "used" is tracked in
  // the lower-level allocation code.
  const ZGenerationId id = age == ZPageAge::old ? ZGenerationId::old : ZGenerationId::young;
  increase_used_generation(id, size);

  // Reset page. This updates the page's sequence number and must
  // be done after we potentially blocked in a safepoint (stalled)
  // where the global sequence number was updated.
  page->reset(age);
  page->reset_top_for_allocation();
  page->reset_livemap();
  if (age == ZPageAge::old) {
    page->remset_alloc();
  }

  // Update allocation statistics. Exclude gc relocations to avoid
  // artificial inflation of the allocation rate during relocation.
  if (!flags.gc_relocation() && is_init_completed()) {
    // Note that there are two allocation rate counters, which have
    // different purposes and are sampled at different frequencies.
    ZStatInc(ZCounterMutatorAllocationRate, size);
    ZStatMutatorAllocRate::sample_allocation(size);
  }

  // Send event
  event.commit((u8)type, size, allocation.flushed(), allocation.committed(),
               page->physical_memory().nsegments(), flags.non_blocking());

  return page;
}

void ZPageAllocator::satisfy_stalled() {
  for (;;) {
    ZPageAllocation* const allocation = _stalled.first();
    if (allocation == nullptr) {
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
    allocation->satisfy(true);
  }
}

ZPage* ZPageAllocator::prepare_to_recycle(ZPage* page, bool allow_defragment) {
  // Make sure we have a page that is safe to recycle
  ZPage* const to_recycle = _safe_recycle.register_and_clone_if_activated(page);

  // Defragment the page before recycle if allowed and needed
  if (allow_defragment && should_defragment(to_recycle)) {
    return defragment_page(to_recycle);
  }

  // Remove the remset before recycling
  if (to_recycle->is_old() && to_recycle == page) {
    to_recycle->remset_delete();
  }

  return to_recycle;
}

void ZPageAllocator::recycle_page(ZPage* page) {
  // Set time when last used
  page->set_last_used();

  // Cache page
  _cache.free_page(page);
}

void ZPageAllocator::free_page(ZPage* page, bool allow_defragment) {
  const ZGenerationId generation_id = page->generation_id();

  // Prepare page for recycling before taking the lock
  ZPage* const to_recycle = prepare_to_recycle(page, allow_defragment);

  ZLocker<ZLock> locker(&_lock);

  // Update used statistics
  const size_t size = to_recycle->size();
  decrease_used(size);
  decrease_used_generation(generation_id, size);

  // Free page
  recycle_page(to_recycle);

  // Try satisfy stalled allocations
  satisfy_stalled();
}

void ZPageAllocator::free_pages(const ZArray<ZPage*>* pages) {
  ZArray<ZPage*> to_recycle_pages;

  size_t young_size = 0;
  size_t old_size = 0;

  // Prepare pages for recycling before taking the lock
  ZArrayIterator<ZPage*> pages_iter(pages);
  for (ZPage* page; pages_iter.next(&page);) {
    if (page->is_young()) {
      young_size += page->size();
    } else {
      old_size += page->size();
    }

    // Prepare to recycle
    ZPage* const to_recycle = prepare_to_recycle(page, true /* allow_defragment */);

    // Register for recycling
    to_recycle_pages.push(to_recycle);
  }

  ZLocker<ZLock> locker(&_lock);

  // Update used statistics
  decrease_used(young_size + old_size);
  decrease_used_generation(ZGenerationId::young, young_size);
  decrease_used_generation(ZGenerationId::old, old_size);

  // Free pages
  ZArrayIterator<ZPage*> iter(&to_recycle_pages);
  for (ZPage* page; iter.next(&page);) {
    recycle_page(page);
  }

  // Try satisfy stalled allocations
  satisfy_stalled();
}

void ZPageAllocator::free_pages_alloc_failed(ZPageAllocation* allocation) {
  // The page(s) in the allocation are either taken from the cache or a newly
  // created, mapped and commited ZPage. These page(s) have not been inserted in
  // the page table, nor allocated a remset, so prepare_to_recycle is not required.
  ZLocker<ZLock> locker(&_lock);

  // Only decrease the overall used and not the generation used,
  // since the allocation failed and generation used wasn't bumped.
  decrease_used(allocation->size());

  size_t freed = 0;

  // Free any allocated/flushed pages
  ZListRemoveIterator<ZPage> iter(allocation->pages());
  for (ZPage* page; iter.next(&page);) {
    freed += page->size();
    recycle_page(page);
  }

  // Adjust capacity and used to reflect the failed capacity increase
  const size_t remaining = allocation->size() - freed;
  decrease_capacity(remaining, true /* set_max_capacity */);

  // Try satisfy stalled allocations
  satisfy_stalled();
}

size_t ZPageAllocator::uncommit(uint64_t* timeout) {
  // We need to join the suspendible thread set while manipulating capacity and
  // used, to make sure GC safepoints will have a consistent view.
  ZList<ZPage> pages;
  size_t flushed;

  {
    SuspendibleThreadSetJoiner sts_joiner;
    ZLocker<ZLock> locker(&_lock);

    // Never uncommit below min capacity. We flush out and uncommit chunks at
    // a time (~0.8% of the max capacity, but at least one granule and at most
    // 256M), in case demand for memory increases while we are uncommitting.
    const size_t retain = MAX2(_used, _min_capacity);
    const size_t release = _capacity - retain;
    const size_t limit = MIN2(align_up(_current_max_capacity >> 7, ZGranuleSize), 256 * M);
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
  ZListRemoveIterator<ZPage> iter(&pages);
  for (ZPage* page; iter.next(&page);) {
    unmap_page(page);
    uncommit_page(page);
    destroy_page(page);
  }

  {
    SuspendibleThreadSetJoiner sts_joiner;
    ZLocker<ZLock> locker(&_lock);

    // Adjust claimed and capacity to reflect the uncommit
    Atomic::sub(&_claimed, flushed);
    decrease_capacity(flushed, false /* set_max_capacity */);
  }

  return flushed;
}

void ZPageAllocator::enable_safe_destroy() const {
  _safe_destroy.enable_deferred_delete();
}

void ZPageAllocator::disable_safe_destroy() const {
  _safe_destroy.disable_deferred_delete();
}

void ZPageAllocator::enable_safe_recycle() const {
  _safe_recycle.activate();
}

void ZPageAllocator::disable_safe_recycle() const {
  _safe_recycle.deactivate();
}

static bool has_alloc_seen_young(const ZPageAllocation* allocation) {
  return allocation->young_seqnum() != ZGeneration::young()->seqnum();
}

static bool has_alloc_seen_old(const ZPageAllocation* allocation) {
  return allocation->old_seqnum() != ZGeneration::old()->seqnum();
}

bool ZPageAllocator::is_alloc_stalling() const {
  ZLocker<ZLock> locker(&_lock);
  return _stalled.first() != nullptr;
}

bool ZPageAllocator::is_alloc_stalling_for_old() const {
  ZLocker<ZLock> locker(&_lock);

  ZPageAllocation* const allocation = _stalled.first();
  if (allocation == nullptr) {
    // No stalled allocations
    return false;
  }

  return has_alloc_seen_young(allocation) && !has_alloc_seen_old(allocation);
}

void ZPageAllocator::notify_out_of_memory() {
  // Fail allocation requests that were enqueued before the last major GC started
  for (ZPageAllocation* allocation = _stalled.first(); allocation != nullptr; allocation = _stalled.first()) {
    if (!has_alloc_seen_old(allocation)) {
      // Not out of memory, keep remaining allocation requests enqueued
      return;
    }

    // Out of memory, dequeue and fail allocation request
    _stalled.remove(allocation);
    allocation->satisfy(false);
  }
}

void ZPageAllocator::restart_gc() const {
  ZPageAllocation* const allocation = _stalled.first();
  if (allocation == nullptr) {
    // No stalled allocations
    return;
  }

  if (!has_alloc_seen_young(allocation)) {
    // Start asynchronous minor GC, keep allocation requests enqueued
    const ZDriverRequest request(GCCause::_z_allocation_stall, ZYoungGCThreads, 0);
    ZDriver::minor()->collect(request);
  } else {
    // Start asynchronous major GC, keep allocation requests enqueued
    const ZDriverRequest request(GCCause::_z_allocation_stall, ZYoungGCThreads, ZOldGCThreads);
    ZDriver::major()->collect(request);
  }
}

void ZPageAllocator::handle_alloc_stalling_for_young() {
  ZLocker<ZLock> locker(&_lock);
  restart_gc();
}

void ZPageAllocator::handle_alloc_stalling_for_old(bool cleared_all_soft_refs) {
  ZLocker<ZLock> locker(&_lock);
  if (cleared_all_soft_refs) {
    notify_out_of_memory();
  }
  restart_gc();
}

void ZPageAllocator::threads_do(ThreadClosure* tc) const {
  tc->do_thread(_unmapper);
  tc->do_thread(_uncommitter);
}
