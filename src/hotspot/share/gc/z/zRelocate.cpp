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
#include "gc/shared/suspendibleThreadSet.hpp"
#include "gc/z/zAbort.inline.hpp"
#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zBarrier.inline.hpp"
#include "gc/z/zCollectedHeap.hpp"
#include "gc/z/zCollector.inline.hpp"
#include "gc/z/zForwarding.inline.hpp"
#include "gc/z/zDriver.hpp"
#include "gc/z/zGeneration.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zIterator.inline.hpp"
#include "gc/z/zPage.inline.hpp"
#include "gc/z/zPageAge.hpp"
#include "gc/z/zRelocate.hpp"
#include "gc/z/zRelocationSet.inline.hpp"
#include "gc/z/zRootsIterator.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zStackWatermark.hpp"
#include "gc/z/zTask.hpp"
#include "gc/z/zThread.inline.hpp"
#include "gc/z/zUncoloredRoot.inline.hpp"
#include "gc/z/zWorkers.hpp"
#include "prims/jvmtiTagMap.hpp"
#include "runtime/atomic.hpp"
#include "utilities/debug.hpp"

static const ZStatSubPhase ZSubPhaseConcurrentMinorRelocateRemsetFlipPagePromoted("Concurrent Minor Relocate Remset FPP");
static const ZStatSubPhase ZSubPhaseConcurrentMinorRelocateRemsetNormalPromoted("Concurrent Minor Relocate Remset NP");

ZRelocateQueue::ZRelocateQueue() :
    _lock(),
    _queue(),
    _nworkers(0),
    _nsynchronized(0),
    _synchronize(false),
    _needs_attention(0) {}

bool ZRelocateQueue::needs_attention() const {
  return Atomic::load(&_needs_attention) != 0;
}

void ZRelocateQueue::inc_needs_attention() {
  const int needs_attention = Atomic::add(&_needs_attention, 1);
  assert(needs_attention == 1 || needs_attention == 2, "Invalid state");
}

void ZRelocateQueue::dec_needs_attention() {
  const int needs_attention = Atomic::sub(&_needs_attention, 1);
  assert(needs_attention == 0 || needs_attention == 1, "Invalid state");
}

bool ZRelocateQueue::is_enabled() const {
  return _nworkers != 0;
}

void ZRelocateQueue::join(uint nworkers) {
  assert(_nworkers == 0, "Invalid state");
  assert(_nsynchronized == 0, "Invalid state");
  _nworkers = nworkers;
}

void ZRelocateQueue::leave() {
  ZLocker<ZConditionLock> locker(&_lock);
  _nworkers--;
  if (_synchronize && _nworkers == _nsynchronized) {
    // All workers synchronized
    _lock.notify_all();
  }
}

void ZRelocateQueue::add(ZForwarding* forwarding) {
  ZLocker<ZConditionLock> locker(&_lock);
  if (is_enabled()) {
    _queue.append(forwarding);
    if (_queue.length() == 1) {
      // Queue became non-empty
      inc_needs_attention();
      _lock.notify_all();
    }
  }
}

bool ZRelocateQueue::poll(ZForwarding** forwarding, bool* synchronized) {
  // Fast path avoids locking
  if (!needs_attention() && !*synchronized) {
    return false;
  }

  // Slow path to get the next forwarding and/or synchronize
  ZLocker<ZConditionLock> locker(&_lock);

  if (_synchronize && !*synchronized) {
    // Synchronize
    *synchronized = true;
    _nsynchronized++;
    if (_nsynchronized == _nworkers) {
      // All workers synchronized
      _lock.notify_all();
    }
  }

  // Wait for queue to become non-empty or desynchronized
  while (_queue.is_empty() && _synchronize) {
    _lock.wait();
  }

  if (!_synchronize && *synchronized) {
    // Desynchronize
    *synchronized = false;
    _nsynchronized--;
  }

  // Check if queue is empty
  if (_queue.is_empty()) {
    return false;
  }

  // Get and remove first
  *forwarding = _queue.at(0);
  _queue.remove_at(0);
  if (_queue.is_empty()) {
    dec_needs_attention();
  }

  return true;
}

