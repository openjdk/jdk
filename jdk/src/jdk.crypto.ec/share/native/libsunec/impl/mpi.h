/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
 * Use is subject to license terms.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/* *********************************************************************
 *
 * The Original Code is the MPI Arbitrary Precision Integer Arithmetic library.
 *
 * The Initial Developer of the Original Code is
 * Michael J. Fromberger.
 * Portions created by the Initial Developer are Copyright (C) 1998
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Netscape Communications Corporation
 *
 *********************************************************************** */

/*  Arbitrary precision integer arithmetic library */

#ifndef _MPI_H
#define _MPI_H

/* $Id: mpi.h,v 1.22 2004/04/27 23:04:36 gerv%gerv.net Exp $ */

#include "mpi-config.h"

#ifndef _WIN32
#include <sys/param.h>
#endif /* _WIN32 */

#ifdef _KERNEL
#include <sys/debug.h>
#include <sys/systm.h>
#define assert ASSERT
#define labs(a) (a >= 0 ? a : -a)
#define UCHAR_MAX 255
#define memset(s, c, n) bzero(s, n)
#define memcpy(a,b,c) bcopy((caddr_t)b, (caddr_t)a, c)
/*
 * Generic #define's to cover missing things in the kernel
 */
#ifndef isdigit
#define isdigit(x)      ((x) >= '0' && (x) <= '9')
#endif
#ifndef isupper
#define isupper(x)      (((unsigned)(x) >= 'A') && ((unsigned)(x) <= 'Z'))
#endif
#ifndef islower
#define islower(x)      (((unsigned)(x) >= 'a') && ((unsigned)(x) <= 'z'))
#endif
#ifndef isalpha
#define isalpha(x)      (isupper(x) || islower(x))
#endif
#ifndef toupper
#define toupper(x)      (islower(x) ? (x) - 'a' + 'A' : (x))
#endif
#ifndef tolower
#define tolower(x)      (isupper(x) ? (x) + 'a' - 'A' : (x))
#endif
#ifndef isspace
#define isspace(x)      (((x) == ' ') || ((x) == '\r') || ((x) == '\n') || \
                         ((x) == '\t') || ((x) == '\b'))
#endif
#endif /* _KERNEL */

#if MP_DEBUG
#undef MP_IOFUNC
#define MP_IOFUNC 1
#endif

#if MP_IOFUNC
#include <stdio.h>
#include <ctype.h>
#endif

#ifndef _KERNEL
#include <limits.h>
#endif

#if defined(BSDI)
#undef ULLONG_MAX
#endif

#if defined( macintosh )
#include <Types.h>
#elif defined( _WIN32_WCE)
/* #include <sys/types.h> What do we need here ?? */
#else
#include <sys/types.h>
#endif

#define  MP_NEG    1
#define  MP_ZPOS   0

#define  MP_OKAY          0 /* no error, all is well */
#define  MP_YES           0 /* yes (boolean result)  */
#define  MP_NO           -1 /* no (boolean result)   */
#define  MP_MEM          -2 /* out of memory         */
#define  MP_RANGE        -3 /* argument out of range */
#define  MP_BADARG       -4 /* invalid parameter     */
#define  MP_UNDEF        -5 /* answer is undefined   */
#define  MP_LAST_CODE    MP_UNDEF

typedef unsigned int      mp_sign;
typedef unsigned int      mp_size;
typedef int               mp_err;
typedef int               mp_flag;

#define MP_32BIT_MAX 4294967295U

#if !defined(ULONG_MAX)
#error "ULONG_MAX not defined"
#elif !defined(UINT_MAX)
#error "UINT_MAX not defined"
#elif !defined(USHRT_MAX)
#error "USHRT_MAX not defined"
#endif

#if defined(ULONG_LONG_MAX)                     /* GCC, HPUX */
#define MP_ULONG_LONG_MAX ULONG_LONG_MAX
#elif defined(ULLONG_MAX)                       /* Solaris */
#define MP_ULONG_LONG_MAX ULLONG_MAX
/* MP_ULONG_LONG_MAX was defined to be ULLONG_MAX */
#elif defined(ULONGLONG_MAX)                    /* IRIX, AIX */
#define MP_ULONG_LONG_MAX ULONGLONG_MAX
#endif

