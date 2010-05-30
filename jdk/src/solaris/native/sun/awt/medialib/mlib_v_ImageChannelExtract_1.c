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
 * FILENAME: mlib_ImageChannelExtract_1.c
 *
 * FUNCTIONS
 *      mlib_v_ImageChannelExtract_U8_21_A8D1X8
 *      mlib_v_ImageChannelExtract_U8_21_A8D2X8
 *      mlib_v_ImageChannelExtract_U8_21_D1
 *      mlib_v_ImageChannelExtract_U8_21
 *      mlib_v_ImageChannelExtract_U8_31_A8D1X8
 *      mlib_v_ImageChannelExtract_U8_31_A8D2X8
 *      mlib_v_ImageChannelExtract_U8_31_D1
 *      mlib_v_ImageChannelExtract_U8_31
 *      mlib_v_ImageChannelExtract_U8_41_A8D1X8
 *      mlib_v_ImageChannelExtract_U8_41_A8D2X8
 *      mlib_v_ImageChannelExtract_U8_41_D1
 *      mlib_v_ImageChannelExtract_U8_41
 *      mlib_v_ImageChannelExtract_S16_21_A8D1X4
 *      mlib_v_ImageChannelExtract_S16_21_A8D2X4
 *      mlib_v_ImageChannelExtract_S16_21_D1
 *      mlib_v_ImageChannelExtract_S16_21
 *      mlib_v_ImageChannelExtract_S16_31_A8D1X4
 *      mlib_v_ImageChannelExtract_S16_31_A8D2X4
 *      mlib_v_ImageChannelExtract_S16_31_D1
 *      mlib_v_ImageChannelExtract_S16_31
 *      mlib_v_ImageChannelExtract_S16_41_A8D1X4
 *      mlib_v_ImageChannelExtract_S16_41_A8D2X4
 *      mlib_v_ImageChannelExtract_S16_41_D1
 *      mlib_v_ImageChannelExtract_S16_41
 *
 * ARGUMENT
 *      src    pointer to source image data
 *      dst    pointer to destination image data
 *      slb    source image line stride in bytes
 *      dlb   destination image line stride in bytes
 *      dsize  image data size in pixels
 *      xsize  image width in pixels
 *      ysize  image height in lines
 *      cmask channel mask
 *
 * DESCRIPTION
 *      Extract the one selected channel of the source image into the
 *      1-channel destination image.
 *
 * NOTE
 *      These functions are separated from mlib_ImageChannelExtract.c
 *      for loop unrolling and structure clarity.
 */

#include "vis_proto.h"
#include "mlib_image.h"
#include "mlib_v_ImageChannelExtract.h"

/***************************************************************/
#define CHANNELEXTRACT_U8_21L(sd0, sd1, dd)                     \
  sda = vis_fpmerge(vis_read_hi(sd0), vis_read_hi(sd1));        \
  sdb = vis_fpmerge(vis_read_lo(sd0), vis_read_lo(sd1));        \
  sdc = vis_fpmerge(vis_read_hi(sda), vis_read_hi(sdb));        \
  sdd = vis_fpmerge(vis_read_lo(sda), vis_read_lo(sdb));        \
  dd  = vis_fpmerge(vis_read_hi(sdc), vis_read_hi(sdd))

/***************************************************************/
#define CHANNELEXTRACT_U8_21R(sd0, sd1, dd)                     \
  sda = vis_fpmerge(vis_read_hi(sd0), vis_read_hi(sd1));        \
  sdb = vis_fpmerge(vis_read_lo(sd0), vis_read_lo(sd1));        \
  sdc = vis_fpmerge(vis_read_hi(sda), vis_read_hi(sdb));        \
  sdd = vis_fpmerge(vis_read_lo(sda), vis_read_lo(sdb));        \
  dd  = vis_fpmerge(vis_read_lo(sdc), vis_read_lo(sdd))

/***************************************************************/
/* extract one channel from a 2-channel image.
 * both source and destination image data are 8-byte aligned.
 * xsize is multiple of 8.
 */

void mlib_v_ImageChannelExtract_U8_21_A8D1X8(const mlib_u8 *src,
                                             mlib_u8       *dst,
                                             mlib_s32      dsize,
                                             mlib_s32      cmask)
{
  mlib_d64 *sp, *dp;
  mlib_d64 sd0, sd1;
  mlib_d64 sda, sdb, sdc, sdd;
  mlib_d64 dd;
  mlib_s32 i;

  sp = (mlib_d64 *) src;
  dp = (mlib_d64 *) dst;

  if (cmask == 2) {
#pragma pipeloop(0)
    for (i = 0; i < dsize / 8; i++) {
      sd0 = *sp++;
      sd1 = *sp++;
      CHANNELEXTRACT_U8_21L(sd0, sd1, dd);
      *dp++ = dd;
    }
  }
  else {
#pragma pipeloop(0)
    for (i = 0; i < dsize / 8; i++) {
      sd0 = *sp++;
      sd1 = *sp++;
      CHANNELEXTRACT_U8_21R(sd0, sd1, dd);
      *dp++ = dd;
    }
  }
}

/***************************************************************/
/* extract one channel from a 2-channel image.
 * both source and destination image data are 8-byte aligned.
 * xsize is multiple of 8.
 */

void mlib_v_ImageChannelExtract_U8_21_A8D2X8(const mlib_u8 *src,
                                             mlib_s32      slb,
                                             mlib_u8       *dst,
                                             mlib_s32      dlb,
                                             mlib_s32      xsize,
                                             mlib_s32      ysize,
                                             mlib_s32      cmask)
{
  mlib_d64 *sp, *dp;
  mlib_d64 *sl, *dl;
  mlib_d64 sd0, sd1;
  mlib_d64 sda, sdb, sdc, sdd;
  mlib_d64 dd;
  mlib_s32 i, j;

  sp = sl = (mlib_d64 *) src;
  dp = dl = (mlib_d64 *) dst;

  if (cmask == 2) {
    for (j = 0; j < ysize; j++) {
#pragma pipeloop(0)
      for (i = 0; i < xsize / 8; i++) {
        sd0 = *sp++;
        sd1 = *sp++;
        CHANNELEXTRACT_U8_21L(sd0, sd1, dd);
        *dp++ = dd;
      }

      sp = sl = (mlib_d64 *) ((mlib_u8 *) sl + slb);
      dp = dl = (mlib_d64 *) ((mlib_u8 *) dl + dlb);
    }
  }
  else {
    for (j = 0; j < ysize; j++) {
#pragma pipeloop(0)
      for (i = 0; i < xsize / 8; i++) {
        sd0 = *sp++;
        sd1 = *sp++;
        CHANNELEXTRACT_U8_21R(sd0, sd1, dd);
        *dp++ = dd;
      }

      sp = sl = (mlib_d64 *) ((mlib_u8 *) sl + slb);
      dp = dl = (mlib_d64 *) ((mlib_u8 *) dl + dlb);
    }
  }
}

/***************************************************************/
/* extract one channel from a 2-channel image.
 */

void mlib_v_ImageChannelExtract_U8_21_D1(const mlib_u8 *src,
                                         mlib_u8       *dst,
                                         mlib_s32      dsize,
                                         mlib_s32      cmask)
{
  mlib_u8 *sa, *da;
  mlib_u8 *dend, *dend2;                              /* end points in dst */
  mlib_d64 *dp;                                       /* 8-byte aligned start points in dst */
  mlib_d64 *sp;                                       /* 8-byte aligned start point in src */
  mlib_d64 sd0, sd1, sd2, sd3;                        /* 8-byte source data */
  mlib_d64 sda, sdb, sdc, sdd;
  mlib_d64 dd0, dd1;
  mlib_s32 soff;                                      /* offset of address in src */
  mlib_s32 doff;                                      /* offset of address in dst */
  mlib_s32 off;                                       /* offset of src over dst */
  mlib_s32 emask;                                     /* edge mask */
  mlib_s32 i, n;

  sa = (void *)src;
  da = dst;

  /* prepare the source address */
  sp = (mlib_d64 *) ((mlib_addr) sa & (~7));
  soff = ((mlib_addr) sa & 7);

  /* prepare the destination addresses */
  dp = (mlib_d64 *) ((mlib_addr) da & (~7));
  doff = ((mlib_addr) da & 7);
  dend = da + dsize - 1;
  dend2 = dend - 7;

  /* calculate the src's offset over dst */
  if (cmask == 2) {
    off = soff / 2 - doff;
  }
  else {
    off = (soff + 1) / 2 - doff;
  }

  if (((cmask == 2) && (soff % 2 == 0)) || ((cmask == 1) && (soff % 2 != 0))) { /* extract even bytes */

    if (off == 0) {                         /* src and dst have same alignment */

      /* generate edge mask for the start point */
      emask = vis_edge8(da, dend);

      /* load 16 bytes */
      sd0 = *sp++;
      sd1 = *sp++;

      /* extract, including some garbage at the start point */
      CHANNELEXTRACT_U8_21L(sd0, sd1, dd0);

      /* store 8 bytes result */
      vis_pst_8(dd0, dp++, emask);

      if ((mlib_addr) dp <= (mlib_addr) dend2) {
        n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 8 + 1;

        /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
        for (i = 0; i < n; i++) {
          sd0 = *sp++;
          sd1 = *sp++;
          CHANNELEXTRACT_U8_21L(sd0, sd1, dd0);
          *dp++ = dd0;
        }
      }

      /* end point handling */
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge8(dp, dend);
        sd0 = *sp++;
        sd1 = *sp++;
        CHANNELEXTRACT_U8_21L(sd0, sd1, dd0);
        vis_pst_8(dd0, dp++, emask);
      }
    }
    else {
      vis_alignaddr((void *)0, off);

      /* generate edge mask for the start point */
      emask = vis_edge8(da, dend);

      if (off < 0) {
        /* load 16 bytes */
        sd2 = *sp++;
        sd3 = *sp++;

        /* extract and store 8 bytes */
        CHANNELEXTRACT_U8_21L(sd2, sd3, dd1);
        vis_pst_8(vis_faligndata(dd1, dd1), dp++, emask);
      }
      else {
        /* load 32 bytes */
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        sd3 = *sp++;

        /* extract and store 8 bytes */
        CHANNELEXTRACT_U8_21L(sd0, sd1, dd0);
        CHANNELEXTRACT_U8_21L(sd2, sd3, dd1);
        vis_pst_8(vis_faligndata(dd0, dd1), dp++, emask);
      }

      if ((mlib_addr) dp <= (mlib_addr) dend2) {
        n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 8 + 1;

        /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
        for (i = 0; i < n; i++) {
          dd0 = dd1;
          sd2 = *sp++;
          sd3 = *sp++;
          CHANNELEXTRACT_U8_21L(sd2, sd3, dd1);
          *dp++ = vis_faligndata(dd0, dd1);
        }
      }

      /* end point handling */
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge8(dp, dend);
        dd0 = dd1;
        sd2 = *sp++;
        sd3 = *sp++;
        CHANNELEXTRACT_U8_21L(sd2, sd3, dd1);
        vis_pst_8(vis_faligndata(dd0, dd1), dp++, emask);
      }
    }
  }
  else {                                    /* extract odd bytes */

    if (off == 0) {                         /* src and dst have same alignment */

      /* generate edge mask for the start point */
      emask = vis_edge8(da, dend);

      /* load 16 bytes, don't care the garbage at the start point */
      sd0 = *sp++;
      sd1 = *sp++;

      /* extract and store 8 bytes */
      CHANNELEXTRACT_U8_21R(sd0, sd1, dd0);
      vis_pst_8(dd0, dp++, emask);

      if ((mlib_addr) dp <= (mlib_addr) dend2) {
        n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 8 + 1;

        /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
        for (i = 0; i < n; i++) {
          sd0 = *sp++;
          sd1 = *sp++;
          CHANNELEXTRACT_U8_21R(sd0, sd1, dd0);
          *dp++ = dd0;
        }
      }

      /* end point handling */
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge8(dp, dend);
        sd0 = *sp++;
        sd1 = *sp++;
        CHANNELEXTRACT_U8_21R(sd0, sd1, dd0);
        vis_pst_8(dd0, dp++, emask);
      }
    }
    else {
      vis_alignaddr((void *)0, off);

      /* generate edge mask for the start point */
      emask = vis_edge8(da, dend);

      if (off < 0) {
        /* load 16 bytes */
        sd2 = *sp++;
        sd3 = *sp++;

        /* extract and store 8 bytes */
        CHANNELEXTRACT_U8_21R(sd2, sd3, dd1);
        vis_pst_8(vis_faligndata(dd1, dd1), dp++, emask);
      }
      else {
        /* load 32 bytes */
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        sd3 = *sp++;

        /* extract and store 8 bytes */
        CHANNELEXTRACT_U8_21R(sd0, sd1, dd0);
        CHANNELEXTRACT_U8_21R(sd2, sd3, dd1);
        vis_pst_8(vis_faligndata(dd0, dd1), dp++, emask);
      }

      if ((mlib_addr) dp <= (mlib_addr) dend2) {
        n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 8 + 1;

        /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
        for (i = 0; i < n; i++) {
          dd0 = dd1;
          sd2 = *sp++;
          sd3 = *sp++;
          CHANNELEXTRACT_U8_21R(sd2, sd3, dd1);
          *dp++ = vis_faligndata(dd0, dd1);
        }
      }

      /* end point handling */
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge8(dp, dend);
        dd0 = dd1;
        sd2 = *sp++;
        sd3 = *sp++;
        CHANNELEXTRACT_U8_21R(sd2, sd3, dd1);
        vis_pst_8(vis_faligndata(dd0, dd1), dp++, emask);
      }
    }
  }
}

