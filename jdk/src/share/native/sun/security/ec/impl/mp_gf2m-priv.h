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

#ifndef _MP_GF2M_PRIV_H_
#define _MP_GF2M_PRIV_H_

#pragma ident   "%Z%%M% %I%     %E% SMI"

#include "mpi-priv.h"

extern const mp_digit mp_gf2m_sqr_tb[16];

#if defined(MP_USE_UINT_DIGIT)
#define MP_DIGIT_BITS 32
#else
#define MP_DIGIT_BITS 64
#endif

/* Platform-specific macros for fast binary polynomial squaring. */
#if MP_DIGIT_BITS == 32
#define gf2m_SQR1(w) \
    mp_gf2m_sqr_tb[(w) >> 28 & 0xF] << 24 | mp_gf2m_sqr_tb[(w) >> 24 & 0xF] << 16 | \
    mp_gf2m_sqr_tb[(w) >> 20 & 0xF] <<  8 | mp_gf2m_sqr_tb[(w) >> 16 & 0xF]
#define gf2m_SQR0(w) \
    mp_gf2m_sqr_tb[(w) >> 12 & 0xF] << 24 | mp_gf2m_sqr_tb[(w) >>  8 & 0xF] << 16 | \
    mp_gf2m_sqr_tb[(w) >>  4 & 0xF] <<  8 | mp_gf2m_sqr_tb[(w)       & 0xF]
#else
#define gf2m_SQR1(w) \
    mp_gf2m_sqr_tb[(w) >> 60 & 0xF] << 56 | mp_gf2m_sqr_tb[(w) >> 56 & 0xF] << 48 | \
    mp_gf2m_sqr_tb[(w) >> 52 & 0xF] << 40 | mp_gf2m_sqr_tb[(w) >> 48 & 0xF] << 32 | \
    mp_gf2m_sqr_tb[(w) >> 44 & 0xF] << 24 | mp_gf2m_sqr_tb[(w) >> 40 & 0xF] << 16 | \
    mp_gf2m_sqr_tb[(w) >> 36 & 0xF] <<  8 | mp_gf2m_sqr_tb[(w) >> 32 & 0xF]
#define gf2m_SQR0(w) \
    mp_gf2m_sqr_tb[(w) >> 28 & 0xF] << 56 | mp_gf2m_sqr_tb[(w) >> 24 & 0xF] << 48 | \
    mp_gf2m_sqr_tb[(w) >> 20 & 0xF] << 40 | mp_gf2m_sqr_tb[(w) >> 16 & 0xF] << 32 | \
    mp_gf2m_sqr_tb[(w) >> 12 & 0xF] << 24 | mp_gf2m_sqr_tb[(w) >>  8 & 0xF] << 16 | \
    mp_gf2m_sqr_tb[(w) >>  4 & 0xF] <<  8 | mp_gf2m_sqr_tb[(w)       & 0xF]
#endif

/* Multiply two binary polynomials mp_digits a, b.
 * Result is a polynomial with degree < 2 * MP_DIGIT_BITS - 1.
 * Output in two mp_digits rh, rl.
 */
void s_bmul_1x1(mp_digit *rh, mp_digit *rl, const mp_digit a, const mp_digit b);

/* Compute xor-multiply of two binary polynomials  (a1, a0) x (b1, b0)
 * result is a binary polynomial in 4 mp_digits r[4].
 * The caller MUST ensure that r has the right amount of space allocated.
 */
void s_bmul_2x2(mp_digit *r, const mp_digit a1, const mp_digit a0, const mp_digit b1,
        const mp_digit b0);

/* Compute xor-multiply of two binary polynomials  (a2, a1, a0) x (b2, b1, b0)
 * result is a binary polynomial in 6 mp_digits r[6].
 * The caller MUST ensure that r has the right amount of space allocated.
 */
void s_bmul_3x3(mp_digit *r, const mp_digit a2, const mp_digit a1, const mp_digit a0,
        const mp_digit b2, const mp_digit b1, const mp_digit b0);

/* Compute xor-multiply of two binary polynomials  (a3, a2, a1, a0) x (b3, b2, b1, b0)
 * result is a binary polynomial in 8 mp_digits r[8].
 * The caller MUST ensure that r has the right amount of space allocated.
 */
void s_bmul_4x4(mp_digit *r, const mp_digit a3, const mp_digit a2, const mp_digit a1,
        const mp_digit a0, const mp_digit b3, const mp_digit b2, const mp_digit b1,
        const mp_digit b0);

#endif /* _MP_GF2M_PRIV_H_ */
