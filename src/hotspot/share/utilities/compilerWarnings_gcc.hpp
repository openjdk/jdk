/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

#define PRAGMA_DISABLE_GCC_WARNING(optstring) _Pragma(STR(GCC diagnostic ignored optstring))

#define PRAGMA_DIAG_PUSH             _Pragma("GCC diagnostic push")
#define PRAGMA_DIAG_POP              _Pragma("GCC diagnostic pop")

#if !defined(__clang_major__) && (__GNUC__ >= 12)
// Disable -Wdangling-pointer which is introduced in GCC 12.
#define PRAGMA_DANGLING_POINTER_IGNORED PRAGMA_DISABLE_GCC_WARNING("-Wdangling-pointer")

// Disable -Winfinite-recursion which is introduced in GCC 12.
#define PRAGMA_INFINITE_RECURSION_IGNORED PRAGMA_DISABLE_GCC_WARNING("-Winfinite-recursion")
#endif

#define PRAGMA_FORMAT_NONLITERAL_IGNORED                \
  PRAGMA_DISABLE_GCC_WARNING("-Wformat-nonliteral")     \
  PRAGMA_DISABLE_GCC_WARNING("-Wformat-security")

// Disable -Wstringop-truncation which is introduced in GCC 8.
// https://gcc.gnu.org/gcc-8/changes.html
#if !defined(__clang_major__) && (__GNUC__ >= 8)
#define PRAGMA_STRINGOP_TRUNCATION_IGNORED PRAGMA_DISABLE_GCC_WARNING("-Wstringop-truncation")
#endif

// Disable -Wstringop-overflow which is introduced in GCC 7.
// https://gcc.gnu.org/gcc-7/changes.html
#if !defined(__clang_major__) && (__GNUC__ >= 7)
#define PRAGMA_STRINGOP_OVERFLOW_IGNORED PRAGMA_DISABLE_GCC_WARNING("-Wstringop-overflow")
#endif

#define PRAGMA_NONNULL_IGNORED PRAGMA_DISABLE_GCC_WARNING("-Wnonnull")

#define PRAGMA_ZERO_AS_NULL_POINTER_CONSTANT_IGNORED \
  PRAGMA_DISABLE_GCC_WARNING("-Wzero-as-null-pointer-constant")

#define PRAGMA_DEPRECATED_IGNORED \
  PRAGMA_DISABLE_GCC_WARNING("-Wdeprecated-declarations")

// This macro is used by the NORETURN variants of FORBID_C_FUNCTION.
//
// The [[noreturn]] attribute requires that the first declaration of a
// function has it if any have it.
//
// gcc, clang, and MSVC all provide compiler-specific alternatives to that
// attribute: __attribute__((noreturn)) for gcc and clang,
// __declspec(noreturn) for MSVC and clang. gcc and MSVC treat their
// respective compiler-specific alternatives as satisfying that requirement.
// clang does not.
//
// So clang warns if we use [[noreturn]] in the forbidding declaration and the
// library header has already been included and uses the compiler-specific
// attribute. Similarly, clang warns if we use the compiler-specific attribute
// while the library uses [[noreturn]] and the library header is included
// after the forbidding declaration.
//
// For now, we're only going to worry about the standard library, and not
// noreturn functions in some other library that we might want to forbid in
// the future.  If there's more than one library to be accounted for, then
// things may get more complicated.
//
// There are several ways we could deal with this.
//
// Probably the most robust is to use the same style of noreturn attribute as
// is used by the library providing the function.  That way it doesn't matter
// in which order the inclusion of the library header and the forbidding are
// performed.  We could use configure to determine which to use and provide a
// macro to select on here.
//
// Another approach is to always use __attribute__ noreturn in the forbidding
// declaration, but ensure the relevant library header has been included
// before the forbidding declaration.  Since there are currently only a couple
// of affected functions, this is easier to implement.  So this is the
// approach being taken for now.
//
// clang's failure to treat the compiler-specific form as counting toward the
// [[noreturn]] requirement is arguably a clang bug.
// https://github.com/llvm/llvm-project/issues/131700

#ifdef __clang__
#define FORBIDDEN_FUNCTION_NORETURN_ATTRIBUTE __attribute__((__noreturn__))
#endif

// This macro is used to suppress a warning for some uses of FORBID_C_FUNCTION.
//
// libstdc++ provides inline definitions of some functions to support
// _FORTIFY_SOURCE.  clang warns about our forbidding declaration adding the
// [[deprecated]] attribute following such a definition:
// "warning: attribute declaration must precede definition [-Wignored-attributes]"
// Use this macro to suppress the warning, not getting protection when using
// that combination.  Other build combinations should provide sufficient
// coverage.
//
// clang's warning in this case is arguably a clang bug.
// https://github.com/llvm/llvm-project/issues/135481
// This issue has been fixed, with the fix probably appearing in clang 21.
#if defined(__clang__) && defined(_FORTIFY_SOURCE)
#if _FORTIFY_SOURCE > 0
#define FORBIDDEN_FUNCTION_IGNORE_CLANG_FORTIFY_WARNING \
  PRAGMA_DISABLE_GCC_WARNING("-Wignored-attributes")
#endif
#endif

#endif // SHARE_UTILITIES_COMPILERWARNINGS_GCC_HPP
