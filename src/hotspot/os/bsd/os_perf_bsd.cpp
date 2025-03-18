/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/os.hpp"
#include "runtime/os_perf.hpp"
#include "runtime/vm_version.hpp"
#include "utilities/globalDefinitions.hpp"

#ifdef __APPLE__
  #import <libproc.h>
  #include <mach/mach.h>
  #include <mach/task_info.h>
#else
# if !defined(__NetBSD__)
  #include <sys/user.h>
# endif
  #include <sys/sched.h>
  #include <sys/resource.h>
  #define NET_RT_IFLIST2 NET_RT_IFLIST
  #define RTM_IFINFO2    RTM_IFINFO
#endif
#ifdef __NetBSD__
  #include <uvm/uvm_extern.h>
#endif
#include <sys/time.h>
#include <sys/sysctl.h>
#include <sys/socket.h>
#include <net/if.h>
#include <net/if_dl.h>
#include <net/route.h>
#include <sys/times.h>

static const time_t NANOS_PER_SEC = 1000000000LL;
static const time_t MICROS_PER_SEC = 1000000LL;

class CPUPerformanceInterface::CPUPerformance : public CHeapObj<mtInternal> {
   friend class CPUPerformanceInterface;
 private:
#ifdef __APPLE__
  uint64_t _jvm_real;
  uint64_t _total_csr_nanos;
  uint64_t _jvm_user;
  uint64_t _jvm_system;
  long _jvm_context_switches;
  long _used_ticks;
  long _total_ticks;
  int  _active_processor_count;
#else
  struct CPUTicks {
    uint64_t usedTicks;
    uint64_t totalTicks;
  };

  struct JVMTicks {
    uint64_t userTicks;
    uint64_t systemTicks;
    CPUTicks cpuTicks;
  };

  int _num_procs;
  int _stathz;          // statistics clock frequency
  JVMTicks _jvm_ticks;
  CPUTicks* _cpus;
  long _total_csr_nanos;
  long _jvm_context_switches;

  int init_stathz(void);
  uint64_t tvtoticks(struct timeval tv);
  int get_cpu_ticks(CPUTicks *ticks, int which_logical_cpu);
  int get_jvm_ticks(JVMTicks *jvm_ticks);
#endif

  bool now_in_nanos(uint64_t* resultp) {
    struct timespec tp;
    int status = clock_gettime(CLOCK_REALTIME, &tp);
    assert(status == 0, "clock_gettime error: %s", os::strerror(errno));
    if (status != 0) {
      return false;
    }
    *resultp = tp.tv_sec * NANOS_PER_SEC + tp.tv_nsec;
    return true;
  }
  double normalize(double value) {
    return MIN2<double>(MAX2<double>(value, 0.0), 1.0);
  }

  int cpu_load(int which_logical_cpu, double* cpu_load);
  int context_switch_rate(double* rate);
  int cpu_load_total_process(double* cpu_load);
  int cpu_loads_process(double* pjvmUserLoad, double* pjvmKernelLoad, double* psystemTotalLoad);

  NONCOPYABLE(CPUPerformance);

 public:
  CPUPerformance();
  bool initialize();
  ~CPUPerformance();
};

#if defined(__APPLE__)

CPUPerformanceInterface::CPUPerformance::CPUPerformance() {
  _jvm_real = 0;
  _total_csr_nanos= 0;
  _jvm_context_switches = 0;
  _jvm_user = 0;
  _jvm_system = 0;
  _used_ticks = 0;
  _total_ticks = 0;
  _active_processor_count = 0;
}

bool CPUPerformanceInterface::CPUPerformance::initialize() {
  return true;
}

CPUPerformanceInterface::CPUPerformance::~CPUPerformance() {
}

int CPUPerformanceInterface::CPUPerformance::cpu_load(int which_logical_cpu, double* cpu_load) {
  return FUNCTIONALITY_NOT_IMPLEMENTED;
}

