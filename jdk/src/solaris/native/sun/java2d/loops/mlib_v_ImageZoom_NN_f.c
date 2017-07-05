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
 * FUNCTION
 *      mlib_ImageZoom - image scaling with edge condition
 *
 * SYNOPSIS
 *      mlib_status mlib_ImageZoom(mlib_image       *dst,
 *                                 const mlib_image *src,
 *                                 mlib_f32         zoomx,
 *                                 mlib_f32         zoomy,
 *                                 mlib_filter      filter,
 *                                 mlib_edge        edge)
 *
 * ARGUMENTS
 *      dst       Pointer to destination image
 *      src       Pointer to source image
 *      zoomx     X zoom factor.
 *      zoomy     Y zoom factor.
 *      filter    Type of resampling filter.
 *      edge      Type of edge condition.
 *
 * DESCRIPTION
 *  The center of the source image is mapped to the center of the
 *  destination image.
 *
 *  The upper-left corner pixel of an image is located at (0.5, 0.5).
 *
 *  The resampling filter can be one of the following:
 *    MLIB_NEAREST
 *    MLIB_BILINEAR
 *    MLIB_BICUBIC
 *    MLIB_BICUBIC2
 *
 *  The edge condition can be one of the following:
 *    MLIB_EDGE_DST_NO_WRITE  (default)
 *    MLIB_EDGE_DST_FILL_ZERO
 *    MLIB_EDGE_OP_NEAREST
 *    MLIB_EDGE_SRC_EXTEND
 *    MLIB_EDGE_SRC_PADDED
 */

#include <mlib_image.h>
#include <vis_proto.h>

/***************************************************************/

#define  _MLIB_VIS_VER_
#include <mlib_ImageZoom.h>

/***************************************************************/

#define  VARIABLE(FORMAT)                                       \
  mlib_s32 j,                                                   \
           dx = GetElemStruct(DX),                              \
           dy = GetElemStruct(DY),                              \
           x = GetElemSubStruct(current, srcX),                 \
           y = GetElemSubStruct(current, srcY),                 \
           src_stride = GetElemStruct(src_stride),              \
           dst_stride = GetElemStruct(dst_stride),              \
           width  = GetElemSubStruct(current, width),           \
           height = GetElemSubStruct(current, height);          \
  FORMAT  *sp = GetElemSubStruct(current, sp),                  \
          *dp = GetElemSubStruct(current, dp)

/***************************************************************/

mlib_status mlib_ImageZoom_U8_1_Nearest(mlib_work_image *param)
{
  VARIABLE(mlib_u8);
  mlib_u8  *dl = dp, *tsp;
  mlib_s32 y0 = -1, dx7 = 7*dx, dx15 = 8*dx + dx7;

  tsp = sp;
  y = GetElemSubStruct(current, srcY) & MLIB_MASK;

  for (j = 0; j < height; j++) {

    if ((y0 >> MLIB_SHIFT) == (y >> MLIB_SHIFT)) {
      mlib_ImageCopy_na(dl - dst_stride, dl, width);
    }
    else {
      mlib_u8 *dp = dl, *dend = dl + width;

      vis_write_gsr(7);
      x = GetElemSubStruct(current, srcX) & MLIB_MASK;

      while (((mlib_addr)dp & 7) && (dp < dend)) {
        *dp++ = tsp[x >> MLIB_SHIFT];
        x += dx;
      }

      x += dx7;

#pragma pipeloop(0)
      for (; dp <= dend - 8; dp += 8) {
        mlib_d64 s0;

        s0 = vis_faligndata(vis_ld_u8_i(tsp, x >> MLIB_SHIFT), s0);
        x -= dx;
        s0 = vis_faligndata(vis_ld_u8_i(tsp, x >> MLIB_SHIFT), s0);
        x -= dx;
        s0 = vis_faligndata(vis_ld_u8_i(tsp, x >> MLIB_SHIFT), s0);
        x -= dx;
        s0 = vis_faligndata(vis_ld_u8_i(tsp, x >> MLIB_SHIFT), s0);
        x -= dx;
        s0 = vis_faligndata(vis_ld_u8_i(tsp, x >> MLIB_SHIFT), s0);
        x -= dx;
        s0 = vis_faligndata(vis_ld_u8_i(tsp, x >> MLIB_SHIFT), s0);
        x -= dx;
        s0 = vis_faligndata(vis_ld_u8_i(tsp, x >> MLIB_SHIFT), s0);
        x -= dx;
        s0 = vis_faligndata(vis_ld_u8_i(tsp, x >> MLIB_SHIFT), s0);
        x += dx15;

        *(mlib_d64*)dp = s0;
      }

      x -= dx7;

      while (dp < dend) {
        *dp++ = tsp[x >> MLIB_SHIFT];
        x += dx;
      }
    }

    y0 = y;
    y += dy;
    dl  = (void*)((mlib_u8*)dl + dst_stride);
    tsp = (void*)((mlib_u8*)sp + (y >> MLIB_SHIFT) * src_stride);
  }

  return MLIB_SUCCESS;
}

