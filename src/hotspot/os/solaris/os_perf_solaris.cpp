/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/os.hpp"
#include "runtime/os_perf.hpp"
#include "os_solaris.inline.hpp"
#include "utilities/macros.hpp"

#include CPU_HEADER(vm_version_ext)

#include <sys/types.h>
#include <procfs.h>
#include <dirent.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <strings.h>
#include <unistd.h>
#include <fcntl.h>
#include <kstat.h>
#include <unistd.h>
#include <string.h>
#include <sys/sysinfo.h>
#include <sys/lwp.h>
#include <pthread.h>
#include <time.h>
#include <utmpx.h>
#include <dlfcn.h>
#include <sys/loadavg.h>
#include <limits.h>

static const double NANOS_PER_SEC = 1000000000.0;

struct CPUPerfTicks {
  kstat_t* kstat;
  uint64_t last_idle;
  uint64_t last_total;
  double   last_ratio;
};

struct CPUPerfCounters {
  int           nProcs;
  CPUPerfTicks* jvmTicks;
  kstat_ctl_t*  kstat_ctrl;
};

static int get_info(const char* path, void* info, size_t s, off_t o) {
  assert(path != NULL, "path is NULL!");
  assert(info != NULL, "info is NULL!");

  int fd = -1;

  if ((fd = open(path, O_RDONLY)) < 0) {
    return OS_ERR;
  }
  if (pread(fd, info, s, o) != s) {
    close(fd);
    return OS_ERR;
  }
  close(fd);
  return OS_OK;
}

static int get_psinfo2(void* info, size_t s, off_t o) {
  return get_info("/proc/self/psinfo", info, s, o);
}

static int get_psinfo(psinfo_t* info) {
  return get_psinfo2(info, sizeof(*info), 0);
}

static int get_psinfo(char* file, psinfo_t* info) {
  assert(file != NULL, "file is NULL!");
  assert(info != NULL, "info is NULL!");
  return get_info(file, info, sizeof(*info), 0);
}


static int get_usage(prusage_t* usage) {
  assert(usage != NULL, "usage is NULL!");
  return get_info("/proc/self/usage", usage, sizeof(*usage), 0);
}

static int read_cpustat(kstat_ctl_t* kstat_ctrl, CPUPerfTicks* load, cpu_stat_t* cpu_stat) {
  assert(kstat_ctrl != NULL, "kstat_ctrl pointer is NULL!");
  assert(load != NULL, "load pointer is NULL!");
  assert(cpu_stat != NULL, "cpu_stat pointer is NULL!");

  if (load->kstat == NULL) {
    // no handle.
    return OS_ERR;
  }
  if (kstat_read(kstat_ctrl, load->kstat, cpu_stat) == OS_ERR) {
    // disable handle for this CPU
     load->kstat = NULL;
     return OS_ERR;
  }
  return OS_OK;
}

static double get_cpu_load(int which_logical_cpu, CPUPerfCounters* counters) {
  assert(counters != NULL, "counters pointer is NULL!");

  cpu_stat_t  cpu_stat = {0};

  if (which_logical_cpu >= counters->nProcs) {
    return .0;
  }

  CPUPerfTicks load = counters->jvmTicks[which_logical_cpu];
  if (read_cpustat(counters->kstat_ctrl, &load, &cpu_stat) != OS_OK) {
    return .0;
  }

  uint_t* usage = cpu_stat.cpu_sysinfo.cpu;
  if (usage == NULL) {
    return .0;
  }

  uint64_t c_idle  = usage[CPU_IDLE];
  uint64_t c_total = 0;

  for (int i = 0; i < CPU_STATES; i++) {
    c_total += usage[i];
  }

  // Calculate diff against previous snapshot
  uint64_t d_idle  = c_idle - load.last_idle;
  uint64_t d_total = c_total - load.last_total;

  /** update if weve moved */
  if (d_total > 0) {
    // Save current values for next time around
    load.last_idle  = c_idle;
    load.last_total = c_total;
    load.last_ratio = (double) (d_total - d_idle) / d_total;
  }

  return load.last_ratio;
}

