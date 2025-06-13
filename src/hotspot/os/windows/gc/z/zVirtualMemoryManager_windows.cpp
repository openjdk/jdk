/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zLargePages.inline.hpp"
#include "gc/z/zMapper_windows.hpp"
#include "gc/z/zSyscall_windows.hpp"
#include "gc/z/zValue.inline.hpp"
#include "gc/z/zVirtualMemory.inline.hpp"
#include "gc/z/zVirtualMemoryManager.inline.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"

class ZVirtualMemoryReserverImpl : public CHeapObj<mtGC> {
public:
  virtual void register_callbacks(ZVirtualMemoryRegistry* registry) {}
  virtual bool reserve(zaddress_unsafe addr, size_t size) = 0;
  virtual void unreserve(zaddress_unsafe addr, size_t size) = 0;
};

// Implements small pages (paged) support using placeholder reservation.
//
// When a memory area is available (kept by the virtual memory manager) a
// single placeholder is covering that memory area. When memory is
// removed from the registry the placeholder is split into granule
// sized placeholders to allow mapping operations on that granularity.
class ZVirtualMemoryReserverSmallPages : public ZVirtualMemoryReserverImpl {
private:
  class PlaceholderCallbacks : public AllStatic {
  private:
    static void split_placeholder(zoffset start, size_t size) {
      ZMapper::split_placeholder(ZOffset::address_unsafe(start), size);
    }

    static void coalesce_placeholders(zoffset start, size_t size) {
      ZMapper::coalesce_placeholders(ZOffset::address_unsafe(start), size);
    }

    // Turn the single placeholder covering the memory area into granule
    // sized placeholders.
    static void split_into_granule_sized_placeholders(zoffset start, size_t size) {
      assert(size >= ZGranuleSize, "Must be at least one granule");
      assert(is_aligned(size, ZGranuleSize), "Must be granule aligned");

      // Don't call split_placeholder on the last granule, since it is already
      // a placeholder and the system call would therefore fail.
      const size_t limit = size - ZGranuleSize;
      for (size_t offset = 0; offset < limit; offset += ZGranuleSize) {
        split_placeholder(start + offset, ZGranuleSize);
      }
    }

    static void coalesce_into_one_placeholder(zoffset start, size_t size) {
      assert(is_aligned(size, ZGranuleSize), "Must be granule aligned");

      // Granule sized areas are already covered by a single placeholder
      if (size > ZGranuleSize) {
        coalesce_placeholders(start, size);
      }
    }

    // Callback implementations

    // Called when a memory area is going to be handed out to be used.
    //
    // Splits the memory area into granule-sized placeholders.
    static void prepare_for_hand_out_callback(const ZVirtualMemory& area) {
      assert(is_aligned(area.size(), ZGranuleSize), "Must be granule aligned");

      split_into_granule_sized_placeholders(area.start(), area.size());
    }

    // Called when a memory area is handed back to the memory manager.
    //
    // Combines the granule-sized placeholders into one placeholder.
    static void prepare_for_hand_back_callback(const ZVirtualMemory& area) {
      assert(is_aligned(area.size(), ZGranuleSize), "Must be granule aligned");

      coalesce_into_one_placeholder(area.start(), area.size());
    }

    // Called when inserting a memory area and it can be merged with an
    // existing, adjacent memory area.
    //
    // Coalesces the underlying placeholders into one.
    static void grow_callback(const ZVirtualMemory& from, const ZVirtualMemory& to) {
      assert(is_aligned(from.size(), ZGranuleSize), "Must be granule aligned");
      assert(is_aligned(to.size(), ZGranuleSize), "Must be granule aligned");
      assert(from != to, "Must have grown");
      assert(to.contains(from), "Must be within");

      coalesce_into_one_placeholder(to.start(), to.size());
    }

    // Called when a memory area is removed from the front or back of an existing
    // memory area.
    //
    // Splits the memory into two placeholders.
    static void shrink_callback(const ZVirtualMemory& from, const ZVirtualMemory& to) {
      assert(is_aligned(from.size(), ZGranuleSize), "Must be granule aligned");
      assert(is_aligned(to.size(), ZGranuleSize), "Must be granule aligned");
      assert(from != to, "Must have shrunk");
      assert(from.contains(to), "Must be larger than what we try to split out");
      assert(from.start() == to.start() || from.end() == to.end(),
             "Only verified to work if we split a placeholder into two placeholders");

      // Split the area into two placeholders
      split_placeholder(to.start(), to.size());
    }

  public:
    static ZVirtualMemoryRegistry::Callbacks callbacks() {
      // Each reserved virtual memory address area registered in _manager is
      // exactly covered by a single placeholder. Callbacks are installed so
      // that whenever a memory area changes, the corresponding placeholder
      // is adjusted.
      //
      // The prepare_for_hand_out callback is called when virtual memory is
      // handed out to callers. The memory area is split into granule-sized
      // placeholders.
      //
      // The prepare_for_hand_back callback is called when previously handed
      // out virtual memory is handed back  to the memory manager. The
      // returned memory area is then covered by a new single placeholder.
      //
      // The grow callback is called when a virtual memory area grows. The
      // resulting memory area is then covered by a single placeholder.
      //
      // The shrink callback is called when a virtual memory area is split into
      // two parts. The two resulting memory areas are then covered by two
      // separate placeholders.
      //
      // See comment in zMapper_windows.cpp explaining why placeholders are
      // split into ZGranuleSize sized placeholders.

      ZVirtualMemoryRegistry::Callbacks callbacks;

      callbacks._prepare_for_hand_out = &prepare_for_hand_out_callback;
      callbacks._prepare_for_hand_back = &prepare_for_hand_back_callback;
      callbacks._grow = &grow_callback;
      callbacks._shrink = &shrink_callback;

      return callbacks;
    }
  };

  virtual void register_callbacks(ZVirtualMemoryRegistry* registry) {
    registry->register_callbacks(PlaceholderCallbacks::callbacks());
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

class ZVirtualMemoryReserverLargePages : public ZVirtualMemoryReserverImpl {
private:
  virtual bool reserve(zaddress_unsafe addr, size_t size) {
    const zaddress_unsafe res = ZMapper::reserve_for_shared_awe(ZAWESection, addr, size);

    assert(res == addr || untype(res) == 0, "Should not reserve other memory than requested");
    return res == addr;
  }

  virtual void unreserve(zaddress_unsafe addr, size_t size) {
    ZMapper::unreserve_for_shared_awe(addr, size);
  }

public:
  ZVirtualMemoryReserverLargePages() {
    ZAWESection = ZMapper::create_shared_awe_section();
  }
};

static ZVirtualMemoryReserverImpl* _impl = nullptr;

void ZVirtualMemoryReserverImpl_initialize() {
  assert(_impl == nullptr, "Should only initialize once");

  if (ZLargePages::is_enabled()) {
    _impl = new ZVirtualMemoryReserverLargePages();
  } else {
    _impl = new ZVirtualMemoryReserverSmallPages();
  }
}

void ZVirtualMemoryReserver::pd_register_callbacks(ZVirtualMemoryRegistry* registry) {
  _impl->register_callbacks(registry);
}

bool ZVirtualMemoryReserver::pd_reserve(zaddress_unsafe addr, size_t size) {
  return _impl->reserve(addr, size);
}

void ZVirtualMemoryReserver::pd_unreserve(zaddress_unsafe addr, size_t size) {
  _impl->unreserve(addr, size);
}
