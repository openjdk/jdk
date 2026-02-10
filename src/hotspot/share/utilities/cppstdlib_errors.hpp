/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_CPPSTDLIB_ERRORS
#define SHARE_UTILITIES_CPPSTDLIB_ERRORS

// The inclusion of this header will enable (some) assertions and rewire the
// assertions facilities of glibcxx, llvm libcpp, and MSVC STL to the hs_err
// mechanism in a debug build.

// Include <cstddef> to get the stdlib macros for libcxx detection.
#include "cppstdlib/cstddef.hpp"
#include "utilities/debug.hpp"

#ifdef ASSERT
// GNU libcxx
#if defined(__GLIBCXX__) || defined(__GLIBCPP__)
// Redirect glibcxx assert macro. No enabling of hardened mode necessary.
#ifdef __glibcxx_assert
#undef __glibcxx_assert
#endif // __glibcxx_assert
#define __glibcxx_assert(cond) vmassert(cond, "assert in glibcxx")

// LLVM libcpp
#elif defined(_LIBCPP_VERSION)
// Redirect libcpp assert macro to vmassert for older versions of clang.
#ifdef _LIBCPP_ASSERT
#undef _LIBCPP_ASSERT
#endif // _LIBCPP_ASSERT
// _LIBCPP_ASSERT is sometimes used in conjunction with the comma operator,
// so we have to use an expression for the assert.
#define _LIBCPP_ASSERT(cond, msg) \
  vmassert_with_file_and_line_expr(cond, __FILE__, __LINE__, "assert in libcpp: %s", msg)

// Redirect the hardening assert macros to vmassert corresponding to _LIBCPP_HARDENING_MODE_EXTENSIVE
// to vmassert. If we did not redefine these here, we would have to define _LIBCPP_HARDENING_MODE or,
// depending on the clang version (<18), _LIBCPP_ENABLE_HARDENING_MODE using compiler arguments.
// These redefinitinos are preferrable because we do not need to distinguish Xcode and other clang
// toolchains in m4. Because we are redefining we also need not worry about the ordering of includes.
#ifdef _LIBCPP_ASSERT_VALID_INPUT_RANGE
#undef _LIBCPP_ASSERT_VALID_INPUT_RANGE
#endif // _LIBCPP_ASSERT_VALID_INPUT_RANGE
#define _LIBCPP_ASSERT_VALID_INPUT_RANGE(cond, msg) _LIBCPP_ASSERT(cond, msg)
#ifdef _LIBCPP_ASSERT_VALID_ELEMENT_ACCESS
#undef _LIBCPP_ASSERT_VALID_ELEMENT_ACCESS
#endif // _LIBCPP_ASSERT_VALID_ELEMENT_ACCESS
#define _LIBCPP_ASSERT_VALID_ELEMENT_ACCESS(cond, msg) _LIBCPP_ASSERT(cond, msg)

// MSVC
#elif defined(_MSVC_STL_VERSION) || defined(_CPPLIB_VER)
// Turn on hardened mode.
// STL hardening was added by https://github.com/microsoft/STL/commit/dfdccda510737c1e5fe8f84d7101df5aec269451
#if _MSVC_STL_UPDATE > 202502L
# ifdef _MSVC_STL_HARDENING
# undef _MSVC_STL_HARDENING
# endif // _MSVC_STL_HARDENING
# define _MSVC_STL_HARDENING 1
// Additionally, redirect doom function to vmassert.
//# ifdef _MSVC_STL_DOOM_FUNCTION
//# undef _MSVC_STL_DOOM_FUNCTION
//# endif //_MSVC_STL_DOOM_FUNCTION
//# define _MSVC_STL_DOOM_FUNCTION(msg) vmassert(false, "abort in STL: %s", msg)
#else
# ifdef _CONTAINER_DEBUG_LEVEL
# undef _CONTAINER_DEBUG_LEVEL
# endif // _CONTAINER_DEBUG_LEVEL
# define _CONTAINER_DEBUG_LEVEL 1
#endif // _MSVC_STL_UPDATE

// Redirct the STL assertion macro to vmassert.
#ifdef _STL_VERIFY
#undef _STL_VERIFY
#endif // _STL_VERIFY
#define _STL_VERIFY(cond, msg) vmassert(cond, "assert in STL: %s", msg)

#endif // libcxx dispatch
#endif // ASSERT

#endif // SHARE_UTILITIES_CPPSTDLIB_ERRORS
