/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "jvm.h"
#include "memory/allocation.inline.hpp"
#include "os_aix.inline.hpp"
#include "runtime/os.hpp"
#include "runtime/os_perf.hpp"
#include "runtime/vm_version.hpp"
#include "utilities/globalDefinitions.hpp"

#include <stdio.h>
#include <stdarg.h>
#include <unistd.h>
#include <errno.h>
#include <string.h>
#include <sys/resource.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <dirent.h>
#include <stdlib.h>
#include <dlfcn.h>
#include <pthread.h>
#include <limits.h>
#include <libperfstat.h>
#include <sys/procfs.h>

typedef struct {
  u_longlong_t  user;
  u_longlong_t  sys;
  u_longlong_t  idle;
  u_longlong_t  wait;
} CPUPerfTicks;

typedef struct {
  pid64_t pid;
  char    name[PRFNSZ];
  char    command_line[PRARGSZ];
} psinfo_subset_t;

typedef enum {
  CPU_LOAD_VM_ONLY,
  CPU_LOAD_GLOBAL,
} CpuLoadTarget;

enum {
  UNDETECTED,
  UNDETECTABLE,
  LINUX26_NPTL,
  BAREMETAL
};

/**
 * Get and set ticks for the specified lcpu
 */
static OSReturn get_lcpu_ticks(perfstat_id_t* lcpu_name, CPUPerfTicks* pticks) {
  perfstat_cpu_t lcpu_stats;

  assert(pticks != NULL, "NULL pointer passed");
  assert(_lcpu_names != NULL, "CPUPerformance un-initialized");
  assert(lcpu_idx < _ncpus, "Invalid CPU index");

  // populate cpu_stats
  if (1 == perfstat_cpu(lcpu_name, &lcpu_stats, sizeof(perfstat_cpu_t), 1)) {
    return OS_ERR;
  }

  pticks->user = lcpu_stats.user;
  pticks->sys  = lcpu_stats.sys;
  pticks->idle = lcpu_stats.idle;
  pticks->wait = lcpu_stats.wait;

  return OS_OK;
}

/**
 * Set and return a value in [0.0, 1.0] by capping any value above the range to 1.0,
 * and any value below the range to 0.0. Any value already in (0.0, 1.0) remains unchanged.
 *
 * For convenienve, this procedure both sets the pointer to the (possibly) new value, and returns a copy.
 */
static double normalize(double* val) {
  *val = MIN2<double>(*val, 1.0);
  *val = MAX2<double>(*val, 0.0);
  return *val;
}

/**
 * Return CPU load caused by the currently executing process (the jvm).
 */
static OSReturn get_jvm_load(double* jvm_user_load, double* jvm_kernel_load, double* jvm_total_load) {
  perfstat_process_t jvm_stats;
  perfstat_rawdata_t perfstat_lookup_data;

  perfstat_lookup_data.type = UTIL_PROCESS;
  snprintf(perfstat_lookup_data.name.name, IDENTIFIER_LENGTH, "%d", getpid());
  perfstat_lookup_data.curstat = NULL;
  perfstat_lookup_data.prevstat = NULL;
  perfstat_lookup_data.sizeof_data = sizeof(perfstat_process_t);
  perfstat_lookup_data.cur_elems = 0;
  perfstat_lookup_data.prev_elems = 0;

  if (0 < perfstat_process_util(&perfstat_lookup_data, &jvm_stats, sizeof(perfstat_process_t), 1)) {
    return OS_ERR;
  }

  // when called via perfstat_process_util, ucpu_time and scpu_time fields are
  // populated with percentages rather than time.
  if (jvm_user_load) {
    *jvm_user_load = jvm_stats.ucpu_time;
  }
  if (jvm_kernel_load) {
    *jvm_kernel_load = jvm_stats.scpu_time;
  }
  if (jvm_total_load) {
    *jvm_total_load = jvm_stats.ucpu_time + jvm_stats.scpu_time;
  }

  return OS_OK;
}

