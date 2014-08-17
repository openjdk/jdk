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
#define MLIB_SHIFT   16
#define MLIB_PREC    (1 << MLIB_SHIFT)
#define MLIB_MASK    (MLIB_PREC - 1)

/***************************************************************/
#define DTYPE  MLIB_TYPE

/***************************************************************/
#define DECLAREVAR_IND()                                        \
  DECLAREVAR0();                                                \
  mlib_s32  *warp_tbl   = param -> warp_tbl;                    \
  mlib_s32  xSrc, ySrc;                                         \
  mlib_s32  srcYStride = param -> srcYStride;                   \
  mlib_s32  max_xsize  = param -> max_xsize;                    \
  mlib_filter filter = param -> filter;                         \
  MLIB_TYPE *sp, *dl;                                           \
  mlib_d64  xf0, xf1, xf2, xf3;                                 \
  mlib_d64  yf0, yf1, yf2, yf3;                                 \
  mlib_d64  c0, c1, c2, c3, val0;                               \
  mlib_s32  filterpos;                                          \
  mlib_f32  *fptr;                                              \
  mlib_d64  s0, s1, s2, s3;                                     \
  mlib_s32  i, size

/***************************************************************/
#define GET_FILTERS_KOEF()                                         \
  filterpos = (X >> FILTER_SHIFT) & FILTER_MASK;                   \
  fptr = (mlib_f32 *) ((mlib_u8 *)mlib_filters_table + filterpos); \
                                                                   \
  xf0 = fptr[0];                                                   \
  xf1 = fptr[1];                                                   \
  xf2 = fptr[2];                                                   \
  xf3 = fptr[3];                                                   \
                                                                   \
  filterpos = (Y >> FILTER_SHIFT) & FILTER_MASK;                   \
  fptr = (mlib_f32 *) ((mlib_u8 *)mlib_filters_table + filterpos); \
                                                                   \
  yf0 = fptr[0];                                                   \
  yf1 = fptr[1];                                                   \
  yf2 = fptr[2];                                                   \
  yf3 = fptr[3]

/***************************************************************/
#define GET_POINTER()                                           \
  xSrc = (X >> MLIB_SHIFT)-1;                                   \
  ySrc = (Y >> MLIB_SHIFT)-1;                                   \
  sp = ((MLIB_TYPE **)lineAddr)[ySrc] + xSrc

/***************************************************************/
#define LOAD_FIRST_ROW(nchan, chan)                             \
  s0 = *(lut + sp[0]*nchan + chan);                             \
  s1 = *(lut + sp[1]*nchan + chan);                             \
  s2 = *(lut + sp[2]*nchan + chan);                             \
  s3 = *(lut + sp[3]*nchan + chan)

/***************************************************************/
#define COUNT_NEXT_ROW(dst, nchan, chan)                        \
  sp = (MLIB_TYPE*)((mlib_addr)sp + srcYStride);                \
  dst = ((*(lut + sp[0]*nchan + chan))*xf0 +                    \
         (*(lut + sp[1]*nchan + chan))*xf1 +                    \
         (*(lut + sp[2]*nchan + chan))*xf2 +                    \
         (*(lut + sp[3]*nchan + chan))*xf3)

/***************************************************************/
#ifdef MLIB_USE_FTOI_CLAMPING

/***********/
#define STORE_SAT_VALUE_U8(ind)                                 \
  dp[ind] = ((mlib_s32)(val0 - (mlib_d64)0x7F800000) >> 24) ^ 0x80

/***********/
#define STORE_SAT_VALUE_S16(ind)                                \
  dp[ind] = ((mlib_s32)(val0)) >> 16

#else

/***********/
#define STORE_SAT_VALUE_U8(ind)                                 \
  val0 -= (mlib_d64)0x7F800000;                                 \
  if (val0 >= MLIB_S32_MAX)                                     \
    dp[ind] = MLIB_U8_MAX;                                      \
  else if (val0 <= MLIB_S32_MIN)                                \
    dp[ind] = MLIB_U8_MIN;                                      \
  else                                                          \
    dp[ind] = ((mlib_s32)val0 >> 24) ^ 0x80

