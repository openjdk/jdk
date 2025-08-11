/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_GLOBALDEFINITIONS_VISCPP_HPP
#define SHARE_UTILITIES_GLOBALDEFINITIONS_VISCPP_HPP

#include "jni.h"

// This file holds compiler-dependent includes,
// globally used constants & types, class (forward)
// declarations and a few frequently used utility functions.

// Need this on windows to get the math constants (e.g., M_PI).
#define _USE_MATH_DEFINES

# include <ctype.h>
# include <fcntl.h>
# include <float.h> // for _isnan
# include <inttypes.h>
# include <io.h>    // for stream.cpp
# include <limits.h>
# include <math.h>
# include <stdarg.h>
# include <stddef.h>// for offsetof
# include <stdint.h>
# include <stdio.h>
# include <stdlib.h>
# include <string.h>
# include <sys/stat.h>
# include <time.h>

// Only 64-bit Windows is supported
#ifndef _LP64
#error unsupported platform
#endif

typedef int64_t ssize_t;

// Non-standard stdlib-like stuff:
inline int strcasecmp(const char *s1, const char *s2) { return _stricmp(s1,s2); }
inline int strncasecmp(const char *s1, const char *s2, size_t n) {
  return _strnicmp(s1,s2,n);
}

// VS doesn't provide strtok_r, which is a POSIX function.  Instead, it
// provides the same function under the name strtok_s.  Note that this is
// *not* the same as the C99 Annex K strtok_s.  VS provides that function
// under the name strtok_s_l.  Make strtok_r a synonym so we can use that name
// in shared code.
const auto strtok_r = strtok_s;

// VS doesn't provide POSIX macros S_ISFIFO or S_IFIFO.  It doesn't even
// provide _S_ISFIFO, per its usual naming convention for POSIX stuff.  But it
// does provide _S_IFIFO, so we can roll our own S_ISFIFO.
#define S_ISFIFO(mode) (((mode) & _S_IFIFO) == _S_IFIFO)

// Checking for nanness

inline int g_isnan(jfloat  f)                    { return _isnan(f); }
inline int g_isnan(jdouble f)                    { return _isnan(f); }

// Checking for finiteness

inline int g_isfinite(jfloat  f)                 { return _finite(f); }
inline int g_isfinite(jdouble f)                 { return _finite(f); }

#define offset_of(klass,field) offsetof(klass,field)

#define THREAD_LOCAL __declspec(thread)

// Inlining support
// MSVC has '__declspec(noinline)' but according to the official documentation
// it only applies to member functions. There are reports though which pretend
// that it also works for freestanding functions.
#define NOINLINE     __declspec(noinline)
#define ALWAYSINLINE __forceinline

#ifdef _M_ARM64
#define USE_VECTORED_EXCEPTION_HANDLING
#endif

#ifndef SSIZE_MAX
#define SSIZE_MIN LLONG_MIN
#define SSIZE_MAX LLONG_MAX
#endif // SSIZE_MAX missing

#endif // SHARE_UTILITIES_GLOBALDEFINITIONS_VISCPP_HPP