static int get_boot_time(uint64_t* time) {
  assert(time != NULL, "time pointer is NULL!");
  setutxent();
  for(;;) {
    struct utmpx* u;
    if ((u = getutxent()) == NULL) {
      break;
    }
    if (u->ut_type == BOOT_TIME) {
      *time = u->ut_xtime;
      endutxent();
      return OS_OK;
    }
  }
  endutxent();
  return OS_ERR;
}

static int get_noof_context_switches(CPUPerfCounters* counters, uint64_t* switches) {
  assert(switches != NULL, "switches pointer is NULL!");
  assert(counters != NULL, "counter pointer is NULL!");
  *switches = 0;
  uint64_t s = 0;

  // Collect data from all CPUs
  for (int i = 0; i < counters->nProcs; i++) {
    cpu_stat_t cpu_stat = {0};
    CPUPerfTicks load = counters->jvmTicks[i];

    if (read_cpustat(counters->kstat_ctrl, &load, &cpu_stat) == OS_OK) {
      s += cpu_stat.cpu_sysinfo.pswitch;
    } else {
      //fail fast...
      return OS_ERR;
    }
  }
  *switches = s;
  return OS_OK;
}

static int perf_context_switch_rate(CPUPerfCounters* counters, double* rate) {
  assert(counters != NULL, "counters is NULL!");
  assert(rate != NULL, "rate pointer is NULL!");
  static pthread_mutex_t contextSwitchLock = PTHREAD_MUTEX_INITIALIZER;
  static uint64_t lastTime = 0;
  static uint64_t lastSwitches = 0;
  static double   lastRate = 0.0;

  uint64_t lt = 0;
  int res = 0;

  if (lastTime == 0) {
    uint64_t tmp;
    if (get_boot_time(&tmp) < 0) {
      return OS_ERR;
    }
    lt = tmp * 1000;
  }

  res = OS_OK;

  pthread_mutex_lock(&contextSwitchLock);
  {

    uint64_t sw = 0;
    clock_t t, d;

    if (lastTime == 0) {
      lastTime = lt;
    }

    t = clock();
    d = t - lastTime;

    if (d == 0) {
      *rate = lastRate;
    } else if (get_noof_context_switches(counters, &sw)== OS_OK) {
      *rate      = ((double)(sw - lastSwitches) / d) * 1000;
      lastRate     = *rate;
      lastSwitches = sw;
      lastTime     = t;
    } else {
      *rate = 0.0;
      res   = OS_ERR;
    }
    if (*rate < 0.0) {
      *rate = 0.0;
      lastRate = 0.0;
    }
  }
  pthread_mutex_unlock(&contextSwitchLock);
  return res;
 }



class CPUPerformanceInterface::CPUPerformance : public CHeapObj<mtInternal> {
   friend class CPUPerformanceInterface;
 private:
  CPUPerfCounters _counters;
  int cpu_load(int which_logical_cpu, double* cpu_load);
  int context_switch_rate(double* rate);
  int cpu_load_total_process(double* cpu_load);
  int cpu_loads_process(double* pjvmUserLoad, double* pjvmKernelLoad, double* psystemTotalLoad);

  CPUPerformance();
  ~CPUPerformance();
  bool initialize();
};

CPUPerformanceInterface::CPUPerformance::CPUPerformance() {
  _counters.nProcs = 0;
  _counters.jvmTicks = NULL;
  _counters.kstat_ctrl = NULL;
}

