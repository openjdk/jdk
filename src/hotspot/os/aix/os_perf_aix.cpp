/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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

#include "jvm.h"
#include "libperfstat_aix.hpp"
#include "memory/allocation.inline.hpp"
#include "os_aix.inline.hpp"
#include "precompiled.hpp"
#include "runtime/os.hpp"
#include "runtime/os_perf.hpp"
#include "runtime/vm_version.hpp"
#include "utilities/globalDefinitions.hpp"

#include <dirent.h>
#include <dlfcn.h>
#include <errno.h>
#include <limits.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdio.h>
#include <string.h>
#include <sys/procfs.h>
#include <sys/resource.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <stdlib.h>
#include <unistd.h>

typedef struct {
  u_longlong_t  user;
  u_longlong_t  sys;
  u_longlong_t  idle;
  u_longlong_t  wait;
} CPUPerfTicks;

typedef struct {
  double utime;
  double stime;
} JVMTime;

enum {
  UNDETECTED,
  UNDETECTABLE,
  LINUX26_NPTL,
  BAREMETAL
};

static bool initialize_libperfstat() {
  static bool is_libperfstat_loaded = false;

  if (!is_libperfstat_loaded) {
    is_libperfstat_loaded = libperfstat::init();
  }

  // If library is still not initialized, assume error.
  if (!is_libperfstat_loaded) {
    printf("Error: failed to load libperfstat (%s)", dlerror());
  }

  return is_libperfstat_loaded;
}

/**
 * Get info for requested PID from /proc/<pid>/psinfo file
 */
static bool read_psinfo(const u_longlong_t& pid, psinfo_t& psinfo) {
  static size_t BUF_LENGTH = 32 + sizeof(u_longlong_t);

  FILE* fp;
  char buf[BUF_LENGTH];
  int len;

  memset(buf, 0, BUF_LENGTH);
  snprintf(buf, BUF_LENGTH, "/proc/%llu/psinfo", pid);
  fp = fopen(buf, "r");

  if (!fp) {
    return false;
  }

  len = fread(&psinfo, sizeof(char), sizeof(psinfo_t), fp);
  return len == sizeof(psinfo_t);
}

/**
 * Get and set ticks for the specified lcpu
 */
static OSReturn get_lcpu_ticks(perfstat_id_t* lcpu_name, CPUPerfTicks* pticks) {
  perfstat_cpu_t lcpu_stats;

  if (!pticks) {
    return OS_ERR;
  }

  // populate cpu_stats
  if (libperfstat::perfstat_cpu(lcpu_name, &lcpu_stats, sizeof(perfstat_cpu_t), 1) < 1) {
    pticks->user = 0;
    pticks->sys  = 0;
    pticks->idle = 0;
    pticks->wait = 0;
    return OS_ERR;
  }

  pticks->user = lcpu_stats.user;
  pticks->sys  = lcpu_stats.sys;
  pticks->idle = lcpu_stats.idle;
  pticks->wait = lcpu_stats.wait;

  return OS_OK;
}

/**
 * Return CPU load caused by the currently executing process (the jvm).
 */
static OSReturn get_jvm_load(double* jvm_uload, double* jvm_sload) {
  static clock_t ticks_per_sec = sysconf(_SC_CLK_TCK);
  static u_longlong_t last_timebase = 0;

  perfstat_process_t jvm_stats;
  perfstat_id_t name_holder;
  u_longlong_t timebase_diff;

  assert(initialize_libperfstat(), "perfstat lib not available");

  snprintf(name_holder.name, IDENTIFIER_LENGTH, "%d", getpid());
  if (libperfstat::perfstat_process(&name_holder, &jvm_stats, sizeof(perfstat_process_t), 1) < 1) {
    return OS_ERR;
  }

  // Update timebase
  timebase_diff = jvm_stats.last_timebase - last_timebase;
  last_timebase = jvm_stats.last_timebase;

  if (jvm_uload) {
    *jvm_uload = jvm_stats.ucpu_time / timebase_diff;
  }
  if (jvm_sload) {
    *jvm_sload = jvm_stats.scpu_time / timebase_diff;
  }

  return OS_OK;
}

static void update_prev_time(JVMTime* from, JVMTime* to) {
  if (from && to) {
    to->utime = from->utime;
    to->stime = from->stime;
  }
}

static void update_prev_ticks(CPUPerfTicks* from, CPUPerfTicks* to) {
  if (from && to) {
    to->user = from->user;
    to->sys  = from->sys;
    to->idle = from->idle;
    to->wait = from->wait;
  }
}

