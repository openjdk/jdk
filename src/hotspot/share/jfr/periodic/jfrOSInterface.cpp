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
#include "jfr/jfrEvents.hpp"
#include "jfr/periodic/jfrNetworkUtilization.hpp"
#include "jfr/periodic/jfrOSInterface.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/os.hpp"
#include "runtime/os_perf.hpp"
#include "utilities/ostream.hpp"

#include <stdlib.h> // for environment variables
#ifdef __APPLE__
#include <crt_externs.h>
#define environ (*_NSGetEnviron())
#endif

#ifndef environ
extern char** environ;
#endif

static JfrOSInterface* _instance = NULL;

JfrOSInterface& JfrOSInterface::instance() {
  return *_instance;
}

JfrOSInterface* JfrOSInterface::create() {
  assert(_instance == NULL, "invariant");
  _instance = new JfrOSInterface();
  return _instance;
}

void JfrOSInterface::destroy() {
  JfrNetworkUtilization::destroy();
  if (_instance != NULL) {
    delete _instance;
    _instance = NULL;
  }
}

class JfrOSInterface::JfrOSInterfaceImpl : public JfrCHeapObj {
  friend class JfrOSInterface;
 private:
  CPUInformationInterface* _cpu_info_interface;
  CPUPerformanceInterface* _cpu_perf_interface;
  SystemProcessInterface*  _system_process_interface;
  NetworkPerformanceInterface* _network_performance_interface;

  // stub helper
  void functionality_not_implemented(char** str) const;

  JfrOSInterfaceImpl();
  bool initialize();
  ~JfrOSInterfaceImpl();

  // cpu info
  int cpu_information(CPUInformation& cpu_info);
  int cpu_load(int which_logical_cpu, double* cpu_load);
  int context_switch_rate(double* rate);
  int cpu_load_total_process(double* cpu_load);
  int cpu_loads_process(double* pjvmUserLoad, double* pjvmKernelLoad, double* psystemTotal);

  // os information
  int os_version(char** os_version) const;

  // environment information
  void generate_environment_variables_events();

   // system processes information
  int system_processes(SystemProcess** system_processes, int* no_of_sys_processes);

  int network_utilization(NetworkInterface** network_interfaces) const;
};

JfrOSInterface::JfrOSInterfaceImpl::JfrOSInterfaceImpl() : _cpu_info_interface(NULL),
                                                           _cpu_perf_interface(NULL),
                                                           _system_process_interface(NULL) {}

bool JfrOSInterface::JfrOSInterfaceImpl::initialize() {
  _cpu_info_interface = new CPUInformationInterface();
  if (!(_cpu_info_interface != NULL && _cpu_info_interface->initialize())) {
    return false;
  }
  _cpu_perf_interface = new CPUPerformanceInterface();
  if (!(_cpu_perf_interface != NULL && _cpu_perf_interface->initialize())) {
    return false;
  }
  _system_process_interface = new SystemProcessInterface();
  if (!(_system_process_interface != NULL && _system_process_interface->initialize())) {
    return false;
  }
  _network_performance_interface = new NetworkPerformanceInterface();
  return _network_performance_interface != NULL && _network_performance_interface->initialize();
}

JfrOSInterface::JfrOSInterfaceImpl::~JfrOSInterfaceImpl(void) {
  if (_cpu_info_interface != NULL) {
    delete _cpu_info_interface;
    _cpu_info_interface = NULL;
  }
  if (_cpu_perf_interface != NULL) {
    delete _cpu_perf_interface;
    _cpu_perf_interface = NULL;
  }
  if (_system_process_interface != NULL) {
    delete _system_process_interface;
    _system_process_interface = NULL;
  }
  if (_network_performance_interface != NULL) {
    delete _network_performance_interface;
    _network_performance_interface = NULL;
  }
}

int JfrOSInterface::JfrOSInterfaceImpl::cpu_load(int which_logical_cpu, double* cpu_load) {
  return _cpu_perf_interface->cpu_load(which_logical_cpu, cpu_load);
}

int JfrOSInterface::JfrOSInterfaceImpl::context_switch_rate(double* rate) {
  return _cpu_perf_interface->context_switch_rate(rate);
}

int JfrOSInterface::JfrOSInterfaceImpl::cpu_load_total_process(double* cpu_load) {
  return _cpu_perf_interface->cpu_load_total_process(cpu_load);
}