void ZRelocateQueue::clear() {
  assert(!is_enabled(), "Invalid state");
  if (!_queue.is_empty()) {
    _queue.clear();
    dec_needs_attention();
  }
}

void ZRelocateQueue::synchronize() {
  ZLocker<ZConditionLock> locker(&_lock);
  _synchronize = true;
  inc_needs_attention();
  while (_nworkers != _nsynchronized) {
    _lock.wait();
  }
}

void ZRelocateQueue::desynchronize() {
  ZLocker<ZConditionLock> locker(&_lock);
  _synchronize = false;
  dec_needs_attention();
  _lock.notify_all();
}

ZRelocate::ZRelocate(ZCollector* collector) :
    _collector(collector),
    _queue() {}

ZWorkers* ZRelocate::workers() const {
  return _collector->workers();
}

void ZRelocate::start() {
  _queue.join(workers()->active_workers());
}

static uintptr_t forwarding_index(ZForwarding* forwarding, zoffset from_offset) {
  return (from_offset - forwarding->start()) >> forwarding->object_alignment_shift();
}

static zaddress forwarding_find(ZForwarding* forwarding, zoffset from_offset, ZForwardingCursor* cursor) {
  const uintptr_t from_index = forwarding_index(forwarding, from_offset);
  const ZForwardingEntry entry = forwarding->find(from_index, cursor);
  return entry.populated() ? ZOffset::address(to_zoffset(entry.to_offset())) : zaddress::null;
}

static zaddress forwarding_find(ZForwarding* forwarding, zaddress_unsafe from_addr, ZForwardingCursor* cursor) {
   return forwarding_find(forwarding, ZAddress::offset(from_addr), cursor);
}

static zaddress forwarding_find(ZForwarding* forwarding, zaddress from_addr, ZForwardingCursor* cursor) {
   return forwarding_find(forwarding, ZAddress::offset(from_addr), cursor);
}

static zaddress forwarding_insert(ZForwarding* forwarding, zoffset from_offset, zaddress to_addr, ZForwardingCursor* cursor) {
  const uintptr_t from_index = forwarding_index(forwarding, from_offset);
  const zoffset to_offset = ZAddress::offset(to_addr);
  const zoffset to_offset_final = forwarding->insert(from_index, to_offset, cursor);
  return ZOffset::address(to_offset_final);
}

static zaddress forwarding_insert(ZForwarding* forwarding, zaddress from_addr, zaddress to_addr, ZForwardingCursor* cursor) {
  return forwarding_insert(forwarding, ZAddress::offset(from_addr), to_addr, cursor);
}

void ZRelocate::add_remset(volatile zpointer* p) {
  ZHeap::heap()->remember(p);
}

void ZRelocate::add_remset_for_fields(volatile zaddress addr) {
  ZHeap::heap()->remember_fields(addr);
}

static zaddress relocate_object_inner(ZForwarding* forwarding, zaddress from_addr, ZForwardingCursor* cursor) {
  assert(ZHeap::heap()->is_object_live(from_addr), "Should be live");

  // Allocate object
  const size_t size = ZUtils::object_size(from_addr);

  ZPage* page = forwarding->page();
  ZGeneration* generation = forwarding->age_to() == ZPageAge::old ?
                            (ZGeneration*)ZHeap::heap()->old_generation() :
                            (ZGeneration*)ZHeap::heap()->young_generation();

  const zaddress to_addr = generation->alloc_object_for_relocation(size);
  if (is_null(to_addr)) {
    // Allocation failed
    return zaddress::null;
  }

  // Copy object
  ZUtils::object_copy_disjoint(from_addr, to_addr, size);

  // Insert forwarding
  const zaddress to_addr_final = forwarding_insert(forwarding, from_addr, to_addr, cursor);

  if (to_addr_final != to_addr) {
    // Already relocated, try undo allocation
    generation->undo_alloc_object_for_relocation(to_addr, size);
  }

  return to_addr_final;
}

