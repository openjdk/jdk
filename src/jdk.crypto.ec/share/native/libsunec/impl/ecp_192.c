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
 * The Original Code is the elliptic curve math library for prime field curves.
 *
 * The Initial Developer of the Original Code is
 * Sun Microsystems, Inc.
 * Portions created by the Initial Developer are Copyright (C) 2003
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Douglas Stebila <douglas@stebila.ca>, Sun Microsystems Laboratories
 *
 *********************************************************************** */

#include "ecp.h"
#include "mpi.h"
#include "mplogic.h"
#include "mpi-priv.h"
#ifndef _KERNEL
#include <stdlib.h>
#endif

#define ECP192_DIGITS ECL_CURVE_DIGITS(192)

/* Fast modular reduction for p192 = 2^192 - 2^64 - 1.  a can be r. Uses
 * algorithm 7 from Brown, Hankerson, Lopez, Menezes. Software
 * Implementation of the NIST Elliptic Curves over Prime Fields. */
mp_err
ec_GFp_nistp192_mod(const mp_int *a, mp_int *r, const GFMethod *meth)
{
        mp_err res = MP_OKAY;
        mp_size a_used = MP_USED(a);
        mp_digit r3;
#ifndef MPI_AMD64_ADD
        mp_digit carry;
#endif
#ifdef ECL_THIRTY_TWO_BIT
        mp_digit a5a = 0, a5b = 0, a4a = 0, a4b = 0, a3a = 0, a3b = 0;
        mp_digit r0a, r0b, r1a, r1b, r2a, r2b;
#else
        mp_digit a5 = 0, a4 = 0, a3 = 0;
        mp_digit r0, r1, r2;
#endif

        /* reduction not needed if a is not larger than field size */
        if (a_used < ECP192_DIGITS) {
                if (a == r) {
                        return MP_OKAY;
                }
                return mp_copy(a, r);
        }

        /* for polynomials larger than twice the field size, use regular
         * reduction */
        if (a_used > ECP192_DIGITS*2) {
                MP_CHECKOK(mp_mod(a, &meth->irr, r));
        } else {
                /* copy out upper words of a */

#ifdef ECL_THIRTY_TWO_BIT

                /* in all the math below,
                 * nXb is most signifiant, nXa is least significant */
                switch (a_used) {
                case 12:
                        a5b = MP_DIGIT(a, 11);
                case 11:
                        a5a = MP_DIGIT(a, 10);
                case 10:
                        a4b = MP_DIGIT(a, 9);
                case 9:
                        a4a = MP_DIGIT(a, 8);
                case 8:
                        a3b = MP_DIGIT(a, 7);
                case 7:
                        a3a = MP_DIGIT(a, 6);
                }


                r2b= MP_DIGIT(a, 5);
                r2a= MP_DIGIT(a, 4);
                r1b = MP_DIGIT(a, 3);
                r1a = MP_DIGIT(a, 2);
                r0b = MP_DIGIT(a, 1);
                r0a = MP_DIGIT(a, 0);

                /* implement r = (a2,a1,a0)+(a5,a5,a5)+(a4,a4,0)+(0,a3,a3) */
                MP_ADD_CARRY(r0a, a3a, r0a, 0,    carry);
                MP_ADD_CARRY(r0b, a3b, r0b, carry, carry);
                MP_ADD_CARRY(r1a, a3a, r1a, carry, carry);
                MP_ADD_CARRY(r1b, a3b, r1b, carry, carry);
                MP_ADD_CARRY(r2a, a4a, r2a, carry, carry);
                MP_ADD_CARRY(r2b, a4b, r2b, carry, carry);
                r3 = carry; carry = 0;
                MP_ADD_CARRY(r0a, a5a, r0a, 0,     carry);
                MP_ADD_CARRY(r0b, a5b, r0b, carry, carry);
                MP_ADD_CARRY(r1a, a5a, r1a, carry, carry);
                MP_ADD_CARRY(r1b, a5b, r1b, carry, carry);
                MP_ADD_CARRY(r2a, a5a, r2a, carry, carry);
                MP_ADD_CARRY(r2b, a5b, r2b, carry, carry);
                r3 += carry;
                MP_ADD_CARRY(r1a, a4a, r1a, 0,     carry);
                MP_ADD_CARRY(r1b, a4b, r1b, carry, carry);
                MP_ADD_CARRY(r2a,   0, r2a, carry, carry);
                MP_ADD_CARRY(r2b,   0, r2b, carry, carry);
                r3 += carry;

                /* reduce out the carry */
                while (r3) {
                        MP_ADD_CARRY(r0a, r3, r0a, 0,     carry);
                        MP_ADD_CARRY(r0b,  0, r0b, carry, carry);
                        MP_ADD_CARRY(r1a, r3, r1a, carry, carry);
                        MP_ADD_CARRY(r1b,  0, r1b, carry, carry);
                        MP_ADD_CARRY(r2a,  0, r2a, carry, carry);
                        MP_ADD_CARRY(r2b,  0, r2b, carry, carry);
                        r3 = carry;
                }

                /* check for final reduction */
                /*
                 * our field is 0xffffffffffffffff, 0xfffffffffffffffe,
                 * 0xffffffffffffffff. That means we can only be over and need
                 * one more reduction
                 *  if r2 == 0xffffffffffffffffff (same as r2+1 == 0)
                 *     and
                 *     r1 == 0xffffffffffffffffff   or
                 *     r1 == 0xfffffffffffffffffe and r0 = 0xfffffffffffffffff
                 * In all cases, we subtract the field (or add the 2's
                 * complement value (1,1,0)).  (r0, r1, r2)
                 */
                if (((r2b == 0xffffffff) && (r2a == 0xffffffff)
                        && (r1b == 0xffffffff) ) &&
                           ((r1a == 0xffffffff) ||
                            (r1a == 0xfffffffe) && (r0a == 0xffffffff) &&
                                        (r0b == 0xffffffff)) ) {
                        /* do a quick subtract */
                        MP_ADD_CARRY(r0a, 1, r0a, 0, carry);
                        r0b += carry;
                        r1a = r1b = r2a = r2b = 0;
                }

                /* set the lower words of r */
                if (a != r) {
                        MP_CHECKOK(s_mp_pad(r, 6));
                }
                MP_DIGIT(r, 5) = r2b;
                MP_DIGIT(r, 4) = r2a;
                MP_DIGIT(r, 3) = r1b;
                MP_DIGIT(r, 2) = r1a;
                MP_DIGIT(r, 1) = r0b;
                MP_DIGIT(r, 0) = r0a;
                MP_USED(r) = 6;
#else
                switch (a_used) {
                case 6:
                        a5 = MP_DIGIT(a, 5);
                case 5:
                        a4 = MP_DIGIT(a, 4);
                case 4:
                        a3 = MP_DIGIT(a, 3);
                }

                r2 = MP_DIGIT(a, 2);
                r1 = MP_DIGIT(a, 1);
                r0 = MP_DIGIT(a, 0);

                /* implement r = (a2,a1,a0)+(a5,a5,a5)+(a4,a4,0)+(0,a3,a3) */
#ifndef MPI_AMD64_ADD
                MP_ADD_CARRY_ZERO(r0, a3, r0, carry);
                MP_ADD_CARRY(r1, a3, r1, carry, carry);
                MP_ADD_CARRY(r2, a4, r2, carry, carry);
                r3 = carry;
                MP_ADD_CARRY_ZERO(r0, a5, r0, carry);
                MP_ADD_CARRY(r1, a5, r1, carry, carry);
                MP_ADD_CARRY(r2, a5, r2, carry, carry);
                r3 += carry;
                MP_ADD_CARRY_ZERO(r1, a4, r1, carry);
                MP_ADD_CARRY(r2,  0, r2, carry, carry);
                r3 += carry;

#else
                r2 = MP_DIGIT(a, 2);
                r1 = MP_DIGIT(a, 1);
                r0 = MP_DIGIT(a, 0);

                /* set the lower words of r */
                __asm__ (
                "xorq   %3,%3           \n\t"
                "addq   %4,%0           \n\t"
                "adcq   %4,%1           \n\t"
                "adcq   %5,%2           \n\t"
                "adcq   $0,%3           \n\t"
                "addq   %6,%0           \n\t"
                "adcq   %6,%1           \n\t"
                "adcq   %6,%2           \n\t"
                "adcq   $0,%3           \n\t"
                "addq   %5,%1           \n\t"
                "adcq   $0,%2           \n\t"
                "adcq   $0,%3           \n\t"
                : "=r"(r0), "=r"(r1), "=r"(r2), "=r"(r3), "=r"(a3),
                  "=r"(a4), "=r"(a5)
                : "0" (r0), "1" (r1), "2" (r2), "3" (r3),
                  "4" (a3), "5" (a4), "6"(a5)
                : "%cc" );
#endif

                /* reduce out the carry */
                while (r3) {
#ifndef MPI_AMD64_ADD
                        MP_ADD_CARRY_ZERO(r0, r3, r0, carry);
                        MP_ADD_CARRY(r1, r3, r1, carry, carry);
                        MP_ADD_CARRY(r2,  0, r2, carry, carry);
                        r3 = carry;
#else
                        a3=r3;
                        __asm__ (
                        "xorq   %3,%3           \n\t"
                        "addq   %4,%0           \n\t"
                        "adcq   %4,%1           \n\t"
                        "adcq   $0,%2           \n\t"
                        "adcq   $0,%3           \n\t"
                        : "=r"(r0), "=r"(r1), "=r"(r2), "=r"(r3), "=r"(a3)
                        : "0" (r0), "1" (r1), "2" (r2), "3" (r3), "4"(a3)
                        : "%cc" );
#endif
                }

                /* check for final reduction */
                /*
                 * our field is 0xffffffffffffffff, 0xfffffffffffffffe,
                 * 0xffffffffffffffff. That means we can only be over and need
                 * one more reduction
                 *  if r2 == 0xffffffffffffffffff (same as r2+1 == 0)
                 *     and
                 *     r1 == 0xffffffffffffffffff   or
                 *     r1 == 0xfffffffffffffffffe and r0 = 0xfffffffffffffffff
                 * In all cases, we subtract the field (or add the 2's
                 * complement value (1,1,0)).  (r0, r1, r2)
                 */
                if (r3 || ((r2 == MP_DIGIT_MAX) &&
                      ((r1 == MP_DIGIT_MAX) ||
                        ((r1 == (MP_DIGIT_MAX-1)) && (r0 == MP_DIGIT_MAX))))) {
                        /* do a quick subtract */
                        r0++;
                        r1 = r2 = 0;
                }
                /* set the lower words of r */
                if (a != r) {
                        MP_CHECKOK(s_mp_pad(r, 3));
                }
                MP_DIGIT(r, 2) = r2;
                MP_DIGIT(r, 1) = r1;
                MP_DIGIT(r, 0) = r0;
                MP_USED(r) = 3;
#endif
        }

  CLEANUP:
        return res;
}

