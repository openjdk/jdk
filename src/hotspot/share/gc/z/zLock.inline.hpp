/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef SHARE_GC_Z_ZLOCK_INLINE_HPP
#define SHARE_GC_Z_ZLOCK_INLINE_HPP

#include "gc/z/zLock.hpp"
#include "runtime/atomic.hpp"
#include "runtime/thread.hpp"
#include "utilities/debug.hpp"

inline ZLock::ZLock() {
  pthread_mutex_init(&_lock, NULL);
}

inline ZLock::~ZLock() {
  pthread_mutex_destroy(&_lock);
}

inline void ZLock::lock() {
  pthread_mutex_lock(&_lock);
}

inline bool ZLock::try_lock() {
  return pthread_mutex_trylock(&_lock) == 0;
}

inline void ZLock::unlock() {
  pthread_mutex_unlock(&_lock);
}

inline ZReentrantLock::ZReentrantLock() :
    _lock(),
    _owner(NULL),
    _count(0) {}

inline void ZReentrantLock::lock() {
  Thread* const thread = Thread::current();
  Thread* const owner = Atomic::load(&_owner);

  if (owner != thread) {
    _lock.lock();
    Atomic::store(thread, &_owner);
  }

  _count++;
}

inline void ZReentrantLock::unlock() {
  assert(is_owned(), "Invalid owner");
  assert(_count > 0, "Invalid count");

  _count--;

  if (_count == 0) {
    Atomic::store((Thread*)NULL, &_owner);
    _lock.unlock();
  }
}

inline bool ZReentrantLock::is_owned() const {
  Thread* const thread = Thread::current();
  Thread* const owner = Atomic::load(&_owner);
  return owner == thread;
}

template <typename T>
inline ZLocker<T>::ZLocker(T* lock) :
    _lock(lock) {
  _lock->lock();
}

template <typename T>
inline ZLocker<T>::~ZLocker() {
  _lock->unlock();
}

#endif // SHARE_GC_Z_ZLOCK_INLINE_HPP
