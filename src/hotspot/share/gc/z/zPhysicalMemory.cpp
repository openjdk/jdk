/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zNUMA.inline.hpp"
#include "gc/z/zPhysicalMemory.inline.hpp"
#include "runtime/init.hpp"
#include "runtime/os.hpp"
#include "services/memTracker.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

ZPhysicalMemory::ZPhysicalMemory() :
    _nsegments(0),
    _segments(NULL) {}

ZPhysicalMemory::ZPhysicalMemory(const ZPhysicalMemorySegment& segment) :
    _nsegments(0),
    _segments(NULL) {
  add_segment(segment);
}

ZPhysicalMemory::ZPhysicalMemory(const ZPhysicalMemory& pmem) :
    _nsegments(0),
    _segments(NULL) {

  // Copy segments
  for (size_t i = 0; i < pmem.nsegments(); i++) {
    add_segment(pmem.segment(i));
  }
}

const ZPhysicalMemory& ZPhysicalMemory::operator=(const ZPhysicalMemory& pmem) {
  // Free segments
  delete [] _segments;
  _segments = NULL;
  _nsegments = 0;

  // Copy segments
  for (size_t i = 0; i < pmem.nsegments(); i++) {
    add_segment(pmem.segment(i));
  }

  return *this;
}

ZPhysicalMemory::~ZPhysicalMemory() {
  delete [] _segments;
  _segments = NULL;
  _nsegments = 0;
}

size_t ZPhysicalMemory::size() const {
  size_t size = 0;

  for (size_t i = 0; i < _nsegments; i++) {
    size += _segments[i].size();
  }

  return size;
}

void ZPhysicalMemory::add_segment(const ZPhysicalMemorySegment& segment) {
  // Try merge with last segment
  if (_nsegments > 0) {
    ZPhysicalMemorySegment& last = _segments[_nsegments - 1];
    assert(last.end() <= segment.start(), "Segments added out of order");
    if (last.end() == segment.start()) {
      last = ZPhysicalMemorySegment(last.start(), last.size() + segment.size());
      return;
    }
  }

  // Resize array
  ZPhysicalMemorySegment* const old_segments = _segments;
  _segments = new ZPhysicalMemorySegment[_nsegments + 1];
  for (size_t i = 0; i < _nsegments; i++) {
    _segments[i] = old_segments[i];
  }
  delete [] old_segments;

  // Add new segment
  _segments[_nsegments] = segment;
  _nsegments++;
}

ZPhysicalMemory ZPhysicalMemory::split(size_t size) {
  ZPhysicalMemory pmem;
  size_t nsegments = 0;

  for (size_t i = 0; i < _nsegments; i++) {
    const ZPhysicalMemorySegment& segment = _segments[i];
    if (pmem.size() < size) {
      if (pmem.size() + segment.size() <= size) {
        // Transfer segment
        pmem.add_segment(segment);
      } else {
        // Split segment
        const size_t split_size = size - pmem.size();
        pmem.add_segment(ZPhysicalMemorySegment(segment.start(), split_size));
        _segments[nsegments++] = ZPhysicalMemorySegment(segment.start() + split_size, segment.size() - split_size);
      }
    } else {
      // Keep segment
      _segments[nsegments++] = segment;
    }
  }

  _nsegments = nsegments;

  return pmem;
}

ZPhysicalMemoryManager::ZPhysicalMemoryManager(size_t max_capacity) :
    _backing(max_capacity) {
  // Register everything as uncommitted
  _uncommitted.free(0, max_capacity);
}

bool ZPhysicalMemoryManager::is_initialized() const {
  return _backing.is_initialized();
}

void ZPhysicalMemoryManager::warn_commit_limits(size_t max) const {
  _backing.warn_commit_limits(max);
}

bool ZPhysicalMemoryManager::supports_uncommit() {
  assert(!is_init_completed(), "Invalid state");

  // Test if uncommit is supported by uncommitting and then re-committing a granule
  return commit(uncommit(ZGranuleSize)) == ZGranuleSize;
}

void ZPhysicalMemoryManager::nmt_commit(const ZPhysicalMemory& pmem, uintptr_t offset) const {
  // From an NMT point of view we treat the first heap view (marked0) as committed
  const uintptr_t addr = ZAddress::marked0(offset);
  const size_t size = pmem.size();
  MemTracker::record_virtual_memory_commit((void*)addr, size, CALLER_PC);
}

