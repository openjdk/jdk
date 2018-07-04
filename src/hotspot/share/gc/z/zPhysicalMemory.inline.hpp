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

#ifndef SHARE_GC_Z_ZPHYSICALMEMORY_INLINE_HPP
#define SHARE_GC_Z_ZPHYSICALMEMORY_INLINE_HPP

#include "gc/z/zPhysicalMemory.hpp"
#include "utilities/debug.hpp"

inline ZPhysicalMemorySegment::ZPhysicalMemorySegment(uintptr_t start, size_t size) :
    _start(start),
    _end(start + size) {}

inline uintptr_t ZPhysicalMemorySegment::start() const {
  return _start;
}

inline uintptr_t ZPhysicalMemorySegment::end() const {
  return _end;
}

inline size_t ZPhysicalMemorySegment::size() const {
  return end() - start();
}

inline void ZPhysicalMemorySegment::expand(size_t size) {
  _end += size;
}

inline ZPhysicalMemorySegment ZPhysicalMemorySegment::split(size_t split_size) {
  assert(split_size <= size(), "Invalid size");
  ZPhysicalMemorySegment segment(_start, split_size);
  _start += split_size;
  return segment;
}

inline bool ZPhysicalMemory::is_null() const {
  return _nsegments == 0;
}

inline size_t ZPhysicalMemory::nsegments() const {
  return _nsegments;
}

inline ZPhysicalMemorySegment ZPhysicalMemory::segment(size_t index) const {
  assert(index < _nsegments, "Invalid segment index");
  return _segments[index];
}

inline size_t ZPhysicalMemoryManager::max_capacity() const {
  return _max_capacity;
}

inline size_t ZPhysicalMemoryManager::current_max_capacity() const {
  return _current_max_capacity;
}

inline size_t ZPhysicalMemoryManager::capacity() const {
  return _capacity;
}

inline size_t ZPhysicalMemoryManager::unused_capacity() const {
  return _capacity - _used;
}

#endif // SHARE_GC_Z_ZPHYSICALMEMORY_INLINE_HPP
