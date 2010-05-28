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
 * FUNCTIONS
 *      mlib_v_ImageChannelInsert_U8
 *      mlib_v_ImageChannelInsert_U8_12_A8D1X8
 *      mlib_v_ImageChannelInsert_U8_12_A8D2X8
 *      mlib_v_ImageChannelInsert_U8_12_D1
 *      mlib_v_ImageChannelInsert_U8_12
 *      mlib_v_ImageChannelInsert_U8_13_A8D1X8
 *      mlib_v_ImageChannelInsert_U8_13_A8D2X8
 *      mlib_v_ImageChannelInsert_U8_13_D1
 *      mlib_v_ImageChannelInsert_U8_13
 *      mlib_v_ImageChannelInsert_U8_14_A8D1X8
 *      mlib_v_ImageChannelInsert_U8_14_A8D2X8
 *      mlib_v_ImageChannelInsert_U8_14_D1
 *      mlib_v_ImageChannelInsert_U8_14
 *      mlib_v_ImageChannelInsert_S16
 *      mlib_v_ImageChannelInsert_S16_12_A8D1X4
 *      mlib_v_ImageChannelInsert_S16_12_A8D2X4
 *      mlib_v_ImageChannelInsert_S16_12_D1
 *      mlib_v_ImageChannelInsert_S16_12
 *      mlib_v_ImageChannelInsert_S16_13_A8D1X4
 *      mlib_v_ImageChannelInsert_S16_13_A8D2X4
 *      mlib_v_ImageChannelInsert_S16_13_D1
 *      mlib_v_ImageChannelInsert_S16_13
 *      mlib_v_ImageChannelInsert_S16_14_A8D1X4
 *      mlib_v_ImageChannelInsert_S16_14_A8D2X4
 *      mlib_v_ImageChannelInsert_S16_14_D1
 *      mlib_v_ImageChannelInsert_S16_14
 *      mlib_v_ImageChannelInsert_S32
 *      mlib_v_ImageChannelInsert_D64
 *
 * ARGUMENT
 *      src     pointer to source image data
 *      dst     pointer to destination image data
 *      slb     source image line stride in bytes
 *      dlb     destination image line stride in bytes
 *      dsize   image data size in pixels
 *      xsize   image width in pixels
 *      ysize   image height in lines
 *      cmask   channel mask
 *
 * DESCRIPTION
 *      Copy the 1-channel source image into the selected channel
 *      of the destination image -- VIS version low level functions.
 *
 * NOTE
 *      These functions are separated from mlib_v_ImageChannelInsert.c
 *      for loop unrolling and structure clarity.
 */

#include "vis_proto.h"
#include "mlib_image.h"
#include "mlib_v_ImageChannelInsert.h"

/***************************************************************/
/* general channel insertion: slower due to the inner loop */
void mlib_v_ImageChannelInsert_U8(const mlib_u8 *src,
                                  mlib_s32      slb,
                                  mlib_u8       *dst,
                                  mlib_s32      dlb,
                                  mlib_s32      channels,
                                  mlib_s32      channeld,
                                  mlib_s32      width,
                                  mlib_s32      height,
                                  mlib_s32      cmask)
{
  mlib_u8 *sp;                                        /* pointer for pixel in src */
  mlib_u8 *sl;                                        /* pointer for line in src */
  mlib_u8 *dp;                                        /* pointer for pixel in dst */
  mlib_u8 *dl;                                        /* pointer for line in dst */
  mlib_s32 i, j, k;                                   /* indices for x, y, channel */
  mlib_s32 deltac[5] = { 0, 1, 1, 1, 1 };
  mlib_s32 inc0, inc1, inc2;
  mlib_u8 s0, s1, s2;

  deltac[channels] = 1;
  for (i = (channeld - 1), k = 0; i >= 0; i--) {
    if ((cmask & (1 << i)) == 0)
      deltac[k]++;
    else
      k++;
  }

  deltac[channels] = channeld;
  for (i = 1; i < channels; i++) {
    deltac[channels] -= deltac[i];
  }

  sp = sl = (void *)src;
  dp = dl = dst + deltac[0];

  if (channels == 2) {
    inc0 = deltac[1];
    inc1 = deltac[2] + inc0;
    for (j = 0; j < height; j++) {
#pragma pipeloop(0)
      for (i = 0; i < width; i++) {
        s0 = sp[0];
        s1 = sp[1];
        dp[0] = s0;
        dp[inc0] = s1;
        dp += inc1;
        sp += 2;
      }

      sp = sl += slb;
      dp = dl += dlb;
    }
  }
  else if (channels == 3) {
    inc0 = deltac[1];
    inc1 = deltac[2] + inc0;
    inc2 = deltac[3] + inc1;
    for (j = 0; j < height; j++) {
#pragma pipeloop(0)
      for (i = 0; i < width; i++) {
        s0 = sp[0];
        s1 = sp[1];
        s2 = sp[2];
        dp[0] = s0;
        dp[inc0] = s1;
        dp[inc1] = s2;
        dp += inc2;
        sp += 3;
      }

      sp = sl += slb;
      dp = dl += dlb;
    }
  }
}

/***************************************************************/
/* general channel insertion: slower due to the inner loop */
void mlib_v_ImageChannelInsert_D64(const mlib_d64 *src,
                                   mlib_s32       slb,
                                   mlib_d64       *dst,
                                   mlib_s32       dlb,
                                   mlib_s32       channels,
                                   mlib_s32       channeld,
                                   mlib_s32       width,
                                   mlib_s32       height,
                                   mlib_s32       cmask)
{
  mlib_d64 *sp;                                       /* pointer for pixel in src */
  mlib_d64 *sl;                                       /* pointer for line in src */
  mlib_d64 *dp;                                       /* pointer for pixel in dst */
  mlib_d64 *dl;                                       /* pointer for line in dst */
  mlib_s32 i, j, k;                                   /* indices for x, y, channel */
  mlib_s32 deltac[5] = { 0, 1, 1, 1, 1 };
  mlib_s32 inc0, inc1, inc2;
  mlib_d64 s0, s1, s2;

  deltac[channels] = 1;
  for (i = (channeld - 1), k = 0; i >= 0; i--) {
    if ((cmask & (1 << i)) == 0)
      deltac[k]++;
    else
      k++;
  }

  deltac[channels] = channeld;
  for (i = 1; i < channels; i++) {
    deltac[channels] -= deltac[i];
  }

  sp = sl = (void *)src;
  dp = dl = dst + deltac[0];

  if (channels == 1) {
    for (j = 0; j < height; j++) {
#pragma pipeloop(0)
      for (i = 0; i < width; i++) {
        s0 = sp[0];
        dp[0] = s0;
        dp += channeld;
        sp++;
      }

      sp = sl = (mlib_d64 *) ((mlib_u8 *) sl + slb);
      dp = dl = (mlib_d64 *) ((mlib_u8 *) dl + dlb);
    }
  }
  else if (channels == 2) {
    inc0 = deltac[1];
    inc1 = deltac[2] + inc0;
    for (j = 0; j < height; j++) {
#pragma pipeloop(0)
      for (i = 0; i < width; i++) {
        s0 = sp[0];
        s1 = sp[1];
        dp[0] = s0;
        dp[inc0] = s1;
        dp += inc1;
        sp += 2;
      }

      sp = sl = (mlib_d64 *) ((mlib_u8 *) sl + slb);
      dp = dl = (mlib_d64 *) ((mlib_u8 *) dl + dlb);
    }
  }
  else if (channels == 3) {
    inc0 = deltac[1];
    inc1 = deltac[2] + inc0;
    inc2 = deltac[3] + inc1;
    for (j = 0; j < height; j++) {
#pragma pipeloop(0)
      for (i = 0; i < width; i++) {
        s0 = sp[0];
        s1 = sp[1];
        s2 = sp[2];
        dp[0] = s0;
        dp[inc0] = s1;
        dp[inc1] = s2;
        dp += inc2;
        sp += 3;
      }

      sp = sl = (mlib_d64 *) ((mlib_u8 *) sl + slb);
      dp = dl = (mlib_d64 *) ((mlib_u8 *) dl + dlb);
    }
  }
}

