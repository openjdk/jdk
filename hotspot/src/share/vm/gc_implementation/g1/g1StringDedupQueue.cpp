/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/javaClasses.hpp"
#include "gc_implementation/g1/g1StringDedupQueue.hpp"
#include "memory/gcLocker.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/stack.inline.hpp"

G1StringDedupQueue* G1StringDedupQueue::_queue = NULL;
const size_t        G1StringDedupQueue::_max_size = 1000000; // Max number of elements per queue
const size_t        G1StringDedupQueue::_max_cache_size = 0; // Max cache size per queue

G1StringDedupQueue::G1StringDedupQueue() :
  _cursor(0),
  _cancel(false),
  _empty(true),
  _dropped(0) {
  _nqueues = MAX2(ParallelGCThreads, (size_t)1);
  _queues = NEW_C_HEAP_ARRAY(G1StringDedupWorkerQueue, _nqueues, mtGC);
  for (size_t i = 0; i < _nqueues; i++) {
    new (_queues + i) G1StringDedupWorkerQueue(G1StringDedupWorkerQueue::default_segment_size(), _max_cache_size, _max_size);
  }
}

G1StringDedupQueue::~G1StringDedupQueue() {
  ShouldNotReachHere();
}

void G1StringDedupQueue::create() {
  assert(_queue == NULL, "One string deduplication queue allowed");
  _queue = new G1StringDedupQueue();
}

void G1StringDedupQueue::wait() {
  MonitorLockerEx ml(StringDedupQueue_lock, Mutex::_no_safepoint_check_flag);
  while (_queue->_empty && !_queue->_cancel) {
    ml.wait(Mutex::_no_safepoint_check_flag);
  }
}

void G1StringDedupQueue::cancel_wait() {
  MonitorLockerEx ml(StringDedupQueue_lock, Mutex::_no_safepoint_check_flag);
  _queue->_cancel = true;
  ml.notify();
}

void G1StringDedupQueue::push(uint worker_id, oop java_string) {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at safepoint");
  assert(worker_id < _queue->_nqueues, "Invalid queue");

  // Push and notify waiter
  G1StringDedupWorkerQueue& worker_queue = _queue->_queues[worker_id];
  if (!worker_queue.is_full()) {
    worker_queue.push(java_string);
    if (_queue->_empty) {
      MonitorLockerEx ml(StringDedupQueue_lock, Mutex::_no_safepoint_check_flag);
      if (_queue->_empty) {
        // Mark non-empty and notify waiter
        _queue->_empty = false;
        ml.notify();
      }
    }
  } else {
    // Queue is full, drop the string and update the statistics
    Atomic::inc_ptr(&_queue->_dropped);
  }
}

oop G1StringDedupQueue::pop() {
  assert(!SafepointSynchronize::is_at_safepoint(), "Must not be at safepoint");
  No_Safepoint_Verifier nsv;

  // Try all queues before giving up
  for (size_t tries = 0; tries < _queue->_nqueues; tries++) {
    // The cursor indicates where we left of last time
    G1StringDedupWorkerQueue* queue = &_queue->_queues[_queue->_cursor];
    while (!queue->is_empty()) {
      oop obj = queue->pop();
      // The oop we pop can be NULL if it was marked
      // dead. Just ignore those and pop the next oop.
      if (obj != NULL) {
        return obj;
      }
    }

    // Try next queue
    _queue->_cursor = (_queue->_cursor + 1) % _queue->_nqueues;
  }

  // Mark empty
  _queue->_empty = true;

  return NULL;
}

void G1StringDedupQueue::unlink_or_oops_do(G1StringDedupUnlinkOrOopsDoClosure* cl) {
  // A worker thread first claims a queue, which ensures exclusive
  // access to that queue, then continues to process it.
  for (;;) {
    // Grab next queue to scan
    size_t queue = cl->claim_queue();
    if (queue >= _queue->_nqueues) {
      // End of queues
      break;
    }

    // Scan the queue
    unlink_or_oops_do(cl, queue);
  }
}

void G1StringDedupQueue::unlink_or_oops_do(G1StringDedupUnlinkOrOopsDoClosure* cl, size_t queue) {
  assert(queue < _queue->_nqueues, "Invalid queue");
  StackIterator<oop, mtGC> iter(_queue->_queues[queue]);
  while (!iter.is_empty()) {
    oop* p = iter.next_addr();
    if (*p != NULL) {
      if (cl->is_alive(*p)) {
        cl->keep_alive(p);
      } else {
        // Clear dead reference
        *p = NULL;
      }
    }
  }
}

void G1StringDedupQueue::print_statistics(outputStream* st) {
  st->print_cr(
    "   [Queue]\n"
    "      [Dropped: "UINTX_FORMAT"]", _queue->_dropped);
}

void G1StringDedupQueue::verify() {
  for (size_t i = 0; i < _queue->_nqueues; i++) {
    StackIterator<oop, mtGC> iter(_queue->_queues[i]);
    while (!iter.is_empty()) {
      oop obj = iter.next();
      if (obj != NULL) {
        guarantee(Universe::heap()->is_in_reserved(obj), "Object must be on the heap");
        guarantee(!obj->is_forwarded(), "Object must not be forwarded");
        guarantee(java_lang_String::is_instance(obj), "Object must be a String");
      }
    }
  }
}
