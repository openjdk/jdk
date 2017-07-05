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
 * The Original Code is the elliptic curve math library.
 *
 * The Initial Developer of the Original Code is
 * Sun Microsystems, Inc.
 * Portions created by the Initial Developer are Copyright (C) 2003
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Stephen Fung <fungstep@hotmail.com> and
 *   Douglas Stebila <douglas@stebila.ca>, Sun Microsystems Laboratories
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
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
 * Use is subject to license terms.
 */

#pragma ident   "%Z%%M% %I%     %E% SMI"

#include "mpi.h"
#include "mp_gf2m.h"
#include "ecl-priv.h"
#include "mpi-priv.h"
#ifndef _KERNEL
#include <stdlib.h>
#endif

/* Allocate memory for a new GFMethod object. */
GFMethod *
GFMethod_new(int kmflag)
{
        mp_err res = MP_OKAY;
        GFMethod *meth;
#ifdef _KERNEL
        meth = (GFMethod *) kmem_alloc(sizeof(GFMethod), kmflag);
#else
        meth = (GFMethod *) malloc(sizeof(GFMethod));
        if (meth == NULL)
                return NULL;
#endif
        meth->constructed = MP_YES;
        MP_DIGITS(&meth->irr) = 0;
        meth->extra_free = NULL;
        MP_CHECKOK(mp_init(&meth->irr, kmflag));

  CLEANUP:
        if (res != MP_OKAY) {
                GFMethod_free(meth);
                return NULL;
        }
        return meth;
}

/* Construct a generic GFMethod for arithmetic over prime fields with
 * irreducible irr. */
GFMethod *
GFMethod_consGFp(const mp_int *irr)
{
        mp_err res = MP_OKAY;
        GFMethod *meth = NULL;

        meth = GFMethod_new(FLAG(irr));
        if (meth == NULL)
                return NULL;

        MP_CHECKOK(mp_copy(irr, &meth->irr));
        meth->irr_arr[0] = mpl_significant_bits(irr);
        meth->irr_arr[1] = meth->irr_arr[2] = meth->irr_arr[3] =
                meth->irr_arr[4] = 0;
        switch(MP_USED(&meth->irr)) {
        /* maybe we need 1 and 2 words here as well?*/
        case 3:
                meth->field_add = &ec_GFp_add_3;
                meth->field_sub = &ec_GFp_sub_3;
                break;
        case 4:
                meth->field_add = &ec_GFp_add_4;
                meth->field_sub = &ec_GFp_sub_4;
                break;
        case 5:
                meth->field_add = &ec_GFp_add_5;
                meth->field_sub = &ec_GFp_sub_5;
                break;
        case 6:
                meth->field_add = &ec_GFp_add_6;
                meth->field_sub = &ec_GFp_sub_6;
                break;
        default:
                meth->field_add = &ec_GFp_add;
                meth->field_sub = &ec_GFp_sub;
        }
        meth->field_neg = &ec_GFp_neg;
        meth->field_mod = &ec_GFp_mod;
        meth->field_mul = &ec_GFp_mul;
        meth->field_sqr = &ec_GFp_sqr;
        meth->field_div = &ec_GFp_div;
        meth->field_enc = NULL;
        meth->field_dec = NULL;
        meth->extra1 = NULL;
        meth->extra2 = NULL;
        meth->extra_free = NULL;

  CLEANUP:
        if (res != MP_OKAY) {
                GFMethod_free(meth);
                return NULL;
        }
        return meth;
}

/* Construct a generic GFMethod for arithmetic over binary polynomial
 * fields with irreducible irr that has array representation irr_arr (see
 * ecl-priv.h for description of the representation).  If irr_arr is NULL,
 * then it is constructed from the bitstring representation. */
