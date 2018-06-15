/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/javaClasses.inline.hpp"
#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/g1StringDedup.hpp"
#include "gc/g1/g1StringDedupQueue.hpp"
#include "logging/log.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/safepointVerifiers.hpp"
#include "utilities/stack.inline.hpp"

const size_t        G1StringDedupQueue::_max_size = 1000000; // Max number of elements per queue
const size_t        G1StringDedupQueue::_max_cache_size = 0; // Max cache size per queue

G1StringDedupQueue::G1StringDedupQueue() :
  _cursor(0),
  _cancel(false),
  _empty(true),
  _dropped(0) {
  _nqueues = ParallelGCThreads;
  _queues = NEW_C_HEAP_ARRAY(G1StringDedupWorkerQueue, _nqueues, mtGC);
  for (size_t i = 0; i < _nqueues; i++) {
    new (_queues + i) G1StringDedupWorkerQueue(G1StringDedupWorkerQueue::default_segment_size(), _max_cache_size, _max_size);
  }
}

G1StringDedupQueue::~G1StringDedupQueue() {
  ShouldNotReachHere();
}

void G1StringDedupQueue::wait_impl() {
  MonitorLockerEx ml(StringDedupQueue_lock, Mutex::_no_safepoint_check_flag);
  while (_empty && !_cancel) {
    ml.wait(Mutex::_no_safepoint_check_flag);
  }
}

void G1StringDedupQueue::cancel_wait_impl() {
  MonitorLockerEx ml(StringDedupQueue_lock, Mutex::_no_safepoint_check_flag);
  _cancel = true;
  ml.notify();
}

void G1StringDedupQueue::push_impl(uint worker_id, oop java_string) {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at safepoint");
  assert(worker_id < _nqueues, "Invalid queue");

  // Push and notify waiter
  G1StringDedupWorkerQueue& worker_queue = _queues[worker_id];
  if (!worker_queue.is_full()) {
    worker_queue.push(java_string);
    if (_empty) {
      MonitorLockerEx ml(StringDedupQueue_lock, Mutex::_no_safepoint_check_flag);
      if (_empty) {
        // Mark non-empty and notify waiter
        _empty = false;
        ml.notify();
      }
    }
  } else {
    // Queue is full, drop the string and update the statistics
    Atomic::inc(&_dropped);
  }
}

oop G1StringDedupQueue::pop_impl() {
  assert(!SafepointSynchronize::is_at_safepoint(), "Must not be at safepoint");
  NoSafepointVerifier nsv;

  // Try all queues before giving up
  for (size_t tries = 0; tries < _nqueues; tries++) {
    // The cursor indicates where we left of last time
    G1StringDedupWorkerQueue* queue = &_queues[_cursor];
    while (!queue->is_empty()) {
      oop obj = queue->pop();
      // The oop we pop can be NULL if it was marked
      // dead. Just ignore those and pop the next oop.
      if (obj != NULL) {
        return obj;
      }
    }

    // Try next queue
    _cursor = (_cursor + 1) % _nqueues;
  }

  // Mark empty
  _empty = true;

  return NULL;
}

void G1StringDedupQueue::unlink_or_oops_do_impl(StringDedupUnlinkOrOopsDoClosure* cl, size_t queue) {
  assert(queue < _nqueues, "Invalid queue");
  StackIterator<oop, mtGC> iter(_queues[queue]);
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

void G1StringDedupQueue::print_statistics_impl() {
  log_debug(gc, stringdedup)("  Queue");
  log_debug(gc, stringdedup)("    Dropped: " UINTX_FORMAT, _dropped);
}

void G1StringDedupQueue::verify_impl() {
  for (size_t i = 0; i < _nqueues; i++) {
    StackIterator<oop, mtGC> iter(_queues[i]);
    while (!iter.is_empty()) {
      oop obj = iter.next();
      if (obj != NULL) {
        guarantee(G1CollectedHeap::heap()->is_in_reserved(obj), "Object must be on the heap");
        guarantee(!obj->is_forwarded(), "Object must not be forwarded");
        guarantee(java_lang_String::is_instance(obj), "Object must be a String");
      }
    }
  }
}
