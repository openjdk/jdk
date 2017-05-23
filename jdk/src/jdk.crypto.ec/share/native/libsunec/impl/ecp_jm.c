/*
 * Copyright (c) 2007, 2017, Oracle and/or its affiliates. All rights reserved.
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
 *   Stephen Fung <fungstep@hotmail.com>, Sun Microsystems Laboratories
 *
 * Last Modified Date from the Original Code: May 2017
 *********************************************************************** */

#include "ecp.h"
#include "ecl-priv.h"
#include "mplogic.h"
#ifndef _KERNEL
#include <stdlib.h>
#endif

#define MAX_SCRATCH 6

/* Computes R = 2P.  Elliptic curve points P and R can be identical.  Uses
 * Modified Jacobian coordinates.
 *
 * Assumes input is already field-encoded using field_enc, and returns
 * output that is still field-encoded.
 *
 */
mp_err
ec_GFp_pt_dbl_jm(const mp_int *px, const mp_int *py, const mp_int *pz,
                                 const mp_int *paz4, mp_int *rx, mp_int *ry, mp_int *rz,
                                 mp_int *raz4, mp_int scratch[], const ECGroup *group)
{
        mp_err res = MP_OKAY;
        mp_int *t0, *t1, *M, *S;

        t0 = &scratch[0];
        t1 = &scratch[1];
        M = &scratch[2];
        S = &scratch[3];

#if MAX_SCRATCH < 4
#error "Scratch array defined too small "
#endif

        /* Check for point at infinity */
        if (ec_GFp_pt_is_inf_jac(px, py, pz) == MP_YES) {
                /* Set r = pt at infinity by setting rz = 0 */

                MP_CHECKOK(ec_GFp_pt_set_inf_jac(rx, ry, rz));
                goto CLEANUP;
        }

        /* M = 3 (px^2) + a*(pz^4) */
        MP_CHECKOK(group->meth->field_sqr(px, t0, group->meth));
        MP_CHECKOK(group->meth->field_add(t0, t0, M, group->meth));
        MP_CHECKOK(group->meth->field_add(t0, M, t0, group->meth));
        MP_CHECKOK(group->meth->field_add(t0, paz4, M, group->meth));

        /* rz = 2 * py * pz */
        MP_CHECKOK(group->meth->field_mul(py, pz, S, group->meth));
        MP_CHECKOK(group->meth->field_add(S, S, rz, group->meth));

        /* t0 = 2y^2 , t1 = 8y^4 */
        MP_CHECKOK(group->meth->field_sqr(py, t0, group->meth));
        MP_CHECKOK(group->meth->field_add(t0, t0, t0, group->meth));
        MP_CHECKOK(group->meth->field_sqr(t0, t1, group->meth));
        MP_CHECKOK(group->meth->field_add(t1, t1, t1, group->meth));

        /* S = 4 * px * py^2 = 2 * px * t0 */
        MP_CHECKOK(group->meth->field_mul(px, t0, S, group->meth));
        MP_CHECKOK(group->meth->field_add(S, S, S, group->meth));


        /* rx = M^2 - 2S */
        MP_CHECKOK(group->meth->field_sqr(M, rx, group->meth));
        MP_CHECKOK(group->meth->field_sub(rx, S, rx, group->meth));
        MP_CHECKOK(group->meth->field_sub(rx, S, rx, group->meth));

        /* ry = M * (S - rx) - t1 */
        MP_CHECKOK(group->meth->field_sub(S, rx, S, group->meth));
        MP_CHECKOK(group->meth->field_mul(S, M, ry, group->meth));
        MP_CHECKOK(group->meth->field_sub(ry, t1, ry, group->meth));

        /* ra*z^4 = 2*t1*(apz4) */
        MP_CHECKOK(group->meth->field_mul(paz4, t1, raz4, group->meth));
        MP_CHECKOK(group->meth->field_add(raz4, raz4, raz4, group->meth));


  CLEANUP:
        return res;
}

