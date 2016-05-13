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
 *      the bilinear filtering.
 *
 */

#include "vis_proto.h"
#include "mlib_image.h"
#include "mlib_ImageCopy.h"
#include "mlib_ImageAffine.h"
#include "mlib_v_ImageFilters.h"
#include "mlib_v_ImageChannelExtract.h"
#include "mlib_v_ImageAffine_BL_S16.h"

/*#define MLIB_VIS2*/

/***************************************************************/
#define DTYPE mlib_s16

#define FUN_NAME(CHAN) mlib_ImageAffine_u16_##CHAN##_bl

/***************************************************************/
mlib_status FUN_NAME(2ch_na)(mlib_affine_param *param);
mlib_status FUN_NAME(4ch_na)(mlib_affine_param *param);

/***************************************************************/
#define XOR_8000(x) x = vis_fxor(x, mask_8000)

/***************************************************************/
#ifdef MLIB_VIS2
#define MLIB_WRITE_BMASK(bmask) vis_write_bmask(bmask, 0)
#else
#define MLIB_WRITE_BMASK(bmask)
#endif /* MLIB_VIS2 */

/***************************************************************/
#undef  DECLAREVAR
#define DECLAREVAR()                                            \
  DECLAREVAR0();                                                \
  mlib_s32  *warp_tbl   = param -> warp_tbl;                    \
  mlib_s32  srcYStride = param -> srcYStride;                   \
  mlib_u8   *dl;                                                \
  mlib_s32  i, size;                                            \
  mlib_d64  mask_8000 = vis_to_double_dup(0x80008000);          \
  mlib_d64  mask_7fff = vis_to_double_dup(0x7FFF7FFF);          \
  mlib_d64  dx64, dy64, deltax, deltay, delta1_x, delta1_y;     \
  mlib_d64  s0, s1, s2, s3;                                     \
  mlib_d64  d0, d1, d2, d3, dd

/***************************************************************/

/* arguments (x, y) are swapped to prevent overflow */
#define FMUL_16x16(x, y)                        \
  vis_fpadd16(vis_fmul8sux16(y, x),             \
              vis_fmul8ulx16(y, x))

/***************************************************************/
#define BUF_SIZE  512

/***************************************************************/
#define DOUBLE_4U16(x0, x1, x2, x3)                                 \
  vis_to_double(((((x0) & 0xFFFE) << 15) | (((x1) & 0xFFFE) >> 1)), \
                ((((x2) & 0xFFFE) << 15) | (((x3) & 0xFFFE) >> 1)))

/***************************************************************/
#define BL_SUM()                                                \
  XOR_8000(s0);                                                 \
  XOR_8000(s1);                                                 \
  XOR_8000(s2);                                                 \
  XOR_8000(s3);                                                 \
                                                                \
  delta1_x = vis_fpsub16(mask_7fff, deltax);                    \
  delta1_y = vis_fpsub16(mask_7fff, deltay);                    \
                                                                \
  d0 = FMUL_16x16(s0, delta1_x);                                \
  d1 = FMUL_16x16(s1, deltax);                                  \
  d0 = vis_fpadd16(d0, d1);                                     \
  d0 = vis_fpadd16(d0, d0);                                     \
  d0 = FMUL_16x16(d0, delta1_y);                                \
                                                                \
  d2 = FMUL_16x16(s2, delta1_x);                                \
  d3 = FMUL_16x16(s3, deltax);                                  \
  d2 = vis_fpadd16(d2, d3);                                     \
  d2 = vis_fpadd16(d2, d2);                                     \
  d2 = FMUL_16x16(d2, deltay);                                  \
                                                                \
  dd = vis_fpadd16(d0, d2);                                     \
  dd = vis_fpadd16(dd, dd);                                     \
  XOR_8000(dd);                                                 \
                                                                \
  deltax = vis_fpadd16(deltax, dx64);                           \
  deltay = vis_fpadd16(deltay, dy64);                           \
  deltax = vis_fand(deltax, mask_7fff);                         \
  deltay = vis_fand(deltay, mask_7fff)

