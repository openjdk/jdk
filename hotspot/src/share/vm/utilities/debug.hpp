/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_UTILITIES_DEBUG_HPP
#define SHARE_VM_UTILITIES_DEBUG_HPP

#include "utilities/globalDefinitions.hpp"
#include "prims/jvm.h"

#include <stdarg.h>

// Simple class to format the ctor arguments into a fixed-sized buffer.
class FormatBufferBase {
 protected:
  char* _buf;
  inline FormatBufferBase(char* buf) : _buf(buf) {}
 public:
  operator const char *() const { return _buf; }
};

// Use resource area for buffer
#define RES_BUFSZ 256
class FormatBufferResource : public FormatBufferBase {
 public:
  FormatBufferResource(const char * format, ...);
};

// Use stack for buffer
template <size_t bufsz = 256>
class FormatBuffer : public FormatBufferBase {
 public:
  inline FormatBuffer(const char * format, ...);
  inline void append(const char* format, ...);
  inline void print(const char* format, ...);
  inline void printv(const char* format, va_list ap);

  char* buffer() { return _buf; }
  int size() { return bufsz; }

 private:
  FormatBuffer(const FormatBuffer &); // prevent copies
  char _buffer[bufsz];

 protected:
  inline FormatBuffer();
};

template <size_t bufsz>
FormatBuffer<bufsz>::FormatBuffer(const char * format, ...) : FormatBufferBase(_buffer) {
  va_list argp;
  va_start(argp, format);
  jio_vsnprintf(_buf, bufsz, format, argp);
  va_end(argp);
}

template <size_t bufsz>
FormatBuffer<bufsz>::FormatBuffer() : FormatBufferBase(_buffer) {
  _buf[0] = '\0';
}

template <size_t bufsz>
void FormatBuffer<bufsz>::print(const char * format, ...) {
  va_list argp;
  va_start(argp, format);
  jio_vsnprintf(_buf, bufsz, format, argp);
  va_end(argp);
}

template <size_t bufsz>
void FormatBuffer<bufsz>::printv(const char * format, va_list argp) {
  jio_vsnprintf(_buf, bufsz, format, argp);
}

template <size_t bufsz>
void FormatBuffer<bufsz>::append(const char* format, ...) {
  // Given that the constructor does a vsnprintf we can assume that
  // _buf is already initialized.
  size_t len = strlen(_buf);
  char* buf_end = _buf + len;

  va_list argp;
  va_start(argp, format);
  jio_vsnprintf(buf_end, bufsz - len, format, argp);
  va_end(argp);
}

// Used to format messages for assert(), guarantee(), fatal(), etc.
typedef FormatBuffer<> err_msg;
typedef FormatBufferResource err_msg_res;

