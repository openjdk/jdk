/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

bool CgroupV1MemoryController::read_use_hierarchy_val(physical_memory_size_type& result) {
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/memory.use_hierarchy", "Use Hierarchy", result);
}

bool CgroupV1MemoryController::uses_mem_hierarchy() {
  physical_memory_size_type use_hierarchy = 0;
  return read_use_hierarchy_val(use_hierarchy) && use_hierarchy > 0;
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

bool CgroupV1MemoryController::read_memory_limit_val(physical_memory_size_type& result) {
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/memory.limit_in_bytes", "Memory Limit", result);
}

bool CgroupV1MemoryController::read_hierarchical_memory_limit_val(physical_memory_size_type& result) {
  CONTAINER_READ_NUMERICAL_KEY_VALUE_CHECKED(reader(), "/memory.stat",
                                             "hierarchical_memory_limit", "Hierarchical Memory Limit",
                                             result);
}

bool CgroupV1MemoryController::read_memory_limit_in_bytes(physical_memory_size_type upper_bound,
                                                          physical_memory_size_type& result) {
  physical_memory_size_type memlimit = 0;
  if (!read_memory_limit_val(memlimit)) {
    log_trace(os, container)("container memory limit failed, upper bound is " PHYS_MEM_TYPE_FORMAT, upper_bound);
    return false;
  }
  if (memlimit >= upper_bound) {
    physical_memory_size_type hierlimit = 0;
    if (uses_mem_hierarchy() && read_hierarchical_memory_limit_val(hierlimit) &&
        hierlimit < upper_bound) {
      log_trace(os, container)("Memory Limit is: " PHYS_MEM_TYPE_FORMAT, hierlimit);
      result = hierlimit;
    } else {
      // Exceeding physical memory is treated as unlimited. This implementation
      // caps it at host_mem since Cg v1 has no value to represent 'max'.
      log_trace(os, container)("container memory limit ignored: " PHYS_MEM_TYPE_FORMAT
                               ", upper bound is " PHYS_MEM_TYPE_FORMAT, memlimit, upper_bound);
      result = value_unlimited;
    }
  } else {
    result = memlimit;
  }
  return true;
}

bool CgroupV1MemoryController::read_mem_swap(physical_memory_size_type& result) {
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/memory.memsw.limit_in_bytes", "Memory and Swap Limit", result);
}

bool CgroupV1MemoryController::read_hierarchical_mem_swap_val(physical_memory_size_type& result) {
  CONTAINER_READ_NUMERICAL_KEY_VALUE_CHECKED(reader(), "/memory.stat",
                                             "hierarchical_memsw_limit", "Hierarchical Memory and Swap Limit",
                                             result);
}

/* memory_and_swap_limit_in_bytes
 *
 * Determine the memory and swap limit metric. Sets the 'result' reference to a positive limit value or
 * 'value_unlimited' (for unlimited).
 *
 * returns:
 *    * false if an error occurred. 'result' reference remains unchanged.
 *    * true if the limit value has been set in the 'result' reference
 */
bool CgroupV1MemoryController::memory_and_swap_limit_in_bytes(physical_memory_size_type upper_mem_bound,
                                                              physical_memory_size_type upper_swap_bound,
                                                              physical_memory_size_type& result) {
  physical_memory_size_type total_mem_swap = upper_mem_bound + upper_swap_bound;
  physical_memory_size_type memory_swap = 0;
  bool mem_swap_read_failed = false;
  if (!read_mem_swap(memory_swap)) {
    mem_swap_read_failed = true;
  }
  if (memory_swap >= total_mem_swap) {
    physical_memory_size_type hiermswlimit = 0;
    if (uses_mem_hierarchy() && read_hierarchical_mem_swap_val(hiermswlimit) &&
        hiermswlimit < total_mem_swap) {
      log_trace(os, container)("Memory and Swap Limit is: " PHYS_MEM_TYPE_FORMAT, hiermswlimit);
      memory_swap = hiermswlimit;
    } else {
      memory_swap = value_unlimited;
    }
  }
  if (memory_swap == value_unlimited) {
    log_trace(os, container)("Memory and Swap Limit is: Unlimited");
    result = value_unlimited;
    return true;
  }

  // If there is a swap limit, but swappiness == 0, reset the limit
  // to the memory limit. Do the same for cases where swap isn't
  // supported.
  physical_memory_size_type swappiness = 0;
  if (!read_mem_swappiness(swappiness)) {
    // assume no swap
    mem_swap_read_failed = true;
  }
  if (swappiness == 0 || mem_swap_read_failed) {
    physical_memory_size_type memlimit = value_unlimited;
    if (!read_memory_limit_in_bytes(upper_mem_bound, memlimit)) {
      return false;
    }
    if (memlimit == value_unlimited) {
      result = value_unlimited; // No memory limit, thus no swap limit
      return true;
    }
    if (mem_swap_read_failed) {
      log_trace(os, container)("Memory and Swap Limit has been reset to " PHYS_MEM_TYPE_FORMAT
                               " because swap is not supported", memlimit);
    } else {
      log_trace(os, container)("Memory and Swap Limit has been reset to " PHYS_MEM_TYPE_FORMAT
                               " because swappiness is 0", memlimit);
    }
    result = memlimit;
    return true;
  }
  result = memory_swap;
  return true;
}

static inline
bool memory_swap_usage_impl(CgroupController* ctrl, physical_memory_size_type& result) {
  CONTAINER_READ_NUMBER_CHECKED(ctrl, "/memory.memsw.usage_in_bytes", "mem swap usage", result);
}

bool CgroupV1MemoryController::memory_and_swap_usage_in_bytes(physical_memory_size_type upper_mem_bound,
                                                              physical_memory_size_type upper_swap_bound,
                                                              physical_memory_size_type& result) {
  physical_memory_size_type memory_sw_limit = value_unlimited;
  if (!memory_and_swap_limit_in_bytes(upper_mem_bound, upper_swap_bound, memory_sw_limit)) {
    return false;
  }
  physical_memory_size_type mem_limit_val = value_unlimited;
  physical_memory_size_type memory_limit = value_unlimited;
  if (read_memory_limit_in_bytes(upper_mem_bound, mem_limit_val)) {
    if (mem_limit_val != value_unlimited) {
      memory_limit = mem_limit_val;
    }
  }
  if (memory_sw_limit != value_unlimited && memory_limit != value_unlimited) {
    if (memory_limit < memory_sw_limit) {
      // swap allowed and > 0
      physical_memory_size_type swap_usage = 0;
      if (!memory_swap_usage_impl(reader(), swap_usage)) {
        return false;
      }
      result = swap_usage;
      return true;
    }
  }
  return memory_usage_in_bytes(result);
}

bool CgroupV1MemoryController::read_mem_swappiness(physical_memory_size_type& result) {
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/memory.swappiness", "Swappiness", result);
}

bool CgroupV1MemoryController::memory_soft_limit_val(physical_memory_size_type& result) {
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/memory.soft_limit_in_bytes", "Memory Soft Limit", result);
}

bool CgroupV1MemoryController::memory_soft_limit_in_bytes(physical_memory_size_type upper_bound,
                                                          physical_memory_size_type& result) {
  physical_memory_size_type mem_soft_limit = 0;
  if (!memory_soft_limit_val(mem_soft_limit)) {
    return false;
  }
  if (mem_soft_limit >= upper_bound) {
    log_trace(os, container)("Memory Soft Limit is: Unlimited");
    result = value_unlimited;
  } else {
    result = mem_soft_limit;
  }
  return true;
}

bool CgroupV1MemoryController::memory_throttle_limit_in_bytes(physical_memory_size_type& result) {
  // Log this string at trace level so as to make tests happy.
  log_trace(os, container)("Memory Throttle Limit is not supported.");
  return false;
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
  _memory = new CachingCgroupController<CgroupMemoryController, physical_memory_size_type>(memory);
  _cpu = new CachingCgroupController<CgroupCpuController, double>(cpu);
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

bool CgroupV1MemoryController::memory_usage_in_bytes(physical_memory_size_type& result) {
  physical_memory_size_type memory_usage = 0;
  if (!memory_usage_val(memory_usage)) {
    return false;
  }
  result = memory_usage;
  return true;
}

/* memory_usage_val
 *
 * Read the amount of used memory for this process into the passed in reference 'result'
 *
 * return:
 *    true when reading of the file was successful and 'result' was set appropriately
 *    false when reading of the file failed
 */
bool CgroupV1MemoryController::memory_usage_val(physical_memory_size_type& result) {
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/memory.usage_in_bytes", "Memory Usage", result);
}

bool CgroupV1MemoryController::memory_max_usage_val(physical_memory_size_type& result) {
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/memory.max_usage_in_bytes", "Maximum Memory Usage", result);
}

/* memory_max_usage_in_bytes
 *
 * Return the maximum amount of used memory for this process in the
 * result reference.
 *
 * return:
 *    true if the result reference has been set
 *    false otherwise (e.g. on error)
 */
bool CgroupV1MemoryController::memory_max_usage_in_bytes(physical_memory_size_type& result) {
  physical_memory_size_type memory_max_usage = 0;
  if (!memory_max_usage_val(memory_max_usage)) {
     return false;
  }
  result = memory_max_usage;
  return true;
}

bool CgroupV1MemoryController::rss_usage_in_bytes(physical_memory_size_type& result) {
  physical_memory_size_type rss = 0;

  if (!reader()->read_numerical_key_value("/memory.stat", "rss", rss)) {
    return false;
  }
  log_trace(os, container)("RSS usage is: " PHYS_MEM_TYPE_FORMAT, rss);
  result = rss;
  return true;
}

bool CgroupV1MemoryController::cache_usage_in_bytes(physical_memory_size_type& result) {
  physical_memory_size_type cache = 0;
  if (!reader()->read_numerical_key_value("/memory.stat", "cache", cache)) {
    return false;
  }
  log_trace(os, container)("Cache usage is: " PHYS_MEM_TYPE_FORMAT, cache);
  result = cache;
  return true;
}

bool CgroupV1MemoryController::kernel_memory_usage_val(physical_memory_size_type& result) {
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/memory.kmem.usage_in_bytes", "Kernel Memory Usage", result);
}

bool CgroupV1MemoryController::kernel_memory_usage_in_bytes(physical_memory_size_type& result) {
  physical_memory_size_type kmem_usage = 0;
  if (!kernel_memory_usage_val(kmem_usage)) {
    return false;
  }
  result = kmem_usage;
  return true;
}

bool CgroupV1MemoryController::kernel_memory_limit_val(physical_memory_size_type& result) {
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/memory.kmem.limit_in_bytes", "Kernel Memory Limit", result);
}

bool CgroupV1MemoryController::kernel_memory_limit_in_bytes(physical_memory_size_type upper_bound,
                                                            physical_memory_size_type& result) {
  physical_memory_size_type kmem_limit = 0;
  if (!kernel_memory_limit_val(kmem_limit)) {
    return false;
  }
  if (kmem_limit >= upper_bound) {
    kmem_limit = value_unlimited;
  }
  result = kmem_limit;
  return true;
}

bool CgroupV1MemoryController::kernel_memory_max_usage_val(physical_memory_size_type& result) {
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/memory.kmem.max_usage_in_bytes", "Maximum Kernel Memory Usage", result);
}

bool CgroupV1MemoryController::kernel_memory_max_usage_in_bytes(physical_memory_size_type& result) {
  physical_memory_size_type kmem_max_usage = 0;
  if (!kernel_memory_max_usage_val(kmem_max_usage)) {
    return false;
  }
  result = kmem_max_usage;
  return true;
}

void CgroupV1MemoryController::print_version_specific_info(outputStream* st, physical_memory_size_type mem_bound) {
  MetricResult kmem_usage;
  physical_memory_size_type temp = 0;
  if (kernel_memory_usage_in_bytes(temp)) {
    kmem_usage.set_value(temp);
  }
  MetricResult kmem_limit;
  temp = value_unlimited;
  if (kernel_memory_limit_in_bytes(mem_bound, temp)) {
    kmem_limit.set_value(temp);
  }
  MetricResult kmem_max_usage;
  temp = 0;
  if (kernel_memory_max_usage_in_bytes(temp)) {
    kmem_max_usage.set_value(temp);
  }

  OSContainer::print_container_helper(st, kmem_limit, "kernel_memory_limit");
  OSContainer::print_container_helper(st, kmem_usage, "kernel_memory_usage");
  OSContainer::print_container_helper(st, kmem_max_usage, "kernel_memory_max_usage");
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
 * a process is guaranteed to run in the provided
 * result reference.
 *
 * return:
 *   true if the value was set in the result reference
 *   false on failure to read the number from the file
 *   and the result reference has not been touched.
 */
bool CgroupV1CpuController::cpu_quota(int& result) {
  uint64_t quota = 0;

  // intentionally not using the macro so as to not log a
  // negative value as a large unsiged int
  if (!reader()->read_number("/cpu.cfs_quota_us", quota)) {
    log_trace(os, container)("CPU Quota failed");
    return false;
  }
  // cast to int since the read value might be negative
  // and we want to avoid logging -1 as a large unsigned value.
  int quota_int = static_cast<int>(quota);
  log_trace(os, container)("CPU Quota is: %d", quota_int);
  result = quota_int;
  return true;
}

bool CgroupV1CpuController::cpu_period_val(uint64_t& result) {
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/cpu.cfs_period_us", "CPU Period", result);
}

bool CgroupV1CpuController::cpu_period(int& result) {
  uint64_t period = value_unlimited;
  if (!cpu_period_val(period)) {
    return false;
  }
  result = static_cast<int>(period);
  return true;
}

bool CgroupV1CpuController::cpu_shares_val(uint64_t& result) {
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/cpu.shares", "CPU Shares", result);
}

/* cpu_shares
 *
 * Return the amount of cpu shares available to the process
 *    - Share number (typically a number relative to 1024)
 *    - (2048 typically expresses 2 CPUs worth of processing)
 *
 * return:
 *    false on error
 *    true if the result has been set in the result reference
 */
bool CgroupV1CpuController::cpu_shares(int& result) {
  uint64_t shares = 0;
  if (!cpu_shares_val(shares)) {
    return false;
  }
  int shares_int = static_cast<int>(shares);
  // Convert 1024 to no shares setup (-1)
  if (shares_int == 1024) {
    shares_int = -1;
  }

  result = shares_int;
  return true;
}

bool CgroupV1CpuacctController::cpu_usage_in_micros_val(uint64_t& result) {
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/cpuacct.usage", "CPU Usage", result);
}

bool CgroupV1CpuacctController::cpu_usage_in_micros(uint64_t& result) {
  uint64_t cpu_usage = 0;
  if (!cpu_usage_in_micros_val(cpu_usage)) {
    return false;
  }
  // Output is in nanoseconds, convert to microseconds.
  result = static_cast<uint64_t>(cpu_usage / 1000);
  return true;
}

static
bool pids_max_val(CgroupController* ctrl, uint64_t& result) {
  CONTAINER_READ_NUMBER_CHECKED_MAX(ctrl, "/pids.max", "Maximum number of tasks", result);
}

/* pids_max
 *
 * Return the maximum number of tasks available to the process
 * in the passed result reference (might be value_unlimited).
 *
 * return:
 *    false on error
 *    true when the result reference has been appropriately set
 */
bool CgroupV1Subsystem::pids_max(uint64_t& result) {
  if (_pids == nullptr) return false;
  uint64_t pids_val = 0;
  if (!pids_max_val(_pids, pids_val)) {
    return false;
  }
  result = pids_val;
  return true;
}

static
bool pids_current_val(CgroupController* ctrl, uint64_t& result) {
  CONTAINER_READ_NUMBER_CHECKED(ctrl, "/pids.current", "Current number of tasks", result);
}

/* pids_current
 *
 * The number of tasks currently in the cgroup (and its descendants) of the process
 *
 * return:
 *    true if the current number of tasks has been set in the result reference
 *    false if an error occurred
 */
bool CgroupV1Subsystem::pids_current(uint64_t& result) {
  if (_pids == nullptr) return false;
  uint64_t pids_current = 0;
  if (!pids_current_val(_pids, pids_current)) {
    return false;
  }
  result = pids_current;
  return true;
}
