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
 * The Original Code is the elliptic curve math library.
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

/* Uses Montgomery reduction for field arithmetic.  See mpi/mpmontg.c for
 * code implementation. */

#include "mpi.h"
#include "mplogic.h"
#include "mpi-priv.h"
#include "ecl-priv.h"
#include "ecp.h"
#ifndef _KERNEL
#include <stdlib.h>
#include <stdio.h>
#endif

/* Construct a generic GFMethod for arithmetic over prime fields with
 * irreducible irr. */
GFMethod *
GFMethod_consGFp_mont(const mp_int *irr)
{
        mp_err res = MP_OKAY;
        int i;
        GFMethod *meth = NULL;
        mp_mont_modulus *mmm;

        meth = GFMethod_consGFp(irr);
        if (meth == NULL)
                return NULL;

#ifdef _KERNEL
        mmm = (mp_mont_modulus *) kmem_alloc(sizeof(mp_mont_modulus),
            FLAG(irr));
#else
        mmm = (mp_mont_modulus *) malloc(sizeof(mp_mont_modulus));
#endif
        if (mmm == NULL) {
                res = MP_MEM;
                goto CLEANUP;
        }

        meth->field_mul = &ec_GFp_mul_mont;
        meth->field_sqr = &ec_GFp_sqr_mont;
        meth->field_div = &ec_GFp_div_mont;
        meth->field_enc = &ec_GFp_enc_mont;
        meth->field_dec = &ec_GFp_dec_mont;
        meth->extra1 = mmm;
        meth->extra2 = NULL;
        meth->extra_free = &ec_GFp_extra_free_mont;

        mmm->N = meth->irr;
        i = mpl_significant_bits(&meth->irr);
        i += MP_DIGIT_BIT - 1;
        mmm->b = i - i % MP_DIGIT_BIT;
        mmm->n0prime = 0 - s_mp_invmod_radix(MP_DIGIT(&meth->irr, 0));

  CLEANUP:
        if (res != MP_OKAY) {
                GFMethod_free(meth);
                return NULL;
        }
        return meth;
}

/* Wrapper functions for generic prime field arithmetic. */

/* Field multiplication using Montgomery reduction. */
mp_err
ec_GFp_mul_mont(const mp_int *a, const mp_int *b, mp_int *r,
                                const GFMethod *meth)
{
        mp_err res = MP_OKAY;

#ifdef MP_MONT_USE_MP_MUL
        /* if MP_MONT_USE_MP_MUL is defined, then the function s_mp_mul_mont
         * is not implemented and we have to use mp_mul and s_mp_redc directly
         */
        MP_CHECKOK(mp_mul(a, b, r));
        MP_CHECKOK(s_mp_redc(r, (mp_mont_modulus *) meth->extra1));
#else
        mp_int s;

        MP_DIGITS(&s) = 0;
        /* s_mp_mul_mont doesn't allow source and destination to be the same */
        if ((a == r) || (b == r)) {
                MP_CHECKOK(mp_init(&s, FLAG(a)));
                MP_CHECKOK(s_mp_mul_mont
                                   (a, b, &s, (mp_mont_modulus *) meth->extra1));
                MP_CHECKOK(mp_copy(&s, r));
                mp_clear(&s);
        } else {
                return s_mp_mul_mont(a, b, r, (mp_mont_modulus *) meth->extra1);
        }
#endif
  CLEANUP:
        return res;
}

/* Field squaring using Montgomery reduction. */
mp_err
ec_GFp_sqr_mont(const mp_int *a, mp_int *r, const GFMethod *meth)
{
        return ec_GFp_mul_mont(a, a, r, meth);
}

/* Field division using Montgomery reduction. */
mp_err
ec_GFp_div_mont(const mp_int *a, const mp_int *b, mp_int *r,
                                const GFMethod *meth)
{
        mp_err res = MP_OKAY;

        /* if A=aZ represents a encoded in montgomery coordinates with Z and #
         * and \ respectively represent multiplication and division in
         * montgomery coordinates, then A\B = (a/b)Z = (A/B)Z and Binv =
         * (1/b)Z = (1/B)(Z^2) where B # Binv = Z */
        MP_CHECKOK(ec_GFp_div(a, b, r, meth));
        MP_CHECKOK(ec_GFp_enc_mont(r, r, meth));
        if (a == NULL) {
                MP_CHECKOK(ec_GFp_enc_mont(r, r, meth));
        }
  CLEANUP:
        return res;
}

/* Encode a field element in Montgomery form. See s_mp_to_mont in
 * mpi/mpmontg.c */
mp_err
ec_GFp_enc_mont(const mp_int *a, mp_int *r, const GFMethod *meth)
{
        mp_mont_modulus *mmm;
        mp_err res = MP_OKAY;

        mmm = (mp_mont_modulus *) meth->extra1;
        MP_CHECKOK(mpl_lsh(a, r, mmm->b));
        MP_CHECKOK(mp_mod(r, &mmm->N, r));
  CLEANUP:
        return res;
}

/* Decode a field element from Montgomery form. */
mp_err
ec_GFp_dec_mont(const mp_int *a, mp_int *r, const GFMethod *meth)
{
        mp_err res = MP_OKAY;

        if (a != r) {
                MP_CHECKOK(mp_copy(a, r));
        }
        MP_CHECKOK(s_mp_redc(r, (mp_mont_modulus *) meth->extra1));
  CLEANUP:
        return res;
}

/* Free the memory allocated to the extra fields of Montgomery GFMethod
 * object. */
void
ec_GFp_extra_free_mont(GFMethod *meth)
{
        if (meth->extra1 != NULL) {
#ifdef _KERNEL
                kmem_free(meth->extra1, sizeof(mp_mont_modulus));
#else
                free(meth->extra1);
#endif
                meth->extra1 = NULL;
        }
}
