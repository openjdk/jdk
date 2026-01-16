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

#include "runtime/atomicAccess.hpp"
#include "utilities/spinCriticalSection.hpp"
#include "utilities/spinYield.hpp"

void SpinCriticalSection::spin_acquire(volatile int* adr) {
  if (AtomicAccess::cmpxchg(adr, 0, 1) == 0) {
    return;   // normal fast-path return
  }

  SpinYield sy(4096, 5, millis_to_nanos(1));

  // Slow-path : We've encountered contention -- Spin/Yield/Block strategy.
  for (;;) {
    while (*adr != 0) {
      sy.wait();
    }
    if (AtomicAccess::cmpxchg(adr, 0, 1) == 0) return;
  }
}

void SpinCriticalSection::spin_release(volatile int* adr) {
  assert(*adr != 0, "invariant");
  // Roach-motel semantics.
  // It's safe if subsequent LDs and STs float "up" into the critical section,
  // but prior LDs and STs within the critical section can't be allowed
  // to reorder or float past the ST that releases the lock.
  // Loads and stores in the critical section - which appear in program
  // order before the store that releases the lock - must also appear
  // before the store that releases the lock in memory visibility order.
  // So we need a #loadstore|#storestore "release" memory barrier before
  // the ST of 0 into the lock-word which releases the lock.
  AtomicAccess::release_store(adr, 0);
}