/***************************************************************/
/* general channel insertion: slower due to the inner loop */
void mlib_v_ImageChannelInsert_S16(const mlib_s16 *src,
                                   mlib_s32       slb,
                                   mlib_s16       *dst,
                                   mlib_s32       dlb,
                                   mlib_s32       channels,
                                   mlib_s32       channeld,
                                   mlib_s32       width,
                                   mlib_s32       height,
                                   mlib_s32       cmask)
{
  mlib_s16 *sp;                                       /* pointer for pixel in src */
  mlib_s16 *sl;                                       /* pointer for line in src */
  mlib_s16 *dp;                                       /* pointer for pixel in dst */
  mlib_s16 *dl;                                       /* pointer for line in dst */
  mlib_s32 i, j, k;                                   /* indices for x, y, channel */
  mlib_s32 deltac[5] = { 0, 1, 1, 1, 1 };
  mlib_s32 inc0, inc1, inc2;
  mlib_s16 s0, s1, s2;

  deltac[channels] = 1;
  for (i = (channeld - 1), k = 0; i >= 0; i--) {
    if ((cmask & (1 << i)) == 0)
      deltac[k]++;
    else
      k++;
  }

  deltac[channels] = channeld;
  for (i = 1; i < channels; i++) {
    deltac[channels] -= deltac[i];
  }

  sp = sl = (void *)src;
  dp = dl = dst + deltac[0];

  if (channels == 2) {
    inc0 = deltac[1];
    inc1 = deltac[2] + inc0;
    for (j = 0; j < height; j++) {
#pragma pipeloop(0)
      for (i = 0; i < width; i++) {
        s0 = sp[0];
        s1 = sp[1];
        dp[0] = s0;
        dp[inc0] = s1;
        dp += inc1;
        sp += 2;
      }

      sp = sl = (mlib_s16 *) ((mlib_u8 *) sl + slb);
      dp = dl = (mlib_s16 *) ((mlib_u8 *) dl + dlb);
    }
  }
  else if (channels == 3) {
    inc0 = deltac[1];
    inc1 = deltac[2] + inc0;
    inc2 = deltac[3] + inc1;
    for (j = 0; j < height; j++) {
#pragma pipeloop(0)
      for (i = 0; i < width; i++) {
        s0 = sp[0];
        s1 = sp[1];
        s2 = sp[2];
        dp[0] = s0;
        dp[inc0] = s1;
        dp[inc1] = s2;
        dp += inc2;
        sp += 3;
      }

      sp = sl = (mlib_s16 *) ((mlib_u8 *) sl + slb);
      dp = dl = (mlib_s16 *) ((mlib_u8 *) dl + dlb);
    }
  }
}

/***************************************************************/
/* general channel insertion: slower due to the inner loop */

void mlib_v_ImageChannelInsert_S32(const mlib_s32 *src,
                                   mlib_s32       slb,
                                   mlib_s32       *dst,
                                   mlib_s32       dlb,
                                   mlib_s32       channels,
                                   mlib_s32       channeld,
                                   mlib_s32       width,
                                   mlib_s32       height,
                                   mlib_s32       cmask)
{
  mlib_s32 *sp;                                       /* pointer for pixel in src */
  mlib_s32 *sl;                                       /* pointer for line in src */
  mlib_s32 *dp;                                       /* pointer for pixel in dst */
  mlib_s32 *dl;                                       /* pointer for line in dst */
  mlib_s32 i, j, k;                                   /* indices for x, y, channel */
  mlib_s32 deltac[5] = { 0, 1, 1, 1, 1 };
  mlib_s32 inc0, inc1, inc2;
  mlib_s32 s0, s1, s2;

  deltac[channels] = 1;
  for (i = (channeld - 1), k = 0; i >= 0; i--) {
    if ((cmask & (1 << i)) == 0)
      deltac[k]++;
    else
      k++;
  }

  deltac[channels] = channeld;
  for (i = 1; i < channels; i++) {
    deltac[channels] -= deltac[i];
  }

  sp = sl = (void *)src;
  dp = dl = dst + deltac[0];

  if (channels == 1) {
    for (j = 0; j < height; j++) {
#pragma pipeloop(0)
      for (i = 0; i < width; i++) {
        s0 = sp[0];
        dp[0] = s0;
        dp += channeld;
        sp++;
      }

      sp = sl = (mlib_s32 *) ((mlib_u8 *) sl + slb);
      dp = dl = (mlib_s32 *) ((mlib_u8 *) dl + dlb);
    }
  }
  else if (channels == 2) {
    inc0 = deltac[1];
    inc1 = deltac[2] + inc0;
    for (j = 0; j < height; j++) {
#pragma pipeloop(0)
      for (i = 0; i < width; i++) {
        s0 = sp[0];
        s1 = sp[1];
        dp[0] = s0;
        dp[inc0] = s1;
        dp += inc1;
        sp += 2;
      }

      sp = sl = (mlib_s32 *) ((mlib_u8 *) sl + slb);
      dp = dl = (mlib_s32 *) ((mlib_u8 *) dl + dlb);
    }
  }
  else if (channels == 3) {
    inc0 = deltac[1];
    inc1 = deltac[2] + inc0;
    inc2 = deltac[3] + inc1;
    for (j = 0; j < height; j++) {
#pragma pipeloop(0)
      for (i = 0; i < width; i++) {
        s0 = sp[0];
        s1 = sp[1];
        s2 = sp[2];
        dp[0] = s0;
        dp[inc0] = s1;
        dp[inc1] = s2;
        dp += inc2;
        sp += 3;
      }

      sp = sl = (mlib_s32 *) ((mlib_u8 *) sl + slb);
      dp = dl = (mlib_s32 *) ((mlib_u8 *) dl + dlb);
    }
  }
}

/***************************************************************/
#define INSERT_U8_12(sd0, dd0, dd1)     /* channel duplicate */ \
  dd0 = vis_fpmerge(vis_read_hi(sd0), vis_read_hi(sd0));        \
  dd1 = vis_fpmerge(vis_read_lo(sd0), vis_read_lo(sd0))

/***************************************************************/
/* insert one channel to a 2-channel image.
 * both source and destination image data are 8-byte aligned.
 * dsize is multiple of 8.
 */

void mlib_v_ImageChannelInsert_U8_12_A8D1X8(const mlib_u8 *src,
                                            mlib_u8       *dst,
                                            mlib_s32      dsize,
                                            mlib_s32      cmask)
{
  mlib_d64 *sp, *dp;
  mlib_d64 sd0;
  mlib_d64 dd0, dd1;
  mlib_s32 bmask;
  mlib_s32 i;

  bmask = cmask | (cmask << 2) | (cmask << 4) | (cmask << 6);

  sp = (mlib_d64 *) src;
  dp = (mlib_d64 *) dst;

#pragma pipeloop(0)
  for (i = 0; i < dsize / 8; i++) {
    sd0 = *sp++;
    INSERT_U8_12(sd0, dd0, dd1);
    vis_pst_8(dd0, dp++, bmask);
    vis_pst_8(dd1, dp++, bmask);
  }
}