// assertions
#ifdef ASSERT
#ifndef USE_REPEATED_ASSERTS
#define assert(p, msg)                                                       \
do {                                                                         \
  if (!(p)) {                                                                \
    report_vm_error(__FILE__, __LINE__, "assert(" #p ") failed", msg);       \
    BREAKPOINT;                                                              \
  }                                                                          \
} while (0)
#else // #ifndef USE_REPEATED_ASSERTS
#define assert(p, msg)
do {                                                                         \
  for (int __i = 0; __i < AssertRepeat; __i++) {                             \
    if (!(p)) {                                                              \
      report_vm_error(__FILE__, __LINE__, "assert(" #p ") failed", msg);     \
      BREAKPOINT;                                                            \
    }                                                                        \
  }                                                                          \
} while (0)
#endif // #ifndef USE_REPEATED_ASSERTS

// This version of assert is for use with checking return status from
// library calls that return actual error values eg. EINVAL,
// ENOMEM etc, rather than returning -1 and setting errno.
// When the status is not what is expected it is very useful to know
// what status was actually returned, so we pass the status variable as
// an extra arg and use strerror to convert it to a meaningful string
// like "Invalid argument", "out of memory" etc
#define assert_status(p, status, msg)                                        \
do {                                                                         \
  if (!(p)) {                                                                \
    report_vm_error(__FILE__, __LINE__, "assert(" #p ") failed",             \
                    err_msg("error %s(%d) %s", strerror(status),             \
                            status, msg));                                   \
    BREAKPOINT;                                                              \
  }                                                                          \
} while (0)

// Do not assert this condition if there's already another error reported.
#define assert_if_no_error(cond,msg) assert((cond) || is_error_reported(), msg)
#else // #ifdef ASSERT
  #define assert(p,msg)
  #define assert_status(p,status,msg)
  #define assert_if_no_error(cond,msg)
#endif // #ifdef ASSERT

// guarantee is like assert except it's always executed -- use it for
// cheap tests that catch errors that would otherwise be hard to find.
// guarantee is also used for Verify options.
#define guarantee(p, msg)                                                    \
do {                                                                         \
  if (!(p)) {                                                                \
    report_vm_error(__FILE__, __LINE__, "guarantee(" #p ") failed", msg);    \
    BREAKPOINT;                                                              \
  }                                                                          \
} while (0)

#define fatal(msg)                                                           \
do {                                                                         \
  report_fatal(__FILE__, __LINE__, msg);                                     \
  BREAKPOINT;                                                                \
} while (0)

// out of memory
#define vm_exit_out_of_memory(size, vm_err_type, msg)                        \
do {                                                                         \
  report_vm_out_of_memory(__FILE__, __LINE__, size, vm_err_type, msg);       \
  BREAKPOINT;                                                                \
} while (0)

#define ShouldNotCallThis()                                                  \
do {                                                                         \
  report_should_not_call(__FILE__, __LINE__);                                \
  BREAKPOINT;                                                                \
} while (0)

#define ShouldNotReachHere()                                                 \
do {                                                                         \
  report_should_not_reach_here(__FILE__, __LINE__);                          \
  BREAKPOINT;                                                                \
} while (0)

#define Unimplemented()                                                      \
do {                                                                         \
  report_unimplemented(__FILE__, __LINE__);                                  \
  BREAKPOINT;                                                                \
} while (0)

#define Untested(msg)                                                        \
do {                                                                         \
  report_untested(__FILE__, __LINE__, msg);                                  \
  BREAKPOINT;                                                                \
} while (0);


// types of VM error - originally in vmError.hpp
enum VMErrorType {
  INTERNAL_ERROR   = 0xe0000000,
  OOM_MALLOC_ERROR = 0xe0000001,
  OOM_MMAP_ERROR   = 0xe0000002
};

// error reporting helper functions
void report_vm_error(const char* file, int line, const char* error_msg,
                     const char* detail_msg = NULL);
void report_fatal(const char* file, int line, const char* message);
void report_vm_out_of_memory(const char* file, int line, size_t size,
                             VMErrorType vm_err_type, const char* message);
void report_should_not_call(const char* file, int line);
void report_should_not_reach_here(const char* file, int line);
void report_unimplemented(const char* file, int line);
void report_untested(const char* file, int line, const char* message);

void warning(const char* format, ...);

#ifdef ASSERT
// Compile-time asserts.
template <bool> struct StaticAssert;
template <> struct StaticAssert<true> {};

// Only StaticAssert<true> is defined, so if cond evaluates to false we get
// a compile time exception when trying to use StaticAssert<false>.
#define STATIC_ASSERT(cond)                   \
  do {                                        \
    StaticAssert<(cond)> DUMMY_STATIC_ASSERT; \
    (void)DUMMY_STATIC_ASSERT; /* ignore */   \
  } while (false)
#else
#define STATIC_ASSERT(cond)
#endif

// out of shared space reporting
enum SharedSpaceType {
  SharedPermGen,
  SharedReadOnly,
  SharedReadWrite,
  SharedMiscData
};

void report_out_of_shared_space(SharedSpaceType space_type);

// out of memory reporting
void report_java_out_of_memory(const char* message);

// Support for self-destruct
bool is_error_reported();
void set_error_reported();

/* Test assert(), fatal(), guarantee(), etc. */
NOT_PRODUCT(void test_error_handler();)

void pd_ps(frame f);
void pd_obfuscate_location(char *buf, size_t buflen);

#endif // SHARE_VM_UTILITIES_DEBUG_HPP
