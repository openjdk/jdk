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

#include <string.h>
#include <math.h>
#include <errno.h>
#include "cgroupSubsystem_linux.hpp"
#include "cgroupV1Subsystem_linux.hpp"
#include "cgroupV2Subsystem_linux.hpp"
#include "logging/log.hpp"
#include "memory/allocation.hpp"
#include "runtime/globals.hpp"
#include "runtime/os.hpp"
#include "utilities/globalDefinitions.hpp"

CgroupSubsystem* CgroupSubsystemFactory::create() {
  CgroupV1MemoryController* memory = NULL;
  CgroupV1Controller* cpuset = NULL;
  CgroupV1Controller* cpu = NULL;
  CgroupV1Controller* cpuacct = NULL;
  FILE *mntinfo = NULL;
  FILE *cgroups = NULL;
  FILE *cgroup = NULL;
  char buf[MAXPATHLEN+1];
  char tmproot[MAXPATHLEN+1];
  char tmpmount[MAXPATHLEN+1];
  char *p;
  bool is_cgroupsV2;
  // true iff all controllers, memory, cpu, cpuset, cpuacct are enabled
  // at the kernel level.
  bool all_controllers_enabled;

  CgroupInfo cg_infos[CG_INFO_LENGTH];
  int cpuset_idx  = 0;
  int cpu_idx     = 1;
  int cpuacct_idx = 2;
  int memory_idx  = 3;

  /*
   * Read /proc/cgroups so as to be able to distinguish cgroups v2 vs cgroups v1.
   *
   * For cgroups v1 unified hierarchy, cpu, cpuacct, cpuset, memory controllers
   * must have non-zero for the hierarchy ID field.
   */
  cgroups = fopen("/proc/cgroups", "r");
  if (cgroups == NULL) {
      log_debug(os, container)("Can't open /proc/cgroups, %s",
                               os::strerror(errno));
      return NULL;
  }

  while ((p = fgets(buf, MAXPATHLEN, cgroups)) != NULL) {
    char name[MAXPATHLEN+1];
    int  hierarchy_id;
    int  enabled;

    // Format of /proc/cgroups documented via man 7 cgroups
    if (sscanf(p, "%s %d %*d %d", name, &hierarchy_id, &enabled) != 3) {
      continue;
    }
    if (strcmp(name, "memory") == 0) {
      cg_infos[memory_idx]._name = os::strdup(name);
      cg_infos[memory_idx]._hierarchy_id = hierarchy_id;
      cg_infos[memory_idx]._enabled = (enabled == 1);
    } else if (strcmp(name, "cpuset") == 0) {
      cg_infos[cpuset_idx]._name = os::strdup(name);
      cg_infos[cpuset_idx]._hierarchy_id = hierarchy_id;
      cg_infos[cpuset_idx]._enabled = (enabled == 1);
    } else if (strcmp(name, "cpu") == 0) {
      cg_infos[cpu_idx]._name = os::strdup(name);
      cg_infos[cpu_idx]._hierarchy_id = hierarchy_id;
      cg_infos[cpu_idx]._enabled = (enabled == 1);
    } else if (strcmp(name, "cpuacct") == 0) {
      cg_infos[cpuacct_idx]._name = os::strdup(name);
      cg_infos[cpuacct_idx]._hierarchy_id = hierarchy_id;
      cg_infos[cpuacct_idx]._enabled = (enabled == 1);
    }
  }
  fclose(cgroups);

  is_cgroupsV2 = true;
  all_controllers_enabled = true;
  for (int i = 0; i < CG_INFO_LENGTH; i++) {
    is_cgroupsV2 = is_cgroupsV2 && cg_infos[i]._hierarchy_id == 0;
    all_controllers_enabled = all_controllers_enabled && cg_infos[i]._enabled;
  }

  if (!all_controllers_enabled) {
    // one or more controllers disabled, disable container support
    log_debug(os, container)("One or more required controllers disabled at kernel level.");
    return NULL;
  }

  /*
   * Read /proc/self/cgroup and determine:
   *  - the cgroup path for cgroups v2 or
   *  - on a cgroups v1 system, collect info for mapping
   *    the host mount point to the local one via /proc/self/mountinfo below.
   */
  cgroup = fopen("/proc/self/cgroup", "r");
  if (cgroup == NULL) {
    log_debug(os, container)("Can't open /proc/self/cgroup, %s",
                             os::strerror(errno));
    return NULL;
  }

  while ((p = fgets(buf, MAXPATHLEN, cgroup)) != NULL) {
    char *controllers;
    char *token;
    char *hierarchy_id_str;
    int  hierarchy_id;
    char *cgroup_path;

    hierarchy_id_str = strsep(&p, ":");
    hierarchy_id = atoi(hierarchy_id_str);
    /* Get controllers and base */
    controllers = strsep(&p, ":");
    cgroup_path = strsep(&p, "\n");

    if (controllers == NULL) {
      continue;
    }

    while (!is_cgroupsV2 && (token = strsep(&controllers, ",")) != NULL) {
      if (strcmp(token, "memory") == 0) {
        assert(hierarchy_id == cg_infos[memory_idx]._hierarchy_id, "/proc/cgroups and /proc/self/cgroup hierarchy mismatch");
        cg_infos[memory_idx]._cgroup_path = os::strdup(cgroup_path);
      } else if (strcmp(token, "cpuset") == 0) {
        assert(hierarchy_id == cg_infos[cpuset_idx]._hierarchy_id, "/proc/cgroups and /proc/self/cgroup hierarchy mismatch");
        cg_infos[cpuset_idx]._cgroup_path = os::strdup(cgroup_path);
      } else if (strcmp(token, "cpu") == 0) {
        assert(hierarchy_id == cg_infos[cpu_idx]._hierarchy_id, "/proc/cgroups and /proc/self/cgroup hierarchy mismatch");
        cg_infos[cpu_idx]._cgroup_path = os::strdup(cgroup_path);
      } else if (strcmp(token, "cpuacct") == 0) {
        assert(hierarchy_id == cg_infos[cpuacct_idx]._hierarchy_id, "/proc/cgroups and /proc/self/cgroup hierarchy mismatch");
        cg_infos[cpuacct_idx]._cgroup_path = os::strdup(cgroup_path);
      }
    }
    if (is_cgroupsV2) {
      for (int i = 0; i < CG_INFO_LENGTH; i++) {
        cg_infos[i]._cgroup_path = os::strdup(cgroup_path);
      }
    }
  }
  fclose(cgroup);

  if (is_cgroupsV2) {
    // Find the cgroup2 mount point by reading /proc/self/mountinfo
    mntinfo = fopen("/proc/self/mountinfo", "r");
    if (mntinfo == NULL) {
        log_debug(os, container)("Can't open /proc/self/mountinfo, %s",
                                 os::strerror(errno));
        return NULL;
    }

    char cgroupv2_mount[MAXPATHLEN+1];
    char fstype[MAXPATHLEN+1];
    bool mount_point_found = false;
    while ((p = fgets(buf, MAXPATHLEN, mntinfo)) != NULL) {
      char *tmp_mount_point = cgroupv2_mount;
      char *tmp_fs_type = fstype;

      // mountinfo format is documented at https://www.kernel.org/doc/Documentation/filesystems/proc.txt
      if (sscanf(p, "%*d %*d %*d:%*d %*s %s %*[^-]- %s cgroup2 %*s", tmp_mount_point, tmp_fs_type) == 2) {
        // we likely have an early match return, be sure we have cgroup2 as fstype
        if (strcmp("cgroup2", tmp_fs_type) == 0) {
          mount_point_found = true;
          break;
        }
      }
    }
    fclose(mntinfo);
    if (!mount_point_found) {
      log_trace(os, container)("Mount point for cgroupv2 not found in /proc/self/mountinfo");
      return NULL;
    }
    // Cgroups v2 case, we have all the info we need.
    // Construct the subsystem, free resources and return
    // Note: any index in cg_infos will do as the path is the same for
    //       all controllers.
    CgroupController* unified = new CgroupV2Controller(cgroupv2_mount, cg_infos[memory_idx]._cgroup_path);
    for (int i = 0; i < CG_INFO_LENGTH; i++) {
      os::free(cg_infos[i]._name);
      os::free(cg_infos[i]._cgroup_path);
    }
    log_debug(os, container)("Detected cgroups v2 unified hierarchy");
    return new CgroupV2Subsystem(unified);
  }

  // What follows is cgroups v1
  log_debug(os, container)("Detected cgroups hybrid or legacy hierarchy, using cgroups v1 controllers");

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
      return NULL;
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
        memory = new CgroupV1MemoryController(tmproot, tmpmount);
      } else if (strcmp(token, "cpuset") == 0) {
        cpuset = new CgroupV1Controller(tmproot, tmpmount);
      } else if (strcmp(token, "cpu") == 0) {
        cpu = new CgroupV1Controller(tmproot, tmpmount);
      } else if (strcmp(token, "cpuacct") == 0) {
        cpuacct= new CgroupV1Controller(tmproot, tmpmount);
      }
    }
  }

  fclose(mntinfo);

  if (memory == NULL) {
    log_debug(os, container)("Required cgroup v1 memory subsystem not found");
    return NULL;
  }
  if (cpuset == NULL) {
    log_debug(os, container)("Required cgroup v1 cpuset subsystem not found");
    return NULL;
  }
  if (cpu == NULL) {
    log_debug(os, container)("Required cgroup v1 cpu subsystem not found");
    return NULL;
  }
  if (cpuacct == NULL) {
    log_debug(os, container)("Required cgroup v1 cpuacct subsystem not found");
    return NULL;
  }

  /*
   * Use info gathered previously from /proc/self/cgroup
   * and map host mount point to
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
  for (int i = 0; i < CG_INFO_LENGTH; i++) {
    CgroupInfo info = cg_infos[i];
    if (strcmp(info._name, "memory") == 0) {
      memory->set_subsystem_path(info._cgroup_path);
    } else if (strcmp(info._name, "cpuset") == 0) {
      cpuset->set_subsystem_path(info._cgroup_path);
    } else if (strcmp(info._name, "cpu") == 0) {
      cpu->set_subsystem_path(info._cgroup_path);
    } else if (strcmp(info._name, "cpuacct") == 0) {
      cpuacct->set_subsystem_path(info._cgroup_path);
    }
  }
  return new CgroupV1Subsystem(cpuset, cpu, cpuacct, memory);
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
int CgroupSubsystem::active_processor_count() {
  int quota_count = 0, share_count = 0;
  int cpu_count, limit_count;
  int result;

  // We use a cache with a timeout to avoid performing expensive
  // computations in the event this function is called frequently.
  // [See 8227006].
  CachingCgroupController* contrl = cpu_controller();
  CachedMetric* cpu_limit = contrl->metrics_cache();
  if (!cpu_limit->should_check_metric()) {
    int val = (int)cpu_limit->value();
    log_trace(os, container)("CgroupSubsystem::active_processor_count (cached): %d", val);
    return val;
  }

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

  // Update cached metric to avoid re-reading container settings too often
  cpu_limit->set_value(result, OSCONTAINER_CACHE_TIMEOUT);

  return result;
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
jlong CgroupSubsystem::memory_limit_in_bytes() {
  CachingCgroupController* contrl = memory_controller();
  CachedMetric* memory_limit = contrl->metrics_cache();
  if (!memory_limit->should_check_metric()) {
    return memory_limit->value();
  }
  jlong mem_limit = read_memory_limit_in_bytes();
  // Update cached metric to avoid re-reading container settings too often
  memory_limit->set_value(mem_limit, OSCONTAINER_CACHE_TIMEOUT);
  return mem_limit;
}