/***************************************************************/

mlib_status mlib_ImageZoom_U8_3_Nearest(mlib_work_image *param)
{
  VARIABLE(mlib_u8);
  mlib_u8  *dl = dp, *tsp, *tt;
  mlib_s32 cx, y0 = -1, dx7 = 7*dx, dx15 = 8*dx + dx7;

  tsp = sp;
  y = GetElemSubStruct(current, srcY) & MLIB_MASK;

  for (j = 0; j < height; j++) {

    if ((y0 >> MLIB_SHIFT) == (y >> MLIB_SHIFT)) {
      mlib_ImageCopy_na(dl - dst_stride, dl, 3*width);
    }
    else {
      mlib_u8 *dp = dl, *dend = dl + 3*width;

      vis_write_gsr(7);
      x = GetElemSubStruct(current, srcX) & MLIB_MASK;

      while (((mlib_addr)dp & 7) && (dp < dend)) {
        cx = x >> MLIB_SHIFT;
        tt = tsp + 2*cx + cx;
        dp[0] = tt[0];
        dp[1] = tt[1];
        dp[2] = tt[2];
        x += dx;
        dp += 3;
      }

      x += dx7;

#pragma pipeloop(0)
      for (; dp <= dend - 24; dp += 24) {
        mlib_d64 s0, s1, s2;

        cx = x >> MLIB_SHIFT;
        tt = tsp + 2*cx + cx;
        x -= dx;
        s2 = vis_faligndata(vis_ld_u8_i(tt, 2), s2);
        s2 = vis_faligndata(vis_ld_u8_i(tt, 1), s2);
        s2 = vis_faligndata(vis_ld_u8_i(tt, 0), s2);

        cx = x >> MLIB_SHIFT;
        tt = tsp + 2*cx + cx;
        x -= dx;
        s2 = vis_faligndata(vis_ld_u8_i(tt, 2), s2);
        s2 = vis_faligndata(vis_ld_u8_i(tt, 1), s2);
        s2 = vis_faligndata(vis_ld_u8_i(tt, 0), s2);

        cx = x >> MLIB_SHIFT;
        tt = tsp + 2*cx + cx;
        x -= dx;
        s2 = vis_faligndata(vis_ld_u8_i(tt, 2), s2);
        s2 = vis_faligndata(vis_ld_u8_i(tt, 1), s2);
        s1 = vis_faligndata(vis_ld_u8_i(tt, 0), s1);

        cx = x >> MLIB_SHIFT;
        tt = tsp + 2*cx + cx;
        x -= dx;
        s1 = vis_faligndata(vis_ld_u8_i(tt, 2), s1);
        s1 = vis_faligndata(vis_ld_u8_i(tt, 1), s1);
        s1 = vis_faligndata(vis_ld_u8_i(tt, 0), s1);

        cx = x >> MLIB_SHIFT;
        tt = tsp + 2*cx + cx;
        x -= dx;
        s1 = vis_faligndata(vis_ld_u8_i(tt, 2), s1);
        s1 = vis_faligndata(vis_ld_u8_i(tt, 1), s1);
        s1 = vis_faligndata(vis_ld_u8_i(tt, 0), s1);

        cx = x >> MLIB_SHIFT;
        tt = tsp + 2*cx + cx;
        x -= dx;
        s1 = vis_faligndata(vis_ld_u8_i(tt, 2), s1);
        s0 = vis_faligndata(vis_ld_u8_i(tt, 1), s0);
        s0 = vis_faligndata(vis_ld_u8_i(tt, 0), s0);

        cx = x >> MLIB_SHIFT;
        tt = tsp + 2*cx + cx;
        x -= dx;
        s0 = vis_faligndata(vis_ld_u8_i(tt, 2), s0);
        s0 = vis_faligndata(vis_ld_u8_i(tt, 1), s0);
        s0 = vis_faligndata(vis_ld_u8_i(tt, 0), s0);

        cx = x >> MLIB_SHIFT;
        tt = tsp + 2*cx + cx;
        x += dx15;
        s0 = vis_faligndata(vis_ld_u8_i(tt, 2), s0);
        s0 = vis_faligndata(vis_ld_u8_i(tt, 1), s0);
        s0 = vis_faligndata(vis_ld_u8_i(tt, 0), s0);

        ((mlib_d64*)dp)[0] = s0;
        ((mlib_d64*)dp)[1] = s1;
        ((mlib_d64*)dp)[2] = s2;
      }

      x -= dx7;

      while (dp < dend) {
        cx = x >> MLIB_SHIFT;
        tt = tsp + 2*cx + cx;
        dp[0] = tt[0];
        dp[1] = tt[1];
        dp[2] = tt[2];
        x += dx;
        dp += 3;
      }
    }

    y0 = y;
    y += dy;
    dl  = (void*)((mlib_u8*)dl + dst_stride);
    tsp = (void*)((mlib_u8*)sp + (y >> MLIB_SHIFT) * src_stride);
  }

  return MLIB_SUCCESS;
}

