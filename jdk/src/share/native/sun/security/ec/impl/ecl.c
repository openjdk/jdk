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

#include "mpi.h"
#include "mplogic.h"
#include "ecl.h"
#include "ecl-priv.h"
#include "ec2.h"
#include "ecp.h"
#ifndef _KERNEL
#include <stdlib.h>
#include <string.h>
#endif

/* Allocate memory for a new ECGroup object. */
ECGroup *
ECGroup_new(int kmflag)
{
        mp_err res = MP_OKAY;
        ECGroup *group;
#ifdef _KERNEL
        group = (ECGroup *) kmem_alloc(sizeof(ECGroup), kmflag);
#else
        group = (ECGroup *) malloc(sizeof(ECGroup));
#endif
        if (group == NULL)
                return NULL;
        group->constructed = MP_YES;
        group->meth = NULL;
        group->text = NULL;
        MP_DIGITS(&group->curvea) = 0;
        MP_DIGITS(&group->curveb) = 0;
        MP_DIGITS(&group->genx) = 0;
        MP_DIGITS(&group->geny) = 0;
        MP_DIGITS(&group->order) = 0;
        group->base_point_mul = NULL;
        group->points_mul = NULL;
        group->validate_point = NULL;
        group->extra1 = NULL;
        group->extra2 = NULL;
        group->extra_free = NULL;
        MP_CHECKOK(mp_init(&group->curvea, kmflag));
        MP_CHECKOK(mp_init(&group->curveb, kmflag));
        MP_CHECKOK(mp_init(&group->genx, kmflag));
        MP_CHECKOK(mp_init(&group->geny, kmflag));
        MP_CHECKOK(mp_init(&group->order, kmflag));

  CLEANUP:
        if (res != MP_OKAY) {
                ECGroup_free(group);
                return NULL;
        }
        return group;
}

/* Construct a generic ECGroup for elliptic curves over prime fields. */
ECGroup *
ECGroup_consGFp(const mp_int *irr, const mp_int *curvea,
                                const mp_int *curveb, const mp_int *genx,
                                const mp_int *geny, const mp_int *order, int cofactor)
{
        mp_err res = MP_OKAY;
        ECGroup *group = NULL;

        group = ECGroup_new(FLAG(irr));
        if (group == NULL)
                return NULL;

        group->meth = GFMethod_consGFp(irr);
        if (group->meth == NULL) {
                res = MP_MEM;
                goto CLEANUP;
        }
        MP_CHECKOK(mp_copy(curvea, &group->curvea));
        MP_CHECKOK(mp_copy(curveb, &group->curveb));
        MP_CHECKOK(mp_copy(genx, &group->genx));
        MP_CHECKOK(mp_copy(geny, &group->geny));
        MP_CHECKOK(mp_copy(order, &group->order));
        group->cofactor = cofactor;
        group->point_add = &ec_GFp_pt_add_aff;
        group->point_sub = &ec_GFp_pt_sub_aff;
        group->point_dbl = &ec_GFp_pt_dbl_aff;
        group->point_mul = &ec_GFp_pt_mul_jm_wNAF;
        group->base_point_mul = NULL;
        group->points_mul = &ec_GFp_pts_mul_jac;
        group->validate_point = &ec_GFp_validate_point;

  CLEANUP:
        if (res != MP_OKAY) {
                ECGroup_free(group);
                return NULL;
        }
        return group;
}

/* Construct a generic ECGroup for elliptic curves over prime fields with
 * field arithmetic implemented in Montgomery coordinates. */
