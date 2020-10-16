/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1ServiceThread.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/os.hpp"
#include "utilities/autoRestore.hpp"
#include "unittest.hpp"

class CheckTask : public G1ServiceTask {
  G1ServiceThread* _st;
  int _execution_count;
  double _timeout;
public:
  CheckTask(const char* name, G1ServiceThread* st) :
      G1ServiceTask(name),
      _st(st),
      _execution_count(0),
      _timeout(0.1) { }
  virtual void execute() { _execution_count++; }
  virtual double timeout() { return _timeout; }
  int execution_count() { return _execution_count;}
  void set_timeout(double timeout) { _timeout = timeout; }
};

static void stop_service_thread(G1ServiceThread* thread) {
  ThreadInVMfromNative tvn(JavaThread::current());
  thread->stop();
}

// Test that a task that is added during runtime gets run.
TEST_VM(G1ServiceThread, test_add) {
  // Create thread and let it start.
  G1ServiceThread* st = new G1ServiceThread();
  os::naked_short_sleep(500);

  CheckTask ct("AddAndRun", st);
  st->register_task(&ct);

  // Give CheckTask time to run.
  os::naked_short_sleep(500);
  stop_service_thread(st);

  ASSERT_GT(ct.execution_count(), 0);
}

// Test that a task that is added while the service thread is
// waiting gets run in a timely manner.
TEST_VM(G1ServiceThread, test_add_while_waiting) {
  // Make sure default tasks use long intervals.
  AutoModifyRestore<uintx> f1(G1PeriodicGCInterval, 100000);
  AutoModifyRestore<uintx> f2(G1ConcRefinementServiceIntervalMillis, 100000);

  // Create thread and let it start.
  G1ServiceThread* st = new G1ServiceThread();
  os::naked_short_sleep(500);

  CheckTask ct("AddWhileWaiting", st);
  st->register_task(&ct);

  // Give CheckTask time to run.
  os::naked_short_sleep(500);
  stop_service_thread(st);

  ASSERT_GT(ct.execution_count(), 0);
}

// Test that a task with negative timeout is not rescheduled.
TEST_VM(G1ServiceThread, test_add_run_once) {
  // Create thread and let it start.
  G1ServiceThread* st = new G1ServiceThread();
  os::naked_short_sleep(500);

  // Negative timeout to avoid rescheduling.
  CheckTask ct("AddRunOnce", st);
  ct.set_timeout(-1);
  st->register_task(&ct);

  // Give CheckTask time to run.
  os::naked_short_sleep(500);
  stop_service_thread(st);

  // Should be exactly 1 since negative timeout should
  // prevent rescheduling.
  ASSERT_EQ(ct.execution_count(), 1);
}

class TestTask : public G1ServiceTask {
  double _timeout;
public:
  TestTask(double timeout) :
      G1ServiceTask("TestTask"),
      _timeout(timeout) {
    set_time(timeout);
  }
  virtual void execute() { }
  virtual double timeout() { return _timeout; }
};

TEST_VM(G1ServiceTaskList, add_ordered) {
  G1ServiceTaskList list;

  int num_test_tasks = 5;
  for (int i = 1; i <= num_test_tasks; i++) {
    // Create tasks with different timeout.
    TestTask* task = new TestTask(0.1 * i);
    list.add_ordered(task);
  }

  // Now fake a run-loop, that reschedules the tasks using a
  // random multiplyer.
  for (double now = 0; now < 1000; now++) {
    // Random multiplyier is at least 1 to ensure progress.
    int multiplyer = 1 + os::random() % 10;
    while (list.peek()->time() < now) {
      G1ServiceTask* task = list.pop();
      task->execute();
      task->set_time(now + (task->timeout() * multiplyer));
      // All additions will verify that the list is sorted.
      list.add_ordered(task);
    }
  }

  while (!list.is_empty()) {
    G1ServiceTask* task = list.pop();
    delete task;
  }
}

#ifdef ASSERT
TEST_VM_ASSERT_MSG(G1ServiceTaskList, pop_empty,
    "Should never try to verify empty list") {
  G1ServiceTaskList list;
  list.pop();
}

TEST_VM_ASSERT_MSG(G1ServiceTaskList, peek_empty,
    "Should never try to verify empty list") {
  G1ServiceTaskList list;
  list.peek();
}

#endif