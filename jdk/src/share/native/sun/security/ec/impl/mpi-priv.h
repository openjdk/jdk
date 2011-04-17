/* *********************************************************************
 *
 * Sun elects to have this file available under and governed by the
 * Mozilla Public License Version 1.1 ("MPL") (see
 * http://www.mozilla.org/MPL/ for full license text). For the avoidance
 * of doubt and subject to the following, Sun also elects to allow
 * licensees to use this file under the MPL, the GNU General Public
 * License version 2 only or the Lesser General Public License version
 * 2.1 only. Any references to the "GNU General Public License version 2
 * or later" or "GPL" in the following shall be construed to mean the
 * GNU General Public License version 2 only. Any references to the "GNU
 * Lesser General Public License version 2.1 or later" or "LGPL" in the
 * following shall be construed to mean the GNU Lesser General Public
 * License version 2.1 only. However, the following notice accompanied
 * the original version of this file:
 *
 *  Arbitrary precision integer arithmetic library
 *
 *  NOTE WELL: the content of this header file is NOT part of the "public"
 *  API for the MPI library, and may change at any time.
 *  Application programs that use libmpi should NOT include this header file.
 *
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
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
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 *********************************************************************** */
/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
 * Use is subject to license terms.
 */

#ifndef _MPI_PRIV_H
#define _MPI_PRIV_H

/* $Id: mpi-priv.h,v 1.20 2005/11/22 07:16:43 relyea%netscape.com Exp $ */

#include "mpi.h"
#ifndef _KERNEL
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#endif /* _KERNEL */

#if MP_DEBUG
#include <stdio.h>

#define DIAG(T,V) {fprintf(stderr,T);mp_print(V,stderr);fputc('\n',stderr);}
#else
#define DIAG(T,V)
#endif

/* If we aren't using a wired-in logarithm table, we need to include
   the math library to get the log() function
 */

/* {{{ s_logv_2[] - log table for 2 in various bases */

#if MP_LOGTAB
/*
  A table of the logs of 2 for various bases (the 0 and 1 entries of
  this table are meaningless and should not be referenced).

  This table is used to compute output lengths for the mp_toradix()
  function.  Since a number n in radix r takes up about log_r(n)
  digits, we estimate the output size by taking the least integer
  greater than log_r(n), where:

  log_r(n) = log_2(n) * log_r(2)

  This table, therefore, is a table of log_r(2) for 2 <= r <= 36,
  which are the output bases supported.
 */

extern const float s_logv_2[];
#define LOG_V_2(R)  s_logv_2[(R)]

#else

/*
   If MP_LOGTAB is not defined, use the math library to compute the
   logarithms on the fly.  Otherwise, use the table.
   Pick which works best for your system.
 */

#include <math.h>
#define LOG_V_2(R)  (log(2.0)/log(R))

#endif /* if MP_LOGTAB */

/* }}} */

/* {{{ Digit arithmetic macros */

/*
  When adding and multiplying digits, the results can be larger than
  can be contained in an mp_digit.  Thus, an mp_word is used.  These
  macros mask off the upper and lower digits of the mp_word (the
  mp_word may be more than 2 mp_digits wide, but we only concern
  ourselves with the low-order 2 mp_digits)
 */

#define  CARRYOUT(W)  (mp_digit)((W)>>DIGIT_BIT)
#define  ACCUM(W)     (mp_digit)(W)

#define MP_MIN(a,b)   (((a) < (b)) ? (a) : (b))
#define MP_MAX(a,b)   (((a) > (b)) ? (a) : (b))
#define MP_HOWMANY(a,b) (((a) + (b) - 1)/(b))
#define MP_ROUNDUP(a,b) (MP_HOWMANY(a,b) * (b))

/* }}} */

/* {{{ Comparison constants */

#define  MP_LT       -1
#define  MP_EQ        0
#define  MP_GT        1

/* }}} */

/* {{{ private function declarations */

/*
   If MP_MACRO is false, these will be defined as actual functions;
   otherwise, suitable macro definitions will be used.  This works
   around the fact that ANSI C89 doesn't support an 'inline' keyword
   (although I hear C9x will ... about bloody time).  At present, the
   macro definitions are identical to the function bodies, but they'll
   expand in place, instead of generating a function call.

   I chose these particular functions to be made into macros because
   some profiling showed they are called a lot on a typical workload,
   and yet they are primarily housekeeping.
 */
#if MP_MACRO == 0
 void     s_mp_setz(mp_digit *dp, mp_size count); /* zero digits           */
 void     s_mp_copy(const mp_digit *sp, mp_digit *dp, mp_size count); /* copy */
 void    *s_mp_alloc(size_t nb, size_t ni, int flag); /* general allocator    */
 void     s_mp_free(void *ptr, mp_size);          /* general free function */
