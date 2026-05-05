/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "cgroupSubsystem_linux.hpp"
#include "cgroupUtil_linux.hpp"
#include "memory/allocation.hpp"
#include "runtime/os.hpp"

// Cgroups version 1 specific implementation

class CgroupV1Controller: public CgroupController {
  private:
    /* mountinfo contents */
    char* _root;
    bool _read_only;

    /* Constructed subsystem directory */
    char* _path;

  public:
    CgroupV1Controller(char *root,
                       char *mountpoint,
                       bool ro) : _root(os::strdup(root)),
                                  _read_only(ro),
                                  _path(nullptr) {
      _cgroup_path = nullptr;
      _mount_point = os::strdup(mountpoint);
    }
    // Shallow copy constructor
    CgroupV1Controller(const CgroupV1Controller& o) : _root(o._root),
                                                      _read_only(o._read_only),
                                                      _path(o._path) {
      _cgroup_path = o._cgroup_path;
      _mount_point = o._mount_point;
    }
    ~CgroupV1Controller() {
      // At least one subsystem controller exists with paths to malloc'd path
      // names
    }

    void set_subsystem_path(const char *cgroup_path);
    const char* subsystem_path() override { return _path; }
    bool is_read_only() override { return _read_only; }
    bool needs_hierarchy_adjustment() override;
};

class CgroupV1MemoryController final : public CgroupMemoryController {

  private:
    CgroupV1Controller _reader;
    CgroupV1Controller* reader() { return &_reader; }
    bool read_memory_limit_val(physical_memory_size_type& result);
    bool read_hierarchical_memory_limit_val(physical_memory_size_type& result);
    bool read_hierarchical_mem_swap_val(physical_memory_size_type& result);
    bool read_use_hierarchy_val(physical_memory_size_type& result);
    bool memory_usage_val(physical_memory_size_type& result);
    bool read_mem_swappiness(physical_memory_size_type& result);
    bool read_mem_swap(physical_memory_size_type& result);
    bool memory_soft_limit_val(physical_memory_size_type& result);
    bool memory_max_usage_val(physical_memory_size_type& result);
    bool kernel_memory_usage_val(physical_memory_size_type& result);
    bool kernel_memory_limit_val(physical_memory_size_type& result);
    bool kernel_memory_max_usage_val(physical_memory_size_type& result);
    bool uses_mem_hierarchy();

  public:
    void set_subsystem_path(const char *cgroup_path) override {
      reader()->set_subsystem_path(cgroup_path);
    }
    bool read_memory_limit_in_bytes(physical_memory_size_type upper_bound,
                                    physical_memory_size_type& value) override;
    bool memory_usage_in_bytes(physical_memory_size_type& result) override;
    bool memory_and_swap_limit_in_bytes(physical_memory_size_type upper_mem_bound,
                                        physical_memory_size_type upper_swap_bound,
                                        physical_memory_size_type& result) override;
    bool memory_and_swap_usage_in_bytes(physical_memory_size_type upper_mem_bound,
                                        physical_memory_size_type upper_swap_bound,
                                        physical_memory_size_type& result) override;
    bool memory_soft_limit_in_bytes(physical_memory_size_type upper_bound,
                                    physical_memory_size_type& result) override;
    bool memory_throttle_limit_in_bytes(physical_memory_size_type& result) override;
    bool memory_max_usage_in_bytes(physical_memory_size_type& result) override;
    bool rss_usage_in_bytes(physical_memory_size_type& result) override;
    bool cache_usage_in_bytes(physical_memory_size_type& result) override;
    bool kernel_memory_usage_in_bytes(physical_memory_size_type& result);
    bool kernel_memory_limit_in_bytes(physical_memory_size_type upper_bound,
                                      physical_memory_size_type& result);
    bool kernel_memory_max_usage_in_bytes(physical_memory_size_type& result);
    void print_version_specific_info(outputStream* st, physical_memory_size_type upper_mem_bound) override;
    bool needs_hierarchy_adjustment() override {
      return reader()->needs_hierarchy_adjustment();
    }
    bool is_read_only() override {
      return reader()->is_read_only();
    }
    const char* subsystem_path() override { return reader()->subsystem_path(); }
    const char* mount_point() override { return reader()->mount_point(); }
    const char* cgroup_path() override { return reader()->cgroup_path(); }

