/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zMapper_windows.hpp"
#include "gc/z/zSyscall_windows.hpp"
#include "gc/z/zVirtualMemory.inline.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"

class ZVirtualMemoryManagerImpl : public CHeapObj<mtGC> {
public:
  virtual void initialize_before_reserve() {}
  virtual void initialize_after_reserve(ZMemoryManager* manager) {}
  virtual bool reserve(zaddress_unsafe addr, size_t size) = 0;
  virtual void unreserve(zaddress_unsafe addr, size_t size) = 0;
};

// Implements small pages (paged) support using placeholder reservation.
// When a memory area is free (kept by the virtual memory manager) a
// single placeholder is covering that memory area. When memory is
// allocated from the manager the placeholder is split into granule
// sized placeholders to allow mapping operations on that granulairty.
class ZVirtualMemoryManagerSmallPages : public ZVirtualMemoryManagerImpl {
private:
  class PlaceholderCallbacks : public AllStatic {
  public:
    // Split an existing placeholder to create a new placeholder
    // for the requested memory area.
    static void split_placeholder(zoffset start, size_t size) {
      ZMapper::split_placeholder(ZOffset::address_unsafe(start), size);
    }

    // Coalesce all placeholders covering the given memory area.
    static void coalesce_placeholders(zoffset start, size_t size) {
      ZMapper::coalesce_placeholders(ZOffset::address_unsafe(start), size);
    }

    // Turn the single placeholder covering a memroy area into granule
    // sized placeholders. This is done by splitting granule sized placeholders
    // from the covering placeholder until the whole area is handled.
    static void create_granule_sized_placeholders(zoffset start, size_t size) {
      size_t current_placeholder_size = size;
      zoffset current_placeholder_offset = start;
      // Split the current placeholder as long as it is larger than a granule
      while (current_placeholder_size > ZGranuleSize) {
        split_placeholder(current_placeholder_offset, ZGranuleSize);
        current_placeholder_offset += ZGranuleSize;
        current_placeholder_size -= ZGranuleSize;
      }
    }

    static void coalesce_into_one_placeholder(zoffset start, size_t size) {
      assert(is_aligned(size, ZGranuleSize), "Must be granule aligned");

      if (size > ZGranuleSize) {
        coalesce_placeholders(start, size);
      }
    }

    // Called when a memory area is returned to the memory manager but can't
    // be merged with an already existing area. Make sure this area is covered
    // by a single placeholder.
    static void create_callback(const ZMemory* area) {
      assert(is_aligned(area->size(), ZGranuleSize), "Must be granule aligned");
      coalesce_into_one_placeholder(area->start(), area->size());
    }

    // Called when a complete memory area in the memory manager is allocated.
    // Create granule sized placeholder for the entier area.
    static void destroy_callback(const ZMemory* area) {
      assert(is_aligned(area->size(), ZGranuleSize), "Must be granule aligned");
      create_granule_sized_placeholders(area->start(), area->size());
    }

    // Called when a memory area is allocated at the front of an exising memory area.
    // Turn the first part of the memory area into granule sized placeholders.
    static void shrink_from_front_callback(const ZMemory* area, size_t size) {
      assert(area->size() > size, "Must be larger than what we try to split out");
      assert(is_aligned(size, ZGranuleSize), "Must be granule aligned");

      // Before creating the granule sized placeholders we need to make sure
      // there is a placeholder covering that exact area. Otherwise splitting
      // of a single granule sized won't work.
      split_placeholder(area->start(), size);
      create_granule_sized_placeholders(area->start(), size);
    }

    // Called when a memory area is allocated at the end of an existing memory area.
    // Turn the second part of the memory area into granule sized placeholders.
    static void shrink_from_back_callback(const ZMemory* area, size_t size) {
      assert(area->size() > size, "Must be larger than what we try to split out");
      assert(is_aligned(size, ZGranuleSize), "Must be granule aligned");

      // Before creating the granule sized placeholders we need to make sure
      // there is a placeholder covering that exact area. Otherwise splitting
      // of a single granule sized won't work.
      zoffset placeholder_start = to_zoffset(untype(area->end()) - size);

      split_placeholder(placeholder_start, size);
      create_granule_sized_placeholders(placeholder_start, size);
    }

    // Called when freeing a memory area and it can be merged at the start of an
    // existing area. Coalesce the underlying placeholders into one.
    static void grow_from_front_callback(const ZMemory* area, size_t size) {
      assert(is_aligned(area->size(), ZGranuleSize), "Must be granule aligned");

      size_t placeholder_size = area->size() + size;
      zoffset placeholder_start = to_zoffset(untype(area->start()) - size);
      coalesce_into_one_placeholder(placeholder_start, placeholder_size);
    }

    // Called when freeing a memory area and it can be merged at the end of an
    // existing area. Coalesce the underlying placeholders into one.
    static void grow_from_back_callback(const ZMemory* area, size_t size) {
      assert(is_aligned(area->size(), ZGranuleSize), "Must be granule aligned");

      size_t placeholder_size = area->size() + size;
      coalesce_into_one_placeholder(area->start(), placeholder_size);
    }

    static void register_with(ZMemoryManager* manager) {
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

      manager->register_callbacks(callbacks);
    }
  };

  virtual void initialize_after_reserve(ZMemoryManager* manager) {
    PlaceholderCallbacks::register_with(manager);
  }

  virtual bool reserve(zaddress_unsafe addr, size_t size) {
    const zaddress_unsafe res = ZMapper::reserve(addr, size);

    assert(res == addr || untype(res) == 0, "Should not reserve other memory than requested");
    return res == addr;
  }

  virtual void unreserve(zaddress_unsafe addr, size_t size) {
    ZMapper::unreserve(addr, size);
  }
};

// Implements Large Pages (locked) support using shared AWE physical memory.

// ZPhysicalMemory layer needs access to the section
HANDLE ZAWESection;

class ZVirtualMemoryManagerLargePages : public ZVirtualMemoryManagerImpl {
private:
  virtual void initialize_before_reserve() {
    ZAWESection = ZMapper::create_shared_awe_section();
  }

  virtual bool reserve(zaddress_unsafe addr, size_t size) {
    const zaddress_unsafe res = ZMapper::reserve_for_shared_awe(ZAWESection, addr, size);

    assert(res == addr || untype(res) == 0, "Should not reserve other memory than requested");
    return res == addr;
  }

  virtual void unreserve(zaddress_unsafe addr, size_t size) {
    ZMapper::unreserve_for_shared_awe(addr, size);
  }
};

static ZVirtualMemoryManagerImpl* _impl = nullptr;

void ZVirtualMemoryManager::pd_initialize_before_reserve() {
  if (ZLargePages::is_enabled()) {
    _impl = new ZVirtualMemoryManagerLargePages();
  } else {
    _impl = new ZVirtualMemoryManagerSmallPages();
  }
  _impl->initialize_before_reserve();
}

void ZVirtualMemoryManager::pd_initialize_after_reserve() {
  _impl->initialize_after_reserve(&_manager);
}

bool ZVirtualMemoryManager::pd_reserve(zaddress_unsafe addr, size_t size) {
  return _impl->reserve(addr, size);
}

void ZVirtualMemoryManager::pd_unreserve(zaddress_unsafe addr, size_t size) {
  _impl->unreserve(addr, size);
}
