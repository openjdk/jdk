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
#include "utilities/debug.hpp"

void ZAdaptiveHeap::pd_machine_memory_info(ZMachineMemoryInfo& info) {
  info._physical_memory = os::Machine::physical_memory();
  info._is_valid = os::Machine::available_memory(info._available_memory);
}

bool ZAdaptiveHeap::pd_machine_elapsed_system_cpu_time(ZSystemCpuTime& value) {
#ifdef __APPLE__
  const int processor_count = os::processor_count();

  if (processor_count <= 0) {
    return false;
  }

  mach_msg_type_number_t count = HOST_CPU_LOAD_INFO_COUNT;
  host_cpu_load_info_data_t load_data;

  kern_return_t ret = host_statistics(mach_host_self(), HOST_CPU_LOAD_INFO, (host_info_t)&load_data, &count);
  if (ret != KERN_SUCCESS) {
    assert(false, "This should never happen");
    return false;
  }

  // The CPU ticks counters are 32-bit and may experience wrapping behaviour.
  natural_t ticks = load_data.cpu_ticks[CPU_STATE_USER] +
                    load_data.cpu_ticks[CPU_STATE_NICE] +
                    load_data.cpu_ticks[CPU_STATE_SYSTEM];

  value._elapsed_time = double(ticks) / CLK_TCK;
  value._processor_count = double(processor_count);
  return true;
#else
  Unimplemented();
#endif
}

bool ZAdaptiveHeap::pd_container_elapsed_system_cpu_time(ZSystemCpuTime& value) {
  ShouldNotReachHere();
}

bool ZAdaptiveHeap::pd_machine_compressed_memory(physical_memory_size_type& value) {
#ifdef __APPLE__
  mach_msg_type_number_t count = HOST_VM_INFO64_COUNT;
  vm_statistics64_data_t vmstat;
  kern_return_t kerr = host_statistics64(mach_host_self(), HOST_VM_INFO64,
                                         (host_info64_t)&vmstat, &count);
  assert(kerr == KERN_SUCCESS,
         "host_statistics64 failed - check mach_host_self() and count");
  if (kerr == KERN_SUCCESS) {
    value = vmstat.compressor_page_count * os::vm_page_size();
    return true;
  }
#endif
  return false;
}
