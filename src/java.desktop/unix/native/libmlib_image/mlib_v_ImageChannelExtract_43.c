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
 * FILENAME: mlib_v_ImageChannelExtract_43.c
 *
 * FUNCTIONS
 *      mlib_v_ImageChannelExtract_U8_43L_D1
 *      mlib_v_ImageChannelExtract_S16_43L_D1
 *
 * SYNOPSIS
 *
 * ARGUMENT
 *      src    pointer to source image data
 *      dst    pointer to destination image data
 *      slb    source image line stride in bytes
 *      dlb    destination image line stride in bytes
 *      dsize image data size in pixels
 *      xsize  image width in pixels
 *      ysize  image height in lines
 *      cmask channel mask
 *
 * DESCRIPTION
 *      extract the right or left 3 channels of a 4-channel image to
 *      a 3-channel image -- VIS version low level functions.
 *
 *      ABGR => BGR   (43R), or  RGBA => RGB  (43L)
 *
 * NOTE
 *      These functions are separated from mlib_v_ImageChannelExtract.c
 *      for loop unrolling and structure clarity.
 */

#include "vis_proto.h"
#include "mlib_image.h"
#include "mlib_v_ImageChannelExtract.h"

/***************************************************************/
#define EXTRACT_U8_43L        /* shift left */                  \
                                                                \
  vis_alignaddr((void *)0, 3);                                  \
  dd0 = vis_faligndata(dd0, sd0);    /* ----------r0g0b0 */     \
  sda = vis_freg_pair(vis_read_lo(sd0), vis_read_hi(sd0));      \
  dd0 = vis_faligndata(dd0, sda);    /* ----r0g0b0r1g1b1 */     \
                                                                \
  vis_alignaddr((void *)0, 2);                                  \
  dd0 = vis_faligndata(dd0, sd1);    /* r0g0b0r1g1b1r2g2 */     \
                                                                \
  vis_alignaddr((void *)0, 3);                                  \
  dd1 = vis_faligndata(dd1, sd1);    /* ----------r2g2b2 */     \
  sda = vis_freg_pair(vis_read_lo(sd1), vis_read_hi(sd1));      \
  dd1 = vis_faligndata(dd1, sda);    /* ----r2g2b2r3g3b3 */     \
  dd1 = vis_faligndata(dd1, sd2);    /* g2b2r3g3b3r4g4b4 */     \
                                                                \
  sda = vis_freg_pair(vis_read_lo(sd2), vis_read_hi(sd2));      \
  vis_alignaddr((void *)0, 1);                                  \
  dd1 = vis_faligndata(dd1, sda);    /* b2r3g3b3r4g4b4r5 */     \
                                                                \
  vis_alignaddr((void *)0, 3);                                  \
  dd2 = vis_faligndata(dd2, sda);    /* ----------r5g5b5 */     \
                                                                \
  dd2 = vis_faligndata(dd2, sd3);    /* ----r5g5b5r6g6b6 */     \
  sda = vis_freg_pair(vis_read_lo(sd3), vis_read_hi(sd3));      \
  dd2 = vis_faligndata(dd2, sda);           /* g5b5r6g6b6r7g7b7 */

/***************************************************************/
#define LOAD_EXTRACT_U8_43L                                             \
                                                                        \
  vis_alignaddr((void *)soff, 0);                                       \
  s0 = s4;                                                              \
  s1 = sp[1];                                                           \
  s2 = sp[2];                                                           \
  s3 = sp[3];                                                           \
  s4 = sp[4];                                                           \
  sd0 = vis_faligndata(s0, s1);  /* the intermediate is ABGR aligned */ \
  sd1 = vis_faligndata(s1, s2);                                         \
  sd2 = vis_faligndata(s2, s3);                                         \
  sd3 = vis_faligndata(s3, s4);                                         \
  sp += 4;                                                              \
                                                                        \
/*  vis_alignaddr((void *)0, 1); */    /* for _old only */              \
  dd2old = dd2;                                                         \
  EXTRACT_U8_43L

