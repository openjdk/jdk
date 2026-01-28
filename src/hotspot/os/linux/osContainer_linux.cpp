/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "cgroupSubsystem_linux.hpp"
#include "logging/log.hpp"
#include "os_linux.hpp"
#include "osContainer_linux.hpp"
#include "runtime/globals.hpp"
#include "runtime/os.hpp"

#include <errno.h>
#include <math.h>
#include <string.h>


bool  OSContainer::_is_initialized   = false;
bool  OSContainer::_is_containerized = false;
CgroupSubsystem* cgroup_subsystem;

/* init
 *
 * Initialize the container support and determine if
 * we are running under cgroup control.
 */
void OSContainer::init() {
  assert(!_is_initialized, "Initializing OSContainer more than once");

  _is_initialized = true;
  _is_containerized = false;

  log_trace(os, container)("OSContainer::init: Initializing Container Support");
  if (!UseContainerSupport) {
    log_trace(os, container)("Container Support not enabled");
    return;
  }

  cgroup_subsystem = CgroupSubsystemFactory::create();
  if (cgroup_subsystem == nullptr) {
    return; // Required subsystem files not found or other error
  }
  /*
   * In order to avoid a false positive on is_containerized() on
   * Linux systems outside a container *and* to ensure compatibility
   * with in-container usage, we detemine is_containerized() by two
   * steps:
   * 1.) Determine if all the cgroup controllers are mounted read only.
   *     If yes, is_containerized() == true. Otherwise, do the fallback
   *     in 2.)
   * 2.) Query for memory and cpu limits. If any limit is set, we set
   *     is_containerized() == true.
   *
   * Step 1.) covers the basic in container use-cases. Step 2.) ensures
   * that limits enforced by other means (e.g. systemd slice) are properly
   * detected.
   */
  const char *reason;
  bool any_mem_cpu_limit_present = false;
  bool controllers_read_only = cgroup_subsystem->is_containerized();
  if (controllers_read_only) {
    // in-container case
    reason = " because all controllers are mounted read-only (container case)";
  } else {
    // We can be in one of two cases:
    //  1.) On a physical Linux system without any limit
    //  2.) On a physical Linux system with a limit enforced by other means (like systemd slice)
    physical_memory_size_type mem_limit_val = value_unlimited;
    (void)memory_limit_in_bytes(mem_limit_val);  // discard error and use default
    double host_cpus = os::Linux::active_processor_count();
    double cpus = host_cpus;
    (void)active_processor_count(cpus);  // discard error and use default
    any_mem_cpu_limit_present = mem_limit_val != value_unlimited || host_cpus != cpus;
    if (any_mem_cpu_limit_present) {
      reason = " because either a cpu or a memory limit is present";
    } else {
      reason = " because no cpu or memory limit is present";
    }
  }
  _is_containerized = controllers_read_only || any_mem_cpu_limit_present;
  log_debug(os, container)("OSContainer::init: is_containerized() = %s%s",
                                                            _is_containerized ? "true" : "false",
                                                            reason);
}

const char * OSContainer::container_type() {
  assert(cgroup_subsystem != nullptr, "cgroup subsystem not available");
  return cgroup_subsystem->container_type();
}

bool OSContainer::memory_limit_in_bytes(physical_memory_size_type& value) {
  assert(cgroup_subsystem != nullptr, "cgroup subsystem not available");
  physical_memory_size_type phys_mem = os::Linux::physical_memory();
  return cgroup_subsystem->memory_limit_in_bytes(phys_mem, value);
}

bool OSContainer::available_memory_in_bytes(physical_memory_size_type& value) {
  physical_memory_size_type mem_limit = value_unlimited;
  physical_memory_size_type mem_usage = 0;
  if (memory_limit_in_bytes(mem_limit) && memory_usage_in_bytes(mem_usage)) {
    assert(mem_usage != value_unlimited, "invariant");
    if (mem_limit != value_unlimited) {
      value = (mem_limit > mem_usage) ? mem_limit - mem_usage : 0;
      return true;
    }
  }
  log_trace(os, container)("calculating available memory in container failed");
  return false;
}

