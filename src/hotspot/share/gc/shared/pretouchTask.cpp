/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/pretouchTask.hpp"
#include "runtime/atomic.hpp"
#include "runtime/globals.hpp"
#include "runtime/os.hpp"

PretouchTask::PretouchTask(const char* task_name, char* start_address, char* end_address, size_t page_size) :
    AbstractGangTask(task_name),
    _cur_addr(start_address),
    _start_addr(start_address),
    _end_addr(end_address),
    _page_size(0) {
#ifdef LINUX
  _page_size = UseTransparentHugePages ? (size_t)os::vm_page_size(): page_size;
#else
  _page_size = page_size;
#endif
}

size_t PretouchTask::chunk_size() {
  return PreTouchParallelChunkSize;
}

void PretouchTask::work(uint worker_id) {
  size_t const actual_chunk_size = MAX2(chunk_size(), _page_size);

  while (true) {
    char* touch_addr = Atomic::fetch_and_add(&_cur_addr, actual_chunk_size);
    if (touch_addr < _start_addr || touch_addr >= _end_addr) {
      break;
    }

    char* end_addr = touch_addr + MIN2(actual_chunk_size, pointer_delta(_end_addr, touch_addr, sizeof(char)));

    os::pretouch_memory(touch_addr, end_addr, _page_size);
  }
}

void PretouchTask::pretouch(const char* task_name, char* start_address, char* end_address,
                            size_t page_size, WorkGang* pretouch_gang) {
  PretouchTask task(task_name, start_address, end_address, page_size);
  size_t total_bytes = pointer_delta(end_address, start_address, sizeof(char));

  if (pretouch_gang != NULL) {
    size_t num_chunks = MAX2((size_t)1, total_bytes / MAX2(PretouchTask::chunk_size(), page_size));

    uint num_workers = MIN2((uint)num_chunks, pretouch_gang->total_workers());
    log_debug(gc, heap)("Running %s with %u workers for " SIZE_FORMAT " work units pre-touching " SIZE_FORMAT "B.",
                        task.name(), num_workers, num_chunks, total_bytes);

    pretouch_gang->run_task(&task, num_workers);
  } else {
    log_debug(gc, heap)("Running %s pre-touching " SIZE_FORMAT "B.",
                        task.name(), total_bytes);
    task.work(0);
  }
}

