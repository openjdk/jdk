/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/globalDefinitions.hpp"
#include "memory/allocation.hpp"
#include "runtime/os.hpp"
#include "logging/log.hpp"
#include "osContainer_linux.hpp"

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

bool  OSContainer::_is_initialized   = false;
bool  OSContainer::_is_containerized = false;
julong _unlimited_memory;

class CgroupSubsystem: CHeapObj<mtInternal> {
 friend class OSContainer;

 private:
    /* mountinfo contents */
    char *_root;
    char *_mount_point;

    /* Constructed subsystem directory */
    char *_path;

 public:
    CgroupSubsystem(char *root, char *mountpoint) {
      _root = os::strdup(root);
      _mount_point = os::strdup(mountpoint);
      _path = NULL;
    }

    /*
     * Set directory to subsystem specific files based
     * on the contents of the mountinfo and cgroup files.
     */
    void set_subsystem_path(char *cgroup_path) {
      char buf[MAXPATHLEN+1];
      if (_root != NULL && cgroup_path != NULL) {
        if (strcmp(_root, "/") == 0) {
          int buflen;
          strncpy(buf, _mount_point, MAXPATHLEN);
          buf[MAXPATHLEN-1] = '\0';
          if (strcmp(cgroup_path,"/") != 0) {
            buflen = strlen(buf);
            if ((buflen + strlen(cgroup_path)) > (MAXPATHLEN-1)) {
              return;
            }
            strncat(buf, cgroup_path, MAXPATHLEN-buflen);
            buf[MAXPATHLEN-1] = '\0';
          }
          _path = os::strdup(buf);
        } else {
          if (strcmp(_root, cgroup_path) == 0) {
            strncpy(buf, _mount_point, MAXPATHLEN);
            buf[MAXPATHLEN-1] = '\0';
            _path = os::strdup(buf);
          } else {
            char *p = strstr(cgroup_path, _root);
            if (p != NULL && p == _root) {
              if (strlen(cgroup_path) > strlen(_root)) {
                int buflen;
                strncpy(buf, _mount_point, MAXPATHLEN);
                buf[MAXPATHLEN-1] = '\0';
                buflen = strlen(buf);
                if ((buflen + strlen(cgroup_path) - strlen(_root)) > (MAXPATHLEN-1)) {
                  return;
                }
                strncat(buf, cgroup_path + strlen(_root), MAXPATHLEN-buflen);
                buf[MAXPATHLEN-1] = '\0';
                _path = os::strdup(buf);
              }
            }
          }
        }
      }
    }

    char *subsystem_path() { return _path; }
};

CgroupSubsystem* memory = NULL;
CgroupSubsystem* cpuset = NULL;
CgroupSubsystem* cpu = NULL;
CgroupSubsystem* cpuacct = NULL;

typedef char * cptr;

