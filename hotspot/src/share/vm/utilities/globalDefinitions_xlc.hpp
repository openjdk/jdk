/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2012, 2013 SAP AG. All rights reserved.
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

#ifndef SHARE_VM_UTILITIES_GLOBALDEFINITIONS_XLC_HPP
#define SHARE_VM_UTILITIES_GLOBALDEFINITIONS_XLC_HPP

#include "prims/jni.h"

// This file holds compiler-dependent includes,
// globally used constants & types, class (forward)
// declarations and a few frequently used utility functions.

#include <ctype.h>
#include <string.h>
#include <stdarg.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <wchar.h>

#include <math.h>
#ifndef FP_PZERO
// Linux doesn't have positive/negative zero
#define FP_PZERO FP_ZERO
#endif
#if (!defined fpclass)
#define fpclass fpclassify
#endif

#include <time.h>
#include <fcntl.h>
#include <dlfcn.h>
#include <pthread.h>

#include <limits.h>
#include <errno.h>

#include <stdint.h>

// Use XLC compiler builtins instead of inline assembler
#define USE_XLC_BUILTINS
#ifdef USE_XLC_BUILTINS
#include <builtins.h>
  #if __IBMCPP__ < 1000
  // the funtion prototype for __dcbtst(void *) is missing in XLC V8.0
  // I could compile a little test, where I provided the prototype.
  // The generated code was correct there. This is the prototype:
  // extern "builtin" void __dcbtst (void *);
  // For now we don't make use of it when compiling with XLC V8.0
  #else
  // __IBMCPP__ >= 1000
  // XLC V10 provides the prototype for __dcbtst (void *);
  #define USE_XLC_PREFETCH_WRITE_BUILTIN
  #endif
#endif // USE_XLC_BUILTINS

// NULL vs NULL_WORD:
// On Linux NULL is defined as a special type '__null'. Assigning __null to
// integer variable will cause gcc warning. Use NULL_WORD in places where a
// pointer is stored as integer value.  On some platforms, sizeof(intptr_t) >
// sizeof(void*), so here we want something which is integer type, but has the
// same size as a pointer.
#ifdef __GNUC__
  #error XLC and __GNUC__?
#else
  #define NULL_WORD  NULL
#endif

// AIX also needs a 64 bit NULL to work as a null address pointer.
// Most system includes on AIX would define it as an int 0 if not already defined with one
// exception: /usr/include/dirent.h will unconditionally redefine NULL to int 0 again.
// In this case you need to copy the following defines to a position after #include <dirent.h>
// (see jmv_aix.h).
#ifdef AIX
  #ifdef _LP64
    #undef NULL
    #define NULL 0L
  #else
    #ifndef NULL
      #define NULL 0
    #endif
  #endif
#endif // AIX

// Compiler-specific primitive types
// All defs of int (uint16_6 etc) are defined in AIX' /usr/include/stdint.h

// Additional Java basic types

typedef uint8_t  jubyte;
typedef uint16_t jushort;
typedef uint32_t juint;
typedef uint64_t julong;

//----------------------------------------------------------------------------------------------------
// Special (possibly not-portable) casts
// Cast floats into same-size integers and vice-versa w/o changing bit-pattern
// %%%%%% These seem like standard C++ to me--how about factoring them out? - Ungar

inline jint    jint_cast   (jfloat  x)           { return *(jint*   )&x; }
inline jlong   jlong_cast  (jdouble x)           { return *(jlong*  )&x; }

inline jfloat  jfloat_cast (jint    x)           { return *(jfloat* )&x; }
inline jdouble jdouble_cast(jlong   x)           { return *(jdouble*)&x; }

//----------------------------------------------------------------------------------------------------
// Constant for jlong (specifying an long long canstant is C++ compiler specific)

// Build a 64bit integer constant
#define CONST64(x)  (x ## LL)
#define UCONST64(x) (x ## ULL)

const jlong min_jlong = CONST64(0x8000000000000000);
const jlong max_jlong = CONST64(0x7fffffffffffffff);

//----------------------------------------------------------------------------------------------------
// Debugging

#define DEBUG_EXCEPTION ::abort();

extern "C" void breakpoint();
#define BREAKPOINT ::breakpoint()

// checking for nanness
#ifdef AIX
inline int g_isnan(float  f) { return isnan(f); }
inline int g_isnan(double f) { return isnan(f); }
#else
#error "missing platform-specific definition here"
#endif

// Checking for finiteness

inline int g_isfinite(jfloat  f)                 { return finite(f); }
inline int g_isfinite(jdouble f)                 { return finite(f); }


// Wide characters

inline int wcslen(const jchar* x) { return wcslen((const wchar_t*)x); }


// Portability macros
#define PRAGMA_INTERFACE             #pragma interface
#define PRAGMA_IMPLEMENTATION        #pragma implementation
#define VALUE_OBJ_CLASS_SPEC

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

// Some constant sizes used throughout the AIX port
#define SIZE_1K   ((uint64_t)         0x400ULL)
#define SIZE_4K   ((uint64_t)        0x1000ULL)
#define SIZE_64K  ((uint64_t)       0x10000ULL)
#define SIZE_1M   ((uint64_t)      0x100000ULL)
#define SIZE_4M   ((uint64_t)      0x400000ULL)
#define SIZE_8M   ((uint64_t)      0x800000ULL)
#define SIZE_16M  ((uint64_t)     0x1000000ULL)
#define SIZE_256M ((uint64_t)    0x10000000ULL)
#define SIZE_1G   ((uint64_t)    0x40000000ULL)
#define SIZE_2G   ((uint64_t)    0x80000000ULL)
#define SIZE_4G   ((uint64_t)   0x100000000ULL)
#define SIZE_16G  ((uint64_t)   0x400000000ULL)
#define SIZE_32G  ((uint64_t)   0x800000000ULL)
#define SIZE_64G  ((uint64_t)  0x1000000000ULL)
#define SIZE_1T   ((uint64_t) 0x10000000000ULL)


#endif // SHARE_VM_UTILITIES_GLOBALDEFINITIONS_XLC_HPP
