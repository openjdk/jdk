/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/gc_globals.hpp"
#include "jfr/utilities/jfrEpochShiftLock.hpp"
#include "runtime/atomicAccess.hpp"
#include "runtime/globals.hpp"
#include "runtime/thread.inline.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/spinYield.hpp"

static volatile int _epoch_shift_lock = 0;

static inline bool acquire(Thread* t = nullptr) {
  if (!(UseShenandoahGC || UseZGC)) {
    return false;
  }
  if (t != nullptr) {
    if (t->is_Java_thread()) {
      return false;
    }
  } else {
    if (Thread::current()->is_Java_thread()) {
      return false;
    }
  }

  if (AtomicAccess::cmpxchg(&_epoch_shift_lock, 0, 1) == 0) {
    return true;   // normal fast-path return
  }

  SpinYield sy(4096, 5, millis_to_nanos(1));

  // Slow-path : We've encountered contention -- Spin/Yield/Block strategy.
  for (;;) {
    while (_epoch_shift_lock != 0) {
      sy.wait();
    }
    if (AtomicAccess::cmpxchg(&_epoch_shift_lock, 0, 1) == 0) {
      return true;
    }
  }
}

JfrEpochShiftLock::JfrEpochShiftLock(Thread* t) : _acquired(acquire(t)) {}

JfrEpochShiftLock::JfrEpochShiftLock() : _acquired(acquire()) {}

JfrEpochShiftLock::~JfrEpochShiftLock() {
  if (_acquired) {
    assert(_epoch_shift_lock != 0, "invariant");
    AtomicAccess::release_store(&_epoch_shift_lock, 0);
  }
}