zaddress ZRelocate::relocate_object(ZForwarding* forwarding, zaddress_unsafe from_addr) {
  ZForwardingCursor cursor;

  // Lookup forwarding
  zaddress to_addr = forwarding_find(forwarding, from_addr, &cursor);
  if (!is_null(to_addr)) {
    // Already relocated
    return to_addr;
  }

  // FIXME: Assert that it's correct that we are here. Old/young addr vs major/minor relocation ...

  // Relocate object
  if (forwarding->retain_page()) {
    to_addr = relocate_object_inner(forwarding, safe(from_addr), &cursor);
    forwarding->release_page();

    if (!is_null(to_addr)) {
      // Success
      return to_addr;
    }

    // Failed to relocate object. Signal and wait for a worker thread to
    // complete relocation of this page, and then forward the object. If
    // the GC aborts the relocation phase before the page has been relocated,
    // then wait return false and we just forward the object in-place.
    _queue.add(forwarding);

    if (!forwarding->wait_page_released()) {
      // Forward object in-place
      return forwarding_insert(forwarding, safe(from_addr), safe(from_addr), &cursor);
    }
  }

  // Forward object
  return forward_object(forwarding, from_addr);
}

zaddress ZRelocate::forward_object(ZForwarding* forwarding, zaddress_unsafe from_addr) {
  ZForwardingCursor cursor;
  const zaddress to_addr = forwarding_find(forwarding, from_addr, &cursor);
  assert(!is_null(to_addr), "Should be forwarded: " PTR_FORMAT, untype(from_addr));
  return to_addr;
}

static ZPage* alloc_page(const ZForwarding* forwarding, ZGenerationId generation, ZPageAge age) {
  if (ZStressRelocateInPlace) {
    // Simulate failure to allocate a new page. This will
    // cause the page being relocated to be relocated in-place.
    return NULL;
  }

  ZAllocationFlags flags;
  flags.set_non_blocking();
  flags.set_gc_relocation();

  return ZHeap::heap()->alloc_page(forwarding->type(), forwarding->size(), flags, generation, age);
}

static void free_page(ZPage* page, bool reclaimed) {
  ZHeap::heap()->free_page(page, reclaimed);
}

static bool should_free_target_page(ZPage* page) {
  // Free target page if it is empty. We can end up with an empty target
  // page if we allocated a new target page, and then lost the race to
  // relocate the remaining objects, leaving the target page empty when
  // relocation completed.
  return page != NULL && page->top() == page->start();
}

class ZRelocateSmallAllocator {
private:
  volatile size_t _in_place_count;
  ZGenerationId   _generation;
  ZPageAge        _age;

public:
  ZRelocateSmallAllocator(ZGenerationId generation, ZPageAge age) :
      _in_place_count(0),
      _generation(generation),
      _age(age) {}

  ZPage* alloc_target_page(ZForwarding* forwarding, ZPage* target) {
    ZPage* const page = alloc_page(forwarding, _generation, _age);
    if (page == NULL) {
      Atomic::inc(&_in_place_count);
    }

    return page;
  }

  void share_target_page(ZPage* page) {
    // Does nothing
  }

  void free_target_page(ZPage* page) {
    if (should_free_target_page(page)) {
      free_page(page, true /* reclaimed */);
    }
  }

  void free_relocated_page(ZPage* page) {
    free_page(page, true /* reclaimed */);
  }

  zaddress alloc_object(ZPage* page, size_t size) const {
    return (page != NULL) ? page->alloc_object(size) : zaddress::null;
  }

  void undo_alloc_object(ZPage* page, zaddress addr, size_t size) const {
    page->undo_alloc_object(addr, size);
  }

  const size_t in_place_count() const {
    return _in_place_count;
  }
};

class ZRelocateMediumAllocator {
private:
  ZConditionLock      _lock;
  ZPage*              _shared;
  bool                _in_place;
  volatile size_t     _in_place_count;
  ZGenerationId       _generation;
  ZPageAge            _age;

public:
  ZRelocateMediumAllocator(ZGenerationId generation, ZPageAge age) :
      _lock(),
      _shared(NULL),
      _in_place(false),
      _in_place_count(0),
      _generation(generation),
      _age(age) {}