/* We only use unsigned long for mp_digit iff long is more than 32 bits. */
#if !defined(MP_USE_UINT_DIGIT) && ULONG_MAX > MP_32BIT_MAX
typedef unsigned long     mp_digit;
#define MP_DIGIT_MAX      ULONG_MAX
#define MP_DIGIT_FMT      "%016lX"   /* printf() format for 1 digit */
#define MP_HALF_DIGIT_MAX UINT_MAX
#undef  MP_NO_MP_WORD
#define MP_NO_MP_WORD 1
#undef  MP_USE_LONG_DIGIT
#define MP_USE_LONG_DIGIT 1
#undef  MP_USE_LONG_LONG_DIGIT

#elif !defined(MP_USE_UINT_DIGIT) && defined(MP_ULONG_LONG_MAX)
typedef unsigned long long mp_digit;
#define MP_DIGIT_MAX       MP_ULONG_LONG_MAX
#define MP_DIGIT_FMT      "%016llX"  /* printf() format for 1 digit */
#define MP_HALF_DIGIT_MAX  UINT_MAX
#undef  MP_NO_MP_WORD
#define MP_NO_MP_WORD 1
#undef  MP_USE_LONG_LONG_DIGIT
#define MP_USE_LONG_LONG_DIGIT 1
#undef  MP_USE_LONG_DIGIT

#else
typedef unsigned int      mp_digit;
#define MP_DIGIT_MAX      UINT_MAX
#define MP_DIGIT_FMT      "%08X"     /* printf() format for 1 digit */
#define MP_HALF_DIGIT_MAX USHRT_MAX
#undef  MP_USE_UINT_DIGIT
#define MP_USE_UINT_DIGIT 1
#undef  MP_USE_LONG_LONG_DIGIT
#undef  MP_USE_LONG_DIGIT
#endif

#if !defined(MP_NO_MP_WORD)
#if  defined(MP_USE_UINT_DIGIT) && \
    (defined(MP_ULONG_LONG_MAX) || (ULONG_MAX > UINT_MAX))

#if (ULONG_MAX > UINT_MAX)
typedef unsigned long     mp_word;
typedef          long     mp_sword;
#define MP_WORD_MAX       ULONG_MAX

#else
typedef unsigned long long mp_word;
typedef          long long mp_sword;
#define MP_WORD_MAX       MP_ULONG_LONG_MAX
#endif

#else
#define MP_NO_MP_WORD 1
#endif
#endif /* !defined(MP_NO_MP_WORD) */

#if !defined(MP_WORD_MAX) && defined(MP_DEFINE_SMALL_WORD)
typedef unsigned int      mp_word;
typedef          int      mp_sword;
#define MP_WORD_MAX       UINT_MAX
#endif

#ifndef CHAR_BIT
#define CHAR_BIT 8
#endif

#define MP_DIGIT_BIT      (CHAR_BIT*sizeof(mp_digit))
#define MP_WORD_BIT       (CHAR_BIT*sizeof(mp_word))
#define MP_RADIX          (1+(mp_word)MP_DIGIT_MAX)

#define MP_HALF_DIGIT_BIT (MP_DIGIT_BIT/2)
#define MP_HALF_RADIX     (1+(mp_digit)MP_HALF_DIGIT_MAX)
/* MP_HALF_RADIX really ought to be called MP_SQRT_RADIX, but it's named
** MP_HALF_RADIX because it's the radix for MP_HALF_DIGITs, and it's
** consistent with the other _HALF_ names.
*/


/* Macros for accessing the mp_int internals           */
#define  MP_FLAG(MP)     ((MP)->flag)
#define  MP_SIGN(MP)     ((MP)->sign)
#define  MP_USED(MP)     ((MP)->used)
#define  MP_ALLOC(MP)    ((MP)->alloc)
#define  MP_DIGITS(MP)   ((MP)->dp)
#define  MP_DIGIT(MP,N)  (MP)->dp[(N)]