/**
 * Calculate the current system load from current ticks using previous ticks as a starting point.
 */
static void calculate_updated_load(CPUPerfTicks* update, CPUPerfTicks* prev, double* load) {
  CPUPerfTicks diff;

  if (update && prev && load) {
    diff.user = update->user - prev->user;
    diff.sys  = update->sys  - prev->sys;
    diff.idle = update->idle - prev->idle;
    diff.wait = update->wait - prev->wait;

    *load = 1.0 - diff.idle/(diff.sys + diff.user + diff.idle + diff.wait);
  }
}

/**
 * Look up lcpu names for later re-use.
 */
static bool populate_lcpu_names(int ncpus, perfstat_id_t* lcpu_names) {
  perfstat_cpu_t* all_lcpu_stats;
  perfstat_cpu_t* lcpu_stats;
  perfstat_id_t   name_holder;

  assert(lcpu_names, "Names pointer NULL");
  assert(initialize_libperfstat(), "perfstat lib not available");

  strncpy(name_holder.name, FIRST_CPU, IDENTIFIER_LENGTH);

  all_lcpu_stats = NEW_RESOURCE_ARRAY(perfstat_cpu_t, ncpus);

  // populate cpu_stats && check that the expected number of records have been populated
  if (ncpus != libperfstat::perfstat_cpu(&name_holder, all_lcpu_stats, sizeof(perfstat_cpu_t), ncpus)) {
    FREE_RESOURCE_ARRAY(perfstat_cpu_t, all_lcpu_stats, ncpus);
    return false;
  }

  for (int n = 0; n < ncpus; n++) {
    strncpy(lcpu_names[n].name, all_lcpu_stats[n].name, IDENTIFIER_LENGTH);
  }

  FREE_RESOURCE_ARRAY(perfstat_cpu_t, all_lcpu_stats, ncpus);
  return true;
}

/**
 * Calculates the context switch rate.
 * (Context Switches / Tick) * (Tick / s) = Context Switches per second
 */
static OSReturn perf_context_switch_rate(double* rate) {
  static clock_t ticks_per_sec = sysconf(_SC_CLK_TCK);

  u_longlong_t ticks;
  perfstat_cpu_total_t cpu_stats;

  assert(initialize_libperfstat(), "perfstat lib not available");

   if (libperfstat::perfstat_cpu_total(NULL, &cpu_stats, sizeof(perfstat_cpu_total_t), 1) < 0) {
     return OS_ERR;
   }

   ticks = cpu_stats.user + cpu_stats.sys + cpu_stats.idle + cpu_stats.wait;
   *rate = (cpu_stats.pswitch / ticks) * ticks_per_sec;

   return OS_OK;
}

class CPUPerformanceInterface::CPUPerformance : public CHeapObj<mtInternal> {
 private:
  int _ncpus;
  perfstat_id_t* _lcpu_names;
  CPUPerfTicks* _prev_ticks;

 public:
  CPUPerformance();
  bool initialize();
  ~CPUPerformance();

  int cpu_load(int which_logical_cpu, double* cpu_load);
  int context_switch_rate(double* rate);
  int cpu_load_total_process(double* cpu_load);
  int cpu_loads_process(double* pjvmUserLoad, double* pjvmKernelLoad, double* psystemTotalLoad);
};

CPUPerformanceInterface::CPUPerformance::CPUPerformance():
  _ncpus(0),
  _lcpu_names(NULL),
  _prev_ticks(NULL) {}

bool CPUPerformanceInterface::CPUPerformance::initialize() {
  perfstat_cpu_total_t cpu_stats;

  if (!initialize_libperfstat()) {
    return false;
  }
  if (libperfstat::perfstat_cpu_total(NULL, &cpu_stats, sizeof(perfstat_cpu_total_t), 1) < 0) {
    return false;
  }
  if (cpu_stats.ncpus <= 0) {
    return false;
  }

  _ncpus = cpu_stats.ncpus;
  _lcpu_names = NEW_C_HEAP_ARRAY(perfstat_id_t, _ncpus, mtInternal);

  _prev_ticks = NEW_C_HEAP_ARRAY(CPUPerfTicks, _ncpus, mtInternal);
  // Set all prev-tick values to 0
  memset(_prev_ticks, 0, _ncpus*sizeof(CPUPerfTicks));

  if (!populate_lcpu_names(_ncpus, _lcpu_names)) {
    return false;
  }

  return true;
}

