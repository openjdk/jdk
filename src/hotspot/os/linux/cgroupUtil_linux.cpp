/*
 * Copyright (c) 2024, Red Hat, Inc.
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
  if (!mem->needs_hierarchy_adjustment()) {
    // nothing to do
    return;
  }
  log_trace(os, container)("Adjusting controller path for memory: %s", mem->subsystem_path());
  assert(mem->cgroup_path() != nullptr, "invariant");
  char* orig = os::strdup(mem->cgroup_path());
  char* cg_path = os::strdup(orig);
  char* last_slash;
  assert(cg_path[0] == '/', "cgroup path must start with '/'");
  julong phys_mem = os::Linux::physical_memory();
  jlong limit = mem->read_memory_limit_in_bytes(phys_mem);
  bool path_iterated = false;
  while (limit < 0 && (last_slash = strrchr(cg_path, '/')) != cg_path) {
    *last_slash = '\0'; // strip path
    // update to shortened path and try again
    mem->set_subsystem_path(cg_path);
    limit = mem->read_memory_limit_in_bytes(phys_mem);
    path_iterated = true;
    if (limit > 0) {
      log_trace(os, container)("Adjusted controller path for memory to: %s", mem->subsystem_path());
      os::free(cg_path);
      os::free(orig);
      return;
    }
  }
  // no lower limit found or limit at leaf
  os::free(cg_path);
  if (path_iterated) {
    mem->set_subsystem_path("/");
    limit = mem->read_memory_limit_in_bytes(phys_mem);
    if (limit > 0) {
      // handle limit set at mount point
      log_trace(os, container)("Adjusted controller path for memory to: %s", mem->subsystem_path());
      os::free(orig);
      return;
    }
    log_trace(os, container)("No lower limit found in hierarchy %s, adjusting to original path %s",
                              mem->mount_point(), orig);
    mem->set_subsystem_path(orig);
  } else {
    log_trace(os, container)("Lowest limit for memory at leaf: %s",
                              mem->subsystem_path());
  }
  os::free(orig);
}

void CgroupUtil::adjust_controller(CgroupCpuController* cpu) {
  if (!cpu->needs_hierarchy_adjustment()) {
    // nothing to do
    return;
  }
  log_trace(os, container)("Adjusting controller path for cpu: %s", cpu->subsystem_path());
  assert(cpu->cgroup_path() != nullptr, "invariant");
  char* orig = os::strdup(cpu->cgroup_path());
  char* cg_path = os::strdup(orig);
  char* last_slash;
  assert(cg_path[0] == '/', "cgroup path must start with '/'");
  int host_cpus = os::Linux::active_processor_count();
  int cpus = CgroupUtil::processor_count(cpu, host_cpus);
  bool path_iterated = false;
  while (cpus == host_cpus && (last_slash = strrchr(cg_path, '/')) != cg_path) {
    *last_slash = '\0'; // strip path
    // update to shortened path and try again
    cpu->set_subsystem_path(cg_path);
    cpus = CgroupUtil::processor_count(cpu, host_cpus);
    path_iterated = true;
    if (cpus != host_cpus) {
      log_trace(os, container)("Adjusted controller path for cpu to: %s", cpu->subsystem_path());
      os::free(cg_path);
      os::free(orig);
      return;
    }
  }
  // no lower limit found or limit at leaf
  os::free(cg_path);
  if (path_iterated) {
    cpu->set_subsystem_path("/");
    cpus = CgroupUtil::processor_count(cpu, host_cpus);
    if (cpus != host_cpus) {
      // handle limit set at mount point
      log_trace(os, container)("Adjusted controller path for cpu to: %s", cpu->subsystem_path());
      os::free(orig);
      return;
    }
    log_trace(os, container)("No lower limit found in hierarchy %s, adjusting to original path %s",
                              cpu->mount_point(), orig);
    cpu->set_subsystem_path(orig);
  } else {
    log_trace(os, container)("Lowest limit for cpu at leaf: %s",
                              cpu->subsystem_path());
  }
  os::free(orig);
  return;
}
