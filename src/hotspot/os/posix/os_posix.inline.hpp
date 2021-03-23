/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

#include <unistd.h>
#include <sys/socket.h>
#include <netdb.h>

// macros for restartable system calls

#define RESTARTABLE(_cmd, _result) do { \
    _result = _cmd; \
  } while(((int)_result == OS_ERR) && (errno == EINTR))

#define RESTARTABLE_RETURN_INT(_cmd) do { \
  int _result; \
  RESTARTABLE(_cmd, _result); \
  return _result; \
} while(false)


inline void os::dll_unload(void *lib) {
  ::dlclose(lib);
}

inline jlong os::lseek(int fd, jlong offset, int whence) {
  return (jlong) BSD_ONLY(::lseek) NOT_BSD(::lseek64)(fd, offset, whence);
}

inline int os::fsync(int fd) {
  return ::fsync(fd);
}

inline int os::ftruncate(int fd, jlong length) {
   return BSD_ONLY(::ftruncate) NOT_BSD(::ftruncate64)(fd, length);
}

// Aix does not have NUMA support but need these for compilation.
inline bool os::numa_has_static_binding()   { AIX_ONLY(ShouldNotReachHere();) return true; }
inline bool os::numa_has_group_homing()     { AIX_ONLY(ShouldNotReachHere();) return false;  }

inline size_t os::write(int fd, const void *buf, unsigned int nBytes) {
  size_t res;
  RESTARTABLE((size_t) ::write(fd, buf, (size_t) nBytes), res);
  return res;
}

inline int os::close(int fd) {
  return ::close(fd);
}

inline int os::socket_close(int fd) {
  return ::close(fd);
}

inline int os::socket(int domain, int type, int protocol) {
  return ::socket(domain, type, protocol);
}

inline int os::recv(int fd, char* buf, size_t nBytes, uint flags) {
  RESTARTABLE_RETURN_INT(::recv(fd, buf, nBytes, flags));
}

inline int os::send(int fd, char* buf, size_t nBytes, uint flags) {
  RESTARTABLE_RETURN_INT(::send(fd, buf, nBytes, flags));
}

inline int os::raw_send(int fd, char* buf, size_t nBytes, uint flags) {
  return os::send(fd, buf, nBytes, flags);
}

inline int os::connect(int fd, struct sockaddr* him, socklen_t len) {
  RESTARTABLE_RETURN_INT(::connect(fd, him, len));
}

inline struct hostent* os::get_host_by_name(char* name) {
  return ::gethostbyname(name);
}

inline void os::exit(int num) {
  ::exit(num);
}

// Platform Mutex/Monitor implementation

inline void os::PlatformMutex::lock() {
  int status = pthread_mutex_lock(mutex());
  assert_status(status == 0, status, "mutex_lock");
}

inline void os::PlatformMutex::unlock() {
  int status = pthread_mutex_unlock(mutex());
  assert_status(status == 0, status, "mutex_unlock");
}

inline bool os::PlatformMutex::try_lock() {
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

#endif // OS_POSIX_OS_POSIX_INLINE_HPP
