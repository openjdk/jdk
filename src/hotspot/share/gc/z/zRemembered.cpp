/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zForwarding.inline.hpp"
#include "gc/z/zGeneration.inline.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zIterator.inline.hpp"
#include "gc/z/zMark.hpp"
#include "gc/z/zPage.inline.hpp"
#include "gc/z/zPageTable.hpp"
#include "gc/z/zRemembered.inline.hpp"
#include "gc/z/zRememberedSet.hpp"
#include "gc/z/zTask.hpp"
#include "gc/z/zVerify.hpp"
#include "memory/iterator.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/bitMap.inline.hpp"
#include "utilities/debug.hpp"

ZRemembered::ZRemembered(ZPageTable* page_table,
                         const ZForwardingTable* old_forwarding_table,
                         ZPageAllocator* page_allocator)
  : _page_table(page_table),
    _old_forwarding_table(old_forwarding_table),
    _page_allocator(page_allocator),
    _found_old() {}

template <typename Function>
void ZRemembered::oops_do_forwarded_via_containing(GrowableArrayView<ZRememberedSetContaining>* array, Function function) const {
  // The array contains duplicated from_addr values. Cache expensive operations.
  zaddress_unsafe from_addr = zaddress_unsafe::null;
  zaddress to_addr = zaddress::null;
  size_t object_size = 0;

  for (const ZRememberedSetContaining containing: *array) {
    if (from_addr != containing._addr) {
      from_addr = containing._addr;

      // Relocate object to new location
      to_addr = ZGeneration::old()->relocate_or_remap_object(from_addr);

      // Figure out size
      object_size = ZUtils::object_size(to_addr);
    }

    // Calculate how far into the from-object the remset entry is
    const uintptr_t field_offset = containing._field_addr - from_addr;

    // The 'containing' could contain mismatched (addr, addr_field).
    // Need to check if the field was within the reported object.
    if (field_offset < object_size) {
      // Calculate the corresponding address in the to-object
      const zaddress to_addr_field = to_addr + field_offset;

      function((volatile zpointer*)untype(to_addr_field));
    }
  }
}

bool ZRemembered::should_scan_page(ZPage* page) const {
  if (!ZGeneration::old()->is_phase_relocate()) {
    // If the old generation collection is not in the relocation phase, then it
    // will not need any synchronization on its forwardings.
    return true;
  }

  ZForwarding* const forwarding = ZGeneration::old()->forwarding(ZOffset::address_unsafe(page->start()));

  if (forwarding == nullptr) {
    // This page was provably not part of the old relocation set
    return true;
  }

  if (!forwarding->relocated_remembered_fields_is_concurrently_scanned()) {
    // Safe to scan
    return true;
  }

  // If we get here, we know that the old collection is concurrently relocating
  // objects. We need to be extremely careful not to scan a page that is
  // concurrently being in-place relocated because it's objects and previous
  // bits could be concurrently be moving around.
  //
  // Before calling this function ZRemembered::scan_forwarding ensures
  // that all forwardings that have not already been fully relocated,
  // will have had their "previous" remembered set bits scanned.
  //
  // The current page we're currently scanning could either be the same page
  // that was found during scan_forwarding, or it could have been replaced
  // by a new "allocating" page. There are two situations we have to consider:
  //
  // 1) If it is a proper new allocating page, then all objects where copied
  // after scan_forwarding ran, and we are guaranteed that no "previous"
  // remembered set bits are set. So, there's no need to scan this page.
  //
  // 2) If this is an in-place relocated page, then the entire page could
  // be concurrently relocated. Meaning that both objects and previous
  // remembered set bits could be moving around. However, if the in-place
  // relocation is ongoing, we've already scanned all relevant "previous"
  // bits when calling scan_forwarding. So, this page *must* not be scanned.
  //
  // Don't scan the page.
  return false;
}

bool ZRemembered::scan_page_and_clear_remset(ZPage* page) const {
  const bool can_trust_live_bits =
      page->is_relocatable() && !ZGeneration::old()->is_phase_mark();

  bool result = false;

  if (!can_trust_live_bits) {
    // We don't have full liveness info - scan all remset entries
    page->log_msg(" (scan_page_remembered)");
    int count = 0;
    page->oops_do_remembered([&](volatile zpointer* p) {
      result |= scan_field(p);
      count++;
    });
    page->log_msg(" (scan_page_remembered done: %d ignoring: " PTR_FORMAT " )", count, p2i(page->remset_current()));
  } else if (page->is_marked()) {
    // We have full liveness info - Only scan remset entries in live objects
    page->log_msg(" (scan_page_remembered_in_live)");
    page->oops_do_remembered_in_live([&](volatile zpointer* p) {
      result |= scan_field(p);
    });
  } else {
    page->log_msg(" (scan_page_remembered_dead)");
    // All objects are dead - do nothing
  }

  if (ZVerifyRemembered) {
    // Make sure self healing of pointers is ordered before clearing of
    // the previous bits so that ZVerify::after_scan can detect missing
    // remset entries accurately.
    OrderAccess::storestore();
  }

  // If we have consumed the remset entries above we also clear them.
  // The exception is if the page is completely empty/garbage, where we don't
  // want to race with an old collection modifying the remset as well.
  if (!can_trust_live_bits || page->is_marked()) {
    page->clear_remset_previous();
  }

  return result;
}