/***********/
#define STORE_SAT_VALUE_S16(ind)                                \
  if (val0 >= MLIB_S32_MAX)                                     \
    dp[ind] = MLIB_S16_MAX;                                     \
  else if (val0 <= MLIB_S32_MIN)                                \
    dp[ind] = MLIB_S16_MIN;                                     \
  else                                                          \
    dp[ind] = (mlib_s32)val0 >> 16

#endif /* MLIB_USE_FTOI_CLAMPING */

/***************************************************************/
#define MAKE_BC_3CH(lut_format)                                 \
  X += dX;                                                      \
  Y += dY;                                                      \
  c0 = (s0*xf0 + s1*xf1 + s2*xf2 + s3*xf3);                     \
  COUNT_NEXT_ROW(c1, 3, 0);                                     \
  COUNT_NEXT_ROW(c2, 3, 0);                                     \
  COUNT_NEXT_ROW(c3, 3, 0);                                     \
  val0 = (c0*yf0 + c1*yf1 + c2*yf2 + c3*yf3);                   \
  sp = (MLIB_TYPE*)((mlib_addr)sp - 3*srcYStride);              \
  LOAD_FIRST_ROW(3, 1);                                         \
  STORE_SAT_VALUE_##lut_format(0);                              \
  c0 = (s0*xf0 + s1*xf1 + s2*xf2 + s3*xf3);                     \
  COUNT_NEXT_ROW(c1, 3, 1);                                     \
  COUNT_NEXT_ROW(c2, 3, 1);                                     \
  COUNT_NEXT_ROW(c3, 3, 1);                                     \
  val0 = (c0*yf0 + c1*yf1 + c2*yf2 + c3*yf3);                   \
  sp = (MLIB_TYPE*)((mlib_addr)sp - 3*srcYStride);              \
  LOAD_FIRST_ROW(3, 2);                                         \
  STORE_SAT_VALUE_##lut_format(1);                              \
  c0 = (s0*xf0 + s1*xf1 + s2*xf2 + s3*xf3);                     \
  COUNT_NEXT_ROW(c1, 3, 2);                                     \
  COUNT_NEXT_ROW(c2, 3, 2);                                     \
  COUNT_NEXT_ROW(c3, 3, 2);                                     \
  val0 = (c0*yf0 + c1*yf1 + c2*yf2 + c3*yf3);                   \
  GET_FILTERS_KOEF();                                           \
  GET_POINTER();                                                \
  LOAD_FIRST_ROW(3, 0);                                         \
  STORE_SAT_VALUE_##lut_format(2);

/***************************************************************/
#define MAKE_LAST_PIXEL_BC_3CH(lut_format)                      \
  c0 = (s0*xf0 + s1*xf1 + s2*xf2 + s3*xf3);                     \
  COUNT_NEXT_ROW(c1, 3, 0);                                     \
  COUNT_NEXT_ROW(c2, 3, 0);                                     \
  COUNT_NEXT_ROW(c3, 3, 0);                                     \
  val0 = (c0*yf0 + c1*yf1 + c2*yf2 + c3*yf3);                   \
  sp = (MLIB_TYPE*)((mlib_addr)sp - 3*srcYStride);              \
  LOAD_FIRST_ROW(3, 1);                                         \
  STORE_SAT_VALUE_##lut_format(0);                              \
  c0 = (s0*xf0 + s1*xf1 + s2*xf2 + s3*xf3);                     \
  COUNT_NEXT_ROW(c1, 3, 1);                                     \
  COUNT_NEXT_ROW(c2, 3, 1);                                     \
  COUNT_NEXT_ROW(c3, 3, 1);                                     \
  val0 = (c0*yf0 + c1*yf1 + c2*yf2 + c3*yf3);                   \
  sp = (MLIB_TYPE*)((mlib_addr)sp - 3*srcYStride);              \
  LOAD_FIRST_ROW(3, 2);                                         \
  STORE_SAT_VALUE_##lut_format(1);                              \
  c0 = (s0*xf0 + s1*xf1 + s2*xf2 + s3*xf3);                     \
  COUNT_NEXT_ROW(c1, 3, 2);                                     \
  COUNT_NEXT_ROW(c2, 3, 2);                                     \
  COUNT_NEXT_ROW(c3, 3, 2);                                     \
  val0 = (c0*yf0 + c1*yf1 + c2*yf2 + c3*yf3);                   \
  STORE_SAT_VALUE_##lut_format(2);

