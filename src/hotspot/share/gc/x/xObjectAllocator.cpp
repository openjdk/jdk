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
#include "gc/x/xGlobals.hpp"
#include "gc/x/xHeap.inline.hpp"
#include "gc/x/xHeuristics.hpp"
#include "gc/x/xObjectAllocator.hpp"
#include "gc/x/xPage.inline.hpp"
#include "gc/x/xPageTable.inline.hpp"
#include "gc/x/xStat.hpp"
#include "gc/x/xThread.inline.hpp"
#include "gc/x/xValue.inline.hpp"
#include "logging/log.hpp"
#include "runtime/atomic.hpp"
#include "runtime/safepoint.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"

static const XStatCounter XCounterUndoObjectAllocationSucceeded("Memory", "Undo Object Allocation Succeeded", XStatUnitOpsPerSecond);
static const XStatCounter XCounterUndoObjectAllocationFailed("Memory", "Undo Object Allocation Failed", XStatUnitOpsPerSecond);

XObjectAllocator::XObjectAllocator() :
    _use_per_cpu_shared_small_pages(XHeuristics::use_per_cpu_shared_small_pages()),
    _used(0),
    _undone(0),
    _alloc_for_relocation(0),
    _undo_alloc_for_relocation(0),
    _shared_medium_page(nullptr),
    _shared_small_page(nullptr) {}

XPage** XObjectAllocator::shared_small_page_addr() {
  return _use_per_cpu_shared_small_pages ? _shared_small_page.addr() : _shared_small_page.addr(0);
}

XPage* const* XObjectAllocator::shared_small_page_addr() const {
  return _use_per_cpu_shared_small_pages ? _shared_small_page.addr() : _shared_small_page.addr(0);
}

void XObjectAllocator::register_alloc_for_relocation(const XPageTable* page_table, uintptr_t addr, size_t size) {
  const XPage* const page = page_table->get(addr);
  const size_t aligned_size = align_up(size, page->object_alignment());
  Atomic::add(_alloc_for_relocation.addr(), aligned_size);
}

void XObjectAllocator::register_undo_alloc_for_relocation(const XPage* page, size_t size) {
  const size_t aligned_size = align_up(size, page->object_alignment());
  Atomic::add(_undo_alloc_for_relocation.addr(), aligned_size);
}

XPage* XObjectAllocator::alloc_page(uint8_t type, size_t size, XAllocationFlags flags) {
  XPage* const page = XHeap::heap()->alloc_page(type, size, flags);
  if (page != nullptr) {
    // Increment used bytes
    Atomic::add(_used.addr(), size);
  }

  return page;
}

void XObjectAllocator::undo_alloc_page(XPage* page) {
  // Increment undone bytes
  Atomic::add(_undone.addr(), page->size());

  XHeap::heap()->undo_alloc_page(page);
}

uintptr_t XObjectAllocator::alloc_object_in_shared_page(XPage** shared_page,
                                                        uint8_t page_type,
                                                        size_t page_size,
                                                        size_t size,
                                                        XAllocationFlags flags) {
  uintptr_t addr = 0;
  XPage* page = Atomic::load_acquire(shared_page);

  if (page != nullptr) {
    addr = page->alloc_object_atomic(size);
  }

  if (addr == 0) {
    // Allocate new page
    XPage* const new_page = alloc_page(page_type, page_size, flags);
    if (new_page != nullptr) {
      // Allocate object before installing the new page
      addr = new_page->alloc_object(size);

    retry:
      // Install new page
      XPage* const prev_page = Atomic::cmpxchg(shared_page, page, new_page);
      if (prev_page != page) {
        if (prev_page == nullptr) {
          // Previous page was retired, retry installing the new page
          page = prev_page;
          goto retry;
        }

        // Another page already installed, try allocation there first
        const uintptr_t prev_addr = prev_page->alloc_object_atomic(size);
        if (prev_addr == 0) {
          // Allocation failed, retry installing the new page
          page = prev_page;
          goto retry;
        }

        // Allocation succeeded in already installed page
        addr = prev_addr;

        // Undo new page allocation
        undo_alloc_page(new_page);
      }
    }
  }

  return addr;
}