static void fill_containing(GrowableArrayCHeap<ZRememberedSetContaining, mtGC>* array, ZPage* page) {
  page->log_msg(" (fill_remembered_containing)");

  ZRememberedSetContainingIterator iter(page);

  for (ZRememberedSetContaining containing; iter.next(&containing);) {
    array->push(containing);
  }
}

struct ZRememberedScanForwardingContext {
  GrowableArrayCHeap<ZRememberedSetContaining, mtGC> _containing_array;

  struct Where {
    static const int NumRecords = 10;

    Tickspan _duration;
    int      _count;
    Tickspan _max_durations[NumRecords];
    int      _max_count;

    Where()
      : _duration(),
        _count(),
        _max_durations(),
        _max_count() {}

    void report(const Tickspan& duration) {
      _duration += duration;
      _count++;

      // Install into max array
      for (int i = 0; i < NumRecords; i++) {
        if (duration > _max_durations[i]) {
          // Slid to the side
          for (int j = _max_count - 1; i < j; j--) {
            _max_durations[j] = _max_durations[j - 1];
          }

          // Install
          _max_durations[i] = duration;
          if (_max_count < NumRecords) {
            _max_count++;
          }
          break;
        }
      }
    }

    void print(const char* name) {
      log_debug(gc, remset)("Remset forwarding %s: %.3fms count: %d %s",
          name, TimeHelper::counter_to_millis(_duration.value()), _count, Thread::current()->name());
      for (int i = 0; i < _max_count; i++) {
        log_debug(gc, remset)("  %.3fms", TimeHelper::counter_to_millis(_max_durations[i].value()));
      }
    }
  };

  Where _where[2];

  ZRememberedScanForwardingContext()
    : _containing_array(),
      _where() {}

  ~ZRememberedScanForwardingContext() {
    print();
  }

  void report_retained(const Tickspan& duration) {
    _where[0].report(duration);
  }

  void report_released(const Tickspan& duration) {
    _where[1].report(duration);
  }

  void print() {
    _where[0].print("retained");
    _where[1].print("released");
  }
};

struct ZRememberedScanForwardingMeasureRetained {
  ZRememberedScanForwardingContext* _context;
  Ticks                             _start;

  ZRememberedScanForwardingMeasureRetained(ZRememberedScanForwardingContext* context)
    : _context(context),
      _start(Ticks::now()) {
  }

  ~ZRememberedScanForwardingMeasureRetained() {
    const Ticks end = Ticks::now();
    const Tickspan duration = end - _start;
    _context->report_retained(duration);
  }
};

struct ZRememberedScanForwardingMeasureReleased {
  ZRememberedScanForwardingContext* _context;
  Ticks                             _start;

  ZRememberedScanForwardingMeasureReleased(ZRememberedScanForwardingContext* context)
    : _context(context),
      _start(Ticks::now()) {
  }

  ~ZRememberedScanForwardingMeasureReleased() {
    const Ticks end = Ticks::now();
    const Tickspan duration = end - _start;
    _context->report_released(duration);
  }
};

bool ZRemembered::scan_forwarding(ZForwarding* forwarding, void* context_void) const {
  ZRememberedScanForwardingContext* const context = (ZRememberedScanForwardingContext*)context_void;
  bool result = false;

  if (forwarding->retain_page(ZGeneration::old()->relocate_queue())) {
    ZRememberedScanForwardingMeasureRetained measure(context);
    forwarding->page()->log_msg(" (scan_forwarding)");

    // We don't want to wait for the old relocation to finish and publish all
    // relocated remembered fields. Reject its fields and collect enough data
    // up-front.
    forwarding->relocated_remembered_fields_notify_concurrent_scan_of();

    // Collect all remset info while the page is retained
    GrowableArrayCHeap<ZRememberedSetContaining, mtGC>* array = &context->_containing_array;
    array->clear();
    fill_containing(array, forwarding->page());
    forwarding->release_page();

    // Relocate (and mark) while page is released, to prevent
    // retain deadlock when relocation threads in-place relocate.
    oops_do_forwarded_via_containing(array, [&](volatile zpointer* p) {
      result |= scan_field(p);
    });

  } else {
    ZRememberedScanForwardingMeasureReleased measure(context);

    // The page has been released. If the page was relocated while this young
    // generation collection was running, the old generation relocation will
    // have published all addresses of fields that had a remembered set entry.
    forwarding->relocated_remembered_fields_apply_to_published([&](volatile zpointer* p) {
      result |= scan_field(p);
    });
  }

  return result;
}

