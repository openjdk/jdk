/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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
  static const int BufferSize = 256;
  operator const char *() const { return _buf; }
};

// Use resource area for buffer
class FormatBufferResource : public FormatBufferBase {
 public:
  FormatBufferResource(const char * format, ...) ATTRIBUTE_PRINTF(2, 3);
};

// Use stack for buffer
template <size_t bufsz = FormatBufferBase::BufferSize>
class FormatBuffer : public FormatBufferBase {
 public:
  inline FormatBuffer(const char* format, ...) ATTRIBUTE_PRINTF(2, 3);
  inline void append(const char* format, ...)  ATTRIBUTE_PRINTF(2, 3);
  inline void print(const char* format, ...)  ATTRIBUTE_PRINTF(2, 3);
  inline void printv(const char* format, va_list ap) ATTRIBUTE_PRINTF(2, 0);

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

// Used to format messages.
typedef FormatBuffer<> err_msg;

// assertions
#ifndef ASSERT
#define vmassert(p, ...)
#else
// Note: message says "assert" rather than "vmassert" for backward
// compatibility with tools that parse/match the message text.
// Note: The signature is vmassert(p, format, ...), but the solaris
// compiler can't handle an empty ellipsis in a macro without a warning.
#define vmassert(p, ...)                                                       \
do {                                                                           \
  if (!(p)) {                                                                  \
    report_vm_error(__FILE__, __LINE__, "assert(" #p ") failed", __VA_ARGS__); \
    BREAKPOINT;                                                                \
  }                                                                            \
} while (0)

#endif

// For backward compatibility.
#define assert(p, ...) vmassert(p, __VA_ARGS__)

// This version of vmassert is for use with checking return status from
// library calls that return actual error values eg. EINVAL,
// ENOMEM etc, rather than returning -1 and setting errno.
// When the status is not what is expected it is very useful to know
// what status was actually returned, so we pass the status variable as
// an extra arg and use strerror to convert it to a meaningful string
// like "Invalid argument", "out of memory" etc
#define vmassert_status(p, status, msg) \
  vmassert(p, "error %s(%d), %s", strerror(status), status, msg)

// For backward compatibility.
#define assert_status(p, status, msg) vmassert_status(p, status, msg)

// guarantee is like vmassert except it's always executed -- use it for
// cheap tests that catch errors that would otherwise be hard to find.
// guarantee is also used for Verify options.
#define guarantee(p, ...)                                                         \
do {                                                                              \
  if (!(p)) {                                                                     \
    report_vm_error(__FILE__, __LINE__, "guarantee(" #p ") failed", __VA_ARGS__); \
    BREAKPOINT;                                                                   \
  }                                                                               \
} while (0)

#define fatal(...)                                                                \
do {                                                                              \
  report_fatal(__FILE__, __LINE__, __VA_ARGS__);                                  \
  BREAKPOINT;                                                                     \
} while (0)

// out of memory
#define vm_exit_out_of_memory(size, vm_err_type, ...)                             \
do {                                                                              \
  report_vm_out_of_memory(__FILE__, __LINE__, size, vm_err_type, __VA_ARGS__);    \
  BREAKPOINT;                                                                     \
} while (0)

#define ShouldNotCallThis()                                                       \
do {                                                                              \
  report_should_not_call(__FILE__, __LINE__);                                     \
  BREAKPOINT;                                                                     \
} while (0)

#define ShouldNotReachHere()                                                      \
do {                                                                              \
  report_should_not_reach_here(__FILE__, __LINE__);                               \
  BREAKPOINT;                                                                     \
} while (0)

#define Unimplemented()                                                           \
do {                                                                              \
  report_unimplemented(__FILE__, __LINE__);                                       \
  BREAKPOINT;                                                                     \
} while (0)

#define Untested(msg)                                                             \
do {                                                                              \
  report_untested(__FILE__, __LINE__, msg);                                       \
  BREAKPOINT;                                                                     \
} while (0);


// types of VM error - originally in vmError.hpp
enum VMErrorType {
  INTERNAL_ERROR   = 0xe0000000,
  OOM_MALLOC_ERROR = 0xe0000001,
  OOM_MMAP_ERROR   = 0xe0000002
};

// error reporting helper functions
void report_vm_error(const char* file, int line, const char* error_msg);
#if !defined(__GNUC__) || defined (__clang_major__) || (((__GNUC__ == 4) && (__GNUC_MINOR__ >= 8)) || __GNUC__ > 4)
// ATTRIBUTE_PRINTF works with gcc >= 4.8 and any other compiler.
void report_vm_error(const char* file, int line, const char* error_msg,
                     const char* detail_fmt, ...) ATTRIBUTE_PRINTF(4, 5);
#else
// GCC < 4.8 warns because of empty format string.  Warning can not be switched off selectively.
void report_vm_error(const char* file, int line, const char* error_msg,
                     const char* detail_fmt, ...);
#endif
void report_fatal(const char* file, int line, const char* detail_fmt, ...) ATTRIBUTE_PRINTF(3, 4);
void report_vm_out_of_memory(const char* file, int line, size_t size, VMErrorType vm_err_type,
                             const char* detail_fmt, ...) ATTRIBUTE_PRINTF(5, 6);
void report_should_not_call(const char* file, int line);
void report_should_not_reach_here(const char* file, int line);
void report_unimplemented(const char* file, int line);
void report_untested(const char* file, int line, const char* message);

void warning(const char* format, ...) ATTRIBUTE_PRINTF(1, 2);

// Compile-time asserts.  Cond must be a compile-time constant expression that
// is convertible to bool.  STATIC_ASSERT() can be used anywhere a declaration
// may appear.
//
// Implementation Note: STATIC_ASSERT_FAILURE<true> provides a value member
// rather than type member that could be used directly in the typedef, because
// a type member would require conditional use of "typename", depending on
// whether Cond is dependent or not.  The use of a value member leads to the
// use of an array type.

template<bool x> struct STATIC_ASSERT_FAILURE;
template<> struct STATIC_ASSERT_FAILURE<true> { enum { value = 1 }; };

#define STATIC_ASSERT(Cond) \
  typedef char PASTE_TOKENS(STATIC_ASSERT_DUMMY_TYPE_, __LINE__)[ \
    STATIC_ASSERT_FAILURE< (Cond) >::value ]

// out of shared space reporting
enum SharedSpaceType {
  SharedReadOnly,
  SharedReadWrite,
  SharedMiscData,
  SharedMiscCode
};

void report_out_of_shared_space(SharedSpaceType space_type);

void report_insufficient_metaspace(size_t required_size);

// out of memory reporting
void report_java_out_of_memory(const char* message);

// Support for self-destruct
bool is_error_reported();
void set_error_reported();

/* Test vmassert(), fatal(), guarantee(), etc. */
NOT_PRODUCT(void test_error_handler();)

// crash in a controlled way:
// how can be one of:
// 1,2 - asserts
// 3,4 - guarantee
// 5-7 - fatal
// 8 - vm_exit_out_of_memory
// 9 - ShouldNotCallThis
// 10 - ShouldNotReachHere
// 11 - Unimplemented
// 12,13 - (not guaranteed) crashes
// 14 - SIGSEGV
// 15 - SIGFPE
NOT_PRODUCT(void controlled_crash(int how);)

// returns an address which is guaranteed to generate a SIGSEGV on read,
// for test purposes, which is not NULL and contains bits in every word
NOT_PRODUCT(void* get_segfault_address();)

void pd_ps(frame f);
void pd_obfuscate_location(char *buf, size_t buflen);

class outputStream;
void print_native_stack(outputStream* st, frame fr, Thread* t, char* buf, int buf_size);

#endif // SHARE_VM_UTILITIES_DEBUG_HPP
