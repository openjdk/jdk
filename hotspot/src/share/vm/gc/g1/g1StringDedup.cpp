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

#include "precompiled.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1GCPhaseTimes.hpp"
#include "gc/g1/g1StringDedup.hpp"
#include "gc/g1/g1StringDedupQueue.hpp"
#include "gc/g1/g1StringDedupStat.hpp"
#include "gc/g1/g1StringDedupTable.hpp"
#include "gc/g1/g1StringDedupThread.hpp"
#include "runtime/atomic.inline.hpp"

bool G1StringDedup::_enabled = false;

void G1StringDedup::initialize() {
  assert(UseG1GC, "String deduplication only available with G1");
  if (UseStringDeduplication) {
    _enabled = true;
    G1StringDedupQueue::create();
    G1StringDedupTable::create();
    G1StringDedupThread::create();
  }
}

void G1StringDedup::stop() {
  assert(is_enabled(), "String deduplication not enabled");
  G1StringDedupThread::stop();
}

bool G1StringDedup::is_candidate_from_mark(oop obj) {
  if (java_lang_String::is_instance_inlined(obj)) {
    bool from_young = G1CollectedHeap::heap()->heap_region_containing(obj)->is_young();
    if (from_young && obj->age() < StringDeduplicationAgeThreshold) {
      // Candidate found. String is being evacuated from young to old but has not
      // reached the deduplication age threshold, i.e. has not previously been a
      // candidate during its life in the young generation.
      return true;
    }
  }

  // Not a candidate
  return false;
}

void G1StringDedup::enqueue_from_mark(oop java_string) {
  assert(is_enabled(), "String deduplication not enabled");
  if (is_candidate_from_mark(java_string)) {
    G1StringDedupQueue::push(0 /* worker_id */, java_string);
  }
}

bool G1StringDedup::is_candidate_from_evacuation(bool from_young, bool to_young, oop obj) {
  if (from_young && java_lang_String::is_instance_inlined(obj)) {
    if (to_young && obj->age() == StringDeduplicationAgeThreshold) {
      // Candidate found. String is being evacuated from young to young and just
      // reached the deduplication age threshold.
      return true;
    }
    if (!to_young && obj->age() < StringDeduplicationAgeThreshold) {
      // Candidate found. String is being evacuated from young to old but has not
      // reached the deduplication age threshold, i.e. has not previously been a
      // candidate during its life in the young generation.
      return true;
    }
  }

  // Not a candidate
  return false;
}

void G1StringDedup::enqueue_from_evacuation(bool from_young, bool to_young, uint worker_id, oop java_string) {
  assert(is_enabled(), "String deduplication not enabled");
  if (is_candidate_from_evacuation(from_young, to_young, java_string)) {
    G1StringDedupQueue::push(worker_id, java_string);
  }
}

void G1StringDedup::deduplicate(oop java_string) {
  assert(is_enabled(), "String deduplication not enabled");
  G1StringDedupStat dummy; // Statistics from this path is never used
  G1StringDedupTable::deduplicate(java_string, dummy);
}

void G1StringDedup::oops_do(OopClosure* keep_alive) {
  assert(is_enabled(), "String deduplication not enabled");
  unlink_or_oops_do(NULL, keep_alive, true /* allow_resize_and_rehash */);
}

void G1StringDedup::unlink(BoolObjectClosure* is_alive) {
  assert(is_enabled(), "String deduplication not enabled");
  // Don't allow a potential resize or rehash during unlink, as the unlink
  // operation itself might remove enough entries to invalidate such a decision.
  unlink_or_oops_do(is_alive, NULL, false /* allow_resize_and_rehash */);
}

