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


#include "mlib_image.h"
#include "mlib_ImageAffine.h"
#include "mlib_ImageColormap.h"

/***************************************************************/
#define MLIB_LIMIT  512

/***************************************************************/
#define DTYPE  MLIB_TYPE

/***************************************************************/
#define DECLAREVAR_IND()                                        \
  DECLAREVAR0();                                                \
  mlib_s32  *warp_tbl   = param -> warp_tbl;                    \
  mlib_s32  xSrc, ySrc;                                         \
  mlib_s32  srcYStride = param -> srcYStride;                   \
  mlib_s32  max_xsize  = param -> max_xsize;                    \
  MLIB_TYPE *sp0, *sp1;                                         \
  MLIB_TYPE *dl;                                                \
  mlib_d64  scale = 1.0 / 65536.0;                              \
  mlib_s32  i, size

/***************************************************************/
#define DECLARE_INTERNAL_VAR_3CH()                              \
  mlib_d64  fdx, fdy;                                           \
  mlib_d64  a00_0, a01_0, a10_0, a11_0;                         \
  mlib_d64  a00_1, a01_1, a10_1, a11_1;                         \
  mlib_d64  a00_2, a01_2, a10_2, a11_2;                         \
  mlib_d64  pix0_0, pix1_0, res0;                               \
  mlib_d64  pix0_1, pix1_1, res1;                               \
  mlib_d64  pix0_2, pix1_2, res2

/***************************************************************/
#define DECLARE_INTERNAL_VAR_4CH()                              \
  mlib_d64  fdx, fdy;                                           \
  mlib_d64  a00_0, a01_0, a10_0, a11_0;                         \
  mlib_d64  a00_1, a01_1, a10_1, a11_1;                         \
  mlib_d64  a00_2, a01_2, a10_2, a11_2;                         \
  mlib_d64  a00_3, a01_3, a10_3, a11_3;                         \
  mlib_d64  pix0_0, pix1_0, res0;                               \
  mlib_d64  pix0_1, pix1_1, res1;                               \
  mlib_d64  pix0_2, pix1_2, res2;                               \
  mlib_d64  pix0_3, pix1_3, res3

/***************************************************************/
#define GET_PIXELS_POINTERS()                                   \
  fdx = (X & MLIB_MASK) * scale;                                \
  fdy = (Y & MLIB_MASK) * scale;                                \
  ySrc = MLIB_POINTER_SHIFT(Y);  Y += dY;                       \
  xSrc = X >> MLIB_SHIFT;  X += dX;                             \
  sp0 = MLIB_POINTER_GET(lineAddr, ySrc) + xSrc;                \
  sp1 = (MLIB_TYPE *)((mlib_u8 *)sp0 + srcYStride)

/***************************************************************/
#define GET_COLOR_POINTERS(ind)                                 \
  pcolor00 = (lut + sp0[0]*ind);                                \
  pcolor10 = (lut + sp1[0]*ind);                                \
  pcolor01 = (lut + sp0[1]*ind);                                \
  pcolor11 = (lut + sp1[1]*ind)

/***************************************************************/
#define COUNT_BL_U8(ind)                                        \
  pix0_##ind = a00_##ind + fdy * (a10_##ind - a00_##ind);       \
  pix1_##ind = a01_##ind + fdy * (a11_##ind - a01_##ind);       \
  res##ind = pix0_##ind + fdx * (pix1_##ind - pix0_##ind) + 0.5

/***************************************************************/
#define COUNT_BL_U8_3CH()                                       \
  COUNT_BL_U8(0);                                               \
  COUNT_BL_U8(1);                                               \
  COUNT_BL_U8(2);

/***************************************************************/
#define COUNT_BL_U8_4CH()                                       \
  COUNT_BL_U8_3CH();                                            \
  COUNT_BL_U8(3);

/***************************************************************/
#define COUNT_BL_S16(ind)                                       \
  pix0_##ind = a00_##ind + fdy * (a10_##ind - a00_##ind);       \
  pix1_##ind = a01_##ind + fdy * (a11_##ind - a01_##ind);       \
  res##ind = pix0_##ind + fdx * (pix1_##ind - pix0_##ind)

/***************************************************************/
#define COUNT_BL_S16_3CH()                                      \
  COUNT_BL_S16(0);                                              \
  COUNT_BL_S16(1);                                              \
  COUNT_BL_S16(2);

/***************************************************************/
#define COUNT_BL_S16_4CH()                                      \
  COUNT_BL_S16_3CH();                                           \
  COUNT_BL_S16(3);

/***************************************************************/
#define LOAD(ind)                                               \
  a00_##ind = pcolor00[ind];                                    \
  a01_##ind = pcolor01[ind];                                    \
  a10_##ind = pcolor10[ind];                                    \
  a11_##ind = pcolor11[ind]

/***************************************************************/
#define LOAD_3CH()                                              \
  LOAD(0);                                                      \
  LOAD(1);                                                      \
  LOAD(2);

/***************************************************************/
#define LOAD_4CH()                                              \
  LOAD_3CH();                                                   \
  LOAD(3);