int CPUPerformanceInterface::CPUPerformance::cpu_load_total_process(double* cpu_load) {
  host_name_port_t host = mach_host_self();
  host_flavor_t flavor = HOST_CPU_LOAD_INFO;
  mach_msg_type_number_t host_info_count = HOST_CPU_LOAD_INFO_COUNT;
  host_cpu_load_info_data_t cpu_load_info;

  kern_return_t kr = host_statistics(host, flavor, (host_info_t)&cpu_load_info, &host_info_count);
  if (kr != KERN_SUCCESS) {
    return OS_ERR;
  }

  long used_ticks  = cpu_load_info.cpu_ticks[CPU_STATE_USER] + cpu_load_info.cpu_ticks[CPU_STATE_NICE] + cpu_load_info.cpu_ticks[CPU_STATE_SYSTEM];
  long total_ticks = used_ticks + cpu_load_info.cpu_ticks[CPU_STATE_IDLE];

  if (_used_ticks == 0 || _total_ticks == 0) {
    // First call, just set the values
    _used_ticks  = used_ticks;
    _total_ticks = total_ticks;
    return OS_ERR;
  }

  long used_delta  = used_ticks - _used_ticks;
  long total_delta = total_ticks - _total_ticks;

  _used_ticks  = used_ticks;
  _total_ticks = total_ticks;

  if (total_delta == 0) {
    // Avoid division by zero
    return OS_ERR;
  }

  *cpu_load = (double)used_delta / total_delta;

  return OS_OK;
}

int CPUPerformanceInterface::CPUPerformance::cpu_loads_process(double* pjvmUserLoad, double* pjvmKernelLoad, double* psystemTotalLoad) {
  int result = cpu_load_total_process(psystemTotalLoad);

  struct tms buf;
  clock_t jvm_real = times(&buf);
  if (jvm_real == (clock_t) (-1)) {
    return OS_ERR;
  }

  int active_processor_count = os::active_processor_count();
  uint64_t jvm_user = buf.tms_utime;
  uint64_t jvm_system = buf.tms_stime;

  if (active_processor_count != _active_processor_count) {
    // Change in active processor count
    result = OS_ERR;
  } else {
    uint64_t delta = active_processor_count * (jvm_real - _jvm_real);
    if (delta == 0) {
      // Avoid division by zero
      return OS_ERR;
    }

    *pjvmUserLoad = normalize((double)(jvm_user - _jvm_user) / delta);
    *pjvmKernelLoad = normalize((double)(jvm_system - _jvm_system) / delta);
  }

  _active_processor_count = active_processor_count;
  _jvm_real = jvm_real;
  _jvm_user = jvm_user;
  _jvm_system = jvm_system;

  return result;
}

#else // !APPLE

CPUPerformanceInterface::CPUPerformance::CPUPerformance() {
  _num_procs = 0;
  _stathz = 0;
  _jvm_ticks = JVMTicks();
  _cpus = nullptr;
  _total_csr_nanos= 0;
  _jvm_context_switches = 0;
}

bool CPUPerformanceInterface::CPUPerformance::initialize() {
  _num_procs = os::processor_count();
  if (_num_procs < 1) {
    return false;
  }

  if (init_stathz() != OS_OK) {
    return false;
  }

  size_t cpus_array_count = _num_procs + 1;
  _cpus = NEW_C_HEAP_ARRAY_RETURN_NULL(CPUTicks, cpus_array_count, mtInternal);
  if (_cpus == nullptr) {
    return false;
  }
  memset(_cpus, 0, cpus_array_count * sizeof(*_cpus));

  // For the CPU load total
  if (get_cpu_ticks(&_cpus[_num_procs], -1) != OS_OK) {
    FREE_C_HEAP_ARRAY(CPUTicks, _cpus);
    _cpus = nullptr;
    return false;
  }

  // For each CPU. Ignoring errors.
  for (int i = 0; i < _num_procs; i++) {
    get_cpu_ticks(&_cpus[i], i);
  }

  // For JVM load
  if (get_jvm_ticks(&_jvm_ticks) != OS_OK) {
    FREE_C_HEAP_ARRAY(CPUTicks, _cpus);
    _cpus = nullptr;
    return false;
  }
  return true;
}

CPUPerformanceInterface::CPUPerformance::~CPUPerformance() {
  if (_cpus != nullptr) {
    FREE_C_HEAP_ARRAY(CPUTicks, _cpus);
  }
}

int CPUPerformanceInterface::CPUPerformance::init_stathz(void) {
  struct clockinfo ci;
  size_t length = sizeof(ci);
  int mib[] = { CTL_KERN, KERN_CLOCKRATE };
  const u_int miblen = sizeof(mib) / sizeof(mib[0]);

  if (sysctl(mib, miblen, &ci, &length, nullptr, 0) == -1) {
    return OS_ERR;
  }

  _stathz = ci.stathz;

  return OS_OK;
}

