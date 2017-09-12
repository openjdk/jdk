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



/*
 *      The functions step along the lines from xLeft to xRight and apply
 *      the bicubic filtering.
 *
 */

#include "vis_proto.h"
#include "mlib_ImageAffine.h"
#include "mlib_v_ImageFilters.h"

/*#define MLIB_VIS2*/

/***************************************************************/
#define DTYPE  mlib_u8

#define FILTER_BITS  8

/***************************************************************/
#ifdef MLIB_VIS2
#define MLIB_WRITE_BMASK(bmask) vis_write_bmask(bmask, 0)
#else
#define MLIB_WRITE_BMASK(bmask)
#endif /* MLIB_VIS2 */

/***************************************************************/
#define sPtr srcPixelPtr

/***************************************************************/
#define NEXT_PIXEL_1BC_U8()                                     \
  xSrc = (X>>MLIB_SHIFT)-1;                                     \
  ySrc = (Y>>MLIB_SHIFT)-1;                                     \
  sPtr = (mlib_u8 *)lineAddr[ySrc] + xSrc

/***************************************************************/
#ifndef MLIB_VIS2

#define ALIGN_ADDR(da, dp)                                      \
  da = vis_alignaddr(dp, 0)

#else

#define ALIGN_ADDR(da, dp)                                      \
  vis_alignaddr(dp, 0);                                         \
  da = (mlib_d64*)(((mlib_addr)(dp)) &~ 7)

#endif /* MLIB_VIS2 */

/***************************************************************/
#define LOAD_BC_U8_1CH_1PIXEL(mlib_filters_u8)                         \
  ALIGN_ADDR(dpSrc, sPtr);                                             \
  data0 = dpSrc[0];                                                    \
  data1 = dpSrc[1];                                                    \
  row00 = vis_faligndata(data0, data1);                                \
  sPtr += srcYStride;                                                  \
  ALIGN_ADDR(dpSrc, sPtr);                                             \
  data0 = dpSrc[0];                                                    \
  data1 = dpSrc[1];                                                    \
  row10 = vis_faligndata(data0, data1);                                \
  sPtr += srcYStride;                                                  \
  ALIGN_ADDR(dpSrc, sPtr);                                             \
  data0 = dpSrc[0];                                                    \
  data1 = dpSrc[1];                                                    \
  row20 = vis_faligndata(data0, data1);                                \
  sPtr += srcYStride;                                                  \
  ALIGN_ADDR(dpSrc, sPtr);                                             \
  data0 = dpSrc[0];                                                    \
  data1 = dpSrc[1];                                                    \
  row30 = vis_faligndata(data0, data1);                                \
  filterposy = (Y >> FILTER_SHIFT) & FILTER_MASK;                      \
  yFilter = *((mlib_d64 *) ((mlib_u8 *)mlib_filters_u8 + filterposy)); \
  filterposx = (X >> FILTER_SHIFT) & FILTER_MASK;                      \
  xFilter = *((mlib_d64 *)((mlib_u8 *)mlib_filters_u8 + filterposx));  \
  X += dX;                                                             \
  Y += dY

/***************************************************************/
#ifndef MLIB_VIS2

#define SUM_4x16(v1, v3)                                        \
  vis_alignaddr((void*)2, 0);                                   \
  v0 = vis_faligndata(v3, v3);                                  \
  v2 = vis_fpadd16(v3, v0);                                     \
  v1 = vis_write_lo(v1, vis_fpadd16s(vis_read_hi(v2), vis_read_lo(v2)))

#else

#define SUM_4x16(v1, v3)                                              \
  v2 = vis_freg_pair(vis_fpadd16s(vis_read_hi(v3), vis_read_lo(v3)),  \
                     vis_fpadd16s(vis_read_hi(v3), vis_read_lo(v3))); \
  v3 = vis_bshuffle(v2, v2);                                          \
  v1 = vis_write_lo(v1, vis_fpadd16s(vis_read_hi(v3), vis_read_lo(v3)))

#endif /* MLIB_VIS2 */

