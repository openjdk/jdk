/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "osContainer_linux.hpp"
#include "cgroupSubsystem_linux.hpp"


bool  OSContainer::_is_initialized   = false;
bool  OSContainer::_is_containerized = false;
const char * OSContainer::_runtime = NULL;
CgroupSubsystem* cgroup_subsystem;

/* init
 *
 * Initialize the container support and determine if
 * we are running under cgroup control.
 */
void OSContainer::init() {
  jlong mem_limit;

  assert(!_is_initialized, "Initializing OSContainer more than once");

  _is_initialized = true;
  _is_containerized = false;
  _runtime = NULL;

  log_trace(os, container)("OSContainer::init: Initializing Container Support");
  if (!UseContainerSupport) {
    log_trace(os, container)("Container Support not enabled");
    return;
  }

  cgroup_subsystem = CgroupSubsystemFactory::create();
  if (cgroup_subsystem == NULL) {
    return; // Required subsystem files not found or other error
  }
  // We need to update the amount of physical memory now that
  // cgroup subsystem files have been processed.
  if ((mem_limit = cgroup_subsystem->memory_limit_in_bytes()) > 0) {
    os::Linux::set_physical_memory(mem_limit);
    log_info(os, container)("Memory Limit is: " JLONG_FORMAT, mem_limit);
  }

  _is_containerized = true;

  if (getpid() == 1) {
    // This process is in container
    _runtime = os::strdup_check_oom(getenv("container"));
  } else {
    // This process might be child of container process.
    // So we check environment variable in PID 1.
    int env_fd = open("/proc/1/environ", O_RDONLY);
    if (env_fd != -1) {
      const int buf_sz = 8192;
      char buf[buf_sz];
      int read_sz = read(env_fd, buf, buf_sz);
      close(env_fd);
      if (read_sz > 0) {
        buf[read_sz - 1] = '\0';
        const char *envname = "container=";
        char *container_env = (char *)memmem(buf, read_sz, envname, strlen(envname));
        if (container_env != NULL) {
          _runtime = os::strdup_check_oom(container_env + strlen(envname));
        }
      }
    }
  }
  log_info(os, container)("Container runtime: %s", _runtime == NULL ? "none" : _runtime);
}

const char * OSContainer::container_type() {
  assert(cgroup_subsystem != NULL, "cgroup subsystem not available");
  return cgroup_subsystem->container_type();
}

jlong OSContainer::memory_limit_in_bytes() {
  assert(cgroup_subsystem != NULL, "cgroup subsystem not available");
  return cgroup_subsystem->memory_limit_in_bytes();
}

jlong OSContainer::memory_and_swap_limit_in_bytes() {
  assert(cgroup_subsystem != NULL, "cgroup subsystem not available");
  return cgroup_subsystem->memory_and_swap_limit_in_bytes();
}

jlong OSContainer::memory_soft_limit_in_bytes() {
  assert(cgroup_subsystem != NULL, "cgroup subsystem not available");
  return cgroup_subsystem->memory_soft_limit_in_bytes();
}

jlong OSContainer::memory_usage_in_bytes() {
  assert(cgroup_subsystem != NULL, "cgroup subsystem not available");
  return cgroup_subsystem->memory_usage_in_bytes();
}

jlong OSContainer::memory_max_usage_in_bytes() {
  assert(cgroup_subsystem != NULL, "cgroup subsystem not available");
  return cgroup_subsystem->memory_max_usage_in_bytes();
}

char * OSContainer::cpu_cpuset_cpus() {
  assert(cgroup_subsystem != NULL, "cgroup subsystem not available");
  return cgroup_subsystem->cpu_cpuset_cpus();
}

char * OSContainer::cpu_cpuset_memory_nodes() {
  assert(cgroup_subsystem != NULL, "cgroup subsystem not available");
  return cgroup_subsystem->cpu_cpuset_memory_nodes();
}

int OSContainer::active_processor_count() {
  assert(cgroup_subsystem != NULL, "cgroup subsystem not available");
  return cgroup_subsystem->active_processor_count();
}

int OSContainer::cpu_quota() {
  assert(cgroup_subsystem != NULL, "cgroup subsystem not available");
  return cgroup_subsystem->cpu_quota();
}

int OSContainer::cpu_period() {
  assert(cgroup_subsystem != NULL, "cgroup subsystem not available");
  return cgroup_subsystem->cpu_period();
}

int OSContainer::cpu_shares() {
  assert(cgroup_subsystem != NULL, "cgroup subsystem not available");
  return cgroup_subsystem->cpu_shares();
}
