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
 * FUNCTION
 *      mlib_ImageAffine_u8_1ch_nn
 *      mlib_ImageAffine_u8_2ch_nn
 *      mlib_ImageAffine_u8_3ch_nn
 *      mlib_ImageAffine_u8_4ch_nn
 *      mlib_ImageAffine_s16_1ch_nn
 *      mlib_ImageAffine_s16_2ch_nn
 *      mlib_ImageAffine_s16_3ch_nn
 *      mlib_ImageAffine_s16_4ch_nn
 *        - image affine transformation with Nearest Neighbor filtering
 *
 */

#include "vis_proto.h"
#include "mlib_image.h"
#include "mlib_ImageCopy.h"
#include "mlib_ImageAffine.h"

#define BUFF_SIZE  256

/***************************************************************/
#define sp srcPixelPtr
#define dp dstPixelPtr

/***************************************************************/
#undef  DTYPE
#define DTYPE mlib_u8

#define LD_U8(sp, x) vis_read_lo(vis_ld_u8_i(sp, ((x) >> MLIB_SHIFT)))

/***************************************************************/
mlib_status mlib_ImageAffine_u8_1ch_nn(mlib_affine_param *param)
{
  DECLAREVAR();
  mlib_s32 i, size;
#ifndef _NO_LONGLONG
  mlib_s64 Y0, Y1, dYl;
#endif /* _NO_LONGLONG */

  for (j = yStart; j <= yFinish; j++) {
    mlib_d64 s0, s1;

    CLIP(1);
    size = xRight - xLeft + 1;

    while (((mlib_s32)dp & 3) && (size > 0)) {
      *dp = *(S_PTR(Y) + (X >> MLIB_SHIFT));
      dp++;
      X += dX;
      Y += dY;
      size--;
    }

#ifdef _NO_LONGLONG
#pragma pipeloop(0)
    for (i = 0; i <= (size - 4); i += 4) {
      mlib_u8 *sp0, *sp1, *sp2, *sp3;

      sp0 = S_PTR(Y);
      sp1 = S_PTR(Y +   dY);
      sp2 = S_PTR(Y + 2*dY);
      sp3 = S_PTR(Y + 3*dY);

      s0 = vis_fpmerge(LD_U8(sp0, X), LD_U8(sp2, X + 2*dX));
      s1 = vis_fpmerge(LD_U8(sp1, X + dX), LD_U8(sp3, X + 3*dX));
      s0 = vis_fpmerge(vis_read_lo(s0), vis_read_lo(s1));

      *(mlib_f32*)dp = vis_read_lo(s0);

      dp += 4;
      X += 4*dX;
      Y += 4*dY;
    }

#else
    Y0 = ((mlib_s64)(Y + dY) << 32) | Y;

    if (dY >= 0) {
      dYl = ((mlib_s64)dY << 33) | (dY << 1);
    } else {
      dYl = -(((mlib_s64)(-dY) << 33) | ((-dY) << 1));
    }

#pragma pipeloop(0)
    for (i = 0; i <= (size - 4); i += 4) {
      mlib_u8 *sp0, *sp1, *sp2, *sp3;

      Y1 = Y0 + dYl;
      sp0 = S_PTRl(Y0, 16);
      sp1 = S_PTRl(Y0, 48);
      sp2 = S_PTRl(Y1, 16);
      sp3 = S_PTRl(Y1, 48);

      s0 = vis_fpmerge(LD_U8(sp0, X), LD_U8(sp2, X + 2*dX));
      s1 = vis_fpmerge(LD_U8(sp1, X + dX), LD_U8(sp3, X + 3*dX));
      s0 = vis_fpmerge(vis_read_lo(s0), vis_read_lo(s1));

      *(mlib_f32*)dp = vis_read_lo(s0);

      dp += 4;
      X += 4*dX;
      Y0 += 2*dYl;
    }

    Y = Y0 & ((1u << 31) - 1);
#endif /* _NO_LONGLONG */

    for (i = 0; i < (size & 3); i++) {
      dp[i] = *(S_PTR(Y) + (X >> MLIB_SHIFT));
      X += dX;
      Y += dY;
    }
  }

  return MLIB_SUCCESS;
}

