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



/*
 *      The functions step along the lines from xLeft to xRight and apply
 *      the bicubic filtering.
 *
 */

#include "vis_proto.h"
#include "mlib_ImageAffine.h"
#include "mlib_v_ImageFilters.h"

/***************************************************************/
#define DTYPE  mlib_s16

#define FILTER_BITS  9

/***************************************************************/
#define sPtr srcPixelPtr

/***************************************************************/
#define NEXT_PIXEL_1BC_S16()                                    \
  xSrc = (X >> MLIB_SHIFT)-1;                                   \
  ySrc = (Y >> MLIB_SHIFT)-1;                                   \
  sPtr = (mlib_s16 *)lineAddr[ySrc] + xSrc

/***************************************************************/
#define LOAD_BC_S16_1CH_1PIXEL(mlib_filters_s16, mlib_filters_s16_4)    \
  vis_alignaddr(sPtr, 0);                                               \
  dpSrc = (mlib_d64*)(((mlib_addr)sPtr) & (~7));                        \
  data0 = dpSrc[0];                                                     \
  data1 = dpSrc[1];                                                     \
  row0 = vis_faligndata(data0, data1);                                  \
  sPtr += srcYStride;                                                   \
  vis_alignaddr(sPtr, 0);                                               \
  dpSrc = (mlib_d64*)(((mlib_addr)sPtr) & (~7));                        \
  data0 = dpSrc[0];                                                     \
  data1 = dpSrc[1];                                                     \
  row1 = vis_faligndata(data0, data1);                                  \
  sPtr += srcYStride;                                                   \
  vis_alignaddr(sPtr, 0);                                               \
  dpSrc = (mlib_d64*)(((mlib_addr)sPtr) & (~7));                        \
  data0 = dpSrc[0];                                                     \
  data1 = dpSrc[1];                                                     \
  row2 = vis_faligndata(data0, data1);                                  \
  sPtr += srcYStride;                                                   \
  vis_alignaddr(sPtr, 0);                                               \
  dpSrc = (mlib_d64*)(((mlib_addr)sPtr) & (~7));                        \
  data0 = dpSrc[0];                                                     \
  data1 = dpSrc[1];                                                     \
  row3 = vis_faligndata(data0, data1);                                  \
  filterposy = (Y >> FILTER_SHIFT) & FILTER_MASK;                       \
  yPtr = ((mlib_d64 *) ((mlib_u8 *)mlib_filters_s16_4 + filterposy*4)); \
  yFilter0 = yPtr[0];                                                   \
  yFilter1 = yPtr[1];                                                   \
  yFilter2 = yPtr[2];                                                   \
  yFilter3 = yPtr[3];                                                   \
  filterposx = (X >> FILTER_SHIFT) & FILTER_MASK;                       \
  xFilter = *((mlib_d64 *)((mlib_u8 *)mlib_filters_s16 + filterposx));  \
  X += dX;                                                              \
  Y += dY

/***************************************************************/
#define RESULT_1BC_S16_1PIXEL()                                          \
  u0 = vis_fmul8sux16(row0, yFilter0);                                   \
  u1 = vis_fmul8ulx16(row0, yFilter0);                                   \
  u2 = vis_fmul8sux16(row1, yFilter1);                                   \
  v0 = vis_fpadd16(u0, u1);                                              \
  u3 = vis_fmul8ulx16(row1, yFilter1);                                   \
  u0 = vis_fmul8sux16(row2, yFilter2);                                   \
  v1 = vis_fpadd16(u2, u3);                                              \
  u1 = vis_fmul8ulx16(row2, yFilter2);                                   \
  sum = vis_fpadd16(v0, v1);                                             \
  u2 = vis_fmul8sux16(row3, yFilter3);                                   \
  v2 = vis_fpadd16(u0, u1);                                              \
  u3 = vis_fmul8ulx16(row3, yFilter3);                                   \
  sum = vis_fpadd16(sum, v2);                                            \
  v3 = vis_fpadd16(u2, u3);                                              \
  sum = vis_fpadd16(sum, v3);                                            \
  d00 = vis_fmul8sux16(sum, xFilter);                                    \
  d10 = vis_fmul8ulx16(sum, xFilter);                                    \
  d0 = vis_fpadd16(d00, d10);                                            \
  p0 = vis_fpadd16s(vis_read_hi(d0), vis_read_lo(d0));                   \
  d0 = vis_fmuld8sux16(f_x01000100, p0);                                 \
  d1 = vis_write_lo(d1, vis_fpadd32s(vis_read_hi(d0), vis_read_lo(d0))); \
  res = vis_fpackfix_pair(d1, d1)

/***************************************************************/
#define BC_S16_1CH(ind, mlib_filters_s16, mlib_filters_s16_4)           \
  u0 = vis_fmul8sux16(row0, yFilter0);                                  \
  u1 = vis_fmul8ulx16(row0, yFilter0);                                  \
  vis_alignaddr(sPtr, 0);                                               \
  dpSrc = (mlib_d64*)(((mlib_addr)sPtr) & (~7));                        \
  u2 = vis_fmul8sux16(row1, yFilter1);                                  \
  v0 = vis_fpadd16(u0, u1);                                             \
  data0 = dpSrc[0];                                                     \
  filterposy = (Y >> FILTER_SHIFT);                                     \
  u3 = vis_fmul8ulx16(row1, yFilter1);                                  \
  data1 = dpSrc[1];                                                     \
  row0 = vis_faligndata(data0, data1);                                  \
  filterposx = (X >> FILTER_SHIFT);                                     \
  sPtr += srcYStride;                                                   \
  vis_alignaddr(sPtr, 0);                                               \
  dpSrc = (mlib_d64*)(((mlib_addr)sPtr) & (~7));                        \
  u0 = vis_fmul8sux16(row2, yFilter2);                                  \
  v1 = vis_fpadd16(u2, u3);                                             \
  data0 = dpSrc[0];                                                     \
  u1 = vis_fmul8ulx16(row2, yFilter2);                                  \
  sum = vis_fpadd16(v0, v1);                                            \
  X += dX;                                                              \
  data1 = dpSrc[1];                                                     \
  row1 = vis_faligndata(data0, data1);                                  \
  sPtr += srcYStride;                                                   \
  vis_alignaddr(sPtr, 0);                                               \
  dpSrc = (mlib_d64*)(((mlib_addr)sPtr) & (~7));                        \
  u2 = vis_fmul8sux16(row3, yFilter3);                                  \
  v2 = vis_fpadd16(u0, u1);                                             \
  Y += dY;                                                              \
  xSrc = (X >> MLIB_SHIFT)-1;                                           \
  data0 = dpSrc[0];                                                     \
  u3 = vis_fmul8ulx16(row3, yFilter3);                                  \
  sum = vis_fpadd16(sum, v2);                                           \
  ySrc = (Y >> MLIB_SHIFT)-1;                                           \
  data1 = dpSrc[1];                                                     \
  filterposy &= FILTER_MASK;                                            \
  row2 = vis_faligndata(data0, data1);                                  \
  sPtr += srcYStride;                                                   \
  filterposx &= FILTER_MASK;                                            \
  vis_alignaddr(sPtr, 0);                                               \
  dpSrc = (mlib_d64*)(((mlib_addr)sPtr) & (~7));                        \
  data0 = dpSrc[0];                                                     \
  v3 = vis_fpadd16(u2, u3);                                             \
  data1 = dpSrc[1];                                                     \
  row3 = vis_faligndata(data0, data1);                                  \
  yPtr = ((mlib_d64 *) ((mlib_u8 *)mlib_filters_s16_4 + filterposy*4)); \
  yFilter0 = yPtr[0];                                                   \
  sum = vis_fpadd16(sum, v3);                                           \
  yFilter1 = yPtr[1];                                                   \
  d0 = vis_fmul8sux16(sum, xFilter);                                    \
  yFilter2 = yPtr[2];                                                   \
  d1 = vis_fmul8ulx16(sum, xFilter);                                    \
  yFilter3 = yPtr[3];                                                   \
  xFilter = *((mlib_d64 *)((mlib_u8 *)mlib_filters_s16 + filterposx));  \
  d0##ind = vis_fpadd16(d0, d1);                                        \
  sPtr = (mlib_s16 *)lineAddr[ySrc] + xSrc

