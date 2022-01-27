/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_COMPILERWARNINGS_GCC_HPP
#define SHARE_UTILITIES_COMPILERWARNINGS_GCC_HPP

// Macros related to control of compiler warnings.

#ifndef ATTRIBUTE_PRINTF
#define ATTRIBUTE_PRINTF(fmt,vargs)  __attribute__((format(printf, fmt, vargs)))
#endif
#ifndef ATTRIBUTE_SCANF
#define ATTRIBUTE_SCANF(fmt,vargs)  __attribute__((format(scanf, fmt, vargs)))
#endif

#define PRAGMA_DISABLE_GCC_WARNING_AUX(x) _Pragma(#x)
#define PRAGMA_DISABLE_GCC_WARNING(option_string) \
  PRAGMA_DISABLE_GCC_WARNING_AUX(GCC diagnostic ignored option_string)

#define PRAGMA_FORMAT_NONLITERAL_IGNORED                \
  PRAGMA_DISABLE_GCC_WARNING("-Wformat-nonliteral")     \
  PRAGMA_DISABLE_GCC_WARNING("-Wformat-security")

#define PRAGMA_FORMAT_IGNORED PRAGMA_DISABLE_GCC_WARNING("-Wformat")

// Disable -Wstringop-truncation which is introduced in GCC 8.
// https://gcc.gnu.org/gcc-8/changes.html
#if !defined(__clang_major__) && (__GNUC__ >= 8)
#define PRAGMA_STRINGOP_TRUNCATION_IGNORED PRAGMA_DISABLE_GCC_WARNING("-Wstringop-truncation")
#endif

#define PRAGMA_NONNULL_IGNORED \
  PRAGMA_DISABLE_GCC_WARNING("-Wnonnull")

#if defined(__clang_major__) && \
      (__clang_major__ >= 4 || \
      (__clang_major__ >= 3 && __clang_minor__ >= 1)) || \
    ((__GNUC__ == 4) && (__GNUC_MINOR__ >= 6)) || (__GNUC__ > 4)
// Tested to work with clang version 3.1 and better.
#define PRAGMA_DIAG_PUSH             _Pragma("GCC diagnostic push")
#define PRAGMA_DIAG_POP              _Pragma("GCC diagnostic pop")

#endif // clang/gcc version check

#if __GNUC__ >= 9

// AIX also needs a 64 bit NULL to work as a null address pointer.
// Most system includes on AIX would define it as an int 0 if not already defined with one
// exception: /usr/include/dirent.h will unconditionally redefine NULL to int 0 again.
// In this case you need to copy the following defines to a position after #include <dirent.h>
#include <dirent.h>
#ifdef _LP64
  #undef NULL
  #define NULL 0L
#else
  #ifndef NULL
    #define NULL 0
  #endif
#endif

#include <sys/socket.h>
#include <stdio.h>

#define FORBID_C_FUNCTION(signature, alternative) \
  extern "C" signature __attribute__((__warning__(alternative)))

#define PRAGMA_PERMIT_FORBIDDEN_C_FUNCTION(name) \
  PRAGMA_DISABLE_GCC_WARNING("-Wattribute-warning")

FORBID_C_FUNCTION(int      closedir(DIR*),                            "use os::closedir");
FORBID_C_FUNCTION(int      connect(int, const struct sockaddr*, socklen_t), "use os::connect");
FORBID_C_FUNCTION(FILE*    fdopen(int, const char*),                  "use os::fdopen");
FORBID_C_FUNCTION(void     flockfile(FILE*),                          "use os::flockfile");
FORBID_C_FUNCTION(FILE*    fopen(const char*, const char*),           "use os::fopen");
FORBID_C_FUNCTION(int      fsync(int),                                "use os::fsync");
FORBID_C_FUNCTION(int      ftruncate(int, off_t),                     "use os::ftruncate");
FORBID_C_FUNCTION(void     funlockfile(FILE *),                       "use os::funlockfile");
FORBID_C_FUNCTION(off_t    lseek(int, off_t, int),                    "use os::lseek");
FORBID_C_FUNCTION(DIR*     opendir(const char *),                     "use os::opendir");
FORBID_C_FUNCTION(long int random(void),                              "use os::random");
FORBID_C_FUNCTION(struct dirent* readdir(DIR*),                       "use os::readdir");
FORBID_C_FUNCTION(ssize_t  recv(int, void*, size_t, int),             "use os::recv");
FORBID_C_FUNCTION(int      stat(const char*, struct stat*),           "use os::stat");
FORBID_C_FUNCTION(ssize_t  send(int, const void*, size_t, int),       "use os::send");
FORBID_C_FUNCTION(char*    strerror(int),                             "use os::strerror");
FORBID_C_FUNCTION(ssize_t  write(int, const void*, size_t ),          "use os::write");

FORBID_C_FUNCTION(char*    strtok(char*, const char*),                "use strtok_r");

#else

#define FORBID_C_FUNCTION(signature, alternative)
#define PRAGMA_PERMIT_FORBIDDEN_C_FUNCTION(name)

#endif // __GNUC__ >= 9

#endif // SHARE_UTILITIES_COMPILERWARNINGS_GCC_HPP