CPUPerformanceInterface::CPUPerformance::~CPUPerformance() {
  if (_lcpu_names) {
    FREE_C_HEAP_ARRAY(perfstat_id_t, _lcpu_names);
  }
  if (_prev_ticks) {
    FREE_C_HEAP_ARRAY(CPUPerfTicks, _prev_ticks);
  }
}

/**
 * Get CPU load for all processes on specified logical CPU.
 */
int CPUPerformanceInterface::CPUPerformance::cpu_load(int lcpu_number, double* lcpu_load) {
  CPUPerfTicks ticks;

  assert(lcpu_load != NULL, "NULL pointer passed to cpu_load");
  assert(lcpu_number < _ncpus, "Invalid lcpu passed to cpu_load");

  if (get_lcpu_ticks(&_lcpu_names[lcpu_number], &ticks) == OS_ERR) {
    *lcpu_load = -1.0;
    return OS_ERR;
  }

  calculate_updated_load(&ticks, &_prev_ticks[lcpu_number], lcpu_load);
  update_prev_ticks(&ticks, &_prev_ticks[lcpu_number]);

  return OS_OK;
}

/**
 * Get CPU load for all processes on all CPUs.
 */
int CPUPerformanceInterface::CPUPerformance::cpu_load_total_process(double* total_load) {
  CPUPerfTicks total_ticks;
  CPUPerfTicks prev_total_ticks;

  assert(total_load != NULL, "NULL pointer passed to cpu_load_total_process");

  total_ticks.user = 0;
  total_ticks.sys  = 0;
  total_ticks.idle = 0;
  total_ticks.wait = 0;

  prev_total_ticks.user = 0;
  prev_total_ticks.sys  = 0;
  prev_total_ticks.idle = 0;
  prev_total_ticks.wait = 0;

  for (int lcpu = 0; lcpu < _ncpus; lcpu++) {
    CPUPerfTicks lcpu_ticks;

    if (get_lcpu_ticks(&_lcpu_names[lcpu], &lcpu_ticks) == OS_ERR) {
      *total_load = -1.0;
      return OS_ERR;
    }

    total_ticks.user = lcpu_ticks.user;
    total_ticks.sys  = lcpu_ticks.sys;
    total_ticks.idle = lcpu_ticks.idle;
    total_ticks.wait = lcpu_ticks.wait;

    prev_total_ticks.user += _prev_ticks[lcpu].user;
    prev_total_ticks.sys  += _prev_ticks[lcpu].sys;
    prev_total_ticks.idle += _prev_ticks[lcpu].idle;
    prev_total_ticks.wait += _prev_ticks[lcpu].wait;

    update_prev_ticks(&lcpu_ticks, &_prev_ticks[lcpu]);
  }

  calculate_updated_load(&total_ticks, &prev_total_ticks, total_load);

  return OS_OK;
}

/**
 * Get CPU load for all CPUs.
 *
 * Set values for:
 * - pjvmUserLoad:     CPU load due to jvm process in user mode. Jvm process assumed to be self process
 * - pjvmKernelLoad:   CPU load due to jvm process in kernel mode. Jvm process assumed to be self process
 * - psystemTotalLoad: Total CPU load from all process on all logical CPUs
 *
 * Note: If any of the above loads cannot be calculated, this procedure returns OS_ERR and any load that could not be calculated is set to -1
 *
 */
int CPUPerformanceInterface::CPUPerformance::cpu_loads_process(double* pjvmUserLoad, double* pjvmKernelLoad, double* psystemTotalLoad) {
  double u, k, t;

  int retval = OS_OK;
  if (get_jvm_load(&u, &k) == OS_ERR || cpu_load_total_process(&t) == OS_ERR) {
    retval = OS_ERR;
  }

  if (pjvmUserLoad) {
    *pjvmUserLoad = u;
  }
  if (pjvmKernelLoad) {
    *pjvmKernelLoad = k;
  }
  if (psystemTotalLoad) {
    *psystemTotalLoad = t;
  }

  return retval;
}

int CPUPerformanceInterface::CPUPerformance::context_switch_rate(double* rate) {
  return perf_context_switch_rate(rate);
}

CPUPerformanceInterface::CPUPerformanceInterface() {
  _impl = NULL;
}

bool CPUPerformanceInterface::initialize() {
  _impl = new CPUPerformanceInterface::CPUPerformance();
  return _impl->initialize();
}

