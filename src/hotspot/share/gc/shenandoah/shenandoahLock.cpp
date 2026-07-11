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


#include "gc/shenandoah/shenandoahLock.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/os.hpp"
#include "runtime/os.inline.hpp"

void ShenandoahLock::contended_lock(bool allow_block_for_safepoint) {
  assert(!allow_block_for_safepoint || Thread::current()->is_Java_thread(), "Must be Java thread if allow for safepoint");
  if (allow_block_for_safepoint) {
    JavaThread* java_thread = JavaThread::current();
    ShenandoahInFlightLockRelease<ShenandoahLock> release;
    while (release.released()) {
      {
        ThreadBlockInVMPreprocess tbivm(java_thread, release);
        if (contended_lock_internal<true>(java_thread)) {
          // Won the lock. Arm the release: if the destructor processes a safepoint, it drops _state.
          release.arm(this);
        }
      }
    }
  } else {
    contended_lock_internal<false>(nullptr);
  }
}

inline void ShenandoahLock::yield_or_sleep(int &yields) {
  // Simple yield-sleep policy: do one 100us sleep after every N yields.
  // Tested with different values of N, and chose 3 for best performance.
  if (yields < 3) {
    os::naked_yield();
    yields++;
  } else {
    os::naked_short_nanosleep(100000);
    yields = 0;
  }
}

ShenandoahSimpleLock::ShenandoahSimpleLock() {
  assert(os::mutex_init_done(), "Too early!");
  DEBUG_ONLY(_owner.store_relaxed(nullptr);)
}

void ShenandoahSimpleLock::contended_lock_for_java_thread(JavaThread* java_thread) {
  ShenandoahInFlightLockRelease<ShenandoahSimpleLock> release;
  while (release.released()) {
    ThreadBlockInVMPreprocess tbivm(java_thread, release);
    _lock.lock();
    release.arm(this);
  }
}

template<typename Lock>
ShenandoahReentrantLock<Lock>::ShenandoahReentrantLock() :
  Lock(), _owner(nullptr), _count(0) {
}

template<typename Lock>
ShenandoahReentrantLock<Lock>::~ShenandoahReentrantLock() {
  assert(_count == 0, "Unbalance");
}

template<typename Lock>
void ShenandoahReentrantLock<Lock>::lock(bool allow_block_for_safepoint) {
  Thread* const thread = Thread::current();
  Thread* const owner = _owner.load_relaxed();

  if (owner != thread) {
    Lock::lock(allow_block_for_safepoint);
    _owner.store_relaxed(thread);
  }

  _count++;
}

template<typename Lock>
void ShenandoahReentrantLock<Lock>::unlock() {
  assert(owned_by_self(), "Invalid owner");
  assert(_count > 0, "Invalid count");

  _count--;

  if (_count == 0) {
    _owner.store_relaxed((Thread*)nullptr);
    Lock::unlock();
  }
}

template<typename Lock>
bool ShenandoahReentrantLock<Lock>::owned_by_self() const {
  Thread* const thread = Thread::current();
  Thread* const owner = _owner.load_relaxed();
  return owner == thread;
}

// Explicit template instantiation
template class ShenandoahReentrantLock<ShenandoahSimpleLock>;
template class ShenandoahReentrantLock<ShenandoahLock>;