/***************************************************************/
/*
 * Either source or destination data are not 8-byte aligned.
 * And ssize is multiple of 8.
 */

void mlib_v_ImageChannelExtract_U8_43L_D1(const mlib_u8 *src,
                                          mlib_u8       *dst,
                                          mlib_s32      dsize)
{
  mlib_u8 *sa, *da;
  mlib_u8 *dend, *dend2;                              /* end points in dst */
  mlib_d64 *dp;                                       /* 8-byte aligned start points in dst */
  mlib_d64 *sp;                                       /* 8-byte aligned start point in src */
  mlib_d64 s0, s1, s2, s3, s4;                        /* 8-byte source row data */
  mlib_d64 sd0, sd1, sd2, sd3;                        /* 8-byte source data */
  mlib_d64 dd0, dd1, dd2;                             /* dst data */
  mlib_d64 dd2old;                                    /* the last datum of the last step */
  mlib_d64 sda;
  mlib_s32 soff;                                      /* offset of address in src */
  mlib_s32 doff;                                      /* offset of address in dst */
  mlib_s32 emask;                                     /* edge mask */
  mlib_s32 i, n;

  sa = (void *)src;
  da = dst;

  /* prepare the source address */
  sp = (mlib_d64 *) ((mlib_addr) sa & (~7));
  soff = ((mlib_addr) sa & 7);

  /* prepare the destination addresses */
  dp = (mlib_d64 *) ((mlib_addr) da & (~7));
  dend = da + dsize * 3 - 1;
  dend2 = dend - 23;
  doff = 8 - ((mlib_addr) da & 7);

  /* generate edge mask for the start point */
  emask = vis_edge8(da, dend);

  /* load 32 byte, convert, store 24 bytes */
  s4 = sp[0];                               /* initial value */
  LOAD_EXTRACT_U8_43L;

  if (dsize >= 8) {
    if (doff == 8) {
      vis_pst_8(dd0, dp++, emask);
      *dp++ = dd1;
      *dp++ = dd2;
    }
    else {
      vis_alignaddr((void *)doff, 0);
      vis_pst_8(vis_faligndata(dd0, dd0), dp++, emask);
      *dp++ = vis_faligndata(dd0, dd1);
      *dp++ = vis_faligndata(dd1, dd2);
    }
  }
  else {                                    /* for very small size */
    if (doff == 8) {
      vis_pst_8(dd0, dp++, emask);
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge8(dp, dend);
        vis_pst_8(dd1, dp++, emask);
        if ((mlib_addr) dp <= (mlib_addr) dend) {
          emask = vis_edge8(dp, dend);
          vis_pst_8(dd2, dp++, emask);
        }
      }
    }
    else {
      vis_alignaddr((void *)doff, 0);
      vis_pst_8(vis_faligndata(dd0, dd0), dp++, emask);
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge8(dp, dend);
        vis_pst_8(vis_faligndata(dd0, dd1), dp++, emask);
        if ((mlib_addr) dp <= (mlib_addr) dend) {
          emask = vis_edge8(dp, dend);
          vis_pst_8(vis_faligndata(dd1, dd2), dp++, emask);
          if ((mlib_addr) dp <= (mlib_addr) dend) {
            emask = vis_edge8(dp, dend);
            vis_pst_8(vis_faligndata(dd2, dd2), dp++, emask);
          }
        }
      }
    }
  }

  /* no edge handling is needed in the loop */
  if (doff == 8) {
    if ((mlib_addr) dp <= (mlib_addr) dend2) {
      n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 24 + 1;
#pragma pipeloop(0)
      for (i = 0; i < n; i++) {
        LOAD_EXTRACT_U8_43L;
        *dp++ = dd0;
        *dp++ = dd1;
        *dp++ = dd2;
      }
    }
  }
  else {
    if ((mlib_addr) dp <= (mlib_addr) dend2) {
      n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 24 + 1;
#pragma pipeloop(0)
      for (i = 0; i < n; i++) {
        LOAD_EXTRACT_U8_43L;
        vis_alignaddr((void *)doff, 0);
        *dp++ = vis_faligndata(dd2old, dd0);
        *dp++ = vis_faligndata(dd0, dd1);
        *dp++ = vis_faligndata(dd1, dd2);
      }
    }
  }

  if ((mlib_addr) dp <= (mlib_addr) dend) {
    LOAD_EXTRACT_U8_43L;
    emask = vis_edge8(dp, dend);
    if (doff == 8) {
      vis_pst_8(dd0, dp++, emask);
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge8(dp, dend);
        vis_pst_8(dd1, dp++, emask);
        if ((mlib_addr) dp <= (mlib_addr) dend) {
          emask = vis_edge8(dp, dend);
          vis_pst_8(dd2, dp++, emask);
        }
      }
    }
    else {
      vis_alignaddr((void *)doff, 0);
      vis_pst_8(vis_faligndata(dd2old, dd0), dp++, emask);
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge8(dp, dend);
        vis_pst_8(vis_faligndata(dd0, dd1), dp++, emask);
        if ((mlib_addr) dp <= (mlib_addr) dend) {
          emask = vis_edge8(dp, dend);
          vis_pst_8(vis_faligndata(dd1, dd2), dp++, emask);
        }
      }
    }
  }
}

