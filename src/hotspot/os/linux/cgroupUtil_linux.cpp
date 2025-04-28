/*
 * Copyright (c) 2024, 2025, Red Hat, Inc.
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

#include "os_linux.hpp"
#include "cgroupUtil_linux.hpp"

int CgroupUtil::processor_count(CgroupCpuController* cpu_ctrl, int host_cpus) {
  assert(host_cpus > 0, "physical host cpus must be positive");
  int limit_count = host_cpus;
  int quota  = cpu_ctrl->cpu_quota();
  int period = cpu_ctrl->cpu_period();
  int quota_count = 0;
  int result = 0;

  if (quota > -1 && period > 0) {
    quota_count = ceilf((float)quota / (float)period);
    log_trace(os, container)("CPU Quota count based on quota/period: %d", quota_count);
  }

  // Use quotas
  if (quota_count != 0) {
    limit_count = quota_count;
  }

  result = MIN2(host_cpus, limit_count);
  log_trace(os, container)("OSContainer::active_processor_count: %d", result);
  return result;
}

void CgroupUtil::adjust_controller(CgroupMemoryController* mem) {
  assert(mem->cgroup_path() != nullptr, "invariant");
  if (strstr(mem->cgroup_path(), "../") != nullptr) {
    log_warning(os, container)("Cgroup memory controller path at '%s' seems to have moved to '%s', detected limits won't be accurate",
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
  julong phys_mem = os::Linux::physical_memory();
  char* limit_cg_path = nullptr;
  jlong limit = mem->read_memory_limit_in_bytes(phys_mem);
  jlong lowest_limit = limit < 0 ? phys_mem : limit;
  julong orig_limit = ((julong)lowest_limit) != phys_mem ? lowest_limit : phys_mem;
  while ((last_slash = strrchr(cg_path, '/')) != cg_path) {
    *last_slash = '\0'; // strip path
    // update to shortened path and try again
    mem->set_subsystem_path(cg_path);
    limit = mem->read_memory_limit_in_bytes(phys_mem);
    if (limit >= 0 && limit < lowest_limit) {
      lowest_limit = limit;
      os::free(limit_cg_path); // handles nullptr
      limit_cg_path = os::strdup(cg_path);
    }
  }
  // need to check limit at mount point
  mem->set_subsystem_path("/");
  limit = mem->read_memory_limit_in_bytes(phys_mem);
  if (limit >= 0 && limit < lowest_limit) {
    lowest_limit = limit;
    os::free(limit_cg_path); // handles nullptr
    limit_cg_path = os::strdup("/");
  }
  assert(lowest_limit >= 0, "limit must be positive");
  if ((julong)lowest_limit != orig_limit) {
    // we've found a lower limit anywhere in the hierarchy,
    // set the path to the limit path
    assert(limit_cg_path != nullptr, "limit path must be set");
    mem->set_subsystem_path(limit_cg_path);
    log_trace(os, container)("Adjusted controller path for memory to: %s. "
                             "Lowest limit was: " JLONG_FORMAT,
                             mem->subsystem_path(),
                             lowest_limit);
  } else {
    log_trace(os, container)("Lowest limit was: " JLONG_FORMAT, lowest_limit);
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
    log_warning(os, container)("Cgroup cpu controller path at '%s' seems to have moved to '%s', detected limits won't be accurate",
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
  int cpus = CgroupUtil::processor_count(cpu, host_cpus);
  int lowest_limit = cpus < host_cpus ? cpus: host_cpus;
  int orig_limit = lowest_limit != host_cpus ? lowest_limit : host_cpus;
  char* limit_cg_path = nullptr;
  while ((last_slash = strrchr(cg_path, '/')) != cg_path) {
    *last_slash = '\0'; // strip path
    // update to shortened path and try again
    cpu->set_subsystem_path(cg_path);
    cpus = CgroupUtil::processor_count(cpu, host_cpus);
    if (cpus != host_cpus && cpus < lowest_limit) {
      lowest_limit = cpus;
      os::free(limit_cg_path); // handles nullptr
      limit_cg_path = os::strdup(cg_path);
    }
  }
  // need to check limit at mount point
  cpu->set_subsystem_path("/");
  cpus = CgroupUtil::processor_count(cpu, host_cpus);
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
                             cpu->subsystem_path(),
                             lowest_limit);
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
