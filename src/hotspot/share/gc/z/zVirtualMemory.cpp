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
#include "gc/z/zGlobals.hpp"
#include "gc/z/zVirtualMemory.inline.hpp"
#include "services/memTracker.hpp"

ZVirtualMemoryManager::ZVirtualMemoryManager() :
    _manager(),
    _initialized(false) {
  // Reserve address space
  if (!reserve(ZAddressSpaceStart, ZAddressSpaceSize)) {
    return;
  }

  // Make the complete address view free
  _manager.free(0, ZAddressOffsetMax);

  // Register address space with native memory tracker
  nmt_reserve(ZAddressSpaceStart, ZAddressSpaceSize);

  // Successfully initialized
  _initialized = true;
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

void ZVirtualMemoryManager::free(ZVirtualMemory vmem) {
  _manager.free(vmem.start(), vmem.size());
}