/***************************************************************/
/* insert one channel to a 2-channel image.
 * both source and destination image data are 8-byte aligned.
 * xsize is multiple of 8.
 */

void mlib_v_ImageChannelInsert_U8_12_A8D2X8(const mlib_u8 *src,
                                            mlib_s32      slb,
                                            mlib_u8       *dst,
                                            mlib_s32      dlb,
                                            mlib_s32      xsize,
                                            mlib_s32      ysize,
                                            mlib_s32      cmask)
{
  mlib_d64 *sp, *dp;
  mlib_d64 *sl, *dl;
  mlib_d64 sd0;
  mlib_d64 dd0, dd1;
  mlib_s32 bmask;
  mlib_s32 i, j;

  bmask = cmask | (cmask << 2) | (cmask << 4) | (cmask << 6);

  sp = sl = (mlib_d64 *) src;
  dp = dl = (mlib_d64 *) dst;

  for (j = 0; j < ysize; j++) {
#pragma pipeloop(0)
    for (i = 0; i < xsize / 8; i++) {
      sd0 = *sp++;
      INSERT_U8_12(sd0, dd0, dd1);
      vis_pst_8(dd0, dp++, bmask);
      vis_pst_8(dd1, dp++, bmask);
    }

    sp = sl = (mlib_d64 *) ((mlib_u8 *) sl + slb);
    dp = dl = (mlib_d64 *) ((mlib_u8 *) dl + dlb);
  }
}

/***************************************************************/
/* insert one channel to a 2-channel image.
 */

void mlib_v_ImageChannelInsert_U8_12_D1(const mlib_u8 *src,
                                        mlib_u8       *dst,
                                        mlib_s32      dsize,
                                        mlib_s32      cmask)
{
  mlib_u8 *sa, *da;
  mlib_u8 *dend, *dend2;                              /* end points in dst */
  mlib_d64 *dp;                                       /* 8-byte aligned start points in dst */
  mlib_d64 *sp;                                       /* 8-byte aligned start point in src */
  mlib_d64 sd0, sd1;                                  /* 8-byte source data */
  mlib_d64 dd0, dd1, dd2, dd3;                        /* 8-byte destination data */
  mlib_s32 soff;                                      /* offset of address in src */
  mlib_s32 doff;                                      /* offset of address in dst */
  mlib_s32 off;                                       /* offset of src over dst */
  mlib_s32 emask;                                     /* edge mask */
  mlib_s32 bmask;                                     /* channel mask */
  mlib_s32 i, n;

  bmask = cmask | (cmask << 2) | (cmask << 4) | (cmask << 6);

  sa = (void *)src;
  da = dst;

  /* prepare the source address */
  sp = (mlib_d64 *) ((mlib_addr) sa & (~7));
  soff = ((mlib_addr) sa & 7);

  /* prepare the destination addresses */
  dp = (mlib_d64 *) ((mlib_addr) da & (~7));
  doff = ((mlib_addr) da & 7);
  dend = da + dsize * 2 - 1;
  dend2 = dend - 15;

  /* calculate the src's offset over dst */
  off = soff * 2 - doff;

  if (doff % 2 != 0) {
    bmask = (~bmask) & 0xff;
  }

  if (off == 0) {                           /* src and dst have same alignment */

    /* load 8 bytes */
    sd0 = *sp++;

    /* insert, including some garbage at the start point */
    INSERT_U8_12(sd0, dd0, dd1);

    /* store 16 bytes result */
    emask = vis_edge8(da, dend);
    vis_pst_8(dd0, dp++, emask & bmask);
    if ((mlib_addr) dp <= (mlib_addr) dend) {
      emask = vis_edge8(dp, dend);
      vis_pst_8(dd1, dp++, emask & bmask);
    }

    if ((mlib_addr) dp <= (mlib_addr) dend2) {
      n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 16 + 1;

      /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
      for (i = 0; i < n; i++) {
        sd0 = *sp++;
        INSERT_U8_12(sd0, dd0, dd1);
        vis_pst_8(dd0, dp++, bmask);
        vis_pst_8(dd1, dp++, bmask);
      }
    }

    /* end point handling */
    if ((mlib_addr) dp <= (mlib_addr) dend) {
      sd0 = *sp++;
      INSERT_U8_12(sd0, dd0, dd1);
      emask = vis_edge8(dp, dend);
      vis_pst_8(dd0, dp++, emask & bmask);
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge8(dp, dend);
        vis_pst_8(dd1, dp++, emask & bmask);
      }
    }
  }
  else if (off < 0) {
    vis_alignaddr((void *)0, off);

    /* generate edge mask for the start point */
    emask = vis_edge8(da, dend);

    /* load 8 bytes */
    sd0 = *sp++;

    /* insert and store 16 bytes */
    INSERT_U8_12(sd0, dd0, dd1);
    vis_pst_8(vis_faligndata(dd0, dd0), dp++, emask & bmask);
    if ((mlib_addr) dp <= (mlib_addr) dend) {
      emask = vis_edge8(dp, dend);
      vis_pst_8(vis_faligndata(dd0, dd1), dp++, emask & bmask);
    }

    if ((mlib_addr) dp <= (mlib_addr) dend2) {
      n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 16 + 1;

      /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
      for (i = 0; i < n; i++) {
        dd2 = dd1;
        sd0 = *sp++;
        INSERT_U8_12(sd0, dd0, dd1);
        vis_pst_8(vis_faligndata(dd2, dd0), dp++, bmask);
        vis_pst_8(vis_faligndata(dd0, dd1), dp++, bmask);
      }
    }

    /* end point handling */
    if ((mlib_addr) dp <= (mlib_addr) dend) {
      emask = vis_edge8(dp, dend);
      dd2 = dd1;
      sd0 = *sp++;
      INSERT_U8_12(sd0, dd0, dd1);
      vis_pst_8(vis_faligndata(dd2, dd0), dp++, emask & bmask);
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge8(dp, dend);
        vis_pst_8(vis_faligndata(dd0, dd1), dp++, emask & bmask);
      }
    }
  }
  else if (off < 8) {
    vis_alignaddr((void *)0, off);

    /* generate edge mask for the start point */
    emask = vis_edge8(da, dend);

    /* load 16 bytes */
    sd0 = *sp++;
    sd1 = *sp++;

    /* insert and store 16 bytes */
    INSERT_U8_12(sd0, dd0, dd1);
    INSERT_U8_12(sd1, dd2, dd3);
    vis_pst_8(vis_faligndata(dd0, dd1), dp++, emask & bmask);
    if ((mlib_addr) dp <= (mlib_addr) dend) {
      emask = vis_edge8(dp, dend);
      vis_pst_8(vis_faligndata(dd1, dd2), dp++, emask & bmask);
    }

    if ((mlib_addr) dp <= (mlib_addr) dend2) {
      n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 16 + 1;

      /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
      for (i = 0; i < n; i++) {
        dd0 = dd2;
        dd1 = dd3;
        sd1 = *sp++;
        INSERT_U8_12(sd1, dd2, dd3);
        vis_pst_8(vis_faligndata(dd0, dd1), dp++, bmask);
        vis_pst_8(vis_faligndata(dd1, dd2), dp++, bmask);
      }
    }

    /* end point handling */
    if ((mlib_addr) dp <= (mlib_addr) dend) {
      emask = vis_edge8(dp, dend);
      dd0 = dd2;
      dd1 = dd3;
      sd1 = *sp++;
      INSERT_U8_12(sd1, dd2, dd3);
      vis_pst_8(vis_faligndata(dd0, dd1), dp++, emask & bmask);
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge8(dp, dend);
        vis_pst_8(vis_faligndata(dd1, dd2), dp++, emask & bmask);
      }
    }
  }
  else {                                    /* (off >= 8) */
    vis_alignaddr((void *)0, off);

    /* generate edge mask for the start point */
    emask = vis_edge8(da, dend);

    /* load 16 bytes */
    sd0 = *sp++;
    sd1 = *sp++;

    /* insert and store 16 bytes */
    INSERT_U8_12(sd0, dd0, dd1);
    INSERT_U8_12(sd1, dd2, dd3);
    vis_pst_8(vis_faligndata(dd1, dd2), dp++, emask & bmask);
    if ((mlib_addr) dp <= (mlib_addr) dend) {
      emask = vis_edge8(dp, dend);
      vis_pst_8(vis_faligndata(dd2, dd3), dp++, emask & bmask);
    }

    if ((mlib_addr) dp <= (mlib_addr) dend2) {
      n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 16 + 1;

      /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
      for (i = 0; i < n; i++) {
        dd0 = dd2;
        dd1 = dd3;
        sd1 = *sp++;
        INSERT_U8_12(sd1, dd2, dd3);
        vis_pst_8(vis_faligndata(dd1, dd2), dp++, bmask);
        vis_pst_8(vis_faligndata(dd2, dd3), dp++, bmask);
      }
    }

    /* end point handling */
    if ((mlib_addr) dp <= (mlib_addr) dend) {
      emask = vis_edge8(dp, dend);
      dd0 = dd2;
      dd1 = dd3;
      sd1 = *sp++;
      INSERT_U8_12(sd1, dd2, dd3);
      vis_pst_8(vis_faligndata(dd1, dd2), dp++, emask & bmask);
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge8(dp, dend);
        vis_pst_8(vis_faligndata(dd2, dd3), dp++, emask & bmask);
      }
    }
  }
}

