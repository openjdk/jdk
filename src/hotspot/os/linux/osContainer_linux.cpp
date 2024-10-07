/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/globals.hpp"
#include "runtime/os.hpp"
#include "logging/log.hpp"
#include "os_linux.hpp"
#include "osContainer_linux.hpp"
#include "cgroupSubsystem_linux.hpp"


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
    any_mem_cpu_limit_present = cgroup_subsystem->memory_limit_in_bytes() > 0 ||
                                     os::Linux::active_processor_count() != cgroup_subsystem->active_processor_count();
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

jlong OSContainer::memory_limit_in_bytes() {
  assert(cgroup_subsystem != nullptr, "cgroup subsystem not available");
  return cgroup_subsystem->memory_limit_in_bytes();
}

jlong OSContainer::memory_and_swap_limit_in_bytes() {
  assert(cgroup_subsystem != nullptr, "cgroup subsystem not available");
  return cgroup_subsystem->memory_and_swap_limit_in_bytes();
}

jlong OSContainer::memory_and_swap_usage_in_bytes() {
  assert(cgroup_subsystem != nullptr, "cgroup subsystem not available");
  return cgroup_subsystem->memory_and_swap_usage_in_bytes();
}

jlong OSContainer::memory_soft_limit_in_bytes() {
  assert(cgroup_subsystem != nullptr, "cgroup subsystem not available");
  return cgroup_subsystem->memory_soft_limit_in_bytes();
}

jlong OSContainer::memory_usage_in_bytes() {
  assert(cgroup_subsystem != nullptr, "cgroup subsystem not available");
  return cgroup_subsystem->memory_usage_in_bytes();
}

jlong OSContainer::memory_max_usage_in_bytes() {
  assert(cgroup_subsystem != nullptr, "cgroup subsystem not available");
  return cgroup_subsystem->memory_max_usage_in_bytes();
}

jlong OSContainer::rss_usage_in_bytes() {
  assert(cgroup_subsystem != nullptr, "cgroup subsystem not available");
  return cgroup_subsystem->rss_usage_in_bytes();
}

jlong OSContainer::cache_usage_in_bytes() {
  assert(cgroup_subsystem != nullptr, "cgroup subsystem not available");
  return cgroup_subsystem->cache_usage_in_bytes();
}

void OSContainer::print_version_specific_info(outputStream* st) {
  assert(cgroup_subsystem != nullptr, "cgroup subsystem not available");
  cgroup_subsystem->print_version_specific_info(st);
}

char * OSContainer::cpu_cpuset_cpus() {
  assert(cgroup_subsystem != nullptr, "cgroup subsystem not available");
  return cgroup_subsystem->cpu_cpuset_cpus();
}

char * OSContainer::cpu_cpuset_memory_nodes() {
  assert(cgroup_subsystem != nullptr, "cgroup subsystem not available");
  return cgroup_subsystem->cpu_cpuset_memory_nodes();
}

int OSContainer::active_processor_count() {
  assert(cgroup_subsystem != nullptr, "cgroup subsystem not available");
  return cgroup_subsystem->active_processor_count();
}

int OSContainer::cpu_quota() {
  assert(cgroup_subsystem != nullptr, "cgroup subsystem not available");
  return cgroup_subsystem->cpu_quota();
}

int OSContainer::cpu_period() {
  assert(cgroup_subsystem != nullptr, "cgroup subsystem not available");
  return cgroup_subsystem->cpu_period();
}

int OSContainer::cpu_shares() {
  assert(cgroup_subsystem != nullptr, "cgroup subsystem not available");
  return cgroup_subsystem->cpu_shares();
}

jlong OSContainer::pids_max() {
  assert(cgroup_subsystem != nullptr, "cgroup subsystem not available");
  return cgroup_subsystem->pids_max();
}

jlong OSContainer::pids_current() {
  assert(cgroup_subsystem != nullptr, "cgroup subsystem not available");
  return cgroup_subsystem->pids_current();
}

void OSContainer::print_container_helper(outputStream* st, jlong j, const char* metrics) {
  st->print("%s: ", metrics);
  if (j >= 0) {
    if (j >= 1024) {
      st->print_cr(UINT64_FORMAT " k", uint64_t(j) / K);
    } else {
      st->print_cr(UINT64_FORMAT, uint64_t(j));
    }
  } else {
    st->print_cr("%s", j == OSCONTAINER_ERROR ? "not supported" : "unlimited");
  }
}