int CPUPerformanceInterface::CPUPerformance::get_cpu_ticks(CPUTicks *ticks, int which_logical_cpu) {
#if defined(__NetBSD__)
  uint64_t cpu_load_info[CPUSTATES];
#else
  long cpu_load_info[CPUSTATES];
#endif
  size_t length = sizeof(cpu_load_info);

  if (which_logical_cpu == -1) {
#if defined(__OpenBSD__)
    int mib[] = { CTL_KERN, KERN_CPTIME };
    const u_int miblen = sizeof(mib) / sizeof(mib[0]);

    if (sysctl(mib, miblen, &cpu_load_info, &length, nullptr, 0) == -1) {
      return OS_ERR;
    }
    // OpenBSD returns the sum/_num_procs. Unify with other stat units
    for (size_t i=0; i < CPUSTATES; i++) {
       cpu_load_info[i] *= _num_procs;
    }
#else
    if (sysctlbyname("kern.cp_time", &cpu_load_info, &length, nullptr, 0) == -1) {
      return OS_ERR;
    }
#endif
  } else {
#if defined(__OpenBSD__)
    int mib[] = { CTL_KERN, KERN_CPTIME2, which_logical_cpu };
    const u_int miblen = sizeof(mib) / sizeof(mib[0]);

    if (sysctl(mib, miblen, &cpu_load_info, &length, nullptr, 0) == -1) {
      return OS_ERR;
    }
#elif defined(__FreeBSD__)
    size_t alllength = length * _num_procs;
    long *allcpus = NEW_C_HEAP_ARRAY(long, CPUSTATES * _num_procs, mtInternal);

    if (sysctlbyname("kern.cp_times", allcpus, &alllength, nullptr, 0) == -1) {
      FREE_C_HEAP_ARRAY(long, allcpus);
      return OS_ERR;
    }

    memcpy(cpu_load_info, &allcpus[which_logical_cpu * CPUSTATES], sizeof(long) * CPUSTATES);
    FREE_C_HEAP_ARRAY(long, allcpus);
#else
    char name[24];
    snprintf(name, sizeof(name), "kern.cp_time.%d", which_logical_cpu);
    if (sysctlbyname(name, &cpu_load_info, &length, nullptr, 0) == -1) {
      return OS_ERR;
    }
#endif
  }

  ticks->totalTicks = 0;
  for (size_t i=0; i < CPUSTATES; i++) {
     ticks->totalTicks += cpu_load_info[i];
  }
  ticks->usedTicks = ticks->totalTicks - cpu_load_info[CP_IDLE];

  return OS_OK;
}

uint64_t CPUPerformanceInterface::CPUPerformance::tvtoticks(struct timeval tv) {
  uint64_t ticks = 0;
  ticks += (uint64_t)tv.tv_sec * _stathz;
  ticks += (uint64_t)tv.tv_usec * _stathz / MICROS_PER_SEC;
  return ticks;
}

int CPUPerformanceInterface::CPUPerformance::get_jvm_ticks(JVMTicks *jvm_ticks) {
  struct rusage usage;

  if (getrusage(RUSAGE_SELF, &usage) != 0) {
    return OS_ERR;
  }

  if (get_cpu_ticks(&jvm_ticks->cpuTicks, -1) != OS_OK) {
    return OS_ERR;
  }

  jvm_ticks->userTicks = tvtoticks(usage.ru_utime);
  jvm_ticks->systemTicks = tvtoticks(usage.ru_stime);

  // ensure values are consistent with each other
  if (jvm_ticks->userTicks + jvm_ticks->systemTicks > jvm_ticks->cpuTicks.usedTicks)
    jvm_ticks->cpuTicks.usedTicks = jvm_ticks->userTicks + jvm_ticks->systemTicks;

  if (jvm_ticks->cpuTicks.usedTicks > jvm_ticks->cpuTicks.totalTicks)
    jvm_ticks->cpuTicks.totalTicks = jvm_ticks->cpuTicks.usedTicks;

  return OS_OK;
}

int CPUPerformanceInterface::CPUPerformance::cpu_load(int which_logical_cpu, double* cpu_load) {
  CPUTicks curCPUTicks, *prevCPUTicks;
  uint64_t cpuUsedDelta, cpuTotalDelta;

  *cpu_load = 0.0;

  if (_cpus == nullptr) {
    return OS_ERR;
  }

  if (which_logical_cpu < -1 || which_logical_cpu >= _num_procs) {
    return OS_ERR;
  }

  if (get_cpu_ticks(&curCPUTicks, which_logical_cpu) != OS_OK) {
    return OS_ERR;
  }

  const int cpu_idx = (which_logical_cpu == -1) ? _num_procs : which_logical_cpu;
  prevCPUTicks = &_cpus[cpu_idx];

  cpuUsedDelta = curCPUTicks.usedTicks > prevCPUTicks->usedTicks ?
    curCPUTicks.usedTicks - prevCPUTicks->usedTicks : 0;
  cpuTotalDelta = curCPUTicks.totalTicks > prevCPUTicks->totalTicks ?
    curCPUTicks.totalTicks - prevCPUTicks->totalTicks : 0;

  prevCPUTicks->usedTicks = curCPUTicks.usedTicks;
  prevCPUTicks->totalTicks = curCPUTicks.totalTicks;

  if (cpuTotalDelta == 0)
    return OS_ERR;

  if (cpuUsedDelta > cpuTotalDelta)
    cpuTotalDelta = cpuUsedDelta;

  *cpu_load = (double)cpuUsedDelta/cpuTotalDelta;

  return OS_OK;
}