/***************************************************************/
#define EXTRACT_S16_43L              /* shift left */           \
  vis_alignaddr((void *)0, 6);                                  \
  dd0 = vis_faligndata(dd0, sd0);    /* --r0g0b0 */             \
  vis_alignaddr((void *)0, 2);                                  \
  dd0 = vis_faligndata(dd0, sd1);    /* r0g0b0r1 */             \
                                                                \
  vis_alignaddr((void *)0, 6);                                  \
  dd1 = vis_faligndata(dd1, sd1);    /* --r1g1b1 */             \
  vis_alignaddr((void *)0, 4);                                  \
  dd1 = vis_faligndata(dd1, sd2);    /* g1b1r2g2 */             \
                                                                \
  vis_alignaddr((void *)0, 6);                                  \
  dd2 = vis_faligndata(dd2, sd2);    /* --r2g2b2 */             \
  dd2 = vis_faligndata(dd2, sd3);           /* b2r3g3b3 */

/***************************************************************/
#define LOAD_EXTRACT_S16_43L                                    \
                                                                \
  vis_alignaddr((void *)soff, 0);                               \
  s0 = s4;                                                      \
  s1 = sp[1];                                                   \
  s2 = sp[2];                                                   \
  s3 = sp[3];                                                   \
  s4 = sp[4];                                                   \
  sd0 = vis_faligndata(s0, s1);                                 \
  sd1 = vis_faligndata(s1, s2);                                 \
  sd2 = vis_faligndata(s2, s3);                                 \
  sd3 = vis_faligndata(s3, s4);                                 \
  sp += 4;                                                      \
  dd2old = dd2;                                                 \
  EXTRACT_S16_43L

/***************************************************************/
/*
 * Either source or destination data are not 8-byte aligned.
 * And size is in pixels.
 */