/***************************************************************/
/* insert one channel to a 2-channel image.
 */

void mlib_v_ImageChannelInsert_U8_12(const mlib_u8 *src,
                                     mlib_s32      slb,
                                     mlib_u8       *dst,
                                     mlib_s32      dlb,
                                     mlib_s32      xsize,
                                     mlib_s32      ysize,
                                     mlib_s32      cmask)
{
  mlib_u8 *sa, *da;
  mlib_u8 *sl, *dl;
  mlib_s32 j;

  sa = sl = (void *)src;
  da = dl = dst;

#pragma pipeloop(0)
  for (j = 0; j < ysize; j++) {
    mlib_v_ImageChannelInsert_U8_12_D1(sa, da, xsize, cmask);
    sa = sl += slb;
    da = dl += dlb;
  }
}

/***************************************************************/
#define INSERT_U8_13(sd0, dd0, dd1, dd2)                        \
  sda = vis_fpmerge(vis_read_hi(sd0), vis_read_lo(sd0));        \
  sdb = vis_fpmerge(vis_read_hi(sda), vis_read_lo(sda));        \
  sdc = vis_fpmerge(vis_read_hi(sdb), vis_read_hi(sdb));        \
  sdd = vis_fpmerge(vis_read_lo(sdb), vis_read_lo(sdb));        \
  dd0 = vis_fpmerge(vis_read_hi(sdc), vis_read_hi(sdd));        \
  sde = vis_fpmerge(vis_read_lo(sdc), vis_read_lo(sdd));        \
  dd1 = vis_freg_pair(vis_read_lo(dd0), vis_read_hi(sde));      \
  dd2 = vis_freg_pair(vis_read_lo(sde), vis_read_lo(sde))

/***************************************************************/
#define LOAD_INSERT_STORE_U8_A8(channeld)                       \
  sd = *sp++;                                                   \
  vis_st_u8(sd = vis_faligndata(sd, sd), da); da += channeld;   \
  vis_st_u8(sd = vis_faligndata(sd, sd), da); da += channeld;   \
  vis_st_u8(sd = vis_faligndata(sd, sd), da); da += channeld;   \
  vis_st_u8(sd = vis_faligndata(sd, sd), da); da += channeld;   \
  vis_st_u8(sd = vis_faligndata(sd, sd), da); da += channeld;   \
  vis_st_u8(sd = vis_faligndata(sd, sd), da); da += channeld;   \
  vis_st_u8(sd = vis_faligndata(sd, sd), da); da += channeld;   \
  vis_st_u8(sd = vis_faligndata(sd, sd), da); da += channeld

/***************************************************************/
#define LOAD_INSERT_STORE_U8(channeld)                          \
  vis_alignaddr((void *)0, off);                                \
  sd0 = sd1;                                                    \
  sd1 = *sp++;                                                  \
  sd  = vis_faligndata(sd0, sd1);                               \
  vis_alignaddr((void *)0, 1);                                  \
  vis_st_u8(sd = vis_faligndata(sd, sd), da); da += channeld;   \
  vis_st_u8(sd = vis_faligndata(sd, sd), da); da += channeld;   \
  vis_st_u8(sd = vis_faligndata(sd, sd), da); da += channeld;   \
  vis_st_u8(sd = vis_faligndata(sd, sd), da); da += channeld;   \
  vis_st_u8(sd = vis_faligndata(sd, sd), da); da += channeld;   \
  vis_st_u8(sd = vis_faligndata(sd, sd), da); da += channeld;   \
  vis_st_u8(sd = vis_faligndata(sd, sd), da); da += channeld;   \
  vis_st_u8(sd = vis_faligndata(sd, sd), da); da += channeld

/***************************************************************/
void mlib_v_ImageChannelInsert_U8_13_A8D1X8(const mlib_u8 *src,
                                            mlib_u8       *dst,
                                            mlib_s32      dsize,
                                            mlib_s32      cmask)
{
  mlib_u8 *da;
  mlib_d64 *sp;
  mlib_d64 sd;
  mlib_s32 i;

  vis_alignaddr((void *)0, 1);              /* for 1-byte left shift */

  sp = (mlib_d64 *) src;
  da = dst + (2 / cmask);                   /* 4,2,1 -> 0,1,2 */

#pragma pipeloop(0)
  for (i = 0; i < dsize / 8; i++) {
    LOAD_INSERT_STORE_U8_A8(3);
  }
}

/***************************************************************/
void mlib_v_ImageChannelInsert_U8_13_A8D2X8(const mlib_u8 *src,
                                            mlib_s32      slb,
                                            mlib_u8       *dst,
                                            mlib_s32      dlb,
                                            mlib_s32      xsize,
                                            mlib_s32      ysize,
                                            mlib_s32      cmask)
{
  mlib_u8 *da, *dl;
  mlib_d64 *sp, *sl;
  mlib_d64 sd;
  mlib_s32 i, j;

  vis_alignaddr((void *)0, 1);

  sp = sl = (mlib_d64 *) src;
  da = dl = dst + (2 / cmask);              /* 4,2,1 -> 0,1,2 */

  for (j = 0; j < ysize; j++) {
#pragma pipeloop(0)
    for (i = 0; i < xsize / 8; i++) {
      LOAD_INSERT_STORE_U8_A8(3);
    }

    sp = sl = (mlib_d64 *) ((mlib_u8 *) sl + slb);
    da = dl = (mlib_u8 *) ((mlib_u8 *) dl + dlb);
  }
}

