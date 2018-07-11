/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zPhysicalMemory.inline.hpp"
#include "logging/log.hpp"
#include "memory/allocation.inline.hpp"
#include "services/memTracker.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

ZPhysicalMemory::ZPhysicalMemory() :
    _nsegments(0),
    _segments(NULL) {}

ZPhysicalMemory::ZPhysicalMemory(size_t size) :
    _nsegments(0),
    _segments(NULL) {
  add_segment(ZPhysicalMemorySegment(0, size));
}

ZPhysicalMemory::ZPhysicalMemory(const ZPhysicalMemorySegment& segment) :
    _nsegments(0),
    _segments(NULL) {
  add_segment(segment);
}

size_t ZPhysicalMemory::size() const {
  size_t size = 0;

  for (size_t i = 0; i < _nsegments; i++) {
    size += _segments[i].size();
  }

  return size;
}

void ZPhysicalMemory::add_segment(ZPhysicalMemorySegment segment) {
  // Try merge with last segment
  if (_nsegments > 0) {
    ZPhysicalMemorySegment& last = _segments[_nsegments - 1];
    assert(last.end() <= segment.start(), "Segments added out of order");
    if (last.end() == segment.start()) {
      // Merge
      last.expand(segment.size());
      return;
    }
  }

  // Make room for a new segment
  const size_t size = sizeof(ZPhysicalMemorySegment) * (_nsegments + 1);
  _segments = (ZPhysicalMemorySegment*)ReallocateHeap((char*)_segments, size, mtGC);

  // Add new segment
  _segments[_nsegments] = segment;
  _nsegments++;
}

ZPhysicalMemory ZPhysicalMemory::split(size_t split_size) {
  // Only splitting of single-segment instances have been implemented.
  assert(nsegments() == 1, "Can only have one segment");
  assert(split_size <= size(), "Invalid size");
  return ZPhysicalMemory(_segments[0].split(split_size));
}

void ZPhysicalMemory::clear() {
  if (_segments != NULL) {
    FreeHeap(_segments);
    _segments = NULL;
    _nsegments = 0;
  }
}

ZPhysicalMemoryManager::ZPhysicalMemoryManager(size_t max_capacity, size_t granule_size) :
    _backing(max_capacity, granule_size),
    _max_capacity(max_capacity),
    _current_max_capacity(max_capacity),
    _capacity(0),
    _used(0) {}

bool ZPhysicalMemoryManager::is_initialized() const {
  return _backing.is_initialized();
}

void ZPhysicalMemoryManager::try_ensure_unused_capacity(size_t size) {
  const size_t unused = unused_capacity();
  if (unused >= size) {
    // Don't try to expand, enough unused capacity available
    return;
  }

  const size_t current_max = current_max_capacity();
  if (_capacity == current_max) {
    // Don't try to expand, current max capacity reached
    return;
  }

  // Try to expand
  const size_t old_capacity = capacity();
  const size_t new_capacity = MIN2(old_capacity + size - unused, current_max);
  _capacity = _backing.try_expand(old_capacity, new_capacity);

  if (_capacity != new_capacity) {
    // Failed, or partly failed, to expand
    log_error(gc, init)("Not enough space available on the backing filesystem to hold the current max");
    log_error(gc, init)("Java heap size (" SIZE_FORMAT "M). Forcefully lowering max Java heap size to "
                        SIZE_FORMAT "M (%.0lf%%).", current_max / M, _capacity / M,
                        percent_of(_capacity, current_max));

    // Adjust current max capacity to avoid further expand attempts
    _current_max_capacity = _capacity;
  }
}

void ZPhysicalMemoryManager::nmt_commit(ZPhysicalMemory pmem, uintptr_t offset) {
  const uintptr_t addr = _backing.nmt_address(offset);
  const size_t size = pmem.size();
  MemTracker::record_virtual_memory_commit((void*)addr, size, CALLER_PC);
}

void ZPhysicalMemoryManager::nmt_uncommit(ZPhysicalMemory pmem, uintptr_t offset) {
  if (MemTracker::tracking_level() > NMT_minimal) {
    const uintptr_t addr = _backing.nmt_address(offset);
    const size_t size = pmem.size();

    Tracker tracker(Tracker::uncommit);
    tracker.record((address)addr, size);
  }
}

ZPhysicalMemory ZPhysicalMemoryManager::alloc(size_t size) {
  if (unused_capacity() < size) {
    // Not enough memory available
    return ZPhysicalMemory();
  }

  _used += size;
  return _backing.alloc(size);
}

void ZPhysicalMemoryManager::free(ZPhysicalMemory pmem) {
  _backing.free(pmem);
  _used -= pmem.size();
}

void ZPhysicalMemoryManager::map(ZPhysicalMemory pmem, uintptr_t offset) {
  // Map page
  _backing.map(pmem, offset);

  // Update native memory tracker
  nmt_commit(pmem, offset);
}

void ZPhysicalMemoryManager::unmap(ZPhysicalMemory pmem, uintptr_t offset) {
  // Update native memory tracker
  nmt_uncommit(pmem, offset);

  // Unmap page
  _backing.unmap(pmem, offset);
}

void ZPhysicalMemoryManager::flip(ZPhysicalMemory pmem, uintptr_t offset) {
  _backing.flip(pmem, offset);
}
