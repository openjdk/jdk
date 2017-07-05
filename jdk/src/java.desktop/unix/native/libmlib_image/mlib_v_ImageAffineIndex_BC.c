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



#include "vis_proto.h"
#include "mlib_image.h"
#include "mlib_ImageColormap.h"
#include "mlib_ImageAffine.h"
#include "mlib_v_ImageFilters.h"

/***************************************************************/
#define MLIB_LIMIT   512
#define MLIB_SHIFT    16

/***************************************************************/
#undef  DECLAREVAR
#define DECLAREVAR()                                            \
  DECLAREVAR0();                                                \
  mlib_s32  *warp_tbl   = param -> warp_tbl;                    \
  mlib_s32  xSrc, ySrc;                                         \
  mlib_s32  srcYStride = param -> srcYStride;                   \
  mlib_s32  filter     = param -> filter;                       \
  mlib_s32  max_xsize  = param -> max_xsize;                    \
  MLIB_TYPE *srcIndexPtr;                                       \
  MLIB_TYPE *dstIndexPtr;                                       \
  mlib_d64  *dstPixelPtr;                                       \
  mlib_s32  i

/***************************************************************/
#define DECLAREVAR_U8()                                         \
  mlib_s32  filterposx, filterposy;                             \
  mlib_d64  sum0, sum1, sum2, sum3;                             \
  mlib_f32  hi_row00, hi_row10, hi_row20, hi_row30;             \
  mlib_f32  hi_row01, hi_row11, hi_row21, hi_row31;             \
  mlib_f32  lo_row00, lo_row10, lo_row20, lo_row30;             \
  mlib_f32  lo_row01, lo_row11, lo_row21, lo_row31;             \
  mlib_d64  xFilter0, xFilter1, xFilter2, xFilter3, yFilter;    \
  mlib_d64  v00, v10, v20, v30;                                 \
  mlib_d64  v01, v11, v21, v31;                                 \
  mlib_d64  v02, v12, v22, v32;                                 \
  mlib_d64  v03, v13, v23, v33;                                 \
  mlib_d64  d0, d1, d2, d3;                                     \
  mlib_d64  d00, d10, d20, d30;                                 \
  mlib_d64  d01, d11, d21, d31;                                 \
  mlib_s32  cols;                                               \
  mlib_d64  res, *xPtr

/***************************************************************/
#define DECLAREVAR_S16()                                        \
  mlib_s32  filterposx, filterposy;                             \
  mlib_d64  sum0, sum1, sum2, sum3;                             \
  mlib_d64  row00, row10, row20, row30;                         \
  mlib_d64  row01, row11, row21, row31;                         \
  mlib_d64  row02, row12, row22, row32;                         \
  mlib_d64  row03, row13, row23, row33;                         \
  mlib_d64  xFilter0, xFilter1, xFilter2, xFilter3;             \
  mlib_d64  yFilter0, yFilter1, yFilter2, yFilter3;             \
  mlib_d64  v00, v01, v02, v03, v10, v11, v12, v13;             \
  mlib_d64  v20, v21, v22, v23, v30, v31, v32, v33;             \
  mlib_d64  u00, u01, u10, u11, u20, u21, u30, u31;             \
  mlib_d64  d0, d1, d2, d3;                                     \
  mlib_d64  *yPtr, *xPtr;                                       \
  mlib_s32  cols;                                               \
  mlib_d64  res;                                                \
  mlib_f32  f_x01000100 = vis_to_float(0x01000100)

/***************************************************************/
#undef  CLIP
#define CLIP()                                                  \
  dstData += dstYStride;                                        \
  xLeft = leftEdges[j];                                         \
  xRight = rightEdges[j];                                       \
  X = xStarts[j];                                               \
  Y = yStarts[j];                                               \
  PREPARE_DELTAS                                                \
  if (xLeft > xRight)                                           \
    continue;                                                   \
  dstIndexPtr = (MLIB_TYPE *)dstData + xLeft;                   \
  dstPixelPtr = dstRowPtr

/***************************************************************/
#define FADD_4BC_U8()                                           \
  d0 = vis_fpadd16(d00, d10);                                   \
  d1 = vis_fpadd16(d20, d30);                                   \
  d0 = vis_fpadd16(d0, d1);                                     \
  d2 = vis_fpadd16(d01, d11);                                   \
  d3 = vis_fpadd16(d21, d31);                                   \
  d2 = vis_fpadd16(d2, d3);                                     \
  res = vis_fpack16_pair(d0, d2)

/***************************************************************/
#define LOAD_BC_U8_4CH_1PIXEL(mlib_filters_u8, mlib_filters_u8_4)      \
  filterposy = (Y >> FILTER_SHIFT) & FILTER_MASK;                      \
  yFilter = *((mlib_d64 *) ((mlib_u8 *)mlib_filters_u8 + filterposy)); \
  filterposx = (X >> FILTER_SHIFT) & FILTER_MASK;                      \
  xPtr = ((mlib_d64 *)((mlib_u8 *)mlib_filters_u8_4+4*filterposx));    \
  xFilter0 = xPtr[0];                                                  \
  xFilter1 = xPtr[1];                                                  \
  xFilter2 = xPtr[2];                                                  \
  xFilter3 = xPtr[3];                                                  \
  X += dX;                                                             \
  Y += dY;                                                             \
  hi_row00 = flut[srcIndexPtr[0]];                                     \
  lo_row00 = flut[srcIndexPtr[1]];                                     \
  hi_row01 = flut[srcIndexPtr[2]];                                     \
  lo_row01 = flut[srcIndexPtr[3]];                                     \
  srcIndexPtr += srcYStride;                                           \
  hi_row10 = flut[srcIndexPtr[0]];                                     \
  lo_row10 = flut[srcIndexPtr[1]];                                     \
  hi_row11 = flut[srcIndexPtr[2]];                                     \
  lo_row11 = flut[srcIndexPtr[3]];                                     \
  srcIndexPtr += srcYStride;                                           \
  hi_row20 = flut[srcIndexPtr[0]];                                     \
  lo_row20 = flut[srcIndexPtr[1]];                                     \
  hi_row21 = flut[srcIndexPtr[2]];                                     \
  lo_row21 = flut[srcIndexPtr[3]];                                     \
  srcIndexPtr += srcYStride;                                           \
  hi_row30 = flut[srcIndexPtr[0]];                                     \
  lo_row30 = flut[srcIndexPtr[1]];                                     \
  hi_row31 = flut[srcIndexPtr[2]];                                     \
  lo_row31 = flut[srcIndexPtr[3]]

/***************************************************************/
#define NEXT_PIXEL_4BC()                                        \
  xSrc = (X >> MLIB_SHIFT)-1;                                   \
  ySrc = (Y >> MLIB_SHIFT)-1;                                   \
  srcIndexPtr = (MLIB_TYPE *)lineAddr[ySrc] + xSrc

