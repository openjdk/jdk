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

#ifndef OS_POSIX_FORBIDDENFUNCTIONS_POSIX_HPP
#define OS_POSIX_FORBIDDENFUNCTIONS_POSIX_HPP

#include "utilities/compilerWarnings.hpp"

// For types used in the signatures.
#include <stddef.h>

// Workaround for noreturn functions: _exit - see the clang
// definition of FORBIDDEN_FUNCTION_NORETURN_ATTRIBUTE.
#ifdef __clang__
#include <unistd.h>
#endif

// If needed, add os::strndup and use that instead.
FORBID_C_FUNCTION(char* strndup(const char*, size_t), "don't use");

// These are unimplementable for Windows, and they aren't useful for a
// POSIX implementation of NMT either.
// https://stackoverflow.com/questions/62962839/stdaligned-alloc-missing-from-visual-studio-2019
FORBID_C_FUNCTION(int posix_memalign(void**, size_t, size_t), "don't use");
FORBID_C_FUNCTION(void* aligned_alloc(size_t, size_t), "don't use");

// realpath with a null second argument mallocs a string for the result.
// With a non-null second argument, there is a risk of buffer overrun.
PRAGMA_DIAG_PUSH
FORBIDDEN_FUNCTION_IGNORE_CLANG_FORTIFY_WARNING
FORBID_C_FUNCTION(char* realpath(const char*, char*), "use os::realpath");
PRAGMA_DIAG_POP

// Returns a malloc'ed string.
FORBID_C_FUNCTION(char* get_current_dir_name(), "use os::get_current_directory");

// Problematic API that should never be used.
FORBID_C_FUNCTION(char* getwd(char*), "use os::get_current_directory");

// BSD utility that is subtly different from realloc.
FORBID_C_FUNCTION(void* reallocf(void*, size_t), "use os::realloc");

#endif // OS_POSIX_FORBIDDENFUNCTIONS_POSIX_HPP
