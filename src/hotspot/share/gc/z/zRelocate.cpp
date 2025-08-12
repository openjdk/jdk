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

#include "gc/shared/gc_globals.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "gc/z/zAbort.inline.hpp"
#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zBarrier.inline.hpp"
#include "gc/z/zCollectedHeap.hpp"
#include "gc/z/zForwarding.inline.hpp"
#include "gc/z/zGeneration.inline.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zIndexDistributor.inline.hpp"
#include "gc/z/zIterator.inline.hpp"
#include "gc/z/zObjectAllocator.hpp"
#include "gc/z/zPage.inline.hpp"
#include "gc/z/zPageAge.inline.hpp"
#include "gc/z/zRelocate.hpp"
#include "gc/z/zRelocationSet.inline.hpp"
#include "gc/z/zRootsIterator.hpp"
#include "gc/z/zStackWatermark.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zStringDedup.inline.hpp"
#include "gc/z/zTask.hpp"
#include "gc/z/zUncoloredRoot.inline.hpp"
#include "gc/z/zVerify.hpp"
#include "gc/z/zWorkers.hpp"
#include "prims/jvmtiTagMap.hpp"
#include "runtime/atomic.hpp"
#include "utilities/debug.hpp"

static const ZStatCriticalPhase ZCriticalPhaseRelocationStall("Relocation Stall");
static const ZStatSubPhase ZSubPhaseConcurrentRelocateRememberedSetFlipPromotedYoung("Concurrent Relocate Remset FP", ZGenerationId::young);

ZRelocateQueue::ZRelocateQueue()
  : _lock(),
    _queue(),
    _nworkers(0),
    _nsynchronized(0),
    _synchronize(false),
    _is_active(false),
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

void ZRelocateQueue::activate(uint nworkers) {
  _is_active = true;
  join(nworkers);
}

void ZRelocateQueue::deactivate() {
  Atomic::store(&_is_active, false);
  clear();
}

bool ZRelocateQueue::is_active() const {
  return Atomic::load(&_is_active);
}

void ZRelocateQueue::join(uint nworkers) {
  assert(nworkers != 0, "Must request at least one worker");
  assert(_nworkers == 0, "Invalid state");
  assert(_nsynchronized == 0, "Invalid state");

  log_debug(gc, reloc)("Joining workers: %u", nworkers);

  _nworkers = nworkers;
}

void ZRelocateQueue::resize_workers(uint nworkers) {
  assert(nworkers != 0, "Must request at least one worker");
  assert(_nworkers == 0, "Invalid state");
  assert(_nsynchronized == 0, "Invalid state");

  log_debug(gc, reloc)("Resize workers: %u", nworkers);

  ZLocker<ZConditionLock> locker(&_lock);
  _nworkers = nworkers;
}

void ZRelocateQueue::leave() {
  ZLocker<ZConditionLock> locker(&_lock);
  _nworkers--;

  assert(_nsynchronized <= _nworkers, "_nsynchronized: %u _nworkers: %u", _nsynchronized, _nworkers);

  log_debug(gc, reloc)("Leaving workers: left: %u _synchronize: %d _nsynchronized: %u", _nworkers, _synchronize, _nsynchronized);

  // Prune done forwardings
  const bool forwardings_done = prune();

  // Check if all workers synchronized
  const bool last_synchronized = _synchronize && _nworkers == _nsynchronized;

  if (forwardings_done || last_synchronized) {
    _lock.notify_all();
  }
}

void ZRelocateQueue::add_and_wait(ZForwarding* forwarding) {
  ZStatTimer timer(ZCriticalPhaseRelocationStall);
  ZLocker<ZConditionLock> locker(&_lock);

  if (forwarding->is_done()) {
    return;
  }

  _queue.append(forwarding);
  if (_queue.length() == 1) {
    // Queue became non-empty
    inc_needs_attention();
    _lock.notify_all();
  }

  while (!forwarding->is_done()) {
    _lock.wait();
  }
}

bool ZRelocateQueue::prune() {
  if (_queue.is_empty()) {
    return false;
  }

  bool done = false;

  for (int i = 0; i < _queue.length();) {
    const ZForwarding* const forwarding = _queue.at(i);
    if (forwarding->is_done()) {
      done = true;

      _queue.delete_at(i);
    } else {
      i++;
    }
  }

  if (_queue.is_empty()) {
    dec_needs_attention();
  }

  return done;
}