void ZPhysicalMemoryManager::nmt_uncommit(const ZPhysicalMemory& pmem, uintptr_t offset) const {
  if (MemTracker::tracking_level() > NMT_minimal) {
    const uintptr_t addr = ZAddress::marked0(offset);
    const size_t size = pmem.size();
    Tracker tracker(Tracker::uncommit);
    tracker.record((address)addr, size);
  }
}

size_t ZPhysicalMemoryManager::commit(size_t size) {
  size_t committed = 0;

  // Fill holes in the backing memory
  while (committed < size) {
    size_t allocated = 0;
    const size_t remaining = size - committed;
    const uintptr_t start = _uncommitted.alloc_from_front_at_most(remaining, &allocated);
    if (start == UINTPTR_MAX) {
      // No holes to commit
      break;
    }

    // Try commit hole
    const size_t filled = _backing.commit(start, allocated);
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

  return committed;
}

size_t ZPhysicalMemoryManager::uncommit(size_t size) {
  size_t uncommitted = 0;

  // Punch holes in backing memory
  while (uncommitted < size) {
    size_t allocated = 0;
    const size_t remaining = size - uncommitted;
    const uintptr_t start = _committed.alloc_from_back_at_most(remaining, &allocated);
    assert(start != UINTPTR_MAX, "Allocation should never fail");

    // Try punch hole
    const size_t punched = _backing.uncommit(start, allocated);
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

ZPhysicalMemory ZPhysicalMemoryManager::alloc(size_t size) {
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

void ZPhysicalMemoryManager::free(const ZPhysicalMemory& pmem) {
  const size_t nsegments = pmem.nsegments();

  // Free segments
  for (size_t i = 0; i < nsegments; i++) {
    const ZPhysicalMemorySegment& segment = pmem.segment(i);
    _committed.free(segment.start(), segment.size());
  }
}

void ZPhysicalMemoryManager::pretouch_view(uintptr_t addr, size_t size) const {
  const size_t page_size = ZLargePages::is_explicit() ? ZGranuleSize : os::vm_page_size();
  os::pretouch_memory((void*)addr, (void*)(addr + size), page_size);
}

void ZPhysicalMemoryManager::map_view(const ZPhysicalMemory& pmem, uintptr_t addr) const {
  const size_t nsegments = pmem.nsegments();
  size_t size = 0;

  // Map segments
  for (size_t i = 0; i < nsegments; i++) {
    const ZPhysicalMemorySegment& segment = pmem.segment(i);
    _backing.map(addr + size, segment.size(), segment.start());
    size += segment.size();
  }

  // Setup NUMA interleaving for large pages
  if (ZNUMA::is_enabled() && ZLargePages::is_explicit()) {
    // To get granule-level NUMA interleaving when using large pages,
    // we simply let the kernel interleave the memory for us at page
    // fault time.
    os::numa_make_global((char*)addr, size);
  }
}

void ZPhysicalMemoryManager::unmap_view(const ZPhysicalMemory& pmem, uintptr_t addr) const {
  _backing.unmap(addr, pmem.size());
}

void ZPhysicalMemoryManager::pretouch(uintptr_t offset, size_t size) const {
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

void ZPhysicalMemoryManager::map(const ZPhysicalMemory& pmem, uintptr_t offset) const {
  if (ZVerifyViews) {
    // Map good view
    map_view(pmem, ZAddress::good(offset));
  } else {
    // Map all views
    map_view(pmem, ZAddress::marked0(offset));
    map_view(pmem, ZAddress::marked1(offset));
    map_view(pmem, ZAddress::remapped(offset));
  }

  nmt_commit(pmem, offset);
}

void ZPhysicalMemoryManager::unmap(const ZPhysicalMemory& pmem, uintptr_t offset) const {
  nmt_uncommit(pmem, offset);

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

void ZPhysicalMemoryManager::debug_map(const ZPhysicalMemory& pmem, uintptr_t offset) const {
  // Map good view
  assert(ZVerifyViews, "Should be enabled");
  map_view(pmem, ZAddress::good(offset));
}

void ZPhysicalMemoryManager::debug_unmap(const ZPhysicalMemory& pmem, uintptr_t offset) const {
  // Unmap good view
  assert(ZVerifyViews, "Should be enabled");
  unmap_view(pmem, ZAddress::good(offset));
}
