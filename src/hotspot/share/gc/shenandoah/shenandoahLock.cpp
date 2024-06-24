/*
 * Copyright (c) 2019, Red Hat, Inc. All rights reserved.
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

#include "runtime/os.hpp"

#include "gc/shenandoah/shenandoahLock.hpp"
#include "runtime/atomic.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/os.inline.hpp"

// These are inline variants of Thread::SpinAcquire with optional blocking in VM.

class ShenandoahNoBlockOp : public StackObj {
public:
  ShenandoahNoBlockOp(JavaThread* java_thread) {
    assert(java_thread == nullptr, "Should not pass anything");
  }
};

void ShenandoahLock::contended_lock(bool allow_block_for_safepoint) {
  Thread* thread = Thread::current();
  if (allow_block_for_safepoint && thread->is_Java_thread()) {
    contended_lock_internal<ThreadBlockInVM>(JavaThread::cast(thread));
  } else {
    contended_lock_internal<ShenandoahNoBlockOp>(nullptr);
  }
}

template<typename BlockOp>
void ShenandoahLock::contended_lock_internal(JavaThread* java_thread) {
  int ctr = 0;
  int yields = 0;
  while (Atomic::load(&_state) == locked ||
         Atomic::cmpxchg(&_state, unlocked, locked) != unlocked) {
    if ((++ctr & 0xFFF) == 0) {
      BlockOp block(java_thread);
      if (yields > 5) {
        os::naked_short_sleep(1);
      } else {
        os::naked_yield();
        yields++;
      }
    } else {
      SpinPause();
    }
  }
}

ShenandoahSimpleLock::ShenandoahSimpleLock() {
  assert(os::mutex_init_done(), "Too early!");
}

void ShenandoahSimpleLock::lock() {
  _lock.lock();
}

void ShenandoahSimpleLock::unlock() {
  _lock.unlock();
}

ShenandoahReentrantLock::ShenandoahReentrantLock() :
  ShenandoahSimpleLock(), _owner(nullptr), _count(0) {
  assert(os::mutex_init_done(), "Too early!");
}

ShenandoahReentrantLock::~ShenandoahReentrantLock() {
  assert(_count == 0, "Unbalance");
}

void ShenandoahReentrantLock::lock() {
  Thread* const thread = Thread::current();
  Thread* const owner = Atomic::load(&_owner);

  if (owner != thread) {
    ShenandoahSimpleLock::lock();
    Atomic::store(&_owner, thread);
  }

  _count++;
}

void ShenandoahReentrantLock::unlock() {
  assert(owned_by_self(), "Invalid owner");
  assert(_count > 0, "Invalid count");

  _count--;

  if (_count == 0) {
    Atomic::store(&_owner, (Thread*)nullptr);
    ShenandoahSimpleLock::unlock();
  }
}

bool ShenandoahReentrantLock::owned_by_self() const {
  Thread* const thread = Thread::current();
  Thread* const owner = Atomic::load(&_owner);
  return owner == thread;
}