/***************************************************************/
#define FADD_1BC_S16()                                                \
  p0 = vis_fpadd16s(vis_read_hi(d00), vis_read_lo(d00));              \
  p1 = vis_fpadd16s(vis_read_hi(d01), vis_read_lo(d01));              \
  p2 = vis_fpadd16s(vis_read_hi(d02), vis_read_lo(d02));              \
  p3 = vis_fpadd16s(vis_read_hi(d03), vis_read_lo(d03));              \
  d0 = vis_fmuld8sux16(f_x01000100, p0);                              \
  d1 = vis_fmuld8sux16(f_x01000100, p1);                              \
  d2 = vis_fmuld8sux16(f_x01000100, p2);                              \
  d3 = vis_fmuld8sux16(f_x01000100, p3);                              \
  d0 = vis_freg_pair(vis_fpadd32s(vis_read_hi(d0), vis_read_lo(d0)),  \
                     vis_fpadd32s(vis_read_hi(d1), vis_read_lo(d1))); \
  d1 = vis_freg_pair(vis_fpadd32s(vis_read_hi(d2), vis_read_lo(d2)),  \
                     vis_fpadd32s(vis_read_hi(d3), vis_read_lo(d3))); \
  res = vis_fpackfix_pair(d0, d1)

/***************************************************************/
mlib_status mlib_ImageAffine_s16_1ch_bc (mlib_affine_param *param)
{
  DECLAREVAR_BC();
  mlib_s32  filterposx, filterposy;
  mlib_d64  data0, data1;
  mlib_d64  sum;
  mlib_d64  row0, row1, row2, row3;
  mlib_f32  p0, p1, p2, p3;
  mlib_d64  xFilter, yFilter0, yFilter1, yFilter2, yFilter3;
  mlib_d64  v0, v1, v2, v3;
  mlib_d64  u0, u1, u2, u3;
  mlib_d64  d0, d1, d2, d3;
  mlib_d64  d00, d10, d01, d02, d03;
  mlib_d64 *yPtr;
  mlib_d64 *dpSrc;
  mlib_s32  align, cols, i;
  mlib_d64  res;
  mlib_f32  f_x01000100 = vis_to_float(0x01000100);
  const mlib_s16 *mlib_filters_table  ;
  const mlib_s16 *mlib_filters_table_4;

  if (filter == MLIB_BICUBIC) {
    mlib_filters_table   = mlib_filters_s16_bc;
    mlib_filters_table_4 = mlib_filters_s16_bc_4;
  } else {
    mlib_filters_table   = mlib_filters_s16_bc2;
    mlib_filters_table_4 = mlib_filters_s16_bc2_4;
  }

  srcYStride >>= 1;

  for (j = yStart; j <= yFinish; j++) {

    vis_write_gsr(10 << 3);

    CLIP(1);

    cols = xRight - xLeft + 1;
    align = (8 - ((mlib_addr)dstPixelPtr) & 7) & 7;
    align >>= 1;
    align = (cols < align)? cols : align;

    for (i = 0; i < align; i++) {
      NEXT_PIXEL_1BC_S16();
      LOAD_BC_S16_1CH_1PIXEL(mlib_filters_table, mlib_filters_table_4);
      RESULT_1BC_S16_1PIXEL();
      vis_st_u16(res, dstPixelPtr++);
    }

    if (i <= cols - 10) {

      NEXT_PIXEL_1BC_S16();
      LOAD_BC_S16_1CH_1PIXEL(mlib_filters_table, mlib_filters_table_4);

      NEXT_PIXEL_1BC_S16();

      BC_S16_1CH(0, mlib_filters_table, mlib_filters_table_4);
      BC_S16_1CH(1, mlib_filters_table, mlib_filters_table_4);
      BC_S16_1CH(2, mlib_filters_table, mlib_filters_table_4);
      BC_S16_1CH(3, mlib_filters_table, mlib_filters_table_4);

      FADD_1BC_S16();

      BC_S16_1CH(0, mlib_filters_table, mlib_filters_table_4);
      BC_S16_1CH(1, mlib_filters_table, mlib_filters_table_4);
      BC_S16_1CH(2, mlib_filters_table, mlib_filters_table_4);
      BC_S16_1CH(3, mlib_filters_table, mlib_filters_table_4);

#pragma pipeloop(0)
      for (; i <= cols - 14; i += 4) {
        *(mlib_d64*)dstPixelPtr = res;
        FADD_1BC_S16();
        BC_S16_1CH(0, mlib_filters_table, mlib_filters_table_4);
        BC_S16_1CH(1, mlib_filters_table, mlib_filters_table_4);
        BC_S16_1CH(2, mlib_filters_table, mlib_filters_table_4);
        BC_S16_1CH(3, mlib_filters_table, mlib_filters_table_4);
        dstPixelPtr += 4;
      }

      *(mlib_d64*)dstPixelPtr = res;
      dstPixelPtr += 4;
      FADD_1BC_S16();
      *(mlib_d64*)dstPixelPtr = res;
      dstPixelPtr += 4;

      RESULT_1BC_S16_1PIXEL();
      vis_st_u16(res, dstPixelPtr++);

      LOAD_BC_S16_1CH_1PIXEL(mlib_filters_table, mlib_filters_table_4);
      RESULT_1BC_S16_1PIXEL();
      vis_st_u16(res, dstPixelPtr++);
      i += 10;
    }

    for (; i < cols; i++) {
      NEXT_PIXEL_1BC_S16();
      LOAD_BC_S16_1CH_1PIXEL(mlib_filters_table, mlib_filters_table_4);
      RESULT_1BC_S16_1PIXEL();
      vis_st_u16(res, dstPixelPtr++);
    }
  }

  return MLIB_SUCCESS;
}

/***************************************************************/
#define NEXT_PIXEL_2BC_S16()                                    \
  xSrc = (X >> MLIB_SHIFT)-1;                                   \
  ySrc = (Y >> MLIB_SHIFT)-1;                                   \
  sPtr = (mlib_s16 *)lineAddr[ySrc] + (xSrc << 1)

/***************************************************************/
#define LOAD_BC_S16_2CH_1PIXEL(mlib_filters_s16, mlib_filters_s16_4)    \
  vis_alignaddr(sPtr, 0);                                               \
  dpSrc = (mlib_d64*)(((mlib_addr)sPtr) & (~7));                        \
  data0 = dpSrc[0];                                                     \
  data1 = dpSrc[1];                                                     \
  data2 = dpSrc[2];                                                     \
  row00 = vis_faligndata(data0, data1);                                 \
  row01 = vis_faligndata(data1, data2);                                 \
  sPtr += srcYStride;                                                   \
  vis_alignaddr(sPtr, 0);                                               \
  dpSrc = (mlib_d64*)(((mlib_addr)sPtr) & (~7));                        \
  data0 = dpSrc[0];                                                     \
  data1 = dpSrc[1];                                                     \
  data2 = dpSrc[2];                                                     \
  row10 = vis_faligndata(data0, data1);                                 \
  row11 = vis_faligndata(data1, data2);                                 \
  sPtr += srcYStride;                                                   \
  vis_alignaddr(sPtr, 0);                                               \
  dpSrc = (mlib_d64*)(((mlib_addr)sPtr) & (~7));                        \
  data0 = dpSrc[0];                                                     \
  data1 = dpSrc[1];                                                     \
  data2 = dpSrc[2];                                                     \
  row20 = vis_faligndata(data0, data1);                                 \
  row21 = vis_faligndata(data1, data2);                                 \
  sPtr += srcYStride;                                                   \
  vis_alignaddr(sPtr, 0);                                               \
  dpSrc = (mlib_d64*)(((mlib_addr)sPtr) & (~7));                        \
  data0 = dpSrc[0];                                                     \
  data1 = dpSrc[1];                                                     \
  data2 = dpSrc[2];                                                     \
  row30 = vis_faligndata(data0, data1);                                 \
  row31 = vis_faligndata(data1, data2);                                 \
  filterposy = (Y >> FILTER_SHIFT) & FILTER_MASK;                       \
  yPtr = ((mlib_d64 *) ((mlib_u8 *)mlib_filters_s16_4 + filterposy*4)); \
  yFilter0 = yPtr[0];                                                   \
  yFilter1 = yPtr[1];                                                   \
  yFilter2 = yPtr[2];                                                   \
  yFilter3 = yPtr[3];                                                   \
  filterposx = (X >> FILTER_SHIFT) & FILTER_MASK;                       \
  xFilter = *((mlib_d64 *)((mlib_u8 *)mlib_filters_s16 + filterposx));  \
  X += dX;                                                              \
  Y += dY

