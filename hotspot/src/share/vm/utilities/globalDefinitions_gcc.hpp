/*
 * Copyright (c) 1998, 2009, Oracle and/or its affiliates. All rights reserved.
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

#ifdef SOLARIS
#include <ieeefp.h>
#endif // SOLARIS

#include <math.h>
#ifndef FP_PZERO
// Linux doesn't have positive/negative zero
#define FP_PZERO FP_ZERO
#endif
#if (!defined fpclass) && ((!defined SPARC) || (!defined SOLARIS))
#define fpclass fpclassify
#endif

#include <time.h>
#include <fcntl.h>
#include <dlfcn.h>
#include <pthread.h>

#ifdef SOLARIS
#include <thread.h>
#endif // SOLARIS

#include <limits.h>
#include <errno.h>

#ifdef SOLARIS
#include <sys/trap.h>
#include <sys/regset.h>
#include <sys/procset.h>
#include <ucontext.h>
#include <setjmp.h>
#endif // SOLARIS

# ifdef SOLARIS_MUTATOR_LIBTHREAD
# include <sys/procfs.h>
# endif

#ifdef LINUX
#include <inttypes.h>
#include <signal.h>
#include <ucontext.h>
#include <sys/time.h>
#endif // LINUX

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
// pointer when it extracts the argument, then we have a problem.
//
// Solution: For 64-bit architectures, redefine NULL as 64-bit constant 0.
//
// Note: this fix doesn't work well on Linux because NULL will be overwritten
// whenever a system header file is included. Linux handles NULL correctly
// through a special type '__null'.
#ifdef SOLARIS
  #ifdef _LP64
    #undef NULL
    #define NULL 0L
  #else
    #ifndef NULL
      #define NULL 0
    #endif
  #endif
#endif

// NULL vs NULL_WORD:
// On Linux NULL is defined as a special type '__null'. Assigning __null to
// integer variable will cause gcc warning. Use NULL_WORD in places where a
// pointer is stored as integer value.  On some platforms, sizeof(intptr_t) >
// sizeof(void*), so here we want something which is integer type, but has the
// same size as a pointer.
#ifdef LINUX
  #ifdef _LP64
    #define NULL_WORD  0L
  #else
    // Cast 0 to intptr_t rather than int32_t since they are not the same type
    // on platforms such as Mac OS X.
    #define NULL_WORD  ((intptr_t)0)
  #endif
#else
  #define NULL_WORD  NULL
#endif

#ifndef LINUX
// Compiler-specific primitive types
typedef unsigned short     uint16_t;
#ifndef _UINT32_T
#define _UINT32_T
typedef unsigned int       uint32_t;
#endif // _UINT32_T

#if !defined(_SYS_INT_TYPES_H)
#ifndef _UINT64_T
#define _UINT64_T
typedef unsigned long long uint64_t;
#endif // _UINT64_T
// %%%% how to access definition of intptr_t portably in 5.5 onward?
typedef int                     intptr_t;
typedef unsigned int            uintptr_t;
// If this gets an error, figure out a symbol XXX that implies the
// prior definition of intptr_t, and add "&& !defined(XXX)" above.
#endif // _SYS_INT_TYPES_H

#endif // !LINUX

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


#ifdef SOLARIS
//----------------------------------------------------------------------------------------------------
// ANSI C++ fixes
// NOTE:In the ANSI committee's continuing attempt to make each version
// of C++ incompatible with the previous version, you can no longer cast
// pointers to functions without specifying linkage unless you want to get
// warnings.
//
// This also means that pointers to functions can no longer be "hidden"
// in opaque types like void * because at the invokation point warnings
// will be generated. While this makes perfect sense from a type safety
// point of view it causes a lot of warnings on old code using C header
// files. Here are some typedefs to make the job of silencing warnings
// a bit easier.
//
// The final kick in the teeth is that you can only have extern "C" linkage
// specified at file scope. So these typedefs are here rather than in the
// .hpp for the class (os:Solaris usually) that needs them.

extern "C" {
   typedef int (*int_fnP_thread_t_iP_uP_stack_tP_gregset_t)(thread_t, int*, unsigned *, stack_t*, gregset_t);
   typedef int (*int_fnP_thread_t_i_gregset_t)(thread_t, int, gregset_t);
   typedef int (*int_fnP_thread_t_i)(thread_t, int);
   typedef int (*int_fnP_thread_t)(thread_t);

   typedef int (*int_fnP_cond_tP_mutex_tP_timestruc_tP)(cond_t *cv, mutex_t *mx, timestruc_t *abst);
   typedef int (*int_fnP_cond_tP_mutex_tP)(cond_t *cv, mutex_t *mx);

   // typedef for missing API in libc
   typedef int (*int_fnP_mutex_tP_i_vP)(mutex_t *, int, void *);
   typedef int (*int_fnP_mutex_tP)(mutex_t *);
   typedef int (*int_fnP_cond_tP_i_vP)(cond_t *cv, int scope, void *arg);
   typedef int (*int_fnP_cond_tP)(cond_t *cv);
};
#endif // SOLARIS

//----------------------------------------------------------------------------------------------------
// Debugging

#define DEBUG_EXCEPTION ::abort();

#ifdef ARM
#ifdef SOLARIS
#define BREAKPOINT __asm__ volatile (".long 0xe1200070")
#else
#define BREAKPOINT __asm__ volatile (".long 0xe7f001f0")
#endif
#else
extern "C" void breakpoint();
#define BREAKPOINT ::breakpoint()
#endif

// checking for nanness
#ifdef SOLARIS
#ifdef SPARC
inline int g_isnan(float  f) { return isnanf(f); }
#else
// isnanf() broken on Intel Solaris use isnand()
inline int g_isnan(float  f) { return isnand(f); }
#endif
inline int g_isnan(double f) { return isnand(f); }
#elif LINUX
inline int g_isnan(float  f) { return isnanf(f); }
inline int g_isnan(double f) { return isnan(f); }
#else
#error "missing platform-specific definition here"
#endif

// GCC 4.3 does not allow 0.0/0.0 to produce a NAN value
#if (__GNUC__ == 4) && (__GNUC_MINOR__ > 2)
#define CAN_USE_NAN_DEFINE 1
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

#if (__GNUC__ == 2) && (__GNUC_MINOR__ < 95)
#define TEMPLATE_TABLE_BUG
#endif
#if (__GNUC__ == 2) && (__GNUC_MINOR__ >= 96)
#define CONST_SDM_BUG
#endif

// Formatting.
#ifdef _LP64
#define FORMAT64_MODIFIER "l"
#else // !_LP64
#define FORMAT64_MODIFIER "ll"
#endif // _LP64

// HACK: gcc warns about applying offsetof() to non-POD object or calculating
//       offset directly when base address is NULL. Use 16 to get around the
//       warning. gcc-3.4 has an option -Wno-invalid-offsetof to suppress
//       this warning.
#define offset_of(klass,field) (size_t)((intx)&(((klass*)16)->field) - 16)

#ifdef offsetof
# undef offsetof
#endif
#define offsetof(klass,field) offset_of(klass,field)
