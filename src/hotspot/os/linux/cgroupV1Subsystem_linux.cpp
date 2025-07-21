/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "cgroupUtil_linux.hpp"
#include "logging/log.hpp"
#include "memory/allocation.hpp"
#include "runtime/globals.hpp"
#include "runtime/os.hpp"
#include "utilities/globalDefinitions.hpp"
#include "os_linux.hpp"

/*
 * Set directory to subsystem specific files based
 * on the contents of the mountinfo and cgroup files.
 *
 * The method determines whether it runs in
 * - host mode
 * - container mode
 *
 * In the host mode, _root is equal to "/" and
 * the subsystem path is equal to the _mount_point path
 * joined with cgroup_path.
 *
 * In the container mode, it can be two possibilities:
 * - private namespace (cgroupns=private)
 * - host namespace (cgroupns=host, default mode in cgroup V1 hosts)
 *
 * Private namespace is equivalent to the host mode, i.e.
 * the subsystem path is set by concatenating
 * _mount_point and cgroup_path.
 *
 * In the host namespace, _root is equal to host's cgroup path
 * of the control group to which the containerized process
 * belongs to at the moment of creation. The mountinfo and
 * cgroup files are mirrored from the host, while the subsystem
 * specific files are mapped directly at _mount_point, i.e.
 * at /sys/fs/cgroup/<controller>/, the subsystem path is
 * then set equal to _mount_point.
 *
 * A special case of the subsystem path is when a cgroup path
 * includes a subgroup, when a containerized process was associated
 * with an existing cgroup, that is different from cgroup
 * in which the process has been created.
 * Here, the _root is equal to the host's initial cgroup path,
 * cgroup_path will be equal to host's new cgroup path.
 * As host cgroup hierarchies are not accessible in the container,
 * it needs to be determined which part of cgroup path
 * is accessible inside container, i.e. mapped under
 * /sys/fs/cgroup/<controller>/<subgroup>.
 * In Docker default setup, host's cgroup path can be
 * of the form: /docker/<CONTAINER_ID>/<subgroup>,
 * from which only <subgroup> is mapped.
 * The method trims cgroup path from left, until the subgroup
 * component is found. The subsystem path will be set to
 * the _mount_point joined with the subgroup path.
 */
void CgroupV1Controller::set_subsystem_path(const char* cgroup_path) {
  if (_cgroup_path != nullptr) {
    os::free(_cgroup_path);
  }
  if (_path != nullptr) {
    os::free(_path);
    _path = nullptr;
  }
  _cgroup_path = os::strdup(cgroup_path);
  stringStream ss;
  if (_root != nullptr && cgroup_path != nullptr) {
    ss.print_raw(_mount_point);
    if (strcmp(_root, "/") == 0) {
      // host processes and containers with cgroupns=private
      if (strcmp(cgroup_path,"/") != 0) {
        ss.print_raw(cgroup_path);
      }
    } else {
      // containers with cgroupns=host, default setting is _root==cgroup_path
      if (strcmp(_root, cgroup_path) != 0) {
        if (*cgroup_path != '\0' && strcmp(cgroup_path, "/") != 0) {
          // When moved to a subgroup, between subgroups, the path suffix will change.
          const char *suffix = cgroup_path;
          while (suffix != nullptr) {
            stringStream pp;
            pp.print_raw(_mount_point);
            pp.print_raw(suffix);
            if (os::file_exists(pp.base())) {
              ss.print_raw(suffix);
              if (suffix != cgroup_path) {
                log_trace(os, container)("set_subsystem_path: cgroup v1 path reduced to: %s.", suffix);
              }
              break;
            }
            log_trace(os, container)("set_subsystem_path: skipped non-existent directory: %s.", suffix);
            suffix = strchr(suffix + 1, '/');
          }
        }
      }
    }
    _path = os::strdup(ss.base());
  }
}

/*
 * The common case, containers, we have _root == _cgroup_path, and thus set the
 * controller path to the _mount_point. This is where the limits are exposed in
 * the cgroup pseudo filesystem (at the leaf) and adjustment of the path won't
 * be needed for that reason.
 */
bool CgroupV1Controller::needs_hierarchy_adjustment() {
  assert(_cgroup_path != nullptr, "sanity");
  return strcmp(_root, _cgroup_path) != 0;
}

