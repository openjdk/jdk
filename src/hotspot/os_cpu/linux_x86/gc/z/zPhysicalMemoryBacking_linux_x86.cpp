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
#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zBackingFile_linux_x86.hpp"
#include "gc/z/zErrno.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zLargePages.inline.hpp"
#include "gc/z/zMemory.hpp"
#include "gc/z/zNUMA.hpp"
#include "gc/z/zPhysicalMemory.inline.hpp"
#include "gc/z/zPhysicalMemoryBacking_linux_x86.hpp"
#include "logging/log.hpp"
#include "runtime/init.hpp"
#include "runtime/os.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"

#include <stdio.h>
#include <sys/mman.h>
#include <sys/types.h>

//
// Support for building on older Linux systems
//

// madvise(2) flags
#ifndef MADV_HUGEPAGE
#define MADV_HUGEPAGE                        14
#endif

// Proc file entry for max map mount
#define ZFILENAME_PROC_MAX_MAP_COUNT         "/proc/sys/vm/max_map_count"

bool ZPhysicalMemoryBacking::is_initialized() const {
  return _file.is_initialized();
}

void ZPhysicalMemoryBacking::warn_available_space(size_t max) const {
  // Note that the available space on a tmpfs or a hugetlbfs filesystem
  // will be zero if no size limit was specified when it was mounted.
  const size_t available = _file.available();
  if (available == 0) {
    // No size limit set, skip check
    log_info(gc, init)("Available space on backing filesystem: N/A");
    return;
  }

  log_info(gc, init)("Available space on backing filesystem: " SIZE_FORMAT "M", available / M);

  // Warn if the filesystem doesn't currently have enough space available to hold
  // the max heap size. The max heap size will be capped if we later hit this limit
  // when trying to expand the heap.
  if (available < max) {
    log_warning(gc)("***** WARNING! INCORRECT SYSTEM CONFIGURATION DETECTED! *****");
    log_warning(gc)("Not enough space available on the backing filesystem to hold the current max Java heap");
    log_warning(gc)("size (" SIZE_FORMAT "M). Please adjust the size of the backing filesystem accordingly "
                    "(available", max / M);
    log_warning(gc)("space is currently " SIZE_FORMAT "M). Continuing execution with the current filesystem "
                    "size could", available / M);
    log_warning(gc)("lead to a premature OutOfMemoryError being thrown, due to failure to map memory.");
  }
}

void ZPhysicalMemoryBacking::warn_max_map_count(size_t max) const {
  const char* const filename = ZFILENAME_PROC_MAX_MAP_COUNT;
  FILE* const file = fopen(filename, "r");
  if (file == NULL) {
    // Failed to open file, skip check
    log_debug(gc, init)("Failed to open %s", filename);
    return;
  }

  size_t actual_max_map_count = 0;
  const int result = fscanf(file, SIZE_FORMAT, &actual_max_map_count);
  fclose(file);
  if (result != 1) {
    // Failed to read file, skip check
    log_debug(gc, init)("Failed to read %s", filename);
    return;
  }

  // The required max map count is impossible to calculate exactly since subsystems
  // other than ZGC are also creating memory mappings, and we have no control over that.
  // However, ZGC tends to create the most mappings and dominate the total count.
  // In the worst cases, ZGC will map each granule three times, i.e. once per heap view.
  // We speculate that we need another 20% to allow for non-ZGC subsystems to map memory.
  const size_t required_max_map_count = (max / ZGranuleSize) * 3 * 1.2;
  if (actual_max_map_count < required_max_map_count) {
    log_warning(gc)("***** WARNING! INCORRECT SYSTEM CONFIGURATION DETECTED! *****");
    log_warning(gc)("The system limit on number of memory mappings per process might be too low for the given");
    log_warning(gc)("max Java heap size (" SIZE_FORMAT "M). Please adjust %s to allow for at",
                    max / M, filename);
    log_warning(gc)("least " SIZE_FORMAT " mappings (current limit is " SIZE_FORMAT "). Continuing execution "
                    "with the current", required_max_map_count, actual_max_map_count);
    log_warning(gc)("limit could lead to a fatal error, due to failure to map memory.");
  }
}

