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
#endif
#ifdef __NetBSD__
  // KERN_CP_TIME gives the same tick counters host_statistics() does on macOS;
  // KERN_PROC2 is how a NetBSD userland walks the process table.
  #include <sys/sched.h>
  #include <sys/param.h>
  #include <sys/resource.h>
#endif
#if defined(__FreeBSD__) || defined(__DragonFly__)
  // CPUSTATES and the CP_* indices, and kern.cp_time to fill them.
  #include <sys/resource.h>
#endif
#ifdef __OpenBSD__
  // The same, with a sixth state (CP_SPIN) and KERN_CPTIME to read them.
  #include <sys/sched.h>
#endif

// Every BSD other than macOS keeps the machine-wide tick counters in an
// array of CPUSTATES entries; only the way to ask, the width of an entry
// and the number of states differ.  Add up everything that is not idle.
// (Decided here rather than with defined() inside a macro, which clang
// rejects under -Wexpansion-to-defined.)
#if defined(__FreeBSD__) || defined(__DragonFly__) || defined(__OpenBSD__)
  #define BSD_HAS_CP_TIME 1
#else
  #define BSD_HAS_CP_TIME 0
#endif
#include <sys/time.h>
#include <sys/sysctl.h>
#include <sys/socket.h>
#include <net/if.h>
#include <net/if_dl.h>
#include <net/route.h>
#include <sys/times.h>

static const double NANOS_PER_SEC = 1000000000.0;

