/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
    int host_cpus = os::Linux::active_processor_count();
    int cpus = host_cpus;
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

bool OSContainer::active_processor_count(int& value) {
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

void OSContainer::print_container_helper(outputStream* st, MetricResult& res, const char* metrics) {
  st->print("%s: ", metrics);
  if (res.success()) {
    if (res.value() != value_unlimited) {
      if (res.value() >= 1024) {
        st->print_cr(PHYS_MEM_TYPE_FORMAT " k", (physical_memory_size_type)(res.value() / K));
      } else {
        st->print_cr(PHYS_MEM_TYPE_FORMAT, res.value());
      }
    } else {
      st->print_cr("%s", "unlimited");
    }
  } else {
    // Not supported
    st->print_cr("%s", "unavailable");
  }
}