/***************************************************************/
#define RESULT_2BC_S16_1PIXEL()                                 \
  u00 = vis_fmul8sux16(row00, yFilter0);                        \
  dr = vis_fpmerge(vis_read_hi(xFilter), vis_read_lo(xFilter)); \
  u01 = vis_fmul8ulx16(row00, yFilter0);                        \
  dr = vis_fpmerge(vis_read_hi(dr), vis_read_lo(dr));           \
  u10 = vis_fmul8sux16(row01, yFilter0);                        \
  dr1 = vis_fpmerge(vis_read_lo(dr), vis_read_lo(dr));          \
  u11 = vis_fmul8ulx16(row01, yFilter0);                        \
  dr = vis_fpmerge(vis_read_hi(dr), vis_read_hi(dr));           \
  u20 = vis_fmul8sux16(row10, yFilter1);                        \
  v00 = vis_fpadd16(u00, u01);                                  \
  u21 = vis_fmul8ulx16(row10, yFilter1);                        \
  v01 = vis_fpadd16(u10, u11);                                  \
  u00 = vis_fmul8sux16(row11, yFilter1);                        \
  xFilter0 = vis_fpmerge(vis_read_hi(dr), vis_read_hi(dr1));    \
  u01 = vis_fmul8ulx16(row11, yFilter1);                        \
  u10 = vis_fmul8sux16(row20, yFilter2);                        \
  u11 = vis_fmul8ulx16(row20, yFilter2);                        \
  v10 = vis_fpadd16(u20, u21);                                  \
  sum0 = vis_fpadd16(v00, v10);                                 \
  u20 = vis_fmul8sux16(row21, yFilter2);                        \
  v11 = vis_fpadd16(u00, u01);                                  \
  u21 = vis_fmul8ulx16(row21, yFilter2);                        \
  xFilter1 = vis_fpmerge(vis_read_lo(dr), vis_read_lo(dr1));    \
  u00 = vis_fmul8sux16(row30, yFilter3);                        \
  v20 = vis_fpadd16(u10, u11);                                  \
  sum1 = vis_fpadd16(v01, v11);                                 \
  u01 = vis_fmul8ulx16(row30, yFilter3);                        \
  sum0 = vis_fpadd16(sum0, v20);                                \
  v21 = vis_fpadd16(u20, u21);                                  \
  u10 = vis_fmul8sux16(row31, yFilter3);                        \
  v30 = vis_fpadd16(u00, u01);                                  \
  sum1 = vis_fpadd16(sum1, v21);                                \
  u11 = vis_fmul8ulx16(row31, yFilter3);                        \
  sum0 = vis_fpadd16(sum0, v30);                                \
  v31 = vis_fpadd16(u10, u11);                                  \
  sum1 = vis_fpadd16(sum1, v31);                                \
  d00 = vis_fmul8sux16(sum0, xFilter0);                         \
  d10 = vis_fmul8ulx16(sum0, xFilter0);                         \
  d20 = vis_fmul8sux16(sum1, xFilter1);                         \
  d30 = vis_fmul8ulx16(sum1, xFilter1);                         \
  d0 = vis_fpadd16(d00, d10);                                   \
  d1 = vis_fpadd16(d20, d30);                                   \
  d0 = vis_fpadd16(d0, d1);                                     \
  p0 = vis_fpadd16s(vis_read_hi(d0), vis_read_lo(d0));          \
  d0 = vis_fmuld8sux16(f_x01000100, p0);                        \
  res = vis_fpackfix_pair(d0, d0)

/***************************************************************/
#define BC_S16_2CH(ind, mlib_filters_s16, mlib_filters_s16_4)           \
  u00 = vis_fmul8sux16(row00, yFilter0);                                \
  dr = vis_fpmerge(vis_read_hi(xFilter), vis_read_lo(xFilter));         \
  u01 = vis_fmul8ulx16(row00, yFilter0);                                \
  dr = vis_fpmerge(vis_read_hi(dr), vis_read_lo(dr));                   \
  u10 = vis_fmul8sux16(row01, yFilter0);                                \
  dr1 = vis_fpmerge(vis_read_lo(dr), vis_read_lo(dr));                  \
  u11 = vis_fmul8ulx16(row01, yFilter0);                                \
  dr = vis_fpmerge(vis_read_hi(dr), vis_read_hi(dr));                   \
  vis_alignaddr(sPtr, 0);                                               \
  dpSrc = (mlib_d64*)(((mlib_addr)sPtr) & (~7));                        \
  u20 = vis_fmul8sux16(row10, yFilter1);                                \
  v00 = vis_fpadd16(u00, u01);                                          \
  u21 = vis_fmul8ulx16(row10, yFilter1);                                \
  data0 = dpSrc[0];                                                     \
  filterposy = (Y >> FILTER_SHIFT);                                     \
  v01 = vis_fpadd16(u10, u11);                                          \
  data1 = dpSrc[1];                                                     \
  u00 = vis_fmul8sux16(row11, yFilter1);                                \
  xFilter0 = vis_fpmerge(vis_read_hi(dr), vis_read_hi(dr1));            \
  data2 = dpSrc[2];                                                     \
  u01 = vis_fmul8ulx16(row11, yFilter1);                                \
  row00 = vis_faligndata(data0, data1);                                 \
  u10 = vis_fmul8sux16(row20, yFilter2);                                \
  row01 = vis_faligndata(data1, data2);                                 \
  filterposx = (X >> FILTER_SHIFT);                                     \
  sPtr += srcYStride;                                                   \
  vis_alignaddr(sPtr, 0);                                               \
  dpSrc = (mlib_d64*)(((mlib_addr)sPtr) & (~7));                        \
  u11 = vis_fmul8ulx16(row20, yFilter2);                                \
  v10 = vis_fpadd16(u20, u21);                                          \
  data0 = dpSrc[0];                                                     \
  sum0 = vis_fpadd16(v00, v10);                                         \
  X += dX;                                                              \
  data1 = dpSrc[1];                                                     \
  u20 = vis_fmul8sux16(row21, yFilter2);                                \
  v11 = vis_fpadd16(u00, u01);                                          \
  data2 = dpSrc[2];                                                     \
  row10 = vis_faligndata(data0, data1);                                 \
  u21 = vis_fmul8ulx16(row21, yFilter2);                                \
  row11 = vis_faligndata(data1, data2);                                 \
  sPtr += srcYStride;                                                   \
  xFilter1 = vis_fpmerge(vis_read_lo(dr), vis_read_lo(dr1));            \
  vis_alignaddr(sPtr, 0);                                               \
  dpSrc = (mlib_d64*)(((mlib_addr)sPtr) & (~7));                        \
  u00 = vis_fmul8sux16(row30, yFilter3);                                \
  v20 = vis_fpadd16(u10, u11);                                          \
  Y += dY;                                                              \
  xSrc = (X >> MLIB_SHIFT)-1;                                           \
  sum1 = vis_fpadd16(v01, v11);                                         \
  data0 = dpSrc[0];                                                     \
  u01 = vis_fmul8ulx16(row30, yFilter3);                                \
  sum0 = vis_fpadd16(sum0, v20);                                        \
  ySrc = (Y >> MLIB_SHIFT)-1;                                           \
  data1 = dpSrc[1];                                                     \
  v21 = vis_fpadd16(u20, u21);                                          \
  u10 = vis_fmul8sux16(row31, yFilter3);                                \
  data2 = dpSrc[2];                                                     \
  v30 = vis_fpadd16(u00, u01);                                          \
  filterposy &= FILTER_MASK;                                            \
  row20 = vis_faligndata(data0, data1);                                 \
  sum1 = vis_fpadd16(sum1, v21);                                        \
  u11 = vis_fmul8ulx16(row31, yFilter3);                                \
  row21 = vis_faligndata(data1, data2);                                 \
  sPtr += srcYStride;                                                   \
  filterposx &= FILTER_MASK;                                            \
  v31 = vis_fpadd16(u10, u11);                                          \
  vis_alignaddr(sPtr, 0);                                               \
  dpSrc = (mlib_d64*)(((mlib_addr)sPtr) & (~7));                        \
  data0 = dpSrc[0];                                                     \
  sum0 = vis_fpadd16(sum0, v30);                                        \
  data1 = dpSrc[1];                                                     \
  sum1 = vis_fpadd16(sum1, v31);                                        \
  data2 = dpSrc[2];                                                     \
  row30 = vis_faligndata(data0, data1);                                 \
  d0 = vis_fmul8sux16(sum0, xFilter0);                                  \
  row31 = vis_faligndata(data1, data2);                                 \
  yPtr = ((mlib_d64 *) ((mlib_u8 *)mlib_filters_s16_4 + filterposy*4)); \
  d1 = vis_fmul8ulx16(sum0, xFilter0);                                  \
  yFilter0 = yPtr[0];                                                   \
  d2 = vis_fmul8sux16(sum1, xFilter1);                                  \
  yFilter1 = yPtr[1];                                                   \
  d3 = vis_fmul8ulx16(sum1, xFilter1);                                  \
  d0##ind = vis_fpadd16(d0, d1);                                        \
  yFilter2 = yPtr[2];                                                   \
  yFilter3 = yPtr[3];                                                   \
  d1##ind = vis_fpadd16(d2, d3);                                        \
  xFilter = *((mlib_d64 *)((mlib_u8 *)mlib_filters_s16 + filterposx));  \
  sPtr = (mlib_s16 *)lineAddr[ySrc] + (xSrc << 1)