bool CPUPerformanceInterface::CPUPerformance::initialize() {
  // initialize kstat control structure,
  _counters.kstat_ctrl = kstat_open();
  assert(_counters.kstat_ctrl != NULL, "error initializing kstat control structure!");

  if (NULL == _counters.kstat_ctrl) {
    return false;
  }

  // Get number of CPU(s)
  if ((_counters.nProcs = sysconf(_SC_NPROCESSORS_ONLN)) == OS_ERR) {
    // ignore error?
    _counters.nProcs = 1;
  }

  assert(_counters.nProcs > 0, "no CPUs detected in sysconf call!");
  if (_counters.nProcs == 0) {
    return false;
  }

  // Data structure(s) for saving CPU load (one per CPU)
  size_t tick_array_size = _counters.nProcs * sizeof(CPUPerfTicks);
  _counters.jvmTicks = (CPUPerfTicks*)NEW_C_HEAP_ARRAY(char, tick_array_size, mtInternal);
  if (NULL == _counters.jvmTicks) {
    return false;
  }
  memset(_counters.jvmTicks, 0, tick_array_size);

  // Get kstat cpu_stat counters for every CPU
  // loop over kstat to find our cpu_stat(s)
  int i = 0;
  for (kstat_t* kstat = _counters.kstat_ctrl->kc_chain; kstat != NULL; kstat = kstat->ks_next) {
    if (strncmp(kstat->ks_module, "cpu_stat", 8) == 0) {
      if (kstat_read(_counters.kstat_ctrl, kstat, NULL) == OS_ERR) {
        continue;
      }
      if (i == _counters.nProcs) {
        // more cpu_stats than reported CPUs
        break;
      }
      _counters.jvmTicks[i++].kstat = kstat;
    }
  }
  return true;
}

CPUPerformanceInterface::CPUPerformance::~CPUPerformance() {
  if (_counters.jvmTicks != NULL) {
    FREE_C_HEAP_ARRAY(char, _counters.jvmTicks);
  }
  if (_counters.kstat_ctrl != NULL) {
    kstat_close(_counters.kstat_ctrl);
  }
}

int CPUPerformanceInterface::CPUPerformance::cpu_load(int which_logical_cpu, double* cpu_load) {
  assert(cpu_load != NULL, "cpu_load pointer is NULL!");
  double t = .0;
  if (-1 == which_logical_cpu) {
    for (int i = 0; i < _counters.nProcs; i++) {
      t += get_cpu_load(i, &_counters);
    }
    // Cap total systemload to 1.0
    t = MIN2<double>((t / _counters.nProcs), 1.0);
  } else {
    t = MIN2<double>(get_cpu_load(which_logical_cpu, &_counters), 1.0);
  }

  *cpu_load = t;
  return OS_OK;
}

int CPUPerformanceInterface::CPUPerformance::cpu_load_total_process(double* cpu_load) {
  assert(cpu_load != NULL, "cpu_load pointer is NULL!");

  psinfo_t info;

  // Get the percentage of "recent cpu usage" from all the lwp:s in the JVM:s
  // process. This is returned as a value between 0.0 and 1.0 multiplied by 0x8000.
  if (get_psinfo2(&info.pr_pctcpu, sizeof(info.pr_pctcpu), offsetof(psinfo_t, pr_pctcpu)) != 0) {
    *cpu_load = 0.0;
    return OS_ERR;
  }
  *cpu_load = (double) info.pr_pctcpu / 0x8000;
  return OS_OK;
}

