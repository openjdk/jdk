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
 * Last Modified Date from the Original Code: May 2017
 *********************************************************************** */

#ifndef _ECL_H
#define _ECL_H

/* Although this is not an exported header file, code which uses elliptic
 * curve point operations will need to include it. */

#include "ecl-exp.h"
#include "mpi.h"

struct ECGroupStr;
typedef struct ECGroupStr ECGroup;

/* Construct ECGroup from hexadecimal representations of parameters. */
ECGroup *ECGroup_fromHex(const ECCurveParams * params, int kmflag);

/* Construct ECGroup from named parameters. */
ECGroup *ECGroup_fromName(const ECCurveName name, int kmflag);

/* Free an allocated ECGroup. */
void ECGroup_free(ECGroup *group);

/* Construct ECCurveParams from an ECCurveName */
ECCurveParams *EC_GetNamedCurveParams(const ECCurveName name, int kmflag);

/* Duplicates an ECCurveParams */
ECCurveParams *ECCurveParams_dup(const ECCurveParams * params, int kmflag);

/* Free an allocated ECCurveParams */
void EC_FreeCurveParams(ECCurveParams * params);

/* Elliptic curve scalar-point multiplication. Computes Q(x, y) = k * P(x,
 * y).  If x, y = NULL, then P is assumed to be the generator (base point)
 * of the group of points on the elliptic curve. Input and output values
 * are assumed to be NOT field-encoded. */
mp_err ECPoint_mul(const ECGroup *group, const mp_int *k, const mp_int *px,
                                   const mp_int *py, mp_int *qx, mp_int *qy,
                                   int timing);

/* Elliptic curve scalar-point multiplication. Computes Q(x, y) = k1 * G +
 * k2 * P(x, y), where G is the generator (base point) of the group of
 * points on the elliptic curve. Input and output values are assumed to
 * be NOT field-encoded. */
mp_err ECPoints_mul(const ECGroup *group, const mp_int *k1,
                                        const mp_int *k2, const mp_int *px, const mp_int *py,
                                        mp_int *qx, mp_int *qy, int timing);

/* Validates an EC public key as described in Section 5.2.2 of X9.62.
 * Returns MP_YES if the public key is valid, MP_NO if the public key
 * is invalid, or an error code if the validation could not be
 * performed. */
mp_err ECPoint_validate(const ECGroup *group, const mp_int *px, const
                                        mp_int *py);

#endif /* _ECL_H */