/***************************************************************/
#define BL_SUM_3CH()                                            \
  XOR_8000(s0);                                                 \
  XOR_8000(s1);                                                 \
  XOR_8000(s2);                                                 \
  XOR_8000(s3);                                                 \
                                                                \
  delta1_x = vis_fpsub16(mask_7fff, deltax);                    \
  delta1_y = vis_fpsub16(mask_7fff, deltay);                    \
                                                                \
  d0 = FMUL_16x16(s0, delta1_y);                                \
  d2 = FMUL_16x16(s2, deltay);                                  \
  d0 = vis_fpadd16(d0, d2);                                     \
  d0 = vis_fpadd16(d0, d0);                                     \
  d0 = FMUL_16x16(d0, delta1_x);                                \
                                                                \
  d1 = FMUL_16x16(s1, delta1_y);                                \
  d3 = FMUL_16x16(s3, deltay);                                  \
  d1 = vis_fpadd16(d1, d3);                                     \
  d1 = vis_fpadd16(d1, d1);                                     \
  d1 = FMUL_16x16(d1, deltax);                                  \
                                                                \
  vis_alignaddr((void*)0, 2);                                   \
  d0 = vis_faligndata(d0, d0);                                  \
  dd = vis_fpadd16(d0, d1);                                     \
  dd = vis_fpadd16(dd, dd);                                     \
  XOR_8000(dd);                                                 \
                                                                \
  deltax = vis_fpadd16(deltax, dx64);                           \
  deltay = vis_fpadd16(deltay, dy64);                           \
  deltax = vis_fand(deltax, mask_7fff);                         \
  deltay = vis_fand(deltay, mask_7fff)

/***************************************************************/
#define LD_U16(sp, ind) vis_ld_u16(sp + ind)

/***************************************************************/
#ifndef MLIB_VIS2

#define LOAD_1CH()                                              \
  s0 = vis_faligndata(LD_U16(sp3, 0), mask_7fff);               \
  s1 = vis_faligndata(LD_U16(sp3, 2), mask_7fff);               \
  s2 = vis_faligndata(LD_U16(sp3, srcYStride), mask_7fff);      \
  s3 = vis_faligndata(LD_U16(sp3, srcYStride + 2), mask_7fff);  \
                                                                \
  s0 = vis_faligndata(LD_U16(sp2, 0), s0);                      \
  s1 = vis_faligndata(LD_U16(sp2, 2), s1);                      \
  s2 = vis_faligndata(LD_U16(sp2, srcYStride), s2);             \
  s3 = vis_faligndata(LD_U16(sp2, srcYStride + 2), s3);         \
                                                                \
  s0 = vis_faligndata(LD_U16(sp1, 0), s0);                      \
  s1 = vis_faligndata(LD_U16(sp1, 2), s1);                      \
  s2 = vis_faligndata(LD_U16(sp1, srcYStride), s2);             \
  s3 = vis_faligndata(LD_U16(sp1, srcYStride + 2), s3);         \
                                                                \
  s0 = vis_faligndata(LD_U16(sp0, 0), s0);                      \
  s1 = vis_faligndata(LD_U16(sp0, 2), s1);                      \
  s2 = vis_faligndata(LD_U16(sp0, srcYStride), s2);             \
  s3 = vis_faligndata(LD_U16(sp0, srcYStride + 2), s3)

#else

#define LOAD_1CH()                                                             \
  s0 = vis_bshuffle(LD_U16(sp0, 0), LD_U16(sp2, 0));                           \
  s1 = vis_bshuffle(LD_U16(sp0, 2), LD_U16(sp2, 2));                           \
  s2 = vis_bshuffle(LD_U16(sp0, srcYStride), LD_U16(sp2, srcYStride));         \
  s3 = vis_bshuffle(LD_U16(sp0, srcYStride + 2), LD_U16(sp2, srcYStride + 2)); \
                                                                               \
  t0 = vis_bshuffle(LD_U16(sp1, 0), LD_U16(sp3, 0));                           \
  t1 = vis_bshuffle(LD_U16(sp1, 2), LD_U16(sp3, 2));                           \
  t2 = vis_bshuffle(LD_U16(sp1, srcYStride), LD_U16(sp3, srcYStride));         \
  t3 = vis_bshuffle(LD_U16(sp1, srcYStride + 2), LD_U16(sp3, srcYStride + 2)); \
                                                                               \
  s0 = vis_bshuffle(s0, t0);                                                   \
  s1 = vis_bshuffle(s1, t1);                                                   \
  s2 = vis_bshuffle(s2, t2);                                                   \
  s3 = vis_bshuffle(s3, t3)

