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

#ifndef SHARE_VM_GC_G1_G1STRINGDEDUPQUEUE_HPP
#define SHARE_VM_GC_G1_G1STRINGDEDUPQUEUE_HPP

#include "gc/shared/stringdedup/stringDedupQueue.hpp"
#include "memory/allocation.hpp"
#include "oops/oop.hpp"
#include "utilities/stack.hpp"

class StringDedupUnlinkOrOopsDoClosure;

//
// G1 enqueues candidates during the stop-the-world mark/evacuation phase.
//

class G1StringDedupQueue : public StringDedupQueue {
private:
  typedef Stack<oop, mtGC> G1StringDedupWorkerQueue;

  static const size_t        _max_size;
  static const size_t        _max_cache_size;

  G1StringDedupWorkerQueue*  _queues;
  size_t                     _nqueues;
  size_t                     _cursor;
  bool                       _cancel;
  volatile bool              _empty;

  // Statistics counter, only used for logging.
  uintx                      _dropped;

  ~G1StringDedupQueue();

  void unlink_or_oops_do(StringDedupUnlinkOrOopsDoClosure* cl, size_t queue);

public:
  G1StringDedupQueue();

protected:

  // Blocks and waits for the queue to become non-empty.
  void wait_impl();

  // Wakes up any thread blocked waiting for the queue to become non-empty.
  void cancel_wait_impl();

  // Pushes a deduplication candidate onto a specific GC worker queue.
  void push_impl(uint worker_id, oop java_string);

  // Pops a deduplication candidate from any queue, returns NULL if
  // all queues are empty.
  oop pop_impl();

  size_t num_queues() const {
    return _nqueues;
  }

  void unlink_or_oops_do_impl(StringDedupUnlinkOrOopsDoClosure* cl, size_t queue);

  void print_statistics_impl();
  void verify_impl();
};

#endif // SHARE_VM_GC_G1_G1STRINGDEDUPQUEUE_HPP
