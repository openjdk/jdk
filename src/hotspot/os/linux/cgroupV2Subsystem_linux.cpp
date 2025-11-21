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

#include <math.h>

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
bool read_cpu_shares_value(CgroupV2Controller* ctrl, uint64_t& value) {
  CONTAINER_READ_NUMBER_CHECKED(ctrl, "/cpu.weight", "Raw value for CPU Shares", value);
}

/* cpu_shares
 *
 * Return the amount of cpu shares available to the process in the
 * 'result' reference.
 *
 *    Share number (typically a number relative to 1024)
 *                 (2048 typically expresses 2 CPUs worth of processing)
 *
 * return:
 *    true if the result reference got updated
 *    false if there was an error
 */
bool CgroupV2CpuController::cpu_shares(int& result) {
  uint64_t shares = 0;
  bool is_ok = read_cpu_shares_value(reader(), shares);
  if (!is_ok) {
    return false;
  }
  int shares_int = static_cast<int>(shares);
  // Convert default value of 100 to no shares setup
  if (shares_int == 100) {
    log_debug(os, container)("CPU Shares is: unlimited");
    result = -1;
    return true;
  }
  // cg v2 values must be in range [1-10000]
  assert(shares_int >= 1 && shares_int <= 10000, "invariant");

  // CPU shares (OCI) value needs to get translated into
  // a proper Cgroups v2 value. See:
  // https://github.com/containers/crun/blob/1.24/crun.1.md#cpu-controller
  //
  // Use the inverse of (x == OCI value, y == cgroupsv2 value):
  // y = 10^(log2(x)^2/612 + 125/612 * log2(x) - 7.0/34.0)
  //
  // By re-arranging it to the standard quadratic form:
  // log2(x)^2 + 125 * log2(x) - (126 + 612 * log_10(y)) = 0
  //
  // Therefore, log2(x) = (-125 + sqrt( 125^2 - 4 * (-(126 + 612 * log_10(y)))))/2
  //
  // As a result we have the inverse (we can discount substraction of the
  // square root value since those values result in very small numbers and the
  // cpu shares values - OCI - are in range [2,262144]):
  //
  // x = 2^((-125 + sqrt(16129 + 2448* log10(y)))/2)
  //
  double log_multiplicand = log10(shares_int);
  double discriminant = 16129 + 2448 * log_multiplicand;
  double square_root = sqrt(discriminant);
  double exponent = (-125 + square_root)/2;
  double scaled_val = pow(2, exponent);
  int x = (int) scaled_val;
  log_trace(os, container)("Scaled CPU shares value is: %d", x);
  // Since the scaled value is not precise, return the closest
  // multiple of PER_CPU_SHARES for a more conservative mapping
  if ( x <= PER_CPU_SHARES ) {
     // Don't do the multiples of PER_CPU_SHARES mapping since we
     // have a value <= PER_CPU_SHARES
     log_debug(os, container)("CPU Shares is: %d", x);
     result = x;
     return true;
  }
  int f = x/PER_CPU_SHARES;
  int lower_multiple = f * PER_CPU_SHARES;
  int upper_multiple = (f + 1) * PER_CPU_SHARES;
  int distance_lower = MAX2(lower_multiple, x) - MIN2(lower_multiple, x);
  int distance_upper = MAX2(upper_multiple, x) - MIN2(upper_multiple, x);
  x = distance_lower <= distance_upper ? lower_multiple : upper_multiple;
  log_trace(os, container)("Closest multiple of %d of the CPU Shares value is: %d", PER_CPU_SHARES, x);
  log_debug(os, container)("CPU Shares is: %d", x);
  result = x;
  return true;
}

/* cpu_quota
 *
 * Return the number of microseconds per period
 * process is guaranteed to run in the passed in 'result' reference.
 *
 * return:
 *    true if the result reference has been set
 *    false on error
 */