extern unsigned long mp_allocs;
extern unsigned long mp_frees;
extern unsigned long mp_copies;
#else

 /* Even if these are defined as macros, we need to respect the settings
    of the MP_MEMSET and MP_MEMCPY configuration options...
  */
 #if MP_MEMSET == 0
  #define  s_mp_setz(dp, count) \
       {int ix;for(ix=0;ix<(count);ix++)(dp)[ix]=0;}
 #else
  #define  s_mp_setz(dp, count) memset(dp, 0, (count) * sizeof(mp_digit))
 #endif /* MP_MEMSET */

 #if MP_MEMCPY == 0
  #define  s_mp_copy(sp, dp, count) \
       {int ix;for(ix=0;ix<(count);ix++)(dp)[ix]=(sp)[ix];}
 #else
  #define  s_mp_copy(sp, dp, count) memcpy(dp, sp, (count) * sizeof(mp_digit))
 #endif /* MP_MEMCPY */

 #define  s_mp_alloc(nb, ni)  calloc(nb, ni)
 #define  s_mp_free(ptr) {if(ptr) free(ptr);}
#endif /* MP_MACRO */

mp_err   s_mp_grow(mp_int *mp, mp_size min);   /* increase allocated size */
mp_err   s_mp_pad(mp_int *mp, mp_size min);    /* left pad with zeroes    */

#if MP_MACRO == 0
 void     s_mp_clamp(mp_int *mp);               /* clip leading zeroes     */
#else
 #define  s_mp_clamp(mp)\
  { mp_size used = MP_USED(mp); \
    while (used > 1 && DIGIT(mp, used - 1) == 0) --used; \
    MP_USED(mp) = used; \
  }
#endif /* MP_MACRO */

void     s_mp_exch(mp_int *a, mp_int *b);      /* swap a and b in place   */

mp_err   s_mp_lshd(mp_int *mp, mp_size p);     /* left-shift by p digits  */
void     s_mp_rshd(mp_int *mp, mp_size p);     /* right-shift by p digits */
mp_err   s_mp_mul_2d(mp_int *mp, mp_digit d);  /* multiply by 2^d in place */
void     s_mp_div_2d(mp_int *mp, mp_digit d);  /* divide by 2^d in place  */
void     s_mp_mod_2d(mp_int *mp, mp_digit d);  /* modulo 2^d in place     */
void     s_mp_div_2(mp_int *mp);               /* divide by 2 in place    */
mp_err   s_mp_mul_2(mp_int *mp);               /* multiply by 2 in place  */
mp_err   s_mp_norm(mp_int *a, mp_int *b, mp_digit *pd);
                                               /* normalize for division  */
mp_err   s_mp_add_d(mp_int *mp, mp_digit d);   /* unsigned digit addition */
mp_err   s_mp_sub_d(mp_int *mp, mp_digit d);   /* unsigned digit subtract */
mp_err   s_mp_mul_d(mp_int *mp, mp_digit d);   /* unsigned digit multiply */
mp_err   s_mp_div_d(mp_int *mp, mp_digit d, mp_digit *r);
                                               /* unsigned digit divide   */
mp_err   s_mp_reduce(mp_int *x, const mp_int *m, const mp_int *mu);
                                               /* Barrett reduction       */
mp_err   s_mp_add(mp_int *a, const mp_int *b); /* magnitude addition      */
mp_err   s_mp_add_3arg(const mp_int *a, const mp_int *b, mp_int *c);
mp_err   s_mp_sub(mp_int *a, const mp_int *b); /* magnitude subtract      */
mp_err   s_mp_sub_3arg(const mp_int *a, const mp_int *b, mp_int *c);
mp_err   s_mp_add_offset(mp_int *a, mp_int *b, mp_size offset);
                                               /* a += b * RADIX^offset   */
mp_err   s_mp_mul(mp_int *a, const mp_int *b); /* magnitude multiply      */
#if MP_SQUARE
mp_err   s_mp_sqr(mp_int *a);                  /* magnitude square        */
#else
#define  s_mp_sqr(a) s_mp_mul(a, a)
#endif
mp_err   s_mp_div(mp_int *rem, mp_int *div, mp_int *quot); /* magnitude div */
mp_err   s_mp_exptmod(const mp_int *a, const mp_int *b, const mp_int *m, mp_int *c);
mp_err   s_mp_2expt(mp_int *a, mp_digit k);    /* a = 2^k                 */
int      s_mp_cmp(const mp_int *a, const mp_int *b); /* magnitude comparison */
int      s_mp_cmp_d(const mp_int *a, mp_digit d); /* magnitude digit compare */
int      s_mp_ispow2(const mp_int *v);         /* is v a power of 2?      */
int      s_mp_ispow2d(mp_digit d);             /* is d a power of 2?      */

