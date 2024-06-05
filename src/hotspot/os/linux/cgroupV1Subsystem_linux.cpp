/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include <string.h>
#include <math.h>
#include <errno.h>
#include "cgroupV1Subsystem_linux.hpp"
#include "logging/log.hpp"
#include "memory/allocation.hpp"
#include "runtime/globals.hpp"
#include "runtime/os.hpp"
#include "utilities/globalDefinitions.hpp"
#include "os_linux.hpp"

/*
 * Set directory to subsystem specific files based
 * on the contents of the mountinfo and cgroup files.
 */
void CgroupV1Controller::set_subsystem_path(char *cgroup_path) {
  stringStream ss;
  if (_root != nullptr && cgroup_path != nullptr) {
    if (strcmp(_root, "/") == 0) {
      ss.print_raw(_mount_point);
      if (strcmp(cgroup_path,"/") != 0) {
        ss.print_raw(cgroup_path);
      }
      _path = os::strdup(ss.base());
    } else {
      if (strcmp(_root, cgroup_path) == 0) {
        ss.print_raw(_mount_point);
        _path = os::strdup(ss.base());
      } else {
        char *p = strstr(cgroup_path, _root);
        if (p != nullptr && p == _root) {
          if (strlen(cgroup_path) > strlen(_root)) {
            ss.print_raw(_mount_point);
            const char* cg_path_sub = cgroup_path + strlen(_root);
            ss.print_raw(cg_path_sub);
            _path = os::strdup(ss.base());
          }
        }
      }
    }
  }
}

/* uses_mem_hierarchy
 *
 * Return whether or not hierarchical cgroup accounting is being
 * done.
 *
 * return:
 *    A number > 0 if true, or
 *    OSCONTAINER_ERROR for not supported
 */
jlong CgroupV1MemoryController::uses_mem_hierarchy() {
  julong use_hierarchy;
  CONTAINER_READ_NUMBER_CHECKED(this, "/memory.use_hierarchy", "Use Hierarchy", use_hierarchy);
  return (jlong)use_hierarchy;
}

void CgroupV1MemoryController::set_subsystem_path(char *cgroup_path) {
  CgroupV1Controller::set_subsystem_path(cgroup_path);
  jlong hierarchy = uses_mem_hierarchy();
  if (hierarchy > 0) {
    set_hierarchical(true);
  }
}

jlong CgroupV1Subsystem::read_memory_limit_in_bytes() {
  julong memlimit;
  CONTAINER_READ_NUMBER_CHECKED(_memory->controller(), "/memory.limit_in_bytes", "Memory Limit", memlimit);
  if (memlimit >= os::Linux::physical_memory()) {
    log_trace(os, container)("Non-Hierarchical Memory Limit is: Unlimited");
    CgroupV1MemoryController* mem_controller = reinterpret_cast<CgroupV1MemoryController*>(_memory->controller());
    if (mem_controller->is_hierarchical()) {
      julong hier_memlimit;
      bool is_ok = _memory->controller()->read_numerical_key_value("/memory.stat",
                                                                   "hierarchical_memory_limit",
                                                                   &hier_memlimit);
      if (!is_ok) {
        return OSCONTAINER_ERROR;
      }
      log_trace(os, container)("Hierarchical Memory Limit is: " JULONG_FORMAT, hier_memlimit);
      if (hier_memlimit >= os::Linux::physical_memory()) {
        log_trace(os, container)("Hierarchical Memory Limit is: Unlimited");
      } else {
        return (jlong)hier_memlimit;
      }
    }
    return (jlong)-1;
  }
  else {
    return (jlong)memlimit;
  }
}

/* read_mem_swap
 *
 * Determine the memory and swap limit metric. Returns a positive limit value strictly
 * lower than the physical memory and swap limit iff there is a limit. Otherwise a
 * negative value is returned indicating the determined status.
 *
 * returns:
 *    * A number > 0 if the limit is available and lower than a physical upper bound.
 *    * OSCONTAINER_ERROR if the limit cannot be retrieved (i.e. not supported) or
 *    * -1 if there isn't any limit in place (note: includes values which exceed a physical
 *      upper bound)
 */
jlong CgroupV1Subsystem::read_mem_swap() {
  julong host_total_memsw;
  julong hier_memswlimit;
  julong memswlimit;
  CONTAINER_READ_NUMBER_CHECKED(_memory->controller(), "/memory.memsw.limit_in_bytes", "Memory and Swap Limit", memswlimit);
  host_total_memsw = os::Linux::host_swap() + os::Linux::physical_memory();
  if (memswlimit >= host_total_memsw) {
    log_trace(os, container)("Non-Hierarchical Memory and Swap Limit is: Unlimited");
    CgroupV1MemoryController* mem_controller = reinterpret_cast<CgroupV1MemoryController*>(_memory->controller());
    if (mem_controller->is_hierarchical()) {
      const char* matchline = "hierarchical_memsw_limit";
      bool is_ok = _memory->controller()->read_numerical_key_value("/memory.stat",
                                                                   matchline,
                                                                   &hier_memswlimit);
      if (!is_ok) {
        return OSCONTAINER_ERROR;
      }
      log_trace(os, container)("Hierarchical Memory and Swap Limit is: " JULONG_FORMAT, hier_memswlimit);
      if (hier_memswlimit >= host_total_memsw) {
        log_trace(os, container)("Hierarchical Memory and Swap Limit is: Unlimited");
      } else {
        return (jlong)hier_memswlimit;
      }
    }
    return (jlong)-1;
  } else {
    return (jlong)memswlimit;
  }
}

