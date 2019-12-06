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
#include "gc/z/zMapper_windows.hpp"
#include "gc/z/zVirtualMemory.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"

static void split_placeholder(uintptr_t start, size_t size) {
  ZMapper::split_placeholder(ZAddress::marked0(start), size);
  ZMapper::split_placeholder(ZAddress::marked1(start), size);
  ZMapper::split_placeholder(ZAddress::remapped(start), size);
}

static void coalesce_placeholders(uintptr_t start, size_t size) {
  ZMapper::coalesce_placeholders(ZAddress::marked0(start), size);
  ZMapper::coalesce_placeholders(ZAddress::marked1(start), size);
  ZMapper::coalesce_placeholders(ZAddress::remapped(start), size);
}

static void split_into_placeholder_granules(uintptr_t start, size_t size) {
  for (uintptr_t addr = start; addr < start + size; addr += ZGranuleSize) {
    split_placeholder(addr, ZGranuleSize);
  }
}

static void coalesce_into_one_placeholder(uintptr_t start, size_t size) {
  assert(is_aligned(size, ZGranuleSize), "Must be granule aligned");

  if (size > ZGranuleSize) {
    coalesce_placeholders(start, size);
  }
}

static void create_callback(const ZMemory* area) {
  assert(is_aligned(area->size(), ZGranuleSize), "Must be granule aligned");
  coalesce_into_one_placeholder(area->start(), area->size());
}

static void destroy_callback(const ZMemory* area) {
  assert(is_aligned(area->size(), ZGranuleSize), "Must be granule aligned");
  // Don't try split the last granule - VirtualFree will fail
  split_into_placeholder_granules(area->start(), area->size() - ZGranuleSize);
}

static void shrink_from_front_callback(const ZMemory* area, size_t size) {
  assert(is_aligned(size, ZGranuleSize), "Must be granule aligned");
  split_into_placeholder_granules(area->start(), size);
}

static void shrink_from_back_callback(const ZMemory* area, size_t size) {
  assert(is_aligned(size, ZGranuleSize), "Must be granule aligned");
  // Don't try split the last granule - VirtualFree will fail
  split_into_placeholder_granules(area->end() - size, size - ZGranuleSize);
}

static void grow_from_front_callback(const ZMemory* area, size_t size) {
  assert(is_aligned(area->size(), ZGranuleSize), "Must be granule aligned");
  coalesce_into_one_placeholder(area->start() - size, area->size() + size);
}

static void grow_from_back_callback(const ZMemory* area, size_t size) {
  assert(is_aligned(area->size(), ZGranuleSize), "Must be granule aligned");
  coalesce_into_one_placeholder(area->start(), area->size() + size);
}

void ZVirtualMemoryManager::initialize_os() {
  // Each reserved virtual memory address area registered in _manager is
  // exactly covered by a single placeholder. Callbacks are installed so
  // that whenever a memory area changes, the corresponding placeholder
  // is adjusted.
  //
  // The create and grow callbacks are called when virtual memory is
  // returned to the memory manager. The new memory area is then covered
  // by a new single placeholder.
  //
  // The destroy and shrink callbacks are called when virtual memory is
  // allocated from the memory manager. The memory area is then is split
  // into granule-sized placeholders.
  //
  // See comment in zMapper_windows.cpp explaining why placeholders are
  // split into ZGranuleSize sized placeholders.

  ZMemoryManager::Callbacks callbacks;

  callbacks._create = &create_callback;
  callbacks._destroy = &destroy_callback;
  callbacks._shrink_from_front = &shrink_from_front_callback;
  callbacks._shrink_from_back = &shrink_from_back_callback;
  callbacks._grow_from_front = &grow_from_front_callback;
  callbacks._grow_from_back = &grow_from_back_callback;

  _manager.register_callbacks(callbacks);
}

bool ZVirtualMemoryManager::reserve_contiguous_platform(uintptr_t start, size_t size) {
  assert(is_aligned(size, ZGranuleSize), "Must be granule aligned");

  // Reserve address views
  const uintptr_t marked0 = ZAddress::marked0(start);
  const uintptr_t marked1 = ZAddress::marked1(start);
  const uintptr_t remapped = ZAddress::remapped(start);

  // Reserve address space
  if (ZMapper::reserve(marked0, size) != marked0) {
    return false;
  }

  if (ZMapper::reserve(marked1, size) != marked1) {
    ZMapper::unreserve(marked0, size);
    return false;
  }

  if (ZMapper::reserve(remapped, size) != remapped) {
    ZMapper::unreserve(marked0, size);
    ZMapper::unreserve(marked1, size);
    return false;
  }

  // Register address views with native memory tracker
  nmt_reserve(marked0, size);
  nmt_reserve(marked1, size);
  nmt_reserve(remapped, size);

  return true;
}
