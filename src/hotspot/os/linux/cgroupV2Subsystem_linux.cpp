/*
 * Copyright (c) 2020, 2025, Red Hat Inc.
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "cgroupV2Subsystem_linux.hpp"
#include "cgroupUtil_linux.hpp"

// Constructor
CgroupV2Controller::CgroupV2Controller(char* mount_path,
                                       char *cgroup_path,
                                       bool ro) :  _read_only(ro),
                                                   _path(construct_path(mount_path, cgroup_path)) {
  _cgroup_path = os::strdup(cgroup_path);
  _mount_point = os::strdup(mount_path);
}
// Shallow copy constructor
CgroupV2Controller::CgroupV2Controller(const CgroupV2Controller& o) :
                                            _read_only(o._read_only),
                                            _path(o._path) {
  _cgroup_path = o._cgroup_path;
  _mount_point = o._mount_point;
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
int CgroupV2CpuController::cpu_shares() {
  julong shares;
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/cpu.weight", "Raw value for CPU Shares", shares);
  int shares_int = (int)shares;
  // Convert default value of 100 to no shares setup
  if (shares_int == 100) {
    log_debug(os, container)("CPU Shares is: %d", -1);
    return -1;
  }

  // CPU shares (OCI) value needs to get translated into
  // a proper Cgroups v2 value. See:
  // https://github.com/containers/crun/blob/master/crun.1.md#cpu-controller
  //
  // Use the inverse of (x == OCI value, y == cgroupsv2 value):
  // ((262142 * y - 1)/9999) + 2 = x
  //
  int x = 262142 * shares_int - 1;
  double frac = x/9999.0;
  x = ((int)frac) + 2;
  log_trace(os, container)("Scaled CPU shares value is: %d", x);
  // Since the scaled value is not precise, return the closest
  // multiple of PER_CPU_SHARES for a more conservative mapping
  if ( x <= PER_CPU_SHARES ) {
     // will always map to 1 CPU
     log_debug(os, container)("CPU Shares is: %d", x);
     return x;
  }
  int f = x/PER_CPU_SHARES;
  int lower_multiple = f * PER_CPU_SHARES;
  int upper_multiple = (f + 1) * PER_CPU_SHARES;
  int distance_lower = MAX2(lower_multiple, x) - MIN2(lower_multiple, x);
  int distance_upper = MAX2(upper_multiple, x) - MIN2(upper_multiple, x);
  x = distance_lower <= distance_upper ? lower_multiple : upper_multiple;
  log_trace(os, container)("Closest multiple of %d of the CPU Shares value is: %d", PER_CPU_SHARES, x);
  log_debug(os, container)("CPU Shares is: %d", x);
  return x;
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
int CgroupV2CpuController::cpu_quota() {
  jlong quota_val;
  bool is_ok = reader()->read_numerical_tuple_value("/cpu.max", true /* use_first */, &quota_val);
  if (!is_ok) {
    return OSCONTAINER_ERROR;
  }
  int limit = (int)quota_val;
  log_trace(os, container)("CPU Quota is: %d", limit);
  return limit;
}

// Constructor
CgroupV2Subsystem::CgroupV2Subsystem(CgroupV2MemoryController * memory,
                                     CgroupV2CpuController* cpu,
                                     CgroupV2CpuacctController* cpuacct,
                                     CgroupV2Controller unified) :
                                     _unified(unified) {
  CgroupUtil::adjust_controller(memory);
  CgroupUtil::adjust_controller(cpu);
  _memory = new CachingCgroupController<CgroupMemoryController>(memory);
  _cpu = new CachingCgroupController<CgroupCpuController>(cpu);
  _cpuacct = cpuacct;
}

bool CgroupV2Subsystem::is_containerized() {
  return _unified.is_read_only() &&
         _memory->controller()->is_read_only() &&
         _cpu->controller()->is_read_only();
}

char* CgroupV2Subsystem::cpu_cpuset_cpus() {
  char cpus[1024];
  CONTAINER_READ_STRING_CHECKED(unified(), "/cpuset.cpus", "cpuset.cpus", cpus, 1024);
  return os::strdup(cpus);
}

char* CgroupV2Subsystem::cpu_cpuset_memory_nodes() {
  char mems[1024];
  CONTAINER_READ_STRING_CHECKED(unified(), "/cpuset.mems", "cpuset.mems", mems, 1024);
  return os::strdup(mems);
}

