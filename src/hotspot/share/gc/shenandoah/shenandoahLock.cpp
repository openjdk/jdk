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

// In-flight release callback, modeled on the file-private InFlightMutexRelease in mutex.cpp and
// shared by both ShenandoahLock (the _state spin lock) and ShenandoahSimpleLock (the PlatformMonitor
// blocking lock). ThreadBlockInVMPreprocess invokes operator() after the acquiring JavaThread has
// transitioned back to _thread_in_vm but before it processes a pending safepoint, giving us the
// chance to release the just-acquired lock so the thread does not hold it across the safepoint.
// The callback does nothing until armed via arm() -- it must not release a lock it does not hold,
// which would clobber a lock held by another thread. So the owner arms it only once it actually
// holds the lock. not_released() reports whether we still hold the lock after the scope (i.e. it was
// armed and no safepoint took it away).
template<typename Lock>
class ShenandoahInFlightLockRelease {
private:
  Lock* _lock;  // non-null == armed (we hold the lock and may need to release it for a safepoint)
public:
  ShenandoahInFlightLockRelease() : _lock(nullptr) {}
  void arm(Lock* lock) {
    assert(lock != nullptr, "Must not");
    _lock = lock;
  }
  void operator()(JavaThread* current) {
    if (_lock != nullptr) {
      _lock->release_for_safepoint();
      _lock = nullptr;
    }
  }
  bool not_released() { return _lock != nullptr; }
};

void ShenandoahLock::contended_lock(bool allow_block_for_safepoint) {
  Thread* thread = Thread::current();
  if (allow_block_for_safepoint && thread->is_Java_thread()) {
    JavaThread* java_thread = JavaThread::cast(thread);
    // Acquire safepoint-aware: spin for the lock INSIDE a _thread_blocked scope so the spinning
    // thread is safepoint-safe and does not delay a pending safepoint. contended_lock_internal
    // returns true if it won _state, or false if it bailed because the poll armed. If it won but a
    // safepoint is then processed on scope exit, the in-flight-release callback drops _state so we
    // do not hold the lock across the safepoint. After the scope we retry with a cheap try_lock()
    // and only re-enter the blocked spin if that fails. Mirrors ShenandoahSimpleLock's retry loop.
    do {
      ShenandoahInFlightLockRelease<ShenandoahLock> release;
      {
        ThreadBlockInVMPreprocess tbivm(java_thread, release);
        if (contended_lock_internal<true>(java_thread)) {
          // Won the lock. Arm the release: if the destructor processes a safepoint, it drops _state.
          release.arm(this);
        }
      }
      if (release.not_released()) {
        // We won the lock and no safepoint took it away.
        return;
      }
      // Either we bailed (poll armed; the scope processed the safepoint), or we won but the lock was
      // released for the safepoint. Try a cheap re-acquire before re-entering the blocked spin.
    } while (_state.compare_exchange(unlocked, locked) != unlocked);
  } else {
    contended_lock_internal<false>(nullptr);
  }
}

template<bool ALLOW_BLOCK>
bool ShenandoahLock::contended_lock_internal(JavaThread* java_thread) {
  assert(!ALLOW_BLOCK || (java_thread != nullptr && java_thread->thread_state() == _thread_blocked),
    "Must have a blocked Java thread when allowing block.");
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
    } else if constexpr (ALLOW_BLOCK) {
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
        while (SafepointSynchronize::is_synchronizing() && !SafepointMechanism::local_poll_armed(java_thread)) {
          yield_or_sleep(yields);
        }
        if (SafepointMechanism::local_poll_armed(java_thread)) {
          return false;
        }
      }
      yield_or_sleep(yields);
    } else {
      yield_or_sleep(yields);
    }
  }
  return true;
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
  DEBUG_ONLY(_owner.store_relaxed(nullptr);)
}

void ShenandoahSimpleLock::contended_lock_for_java_thread(JavaThread* java_thread) {
  // Mirror Mutex::lock_contended for an active Java thread: block on the native monitor inside a
  // ThreadBlockInVMPreprocess so a pending safepoint can run while we wait. _lock.lock() always
  // returns holding the monitor, so we arm the in-flight release unconditionally; if a safepoint is
  // then processed on scope exit, the callback unlocks on its behalf and we retry.
  do {
    ShenandoahInFlightLockRelease<ShenandoahSimpleLock> release;
    {
      ThreadBlockInVMPreprocess tbivm(java_thread, release);
      _lock.lock();
      release.arm(this);
    }
    if (release.not_released()) {
      // The callback did not fire: no safepoint took the lock away, so we hold it.
      return;
    }
    // A safepoint was processed and the lock was released on its behalf; retry.
  } while (!_lock.try_lock());
}

void ShenandoahSimpleLock::lock(bool allow_block_for_safepoint) {
  Thread* const thread = Thread::current();
  assert(_owner.load_relaxed() != thread, "reentrant locking attempt, would deadlock");

  if (allow_block_for_safepoint && thread->is_Java_thread()) {
    // Acquire safepoint-aware: block in _thread_blocked, releasing for any safepoint that arrives
    // while we wait, and retry until we hold the lock with no safepoint pending.
    contended_lock_for_java_thread(JavaThread::cast(thread));
  } else {
    // VM/GC threads, and all callers that never request safepoint blocking (e.g. NMethod lock),
    // acquire the native monitor directly -- behavior identical to the original ShenandoahSimpleLock.
    _lock.lock();
  }

  assert(_owner.load_relaxed() == nullptr, "must not be owned");
  DEBUG_ONLY(_owner.store_relaxed(thread);)
}

void ShenandoahSimpleLock::unlock() {
  assert(_owner.load_relaxed() == Thread::current(), "sanity");
  DEBUG_ONLY(_owner.store_relaxed((Thread*)nullptr);)
  _lock.unlock();
}

void ShenandoahSimpleLock::release_for_safepoint() {
  _lock.unlock();
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