/***************************************************************/
#define MAKE_BC_4CH(lut_format)                                 \
  X += dX;                                                      \
  Y += dY;                                                      \
  c0 = (s0*xf0 + s1*xf1 + s2*xf2 + s3*xf3);                     \
  COUNT_NEXT_ROW(c1, 4, 0);                                     \
  COUNT_NEXT_ROW(c2, 4, 0);                                     \
  COUNT_NEXT_ROW(c3, 4, 0);                                     \
  val0 = (c0*yf0 + c1*yf1 + c2*yf2 + c3*yf3);                   \
  sp = (MLIB_TYPE*)((mlib_addr)sp - 3*srcYStride);              \
  LOAD_FIRST_ROW(4, 1);                                         \
  STORE_SAT_VALUE_##lut_format(0);                              \
  c0 = (s0*xf0 + s1*xf1 + s2*xf2 + s3*xf3);                     \
  COUNT_NEXT_ROW(c1, 4, 1);                                     \
  COUNT_NEXT_ROW(c2, 4, 1);                                     \
  COUNT_NEXT_ROW(c3, 4, 1);                                     \
  val0 = (c0*yf0 + c1*yf1 + c2*yf2 + c3*yf3);                   \
  sp = (MLIB_TYPE*)((mlib_addr)sp - 3*srcYStride);              \
  LOAD_FIRST_ROW(4, 2);                                         \
  STORE_SAT_VALUE_##lut_format(1);                              \
  c0 = (s0*xf0 + s1*xf1 + s2*xf2 + s3*xf3);                     \
  COUNT_NEXT_ROW(c1, 4, 2);                                     \
  COUNT_NEXT_ROW(c2, 4, 2);                                     \
  COUNT_NEXT_ROW(c3, 4, 2);                                     \
  val0 = (c0*yf0 + c1*yf1 + c2*yf2 + c3*yf3);                   \
  sp = (MLIB_TYPE*)((mlib_addr)sp - 3*srcYStride);              \
  LOAD_FIRST_ROW(4, 3);                                         \
  STORE_SAT_VALUE_##lut_format(2);                              \
  c0 = (s0*xf0 + s1*xf1 + s2*xf2 + s3*xf3);                     \
  COUNT_NEXT_ROW(c1, 4, 3);                                     \
  COUNT_NEXT_ROW(c2, 4, 3);                                     \
  COUNT_NEXT_ROW(c3, 4, 3);                                     \
  val0 = (c0*yf0 + c1*yf1 + c2*yf2 + c3*yf3);                   \
  GET_FILTERS_KOEF();                                           \
  GET_POINTER();                                                \
  LOAD_FIRST_ROW(4, 0);                                         \
  STORE_SAT_VALUE_##lut_format(3);

