/*
 * Copyright (c) 2002, 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_WORKERUTILS_HPP
#define SHARE_GC_SHARED_WORKERUTILS_HPP

#include "memory/allocation.hpp"
#include "metaprogramming/enableIf.hpp"
#include "metaprogramming/logical.hpp"
#include "runtime/mutex.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

// A class that acts as a synchronisation barrier. Workers enter
// the barrier and must wait until all other workers have entered
// before any of them may leave.

class WorkerThreadsBarrierSync : public StackObj {
protected:
  Monitor _monitor;
  uint    _n_workers;
  uint    _n_completed;
  bool    _should_reset;
  bool    _aborted;

  Monitor* monitor()        { return &_monitor; }
  uint     n_workers()      { return _n_workers; }
  uint     n_completed()    { return _n_completed; }
  bool     should_reset()   { return _should_reset; }
  bool     aborted()        { return _aborted; }

  void     zero_completed() { _n_completed = 0; }
  void     inc_completed()  { _n_completed++; }
  void     set_aborted()    { _aborted = true; }
  void     set_should_reset(bool v) { _should_reset = v; }

public:
  WorkerThreadsBarrierSync();

  // Set the number of workers that will use the barrier.
  // Must be called before any of the workers start running.
  void set_n_workers(uint n_workers);

  // Enter the barrier. A worker that enters the barrier will
  // not be allowed to leave until all other threads have
  // also entered the barrier or the barrier is aborted.
  // Returns false if the barrier was aborted.
  bool enter();

  // Aborts the barrier and wakes up any threads waiting for
  // the barrier to complete. The barrier will remain in the
  // aborted state until the next call to set_n_workers().
  void abort();
};

// A class to manage claiming of subtasks within a group of tasks.  The
// subtasks will be identified by integer indices, usually elements of an
// enumeration type.

class SubTasksDone: public CHeapObj<mtInternal> {
  volatile bool* _tasks;
  uint _n_tasks;

  // make sure verification logic is run exactly once to avoid duplicate assertion failures
  DEBUG_ONLY(volatile bool _verification_done = false;)
  void all_tasks_claimed_impl(uint skipped[], size_t skipped_size) NOT_DEBUG_RETURN;

  NONCOPYABLE(SubTasksDone);

public:
  // Initializes "this" to a state in which there are "n" tasks to be
  // processed, none of the which are originally claimed.
  SubTasksDone(uint n);

  // Attempt to claim the task "t", returning true if successful,
  // false if it has already been claimed.  The task "t" is required
  // to be within the range of "this".
  bool try_claim_task(uint t);

  // The calling thread asserts that it has attempted to claim all the tasks
  // that it will try to claim.  Tasks that are meant to be skipped must be
  // explicitly passed as extra arguments. Every thread in the parallel task
  // must execute this.
  template<typename T0, typename... Ts,
          ENABLE_IF(Conjunction<std::is_same<T0, Ts>...>::value)>
  void all_tasks_claimed(T0 first_skipped, Ts... more_skipped) {
    static_assert(std::is_convertible<T0, uint>::value, "not convertible");
    uint skipped[] = { static_cast<uint>(first_skipped), static_cast<uint>(more_skipped)... };
    all_tasks_claimed_impl(skipped, ARRAY_SIZE(skipped));
  }
  // if there are no skipped tasks.
  void all_tasks_claimed() {
    all_tasks_claimed_impl(nullptr, 0);
  }

  // Destructor.
  ~SubTasksDone();
};

// As above, but for sequential tasks, i.e. instead of claiming
// sub-tasks from a set (possibly an enumeration), claim sub-tasks
// in sequential order. This is ideal for claiming dynamically
// partitioned tasks (like striding in the parallel remembered
// set scanning).

class SequentialSubTasksDone : public CHeapObj<mtInternal> {

  uint _num_tasks;     // Total number of tasks available.
  volatile uint _num_claimed;   // Number of tasks claimed.

  NONCOPYABLE(SequentialSubTasksDone);

public:
  SequentialSubTasksDone(uint num_tasks) : _num_tasks(num_tasks), _num_claimed(0) { }
  ~SequentialSubTasksDone() {
    // Claiming may try to claim more tasks than there are.
    assert(_num_claimed >= _num_tasks, "Claimed %u tasks of %u", _num_claimed, _num_tasks);
  }

  // Attempt to claim the next unclaimed task in the sequence,
  // returning true if successful, with t set to the index of the
  // claimed task. Returns false if there are no more unclaimed tasks
  // in the sequence. In this case t is undefined.
  bool try_claim_task(uint& t);
};

#endif // SHARE_GC_SHARED_WORKERUTILS_HPP