/***************************************************************/
#define RESULT_1BC_U8_1PIXEL(ind)                                    \
  v0 = vis_fmul8x16au(vis_read_hi(row0##ind), vis_read_hi(yFilter)); \
  v1 = vis_fmul8x16al(vis_read_hi(row1##ind), vis_read_hi(yFilter)); \
  sum = vis_fpadd16(v0, v1);                                         \
  v2 = vis_fmul8x16au(vis_read_hi(row2##ind), vis_read_lo(yFilter)); \
  sum = vis_fpadd16(sum, v2);                                        \
  v3 = vis_fmul8x16al(vis_read_hi(row3##ind), vis_read_lo(yFilter)); \
  sum = vis_fpadd16(sum, v3);                                        \
  v0 = vis_fmul8sux16(sum, xFilter);                                 \
  v1 = vis_fmul8ulx16(sum, xFilter);                                 \
  v3 = vis_fpadd16(v1, v0);                                          \
  SUM_4x16(v1, v3);                                                  \
  res = vis_write_lo(res, vis_fpack16(v1))

/***************************************************************/
#define BC_U8_1CH(index, ind1, ind2, mlib_filters_u8)                  \
  ALIGN_ADDR(dpSrc, sPtr);                                             \
  data0 = dpSrc[0];                                                    \
  v0 = vis_fmul8x16au(vis_read_hi(row0##ind1), vis_read_hi(yFilter));  \
  filterposy = (Y >> FILTER_SHIFT);                                    \
  data1 = dpSrc[1];                                                    \
  v1 = vis_fmul8x16al(vis_read_hi(row1##ind1), vis_read_hi(yFilter));  \
  row0##ind2 = vis_faligndata(data0, data1);                           \
  filterposx = (X >> FILTER_SHIFT);                                    \
  sPtr += srcYStride;                                                  \
  ALIGN_ADDR(dpSrc, sPtr);                                             \
  sum = vis_fpadd16(v0, v1);                                           \
  data0 = dpSrc[0];                                                    \
  v2 = vis_fmul8x16au(vis_read_hi(row2##ind1), vis_read_lo(yFilter));  \
  X += dX;                                                             \
  data1 = dpSrc[1];                                                    \
  row1##ind2 = vis_faligndata(data0, data1);                           \
  sPtr += srcYStride;                                                  \
  ALIGN_ADDR(dpSrc, sPtr);                                             \
  Y += dY;                                                             \
  sum = vis_fpadd16(sum, v2);                                          \
  xSrc = (X>>MLIB_SHIFT)-1;                                            \
  v3 = vis_fmul8x16al(vis_read_hi(row3##ind1), vis_read_lo(yFilter));  \
  data0 = dpSrc[0];                                                    \
  ySrc = (Y>>MLIB_SHIFT)-1;                                            \
  sum = vis_fpadd16(sum, v3);                                          \
  data1 = dpSrc[1];                                                    \
  filterposy &= FILTER_MASK;                                           \
  v0 = vis_fmul8sux16(sum, xFilter);                                   \
  row2##ind2 = vis_faligndata(data0, data1);                           \
  sPtr += srcYStride;                                                  \
  v1 = vis_fmul8ulx16(sum, xFilter);                                   \
  filterposx &= FILTER_MASK;                                           \
  ALIGN_ADDR(dpSrc, sPtr);                                             \
  data0 = dpSrc[0];                                                    \
  d##index = vis_fpadd16(v0, v1);                                      \
  data1 = dpSrc[1];                                                    \
  row3##ind2 = vis_faligndata(data0, data1);                           \
  yFilter = *((mlib_d64 *) ((mlib_u8 *)mlib_filters_u8 + filterposy)); \
  xFilter = *((mlib_d64 *)((mlib_u8 *)mlib_filters_u8 + filterposx));  \
  sPtr = (mlib_u8 *)lineAddr[ySrc] + xSrc

/***************************************************************/
#ifndef MLIB_VIS2

#define FADD_1BC_U8()                                           \
  p0 = vis_fpadd16s(vis_read_hi(d0), vis_read_lo(d0));          \
  p1 = vis_fpadd16s(vis_read_hi(d1), vis_read_lo(d1));          \
  p2 = vis_fpadd16s(vis_read_hi(d2), vis_read_lo(d2));          \
  p3 = vis_fpadd16s(vis_read_hi(d3), vis_read_lo(d3));          \
  m02 = vis_fpmerge(p0, p2);                                    \
  m13 = vis_fpmerge(p1, p3);                                    \
  m0213 = vis_fpmerge(vis_read_hi(m02), vis_read_hi(m13));      \
  e0 = vis_fpmerge(vis_read_hi(m0213), vis_read_lo(m0213));     \
  m0213 = vis_fpmerge(vis_read_lo(m02), vis_read_lo(m13));      \
  e1 = vis_fpmerge(vis_read_hi(m0213), vis_read_lo(m0213));     \
  res = vis_fpadd16(e0, e1)

#else

#define FADD_1BC_U8()                                                 \
  v0 = vis_freg_pair(vis_fpadd16s(vis_read_hi(d0), vis_read_lo(d0)),  \
                     vis_fpadd16s(vis_read_hi(d1), vis_read_lo(d1))); \
  v1 = vis_freg_pair(vis_fpadd16s(vis_read_hi(d2), vis_read_lo(d2)),  \
                     vis_fpadd16s(vis_read_hi(d3), vis_read_lo(d3))); \
  v2 = vis_bshuffle(v0, v0);                                          \
  v3 = vis_bshuffle(v1, v1);                                          \
  res = vis_freg_pair(vis_fpadd16s(vis_read_hi(v2), vis_read_lo(v2)), \
                      vis_fpadd16s(vis_read_hi(v3), vis_read_lo(v3)))

#endif /* MLIB_VIS2 */

/***************************************************************/
mlib_status mlib_ImageAffine_u8_1ch_bc (mlib_affine_param *param)
{
  DECLAREVAR_BC();
  mlib_s32  filterposx, filterposy;
  mlib_d64  data0, data1;
  mlib_d64  sum;
  mlib_d64  row00, row10, row20, row30;
  mlib_d64  row01, row11, row21, row31;
  mlib_d64  xFilter, yFilter;
  mlib_d64  v0, v1, v2, v3;
  mlib_d64  d0, d1, d2, d3;
#ifndef MLIB_VIS2
  mlib_f32  p0, p1, p2, p3;
  mlib_d64  e0, e1;
  mlib_d64  m02, m13, m0213;
#endif /* MLIB_VIS2 */
  mlib_d64  *dpSrc;
  mlib_s32  align, cols, i;
  mlib_d64  res;
  const mlib_s16 *mlib_filters_table;

  if (filter == MLIB_BICUBIC) {
    mlib_filters_table = mlib_filters_u8_bc;
  } else {
    mlib_filters_table = mlib_filters_u8_bc2;
  }

  for (j = yStart; j <= yFinish; j++) {

    vis_write_gsr(3 << 3);
    MLIB_WRITE_BMASK(0x0145ABEF);

    CLIP(1);

    cols = xRight - xLeft + 1;
    align = (4 - ((mlib_addr)dstPixelPtr) & 3) & 3;
    align = (cols < align)? cols : align;

    for (i = 0; i < align; i++) {
      NEXT_PIXEL_1BC_U8();
      LOAD_BC_U8_1CH_1PIXEL(mlib_filters_table);
      RESULT_1BC_U8_1PIXEL(0);
      vis_st_u8(res, dstPixelPtr++);
    }

    if (i <= cols - 10) {

      NEXT_PIXEL_1BC_U8();
      LOAD_BC_U8_1CH_1PIXEL(mlib_filters_table);

      NEXT_PIXEL_1BC_U8();

      BC_U8_1CH(0, 0, 1, mlib_filters_table);
      BC_U8_1CH(1, 1, 0, mlib_filters_table);
      BC_U8_1CH(2, 0, 1, mlib_filters_table);
      BC_U8_1CH(3, 1, 0, mlib_filters_table);

      FADD_1BC_U8();

      BC_U8_1CH(0, 0, 1, mlib_filters_table);
      BC_U8_1CH(1, 1, 0, mlib_filters_table);
      BC_U8_1CH(2, 0, 1, mlib_filters_table);
      BC_U8_1CH(3, 1, 0, mlib_filters_table);

#pragma pipeloop(0)
      for (; i <= cols - 14; i+=4) {
        *(mlib_f32*)dstPixelPtr = vis_fpack16(res);
        FADD_1BC_U8();
        BC_U8_1CH(0, 0, 1, mlib_filters_table);
        BC_U8_1CH(1, 1, 0, mlib_filters_table);
        BC_U8_1CH(2, 0, 1, mlib_filters_table);
        BC_U8_1CH(3, 1, 0, mlib_filters_table);
        dstPixelPtr += 4;
      }

      *(mlib_f32*)dstPixelPtr = vis_fpack16(res);
      dstPixelPtr += 4;
      FADD_1BC_U8();
      *(mlib_f32*)dstPixelPtr = vis_fpack16(res);
      dstPixelPtr += 4;

      RESULT_1BC_U8_1PIXEL(0);
      vis_st_u8(res, dstPixelPtr++);

      LOAD_BC_U8_1CH_1PIXEL(mlib_filters_table);
      RESULT_1BC_U8_1PIXEL(0);
      vis_st_u8(res, dstPixelPtr++);
      i += 10;
    }

    for (; i < cols; i++) {
      NEXT_PIXEL_1BC_U8();
      LOAD_BC_U8_1CH_1PIXEL(mlib_filters_table);
      RESULT_1BC_U8_1PIXEL(0);
      vis_st_u8(res, dstPixelPtr++);
    }
  }

  return MLIB_SUCCESS;
}

/***************************************************************/
#define FADD_2BC_U8()                                           \
  d0 = vis_fpadd16(d00, d10);                                   \
  d1 = vis_fpadd16(d01, d11);                                   \
  d2 = vis_fpadd16(d02, d12);                                   \
  d3 = vis_fpadd16(d03, d13);                                   \
  p0 = vis_fpadd16s(vis_read_hi(d0), vis_read_lo(d0));          \
  p1 = vis_fpadd16s(vis_read_hi(d1), vis_read_lo(d1));          \
  p2 = vis_fpadd16s(vis_read_hi(d2), vis_read_lo(d2));          \
  p3 = vis_fpadd16s(vis_read_hi(d3), vis_read_lo(d3));          \
  e0 = vis_freg_pair(p0, p1);                                   \
  e1 = vis_freg_pair(p2, p3);                                   \
  res = vis_fpack16_pair(e0, e1)

/***************************************************************/
#define LOAD_BC_U8_2CH_1PIXEL(mlib_filters_u8)                         \
  filterposy = (Y >> FILTER_SHIFT) & FILTER_MASK;                      \
  yFilter = *((mlib_d64 *) ((mlib_u8 *)mlib_filters_u8 + filterposy)); \
  filterposx = (X >> FILTER_SHIFT) & FILTER_MASK;                      \
  xFilter = *((mlib_d64 *)((mlib_u8 *)mlib_filters_u8 + filterposx));  \
  X += dX;                                                             \
  Y += dY;                                                             \
  ALIGN_ADDR(dpSrc, sPtr);                                             \
  data0 = dpSrc[0];                                                    \
  data1 = dpSrc[1];                                                    \
  row0 = vis_faligndata(data0, data1);                                 \
  sPtr += srcYStride;                                                  \
  ALIGN_ADDR(dpSrc, sPtr);                                             \
  data0 = dpSrc[0];                                                    \
  data1 = dpSrc[1];                                                    \
  row1 = vis_faligndata(data0, data1);                                 \
  sPtr += srcYStride;                                                  \
  ALIGN_ADDR(dpSrc, sPtr);                                             \
  data0 = dpSrc[0];                                                    \
  data1 = dpSrc[1];                                                    \
  row2 = vis_faligndata(data0, data1);                                 \
  sPtr += srcYStride;                                                  \
  ALIGN_ADDR(dpSrc, sPtr);                                             \
  data0 = dpSrc[0];                                                    \
  data1 = dpSrc[1];                                                    \
  row3 = vis_faligndata(data0, data1)

/***************************************************************/
#define NEXT_PIXEL_2BC_U8()                                     \
  xSrc = (X>>MLIB_SHIFT)-1;                                     \
  ySrc = (Y>>MLIB_SHIFT)-1;                                     \
  sPtr = (mlib_u8 *)lineAddr[ySrc] + (xSrc<<1)

/***************************************************************/
#define RESULT_2BC_U8_1PIXEL()                                   \
  v00 = vis_fmul8x16au(vis_read_hi(row0), vis_read_hi(yFilter)); \
  dr = vis_fpmerge(vis_read_hi(xFilter), vis_read_lo(xFilter));  \
  v01 = vis_fmul8x16au(vis_read_lo(row0), vis_read_hi(yFilter)); \
  dr = vis_fpmerge(vis_read_hi(dr), vis_read_lo(dr));            \
  v10 = vis_fmul8x16al(vis_read_hi(row1), vis_read_hi(yFilter)); \
  dr1 = vis_fpmerge(vis_read_lo(dr), vis_read_lo(dr));           \
  v11 = vis_fmul8x16al(vis_read_lo(row1), vis_read_hi(yFilter)); \
  dr = vis_fpmerge(vis_read_hi(dr), vis_read_hi(dr));            \
  v20 = vis_fmul8x16au(vis_read_hi(row2), vis_read_lo(yFilter)); \
  xFilter0 = vis_fpmerge(vis_read_hi(dr), vis_read_hi(dr1));     \
  v21 = vis_fmul8x16au(vis_read_lo(row2), vis_read_lo(yFilter)); \
  xFilter1 = vis_fpmerge(vis_read_lo(dr), vis_read_lo(dr1));     \
  v30 = vis_fmul8x16al(vis_read_hi(row3), vis_read_lo(yFilter)); \
  sum0 = vis_fpadd16(v00, v10);                                  \
  v31 = vis_fmul8x16al(vis_read_lo(row3), vis_read_lo(yFilter)); \
  sum1 = vis_fpadd16(v01, v11);                                  \
  sum0 = vis_fpadd16(sum0, v20);                                 \
  sum1 = vis_fpadd16(sum1, v21);                                 \
  sum0 = vis_fpadd16(sum0, v30);                                 \
  sum1 = vis_fpadd16(sum1, v31);                                 \
  v00 = vis_fmul8sux16(sum0, xFilter0);                          \
  v01 = vis_fmul8sux16(sum1, xFilter1);                          \
  v10 = vis_fmul8ulx16(sum0, xFilter0);                          \
  sum0 = vis_fpadd16(v00, v10);                                  \
  v11 = vis_fmul8ulx16(sum1, xFilter1);                          \
  sum1 = vis_fpadd16(v01, v11);                                  \
  d0 = vis_fpadd16(sum0, sum1);                                  \
  v00 = vis_write_lo(v00, vis_fpadd16s(vis_read_hi(d0),          \
                                       vis_read_lo(d0)));        \
  res = vis_write_lo(res, vis_fpack16(v00))

/***************************************************************/
#define BC_U8_2CH(index, mlib_filters_u8)                              \
  v00 = vis_fmul8x16au(vis_read_hi(row0), vis_read_hi(yFilter));       \
  dr = vis_fpmerge(vis_read_hi(xFilter), vis_read_lo(xFilter));        \
  v01 = vis_fmul8x16au(vis_read_lo(row0), vis_read_hi(yFilter));       \
  dr = vis_fpmerge(vis_read_hi(dr), vis_read_lo(dr));                  \
  v10 = vis_fmul8x16al(vis_read_hi(row1), vis_read_hi(yFilter));       \
  dr1 = vis_fpmerge(vis_read_lo(dr), vis_read_lo(dr));                 \
  v11 = vis_fmul8x16al(vis_read_lo(row1), vis_read_hi(yFilter));       \
  dr = vis_fpmerge(vis_read_hi(dr), vis_read_hi(dr));                  \
  v20 = vis_fmul8x16au(vis_read_hi(row2), vis_read_lo(yFilter));       \
  xFilter0 = vis_fpmerge(vis_read_hi(dr), vis_read_hi(dr1));           \
  v21 = vis_fmul8x16au(vis_read_lo(row2), vis_read_lo(yFilter));       \
  xFilter1 = vis_fpmerge(vis_read_lo(dr), vis_read_lo(dr1));           \
  v30 = vis_fmul8x16al(vis_read_hi(row3), vis_read_lo(yFilter));       \
  v31 = vis_fmul8x16al(vis_read_lo(row3), vis_read_lo(yFilter));       \
  ALIGN_ADDR(dpSrc, sPtr);                                             \
  data0 = dpSrc[0];                                                    \
  sum0 = vis_fpadd16(v00, v10);                                        \
  filterposy = (Y >> FILTER_SHIFT);                                    \
  data1 = dpSrc[1];                                                    \
  row0 = vis_faligndata(data0, data1);                                 \
  filterposx = (X >> FILTER_SHIFT);                                    \
  sPtr += srcYStride;                                                  \
  ALIGN_ADDR(dpSrc, sPtr);                                             \
  data0 = dpSrc[0];                                                    \
  sum1 = vis_fpadd16(v01, v11);                                        \
  X += dX;                                                             \
  data1 = dpSrc[1];                                                    \
  sum0 = vis_fpadd16(sum0, v20);                                       \
  row1 = vis_faligndata(data0, data1);                                 \
  sPtr += srcYStride;                                                  \
  ALIGN_ADDR(dpSrc, sPtr);                                             \
  Y += dY;                                                             \
  sum1 = vis_fpadd16(sum1, v21);                                       \
  xSrc = (X>>MLIB_SHIFT)-1;                                            \
  data0 = dpSrc[0];                                                    \
  ySrc = (Y>>MLIB_SHIFT)-1;                                            \
  sum0 = vis_fpadd16(sum0, v30);                                       \
  data1 = dpSrc[1];                                                    \
  filterposy &= FILTER_MASK;                                           \
  sum1 = vis_fpadd16(sum1, v31);                                       \
  v00 = vis_fmul8sux16(sum0, xFilter0);                                \
  row2 = vis_faligndata(data0, data1);                                 \
  v01 = vis_fmul8sux16(sum1, xFilter1);                                \
  sPtr += srcYStride;                                                  \
  v10 = vis_fmul8ulx16(sum0, xFilter0);                                \
  filterposx &= FILTER_MASK;                                           \
  ALIGN_ADDR(dpSrc, sPtr);                                             \
  v11= vis_fmul8ulx16(sum1, xFilter1);                                 \
  data0 = dpSrc[0];                                                    \
  d0##index = vis_fpadd16(v00, v10);                                   \
  data1 = dpSrc[1];                                                    \
  row3 = vis_faligndata(data0, data1);                                 \
  yFilter = *((mlib_d64 *) ((mlib_u8 *)mlib_filters_u8 + filterposy)); \
  d1##index = vis_fpadd16(v01, v11);                                   \
  xFilter = *((mlib_d64 *)((mlib_u8 *)mlib_filters_u8 + filterposx));  \
  sPtr = (mlib_u8 *)lineAddr[ySrc] + (xSrc<<1)

/***************************************************************/
mlib_status mlib_ImageAffine_u8_2ch_bc (mlib_affine_param *param)
{
  DECLAREVAR_BC();
  DTYPE  *dstLineEnd;
  mlib_s32  filterposx, filterposy;
  mlib_d64  data0, data1;
  mlib_d64  sum0, sum1;
  mlib_d64  row0, row1, row2, row3;
  mlib_f32  p0, p1, p2, p3;
  mlib_d64  xFilter;
  mlib_d64  xFilter0, xFilter1, yFilter;
  mlib_d64  v00, v10, v20, v30;
  mlib_d64  v01, v11, v21, v31;
  mlib_d64  d0, d1, d2, d3;
  mlib_d64  d00, d01, d02, d03;
  mlib_d64  d10, d11, d12, d13;
  mlib_d64  e0, e1;
  mlib_d64  *dpSrc;
  mlib_s32  cols, i, mask, off;
  mlib_d64  dr, dr1;
  mlib_d64  res, *dp;
  const mlib_s16 *mlib_filters_table;

  if (filter == MLIB_BICUBIC) {
    mlib_filters_table = mlib_filters_u8_bc;
  } else {
    mlib_filters_table = mlib_filters_u8_bc2;
  }

  for (j = yStart; j <= yFinish; j++) {

    vis_write_gsr(3 << 3);

    CLIP(2);
    dstLineEnd  = (DTYPE*)dstData + 2 * xRight;

    cols = xRight - xLeft + 1;
    dp = vis_alignaddr(dstPixelPtr, 0);
    off = dstPixelPtr - (mlib_u8*)dp;
    dstLineEnd += 1;
    mask = vis_edge8(dstPixelPtr, dstLineEnd);
    i = 0;

    if (i <= cols - 10) {

      NEXT_PIXEL_2BC_U8();
      LOAD_BC_U8_2CH_1PIXEL(mlib_filters_table);

      NEXT_PIXEL_2BC_U8();

      BC_U8_2CH(0, mlib_filters_table);
      BC_U8_2CH(1, mlib_filters_table);
      BC_U8_2CH(2, mlib_filters_table);
      BC_U8_2CH(3, mlib_filters_table);

      FADD_2BC_U8();

      BC_U8_2CH(0, mlib_filters_table);
      BC_U8_2CH(1, mlib_filters_table);
      BC_U8_2CH(2, mlib_filters_table);
      BC_U8_2CH(3, mlib_filters_table);

#pragma pipeloop(0)
      for (; i <= cols-14; i+=4) {
        vis_alignaddr((void *)(8 - (mlib_addr)dstPixelPtr), 0);
        res = vis_faligndata(res, res);
        vis_pst_8(res, dp++, mask);
        vis_pst_8(res, dp, ~mask);
        FADD_2BC_U8();
        BC_U8_2CH(0, mlib_filters_table);
        BC_U8_2CH(1, mlib_filters_table);
        BC_U8_2CH(2, mlib_filters_table);
        BC_U8_2CH(3, mlib_filters_table);
      }

      vis_alignaddr((void *)(8 - (mlib_addr)dstPixelPtr), 0);
      res = vis_faligndata(res, res);
      vis_pst_8(res, dp++, mask);
      vis_pst_8(res, dp, ~mask);

      FADD_2BC_U8();
      vis_alignaddr((void *)(8 - (mlib_addr)dstPixelPtr), 0);
      res = vis_faligndata(res, res);
      vis_pst_8(res, dp++, mask);
      vis_pst_8(res, dp, ~mask);

      dstPixelPtr = (mlib_u8*)dp + off;

      RESULT_2BC_U8_1PIXEL();
      vis_alignaddr((void *)7, 0);
      vis_st_u8(res, dstPixelPtr+1);
      res = vis_faligndata(res, res);
      vis_st_u8(res, dstPixelPtr);
      dstPixelPtr += 2;

      LOAD_BC_U8_2CH_1PIXEL(mlib_filters_table);
      RESULT_2BC_U8_1PIXEL();
      vis_alignaddr((void *)7, 0);
      vis_st_u8(res, dstPixelPtr+1);
      res = vis_faligndata(res, res);
      vis_st_u8(res, dstPixelPtr);
      dstPixelPtr += 2;
      i += 10;
    }

    for (; i < cols; i++) {
      NEXT_PIXEL_2BC_U8();
      LOAD_BC_U8_2CH_1PIXEL(mlib_filters_table);
      RESULT_2BC_U8_1PIXEL();
      vis_alignaddr((void *)7, 0);
      vis_st_u8(res, dstPixelPtr+1);
      res = vis_faligndata(res, res);
      vis_st_u8(res, dstPixelPtr);
      dstPixelPtr += 2;
    }
  }

  return MLIB_SUCCESS;
}

/***************************************************************/
#ifndef MLIB_VIS2

#define FADD_3BC_U8()                                           \
  vis_alignaddr((void*)6, 0);                                   \
  d3 = vis_faligndata(d0, d1);                                  \
  vis_alignaddr((void*)2, 0);                                   \
  d4 = vis_faligndata(d1, d2);                                  \
  d0 = vis_fpadd16(d0, d3);                                     \
  d2 = vis_fpadd16(d2, d4);                                     \
  d1 = vis_faligndata(d2, d2);                                  \
  d0 = vis_fpadd16(d0, d1);                                     \
  f0.f = vis_fpack16(d0)

#else

#define FADD_3BC_U8()                                           \
  vis_alignaddr((void*)4, 0);                                   \
  d3 = vis_bshuffle(d0, d1);                                    \
  d1 = vis_faligndata(d1, d2);                                  \
  d2 = vis_faligndata(d2, d2);                                  \
  d4 = vis_bshuffle(d1, d2);                                    \
  d0 = vis_fpadd16(d0, d3);                                     \
  d1 = vis_fpadd16(d1, d4);                                     \
  d0 = vis_fpadd16(d0, d1);                                     \
  f0.f = vis_fpack16(d0)

#endif /* MLIB_VIS2 */

/***************************************************************/
#define LOAD_BC_U8_3CH_1PIXEL(mlib_filters_u8, mlib_filters_u8_3)      \
  filterposy = (Y >> FILTER_SHIFT) & FILTER_MASK;                      \
  yFilter = *((mlib_d64 *) ((mlib_u8 *)mlib_filters_u8 + filterposy)); \
  filterposx = (X >> FILTER_SHIFT) & FILTER_MASK;                      \
  xPtr=((mlib_d64 *)((mlib_u8 *)mlib_filters_u8_3+3*filterposx));      \
  xFilter0 = xPtr[0];                                                  \
  xFilter1 = xPtr[1];                                                  \
  xFilter2 = xPtr[2];                                                  \
  X += dX;                                                             \
  Y += dY;                                                             \
  ALIGN_ADDR(dpSrc, sPtr);                                             \
  data0 = dpSrc[0];                                                    \
  data1 = dpSrc[1];                                                    \
  data2 = dpSrc[2];                                                    \
  row00 = vis_faligndata(data0, data1);                                \
  row01 = vis_faligndata(data1, data2);                                \
  sPtr += srcYStride;                                                  \
  ALIGN_ADDR(dpSrc, sPtr);                                             \
  data0 = dpSrc[0];                                                    \
  data1 = dpSrc[1];                                                    \
  data2 = dpSrc[2];                                                    \
  row10 = vis_faligndata(data0, data1);                                \
  row11 = vis_faligndata(data1, data2);                                \
  sPtr += srcYStride;                                                  \
  ALIGN_ADDR(dpSrc, sPtr);                                             \
  data0 = dpSrc[0];                                                    \
  data1 = dpSrc[1];                                                    \
  data2 = dpSrc[2];                                                    \
  row20 = vis_faligndata(data0, data1);                                \
  row21 = vis_faligndata(data1, data2);                                \
  sPtr += srcYStride;                                                  \
  ALIGN_ADDR(dpSrc, sPtr);                                             \
  data0 = dpSrc[0];                                                    \
  data1 = dpSrc[1];                                                    \
  data2 = dpSrc[2];                                                    \
  row30 = vis_faligndata(data0, data1);                                \
  row31 = vis_faligndata(data1, data2)

/***************************************************************/
#define STORE_BC_U8_3CH_1PIXEL()                                \
 dstPixelPtr[0] = f0.t[0];                                      \
 dstPixelPtr[1] = f0.t[1];                                      \
 dstPixelPtr[2] = f0.t[2];                                      \
 dstPixelPtr += 3

/***************************************************************/
#define NEXT_PIXEL_3BC_U8()                                     \
  xSrc = (X>>MLIB_SHIFT)-1;                                     \
  ySrc = (Y>>MLIB_SHIFT)-1;                                     \
  sPtr = (mlib_u8 *)lineAddr[ySrc] + (3*xSrc)

/***************************************************************/
#define RESULT_3BC_U8_1PIXEL()                                    \
  v00 = vis_fmul8x16au(vis_read_hi(row00), vis_read_hi(yFilter)); \
  v01 = vis_fmul8x16au(vis_read_lo(row00), vis_read_hi(yFilter)); \
  v02 = vis_fmul8x16au(vis_read_hi(row01), vis_read_hi(yFilter)); \
  v10 = vis_fmul8x16al(vis_read_hi(row10), vis_read_hi(yFilter)); \
  v11 = vis_fmul8x16al(vis_read_lo(row10), vis_read_hi(yFilter)); \
  v12 = vis_fmul8x16al(vis_read_hi(row11), vis_read_hi(yFilter)); \
  v20 = vis_fmul8x16au(vis_read_hi(row20), vis_read_lo(yFilter)); \
  sum0 = vis_fpadd16(v00, v10);                                   \
  v21 = vis_fmul8x16au(vis_read_lo(row20), vis_read_lo(yFilter)); \
  sum1 = vis_fpadd16(v01, v11);                                   \
  v22 = vis_fmul8x16au(vis_read_hi(row21), vis_read_lo(yFilter)); \
  sum2 = vis_fpadd16(v02, v12);                                   \
  v30 = vis_fmul8x16al(vis_read_hi(row30), vis_read_lo(yFilter)); \
  sum0 = vis_fpadd16(sum0, v20);                                  \
  v31 = vis_fmul8x16al(vis_read_lo(row30), vis_read_lo(yFilter)); \
  sum1 = vis_fpadd16(sum1, v21);                                  \
  v32 = vis_fmul8x16al(vis_read_hi(row31), vis_read_lo(yFilter)); \
  sum2 = vis_fpadd16(sum2, v22);                                  \
  sum0 = vis_fpadd16(sum0, v30);                                  \
  sum1 = vis_fpadd16(sum1, v31);                                  \
  v00 = vis_fmul8sux16(sum0, xFilter0);                           \
  sum2 = vis_fpadd16(sum2, v32);                                  \
  v01 = vis_fmul8ulx16(sum0, xFilter0);                           \
  v10 = vis_fmul8sux16(sum1, xFilter1);                           \
  d0 = vis_fpadd16(v00, v01);                                     \
  v11 = vis_fmul8ulx16(sum1, xFilter1);                           \
  v20 = vis_fmul8sux16(sum2, xFilter2);                           \
  d1 = vis_fpadd16(v10, v11);                                     \
  v21 = vis_fmul8ulx16(sum2, xFilter2);                           \
  d2 = vis_fpadd16(v20, v21);                                     \
  FADD_3BC_U8();

/***************************************************************/
#define BC_U8_3CH(mlib_filters_u8, mlib_filters_u8_3)                 \
  v00 = vis_fmul8x16au(vis_read_hi(row00), vis_read_hi(yFilter));     \
  v01 = vis_fmul8x16au(vis_read_lo(row00), vis_read_hi(yFilter));     \
  v02 = vis_fmul8x16au(vis_read_hi(row01), vis_read_hi(yFilter));     \
  ALIGN_ADDR(dpSrc, sPtr);                                            \
  data0 = dpSrc[0];                                                   \
  filterposy = (Y >> FILTER_SHIFT);                                   \
  v10 = vis_fmul8x16al(vis_read_hi(row10), vis_read_hi(yFilter));     \
  data1 = dpSrc[1];                                                   \
  v11 = vis_fmul8x16al(vis_read_lo(row10), vis_read_hi(yFilter));     \
  sum0 = vis_fpadd16(v00, v10);                                       \
  data2 = dpSrc[2];                                                   \
  row00 = vis_faligndata(data0, data1);                               \
  v12 = vis_fmul8x16al(vis_read_hi(row11), vis_read_hi(yFilter));     \
  row01 = vis_faligndata(data1, data2);                               \
  filterposx = (X >> FILTER_SHIFT);                                   \
  sPtr += srcYStride;                                                 \
  ALIGN_ADDR(dpSrc, sPtr);                                            \
  v20 = vis_fmul8x16au(vis_read_hi(row20), vis_read_lo(yFilter));     \
  sum1 = vis_fpadd16(v01, v11);                                       \
  data0 = dpSrc[0];                                                   \
  X += dX;                                                            \
  data1 = dpSrc[1];                                                   \
  v21 = vis_fmul8x16au(vis_read_lo(row20), vis_read_lo(yFilter));     \
  sum2 = vis_fpadd16(v02, v12);                                       \
  data2 = dpSrc[2];                                                   \
  row10 = vis_faligndata(data0, data1);                               \
  v22 = vis_fmul8x16au(vis_read_hi(row21), vis_read_lo(yFilter));     \
  row11 = vis_faligndata(data1, data2);                               \
  sPtr += srcYStride;                                                 \
  ALIGN_ADDR(dpSrc, sPtr);                                            \
  Y += dY;                                                            \
  xSrc = (X>>MLIB_SHIFT)-1;                                           \
  v30 = vis_fmul8x16al(vis_read_hi(row30), vis_read_lo(yFilter));     \
  sum0 = vis_fpadd16(sum0, v20);                                      \
  data0 = dpSrc[0];                                                   \
  ySrc = (Y>>MLIB_SHIFT)-1;                                           \
  data1 = dpSrc[1];                                                   \
  v31 = vis_fmul8x16al(vis_read_lo(row30), vis_read_lo(yFilter));     \
  sum1 = vis_fpadd16(sum1, v21);                                      \
  data2 = dpSrc[2];                                                   \
  filterposy &= FILTER_MASK;                                          \
  row20 = vis_faligndata(data0, data1);                               \
  v32 = vis_fmul8x16al(vis_read_hi(row31), vis_read_lo(yFilter));     \
  row21 = vis_faligndata(data1, data2);                               \
  sPtr += srcYStride;                                                 \
  filterposx &= FILTER_MASK;                                          \
  sum2 = vis_fpadd16(sum2, v22);                                      \
  ALIGN_ADDR(dpSrc, sPtr);                                            \
  sum0 = vis_fpadd16(sum0, v30);                                      \
  data0 = dpSrc[0];                                                   \
  sum1 = vis_fpadd16(sum1, v31);                                      \
  v00 = vis_fmul8sux16(sum0, xFilter0);                               \
  data1 = dpSrc[1];                                                   \
  sum2 = vis_fpadd16(sum2, v32);                                      \
  v01 = vis_fmul8ulx16(sum0, xFilter0);                               \
  data2 = dpSrc[2];                                                   \
  row30 = vis_faligndata(data0, data1);                               \
  v10 = vis_fmul8sux16(sum1, xFilter1);                               \
  d0 = vis_fpadd16(v00, v01);                                         \
  row31 = vis_faligndata(data1, data2);                               \
  yFilter = *((mlib_d64 *)((mlib_u8 *)mlib_filters_u8 + filterposy)); \
  v11 = vis_fmul8ulx16(sum1, xFilter1);                               \
  xPtr=((mlib_d64 *)((mlib_u8 *)mlib_filters_u8_3+3*filterposx));     \
  xFilter0 = xPtr[0];                                                 \
  v20 = vis_fmul8sux16(sum2, xFilter2);                               \
  d1 = vis_fpadd16(v10, v11);                                         \
  xFilter1 = xPtr[1];                                                 \
  v21 = vis_fmul8ulx16(sum2, xFilter2);                               \
  xFilter2 = xPtr[2];                                                 \
  sPtr = (mlib_u8 *)lineAddr[ySrc] + (3*xSrc);                        \
  d2 = vis_fpadd16(v20, v21)

/***************************************************************/
mlib_status mlib_ImageAffine_u8_3ch_bc (mlib_affine_param *param)
{
  DECLAREVAR_BC();
  mlib_s32  filterposx, filterposy;
  mlib_d64  data0, data1, data2;
  mlib_d64  sum0, sum1, sum2;
  mlib_d64  row00, row10, row20, row30;
  mlib_d64  row01, row11, row21, row31;
  mlib_d64  xFilter0, xFilter1, xFilter2, yFilter;
  mlib_d64  v00, v10, v20, v30;
  mlib_d64  v01, v11, v21, v31;
  mlib_d64  v02, v12, v22, v32;
  mlib_d64  d0, d1, d2, d3, d4;
  mlib_d64  *dpSrc;
  mlib_s32  cols, i;
  mlib_d64  *xPtr;
  union {
    mlib_u8 t[4];
    mlib_f32 f;
  } f0;
  const mlib_s16 *mlib_filters_table  ;
  const mlib_s16 *mlib_filters_table_3;

  if (filter == MLIB_BICUBIC) {
    mlib_filters_table   = mlib_filters_u8_bc;
    mlib_filters_table_3 = mlib_filters_u8_bc_3;
  } else {
    mlib_filters_table   = mlib_filters_u8_bc2;
    mlib_filters_table_3 = mlib_filters_u8_bc2_3;
  }

  vis_write_gsr(3 << 3);
  MLIB_WRITE_BMASK(0x6789ABCD);

  for (j = yStart; j <= yFinish; j ++) {

    CLIP(3);

    cols = xRight - xLeft + 1;
    i = 0;

    if (i <= cols - 4) {

      NEXT_PIXEL_3BC_U8();
      LOAD_BC_U8_3CH_1PIXEL(mlib_filters_table, mlib_filters_table_3);

      NEXT_PIXEL_3BC_U8();

      BC_U8_3CH(mlib_filters_table, mlib_filters_table_3);
      FADD_3BC_U8();

      BC_U8_3CH(mlib_filters_table, mlib_filters_table_3);

#pragma pipeloop(0)
      for (; i < cols-4; i++) {
        STORE_BC_U8_3CH_1PIXEL();

        FADD_3BC_U8();
        BC_U8_3CH(mlib_filters_table, mlib_filters_table_3);
      }

      STORE_BC_U8_3CH_1PIXEL();

      FADD_3BC_U8();
      STORE_BC_U8_3CH_1PIXEL();

      RESULT_3BC_U8_1PIXEL();
      STORE_BC_U8_3CH_1PIXEL();

      LOAD_BC_U8_3CH_1PIXEL(mlib_filters_table, mlib_filters_table_3);
      RESULT_3BC_U8_1PIXEL();
      STORE_BC_U8_3CH_1PIXEL();
      i += 4;
    }

    for (; i < cols; i++) {
      NEXT_PIXEL_3BC_U8();
      LOAD_BC_U8_3CH_1PIXEL(mlib_filters_table, mlib_filters_table_3);
      RESULT_3BC_U8_1PIXEL();
      STORE_BC_U8_3CH_1PIXEL();
    }
  }

  return MLIB_SUCCESS;
}

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
  xPtr=((mlib_d64 *)((mlib_u8 *)mlib_filters_u8_4+4*filterposx));      \
  xFilter0 = xPtr[0];                                                  \
  xFilter1 = xPtr[1];                                                  \
  xFilter2 = xPtr[2];                                                  \
  xFilter3 = xPtr[3];                                                  \
  X += dX;                                                             \
  Y += dY;                                                             \
  ALIGN_ADDR(dpSrc, sPtr);                                             \
  data0 = dpSrc[0];                                                    \
  data1 = dpSrc[1];                                                    \
  data2 = dpSrc[2];                                                    \
  row00 = vis_faligndata(data0, data1);                                \
  row01 = vis_faligndata(data1, data2);                                \
  sPtr += srcYStride;                                                  \
  ALIGN_ADDR(dpSrc, sPtr);                                             \
  data0 = dpSrc[0];                                                    \
  data1 = dpSrc[1];                                                    \
  data2 = dpSrc[2];                                                    \
  row10 = vis_faligndata(data0, data1);                                \
  row11 = vis_faligndata(data1, data2);                                \
  sPtr += srcYStride;                                                  \
  ALIGN_ADDR(dpSrc, sPtr);                                             \
  data0 = dpSrc[0];                                                    \
  data1 = dpSrc[1];                                                    \
  data2 = dpSrc[2];                                                    \
  row20 = vis_faligndata(data0, data1);                                \
  row21 = vis_faligndata(data1, data2);                                \
  sPtr += srcYStride;                                                  \
  ALIGN_ADDR(dpSrc, sPtr);                                             \
  data0 = dpSrc[0];                                                    \
  data1 = dpSrc[1];                                                    \
  data2 = dpSrc[2];                                                    \
  row30 = vis_faligndata(data0, data1);                                \
  row31 = vis_faligndata(data1, data2)

/***************************************************************/
#define NEXT_PIXEL_4BC_U8()                                     \
  xSrc = (X>>MLIB_SHIFT)-1;                                     \
  ySrc = (Y>>MLIB_SHIFT)-1;                                     \
  sPtr = (mlib_u8 *)lineAddr[ySrc] + (4*xSrc)

/***************************************************************/
#define RESULT_4BC_U8_1PIXEL(ind)                                 \
  v00 = vis_fmul8x16au(vis_read_hi(row00), vis_read_hi(yFilter)); \
  v01 = vis_fmul8x16au(vis_read_lo(row00), vis_read_hi(yFilter)); \
  v02 = vis_fmul8x16au(vis_read_hi(row01), vis_read_hi(yFilter)); \
  v03 = vis_fmul8x16au(vis_read_lo(row01), vis_read_hi(yFilter)); \
  v10 = vis_fmul8x16al(vis_read_hi(row10), vis_read_hi(yFilter)); \
  v11 = vis_fmul8x16al(vis_read_lo(row10), vis_read_hi(yFilter)); \
  sum0 = vis_fpadd16(v00, v10);                                   \
  v12 = vis_fmul8x16al(vis_read_hi(row11), vis_read_hi(yFilter)); \
  sum1 = vis_fpadd16(v01, v11);                                   \
  v13 = vis_fmul8x16al(vis_read_lo(row11), vis_read_hi(yFilter)); \
  sum2 = vis_fpadd16(v02, v12);                                   \
  v20 = vis_fmul8x16au(vis_read_hi(row20), vis_read_lo(yFilter)); \
  sum3 = vis_fpadd16(v03, v13);                                   \
  v21 = vis_fmul8x16au(vis_read_lo(row20), vis_read_lo(yFilter)); \
  sum0 = vis_fpadd16(sum0, v20);                                  \
  v22 = vis_fmul8x16au(vis_read_hi(row21), vis_read_lo(yFilter)); \
  sum1 = vis_fpadd16(sum1, v21);                                  \
  v23 = vis_fmul8x16au(vis_read_lo(row21), vis_read_lo(yFilter)); \
  sum2 = vis_fpadd16(sum2, v22);                                  \
  v30 = vis_fmul8x16al(vis_read_hi(row30), vis_read_lo(yFilter)); \
  sum3 = vis_fpadd16(sum3, v23);                                  \
  v31 = vis_fmul8x16al(vis_read_lo(row30), vis_read_lo(yFilter)); \
  sum0 = vis_fpadd16(sum0, v30);                                  \
  v32 = vis_fmul8x16al(vis_read_hi(row31), vis_read_lo(yFilter)); \
  sum1 = vis_fpadd16(sum1, v31);                                  \
  v33 = vis_fmul8x16al(vis_read_lo(row31), vis_read_lo(yFilter)); \
  sum2 = vis_fpadd16(sum2, v32);                                  \
  v00 = vis_fmul8sux16(sum0, xFilter0);                           \
  sum3 = vis_fpadd16(sum3, v33);                                  \
  v01 = vis_fmul8ulx16(sum0, xFilter0);                           \
  v10 = vis_fmul8sux16(sum1, xFilter1);                           \
  d0##ind = vis_fpadd16(v00, v01);                                \
  v11 = vis_fmul8ulx16(sum1, xFilter1);                           \
  v20 = vis_fmul8sux16(sum2, xFilter2);                           \
  d1##ind = vis_fpadd16(v10, v11);                                \
  v21 = vis_fmul8ulx16(sum2, xFilter2);                           \
  v30 = vis_fmul8sux16(sum3, xFilter3);                           \
  d2##ind = vis_fpadd16(v20, v21);                                \
  v31 = vis_fmul8ulx16(sum3, xFilter3);                           \
  d3##ind = vis_fpadd16(v30, v31)

/***************************************************************/
#define BC_U8_4CH(ind, mlib_filters_u8, mlib_filters_u8_4)            \
  v00 = vis_fmul8x16au(vis_read_hi(row00), vis_read_hi(yFilter));     \
  v01 = vis_fmul8x16au(vis_read_lo(row00), vis_read_hi(yFilter));     \
  v02 = vis_fmul8x16au(vis_read_hi(row01), vis_read_hi(yFilter));     \
  v03 = vis_fmul8x16au(vis_read_lo(row01), vis_read_hi(yFilter));     \
  ALIGN_ADDR(dpSrc, sPtr);                                            \
  data0 = dpSrc[0];                                                   \
  filterposy = (Y >> FILTER_SHIFT);                                   \
  v10 = vis_fmul8x16al(vis_read_hi(row10), vis_read_hi(yFilter));     \
  data1 = dpSrc[1];                                                   \
  v11 = vis_fmul8x16al(vis_read_lo(row10), vis_read_hi(yFilter));     \
  sum0 = vis_fpadd16(v00, v10);                                       \
  data2 = dpSrc[2];                                                   \
  row00 = vis_faligndata(data0, data1);                               \
  v12 = vis_fmul8x16al(vis_read_hi(row11), vis_read_hi(yFilter));     \
  row01 = vis_faligndata(data1, data2);                               \
  filterposx = (X >> FILTER_SHIFT);                                   \
  v13 = vis_fmul8x16al(vis_read_lo(row11), vis_read_hi(yFilter));     \
  sPtr += srcYStride;                                                 \
  ALIGN_ADDR(dpSrc, sPtr);                                            \
  v20 = vis_fmul8x16au(vis_read_hi(row20), vis_read_lo(yFilter));     \
  sum1 = vis_fpadd16(v01, v11);                                       \
  data0 = dpSrc[0];                                                   \
  X += dX;                                                            \
  data1 = dpSrc[1];                                                   \
  v21 = vis_fmul8x16au(vis_read_lo(row20), vis_read_lo(yFilter));     \
  sum2 = vis_fpadd16(v02, v12);                                       \
  data2 = dpSrc[2];                                                   \
  row10 = vis_faligndata(data0, data1);                               \
  v22 = vis_fmul8x16au(vis_read_hi(row21), vis_read_lo(yFilter));     \
  row11 = vis_faligndata(data1, data2);                               \
  sPtr += srcYStride;                                                 \
  ALIGN_ADDR(dpSrc, sPtr);                                            \
  v23 = vis_fmul8x16au(vis_read_lo(row21), vis_read_lo(yFilter));     \
  sum3 = vis_fpadd16(v03, v13);                                       \
  Y += dY;                                                            \
  xSrc = (X>>MLIB_SHIFT)-1;                                           \
  v30 = vis_fmul8x16al(vis_read_hi(row30), vis_read_lo(yFilter));     \
  sum0 = vis_fpadd16(sum0, v20);                                      \
  data0 = dpSrc[0];                                                   \
  ySrc = (Y>>MLIB_SHIFT)-1;                                           \
  data1 = dpSrc[1];                                                   \
  v31 = vis_fmul8x16al(vis_read_lo(row30), vis_read_lo(yFilter));     \
  sum1 = vis_fpadd16(sum1, v21);                                      \
  data2 = dpSrc[2];                                                   \
  filterposy &= FILTER_MASK;                                          \
  row20 = vis_faligndata(data0, data1);                               \
  v32 = vis_fmul8x16al(vis_read_hi(row31), vis_read_lo(yFilter));     \
  row21 = vis_faligndata(data1, data2);                               \
  sPtr += srcYStride;                                                 \
  filterposx &= FILTER_MASK;                                          \
  v33 = vis_fmul8x16al(vis_read_lo(row31), vis_read_lo(yFilter));     \
  sum2 = vis_fpadd16(sum2, v22);                                      \
  ALIGN_ADDR(dpSrc, sPtr);                                            \
  sum3 = vis_fpadd16(sum3, v23);                                      \
  sum0 = vis_fpadd16(sum0, v30);                                      \
  data0 = dpSrc[0];                                                   \
  sum1 = vis_fpadd16(sum1, v31);                                      \
  v00 = vis_fmul8sux16(sum0, xFilter0);                               \
  data1 = dpSrc[1];                                                   \
  sum2 = vis_fpadd16(sum2, v32);                                      \
  v01 = vis_fmul8ulx16(sum0, xFilter0);                               \
  sum3 = vis_fpadd16(sum3, v33);                                      \
  data2 = dpSrc[2];                                                   \
  row30 = vis_faligndata(data0, data1);                               \
  v10 = vis_fmul8sux16(sum1, xFilter1);                               \
  d0##ind = vis_fpadd16(v00, v01);                                    \
  row31 = vis_faligndata(data1, data2);                               \
  yFilter = *((mlib_d64 *)((mlib_u8 *)mlib_filters_u8 + filterposy)); \
  v11 = vis_fmul8ulx16(sum1, xFilter1);                               \
  xPtr=((mlib_d64 *)((mlib_u8 *)mlib_filters_u8_4+4*filterposx));     \
  xFilter0 = xPtr[0];                                                 \
  v20 = vis_fmul8sux16(sum2, xFilter2);                               \
  d1##ind = vis_fpadd16(v10, v11);                                    \
  xFilter1 = xPtr[1];                                                 \
  v21 = vis_fmul8ulx16(sum2, xFilter2);                               \
  xFilter2 = xPtr[2];                                                 \
  v30 = vis_fmul8sux16(sum3, xFilter3);                               \
  d2##ind = vis_fpadd16(v20, v21);                                    \
  v31 = vis_fmul8ulx16(sum3, xFilter3);                               \
  xFilter3 = xPtr[3];                                                 \
  sPtr = (mlib_u8 *)lineAddr[ySrc] + (4*xSrc);                        \
  d3##ind = vis_fpadd16(v30, v31)

/***************************************************************/
mlib_status mlib_ImageAffine_u8_4ch_bc (mlib_affine_param *param)
{
  DECLAREVAR_BC();
  DTYPE  *dstLineEnd;
  mlib_s32  filterposx, filterposy;
  mlib_d64  data0, data1, data2;
  mlib_d64  sum0, sum1, sum2, sum3;
  mlib_d64  row00, row10, row20, row30;
  mlib_d64  row01, row11, row21, row31;
  mlib_d64  xFilter0, xFilter1, xFilter2, xFilter3, yFilter;
  mlib_d64  v00, v10, v20, v30;
  mlib_d64  v01, v11, v21, v31;
  mlib_d64  v02, v12, v22, v32;
  mlib_d64  v03, v13, v23, v33;
  mlib_d64  d0, d1, d2, d3;
  mlib_d64  d00, d10, d20, d30;
  mlib_d64  d01, d11, d21, d31;
  mlib_d64  *dpSrc;
  mlib_s32  cols, i;
  mlib_d64  res, *dp, *xPtr;
  mlib_s32  mask, emask, gsrd;
  const mlib_s16 *mlib_filters_table  ;
  const mlib_s16 *mlib_filters_table_4;

  if (filter == MLIB_BICUBIC) {
    mlib_filters_table   = mlib_filters_u8_bc;
    mlib_filters_table_4 = mlib_filters_u8_bc_4;
  } else {
    mlib_filters_table   = mlib_filters_u8_bc2;
    mlib_filters_table_4 = mlib_filters_u8_bc2_4;
  }

  for (j = yStart; j <= yFinish; j++) {

    vis_write_gsr(3 << 3);

    CLIP(4);
    dstLineEnd  = (DTYPE*)dstData + 4 * xRight;
    dstLineEnd += 3;
    dp = (mlib_d64*)vis_alignaddr(dstPixelPtr, 0);
    mask = vis_edge8(dstPixelPtr, dstLineEnd);
    gsrd = ((8 - (mlib_addr)dstPixelPtr) & 7);

    cols = xRight - xLeft + 1;
    i = 0;

    if (i <= cols - 6) {

      NEXT_PIXEL_4BC_U8();
      LOAD_BC_U8_4CH_1PIXEL(mlib_filters_table, mlib_filters_table_4);

      NEXT_PIXEL_4BC_U8();

      BC_U8_4CH(0, mlib_filters_table, mlib_filters_table_4);
      BC_U8_4CH(1, mlib_filters_table, mlib_filters_table_4);
      FADD_4BC_U8();

      BC_U8_4CH(0, mlib_filters_table, mlib_filters_table_4);
      BC_U8_4CH(1, mlib_filters_table, mlib_filters_table_4);

#pragma pipeloop(0)
      for (; i <= cols-8; i+=2) {
        vis_alignaddr((void *)gsrd, 0);
        res = vis_faligndata(res, res);

        vis_pst_8(res, dp++, mask);
        vis_pst_8(res, dp, ~mask);

        FADD_4BC_U8();
        BC_U8_4CH(0, mlib_filters_table, mlib_filters_table_4);
        BC_U8_4CH(1, mlib_filters_table, mlib_filters_table_4);
      }

      vis_alignaddr((void *)gsrd, 0);
      res = vis_faligndata(res, res);

      vis_pst_8(res, dp++, mask);
      vis_pst_8(res, dp, ~mask);

      FADD_4BC_U8();
      vis_alignaddr((void *)gsrd, 0);
      res = vis_faligndata(res, res);

      vis_pst_8(res, dp++, mask);
      vis_pst_8(res, dp, ~mask);

      RESULT_4BC_U8_1PIXEL(0);
      LOAD_BC_U8_4CH_1PIXEL(mlib_filters_table, mlib_filters_table_4);
      RESULT_4BC_U8_1PIXEL(1);
      FADD_4BC_U8();

      vis_alignaddr((void *)gsrd, 0);
      res = vis_faligndata(res, res);

      vis_pst_8(res, dp++, mask);
      vis_pst_8(res, dp, ~mask);
      i += 6;
    }

    if (i <= cols-4) {
      NEXT_PIXEL_4BC_U8();
      LOAD_BC_U8_4CH_1PIXEL(mlib_filters_table, mlib_filters_table_4);

      NEXT_PIXEL_4BC_U8();

      BC_U8_4CH(0, mlib_filters_table, mlib_filters_table_4);
      BC_U8_4CH(1, mlib_filters_table, mlib_filters_table_4);
      FADD_4BC_U8();
      vis_alignaddr((void *)gsrd, 0);
      res = vis_faligndata(res, res);

      vis_pst_8(res, dp++, mask);
      vis_pst_8(res, dp, ~mask);

      RESULT_4BC_U8_1PIXEL(0);
      LOAD_BC_U8_4CH_1PIXEL(mlib_filters_table, mlib_filters_table_4);
      RESULT_4BC_U8_1PIXEL(1);
      FADD_4BC_U8();

      vis_alignaddr((void *)gsrd, 0);
      res = vis_faligndata(res, res);

      vis_pst_8(res, dp++, mask);
      vis_pst_8(res, dp, ~mask);
      i += 4;
    }

    if (i <= cols-2) {
      NEXT_PIXEL_4BC_U8();
      LOAD_BC_U8_4CH_1PIXEL(mlib_filters_table, mlib_filters_table_4);
      RESULT_4BC_U8_1PIXEL(0);

      NEXT_PIXEL_4BC_U8();
      LOAD_BC_U8_4CH_1PIXEL(mlib_filters_table, mlib_filters_table_4);
      RESULT_4BC_U8_1PIXEL(1);
      FADD_4BC_U8();

      vis_alignaddr((void *)gsrd, 0);
      res = vis_faligndata(res, res);

      vis_pst_8(res, dp++, mask);
      vis_pst_8(res, dp, ~mask);
      i += 2;
    }

    if (i < cols) {
      NEXT_PIXEL_4BC_U8();
      LOAD_BC_U8_4CH_1PIXEL(mlib_filters_table, mlib_filters_table_4);
      RESULT_4BC_U8_1PIXEL(0);

      d0 = vis_fpadd16(d00, d10);
      d1 = vis_fpadd16(d20, d30);
      d0 = vis_fpadd16(d0, d1);
      res = vis_fpack16_pair(d0, d0);
      vis_alignaddr((void *)gsrd, 0);
      res = vis_faligndata(res, res);

      emask = vis_edge8(dp, dstLineEnd);
      vis_pst_8(res, dp++, emask & mask);

      if ((mlib_u8*)dp <= (mlib_u8*)dstLineEnd) {
        mask = vis_edge8(dp, dstLineEnd);
        vis_pst_8(res, dp, mask);
      }
    }
  }

  return MLIB_SUCCESS;
}

/***************************************************************/