/***************************************************************/
#undef  LD_U8
#define LD_U8(sp, x) vis_read_lo(vis_ld_u8_i(sp, x))

/***************************************************************/
#define GET_POINTERS_2CH                                        \
  sp0 = S_PTR(Y) + 2*(X >> MLIB_SHIFT);                         \
  sp1 = S_PTR(Y +   dY) + 2*((X +   dX) >> MLIB_SHIFT);         \
  sp2 = S_PTR(Y + 2*dY) + 2*((X + 2*dX) >> MLIB_SHIFT);         \
  sp3 = S_PTR(Y + 3*dY) + 2*((X + 3*dX) >> MLIB_SHIFT);         \
  X += 4*dX;                                                    \
  Y += 4*dY

/***************************************************************/
#define AFFINE_U8_2CH                                           \
  s0 = vis_fpmerge(LD_U8(sp0, 0), LD_U8(sp2, 0));               \
  s1 = vis_fpmerge(LD_U8(sp0, 1), LD_U8(sp2, 1));               \
  s2 = vis_fpmerge(LD_U8(sp1, 0), LD_U8(sp3, 0));               \
  s3 = vis_fpmerge(LD_U8(sp1, 1), LD_U8(sp3, 1));               \
                                                                \
  s0 = vis_fpmerge(vis_read_lo(s0), vis_read_lo(s2));           \
  s1 = vis_fpmerge(vis_read_lo(s1), vis_read_lo(s3));           \
  dd = vis_fpmerge(vis_read_lo(s0), vis_read_lo(s1))

/***************************************************************/
mlib_status mlib_ImageAffine_u8_2ch_nn(mlib_affine_param *param)
{
  DECLAREVAR();
  DTYPE  *dstLineEnd;
  mlib_s32 i, size;

  for (j = yStart; j <= yFinish; j++) {
    mlib_u8  *sp0, *sp1, *sp2, *sp3;
    mlib_d64 *da, s0, s1, s2, s3, dd, d_old;
    mlib_s32 emask;

    CLIP(2);
    dstLineEnd  = (DTYPE*)dstData + 2 * xRight;
    size = xRight - xLeft + 1;
    dstLineEnd++;

    if (((mlib_s32)dp & 7) == 0) {
#pragma pipeloop(0)
      for (i = 0; i <= (size - 4); i += 4) {
        GET_POINTERS_2CH;
        AFFINE_U8_2CH;
        *(mlib_d64*)dp = dd;
        dp += 8;
      }

      if (i < size) {
        sp0 = sp1 = sp2 = sp3 = S_PTR(Y) + 2*(X >> MLIB_SHIFT);
        if (i + 1 < size) sp1 = S_PTR(Y +   dY) + 2*((X +   dX) >> MLIB_SHIFT);
        if (i + 2 < size) sp2 = S_PTR(Y + 2*dY) + 2*((X + 2*dX) >> MLIB_SHIFT);
        if (i + 3 < size) sp3 = S_PTR(Y + 3*dY) + 2*((X + 3*dX) >> MLIB_SHIFT);

        AFFINE_U8_2CH;
        emask = vis_edge8(dp, dstLineEnd);
        vis_pst_8(dd, dp, emask);
      }

    } else {
      da = vis_alignaddr(dp, 0);
      d_old = vis_faligndata(da[0], da[0]);
      vis_alignaddr((void*)0, (mlib_u8*)da - dp);

#pragma pipeloop(0)
      for (i = 0; i <= (size - 4); i += 4) {
        GET_POINTERS_2CH;
        AFFINE_U8_2CH;

        *da++ = vis_faligndata(d_old, dd);
        d_old = dd;
      }

      if (i < size) {
        sp0 = sp1 = sp2 = sp3 = S_PTR(Y) + 2*(X >> MLIB_SHIFT);
        if (i + 1 < size) sp1 = S_PTR(Y +   dY) + 2*((X +   dX) >> MLIB_SHIFT);
        if (i + 2 < size) sp2 = S_PTR(Y + 2*dY) + 2*((X + 2*dX) >> MLIB_SHIFT);
        if (i + 3 < size) sp3 = S_PTR(Y + 3*dY) + 2*((X + 3*dX) >> MLIB_SHIFT);

        AFFINE_U8_2CH;
      }

      emask = vis_edge8(da, dstLineEnd);
      vis_pst_8(vis_faligndata(d_old, dd), da++, emask);

      if ((mlib_u8*)da <= dstLineEnd) {
        emask = vis_edge8(da, dstLineEnd);
        vis_pst_8(vis_faligndata(dd, dd), da, emask);
      }
    }
  }

  return MLIB_SUCCESS;
}