/***************************************************************/
void mlib_v_ImageChannelInsert_U8_13_D1(const mlib_u8 *src,
                                        mlib_u8       *dst,
                                        mlib_s32      dsize,
                                        mlib_s32      cmask)
{
  mlib_u8 *sa, *da;
  mlib_u8 *dend;                                      /* end point in destination */
  mlib_d64 *sp;                                       /* 8-byte aligned start points in src */
  mlib_d64 sd0, sd1, sd;                              /* 8-byte registers for source data */
  mlib_s32 off;                                       /* offset of address alignment in src */
  mlib_s32 i;

  /* prepare the src address */
  sa = (void *)src;
  sp = (mlib_d64 *) ((mlib_addr) sa & (~7));
  off = (mlib_addr) sa & 7;

  /* prepare the dst address */
  da = dst + (2 / cmask);                   /* 4,2,1 -> 0,1,2 */
  dend = da + dsize * 3 - 1;

  sd1 = *sp++;

#pragma pipeloop(0)
  for (i = 0; i < dsize / 8; i++) {
    LOAD_INSERT_STORE_U8(3);
  }

  /* right end handling */
  if ((mlib_addr) da <= (mlib_addr) dend) {

    vis_alignaddr((void *)0, off);
    sd0 = sd1;
    sd1 = *sp++;
    sd = vis_faligndata(sd0, sd1);

    vis_alignaddr((void *)0, 1);
    vis_st_u8(sd = vis_faligndata(sd, sd), da);
    da += 3;
    if ((mlib_addr) da <= (mlib_addr) dend) {
      vis_st_u8(sd = vis_faligndata(sd, sd), da);
      da += 3;
      if ((mlib_addr) da <= (mlib_addr) dend) {
        vis_st_u8(sd = vis_faligndata(sd, sd), da);
        da += 3;
        if ((mlib_addr) da <= (mlib_addr) dend) {
          vis_st_u8(sd = vis_faligndata(sd, sd), da);
          da += 3;
          if ((mlib_addr) da <= (mlib_addr) dend) {
            vis_st_u8(sd = vis_faligndata(sd, sd), da);
            da += 3;
            if ((mlib_addr) da <= (mlib_addr) dend) {
              vis_st_u8(sd = vis_faligndata(sd, sd), da);
              da += 3;
              if ((mlib_addr) da <= (mlib_addr) dend) {
                vis_st_u8(sd = vis_faligndata(sd, sd), da);
              }
            }
          }
        }
      }
    }
  }
}

/***************************************************************/
void mlib_v_ImageChannelInsert_U8_13(const mlib_u8 *src,
                                     mlib_s32      slb,
                                     mlib_u8       *dst,
                                     mlib_s32      dlb,
                                     mlib_s32      xsize,
                                     mlib_s32      ysize,
                                     mlib_s32      cmask)
{
  mlib_u8 *sa, *da;
  mlib_u8 *sl, *dl;
  mlib_s32 j;

  sa = sl = (void *)src;
  da = dl = dst;

#pragma pipeloop(0)
  for (j = 0; j < ysize; j++) {
    mlib_v_ImageChannelInsert_U8_13_D1(sa, da, xsize, cmask);
    sa = sl += slb;
    da = dl += dlb;
  }
}

/***************************************************************/
#define INSERT_U8_14(sd0, dd0, dd1, dd2, dd3)                   \
  sda = vis_fpmerge(vis_read_hi(sd0), vis_read_hi(sd0));        \
  sdb = vis_fpmerge(vis_read_lo(sd0), vis_read_lo(sd0));        \
  dd0 = vis_fpmerge(vis_read_hi(sda), vis_read_hi(sda));        \
  dd1 = vis_fpmerge(vis_read_lo(sda), vis_read_lo(sda));        \
  dd2 = vis_fpmerge(vis_read_hi(sdb), vis_read_hi(sdb));        \
  dd3 = vis_fpmerge(vis_read_lo(sdb), vis_read_lo(sdb))

/***************************************************************/
void mlib_v_ImageChannelInsert_U8_14_A8D1X8(const mlib_u8 *src,
                                            mlib_u8       *dst,
                                            mlib_s32      dsize,
                                            mlib_s32      cmask)
{
  mlib_d64 *sp, *dp;
  mlib_d64 sd0;
  mlib_d64 sda, sdb;
  mlib_d64 dd0, dd1, dd2, dd3;
  mlib_s32 bmask;
  mlib_s32 i;

  bmask = cmask | (cmask << 4);

  sp = (mlib_d64 *) src;
  dp = (mlib_d64 *) dst;

#pragma pipeloop(0)
  for (i = 0; i < dsize / 8; i++) {
    sd0 = *sp++;
    INSERT_U8_14(sd0, dd0, dd1, dd2, dd3);
    vis_pst_8(dd0, dp++, bmask);
    vis_pst_8(dd1, dp++, bmask);
    vis_pst_8(dd2, dp++, bmask);
    vis_pst_8(dd3, dp++, bmask);
  }
}

/***************************************************************/
void mlib_v_ImageChannelInsert_U8_14_A8D2X8(const mlib_u8 *src,
                                            mlib_s32      slb,
                                            mlib_u8       *dst,
                                            mlib_s32      dlb,
                                            mlib_s32      xsize,
                                            mlib_s32      ysize,
                                            mlib_s32      cmask)
{
  mlib_d64 *sp, *dp;
  mlib_d64 *sl, *dl;
  mlib_d64 sd0;
  mlib_d64 sda, sdb;
  mlib_d64 dd0, dd1, dd2, dd3;
  mlib_s32 bmask;
  mlib_s32 i, j;

  bmask = cmask | (cmask << 4);

  sp = sl = (mlib_d64 *) src;
  dp = dl = (mlib_d64 *) dst;

  for (j = 0; j < ysize; j++) {
#pragma pipeloop(0)
    for (i = 0; i < xsize / 8; i++) {
      sd0 = *sp++;
      INSERT_U8_14(sd0, dd0, dd1, dd2, dd3);
      vis_pst_8(dd0, dp++, bmask);
      vis_pst_8(dd1, dp++, bmask);
      vis_pst_8(dd2, dp++, bmask);
      vis_pst_8(dd3, dp++, bmask);
    }

    sp = sl = (mlib_d64 *) ((mlib_u8 *) sl + slb);
    dp = dl = (mlib_d64 *) ((mlib_u8 *) dl + dlb);
  }
}

