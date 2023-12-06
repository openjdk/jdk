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

class PerfTask : public WorkerTask {
public:
  PerfTask(bool can_caller_execute) :
    WorkerTask("Parallel Perf Task", can_caller_execute) {}

  void work(uint worker_id) {
    // Do nothing, pretend the work is very small.
  }
};

static uint expected_ids_bitset(int expected_workers) {
  return (1 << expected_workers) - 1;
}

static void basic_run_with(WorkerThreads* workers, uint num_workers, bool caller_runs, NumberSeq* stats) {
  ParallelTask task(num_workers, caller_runs);
  {
    jlong start = os::javaTimeNanos();
    workers->run_task(&task);
    stats->add(os::javaTimeNanos() - start);
  }
  EXPECT_EQ(num_workers, task.actual_workers());
  EXPECT_EQ(expected_ids_bitset(num_workers), task.actual_ids_bitset());
  if (!caller_runs) {
    EXPECT_FALSE(task.seen_caller());
  }
}

TEST_VM(WorkerThreads, basic) {
  static const int TRIES = 100000;
  static const uint max_workers = os::processor_count();
  static const uint half_workers = max_workers / 2;
  static const uint min_workers = 1;

  WorkerThreads* workers = new WorkerThreads("test", max_workers);
  workers->initialize_workers();

  NumberSeq seq_full, seq_full_caller, seq_half, seq_half_caller, seq_min, seq_min_caller;

  // Full parallelism
  workers->set_active_workers(max_workers);
  for (int t = 0; t < TRIES; t++) {
    basic_run_with(workers, max_workers, false, &seq_full);
  }
  for (int t = 0; t < TRIES; t++) {
    basic_run_with(workers, max_workers,   true, &seq_full_caller);
  }

  // Half parallelism
  workers->set_active_workers(half_workers);
  for (int t = 0; t < TRIES; t++) {
    basic_run_with(workers, half_workers, false, &seq_half);
  }
  for (int t = 0; t < TRIES; t++) {
    basic_run_with(workers, half_workers,  true, &seq_half_caller);
  }

  // Min parallelism
  workers->set_active_workers(min_workers);
  for (int t = 0; t < TRIES; t++) {
    basic_run_with(workers, min_workers,  false, &seq_min);
  }
  for (int t = 0; t < TRIES; t++) {
    basic_run_with(workers, min_workers,   true, &seq_min_caller);
  }

  tty->print_cr("Full:");
  seq_full.dump();
  tty->cr();

  tty->print_cr("Full + caller runs:");
  seq_full_caller.dump();
  tty->cr();

  tty->print_cr("Half:");
  seq_half.dump();
  tty->cr();

  tty->print_cr("Half + caller runs:");
  seq_half_caller.dump();
  tty->cr();

  tty->print_cr("Min:");
  seq_min.dump();
  tty->cr();

  tty->print_cr("Min + caller runs:");
  seq_min_caller.dump();
  tty->cr();
}
