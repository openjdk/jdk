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
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zIterator.inline.hpp"
#include "gc/z/zPageTable.hpp"
#include "gc/z/zRemembered.inline.hpp"
#include "gc/z/zRememberedSet.hpp"
#include "gc/z/zTask.hpp"
#include "memory/iterator.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/debug.hpp"

static const ZStatSubPhase ZSubPhaseConcurrentMinorMarkRootRemsetForwarding("Concurrent Minor Mark Root Remset Forw");
static const ZStatSubPhase ZSubPhaseConcurrentMinorMarkRootRemsetPage("Concurrent Minor Mark Root Remset Page");

ZRemembered::ZRemembered(ZPageTable* page_table, ZPageAllocator* page_allocator) :
    _page_table(page_table),
    _page_allocator(page_allocator) {
}

void ZRemembered::remember_fields(zaddress addr) const {
  assert(ZHeap::heap()->is_old(addr), "Should already have been checked");
  ZIterator::basic_oop_iterate_safe(to_oop(addr), [&](volatile zpointer* p) {
    remember(p);
  });
}

template <typename Function>
void ZRemembered::oops_do_forwarded_via_containing(GrowableArrayView<ZRememberedSetContaining>* array, Function function) const {
  // The array contains duplicated from_addr values. Cache expensive operations.
  zaddress_unsafe from_addr = zaddress_unsafe::null;
  zaddress to_addr = zaddress::null;
  size_t object_size = 0;

  for (ZRememberedSetContaining containing: *array) {
    if (from_addr != containing._addr) {
      from_addr = containing._addr;

      // Relocate object to new location
      to_addr = ZHeap::heap()->major_collector()->relocate_or_remap_object(from_addr);

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

template <typename Function>
void ZRemembered::oops_do_forwarded(ZForwarding* forwarding, Function function) const {
  // All objects have been forwarded, and the page could have been detached.
  // Visit all objects via the forwarding table.
  forwarding->oops_do_in_forwarded_via_table([&](volatile zpointer* p) {
    function(p);
  });
}

bool ZRemembered::should_scan_page(ZPage* page) const {
  if (!ZHeap::heap()->major_collector()->is_phase_relocate()) {
    // If the major cycle is not in the relocation phase, then it will not need any
    // synchronization on its forwardings.
    return true;
  }

  if (page->is_allocating()) {
    // If the page is old and was allocated after major mark start, then it can't be part
    // of the major relocation set.
    return true;
  }

  // If we get here, we know that the major collection is concurrently relocating objects,
  // and the page was allocated at a time that makes it possible for it to be in the
  // relocation set.

  if (ZHeap::heap()->major_collector()->forwarding(ZOffset::address_unsafe(page->start())) == NULL) {
    // This page was provably not part of the major relocation set.
    return true;
  }

  return false;
}

void ZRemembered::scan_page(ZPage* page) const {
  const bool can_trust_live_bits =
      page->is_relocatable() && !ZHeap::heap()->major_collector()->is_phase_mark();

  if (!can_trust_live_bits) {
    // We don't have full liveness info - scan all remset entries
    page->log_msg(" (scan_page_remembered)");
    page->oops_do_remembered([&](volatile zpointer* p) {
      scan_field(p);
    });
  } else if (page->is_marked()) {
    // We have full liveness info - Only scan remset entries in live objects
    page->log_msg(" (scan_page_remembered_in_live)");
    page->oops_do_remembered_in_live([&](volatile zpointer* p) {
      scan_field(p);
    });
  } else {
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

void ZRemembered::scan_forwarding(ZForwarding* forwarding, void* context) const {
  if (forwarding->get_and_set_remset_scanned()) {
    // Scanned last minor cycle; implies that the to-space objects
    // are going to be found in the page table scan
    return;
  }

  if (forwarding->retain_page()) {
    // Collect all remset info while the page is retained
    GrowableArrayCHeap<ZRememberedSetContaining, mtGC>* array = (GrowableArrayCHeap<ZRememberedSetContaining, mtGC>*)context;
    array->clear();
    fill_containing(array, forwarding->page());
    forwarding->release_page();

    // Relocate (and mark) while page is released, to prevent
    // retain deadlock when relocation threads in-place relocate.
    oops_do_forwarded_via_containing(array, [&](volatile zpointer* p) {
      scan_field(p);
    });

  } else {
    oops_do_forwarded(forwarding, [&](volatile zpointer* p) {
      scan_field(p);
    });
  }
}

class ZRememberedScanForwardingTask : public ZTask {
private:
  ZForwardingTableParallelIterator _iterator;
  const ZRemembered&               _remembered;

public:
  ZRememberedScanForwardingTask(const ZRemembered& remembered) :
      ZTask("ZRememberedScanForwardingTask"),
      _iterator(ZHeap::heap()->major_collector()->forwarding_table()),
      _remembered(remembered) {}

  virtual void work() {
    GrowableArrayCHeap<ZRememberedSetContaining, mtGC> containing_array;

    _iterator.do_forwardings([&](ZForwarding* forwarding) {
      _remembered.scan_forwarding(forwarding, &containing_array);
    });
  }
};

class ZRememberedScanPageTask : public ZTask {
private:
  ZGenerationPagesParallelIterator _iterator;
  const ZRemembered&               _remembered;

public:
  ZRememberedScanPageTask(const ZRemembered& remembered) :
      ZTask("ZRememberedScanPageTask"),
      _iterator(remembered._page_table, ZGenerationId::old, remembered._page_allocator),
      _remembered(remembered) {}

  virtual void work() {
    _iterator.do_pages([&](ZPage* page) {
      if (_remembered.should_scan_page(page)) {
        // Visit all entries pointing into young gen
        _remembered.scan_page(page);
        // ... and as a side-effect clear the previous entries
        page->clear_previous_remembered();
      }
    });
  }
};

void ZRemembered::scan() const {
  if (ZHeap::heap()->major_collector()->is_phase_relocate()) {
    ZStatTimerMinor timer(ZSubPhaseConcurrentMinorMarkRootRemsetForwarding);
    ZRememberedScanForwardingTask task(*this);
    ZHeap::heap()->minor_collector()->workers()->run(&task);
  }

  ZStatTimerMinor timer(ZSubPhaseConcurrentMinorMarkRootRemsetPage);
  ZRememberedScanPageTask task(*this);
  ZHeap::heap()->minor_collector()->workers()->run(&task);
}

void ZRemembered::scan_field(volatile zpointer* p) const {
  assert(ZHeap::heap()->minor_collector()->is_phase_mark(), "Wrong phase");

  zaddress addr = ZBarrier::mark_minor_good_barrier_on_oop_field(p);

  if (!is_null(addr) && ZHeap::heap()->is_young(addr)) {
    remember(p);
  }
}

void ZRemembered::flip() const {
  ZRememberedSet::flip();
}
