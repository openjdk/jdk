/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/gcLocker.hpp"
#include "gc/shared/gcTrace.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "logging/log.hpp"
#include "runtime/atomic.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaThread.inline.hpp"
#include "runtime/safepoint.hpp"
#include "utilities/spinYield.hpp"
#include "runtime/threadSMR.hpp"
#include "utilities/ticks.hpp"

// GCLockerTimingDebugLogger tracks specific timing information for GC lock waits.
class GCLockerTimingDebugLogger : public StackObj {
  const char* _log_message;
  Ticks _start;

public:
  GCLockerTimingDebugLogger(const char* log_message) : _log_message(log_message) {
    assert(_log_message != nullptr, "GC locker debug message must be set.");
    if (log_is_enabled(Debug, gc, jni)) {
      _start = Ticks::now();
    }
  }

  ~GCLockerTimingDebugLogger() {
    Log(gc, jni) log;
    if (log.is_debug()) {
      ResourceMark rm; // JavaThread::name() allocates to convert to UTF8
      const Tickspan elapsed_time = Ticks::now() - _start;
      log.debug("%s Resumed after " UINT64_FORMAT "ms. Thread \"%s\".", _log_message, elapsed_time.milliseconds(), Thread::current()->name());
    }
  }
};

Monitor* GCLocker::_lock;
volatile bool GCLocker::_is_gc_request_pending;

DEBUG_ONLY(uint64_t GCLocker::_debug_count;)

void GCLocker::initialize() {
  assert(Heap_lock != nullptr, "inv");
  _lock = Heap_lock;
  _is_gc_request_pending = false;

  DEBUG_ONLY(_debug_count = 0;)
}

bool GCLocker::is_active() {
  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *cur = jtiwh.next(); /* empty */) {
    if (cur->in_critical()) {
      return true;
    }
  }
  return false;
}

void GCLocker::block() {
  assert(_lock->is_locked(), "precondition");
  assert(Atomic::load(&_is_gc_request_pending) == false, "precondition");

  GCLockerTimingDebugLogger logger("Thread blocked to start GC.");

  Atomic::store(&_is_gc_request_pending, true);

  OrderAccess::storeload();

  JavaThread* java_thread = JavaThread::current();
  ThreadBlockInVM tbivm(java_thread);

  // Wait for threads leaving critical section
  SpinYield spin_yield;
  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *cur = jtiwh.next(); /* empty */) {
    while (cur->in_critical_atomic()) {
      spin_yield.wait();
    }
  }

#ifdef ASSERT
  // Matching the storestore in GCLocker::exit
  OrderAccess::loadload();
  assert(Atomic::load(&_debug_count) == 0, "inv");
#endif
}

void GCLocker::unblock() {
  assert(_lock->is_locked(), "precondition");
  assert(Atomic::load(&_is_gc_request_pending) == true, "precondition");

  Atomic::store(&_is_gc_request_pending, false);
}

void GCLocker::enter_slow(JavaThread* thread) {
  GCLockerTimingDebugLogger logger("Thread blocked to enter critical region.");
  while (true) {
    {
      // There is a pending gc request and _lock is locked. Wait for the
      // completion of a gc. It's enough to do an empty locker section.
      MutexLocker locker(_lock);
    }

    thread->enter_critical();
    OrderAccess::storeload();
    if (!Atomic::load(&_is_gc_request_pending)) {
      return;
    }

    thread->exit_critical();
  }
}
