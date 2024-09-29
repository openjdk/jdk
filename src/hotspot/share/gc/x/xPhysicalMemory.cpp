/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/gcLogPrecious.hpp"
#include "gc/x/xAddress.inline.hpp"
#include "gc/x/xArray.inline.hpp"
#include "gc/x/xGlobals.hpp"
#include "gc/x/xLargePages.inline.hpp"
#include "gc/x/xList.inline.hpp"
#include "gc/x/xNUMA.inline.hpp"
#include "gc/x/xPhysicalMemory.inline.hpp"
#include "logging/log.hpp"
#include "nmt/memTracker.hpp"
#include "runtime/globals.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/init.hpp"
#include "runtime/os.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/powerOfTwo.hpp"

XPhysicalMemory::XPhysicalMemory() :
    _segments() {}

XPhysicalMemory::XPhysicalMemory(const XPhysicalMemorySegment& segment) :
    _segments() {
  add_segment(segment);
}

XPhysicalMemory::XPhysicalMemory(const XPhysicalMemory& pmem) :
    _segments() {
  add_segments(pmem);
}

const XPhysicalMemory& XPhysicalMemory::operator=(const XPhysicalMemory& pmem) {
  // Free segments
  _segments.clear_and_deallocate();

  // Copy segments
  add_segments(pmem);

  return *this;
}

size_t XPhysicalMemory::size() const {
  size_t size = 0;

  for (int i = 0; i < _segments.length(); i++) {
    size += _segments.at(i).size();
  }

  return size;
}

void XPhysicalMemory::insert_segment(int index, uintptr_t start, size_t size, bool committed) {
  _segments.insert_before(index, XPhysicalMemorySegment(start, size, committed));
}

void XPhysicalMemory::replace_segment(int index, uintptr_t start, size_t size, bool committed) {
  _segments.at_put(index, XPhysicalMemorySegment(start, size, committed));
}

void XPhysicalMemory::remove_segment(int index) {
  _segments.remove_at(index);
}

void XPhysicalMemory::add_segments(const XPhysicalMemory& pmem) {
  for (int i = 0; i < pmem.nsegments(); i++) {
    add_segment(pmem.segment(i));
  }
}

void XPhysicalMemory::remove_segments() {
  _segments.clear_and_deallocate();
}

static bool is_mergable(const XPhysicalMemorySegment& before, const XPhysicalMemorySegment& after) {
  return before.end() == after.start() && before.is_committed() == after.is_committed();
}

void XPhysicalMemory::add_segment(const XPhysicalMemorySegment& segment) {
  // Insert segments in address order, merge segments when possible
  for (int i = _segments.length(); i > 0; i--) {
    const int current = i - 1;

    if (_segments.at(current).end() <= segment.start()) {
      if (is_mergable(_segments.at(current), segment)) {
        if (current + 1 < _segments.length() && is_mergable(segment, _segments.at(current + 1))) {
          // Merge with end of current segment and start of next segment
          const size_t start = _segments.at(current).start();
          const size_t size = _segments.at(current).size() + segment.size() + _segments.at(current + 1).size();
          replace_segment(current, start, size, segment.is_committed());
          remove_segment(current + 1);
          return;
        }

        // Merge with end of current segment
        const size_t start = _segments.at(current).start();
        const size_t size = _segments.at(current).size() + segment.size();
        replace_segment(current, start, size, segment.is_committed());
        return;
      } else if (current + 1 < _segments.length() && is_mergable(segment, _segments.at(current + 1))) {
        // Merge with start of next segment
        const size_t start = segment.start();
        const size_t size = segment.size() + _segments.at(current + 1).size();
        replace_segment(current + 1, start, size, segment.is_committed());
        return;
      }

      // Insert after current segment
      insert_segment(current + 1, segment.start(), segment.size(), segment.is_committed());
      return;
    }
  }

  if (_segments.length() > 0 && is_mergable(segment, _segments.at(0))) {
    // Merge with start of first segment
    const size_t start = segment.start();
    const size_t size = segment.size() + _segments.at(0).size();
    replace_segment(0, start, size, segment.is_committed());
    return;
  }

  // Insert before first segment
  insert_segment(0, segment.start(), segment.size(), segment.is_committed());
}

bool XPhysicalMemory::commit_segment(int index, size_t size) {
  assert(size <= _segments.at(index).size(), "Invalid size");
  assert(!_segments.at(index).is_committed(), "Invalid state");

  if (size == _segments.at(index).size()) {
    // Completely committed
    _segments.at(index).set_committed(true);
    return true;
  }

  if (size > 0) {
    // Partially committed, split segment
    insert_segment(index + 1, _segments.at(index).start() + size, _segments.at(index).size() - size, false /* committed */);
    replace_segment(index, _segments.at(index).start(), size, true /* committed */);
  }

  return false;
}

