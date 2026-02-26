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

class ShenandoahLock  {
private:
  enum LockState { unlocked = 0, locked = 1 };

  shenandoah_padding(0);
  Atomic<LockState> _state;
  shenandoah_padding(1);
  Atomic<Thread*> _owner;
  shenandoah_padding(2);

  template<bool ALLOW_BLOCK>
  void contended_lock_internal(JavaThread* java_thread);
  static void yield_or_sleep(int &yields);

public:
  ShenandoahLock() : _state(unlocked), _owner(nullptr) {};

  void lock(bool allow_block_for_safepoint) {
    assert(_owner.load_relaxed() != Thread::current(), "reentrant locking attempt, would deadlock");

    if ((allow_block_for_safepoint && SafepointSynchronize::is_synchronizing()) ||
        (_state.compare_exchange(unlocked, locked) != unlocked)) {
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

  bool owned_by_self() {
#ifdef ASSERT
    return _state.load_relaxed() == locked && _owner.load_relaxed() == Thread::current();
#else
    ShouldNotReachHere();
    return false;
#endif
  }
};

class ShenandoahLocker : public StackObj {
private:
  ShenandoahLock* const _lock;
public:
  ShenandoahLocker(ShenandoahLock* lock, bool allow_block_for_safepoint = false) : _lock(lock) {
    if (_lock != nullptr) {
      _lock->lock(allow_block_for_safepoint);
    }
  }

  ~ShenandoahLocker() {
    if (_lock != nullptr) {
      _lock->unlock();
    }
  }
};

class ShenandoahSimpleLock {
private:
  PlatformMonitor   _lock; // native lock
public:
  ShenandoahSimpleLock();

  virtual void lock();
  virtual void unlock();
};

class ShenandoahReentrantLock : public ShenandoahSimpleLock {
private:
  Atomic<Thread*>       _owner;
  uint64_t              _count;

public:
  ShenandoahReentrantLock();
  ~ShenandoahReentrantLock();

  virtual void lock();
  virtual void unlock();

  // If the lock already owned by this thread
  bool owned_by_self() const ;
};

class ShenandoahReentrantLocker : public StackObj {
private:
  ShenandoahReentrantLock* const _lock;

public:
  ShenandoahReentrantLocker(ShenandoahReentrantLock* lock) :
    _lock(lock) {
    if (_lock != nullptr) {
      _lock->lock();
    }
  }

  ~ShenandoahReentrantLocker() {
    if (_lock != nullptr) {
      assert(_lock->owned_by_self(), "Must be owner");
      _lock->unlock();
    }
  }
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHLOCK_HPP