GFMethod *
GFMethod_consGF2m(const mp_int *irr, const unsigned int irr_arr[5])
{
        mp_err res = MP_OKAY;
        int ret;
        GFMethod *meth = NULL;

        meth = GFMethod_new(FLAG(irr));
        if (meth == NULL)
                return NULL;

        MP_CHECKOK(mp_copy(irr, &meth->irr));
        if (irr_arr != NULL) {
                /* Irreducible polynomials are either trinomials or pentanomials. */
                meth->irr_arr[0] = irr_arr[0];
                meth->irr_arr[1] = irr_arr[1];
                meth->irr_arr[2] = irr_arr[2];
                if (irr_arr[2] > 0) {
                        meth->irr_arr[3] = irr_arr[3];
                        meth->irr_arr[4] = irr_arr[4];
                } else {
                        meth->irr_arr[3] = meth->irr_arr[4] = 0;
                }
        } else {
                ret = mp_bpoly2arr(irr, meth->irr_arr, 5);
                /* Irreducible polynomials are either trinomials or pentanomials. */
                if ((ret != 5) && (ret != 3)) {
                        res = MP_UNDEF;
                        goto CLEANUP;
                }
        }
        meth->field_add = &ec_GF2m_add;
        meth->field_neg = &ec_GF2m_neg;
        meth->field_sub = &ec_GF2m_add;
        meth->field_mod = &ec_GF2m_mod;
        meth->field_mul = &ec_GF2m_mul;
        meth->field_sqr = &ec_GF2m_sqr;
        meth->field_div = &ec_GF2m_div;
        meth->field_enc = NULL;
        meth->field_dec = NULL;
        meth->extra1 = NULL;
        meth->extra2 = NULL;
        meth->extra_free = NULL;

  CLEANUP:
        if (res != MP_OKAY) {
                GFMethod_free(meth);
                return NULL;
        }
        return meth;
}

/* Free the memory allocated (if any) to a GFMethod object. */
void
GFMethod_free(GFMethod *meth)
{
        if (meth == NULL)
                return;
        if (meth->constructed == MP_NO)
                return;
        mp_clear(&meth->irr);
        if (meth->extra_free != NULL)
                meth->extra_free(meth);
#ifdef _KERNEL
        kmem_free(meth, sizeof(GFMethod));
#else
        free(meth);
#endif
}

/* Wrapper functions for generic prime field arithmetic. */

/* Add two field elements.  Assumes that 0 <= a, b < meth->irr */
mp_err
ec_GFp_add(const mp_int *a, const mp_int *b, mp_int *r,
                   const GFMethod *meth)
{
        /* PRE: 0 <= a, b < p = meth->irr POST: 0 <= r < p, r = a + b (mod p) */
        mp_err res;

        if ((res = mp_add(a, b, r)) != MP_OKAY) {
                return res;
        }
        if (mp_cmp(r, &meth->irr) >= 0) {
                return mp_sub(r, &meth->irr, r);
        }
        return res;
}

/* Negates a field element.  Assumes that 0 <= a < meth->irr */
mp_err
ec_GFp_neg(const mp_int *a, mp_int *r, const GFMethod *meth)
{
        /* PRE: 0 <= a < p = meth->irr POST: 0 <= r < p, r = -a (mod p) */

        if (mp_cmp_z(a) == 0) {
                mp_zero(r);
                return MP_OKAY;
        }
        return mp_sub(&meth->irr, a, r);
}

/* Subtracts two field elements.  Assumes that 0 <= a, b < meth->irr */
mp_err
ec_GFp_sub(const mp_int *a, const mp_int *b, mp_int *r,
                   const GFMethod *meth)
{
        mp_err res = MP_OKAY;

        /* PRE: 0 <= a, b < p = meth->irr POST: 0 <= r < p, r = a - b (mod p) */
        res = mp_sub(a, b, r);
        if (res == MP_RANGE) {
                MP_CHECKOK(mp_sub(b, a, r));
                if (mp_cmp_z(r) < 0) {
                        MP_CHECKOK(mp_add(r, &meth->irr, r));
                }
                MP_CHECKOK(ec_GFp_neg(r, r, meth));
        }
        if (mp_cmp_z(r) < 0) {
                MP_CHECKOK(mp_add(r, &meth->irr, r));
        }
  CLEANUP:
        return res;
}
/*
 * Inline adds for small curve lengths.
 */
/* 3 words */
mp_err
ec_GFp_add_3(const mp_int *a, const mp_int *b, mp_int *r,
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
        MP_ADD_CARRY(a0, r0, r0, 0,     carry);
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
                : "r" (a0), "r" (a1), "r" (a2),
                  "0" (r0), "1" (r1), "2" (r2)
                : "%cc" );