void ZPhysicalMemoryBacking::warn_commit_limits(size_t max) const {
  // Warn if available space is too low
  warn_available_space(max);

  // Warn if max map count is too low
  warn_max_map_count(max);
}

bool ZPhysicalMemoryBacking::supports_uncommit() {
  assert(!is_init_completed(), "Invalid state");
  assert(_file.size() >= ZGranuleSize, "Invalid size");

  // Test if uncommit is supported by uncommitting and then re-committing a granule
  return commit(uncommit(ZGranuleSize)) == ZGranuleSize;
}

size_t ZPhysicalMemoryBacking::commit(size_t size) {
  size_t committed = 0;

  // Fill holes in the backing file
  while (committed < size) {
    size_t allocated = 0;
    const size_t remaining = size - committed;
    const uintptr_t start = _uncommitted.alloc_from_front_at_most(remaining, &allocated);
    if (start == UINTPTR_MAX) {
      // No holes to commit
      break;
    }

    // Try commit hole
    const size_t filled = _file.commit(start, allocated);
    if (filled > 0) {
      // Successful or partialy successful
      _committed.free(start, filled);
      committed += filled;
    }
    if (filled < allocated) {
      // Failed or partialy failed
      _uncommitted.free(start + filled, allocated - filled);
      return committed;
    }
  }

  // Expand backing file
  if (committed < size) {
    const size_t remaining = size - committed;
    const uintptr_t start = _file.size();
    const size_t expanded = _file.commit(start, remaining);
    if (expanded > 0) {
      // Successful or partialy successful
      _committed.free(start, expanded);
      committed += expanded;
    }
  }

  return committed;
}

size_t ZPhysicalMemoryBacking::uncommit(size_t size) {
  size_t uncommitted = 0;

  // Punch holes in backing file
  while (uncommitted < size) {
    size_t allocated = 0;
    const size_t remaining = size - uncommitted;
    const uintptr_t start = _committed.alloc_from_back_at_most(remaining, &allocated);
    assert(start != UINTPTR_MAX, "Allocation should never fail");

    // Try punch hole
    const size_t punched = _file.uncommit(start, allocated);
    if (punched > 0) {
      // Successful or partialy successful
      _uncommitted.free(start, punched);
      uncommitted += punched;
    }
    if (punched < allocated) {
      // Failed or partialy failed
      _committed.free(start + punched, allocated - punched);
      return uncommitted;
    }
  }

  return uncommitted;
}

ZPhysicalMemory ZPhysicalMemoryBacking::alloc(size_t size) {
  assert(is_aligned(size, ZGranuleSize), "Invalid size");

  ZPhysicalMemory pmem;

  // Allocate segments
  for (size_t allocated = 0; allocated < size; allocated += ZGranuleSize) {
    const uintptr_t start = _committed.alloc_from_front(ZGranuleSize);
    assert(start != UINTPTR_MAX, "Allocation should never fail");
    pmem.add_segment(ZPhysicalMemorySegment(start, ZGranuleSize));
  }

  return pmem;
}

void ZPhysicalMemoryBacking::free(const ZPhysicalMemory& pmem) {
  const size_t nsegments = pmem.nsegments();

  // Free segments
  for (size_t i = 0; i < nsegments; i++) {
    const ZPhysicalMemorySegment& segment = pmem.segment(i);
    _committed.free(segment.start(), segment.size());
  }
}

void ZPhysicalMemoryBacking::map_failed(ZErrno err) const {
  if (err == ENOMEM) {
    fatal("Failed to map memory. Please check the system limit on number of "
          "memory mappings allowed per process (see %s)", ZFILENAME_PROC_MAX_MAP_COUNT);
  } else {
    fatal("Failed to map memory (%s)", err.to_string());
  }
}

