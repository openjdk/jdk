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

#ifndef SHARE_RUNTIME_MRSWMUTEX_HPP
#define SHARE_RUNTIME_MRSWMUTEX_HPP

#include "javaThread.hpp"
#include "memory/allocation.hpp"
#include "runtime/atomic.hpp"
#include "runtime/mutex.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

class NoTransition;

// Multi-reader single-writer lock implementation.
// If a transition needs to occur when the reader or writer lock is taken (in
// case the thread blocks), then specialize on the ReaderTransition and/or
// WriterTransition parameters. This lock is unfair, high contention of readers
// may starve some of them.
// Writers take precedence, blocking new readers from entering and allowing
// current readers to proceed. The consequences are undefined if one or more
// writer threads attempt to enter their critical region when another writer
// thread already is attempting to do so or currently is in its critical region.
class MRSWMutex : public CHeapObj<mtSynchronizer> {
private:
  NONCOPYABLE(MRSWMutex);
  class NoTransition : public StackObj {
  public:
    NoTransition(Thread* thread) {
    }
  };

  template<typename T>
  class Locker : public StackObj {
  private:
    T* const _lock;

  public:
    Locker(T* lock)
      : _lock(lock) {
      if (_lock != nullptr) {
        _lock->lock();
      }
    }
    ~Locker() {
      if (_lock != nullptr) {
        _lock->unlock();
      }
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
  volatile int64_t _count;

public:
  MRSWMutex()
    : _mon(),
      _count(0) {
  }

  ~MRSWMutex() {
  }

  template<typename WriterTransition = NoTransition>
  void write_lock() {
    for (;;) {
      const int64_t count = Atomic::load_acquire(&_count);

      if (count < 0) {
        // Already blocked, wait until unblocked

        // Do requested transition before blocking
        WriterTransition tbivm(JavaThread::current());

        Locker<PlatformMonitor> locker(&_mon);
        while (Atomic::load_acquire(&_count) < 0) {
          _mon.wait(0);
        }

        // Unblocked
        continue;
      }

      // Increment and invert count
      if (Atomic::cmpxchg(&_count, count, -(count + 1)) != count) {
        continue;
      }

      // If the previous count was 0, then we just incremented and inverted
      // it to -1 and have now blocked. Otherwise we wait until all reader
      // threads have exited the critical region.
      if (count != 0) {
        // Do requested transition before blocking
        WriterTransition tbivm(JavaThread::current());

        // Wait until blocked
        Locker<PlatformMonitor> locker(&_mon);
        while (Atomic::load_acquire(&_count) != -1) {
          _mon.wait(0);
        }
      }

      // Blocked.
      return;
    }
  }
  void write_unlock();

  template<typename ReaderTransition = NoTransition>
  void read_lock() {
    for (;;) {
      const int64_t count = Atomic::load_acquire(&_count);
      if (count < 0) {
        // Wait until unblocked

        // Do requested transition before blocking
        ReaderTransition tbivm(JavaThread::current());

        Locker<PlatformMonitor> locker(&_mon);
        while (Atomic::load_acquire(&_count) < 0) {
          _mon.wait(0);
        }

        // Unblocked
        continue;
      }

      // Increment count
      if (Atomic::cmpxchg(&_count, count, count + 1) != count) {
        continue;
      }

      // Entered critical region
      return;
    }
  }
  void read_unlock();
};
#endif // SHARE_RUNTIME_MRSWMUTEX_HPP
