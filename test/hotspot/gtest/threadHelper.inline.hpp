/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef GTEST_THREADHELPER_INLINE_HPP
#define GTEST_THREADHELPER_INLINE_HPP

#include "runtime/mutex.hpp"
#include "runtime/semaphore.hpp"
#include "runtime/thread.hpp"
#include "runtime/vmThread.hpp"
#include "runtime/vmOperations.hpp"
#include "unittest.hpp"

class VM_StopSafepoint : public VM_Operation {
public:
  Semaphore* _running;
  Semaphore* _test_complete;
  VM_StopSafepoint(Semaphore* running, Semaphore* wait_for) :
    _running(running), _test_complete(wait_for) {}
  VMOp_Type type() const          { return VMOp_None; }
  Mode evaluation_mode() const    { return _no_safepoint; }
  bool is_cheap_allocated() const { return false; }
  void doit()                     { _running->signal(); _test_complete->wait(); }
};

// This class and thread keep the non-safepoint op running while we do our testing.
class VMThreadBlocker : public JavaThread {
public:
  Semaphore _ready;
  Semaphore _unblock;
  VMThreadBlocker() {}
  virtual ~VMThreadBlocker() {}
  const char* get_thread_name_string(char* buf, int buflen) const {
    return "VMThreadBlocker";
  }
  void run() {
    this->set_thread_state(_thread_in_vm);
    {
      MutexLocker ml(Threads_lock);
      Threads::add(this);
    }
    VM_StopSafepoint ss(&_ready, &_unblock);
    VMThread::execute(&ss);
  }

  // Override as JavaThread::post_run() calls JavaThread::exit which
  // expects a valid thread object oop.
  virtual void post_run() {
    Threads::remove(this);
    this->smr_delete();
  }

  void doit() {
    if (os::create_thread(this, os::os_thread)) {
      os::start_thread(this);
    } else {
      ASSERT_TRUE(false);
    }
  }
  void ready() {
    _ready.wait();
  }
  void release() {
    _unblock.signal();
  }
};

// For testing in a real JavaThread.
class JavaTestThread : public JavaThread {
public:
  Semaphore* _post;
  JavaTestThread(Semaphore* post)
    : _post(post) {
  }
  virtual ~JavaTestThread() {}

  const char* get_thread_name_string(char* buf, int buflen) const {
    return "JavaTestThread";
  }

  void pre_run() {
    this->set_thread_state(_thread_in_vm);
    {
      MutexLocker ml(Threads_lock);
      Threads::add(this);
    }
    {
      MutexLockerEx ml(SR_lock(), Mutex::_no_safepoint_check_flag);
    }
  }

  virtual void main_run() = 0;

  void run() {
    main_run();
  }

  // Override as JavaThread::post_run() calls JavaThread::exit which
  // expects a valid thread object oop. And we need to call signal.
  void post_run() {
    Threads::remove(this);
    _post->signal();
    this->smr_delete();
  }

  void doit() {
    if (os::create_thread(this, os::os_thread)) {
      os::start_thread(this);
    } else {
      ASSERT_TRUE(false);
    }
  }
};

template <typename FUNC>
class SingleTestThread : public JavaTestThread {
public:
  FUNC& _f;
  SingleTestThread(Semaphore* post, FUNC& f)
    : JavaTestThread(post), _f(f) {
  }

  virtual ~SingleTestThread(){}

  virtual void main_run() {
    _f(this);
  }
};

template <typename TESTFUNC>
static void nomt_test_doer(TESTFUNC &f) {
  Semaphore post;

  VMThreadBlocker* blocker = new VMThreadBlocker();
  blocker->doit();
  blocker->ready();

  SingleTestThread<TESTFUNC>* stt = new SingleTestThread<TESTFUNC>(&post, f);
  stt->doit();
  post.wait();

  blocker->release();
}

template <typename RUNNER>
static void mt_test_doer() {
  Semaphore post;

  VMThreadBlocker* blocker = new VMThreadBlocker();
  blocker->doit();
  blocker->ready();

  RUNNER* runner = new RUNNER(&post);
  runner->doit();
  post.wait();

  blocker->release();
}

#endif // include guard
