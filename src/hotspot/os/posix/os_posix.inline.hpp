/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_POSIX_OS_POSIX_INLINE_HPP
#define OS_POSIX_OS_POSIX_INLINE_HPP

#include "runtime/os.hpp"

#ifdef SUPPORTS_CLOCK_MONOTONIC

// Exported clock functionality

inline bool os::Posix::supports_monotonic_clock() {
  return _clock_gettime != NULL;
}

inline int os::Posix::clock_gettime(clockid_t clock_id, struct timespec *tp) {
  return _clock_gettime != NULL ? _clock_gettime(clock_id, tp) : -1;
}

inline int os::Posix::clock_getres(clockid_t clock_id, struct timespec *tp) {
  return _clock_getres != NULL ? _clock_getres(clock_id, tp) : -1;
}

#endif // SUPPORTS_CLOCK_MONOTONIC

#ifndef SOLARIS

// Platform Monitor implementation

inline void os::PlatformMonitor::lock() {
  int status = pthread_mutex_lock(mutex());
  assert_status(status == 0, status, "mutex_lock");
}

inline void os::PlatformMonitor::unlock() {
  int status = pthread_mutex_unlock(mutex());
  assert_status(status == 0, status, "mutex_unlock");
}

inline bool os::PlatformMonitor::try_lock() {
  int status = pthread_mutex_trylock(mutex());
  assert_status(status == 0 || status == EBUSY, status, "mutex_trylock");
  return status == 0;
}

inline void os::PlatformMonitor::notify() {
  int status = pthread_cond_signal(cond());
  assert_status(status == 0, status, "cond_signal");
}

inline void os::PlatformMonitor::notify_all() {
  int status = pthread_cond_broadcast(cond());
  assert_status(status == 0, status, "cond_broadcast");
}

#endif // !SOLARIS

#endif // OS_POSIX_OS_POSIX_INLINE_HPP
