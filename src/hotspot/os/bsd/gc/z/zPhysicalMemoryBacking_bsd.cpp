/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zGlobals.hpp"
#include "gc/z/zLargePages.inline.hpp"
#include "gc/z/zPhysicalMemory.inline.hpp"
#include "gc/z/zPhysicalMemoryBacking_bsd.hpp"
#include "runtime/globals.hpp"
#include "runtime/init.hpp"
#include "runtime/os.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"

bool ZPhysicalMemoryBacking::is_initialized() const {
  return _file.is_initialized();
}

void ZPhysicalMemoryBacking::warn_commit_limits(size_t max) const {
  // Does nothing
}

bool ZPhysicalMemoryBacking::supports_uncommit() {
  assert(!is_init_completed(), "Invalid state");
  assert(_file.size() >= ZGranuleSize, "Invalid size");

  // Test if uncommit is supported by uncommitting and then re-committing a granule
  return commit(uncommit(ZGranuleSize)) == ZGranuleSize;
}

size_t ZPhysicalMemoryBacking::commit(size_t size) {
  size_t committed = 0;

  // Fill holes in the backing file
  while (committed < size) {
    size_t allocated = 0;
    const size_t remaining = size - committed;
    const uintptr_t start = _uncommitted.alloc_from_front_at_most(remaining, &allocated);
    if (start == UINTPTR_MAX) {
      // No holes to commit
      break;
    }

    // Try commit hole
    const size_t filled = _file.commit(start, allocated);
    if (filled > 0) {
      // Successful or partialy successful
      _committed.free(start, filled);
      committed += filled;
    }
    if (filled < allocated) {
      // Failed or partialy failed
      _uncommitted.free(start + filled, allocated - filled);
      return committed;
    }
  }

  // Expand backing file
  if (committed < size) {
    const size_t remaining = size - committed;
    const uintptr_t start = _file.size();
    const size_t expanded = _file.commit(start, remaining);
    if (expanded > 0) {
      // Successful or partialy successful
      _committed.free(start, expanded);
      committed += expanded;
    }
  }

  return committed;
}

size_t ZPhysicalMemoryBacking::uncommit(size_t size) {
  size_t uncommitted = 0;

  // Punch holes in backing file
  while (uncommitted < size) {
    size_t allocated = 0;
    const size_t remaining = size - uncommitted;
    const uintptr_t start = _committed.alloc_from_back_at_most(remaining, &allocated);
    assert(start != UINTPTR_MAX, "Allocation should never fail");

    // Try punch hole
    const size_t punched = _file.uncommit(start, allocated);
    if (punched > 0) {
      // Successful or partialy successful
      _uncommitted.free(start, punched);
      uncommitted += punched;
    }
    if (punched < allocated) {
      // Failed or partialy failed
      _committed.free(start + punched, allocated - punched);
      return uncommitted;
    }
  }

  return uncommitted;
}

ZPhysicalMemory ZPhysicalMemoryBacking::alloc(size_t size) {
  assert(is_aligned(size, ZGranuleSize), "Invalid size");

  ZPhysicalMemory pmem;

  // Allocate segments
  for (size_t allocated = 0; allocated < size; allocated += ZGranuleSize) {
    const uintptr_t start = _committed.alloc_from_front(ZGranuleSize);
    assert(start != UINTPTR_MAX, "Allocation should never fail");
    pmem.add_segment(ZPhysicalMemorySegment(start, ZGranuleSize));
  }

  return pmem;
}

void ZPhysicalMemoryBacking::free(const ZPhysicalMemory& pmem) {
  const size_t nsegments = pmem.nsegments();

  // Free segments
  for (size_t i = 0; i < nsegments; i++) {
    const ZPhysicalMemorySegment& segment = pmem.segment(i);
    _committed.free(segment.start(), segment.size());
  }
}

void ZPhysicalMemoryBacking::pretouch_view(uintptr_t addr, size_t size) const {
  const size_t page_size = ZLargePages::is_explicit() ? ZGranuleSize : os::vm_page_size();
  os::pretouch_memory((void*)addr, (void*)(addr + size), page_size);
}

void ZPhysicalMemoryBacking::map_view(const ZPhysicalMemory& pmem, uintptr_t addr) const {
  const size_t nsegments = pmem.nsegments();
  size_t size = 0;

  // Map segments
  for (size_t i = 0; i < nsegments; i++) {
    const ZPhysicalMemorySegment& segment = pmem.segment(i);
    const uintptr_t segment_addr = addr + size;
    _file.map(segment_addr, segment.size(), segment.start());
    size += segment.size();
  }
}

void ZPhysicalMemoryBacking::unmap_view(const ZPhysicalMemory& pmem, uintptr_t addr) const {
  _file.unmap(addr, pmem.size());
}

uintptr_t ZPhysicalMemoryBacking::nmt_address(uintptr_t offset) const {
  // From an NMT point of view we treat the first heap view (marked0) as committed
  return ZAddress::marked0(offset);
}

void ZPhysicalMemoryBacking::pretouch(uintptr_t offset, size_t size) const {
  if (ZVerifyViews) {
    // Pre-touch good view
    pretouch_view(ZAddress::good(offset), size);
  } else {
    // Pre-touch all views
    pretouch_view(ZAddress::marked0(offset), size);
    pretouch_view(ZAddress::marked1(offset), size);
    pretouch_view(ZAddress::remapped(offset), size);
  }
}

void ZPhysicalMemoryBacking::map(const ZPhysicalMemory& pmem, uintptr_t offset) const {
  if (ZVerifyViews) {
    // Map good view
    map_view(pmem, ZAddress::good(offset));
  } else {
    // Map all views
    map_view(pmem, ZAddress::marked0(offset));
    map_view(pmem, ZAddress::marked1(offset));
    map_view(pmem, ZAddress::remapped(offset));
  }
}

void ZPhysicalMemoryBacking::unmap(const ZPhysicalMemory& pmem, uintptr_t offset) const {
  if (ZVerifyViews) {
    // Unmap good view
    unmap_view(pmem, ZAddress::good(offset));
  } else {
    // Unmap all views
    unmap_view(pmem, ZAddress::marked0(offset));
    unmap_view(pmem, ZAddress::marked1(offset));
    unmap_view(pmem, ZAddress::remapped(offset));
  }
}

void ZPhysicalMemoryBacking::debug_map(const ZPhysicalMemory& pmem, uintptr_t offset) const {
  // Map good view
  assert(ZVerifyViews, "Should be enabled");
  map_view(pmem, ZAddress::good(offset));
}

void ZPhysicalMemoryBacking::debug_unmap(const ZPhysicalMemory& pmem, uintptr_t offset) const {
  // Unmap good view
  assert(ZVerifyViews, "Should be enabled");
  unmap_view(pmem, ZAddress::good(offset));
}