/***************************************************************/
#define RESULT_4BC_U8_1PIXEL(ind)                               \
  v00 = vis_fmul8x16au(hi_row00, vis_read_hi(yFilter));         \
  v01 = vis_fmul8x16au(lo_row00, vis_read_hi(yFilter));         \
  v02 = vis_fmul8x16au(hi_row01, vis_read_hi(yFilter));         \
  v03 = vis_fmul8x16au(lo_row01, vis_read_hi(yFilter));         \
  v10 = vis_fmul8x16al(hi_row10, vis_read_hi(yFilter));         \
  v11 = vis_fmul8x16al(lo_row10, vis_read_hi(yFilter));         \
  sum0 = vis_fpadd16(v00, v10);                                 \
  v12 = vis_fmul8x16al(hi_row11, vis_read_hi(yFilter));         \
  sum1 = vis_fpadd16(v01, v11);                                 \
  v13 = vis_fmul8x16al(lo_row11, vis_read_hi(yFilter));         \
  sum2 = vis_fpadd16(v02, v12);                                 \
  v20 = vis_fmul8x16au(hi_row20, vis_read_lo(yFilter));         \
  sum3 = vis_fpadd16(v03, v13);                                 \
  v21 = vis_fmul8x16au(lo_row20, vis_read_lo(yFilter));         \
  sum0 = vis_fpadd16(sum0, v20);                                \
  v22 = vis_fmul8x16au(hi_row21, vis_read_lo(yFilter));         \
  sum1 = vis_fpadd16(sum1, v21);                                \
  v23 = vis_fmul8x16au(lo_row21, vis_read_lo(yFilter));         \
  sum2 = vis_fpadd16(sum2, v22);                                \
  v30 = vis_fmul8x16al(hi_row30, vis_read_lo(yFilter));         \
  sum3 = vis_fpadd16(sum3, v23);                                \
  v31 = vis_fmul8x16al(lo_row30, vis_read_lo(yFilter));         \
  sum0 = vis_fpadd16(sum0, v30);                                \
  v32 = vis_fmul8x16al(hi_row31, vis_read_lo(yFilter));         \
  sum1 = vis_fpadd16(sum1, v31);                                \
  v33 = vis_fmul8x16al(lo_row31, vis_read_lo(yFilter));         \
  sum2 = vis_fpadd16(sum2, v32);                                \
  v00 = vis_fmul8sux16(sum0, xFilter0);                         \
  sum3 = vis_fpadd16(sum3, v33);                                \
  v01 = vis_fmul8ulx16(sum0, xFilter0);                         \
  v10 = vis_fmul8sux16(sum1, xFilter1);                         \
  d0##ind = vis_fpadd16(v00, v01);                              \
  v11 = vis_fmul8ulx16(sum1, xFilter1);                         \
  v20 = vis_fmul8sux16(sum2, xFilter2);                         \
  d1##ind = vis_fpadd16(v10, v11);                              \
  v21 = vis_fmul8ulx16(sum2, xFilter2);                         \
  v30 = vis_fmul8sux16(sum3, xFilter3);                         \
  d2##ind = vis_fpadd16(v20, v21);                              \
  v31 = vis_fmul8ulx16(sum3, xFilter3);                         \
  d3##ind = vis_fpadd16(v30, v31)

/***************************************************************/
#define BC_U8_4CH(ind, mlib_filters_u8, mlib_filters_u8_4)            \
  v00 = vis_fmul8x16au(hi_row00, vis_read_hi(yFilter));               \
  v01 = vis_fmul8x16au(lo_row00, vis_read_hi(yFilter));               \
  v02 = vis_fmul8x16au(hi_row01, vis_read_hi(yFilter));               \
  v03 = vis_fmul8x16au(lo_row01, vis_read_hi(yFilter));               \
  hi_row00 = flut[srcIndexPtr[0]];                                    \
  filterposy = (Y >> FILTER_SHIFT);                                   \
  v10 = vis_fmul8x16al(hi_row10, vis_read_hi(yFilter));               \
  lo_row00 = flut[srcIndexPtr[1]];                                    \
  v11 = vis_fmul8x16al(lo_row10, vis_read_hi(yFilter));               \
  sum0 = vis_fpadd16(v00, v10);                                       \
  hi_row01 = flut[srcIndexPtr[2]];                                    \
  v12 = vis_fmul8x16al(hi_row11, vis_read_hi(yFilter));               \
  lo_row01 = flut[srcIndexPtr[3]];                                    \
  filterposx = (X >> FILTER_SHIFT);                                   \
  v13 = vis_fmul8x16al(lo_row11, vis_read_hi(yFilter));               \
  srcIndexPtr += srcYStride;                                          \
  hi_row10 = flut[srcIndexPtr[0]];                                    \
  v20 = vis_fmul8x16au(hi_row20, vis_read_lo(yFilter));               \
  sum1 = vis_fpadd16(v01, v11);                                       \
  lo_row10 = flut[srcIndexPtr[1]];                                    \
  X += dX;                                                            \
  hi_row11 = flut[srcIndexPtr[2]];                                    \
  v21 = vis_fmul8x16au(lo_row20, vis_read_lo(yFilter));               \
  sum2 = vis_fpadd16(v02, v12);                                       \
  lo_row11 = flut[srcIndexPtr[3]];                                    \
  v22 = vis_fmul8x16au(hi_row21, vis_read_lo(yFilter));               \
  srcIndexPtr += srcYStride;                                          \
  hi_row20 = flut[srcIndexPtr[0]];                                    \
  v23 = vis_fmul8x16au(lo_row21, vis_read_lo(yFilter));               \
  sum3 = vis_fpadd16(v03, v13);                                       \
  Y += dY;                                                            \
  xSrc = (X >> MLIB_SHIFT)-1;                                         \
  v30 = vis_fmul8x16al(hi_row30, vis_read_lo(yFilter));               \
  sum0 = vis_fpadd16(sum0, v20);                                      \
  lo_row20 = flut[srcIndexPtr[1]];                                    \
  ySrc = (Y >> MLIB_SHIFT)-1;                                         \
  hi_row21 = flut[srcIndexPtr[2]];                                    \
  v31 = vis_fmul8x16al(lo_row30, vis_read_lo(yFilter));               \
  sum1 = vis_fpadd16(sum1, v21);                                      \
  filterposy &= FILTER_MASK;                                          \
  lo_row21 = flut[srcIndexPtr[3]];                                    \
  v32 = vis_fmul8x16al(hi_row31, vis_read_lo(yFilter));               \
  srcIndexPtr += srcYStride;                                          \
  filterposx &= FILTER_MASK;                                          \
  v33 = vis_fmul8x16al(lo_row31, vis_read_lo(yFilter));               \
  sum2 = vis_fpadd16(sum2, v22);                                      \
  hi_row30 = flut[srcIndexPtr[0]];                                    \
  sum3 = vis_fpadd16(sum3, v23);                                      \
  sum0 = vis_fpadd16(sum0, v30);                                      \
  lo_row30 = flut[srcIndexPtr[1]];                                    \
  sum1 = vis_fpadd16(sum1, v31);                                      \
  v00 = vis_fmul8sux16(sum0, xFilter0);                               \
  hi_row31 = flut[srcIndexPtr[2]];                                    \
  sum2 = vis_fpadd16(sum2, v32);                                      \
  v01 = vis_fmul8ulx16(sum0, xFilter0);                               \
  sum3 = vis_fpadd16(sum3, v33);                                      \
  lo_row31 = flut[srcIndexPtr[3]];                                    \
  v10 = vis_fmul8sux16(sum1, xFilter1);                               \
  d0##ind = vis_fpadd16(v00, v01);                                    \
  yFilter = *((mlib_d64 *)((mlib_u8 *)mlib_filters_u8 + filterposy)); \
  v11 = vis_fmul8ulx16(sum1, xFilter1);                               \
  xPtr = ((mlib_d64 *)((mlib_u8 *)mlib_filters_u8_4+4*filterposx));   \
  xFilter0 = xPtr[0];                                                 \
  v20 = vis_fmul8sux16(sum2, xFilter2);                               \
  d1##ind = vis_fpadd16(v10, v11);                                    \
  xFilter1 = xPtr[1];                                                 \
  v21 = vis_fmul8ulx16(sum2, xFilter2);                               \
  xFilter2 = xPtr[2];                                                 \
  v30 = vis_fmul8sux16(sum3, xFilter3);                               \
  d2##ind = vis_fpadd16(v20, v21);                                    \
  xFilter3 = xPtr[3];                                                 \
  v31 = vis_fmul8ulx16(sum3, xFilter3);                               \
  srcIndexPtr = (MLIB_TYPE *)lineAddr[ySrc] + xSrc;                   \
  d3##ind = vis_fpadd16(v30, v31)

