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
#include "gc/x/xAddress.inline.hpp"
#include "gc/x/xGlobals.hpp"
#include "gc/x/xLargePages.inline.hpp"
#include "gc/x/xMapper_windows.hpp"
#include "gc/x/xSyscall_windows.hpp"
#include "gc/x/xVirtualMemory.inline.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"

class XVirtualMemoryManagerImpl : public CHeapObj<mtGC> {
public:
  virtual void initialize_before_reserve() {}
  virtual void initialize_after_reserve(XMemoryManager* manager) {}
  virtual bool reserve(uintptr_t addr, size_t size) = 0;
  virtual void unreserve(uintptr_t addr, size_t size) = 0;
};

// Implements small pages (paged) support using placeholder reservation.
class XVirtualMemoryManagerSmallPages : public XVirtualMemoryManagerImpl {
private:
  class PlaceholderCallbacks : public AllStatic {
  public:
    static void split_placeholder(uintptr_t start, size_t size) {
      XMapper::split_placeholder(XAddress::marked0(start), size);
      XMapper::split_placeholder(XAddress::marked1(start), size);
      XMapper::split_placeholder(XAddress::remapped(start), size);
    }

    static void coalesce_placeholders(uintptr_t start, size_t size) {
      XMapper::coalesce_placeholders(XAddress::marked0(start), size);
      XMapper::coalesce_placeholders(XAddress::marked1(start), size);
      XMapper::coalesce_placeholders(XAddress::remapped(start), size);
    }

    static void split_into_placeholder_granules(uintptr_t start, size_t size) {
      for (uintptr_t addr = start; addr < start + size; addr += XGranuleSize) {
        split_placeholder(addr, XGranuleSize);
      }
    }

    static void coalesce_into_one_placeholder(uintptr_t start, size_t size) {
      assert(is_aligned(size, XGranuleSize), "Must be granule aligned");

      if (size > XGranuleSize) {
        coalesce_placeholders(start, size);
      }
    }

    static void create_callback(const XMemory* area) {
      assert(is_aligned(area->size(), XGranuleSize), "Must be granule aligned");
      coalesce_into_one_placeholder(area->start(), area->size());
    }

    static void destroy_callback(const XMemory* area) {
      assert(is_aligned(area->size(), XGranuleSize), "Must be granule aligned");
      // Don't try split the last granule - VirtualFree will fail
      split_into_placeholder_granules(area->start(), area->size() - XGranuleSize);
    }

    static void shrink_from_front_callback(const XMemory* area, size_t size) {
      assert(is_aligned(size, XGranuleSize), "Must be granule aligned");
      split_into_placeholder_granules(area->start(), size);
    }

    static void shrink_from_back_callback(const XMemory* area, size_t size) {
      assert(is_aligned(size, XGranuleSize), "Must be granule aligned");
      // Don't try split the last granule - VirtualFree will fail
      split_into_placeholder_granules(area->end() - size, size - XGranuleSize);
    }

    static void grow_from_front_callback(const XMemory* area, size_t size) {
      assert(is_aligned(area->size(), XGranuleSize), "Must be granule aligned");
      coalesce_into_one_placeholder(area->start() - size, area->size() + size);
    }

    static void grow_from_back_callback(const XMemory* area, size_t size) {
      assert(is_aligned(area->size(), XGranuleSize), "Must be granule aligned");
      coalesce_into_one_placeholder(area->start(), area->size() + size);
    }

    static void register_with(XMemoryManager* manager) {
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
      // split into XGranuleSize sized placeholders.

      XMemoryManager::Callbacks callbacks;

      callbacks._create = &create_callback;
      callbacks._destroy = &destroy_callback;
      callbacks._shrink_from_front = &shrink_from_front_callback;
      callbacks._shrink_from_back = &shrink_from_back_callback;
      callbacks._grow_from_front = &grow_from_front_callback;
      callbacks._grow_from_back = &grow_from_back_callback;

      manager->register_callbacks(callbacks);
    }
  };

  virtual void initialize_after_reserve(XMemoryManager* manager) {
    PlaceholderCallbacks::register_with(manager);
  }

  virtual bool reserve(uintptr_t addr, size_t size) {
    const uintptr_t res = XMapper::reserve(addr, size);

    assert(res == addr || res == 0, "Should not reserve other memory than requested");
    return res == addr;
  }

  virtual void unreserve(uintptr_t addr, size_t size) {
    XMapper::unreserve(addr, size);
  }
};

// Implements Large Pages (locked) support using shared AWE physical memory.

// XPhysicalMemory layer needs access to the section
HANDLE XAWESection;

class XVirtualMemoryManagerLargePages : public XVirtualMemoryManagerImpl {
private:
  virtual void initialize_before_reserve() {
    XAWESection = XMapper::create_shared_awe_section();
  }

  virtual bool reserve(uintptr_t addr, size_t size) {
    const uintptr_t res = XMapper::reserve_for_shared_awe(XAWESection, addr, size);

    assert(res == addr || res == 0, "Should not reserve other memory than requested");
    return res == addr;
  }

  virtual void unreserve(uintptr_t addr, size_t size) {
    XMapper::unreserve_for_shared_awe(addr, size);
  }
};

static XVirtualMemoryManagerImpl* _impl = nullptr;

void XVirtualMemoryManager::pd_initialize_before_reserve() {
  if (XLargePages::is_enabled()) {
    _impl = new XVirtualMemoryManagerLargePages();
  } else {
    _impl = new XVirtualMemoryManagerSmallPages();
  }
  _impl->initialize_before_reserve();
}

void XVirtualMemoryManager::pd_initialize_after_reserve() {
  _impl->initialize_after_reserve(&_manager);
}

bool XVirtualMemoryManager::pd_reserve(uintptr_t addr, size_t size) {
  return _impl->reserve(addr, size);
}

void XVirtualMemoryManager::pd_unreserve(uintptr_t addr, size_t size) {
  _impl->unreserve(addr, size);
}
