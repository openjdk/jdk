/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CGROUP_V1_SUBSYSTEM_LINUX_HPP
#define CGROUP_V1_SUBSYSTEM_LINUX_HPP

#include "runtime/os.hpp"
#include "memory/allocation.hpp"
#include "cgroupSubsystem_linux.hpp"

// Cgroups version 1 specific implementation

class CgroupV1Controller: public CgroupController {
  private:
    /* mountinfo contents */
    char *_root;
    char *_mount_point;

    /* Constructed subsystem directory */
    char *_path;

  public:
    CgroupV1Controller(char *root, char *mountpoint) {
      _root = os::strdup(root);
      _mount_point = os::strdup(mountpoint);
      _path = nullptr;
    }

    virtual void set_subsystem_path(char *cgroup_path);
    char *subsystem_path() { return _path; }
};

class CgroupV1MemoryController: public CgroupV1Controller {

  public:
    bool is_hierarchical() { return _uses_mem_hierarchy; }
    void set_subsystem_path(char *cgroup_path);
  private:
    /* Some container runtimes set limits via cgroup
     * hierarchy. If set to true consider also memory.stat
     * file if everything else seems unlimited */
    bool _uses_mem_hierarchy;
    jlong uses_mem_hierarchy();
    void set_hierarchical(bool value) { _uses_mem_hierarchy = value; }

  public:
    CgroupV1MemoryController(char *root, char *mountpoint) : CgroupV1Controller(root, mountpoint) {
      _uses_mem_hierarchy = false;
    }

};

class CgroupV1Subsystem: public CgroupSubsystem {

  public:
    jlong read_memory_limit_in_bytes();
    jlong memory_and_swap_limit_in_bytes();
    jlong memory_soft_limit_in_bytes();
    jlong memory_usage_in_bytes();
    jlong memory_max_usage_in_bytes();
    jlong rss_usage_in_bytes();
    jlong cache_usage_in_bytes();

    jlong kernel_memory_usage_in_bytes();
    jlong kernel_memory_limit_in_bytes();
    jlong kernel_memory_max_usage_in_bytes();

    char * cpu_cpuset_cpus();
    char * cpu_cpuset_memory_nodes();

    int cpu_quota();
    int cpu_period();

    int cpu_shares();

    jlong pids_max();
    jlong pids_current();

    void print_version_specific_info(outputStream* st);

    const char * container_type() {
      return "cgroupv1";
    }
    CachingCgroupController * memory_controller() { return _memory; }
    CachingCgroupController * cpu_controller() { return _cpu; }

  private:
    /* controllers */
    CachingCgroupController* _memory = nullptr;
    CgroupV1Controller* _cpuset = nullptr;
    CachingCgroupController* _cpu = nullptr;
    CgroupV1Controller* _cpuacct = nullptr;
    CgroupV1Controller* _pids = nullptr;

    char * pids_max_val();

    jlong read_mem_swappiness();
    jlong read_mem_swap();

  public:
    CgroupV1Subsystem(CgroupV1Controller* cpuset,
                      CgroupV1Controller* cpu,
                      CgroupV1Controller* cpuacct,
                      CgroupV1Controller* pids,
                      CgroupV1MemoryController* memory) {
      _cpuset = cpuset;
      _cpu = new CachingCgroupController(cpu);
      _cpuacct = cpuacct;
      _pids = pids;
      _memory = new CachingCgroupController(memory);
    }
};

#endif // CGROUP_V1_SUBSYSTEM_LINUX_HPP
