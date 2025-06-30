/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CGROUP_SUBSYSTEM_LINUX_HPP
#define CGROUP_SUBSYSTEM_LINUX_HPP

#include "memory/allocation.hpp"
#include "runtime/os.hpp"
#include "logging/log.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include "osContainer_linux.hpp"

// Shared cgroups code (used by cgroup version 1 and version 2)

/*
 * PER_CPU_SHARES has been set to 1024 because CPU shares' quota
 * is commonly used in cloud frameworks like Kubernetes[1],
 * AWS[2] and Mesos[3] in a similar way. They spawn containers with
 * --cpu-shares option values scaled by PER_CPU_SHARES. Thus, we do
 * the inverse for determining the number of possible available
 * CPUs to the JVM inside a container. See JDK-8216366.
 *
 * [1] https://kubernetes.io/docs/concepts/configuration/manage-compute-resources-container/#meaning-of-cpu
 *     In particular:
 *        When using Docker:
 *          The spec.containers[].resources.requests.cpu is converted to its core value, which is potentially
 *          fractional, and multiplied by 1024. The greater of this number or 2 is used as the value of the
 *          --cpu-shares flag in the docker run command.
 * [2] https://docs.aws.amazon.com/AmazonECS/latest/APIReference/API_ContainerDefinition.html
 * [3] https://github.com/apache/mesos/blob/3478e344fb77d931f6122980c6e94cd3913c441d/src/docker/docker.cpp#L648
 *     https://github.com/apache/mesos/blob/3478e344fb77d931f6122980c6e94cd3913c441d/src/slave/containerizer/mesos/isolators/cgroups/constants.hpp#L30
 */
#define PER_CPU_SHARES 1024

#define CGROUPS_V1               1
#define CGROUPS_V2               2
#define INVALID_CGROUPS_V2       3
#define INVALID_CGROUPS_V1       4
#define INVALID_CGROUPS_NO_MOUNT 5
#define INVALID_CGROUPS_GENERIC  6

// Five controllers: cpu, cpuset, cpuacct, memory, pids
#define CG_INFO_LENGTH 5
#define CPUSET_IDX     0
#define CPU_IDX        1
#define CPUACCT_IDX    2
#define MEMORY_IDX     3
#define PIDS_IDX       4

#define CONTAINER_READ_NUMBER_CHECKED(controller, filename, log_string, retval)       \
{                                                                                     \
  bool is_ok;                                                                         \
  is_ok = controller->read_number(filename, &retval);                                 \
  if (!is_ok) {                                                                       \
    log_trace(os, container)(log_string " failed: %d", OSCONTAINER_ERROR);            \
    return OSCONTAINER_ERROR;                                                         \
  }                                                                                   \
  log_trace(os, container)(log_string " is: " JULONG_FORMAT, retval);                 \
}

#define CONTAINER_READ_NUMBER_CHECKED_MAX(controller, filename, log_string, retval)   \
{                                                                                     \
  bool is_ok;                                                                         \
  is_ok = controller->read_number_handle_max(filename, &retval);                      \
  if (!is_ok) {                                                                       \
    log_trace(os, container)(log_string " failed: %d", OSCONTAINER_ERROR);            \
    return OSCONTAINER_ERROR;                                                         \
  }                                                                                   \
  log_trace(os, container)(log_string " is: " JLONG_FORMAT, retval);                  \
}

#define CONTAINER_READ_STRING_CHECKED(controller, filename, log_string, retval, buf_size) \
{                                                                                         \
  bool is_ok;                                                                             \
  is_ok = controller->read_string(filename, retval, buf_size);                            \
  if (!is_ok) {                                                                           \
    log_trace(os, container)(log_string " failed: %d", OSCONTAINER_ERROR);                \
    return nullptr;                                                                       \
  }                                                                                       \
  log_trace(os, container)(log_string " is: %s", retval);                                 \
}

class CgroupController: public CHeapObj<mtInternal> {
  protected:
    char* _cgroup_path;
    char* _mount_point;
  public:
    virtual const char* subsystem_path() = 0;
    virtual bool is_read_only() = 0;
    const char* cgroup_path() { return _cgroup_path; }
    const char* mount_point() { return _mount_point; }
    virtual bool needs_hierarchy_adjustment() { return false; }

    /* Read a numerical value as unsigned long
     *
     * returns: false if any error occurred. true otherwise and
     * the parsed value is set in the provided julong pointer.
     */
    bool read_number(const char* filename, julong* result);