ECGroup *
ECGroup_consGFp_mont(const mp_int *irr, const mp_int *curvea,
                                         const mp_int *curveb, const mp_int *genx,
                                         const mp_int *geny, const mp_int *order, int cofactor)
{
        mp_err res = MP_OKAY;
        ECGroup *group = NULL;

        group = ECGroup_new(FLAG(irr));
        if (group == NULL)
                return NULL;

        group->meth = GFMethod_consGFp_mont(irr);
        if (group->meth == NULL) {
                res = MP_MEM;
                goto CLEANUP;
        }
        MP_CHECKOK(group->meth->
                           field_enc(curvea, &group->curvea, group->meth));
        MP_CHECKOK(group->meth->
                           field_enc(curveb, &group->curveb, group->meth));
        MP_CHECKOK(group->meth->field_enc(genx, &group->genx, group->meth));
        MP_CHECKOK(group->meth->field_enc(geny, &group->geny, group->meth));
        MP_CHECKOK(mp_copy(order, &group->order));
        group->cofactor = cofactor;
        group->point_add = &ec_GFp_pt_add_aff;
        group->point_sub = &ec_GFp_pt_sub_aff;
        group->point_dbl = &ec_GFp_pt_dbl_aff;
        group->point_mul = &ec_GFp_pt_mul_jm_wNAF;
        group->base_point_mul = NULL;
        group->points_mul = &ec_GFp_pts_mul_jac;
        group->validate_point = &ec_GFp_validate_point;

  CLEANUP:
        if (res != MP_OKAY) {
                ECGroup_free(group);
                return NULL;
        }
        return group;
}

#ifdef NSS_ECC_MORE_THAN_SUITE_B
/* Construct a generic ECGroup for elliptic curves over binary polynomial
 * fields. */
ECGroup *
ECGroup_consGF2m(const mp_int *irr, const unsigned int irr_arr[5],
                                 const mp_int *curvea, const mp_int *curveb,
                                 const mp_int *genx, const mp_int *geny,
                                 const mp_int *order, int cofactor)
{
        mp_err res = MP_OKAY;
        ECGroup *group = NULL;

        group = ECGroup_new(FLAG(irr));
        if (group == NULL)
                return NULL;

        group->meth = GFMethod_consGF2m(irr, irr_arr);
        if (group->meth == NULL) {
                res = MP_MEM;
                goto CLEANUP;
        }
        MP_CHECKOK(mp_copy(curvea, &group->curvea));
        MP_CHECKOK(mp_copy(curveb, &group->curveb));
        MP_CHECKOK(mp_copy(genx, &group->genx));
        MP_CHECKOK(mp_copy(geny, &group->geny));
        MP_CHECKOK(mp_copy(order, &group->order));
        group->cofactor = cofactor;
        group->point_add = &ec_GF2m_pt_add_aff;
        group->point_sub = &ec_GF2m_pt_sub_aff;
        group->point_dbl = &ec_GF2m_pt_dbl_aff;
        group->point_mul = &ec_GF2m_pt_mul_mont;
        group->base_point_mul = NULL;
        group->points_mul = &ec_pts_mul_basic;
        group->validate_point = &ec_GF2m_validate_point;

  CLEANUP:
        if (res != MP_OKAY) {
                ECGroup_free(group);
                return NULL;
        }
        return group;
}
#endif

/* Construct ECGroup from hex parameters and name, if any. Called by
 * ECGroup_fromHex and ECGroup_fromName. */
