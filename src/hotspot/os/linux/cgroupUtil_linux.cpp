/*
 * Copyright (c) 2024, 2025, Red Hat, Inc.
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

#include "cgroupUtil_linux.hpp"
#include "os_linux.hpp"

bool CgroupUtil::processor_count(CgroupCpuController* cpu_ctrl, int upper_bound, double& value) {
  assert(upper_bound > 0, "upper bound of cpus must be positive");
  int quota = -1;
  int period = -1;
  if (!cpu_ctrl->cpu_quota(quota)) {
    return false;
  }
  if (!cpu_ctrl->cpu_period(period)) {
    return false;
  }
  int quota_count = 0;
  double result = upper_bound;

  if (quota > 0 && period > 0) { // Use quotas
    double cpu_quota = static_cast<double>(quota) / period;
    log_trace(os, container)("CPU Quota based on quota/period: %.2f", cpu_quota);
    result = MIN2(result, cpu_quota);
  }

  log_trace(os, container)("OSContainer::active_processor_count: %.2f", result);
  value = result;
  return true;
}

// Get an updated memory limit. The return value is strictly less than or equal to the
// passed in 'lowest' value.
physical_memory_size_type CgroupUtil::get_updated_mem_limit(CgroupMemoryController* mem,
                                                            physical_memory_size_type lowest,
                                                            physical_memory_size_type upper_bound) {
  assert(lowest <= upper_bound, "invariant");
  physical_memory_size_type current_limit = value_unlimited;
  if (mem->read_memory_limit_in_bytes(upper_bound, current_limit) && current_limit != value_unlimited) {
    assert(current_limit <= upper_bound, "invariant");
    if (lowest > current_limit) {
      return current_limit;
    }
  }
  return lowest;
}

// Get an updated cpu limit. The return value is strictly less than or equal to the
// passed in 'lowest' value.
double CgroupUtil::get_updated_cpu_limit(CgroupCpuController* cpu,
                                     int lowest,
                                     int upper_bound) {
  assert(lowest > 0 && lowest <= upper_bound, "invariant");
  double cpu_limit_val = -1;
  if (CgroupUtil::processor_count(cpu, upper_bound, cpu_limit_val) && cpu_limit_val != upper_bound) {
    assert(cpu_limit_val <= upper_bound, "invariant");
    if (lowest > cpu_limit_val) {
      return cpu_limit_val;
    }
  }
  return lowest;
}

void CgroupUtil::adjust_controller(CgroupMemoryController* mem) {
  assert(mem->cgroup_path() != nullptr, "invariant");
  if (strstr(mem->cgroup_path(), "../") != nullptr) {
    log_warning(os, container)("Cgroup memory controller path at '%s' seems to have moved "
                               "to '%s'. Detected limits won't be accurate",
                               mem->mount_point(), mem->cgroup_path());
    mem->set_subsystem_path("/");
    return;
  }
  if (!mem->needs_hierarchy_adjustment()) {
    // nothing to do
    return;
  }
  log_trace(os, container)("Adjusting controller path for memory: %s", mem->subsystem_path());
  char* orig = os::strdup(mem->cgroup_path());
  char* cg_path = os::strdup(orig);
  char* last_slash;
  assert(cg_path[0] == '/', "cgroup path must start with '/'");
  physical_memory_size_type phys_mem = os::Linux::physical_memory();
  char* limit_cg_path = nullptr;
  physical_memory_size_type limit = value_unlimited;
  physical_memory_size_type lowest_limit = phys_mem;
  lowest_limit = get_updated_mem_limit(mem, lowest_limit, phys_mem);
  physical_memory_size_type orig_limit = lowest_limit != phys_mem ? lowest_limit : phys_mem;
  while ((last_slash = strrchr(cg_path, '/')) != cg_path) {
    *last_slash = '\0'; // strip path
    // update to shortened path and try again
    mem->set_subsystem_path(cg_path);
    limit = get_updated_mem_limit(mem, lowest_limit, phys_mem);
    if (limit < lowest_limit) {
      lowest_limit = limit;
      os::free(limit_cg_path); // handles nullptr
      limit_cg_path = os::strdup(cg_path);
    }
  }
  // need to check limit at mount point
  mem->set_subsystem_path("/");
  limit = get_updated_mem_limit(mem, lowest_limit, phys_mem);
  if (limit < lowest_limit) {
    lowest_limit = limit;
    os::free(limit_cg_path); // handles nullptr
    limit_cg_path = os::strdup("/");
  }
  assert(lowest_limit <= phys_mem, "limit must not exceed host memory");
  if (lowest_limit != orig_limit) {
    // we've found a lower limit anywhere in the hierarchy,
    // set the path to the limit path
    assert(limit_cg_path != nullptr, "limit path must be set");
    mem->set_subsystem_path(limit_cg_path);
    log_trace(os, container)("Adjusted controller path for memory to: %s. "
                             "Lowest limit was: " PHYS_MEM_TYPE_FORMAT,
                             mem->subsystem_path(),
                             lowest_limit);
  } else {
    log_trace(os, container)("Lowest limit was: " PHYS_MEM_TYPE_FORMAT, lowest_limit);
    log_trace(os, container)("No lower limit found for memory in hierarchy %s, "
                             "adjusting to original path %s",
                              mem->mount_point(), orig);
    mem->set_subsystem_path(orig);
  }
  os::free(cg_path);
  os::free(orig);
  os::free(limit_cg_path);
}

void CgroupUtil::adjust_controller(CgroupCpuController* cpu) {
  assert(cpu->cgroup_path() != nullptr, "invariant");
  if (strstr(cpu->cgroup_path(), "../") != nullptr) {
    log_warning(os, container)("Cgroup cpu controller path at '%s' seems to have moved "
                               "to '%s'. Detected limits won't be accurate",
                               cpu->mount_point(), cpu->cgroup_path());
    cpu->set_subsystem_path("/");
    return;
  }
  if (!cpu->needs_hierarchy_adjustment()) {
    // nothing to do
    return;
  }
  log_trace(os, container)("Adjusting controller path for cpu: %s", cpu->subsystem_path());
  char* orig = os::strdup(cpu->cgroup_path());
  char* cg_path = os::strdup(orig);
  char* last_slash;
  assert(cg_path[0] == '/', "cgroup path must start with '/'");
  int host_cpus = os::Linux::active_processor_count();
  int lowest_limit = host_cpus;
  double cpus = get_updated_cpu_limit(cpu, lowest_limit, host_cpus);
  int orig_limit = lowest_limit != host_cpus ? lowest_limit : host_cpus;
  char* limit_cg_path = nullptr;
  while ((last_slash = strrchr(cg_path, '/')) != cg_path) {
    *last_slash = '\0'; // strip path
    // update to shortened path and try again
    cpu->set_subsystem_path(cg_path);
    cpus = get_updated_cpu_limit(cpu, lowest_limit, host_cpus);
    if (cpus != host_cpus && cpus < lowest_limit) {
      lowest_limit = cpus;
      os::free(limit_cg_path); // handles nullptr
      limit_cg_path = os::strdup(cg_path);
    }
  }
  // need to check limit at mount point
  cpu->set_subsystem_path("/");
  cpus = get_updated_cpu_limit(cpu, lowest_limit, host_cpus);
  if (cpus != host_cpus && cpus < lowest_limit) {
    lowest_limit = cpus;
    os::free(limit_cg_path); // handles nullptr
    limit_cg_path = os::strdup(cg_path);
  }
  assert(lowest_limit >= 0, "limit must be positive");
  if (lowest_limit != orig_limit) {
    // we've found a lower limit anywhere in the hierarchy,
    // set the path to the limit path
    assert(limit_cg_path != nullptr, "limit path must be set");
    cpu->set_subsystem_path(limit_cg_path);
    log_trace(os, container)("Adjusted controller path for cpu to: %s. "
                             "Lowest limit was: %d",
                             cpu->subsystem_path(), lowest_limit);
  } else {
    log_trace(os, container)("Lowest limit was: %d", lowest_limit);
    log_trace(os, container)("No lower limit found for cpu in hierarchy %s, "
                             "adjusting to original path %s",
                              cpu->mount_point(), orig);
    cpu->set_subsystem_path(orig);
  }
  os::free(cg_path);
  os::free(orig);
  os::free(limit_cg_path);
}
