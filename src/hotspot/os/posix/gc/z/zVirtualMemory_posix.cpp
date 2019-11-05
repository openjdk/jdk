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
#include "gc/z/zVirtualMemory.hpp"
#include "logging/log.hpp"

#include <sys/mman.h>
#include <sys/types.h>

void ZVirtualMemoryManager::initialize_os() {
  // Does nothing
}

static void unmap(uintptr_t start, size_t size) {
  const int res = munmap((void*)start, size);
  assert(res == 0, "Failed to unmap memory");
}

static bool map(uintptr_t start, size_t size) {
  const void* const res = mmap((void*)start, size, PROT_NONE, MAP_ANONYMOUS|MAP_PRIVATE|MAP_NORESERVE, -1, 0);
  if (res == MAP_FAILED) {
    // Failed to reserve memory
    return false;
  }

  if ((uintptr_t)res != start) {
    // Failed to reserve memory at the requested address
    unmap((uintptr_t)res, size);
    return false;
  }

  // Success
  return true;
}

bool ZVirtualMemoryManager::reserve_contiguous_platform(uintptr_t start, size_t size) {
  // Reserve address views
  const uintptr_t marked0 = ZAddress::marked0(start);
  const uintptr_t marked1 = ZAddress::marked1(start);
  const uintptr_t remapped = ZAddress::remapped(start);

  if (!map(marked0, size)) {
    return false;
  }

  if (!map(marked1, size)) {
    unmap(marked0, size);
    return false;
  }

  if (!map(remapped, size)) {
    unmap(marked0, size);
    unmap(marked1, size);
    return false;
  }

  // Register address views with native memory tracker
  nmt_reserve(marked0, size);
  nmt_reserve(marked1, size);
  nmt_reserve(remapped, size);

  return true;
}
