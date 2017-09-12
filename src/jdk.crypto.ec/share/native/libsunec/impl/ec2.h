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
 * The Original Code is the elliptic curve math library for binary polynomial field curves.
 *
 * The Initial Developer of the Original Code is
 * Sun Microsystems, Inc.
 * Portions created by the Initial Developer are Copyright (C) 2003
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Douglas Stebila <douglas@stebila.ca>, Sun Microsystems Laboratories
 *
 * Last Modified Date from the Original Code: May 2017
 *********************************************************************** */

#ifndef _EC2_H
#define _EC2_H

#include "ecl-priv.h"

/* Checks if point P(px, py) is at infinity.  Uses affine coordinates. */
mp_err ec_GF2m_pt_is_inf_aff(const mp_int *px, const mp_int *py);

/* Sets P(px, py) to be the point at infinity.  Uses affine coordinates. */
mp_err ec_GF2m_pt_set_inf_aff(mp_int *px, mp_int *py);

/* Computes R = P + Q where R is (rx, ry), P is (px, py) and Q is (qx,
 * qy). Uses affine coordinates. */
mp_err ec_GF2m_pt_add_aff(const mp_int *px, const mp_int *py,
                                                  const mp_int *qx, const mp_int *qy, mp_int *rx,
                                                  mp_int *ry, const ECGroup *group);

/* Computes R = P - Q.  Uses affine coordinates. */
mp_err ec_GF2m_pt_sub_aff(const mp_int *px, const mp_int *py,
                                                  const mp_int *qx, const mp_int *qy, mp_int *rx,
                                                  mp_int *ry, const ECGroup *group);

/* Computes R = 2P.  Uses affine coordinates. */
mp_err ec_GF2m_pt_dbl_aff(const mp_int *px, const mp_int *py, mp_int *rx,
                                                  mp_int *ry, const ECGroup *group);

/* Validates a point on a GF2m curve. */
mp_err ec_GF2m_validate_point(const mp_int *px, const mp_int *py, const ECGroup *group);

/* by default, this routine is unused and thus doesn't need to be compiled */
#ifdef ECL_ENABLE_GF2M_PT_MUL_AFF
/* Computes R = nP where R is (rx, ry) and P is (px, py). The parameters
 * a, b and p are the elliptic curve coefficients and the irreducible that
 * determines the field GF2m.  Uses affine coordinates. */
mp_err ec_GF2m_pt_mul_aff(const mp_int *n, const mp_int *px,
                                                  const mp_int *py, mp_int *rx, mp_int *ry,
                                                  const ECGroup *group);
#endif

/* Computes R = nP where R is (rx, ry) and P is (px, py). The parameters
 * a, b and p are the elliptic curve coefficients and the irreducible that
 * determines the field GF2m.  Uses Montgomery projective coordinates. */
mp_err ec_GF2m_pt_mul_mont(const mp_int *n, const mp_int *px,
                                                   const mp_int *py, mp_int *rx, mp_int *ry,
                                                   const ECGroup *group, int timing);

#ifdef ECL_ENABLE_GF2M_PROJ
/* Converts a point P(px, py) from affine coordinates to projective
 * coordinates R(rx, ry, rz). */
mp_err ec_GF2m_pt_aff2proj(const mp_int *px, const mp_int *py, mp_int *rx,
                                                   mp_int *ry, mp_int *rz, const ECGroup *group);

/* Converts a point P(px, py, pz) from projective coordinates to affine
 * coordinates R(rx, ry). */
mp_err ec_GF2m_pt_proj2aff(const mp_int *px, const mp_int *py,
                                                   const mp_int *pz, mp_int *rx, mp_int *ry,
                                                   const ECGroup *group);

/* Checks if point P(px, py, pz) is at infinity.  Uses projective
 * coordinates. */
mp_err ec_GF2m_pt_is_inf_proj(const mp_int *px, const mp_int *py,
                                                          const mp_int *pz);

/* Sets P(px, py, pz) to be the point at infinity.  Uses projective
 * coordinates. */
mp_err ec_GF2m_pt_set_inf_proj(mp_int *px, mp_int *py, mp_int *pz);

/* Computes R = P + Q where R is (rx, ry, rz), P is (px, py, pz) and Q is
 * (qx, qy, qz).  Uses projective coordinates. */
mp_err ec_GF2m_pt_add_proj(const mp_int *px, const mp_int *py,
                                                   const mp_int *pz, const mp_int *qx,
                                                   const mp_int *qy, mp_int *rx, mp_int *ry,
                                                   mp_int *rz, const ECGroup *group);

/* Computes R = 2P.  Uses projective coordinates. */
mp_err ec_GF2m_pt_dbl_proj(const mp_int *px, const mp_int *py,
                                                   const mp_int *pz, mp_int *rx, mp_int *ry,
                                                   mp_int *rz, const ECGroup *group);

/* Computes R = nP where R is (rx, ry) and P is (px, py). The parameters
 * a, b and p are the elliptic curve coefficients and the prime that
 * determines the field GF2m.  Uses projective coordinates. */
mp_err ec_GF2m_pt_mul_proj(const mp_int *n, const mp_int *px,
                                                   const mp_int *py, mp_int *rx, mp_int *ry,
                                                   const ECGroup *group);
#endif

#endif /* _EC2_H */
