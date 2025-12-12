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

#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/gcTrace.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/universe.hpp"
#include "runtime/os.hpp"
#include "services/cpuTimeUsage.hpp"
#include "utilities/globalDefinitions.hpp"

void GCTraceTimeLoggerImpl::log_start(Ticks start) {
  _start = start;

  LogStream out(_out_start);

  out.print("%s", _title);
  if (_gc_cause != GCCause::_no_gc) {
    out.print(" (%s)", GCCause::to_string(_gc_cause));
  }
  out.cr();

  if (_log_heap_usage) {
    _heap_usage_before = Universe::heap()->used();
  }
}

void GCTraceTimeLoggerImpl::log_end(Ticks end) {
  double duration_in_ms = TimeHelper::counter_to_millis(end.value() - _start.value());

  LogStream out(_out_end);

  out.print("%s", _title);

  if (_gc_cause != GCCause::_no_gc) {
    out.print(" (%s)", GCCause::to_string(_gc_cause));
  }

  if (_heap_usage_before != SIZE_MAX) {
    CollectedHeap* heap = Universe::heap();
    size_t used_before_m = _heap_usage_before / M;
    size_t used_m = heap->used() / M;
    size_t capacity_m = heap->capacity() / M;
    out.print(" %zuM->%zuM(%zuM)", used_before_m, used_m, capacity_m);
  }

  out.print_cr(" %.3fms", duration_in_ms);
}

GCTraceCPUTime::GCTraceCPUTime(GCTracer* tracer) :
  _active(log_is_enabled(Info, gc, cpu) ||
          (tracer != nullptr && tracer->should_report_cpu_time_event())),
  _starting_user_time(0),
  _starting_system_time(0),
  _starting_real_time(os::elapsedTime()),
  _tracer(tracer)
{
  if (_active) {
    CPUTime_t cpu_time_vm = CPUTimeUsage::GC::detailed_gc_operation_vm_thread();
    CPUTime_t cpu_time_gc = CPUTimeUsage::GC::detailed_gc_threads();
    CPUTime_t cpu_time_stringdedup = CPUTimeUsage::GC::detailed_stringdedup();
    _starting_user_time = cpu_time_vm.user + cpu_time_gc.user + cpu_time_stringdedup.user;
    _starting_system_time = cpu_time_vm.system + cpu_time_gc.system + cpu_time_stringdedup.system;
    if (CPUTimeUsage::Error::has_error()) {
      log_warning(gc, cpu)("TraceCPUTime: CPUTimeUsage may contain invalid results");
      _active = false;
    }
  }
}

GCTraceCPUTime::~GCTraceCPUTime() {
  if (_active) {
    CPUTime_t cpu_time_vm = CPUTimeUsage::GC::detailed_gc_operation_vm_thread();
    CPUTime_t cpu_time_gc = CPUTimeUsage::GC::detailed_gc_threads();
    CPUTime_t cpu_time_stringdedup = CPUTimeUsage::GC::detailed_stringdedup();

    double real_time = os::elapsedTime();
    jlong user_time = cpu_time_vm.user + cpu_time_gc.user + cpu_time_stringdedup.user;
    jlong system_time = cpu_time_vm.system + cpu_time_gc.system + cpu_time_stringdedup.system;

    if (!CPUTimeUsage::Error::has_error()) {
      user_time -= _starting_user_time;
      system_time -= _starting_system_time;
      real_time -= _starting_real_time;
      double user_time_seconds = 1.0 * user_time / NANOSECS_PER_SEC;
      double system_time_seconds = 1.0 * system_time / NANOSECS_PER_SEC;
      log_info(gc, cpu)("User=%3.2fs Sys=%3.2fs Real=%3.2fs", user_time_seconds, system_time_seconds, real_time);
      if (_tracer != nullptr) {
        _tracer->report_cpu_time_event(user_time, system_time, real_time);
      }
    } else {
      log_warning(gc, cpu)("TraceCPUTime: CPUTimeUsage may contain invalid results");
    }
  }
}