/***************************************************************/
#define FADD_2BC_S16()                                          \
  d0 = vis_fpadd16(d00, d10);                                   \
  d2 = vis_fpadd16(d01, d11);                                   \
  p0 = vis_fpadd16s(vis_read_hi(d0), vis_read_lo(d0));          \
  p1 = vis_fpadd16s(vis_read_hi(d2), vis_read_lo(d2));          \
  d0 = vis_fmuld8sux16(f_x01000100, p0);                        \
  d1 = vis_fmuld8sux16(f_x01000100, p1);                        \
  res = vis_fpackfix_pair(d0, d1)

/***************************************************************/
mlib_status mlib_ImageAffine_s16_2ch_bc (mlib_affine_param *param)
{
  DECLAREVAR_BC();
  DTYPE  *dstLineEnd;
  mlib_s32  filterposx, filterposy;
  mlib_d64  data0, data1, data2;
  mlib_d64  sum0, sum1;
  mlib_d64  row00, row10, row20, row30;
  mlib_d64  row01, row11, row21, row31;
  mlib_f32  p0, p1;
  mlib_d64  xFilter, xFilter0, xFilter1;
  mlib_d64  yFilter0, yFilter1, yFilter2, yFilter3;
  mlib_d64  v00, v01, v10, v11, v20, v21, v30, v31;
  mlib_d64  u00, u01, u10, u11, u20, u21;
  mlib_d64  d0, d1, d2, d3;
  mlib_d64  d00, d10, d20, d30, d01, d11;
  mlib_d64  *yPtr;
  mlib_d64  *dp, *dpSrc;
  mlib_s32  cols, i, mask, emask;
  mlib_d64  res, res1;
  mlib_d64  dr, dr1;
  mlib_f32 f_x01000100 = vis_to_float(0x01000100);
  const mlib_s16 *mlib_filters_table  ;
  const mlib_s16 *mlib_filters_table_4;

  if (filter == MLIB_BICUBIC) {
    mlib_filters_table   = mlib_filters_s16_bc;
    mlib_filters_table_4 = mlib_filters_s16_bc_4;
  } else {
    mlib_filters_table   = mlib_filters_s16_bc2;
    mlib_filters_table_4 = mlib_filters_s16_bc2_4;
  }

  srcYStride >>= 1;

  for (j = yStart; j <= yFinish; j++) {

    vis_write_gsr(10 << 3);

    CLIP(2);
    dstLineEnd  = (DTYPE*)dstData + 2 * xRight;

    cols = xRight - xLeft + 1;
    dp = vis_alignaddr(dstPixelPtr, 0);
    dstLineEnd += 1;
    mask = vis_edge16(dstPixelPtr, dstLineEnd);
    i = 0;

    if (i <= cols - 6) {

      NEXT_PIXEL_2BC_S16();
      LOAD_BC_S16_2CH_1PIXEL(mlib_filters_table, mlib_filters_table_4);

      NEXT_PIXEL_2BC_S16();

      BC_S16_2CH(0, mlib_filters_table, mlib_filters_table_4);
      BC_S16_2CH(1, mlib_filters_table, mlib_filters_table_4);

      FADD_2BC_S16();

      BC_S16_2CH(0, mlib_filters_table, mlib_filters_table_4);
      BC_S16_2CH(1, mlib_filters_table, mlib_filters_table_4);

#pragma pipeloop(0)
      for (; i <= cols-8; i += 2) {
        vis_alignaddr((void *)(8 - (mlib_addr)dstPixelPtr), 0);
        res = vis_faligndata(res, res);
        vis_pst_16(res, dp++, mask);
        vis_pst_16(res, dp, ~mask);
        FADD_2BC_S16();
        BC_S16_2CH(0, mlib_filters_table, mlib_filters_table_4);
        BC_S16_2CH(1, mlib_filters_table, mlib_filters_table_4);
      }

      vis_alignaddr((void *)(8 - (mlib_addr)dstPixelPtr), 0);
      res = vis_faligndata(res, res);
      vis_pst_16(res, dp++, mask);
      vis_pst_16(res, dp, ~mask);

      FADD_2BC_S16();
      vis_alignaddr((void *)(8 - (mlib_addr)dstPixelPtr), 0);
      res = vis_faligndata(res, res);
      vis_pst_16(res, dp++, mask);
      vis_pst_16(res, dp, ~mask);

      RESULT_2BC_S16_1PIXEL();
      res1 = res;

      LOAD_BC_S16_2CH_1PIXEL(mlib_filters_table, mlib_filters_table_4);
      RESULT_2BC_S16_1PIXEL();
      res = vis_write_hi(res, vis_read_hi(res1));
      vis_alignaddr((void *)(8 - (mlib_addr)dstPixelPtr), 0);
      res = vis_faligndata(res, res);
      vis_pst_16(res, dp++, mask);
      vis_pst_16(res, dp, ~mask);

      i += 6;
    }

    if (i <= cols - 4) {
      NEXT_PIXEL_2BC_S16();
      LOAD_BC_S16_2CH_1PIXEL(mlib_filters_table, mlib_filters_table_4);

      NEXT_PIXEL_2BC_S16();

      BC_S16_2CH(0, mlib_filters_table, mlib_filters_table_4);
      BC_S16_2CH(1, mlib_filters_table, mlib_filters_table_4);

      FADD_2BC_S16();
      vis_alignaddr((void *)(8 - (mlib_addr)dstPixelPtr), 0);
      res = vis_faligndata(res, res);
      vis_pst_16(res, dp++, mask);
      vis_pst_16(res, dp, ~mask);

      RESULT_2BC_S16_1PIXEL();
      res1 = res;

      LOAD_BC_S16_2CH_1PIXEL(mlib_filters_table, mlib_filters_table_4);
      RESULT_2BC_S16_1PIXEL();
      res = vis_write_hi(res, vis_read_hi(res1));
      vis_alignaddr((void *)(8 - (mlib_addr)dstPixelPtr), 0);
      res = vis_faligndata(res, res);
      vis_pst_16(res, dp++, mask);
      vis_pst_16(res, dp, ~mask);

      i += 4;
    }

    if (i <= cols - 2) {
      NEXT_PIXEL_2BC_S16();
      LOAD_BC_S16_2CH_1PIXEL(mlib_filters_table, mlib_filters_table_4);
      RESULT_2BC_S16_1PIXEL();
      res1 = res;

      NEXT_PIXEL_2BC_S16();
      LOAD_BC_S16_2CH_1PIXEL(mlib_filters_table, mlib_filters_table_4);
      RESULT_2BC_S16_1PIXEL();
      res = vis_write_hi(res, vis_read_hi(res1));
      vis_alignaddr((void *)(8 - (mlib_addr)dstPixelPtr), 0);
      res = vis_faligndata(res, res);
      vis_pst_16(res, dp++, mask);
      vis_pst_16(res, dp, ~mask);

      i += 2;
    }

    if (i < cols) {
      NEXT_PIXEL_2BC_S16();
      LOAD_BC_S16_2CH_1PIXEL(mlib_filters_table, mlib_filters_table_4);
      RESULT_2BC_S16_1PIXEL();
      vis_alignaddr((void *)(8 - (mlib_addr)dstPixelPtr), 0);
      res = vis_faligndata(res, res);
      emask = vis_edge16(dp, dstLineEnd);
      vis_pst_16(res, dp++, mask & emask);

      if ((mlib_s16*)dp <= dstLineEnd) {
        mask = vis_edge16(dp, dstLineEnd);
        vis_pst_16(res, dp, mask);
      }
    }
  }

  return MLIB_SUCCESS;
}