  ~ZRelocateMediumAllocator() {
    if (should_free_target_page(_shared)) {
      free_page(_shared, true /* reclaimed */);
    }
  }

  ZPage* alloc_target_page(ZForwarding* forwarding, ZPage* target) {
    ZLocker<ZConditionLock> locker(&_lock);

    // Wait for any ongoing in-place relocation to complete
    while (_in_place) {
      _lock.wait();
    }

    // Allocate a new page only if the shared page is the same as the
    // current target page. The shared page will be different from the
    // current target page if another thread shared a page, or allocated
    // a new page.
    if (_shared == target) {
      _shared = alloc_page(forwarding, _generation, _age);
      if (_shared == NULL) {
        Atomic::inc(&_in_place_count);
        _in_place = true;
      }
    }

    return _shared;
  }

  void share_target_page(ZPage* page) {
    ZLocker<ZConditionLock> locker(&_lock);

    assert(_in_place, "Invalid state");
    assert(_shared == NULL, "Invalid state");
    assert(page != NULL, "Invalid page");

    _shared = page;
    _in_place = false;

    _lock.notify_all();
  }

  void free_target_page(ZPage* page) {
    // Does nothing
  }

  void free_relocated_page(ZPage* page) {
    free_page(page, true /* reclaimed */);
  }

  zaddress alloc_object(ZPage* page, size_t size) const {
    return (page != NULL) ? page->alloc_object_atomic(size) : zaddress::null;
  }

  void undo_alloc_object(ZPage* page, zaddress addr, size_t size) const {
    page->undo_alloc_object_atomic(addr, size);
  }

  const size_t in_place_count() const {
    return _in_place_count;
  }
};

template <typename Allocator>
class ZRelocateWork : public StackObj {
private:
  Allocator* const _allocator;
  ZForwarding*     _forwarding;
  ZPage*           _target;

  zaddress try_relocate_object_inner(zaddress from_addr) const {
    ZForwardingCursor cursor;

    // Lookup forwarding
    {
      const zaddress to_addr = forwarding_find(_forwarding, from_addr, &cursor);
      if (!is_null(to_addr)) {
        // Already relocated
        return to_addr;
      }
    }

    // Allocate object
    const size_t size = ZUtils::object_size(from_addr);
    const zaddress allocated_addr = _allocator->alloc_object(_target, size);
    if (is_null(allocated_addr)) {
      // Allocation failed
      return zaddress::null;
    }

    // Copy object. Use conjoint copying if we are relocating
    // in-place and the new object overlapps with the old object.
    if (_forwarding->in_place_relocation() && allocated_addr + size > from_addr) {
      ZUtils::object_copy_conjoint(from_addr, allocated_addr, size);
    } else {
      ZUtils::object_copy_disjoint(from_addr, allocated_addr, size);
    }

    // Insert forwarding
    const zaddress to_addr = forwarding_insert(_forwarding, from_addr, allocated_addr, &cursor);
    if (to_addr != allocated_addr) {
      // Already relocated, undo allocation
      _allocator->undo_alloc_object(_target, to_addr, size);
    }

    return to_addr;
  }