/***************************************************************/
#define LOAD_BC_S16_4CH_1PIXEL(mlib_filters_s16_4)                      \
  row00 = flut[srcIndexPtr[0]];                                         \
  row01 = flut[srcIndexPtr[1]];                                         \
  row02 = flut[srcIndexPtr[2]];                                         \
  row03 = flut[srcIndexPtr[3]];                                         \
  srcIndexPtr += srcYStride;                                            \
  row10 = flut[srcIndexPtr[0]];                                         \
  row11 = flut[srcIndexPtr[1]];                                         \
  row12 = flut[srcIndexPtr[2]];                                         \
  row13 = flut[srcIndexPtr[3]];                                         \
  srcIndexPtr += srcYStride;                                            \
  row20 = flut[srcIndexPtr[0]];                                         \
  row21 = flut[srcIndexPtr[1]];                                         \
  row22 = flut[srcIndexPtr[2]];                                         \
  row23 = flut[srcIndexPtr[3]];                                         \
  srcIndexPtr += srcYStride;                                            \
  row30 = flut[srcIndexPtr[0]];                                         \
  row31 = flut[srcIndexPtr[1]];                                         \
  row32 = flut[srcIndexPtr[2]];                                         \
  row33 = flut[srcIndexPtr[3]];                                         \
  filterposy = (Y >> FILTER_SHIFT) & FILTER_MASK;                       \
  yPtr = ((mlib_d64 *) ((mlib_u8 *)mlib_filters_s16_4 + filterposy*4)); \
  yFilter0 = yPtr[0];                                                   \
  yFilter1 = yPtr[1];                                                   \
  yFilter2 = yPtr[2];                                                   \
  yFilter3 = yPtr[3];                                                   \
  filterposx = (X >> FILTER_SHIFT) & FILTER_MASK;                       \
  xPtr = ((mlib_d64 *)((mlib_u8 *)mlib_filters_s16_4 + filterposx*4));  \
  xFilter0 = xPtr[0];                                                   \
  xFilter1 = xPtr[1];                                                   \
  xFilter2 = xPtr[2];                                                   \
  xFilter3 = xPtr[3];                                                   \
  X += dX;                                                              \
  Y += dY

/***************************************************************/
#define RESULT_4BC_S16_1PIXEL()                                 \
  u00 = vis_fmul8sux16(row00, yFilter0);                        \
  u01 = vis_fmul8ulx16(row00, yFilter0);                        \
  u10 = vis_fmul8sux16(row01, yFilter0);                        \
  u11 = vis_fmul8ulx16(row01, yFilter0);                        \
  v00 = vis_fpadd16(u00, u01);                                  \
  u20 = vis_fmul8sux16(row02, yFilter0);                        \
  v01 = vis_fpadd16(u10, u11);                                  \
  u21 = vis_fmul8ulx16(row02, yFilter0);                        \
  u30 = vis_fmul8sux16(row03, yFilter0);                        \
  u31 = vis_fmul8ulx16(row03, yFilter0);                        \
  v02 = vis_fpadd16(u20, u21);                                  \
  u00 = vis_fmul8sux16(row10, yFilter1);                        \
  u01 = vis_fmul8ulx16(row10, yFilter1);                        \
  v03 = vis_fpadd16(u30, u31);                                  \
  u10 = vis_fmul8sux16(row11, yFilter1);                        \
  u11 = vis_fmul8ulx16(row11, yFilter1);                        \
  v10 = vis_fpadd16(u00, u01);                                  \
  u20 = vis_fmul8sux16(row12, yFilter1);                        \
  v11 = vis_fpadd16(u10, u11);                                  \
  u21 = vis_fmul8ulx16(row12, yFilter1);                        \
  u30 = vis_fmul8sux16(row13, yFilter1);                        \
  u31 = vis_fmul8ulx16(row13, yFilter1);                        \
  u00 = vis_fmul8sux16(row20, yFilter2);                        \
  v12 = vis_fpadd16(u20, u21);                                  \
  u01 = vis_fmul8ulx16(row20, yFilter2);                        \
  v13 = vis_fpadd16(u30, u31);                                  \
  u10 = vis_fmul8sux16(row21, yFilter2);                        \
  u11 = vis_fmul8ulx16(row21, yFilter2);                        \
  v20 = vis_fpadd16(u00, u01);                                  \
  u20 = vis_fmul8sux16(row22, yFilter2);                        \
  sum0 = vis_fpadd16(v00, v10);                                 \
  u21 = vis_fmul8ulx16(row22, yFilter2);                        \
  u30 = vis_fmul8sux16(row23, yFilter2);                        \
  u31 = vis_fmul8ulx16(row23, yFilter2);                        \
  u00 = vis_fmul8sux16(row30, yFilter3);                        \
  u01 = vis_fmul8ulx16(row30, yFilter3);                        \
  v21 = vis_fpadd16(u10, u11);                                  \
  sum1 = vis_fpadd16(v01, v11);                                 \
  u10 = vis_fmul8sux16(row31, yFilter3);                        \
  sum2 = vis_fpadd16(v02, v12);                                 \
  sum3 = vis_fpadd16(v03, v13);                                 \
  v22 = vis_fpadd16(u20, u21);                                  \
  u11 = vis_fmul8ulx16(row31, yFilter3);                        \
  sum0 = vis_fpadd16(sum0, v20);                                \
  u20 = vis_fmul8sux16(row32, yFilter3);                        \
  u21 = vis_fmul8ulx16(row32, yFilter3);                        \
  v23 = vis_fpadd16(u30, u31);                                  \
  v30 = vis_fpadd16(u00, u01);                                  \
  sum1 = vis_fpadd16(sum1, v21);                                \
  u30 = vis_fmul8sux16(row33, yFilter3);                        \
  u31 = vis_fmul8ulx16(row33, yFilter3);                        \
  v31 = vis_fpadd16(u10, u11);                                  \
  sum2 = vis_fpadd16(sum2, v22);                                \
  sum3 = vis_fpadd16(sum3, v23);                                \
  v32 = vis_fpadd16(u20, u21);                                  \
  sum0 = vis_fpadd16(sum0, v30);                                \
  v33 = vis_fpadd16(u30, u31);                                  \
  v00 = vis_fmul8sux16(sum0, xFilter0);                         \
  sum1 = vis_fpadd16(sum1, v31);                                \
  sum2 = vis_fpadd16(sum2, v32);                                \
  v01 = vis_fmul8ulx16(sum0, xFilter0);                         \
  v10 = vis_fmul8sux16(sum1, xFilter1);                         \
  sum3 = vis_fpadd16(sum3, v33);                                \
  v11 = vis_fmul8ulx16(sum1, xFilter1);                         \
  d0 = vis_fpadd16(v00, v01);                                   \
  v20 = vis_fmul8sux16(sum2, xFilter2);                         \
  v21 = vis_fmul8ulx16(sum2, xFilter2);                         \
  d1 = vis_fpadd16(v10, v11);                                   \
  v30 = vis_fmul8sux16(sum3, xFilter3);                         \
  v31 = vis_fmul8ulx16(sum3, xFilter3);                         \
  d2 = vis_fpadd16(v20, v21);                                   \
  d3 = vis_fpadd16(v30, v31);                                   \
  d0 = vis_fpadd16(d0, d1);                                     \
  d2 = vis_fpadd16(d2, d3);                                     \
  d0 = vis_fpadd16(d0, d2);                                     \
  d2 = vis_fmuld8sux16(f_x01000100, vis_read_hi(d0));           \
  d3 = vis_fmuld8sux16(f_x01000100, vis_read_lo(d0));           \
  res = vis_fpackfix_pair(d2, d3)

