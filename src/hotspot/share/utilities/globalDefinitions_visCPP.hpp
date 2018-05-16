/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_UTILITIES_GLOBALDEFINITIONS_VISCPP_HPP
#define SHARE_VM_UTILITIES_GLOBALDEFINITIONS_VISCPP_HPP

#include "jni.h"

// This file holds compiler-dependent includes,
// globally used constants & types, class (forward)
// declarations and a few frequently used utility functions.

# include <ctype.h>
# include <string.h>
# include <stdarg.h>
# include <stdlib.h>
# include <stdint.h>
# include <stddef.h>// for offsetof
# include <io.h>    // for stream.cpp
# include <float.h> // for _isnan
# include <stdio.h> // for va_list
# include <time.h>
# include <fcntl.h>
# include <limits.h>
# include <inttypes.h>
// Need this on windows to get the math constants (e.g., M_PI).
#define _USE_MATH_DEFINES
# include <math.h>

// 4810578: varargs unsafe on 32-bit integer/64-bit pointer architectures
// When __cplusplus is defined, NULL is defined as 0 (32-bit constant) in
// system header files.  On 32-bit architectures, there is no problem.
// On 64-bit architectures, defining NULL as a 32-bit constant can cause
// problems with varargs functions: C++ integral promotion rules say for
// varargs, we pass the argument 0 as an int.  So, if NULL was passed to a
// varargs function it will remain 32-bits.  Depending on the calling
// convention of the machine, if the argument is passed on the stack then
// only 32-bits of the "NULL" pointer may be initialized to zero.  The
// other 32-bits will be garbage.  If the varargs function is expecting a
// pointer when it extracts the argument, then we may have a problem.
//
// Solution: For 64-bit architectures, redefine NULL as 64-bit constant 0.
#ifdef _LP64
#undef NULL
// 64-bit Windows uses a P64 data model (not LP64, although we define _LP64)
// Since longs are 32-bit we cannot use 0L here.  Use the Visual C++ specific
// 64-bit integer-suffix (LL) instead.
#define NULL 0LL
#else
#ifndef NULL
#define NULL 0
#endif
#endif

// NULL vs NULL_WORD:
// On Linux NULL is defined as a special type '__null'. Assigning __null to
// integer variable will cause gcc warning. Use NULL_WORD in places where a
// pointer is stored as integer value.
#define NULL_WORD NULL

#ifdef _WIN64
typedef int64_t ssize_t;
#else
typedef int32_t ssize_t;
#endif

// Additional Java basic types

typedef uint8_t  jubyte;
typedef uint16_t jushort;
typedef uint32_t juint;
typedef uint64_t julong;

// Non-standard stdlib-like stuff:
inline int strcasecmp(const char *s1, const char *s2) { return _stricmp(s1,s2); }
inline int strncasecmp(const char *s1, const char *s2, size_t n) {
  return _strnicmp(s1,s2,n);
}

// Checking for nanness

inline int g_isnan(jfloat  f)                    { return _isnan(f); }
inline int g_isnan(jdouble f)                    { return _isnan(f); }

// Checking for finiteness

inline int g_isfinite(jfloat  f)                 { return _finite(f); }
inline int g_isfinite(jdouble f)                 { return _finite(f); }

// Miscellaneous

// Visual Studio 2005 deprecates POSIX names - use ISO C++ names instead
#if _MSC_VER >= 1400
#define open _open
#define close _close
#define read  _read
#define write _write
#define lseek _lseek
#define unlink _unlink
#define strdup _strdup
#endif

#if _MSC_VER < 1800
// Visual Studio 2013 introduced strtoull(); before, one has to use _strtoui64() instead.
#define strtoull _strtoui64
// Visual Studio prior to 2013 had no va_copy, but could safely copy va_list by assignement
#define va_copy(dest, src) dest = src
// Fixes some wrong warnings about 'this' : used in base member initializer list
#pragma warning( disable : 4355 )
#endif


#pragma warning( disable : 4100 ) // unreferenced formal parameter
#pragma warning( disable : 4127 ) // conditional expression is constant
#pragma warning( disable : 4514 ) // unreferenced inline function has been removed
#pragma warning( disable : 4244 ) // possible loss of data
#pragma warning( disable : 4512 ) // assignment operator could not be generated
#pragma warning( disable : 4201 ) // nonstandard extension used : nameless struct/union (needed in windows.h)
#pragma warning( disable : 4511 ) // copy constructor could not be generated
#pragma warning( disable : 4291 ) // no matching operator delete found; memory will not be freed if initialization thows an exception
#pragma warning( disable : 4351 ) // new behavior: elements of array ... will be default initialized
#ifdef CHECK_UNHANDLED_OOPS
#pragma warning( disable : 4521 ) // class has multiple copy ctors of a single type
#pragma warning( disable : 4522 ) // class has multiple assignment operators of a single type
#endif // CHECK_UNHANDLED_OOPS
#if _MSC_VER >= 1400
#pragma warning( disable : 4996 ) // unsafe string functions. Same as define _CRT_SECURE_NO_WARNINGS/_CRT_SECURE_NO_DEPRICATE
#endif

// Portability macros
#define PRAGMA_INTERFACE
#define PRAGMA_IMPLEMENTATION
#define PRAGMA_IMPLEMENTATION_(arg)

// Formatting.
#define FORMAT64_MODIFIER "I64"

#define offset_of(klass,field) offsetof(klass,field)

#ifndef USE_LIBRARY_BASED_TLS_ONLY
#define THREAD_LOCAL_DECL __declspec( thread )
#endif

// Inlining support
// MSVC has '__declspec(noinline)' but according to the official documentation
// it only applies to member functions. There are reports though which pretend
// that it also works for freestanding functions.
#define NOINLINE     __declspec(noinline)
#define ALWAYSINLINE __forceinline

// Alignment
#define ATTRIBUTE_ALIGNED(x) __declspec(align(x))

#endif // SHARE_VM_UTILITIES_GLOBALDEFINITIONS_VISCPP_HPP
