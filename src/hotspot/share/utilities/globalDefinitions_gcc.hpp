/*
 * Copyright (c) 1998, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_GLOBALDEFINITIONS_GCC_HPP
#define SHARE_UTILITIES_GLOBALDEFINITIONS_GCC_HPP

#include "jni.h"

// This file holds compiler-dependent includes,
// globally used constants & types, class (forward)
// declarations and a few frequently used utility functions.

#include "cppstdlib/cstdlib.hpp"

#include <alloca.h>
#include <ctype.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <inttypes.h>
#include <limits.h>
#include <math.h>
#include <pthread.h>
#include <stdarg.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include <wchar.h>

#if defined(LINUX) || defined(_ALLBSD_SOURCE) || defined(_AIX)
#include <signal.h>
#ifndef __OpenBSD__
#include <ucontext.h>
#endif
#ifdef __APPLE__
  #include <AvailabilityMacros.h>
  #include <mach/mach.h>
#endif
#include <sys/time.h>
#endif // LINUX || _ALLBSD_SOURCE

// checking for nanness
#if defined(__APPLE__)
inline int g_isnan(double f) { return isnan(f); }
#elif defined(LINUX) || defined(_ALLBSD_SOURCE) || defined(_AIX)
inline int g_isnan(float  f) { return isnan(f); }
inline int g_isnan(double f) { return isnan(f); }
#else
#error "missing platform-specific definition here"
#endif

// Checking for finiteness

inline int g_isfinite(jfloat  f)                 { return isfinite(f); }
inline int g_isfinite(jdouble f)                 { return isfinite(f); }

#if defined(_LP64) && defined(__APPLE__)
#define JLONG_FORMAT          "%ld"
#define JLONG_FORMAT_W(width) "%" #width "ld"
#endif // _LP64 && __APPLE__

#define THREAD_LOCAL __thread

// Inlining support
#define NOINLINE     __attribute__ ((noinline))
#define ALWAYSINLINE inline __attribute__ ((always_inline))
#define ATTRIBUTE_FLATTEN __attribute__ ((flatten))

#endif // SHARE_UTILITIES_GLOBALDEFINITIONS_GCC_HPP