#endif /* MLIB_VIS2 */

/***************************************************************/
#define GET_POINTER(sp)                                                       \
  sp = *(mlib_u8**)((mlib_u8*)lineAddr + PTR_SHIFT(Y)) + 2*(X >> MLIB_SHIFT); \
  X += dX;                                                                    \
  Y += dY

/***************************************************************/
#undef  PREPARE_DELTAS
#define PREPARE_DELTAS                                                             \
  if (warp_tbl != NULL) {                                                          \
    dX = warp_tbl[2*j    ];                                                        \
    dY = warp_tbl[2*j + 1];                                                        \
    dx64 = vis_to_double_dup((((dX << 1) & 0xFFFF) << 16) | ((dX << 1) & 0xFFFF)); \
    dy64 = vis_to_double_dup((((dY << 1) & 0xFFFF) << 16) | ((dY << 1) & 0xFFFF)); \
  }

/***************************************************************/
mlib_status FUN_NAME(1ch)(mlib_affine_param *param)
{
  DECLAREVAR();
  mlib_s32 off;
  mlib_s32 x0, x1, x2, x3, y0, y1, y2, y3;
#ifdef MLIB_VIS2
  mlib_d64 t0, t1, t2, t3;
  vis_write_bmask(0x45CD67EF, 0);
#else
  vis_alignaddr((void*)0, 6);
#endif /* MLIB_VIS2 */

  dx64 = vis_to_double_dup((((dX << 1) & 0xFFFF) << 16) | ((dX << 1) & 0xFFFF));
  dy64 = vis_to_double_dup((((dY << 1) & 0xFFFF) << 16) | ((dY << 1) & 0xFFFF));

  for (j = yStart; j <= yFinish; j++) {
    mlib_u8  *sp0, *sp1, *sp2, *sp3;
    mlib_d64 *dp, dmask;

    NEW_LINE(1);

    off = (mlib_s32)dl & 7;
    dp = (mlib_d64*)(dl - off);
    off >>= 1;

    x0 = X - off*dX; y0 = Y - off*dY;
    x1 = x0 + dX;    y1 = y0 + dY;
    x2 = x1 + dX;    y2 = y1 + dY;
    x3 = x2 + dX;    y3 = y2 + dY;

    deltax = DOUBLE_4U16(x0, x1, x2, x3);
    deltay = DOUBLE_4U16(y0, y1, y2, y3);

    if (off) {
      mlib_s32 emask = vis_edge16((void*)(2*off), (void*)(2*(off + size - 1)));

      off = 4 - off;
      GET_POINTER(sp3);
      sp0 = sp1 = sp2 = sp3;

      if (off > 1 && size > 1) {
        GET_POINTER(sp3);
      }

      if (off > 2) {
        sp2 = sp3;

        if (size > 2) {
          GET_POINTER(sp3);
        }
      }

      LOAD_1CH();
      BL_SUM();

      dmask = ((mlib_d64*)mlib_dmask_arr)[emask];
      *dp++ = vis_for (vis_fand(dmask, dd), vis_fandnot(dmask, dp[0]));

      size -= off;

      if (size < 0) size = 0;
    }

#pragma pipeloop(0)
    for (i = 0; i < size/4; i++) {
      GET_POINTER(sp0);
      GET_POINTER(sp1);
      GET_POINTER(sp2);
      GET_POINTER(sp3);

      LOAD_1CH();
      BL_SUM();

      dp[i] = dd;
    }

    off = size & 3;

    if (off) {
      GET_POINTER(sp0);
      sp1 = sp2 = sp3 = sp0;

      if (off > 1) {
        GET_POINTER(sp1);
      }

      if (off > 2) {
        GET_POINTER(sp2);
      }

      LOAD_1CH();
      BL_SUM();

      dmask = ((mlib_d64*)mlib_dmask_arr)[(0xF0 >> off) & 0x0F];
      dp[i] = vis_for (vis_fand(dmask, dd), vis_fandnot(dmask, dp[i]));
    }
  }

  return MLIB_SUCCESS;
}