/***************************************************************/
#define BC_S16_4CH(mlib_filters_s16_4)                                  \
  u00 = vis_fmul8sux16(row00, yFilter0);                                \
  u01 = vis_fmul8ulx16(row00, yFilter0);                                \
  u10 = vis_fmul8sux16(row01, yFilter0);                                \
  u11 = vis_fmul8ulx16(row01, yFilter0);                                \
  v00 = vis_fpadd16(u00, u01);                                          \
  u20 = vis_fmul8sux16(row02, yFilter0);                                \
  v01 = vis_fpadd16(u10, u11);                                          \
  u21 = vis_fmul8ulx16(row02, yFilter0);                                \
  u30 = vis_fmul8sux16(row03, yFilter0);                                \
  u31 = vis_fmul8ulx16(row03, yFilter0);                                \
  v02 = vis_fpadd16(u20, u21);                                          \
  row00 = flut[srcIndexPtr[0]];                                         \
  u00 = vis_fmul8sux16(row10, yFilter1);                                \
  u01 = vis_fmul8ulx16(row10, yFilter1);                                \
  filterposy = (Y >> FILTER_SHIFT);                                     \
  v03 = vis_fpadd16(u30, u31);                                          \
  row01 = flut[srcIndexPtr[1]];                                         \
  u10 = vis_fmul8sux16(row11, yFilter1);                                \
  u11 = vis_fmul8ulx16(row11, yFilter1);                                \
  v10 = vis_fpadd16(u00, u01);                                          \
  row02 = flut[srcIndexPtr[2]];                                         \
  u20 = vis_fmul8sux16(row12, yFilter1);                                \
  v11 = vis_fpadd16(u10, u11);                                          \
  u21 = vis_fmul8ulx16(row12, yFilter1);                                \
  u30 = vis_fmul8sux16(row13, yFilter1);                                \
  row03 = flut[srcIndexPtr[3]];                                         \
  u31 = vis_fmul8ulx16(row13, yFilter1);                                \
  u00 = vis_fmul8sux16(row20, yFilter2);                                \
  filterposx = (X >> FILTER_SHIFT);                                     \
  srcIndexPtr += srcYStride;                                            \
  v12 = vis_fpadd16(u20, u21);                                          \
  u01 = vis_fmul8ulx16(row20, yFilter2);                                \
  v13 = vis_fpadd16(u30, u31);                                          \
  row10 = flut[srcIndexPtr[0]];                                         \
  u10 = vis_fmul8sux16(row21, yFilter2);                                \
  X += dX;                                                              \
  u11 = vis_fmul8ulx16(row21, yFilter2);                                \
  v20 = vis_fpadd16(u00, u01);                                          \
  row11 = flut[srcIndexPtr[1]];                                         \
  u20 = vis_fmul8sux16(row22, yFilter2);                                \
  sum0 = vis_fpadd16(v00, v10);                                         \
  u21 = vis_fmul8ulx16(row22, yFilter2);                                \
  row12 = flut[srcIndexPtr[2]];                                         \
  u30 = vis_fmul8sux16(row23, yFilter2);                                \
  u31 = vis_fmul8ulx16(row23, yFilter2);                                \
  row13 = flut[srcIndexPtr[3]];                                         \
  u00 = vis_fmul8sux16(row30, yFilter3);                                \
  srcIndexPtr += srcYStride;                                            \
  u01 = vis_fmul8ulx16(row30, yFilter3);                                \
  v21 = vis_fpadd16(u10, u11);                                          \
  Y += dY;                                                              \
  xSrc = (X >> MLIB_SHIFT)-1;                                           \
  sum1 = vis_fpadd16(v01, v11);                                         \
  row20 = flut[srcIndexPtr[0]];                                         \
  u10 = vis_fmul8sux16(row31, yFilter3);                                \
  sum2 = vis_fpadd16(v02, v12);                                         \
  sum3 = vis_fpadd16(v03, v13);                                         \
  ySrc = (Y >> MLIB_SHIFT)-1;                                           \
  row21 = flut[srcIndexPtr[1]];                                         \
  v22 = vis_fpadd16(u20, u21);                                          \
  u11 = vis_fmul8ulx16(row31, yFilter3);                                \
  sum0 = vis_fpadd16(sum0, v20);                                        \
  u20 = vis_fmul8sux16(row32, yFilter3);                                \
  row22 = flut[srcIndexPtr[2]];                                         \
  u21 = vis_fmul8ulx16(row32, yFilter3);                                \
  v23 = vis_fpadd16(u30, u31);                                          \
  v30 = vis_fpadd16(u00, u01);                                          \
  filterposy &= FILTER_MASK;                                            \
  sum1 = vis_fpadd16(sum1, v21);                                        \
  u30 = vis_fmul8sux16(row33, yFilter3);                                \
  row23 = flut[srcIndexPtr[3]];                                         \
  u31 = vis_fmul8ulx16(row33, yFilter3);                                \
  srcIndexPtr += srcYStride;                                            \
  filterposx &= FILTER_MASK;                                            \
  v31 = vis_fpadd16(u10, u11);                                          \
  row30 = flut[srcIndexPtr[0]];                                         \
  sum2 = vis_fpadd16(sum2, v22);                                        \
  sum3 = vis_fpadd16(sum3, v23);                                        \
  row31 = flut[srcIndexPtr[1]];                                         \
  v32 = vis_fpadd16(u20, u21);                                          \
  sum0 = vis_fpadd16(sum0, v30);                                        \
  row32 = flut[srcIndexPtr[2]];                                         \
  v33 = vis_fpadd16(u30, u31);                                          \
  row33 = flut[srcIndexPtr[3]];                                         \
  v00 = vis_fmul8sux16(sum0, xFilter0);                                 \
  yPtr = ((mlib_d64 *) ((mlib_u8 *)mlib_filters_s16_4 + filterposy*4)); \
  sum1 = vis_fpadd16(sum1, v31);                                        \
  yFilter0 = yPtr[0];                                                   \
  sum2 = vis_fpadd16(sum2, v32);                                        \
  v01 = vis_fmul8ulx16(sum0, xFilter0);                                 \
  yFilter1 = yPtr[1];                                                   \
  v10 = vis_fmul8sux16(sum1, xFilter1);                                 \
  sum3 = vis_fpadd16(sum3, v33);                                        \
  yFilter2 = yPtr[2];                                                   \
  v11 = vis_fmul8ulx16(sum1, xFilter1);                                 \
  d0 = vis_fpadd16(v00, v01);                                           \
  yFilter3 = yPtr[3];                                                   \
  xPtr = ((mlib_d64 *)((mlib_u8 *)mlib_filters_s16_4 + filterposx*4));  \
  v20 = vis_fmul8sux16(sum2, xFilter2);                                 \
  xFilter0 = xPtr[0];                                                   \
  v21 = vis_fmul8ulx16(sum2, xFilter2);                                 \
  d1 = vis_fpadd16(v10, v11);                                           \
  xFilter1 = xPtr[1];                                                   \
  v30 = vis_fmul8sux16(sum3, xFilter3);                                 \
  v31 = vis_fmul8ulx16(sum3, xFilter3);                                 \
  d2 = vis_fpadd16(v20, v21);                                           \
  xFilter2 = xPtr[2];                                                   \
  d3 = vis_fpadd16(v30, v31);                                           \
  xFilter3 = xPtr[3];                                                   \
  srcIndexPtr = (MLIB_TYPE *)lineAddr[ySrc] + xSrc

/***************************************************************/
#define FADD_4BC_S16()                                          \
  d0 = vis_fpadd16(d0, d1);                                     \
  d2 = vis_fpadd16(d2, d3);                                     \
  d0 = vis_fpadd16(d0, d2);                                     \
  d2 = vis_fmuld8sux16(f_x01000100, vis_read_hi(d0));           \
  d3 = vis_fmuld8sux16(f_x01000100, vis_read_lo(d0));           \
  res = vis_fpackfix_pair(d2, d3)

/***************************************************************/
#undef  MLIB_TYPE
#define MLIB_TYPE mlib_u8

/***************************************************************/
#undef  FILTER_SHIFT
#define FILTER_SHIFT  5
#undef  FILTER_MASK
#define FILTER_MASK   (((1 << 8) - 1) << 3)