/***************************************************************/
/* extract one channel from a 2-channel image.
 */

void mlib_v_ImageChannelExtract_U8_21(const mlib_u8 *src,
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

  for (j = 0; j < ysize; j++) {
    mlib_v_ImageChannelExtract_U8_21_D1(sa, da, xsize, cmask);
    sa = sl += slb;
    da = dl += dlb;
  }
}

/***************************************************************/
#define CHANNELEXTRACT_U8_31L(sd0, sd1, sd2, dd)                \
  sda = vis_fpmerge(vis_read_hi(sd0), vis_read_lo(sd1));        \
  sdb = vis_fpmerge(vis_read_lo(sd0), vis_read_hi(sd2));        \
  sdc = vis_fpmerge(vis_read_hi(sd1), vis_read_lo(sd2));        \
  sdd = vis_fpmerge(vis_read_hi(sda), vis_read_lo(sdb));        \
  sde = vis_fpmerge(vis_read_lo(sda), vis_read_hi(sdc));        \
  dd  = vis_fpmerge(vis_read_hi(sdd), vis_read_lo(sde))

/***************************************************************/
#define CHANNELEXTRACT_U8_31M(sd0, sd1, sd2, dd)                \
  sda = vis_fpmerge(vis_read_hi(sd0), vis_read_lo(sd1));        \
  sdb = vis_fpmerge(vis_read_lo(sd0), vis_read_hi(sd2));        \
  sdc = vis_fpmerge(vis_read_hi(sd1), vis_read_lo(sd2));        \
  sdd = vis_fpmerge(vis_read_hi(sda), vis_read_lo(sdb));        \
  sde = vis_fpmerge(vis_read_hi(sdb), vis_read_lo(sdc));        \
  dd  = vis_fpmerge(vis_read_lo(sdd), vis_read_hi(sde))

/***************************************************************/
#define CHANNELEXTRACT_U8_31R(sd0, sd1, sd2, dd)                \
  sda = vis_fpmerge(vis_read_hi(sd0), vis_read_lo(sd1));        \
  sdb = vis_fpmerge(vis_read_lo(sd0), vis_read_hi(sd2));        \
  sdc = vis_fpmerge(vis_read_hi(sd1), vis_read_lo(sd2));        \
  sdd = vis_fpmerge(vis_read_lo(sda), vis_read_hi(sdc));        \
  sde = vis_fpmerge(vis_read_hi(sdb), vis_read_lo(sdc));        \
  dd  = vis_fpmerge(vis_read_hi(sdd), vis_read_lo(sde))

/***************************************************************/
void mlib_v_ImageChannelExtract_U8_31_A8D1X8(const mlib_u8 *src,
                                             mlib_u8       *dst,
                                             mlib_s32      dsize,
                                             mlib_s32      cmask)
{
  mlib_d64 *sp, *dp;
  mlib_d64 sd0, sd1, sd2;
  mlib_d64 sda, sdb, sdc, sdd, sde;
  mlib_d64 dd;
  mlib_s32 i;

  sp = (mlib_d64 *) src;
  dp = (mlib_d64 *) dst;

  if (cmask == 4) {
#pragma pipeloop(0)
    for (i = 0; i < dsize / 8; i++) {
      sd0 = *sp++;
      sd1 = *sp++;
      sd2 = *sp++;
      CHANNELEXTRACT_U8_31L(sd0, sd1, sd2, dd);
      *dp++ = dd;
    }
  }
  else if (cmask == 2) {
#pragma pipeloop(0)
    for (i = 0; i < dsize / 8; i++) {
      sd0 = *sp++;
      sd1 = *sp++;
      sd2 = *sp++;
      CHANNELEXTRACT_U8_31M(sd0, sd1, sd2, dd);
      *dp++ = dd;
    }
  }
  else {
#pragma pipeloop(0)
    for (i = 0; i < dsize / 8; i++) {
      sd0 = *sp++;
      sd1 = *sp++;
      sd2 = *sp++;
      CHANNELEXTRACT_U8_31R(sd0, sd1, sd2, dd);
      *dp++ = dd;
    }
  }
}

/***************************************************************/
void mlib_v_ImageChannelExtract_U8_31_A8D2X8(const mlib_u8 *src,
                                             mlib_s32      slb,
                                             mlib_u8       *dst,
                                             mlib_s32      dlb,
                                             mlib_s32      xsize,
                                             mlib_s32      ysize,
                                             mlib_s32      cmask)
{
  mlib_d64 *sp, *dp;
  mlib_d64 *sl, *dl;
  mlib_d64 sd0, sd1, sd2;
  mlib_d64 sda, sdb, sdc, sdd, sde;
  mlib_d64 dd;
  mlib_s32 i, j;

  sp = sl = (mlib_d64 *) src;
  dp = dl = (mlib_d64 *) dst;

  if (cmask == 4) {
    for (j = 0; j < ysize; j++) {
#pragma pipeloop(0)
      for (i = 0; i < xsize / 8; i++) {
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        CHANNELEXTRACT_U8_31L(sd0, sd1, sd2, dd);
        *dp++ = dd;
      }

      sp = sl = (mlib_d64 *) ((mlib_u8 *) sl + slb);
      dp = dl = (mlib_d64 *) ((mlib_u8 *) dl + dlb);
    }
  }
  else if (cmask == 2) {
    for (j = 0; j < ysize; j++) {
#pragma pipeloop(0)
      for (i = 0; i < xsize / 8; i++) {
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        CHANNELEXTRACT_U8_31M(sd0, sd1, sd2, dd);
        *dp++ = dd;
      }

      sp = sl = (mlib_d64 *) ((mlib_u8 *) sl + slb);
      dp = dl = (mlib_d64 *) ((mlib_u8 *) dl + dlb);
    }
  }
  else {
    for (j = 0; j < ysize; j++) {
#pragma pipeloop(0)
      for (i = 0; i < xsize / 8; i++) {
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        CHANNELEXTRACT_U8_31R(sd0, sd1, sd2, dd);
        *dp++ = dd;
      }

      sp = sl = (mlib_d64 *) ((mlib_u8 *) sl + slb);
      dp = dl = (mlib_d64 *) ((mlib_u8 *) dl + dlb);
    }
  }
}

/***************************************************************/
void mlib_v_ImageChannelExtract_U8_31_D1(const mlib_u8 *src,
                                         mlib_u8       *dst,
                                         mlib_s32      dsize,
                                         mlib_s32      cmask)
{
  mlib_u8 *sa, *da;
  mlib_u8 *dend, *dend2;                              /* end points in dst */
  mlib_d64 *dp;                                       /* 8-byte aligned start points in dst */
  mlib_d64 *sp;                                       /* 8-byte aligned start point in src */
  mlib_d64 sd0, sd1, sd2;                             /* 8-byte source data */
  mlib_d64 sd3, sd4, sd5;
  mlib_d64 sda, sdb, sdc, sdd, sde;
  mlib_d64 dd0, dd1;
  mlib_s32 soff;                                      /* offset of address in src */
  mlib_s32 doff;                                      /* offset of address in dst */
  mlib_s32 off;                                       /* offset of src over dst */
  mlib_s32 emask;                                     /* edge mask */
  mlib_s32 i, n;

  sa = (void *)src;
  da = dst;

  /* prepare the source address */
  sp = (mlib_d64 *) ((mlib_addr) sa & (~7));
  soff = ((mlib_addr) sa & 7);

  /* prepare the destination addresses */
  dp = (mlib_d64 *) ((mlib_addr) da & (~7));
  doff = ((mlib_addr) da & 7);
  dend = da + dsize - 1;
  dend2 = dend - 7;

  /* calculate the src's offset over dst */
  if (cmask == 4) {
    off = soff / 3 - doff;
  }
  else if (cmask == 2) {
    off = (soff + 1) / 3 - doff;
  }
  else {
    off = (soff + 2) / 3 - doff;
  }

  if (((cmask == 4) && (soff % 3 == 0)) ||
      ((cmask == 2) && (soff % 3 == 2)) ||
      ((cmask == 1) && (soff % 3 == 1))) { /* extract left channel */

    if (off == 0) {                         /* src and dst have same alignment */

      /* generate edge mask for the start point */
      emask = vis_edge8(da, dend);

      /* load 16 bytes */
      sd0 = *sp++;
      sd1 = *sp++;
      sd2 = *sp++;

      /* extract, including some garbage at the start point */
      CHANNELEXTRACT_U8_31L(sd0, sd1, sd2, dd0);

      /* store 8 bytes result */
      vis_pst_8(dd0, dp++, emask);

      if ((mlib_addr) dp <= (mlib_addr) dend2) {
        n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 8 + 1;

        /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
        for (i = 0; i < n; i++) {
          sd0 = *sp++;
          sd1 = *sp++;
          sd2 = *sp++;
          CHANNELEXTRACT_U8_31L(sd0, sd1, sd2, dd0);
          *dp++ = dd0;
        }
      }

      /* end point handling */
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge8(dp, dend);
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        CHANNELEXTRACT_U8_31L(sd0, sd1, sd2, dd0);
        vis_pst_8(dd0, dp++, emask);
      }
    }
    else {
      vis_alignaddr((void *)0, off);

      /* generate edge mask for the start point */
      emask = vis_edge8(da, dend);

      if (off < 0) {
        /* load 24 bytes */
        sd3 = *sp++;
        sd4 = *sp++;
        sd5 = *sp++;

        /* extract and store 8 bytes */
        CHANNELEXTRACT_U8_31L(sd3, sd4, sd5, dd1);
        vis_pst_8(vis_faligndata(dd1, dd1), dp++, emask);
      }
      else {
        /* load 48 bytes */
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        sd3 = *sp++;
        sd4 = *sp++;
        sd5 = *sp++;

        /* extract and store 8 bytes */
        CHANNELEXTRACT_U8_31L(sd0, sd1, sd2, dd0);
        CHANNELEXTRACT_U8_31L(sd3, sd4, sd5, dd1);
        vis_pst_8(vis_faligndata(dd0, dd1), dp++, emask);
      }

      if ((mlib_addr) dp <= (mlib_addr) dend2) {
        n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 8 + 1;

        /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
        for (i = 0; i < n; i++) {
          dd0 = dd1;
          sd3 = *sp++;
          sd4 = *sp++;
          sd5 = *sp++;
          CHANNELEXTRACT_U8_31L(sd3, sd4, sd5, dd1);
          *dp++ = vis_faligndata(dd0, dd1);
        }
      }

      /* end point handling */
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge8(dp, dend);
        dd0 = dd1;
        sd3 = *sp++;
        sd4 = *sp++;
        sd5 = *sp++;
        CHANNELEXTRACT_U8_31L(sd3, sd4, sd5, dd1);
        vis_pst_8(vis_faligndata(dd0, dd1), dp++, emask);
      }
    }
  }
  else if (((cmask == 4) && (soff % 3 == 1)) ||
           ((cmask == 2) && (soff % 3 == 0)) ||
           ((cmask == 1) && (soff % 3 == 2))) {
    /* extract middle channel */

    if (off == 0) {                         /* src and dst have same alignment */

      /* generate edge mask for the start point */
      emask = vis_edge8(da, dend);

      /* load 16 bytes */
      sd0 = *sp++;
      sd1 = *sp++;
      sd2 = *sp++;

      /* extract, including some garbage at the start point */
      CHANNELEXTRACT_U8_31M(sd0, sd1, sd2, dd0);

      /* store 8 bytes result */
      vis_pst_8(dd0, dp++, emask);

      if ((mlib_addr) dp <= (mlib_addr) dend2) {
        n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 8 + 1;

        /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
        for (i = 0; i < n; i++) {
          sd0 = *sp++;
          sd1 = *sp++;
          sd2 = *sp++;
          CHANNELEXTRACT_U8_31M(sd0, sd1, sd2, dd0);
          *dp++ = dd0;
        }
      }

      /* end point handling */
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge8(dp, dend);
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        CHANNELEXTRACT_U8_31M(sd0, sd1, sd2, dd0);
        vis_pst_8(dd0, dp++, emask);
      }
    }
    else {
      vis_alignaddr((void *)0, off);

      /* generate edge mask for the start point */
      emask = vis_edge8(da, dend);

      if (off < 0) {
        /* load 24 bytes */
        sd3 = *sp++;
        sd4 = *sp++;
        sd5 = *sp++;

        /* extract and store 8 bytes */
        CHANNELEXTRACT_U8_31M(sd3, sd4, sd5, dd1);
        vis_pst_8(vis_faligndata(dd1, dd1), dp++, emask);
      }
      else {
        /* load 48 bytes */
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        sd3 = *sp++;
        sd4 = *sp++;
        sd5 = *sp++;

        /* extract and store 8 bytes */
        CHANNELEXTRACT_U8_31M(sd0, sd1, sd2, dd0);
        CHANNELEXTRACT_U8_31M(sd3, sd4, sd5, dd1);
        vis_pst_8(vis_faligndata(dd0, dd1), dp++, emask);
      }

      if ((mlib_addr) dp <= (mlib_addr) dend2) {
        n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 8 + 1;

        /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
        for (i = 0; i < n; i++) {
          dd0 = dd1;
          sd3 = *sp++;
          sd4 = *sp++;
          sd5 = *sp++;
          CHANNELEXTRACT_U8_31M(sd3, sd4, sd5, dd1);
          *dp++ = vis_faligndata(dd0, dd1);
        }
      }

      /* end point handling */
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge8(dp, dend);
        dd0 = dd1;
        sd3 = *sp++;
        sd4 = *sp++;
        sd5 = *sp++;
        CHANNELEXTRACT_U8_31M(sd3, sd4, sd5, dd1);
        vis_pst_8(vis_faligndata(dd0, dd1), dp++, emask);
      }
    }
  }
  else {                                    /* extract right channel */

    if (off == 0) {                         /* src and dst have same alignment */

      /* generate edge mask for the start point */
      emask = vis_edge8(da, dend);

      /* load 16 bytes */
      sd0 = *sp++;
      sd1 = *sp++;
      sd2 = *sp++;

      /* extract, including some garbage at the start point */
      CHANNELEXTRACT_U8_31R(sd0, sd1, sd2, dd0);

      /* store 8 bytes result */
      vis_pst_8(dd0, dp++, emask);

      if ((mlib_addr) dp <= (mlib_addr) dend2) {
        n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 8 + 1;

        /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
        for (i = 0; i < n; i++) {
          sd0 = *sp++;
          sd1 = *sp++;
          sd2 = *sp++;
          CHANNELEXTRACT_U8_31R(sd0, sd1, sd2, dd0);
          *dp++ = dd0;
        }
      }

      /* end point handling */
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge8(dp, dend);
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        CHANNELEXTRACT_U8_31R(sd0, sd1, sd2, dd0);
        vis_pst_8(dd0, dp++, emask);
      }
    }
    else {
      vis_alignaddr((void *)0, off);

      /* generate edge mask for the start point */
      emask = vis_edge8(da, dend);

      if (off < 0) {
        /* load 24 bytes */
        sd3 = *sp++;
        sd4 = *sp++;
        sd5 = *sp++;

        /* extract and store 8 bytes */
        CHANNELEXTRACT_U8_31R(sd3, sd4, sd5, dd1);
        vis_pst_8(vis_faligndata(dd1, dd1), dp++, emask);
      }
      else {
        /* load 48 bytes */
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        sd3 = *sp++;
        sd4 = *sp++;
        sd5 = *sp++;

        /* extract and store 8 bytes */
        CHANNELEXTRACT_U8_31R(sd0, sd1, sd2, dd0);
        CHANNELEXTRACT_U8_31R(sd3, sd4, sd5, dd1);
        vis_pst_8(vis_faligndata(dd0, dd1), dp++, emask);
      }

      if ((mlib_addr) dp <= (mlib_addr) dend2) {
        n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 8 + 1;

        /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
        for (i = 0; i < n; i++) {
          dd0 = dd1;
          sd3 = *sp++;
          sd4 = *sp++;
          sd5 = *sp++;
          CHANNELEXTRACT_U8_31R(sd3, sd4, sd5, dd1);
          *dp++ = vis_faligndata(dd0, dd1);
        }
      }

      /* end point handling */
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge8(dp, dend);
        dd0 = dd1;
        sd3 = *sp++;
        sd4 = *sp++;
        sd5 = *sp++;
        CHANNELEXTRACT_U8_31R(sd3, sd4, sd5, dd1);
        vis_pst_8(vis_faligndata(dd0, dd1), dp++, emask);
      }
    }
  }
}