int      s_mp_tovalue(char ch, int r);          /* convert ch to value    */
char     s_mp_todigit(mp_digit val, int r, int low); /* convert val to digit */
int      s_mp_outlen(int bits, int r);          /* output length in bytes */
mp_digit s_mp_invmod_radix(mp_digit P);   /* returns (P ** -1) mod RADIX */
mp_err   s_mp_invmod_odd_m( const mp_int *a, const mp_int *m, mp_int *c);
mp_err   s_mp_invmod_2d(    const mp_int *a, mp_size k,       mp_int *c);
mp_err   s_mp_invmod_even_m(const mp_int *a, const mp_int *m, mp_int *c);

#ifdef NSS_USE_COMBA

#define IS_POWER_OF_2(a) ((a) && !((a) & ((a)-1)))

void s_mp_mul_comba_4(const mp_int *A, const mp_int *B, mp_int *C);
void s_mp_mul_comba_8(const mp_int *A, const mp_int *B, mp_int *C);
void s_mp_mul_comba_16(const mp_int *A, const mp_int *B, mp_int *C);
void s_mp_mul_comba_32(const mp_int *A, const mp_int *B, mp_int *C);

void s_mp_sqr_comba_4(const mp_int *A, mp_int *B);
void s_mp_sqr_comba_8(const mp_int *A, mp_int *B);
void s_mp_sqr_comba_16(const mp_int *A, mp_int *B);
void s_mp_sqr_comba_32(const mp_int *A, mp_int *B);

#endif /* end NSS_USE_COMBA */

/* ------ mpv functions, operate on arrays of digits, not on mp_int's ------ */
#if defined (__OS2__) && defined (__IBMC__)
#define MPI_ASM_DECL __cdecl
#else
#define MPI_ASM_DECL
#endif

#ifdef MPI_AMD64

mp_digit MPI_ASM_DECL s_mpv_mul_set_vec64(mp_digit*, mp_digit *, mp_size, mp_digit);
mp_digit MPI_ASM_DECL s_mpv_mul_add_vec64(mp_digit*, const mp_digit*, mp_size, mp_digit);

/* c = a * b */
#define s_mpv_mul_d(a, a_len, b, c) \
        ((unsigned long*)c)[a_len] = s_mpv_mul_set_vec64(c, a, a_len, b)

/* c += a * b */
#define s_mpv_mul_d_add(a, a_len, b, c) \
        ((unsigned long*)c)[a_len] = s_mpv_mul_add_vec64(c, a, a_len, b)

#else

void     MPI_ASM_DECL s_mpv_mul_d(const mp_digit *a, mp_size a_len,
                                        mp_digit b, mp_digit *c);
void     MPI_ASM_DECL s_mpv_mul_d_add(const mp_digit *a, mp_size a_len,
                                            mp_digit b, mp_digit *c);

#endif

void     MPI_ASM_DECL s_mpv_mul_d_add_prop(const mp_digit *a,
                                                mp_size a_len, mp_digit b,
                                                mp_digit *c);
void     MPI_ASM_DECL s_mpv_sqr_add_prop(const mp_digit *a,
                                                mp_size a_len,
                                                mp_digit *sqrs);

mp_err   MPI_ASM_DECL s_mpv_div_2dx1d(mp_digit Nhi, mp_digit Nlo,
                            mp_digit divisor, mp_digit *quot, mp_digit *rem);

/* c += a * b * (MP_RADIX ** offset);  */
#define s_mp_mul_d_add_offset(a, b, c, off) \
(s_mpv_mul_d_add_prop(MP_DIGITS(a), MP_USED(a), b, MP_DIGITS(c) + off), MP_OKAY)

typedef struct {
  mp_int       N;       /* modulus N */
  mp_digit     n0prime; /* n0' = - (n0 ** -1) mod MP_RADIX */
  mp_size      b;       /* R == 2 ** b,  also b = # significant bits in N */
} mp_mont_modulus;

mp_err s_mp_mul_mont(const mp_int *a, const mp_int *b, mp_int *c,
                       mp_mont_modulus *mmm);
mp_err s_mp_redc(mp_int *T, mp_mont_modulus *mmm);

/*
 * s_mpi_getProcessorLineSize() returns the size in bytes of the cache line
 * if a cache exists, or zero if there is no cache. If more than one
 * cache line exists, it should return the smallest line size (which is
 * usually the L1 cache).
 *
 * mp_modexp uses this information to make sure that private key information
 * isn't being leaked through the cache.
 *
 * see mpcpucache.c for the implementation.
 */
unsigned long s_mpi_getProcessorLineSize();

/* }}} */
#endif /* _MPI_PRIV_H */
