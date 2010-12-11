/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_LINUX_VM_OS_LINUX_INLINE_HPP
#define OS_LINUX_VM_OS_LINUX_INLINE_HPP

#include "runtime/atomic.hpp"
#include "runtime/os.hpp"
#ifdef TARGET_OS_ARCH_linux_x86
# include "atomic_linux_x86.inline.hpp"
# include "orderAccess_linux_x86.inline.hpp"
#endif
#ifdef TARGET_OS_ARCH_linux_sparc
# include "atomic_linux_sparc.inline.hpp"
# include "orderAccess_linux_sparc.inline.hpp"
#endif
#ifdef TARGET_OS_ARCH_linux_zero
# include "atomic_linux_zero.inline.hpp"
# include "orderAccess_linux_zero.inline.hpp"
#endif

// System includes

#include <unistd.h>
#include <sys/socket.h>
#include <sys/poll.h>
#include <sys/ioctl.h>
#include <netdb.h>

inline void* os::thread_local_storage_at(int index) {
  return pthread_getspecific((pthread_key_t)index);
}

inline const char* os::file_separator() {
  return "/";
}

inline const char* os::line_separator() {
  return "\n";
}

inline const char* os::path_separator() {
  return ":";
}

inline const char* os::jlong_format_specifier() {
  return "%lld";
}

inline const char* os::julong_format_specifier() {
  return "%llu";
}

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
  return true;
}


// On Linux, reservations are made on a page by page basis, nothing to do.
inline void os::split_reserved_memory(char *base, size_t size,
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
  return (jlong) ::lseek64(fd, offset, whence);
}

inline int os::fsync(int fd) {
  return ::fsync(fd);
}

inline char* os::native_path(char *path) {
  return path;
}

inline int os::ftruncate(int fd, jlong length) {
  return ::ftruncate64(fd, length);
}

inline struct dirent* os::readdir(DIR* dirp, dirent *dbuf)
{
  dirent* p;
  int status;
  assert(dirp != NULL, "just checking");

  // NOTE: Linux readdir_r (on RH 6.2 and 7.2 at least) is NOT like the POSIX
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

inline int os::recv(int fd, char *buf, int nBytes, int flags) {
  RESTARTABLE_RETURN_INT(::recv(fd, buf, nBytes, (unsigned int) flags));
}

inline int os::send(int fd, char *buf, int nBytes, int flags) {
  RESTARTABLE_RETURN_INT(::send(fd, buf, nBytes, (unsigned int) flags));
}

inline int os::raw_send(int fd, char *buf, int nBytes, int flags) {
  return os::send(fd, buf, nBytes, flags);
}

inline int os::timeout(int fd, long timeout) {
  julong prevtime,newtime;
  struct timeval t;

  gettimeofday(&t, NULL);
  prevtime = ((julong)t.tv_sec * 1000)  +  t.tv_usec / 1000;

  for(;;) {
    struct pollfd pfd;

    pfd.fd = fd;
    pfd.events = POLLIN | POLLERR;

    int res = ::poll(&pfd, 1, timeout);

    if (res == OS_ERR && errno == EINTR) {

      // On Linux any value < 0 means "forever"

      if(timeout >= 0) {
        gettimeofday(&t, NULL);
        newtime = ((julong)t.tv_sec * 1000)  +  t.tv_usec / 1000;
        timeout -= newtime - prevtime;
        if(timeout <= 0)
          return OS_OK;
        prevtime = newtime;
      }
    } else
      return res;
  }
}

inline int os::listen(int fd, int count) {
  return ::listen(fd, count);
}

inline int os::connect(int fd, struct sockaddr *him, int len) {
  RESTARTABLE_RETURN_INT(::connect(fd, him, len));
}

inline int os::accept(int fd, struct sockaddr *him, int *len) {
  // This cast is from int to unsigned int on linux.  Since we
  // only pass the parameter "len" around the vm and don't try to
  // fetch it's value, this cast is safe for now. The java.net group
  // may need and want to change this interface someday if socklen_t goes
  // to 64 bits on some platform that we support.
  // Linux doc says this can't return EINTR, unlike accept() on Solaris

  return ::accept(fd, him, (socklen_t *)len);
}

inline int os::recvfrom(int fd, char *buf, int nBytes, int flags,
                         sockaddr *from, int *fromlen) {
  RESTARTABLE_RETURN_INT(::recvfrom(fd, buf, nBytes, (unsigned int) flags, from, (socklen_t *)fromlen));
}

inline int os::sendto(int fd, char *buf, int len, int flags,
                        struct sockaddr *to, int tolen) {
  RESTARTABLE_RETURN_INT(::sendto(fd, buf, len, (unsigned int) flags, to, tolen));
}

inline int os::socket_available(int fd, jint *pbytes) {
  // Linux doc says EINTR not returned, unlike Solaris
  int ret = ::ioctl(fd, FIONREAD, pbytes);

  //%% note ioctl can return 0 when successful, JVM_SocketAvailable
  // is expected to return 0 on failure and 1 on success to the jdk.
  return (ret < 0) ? 0 : 1;
}


inline int os::socket_shutdown(int fd, int howto){
  return ::shutdown(fd, howto);
}

inline int os::bind(int fd, struct sockaddr *him, int len){
  return ::bind(fd, him, len);
}

inline int os::get_sock_name(int fd, struct sockaddr *him, int *len){
  return ::getsockname(fd, him, (socklen_t *)len);
}

inline int os::get_host_name(char* name, int namelen){
  return ::gethostname(name, namelen);
}

inline struct hostent*  os::get_host_by_name(char* name) {
  return ::gethostbyname(name);
}
inline int os::get_sock_opt(int fd, int level, int optname,
                             char *optval, int* optlen){
  return ::getsockopt(fd, level, optname, optval, (socklen_t *)optlen);
}

inline int os::set_sock_opt(int fd, int level, int optname,
                             const char *optval, int optlen){
  return ::setsockopt(fd, level, optname, optval, optlen);
}
#endif // OS_LINUX_VM_OS_LINUX_INLINE_HPP