ZForwarding* ZRelocateQueue::prune_and_claim() {
  if (prune()) {
    _lock.notify_all();
  }

  for (int i = 0; i < _queue.length(); i++) {
    ZForwarding* const forwarding = _queue.at(i);
    if (forwarding->claim()) {
      return forwarding;
    }
  }

  return nullptr;
}

class ZRelocateQueueSynchronizeThread {
private:
  ZRelocateQueue* const _queue;

public:
  ZRelocateQueueSynchronizeThread(ZRelocateQueue* queue)
    : _queue(queue) {
    _queue->synchronize_thread();
  }

  ~ZRelocateQueueSynchronizeThread() {
    _queue->desynchronize_thread();
  }
};

void ZRelocateQueue::synchronize_thread() {
  _nsynchronized++;

  log_debug(gc, reloc)("Synchronize worker _nsynchronized %u", _nsynchronized);

  assert(_nsynchronized <= _nworkers, "_nsynchronized: %u _nworkers: %u", _nsynchronized, _nworkers);
  if (_nsynchronized == _nworkers) {
    // All workers synchronized
    _lock.notify_all();
  }
}

void ZRelocateQueue::desynchronize_thread() {
  _nsynchronized--;

  log_debug(gc, reloc)("Desynchronize worker _nsynchronized %u", _nsynchronized);

  assert(_nsynchronized < _nworkers, "_nsynchronized: %u _nworkers: %u", _nsynchronized, _nworkers);
}

ZForwarding* ZRelocateQueue::synchronize_poll() {
  // Fast path avoids locking
  if (!needs_attention()) {
    return nullptr;
  }

  // Slow path to get the next forwarding and/or synchronize
  ZLocker<ZConditionLock> locker(&_lock);

  {
    ZForwarding* const forwarding = prune_and_claim();
    if (forwarding != nullptr) {
      // Don't become synchronized while there are elements in the queue
      return forwarding;
    }
  }

  if (!_synchronize) {
    return nullptr;
  }

  ZRelocateQueueSynchronizeThread rqst(this);

  do {
    _lock.wait();

    ZForwarding* const forwarding = prune_and_claim();
    if (forwarding != nullptr) {
      return forwarding;
    }
  } while (_synchronize);

  return nullptr;
}

void ZRelocateQueue::clear() {
  assert(_nworkers == 0, "Invalid state");

  if (_queue.is_empty()) {
    return;
  }

  ZArrayIterator<ZForwarding*> iter(&_queue);
  for (ZForwarding* forwarding; iter.next(&forwarding);) {
    assert(forwarding->is_done(), "All should be done");
  }

  assert(false, "Clear was not empty");

  _queue.clear();
  dec_needs_attention();
}

void ZRelocateQueue::synchronize() {
  ZLocker<ZConditionLock> locker(&_lock);
  _synchronize = true;

  inc_needs_attention();

  log_debug(gc, reloc)("Synchronize all workers 1 _nworkers: %u _nsynchronized: %u", _nworkers, _nsynchronized);

  while (_nworkers != _nsynchronized) {
    _lock.wait();
    log_debug(gc, reloc)("Synchronize all workers 2 _nworkers: %u _nsynchronized: %u", _nworkers, _nsynchronized);
  }
}

void ZRelocateQueue::desynchronize() {
  ZLocker<ZConditionLock> locker(&_lock);
  _synchronize = false;

  log_debug(gc, reloc)("Desynchronize all workers _nworkers: %u _nsynchronized: %u", _nworkers, _nsynchronized);

  assert(_nsynchronized <= _nworkers, "_nsynchronized: %u _nworkers: %u", _nsynchronized, _nworkers);

  dec_needs_attention();

  _lock.notify_all();
}

ZRelocate::ZRelocate(ZGeneration* generation)
  : _generation(generation),
    _queue() {}

ZWorkers* ZRelocate::workers() const {
  return _generation->workers();
}

void ZRelocate::start() {
  _queue.activate(workers()->active_workers());
}

void ZRelocate::add_remset(volatile zpointer* p) {
  ZGeneration::young()->remember(p);
}