    /* Convenience method to deal with numbers as well as the string 'max'
     * in interface files. Otherwise same as read_number().
     *
     * returns: false if any error occurred. true otherwise and
     * the parsed value (which might be negative) is being set in
     * the provided jlong pointer.
     */
    bool read_number_handle_max(const char* filename, jlong* result);

    /* Read a string of at most buf_size - 1 characters from the interface file.
     * The provided buffer must be at least buf_size in size so as to account
     * for the null terminating character. Callers must ensure that the buffer
     * is appropriately in-scope and of sufficient size.
     *
     * returns: false if any error occured. true otherwise and the passed
     * in buffer will contain the first buf_size - 1 characters of the string
     * or up to the first new line character ('\n') whichever comes first.
     */
    bool read_string(const char* filename, char* buf, size_t buf_size);

    /* Read a tuple value as a number. Tuple is: '<first> <second>'.
     * Handles 'max' (for unlimited) for any tuple value. This is handy for
     * parsing interface files like cpu.max which contain such tuples.
     *
     * returns: false if any error occurred. true otherwise and the parsed
     * value of the appropriate tuple entry set in the provided jlong pointer.
     */
    bool read_numerical_tuple_value(const char* filename, bool use_first, jlong* result);

    /* Read a numerical value from a multi-line interface file. The matched line is
     * determined by the provided 'key'. The associated numerical value is being set
     * via the passed in julong pointer. Example interface file 'memory.stat'
     *
     * returns: false if any error occurred. true otherwise and the parsed value is
     * being set in the provided julong pointer.
     */
    bool read_numerical_key_value(const char* filename, const char* key, julong* result);

  private:
    static jlong limit_from_str(char* limit_str);
};

class CachedMetric : public CHeapObj<mtInternal>{
  private:
    volatile jlong _metric;
    volatile jlong _next_check_counter;
  public:
    CachedMetric() {
      _metric = -1;
      _next_check_counter = min_jlong;
    }
    bool should_check_metric() {
      return os::elapsed_counter() > _next_check_counter;
    }
    jlong value() { return _metric; }
    void set_value(jlong value, jlong timeout) {
      _metric = value;
      // Metric is unlikely to change, but we want to remain
      // responsive to configuration changes. A very short grace time
      // between re-read avoids excessive overhead during startup without
      // significantly reducing the VMs ability to promptly react to changed
      // metric config
      _next_check_counter = os::elapsed_counter() + timeout;
    }
};

template <class T>
class CachingCgroupController : public CHeapObj<mtInternal> {
  private:
    T* _controller;
    CachedMetric* _metrics_cache;

  public:
    CachingCgroupController(T* cont) {
      _controller = cont;
      _metrics_cache = new CachedMetric();
    }

    CachedMetric* metrics_cache() { return _metrics_cache; }
    T* controller() { return _controller; }
};

// Pure virtual class representing version agnostic CPU controllers
class CgroupCpuController: public CHeapObj<mtInternal> {
  public:
    virtual int cpu_quota() = 0;
    virtual int cpu_period() = 0;
    virtual int cpu_shares() = 0;
    virtual bool needs_hierarchy_adjustment() = 0;
    virtual bool is_read_only() = 0;
    virtual const char* subsystem_path() = 0;
    virtual void set_subsystem_path(const char* cgroup_path) = 0;
    virtual const char* mount_point() = 0;
    virtual const char* cgroup_path() = 0;
};

// Pure virtual class representing version agnostic CPU accounting controllers
class CgroupCpuacctController: public CHeapObj<mtInternal> {
  public:
    virtual jlong cpu_usage_in_micros() = 0;
    virtual bool needs_hierarchy_adjustment() = 0;
    virtual bool is_read_only() = 0;
    virtual const char* subsystem_path() = 0;
    virtual void set_subsystem_path(const char* cgroup_path) = 0;
    virtual const char* mount_point() = 0;
    virtual const char* cgroup_path() = 0;
};

