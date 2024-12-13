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
#include "classfile/classLoaderDataGraph.hpp"
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/gcLogPrecious.hpp"
#include "gc/shared/locationPrinter.hpp"
#include "gc/shared/tlab_globals.hpp"
#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zArray.inline.hpp"
#include "gc/z/zGeneration.inline.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zHeapIterator.hpp"
#include "gc/z/zHeuristics.hpp"
#include "gc/z/zInitialize.hpp"
#include "gc/z/zPage.inline.hpp"
#include "gc/z/zPageTable.inline.hpp"
#include "gc/z/zResurrection.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zUncoloredRoot.inline.hpp"
#include "gc/z/zUtils.hpp"
#include "gc/z/zVerify.hpp"
#include "gc/z/zWorkers.hpp"
#include "logging/log.hpp"
#include "memory/iterator.hpp"
#include "memory/metaspaceUtils.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/javaThread.hpp"
#include "utilities/debug.hpp"

static const ZStatCounter ZCounterUndoPageAllocation("Memory", "Undo Page Allocation", ZStatUnitOpsPerSecond);
static const ZStatCounter ZCounterOutOfMemory("Memory", "Out Of Memory", ZStatUnitOpsPerSecond);

ZHeap* ZHeap::_heap = nullptr;

ZHeap::ZHeap()
  : _page_allocator(MinHeapSize, InitialHeapSize, SoftMaxHeapSize, MaxHeapSize),
    _page_table(),
    _allocator_eden(),
    _allocator_relocation(),
    _serviceability(initial_capacity(), min_capacity(), max_capacity()),
    _old(&_page_table, &_page_allocator),
    _young(&_page_table, _old.forwarding_table(), &_page_allocator),
    _initialized(false) {

  // Install global heap instance
  assert(_heap == nullptr, "Already initialized");
  _heap = this;

  if (!_page_allocator.is_initialized() || !_young.is_initialized() || !_old.is_initialized()) {
    return;
  }

  // Prime cache
  if (!_page_allocator.prime_cache(_old.workers(), InitialHeapSize)) {
    ZInitialize::error("Failed to allocate initial Java heap (" SIZE_FORMAT "M)", InitialHeapSize / M);
    return;
  }

  if (UseDynamicNumberOfGCThreads) {
    log_info_p(gc, init)("GC Workers Max: %u (dynamic)", ConcGCThreads);
  }

  // Update statistics
  _young.stat_heap()->at_initialize(_page_allocator.min_capacity(), _page_allocator.max_capacity());
  _old.stat_heap()->at_initialize(_page_allocator.min_capacity(), _page_allocator.max_capacity());

  // Successfully initialized
  _initialized = true;
}

bool ZHeap::is_initialized() const {
  return _initialized;
}