bool CgroupV2CpuController::cpu_quota(int& result) {
  uint64_t quota_val = 0;
  if (!reader()->read_numerical_tuple_value("/cpu.max", true /* use_first */, quota_val)) {
    return false;
  }
  int limit = -1;
  // The read first tuple value might be 'max' which maps
  // to value_unlimited. Keep that at -1;
  if (quota_val != value_unlimited) {
    limit = static_cast<int>(quota_val);
  }
  log_trace(os, container)("CPU Quota is: %d", limit);
  result = limit;
  return true;
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

bool CgroupV2CpuController::cpu_period(int& result) {
  uint64_t cpu_period = 0;
  if (!reader()->read_numerical_tuple_value("/cpu.max", false /* use_first */, cpu_period)) {
    log_trace(os, container)("CPU Period failed");
    return false;
  }
  int period_int = static_cast<int>(cpu_period);
  log_trace(os, container)("CPU Period is: %d", period_int);
  result = period_int;
  return true;
}

bool CgroupV2CpuController::cpu_usage_in_micros(uint64_t& value) {
  bool is_ok = reader()->read_numerical_key_value("/cpu.stat", "usage_usec", value);
  if (!is_ok) {
    log_trace(os, container)("CPU Usage failed");
    return false;
  }
  log_trace(os, container)("CPU Usage is: " UINT64_FORMAT, value);
  return true;
}

/* memory_usage_in_bytes
 *
 * read the amount of used memory used by this cgroup and descendents
 * into the passed in 'value' reference.
 *
 * return:
 *    false on failure, true otherwise.
 */
bool CgroupV2MemoryController::memory_usage_in_bytes(physical_memory_size_type& value) {
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/memory.current", "Memory Usage", value);
}

bool CgroupV2MemoryController::memory_soft_limit_in_bytes(physical_memory_size_type upper_bound,
                                                          physical_memory_size_type& value) {
  CONTAINER_READ_NUMBER_CHECKED_MAX(reader(), "/memory.low", "Memory Soft Limit", value);
}

bool CgroupV2MemoryController::memory_throttle_limit_in_bytes(physical_memory_size_type& value) {
  CONTAINER_READ_NUMBER_CHECKED_MAX(reader(), "/memory.high", "Memory Throttle Limit", value);
}

bool CgroupV2MemoryController::memory_max_usage_in_bytes(physical_memory_size_type& value) {
  CONTAINER_READ_NUMBER_CHECKED(reader(), "/memory.peak", "Maximum Memory Usage", value);
}

bool CgroupV2MemoryController::rss_usage_in_bytes(physical_memory_size_type& value) {
  if (!reader()->read_numerical_key_value("/memory.stat", "anon", value)) {
    return false;
  }
  log_trace(os, container)("RSS usage is: " PHYS_MEM_TYPE_FORMAT, value);
  return true;
}

bool CgroupV2MemoryController::cache_usage_in_bytes(physical_memory_size_type& value) {
  if (!reader()->read_numerical_key_value("/memory.stat", "file", value)) {
    return false;
  }
  log_trace(os, container)("Cache usage is: " PHYS_MEM_TYPE_FORMAT, value);
  return true;
}

// Note that for cgroups v2 the actual limits set for swap and
// memory live in two different files, memory.swap.max and memory.max
// respectively. In order to properly report a cgroup v1 like
// compound value we need to sum the two values. Setting a swap limit
// without also setting a memory limit is not allowed.
bool CgroupV2MemoryController::memory_and_swap_limit_in_bytes(physical_memory_size_type upper_mem_bound,
                                                              physical_memory_size_type upper_swap_bound, /* unused in cg v2 */
                                                              physical_memory_size_type& result) {
  physical_memory_size_type swap_limit_val = 0;
  if (!reader()->read_number_handle_max("/memory.swap.max", swap_limit_val)) {
    // Some container tests rely on this trace logging to happen.
    log_trace(os, container)("Swap Limit failed");
    // swap disabled at kernel level, treat it as no swap
    physical_memory_size_type mem_limit = value_unlimited;
    if (!read_memory_limit_in_bytes(upper_mem_bound, mem_limit)) {
      return false;
    }
    result = mem_limit;
    return true;
  }
  if (swap_limit_val == value_unlimited) {
    log_trace(os, container)("Memory and Swap Limit is: Unlimited");
    result = swap_limit_val;
    return true;
  }
  log_trace(os, container)("Swap Limit is: " PHYS_MEM_TYPE_FORMAT, swap_limit_val);
  physical_memory_size_type memory_limit = 0;
  if (read_memory_limit_in_bytes(upper_mem_bound, memory_limit)) {
    assert(memory_limit != value_unlimited, "swap limit without memory limit?");
    result = memory_limit + swap_limit_val;
    log_trace(os, container)("Memory and Swap Limit is: " PHYS_MEM_TYPE_FORMAT, result);
    return true;
  } else {
    return false;
  }
}

// memory.swap.current : total amount of swap currently used by the cgroup and its descendants
static
bool memory_swap_current_value(CgroupV2Controller* ctrl, physical_memory_size_type& result) {
  CONTAINER_READ_NUMBER_CHECKED(ctrl, "/memory.swap.current", "Swap currently used", result);
}

bool CgroupV2MemoryController::memory_and_swap_usage_in_bytes(physical_memory_size_type upper_mem_bound,
                                                              physical_memory_size_type upper_swap_bound,
                                                              physical_memory_size_type& result) {
  physical_memory_size_type memory_usage = 0;
  if (!memory_usage_in_bytes(memory_usage)) {
     return false;
  }
  physical_memory_size_type swap_current = 0;
  if (!memory_swap_current_value(reader(), swap_current)) {
    result = memory_usage; // treat as no swap usage
    return true;
  }
  result = memory_usage + swap_current;
  return true;
}

static
bool memory_limit_value(CgroupV2Controller* ctrl, physical_memory_size_type& result) {
  CONTAINER_READ_NUMBER_CHECKED_MAX(ctrl, "/memory.max", "Memory Limit", result);
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
bool CgroupV2MemoryController::read_memory_limit_in_bytes(physical_memory_size_type upper_bound,
                                                          physical_memory_size_type& result) {
  physical_memory_size_type limit = 0; // default unlimited
  if (!memory_limit_value(reader(), limit)) {
    log_trace(os, container)("container memory limit failed, using host value " PHYS_MEM_TYPE_FORMAT,
                              upper_bound);
    return false;
  }
  bool is_unlimited = limit == value_unlimited;
  bool exceeds_physical_mem = false;
  if (!is_unlimited && limit >= upper_bound) {
    exceeds_physical_mem = true;
  }
  if (log_is_enabled(Trace, os, container)) {
    if (!is_unlimited) {
      log_trace(os, container)("Memory Limit is: " PHYS_MEM_TYPE_FORMAT, limit);
    }
    if (is_unlimited || exceeds_physical_mem) {
      if (is_unlimited) {
        log_trace(os, container)("Memory Limit is: Unlimited");
        log_trace(os, container)("container memory limit unlimited, using upper bound value " PHYS_MEM_TYPE_FORMAT, upper_bound);
      } else {
        log_trace(os, container)("container memory limit ignored: " PHYS_MEM_TYPE_FORMAT ", upper bound is " PHYS_MEM_TYPE_FORMAT,
                                 limit, upper_bound);
      }
    }
  }
  result = limit;
  return true;
}

static
bool memory_swap_limit_value(CgroupV2Controller* ctrl, physical_memory_size_type& value) {
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

void CgroupV2MemoryController::print_version_specific_info(outputStream* st, physical_memory_size_type upper_mem_bound) {
  MetricResult swap_current;
  physical_memory_size_type swap_current_val = 0;
  if (memory_swap_current_value(reader(), swap_current_val)) {
    swap_current.set_value(swap_current_val);
  }
  MetricResult swap_limit;
  physical_memory_size_type swap_limit_val = 0;
  if (memory_swap_limit_value(reader(), swap_limit_val)) {
    swap_limit.set_value(swap_limit_val);
  }
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
 * Calculate the maximum number of tasks available to the process. Set the
 * value in the passed in 'value' reference. The value might be 'value_unlimited' when
 * there is no limit.
 *
 * return:
 *    true if the value has been set appropriately
 *    false if there was an error
 */
bool CgroupV2Subsystem::pids_max(uint64_t& value) {
  CONTAINER_READ_NUMBER_CHECKED_MAX(unified(), "/pids.max", "Maximum number of tasks", value);
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
bool CgroupV2Subsystem::pids_current(uint64_t& value) {
  CONTAINER_READ_NUMBER_CHECKED(unified(), "/pids.current", "Current number of tasks", value);
}
