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
#include "runtime/threadCritical.hpp"
#include "services/memTracker.hpp"
#include "services/memTrackWorker.hpp"
#include "utilities/decoder.hpp"
#include "utilities/vmError.hpp"


void GenerationData::reset() {
  _number_of_classes = 0;
  while (_recorder_list != NULL) {
    MemRecorder* tmp = _recorder_list;
    _recorder_list = _recorder_list->next();
    MemTracker::release_thread_recorder(tmp);
  }
}

MemTrackWorker::MemTrackWorker(MemSnapshot* snapshot): _snapshot(snapshot) {
  // create thread uses cgc thread type for now. We should revisit
  // the option, or create new thread type.
  _has_error = !os::create_thread(this, os::cgc_thread);
  set_name("MemTrackWorker", 0);

  // initial generation circuit buffer
  if (!has_error()) {
    _head = _tail = 0;
    for(int index = 0; index < MAX_GENERATIONS; index ++) {
      ::new ((void*)&_gen[index]) GenerationData();
    }
  }
  NOT_PRODUCT(_sync_point_count = 0;)
  NOT_PRODUCT(_merge_count = 0;)
  NOT_PRODUCT(_last_gen_in_use = 0;)
}

MemTrackWorker::~MemTrackWorker() {
  for (int index = 0; index < MAX_GENERATIONS; index ++) {
    _gen[index].reset();
  }
}

void* MemTrackWorker::operator new(size_t size) throw() {
  assert(false, "use nothrow version");
  return NULL;
}

void* MemTrackWorker::operator new(size_t size, const std::nothrow_t& nothrow_constant) throw() {
  return allocate(size, false, mtNMT);
}

void MemTrackWorker::start() {
  os::start_thread(this);
}

/*
 * Native memory tracking worker thread loop:
 *   1. merge one generation of memory recorders to staging area
 *   2. promote staging data to memory snapshot
 *
 * This thread can run through safepoint.
 */

void MemTrackWorker::run() {
  assert(MemTracker::is_on(), "native memory tracking is off");
  this->initialize_thread_local_storage();
  this->record_stack_base_and_size();
  assert(_snapshot != NULL, "Worker should not be started");
  MemRecorder* rec;
  unsigned long processing_generation = 0;
  bool          worker_idle = false;

  while (!MemTracker::shutdown_in_progress()) {
    NOT_PRODUCT(_last_gen_in_use = generations_in_use();)
    {
      // take a recorder from earliest generation in buffer
      ThreadCritical tc;
      rec = _gen[_head].next_recorder();
    }
    if (rec != NULL) {
      if (rec->get_generation() != processing_generation || worker_idle) {
        processing_generation = rec->get_generation();
        worker_idle = false;
        MemTracker::set_current_processing_generation(processing_generation);
      }

      // merge the recorder into staging area
      if (!_snapshot->merge(rec)) {
        MemTracker::shutdown(MemTracker::NMT_out_of_memory);
      } else {
        NOT_PRODUCT(_merge_count ++;)
      }
      MemTracker::release_thread_recorder(rec);
    } else {
      // no more recorder to merge, promote staging area
      // to snapshot
      if (_head != _tail) {
        long number_of_classes;
        {
          ThreadCritical tc;
          if (_gen[_head].has_more_recorder() || _head == _tail) {
            continue;
          }
          number_of_classes = _gen[_head].number_of_classes();
          _gen[_head].reset();

          // done with this generation, increment _head pointer
          _head = (_head + 1) % MAX_GENERATIONS;
        }
        // promote this generation data to snapshot
        if (!_snapshot->promote(number_of_classes)) {
          // failed to promote, means out of memory
          MemTracker::shutdown(MemTracker::NMT_out_of_memory);
        }
      } else {
        // worker thread is idle
        worker_idle = true;
        MemTracker::report_worker_idle();
        _snapshot->wait(1000);
        ThreadCritical tc;
        // check if more data arrived
        if (!_gen[_head].has_more_recorder()) {
          _gen[_head].add_recorders(MemTracker::get_pending_recorders());
        }
      }
    }
  }
  assert(MemTracker::shutdown_in_progress(), "just check");

  // transits to final shutdown
  MemTracker::final_shutdown();
}

// at synchronization point, where 'safepoint visible' Java threads are blocked
// at a safepoint, and the rest of threads are blocked on ThreadCritical lock.
// The caller MemTracker::sync() already takes ThreadCritical before calling this
// method.
//
// Following tasks are performed:
//   1. add all recorders in pending queue to current generation
//   2. increase generation

void MemTrackWorker::at_sync_point(MemRecorder* rec, int number_of_classes) {
  NOT_PRODUCT(_sync_point_count ++;)
  assert(count_recorder(rec) <= MemRecorder::_instance_count,
    "pending queue has infinite loop");

  bool out_of_generation_buffer = false;
  // check shutdown state inside ThreadCritical
  if (MemTracker::shutdown_in_progress()) return;

  _gen[_tail].set_number_of_classes(number_of_classes);
  // append the recorders to the end of the generation
  _gen[_tail].add_recorders(rec);
  assert(count_recorder(_gen[_tail].peek()) <= MemRecorder::_instance_count,
    "after add to current generation has infinite loop");
  // we have collected all recorders for this generation. If there is data,
  // we need to increment _tail to start a new generation.
  if (_gen[_tail].has_more_recorder()  || _head == _tail) {
    _tail = (_tail + 1) % MAX_GENERATIONS;
    out_of_generation_buffer = (_tail == _head);
  }

  if (out_of_generation_buffer) {
    MemTracker::shutdown(MemTracker::NMT_out_of_generation);
  }
}

#ifndef PRODUCT
int MemTrackWorker::count_recorder(const MemRecorder* head) {
  int count = 0;
  while(head != NULL) {
    count ++;
    head = head->next();
  }
  return count;
}

int MemTrackWorker::count_pending_recorders() const {
  int count = 0;
  for (int index = 0; index < MAX_GENERATIONS; index ++) {
    MemRecorder* head = _gen[index].peek();
    if (head != NULL) {
      count += count_recorder(head);
    }
  }
  return count;
}
#endif