/***************************************************************/
mlib_status mlib_ImageAffineIndex_U8_U8_3CH_BC(mlib_affine_param *param,
                                               const void        *colormap)
{
  DECLAREVAR();
  DECLAREVAR_U8();
  mlib_f32  *flut   = (mlib_f32 *)mlib_ImageGetLutNormalTable(colormap) -
  mlib_ImageGetLutOffset(colormap);
  mlib_d64  dstRowData[MLIB_LIMIT/2];
  mlib_d64  *dstRowPtr = dstRowData;
  const mlib_s16 *mlib_filters_table_u8, *mlib_filters_table_u8_4;

  if (filter == MLIB_BICUBIC) {
    mlib_filters_table_u8   = mlib_filters_u8_bc;
    mlib_filters_table_u8_4 = mlib_filters_u8_bc_4;
  } else {
    mlib_filters_table_u8   = mlib_filters_u8_bc2;
    mlib_filters_table_u8_4 = mlib_filters_u8_bc2_4;
  }

  if (max_xsize > MLIB_LIMIT) {
    dstRowPtr = mlib_malloc(sizeof(mlib_d64) * ((max_xsize + 1) >> 1));

    if (dstRowPtr == NULL) return MLIB_FAILURE;
  }

  vis_write_gsr(3 << 3);

  for (j = yStart; j <= yFinish; j++) {

    CLIP();

    cols = xRight - xLeft + 1;

    i = 0;

    if (i <= cols - 6) {

      NEXT_PIXEL_4BC();
      LOAD_BC_U8_4CH_1PIXEL(mlib_filters_table_u8, mlib_filters_table_u8_4);

      NEXT_PIXEL_4BC();

      BC_U8_4CH(0, mlib_filters_table_u8, mlib_filters_table_u8_4);
      BC_U8_4CH(1, mlib_filters_table_u8, mlib_filters_table_u8_4);
      FADD_4BC_U8();

      BC_U8_4CH(0, mlib_filters_table_u8, mlib_filters_table_u8_4);
      BC_U8_4CH(1, mlib_filters_table_u8, mlib_filters_table_u8_4);

#pragma pipeloop(0)
      for (; i <= cols-8; i += 2) {
        *dstPixelPtr++ = res;

        FADD_4BC_U8();
        BC_U8_4CH(0, mlib_filters_table_u8, mlib_filters_table_u8_4);
        BC_U8_4CH(1, mlib_filters_table_u8, mlib_filters_table_u8_4);
      }

      *dstPixelPtr++ = res;

      FADD_4BC_U8();
      *dstPixelPtr++ = res;

      RESULT_4BC_U8_1PIXEL(0);
      LOAD_BC_U8_4CH_1PIXEL(mlib_filters_table_u8, mlib_filters_table_u8_4);
      RESULT_4BC_U8_1PIXEL(1);
      FADD_4BC_U8();

      *dstPixelPtr++ = res;
      i += 6;
    }

    if (i <= cols-4) {
      NEXT_PIXEL_4BC();
      LOAD_BC_U8_4CH_1PIXEL(mlib_filters_table_u8, mlib_filters_table_u8_4);

      NEXT_PIXEL_4BC();

      BC_U8_4CH(0, mlib_filters_table_u8, mlib_filters_table_u8_4);
      BC_U8_4CH(1, mlib_filters_table_u8, mlib_filters_table_u8_4);
      FADD_4BC_U8();
      *dstPixelPtr++ = res;

      RESULT_4BC_U8_1PIXEL(0);
      LOAD_BC_U8_4CH_1PIXEL(mlib_filters_table_u8, mlib_filters_table_u8_4);
      RESULT_4BC_U8_1PIXEL(1);
      FADD_4BC_U8();

      *dstPixelPtr++ = res;
      i += 4;
    }

    if (i <= cols-2) {
      NEXT_PIXEL_4BC();
      LOAD_BC_U8_4CH_1PIXEL(mlib_filters_table_u8, mlib_filters_table_u8_4);
      RESULT_4BC_U8_1PIXEL(0);

      NEXT_PIXEL_4BC();
      LOAD_BC_U8_4CH_1PIXEL(mlib_filters_table_u8, mlib_filters_table_u8_4);
      RESULT_4BC_U8_1PIXEL(1);
      FADD_4BC_U8();

      *dstPixelPtr++ = res;
      i += 2;
    }

    if (i < cols) {
      NEXT_PIXEL_4BC();
      LOAD_BC_U8_4CH_1PIXEL(mlib_filters_table_u8, mlib_filters_table_u8_4);
      RESULT_4BC_U8_1PIXEL(0);

      d0 = vis_fpadd16(d00, d10);
      d1 = vis_fpadd16(d20, d30);
      d0 = vis_fpadd16(d0, d1);
      res = vis_fpack16_pair(d0, d0);
      *dstPixelPtr++ = res;
    }

    mlib_ImageColorTrue2IndexLine_U8_U8_3_in_4((mlib_u8 *)dstRowPtr,
                                               dstIndexPtr,
                                               xRight - xLeft + 1,
                                               colormap);
  }

  if (dstRowPtr != dstRowData) mlib_free(dstRowPtr);

  return MLIB_SUCCESS;
}

/***************************************************************/
#undef  FILTER_SHIFT
#define FILTER_SHIFT  4
#undef  FILTER_MASK
#define FILTER_MASK   (((1 << 9) - 1) << 3)

/***************************************************************/
mlib_status mlib_ImageAffineIndex_U8_S16_3CH_BC(mlib_affine_param *param,
                                                const void        *colormap)
{
  DECLAREVAR();
  DECLAREVAR_S16();
  mlib_d64 *flut   = (mlib_d64 *)mlib_ImageGetLutNormalTable(colormap) -
  mlib_ImageGetLutOffset(colormap);
  mlib_d64 dstRowData[MLIB_LIMIT];
  mlib_d64 *dstRowPtr = dstRowData;
  const mlib_s16 *mlib_filters_table_s16_4;

  if (filter == MLIB_BICUBIC) {
    mlib_filters_table_s16_4 = mlib_filters_s16_bc_4;
  } else {
    mlib_filters_table_s16_4 = mlib_filters_s16_bc2_4;
  }

  if (max_xsize > MLIB_LIMIT) {
    dstRowPtr = mlib_malloc(sizeof(mlib_d64) * max_xsize);

    if (dstRowPtr == NULL) return MLIB_FAILURE;
  }

  for (j = yStart; j <= yFinish; j++) {

    CLIP();

    vis_write_gsr(10 << 3);

    cols = xRight - xLeft + 1;
    i = 0;

    if (i <= cols - 4) {

      NEXT_PIXEL_4BC();
      LOAD_BC_S16_4CH_1PIXEL(mlib_filters_table_s16_4);

      NEXT_PIXEL_4BC();

      BC_S16_4CH(mlib_filters_table_s16_4);
      FADD_4BC_S16();

      BC_S16_4CH(mlib_filters_table_s16_4);

#pragma pipeloop(0)

      for (; i < cols-4; i++) {
        *dstPixelPtr++ = res;

        FADD_4BC_S16();
        BC_S16_4CH(mlib_filters_table_s16_4);
      }

      *dstPixelPtr++ = res;

      FADD_4BC_S16();
      *dstPixelPtr++ = res;

      RESULT_4BC_S16_1PIXEL();
      *dstPixelPtr++ = res;

      LOAD_BC_S16_4CH_1PIXEL(mlib_filters_table_s16_4);
      RESULT_4BC_S16_1PIXEL();
      *dstPixelPtr++ = res;
      i += 4;
    }

#pragma pipeloop(0)
    for (; i < cols; i++) {
      NEXT_PIXEL_4BC();
      LOAD_BC_S16_4CH_1PIXEL(mlib_filters_table_s16_4);
      RESULT_4BC_S16_1PIXEL();
      *dstPixelPtr++ = res;
    }

    mlib_ImageColorTrue2IndexLine_S16_U8_3_in_4((mlib_s16 *)dstRowPtr,
                                                dstIndexPtr,
                                                xRight - xLeft + 1,
                                                colormap);
  }

  if (dstRowPtr != dstRowData) mlib_free(dstRowPtr);

  return MLIB_SUCCESS;
}

/***************************************************************/
#undef  FILTER_SHIFT
#define FILTER_SHIFT  5
#undef  FILTER_MASK
#define FILTER_MASK   (((1 << 8) - 1) << 3)