uintptr_t XObjectAllocator::alloc_large_object(size_t size, XAllocationFlags flags) {
  uintptr_t addr = 0;

  // Allocate new large page
  const size_t page_size = align_up(size, XGranuleSize);
  XPage* const page = alloc_page(XPageTypeLarge, page_size, flags);
  if (page != nullptr) {
    // Allocate the object
    addr = page->alloc_object(size);
  }

  return addr;
}

uintptr_t XObjectAllocator::alloc_medium_object(size_t size, XAllocationFlags flags) {
  return alloc_object_in_shared_page(_shared_medium_page.addr(), XPageTypeMedium, XPageSizeMedium, size, flags);
}

uintptr_t XObjectAllocator::alloc_small_object(size_t size, XAllocationFlags flags) {
  return alloc_object_in_shared_page(shared_small_page_addr(), XPageTypeSmall, XPageSizeSmall, size, flags);
}

uintptr_t XObjectAllocator::alloc_object(size_t size, XAllocationFlags flags) {
  if (size <= XObjectSizeLimitSmall) {
    // Small
    return alloc_small_object(size, flags);
  } else if (size <= XObjectSizeLimitMedium) {
    // Medium
    return alloc_medium_object(size, flags);
  } else {
    // Large
    return alloc_large_object(size, flags);
  }
}

uintptr_t XObjectAllocator::alloc_object(size_t size) {
  XAllocationFlags flags;
  return alloc_object(size, flags);
}

uintptr_t XObjectAllocator::alloc_object_for_relocation(const XPageTable* page_table, size_t size) {
  XAllocationFlags flags;
  flags.set_non_blocking();

  const uintptr_t addr = alloc_object(size, flags);
  if (addr != 0) {
    register_alloc_for_relocation(page_table, addr, size);
  }

  return addr;
}

void XObjectAllocator::undo_alloc_object_for_relocation(XPage* page, uintptr_t addr, size_t size) {
  const uint8_t type = page->type();

  if (type == XPageTypeLarge) {
    register_undo_alloc_for_relocation(page, size);
    undo_alloc_page(page);
    XStatInc(XCounterUndoObjectAllocationSucceeded);
  } else {
    if (page->undo_alloc_object_atomic(addr, size)) {
      register_undo_alloc_for_relocation(page, size);
      XStatInc(XCounterUndoObjectAllocationSucceeded);
    } else {
      XStatInc(XCounterUndoObjectAllocationFailed);
    }
  }
}

size_t XObjectAllocator::used() const {
  size_t total_used = 0;
  size_t total_undone = 0;

  XPerCPUConstIterator<size_t> iter_used(&_used);
  for (const size_t* cpu_used; iter_used.next(&cpu_used);) {
    total_used += *cpu_used;
  }

  XPerCPUConstIterator<size_t> iter_undone(&_undone);
  for (const size_t* cpu_undone; iter_undone.next(&cpu_undone);) {
    total_undone += *cpu_undone;
  }

  return total_used - total_undone;
}

size_t XObjectAllocator::remaining() const {
  assert(XThread::is_java(), "Should be a Java thread");

  const XPage* const page = Atomic::load_acquire(shared_small_page_addr());
  if (page != nullptr) {
    return page->remaining();
  }

  return 0;
}

size_t XObjectAllocator::relocated() const {
  size_t total_alloc = 0;
  size_t total_undo_alloc = 0;

  XPerCPUConstIterator<size_t> iter_alloc(&_alloc_for_relocation);
  for (const size_t* alloc; iter_alloc.next(&alloc);) {
    total_alloc += Atomic::load(alloc);
  }

  XPerCPUConstIterator<size_t> iter_undo_alloc(&_undo_alloc_for_relocation);
  for (const size_t* undo_alloc; iter_undo_alloc.next(&undo_alloc);) {
    total_undo_alloc += Atomic::load(undo_alloc);
  }

  assert(total_alloc >= total_undo_alloc, "Mismatch");

  return total_alloc - total_undo_alloc;
}

void XObjectAllocator::retire_pages() {
  assert(SafepointSynchronize::is_at_safepoint(), "Should be at safepoint");

  // Reset used and undone bytes
  _used.set_all(0);
  _undone.set_all(0);

  // Reset relocated bytes
  _alloc_for_relocation.set_all(0);
  _undo_alloc_for_relocation.set_all(0);

  // Reset allocation pages
  _shared_medium_page.set(nullptr);
  _shared_small_page.set_all(nullptr);
}
