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

#ifndef _MP_GF2M_PRIV_H_
#define _MP_GF2M_PRIV_H_

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
