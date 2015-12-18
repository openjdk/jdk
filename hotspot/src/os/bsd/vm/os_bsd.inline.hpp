/*
 * Copyright (c) 1999, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_BSD_VM_OS_BSD_INLINE_HPP
#define OS_BSD_VM_OS_BSD_INLINE_HPP

#include "runtime/os.hpp"

// System includes

#include <unistd.h>
#include <sys/socket.h>
#include <sys/poll.h>
#include <netdb.h>

// File names are case-sensitive on windows only
inline int os::file_name_strcmp(const char* s1, const char* s2) {
  return strcmp(s1, s2);
}

inline bool os::obsolete_option(const JavaVMOption *option) {
  return false;
}

inline bool os::uses_stack_guard_pages() {
  return true;
}

inline bool os::allocate_stack_guard_pages() {
  assert(uses_stack_guard_pages(), "sanity check");
#if !defined(__FreeBSD__) || __FreeBSD__ < 5
  // Since FreeBSD 4 uses malloc() for allocating the thread stack
  // there is no need to do anything extra to allocate the guard pages
  return false;
#else
  // FreeBSD 5+ uses mmap MAP_STACK for allocating the thread stacks.
  // Must 'allocate' them or guard pages are ignored.
  return true;
#endif
}


// On Bsd, reservations are made on a page by page basis, nothing to do.
inline void os::pd_split_reserved_memory(char *base, size_t size,
                                      size_t split, bool realloc) {
}


// Bang the shadow pages if they need to be touched to be mapped.
inline void os::bang_stack_shadow_pages() {
}

inline void os::dll_unload(void *lib) {
  ::dlclose(lib);
}

inline const int os::default_file_open_flags() { return 0;}

inline DIR* os::opendir(const char* dirname)
{
  assert(dirname != NULL, "just checking");
  return ::opendir(dirname);
}

inline int os::readdir_buf_size(const char *path)
{
  return NAME_MAX + sizeof(dirent) + 1;
}

inline jlong os::lseek(int fd, jlong offset, int whence) {
  return (jlong) ::lseek(fd, offset, whence);
}

inline int os::fsync(int fd) {
  return ::fsync(fd);
}

inline char* os::native_path(char *path) {
  return path;
}

inline int os::ftruncate(int fd, jlong length) {
  return ::ftruncate(fd, length);
}

inline struct dirent* os::readdir(DIR* dirp, dirent *dbuf)
{
  dirent* p;
  int status;
  assert(dirp != NULL, "just checking");

  // NOTE: Bsd readdir_r (on RH 6.2 and 7.2 at least) is NOT like the POSIX
  // version. Here is the doc for this function:
  // http://www.gnu.org/manual/glibc-2.2.3/html_node/libc_262.html

  if((status = ::readdir_r(dirp, dbuf, &p)) != 0) {
    errno = status;
    return NULL;
  } else
    return p;
}

inline int os::closedir(DIR *dirp) {
  assert(dirp != NULL, "argument is NULL");
  return ::closedir(dirp);
}

// macros for restartable system calls

#define RESTARTABLE(_cmd, _result) do { \
    _result = _cmd; \
  } while(((int)_result == OS_ERR) && (errno == EINTR))

#define RESTARTABLE_RETURN_INT(_cmd) do { \
  int _result; \
  RESTARTABLE(_cmd, _result); \
  return _result; \
} while(false)

inline bool os::numa_has_static_binding()   { return true; }
inline bool os::numa_has_group_homing()     { return false;  }

inline size_t os::restartable_read(int fd, void *buf, unsigned int nBytes) {
  size_t res;
  RESTARTABLE( (size_t) ::read(fd, buf, (size_t) nBytes), res);
  return res;
}

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

inline bool os::supports_monotonic_clock() {
#ifdef __APPLE__
  return true;
#else
  return Bsd::_clock_gettime != NULL;
#endif
}

inline void os::exit(int num) {
  ::exit(num);
}

#endif // OS_BSD_VM_OS_BSD_INLINE_HPP
