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
 *   Sheueling Chang-Shantz <sheueling.chang@sun.com>,
 *   Stephen Fung <fungstep@hotmail.com>, and
 *   Douglas Stebila <douglas@stebila.ca>, Sun Microsystems Laboratories.
 *   Bodo Moeller <moeller@cdc.informatik.tu-darmstadt.de>,
 *   Nils Larsch <nla@trustcenter.de>, and
 *   Lenka Fibikova <fibikova@exp-math.uni-essen.de>, the OpenSSL Project
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

#include "ecp.h"
#include "mplogic.h"
#ifndef _KERNEL
#include <stdlib.h>
#endif

/* Checks if point P(px, py) is at infinity.  Uses affine coordinates. */
mp_err
ec_GFp_pt_is_inf_aff(const mp_int *px, const mp_int *py)
{

        if ((mp_cmp_z(px) == 0) && (mp_cmp_z(py) == 0)) {
                return MP_YES;
        } else {
                return MP_NO;
        }

}

/* Sets P(px, py) to be the point at infinity.  Uses affine coordinates. */
mp_err
ec_GFp_pt_set_inf_aff(mp_int *px, mp_int *py)
{
        mp_zero(px);
        mp_zero(py);
        return MP_OKAY;
}

/* Computes R = P + Q based on IEEE P1363 A.10.1. Elliptic curve points P,
 * Q, and R can all be identical. Uses affine coordinates. Assumes input
 * is already field-encoded using field_enc, and returns output that is
 * still field-encoded. */
mp_err
ec_GFp_pt_add_aff(const mp_int *px, const mp_int *py, const mp_int *qx,
                                  const mp_int *qy, mp_int *rx, mp_int *ry,
                                  const ECGroup *group)
{
        mp_err res = MP_OKAY;
        mp_int lambda, temp, tempx, tempy;

        MP_DIGITS(&lambda) = 0;
        MP_DIGITS(&temp) = 0;
        MP_DIGITS(&tempx) = 0;
        MP_DIGITS(&tempy) = 0;
        MP_CHECKOK(mp_init(&lambda, FLAG(px)));
        MP_CHECKOK(mp_init(&temp, FLAG(px)));
        MP_CHECKOK(mp_init(&tempx, FLAG(px)));
        MP_CHECKOK(mp_init(&tempy, FLAG(px)));
        /* if P = inf, then R = Q */
        if (ec_GFp_pt_is_inf_aff(px, py) == 0) {
                MP_CHECKOK(mp_copy(qx, rx));
                MP_CHECKOK(mp_copy(qy, ry));
                res = MP_OKAY;
                goto CLEANUP;
        }
        /* if Q = inf, then R = P */
        if (ec_GFp_pt_is_inf_aff(qx, qy) == 0) {
                MP_CHECKOK(mp_copy(px, rx));
                MP_CHECKOK(mp_copy(py, ry));
                res = MP_OKAY;
                goto CLEANUP;
        }
        /* if px != qx, then lambda = (py-qy) / (px-qx) */
        if (mp_cmp(px, qx) != 0) {
                MP_CHECKOK(group->meth->field_sub(py, qy, &tempy, group->meth));
                MP_CHECKOK(group->meth->field_sub(px, qx, &tempx, group->meth));
                MP_CHECKOK(group->meth->
                                   field_div(&tempy, &tempx, &lambda, group->meth));
        } else {
                /* if py != qy or qy = 0, then R = inf */
                if (((mp_cmp(py, qy) != 0)) || (mp_cmp_z(qy) == 0)) {
                        mp_zero(rx);
                        mp_zero(ry);
                        res = MP_OKAY;
                        goto CLEANUP;
                }
                /* lambda = (3qx^2+a) / (2qy) */
                MP_CHECKOK(group->meth->field_sqr(qx, &tempx, group->meth));
                MP_CHECKOK(mp_set_int(&temp, 3));
                if (group->meth->field_enc) {
                        MP_CHECKOK(group->meth->field_enc(&temp, &temp, group->meth));
                }
                MP_CHECKOK(group->meth->
                                   field_mul(&tempx, &temp, &tempx, group->meth));
                MP_CHECKOK(group->meth->
                                   field_add(&tempx, &group->curvea, &tempx, group->meth));
                MP_CHECKOK(mp_set_int(&temp, 2));
                if (group->meth->field_enc) {
                        MP_CHECKOK(group->meth->field_enc(&temp, &temp, group->meth));
                }
                MP_CHECKOK(group->meth->field_mul(qy, &temp, &tempy, group->meth));
                MP_CHECKOK(group->meth->
                                   field_div(&tempx, &tempy, &lambda, group->meth));
        }
        /* rx = lambda^2 - px - qx */
        MP_CHECKOK(group->meth->field_sqr(&lambda, &tempx, group->meth));
        MP_CHECKOK(group->meth->field_sub(&tempx, px, &tempx, group->meth));
        MP_CHECKOK(group->meth->field_sub(&tempx, qx, &tempx, group->meth));
        /* ry = (x1-x2) * lambda - y1 */
        MP_CHECKOK(group->meth->field_sub(qx, &tempx, &tempy, group->meth));
        MP_CHECKOK(group->meth->
                           field_mul(&tempy, &lambda, &tempy, group->meth));
        MP_CHECKOK(group->meth->field_sub(&tempy, qy, &tempy, group->meth));
        MP_CHECKOK(mp_copy(&tempx, rx));
        MP_CHECKOK(mp_copy(&tempy, ry));

  CLEANUP:
        mp_clear(&lambda);
        mp_clear(&temp);
        mp_clear(&tempx);
        mp_clear(&tempy);
        return res;
}