/***************************************************************/
#undef  GET_POINTER
#define GET_POINTER(sp)                                                      \
  sp = *(mlib_f32**)((mlib_u8*)lineAddr + PTR_SHIFT(Y)) + (X >> MLIB_SHIFT); \
  X += dX;                                                                   \
  Y += dY

/***************************************************************/
#define LOAD_2CH()                                              \
  s0 = vis_freg_pair(sp0[0], sp1[0]);                           \
  s1 = vis_freg_pair(sp0[1], sp1[1]);                           \
  s2 = vis_freg_pair(sp0[srcYStride], sp1[srcYStride]);         \
  s3 = vis_freg_pair(sp0[srcYStride + 1], sp1[srcYStride + 1])

/***************************************************************/
#undef  PREPARE_DELTAS
#define PREPARE_DELTAS                                               \
  if (warp_tbl != NULL) {                                            \
    dX = warp_tbl[2*j    ];                                          \
    dY = warp_tbl[2*j + 1];                                          \
    dx64 = vis_to_double_dup(((dX & 0xFFFF) << 16) | (dX & 0xFFFF)); \
    dy64 = vis_to_double_dup(((dY & 0xFFFF) << 16) | (dY & 0xFFFF)); \
  }

/***************************************************************/
mlib_status FUN_NAME(2ch)(mlib_affine_param *param)
{
  DECLAREVAR();
  mlib_s32 off;
  mlib_s32 x0, x1, y0, y1;

  if (((mlib_s32)lineAddr[0] | (mlib_s32)dstData | srcYStride | dstYStride) & 3) {
    return FUN_NAME(2ch_na)(param);
  }

  srcYStride >>= 2;

  dx64 = vis_to_double_dup(((dX & 0xFFFF) << 16) | (dX & 0xFFFF));
  dy64 = vis_to_double_dup(((dY & 0xFFFF) << 16) | (dY & 0xFFFF));

  for (j = yStart; j <= yFinish; j++) {
    mlib_f32 *sp0, *sp1;
    mlib_d64 *dp;

    NEW_LINE(2);

    off = (mlib_s32)dl & 7;
    dp = (mlib_d64*)(dl - off);

    if (off) {
      x0 = X - dX; y0 = Y - dY;
      x1 = X;      y1 = Y;
    } else {
      x0 = X;      y0 = Y;
      x1 = X + dX; y1 = Y + dY;
    }

    deltax = DOUBLE_4U16(x0, x0, x1, x1);
    deltay = DOUBLE_4U16(y0, y0, y1, y1);

    if (off) {
      GET_POINTER(sp1);
      sp0 = sp1;
      LOAD_2CH();

      BL_SUM();

      ((mlib_f32*)dp)[1] = vis_read_lo(dd);
      dp++;
      size--;
    }

#pragma pipeloop(0)
    for (i = 0; i < size/2; i++) {
      GET_POINTER(sp0);
      GET_POINTER(sp1);
      LOAD_2CH();

      BL_SUM();

      *dp++ = dd;
    }

    if (size & 1) {
      GET_POINTER(sp0);
      sp1 = sp0;
      LOAD_2CH();

      BL_SUM();

      ((mlib_f32*)dp)[0] = vis_read_hi(dd);
    }
  }

  return MLIB_SUCCESS;
}

/***************************************************************/
#undef  GET_POINTER
#define GET_POINTER(sp)                                                       \
  sp = *(mlib_u8**)((mlib_u8*)lineAddr + PTR_SHIFT(Y)) + 4*(X >> MLIB_SHIFT); \
  X += dX;                                                                    \
  Y += dY

