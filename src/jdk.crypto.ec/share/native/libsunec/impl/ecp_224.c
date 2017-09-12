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

#define ECP224_DIGITS ECL_CURVE_DIGITS(224)

/* Fast modular reduction for p224 = 2^224 - 2^96 + 1.  a can be r. Uses
 * algorithm 7 from Brown, Hankerson, Lopez, Menezes. Software
 * Implementation of the NIST Elliptic Curves over Prime Fields. */
mp_err
ec_GFp_nistp224_mod(const mp_int *a, mp_int *r, const GFMethod *meth)
{
        mp_err res = MP_OKAY;
        mp_size a_used = MP_USED(a);

        int    r3b;
        mp_digit carry;
#ifdef ECL_THIRTY_TWO_BIT
        mp_digit a6a = 0, a6b = 0,
                a5a = 0, a5b = 0, a4a = 0, a4b = 0, a3a = 0, a3b = 0;
        mp_digit r0a, r0b, r1a, r1b, r2a, r2b, r3a;
#else
        mp_digit a6 = 0, a5 = 0, a4 = 0, a3b = 0, a5a = 0;
        mp_digit a6b = 0, a6a_a5b = 0, a5b = 0, a5a_a4b = 0, a4a_a3b = 0;
        mp_digit r0, r1, r2, r3;
#endif

        /* reduction not needed if a is not larger than field size */
        if (a_used < ECP224_DIGITS) {
                if (a == r) return MP_OKAY;
                return mp_copy(a, r);
        }
        /* for polynomials larger than twice the field size, use regular
         * reduction */
        if (a_used > ECL_CURVE_DIGITS(224*2)) {
                MP_CHECKOK(mp_mod(a, &meth->irr, r));
        } else {
#ifdef ECL_THIRTY_TWO_BIT
                /* copy out upper words of a */
                switch (a_used) {
                case 14:
                        a6b = MP_DIGIT(a, 13);
                case 13:
                        a6a = MP_DIGIT(a, 12);
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
                }
                r3a = MP_DIGIT(a, 6);
                r2b= MP_DIGIT(a, 5);
                r2a= MP_DIGIT(a, 4);
                r1b = MP_DIGIT(a, 3);
                r1a = MP_DIGIT(a, 2);
                r0b = MP_DIGIT(a, 1);
                r0a = MP_DIGIT(a, 0);


                /* implement r = (a3a,a2,a1,a0)
                        +(a5a, a4,a3b,  0)
                        +(  0, a6,a5b,  0)
                        -(  0    0,    0|a6b, a6a|a5b )
                        -(  a6b, a6a|a5b, a5a|a4b, a4a|a3b ) */
                MP_ADD_CARRY (r1b, a3b, r1b, 0,     carry);
                MP_ADD_CARRY (r2a, a4a, r2a, carry, carry);
                MP_ADD_CARRY (r2b, a4b, r2b, carry, carry);
                MP_ADD_CARRY (r3a, a5a, r3a, carry, carry);
                r3b = carry;
                MP_ADD_CARRY (r1b, a5b, r1b, 0,     carry);
                MP_ADD_CARRY (r2a, a6a, r2a, carry, carry);
                MP_ADD_CARRY (r2b, a6b, r2b, carry, carry);
                MP_ADD_CARRY (r3a,   0, r3a, carry, carry);
                r3b += carry;
                MP_SUB_BORROW(r0a, a3b, r0a, 0,     carry);
                MP_SUB_BORROW(r0b, a4a, r0b, carry, carry);
                MP_SUB_BORROW(r1a, a4b, r1a, carry, carry);
                MP_SUB_BORROW(r1b, a5a, r1b, carry, carry);
                MP_SUB_BORROW(r2a, a5b, r2a, carry, carry);
                MP_SUB_BORROW(r2b, a6a, r2b, carry, carry);
                MP_SUB_BORROW(r3a, a6b, r3a, carry, carry);
                r3b -= carry;
                MP_SUB_BORROW(r0a, a5b, r0a, 0,     carry);
                MP_SUB_BORROW(r0b, a6a, r0b, carry, carry);
                MP_SUB_BORROW(r1a, a6b, r1a, carry, carry);
                if (carry) {
                        MP_SUB_BORROW(r1b, 0, r1b, carry, carry);
                        MP_SUB_BORROW(r2a, 0, r2a, carry, carry);
                        MP_SUB_BORROW(r2b, 0, r2b, carry, carry);
                        MP_SUB_BORROW(r3a, 0, r3a, carry, carry);
                        r3b -= carry;
                }

                while (r3b > 0) {
                        int tmp;
                        MP_ADD_CARRY(r1b, r3b, r1b, 0,     carry);
                        if (carry) {
                                MP_ADD_CARRY(r2a,  0, r2a, carry, carry);
                                MP_ADD_CARRY(r2b,  0, r2b, carry, carry);
                                MP_ADD_CARRY(r3a,  0, r3a, carry, carry);
                        }
                        tmp = carry;
                        MP_SUB_BORROW(r0a, r3b, r0a, 0,     carry);
                        if (carry) {
                                MP_SUB_BORROW(r0b, 0, r0b, carry, carry);
                                MP_SUB_BORROW(r1a, 0, r1a, carry, carry);
                                MP_SUB_BORROW(r1b, 0, r1b, carry, carry);
                                MP_SUB_BORROW(r2a, 0, r2a, carry, carry);
                                MP_SUB_BORROW(r2b, 0, r2b, carry, carry);
                                MP_SUB_BORROW(r3a, 0, r3a, carry, carry);
                                tmp -= carry;
                        }
                        r3b = tmp;
                }

                while (r3b < 0) {
                        mp_digit maxInt = MP_DIGIT_MAX;
                        MP_ADD_CARRY (r0a, 1, r0a, 0,     carry);
                        MP_ADD_CARRY (r0b, 0, r0b, carry, carry);
                        MP_ADD_CARRY (r1a, 0, r1a, carry, carry);
                        MP_ADD_CARRY (r1b, maxInt, r1b, carry, carry);
                        MP_ADD_CARRY (r2a, maxInt, r2a, carry, carry);
                        MP_ADD_CARRY (r2b, maxInt, r2b, carry, carry);
                        MP_ADD_CARRY (r3a, maxInt, r3a, carry, carry);
                        r3b += carry;
                }
                /* check for final reduction */
                /* now the only way we are over is if the top 4 words are all ones */
                if ((r3a == MP_DIGIT_MAX) && (r2b == MP_DIGIT_MAX)
                        && (r2a == MP_DIGIT_MAX) && (r1b == MP_DIGIT_MAX) &&
                         ((r1a != 0) || (r0b != 0) || (r0a != 0)) ) {
                        /* one last subraction */
                        MP_SUB_BORROW(r0a, 1, r0a, 0,     carry);
                        MP_SUB_BORROW(r0b, 0, r0b, carry, carry);
                        MP_SUB_BORROW(r1a, 0, r1a, carry, carry);
                        r1b = r2a = r2b = r3a = 0;
                }


                if (a != r) {
                        MP_CHECKOK(s_mp_pad(r, 7));
                }
                /* set the lower words of r */
                MP_SIGN(r) = MP_ZPOS;
                MP_USED(r) = 7;
                MP_DIGIT(r, 6) = r3a;
                MP_DIGIT(r, 5) = r2b;
                MP_DIGIT(r, 4) = r2a;
                MP_DIGIT(r, 3) = r1b;
                MP_DIGIT(r, 2) = r1a;
                MP_DIGIT(r, 1) = r0b;
                MP_DIGIT(r, 0) = r0a;
#else
                /* copy out upper words of a */
                switch (a_used) {
                case 7:
                        a6 = MP_DIGIT(a, 6);
                        a6b = a6 >> 32;
                        a6a_a5b = a6 << 32;
                case 6:
                        a5 = MP_DIGIT(a, 5);
                        a5b = a5 >> 32;
                        a6a_a5b |= a5b;
                        a5b = a5b << 32;
                        a5a_a4b = a5 << 32;
                        a5a = a5 & 0xffffffff;
                case 5:
                        a4 = MP_DIGIT(a, 4);
                        a5a_a4b |= a4 >> 32;
                        a4a_a3b = a4 << 32;
                case 4:
                        a3b = MP_DIGIT(a, 3) >> 32;
                        a4a_a3b |= a3b;
                        a3b = a3b << 32;
                }

                r3 = MP_DIGIT(a, 3) & 0xffffffff;
                r2 = MP_DIGIT(a, 2);
                r1 = MP_DIGIT(a, 1);
                r0 = MP_DIGIT(a, 0);

                /* implement r = (a3a,a2,a1,a0)
                        +(a5a, a4,a3b,  0)
                        +(  0, a6,a5b,  0)
                        -(  0    0,    0|a6b, a6a|a5b )
                        -(  a6b, a6a|a5b, a5a|a4b, a4a|a3b ) */
                MP_ADD_CARRY_ZERO (r1, a3b, r1, carry);
                MP_ADD_CARRY (r2, a4 , r2, carry, carry);
                MP_ADD_CARRY (r3, a5a, r3, carry, carry);
                MP_ADD_CARRY_ZERO (r1, a5b, r1, carry);
                MP_ADD_CARRY (r2, a6 , r2, carry, carry);
                MP_ADD_CARRY (r3,   0, r3, carry, carry);

                MP_SUB_BORROW(r0, a4a_a3b, r0, 0,     carry);
                MP_SUB_BORROW(r1, a5a_a4b, r1, carry, carry);
                MP_SUB_BORROW(r2, a6a_a5b, r2, carry, carry);
                MP_SUB_BORROW(r3, a6b    , r3, carry, carry);
                MP_SUB_BORROW(r0, a6a_a5b, r0, 0,     carry);
                MP_SUB_BORROW(r1, a6b    , r1, carry, carry);
                if (carry) {
                        MP_SUB_BORROW(r2, 0, r2, carry, carry);
                        MP_SUB_BORROW(r3, 0, r3, carry, carry);
                }


                /* if the value is negative, r3 has a 2's complement
                 * high value */
                r3b = (int)(r3 >>32);
                while (r3b > 0) {
                        r3 &= 0xffffffff;
                        MP_ADD_CARRY_ZERO(r1,((mp_digit)r3b) << 32, r1, carry);
                        if (carry) {
                                MP_ADD_CARRY(r2,  0, r2, carry, carry);
                                MP_ADD_CARRY(r3,  0, r3, carry, carry);
                        }
                        MP_SUB_BORROW(r0, r3b, r0, 0, carry);
                        if (carry) {
                                MP_SUB_BORROW(r1, 0, r1, carry, carry);
                                MP_SUB_BORROW(r2, 0, r2, carry, carry);
                                MP_SUB_BORROW(r3, 0, r3, carry, carry);
                        }
                        r3b = (int)(r3 >>32);
                }

                while (r3b < 0) {
                        MP_ADD_CARRY_ZERO (r0, 1, r0, carry);
                        MP_ADD_CARRY (r1, MP_DIGIT_MAX <<32, r1, carry, carry);
                        MP_ADD_CARRY (r2, MP_DIGIT_MAX, r2, carry, carry);
                        MP_ADD_CARRY (r3, MP_DIGIT_MAX >> 32, r3, carry, carry);
                        r3b = (int)(r3 >>32);
                }
                /* check for final reduction */
                /* now the only way we are over is if the top 4 words are all ones */
                if ((r3 == (MP_DIGIT_MAX >> 32)) && (r2 == MP_DIGIT_MAX)
                        && ((r1 & MP_DIGIT_MAX << 32)== MP_DIGIT_MAX << 32) &&
                         ((r1 != MP_DIGIT_MAX << 32 ) || (r0 != 0)) ) {
                        /* one last subraction */
                        MP_SUB_BORROW(r0, 1, r0, 0,     carry);
                        MP_SUB_BORROW(r1, 0, r1, carry, carry);
                        r2 = r3 = 0;
                }


                if (a != r) {
                        MP_CHECKOK(s_mp_pad(r, 4));
                }
                /* set the lower words of r */
                MP_SIGN(r) = MP_ZPOS;
                MP_USED(r) = 4;
                MP_DIGIT(r, 3) = r3;
                MP_DIGIT(r, 2) = r2;
                MP_DIGIT(r, 1) = r1;
                MP_DIGIT(r, 0) = r0;
#endif
        }

  CLEANUP:
        return res;
}

