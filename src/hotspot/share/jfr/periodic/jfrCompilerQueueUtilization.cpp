/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "compiler/compileBroker.hpp"
#include "jfr/jfrEvents.hpp"
#include "jfr/periodic/jfrCompilerQueueUtilization.hpp"

struct CompilerQueueEntry {
  CompileQueue* compilerQueue;
  u8 compiler;
  uint64_t added;
  uint64_t removed;
};

// If current counters are less than previous we assume the interface has been reset
// If no bytes have been either sent or received, we'll also skip the event
static uint64_t rate_per_second(uint64_t current, uint64_t old, const JfrTickspan& interval) {
  assert(interval.value() > 0, "invariant");
  if (current <= old) {
    return 0;
  }
  return ((current - old) * NANOSECS_PER_SEC) / interval.nanoseconds();
}

void JfrCompilerQueueUtilization::send_events() {
  static CompilerQueueEntry compilerQueueEntries[2] = {
    {CompileBroker::c1_compile_queue(), 1, 0, 0},
    {CompileBroker::c2_compile_queue(), 2, 0, 0}};

  const JfrTicks cur_time = JfrTicks::now();
  static JfrTicks last_sample_instant;
  const JfrTickspan interval = cur_time - last_sample_instant;
  for(int i = 0; i < 2; i ++)
  {
    CompilerQueueEntry* entry = &compilerQueueEntries[i];
    if (entry->compilerQueue != NULL)
    {
      const uint64_t current_added = entry->compilerQueue->get_total_added();
      const uint64_t current_removed = entry->compilerQueue->get_total_removed();
      const uint64_t ingress = rate_per_second(current_added, entry->added, interval);
      const uint64_t egress = rate_per_second(current_removed, entry->removed, interval);

      EventCompilerQueueUtilization event;
      event.set_compiler(entry->compiler);
      event.set_compilerThreadCount((i == 0) ? CompileBroker::get_c1_thread_count() : CompileBroker::get_c2_thread_count());
      event.set_ingress(ingress);
      event.set_egress(egress);
      event.set_size(entry->compilerQueue->size());
      event.set_peak(entry->compilerQueue->get_peak_size());
      event.set_added(current_added - entry->added);
      event.set_removed(current_removed - entry->removed);
      event.set_totalAdded(current_added);
      event.set_totalRemoved(current_removed);
      event.commit();

      entry->added = current_added;
      entry->removed = current_removed;
    }

    last_sample_instant = cur_time;
  }
}