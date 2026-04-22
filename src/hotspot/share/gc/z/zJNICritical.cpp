/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/z/zJNICritical.hpp"
#include "gc/z/zLock.inline.hpp"
#include "gc/z/zStat.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/thread.inline.hpp"
#include "utilities/debug.hpp"

//
// The JNI critical count reflects number of Java threads currently
// inside a JNI critical region.
//
// * Normal (count >= 0). Java threads are allowed to enter and exit
//   a critical region.
//
// * Blocked (count == -1). No Java thread is inside a critical region,
//   and no Java thread can enter a critical region.
//
// * Block in progress (count < -1). Java threads are only allowed
//   to exit a critical region. Attempts to enter a critical region
//   will be blocked.
//

static const ZStatCriticalPhase ZCriticalPhaseJNICriticalStall("JNI Critical Stall", false /* verbose */);

Atomic<int64_t> ZJNICritical::_count;
ZConditionLock* ZJNICritical::_lock;

void ZJNICritical::initialize() {
  precond(_count.load_relaxed() == 0);
  _lock = new ZConditionLock();
}

void ZJNICritical::block() {
  for (;;) {
    const int64_t count = _count.load_acquire();

    if (count < 0) {
      // Already blocked, wait until unblocked
      ZLocker<ZConditionLock> locker(_lock);
      while (_count.load_acquire() < 0) {
        _lock->wait();
      }

      // Unblocked
      continue;
    }

    // Increment and invert count
    if (!_count.compare_set(count, -(count + 1))) {
      continue;
    }

    // If the previous count was 0, then we just incremented and inverted
    // it to -1 and we have now blocked. Otherwise we wait until all Java
    // threads have exited the critical region.
    if (count != 0) {
      // Wait until blocked
      ZLocker<ZConditionLock> locker(_lock);
      while (_count.load_acquire() != -1) {
        _lock->wait();
      }
    }

    // Blocked
    return;
  }
}

void ZJNICritical::unblock() {
  const int64_t count = _count.load_acquire();
  assert(count == -1, "Invalid count");

  // Notify unblocked
  ZLocker<ZConditionLock> locker(_lock);
  _count.release_store(0);
  _lock->notify_all();
}

void ZJNICritical::enter_inner(JavaThread* thread) {
  for (;;) {
    const int64_t count = _count.load_acquire();

    if (count < 0) {
      // Wait until unblocked
      ZStatTimer timer(ZCriticalPhaseJNICriticalStall);

      // Transition thread to blocked before locking to avoid deadlock
      ThreadBlockInVM tbivm(thread);

      ZLocker<ZConditionLock> locker(_lock);
      while (_count.load_acquire() < 0) {
        _lock->wait();
      }

      // Unblocked
      continue;
    }

    // Increment count
    if (!_count.compare_set(count, count + 1)) {
      continue;
    }

    // Entered critical region
    return;
  }
}

void ZJNICritical::enter(JavaThread* thread) {
  assert(thread == JavaThread::current(), "Must be this thread");

  if (!thread->in_critical()) {
    enter_inner(thread);
  }

  thread->enter_critical();
}

void ZJNICritical::exit_inner() {
  for (;;) {
    const int64_t count = _count.load_acquire();
    assert(count != 0, "Invalid count");

    if (count > 0) {
      // No block in progress, decrement count
      if (!_count.compare_set(count, count - 1)) {
        continue;
      }
    } else {
      // Block in progress, increment count
      if (!_count.compare_set(count, count + 1)) {
        continue;
      }

      // If the previous count was -2, then we just incremented it to -1,
      // and we should signal that all Java threads have now exited the
      // critical region and we are now blocked.
      if (count == -2) {
        // Notify blocked
        ZLocker<ZConditionLock> locker(_lock);
        _lock->notify_all();
      }
    }

    // Exited critical region
    return;
  }
}

void ZJNICritical::exit(JavaThread* thread) {
  assert(thread == JavaThread::current(), "Must be this thread");

  thread->exit_critical();

  if (!thread->in_critical()) {
    exit_inner();
  }
}
