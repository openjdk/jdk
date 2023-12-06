/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 */

#include "precompiled.hpp"
#include "gc/shared/workerThread.hpp"
#include "memory/universe.hpp"
#include "utilities/spinYield.hpp"
#include "unittest.hpp"


class ParallelTask : public WorkerTask {
protected:
  const uint _expected_workers;
  volatile uint _actual_workers;
  volatile uint _actual_ids_bitset;
  const Thread* _caller_thread;
  bool _seen_caller;

public:
  ParallelTask(int expected_workers, bool can_caller_execute) :
    WorkerTask("Parallel Task", can_caller_execute),
    _expected_workers(expected_workers),
    _actual_workers(0),
    _actual_ids_bitset(0),
    _caller_thread(Thread::current()),
    _seen_caller(false)
    {};

  void record_worker(uint worker_id) {
    if (!_seen_caller && Thread::current() == _caller_thread) {
      _seen_caller = true;
    }
    while (true) {
      uint cur_ids = Atomic::load(&_actual_ids_bitset);
      uint new_ids = cur_ids | (1 << worker_id);
      if (cur_ids == new_ids) {
        return;
      }
      if (Atomic::cmpxchg(&_actual_ids_bitset, cur_ids, new_ids) == cur_ids) {
        return;
      }
    }
  }

  void work(uint worker_id) {
    record_worker(worker_id);

    Atomic::inc(&_actual_workers);
    SpinYield sp;
    while (Atomic::load(&_actual_workers) < _expected_workers) {
      sp.wait();
    }
  }

  uint actual_workers() {
    return Atomic::load(&_actual_workers);
  }

  uint actual_ids_bitset() {
    return Atomic::load(&_actual_ids_bitset);
  }

  bool seen_caller() {
    return _seen_caller;
  }
};

static uint expected_ids_bitset(int expected_workers) {
  return (1 << expected_workers) - 1;
}

TEST_VM(WorkerThreads, basic) {
  static const int TRIES = 1000;
  static const uint max_workers = 4;
  static const uint half_workers = max_workers / 2;

  WorkerThreads* workers = new WorkerThreads("test", max_workers);
  workers->initialize_workers();

  // Full parallelism
  workers->set_active_workers(max_workers);
  for (int t = 0; t < TRIES; t++) {
    ParallelTask task(max_workers, false);
    workers->run_task(&task);
    EXPECT_EQ(max_workers, task.actual_workers());
    EXPECT_EQ(expected_ids_bitset(max_workers), task.actual_ids_bitset());
    EXPECT_FALSE(task.seen_caller());
  }

  // Full parallelism, can execute in caller
  workers->set_active_workers(max_workers);
  for (int t = 0; t < TRIES; t++) {
    ParallelTask task(max_workers, true);
    workers->run_task(&task);
    EXPECT_EQ(max_workers, task.actual_workers());
    EXPECT_EQ(expected_ids_bitset(max_workers), task.actual_ids_bitset());
  }

  // Half parallelism
  workers->set_active_workers(half_workers);
  for (int t = 0; t < TRIES; t++) {
    ParallelTask task(half_workers, false);
    workers->run_task(&task);
    EXPECT_EQ(half_workers, task.actual_workers());
    EXPECT_EQ(expected_ids_bitset(half_workers), task.actual_ids_bitset());
    EXPECT_FALSE(task.seen_caller());
  }

  // Half parallelism, can execute in caller
  workers->set_active_workers(half_workers);
  for (int t = 0; t < TRIES; t++) {
    ParallelTask task(half_workers, true);
    workers->run_task(&task);
    EXPECT_EQ(half_workers, task.actual_workers());
    EXPECT_EQ(expected_ids_bitset(half_workers), task.actual_ids_bitset());
  }

  // Lowest parallelism
  workers->set_active_workers(1);
  for (int t = 0; t < TRIES; t++) {
    ParallelTask task(1, false);
    workers->run_task(&task);
    EXPECT_EQ(1u, task.actual_workers());
    EXPECT_EQ(expected_ids_bitset(1), task.actual_ids_bitset());
    EXPECT_FALSE(task.seen_caller());
  }

  // Lowest parallelism, can execute in caller
  workers->set_active_workers(1);
  for (int t = 0; t < TRIES; t++) {
    ParallelTask task(1, true);
    workers->run_task(&task);
    EXPECT_EQ(1u, task.actual_workers());
    EXPECT_EQ(expected_ids_bitset(1), task.actual_ids_bitset());
  }
}
