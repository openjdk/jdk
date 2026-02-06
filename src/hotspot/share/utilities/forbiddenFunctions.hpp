/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_FORBIDDENFUNCTIONS_HPP
#define SHARE_UTILITIES_FORBIDDENFUNCTIONS_HPP

#include "utilities/compilerWarnings.hpp"
#include "utilities/macros.hpp"

// For types used in the signatures.
#include <stdarg.h>
#include <stddef.h>

#ifdef _WINDOWS
#include "forbiddenFunctions_windows.hpp"
#else
#include "forbiddenFunctions_posix.hpp"
#endif

// Forbid the use of various C library functions.  Some of these have os::
// replacements that should be used instead.  Others are considered obsolete
// or have security concerns, either with preferred alternatives, or to be
// avoided entirely.

FORBID_IMPORTED_C_FUNCTION(char* strerror(int), noexcept, "use os::strerror");
FORBID_IMPORTED_C_FUNCTION(char* strtok(char*, const char*), noexcept, "use strtok_r");

// AIX declarations for sprintf and snprintf are not noexcept, which is
// inconsistent with most other system header declarations, including being
// inconsistent with vsprintf and fsnprintf.
FORBID_C_FUNCTION(int sprintf(char*, const char*, ...), NOT_AIX(noexcept), "use os::snprintf");
FORBID_C_FUNCTION(int snprintf(char*, size_t, const char*, ...), NOT_AIX(noexcept), "use os::snprintf");

PRAGMA_DIAG_PUSH
FORBIDDEN_FUNCTION_IGNORE_CLANG_FORTIFY_WARNING
FORBID_C_FUNCTION(int vsprintf(char*, const char*, va_list), noexcept, "use os::vsnprintf");
FORBID_C_FUNCTION(int vsnprintf(char*, size_t, const char*, va_list), noexcept, "use os::vsnprintf");
PRAGMA_DIAG_POP

// All of the following functions return raw C-heap pointers.  We generally
// want allocation to be done through NMT.
FORBID_IMPORTED_C_FUNCTION(char* strdup(const char *s), noexcept, "use os::strdup");
FORBID_IMPORTED_C_FUNCTION(wchar_t* wcsdup(const wchar_t *s), noexcept, "don't use");

// Disallow non-wrapped raw library function.
MACOS_AARCH64_ONLY(FORBID_C_FUNCTION(void pthread_jit_write_protect_np(int enabled), noexcept, \
                                     "use os::current_thread_enable_wx");)

#endif // SHARE_UTILITIES_FORBIDDENFUNCTIONS_HPP