/***************************************************************/
#undef  LD_U8
#define LD_U8(sp, x) vis_read_lo(vis_ld_u8(sp + x))

/***************************************************************/
mlib_status mlib_ImageAffine_u8_3ch_nn(mlib_affine_param *param)
{
  DECLAREVAR();
  DTYPE  *srcPixelPtr;
  mlib_s32 i, size;

  for (j = yStart; j <= yFinish; j++) {
    mlib_d64 s0, s1, s2, s3, s4, s5;

    CLIP(3);
    size = xRight - xLeft + 1;

    while (((mlib_s32)dp & 3) && (size > 0)) {
      sp = S_PTR(Y) + 3*(X >> MLIB_SHIFT);
      dp[0] = sp[0];
      dp[1] = sp[1];
      dp[2] = sp[2];
      dp += 3;
      X += dX;
      Y += dY;
      size--;
    }

#pragma pipeloop(0)
    for (i = 0; i <= (size - 4); i += 4) {
      mlib_u8 *sp0, *sp1, *sp2, *sp3;

      sp0 = S_PTR(Y);
      sp1 = S_PTR(Y +   dY);
      sp2 = S_PTR(Y + 2*dY);
      sp3 = S_PTR(Y + 3*dY);

      sp0 += 3*(X >> MLIB_SHIFT);
      sp1 += 3*((X + dX) >> MLIB_SHIFT);
      sp2 += 3*((X + 2*dX) >> MLIB_SHIFT);
      sp3 += 3*((X + 3*dX) >> MLIB_SHIFT);

      s0 = vis_fpmerge(LD_U8(sp0, 0), LD_U8(sp0, 2));
      s1 = vis_fpmerge(LD_U8(sp0, 1), LD_U8(sp1, 0));
      s0 = vis_fpmerge(vis_read_lo(s0), vis_read_lo(s1));
      s2 = vis_fpmerge(LD_U8(sp1, 1), LD_U8(sp2, 0));
      s3 = vis_fpmerge(LD_U8(sp1, 2), LD_U8(sp2, 1));
      s2 = vis_fpmerge(vis_read_lo(s2), vis_read_lo(s3));
      s4 = vis_fpmerge(LD_U8(sp2, 2), LD_U8(sp3, 1));
      s5 = vis_fpmerge(LD_U8(sp3, 0), LD_U8(sp3, 2));
      s4 = vis_fpmerge(vis_read_lo(s4), vis_read_lo(s5));

      ((mlib_f32*)dp)[0] = vis_read_lo(s0);
      ((mlib_f32*)dp)[1] = vis_read_lo(s2);
      ((mlib_f32*)dp)[2] = vis_read_lo(s4);

      dp += 12;
      X += 4*dX;
      Y += 4*dY;
    }

    for (i = 0; i < (size & 3); i++) {
      sp = S_PTR(Y) + 3*(X >> MLIB_SHIFT);
      dp[0] = sp[0];
      dp[1] = sp[1];
      dp[2] = sp[2];
      dp += 3;
      X += dX;
      Y += dY;
    }
  }

  return MLIB_SUCCESS;
}

