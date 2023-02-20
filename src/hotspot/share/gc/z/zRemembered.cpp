/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zForwarding.inline.hpp"
#include "gc/z/zGeneration.inline.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zIterator.inline.hpp"
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

static const ZStatSubPhase ZSubPhaseConcurrentMarkRootRemsetForwardingYoung("Concurrent Mark Root Remset Forw", ZGenerationId::young);
static const ZStatSubPhase ZSubPhaseConcurrentMarkRootRemsetPageYoung("Concurrent Mark Root Remset Page", ZGenerationId::young);

ZRemembered::ZRemembered(ZPageTable* page_table, ZPageAllocator* page_allocator) :
    _page_table(page_table),
    _page_allocator(page_allocator) {
}

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

  if (forwarding == NULL) {
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

void ZRemembered::scan_page(ZPage* page) const {
  const bool can_trust_live_bits =
      page->is_relocatable() && !ZGeneration::old()->is_phase_mark();

  if (!can_trust_live_bits) {
    // We don't have full liveness info - scan all remset entries
    page->log_msg(" (scan_page_remembered)");
    int count = 0;
    page->oops_do_remembered([&](volatile zpointer* p) {
      scan_field(p);
      count++;
    });
    page->log_msg(" (scan_page_remembered done: %d ignoring: " PTR_FORMAT " )", count, p2i(page->remset_current()));
  } else if (page->is_marked()) {
    // We have full liveness info - Only scan remset entries in live objects
    page->log_msg(" (scan_page_remembered_in_live)");
    page->oops_do_remembered_in_live([&](volatile zpointer* p) {
      scan_field(p);
    });
  } else {
    page->log_msg(" (scan_page_remembered_dead)");
    // All objects are dead - do nothing
  }
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
    static const int                                   NumRecords = 10;
    Tickspan                                           _duration;
    int                                                _count;
    Tickspan                                           _max_durations[NumRecords];
    int                                                _max_count;

    Where() :
        _duration(),
        _count(),
        _max_durations(),
        _max_count() {}

    void report(const Tickspan& duration) {
      _duration += duration;
      _count++;
      // Install into max array
      int i = 0;
      for (; i < NumRecords; i++) {
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

  ZRememberedScanForwardingContext() :
      _containing_array(),
      _where() {}

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
  ZRememberedScanForwardingMeasureRetained(ZRememberedScanForwardingContext* context) :
      _context(context),
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
  ZRememberedScanForwardingMeasureReleased(ZRememberedScanForwardingContext* context) :
      _context(context),
      _start(Ticks::now()) {
  }
  ~ZRememberedScanForwardingMeasureReleased() {
    const Ticks end = Ticks::now();
    const Tickspan duration = end - _start;
    _context->report_released(duration);
  }
};

void ZRemembered::scan_forwarding(ZForwarding* forwarding, void* context_void) const {
  ZRememberedScanForwardingContext* const context = (ZRememberedScanForwardingContext*)context_void;

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
      scan_field(p);
    });

  } else {
    ZRememberedScanForwardingMeasureReleased measure(context);

    // The page has been released. If the page was relocated while this young
    // generation collection was running, the old generation relocation will
    // have published all addresses of fields that had a remembered set entry.
    forwarding->relocated_remembered_fields_apply_to_published([&](volatile zpointer* p) { scan_field(p); });
  }
}

class ZRememberedScanForwardingTask : public ZRestartableTask {
private:
  ZRelocationSetParallelIterator _iterator;
  const ZRemembered&             _remembered;

public:
  ZRememberedScanForwardingTask(const ZRemembered& remembered) :
      ZRestartableTask("ZRememberedScanForwardingTask"),
      _iterator(ZGeneration::old()->relocation_set_parallel_iterator()),
      _remembered(remembered) {}

  virtual void work() {
    ZRememberedScanForwardingContext context;

    for (ZForwarding* forwarding; _iterator.next(&forwarding);) {
      _remembered.scan_forwarding(forwarding, &context);
      ZVerify::after_scan(forwarding);

      if (ZGeneration::young()->should_worker_resize()) {
        break;
      }
    };

    context.print();
  }
};

class ZRememberedScanPageTask : public ZRestartableTask {
private:
  const ZRemembered&        _remembered;
  ZOldPagesParallelIterator _old_pages_parallel_iterator;

public:
  ZRememberedScanPageTask(const ZRemembered& remembered) :
      ZRestartableTask("ZRememberedScanPageTask"),
      _remembered(remembered),
      _old_pages_parallel_iterator(remembered._page_table) {
    _remembered._page_allocator->enable_safe_destroy();
    _remembered._page_allocator->enable_safe_recycle();
  }

  ~ZRememberedScanPageTask() {
    _remembered._page_allocator->disable_safe_recycle();
    _remembered._page_allocator->disable_safe_destroy();
    // We are done scanning the set of old pages.
    // Clear the set for the next young collection.
    _remembered._page_table->clear_found_old_previous_set();
  }

  virtual void work() {
    for (ZPage* page; _old_pages_parallel_iterator.next(&page);) {
      if (_remembered.should_scan_page(page)) {
        // Visit all entries pointing into young gen
        _remembered.scan_page(page);
        // ... and as a side-effect clear the previous entries
        page->clear_remset_previous();
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
      _remembered._page_table->register_found_old(page);

      if (ZGeneration::young()->should_worker_resize()) {
        break;
      }
    }
  }
};

void ZRemembered::scan() const {
  if (ZGeneration::old()->is_phase_relocate()) {
    ZStatTimerYoung timer(ZSubPhaseConcurrentMarkRootRemsetForwardingYoung);
    ZRememberedScanForwardingTask task(*this);
    ZGeneration::young()->workers()->run(&task);
  }

  ZStatTimerYoung timer(ZSubPhaseConcurrentMarkRootRemsetPageYoung);
  ZRememberedScanPageTask task(*this);
  ZGeneration::young()->workers()->run(&task);
}

void ZRemembered::scan_field(volatile zpointer* p) const {
  assert(ZGeneration::young()->is_phase_mark(), "Wrong phase");

  const zaddress addr = ZBarrier::mark_young_good_barrier_on_oop_field(p);

  if (!is_null(addr) && ZHeap::heap()->is_young(addr)) {
    remember(p);
  }
}

void ZRemembered::flip() const {
  ZRememberedSet::flip();
  _page_table->flip_found_old_sets();
}
