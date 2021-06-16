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
#include "runtime/atomic.hpp"
#include "runtime/os.hpp"
#include "runtime/thread.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "threadHelper.inline.hpp"
#include "unittest.hpp"

// Timing controller, might need bigger barriers
class Control : public AllStatic {
  static bool _suspend_done;
  static bool _block_done;
 public:
  static bool suspend_done() { return Atomic::load(&_suspend_done); }
  static bool block_done() { return Atomic::load(&_block_done); }
  static void set_suspend_done() { Atomic::store(&_suspend_done, true); }
  static void set_block_done() { Atomic::store(&_block_done, true); }
};

bool Control::_suspend_done = false;
bool Control::_block_done = false;

class BlockeeThread : public JavaTestThread {
  public:
  BlockeeThread(Semaphore* post) : JavaTestThread(post) {}
  virtual ~BlockeeThread() {}
  void main_run() {
    while (!Control::suspend_done()) {
      ThreadBlockInVM tbivm(this);
    }
  }
};

class BlockingThread : public JavaTestThread {
  JavaThread* _target;
  public:
  BlockingThread(Semaphore* post, JavaThread* target) : JavaTestThread(post), _target(target) {}
  virtual ~BlockingThread() {}
  void main_run() {
    int print_count = 0;
    // Suspend the target thread and check its state
    while (!Control::block_done()) {
      ASSERT_LT(print_count++, 100) << "Blocking thread - never suspended";
      if (_target->block_suspend(this)) {
        tty->print_cr("Block succeeded");
        Control::set_block_done();
        os::naked_short_sleep(10);
         while (!Control::suspend_done()) {
           ASSERT_EQ(_target->thread_state(), _thread_blocked) << "should be blocked";
        }
        _target->continue_resume(this);
        tty->print_cr("Release succeeded");
      }
    }
  }
};

class SuspendingThread : public JavaTestThread {
  JavaThread* _target;
  public:
  SuspendingThread(Semaphore* post, JavaThread* target) : JavaTestThread(post), _target(target) {}
  virtual ~SuspendingThread() {}
  void main_run() {
    int print_count = 0;
    int test_count = 0;
    // Suspend the target thread and resume it
    while (test_count < 100) {
      ASSERT_LT(print_count++, 100) << "Suspending thread - never suspended";
      if (_target->java_suspend()) {
        ASSERT_EQ(_target->thread_state(), _thread_blocked) << "should be blocked";
        _target->java_resume();
        test_count++;
      }
    }
    // Still blocked until Blocking thread resumes the thread
    ASSERT_EQ(_target->thread_state(), _thread_blocked) << "should still be blocked";
    Control::set_suspend_done();
  }
};

// This guy should fail, then pass.
class AnotherBlockingThread : public JavaTestThread {
  JavaThread* _target;
  public:
  AnotherBlockingThread(Semaphore* post, JavaThread* target) : JavaTestThread(post), _target(target) {}
  virtual ~AnotherBlockingThread() {}
  void main_run() {
    bool done = false;
    // Suspend the target thread and check its state
    while (!Control::block_done()) {
      os::naked_short_sleep(10);
    }
    while (!done) {
      if (_target->block_suspend(this)) {
        ASSERT_EQ(Control::suspend_done(), true) << "should only pass if Blocking thread releases the block";
        tty->print_cr("Other Block succeeded");
        _target->continue_resume(this);
        tty->print_cr("Other Release succeeded");
        done = true;
      }
    }
  }
};

#define TEST_THREAD_COUNT 4

class DriverSuspendThread : public JavaTestThread {
public:
  Semaphore _done;
  DriverSuspendThread(Semaphore* post) : JavaTestThread(post) { };
  virtual ~DriverSuspendThread(){}

  void main_run() {
    Semaphore done(0);

    BlockeeThread* target = new BlockeeThread(&done);
    BlockingThread* bt = new BlockingThread(&done, target);
    SuspendingThread* st = new SuspendingThread(&done, target);
    AnotherBlockingThread* obt = new AnotherBlockingThread(&done, target);

    target->doit();
    bt->doit();
    st->doit();
    obt->doit();

    for (int i = 0; i < TEST_THREAD_COUNT; i++) {
      done.wait();
    }
  }
};

TEST_VM(ThreadSuspend, test_thread_suspend) {
  mt_test_doer<DriverSuspendThread>();
}
