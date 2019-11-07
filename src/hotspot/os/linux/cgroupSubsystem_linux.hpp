/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

typedef char * cptr;

class CgroupController: public CHeapObj<mtInternal> {
  public:
    virtual char *subsystem_path();
};

PRAGMA_DIAG_PUSH
PRAGMA_FORMAT_NONLITERAL_IGNORED
template <typename T> int subsystem_file_line_contents(CgroupController* c,
                                              const char *filename,
                                              const char *matchline,
                                              const char *scan_fmt,
                                              T returnval) {
  FILE *fp = NULL;
  char *p;
  char file[MAXPATHLEN+1];
  char buf[MAXPATHLEN+1];
  char discard[MAXPATHLEN+1];
  bool found_match = false;

  if (c == NULL) {
    log_debug(os, container)("subsystem_file_line_contents: CgroupController* is NULL");
    return OSCONTAINER_ERROR;
  }
  if (c->subsystem_path() == NULL) {
    log_debug(os, container)("subsystem_file_line_contents: subsystem path is NULL");
    return OSCONTAINER_ERROR;
  }

  strncpy(file, c->subsystem_path(), MAXPATHLEN);
  file[MAXPATHLEN-1] = '\0';
  int filelen = strlen(file);
  if ((filelen + strlen(filename)) > (MAXPATHLEN-1)) {
    log_debug(os, container)("File path too long %s, %s", file, filename);
    return OSCONTAINER_ERROR;
  }
  strncat(file, filename, MAXPATHLEN-filelen);
  log_trace(os, container)("Path to %s is %s", filename, file);
  fp = fopen(file, "r");
  if (fp != NULL) {
    int err = 0;
    while ((p = fgets(buf, MAXPATHLEN, fp)) != NULL) {
      found_match = false;
      if (matchline == NULL) {
        // single-line file case
        int matched = sscanf(p, scan_fmt, returnval);
        found_match = (matched == 1);
      } else {
        // multi-line file case
        if (strstr(p, matchline) != NULL) {
          // discard matchline string prefix
          int matched = sscanf(p, scan_fmt, discard, returnval);
          found_match = (matched == 2);
        } else {
          continue; // substring not found
        }
      }
      if (found_match) {
        fclose(fp);
        return 0;
      } else {
        err = 1;
        log_debug(os, container)("Type %s not found in file %s", scan_fmt, file);
      }
    }
    if (err == 0) {
      log_debug(os, container)("Empty file %s", file);
    }
  } else {
    log_debug(os, container)("Open of file %s failed, %s", file, os::strerror(errno));
  }
  if (fp != NULL)
    fclose(fp);
  return OSCONTAINER_ERROR;
}
PRAGMA_DIAG_POP

#define GET_CONTAINER_INFO(return_type, subsystem, filename,              \
                           logstring, scan_fmt, variable)                 \
  return_type variable;                                                   \
{                                                                         \
  int err;                                                                \
  err = subsystem_file_line_contents(subsystem,                           \
                                     filename,                            \
                                     NULL,                                \
                                     scan_fmt,                            \
                                     &variable);                          \
  if (err != 0)                                                           \
    return (return_type) OSCONTAINER_ERROR;                               \
                                                                          \
  log_trace(os, container)(logstring, variable);                          \
}

#define GET_CONTAINER_INFO_CPTR(return_type, subsystem, filename,         \
                               logstring, scan_fmt, variable, bufsize)    \
  char variable[bufsize];                                                 \
{                                                                         \
  int err;                                                                \
  err = subsystem_file_line_contents(subsystem,                           \
                                     filename,                            \
                                     NULL,                                \
                                     scan_fmt,                            \
                                     variable);                           \
  if (err != 0)                                                           \
    return (return_type) NULL;                                            \
                                                                          \
  log_trace(os, container)(logstring, variable);                          \
}

#define GET_CONTAINER_INFO_LINE(return_type, controller, filename,        \
                           matchline, logstring, scan_fmt, variable)      \
  return_type variable;                                                   \
{                                                                         \
  int err;                                                                \
  err = subsystem_file_line_contents(controller,                          \
                                filename,                                 \
                                matchline,                                \
                                scan_fmt,                                 \
                                &variable);                               \
  if (err != 0)                                                           \
    return (return_type) OSCONTAINER_ERROR;                               \
                                                                          \
  log_trace(os, container)(logstring, variable);                          \
}

// Four controllers: cpu, cpuset, cpuacct, memory
#define CG_INFO_LENGTH 4

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

class CachingCgroupController : public CHeapObj<mtInternal> {
  private:
    CgroupController* _controller;
    CachedMetric* _metrics_cache;

  public:
    CachingCgroupController(CgroupController* cont) {
      _controller = cont;
      _metrics_cache = new CachedMetric();
    }

    CachedMetric* metrics_cache() { return _metrics_cache; }
    CgroupController* controller() { return _controller; }
};

class CgroupSubsystem: public CHeapObj<mtInternal> {
  public:
    jlong memory_limit_in_bytes();
    int active_processor_count();

    virtual int cpu_quota();
    virtual int cpu_period();
    virtual int cpu_shares();
    virtual jlong memory_usage_in_bytes();
    virtual jlong memory_and_swap_limit_in_bytes();
    virtual jlong memory_soft_limit_in_bytes();
    virtual jlong memory_max_usage_in_bytes();
    virtual char * cpu_cpuset_cpus();
    virtual char * cpu_cpuset_memory_nodes();
    virtual jlong read_memory_limit_in_bytes();
    virtual const char * container_type();
    virtual CachingCgroupController* memory_controller();
    virtual CachingCgroupController* cpu_controller();
};

class CgroupSubsystemFactory: AllStatic {
  public:
    static CgroupSubsystem* create();
};

// Class representing info in /proc/self/cgroup.
// See man 7 cgroups
class CgroupInfo : public StackObj {
  friend class CgroupSubsystemFactory;

  private:
  char* _name;
  int _hierarchy_id;
  bool _enabled;
  char* _cgroup_path;

};


#endif // CGROUP_SUBSYSTEM_LINUX_HPP