// Pure virtual class representing version agnostic memory controllers
class CgroupMemoryController: public CHeapObj<mtInternal> {
  public:
    virtual jlong read_memory_limit_in_bytes(julong upper_bound) = 0;
    virtual jlong memory_usage_in_bytes() = 0;
    virtual jlong memory_and_swap_limit_in_bytes(julong host_mem, julong host_swap) = 0;
    virtual jlong memory_and_swap_usage_in_bytes(julong host_mem, julong host_swap) = 0;
    virtual jlong memory_soft_limit_in_bytes(julong upper_bound) = 0;
    virtual jlong memory_throttle_limit_in_bytes() = 0;
    virtual jlong memory_max_usage_in_bytes() = 0;
    virtual jlong rss_usage_in_bytes() = 0;
    virtual jlong cache_usage_in_bytes() = 0;
    virtual void print_version_specific_info(outputStream* st, julong host_mem) = 0;
    virtual bool needs_hierarchy_adjustment() = 0;
    virtual bool is_read_only() = 0;
    virtual const char* subsystem_path() = 0;
    virtual void set_subsystem_path(const char* cgroup_path) = 0;
    virtual const char* mount_point() = 0;
    virtual const char* cgroup_path() = 0;
};

class CgroupSubsystem: public CHeapObj<mtInternal> {
  public:
    jlong memory_limit_in_bytes();
    int active_processor_count();

    virtual jlong pids_max() = 0;
    virtual jlong pids_current() = 0;
    virtual bool is_containerized() = 0;

    virtual char * cpu_cpuset_cpus() = 0;
    virtual char * cpu_cpuset_memory_nodes() = 0;
    virtual const char * container_type() = 0;
    virtual CachingCgroupController<CgroupMemoryController>* memory_controller() = 0;
    virtual CachingCgroupController<CgroupCpuController>* cpu_controller() = 0;
    virtual CgroupCpuacctController* cpuacct_controller() = 0;

    int cpu_quota();
    int cpu_period();
    int cpu_shares();

    jlong cpu_usage_in_micros();

    jlong memory_usage_in_bytes();
    jlong memory_and_swap_limit_in_bytes();
    jlong memory_and_swap_usage_in_bytes();
    jlong memory_soft_limit_in_bytes();
    jlong memory_throttle_limit_in_bytes();
    jlong memory_max_usage_in_bytes();
    jlong rss_usage_in_bytes();
    jlong cache_usage_in_bytes();
    void print_version_specific_info(outputStream* st);
};

// Utility class for storing info retrieved from /proc/cgroups,
// /proc/self/cgroup and /proc/self/mountinfo
// For reference see man 7 cgroups and CgroupSubsystemFactory
class CgroupInfo : public StackObj {
  friend class CgroupSubsystemFactory;
  friend class WhiteBox;

  private:
    char* _name;
    int _hierarchy_id;
    bool _enabled;
    bool _read_only;            // whether or not the mount path is mounted read-only
    bool _data_complete;    // indicating cgroup v1 data is complete for this controller
    char* _cgroup_path;     // cgroup controller path from /proc/self/cgroup
    char* _root_mount_path; // root mount path from /proc/self/mountinfo. Unused for cgroup v2
    char* _mount_path;      // mount path from /proc/self/mountinfo.

  public:
    CgroupInfo() {
      _name = nullptr;
      _hierarchy_id = -1;
      _enabled = false;
      _read_only = false;
      _data_complete = false;
      _cgroup_path = nullptr;
      _root_mount_path = nullptr;
      _mount_path = nullptr;
    }

};

class CgroupSubsystemFactory: AllStatic {
  friend class WhiteBox;

  public:
    static CgroupSubsystem* create();
  private:
    static inline bool is_cgroup_v2(u1* flags) {
       return *flags == CGROUPS_V2;
    }

#ifdef ASSERT
    static inline bool is_valid_cgroup(u1* flags) {
       return *flags == CGROUPS_V1 || *flags == CGROUPS_V2;
    }
    static inline bool is_cgroup_v1(u1* flags) {
       return *flags == CGROUPS_V1;
    }
#endif

    static void set_controller_paths(CgroupInfo* cg_infos,
                                     int controller,
                                     const char* name,
                                     char* mount_path,
                                     char* root_path,
                                     bool read_only);
    // Determine the cgroup type (version 1 or version 2), given
    // relevant paths to files. Sets 'flags' accordingly.
    static bool determine_type(CgroupInfo* cg_infos,
                               bool cgroups_v2_enabled,
                               const char* controllers_file,
                               const char* proc_self_cgroup,
                               const char* proc_self_mountinfo,
                               u1* flags);
    static void cleanup(CgroupInfo* cg_infos);
};

#endif // CGROUP_SUBSYSTEM_LINUX_HPP
