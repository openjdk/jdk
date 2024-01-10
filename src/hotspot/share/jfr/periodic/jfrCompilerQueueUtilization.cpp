/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

enum {
    c1_compiler_queue_id = 1,
    c2_compiler_queue_id = 2,
    num_compiler_queues = 2
};

typedef int (*GET_COMPILER_THREAD_COUNT)();

struct CompilerQueueEntry {
  CompileQueue* compilerQueue;
  u8 compiler_queue_id;
  GET_COMPILER_THREAD_COUNT get_compiler_thread_count;
  uint64_t added;
  uint64_t removed;
};

// If current counters are less than previous, we assume the interface has been reset
// If no bytes have been either sent or received, we'll also skip the event
static uint64_t rate_per_second(uint64_t current, uint64_t old, const JfrTickspan& interval) {
  assert(interval.value() > 0, "invariant");
  if (current <= old) {
    return 0;
  }
  return ((current - old) * NANOSECS_PER_SEC) / interval.nanoseconds();
}

void JfrCompilerQueueUtilization::send_events() {
  static CompilerQueueEntry compilerQueueEntries[num_compiler_queues] = {
    {CompileBroker::c1_compile_queue(), c1_compiler_queue_id, &CompileBroker::get_c1_thread_count, 0, 0},
    {CompileBroker::c2_compile_queue(), c2_compiler_queue_id, &CompileBroker::get_c2_thread_count, 0, 0}};

  const JfrTicks cur_time = JfrTicks::now();
  static JfrTicks last_sample_instant;
  const JfrTickspan interval = cur_time - last_sample_instant;
  for (int i = 0; i < num_compiler_queues; i ++) {
    CompilerQueueEntry* entry = &compilerQueueEntries[i];
    if (entry->compilerQueue != nullptr) {
      const uint64_t current_added = entry->compilerQueue->get_total_added();
      const uint64_t current_removed = entry->compilerQueue->get_total_removed();
      const uint64_t addedRate = rate_per_second(current_added, entry->added, interval);
      const uint64_t removedRate = rate_per_second(current_removed, entry->removed, interval);

      EventCompilerQueueUtilization event;
      event.set_compiler(entry->compiler_queue_id);
      event.set_addedRate(addedRate);
      event.set_removedRate(removedRate);
      event.set_queueSize(entry->compilerQueue->size());
      event.set_peakQueueSize(entry->compilerQueue->get_peak_size());
      event.set_addedCount(current_added - entry->added);
      event.set_removedCount(current_removed - entry->removed);
      event.set_totalAddedCount(current_added);
      event.set_totalRemovedCount(current_removed);
      event.set_compilerThreadCount(entry->get_compiler_thread_count());
      event.commit();

      entry->added = current_added;
      entry->removed = current_removed;
    }

    last_sample_instant = cur_time;
  }
}