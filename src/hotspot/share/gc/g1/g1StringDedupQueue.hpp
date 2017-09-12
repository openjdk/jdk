/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_G1STRINGDEDUPQUEUE_HPP
#define SHARE_VM_GC_G1_G1STRINGDEDUPQUEUE_HPP

#include "memory/allocation.hpp"
#include "oops/oop.hpp"
#include "utilities/stack.hpp"

class G1StringDedupUnlinkOrOopsDoClosure;

//
// The deduplication queue acts as the communication channel between the stop-the-world
// mark/evacuation phase and the concurrent deduplication phase. Deduplication candidates
// found during mark/evacuation are placed on this queue for later processing in the
// deduplication thread. A queue entry is an oop pointing to a String object (as opposed
// to entries in the deduplication hashtable which points to character arrays).
//
// While users of the queue treat it as a single queue, it is implemented as a set of
// queues, one queue per GC worker thread, to allow lock-free and cache-friendly enqueue
// operations by the GC workers.
//
// The oops in the queue are treated as weak pointers, meaning the objects they point to
// can become unreachable and pruned (cleared) before being popped by the deduplication
// thread.
//
// Pushing to the queue is thread safe (this relies on each thread using a unique worker
// id), but only allowed during a safepoint. Popping from the queue is NOT thread safe
// and can only be done by the deduplication thread outside a safepoint.
//
// The StringDedupQueue_lock is only used for blocking and waking up the deduplication
// thread in case the queue is empty or becomes non-empty, respectively. This lock does
// not otherwise protect the queue content.
//
class G1StringDedupQueue : public CHeapObj<mtGC> {
private:
  typedef Stack<oop, mtGC> G1StringDedupWorkerQueue;

  static G1StringDedupQueue* _queue;
  static const size_t        _max_size;
  static const size_t        _max_cache_size;

  G1StringDedupWorkerQueue*  _queues;
  size_t                     _nqueues;
  size_t                     _cursor;
  bool                       _cancel;
  volatile bool              _empty;

  // Statistics counter, only used for logging.
  uintx                      _dropped;

  G1StringDedupQueue();
  ~G1StringDedupQueue();

  static void unlink_or_oops_do(G1StringDedupUnlinkOrOopsDoClosure* cl, size_t queue);

public:
  static void create();

  // Blocks and waits for the queue to become non-empty.
  static void wait();

  // Wakes up any thread blocked waiting for the queue to become non-empty.
  static void cancel_wait();

  // Pushes a deduplication candidate onto a specific GC worker queue.
  static void push(uint worker_id, oop java_string);

  // Pops a deduplication candidate from any queue, returns NULL if
  // all queues are empty.
  static oop pop();

  static void unlink_or_oops_do(G1StringDedupUnlinkOrOopsDoClosure* cl);

  static void print_statistics();
  static void verify();
};

#endif // SHARE_VM_GC_G1_G1STRINGDEDUPQUEUE_HPP