/* This defines the maximum I/O base (minimum is 2)   */
#define MP_MAX_RADIX         64

typedef struct {
  mp_sign       flag;    /* KM_SLEEP/KM_NOSLEEP        */
  mp_sign       sign;    /* sign of this quantity      */
  mp_size       alloc;   /* how many digits allocated  */
  mp_size       used;    /* how many digits used       */
  mp_digit     *dp;      /* the digits themselves      */
} mp_int;

/* Default precision       */
mp_size mp_get_prec(void);
void    mp_set_prec(mp_size prec);

/* Memory management       */
mp_err mp_init(mp_int *mp, int kmflag);
mp_err mp_init_size(mp_int *mp, mp_size prec, int kmflag);
mp_err mp_init_copy(mp_int *mp, const mp_int *from);
mp_err mp_copy(const mp_int *from, mp_int *to);
void   mp_exch(mp_int *mp1, mp_int *mp2);
void   mp_clear(mp_int *mp);
void   mp_zero(mp_int *mp);
void   mp_set(mp_int *mp, mp_digit d);
mp_err mp_set_int(mp_int *mp, long z);
#define mp_set_long(mp,z) mp_set_int(mp,z)
mp_err mp_set_ulong(mp_int *mp, unsigned long z);

/* Single digit arithmetic */
mp_err mp_add_d(const mp_int *a, mp_digit d, mp_int *b);
mp_err mp_sub_d(const mp_int *a, mp_digit d, mp_int *b);
mp_err mp_mul_d(const mp_int *a, mp_digit d, mp_int *b);
mp_err mp_mul_2(const mp_int *a, mp_int *c);
mp_err mp_div_d(const mp_int *a, mp_digit d, mp_int *q, mp_digit *r);
mp_err mp_div_2(const mp_int *a, mp_int *c);
mp_err mp_expt_d(const mp_int *a, mp_digit d, mp_int *c);

/* Sign manipulations      */
mp_err mp_abs(const mp_int *a, mp_int *b);
mp_err mp_neg(const mp_int *a, mp_int *b);

/* Full arithmetic         */
mp_err mp_add(const mp_int *a, const mp_int *b, mp_int *c);
mp_err mp_sub(const mp_int *a, const mp_int *b, mp_int *c);
mp_err mp_mul(const mp_int *a, const mp_int *b, mp_int *c);
#if MP_SQUARE
mp_err mp_sqr(const mp_int *a, mp_int *b);
#else
#define mp_sqr(a, b) mp_mul(a, a, b)
#endif
mp_err mp_div(const mp_int *a, const mp_int *b, mp_int *q, mp_int *r);
mp_err mp_div_2d(const mp_int *a, mp_digit d, mp_int *q, mp_int *r);
mp_err mp_expt(mp_int *a, mp_int *b, mp_int *c);
mp_err mp_2expt(mp_int *a, mp_digit k);
mp_err mp_sqrt(const mp_int *a, mp_int *b);

/* Modular arithmetic      */
#if MP_MODARITH
mp_err mp_mod(const mp_int *a, const mp_int *m, mp_int *c);
mp_err mp_mod_d(const mp_int *a, mp_digit d, mp_digit *c);
mp_err mp_addmod(const mp_int *a, const mp_int *b, const mp_int *m, mp_int *c);
mp_err mp_submod(const mp_int *a, const mp_int *b, const mp_int *m, mp_int *c);
mp_err mp_mulmod(const mp_int *a, const mp_int *b, const mp_int *m, mp_int *c);
#if MP_SQUARE
mp_err mp_sqrmod(const mp_int *a, const mp_int *m, mp_int *c);
#else
#define mp_sqrmod(a, m, c) mp_mulmod(a, a, m, c)
#endif
mp_err mp_exptmod(const mp_int *a, const mp_int *b, const mp_int *m, mp_int *c);
mp_err mp_exptmod_d(const mp_int *a, mp_digit d, const mp_int *m, mp_int *c);
#endif /* MP_MODARITH */

