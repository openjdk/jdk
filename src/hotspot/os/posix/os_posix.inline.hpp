/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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

#include "os_posix.hpp"

#include "runtime/mutex.hpp"
#include "runtime/os.hpp"

#include <unistd.h>
#include <sys/socket.h>
#include <netdb.h>

// Aix does not have NUMA support but need these for compilation.
inline bool os::numa_has_static_binding()   { AIX_ONLY(ShouldNotReachHere();) return true; }
inline bool os::numa_has_group_homing()     { AIX_ONLY(ShouldNotReachHere();) return false;  }

// Platform Mutex/Monitor implementation

inline void PlatformMutex::lock() {
  int status = pthread_mutex_lock(mutex());
  assert_status(status == 0, status, "mutex_lock");
}

inline void PlatformMutex::unlock() {
  int status = pthread_mutex_unlock(mutex());
  assert_status(status == 0, status, "mutex_unlock");
}

inline bool PlatformMutex::try_lock() {
  int status = pthread_mutex_trylock(mutex());
  assert_status(status == 0 || status == EBUSY, status, "mutex_trylock");
  return status == 0;
}

inline void PlatformMonitor::notify() {
  int status = pthread_cond_signal(cond());
  assert_status(status == 0, status, "cond_signal");
}

inline void PlatformMonitor::notify_all() {
  int status = pthread_cond_broadcast(cond());
  assert_status(status == 0, status, "cond_broadcast");
}

#endif // OS_POSIX_OS_POSIX_INLINE_HPP