/***************************************************************/
void mlib_v_ImageChannelExtract_U8_31(const mlib_u8 *src,
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

  for (j = 0; j < ysize; j++) {
    mlib_v_ImageChannelExtract_U8_31_D1(sa, da, xsize, cmask);
    sa = sl += slb;
    da = dl += dlb;
  }
}

/***************************************************************/
#define CHANNELEXTRACT_U8_41L(sd0, sd1, sd2, sd3, dd)           \
  sda = vis_fpmerge(vis_read_hi(sd0), vis_read_hi(sd2));        \
  sdb = vis_fpmerge(vis_read_lo(sd0), vis_read_lo(sd2));        \
  sdc = vis_fpmerge(vis_read_hi(sd1), vis_read_hi(sd3));        \
  sdd = vis_fpmerge(vis_read_lo(sd1), vis_read_lo(sd3));        \
  sde = vis_fpmerge(vis_read_hi(sda), vis_read_hi(sdc));        \
  sdf = vis_fpmerge(vis_read_hi(sdb), vis_read_hi(sdd));        \
  dd  = vis_fpmerge(vis_read_hi(sde), vis_read_hi(sdf))

/***************************************************************/
#define CHANNELEXTRACT_U8_41ML(sd0, sd1, sd2, sd3, dd)          \
  sda = vis_fpmerge(vis_read_hi(sd0), vis_read_hi(sd2));        \
  sdb = vis_fpmerge(vis_read_lo(sd0), vis_read_lo(sd2));        \
  sdc = vis_fpmerge(vis_read_hi(sd1), vis_read_hi(sd3));        \
  sdd = vis_fpmerge(vis_read_lo(sd1), vis_read_lo(sd3));        \
  sde = vis_fpmerge(vis_read_hi(sda), vis_read_hi(sdc));        \
  sdf = vis_fpmerge(vis_read_hi(sdb), vis_read_hi(sdd));        \
  dd  = vis_fpmerge(vis_read_lo(sde), vis_read_lo(sdf))

/***************************************************************/
#define CHANNELEXTRACT_U8_41MR(sd0, sd1, sd2, sd3, dd)          \
  sda = vis_fpmerge(vis_read_hi(sd0), vis_read_hi(sd2));        \
  sdb = vis_fpmerge(vis_read_lo(sd0), vis_read_lo(sd2));        \
  sdc = vis_fpmerge(vis_read_hi(sd1), vis_read_hi(sd3));        \
  sdd = vis_fpmerge(vis_read_lo(sd1), vis_read_lo(sd3));        \
  sde = vis_fpmerge(vis_read_lo(sda), vis_read_lo(sdc));        \
  sdf = vis_fpmerge(vis_read_lo(sdb), vis_read_lo(sdd));        \
  dd  = vis_fpmerge(vis_read_hi(sde), vis_read_hi(sdf))

/***************************************************************/
#define CHANNELEXTRACT_U8_41R(sd0, sd1, sd2, sd3, dd)           \
  sda = vis_fpmerge(vis_read_hi(sd0), vis_read_hi(sd2));        \
  sdb = vis_fpmerge(vis_read_lo(sd0), vis_read_lo(sd2));        \
  sdc = vis_fpmerge(vis_read_hi(sd1), vis_read_hi(sd3));        \
  sdd = vis_fpmerge(vis_read_lo(sd1), vis_read_lo(sd3));        \
  sde = vis_fpmerge(vis_read_lo(sda), vis_read_lo(sdc));        \
  sdf = vis_fpmerge(vis_read_lo(sdb), vis_read_lo(sdd));        \
  dd  = vis_fpmerge(vis_read_lo(sde), vis_read_lo(sdf))

/***************************************************************/
void mlib_v_ImageChannelExtract_U8_41_A8D1X8(const mlib_u8 *src,
                                             mlib_u8       *dst,
                                             mlib_s32      dsize,
                                             mlib_s32      cmask)
{
  mlib_d64 *sp, *dp;
  mlib_d64 sd0, sd1, sd2, sd3;
  mlib_d64 sda, sdb, sdc, sdd, sde, sdf;
  mlib_d64 dd;
  mlib_s32 i;

  sp = (mlib_d64 *) src;
  dp = (mlib_d64 *) dst;

  if (cmask == 8) {
#pragma pipeloop(0)
    for (i = 0; i < dsize / 8; i++) {
      sd0 = *sp++;
      sd1 = *sp++;
      sd2 = *sp++;
      sd3 = *sp++;
      CHANNELEXTRACT_U8_41L(sd0, sd1, sd2, sd3, dd);
      *dp++ = dd;
    }
  }
  else if (cmask == 4) {
#pragma pipeloop(0)
    for (i = 0; i < dsize / 8; i++) {
      sd0 = *sp++;
      sd1 = *sp++;
      sd2 = *sp++;
      sd3 = *sp++;
      CHANNELEXTRACT_U8_41ML(sd0, sd1, sd2, sd3, dd);
      *dp++ = dd;
    }
  }
  else if (cmask == 2) {
#pragma pipeloop(0)
    for (i = 0; i < dsize / 8; i++) {
      sd0 = *sp++;
      sd1 = *sp++;
      sd2 = *sp++;
      sd3 = *sp++;
      CHANNELEXTRACT_U8_41MR(sd0, sd1, sd2, sd3, dd);
      *dp++ = dd;
    }
  }
  else {
#pragma pipeloop(0)
    for (i = 0; i < dsize / 8; i++) {
      sd0 = *sp++;
      sd1 = *sp++;
      sd2 = *sp++;
      sd3 = *sp++;
      CHANNELEXTRACT_U8_41R(sd0, sd1, sd2, sd3, dd);
      *dp++ = dd;
    }
  }
}

/***************************************************************/
void mlib_v_ImageChannelExtract_U8_41_A8D2X8(const mlib_u8 *src,
                                             mlib_s32      slb,
                                             mlib_u8       *dst,
                                             mlib_s32      dlb,
                                             mlib_s32      xsize,
                                             mlib_s32      ysize,
                                             mlib_s32      cmask)
{
  mlib_d64 *sp, *dp;
  mlib_d64 *sl, *dl;
  mlib_d64 sd0, sd1, sd2, sd3;
  mlib_d64 sda, sdb, sdc, sdd, sde, sdf;
  mlib_d64 dd;
  mlib_s32 i, j;

  sp = sl = (mlib_d64 *) src;
  dp = dl = (mlib_d64 *) dst;

  if (cmask == 8) {
    for (j = 0; j < ysize; j++) {
#pragma pipeloop(0)
      for (i = 0; i < xsize / 8; i++) {
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        sd3 = *sp++;
        CHANNELEXTRACT_U8_41L(sd0, sd1, sd2, sd3, dd);
        *dp++ = dd;
      }

      sp = sl = (mlib_d64 *) ((mlib_u8 *) sl + slb);
      dp = dl = (mlib_d64 *) ((mlib_u8 *) dl + dlb);
    }
  }
  else if (cmask == 4) {
    for (j = 0; j < ysize; j++) {
#pragma pipeloop(0)
      for (i = 0; i < xsize / 8; i++) {
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        sd3 = *sp++;
        CHANNELEXTRACT_U8_41ML(sd0, sd1, sd2, sd3, dd);
        *dp++ = dd;
      }

      sp = sl = (mlib_d64 *) ((mlib_u8 *) sl + slb);
      dp = dl = (mlib_d64 *) ((mlib_u8 *) dl + dlb);
    }
  }
  else if (cmask == 2) {
    for (j = 0; j < ysize; j++) {
#pragma pipeloop(0)
      for (i = 0; i < xsize / 8; i++) {
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        sd3 = *sp++;
        CHANNELEXTRACT_U8_41MR(sd0, sd1, sd2, sd3, dd);
        *dp++ = dd;
      }

      sp = sl = (mlib_d64 *) ((mlib_u8 *) sl + slb);
      dp = dl = (mlib_d64 *) ((mlib_u8 *) dl + dlb);
    }
  }
  else {
    for (j = 0; j < ysize; j++) {
#pragma pipeloop(0)
      for (i = 0; i < xsize / 8; i++) {
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        sd3 = *sp++;
        CHANNELEXTRACT_U8_41R(sd0, sd1, sd2, sd3, dd);
        *dp++ = dd;
      }

      sp = sl = (mlib_d64 *) ((mlib_u8 *) sl + slb);
      dp = dl = (mlib_d64 *) ((mlib_u8 *) dl + dlb);
    }
  }
}