/***************************************************************/
void mlib_v_ImageChannelInsert_U8_14_D1(const mlib_u8 *src,
                                        mlib_u8       *dst,
                                        mlib_s32      dsize,
                                        mlib_s32      cmask)
{
  mlib_u8 *sa, *da;
  mlib_u8 *dend, *dend2;                              /* end points in dst */
  mlib_d64 *dp;                                       /* 8-byte aligned start points in dst */
  mlib_d64 *sp;                                       /* 8-byte aligned start point in src */
  mlib_d64 sd0, sd1, sd;                              /* 8-byte source data */
  mlib_d64 sda, sdb;
  mlib_d64 dd0, dd1, dd2, dd3, dd4;
  mlib_s32 soff;                                      /* offset of address in src */
  mlib_s32 doff;                                      /* offset of address in dst */
  mlib_s32 emask;                                     /* edge mask */
  mlib_s32 bmask;                                     /* channel mask */
  mlib_s32 i, n;

  sa = (void *)src;
  da = dst;

  bmask = cmask | (cmask << 4) | (cmask << 8);

  /* prepare the source address */
  sp = (mlib_d64 *) ((mlib_addr) sa & (~7));
  soff = ((mlib_addr) sa & 7);

  /* prepare the destination addresses */
  dp = (mlib_d64 *) ((mlib_addr) da & (~7));
  doff = ((mlib_addr) da & 7);
  dend = da + dsize * 4 - 1;
  dend2 = dend - 31;

  bmask = (bmask >> (doff % 4)) & 0xff;

  if (doff == 0) {                          /* dst is 8-byte aligned */

    vis_alignaddr((void *)0, soff);
    sd0 = *sp++;
    sd1 = *sp++;
    sd = vis_faligndata(sd0, sd1);          /* the intermediate is aligned */

    INSERT_U8_14(sd, dd0, dd1, dd2, dd3);

    emask = vis_edge8(da, dend);
    vis_pst_8(dd0, dp++, emask & bmask);
    if ((mlib_addr) dp <= (mlib_addr) dend) { /* for very small size */
      emask = vis_edge8(dp, dend);
      vis_pst_8(dd1, dp++, emask & bmask);
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge8(dp, dend);
        vis_pst_8(dd2, dp++, emask & bmask);
        if ((mlib_addr) dp <= (mlib_addr) dend) {
          emask = vis_edge8(dp, dend);
          vis_pst_8(dd3, dp++, emask & bmask);
        }
      }
    }

    if ((mlib_addr) dp <= (mlib_addr) dend2) {
      n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 32 + 1;

      /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
      for (i = 0; i < n; i++) {
        sd0 = sd1;
        sd1 = *sp++;
        sd = vis_faligndata(sd0, sd1);

        INSERT_U8_14(sd, dd0, dd1, dd2, dd3);

        vis_pst_8(dd0, dp++, bmask);
        vis_pst_8(dd1, dp++, bmask);
        vis_pst_8(dd2, dp++, bmask);
        vis_pst_8(dd3, dp++, bmask);
      }
    }

    /* end point handling */
    if ((mlib_addr) dp <= (mlib_addr) dend) {
      sd0 = sd1;
      sd1 = *sp++;
      sd = vis_faligndata(sd0, sd1);

      INSERT_U8_14(sd, dd0, dd1, dd2, dd3);

      emask = vis_edge8(dp, dend);
      vis_pst_8(dd0, dp++, emask & bmask);
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge8(dp, dend);
        vis_pst_8(dd1, dp++, emask & bmask);
        if ((mlib_addr) dp <= (mlib_addr) dend) {
          emask = vis_edge8(dp, dend);
          vis_pst_8(dd2, dp++, emask & bmask);
          if ((mlib_addr) dp <= (mlib_addr) dend) {
            emask = vis_edge8(dp, dend);
            vis_pst_8(dd3, dp++, emask & bmask);
          }
        }
      }
    }
  }
  else {                                    /* dst is not 8-byte aligned */
    vis_alignaddr((void *)0, soff);
    sd0 = *sp++;
    sd1 = *sp++;
    sd = vis_faligndata(sd0, sd1);          /* the intermediate is aligned */

    INSERT_U8_14(sd, dd0, dd1, dd2, dd3);

    vis_alignaddr((void *)0, -doff);

    emask = vis_edge8(da, dend);
    vis_pst_8(vis_faligndata(dd0, dd0), dp++, emask & bmask);
    if ((mlib_addr) dp <= (mlib_addr) dend) { /* for very small size */
      emask = vis_edge8(dp, dend);
      vis_pst_8(vis_faligndata(dd0, dd1), dp++, emask & bmask);
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge8(dp, dend);
        vis_pst_8(vis_faligndata(dd1, dd2), dp++, emask & bmask);
        if ((mlib_addr) dp <= (mlib_addr) dend) {
          emask = vis_edge8(dp, dend);
          vis_pst_8(vis_faligndata(dd2, dd3), dp++, emask & bmask);
        }
      }
    }

    if ((mlib_addr) dp <= (mlib_addr) dend2) {
      n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 32 + 1;

      /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
      for (i = 0; i < n; i++) {
        dd4 = dd3;

        vis_alignaddr((void *)0, soff);
        sd0 = sd1;
        sd1 = *sp++;
        sd = vis_faligndata(sd0, sd1);

        INSERT_U8_14(sd, dd0, dd1, dd2, dd3);

        vis_alignaddr((void *)0, -doff);
        vis_pst_8(vis_faligndata(dd4, dd0), dp++, bmask);
        vis_pst_8(vis_faligndata(dd0, dd1), dp++, bmask);
        vis_pst_8(vis_faligndata(dd1, dd2), dp++, bmask);
        vis_pst_8(vis_faligndata(dd2, dd3), dp++, bmask);
      }
    }

    /* end point handling */
    if ((mlib_addr) dp <= (mlib_addr) dend) {
      dd4 = dd3;

      vis_alignaddr((void *)0, soff);
      sd0 = sd1;
      sd1 = *sp++;
      sd = vis_faligndata(sd0, sd1);

      INSERT_U8_14(sd, dd0, dd1, dd2, dd3);

      vis_alignaddr((void *)0, -doff);
      emask = vis_edge8(dp, dend);
      vis_pst_8(vis_faligndata(dd4, dd0), dp++, emask & bmask);
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge8(dp, dend);
        vis_pst_8(vis_faligndata(dd0, dd1), dp++, emask & bmask);
        if ((mlib_addr) dp <= (mlib_addr) dend) {
          emask = vis_edge8(dp, dend);
          vis_pst_8(vis_faligndata(dd1, dd2), dp++, emask & bmask);
          if ((mlib_addr) dp <= (mlib_addr) dend) {
            emask = vis_edge8(dp, dend);
            vis_pst_8(vis_faligndata(dd2, dd3), dp++, emask & bmask);
          }
        }
      }
    }
  }
}

/***************************************************************/
void mlib_v_ImageChannelInsert_U8_14(const mlib_u8 *src,
                                     mlib_s32      slb,
                                     mlib_u8       *dst,
                                     mlib_s32      dlb,
                                     mlib_s32      xsize,
                                     mlib_s32      ysize,
                                     mlib_s32      cmask)
{
  mlib_u8 *sa, *da;
  mlib_u8 *sl, *dl;
  mlib_s32 j;

  sa = sl = (void *)src;
  da = dl = dst;

#pragma pipeloop(0)
  for (j = 0; j < ysize; j++) {
    mlib_v_ImageChannelInsert_U8_14_D1(sa, da, xsize, cmask);
    sa = sl += slb;
    da = dl += dlb;
  }
}

/***************************************************************/
#define LOAD_INSERT_STORE_S16_1X_A8(channeld)                   \
  sd  = *sp++;                                                  \
  vis_st_u16(sd = vis_faligndata(sd, sd), da); da += channeld;  \
  vis_st_u16(sd = vis_faligndata(sd, sd), da); da += channeld;  \
  vis_st_u16(sd = vis_faligndata(sd, sd), da); da += channeld;  \
  vis_st_u16(sd = vis_faligndata(sd, sd), da); da += channeld

/***************************************************************/
#define LOAD_INSERT_STORE_S16_1X(channeld)                      \
  vis_alignaddr((void *)0, off);                                \
  sd0 = sd1;                                                    \
  sd1 = *sp++;                                                  \
  sd  = vis_faligndata(sd0, sd1);                               \
  vis_alignaddr((void *)0, 2);                                  \
  vis_st_u16(sd = vis_faligndata(sd, sd), da); da += channeld;  \
  vis_st_u16(sd = vis_faligndata(sd, sd), da); da += channeld;  \
  vis_st_u16(sd = vis_faligndata(sd, sd), da); da += channeld;  \
  vis_st_u16(sd = vis_faligndata(sd, sd), da); da += channeld