#endif

        MP_CHECKOK(s_mp_pad(r, 3));
        MP_DIGIT(r, 2) = r2;
        MP_DIGIT(r, 1) = r1;
        MP_DIGIT(r, 0) = r0;
        MP_SIGN(r) = MP_ZPOS;
        MP_USED(r) = 3;

        /* Do quick 'subract' if we've gone over
         * (add the 2's complement of the curve field) */
         a2 = MP_DIGIT(&meth->irr,2);
        if (carry ||  r2 >  a2 ||
                ((r2 == a2) && mp_cmp(r,&meth->irr) != MP_LT)) {
                a1 = MP_DIGIT(&meth->irr,1);
                a0 = MP_DIGIT(&meth->irr,0);
#ifndef MPI_AMD64_ADD
                MP_SUB_BORROW(r0, a0, r0, 0,     carry);
                MP_SUB_BORROW(r1, a1, r1, carry, carry);
                MP_SUB_BORROW(r2, a2, r2, carry, carry);
#else
                __asm__ (
                        "subq   %3,%0           \n\t"
                        "sbbq   %4,%1           \n\t"
                        "sbbq   %5,%2           \n\t"
                        : "=r"(r0), "=r"(r1), "=r"(r2)
                        : "r" (a0), "r" (a1), "r" (a2),
                          "0" (r0), "1" (r1), "2" (r2)
                        : "%cc" );
#endif
                MP_DIGIT(r, 2) = r2;
                MP_DIGIT(r, 1) = r1;
                MP_DIGIT(r, 0) = r0;
        }

        s_mp_clamp(r);

  CLEANUP:
        return res;
}

/* 4 words */
mp_err
ec_GFp_add_4(const mp_int *a, const mp_int *b, mp_int *r,
                        const GFMethod *meth)
{
        mp_err res = MP_OKAY;
        mp_digit a0 = 0, a1 = 0, a2 = 0, a3 = 0;
        mp_digit r0 = 0, r1 = 0, r2 = 0, r3 = 0;
        mp_digit carry;

        switch(MP_USED(a)) {
        case 4:
                a3 = MP_DIGIT(a,3);
        case 3:
                a2 = MP_DIGIT(a,2);
        case 2:
                a1 = MP_DIGIT(a,1);
        case 1:
                a0 = MP_DIGIT(a,0);
        }
        switch(MP_USED(b)) {
        case 4:
                r3 = MP_DIGIT(b,3);
        case 3:
                r2 = MP_DIGIT(b,2);
        case 2:
                r1 = MP_DIGIT(b,1);
        case 1:
                r0 = MP_DIGIT(b,0);
        }

#ifndef MPI_AMD64_ADD
        MP_ADD_CARRY(a0, r0, r0, 0,     carry);
        MP_ADD_CARRY(a1, r1, r1, carry, carry);
        MP_ADD_CARRY(a2, r2, r2, carry, carry);
        MP_ADD_CARRY(a3, r3, r3, carry, carry);
#else
        __asm__ (
                "xorq   %4,%4           \n\t"
                "addq   %5,%0           \n\t"
                "adcq   %6,%1           \n\t"
                "adcq   %7,%2           \n\t"
                "adcq   %8,%3           \n\t"
                "adcq   $0,%4           \n\t"
                : "=r"(r0), "=r"(r1), "=r"(r2), "=r"(r3), "=r"(carry)
                : "r" (a0), "r" (a1), "r" (a2), "r" (a3),
                  "0" (r0), "1" (r1), "2" (r2), "3" (r3)
                : "%cc" );
#endif

        MP_CHECKOK(s_mp_pad(r, 4));
        MP_DIGIT(r, 3) = r3;
        MP_DIGIT(r, 2) = r2;
        MP_DIGIT(r, 1) = r1;
        MP_DIGIT(r, 0) = r0;
        MP_SIGN(r) = MP_ZPOS;
        MP_USED(r) = 4;

        /* Do quick 'subract' if we've gone over
         * (add the 2's complement of the curve field) */
         a3 = MP_DIGIT(&meth->irr,3);
        if (carry ||  r3 >  a3 ||
                ((r3 == a3) && mp_cmp(r,&meth->irr) != MP_LT)) {
                a2 = MP_DIGIT(&meth->irr,2);
                a1 = MP_DIGIT(&meth->irr,1);
                a0 = MP_DIGIT(&meth->irr,0);
#ifndef MPI_AMD64_ADD
                MP_SUB_BORROW(r0, a0, r0, 0,     carry);
                MP_SUB_BORROW(r1, a1, r1, carry, carry);
                MP_SUB_BORROW(r2, a2, r2, carry, carry);
                MP_SUB_BORROW(r3, a3, r3, carry, carry);
#else
                __asm__ (
                        "subq   %4,%0           \n\t"
                        "sbbq   %5,%1           \n\t"
                        "sbbq   %6,%2           \n\t"
                        "sbbq   %7,%3           \n\t"
                        : "=r"(r0), "=r"(r1), "=r"(r2), "=r"(r3)
                        : "r" (a0), "r" (a1), "r" (a2), "r" (a3),
                          "0" (r0), "1" (r1), "2" (r2), "3" (r3)
                        : "%cc" );
#endif
                MP_DIGIT(r, 3) = r3;
                MP_DIGIT(r, 2) = r2;
                MP_DIGIT(r, 1) = r1;
                MP_DIGIT(r, 0) = r0;
        }

        s_mp_clamp(r);

  CLEANUP:
        return res;
}