/***************************************************************/
void mlib_v_ImageChannelExtract_U8_41_D1(const mlib_u8 *src,
                                         mlib_u8       *dst,
                                         mlib_s32      dsize,
                                         mlib_s32      cmask)
{
  mlib_u8 *sa, *da;
  mlib_u8 *dend, *dend2;                              /* end points in dst */
  mlib_d64 *dp;                                       /* 8-byte aligned start points in dst */
  mlib_d64 *sp;                                       /* 8-byte aligned start point in src */
  mlib_d64 sd0, sd1, sd2, sd3;                        /* 8-byte source data */
  mlib_d64 sd4, sd5, sd6, sd7;
  mlib_d64 sda, sdb, sdc, sdd;
  mlib_d64 sde, sdf;
  mlib_d64 dd0, dd1;
  mlib_s32 soff;                                      /* offset of address in src */
  mlib_s32 doff;                                      /* offset of address in dst */
  mlib_s32 off;                                       /* offset of src over dst */
  mlib_s32 emask;                                     /* edge mask */
  mlib_s32 i, n;

  sa = (void *)src;
  da = dst;

  /* prepare the source address */
  sp = (mlib_d64 *) ((mlib_addr) sa & (~7));
  soff = ((mlib_addr) sa & 7);

  /* prepare the destination addresses */
  dp = (mlib_d64 *) ((mlib_addr) da & (~7));
  doff = ((mlib_addr) da & 7);
  dend = da + dsize - 1;
  dend2 = dend - 7;

  /* calculate the src's offset over dst */
  if (cmask == 8) {
    off = soff / 4 - doff;
  }
  else if (cmask == 4) {
    off = (soff + 1) / 4 - doff;
  }
  else if (cmask == 2) {
    off = (soff + 2) / 4 - doff;
  }
  else {
    off = (soff + 3) / 4 - doff;
  }

  if (((cmask == 8) && (soff % 4 == 0)) ||
      ((cmask == 4) && (soff % 4 == 3)) ||
      ((cmask == 2) && (soff % 4 == 2)) ||
      ((cmask == 1) && (soff % 4 == 1))) { /* extract left channel */

    if (off == 0) {                         /* src and dst have same alignment */

      /* generate edge mask for the start point */
      emask = vis_edge8(da, dend);

      /* load 16 bytes */
      sd0 = *sp++;
      sd1 = *sp++;
      sd2 = *sp++;
      sd3 = *sp++;

      /* extract, including some garbage at the start point */
      CHANNELEXTRACT_U8_41L(sd0, sd1, sd2, sd3, dd0);

      /* store 8 bytes result */
      vis_pst_8(dd0, dp++, emask);

      if ((mlib_addr) dp <= (mlib_addr) dend2) {
        n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 8 + 1;

        /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
        for (i = 0; i < n; i++) {
          sd0 = *sp++;
          sd1 = *sp++;
          sd2 = *sp++;
          sd3 = *sp++;
          CHANNELEXTRACT_U8_41L(sd0, sd1, sd2, sd3, dd0);
          *dp++ = dd0;
        }
      }

      /* end point handling */
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge8(dp, dend);
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        sd3 = *sp++;
        CHANNELEXTRACT_U8_41L(sd0, sd1, sd2, sd3, dd0);
        vis_pst_8(dd0, dp++, emask);
      }
    }
    else {
      vis_alignaddr((void *)0, off);

      /* generate edge mask for the start point */
      emask = vis_edge8(da, dend);

      if (off < 0) {
        /* load 24 bytes */
        sd4 = *sp++;
        sd5 = *sp++;
        sd6 = *sp++;
        sd7 = *sp++;

        /* extract and store 8 bytes */
        CHANNELEXTRACT_U8_41L(sd4, sd5, sd6, sd7, dd1);
        vis_pst_8(vis_faligndata(dd1, dd1), dp++, emask);
      }
      else {
        /* load 48 bytes */
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        sd3 = *sp++;
        sd4 = *sp++;
        sd5 = *sp++;
        sd6 = *sp++;
        sd7 = *sp++;

        /* extract and store 8 bytes */
        CHANNELEXTRACT_U8_41L(sd0, sd1, sd2, sd3, dd0);
        CHANNELEXTRACT_U8_41L(sd4, sd5, sd6, sd7, dd1);
        vis_pst_8(vis_faligndata(dd0, dd1), dp++, emask);
      }

      if ((mlib_addr) dp <= (mlib_addr) dend2) {
        n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 8 + 1;

        /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
        for (i = 0; i < n; i++) {
          dd0 = dd1;
          sd4 = *sp++;
          sd5 = *sp++;
          sd6 = *sp++;
          sd7 = *sp++;
          CHANNELEXTRACT_U8_41L(sd4, sd5, sd6, sd7, dd1);
          *dp++ = vis_faligndata(dd0, dd1);
        }
      }

      /* end point handling */
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge8(dp, dend);
        dd0 = dd1;
        sd4 = *sp++;
        sd5 = *sp++;
        sd6 = *sp++;
        sd7 = *sp++;
        CHANNELEXTRACT_U8_41L(sd4, sd5, sd6, sd7, dd1);
        vis_pst_8(vis_faligndata(dd0, dd1), dp++, emask);
      }
    }
  }
  else if (((cmask == 8) && (soff % 4 == 1)) ||
           ((cmask == 4) && (soff % 4 == 0)) ||
           ((cmask == 2) && (soff % 4 == 3)) ||
           ((cmask == 1) && (soff % 4 == 2))) {
    /* extract middle left channel */

    if (off == 0) {                         /* src and dst have same alignment */

      /* generate edge mask for the start point */
      emask = vis_edge8(da, dend);

      /* load 16 bytes */
      sd0 = *sp++;
      sd1 = *sp++;
      sd2 = *sp++;
      sd3 = *sp++;

      /* extract, including some garbage at the start point */
      CHANNELEXTRACT_U8_41ML(sd0, sd1, sd2, sd3, dd0);

      /* store 8 bytes result */
      vis_pst_8(dd0, dp++, emask);

      if ((mlib_addr) dp <= (mlib_addr) dend2) {
        n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 8 + 1;

        /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
        for (i = 0; i < n; i++) {
          sd0 = *sp++;
          sd1 = *sp++;
          sd2 = *sp++;
          sd3 = *sp++;
          CHANNELEXTRACT_U8_41ML(sd0, sd1, sd2, sd3, dd0);
          *dp++ = dd0;
        }
      }

      /* end point handling */
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge8(dp, dend);
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        sd3 = *sp++;
        CHANNELEXTRACT_U8_41ML(sd0, sd1, sd2, sd3, dd0);
        vis_pst_8(dd0, dp++, emask);
      }
    }
    else {
      vis_alignaddr((void *)0, off);

      /* generate edge mask for the start point */
      emask = vis_edge8(da, dend);

      if (off < 0) {
        /* load 24 bytes */
        sd4 = *sp++;
        sd5 = *sp++;
        sd6 = *sp++;
        sd7 = *sp++;

        /* extract and store 8 bytes */
        CHANNELEXTRACT_U8_41ML(sd4, sd5, sd6, sd7, dd1);
        vis_pst_8(vis_faligndata(dd1, dd1), dp++, emask);
      }
      else {
        /* load 48 bytes */
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        sd3 = *sp++;
        sd4 = *sp++;
        sd5 = *sp++;
        sd6 = *sp++;
        sd7 = *sp++;

        /* extract and store 8 bytes */
        CHANNELEXTRACT_U8_41ML(sd0, sd1, sd2, sd3, dd0);
        CHANNELEXTRACT_U8_41ML(sd4, sd5, sd6, sd7, dd1);
        vis_pst_8(vis_faligndata(dd0, dd1), dp++, emask);
      }

      if ((mlib_addr) dp <= (mlib_addr) dend2) {
        n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 8 + 1;

        /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
        for (i = 0; i < n; i++) {
          dd0 = dd1;
          sd4 = *sp++;
          sd5 = *sp++;
          sd6 = *sp++;
          sd7 = *sp++;
          CHANNELEXTRACT_U8_41ML(sd4, sd5, sd6, sd7, dd1);
          *dp++ = vis_faligndata(dd0, dd1);
        }
      }

      /* end point handling */
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge8(dp, dend);
        dd0 = dd1;
        sd4 = *sp++;
        sd5 = *sp++;
        sd6 = *sp++;
        sd7 = *sp++;
        CHANNELEXTRACT_U8_41ML(sd4, sd5, sd6, sd7, dd1);
        vis_pst_8(vis_faligndata(dd0, dd1), dp++, emask);
      }
    }
  }
  else if (((cmask == 8) && (soff % 4 == 2)) ||
           ((cmask == 4) && (soff % 4 == 1)) ||
           ((cmask == 2) && (soff % 4 == 0)) ||
           ((cmask == 1) && (soff % 4 == 3))) { /* extract middle right channel */

    if (off == 0) {                         /* src and dst have same alignment */

      /* generate edge mask for the start point */
      emask = vis_edge8(da, dend);

      /* load 16 bytes */
      sd0 = *sp++;
      sd1 = *sp++;
      sd2 = *sp++;
      sd3 = *sp++;

      /* extract, including some garbage at the start point */
      CHANNELEXTRACT_U8_41MR(sd0, sd1, sd2, sd3, dd0);

      /* store 8 bytes result */
      vis_pst_8(dd0, dp++, emask);

      if ((mlib_addr) dp <= (mlib_addr) dend2) {
        n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 8 + 1;

        /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
        for (i = 0; i < n; i++) {
          sd0 = *sp++;
          sd1 = *sp++;
          sd2 = *sp++;
          sd3 = *sp++;
          CHANNELEXTRACT_U8_41MR(sd0, sd1, sd2, sd3, dd0);
          *dp++ = dd0;
        }
      }

      /* end point handling */
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge8(dp, dend);
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        sd3 = *sp++;
        CHANNELEXTRACT_U8_41MR(sd0, sd1, sd2, sd3, dd0);
        vis_pst_8(dd0, dp++, emask);
      }
    }
    else {
      vis_alignaddr((void *)0, off);

      /* generate edge mask for the start point */
      emask = vis_edge8(da, dend);

      if (off < 0) {
        /* load 24 bytes */
        sd4 = *sp++;
        sd5 = *sp++;
        sd6 = *sp++;
        sd7 = *sp++;

        /* extract and store 8 bytes */
        CHANNELEXTRACT_U8_41MR(sd4, sd5, sd6, sd7, dd1);
        vis_pst_8(vis_faligndata(dd1, dd1), dp++, emask);
      }
      else {
        /* load 48 bytes */
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        sd3 = *sp++;
        sd4 = *sp++;
        sd5 = *sp++;
        sd6 = *sp++;
        sd7 = *sp++;

        /* extract and store 8 bytes */
        CHANNELEXTRACT_U8_41MR(sd0, sd1, sd2, sd3, dd0);
        CHANNELEXTRACT_U8_41MR(sd4, sd5, sd6, sd7, dd1);
        vis_pst_8(vis_faligndata(dd0, dd1), dp++, emask);
      }

      if ((mlib_addr) dp <= (mlib_addr) dend2) {
        n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 8 + 1;

        /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
        for (i = 0; i < n; i++) {
          dd0 = dd1;
          sd4 = *sp++;
          sd5 = *sp++;
          sd6 = *sp++;
          sd7 = *sp++;
          CHANNELEXTRACT_U8_41MR(sd4, sd5, sd6, sd7, dd1);
          *dp++ = vis_faligndata(dd0, dd1);
        }
      }

      /* end point handling */
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge8(dp, dend);
        dd0 = dd1;
        sd4 = *sp++;
        sd5 = *sp++;
        sd6 = *sp++;
        sd7 = *sp++;
        CHANNELEXTRACT_U8_41MR(sd4, sd5, sd6, sd7, dd1);
        vis_pst_8(vis_faligndata(dd0, dd1), dp++, emask);
      }
    }
  }
  else {                                    /* extract right channel */
    if (off == 0) {                         /* src and dst have same alignment */

      /* generate edge mask for the start point */
      emask = vis_edge8(da, dend);

      /* load 16 bytes */
      sd0 = *sp++;
      sd1 = *sp++;
      sd2 = *sp++;
      sd3 = *sp++;

      /* extract, including some garbage at the start point */
      CHANNELEXTRACT_U8_41R(sd0, sd1, sd2, sd3, dd0);

      /* store 8 bytes result */
      vis_pst_8(dd0, dp++, emask);

      if ((mlib_addr) dp <= (mlib_addr) dend2) {
        n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 8 + 1;

        /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
        for (i = 0; i < n; i++) {
          sd0 = *sp++;
          sd1 = *sp++;
          sd2 = *sp++;
          sd3 = *sp++;
          CHANNELEXTRACT_U8_41R(sd0, sd1, sd2, sd3, dd0);
          *dp++ = dd0;
        }
      }

      /* end point handling */
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge8(dp, dend);
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        sd3 = *sp++;
        CHANNELEXTRACT_U8_41R(sd0, sd1, sd2, sd3, dd0);
        vis_pst_8(dd0, dp++, emask);
      }
    }
    else {
      vis_alignaddr((void *)0, off);

      /* generate edge mask for the start point */
      emask = vis_edge8(da, dend);

      if (off < 0) {
        /* load 24 bytes */
        sd4 = *sp++;
        sd5 = *sp++;
        sd6 = *sp++;
        sd7 = *sp++;

        /* extract and store 8 bytes */
        CHANNELEXTRACT_U8_41R(sd4, sd5, sd6, sd7, dd1);
        vis_pst_8(vis_faligndata(dd1, dd1), dp++, emask);
      }
      else {
        /* load 48 bytes */
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        sd3 = *sp++;
        sd4 = *sp++;
        sd5 = *sp++;
        sd6 = *sp++;
        sd7 = *sp++;

        /* extract and store 8 bytes */
        CHANNELEXTRACT_U8_41R(sd0, sd1, sd2, sd3, dd0);
        CHANNELEXTRACT_U8_41R(sd4, sd5, sd6, sd7, dd1);
        vis_pst_8(vis_faligndata(dd0, dd1), dp++, emask);
      }

      if ((mlib_addr) dp <= (mlib_addr) dend2) {
        n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 8 + 1;

        /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
        for (i = 0; i < n; i++) {
          dd0 = dd1;
          sd4 = *sp++;
          sd5 = *sp++;
          sd6 = *sp++;
          sd7 = *sp++;
          CHANNELEXTRACT_U8_41R(sd4, sd5, sd6, sd7, dd1);
          *dp++ = vis_faligndata(dd0, dd1);
        }
      }

      /* end point handling */
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge8(dp, dend);
        dd0 = dd1;
        sd4 = *sp++;
        sd5 = *sp++;
        sd6 = *sp++;
        sd7 = *sp++;
        CHANNELEXTRACT_U8_41R(sd4, sd5, sd6, sd7, dd1);
        vis_pst_8(vis_faligndata(dd0, dd1), dp++, emask);
      }
    }
  }
}