/***************************************************************/
void mlib_v_ImageChannelInsert_S16_12_A8D1X4(const mlib_s16 *src,
                                             mlib_s16       *dst,
                                             mlib_s32       dsize,
                                             mlib_s32       cmask)
{
  mlib_s16 *da;
  mlib_d64 *sp;
  mlib_d64 sd;
  mlib_s32 i;

  sp = (mlib_d64 *) src;
  da = dst + (2 - cmask);                   /* 2,1 -> 0,1 */

  vis_alignaddr((void *)0, 2);

#pragma pipeloop(0)
  for (i = 0; i < dsize / 4; i++) {
    LOAD_INSERT_STORE_S16_1X_A8(2);
  }
}

/***************************************************************/
void mlib_v_ImageChannelInsert_S16_12_A8D2X4(const mlib_s16 *src,
                                             mlib_s32       slb,
                                             mlib_s16       *dst,
                                             mlib_s32       dlb,
                                             mlib_s32       xsize,
                                             mlib_s32       ysize,
                                             mlib_s32       cmask)
{
  mlib_s16 *da, *dl;
  mlib_d64 *sp, *sl;
  mlib_d64 sd;
  mlib_s32 i, j;

  sp = sl = (mlib_d64 *) src;
  da = dl = dst + (2 - cmask);              /* 2,1 -> 0,1 */

  vis_alignaddr((void *)0, 2);

  for (j = 0; j < ysize; j++) {
#pragma pipeloop(0)
    for (i = 0; i < xsize / 4; i++) {
      LOAD_INSERT_STORE_S16_1X_A8(2);
    }

    sp = sl = (mlib_d64 *) ((mlib_u8 *) sl + slb);
    da = dl = (mlib_s16 *) ((mlib_u8 *) dl + dlb);
  }
}

/***************************************************************/
void mlib_v_ImageChannelInsert_S16_12_D1(const mlib_s16 *src,
                                         mlib_s16       *dst,
                                         mlib_s32       dsize,
                                         mlib_s32       cmask)
{
  mlib_s16 *sa, *da;
  mlib_s16 *dend;                                     /* end point in destination */
  mlib_d64 *sp;                                       /* 8-byte aligned start points in src */
  mlib_d64 sd0, sd1, sd;                              /* 8-byte registers for source data */
  mlib_s32 off;                                       /* offset of address alignment in src */
  mlib_s32 i;

  sa = (void *)src;
  da = dst + (2 - cmask);                   /* 2,1 -> 0,1 */

  /* prepare the src address */
  sp = (mlib_d64 *) ((mlib_addr) sa & (~7));
  off = (mlib_addr) sa & 7;

  dend = da + dsize * 2 - 1;

  sd1 = *sp++;

#pragma pipeloop(0)
  for (i = 0; i < dsize / 4; i++) {
    LOAD_INSERT_STORE_S16_1X(2);
  }

  /* right end handling */
  if ((mlib_addr) da <= (mlib_addr) dend) {

    vis_alignaddr((void *)0, off);
    sd0 = sd1;
    sd1 = *sp++;
    sd = vis_faligndata(sd0, sd1);

    vis_alignaddr((void *)0, 2);
    vis_st_u16(sd = vis_faligndata(sd, sd), da);
    da += 2;
    if ((mlib_addr) da <= (mlib_addr) dend) {
      vis_st_u16(sd = vis_faligndata(sd, sd), da);
      da += 2;
      if ((mlib_addr) da <= (mlib_addr) dend) {
        vis_st_u16(sd = vis_faligndata(sd, sd), da);
      }
    }
  }
}

/***************************************************************/
void mlib_v_ImageChannelInsert_S16_12(const mlib_s16 *src,
                                      mlib_s32       slb,
                                      mlib_s16       *dst,
                                      mlib_s32       dlb,
                                      mlib_s32       xsize,
                                      mlib_s32       ysize,
                                      mlib_s32       cmask)
{
  mlib_s16 *sa, *da;
  mlib_s16 *sl, *dl;
  mlib_s32 j;

  sa = sl = (void *)src;
  da = dl = dst;

#pragma pipeloop(0)
  for (j = 0; j < ysize; j++) {
    mlib_v_ImageChannelInsert_S16_12_D1(sa, da, xsize, cmask);
    sa = sl = (mlib_s16 *) ((mlib_u8 *) sl + slb);
    da = dl = (mlib_s16 *) ((mlib_u8 *) dl + dlb);
  }
}

/***************************************************************/
void mlib_v_ImageChannelInsert_S16_13_A8D1X4(const mlib_s16 *src,
                                             mlib_s16       *dst,
                                             mlib_s32       dsize,
                                             mlib_s32       cmask)
{
  mlib_s16 *da;
  mlib_d64 *sp;
  mlib_d64 sd;
  mlib_s32 i;

  sp = (mlib_d64 *) src;
  da = dst + (2 / cmask);                   /* 4,2,1 -> 0,1,2 */

  vis_alignaddr((void *)0, 2);

#pragma pipeloop(0)
  for (i = 0; i < dsize / 4; i++) {
    LOAD_INSERT_STORE_S16_1X_A8(3);
  }
}

/***************************************************************/
void mlib_v_ImageChannelInsert_S16_13_A8D2X4(const mlib_s16 *src,
                                             mlib_s32       slb,
                                             mlib_s16       *dst,
                                             mlib_s32       dlb,
                                             mlib_s32       xsize,
                                             mlib_s32       ysize,
                                             mlib_s32       cmask)
{
  mlib_s16 *da, *dl;
  mlib_d64 *sp, *sl;
  mlib_d64 sd;
  mlib_s32 i, j;

  sp = sl = (mlib_d64 *) src;
  da = dl = dst + (2 / cmask);              /* 4,2,1 -> 0,1,2 */

  vis_alignaddr((void *)0, 2);

  for (j = 0; j < ysize; j++) {
#pragma pipeloop(0)
    for (i = 0; i < xsize / 4; i++) {
      LOAD_INSERT_STORE_S16_1X_A8(3);
    }

    sp = sl = (mlib_d64 *) ((mlib_u8 *) sl + slb);
    da = dl = (mlib_s16 *) ((mlib_u8 *) dl + dlb);
  }
}

/***************************************************************/
void mlib_v_ImageChannelInsert_S16_13_D1(const mlib_s16 *src,
                                         mlib_s16       *dst,
                                         mlib_s32       dsize,
                                         mlib_s32       cmask)
{
  mlib_s16 *sa, *da;
  mlib_s16 *dend;                                     /* end point in destination */
  mlib_d64 *sp;                                       /* 8-byte aligned start points in src */
  mlib_d64 sd0, sd1, sd;                              /* 8-byte registers for source data */
  mlib_s32 off;                                       /* offset of address alignment in src */
  mlib_s32 i;

  sa = (void *)src;
  da = dst + (2 / cmask);                   /* 4,2,1 -> 0,1,2 */

  /* prepare the src address */
  sp = (mlib_d64 *) ((mlib_addr) sa & (~7));
  off = (mlib_addr) sa & 7;

  dend = da + dsize * 3 - 1;

  sd1 = *sp++;

#pragma pipeloop(0)
  for (i = 0; i < dsize / 4; i++) {
    LOAD_INSERT_STORE_S16_1X(3);
  }

  /* right end handling */
  if ((mlib_addr) da <= (mlib_addr) dend) {

    vis_alignaddr((void *)0, off);
    sd0 = sd1;
    sd1 = *sp++;
    sd = vis_faligndata(sd0, sd1);

    vis_alignaddr((void *)0, 2);
    vis_st_u16(sd = vis_faligndata(sd, sd), da);
    da += 3;
    if ((mlib_addr) da <= (mlib_addr) dend) {
      vis_st_u16(sd = vis_faligndata(sd, sd), da);
      da += 3;
      if ((mlib_addr) da <= (mlib_addr) dend) {
        vis_st_u16(sd = vis_faligndata(sd, sd), da);
      }
    }
  }
}

