/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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


#ifndef __MLIB_IMAGE_LOOKUP_V_FUNC_INTENAL_H
#define __MLIB_IMAGE_LOOKUP_V_FUNC_INTENAL_H


#ifdef __cplusplus
extern "C" {
#endif

/* mlib_v_ImageLookUpS16S16Func.c */

void mlib_v_ImageLookUp_S16_S16_1(const mlib_s16 *src,
                                  mlib_s32       slb,
                                  mlib_s16       *dst,
                                  mlib_s32       dlb,
                                  mlib_s32       xsize,
                                  mlib_s32       ysize,
                                  const mlib_s16 **table);

void mlib_v_ImageLookUp_S16_S16_2(const mlib_s16 *src,
                                  mlib_s32       slb,
                                  mlib_s16       *dst,
                                  mlib_s32       dlb,
                                  mlib_s32       xsize,
                                  mlib_s32       ysize,
                                  const mlib_s16 **table);

void mlib_v_ImageLookUp_S16_S16_3(const mlib_s16 *src,
                                  mlib_s32       slb,
                                  mlib_s16       *dst,
                                  mlib_s32       dlb,
                                  mlib_s32       xsize,
                                  mlib_s32       ysize,
                                  const mlib_s16 **table);

void mlib_v_ImageLookUp_S16_S16_4(const mlib_s16 *src,
                                  mlib_s32       slb,
                                  mlib_s16       *dst,
                                  mlib_s32       dlb,
                                  mlib_s32       xsize,
                                  mlib_s32       ysize,
                                  const mlib_s16 **table);

/* mlib_v_ImageLookUpS16S32Func.c */

void mlib_v_ImageLookUp_S16_S32_1(const mlib_s16 *src,
                                  mlib_s32       slb,
                                  mlib_s32       *dst,
                                  mlib_s32       dlb,
                                  mlib_s32       xsize,
                                  mlib_s32       ysize,
                                  const mlib_s32 **table);

void mlib_v_ImageLookUp_S16_S32_2(const mlib_s16 *src,
                                  mlib_s32       slb,
                                  mlib_s32       *dst,
                                  mlib_s32       dlb,
                                  mlib_s32       xsize,
                                  mlib_s32       ysize,
                                  const mlib_s32 **table);

void mlib_v_ImageLookUp_S16_S32_3(const mlib_s16 *src,
                                  mlib_s32       slb,
                                  mlib_s32       *dst,
                                  mlib_s32       dlb,
                                  mlib_s32       xsize,
                                  mlib_s32       ysize,
                                  const mlib_s32 **table);

void mlib_v_ImageLookUp_S16_S32_4(const mlib_s16 *src,
                                  mlib_s32       slb,
                                  mlib_s32       *dst,
                                  mlib_s32       dlb,
                                  mlib_s32       xsize,
                                  mlib_s32       ysize,
                                  const mlib_s32 **table);

/* mlib_v_ImageLookUpS16U8Func.c */

void mlib_v_ImageLookUp_S16_U8_1(const mlib_s16 *src,
                                 mlib_s32       slb,
                                 mlib_u8        *dst,
                                 mlib_s32       dlb,
                                 mlib_s32       xsize,
                                 mlib_s32       ysize,
                                 const mlib_u8  **table);

void mlib_v_ImageLookUp_S16_U8_2(const mlib_s16 *src,
                                 mlib_s32       slb,
                                 mlib_u8        *dst,
                                 mlib_s32       dlb,
                                 mlib_s32       xsize,
                                 mlib_s32       ysize,
                                 const mlib_u8  **table);

void mlib_v_ImageLookUp_S16_U8_3(const mlib_s16 *src,
                                 mlib_s32       slb,
                                 mlib_u8        *dst,
                                 mlib_s32       dlb,
                                 mlib_s32       xsize,
                                 mlib_s32       ysize,
                                 const mlib_u8  **table);

void mlib_v_ImageLookUp_S16_U8_4(const mlib_s16 *src,
                                 mlib_s32       slb,
                                 mlib_u8        *dst,
                                 mlib_s32       dlb,
                                 mlib_s32       xsize,
                                 mlib_s32       ysize,
                                 const mlib_u8  **table);

/* mlib_v_ImageLookUpS32S16Func.c */

void mlib_v_ImageLookUp_S32_S16_1(const mlib_s32 *src,
                                  mlib_s32       slb,
                                  mlib_s16       *dst,
                                  mlib_s32       dlb,
                                  mlib_s32       xsize,
                                  mlib_s32       ysize,
                                  const mlib_s16 **table);

void mlib_v_ImageLookUp_S32_S16_2(const mlib_s32 *src,
                                  mlib_s32       slb,
                                  mlib_s16       *dst,
                                  mlib_s32       dlb,
                                  mlib_s32       xsize,
                                  mlib_s32       ysize,
                                  const mlib_s16 **table);

void mlib_v_ImageLookUp_S32_S16_3(const mlib_s32 *src,
                                  mlib_s32       slb,
                                  mlib_s16       *dst,
                                  mlib_s32       dlb,
                                  mlib_s32       xsize,
                                  mlib_s32       ysize,
                                  const mlib_s16 **table);

void mlib_v_ImageLookUp_S32_S16_4(const mlib_s32 *src,
                                  mlib_s32       slb,
                                  mlib_s16       *dst,
                                  mlib_s32       dlb,
                                  mlib_s32       xsize,
                                  mlib_s32       ysize,
                                  const mlib_s16 **table);

/* mlib_v_ImageLookUpS32S32Func.c */

void mlib_v_ImageLookUp_S32_S32(const mlib_s32 *src,
                                mlib_s32       slb,
                                mlib_s32       *dst,
                                mlib_s32       dlb,
                                mlib_s32       xsize,
                                mlib_s32       ysize,
                                const mlib_s32 **table,
                                mlib_s32       csize);

/* mlib_v_ImageLookUpS32U8Func.c */

void mlib_v_ImageLookUp_S32_U8_1(const mlib_s32 *src,
                                 mlib_s32       slb,
                                 mlib_u8        *dst,
                                 mlib_s32       dlb,
                                 mlib_s32       xsize,
                                 mlib_s32       ysize,
                                 const mlib_u8  **table);

void mlib_v_ImageLookUp_S32_U8_2(const mlib_s32 *src,
                                 mlib_s32       slb,
                                 mlib_u8        *dst,
                                 mlib_s32       dlb,
                                 mlib_s32       xsize,
                                 mlib_s32       ysize,
                                 const mlib_u8  **table);

void mlib_v_ImageLookUp_S32_U8_3(const mlib_s32 *src,
                                 mlib_s32       slb,
                                 mlib_u8        *dst,
                                 mlib_s32       dlb,
                                 mlib_s32       xsize,
                                 mlib_s32       ysize,
                                 const mlib_u8  **table);

void mlib_v_ImageLookUp_S32_U8_4(const mlib_s32 *src,
                                 mlib_s32       slb,
                                 mlib_u8        *dst,
                                 mlib_s32       dlb,
                                 mlib_s32       xsize,
                                 mlib_s32       ysize,
                                 const mlib_u8  **table);

/* mlib_v_ImageLookUpSIS16S16Func.c */

void mlib_v_ImageLookUpSI_S16_S16_2(const mlib_s16 *src,
                                    mlib_s32       slb,
                                    mlib_s16       *dst,
                                    mlib_s32       dlb,
                                    mlib_s32       xsize,
                                    mlib_s32       ysize,
                                    const mlib_s16 **table);

void mlib_v_ImageLookUpSI_S16_S16_3(const mlib_s16 *src,
                                    mlib_s32       slb,
                                    mlib_s16       *dst,
                                    mlib_s32       dlb,
                                    mlib_s32       xsize,
                                    mlib_s32       ysize,
                                    const mlib_s16 **table);

void mlib_v_ImageLookUpSI_S16_S16_4(const mlib_s16 *src,
                                    mlib_s32       slb,
                                    mlib_s16       *dst,
                                    mlib_s32       dlb,
                                    mlib_s32       xsize,
                                    mlib_s32       ysize,
                                    const mlib_s16 **table);

/* mlib_v_ImageLookUpSIS16S32Func.c */

void mlib_v_ImageLookUpSI_S16_S32_2(const mlib_s16 *src,
                                    mlib_s32       slb,
                                    mlib_s32       *dst,
                                    mlib_s32       dlb,
                                    mlib_s32       xsize,
                                    mlib_s32       ysize,
                                    const mlib_s32 **table);

void mlib_v_ImageLookUpSI_S16_S32_3(const mlib_s16 *src,
                                    mlib_s32       slb,
                                    mlib_s32       *dst,
                                    mlib_s32       dlb,
                                    mlib_s32       xsize,
                                    mlib_s32       ysize,
                                    const mlib_s32 **table);

void mlib_v_ImageLookUpSI_S16_S32_4(const mlib_s16 *src,
                                    mlib_s32       slb,
                                    mlib_s32       *dst,
                                    mlib_s32       dlb,
                                    mlib_s32       xsize,
                                    mlib_s32       ysize,
                                    const mlib_s32 **table);

/* mlib_v_ImageLookUpSIS16U8Func.c */

void mlib_v_ImageLookUpSI_S16_U8_2(const mlib_s16 *src,
                                   mlib_s32       slb,
                                   mlib_u8        *dst,
                                   mlib_s32       dlb,
                                   mlib_s32       xsize,
                                   mlib_s32       ysize,
                                   const mlib_u8  **table);

void mlib_v_ImageLookUpSI_S16_U8_3(const mlib_s16 *src,
                                   mlib_s32       slb,
                                   mlib_u8        *dst,
                                   mlib_s32       dlb,
                                   mlib_s32       xsize,
                                   mlib_s32       ysize,
                                   const mlib_u8  **table);

void mlib_v_ImageLookUpSI_S16_U8_4(const mlib_s16 *src,
                                   mlib_s32       slb,
                                   mlib_u8        *dst,
                                   mlib_s32       dlb,
                                   mlib_s32       xsize,
                                   mlib_s32       ysize,
                                   const mlib_u8  **table);

/* mlib_v_ImageLookUpSIS32S16Func.c */

void mlib_v_ImageLookUpSI_S32_S16_2(const mlib_s32 *src,
                                    mlib_s32       slb,
                                    mlib_s16       *dst,
                                    mlib_s32       dlb,
                                    mlib_s32       xsize,
                                    mlib_s32       ysize,
                                    const mlib_s16 **table);

void mlib_v_ImageLookUpSI_S32_S16_3(const mlib_s32 *src,
                                    mlib_s32       slb,
                                    mlib_s16       *dst,
                                    mlib_s32       dlb,
                                    mlib_s32       xsize,
                                    mlib_s32       ysize,
                                    const mlib_s16 **table);

void mlib_v_ImageLookUpSI_S32_S16_4(const mlib_s32 *src,
                                    mlib_s32       slb,
                                    mlib_s16       *dst,
                                    mlib_s32       dlb,
                                    mlib_s32       xsize,
                                    mlib_s32       ysize,
                                    const mlib_s16 **table);

/* mlib_v_ImageLookUpSIS32S32Func.c */

void mlib_v_ImageLookUpSI_S32_S32(const mlib_s32 *src,
                                  mlib_s32       slb,
                                  mlib_s32       *dst,
                                  mlib_s32       dlb,
                                  mlib_s32       xsize,
                                  mlib_s32       ysize,
                                  const mlib_s32 **table,
                                  mlib_s32       csize);

/* mlib_v_ImageLookUpSIS32U8Func.c */

void mlib_v_ImageLookUpSI_S32_U8_2(const mlib_s32 *src,
                                   mlib_s32       slb,
                                   mlib_u8        *dst,
                                   mlib_s32       dlb,
                                   mlib_s32       xsize,
                                   mlib_s32       ysize,
                                   const mlib_u8  **table);

void mlib_v_ImageLookUpSI_S32_U8_3(const mlib_s32 *src,
                                   mlib_s32       slb,
                                   mlib_u8        *dst,
                                   mlib_s32       dlb,
                                   mlib_s32       xsize,
                                   mlib_s32       ysize,
                                   const mlib_u8  **table);

void mlib_v_ImageLookUpSI_S32_U8_4(const mlib_s32 *src,
                                   mlib_s32       slb,
                                   mlib_u8        *dst,
                                   mlib_s32       dlb,
                                   mlib_s32       xsize,
                                   mlib_s32       ysize,
                                   const mlib_u8  **table);

/* mlib_v_ImageLookUpSIU8S16Func.c */

void mlib_v_ImageLookUpSI_U8_S16_2(const mlib_u8  *src,
                                   mlib_s32       slb,
                                   mlib_s16       *dst,
                                   mlib_s32       dlb,
                                   mlib_s32       xsize,
                                   mlib_s32       ysize,
                                   const mlib_s16 **table);

void mlib_v_ImageLookUpSI_U8_S16_3(const mlib_u8  *src,
                                   mlib_s32       slb,
                                   mlib_s16       *dst,
                                   mlib_s32       dlb,
                                   mlib_s32       xsize,
                                   mlib_s32       ysize,
                                   const mlib_s16 **table);

void mlib_v_ImageLookUpSI_U8_S16_4(const mlib_u8  *src,
                                   mlib_s32       slb,
                                   mlib_s16       *dst,
                                   mlib_s32       dlb,
                                   mlib_s32       xsize,
                                   mlib_s32       ysize,
                                   const mlib_s16 **table);

/* mlib_v_ImageLookUpSIU8S32Func.c */

void mlib_v_ImageLookUpSI_U8_S32_2(const mlib_u8  *src,
                                   mlib_s32       slb,
                                   mlib_s32       *dst,
                                   mlib_s32       dlb,
                                   mlib_s32       xsize,
                                   mlib_s32       ysize,
                                   const mlib_s32 **table);

void mlib_v_ImageLookUpSI_U8_S32_3(const mlib_u8  *src,
                                   mlib_s32       slb,
                                   mlib_s32       *dst,
                                   mlib_s32       dlb,
                                   mlib_s32       xsize,
                                   mlib_s32       ysize,
                                   const mlib_s32 **table);

void mlib_v_ImageLookUpSI_U8_S32_4(const mlib_u8  *src,
                                   mlib_s32       slb,
                                   mlib_s32       *dst,
                                   mlib_s32       dlb,
                                   mlib_s32       xsize,
                                   mlib_s32       ysize,
                                   const mlib_s32 **table);

/* mlib_v_ImageLookUpSIU8U8Func.c */

void mlib_v_ImageLookUpSI_U8_U8_2(const mlib_u8 *src,
                                  mlib_s32      slb,
                                  mlib_u8       *dst,
                                  mlib_s32      dlb,
                                  mlib_s32      xsize,
                                  mlib_s32      ysize,
                                  const mlib_u8 **table);

void mlib_v_ImageLookUpSI_U8_U8_3(const mlib_u8 *src,
                                  mlib_s32      slb,
                                  mlib_u8       *dst,
                                  mlib_s32      dlb,
                                  mlib_s32      xsize,
                                  mlib_s32      ysize,
                                  const mlib_u8 **table);

void mlib_v_ImageLookUpSI_U8_U8_4(const mlib_u8 *src,
                                  mlib_s32      slb,
                                  mlib_u8       *dst,
                                  mlib_s32      dlb,
                                  mlib_s32      xsize,
                                  mlib_s32      ysize,
                                  const mlib_u8 **table);

/* mlib_v_ImageLookUpU8S16Func.c */

void mlib_v_ImageLookUp_U8_S16_1(const mlib_u8  *src,
                                 mlib_s32       slb,
                                 mlib_s16       *dst,
                                 mlib_s32       dlb,
                                 mlib_s32       xsize,
                                 mlib_s32       ysize,
                                 const mlib_s16 **table);

void mlib_v_ImageLookUp_U8_S16_2(const mlib_u8  *src,
                                 mlib_s32       slb,
                                 mlib_s16       *dst,
                                 mlib_s32       dlb,
                                 mlib_s32       xsize,
                                 mlib_s32       ysize,
                                 const mlib_s16 **table);

void mlib_v_ImageLookUp_U8_S16_3(const mlib_u8  *src,
                                 mlib_s32       slb,
                                 mlib_s16       *dst,
                                 mlib_s32       dlb,
                                 mlib_s32       xsize,
                                 mlib_s32       ysize,
                                 const mlib_s16 **table);

void mlib_v_ImageLookUp_U8_S16_4(const mlib_u8  *src,
                                 mlib_s32       slb,
                                 mlib_s16       *dst,
                                 mlib_s32       dlb,
                                 mlib_s32       xsize,
                                 mlib_s32       ysize,
                                 const mlib_s16 **table);

/* mlib_v_ImageLookUpU8S32Func.c */


void mlib_v_ImageLookUp_U8_S32_1(const mlib_u8  *src,
                                 mlib_s32       slb,
                                 mlib_s32       *dst,
                                 mlib_s32       dlb,
                                 mlib_s32       xsize,
                                 mlib_s32       ysize,
                                 const mlib_s32 **table);

void mlib_v_ImageLookUp_U8_S32_2(const mlib_u8  *src,
                                 mlib_s32       slb,
                                 mlib_s32       *dst,
                                 mlib_s32       dlb,
                                 mlib_s32       xsize,
                                 mlib_s32       ysize,
                                 const mlib_s32 **table);

void mlib_v_ImageLookUp_U8_S32_3(const mlib_u8  *src,
                                 mlib_s32       slb,
                                 mlib_s32       *dst,
                                 mlib_s32       dlb,
                                 mlib_s32       xsize,
                                 mlib_s32       ysize,
                                 const mlib_s32 **table);

void mlib_v_ImageLookUp_U8_S32_4(const mlib_u8  *src,
                                 mlib_s32       slb,
                                 mlib_s32       *dst,
                                 mlib_s32       dlb,
                                 mlib_s32       xsize,
                                 mlib_s32       ysize,
                                 const mlib_s32 **table);

/* mlib_v_ImageLookUpU8U8Func.c */

void mlib_v_ImageLookUp_U8_U8_1(const mlib_u8 *src,
                                mlib_s32      slb,
                                mlib_u8       *dst,
                                mlib_s32      dlb,
                                mlib_s32      xsize,
                                mlib_s32      ysize,
                                const mlib_u8 **table);

void mlib_v_ImageLookUp_U8_U8_2(const mlib_u8 *src,
                                mlib_s32      slb,
                                mlib_u8       *dst,
                                mlib_s32      dlb,
                                mlib_s32      xsize,
                                mlib_s32      ysize,
                                const mlib_u8 **table);

void mlib_v_ImageLookUp_U8_U8_3(const mlib_u8 *src,
                                mlib_s32      slb,
                                mlib_u8       *dst,
                                mlib_s32      dlb,
                                mlib_s32      xsize,
                                mlib_s32      ysize,
                                const mlib_u8 **table);

void mlib_v_ImageLookUp_U8_U8_4(const mlib_u8 *src,
                                mlib_s32      slb,
                                mlib_u8       *dst,
                                mlib_s32      dlb,
                                mlib_s32      xsize,
                                mlib_s32      ysize,
                                const mlib_u8 **table);


/* mlib_v_ImageLookUpU8U16Func.c */

void mlib_v_ImageLookUp_U8_U16_1(const mlib_u8  *src,
                                 mlib_s32       slb,
                                 mlib_u16       *dst,
                                 mlib_s32       dlb,
                                 mlib_s32       xsize,
                                 mlib_s32       ysize,
                                 const mlib_u16 **table);

void mlib_v_ImageLookUp_U8_U16_2(const mlib_u8  *src,
                                 mlib_s32       slb,
                                 mlib_u16       *dst,
                                 mlib_s32       dlb,
                                 mlib_s32       xsize,
                                 mlib_s32       ysize,
                                 const mlib_u16 **table);

void mlib_v_ImageLookUp_U8_U16_3(const mlib_u8  *src,
                                 mlib_s32       slb,
                                 mlib_u16       *dst,
                                 mlib_s32       dlb,
                                 mlib_s32       xsize,
                                 mlib_s32       ysize,
                                 const mlib_u16 **table);

void mlib_v_ImageLookUp_U8_U16_4(const mlib_u8  *src,
                                 mlib_s32       slb,
                                 mlib_u16       *dst,
                                 mlib_s32       dlb,
                                 mlib_s32       xsize,
                                 mlib_s32       ysize,
                                 const mlib_u16 **table);

/* mlib_v_ImageLookUpS32U16Func.c */

void mlib_v_ImageLookUp_S32_U16_1(const mlib_s32 *src,
                                  mlib_s32       slb,
                                  mlib_u16       *dst,
                                  mlib_s32       dlb,
                                  mlib_s32       xsize,
                                  mlib_s32       ysize,
                                  const mlib_u16 **table);

void mlib_v_ImageLookUp_S32_U16_2(const mlib_s32 *src,
                                  mlib_s32       slb,
                                  mlib_u16       *dst,
                                  mlib_s32       dlb,
                                  mlib_s32       xsize,
                                  mlib_s32       ysize,
                                  const mlib_u16 **table);

void mlib_v_ImageLookUp_S32_U16_3(const mlib_s32 *src,
                                  mlib_s32       slb,
                                  mlib_u16       *dst,
                                  mlib_s32       dlb,
                                  mlib_s32       xsize,
                                  mlib_s32       ysize,
                                  const mlib_u16 **table);

void mlib_v_ImageLookUp_S32_U16_4(const mlib_s32 *src,
                                  mlib_s32       slb,
                                  mlib_u16       *dst,
                                  mlib_s32       dlb,
                                  mlib_s32       xsize,
                                  mlib_s32       ysize,
                                  const mlib_u16 **table);


/* mlib_v_ImageLookUpS16U16Func.c */

void mlib_v_ImageLookUp_S16_U16_1(const mlib_s16 *src,
                                  mlib_s32       slb,
                                  mlib_u16       *dst,
                                  mlib_s32       dlb,
                                  mlib_s32       xsize,
                                  mlib_s32       ysize,
                                  const mlib_u16 **table);

void mlib_v_ImageLookUp_S16_U16_2(const mlib_s16 *src,
                                  mlib_s32       slb,
                                  mlib_u16       *dst,
                                  mlib_s32       dlb,
                                  mlib_s32       xsize,
                                  mlib_s32       ysize,
                                  const mlib_u16 **table);

void mlib_v_ImageLookUp_S16_U16_3(const mlib_s16 *src,
                                  mlib_s32       slb,
                                  mlib_u16       *dst,
                                  mlib_s32       dlb,
                                  mlib_s32       xsize,
                                  mlib_s32       ysize,
                                  const mlib_u16 **table);

void mlib_v_ImageLookUp_S16_U16_4(const mlib_s16 *src,
                                  mlib_s32       slb,
                                  mlib_u16       *dst,
                                  mlib_s32       dlb,
                                  mlib_s32       xsize,
                                  mlib_s32       ysize,
                                  const mlib_u16 **table);

/* mlib_v_ImageLookUpSIU8U16Func.c */

void mlib_v_ImageLookUpSI_U8_U16_2(const mlib_u8  *src,
                                   mlib_s32       slb,
                                   mlib_u16       *dst,
                                   mlib_s32       dlb,
                                   mlib_s32       xsize,
                                   mlib_s32       ysize,
                                   const mlib_u16 **table);

void mlib_v_ImageLookUpSI_U8_U16_3(const mlib_u8  *src,
                                   mlib_s32       slb,
                                   mlib_u16       *dst,
                                   mlib_s32       dlb,
                                   mlib_s32       xsize,
                                   mlib_s32       ysize,
                                   const mlib_u16 **table);

void mlib_v_ImageLookUpSI_U8_U16_4(const mlib_u8  *src,
                                   mlib_s32       slb,
                                   mlib_u16       *dst,
                                   mlib_s32       dlb,
                                   mlib_s32       xsize,
                                   mlib_s32       ysize,
                                   const mlib_u16 **table);

/* mlib_v_ImageLookUpSIS16U16Func.c */

void mlib_v_ImageLookUpSI_S16_U16_2(const mlib_s16 *src,
                                    mlib_s32       slb,
                                    mlib_u16       *dst,
                                    mlib_s32       dlb,
                                    mlib_s32       xsize,
                                    mlib_s32       ysize,
                                    const mlib_u16 **table);

void mlib_v_ImageLookUpSI_S16_U16_3(const mlib_s16 *src,
                                    mlib_s32       slb,
                                    mlib_u16       *dst,
                                    mlib_s32       dlb,
                                    mlib_s32       xsize,
                                    mlib_s32       ysize,
                                    const mlib_u16 **table);

void mlib_v_ImageLookUpSI_S16_U16_4(const mlib_s16 *src,
                                    mlib_s32       slb,
                                    mlib_u16       *dst,
                                    mlib_s32       dlb,
                                    mlib_s32       xsize,
                                    mlib_s32       ysize,
                                    const mlib_u16 **table);

/* mlib_v_ImageLookUpSIS32U16Func.c */

void mlib_v_ImageLookUpSI_S32_U16_2(const mlib_s32 *src,
                                    mlib_s32       slb,
                                    mlib_u16       *dst,
                                    mlib_s32       dlb,
                                    mlib_s32       xsize,
                                    mlib_s32       ysize,
                                    const mlib_u16 **table);

void mlib_v_ImageLookUpSI_S32_U16_3(const mlib_s32 *src,
                                    mlib_s32       slb,
                                    mlib_u16       *dst,
                                    mlib_s32       dlb,
                                    mlib_s32       xsize,
                                    mlib_s32       ysize,
                                    const mlib_u16 **table);

void mlib_v_ImageLookUpSI_S32_U16_4(const mlib_s32 *src,
                                    mlib_s32       slb,
                                    mlib_u16       *dst,
                                    mlib_s32       dlb,
                                    mlib_s32       xsize,
                                    mlib_s32       ysize,
                                    const mlib_u16 **table);

/* mlib_v_ImageLookUpU16S32Func.c */

void mlib_v_ImageLookUp_U16_S32_1(const mlib_u16 *src,
                                  mlib_s32       slb,
                                  mlib_s32       *dst,
                                  mlib_s32       dlb,
                                  mlib_s32       xsize,
                                  mlib_s32       ysize,
                                  const mlib_s32 **table);

void mlib_v_ImageLookUp_U16_S32_2(const mlib_u16 *src,
                                  mlib_s32       slb,
                                  mlib_s32       *dst,
                                  mlib_s32       dlb,
                                  mlib_s32       xsize,
                                  mlib_s32       ysize,
                                  const mlib_s32 **table);

void mlib_v_ImageLookUp_U16_S32_3(const mlib_u16 *src,
                                  mlib_s32       slb,
                                  mlib_s32       *dst,
                                  mlib_s32       dlb,
                                  mlib_s32       xsize,
                                  mlib_s32       ysize,
                                  const mlib_s32 **table);

void mlib_v_ImageLookUp_U16_S32_4(const mlib_u16 *src,
                                  mlib_s32       slb,
                                  mlib_s32       *dst,
                                  mlib_s32       dlb,
                                  mlib_s32       xsize,
                                  mlib_s32       ysize,
                                  const mlib_s32 **table);

/* mlib_v_ImageLookUpSIU16S32Func.c */

void mlib_v_ImageLookUpSI_U16_S32_2(const mlib_u16 *src,
                                    mlib_s32       slb,
                                    mlib_s32       *dst,
                                    mlib_s32       dlb,
                                    mlib_s32       xsize,
                                    mlib_s32       ysize,
                                    const mlib_s32 **table);

void mlib_v_ImageLookUpSI_U16_S32_3(const mlib_u16 *src,
                                    mlib_s32       slb,
                                    mlib_s32       *dst,
                                    mlib_s32       dlb,
                                    mlib_s32       xsize,
                                    mlib_s32       ysize,
                                    const mlib_s32 **table);

void mlib_v_ImageLookUpSI_U16_S32_4(const mlib_u16 *src,
                                    mlib_s32       slb,
                                    mlib_s32       *dst,
                                    mlib_s32       dlb,
                                    mlib_s32       xsize,
                                    mlib_s32       ysize,
                                    const mlib_s32 **table);

/* mlib_v_ImageLookUpSIU16U16Func.c */

void mlib_v_ImageLookUpSI_U16_U16_2(const mlib_u16 *src,
                                    mlib_s32       slb,
                                    mlib_u16       *dst,
                                    mlib_s32       dlb,
                                    mlib_s32       xsize,
                                    mlib_s32       ysize,
                                    const mlib_u16 **table);

void mlib_v_ImageLookUpSI_U16_U16_3(const mlib_u16 *src,
                                    mlib_s32       slb,
                                    mlib_u16       *dst,
                                    mlib_s32       dlb,
                                    mlib_s32       xsize,
                                    mlib_s32       ysize,
                                    const mlib_u16 **table);

void mlib_v_ImageLookUpSI_U16_U16_4(const mlib_u16 *src,
                                    mlib_s32       slb,
                                    mlib_u16       *dst,
                                    mlib_s32       dlb,
                                    mlib_s32       xsize,
                                    mlib_s32       ysize,
                                    const mlib_u16 **table);

/* mlib_v_ImageLookUpU16U16Func.c */

void mlib_v_ImageLookUp_U16_U16_1(const mlib_u16 *src,
                                  mlib_s32       slb,
                                  mlib_u16       *dst,
                                  mlib_s32       dlb,
                                  mlib_s32       xsize,
                                  mlib_s32       ysize,
                                  const mlib_u16 **table);

void mlib_v_ImageLookUp_U16_U16_2(const mlib_u16 *src,
                                  mlib_s32       slb,
                                  mlib_u16       *dst,
                                  mlib_s32       dlb,
                                  mlib_s32       xsize,
                                  mlib_s32       ysize,
                                  const mlib_u16 **table);

void mlib_v_ImageLookUp_U16_U16_3(const mlib_u16 *src,
                                  mlib_s32       slb,
                                  mlib_u16       *dst,
                                  mlib_s32       dlb,
                                  mlib_s32       xsize,
                                  mlib_s32       ysize,
                                  const mlib_u16 **table);

void mlib_v_ImageLookUp_U16_U16_4(const mlib_u16 *src,
                                  mlib_s32       slb,
                                  mlib_u16       *dst,
                                  mlib_s32       dlb,
                                  mlib_s32       xsize,
                                  mlib_s32       ysize,
                                  const mlib_u16 **table);

/* mlib_v_ImageLookUpU16S16Func.c */

void mlib_v_ImageLookUp_U16_S16_1(const mlib_u16 *src,
                                  mlib_s32       slb,
                                  mlib_s16       *dst,
                                  mlib_s32       dlb,
                                  mlib_s32       xsize,
                                  mlib_s32       ysize,
                                  const mlib_s16 **table);

void mlib_v_ImageLookUp_U16_S16_2(const mlib_u16 *src,
                                  mlib_s32       slb,
                                  mlib_s16       *dst,
                                  mlib_s32       dlb,
                                  mlib_s32       xsize,
                                  mlib_s32       ysize,
                                  const mlib_s16 **table);

void mlib_v_ImageLookUp_U16_S16_3(const mlib_u16 *src,
                                  mlib_s32       slb,
                                  mlib_s16       *dst,
                                  mlib_s32       dlb,
                                  mlib_s32       xsize,
                                  mlib_s32       ysize,
                                  const mlib_s16 **table);

void mlib_v_ImageLookUp_U16_S16_4(const mlib_u16 *src,
                                  mlib_s32       slb,
                                  mlib_s16       *dst,
                                  mlib_s32       dlb,
                                  mlib_s32       xsize,
                                  mlib_s32       ysize,
                                  const mlib_s16 **table);

/* mlib_v_ImageLookUpSIU16S16Func.c */

void mlib_v_ImageLookUpSI_U16_S16_2(const mlib_u16 *src,
                                    mlib_s32       slb,
                                    mlib_s16       *dst,
                                    mlib_s32       dlb,
                                    mlib_s32       xsize,
                                    mlib_s32       ysize,
                                    const mlib_s16 **table);

void mlib_v_ImageLookUpSI_U16_S16_3(const mlib_u16 *src,
                                    mlib_s32       slb,
                                    mlib_s16       *dst,
                                    mlib_s32       dlb,
                                    mlib_s32       xsize,
                                    mlib_s32       ysize,
                                    const mlib_s16 **table);

void mlib_v_ImageLookUpSI_U16_S16_4(const mlib_u16 *src,
                                    mlib_s32       slb,
                                    mlib_s16       *dst,
                                    mlib_s32       dlb,
                                    mlib_s32       xsize,
                                    mlib_s32       ysize,
                                    const mlib_s16 **table);

/* mlib_v_ImageLookUpU16U8Func.c */

void mlib_v_ImageLookUp_U16_U8_1(const mlib_u16 *src,
                                 mlib_s32       slb,
                                 mlib_u8        *dst,
                                 mlib_s32       dlb,
                                 mlib_s32       xsize,
                                 mlib_s32       ysize,
                                 const mlib_u8  **table);

void mlib_v_ImageLookUp_U16_U8_2(const mlib_u16 *src,
                                 mlib_s32       slb,
                                 mlib_u8        *dst,
                                 mlib_s32       dlb,
                                 mlib_s32       xsize,
                                 mlib_s32       ysize,
                                 const mlib_u8  **table);

void mlib_v_ImageLookUp_U16_U8_3(const mlib_u16 *src,
                                 mlib_s32       slb,
                                 mlib_u8        *dst,
                                 mlib_s32       dlb,
                                 mlib_s32       xsize,
                                 mlib_s32       ysize,
                                 const mlib_u8  **table);

void mlib_v_ImageLookUp_U16_U8_4(const mlib_u16 *src,
                                 mlib_s32       slb,
                                 mlib_u8        *dst,
                                 mlib_s32       dlb,
                                 mlib_s32       xsize,
                                 mlib_s32       ysize,
                                 const mlib_u8  **table);

/* mlib_v_ImageLookUpSIU16U8Func.c */

void mlib_v_ImageLookUpSI_U16_U8_2(const mlib_u16 *src,
                                   mlib_s32       slb,
                                   mlib_u8        *dst,
                                   mlib_s32       dlb,
                                   mlib_s32       xsize,
                                   mlib_s32       ysize,
                                   const mlib_u8  **table);

void mlib_v_ImageLookUpSI_U16_U8_3(const mlib_u16 *src,
                                   mlib_s32       slb,
                                   mlib_u8        *dst,
                                   mlib_s32       dlb,
                                   mlib_s32       xsize,
                                   mlib_s32       ysize,
                                   const mlib_u8  **table);

void mlib_v_ImageLookUpSI_U16_U8_4(const mlib_u16 *src,
                                   mlib_s32       slb,
                                   mlib_u8        *dst,
                                   mlib_s32       dlb,
                                   mlib_s32       xsize,
                                   mlib_s32       ysize,
                                   const mlib_u8  **table);

#ifdef __cplusplus
}
#endif
#endif /* __MLIB_IMAGE_LOOKUP_V_FUNC_INTENAL_H */