#ifndef ECL_THIRTY_TWO_BIT
/* Compute the sum of 192 bit curves. Do the work in-line since the
 * number of words are so small, we don't want to overhead of mp function
 * calls.  Uses optimized modular reduction for p192.
 */
mp_err
ec_GFp_nistp192_add(const mp_int *a, const mp_int *b, mp_int *r,
                        const GFMethod *meth)
{
        mp_err res = MP_OKAY;
        mp_digit a0 = 0, a1 = 0, a2 = 0;
        mp_digit r0 = 0, r1 = 0, r2 = 0;
        mp_digit carry;

        switch(MP_USED(a)) {
        case 3:
                a2 = MP_DIGIT(a,2);
        case 2:
                a1 = MP_DIGIT(a,1);
        case 1:
                a0 = MP_DIGIT(a,0);
        }
        switch(MP_USED(b)) {
        case 3:
                r2 = MP_DIGIT(b,2);
        case 2:
                r1 = MP_DIGIT(b,1);
        case 1:
                r0 = MP_DIGIT(b,0);
        }

#ifndef MPI_AMD64_ADD
        MP_ADD_CARRY_ZERO(a0, r0, r0, carry);
        MP_ADD_CARRY(a1, r1, r1, carry, carry);
        MP_ADD_CARRY(a2, r2, r2, carry, carry);
#else
        __asm__ (
                "xorq   %3,%3           \n\t"
                "addq   %4,%0           \n\t"
                "adcq   %5,%1           \n\t"
                "adcq   %6,%2           \n\t"
                "adcq   $0,%3           \n\t"
                : "=r"(r0), "=r"(r1), "=r"(r2), "=r"(carry)
                : "r" (a0), "r" (a1), "r" (a2), "0" (r0),
                  "1" (r1), "2" (r2)
                : "%cc" );
#endif

        /* Do quick 'subract' if we've gone over
         * (add the 2's complement of the curve field) */
        if (carry || ((r2 == MP_DIGIT_MAX) &&
                      ((r1 == MP_DIGIT_MAX) ||
                        ((r1 == (MP_DIGIT_MAX-1)) && (r0 == MP_DIGIT_MAX))))) {
#ifndef MPI_AMD64_ADD
                MP_ADD_CARRY_ZERO(r0, 1, r0, carry);
                MP_ADD_CARRY(r1, 1, r1, carry, carry);
                MP_ADD_CARRY(r2, 0, r2, carry, carry);
#else
                __asm__ (
                        "addq   $1,%0           \n\t"
                        "adcq   $1,%1           \n\t"
                        "adcq   $0,%2           \n\t"
                        : "=r"(r0), "=r"(r1), "=r"(r2)
                        : "0" (r0), "1" (r1), "2" (r2)
                        : "%cc" );
#endif
        }


        MP_CHECKOK(s_mp_pad(r, 3));
        MP_DIGIT(r, 2) = r2;
        MP_DIGIT(r, 1) = r1;
        MP_DIGIT(r, 0) = r0;
        MP_SIGN(r) = MP_ZPOS;
        MP_USED(r) = 3;
        s_mp_clamp(r);


  CLEANUP:
        return res;
}