/***************************************************************/
#undef  LD_U8
#define LD_U8(sp, x) vis_read_lo(vis_ld_u8_i(sp, x))

/***************************************************************/
#define AFFINE_U8_4x2                                           \
  sp0 = S_PTR(Y) + 4*(X >> MLIB_SHIFT);                         \
  sp1 = S_PTR(Y + dY) + 4*((X + dX) >> MLIB_SHIFT);             \
                                                                \
  s0 = vis_fpmerge(LD_U8(sp0, 0), LD_U8(sp1, 0));               \
  s1 = vis_fpmerge(LD_U8(sp0, 1), LD_U8(sp1, 1));               \
  s2 = vis_fpmerge(LD_U8(sp0, 2), LD_U8(sp1, 2));               \
  s3 = vis_fpmerge(LD_U8(sp0, 3), LD_U8(sp1, 3));               \
                                                                \
  s0 = vis_fpmerge(vis_read_lo(s0), vis_read_lo(s2));           \
  s1 = vis_fpmerge(vis_read_lo(s1), vis_read_lo(s3));           \
  dd = vis_fpmerge(vis_read_lo(s0), vis_read_lo(s1));           \
                                                                \
  X += 2*dX;                                                    \
  Y += 2*dY

/***************************************************************/
#define AFFINE_U8_4x1                                           \
  sp0 = S_PTR(Y) + 4*(X >> MLIB_SHIFT);                         \
                                                                \
  s0 = vis_fpmerge(LD_U8(sp0, 0), LD_U8(sp0, 2));               \
  s1 = vis_fpmerge(LD_U8(sp0, 1), LD_U8(sp0, 3));               \
  s0 = vis_fpmerge(vis_read_lo(s0), vis_read_lo(s1));           \
  dd = vis_freg_pair(vis_read_lo(s0), vis_fzeros())

/***************************************************************/
mlib_status mlib_ImageAffine_u8_4ch_nn(mlib_affine_param *param)
{
  DECLAREVAR();
  DTYPE  *dstLineEnd;
  mlib_s32 i, size;

  for (j = yStart; j <= yFinish; j++) {
    mlib_u8  *sp0, *sp1;
    mlib_d64 *da, s0, s1, s2, s3, dd, d_old;
    mlib_s32 emask;

    CLIP(4);
    dstLineEnd  = (DTYPE*)dstData + 4 * xRight;
    size = xRight - xLeft + 1;

    if (((mlib_s32)dp & 7) == 0) {
#pragma pipeloop(0)
      for (i = 0; i <= (size - 2); i += 2) {
        AFFINE_U8_4x2;
        *(mlib_d64*)dp = dd;
        dp += 8;
      }

      if (i < size) {
        AFFINE_U8_4x1;
        *(mlib_f32*)dp = vis_read_hi(dd);
      }

    } else {
      da = vis_alignaddr(dp, 0);
      d_old = vis_faligndata(da[0], da[0]);
      vis_alignaddr((void*)0, (mlib_u8*)da - dp);

#pragma pipeloop(0)
      for (i = 0; i <= (size - 2); i += 2) {
        AFFINE_U8_4x2;

        *da++ = vis_faligndata(d_old, dd);
        d_old = dd;
      }

      if (i < size) {
        AFFINE_U8_4x1;
      }

      dstLineEnd += 3;
      emask = vis_edge8(da, dstLineEnd);
      vis_pst_8(vis_faligndata(d_old, dd), da++, emask);

      if ((mlib_u8*)da <= dstLineEnd) {
        emask = vis_edge8(da, dstLineEnd);
        vis_pst_8(vis_faligndata(dd, dd), da, emask);
      }
    }
  }

  return MLIB_SUCCESS;
}

