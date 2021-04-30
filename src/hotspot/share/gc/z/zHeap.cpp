/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/locationPrinter.hpp"
#include "gc/shared/tlab_globals.hpp"
#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zArray.inline.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zHeapIterator.hpp"
#include "gc/z/zHeuristics.hpp"
#include "gc/z/zPage.inline.hpp"
#include "gc/z/zPageTable.inline.hpp"
#include "gc/z/zResurrection.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zThread.inline.hpp"
#include "gc/z/zUncoloredRoot.inline.hpp"
#include "gc/z/zVerify.hpp"
#include "gc/z/zWorkers.hpp"
#include "logging/log.hpp"
#include "memory/iterator.hpp"
#include "memory/metaspaceUtils.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/thread.hpp"
#include "utilities/debug.hpp"

static const ZStatCounter ZCounterUndoPageAllocation("Memory", "Undo Page Allocation", ZStatUnitOpsPerSecond);
static const ZStatCounter ZCounterOutOfMemory("Memory", "Out Of Memory", ZStatUnitOpsPerSecond);

ZHeap* ZHeap::_heap = NULL;

ZHeap::ZHeap() :
    _page_allocator(MinHeapSize, InitialHeapSize, MaxHeapSize),
    _page_table(),
    _serviceability(min_capacity(), max_capacity()),
    _young_generation(&_page_table, &_page_allocator),
    _old_generation(),
    _minor_cycle(&_page_table, &_page_allocator),
    _major_cycle(&_page_table, &_page_allocator),
    _initialized() {
  // Install global heap instance
  assert(_heap == NULL, "Already initialized");
  _heap = this;

  _initialized = _page_allocator.initialize_heap(_major_cycle.workers());

  // Update statistics
  _minor_cycle.stat_heap()->set_at_initialize(_page_allocator.stats(NULL));
  _major_cycle.stat_heap()->set_at_initialize(_page_allocator.stats(NULL));
}

bool ZHeap::is_initialized() const {
  return _initialized;
}

size_t ZHeap::min_capacity() const {
  return _page_allocator.min_capacity();
}

size_t ZHeap::max_capacity() const {
  return _page_allocator.max_capacity();
}

size_t ZHeap::soft_max_capacity() const {
  return _page_allocator.soft_max_capacity();
}

size_t ZHeap::capacity() const {
  return _page_allocator.capacity();
}

size_t ZHeap::used() const {
  return _page_allocator.used();
}

size_t ZHeap::unused() const {
  return _page_allocator.unused();
}

size_t ZHeap::tlab_capacity() const {
  return capacity();
}

size_t ZHeap::tlab_used() const {
  return _young_generation.used();
}

size_t ZHeap::max_tlab_size() const {
  return ZObjectSizeLimitSmall;
}

size_t ZHeap::unsafe_max_tlab_alloc() const {
  size_t size = _young_generation.remaining();

  if (size < MinTLABSize) {
    // The remaining space in the allocator is not enough to
    // fit the smallest possible TLAB. This means that the next
    // TLAB allocation will force the allocator to get a new
    // backing page anyway, which in turn means that we can then
    // fit the largest possible TLAB.
    size = max_tlab_size();
  }

  return MIN2(size, max_tlab_size());
}

bool ZHeap::is_in(uintptr_t addr) const {
  if (addr == 0) {
    // Null isn't in the heap.
    return false;
  }

  // An address is considered to be "in the heap" if it points into
  // the allocated part of a page, regardless of which heap view is
  // used. Note that an address with the finalizable metadata bit set
  // is not pointing into a heap view, and therefore not considered
  // to be "in the heap".

  assert(!is_valid(zpointer(addr)), "Don't pass in colored oops");

  if (!is_valid(zaddress(addr))) {
    return false;
  }

  zaddress o = to_zaddress(addr);
  const ZPage* const page = _page_table.get(o);
  if (page == NULL) {
    return false;
  }

  return is_in_page_relaxed(page, o);
}

bool ZHeap::is_in_page_relaxed(const ZPage* page, zaddress addr) const {
  if (page->is_in(addr)) {
    return true;
  }

  // Could still be a from-object during an in-place relocation
  if (_major_cycle.phase() == ZPhase::Relocate) {
    const ZForwarding* const forwarding = _major_cycle.forwarding(unsafe(addr));
    if (forwarding != NULL && forwarding->in_place_relocation_is_below_top_at_start(ZAddress::offset(addr))) {
      return true;
    }
  }
  if (_minor_cycle.phase() == ZPhase::Relocate) {
    const ZForwarding* const forwarding = _minor_cycle.forwarding(unsafe(addr));
    if (forwarding != NULL && forwarding->in_place_relocation_is_below_top_at_start(ZAddress::offset(addr))) {
      return true;
    }
  }

  return false;
}

void ZHeap::threads_do(ThreadClosure* tc) const {
  _page_allocator.threads_do(tc);
  _minor_cycle.threads_do(tc);
  _major_cycle.threads_do(tc);
}

void ZHeap::out_of_memory() {
  ResourceMark rm;

  ZStatInc(ZCounterOutOfMemory);
  log_info(gc)("Out Of Memory (%s)", Thread::current()->name());
}