void ZPhysicalMemoryBacking::advise_view(uintptr_t addr, size_t size, int advice) const {
  if (madvise((void*)addr, size, advice) == -1) {
    ZErrno err;
    log_error(gc)("Failed to advise on memory (advice %d, %s)", advice, err.to_string());
  }
}

void ZPhysicalMemoryBacking::pretouch_view(uintptr_t addr, size_t size) const {
  const size_t page_size = ZLargePages::is_explicit() ? os::large_page_size() : os::vm_page_size();
  os::pretouch_memory((void*)addr, (void*)(addr + size), page_size);
}

void ZPhysicalMemoryBacking::map_view(const ZPhysicalMemory& pmem, uintptr_t addr, bool pretouch) const {
  const size_t nsegments = pmem.nsegments();
  size_t size = 0;

  // Map segments
  for (size_t i = 0; i < nsegments; i++) {
    const ZPhysicalMemorySegment& segment = pmem.segment(i);
    const uintptr_t segment_addr = addr + size;
    const void* const res = mmap((void*)segment_addr, segment.size(), PROT_READ|PROT_WRITE, MAP_FIXED|MAP_SHARED, _file.fd(), segment.start());
    if (res == MAP_FAILED) {
      ZErrno err;
      map_failed(err);
    }

    size += segment.size();
  }

  // Advise on use of transparent huge pages before touching it
  if (ZLargePages::is_transparent()) {
    advise_view(addr, size, MADV_HUGEPAGE);
  }

  // NUMA interleave memory before touching it
  ZNUMA::memory_interleave(addr, size);

  // Pre-touch memory
  if (pretouch) {
    pretouch_view(addr, size);
  }
}

void ZPhysicalMemoryBacking::unmap_view(const ZPhysicalMemory& pmem, uintptr_t addr) const {
  // Note that we must keep the address space reservation intact and just detach
  // the backing memory. For this reason we map a new anonymous, non-accessible
  // and non-reserved page over the mapping instead of actually unmapping.
  const void* const res = mmap((void*)addr, pmem.size(), PROT_NONE, MAP_FIXED|MAP_ANONYMOUS|MAP_PRIVATE|MAP_NORESERVE, -1, 0);
  if (res == MAP_FAILED) {
    ZErrno err;
    map_failed(err);
  }
}

uintptr_t ZPhysicalMemoryBacking::nmt_address(uintptr_t offset) const {
  // From an NMT point of view we treat the first heap view (marked0) as committed
  return ZAddress::marked0(offset);
}

void ZPhysicalMemoryBacking::map(const ZPhysicalMemory& pmem, uintptr_t offset) const {
  if (ZVerifyViews) {
    // Map good view
    map_view(pmem, ZAddress::good(offset), AlwaysPreTouch);
  } else {
    // Map all views
    map_view(pmem, ZAddress::marked0(offset), AlwaysPreTouch);
    map_view(pmem, ZAddress::marked1(offset), AlwaysPreTouch);
    map_view(pmem, ZAddress::remapped(offset), AlwaysPreTouch);
  }
}

void ZPhysicalMemoryBacking::unmap(const ZPhysicalMemory& pmem, uintptr_t offset) const {
  if (ZVerifyViews) {
    // Unmap good view
    unmap_view(pmem, ZAddress::good(offset));
  } else {
    // Unmap all views
    unmap_view(pmem, ZAddress::marked0(offset));
    unmap_view(pmem, ZAddress::marked1(offset));
    unmap_view(pmem, ZAddress::remapped(offset));
  }
}

void ZPhysicalMemoryBacking::debug_map(const ZPhysicalMemory& pmem, uintptr_t offset) const {
  // Map good view
  assert(ZVerifyViews, "Should be enabled");
  map_view(pmem, ZAddress::good(offset), false /* pretouch */);
}

void ZPhysicalMemoryBacking::debug_unmap(const ZPhysicalMemory& pmem, uintptr_t offset) const {
  // Unmap good view
  assert(ZVerifyViews, "Should be enabled");
  unmap_view(pmem, ZAddress::good(offset));
}
