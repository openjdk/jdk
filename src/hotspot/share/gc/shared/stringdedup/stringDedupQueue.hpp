/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_SHARED_STRINGDEDUP_STRINGDEDUPQUEUE_HPP
#define SHARE_VM_GC_SHARED_STRINGDEDUP_STRINGDEDUPQUEUE_HPP

#include "memory/allocation.hpp"
#include "oops/oop.hpp"

class StringDedupUnlinkOrOopsDoClosure;

//
// The deduplication queue acts as the communication channel between mark/evacuation
// phase and the concurrent deduplication phase. Deduplication candidates
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
// id). Popping from the queue is NOT thread safe and can only be done by the deduplication
// thread outside a safepoint.
//

class StringDedupQueue : public CHeapObj<mtGC> {
private:
  static StringDedupQueue*   _queue;
  static volatile size_t     _claimed_index;

public:
  template <typename Q>
  static void create();

  // Blocks and waits for the queue to become non-empty.
  static inline void wait();

  // Wakes up any thread blocked waiting for the queue to become non-empty.
  static inline void cancel_wait();

  // Pushes a deduplication candidate onto a specific GC worker queue.
  static inline void push(uint worker_id, oop java_string);

  // Pops a deduplication candidate from any queue, returns NULL if
  // all queues are empty.
  static inline oop pop();

  static void unlink_or_oops_do(StringDedupUnlinkOrOopsDoClosure* cl);

  static void print_statistics();
  static void verify();

  // GC support
  static void gc_prologue();
  static void gc_epilogue();

protected:
  static StringDedupQueue* const queue();

  // Queue interface.

  // Blocks and waits for the queue to become non-empty.
  virtual void wait_impl() = 0;

  // Wakes up any thread blocked waiting for the queue to become non-empty.
  virtual void cancel_wait_impl() = 0;

  // Pushes a deduplication candidate onto a specific GC worker queue.
  virtual void push_impl(uint worker_id, oop java_string) = 0;

  // Pops a deduplication candidate from any queue, returns NULL if
  // all queues are empty.
  virtual oop pop_impl() = 0;

  virtual void unlink_or_oops_do_impl(StringDedupUnlinkOrOopsDoClosure* cl, size_t queue) = 0;

  virtual void print_statistics_impl() = 0;
  virtual void verify_impl() = 0;

  virtual size_t num_queues() const = 0;

  static size_t claim();
};

#endif // SHARE_VM_GC_SHARED_STRINGDEDUP_STRINGDEDUPQUEUE_HPP
