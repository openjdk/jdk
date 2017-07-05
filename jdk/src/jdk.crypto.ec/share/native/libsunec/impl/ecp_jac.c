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
 *   Sheueling Chang-Shantz <sheueling.chang@sun.com>,
 *   Stephen Fung <fungstep@hotmail.com>, and
 *   Douglas Stebila <douglas@stebila.ca>, Sun Microsystems Laboratories.
 *   Bodo Moeller <moeller@cdc.informatik.tu-darmstadt.de>,
 *   Nils Larsch <nla@trustcenter.de>, and
 *   Lenka Fibikova <fibikova@exp-math.uni-essen.de>, the OpenSSL Project
 *
 *********************************************************************** */

#include "ecp.h"
#include "mplogic.h"
#ifndef _KERNEL
#include <stdlib.h>
#endif
#ifdef ECL_DEBUG
#include <assert.h>
#endif

/* Converts a point P(px, py) from affine coordinates to Jacobian
 * projective coordinates R(rx, ry, rz). Assumes input is already
 * field-encoded using field_enc, and returns output that is still
 * field-encoded. */
mp_err
ec_GFp_pt_aff2jac(const mp_int *px, const mp_int *py, mp_int *rx,
                                  mp_int *ry, mp_int *rz, const ECGroup *group)
{
        mp_err res = MP_OKAY;

        if (ec_GFp_pt_is_inf_aff(px, py) == MP_YES) {
                MP_CHECKOK(ec_GFp_pt_set_inf_jac(rx, ry, rz));
        } else {
                MP_CHECKOK(mp_copy(px, rx));
                MP_CHECKOK(mp_copy(py, ry));
                MP_CHECKOK(mp_set_int(rz, 1));
                if (group->meth->field_enc) {
                        MP_CHECKOK(group->meth->field_enc(rz, rz, group->meth));
                }
        }
  CLEANUP:
        return res;
}

/* Converts a point P(px, py, pz) from Jacobian projective coordinates to
 * affine coordinates R(rx, ry).  P and R can share x and y coordinates.
 * Assumes input is already field-encoded using field_enc, and returns
 * output that is still field-encoded. */
mp_err
ec_GFp_pt_jac2aff(const mp_int *px, const mp_int *py, const mp_int *pz,
                                  mp_int *rx, mp_int *ry, const ECGroup *group)
{
        mp_err res = MP_OKAY;
        mp_int z1, z2, z3;

        MP_DIGITS(&z1) = 0;
        MP_DIGITS(&z2) = 0;
        MP_DIGITS(&z3) = 0;
        MP_CHECKOK(mp_init(&z1, FLAG(px)));
        MP_CHECKOK(mp_init(&z2, FLAG(px)));
        MP_CHECKOK(mp_init(&z3, FLAG(px)));

        /* if point at infinity, then set point at infinity and exit */
        if (ec_GFp_pt_is_inf_jac(px, py, pz) == MP_YES) {
                MP_CHECKOK(ec_GFp_pt_set_inf_aff(rx, ry));
                goto CLEANUP;
        }

        /* transform (px, py, pz) into (px / pz^2, py / pz^3) */
        if (mp_cmp_d(pz, 1) == 0) {
                MP_CHECKOK(mp_copy(px, rx));
                MP_CHECKOK(mp_copy(py, ry));
        } else {
                MP_CHECKOK(group->meth->field_div(NULL, pz, &z1, group->meth));
                MP_CHECKOK(group->meth->field_sqr(&z1, &z2, group->meth));
                MP_CHECKOK(group->meth->field_mul(&z1, &z2, &z3, group->meth));
                MP_CHECKOK(group->meth->field_mul(px, &z2, rx, group->meth));
                MP_CHECKOK(group->meth->field_mul(py, &z3, ry, group->meth));
        }

  CLEANUP:
        mp_clear(&z1);
        mp_clear(&z2);
        mp_clear(&z3);
        return res;
}

/* Checks if point P(px, py, pz) is at infinity. Uses Jacobian
 * coordinates. */
mp_err
ec_GFp_pt_is_inf_jac(const mp_int *px, const mp_int *py, const mp_int *pz)
{
        return mp_cmp_z(pz);
}

/* Sets P(px, py, pz) to be the point at infinity.  Uses Jacobian
 * coordinates. */