/* Computes R = P + Q where R is (rx, ry, rz), P is (px, py, pz) and Q is
 * (qx, qy, 1).  Elliptic curve points P, Q, and R can all be identical.
 * Uses mixed Modified_Jacobian-affine coordinates. Assumes input is
 * already field-encoded using field_enc, and returns output that is still
 * field-encoded. */
mp_err
ec_GFp_pt_add_jm_aff(const mp_int *px, const mp_int *py, const mp_int *pz,
                                         const mp_int *paz4, const mp_int *qx,
                                         const mp_int *qy, mp_int *rx, mp_int *ry, mp_int *rz,
                                         mp_int *raz4, mp_int scratch[], const ECGroup *group)
{
        mp_err res = MP_OKAY;
        mp_int *A, *B, *C, *D, *C2, *C3;

        A = &scratch[0];
        B = &scratch[1];
        C = &scratch[2];
        D = &scratch[3];
        C2 = &scratch[4];
        C3 = &scratch[5];

#if MAX_SCRATCH < 6
#error "Scratch array defined too small "
#endif

        /* If either P or Q is the point at infinity, then return the other
         * point */
        if (ec_GFp_pt_is_inf_jac(px, py, pz) == MP_YES) {
                MP_CHECKOK(ec_GFp_pt_aff2jac(qx, qy, rx, ry, rz, group));
                MP_CHECKOK(group->meth->field_sqr(rz, raz4, group->meth));
                MP_CHECKOK(group->meth->field_sqr(raz4, raz4, group->meth));
                MP_CHECKOK(group->meth->
                                   field_mul(raz4, &group->curvea, raz4, group->meth));
                goto CLEANUP;
        }
        if (ec_GFp_pt_is_inf_aff(qx, qy) == MP_YES) {
                MP_CHECKOK(mp_copy(px, rx));
                MP_CHECKOK(mp_copy(py, ry));
                MP_CHECKOK(mp_copy(pz, rz));
                MP_CHECKOK(mp_copy(paz4, raz4));
                goto CLEANUP;
        }

        /* A = qx * pz^2, B = qy * pz^3 */
        MP_CHECKOK(group->meth->field_sqr(pz, A, group->meth));
        MP_CHECKOK(group->meth->field_mul(A, pz, B, group->meth));
        MP_CHECKOK(group->meth->field_mul(A, qx, A, group->meth));
        MP_CHECKOK(group->meth->field_mul(B, qy, B, group->meth));

        /*
         * Additional checks for point equality and point at infinity
         */
        if (mp_cmp(px, A) == 0 && mp_cmp(py, B) == 0) {
            /* POINT_DOUBLE(P) */
            MP_CHECKOK(ec_GFp_pt_dbl_jm(px, py, pz, paz4, rx, ry, rz, raz4,
                                        scratch, group));
            goto CLEANUP;
        }

        /* C = A - px, D = B - py */
        MP_CHECKOK(group->meth->field_sub(A, px, C, group->meth));
        MP_CHECKOK(group->meth->field_sub(B, py, D, group->meth));

        /* C2 = C^2, C3 = C^3 */
        MP_CHECKOK(group->meth->field_sqr(C, C2, group->meth));
        MP_CHECKOK(group->meth->field_mul(C, C2, C3, group->meth));

        /* rz = pz * C */
        MP_CHECKOK(group->meth->field_mul(pz, C, rz, group->meth));

        /* C = px * C^2 */
        MP_CHECKOK(group->meth->field_mul(px, C2, C, group->meth));
        /* A = D^2 */
        MP_CHECKOK(group->meth->field_sqr(D, A, group->meth));

        /* rx = D^2 - (C^3 + 2 * (px * C^2)) */
        MP_CHECKOK(group->meth->field_add(C, C, rx, group->meth));
        MP_CHECKOK(group->meth->field_add(C3, rx, rx, group->meth));
        MP_CHECKOK(group->meth->field_sub(A, rx, rx, group->meth));

        /* C3 = py * C^3 */
        MP_CHECKOK(group->meth->field_mul(py, C3, C3, group->meth));

        /* ry = D * (px * C^2 - rx) - py * C^3 */
        MP_CHECKOK(group->meth->field_sub(C, rx, ry, group->meth));
        MP_CHECKOK(group->meth->field_mul(D, ry, ry, group->meth));
        MP_CHECKOK(group->meth->field_sub(ry, C3, ry, group->meth));

        /* raz4 = a * rz^4 */
        MP_CHECKOK(group->meth->field_sqr(rz, raz4, group->meth));
        MP_CHECKOK(group->meth->field_sqr(raz4, raz4, group->meth));
        MP_CHECKOK(group->meth->
                           field_mul(raz4, &group->curvea, raz4, group->meth));
CLEANUP:
        return res;
}