  void update_remset_old_to_old(zaddress from_addr, zaddress to_addr) const {
    // Old-to-old relocation - move existing remset bits

    // If this is called for an in-place relocated page, then this code has the
    // responsibility to clear the old remset bits. Extra care is needed because:
    //
    // 1) The to-object copy can overlap with the from-object copy
    // 2) Remset bits of old objects need to be cleared
    //
    // A watermark is used to keep track of how far the old remset bits have been removed.

    const bool in_place = _forwarding->in_place_relocation();
    ZPage* const from_page = _forwarding->page();
    const uintptr_t from_local_offset = from_page->local_offset(from_addr);

    if (in_place) {
      // Make sure remset entries of dead objects are cleared
      _forwarding->in_place_relocation_clear_remset_up_to(from_local_offset);
    }

    // Note: even with in-place relocation, the to_page could be another page
    ZPage* const to_page = ZHeap::heap()->page(to_addr);

    // Uses _relaxed version to handle that in-place relocation resets _top
    assert(ZHeap::heap()->is_in_page_relaxed(from_page, from_addr), "Must be");
    assert(to_page->is_in(to_addr), "Must be");


    // Read the size from the to-object, since the from-object
    // could have been overwritten during in-place relocation.
    const size_t size = ZUtils::object_size(to_addr);

    ZRememberSetIterator iter = from_page->remset_iterator_current_limited(from_local_offset, size);
    for (size_t index; iter.next(&index);) {
      const uintptr_t field_local_offset = index * oopSize;

      if (in_place) {
        // Need to forget the bit in the from-page. This is performed during
        // in-place relocation, which will slide the objects in the current page.
        from_page->clear_remset_non_par(field_local_offset);
      }

      // Add remset entry in the to-page
      const uintptr_t offset = field_local_offset - from_local_offset;
      const zaddress to_field = to_addr + offset;
      log_trace(gc, reloc)("Remember: " PTR_FORMAT, untype(to_field));
      to_page->remember((volatile zpointer*)to_field);
    }

    if (in_place) {
      // Record that the code above cleared all remset bits inside the from-object
      _forwarding->in_place_relocation_set_clear_remset_watermark(from_local_offset + size);
    }
  }

  void update_remset_promoted_all(zaddress to_addr) const {
    ZRelocate::add_remset_for_fields(to_addr);
  }

  static bool add_remset_if_young(volatile zpointer* p, zaddress addr) {
    if (ZHeap::heap()->is_young(addr)) {
      ZRelocate::add_remset(p);
      return true;
    }

    return false;
  }

  static void update_remset_promoted_filter_and_remap_per_field(volatile zpointer* p) {
    const zpointer ptr = Atomic::load(p);

    assert(ZPointer::is_major_load_good(ptr), "Should be at least major load good: " PTR_FORMAT, untype(ptr));

    if (ZPointer::is_store_good(ptr)) {
      // Already has a remset entry
      return;
    }

    if (ZPointer::is_load_good(ptr)) {
      if (!is_null_any(ptr)) {
        const zaddress addr = ZPointer::uncolor(ptr);
        add_remset_if_young(p, addr);
      }
      // No need to remap it is already load good
      return;
    }

    if (is_null_any(ptr)) {
      // Eagerly remap to skip adding a remset entry just to get deferred remapping
      ZBarrier::remap_minor_relocated(p, ptr);
      return;
    }

    zaddress_unsafe addr_unsafe = ZPointer::uncolor_unsafe(ptr);
    ZForwarding* forwarding = ZHeap::heap()->minor_collector()->forwarding(addr_unsafe);

    if (forwarding == NULL) {
      // Object isn't being relocated
      zaddress addr = safe(addr_unsafe);
      if (!add_remset_if_young(p, addr)) {
        // Not young - eagerly remap to skip adding a remset entry just to get deferred remapping
        ZBarrier::remap_minor_relocated(p, ptr);
      }
      return;
    }

    zaddress addr = forwarding->find(addr_unsafe);

    if (!is_null(addr)) {
      // Object has already been relocated
      if (!add_remset_if_young(p, addr)) {
        // Not young - eagerly remap to skip adding a remset entry just to get deferred remapping
        ZBarrier::remap_minor_relocated(p, ptr);
      }
      return;
    }

    // Object has not been relocated yet
    // Don't want to eagerly relocate objects, so just add a remset
    ZRelocate::add_remset(p);
    return;
  }

  void update_remset_promoted_filter_and_remap(zaddress to_addr) const {
    ZIterator::basic_oop_iterate(to_oop(to_addr), update_remset_promoted_filter_and_remap_per_field);
  }

  void update_remset_promoted(zaddress to_addr) const {
    switch (ZRelocateRemsetStrategy) {
    case 0: update_remset_promoted_all(to_addr); break;
    case 1: update_remset_promoted_filter_and_remap(to_addr); break;
    case 2: /* Handled after relocation is done */ break;
    default: fatal("Unsupported ZRelocateRemsetStrategy"); break;
    };
  }