bool OSContainer::available_swap_in_bytes(physical_memory_size_type& value) {
  physical_memory_size_type mem_limit = 0;
  physical_memory_size_type mem_swap_limit = 0;
  if (memory_limit_in_bytes(mem_limit) &&
      memory_and_swap_limit_in_bytes(mem_swap_limit) &&
      mem_limit != value_unlimited &&
      mem_swap_limit != value_unlimited) {
    if (mem_limit >= mem_swap_limit) {
      value = 0; // no swap, thus no free swap
      return true;
    }
    physical_memory_size_type swap_limit = mem_swap_limit - mem_limit;
    physical_memory_size_type mem_swap_usage = 0;
    physical_memory_size_type mem_usage = 0;
    if (memory_and_swap_usage_in_bytes(mem_swap_usage) &&
        memory_usage_in_bytes(mem_usage)) {
      physical_memory_size_type swap_usage = value_unlimited;
      if (mem_usage > mem_swap_usage) {
        swap_usage = 0; // delta usage must not be negative
      } else {
        swap_usage = mem_swap_usage - mem_usage;
      }
      // free swap is based on swap limit (upper bound) and swap usage
      if (swap_usage >= swap_limit) {
        value = 0; // free swap must not be negative
        return true;
      }
      value = swap_limit - swap_usage;
      return true;
    }
  }
  // unlimited or not supported. Leave an appropriate trace message
  if (log_is_enabled(Trace, os, container)) {
    char mem_swap_buf[25]; // uint64_t => 20 + 1, 'unlimited' => 9 + 1; 10 < 21 < 25
    char mem_limit_buf[25];
    int num = 0;
    if (mem_swap_limit == value_unlimited) {
      num = os::snprintf(mem_swap_buf, sizeof(mem_swap_buf), "%s", "unlimited");
    } else {
      num = os::snprintf(mem_swap_buf, sizeof(mem_swap_buf), PHYS_MEM_TYPE_FORMAT, mem_swap_limit);
    }
    assert(num < 25, "buffer too small");
    mem_swap_buf[num] = '\0';
    if (mem_limit == value_unlimited) {
      num = os::snprintf(mem_limit_buf, sizeof(mem_limit_buf), "%s", "unlimited");
    } else {
      num = os::snprintf(mem_limit_buf, sizeof(mem_limit_buf), PHYS_MEM_TYPE_FORMAT, mem_limit);
    }
    assert(num < 25, "buffer too small");
    mem_limit_buf[num] = '\0';
    log_trace(os,container)("OSContainer::available_swap_in_bytes: container_swap_limit=%s"
                            " container_mem_limit=%s", mem_swap_buf, mem_limit_buf);
  }
  return false;
}

bool OSContainer::memory_and_swap_limit_in_bytes(physical_memory_size_type& value) {
  assert(cgroup_subsystem != nullptr, "cgroup subsystem not available");
  physical_memory_size_type phys_mem = os::Linux::physical_memory();
  physical_memory_size_type host_swap = 0;
  if (!os::Linux::host_swap(host_swap)) {
    return false;
  }
  return cgroup_subsystem->memory_and_swap_limit_in_bytes(phys_mem, host_swap, value);
}

bool OSContainer::memory_and_swap_usage_in_bytes(physical_memory_size_type& value) {
  assert(cgroup_subsystem != nullptr, "cgroup subsystem not available");
  physical_memory_size_type phys_mem = os::Linux::physical_memory();
  physical_memory_size_type host_swap = 0;
  if (!os::Linux::host_swap(host_swap)) {
    return false;
  }
  return cgroup_subsystem->memory_and_swap_usage_in_bytes(phys_mem, host_swap, value);
}

bool OSContainer::memory_soft_limit_in_bytes(physical_memory_size_type& value) {
  assert(cgroup_subsystem != nullptr, "cgroup subsystem not available");
  physical_memory_size_type phys_mem = os::Linux::physical_memory();
  return cgroup_subsystem->memory_soft_limit_in_bytes(phys_mem, value);
}

bool OSContainer::memory_throttle_limit_in_bytes(physical_memory_size_type& value) {
  assert(cgroup_subsystem != nullptr, "cgroup subsystem not available");
  return cgroup_subsystem->memory_throttle_limit_in_bytes(value);
}

bool OSContainer::memory_usage_in_bytes(physical_memory_size_type& value) {
  assert(cgroup_subsystem != nullptr, "cgroup subsystem not available");
  return cgroup_subsystem->memory_usage_in_bytes(value);
}

bool OSContainer::memory_max_usage_in_bytes(physical_memory_size_type& value) {
  assert(cgroup_subsystem != nullptr, "cgroup subsystem not available");
  return cgroup_subsystem->memory_max_usage_in_bytes(value);
}

bool OSContainer::rss_usage_in_bytes(physical_memory_size_type& value) {
  assert(cgroup_subsystem != nullptr, "cgroup subsystem not available");
  return cgroup_subsystem->rss_usage_in_bytes(value);
}

bool OSContainer::cache_usage_in_bytes(physical_memory_size_type& value) {
  assert(cgroup_subsystem != nullptr, "cgroup subsystem not available");
  return cgroup_subsystem->cache_usage_in_bytes(value);
}