/***************************************************************/
#ifndef MLIB_VIS2

#define LOAD_2CH_NA()                                           \
  s0 = vis_faligndata(LD_U16(sp1, 2), mask_7fff);               \
  s1 = vis_faligndata(LD_U16(sp1, 6), mask_7fff);               \
  s2 = vis_faligndata(LD_U16(sp1, srcYStride + 2), mask_7fff);  \
  s3 = vis_faligndata(LD_U16(sp1, srcYStride + 6), mask_7fff);  \
                                                                \
  s0 = vis_faligndata(LD_U16(sp1, 0), s0);                      \
  s1 = vis_faligndata(LD_U16(sp1, 4), s1);                      \
  s2 = vis_faligndata(LD_U16(sp1, srcYStride), s2);             \
  s3 = vis_faligndata(LD_U16(sp1, srcYStride + 4), s3);         \
                                                                \
  s0 = vis_faligndata(LD_U16(sp0, 2), s0);                      \
  s1 = vis_faligndata(LD_U16(sp0, 6), s1);                      \
  s2 = vis_faligndata(LD_U16(sp0, srcYStride + 2), s2);         \
  s3 = vis_faligndata(LD_U16(sp0, srcYStride + 6), s3);         \
                                                                \
  s0 = vis_faligndata(LD_U16(sp0, 0), s0);                      \
  s1 = vis_faligndata(LD_U16(sp0, 4), s1);                      \
  s2 = vis_faligndata(LD_U16(sp0, srcYStride), s2);             \
  s3 = vis_faligndata(LD_U16(sp0, srcYStride + 4), s3)

#else

#define LOAD_2CH_NA()                                                          \
  s0 = vis_bshuffle(LD_U16(sp0, 0), LD_U16(sp1, 0));                           \
  s1 = vis_bshuffle(LD_U16(sp0, 4), LD_U16(sp1, 4));                           \
  s2 = vis_bshuffle(LD_U16(sp0, srcYStride), LD_U16(sp1, srcYStride));         \
  s3 = vis_bshuffle(LD_U16(sp0, srcYStride + 4), LD_U16(sp1, srcYStride + 4)); \
                                                                               \
  t0 = vis_bshuffle(LD_U16(sp0, 2), LD_U16(sp1, 2));                           \
  t1 = vis_bshuffle(LD_U16(sp0, 6), LD_U16(sp1, 6));                           \
  t2 = vis_bshuffle(LD_U16(sp0, srcYStride + 2), LD_U16(sp1, srcYStride + 2)); \
  t3 = vis_bshuffle(LD_U16(sp0, srcYStride + 6), LD_U16(sp1, srcYStride + 6)); \
                                                                               \
  s0 = vis_bshuffle(s0, t0);                                                   \
  s1 = vis_bshuffle(s1, t1);                                                   \
  s2 = vis_bshuffle(s2, t2);                                                   \
  s3 = vis_bshuffle(s3, t3)

#endif /* MLIB_VIS2 */

