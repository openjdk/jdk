/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
  GET_CONTAINER_INFO(jlong, this, "/memory.use_hierarchy",
                    "Use Hierarchy is: ", JLONG_FORMAT, JLONG_FORMAT, use_hierarchy);
  return use_hierarchy;
}

void CgroupV1MemoryController::set_subsystem_path(char *cgroup_path) {
  CgroupV1Controller::set_subsystem_path(cgroup_path);
  jlong hierarchy = uses_mem_hierarchy();
  if (hierarchy > 0) {
    set_hierarchical(true);
  }
}

jlong CgroupV1Subsystem::read_memory_limit_in_bytes() {
  GET_CONTAINER_INFO(julong, _memory->controller(), "/memory.limit_in_bytes",
                     "Memory Limit is: ", JULONG_FORMAT, JULONG_FORMAT, memlimit);

  if (memlimit >= os::Linux::physical_memory()) {
    log_trace(os, container)("Non-Hierarchical Memory Limit is: Unlimited");
    CgroupV1MemoryController* mem_controller = reinterpret_cast<CgroupV1MemoryController*>(_memory->controller());
    if (mem_controller->is_hierarchical()) {
      GET_CONTAINER_INFO_LINE(julong, _memory->controller(), "/memory.stat", "hierarchical_memory_limit",
                             "Hierarchical Memory Limit is: " JULONG_FORMAT, JULONG_FORMAT, hier_memlimit)
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
  GET_CONTAINER_INFO(julong, _memory->controller(), "/memory.memsw.limit_in_bytes",
                     "Memory and Swap Limit is: ", JULONG_FORMAT, JULONG_FORMAT, memswlimit);
  host_total_memsw = os::Linux::host_swap() + os::Linux::physical_memory();
  if (memswlimit >= host_total_memsw) {
    log_trace(os, container)("Non-Hierarchical Memory and Swap Limit is: Unlimited");
    CgroupV1MemoryController* mem_controller = reinterpret_cast<CgroupV1MemoryController*>(_memory->controller());
    if (mem_controller->is_hierarchical()) {
      const char* matchline = "hierarchical_memsw_limit";
      GET_CONTAINER_INFO_LINE(julong, _memory->controller(), "/memory.stat", matchline,
                             "Hierarchical Memory and Swap Limit is : " JULONG_FORMAT, JULONG_FORMAT, hier_memswlimit)
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

jlong CgroupV1Subsystem::read_mem_swappiness() {
  GET_CONTAINER_INFO(julong, _memory->controller(), "/memory.swappiness",
                     "Swappiness is: ", JULONG_FORMAT, JULONG_FORMAT, swappiness);
  return swappiness;
}

jlong CgroupV1Subsystem::memory_soft_limit_in_bytes() {
  GET_CONTAINER_INFO(julong, _memory->controller(), "/memory.soft_limit_in_bytes",
                     "Memory Soft Limit is: ", JULONG_FORMAT, JULONG_FORMAT, memsoftlimit);
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
  GET_CONTAINER_INFO(jlong, _memory->controller(), "/memory.usage_in_bytes",
                     "Memory Usage is: ", JLONG_FORMAT, JLONG_FORMAT, memusage);
  return memusage;
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
  GET_CONTAINER_INFO(jlong, _memory->controller(), "/memory.max_usage_in_bytes",
                     "Maximum Memory Usage is: ", JLONG_FORMAT, JLONG_FORMAT, memmaxusage);
  return memmaxusage;
}

jlong CgroupV1Subsystem::rss_usage_in_bytes() {
  GET_CONTAINER_INFO_LINE(julong, _memory->controller(), "/memory.stat",
                          "rss", JULONG_FORMAT, JULONG_FORMAT, rss);
  return rss;
}

jlong CgroupV1Subsystem::cache_usage_in_bytes() {
  GET_CONTAINER_INFO_LINE(julong, _memory->controller(), "/memory.stat",
                          "cache", JULONG_FORMAT, JULONG_FORMAT, cache);
  return cache;
}

jlong CgroupV1Subsystem::kernel_memory_usage_in_bytes() {
  GET_CONTAINER_INFO(jlong, _memory->controller(), "/memory.kmem.usage_in_bytes",
                     "Kernel Memory Usage is: ", JLONG_FORMAT, JLONG_FORMAT, kmem_usage);
  return kmem_usage;
}

jlong CgroupV1Subsystem::kernel_memory_limit_in_bytes() {
  GET_CONTAINER_INFO(julong, _memory->controller(), "/memory.kmem.limit_in_bytes",
                     "Kernel Memory Limit is: ", JULONG_FORMAT, JULONG_FORMAT, kmem_limit);
  if (kmem_limit >= os::Linux::physical_memory()) {
    return (jlong)-1;
  }
  return (jlong)kmem_limit;
}

jlong CgroupV1Subsystem::kernel_memory_max_usage_in_bytes() {
  GET_CONTAINER_INFO(jlong, _memory->controller(), "/memory.kmem.max_usage_in_bytes",
                     "Maximum Kernel Memory Usage is: ", JLONG_FORMAT, JLONG_FORMAT, kmem_max_usage);
  return kmem_max_usage;
}

void CgroupV1Subsystem::print_version_specific_info(outputStream* st) {
  jlong kmem_usage = kernel_memory_usage_in_bytes();
  jlong kmem_limit = kernel_memory_limit_in_bytes();
  jlong kmem_max_usage = kernel_memory_max_usage_in_bytes();

  OSContainer::print_container_helper(st, kmem_usage, "kernel_memory_usage_in_bytes");
  OSContainer::print_container_helper(st, kmem_limit, "kernel_memory_max_usage_in_bytes");
  OSContainer::print_container_helper(st, kmem_max_usage, "kernel_memory_limit_in_bytes");
}

char * CgroupV1Subsystem::cpu_cpuset_cpus() {
  GET_CONTAINER_INFO_CPTR(cptr, _cpuset, "/cpuset.cpus",
                     "cpuset.cpus is: %s", "%1023s", cpus, 1024);
  return os::strdup(cpus);
}

char * CgroupV1Subsystem::cpu_cpuset_memory_nodes() {
  GET_CONTAINER_INFO_CPTR(cptr, _cpuset, "/cpuset.mems",
                     "cpuset.mems is: %s", "%1023s", mems, 1024);
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
  GET_CONTAINER_INFO(int, _cpu->controller(), "/cpu.cfs_quota_us",
                     "CPU Quota is: ", "%d", "%d", quota);
  return quota;
}

int CgroupV1Subsystem::cpu_period() {
  GET_CONTAINER_INFO(int, _cpu->controller(), "/cpu.cfs_period_us",
                     "CPU Period is: ", "%d", "%d", period);
  return period;
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
  GET_CONTAINER_INFO(int, _cpu->controller(), "/cpu.shares",
                     "CPU Shares is: ", "%d", "%d", shares);
  // Convert 1024 to no shares setup
  if (shares == 1024) return -1;

  return shares;
}


char* CgroupV1Subsystem::pids_max_val() {
  GET_CONTAINER_INFO_CPTR(cptr, _pids, "/pids.max",
                     "Maximum number of tasks is: %s", "%1023s", pidsmax, 1024);
  return os::strdup(pidsmax);
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
  char * pidsmax_str = pids_max_val();
  return limit_from_str(pidsmax_str);
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
  GET_CONTAINER_INFO(jlong, _pids, "/pids.current",
                     "Current number of tasks is: ", JLONG_FORMAT, JLONG_FORMAT, pids_current);
  return pids_current;
}
