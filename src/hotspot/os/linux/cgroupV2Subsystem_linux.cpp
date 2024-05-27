/*
 * Copyright (c) 2020, 2022, Red Hat Inc.
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
int CgroupV2Subsystem::cpu_shares() {
  julong shares;
  CONTAINER_READ_NUMBER_CHECKED(_unified, "/cpu.weight", "Raw value for CPU Shares", shares);
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
int CgroupV2Subsystem::cpu_quota() {
  jlong quota_val;
  bool is_ok = _unified->read_numerical_tuple_value("/cpu.max", true /* use_first */, &quota_val);
  if (!is_ok) {
    return OSCONTAINER_ERROR;
  }
  int limit = (int)quota_val;
  log_trace(os, container)("CPU Quota is: %d", limit);
  return limit;
}

char* CgroupV2Subsystem::cpu_cpuset_cpus() {
  char cpus[1024];
  CONTAINER_READ_STRING_CHECKED(_unified, "/cpuset.cpus", "cpuset.cpus", cpus, 1024);
  return os::strdup(cpus);
}

char* CgroupV2Subsystem::cpu_cpuset_memory_nodes() {
  char mems[1024];
  CONTAINER_READ_STRING_CHECKED(_unified, "/cpuset.mems", "cpuset.mems", mems, 1024);
  return os::strdup(mems);
}

int CgroupV2Subsystem::cpu_period() {
  jlong period_val;
  bool is_ok = _unified->read_numerical_tuple_value("/cpu.max", false /* use_first */, &period_val);
  if (!is_ok) {
    log_trace(os, container)("CPU Period failed: %d", OSCONTAINER_ERROR);
    return OSCONTAINER_ERROR;
  }
  int period = (int)period_val;
  log_trace(os, container)("CPU Period is: %d", period);
  return period;
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
jlong CgroupV2Subsystem::memory_usage_in_bytes() {
  julong memusage;
  CONTAINER_READ_NUMBER_CHECKED(_unified, "/memory.current", "Memory Usage", memusage);
  return (jlong)memusage;
}

jlong CgroupV2Subsystem::memory_soft_limit_in_bytes() {
  jlong mem_soft_limit;
  CONTAINER_READ_NUMBER_CHECKED_MAX(_unified, "/memory.low", "Memory Soft Limit", mem_soft_limit);
  return mem_soft_limit;
}

jlong CgroupV2Subsystem::memory_max_usage_in_bytes() {
  // Log this string at trace level so as to make tests happy.
  log_trace(os, container)("Maximum Memory Usage is not supported.");
  return OSCONTAINER_ERROR; // not supported
}

jlong CgroupV2Subsystem::rss_usage_in_bytes() {
  julong rss;
  bool is_ok = _memory->controller()->
                    read_numerical_key_value("/memory.stat", "anon", &rss);
  if (!is_ok) {
    return OSCONTAINER_ERROR;
  }
  log_trace(os, container)("RSS usage is: " JULONG_FORMAT, rss);
  return (jlong)rss;
}

jlong CgroupV2Subsystem::cache_usage_in_bytes() {
  julong cache;
  bool is_ok = _memory->controller()->
                    read_numerical_key_value("/memory.stat", "file", &cache);
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
jlong CgroupV2Subsystem::memory_and_swap_limit_in_bytes() {
  jlong swap_limit;
  bool is_ok = _memory->controller()->read_number_handle_max("/memory.swap.max", &swap_limit);
  if (!is_ok) {
    // Some container tests rely on this trace logging to happen.
    log_trace(os, container)("Swap Limit failed: %d", OSCONTAINER_ERROR);
    // swap disabled at kernel level, treat it as no swap
    return read_memory_limit_in_bytes();
  }
  log_trace(os, container)("Swap Limit is: " JLONG_FORMAT, swap_limit);
  if (swap_limit >= 0) {
    jlong memory_limit = read_memory_limit_in_bytes();
    assert(memory_limit >= 0, "swap limit without memory limit?");
    return memory_limit + swap_limit;
  }
  log_trace(os, container)("Memory and Swap Limit is: " JLONG_FORMAT, swap_limit);
  return swap_limit;
}

jlong CgroupV2Subsystem::memory_and_swap_usage_in_bytes() {
    jlong memory_usage = memory_usage_in_bytes();
    if (memory_usage >= 0) {
        jlong swap_current = mem_swp_current_val();
        return memory_usage + (swap_current >= 0 ? swap_current : 0);
    }
    return memory_usage; // not supported or unlimited case
}

jlong CgroupV2Subsystem::mem_swp_limit_val() {
  jlong swap_limit;
  CONTAINER_READ_NUMBER_CHECKED_MAX(_unified, "/memory.swap.max", "Swap Limit", swap_limit);
  return swap_limit;
}

// memory.swap.current : total amount of swap currently used by the cgroup and its descendants
jlong CgroupV2Subsystem::mem_swp_current_val() {
  julong swap_current;
  CONTAINER_READ_NUMBER_CHECKED(_unified, "/memory.swap.current", "Swap currently used", swap_current);
  return (jlong)swap_current;
}

/* memory_limit_in_bytes
 *
 * Return the limit of available memory for this process.
 *
 * return:
 *    memory limit in bytes or
 *    -1 for unlimited, OSCONTAINER_ERROR for an error
 */
jlong CgroupV2Subsystem::read_memory_limit_in_bytes() {
  jlong memory_limit;
  CONTAINER_READ_NUMBER_CHECKED_MAX(_unified, "/memory.max", "Memory Limit", memory_limit);
  return memory_limit;
}

void CgroupV2Subsystem::print_version_specific_info(outputStream* st) {
  jlong swap_current = mem_swp_current_val();
  jlong swap_limit = mem_swp_limit_val();

  OSContainer::print_container_helper(st, swap_current, "memory_swap_current_in_bytes");
  OSContainer::print_container_helper(st, swap_limit, "memory_swap_max_limit_in_bytes");
}

char* CgroupV2Controller::construct_path(char* mount_path, char *cgroup_path) {
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
  CONTAINER_READ_NUMBER_CHECKED_MAX(_unified, "/pids.max", "Maximum number of tasks", pids_max);
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
  CONTAINER_READ_NUMBER_CHECKED(_unified, "/pids.current", "Current number of tasks", pids_current);
  return pids_current;
}
