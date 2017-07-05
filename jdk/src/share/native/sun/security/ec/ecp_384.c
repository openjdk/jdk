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
 * The Original Code is the elliptic curve math library for prime field curves.
 *
 * The Initial Developer of the Original Code is
 * Sun Microsystems, Inc.
 * Portions created by the Initial Developer are Copyright (C) 2003
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Douglas Stebila <douglas@stebila.ca>
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
 * Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
 * Use is subject to license terms.
 */

#pragma ident   "%Z%%M% %I%     %E% SMI"

#include "ecp.h"
#include "mpi.h"
#include "mplogic.h"
#include "mpi-priv.h"
#ifndef _KERNEL
#include <stdlib.h>
#endif

/* Fast modular reduction for p384 = 2^384 - 2^128 - 2^96 + 2^32 - 1.  a can be r.
 * Uses algorithm 2.30 from Hankerson, Menezes, Vanstone. Guide to
 * Elliptic Curve Cryptography. */
mp_err
ec_GFp_nistp384_mod(const mp_int *a, mp_int *r, const GFMethod *meth)
{
        mp_err res = MP_OKAY;
        int a_bits = mpl_significant_bits(a);
        int i;

        /* m1, m2 are statically-allocated mp_int of exactly the size we need */
        mp_int m[10];

#ifdef ECL_THIRTY_TWO_BIT
        mp_digit s[10][12];
        for (i = 0; i < 10; i++) {
                MP_SIGN(&m[i]) = MP_ZPOS;
                MP_ALLOC(&m[i]) = 12;
                MP_USED(&m[i]) = 12;
                MP_DIGITS(&m[i]) = s[i];
        }
#else
        mp_digit s[10][6];
        for (i = 0; i < 10; i++) {
                MP_SIGN(&m[i]) = MP_ZPOS;
                MP_ALLOC(&m[i]) = 6;
                MP_USED(&m[i]) = 6;
                MP_DIGITS(&m[i]) = s[i];
        }
#endif

#ifdef ECL_THIRTY_TWO_BIT
        /* for polynomials larger than twice the field size or polynomials
         * not using all words, use regular reduction */
        if ((a_bits > 768) || (a_bits <= 736)) {
                MP_CHECKOK(mp_mod(a, &meth->irr, r));
        } else {
                for (i = 0; i < 12; i++) {
                        s[0][i] = MP_DIGIT(a, i);
                }
                s[1][0] = 0;
                s[1][1] = 0;
                s[1][2] = 0;
                s[1][3] = 0;
                s[1][4] = MP_DIGIT(a, 21);
                s[1][5] = MP_DIGIT(a, 22);
                s[1][6] = MP_DIGIT(a, 23);
                s[1][7] = 0;
                s[1][8] = 0;
                s[1][9] = 0;
                s[1][10] = 0;
                s[1][11] = 0;
                for (i = 0; i < 12; i++) {
                        s[2][i] = MP_DIGIT(a, i+12);
                }
                s[3][0] = MP_DIGIT(a, 21);
                s[3][1] = MP_DIGIT(a, 22);
                s[3][2] = MP_DIGIT(a, 23);
                for (i = 3; i < 12; i++) {
                        s[3][i] = MP_DIGIT(a, i+9);
                }
                s[4][0] = 0;
                s[4][1] = MP_DIGIT(a, 23);
                s[4][2] = 0;
                s[4][3] = MP_DIGIT(a, 20);
                for (i = 4; i < 12; i++) {
                        s[4][i] = MP_DIGIT(a, i+8);
                }
                s[5][0] = 0;
                s[5][1] = 0;
                s[5][2] = 0;
                s[5][3] = 0;
                s[5][4] = MP_DIGIT(a, 20);
                s[5][5] = MP_DIGIT(a, 21);
                s[5][6] = MP_DIGIT(a, 22);
                s[5][7] = MP_DIGIT(a, 23);
                s[5][8] = 0;
                s[5][9] = 0;
                s[5][10] = 0;
                s[5][11] = 0;
                s[6][0] = MP_DIGIT(a, 20);
                s[6][1] = 0;
                s[6][2] = 0;
                s[6][3] = MP_DIGIT(a, 21);
                s[6][4] = MP_DIGIT(a, 22);
                s[6][5] = MP_DIGIT(a, 23);
                s[6][6] = 0;
                s[6][7] = 0;
                s[6][8] = 0;
                s[6][9] = 0;
                s[6][10] = 0;
                s[6][11] = 0;
                s[7][0] = MP_DIGIT(a, 23);
                for (i = 1; i < 12; i++) {
                        s[7][i] = MP_DIGIT(a, i+11);
                }
                s[8][0] = 0;
                s[8][1] = MP_DIGIT(a, 20);
                s[8][2] = MP_DIGIT(a, 21);
                s[8][3] = MP_DIGIT(a, 22);
                s[8][4] = MP_DIGIT(a, 23);
                s[8][5] = 0;
                s[8][6] = 0;
                s[8][7] = 0;
                s[8][8] = 0;
                s[8][9] = 0;
                s[8][10] = 0;
                s[8][11] = 0;
                s[9][0] = 0;
                s[9][1] = 0;
                s[9][2] = 0;
                s[9][3] = MP_DIGIT(a, 23);
                s[9][4] = MP_DIGIT(a, 23);
                s[9][5] = 0;
                s[9][6] = 0;
                s[9][7] = 0;
                s[9][8] = 0;
                s[9][9] = 0;
                s[9][10] = 0;
                s[9][11] = 0;

                MP_CHECKOK(mp_add(&m[0], &m[1], r));
                MP_CHECKOK(mp_add(r, &m[1], r));
                MP_CHECKOK(mp_add(r, &m[2], r));
                MP_CHECKOK(mp_add(r, &m[3], r));
                MP_CHECKOK(mp_add(r, &m[4], r));
                MP_CHECKOK(mp_add(r, &m[5], r));
                MP_CHECKOK(mp_add(r, &m[6], r));
                MP_CHECKOK(mp_sub(r, &m[7], r));
                MP_CHECKOK(mp_sub(r, &m[8], r));
                MP_CHECKOK(mp_submod(r, &m[9], &meth->irr, r));
                s_mp_clamp(r);
        }
#else
        /* for polynomials larger than twice the field size or polynomials
         * not using all words, use regular reduction */
        if ((a_bits > 768) || (a_bits <= 736)) {
                MP_CHECKOK(mp_mod(a, &meth->irr, r));
        } else {
                for (i = 0; i < 6; i++) {
                        s[0][i] = MP_DIGIT(a, i);
                }
                s[1][0] = 0;
                s[1][1] = 0;
                s[1][2] = (MP_DIGIT(a, 10) >> 32) | (MP_DIGIT(a, 11) << 32);
                s[1][3] = MP_DIGIT(a, 11) >> 32;
                s[1][4] = 0;
                s[1][5] = 0;
                for (i = 0; i < 6; i++) {
                        s[2][i] = MP_DIGIT(a, i+6);
                }
                s[3][0] = (MP_DIGIT(a, 10) >> 32) | (MP_DIGIT(a, 11) << 32);
                s[3][1] = (MP_DIGIT(a, 11) >> 32) | (MP_DIGIT(a, 6) << 32);
                for (i = 2; i < 6; i++) {
                        s[3][i] = (MP_DIGIT(a, i+4) >> 32) | (MP_DIGIT(a, i+5) << 32);
                }
                s[4][0] = (MP_DIGIT(a, 11) >> 32) << 32;
                s[4][1] = MP_DIGIT(a, 10) << 32;
                for (i = 2; i < 6; i++) {
                        s[4][i] = MP_DIGIT(a, i+4);
                }
                s[5][0] = 0;
                s[5][1] = 0;
                s[5][2] = MP_DIGIT(a, 10);
                s[5][3] = MP_DIGIT(a, 11);
                s[5][4] = 0;
                s[5][5] = 0;
                s[6][0] = (MP_DIGIT(a, 10) << 32) >> 32;
                s[6][1] = (MP_DIGIT(a, 10) >> 32) << 32;
                s[6][2] = MP_DIGIT(a, 11);
                s[6][3] = 0;
                s[6][4] = 0;
                s[6][5] = 0;
                s[7][0] = (MP_DIGIT(a, 11) >> 32) | (MP_DIGIT(a, 6) << 32);
                for (i = 1; i < 6; i++) {
                        s[7][i] = (MP_DIGIT(a, i+5) >> 32) | (MP_DIGIT(a, i+6) << 32);
                }
                s[8][0] = MP_DIGIT(a, 10) << 32;
                s[8][1] = (MP_DIGIT(a, 10) >> 32) | (MP_DIGIT(a, 11) << 32);
                s[8][2] = MP_DIGIT(a, 11) >> 32;
                s[8][3] = 0;
                s[8][4] = 0;
                s[8][5] = 0;
                s[9][0] = 0;
                s[9][1] = (MP_DIGIT(a, 11) >> 32) << 32;
                s[9][2] = MP_DIGIT(a, 11) >> 32;
                s[9][3] = 0;
                s[9][4] = 0;
                s[9][5] = 0;

                MP_CHECKOK(mp_add(&m[0], &m[1], r));
                MP_CHECKOK(mp_add(r, &m[1], r));
                MP_CHECKOK(mp_add(r, &m[2], r));
                MP_CHECKOK(mp_add(r, &m[3], r));
                MP_CHECKOK(mp_add(r, &m[4], r));
                MP_CHECKOK(mp_add(r, &m[5], r));
                MP_CHECKOK(mp_add(r, &m[6], r));
                MP_CHECKOK(mp_sub(r, &m[7], r));
                MP_CHECKOK(mp_sub(r, &m[8], r));
                MP_CHECKOK(mp_submod(r, &m[9], &meth->irr, r));
                s_mp_clamp(r);
        }
#endif

  CLEANUP:
        return res;
}

