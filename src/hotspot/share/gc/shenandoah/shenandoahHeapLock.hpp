/*
 * Copyright (c) 2017, 2018, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_VM_GC_SHENANDOAH_SHENANDOAHHEAPLOCK_HPP
#define SHARE_VM_GC_SHENANDOAH_SHENANDOAHHEAPLOCK_HPP

#include "memory/allocation.hpp"
#include "runtime/thread.hpp"

class ShenandoahHeapLock  {
private:
  enum LockState { unlocked = 0, locked = 1 };

  DEFINE_PAD_MINUS_SIZE(0, DEFAULT_CACHE_LINE_SIZE, sizeof(volatile int));
  volatile int _state;
  DEFINE_PAD_MINUS_SIZE(1, DEFAULT_CACHE_LINE_SIZE, sizeof(volatile Thread*));
  volatile Thread* _owner;
  DEFINE_PAD_MINUS_SIZE(2, DEFAULT_CACHE_LINE_SIZE, 0);

public:
  ShenandoahHeapLock() : _state(unlocked), _owner(NULL) {};

  void lock() {
    Thread::SpinAcquire(&_state, "Shenandoah Heap Lock");
#ifdef ASSERT
    assert(_state == locked, "must be locked");
    assert(_owner == NULL, "must not be owned");
    _owner = Thread::current();
#endif
  }

  void unlock() {
#ifdef ASSERT
    assert (_owner == Thread::current(), "sanity");
    _owner = NULL;
#endif
    Thread::SpinRelease(&_state);
  }

#ifdef ASSERT
  void assert_owned_by_current_thread() {
    assert(_state == locked, "must be locked");
    assert(_owner == Thread::current(), "must be owned by current thread");
  }

  void assert_not_owned_by_current_thread() {
    assert(_owner != Thread::current(), "must be not owned by current thread");
  }

  void assert_owned_by_current_thread_or_safepoint() {
    Thread* thr = Thread::current();
    assert((_state == locked && _owner == thr) ||
           (SafepointSynchronize::is_at_safepoint() && thr->is_VM_thread()),
           "must own heap lock or by VM thread at safepoint");
  }
#endif
};

class ShenandoahHeapLocker : public StackObj {
private:
  ShenandoahHeapLock* _lock;
public:
  ShenandoahHeapLocker(ShenandoahHeapLock* lock) {
    _lock = lock;
    _lock->lock();
  }

  ~ShenandoahHeapLocker() {
    _lock->unlock();
  }
};

#endif // SHARE_VM_GC_SHENANDOAH_SHENANDOAHHEAPLOCK_HPP