int CPUPerformanceInterface::CPUPerformance::cpu_load_total_process(double* cpu_load) {
  double jvmUserLoad, jvmKernelLoad, systemTotalLoad;

  if (cpu_loads_process(&jvmUserLoad, &jvmKernelLoad, &systemTotalLoad) != OS_OK) {
    *cpu_load = 0.0;
    return OS_ERR;
  }

  *cpu_load = jvmUserLoad + jvmKernelLoad;
  return OS_OK;
}

int CPUPerformanceInterface::CPUPerformance::cpu_loads_process(double* pjvmUserLoad, double* pjvmKernelLoad, double* psystemTotalLoad) {
  JVMTicks curJVMTicks;
  CPUTicks *curCPUTicks, *prevCPUTicks;

  uint64_t jvmUserDelta, jvmSystemDelta, cpuUsedDelta, cpuTotalDelta;

  *pjvmUserLoad = 0.0;
  *pjvmKernelLoad = 0.0;
  *psystemTotalLoad = 0.0;

  if (_cpus == nullptr) {
    return OS_ERR;
  }

  if (get_jvm_ticks(&curJVMTicks) != OS_OK) {
    return OS_ERR;
  }

  curCPUTicks = &curJVMTicks.cpuTicks;
  prevCPUTicks = &_jvm_ticks.cpuTicks;

  jvmUserDelta = curJVMTicks.userTicks > _jvm_ticks.userTicks ?
    curJVMTicks.userTicks - _jvm_ticks.userTicks : 0;
  jvmSystemDelta = curJVMTicks.systemTicks > _jvm_ticks.systemTicks ?
    curJVMTicks.systemTicks - _jvm_ticks.systemTicks : 0;

  cpuUsedDelta = curCPUTicks->usedTicks > prevCPUTicks->usedTicks ?
    curCPUTicks->usedTicks - prevCPUTicks->usedTicks : 0;
  cpuTotalDelta = curCPUTicks->totalTicks > prevCPUTicks->totalTicks ?
    curCPUTicks->totalTicks - prevCPUTicks->totalTicks : 0;

  _jvm_ticks.userTicks = curJVMTicks.userTicks;
  _jvm_ticks.systemTicks = curJVMTicks.systemTicks;
  prevCPUTicks->usedTicks = curCPUTicks->usedTicks;
  prevCPUTicks->totalTicks = curCPUTicks->totalTicks;

  // ensure values are consistent with each other
  if (jvmUserDelta + jvmSystemDelta > cpuUsedDelta)
    cpuUsedDelta = jvmUserDelta + jvmSystemDelta;

  if (cpuUsedDelta > cpuTotalDelta)
    cpuTotalDelta = cpuUsedDelta;

  if (cpuTotalDelta == 0) {
    return OS_ERR;
  }

  *pjvmUserLoad = (double)jvmUserDelta/cpuTotalDelta;
  *pjvmKernelLoad = (double)jvmSystemDelta/cpuTotalDelta;
  *psystemTotalLoad = (double)cpuUsedDelta/cpuTotalDelta;

  return OS_OK;
}

#endif

