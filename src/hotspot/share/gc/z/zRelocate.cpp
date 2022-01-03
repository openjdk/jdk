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
#include "gc/z/zAllocator.inline.hpp"
#include "gc/z/zBarrier.inline.hpp"
#include "gc/z/zCollectedHeap.hpp"
#include "gc/z/zCollector.inline.hpp"
#include "gc/z/zForwarding.inline.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zIndexDistributor.inline.hpp"
#include "gc/z/zIterator.inline.hpp"
#include "gc/z/zPage.inline.hpp"
#include "gc/z/zPageAge.hpp"
#include "gc/z/zRelocate.hpp"
#include "gc/z/zRelocationSet.inline.hpp"
#include "gc/z/zRootsIterator.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zStackWatermark.hpp"
#include "gc/z/zTask.hpp"
#include "gc/z/zUncoloredRoot.inline.hpp"
#include "gc/z/zWorkers.hpp"
#include "prims/jvmtiTagMap.hpp"
#include "runtime/atomic.hpp"
#include "utilities/debug.hpp"

static const ZStatSubPhase ZSubPhaseConcurrentRelocateRememberedSetFlipPromotedYoung("Concurrent Relocate Remset FP (Young)");
static const ZStatSubPhase ZSubPhaseConcurrentRelocateRememberedSetNormalPromotedYoung("Concurrent Relocate Remset NP (Young)");

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
  if (forwarding->retain_page()) {
    _queue.append(forwarding);
    forwarding->release_page();
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
  assert(_nworkers == 0, "Invalid state");
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

  ZAllocatorForRelocation* const allocator = forwarding->to_age() == ZPageAge::old
      ? (ZAllocatorForRelocation*) ZAllocator::old()
      : (ZAllocatorForRelocation*) ZAllocator::survivor();

  const zaddress to_addr = allocator->alloc_object(size);

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
    allocator->undo_alloc_object(to_addr, size);
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

    if (ZAbort::should_abort()) {
      // Prevent repeated queueing and logging if we have aborted
      return forwarding_insert(forwarding, safe(from_addr), safe(from_addr), &cursor);
    }

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

static ZPage* alloc_page(ZAllocatorForRelocation* allocator, ZPageType type, size_t size) {
  if (ZStressRelocateInPlace) {
    // Simulate failure to allocate a new page. This will
    // cause the page being relocated to be relocated in-place.
    return NULL;
  }

  ZAllocationFlags flags;
  flags.set_non_blocking();
  flags.set_gc_relocation();

  return allocator->alloc_page_for_relocation(type, size, flags);
}

static void retire_target_page(ZCollector* collector, ZPage* page) {
  if (collector->is_young() && page->is_old()) {
    collector->increase_promoted(page->used());
  } else {
    collector->increase_compacted(page->used());
  }

  // Free target page if it is empty. We can end up with an empty target
  // page if we allocated a new target page, and then lost the race to
  // relocate the remaining objects, leaving the target page empty when
  // relocation completed.
  if (page->used() == 0) {
    ZHeap::heap()->free_page(page);
  }
}

class ZRelocateSmallAllocator {
private:
  ZCollector* const              _collector;
  ZAllocatorForRelocation* const _allocator;
  volatile size_t                _in_place_count;

public:
  ZRelocateSmallAllocator(ZCollector* collector, ZAllocatorForRelocation* allocator) :
      _collector(collector),
      _allocator(allocator),
      _in_place_count(0) {}

  ZPage* alloc_and_retire_target_page(ZForwarding* forwarding, ZPage* target) {
    ZPage* const page = alloc_page(_allocator, forwarding->type(), forwarding->size());
    if (page == NULL) {
      Atomic::inc(&_in_place_count);
    }

    if (target != NULL) {
      // Retire the old target page
      retire_target_page(_collector, target);
    }

    return page;
  }

  void share_target_page(ZPage* page) {
    // Does nothing
  }

  void free_target_page(ZPage* page) {
    if (page != NULL) {
      retire_target_page(_collector, page);
    }
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
  ZCollector* const              _collector;
  ZAllocatorForRelocation* const _allocator;
  ZConditionLock                 _lock;
  ZPage*                         _shared;
  bool                           _in_place;
  volatile size_t                _in_place_count;

public:
  ZRelocateMediumAllocator(ZCollector* collector, ZAllocatorForRelocation* allocator) :
      _collector(collector),
      _allocator(allocator),
      _lock(),
      _shared(NULL),
      _in_place(false),
      _in_place_count(0) {}

  ~ZRelocateMediumAllocator() {
    if (_shared != NULL) {
      retire_target_page(_collector, _shared);
    }
  }

  ZPage* alloc_and_retire_target_page(ZForwarding* forwarding, ZPage* target) {
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
      _shared = alloc_page(_allocator, forwarding->type(), forwarding->size());
      if (_shared == NULL) {
        Atomic::inc(&_in_place_count);
        _in_place = true;
      }

      // This thread is responsible for retiring the shared target page
      if (target != NULL) {
        retire_target_page(_collector, target);
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
  Allocator* const  _allocator;
  ZForwarding*      _forwarding;
  ZPage*            _target;
  ZCollector* const _collector;
  size_t            _other_promoted;
  size_t            _other_compacted;

  size_t object_alignment() const {
    return 1 << _forwarding->object_alignment_shift();
  }

  void increase_other_forwarded(size_t unaligned_object_size) {
    const size_t aligned_size = align_up(unaligned_object_size, object_alignment());
    if (_forwarding->is_promotion()) {
      _other_promoted += aligned_size;
    } else {
      _other_compacted += aligned_size;
    }
  }

  zaddress try_relocate_object_inner(zaddress from_addr) {
    ZForwardingCursor cursor;

    const size_t size = ZUtils::object_size(from_addr);

    // Lookup forwarding
    {
      const zaddress to_addr = forwarding_find(_forwarding, from_addr, &cursor);
      if (!is_null(to_addr)) {
        // Already relocated
        increase_other_forwarded(size);
        return to_addr;
      }
    }

    // Allocate object
    const zaddress allocated_addr = _allocator->alloc_object(_target, size);
    if (is_null(allocated_addr)) {
      // Allocation failed
      return zaddress::null;
    }

    // Copy object. Use conjoint copying if we are relocating
    // in-place and the new object overlaps with the old object.
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
      increase_other_forwarded(size);
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

    ZRememberedSetIterator iter = from_page->remset_iterator_current_limited(from_local_offset, size);
    for (uintptr_t field_local_offset; iter.next(&field_local_offset);) {
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

    assert(ZPointer::is_old_load_good(ptr), "Should be at least old load good: " PTR_FORMAT, untype(ptr));

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
      ZBarrier::remap_young_relocated(p, ptr);
      return;
    }

    zaddress_unsafe addr_unsafe = ZPointer::uncolor_unsafe(ptr);
    ZForwarding* forwarding = ZCollector::young()->forwarding(addr_unsafe);

    if (forwarding == NULL) {
      // Object isn't being relocated
      zaddress addr = safe(addr_unsafe);
      if (!add_remset_if_young(p, addr)) {
        // Not young - eagerly remap to skip adding a remset entry just to get deferred remapping
        ZBarrier::remap_young_relocated(p, ptr);
      }
      return;
    }

    zaddress addr = forwarding->find(addr_unsafe);

    if (!is_null(addr)) {
      // Object has already been relocated
      if (!add_remset_if_young(p, addr)) {
        // Not young - eagerly remap to skip adding a remset entry just to get deferred remapping
        ZBarrier::remap_young_relocated(p, ptr);
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
    if (_forwarding->to_age() == ZPageAge::old) {
      // Need to deal with remset when moving stuff to old
      if (_forwarding->from_age() == ZPageAge::old) {
        update_remset_old_to_old(from_addr, to_addr);
      } else {
        update_remset_promoted(to_addr);
      }
    }
  }

  bool try_relocate_object(zaddress from_addr) {
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
    ZPageAge new_age = _forwarding->to_age();
    bool promotion = _forwarding->is_promotion();
    // Promotions happen through a new cloned page
    ZPage* new_page = promotion ? prev_page->clone_limited() : prev_page;
    new_page->reset(new_age, ZPageResetType::InPlaceRelocation);

    if (promotion) {
      // Register the the promotion
      ZCollector::young()->in_place_relocate_promote(prev_page, new_page);
      ZCollector::young()->register_in_place_relocate_promoted(prev_page);
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
      _target = _allocator->alloc_and_retire_target_page(_forwarding, _target);
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
  ZRelocateWork(Allocator* allocator, ZCollector* collector) :
      _allocator(allocator),
      _forwarding(NULL),
      _target(NULL),
      _collector(collector),
      _other_promoted(0),
      _other_compacted(0) {}

  ~ZRelocateWork() {
    _allocator->free_target_page(_target);
    // Report statistics on-behalf of non-worker threads
    _collector->increase_promoted(_other_promoted);
    _collector->increase_compacted(_other_compacted);
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

    _collector->increase_freed(_forwarding->page()->size());

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
      ZHeap::heap()->free_page(page);
    }
  }
};

class ZRelocateStoreBufferInstallBasePointersThreadClosure : public ThreadClosure {
public:
  virtual void do_thread(Thread* thread) {
    JavaThread* jt = JavaThread::cast(thread);
    ZStoreBarrierBuffer* buffer = ZThreadLocalData::store_barrier_buffer(jt);
    buffer->install_base_pointers();
  }
};

// Installs the object base pointers (object starts), for the fields written
// in the store buffer. The code that searches for the object start uses that
// liveness information stored in the pages. That information is lost when the
// pages have been relocated and then destroyed.
class ZRelocateStoreBufferInstallBasePointersTask : public ZTask {
private:
  ZJavaThreadsIterator _threads_iter;

public:
  ZRelocateStoreBufferInstallBasePointersTask() :
    ZTask("ZRelocateStoreBufferInstallBasePointersTask"),
    _threads_iter() {}

  virtual void work() {
    ZRelocateStoreBufferInstallBasePointersThreadClosure fix_store_buffer_cl;
    _threads_iter.apply(&fix_store_buffer_cl);
  }
};

class ZRelocateTask : public ZRestartableTask {
private:
  ZRelocationSetParallelIterator _iter;
  ZCollector* const              _collector;
  ZRelocateQueue* const          _queue;
  ZRelocateSmallAllocator        _survivor_small_allocator;
  ZRelocateMediumAllocator       _survivor_medium_allocator;
  ZRelocateSmallAllocator        _old_small_allocator;
  ZRelocateMediumAllocator       _old_medium_allocator;

public:
  ZRelocateTask(ZRelocationSet* relocation_set, ZRelocateQueue* queue) :
      ZRestartableTask("ZRelocateTask"),
      _iter(relocation_set),
      _collector(relocation_set->collector()),
      _queue(queue),
      _survivor_small_allocator(_collector, ZAllocator::survivor()),
      _survivor_medium_allocator(_collector, ZAllocator::survivor()),
      _old_small_allocator(_collector, ZAllocator::old()),
      _old_medium_allocator(_collector, ZAllocator::old()) {}

  ~ZRelocateTask() {
    _collector->stat_relocation()->at_relocate_end(_survivor_small_allocator.in_place_count() + _old_small_allocator.in_place_count(),
                                                   _survivor_medium_allocator.in_place_count() + _old_medium_allocator.in_place_count());
  }

  virtual void work() {
    ZRelocateWork<ZRelocateSmallAllocator> survivor_small(&_survivor_small_allocator, _collector);
    ZRelocateWork<ZRelocateMediumAllocator> survivor_medium(&_survivor_medium_allocator, _collector);
    ZRelocateWork<ZRelocateSmallAllocator> old_small(&_old_small_allocator, _collector);
    ZRelocateWork<ZRelocateMediumAllocator> old_medium(&_old_medium_allocator, _collector);

    bool synchronized = false;

    const auto do_forwarding = [&](ZForwarding* forwarding) {
      if (forwarding->claim()) {
        ZPage* page = forwarding->page();
        ZPageAge to_age = forwarding->to_age();
        if (page->is_small()) {
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

      // Check if we should resize threads
      if (_collector->should_worker_resize()) {
        break;
      }
    }

    _queue->leave();
  }

  virtual void resize_workers(uint nworkers) {
    _queue->join(nworkers);
  }
};

static void remap_and_maybe_add_remset(volatile zpointer* p) {
  const zpointer ptr = Atomic::load(p);

  if (ZPointer::is_store_good(ptr)) {
    // Already has a remset entry
    return;
  }

  // Remset entries are used for two reasons:
  // 1) Young marking old-to-young pointer roots
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

class ZRelocateAddRemsetForFlipPromoted : public ZRestartableTask {
private:
  ZStatTimerYoung                _timer;
  ZArrayParallelIterator<ZPage*> _iter;

public:
  ZRelocateAddRemsetForFlipPromoted(ZArray<ZPage*>* pages) :
      ZRestartableTask("ZRelocateAddRemsetForFlipPromoted"),
      _timer(ZSubPhaseConcurrentRelocateRememberedSetFlipPromotedYoung),
      _iter(pages) {}

  virtual void work() {
    SuspendibleThreadSetJoiner sts_joiner;

    for (ZPage* page; _iter.next(&page);) {
      page->object_iterate([&](oop obj) {
        ZIterator::basic_oop_iterate_safe(obj, remap_and_maybe_add_remset);
      });

      SuspendibleThreadSet::yield();
      if (ZCollector::young()->should_worker_stop()) {
        return;
      }
    }
  }
};

class ZRelocateAddRemsetForNormalPromoted : public ZRestartableTask {
private:
  ZStatTimerYoung                  _timer;
  ZForwardingTableParallelIterator _iter;

public:
  ZRelocateAddRemsetForNormalPromoted() :
      ZRestartableTask("ZRelocateAddRemsetForNormalPromoted"),
      _timer(ZSubPhaseConcurrentRelocateRememberedSetNormalPromotedYoung),
      _iter(ZCollector::young()->forwarding_table()) {}

  virtual void work() {
    SuspendibleThreadSetJoiner sts_joiner;

    _iter.do_forwardings([](ZForwarding* forwarding) {
      forwarding->oops_do_in_forwarded_via_table(remap_and_maybe_add_remset);

      SuspendibleThreadSet::yield();
      return !ZCollector::young()->should_worker_stop();
    });
  }
};

void ZRelocate::relocate(ZRelocationSet* relocation_set) {
  {
    // Install the store buffer's base pointers before the
    // relocate task destroys the liveness information in
    // the relocated pages.
    ZRelocateStoreBufferInstallBasePointersTask buffer_task;
    workers()->run(&buffer_task);
  }

  {
    ZRelocateTask relocate_task(relocation_set, &_queue);
    workers()->run(&relocate_task);
  }

  if (relocation_set->collector()->is_young()) {
    ZRelocateAddRemsetForFlipPromoted task(relocation_set->flip_promoted_pages());
    workers()->run(&task);
  }

  if (relocation_set->collector()->is_young() && ZRelocateRemsetStrategy == 2) {
    ZRelocateAddRemsetForNormalPromoted task;
    workers()->run(&task);
  }

  _queue.clear();
}

ZPageAge ZRelocate::compute_to_age(ZPageAge from_age, bool promote_all) {
  if (promote_all) {
    return ZPageAge::old;
  } else if (from_age == ZPageAge::eden) {
    return ZPageAge::survivor;
  } else {
    return ZPageAge::old;
  }
}

class ZFlipAgePagesTask : public ZTask {
private:
  ZArrayParallelIterator<ZPage*> _iter;
  const bool                     _promote_all;

public:
  ZFlipAgePagesTask(const ZArray<ZPage*>* pages, bool promote_all) :
      ZTask("ZPromotePagesTask"),
      _iter(pages),
      _promote_all(promote_all) {}

  virtual void work() {
    SuspendibleThreadSetJoiner sts_joiner;
    ZArray<ZPage*> promoted_pages;

    for (ZPage* prev_page; _iter.next(&prev_page);) {
      const ZPageAge from_age = prev_page->age();
      const ZPageAge to_age = ZRelocate::compute_to_age(from_age, _promote_all);
      assert(from_age != ZPageAge::old, "invalid age for a young collection");

      // Figure out if this is proper promotion
      const bool promotion = to_age == ZPageAge::old;

      // Logging
      prev_page->log_msg(promotion ? " (flip promoted)" : " (flip survived)");

      // Setup to-space page
      ZPage* const new_page = promotion ? prev_page->clone_limited_promote_flipped() : prev_page;
      new_page->reset(to_age, ZPageResetType::FlipAging);

      if (promotion) {
        ZCollector::young()->flip_promote(prev_page, new_page);
        // Defer promoted page registration times the lock is taken
        promoted_pages.push(prev_page);
      }

      SuspendibleThreadSet::yield();
    }

    ZCollector::young()->register_flip_promoted(promoted_pages);
  }
};

void ZRelocate::flip_age_pages(const ZArray<ZPage*>* pages, bool promote_all) {
  ZFlipAgePagesTask flip_age_task(pages, promote_all);
  workers()->run(&flip_age_task);
}

void ZRelocate::synchronize() {
  _queue.synchronize();
}

void ZRelocate::desynchronize() {
  _queue.desynchronize();
}
