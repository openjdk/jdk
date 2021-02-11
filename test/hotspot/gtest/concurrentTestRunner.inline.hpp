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
 */

#ifndef GTEST_CONCURRENT_TEST_RUNNER_INLINE_HPP
#define GTEST_CONCURRENT_TEST_RUNNER_INLINE_HPP

#include "threadHelper.inline.hpp"
// #include "unittest.hpp"

class TestRunnable {
public:
  virtual void runUnitTest() {
  };
};

class UnitTestThread : public JavaTestThread {
public:
  long testDuration;
  TestRunnable* runnable;

  UnitTestThread(TestRunnable* runnableArg, Semaphore* doneArg, long testDurationArg) : JavaTestThread(doneArg) {
    runnable = runnableArg;
    testDuration = testDurationArg;
  }
  virtual ~UnitTestThread() {}

  void main_run() {
    tty->print_cr("Starting test thread");
    long stopTime = os::javaTimeMillis() + testDuration;
    while (os::javaTimeMillis() < stopTime) {
      runnable->runUnitTest();
    }
    tty->print_cr("Leaving test thread");
  }
};

class ConcurrentTestRunner {
public:
  long testDurationMillis;
  int nrOfThreads;
  TestRunnable* unitTestRunnable;

  ConcurrentTestRunner(TestRunnable* runnableArg, int nrOfThreadsArg, long testDurationMillisArg) {
    unitTestRunnable = runnableArg;
    nrOfThreads = nrOfThreadsArg;
    testDurationMillis = testDurationMillisArg;
  }

  virtual ~ConcurrentTestRunner() {}

  void run() {
    Semaphore done(0);

    UnitTestThread* t[nrOfThreads];
    for (int i = 0; i < nrOfThreads; i++) {
      t[i] = new UnitTestThread(unitTestRunnable, &done, testDurationMillis);
    }

    for (int i = 0; i < nrOfThreads; i++) {
      t[i]->doit();
    }

    for (int i = 0; i < nrOfThreads; i++) {
      done.wait();
    }
  }
};

#endif // include guard