int CPUPerformanceInterface::CPUPerformance::context_switch_rate(double* rate) {
#ifdef __APPLE__
  mach_port_t task = mach_task_self();
  mach_msg_type_number_t task_info_count = TASK_INFO_MAX;
  task_info_data_t task_info_data;
  kern_return_t kr = task_info(task, TASK_EVENTS_INFO, (task_info_t)task_info_data, &task_info_count);
  if (kr != KERN_SUCCESS) {
    return OS_ERR;
  }

  long jvm_context_switches = ((task_events_info_t)task_info_data)->csw;
#elif defined(__FreeBSD__)
  unsigned int jvm_context_switches = 0;
  size_t length = sizeof(jvm_context_switches);
  if (sysctlbyname("vm.stats.sys.v_swtch", &jvm_context_switches, &length, nullptr, 0) == -1) {
    return OS_ERR;
  }
#elif defined(__OpenBSD__) || defined(__NetBSD__)
#if defined(__OpenBSD__)
  struct uvmexp js;
  int mib[] = { CTL_VM, VM_UVMEXP };
#else
  struct uvmexp_sysctl js;
  int mib[] = { CTL_VM, VM_UVMEXP2 };
#endif
  size_t jslength = sizeof(js);
  const u_int miblen = sizeof(mib) / sizeof(mib[0]);
  unsigned int jvm_context_switches = 0;
  if (sysctl(mib, miblen, &js, &jslength, nullptr, 0) != 0) {
    return OS_ERR;
  }

  jvm_context_switches = (unsigned int)js.swtch;
#endif

  int result = OS_OK;
  if (_total_csr_nanos == 0 || _jvm_context_switches == 0) {
    // First call just set initial values.
    result = OS_ERR;
  }

  uint64_t total_csr_nanos;
  if(!now_in_nanos(&total_csr_nanos)) {
    return OS_ERR;
  }
  double delta_in_sec = (double)(total_csr_nanos - _total_csr_nanos) / (double)NANOS_PER_SEC;
  if (delta_in_sec == 0.0) {
    // Avoid division by zero
    return OS_ERR;
  }

  *rate = (jvm_context_switches - _jvm_context_switches) / delta_in_sec;

  _jvm_context_switches = jvm_context_switches;
  _total_csr_nanos = total_csr_nanos;

  return result;
}

CPUPerformanceInterface::CPUPerformanceInterface() {
  _impl = nullptr;
}

bool CPUPerformanceInterface::initialize() {
  _impl = new CPUPerformanceInterface::CPUPerformance();
  return _impl->initialize();
}