// When scanning the remembered set during the young generation marking, we
// want to visit all old pages. And we want that to be done in parallel and
// fast.
//
// Walking over the entire page table and letting the workers claim indices
// have been shown to have scalability issues.
//
// So, we have the "found old" optimization, which allows us to perform much
// fewer claims (order of old pages, instead of order of slots in the page
// table), and it allows us to read fewer pages.
//
// The set of "found old pages" isn't precise, and can contain stale entries
// referring to slots of freed pages, or even slots where young pages have
// been installed. However, it will not lack any of the old pages.
//
// The data is maintained very similar to when and how we maintain the
// remembered set bits: We keep two separates sets, one for read-only access
// by the young marking, and a currently active set where we register new
// pages. When pages get relocated, or die, the page table slot for that page
// must be cleared. This clearing is done just like we do with the remset
// scanning: The old entries are not copied to the current active set, only
// slots that were found to actually contain old pages are registered in the
// active set.

ZRemembered::FoundOld::FoundOld()
    // Array initialization requires copy constructors, which CHeapBitMap
    // doesn't provide. Instantiate two instances, and populate an array
    // with pointers to the two instances.
  : _allocated_bitmap_0{ZAddressOffsetMax >> ZGranuleSizeShift, mtGC, true /* clear */},
    _allocated_bitmap_1{ZAddressOffsetMax >> ZGranuleSizeShift, mtGC, true /* clear */},
    _bitmaps{&_allocated_bitmap_0, &_allocated_bitmap_1},
    _current{0} {}

BitMap* ZRemembered::FoundOld::current_bitmap() {
  return _bitmaps[_current];
}

BitMap* ZRemembered::FoundOld::previous_bitmap() {
  return _bitmaps[_current ^ 1];
}

void ZRemembered::FoundOld::flip() {
  _current ^= 1;
}

void ZRemembered::FoundOld::clear_previous() {
  previous_bitmap()->clear_large();
}

void ZRemembered::FoundOld::register_page(ZPage* page) {
  assert(page->is_old(), "Only register old pages");
  current_bitmap()->par_set_bit(untype(page->start()) >> ZGranuleSizeShift, memory_order_relaxed);
}

void ZRemembered::flip_found_old_sets() {
  _found_old.flip();
}

void ZRemembered::clear_found_old_previous_set() {
  _found_old.clear_previous();
}

void ZRemembered::register_found_old(ZPage* page) {
  assert(page->is_old(), "Should only register old pages");
  _found_old.register_page(page);
}

struct ZRemsetTableEntry {
  ZPage* _page;
  ZForwarding* _forwarding;
};

ZRemsetTableIterator::ZRemsetTableIterator(ZRemembered* remembered, bool previous)
  : _remembered(remembered),
    _bm(previous
        ? _remembered->_found_old.previous_bitmap()
        : _remembered->_found_old.current_bitmap()),
    _page_table(remembered->_page_table),
    _old_forwarding_table(remembered->_old_forwarding_table),
    _claimed(0) {}

  // This iterator uses the "found old" optimization.
bool ZRemsetTableIterator::next(ZRemsetTableEntry* entry_addr) {
  BitMap::idx_t prev = Atomic::load(&_claimed);

  for (;;) {
    if (prev == _bm->size()) {
      return false;
    }

    const BitMap::idx_t page_index = _bm->find_first_set_bit(_claimed);
    if (page_index == _bm->size()) {
      Atomic::cmpxchg(&_claimed, prev, page_index, memory_order_relaxed);
      return false;
    }

    const BitMap::idx_t res = Atomic::cmpxchg(&_claimed, prev, page_index + 1, memory_order_relaxed);
    if (res != prev) {
      // Someone else claimed
      prev = res;
      continue;
    }

    // Found bit - look around for page or forwarding to scan

    ZForwarding* forwarding = nullptr;
    if (ZGeneration::old()->is_phase_relocate()) {
      forwarding = _old_forwarding_table->at(page_index);
    }

    ZPage* page = _page_table->at(page_index);
    if (page != nullptr && !page->is_old()) {
      page = nullptr;
    }

    if (page == nullptr && forwarding == nullptr) {
      // Nothing to scan
      continue;
    }

    // Found old page or old forwarding
    entry_addr->_forwarding = forwarding;
    entry_addr->_page = page;

    return true;
  }
}

