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

#include "cgroupUtil_linux.hpp"
#include "cgroupV1Subsystem_linux.hpp"
#include "logging/log.hpp"
#include "memory/allocation.hpp"
#include "os_linux.hpp"
#include "runtime/globals.hpp"
#include "runtime/os.hpp"
#include "utilities/globalDefinitions.hpp"

#include <errno.h>
#include <math.h>
#include <string.h>

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

bool CgroupV1MemoryController::read_memory_limit_val(size_t& result) {
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/memory.limit_in_bytes", "Memory Limit", result);
}

ssize_t CgroupV1MemoryController::read_memory_limit_in_bytes(size_t host_mem) {
  size_t memlimit = 0;
  if (!read_memory_limit_val(memlimit)) {
    log_trace(os, container)("container memory limit failed, using host value %zu", host_mem);
    return (ssize_t)-1; // treat as unlimited
  }
  if (memlimit >= host_mem) {
    // Exceeding physical memory is treated as unlimited. This implementation
    // caps it at host_mem since Cg v1 has no value to represent 'max'.
    log_trace(os, container)("container memory limit ignored: %zu, using host value %zu",
                              memlimit, host_mem);
    return (ssize_t)-1; // unlimited
  } else {
    return static_cast<ssize_t>(memlimit);
  }
}

bool CgroupV1MemoryController::read_mem_swap(size_t& result) {
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/memory.memsw.limit_in_bytes", "Memory and Swap Limit", result);
}

/* memory_and_swap_limit_in_bytes
 *
 * Determine the memory and swap limit metric. Returns a positive limit value strictly
 * lower than the physical memory and swap limit iff there is a limit. Otherwise a
 * negative value is returned indicating an unlimited value.
 *
 * returns:
 *    * A number > 0 if the limit is available and lower than a physical upper bound.
 *    * -1 if there isn't any limit in place. note: this includes values which exceed
 *      a physical upper bound (or a failure to read from the interface files).
 */
ssize_t CgroupV1MemoryController::memory_and_swap_limit_in_bytes(size_t host_mem, size_t host_swap) {
  size_t total_mem_swap = host_mem + host_swap;
  size_t memory_swap = 0;
  bool mem_swap_read_failed = false;
  if (!read_mem_swap(memory_swap)) {
    mem_swap_read_failed = true;
  }
  if (memory_swap >= total_mem_swap) {
    log_trace(os, container)("Memory and Swap Limit is: Unlimited");
    return (ssize_t)-1;
  }
  // If there is a swap limit, but swappiness == 0, reset the limit
  // to the memory limit. Do the same for cases where swap isn't
  // supported.
  size_t swappiness = 0;
  if (!read_mem_swappiness(swappiness)) {
    // assume no swap
    mem_swap_read_failed = true;
  }
  if (swappiness == 0 || mem_swap_read_failed) {
    ssize_t memlimit = read_memory_limit_in_bytes(host_mem);
    if (mem_swap_read_failed) {
      log_trace(os, container)("Memory and Swap Limit has been reset to %zd because swap is not supported", memlimit);
    } else {
      log_trace(os, container)("Memory and Swap Limit has been reset to %zd because swappiness is 0", memlimit);
    }
    return memlimit;
  }
  return static_cast<ssize_t>(memory_swap);
}

static inline
bool memory_swap_usage_impl(CgroupController* ctrl, size_t& result) {
  CONTAINER_READ_NUMBER_CHECKED(ctrl, "/memory.memsw.usage_in_bytes", "mem swap usage", result);
}

ssize_t CgroupV1MemoryController::memory_and_swap_usage_in_bytes(size_t phys_mem, size_t host_swap) {
  ssize_t memory_sw_limit = memory_and_swap_limit_in_bytes(phys_mem, host_swap);
  ssize_t memory_limit = read_memory_limit_in_bytes(phys_mem);
  if (memory_sw_limit > 0 && memory_limit > 0) {
    ssize_t delta_swap = memory_sw_limit - memory_limit;
    if (delta_swap > 0) {
      size_t swap_usage = 0;
      if (!memory_swap_usage_impl(reader(), swap_usage)) {
        return -1; // usage value error
      }
      return static_cast<ssize_t>(swap_usage);
    }
  }
  return memory_usage_in_bytes();
}

bool CgroupV1MemoryController::read_mem_swappiness(size_t& result) {
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/memory.swappiness", "Swappiness", result);
}

