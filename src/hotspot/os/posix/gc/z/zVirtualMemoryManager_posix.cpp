/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zVirtualMemoryManager.hpp"
#include "logging/log.hpp"
#ifdef LINUX
#include "gc/z/zSyscall_linux.hpp"
#endif

#include <sys/mman.h>

void ZVirtualMemoryReserver::pd_register_callbacks(ZVirtualMemoryRegistry* registry) {
  // Does nothing
}

bool ZVirtualMemoryReserver::pd_reserve(zaddress_unsafe addr, size_t size) {
  const int flags = MAP_ANONYMOUS|MAP_PRIVATE|MAP_NORESERVE LINUX_ONLY(|MAP_FIXED_NOREPLACE);

  void* const res = mmap((void*)untype(addr), size, PROT_NONE, flags, -1, 0);
  if (res == MAP_FAILED) {
    // Failed to reserve memory
    return false;
  }

  if (res != (void*)untype(addr)) {
    // Failed to reserve memory at the requested address
    munmap(res, size);
    return false;
  }

  // Success
  return true;
}

void ZVirtualMemoryReserver::pd_unreserve(zaddress_unsafe addr, size_t size) {
  const int res = munmap((void*)untype(addr), size);
  assert(res == 0, "Failed to unmap memory");
}