void OSContainer::print_version_specific_info(outputStream* st) {
  assert(cgroup_subsystem != nullptr, "cgroup subsystem not available");
  physical_memory_size_type phys_mem = os::Linux::physical_memory();
  cgroup_subsystem->print_version_specific_info(st, phys_mem);
}

char * OSContainer::cpu_cpuset_cpus() {
  assert(cgroup_subsystem != nullptr, "cgroup subsystem not available");
  return cgroup_subsystem->cpu_cpuset_cpus();
}

char * OSContainer::cpu_cpuset_memory_nodes() {
  assert(cgroup_subsystem != nullptr, "cgroup subsystem not available");
  return cgroup_subsystem->cpu_cpuset_memory_nodes();
}

bool OSContainer::active_processor_count(double& value) {
  assert(cgroup_subsystem != nullptr, "cgroup subsystem not available");
  return cgroup_subsystem->active_processor_count(value);
}

bool OSContainer::cpu_quota(int& value) {
  assert(cgroup_subsystem != nullptr, "cgroup subsystem not available");
  return cgroup_subsystem->cpu_quota(value);
}

bool OSContainer::cpu_period(int& value) {
  assert(cgroup_subsystem != nullptr, "cgroup subsystem not available");
  return cgroup_subsystem->cpu_period(value);
}

bool OSContainer::cpu_shares(int& value) {
  assert(cgroup_subsystem != nullptr, "cgroup subsystem not available");
  return cgroup_subsystem->cpu_shares(value);
}

bool OSContainer::cpu_usage_in_micros(uint64_t& value) {
  assert(cgroup_subsystem != nullptr, "cgroup subsystem not available");
  return cgroup_subsystem->cpu_usage_in_micros(value);
}

bool OSContainer::pids_max(uint64_t& value) {
  assert(cgroup_subsystem != nullptr, "cgroup subsystem not available");
  return cgroup_subsystem->pids_max(value);
}

bool OSContainer::pids_current(uint64_t& value) {
  assert(cgroup_subsystem != nullptr, "cgroup subsystem not available");
  return cgroup_subsystem->pids_current(value);
}

template<typename T> struct metric_fmt;
template<> struct metric_fmt<unsigned long long int> { static constexpr const char* fmt = "%llu"; };
template<> struct metric_fmt<unsigned long int> { static constexpr const char* fmt = "%lu"; };
template<> struct metric_fmt<int> { static constexpr const char* fmt = "%d"; };
template<> struct metric_fmt<double> { static constexpr const char* fmt = "%.2f"; };
template<> struct metric_fmt<const char*> { static constexpr const char* fmt = "%s"; };

template void OSContainer::print_container_metric<unsigned long long int>(outputStream*, const char*, unsigned long long int, const char*);
template void OSContainer::print_container_metric<unsigned long int>(outputStream*, const char*, unsigned long int, const char*);
template void OSContainer::print_container_metric<int>(outputStream*, const char*, int, const char*);
template void OSContainer::print_container_metric<double>(outputStream*, const char*, double, const char*);
template void OSContainer::print_container_metric<const char*>(outputStream*, const char*, const char*, const char*);

template <typename T>
void OSContainer::print_container_metric(outputStream* st, const char* metrics, T value, const char* unit) {
  constexpr int max_length = 38; // Longest "metric: value" string ("maximum number of tasks: not supported")
  constexpr int longest_value = max_length - 11; // Max length - shortest "metric: " string ("cpu_quota: ")
  char value_str[longest_value + 1] = {};
  os::snprintf_checked(value_str, longest_value, metric_fmt<T>::fmt, value);

  const int pad_width = max_length - static_cast<int>(strlen(metrics)) - 2; // -2 for the ": "
  const char* unit_prefix = unit[0] != '\0' ? " " : "";

  char line[128] = {};
  os::snprintf_checked(line, sizeof(line), "%s: %*s%s%s", metrics, pad_width, value_str, unit_prefix, unit);
  st->print_cr("%s", line);
}

void OSContainer::print_container_helper(outputStream* st, MetricResult& res, const char* metrics) {
  if (res.success()) {
    if (res.value() != value_unlimited) {
      if (res.value() >= 1024) {
        print_container_metric(st, metrics, res.value() / K, "kB");
      } else {
        print_container_metric(st, metrics, res.value(), "B");
      }
    } else {
      print_container_metric(st, metrics, "unlimited");
    }
  } else {
    // Not supported
    print_container_metric(st, metrics, "unavailable");
  }
}
