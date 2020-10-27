/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1SERVICETHREAD_HPP
#define SHARE_GC_G1_G1SERVICETHREAD_HPP

#include "gc/shared/concurrentGCThread.hpp"
#include "runtime/mutex.hpp"

class G1ServiceTask : public CHeapObj<mtGC> {
  // The next absolute time this task should be executed.
  double _time;
  // Name of the task.
  const char* _name;
  // Next task in the task queue.
  G1ServiceTask* _next;

public:
  G1ServiceTask(const char* name);
  const char* name();

  void set_time(double time);
  double time();

  void set_next(G1ServiceTask* next);
  G1ServiceTask* next();

  // Do the actual work for the task.
  virtual void execute() = 0;
  // Delay to the next invocation.
  virtual uint64_t delay_ms() = 0;
  // Return if the task should be rescheduled or not.
  virtual bool should_reschedule() = 0;
};

class G1SentinelTask : public G1ServiceTask {
public:
  G1SentinelTask();
  virtual void execute();
  virtual uint64_t delay_ms();
  virtual bool should_reschedule();
};

class G1ServiceTaskQueue {
  // The sentinel task is the entry point of this priority queue holding the
  // service tasks. The queue is ordered by the time the tasks are scheduled
  // to run and the sentinel task has the time set to DBL_MAX. This guarantees
  // that any new task will be added just before the sentinel at the latest.
  G1SentinelTask _sentinel;

  // Verify that the queue is ordered.
  void verify_task_queue() NOT_DEBUG_RETURN;
public:
  G1ServiceTaskQueue();
  G1ServiceTask* pop();
  G1ServiceTask* peek();
  void add_ordered(G1ServiceTask* task);
  bool is_empty();
};

// The G1ServiceThread is used to periodically do a number of different tasks:
//   - re-assess the validity of the prediction for the
//     remembered set lengths of the young generation.
//   - check if a periodic GC should be scheduled.
class G1ServiceThread: public ConcurrentGCThread {
  // The monitor is used to ensure thread safety for the task queue
  // and allow other threads to signal the service thread to wake up.
  Monitor _monitor;
  G1ServiceTaskQueue _task_queue;

  double _vtime_accum;  // Accumulated virtual time.

  void run_service();
  void stop_service();

  // Returns the time in milliseconds until the next task is due.
  // Used both to determine if there are tasks ready to run and
  // how long to sleep when nothing is ready.
  int64_t time_to_next_task_ms();
  void sleep_before_next_cycle();

  G1ServiceTask* pop_due_task();
  void run_task(G1ServiceTask* task);
  void reschedule_task(G1ServiceTask* task);

public:
  G1ServiceThread();
  double vtime_accum() { return _vtime_accum; }
  void register_task(G1ServiceTask* task);
};

#endif // SHARE_GC_G1_G1SERVICETHREAD_HPP