/***************************************************************/
mlib_status mlib_ImageAffineIndex_U8_U8_4CH_BC(mlib_affine_param *param,
                                               const void        *colormap)
{
  DECLAREVAR();
  DECLAREVAR_U8();
  mlib_f32  *flut   = (mlib_f32 *)mlib_ImageGetLutNormalTable(colormap) -
  mlib_ImageGetLutOffset(colormap);
  mlib_d64  dstRowData[MLIB_LIMIT/2];
  mlib_d64  *dstRowPtr = dstRowData;
  const mlib_s16 *mlib_filters_table_u8, *mlib_filters_table_u8_4;

  if (filter == MLIB_BICUBIC) {
    mlib_filters_table_u8   = mlib_filters_u8_bc;
    mlib_filters_table_u8_4 = mlib_filters_u8_bc_4;
  } else {
    mlib_filters_table_u8   = mlib_filters_u8_bc2;
    mlib_filters_table_u8_4 = mlib_filters_u8_bc2_4;
  }

  if (max_xsize > MLIB_LIMIT) {
    dstRowPtr = mlib_malloc(sizeof(mlib_d64) * ((max_xsize + 1) >> 1));

    if (dstRowPtr == NULL) return MLIB_FAILURE;
  }

  vis_write_gsr(3 << 3);

  for (j = yStart; j <= yFinish; j++) {

    CLIP();

    cols = xRight - xLeft + 1;

    i = 0;

    if (i <= cols - 6) {

      NEXT_PIXEL_4BC();
      LOAD_BC_U8_4CH_1PIXEL(mlib_filters_table_u8, mlib_filters_table_u8_4);

      NEXT_PIXEL_4BC();

      BC_U8_4CH(0, mlib_filters_table_u8, mlib_filters_table_u8_4);
      BC_U8_4CH(1, mlib_filters_table_u8, mlib_filters_table_u8_4);
      FADD_4BC_U8();

      BC_U8_4CH(0, mlib_filters_table_u8, mlib_filters_table_u8_4);
      BC_U8_4CH(1, mlib_filters_table_u8, mlib_filters_table_u8_4);

#pragma pipeloop(0)
      for (; i <= cols-8; i += 2) {
        *dstPixelPtr++ = res;

        FADD_4BC_U8();
        BC_U8_4CH(0, mlib_filters_table_u8, mlib_filters_table_u8_4);
        BC_U8_4CH(1, mlib_filters_table_u8, mlib_filters_table_u8_4);
      }

      *dstPixelPtr++ = res;

      FADD_4BC_U8();
      *dstPixelPtr++ = res;

      RESULT_4BC_U8_1PIXEL(0);
      LOAD_BC_U8_4CH_1PIXEL(mlib_filters_table_u8, mlib_filters_table_u8_4);
      RESULT_4BC_U8_1PIXEL(1);
      FADD_4BC_U8();

      *dstPixelPtr++ = res;
      i += 6;
    }

    if (i <= cols-4) {
      NEXT_PIXEL_4BC();
      LOAD_BC_U8_4CH_1PIXEL(mlib_filters_table_u8, mlib_filters_table_u8_4);

      NEXT_PIXEL_4BC();

      BC_U8_4CH(0, mlib_filters_table_u8, mlib_filters_table_u8_4);
      BC_U8_4CH(1, mlib_filters_table_u8, mlib_filters_table_u8_4);
      FADD_4BC_U8();
      *dstPixelPtr++ = res;

      RESULT_4BC_U8_1PIXEL(0);
      LOAD_BC_U8_4CH_1PIXEL(mlib_filters_table_u8, mlib_filters_table_u8_4);
      RESULT_4BC_U8_1PIXEL(1);
      FADD_4BC_U8();

      *dstPixelPtr++ = res;
      i += 4;
    }

    if (i <= cols-2) {
      NEXT_PIXEL_4BC();
      LOAD_BC_U8_4CH_1PIXEL(mlib_filters_table_u8, mlib_filters_table_u8_4);
      RESULT_4BC_U8_1PIXEL(0);

      NEXT_PIXEL_4BC();
      LOAD_BC_U8_4CH_1PIXEL(mlib_filters_table_u8, mlib_filters_table_u8_4);
      RESULT_4BC_U8_1PIXEL(1);
      FADD_4BC_U8();

      *dstPixelPtr++ = res;
      i += 2;
    }

    if (i < cols) {
      NEXT_PIXEL_4BC();
      LOAD_BC_U8_4CH_1PIXEL(mlib_filters_table_u8, mlib_filters_table_u8_4);
      RESULT_4BC_U8_1PIXEL(0);

      d0 = vis_fpadd16(d00, d10);
      d1 = vis_fpadd16(d20, d30);
      d0 = vis_fpadd16(d0, d1);
      res = vis_fpack16_pair(d0, d0);
      *dstPixelPtr++ = res;
    }

    mlib_ImageColorTrue2IndexLine_U8_U8_4((mlib_u8 *)dstRowPtr,
                                          dstIndexPtr,
                                          xRight - xLeft + 1,
                                          colormap);
  }

  if (dstRowPtr != dstRowData) mlib_free(dstRowPtr);

  return MLIB_SUCCESS;
}

/***************************************************************/
#undef  FILTER_SHIFT
#define FILTER_SHIFT  4
#undef  FILTER_MASK
#define FILTER_MASK   (((1 << 9) - 1) << 3)

/***************************************************************/
mlib_status mlib_ImageAffineIndex_U8_S16_4CH_BC(mlib_affine_param *param,
                                                const void        *colormap)
{
  DECLAREVAR();
  DECLAREVAR_S16();
  mlib_d64 *flut   = (mlib_d64 *)mlib_ImageGetLutNormalTable(colormap) -
  mlib_ImageGetLutOffset(colormap);
  mlib_d64 dstRowData[MLIB_LIMIT];
  mlib_d64 *dstRowPtr = dstRowData;
  const mlib_s16 *mlib_filters_table_s16_4;

  if (filter == MLIB_BICUBIC) {
    mlib_filters_table_s16_4 = mlib_filters_s16_bc_4;
  } else {
    mlib_filters_table_s16_4 = mlib_filters_s16_bc2_4;
  }

  if (max_xsize > MLIB_LIMIT) {
    dstRowPtr = mlib_malloc(sizeof(mlib_d64) * max_xsize);

    if (dstRowPtr == NULL) return MLIB_FAILURE;
  }

  for (j = yStart; j <= yFinish; j++) {

    CLIP();

    vis_write_gsr(10 << 3);

    cols = xRight - xLeft + 1;
    i = 0;

    if (i <= cols - 4) {

      NEXT_PIXEL_4BC();
      LOAD_BC_S16_4CH_1PIXEL(mlib_filters_table_s16_4);

      NEXT_PIXEL_4BC();

      BC_S16_4CH(mlib_filters_table_s16_4);
      FADD_4BC_S16();

      BC_S16_4CH(mlib_filters_table_s16_4);

#pragma pipeloop(0)

      for (; i < cols-4; i++) {
        *dstPixelPtr++ = res;

        FADD_4BC_S16();
        BC_S16_4CH(mlib_filters_table_s16_4);
      }

      *dstPixelPtr++ = res;

      FADD_4BC_S16();
      *dstPixelPtr++ = res;

      RESULT_4BC_S16_1PIXEL();
      *dstPixelPtr++ = res;

      LOAD_BC_S16_4CH_1PIXEL(mlib_filters_table_s16_4);
      RESULT_4BC_S16_1PIXEL();
      *dstPixelPtr++ = res;
      i += 4;
    }

#pragma pipeloop(0)
    for (; i < cols; i++) {
      NEXT_PIXEL_4BC();
      LOAD_BC_S16_4CH_1PIXEL(mlib_filters_table_s16_4);
      RESULT_4BC_S16_1PIXEL();
      *dstPixelPtr++ = res;
    }

    mlib_ImageColorTrue2IndexLine_S16_U8_4((mlib_s16 *)dstRowPtr,
                                           dstIndexPtr,
                                           xRight - xLeft + 1,
                                           colormap);
  }

  if (dstRowPtr != dstRowData) mlib_free(dstRowPtr);

  return MLIB_SUCCESS;
}

/***************************************************************/
#undef  MLIB_TYPE
#define MLIB_TYPE mlib_s16

/***************************************************************/
#undef  FILTER_SHIFT
#define FILTER_SHIFT  5
#undef  FILTER_MASK
#define FILTER_MASK   (((1 << 8) - 1) << 3)