bool CgroupV1MemoryController::memory_soft_limit_val(size_t& result) {
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/memory.soft_limit_in_bytes", "Memory Soft Limit", result);
}

ssize_t CgroupV1MemoryController::memory_soft_limit_in_bytes(size_t phys_mem) {
  size_t mem_soft_limit = 0;
  if (!memory_soft_limit_val(mem_soft_limit)) {
    return (ssize_t)-1; // treat as unlimited
  }
  if (mem_soft_limit >= phys_mem) {
    log_trace(os, container)("Memory Soft Limit is: Unlimited");
    return (ssize_t)-1;
  } else {
    return static_cast<ssize_t>(mem_soft_limit);
  }
}

ssize_t CgroupV1MemoryController::memory_throttle_limit_in_bytes() {
  // Log this string at trace level so as to make tests happy.
  log_trace(os, container)("Memory Throttle Limit is not supported.");
  return (ssize_t)-1; // not supported
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

ssize_t CgroupV1MemoryController::memory_usage_in_bytes() {
  size_t memory_usage = 0;
  if (!memory_usage_val(memory_usage)) {
    return -1; // usage value error
  }
  return static_cast<ssize_t>(memory_usage);
}

/* memory_usage_val
 *
 * Read the amount of used memory for this process into the passed in reference 'result'
 *
 * return:
 *    true when reading of the file was successful and 'result' was set appropriately
 *    false when reading of the file failed
 */
bool CgroupV1MemoryController::memory_usage_val(size_t& result) {
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/memory.usage_in_bytes", "Memory Usage", result);
}

bool CgroupV1MemoryController::memory_max_usage_val(size_t& result) {
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/memory.max_usage_in_bytes", "Maximum Memory Usage", result);
}

/* memory_max_usage_in_bytes
 *
 * Return the maximum amount of used memory for this process.
 *
 * return:
 *    max memory usage in bytes. -1 when unavailable
 */
ssize_t CgroupV1MemoryController::memory_max_usage_in_bytes() {
  size_t memory_max_usage = 0;
  if (!memory_max_usage_val(memory_max_usage)) {
     return -1; // usage value error
  }
  return static_cast<ssize_t>(memory_max_usage);
}

ssize_t CgroupV1MemoryController::rss_usage_in_bytes() {
  size_t rss = 0;
  bool is_ok = reader()->read_numerical_key_value("/memory.stat", "rss", rss);
  if (!is_ok) {
    return -1; // usage value error
  }
  log_trace(os, container)("RSS usage is: %zu", rss);
  return static_cast<ssize_t>(rss);
}

ssize_t CgroupV1MemoryController::cache_usage_in_bytes() {
  size_t cache;
  bool is_ok = reader()->read_numerical_key_value("/memory.stat", "cache", cache);
  if (!is_ok) {
    return -1; // usage value error
  }
  log_trace(os, container)("Cache usage is: %zu", cache);
  return static_cast<ssize_t>(cache);
}

bool CgroupV1MemoryController::kernel_memory_usage_val(size_t& result) {
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/memory.kmem.usage_in_bytes", "Kernel Memory Usage", result);
}

ssize_t CgroupV1MemoryController::kernel_memory_usage_in_bytes() {
  size_t kmem_usage = 0;
  if (!kernel_memory_usage_val(kmem_usage)) {
    return -1; // usage value error
  }
  return static_cast<ssize_t>(kmem_usage);
}

bool CgroupV1MemoryController::kernel_memory_limit_val(size_t& result) {
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/memory.kmem.limit_in_bytes", "Kernel Memory Limit", result);
}

ssize_t CgroupV1MemoryController::kernel_memory_limit_in_bytes(size_t phys_mem) {
  size_t kmem_limit = 0;
  if (!kernel_memory_limit_val(kmem_limit) || kmem_limit >= phys_mem) {
    return (ssize_t)-1; // treat as unlimited
  }
  return static_cast<ssize_t>(kmem_limit);
}

bool CgroupV1MemoryController::kernel_memory_max_usage_val(size_t& result) {
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/memory.kmem.max_usage_in_bytes", "Maximum Kernel Memory Usage", result);
}

ssize_t CgroupV1MemoryController::kernel_memory_max_usage_in_bytes() {
  size_t kmem_max_usage = 0;
  if (!kernel_memory_max_usage_val(kmem_max_usage)) {
    return -1; // usage value error
  }
  return static_cast<ssize_t>(kmem_max_usage);
}

void CgroupV1MemoryController::print_version_specific_info(outputStream* st, size_t phys_mem) {
  ssize_t kmem_usage = kernel_memory_usage_in_bytes();
  ssize_t kmem_limit = kernel_memory_limit_in_bytes(phys_mem);
  ssize_t kmem_max_usage = kernel_memory_max_usage_in_bytes();

  OSContainer::print_container_helper(st, kmem_limit, "kernel_memory_limit_in_bytes", false /* is_usage */);
  OSContainer::print_container_helper(st, kmem_usage, "kernel_memory_usage_in_bytes", true  /* is_usage */);
  OSContainer::print_container_helper(st, kmem_max_usage, "kernel_memory_max_usage_in_bytes", true /* is_usage */);
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
 *    -1 for no quota or when an error occurs
 */
int CgroupV1CpuController::cpu_quota() {
  size_t quota = 0;
  bool is_ok = reader()->read_number("/cpu.cfs_quota_us", quota);
  if (!is_ok) {
    log_trace(os, container)("CPU Quota failed: %d", OSCONTAINER_ERROR);
    return -1; // treat as unlimited
  }
  // cast to int since the read value might be negative
  // and we want to avoid logging -1 as a large unsigned value.
  int quota_int = (int)quota;
  log_trace(os, container)("CPU Quota is: %d", quota_int);
  return quota_int;
}

bool CgroupV1CpuController::cpu_period(size_t& result) {
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/cpu.cfs_period_us", "CPU Period", result);
}

int CgroupV1CpuController::cpu_period() {
  size_t period = 0;
  if (!cpu_period(period)) {
    return -1; // treat as unlimited
  }
  return static_cast<int>(period);
}

bool CgroupV1CpuController::cpu_shares(size_t& result) {
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/cpu.shares", "CPU Shares", result);
}

/* cpu_shares
 *
 * Return the amount of cpu shares available to the process
 *
 * return:
 *    Share number (typically a number relative to 1024)
 *                 (2048 typically expresses 2 CPUs worth of processing)
 *    -1 for no share setup (or error)
 */
int CgroupV1CpuController::cpu_shares() {
  size_t shares = 0;
  if (!cpu_shares(shares)) {
    return -1; // treat as unlimited
  }
  int shares_int = (int)shares;
  // Convert 1024 to no shares setup
  if (shares_int == 1024) return -1;

  return shares_int;
}

bool CgroupV1CpuacctController::cpu_usage_in_micros(size_t& result) {
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/cpuacct.usage", "CPU Usage", result);
}

ssize_t CgroupV1CpuacctController::cpu_usage_in_micros() {
  size_t cpu_usage = 0;
  if (!cpu_usage_in_micros(cpu_usage)) {
    return -1; // usage value error
  }
  // Output is in nanoseconds, convert to microseconds.
  return static_cast<ssize_t>(cpu_usage / 1000);
}

static
bool pids_max_val(CgroupController* ctrl, ssize_t& result) {
  CONTAINER_READ_NUMBER_CHECKED_MAX(ctrl, "/pids.max", "Maximum number of tasks", result);
}

/* pids_max
 *
 * Return the maximum number of tasks available to the process
 *
 * return:
 *    maximum number of tasks
 *    -1 for unlimited (or failure to retrieve the value)
 */
ssize_t CgroupV1Subsystem::pids_max() {
  if (_pids == nullptr) return -1; // treat as unlimited
  ssize_t pids_val = 0;
  if (!pids_max_val(_pids, pids_val)) {
    return -1; // treat failure as unlimited
  }
  return pids_val;
}

static
bool pids_current_val(CgroupController* ctrl, size_t& result) {
  CONTAINER_READ_NUMBER_CHECKED(ctrl, "/pids.current", "Current number of tasks", result);
}

/* pids_current
 *
 * The number of tasks currently in the cgroup (and its descendants) of the process
 *
 * return:
 *    current number of tasks or -1 for not supported
 */
ssize_t CgroupV1Subsystem::pids_current() {
  if (_pids == nullptr) return -1; // treat as unlimited
  size_t pids_current = 0;
  if (!pids_current_val(_pids, pids_current)) {
    return -1; // treat as unlimited
  }
  return static_cast<ssize_t>(pids_current);
}
