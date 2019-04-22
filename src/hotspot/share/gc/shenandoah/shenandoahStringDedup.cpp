/*
 * Copyright (c) 2017, 2019, Red Hat, Inc. All rights reserved.
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

#include "gc/shared/stringdedup/stringDedup.inline.hpp"
#include "gc/shared/workgroup.hpp"
#include "gc/shenandoah/shenandoahCollectionSet.inline.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.inline.hpp"
#include "gc/shenandoah/shenandoahStringDedup.hpp"
#include "gc/shenandoah/shenandoahStrDedupQueue.hpp"
#include "gc/shenandoah/shenandoahTimingTracker.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "runtime/thread.hpp"

void ShenandoahStringDedup::initialize() {
  assert(UseShenandoahGC, "String deduplication available with Shenandoah GC");
  StringDedup::initialize_impl<ShenandoahStrDedupQueue, StringDedupStat>();
}

/* Enqueue candidates for deduplication.
 * The method should only be called by GC worker threads during marking phases.
 */
void ShenandoahStringDedup::enqueue_candidate(oop java_string) {
  assert(Thread::current()->is_Worker_thread(),
        "Only from a GC worker thread");

  if (java_string->age() <= StringDeduplicationAgeThreshold) {
    const markOop mark = java_string->mark();

    // Having/had displaced header, too risk to deal with them, skip
    if (mark == markOopDesc::INFLATING() || mark->has_displaced_mark_helper()) {
      return;
    }

    // Increase string age and enqueue it when it rearches age threshold
    markOop new_mark = mark->incr_age();
    if (mark == java_string->cas_set_mark(new_mark, mark)) {
      if (mark->age() == StringDeduplicationAgeThreshold) {
        StringDedupQueue::push(ShenandoahWorkerSession::worker_id(), java_string);
      }
    }
  }
}

// Deduplicate a string, return true if it is deduplicated.
void ShenandoahStringDedup::deduplicate(oop java_string) {
  assert(is_enabled(), "String deduplication not enabled");
  StringDedupStat dummy; // Statistics from this path is never used
  StringDedupTable::deduplicate(java_string, &dummy);
}

void ShenandoahStringDedup::parallel_oops_do(BoolObjectClosure* is_alive, OopClosure* cl, uint worker_id) {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at a safepoint");
  assert(is_enabled(), "String deduplication not enabled");

  ShenandoahWorkerTimings* worker_times = ShenandoahHeap::heap()->phase_timings()->worker_times();

  StringDedupUnlinkOrOopsDoClosure sd_cl(is_alive, cl);

  {
    ShenandoahWorkerTimingsTracker x(worker_times, ShenandoahPhaseTimings::StringDedupQueueRoots, worker_id);
    StringDedupQueue::unlink_or_oops_do(&sd_cl);
  }
  {
    ShenandoahWorkerTimingsTracker x(worker_times, ShenandoahPhaseTimings::StringDedupTableRoots, worker_id);
    StringDedupTable::unlink_or_oops_do(&sd_cl, worker_id);
  }
}

void ShenandoahStringDedup::oops_do_slow(OopClosure* cl) {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at a safepoint");
  assert(is_enabled(), "String deduplication not enabled");
  AlwaysTrueClosure always_true;
  StringDedupUnlinkOrOopsDoClosure sd_cl(&always_true, cl);
  StringDedupQueue::unlink_or_oops_do(&sd_cl);
  StringDedupTable::unlink_or_oops_do(&sd_cl, 0);
}

class ShenandoahIsMarkedNextClosure : public BoolObjectClosure {
private:
  ShenandoahMarkingContext* const _mark_context;

public:
  ShenandoahIsMarkedNextClosure() : _mark_context(ShenandoahHeap::heap()->marking_context()) { }

  bool do_object_b(oop obj) {
    return _mark_context->is_marked(obj);
  }
};

//
// Task for parallel unlink_or_oops_do() operation on the deduplication queue
// and table.
//
class ShenandoahStringDedupUnlinkOrOopsDoTask : public AbstractGangTask {
private:
  StringDedupUnlinkOrOopsDoClosure _cl;

public:
  ShenandoahStringDedupUnlinkOrOopsDoTask(BoolObjectClosure* is_alive,
                                  OopClosure* keep_alive,
                                  bool allow_resize_and_rehash) :
    AbstractGangTask("StringDedupUnlinkOrOopsDoTask"),
    _cl(is_alive, keep_alive) {
      StringDedup::gc_prologue(allow_resize_and_rehash);
  }

  ~ShenandoahStringDedupUnlinkOrOopsDoTask() {
    StringDedup::gc_epilogue();
  }

  virtual void work(uint worker_id) {
    StringDedupQueue::unlink_or_oops_do(&_cl);
    StringDedupTable::unlink_or_oops_do(&_cl, worker_id);
  }
};

void ShenandoahStringDedup::unlink_or_oops_do(BoolObjectClosure* is_alive,
                                      OopClosure* keep_alive,
                                      bool allow_resize_and_rehash) {
  assert(is_enabled(), "String deduplication not enabled");

  ShenandoahStringDedupUnlinkOrOopsDoTask task(is_alive, keep_alive, allow_resize_and_rehash);
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  heap->workers()->run_task(&task);
}