int CPUPerformanceInterface::CPUPerformance::cpu_loads_process(double* pjvmUserLoad, double* pjvmKernelLoad, double* psystemTotalLoad) {
  assert(pjvmUserLoad != NULL, "pjvmUserLoad not inited");
  assert(pjvmKernelLoad != NULL, "pjvmKernelLoad not inited");
  assert(psystemTotalLoad != NULL, "psystemTotalLoad not inited");

  static uint64_t lastTime;
  static uint64_t lastUser, lastKernel;
  static double lastUserRes, lastKernelRes;

  pstatus_t pss;
  psinfo_t  info;

  *pjvmKernelLoad = *pjvmUserLoad = *psystemTotalLoad = 0;
  if (get_info("/proc/self/status", &pss.pr_utime, sizeof(timestruc_t)*2, offsetof(pstatus_t, pr_utime)) != 0) {
    return OS_ERR;
  }

  if (get_psinfo(&info) != 0) {
    return OS_ERR;
  }

  // get the total time in user, kernel and total time
  // check ratios for 'lately' and multiply the 'recent load'.
  uint64_t time   = (info.pr_time.tv_sec * NANOS_PER_SEC) + info.pr_time.tv_nsec;
  uint64_t user   = (pss.pr_utime.tv_sec * NANOS_PER_SEC) + pss.pr_utime.tv_nsec;
  uint64_t kernel = (pss.pr_stime.tv_sec * NANOS_PER_SEC) + pss.pr_stime.tv_nsec;
  uint64_t diff   = time - lastTime;
  double load     = (double) info.pr_pctcpu / 0x8000;

  if (diff > 0) {
    lastUserRes = (load * (user - lastUser)) / diff;
    lastKernelRes = (load * (kernel - lastKernel)) / diff;

    // BUG9182835 - patch for clamping these values to sane ones.
    lastUserRes   = MIN2<double>(1, lastUserRes);
    lastUserRes   = MAX2<double>(0, lastUserRes);
    lastKernelRes = MIN2<double>(1, lastKernelRes);
    lastKernelRes = MAX2<double>(0, lastKernelRes);
  }

  double t = .0;
  cpu_load(-1, &t);
  // clamp at user+system and 1.0
  if (lastUserRes + lastKernelRes > t) {
    t = MIN2<double>(lastUserRes + lastKernelRes, 1.0);
  }

  *pjvmUserLoad   = lastUserRes;
  *pjvmKernelLoad = lastKernelRes;
  *psystemTotalLoad = t;

  lastTime   = time;
  lastUser   = user;
  lastKernel = kernel;

  return OS_OK;
}

int CPUPerformanceInterface::CPUPerformance::context_switch_rate(double* rate) {
  return perf_context_switch_rate(&_counters, rate);
}

CPUPerformanceInterface::CPUPerformanceInterface() {
  _impl = NULL;
}

bool CPUPerformanceInterface::initialize() {
  _impl = new CPUPerformanceInterface::CPUPerformance();
  return _impl != NULL && _impl->initialize();
}

CPUPerformanceInterface::~CPUPerformanceInterface(void) {
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
  friend class SystemProcessInterface;
 private:
  class ProcessIterator : public CHeapObj<mtInternal> {
    friend class SystemProcessInterface::SystemProcesses;
   private:
    DIR*           _dir;
    struct dirent* _entry;
    bool           _valid;

    ProcessIterator();
    ~ProcessIterator();
    bool initialize();

    bool is_valid() const { return _valid; }
    bool is_valid_entry(struct dirent* const entry) const;
    bool is_dir(const char* const name) const;
    char* allocate_string(const char* const str) const;
    int current(SystemProcess* const process_info);
    int next_process();
  };

  ProcessIterator* _iterator;
  SystemProcesses();
  bool initialize();
  ~SystemProcesses();

  //information about system processes
  int system_processes(SystemProcess** system_processes, int* no_of_sys_processes) const;
};

bool SystemProcessInterface::SystemProcesses::ProcessIterator::is_dir(const char* name) const {
  struct stat64 mystat;
  int ret_val = 0;

  ret_val = ::stat64(name, &mystat);

  if (ret_val < 0) {
    return false;
  }
  ret_val = S_ISDIR(mystat.st_mode);
  return ret_val > 0;
}