void ZRemembered::remap_current(ZRemsetTableIterator* iter) {
  for (ZRemsetTableEntry entry; iter->next(&entry);) {
    assert(entry._forwarding == nullptr, "Shouldn't be looking for forwardings");
    assert(entry._page != nullptr, "Must have found a page");
    assert(entry._page->is_old(), "Should only have found old pages");

    entry._page->oops_do_current_remembered(ZBarrier::load_barrier_on_oop_field);
  }
}

// This task scans the remembered set and follows pointers when possible.
// Interleaving remembered set scanning with marking makes the marking times
// lower and more predictable.
class ZRememberedScanMarkFollowTask : public ZRestartableTask {
private:
  ZRemembered* const   _remembered;
  ZMark* const         _mark;
  ZRemsetTableIterator _remset_table_iterator;

public:
  ZRememberedScanMarkFollowTask(ZRemembered* remembered, ZMark* mark)
    : ZRestartableTask("ZRememberedScanMarkFollowTask"),
      _remembered(remembered),
      _mark(mark),
      _remset_table_iterator(remembered, true /* previous */) {
    _mark->prepare_work();
    _remembered->_page_allocator->enable_safe_destroy();
  }

  ~ZRememberedScanMarkFollowTask() {
    _remembered->_page_allocator->disable_safe_destroy();
    _mark->finish_work();
    // We are done scanning the set of old pages.
    // Clear the set for the next young collection.
    _remembered->clear_found_old_previous_set();
  }

  virtual void work_inner() {
    ZRememberedScanForwardingContext context;

    // Follow initial roots
    if (!_mark->follow_work_partial()) {
      // Bail
      return;
    }

    for (ZRemsetTableEntry entry; _remset_table_iterator.next(&entry);) {
      bool left_marking = false;
      ZForwarding* forwarding = entry._forwarding;
      ZPage* page = entry._page;

      // Scan forwarding
      if (forwarding != nullptr) {
        bool found_roots = _remembered->scan_forwarding(forwarding, &context);
        ZVerify::after_scan(forwarding);
        if (found_roots) {
          // Follow remembered set when possible
          left_marking = !_mark->follow_work_partial();
        }
      }

      // Scan page
      if (page != nullptr) {
        if (_remembered->should_scan_page(page)) {
          // Visit all entries pointing into young gen
          bool found_roots = _remembered->scan_page_and_clear_remset(page);

          if (found_roots && !left_marking) {
            // Follow remembered set when possible
            left_marking = !_mark->follow_work_partial();
          }
        }

        // The remset scanning maintains the "maybe old" pages optimization.
        //
        // We maintain two sets of old pages: The first is the currently active
        // set, where old pages are registered into. The second is the old
        // read-only copy. The two sets flip during young mark start. This
        // analogous to how we set and clean remembered set bits.
        //
        // The iterator reads from the read-only copy, and then here, we install
        // entries in the current active set.
        _remembered->register_found_old(page);
      }

      SuspendibleThreadSet::yield();
      if (left_marking) {
        // Bail
        return;
      }
    }

    _mark->follow_work_complete();
  }

  virtual void work() {
    SuspendibleThreadSetJoiner sts_joiner;
    work_inner();
    // We might have found pointers into the other generation, and then we want to
    // publish such marking stacks to prevent that generation from getting a mark continue.
    // We also flush in case of a resize where a new worker thread continues the marking
    // work, causing a mark continue for the collected generation.
    ZHeap::heap()->mark_flush(Thread::current());
  }

  virtual void resize_workers(uint nworkers) {
    _mark->resize_workers(nworkers);
  }
};

void ZRemembered::scan_and_follow(ZMark* mark) {
  {
    // Follow the object graph and lazily scan the remembered set
    ZRememberedScanMarkFollowTask task(this, mark);
    ZGeneration::young()->workers()->run(&task);

    // Try to terminate after following the graph
    if (ZAbort::should_abort() || !mark->try_terminate_flush()) {
      return;
    }
  }

  // If flushing failed, we have to restart marking again, but this time we don't need to
  // scan the remembered set.
  mark->mark_follow();
}

bool ZRemembered::scan_field(volatile zpointer* p) const {
  assert(ZGeneration::young()->is_phase_mark(), "Wrong phase");

  const zaddress addr = ZBarrier::remset_barrier_on_oop_field(p);

  if (!is_null(addr) && ZHeap::heap()->is_young(addr)) {
    remember(p);
    return true;
  }

  return false;
}

void ZRemembered::flip() {
  ZRememberedSet::flip();
  flip_found_old_sets();
}