/* Compute the square of polynomial a, reduce modulo p224. Store the
 * result in r.  r could be a.  Uses optimized modular reduction for p224.
 */
mp_err
ec_GFp_nistp224_sqr(const mp_int *a, mp_int *r, const GFMethod *meth)
{
        mp_err res = MP_OKAY;

        MP_CHECKOK(mp_sqr(a, r));
        MP_CHECKOK(ec_GFp_nistp224_mod(r, r, meth));
  CLEANUP:
        return res;
}

/* Compute the product of two polynomials a and b, reduce modulo p224.
 * Store the result in r.  r could be a or b; a could be b.  Uses
 * optimized modular reduction for p224. */
mp_err
ec_GFp_nistp224_mul(const mp_int *a, const mp_int *b, mp_int *r,
                                        const GFMethod *meth)
{
        mp_err res = MP_OKAY;

        MP_CHECKOK(mp_mul(a, b, r));
        MP_CHECKOK(ec_GFp_nistp224_mod(r, r, meth));
  CLEANUP:
        return res;
}

/* Divides two field elements. If a is NULL, then returns the inverse of
 * b. */
mp_err
ec_GFp_nistp224_div(const mp_int *a, const mp_int *b, mp_int *r,
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
                MP_CHECKOK(ec_GFp_nistp224_mod(r, r, meth));
          CLEANUP:
                mp_clear(&t);
                return res;
        }
}

/* Wire in fast field arithmetic and precomputation of base point for
 * named curves. */
mp_err
ec_group_set_gfp224(ECGroup *group, ECCurveName name)
{
        if (name == ECCurve_NIST_P224) {
                group->meth->field_mod = &ec_GFp_nistp224_mod;
                group->meth->field_mul = &ec_GFp_nistp224_mul;
                group->meth->field_sqr = &ec_GFp_nistp224_sqr;
                group->meth->field_div = &ec_GFp_nistp224_div;
        }
        return MP_OKAY;
}
