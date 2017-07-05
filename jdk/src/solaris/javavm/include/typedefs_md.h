/*
 * Copyright 1994-2002 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * Solaris-dependent types for Green threads
 */

#ifndef _JAVASOFT_SOLARIS_TYPES_MD_H_
#define _JAVASOFT_SOLARIS_TYPES_MD_H_

#include <sys/types.h>
#include <sys/stat.h>

#ifdef __linux__
#include <stdint.h>
#define HAVE_INTPTR_T
#define _UINT64_T
#endif

#define int8_t char

/* Fix for varargs differences on PowerPC */
#if defined(__powerpc__)
#define VARGS(x) (x)
#else
#define VARGS(x) (&x)
#endif /* __powerpc__ */


#if defined(__alpha__)
#define PTR_IS_64 1
#define LONG_IS_64 1
#else
#define PTR_IS_32 1
#endif

/* don't redefine typedef's on Solaris 2.6 or Later */

#if !defined(_ILP32) && !defined(_LP64)

#ifndef HAVE_INTPTR_T
#ifdef LONG_IS_64
typedef long intptr_t;
typedef unsigned long uintptr_t;
#else
typedef int intptr_t;
typedef unsigned int uintptr_t;
#endif  /* LONG_IS_64 */
#endif /* don't HAVE_INTPTR_T */

#ifndef _UINT64_T
#define _UINT64_T
#ifdef LONG_IS_64
typedef unsigned long uint64_t;
#else
typedef unsigned long long uint64_t;
#endif
#define _UINT32_T
#ifndef uint32_t /* [sbb] scaffolding */
typedef unsigned int uint32_t;
#endif /* [sbb] scaffolding */
#if defined(__linux__)
typedef unsigned int uint_t;
#endif
#endif

#ifndef __BIT_TYPES_DEFINED__
/* that should get Linux, at least */
#ifndef _INT64_T
#define _INT64_T
#ifdef LONG_IS_64
typedef long int64_t;
#else
typedef long long int64_t;
#endif
#define _INT32_T
#ifndef int32_t /* [sbb] scaffolding */
typedef int int32_t;
#endif /* [sbb] scaffolding */
#if defined(__linux__)
typedef int int_t;
#endif
#endif
#endif /* __BIT_TYPES_DEFINED__ */

#endif   /* !defined(_ILP32) && !defined(_LP64) */

/* use these macros when the compiler supports the long long type */

#define ll_high(a)    ((uint32_t)(((uint64_t)(a))>>32))
#define ll_low(a)     ((uint32_t)(a))
#define int2ll(a)       ((int64_t)(a))
#define ll2int(a)       ((int)(a))
#define ll_add(a, b)    ((int64_t)(a) + (int64_t)(b))
#define ll_and(a, b)    ((int64_t)(a) & (int64_t)(b))
#define ll_div(a, b)    ((int64_t)(a) / (int64_t)(b))
#define ll_mul(a, b)    ((int64_t)(a) * (int64_t)(b))
#define ll_neg(a)       (-(a))
#define ll_not(a)     (~(uint64_t)(a))
#define ll_or(a, b)   ((uint64_t)(a) | (b))
#define ll_shl(a, n)  ((uint64_t)(a) << (n))
#define ll_shr(a, n)  ((int64_t)(a) >> (n))
#define ll_sub(a, b)  ((uint64_t)(a) - (b))
#define ll_ushr(a, n) ((uint64_t)(a) >>(n))
#define ll_xor(a, b)  ((int64_t)(a) ^ (int64_t)(b))
#define uint2ll(a)    ((uint64_t)(a))
#define ll_rem(a,b)     ((int64_t)(a) % (int64_t)(b))

extern int32_t float2l(float f);
extern int32_t double2l(double d);
extern int64_t float2ll(float f);
extern int64_t double2ll(double d);

#define ll2float(a)     ((float) (a))
#define ll2double(a)    ((double) (a))

/* Useful on machines where jlong and jdouble have different endianness. */
#define ll2double_bits(a)  ((void) 0)

/* comparison operators */
#define ll_ltz(ll)      ((ll)<0)
#define ll_gez(ll)      ((ll)>=0)
#define ll_eqz(a)       ((a) == 0)
#define ll_nez(a)       ((a) != 0)
#define ll_eq(a, b)     ((a) == (b))
#define ll_ne(a,b)      ((a) != (b))
#define ll_ge(a,b)      ((a) >= (b))
#define ll_le(a,b)      ((a) <= (b))
#define ll_lt(a,b)      ((a) < (b))
#define ll_gt(a,b)      ((a) > (b))

#define ll_zero_const   ((int64_t) 0)
#define ll_one_const    ((int64_t) 1)

extern void ll2str(int64_t a, char *s, char *limit);

#define ll2ptr(a) ((void*)(uintptr_t)(a))
#define ptr2ll(a) ((int64_t)(uintptr_t)(a))

#ifdef ppc
#define HAVE_ALIGNED_DOUBLES
#define HAVE_ALIGNED_LONGLONGS
#endif

/* printf format modifier for printing pointers */
#ifdef _LP64
#define FORMAT64_MODIFIER "l"
#else
#define FORMAT64_MODIFIER "ll"
#endif

#endif /* !_JAVASOFT_SOLARIS_TYPES_MD_H_ */
