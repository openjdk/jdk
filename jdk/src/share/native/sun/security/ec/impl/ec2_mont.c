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
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
 * Use is subject to license terms.
 */

#include "ec2.h"
#include "mplogic.h"
#include "mp_gf2m.h"
#ifndef _KERNEL
#include <stdlib.h>
#endif

/* Compute the x-coordinate x/z for the point 2*(x/z) in Montgomery
 * projective coordinates. Uses algorithm Mdouble in appendix of Lopez, J.
 * and Dahab, R.  "Fast multiplication on elliptic curves over GF(2^m)
 * without precomputation". modified to not require precomputation of
 * c=b^{2^{m-1}}. */
static mp_err
gf2m_Mdouble(mp_int *x, mp_int *z, const ECGroup *group, int kmflag)
{
        mp_err res = MP_OKAY;
        mp_int t1;

        MP_DIGITS(&t1) = 0;
        MP_CHECKOK(mp_init(&t1, kmflag));

        MP_CHECKOK(group->meth->field_sqr(x, x, group->meth));
        MP_CHECKOK(group->meth->field_sqr(z, &t1, group->meth));
        MP_CHECKOK(group->meth->field_mul(x, &t1, z, group->meth));
        MP_CHECKOK(group->meth->field_sqr(x, x, group->meth));
        MP_CHECKOK(group->meth->field_sqr(&t1, &t1, group->meth));
        MP_CHECKOK(group->meth->
                           field_mul(&group->curveb, &t1, &t1, group->meth));
        MP_CHECKOK(group->meth->field_add(x, &t1, x, group->meth));

  CLEANUP:
        mp_clear(&t1);
        return res;
}

/* Compute the x-coordinate x1/z1 for the point (x1/z1)+(x2/x2) in
 * Montgomery projective coordinates. Uses algorithm Madd in appendix of
 * Lopex, J. and Dahab, R.  "Fast multiplication on elliptic curves over
 * GF(2^m) without precomputation". */
static mp_err
gf2m_Madd(const mp_int *x, mp_int *x1, mp_int *z1, mp_int *x2, mp_int *z2,
                  const ECGroup *group, int kmflag)
{
        mp_err res = MP_OKAY;
        mp_int t1, t2;

        MP_DIGITS(&t1) = 0;
        MP_DIGITS(&t2) = 0;
        MP_CHECKOK(mp_init(&t1, kmflag));
        MP_CHECKOK(mp_init(&t2, kmflag));

        MP_CHECKOK(mp_copy(x, &t1));
        MP_CHECKOK(group->meth->field_mul(x1, z2, x1, group->meth));
        MP_CHECKOK(group->meth->field_mul(z1, x2, z1, group->meth));
        MP_CHECKOK(group->meth->field_mul(x1, z1, &t2, group->meth));
        MP_CHECKOK(group->meth->field_add(z1, x1, z1, group->meth));
        MP_CHECKOK(group->meth->field_sqr(z1, z1, group->meth));
        MP_CHECKOK(group->meth->field_mul(z1, &t1, x1, group->meth));
        MP_CHECKOK(group->meth->field_add(x1, &t2, x1, group->meth));

  CLEANUP:
        mp_clear(&t1);
        mp_clear(&t2);
        return res;
}

/* Compute the x, y affine coordinates from the point (x1, z1) (x2, z2)
 * using Montgomery point multiplication algorithm Mxy() in appendix of
 * Lopex, J. and Dahab, R.  "Fast multiplication on elliptic curves over
 * GF(2^m) without precomputation". Returns: 0 on error 1 if return value
 * should be the point at infinity 2 otherwise */
static int
gf2m_Mxy(const mp_int *x, const mp_int *y, mp_int *x1, mp_int *z1,
                 mp_int *x2, mp_int *z2, const ECGroup *group)
{
        mp_err res = MP_OKAY;
        int ret = 0;
        mp_int t3, t4, t5;

        MP_DIGITS(&t3) = 0;
        MP_DIGITS(&t4) = 0;
        MP_DIGITS(&t5) = 0;
        MP_CHECKOK(mp_init(&t3, FLAG(x2)));
        MP_CHECKOK(mp_init(&t4, FLAG(x2)));
        MP_CHECKOK(mp_init(&t5, FLAG(x2)));

        if (mp_cmp_z(z1) == 0) {
                mp_zero(x2);
                mp_zero(z2);
                ret = 1;
                goto CLEANUP;
        }

        if (mp_cmp_z(z2) == 0) {
                MP_CHECKOK(mp_copy(x, x2));
                MP_CHECKOK(group->meth->field_add(x, y, z2, group->meth));
                ret = 2;
                goto CLEANUP;
        }

        MP_CHECKOK(mp_set_int(&t5, 1));
        if (group->meth->field_enc) {
                MP_CHECKOK(group->meth->field_enc(&t5, &t5, group->meth));
        }

        MP_CHECKOK(group->meth->field_mul(z1, z2, &t3, group->meth));

        MP_CHECKOK(group->meth->field_mul(z1, x, z1, group->meth));
        MP_CHECKOK(group->meth->field_add(z1, x1, z1, group->meth));
        MP_CHECKOK(group->meth->field_mul(z2, x, z2, group->meth));
        MP_CHECKOK(group->meth->field_mul(z2, x1, x1, group->meth));
        MP_CHECKOK(group->meth->field_add(z2, x2, z2, group->meth));

        MP_CHECKOK(group->meth->field_mul(z2, z1, z2, group->meth));
        MP_CHECKOK(group->meth->field_sqr(x, &t4, group->meth));
        MP_CHECKOK(group->meth->field_add(&t4, y, &t4, group->meth));
        MP_CHECKOK(group->meth->field_mul(&t4, &t3, &t4, group->meth));
        MP_CHECKOK(group->meth->field_add(&t4, z2, &t4, group->meth));

        MP_CHECKOK(group->meth->field_mul(&t3, x, &t3, group->meth));
        MP_CHECKOK(group->meth->field_div(&t5, &t3, &t3, group->meth));
        MP_CHECKOK(group->meth->field_mul(&t3, &t4, &t4, group->meth));
        MP_CHECKOK(group->meth->field_mul(x1, &t3, x2, group->meth));
        MP_CHECKOK(group->meth->field_add(x2, x, z2, group->meth));

        MP_CHECKOK(group->meth->field_mul(z2, &t4, z2, group->meth));
        MP_CHECKOK(group->meth->field_add(z2, y, z2, group->meth));

        ret = 2;

  CLEANUP:
        mp_clear(&t3);
        mp_clear(&t4);
        mp_clear(&t5);
        if (res == MP_OKAY) {
                return ret;
        } else {
                return 0;
        }
}