CPUPerformanceInterface::~CPUPerformanceInterface() {
  if (_impl != NULL) {
    delete _impl;
  }
}

int CPUPerformanceInterface::cpu_load(int which_logical_cpu, double* cpu_load) const {
  return _impl->cpu_load(which_logical_cpu, cpu_load);
}

int CPUPerformanceInterface::cpu_load_total_process(double* cpu_load) const {
  return _impl->cpu_load_total_process(cpu_load);
}

int CPUPerformanceInterface::cpu_loads_process(double* pjvmUserLoad, double* pjvmKernelLoad, double* psystemTotalLoad) const {
  return _impl->cpu_loads_process(pjvmUserLoad, pjvmKernelLoad, psystemTotalLoad);
}

int CPUPerformanceInterface::context_switch_rate(double* rate) const {
  return _impl->context_switch_rate(rate);
}

class SystemProcessInterface::SystemProcesses : public CHeapObj<mtInternal> {
  private:
  char* allocate_string(const char* str) const;

  public:
  SystemProcesses();
  bool initialize();
  ~SystemProcesses();
  int system_processes(SystemProcess** system_processes, int* no_of_sys_processes) const;
};

SystemProcessInterface::SystemProcesses::SystemProcesses() {
}

bool SystemProcessInterface::SystemProcesses::initialize() {
  return initialize_libperfstat();

}

SystemProcessInterface::SystemProcesses::~SystemProcesses() {
}

char* SystemProcessInterface::SystemProcesses::allocate_string(const char* str) const {
  if (str != NULL) {
    return os::strdup_check_oom(str, mtInternal);
  }
  return NULL;
}

int SystemProcessInterface::SystemProcesses::system_processes(SystemProcess** system_processes, int* nprocs) const {
  perfstat_process_t* proc_stats;
  SystemProcess* head;
  perfstat_id_t name_holder;
  int records_allocated = 0;

  assert(nprocs != NULL, "system_processes counter pointers is NULL!");

  head = NULL;
  *nprocs = 0;
  strncpy(name_holder.name, "", IDENTIFIER_LENGTH);

  // calling perfstat_<subsystem>(NULL, NULL, _, 0) returns number of available records
  *nprocs = libperfstat::perfstat_process(NULL, NULL, sizeof(perfstat_process_t), 0);
  if(*nprocs < 1) {
    // expect at least 1 process
    return OS_ERR;
  }

  records_allocated = *nprocs;
  proc_stats = NEW_RESOURCE_ARRAY(perfstat_process_t, records_allocated);

  // populate stats && set the actual number of procs that have been populated
  // should never be higher than requested, but may be lower due to process death
  *nprocs = libperfstat::perfstat_process(&name_holder, proc_stats, sizeof(perfstat_process_t), records_allocated);

  for (int n = 0; n < *nprocs; n++) {
    psinfo_t psinfo;
    char* name     = NEW_C_HEAP_ARRAY(char, IDENTIFIER_LENGTH, mtInternal);
    char* exe_name = NEW_C_HEAP_ARRAY(char, PRFNSZ, mtInternal);
    char* cmd_line = NEW_C_HEAP_ARRAY(char, PRARGSZ, mtInternal);

    strncpy(name, proc_stats[n].proc_name, IDENTIFIER_LENGTH);

    if (read_psinfo(proc_stats[n].pid, psinfo)) {
      strncpy(exe_name, psinfo.pr_fname, PRFNSZ);
      strncpy(cmd_line, psinfo.pr_psargs, PRARGSZ);
    }

    // create a new SystemProcess with next pointing to current head.
    SystemProcess* sp = new SystemProcess(proc_stats[n].pid,
                                          name,
                                          exe_name,
                                          cmd_line,
                                          head);
    // update head.
    head = sp;
  }

  FREE_RESOURCE_ARRAY(perfstat_process_t, proc_stats, records_allocated);

  *system_processes = head;
  return OS_OK;
}

int SystemProcessInterface::system_processes(SystemProcess** system_procs, int* no_of_sys_processes) const {
  return _impl->system_processes(system_procs, no_of_sys_processes);
}

SystemProcessInterface::SystemProcessInterface() {
  _impl = NULL;
}

bool SystemProcessInterface::initialize() {
  _impl = new SystemProcessInterface::SystemProcesses();
  return _impl->initialize();
}