/***************************************************************/
mlib_status FUN_NAME(2ch_na)(mlib_affine_param *param)
{
  DECLAREVAR();
  mlib_s32 max_xsize = param -> max_xsize, bsize;
  mlib_s32 x0, x1, y0, y1;
  mlib_d64 buff[BUF_SIZE], *pbuff = buff;
#ifdef MLIB_VIS2
  mlib_d64 t0, t1, t2, t3;
#endif /* MLIB_VIS2 */

  bsize = (max_xsize + 1)/2;

  if (bsize > BUF_SIZE) {
    pbuff = mlib_malloc(bsize*sizeof(mlib_d64));

    if (pbuff == NULL) return MLIB_FAILURE;
  }

  MLIB_WRITE_BMASK(0x45CD67EF);

  dx64 = vis_to_double_dup(((dX & 0xFFFF) << 16) | (dX & 0xFFFF));
  dy64 = vis_to_double_dup(((dY & 0xFFFF) << 16) | (dY & 0xFFFF));

  for (j = yStart; j <= yFinish; j++) {
    mlib_u8 *sp0, *sp1;

#ifndef MLIB_VIS2
    vis_alignaddr((void*)0, 6);
#endif /* MLIB_VIS2 */

    NEW_LINE(2);

    x0 = X;      y0 = Y;
    x1 = X + dX; y1 = Y + dY;

    deltax = DOUBLE_4U16(x0, x0, x1, x1);
    deltay = DOUBLE_4U16(y0, y0, y1, y1);

#pragma pipeloop(0)
    for (i = 0; i < size/2; i++) {
      GET_POINTER(sp0);
      GET_POINTER(sp1);
      LOAD_2CH_NA();

      BL_SUM();

      pbuff[i] = dd;
    }

    if (size & 1) {
      GET_POINTER(sp0);
      sp1 = sp0;
      LOAD_2CH_NA();

      BL_SUM();

      pbuff[i] = dd;
    }

    mlib_ImageCopy_na((mlib_u8*)pbuff, dl, 4*size);
  }

  if (pbuff != buff) {
    mlib_free(pbuff);
  }

  return MLIB_SUCCESS;
}

/***************************************************************/
#undef  PREPARE_DELTAS
#define PREPARE_DELTAS                                                             \
  if (warp_tbl != NULL) {                                                          \
    dX = warp_tbl[2*j    ];                                                        \
    dY = warp_tbl[2*j + 1];                                                        \
    dX = (dX - (dX >> 31)) &~ 1; /* rounding towards ZERO */                       \
    dY = (dY - (dY >> 31)) &~ 1; /* rounding towards ZERO */                       \
    dx64 = vis_to_double_dup((((dX >> 1) & 0xFFFF) << 16) | ((dX >> 1) & 0xFFFF)); \
    dy64 = vis_to_double_dup((((dY >> 1) & 0xFFFF) << 16) | ((dY >> 1) & 0xFFFF)); \
  }

/***************************************************************/
mlib_status FUN_NAME(3ch)(mlib_affine_param *param)
{
  DECLAREVAR();
  mlib_s32 max_xsize = param -> max_xsize;
  mlib_d64 buff[BUF_SIZE], *pbuff = buff;

  if (max_xsize > BUF_SIZE) {
    pbuff = mlib_malloc(max_xsize*sizeof(mlib_d64));

    if (pbuff == NULL) return MLIB_FAILURE;
  }

  dX = (dX - (dX >> 31)) &~ 1; /* rounding towards ZERO */
  dY = (dY - (dY >> 31)) &~ 1; /* rounding towards ZERO */
  dx64 = vis_to_double_dup((((dX >> 1) & 0xFFFF) << 16) | ((dX >> 1) & 0xFFFF));
  dy64 = vis_to_double_dup((((dY >> 1) & 0xFFFF) << 16) | ((dY >> 1) & 0xFFFF));

  for (j = yStart; j <= yFinish; j++) {
    mlib_u8  *sp;
    mlib_d64 *sp0, *sp1;

    NEW_LINE(3);

    deltax = DOUBLE_4U16(X, X, X, X);
    deltay = DOUBLE_4U16(Y, Y, Y, Y);

#pragma pipeloop(0)
    for (i = 0; i < size; i++) {
      sp = *(mlib_u8**)((mlib_u8*)lineAddr + PTR_SHIFT(Y)) + 6*(X >> MLIB_SHIFT) - 2;

      vis_alignaddr(sp, 0);
      sp0 = AL_ADDR(sp, 0);
      s0 = vis_faligndata(sp0[0], sp0[1]);
      s1 = vis_faligndata(sp0[1], sp0[2]);

      vis_alignaddr(sp, srcYStride);
      sp1 = AL_ADDR(sp, srcYStride);
      s2 = vis_faligndata(sp1[0], sp1[1]);
      s3 = vis_faligndata(sp1[1], sp1[2]);

      BL_SUM_3CH();

      pbuff[i] = dd;
      X += dX;
      Y += dY;
    }

    mlib_v_ImageChannelExtract_S16_43L_D1((void *)pbuff, (void *)dl, size);
  }

  if (pbuff != buff) {
    mlib_free(pbuff);
  }

  return MLIB_SUCCESS;
}