int CgroupV2CpuController::cpu_period() {
  jlong period_val;
  bool is_ok = reader()->read_numerical_tuple_value("/cpu.max", false /* use_first */, &period_val);
  if (!is_ok) {
    log_trace(os, container)("CPU Period failed: %d", OSCONTAINER_ERROR);
    return OSCONTAINER_ERROR;
  }
  int period = (int)period_val;
  log_trace(os, container)("CPU Period is: %d", period);
  return period;
}

jlong CgroupV2CpuController::cpu_usage_in_micros() {
  julong cpu_usage;
  bool is_ok = reader()->read_numerical_key_value("/cpu.stat", "usage_usec", &cpu_usage);
  if (!is_ok) {
    log_trace(os, container)("CPU Usage failed: %d", OSCONTAINER_ERROR);
    return OSCONTAINER_ERROR;
  }
  log_trace(os, container)("CPU Usage is: " JULONG_FORMAT, cpu_usage);
  return (jlong)cpu_usage;
}

/* memory_usage_in_bytes
 *
 * Return the amount of used memory used by this cgroup and descendents
 *
 * return:
 *    memory usage in bytes or
 *    -1 for unlimited
 *    OSCONTAINER_ERROR for not supported
 */
jlong CgroupV2MemoryController::memory_usage_in_bytes() {
  julong memusage;
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/memory.current", "Memory Usage", memusage);
  return (jlong)memusage;
}

jlong CgroupV2MemoryController::memory_soft_limit_in_bytes(julong phys_mem) {
  jlong mem_soft_limit;
  CONTAINER_READ_NUMBER_CHECKED_MAX(reader(), "/memory.low", "Memory Soft Limit", mem_soft_limit);
  return mem_soft_limit;
}

jlong CgroupV2MemoryController::memory_throttle_limit_in_bytes() {
  jlong mem_throttle_limit;
  CONTAINER_READ_NUMBER_CHECKED_MAX(reader(), "/memory.high", "Memory Throttle Limit", mem_throttle_limit);
  return mem_throttle_limit;
}

jlong CgroupV2MemoryController::memory_max_usage_in_bytes() {
  julong mem_max_usage;
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/memory.peak", "Maximum Memory Usage", mem_max_usage);
  return mem_max_usage;
}

jlong CgroupV2MemoryController::rss_usage_in_bytes() {
  julong rss;
  bool is_ok = reader()->read_numerical_key_value("/memory.stat", "anon", &rss);
  if (!is_ok) {
    return OSCONTAINER_ERROR;
  }
  log_trace(os, container)("RSS usage is: " JULONG_FORMAT, rss);
  return (jlong)rss;
}

jlong CgroupV2MemoryController::cache_usage_in_bytes() {
  julong cache;
  bool is_ok = reader()->read_numerical_key_value("/memory.stat", "file", &cache);
  if (!is_ok) {
    return OSCONTAINER_ERROR;
  }
  log_trace(os, container)("Cache usage is: " JULONG_FORMAT, cache);
  return (jlong)cache;
}

// Note that for cgroups v2 the actual limits set for swap and
// memory live in two different files, memory.swap.max and memory.max
// respectively. In order to properly report a cgroup v1 like
// compound value we need to sum the two values. Setting a swap limit
// without also setting a memory limit is not allowed.
jlong CgroupV2MemoryController::memory_and_swap_limit_in_bytes(julong phys_mem,
                                                               julong host_swap /* unused in cg v2 */) {
  jlong swap_limit;
  bool is_ok = reader()->read_number_handle_max("/memory.swap.max", &swap_limit);
  if (!is_ok) {
    // Some container tests rely on this trace logging to happen.
    log_trace(os, container)("Swap Limit failed: %d", OSCONTAINER_ERROR);
    // swap disabled at kernel level, treat it as no swap
    return read_memory_limit_in_bytes(phys_mem);
  }
  log_trace(os, container)("Swap Limit is: " JLONG_FORMAT, swap_limit);
  if (swap_limit >= 0) {
    jlong memory_limit = read_memory_limit_in_bytes(phys_mem);
    assert(memory_limit >= 0, "swap limit without memory limit?");
    return memory_limit + swap_limit;
  }
  log_trace(os, container)("Memory and Swap Limit is: " JLONG_FORMAT, swap_limit);
  return swap_limit;
}

