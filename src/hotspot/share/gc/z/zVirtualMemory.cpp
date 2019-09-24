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
#include "gc/z/zGlobals.hpp"
#include "gc/z/zVirtualMemory.inline.hpp"
#include "logging/log.hpp"
#include "services/memTracker.hpp"

ZVirtualMemoryManager::ZVirtualMemoryManager(size_t max_capacity) :
    _manager(),
    _initialized(false) {

  log_info(gc, init)("Address Space: " SIZE_FORMAT "T", ZAddressOffsetMax / K / G);

  // Reserve address space
  if (reserve(0, ZAddressOffsetMax) < max_capacity) {
    log_error(gc)("Failed to reserve address space for Java heap");
    return;
  }

  // Successfully initialized
  _initialized = true;
}

size_t ZVirtualMemoryManager::reserve(uintptr_t start, size_t size) {
  if (size < ZGranuleSize) {
    // Too small
    return 0;
  }

  if (!reserve_platform(start, size)) {
    const size_t half = size / 2;
    return reserve(start, half) + reserve(start + half, half);
  }

  // Make the address range free
  _manager.free(start, size);

  return size;
}

void ZVirtualMemoryManager::nmt_reserve(uintptr_t start, size_t size) {
  MemTracker::record_virtual_memory_reserve((void*)start, size, CALLER_PC);
  MemTracker::record_virtual_memory_type((void*)start, mtJavaHeap);
}

bool ZVirtualMemoryManager::is_initialized() const {
  return _initialized;
}

ZVirtualMemory ZVirtualMemoryManager::alloc(size_t size, bool alloc_from_front) {
  uintptr_t start;

  if (alloc_from_front || size <= ZPageSizeSmall) {
    // Small page
    start = _manager.alloc_from_front(size);
  } else {
    // Medium/Large page
    start = _manager.alloc_from_back(size);
  }

  return ZVirtualMemory(start, size);
}

void ZVirtualMemoryManager::free(const ZVirtualMemory& vmem) {
  _manager.free(vmem.start(), vmem.size());
}
