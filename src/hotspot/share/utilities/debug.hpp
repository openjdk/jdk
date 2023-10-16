/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_DEBUG_HPP
#define SHARE_UTILITIES_DEBUG_HPP

#include "utilities/attributeNoreturn.hpp"
#include "utilities/breakpoint.hpp"
#include "utilities/compilerWarnings.hpp"
#include "utilities/macros.hpp"

#include <stddef.h>
#include <stdint.h>

class oopDesc;

// ShowRegistersOnAssert support (for now Linux only)
#if defined(LINUX) && !defined(ZERO)
#define CAN_SHOW_REGISTERS_ON_ASSERT
extern char* g_assert_poison;
#define TOUCH_ASSERT_POISON (*g_assert_poison) = 'X';
void initialize_assert_poison();
void disarm_assert_poison();
bool handle_assert_poison_fault(const void* ucVoid, const void* faulting_address);
#else
#define TOUCH_ASSERT_POISON
#endif // CAN_SHOW_REGISTERS_ON_ASSERT

// The DebuggingContext class provides a mechanism for temporarily disabling
// asserts and various consistency checks.  Ordinarily that would be a really
// bad idea, but it's essential for some of the debugging commands provided by
// HotSpot.  (See the Command class in debug.cpp.) These commands are intended
// to be invoked from the debugger while the program is otherwise stopped.
// The commands may invoke operations while the program is in a state where
// those operations are not normally permitted, with the state checked by an
// assert.  We want the debugging commands to bypass those checks.
class DebuggingContext {
  static int _enabled;          // Nesting counter.

public:
  DebuggingContext();
  ~DebuggingContext();
  // Asserts and other code use this to determine whether to bypass checks
  // that would otherwise lead to program termination.
  static bool is_enabled() { return _enabled > 0; }
};

// VMASSERT_CHECK_PASSED(P) provides the mechanism by which DebuggingContext
// disables asserts.  It returns true if P is true or DebuggingContext is
// enabled.  Assertion failure is reported if it returns false, terminating
// the program.
//
// The DebuggingContext check being enabled isn't placed inside the report
// function, as that would prevent the report function from being noreturn.
// The report function should be noreturn so there isn't a control path to the
// assertion's continuation that has P being false.  Otherwise, the compiler
// might logically split the continuation to include that path explicitly,
// possibly leading to discovering (and warning about) invalid code.  For
// example, if P := x != nullptr, and the continuation contains a dereference
// of x, the compiler might warn because there is a control path (!P -> report
// -> continuation) where that dereference is known to be invalid.  (Of
// course, if execution actually took that path things would go wrong, but
// that's the risk the DebuggingContext mechanism takes.)
//
// Similarly, the check for enabled DebuggingContext shouldn't follow P.
// Having this macro expand to `P || DebuggingContext::is_enabled()` has the
// same problem of a control path through !P to the assertion's continuation.
//
// But it can't be just `DebuggingContext::is_enabled() || P` either.  That
// prevents the compiler from inferring based on P that it is true in the
// continuation.  But it also prevents the use of assertions in constexpr
// contexts, since that expression is not constexpr.
//
// We could accomodate constexpr usage with std::is_constant_evaluated() (from
// C++20). Unfortunately, we don't currently support C++20.  However, most
// supported compilers have implemented it, and that implementation uses a
// compiler intrinsic that we can use directly without otherwise using C++20.
//
// Note that if we could use std::is_constant_evaluated() then we could just
// use this definition for DebuggingContext::is_enabled:
//   static constexpr bool is_enabled() {
//     return !std::is_constant_evaluated() && _enabled;
//   }
// The idea being that we are definitely not executing for debugging if doing
// constant evaluation in the compiler. We don't do something like that now,
// because we need a fallback when we don't have any mechanism for detecting
// constant evaluation.
#if defined(TARGET_COMPILER_gcc) || defined(TARGET_COMPILER_xlc)

// gcc10 added both __has_builtin and __builtin_is_constant_evaluated.
// clang has had __has_builtin for a long time, so likely also in xlclang++.
// Similarly, clang has had __builtin_is_constant_evaluated for a long time.

#ifdef __has_builtin
#if __has_builtin(__builtin_is_constant_evaluated)
#define VMASSERT_CHECK_PASSED(p) \
  ((! __builtin_is_constant_evaluated() && DebuggingContext::is_enabled()) || (p))
#endif
#endif

#elif defined(TARGET_COMPILER_visCPP)

// std::is_constant_evaluated() and it's associated intrinsic are available in
// VS 2019 16.5.  The minimum supported version of VS 2019 is already past
// that, so we can rely on the intrinsic being available.
#define VMASSERT_CHECK_PASSED(p) \
  ((! __builtin_is_constant_evaluated() && DebuggingContext::is_enabled()) || (p))

#endif // End dispatch on TARGET_COMPILER_xxx

// If we don't have a way to detect constant evaluation, then fall back to the
// less than ideal form of the check, and hope it works.  This succeeds at
// least for gcc.  The support needed to use the above definition was added in
// gcc10. The problems arising from analyzing the failed P case don't seem to
// appear until gcc12.  An alternative is to not provide DebuggingContext
// support for such a configuration.
#ifndef VMASSERT_CHECK_PASSED
#define VMASSERT_CHECK_PASSED(p) ((p) || DebuggingContext::is_enabled())
#endif

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
  if (! VMASSERT_CHECK_PASSED(p)) {                                            \
    TOUCH_ASSERT_POISON;                                                       \
    report_vm_error(__FILE__, __LINE__, "assert(" #p ") failed", __VA_ARGS__); \
  }                                                                            \
} while (0)
#endif

