/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1ConcurrentRefineStats.hpp"
#include "gc/g1/g1DirtyCardQueue.hpp"
#include "gc/g1/g1YoungGCPreEvacuateTasks.hpp"
#include "gc/shared/barrierSet.inline.hpp"
#include "gc/shared/threadLocalAllocBuffer.inline.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/iterator.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/threads.hpp"

class G1PreEvacuateCollectionSetBatchTask::JavaThreadRetireTLABAndFlushLogs : public G1AbstractSubTask {
  G1JavaThreadsListClaimer _claimer;

  // Per worker thread statistics.
  ThreadLocalAllocStats* _local_tlab_stats;
  G1ConcurrentRefineStats* _local_refinement_stats;

  uint _num_workers;

  // There is relatively little work to do per thread.
  static const uint ThreadsPerWorker = 250;

  struct RetireTLABAndFlushLogsClosure : public ThreadClosure {
    ThreadLocalAllocStats _tlab_stats;
    G1ConcurrentRefineStats _refinement_stats;

    RetireTLABAndFlushLogsClosure() : _tlab_stats(), _refinement_stats() { }

    void do_thread(Thread* thread) override {
      assert(thread->is_Java_thread(), "must be");
      // Flushes deferred card marks, so must precede concatenating logs.
      BarrierSet::barrier_set()->make_parsable((JavaThread*)thread);
      if (UseTLAB) {
        thread->tlab().retire(&_tlab_stats);
      }

      G1DirtyCardQueueSet& qset = G1BarrierSet::dirty_card_queue_set();
      _refinement_stats += qset.concatenate_log_and_stats(thread);
    }
  };

public:
  JavaThreadRetireTLABAndFlushLogs() :
    G1AbstractSubTask(G1GCPhaseTimes::RetireTLABsAndFlushLogs),
    _claimer(ThreadsPerWorker),
    _local_tlab_stats(nullptr),
    _local_refinement_stats(nullptr),
    _num_workers(0) {
  }

  ~JavaThreadRetireTLABAndFlushLogs() {
    static_assert(std::is_trivially_destructible<G1ConcurrentRefineStats>::value, "must be");
    FREE_C_HEAP_ARRAY(G1ConcurrentRefineStats, _local_refinement_stats);

    static_assert(std::is_trivially_destructible<ThreadLocalAllocStats>::value, "must be");
    FREE_C_HEAP_ARRAY(ThreadLocalAllocStats, _local_tlab_stats);
  }

  void do_work(uint worker_id) override {
    RetireTLABAndFlushLogsClosure tc;
    _claimer.apply(&tc);

    _local_tlab_stats[worker_id] = tc._tlab_stats;
    _local_refinement_stats[worker_id] = tc._refinement_stats;
  }

  double worker_cost() const override {
    return (double)_claimer.length() / ThreadsPerWorker;
  }

  void set_max_workers(uint max_workers) override {
    _num_workers = max_workers;
    _local_tlab_stats = NEW_C_HEAP_ARRAY(ThreadLocalAllocStats, _num_workers, mtGC);
    _local_refinement_stats = NEW_C_HEAP_ARRAY(G1ConcurrentRefineStats, _num_workers, mtGC);

    for (uint i = 0; i < _num_workers; i++) {
      ::new (&_local_tlab_stats[i]) ThreadLocalAllocStats();
      ::new (&_local_refinement_stats[i]) G1ConcurrentRefineStats();
    }
  }

  ThreadLocalAllocStats tlab_stats() const {
    ThreadLocalAllocStats result;
    for (uint i = 0; i < _num_workers; i++) {
      result.update(_local_tlab_stats[i]);
    }
    return result;
  }

  G1ConcurrentRefineStats refinement_stats() const {
    G1ConcurrentRefineStats result;
    for (uint i = 0; i < _num_workers; i++) {
      result += _local_refinement_stats[i];
    }
    return result;
  }
};

class G1PreEvacuateCollectionSetBatchTask::NonJavaThreadFlushLogs : public G1AbstractSubTask {
  struct FlushLogsClosure : public ThreadClosure {
    G1ConcurrentRefineStats _refinement_stats;

    FlushLogsClosure() : _refinement_stats() { }

    void do_thread(Thread* thread) override {
      G1DirtyCardQueueSet& qset = G1BarrierSet::dirty_card_queue_set();
      _refinement_stats += qset.concatenate_log_and_stats(thread);
    }
  } _tc;

public:
  NonJavaThreadFlushLogs() : G1AbstractSubTask(G1GCPhaseTimes::NonJavaThreadFlushLogs), _tc() { }

  void do_work(uint worker_id) override {
    Threads::non_java_threads_do(&_tc);
  }

  double worker_cost() const override {
    return 1.0;
  }

  G1ConcurrentRefineStats refinement_stats() const { return _tc._refinement_stats; }
};

G1PreEvacuateCollectionSetBatchTask::G1PreEvacuateCollectionSetBatchTask() :
  G1BatchedTask("Pre Evacuate Prepare", G1CollectedHeap::heap()->phase_times()),
  _old_pending_cards(G1BarrierSet::dirty_card_queue_set().num_cards()),
  _java_retire_task(new JavaThreadRetireTLABAndFlushLogs()),
  _non_java_retire_task(new NonJavaThreadFlushLogs()) {

  // Disable mutator refinement until concurrent refinement decides otherwise.
  G1BarrierSet::dirty_card_queue_set().set_mutator_refinement_threshold(SIZE_MAX);

  add_serial_task(_non_java_retire_task);
  add_parallel_task(_java_retire_task);
}

static void verify_empty_dirty_card_logs() {
#ifdef ASSERT
  ResourceMark rm;

  struct Verifier : public ThreadClosure {
    Verifier() {}
    void do_thread(Thread* t) override {
      G1DirtyCardQueue& queue = G1ThreadLocalData::dirty_card_queue(t);
      assert(queue.is_empty(), "non-empty dirty card queue for thread %s", t->name());
    }
  } verifier;
  Threads::threads_do(&verifier);
#endif
}

G1PreEvacuateCollectionSetBatchTask::~G1PreEvacuateCollectionSetBatchTask() {
  _java_retire_task->tlab_stats().publish();

  G1DirtyCardQueueSet& qset = G1BarrierSet::dirty_card_queue_set();

  G1ConcurrentRefineStats total_refinement_stats;
  total_refinement_stats += _java_retire_task->refinement_stats();
  total_refinement_stats += _non_java_retire_task->refinement_stats();
  qset.update_refinement_stats(total_refinement_stats);

  verify_empty_dirty_card_logs();

  size_t pending_cards = qset.num_cards();
  size_t thread_buffer_cards = pending_cards - _old_pending_cards;
  G1CollectedHeap::heap()->policy()->record_concurrent_refinement_stats(pending_cards, thread_buffer_cards);
}
