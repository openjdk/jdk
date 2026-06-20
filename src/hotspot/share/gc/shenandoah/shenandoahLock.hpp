/*
 * Copyright (c) 2017, 2019, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHLOCK_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHLOCK_HPP

#include "gc/shenandoah/shenandoahPadding.hpp"
#include "memory/allocation.hpp"
#include "runtime/atomic.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/safepoint.hpp"

// Safepoint-aware in-flight lock-release callback shared by the lock classes below; defined in the
// .cpp. Forward-declared here so the lock classes can befriend it (it needs release_for_safepoint()).
template<typename Lock> class ShenandoahInFlightLockRelease;

class ShenandoahLock {
  // Grants access to release_for_safepoint() for the in-flight release callback (defined in the .cpp).
  template<typename Lock> friend class ShenandoahInFlightLockRelease;
private:
  enum LockState { unlocked = 0, locked = 1 };

  shenandoah_padding(0);
  Atomic<LockState> _state;
  shenandoah_padding(1);
#ifdef ASSERT
  Atomic<Thread*> _owner;
  shenandoah_padding(2);
#endif

  // Spin/TTAS on _state. Returns true once the lock is acquired. When ALLOW_BLOCK is set and a
  // safepoint poll becomes armed, returns false WITHOUT acquiring, so the caller (contended_lock)
  // can block for the safepoint and retry. Runs inside the caller's _thread_blocked scope when
  // ALLOW_BLOCK, so the spinning thread is safepoint-safe and does not delay the safepoint.
  template<bool ALLOW_BLOCK>
  bool contended_lock_internal(JavaThread* java_thread);
  static void yield_or_sleep(int &yields);

  // Release _state on behalf of an arriving safepoint (in-flight release). Called from the
  // ThreadBlockInVMPreprocess callback only when contended_lock_internal had just acquired the lock
  // and a safepoint is about to be processed, so the thread does not hold the lock across the
  // safepoint. No owner/critical-section writes exist at this point (the owner is set only after
  // contended_lock returns), so a plain store is sufficient.
  void release_for_safepoint() {
    _state.store_relaxed(unlocked);
  }

public:
  ShenandoahLock() : _state(unlocked) {
    DEBUG_ONLY(_owner.store_relaxed(nullptr);)
  };

  void lock(bool allow_block_for_safepoint = false) {
    assert(_owner.load_relaxed() != Thread::current(), "reentrant locking attempt, would deadlock");

    if (_state.compare_exchange(unlocked, locked) != unlocked) {
      // 1. Java thread, and there is a pending safepoint. Dive into contended locking
      //    immediately without trying anything else, and block.
      // 2. Fast lock fails, dive into contended lock handling.
      contended_lock(allow_block_for_safepoint);
    }

    assert(_state.load_relaxed() == locked, "must be locked");
    assert(_owner.load_relaxed() == nullptr, "must not be owned");
    DEBUG_ONLY(_owner.store_relaxed(Thread::current());)
  }

  void unlock() {
    assert(_owner.load_relaxed() == Thread::current(), "sanity");
    DEBUG_ONLY(_owner.store_relaxed((Thread*)nullptr);)
    OrderAccess::fence();
    _state.store_relaxed(unlocked);
  }

  void contended_lock(bool allow_block_for_safepoint);

  // Single non-blocking TTAS attempt to acquire the lock. Returns true iff this call won it. Does
  // not set _owner; the caller does that. Used by contended_lock to re-acquire after a safepoint.
  bool try_lock() {
    bool acquired = _state.compare_exchange(unlocked, locked) == unlocked;
#ifdef ASSERT
    if (acquired) {
      assert(_state.load_relaxed() == locked, "must be locked");
      assert(_owner.load_relaxed() == nullptr, "must not be owned");
      DEBUG_ONLY(_owner.store_relaxed(Thread::current());)
    }
#endif
    return acquired;
  }

  bool owned_by_self() {
#ifdef ASSERT
    return _state.load_relaxed() == locked && _owner.load_relaxed() == Thread::current();
#else
    ShouldNotReachHere();
    return false;
#endif
  }
};

// Blocking lock backed by a PlatformMonitor: a contended waiter parks on the native monitor instead
// of busy-spinning like ShenandoahLock. When used as the heap lock, a JavaThread that passes
// allow_block_for_safepoint acquires in a safepoint-aware way -- it blocks in _thread_blocked (so a
// pending safepoint is not stalled) and, if a safepoint becomes pending while it is acquiring,
// releases the lock on the safepoint's behalf and retries afterward. This mirrors HotSpot's
// Mutex::lock_contended (ThreadBlockInVMPreprocess + in-flight release), but is a Shenandoah-private
// lock so it is exempt from the Mutex rank model: the heap lock is taken by JavaThreads both with
// and without a safepoint check, which no single Mutex rank permits.
//
// Callers that never pass allow_block_for_safepoint (e.g. ShenandoahNMethodLock) keep the plain
// blocking behavior unchanged -- they always take the direct _lock.lock() path below.
class ShenandoahSimpleLock {
  // Grants access to release_for_safepoint() for the in-flight release callback (defined in the .cpp).
  template<typename Lock> friend class ShenandoahInFlightLockRelease;
private:
  PlatformMonitor   _lock; // native lock
  DEBUG_ONLY(Atomic<Thread*> _owner;)

  void contended_lock_for_java_thread(JavaThread* java_thread);

  // Release the native lock on behalf of an arriving safepoint (in-flight release). Called from the
  // ThreadBlockInVMPreprocess callback while the acquiring JavaThread is being safepointed.
  void release_for_safepoint();

public:
  ShenandoahSimpleLock();
  void lock(bool allow_block_for_safepoint = false);
  void unlock();

  bool owned_by_self() {
#ifdef ASSERT
    return _owner.load_relaxed() == Thread::current();
#else
    ShouldNotReachHere();
    return false;
#endif
  }
};

// templated reentrant lock
template<typename Lock>
class ShenandoahReentrantLock : public Lock {
private:
  Atomic<Thread*>       _owner;
  uint64_t              _count;

public:
  ShenandoahReentrantLock();
  ~ShenandoahReentrantLock();

  void lock(bool allow_block_for_safepoint = false);
  void unlock();

  // If the lock already owned by this thread
  bool owned_by_self() const ;
};

// template based ShenandoahLocker
template<typename Lock>
class ShenandoahLocker : public StackObj {
  Lock* const _lock;
public:
  ShenandoahLocker(Lock* lock, bool allow_block_for_safepoint = false) : _lock(lock) {
    assert(_lock != nullptr, "Must not");
    _lock->lock(allow_block_for_safepoint);
  }

  ~ShenandoahLocker() {
    _lock->unlock();
  }
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHLOCK_HPP