jlong CgroupV1Subsystem::memory_and_swap_limit_in_bytes() {
  jlong memory_swap = read_mem_swap();
  if (memory_swap == -1) {
    return memory_swap;
  }
  // If there is a swap limit, but swappiness == 0, reset the limit
  // to the memory limit. Do the same for cases where swap isn't
  // supported.
  jlong swappiness = read_mem_swappiness();
  if (swappiness == 0 || memory_swap == OSCONTAINER_ERROR) {
    jlong memlimit = read_memory_limit_in_bytes();
    if (memory_swap == OSCONTAINER_ERROR) {
      log_trace(os, container)("Memory and Swap Limit has been reset to " JLONG_FORMAT " because swap is not supported", memlimit);
    } else {
      log_trace(os, container)("Memory and Swap Limit has been reset to " JLONG_FORMAT " because swappiness is 0", memlimit);
    }
    return memlimit;
  }
  return memory_swap;
}

static inline
jlong memory_swap_usage_impl(CgroupController* ctrl) {
  julong memory_swap_usage;
  CONTAINER_READ_NUMBER_CHECKED(ctrl, "/memory.memsw.usage_in_bytes", "mem swap usage", memory_swap_usage);
  return (jlong)memory_swap_usage;
}

jlong CgroupV1Subsystem::memory_and_swap_usage_in_bytes() {
  jlong memory_sw_limit = memory_and_swap_limit_in_bytes();
  jlong memory_limit = CgroupSubsystem::memory_limit_in_bytes();
  if (memory_sw_limit > 0 && memory_limit > 0) {
    jlong delta_swap = memory_sw_limit - memory_limit;
    if (delta_swap > 0) {
      return memory_swap_usage_impl(_memory->controller());
    }
  }
  return memory_usage_in_bytes();
}

jlong CgroupV1Subsystem::read_mem_swappiness() {
  julong swappiness;
  CONTAINER_READ_NUMBER_CHECKED(_memory->controller(), "/memory.swappiness", "Swappiness", swappiness);
  return (jlong)swappiness;
}

jlong CgroupV1Subsystem::memory_soft_limit_in_bytes() {
  julong memsoftlimit;
  CONTAINER_READ_NUMBER_CHECKED(_memory->controller(), "/memory.soft_limit_in_bytes", "Memory Soft Limit", memsoftlimit);
  if (memsoftlimit >= os::Linux::physical_memory()) {
    log_trace(os, container)("Memory Soft Limit is: Unlimited");
    return (jlong)-1;
  } else {
    return (jlong)memsoftlimit;
  }
}

/* memory_usage_in_bytes
 *
 * Return the amount of used memory for this process.
 *
 * return:
 *    memory usage in bytes or
 *    -1 for unlimited
 *    OSCONTAINER_ERROR for not supported
 */
jlong CgroupV1Subsystem::memory_usage_in_bytes() {
  julong memusage;
  CONTAINER_READ_NUMBER_CHECKED(_memory->controller(), "/memory.usage_in_bytes", "Memory Usage", memusage);
  return (jlong)memusage;
}

/* memory_max_usage_in_bytes
 *
 * Return the maximum amount of used memory for this process.
 *
 * return:
 *    max memory usage in bytes or
 *    OSCONTAINER_ERROR for not supported
 */
jlong CgroupV1Subsystem::memory_max_usage_in_bytes() {
  julong memmaxusage;
  CONTAINER_READ_NUMBER_CHECKED(_memory->controller(), "/memory.max_usage_in_bytes", "Maximum Memory Usage", memmaxusage);
  return (jlong)memmaxusage;
}

jlong CgroupV1Subsystem::rss_usage_in_bytes() {
  julong rss;
  bool is_ok = _memory->controller()->read_numerical_key_value("/memory.stat",
                                                               "rss",
                                                               &rss);
  if (!is_ok) {
    return OSCONTAINER_ERROR;
  }
  log_trace(os, container)("RSS usage is: " JULONG_FORMAT, rss);
  return (jlong)rss;
}

jlong CgroupV1Subsystem::cache_usage_in_bytes() {
  julong cache;
  bool is_ok = _memory->controller()->read_numerical_key_value("/memory.stat",
                                                               "cache",
                                                               &cache);
  if (!is_ok) {
    return OSCONTAINER_ERROR;
  }
  log_trace(os, container)("Cache usage is: " JULONG_FORMAT, cache);
  return cache;
}

