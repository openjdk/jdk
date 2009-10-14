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
 * The Original Code is the elliptic curve math library for binary polynomial field curves.
 *
 * The Initial Developer of the Original Code is
 * Sun Microsystems, Inc.
 * Portions created by the Initial Developer are Copyright (C) 2003
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Sheueling Chang-Shantz <sheueling.chang@sun.com>,
 *   Stephen Fung <fungstep@hotmail.com>, and
 *   Douglas Stebila <douglas@stebila.ca>, Sun Microsystems Laboratories.
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

#include "ec2.h"
#include "mp_gf2m.h"
#include "mp_gf2m-priv.h"
#include "mpi.h"
#include "mpi-priv.h"
#ifndef _KERNEL
#include <stdlib.h>
#endif

/* Fast reduction for polynomials over a 193-bit curve. Assumes reduction
 * polynomial with terms {193, 15, 0}. */
mp_err
ec_GF2m_193_mod(const mp_int *a, mp_int *r, const GFMethod *meth)
{
        mp_err res = MP_OKAY;
        mp_digit *u, z;

        if (a != r) {
                MP_CHECKOK(mp_copy(a, r));
        }
#ifdef ECL_SIXTY_FOUR_BIT
        if (MP_USED(r) < 7) {
                MP_CHECKOK(s_mp_pad(r, 7));
        }
        u = MP_DIGITS(r);
        MP_USED(r) = 7;

        /* u[6] only has 2 significant bits */
        z = u[6];
        u[3] ^= (z << 14) ^ (z >> 1);
        u[2] ^= (z << 63);
        z = u[5];
        u[3] ^= (z >> 50);
        u[2] ^= (z << 14) ^ (z >> 1);
        u[1] ^= (z << 63);
        z = u[4];
        u[2] ^= (z >> 50);
        u[1] ^= (z << 14) ^ (z >> 1);
        u[0] ^= (z << 63);
        z = u[3] >> 1;                          /* z only has 63 significant bits */
        u[1] ^= (z >> 49);
        u[0] ^= (z << 15) ^ z;
        /* clear bits above 193 */
        u[6] = u[5] = u[4] = 0;
        u[3] ^= z << 1;
#else
        if (MP_USED(r) < 13) {
                MP_CHECKOK(s_mp_pad(r, 13));
        }
        u = MP_DIGITS(r);
        MP_USED(r) = 13;

        /* u[12] only has 2 significant bits */
        z = u[12];
        u[6] ^= (z << 14) ^ (z >> 1);
        u[5] ^= (z << 31);
        z = u[11];
        u[6] ^= (z >> 18);
        u[5] ^= (z << 14) ^ (z >> 1);
        u[4] ^= (z << 31);
        z = u[10];
        u[5] ^= (z >> 18);
        u[4] ^= (z << 14) ^ (z >> 1);
        u[3] ^= (z << 31);
        z = u[9];
        u[4] ^= (z >> 18);
        u[3] ^= (z << 14) ^ (z >> 1);
        u[2] ^= (z << 31);
        z = u[8];
        u[3] ^= (z >> 18);
        u[2] ^= (z << 14) ^ (z >> 1);
        u[1] ^= (z << 31);
        z = u[7];
        u[2] ^= (z >> 18);
        u[1] ^= (z << 14) ^ (z >> 1);
        u[0] ^= (z << 31);
        z = u[6] >> 1;                          /* z only has 31 significant bits */
        u[1] ^= (z >> 17);
        u[0] ^= (z << 15) ^ z;
        /* clear bits above 193 */
        u[12] = u[11] = u[10] = u[9] = u[8] = u[7] = 0;
        u[6] ^= z << 1;
#endif
        s_mp_clamp(r);

  CLEANUP:
        return res;
}

/* Fast squaring for polynomials over a 193-bit curve. Assumes reduction
 * polynomial with terms {193, 15, 0}. */
mp_err
ec_GF2m_193_sqr(const mp_int *a, mp_int *r, const GFMethod *meth)
{
        mp_err res = MP_OKAY;
        mp_digit *u, *v;

        v = MP_DIGITS(a);

#ifdef ECL_SIXTY_FOUR_BIT
        if (MP_USED(a) < 4) {
                return mp_bsqrmod(a, meth->irr_arr, r);
        }
        if (MP_USED(r) < 7) {
                MP_CHECKOK(s_mp_pad(r, 7));
        }
        MP_USED(r) = 7;
#else
        if (MP_USED(a) < 7) {
                return mp_bsqrmod(a, meth->irr_arr, r);
        }
        if (MP_USED(r) < 13) {
                MP_CHECKOK(s_mp_pad(r, 13));
        }
        MP_USED(r) = 13;
#endif
        u = MP_DIGITS(r);

#ifdef ECL_THIRTY_TWO_BIT
        u[12] = gf2m_SQR0(v[6]);
        u[11] = gf2m_SQR1(v[5]);
        u[10] = gf2m_SQR0(v[5]);
        u[9] = gf2m_SQR1(v[4]);
        u[8] = gf2m_SQR0(v[4]);
        u[7] = gf2m_SQR1(v[3]);
#endif
        u[6] = gf2m_SQR0(v[3]);
        u[5] = gf2m_SQR1(v[2]);
        u[4] = gf2m_SQR0(v[2]);
        u[3] = gf2m_SQR1(v[1]);
        u[2] = gf2m_SQR0(v[1]);
        u[1] = gf2m_SQR1(v[0]);
        u[0] = gf2m_SQR0(v[0]);
        return ec_GF2m_193_mod(r, r, meth);

  CLEANUP:
        return res;
}