/***************************************************************/
#define MAKE_LAST_PIXEL_BC_4CH(lut_format)                      \
  c0 = (s0*xf0 + s1*xf1 + s2*xf2 + s3*xf3);                     \
  COUNT_NEXT_ROW(c1, 4, 0);                                     \
  COUNT_NEXT_ROW(c2, 4, 0);                                     \
  COUNT_NEXT_ROW(c3, 4, 0);                                     \
  val0 = (c0*yf0 + c1*yf1 + c2*yf2 + c3*yf3);                   \
  sp = (MLIB_TYPE*)((mlib_addr)sp - 3*srcYStride);              \
  LOAD_FIRST_ROW(4, 1);                                         \
  STORE_SAT_VALUE_##lut_format(0);                              \
  c0 = (s0*xf0 + s1*xf1 + s2*xf2 + s3*xf3);                     \
  COUNT_NEXT_ROW(c1, 4, 1);                                     \
  COUNT_NEXT_ROW(c2, 4, 1);                                     \
  COUNT_NEXT_ROW(c3, 4, 1);                                     \
  val0 = (c0*yf0 + c1*yf1 + c2*yf2 + c3*yf3);                   \
  sp = (MLIB_TYPE*)((mlib_addr)sp - 3*srcYStride);              \
  LOAD_FIRST_ROW(4, 2);                                         \
  STORE_SAT_VALUE_##lut_format(1);                              \
  c0 = (s0*xf0 + s1*xf1 + s2*xf2 + s3*xf3);                     \
  COUNT_NEXT_ROW(c1, 4, 2);                                     \
  COUNT_NEXT_ROW(c2, 4, 2);                                     \
  COUNT_NEXT_ROW(c3, 4, 2);                                     \
  val0 = (c0*yf0 + c1*yf1 + c2*yf2 + c3*yf3);                   \
  sp = (MLIB_TYPE*)((mlib_addr)sp - 3*srcYStride);              \
  LOAD_FIRST_ROW(4, 3);                                         \
  STORE_SAT_VALUE_##lut_format(2);                              \
  c0 = (s0*xf0 + s1*xf1 + s2*xf2 + s3*xf3);                     \
  COUNT_NEXT_ROW(c1, 4, 3);                                     \
  COUNT_NEXT_ROW(c2, 4, 3);                                     \
  COUNT_NEXT_ROW(c3, 4, 3);                                     \
  val0 = (c0*yf0 + c1*yf1 + c2*yf2 + c3*yf3);                   \
  STORE_SAT_VALUE_##lut_format(3);

/***************************************************************/
#define FILTER_U8  ((filter == MLIB_BICUBIC) ? mlib_filters_u8f_bc  : mlib_filters_u8f_bc2)
#define FILTER_S16 ((filter == MLIB_BICUBIC) ? mlib_filters_s16f_bc : mlib_filters_s16f_bc2)

/***************************************************************/
#define mlib_U8  mlib_u8
#define mlib_S16 mlib_s16

