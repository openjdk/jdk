/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1BatchedTask.hpp"
#include "gc/shared/workerThread.hpp"
#include "runtime/atomic.hpp"
#include "unittest.hpp"

class G1BatchedTaskWorkers : AllStatic {
  static WorkerThreads* _workers;
  static WorkerThreads* workers() {
    if (_workers == nullptr) {
      _workers = new WorkerThreads("G1 Small Workers", MaxWorkers);
      _workers->initialize_workers();
      _workers->set_active_workers(MaxWorkers);
    }
    return _workers;
  }

public:
  static const uint MaxWorkers = 4;
  static void run_task(WorkerTask* task) {
    workers()->run_task(task);
  }
};

WorkerThreads* G1BatchedTaskWorkers::_workers = nullptr;

class G1TestSubTask : public G1AbstractSubTask {
  mutable uint _phase;
  volatile uint _num_do_work; // Amount of do_work() has been called.

  void check_and_inc_phase(uint expected) const {
    ASSERT_EQ(_phase, expected);
    _phase++;
  }

  bool volatile* _do_work_called_by;

protected:
  uint _max_workers;

  void do_work_called(uint worker_id) {
    Atomic::inc(&_num_do_work);
    bool orig_value = Atomic::cmpxchg(&_do_work_called_by[worker_id], false, true);
    ASSERT_EQ(orig_value, false);
  }

  void verify_do_work_called_by(uint num_workers) {
    ASSERT_EQ(Atomic::load(&_num_do_work), num_workers);
    // Do not need to check the _do_work_called_by array. The count is already verified
    // by above statement, and we already check that a given flag is only set once.
  }

public:
  // Actual use of GCParPhasesSentinel will cause an assertion failure when trying
  // to add timing information - this should be disabled here.
  G1TestSubTask() : G1AbstractSubTask(G1GCPhaseTimes::GCParPhasesSentinel),
    _phase(0),
    _num_do_work(0),
    _do_work_called_by(nullptr),
    _max_workers(0) {
    check_and_inc_phase(0);
  }

  ~G1TestSubTask() {
    check_and_inc_phase(3);
    FREE_C_HEAP_ARRAY(bool, _do_work_called_by);
  }

  double worker_cost() const override {
    check_and_inc_phase(1);
    return 1.0;
  }

  // Called by G1BatchedTask to provide information about the the maximum
  // number of workers for all subtasks after it has been determined.
  void set_max_workers(uint max_workers) override {
    assert(max_workers >= 1, "must be");
    check_and_inc_phase(2);

    _do_work_called_by = NEW_C_HEAP_ARRAY(bool, max_workers, mtInternal);
    for (uint i = 0; i < max_workers; i++) {
      _do_work_called_by[i] = false;
    }
    _max_workers = max_workers;
  }

  void do_work(uint worker_id) override {
    do_work_called(worker_id);
  }
};

class G1SerialTestSubTask : public G1TestSubTask {
public:
  G1SerialTestSubTask() : G1TestSubTask() { }
  ~G1SerialTestSubTask() {
    verify_do_work_called_by(1);
  }

  double worker_cost() const override {
    G1TestSubTask::worker_cost();
    return 1.0;
  }
};

class G1ParallelTestSubTask : public G1TestSubTask {
public:
  G1ParallelTestSubTask() : G1TestSubTask() { }
  ~G1ParallelTestSubTask() {
    verify_do_work_called_by(_max_workers);
  }

  double worker_cost() const override {
    G1TestSubTask::worker_cost();
    return 2.0;
  }
};

class G1TestBatchedTask : public G1BatchedTask {
public:
  G1TestBatchedTask() : G1BatchedTask("Batched Test Task", nullptr) {
    add_serial_task(new G1SerialTestSubTask());
    add_parallel_task(new G1ParallelTestSubTask());
  }
};

TEST_VM(G1BatchedTask, check) {
  G1TestBatchedTask task;
  uint tasks = task.num_workers_estimate();
  ASSERT_EQ(tasks, 3u);
  task.set_max_workers(G1BatchedTaskWorkers::MaxWorkers);
  G1BatchedTaskWorkers::run_task(&task);
}
