/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_READWRITELOCK_HPP
#define SHARE_RUNTIME_READWRITELOCK_HPP

#include "memory/allocation.hpp"
#include "runtime/atomic.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/mutex.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

// Multi-reader single-writer lock implementation.
// * This lock is unfair, high contention of readers may starve some of them.
// * Writers take precedence, blocking new readers from entering and allowing
//   current readers to proceed.
// * A writer cannot downgrade to a read-lock
// * A reader cannot upgrade to a write-lock

class ReadWriteLock : public CHeapObj<mtSynchronizer> {
private:
  NONCOPYABLE(ReadWriteLock);

  class Locker : public StackObj {
  private:
    PlatformMonitor* const _lock;

  public:
    Locker(PlatformMonitor* lock)
      : _lock(lock) {
      _lock->lock();
    }
    ~Locker() {
      _lock->unlock();
    }
  };

  PlatformMonitor _mon;

  // The count reflects the number of reader threads inside a critical region and whether or not a writer is waiting.
  //
  // * Normal (count >= 0). Readers are allowed to enter and exit their critical region, no writer waiting.
  //
  // * Blocked (count == -1). A writer is inside its critical region.
  //
  // * Block in progress (count < -1). Readers are only allowed to exit their critical region.
  //   Attempts by readers to enter their critical region is blocked.
  //
  volatile int32_t _count;

public:
  ReadWriteLock()
    : _mon(),
      _count(0) {
  }

  ~ReadWriteLock() {
  }

  void write_lock(Thread* current = Thread::current());
  void write_unlock();

  void read_lock(Thread* current = Thread::current());
  void read_unlock();
};
#endif // SHARE_RUNTIME_READWRITELOCK_HPP
