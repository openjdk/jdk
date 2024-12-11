/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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

#define PRAGMA_FORMAT_IGNORED PRAGMA_DISABLE_GCC_WARNING("-Wformat")

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

// This variant of FORBID_C_FUNCTION overrides the default variant.  Clang
// seems to make a distinction between [[noreturn]] and the old-style noreturn
// attribute.  For example, if <stdlib.h> has already been included, using
// [[noreturn]] when forbidding exit(int) gives "error: 'noreturn' attribute
// does not appear on the first declaration", with the previous declaration
// reported as being "void exit(int) __dead2;". __dead2 is an old-style
// noreturn attribute.
#ifdef __clang__
#define FORBID_NORETURN_C_FUNCTION(Signature, Alternative) \
  FORBID_C_FUNCTION(__attribute__((__noreturn__)) Signature, Alternative)
#endif // __clang__

#endif // SHARE_UTILITIES_COMPILERWARNINGS_GCC_HPP