/***************************************************************/
mlib_status mlib_ImageAffineIndex_S16_U8_3CH_BC(mlib_affine_param *param,
                                                const void        *colormap)
{
  DECLAREVAR();
  DECLAREVAR_U8();
  mlib_f32  *flut   = (mlib_f32 *)mlib_ImageGetLutNormalTable(colormap) -
  mlib_ImageGetLutOffset(colormap);
  mlib_d64  dstRowData[MLIB_LIMIT/2];
  mlib_d64  *dstRowPtr = dstRowData;
  const mlib_s16 *mlib_filters_table_u8, *mlib_filters_table_u8_4;

  if (filter == MLIB_BICUBIC) {
    mlib_filters_table_u8   = mlib_filters_u8_bc;
    mlib_filters_table_u8_4 = mlib_filters_u8_bc_4;
  } else {
    mlib_filters_table_u8   = mlib_filters_u8_bc2;
    mlib_filters_table_u8_4 = mlib_filters_u8_bc2_4;
  }

  srcYStride >>= 1;

  if (max_xsize > MLIB_LIMIT) {
    dstRowPtr = mlib_malloc(sizeof(mlib_d64) * ((max_xsize + 1) >> 1));

    if (dstRowPtr == NULL) return MLIB_FAILURE;
  }

  vis_write_gsr(3 << 3);

  for (j = yStart; j <= yFinish; j++) {

    CLIP();

    cols = xRight - xLeft + 1;

    i = 0;

    if (i <= cols - 6) {

      NEXT_PIXEL_4BC();
      LOAD_BC_U8_4CH_1PIXEL(mlib_filters_table_u8, mlib_filters_table_u8_4);

      NEXT_PIXEL_4BC();

      BC_U8_4CH(0, mlib_filters_table_u8, mlib_filters_table_u8_4);
      BC_U8_4CH(1, mlib_filters_table_u8, mlib_filters_table_u8_4);
      FADD_4BC_U8();

      BC_U8_4CH(0, mlib_filters_table_u8, mlib_filters_table_u8_4);
      BC_U8_4CH(1, mlib_filters_table_u8, mlib_filters_table_u8_4);

#pragma pipeloop(0)
      for (; i <= cols-8; i += 2) {
        *dstPixelPtr++ = res;

        FADD_4BC_U8();
        BC_U8_4CH(0, mlib_filters_table_u8, mlib_filters_table_u8_4);
        BC_U8_4CH(1, mlib_filters_table_u8, mlib_filters_table_u8_4);
      }

      *dstPixelPtr++ = res;

      FADD_4BC_U8();
      *dstPixelPtr++ = res;

      RESULT_4BC_U8_1PIXEL(0);
      LOAD_BC_U8_4CH_1PIXEL(mlib_filters_table_u8, mlib_filters_table_u8_4);
      RESULT_4BC_U8_1PIXEL(1);
      FADD_4BC_U8();

      *dstPixelPtr++ = res;
      i += 6;
    }

    if (i <= cols-4) {
      NEXT_PIXEL_4BC();
      LOAD_BC_U8_4CH_1PIXEL(mlib_filters_table_u8, mlib_filters_table_u8_4);

      NEXT_PIXEL_4BC();

      BC_U8_4CH(0, mlib_filters_table_u8, mlib_filters_table_u8_4);
      BC_U8_4CH(1, mlib_filters_table_u8, mlib_filters_table_u8_4);
      FADD_4BC_U8();
      *dstPixelPtr++ = res;

      RESULT_4BC_U8_1PIXEL(0);
      LOAD_BC_U8_4CH_1PIXEL(mlib_filters_table_u8, mlib_filters_table_u8_4);
      RESULT_4BC_U8_1PIXEL(1);
      FADD_4BC_U8();

      *dstPixelPtr++ = res;
      i += 4;
    }

    if (i <= cols-2) {
      NEXT_PIXEL_4BC();
      LOAD_BC_U8_4CH_1PIXEL(mlib_filters_table_u8, mlib_filters_table_u8_4);
      RESULT_4BC_U8_1PIXEL(0);

      NEXT_PIXEL_4BC();
      LOAD_BC_U8_4CH_1PIXEL(mlib_filters_table_u8, mlib_filters_table_u8_4);
      RESULT_4BC_U8_1PIXEL(1);
      FADD_4BC_U8();

      *dstPixelPtr++ = res;
      i += 2;
    }

    if (i < cols) {
      NEXT_PIXEL_4BC();
      LOAD_BC_U8_4CH_1PIXEL(mlib_filters_table_u8, mlib_filters_table_u8_4);
      RESULT_4BC_U8_1PIXEL(0);

      d0 = vis_fpadd16(d00, d10);
      d1 = vis_fpadd16(d20, d30);
      d0 = vis_fpadd16(d0, d1);
      res = vis_fpack16_pair(d0, d0);
      *dstPixelPtr++ = res;
    }

    mlib_ImageColorTrue2IndexLine_U8_S16_3_in_4((mlib_u8 *)dstRowPtr,
                                                dstIndexPtr,
                                                xRight - xLeft + 1,
                                                colormap);
  }

  if (dstRowPtr != dstRowData) mlib_free(dstRowPtr);

  return MLIB_SUCCESS;
}

/***************************************************************/
#undef  FILTER_SHIFT
#define FILTER_SHIFT  4
#undef  FILTER_MASK
#define FILTER_MASK   (((1 << 9) - 1) << 3)

/***************************************************************/
mlib_status mlib_ImageAffineIndex_S16_S16_3CH_BC(mlib_affine_param *param,
                                                 const void        *colormap)
{
  DECLAREVAR();
  DECLAREVAR_S16();
  mlib_d64 *flut   = (mlib_d64 *)mlib_ImageGetLutNormalTable(colormap) -
  mlib_ImageGetLutOffset(colormap);
  mlib_d64 dstRowData[MLIB_LIMIT];
  mlib_d64 *dstRowPtr = dstRowData;
  const mlib_s16 *mlib_filters_table_s16_4;

  if (filter == MLIB_BICUBIC) {
    mlib_filters_table_s16_4 = mlib_filters_s16_bc_4;
  } else {
    mlib_filters_table_s16_4 = mlib_filters_s16_bc2_4;
  }

  srcYStride >>= 1;

  if (max_xsize > MLIB_LIMIT) {
    dstRowPtr = mlib_malloc(sizeof(mlib_d64) * max_xsize);

    if (dstRowPtr == NULL) return MLIB_FAILURE;
  }

  for (j = yStart; j <= yFinish; j++) {

    CLIP();

    vis_write_gsr(10 << 3);

    cols = xRight - xLeft + 1;
    i = 0;

    if (i <= cols - 4) {

      NEXT_PIXEL_4BC();
      LOAD_BC_S16_4CH_1PIXEL(mlib_filters_table_s16_4);

      NEXT_PIXEL_4BC();

      BC_S16_4CH(mlib_filters_table_s16_4);
      FADD_4BC_S16();

      BC_S16_4CH(mlib_filters_table_s16_4);

#pragma pipeloop(0)

      for (; i < cols-4; i++) {
        *dstPixelPtr++ = res;

        FADD_4BC_S16();
        BC_S16_4CH(mlib_filters_table_s16_4);
      }

      *dstPixelPtr++ = res;

      FADD_4BC_S16();
      *dstPixelPtr++ = res;

      RESULT_4BC_S16_1PIXEL();
      *dstPixelPtr++ = res;

      LOAD_BC_S16_4CH_1PIXEL(mlib_filters_table_s16_4);
      RESULT_4BC_S16_1PIXEL();
      *dstPixelPtr++ = res;
      i += 4;
    }

#pragma pipeloop(0)
    for (; i < cols; i++) {
      NEXT_PIXEL_4BC();
      LOAD_BC_S16_4CH_1PIXEL(mlib_filters_table_s16_4);
      RESULT_4BC_S16_1PIXEL();
      *dstPixelPtr++ = res;
    }

    mlib_ImageColorTrue2IndexLine_S16_S16_3_in_4((mlib_s16 *)dstRowPtr,
                                                 dstIndexPtr,
                                                 xRight - xLeft + 1,
                                                 colormap);
  }

  if (dstRowPtr != dstRowData) mlib_free(dstRowPtr);

  return MLIB_SUCCESS;
}

/***************************************************************/
#undef  FILTER_SHIFT
#define FILTER_SHIFT  5
#undef  FILTER_MASK
#define FILTER_MASK   (((1 << 8) - 1) << 3)

