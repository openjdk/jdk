/*
 * Copyright (c) 2001, 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/workerUtils.hpp"
#include "runtime/atomic.hpp"
#include "runtime/mutexLocker.hpp"

// *** WorkerThreadsBarrierSync

WorkerThreadsBarrierSync::WorkerThreadsBarrierSync()
  : _monitor(Mutex::nosafepoint, "WorkerThreadsBarrierSync_lock"),
    _n_workers(0), _n_completed(0), _should_reset(false), _aborted(false) {
}

void WorkerThreadsBarrierSync::set_n_workers(uint n_workers) {
  _n_workers    = n_workers;
  _n_completed  = 0;
  _should_reset = false;
  _aborted      = false;
}

bool WorkerThreadsBarrierSync::enter() {
  MonitorLocker ml(monitor(), Mutex::_no_safepoint_check_flag);
  if (should_reset()) {
    // The should_reset() was set and we are the first worker to enter
    // the sync barrier. We will zero the n_completed() count which
    // effectively resets the barrier.
    zero_completed();
    set_should_reset(false);
  }
  inc_completed();
  if (n_completed() == n_workers()) {
    // At this point we would like to reset the barrier to be ready in
    // case it is used again. However, we cannot set n_completed() to
    // 0, even after the notify_all(), given that some other workers
    // might still be waiting for n_completed() to become ==
    // n_workers(). So, if we set n_completed() to 0, those workers
    // will get stuck (as they will wake up, see that n_completed() !=
    // n_workers() and go back to sleep). Instead, we raise the
    // should_reset() flag and the barrier will be reset the first
    // time a worker enters it again.
    set_should_reset(true);
    ml.notify_all();
  } else {
    while (n_completed() != n_workers() && !aborted()) {
      ml.wait();
    }
  }
  return !aborted();
}

void WorkerThreadsBarrierSync::abort() {
  MutexLocker x(monitor(), Mutex::_no_safepoint_check_flag);
  set_aborted();
  monitor()->notify_all();
}

// SubTasksDone functions.

SubTasksDone::SubTasksDone(uint n) :
  _tasks(NULL), _n_tasks(n) {
  _tasks = NEW_C_HEAP_ARRAY(bool, n, mtInternal);
  for (uint i = 0; i < _n_tasks; i++) {
    _tasks[i] = false;
  }
}

#ifdef ASSERT
void SubTasksDone::all_tasks_claimed_impl(uint skipped[], size_t skipped_size) {
  if (Atomic::cmpxchg(&_verification_done, false, true)) {
    // another thread has done the verification
    return;
  }
  // all non-skipped tasks are claimed
  for (uint i = 0; i < _n_tasks; ++i) {
    if (!_tasks[i]) {
      auto is_skipped = false;
      for (size_t j = 0; j < skipped_size; ++j) {
        if (i == skipped[j]) {
          is_skipped = true;
          break;
        }
      }
      assert(is_skipped, "%d not claimed.", i);
    }
  }
  // all skipped tasks are *not* claimed
  for (size_t i = 0; i < skipped_size; ++i) {
    auto task_index = skipped[i];
    assert(task_index < _n_tasks, "Array in range.");
    assert(!_tasks[task_index], "%d is both claimed and skipped.", task_index);
  }
}
#endif

bool SubTasksDone::try_claim_task(uint t) {
  assert(t < _n_tasks, "bad task id.");
  return !_tasks[t] && !Atomic::cmpxchg(&_tasks[t], false, true);
}

SubTasksDone::~SubTasksDone() {
  assert(_verification_done, "all_tasks_claimed must have been called.");
  FREE_C_HEAP_ARRAY(bool, _tasks);
}

// *** SequentialSubTasksDone

bool SequentialSubTasksDone::try_claim_task(uint& t) {
  t = _num_claimed;
  if (t < _num_tasks) {
    t = Atomic::add(&_num_claimed, 1u) - 1;
  }
  return t < _num_tasks;
}
