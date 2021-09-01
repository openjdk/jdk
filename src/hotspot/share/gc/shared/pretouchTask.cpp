/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/atomic.hpp"
#include "runtime/globals.hpp"
#include "runtime/os.hpp"

static size_t adjusted_page_size(size_t page_size) {
  // When using THP we need to always touch using small pages as the OS will
  // initially always use small pages.
  if (LINUX_ONLY(UseTransparentHugePages ||) false) {
    return os::vm_page_size();
  } else {
    return page_size;
  }
}

static size_t adjusted_chunk_size(size_t page_size) {
  // Chunk size should be at least page size to avoid having multiple
  // threads touching a single page.
  return MAX2(PreTouchParallelChunkSize, page_size);
}

BasicTouchTask::BasicTouchTask(const char* name,
                               void* start,
                               void* end,
                               size_t page_size) :
  AbstractGangTask(name),
  _cur(reinterpret_cast<char*>(start)),
  _end(end),
  _page_size(adjusted_page_size(page_size)),
  _chunk_size(adjusted_chunk_size(_page_size))
{
  assert(start <= end,
         "Invalid range: " PTR_FORMAT " -> " PTR_FORMAT, p2i(start), p2i(end));
}

void BasicTouchTask::work(uint worker_id) {
  while (true) {
    char* cur_start = Atomic::load(&_cur);
    char* cur_end = cur_start + MIN2(_chunk_size, pointer_delta(_end, cur_start, 1));
    if (cur_start >= cur_end) {
      break;
    } else if (cur_start == Atomic::cmpxchg(&_cur, cur_start, cur_end)) {
      do_touch(cur_start, cur_end, _page_size);
    } // Else chunk claim failed, so try again.
  }
}

void BasicTouchTask::touch_impl(WorkGang* gang) {
  size_t total_bytes = pointer_delta(_end, Atomic::load(&_cur), 1);
  if ((gang == nullptr) || (total_bytes <= _chunk_size)) {
    log_debug(gc, heap)("Running %s pre-touching %zuB", name(), total_bytes);
    work(0);
  } else {
    assert(total_bytes > 0, "invariant");
    size_t num_chunks = ((total_bytes - 1) / _chunk_size) + 1;
    uint num_workers = (uint)MIN2(num_chunks, (size_t)gang->total_workers());
    log_debug(gc, heap)("Running %s with %u workers for %zu chunks touching %zuB",
                        name(), num_workers, num_chunks, total_bytes);
    gang->run_task(this, num_workers);
  }
}

PretouchTask::PretouchTask(const char* task_name,
                           void* start,
                           void* end,
                           size_t page_size) :
  BasicTouchTask(task_name, start, end, page_size)
{}

void PretouchTask::do_touch(void* start, void* end, size_t page_size) {
  os::pretouch_memory(start, end, page_size);
}

void PretouchTask::pretouch(const char* task_name,
                            void* start,
                            void* end,
                            size_t page_size,
                            WorkGang* pretouch_gang) {
  PretouchTask task{task_name, start, end, page_size};
  task.touch_impl(pretouch_gang);
}

TouchTask::TouchTask(const char* task_name,
                     void* start,
                     void* end,
                     size_t page_size) :
  BasicTouchTask(task_name, start, end, page_size)
{}

void TouchTask::do_touch(void* start, void* end, size_t page_size) {
  os::touch_memory(start, end, page_size);
}

void TouchTask::touch(const char* task_name,
                      void* start,
                      void* end,
                      size_t page_size,
                      WorkGang* touch_gang) {
  TouchTask task{task_name, start, end, page_size};
  task.touch_impl(touch_gang);
}