ECGroup *
ecgroup_fromNameAndHex(const ECCurveName name,
                                   const ECCurveParams * params, int kmflag)
{
        mp_int irr, curvea, curveb, genx, geny, order;
        int bits;
        ECGroup *group = NULL;
        mp_err res = MP_OKAY;

        /* initialize values */
        MP_DIGITS(&irr) = 0;
        MP_DIGITS(&curvea) = 0;
        MP_DIGITS(&curveb) = 0;
        MP_DIGITS(&genx) = 0;
        MP_DIGITS(&geny) = 0;
        MP_DIGITS(&order) = 0;
        MP_CHECKOK(mp_init(&irr, kmflag));
        MP_CHECKOK(mp_init(&curvea, kmflag));
        MP_CHECKOK(mp_init(&curveb, kmflag));
        MP_CHECKOK(mp_init(&genx, kmflag));
        MP_CHECKOK(mp_init(&geny, kmflag));
        MP_CHECKOK(mp_init(&order, kmflag));
        MP_CHECKOK(mp_read_radix(&irr, params->irr, 16));
        MP_CHECKOK(mp_read_radix(&curvea, params->curvea, 16));
        MP_CHECKOK(mp_read_radix(&curveb, params->curveb, 16));
        MP_CHECKOK(mp_read_radix(&genx, params->genx, 16));
        MP_CHECKOK(mp_read_radix(&geny, params->geny, 16));
        MP_CHECKOK(mp_read_radix(&order, params->order, 16));

        /* determine number of bits */
        bits = mpl_significant_bits(&irr) - 1;
        if (bits < MP_OKAY) {
                res = bits;
                goto CLEANUP;
        }

        /* determine which optimizations (if any) to use */
        if (params->field == ECField_GFp) {
#ifdef NSS_ECC_MORE_THAN_SUITE_B
            switch (name) {
#ifdef ECL_USE_FP
                case ECCurve_SECG_PRIME_160R1:
                        group =
                                ECGroup_consGFp(&irr, &curvea, &curveb, &genx, &geny,
                                                                &order, params->cofactor);
                        if (group == NULL) { res = MP_UNDEF; goto CLEANUP; }
                        MP_CHECKOK(ec_group_set_secp160r1_fp(group));
                        break;
#endif
                case ECCurve_SECG_PRIME_192R1:
#ifdef ECL_USE_FP
                        group =
                                ECGroup_consGFp(&irr, &curvea, &curveb, &genx, &geny,
                                                                &order, params->cofactor);
                        if (group == NULL) { res = MP_UNDEF; goto CLEANUP; }
                        MP_CHECKOK(ec_group_set_nistp192_fp(group));
#else
                        group =
                                ECGroup_consGFp(&irr, &curvea, &curveb, &genx, &geny,
                                                                &order, params->cofactor);
                        if (group == NULL) { res = MP_UNDEF; goto CLEANUP; }
                        MP_CHECKOK(ec_group_set_gfp192(group, name));
#endif
                        break;
                case ECCurve_SECG_PRIME_224R1:
#ifdef ECL_USE_FP
                        group =
                                ECGroup_consGFp(&irr, &curvea, &curveb, &genx, &geny,
                                                                &order, params->cofactor);
                        if (group == NULL) { res = MP_UNDEF; goto CLEANUP; }
                        MP_CHECKOK(ec_group_set_nistp224_fp(group));
#else
                        group =
                                ECGroup_consGFp(&irr, &curvea, &curveb, &genx, &geny,
                                                                &order, params->cofactor);
                        if (group == NULL) { res = MP_UNDEF; goto CLEANUP; }
                        MP_CHECKOK(ec_group_set_gfp224(group, name));
#endif
                        break;
                case ECCurve_SECG_PRIME_256R1:
                        group =
                                ECGroup_consGFp(&irr, &curvea, &curveb, &genx, &geny,
                                                                &order, params->cofactor);
                        if (group == NULL) { res = MP_UNDEF; goto CLEANUP; }
                        MP_CHECKOK(ec_group_set_gfp256(group, name));
                        break;
                case ECCurve_SECG_PRIME_521R1:
                        group =
                                ECGroup_consGFp(&irr, &curvea, &curveb, &genx, &geny,
                                                                &order, params->cofactor);
                        if (group == NULL) { res = MP_UNDEF; goto CLEANUP; }
                        MP_CHECKOK(ec_group_set_gfp521(group, name));
                        break;
                default:
                        /* use generic arithmetic */
#endif
                        group =
                                ECGroup_consGFp_mont(&irr, &curvea, &curveb, &genx, &geny,
                                                                         &order, params->cofactor);
                        if (group == NULL) { res = MP_UNDEF; goto CLEANUP; }
#ifdef NSS_ECC_MORE_THAN_SUITE_B
                }
        } else if (params->field == ECField_GF2m) {
                group = ECGroup_consGF2m(&irr, NULL, &curvea, &curveb, &genx, &geny, &order, params->cofactor);
                if (group == NULL) { res = MP_UNDEF; goto CLEANUP; }
                if ((name == ECCurve_NIST_K163) ||
                    (name == ECCurve_NIST_B163) ||
                    (name == ECCurve_SECG_CHAR2_163R1)) {
                        MP_CHECKOK(ec_group_set_gf2m163(group, name));
                } else if ((name == ECCurve_SECG_CHAR2_193R1) ||
                           (name == ECCurve_SECG_CHAR2_193R2)) {
                        MP_CHECKOK(ec_group_set_gf2m193(group, name));
                } else if ((name == ECCurve_NIST_K233) ||
                           (name == ECCurve_NIST_B233)) {
                        MP_CHECKOK(ec_group_set_gf2m233(group, name));
                }
#endif
        } else {
                res = MP_UNDEF;
                goto CLEANUP;
        }

        /* set name, if any */
        if ((group != NULL) && (params->text != NULL)) {
#ifdef _KERNEL
                int n = strlen(params->text) + 1;

                group->text = kmem_alloc(n, kmflag);
                if (group->text == NULL) {
                        res = MP_MEM;
                        goto CLEANUP;
                }
                bcopy(params->text, group->text, n);
                group->text_len = n;
#else
                group->text = strdup(params->text);
                if (group->text == NULL) {
                        res = MP_MEM;
                }
#endif
        }

  CLEANUP:
        mp_clear(&irr);
        mp_clear(&curvea);
        mp_clear(&curveb);
        mp_clear(&genx);
        mp_clear(&geny);
        mp_clear(&order);
        if (res != MP_OKAY) {
                ECGroup_free(group);
                return NULL;
        }
        return group;
}