/***************************************************************/
mlib_status mlib_ImageAffineIndex_S16_U8_4CH_BC(mlib_affine_param *param,
                                                const void        *colormap)
{
  DECLAREVAR();
  DECLAREVAR_U8();
  mlib_f32  *flut   = (mlib_f32 *)mlib_ImageGetLutNormalTable(colormap) -
  mlib_ImageGetLutOffset(colormap);
  mlib_d64  dstRowData[MLIB_LIMIT/2];
  mlib_d64  *dstRowPtr = dstRowData;
  const mlib_s16 *mlib_filters_table_u8, *mlib_filters_table_u8_4;

  if (filter == MLIB_BICUBIC) {
    mlib_filters_table_u8   = mlib_filters_u8_bc;
    mlib_filters_table_u8_4 = mlib_filters_u8_bc_4;
  } else {
    mlib_filters_table_u8   = mlib_filters_u8_bc2;
    mlib_filters_table_u8_4 = mlib_filters_u8_bc2_4;
  }

  srcYStride >>= 1;

  if (max_xsize > MLIB_LIMIT) {
    dstRowPtr = mlib_malloc(sizeof(mlib_d64) * ((max_xsize + 1) >> 1));

    if (dstRowPtr == NULL) return MLIB_FAILURE;
  }

  vis_write_gsr(3 << 3);

  for (j = yStart; j <= yFinish; j++) {

    CLIP();

    cols = xRight - xLeft + 1;

    i = 0;

    if (i <= cols - 6) {

      NEXT_PIXEL_4BC();
      LOAD_BC_U8_4CH_1PIXEL(mlib_filters_table_u8, mlib_filters_table_u8_4);

      NEXT_PIXEL_4BC();

      BC_U8_4CH(0, mlib_filters_table_u8, mlib_filters_table_u8_4);
      BC_U8_4CH(1, mlib_filters_table_u8, mlib_filters_table_u8_4);
      FADD_4BC_U8();

      BC_U8_4CH(0, mlib_filters_table_u8, mlib_filters_table_u8_4);
      BC_U8_4CH(1, mlib_filters_table_u8, mlib_filters_table_u8_4);

#pragma pipeloop(0)
      for (; i <= cols-8; i += 2) {
        *dstPixelPtr++ = res;

        FADD_4BC_U8();
        BC_U8_4CH(0, mlib_filters_table_u8, mlib_filters_table_u8_4);
        BC_U8_4CH(1, mlib_filters_table_u8, mlib_filters_table_u8_4);
      }

      *dstPixelPtr++ = res;

      FADD_4BC_U8();
      *dstPixelPtr++ = res;

      RESULT_4BC_U8_1PIXEL(0);
      LOAD_BC_U8_4CH_1PIXEL(mlib_filters_table_u8, mlib_filters_table_u8_4);
      RESULT_4BC_U8_1PIXEL(1);
      FADD_4BC_U8();

      *dstPixelPtr++ = res;
      i += 6;
    }

    if (i <= cols-4) {
      NEXT_PIXEL_4BC();
      LOAD_BC_U8_4CH_1PIXEL(mlib_filters_table_u8, mlib_filters_table_u8_4);

      NEXT_PIXEL_4BC();

      BC_U8_4CH(0, mlib_filters_table_u8, mlib_filters_table_u8_4);
      BC_U8_4CH(1, mlib_filters_table_u8, mlib_filters_table_u8_4);
      FADD_4BC_U8();
      *dstPixelPtr++ = res;

      RESULT_4BC_U8_1PIXEL(0);
      LOAD_BC_U8_4CH_1PIXEL(mlib_filters_table_u8, mlib_filters_table_u8_4);
      RESULT_4BC_U8_1PIXEL(1);
      FADD_4BC_U8();

      *dstPixelPtr++ = res;
      i += 4;
    }

    if (i <= cols-2) {
      NEXT_PIXEL_4BC();
      LOAD_BC_U8_4CH_1PIXEL(mlib_filters_table_u8, mlib_filters_table_u8_4);
      RESULT_4BC_U8_1PIXEL(0);

      NEXT_PIXEL_4BC();
      LOAD_BC_U8_4CH_1PIXEL(mlib_filters_table_u8, mlib_filters_table_u8_4);
      RESULT_4BC_U8_1PIXEL(1);
      FADD_4BC_U8();

      *dstPixelPtr++ = res;
      i += 2;
    }

    if (i < cols) {
      NEXT_PIXEL_4BC();
      LOAD_BC_U8_4CH_1PIXEL(mlib_filters_table_u8, mlib_filters_table_u8_4);
      RESULT_4BC_U8_1PIXEL(0);

      d0 = vis_fpadd16(d00, d10);
      d1 = vis_fpadd16(d20, d30);
      d0 = vis_fpadd16(d0, d1);
      res = vis_fpack16_pair(d0, d0);
      *dstPixelPtr++ = res;
    }

    mlib_ImageColorTrue2IndexLine_U8_S16_4((mlib_u8 *)dstRowPtr,
                                           dstIndexPtr,
                                           xRight - xLeft + 1,
                                           colormap);
  }

  if (dstRowPtr != dstRowData) mlib_free(dstRowPtr);

  return MLIB_SUCCESS;
}

/***************************************************************/
#undef  FILTER_SHIFT
#define FILTER_SHIFT  4
#undef  FILTER_MASK
#define FILTER_MASK   (((1 << 9) - 1) << 3)

/***************************************************************/
mlib_status mlib_ImageAffineIndex_S16_S16_4CH_BC(mlib_affine_param *param,
                                                 const void        *colormap)
{
  DECLAREVAR();
  DECLAREVAR_S16();
  mlib_d64 *flut   = (mlib_d64 *)mlib_ImageGetLutNormalTable(colormap) -
  mlib_ImageGetLutOffset(colormap);
  mlib_d64 dstRowData[MLIB_LIMIT];
  mlib_d64 *dstRowPtr = dstRowData;
  const mlib_s16 *mlib_filters_table_s16_4;

  if (filter == MLIB_BICUBIC) {
    mlib_filters_table_s16_4 = mlib_filters_s16_bc_4;
  } else {
    mlib_filters_table_s16_4 = mlib_filters_s16_bc2_4;
  }

  srcYStride >>= 1;

  if (max_xsize > MLIB_LIMIT) {
    dstRowPtr = mlib_malloc(sizeof(mlib_d64) * max_xsize);

    if (dstRowPtr == NULL) return MLIB_FAILURE;
  }

  for (j = yStart; j <= yFinish; j++) {

    CLIP();

    vis_write_gsr(10 << 3);

    cols = xRight - xLeft + 1;
    i = 0;

    if (i <= cols - 4) {

      NEXT_PIXEL_4BC();
      LOAD_BC_S16_4CH_1PIXEL(mlib_filters_table_s16_4);

      NEXT_PIXEL_4BC();

      BC_S16_4CH(mlib_filters_table_s16_4);
      FADD_4BC_S16();

      BC_S16_4CH(mlib_filters_table_s16_4);

#pragma pipeloop(0)

      for (; i < cols-4; i++) {
        *dstPixelPtr++ = res;

        FADD_4BC_S16();
        BC_S16_4CH(mlib_filters_table_s16_4);
      }

      *dstPixelPtr++ = res;

      FADD_4BC_S16();
      *dstPixelPtr++ = res;

      RESULT_4BC_S16_1PIXEL();
      *dstPixelPtr++ = res;

      LOAD_BC_S16_4CH_1PIXEL(mlib_filters_table_s16_4);
      RESULT_4BC_S16_1PIXEL();
      *dstPixelPtr++ = res;
      i += 4;
    }

#pragma pipeloop(0)
    for (; i < cols; i++) {
      NEXT_PIXEL_4BC();
      LOAD_BC_S16_4CH_1PIXEL(mlib_filters_table_s16_4);
      RESULT_4BC_S16_1PIXEL();
      *dstPixelPtr++ = res;
    }

    mlib_ImageColorTrue2IndexLine_S16_S16_4((mlib_s16 *)dstRowPtr,
                                            dstIndexPtr,
                                            xRight - xLeft + 1,
                                            colormap);
  }

  if (dstRowPtr != dstRowData) mlib_free(dstRowPtr);

  return MLIB_SUCCESS;
}

/***************************************************************/