// memory.swap.current : total amount of swap currently used by the cgroup and its descendants
static
jlong memory_swap_current_value(CgroupV2Controller* ctrl) {
  julong swap_current;
  CONTAINER_READ_NUMBER_CHECKED(ctrl, "/memory.swap.current", "Swap currently used", swap_current);
  return (jlong)swap_current;
}

jlong CgroupV2MemoryController::memory_and_swap_usage_in_bytes(julong host_mem, julong host_swap) {
  jlong memory_usage = memory_usage_in_bytes();
  if (memory_usage >= 0) {
      jlong swap_current = memory_swap_current_value(reader());
      return memory_usage + (swap_current >= 0 ? swap_current : 0);
  }
  return memory_usage; // not supported or unlimited case
}

static
jlong memory_limit_value(CgroupV2Controller* ctrl) {
  jlong memory_limit;
  CONTAINER_READ_NUMBER_CHECKED_MAX(ctrl, "/memory.max", "Memory Limit", memory_limit);
  return memory_limit;
}

/* read_memory_limit_in_bytes
 *
 * Return the limit of available memory for this process.
 *
 * return:
 *    memory limit in bytes or
 *    -1 for unlimited, OSCONTAINER_ERROR for an error
 */
jlong CgroupV2MemoryController::read_memory_limit_in_bytes(julong phys_mem) {
  jlong limit = memory_limit_value(reader());
  if (log_is_enabled(Trace, os, container)) {
    if (limit == -1) {
      log_trace(os, container)("Memory Limit is: Unlimited");
    } else {
      log_trace(os, container)("Memory Limit is: " JLONG_FORMAT, limit);
    }
  }
  if (log_is_enabled(Debug, os, container)) {
    julong read_limit = (julong)limit; // avoid signed/unsigned compare
    if (limit < 0 || read_limit >= phys_mem) {
      const char* reason;
      if (limit == -1) {
        reason = "unlimited";
      } else if (limit == OSCONTAINER_ERROR) {
        reason = "failed";
      } else {
        assert(read_limit >= phys_mem, "Expected mem limit to exceed host memory");
        reason = "ignored";
      }
      log_debug(os, container)("container memory limit %s: " JLONG_FORMAT ", using host value " JLONG_FORMAT,
                               reason, limit, phys_mem);
    }
  }
  return limit;
}

static
jlong memory_swap_limit_value(CgroupV2Controller* ctrl) {
  jlong swap_limit;
  CONTAINER_READ_NUMBER_CHECKED_MAX(ctrl, "/memory.swap.max", "Swap Limit", swap_limit);
  return swap_limit;
}

void CgroupV2Controller::set_subsystem_path(const char* cgroup_path) {
  if (_cgroup_path != nullptr) {
    os::free(_cgroup_path);
  }
  _cgroup_path = os::strdup(cgroup_path);
  if (_path != nullptr) {
    os::free(_path);
  }
  _path = construct_path(_mount_point, cgroup_path);
}

// For cgv2 we only need hierarchy walk if the cgroup path isn't '/' (root)
bool CgroupV2Controller::needs_hierarchy_adjustment() {
  return strcmp(_cgroup_path, "/") != 0;
}

void CgroupV2MemoryController::print_version_specific_info(outputStream* st, julong phys_mem) {
  jlong swap_current = memory_swap_current_value(reader());
  jlong swap_limit = memory_swap_limit_value(reader());

  OSContainer::print_container_helper(st, swap_current, "memory_swap_current_in_bytes");
  OSContainer::print_container_helper(st, swap_limit, "memory_swap_max_limit_in_bytes");
}

char* CgroupV2Controller::construct_path(char* mount_path, const char* cgroup_path) {
  stringStream ss;
  ss.print_raw(mount_path);
  if (strcmp(cgroup_path, "/") != 0) {
    ss.print_raw(cgroup_path);
  }
  return os::strdup(ss.base());
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
jlong CgroupV2Subsystem::pids_max() {
  jlong pids_max;
  CONTAINER_READ_NUMBER_CHECKED_MAX(unified(), "/pids.max", "Maximum number of tasks", pids_max);
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
jlong CgroupV2Subsystem::pids_current() {
  julong pids_current;
  CONTAINER_READ_NUMBER_CHECKED(unified(), "/pids.current", "Current number of tasks", pids_current);
  return pids_current;
}