  void update_remset_for_fields(zaddress from_addr, zaddress to_addr) const {
    if (_forwarding->age_to() == ZPageAge::old) {
      // Need to deal with remset when moving stuff to old
      if (_forwarding->age_from() == ZPageAge::old) {
        update_remset_old_to_old(from_addr, to_addr);
      } else {
        update_remset_promoted(to_addr);
      }
    }
  }

  bool try_relocate_object(zaddress from_addr) const {
    zaddress to_addr = try_relocate_object_inner(from_addr);

    if (is_null(to_addr)) {
      return false;
    }

    update_remset_for_fields(from_addr, to_addr);

    return true;
  }

  ZPage* start_in_place_relocation() {
    _forwarding->in_place_relocation_claim_page();
    _forwarding->in_place_relocation_start();

    ZPage* prev_page = _forwarding->page();
    ZPageAge new_age = _forwarding->age_to();
    ZGenerationId new_generation = new_age == ZPageAge::old ? ZGenerationId::old : ZGenerationId::young;
    bool promotion = _forwarding->age_from() != ZPageAge::old &&
                     _forwarding->age_to() == ZPageAge::old;
    // Promotions happen through a new cloned page
    ZPage* new_page = promotion ? new ZPage(*prev_page) : prev_page;
    new_page->reset(new_generation, new_age, ZPage::InPlaceReset);

    if (promotion) {
      // Register the the promotion
      ZHeap::heap()->minor_collector()->promote_reloc(prev_page, new_page);
    }

    return new_page;
  }

  void relocate_object(oop obj) {
    const zaddress addr = to_zaddress(obj);
    assert(ZHeap::heap()->is_object_live(addr), "Should be live");

    while (!try_relocate_object(addr)) {
      // Allocate a new target page, or if that fails, use the page being
      // relocated as the new target, which will cause it to be relocated
      // in-place.
      _target = _allocator->alloc_target_page(_forwarding, _target);
      if (_target != NULL) {
        continue;
      }

      // Start in-place relocation to block other threads from accessing
      // the page, or its forwarding table, until it has been released
      // (relocation completed).
      _target = start_in_place_relocation();
    }
  }

public:
  ZRelocateWork(Allocator* allocator) :
      _allocator(allocator),
      _forwarding(NULL),
      _target(NULL) {}

  ~ZRelocateWork() {
    _allocator->free_target_page(_target);
  }

  void do_forwarding(ZForwarding* forwarding) {
    _forwarding = forwarding;

    // Check if we should abort
    if (ZAbort::should_abort()) {
      _forwarding->abort_page();
      return;
    }

    // Relocate objects
    _forwarding->object_iterate([&](oop obj) { relocate_object(obj); });

    // Verify
    if (ZVerifyForwarding) {
      _forwarding->verify();
    }

    // Deal with in-place relocation
    const bool in_place = _forwarding->in_place_relocation();
    if (in_place) {
      // We are done with the from_space copy of the page
      _forwarding->in_place_relocation_finish();
    }

    // Release relocated page
    _forwarding->release_page();

    if (in_place) {
      // The relocated page has been relocated in-place and should not
      // be freed. Keep it as target page until it is full, and offer to
      // share it with other worker threads.
      _allocator->share_target_page(_target);
    } else {
      // Detach and free relocated page
      ZPage* const page = _forwarding->detach_page();
      _allocator->free_relocated_page(page);
    }
  }
};

class ZFixStoreBufferThreadClosure : public ThreadClosure {
public:
  virtual void do_thread(Thread* thread) {
    JavaThread* jt = JavaThread::cast(thread);
    ZStoreBarrierBuffer* buffer = ZThreadLocalData::store_barrier_buffer(jt);
    buffer->install_base_pointers();
  }
};

class ZFixStoreBufferTask : public ZTask {
private:
  ZJavaThreadsIterator _threads_iter;

public:
  ZFixStoreBufferTask() :
    ZTask("ZFixStoreBufferTask"),
    _threads_iter() {}

