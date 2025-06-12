/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/vtimeScope.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "logging/log.hpp"
#include "memory/universe.hpp"
#include "runtime/cpuTimeCounters.hpp"
#include "runtime/os.hpp"

inline VTimeScope::VTimeScope(VMThread *thread)
    : _start(0), _enabled(os::is_thread_cpu_time_supported()),
      _gcLogging(log_is_enabled(Info, gc) || log_is_enabled(Info, gc, cpu)),
      _thread((Thread*)thread) {
  if (_enabled && _gcLogging) {
    _start = os::thread_cpu_time(_thread);
  }
}

inline VTimeScope::~VTimeScope() {
  if (_enabled) {
    jlong end = _gcLogging || UsePerfData ? os::thread_cpu_time(_thread) : 0;

    if (_gcLogging) {
      jlong duration = end > _start ? end - _start : 0;
      Universe::heap()->add_vm_vtime(duration);
    }

    if (UsePerfData) {
      CPUTimeCounters::get_instance()->update_counter(CPUTimeGroups::CPUTimeType::vm, end);
    }
  }
}