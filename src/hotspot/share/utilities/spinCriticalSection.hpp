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
#include "runtime/safepointVerifiers.hpp"
#include "runtime/thread.hpp"
#include "utilities/macros.hpp"

// Ad-hoc mutual exclusion primitive: spin critical section,
// which employs a spin lock.
//
// We use this critical section only for low-contention code, and
// when it is know that the duration is short. To be used where
// we're concerned about native mutex_t or HotSpot Mutex:: latency.
// This class uses low-level leaf-lock primitives to implement
// synchronization and is not for general synchronization use.
// Should not be used in signal-handling contexts.
class SpinCriticalSection {
private:
  // We use int type as 32-bit atomic operation is the most performant
  // compared to  smaller/larger types.
  volatile int* const _lock;
  DEBUG_ONLY(NoSafepointVerifier _nsv;)

  static void spin_acquire(volatile int* Lock);
  static void spin_release(volatile int* Lock);
public:
  NONCOPYABLE(SpinCriticalSection);
  SpinCriticalSection(volatile int* lock)
    : _lock(lock)
      DEBUG_ONLY(COMMA _nsv(Thread::current_or_null() != nullptr)) {
    spin_acquire(_lock);
  }
  ~SpinCriticalSection() {
    spin_release(_lock);
  }
};

#endif // SHARE_UTILITIES_SPINCRITICALSECTION_HPP
