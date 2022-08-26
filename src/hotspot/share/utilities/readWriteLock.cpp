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

#include "precompiled.hpp"
#include "runtime/atomic.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/mutex.hpp"
#include "runtime/thread.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/readWriteLock.hpp"

inline void ReadWriteLock::await_write_unlock() {
  Locker locker(&_mon);
  while (Atomic::load_acquire(&_count) < 0) {
    _mon.wait(0);
  }
}

inline void ReadWriteLock::await_write_lock() {
  Locker locker(&_mon);
  while (Atomic::load_acquire(&_count) != -1) {
    _mon.wait(0);
  }
}

void ReadWriteLock::read_lock(Thread* current) {
  assert(current == nullptr || current == Thread::current(), "invariant");

  for (;;) {
    const int32_t count = Atomic::load_acquire(&_count);
    if (count < 0) {
      // Wait until unlocked by writer

      if (current != nullptr && current->is_Java_thread()) {
        ThreadBlockInVM tbivm(JavaThread::cast(current));
        await_write_unlock();
      } else {
        await_write_unlock();
      }
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

void ReadWriteLock::read_unlock() {
  for (;;) {
    const int32_t count = Atomic::load_acquire(&_count);
    assert(count != 0 && count != -1, "invariant");

    if (count > 0) {
      // No writer in progress, try to decrement reader count.
      if (Atomic::cmpxchg(&_count, count, count - 1) != count) {
        continue;
      }
    } else {
      // Writer in progress, try to increment reader count.
      if (Atomic::cmpxchg(&_count, count, count + 1) != count) {
        continue;
      }
      // If the previous count was -2, then we just incremented it to -1,
      // and we should signal that all readers have now exited their
      // critical region and the writer may now block readers.
      if (count == -2) {
        Locker locker(&_mon);
        _mon.notify_all();
      }
    }
    // Exited critical region
    return;
  }
}

void ReadWriteLock::write_lock(Thread* current) {
  assert(current == nullptr || current == Thread::current(), "invariant");

  for (;;) {
    const int32_t count = Atomic::load_acquire(&_count);

    if (count < 0) {
      // Already has a writer, wait until unlocked

      if (current != nullptr && current->is_Java_thread()) {
        ThreadBlockInVM tbivm(JavaThread::cast(current));
        await_write_unlock();
      } else {
        await_write_unlock();
      }
      continue;
    }

    // Increment and invert count
    if (Atomic::cmpxchg(&_count, count, -(count + 1)) != count) {
      continue;
    }

    // If the previous count was 0, then we just incremented and inverted
    // it to -1 and have now blocked readers. Otherwise we wait until all reader
    // threads have exited the critical region.
    if (count != 0) {
      // Wait until all readers exit.
      if (current != nullptr && current->is_Java_thread()) {
        ThreadBlockInVM tbivm(JavaThread::cast(current));
        await_write_lock();
      } else {
        await_write_lock();
      }
    }

    // Locked.
    return;
  }
}

void ReadWriteLock::write_unlock() {
  assert(Atomic::load_acquire(&_count) == -1, "invariant");

  Locker locker(&_mon);
  Atomic::release_store(&_count, (int32_t)0);
  _mon.notify_all();
}
