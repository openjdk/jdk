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

// In-flight release callback for ShenandoahLock and ShenandoahSimpleLock. ThreadBlockInVMPreprocess
// invokes operator() after the acquiring JavaThread has transitioned back to _thread_in_vm before it
// processes a pending safepoint, giving us the chance to release the just-acquired lock so the thread
// does not hold it across the safepoint.
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
  bool released() {
    return _lock == nullptr;
  }
};

class ShenandoahLock {
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

  void contended_lock(bool allow_block_for_safepoint);

  // Spin/TTAS on _state. Returns true once the lock is acquired. When ALLOW_BLOCK is set and a
  // safepoint poll becomes armed, returns false WITHOUT acquiring, so the caller (contended_lock)
  // can block for the safepoint and retry. Runs inside the caller's _thread_blocked scope when
  // ALLOW_BLOCK, so the spinning thread is safepoint-safe and does not delay the safepoint.
  template<bool ALLOW_BLOCK>
  bool contended_lock_internal(JavaThread* java_thread) {
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
        // Reached when ALLOW_BLOCK but no safepoint is pending (or one was announced then cleared
        // before our poll armed): yield like the non-blocking path rather than re-spin tightly.
        yield_or_sleep(yields);
      } else {
        yield_or_sleep(yields);
      }
    }
    return true;
  }

  static void yield_or_sleep(int &yields);

  void release_for_safepoint() {
    _state.store_relaxed(unlocked);
  }

public:
  ShenandoahLock() : _state(unlocked) {
    DEBUG_ONLY(_owner.store_relaxed(nullptr);)
  };

  void lock(bool allow_block_for_safepoint = false) {
    assert(_owner.load_relaxed() != Thread::current(), "reentrant locking attempt, would deadlock");

    if (_state.load_relaxed() == locked || 
        _state.compare_exchange(unlocked, locked) != unlocked) {
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

  // Single non-blocking CAS attempt to acquire the lock. Returns true if this thread won it.
  bool try_lock() {
    bool const acquired = _state.load_relaxed() == unlocked &&
	                  _state.compare_exchange(unlocked, locked) == unlocked;
#ifdef ASSERT
    if (acquired) {
      assert(_state.load_relaxed() == locked, "must be locked");
      assert(_owner.load_relaxed() == nullptr, "must not be owned");
      DEBUG_ONLY(_owner.store_relaxed(Thread::current());)
    }
#endif
    return acquired;
  }

  bool owned_by_self() const {
#ifdef ASSERT
    return _state.load_relaxed() == locked && _owner.load_relaxed() == Thread::current();
#else
    ShouldNotReachHere();
    return false;
#endif
  }
};

// Blocking lock backed by a PlatformMonitor: a contended waiter parks on the native monitor instead
// of busy-spinning like ShenandoahLock.
class ShenandoahSimpleLock {
  template<typename Lock> friend class ShenandoahInFlightLockRelease; 
private:
  PlatformMonitor   _lock; // native lock
  DEBUG_ONLY(Atomic<Thread*> _owner;)

  void contended_lock_for_java_thread(JavaThread* java_thread);

  // Release the native lock on behalf of an arriving safepoint (in-flight release). Called from the
  // ThreadBlockInVMPreprocess callback while the acquiring JavaThread is being safepointed.
  void release_for_safepoint() {
    // The owner is set only after lock() returns, so there is no owner write to undo here.
    _lock.unlock();
  }
public:
  ShenandoahSimpleLock();
  void lock(bool allow_block_for_safepoint) {
    assert(!allow_block_for_safepoint || Thread::current()->is_Java_thread(), "Must be Java thread if allow for safepoint");
    assert(_owner.load_relaxed() != Thread::current(), "reentrant locking attempt, would deadlock");

    if (allow_block_for_safepoint) {
      if (!_lock.try_lock()) {
        contended_lock_for_java_thread(JavaThread::current());
      }
    } else {
      _lock.lock();
    }

    assert(_owner.load_relaxed() == nullptr, "must not be owned");
    DEBUG_ONLY(_owner.store_relaxed(Thread::current());)
  }

  void unlock() {
    assert(_owner.load_relaxed() == Thread::current(), "sanity");
    DEBUG_ONLY(_owner.store_relaxed((Thread*)nullptr);)
    _lock.unlock();
  }

  bool try_lock() {
    bool const acquired = _lock.try_lock();
#ifdef ASSERT
    if (acquired) {
      assert(_owner.load_relaxed() == nullptr, "must not be owned");
      DEBUG_ONLY(_owner.store_relaxed(Thread::current());)
    }
#endif
    return acquired;
  }

  bool owned_by_self() const {
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
