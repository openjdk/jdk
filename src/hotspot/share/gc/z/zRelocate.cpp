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
#include "gc/z/zForwarding.inline.hpp"
#include "gc/z/zCycle.hpp"
#include "gc/z/zGeneration.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zIterator.inline.hpp"
#include "gc/z/zPage.inline.hpp"
#include "gc/z/zRelocate.hpp"
#include "gc/z/zRelocationSet.inline.hpp"
#include "gc/z/zRootsIterator.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zStackWatermark.hpp"
#include "gc/z/zTask.hpp"
#include "gc/z/zThread.inline.hpp"
#include "gc/z/zUncoloredRoot.inline.hpp"
#include "gc/z/zWorkers.inline.hpp"
#include "prims/jvmtiTagMap.hpp"
#include "runtime/atomic.hpp"
#include "utilities/debug.hpp"

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

ZRelocate::ZRelocate(ZCycle* cycle) :
    _cycle(cycle),
    _queue() {}

ZWorkers* ZRelocate::workers() const {
  return _cycle->workers();
}

void ZRelocate::start() {
  _queue.join(workers()->nconcurrent());
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

static zaddress relocate_object_inner(ZForwarding* forwarding, zaddress from_addr, ZForwardingCursor* cursor) {
  assert(ZHeap::heap()->is_object_live(from_addr), "Should be live");

  // Allocate object
  const size_t size = ZUtils::object_size(from_addr);


  const zaddress to_addr = ZHeap::heap()->old_generation()->alloc_object_for_relocation(size);
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
    ZHeap::heap()->old_generation()->undo_alloc_object_for_relocation(to_addr, size);
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

static ZPage* alloc_page(const ZForwarding* forwarding, ZCycle* cycle) {
  if (ZStressRelocateInPlace) {
    // Simulate failure to allocate a new page. This will
    // cause the page being relocated to be relocated in-place.
    return NULL;
  }

  ZAllocationFlags flags;
  flags.set_non_blocking();
  flags.set_worker_relocation();
  return ZHeap::heap()->alloc_page(forwarding->type(), forwarding->size(), flags, cycle);
}

static void free_page(ZPage* page, ZCycle* cycle) {
  ZHeap::heap()->free_page(page, cycle);
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
  ZCycle*         _cycle;

public:
  ZRelocateSmallAllocator(ZCycle* cycle) :
      _in_place_count(0),
      _cycle(cycle) {}

  ZPage* alloc_target_page(ZForwarding* forwarding, ZPage* target) {
    ZPage* const page = alloc_page(forwarding, _cycle);
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
      free_page(page, _cycle);
    }
  }

  void free_relocated_page(ZPage* page) {
    free_page(page, _cycle);
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
  ZCycle*             _cycle;

public:
  ZRelocateMediumAllocator(ZCycle* cycle) :
      _lock(),
      _shared(NULL),
      _in_place(false),
      _in_place_count(0),
      _cycle(cycle) {}

  ~ZRelocateMediumAllocator() {
    if (should_free_target_page(_shared)) {
      free_page(_shared, _cycle);
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
      _shared = alloc_page(forwarding, _cycle);
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
    free_page(page, _cycle);
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
class ZRelocateClosure : public ObjectClosure {
private:
  Allocator* const _allocator;
  ZForwarding*     _forwarding;
  ZPage*           _target;

  bool relocate_object(zaddress from_addr) const {
    ZForwardingCursor cursor;

    // Lookup forwarding
    if (!is_null(forwarding_find(_forwarding, from_addr, &cursor))) {
      // Already relocated
      return true;
    }

    // Allocate object
    const size_t size = ZUtils::object_size(from_addr);
    const zaddress to_addr = _allocator->alloc_object(_target, size);
    if (is_null(to_addr)) {
      // Allocation failed
      return false;
    }

    // Copy object. Use conjoint copying if we are relocating
    // in-place and the new object overlapps with the old object.
    if (_forwarding->in_place() && to_addr + size > from_addr) {
      ZUtils::object_copy_conjoint(from_addr, to_addr, size);
    } else {
      ZUtils::object_copy_disjoint(from_addr, to_addr, size);
    }

    // Insert forwarding
    if (forwarding_insert(_forwarding, from_addr, to_addr, &cursor) != to_addr) {
      // Already relocated, undo allocation
      _allocator->undo_alloc_object(_target, to_addr, size);
    }

    return true;
  }

  virtual void do_object(oop obj) {
    const zaddress addr = to_zaddress(obj);
    assert(ZHeap::heap()->is_object_live(addr), "Should be live");

    while (!relocate_object(addr)) {
      // Allocate a new target page, or if that fails, use the page being
      // relocated as the new target, which will cause it to be relocated
      // in-place.
      _target = _allocator->alloc_target_page(_forwarding, _target);
      if (_target != NULL) {
        continue;
      }

      // Claim the page being relocated to block other threads from accessing
      // it, or its forwarding table, until it has been released (relocation
      // completed).
      _target = _forwarding->claim_page_for_in_place_relocation();
    }
  }

public:
  ZRelocateClosure(Allocator* allocator) :
      _allocator(allocator),
      _forwarding(NULL),
      _target(NULL) {}

  ~ZRelocateClosure() {
    _allocator->free_target_page(_target);
  }

  void do_forwarding(ZForwarding* forwarding) {
    _forwarding = forwarding;

    // Check if we should abort
    if (ZAbort::should_abort()) {
      _forwarding->abort_page();
      return;
    }

    // TODO: Why is this needed?
    // Clear current bits
    // _forwarding->page()->clear_current_remembered();

    // Relocate objects
    _forwarding->object_iterate(this);

    // Deal with in-place relocation
    if (_forwarding->in_place()) {
      if (_forwarding->generation_id() == ZGenerationId::young) {
        _target = ZHeap::heap()->minor_cycle()->promote(_target);
      }

      // We are done with the from_space copy of the page
      _forwarding->clear_in_place_relocation();
    }

    // Rebuild remset
    _forwarding->oops_do_in_forwarded(ZRelocate::add_remset);

    // Verify
    if (ZVerifyForwarding) {
      _forwarding->verify();
    }

    // Release relocated page
    _forwarding->release_page();

    if (_forwarding->in_place()) {
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
    JavaThread* jt = thread->as_Java_thread();
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
  ZCycle* const                  _cycle;
  ZRelocateQueue* const          _queue;
  ZRelocateSmallAllocator        _small_allocator;
  ZRelocateMediumAllocator       _medium_allocator;

  static bool is_small(ZForwarding* forwarding) {
    return forwarding->type() == ZPageTypeSmall;
  }

public:
  ZRelocateTask(ZRelocationSet* relocation_set, ZRelocateQueue* queue) :
      ZTask("ZRelocateTask"),
      _iter(relocation_set),
      _cycle(relocation_set->cycle()),
      _queue(queue),
      _small_allocator(relocation_set->cycle()),
      _medium_allocator(relocation_set->cycle()) {}

  ~ZRelocateTask() {
    _cycle->stat_relocation()->set_at_relocate_end(_small_allocator.in_place_count(),
                                                   _medium_allocator.in_place_count());
  }

  virtual void work() {
    ZRelocateClosure<ZRelocateSmallAllocator> small(&_small_allocator);
    ZRelocateClosure<ZRelocateMediumAllocator> medium(&_medium_allocator);

    bool synchronized = false;

    const auto do_forwarding = [&](ZForwarding* forwarding) {
      if (forwarding->claim()) {
        if (is_small(forwarding)) {
          small.do_forwarding(forwarding);
        } else {
          medium.do_forwarding(forwarding);
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

void ZRelocate::relocate(ZRelocationSet* relocation_set) {
  ZFixStoreBufferTask buffer_task;
  ZRelocateTask relocate_task(relocation_set, &_queue);
  workers()->run_concurrent(&buffer_task);
  workers()->run_concurrent(&relocate_task);
  _queue.clear();
}

// FIXME: Temporary here because of accessBackend.inline.hpp circular dependencies
template <typename Function>
inline void ZPage::object_iterate_unconditional(Function function) {
  auto do_bit = [&](BitMap::idx_t index) -> bool {
    const oop obj = object_from_bit_index(index);

    // Apply function
    function(obj);

    return true;
  };

  _livemap.iterate_unconditional(do_bit);
}

void ZRelocate::promote_pages(const ZArray<ZPage*>* pages) {
  SuspendibleThreadSetJoiner sts_joiner;
  // TODO: Make multi-threaded
  for (int i = 0; i < pages->length(); i++) {
    ZPage* page = pages->at(i);
    ZHeap::heap()->minor_cycle()->promote(page);

    page->log_msg(" (in-place promoted)");
    page->object_iterate_unconditional([&](oop obj) {
      z_basic_oop_iterate(obj, ZRelocate::add_remset);
    });

    SuspendibleThreadSet::yield();
  }
}

void ZRelocate::synchronize() {
  _queue.synchronize();
}

void ZRelocate::desynchronize() {
  _queue.desynchronize();
}
