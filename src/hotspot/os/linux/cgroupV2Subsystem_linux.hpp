/*
 * Copyright (c) 2020, 2024, Red Hat Inc.
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

#ifndef CGROUP_V2_SUBSYSTEM_LINUX_HPP
#define CGROUP_V2_SUBSYSTEM_LINUX_HPP

#include "cgroupSubsystem_linux.hpp"
#include "cgroupUtil_linux.hpp"

class CgroupV2Controller: public CgroupController {
  private:
    bool _read_only;

    /* Constructed full path to the subsystem directory */
    char *_path;
    static char* construct_path(char* mount_path, const char *cgroup_path);

  public:
    CgroupV2Controller(char* mount_path, char *cgroup_path, bool ro);
    // Shallow copy constructor
    CgroupV2Controller(const CgroupV2Controller& o);
    ~CgroupV2Controller() {
      // At least one controller exists with references to the paths
    }

    const char* subsystem_path() override { return _path; }
    bool needs_hierarchy_adjustment() override;
    // Allow for optional updates of the subsystem path
    void set_subsystem_path(const char* cgroup_path);
    bool is_read_only() override { return _read_only; }
};

class CgroupV2CpuController: public CgroupCpuController {
  private:
    CgroupV2Controller _reader;
    CgroupV2Controller* reader() { return &_reader; }
  public:
    CgroupV2CpuController(const CgroupV2Controller& reader) : _reader(reader) {
    }
    int cpu_quota() override;
    int cpu_period() override;
    int cpu_shares() override;
    jlong cpu_usage_in_micros();
    bool is_read_only() override {
      return reader()->is_read_only();
    }
    const char* subsystem_path() override {
      return reader()->subsystem_path();
    }
    bool needs_hierarchy_adjustment() override {
      return reader()->needs_hierarchy_adjustment();
    }
    void set_subsystem_path(const char* cgroup_path) override {
      reader()->set_subsystem_path(cgroup_path);
    }
    const char* mount_point() override { return reader()->mount_point(); }
    const char* cgroup_path() override { return reader()->cgroup_path(); }
};

class CgroupV2CpuacctController: public CgroupCpuacctController {
  private:
    CgroupV2CpuController* _reader;
    CgroupV2CpuController* reader() { return _reader; }
  public:
    CgroupV2CpuacctController(CgroupV2CpuController* reader) : _reader(reader) {
    }
    // In cgroup v2, cpu usage is a part of the cpu controller.
    jlong cpu_usage_in_micros() override {
      return reader()->cpu_usage_in_micros();
    }
    bool is_read_only() override {
      return reader()->is_read_only();
    }
    const char* subsystem_path() override {
      return reader()->subsystem_path();
    }
    bool needs_hierarchy_adjustment() override {
      return reader()->needs_hierarchy_adjustment();
    }
    void set_subsystem_path(const char* cgroup_path) override {
      reader()->set_subsystem_path(cgroup_path);
    }
    const char* mount_point() override { return reader()->mount_point(); }
    const char* cgroup_path() override { return reader()->cgroup_path(); }
};

class CgroupV2MemoryController final: public CgroupMemoryController {
  private:
    CgroupV2Controller _reader;
    CgroupV2Controller* reader() { return &_reader; }
  public:
    CgroupV2MemoryController(const CgroupV2Controller& reader) : _reader(reader) {
    }

    jlong read_memory_limit_in_bytes(julong upper_bound) override;
    jlong memory_and_swap_limit_in_bytes(julong host_mem, julong host_swp) override;
    jlong memory_and_swap_usage_in_bytes(julong host_mem, julong host_swp) override;
    jlong memory_soft_limit_in_bytes(julong upper_bound) override;
    jlong memory_throttle_limit_in_bytes() override;
    jlong memory_usage_in_bytes() override;
    jlong memory_max_usage_in_bytes() override;
    jlong rss_usage_in_bytes() override;
    jlong cache_usage_in_bytes() override;
    void print_version_specific_info(outputStream* st, julong host_mem) override;
    bool is_read_only() override {
      return reader()->is_read_only();
    }
    const char* subsystem_path() override {
      return reader()->subsystem_path();
    }
    bool needs_hierarchy_adjustment() override {
      return reader()->needs_hierarchy_adjustment();
    }
    void set_subsystem_path(const char* cgroup_path) override {
      reader()->set_subsystem_path(cgroup_path);
    }
    const char* mount_point() override { return reader()->mount_point(); }
    const char* cgroup_path() override { return reader()->cgroup_path(); }
};

class CgroupV2Subsystem: public CgroupSubsystem {
  private:
    /* One unified controller */
    CgroupV2Controller _unified;
    /* Caching wrappers for cpu/memory metrics */
    CachingCgroupController<CgroupMemoryController>* _memory = nullptr;
    CachingCgroupController<CgroupCpuController>* _cpu = nullptr;

    CgroupCpuacctController* _cpuacct = nullptr;

    CgroupV2Controller* unified() { return &_unified; }

  public:
    CgroupV2Subsystem(CgroupV2MemoryController * memory,
                      CgroupV2CpuController* cpu,
                      CgroupV2CpuacctController* cpuacct,
                      CgroupV2Controller unified);

    char * cpu_cpuset_cpus() override;
    char * cpu_cpuset_memory_nodes() override;
    jlong pids_max() override;
    jlong pids_current() override;

    bool is_containerized() override;

    const char * container_type() override {
      return "cgroupv2";
    }
    CachingCgroupController<CgroupMemoryController>* memory_controller() override { return _memory; }
    CachingCgroupController<CgroupCpuController>* cpu_controller() override { return _cpu; }
    CgroupCpuacctController* cpuacct_controller() override { return _cpuacct; };
};

#endif // CGROUP_V2_SUBSYSTEM_LINUX_HPP