/* Computes R = P - Q. Elliptic curve points P, Q, and R can all be
 * identical. Uses affine coordinates. Assumes input is already
 * field-encoded using field_enc, and returns output that is still
 * field-encoded. */
mp_err
ec_GFp_pt_sub_aff(const mp_int *px, const mp_int *py, const mp_int *qx,
                                  const mp_int *qy, mp_int *rx, mp_int *ry,
                                  const ECGroup *group)
{
        mp_err res = MP_OKAY;
        mp_int nqy;

        MP_DIGITS(&nqy) = 0;
        MP_CHECKOK(mp_init(&nqy, FLAG(px)));
        /* nqy = -qy */
        MP_CHECKOK(group->meth->field_neg(qy, &nqy, group->meth));
        res = group->point_add(px, py, qx, &nqy, rx, ry, group);
  CLEANUP:
        mp_clear(&nqy);
        return res;
}

/* Computes R = 2P. Elliptic curve points P and R can be identical. Uses
 * affine coordinates. Assumes input is already field-encoded using
 * field_enc, and returns output that is still field-encoded. */
mp_err
ec_GFp_pt_dbl_aff(const mp_int *px, const mp_int *py, mp_int *rx,
                                  mp_int *ry, const ECGroup *group)
{
        return ec_GFp_pt_add_aff(px, py, px, py, rx, ry, group);
}

/* by default, this routine is unused and thus doesn't need to be compiled */
#ifdef ECL_ENABLE_GFP_PT_MUL_AFF
/* Computes R = nP based on IEEE P1363 A.10.3. Elliptic curve points P and
 * R can be identical. Uses affine coordinates. Assumes input is already
 * field-encoded using field_enc, and returns output that is still
 * field-encoded. */
mp_err
ec_GFp_pt_mul_aff(const mp_int *n, const mp_int *px, const mp_int *py,
                                  mp_int *rx, mp_int *ry, const ECGroup *group)
{
        mp_err res = MP_OKAY;
        mp_int k, k3, qx, qy, sx, sy;
        int b1, b3, i, l;

        MP_DIGITS(&k) = 0;
        MP_DIGITS(&k3) = 0;
        MP_DIGITS(&qx) = 0;
        MP_DIGITS(&qy) = 0;
        MP_DIGITS(&sx) = 0;
        MP_DIGITS(&sy) = 0;
        MP_CHECKOK(mp_init(&k));
        MP_CHECKOK(mp_init(&k3));
        MP_CHECKOK(mp_init(&qx));
        MP_CHECKOK(mp_init(&qy));
        MP_CHECKOK(mp_init(&sx));
        MP_CHECKOK(mp_init(&sy));

        /* if n = 0 then r = inf */
        if (mp_cmp_z(n) == 0) {
                mp_zero(rx);
                mp_zero(ry);
                res = MP_OKAY;
                goto CLEANUP;
        }
        /* Q = P, k = n */
        MP_CHECKOK(mp_copy(px, &qx));
        MP_CHECKOK(mp_copy(py, &qy));
        MP_CHECKOK(mp_copy(n, &k));
        /* if n < 0 then Q = -Q, k = -k */
        if (mp_cmp_z(n) < 0) {
                MP_CHECKOK(group->meth->field_neg(&qy, &qy, group->meth));
                MP_CHECKOK(mp_neg(&k, &k));
        }
#ifdef ECL_DEBUG                                /* basic double and add method */
        l = mpl_significant_bits(&k) - 1;
        MP_CHECKOK(mp_copy(&qx, &sx));
        MP_CHECKOK(mp_copy(&qy, &sy));
        for (i = l - 1; i >= 0; i--) {
                /* S = 2S */
                MP_CHECKOK(group->point_dbl(&sx, &sy, &sx, &sy, group));
                /* if k_i = 1, then S = S + Q */
                if (mpl_get_bit(&k, i) != 0) {
                        MP_CHECKOK(group->
                                           point_add(&sx, &sy, &qx, &qy, &sx, &sy, group));
                }
        }
#else                                                   /* double and add/subtract method from
                                                                 * standard */
        /* k3 = 3 * k */
        MP_CHECKOK(mp_set_int(&k3, 3));
        MP_CHECKOK(mp_mul(&k, &k3, &k3));
        /* S = Q */
        MP_CHECKOK(mp_copy(&qx, &sx));
        MP_CHECKOK(mp_copy(&qy, &sy));
        /* l = index of high order bit in binary representation of 3*k */
        l = mpl_significant_bits(&k3) - 1;
        /* for i = l-1 downto 1 */
        for (i = l - 1; i >= 1; i--) {
                /* S = 2S */
                MP_CHECKOK(group->point_dbl(&sx, &sy, &sx, &sy, group));
                b3 = MP_GET_BIT(&k3, i);
                b1 = MP_GET_BIT(&k, i);
                /* if k3_i = 1 and k_i = 0, then S = S + Q */
                if ((b3 == 1) && (b1 == 0)) {
                        MP_CHECKOK(group->
                                           point_add(&sx, &sy, &qx, &qy, &sx, &sy, group));
                        /* if k3_i = 0 and k_i = 1, then S = S - Q */
                } else if ((b3 == 0) && (b1 == 1)) {
                        MP_CHECKOK(group->
                                           point_sub(&sx, &sy, &qx, &qy, &sx, &sy, group));
                }
        }
#endif
        /* output S */
        MP_CHECKOK(mp_copy(&sx, rx));
        MP_CHECKOK(mp_copy(&sy, ry));

  CLEANUP:
        mp_clear(&k);
        mp_clear(&k3);
        mp_clear(&qx);
        mp_clear(&qy);
        mp_clear(&sx);
        mp_clear(&sy);
        return res;
}
#endif

