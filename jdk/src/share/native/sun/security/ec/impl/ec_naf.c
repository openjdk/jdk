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
 *   Stephen Fung <fungstep@hotmail.com>, Sun Microsystems Laboratories
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
