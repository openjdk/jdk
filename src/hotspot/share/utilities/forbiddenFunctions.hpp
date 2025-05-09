/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

// Workaround for noreturn functions: exit, _exit, _Exit - see the clang
// definition of FORBIDDEN_FUNCTION_NORETURN_ATTRIBUTE.
#ifdef __clang__
#include <stdlib.h>
#endif

#ifdef _WINDOWS
#include "forbiddenFunctions_windows.hpp"
#else
#include "forbiddenFunctions_posix.hpp"
#endif

// Forbid the use of various C library functions.  Some of these have os::
// replacements that should be used instead.  Others are considered obsolete
// or have security concerns, either with preferred alternatives, or to be
// avoided entirely.

FORBID_IMPORTED_NORETURN_C_FUNCTION(void exit(int), "use os::exit")
FORBID_IMPORTED_NORETURN_C_FUNCTION(void _Exit(int), "use os::exit")

// Windows puts _exit in <stdlib.h>, POSIX in <unistd.h>.
FORBID_IMPORTED_NORETURN_C_FUNCTION(void _exit(int), "use os::exit")

FORBID_IMPORTED_C_FUNCTION(char* strerror(int), "use os::strerror");
FORBID_IMPORTED_C_FUNCTION(char* strtok(char*, const char*), "use strtok_r");

FORBID_C_FUNCTION(int sprintf(char*, const char*, ...), "use os::snprintf");

PRAGMA_DIAG_PUSH
FORBIDDEN_FUNCTION_IGNORE_CLANG_FORTIFY_WARNING
FORBID_C_FUNCTION(int vsprintf(char*, const char*, va_list), "use os::vsnprintf");
FORBID_C_FUNCTION(int vsnprintf(char*, size_t, const char*, va_list), "use os::vsnprintf");
PRAGMA_DIAG_POP

// All of the following functions return raw C-heap pointers (sometimes as an
// option, e.g. realpath or getwd) or, in case of free(), take raw C-heap
// pointers.  We generally want allocation to be done through NMT.
FORBID_IMPORTED_C_FUNCTION(void* malloc(size_t size), "use os::malloc");
FORBID_IMPORTED_C_FUNCTION(void free(void *ptr), "use os::free");
FORBID_IMPORTED_C_FUNCTION(void* calloc(size_t nmemb, size_t size), "use os::malloc and zero out manually");
FORBID_IMPORTED_C_FUNCTION(void* realloc(void *ptr, size_t size), "use os::realloc");
FORBID_IMPORTED_C_FUNCTION(char* strdup(const char *s), "use os::strdup");
FORBID_IMPORTED_C_FUNCTION(wchar_t* wcsdup(const wchar_t *s), "don't use");

#endif // SHARE_UTILITIES_FORBIDDENFUNCTIONS_HPP
