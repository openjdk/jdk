/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zGranuleMap.inline.hpp"
#include "gc/z/zMapper_windows.hpp"
#include "gc/z/zPhysicalMemoryBacking_windows.hpp"
#include "logging/log.hpp"
#include "runtime/globals.hpp"
#include "utilities/debug.hpp"

// The backing commits and uncommits physical memory, that can be
// multi-mapped into the virtual address space. To support fine-graned
// committing and uncommitting, each ZGranuleSize'd chunk is mapped to
// a separate paging file mapping.

ZPhysicalMemoryBacking::ZPhysicalMemoryBacking(size_t max_capacity) :
    _handles(max_capacity) {}

bool ZPhysicalMemoryBacking::is_initialized() const {
  return true;
}

void ZPhysicalMemoryBacking::warn_commit_limits(size_t max) const {
  // Does nothing
}

HANDLE ZPhysicalMemoryBacking::get_handle(uintptr_t offset) const {
  HANDLE const handle = _handles.get(offset);
  assert(handle != 0, "Should be set");
  return handle;
}

void ZPhysicalMemoryBacking::put_handle(uintptr_t offset, HANDLE handle) {
  assert(handle != INVALID_HANDLE_VALUE, "Invalid handle");
  assert(_handles.get(offset) == 0, "Should be cleared");
  _handles.put(offset, handle);
}

void ZPhysicalMemoryBacking::clear_handle(uintptr_t offset) {
  assert(_handles.get(offset) != 0, "Should be set");
  _handles.put(offset, 0);
}

size_t ZPhysicalMemoryBacking::commit_from_paging_file(size_t offset, size_t size) {
  for (size_t i = 0; i < size; i += ZGranuleSize) {
    HANDLE const handle = ZMapper::create_and_commit_paging_file_mapping(ZGranuleSize);
    if (handle == 0) {
      return i;
    }

    put_handle(offset + i, handle);
  }

  return size;
}

size_t ZPhysicalMemoryBacking::uncommit_from_paging_file(size_t offset, size_t size) {
  for (size_t i = 0; i < size; i += ZGranuleSize) {
    HANDLE const handle = get_handle(offset + i);
    clear_handle(offset + i);
    ZMapper::close_paging_file_mapping(handle);
  }

  return size;
}

size_t ZPhysicalMemoryBacking::commit(size_t offset, size_t length) {
  log_trace(gc, heap)("Committing memory: " SIZE_FORMAT "M-" SIZE_FORMAT "M (" SIZE_FORMAT "M)",
                      offset / M, (offset + length) / M, length / M);

  return commit_from_paging_file(offset, length);
}

size_t ZPhysicalMemoryBacking::uncommit(size_t offset, size_t length) {
  log_trace(gc, heap)("Uncommitting memory: " SIZE_FORMAT "M-" SIZE_FORMAT "M (" SIZE_FORMAT "M)",
                      offset / M, (offset + length) / M, length / M);

  return uncommit_from_paging_file(offset, length);
}

void ZPhysicalMemoryBacking::map(uintptr_t addr, size_t size, size_t offset) const {
  assert(is_aligned(offset, ZGranuleSize), "Misaligned");
  assert(is_aligned(addr, ZGranuleSize), "Misaligned");
  assert(is_aligned(size, ZGranuleSize), "Misaligned");

  for (size_t i = 0; i < size; i += ZGranuleSize) {
    HANDLE const handle = get_handle(offset + i);
    ZMapper::map_view_replace_placeholder(handle, 0 /* offset */, addr + i, ZGranuleSize);
  }
}

void ZPhysicalMemoryBacking::unmap(uintptr_t addr, size_t size) const {
  assert(is_aligned(addr, ZGranuleSize), "Misaligned");
  assert(is_aligned(size, ZGranuleSize), "Misaligned");

  for (size_t i = 0; i < size; i += ZGranuleSize) {
    ZMapper::unmap_view_preserve_placeholder(addr + i, ZGranuleSize);
  }
}