int JfrOSInterface::JfrOSInterfaceImpl::cpu_loads_process(double* pjvmUserLoad,
                                                          double* pjvmKernelLoad,
                                                          double* psystemTotal) {
  return _cpu_perf_interface->cpu_loads_process(pjvmUserLoad, pjvmKernelLoad, psystemTotal);
}

int JfrOSInterface::JfrOSInterfaceImpl::cpu_information(CPUInformation& cpu_info) {
  return _cpu_info_interface->cpu_information(cpu_info);
}

int JfrOSInterface::JfrOSInterfaceImpl::system_processes(SystemProcess** system_processes, int* no_of_sys_processes) {
  assert(system_processes != NULL, "system_processes pointer is NULL!");
  assert(no_of_sys_processes != NULL, "no_of_sys_processes pointer is NULL!");
  return _system_process_interface->system_processes(system_processes, no_of_sys_processes);
}

int JfrOSInterface::JfrOSInterfaceImpl::network_utilization(NetworkInterface** network_interfaces) const {
  return _network_performance_interface->network_utilization(network_interfaces);
}

// assigned char* is RESOURCE_HEAP_ALLOCATED
// caller need to ensure proper ResourceMark placement.
int JfrOSInterface::JfrOSInterfaceImpl::os_version(char** os_version) const {
  assert(os_version != NULL, "os_version pointer is NULL!");
  stringStream os_ver_info;
  os::print_os_info_brief(&os_ver_info);
  *os_version = os_ver_info.as_string();
  return OS_OK;
}

void JfrOSInterface::JfrOSInterfaceImpl::functionality_not_implemented(char** str) const {
  assert(str != NULL, "address to string is NULL!");
  const char* not_impl = "Functionality_not_implemented";
  const size_t not_impl_len = strlen(not_impl);
  *str = NEW_C_HEAP_ARRAY(char, not_impl_len+1, mtTracing);
  strncpy(*str, not_impl, not_impl_len);
  (*str)[not_impl_len] = '\0';
}

JfrOSInterface::JfrOSInterface() {
  _impl = NULL;
}

bool JfrOSInterface::initialize() {
  _impl = new JfrOSInterface::JfrOSInterfaceImpl();
  return _impl != NULL && _impl->initialize();
}

JfrOSInterface::~JfrOSInterface() {
  if (_impl != NULL) {
    delete _impl;
    _impl = NULL;
  }
}

int JfrOSInterface::cpu_information(CPUInformation& cpu_info) {
  return instance()._impl->cpu_information(cpu_info);
}

int JfrOSInterface::cpu_load(int which_logical_cpu, double* cpu_load) {
  return instance()._impl->cpu_load(which_logical_cpu, cpu_load);
}

int JfrOSInterface::context_switch_rate(double* rate) {
  return instance()._impl->context_switch_rate(rate);
}

int JfrOSInterface::cpu_load_total_process(double* cpu_load) {
  return instance()._impl->cpu_load_total_process(cpu_load);
}

int JfrOSInterface::cpu_loads_process(double* jvm_user_load, double* jvm_kernel_load, double* system_total_load){
  return instance()._impl->cpu_loads_process(jvm_user_load, jvm_kernel_load, system_total_load);
}

int JfrOSInterface::os_version(char** os_version) {
  return instance()._impl->os_version(os_version);
}

int JfrOSInterface::generate_initial_environment_variable_events() {
  if (environ == NULL) {
    return OS_ERR;
  }

  if (EventInitialEnvironmentVariable::is_enabled()) {
    // One time stamp for all events, so they can be grouped together
    JfrTicks time_stamp = JfrTicks::now();
    for (char** p = environ; *p != NULL; p++) {
      char* variable = *p;
      char* equal_sign = strchr(variable, '=');
      if (equal_sign != NULL) {
        // Extract key/value
        ResourceMark rm;
        ptrdiff_t key_length = equal_sign - variable;
        char* key = NEW_RESOURCE_ARRAY(char, key_length + 1);
        char* value = equal_sign + 1;
        strncpy(key, variable, key_length);
        key[key_length] = '\0';
        EventInitialEnvironmentVariable event(UNTIMED);
        event.set_endtime(time_stamp);
        event.set_key(key);
        event.set_value(value);
        event.commit();
      }
    }
  }
  return OS_OK;
}

int JfrOSInterface::system_processes(SystemProcess** sys_processes, int* no_of_sys_processes) {
  return instance()._impl->system_processes(sys_processes, no_of_sys_processes);
}

int JfrOSInterface::network_utilization(NetworkInterface** network_interfaces) {
  return instance()._impl->network_utilization(network_interfaces);
}
