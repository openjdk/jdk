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
  // The next time this task should be executed.
  double _time;
  // Name of the task.
  const char* _name;
  // Next task in the task list.
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
  // Timeout to the next invocation. A negative value can be used
  // to stop the task from being rescheduled and run again.
  virtual int64_t timeout_ms() = 0;
};

class G1SentinelTask : public G1ServiceTask {
public:
  G1SentinelTask();
  virtual void execute();
  virtual int64_t timeout_ms();
};

class G1ServiceTaskList {
  // The sentinel task is the entry point of this ordered circular list holding
  // the service tasks. The list is ordered by the time the tasks are scheduled
  // to run and the sentinel task has the time set to DBL_MAX. This guarantees
  // that any new task will be added just before the sentinel at the latest.
  G1SentinelTask _sentinel;

  // Verify that the list is ordered.
  void verify_task_list() NOT_DEBUG_RETURN;
public:
  G1ServiceTaskList();
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
private:
  // The monitor is used to ensure thread saftey for the task list
  // and allow other threads to signal the service thread to wake up.
  Monitor _monitor;
  G1ServiceTaskList _task_list;

  double _vtime_accum;  // Accumulated virtual time.

  void run_service();
  void stop_service();

  int64_t sleep_time();
  void sleep_before_next_cycle();

  G1ServiceTask* pop_due_task();
  void run_task(G1ServiceTask* task);
  void run_tasks();
  void reschedule_task(G1ServiceTask* task);

public:
  G1ServiceThread();
  double vtime_accum() { return _vtime_accum; }
  void register_task(G1ServiceTask* task);
};

#endif // SHARE_GC_G1_G1SERVICETHREAD_HPP
