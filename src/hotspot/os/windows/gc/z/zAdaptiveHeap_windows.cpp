/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/z/zAdaptiveHeap.hpp"
#include "runtime/os.inline.hpp"

#include <windows.h>
#include <processthreadsapi.h>

void ZAdaptiveHeap::pd_machine_memory_info(ZMachineMemoryInfo& info) {
  info._physical_memory = os::Machine::physical_memory();
  info._is_valid = os::Machine::available_memory(info._available_memory);
}

bool ZAdaptiveHeap::pd_machine_elapsed_system_cpu_time(ZSystemCpuTime& value) {
  const int processor_count = os::processor_count();

  if (processor_count <= 0 || processor_count > 64) {
    // GetSystemTimes is not accurate on systems with more than 64 cores.
    return false;
  }

  FILETIME idle, kernel, user;
  if (GetSystemTimes(&idle, &kernel, &user) == 0) {
    assert(false, "this should not fail");
    return false;
  }

  // Kernel time includes idle time
  jlong ticks = jlong_from(user.dwHighDateTime, user.dwLowDateTime) +
                jlong_from(kernel.dwHighDateTime, kernel.dwLowDateTime) -
                jlong_from(idle.dwHighDateTime, idle.dwLowDateTime);

  // Ticks are 100 ns
  value._elapsed_time = double(ticks) / 1e7;
  value._processor_count = double(processor_count);
  return true;
}

bool ZAdaptiveHeap::pd_container_elapsed_system_cpu_time(ZSystemCpuTime& value) {
  ShouldNotReachHere();
}

bool ZAdaptiveHeap::pd_machine_compressed_memory(physical_memory_size_type& value) {
  return false;
}
