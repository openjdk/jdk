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
#include "gc/x/xGlobals.hpp"
#include "gc/x/xGranuleMap.inline.hpp"
#include "gc/x/xLargePages.inline.hpp"
#include "gc/x/xMapper_windows.hpp"
#include "gc/x/xPhysicalMemoryBacking_windows.hpp"
#include "logging/log.hpp"
#include "runtime/globals.hpp"
#include "utilities/debug.hpp"

class XPhysicalMemoryBackingImpl : public CHeapObj<mtGC> {
public:
  virtual size_t commit(size_t offset, size_t size) = 0;
  virtual size_t uncommit(size_t offset, size_t size) = 0;
  virtual void map(uintptr_t addr, size_t size, size_t offset) const = 0;
  virtual void unmap(uintptr_t addr, size_t size) const = 0;
};

// Implements small pages (paged) support using placeholder reservation.
//
// The backing commits and uncommits physical memory, that can be
// multi-mapped into the virtual address space. To support fine-graned
// committing and uncommitting, each XGranuleSize'd chunk is mapped to
// a separate paging file mapping.

class XPhysicalMemoryBackingSmallPages : public XPhysicalMemoryBackingImpl {
private:
  XGranuleMap<HANDLE> _handles;

  HANDLE get_handle(uintptr_t offset) const {
    HANDLE const handle = _handles.get(offset);
    assert(handle != 0, "Should be set");
    return handle;
  }

  void put_handle(uintptr_t offset, HANDLE handle) {
    assert(handle != INVALID_HANDLE_VALUE, "Invalid handle");
    assert(_handles.get(offset) == 0, "Should be cleared");
    _handles.put(offset, handle);
  }

  void clear_handle(uintptr_t offset) {
    assert(_handles.get(offset) != 0, "Should be set");
    _handles.put(offset, 0);
  }

public:
  XPhysicalMemoryBackingSmallPages(size_t max_capacity) :
      XPhysicalMemoryBackingImpl(),
      _handles(max_capacity) {}

  size_t commit(size_t offset, size_t size) {
    for (size_t i = 0; i < size; i += XGranuleSize) {
      HANDLE const handle = XMapper::create_and_commit_paging_file_mapping(XGranuleSize);
      if (handle == 0) {
        return i;
      }

      put_handle(offset + i, handle);
    }

    return size;
  }

  size_t uncommit(size_t offset, size_t size) {
    for (size_t i = 0; i < size; i += XGranuleSize) {
      HANDLE const handle = get_handle(offset + i);
      clear_handle(offset + i);
      XMapper::close_paging_file_mapping(handle);
    }

    return size;
  }

  void map(uintptr_t addr, size_t size, size_t offset) const {
    assert(is_aligned(offset, XGranuleSize), "Misaligned");
    assert(is_aligned(addr, XGranuleSize), "Misaligned");
    assert(is_aligned(size, XGranuleSize), "Misaligned");

    for (size_t i = 0; i < size; i += XGranuleSize) {
      HANDLE const handle = get_handle(offset + i);
      XMapper::map_view_replace_placeholder(handle, 0 /* offset */, addr + i, XGranuleSize);
    }
  }

  void unmap(uintptr_t addr, size_t size) const {
    assert(is_aligned(addr, XGranuleSize), "Misaligned");
    assert(is_aligned(size, XGranuleSize), "Misaligned");

    for (size_t i = 0; i < size; i += XGranuleSize) {
      XMapper::unmap_view_preserve_placeholder(addr + i, XGranuleSize);
    }
  }
};

// Implements Large Pages (locked) support using shared AWE physical memory.
//
// Shared AWE physical memory also works with small pages, but it has
// a few drawbacks that makes it a no-go to use it at this point:
//
// 1) It seems to use 8 bytes of committed memory per *reserved* memory.
// Given our scheme to use a large address space range this turns out to
// use too much memory.
//
// 2) It requires memory locking privileges, even for small pages. This
// has always been a requirement for large pages, and would be an extra
// restriction for usage with small pages.
//
// Note: The large pages size is tied to our XGranuleSize.

extern HANDLE XAWESection;

class XPhysicalMemoryBackingLargePages : public XPhysicalMemoryBackingImpl {
private:
  ULONG_PTR* const _page_array;

  static ULONG_PTR* alloc_page_array(size_t max_capacity) {
    const size_t npages = max_capacity / XGranuleSize;
    const size_t array_size = npages * sizeof(ULONG_PTR);

    return (ULONG_PTR*)os::malloc(array_size, mtGC);
  }

public:
  XPhysicalMemoryBackingLargePages(size_t max_capacity) :
      XPhysicalMemoryBackingImpl(),
      _page_array(alloc_page_array(max_capacity)) {}