/***************************************************************/
mlib_status FUN_NAME(4ch)(mlib_affine_param *param)
{
  DECLAREVAR();

  if (((mlib_s32)lineAddr[0] | (mlib_s32)dstData | srcYStride | dstYStride) & 7) {
    return FUN_NAME(4ch_na)(param);
  }

  srcYStride >>= 3;

  dX = (dX - (dX >> 31)) &~ 1; /* rounding towards ZERO */
  dY = (dY - (dY >> 31)) &~ 1; /* rounding towards ZERO */
  dx64 = vis_to_double_dup((((dX >> 1) & 0xFFFF) << 16) | ((dX >> 1) & 0xFFFF));
  dy64 = vis_to_double_dup((((dY >> 1) & 0xFFFF) << 16) | ((dY >> 1) & 0xFFFF));

  for (j = yStart; j <= yFinish; j++) {
    mlib_d64 *sp;

    NEW_LINE(4);

    deltax = DOUBLE_4U16(X, X, X, X);
    deltay = DOUBLE_4U16(Y, Y, Y, Y);

#pragma pipeloop(0)
    for (i = 0; i < size; i++) {
      sp = *(mlib_d64**)((mlib_u8*)lineAddr + PTR_SHIFT(Y)) + (X >> MLIB_SHIFT);
      s0 = sp[0];
      s1 = sp[1];
      s2 = sp[srcYStride];
      s3 = sp[srcYStride + 1];

      BL_SUM();

      ((mlib_d64*)dl)[i] = dd;
      X += dX;
      Y += dY;
    }
  }

  return MLIB_SUCCESS;
}

/***************************************************************/
mlib_status FUN_NAME(4ch_na)(mlib_affine_param *param)
{
  DECLAREVAR();
  mlib_s32 max_xsize = param -> max_xsize;
  mlib_d64 buff[BUF_SIZE], *pbuff = buff;

  if (max_xsize > BUF_SIZE) {
    pbuff = mlib_malloc(max_xsize*sizeof(mlib_d64));

    if (pbuff == NULL) return MLIB_FAILURE;
  }

  dX = (dX - (dX >> 31)) &~ 1; /* rounding towards ZERO */
  dY = (dY - (dY >> 31)) &~ 1; /* rounding towards ZERO */
  dx64 = vis_to_double_dup((((dX >> 1) & 0xFFFF) << 16) | ((dX >> 1) & 0xFFFF));
  dy64 = vis_to_double_dup((((dY >> 1) & 0xFFFF) << 16) | ((dY >> 1) & 0xFFFF));

  for (j = yStart; j <= yFinish; j++) {
    mlib_u8  *sp;
    mlib_d64 *sp0, *sp1;

    NEW_LINE(4);

    deltax = DOUBLE_4U16(X, X, X, X);
    deltay = DOUBLE_4U16(Y, Y, Y, Y);

#pragma pipeloop(0)
    for (i = 0; i < size; i++) {
      sp = *(mlib_u8**)((mlib_u8*)lineAddr + PTR_SHIFT(Y)) + 8*(X >> MLIB_SHIFT);

      vis_alignaddr(sp, 0);
      sp0 = AL_ADDR(sp, 0);
      s0 = vis_faligndata(sp0[0], sp0[1]);
      s1 = vis_faligndata(sp0[1], sp0[2]);

      vis_alignaddr(sp, srcYStride);
      sp1 = AL_ADDR(sp, srcYStride);
      s2 = vis_faligndata(sp1[0], sp1[1]);
      s3 = vis_faligndata(sp1[1], sp1[2]);

      BL_SUM();

      pbuff[i] = dd;
      X += dX;
      Y += dY;
    }

    mlib_ImageCopy_na((mlib_u8*)pbuff, dl, 8*size);
  }

  if (pbuff != buff) {
    mlib_free(pbuff);
  }

  return MLIB_SUCCESS;
}

/***************************************************************/