size_t ZHeap::initial_capacity() const {
  return _page_allocator.initial_capacity();
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

size_t ZHeap::used_generation(ZGenerationId id) const {
  return _page_allocator.used_generation(id);
}

size_t ZHeap::used_young() const {
  return _page_allocator.used_generation(ZGenerationId::young);
}

size_t ZHeap::used_old() const {
  return _page_allocator.used_generation(ZGenerationId::old);
}

size_t ZHeap::unused() const {
  return _page_allocator.unused();
}

size_t ZHeap::tlab_capacity() const {
  return capacity();
}

size_t ZHeap::tlab_used() const {
  return _allocator_eden.tlab_used();
}

size_t ZHeap::max_tlab_size() const {
  return ZObjectSizeLimitSmall;
}

size_t ZHeap::unsafe_max_tlab_alloc() const {
  size_t size = _allocator_eden.remaining();

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

  const zaddress o = to_zaddress(addr);
  const ZPage* const page = _page_table.get(o);
  if (page == nullptr) {
    return false;
  }

  return is_in_page_relaxed(page, o);
}

bool ZHeap::is_in_page_relaxed(const ZPage* page, zaddress addr) const {
  if (page->is_in(addr)) {
    return true;
  }

  // Could still be a from-object during an in-place relocation
  if (_old.is_phase_relocate()) {
    const ZForwarding* const forwarding = _old.forwarding(unsafe(addr));
    if (forwarding != nullptr && forwarding->in_place_relocation_is_below_top_at_start(ZAddress::offset(addr))) {
      return true;
    }
  }
  if (_young.is_phase_relocate()) {
    const ZForwarding* const forwarding = _young.forwarding(unsafe(addr));
    if (forwarding != nullptr && forwarding->in_place_relocation_is_below_top_at_start(ZAddress::offset(addr))) {
      return true;
    }
  }

  return false;
}

void ZHeap::threads_do(ThreadClosure* tc) const {
  _page_allocator.threads_do(tc);
  _young.threads_do(tc);
  _old.threads_do(tc);
}

void ZHeap::out_of_memory() {
  ResourceMark rm;

  ZStatInc(ZCounterOutOfMemory);
  log_info(gc)("Out Of Memory (%s)", Thread::current()->name());
}

ZPage* ZHeap::alloc_page(ZPageType type, size_t size, ZAllocationFlags flags, ZPageAge age) {
  ZPage* const page = _page_allocator.alloc_page(type, size, flags, age);
  if (page != nullptr) {
    // Insert page table entry
    _page_table.insert(page);
  }

  return page;
}

void ZHeap::undo_alloc_page(ZPage* page) {
  assert(page->is_allocating(), "Invalid page state");

  ZStatInc(ZCounterUndoPageAllocation);
  log_trace(gc)("Undo page allocation, thread: " PTR_FORMAT " (%s), page: " PTR_FORMAT ", size: " SIZE_FORMAT,
                p2i(Thread::current()), ZUtils::thread_name(), p2i(page), page->size());

  free_page(page, false /* allow_defragment */);
}

void ZHeap::free_page(ZPage* page, bool allow_defragment) {
  // Remove page table entry
  _page_table.remove(page);

  // Free page
  _page_allocator.free_page(page, allow_defragment);
}

size_t ZHeap::free_empty_pages(const ZArray<ZPage*>* pages) {
  size_t freed = 0;
  // Remove page table entries
  ZArrayIterator<ZPage*> iter(pages);
  for (ZPage* page; iter.next(&page);) {
    _page_table.remove(page);
    freed += page->size();
  }

  // Free pages
  _page_allocator.free_pages(pages);

  return freed;
}

void ZHeap::keep_alive(oop obj) {
  const zaddress addr = to_zaddress(obj);
  ZBarrier::mark<ZMark::Resurrect, ZMark::AnyThread, ZMark::Follow, ZMark::Strong>(addr);
}

void ZHeap::mark_flush_and_free(Thread* thread) {
  _young.mark_flush_and_free(thread);
  _old.mark_flush_and_free(thread);
}

bool ZHeap::is_allocating(zaddress addr) const {
  const ZPage* const page = _page_table.get(addr);
  return page->is_allocating();
}

void ZHeap::object_iterate(ObjectClosure* object_cl, bool visit_weaks) {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");
  ZHeapIterator iter(1 /* nworkers */, visit_weaks, false /* for_verify */);
  iter.object_iterate(object_cl, 0 /* worker_id */);
}

void ZHeap::object_and_field_iterate_for_verify(ObjectClosure* object_cl, OopFieldClosure* field_cl, bool visit_weaks) {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");
  ZHeapIterator iter(1 /* nworkers */, visit_weaks, true /* for_verify */);
  iter.object_and_field_iterate(object_cl, field_cl, 0 /* worker_id */);
}

ParallelObjectIteratorImpl* ZHeap::parallel_object_iterator(uint nworkers, bool visit_weaks) {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");
  return new ZHeapIterator(nworkers, visit_weaks, false /* for_verify */);
}

void ZHeap::serviceability_initialize() {
  _serviceability.initialize();
}

GCMemoryManager* ZHeap::serviceability_cycle_memory_manager(bool minor) {
  return _serviceability.cycle_memory_manager(minor);
}

GCMemoryManager* ZHeap::serviceability_pause_memory_manager(bool minor) {
  return _serviceability.pause_memory_manager(minor);
}

MemoryPool* ZHeap::serviceability_memory_pool(ZGenerationId id) {
  return _serviceability.memory_pool(id);
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
  _page_allocator.enable_safe_destroy();

  // Print all pages
  st->print_cr("ZGC Page Table:");
  ZPageTableIterator iter(&_page_table);
  for (ZPage* page; iter.next(&page);) {
    page->print_on(st);
  }

  // Allow pages to be deleted
  _page_allocator.disable_safe_destroy();
}

bool ZHeap::print_location(outputStream* st, uintptr_t addr) const {
  // Intentionally unchecked cast
  const bool uncolored = is_valid(zaddress(addr));
  const bool colored = is_valid(zpointer(addr));
  if (colored && uncolored) {
    // Should not reach here
    return false;
  }

  if (colored) {
    return print_location(st, zpointer(addr));
  }

  if (uncolored) {
    return print_location(st, zaddress(addr));
  }

  return false;
}

bool ZHeap::print_location(outputStream* st, zaddress addr) const {
  assert(is_valid(addr), "must be");

  st->print(PTR_FORMAT " is a zaddress: ", untype(addr));

  if (addr == zaddress::null) {
    st->print_raw_cr("null");
    return true;
  }

  if (!ZHeap::is_in(untype(addr))) {
    st->print_raw_cr("not in heap");
    return false;
  }

  if (LocationPrinter::is_valid_obj((void*)untype(addr))) {
    to_oop(addr)->print_on(st);
    return true;
  }

  ZPage* const page = ZHeap::page(addr);
  zaddress_unsafe base;

  if (page->is_relocatable() && page->is_marked() && !ZGeneration::generation(page->generation_id())->is_phase_mark()) {
    base = page->find_base((volatile zpointer*) addr);
  } else {
    // TODO: This part is probably broken, but register printing recovers from crashes
    st->print_raw("Unreliable ");
    base = page->find_base_unsafe((volatile zpointer*) addr);
  }

  if (base == zaddress_unsafe::null) {
    st->print_raw_cr("Cannot find base");
    return false;
  }

  if (untype(base) == untype(addr)) {
    st->print_raw_cr("Bad mark info/base");
    return false;
  }

  st->print_raw_cr("Internal address");
  print_location(st, untype(base));
  return true;
}

bool ZHeap::print_location(outputStream* st, zpointer ptr) const {
  assert(is_valid(ptr), "must be");

  st->print(PTR_FORMAT " is %s zpointer: ", untype(ptr),
            ZPointer::is_load_good(ptr) ? "a good" : "a bad");

  if (!ZPointer::is_load_good(ptr)) {
    st->print_cr("decoded " PTR_FORMAT, untype(ZPointer::uncolor_unsafe(ptr)));
    // ptr is not load good but let us still investigate the uncolored address
    return print_location(st, untype(ZPointer::uncolor_unsafe(ptr)));
  }

  const zaddress addr =  ZPointer::uncolor(ptr);

  if (addr == zaddress::null) {
    st->print_raw_cr("null");
    return true;
  }

  if (LocationPrinter::is_valid_obj((void*)untype(addr))) {
    to_oop(addr)->print_on(st);
    return true;
  }

  st->print_cr("invalid object " PTR_FORMAT,  untype(addr));
  return false;
}