  size_t commit(size_t offset, size_t size) {
    const size_t index = offset >> XGranuleSizeShift;
    const size_t npages = size >> XGranuleSizeShift;

    size_t npages_res = npages;
    const bool res = AllocateUserPhysicalPages(XAWESection, &npages_res, &_page_array[index]);
    if (!res) {
      fatal("Failed to allocate physical memory " SIZE_FORMAT "M @ " PTR_FORMAT " (%d)",
            size / M, offset, GetLastError());
    } else {
      log_debug(gc)("Allocated physical memory: " SIZE_FORMAT "M @ " PTR_FORMAT, size / M, offset);
    }

    // AllocateUserPhysicalPages might not be able to allocate the requested amount of memory.
    // The allocated number of pages are written in npages_res.
    return npages_res << XGranuleSizeShift;
  }

  size_t uncommit(size_t offset, size_t size) {
    const size_t index = offset >> XGranuleSizeShift;
    const size_t npages = size >> XGranuleSizeShift;

    size_t npages_res = npages;
    const bool res = FreeUserPhysicalPages(XAWESection, &npages_res, &_page_array[index]);
    if (!res) {
      fatal("Failed to uncommit physical memory " SIZE_FORMAT "M @ " PTR_FORMAT " (%d)",
            size, offset, GetLastError());
    }

    return npages_res << XGranuleSizeShift;
  }

  void map(uintptr_t addr, size_t size, size_t offset) const {
    const size_t npages = size >> XGranuleSizeShift;
    const size_t index = offset >> XGranuleSizeShift;

    const bool res = MapUserPhysicalPages((char*)addr, npages, &_page_array[index]);
    if (!res) {
      fatal("Failed to map view " PTR_FORMAT " " SIZE_FORMAT "M @ " PTR_FORMAT " (%d)",
            addr, size / M, offset, GetLastError());
    }
  }

  void unmap(uintptr_t addr, size_t size) const {
    const size_t npages = size >> XGranuleSizeShift;

    const bool res = MapUserPhysicalPages((char*)addr, npages, nullptr);
    if (!res) {
      fatal("Failed to unmap view " PTR_FORMAT " " SIZE_FORMAT "M (%d)",
            addr, size / M, GetLastError());
    }
  }
};

static XPhysicalMemoryBackingImpl* select_impl(size_t max_capacity) {
  if (XLargePages::is_enabled()) {
    return new XPhysicalMemoryBackingLargePages(max_capacity);
  }

  return new XPhysicalMemoryBackingSmallPages(max_capacity);
}

XPhysicalMemoryBacking::XPhysicalMemoryBacking(size_t max_capacity) :
    _impl(select_impl(max_capacity)) {}

bool XPhysicalMemoryBacking::is_initialized() const {
  return true;
}

void XPhysicalMemoryBacking::warn_commit_limits(size_t max_capacity) const {
  // Does nothing
}

size_t XPhysicalMemoryBacking::commit(size_t offset, size_t length) {
  log_trace(gc, heap)("Committing memory: " SIZE_FORMAT "M-" SIZE_FORMAT "M (" SIZE_FORMAT "M)",
                      offset / M, (offset + length) / M, length / M);

  return _impl->commit(offset, length);
}

size_t XPhysicalMemoryBacking::uncommit(size_t offset, size_t length) {
  log_trace(gc, heap)("Uncommitting memory: " SIZE_FORMAT "M-" SIZE_FORMAT "M (" SIZE_FORMAT "M)",
                      offset / M, (offset + length) / M, length / M);

  return _impl->uncommit(offset, length);
}

void XPhysicalMemoryBacking::map(uintptr_t addr, size_t size, size_t offset) const {
  assert(is_aligned(offset, XGranuleSize), "Misaligned: " PTR_FORMAT, offset);
  assert(is_aligned(addr, XGranuleSize), "Misaligned: " PTR_FORMAT, addr);
  assert(is_aligned(size, XGranuleSize), "Misaligned: " PTR_FORMAT, size);

  _impl->map(addr, size, offset);
}

void XPhysicalMemoryBacking::unmap(uintptr_t addr, size_t size) const {
  assert(is_aligned(addr, XGranuleSize), "Misaligned");
  assert(is_aligned(size, XGranuleSize), "Misaligned");

  _impl->unmap(addr, size);
}