/***************************************************************/

mlib_status mlib_ImageZoom_S16_3_Nearest(mlib_work_image *param)
{
  VARIABLE(mlib_s16);
  mlib_s16 *dl = dp, *tsp, *tt;
  mlib_s32 cx, y0 = -1, dx3 = 3*dx, dx7 = 4*dx + dx3;

  tsp = sp;
  y = GetElemSubStruct(current, srcY) & MLIB_MASK;

  for (j = 0; j < height; j++) {

    if ((y0 >> MLIB_SHIFT) == (y >> MLIB_SHIFT)) {
      mlib_ImageCopy_na((void*)((mlib_u8*)dl - dst_stride), (void*)dl, 6*width);
    }
    else {
      mlib_s16 *dp = dl, *dend = dl + 3*width;

      vis_write_gsr(6);
      x = GetElemSubStruct(current, srcX) & MLIB_MASK;

      while (((mlib_addr)dp & 7) && (dp < dend)) {
        cx = x >> MLIB_SHIFT;
        tt = tsp + 2*cx + cx;
        dp[0] = tt[0];
        dp[1] = tt[1];
        dp[2] = tt[2];
        x += dx;
        dp += 3;
      }

      x += dx3;

#pragma pipeloop(0)
      for (; dp <= dend - 12; dp += 12) {
        mlib_d64 s0, s1, s2;

        cx = x >> MLIB_SHIFT;
        tt = tsp + 2*cx + cx;
        x -= dx;
        s2 = vis_faligndata(vis_ld_u16_i(tt, 4), s2);
        s2 = vis_faligndata(vis_ld_u16_i(tt, 2), s2);
        s2 = vis_faligndata(vis_ld_u16_i(tt, 0), s2);

        cx = x >> MLIB_SHIFT;
        tt = tsp + 2*cx + cx;
        x -= dx;
        s2 = vis_faligndata(vis_ld_u16_i(tt, 4), s2);
        s1 = vis_faligndata(vis_ld_u16_i(tt, 2), s1);
        s1 = vis_faligndata(vis_ld_u16_i(tt, 0), s1);

        cx = x >> MLIB_SHIFT;
        tt = tsp + 2*cx + cx;
        x -= dx;
        s1 = vis_faligndata(vis_ld_u16_i(tt, 4), s1);
        s1 = vis_faligndata(vis_ld_u16_i(tt, 2), s1);
        s0 = vis_faligndata(vis_ld_u16_i(tt, 0), s0);

        cx = x >> MLIB_SHIFT;
        tt = tsp + 2*cx + cx;
        x += dx7;
        s0 = vis_faligndata(vis_ld_u16_i(tt, 4), s0);
        s0 = vis_faligndata(vis_ld_u16_i(tt, 2), s0);
        s0 = vis_faligndata(vis_ld_u16_i(tt, 0), s0);

        ((mlib_d64*)dp)[0] = s0;
        ((mlib_d64*)dp)[1] = s1;
        ((mlib_d64*)dp)[2] = s2;
      }

      x -= dx3;

      while (dp < dend) {
        cx = x >> MLIB_SHIFT;
        tt = tsp + 2*cx + cx;
        dp[0] = tt[0];
        dp[1] = tt[1];
        dp[2] = tt[2];
        x += dx;
        dp += 3;
      }
    }

    y0 = y;
    y += dy;
    dl  = (void*)((mlib_u8*)dl + dst_stride);
    tsp = (void*)((mlib_u8*)sp + (y >> MLIB_SHIFT) * src_stride);
  }

  return MLIB_SUCCESS;
}