/***************************************************************/
void mlib_v_ImageChannelExtract_U8_41(const mlib_u8 *src,
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

  for (j = 0; j < ysize; j++) {
    mlib_v_ImageChannelExtract_U8_41_D1(sa, da, xsize, cmask);
    sa = sl += slb;
    da = dl += dlb;
  }
}

/***************************************************************/
#define CHANNELEXTRACT_S16_21L(sd0, sd1, dd)                    \
  sda = vis_fpmerge(vis_read_hi(sd0), vis_read_hi(sd1));        \
  sdb = vis_fpmerge(vis_read_lo(sd0), vis_read_lo(sd1));        \
  sdc = vis_fpmerge(vis_read_hi(sda), vis_read_hi(sdb));        \
  dd  = vis_fpmerge(vis_read_hi(sdc), vis_read_lo(sdc))

/***************************************************************/
#define CHANNELEXTRACT_S16_21R(sd0, sd1, dd)                    \
  sda = vis_fpmerge(vis_read_hi(sd0), vis_read_hi(sd1));        \
  sdb = vis_fpmerge(vis_read_lo(sd0), vis_read_lo(sd1));        \
  sdc = vis_fpmerge(vis_read_lo(sda), vis_read_lo(sdb));        \
  dd  = vis_fpmerge(vis_read_hi(sdc), vis_read_lo(sdc))

/***************************************************************/
/* extract one channel from a 2-channel image.
 * both source and destination image data are 8-byte aligned.
 * dsize is multiple of 4.
 */

void mlib_v_ImageChannelExtract_S16_21_A8D1X4(const mlib_s16 *src,
                                              mlib_s16       *dst,
                                              mlib_s32       dsize,
                                              mlib_s32       cmask)
{
  mlib_d64 *sp, *dp;
  mlib_d64 sd0, sd1;
  mlib_d64 sda, sdb, sdc;
  mlib_d64 dd;
  mlib_s32 i;

  sp = (mlib_d64 *) src;
  dp = (mlib_d64 *) dst;

  if (cmask == 2) {
#pragma pipeloop(0)
    for (i = 0; i < dsize / 4; i++) {
      sd0 = *sp++;
      sd1 = *sp++;
      CHANNELEXTRACT_S16_21L(sd0, sd1, dd);
      *dp++ = dd;
    }
  }
  else {
#pragma pipeloop(0)
    for (i = 0; i < dsize / 4; i++) {
      sd0 = *sp++;
      sd1 = *sp++;
      CHANNELEXTRACT_S16_21R(sd0, sd1, dd);
      *dp++ = dd;
    }
  }
}

/***************************************************************/
void mlib_v_ImageChannelExtract_S16_21_A8D2X4(const mlib_s16 *src,
                                              mlib_s32       slb,
                                              mlib_s16       *dst,
                                              mlib_s32       dlb,
                                              mlib_s32       xsize,
                                              mlib_s32       ysize,
                                              mlib_s32       cmask)
{
  mlib_d64 *sp, *dp;
  mlib_d64 *sl, *dl;
  mlib_d64 sd0, sd1;
  mlib_d64 sda, sdb, sdc;
  mlib_d64 dd;
  mlib_s32 i, j;

  sp = sl = (mlib_d64 *) src;
  dp = dl = (mlib_d64 *) dst;

  if (cmask == 2) {
    for (j = 0; j < ysize; j++) {
#pragma pipeloop(0)
      for (i = 0; i < xsize / 4; i++) {
        sd0 = *sp++;
        sd1 = *sp++;
        CHANNELEXTRACT_S16_21L(sd0, sd1, dd);
        *dp++ = dd;
      }

      sp = sl = (mlib_d64 *) ((mlib_u8 *) sl + slb);
      dp = dl = (mlib_d64 *) ((mlib_u8 *) dl + dlb);
    }
  }
  else {
    for (j = 0; j < ysize; j++) {
#pragma pipeloop(0)
      for (i = 0; i < xsize / 4; i++) {
        sd0 = *sp++;
        sd1 = *sp++;
        CHANNELEXTRACT_S16_21R(sd0, sd1, dd);
        *dp++ = dd;
      }

      sp = sl = (mlib_d64 *) ((mlib_u8 *) sl + slb);
      dp = dl = (mlib_d64 *) ((mlib_u8 *) dl + dlb);
    }
  }
}

/***************************************************************/
void mlib_v_ImageChannelExtract_S16_21_D1(const mlib_s16 *src,
                                          mlib_s16       *dst,
                                          mlib_s32       dsize,
                                          mlib_s32       cmask)
{
  mlib_s16 *sa, *da;
  mlib_s16 *dend, *dend2;                             /* end points in dst */
  mlib_d64 *dp;                                       /* 8-byte aligned start points in dst */
  mlib_d64 *sp;                                       /* 8-byte aligned start point in src */
  mlib_d64 sd0, sd1, sd2, sd3;                        /* 8-byte source data */
  mlib_d64 sda, sdb, sdc;
  mlib_d64 dd0, dd1;
  mlib_s32 soff;                                      /* offset of address in src */
  mlib_s32 doff;                                      /* offset of address in dst */
  mlib_s32 off;                                       /* offset of dst over src */
  mlib_s32 emask;                                     /* edge mask */
  mlib_s32 i, n;

  sa = (void *)src;
  da = dst;

  /* prepare the source address */
  sp = (mlib_d64 *) ((mlib_addr) sa & (~7));
  soff = ((mlib_addr) sa & 7);

  /* prepare the destination addresses */
  dp = (mlib_d64 *) ((mlib_addr) da & (~7));
  doff = ((mlib_addr) da & 7);
  dend = da + dsize - 1;
  dend2 = dend - 3;

  /* calculate the src's offset over dst */
  if (cmask == 2) {
    off = (soff / 4) * 2 - doff;
  }
  else {
    off = ((soff + 3) / 4) * 2 - doff;
  }

  if (((cmask == 2) && (soff % 4 == 0)) || ((cmask == 1) && (soff % 4 != 0))) { /* extract even words */

    if (off == 0) {                         /* src and dst have same alignment */

      /* generate edge mask for the start point */
      emask = vis_edge16(da, dend);

      /* load 16 bytes */
      sd0 = *sp++;
      sd1 = *sp++;

      /* extract, including some garbage at the start point */
      CHANNELEXTRACT_S16_21L(sd0, sd1, dd0);

      /* store 8 bytes result */
      vis_pst_16(dd0, dp++, emask);

      if ((mlib_addr) dp <= (mlib_addr) dend2) {
        n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 8 + 1;

        /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
        for (i = 0; i < n; i++) {
          sd0 = *sp++;
          sd1 = *sp++;
          CHANNELEXTRACT_S16_21L(sd0, sd1, dd0);
          *dp++ = dd0;
        }
      }

      /* end point handling */
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge16(dp, dend);
        sd0 = *sp++;
        sd1 = *sp++;
        CHANNELEXTRACT_S16_21L(sd0, sd1, dd0);
        vis_pst_16(dd0, dp++, emask);
      }
    }
    else {
      vis_alignaddr((void *)0, off);

      /* generate edge mask for the start point */
      emask = vis_edge16(da, dend);

      if (off < 0) {
        /* load 16 bytes */
        sd2 = *sp++;
        sd3 = *sp++;

        /* extract and store 8 bytes */
        CHANNELEXTRACT_S16_21L(sd2, sd3, dd1);
        vis_pst_16(vis_faligndata(dd1, dd1), dp++, emask);
      }
      else {
        /* load 32 bytes */
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        sd3 = *sp++;

        /* extract and store 8 bytes */
        CHANNELEXTRACT_S16_21L(sd0, sd1, dd0);
        CHANNELEXTRACT_S16_21L(sd2, sd3, dd1);
        vis_pst_16(vis_faligndata(dd0, dd1), dp++, emask);
      }

      if ((mlib_addr) dp <= (mlib_addr) dend2) {
        n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 8 + 1;

        /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
        for (i = 0; i < n; i++) {
          dd0 = dd1;
          sd2 = *sp++;
          sd3 = *sp++;
          CHANNELEXTRACT_S16_21L(sd2, sd3, dd1);
          *dp++ = vis_faligndata(dd0, dd1);
        }
      }

      /* end point handling */
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge16(dp, dend);
        dd0 = dd1;
        sd2 = *sp++;
        sd3 = *sp++;
        CHANNELEXTRACT_S16_21L(sd2, sd3, dd1);
        vis_pst_16(vis_faligndata(dd0, dd1), dp++, emask);
      }
    }
  }
  else {                                    /* extract odd words */

    if (off == 0) {                         /* src and dst have same alignment */

      /* generate edge mask for the start point */
      emask = vis_edge16(da, dend);

      /* load 16 bytes, don't care the garbage at the start point */
      sd0 = *sp++;
      sd1 = *sp++;

      /* extract and store 8 bytes */
      CHANNELEXTRACT_S16_21R(sd0, sd1, dd0);
      vis_pst_16(dd0, dp++, emask);

      if ((mlib_addr) dp <= (mlib_addr) dend2) {
        n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 8 + 1;

        /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
        for (i = 0; i < n; i++) {
          sd0 = *sp++;
          sd1 = *sp++;
          CHANNELEXTRACT_S16_21R(sd0, sd1, dd0);
          *dp++ = dd0;
        }
      }

      /* end point handling */
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge16(dp, dend);
        sd0 = *sp++;
        sd1 = *sp++;
        CHANNELEXTRACT_S16_21R(sd0, sd1, dd0);
        vis_pst_16(dd0, dp++, emask);
      }
    }
    else {
      vis_alignaddr((void *)0, off);

      /* generate edge mask for the start point */
      emask = vis_edge16(da, dend);

      if (off < 0) {
        /* load 16 bytes */
        sd2 = *sp++;
        sd3 = *sp++;

        /* extract and store 8 bytes */
        CHANNELEXTRACT_S16_21R(sd2, sd3, dd1);
        vis_pst_16(vis_faligndata(dd1, dd1), dp++, emask);
      }
      else {
        /* load 32 bytes */
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        sd3 = *sp++;

        /* extract and store 8 bytes */
        CHANNELEXTRACT_S16_21R(sd0, sd1, dd0);
        CHANNELEXTRACT_S16_21R(sd2, sd3, dd1);
        vis_pst_16(vis_faligndata(dd0, dd1), dp++, emask);
      }

      if ((mlib_addr) dp <= (mlib_addr) dend2) {
        n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 8 + 1;

        /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
        for (i = 0; i < n; i++) {
          dd0 = dd1;
          sd2 = *sp++;
          sd3 = *sp++;
          CHANNELEXTRACT_S16_21R(sd2, sd3, dd1);
          *dp++ = vis_faligndata(dd0, dd1);
        }
      }

      /* end point handling */
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge16(dp, dend);
        dd0 = dd1;
        sd2 = *sp++;
        sd3 = *sp++;
        CHANNELEXTRACT_S16_21R(sd2, sd3, dd1);
        vis_pst_16(vis_faligndata(dd0, dd1), dp++, emask);
      }
    }
  }
}