/* Compute the diff of 192 bit curves. Do the work in-line since the
 * number of words are so small, we don't want to overhead of mp function
 * calls.  Uses optimized modular reduction for p192.
 */
mp_err
ec_GFp_nistp192_sub(const mp_int *a, const mp_int *b, mp_int *r,
                        const GFMethod *meth)
{
        mp_err res = MP_OKAY;
        mp_digit b0 = 0, b1 = 0, b2 = 0;
        mp_digit r0 = 0, r1 = 0, r2 = 0;
        mp_digit borrow;

        switch(MP_USED(a)) {
        case 3:
                r2 = MP_DIGIT(a,2);
        case 2:
                r1 = MP_DIGIT(a,1);
        case 1:
                r0 = MP_DIGIT(a,0);
        }

        switch(MP_USED(b)) {
        case 3:
                b2 = MP_DIGIT(b,2);
        case 2:
                b1 = MP_DIGIT(b,1);
        case 1:
                b0 = MP_DIGIT(b,0);
        }

#ifndef MPI_AMD64_ADD
        MP_SUB_BORROW(r0, b0, r0, 0,     borrow);
        MP_SUB_BORROW(r1, b1, r1, borrow, borrow);
        MP_SUB_BORROW(r2, b2, r2, borrow, borrow);
#else
        __asm__ (
                "xorq   %3,%3           \n\t"
                "subq   %4,%0           \n\t"
                "sbbq   %5,%1           \n\t"
                "sbbq   %6,%2           \n\t"
                "adcq   $0,%3           \n\t"
                : "=r"(r0), "=r"(r1), "=r"(r2), "=r"(borrow)
                : "r" (b0), "r" (b1), "r" (b2), "0" (r0),
                  "1" (r1), "2" (r2)
                : "%cc" );
#endif

        /* Do quick 'add' if we've gone under 0
         * (subtract the 2's complement of the curve field) */
        if (borrow) {
#ifndef MPI_AMD64_ADD
                MP_SUB_BORROW(r0, 1, r0, 0,     borrow);
                MP_SUB_BORROW(r1, 1, r1, borrow, borrow);
                MP_SUB_BORROW(r2,  0, r2, borrow, borrow);
#else
                __asm__ (
                        "subq   $1,%0           \n\t"
                        "sbbq   $1,%1           \n\t"
                        "sbbq   $0,%2           \n\t"
                        : "=r"(r0), "=r"(r1), "=r"(r2)
                        : "0" (r0), "1" (r1), "2" (r2)
                        : "%cc" );
#endif
        }

        MP_CHECKOK(s_mp_pad(r, 3));
        MP_DIGIT(r, 2) = r2;
        MP_DIGIT(r, 1) = r1;
        MP_DIGIT(r, 0) = r0;
        MP_SIGN(r) = MP_ZPOS;
        MP_USED(r) = 3;
        s_mp_clamp(r);

  CLEANUP:
        return res;
}

