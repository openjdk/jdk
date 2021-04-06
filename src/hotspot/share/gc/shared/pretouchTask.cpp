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
#include "runtime/nonJavaThread.hpp"
#include "utilities/ticks.hpp"

PretouchTaskCoordinator* PretouchTaskCoordinator::_task_coordinator = NULL;
uint PretouchTaskCoordinator::_object_creation = 0;

PretouchTask::PretouchTask(const char* task_name,
                           char* start_address,
                           char* end_address,
                           size_t page_size,
                           size_t chunk_size) :
    AbstractGangTask(task_name),
    _cur_addr(start_address),
    _start_addr(start_address),
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
    char* touch_addr = Atomic::fetch_and_add(&_cur_addr, _chunk_size);
    if (touch_addr < _start_addr || touch_addr >= _end_addr) {
      break;
    }

    char* end_addr = touch_addr + MIN2(_chunk_size, pointer_delta(_end_addr, touch_addr, sizeof(char)));

    os::pretouch_memory(touch_addr, end_addr, _page_size);
  }
}


void PretouchTask::setup_chunk_size_and_page_size(size_t& chunk_size, size_t& page_size)
{
  // Chunk size should be at least (unmodified) page size as using multiple threads
  // pretouch on a single page can decrease performance.
  chunk_size = MAX2(PretouchTask::chunk_size(), page_size);
#ifdef LINUX
  // When using THP we need to always pre-touch using small pages as the OS will
  // initially always use small pages.
  page_size = UseTransparentHugePages ? (size_t)os::vm_page_size() : page_size;
#endif
}

void PretouchTask::pretouch(const char* task_name, char* start_address, char* end_address,
                            size_t page_size, WorkGang* pretouch_gang) {
  size_t chunk_size = 0;
  setup_chunk_size_and_page_size(chunk_size, page_size);

  PretouchTask task(task_name, start_address, end_address, page_size, chunk_size);
  size_t total_bytes = pointer_delta(end_address, start_address, sizeof(char));

  if (total_bytes == 0) {
    return;
  }

  if (pretouch_gang != NULL) {
    size_t num_chunks = (total_bytes + chunk_size - 1) / chunk_size;

    uint num_workers = (uint)MIN2(num_chunks, (size_t)pretouch_gang->total_workers());
    log_debug(gc, heap)("Running %s with %u workers for " SIZE_FORMAT " work units pre-touching " SIZE_FORMAT "B.",
                        task.name(), num_workers, num_chunks, total_bytes);
    pretouch_gang->run_task(&task, num_workers);
  } else {

    Ticks start = Ticks::now();
    if (UseMultithreadedPretouchForOldGen) {
      PretouchTaskCoordinator::coordinate_and_execute(task_name, start_address, end_address, page_size);
    } else {
      // Test purpose following lines are commented.
      //log_debug(gc, heap)("Running %s pre-touching " SIZE_FORMAT "B.",
      //                    task.name(), total_bytes);
      task.work(0);
    }
    Ticks end = Ticks::now();
    log_debug(gc, heap)("Running %s pre-touching " SIZE_FORMAT "B %.4lfms",
                         task.name(), total_bytes, (double)(end-start).milliseconds());

  }
}

// Called to initialize _task_coordinator
void PretouchTaskCoordinator::createObject() {
  volatile uint my_id = Atomic::fetch_and_add(&_object_creation, 1u);
  if (my_id == 0) {
    // First thread creates the object.
    _task_coordinator = new PretouchTaskCoordinator("Pretouch during oldgen expansion", NULL, NULL);
  } else {
    // Other threads will wait until _task_coordinator object is initialized.
    PretouchTaskCoordinator *is_initialized = NULL;
    do {
      SpinPause();
      is_initialized = Atomic::load_acquire(&_task_coordinator);
    } while(!is_initialized);
  }
  my_id = Atomic::sub(&_object_creation, 1u);
}

PretouchTaskCoordinator::PretouchTaskCoordinator(const char* task_name, char* start_address,
                                                 char* end_address):
    _n_threads(0),
    _task_status(Done),
    _pretouch_task(NULL){
  ;
}


void PretouchTaskCoordinator::coordinate_and_execute(const char* task_name, char* start_address,
                                                    char* end_address, size_t page_size) {

  size_t total_bytes = pointer_delta(end_address, start_address, sizeof(char));

  if (total_bytes == 0) {
    return;
  }

  PretouchTaskCoordinator *task_coordinator = get_task_coordinator();

  size_t chunk_size = 0;
  PretouchTask::setup_chunk_size_and_page_size(chunk_size, page_size);

  size_t num_chunks = (total_bytes + chunk_size - 1) / chunk_size;

  PretouchTask task(task_name, start_address, end_address, page_size, chunk_size);
  task_coordinator->release_set_pretouch_task(&task);

  // Test purpose following lines are commented.
  //log_debug(gc, heap)("Running %s with " SIZE_FORMAT " work units pre-touching " SIZE_FORMAT "B.",
  //                    task->name(), num_chunks, total_bytes);

  // Mark Pretouch task ready here to let other threads waiting to expand oldgen will join
  // pretouch task.
  task_coordinator->release_set_task_ready();

  // Execute the task
  task_coordinator->task_execute();

  // Wait for other threads to finish.
  do {
    SpinPause();
  } while (task_coordinator->wait_for_all_threads_acquire()) ;

}


void PretouchTaskCoordinator::task_execute() {

  uint cur_thread_id = Atomic::add(&_n_threads, 1u);

  PretouchTask *task = const_cast<PretouchTask *>(pretouch_task_acquire());
  task->work(static_cast<AbstractGangWorker*>(Thread::current())->id());

  // First thread to exit marks task completed.
  if (! is_task_done_acquire()) {
    release_set_task_done();
  }

  cur_thread_id = Atomic::sub(&_n_threads, 1u);
}

void PretouchTaskCoordinator::worker_wait_for_task(){

  while (! is_task_done_acquire()) {
    if (is_task_ready_acquire()) {
      task_execute();
      break;
    }
    SpinPause();
  }
}