/***************************************************************/
#define STORE_INTO_INTERM_BUF_3CH(LTYPE)                        \
  dp[0] = (mlib_##LTYPE)res0;                                   \
  dp[1] = (mlib_##LTYPE)res1;                                   \
  dp[2] = (mlib_##LTYPE)res2

/***************************************************************/
#define STORE_INTO_INTERM_BUF_4CH(LTYPE)                        \
  dp[0] = (mlib_##LTYPE)res0;                                   \
  dp[1] = (mlib_##LTYPE)res1;                                   \
  dp[2] = (mlib_##LTYPE)res2;                                   \
  dp[3] = (mlib_##LTYPE)res3

/***************************************************************/
#undef  MLIB_TYPE
#define MLIB_TYPE mlib_u8

/***************************************************************/
#define mlib_U8  mlib_u8
#define mlib_S16 mlib_s16

/***************************************************************/
#define FUNC_AFFINEINDEX_BL_0(ITYPE, LTYPE, NCHAN)                                               \
  mlib_status mlib_ImageAffineIndex_##ITYPE##_##LTYPE##_##NCHAN##CH_BL(mlib_affine_param *param, \
                                                                       const void        *colormap) \
  {                                                                                              \
    DECLAREVAR_IND();                                                                            \
    mlib_##LTYPE  *dp, buff_lcl[NCHAN*MLIB_LIMIT], *pbuff = buff_lcl;                            \
    mlib_d64 *pcolor00, *pcolor10, *pcolor01, *pcolor11;                                         \
    mlib_d64 *lut = mlib_ImageGetLutDoubleData(colormap);                                        \
                                                                                                 \
    lut -= NCHAN*mlib_ImageGetLutOffset(colormap);                                               \
                                                                                                 \
    if (max_xsize > MLIB_LIMIT) {                                                                \
      pbuff = mlib_malloc(NCHAN * sizeof(mlib_##LTYPE) * max_xsize);                             \
      if (pbuff == NULL) return MLIB_FAILURE;                                                    \
    }                                                                                            \
                                                                                                 \
    for (j = yStart; j <= yFinish; j++) {                                                        \
      DECLARE_INTERNAL_VAR_##NCHAN##CH();                                                        \
                                                                                                 \
      NEW_LINE(1);                                                                               \
      dp = pbuff;                                                                                \
                                                                                                 \
      GET_PIXELS_POINTERS();                                                                     \
      GET_COLOR_POINTERS(NCHAN);                                                                 \
      LOAD_##NCHAN##CH();

    /* pragma pipeloop(0) must be here */

/***************************************************************/
#define FUNC_AFFINEINDEX_BL_1(ITYPE, LTYPE, NCHAN)                   \
      for (i = 0; i < (xRight - xLeft); i++, dp += NCHAN) {          \
        COUNT_BL_##LTYPE##_##NCHAN##CH();                            \
                                                                     \
        GET_PIXELS_POINTERS();                                       \
        GET_COLOR_POINTERS(NCHAN);                                   \
        LOAD_##NCHAN##CH();                                          \
                                                                     \
        STORE_INTO_INTERM_BUF_##NCHAN##CH(LTYPE);                    \
      }                                                              \
                                                                     \
      COUNT_BL_##LTYPE##_##NCHAN##CH();                              \
      STORE_INTO_INTERM_BUF_##NCHAN##CH(LTYPE);                      \
                                                                     \
      mlib_ImageColorTrue2IndexLine_##LTYPE##_##ITYPE##_##NCHAN      \
                          (pbuff, dl, xRight - xLeft + 1, colormap); \
    }                                                                \
                                                                     \
    if (pbuff != buff_lcl) mlib_free(pbuff);                         \
                                                                     \
    return MLIB_SUCCESS;                                             \
  }

/***************************************************************/
#undef  MLIB_TYPE
#define MLIB_TYPE mlib_u8

FUNC_AFFINEINDEX_BL_0(U8, U8, 3)
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
FUNC_AFFINEINDEX_BL_1(U8, U8, 3)

FUNC_AFFINEINDEX_BL_0(U8, S16, 3)
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
FUNC_AFFINEINDEX_BL_1(U8, S16, 3)

FUNC_AFFINEINDEX_BL_0(U8, U8, 4)
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
FUNC_AFFINEINDEX_BL_1(U8, U8, 4)

FUNC_AFFINEINDEX_BL_0(U8, S16, 4)
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
FUNC_AFFINEINDEX_BL_1(U8, S16, 4)

/***************************************************************/
#undef  MLIB_TYPE
#define MLIB_TYPE mlib_s16

FUNC_AFFINEINDEX_BL_0(S16, U8, 3)
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
FUNC_AFFINEINDEX_BL_1(S16, U8, 3)

FUNC_AFFINEINDEX_BL_0(S16, S16, 3)
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
FUNC_AFFINEINDEX_BL_1(S16, S16, 3)

FUNC_AFFINEINDEX_BL_0(S16, U8, 4)
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
FUNC_AFFINEINDEX_BL_1(S16, U8, 4)

FUNC_AFFINEINDEX_BL_0(S16, S16, 4)
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
FUNC_AFFINEINDEX_BL_1(S16, S16, 4)

/***************************************************************/
const type_affine_i_fun mlib_AffineFunArr_bl_i[] = {
  mlib_ImageAffineIndex_U8_U8_3CH_BL,
  mlib_ImageAffineIndex_U8_U8_4CH_BL,
  mlib_ImageAffineIndex_S16_U8_3CH_BL,
  mlib_ImageAffineIndex_S16_U8_4CH_BL,
  mlib_ImageAffineIndex_U8_S16_3CH_BL,
  mlib_ImageAffineIndex_U8_S16_4CH_BL,
  mlib_ImageAffineIndex_S16_S16_3CH_BL,
  mlib_ImageAffineIndex_S16_S16_4CH_BL
};
/***************************************************************/