class CPUPerformanceInterface::CPUPerformance : public CHeapObj<mtInternal> {
   friend class CPUPerformanceInterface;
 private:
#if defined(__APPLE__) || defined(__NetBSD__) || BSD_HAS_CP_TIME
  uint64_t _jvm_real;
  uint64_t _total_csr_nanos;
  uint64_t _jvm_user;
  uint64_t _jvm_system;
  long _jvm_context_switches;
  long _used_ticks;
  long _total_ticks;
  int  _active_processor_count;
  uint64_t* _cpu_used_ticks;
  uint64_t* _cpu_total_ticks;
  int _cpu_tick_count;

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
#endif

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

CPUPerformanceInterface::CPUPerformance::CPUPerformance() {
#if defined(__APPLE__) || defined(__NetBSD__) || BSD_HAS_CP_TIME
  _jvm_real = 0;
  _total_csr_nanos= 0;
  _jvm_context_switches = 0;
  _jvm_user = 0;
  _jvm_system = 0;
  _used_ticks = 0;
  _total_ticks = 0;
  _active_processor_count = 0;
  _cpu_used_ticks = nullptr;
  _cpu_total_ticks = nullptr;
  _cpu_tick_count = 0;
#endif
}

bool CPUPerformanceInterface::CPUPerformance::initialize() {
  return true;
}

CPUPerformanceInterface::CPUPerformance::~CPUPerformance() {
#if defined(__APPLE__) || defined(__NetBSD__) || BSD_HAS_CP_TIME
  FREE_C_HEAP_ARRAY(_cpu_used_ticks);
  FREE_C_HEAP_ARRAY(_cpu_total_ticks);
#endif
}

int CPUPerformanceInterface::CPUPerformance::cpu_load(int which_logical_cpu, double* cpu_load) {
#ifdef __NetBSD__
  // KERN_CP_TIME returns the machine-wide counters when asked for one
  // CPUSTATES array, and the per-CPU ones when asked for ncpu of them.
  int ncpu = os::processor_count();
  if (which_logical_cpu < 0 || which_logical_cpu >= ncpu) {
    return OS_ERR;
  }
  ResourceMark rm;
  size_t len = sizeof(uint64_t) * CPUSTATES * ncpu;
  uint64_t* cp_time = NEW_RESOURCE_ARRAY(uint64_t, CPUSTATES * ncpu);
  int mib[2] = { CTL_KERN, KERN_CP_TIME };
  if (sysctl(mib, 2, cp_time, &len, nullptr, 0) != 0) {
    return OS_ERR;
  }
  if (len != sizeof(uint64_t) * CPUSTATES * ncpu) {
    // The kernel answered machine-wide rather than per-CPU.
    return OS_ERR;
  }

  const uint64_t* c = cp_time + (size_t)which_logical_cpu * CPUSTATES;
  uint64_t used  = c[CP_USER] + c[CP_NICE] + c[CP_SYS] + c[CP_INTR];
  uint64_t total = used + c[CP_IDLE];

  if (_cpu_used_ticks == nullptr) {
    _cpu_used_ticks  = NEW_C_HEAP_ARRAY(uint64_t, ncpu, mtInternal);
    _cpu_total_ticks = NEW_C_HEAP_ARRAY(uint64_t, ncpu, mtInternal);
    memset(_cpu_used_ticks, 0, sizeof(uint64_t) * ncpu);
    memset(_cpu_total_ticks, 0, sizeof(uint64_t) * ncpu);
    _cpu_tick_count = ncpu;
  }
  if (which_logical_cpu >= _cpu_tick_count) {
    return OS_ERR;
  }

  uint64_t used_prev  = _cpu_used_ticks[which_logical_cpu];
  uint64_t total_prev = _cpu_total_ticks[which_logical_cpu];
  _cpu_used_ticks[which_logical_cpu]  = used;
  _cpu_total_ticks[which_logical_cpu] = total;

  if (total_prev == 0 || total <= total_prev) {
    // First call for this CPU, or the counters did not move.
    return OS_ERR;
  }

  *cpu_load = normalize((double)(used - used_prev) / (total - total_prev));
  return OS_OK;
#else
  return FUNCTIONALITY_NOT_IMPLEMENTED;
#endif
}

int CPUPerformanceInterface::CPUPerformance::cpu_load_total_process(double* cpu_load) {
#ifdef __APPLE__
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
#elif defined(__NetBSD__)
  uint64_t cp_time[CPUSTATES];
  size_t len = sizeof(cp_time);
  int mib[2] = { CTL_KERN, KERN_CP_TIME };
  if (sysctl(mib, 2, cp_time, &len, nullptr, 0) != 0) {
    return OS_ERR;
  }

  long used_ticks  = (long)(cp_time[CP_USER] + cp_time[CP_NICE] + cp_time[CP_SYS]
                            + cp_time[CP_INTR]);
  long total_ticks = used_ticks + (long)cp_time[CP_IDLE];

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
#elif BSD_HAS_CP_TIME
  long cp_time[CPUSTATES];
  size_t len = sizeof(cp_time);
#ifdef __OpenBSD__
  int mib[2] = { CTL_KERN, KERN_CPTIME };
  if (sysctl(mib, 2, cp_time, &len, nullptr, 0) != 0) {
    return OS_ERR;
  }
#else
  if (sysctlbyname("kern.cp_time", cp_time, &len, nullptr, 0) != 0) {
    return OS_ERR;
  }
#endif

  long used_ticks = 0;
  for (int i = 0; i < CPUSTATES; i++) {
    if (i != CP_IDLE) {
      used_ticks += cp_time[i];
    }
  }
  long total_ticks = used_ticks + cp_time[CP_IDLE];

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
#else
  return FUNCTIONALITY_NOT_IMPLEMENTED;
#endif
}

int CPUPerformanceInterface::CPUPerformance::cpu_loads_process(double* pjvmUserLoad, double* pjvmKernelLoad, double* psystemTotalLoad) {
#if defined(__APPLE__) || defined(__NetBSD__) || BSD_HAS_CP_TIME
  // times(2) is POSIX; only the system-wide part below needed a platform of
  // its own.
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
#else
  return FUNCTIONALITY_NOT_IMPLEMENTED;
#endif
}

int CPUPerformanceInterface::CPUPerformance::context_switch_rate(double* rate) {
#ifdef __NetBSD__
  // getrusage(2) counts this process's switches; the rate is per second of
  // wall clock since the previous call.
  struct rusage ru;
  if (getrusage(RUSAGE_SELF, &ru) != 0) {
    return OS_ERR;
  }
  uint64_t switches = (uint64_t)ru.ru_nvcsw + (uint64_t)ru.ru_nivcsw;

  uint64_t now = 0;
  if (!now_in_nanos(&now)) {
    return OS_ERR;
  }

  if (_total_csr_nanos == 0 || now <= _total_csr_nanos) {
    _total_csr_nanos = now;
    _jvm_context_switches = (long)switches;
    return OS_ERR;
  }

  double seconds = (double)(now - _total_csr_nanos) / NANOS_PER_SEC;
  *rate = (double)(switches - (uint64_t)_jvm_context_switches) / seconds;

  _total_csr_nanos = now;
  _jvm_context_switches = (long)switches;
  return OS_OK;
#endif
#ifdef __APPLE__
  mach_port_t task = mach_task_self();
  mach_msg_type_number_t task_info_count = TASK_INFO_MAX;
  task_info_data_t task_info_data;
  kern_return_t kr = task_info(task, TASK_EVENTS_INFO, (task_info_t)task_info_data, &task_info_count);
  if (kr != KERN_SUCCESS) {
    return OS_ERR;
  }

  int result = OS_OK;
  if (_total_csr_nanos == 0 || _jvm_context_switches == 0) {
    // First call just set initial values.
    result = OS_ERR;
  }

  long jvm_context_switches = ((task_events_info_t)task_info_data)->csw;

  uint64_t total_csr_nanos;
  if(!now_in_nanos(&total_csr_nanos)) {
    return OS_ERR;
  }
  double delta_in_sec = (double)(total_csr_nanos - _total_csr_nanos) / NANOS_PER_SEC;
  if (delta_in_sec == 0.0) {
    // Avoid division by zero
    return OS_ERR;
  }

  *rate = (jvm_context_switches - _jvm_context_switches) / delta_in_sec;

  _jvm_context_switches = jvm_context_switches;
  _total_csr_nanos = total_csr_nanos;

  return result;
#else
  return FUNCTIONALITY_NOT_IMPLEMENTED;
#endif
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
      FREE_RESOURCE_ARRAY(pids, pid_count);
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
#elif defined(__NetBSD__)
  // KERN_PROC2 lists the processes; KERN_PROC_PATHNAME names each one.  The
  // list is asked for twice, because it can grow between the sizing call and
  // the fetch.
  ResourceMark rm;
  int mib[6] = { CTL_KERN, KERN_PROC2, KERN_PROC_ALL, 0,
                 (int)sizeof(struct kinfo_proc2), 0 };
  size_t len = 0;
  if (sysctl(mib, 6, nullptr, &len, nullptr, 0) != 0 || len == 0) {
    return OS_ERR;
  }

  int count = (int)(len / sizeof(struct kinfo_proc2));
  mib[5] = count;
  struct kinfo_proc2* procs = NEW_RESOURCE_ARRAY(struct kinfo_proc2, count);
  if (sysctl(mib, 6, procs, &len, nullptr, 0) != 0) {
    return OS_ERR;
  }
  count = (int)(len / sizeof(struct kinfo_proc2));

  int process_count = 0;
  SystemProcess* next = nullptr;
  for (int i = 0; i < count; i++) {
    pid_t pid = procs[i].p_pid;
    if (pid == 0) {
      continue;
    }
    char buffer[MAXPATHLEN];
    size_t path_len = sizeof(buffer);
    int name[4] = { CTL_KERN, KERN_PROC_ARGS, (int)pid, KERN_PROC_PATHNAME };
    if (sysctl(name, 4, buffer, &path_len, nullptr, 0) != 0 || path_len == 0) {
      // A process can go away between the listing and this call, and a
      // kernel thread has no path at all.  Report what can be named.
      continue;
    }
    SystemProcess* current = new SystemProcess();
    char* path = NEW_C_HEAP_ARRAY(char, strlen(buffer) + 1, mtInternal);
    strcpy(path, buffer);
    current->set_path(path);
    current->set_pid((int)pid);
    current->set_next(next);
    next = current;
    process_count++;
  }

  *no_of_sys_processes = process_count;
  *system_processes = next;

  return OS_OK;
#endif
  return FUNCTIONALITY_NOT_IMPLEMENTED;
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
      FREE_C_HEAP_ARRAY(cpu_name);
      _cpu_info->set_cpu_name(nullptr);
    }
    if (_cpu_info->cpu_description() != nullptr) {
      const char* cpu_desc = _cpu_info->cpu_description();
      FREE_C_HEAP_ARRAY(cpu_desc);
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
  // NET_RT_IFLIST2 and RTM_IFINFO2 are macOS extensions.  The BSDs carry
  // the same counters in the base NET_RT_IFLIST listing, in struct
  // if_msghdr itself rather than in the larger if_msghdr2 that follows it
  // there.
#ifdef __APPLE__
  const int iflist_op = NET_RT_IFLIST2;
  const int ifinfo_type = RTM_IFINFO2;
#else
  const int iflist_op = NET_RT_IFLIST;
  const int ifinfo_type = RTM_IFINFO;
#endif
  int mib[] = {CTL_NET, PF_ROUTE, /* protocol number */ 0, /* address family */ 0, iflist_op, /* NET_RT_FLAGS mask*/ 0};
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

    if (msghdr->ifm_type != ifinfo_type) {
      continue;
    }

#ifdef __APPLE__
    if_msghdr2* msghdr2 = reinterpret_cast<if_msghdr2*>(msghdr);
    sockaddr_dl* sockaddr = reinterpret_cast<sockaddr_dl*>(msghdr2 + 1);
#else
    sockaddr_dl* sockaddr = reinterpret_cast<sockaddr_dl*>(msghdr + 1);
#endif

    // The interface name is not necessarily NUL-terminated
    char name_buf[128];
    size_t name_len = MIN2(sizeof(name_buf) - 1, static_cast<size_t>(sockaddr->sdl_nlen));
    strncpy(name_buf, sockaddr->sdl_data, name_len);
    name_buf[name_len] = '\0';

#ifdef __APPLE__
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
