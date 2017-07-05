/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_SOLARIS_VM_OS_SOLARIS_INLINE_HPP
#define OS_SOLARIS_VM_OS_SOLARIS_INLINE_HPP

#include "runtime/os.hpp"

// System includes
#include <sys/param.h>
#include <dlfcn.h>
#include <sys/socket.h>
#include <sys/poll.h>
#include <sys/filio.h>
#include <unistd.h>
#include <netdb.h>
#include <setjmp.h>

// File names are case-sensitive on windows only
inline int os::file_name_strcmp(const char* s1, const char* s2) {
  return strcmp(s1, s2);
}

inline bool os::uses_stack_guard_pages() {
  return true;
}

inline bool os::allocate_stack_guard_pages() {
  assert(uses_stack_guard_pages(), "sanity check");
  int r = thr_main() ;
  guarantee (r == 0 || r == 1, "CR6501650 or CR6493689") ;
  return r;
}


// On Solaris, reservations are made on a page by page basis, nothing to do.
inline void os::pd_split_reserved_memory(char *base, size_t size,
                                      size_t split, bool realloc) {
}


// Bang the shadow pages if they need to be touched to be mapped.
inline void os::map_stack_shadow_pages() {
}
inline void os::dll_unload(void *lib) { ::dlclose(lib); }

inline const int os::default_file_open_flags() { return 0;}

inline DIR* os::opendir(const char* dirname) {
  assert(dirname != NULL, "just checking");
  return ::opendir(dirname);
}

inline int os::readdir_buf_size(const char *path) {
  int size = pathconf(path, _PC_NAME_MAX);
  return (size < 0 ? MAXPATHLEN : size) + sizeof(dirent) + 1;
}

inline struct dirent* os::readdir(DIR* dirp, dirent* dbuf) {
  assert(dirp != NULL, "just checking");
#if defined(_LP64) || defined(_GNU_SOURCE) || _FILE_OFFSET_BITS==64
  dirent* p;
  int status;

  if((status = ::readdir_r(dirp, dbuf, &p)) != 0) {
    errno = status;
    return NULL;
  } else
    return p;
#else  // defined(_LP64) || defined(_GNU_SOURCE) || _FILE_OFFSET_BITS==64
  return ::readdir_r(dirp, dbuf);
#endif // defined(_LP64) || defined(_GNU_SOURCE) || _FILE_OFFSET_BITS==64
}

inline int os::closedir(DIR *dirp) {
  assert(dirp != NULL, "argument is NULL");
  return ::closedir(dirp);
}

//////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////

// macros for restartable system calls

#define RESTARTABLE(_cmd, _result) do { \
  do { \
    _result = _cmd; \
  } while((_result == OS_ERR) && (errno == EINTR)); \
} while(false)

#define RESTARTABLE_RETURN_INT(_cmd) do { \
  int _result; \
  RESTARTABLE(_cmd, _result); \
  return _result; \
} while(false)

inline bool os::numa_has_static_binding()   { return false; }
inline bool os::numa_has_group_homing()     { return true;  }

inline int    os::socket(int domain, int type, int protocol) {
  return ::socket(domain, type, protocol);
}

inline struct hostent* os::get_host_by_name(char* name) {
  return ::gethostbyname(name);
}

inline bool os::supports_monotonic_clock() {
  // javaTimeNanos() is monotonic on Solaris, see getTimeNanos() comments
  return true;
}

inline void os::exit(int num) {
  ::exit(num);
}

#endif // OS_SOLARIS_VM_OS_SOLARIS_INLINE_HPP