/***************************************************************/
void mlib_v_ImageChannelExtract_S16_21(const mlib_s16 *src,
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

  for (j = 0; j < ysize; j++) {
    mlib_v_ImageChannelExtract_S16_21_D1(sa, da, xsize, cmask);
    sa = sl = (mlib_s16 *) ((mlib_u8 *) sl + slb);
    da = dl = (mlib_s16 *) ((mlib_u8 *) dl + dlb);
  }
}

/***************************************************************/
#define CHANNELEXTRACT_S16_31L(sd0, sd1, sd2, dd)               \
  /* extract the left channel */                                \
  sda = vis_fpmerge(vis_read_hi(sd0), vis_read_lo(sd1));        \
  sdb = vis_fpmerge(vis_read_lo(sd0), vis_read_hi(sd2));        \
  sdc = vis_fpmerge(vis_read_hi(sda), vis_read_lo(sdb));        \
  dd  = vis_fpmerge(vis_read_hi(sdc), vis_read_lo(sdc))

/***************************************************************/
#define CHANNELEXTRACT_S16_31M(sd0, sd1, sd2, dd)               \
  /* extract the middle channel */                              \
  sda = vis_fpmerge(vis_read_hi(sd0), vis_read_lo(sd1));        \
  sdb = vis_fpmerge(vis_read_hi(sd1), vis_read_lo(sd2));        \
  sdc = vis_fpmerge(vis_read_lo(sda), vis_read_hi(sdb));        \
  dd  = vis_fpmerge(vis_read_hi(sdc), vis_read_lo(sdc))

/***************************************************************/
#define CHANNELEXTRACT_S16_31R(sd0, sd1, sd2, dd)               \
  /* extract the right channel */                               \
  sda = vis_fpmerge(vis_read_lo(sd0), vis_read_hi(sd2));        \
  sdb = vis_fpmerge(vis_read_hi(sd1), vis_read_lo(sd2));        \
  sdc = vis_fpmerge(vis_read_hi(sda), vis_read_lo(sdb));        \
  dd  = vis_fpmerge(vis_read_hi(sdc), vis_read_lo(sdc))

/***************************************************************/
void mlib_v_ImageChannelExtract_S16_31_A8D1X4(const mlib_s16 *src,
                                              mlib_s16       *dst,
                                              mlib_s32       dsize,
                                              mlib_s32       cmask)
{
  mlib_d64 *sp, *dp;
  mlib_d64 sd0, sd1, sd2;
  mlib_d64 sda, sdb, sdc;
  mlib_d64 dd;
  mlib_s32 i;

  sp = (mlib_d64 *) src;
  dp = (mlib_d64 *) dst;

  if (cmask == 4) {
#pragma pipeloop(0)
    for (i = 0; i < dsize / 4; i++) {
      sd0 = *sp++;
      sd1 = *sp++;
      sd2 = *sp++;
      CHANNELEXTRACT_S16_31L(sd0, sd1, sd2, dd);
      *dp++ = dd;
    }
  }
  else if (cmask == 2) {
#pragma pipeloop(0)
    for (i = 0; i < dsize / 4; i++) {
      sd0 = *sp++;
      sd1 = *sp++;
      sd2 = *sp++;
      CHANNELEXTRACT_S16_31M(sd0, sd1, sd2, dd);
      *dp++ = dd;
    }
  }
  else {
#pragma pipeloop(0)
    for (i = 0; i < dsize / 4; i++) {
      sd0 = *sp++;
      sd1 = *sp++;
      sd2 = *sp++;
      CHANNELEXTRACT_S16_31R(sd0, sd1, sd2, dd);
      *dp++ = dd;
    }
  }
}

/***************************************************************/
void mlib_v_ImageChannelExtract_S16_31_A8D2X4(const mlib_s16 *src,
                                              mlib_s32       slb,
                                              mlib_s16       *dst,
                                              mlib_s32       dlb,
                                              mlib_s32       xsize,
                                              mlib_s32       ysize,
                                              mlib_s32       cmask)
{
  mlib_d64 *sp, *dp;
  mlib_d64 *sl, *dl;
  mlib_d64 sd0, sd1, sd2;
  mlib_d64 sda, sdb, sdc;
  mlib_d64 dd;
  mlib_s32 i, j;

  sp = sl = (mlib_d64 *) src;
  dp = dl = (mlib_d64 *) dst;

  if (cmask == 4) {
    for (j = 0; j < ysize; j++) {
#pragma pipeloop(0)
      for (i = 0; i < xsize / 4; i++) {
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        CHANNELEXTRACT_S16_31L(sd0, sd1, sd2, dd);
        *dp++ = dd;
      }

      sp = sl = (mlib_d64 *) ((mlib_u8 *) sl + slb);
      dp = dl = (mlib_d64 *) ((mlib_u8 *) dl + dlb);
    }
  }
  else if (cmask == 2) {
    for (j = 0; j < ysize; j++) {
#pragma pipeloop(0)
      for (i = 0; i < xsize / 4; i++) {
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        CHANNELEXTRACT_S16_31M(sd0, sd1, sd2, dd);
        *dp++ = dd;
      }

      sp = sl = (mlib_d64 *) ((mlib_u8 *) sl + slb);
      dp = dl = (mlib_d64 *) ((mlib_u8 *) dl + dlb);
    }
  }
  else {
    for (j = 0; j < ysize; j++) {
#pragma pipeloop(0)
      for (i = 0; i < xsize / 4; i++) {
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        CHANNELEXTRACT_S16_31R(sd0, sd1, sd2, dd);
        *dp++ = dd;
      }

      sp = sl = (mlib_d64 *) ((mlib_u8 *) sl + slb);
      dp = dl = (mlib_d64 *) ((mlib_u8 *) dl + dlb);
    }
  }
}

/***************************************************************/
void mlib_v_ImageChannelExtract_S16_31_D1(const mlib_s16 *src,
                                          mlib_s16       *dst,
                                          mlib_s32       dsize,
                                          mlib_s32       cmask)
{
  mlib_s16 *sa, *da;
  mlib_s16 *dend, *dend2;                             /* end points in dst */
  mlib_d64 *dp;                                       /* 8-byte aligned start points in dst */
  mlib_d64 *sp;                                       /* 8-byte aligned start point in src */
  mlib_d64 sd0, sd1, sd2;                             /* 8-byte source data */
  mlib_d64 sd3, sd4, sd5;
  mlib_d64 sda, sdb, sdc;
  mlib_d64 dd0, dd1;
  mlib_s32 soff;                                      /* offset of address in src */
  mlib_s32 doff;                                      /* offset of address in dst */
  mlib_s32 off;                                       /* offset of src over dst */
  mlib_s32 emask;                                     /* edge mask */
  mlib_s32 i, n;

  sa = (void *)src;
  da = dst;

  /* prepare the source address */
  sp = (mlib_d64 *) ((mlib_addr) sa & (~7));
  soff = ((mlib_addr) sa & 7);

  /* prepare the destination addresses */
  dp = (mlib_d64 *) ((mlib_addr) da & (~7));
  doff = ((mlib_addr) da & 7);
  dend = da + dsize - 1;
  dend2 = dend - 3;

  /* calculate the src's offset over dst */
  if (cmask == 4) {
    off = (soff / 6) * 2 - doff;
  }
  else if (cmask == 2) {
    off = ((soff + 2) / 6) * 2 - doff;
  }
  else {
    off = ((soff + 4) / 6) * 2 - doff;
  }

  if (((cmask == 4) && (soff % 6 == 0)) ||
      ((cmask == 2) && (soff % 6 == 4)) ||
      ((cmask == 1) && (soff % 6 == 2))) { /* extract left channel */

    if (off == 0) {                         /* src and dst have same alignment */

      /* generate edge mask for the start point */
      emask = vis_edge16(da, dend);

      /* load 16 bytes */
      sd0 = *sp++;
      sd1 = *sp++;
      sd2 = *sp++;

      /* extract, including some garbage at the start point */
      CHANNELEXTRACT_S16_31L(sd0, sd1, sd2, dd0);

      /* store 8 bytes result */
      vis_pst_16(dd0, dp++, emask);

      if ((mlib_addr) dp <= (mlib_addr) dend2) {
        n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 8 + 1;

        /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
        for (i = 0; i < n; i++) {
          sd0 = *sp++;
          sd1 = *sp++;
          sd2 = *sp++;
          CHANNELEXTRACT_S16_31L(sd0, sd1, sd2, dd0);
          *dp++ = dd0;
        }
      }

      /* end point handling */
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge16(dp, dend);
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        CHANNELEXTRACT_S16_31L(sd0, sd1, sd2, dd0);
        vis_pst_16(dd0, dp++, emask);
      }
    }
    else {
      vis_alignaddr((void *)0, off);

      /* generate edge mask for the start point */
      emask = vis_edge16(da, dend);

      if (off < 0) {
        /* load 24 bytes */
        sd3 = *sp++;
        sd4 = *sp++;
        sd5 = *sp++;

        /* extract and store 8 bytes */
        CHANNELEXTRACT_S16_31L(sd3, sd4, sd5, dd1);
        vis_pst_16(vis_faligndata(dd1, dd1), dp++, emask);
      }
      else {
        /* load 48 bytes */
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        sd3 = *sp++;
        sd4 = *sp++;
        sd5 = *sp++;

        /* extract and store 8 bytes */
        CHANNELEXTRACT_S16_31L(sd0, sd1, sd2, dd0);
        CHANNELEXTRACT_S16_31L(sd3, sd4, sd5, dd1);
        vis_pst_16(vis_faligndata(dd0, dd1), dp++, emask);
      }

      if ((mlib_addr) dp <= (mlib_addr) dend2) {
        n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 8 + 1;

        /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
        for (i = 0; i < n; i++) {
          dd0 = dd1;
          sd3 = *sp++;
          sd4 = *sp++;
          sd5 = *sp++;
          CHANNELEXTRACT_S16_31L(sd3, sd4, sd5, dd1);
          *dp++ = vis_faligndata(dd0, dd1);
        }
      }

      /* end point handling */
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge16(dp, dend);
        dd0 = dd1;
        sd3 = *sp++;
        sd4 = *sp++;
        sd5 = *sp++;
        CHANNELEXTRACT_S16_31L(sd3, sd4, sd5, dd1);
        vis_pst_16(vis_faligndata(dd0, dd1), dp++, emask);
      }
    }
  }
  else if (((cmask == 4) && (soff % 6 == 2)) ||
           ((cmask == 2) && (soff % 6 == 0)) ||
           ((cmask == 1) && (soff % 6 == 4))) {
    /* extract middle channel */

    if (off == 0) {                         /* src and dst have same alignment */

      /* generate edge mask for the start point */
      emask = vis_edge16(da, dend);

      /* load 16 bytes */
      sd0 = *sp++;
      sd1 = *sp++;
      sd2 = *sp++;

      /* extract, including some garbage at the start point */
      CHANNELEXTRACT_S16_31M(sd0, sd1, sd2, dd0);

      /* store 8 bytes result */
      vis_pst_16(dd0, dp++, emask);

      if ((mlib_addr) dp <= (mlib_addr) dend2) {
        n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 8 + 1;

        /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
        for (i = 0; i < n; i++) {
          sd0 = *sp++;
          sd1 = *sp++;
          sd2 = *sp++;
          CHANNELEXTRACT_S16_31M(sd0, sd1, sd2, dd0);
          *dp++ = dd0;
        }
      }

      /* end point handling */
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge16(dp, dend);
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        CHANNELEXTRACT_S16_31M(sd0, sd1, sd2, dd0);
        vis_pst_16(dd0, dp++, emask);
      }
    }
    else {
      vis_alignaddr((void *)0, off);

      /* generate edge mask for the start point */
      emask = vis_edge16(da, dend);

      if (off < 0) {
        /* load 24 bytes */
        sd3 = *sp++;
        sd4 = *sp++;
        sd5 = *sp++;

        /* extract and store 8 bytes */
        CHANNELEXTRACT_S16_31M(sd3, sd4, sd5, dd1);
        vis_pst_16(vis_faligndata(dd1, dd1), dp++, emask);
      }
      else {
        /* load 48 bytes */
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        sd3 = *sp++;
        sd4 = *sp++;
        sd5 = *sp++;

        /* extract and store 8 bytes */
        CHANNELEXTRACT_S16_31M(sd0, sd1, sd2, dd0);
        CHANNELEXTRACT_S16_31M(sd3, sd4, sd5, dd1);
        vis_pst_16(vis_faligndata(dd0, dd1), dp++, emask);
      }

      if ((mlib_addr) dp <= (mlib_addr) dend2) {
        n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 8 + 1;

        /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
        for (i = 0; i < n; i++) {
          dd0 = dd1;
          sd3 = *sp++;
          sd4 = *sp++;
          sd5 = *sp++;
          CHANNELEXTRACT_S16_31M(sd3, sd4, sd5, dd1);
          *dp++ = vis_faligndata(dd0, dd1);
        }
      }

      /* end point handling */
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge16(dp, dend);
        dd0 = dd1;
        sd3 = *sp++;
        sd4 = *sp++;
        sd5 = *sp++;
        CHANNELEXTRACT_S16_31M(sd3, sd4, sd5, dd1);
        vis_pst_16(vis_faligndata(dd0, dd1), dp++, emask);
      }
    }
  }
  else {                                    /* extract right channel */

    if (off == 0) {                         /* src and dst have same alignment */

      /* generate edge mask for the start point */
      emask = vis_edge16(da, dend);

      /* load 16 bytes */
      sd0 = *sp++;
      sd1 = *sp++;
      sd2 = *sp++;

      /* extract, including some garbage at the start point */
      CHANNELEXTRACT_S16_31R(sd0, sd1, sd2, dd0);

      /* store 8 bytes result */
      vis_pst_16(dd0, dp++, emask);

      if ((mlib_addr) dp <= (mlib_addr) dend2) {
        n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 8 + 1;

        /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
        for (i = 0; i < n; i++) {
          sd0 = *sp++;
          sd1 = *sp++;
          sd2 = *sp++;
          CHANNELEXTRACT_S16_31R(sd0, sd1, sd2, dd0);
          *dp++ = dd0;
        }
      }

      /* end point handling */
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge16(dp, dend);
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        CHANNELEXTRACT_S16_31R(sd0, sd1, sd2, dd0);
        vis_pst_16(dd0, dp++, emask);
      }
    }
    else {
      vis_alignaddr((void *)0, off);

      /* generate edge mask for the start point */
      emask = vis_edge16(da, dend);

      if (off < 0) {
        /* load 24 bytes */
        sd3 = *sp++;
        sd4 = *sp++;
        sd5 = *sp++;

        /* extract and store 8 bytes */
        CHANNELEXTRACT_S16_31R(sd3, sd4, sd5, dd1);
        vis_pst_16(vis_faligndata(dd1, dd1), dp++, emask);
      }
      else {
        /* load 48 bytes */
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        sd3 = *sp++;
        sd4 = *sp++;
        sd5 = *sp++;

        /* extract and store 8 bytes */
        CHANNELEXTRACT_S16_31R(sd0, sd1, sd2, dd0);
        CHANNELEXTRACT_S16_31R(sd3, sd4, sd5, dd1);
        vis_pst_16(vis_faligndata(dd0, dd1), dp++, emask);
      }

      if ((mlib_addr) dp <= (mlib_addr) dend2) {
        n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 8 + 1;

        /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
        for (i = 0; i < n; i++) {
          dd0 = dd1;
          sd3 = *sp++;
          sd4 = *sp++;
          sd5 = *sp++;
          CHANNELEXTRACT_S16_31R(sd3, sd4, sd5, dd1);
          *dp++ = vis_faligndata(dd0, dd1);
        }
      }

      /* end point handling */
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge16(dp, dend);
        dd0 = dd1;
        sd3 = *sp++;
        sd4 = *sp++;
        sd5 = *sp++;
        CHANNELEXTRACT_S16_31R(sd3, sd4, sd5, dd1);
        vis_pst_16(vis_faligndata(dd0, dd1), dp++, emask);
      }
    }
  }
}