/***************************************************************/
#define FUNC_AFFINEINDEX_BC_0(ITYPE, LTYPE, NCHAN)                                                  \
  mlib_status mlib_ImageAffineIndex_##ITYPE##_##LTYPE##_##NCHAN##CH_BC(mlib_affine_param *param,    \
                                                                       const void        *colormap) \
  {                                                                                                 \
    DECLAREVAR_IND();                                                                               \
    mlib_##LTYPE buff_lcl[NCHAN * MLIB_LIMIT], *pbuff = buff_lcl, *dp;                              \
    mlib_d64  *lut = ((mlib_d64*)mlib_ImageGetLutDoubleData(colormap) -                             \
                      NCHAN * mlib_ImageGetLutOffset(colormap));                                    \
    const mlib_f32 *mlib_filters_table = FILTER_##LTYPE;                                            \
                                                                                                    \
    if (max_xsize > MLIB_LIMIT) {                                                                   \
      pbuff = mlib_malloc(NCHAN * sizeof(mlib_##LTYPE) * max_xsize);                                \
      if (pbuff == NULL) return MLIB_FAILURE;                                                       \
    }                                                                                               \
                                                                                                    \
    for (j = yStart; j <= yFinish; j++) {                                                           \
                                                                                                    \
      NEW_LINE(1);                                                                                  \
      dp = pbuff;                                                                                   \
                                                                                                    \
      GET_FILTERS_KOEF();                                                                           \
      GET_POINTER();                                                                                \
      LOAD_FIRST_ROW(NCHAN, 0);

    /* pragma pipeloop(0) must be here */

/***************************************************************/
#define FUNC_AFFINEINDEX_BC_1(ITYPE, LTYPE, NCHAN)                         \
                                                                           \
      for (i = 0; i < (xRight - xLeft); i++, dp += NCHAN) {                \
        MAKE_BC_##NCHAN##CH(LTYPE);                                        \
      }                                                                    \
                                                                           \
      MAKE_LAST_PIXEL_BC_##NCHAN##CH(LTYPE);                               \
                                                                           \
      mlib_ImageColorTrue2IndexLine_##LTYPE##_##ITYPE##_##NCHAN            \
                                (pbuff, dl, xRight - xLeft + 1, colormap); \
    }                                                                      \
                                                                           \
    if (pbuff != buff_lcl) mlib_free(pbuff);                               \
                                                                           \
    return MLIB_SUCCESS;                                                   \
  }

/***************************************************************/
#undef MLIB_TYPE
#define MLIB_TYPE mlib_u8

/***************************************************************/
#undef  FILTER_SHIFT
#define FILTER_SHIFT 4
#undef  FILTER_MASK
#define FILTER_MASK  (((1 << 8) - 1) << 4)

FUNC_AFFINEINDEX_BC_0(U8, U8, 3)
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
FUNC_AFFINEINDEX_BC_1(U8, U8, 3)

/***************************************************************/
#undef  FILTER_SHIFT
#define FILTER_SHIFT 3
#undef  FILTER_MASK
#define FILTER_MASK  (((1 << 9) - 1) << 4)

FUNC_AFFINEINDEX_BC_0(U8, S16, 3)
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
FUNC_AFFINEINDEX_BC_1(U8, S16, 3)

/***************************************************************/
#undef  FILTER_SHIFT
#define FILTER_SHIFT 4
#undef  FILTER_MASK
#define FILTER_MASK  (((1 << 8) - 1) << 4)

FUNC_AFFINEINDEX_BC_0(U8, U8, 4)
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
FUNC_AFFINEINDEX_BC_1(U8, U8, 4)

/***************************************************************/
#undef  FILTER_SHIFT
#define FILTER_SHIFT 3
#undef  FILTER_MASK
#define FILTER_MASK  (((1 << 9) - 1) << 4)

FUNC_AFFINEINDEX_BC_0(U8, S16, 4)
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
FUNC_AFFINEINDEX_BC_1(U8, S16, 4)

/***************************************************************/
#undef  MLIB_TYPE
#define MLIB_TYPE mlib_s16

/***************************************************************/
#undef  FILTER_SHIFT
#define FILTER_SHIFT 4
#undef  FILTER_MASK
#define FILTER_MASK  (((1 << 8) - 1) << 4)

FUNC_AFFINEINDEX_BC_0(S16, U8, 3)
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
FUNC_AFFINEINDEX_BC_1(S16, U8, 3)

/***************************************************************/
#undef  FILTER_SHIFT
#define FILTER_SHIFT 3
#undef  FILTER_MASK
#define FILTER_MASK  (((1 << 9) - 1) << 4)

FUNC_AFFINEINDEX_BC_0(S16, S16, 3)
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
FUNC_AFFINEINDEX_BC_1(S16, S16, 3)

/***************************************************************/
#undef  FILTER_SHIFT
#define FILTER_SHIFT 4
#undef  FILTER_MASK
#define FILTER_MASK  (((1 << 8) - 1) << 4)

FUNC_AFFINEINDEX_BC_0(S16, U8, 4)
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
FUNC_AFFINEINDEX_BC_1(S16, U8, 4)

/***************************************************************/
#undef  FILTER_SHIFT
#define FILTER_SHIFT 3
#undef  FILTER_MASK
#define FILTER_MASK  (((1 << 9) - 1) << 4)

FUNC_AFFINEINDEX_BC_0(S16, S16, 4)
#ifdef __SUNPRO_C
#pragma pipeloop(0)
#endif /* __SUNPRO_C */
FUNC_AFFINEINDEX_BC_1(S16, S16, 4)

/***************************************************************/
