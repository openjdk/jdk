/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1RegionPinCache.inline.hpp"
#include "gc/g1/g1ThreadLocalData.hpp"
#include "gc/g1/g1YoungGCPreEvacuateTasks.hpp"
#include "gc/shared/barrierSet.inline.hpp"
#include "gc/shared/threadLocalAllocBuffer.inline.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/iterator.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/threads.hpp"

class G1PreEvacuateCollectionSetBatchTask::JavaThreadRetireTLABs : public G1AbstractSubTask {
  G1JavaThreadsListClaimer _claimer;

  // Per worker thread statistics.
  ThreadLocalAllocStats* _local_tlab_stats;

  uint _num_workers;

  // There is relatively little work to do per thread.
  static const uint ThreadsPerWorker = 250;

  struct RetireTLABClosure : public ThreadClosure {
    ThreadLocalAllocStats _tlab_stats;

    RetireTLABClosure() : _tlab_stats() { }

    void do_thread(Thread* thread) override {
      assert(thread->is_Java_thread(), "must be");
      // Flushes deferred card marks, so must precede concatenating logs.
      BarrierSet::barrier_set()->make_parsable((JavaThread*)thread);
      // Retire TLABs.
      if (UseTLAB) {
        thread->retire_tlab(&_tlab_stats);
      }
      // Flush region pin count cache.
      G1ThreadLocalData::pin_count_cache(thread).flush();
    }
  };

public:
  JavaThreadRetireTLABs() :
    G1AbstractSubTask(G1GCPhaseTimes::RetireTLABs),
    _claimer(ThreadsPerWorker),
    _local_tlab_stats(nullptr),
    _num_workers(0) {
  }

  ~JavaThreadRetireTLABs() {
    static_assert(std::is_trivially_destructible<ThreadLocalAllocStats>::value, "must be");
    FREE_C_HEAP_ARRAY(ThreadLocalAllocStats, _local_tlab_stats);
  }

  void do_work(uint worker_id) override {
    RetireTLABClosure tc;
    _claimer.apply(&tc);

    _local_tlab_stats[worker_id] = tc._tlab_stats;
  }

  double worker_cost() const override {
    return (double)_claimer.length() / ThreadsPerWorker;
  }

  void set_max_workers(uint max_workers) override {
    _num_workers = max_workers;
    _local_tlab_stats = NEW_C_HEAP_ARRAY(ThreadLocalAllocStats, _num_workers, mtGC);

    for (uint i = 0; i < _num_workers; i++) {
      ::new (&_local_tlab_stats[i]) ThreadLocalAllocStats();
    }
  }

  ThreadLocalAllocStats tlab_stats() const {
    ThreadLocalAllocStats result;
    for (uint i = 0; i < _num_workers; i++) {
      result.update(_local_tlab_stats[i]);
    }
    return result;
  }
};

G1PreEvacuateCollectionSetBatchTask::G1PreEvacuateCollectionSetBatchTask() :
  G1BatchedTask("Pre Evacuate Prepare", G1CollectedHeap::heap()->phase_times()),
  _java_retire_task(new JavaThreadRetireTLABs()) {

  add_parallel_task(_java_retire_task);
}

G1PreEvacuateCollectionSetBatchTask::~G1PreEvacuateCollectionSetBatchTask() {
  _java_retire_task->tlab_stats().publish();
}
