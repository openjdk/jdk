/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/z/zCPU.inline.hpp"
#include "gc/z/zErrno.hpp"
#include "gc/z/zNUMA.hpp"
#include "gc/z/zNUMA.inline.hpp"
#include "gc/z/zSyscall_linux.hpp"
#include "runtime/globals.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/os.hpp"
#include "utilities/debug.hpp"

static bool numa_memory_id(void* addr, uint32_t* id) {
  return ZSyscall::get_mempolicy((int*)id, NULL, 0, addr, MPOL_F_NODE | MPOL_F_ADDR) != -1;
}

static bool is_numa_supported() {
  // Test if syscall is available
  uint32_t dummy = 0;
  const bool available = numa_memory_id(&dummy, &dummy);

  if (!available && !FLAG_IS_DEFAULT(UseNUMA)) {
    warning("NUMA support disabled, system call get_mempolicy not available");
  }

  return available;
}

void ZNUMA::pd_initialize() {
  if (!UseNUMA) {
    _state = Disabled;
  } else {
    _state = is_numa_supported() ? Enabled : Unsupported;
  }
}

uint32_t ZNUMA::count() {
  if (!is_enabled()) {
    // NUMA support not enabled
    return 1;
  }

  return os::Linux::numa_max_node() + 1;
}

uint32_t ZNUMA::id() {
  if (!is_enabled()) {
    // NUMA support not enabled
    return 0;
  }

  return os::Linux::get_node_by_cpu(ZCPU::id());
}

uint32_t ZNUMA::memory_id(uintptr_t addr) {
  if (!is_enabled()) {
    // NUMA support not enabled, assume everything belongs to node zero
    return 0;
  }

  uint32_t id = (uint32_t)-1;

  if (!numa_memory_id((void*)addr, &id)) {
    ZErrno err;
    fatal("Failed to get NUMA id for memory at " PTR_FORMAT " (%s)", addr, err.to_string());
  }

  assert(id < count(), "Invalid NUMA id");

  return id;
}
