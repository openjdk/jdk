/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

bool ZPhysicalMemoryManager::is_initialized() const {
  return _backing.is_initialized();
}

void ZPhysicalMemoryManager::warn_commit_limits(size_t max) const {
  _backing.warn_commit_limits(max);
}

bool ZPhysicalMemoryManager::supports_uncommit() {
  return _backing.supports_uncommit();
}

void ZPhysicalMemoryManager::nmt_commit(const ZPhysicalMemory& pmem, uintptr_t offset) const {
  const uintptr_t addr = _backing.nmt_address(offset);
  const size_t size = pmem.size();
  MemTracker::record_virtual_memory_commit((void*)addr, size, CALLER_PC);
}

void ZPhysicalMemoryManager::nmt_uncommit(const ZPhysicalMemory& pmem, uintptr_t offset) const {
  if (MemTracker::tracking_level() > NMT_minimal) {
    const uintptr_t addr = _backing.nmt_address(offset);
    const size_t size = pmem.size();
    Tracker tracker(Tracker::uncommit);
    tracker.record((address)addr, size);
  }
}

size_t ZPhysicalMemoryManager::commit(size_t size) {
  return _backing.commit(size);
}

size_t ZPhysicalMemoryManager::uncommit(size_t size) {
  return _backing.uncommit(size);
}

ZPhysicalMemory ZPhysicalMemoryManager::alloc(size_t size) {
  return _backing.alloc(size);
}

void ZPhysicalMemoryManager::free(const ZPhysicalMemory& pmem) {
  _backing.free(pmem);
}

void ZPhysicalMemoryManager::pretouch(uintptr_t offset, size_t size) const {
  _backing.pretouch(offset, size);
}

void ZPhysicalMemoryManager::map(const ZPhysicalMemory& pmem, uintptr_t offset) const {
  _backing.map(pmem, offset);
  nmt_commit(pmem, offset);
}

void ZPhysicalMemoryManager::unmap(const ZPhysicalMemory& pmem, uintptr_t offset) const {
  nmt_uncommit(pmem, offset);
  _backing.unmap(pmem, offset);
}

void ZPhysicalMemoryManager::debug_map(const ZPhysicalMemory& pmem, uintptr_t offset) const {
  _backing.debug_map(pmem, offset);
}

void ZPhysicalMemoryManager::debug_unmap(const ZPhysicalMemory& pmem, uintptr_t offset) const {
  _backing.debug_unmap(pmem, offset);
}