  virtual void work() {
    // Fix up store barrier buffer base pointers before relocating and releasing pages
    ZFixStoreBufferThreadClosure fix_store_buffer_cl;
    _threads_iter.apply(&fix_store_buffer_cl);
  }
};

class ZRelocateTask : public ZTask {
private:
  ZRelocationSetParallelIterator _iter;
  ZCollector* const              _collector;
  ZRelocateQueue* const          _queue;
  ZRelocateSmallAllocator        _survivor_small_allocator;
  ZRelocateMediumAllocator       _survivor_medium_allocator;
  ZRelocateSmallAllocator        _old_small_allocator;
  ZRelocateMediumAllocator       _old_medium_allocator;

  static bool is_small(ZForwarding* forwarding) {
    return forwarding->type() == ZPageTypeSmall;
  }

public:
  ZRelocateTask(ZRelocationSet* relocation_set, ZRelocateQueue* queue) :
      ZTask("ZRelocateTask"),
      _iter(relocation_set),
      _collector(relocation_set->collector()),
      _queue(queue),
      _survivor_small_allocator(ZGenerationId::young, ZPageAge::survivor),
      _survivor_medium_allocator(ZGenerationId::young, ZPageAge::survivor),
      _old_small_allocator(ZGenerationId::old, ZPageAge::old),
      _old_medium_allocator(ZGenerationId::old, ZPageAge::old) {}

  ~ZRelocateTask() {
    _collector->stat_relocation()->set_at_relocate_end(_survivor_small_allocator.in_place_count() + _old_small_allocator.in_place_count(),
                                                       _survivor_medium_allocator.in_place_count() + _old_medium_allocator.in_place_count());
  }

  virtual void work() {
    ZRelocateWork<ZRelocateSmallAllocator> survivor_small(&_survivor_small_allocator);
    ZRelocateWork<ZRelocateMediumAllocator> survivor_medium(&_survivor_medium_allocator);
    ZRelocateWork<ZRelocateSmallAllocator> old_small(&_old_small_allocator);
    ZRelocateWork<ZRelocateMediumAllocator> old_medium(&_old_medium_allocator);

    bool synchronized = false;

    const auto do_forwarding = [&](ZForwarding* forwarding) {
      if (forwarding->claim()) {
        ZPage* page = forwarding->page();
        ZPageAge to_age = forwarding->age_to();
        if (is_small(forwarding)) {
          ZRelocateWork<ZRelocateSmallAllocator>* small = to_age == ZPageAge::old ? &old_small : &survivor_small;
          small->do_forwarding(forwarding);
        } else {
          ZRelocateWork<ZRelocateMediumAllocator>* medium = to_age == ZPageAge::old ? &old_medium : &survivor_medium;
          medium->do_forwarding(forwarding);
        }
      }
    };

    for (ZForwarding* iter_forwarding; _iter.next(&iter_forwarding);) {
      // Relocate page
      do_forwarding(iter_forwarding);

      // Prioritize relocation of pages other threads are waiting for
      for (ZForwarding* queue_forwarding; _queue->poll(&queue_forwarding, &synchronized);) {
        do_forwarding(queue_forwarding);
      }
    }

    _queue->leave();
  }
};

static void remap_and_maybe_add_remset(volatile zpointer* p) {
  const zpointer ptr = Atomic::load(p);

  if (ZPointer::is_store_good(ptr)) {
    // Already has a remset entry
    return;
  }

  // Remset entries are used for two reasons:
  // 1) Minor marking old-to-young pointer roots
  // 2) Deferred remapping of stale old-to-young pointers
  //
  // This load barrier will up-front perform the remapping of (2),
  // and the code below only has to make sure we register up-to-date
  // old-to-young pointers for (1).
  const zaddress addr = ZBarrier::load_barrier_on_oop_field_preloaded(p, ptr);

  if (is_null(addr)) {
    // No need for remset entries for NULL pointers
    return;
  }

  if (ZHeap::heap()->is_old(addr)) {
    // No need for remset entries for pointers to old gen
    return;
  }

  ZRelocate::add_remset(p);
}