/***************************************************************/
#define NEXT_PIXEL_3BC_S16()                                    \
  xSrc = (X >> MLIB_SHIFT)-1;                                   \
  ySrc = (Y >> MLIB_SHIFT)-1;                                   \
  sPtr = (mlib_s16 *)lineAddr[ySrc] + (xSrc*3)

/***************************************************************/
#define LOAD_BC_S16_3CH_1PIXEL(mlib_filters_s16_3, mlib_filters_s16_4)  \
  dpSrc = vis_alignaddr(sPtr, 0);                                       \
  data0 = dpSrc[0];                                                     \
  data1 = dpSrc[1];                                                     \
  data2 = dpSrc[2];                                                     \
  data3 = dpSrc[3];                                                     \
  row00 = vis_faligndata(data0, data1);                                 \
  row01 = vis_faligndata(data1, data2);                                 \
  row02 = vis_faligndata(data2, data3);                                 \
  sPtr += srcYStride;                                                   \
  dpSrc = vis_alignaddr(sPtr, 0);                                       \
  data0 = dpSrc[0];                                                     \
  data1 = dpSrc[1];                                                     \
  data2 = dpSrc[2];                                                     \
  data3 = dpSrc[3];                                                     \
  row10 = vis_faligndata(data0, data1);                                 \
  row11 = vis_faligndata(data1, data2);                                 \
  row12 = vis_faligndata(data2, data3);                                 \
  sPtr += srcYStride;                                                   \
  dpSrc = vis_alignaddr(sPtr, 0);                                       \
  data0 = dpSrc[0];                                                     \
  data1 = dpSrc[1];                                                     \
  data2 = dpSrc[2];                                                     \
  data3 = dpSrc[3];                                                     \
  row20 = vis_faligndata(data0, data1);                                 \
  row21 = vis_faligndata(data1, data2);                                 \
  row22 = vis_faligndata(data2, data3);                                 \
  sPtr += srcYStride;                                                   \
  dpSrc = vis_alignaddr(sPtr, 0);                                       \
  data0 = dpSrc[0];                                                     \
  data1 = dpSrc[1];                                                     \
  data2 = dpSrc[2];                                                     \
  data3 = dpSrc[3];                                                     \
  row30 = vis_faligndata(data0, data1);                                 \
  row31 = vis_faligndata(data1, data2);                                 \
  row32 = vis_faligndata(data2, data3);                                 \
  filterposy = (Y >> FILTER_SHIFT) & FILTER_MASK;                       \
  yPtr = ((mlib_d64 *) ((mlib_u8 *)mlib_filters_s16_4 + filterposy*4)); \
  yFilter0 = yPtr[0];                                                   \
  yFilter1 = yPtr[1];                                                   \
  yFilter2 = yPtr[2];                                                   \
  yFilter3 = yPtr[3];                                                   \
  filterposx = (X >> FILTER_SHIFT) & FILTER_MASK;                       \
  xPtr = ((mlib_d64 *)((mlib_u8 *)mlib_filters_s16_3 + filterposx*3));  \
  xFilter0 = xPtr[0];                                                   \
  xFilter1 = xPtr[1];                                                   \
  xFilter2 = xPtr[2];                                                   \
  X += dX;                                                              \
  Y += dY

/***************************************************************/
#define STORE_BC_S16_3CH_1PIXEL()                               \
  dstPixelPtr[0] = f0.t[0];                                     \
  dstPixelPtr[1] = f0.t[1];                                     \
  dstPixelPtr[2] = f0.t[2];                                     \
  dstPixelPtr += 3

/***************************************************************/
#define RESULT_3BC_S16_1PIXEL()                                 \
  u00 = vis_fmul8sux16(row00, yFilter0);                        \
  u01 = vis_fmul8ulx16(row00, yFilter0);                        \
  u10 = vis_fmul8sux16(row01, yFilter0);                        \
  u11 = vis_fmul8ulx16(row01, yFilter0);                        \
  v00 = vis_fpadd16(u00, u01);                                  \
  u20 = vis_fmul8sux16(row02, yFilter0);                        \
  v01 = vis_fpadd16(u10, u11);                                  \
  u21 = vis_fmul8ulx16(row02, yFilter0);                        \
  u00 = vis_fmul8sux16(row10, yFilter1);                        \
  u01 = vis_fmul8ulx16(row10, yFilter1);                        \
  v02 = vis_fpadd16(u20, u21);                                  \
  u10 = vis_fmul8sux16(row11, yFilter1);                        \
  u11 = vis_fmul8ulx16(row11, yFilter1);                        \
  v10 = vis_fpadd16(u00, u01);                                  \
  u20 = vis_fmul8sux16(row12, yFilter1);                        \
  u21 = vis_fmul8ulx16(row12, yFilter1);                        \
  u00 = vis_fmul8sux16(row20, yFilter2);                        \
  v11 = vis_fpadd16(u10, u11);                                  \
  u01 = vis_fmul8ulx16(row20, yFilter2);                        \
  v12 = vis_fpadd16(u20, u21);                                  \
  u10 = vis_fmul8sux16(row21, yFilter2);                        \
  u11 = vis_fmul8ulx16(row21, yFilter2);                        \
  v20 = vis_fpadd16(u00, u01);                                  \
  u20 = vis_fmul8sux16(row22, yFilter2);                        \
  sum0 = vis_fpadd16(v00, v10);                                 \
  u21 = vis_fmul8ulx16(row22, yFilter2);                        \
  u00 = vis_fmul8sux16(row30, yFilter3);                        \
  u01 = vis_fmul8ulx16(row30, yFilter3);                        \
  v21 = vis_fpadd16(u10, u11);                                  \
  sum1 = vis_fpadd16(v01, v11);                                 \
  u10 = vis_fmul8sux16(row31, yFilter3);                        \
  sum2 = vis_fpadd16(v02, v12);                                 \
  v22 = vis_fpadd16(u20, u21);                                  \
  u11 = vis_fmul8ulx16(row31, yFilter3);                        \
  sum0 = vis_fpadd16(sum0, v20);                                \
  u20 = vis_fmul8sux16(row32, yFilter3);                        \
  v30 = vis_fpadd16(u00, u01);                                  \
  sum1 = vis_fpadd16(sum1, v21);                                \
  u21 = vis_fmul8ulx16(row32, yFilter3);                        \
  v31 = vis_fpadd16(u10, u11);                                  \
  sum2 = vis_fpadd16(sum2, v22);                                \
  v32 = vis_fpadd16(u20, u21);                                  \
  sum0 = vis_fpadd16(sum0, v30);                                \
  row30 = vis_faligndata(data0, data1);                         \
  v00 = vis_fmul8sux16(sum0, xFilter0);                         \
  sum1 = vis_fpadd16(sum1, v31);                                \
  sum2 = vis_fpadd16(sum2, v32);                                \
  v01 = vis_fmul8ulx16(sum0, xFilter0);                         \
  v10 = vis_fmul8sux16(sum1, xFilter1);                         \
  v11 = vis_fmul8ulx16(sum1, xFilter1);                         \
  d0 = vis_fpadd16(v00, v01);                                   \
  v20 = vis_fmul8sux16(sum2, xFilter2);                         \
  v21 = vis_fmul8ulx16(sum2, xFilter2);                         \
  d1 = vis_fpadd16(v10, v11);                                   \
  d2 = vis_fpadd16(v20, v21);                                   \
  vis_alignaddr((void*)6, 0);                                   \
  d3 = vis_faligndata(d0, d1);                                  \
  vis_alignaddr((void*)2, 0);                                   \
  d4 = vis_faligndata(d1, d2);                                  \
  d0 = vis_fpadd16(d0, d3);                                     \
  d2 = vis_fpadd16(d2, d4);                                     \
  d1 = vis_faligndata(d2, d2);                                  \
  d0 = vis_fpadd16(d0, d1);                                     \
  d2 = vis_fmuld8sux16(f_x01000100, vis_read_hi(d0));           \
  d3 = vis_fmuld8sux16(f_x01000100, vis_read_lo(d0));           \
  f0.d = vis_fpackfix_pair(d2, d3)