mp_err
ec_GFp_pt_set_inf_jac(mp_int *px, mp_int *py, mp_int *pz)
{
        mp_zero(pz);
        return MP_OKAY;
}

/* Computes R = P + Q where R is (rx, ry, rz), P is (px, py, pz) and Q is
 * (qx, qy, 1).  Elliptic curve points P, Q, and R can all be identical.
 * Uses mixed Jacobian-affine coordinates. Assumes input is already
 * field-encoded using field_enc, and returns output that is still
 * field-encoded. Uses equation (2) from Brown, Hankerson, Lopez, and
 * Menezes. Software Implementation of the NIST Elliptic Curves Over Prime
 * Fields. */
mp_err
ec_GFp_pt_add_jac_aff(const mp_int *px, const mp_int *py, const mp_int *pz,
                                          const mp_int *qx, const mp_int *qy, mp_int *rx,
                                          mp_int *ry, mp_int *rz, const ECGroup *group)
{
        mp_err res = MP_OKAY;
        mp_int A, B, C, D, C2, C3;

        MP_DIGITS(&A) = 0;
        MP_DIGITS(&B) = 0;
        MP_DIGITS(&C) = 0;
        MP_DIGITS(&D) = 0;
        MP_DIGITS(&C2) = 0;
        MP_DIGITS(&C3) = 0;
        MP_CHECKOK(mp_init(&A, FLAG(px)));
        MP_CHECKOK(mp_init(&B, FLAG(px)));
        MP_CHECKOK(mp_init(&C, FLAG(px)));
        MP_CHECKOK(mp_init(&D, FLAG(px)));
        MP_CHECKOK(mp_init(&C2, FLAG(px)));
        MP_CHECKOK(mp_init(&C3, FLAG(px)));

        /* If either P or Q is the point at infinity, then return the other
         * point */
        if (ec_GFp_pt_is_inf_jac(px, py, pz) == MP_YES) {
                MP_CHECKOK(ec_GFp_pt_aff2jac(qx, qy, rx, ry, rz, group));
                goto CLEANUP;
        }
        if (ec_GFp_pt_is_inf_aff(qx, qy) == MP_YES) {
                MP_CHECKOK(mp_copy(px, rx));
                MP_CHECKOK(mp_copy(py, ry));
                MP_CHECKOK(mp_copy(pz, rz));
                goto CLEANUP;
        }

        /* A = qx * pz^2, B = qy * pz^3 */
        MP_CHECKOK(group->meth->field_sqr(pz, &A, group->meth));
        MP_CHECKOK(group->meth->field_mul(&A, pz, &B, group->meth));
        MP_CHECKOK(group->meth->field_mul(&A, qx, &A, group->meth));
        MP_CHECKOK(group->meth->field_mul(&B, qy, &B, group->meth));

        /* C = A - px, D = B - py */
        MP_CHECKOK(group->meth->field_sub(&A, px, &C, group->meth));
        MP_CHECKOK(group->meth->field_sub(&B, py, &D, group->meth));

        /* C2 = C^2, C3 = C^3 */
        MP_CHECKOK(group->meth->field_sqr(&C, &C2, group->meth));
        MP_CHECKOK(group->meth->field_mul(&C, &C2, &C3, group->meth));

        /* rz = pz * C */
        MP_CHECKOK(group->meth->field_mul(pz, &C, rz, group->meth));

        /* C = px * C^2 */
        MP_CHECKOK(group->meth->field_mul(px, &C2, &C, group->meth));
        /* A = D^2 */
        MP_CHECKOK(group->meth->field_sqr(&D, &A, group->meth));

        /* rx = D^2 - (C^3 + 2 * (px * C^2)) */
        MP_CHECKOK(group->meth->field_add(&C, &C, rx, group->meth));
        MP_CHECKOK(group->meth->field_add(&C3, rx, rx, group->meth));
        MP_CHECKOK(group->meth->field_sub(&A, rx, rx, group->meth));

        /* C3 = py * C^3 */
        MP_CHECKOK(group->meth->field_mul(py, &C3, &C3, group->meth));

        /* ry = D * (px * C^2 - rx) - py * C^3 */
        MP_CHECKOK(group->meth->field_sub(&C, rx, ry, group->meth));
        MP_CHECKOK(group->meth->field_mul(&D, ry, ry, group->meth));
        MP_CHECKOK(group->meth->field_sub(ry, &C3, ry, group->meth));

  CLEANUP:
        mp_clear(&A);
        mp_clear(&B);
        mp_clear(&C);
        mp_clear(&D);
        mp_clear(&C2);
        mp_clear(&C3);
        return res;
}