// if it has a numeric name, is a directory and has a 'psinfo' file in it
bool SystemProcessInterface::SystemProcesses::ProcessIterator::is_valid_entry(struct dirent* entry) const {
  // ignore the "." and ".." directories
  if ((strcmp(entry->d_name, ".") == 0) ||
      (strcmp(entry->d_name, "..") == 0)) {
    return false;
  }

  char buffer[PATH_MAX] = {0};
  uint64_t size = 0;
  bool result = false;
  FILE *fp = NULL;

  if (atoi(entry->d_name) != 0) {
    jio_snprintf(buffer, PATH_MAX, "/proc/%s", entry->d_name);

    if (is_dir(buffer)) {
      memset(buffer, 0, PATH_MAX);
      jio_snprintf(buffer, PATH_MAX, "/proc/%s/psinfo", entry->d_name);
      if ((fp = fopen(buffer, "r")) != NULL) {
        int nread = 0;
        psinfo_t psinfo_data;
        if ((nread = fread(&psinfo_data, 1, sizeof(psinfo_t), fp)) != -1) {
          // only considering system process owned by root
          if (psinfo_data.pr_uid == 0) {
            result = true;
          }
        }
      }
    }
  }

  if (fp != NULL) {
    fclose(fp);
  }

  return result;
}

char* SystemProcessInterface::SystemProcesses::ProcessIterator::allocate_string(const char* str) const {
  if (str != NULL) {
    size_t len = strlen(str);
    char* tmp = NEW_C_HEAP_ARRAY(char, len+1, mtInternal);
    strncpy(tmp, str, len);
    tmp[len] = '\0';
    return tmp;
  }
  return NULL;
}

int SystemProcessInterface::SystemProcesses::ProcessIterator::current(SystemProcess* process_info) {
  if (!is_valid()) {
    return OS_ERR;
  }

  char psinfo_path[PATH_MAX] = {0};
  jio_snprintf(psinfo_path, PATH_MAX, "/proc/%s/psinfo", _entry->d_name);

  FILE *fp = NULL;
  if ((fp = fopen(psinfo_path, "r")) == NULL) {
    return OS_ERR;
  }

  int nread = 0;
  psinfo_t psinfo_data;
  if ((nread = fread(&psinfo_data, 1, sizeof(psinfo_t), fp)) == -1) {
    fclose(fp);
    return OS_ERR;
  }

  char *exe_path = NULL;
  if ((psinfo_data.pr_fname != NULL) &&
      (psinfo_data.pr_psargs != NULL)) {
    char *path_substring = strstr(psinfo_data.pr_psargs, psinfo_data.pr_fname);
    if (path_substring != NULL) {
      int len = path_substring - psinfo_data.pr_psargs;
      exe_path = NEW_C_HEAP_ARRAY(char, len+1, mtInternal);
      if (exe_path != NULL) {
        jio_snprintf(exe_path, len, "%s", psinfo_data.pr_psargs);
        exe_path[len] = '\0';
      }
    }
  }

  process_info->set_pid(atoi(_entry->d_name));
  process_info->set_name(allocate_string(psinfo_data.pr_fname));
  process_info->set_path(allocate_string(exe_path));
  process_info->set_command_line(allocate_string(psinfo_data.pr_psargs));

  if (exe_path != NULL) {
    FREE_C_HEAP_ARRAY(char, exe_path);
  }

  if (fp != NULL) {
    fclose(fp);
  }

  return OS_OK;
}

int SystemProcessInterface::SystemProcesses::ProcessIterator::next_process() {
  if (!is_valid()) {
    return OS_ERR;
  }

  do {
    _entry = os::readdir(_dir);
    if (_entry == NULL) {
      // Error or reached end.  Could use errno to distinguish those cases.
      _valid = false;
      return OS_ERR;
    }
  } while(!is_valid_entry(_entry));

  _valid = true;
  return OS_OK;
}

SystemProcessInterface::SystemProcesses::ProcessIterator::ProcessIterator() {
  _dir = NULL;
  _entry = NULL;
  _valid = false;
}

bool SystemProcessInterface::SystemProcesses::ProcessIterator::initialize() {
  _dir = os::opendir("/proc");
  _entry = NULL;
  _valid = true;
  next_process();

  return true;
}

SystemProcessInterface::SystemProcesses::ProcessIterator::~ProcessIterator() {
  if (_dir != NULL) {
    os::closedir(_dir);
  }
}

SystemProcessInterface::SystemProcesses::SystemProcesses() {
  _iterator = NULL;
}