/***************************************************************/
#undef  DTYPE
#define DTYPE mlib_u16

#define SHIFT1(x) (((x) >> (MLIB_SHIFT - 1)) &~ 1)

/***************************************************************/
mlib_status mlib_ImageAffine_s16_1ch_nn(mlib_affine_param *param)
{
  DECLAREVAR();
  mlib_s32 i, size;

  vis_alignaddr((void*)0, 6);

  for (j = yStart; j <= yFinish; j++) {
    mlib_d64 ss;

    CLIP(1);
    size = xRight - xLeft + 1;

    while (((mlib_s32)dp & 7) && (size > 0)) {
      *dp = *(S_PTR(Y) + (X >> MLIB_SHIFT));
      dp++;
      X += dX;
      Y += dY;
      size--;
    }

#pragma pipeloop(0)
    for (i = 0; i <= (size - 4); i += 4) {
      mlib_u16 *sp0, *sp1, *sp2, *sp3;

      sp0 = S_PTR(Y);
      sp1 = S_PTR(Y +   dY);
      sp2 = S_PTR(Y + 2*dY);
      sp3 = S_PTR(Y + 3*dY);

      ss = vis_faligndata(vis_ld_u16_i(sp3, SHIFT1(X + 3*dX)), ss);
      ss = vis_faligndata(vis_ld_u16_i(sp2, SHIFT1(X + 2*dX)), ss);
      ss = vis_faligndata(vis_ld_u16_i(sp1, SHIFT1(X +   dX)), ss);
      ss = vis_faligndata(vis_ld_u16_i(sp0, SHIFT1(X)), ss);

      *(mlib_d64*)dp = ss;

      dp += 4;
      X += 4*dX;
      Y += 4*dY;
    }

    for (i = 0; i < (size & 3); i++) {
      dp[i] = *(S_PTR(Y) + (X >> MLIB_SHIFT));
      X += dX;
      Y += dY;
    }
  }

  return MLIB_SUCCESS;
}

/***************************************************************/
mlib_status mlib_ImageAffine_s16_2ch_nn(mlib_affine_param *param)
{
  DECLAREVAR();
  DTYPE  *srcPixelPtr;
  DTYPE  *dstLineEnd;

  for (j = yStart; j <= yFinish; j++) {
    CLIP(2);
    dstLineEnd  = (DTYPE*)dstData + 2 * xRight;

#pragma pipeloop(0)
    for (; dp <= dstLineEnd; dp += 2) {
      sp = S_PTR(Y) + 2*(X >> MLIB_SHIFT);
      dp[0] = sp[0];
      dp[1] = sp[1];

      X += dX;
      Y += dY;
    }
  }

  return MLIB_SUCCESS;
}

/***************************************************************/
#undef  LD_U16
#define LD_U16(sp, x) vis_ld_u16(sp + x)

