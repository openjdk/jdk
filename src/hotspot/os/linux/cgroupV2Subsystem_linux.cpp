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

#include "cgroupUtil_linux.hpp"
#include "cgroupV2Subsystem_linux.hpp"

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

static
bool read_cpu_shares_value(CgroupV2Controller* ctrl, size_t& value) {
  CONTAINER_READ_NUMBER_CHECKED(ctrl, "/cpu.weight", "Raw value for CPU Shares", value);
}

/* cpu_shares
 *
 * Return the amount of cpu shares available to the process
 *
 * return:
 *    Share number (typically a number relative to 1024)
 *                 (2048 typically expresses 2 CPUs worth of processing)
 *    -1 for no share setup (or on error)
 */
int CgroupV2CpuController::cpu_shares() {
  size_t shares = 0;
  bool is_ok = read_cpu_shares_value(reader(), shares);
  if (!is_ok) {
    return -1; // treat as unlimited
  }
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
 */
int CgroupV2CpuController::cpu_quota() {
  ssize_t quota_val = 0;
  bool is_ok = reader()->read_numerical_tuple_value("/cpu.max", true /* use_first */, quota_val);
  if (!is_ok) {
    return -1; // treat as no limit
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

bool CgroupV2CpuController::cpu_period(ssize_t& value) {
  bool is_ok = reader()->read_numerical_tuple_value("/cpu.max", false /* use_first */, value);
  if (!is_ok) {
    log_trace(os, container)("CPU Period failed: %d", OSCONTAINER_ERROR);
    return false;
  }
  log_trace(os, container)("CPU Period is: %zd", value);
  return true;
}

int CgroupV2CpuController::cpu_period() {
  ssize_t period = 0;
  if (!cpu_period(period)) {
    return -1; // treat as unlimited
  }
  return static_cast<int>(period);
}

bool CgroupV2CpuController::cpu_usage_in_micros(size_t& value) {
  bool is_ok = reader()->read_numerical_key_value("/cpu.stat", "usage_usec", value);
  if (!is_ok) {
    log_trace(os, container)("CPU Usage failed: %d", OSCONTAINER_ERROR);
    return false;
  }
  log_trace(os, container)("CPU Usage is: %zu", value);
  return true;
}

ssize_t CgroupV2CpuController::cpu_usage_in_micros() {
  size_t usage_micros = 0;
  if (!cpu_usage_in_micros(usage_micros)) {
    return -1; // usage value error
  }
  return static_cast<ssize_t>(usage_micros);
}

ssize_t CgroupV2MemoryController::memory_usage_in_bytes() {
  size_t mem_usage = 0;
  if (!memory_usage_in_bytes(mem_usage)) {
    return -1; // usage value error
  }
  return static_cast<ssize_t>(mem_usage);
}

/* memory_usage_in_bytes
 *
 * read the amount of used memory used by this cgroup and descendents
 * into the passed in 'value' reference.
 *
 * return:
 *    false on failure, true otherwise.
 */
bool CgroupV2MemoryController::memory_usage_in_bytes(size_t& value) {
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/memory.current", "Memory Usage", value);
}

ssize_t CgroupV2MemoryController::memory_soft_limit_in_bytes(size_t host_mem) {
  ssize_t result = 0;
  if (!memory_soft_limit_in_bytes(host_mem, result)) {
    return -1; // treat as unlimited
  }
  return result;
}

bool CgroupV2MemoryController::memory_soft_limit_in_bytes(size_t phys_mem, ssize_t& value) {
  CONTAINER_READ_NUMBER_CHECKED_MAX(reader(), "/memory.low", "Memory Soft Limit", value);
}

ssize_t CgroupV2MemoryController::memory_throttle_limit_in_bytes() {
  ssize_t result = 0;
  if (!memory_throttle_limit_in_bytes(result)) {
    return -1; // treat as unlimited
  }
  return result;
}

bool CgroupV2MemoryController::memory_throttle_limit_in_bytes(ssize_t& value) {
  CONTAINER_READ_NUMBER_CHECKED_MAX(reader(), "/memory.high", "Memory Throttle Limit", value);
}

ssize_t CgroupV2MemoryController::memory_max_usage_in_bytes() {
  size_t max_usage = 0;
  if (!memory_max_usage_in_bytes(max_usage)) {
    return -1; // usage value error
  }
  return static_cast<ssize_t>(max_usage);
}

bool CgroupV2MemoryController::memory_max_usage_in_bytes(size_t& value) {
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/memory.peak", "Maximum Memory Usage", value);
}

ssize_t CgroupV2MemoryController::rss_usage_in_bytes() {
  size_t rss_usage = 0;
  if (!rss_usage_in_bytes(rss_usage)) {
    return -1; // usage value error
  }
  return static_cast<ssize_t>(rss_usage);
}

bool CgroupV2MemoryController::rss_usage_in_bytes(size_t& value) {
  bool is_ok = reader()->read_numerical_key_value("/memory.stat", "anon", value);
  if (!is_ok) {
    return false;
  }
  log_trace(os, container)("RSS usage is: %zu", value);
  return true;
}

ssize_t CgroupV2MemoryController::cache_usage_in_bytes() {
  size_t cache_usage = 0;
  if (!cache_usage_in_bytes(cache_usage)) {
    return -1; // usage value error
  }
  return static_cast<ssize_t>(cache_usage);
}

bool CgroupV2MemoryController::cache_usage_in_bytes(size_t& value) {
  bool is_ok = reader()->read_numerical_key_value("/memory.stat", "file", value);
  if (!is_ok) {
    return false;
  }
  log_trace(os, container)("Cache usage is: %zu", value);
  return true;
}

ssize_t CgroupV2MemoryController::memory_and_swap_limit_in_bytes(size_t host_mem, size_t host_swap) {
  ssize_t mem_swap_limit = 0;
  if (!memory_and_swap_limit_in_bytes(host_mem, host_swap, mem_swap_limit)) {
    return -1; // treat as unlimited;
  }
  return mem_swap_limit;
}

// Note that for cgroups v2 the actual limits set for swap and
// memory live in two different files, memory.swap.max and memory.max
// respectively. In order to properly report a cgroup v1 like
// compound value we need to sum the two values. Setting a swap limit
// without also setting a memory limit is not allowed.
bool CgroupV2MemoryController::memory_and_swap_limit_in_bytes(size_t phys_mem,
                                                              size_t host_swap /* unused in cg v2 */,
                                                              ssize_t& result) {
  ssize_t swap_limit = -1;
  bool is_ok = reader()->read_number_handle_max("/memory.swap.max", swap_limit);
  if (!is_ok) {
    // Some container tests rely on this trace logging to happen.
    log_trace(os, container)("Swap Limit failed: %d", OSCONTAINER_ERROR);
    // swap disabled at kernel level, treat it as no swap
    return read_memory_limit_in_bytes(phys_mem, result);
  }
  log_trace(os, container)("Swap Limit is: %zd", swap_limit);
  if (swap_limit >= 0) {
    ssize_t memory_limit = -1;
    if (read_memory_limit_in_bytes(phys_mem, memory_limit)) {
      assert(memory_limit >= 0, "swap limit without memory limit?");
      result = memory_limit + swap_limit;
      return true;
    }
  }
  log_trace(os, container)("Memory and Swap Limit is: %zd", swap_limit);
  result = swap_limit;
  return true;
}

// memory.swap.current : total amount of swap currently used by the cgroup and its descendants
static
bool memory_swap_current_value(CgroupV2Controller* ctrl, size_t& result) {
  CONTAINER_READ_NUMBER_CHECKED(ctrl, "/memory.swap.current", "Swap currently used", result);
}

ssize_t CgroupV2MemoryController::memory_and_swap_usage_in_bytes(size_t host_mem, size_t host_swap) {
  size_t memory_swap_usage = 0;
  if (!memory_and_swap_usage_in_bytes(host_mem, host_swap, memory_swap_usage)) {
    return -1; // usage value error
  }
  return static_cast<ssize_t>(memory_swap_usage);
}

bool CgroupV2MemoryController::memory_and_swap_usage_in_bytes(size_t host_mem, size_t host_swap, size_t& result) {
  size_t memory_usage = 0;
  bool is_ok = memory_usage_in_bytes(memory_usage);
  if (!is_ok) {
     return false;
  }
  size_t swap_current = 0;
  is_ok = memory_swap_current_value(reader(), swap_current);
  if (!is_ok) {
    result = memory_usage; // treat as no swap usage
    return true;
  }
  result = memory_usage + swap_current;
  return true;
}

static
bool memory_limit_value(CgroupV2Controller* ctrl, ssize_t& result) {
  CONTAINER_READ_NUMBER_CHECKED_MAX(ctrl, "/memory.max", "Memory Limit", result);
}

ssize_t CgroupV2MemoryController::read_memory_limit_in_bytes(size_t host_mem) {
  ssize_t memory_limit = 0;
  if (!read_memory_limit_in_bytes(host_mem, memory_limit)) {
    return -1; // treat as unlimited
  }
  return memory_limit;
}

/* read_memory_limit_in_bytes
 *
 * Calculate the limit of available memory for this process. The result will be
 * set in the 'result' variable if the function returns true.
 *
 * return:
 *    true when the limit could be read correctly.
 *    false in case of any error.
 */
bool CgroupV2MemoryController::read_memory_limit_in_bytes(size_t phys_mem, ssize_t& result) {
  ssize_t limit = -1; // default unlimited
  bool is_ok = memory_limit_value(reader(), limit);
  if (!is_ok) {
    log_trace(os, container)("container memory limit failed, using host value %zu",
                              phys_mem);
    return false;
  }
  size_t read_limit = static_cast<size_t>(limit);
  ssize_t orig_limit = limit;
  bool exceeds_physical_mem = false;
  if (read_limit >= phys_mem) {
    exceeds_physical_mem = true;
    limit = -1; // reset limit
  }
  if (log_is_enabled(Trace, os, container)) {
    if (limit == -1) {
      log_trace(os, container)("Memory Limit is: Unlimited");
    } else {
      log_trace(os, container)("Memory Limit is: %zd", limit);
    }
    if (orig_limit < 0 || exceeds_physical_mem) {
        const char* reason;
        if (orig_limit == -1) {
          reason = "unlimited";
        } else {
          assert(read_limit >= phys_mem, "Expected mem limit to exceed host memory");
          reason = "ignored";
        }
        log_trace(os, container)("container memory limit %s: %zd, using host value %zu",
                                 reason, orig_limit, phys_mem);
    }
  }
  result = limit;
  return true;
}

static
bool memory_swap_limit_value(CgroupV2Controller* ctrl, ssize_t& value) {
  CONTAINER_READ_NUMBER_CHECKED_MAX(ctrl, "/memory.swap.max", "Swap Limit", value);
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

void CgroupV2MemoryController::print_version_specific_info(outputStream* st, size_t phys_mem) {
  size_t swap_current_val = 0;
  ssize_t swap_current = -1;
  if (memory_swap_current_value(reader(), swap_current_val)) {
    swap_current = static_cast<ssize_t>(swap_current_val);
  }
  ssize_t swap_limit = -1;
  memory_swap_limit_value(reader(), swap_limit); // use default of -1 if we have a failure

  OSContainer::print_container_helper(st, swap_current, "memory_swap_current_in_bytes", true /* is_usage */);
  OSContainer::print_container_helper(st, swap_limit, "memory_swap_max_limit_in_bytes", false /* is_usage */);
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
 * Calculate the maximum number of tasks available to the process. Set the
 * value in the passed in 'value' reference. The value might be -1 when
 * there is no limit.
 *
 * return:
 *    true if the value has been set appropriately
 *    false if there was an error
 */
bool CgroupV2Subsystem::pids_max(ssize_t& value) {
  CONTAINER_READ_NUMBER_CHECKED_MAX(unified(), "/pids.max", "Maximum number of tasks", value);
}

ssize_t CgroupV2Subsystem::pids_max() {
  ssize_t max_pids = 0;
  if (!pids_max(max_pids)) {
    return -1; // treat as unlimited
  }
  return max_pids;
}

/* pids_current
 *
 * The number of tasks currently in the cgroup (and its descendants) of the process. Set
 * in the passed in 'value' reference.
 *
 * return:
 *    true on success
 *    false when there was an error
 */
bool CgroupV2Subsystem::pids_current(size_t& value) {
  CONTAINER_READ_NUMBER_CHECKED(unified(), "/pids.current", "Current number of tasks", value);
}

ssize_t CgroupV2Subsystem::pids_current() {
  size_t pids_curr = 0;
  if (!pids_current(pids_curr)) {
    return -1; // treat as unlimited
  }
  return static_cast<ssize_t>(pids_curr);
}