/* Construct ECGroup from hexadecimal representations of parameters. */
ECGroup *
ECGroup_fromHex(const ECCurveParams * params, int kmflag)
{
        return ecgroup_fromNameAndHex(ECCurve_noName, params, kmflag);
}

/* Construct ECGroup from named parameters. */
ECGroup *
ECGroup_fromName(const ECCurveName name, int kmflag)
{
        ECGroup *group = NULL;
        ECCurveParams *params = NULL;
        mp_err res = MP_OKAY;

        params = EC_GetNamedCurveParams(name, kmflag);
        if (params == NULL) {
                res = MP_UNDEF;
                goto CLEANUP;
        }

        /* construct actual group */
        group = ecgroup_fromNameAndHex(name, params, kmflag);
        if (group == NULL) {
                res = MP_UNDEF;
                goto CLEANUP;
        }

  CLEANUP:
        EC_FreeCurveParams(params);
        if (res != MP_OKAY) {
                ECGroup_free(group);
                return NULL;
        }
        return group;
}

/* Validates an EC public key as described in Section 5.2.2 of X9.62. */
mp_err ECPoint_validate(const ECGroup *group, const mp_int *px, const
                                        mp_int *py)
{
    /* 1: Verify that publicValue is not the point at infinity */
    /* 2: Verify that the coordinates of publicValue are elements
     *    of the field.
     */
    /* 3: Verify that publicValue is on the curve. */
    /* 4: Verify that the order of the curve times the publicValue
     *    is the point at infinity.
     */
        return group->validate_point(px, py, group);
}

/* Free the memory allocated (if any) to an ECGroup object. */
void
ECGroup_free(ECGroup *group)
{
        if (group == NULL)
                return;
        GFMethod_free(group->meth);
        if (group->constructed == MP_NO)
                return;
        mp_clear(&group->curvea);
        mp_clear(&group->curveb);
        mp_clear(&group->genx);
        mp_clear(&group->geny);
        mp_clear(&group->order);
        if (group->text != NULL)
#ifdef _KERNEL
                kmem_free(group->text, group->text_len);
#else
                free(group->text);
#endif
        if (group->extra_free != NULL)
                group->extra_free(group);
#ifdef _KERNEL
        kmem_free(group, sizeof (ECGroup));
#else
        free(group);
#endif
}