/* 5 words */
mp_err
ec_GFp_add_5(const mp_int *a, const mp_int *b, mp_int *r,
                        const GFMethod *meth)
{
        mp_err res = MP_OKAY;
        mp_digit a0 = 0, a1 = 0, a2 = 0, a3 = 0, a4 = 0;
        mp_digit r0 = 0, r1 = 0, r2 = 0, r3 = 0, r4 = 0;
        mp_digit carry;

        switch(MP_USED(a)) {
        case 5:
                a4 = MP_DIGIT(a,4);
        case 4:
                a3 = MP_DIGIT(a,3);
        case 3:
                a2 = MP_DIGIT(a,2);
        case 2:
                a1 = MP_DIGIT(a,1);
        case 1:
                a0 = MP_DIGIT(a,0);
        }
        switch(MP_USED(b)) {
        case 5:
                r4 = MP_DIGIT(b,4);
        case 4:
                r3 = MP_DIGIT(b,3);
        case 3:
                r2 = MP_DIGIT(b,2);
        case 2:
                r1 = MP_DIGIT(b,1);
        case 1:
                r0 = MP_DIGIT(b,0);
        }

        MP_ADD_CARRY(a0, r0, r0, 0,     carry);
        MP_ADD_CARRY(a1, r1, r1, carry, carry);
        MP_ADD_CARRY(a2, r2, r2, carry, carry);
        MP_ADD_CARRY(a3, r3, r3, carry, carry);
        MP_ADD_CARRY(a4, r4, r4, carry, carry);

        MP_CHECKOK(s_mp_pad(r, 5));
        MP_DIGIT(r, 4) = r4;
        MP_DIGIT(r, 3) = r3;
        MP_DIGIT(r, 2) = r2;
        MP_DIGIT(r, 1) = r1;
        MP_DIGIT(r, 0) = r0;
        MP_SIGN(r) = MP_ZPOS;
        MP_USED(r) = 5;

        /* Do quick 'subract' if we've gone over
         * (add the 2's complement of the curve field) */
         a4 = MP_DIGIT(&meth->irr,4);
        if (carry ||  r4 >  a4 ||
                ((r4 == a4) && mp_cmp(r,&meth->irr) != MP_LT)) {
                a3 = MP_DIGIT(&meth->irr,3);
                a2 = MP_DIGIT(&meth->irr,2);
                a1 = MP_DIGIT(&meth->irr,1);
                a0 = MP_DIGIT(&meth->irr,0);
                MP_SUB_BORROW(r0, a0, r0, 0,     carry);
                MP_SUB_BORROW(r1, a1, r1, carry, carry);
                MP_SUB_BORROW(r2, a2, r2, carry, carry);
                MP_SUB_BORROW(r3, a3, r3, carry, carry);
                MP_SUB_BORROW(r4, a4, r4, carry, carry);
                MP_DIGIT(r, 4) = r4;
                MP_DIGIT(r, 3) = r3;
                MP_DIGIT(r, 2) = r2;
                MP_DIGIT(r, 1) = r1;
                MP_DIGIT(r, 0) = r0;
        }

        s_mp_clamp(r);

  CLEANUP:
        return res;
}