PRAGMA_DIAG_PUSH
PRAGMA_FORMAT_NONLITERAL_IGNORED
template <typename T> int subsystem_file_contents(CgroupSubsystem* c,
                                              const char *filename,
                                              const char *scan_fmt,
                                              T returnval) {
  FILE *fp = NULL;
  char *p;
  char file[MAXPATHLEN+1];
  char buf[MAXPATHLEN+1];

  if (c == NULL) {
    log_debug(os, container)("subsystem_file_contents: CgroupSubsytem* is NULL");
    return OSCONTAINER_ERROR;
  }
  if (c->subsystem_path() == NULL) {
    log_debug(os, container)("subsystem_file_contents: subsystem path is NULL");
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
    p = fgets(buf, MAXPATHLEN, fp);
    if (p != NULL) {
      int matched = sscanf(p, scan_fmt, returnval);
      if (matched == 1) {
        fclose(fp);
        return 0;
      } else {
        log_debug(os, container)("Type %s not found in file %s", scan_fmt, file);
      }
    } else {
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
  err = subsystem_file_contents(subsystem,                                \
                                filename,                                 \
                                scan_fmt,                                 \
                                &variable);                               \
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
  err = subsystem_file_contents(subsystem,                                \
                                filename,                                 \
                                scan_fmt,                                 \
                                variable);                                \
  if (err != 0)                                                           \
    return (return_type) NULL;                                            \
                                                                          \
  log_trace(os, container)(logstring, variable);                          \
}

/* init
 *
 * Initialize the container support and determine if
 * we are running under cgroup control.
 */
void OSContainer::init() {
  FILE *mntinfo = NULL;
  FILE *cgroup = NULL;
  char buf[MAXPATHLEN+1];
  char tmproot[MAXPATHLEN+1];
  char tmpmount[MAXPATHLEN+1];
  char *p;
  jlong mem_limit;

  assert(!_is_initialized, "Initializing OSContainer more than once");

  _is_initialized = true;
  _is_containerized = false;

  _unlimited_memory = (LONG_MAX / os::vm_page_size()) * os::vm_page_size();

  log_trace(os, container)("OSContainer::init: Initializing Container Support");
  if (!UseContainerSupport) {
    log_trace(os, container)("Container Support not enabled");
    return;
  }

  /*
   * Find the cgroup mount point for memory and cpuset
   * by reading /proc/self/mountinfo
   *
   * Example for docker:
   * 219 214 0:29 /docker/7208cebd00fa5f2e342b1094f7bed87fa25661471a4637118e65f1c995be8a34 /sys/fs/cgroup/memory ro,nosuid,nodev,noexec,relatime - cgroup cgroup rw,memory
   *
   * Example for host:
   * 34 28 0:29 / /sys/fs/cgroup/memory rw,nosuid,nodev,noexec,relatime shared:16 - cgroup cgroup rw,memory
   */
  mntinfo = fopen("/proc/self/mountinfo", "r");
  if (mntinfo == NULL) {
      log_debug(os, container)("Can't open /proc/self/mountinfo, %s",
                               os::strerror(errno));
      return;
  }

  while ((p = fgets(buf, MAXPATHLEN, mntinfo)) != NULL) {
    char tmpcgroups[MAXPATHLEN+1];
    char *cptr = tmpcgroups;
    char *token;

    // mountinfo format is documented at https://www.kernel.org/doc/Documentation/filesystems/proc.txt
    if (sscanf(p, "%*d %*d %*d:%*d %s %s %*[^-]- cgroup %*s %s", tmproot, tmpmount, tmpcgroups) != 3) {
      continue;
    }
    while ((token = strsep(&cptr, ",")) != NULL) {
      if (strcmp(token, "memory") == 0) {
        memory = new CgroupSubsystem(tmproot, tmpmount);
      } else if (strcmp(token, "cpuset") == 0) {
        cpuset = new CgroupSubsystem(tmproot, tmpmount);
      } else if (strcmp(token, "cpu") == 0) {
        cpu = new CgroupSubsystem(tmproot, tmpmount);
      } else if (strcmp(token, "cpuacct") == 0) {
        cpuacct= new CgroupSubsystem(tmproot, tmpmount);
      }
    }
  }

  fclose(mntinfo);

  if (memory == NULL) {
    log_debug(os, container)("Required cgroup memory subsystem not found");
    return;
  }
  if (cpuset == NULL) {
    log_debug(os, container)("Required cgroup cpuset subsystem not found");
    return;
  }
  if (cpu == NULL) {
    log_debug(os, container)("Required cgroup cpu subsystem not found");
    return;
  }
  if (cpuacct == NULL) {
    log_debug(os, container)("Required cgroup cpuacct subsystem not found");
    return;
  }

  /*
   * Read /proc/self/cgroup and map host mount point to
   * local one via /proc/self/mountinfo content above
   *
   * Docker example:
   * 5:memory:/docker/6558aed8fc662b194323ceab5b964f69cf36b3e8af877a14b80256e93aecb044
   *
   * Host example:
   * 5:memory:/user.slice
   *
   * Construct a path to the process specific memory and cpuset
   * cgroup directory.
   *
   * For a container running under Docker from memory example above
   * the paths would be:
   *
   * /sys/fs/cgroup/memory
   *
   * For a Host from memory example above the path would be:
   *
   * /sys/fs/cgroup/memory/user.slice
   *
   */
  cgroup = fopen("/proc/self/cgroup", "r");
  if (cgroup == NULL) {
    log_debug(os, container)("Can't open /proc/self/cgroup, %s",
                             os::strerror(errno));
    return;
  }

  while ((p = fgets(buf, MAXPATHLEN, cgroup)) != NULL) {
    char *controllers;
    char *token;
    char *base;

    /* Skip cgroup number */
    strsep(&p, ":");
    /* Get controllers and base */
    controllers = strsep(&p, ":");
    base = strsep(&p, "\n");

    if (controllers == NULL) {
      continue;
    }

    while ((token = strsep(&controllers, ",")) != NULL) {
      if (strcmp(token, "memory") == 0) {
        memory->set_subsystem_path(base);
      } else if (strcmp(token, "cpuset") == 0) {
        cpuset->set_subsystem_path(base);
      } else if (strcmp(token, "cpu") == 0) {
        cpu->set_subsystem_path(base);
      } else if (strcmp(token, "cpuacct") == 0) {
        cpuacct->set_subsystem_path(base);
      }
    }
  }

  fclose(cgroup);

  // We need to update the amount of physical memory now that
  // command line arguments have been processed.
  if ((mem_limit = memory_limit_in_bytes()) > 0) {
    os::Linux::set_physical_memory(mem_limit);
  }

  _is_containerized = true;

}

const char * OSContainer::container_type() {
  if (is_containerized()) {
    return "cgroupv1";
  } else {
    return NULL;
  }
}


/* memory_limit_in_bytes
 *
 * Return the limit of available memory for this process.
 *
 * return:
 *    memory limit in bytes or
 *    -1 for unlimited
 *    OSCONTAINER_ERROR for not supported
 */
jlong OSContainer::memory_limit_in_bytes() {
  GET_CONTAINER_INFO(julong, memory, "/memory.limit_in_bytes",
                     "Memory Limit is: " JULONG_FORMAT, JULONG_FORMAT, memlimit);

  if (memlimit >= _unlimited_memory) {
    log_trace(os, container)("Memory Limit is: Unlimited");
    return (jlong)-1;
  }
  else {
    return (jlong)memlimit;
  }
}

jlong OSContainer::memory_and_swap_limit_in_bytes() {
  GET_CONTAINER_INFO(julong, memory, "/memory.memsw.limit_in_bytes",
                     "Memory and Swap Limit is: " JULONG_FORMAT, JULONG_FORMAT, memswlimit);
  if (memswlimit >= _unlimited_memory) {
    log_trace(os, container)("Memory and Swap Limit is: Unlimited");
    return (jlong)-1;
  } else {
    return (jlong)memswlimit;
  }
}

jlong OSContainer::memory_soft_limit_in_bytes() {
  GET_CONTAINER_INFO(julong, memory, "/memory.soft_limit_in_bytes",
                     "Memory Soft Limit is: " JULONG_FORMAT, JULONG_FORMAT, memsoftlimit);
  if (memsoftlimit >= _unlimited_memory) {
    log_trace(os, container)("Memory Soft Limit is: Unlimited");
    return (jlong)-1;
  } else {
    return (jlong)memsoftlimit;
  }
}

/* memory_usage_in_bytes
 *
 * Return the amount of used memory for this process.
 *
 * return:
 *    memory usage in bytes or
 *    -1 for unlimited
 *    OSCONTAINER_ERROR for not supported
 */
jlong OSContainer::memory_usage_in_bytes() {
  GET_CONTAINER_INFO(jlong, memory, "/memory.usage_in_bytes",
                     "Memory Usage is: " JLONG_FORMAT, JLONG_FORMAT, memusage);
  return memusage;
}

/* memory_max_usage_in_bytes
 *
 * Return the maximum amount of used memory for this process.
 *
 * return:
 *    max memory usage in bytes or
 *    OSCONTAINER_ERROR for not supported
 */
jlong OSContainer::memory_max_usage_in_bytes() {
  GET_CONTAINER_INFO(jlong, memory, "/memory.max_usage_in_bytes",
                     "Maximum Memory Usage is: " JLONG_FORMAT, JLONG_FORMAT, memmaxusage);
  return memmaxusage;
}

/* active_processor_count
 *
 * Calculate an appropriate number of active processors for the
 * VM to use based on these three inputs.
 *
 * cpu affinity
 * cgroup cpu quota & cpu period
 * cgroup cpu shares
 *
 * Algorithm:
 *
 * Determine the number of available CPUs from sched_getaffinity
 *
 * If user specified a quota (quota != -1), calculate the number of
 * required CPUs by dividing quota by period.
 *
 * If shares are in effect (shares != -1), calculate the number
 * of CPUs required for the shares by dividing the share value
 * by PER_CPU_SHARES.
 *
 * All results of division are rounded up to the next whole number.
 *
 * If neither shares or quotas have been specified, return the
 * number of active processors in the system.
 *
 * If both shares and quotas have been specified, the results are
 * based on the flag PreferContainerQuotaForCPUCount.  If true,
 * return the quota value.  If false return the smallest value
 * between shares or quotas.
 *
 * If shares and/or quotas have been specified, the resulting number
 * returned will never exceed the number of active processors.
 *
 * return:
 *    number of CPUs
 */
int OSContainer::active_processor_count() {
  int quota_count = 0, share_count = 0;
  int cpu_count, limit_count;
  int result;

  cpu_count = limit_count = os::Linux::active_processor_count();
  int quota  = cpu_quota();
  int period = cpu_period();
  int share  = cpu_shares();

  if (quota > -1 && period > 0) {
    quota_count = ceilf((float)quota / (float)period);
    log_trace(os, container)("CPU Quota count based on quota/period: %d", quota_count);
  }
  if (share > -1) {
    share_count = ceilf((float)share / (float)PER_CPU_SHARES);
    log_trace(os, container)("CPU Share count based on shares: %d", share_count);
  }

  // If both shares and quotas are setup results depend
  // on flag PreferContainerQuotaForCPUCount.
  // If true, limit CPU count to quota
  // If false, use minimum of shares and quotas
  if (quota_count !=0 && share_count != 0) {
    if (PreferContainerQuotaForCPUCount) {
      limit_count = quota_count;
    } else {
      limit_count = MIN2(quota_count, share_count);
    }
  } else if (quota_count != 0) {
    limit_count = quota_count;
  } else if (share_count != 0) {
    limit_count = share_count;
  }

  result = MIN2(cpu_count, limit_count);
  log_trace(os, container)("OSContainer::active_processor_count: %d", result);
  return result;
}

char * OSContainer::cpu_cpuset_cpus() {
  GET_CONTAINER_INFO_CPTR(cptr, cpuset, "/cpuset.cpus",
                     "cpuset.cpus is: %s", "%1023s", cpus, 1024);
  return os::strdup(cpus);
}

char * OSContainer::cpu_cpuset_memory_nodes() {
  GET_CONTAINER_INFO_CPTR(cptr, cpuset, "/cpuset.mems",
                     "cpuset.mems is: %s", "%1023s", mems, 1024);
  return os::strdup(mems);
}

/* cpu_quota
 *
 * Return the number of milliseconds per period
 * process is guaranteed to run.
 *
 * return:
 *    quota time in milliseconds
 *    -1 for no quota
 *    OSCONTAINER_ERROR for not supported
 */
int OSContainer::cpu_quota() {
  GET_CONTAINER_INFO(int, cpu, "/cpu.cfs_quota_us",
                     "CPU Quota is: %d", "%d", quota);
  return quota;
}

int OSContainer::cpu_period() {
  GET_CONTAINER_INFO(int, cpu, "/cpu.cfs_period_us",
                     "CPU Period is: %d", "%d", period);
  return period;
}

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
int OSContainer::cpu_shares() {
  GET_CONTAINER_INFO(int, cpu, "/cpu.shares",
                     "CPU Shares is: %d", "%d", shares);
  // Convert 1024 to no shares setup
  if (shares == 1024) return -1;

  return shares;
}