/***************************************************************/
#define BC_S16_3CH(mlib_filters_s16_3, mlib_filters_s16_4)              \
  u00 = vis_fmul8sux16(row00, yFilter0);                                \
  u01 = vis_fmul8ulx16(row00, yFilter0);                                \
  u10 = vis_fmul8sux16(row01, yFilter0);                                \
  u11 = vis_fmul8ulx16(row01, yFilter0);                                \
  v00 = vis_fpadd16(u00, u01);                                          \
  u20 = vis_fmul8sux16(row02, yFilter0);                                \
  v01 = vis_fpadd16(u10, u11);                                          \
  u21 = vis_fmul8ulx16(row02, yFilter0);                                \
  dpSrc = vis_alignaddr(sPtr, 0);                                       \
  u00 = vis_fmul8sux16(row10, yFilter1);                                \
  u01 = vis_fmul8ulx16(row10, yFilter1);                                \
  data0 = dpSrc[0];                                                     \
  filterposy = (Y >> FILTER_SHIFT);                                     \
  v02 = vis_fpadd16(u20, u21);                                          \
  data1 = dpSrc[1];                                                     \
  u10 = vis_fmul8sux16(row11, yFilter1);                                \
  data2 = dpSrc[2];                                                     \
  u11 = vis_fmul8ulx16(row11, yFilter1);                                \
  v10 = vis_fpadd16(u00, u01);                                          \
  data3 = dpSrc[3];                                                     \
  u20 = vis_fmul8sux16(row12, yFilter1);                                \
  row00 = vis_faligndata(data0, data1);                                 \
  u21 = vis_fmul8ulx16(row12, yFilter1);                                \
  row01 = vis_faligndata(data1, data2);                                 \
  u00 = vis_fmul8sux16(row20, yFilter2);                                \
  row02 = vis_faligndata(data2, data3);                                 \
  filterposx = (X >> FILTER_SHIFT);                                     \
  sPtr += srcYStride;                                                   \
  dpSrc = vis_alignaddr(sPtr, 0);                                       \
  v11 = vis_fpadd16(u10, u11);                                          \
  u01 = vis_fmul8ulx16(row20, yFilter2);                                \
  v12 = vis_fpadd16(u20, u21);                                          \
  data0 = dpSrc[0];                                                     \
  u10 = vis_fmul8sux16(row21, yFilter2);                                \
  X += dX;                                                              \
  data1 = dpSrc[1];                                                     \
  u11 = vis_fmul8ulx16(row21, yFilter2);                                \
  v20 = vis_fpadd16(u00, u01);                                          \
  data2 = dpSrc[2];                                                     \
  u20 = vis_fmul8sux16(row22, yFilter2);                                \
  sum0 = vis_fpadd16(v00, v10);                                         \
  data3 = dpSrc[3];                                                     \
  row10 = vis_faligndata(data0, data1);                                 \
  u21 = vis_fmul8ulx16(row22, yFilter2);                                \
  row11 = vis_faligndata(data1, data2);                                 \
  u00 = vis_fmul8sux16(row30, yFilter3);                                \
  row12 = vis_faligndata(data2, data3);                                 \
  sPtr += srcYStride;                                                   \
  dpSrc = vis_alignaddr(sPtr, 0);                                       \
  u01 = vis_fmul8ulx16(row30, yFilter3);                                \
  v21 = vis_fpadd16(u10, u11);                                          \
  Y += dY;                                                              \
  xSrc = (X >> MLIB_SHIFT)-1;                                           \
  sum1 = vis_fpadd16(v01, v11);                                         \
  data0 = dpSrc[0];                                                     \
  u10 = vis_fmul8sux16(row31, yFilter3);                                \
  sum2 = vis_fpadd16(v02, v12);                                         \
  ySrc = (Y >> MLIB_SHIFT)-1;                                           \
  data1 = dpSrc[1];                                                     \
  v22 = vis_fpadd16(u20, u21);                                          \
  u11 = vis_fmul8ulx16(row31, yFilter3);                                \
  data2 = dpSrc[2];                                                     \
  sum0 = vis_fpadd16(sum0, v20);                                        \
  u20 = vis_fmul8sux16(row32, yFilter3);                                \
  data3 = dpSrc[3];                                                     \
  v30 = vis_fpadd16(u00, u01);                                          \
  filterposy &= FILTER_MASK;                                            \
  row20 = vis_faligndata(data0, data1);                                 \
  sum1 = vis_fpadd16(sum1, v21);                                        \
  u21 = vis_fmul8ulx16(row32, yFilter3);                                \
  row21 = vis_faligndata(data1, data2);                                 \
  row22 = vis_faligndata(data2, data3);                                 \
  sPtr += srcYStride;                                                   \
  filterposx &= FILTER_MASK;                                            \
  v31 = vis_fpadd16(u10, u11);                                          \
  dpSrc = vis_alignaddr(sPtr, 0);                                       \
  data0 = dpSrc[0];                                                     \
  sum2 = vis_fpadd16(sum2, v22);                                        \
  data1 = dpSrc[1];                                                     \
  v32 = vis_fpadd16(u20, u21);                                          \
  data2 = dpSrc[2];                                                     \
  sum0 = vis_fpadd16(sum0, v30);                                        \
  data3 = dpSrc[3];                                                     \
  row30 = vis_faligndata(data0, data1);                                 \
  v00 = vis_fmul8sux16(sum0, xFilter0);                                 \
  row31 = vis_faligndata(data1, data2);                                 \
  row32 = vis_faligndata(data2, data3);                                 \
  yPtr = ((mlib_d64 *) ((mlib_u8 *)mlib_filters_s16_4 + filterposy*4)); \
  sum1 = vis_fpadd16(sum1, v31);                                        \
  yFilter0 = yPtr[0];                                                   \
  sum2 = vis_fpadd16(sum2, v32);                                        \
  v01 = vis_fmul8ulx16(sum0, xFilter0);                                 \
  yFilter1 = yPtr[1];                                                   \
  v10 = vis_fmul8sux16(sum1, xFilter1);                                 \
  yFilter2 = yPtr[2];                                                   \
  v11 = vis_fmul8ulx16(sum1, xFilter1);                                 \
  d0 = vis_fpadd16(v00, v01);                                           \
  yFilter3 = yPtr[3];                                                   \
  xPtr = ((mlib_d64 *)((mlib_u8 *)mlib_filters_s16_3 + filterposx*3));  \
  v20 = vis_fmul8sux16(sum2, xFilter2);                                 \
  xFilter0 = xPtr[0];                                                   \
  v21 = vis_fmul8ulx16(sum2, xFilter2);                                 \
  d1 = vis_fpadd16(v10, v11);                                           \
  xFilter1 = xPtr[1];                                                   \
  d2 = vis_fpadd16(v20, v21);                                           \
  xFilter2 = xPtr[2];                                                   \
  sPtr = (mlib_s16 *)lineAddr[ySrc] + (xSrc*3)

/***************************************************************/
#define FADD_3BC_S16()                                          \
  vis_alignaddr((void*)6, 0);                                   \
  d3 = vis_faligndata(d0, d1);                                  \
  vis_alignaddr((void*)2, 0);                                   \
  d4 = vis_faligndata(d1, d2);                                  \
  d0 = vis_fpadd16(d0, d3);                                     \
  d2 = vis_fpadd16(d2, d4);                                     \
  d1 = vis_faligndata(d2, d2);                                  \
  d0 = vis_fpadd16(d0, d1);                                     \
  d2 = vis_fmuld8sux16(f_x01000100, vis_read_hi(d0));           \
  d3 = vis_fmuld8sux16(f_x01000100, vis_read_lo(d0));           \
  f0.d = vis_fpackfix_pair(d2, d3)