/* 6 words */
mp_err
ec_GFp_add_6(const mp_int *a, const mp_int *b, mp_int *r,
                        const GFMethod *meth)
{
        mp_err res = MP_OKAY;
        mp_digit a0 = 0, a1 = 0, a2 = 0, a3 = 0, a4 = 0, a5 = 0;
        mp_digit r0 = 0, r1 = 0, r2 = 0, r3 = 0, r4 = 0, r5 = 0;
        mp_digit carry;

        switch(MP_USED(a)) {
        case 6:
                a5 = MP_DIGIT(a,5);
        case 5:
                a4 = MP_DIGIT(a,4);
        case 4:
                a3 = MP_DIGIT(a,3);
        case 3:
                a2 = MP_DIGIT(a,2);
        case 2:
                a1 = MP_DIGIT(a,1);
        case 1:
                a0 = MP_DIGIT(a,0);
        }
        switch(MP_USED(b)) {
        case 6:
                r5 = MP_DIGIT(b,5);
        case 5:
                r4 = MP_DIGIT(b,4);
        case 4:
                r3 = MP_DIGIT(b,3);
        case 3:
                r2 = MP_DIGIT(b,2);
        case 2:
                r1 = MP_DIGIT(b,1);
        case 1:
                r0 = MP_DIGIT(b,0);
        }

        MP_ADD_CARRY(a0, r0, r0, 0,     carry);
        MP_ADD_CARRY(a1, r1, r1, carry, carry);
        MP_ADD_CARRY(a2, r2, r2, carry, carry);
        MP_ADD_CARRY(a3, r3, r3, carry, carry);
        MP_ADD_CARRY(a4, r4, r4, carry, carry);
        MP_ADD_CARRY(a5, r5, r5, carry, carry);

        MP_CHECKOK(s_mp_pad(r, 6));
        MP_DIGIT(r, 5) = r5;
        MP_DIGIT(r, 4) = r4;
        MP_DIGIT(r, 3) = r3;
        MP_DIGIT(r, 2) = r2;
        MP_DIGIT(r, 1) = r1;
        MP_DIGIT(r, 0) = r0;
        MP_SIGN(r) = MP_ZPOS;
        MP_USED(r) = 6;

        /* Do quick 'subract' if we've gone over
         * (add the 2's complement of the curve field) */
        a5 = MP_DIGIT(&meth->irr,5);
        if (carry ||  r5 >  a5 ||
                ((r5 == a5) && mp_cmp(r,&meth->irr) != MP_LT)) {
                a4 = MP_DIGIT(&meth->irr,4);
                a3 = MP_DIGIT(&meth->irr,3);
                a2 = MP_DIGIT(&meth->irr,2);
                a1 = MP_DIGIT(&meth->irr,1);
                a0 = MP_DIGIT(&meth->irr,0);
                MP_SUB_BORROW(r0, a0, r0, 0,     carry);
                MP_SUB_BORROW(r1, a1, r1, carry, carry);
                MP_SUB_BORROW(r2, a2, r2, carry, carry);
                MP_SUB_BORROW(r3, a3, r3, carry, carry);
                MP_SUB_BORROW(r4, a4, r4, carry, carry);
                MP_SUB_BORROW(r5, a5, r5, carry, carry);
                MP_DIGIT(r, 5) = r5;
                MP_DIGIT(r, 4) = r4;
                MP_DIGIT(r, 3) = r3;
                MP_DIGIT(r, 2) = r2;
                MP_DIGIT(r, 1) = r1;
                MP_DIGIT(r, 0) = r0;
        }

        s_mp_clamp(r);

  CLEANUP:
        return res;
}

/*
 * The following subraction functions do in-line subractions based
 * on our curve size.
 *
 * ... 3 words
 */
mp_err
ec_GFp_sub_3(const mp_int *a, const mp_int *b, mp_int *r,
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
                : "=r"(r0), "=r"(r1), "=r"(r2), "=r" (borrow)
                : "r" (b0), "r" (b1), "r" (b2),
                  "0" (r0), "1" (r1), "2" (r2)
                : "%cc" );
#endif

        /* Do quick 'add' if we've gone under 0
         * (subtract the 2's complement of the curve field) */
        if (borrow) {
                b2 = MP_DIGIT(&meth->irr,2);
                b1 = MP_DIGIT(&meth->irr,1);
                b0 = MP_DIGIT(&meth->irr,0);
#ifndef MPI_AMD64_ADD
                MP_ADD_CARRY(b0, r0, r0, 0,      borrow);
                MP_ADD_CARRY(b1, r1, r1, borrow, borrow);
                MP_ADD_CARRY(b2, r2, r2, borrow, borrow);
#else
                __asm__ (
                        "addq   %3,%0           \n\t"
                        "adcq   %4,%1           \n\t"
                        "adcq   %5,%2           \n\t"
                        : "=r"(r0), "=r"(r1), "=r"(r2)
                        : "r" (b0), "r" (b1), "r" (b2),
                          "0" (r0), "1" (r1), "2" (r2)
                        : "%cc" );
#endif
        }