/* Computes R = nP where R is (rx, ry) and P is the base point. Elliptic
 * curve points P and R can be identical. Uses mixed Modified-Jacobian
 * co-ordinates for doubling and Chudnovsky Jacobian coordinates for
 * additions. Assumes input is already field-encoded using field_enc, and
 * returns output that is still field-encoded. Uses 5-bit window NAF
 * method (algorithm 11) for scalar-point multiplication from Brown,
 * Hankerson, Lopez, Menezes. Software Implementation of the NIST Elliptic
 * Curves Over Prime Fields. */
mp_err
ec_GFp_pt_mul_jm_wNAF(const mp_int *n, const mp_int *px, const mp_int *py,
                                          mp_int *rx, mp_int *ry, const ECGroup *group,
                                          int timing)
{
        mp_err res = MP_OKAY;
        mp_int precomp[16][2], rz, tpx, tpy, tpz;
        mp_int raz4, tpaz4;
        mp_int scratch[MAX_SCRATCH];
        signed char *naf = NULL;
        int i, orderBitSize;
        int numDoubles, numAdds, extraDoubles, extraAdds;

        MP_DIGITS(&rz) = 0;
        MP_DIGITS(&raz4) = 0;
        MP_DIGITS(&tpx) = 0;
        MP_DIGITS(&tpy) = 0;
        MP_DIGITS(&tpz) = 0;
        MP_DIGITS(&tpaz4) = 0;
        for (i = 0; i < 16; i++) {
                MP_DIGITS(&precomp[i][0]) = 0;
                MP_DIGITS(&precomp[i][1]) = 0;
        }
        for (i = 0; i < MAX_SCRATCH; i++) {
                MP_DIGITS(&scratch[i]) = 0;
        }

        ARGCHK(group != NULL, MP_BADARG);
        ARGCHK((n != NULL) && (px != NULL) && (py != NULL), MP_BADARG);

        /* initialize precomputation table */
        MP_CHECKOK(mp_init(&tpx, FLAG(n)));
        MP_CHECKOK(mp_init(&tpy, FLAG(n)));
        MP_CHECKOK(mp_init(&tpz, FLAG(n)));
        MP_CHECKOK(mp_init(&tpaz4, FLAG(n)));
        MP_CHECKOK(mp_init(&rz, FLAG(n)));
        MP_CHECKOK(mp_init(&raz4, FLAG(n)));

        for (i = 0; i < 16; i++) {
                MP_CHECKOK(mp_init(&precomp[i][0], FLAG(n)));
                MP_CHECKOK(mp_init(&precomp[i][1], FLAG(n)));
        }
        for (i = 0; i < MAX_SCRATCH; i++) {
                MP_CHECKOK(mp_init(&scratch[i], FLAG(n)));
        }

        /* Set out[8] = P */
        MP_CHECKOK(mp_copy(px, &precomp[8][0]));
        MP_CHECKOK(mp_copy(py, &precomp[8][1]));

        /* Set (tpx, tpy) = 2P */
        MP_CHECKOK(group->
                           point_dbl(&precomp[8][0], &precomp[8][1], &tpx, &tpy,
                                                 group));

        /* Set 3P, 5P, ..., 15P */
        for (i = 8; i < 15; i++) {
                MP_CHECKOK(group->
                                   point_add(&precomp[i][0], &precomp[i][1], &tpx, &tpy,
                                                         &precomp[i + 1][0], &precomp[i + 1][1],
                                                         group));
        }

        /* Set -15P, -13P, ..., -P */
        for (i = 0; i < 8; i++) {
                MP_CHECKOK(mp_copy(&precomp[15 - i][0], &precomp[i][0]));
                MP_CHECKOK(group->meth->
                                   field_neg(&precomp[15 - i][1], &precomp[i][1],
                                                         group->meth));
        }

        /* R = inf */
        MP_CHECKOK(ec_GFp_pt_set_inf_jac(rx, ry, &rz));

        orderBitSize = mpl_significant_bits(&group->order);

        /* Allocate memory for NAF */
#ifdef _KERNEL
        naf = (signed char *) kmem_alloc((orderBitSize + 1), FLAG(n));
#else
        naf = (signed char *) malloc(sizeof(signed char) * (orderBitSize + 1));
        if (naf == NULL) {
                res = MP_MEM;
                goto CLEANUP;
        }
#endif

        /* Compute 5NAF */
        ec_compute_wNAF(naf, orderBitSize, n, 5);

        numAdds = 0;
        numDoubles = orderBitSize;
        /* wNAF method */
        for (i = orderBitSize; i >= 0; i--) {

                if (ec_GFp_pt_is_inf_jac(rx, ry, &rz) == MP_YES) {
                  numDoubles--;
                }

                /* R = 2R */
                ec_GFp_pt_dbl_jm(rx, ry, &rz, &raz4, rx, ry, &rz,
                                             &raz4, scratch, group);

                if (naf[i] != 0) {
                        ec_GFp_pt_add_jm_aff(rx, ry, &rz, &raz4,
                                                                 &precomp[(naf[i] + 15) / 2][0],
                                                                 &precomp[(naf[i] + 15) / 2][1], rx, ry,
                                                                 &rz, &raz4, scratch, group);
                        numAdds++;
                }
        }

        /* extra operations to make timing less dependent on secrets */
        if (timing) {
                /* low-order bit of timing argument contains no entropy */
                timing >>= 1;

                MP_CHECKOK(ec_GFp_pt_set_inf_jac(&tpx, &tpy, &tpz));
                mp_zero(&tpaz4);

                /* Set the temp value to a non-infinite point */
                ec_GFp_pt_add_jm_aff(&tpx, &tpy, &tpz, &tpaz4,
                                                                 &precomp[8][0],
                                                                 &precomp[8][1], &tpx, &tpy,
                                                                 &tpz, &tpaz4, scratch, group);

                /* two bits of extra adds */
                extraAdds = timing & 0x3;
                timing >>= 2;
                /* Window size is 5, so the maximum number of additions is ceil(orderBitSize/5) */
                /* This is the same as (orderBitSize + 4) / 5 */
                for(i = numAdds; i <= (orderBitSize + 4) / 5 + extraAdds; i++) {
                        ec_GFp_pt_add_jm_aff(&tpx, &tpy, &tpz, &tpaz4,
                                                                 &precomp[9 + (i % 3)][0],
                                                                 &precomp[9 + (i % 3)][1], &tpx, &tpy,
                                                                 &tpz, &tpaz4, scratch, group);
                }

                /* two bits of extra doubles */
                extraDoubles = timing & 0x3;
                timing >>= 2;
                for(i = numDoubles; i <= orderBitSize + extraDoubles; i++) {
                        ec_GFp_pt_dbl_jm(&tpx, &tpy, &tpz, &tpaz4, &tpx, &tpy, &tpz,
                                             &tpaz4, scratch, group);
                }

        }

        /* convert result S to affine coordinates */
        MP_CHECKOK(ec_GFp_pt_jac2aff(rx, ry, &rz, rx, ry, group));

  CLEANUP:
        for (i = 0; i < MAX_SCRATCH; i++) {
                mp_clear(&scratch[i]);
        }
        for (i = 0; i < 16; i++) {
                mp_clear(&precomp[i][0]);
                mp_clear(&precomp[i][1]);
        }
        mp_clear(&tpx);
        mp_clear(&tpy);
        mp_clear(&tpz);
        mp_clear(&tpaz4);
        mp_clear(&rz);
        mp_clear(&raz4);
#ifdef _KERNEL
        kmem_free(naf, (orderBitSize + 1));
#else
        free(naf);
#endif
        return res;
}
