/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_SPINCRITICALSECTION_HPP
#define SHARE_UTILITIES_SPINCRITICALSECTION_HPP

#include "runtime/javaThread.hpp"

class SpinCriticalSectionHelper {
  friend class SpinCriticalSection;
  template<class Lambda, class...Args>
  friend class SpinSingleSection;
  // Low-level leaf-lock primitives used to implement synchronization.
  // Not for general synchronization use.
  static void spin_acquire(volatile int* Lock);
  static void spin_release(volatile int* Lock);
  static bool try_spin_acquire(volatile int* Lock);
};

// Short critical section. To be used when having a
// mutex is considered to be expensive.
class SpinCriticalSection {
private:
  volatile int* const _lock;
public:
  SpinCriticalSection(volatile int* lock) : _lock(lock) {
    SpinCriticalSectionHelper::spin_acquire(_lock);
  }
  ~SpinCriticalSection() {
    SpinCriticalSectionHelper::spin_release(_lock);
  }
};

template<class Lambda, class...Args>
class SpinSingleSection {
private:
  volatile int* const _lock;
  Thread* _lock_owner;
public:
  SpinSingleSection(volatile int* lock, Lambda& F, Args&... args) : _lock(lock), _lock_owner(nullptr) {
    if (SpinCriticalSectionHelper::try_spin_acquire(_lock)) {
      _lock_owner = Thread::current();
      F(args...);
    }
  }
  ~SpinSingleSection() {
    // It is safe to not have any atomic operations here,
    // as a thread either sees a nullptr or a pointer to a thread which
    // succeeded in locking the lock. Comparison will fail in both
    // cases if it is not a succeeded thread.
    if (_lock_owner == Thread::current()) {
      SpinCriticalSectionHelper::spin_release(_lock);
    }
  }
};
#endif //SHARE_UTILITIES_SPINCRITICALSECTION_HPP
