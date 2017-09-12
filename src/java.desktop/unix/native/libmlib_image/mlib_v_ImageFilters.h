/*
 * Copyright (c) 1998, 2003, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */


#ifndef __MLIB_V_IMAGEFILTERS_H
#define __MLIB_V_IMAGEFILTERS_H


#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

/*
 *    These tables are used by VIS versions
 *    of the following functions:
 *      mlib_ImageRotate(Index)
 *      mlib_ImageAffine(Index)
 *      mlib_ImageZoom(Index)
 *      mlib_ImageGridWarp
 *      mlib_ImagePolynomialWarp
 */

#include "mlib_image.h"

#if defined (__INIT_TABLE)

#pragma align 8 (mlib_filters_u8_bl)
#pragma align 8 (mlib_filters_u8_bc)
#pragma align 8 (mlib_filters_u8_bc2)
#pragma align 8 (mlib_filters_u8_bc_3)
#pragma align 8 (mlib_filters_u8_bc2_3)
#pragma align 8 (mlib_filters_u8_bc_4)
#pragma align 8 (mlib_filters_u8_bc2_4)
#pragma align 8 (mlib_filters_s16_bc)
#pragma align 8 (mlib_filters_s16_bc2)
#pragma align 8 (mlib_filters_s16_bc_3)
#pragma align 8 (mlib_filters_s16_bc2_3)
#pragma align 8 (mlib_filters_s16_bc_4)
#pragma align 8 (mlib_filters_s16_bc2_4)

#endif /* defined (__INIT_TABLE) */

extern const mlib_s16 mlib_filters_u8_bl[];
extern const mlib_s16 mlib_filters_u8_bc[];
extern const mlib_s16 mlib_filters_u8_bc2[];
extern const mlib_s16 mlib_filters_u8_bc_3[];
extern const mlib_s16 mlib_filters_u8_bc2_3[];
extern const mlib_s16 mlib_filters_u8_bc_4[];
extern const mlib_s16 mlib_filters_u8_bc2_4[];
extern const mlib_s16 mlib_filters_s16_bc[];
extern const mlib_s16 mlib_filters_s16_bc2[];
extern const mlib_s16 mlib_filters_s16_bc_3[];
extern const mlib_s16 mlib_filters_s16_bc2_3[];
extern const mlib_s16 mlib_filters_s16_bc_4[];
extern const mlib_s16 mlib_filters_s16_bc2_4[];

#ifdef __cplusplus
}
#endif /* __cplusplus */
#endif /* __MLIB_V_IMAGEFILTERS_H */
