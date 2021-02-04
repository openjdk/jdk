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

#include "precompiled.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "threadHelper.inline.hpp"
#include "unittest.hpp"

#define TEST_THREAD_COUNT 10 // TODO: update to original value of 30
#define TEST_DURATION 15000 // milliSeconds


class UnitTestThread : public JavaTestThread {
  public:
  UnitTestThread(Semaphore* post) : JavaTestThread(post) {}
  virtual ~UnitTestThread() {}

  void run_unit_test() {
    os::naked_short_sleep(100);
  }

  void main_run() {
    long stopTime = os::javaTimeMillis() + TEST_DURATION;

    int iteration = 0;
    tty->print_cr("Start: TestIteration: %d ", iteration);
    while (os::javaTimeMillis() < stopTime) {
      tty->print_cr("TestIteration: %d ", iteration);
      run_unit_test();
      iteration++;
    }
  }
};


class DriverThread : public JavaTestThread {
public:
  Semaphore _done;
  DriverThread(Semaphore* post) : JavaTestThread(post) { };
  virtual ~DriverThread(){}

  void main_run() {
    Semaphore done(0);


    UnitTestThread* st[TEST_THREAD_COUNT];
    for (int i = 0; i < TEST_THREAD_COUNT; i++) {
      st[i] = new UnitTestThread(&done);
    }

    for (int i = 0; i < TEST_THREAD_COUNT; i++) {
      st[i]->doit();
    }

    for (int i = 0; i < TEST_THREAD_COUNT; i++) {
      done.wait();
    }
  }
};

TEST_VM(ReservedSpaceConcurrent, test_concurrent_threads) {
  mt_test_doer<DriverThread>();
}
