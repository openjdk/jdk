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
 * The Original Code is the elliptic curve math library.
 *
 * The Initial Developer of the Original Code is
 * Sun Microsystems, Inc.
 * Portions created by the Initial Developer are Copyright (C) 2003
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Stephen Fung <fungstep@hotmail.com>, Sun Microsystems Laboratories
 *
 *********************************************************************** */

#include "ecl-priv.h"

/* Returns 2^e as an integer. This is meant to be used for small powers of
 * two. */
int
ec_twoTo(int e)
{
        int a = 1;
        int i;

        for (i = 0; i < e; i++) {
                a *= 2;
        }
        return a;
}

/* Computes the windowed non-adjacent-form (NAF) of a scalar. Out should
 * be an array of signed char's to output to, bitsize should be the number
 * of bits of out, in is the original scalar, and w is the window size.
 * NAF is discussed in the paper: D. Hankerson, J. Hernandez and A.
 * Menezes, "Software implementation of elliptic curve cryptography over
 * binary fields", Proc. CHES 2000. */
mp_err
ec_compute_wNAF(signed char *out, int bitsize, const mp_int *in, int w)
{
        mp_int k;
        mp_err res = MP_OKAY;
        int i, twowm1, mask;

        twowm1 = ec_twoTo(w - 1);
        mask = 2 * twowm1 - 1;

        MP_DIGITS(&k) = 0;
        MP_CHECKOK(mp_init_copy(&k, in));

        i = 0;
        /* Compute wNAF form */
        while (mp_cmp_z(&k) > 0) {
                if (mp_isodd(&k)) {
                        out[i] = MP_DIGIT(&k, 0) & mask;
                        if (out[i] >= twowm1)
                                out[i] -= 2 * twowm1;

                        /* Subtract off out[i].  Note mp_sub_d only works with
                         * unsigned digits */
                        if (out[i] >= 0) {
                                mp_sub_d(&k, out[i], &k);
                        } else {
                                mp_add_d(&k, -(out[i]), &k);
                        }
                } else {
                        out[i] = 0;
                }
                mp_div_2(&k, &k);
                i++;
        }
        /* Zero out the remaining elements of the out array. */
        for (; i < bitsize + 1; i++) {
                out[i] = 0;
        }
  CLEANUP:
        mp_clear(&k);
        return res;

}