/* Computes R = 2P.  Elliptic curve points P and R can be identical.  Uses
 * Jacobian coordinates.
 *
 * Assumes input is already field-encoded using field_enc, and returns
 * output that is still field-encoded.
 *
 * This routine implements Point Doubling in the Jacobian Projective
 * space as described in the paper "Efficient elliptic curve exponentiation
 * using mixed coordinates", by H. Cohen, A Miyaji, T. Ono.
 */
mp_err
ec_GFp_pt_dbl_jac(const mp_int *px, const mp_int *py, const mp_int *pz,
                                  mp_int *rx, mp_int *ry, mp_int *rz, const ECGroup *group)
{
        mp_err res = MP_OKAY;
        mp_int t0, t1, M, S;

        MP_DIGITS(&t0) = 0;
        MP_DIGITS(&t1) = 0;
        MP_DIGITS(&M) = 0;
        MP_DIGITS(&S) = 0;
        MP_CHECKOK(mp_init(&t0, FLAG(px)));
        MP_CHECKOK(mp_init(&t1, FLAG(px)));
        MP_CHECKOK(mp_init(&M, FLAG(px)));
        MP_CHECKOK(mp_init(&S, FLAG(px)));

        if (ec_GFp_pt_is_inf_jac(px, py, pz) == MP_YES) {
                MP_CHECKOK(ec_GFp_pt_set_inf_jac(rx, ry, rz));
                goto CLEANUP;
        }

        if (mp_cmp_d(pz, 1) == 0) {
                /* M = 3 * px^2 + a */
                MP_CHECKOK(group->meth->field_sqr(px, &t0, group->meth));
                MP_CHECKOK(group->meth->field_add(&t0, &t0, &M, group->meth));
                MP_CHECKOK(group->meth->field_add(&t0, &M, &t0, group->meth));
                MP_CHECKOK(group->meth->
                                   field_add(&t0, &group->curvea, &M, group->meth));
        } else if (mp_cmp_int(&group->curvea, -3, FLAG(px)) == 0) {
                /* M = 3 * (px + pz^2) * (px - pz^2) */
                MP_CHECKOK(group->meth->field_sqr(pz, &M, group->meth));
                MP_CHECKOK(group->meth->field_add(px, &M, &t0, group->meth));
                MP_CHECKOK(group->meth->field_sub(px, &M, &t1, group->meth));
                MP_CHECKOK(group->meth->field_mul(&t0, &t1, &M, group->meth));
                MP_CHECKOK(group->meth->field_add(&M, &M, &t0, group->meth));
                MP_CHECKOK(group->meth->field_add(&t0, &M, &M, group->meth));
        } else {
                /* M = 3 * (px^2) + a * (pz^4) */
                MP_CHECKOK(group->meth->field_sqr(px, &t0, group->meth));
                MP_CHECKOK(group->meth->field_add(&t0, &t0, &M, group->meth));
                MP_CHECKOK(group->meth->field_add(&t0, &M, &t0, group->meth));
                MP_CHECKOK(group->meth->field_sqr(pz, &M, group->meth));
                MP_CHECKOK(group->meth->field_sqr(&M, &M, group->meth));
                MP_CHECKOK(group->meth->
                                   field_mul(&M, &group->curvea, &M, group->meth));
                MP_CHECKOK(group->meth->field_add(&M, &t0, &M, group->meth));
        }

        /* rz = 2 * py * pz */
        /* t0 = 4 * py^2 */
        if (mp_cmp_d(pz, 1) == 0) {
                MP_CHECKOK(group->meth->field_add(py, py, rz, group->meth));
                MP_CHECKOK(group->meth->field_sqr(rz, &t0, group->meth));
        } else {
                MP_CHECKOK(group->meth->field_add(py, py, &t0, group->meth));
                MP_CHECKOK(group->meth->field_mul(&t0, pz, rz, group->meth));
                MP_CHECKOK(group->meth->field_sqr(&t0, &t0, group->meth));
        }

        /* S = 4 * px * py^2 = px * (2 * py)^2 */
        MP_CHECKOK(group->meth->field_mul(px, &t0, &S, group->meth));

        /* rx = M^2 - 2 * S */
        MP_CHECKOK(group->meth->field_add(&S, &S, &t1, group->meth));
        MP_CHECKOK(group->meth->field_sqr(&M, rx, group->meth));
        MP_CHECKOK(group->meth->field_sub(rx, &t1, rx, group->meth));

        /* ry = M * (S - rx) - 8 * py^4 */
        MP_CHECKOK(group->meth->field_sqr(&t0, &t1, group->meth));
        if (mp_isodd(&t1)) {
                MP_CHECKOK(mp_add(&t1, &group->meth->irr, &t1));
        }
        MP_CHECKOK(mp_div_2(&t1, &t1));
        MP_CHECKOK(group->meth->field_sub(&S, rx, &S, group->meth));
        MP_CHECKOK(group->meth->field_mul(&M, &S, &M, group->meth));
        MP_CHECKOK(group->meth->field_sub(&M, &t1, ry, group->meth));

  CLEANUP:
        mp_clear(&t0);
        mp_clear(&t1);
        mp_clear(&M);
        mp_clear(&S);
        return res;
}

