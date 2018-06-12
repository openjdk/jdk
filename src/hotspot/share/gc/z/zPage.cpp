/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/collectedHeap.hpp"
#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zForwardingTable.inline.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zLiveMap.inline.hpp"
#include "gc/z/zMark.hpp"
#include "gc/z/zPage.inline.hpp"
#include "gc/z/zPhysicalMemory.inline.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zThread.hpp"
#include "gc/z/zUtils.inline.hpp"
#include "logging/log.hpp"
#include "runtime/orderAccess.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

static const ZStatCounter ZCounterRelocationContention("Contention", "Relocation Contention", ZStatUnitOpsPerSecond);

ZPage::ZPage(uint8_t type, ZVirtualMemory vmem, ZPhysicalMemory pmem) :
    _type(type),
    _pinned(0),
    _numa_id((uint8_t)-1),
    _seqnum(0),
    _virtual(vmem),
    _top(start()),
    _livemap(object_max_count()),
    _refcount(0),
    _forwarding(),
    _physical(pmem) {
  assert(!_physical.is_null(), "Should not be null");
  assert(!_virtual.is_null(), "Should not be null");
  assert((type == ZPageTypeSmall && size() == ZPageSizeSmall) ||
         (type == ZPageTypeMedium && size() == ZPageSizeMedium) ||
         (type == ZPageTypeLarge && is_aligned(size(), ZPageSizeMin)),
         "Page type/size mismatch");
}

ZPage::~ZPage() {
  assert(!is_active(), "Should not be active");
  assert(is_detached(), "Should be detached");
}

void ZPage::reset() {
  assert(!is_active(), "Should not be active");
  assert(!is_pinned(), "Should not be pinned");
  assert(!is_detached(), "Should not be detached");

  _seqnum = ZGlobalSeqNum;
  _top = start();
  _livemap.reset();

  // Make sure we don't make the page active before
  // the reset of the above fields are visible.
  OrderAccess::storestore();

  _refcount = 1;
}

uintptr_t ZPage::relocate_object_inner(uintptr_t from_index, uintptr_t from_offset) {
  ZForwardingTableCursor cursor;

  // Lookup address in forwarding table
  const ZForwardingTableEntry entry = _forwarding.find(from_index, &cursor);
  if (entry.from_index() == from_index) {
    // Already relocated, return new address
    return entry.to_offset();
  }

  // Not found in forwarding table, relocate object
  assert(is_object_marked(from_offset), "Should be marked");

  if (is_pinned()) {
    // In-place forward
    return _forwarding.insert(from_index, from_offset, &cursor);
  }

  // Allocate object
  const uintptr_t from_good = ZAddress::good(from_offset);
  const size_t size = ZUtils::object_size(from_good);
  const uintptr_t to_good = ZHeap::heap()->alloc_object_for_relocation(size);
  if (to_good == 0) {
    // Failed, in-place forward
    return _forwarding.insert(from_index, from_offset, &cursor);
  }

  // Copy object
  ZUtils::object_copy(from_good, to_good, size);

  // Update forwarding table
  const uintptr_t to_offset = ZAddress::offset(to_good);
  const uintptr_t to_offset_final = _forwarding.insert(from_index, to_offset, &cursor);
  if (to_offset_final == to_offset) {
    // Relocation succeeded
    return to_offset;
  }

  // Relocation contention
  ZStatInc(ZCounterRelocationContention);
  log_trace(gc)("Relocation contention, thread: " PTR_FORMAT " (%s), page: " PTR_FORMAT
                ", entry: " SIZE_FORMAT ", oop: " PTR_FORMAT ", size: " SIZE_FORMAT,
                ZThread::id(), ZThread::name(), p2i(this), cursor, from_good, size);

  // Try undo allocation
  ZHeap::heap()->undo_alloc_object_for_relocation(to_good, size);

  return to_offset_final;
}

uintptr_t ZPage::relocate_object(uintptr_t from) {
  assert(ZHeap::heap()->is_relocating(from), "Should be relocating");

  const uintptr_t from_offset = ZAddress::offset(from);
  const uintptr_t from_index = (from_offset - start()) >> object_alignment_shift();
  const uintptr_t to_offset = relocate_object_inner(from_index, from_offset);
  if (from_offset == to_offset) {
    // In-place forwarding, pin page
    set_pinned();
  }

  return ZAddress::good(to_offset);
}

uintptr_t ZPage::forward_object(uintptr_t from) {
  assert(ZHeap::heap()->is_relocating(from), "Should be relocated");

  // Lookup address in forwarding table
  const uintptr_t from_offset = ZAddress::offset(from);
  const uintptr_t from_index = (from_offset - start()) >> object_alignment_shift();
  const ZForwardingTableEntry entry = _forwarding.find(from_index);
  assert(entry.from_index() == from_index, "Should be forwarded");

  return ZAddress::good(entry.to_offset());
}

void ZPage::print_on(outputStream* out) const {
  out->print_cr(" %-6s  " PTR_FORMAT " " PTR_FORMAT " " PTR_FORMAT " %s%s%s%s%s%s",
                type_to_string(), start(), top(), end(),
                is_allocating()  ? " Allocating"  : "",
                is_relocatable() ? " Relocatable" : "",
                is_forwarding()  ? " Forwarding"  : "",
                is_pinned()      ? " Pinned"      : "",
                is_detached()    ? " Detached"    : "",
                !is_active()     ? " Inactive"    : "");
}

void ZPage::print() const {
  print_on(tty);
}
