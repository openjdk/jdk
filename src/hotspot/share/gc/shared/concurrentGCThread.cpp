/*
 * Copyright (c) 2001, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/systemDictionary.hpp"
#include "gc/shared/concurrentGCThread.hpp"
#include "oops/instanceRefKlass.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/init.hpp"
#include "runtime/java.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/os.hpp"

ConcurrentGCThread::ConcurrentGCThread() :
  _should_terminate(false), _has_terminated(false) {
};

void ConcurrentGCThread::create_and_start(ThreadPriority prio) {
  if (os::create_thread(this, os::cgc_thread)) {
    // XXX: need to set this to low priority
    // unless "aggressive mode" set; priority
    // should be just less than that of VMThread.
    os::set_priority(this, prio);
    if (!_should_terminate) {
      os::start_thread(this);
    }
  }
}

void ConcurrentGCThread::initialize_in_thread() {
  this->set_active_handles(JNIHandleBlock::allocate_block());
  // From this time Thread::current() should be working.
  assert(this == Thread::current(), "just checking");
}

void ConcurrentGCThread::terminate() {
  assert(_should_terminate, "Should only be called on terminate request.");
  // Signal that it is terminated
  {
    MutexLockerEx mu(Terminator_lock,
                     Mutex::_no_safepoint_check_flag);
    _has_terminated = true;
    Terminator_lock->notify();
  }
}

void ConcurrentGCThread::run() {
  initialize_in_thread();
  wait_init_completed();

  run_service();

  terminate();
}

void ConcurrentGCThread::stop() {
  // it is ok to take late safepoints here, if needed
  {
    MutexLockerEx mu(Terminator_lock);
    assert(!_has_terminated,   "stop should only be called once");
    assert(!_should_terminate, "stop should only be called once");
    _should_terminate = true;
  }

  stop_service();

  {
    MutexLockerEx mu(Terminator_lock);
    while (!_has_terminated) {
      Terminator_lock->wait();
    }
  }
}