/***************************************************************/
void mlib_v_ImageChannelExtract_S16_31(const mlib_s16 *src,
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

  for (j = 0; j < ysize; j++) {
    mlib_v_ImageChannelExtract_S16_31_D1(sa, da, xsize, cmask);
    sa = sl = (mlib_s16 *) ((mlib_u8 *) sl + slb);
    da = dl = (mlib_s16 *) ((mlib_u8 *) dl + dlb);
  }
}

/***************************************************************/
#define CHANNELEXTRACT_S16_41L(sd0, sd1,  sd2, sd3, dd)         \
  /* extract the left channel */                                \
  sda = vis_fpmerge(vis_read_hi(sd0), vis_read_hi(sd2));        \
  sdb = vis_fpmerge(vis_read_hi(sd1), vis_read_hi(sd3));        \
  sdc = vis_fpmerge(vis_read_hi(sda), vis_read_hi(sdb));        \
  dd  = vis_fpmerge(vis_read_hi(sdc), vis_read_lo(sdc))

/***************************************************************/
#define CHANNELEXTRACT_S16_41ML(sd0, sd1, sd2, sd3, dd)         \
  /* extract the middle left channel */                         \
  sda = vis_fpmerge(vis_read_hi(sd0), vis_read_hi(sd2));        \
  sdb = vis_fpmerge(vis_read_hi(sd1), vis_read_hi(sd3));        \
  sdc = vis_fpmerge(vis_read_lo(sda), vis_read_lo(sdb));        \
  dd  = vis_fpmerge(vis_read_hi(sdc), vis_read_lo(sdc))

/***************************************************************/
#define CHANNELEXTRACT_S16_41MR(sd0, sd1, sd2, sd3, dd)         \
  /* extract the middle right channel */                        \
  sda = vis_fpmerge(vis_read_lo(sd0), vis_read_lo(sd2));        \
  sdb = vis_fpmerge(vis_read_lo(sd1), vis_read_lo(sd3));        \
  sdc = vis_fpmerge(vis_read_hi(sda), vis_read_hi(sdb));        \
  dd  = vis_fpmerge(vis_read_hi(sdc), vis_read_lo(sdc))

/***************************************************************/
#define CHANNELEXTRACT_S16_41R(sd0, sd1, sd2, sd3, dd)          \
  /* extract the right channel */                               \
  sda = vis_fpmerge(vis_read_lo(sd0), vis_read_lo(sd2));        \
  sdb = vis_fpmerge(vis_read_lo(sd1), vis_read_lo(sd3));        \
  sdc = vis_fpmerge(vis_read_lo(sda), vis_read_lo(sdb));        \
  dd  = vis_fpmerge(vis_read_hi(sdc), vis_read_lo(sdc))

/***************************************************************/
void mlib_v_ImageChannelExtract_S16_41_A8D1X4(const mlib_s16 *src,
                                              mlib_s16       *dst,
                                              mlib_s32       dsize,
                                              mlib_s32       cmask)
{
  mlib_d64 *sp, *dp;
  mlib_d64 sd0, sd1, sd2, sd3;
  mlib_d64 sda, sdb, sdc;
  mlib_d64 dd;
  mlib_s32 i;

  sp = (mlib_d64 *) src;
  dp = (mlib_d64 *) dst;

  if (cmask == 8) {
#pragma pipeloop(0)
    for (i = 0; i < dsize / 4; i++) {
      sd0 = *sp++;
      sd1 = *sp++;
      sd2 = *sp++;
      sd3 = *sp++;
      CHANNELEXTRACT_S16_41L(sd0, sd1, sd2, sd3, dd);
      *dp++ = dd;
    }
  }
  else if (cmask == 4) {
#pragma pipeloop(0)
    for (i = 0; i < dsize / 4; i++) {
      sd0 = *sp++;
      sd1 = *sp++;
      sd2 = *sp++;
      sd3 = *sp++;
      CHANNELEXTRACT_S16_41ML(sd0, sd1, sd2, sd3, dd);
      *dp++ = dd;
    }
  }
  else if (cmask == 2) {
#pragma pipeloop(0)
    for (i = 0; i < dsize / 4; i++) {
      sd0 = *sp++;
      sd1 = *sp++;
      sd2 = *sp++;
      sd3 = *sp++;
      CHANNELEXTRACT_S16_41MR(sd0, sd1, sd2, sd3, dd);
      *dp++ = dd;
    }
  }
  else {
#pragma pipeloop(0)
    for (i = 0; i < dsize / 4; i++) {
      sd0 = *sp++;
      sd1 = *sp++;
      sd2 = *sp++;
      sd3 = *sp++;
      CHANNELEXTRACT_S16_41R(sd0, sd1, sd2, sd3, dd);
      *dp++ = dd;
    }
  }
}

/***************************************************************/
void mlib_v_ImageChannelExtract_S16_41_A8D2X4(const mlib_s16 *src,
                                              mlib_s32       slb,
                                              mlib_s16       *dst,
                                              mlib_s32       dlb,
                                              mlib_s32       xsize,
                                              mlib_s32       ysize,
                                              mlib_s32       cmask)
{
  mlib_d64 *sp, *dp;
  mlib_d64 *sl, *dl;
  mlib_d64 sd0, sd1, sd2, sd3;
  mlib_d64 sda, sdb, sdc;
  mlib_d64 dd;
  mlib_s32 i, j;

  sp = sl = (mlib_d64 *) src;
  dp = dl = (mlib_d64 *) dst;

  if (cmask == 8) {
    for (j = 0; j < ysize; j++) {
#pragma pipeloop(0)
      for (i = 0; i < xsize / 4; i++) {
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        sd3 = *sp++;
        CHANNELEXTRACT_S16_41L(sd0, sd1, sd2, sd3, dd);
        *dp++ = dd;
      }

      sp = sl = (mlib_d64 *) ((mlib_u8 *) sl + slb);
      dp = dl = (mlib_d64 *) ((mlib_u8 *) dl + dlb);
    }
  }
  else if (cmask == 4) {
    for (j = 0; j < ysize; j++) {
#pragma pipeloop(0)
      for (i = 0; i < xsize / 4; i++) {
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        sd3 = *sp++;
        CHANNELEXTRACT_S16_41ML(sd0, sd1, sd2, sd3, dd);
        *dp++ = dd;
      }

      sp = sl = (mlib_d64 *) ((mlib_u8 *) sl + slb);
      dp = dl = (mlib_d64 *) ((mlib_u8 *) dl + dlb);
    }
  }
  else if (cmask == 2) {
    for (j = 0; j < ysize; j++) {
#pragma pipeloop(0)
      for (i = 0; i < xsize / 4; i++) {
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        sd3 = *sp++;
        CHANNELEXTRACT_S16_41MR(sd0, sd1, sd2, sd3, dd);
        *dp++ = dd;
      }

      sp = sl = (mlib_d64 *) ((mlib_u8 *) sl + slb);
      dp = dl = (mlib_d64 *) ((mlib_u8 *) dl + dlb);
    }
  }
  else {
    for (j = 0; j < ysize; j++) {
#pragma pipeloop(0)
      for (i = 0; i < xsize / 4; i++) {
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        sd3 = *sp++;
        CHANNELEXTRACT_S16_41R(sd0, sd1, sd2, sd3, dd);
        *dp++ = dd;
      }

      sp = sl = (mlib_d64 *) ((mlib_u8 *) sl + slb);
      dp = dl = (mlib_d64 *) ((mlib_u8 *) dl + dlb);
    }
  }
}