bool SystemProcessInterface::SystemProcesses::initialize() {
  _iterator = new SystemProcessInterface::SystemProcesses::ProcessIterator();
  return _iterator != NULL && _iterator->initialize();
}

SystemProcessInterface::SystemProcesses::~SystemProcesses() {
  if (_iterator != NULL) {
    delete _iterator;
  }
}

int SystemProcessInterface::SystemProcesses::system_processes(SystemProcess** system_processes, int* no_of_sys_processes) const {
  assert(system_processes != NULL, "system_processes pointer is NULL!");
  assert(no_of_sys_processes != NULL, "system_processes counter pointer is NULL!");
  assert(_iterator != NULL, "iterator is NULL!");

  // initialize pointers
  *no_of_sys_processes = 0;
  *system_processes = NULL;

  while (_iterator->is_valid()) {
    SystemProcess* tmp = new SystemProcess();
    _iterator->current(tmp);

    //if already existing head
    if (*system_processes != NULL) {
      //move "first to second"
      tmp->set_next(*system_processes);
    }
    // new head
    *system_processes = tmp;
    // increment
    (*no_of_sys_processes)++;
    // step forward
    _iterator->next_process();
  }
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
  return _impl != NULL && _impl->initialize();

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
  if (_cpu_info == NULL) {
    return false;
  }
  _cpu_info->set_number_of_hardware_threads(VM_Version_Ext::number_of_threads());
  _cpu_info->set_number_of_cores(VM_Version_Ext::number_of_cores());
  _cpu_info->set_number_of_sockets(VM_Version_Ext::number_of_sockets());
  _cpu_info->set_cpu_name(VM_Version_Ext::cpu_name());
  _cpu_info->set_cpu_description(VM_Version_Ext::cpu_description());
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
  friend class NetworkPerformanceInterface;
 private:
  NetworkPerformance();
  NetworkPerformance(const NetworkPerformance& rhs); // no impl
  NetworkPerformance& operator=(const NetworkPerformance& rhs); // no impl
  bool initialize();
  ~NetworkPerformance();
  int network_utilization(NetworkInterface** network_interfaces) const;
};

NetworkPerformanceInterface::NetworkPerformance::NetworkPerformance() {

}

bool NetworkPerformanceInterface::NetworkPerformance::initialize() {
  return true;
}

NetworkPerformanceInterface::NetworkPerformance::~NetworkPerformance() {

}

int NetworkPerformanceInterface::NetworkPerformance::network_utilization(NetworkInterface** network_interfaces) const
{
  kstat_ctl_t* ctl = kstat_open();
  if (ctl == NULL) {
    return OS_ERR;
  }

  NetworkInterface* ret = NULL;
  for (kstat_t* k = ctl->kc_chain; k != NULL; k = k->ks_next) {
    if (strcmp(k->ks_class, "net") != 0) {
      continue;
    }
    if (strcmp(k->ks_module, "link") != 0) {
      continue;
    }

    if (kstat_read(ctl, k, NULL) == -1) {
      return OS_ERR;
    }

    uint64_t bytes_in = UINT64_MAX;
    uint64_t bytes_out = UINT64_MAX;
    for (int i = 0; i < k->ks_ndata; ++i) {
      kstat_named_t* data = &reinterpret_cast<kstat_named_t*>(k->ks_data)[i];
      if (strcmp(data->name, "rbytes64") == 0) {
        bytes_in = data->value.ui64;
      }
      else if (strcmp(data->name, "obytes64") == 0) {
        bytes_out = data->value.ui64;
      }
    }

    if ((bytes_in != UINT64_MAX) && (bytes_out != UINT64_MAX)) {
      NetworkInterface* cur = new NetworkInterface(k->ks_name, bytes_in, bytes_out, ret);
      ret = cur;
    }
  }

  kstat_close(ctl);
  *network_interfaces = ret;

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
  return _impl != NULL && _impl->initialize();
}

int NetworkPerformanceInterface::network_utilization(NetworkInterface** network_interfaces) const {
  return _impl->network_utilization(network_interfaces);
}