/* by default, this routine is unused and thus doesn't need to be compiled */
#ifdef ECL_ENABLE_GFP_PT_MUL_JAC
/* Computes R = nP where R is (rx, ry) and P is (px, py). The parameters
 * a, b and p are the elliptic curve coefficients and the prime that
 * determines the field GFp.  Elliptic curve points P and R can be
 * identical.  Uses mixed Jacobian-affine coordinates. Assumes input is
 * already field-encoded using field_enc, and returns output that is still
 * field-encoded. Uses 4-bit window method. */
mp_err
ec_GFp_pt_mul_jac(const mp_int *n, const mp_int *px, const mp_int *py,
                                  mp_int *rx, mp_int *ry, const ECGroup *group)
{
        mp_err res = MP_OKAY;
        mp_int precomp[16][2], rz;
        int i, ni, d;

        MP_DIGITS(&rz) = 0;
        for (i = 0; i < 16; i++) {
                MP_DIGITS(&precomp[i][0]) = 0;
                MP_DIGITS(&precomp[i][1]) = 0;
        }

        ARGCHK(group != NULL, MP_BADARG);
        ARGCHK((n != NULL) && (px != NULL) && (py != NULL), MP_BADARG);

        /* initialize precomputation table */
        for (i = 0; i < 16; i++) {
                MP_CHECKOK(mp_init(&precomp[i][0]));
                MP_CHECKOK(mp_init(&precomp[i][1]));
        }

        /* fill precomputation table */
        mp_zero(&precomp[0][0]);
        mp_zero(&precomp[0][1]);
        MP_CHECKOK(mp_copy(px, &precomp[1][0]));
        MP_CHECKOK(mp_copy(py, &precomp[1][1]));
        for (i = 2; i < 16; i++) {
                MP_CHECKOK(group->
                                   point_add(&precomp[1][0], &precomp[1][1],
                                                         &precomp[i - 1][0], &precomp[i - 1][1],
                                                         &precomp[i][0], &precomp[i][1], group));
        }

        d = (mpl_significant_bits(n) + 3) / 4;

        /* R = inf */
        MP_CHECKOK(mp_init(&rz));
        MP_CHECKOK(ec_GFp_pt_set_inf_jac(rx, ry, &rz));

        for (i = d - 1; i >= 0; i--) {
                /* compute window ni */
                ni = MP_GET_BIT(n, 4 * i + 3);
                ni <<= 1;
                ni |= MP_GET_BIT(n, 4 * i + 2);
                ni <<= 1;
                ni |= MP_GET_BIT(n, 4 * i + 1);
                ni <<= 1;
                ni |= MP_GET_BIT(n, 4 * i);
                /* R = 2^4 * R */
                MP_CHECKOK(ec_GFp_pt_dbl_jac(rx, ry, &rz, rx, ry, &rz, group));
                MP_CHECKOK(ec_GFp_pt_dbl_jac(rx, ry, &rz, rx, ry, &rz, group));
                MP_CHECKOK(ec_GFp_pt_dbl_jac(rx, ry, &rz, rx, ry, &rz, group));
                MP_CHECKOK(ec_GFp_pt_dbl_jac(rx, ry, &rz, rx, ry, &rz, group));
                /* R = R + (ni * P) */
                MP_CHECKOK(ec_GFp_pt_add_jac_aff
                                   (rx, ry, &rz, &precomp[ni][0], &precomp[ni][1], rx, ry,
                                        &rz, group));
        }

        /* convert result S to affine coordinates */
        MP_CHECKOK(ec_GFp_pt_jac2aff(rx, ry, &rz, rx, ry, group));

  CLEANUP:
        mp_clear(&rz);
        for (i = 0; i < 16; i++) {
                mp_clear(&precomp[i][0]);
                mp_clear(&precomp[i][1]);
        }
        return res;
}
#endif

