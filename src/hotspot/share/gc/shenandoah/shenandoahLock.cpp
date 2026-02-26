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
  Thread* thread = Thread::current();
  if (allow_block_for_safepoint && thread->is_Java_thread()) {
    contended_lock_internal<true>(JavaThread::cast(thread));
  } else {
    contended_lock_internal<false>(nullptr);
  }
}

template<bool ALLOW_BLOCK>
void ShenandoahLock::contended_lock_internal(JavaThread* java_thread) {
  assert(!ALLOW_BLOCK || java_thread != nullptr, "Must have a Java thread when allowing block.");
  // Spin this much, but only on multi-processor systems.
  int ctr = os::is_MP() ? 0xFF : 0;
  int yields = 0;
  // Apply TTAS to avoid more expensive CAS calls if the lock is still held by other thread.
  while (_state.load_relaxed() == locked ||
         _state.compare_exchange(unlocked, locked) != unlocked) {
    if (ctr > 0 && !SafepointSynchronize::is_synchronizing()) {
      // Lightly contended, spin a little if no safepoint is pending.
      SpinPause();
      ctr--;
    } else if (ALLOW_BLOCK) {
      ThreadBlockInVM block(java_thread);
      if (SafepointSynchronize::is_synchronizing()) {
        // If safepoint is pending, we want to block and allow safepoint to proceed.
        // Normally, TBIVM above would block us in its destructor.
        //
        // But that blocking only happens when TBIVM knows the thread poll is armed.
        // There is a window between announcing a safepoint and arming the thread poll
        // during which trying to continuously enter TBIVM is counter-productive.
        // Under high contention, we may end up going in circles thousands of times.
        // To avoid it, we wait here until local poll is armed and then proceed
        // to TBVIM exit for blocking. We do not SpinPause, but yield to let
        // VM thread to arm the poll sooner.
        while (SafepointSynchronize::is_synchronizing() &&
               !SafepointMechanism::local_poll_armed(java_thread)) {
          yield_or_sleep(yields);
        }
      } else {
        yield_or_sleep(yields);
      }
    } else {
      yield_or_sleep(yields);
    }
  }
}

void ShenandoahLock::yield_or_sleep(int &yields) {
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
  Thread* const owner = _owner.load_relaxed();

  if (owner != thread) {
    ShenandoahSimpleLock::lock();
    _owner.store_relaxed(thread);
  }

  _count++;
}

void ShenandoahReentrantLock::unlock() {
  assert(owned_by_self(), "Invalid owner");
  assert(_count > 0, "Invalid count");

  _count--;

  if (_count == 0) {
    _owner.store_relaxed((Thread*)nullptr);
    ShenandoahSimpleLock::unlock();
  }
}

bool ShenandoahReentrantLock::owned_by_self() const {
  Thread* const thread = Thread::current();
  Thread* const owner = _owner.load_relaxed();
  return owner == thread;
}