/***************************************************************/
void mlib_v_ImageChannelInsert_S16_13(const mlib_s16 *src,
                                      mlib_s32       slb,
                                      mlib_s16       *dst,
                                      mlib_s32       dlb,
                                      mlib_s32       xsize,
                                      mlib_s32       ysize,
                                      mlib_s32       cmask)
{
  mlib_s16 *sa, *da;
  mlib_s16 *sl, *dl;
  mlib_s32 j;

  sa = sl = (void *)src;
  da = dl = dst;

#pragma pipeloop(0)
  for (j = 0; j < ysize; j++) {
    mlib_v_ImageChannelInsert_S16_13_D1(sa, da, xsize, cmask);
    sa = sl = (mlib_s16 *) ((mlib_u8 *) sl + slb);
    da = dl = (mlib_s16 *) ((mlib_u8 *) dl + dlb);
  }
}

/***************************************************************/
#define INSERT_S16_14(sp, dp, bmask)    /* channel duplicate */ \
  /* obsolete: it is slower than the vis_st_u16() version*/     \
  sd0 = *sp++;                                                  \
  sda = vis_fpmerge(vis_read_hi(sd0), vis_read_hi(sd0));        \
  sdb = vis_fpmerge(vis_read_lo(sd0), vis_read_lo(sd0));        \
  sdc = vis_fpmerge(vis_read_hi(sda), vis_read_hi(sda));        \
  sdd = vis_fpmerge(vis_read_lo(sda), vis_read_lo(sda));        \
  sde = vis_fpmerge(vis_read_hi(sdb), vis_read_hi(sdb));        \
  sdf = vis_fpmerge(vis_read_lo(sdb), vis_read_lo(sdb));        \
  dd0 = vis_fpmerge(vis_read_hi(sdc), vis_read_lo(sdc));        \
  dd1 = vis_fpmerge(vis_read_hi(sdd), vis_read_lo(sdd));        \
  dd2 = vis_fpmerge(vis_read_hi(sde), vis_read_lo(sde));        \
  dd3 = vis_fpmerge(vis_read_hi(sdf), vis_read_lo(sdf));        \
  vis_pst_16(dd0, dp++, bmask);                                 \
  vis_pst_16(dd1, dp++, bmask);                                 \
  vis_pst_16(dd2, dp++, bmask);                                 \
  vis_pst_16(dd3, dp++, bmask)

/***************************************************************/
void mlib_v_ImageChannelInsert_S16_14_A8D1X4(const mlib_s16 *src,
                                             mlib_s16       *dst,
                                             mlib_s32       dsize,
                                             mlib_s32       cmask)
{
  mlib_s16 *da;
  mlib_d64 *sp;
  mlib_d64 sd;
  mlib_s32 i;

  sp = (mlib_d64 *) src;
  da = dst + (6 / cmask + 1) / 2;           /* 8,4,2,1 -> 0,1,2,3 */

  vis_alignaddr((void *)0, 2);

#pragma pipeloop(0)
  for (i = 0; i < dsize / 4; i++) {
    LOAD_INSERT_STORE_S16_1X_A8(4);
  }
}

/***************************************************************/
void mlib_v_ImageChannelInsert_S16_14_A8D2X4(const mlib_s16 *src,
                                             mlib_s32       slb,
                                             mlib_s16       *dst,
                                             mlib_s32       dlb,
                                             mlib_s32       xsize,
                                             mlib_s32       ysize,
                                             mlib_s32       cmask)
{
  mlib_s16 *da, *dl;
  mlib_d64 *sp, *sl;
  mlib_d64 sd;
  mlib_s32 i, j;

  sp = sl = (mlib_d64 *) src;
  da = dl = dst + (6 / cmask + 1) / 2;      /* 8,4,2,1 -> 0,1,2,3 */

  vis_alignaddr((void *)0, 2);

  for (j = 0; j < ysize; j++) {
#pragma pipeloop(0)
    for (i = 0; i < xsize / 4; i++) {
      LOAD_INSERT_STORE_S16_1X_A8(4);
    }

    sp = sl = (mlib_d64 *) ((mlib_u8 *) sl + slb);
    da = dl = (mlib_s16 *) ((mlib_u8 *) dl + dlb);
  }
}

/***************************************************************/
void mlib_v_ImageChannelInsert_S16_14_D1(const mlib_s16 *src,
                                         mlib_s16       *dst,
                                         mlib_s32       dsize,
                                         mlib_s32       cmask)
{
  mlib_s16 *sa, *da;
  mlib_s16 *dend;                                     /* end point in destination */
  mlib_d64 *sp;                                       /* 8-byte aligned start points in src */
  mlib_d64 sd0, sd1, sd;                              /* 8-byte registers for source data */
  mlib_s32 off;                                       /* offset of address alignment in src */
  mlib_s32 i;

  sa = (void *)src;
  da = dst + (6 / cmask + 1) / 2;           /* 8,4,2,1 -> 0,1,2,3 */

  /* prepare the src address */
  sp = (mlib_d64 *) ((mlib_addr) sa & (~7));
  off = (mlib_addr) sa & 7;

  dend = da + dsize * 4 - 1;

  sd1 = *sp++;

#pragma pipeloop(0)
  for (i = 0; i < dsize / 4; i++) {
    LOAD_INSERT_STORE_S16_1X(4);
  }

  /* right end handling */
  if ((mlib_addr) da <= (mlib_addr) dend) {

    vis_alignaddr((void *)0, off);
    sd0 = sd1;
    sd1 = *sp++;
    sd = vis_faligndata(sd0, sd1);

    vis_alignaddr((void *)0, 2);
    vis_st_u16(sd = vis_faligndata(sd, sd), da);
    da += 4;
    if ((mlib_addr) da <= (mlib_addr) dend) {
      vis_st_u16(sd = vis_faligndata(sd, sd), da);
      da += 4;
      if ((mlib_addr) da <= (mlib_addr) dend) {
        vis_st_u16(sd = vis_faligndata(sd, sd), da);
      }
    }
  }
}

/***************************************************************/
void mlib_v_ImageChannelInsert_S16_14(const mlib_s16 *src,
                                      mlib_s32       slb,
                                      mlib_s16       *dst,
                                      mlib_s32       dlb,
                                      mlib_s32       xsize,
                                      mlib_s32       ysize,
                                      mlib_s32       cmask)
{
  mlib_s16 *sa, *da;
  mlib_s16 *sl, *dl;
  mlib_s32 j;

  sa = sl = (void *)src;
  da = dl = dst;

#pragma pipeloop(0)
  for (j = 0; j < ysize; j++) {
    mlib_v_ImageChannelInsert_S16_14_D1(sa, da, xsize, cmask);
    sa = sl = (mlib_s16 *) ((mlib_u8 *) sl + slb);
    da = dl = (mlib_s16 *) ((mlib_u8 *) dl + dlb);
  }
}

/***************************************************************/