bool XPhysicalMemory::uncommit_segment(int index, size_t size) {
  assert(size <= _segments.at(index).size(), "Invalid size");
  assert(_segments.at(index).is_committed(), "Invalid state");

  if (size == _segments.at(index).size()) {
    // Completely uncommitted
    _segments.at(index).set_committed(false);
    return true;
  }

  if (size > 0) {
    // Partially uncommitted, split segment
    insert_segment(index + 1, _segments.at(index).start() + size, _segments.at(index).size() - size, true /* committed */);
    replace_segment(index, _segments.at(index).start(), size, false /* committed */);
  }

  return false;
}

XPhysicalMemory XPhysicalMemory::split(size_t size) {
  XPhysicalMemory pmem;
  int nsegments = 0;

  for (int i = 0; i < _segments.length(); i++) {
    const XPhysicalMemorySegment& segment = _segments.at(i);
    if (pmem.size() < size) {
      if (pmem.size() + segment.size() <= size) {
        // Transfer segment
        pmem.add_segment(segment);
      } else {
        // Split segment
        const size_t split_size = size - pmem.size();
        pmem.add_segment(XPhysicalMemorySegment(segment.start(), split_size, segment.is_committed()));
        _segments.at_put(nsegments++, XPhysicalMemorySegment(segment.start() + split_size, segment.size() - split_size, segment.is_committed()));
      }
    } else {
      // Keep segment
      _segments.at_put(nsegments++, segment);
    }
  }

  _segments.trunc_to(nsegments);

  return pmem;
}

XPhysicalMemory XPhysicalMemory::split_committed() {
  XPhysicalMemory pmem;
  int nsegments = 0;

  for (int i = 0; i < _segments.length(); i++) {
    const XPhysicalMemorySegment& segment = _segments.at(i);
    if (segment.is_committed()) {
      // Transfer segment
      pmem.add_segment(segment);
    } else {
      // Keep segment
      _segments.at_put(nsegments++, segment);
    }
  }

  _segments.trunc_to(nsegments);

  return pmem;
}

XPhysicalMemoryManager::XPhysicalMemoryManager(size_t max_capacity) :
    _backing(max_capacity) {
  // Make the whole range free
  _manager.free(0, max_capacity);
}

bool XPhysicalMemoryManager::is_initialized() const {
  return _backing.is_initialized();
}

void XPhysicalMemoryManager::warn_commit_limits(size_t max_capacity) const {
  _backing.warn_commit_limits(max_capacity);
}

void XPhysicalMemoryManager::try_enable_uncommit(size_t min_capacity, size_t max_capacity) {
  assert(!is_init_completed(), "Invalid state");

  // If uncommit is not explicitly disabled, max capacity is greater than
  // min capacity, and uncommit is supported by the platform, then uncommit
  // will be enabled.
  if (!ZUncommit) {
    log_info_p(gc, init)("Uncommit: Disabled");
    return;
  }

  if (max_capacity == min_capacity) {
    log_info_p(gc, init)("Uncommit: Implicitly Disabled (-Xms equals -Xmx)");
    FLAG_SET_ERGO(ZUncommit, false);
    return;
  }

  // Test if uncommit is supported by the operating system by committing
  // and then uncommitting a granule.
  XPhysicalMemory pmem(XPhysicalMemorySegment(0, XGranuleSize, false /* committed */));
  if (!commit(pmem) || !uncommit(pmem)) {
    log_info_p(gc, init)("Uncommit: Implicitly Disabled (Not supported by operating system)");
    FLAG_SET_ERGO(ZUncommit, false);
    return;
  }

  log_info_p(gc, init)("Uncommit: Enabled");
  log_info_p(gc, init)("Uncommit Delay: " UINTX_FORMAT "s", ZUncommitDelay);
}

void XPhysicalMemoryManager::nmt_commit(uintptr_t offset, size_t size) const {
  // From an NMT point of view we treat the first heap view (marked0) as committed
  const uintptr_t addr = XAddress::marked0(offset);
  MemTracker::record_virtual_memory_commit((void*)addr, size, CALLER_PC);
}

void XPhysicalMemoryManager::nmt_uncommit(uintptr_t offset, size_t size) const {
  const uintptr_t addr = XAddress::marked0(offset);
  ThreadCritical tc;
  MemTracker::record_virtual_memory_uncommit((address)addr, size);
}