static inline
void verbose_log(julong read_mem_limit, julong host_mem) {
  if (log_is_enabled(Debug, os, container)) {
    jlong mem_limit = (jlong)read_mem_limit; // account for negative values
    if (mem_limit < 0 || read_mem_limit >= host_mem) {
      const char *reason;
      if (mem_limit == OSCONTAINER_ERROR) {
        reason = "failed";
      } else if (mem_limit == -1) {
        reason = "unlimited";
      } else {
        assert(read_mem_limit >= host_mem, "Expected read value exceeding host_mem");
        // Exceeding physical memory is treated as unlimited. This implementation
        // caps it at host_mem since Cg v1 has no value to represent 'max'.
        reason = "ignored";
      }
      log_debug(os, container)("container memory limit %s: " JLONG_FORMAT ", using host value " JLONG_FORMAT,
                               reason, mem_limit, host_mem);
    }
  }
}

jlong CgroupV1MemoryController::read_memory_limit_in_bytes(julong phys_mem) {
  julong memlimit;
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/memory.limit_in_bytes", "Memory Limit", memlimit);
  if (memlimit >= phys_mem) {
    verbose_log(memlimit, phys_mem);
    return (jlong)-1;
  } else {
    verbose_log(memlimit, phys_mem);
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
jlong CgroupV1MemoryController::read_mem_swap(julong host_total_memsw) {
  julong memswlimit;
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/memory.memsw.limit_in_bytes", "Memory and Swap Limit", memswlimit);
  if (memswlimit >= host_total_memsw) {
    log_trace(os, container)("Memory and Swap Limit is: Unlimited");
    return (jlong)-1;
  } else {
    return (jlong)memswlimit;
  }
}

jlong CgroupV1MemoryController::memory_and_swap_limit_in_bytes(julong host_mem, julong host_swap) {
  jlong memory_swap = read_mem_swap(host_mem + host_swap);
  if (memory_swap == -1) {
    return memory_swap;
  }
  // If there is a swap limit, but swappiness == 0, reset the limit
  // to the memory limit. Do the same for cases where swap isn't
  // supported.
  jlong swappiness = read_mem_swappiness();
  if (swappiness == 0 || memory_swap == OSCONTAINER_ERROR) {
    jlong memlimit = read_memory_limit_in_bytes(host_mem);
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

jlong CgroupV1MemoryController::memory_and_swap_usage_in_bytes(julong phys_mem, julong host_swap) {
  jlong memory_sw_limit = memory_and_swap_limit_in_bytes(phys_mem, host_swap);
  jlong memory_limit = read_memory_limit_in_bytes(phys_mem);
  if (memory_sw_limit > 0 && memory_limit > 0) {
    jlong delta_swap = memory_sw_limit - memory_limit;
    if (delta_swap > 0) {
      return memory_swap_usage_impl(reader());
    }
  }
  return memory_usage_in_bytes();
}

jlong CgroupV1MemoryController::read_mem_swappiness() {
  julong swappiness;
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/memory.swappiness", "Swappiness", swappiness);
  return (jlong)swappiness;
}

jlong CgroupV1MemoryController::memory_soft_limit_in_bytes(julong phys_mem) {
  julong memsoftlimit;
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/memory.soft_limit_in_bytes", "Memory Soft Limit", memsoftlimit);
  if (memsoftlimit >= phys_mem) {
    log_trace(os, container)("Memory Soft Limit is: Unlimited");
    return (jlong)-1;
  } else {
    return (jlong)memsoftlimit;
  }
}

jlong CgroupV1MemoryController::memory_throttle_limit_in_bytes() {
  // Log this string at trace level so as to make tests happy.
  log_trace(os, container)("Memory Throttle Limit is not supported.");
  return OSCONTAINER_ERROR; // not supported
}

// Constructor
CgroupV1Subsystem::CgroupV1Subsystem(CgroupV1Controller* cpuset,
                      CgroupV1CpuController* cpu,
                      CgroupV1CpuacctController* cpuacct,
                      CgroupV1Controller* pids,
                      CgroupV1MemoryController* memory) :
    _cpuset(cpuset),
    _cpuacct(cpuacct),
    _pids(pids) {
  CgroupUtil::adjust_controller(memory);
  CgroupUtil::adjust_controller(cpu);
  _memory = new CachingCgroupController<CgroupMemoryController>(memory);
  _cpu = new CachingCgroupController<CgroupCpuController>(cpu);
}

bool CgroupV1Subsystem::is_containerized() {
  // containerized iff all required controllers are mounted
  // read-only. See OSContainer::is_containerized() for
  // the full logic.
  //
  return _memory->controller()->is_read_only() &&
         _cpu->controller()->is_read_only() &&
         _cpuacct->is_read_only() &&
         _cpuset->is_read_only();
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
jlong CgroupV1MemoryController::memory_usage_in_bytes() {
  julong memusage;
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/memory.usage_in_bytes", "Memory Usage", memusage);
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
jlong CgroupV1MemoryController::memory_max_usage_in_bytes() {
  julong memmaxusage;
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/memory.max_usage_in_bytes", "Maximum Memory Usage", memmaxusage);
  return (jlong)memmaxusage;
}

jlong CgroupV1MemoryController::rss_usage_in_bytes() {
  julong rss;
  bool is_ok = reader()->read_numerical_key_value("/memory.stat", "rss", &rss);
  if (!is_ok) {
    return OSCONTAINER_ERROR;
  }
  log_trace(os, container)("RSS usage is: " JULONG_FORMAT, rss);
  return (jlong)rss;
}

jlong CgroupV1MemoryController::cache_usage_in_bytes() {
  julong cache;
  bool is_ok = reader()->read_numerical_key_value("/memory.stat", "cache", &cache);
  if (!is_ok) {
    return OSCONTAINER_ERROR;
  }
  log_trace(os, container)("Cache usage is: " JULONG_FORMAT, cache);
  return cache;
}

jlong CgroupV1MemoryController::kernel_memory_usage_in_bytes() {
  julong kmem_usage;
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/memory.kmem.usage_in_bytes", "Kernel Memory Usage", kmem_usage);
  return (jlong)kmem_usage;
}

jlong CgroupV1MemoryController::kernel_memory_limit_in_bytes(julong phys_mem) {
  julong kmem_limit;
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/memory.kmem.limit_in_bytes", "Kernel Memory Limit", kmem_limit);
  if (kmem_limit >= phys_mem) {
    return (jlong)-1;
  }
  return (jlong)kmem_limit;
}

jlong CgroupV1MemoryController::kernel_memory_max_usage_in_bytes() {
  julong kmem_max_usage;
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/memory.kmem.max_usage_in_bytes", "Maximum Kernel Memory Usage", kmem_max_usage);
  return (jlong)kmem_max_usage;
}

void CgroupV1MemoryController::print_version_specific_info(outputStream* st, julong phys_mem) {
  jlong kmem_usage = kernel_memory_usage_in_bytes();
  jlong kmem_limit = kernel_memory_limit_in_bytes(phys_mem);
  jlong kmem_max_usage = kernel_memory_max_usage_in_bytes();

  OSContainer::print_container_helper(st, kmem_limit, "kernel_memory_limit_in_bytes");
  OSContainer::print_container_helper(st, kmem_usage, "kernel_memory_usage_in_bytes");
  OSContainer::print_container_helper(st, kmem_max_usage, "kernel_memory_max_usage_in_bytes");
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
int CgroupV1CpuController::cpu_quota() {
  julong quota;
  bool is_ok = reader()->read_number("/cpu.cfs_quota_us", &quota);
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

int CgroupV1CpuController::cpu_period() {
  julong period;
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/cpu.cfs_period_us", "CPU Period", period);
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
int CgroupV1CpuController::cpu_shares() {
  julong shares;
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/cpu.shares", "CPU Shares", shares);
  int shares_int = (int)shares;
  // Convert 1024 to no shares setup
  if (shares_int == 1024) return -1;

  return shares_int;
}

jlong CgroupV1CpuacctController::cpu_usage_in_micros() {
  julong cpu_usage;
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/cpuacct.usage", "CPU Usage", cpu_usage);
  // Output is in nanoseconds, convert to microseconds.
  return (jlong)cpu_usage / 1000;
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
