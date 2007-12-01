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
 * Win32 dependent type definitions
 */

#ifndef _JAVASOFT_WIN32_TYPEDEF_MD_H_
#define _JAVASOFT_WIN32_TYPEDEF_MD_H_

#include <windows.h>

#define VARGS(x) (&x)

typedef char             int8_t;
typedef __int16          int16_t;
typedef __int32          int32_t;
typedef __int64          int64_t;

typedef unsigned char    uint8_t;
typedef unsigned __int16 uint16_t;
typedef unsigned int     uint_t;
typedef unsigned __int32 uint32_t;
typedef unsigned __int64 uint64_t;

/* Make sure that we have the intptr_t and uintptr_t definitions */
#ifndef _INTPTR_T_DEFINED
#ifdef _WIN64
typedef __int64 intptr_t;
#else
typedef int intptr_t;
#endif
#define _INTPTR_T_DEFINED
#endif

#ifndef _UINTPTR_T_DEFINED
#ifdef _WIN64
typedef unsigned __int64 uintptr_t;
#else
typedef unsigned int uintptr_t;
#endif
#define _UINTPTR_T_DEFINED
#endif

typedef intptr_t ssize_t;

/* use these macros when the compiler supports the long long type */

#define ll_high(a)      ((long)((a)>>32))
#define ll_low(a)       ((long)(a))
#define int2ll(a)       ((int64_t)(a))
#define ll2int(a)       ((int)(a))
#define ll_add(a, b)    ((a) + (b))
#define ll_and(a, b)    ((a) & (b))
#define ll_div(a, b)    ((a) / (b))
#define ll_mul(a, b)    ((a) * (b))
#define ll_neg(a)       (-(a))
#define ll_not(a)       (~(a))
#define ll_or(a, b)     ((a) | (b))
/* THE FOLLOWING DEFINITION IS NOW A FUNCTION CALL IN ORDER TO WORKAROUND
   OPTIMIZER BUG IN MSVC++ 2.1 (see system_md.c)
   #define ll_shl(a, n) ((a) << (n)) */
#define ll_shr(a, n)    ((a) >> (n))
#define ll_sub(a, b)    ((a) - (b))
#define ll_ushr(a, n)   ((uint64_t)(a) >> (n))
#define ll_xor(a, b)    ((a) ^ (b))
#define uint2ll(a)      ((uint64_t)(unsigned long)(a))
#define ll_rem(a,b)     ((a) % (b))

int32_t float2l(float f);
int32_t double2l(double f);
int64_t float2ll(float f);
int64_t double2ll(double f);
#define ll2float(a)     ((float) (a))
#define ll2double(a)    ((double) (a))

/* Useful on machines where jlong and jdouble have different endianness. */
#define ll2double_bits(a) ((void) 0)

/* comparison operators */
#define ll_ltz(ll)      ((ll) < 0)
#define ll_gez(ll)      ((ll) >= 0)
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

int64_t ll_shl(int64_t a, int bits);

#define ll2ptr(a) ((void*)(a))
#define ptr2ll(a) ((jlong)(a))

/* printf format modifier for printing pointers */
#define FORMAT64_MODIFIER "I64"

#endif /* !_JAVASOFT_WIN32_TYPEDEF_MD_H_ */
