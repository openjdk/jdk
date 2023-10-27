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
#include "gc/shared/gcLogPrecious.hpp"
#include "gc/x/xAddress.inline.hpp"
#include "gc/x/xAddressSpaceLimit.hpp"
#include "gc/x/xGlobals.hpp"
#include "gc/x/xVirtualMemory.inline.hpp"
#include "nmt/memTracker.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"

XVirtualMemoryManager::XVirtualMemoryManager(size_t max_capacity) :
    _manager(),
    _reserved(0),
    _initialized(false) {

  // Check max supported heap size
  if (max_capacity > XAddressOffsetMax) {
    log_error_p(gc)("Java heap too large (max supported heap size is " SIZE_FORMAT "G)",
                    XAddressOffsetMax / G);
    return;
  }

  // Initialize platform specific parts before reserving address space
  pd_initialize_before_reserve();

  // Reserve address space
  if (!reserve(max_capacity)) {
    log_error_pd(gc)("Failed to reserve enough address space for Java heap");
    return;
  }

  // Initialize platform specific parts after reserving address space
  pd_initialize_after_reserve();

  // Successfully initialized
  _initialized = true;
}

size_t XVirtualMemoryManager::reserve_discontiguous(uintptr_t start, size_t size, size_t min_range) {
  if (size < min_range) {
    // Too small
    return 0;
  }

  assert(is_aligned(size, XGranuleSize), "Misaligned");

  if (reserve_contiguous(start, size)) {
    return size;
  }

  const size_t half = size / 2;
  if (half < min_range) {
    // Too small
    return 0;
  }

  // Divide and conquer
  const size_t first_part = align_down(half, XGranuleSize);
  const size_t second_part = size - first_part;
  return reserve_discontiguous(start, first_part, min_range) +
         reserve_discontiguous(start + first_part, second_part, min_range);
}

size_t XVirtualMemoryManager::reserve_discontiguous(size_t size) {
  // Don't try to reserve address ranges smaller than 1% of the requested size.
  // This avoids an explosion of reservation attempts in case large parts of the
  // address space is already occupied.
  const size_t min_range = align_up(size / 100, XGranuleSize);
  size_t start = 0;
  size_t reserved = 0;

  // Reserve size somewhere between [0, XAddressOffsetMax)
  while (reserved < size && start < XAddressOffsetMax) {
    const size_t remaining = MIN2(size - reserved, XAddressOffsetMax - start);
    reserved += reserve_discontiguous(start, remaining, min_range);
    start += remaining;
  }

  return reserved;
}

bool XVirtualMemoryManager::reserve_contiguous(uintptr_t start, size_t size) {
  assert(is_aligned(size, XGranuleSize), "Must be granule aligned");

  // Reserve address views
  const uintptr_t marked0 = XAddress::marked0(start);
  const uintptr_t marked1 = XAddress::marked1(start);
  const uintptr_t remapped = XAddress::remapped(start);

  // Reserve address space
  if (!pd_reserve(marked0, size)) {
    return false;
  }

  if (!pd_reserve(marked1, size)) {
    pd_unreserve(marked0, size);
    return false;
  }

  if (!pd_reserve(remapped, size)) {
    pd_unreserve(marked0, size);
    pd_unreserve(marked1, size);
    return false;
  }

  // Register address views with native memory tracker
  nmt_reserve(marked0, size);
  nmt_reserve(marked1, size);
  nmt_reserve(remapped, size);

  // Make the address range free
  _manager.free(start, size);

  return true;
}

bool XVirtualMemoryManager::reserve_contiguous(size_t size) {
  // Allow at most 8192 attempts spread evenly across [0, XAddressOffsetMax)
  const size_t unused = XAddressOffsetMax - size;
  const size_t increment = MAX2(align_up(unused / 8192, XGranuleSize), XGranuleSize);

  for (size_t start = 0; start + size <= XAddressOffsetMax; start += increment) {
    if (reserve_contiguous(start, size)) {
      // Success
      return true;
    }
  }

  // Failed
  return false;
}

bool XVirtualMemoryManager::reserve(size_t max_capacity) {
  const size_t limit = MIN2(XAddressOffsetMax, XAddressSpaceLimit::heap_view());
  const size_t size = MIN2(max_capacity * XVirtualToPhysicalRatio, limit);

  size_t reserved = size;
  bool contiguous = true;

  // Prefer a contiguous address space
  if (!reserve_contiguous(size)) {
    // Fall back to a discontiguous address space
    reserved = reserve_discontiguous(size);
    contiguous = false;
  }

  log_info_p(gc, init)("Address Space Type: %s/%s/%s",
                       (contiguous ? "Contiguous" : "Discontiguous"),
                       (limit == XAddressOffsetMax ? "Unrestricted" : "Restricted"),
                       (reserved == size ? "Complete" : "Degraded"));
  log_info_p(gc, init)("Address Space Size: " SIZE_FORMAT "M x " SIZE_FORMAT " = " SIZE_FORMAT "M",
                       reserved / M, XHeapViews, (reserved * XHeapViews) / M);

  // Record reserved
  _reserved = reserved;

  return reserved >= max_capacity;
}

void XVirtualMemoryManager::nmt_reserve(uintptr_t start, size_t size) {
  MemTracker::record_virtual_memory_reserve((void*)start, size, CALLER_PC);
  MemTracker::record_virtual_memory_type((void*)start, mtJavaHeap);
}

bool XVirtualMemoryManager::is_initialized() const {
  return _initialized;
}

XVirtualMemory XVirtualMemoryManager::alloc(size_t size, bool force_low_address) {
  uintptr_t start;

  // Small pages are allocated at low addresses, while medium/large pages
  // are allocated at high addresses (unless forced to be at a low address).
  if (force_low_address || size <= XPageSizeSmall) {
    start = _manager.alloc_low_address(size);
  } else {
    start = _manager.alloc_high_address(size);
  }

  return XVirtualMemory(start, size);
}

void XVirtualMemoryManager::free(const XVirtualMemory& vmem) {
  _manager.free(vmem.start(), vmem.size());
}
