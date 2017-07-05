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

#ifndef _ECP_H
#define _ECP_H

#include "ecl-priv.h"

/* Checks if point P(px, py) is at infinity.  Uses affine coordinates. */
mp_err ec_GFp_pt_is_inf_aff(const mp_int *px, const mp_int *py);

/* Sets P(px, py) to be the point at infinity.  Uses affine coordinates. */
mp_err ec_GFp_pt_set_inf_aff(mp_int *px, mp_int *py);

/* Computes R = P + Q where R is (rx, ry), P is (px, py) and Q is (qx,
 * qy). Uses affine coordinates. */
mp_err ec_GFp_pt_add_aff(const mp_int *px, const mp_int *py,
                                                 const mp_int *qx, const mp_int *qy, mp_int *rx,
                                                 mp_int *ry, const ECGroup *group);

/* Computes R = P - Q.  Uses affine coordinates. */
mp_err ec_GFp_pt_sub_aff(const mp_int *px, const mp_int *py,
                                                 const mp_int *qx, const mp_int *qy, mp_int *rx,
                                                 mp_int *ry, const ECGroup *group);

/* Computes R = 2P.  Uses affine coordinates. */
mp_err ec_GFp_pt_dbl_aff(const mp_int *px, const mp_int *py, mp_int *rx,
                                                 mp_int *ry, const ECGroup *group);

/* Validates a point on a GFp curve. */
mp_err ec_GFp_validate_point(const mp_int *px, const mp_int *py, const ECGroup *group);

#ifdef ECL_ENABLE_GFP_PT_MUL_AFF
/* Computes R = nP where R is (rx, ry) and P is (px, py). The parameters
 * a, b and p are the elliptic curve coefficients and the prime that
 * determines the field GFp.  Uses affine coordinates. */
mp_err ec_GFp_pt_mul_aff(const mp_int *n, const mp_int *px,
                                                 const mp_int *py, mp_int *rx, mp_int *ry,
                                                 const ECGroup *group);
#endif

/* Converts a point P(px, py) from affine coordinates to Jacobian
 * projective coordinates R(rx, ry, rz). */
mp_err ec_GFp_pt_aff2jac(const mp_int *px, const mp_int *py, mp_int *rx,
                                                 mp_int *ry, mp_int *rz, const ECGroup *group);

/* Converts a point P(px, py, pz) from Jacobian projective coordinates to
 * affine coordinates R(rx, ry). */
mp_err ec_GFp_pt_jac2aff(const mp_int *px, const mp_int *py,
                                                 const mp_int *pz, mp_int *rx, mp_int *ry,
                                                 const ECGroup *group);

/* Checks if point P(px, py, pz) is at infinity.  Uses Jacobian
 * coordinates. */
mp_err ec_GFp_pt_is_inf_jac(const mp_int *px, const mp_int *py,
                                                        const mp_int *pz);

/* Sets P(px, py, pz) to be the point at infinity.  Uses Jacobian
 * coordinates. */
mp_err ec_GFp_pt_set_inf_jac(mp_int *px, mp_int *py, mp_int *pz);

/* Computes R = P + Q where R is (rx, ry, rz), P is (px, py, pz) and Q is
 * (qx, qy, qz).  Uses Jacobian coordinates. */
mp_err ec_GFp_pt_add_jac_aff(const mp_int *px, const mp_int *py,
                                                         const mp_int *pz, const mp_int *qx,
                                                         const mp_int *qy, mp_int *rx, mp_int *ry,
                                                         mp_int *rz, const ECGroup *group);

/* Computes R = 2P.  Uses Jacobian coordinates. */
mp_err ec_GFp_pt_dbl_jac(const mp_int *px, const mp_int *py,
                                                 const mp_int *pz, mp_int *rx, mp_int *ry,
                                                 mp_int *rz, const ECGroup *group);

#ifdef ECL_ENABLE_GFP_PT_MUL_JAC
/* Computes R = nP where R is (rx, ry) and P is (px, py). The parameters
 * a, b and p are the elliptic curve coefficients and the prime that
 * determines the field GFp.  Uses Jacobian coordinates. */
mp_err ec_GFp_pt_mul_jac(const mp_int *n, const mp_int *px,
                                                 const mp_int *py, mp_int *rx, mp_int *ry,
                                                 const ECGroup *group);
#endif

/* Computes R(x, y) = k1 * G + k2 * P(x, y), where G is the generator
 * (base point) of the group of points on the elliptic curve. Allows k1 =
 * NULL or { k2, P } = NULL.  Implemented using mixed Jacobian-affine
 * coordinates. Input and output values are assumed to be NOT
 * field-encoded and are in affine form. */
mp_err
 ec_GFp_pts_mul_jac(const mp_int *k1, const mp_int *k2, const mp_int *px,
                                        const mp_int *py, mp_int *rx, mp_int *ry,
                                        const ECGroup *group);

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
                                           mp_int *rx, mp_int *ry, const ECGroup *group);

#endif /* _ECP_H */