//
// Task for parallel unlink_or_oops_do() operation on the deduplication queue
// and table.
//
class G1StringDedupUnlinkOrOopsDoTask : public AbstractGangTask {
private:
  G1StringDedupUnlinkOrOopsDoClosure _cl;
  G1GCPhaseTimes* _phase_times;

public:
  G1StringDedupUnlinkOrOopsDoTask(BoolObjectClosure* is_alive,
                                  OopClosure* keep_alive,
                                  bool allow_resize_and_rehash,
                                  G1GCPhaseTimes* phase_times) :
    AbstractGangTask("G1StringDedupUnlinkOrOopsDoTask"),
    _cl(is_alive, keep_alive, allow_resize_and_rehash), _phase_times(phase_times) { }

  virtual void work(uint worker_id) {
    {
      G1GCParPhaseTimesTracker x(_phase_times, G1GCPhaseTimes::StringDedupQueueFixup, worker_id);
      G1StringDedupQueue::unlink_or_oops_do(&_cl);
    }
    {
      G1GCParPhaseTimesTracker x(_phase_times, G1GCPhaseTimes::StringDedupTableFixup, worker_id);
      G1StringDedupTable::unlink_or_oops_do(&_cl, worker_id);
    }
  }
};

void G1StringDedup::unlink_or_oops_do(BoolObjectClosure* is_alive,
                                      OopClosure* keep_alive,
                                      bool allow_resize_and_rehash,
                                      G1GCPhaseTimes* phase_times) {
  assert(is_enabled(), "String deduplication not enabled");

  G1StringDedupUnlinkOrOopsDoTask task(is_alive, keep_alive, allow_resize_and_rehash, phase_times);
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  g1h->workers()->run_task(&task);
}

void G1StringDedup::threads_do(ThreadClosure* tc) {
  assert(is_enabled(), "String deduplication not enabled");
  tc->do_thread(G1StringDedupThread::thread());
}

void G1StringDedup::print_worker_threads_on(outputStream* st) {
  assert(is_enabled(), "String deduplication not enabled");
  G1StringDedupThread::thread()->print_on(st);
  st->cr();
}

void G1StringDedup::verify() {
  assert(is_enabled(), "String deduplication not enabled");
  G1StringDedupQueue::verify();
  G1StringDedupTable::verify();
}

G1StringDedupUnlinkOrOopsDoClosure::G1StringDedupUnlinkOrOopsDoClosure(BoolObjectClosure* is_alive,
                                                                       OopClosure* keep_alive,
                                                                       bool allow_resize_and_rehash) :
  _is_alive(is_alive),
  _keep_alive(keep_alive),
  _resized_table(NULL),
  _rehashed_table(NULL),
  _next_queue(0),
  _next_bucket(0) {
  if (allow_resize_and_rehash) {
    // If both resize and rehash is needed, only do resize. Rehash of
    // the table will eventually happen if the situation persists.
    _resized_table = G1StringDedupTable::prepare_resize();
    if (!is_resizing()) {
      _rehashed_table = G1StringDedupTable::prepare_rehash();
    }
  }
}

G1StringDedupUnlinkOrOopsDoClosure::~G1StringDedupUnlinkOrOopsDoClosure() {
  assert(!is_resizing() || !is_rehashing(), "Can not both resize and rehash");
  if (is_resizing()) {
    G1StringDedupTable::finish_resize(_resized_table);
  } else if (is_rehashing()) {
    G1StringDedupTable::finish_rehash(_rehashed_table);
  }
}

// Atomically claims the next available queue for exclusive access by
// the current thread. Returns the queue number of the claimed queue.
size_t G1StringDedupUnlinkOrOopsDoClosure::claim_queue() {
  return (size_t)Atomic::add_ptr(1, &_next_queue) - 1;
}

// Atomically claims the next available table partition for exclusive
// access by the current thread. Returns the table bucket number where
// the claimed partition starts.
size_t G1StringDedupUnlinkOrOopsDoClosure::claim_table_partition(size_t partition_size) {
  return (size_t)Atomic::add_ptr(partition_size, &_next_bucket) - partition_size;
}
