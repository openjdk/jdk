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
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "gc_implementation/g1/g1GCPhaseTimes.hpp"
#include "gc_implementation/g1/g1StringDedup.hpp"
#include "gc_implementation/g1/g1StringDedupQueue.hpp"
#include "gc_implementation/g1/g1StringDedupStat.hpp"
#include "gc_implementation/g1/g1StringDedupTable.hpp"
#include "gc_implementation/g1/g1StringDedupThread.hpp"

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
  if (java_lang_String::is_instance(obj)) {
    bool from_young = G1CollectedHeap::heap()->heap_region_containing_raw(obj)->is_young();
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
  if (from_young && java_lang_String::is_instance(obj)) {
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
  unlink_or_oops_do(NULL, keep_alive);
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

public:
  G1StringDedupUnlinkOrOopsDoTask(BoolObjectClosure* is_alive,
                                  OopClosure* keep_alive,
                                  bool allow_resize_and_rehash) :
    AbstractGangTask("G1StringDedupUnlinkOrOopsDoTask"),
    _cl(is_alive, keep_alive, allow_resize_and_rehash) {
  }

  virtual void work(uint worker_id) {
    double queue_fixup_start = os::elapsedTime();
    G1StringDedupQueue::unlink_or_oops_do(&_cl);

    double table_fixup_start = os::elapsedTime();
    G1StringDedupTable::unlink_or_oops_do(&_cl, worker_id);

    double queue_fixup_time_ms = (table_fixup_start - queue_fixup_start) * 1000.0;
    double table_fixup_time_ms = (os::elapsedTime() - table_fixup_start) * 1000.0;
    G1CollectorPolicy* g1p = G1CollectedHeap::heap()->g1_policy();
    g1p->phase_times()->record_string_dedup_queue_fixup_worker_time(worker_id, queue_fixup_time_ms);
    g1p->phase_times()->record_string_dedup_table_fixup_worker_time(worker_id, table_fixup_time_ms);
  }
};

void G1StringDedup::unlink_or_oops_do(BoolObjectClosure* is_alive, OopClosure* keep_alive, bool allow_resize_and_rehash) {
  assert(is_enabled(), "String deduplication not enabled");
  G1CollectorPolicy* g1p = G1CollectedHeap::heap()->g1_policy();
  g1p->phase_times()->note_string_dedup_fixup_start();
  double fixup_start = os::elapsedTime();

  G1StringDedupUnlinkOrOopsDoTask task(is_alive, keep_alive, allow_resize_and_rehash);
  if (G1CollectedHeap::use_parallel_gc_threads()) {
    G1CollectedHeap* g1h = G1CollectedHeap::heap();
    g1h->set_par_threads();
    g1h->workers()->run_task(&task);
    g1h->set_par_threads(0);
  } else {
    task.work(0);
  }

  double fixup_time_ms = (os::elapsedTime() - fixup_start) * 1000.0;
  g1p->phase_times()->record_string_dedup_fixup_time(fixup_time_ms);
  g1p->phase_times()->note_string_dedup_fixup_end();
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
