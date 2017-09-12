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
 * The Original Code is the Multi-precision Binary Polynomial Arithmetic Library.
 *
 * The Initial Developer of the Original Code is
 * Sun Microsystems, Inc.
 * Portions created by the Initial Developer are Copyright (C) 2003
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Sheueling Chang Shantz <sheueling.chang@sun.com> and
 *   Douglas Stebila <douglas@stebila.ca> of Sun Laboratories.
 *
 *********************************************************************** */

#ifndef _MP_GF2M_H_
#define _MP_GF2M_H_

#include "mpi.h"

mp_err mp_badd(const mp_int *a, const mp_int *b, mp_int *c);
mp_err mp_bmul(const mp_int *a, const mp_int *b, mp_int *c);

/* For modular arithmetic, the irreducible polynomial f(t) is represented
 * as an array of int[], where f(t) is of the form:
 *     f(t) = t^p[0] + t^p[1] + ... + t^p[k]
 * where m = p[0] > p[1] > ... > p[k] = 0.
 */
mp_err mp_bmod(const mp_int *a, const unsigned int p[], mp_int *r);
mp_err mp_bmulmod(const mp_int *a, const mp_int *b, const unsigned int p[],
    mp_int *r);
mp_err mp_bsqrmod(const mp_int *a, const unsigned int p[], mp_int *r);
mp_err mp_bdivmod(const mp_int *y, const mp_int *x, const mp_int *pp,
    const unsigned int p[], mp_int *r);

int mp_bpoly2arr(const mp_int *a, unsigned int p[], int max);
mp_err mp_barr2poly(const unsigned int p[], mp_int *a);

#endif /* _MP_GF2M_H_ */