/***************************************************************/
mlib_status mlib_ImageAffine_s16_3ch_nn(mlib_affine_param *param)
{
  DECLAREVAR();
  DTYPE  *srcPixelPtr;
  mlib_s32 i, size;

  vis_alignaddr((void*)0, 6);

  for (j = yStart; j <= yFinish; j++) {
    mlib_d64 s0, s1, s2;

    CLIP(3);
    size = xRight - xLeft + 1;

    while (((mlib_s32)dp & 7) && (size > 0)) {
      sp = S_PTR(Y) + 3*(X >> MLIB_SHIFT);
      dp[0] = sp[0];
      dp[1] = sp[1];
      dp[2] = sp[2];
      dp += 3;
      X += dX;
      Y += dY;
      size--;
    }

#pragma pipeloop(0)
    for (i = 0; i <= (size - 4); i += 4) {
      mlib_u16 *sp0, *sp1, *sp2, *sp3;

      sp0 = S_PTR(Y);
      sp1 = S_PTR(Y +   dY);
      sp2 = S_PTR(Y + 2*dY);
      sp3 = S_PTR(Y + 3*dY);

      sp0 += 3*(X >> MLIB_SHIFT);
      sp1 += 3*((X + dX) >> MLIB_SHIFT);
      sp2 += 3*((X + 2*dX) >> MLIB_SHIFT);
      sp3 += 3*((X + 3*dX) >> MLIB_SHIFT);

      s2 = vis_faligndata(LD_U16(sp3, 2), s2);
      s2 = vis_faligndata(LD_U16(sp3, 1), s2);
      s2 = vis_faligndata(LD_U16(sp3, 0), s2);
      s2 = vis_faligndata(LD_U16(sp2, 2), s2);
      s1 = vis_faligndata(LD_U16(sp2, 1), s1);
      s1 = vis_faligndata(LD_U16(sp2, 0), s1);
      s1 = vis_faligndata(LD_U16(sp1, 2), s1);
      s1 = vis_faligndata(LD_U16(sp1, 1), s1);
      s0 = vis_faligndata(LD_U16(sp1, 0), s0);
      s0 = vis_faligndata(LD_U16(sp0, 2), s0);
      s0 = vis_faligndata(LD_U16(sp0, 1), s0);
      s0 = vis_faligndata(LD_U16(sp0, 0), s0);

      ((mlib_d64*)dp)[0] = s0;
      ((mlib_d64*)dp)[1] = s1;
      ((mlib_d64*)dp)[2] = s2;

      dp += 12;
      X += 4*dX;
      Y += 4*dY;
    }

    for (i = 0; i < (size & 3); i++) {
      sp = S_PTR(Y) + 3*(X >> MLIB_SHIFT);
      dp[0] = sp[0];
      dp[1] = sp[1];
      dp[2] = sp[2];
      dp += 3;
      X += dX;
      Y += dY;
    }
  }

  return MLIB_SUCCESS;
}

/***************************************************************/
#define AFFINE_S16_4ch                                          \
  sp = S_PTR(Y) + 4*(X >> MLIB_SHIFT);                          \
                                                                \
  dd = vis_faligndata(LD_U16(sp, 3), dd);                       \
  dd = vis_faligndata(LD_U16(sp, 2), dd);                       \
  dd = vis_faligndata(LD_U16(sp, 1), dd);                       \
  dd = vis_faligndata(LD_U16(sp, 0), dd);                       \
                                                                \
  X += dX;                                                      \
  Y += dY

/***************************************************************/
mlib_status mlib_ImageAffine_s16_4ch_nn(mlib_affine_param *param)
{
  DECLAREVAR();
  DTYPE  *srcPixelPtr;
  mlib_s32 i, size, max_xsize = param -> max_xsize;
  mlib_d64 buff[BUFF_SIZE], *pbuff = buff;

  if (max_xsize > BUFF_SIZE) {
    pbuff = mlib_malloc(sizeof(mlib_d64)*max_xsize);
  }

  for (j = yStart; j <= yFinish; j++) {
    mlib_d64 *da, dd;

    vis_alignaddr((void*)0, 6);

    CLIP(4);
    size = xRight - xLeft + 1;

    if ((mlib_s32)dp & 7) {
      da = buff;
    } else {
      da = (mlib_d64*)dp;
    }

#pragma pipeloop(0)
    for (i = 0; i < size; i++) {
      AFFINE_S16_4ch;
      da[i] = dd;
    }

    if ((mlib_s32)dp & 7) {
      mlib_ImageCopy_na((mlib_u8*)buff, (mlib_u8*)dp, 8*size);
    }
  }

  if (pbuff != buff) {
    mlib_free(pbuff);
  }

  return MLIB_SUCCESS;
}

/***************************************************************/