/***************************************************************/
void mlib_v_ImageChannelExtract_S16_41_D1(const mlib_s16 *src,
                                          mlib_s16       *dst,
                                          mlib_s32       dsize,
                                          mlib_s32       cmask)
{
  mlib_s16 *sa, *da;
  mlib_s16 *dend, *dend2;                             /* end points in dst */
  mlib_d64 *dp;                                       /* 8-byte aligned start points in dst */
  mlib_d64 *sp;                                       /* 8-byte aligned start point in src */
  mlib_d64 sd0, sd1, sd2, sd3;                        /* 8-byte source data */
  mlib_d64 sd4, sd5, sd6, sd7;
  mlib_d64 sda, sdb, sdc;
  mlib_d64 dd0, dd1;
  mlib_s32 soff;                                      /* offset of address in src */
  mlib_s32 doff;                                      /* offset of address in dst */
  mlib_s32 off;                                       /* offset of src over dst */
  mlib_s32 emask;                                     /* edge mask */
  mlib_s32 i, n;

  sa = (void *)src;
  da = dst;

  /* prepare the source address */
  sp = (mlib_d64 *) ((mlib_addr) sa & (~7));
  soff = ((mlib_addr) sa & 7);

  /* prepare the destination addresses */
  dp = (mlib_d64 *) ((mlib_addr) da & (~7));
  doff = ((mlib_addr) da & 7);
  dend = da + dsize - 1;
  dend2 = dend - 3;

  /* calculate the src's offset over dst */
  if (cmask == 8) {
    off = (soff / 8) * 2 - doff;
  }
  else if (cmask == 4) {
    off = ((soff + 2) / 8) * 2 - doff;
  }
  else if (cmask == 2) {
    off = ((soff + 4) / 8) * 2 - doff;
  }
  else {
    off = ((soff + 6) / 8) * 2 - doff;
  }

  if (((cmask == 8) && (soff == 0)) ||
      ((cmask == 4) && (soff == 6)) ||
      ((cmask == 2) && (soff == 4)) ||
      ((cmask == 1) && (soff == 2))) { /* extract left channel */

    if (off == 0) {                         /* src and dst have same alignment */

      /* generate edge mask for the start point */
      emask = vis_edge16(da, dend);

      /* load 16 bytes */
      sd0 = *sp++;
      sd1 = *sp++;
      sd2 = *sp++;
      sd3 = *sp++;

      /* extract, including some garbage at the start point */
      CHANNELEXTRACT_S16_41L(sd0, sd1, sd2, sd3, dd0);

      /* store 8 bytes result */
      vis_pst_16(dd0, dp++, emask);

      if ((mlib_addr) dp <= (mlib_addr) dend2) {
        n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 8 + 1;

        /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
        for (i = 0; i < n; i++) {
          sd0 = *sp++;
          sd1 = *sp++;
          sd2 = *sp++;
          sd3 = *sp++;
          CHANNELEXTRACT_S16_41L(sd0, sd1, sd2, sd3, dd0);
          *dp++ = dd0;
        }
      }

      /* end point handling */
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge16(dp, dend);
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        sd3 = *sp++;
        CHANNELEXTRACT_S16_41L(sd0, sd1, sd2, sd3, dd0);
        vis_pst_16(dd0, dp++, emask);
      }
    }
    else {
      vis_alignaddr((void *)0, off);

      /* generate edge mask for the start point */
      emask = vis_edge16(da, dend);

      if (off < 0) {
        /* load 24 bytes */
        sd4 = *sp++;
        sd5 = *sp++;
        sd6 = *sp++;
        sd7 = *sp++;

        /* extract and store 8 bytes */
        CHANNELEXTRACT_S16_41L(sd4, sd5, sd6, sd7, dd1);
        vis_pst_16(vis_faligndata(dd1, dd1), dp++, emask);
      }
      else {
        /* load 48 bytes */
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        sd3 = *sp++;
        sd4 = *sp++;
        sd5 = *sp++;
        sd6 = *sp++;
        sd7 = *sp++;

        /* extract and store 8 bytes */
        CHANNELEXTRACT_S16_41L(sd0, sd1, sd2, sd3, dd0);
        CHANNELEXTRACT_S16_41L(sd4, sd5, sd6, sd7, dd1);
        vis_pst_16(vis_faligndata(dd0, dd1), dp++, emask);
      }

      if ((mlib_addr) dp <= (mlib_addr) dend2) {
        n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 8 + 1;

        /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
        for (i = 0; i < n; i++) {
          dd0 = dd1;
          sd4 = *sp++;
          sd5 = *sp++;
          sd6 = *sp++;
          sd7 = *sp++;
          CHANNELEXTRACT_S16_41L(sd4, sd5, sd6, sd7, dd1);
          *dp++ = vis_faligndata(dd0, dd1);
        }
      }

      /* end point handling */
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge16(dp, dend);
        dd0 = dd1;
        sd4 = *sp++;
        sd5 = *sp++;
        sd6 = *sp++;
        sd7 = *sp++;
        CHANNELEXTRACT_S16_41L(sd4, sd5, sd6, sd7, dd1);
        vis_pst_16(vis_faligndata(dd0, dd1), dp++, emask);
      }
    }
  }
  else if (((cmask == 8) && (soff == 2)) ||
           ((cmask == 4) && (soff == 0)) ||
           ((cmask == 2) && (soff == 6)) ||
           ((cmask == 1) && (soff == 4))) { /* extract middle left channel */

    if (off == 0) {                         /* src and dst have same alignment */

      /* generate edge mask for the start point */
      emask = vis_edge16(da, dend);

      /* load 16 bytes */
      sd0 = *sp++;
      sd1 = *sp++;
      sd2 = *sp++;
      sd3 = *sp++;

      /* extract, including some garbage at the start point */
      CHANNELEXTRACT_S16_41ML(sd0, sd1, sd2, sd3, dd0);

      /* store 8 bytes result */
      vis_pst_16(dd0, dp++, emask);

      if ((mlib_addr) dp <= (mlib_addr) dend2) {
        n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 8 + 1;

        /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
        for (i = 0; i < n; i++) {
          sd0 = *sp++;
          sd1 = *sp++;
          sd2 = *sp++;
          sd3 = *sp++;
          CHANNELEXTRACT_S16_41ML(sd0, sd1, sd2, sd3, dd0);
          *dp++ = dd0;
        }
      }

      /* end point handling */
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge16(dp, dend);
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        sd3 = *sp++;
        CHANNELEXTRACT_S16_41ML(sd0, sd1, sd2, sd3, dd0);
        vis_pst_16(dd0, dp++, emask);
      }
    }
    else {
      vis_alignaddr((void *)0, off);

      /* generate edge mask for the start point */
      emask = vis_edge16(da, dend);

      if (off < 0) {
        /* load 24 bytes */
        sd4 = *sp++;
        sd5 = *sp++;
        sd6 = *sp++;
        sd7 = *sp++;

        /* extract and store 8 bytes */
        CHANNELEXTRACT_S16_41ML(sd4, sd5, sd6, sd7, dd1);
        vis_pst_16(vis_faligndata(dd1, dd1), dp++, emask);
      }
      else {
        /* load 48 bytes */
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        sd3 = *sp++;
        sd4 = *sp++;
        sd5 = *sp++;
        sd6 = *sp++;
        sd7 = *sp++;

        /* extract and store 8 bytes */
        CHANNELEXTRACT_S16_41ML(sd0, sd1, sd2, sd3, dd0);
        CHANNELEXTRACT_S16_41ML(sd4, sd5, sd6, sd7, dd1);
        vis_pst_16(vis_faligndata(dd0, dd1), dp++, emask);
      }

      if ((mlib_addr) dp <= (mlib_addr) dend2) {
        n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 8 + 1;

        /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
        for (i = 0; i < n; i++) {
          dd0 = dd1;
          sd4 = *sp++;
          sd5 = *sp++;
          sd6 = *sp++;
          sd7 = *sp++;
          CHANNELEXTRACT_S16_41ML(sd4, sd5, sd6, sd7, dd1);
          *dp++ = vis_faligndata(dd0, dd1);
        }
      }

      /* end point handling */
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge16(dp, dend);
        dd0 = dd1;
        sd4 = *sp++;
        sd5 = *sp++;
        sd6 = *sp++;
        sd7 = *sp++;
        CHANNELEXTRACT_S16_41ML(sd4, sd5, sd6, sd7, dd1);
        vis_pst_16(vis_faligndata(dd0, dd1), dp++, emask);
      }
    }
  }
  else if (((cmask == 8) && (soff == 4)) ||
           ((cmask == 4) && (soff == 2)) ||
           ((cmask == 2) && (soff == 0)) ||
           ((cmask == 1) && (soff == 6))) { /* extract middle right channel */

    if (off == 0) {                         /* src and dst have same alignment */

      /* generate edge mask for the start point */
      emask = vis_edge16(da, dend);

      /* load 16 bytes */
      sd0 = *sp++;
      sd1 = *sp++;
      sd2 = *sp++;
      sd3 = *sp++;

      /* extract, including some garbage at the start point */
      CHANNELEXTRACT_S16_41MR(sd0, sd1, sd2, sd3, dd0);

      /* store 8 bytes result */
      vis_pst_16(dd0, dp++, emask);

      if ((mlib_addr) dp <= (mlib_addr) dend2) {
        n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 8 + 1;

        /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
        for (i = 0; i < n; i++) {
          sd0 = *sp++;
          sd1 = *sp++;
          sd2 = *sp++;
          sd3 = *sp++;
          CHANNELEXTRACT_S16_41MR(sd0, sd1, sd2, sd3, dd0);
          *dp++ = dd0;
        }
      }

      /* end point handling */
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge16(dp, dend);
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        sd3 = *sp++;
        CHANNELEXTRACT_S16_41MR(sd0, sd1, sd2, sd3, dd0);
        vis_pst_16(dd0, dp++, emask);
      }
    }
    else {
      vis_alignaddr((void *)0, off);

      /* generate edge mask for the start point */
      emask = vis_edge16(da, dend);

      if (off < 0) {
        /* load 24 bytes */
        sd4 = *sp++;
        sd5 = *sp++;
        sd6 = *sp++;
        sd7 = *sp++;

        /* extract and store 8 bytes */
        CHANNELEXTRACT_S16_41MR(sd4, sd5, sd6, sd7, dd1);
        vis_pst_16(vis_faligndata(dd1, dd1), dp++, emask);
      }
      else {
        /* load 48 bytes */
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        sd3 = *sp++;
        sd4 = *sp++;
        sd5 = *sp++;
        sd6 = *sp++;
        sd7 = *sp++;

        /* extract and store 8 bytes */
        CHANNELEXTRACT_S16_41MR(sd0, sd1, sd2, sd3, dd0);
        CHANNELEXTRACT_S16_41MR(sd4, sd5, sd6, sd7, dd1);
        vis_pst_16(vis_faligndata(dd0, dd1), dp++, emask);
      }

      if ((mlib_addr) dp <= (mlib_addr) dend2) {
        n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 8 + 1;

        /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
        for (i = 0; i < n; i++) {
          dd0 = dd1;
          sd4 = *sp++;
          sd5 = *sp++;
          sd6 = *sp++;
          sd7 = *sp++;
          CHANNELEXTRACT_S16_41MR(sd4, sd5, sd6, sd7, dd1);
          *dp++ = vis_faligndata(dd0, dd1);
        }
      }

      /* end point handling */
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge16(dp, dend);
        dd0 = dd1;
        sd4 = *sp++;
        sd5 = *sp++;
        sd6 = *sp++;
        sd7 = *sp++;
        CHANNELEXTRACT_S16_41MR(sd4, sd5, sd6, sd7, dd1);
        vis_pst_16(vis_faligndata(dd0, dd1), dp++, emask);
      }
    }
  }
  else {                                    /* extract right channel */
    if (off == 0) {                         /* src and dst have same alignment */

      /* generate edge mask for the start point */
      emask = vis_edge16(da, dend);

      /* load 16 bytes */
      sd0 = *sp++;
      sd1 = *sp++;
      sd2 = *sp++;
      sd3 = *sp++;

      /* extract, including some garbage at the start point */
      CHANNELEXTRACT_S16_41R(sd0, sd1, sd2, sd3, dd0);

      /* store 8 bytes result */
      vis_pst_16(dd0, dp++, emask);

      if ((mlib_addr) dp <= (mlib_addr) dend2) {
        n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 8 + 1;

        /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
        for (i = 0; i < n; i++) {
          sd0 = *sp++;
          sd1 = *sp++;
          sd2 = *sp++;
          sd3 = *sp++;
          CHANNELEXTRACT_S16_41R(sd0, sd1, sd2, sd3, dd0);
          *dp++ = dd0;
        }
      }

      /* end point handling */
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge16(dp, dend);
        sd0 = *sp++;
        sd1 = *sp++;
        sd2 = *sp++;
        sd3 = *sp++;
        CHANNELEXTRACT_S16_41R(sd0, sd1, sd2, sd3, dd0);
        vis_pst_16(dd0, dp++, emask);
      }
    }
    else {
      vis_alignaddr((void *)0, off);

      /* generate edge mask for the start point */
      emask = vis_edge16(da, dend);

      if (off < 0) {
        /* load 24 bytes */
        sd4 = *sp++;
        sd5 = *sp++;
        sd6 = *sp++;
        sd7 = *sp++;

        /* extract and store 8 bytes */
        CHANNELEXTRACT_S16_41R(sd4, sd5, sd6, sd7, dd1);
        vis_pst_16(vis_faligndata(dd1, dd1), dp++, emask);
      }

      if ((mlib_addr) dp <= (mlib_addr) dend2) {
        n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 8 + 1;

        /* 8-pixel column loop, emask not needed */
#pragma pipeloop(0)
        for (i = 0; i < n; i++) {
          dd0 = dd1;
          sd4 = *sp++;
          sd5 = *sp++;
          sd6 = *sp++;
          sd7 = *sp++;
          CHANNELEXTRACT_S16_41R(sd4, sd5, sd6, sd7, dd1);
          *dp++ = vis_faligndata(dd0, dd1);
        }
      }

      /* end point handling */
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge16(dp, dend);
        dd0 = dd1;
        sd4 = *sp++;
        sd5 = *sp++;
        sd6 = *sp++;
        sd7 = *sp++;
        CHANNELEXTRACT_S16_41R(sd4, sd5, sd6, sd7, dd1);
        vis_pst_16(vis_faligndata(dd0, dd1), dp++, emask);
      }
    }
  }
}

/***************************************************************/
void mlib_v_ImageChannelExtract_S16_41(const mlib_s16 *src,
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
  for (j = 0; j < ysize; j++) {
    mlib_v_ImageChannelExtract_S16_41_D1(sa, da, xsize, cmask);
    sa = sl = (mlib_s16 *) ((mlib_u8 *) sl + slb);
    da = dl = (mlib_s16 *) ((mlib_u8 *) dl + dlb);
  }
}

/***************************************************************/