void XPhysicalMemoryManager::alloc(XPhysicalMemory& pmem, size_t size) {
  assert(is_aligned(size, XGranuleSize), "Invalid size");

  // Allocate segments
  while (size > 0) {
    size_t allocated = 0;
    const uintptr_t start = _manager.alloc_low_address_at_most(size, &allocated);
    assert(start != UINTPTR_MAX, "Allocation should never fail");
    pmem.add_segment(XPhysicalMemorySegment(start, allocated, false /* committed */));
    size -= allocated;
  }
}

void XPhysicalMemoryManager::free(const XPhysicalMemory& pmem) {
  // Free segments
  for (int i = 0; i < pmem.nsegments(); i++) {
    const XPhysicalMemorySegment& segment = pmem.segment(i);
    _manager.free(segment.start(), segment.size());
  }
}

bool XPhysicalMemoryManager::commit(XPhysicalMemory& pmem) {
  // Commit segments
  for (int i = 0; i < pmem.nsegments(); i++) {
    const XPhysicalMemorySegment& segment = pmem.segment(i);
    if (segment.is_committed()) {
      // Segment already committed
      continue;
    }

    // Commit segment
    const size_t committed = _backing.commit(segment.start(), segment.size());
    if (!pmem.commit_segment(i, committed)) {
      // Failed or partially failed
      return false;
    }
  }

  // Success
  return true;
}

bool XPhysicalMemoryManager::uncommit(XPhysicalMemory& pmem) {
  // Commit segments
  for (int i = 0; i < pmem.nsegments(); i++) {
    const XPhysicalMemorySegment& segment = pmem.segment(i);
    if (!segment.is_committed()) {
      // Segment already uncommitted
      continue;
    }

    // Uncommit segment
    const size_t uncommitted = _backing.uncommit(segment.start(), segment.size());
    if (!pmem.uncommit_segment(i, uncommitted)) {
      // Failed or partially failed
      return false;
    }
  }

  // Success
  return true;
}

void XPhysicalMemoryManager::pretouch_view(uintptr_t addr, size_t size) const {
  const size_t page_size = XLargePages::is_explicit() ? XGranuleSize : os::vm_page_size();
  os::pretouch_memory((void*)addr, (void*)(addr + size), page_size);
}

void XPhysicalMemoryManager::map_view(uintptr_t addr, const XPhysicalMemory& pmem) const {
  size_t size = 0;

  // Map segments
  for (int i = 0; i < pmem.nsegments(); i++) {
    const XPhysicalMemorySegment& segment = pmem.segment(i);
    _backing.map(addr + size, segment.size(), segment.start());
    size += segment.size();
  }

  // Setup NUMA interleaving for large pages
  if (XNUMA::is_enabled() && XLargePages::is_explicit()) {
    // To get granule-level NUMA interleaving when using large pages,
    // we simply let the kernel interleave the memory for us at page
    // fault time.
    os::numa_make_global((char*)addr, size);
  }
}

void XPhysicalMemoryManager::unmap_view(uintptr_t addr, size_t size) const {
  _backing.unmap(addr, size);
}

void XPhysicalMemoryManager::pretouch(uintptr_t offset, size_t size) const {
  if (ZVerifyViews) {
    // Pre-touch good view
    pretouch_view(XAddress::good(offset), size);
  } else {
    // Pre-touch all views
    pretouch_view(XAddress::marked0(offset), size);
    pretouch_view(XAddress::marked1(offset), size);
    pretouch_view(XAddress::remapped(offset), size);
  }
}

void XPhysicalMemoryManager::map(uintptr_t offset, const XPhysicalMemory& pmem) const {
  const size_t size = pmem.size();

  if (ZVerifyViews) {
    // Map good view
    map_view(XAddress::good(offset), pmem);
  } else {
    // Map all views
    map_view(XAddress::marked0(offset), pmem);
    map_view(XAddress::marked1(offset), pmem);
    map_view(XAddress::remapped(offset), pmem);
  }

  nmt_commit(offset, size);
}

void XPhysicalMemoryManager::unmap(uintptr_t offset, size_t size) const {
  nmt_uncommit(offset, size);

  if (ZVerifyViews) {
    // Unmap good view
    unmap_view(XAddress::good(offset), size);
  } else {
    // Unmap all views
    unmap_view(XAddress::marked0(offset), size);
    unmap_view(XAddress::marked1(offset), size);
    unmap_view(XAddress::remapped(offset), size);
  }
}

void XPhysicalMemoryManager::debug_map(uintptr_t offset, const XPhysicalMemory& pmem) const {
  // Map good view
  assert(ZVerifyViews, "Should be enabled");
  map_view(XAddress::good(offset), pmem);
}

void XPhysicalMemoryManager::debug_unmap(uintptr_t offset, size_t size) const {
  // Unmap good view
  assert(ZVerifyViews, "Should be enabled");
  unmap_view(XAddress::good(offset), size);
}