#endif

/* Compute the square of polynomial a, reduce modulo p192. Store the
 * result in r.  r could be a.  Uses optimized modular reduction for p192.
 */
mp_err
ec_GFp_nistp192_sqr(const mp_int *a, mp_int *r, const GFMethod *meth)
{
        mp_err res = MP_OKAY;

        MP_CHECKOK(mp_sqr(a, r));
        MP_CHECKOK(ec_GFp_nistp192_mod(r, r, meth));
  CLEANUP:
        return res;
}

/* Compute the product of two polynomials a and b, reduce modulo p192.
 * Store the result in r.  r could be a or b; a could be b.  Uses
 * optimized modular reduction for p192. */
mp_err
ec_GFp_nistp192_mul(const mp_int *a, const mp_int *b, mp_int *r,
                                        const GFMethod *meth)
{
        mp_err res = MP_OKAY;

        MP_CHECKOK(mp_mul(a, b, r));
        MP_CHECKOK(ec_GFp_nistp192_mod(r, r, meth));
  CLEANUP:
        return res;
}

/* Divides two field elements. If a is NULL, then returns the inverse of
 * b. */
mp_err
ec_GFp_nistp192_div(const mp_int *a, const mp_int *b, mp_int *r,
                   const GFMethod *meth)
{
        mp_err res = MP_OKAY;
        mp_int t;

        /* If a is NULL, then return the inverse of b, otherwise return a/b. */
        if (a == NULL) {
                return  mp_invmod(b, &meth->irr, r);
        } else {
                /* MPI doesn't support divmod, so we implement it using invmod and
                 * mulmod. */
                MP_CHECKOK(mp_init(&t, FLAG(b)));
                MP_CHECKOK(mp_invmod(b, &meth->irr, &t));
                MP_CHECKOK(mp_mul(a, &t, r));
                MP_CHECKOK(ec_GFp_nistp192_mod(r, r, meth));
          CLEANUP:
                mp_clear(&t);
                return res;
        }
}

/* Wire in fast field arithmetic and precomputation of base point for
 * named curves. */
mp_err
ec_group_set_gfp192(ECGroup *group, ECCurveName name)
{
        if (name == ECCurve_NIST_P192) {
                group->meth->field_mod = &ec_GFp_nistp192_mod;
                group->meth->field_mul = &ec_GFp_nistp192_mul;
                group->meth->field_sqr = &ec_GFp_nistp192_sqr;
                group->meth->field_div = &ec_GFp_nistp192_div;
#ifndef ECL_THIRTY_TWO_BIT
                group->meth->field_add = &ec_GFp_nistp192_add;
                group->meth->field_sub = &ec_GFp_nistp192_sub;
#endif
        }
        return MP_OKAY;
}