/* Comparisons             */
int    mp_cmp_z(const mp_int *a);
int    mp_cmp_d(const mp_int *a, mp_digit d);
int    mp_cmp(const mp_int *a, const mp_int *b);
int    mp_cmp_mag(mp_int *a, mp_int *b);
int    mp_cmp_int(const mp_int *a, long z, int kmflag);
int    mp_isodd(const mp_int *a);
int    mp_iseven(const mp_int *a);

/* Number theoretic        */
#if MP_NUMTH
mp_err mp_gcd(mp_int *a, mp_int *b, mp_int *c);
mp_err mp_lcm(mp_int *a, mp_int *b, mp_int *c);
mp_err mp_xgcd(const mp_int *a, const mp_int *b, mp_int *g, mp_int *x, mp_int *y);
mp_err mp_invmod(const mp_int *a, const mp_int *m, mp_int *c);
mp_err mp_invmod_xgcd(const mp_int *a, const mp_int *m, mp_int *c);
#endif /* end MP_NUMTH */

/* Input and output        */
#if MP_IOFUNC
void   mp_print(mp_int *mp, FILE *ofp);
#endif /* end MP_IOFUNC */

/* Base conversion         */
mp_err mp_read_raw(mp_int *mp, char *str, int len);
int    mp_raw_size(mp_int *mp);
mp_err mp_toraw(mp_int *mp, char *str);
mp_err mp_read_radix(mp_int *mp, const char *str, int radix);
mp_err mp_read_variable_radix(mp_int *a, const char * str, int default_radix);
int    mp_radix_size(mp_int *mp, int radix);
mp_err mp_toradix(mp_int *mp, char *str, int radix);
int    mp_tovalue(char ch, int r);

#define mp_tobinary(M, S)  mp_toradix((M), (S), 2)
#define mp_tooctal(M, S)   mp_toradix((M), (S), 8)
#define mp_todecimal(M, S) mp_toradix((M), (S), 10)
#define mp_tohex(M, S)     mp_toradix((M), (S), 16)

/* Error strings           */
const  char  *mp_strerror(mp_err ec);

/* Octet string conversion functions */
mp_err mp_read_unsigned_octets(mp_int *mp, const unsigned char *str, mp_size len);
int    mp_unsigned_octet_size(const mp_int *mp);
mp_err mp_to_unsigned_octets(const mp_int *mp, unsigned char *str, mp_size maxlen);
mp_err mp_to_signed_octets(const mp_int *mp, unsigned char *str, mp_size maxlen);
mp_err mp_to_fixlen_octets(const mp_int *mp, unsigned char *str, mp_size len);

/* Miscellaneous */
mp_size mp_trailing_zeros(const mp_int *mp);

#define MP_CHECKOK(x)  if (MP_OKAY > (res = (x))) goto CLEANUP
#define MP_CHECKERR(x) if (MP_OKAY > (res = (x))) goto CLEANUP

#if defined(MP_API_COMPATIBLE)
#define NEG             MP_NEG
#define ZPOS            MP_ZPOS
#define DIGIT_MAX       MP_DIGIT_MAX
#define DIGIT_BIT       MP_DIGIT_BIT
#define DIGIT_FMT       MP_DIGIT_FMT
#define RADIX           MP_RADIX
#define MAX_RADIX       MP_MAX_RADIX
#define FLAG(MP)        MP_FLAG(MP)
#define SIGN(MP)        MP_SIGN(MP)
#define USED(MP)        MP_USED(MP)
#define ALLOC(MP)       MP_ALLOC(MP)
#define DIGITS(MP)      MP_DIGITS(MP)
#define DIGIT(MP,N)     MP_DIGIT(MP,N)

#if MP_ARGCHK == 1
#define  ARGCHK(X,Y)  {if(!(X)){return (Y);}}
#elif MP_ARGCHK == 2
#ifdef _KERNEL
#define  ARGCHK(X,Y)  ASSERT(X)
#else
#include <assert.h>
#define  ARGCHK(X,Y)  assert(X)
#endif
#else
#define  ARGCHK(X,Y)  /*  */
#endif
#endif /* defined MP_API_COMPATIBLE */

#endif /* _MPI_H */