jlong CgroupV1Subsystem::kernel_memory_usage_in_bytes() {
  julong kmem_usage;
  CONTAINER_READ_NUMBER_CHECKED(_memory->controller(), "/memory.kmem.usage_in_bytes", "Kernel Memory Usage", kmem_usage);
  return (jlong)kmem_usage;
}

jlong CgroupV1Subsystem::kernel_memory_limit_in_bytes() {
  julong kmem_limit;
  CONTAINER_READ_NUMBER_CHECKED(_memory->controller(), "/memory.kmem.limit_in_bytes", "Kernel Memory Limit", kmem_limit);
  if (kmem_limit >= os::Linux::physical_memory()) {
    return (jlong)-1;
  }
  return (jlong)kmem_limit;
}

jlong CgroupV1Subsystem::kernel_memory_max_usage_in_bytes() {
  julong kmem_max_usage;
  CONTAINER_READ_NUMBER_CHECKED(_memory->controller(), "/memory.kmem.max_usage_in_bytes", "Maximum Kernel Memory Usage", kmem_max_usage);
  return (jlong)kmem_max_usage;
}

void CgroupV1Subsystem::print_version_specific_info(outputStream* st) {
  jlong kmem_usage = kernel_memory_usage_in_bytes();
  jlong kmem_limit = kernel_memory_limit_in_bytes();
  jlong kmem_max_usage = kernel_memory_max_usage_in_bytes();

  OSContainer::print_container_helper(st, kmem_usage, "kernel_memory_usage_in_bytes");
  OSContainer::print_container_helper(st, kmem_limit, "kernel_memory_max_usage_in_bytes");
  OSContainer::print_container_helper(st, kmem_max_usage, "kernel_memory_limit_in_bytes");
}

char* CgroupV1Subsystem::cpu_cpuset_cpus() {
  char cpus[1024];
  CONTAINER_READ_STRING_CHECKED(_cpuset, "/cpuset.cpus", "cpuset.cpus", cpus, 1024);
  return os::strdup(cpus);
}

char* CgroupV1Subsystem::cpu_cpuset_memory_nodes() {
  char mems[1024];
  CONTAINER_READ_STRING_CHECKED(_cpuset, "/cpuset.mems", "cpuset.mems", mems, 1024);
  return os::strdup(mems);
}

/* cpu_quota
 *
 * Return the number of microseconds per period
 * process is guaranteed to run.
 *
 * return:
 *    quota time in microseconds
 *    -1 for no quota
 *    OSCONTAINER_ERROR for not supported
 */
int CgroupV1Subsystem::cpu_quota() {
  julong quota;
  bool is_ok = _cpu->controller()->
                  read_number("/cpu.cfs_quota_us", &quota);
  if (!is_ok) {
    log_trace(os, container)("CPU Quota failed: %d", OSCONTAINER_ERROR);
    return OSCONTAINER_ERROR;
  }
  // cast to int since the read value might be negative
  // and we want to avoid logging -1 as a large unsigned value.
  int quota_int = (int)quota;
  log_trace(os, container)("CPU Quota is: %d", quota_int);
  return quota_int;
}

int CgroupV1Subsystem::cpu_period() {
  julong period;
  CONTAINER_READ_NUMBER_CHECKED(_cpu->controller(), "/cpu.cfs_period_us", "CPU Period", period);
  return (int)period;
}

/* cpu_shares
 *
 * Return the amount of cpu shares available to the process
 *
 * return:
 *    Share number (typically a number relative to 1024)
 *                 (2048 typically expresses 2 CPUs worth of processing)
 *    -1 for no share setup
 *    OSCONTAINER_ERROR for not supported
 */
int CgroupV1Subsystem::cpu_shares() {
  julong shares;
  CONTAINER_READ_NUMBER_CHECKED(_cpu->controller(), "/cpu.shares", "CPU Shares", shares);
  int shares_int = (int)shares;
  // Convert 1024 to no shares setup
  if (shares_int == 1024) return -1;

  return shares_int;
}

/* pids_max
 *
 * Return the maximum number of tasks available to the process
 *
 * return:
 *    maximum number of tasks
 *    -1 for unlimited
 *    OSCONTAINER_ERROR for not supported
 */
jlong CgroupV1Subsystem::pids_max() {
  if (_pids == nullptr) return OSCONTAINER_ERROR;
  jlong pids_max;
  CONTAINER_READ_NUMBER_CHECKED_MAX(_pids, "/pids.max", "Maximum number of tasks", pids_max);
  return pids_max;
}

/* pids_current
 *
 * The number of tasks currently in the cgroup (and its descendants) of the process
 *
 * return:
 *    current number of tasks
 *    OSCONTAINER_ERROR for not supported
 */
jlong CgroupV1Subsystem::pids_current() {
  if (_pids == nullptr) return OSCONTAINER_ERROR;
  julong pids_current;
  CONTAINER_READ_NUMBER_CHECKED(_pids, "/pids.current", "Current number of tasks", pids_current);
  return (jlong)pids_current;
}