/* Fast multiplication for polynomials over a 193-bit curve. Assumes
 * reduction polynomial with terms {193, 15, 0}. */
mp_err
ec_GF2m_193_mul(const mp_int *a, const mp_int *b, mp_int *r,
                                const GFMethod *meth)
{
        mp_err res = MP_OKAY;
        mp_digit a3 = 0, a2 = 0, a1 = 0, a0, b3 = 0, b2 = 0, b1 = 0, b0;

#ifdef ECL_THIRTY_TWO_BIT
        mp_digit a6 = 0, a5 = 0, a4 = 0, b6 = 0, b5 = 0, b4 = 0;
        mp_digit rm[8];
#endif

        if (a == b) {
                return ec_GF2m_193_sqr(a, r, meth);
        } else {
                switch (MP_USED(a)) {
#ifdef ECL_THIRTY_TWO_BIT
                case 7:
                        a6 = MP_DIGIT(a, 6);
                case 6:
                        a5 = MP_DIGIT(a, 5);
                case 5:
                        a4 = MP_DIGIT(a, 4);
#endif
                case 4:
                        a3 = MP_DIGIT(a, 3);
                case 3:
                        a2 = MP_DIGIT(a, 2);
                case 2:
                        a1 = MP_DIGIT(a, 1);
                default:
                        a0 = MP_DIGIT(a, 0);
                }
                switch (MP_USED(b)) {
#ifdef ECL_THIRTY_TWO_BIT
                case 7:
                        b6 = MP_DIGIT(b, 6);
                case 6:
                        b5 = MP_DIGIT(b, 5);
                case 5:
                        b4 = MP_DIGIT(b, 4);
#endif
                case 4:
                        b3 = MP_DIGIT(b, 3);
                case 3:
                        b2 = MP_DIGIT(b, 2);
                case 2:
                        b1 = MP_DIGIT(b, 1);
                default:
                        b0 = MP_DIGIT(b, 0);
                }
#ifdef ECL_SIXTY_FOUR_BIT
                MP_CHECKOK(s_mp_pad(r, 8));
                s_bmul_4x4(MP_DIGITS(r), a3, a2, a1, a0, b3, b2, b1, b0);
                MP_USED(r) = 8;
                s_mp_clamp(r);
#else
                MP_CHECKOK(s_mp_pad(r, 14));
                s_bmul_3x3(MP_DIGITS(r) + 8, a6, a5, a4, b6, b5, b4);
                s_bmul_4x4(MP_DIGITS(r), a3, a2, a1, a0, b3, b2, b1, b0);
                s_bmul_4x4(rm, a3, a6 ^ a2, a5 ^ a1, a4 ^ a0, b3, b6 ^ b2, b5 ^ b1,
                                   b4 ^ b0);
                rm[7] ^= MP_DIGIT(r, 7);
                rm[6] ^= MP_DIGIT(r, 6);
                rm[5] ^= MP_DIGIT(r, 5) ^ MP_DIGIT(r, 13);
                rm[4] ^= MP_DIGIT(r, 4) ^ MP_DIGIT(r, 12);
                rm[3] ^= MP_DIGIT(r, 3) ^ MP_DIGIT(r, 11);
                rm[2] ^= MP_DIGIT(r, 2) ^ MP_DIGIT(r, 10);
                rm[1] ^= MP_DIGIT(r, 1) ^ MP_DIGIT(r, 9);
                rm[0] ^= MP_DIGIT(r, 0) ^ MP_DIGIT(r, 8);
                MP_DIGIT(r, 11) ^= rm[7];
                MP_DIGIT(r, 10) ^= rm[6];
                MP_DIGIT(r, 9) ^= rm[5];
                MP_DIGIT(r, 8) ^= rm[4];
                MP_DIGIT(r, 7) ^= rm[3];
                MP_DIGIT(r, 6) ^= rm[2];
                MP_DIGIT(r, 5) ^= rm[1];
                MP_DIGIT(r, 4) ^= rm[0];
                MP_USED(r) = 14;
                s_mp_clamp(r);
#endif
                return ec_GF2m_193_mod(r, r, meth);
        }

  CLEANUP:
        return res;
}

/* Wire in fast field arithmetic for 193-bit curves. */
mp_err
ec_group_set_gf2m193(ECGroup *group, ECCurveName name)
{
        group->meth->field_mod = &ec_GF2m_193_mod;
        group->meth->field_mul = &ec_GF2m_193_mul;
        group->meth->field_sqr = &ec_GF2m_193_sqr;
        return MP_OKAY;
}