class ZRelocateAddRemsetForInPlacePromoted : public ZTask {
private:
  ZArrayParallelIterator<ZPage*> _iter;

public:
  ZRelocateAddRemsetForInPlacePromoted(ZArray<ZPage*>* pages) :
      ZTask("ZRelocateAddRemsetForInPlacePromoted"),
      _iter(pages) {}

  virtual void work() {
    SuspendibleThreadSetJoiner sts_joiner;

    for (ZPage* page; _iter.next(&page);) {
      page->object_iterate([&](oop obj) {
        ZIterator::basic_oop_iterate_safe(obj, remap_and_maybe_add_remset);
      });

      SuspendibleThreadSet::yield();
    }
  }
};

class ZRelocateAddRemsetForNormalPromoted : public ZTask {
private:
  ZForwardingTableParallelIterator _iter;

public:
  ZRelocateAddRemsetForNormalPromoted() :
      ZTask("ZRelocateAddRemsetForNormalPromoted"),
      _iter(ZHeap::heap()->minor_collector()->forwarding_table()) {}

  virtual void work() {
    SuspendibleThreadSetJoiner sts_joiner;

    _iter.do_forwardings([](ZForwarding* forwarding) {
      forwarding->oops_do_in_forwarded_via_table(remap_and_maybe_add_remset);

      SuspendibleThreadSet::yield();
    });
  }
};
void ZRelocate::relocate(ZRelocationSet* relocation_set) {
  {
    ZFixStoreBufferTask buffer_task;
    workers()->run(&buffer_task);
  }

  {
    ZRelocateTask relocate_task(relocation_set, &_queue);
    workers()->run(&relocate_task);
  }

  if (relocation_set->collector()->is_minor()) {
    ZStatTimerMinor timer(ZSubPhaseConcurrentMinorRelocateRemsetFlipPagePromoted);
    ZRelocateAddRemsetForInPlacePromoted task(relocation_set->promote_flip_pages());
    workers()->run(&task);
  }

  if (relocation_set->collector()->is_minor() && ZRelocateRemsetStrategy == 2) {
    ZStatTimerMinor timer(ZSubPhaseConcurrentMinorRelocateRemsetNormalPromoted);
    ZRelocateAddRemsetForNormalPromoted task;
    workers()->run(&task);
  }

  _queue.clear();
}

// FIXME: Temporary here because of accessBackend.inline.hpp circular dependencies
template <typename Function>
inline void ZPage::object_iterate(Function function) {
  auto do_bit = [&](BitMap::idx_t index) -> bool {
    const oop obj = object_from_bit_index(index);

    // Apply function
    function(obj);

    return true;
  };

  _livemap.iterate(generation_id(), do_bit);
}

void ZRelocate::promote_pages(const ZArray<ZPage*>* pages) {
  SuspendibleThreadSetJoiner sts_joiner;

  const bool promote_all = ZCollectedHeap::heap()->driver_major()->promote_all();

  // TODO: Make multi-threaded
  for (int i = 0; i < pages->length(); i++) {
    ZPage* prev_page = pages->at(i);
    ZPageAge age_from = prev_page->age();
    ZPageAge age_to = ZForwarding::compute_age_to(age_from, promote_all);
    assert(age_from != ZPageAge::old, "invalid age for a minor collection");

    // Figure out if this is proper promotion
    const ZGenerationId generation_to = age_to == ZPageAge::old ? ZGenerationId::old : ZGenerationId::young;
    const bool promotion = age_to == ZPageAge::old;

    // Logging
    prev_page->log_msg(promotion ? " (in-place promoted)" : " (in-place survived)");

    // Setup to-space page
    ZPage* new_page = promotion ? new ZPage(*prev_page) : prev_page;
    new_page->reset(generation_to, age_to, ZPage::FlipReset);

    if (promotion) {
      ZHeap::heap()->minor_collector()->promote_flip(prev_page, new_page);
    }

    SuspendibleThreadSet::yield();
  }
}

void ZRelocate::synchronize() {
  _queue.synchronize();
}

void ZRelocate::desynchronize() {
  _queue.desynchronize();
}
