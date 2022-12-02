/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "services/memJfrReporter.hpp"
#include "services/memReporter.hpp"
#include "services/memTracker.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ticks.hpp"

Ticks MemJFRBaseline::_baseline_timestamp;
MemBaseline MemJFRBaseline::_baseline;

MemBaseline& MemJFRBaseline::getBaseline() {
  Tickspan since_baselined = Ticks::now() - _baseline_timestamp;
  if (since_baselined.milliseconds() > BaselineAgeThreshold ||
      _baseline.baseline_type() == MemBaseline::Not_baselined) {
    // Summary only baseline.
    _baseline.baseline(true);
    _baseline_timestamp.stamp();
  }

  return _baseline;
}

Ticks MemJFRBaseline::getTimestamp() {
  return _baseline_timestamp;
}

void MemJFRReporter::sendTotalEvent() {
  if (!MemTracker::enabled()) {
    return;
  }

  MemBaseline& usage = MemJFRBaseline::getBaseline();
  Ticks timestamp = MemJFRBaseline::getTimestamp();

  const size_t malloced_memory = usage.malloc_memory_snapshot()->total();
  const size_t reserved_memory = usage.virtual_memory_snapshot()->total_reserved();
  const size_t committed_memory = usage.virtual_memory_snapshot()->total_committed();

  const size_t reserved = malloced_memory + reserved_memory;
  const size_t committed = malloced_memory + committed_memory;

  EventNativeMemoryUsageTotal event;
  event.set_starttime(timestamp);
  event.set_reserved(reserved);
  event.set_committed(committed);
  event.commit();
}

void MemJFRReporter::sendTypeEvent(const Ticks& starttime, const char* type, size_t reserved, size_t committed) {
  EventNativeMemoryUsage event;
  event.set_starttime(starttime);
  event.set_type(type);
  event.set_reserved(reserved);
  event.set_committed(committed);
  event.commit();
}

void MemJFRReporter::sendTypeEvents() {
  if (!MemTracker::enabled()) {
    return;
  }

  MemBaseline& usage = MemJFRBaseline::getBaseline();
  Ticks timestamp = MemJFRBaseline::getTimestamp();

  for (int index = 0; index < mt_number_of_types; index ++) {
    MEMFLAGS flag = NMTUtil::index_to_flag(index);
    MallocMemory* malloc_memory = usage.malloc_memory(flag);
    VirtualMemory* virtual_memory = usage.virtual_memory(flag);

    size_t reserved = MemReporterBase::reserved_total(malloc_memory, virtual_memory);
    size_t committed = MemReporterBase::committed_total(malloc_memory, virtual_memory);

    // Some special cases to get accounting correct
    if (flag == mtThread) {
      // Count thread's native stack in "Thread" category
      if (ThreadStackTracker::track_as_vm()) {
        VirtualMemory* thread_stack_usage = usage.virtual_memory(mtThreadStack);
        reserved += thread_stack_usage->reserved();
        committed += thread_stack_usage->committed();
      } else {
        MallocMemory* thread_stack_usage = usage.malloc_memory(mtThreadStack);
        reserved += thread_stack_usage->malloc_size();
        committed += thread_stack_usage->malloc_size();
      }
    } else if (flag == mtNMT) {
      // Count malloc headers in "NMT" category
      reserved += usage.malloc_memory_snapshot()->malloc_overhead();
      committed += usage.malloc_memory_snapshot()->malloc_overhead();
    }
    sendTypeEvent(timestamp, NMTUtil::flag_to_name(flag), reserved, committed);
  }
}