static void update_last_ticks(CPUPerfTicks* from, CPUPerfTicks* to) {
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
static void populate_lcpu_names(int ncpus, perfstat_id_t* lcpu_names) {
  int n_records;
  perfstat_cpu_t* all_lcpu_stats;
  perfstat_cpu_t* lcpu_stats;
  perfstat_id_t   name_holder;

  strncpy(name_holder.name, FIRST_CPU, IDENTIFIER_LENGTH);

  // calling perfstat_<subsystem>(NULL, NULL, _, 0) returns number of available records
  assert(0 > (n_records = perfstat_cpu(NULL, NULL, sizeof(perfstat_cpu_t), 0)));

  all_lcpu_stats = (perfstat_cpu_t*) NEW_RESOURCE_ARRAY(perfstat_cpu_t, n_records);

  // populate cpu_stats && check that the expected number of records have been populated
  assert(ncpus == perfstat_cpu(&name_holder, all_lcpu_stats, sizeof(perfstat_cpu_t), n_records));

  for (int record=0; record < n_records; record++) {
    strncpy(lcpu_names[record].name, all_lcpu_stats[record].name, IDENTIFIER_LENGTH);
  }

  FREE_RESOURCE_ARRAY(perfstat_cpu_t, all_lcpu_stats, n_records);
}

/**
 * Calculates the context switch rate.
 * (Context Switches / Tick) * (Tick / s) = Context Switches per second
 */
static OSReturn perf_context_switch_rate(double* rate) {
  static clock_t ticks_per_sec = sysconf(_SC_CLK_TCK);

  u_longlong_t ticks;
  perfstat_cpu_total_t* cpu_stats;

  assert(rate != NULL, "NULL pointer passed");

  cpu_stats = (perfstat_cpu_total_t*) NEW_RESOURCE_ARRAY(perfstat_cpu_total_t, 1);

   if (0 < perfstat_cpu_total(NULL, cpu_stats, sizeof(perfstat_cpu_total_t), 1)) {
     return OS_ERR;
   }

   ticks = cpu_stats->user + cpu_stats->sys + cpu_stats->idle + cpu_stats->wait;
   *rate = (cpu_stats->pswitch / ticks) * ticks_per_sec;

   FREE_RESOURCE_ARRAY(perfstat_cpu_total_t, cpu_stats, 1);

   return OS_OK;
}

class CPUPerformanceInterface::CPUPerformance : public CHeapObj<mtInternal> {
 private:
  int _ncpus;
  perfstat_id_t* _lcpu_names;
  CPUPerfTicks _last_total_ticks;

 public:
  CPUPerformance();
  bool initialize();
  ~CPUPerformance();

  int cpu_load(int which_logical_cpu, double* cpu_load);
  int context_switch_rate(double* rate);
  int cpu_load_total_process(double* cpu_load);
  int cpu_loads_process(double* pjvmUserLoad, double* pjvmKernelLoad, double* psystemTotalLoad);
};

CPUPerformanceInterface::CPUPerformance::CPUPerformance() {
  /* Set default values only */
  _ncpus = 0;
  _lcpu_names = NULL;

  _last_total_ticks.user = 0;
  _last_total_ticks.sys  = 0;
  _last_total_ticks.idle = 0;
  _last_total_ticks.wait = 0;
}

bool CPUPerformanceInterface::CPUPerformance::initialize() {
  perfstat_cpu_total_t* cpu_stats;

  cpu_stats = (perfstat_cpu_total_t*) NEW_RESOURCE_ARRAY(perfstat_cpu_total_t, 1);

   if (perfstat_cpu_total(NULL, cpu_stats, sizeof(perfstat_cpu_total_t), 1) < 0) {
     FREE_RESOURCE_ARRAY(perfstat_cpu_total_t, cpu_stats, 1);
     return false;
  }

  _ncpus = cpu_stats->ncpus;
  _lcpu_names = NEW_C_HEAP_ARRAY(perfstat_id_t, cpu_stats->ncpus, mtInternal);
  populate_lcpu_names(_ncpus, _lcpu_names);

  FREE_RESOURCE_ARRAY(perfstat_cpu_total_t, cpu_stats, 1);
  return true;
}

CPUPerformanceInterface::CPUPerformance::~CPUPerformance() {
  if (_lcpu_names) {
    FREE_C_HEAP_ARRAY(perfstat_id_t, _lcpu_names);
  }
}

/**
 * Get CPU load for all processes on specified logical CPU.
 */
int CPUPerformanceInterface::CPUPerformance::cpu_load(int which_logical_cpu, double* lcpu_load) {
  CPUPerfTicks lcpu_stats;

  assert(lcpu_load != NULL, "NULL pointer passed to cpu_load");

  if (get_lcpu_ticks(&_lcpu_names[which_logical_cpu], &lcpu_stats) == OS_ERR) {
    *lcpu_load = 0.0;
    return OS_ERR;
  }

  calculate_updated_load(&lcpu_stats, &_last_total_ticks, lcpu_load);
  update_last_ticks(&lcpu_stats, &_last_total_ticks);

  return OS_OK;
}

/**
 * Get CPU load for all processes on all CPUs.
 */
int CPUPerformanceInterface::CPUPerformance::cpu_load_total_process(double* total_load) {
  double load_avg = 0.0;

  assert(total_load != NULL, "NULL pointer passed to cpu_load_total_process");

  for (int cpu=0; cpu < _ncpus; cpu++) {
    double l;
    if (cpu_load(cpu, &l) != OS_ERR) {
      load_avg += l;
    }
  }
  load_avg = load_avg/_ncpus;

  *total_load = load_avg;

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
 */
int CPUPerformanceInterface::CPUPerformance::cpu_loads_process(double* pjvmUserLoad, double* pjvmKernelLoad, double* psystemTotalLoad) {
  double u, k, t;

  assert(pjvmUserLoad     != NULL, "pjvmUserLoad not inited");
  assert(pjvmKernelLoad   != NULL, "pjvmKernelLoad not inited");
  assert(psystemTotalLoad != NULL, "psystemTotalLoad not inited");

  if (get_jvm_load(&u, &k, NULL) == OS_ERR ||
      cpu_load_total_process(&t) == OS_ERR)
  {
    *pjvmUserLoad     = 0.0;
    *pjvmKernelLoad   = 0.0;
    *psystemTotalLoad = 0.0;
    return OS_ERR;
  }

  *pjvmUserLoad     = normalize(&u);
  *pjvmKernelLoad   = normalize(&k);
  *psystemTotalLoad = normalize(&t);

  return OS_OK;
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
  return true;
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
  perfstat_process_t* all_proc_stats;
  perfstat_process_t* proc_stats;
  perfstat_id_t       name_holder;

  assert(system_processes != NULL, "system_processes pointer is NULL!");
  assert(nprocs != NULL, "system_processes counter pointers is NULL!");

  // initialize pointers
  *nprocs = 0;
  *system_processes = NULL;

  strcpy(name_holder.name, "");

  // calling perfstat_<subsystem>(NULL, NULL, _, 0) returns number of available records
  if((*nprocs = perfstat_process(NULL, NULL, sizeof(perfstat_process_t), 0)) < 1) {
    // expect at least 1 process
    return OS_ERR;
  }

  all_proc_stats = (perfstat_process_t*) NEW_RESOURCE_ARRAY(perfstat_process_t, *nprocs);

  // populate stats && (re)set the number of procs that have been populated
  *nprocs = perfstat_process(&name_holder, all_proc_stats, sizeof(perfstat_process_t), *nprocs);

  for (int record=0; record < *nprocs; record++) {
    proc_stats = &(all_proc_stats[record]);

    // create new SystemProcess. With next pointing to current head.
    SystemProcess* sp = new SystemProcess(proc_stats->pid,
                                          allocate_string(proc_stats->proc_name),
                                          NULL,
                                          NULL,
                                          *system_processes);
    // update head.
    *system_processes = sp;
  }

  FREE_RESOURCE_ARRAY(perfstat_process_t, all_proc_stats, 1);
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

bool NetworkPerformanceInterface::NetworkPerformance::initialize() { return true; }

NetworkPerformanceInterface::NetworkPerformance::~NetworkPerformance() {}

char* NetworkPerformanceInterface::NetworkPerformance::allocate_string(const char* str) const {
  if (str != NULL) {
    return os::strdup_check_oom(str, mtInternal);
  }
  return NULL;
}

int NetworkPerformanceInterface::NetworkPerformance::network_utilization(NetworkInterface** network_interfaces) const
{
  assert(network_interfaces != NULL, "network_interfaces is NULL");

  int n_records = 0;
  NetworkInterface* head = NULL;
  perfstat_netinterface_t* net_stats;
  perfstat_netinterface_t* all_net_stats;
  perfstat_id_t name_holder;

  *network_interfaces = NULL;
  strncpy(name_holder.name , FIRST_NETINTERFACE, IDENTIFIER_LENGTH);

  // calling perfstat_<subsyste>(NULL, NULL, ..., 0) returns number of available records
  if (0 > (n_records = perfstat_netinterface(NULL, NULL, sizeof(perfstat_netinterface_t), 0))) {
    return OS_ERR;
  }

  all_net_stats = (perfstat_netinterface_t*) NEW_RESOURCE_ARRAY(perfstat_netinterface_t, n_records);

  // populate net_stats && check that the expected number of records have been populated
  if (n_records > (perfstat_netinterface(&name_holder, all_net_stats, sizeof(perfstat_netinterface_t), n_records))) {
    return OS_ERR;
  }

  for (int i = n_records - 1; i >= 0; i--) {
    net_stats = &all_net_stats[i];

    // Create new Network interface *with current head as next node*
    NetworkInterface* net_interface = new NetworkInterface(allocate_string(net_stats->name),
                                                           net_stats->ibytes,
                                                           net_stats->obytes,
                                                           head);
    head = net_interface;
  }

  FREE_RESOURCE_ARRAY(perfstat_netinterface_t, all_net_stats, 1);

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
