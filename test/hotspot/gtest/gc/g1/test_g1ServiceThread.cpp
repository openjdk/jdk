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
  bool _has_executed;
public:
  CheckTask(const char* name, G1ServiceThread* st) : G1ServiceTask(name), _st(st), _has_executed(false) { }
  virtual void execute() { _has_executed = true; }
  virtual double interval() { return 10; }
  bool has_executed() { return _has_executed;}
};

// Test that a task that is added during runtime gets run.
TEST(G1ServiceThread, test_add) {
  G1ServiceThread* st = new G1ServiceThread();

  // Give the thread time to start running
  os::naked_short_sleep(999);

  CheckTask ct("CheckTask", st);
  st->register_task(&ct);

  // Give CheckTask time to run.
  os::naked_short_sleep(999);

  // Stop our service thread.
  {
    ThreadInVMfromNative tvn(JavaThread::current());
    st->stop();
  }
  ASSERT_TRUE(ct.has_executed());
}

// Test that a task that is added while the service thread is
// waiting gets run in a timely manner.
TEST(G1ServiceThread, test_add_while_waiting) {
  G1ServiceThread* st = new G1ServiceThread();

  // Make sure default tasks use long intervals.
  AutoModifyRestore<uintx> f1(G1PeriodicGCInterval, 100000);
  AutoModifyRestore<uintx> f2(G1ConcRefinementServiceIntervalMillis, 100000);

  // Give the thread time to start running
  os::naked_short_sleep(999);

  CheckTask ct("CheckTask", st);
  st->register_task(&ct);

  // Give CheckTask time to run.
  os::naked_short_sleep(999);

  // Stop our service thread.
  {
    ThreadInVMfromNative tvn(JavaThread::current());
    st->stop();
  }
  ASSERT_TRUE(ct.has_executed());
}

class TestTask : public G1ServiceTask {
  double _interval;
public:
  TestTask(const char* name, double interval) :
      G1ServiceTask(name),
      _interval(interval) {
    set_time(interval);
  }
  virtual void execute() { }
  virtual double interval() { return _interval; }
};

TEST(G1ServiceTaskList, add_ordered) {
  G1ServiceTaskList list;
  TestTask a("a", 0.1);
  list.add_ordered(&a);
  TestTask b("b", 0.2);
  list.add_ordered(&b);
  TestTask c("c", 0.3);
  list.add_ordered(&c);
  TestTask d("d", 0.4);
  list.add_ordered(&d);
  TestTask e("e", 0.5);
  list.add_ordered(&e);

  // Now fake a run-loop, that reschedules the tasks using a
  // random multiplyer.
  for (double now = 0; now < 1000; now++) {
    // Multiplyier is at least 1 to ensure progress.
    int multiplyer = 1 + os::random() % 10;
    while (list.peek_first()->time() < now) {
      G1ServiceTask* task = list.pop_first();
      task->execute();
      task->set_time(now + (task->interval() * multiplyer));
      // All additions will verify that the list is sorted.
      list.add_ordered(task);
    }
  }
}