// For backward compatibility.
#define assert(p, ...) vmassert(p, __VA_ARGS__)

#define precond(p)   assert(p, "precond")
#define postcond(p)  assert(p, "postcond")

#ifndef ASSERT
#define vmassert_status(p, status, msg)
#else
// This version of vmassert is for use with checking return status from
// library calls that return actual error values eg. EINVAL,
// ENOMEM etc, rather than returning -1 and setting errno.
// When the status is not what is expected it is very useful to know
// what status was actually returned, so we pass the status variable as
// an extra arg and use strerror to convert it to a meaningful string
// like "Invalid argument", "out of memory" etc
#define vmassert_status(p, status, msg) \
do {                                                                           \
  if (! VMASSERT_CHECK_PASSED(p)) {                                            \
    TOUCH_ASSERT_POISON;                                                       \
    report_vm_status_error(__FILE__, __LINE__, "assert(" #p ") failed",        \
                           status, msg);                                       \
  }                                                                            \
} while (0)
#endif // ASSERT

// For backward compatibility.
#define assert_status(p, status, msg) vmassert_status(p, status, msg)

// guarantee is like vmassert except it's always executed -- use it for
// cheap tests that catch errors that would otherwise be hard to find.
// guarantee is also used for Verify options.
// guarantee is not subject to DebuggingContext bypass.
#define guarantee(p, ...)                                                         \
do {                                                                              \
  if (!(p)) {                                                                     \
    TOUCH_ASSERT_POISON;                                                          \
    report_vm_error(__FILE__, __LINE__, "guarantee(" #p ") failed", __VA_ARGS__); \
  }                                                                               \
} while (0)

#define fatal(...)                                                                \
do {                                                                              \
  TOUCH_ASSERT_POISON;                                                            \
  report_fatal(INTERNAL_ERROR, __FILE__, __LINE__, __VA_ARGS__);                  \
} while (0)

// out of memory
#define vm_exit_out_of_memory(size, vm_err_type, ...)                             \
do {                                                                              \
  report_vm_out_of_memory(__FILE__, __LINE__, size, vm_err_type, __VA_ARGS__);    \
} while (0)

#define check_with_errno(check_type, cond, msg)                                   \
  do {                                                                            \
    int err = errno;                                                              \
    check_type(cond, "%s; error='%s' (errno=%s)", msg, os::strerror(err),         \
               os::errno_name(err));                                              \
} while (false)

#define assert_with_errno(cond, msg)    check_with_errno(assert, cond, msg)
#define guarantee_with_errno(cond, msg) check_with_errno(guarantee, cond, msg)

#define ShouldNotCallThis()                                                       \
do {                                                                              \
  TOUCH_ASSERT_POISON;                                                            \
  report_should_not_call(__FILE__, __LINE__);                                     \
} while (0)

#define ShouldNotReachHere()                                                      \
do {                                                                              \
  TOUCH_ASSERT_POISON;                                                            \
  report_should_not_reach_here(__FILE__, __LINE__);                               \
} while (0)

#define Unimplemented()                                                           \
do {                                                                              \
  TOUCH_ASSERT_POISON;                                                            \
  report_unimplemented(__FILE__, __LINE__);                                       \
} while (0)

#define Untested(msg)                                                             \
do {                                                                              \
  report_untested(__FILE__, __LINE__, msg);                                       \
  BREAKPOINT;                                                                     \
} while (0);


// types of VM error - originally in vmError.hpp
enum VMErrorType : unsigned int {
  INTERNAL_ERROR   = 0xe0000000,
  OOM_MALLOC_ERROR = 0xe0000001,
  OOM_MMAP_ERROR   = 0xe0000002,
  OOM_MPROTECT_ERROR = 0xe0000003,
  OOM_JAVA_HEAP_FATAL = 0xe0000004,
  OOM_HOTSPOT_ARENA = 0xe0000005
};

// error reporting helper functions
ATTRIBUTE_NORETURN
void report_vm_error(const char* file, int line, const char* error_msg);

ATTRIBUTE_NORETURN
ATTRIBUTE_PRINTF(4, 5)
void report_vm_error(const char* file, int line, const char* error_msg,
                     const char* detail_fmt, ...);

ATTRIBUTE_NORETURN
void report_vm_status_error(const char* file, int line, const char* error_msg,
                            int status, const char* detail);

ATTRIBUTE_NORETURN
ATTRIBUTE_PRINTF(4, 5)
void report_fatal(VMErrorType error_type, const char* file, int line, const char* detail_fmt, ...);

ATTRIBUTE_NORETURN
ATTRIBUTE_PRINTF(5, 6)
void report_vm_out_of_memory(const char* file, int line, size_t size, VMErrorType vm_err_type,
                             const char* detail_fmt, ...);

ATTRIBUTE_NORETURN void report_should_not_call(const char* file, int line);
ATTRIBUTE_NORETURN void report_should_not_reach_here(const char* file, int line);
ATTRIBUTE_NORETURN void report_unimplemented(const char* file, int line);

// NOT ATTRIBUTE_NORETURN
void report_untested(const char* file, int line, const char* message);

ATTRIBUTE_PRINTF(1, 2)
void warning(const char* format, ...);

#define STATIC_ASSERT(Cond) static_assert((Cond), #Cond)

// out of memory reporting
void report_java_out_of_memory(const char* message);

// Returns true iff the address p is readable and *(intptr_t*)p != errvalue
extern "C" bool dbg_is_safe(const void* p, intptr_t errvalue);
extern "C" bool dbg_is_good_oop(oopDesc* o);

#endif // SHARE_UTILITIES_DEBUG_HPP
