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

#include "gc/shared/vmThreadCpuTimeScope.hpp"

#include "gc/shared/collectedHeap.inline.hpp"
#include "memory/universe.hpp"
#include "runtime/cpuTimeCounters.hpp"
#include "runtime/os.hpp"
#include "runtime/vmThread.hpp"

inline VMThreadCPUTimeScope::VMThreadCPUTimeScope(VMThread* thread, bool is_gc_operation)
  : _start(0),
    _enabled(os::is_thread_cpu_time_supported()),
    _is_gc_operation(is_gc_operation),
    _thread(thread) {
  if (_is_gc_operation && _enabled) {
    _start = os::thread_cpu_time(_thread);
  }
}

inline VMThreadCPUTimeScope::~VMThreadCPUTimeScope() {
  if (!_enabled) {
    return;
  }

  jlong end = (_is_gc_operation || UsePerfData) ? os::thread_cpu_time(_thread) : 0;

  if (_is_gc_operation) {
    jlong duration = end > _start ? end - _start : 0;
    Universe::heap()->add_vmthread_cpu_time(duration);
  }

  if (UsePerfData) {
    CPUTimeCounters::update_counter(CPUTimeGroups::CPUTimeType::vm, end);
  }
}