static zaddress relocate_object_inner(ZForwarding* forwarding, zaddress from_addr, ZForwardingCursor* cursor) {
  assert(ZHeap::heap()->is_object_live(from_addr), "Should be live");

  // Allocate object
  const size_t size = ZUtils::object_size(from_addr);
  const ZPageAge to_age = forwarding->to_age();

  const zaddress to_addr = ZHeap::heap()->alloc_object_for_relocation(size, to_age);

  if (is_null(to_addr)) {
    // Allocation failed
    return zaddress::null;
  }

  // Copy object
  ZUtils::object_copy_disjoint(from_addr, to_addr, size);

  // Insert forwarding
  const zaddress to_addr_final = forwarding->insert(from_addr, to_addr, cursor);

  if (to_addr_final != to_addr) {
    // Already relocated, try undo allocation
    ZHeap::heap()->undo_alloc_object_for_relocation(to_addr, size);
  }

  return to_addr_final;
}

zaddress ZRelocate::relocate_object(ZForwarding* forwarding, zaddress_unsafe from_addr) {
  ZForwardingCursor cursor;

  // Lookup forwarding
  zaddress to_addr = forwarding->find(from_addr, &cursor);
  if (!is_null(to_addr)) {
    // Already relocated
    return to_addr;
  }

  // Relocate object
  if (forwarding->retain_page(&_queue)) {
    assert(_generation->is_phase_relocate(), "Must be");
    to_addr = relocate_object_inner(forwarding, safe(from_addr), &cursor);
    forwarding->release_page();

    if (!is_null(to_addr)) {
      // Success
      return to_addr;
    }

    // Failed to relocate object. Signal and wait for a worker thread to
    // complete relocation of this page, and then forward the object.
    _queue.add_and_wait(forwarding);
  }

  // Forward object
  return forward_object(forwarding, from_addr);
}

zaddress ZRelocate::forward_object(ZForwarding* forwarding, zaddress_unsafe from_addr) {
  const zaddress to_addr = forwarding->find(from_addr);
  assert(!is_null(to_addr), "Should be forwarded: " PTR_FORMAT, untype(from_addr));
  return to_addr;
}

static ZPage* alloc_page(ZForwarding* forwarding) {
  if (ZStressRelocateInPlace) {
    // Simulate failure to allocate a new page. This will
    // cause the page being relocated to be relocated in-place.
    return nullptr;
  }

  const ZPageType type = forwarding->type();
  const size_t size = forwarding->size();
  const ZPageAge age = forwarding->to_age();

  ZAllocationFlags flags;
  flags.set_non_blocking();
  flags.set_gc_relocation();

  return ZHeap::heap()->alloc_page(type, size, flags, age);
}