/***************************************************************/
mlib_status mlib_ImageAffine_s16_3ch_bc (mlib_affine_param *param)
{
  DECLAREVAR_BC();
  mlib_s32  filterposx, filterposy;
  mlib_d64  data0, data1, data2, data3;
  mlib_d64  sum0, sum1, sum2;
  mlib_d64  row00, row10, row20, row30;
  mlib_d64  row01, row11, row21, row31;
  mlib_d64  row02, row12, row22, row32;
  mlib_d64  xFilter0, xFilter1, xFilter2;
  mlib_d64  yFilter0, yFilter1, yFilter2, yFilter3;
  mlib_d64  v00, v01, v02, v10, v11, v12, v20, v21, v22, v30, v31, v32;
  mlib_d64  u00, u01, u10, u11, u20, u21;
  mlib_d64  d0, d1, d2, d3, d4;
  mlib_d64 *yPtr, *xPtr;
  mlib_d64 *dpSrc;
  mlib_s32  cols, i;
  mlib_f32  f_x01000100 = vis_to_float(0x01000100);
  union {
    mlib_s16 t[4];
    mlib_d64 d;
  } f0;
  const mlib_s16 *mlib_filters_table_3;
  const mlib_s16 *mlib_filters_table_4;

  if (filter == MLIB_BICUBIC) {
    mlib_filters_table_3 = mlib_filters_s16_bc_3;
    mlib_filters_table_4 = mlib_filters_s16_bc_4;
  } else {
    mlib_filters_table_3 = mlib_filters_s16_bc2_3;
    mlib_filters_table_4 = mlib_filters_s16_bc2_4;
  }

  srcYStride >>= 1;

  for (j = yStart; j <= yFinish; j++) {

    vis_write_gsr(10 << 3);

    CLIP(3);

    cols = xRight - xLeft + 1;

    i = 0;

    if (i <= cols - 4) {

      NEXT_PIXEL_3BC_S16();
      LOAD_BC_S16_3CH_1PIXEL(mlib_filters_table_3, mlib_filters_table_4);

      NEXT_PIXEL_3BC_S16();

      BC_S16_3CH(mlib_filters_table_3, mlib_filters_table_4);
      FADD_3BC_S16();

      BC_S16_3CH(mlib_filters_table_3, mlib_filters_table_4);

#pragma pipeloop(0)
      for (; i < cols-4; i++) {
        STORE_BC_S16_3CH_1PIXEL();

        FADD_3BC_S16();
        BC_S16_3CH(mlib_filters_table_3, mlib_filters_table_4);
      }

      STORE_BC_S16_3CH_1PIXEL();

      FADD_3BC_S16();
      STORE_BC_S16_3CH_1PIXEL();

      RESULT_3BC_S16_1PIXEL();
      STORE_BC_S16_3CH_1PIXEL();

      LOAD_BC_S16_3CH_1PIXEL(mlib_filters_table_3, mlib_filters_table_4);
      RESULT_3BC_S16_1PIXEL();
      STORE_BC_S16_3CH_1PIXEL();
      i += 4;
    }

    for (; i < cols; i++) {
      NEXT_PIXEL_3BC_S16();
      LOAD_BC_S16_3CH_1PIXEL(mlib_filters_table_3, mlib_filters_table_4);
      RESULT_3BC_S16_1PIXEL();
      STORE_BC_S16_3CH_1PIXEL();
    }
  }

  return MLIB_SUCCESS;
}

/***************************************************************/
#define NEXT_PIXEL_4BC_S16()                                    \
  xSrc = (X >> MLIB_SHIFT)-1;                                   \
  ySrc = (Y >> MLIB_SHIFT)-1;                                   \
  sPtr = (mlib_s16 *)lineAddr[ySrc] + (xSrc << 2)

/***************************************************************/
#define LOAD_BC_S16_4CH_1PIXEL(mlib_filters_s16_4)                      \
  dpSrc = vis_alignaddr(sPtr, 0);                                       \
  data0 = dpSrc[0];                                                     \
  data1 = dpSrc[1];                                                     \
  data2 = dpSrc[2];                                                     \
  data3 = dpSrc[3];                                                     \
  data4 = dpSrc[4];                                                     \
  row00 = vis_faligndata(data0, data1);                                 \
  row01 = vis_faligndata(data1, data2);                                 \
  row02 = vis_faligndata(data2, data3);                                 \
  row03 = vis_faligndata(data3, data4);                                 \
  sPtr += srcYStride;                                                   \
  dpSrc = vis_alignaddr(sPtr, 0);                                       \
  data0 = dpSrc[0];                                                     \
  data1 = dpSrc[1];                                                     \
  data2 = dpSrc[2];                                                     \
  data3 = dpSrc[3];                                                     \
  data4 = dpSrc[4];                                                     \
  row10 = vis_faligndata(data0, data1);                                 \
  row11 = vis_faligndata(data1, data2);                                 \
  row12 = vis_faligndata(data2, data3);                                 \
  row13 = vis_faligndata(data3, data4);                                 \
  sPtr += srcYStride;                                                   \
  dpSrc = vis_alignaddr(sPtr, 0);                                       \
  data0 = dpSrc[0];                                                     \
  data1 = dpSrc[1];                                                     \
  data2 = dpSrc[2];                                                     \
  data3 = dpSrc[3];                                                     \
  data4 = dpSrc[4];                                                     \
  row20 = vis_faligndata(data0, data1);                                 \
  row21 = vis_faligndata(data1, data2);                                 \
  row22 = vis_faligndata(data2, data3);                                 \
  row23 = vis_faligndata(data3, data4);                                 \
  sPtr += srcYStride;                                                   \
  dpSrc = vis_alignaddr(sPtr, 0);                                       \
  data0 = dpSrc[0];                                                     \
  data1 = dpSrc[1];                                                     \
  data2 = dpSrc[2];                                                     \
  data3 = dpSrc[3];                                                     \
  data4 = dpSrc[4];                                                     \
  row30 = vis_faligndata(data0, data1);                                 \
  row31 = vis_faligndata(data1, data2);                                 \
  row32 = vis_faligndata(data2, data3);                                 \
  row33 = vis_faligndata(data3, data4);                                 \
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
  dpSrc = vis_alignaddr(sPtr, 0);                                       \
  u00 = vis_fmul8sux16(row10, yFilter1);                                \
  u01 = vis_fmul8ulx16(row10, yFilter1);                                \
  data0 = dpSrc[0];                                                     \
  filterposy = (Y >> FILTER_SHIFT);                                     \
  v03 = vis_fpadd16(u30, u31);                                          \
  data1 = dpSrc[1];                                                     \
  u10 = vis_fmul8sux16(row11, yFilter1);                                \
  data2 = dpSrc[2];                                                     \
  u11 = vis_fmul8ulx16(row11, yFilter1);                                \
  v10 = vis_fpadd16(u00, u01);                                          \
  data3 = dpSrc[3];                                                     \
  u20 = vis_fmul8sux16(row12, yFilter1);                                \
  v11 = vis_fpadd16(u10, u11);                                          \
  data4 = dpSrc[4];                                                     \
  u21 = vis_fmul8ulx16(row12, yFilter1);                                \
  row00 = vis_faligndata(data0, data1);                                 \
  u30 = vis_fmul8sux16(row13, yFilter1);                                \
  row01 = vis_faligndata(data1, data2);                                 \
  u31 = vis_fmul8ulx16(row13, yFilter1);                                \
  row02 = vis_faligndata(data2, data3);                                 \
  u00 = vis_fmul8sux16(row20, yFilter2);                                \
  row03 = vis_faligndata(data3, data4);                                 \
  filterposx = (X >> FILTER_SHIFT);                                     \
  sPtr += srcYStride;                                                   \
  v12 = vis_fpadd16(u20, u21);                                          \
  dpSrc = vis_alignaddr(sPtr, 0);                                       \
  u01 = vis_fmul8ulx16(row20, yFilter2);                                \
  v13 = vis_fpadd16(u30, u31);                                          \
  data0 = dpSrc[0];                                                     \
  u10 = vis_fmul8sux16(row21, yFilter2);                                \
  X += dX;                                                              \
  data1 = dpSrc[1];                                                     \
  u11 = vis_fmul8ulx16(row21, yFilter2);                                \
  v20 = vis_fpadd16(u00, u01);                                          \
  data2 = dpSrc[2];                                                     \
  u20 = vis_fmul8sux16(row22, yFilter2);                                \
  sum0 = vis_fpadd16(v00, v10);                                         \
  data3 = dpSrc[3];                                                     \
  u21 = vis_fmul8ulx16(row22, yFilter2);                                \
  data4 = dpSrc[4];                                                     \
  row10 = vis_faligndata(data0, data1);                                 \
  u30 = vis_fmul8sux16(row23, yFilter2);                                \
  row11 = vis_faligndata(data1, data2);                                 \
  u31 = vis_fmul8ulx16(row23, yFilter2);                                \
  row12 = vis_faligndata(data2, data3);                                 \
  u00 = vis_fmul8sux16(row30, yFilter3);                                \
  row13 = vis_faligndata(data3, data4);                                 \
  sPtr += srcYStride;                                                   \
  dpSrc = vis_alignaddr(sPtr, 0);                                       \
  u01 = vis_fmul8ulx16(row30, yFilter3);                                \
  v21 = vis_fpadd16(u10, u11);                                          \
  Y += dY;                                                              \
  xSrc = (X >> MLIB_SHIFT)-1;                                           \
  sum1 = vis_fpadd16(v01, v11);                                         \
  data0 = dpSrc[0];                                                     \
  u10 = vis_fmul8sux16(row31, yFilter3);                                \
  sum2 = vis_fpadd16(v02, v12);                                         \
  sum3 = vis_fpadd16(v03, v13);                                         \
  ySrc = (Y >> MLIB_SHIFT)-1;                                           \
  data1 = dpSrc[1];                                                     \
  v22 = vis_fpadd16(u20, u21);                                          \
  u11 = vis_fmul8ulx16(row31, yFilter3);                                \
  data2 = dpSrc[2];                                                     \
  sum0 = vis_fpadd16(sum0, v20);                                        \
  u20 = vis_fmul8sux16(row32, yFilter3);                                \
  data3 = dpSrc[3];                                                     \
  u21 = vis_fmul8ulx16(row32, yFilter3);                                \
  v23 = vis_fpadd16(u30, u31);                                          \
  data4 = dpSrc[4];                                                     \
  v30 = vis_fpadd16(u00, u01);                                          \
  filterposy &= FILTER_MASK;                                            \
  row20 = vis_faligndata(data0, data1);                                 \
  sum1 = vis_fpadd16(sum1, v21);                                        \
  u30 = vis_fmul8sux16(row33, yFilter3);                                \
  row21 = vis_faligndata(data1, data2);                                 \
  u31 = vis_fmul8ulx16(row33, yFilter3);                                \
  row22 = vis_faligndata(data2, data3);                                 \
  row23 = vis_faligndata(data3, data4);                                 \
  sPtr += srcYStride;                                                   \
  filterposx &= FILTER_MASK;                                            \
  v31 = vis_fpadd16(u10, u11);                                          \
  dpSrc = vis_alignaddr(sPtr, 0);                                       \
  data0 = dpSrc[0];                                                     \
  sum2 = vis_fpadd16(sum2, v22);                                        \
  sum3 = vis_fpadd16(sum3, v23);                                        \
  data1 = dpSrc[1];                                                     \
  v32 = vis_fpadd16(u20, u21);                                          \
  data2 = dpSrc[2];                                                     \
  sum0 = vis_fpadd16(sum0, v30);                                        \
  data3 = dpSrc[3];                                                     \
  v33 = vis_fpadd16(u30, u31);                                          \
  data4 = dpSrc[4];                                                     \
  row30 = vis_faligndata(data0, data1);                                 \
  v00 = vis_fmul8sux16(sum0, xFilter0);                                 \
  row31 = vis_faligndata(data1, data2);                                 \
  row32 = vis_faligndata(data2, data3);                                 \
  row33 = vis_faligndata(data3, data4);                                 \
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
  sPtr = (mlib_s16 *)lineAddr[ySrc] + (xSrc << 2)

