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
#include "gc/z/zErrno.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zInitialize.hpp"
#include "gc/z/zLargePages.inline.hpp"
#include "gc/z/zPhysicalMemoryBacking_bsd.hpp"
#include "logging/log.hpp"
#include "runtime/globals.hpp"
#include "runtime/os.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"

#include <mach/mach.h>
#include <mach/mach_vm.h>
#include <sys/mman.h>
#include <sys/types.h>

// The backing is represented by a reserved virtual address space, in which
// we commit and uncommit physical memory. Multi-mapping the different heap
// views is done by simply remapping the backing memory using mach_vm_remap().

static int vm_flags_superpage() {
  if (!ZLargePages::is_explicit()) {
    return 0;
  }

  const int page_size_in_megabytes = ZGranuleSize >> 20;
  return page_size_in_megabytes << VM_FLAGS_SUPERPAGE_SHIFT;
}

static ZErrno mremap(uintptr_t from_addr, uintptr_t to_addr, size_t size) {
  mach_vm_address_t remap_addr = to_addr;
  vm_prot_t remap_cur_prot;
  vm_prot_t remap_max_prot;

  // Remap memory to an additional location
  const kern_return_t res = mach_vm_remap(mach_task_self(),
                                          &remap_addr,
                                          size,
                                          0 /* mask */,
                                          VM_FLAGS_FIXED | VM_FLAGS_OVERWRITE | vm_flags_superpage(),
                                          mach_task_self(),
                                          from_addr,
                                          FALSE /* copy */,
                                          &remap_cur_prot,
                                          &remap_max_prot,
                                          VM_INHERIT_COPY);

  return (res == KERN_SUCCESS) ? ZErrno(0) : ZErrno(EINVAL);
}

ZPhysicalMemoryBacking::ZPhysicalMemoryBacking(size_t max_capacity)
  : _base(0),
    _initialized(false) {

  // Reserve address space for backing memory
  _base = (uintptr_t)os::reserve_memory(max_capacity, mtJavaHeap);
  if (_base == 0) {
    // Failed
    ZInitialize::error("Failed to reserve address space for backing memory");
    return;
  }

  // Successfully initialized
  _initialized = true;
}

bool ZPhysicalMemoryBacking::is_initialized() const {
  return _initialized;
}

void ZPhysicalMemoryBacking::warn_commit_limits(size_t max_capacity) const {
  // Does nothing
}

bool ZPhysicalMemoryBacking::commit_inner(zbacking_offset offset, size_t length) const {
  assert(is_aligned(untype(offset), os::vm_page_size()), "Invalid offset");
  assert(is_aligned(length, os::vm_page_size()), "Invalid length");

  log_trace(gc, heap)("Committing memory: %zuM-%zuM (%zuM)",
                      untype(offset) / M, untype(to_zbacking_offset_end(offset, length)) / M, length / M);

  const uintptr_t addr = _base + untype(offset);
  const void* const res = mmap((void*)addr, length, PROT_READ | PROT_WRITE, MAP_FIXED | MAP_ANONYMOUS | MAP_PRIVATE, -1, 0);
  if (res == MAP_FAILED) {
    ZErrno err;
    log_error(gc)("Failed to commit memory (%s)", err.to_string());
    return false;
  }

  // Success
  return true;
}

size_t ZPhysicalMemoryBacking::commit(zbacking_offset offset, size_t length, uint32_t /* numa_id - ignored */) const {
  // Try to commit the whole region
  if (commit_inner(offset, length)) {
    // Success
    return length;
  }

  // Failed, try to commit as much as possible
  zbacking_offset start = offset;
  zbacking_offset end = offset + length;

  for (;;) {
    length = align_down((end - start) / 2, ZGranuleSize);
    if (length == 0) {
      // Done, don't commit more
      return start - offset;
    }

    if (commit_inner(start, length)) {
      // Success, try commit more
      start += length;
    } else {
      // Failed, try commit less
      end -= length;
    }
  }
}

size_t ZPhysicalMemoryBacking::uncommit(zbacking_offset offset, size_t length) const {
  assert(is_aligned(untype(offset), os::vm_page_size()), "Invalid offset");
  assert(is_aligned(length, os::vm_page_size()), "Invalid length");

  log_trace(gc, heap)("Uncommitting memory: %zuM-%zuM (%zuM)",
                      untype(offset) / M, untype(to_zbacking_offset_end(offset, length)) / M, length / M);

  const uintptr_t start = _base + untype(offset);
  const void* const res = mmap((void*)start, length, PROT_NONE, MAP_FIXED | MAP_ANONYMOUS | MAP_PRIVATE | MAP_NORESERVE, -1, 0);
  if (res == MAP_FAILED) {
    ZErrno err;
    log_error(gc)("Failed to uncommit memory (%s)", err.to_string());
    return 0;
  }

  return length;
}

void ZPhysicalMemoryBacking::map(zaddress_unsafe addr, size_t size, zbacking_offset offset) const {
  const ZErrno err = mremap(_base + untype(offset), untype(addr), size);
  if (err) {
    fatal("Failed to remap memory (%s)", err.to_string());
  }
}

void ZPhysicalMemoryBacking::unmap(zaddress_unsafe addr, size_t size) const {
  // Note that we must keep the address space reservation intact and just detach
  // the backing memory. For this reason we map a new anonymous, non-accessible
  // and non-reserved page over the mapping instead of actually unmapping.
  const void* const res = mmap((void*)untype(addr), size, PROT_NONE, MAP_FIXED | MAP_ANONYMOUS | MAP_PRIVATE | MAP_NORESERVE, -1, 0);
  if (res == MAP_FAILED) {
    ZErrno err;
    fatal("Failed to map memory (%s)", err.to_string());
  }
}