ZPage* ZHeap::alloc_page(uint8_t type, size_t size, ZAllocationFlags flags, ZCycle* cycle, ZGenerationId generation, ZPageAge age) {
  ZPage* const page = _page_allocator.alloc_page(type, size, flags, cycle, generation, age);
  if (page != NULL) {
    // Insert page table entry
    _page_table.insert(page);
  }

  return page;
}

void ZHeap::undo_alloc_page(ZPage* page) {
  assert(page->is_allocating(), "Invalid page state");

  ZStatInc(ZCounterUndoPageAllocation);
  log_trace(gc)("Undo page allocation, thread: " PTR_FORMAT " (%s), page: " PTR_FORMAT ", size: " SIZE_FORMAT,
                ZThread::id(), ZThread::name(), p2i(page), page->size());

  free_page(page, NULL /* worker_generation */);
}

void ZHeap::free_page(ZPage* page, ZCycle* cycle) {
  // Remove page table entry
  _page_table.remove(page);

  // Free page
  _page_allocator.free_page(page, cycle);
}

void ZHeap::free_pages(const ZArray<ZPage*>* pages, ZCycle* cycle) {
  // Remove page table entries
  ZArrayIterator<ZPage*> iter(pages);
  for (ZPage* page; iter.next(&page);) {
    _page_table.remove(page);
  }

  // Free pages
  _page_allocator.free_pages(pages, cycle);
}

void ZHeap::recycle_page(ZPage* page) {
  // Recycle page
  _page_allocator.recycle_page(page);
}

void ZHeap::safe_destroy_page(ZPage* page) {
  // Safely destroy page
  _page_allocator.safe_destroy_page(page);
}

void ZHeap::mark_flush_and_free(Thread* thread) {
  minor_cycle()->mark_flush_and_free(thread);
  major_cycle()->mark_flush_and_free(thread);
}

void ZHeap::keep_alive(oop obj) {
  zaddress addr = to_zaddress(obj);
  ZUncoloredRoot::keep_alive_object(addr);
}

void ZHeap::object_iterate(ObjectClosure* object_cl, bool visit_weaks) {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");
  ZHeapIterator iter(1 /* nworkers */, visit_weaks);
  iter.object_iterate(object_cl, 0 /* worker_id */);
}

void ZHeap::object_and_field_iterate(ObjectClosure* object_cl, OopFieldClosure* field_cl, bool visit_weaks) {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");
  ZHeapIterator iter(1 /* nworkers */, visit_weaks);
  iter.object_and_field_iterate(object_cl, field_cl, 0 /* worker_id */);
}

ParallelObjectIterator* ZHeap::parallel_object_iterator(uint nworkers, bool visit_weaks) {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");
  return new ZHeapIterator(nworkers, visit_weaks);
}

void ZHeap::serviceability_initialize() {
  _serviceability.initialize();
}

GCMemoryManager* ZHeap::serviceability_cycle_memory_manager() {
  return _serviceability.cycle_memory_manager();
}

GCMemoryManager* ZHeap::serviceability_pause_memory_manager() {
  return _serviceability.pause_memory_manager();
}

MemoryPool* ZHeap::serviceability_memory_pool() {
  return _serviceability.memory_pool();
}

ZServiceabilityCounters* ZHeap::serviceability_counters() {
  return _serviceability.counters();
}

void ZHeap::print_on(outputStream* st) const {
  st->print_cr(" ZHeap           used " SIZE_FORMAT "M, capacity " SIZE_FORMAT "M, max capacity " SIZE_FORMAT "M",
               used() / M,
               capacity() / M,
               max_capacity() / M);
  MetaspaceUtils::print_on(st);
}

void ZHeap::print_extended_on(outputStream* st) const {
  print_on(st);
  st->cr();

  // Do not allow pages to be deleted
  _page_allocator.enable_deferred_destroy();

  // Print all pages
  st->print_cr("ZGC Page Table:");
  ZPageTableIterator iter(&_page_table);
  for (ZPage* page; iter.next(&page);) {
    page->print_on(st);
  }

  // Allow pages to be deleted
  _page_allocator.disable_deferred_destroy();
}

bool ZHeap::print_location(outputStream* st, uintptr_t addr) const {
  if (LocationPrinter::is_valid_obj((void*)addr)) {
    // Intentionally unchecked cast
    const zpointer obj = zpointer(addr);
    const bool uncolored = is_valid(zaddress(addr));
    const bool colored = is_valid(zpointer(addr));

    const char* const desc =  uncolored
        ? "an uncolored" : !colored
        ? "an invalid"   : ZPointer::is_load_good(obj)
        ? "a good"
        : "a bad";

    st->print(PTR_FORMAT " is %s oop: ", addr, desc);

    if (uncolored) {
      to_oop(zaddress(addr))->print_on(st);
    }

    return true;
  }

  return false;
}

void ZHeap::verify() {
  // Heap verification can only be done between mark end and
  // relocate start. This is the only window where all oop are
  // good and the whole heap is in a consistent state.
  guarantee(ZHeap::heap()->major_cycle()->phase() == ZPhase::MarkComplete, "Invalid phase");

  ZVerify::after_weak_processing();
}