static void retire_target_page(ZGeneration* generation, ZPage* page) {
  if (generation->is_young() && page->is_old()) {
    generation->increase_promoted(page->used());
  } else {
    generation->increase_compacted(page->used());
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
  ZGeneration* const _generation;
  volatile size_t    _in_place_count;

public:
  ZRelocateSmallAllocator(ZGeneration* generation)
    : _generation(generation),
      _in_place_count(0) {}

  ZPage* alloc_and_retire_target_page(ZForwarding* forwarding, ZPage* target) {
    ZPage* const page = alloc_page(forwarding);
    if (page == nullptr) {
      Atomic::inc(&_in_place_count);
    }

    if (target != nullptr) {
      // Retire the old target page
      retire_target_page(_generation, target);
    }

    return page;
  }

  void share_target_page(ZPage* page) {
    // Does nothing
  }

  void free_target_page(ZPage* page) {
    if (page != nullptr) {
      retire_target_page(_generation, page);
    }
  }

  zaddress alloc_object(ZPage* page, size_t size) const {
    return (page != nullptr) ? page->alloc_object(size) : zaddress::null;
  }

  void undo_alloc_object(ZPage* page, zaddress addr, size_t size) const {
    page->undo_alloc_object(addr, size);
  }

  size_t in_place_count() const {
    return _in_place_count;
  }
};

class ZRelocateMediumAllocator {
private:
  ZGeneration* const _generation;
  ZConditionLock     _lock;
  ZPage*             _shared[ZNumRelocationAges];
  bool               _in_place;
  volatile size_t    _in_place_count;

public:
  ZRelocateMediumAllocator(ZGeneration* generation)
    : _generation(generation),
      _lock(),
      _shared(),
      _in_place(false),
      _in_place_count(0) {}

  ~ZRelocateMediumAllocator() {
    for (uint i = 0; i < ZNumRelocationAges; ++i) {
      if (_shared[i] != nullptr) {
        retire_target_page(_generation, _shared[i]);
      }
    }
  }

  ZPage* shared(ZPageAge age) {
    return _shared[untype(age - 1)];
  }

  void set_shared(ZPageAge age, ZPage* page) {
    _shared[untype(age - 1)] = page;
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
    const ZPageAge to_age = forwarding->to_age();
    if (shared(to_age) == target) {
      ZPage* const to_page = alloc_page(forwarding);
      set_shared(to_age, to_page);
      if (to_page == nullptr) {
        Atomic::inc(&_in_place_count);
        _in_place = true;
      }

      // This thread is responsible for retiring the shared target page
      if (target != nullptr) {
        retire_target_page(_generation, target);
      }
    }

    return shared(to_age);
  }

  void share_target_page(ZPage* page) {
    const ZPageAge age = page->age();

    ZLocker<ZConditionLock> locker(&_lock);
    assert(_in_place, "Invalid state");
    assert(shared(age) == nullptr, "Invalid state");
    assert(page != nullptr, "Invalid page");

    set_shared(age, page);
    _in_place = false;

    _lock.notify_all();
  }

  void free_target_page(ZPage* page) {
    // Does nothing
  }

  zaddress alloc_object(ZPage* page, size_t size) const {
    return (page != nullptr) ? page->alloc_object_atomic(size) : zaddress::null;
  }

  void undo_alloc_object(ZPage* page, zaddress addr, size_t size) const {
    page->undo_alloc_object_atomic(addr, size);
  }

  size_t in_place_count() const {
    return _in_place_count;
  }
};

template <typename Allocator>
class ZRelocateWork : public StackObj {
private:
  Allocator* const    _allocator;
  ZForwarding*        _forwarding;
  ZPage*              _target[ZNumRelocationAges];
  ZGeneration* const  _generation;
  size_t              _other_promoted;
  size_t              _other_compacted;
  ZStringDedupContext _string_dedup_context;


  ZPage* target(ZPageAge age) {
    return _target[untype(age - 1)];
  }

  void set_target(ZPageAge age, ZPage* page) {
    _target[untype(age - 1)] = page;
  }

  size_t object_alignment() const {
    return (size_t)1 << _forwarding->object_alignment_shift();
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
    ZPage* const to_page = target(_forwarding->to_age());

    // Lookup forwarding
    {
      const zaddress to_addr = _forwarding->find(from_addr, &cursor);
      if (!is_null(to_addr)) {
        // Already relocated
        increase_other_forwarded(size);
        return to_addr;
      }
    }

    // Allocate object
    const zaddress allocated_addr = _allocator->alloc_object(to_page, size);
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
    const zaddress to_addr = _forwarding->insert(from_addr, allocated_addr, &cursor);
    if (to_addr != allocated_addr) {
      // Already relocated, undo allocation
      _allocator->undo_alloc_object(to_page, to_addr, size);
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

    // Note: even with in-place relocation, the to_page could be another page
    ZPage* const to_page = ZHeap::heap()->page(to_addr);

    // Uses _relaxed version to handle that in-place relocation resets _top
    assert(ZHeap::heap()->is_in_page_relaxed(from_page, from_addr), "Must be");
    assert(to_page->is_in(to_addr), "Must be");


    // Read the size from the to-object, since the from-object
    // could have been overwritten during in-place relocation.
    const size_t size = ZUtils::object_size(to_addr);

    // If a young generation collection started while the old generation
    // relocated  objects, the remember set bits were flipped from "current"
    // to "previous".
    //
    // We need to select the correct remembered sets bitmap to ensure that the
    // old remset bits are found.
    //
    // Note that if the young generation marking (remset scanning) finishes
    // before the old generation relocation has relocated this page, then the
    // young generation will visit this page's previous remembered set bits and
    // moved them over to the current bitmap.
    //
    // If the young generation runs multiple cycles while the old generation is
    // relocating, then the first cycle will have consumed the old remset,
    // bits and moved associated objects to a new old page. The old relocation
    // could find either of the two bitmaps. So, either it will find the original
    // remset bits for the page, or it will find an empty bitmap for the page. It
    // doesn't matter for correctness, because the young generation marking has
    // already taken care of the bits.

    const bool active_remset_is_current = ZGeneration::old()->active_remset_is_current();

    // When in-place relocation is done and the old remset bits are located in
    // the bitmap that is going to be used for the new remset bits, then we
    // need to clear the old bits before the new bits are inserted.
    const bool iterate_current_remset = active_remset_is_current && !in_place;

    BitMap::Iterator iter = iterate_current_remset
        ? from_page->remset_iterator_limited_current(from_local_offset, size)
        : from_page->remset_iterator_limited_previous(from_local_offset, size);

    for (BitMap::idx_t field_bit : iter) {
      const uintptr_t field_local_offset = ZRememberedSet::to_offset(field_bit);

      // Add remset entry in the to-page
      const uintptr_t offset = field_local_offset - from_local_offset;
      const zaddress to_field = to_addr + offset;
      log_trace(gc, reloc)("Remember: from: " PTR_FORMAT " to: " PTR_FORMAT " current: %d marking: %d page: " PTR_FORMAT " remset: " PTR_FORMAT,
          untype(from_page->start() + field_local_offset), untype(to_field), active_remset_is_current, ZGeneration::young()->is_phase_mark(), p2i(to_page), p2i(to_page->remset_current()));

      volatile zpointer* const p = (volatile zpointer*)to_field;

      if (ZGeneration::young()->is_phase_mark()) {
        // Young generation remembered set scanning needs to know about this
        // field. It will take responsibility to add a new remember set entry if needed.
        _forwarding->relocated_remembered_fields_register(p);
      } else {
        to_page->remember(p);
        if (in_place) {
          assert(to_page->is_remembered(p), "p: " PTR_FORMAT, p2i(p));
        }
      }
    }
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

    const zaddress_unsafe addr_unsafe = ZPointer::uncolor_unsafe(ptr);
    ZForwarding* const forwarding = ZGeneration::young()->forwarding(addr_unsafe);

    if (forwarding == nullptr) {
      // Object isn't being relocated
      const zaddress addr = safe(addr_unsafe);
      if (!add_remset_if_young(p, addr)) {
        // Not young - eagerly remap to skip adding a remset entry just to get deferred remapping
        ZBarrier::remap_young_relocated(p, ptr);
      }
      return;
    }

    const zaddress addr = forwarding->find(addr_unsafe);

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

  void update_remset_promoted(zaddress to_addr) const {
    ZIterator::basic_oop_iterate(to_oop(to_addr), update_remset_promoted_filter_and_remap_per_field);
  }

  void update_remset_for_fields(zaddress from_addr, zaddress to_addr) const {
    if (_forwarding->to_age() != ZPageAge::old) {
      // No remembered set in young pages
      return;
    }

    // Need to deal with remset when moving objects to the old generation
    if (_forwarding->from_age() == ZPageAge::old) {
      update_remset_old_to_old(from_addr, to_addr);
      return;
    }

    // Normal promotion
    update_remset_promoted(to_addr);
  }

  void maybe_string_dedup(zaddress to_addr) {
    if (_forwarding->is_promotion()) {
      // Only deduplicate promoted objects, and let short-lived strings simply die instead.
      _string_dedup_context.request(to_oop(to_addr));
    }
  }

  bool try_relocate_object(zaddress from_addr) {
    const zaddress to_addr = try_relocate_object_inner(from_addr);

    if (is_null(to_addr)) {
      return false;
    }

    update_remset_for_fields(from_addr, to_addr);

    maybe_string_dedup(to_addr);

    return true;
  }

  void start_in_place_relocation_prepare_remset(ZPage* from_page) {
    if (_forwarding->from_age() != ZPageAge::old) {
      // Only old pages have use remset bits
      return;
    }

    if (ZGeneration::old()->active_remset_is_current()) {
      // We want to iterate over and clear the remset bits of the from-space page,
      // and insert current bits in the to-space page. However, with in-place
      // relocation, the from-space and to-space pages are the same. Clearing
      // is destructive, and is difficult to perform before or during the iteration.
      // However, clearing of the current bits has to be done before exposing the
      // to-space objects in the forwarding table.
      //
      // To solve this tricky dependency problem, we start by stashing away the
      // current bits in the previous bits, and clearing the current bits
      // (implemented by swapping the bits). This way, the current bits are
      // cleared before copying the objects (like a normal to-space page),
      // and the previous bits are representing a copy of the current bits
      // of the from-space page, and are used for iteration.
      from_page->swap_remset_bitmaps();
    }
  }

  ZPage* start_in_place_relocation(zoffset relocated_watermark) {
    _forwarding->in_place_relocation_claim_page();
    _forwarding->in_place_relocation_start(relocated_watermark);

    ZPage* const from_page = _forwarding->page();

    const ZPageAge to_age = _forwarding->to_age();
    const bool promotion = _forwarding->is_promotion();

    // Promotions happen through a new cloned page
    ZPage* const to_page = promotion
        ? from_page->clone_for_promotion()
        : from_page->reset(to_age);

    // Reset page for in-place relocation
    to_page->reset_top_for_allocation();

    // Verify that the inactive remset is clear when resetting the page for
    // in-place relocation.
    if (from_page->age() == ZPageAge::old) {
      if (ZGeneration::old()->active_remset_is_current()) {
        to_page->verify_remset_cleared_previous();
      } else {
        to_page->verify_remset_cleared_current();
      }
    }

    // Clear remset bits for all objects that were relocated
    // before this page became an in-place relocated page.
    start_in_place_relocation_prepare_remset(from_page);

    if (promotion) {
      // Register the promotion
      ZGeneration::young()->in_place_relocate_promote(from_page, to_page);
      ZGeneration::young()->register_in_place_relocate_promoted(from_page);
    }

    return to_page;
  }

  void relocate_object(oop obj) {
    const zaddress addr = to_zaddress(obj);
    assert(ZHeap::heap()->is_object_live(addr), "Should be live");

    while (!try_relocate_object(addr)) {
      // Allocate a new target page, or if that fails, use the page being
      // relocated as the new target, which will cause it to be relocated
      // in-place.
      const ZPageAge to_age = _forwarding->to_age();
      ZPage* to_page = _allocator->alloc_and_retire_target_page(_forwarding, target(to_age));
      set_target(to_age, to_page);
      if (to_page != nullptr) {
        continue;
      }

      // Start in-place relocation to block other threads from accessing
      // the page, or its forwarding table, until it has been released
      // (relocation completed).
      to_page = start_in_place_relocation(ZAddress::offset(addr));
      set_target(to_age, to_page);
    }
  }

public:
  ZRelocateWork(Allocator* allocator, ZGeneration* generation)
    : _allocator(allocator),
      _forwarding(nullptr),
      _target(),
      _generation(generation),
      _other_promoted(0),
      _other_compacted(0) {}

  ~ZRelocateWork() {
    for (uint i = 0; i < ZNumRelocationAges; ++i) {
      _allocator->free_target_page(_target[i]);
    }
    // Report statistics on-behalf of non-worker threads
    _generation->increase_promoted(_other_promoted);
    _generation->increase_compacted(_other_compacted);
  }

  bool active_remset_is_current() const {
    // Normal old-to-old relocation can treat the from-page remset as a
    // read-only copy, and then copy over the appropriate remset bits to the
    // cleared to-page's 'current' remset bitmap.
    //
    // In-place relocation is more complicated. Since, the same page is both
    // a from-page and a to-page, we need to remove the old remset bits, and
    // add remset bits that corresponds to the new locations of the relocated
    // objects.
    //
    // Depending on how long ago (in terms of number of young GC's and the
    // current young GC's phase), the page was allocated, the active
    // remembered set will be in either the 'current' or 'previous' bitmap.
    //
    // If the active bits are in the 'previous' bitmap, we know that the
    // 'current' bitmap was cleared at some earlier point in time, and we can
    // simply set new bits in 'current' bitmap, and later when relocation has
    // read all the old remset bits, we could just clear the 'previous' remset
    // bitmap.
    //
    // If, on the other hand, the active bits are in the 'current' bitmap, then
    // that bitmap will be used to both read the old remset bits, and the
    // destination for the remset bits that we copy when an object is copied
    // to it's new location within the page. We need to *carefully* remove all
    // all old remset bits, without clearing out the newly set bits.
    return ZGeneration::old()->active_remset_is_current();
  }

  void clear_remset_before_in_place_reuse(ZPage* page) {
    if (_forwarding->from_age() != ZPageAge::old) {
      // No remset bits
      return;
    }

    // Clear 'previous' remset bits. For in-place relocated pages, the previous
    // remset bits are always used, even when active_remset_is_current().
    page->clear_remset_previous();
  }

  void finish_in_place_relocation() {
    // We are done with the from_space copy of the page
    _forwarding->in_place_relocation_finish();
  }

  void do_forwarding(ZForwarding* forwarding) {
    _forwarding = forwarding;

    _forwarding->page()->log_msg(" (relocate page)");

    ZVerify::before_relocation(_forwarding);

    // Relocate objects
    _forwarding->object_iterate([&](oop obj) { relocate_object(obj); });

    ZVerify::after_relocation(_forwarding);

    // Verify
    if (ZVerifyForwarding) {
      _forwarding->verify();
    }

    _generation->increase_freed(_forwarding->page()->size());

    // Deal with in-place relocation
    const bool in_place = _forwarding->in_place_relocation();
    if (in_place) {
      finish_in_place_relocation();
    }

    // Old from-space pages need to deal with remset bits
    if (_forwarding->from_age() == ZPageAge::old) {
      _forwarding->relocated_remembered_fields_after_relocate();
    }

    // Release relocated page
    _forwarding->release_page();

    if (in_place) {
      // Wait for all other threads to call release_page
      ZPage* const page = _forwarding->detach_page();

      // Ensure that previous remset bits are cleared
      clear_remset_before_in_place_reuse(page);

      page->log_msg(" (relocate page done in-place)");

      // Different pages when promoting
      ZPage* const target_page = target(_forwarding->to_age());
      _allocator->share_target_page(target_page);

    } else {
      // Wait for all other threads to call release_page
      ZPage* const page = _forwarding->detach_page();

      page->log_msg(" (relocate page done normal)");

      // Free page
      ZHeap::heap()->free_page(page);
    }
  }
};

class ZRelocateStoreBufferInstallBasePointersThreadClosure : public ThreadClosure {
public:
  virtual void do_thread(Thread* thread) {
    JavaThread* const jt = JavaThread::cast(thread);
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
  ZRelocateStoreBufferInstallBasePointersTask(ZGeneration* generation)
    : ZTask("ZRelocateStoreBufferInstallBasePointersTask"),
      _threads_iter(generation->id_optional()) {}

  virtual void work() {
    ZRelocateStoreBufferInstallBasePointersThreadClosure fix_store_buffer_cl;
    _threads_iter.apply(&fix_store_buffer_cl);
  }
};

class ZRelocateTask : public ZRestartableTask {
private:
  ZRelocationSetParallelIterator _iter;
  ZGeneration* const             _generation;
  ZRelocateQueue* const          _queue;
  ZRelocateSmallAllocator        _small_allocator;
  ZRelocateMediumAllocator       _medium_allocator;

public:
  ZRelocateTask(ZRelocationSet* relocation_set, ZRelocateQueue* queue)
    : ZRestartableTask("ZRelocateTask"),
      _iter(relocation_set),
      _generation(relocation_set->generation()),
      _queue(queue),
      _small_allocator(_generation),
      _medium_allocator(_generation) {}

  ~ZRelocateTask() {
    _generation->stat_relocation()->at_relocate_end(_small_allocator.in_place_count(), _medium_allocator.in_place_count());

    // Signal that we're not using the queue anymore. Used mostly for asserts.
    _queue->deactivate();
  }

  virtual void work() {
    ZRelocateWork<ZRelocateSmallAllocator> small(&_small_allocator, _generation);
    ZRelocateWork<ZRelocateMediumAllocator> medium(&_medium_allocator, _generation);

    const auto do_forwarding = [&](ZForwarding* forwarding) {
      ZPage* const page = forwarding->page();
      if (page->is_small()) {
        small.do_forwarding(forwarding);
      } else {
        medium.do_forwarding(forwarding);
      }

      // Absolute last thing done while relocating a page.
      //
      // We don't use the SuspendibleThreadSet when relocating pages.
      // Instead the ZRelocateQueue is used as a pseudo STS joiner/leaver.
      //
      // After the mark_done call a safepointing could be completed and a
      // new GC phase could be entered.
      forwarding->mark_done();
    };

    const auto claim_and_do_forwarding = [&](ZForwarding* forwarding) {
      if (forwarding->claim()) {
        do_forwarding(forwarding);
      }
    };

    const auto do_forwarding_one_from_iter = [&]() {
      ZForwarding* forwarding;

      if (_iter.next(&forwarding)) {
        claim_and_do_forwarding(forwarding);
        return true;
      }

      return false;
    };

    for (;;) {
      // As long as there are requests in the relocate queue, there are threads
      // waiting in a VM state that does not allow them to be blocked. The
      // worker thread needs to finish relocate these pages, and allow the
      // other threads to continue and proceed to a blocking state. After that,
      // the worker threads are allowed to safepoint synchronize.
      for (ZForwarding* forwarding; (forwarding = _queue->synchronize_poll()) != nullptr;) {
        do_forwarding(forwarding);
      }

      if (!do_forwarding_one_from_iter()) {
        // No more work
        break;
      }

      if (_generation->should_worker_resize()) {
        break;
      }
    }

    _queue->leave();
  }

  virtual void resize_workers(uint nworkers) {
    _queue->resize_workers(nworkers);
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
    // No need for remset entries for null pointers
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
  ZRelocateAddRemsetForFlipPromoted(ZArray<ZPage*>* pages)
    : ZRestartableTask("ZRelocateAddRemsetForFlipPromoted"),
      _timer(ZSubPhaseConcurrentRelocateRememberedSetFlipPromotedYoung),
      _iter(pages) {}

  virtual void work() {
    SuspendibleThreadSetJoiner sts_joiner;
    ZStringDedupContext        string_dedup_context;

    for (ZPage* page; _iter.next(&page);) {
      page->object_iterate([&](oop obj) {
        // Remap oops and add remset if needed
        ZIterator::basic_oop_iterate_safe(obj, remap_and_maybe_add_remset);

        // String dedup
        string_dedup_context.request(obj);
      });

      SuspendibleThreadSet::yield();
      if (ZGeneration::young()->should_worker_resize()) {
        return;
      }
    }
  }
};

void ZRelocate::relocate(ZRelocationSet* relocation_set) {
  {
    // Install the store buffer's base pointers before the
    // relocate task destroys the liveness information in
    // the relocated pages.
    ZRelocateStoreBufferInstallBasePointersTask buffer_task(_generation);
    workers()->run(&buffer_task);
  }

  {
    ZRelocateTask relocate_task(relocation_set, &_queue);
    workers()->run(&relocate_task);
  }

  if (relocation_set->generation()->is_young()) {
    ZRelocateAddRemsetForFlipPromoted task(relocation_set->flip_promoted_pages());
    workers()->run(&task);
  }
}

ZPageAge ZRelocate::compute_to_age(ZPageAge from_age) {
  if (from_age == ZPageAge::old) {
    return ZPageAge::old;
  }

  const uint age = untype(from_age);
  if (age >= ZGeneration::young()->tenuring_threshold()) {
    return ZPageAge::old;
  }

  return to_zpageage(age + 1);
}

class ZFlipAgePagesTask : public ZTask {
private:
  ZArrayParallelIterator<ZPage*> _iter;

public:
  ZFlipAgePagesTask(const ZArray<ZPage*>* pages)
    : ZTask("ZPromotePagesTask"),
      _iter(pages) {}

  virtual void work() {
    SuspendibleThreadSetJoiner sts_joiner;
    ZArray<ZPage*> promoted_pages;

    for (ZPage* prev_page; _iter.next(&prev_page);) {
      const ZPageAge from_age = prev_page->age();
      const ZPageAge to_age = ZRelocate::compute_to_age(from_age);
      assert(from_age != ZPageAge::old, "invalid age for a young collection");

      // Figure out if this is proper promotion
      const bool promotion = to_age == ZPageAge::old;

      if (promotion) {
        // Before promoting an object (and before relocate start), we must ensure that all
        // contained zpointers are store good. The marking code ensures that for non-null
        // pointers, but null pointers are ignored. This code ensures that even null pointers
        // are made store good, for the promoted objects.
        prev_page->object_iterate([&](oop obj) {
          ZIterator::basic_oop_iterate_safe(obj, ZBarrier::promote_barrier_on_young_oop_field);
        });
      }

      // Logging
      prev_page->log_msg(promotion ? " (flip promoted)" : " (flip survived)");

      // Setup to-space page
      ZPage* const new_page = promotion
          ? prev_page->clone_for_promotion()
          : prev_page->reset(to_age);

      // Reset page for flip aging
      new_page->reset_livemap();

      if (promotion) {
        ZGeneration::young()->flip_promote(prev_page, new_page);
        // Defer promoted page registration times the lock is taken
        promoted_pages.push(prev_page);
      }

      SuspendibleThreadSet::yield();
    }

    ZGeneration::young()->register_flip_promoted(promoted_pages);
  }
};

void ZRelocate::flip_age_pages(const ZArray<ZPage*>* pages) {
  ZFlipAgePagesTask flip_age_task(pages);
  workers()->run(&flip_age_task);
}

void ZRelocate::synchronize() {
  _queue.synchronize();
}

void ZRelocate::desynchronize() {
  _queue.desynchronize();
}

ZRelocateQueue* ZRelocate::queue() {
  return &_queue;
}

bool ZRelocate::is_queue_active() const {
  return _queue.is_active();
}
