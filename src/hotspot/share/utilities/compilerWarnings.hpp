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

#ifndef SHARE_UTILITIES_COMPILERWARNINGS_HPP
#define SHARE_UTILITIES_COMPILERWARNINGS_HPP

// Macros related to control of compiler warnings.

#include "utilities/macros.hpp"

#include COMPILER_HEADER(utilities/compilerWarnings)

// Defaults when not defined for the TARGET_COMPILER_xxx.

#ifndef PRAGMA_DIAG_PUSH
#define PRAGMA_DIAG_PUSH
#endif
#ifndef PRAGMA_DIAG_POP
#define PRAGMA_DIAG_POP
#endif

#ifndef PRAGMA_DISABLE_GCC_WARNING
#define PRAGMA_DISABLE_GCC_WARNING(name)
#endif

#ifndef PRAGMA_DISABLE_MSVC_WARNING
#define PRAGMA_DISABLE_MSVC_WARNING(num)
#endif

#ifndef ATTRIBUTE_PRINTF
#define ATTRIBUTE_PRINTF(fmt, vargs)
#endif
#ifndef ATTRIBUTE_SCANF
#define ATTRIBUTE_SCANF(fmt, vargs)
#endif

#ifndef PRAGMA_DANGLING_POINTER_IGNORED
#define PRAGMA_DANGLING_POINTER_IGNORED
#endif

#ifndef PRAGMA_FORMAT_NONLITERAL_IGNORED
#define PRAGMA_FORMAT_NONLITERAL_IGNORED
#endif

#ifndef PRAGMA_STRINGOP_TRUNCATION_IGNORED
#define PRAGMA_STRINGOP_TRUNCATION_IGNORED
#endif

#ifndef PRAGMA_STRINGOP_OVERFLOW_IGNORED
#define PRAGMA_STRINGOP_OVERFLOW_IGNORED
#endif

#ifndef PRAGMA_INFINITE_RECURSION_IGNORED
#define PRAGMA_INFINITE_RECURSION_IGNORED
#endif

#ifndef PRAGMA_NONNULL_IGNORED
#define PRAGMA_NONNULL_IGNORED
#endif

#ifndef PRAGMA_ZERO_AS_NULL_POINTER_CONSTANT_IGNORED
#define PRAGMA_ZERO_AS_NULL_POINTER_CONSTANT_IGNORED
#endif

// Support warnings for use of certain C functions, except where explicitly
// permitted.

// FORBID_C_FUNCTION(Signature, Noexcept, Alternative)
// - Signature: the function that should not normally be used.
// - Noexcept: either the token `noexcept` or nothing. See below.
// - Alternative: a string literal that may be used in a warning about a use,
//   often suggesting an alternative.
// Declares the C-linkage function designated by Signature to be deprecated,
// using the `deprecated` attribute with Alternative as an argument.
//
// The Noexcept argument is used to deal with differences among the standard
// libraries of various platforms.  For example, the C standard library on
// Linux declares many functions `noexcept`. Windows and BSD C standard
// libraries don't include exception specifications at all. This matters
// because some compilers reject (some) differences between declarations that
// differ in the exception specification. clang complains if the first
// declaration is not noexcept while some later declaration is, but not the
// reverse. gcc doesn't seem to care. (Maybe that's a gcc bug?) So if the
// forbidding declaration differs from the platform's library then we may get
// errors building with clang (but not gcc), depending on the difference and
// the include order.
//
// The variants with IMPORTED in the name are to deal with Windows
// requirements, using FORBIDDEN_FUNCTION_IMPORT_SPEC.  See the Visual
// Studio definition of that macro for more details.  The default has
// an empty expansion.  The potentially added spec must precede the
// base signature but follow all attributes.
//
// FORBID_NORETURN_C_FUNCTION deals with a clang issue.  See the clang
// definition of FORBIDDEN_FUNCTION_NORETURN_ATTRIBUTE for more
// details.  The default expands to `[[noreturn]]`.
#define FORBID_C_FUNCTION(Signature, Noexcept, Alternative)     \
  extern "C" {                                                  \
    [[deprecated(Alternative)]]                                 \
    Signature                                                   \
    /* 2-step pasting to avoid expansion of FFCN => nothing. */ \
    PASTE_TOKENS(                                               \
      FORBIDDEN_FUNCTION_,                                      \
      PASTE_TOKENS(COND_NOEXCEPT_, Noexcept))                   \
    ;                                                           \
  }

// Both Linux and AIX C libraries declare functions noexcept.
// Neither BSD nor Windows C libraries declare functions noexcept.
#define FORBIDDEN_FUNCTION_COND_NOEXCEPT_noexcept NOT_WINDOWS(NOT_BSD(noexcept))
#define FORBIDDEN_FUNCTION_COND_NOEXCEPT_

#ifndef FORBIDDEN_FUNCTION_IMPORT_SPEC
#define FORBIDDEN_FUNCTION_IMPORT_SPEC
#endif

#ifndef FORBIDDEN_FUNCTION_NORETURN_ATTRIBUTE
#define FORBIDDEN_FUNCTION_NORETURN_ATTRIBUTE [[noreturn]]
#endif

#ifndef FORBIDDEN_FUNCTION_IGNORE_CLANG_FORTIFY_WARNING
#define FORBIDDEN_FUNCTION_IGNORE_CLANG_FORTIFY_WARNING
#endif

#define FORBID_IMPORTED_C_FUNCTION(Signature, Noexcept, Alternative) \
  FORBID_C_FUNCTION(FORBIDDEN_FUNCTION_IMPORT_SPEC Signature, Noexcept, Alternative)

#define FORBID_NORETURN_C_FUNCTION(Signature, Noexcept, Alternative) \
  FORBID_C_FUNCTION(FORBIDDEN_FUNCTION_NORETURN_ATTRIBUTE Signature, Noexcept, Alternative)

#define FORBID_IMPORTED_NORETURN_C_FUNCTION(Signature, Noexcept, Alternative) \
  FORBID_NORETURN_C_FUNCTION(FORBIDDEN_FUNCTION_IMPORT_SPEC Signature, Noexcept, Alternative)

// A BEGIN/END_ALLOW_FORBIDDEN_FUNCTIONS pair establishes a scope in which the
// deprecation warnings used to forbid the use of certain functions are
// suppressed.  These macros are not intended for warning suppression at
// individual call sites; see permitForbiddenFunctions.hpp for the approach
// taken for that where needed.  Rather, these are used to suppress warnings
// from 3rd-party code included by HotSpot, such as the gtest framework and
// C++ Standard Library headers, which may refer to functions that are
// disallowed in other parts of HotSpot.  They are also used in the
// implementation of the "permit" mechanism.
#define BEGIN_ALLOW_FORBIDDEN_FUNCTIONS         \
  PRAGMA_DIAG_PUSH                              \
  PRAGMA_DEPRECATED_IGNORED

#define END_ALLOW_FORBIDDEN_FUNCTIONS           \
  PRAGMA_DIAG_POP

#endif // SHARE_UTILITIES_COMPILERWARNINGS_HPP