SystemProcessInterface::~SystemProcessInterface() {
  if (_impl != NULL) {
    delete _impl;
  }
}

CPUInformationInterface::CPUInformationInterface() {
  _cpu_info = NULL;
}

bool CPUInformationInterface::initialize() {
  _cpu_info = new CPUInformation();
  VM_Version::initialize_cpu_information();
  _cpu_info->set_number_of_hardware_threads(VM_Version::number_of_threads());
  _cpu_info->set_number_of_cores(VM_Version::number_of_cores());
  _cpu_info->set_number_of_sockets(VM_Version::number_of_sockets());
  _cpu_info->set_cpu_name(VM_Version::cpu_name());
  _cpu_info->set_cpu_description(VM_Version::cpu_description());
  return true;
}

CPUInformationInterface::~CPUInformationInterface() {
  if (_cpu_info != NULL) {
    if (_cpu_info->cpu_name() != NULL) {
      const char* cpu_name = _cpu_info->cpu_name();
      FREE_C_HEAP_ARRAY(char, cpu_name);
      _cpu_info->set_cpu_name(NULL);
    }
    if (_cpu_info->cpu_description() != NULL) {
       const char* cpu_desc = _cpu_info->cpu_description();
       FREE_C_HEAP_ARRAY(char, cpu_desc);
      _cpu_info->set_cpu_description(NULL);
    }
    delete _cpu_info;
  }
}

int CPUInformationInterface::cpu_information(CPUInformation& cpu_info) {
  if (_cpu_info == NULL) {
    return OS_ERR;
  }

  cpu_info = *_cpu_info; // shallow copy assignment
  return OS_OK;
}

class NetworkPerformanceInterface::NetworkPerformance : public CHeapObj<mtInternal> {
  NONCOPYABLE(NetworkPerformance);

 private:
  char* allocate_string(const char* str) const;

  public:
  NetworkPerformance();
  bool initialize();
  ~NetworkPerformance();
  int network_utilization(NetworkInterface** network_interfaces) const;
};

NetworkPerformanceInterface::NetworkPerformance::NetworkPerformance() {}

bool NetworkPerformanceInterface::NetworkPerformance::initialize() {
  return initialize_libperfstat();
}

NetworkPerformanceInterface::NetworkPerformance::~NetworkPerformance() {}

int NetworkPerformanceInterface::NetworkPerformance::network_utilization(NetworkInterface** network_interfaces) const
{
  int n_records = 0;
  perfstat_netinterface_t* net_stats;
  perfstat_id_t name_holder;
  int records_allocated = 0;

  assert(network_interfaces != NULL, "network_interfaces is NULL");

  *network_interfaces = NULL;
  strncpy(name_holder.name , FIRST_NETINTERFACE, IDENTIFIER_LENGTH);

  // calling perfstat_<subsystem>(NULL, NULL, _, 0) returns number of available records
  if ((n_records = libperfstat::perfstat_netinterface(NULL, NULL, sizeof(perfstat_netinterface_t), 0)) <= 0) {
    return OS_ERR;
  }

  records_allocated = n_records;
  net_stats = NEW_RESOURCE_ARRAY(perfstat_netinterface_t, records_allocated);

  // populate net_stats && update the number of records that have been populated
  n_records = libperfstat::perfstat_netinterface(&name_holder, net_stats, sizeof(perfstat_netinterface_t), n_records);

  // check for error
  if (n_records < 0) {
    return OS_ERR;
  }

  for (int i = 0; i < n_records; i++) {
    // Create new Network interface *with current head as next node*
    NetworkInterface* new_interface = new NetworkInterface(net_stats[i].name,
                                                           net_stats[i].ibytes,
                                                           net_stats[i].obytes,
                                                           *network_interfaces);
    *network_interfaces = new_interface;
  }

  FREE_RESOURCE_ARRAY(perfstat_netinterface_t, net_stats, records_allocated);
  return OS_OK;
}

NetworkPerformanceInterface::NetworkPerformanceInterface() {
  _impl = NULL;
}

NetworkPerformanceInterface::~NetworkPerformanceInterface() {
  if (_impl != NULL) {
    delete _impl;
  }
}

bool NetworkPerformanceInterface::initialize() {
  _impl = new NetworkPerformanceInterface::NetworkPerformance();
  return _impl->initialize();
}

int NetworkPerformanceInterface::network_utilization(NetworkInterface** network_interfaces) const {
  return _impl->network_utilization(network_interfaces);
}