/***************************************************************/
#define FADD_4BC_S16()                                          \
  d0 = vis_fpadd16(d0, d1);                                     \
  d2 = vis_fpadd16(d2, d3);                                     \
  d0 = vis_fpadd16(d0, d2);                                     \
  d2 = vis_fmuld8sux16(f_x01000100, vis_read_hi(d0));           \
  d3 = vis_fmuld8sux16(f_x01000100, vis_read_lo(d0));           \
  res = vis_fpackfix_pair(d2, d3)

/***************************************************************/
mlib_status mlib_ImageAffine_s16_4ch_bc (mlib_affine_param *param)
{
  DECLAREVAR_BC();
  DTYPE  *dstLineEnd;
  mlib_s32  filterposx, filterposy;
  mlib_d64  data0, data1, data2, data3, data4;
  mlib_d64  sum0, sum1, sum2, sum3;
  mlib_d64  row00, row10, row20, row30;
  mlib_d64  row01, row11, row21, row31;
  mlib_d64  row02, row12, row22, row32;
  mlib_d64  row03, row13, row23, row33;
  mlib_d64  xFilter0, xFilter1, xFilter2, xFilter3;
  mlib_d64  yFilter0, yFilter1, yFilter2, yFilter3;
  mlib_d64  v00, v01, v02, v03, v10, v11, v12, v13;
  mlib_d64  v20, v21, v22, v23, v30, v31, v32, v33;
  mlib_d64  u00, u01, u10, u11, u20, u21, u30, u31;
  mlib_d64  d0, d1, d2, d3;
  mlib_d64 *yPtr, *xPtr;
  mlib_d64 *dp, *dpSrc;
  mlib_s32  cols, i, mask, gsrd;
  mlib_d64  res;
  mlib_f32  f_x01000100 = vis_to_float(0x01000100);
  const mlib_s16 *mlib_filters_table_4;

  if (filter == MLIB_BICUBIC) {
    mlib_filters_table_4 = mlib_filters_s16_bc_4;
  } else {
    mlib_filters_table_4 = mlib_filters_s16_bc2_4;
  }

  srcYStride >>= 1;

  for (j = yStart; j <= yFinish; j++) {

    vis_write_gsr(10 << 3);

    CLIP(4);
    dstLineEnd  = (DTYPE*)dstData + 4 * xRight;

    cols = xRight - xLeft + 1;
    dp = vis_alignaddr(dstPixelPtr, 0);
    dstLineEnd += 3;
    mask = vis_edge16(dstPixelPtr, dstLineEnd);
    gsrd = ((8 - (mlib_addr)dstPixelPtr) & 7);

    i = 0;

    if (i <= cols - 4) {

      NEXT_PIXEL_4BC_S16();
      LOAD_BC_S16_4CH_1PIXEL(mlib_filters_table_4);

      NEXT_PIXEL_4BC_S16();

      BC_S16_4CH(mlib_filters_table_4);
      FADD_4BC_S16();

      BC_S16_4CH(mlib_filters_table_4);

#pragma pipeloop(0)
      for (; i < cols-4; i++) {
        vis_alignaddr((void *)gsrd, 0);
        res = vis_faligndata(res, res);

        vis_pst_16(res, dp++, mask);
        vis_pst_16(res, dp, ~mask);

        FADD_4BC_S16();
        BC_S16_4CH(mlib_filters_table_4);
      }

      vis_alignaddr((void *)gsrd, 0);
      res = vis_faligndata(res, res);
      vis_pst_16(res, dp++, mask);
      vis_pst_16(res, dp, ~mask);

      FADD_4BC_S16();
      vis_alignaddr((void *)gsrd, 0);
      res = vis_faligndata(res, res);
      vis_pst_16(res, dp++, mask);
      vis_pst_16(res, dp, ~mask);

      RESULT_4BC_S16_1PIXEL();
      vis_alignaddr((void *)gsrd, 0);
      res = vis_faligndata(res, res);
      vis_pst_16(res, dp++, mask);
      vis_pst_16(res, dp, ~mask);

      LOAD_BC_S16_4CH_1PIXEL(mlib_filters_table_4);
      RESULT_4BC_S16_1PIXEL();
      vis_alignaddr((void *)gsrd, 0);
      res = vis_faligndata(res, res);
      vis_pst_16(res, dp++, mask);
      vis_pst_16(res, dp, ~mask);
      i += 4;
    }

#pragma pipeloop(0)
    for (; i < cols; i++) {
      NEXT_PIXEL_4BC_S16();
      LOAD_BC_S16_4CH_1PIXEL(mlib_filters_table_4);
      RESULT_4BC_S16_1PIXEL();
      vis_alignaddr((void *)gsrd, 0);
      res = vis_faligndata(res, res);
      vis_pst_16(res, dp++, mask);
      vis_pst_16(res, dp, ~mask);
    }
  }

  return MLIB_SUCCESS;
}

/***************************************************************/
