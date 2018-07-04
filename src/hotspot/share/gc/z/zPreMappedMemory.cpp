/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zPage.inline.hpp"
#include "gc/z/zPhysicalMemory.inline.hpp"
#include "gc/z/zPreMappedMemory.inline.hpp"
#include "gc/z/zVirtualMemory.inline.hpp"
#include "logging/log.hpp"

ZPreMappedMemory::ZPreMappedMemory(ZVirtualMemoryManager &vmm, ZPhysicalMemoryManager &pmm, size_t size) :
    _vmem(),
    _pmem(),
    _initialized(false) {
  if (!vmm.is_initialized() || !pmm.is_initialized()) {
    // Not initialized
    return;
  }

  // Pre-mapping and pre-touching memory can take a long time. Log a message
  // to help the user understand why the JVM might seem slow to start.
  log_info(gc, init)("Pre-touching: %s", AlwaysPreTouch ? "Enabled" : "Disabled");
  log_info(gc, init)("Pre-mapping: " SIZE_FORMAT "M", size / M);

  if (size > 0) {
    _pmem = pmm.alloc(size);
    if (_pmem.is_null()) {
      // Out of memory
      log_error(gc, init)("Failed to pre-map Java heap (Cannot allocate physical memory)");
      return;
    }

    _vmem = vmm.alloc(size, true /* alloc_from_front */);
    if (_vmem.is_null()) {
      // Out of address space
      log_error(gc, init)("Failed to pre-map Java heap (Cannot allocate virtual memory)");
      pmm.free(_pmem);
      return;
    }

    // Map physical memory
    pmm.map(_pmem, _vmem.start());
  }

  _initialized = true;
}

ZPage* ZPreMappedMemory::alloc_page(uint8_t type, size_t size) {
  if (size > available()) {
    // Not enough pre-mapped memory
    return NULL;
  }

  // Take a chunk of the pre-mapped memory
  const ZPhysicalMemory pmem = _pmem.split(size);
  const ZVirtualMemory  vmem = _vmem.split(size);

  ZPage* const page = new ZPage(type, vmem, pmem);
  page->set_pre_mapped();

  return page;
}

void ZPreMappedMemory::clear() {
  assert(_pmem.is_null(), "Should be detached");
  _vmem.clear();
}