void mlib_v_ImageChannelExtract_S16_43L_D1(const mlib_s16 *src,
                                           mlib_s16       *dst,
                                           mlib_s32       dsize)
{
  mlib_s16 *sa, *da;                                  /* pointer for pixel */
  mlib_s16 *dend, *dend2;                             /* end points in dst */
  mlib_d64 *dp;                                       /* 8-byte aligned start points in dst */
  mlib_d64 *sp;                                       /* 8-byte aligned start point in src */
  mlib_d64 s0, s1, s2, s3, s4;                        /* 8-byte source row data */
  mlib_d64 sd0, sd1, sd2, sd3;                        /* 8-byte source data */
  mlib_d64 dd0, dd1, dd2;                             /* dst data */
  mlib_d64 dd2old;                                    /* the last datum of the last step */
  mlib_s32 soff;                                      /* offset of address in src */
  mlib_s32 doff;                                      /* offset of address in dst */
  mlib_s32 emask;                                     /* edge mask */
  mlib_s32 i, n;

  sa = (void *)src;
  da = dst;

  /* prepare the source address */
  sp = (mlib_d64 *) ((mlib_addr) sa & (~7));
  soff = ((mlib_addr) sa & 7);

  /* prepare the destination addresses */
  dp = (mlib_d64 *) ((mlib_addr) da & (~7));
  dend = da + dsize * 3 - 1;
  dend2 = dend - 11;
  doff = 8 - ((mlib_addr) da & 7);

  /* generate edge mask for the start point */
  emask = vis_edge16(da, dend);

  /* load 32 byte, convert, store 24 bytes */
  s4 = sp[0];                               /* initial value */
  LOAD_EXTRACT_S16_43L;

  if (dsize >= 4) {
    if (doff == 8) {
      vis_pst_16(dd0, dp++, emask);
      *dp++ = dd1;
      *dp++ = dd2;
    }
    else {
      vis_alignaddr((void *)doff, 0);
      vis_pst_16(vis_faligndata(dd0, dd0), dp++, emask);
      *dp++ = vis_faligndata(dd0, dd1);
      *dp++ = vis_faligndata(dd1, dd2);
    }
  }
  else {                                    /* for very small size */
    if (doff == 8) {
      vis_pst_16(dd0, dp++, emask);
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge16(dp, dend);
        vis_pst_16(dd1, dp++, emask);
        if ((mlib_addr) dp <= (mlib_addr) dend) {
          emask = vis_edge16(dp, dend);
          vis_pst_16(dd2, dp++, emask);
        }
      }
    }
    else {
      vis_alignaddr((void *)doff, 0);
      vis_pst_16(vis_faligndata(dd0, dd0), dp++, emask);
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge16(dp, dend);
        vis_pst_16(vis_faligndata(dd0, dd1), dp++, emask);
        if ((mlib_addr) dp <= (mlib_addr) dend) {
          emask = vis_edge16(dp, dend);
          vis_pst_16(vis_faligndata(dd1, dd2), dp++, emask);
        }
      }
    }
  }

  /* no edge handling is needed in the loop */
  if (doff == 8) {
    if ((mlib_addr) dp <= (mlib_addr) dend2) {
      n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 24 + 1;
#pragma pipeloop(0)
      for (i = 0; i < n; i++) {
        LOAD_EXTRACT_S16_43L;
        *dp++ = dd0;
        *dp++ = dd1;
        *dp++ = dd2;
      }
    }
  }
  else {
    if ((mlib_addr) dp <= (mlib_addr) dend2) {
      n = ((mlib_u8 *) dend2 - (mlib_u8 *) dp) / 24 + 1;
#pragma pipeloop(0)
      for (i = 0; i < n; i++) {
        LOAD_EXTRACT_S16_43L;
        vis_alignaddr((void *)doff, 0);
        *dp++ = vis_faligndata(dd2old, dd0);
        *dp++ = vis_faligndata(dd0, dd1);
        *dp++ = vis_faligndata(dd1, dd2);
      }
    }
  }

  if ((mlib_addr) dp <= (mlib_addr) dend) {
    LOAD_EXTRACT_S16_43L;
    emask = vis_edge16(dp, dend);
    if (doff == 8) {
      vis_pst_16(dd0, dp++, emask);
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge16(dp, dend);
        vis_pst_16(dd1, dp++, emask);
        if ((mlib_addr) dp <= (mlib_addr) dend) {
          emask = vis_edge16(dp, dend);
          vis_pst_16(dd2, dp++, emask);
        }
      }
    }
    else {
      vis_alignaddr((void *)doff, 0);
      vis_pst_16(vis_faligndata(dd2old, dd0), dp++, emask);
      if ((mlib_addr) dp <= (mlib_addr) dend) {
        emask = vis_edge16(dp, dend);
        vis_pst_16(vis_faligndata(dd0, dd1), dp++, emask);
        if ((mlib_addr) dp <= (mlib_addr) dend) {
          emask = vis_edge16(dp, dend);
          vis_pst_16(vis_faligndata(dd1, dd2), dp++, emask);
        }
      }
    }
  }
}

/***************************************************************/