/* Elliptic curve scalar-point multiplication. Computes R(x, y) = k1 * G +
 * k2 * P(x, y), where G is the generator (base point) of the group of
 * points on the elliptic curve. Allows k1 = NULL or { k2, P } = NULL.
 * Uses mixed Jacobian-affine coordinates. Input and output values are
 * assumed to be NOT field-encoded. Uses algorithm 15 (simultaneous
 * multiple point multiplication) from Brown, Hankerson, Lopez, Menezes.
 * Software Implementation of the NIST Elliptic Curves over Prime Fields. */
mp_err
ec_GFp_pts_mul_jac(const mp_int *k1, const mp_int *k2, const mp_int *px,
                                   const mp_int *py, mp_int *rx, mp_int *ry,
                                   const ECGroup *group)
{
        mp_err res = MP_OKAY;
        mp_int precomp[4][4][2];
        mp_int rz;
        const mp_int *a, *b;
        int i, j;
        int ai, bi, d;

        for (i = 0; i < 4; i++) {
                for (j = 0; j < 4; j++) {
                        MP_DIGITS(&precomp[i][j][0]) = 0;
                        MP_DIGITS(&precomp[i][j][1]) = 0;
                }
        }
        MP_DIGITS(&rz) = 0;

        ARGCHK(group != NULL, MP_BADARG);
        ARGCHK(!((k1 == NULL)
                         && ((k2 == NULL) || (px == NULL)
                                 || (py == NULL))), MP_BADARG);

        /* if some arguments are not defined used ECPoint_mul */
        if (k1 == NULL) {
                return ECPoint_mul(group, k2, px, py, rx, ry);
        } else if ((k2 == NULL) || (px == NULL) || (py == NULL)) {
                return ECPoint_mul(group, k1, NULL, NULL, rx, ry);
        }

        /* initialize precomputation table */
        for (i = 0; i < 4; i++) {
                for (j = 0; j < 4; j++) {
                        MP_CHECKOK(mp_init(&precomp[i][j][0], FLAG(k1)));
                        MP_CHECKOK(mp_init(&precomp[i][j][1], FLAG(k1)));
                }
        }

        /* fill precomputation table */
        /* assign {k1, k2} = {a, b} such that len(a) >= len(b) */
        if (mpl_significant_bits(k1) < mpl_significant_bits(k2)) {
                a = k2;
                b = k1;
                if (group->meth->field_enc) {
                        MP_CHECKOK(group->meth->
                                           field_enc(px, &precomp[1][0][0], group->meth));
                        MP_CHECKOK(group->meth->
                                           field_enc(py, &precomp[1][0][1], group->meth));
                } else {
                        MP_CHECKOK(mp_copy(px, &precomp[1][0][0]));
                        MP_CHECKOK(mp_copy(py, &precomp[1][0][1]));
                }
                MP_CHECKOK(mp_copy(&group->genx, &precomp[0][1][0]));
                MP_CHECKOK(mp_copy(&group->geny, &precomp[0][1][1]));
        } else {
                a = k1;
                b = k2;
                MP_CHECKOK(mp_copy(&group->genx, &precomp[1][0][0]));
                MP_CHECKOK(mp_copy(&group->geny, &precomp[1][0][1]));
                if (group->meth->field_enc) {
                        MP_CHECKOK(group->meth->
                                           field_enc(px, &precomp[0][1][0], group->meth));
                        MP_CHECKOK(group->meth->
                                           field_enc(py, &precomp[0][1][1], group->meth));
                } else {
                        MP_CHECKOK(mp_copy(px, &precomp[0][1][0]));
                        MP_CHECKOK(mp_copy(py, &precomp[0][1][1]));
                }
        }
        /* precompute [*][0][*] */
        mp_zero(&precomp[0][0][0]);
        mp_zero(&precomp[0][0][1]);
        MP_CHECKOK(group->
                           point_dbl(&precomp[1][0][0], &precomp[1][0][1],
                                                 &precomp[2][0][0], &precomp[2][0][1], group));
        MP_CHECKOK(group->
                           point_add(&precomp[1][0][0], &precomp[1][0][1],
                                                 &precomp[2][0][0], &precomp[2][0][1],
                                                 &precomp[3][0][0], &precomp[3][0][1], group));
        /* precompute [*][1][*] */
        for (i = 1; i < 4; i++) {
                MP_CHECKOK(group->
                                   point_add(&precomp[0][1][0], &precomp[0][1][1],
                                                         &precomp[i][0][0], &precomp[i][0][1],
                                                         &precomp[i][1][0], &precomp[i][1][1], group));
        }
        /* precompute [*][2][*] */
        MP_CHECKOK(group->
                           point_dbl(&precomp[0][1][0], &precomp[0][1][1],
                                                 &precomp[0][2][0], &precomp[0][2][1], group));
        for (i = 1; i < 4; i++) {
                MP_CHECKOK(group->
                                   point_add(&precomp[0][2][0], &precomp[0][2][1],
                                                         &precomp[i][0][0], &precomp[i][0][1],
                                                         &precomp[i][2][0], &precomp[i][2][1], group));
        }
        /* precompute [*][3][*] */
        MP_CHECKOK(group->
                           point_add(&precomp[0][1][0], &precomp[0][1][1],
                                                 &precomp[0][2][0], &precomp[0][2][1],
                                                 &precomp[0][3][0], &precomp[0][3][1], group));
        for (i = 1; i < 4; i++) {
                MP_CHECKOK(group->
                                   point_add(&precomp[0][3][0], &precomp[0][3][1],
                                                         &precomp[i][0][0], &precomp[i][0][1],
                                                         &precomp[i][3][0], &precomp[i][3][1], group));
        }

        d = (mpl_significant_bits(a) + 1) / 2;

        /* R = inf */
        MP_CHECKOK(mp_init(&rz, FLAG(k1)));
        MP_CHECKOK(ec_GFp_pt_set_inf_jac(rx, ry, &rz));

        for (i = d - 1; i >= 0; i--) {
                ai = MP_GET_BIT(a, 2 * i + 1);
                ai <<= 1;
                ai |= MP_GET_BIT(a, 2 * i);
                bi = MP_GET_BIT(b, 2 * i + 1);
                bi <<= 1;
                bi |= MP_GET_BIT(b, 2 * i);
                /* R = 2^2 * R */
                MP_CHECKOK(ec_GFp_pt_dbl_jac(rx, ry, &rz, rx, ry, &rz, group));
                MP_CHECKOK(ec_GFp_pt_dbl_jac(rx, ry, &rz, rx, ry, &rz, group));
                /* R = R + (ai * A + bi * B) */
                MP_CHECKOK(ec_GFp_pt_add_jac_aff
                                   (rx, ry, &rz, &precomp[ai][bi][0], &precomp[ai][bi][1],
                                        rx, ry, &rz, group));
        }

        MP_CHECKOK(ec_GFp_pt_jac2aff(rx, ry, &rz, rx, ry, group));

        if (group->meth->field_dec) {
                MP_CHECKOK(group->meth->field_dec(rx, rx, group->meth));
                MP_CHECKOK(group->meth->field_dec(ry, ry, group->meth));
        }

  CLEANUP:
        mp_clear(&rz);
        for (i = 0; i < 4; i++) {
                for (j = 0; j < 4; j++) {
                        mp_clear(&precomp[i][j][0]);
                        mp_clear(&precomp[i][j][1]);
                }
        }
        return res;
}