/* Validates a point on a GFp curve. */
mp_err
ec_GFp_validate_point(const mp_int *px, const mp_int *py, const ECGroup *group)
{
        mp_err res = MP_NO;
        mp_int accl, accr, tmp, pxt, pyt;

        MP_DIGITS(&accl) = 0;
        MP_DIGITS(&accr) = 0;
        MP_DIGITS(&tmp) = 0;
        MP_DIGITS(&pxt) = 0;
        MP_DIGITS(&pyt) = 0;
        MP_CHECKOK(mp_init(&accl, FLAG(px)));
        MP_CHECKOK(mp_init(&accr, FLAG(px)));
        MP_CHECKOK(mp_init(&tmp, FLAG(px)));
        MP_CHECKOK(mp_init(&pxt, FLAG(px)));
        MP_CHECKOK(mp_init(&pyt, FLAG(px)));

    /* 1: Verify that publicValue is not the point at infinity */
        if (ec_GFp_pt_is_inf_aff(px, py) == MP_YES) {
                res = MP_NO;
                goto CLEANUP;
        }
    /* 2: Verify that the coordinates of publicValue are elements
     *    of the field.
     */
        if ((MP_SIGN(px) == MP_NEG) || (mp_cmp(px, &group->meth->irr) >= 0) ||
                (MP_SIGN(py) == MP_NEG) || (mp_cmp(py, &group->meth->irr) >= 0)) {
                res = MP_NO;
                goto CLEANUP;
        }
    /* 3: Verify that publicValue is on the curve. */
        if (group->meth->field_enc) {
                group->meth->field_enc(px, &pxt, group->meth);
                group->meth->field_enc(py, &pyt, group->meth);
        } else {
                mp_copy(px, &pxt);
                mp_copy(py, &pyt);
        }
        /* left-hand side: y^2  */
        MP_CHECKOK( group->meth->field_sqr(&pyt, &accl, group->meth) );
        /* right-hand side: x^3 + a*x + b */
        MP_CHECKOK( group->meth->field_sqr(&pxt, &tmp, group->meth) );
        MP_CHECKOK( group->meth->field_mul(&pxt, &tmp, &accr, group->meth) );
        MP_CHECKOK( group->meth->field_mul(&group->curvea, &pxt, &tmp, group->meth) );
        MP_CHECKOK( group->meth->field_add(&tmp, &accr, &accr, group->meth) );
        MP_CHECKOK( group->meth->field_add(&accr, &group->curveb, &accr, group->meth) );
        /* check LHS - RHS == 0 */
        MP_CHECKOK( group->meth->field_sub(&accl, &accr, &accr, group->meth) );
        if (mp_cmp_z(&accr) != 0) {
                res = MP_NO;
                goto CLEANUP;
        }
    /* 4: Verify that the order of the curve times the publicValue
     *    is the point at infinity.
     */
        MP_CHECKOK( ECPoint_mul(group, &group->order, px, py, &pxt, &pyt) );
        if (ec_GFp_pt_is_inf_aff(&pxt, &pyt) != MP_YES) {
                res = MP_NO;
                goto CLEANUP;
        }

        res = MP_YES;

CLEANUP:
        mp_clear(&accl);
        mp_clear(&accr);
        mp_clear(&tmp);
        mp_clear(&pxt);
        mp_clear(&pyt);
        return res;
}
