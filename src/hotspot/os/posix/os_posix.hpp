/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_POSIX_OS_POSIX_HPP
#define OS_POSIX_OS_POSIX_HPP

#include "runtime/os.hpp"

#include <errno.h>

// Note: the Posix API aims to capture functionality available on all Posix
// compliant platforms, but in practice the implementations may depend on
// non-Posix functionality.
// This use of non-Posix API's is made possible by compiling/linking in a mode
// that is not restricted to being fully Posix complaint, such as by declaring
// -D_GNU_SOURCE. But be aware that in doing so we may enable non-Posix
// behaviour in API's that are defined by Posix. For example, that SIGSTKSZ
// is not defined as a constant as of Glibc 2.34.

// macros for restartable system calls

#define RESTARTABLE(_cmd, _result) do { \
    _result = _cmd; \
  } while(((int)_result == OS_ERR) && (errno == EINTR))

#define RESTARTABLE_RETURN_SSIZE_T(_cmd) do { \
  ssize_t _result; \
  RESTARTABLE(_cmd, _result); \
  return _result; \
} while(false)

class os::Posix {
  friend class os;

protected:
  static void print_distro_info(outputStream* st);
  static void print_rlimit_info(outputStream* st);
  static void print_uname_info(outputStream* st);
  static void print_libversion_info(outputStream* st);
  static void print_load_average(outputStream* st);
  static void print_uptime_info(outputStream* st);

public:
  static void init(void);  // early initialization - no logging available
  static void init_2(void);// later initialization - logging available

  // Return default stack size for the specified thread type
  static size_t default_stack_size(os::ThreadType thr_type);
  static size_t get_initial_stack_size(ThreadType thr_type, size_t req_stack_size);

  // Helper function; describes pthread attributes as short string. String is written
  // to buf with len buflen; buf is returned.
  static char* describe_pthread_attr(char* buf, size_t buflen, const pthread_attr_t* attr);

  // Returns true if given uid is root.
  static bool is_root(uid_t uid);

  // Returns true if given uid is effective or root uid.
  static bool matches_effective_uid_or_root(uid_t uid);

  // Returns true if either given uid is effective uid and given gid is
  // effective gid, or if given uid is root.
  static bool matches_effective_uid_and_gid_or_root(uid_t uid, gid_t gid);

  static void print_umask(outputStream* st, mode_t umsk);

  // Set PC into context. Needed for continuation after signal.
  static address ucontext_get_pc(const ucontext_t* ctx);
  static void    ucontext_set_pc(ucontext_t* ctx, address pc);

  DEBUG_ONLY(static bool ucontext_is_interpreter(const ucontext_t* ctx);)

  static void to_RTC_abstime(timespec* abstime, int64_t millis);

  // clock ticks per second of the system
  static int clock_tics_per_second();

  static bool handle_stack_overflow(JavaThread* thread, address addr, address pc,
                                    const void* ucVoid,
                                    address* stub);
};

#endif // OS_POSIX_OS_POSIX_HPP