/***************************************************************/

mlib_status mlib_ImageZoom_S16_1_Nearest(mlib_work_image *param)
{
  VARIABLE(mlib_u16);
  mlib_u16  *dl = dp, *tsp;
  mlib_s32  y0 = -1, dx3 = 3*dx, dx7 = 4*dx + dx3;

  tsp = sp;
  y = GetElemSubStruct(current, srcY) & MLIB_MASK;

  for (j = 0; j < height; j++) {

    if ((y0 >> MLIB_SHIFT) == (y >> MLIB_SHIFT)) {
      mlib_ImageCopy_na((void*)((mlib_u8*)dl - dst_stride), (void*)dl, 2*width);
    }
    else {
      mlib_u16 *dp = dl, *dend = dl + width;

      vis_write_gsr(6);
      x = GetElemSubStruct(current, srcX) & MLIB_MASK;

      while (((mlib_addr)dp & 7) && (dp < dend)) {
        *dp++ = tsp[x >> MLIB_SHIFT];
        x += dx;
      }

      x += dx3;

#pragma pipeloop(0)
      for (; dp <= dend - 4; dp += 4) {
        mlib_d64 s0;

        s0 = vis_faligndata(vis_ld_u16_i(tsp, (x >> (MLIB_SHIFT - 1)) &~ 1), s0);
        x -= dx;
        s0 = vis_faligndata(vis_ld_u16_i(tsp, (x >> (MLIB_SHIFT - 1)) &~ 1), s0);
        x -= dx;
        s0 = vis_faligndata(vis_ld_u16_i(tsp, (x >> (MLIB_SHIFT - 1)) &~ 1), s0);
        x -= dx;
        s0 = vis_faligndata(vis_ld_u16_i(tsp, (x >> (MLIB_SHIFT - 1)) &~ 1), s0);
        x += dx7;

        *(mlib_d64*)dp = s0;
      }

      x -= dx3;

      while (dp < dend) {
        *dp++ = tsp[x >> MLIB_SHIFT];
        x += dx;
      }
    }

    y0 = y;
    y += dy;
    dl  = (void*)((mlib_u8*)dl + dst_stride);
    tsp = (void*)((mlib_u8*)sp + (y >> MLIB_SHIFT) * src_stride);
  }

  return MLIB_SUCCESS;
}

/***************************************************************/