/* Compute the square of polynomial a, reduce modulo p384. Store the
 * result in r.  r could be a.  Uses optimized modular reduction for p384.
 */
mp_err
ec_GFp_nistp384_sqr(const mp_int *a, mp_int *r, const GFMethod *meth)
{
        mp_err res = MP_OKAY;

        MP_CHECKOK(mp_sqr(a, r));
        MP_CHECKOK(ec_GFp_nistp384_mod(r, r, meth));
  CLEANUP:
        return res;
}

/* Compute the product of two polynomials a and b, reduce modulo p384.
 * Store the result in r.  r could be a or b; a could be b.  Uses
 * optimized modular reduction for p384. */
mp_err
ec_GFp_nistp384_mul(const mp_int *a, const mp_int *b, mp_int *r,
                                        const GFMethod *meth)
{
        mp_err res = MP_OKAY;

        MP_CHECKOK(mp_mul(a, b, r));
        MP_CHECKOK(ec_GFp_nistp384_mod(r, r, meth));
  CLEANUP:
        return res;
}

/* Wire in fast field arithmetic and precomputation of base point for
 * named curves. */
mp_err
ec_group_set_gfp384(ECGroup *group, ECCurveName name)
{
        if (name == ECCurve_NIST_P384) {
                group->meth->field_mod = &ec_GFp_nistp384_mod;
                group->meth->field_mul = &ec_GFp_nistp384_mul;
                group->meth->field_sqr = &ec_GFp_nistp384_sqr;
        }
        return MP_OKAY;
}