CPUPerformanceInterface::~CPUPerformanceInterface() {
  if (_impl != nullptr) {
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
  SystemProcesses();
  bool initialize();
  NONCOPYABLE(SystemProcesses);
  ~SystemProcesses();

  //information about system processes
  int system_processes(SystemProcess** system_processes, int* no_of_sys_processes) const;
};

SystemProcessInterface::SystemProcesses::SystemProcesses() {
}

bool SystemProcessInterface::SystemProcesses::initialize() {
  return true;
}

SystemProcessInterface::SystemProcesses::~SystemProcesses() {
}
int SystemProcessInterface::SystemProcesses::system_processes(SystemProcess** system_processes, int* no_of_sys_processes) const {
  assert(system_processes != nullptr, "system_processes pointer is null!");
  assert(no_of_sys_processes != nullptr, "system_processes counter pointer is null!");
#ifdef __APPLE__
  pid_t* pids = nullptr;
  int pid_count = 0;
  ResourceMark rm;

  int try_count = 0;
  while (pids == nullptr) {
    // Find out buffer size
    size_t pids_bytes = proc_listpids(PROC_ALL_PIDS, 0, nullptr, 0);
    if (pids_bytes <= 0) {
      return OS_ERR;
    }
    pid_count = pids_bytes / sizeof(pid_t);
    pids = NEW_RESOURCE_ARRAY(pid_t, pid_count);
    memset(pids, 0, pids_bytes);

    pids_bytes = proc_listpids(PROC_ALL_PIDS, 0, pids, pids_bytes);
    if (pids_bytes <= 0) {
       // couldn't fit buffer, retry.
      FREE_RESOURCE_ARRAY(pid_t, pids, pid_count);
      pids = nullptr;
      try_count++;
      if (try_count > 3) {
      return OS_ERR;
      }
    } else {
      pid_count = pids_bytes / sizeof(pid_t);
    }
  }

  int process_count = 0;
  SystemProcess* next = nullptr;
  for (int i = 0; i < pid_count; i++) {
    pid_t pid = pids[i];
    if (pid != 0) {
      char buffer[PROC_PIDPATHINFO_MAXSIZE];
      memset(buffer, 0 , sizeof(buffer));
      if (proc_pidpath(pid, buffer, sizeof(buffer)) != -1) {
        int length = strlen(buffer);
        if (length > 0) {
          SystemProcess* current = new SystemProcess();
          char * path = NEW_C_HEAP_ARRAY(char, length + 1, mtInternal);
          strcpy(path, buffer);
          current->set_path(path);
          current->set_pid((int)pid);
          current->set_next(next);
          next = current;
          process_count++;
        }
      }
    }
  }

  *no_of_sys_processes = process_count;
  *system_processes = next;

  return OS_OK;
#elif defined(__FreeBSD__)
  struct kinfo_proc *lproc;
  int mib[] = { CTL_KERN, KERN_PROC, KERN_PROC_PROC };
  const u_int miblen = sizeof(mib) / sizeof(mib[0]);
  size_t length;
  int pid_count;

  if (sysctl(mib, miblen, nullptr, &length, nullptr, 0) == -1) {
    return OS_ERR;
  }

  lproc = NEW_C_HEAP_ARRAY_RETURN_NULL(struct kinfo_proc, length, mtInternal);
  if (lproc == nullptr) {
    return OS_ERR;
  }

  if (sysctl(mib, miblen, lproc, &length, nullptr, 0) == -1) {
    FREE_C_HEAP_ARRAY(struct kinfo_proc, lproc);
    return OS_ERR;
  }

  pid_count = length / sizeof(*lproc);
  int process_count = 0;
  SystemProcess *next = nullptr;

  for (int i = 0; i < pid_count; i++) {
    // Executable path
    int pmib[] = { CTL_KERN, KERN_PROC, KERN_PROC_PATHNAME, lproc[i].ki_pid };
    const u_int pmiblen = sizeof(pmib) / sizeof(pmib[0]);
    char pbuf[PATH_MAX];
    size_t plen = sizeof(pbuf);
    if (sysctl(pmib, pmiblen, pbuf, &plen, nullptr, 0) == -1) {
      continue;
    }
    plen = strnlen(pbuf, PATH_MAX);
    if (plen == 0) {
      continue;
    }
    char *path = NEW_C_HEAP_ARRAY_RETURN_NULL(char, plen + 1, mtInternal);
    if (path == nullptr) {
      continue;
    }
    strlcpy(path, pbuf, plen + 1);

    // Command line
    int amib[] = { CTL_KERN, KERN_PROC, KERN_PROC_ARGS, lproc[i].ki_pid };
    const u_int amiblen = sizeof(amib) / sizeof(amib[0]);
    char abuf[ARG_MAX];
    size_t alen = sizeof(abuf);
    char *cmdline = nullptr;
    if (sysctl(amib, amiblen, abuf, &alen, nullptr, 0) != -1 && alen > 0) {
      // Arguments are NUL separated in the result, replace that with a space
      for (size_t j = 0; j < alen; j++) {
        if (abuf[j] == '\0') {
          abuf[j] = ' ';
        }
      }
      cmdline = NEW_C_HEAP_ARRAY_RETURN_NULL(char, alen + 1, mtInternal);
      if (cmdline != nullptr) {
        strlcpy(cmdline, abuf, alen + 1);
      }
    }

    SystemProcess* current = new SystemProcess();
    current->set_pid((int)lproc[i].ki_pid);
    current->set_path(path);
    current->set_command_line(cmdline);
    current->set_next(next);
    next = current;
    process_count++;
  }

  FREE_C_HEAP_ARRAY(struct kinfo_proc, lproc);
  *no_of_sys_processes = process_count;
  *system_processes = next;

  return OS_OK;
#elif defined(__OpenBSD__)
  struct kinfo_proc *lproc;
  int mib[] = { CTL_KERN, KERN_PROC, KERN_PROC_ALL, 0, sizeof(struct kinfo_proc), 0 };
  const u_int miblen = sizeof(mib) / sizeof(mib[0]);
  size_t length;
  int pid_count, ret = OS_OK;

  if (sysctl(mib, miblen, nullptr, &length, nullptr, 0) == -1) {
    return OS_ERR;
  }

  lproc = NEW_C_HEAP_ARRAY_RETURN_NULL(struct kinfo_proc, length, mtInternal);
  if (lproc == nullptr) {
    return OS_ERR;
  }

  mib[5] = length / sizeof(struct kinfo_proc);

  if (sysctl(mib, miblen, lproc, &length, nullptr, 0) == -1) {
    FREE_C_HEAP_ARRAY(struct kinfo_proc, lproc);
    return OS_ERR;
  }

  pid_count = length / sizeof(*lproc);
  int process_count = 0;
  SystemProcess *next = nullptr;

  for (int i = 0; i < pid_count; i++) {
    int pmib[] = { CTL_KERN, KERN_PROC_ARGS, lproc[i].p_pid, KERN_PROC_ARGV };
    const u_int pmiblen = sizeof(pmib) / sizeof(pmib[0]);
    size_t slen;

    if (sysctl(pmib, pmiblen, nullptr, &length, nullptr, 0) == -1) {
      ret = OS_ERR;
      break;
    }

    // Allocate space for args and get the arguments
    char **argv = NEW_C_HEAP_ARRAY_RETURN_NULL(char*, length, mtInternal);
    if (argv == nullptr) {
      ret = OS_ERR;
      break;
    }

    if (sysctl(pmib, pmiblen, argv, &length, nullptr, 0) == -1) {
      ret = OS_ERR;
      FREE_C_HEAP_ARRAY(char*, argv);
      break;
    }

    if (argv[0] == nullptr) {
      FREE_C_HEAP_ARRAY(char*, argv);
      continue;
    }

    slen = strnlen(argv[0], length);
    if (slen > 0) {
      SystemProcess* current = new SystemProcess();
      char * path = NEW_C_HEAP_ARRAY(char, slen + 1, mtInternal);
      strlcpy(path, argv[0], slen + 1);
      current->set_path(path);
      current->set_pid((int)lproc[i].p_pid);
      /* TODO: build concatenated string for current->set_command_line() */
      current->set_next(next);
      next = current;
      process_count++;
    }

    FREE_C_HEAP_ARRAY(char*, argv);
  }

  FREE_C_HEAP_ARRAY(struct kinfo_proc, lproc);

  if (ret != OS_OK) {
    SystemProcess* current = next;
    while (current) {
      next = current->next();
      delete current; /* ~SystemProcess frees internal strings */
      current = next;
    }
    return ret;
  }

  *no_of_sys_processes = process_count;
  *system_processes = next;

  return OS_OK;
#elif defined(__NetBSD__)
  struct kinfo_proc2 *lproc;
  int mib[] = { CTL_KERN, KERN_PROC2, KERN_PROC_ALL, 0, sizeof(struct kinfo_proc2), 0 };
  const u_int miblen = sizeof(mib) / sizeof(mib[0]);
  size_t length;
  int pid_count;

  if (sysctl(mib, miblen, nullptr, &length, nullptr, 0) == -1) {
    return OS_ERR;
  }

  lproc = NEW_C_HEAP_ARRAY_RETURN_NULL(struct kinfo_proc2, length, mtInternal);
  if (lproc == nullptr) {
    return OS_ERR;
  }

  mib[5] = length / sizeof(struct kinfo_proc2);

  if (sysctl(mib, miblen, lproc, &length, nullptr, 0) == -1) {
    FREE_C_HEAP_ARRAY(struct kinfo_proc2, lproc);
    return OS_ERR;
  }

  pid_count = length / sizeof(*lproc);
  int process_count = 0;
  SystemProcess *next = nullptr;

  for (int i = 0; i < pid_count; i++) {
    // Executable path
#if !defined(KERN_PROC_PATHNAME)
#define KERN_PROC_PATHNAME 5
#endif
    int pmib[] = { CTL_KERN, KERN_PROC_ARGS, lproc[i].p_pid, KERN_PROC_PATHNAME };
    const u_int pmiblen = sizeof(pmib) / sizeof(pmib[0]);
    char pbuf[PATH_MAX];
    size_t plen = sizeof(pbuf);
    if (sysctl(pmib, pmiblen, pbuf, &plen, nullptr, 0) == -1) {
      continue;
    }
    plen = strnlen(pbuf, PATH_MAX);
    if (plen == 0) {
      continue;
    }
    char *path = NEW_C_HEAP_ARRAY_RETURN_NULL(char, plen + 1, mtInternal);
    if (path == nullptr) {
      continue;
    }
    strlcpy(path, pbuf, plen + 1);

    // Command line
    int amib[] = { CTL_KERN, KERN_PROC_ARGS, lproc[i].p_pid, KERN_PROC_ARGV };
    const u_int amiblen = sizeof(amib) / sizeof(amib[0]);
    char abuf[ARG_MAX];
    size_t alen = sizeof(abuf);
    char *cmdline = nullptr;
    if (sysctl(amib, amiblen, abuf, &alen, nullptr, 0) != -1 && alen > 0) {
      // Arguments are NUL separated in the result, replace that with a space
      for (size_t j = 0; j < alen; j++) {
        if (abuf[j] == '\0') {
          abuf[j] = ' ';
        }
      }
      cmdline = NEW_C_HEAP_ARRAY_RETURN_NULL(char, alen + 1, mtInternal);
      if (cmdline != nullptr) {
        strlcpy(cmdline, abuf, alen + 1);
      }
    }

    SystemProcess* current = new SystemProcess();
    current->set_pid((int)lproc[i].p_pid);
    current->set_path(path);
    current->set_command_line(cmdline);
    current->set_next(next);
    next = current;
    process_count++;
  }

  FREE_C_HEAP_ARRAY(struct kinfo_proc2, lproc);
  *no_of_sys_processes = process_count;
  *system_processes = next;

  return OS_OK;
#else
  return FUNCTIONALITY_NOT_IMPLEMENTED;
#endif
}

int SystemProcessInterface::system_processes(SystemProcess** system_procs, int* no_of_sys_processes) const {
  return _impl->system_processes(system_procs, no_of_sys_processes);
}

SystemProcessInterface::SystemProcessInterface() {
  _impl = nullptr;
}

bool SystemProcessInterface::initialize() {
  _impl = new SystemProcessInterface::SystemProcesses();
  return _impl->initialize();
}

SystemProcessInterface::~SystemProcessInterface() {
  if (_impl != nullptr) {
    delete _impl;
 }
}

CPUInformationInterface::CPUInformationInterface() {
  _cpu_info = nullptr;
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
  if (_cpu_info != nullptr) {
    if (_cpu_info->cpu_name() != nullptr) {
      const char* cpu_name = _cpu_info->cpu_name();
      FREE_C_HEAP_ARRAY(char, cpu_name);
      _cpu_info->set_cpu_name(nullptr);
    }
    if (_cpu_info->cpu_description() != nullptr) {
      const char* cpu_desc = _cpu_info->cpu_description();
      FREE_C_HEAP_ARRAY(char, cpu_desc);
      _cpu_info->set_cpu_description(nullptr);
    }
    delete _cpu_info;
  }
}

int CPUInformationInterface::cpu_information(CPUInformation& cpu_info) {
  if (nullptr == _cpu_info) {
    return OS_ERR;
  }

  cpu_info = *_cpu_info; // shallow copy assignment
  return OS_OK;
}

class NetworkPerformanceInterface::NetworkPerformance : public CHeapObj<mtInternal> {
  friend class NetworkPerformanceInterface;
 private:
  NetworkPerformance();
  NONCOPYABLE(NetworkPerformance);
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

int NetworkPerformanceInterface::NetworkPerformance::network_utilization(NetworkInterface** network_interfaces) const {
  size_t len;
  int mib[] = {CTL_NET, PF_ROUTE, /* protocol number */ 0, /* address family */ 0, NET_RT_IFLIST2, /* NET_RT_FLAGS mask*/ 0};
  if (sysctl(mib, sizeof(mib) / sizeof(mib[0]), nullptr, &len, nullptr, 0) != 0) {
    return OS_ERR;
  }
  uint8_t* buf = NEW_RESOURCE_ARRAY(uint8_t, len);
  if (sysctl(mib, sizeof(mib) / sizeof(mib[0]), buf, &len, nullptr, 0) != 0) {
    return OS_ERR;
  }

  size_t index = 0;
  NetworkInterface* ret = nullptr;
  while (index < len) {
    if_msghdr* msghdr = reinterpret_cast<if_msghdr*>(buf + index);
    index += msghdr->ifm_msglen;

    if (msghdr->ifm_type != RTM_IFINFO2) {
      continue;
    }

#if defined(__APPLE__)
    if_msghdr2* msghdr2 = reinterpret_cast<if_msghdr2*>(msghdr);
    sockaddr_dl* sockaddr = reinterpret_cast<sockaddr_dl*>(msghdr2 + 1);
#else
    sockaddr_dl* sockaddr = reinterpret_cast<sockaddr_dl*>(msghdr + 1);
#endif

    // The interface name is not necessarily NUL-terminated
    char name_buf[128];
    size_t name_len = MIN2(sizeof(name_buf) - 1, static_cast<size_t>(sockaddr->sdl_nlen));
    strlcpy(name_buf, sockaddr->sdl_data, name_len + 1);

#if defined(__APPLE__)
    uint64_t bytes_in = msghdr2->ifm_data.ifi_ibytes;
    uint64_t bytes_out = msghdr2->ifm_data.ifi_obytes;
#else
    uint64_t bytes_in = msghdr->ifm_data.ifi_ibytes;
    uint64_t bytes_out = msghdr->ifm_data.ifi_obytes;
#endif

    NetworkInterface* cur = new NetworkInterface(name_buf, bytes_in, bytes_out, ret);
    ret = cur;
  }

  *network_interfaces = ret;

  return OS_OK;
}

NetworkPerformanceInterface::NetworkPerformanceInterface() {
  _impl = nullptr;
}

NetworkPerformanceInterface::~NetworkPerformanceInterface() {
  if (_impl != nullptr) {
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
