/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2012, 2023 SAP SE. All rights reserved.
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

#ifndef SHARE_UTILITIES_GLOBALDEFINITIONS_XLC_HPP
#define SHARE_UTILITIES_GLOBALDEFINITIONS_XLC_HPP

#include "jni.h"

// This file holds compiler-dependent includes,
// globally used constants & types, class (forward)
// declarations and a few frequently used utility functions.

#include <alloca.h>
#include <ctype.h>
#include <string.h>
#include <stdarg.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
// In stdlib.h on AIX malloc is defined as a macro causing
// compiler errors when resolving them in different depths as it
// happens in the log tags. This avoids the macro.
#if (defined(__VEC__) || defined(__AIXVEC)) && defined(AIX) \
    && defined(__open_xl_version__) && __open_xl_version__ >= 17
  #undef malloc
  extern void *malloc(size_t) asm("vec_malloc");
#endif

#include <wchar.h>

#include <math.h>
#include <time.h>
#include <fcntl.h>
#include <dlfcn.h>
#include <pthread.h>

#include <limits.h>
#include <errno.h>

#include <stdint.h>

#if defined(__open_xl_version__)
  #if __open_xl_version__ < 17
  #error "open xlc < 17 not supported"
  #endif
#else
  #error "xlc version not supported, macro __open_xl_version__ not found"
#endif

#ifndef _AIX
#error "missing AIX-specific definition _AIX"
#endif

// Use XLC compiler builtins instead of inline assembler
#define USE_XLC_BUILTINS

#ifdef USE_XLC_BUILTINS
#include <builtins.h>
// XLC V10 and higher provide the prototype for __dcbtst (void *);
#endif // USE_XLC_BUILTINS

// NULL vs NULL_WORD:
// Some platform/tool-chain combinations can't assign NULL to an integer
// type so we define NULL_WORD to use in those contexts.
#define NULL_WORD  0L

// checking for nanness
inline int g_isnan(float  f) { return isnan(f); }
inline int g_isnan(double f) { return isnan(f); }

// Checking for finiteness
inline int g_isfinite(jfloat  f)                 { return finite(f); }
inline int g_isfinite(jdouble f)                 { return finite(f); }

// Formatting.
#ifdef _LP64
#define FORMAT64_MODIFIER "l"
#else // !_LP64
#define FORMAT64_MODIFIER "ll"
#endif // _LP64

// Cannot use xlc's offsetof as implementation of hotspot's
// offset_of(), because xlc warns about applying offsetof() to non-POD
// object and xlc cannot compile the expression offsetof(DataLayout,
// _cells[index]) in DataLayout::cell_offset() .  Therefore we define
// offset_of as it is defined for gcc.
#define offset_of(klass,field) (size_t)((intx)&(((klass*)16)->field) - 16)

#define THREAD_LOCAL __thread

// Inlining support
//
// Be aware that for function/method declarations, xlC only supports the following
// syntax (i.e. the attribute must be placed AFTER the function/method declarator):
//
//   void* operator new(size_t size) throw() NOINLINE;
//
// For function/method definitions, the more common placement BEFORE the
// function/method declarator seems to be supported as well:
//
//   NOINLINE void* CHeapObj<F>::operator new(size_t size) throw() {...}

#define NOINLINE     __attribute__((__noinline__))
#define ALWAYSINLINE inline __attribute__((__always_inline__))

#endif // SHARE_UTILITIES_GLOBALDEFINITIONS_XLC_HPP
