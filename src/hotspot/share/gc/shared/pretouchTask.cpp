/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/pretouchTask.hpp"
#include "logging/log.hpp"
#include "runtime/atomic.hpp"
#include "runtime/globals.hpp"
#include "runtime/os.hpp"
#include "utilities/align.hpp"

PretouchTask::PretouchTask(const char* task_name,
                           char* start_address,
                           char* end_address,
                           size_t page_size,
                           size_t chunk_size) :
    WorkerTask(task_name),
    _cur_addr(start_address),
    _end_addr(end_address),
    _page_size(page_size),
    _chunk_size(chunk_size) {

  assert(chunk_size >= page_size,
         "Chunk size " SIZE_FORMAT " is smaller than page size " SIZE_FORMAT,
         chunk_size, page_size);
}

size_t PretouchTask::chunk_size() {
  return PreTouchParallelChunkSize;
}

void PretouchTask::work(uint worker_id) {
  while (true) {
    char* cur_start = Atomic::load(&_cur_addr);
    char* cur_end = cur_start + MIN2(_chunk_size, pointer_delta(_end_addr, cur_start, 1));
    if (cur_start >= cur_end) {
      break;
    } else if (cur_start == Atomic::cmpxchg(&_cur_addr, cur_start, cur_end)) {
      os::pretouch_memory(cur_start, cur_end, _page_size);
    } // Else attempt to claim chunk failed, so try again.
  }
}

void PretouchTask::pretouch(const char* task_name, char* start_address, char* end_address,
                            size_t page_size, WorkerThreads* pretouch_workers) {
  // Page-align the chunk size, so if start_address is also page-aligned (as
  // is common) then there won't be any pages shared by multiple chunks.
  size_t chunk_size = align_down_bounded(PretouchTask::chunk_size(), page_size);
  PretouchTask task(task_name, start_address, end_address, page_size, chunk_size);
  size_t total_bytes = pointer_delta(end_address, start_address, sizeof(char));

  if (total_bytes == 0) {
    return;
  }

  if (pretouch_workers != nullptr) {
    size_t num_chunks = ((total_bytes - 1) / chunk_size) + 1;

    uint num_workers = (uint)MIN2(num_chunks, (size_t)pretouch_workers->max_workers());
    log_debug(gc, heap)("Running %s with %u workers for " SIZE_FORMAT " work units pre-touching " SIZE_FORMAT "B.",
                        task.name(), num_workers, num_chunks, total_bytes);

    pretouch_workers->run_task(&task, num_workers);
  } else {
    log_debug(gc, heap)("Running %s pre-touching " SIZE_FORMAT "B.",
                        task.name(), total_bytes);
    task.work(0);
  }
}