#ifdef MPI_AMD64_ADD
        /* compiler fakeout? */
        if ((r2 == b0) && (r1 == b0) && (r0 == b0)) {
                MP_CHECKOK(s_mp_pad(r, 4));
        }
#endif
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

/* 4 words */
mp_err
ec_GFp_sub_4(const mp_int *a, const mp_int *b, mp_int *r,
                        const GFMethod *meth)
{
        mp_err res = MP_OKAY;
        mp_digit b0 = 0, b1 = 0, b2 = 0, b3 = 0;
        mp_digit r0 = 0, r1 = 0, r2 = 0, r3 = 0;
        mp_digit borrow;

        switch(MP_USED(a)) {
        case 4:
                r3 = MP_DIGIT(a,3);
        case 3:
                r2 = MP_DIGIT(a,2);
        case 2:
                r1 = MP_DIGIT(a,1);
        case 1:
                r0 = MP_DIGIT(a,0);
        }
        switch(MP_USED(b)) {
        case 4:
                b3 = MP_DIGIT(b,3);
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
        MP_SUB_BORROW(r3, b3, r3, borrow, borrow);
#else
        __asm__ (
                "xorq   %4,%4           \n\t"
                "subq   %5,%0           \n\t"
                "sbbq   %6,%1           \n\t"
                "sbbq   %7,%2           \n\t"
                "sbbq   %8,%3           \n\t"
                "adcq   $0,%4           \n\t"
                : "=r"(r0), "=r"(r1), "=r"(r2), "=r"(r3), "=r" (borrow)
                : "r" (b0), "r" (b1), "r" (b2), "r" (b3),
                  "0" (r0), "1" (r1), "2" (r2), "3" (r3)
                : "%cc" );
#endif

        /* Do quick 'add' if we've gone under 0
         * (subtract the 2's complement of the curve field) */
        if (borrow) {
                b3 = MP_DIGIT(&meth->irr,3);
                b2 = MP_DIGIT(&meth->irr,2);
                b1 = MP_DIGIT(&meth->irr,1);
                b0 = MP_DIGIT(&meth->irr,0);
#ifndef MPI_AMD64_ADD
                MP_ADD_CARRY(b0, r0, r0, 0,      borrow);
                MP_ADD_CARRY(b1, r1, r1, borrow, borrow);
                MP_ADD_CARRY(b2, r2, r2, borrow, borrow);
                MP_ADD_CARRY(b3, r3, r3, borrow, borrow);
#else
                __asm__ (
                        "addq   %4,%0           \n\t"
                        "adcq   %5,%1           \n\t"
                        "adcq   %6,%2           \n\t"
                        "adcq   %7,%3           \n\t"
                        : "=r"(r0), "=r"(r1), "=r"(r2), "=r"(r3)
                        : "r" (b0), "r" (b1), "r" (b2), "r" (b3),
                          "0" (r0), "1" (r1), "2" (r2), "3" (r3)
                        : "%cc" );
#endif
        }
#ifdef MPI_AMD64_ADD
        /* compiler fakeout? */
        if ((r3 == b0) && (r1 == b0) && (r0 == b0)) {
                MP_CHECKOK(s_mp_pad(r, 4));
        }
#endif
        MP_CHECKOK(s_mp_pad(r, 4));
        MP_DIGIT(r, 3) = r3;
        MP_DIGIT(r, 2) = r2;
        MP_DIGIT(r, 1) = r1;
        MP_DIGIT(r, 0) = r0;
        MP_SIGN(r) = MP_ZPOS;
        MP_USED(r) = 4;
        s_mp_clamp(r);

  CLEANUP:
        return res;
}

/* 5 words */
mp_err
ec_GFp_sub_5(const mp_int *a, const mp_int *b, mp_int *r,
                        const GFMethod *meth)
{
        mp_err res = MP_OKAY;
        mp_digit b0 = 0, b1 = 0, b2 = 0, b3 = 0, b4 = 0;
        mp_digit r0 = 0, r1 = 0, r2 = 0, r3 = 0, r4 = 0;
        mp_digit borrow;

        switch(MP_USED(a)) {
        case 5:
                r4 = MP_DIGIT(a,4);
        case 4:
                r3 = MP_DIGIT(a,3);
        case 3:
                r2 = MP_DIGIT(a,2);
        case 2:
                r1 = MP_DIGIT(a,1);
        case 1:
                r0 = MP_DIGIT(a,0);
        }
        switch(MP_USED(b)) {
        case 5:
                b4 = MP_DIGIT(b,4);
        case 4:
                b3 = MP_DIGIT(b,3);
        case 3:
                b2 = MP_DIGIT(b,2);
        case 2:
                b1 = MP_DIGIT(b,1);
        case 1:
                b0 = MP_DIGIT(b,0);
        }

        MP_SUB_BORROW(r0, b0, r0, 0,     borrow);
        MP_SUB_BORROW(r1, b1, r1, borrow, borrow);
        MP_SUB_BORROW(r2, b2, r2, borrow, borrow);
        MP_SUB_BORROW(r3, b3, r3, borrow, borrow);
        MP_SUB_BORROW(r4, b4, r4, borrow, borrow);

        /* Do quick 'add' if we've gone under 0
         * (subtract the 2's complement of the curve field) */
        if (borrow) {
                b4 = MP_DIGIT(&meth->irr,4);
                b3 = MP_DIGIT(&meth->irr,3);
                b2 = MP_DIGIT(&meth->irr,2);
                b1 = MP_DIGIT(&meth->irr,1);
                b0 = MP_DIGIT(&meth->irr,0);
                MP_ADD_CARRY(b0, r0, r0, 0,      borrow);
                MP_ADD_CARRY(b1, r1, r1, borrow, borrow);
                MP_ADD_CARRY(b2, r2, r2, borrow, borrow);
                MP_ADD_CARRY(b3, r3, r3, borrow, borrow);
        }
        MP_CHECKOK(s_mp_pad(r, 5));
        MP_DIGIT(r, 4) = r4;
        MP_DIGIT(r, 3) = r3;
        MP_DIGIT(r, 2) = r2;
        MP_DIGIT(r, 1) = r1;
        MP_DIGIT(r, 0) = r0;
        MP_SIGN(r) = MP_ZPOS;
        MP_USED(r) = 5;
        s_mp_clamp(r);

  CLEANUP:
        return res;
}

/* 6 words */
mp_err
ec_GFp_sub_6(const mp_int *a, const mp_int *b, mp_int *r,
                        const GFMethod *meth)
{
        mp_err res = MP_OKAY;
        mp_digit b0 = 0, b1 = 0, b2 = 0, b3 = 0, b4 = 0, b5 = 0;
        mp_digit r0 = 0, r1 = 0, r2 = 0, r3 = 0, r4 = 0, r5 = 0;
        mp_digit borrow;

        switch(MP_USED(a)) {
        case 6:
                r5 = MP_DIGIT(a,5);
        case 5:
                r4 = MP_DIGIT(a,4);
        case 4:
                r3 = MP_DIGIT(a,3);
        case 3:
                r2 = MP_DIGIT(a,2);
        case 2:
                r1 = MP_DIGIT(a,1);
        case 1:
                r0 = MP_DIGIT(a,0);
        }
        switch(MP_USED(b)) {
        case 6:
                b5 = MP_DIGIT(b,5);
        case 5:
                b4 = MP_DIGIT(b,4);
        case 4:
                b3 = MP_DIGIT(b,3);
        case 3:
                b2 = MP_DIGIT(b,2);
        case 2:
                b1 = MP_DIGIT(b,1);
        case 1:
                b0 = MP_DIGIT(b,0);
        }

        MP_SUB_BORROW(r0, b0, r0, 0,     borrow);
        MP_SUB_BORROW(r1, b1, r1, borrow, borrow);
        MP_SUB_BORROW(r2, b2, r2, borrow, borrow);
        MP_SUB_BORROW(r3, b3, r3, borrow, borrow);
        MP_SUB_BORROW(r4, b4, r4, borrow, borrow);
        MP_SUB_BORROW(r5, b5, r5, borrow, borrow);

        /* Do quick 'add' if we've gone under 0
         * (subtract the 2's complement of the curve field) */
        if (borrow) {
                b5 = MP_DIGIT(&meth->irr,5);
                b4 = MP_DIGIT(&meth->irr,4);
                b3 = MP_DIGIT(&meth->irr,3);
                b2 = MP_DIGIT(&meth->irr,2);
                b1 = MP_DIGIT(&meth->irr,1);
                b0 = MP_DIGIT(&meth->irr,0);
                MP_ADD_CARRY(b0, r0, r0, 0,      borrow);
                MP_ADD_CARRY(b1, r1, r1, borrow, borrow);
                MP_ADD_CARRY(b2, r2, r2, borrow, borrow);
                MP_ADD_CARRY(b3, r3, r3, borrow, borrow);
                MP_ADD_CARRY(b4, r4, r4, borrow, borrow);
        }

        MP_CHECKOK(s_mp_pad(r, 6));
        MP_DIGIT(r, 5) = r5;
        MP_DIGIT(r, 4) = r4;
        MP_DIGIT(r, 3) = r3;
        MP_DIGIT(r, 2) = r2;
        MP_DIGIT(r, 1) = r1;
        MP_DIGIT(r, 0) = r0;
        MP_SIGN(r) = MP_ZPOS;
        MP_USED(r) = 6;
        s_mp_clamp(r);

  CLEANUP:
        return res;
}


/* Reduces an integer to a field element. */
mp_err
ec_GFp_mod(const mp_int *a, mp_int *r, const GFMethod *meth)
{
        return mp_mod(a, &meth->irr, r);
}

/* Multiplies two field elements. */
mp_err
ec_GFp_mul(const mp_int *a, const mp_int *b, mp_int *r,
                   const GFMethod *meth)
{
        return mp_mulmod(a, b, &meth->irr, r);
}

/* Squares a field element. */
mp_err
ec_GFp_sqr(const mp_int *a, mp_int *r, const GFMethod *meth)
{
        return mp_sqrmod(a, &meth->irr, r);
}

/* Divides two field elements. If a is NULL, then returns the inverse of
 * b. */
mp_err
ec_GFp_div(const mp_int *a, const mp_int *b, mp_int *r,
                   const GFMethod *meth)
{
        mp_err res = MP_OKAY;
        mp_int t;

        /* If a is NULL, then return the inverse of b, otherwise return a/b. */
        if (a == NULL) {
                return mp_invmod(b, &meth->irr, r);
        } else {
                /* MPI doesn't support divmod, so we implement it using invmod and
                 * mulmod. */
                MP_CHECKOK(mp_init(&t, FLAG(b)));
                MP_CHECKOK(mp_invmod(b, &meth->irr, &t));
                MP_CHECKOK(mp_mulmod(a, &t, &meth->irr, r));
          CLEANUP:
                mp_clear(&t);
                return res;
        }
}

/* Wrapper functions for generic binary polynomial field arithmetic. */

/* Adds two field elements. */
mp_err
ec_GF2m_add(const mp_int *a, const mp_int *b, mp_int *r,
                        const GFMethod *meth)
{
        return mp_badd(a, b, r);
}

/* Negates a field element. Note that for binary polynomial fields, the
 * negation of a field element is the field element itself. */
mp_err
ec_GF2m_neg(const mp_int *a, mp_int *r, const GFMethod *meth)
{
        if (a == r) {
                return MP_OKAY;
        } else {
                return mp_copy(a, r);
        }
}

/* Reduces a binary polynomial to a field element. */
mp_err
ec_GF2m_mod(const mp_int *a, mp_int *r, const GFMethod *meth)
{
        return mp_bmod(a, meth->irr_arr, r);
}

/* Multiplies two field elements. */
mp_err
ec_GF2m_mul(const mp_int *a, const mp_int *b, mp_int *r,
                        const GFMethod *meth)
{
        return mp_bmulmod(a, b, meth->irr_arr, r);
}

/* Squares a field element. */
mp_err
ec_GF2m_sqr(const mp_int *a, mp_int *r, const GFMethod *meth)
{
        return mp_bsqrmod(a, meth->irr_arr, r);
}

/* Divides two field elements. If a is NULL, then returns the inverse of
 * b. */
mp_err
ec_GF2m_div(const mp_int *a, const mp_int *b, mp_int *r,
                        const GFMethod *meth)
{
        mp_err res = MP_OKAY;
        mp_int t;

        /* If a is NULL, then return the inverse of b, otherwise return a/b. */
        if (a == NULL) {
                /* The GF(2^m) portion of MPI doesn't support invmod, so we
                 * compute 1/b. */
                MP_CHECKOK(mp_init(&t, FLAG(b)));
                MP_CHECKOK(mp_set_int(&t, 1));
                MP_CHECKOK(mp_bdivmod(&t, b, &meth->irr, meth->irr_arr, r));
          CLEANUP:
                mp_clear(&t);
                return res;
        } else {
                return mp_bdivmod(a, b, &meth->irr, meth->irr_arr, r);
        }
}