  public:
    CgroupV1MemoryController(const CgroupV1Controller& reader)
      : _reader(reader) {
    }

};

class CgroupV1CpuController final : public CgroupCpuController {

  private:
    CgroupV1Controller _reader;
    CgroupV1Controller* reader() { return &_reader; }
    bool cpu_period_val(uint64_t& result);
    bool cpu_shares_val(uint64_t& result);
  public:
    bool cpu_quota(int& result) override;
    bool cpu_period(int& result) override;
    bool cpu_shares(int& result) override;
    void set_subsystem_path(const char *cgroup_path) override {
      reader()->set_subsystem_path(cgroup_path);
    }
    bool is_read_only() override {
      return reader()->is_read_only();
    }
    const char* subsystem_path() override {
      return reader()->subsystem_path();
    }
    const char* mount_point() override {
      return reader()->mount_point();
    }
    bool needs_hierarchy_adjustment() override {
      return reader()->needs_hierarchy_adjustment();
    }
    const char* cgroup_path() override { return reader()->cgroup_path(); }

  public:
    CgroupV1CpuController(const CgroupV1Controller& reader) : _reader(reader) {
    }
};

class CgroupV1CpuacctController final : public CgroupCpuacctController {

  private:
    CgroupV1Controller _reader;
    CgroupV1Controller* reader() { return &_reader; }
    bool cpu_usage_in_micros_val(uint64_t& result);
  public:
    bool cpu_usage_in_micros(uint64_t& result) override;
    void set_subsystem_path(const char *cgroup_path) override {
      reader()->set_subsystem_path(cgroup_path);
    }
    bool is_read_only() override {
      return reader()->is_read_only();
    }
    const char* subsystem_path() override {
      return reader()->subsystem_path();
    }
    const char* mount_point() override {
      return reader()->mount_point();
    }
    bool needs_hierarchy_adjustment() override {
      return reader()->needs_hierarchy_adjustment();
    }
    const char* cgroup_path() override { return reader()->cgroup_path(); }

  public:
    CgroupV1CpuacctController(const CgroupV1Controller& reader) : _reader(reader) {
    }
};

class CgroupV1Subsystem: public CgroupSubsystem {

  public:
    CgroupV1Subsystem(CgroupV1Controller* cpuset,
                      CgroupV1CpuController* cpu,
                      CgroupV1CpuacctController* cpuacct,
                      CgroupV1Controller* pids,
                      CgroupV1MemoryController* memory);

    bool kernel_memory_usage_in_bytes(physical_memory_size_type& result);
    bool kernel_memory_limit_in_bytes(physical_memory_size_type& result);
    bool kernel_memory_max_usage_in_bytes(physical_memory_size_type& result);

    char * cpu_cpuset_cpus() override;
    char * cpu_cpuset_memory_nodes() override;

    bool pids_max(uint64_t& result) override;
    bool pids_current(uint64_t& result) override;
    bool is_containerized() override;

    const char * container_type() override {
      return "cgroupv1";
    }
    CachingCgroupController<CgroupMemoryController, physical_memory_size_type>* memory_controller() override { return _memory; }
    CachingCgroupController<CgroupCpuController, double>* cpu_controller() override { return _cpu; }
    CgroupCpuacctController* cpuacct_controller() override { return _cpuacct; }

  private:
    /* controllers */
    CachingCgroupController<CgroupMemoryController, physical_memory_size_type>* _memory = nullptr;
    CgroupV1Controller* _cpuset = nullptr;
    CachingCgroupController<CgroupCpuController, double>* _cpu = nullptr;
    CgroupV1CpuacctController* _cpuacct = nullptr;
    CgroupV1Controller* _pids = nullptr;

};

#endif // CGROUP_V1_SUBSYSTEM_LINUX_HPP
