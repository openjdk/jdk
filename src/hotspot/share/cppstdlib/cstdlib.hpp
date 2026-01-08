/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CPPSTDLIB_CSTDLIB_HPP
#define SHARE_CPPSTDLIB_CSTDLIB_HPP

#include "utilities/compilerWarnings.hpp"

// HotSpot usage for <cstdlib>:
//
// Some functions are explicitly forbidden below. That may not be a complete
// list of all the functions we should forbid.
//
// We assume <cstdlib> provides definitions in the global namespace, in
// addition to providing them in the std namespace. We prefer to use the names
// in the global namespace.

BEGIN_ALLOW_FORBIDDEN_FUNCTIONS
#include "utilities/vmassert_uninstall.hpp"

#include <cstdlib>

#include "utilities/vmassert_reinstall.hpp" // don't reorder
END_ALLOW_FORBIDDEN_FUNCTIONS

// AIX may define malloc and calloc as macros when certain other features are
// present, causing us all sorts of grief.
// https://www.ibm.com/docs/en/openxl-c-and-cpp-aix/17.1.4?topic=compilers-memory-allocation
// Replace the macro definitions with something we can work with.
// AIX 7.3 no longer uses macro renaming when building with clang, instead
// using the same asm replacement approach as used below.  This workaround can
// be removed once earlier versions are no longer supported as build platforms.
#if defined(AIX) && (defined(__VEC__) || defined(__AIXVEC))
#if defined(malloc) || defined(calloc)
#if !defined(malloc) || !defined(calloc)
#error "Inconsistent alloc macro mappings, expected both to be mapped."
#endif
// Remove the macros.
#undef malloc
#undef calloc
// Implement the mapping using gcc/clang asm name mapping.
extern "C" {
extern void* malloc(size_t) noexcept asm("vec_malloc");
extern void* calloc(size_t, size_t) noexcept asm("vec_calloc");
} // extern "C"
// Because the macros are in place when <cstdlib> brings names into the std
// namespace, macro replacement causes the expanded names to be added instead
// of the intended names. We can't remove std::vec_malloc and std::vec_calloc,
// but we do add the standard names in case someone uses them.
namespace std {
using ::malloc;
using ::calloc;
} // namespace std
#endif // Macro definition for malloc or calloc
#endif // AIX altivec allocator support

// Prefer os:: variants of these.
FORBID_IMPORTED_NORETURN_C_FUNCTION(void exit(int), noexcept, "use os::exit")
FORBID_IMPORTED_NORETURN_C_FUNCTION(void _Exit(int), noexcept, "use os::exit")

// Windows puts _exit in <stdlib.h>. POSIX puts it in <unistd.h>.
// We can't forbid it here when using clang if it's not in <stdlib.h> - see
// the clang definition for FORBIDDEN_FUNCTION_NORETURN_ATTRIBUTE.
#ifdef _WINDOWS
FORBID_IMPORTED_NORETURN_C_FUNCTION(void _exit(int), /* not noexcept */, "use os::exit")
#endif // _WINDOWS

// These functions return raw C-heap pointers or, in case of free(), take raw
// C-heap pointers.  We generally want allocation to be done through NMT, using
// os::malloc and friends.
FORBID_IMPORTED_C_FUNCTION(void* malloc(size_t), noexcept, "use os::malloc");
FORBID_IMPORTED_C_FUNCTION(void free(void*), noexcept, "use os::free");
FORBID_IMPORTED_C_FUNCTION(void* calloc(size_t, size_t), noexcept, "use os::malloc and zero out manually");
FORBID_IMPORTED_C_FUNCTION(void* realloc(void*, size_t), noexcept, "use os::realloc");

// These are not provided (and are unimplementable?) by Windows.
// https://stackoverflow.com/questions/62962839/stdaligned-alloc-missing-from-visual-studio-2019
// They also aren't useful for a POSIX implementation of NMT.
#ifndef _WINDOWS
FORBID_C_FUNCTION(void* aligned_alloc(size_t, size_t), noexcept, "don't use");
FORBID_C_FUNCTION(int posix_memalign(void**, size_t, size_t), noexcept, "don't use");
#endif // !_WINDOWS

#endif // SHARE_CPPSTDLIB_CSTDLIB_HPP