/* Computes R = nP based on algorithm 2P of Lopex, J. and Dahab, R.  "Fast
 * multiplication on elliptic curves over GF(2^m) without
 * precomputation". Elliptic curve points P and R can be identical. Uses
 * Montgomery projective coordinates. */
mp_err
ec_GF2m_pt_mul_mont(const mp_int *n, const mp_int *px, const mp_int *py,
                                        mp_int *rx, mp_int *ry, const ECGroup *group)
{
        mp_err res = MP_OKAY;
        mp_int x1, x2, z1, z2;
        int i, j;
        mp_digit top_bit, mask;

        MP_DIGITS(&x1) = 0;
        MP_DIGITS(&x2) = 0;
        MP_DIGITS(&z1) = 0;
        MP_DIGITS(&z2) = 0;
        MP_CHECKOK(mp_init(&x1, FLAG(n)));
        MP_CHECKOK(mp_init(&x2, FLAG(n)));
        MP_CHECKOK(mp_init(&z1, FLAG(n)));
        MP_CHECKOK(mp_init(&z2, FLAG(n)));

        /* if result should be point at infinity */
        if ((mp_cmp_z(n) == 0) || (ec_GF2m_pt_is_inf_aff(px, py) == MP_YES)) {
                MP_CHECKOK(ec_GF2m_pt_set_inf_aff(rx, ry));
                goto CLEANUP;
        }

        MP_CHECKOK(mp_copy(px, &x1));   /* x1 = px */
        MP_CHECKOK(mp_set_int(&z1, 1)); /* z1 = 1 */
        MP_CHECKOK(group->meth->field_sqr(&x1, &z2, group->meth));      /* z2 =
                                                                                                                                 * x1^2 =
                                                                                                                                 * px^2 */
        MP_CHECKOK(group->meth->field_sqr(&z2, &x2, group->meth));
        MP_CHECKOK(group->meth->field_add(&x2, &group->curveb, &x2, group->meth));      /* x2
                                                                                                                                                                 * =
                                                                                                                                                                 * px^4
                                                                                                                                                                 * +
                                                                                                                                                                 * b
                                                                                                                                                                 */

        /* find top-most bit and go one past it */
        i = MP_USED(n) - 1;
        j = MP_DIGIT_BIT - 1;
        top_bit = 1;
        top_bit <<= MP_DIGIT_BIT - 1;
        mask = top_bit;
        while (!(MP_DIGITS(n)[i] & mask)) {
                mask >>= 1;
                j--;
        }
        mask >>= 1;
        j--;

        /* if top most bit was at word break, go to next word */
        if (!mask) {
                i--;
                j = MP_DIGIT_BIT - 1;
                mask = top_bit;
        }

        for (; i >= 0; i--) {
                for (; j >= 0; j--) {
                        if (MP_DIGITS(n)[i] & mask) {
                                MP_CHECKOK(gf2m_Madd(px, &x1, &z1, &x2, &z2, group, FLAG(n)));
                                MP_CHECKOK(gf2m_Mdouble(&x2, &z2, group, FLAG(n)));
                        } else {
                                MP_CHECKOK(gf2m_Madd(px, &x2, &z2, &x1, &z1, group, FLAG(n)));
                                MP_CHECKOK(gf2m_Mdouble(&x1, &z1, group, FLAG(n)));
                        }
                        mask >>= 1;
                }
                j = MP_DIGIT_BIT - 1;
                mask = top_bit;
        }

        /* convert out of "projective" coordinates */
        i = gf2m_Mxy(px, py, &x1, &z1, &x2, &z2, group);
        if (i == 0) {
                res = MP_BADARG;
                goto CLEANUP;
        } else if (i == 1) {
                MP_CHECKOK(ec_GF2m_pt_set_inf_aff(rx, ry));
        } else {
                MP_CHECKOK(mp_copy(&x2, rx));
                MP_CHECKOK(mp_copy(&z2, ry));
        }

  CLEANUP:
        mp_clear(&x1);
        mp_clear(&x2);
        mp_clear(&z1);
        mp_clear(&z2);
        return res;
}
