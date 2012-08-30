/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

MemTrackWorker::MemTrackWorker() {
  // create thread uses cgc thread type for now. We should revisit
  // the option, or create new thread type.
  _has_error = !os::create_thread(this, os::cgc_thread);
  set_name("MemTrackWorker", 0);

  // initial generation circuit buffer
  if (!has_error()) {
    _head = _tail = 0;
    for(int index = 0; index < MAX_GENERATIONS; index ++) {
      _gen[index] = NULL;
    }
  }
  NOT_PRODUCT(_sync_point_count = 0;)
  NOT_PRODUCT(_merge_count = 0;)
  NOT_PRODUCT(_last_gen_in_use = 0;)
}

MemTrackWorker::~MemTrackWorker() {
  for (int index = 0; index < MAX_GENERATIONS; index ++) {
    MemRecorder* rc = _gen[index];
    if (rc != NULL) {
      delete rc;
    }
  }
}

void* MemTrackWorker::operator new(size_t size) {
  assert(false, "use nothrow version");
  return NULL;
}

void* MemTrackWorker::operator new(size_t size, const std::nothrow_t& nothrow_constant) {
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
  MemSnapshot* snapshot = MemTracker::get_snapshot();
  assert(snapshot != NULL, "Worker should not be started");
  MemRecorder* rec;

  while (!MemTracker::shutdown_in_progress()) {
    NOT_PRODUCT(_last_gen_in_use = generations_in_use();)
    {
      // take a recorder from earliest generation in buffer
      ThreadCritical tc;
      rec = _gen[_head];
      if (rec != NULL) {
        _gen[_head] = rec->next();
      }
      assert(count_recorder(_gen[_head]) <= MemRecorder::_instance_count,
        "infinite loop after dequeue");
    }
    if (rec != NULL) {
      // merge the recorder into staging area
      if (!snapshot->merge(rec)) {
        MemTracker::shutdown(MemTracker::NMT_out_of_memory);
      } else {
        NOT_PRODUCT(_merge_count ++;)
      }
      MemTracker::release_thread_recorder(rec);
    } else {
      // no more recorder to merge, promote staging area
      // to snapshot
      if (_head != _tail) {
        {
          ThreadCritical tc;
          if (_gen[_head] != NULL || _head == _tail) {
            continue;
          }
          // done with this generation, increment _head pointer
          _head = (_head + 1) % MAX_GENERATIONS;
        }
        // promote this generation data to snapshot
        snapshot->promote();
      } else {
        snapshot->wait(1000);
        ThreadCritical tc;
        // check if more data arrived
        if (_gen[_head] == NULL) {
          _gen[_head] = MemTracker::get_pending_recorders();
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

void MemTrackWorker::at_sync_point(MemRecorder* rec) {
  NOT_PRODUCT(_sync_point_count ++;)
  assert(count_recorder(rec) <= MemRecorder::_instance_count,
    "pending queue has infinite loop");

  bool out_of_generation_buffer = false;
  // check shutdown state inside ThreadCritical
  if (MemTracker::shutdown_in_progress()) return;
  // append the recorders to the end of the generation
  if( rec != NULL) {
    MemRecorder* cur_head = _gen[_tail];
    if (cur_head == NULL) {
      _gen[_tail] = rec;
    } else {
      while (cur_head->next() != NULL) {
        cur_head = cur_head->next();
      }
      cur_head->set_next(rec);
    }
  }
  assert(count_recorder(rec) <= MemRecorder::_instance_count,
    "after add to current generation has infinite loop");
  // we have collected all recorders for this generation. If there is data,
  // we need to increment _tail to start a new generation.
  if (_gen[_tail] != NULL || _head == _tail) {
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
    MemRecorder* head = _gen[index];
    if (head != NULL) {
      count += count_recorder(head);
    }
  }
  return count;
}
#endif
