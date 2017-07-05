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
 * FILENAME: mlib_v_ImageChannelInsert_34.c
 *
 * FUNCTIONS
 *      mlib_v_ImageChannelInsert_U8_34R_A8D1X8
 *      mlib_v_ImageChannelInsert_U8_34R_A8D2X8
 *      mlib_v_ImageChannelInsert_U8_34R_D1
 *      mlib_v_ImageChannelInsert_U8_34R
 *      mlib_v_ImageChannelInsert_S16_34R_A8D1X4
 *      mlib_v_ImageChannelInsert_S16_34R_A8D2X4
 *      mlib_v_ImageChannelInsert_S16_34R_D1
 *      mlib_v_ImageChannelInsert_S16_34R
 *      mlib_v_ImageChannelInsert_U8_34L_A8D1X8
 *      mlib_v_ImageChannelInsert_U8_34L_A8D2X8
 *      mlib_v_ImageChannelInsert_U8_34L_D1
 *      mlib_v_ImageChannelInsert_U8_34L
 *      mlib_v_ImageChannelInsert_S16_34L_A8D1X4
 *      mlib_v_ImageChannelInsert_S16_34L_A8D2X4
 *      mlib_v_ImageChannelInsert_S16_34L_D1
 *      mlib_v_ImageChannelInsert_S16_34L
 *
 * SYNOPSIS
 *
 * ARGUMENT
 *      src       pointer to source image data
 *      dst       pointer to destination image data
 *          slb   source image line stride in bytes
 *          dlb   destination image line stride in bytes
 *          dsize       image data size in pixels
 *          xsize       image width in pixels
 *          ysize       image height in lines
 *          cmask channel mask
 *
 * DESCRIPTION
 *          Insert a 3-channel image into the right or left 3 channels of
 *          a 4-channel image low level functions.
 *
 *                BGR => ABGR   (34R), or       RGB => RGBA     (34L)
 *
 * NOTE
 *          These functions are separated from mlib_v_ImageChannelInsert.c
 *          for loop unrolling and structure clarity.
 */

#include <stdlib.h>
#include "vis_proto.h"
#include "mlib_image.h"

/***************************************************************/
#define INSERT_U8_34R                                                                         \
  sda = vis_fpmerge(vis_read_hi(sd0), vis_read_lo(sd1));                    \
  sdb = vis_fpmerge(vis_read_lo(sd0), vis_read_hi(sd2));                    \
  sdc = vis_fpmerge(vis_read_hi(sd1), vis_read_lo(sd2));                    \
  sdd = vis_fpmerge(vis_read_hi(sda), vis_read_lo(sdb));                    \
  sde = vis_fpmerge(vis_read_lo(sda), vis_read_hi(sdc));                    \
  sdf = vis_fpmerge(vis_read_hi(sdb), vis_read_lo(sdc));                    \
  sdg = vis_fpmerge(vis_read_hi(sdd), vis_read_lo(sde));                    \
  sdh = vis_fpmerge(vis_read_lo(sdd), vis_read_hi(sdf));                    \
  sdi = vis_fpmerge(vis_read_hi(sde), vis_read_lo(sdf));                    \
  sdj = vis_fpmerge(vis_read_hi(sdg), vis_read_hi(sdi));                    \
  sdk = vis_fpmerge(vis_read_lo(sdg), vis_read_lo(sdi));                    \
  sdl = vis_fpmerge(vis_read_hi(sdh), vis_read_hi(sdh));                    \
  sdm = vis_fpmerge(vis_read_lo(sdh), vis_read_lo(sdh));                    \
  dd0 = vis_fpmerge(vis_read_hi(sdl), vis_read_hi(sdj));                    \
  dd1 = vis_fpmerge(vis_read_lo(sdl), vis_read_lo(sdj));                    \
  dd2 = vis_fpmerge(vis_read_hi(sdm), vis_read_hi(sdk));                    \
  dd3 = vis_fpmerge(vis_read_lo(sdm), vis_read_lo(sdk));

/***************************************************************/
#define LOAD_INSERT_STORE_U8_34R_A8                                                         \
  sd0 = *sp++;                                  /* b0g0r0b1g1r1b2g2 */                  \
  sd1 = *sp++;                                  /* r2b3g3r3b4g4r4b5 */                  \
  sd2 = *sp++;                                  /* g5r5b6g6r6b7g7r7 */                  \
  INSERT_U8_34R                                                                                           \
  vis_pst_8(dd0, dp++, bmask);                                                                \
  vis_pst_8(dd1, dp++, bmask);                                                                \
  vis_pst_8(dd2, dp++, bmask);                                                                \
  vis_pst_8(dd3, dp++, bmask);

/***************************************************************/
#define LOAD_INSERT_U8_34R                                                                      \
  vis_alignaddr((void *)soff, 0);                                                             \
  s0 = s3;                                                                                                    \
  s1 = sp[1];                                                                                               \
  s2 = sp[2];                                                                                               \
  s3 = sp[3];                                                                                               \
  sd0 = vis_faligndata(s0, s1);                                 \
  sd1 = vis_faligndata(s1, s2);                                                               \
  sd2 = vis_faligndata(s2, s3);                                                               \
  sp += 3;                                                                                                    \
  dd4 = dd3;                                                                  \
  INSERT_U8_34R

/***************************************************************/
/*
 * Both source and destination image data are 1-d vectors and
 * 8-byte aligned. And dsize is multiple of 8.
 */

void
mlib_v_ImageChannelInsert_U8_34R_A8D1X8(mlib_u8  *src,
                                                                mlib_u8  *dst,
                                                                mlib_s32 dsize)
{
  mlib_d64  *sp, *dp;
  mlib_d64  sd0, sd1, sd2;          /* source data */
  mlib_d64  dd0, dd1, dd2, dd3; /* dst data */
  mlib_d64  sda, sdb, sdc, sdd; /* intermediate variables */
  mlib_d64  sde, sdf, sdg, sdh;
  mlib_d64  sdi, sdj, sdk, sdl;
  mlib_d64  sdm;
  int       bmask = 0x77;
  int       i;

  sp = (mlib_d64 *)src;
  dp = (mlib_d64 *)dst;

#pragma pipeloop(0)
  for (i = 0; i < dsize / 8; i++) {
    LOAD_INSERT_STORE_U8_34R_A8;
  }
}

/***************************************************************/
/*
 * Either source or destination image data are not 1-d vectors, but
 * they are 8-byte aligned. And slb and dlb are multiple of 8.
 * The xsize is multiple of 8.
 */

void
mlib_v_ImageChannelInsert_U8_34R_A8D2X8(mlib_u8  *src,  mlib_s32 slb,
                                                                mlib_u8  *dst,  mlib_s32 dlb,
                                                                mlib_s32 xsize, mlib_s32 ysize)
{
  mlib_d64  *sp, *dp;             /* 8-byte aligned pointer for pixel */
  mlib_d64  *sl, *dl;             /* 8-byte aligned pointer for line */
  mlib_d64  sd0, sd1, sd2;      /* source data */
  mlib_d64  dd0, dd1, dd2, dd3; /* dst data */
  mlib_d64  sda, sdb, sdc, sdd; /* intermediate variables */
  mlib_d64  sde, sdf, sdg, sdh;
  mlib_d64  sdi, sdj, sdk, sdl;
  mlib_d64  sdm;
  int         bmask = 0x77;
  int       i, j;               /* indices for x, y */

  sp = sl = (mlib_d64 *)src;
  dp = dl = (mlib_d64 *)dst;

  /* row loop */
  for (j = 0; j < ysize; j++) {
    /* 8-byte column loop */
#pragma pipeloop(0)
    for (i = 0; i < xsize / 8; i++) {
      LOAD_INSERT_STORE_U8_34R_A8;
    }
    sp = sl = (mlib_d64 *)((mlib_u8 *)sl + slb);
    dp = dl = (mlib_d64 *)((mlib_u8 *)dl + dlb);
  }
}

/***************************************************************/
/*
 * either source or destination data are not 8-byte aligned.
 */

void
mlib_v_ImageChannelInsert_U8_34R_D1(mlib_u8  *src,
                                                            mlib_u8  *dst,
                                                            mlib_s32 dsize)
{
  mlib_u8   *sa, *da;
  mlib_u8   *dend, *dend2;      /* end points in dst */
  mlib_d64  *dp;                  /* 8-byte aligned start points in dst */
  mlib_d64  *sp;                  /* 8-byte aligned start point in src */
  mlib_d64  s0, s1, s2, s3;     /* 8-byte source raw data */
  mlib_d64  sd0, sd1, sd2;      /* 8-byte source data */
  mlib_d64  dd0, dd1, dd2, dd3; /* dst data */
  mlib_d64  dd4;                  /* the last datum of the last step */
  mlib_d64  sda, sdb, sdc, sdd; /* intermediate variables */
  mlib_d64  sde, sdf, sdg, sdh;
  mlib_d64  sdi, sdj, sdk, sdl;
  mlib_d64  sdm;
  int       soff;                 /* offset of address in src */
  int       doff;                 /* offset of address in dst */
  int       emask;              /* edge mask */
  int         bmask;            /* channel mask */
  int         i, n;

  sa = src;
  da = dst;

  /* prepare the source address */
  sp    = (mlib_d64 *) ((mlib_addr) sa & (~7));
  soff  = ((mlib_addr) sa & 7);

  /* prepare the destination addresses */
  dp    = (mlib_d64 *)((mlib_addr) da & (~7));
  dend  = da + dsize * 4 - 1;
  dend2 = dend - 31;
  doff  = ((mlib_addr) da & 7);

  /* set band mask for vis_pst_8 to store the bytes needed */
  bmask = 0xff & (0x7777 >> doff) ;

  /* generate edge mask for the start point */
  emask = vis_edge8(da, dend);

  /* load 24 bytes, convert to 32 bytes */
  s3 = sp[0];                                   /* initial value */
  LOAD_INSERT_U8_34R;

  if (doff == 0) {                              /* dst is 8-byte aligned */

    if (dsize >= 8 ) {
      vis_pst_8(dd0, dp++, emask & bmask);
      vis_pst_8(dd1, dp++, bmask);
      vis_pst_8(dd2, dp++, bmask);
      vis_pst_8(dd3, dp++, bmask);
    }
    else {                                      /* for very small size */
      vis_pst_8(dd0, dp++, emask & bmask);
      if ((mlib_addr) dp <= (mlib_addr) dend)  {
        emask = vis_edge8(dp, dend);
        vis_pst_8(dd1, dp++, emask & bmask);
        if ((mlib_addr) dp <= (mlib_addr) dend)  {
          emask = vis_edge8(dp, dend);
          vis_pst_8(dd2, dp++, emask & bmask);
          if ((mlib_addr) dp <= (mlib_addr) dend)  {
            emask = vis_edge8(dp, dend);
            vis_pst_8(dd3, dp++, emask & bmask);
          }
        }
      }
    }

    /* no edge handling is needed in the loop */
    if ((mlib_addr) dp <= (mlib_addr) dend2)  {
      n = ((mlib_u8 *)dend2 - (mlib_u8 *)dp) / 32 + 1;
#pragma pipeloop(0)
      for (i = 0; i < n; i++) {
        LOAD_INSERT_U8_34R;
        vis_pst_8(dd0, dp++, bmask);
        vis_pst_8(dd1, dp++, bmask);
        vis_pst_8(dd2, dp++, bmask);
        vis_pst_8(dd3, dp++, bmask);
      }
    }

    if ((mlib_addr) dp <= (mlib_addr) dend)  {
      LOAD_INSERT_U8_34R;
      emask = vis_edge8(dp, dend);
      vis_pst_8(dd0, dp++, emask & bmask);
      if ((mlib_addr) dp <= (mlib_addr) dend)  {
        emask = vis_edge8(dp, dend);
        vis_pst_8(dd1, dp++, emask & bmask);
        if ((mlib_addr) dp <= (mlib_addr) dend)  {
          emask = vis_edge8(dp, dend);
          vis_pst_8(dd2, dp++, emask & bmask);
          if ((mlib_addr) dp <= (mlib_addr) dend)  {
            emask = vis_edge8(dp, dend);
            vis_pst_8(dd3, dp++, emask & bmask);
          }
        }
      }
    }
  }
  else {                                        /* (doff != 0) */
    vis_alignaddr((void *)0, -doff);

    if (dsize >= 8 ) {
      vis_pst_8(vis_faligndata(dd0, dd0), dp++, emask & bmask);
      vis_pst_8(vis_faligndata(dd0, dd1), dp++, bmask);
      vis_pst_8(vis_faligndata(dd1, dd2), dp++, bmask);
      vis_pst_8(vis_faligndata(dd2, dd3), dp++, bmask);
    }
    else {                                      /* for very small size */
      vis_pst_8(vis_faligndata(dd0, dd0), dp++, emask & bmask);
      if ((mlib_addr) dp <= (mlib_addr) dend)  {
        emask = vis_edge8(dp, dend);
        vis_pst_8(vis_faligndata(dd0, dd1), dp++, emask & bmask);
        if ((mlib_addr) dp <= (mlib_addr) dend)  {
          emask = vis_edge8(dp, dend);
          vis_pst_8(vis_faligndata(dd1, dd2), dp++, emask & bmask);
          if ((mlib_addr) dp <= (mlib_addr) dend)  {
            emask = vis_edge8(dp, dend);
            vis_pst_8(vis_faligndata(dd2, dd3), dp++, emask & bmask);
            if ((mlib_addr) dp <= (mlib_addr) dend)  {
              emask = vis_edge8(dp, dend);
              vis_pst_8(vis_faligndata(dd3, dd3), dp++, emask & bmask);
            }
          }
        }
      }
    }

    /* no edge handling is needed in the loop */
    if ((mlib_addr) dp <= (mlib_addr) dend2)  {
      n = ((mlib_u8 *)dend2 - (mlib_u8 *)dp) / 32 + 1;
#pragma pipeloop(0)
      for (i = 0; i < n; i++) {
        LOAD_INSERT_U8_34R;
        vis_alignaddr((void *)0, -doff);
        vis_pst_8(vis_faligndata(dd4, dd0), dp++, bmask);
        vis_pst_8(vis_faligndata(dd0, dd1), dp++, bmask);
        vis_pst_8(vis_faligndata(dd1, dd2), dp++, bmask);
        vis_pst_8(vis_faligndata(dd2, dd3), dp++, bmask);
      }
    }

    if ((mlib_addr) dp <= (mlib_addr) dend)  {
      LOAD_INSERT_U8_34R;
      vis_alignaddr((void *)0, -doff);
      emask = vis_edge8(dp, dend);
      vis_pst_8(vis_faligndata(dd4, dd0), dp++, emask & bmask);
      if ((mlib_addr) dp <= (mlib_addr) dend)  {
        emask = vis_edge8(dp, dend);
        vis_pst_8(vis_faligndata(dd0, dd1), dp++, emask & bmask);
        if ((mlib_addr) dp <= (mlib_addr) dend)  {
          emask = vis_edge8(dp, dend);
          vis_pst_8(vis_faligndata(dd1, dd2), dp++, emask & bmask);
          if ((mlib_addr) dp <= (mlib_addr) dend)  {
            emask = vis_edge8(dp, dend);
            vis_pst_8(vis_faligndata(dd2, dd3), dp++, emask & bmask);
          }
        }
      }
    }
  }
}

/***************************************************************/

void
mlib_v_ImageChannelInsert_U8_34R(mlib_u8  *src,  mlib_s32 slb,
                                                 mlib_u8  *dst,  mlib_s32 dlb,
                                                         mlib_s32 xsize, mlib_s32 ysize)
{
  mlib_u8   *sa, *da;
  mlib_u8   *sl, *dl;
  int         j;

  sa = sl = src;
  da = dl = dst;

#pragma pipeloop(0)
  for (j = 0; j < ysize; j++) {
    mlib_v_ImageChannelInsert_U8_34R_D1(sa, da, xsize);
    sa = sl += slb;
    da = dl += dlb;
  }
}

/***************************************************************/
#define INSERT_S16_34R                                                                              \
  vis_alignaddr((void *)0, 6);                                                                \
  dd0 = vis_faligndata(sd0, sd0);                 /* b1b0g0r0 */                \
  vis_alignaddr((void *)0, 4);                                                                \
  dd1 = vis_faligndata(sd0, sd1);                 /* r0b1gbr1 */                \
  vis_alignaddr((void *)0, 2);                                                                \
  dd2 = vis_faligndata(sd1, sd2);                       /* r1b2g2r2 */          \
  dd3 = sd2;                                                          /* r2b3g3r3 */

/***************************************************************/
#define LOAD_INSERT_STORE_S16_34R_A8                                                      \
  sd0 = *sp++;                                          /* b0g0r0b1 */                      \
  sd1 = *sp++;                                          /* g1r1b2g2 */                      \
  sd2 = *sp++;                                          /* r2b3g3r3 */                      \
  INSERT_S16_34R                                                                                          \
  vis_pst_16(dd0, dp++, bmask);                                                               \
  vis_pst_16(dd1, dp++, bmask);                                                               \
  vis_pst_16(dd2, dp++, bmask);                                                               \
  vis_pst_16(dd3, dp++, bmask);

/***************************************************************/
#define LOAD_INSERT_S16_34R                                                                       \
  vis_alignaddr((void *)soff, 0);                                                             \
  s0 = s3;                                                                                                    \
  s1 = sp[1];                                                                                               \
  s2 = sp[2];                                                                                               \
  s3 = sp[3];                                                                                               \
  sd0 = vis_faligndata(s0, s1);                                                               \
  sd1 = vis_faligndata(s1, s2);                                                               \
  sd2 = vis_faligndata(s2, s3);                                                               \
  sp += 3;                                                                                                    \
  dd4 = dd3;                                                                                                \
  INSERT_S16_34R

/***************************************************************/
/*
 * both source and destination image data are 1-d vectors and
 * 8-byte aligned.  dsize is multiple of 4.
 */

void
mlib_v_ImageChannelInsert_S16_34R_A8D1X4(mlib_s16 *src,
                                                                 mlib_s16 *dst,
                                                                 mlib_s32 dsize)
{
  mlib_d64  *sp, *dp;           /* 8-byte aligned pointer for pixel */
  mlib_d64  sd0, sd1, sd2;      /* source data */
  mlib_d64  dd0, dd1, dd2, dd3; /* dst data */
  int       bmask = 0x07;       /* channel mask */
  int       i;

  sp = (mlib_d64 *)src;
  dp = (mlib_d64 *)dst;

  /* set GSR.offset for vis_faligndata()  */
  /* vis_alignaddr((void *)0, 2); */            /* only for _old */

#pragma pipeloop(0)
  for (i = 0; i < dsize / 4; i++) {
    LOAD_INSERT_STORE_S16_34R_A8;
  }
}

/***************************************************************/
/*
 * either source or destination image data are not 1-d vectors, but
 * they are 8-byte aligned.  xsize is multiple of 4.
 */

void
mlib_v_ImageChannelInsert_S16_34R_A8D2X4(mlib_s16 *src,  mlib_s32 slb,
                                                                 mlib_s16 *dst,  mlib_s32 dlb,
                                                                 mlib_s32 xsize, mlib_s32 ysize)
{
  mlib_d64  *sp, *dp;           /* 8-byte aligned pointer for pixel */
  mlib_d64  *sl, *dl;           /* 8-byte aligned pointer for line */
  mlib_d64  sd0, sd1, sd2;      /* source data */
  mlib_d64  dd0, dd1, dd2, dd3; /* dst data */
  int       bmask = 0x07;       /* channel mask */
  int       i, j;               /* indices for x, y */

  sp = sl = (mlib_d64 *)src;
  dp = dl = (mlib_d64 *)dst;

  /* row loop */
  for (j = 0; j < ysize; j++) {
    /* 4-pixel column loop */
#pragma pipeloop(0)
    for (i = 0; i < xsize / 4; i++) {
      LOAD_INSERT_STORE_S16_34R_A8;
    }
    sp = sl = (mlib_d64 *)((mlib_u8 *)sl + slb);
    dp = dl = (mlib_d64 *)((mlib_u8 *)dl + dlb);
  }
}

/***************************************************************/
/*
 * either source or destination data are not 8-byte aligned.
 */

void
mlib_v_ImageChannelInsert_S16_34R_D1(mlib_s16 *src,
                                                             mlib_s16 *dst,
                                                             mlib_s32 dsize)
{
  mlib_s16  *sa, *da;           /* pointer for pixel */
  mlib_s16  *dend, *dend2;      /* end points in dst */
  mlib_d64  *dp;                /* 8-byte aligned start points in dst */
  mlib_d64  *sp;                /* 8-byte aligned start point in src */
  mlib_d64  s0, s1, s2, s3;     /* 8-byte source raw data */
  mlib_d64  sd0, sd1, sd2;      /* 8-byte source data */
  mlib_d64  dd0, dd1, dd2, dd3; /* dst data */
  mlib_d64  dd4;                /* the last datum of the last step */
  int soff;             /* offset of address in src */
  int doff;             /* offset of address in dst */
  int       emask;              /* edge mask */
  int       bmask;              /* channel mask */
  int       i, n;

  sa = src;
  da = dst;

  /* prepare the source address */
  sp    = (mlib_d64 *) ((mlib_addr) sa & (~7));
  soff  = ((mlib_addr) sa & 7);

  /* prepare the destination addresses */
  dp    = (mlib_d64 *)((mlib_addr) da & (~7));
  dend  = da + dsize * 4 - 1;
  dend2 = dend - 15;
  doff  = ((mlib_addr) da & 7);

  /* set channel mask for vis_pst_16 to store the words needed */
  bmask = 0xff & (0x77 >> (doff / 2));

  /* generate edge mask for the start point */
  emask = vis_edge16(da, dend);

  /* load 24 byte, convert, store 32 bytes */
  s3 = sp[0];                                   /* initial value */
  LOAD_INSERT_S16_34R;

  if (doff == 0) {                              /* dst is 8-byte aligned */

    if (dsize >= 4 ) {
      vis_pst_16(dd0, dp++, emask & bmask);
      vis_pst_16(dd1, dp++, bmask);
      vis_pst_16(dd2, dp++, bmask);
      vis_pst_16(dd3, dp++, bmask);
    }
    else {                                      /* for very small size */
      vis_pst_16(dd0, dp++, emask & bmask);
      if ((mlib_addr) dp <= (mlib_addr) dend)  {
        emask = vis_edge16(dp, dend);
        vis_pst_16(dd1, dp++, emask & bmask);
        if ((mlib_addr) dp <= (mlib_addr) dend)  {
          emask = vis_edge16(dp, dend);
          vis_pst_16(dd2, dp++, emask & bmask);
        }
      }
    }

    /* no edge handling is needed in the loop */
    if ((mlib_addr) dp <= (mlib_addr) dend2)  {
      n = ((mlib_u8 *)dend2 - (mlib_u8 *)dp) / 32 + 1;
#pragma pipeloop(0)
      for (i = 0; i < n; i++) {
        LOAD_INSERT_S16_34R;
        vis_pst_16(dd0, dp++, bmask);
        vis_pst_16(dd1, dp++, bmask);
        vis_pst_16(dd2, dp++, bmask);
        vis_pst_16(dd3, dp++, bmask);
      }
    }

    if ((mlib_addr) dp <= (mlib_addr) dend)  {
      LOAD_INSERT_S16_34R;
      emask = vis_edge16(dp, dend);
      vis_pst_16(dd0, dp++, emask & bmask);
      if ((mlib_addr) dp <= (mlib_addr) dend)  {
        emask = vis_edge16(dp, dend);
        vis_pst_16(dd1, dp++, emask & bmask);
        if ((mlib_addr) dp <= (mlib_addr) dend)  {
          emask = vis_edge16(dp, dend);
          vis_pst_16(dd2, dp++, emask & bmask);
        }
      }
    }
  }
  else {                                        /* (doff != 0) */
    vis_alignaddr((void *)0, -doff);

    if (dsize >= 4 ) {
      vis_pst_16(vis_faligndata(dd0, dd0), dp++, emask & bmask);
      vis_pst_16(vis_faligndata(dd0, dd1), dp++, bmask);
      vis_pst_16(vis_faligndata(dd1, dd2), dp++, bmask);
      vis_pst_16(vis_faligndata(dd2, dd3), dp++, bmask);
    }
    else {                                      /* for very small size */
      vis_pst_16(vis_faligndata(dd0, dd0), dp++, emask & bmask);
      if ((mlib_addr) dp <= (mlib_addr) dend)  {
        emask = vis_edge16(dp, dend);
        vis_pst_16(vis_faligndata(dd0, dd1), dp++, emask & bmask);
        if ((mlib_addr) dp <= (mlib_addr) dend)  {
          emask = vis_edge16(dp, dend);
          vis_pst_16(vis_faligndata(dd1, dd2), dp++, emask & bmask);
          if ((mlib_addr) dp <= (mlib_addr) dend)  {
            emask = vis_edge16(dp, dend);
            vis_pst_16(vis_faligndata(dd2, dd3), dp++, emask & bmask);
          }
        }
      }
    }

    /* no edge handling is needed in the loop */
    if ((mlib_addr) dp <= (mlib_addr) dend2)  {
      n = ((mlib_u8 *)dend2 - (mlib_u8 *)dp) / 32 + 1;
#pragma pipeloop(0)
      for (i = 0; i < n; i++) {
        LOAD_INSERT_S16_34R;
        vis_alignaddr((void *)0, -doff);
        vis_pst_16(vis_faligndata(dd4, dd0), dp++, bmask);
        vis_pst_16(vis_faligndata(dd0, dd1), dp++, bmask);
        vis_pst_16(vis_faligndata(dd1, dd2), dp++, bmask);
        vis_pst_16(vis_faligndata(dd2, dd3), dp++, bmask);
      }
    }

    if ((mlib_addr) dp <= (mlib_addr) dend)  {
      LOAD_INSERT_S16_34R;
      vis_alignaddr((void *)0, -doff);
      emask = vis_edge16(dp, dend);
      vis_pst_16(vis_faligndata(dd4, dd0), dp++, emask & bmask);
      if ((mlib_addr) dp <= (mlib_addr) dend)  {
        emask = vis_edge16(dp, dend);
        vis_pst_16(vis_faligndata(dd0, dd1), dp++, emask & bmask);
        if ((mlib_addr) dp <= (mlib_addr) dend)  {
          emask = vis_edge16(dp, dend);
          vis_pst_16(vis_faligndata(dd1, dd2), dp++, emask & bmask);
          if ((mlib_addr) dp <= (mlib_addr) dend)  {
            emask = vis_edge16(dp, dend);
            vis_pst_16(vis_faligndata(dd2, dd3), dp++, emask & bmask);
          }
        }
      }
    }
  }
}

/***************************************************************/

void
mlib_v_ImageChannelInsert_S16_34R(mlib_s16 *src,  mlib_s32 slb,
                                                          mlib_s16 *dst,  mlib_s32 dlb,
                                                          mlib_s32 xsize, mlib_s32 ysize)
{
  mlib_s16  *sa, *da;
  mlib_s16  *sl, *dl;
  int       j;

  sa = sl = src;
  da = dl = dst;

#pragma pipeloop(0)
  for (j = 0; j < ysize; j++) {
    mlib_v_ImageChannelInsert_S16_34R_D1(sa, da, xsize);
    sa = sl = (mlib_s16 *)((mlib_u8 *)sl + slb);
    da = dl = (mlib_s16 *)((mlib_u8 *)dl + dlb);
  }
}

/***************************************************************/
#define INSERT_U8_34L                                                                                 \
  sda = vis_fpmerge(vis_read_hi(sd0), vis_read_lo(sd1));                    \
  sdb = vis_fpmerge(vis_read_lo(sd0), vis_read_hi(sd2));                    \
  sdc = vis_fpmerge(vis_read_hi(sd1), vis_read_lo(sd2));                    \
  sdd = vis_fpmerge(vis_read_hi(sda), vis_read_lo(sdb));                    \
  sde = vis_fpmerge(vis_read_lo(sda), vis_read_hi(sdc));                    \
  sdf = vis_fpmerge(vis_read_hi(sdb), vis_read_lo(sdc));                    \
  sdg = vis_fpmerge(vis_read_hi(sdd), vis_read_lo(sde));                    \
  sdh = vis_fpmerge(vis_read_lo(sdd), vis_read_hi(sdf));                    \
  sdi = vis_fpmerge(vis_read_hi(sde), vis_read_lo(sdf));                    \
  sdj = vis_fpmerge(vis_read_hi(sdg), vis_read_hi(sdi));                    \
  sdk = vis_fpmerge(vis_read_lo(sdg), vis_read_lo(sdi));                    \
  sdl = vis_fpmerge(vis_read_hi(sdh), vis_read_hi(sdh));                    \
  sdm = vis_fpmerge(vis_read_lo(sdh), vis_read_lo(sdh));                    \
  dd0 = vis_fpmerge(vis_read_hi(sdj), vis_read_hi(sdl));                    \
  dd1 = vis_fpmerge(vis_read_lo(sdj), vis_read_lo(sdl));                    \
  dd2 = vis_fpmerge(vis_read_hi(sdk), vis_read_hi(sdm));                    \
  dd3 = vis_fpmerge(vis_read_lo(sdk), vis_read_lo(sdm));

/***************************************************************/
#define LOAD_INSERT_STORE_U8_34L_A8                                                         \
  sd0 = *sp++;                                  /* b0g0r0b1g1r1b2g2 */                  \
  sd1 = *sp++;                                  /* r2b3g3r3b4g4r4b5 */                  \
  sd2 = *sp++;                                  /* g5r5b6g6r6b7g7r7 */                  \
  INSERT_U8_34L                                                                                                       \
  vis_pst_8(dd0, dp++, bmask);                                                                \
  vis_pst_8(dd1, dp++, bmask);                                                                \
  vis_pst_8(dd2, dp++, bmask);                                                                \
  vis_pst_8(dd3, dp++, bmask);

/***************************************************************/
#define LOAD_INSERT_U8_34L                                                                        \
  vis_alignaddr((void *)soff, 0);                                                             \
  s0 = s3;                                                                                                    \
  s1 = sp[1];                                                                                               \
  s2 = sp[2];                                                                                               \
  s3 = sp[3];                                                                                               \
  sd0 = vis_faligndata(s0, s1);                                 \
  sd1 = vis_faligndata(s1, s2);                                                               \
  sd2 = vis_faligndata(s2, s3);                                                               \
  sp += 3;                                                                                                    \
  dd4 = dd3;                                                    \
  INSERT_U8_34L

/***************************************************************/
/*
 * Both source and destination image data are 1-d vectors and
 * 8-byte aligned. And dsize is multiple of 8.
 */
void
mlib_v_ImageChannelInsert_U8_34L_A8D1X8(mlib_u8  *src,
                                                                mlib_u8  *dst,
                                                                mlib_s32 dsize)
{
  mlib_d64  *sp, *dp;
  mlib_d64  sd0, sd1, sd2;          /* source data */
  mlib_d64  dd0, dd1, dd2, dd3; /* dst data */
  mlib_d64  sda, sdb, sdc, sdd; /* intermediate variables */
  mlib_d64  sde, sdf, sdg, sdh;
  mlib_d64  sdi, sdj, sdk, sdl;
  mlib_d64  sdm;
  int         bmask = 0xee;
  int         i;

  sp = (mlib_d64 *)src;
  dp = (mlib_d64 *)dst;

#pragma pipeloop(0)
  for (i = 0; i < dsize / 8; i++) {
    LOAD_INSERT_STORE_U8_34L_A8;
  }
}

/***************************************************************/
/*
 * Either source or destination image data are not 1-d vectors, but
 * they are 8-byte aligned. And slb and dlb are multiple of 8.
 * The xsize is multiple of 8.
 */
void
mlib_v_ImageChannelInsert_U8_34L_A8D2X8(mlib_u8  *src,  mlib_s32 slb,
                                                                mlib_u8  *dst,  mlib_s32 dlb,
                                                        mlib_s32 xsize, mlib_s32 ysize)
{
  mlib_d64  *sp, *dp;           /* 8-byte aligned pointer for pixel */
  mlib_d64  *sl, *dl;           /* 8-byte aligned pointer for line */
  mlib_d64  sd0, sd1, sd2;      /* source data */
  mlib_d64  dd0, dd1, dd2, dd3; /* dst data */
  mlib_d64  sda, sdb, sdc, sdd; /* intermediate variables */
  mlib_d64  sde, sdf, sdg, sdh;
  mlib_d64  sdi, sdj, sdk, sdl;
  mlib_d64  sdm;
  int         bmask = 0xee;
  int       i, j;               /* indices for x, y */

  sp = sl = (mlib_d64 *)src;
  dp = dl = (mlib_d64 *)dst;

  /* row loop */
  for (j = 0; j < ysize; j++) {
    /* 8-byte column loop */
#pragma pipeloop(0)
    for (i = 0; i < xsize / 8; i++) {
      LOAD_INSERT_STORE_U8_34L_A8;
    }
    sp = sl = (mlib_d64 *)((mlib_u8 *)sl + slb);
    dp = dl = (mlib_d64 *)((mlib_u8 *)dl + dlb);
  }
}

/***************************************************************/
/*
 * either source or destination data are not 8-byte aligned.
 */
void
mlib_v_ImageChannelInsert_U8_34L_D1(mlib_u8  *src,
                                                            mlib_u8  *dst,
                                                            mlib_s32 dsize)
{
  mlib_u8   *sa, *da;
  mlib_u8   *dend, *dend2;      /* end points in dst */
  mlib_d64  *dp;                /* 8-byte aligned start points in dst */
  mlib_d64  *sp;                /* 8-byte aligned start point in src */
  mlib_d64  s0, s1, s2, s3;     /* 8-byte source raw data */
  mlib_d64  sd0, sd1, sd2;      /* 8-byte source data */
  mlib_d64  dd0, dd1, dd2, dd3; /* dst data */
  mlib_d64  dd4;                /* the last datum of the last step */
  mlib_d64  sda, sdb, sdc, sdd; /* intermediate variables */
  mlib_d64  sde, sdf, sdg, sdh;
  mlib_d64  sdi, sdj, sdk, sdl;
  mlib_d64  sdm;
  int       soff;               /* offset of address in src */
  int       doff;               /* offset of address in dst */
  int       emask;              /* edge mask */
  int         bmask;            /* channel mask */
  int         i, n;

  sa = src;
  da = dst;

  /* prepare the source address */
  sp    = (mlib_d64 *) ((mlib_addr) sa & (~7));
  soff  = ((mlib_addr) sa & 7);

  /* prepare the destination addresses */
  dp    = (mlib_d64 *)((mlib_addr) da & (~7));
  dend  = da + dsize * 4 - 1;
  dend2 = dend - 31;
  doff  = ((mlib_addr) da & 7);

  /* set band mask for vis_pst_8 to store the bytes needed */
  bmask = 0xff & (0xeeee >> doff) ;

  /* generate edge mask for the start point */
  emask = vis_edge8(da, dend);

  /* load 24 bytes, convert to 32 bytes */
  s3 = sp[0];                                   /* initial value */
  LOAD_INSERT_U8_34L;

  if (doff == 0) {                              /* dst is 8-byte aligned */

    if (dsize >= 8 ) {
      vis_pst_8(dd0, dp++, emask & bmask);
      vis_pst_8(dd1, dp++, bmask);
      vis_pst_8(dd2, dp++, bmask);
      vis_pst_8(dd3, dp++, bmask);
    }
    else {                                      /* for very small size */
      vis_pst_8(dd0, dp++, emask & bmask);
      if ((mlib_addr) dp <= (mlib_addr) dend)  {
        emask = vis_edge8(dp, dend);
        vis_pst_8(dd1, dp++, emask & bmask);
        if ((mlib_addr) dp <= (mlib_addr) dend)  {
          emask = vis_edge8(dp, dend);
          vis_pst_8(dd2, dp++, emask & bmask);
          if ((mlib_addr) dp <= (mlib_addr) dend)  {
            emask = vis_edge8(dp, dend);
            vis_pst_8(dd3, dp++, emask & bmask);
          }
        }
      }
    }

    /* no edge handling is needed in the loop */
    if ((mlib_addr) dp <= (mlib_addr) dend2)  {
      n = ((mlib_u8 *)dend2 - (mlib_u8 *)dp) / 32 + 1;
#pragma pipeloop(0)
      for (i = 0; i < n; i++) {
        LOAD_INSERT_U8_34L;
        vis_pst_8(dd0, dp++, bmask);
        vis_pst_8(dd1, dp++, bmask);
        vis_pst_8(dd2, dp++, bmask);
        vis_pst_8(dd3, dp++, bmask);
      }
    }

    if ((mlib_addr) dp <= (mlib_addr) dend)  {
      LOAD_INSERT_U8_34L;
      emask = vis_edge8(dp, dend);
      vis_pst_8(dd0, dp++, emask & bmask);
      if ((mlib_addr) dp <= (mlib_addr) dend)  {
        emask = vis_edge8(dp, dend);
        vis_pst_8(dd1, dp++, emask & bmask);
        if ((mlib_addr) dp <= (mlib_addr) dend)  {
          emask = vis_edge8(dp, dend);
          vis_pst_8(dd2, dp++, emask & bmask);
          if ((mlib_addr) dp <= (mlib_addr) dend)  {
            emask = vis_edge8(dp, dend);
            vis_pst_8(dd3, dp++, emask & bmask);
          }
        }
      }
    }
  }
  else {                                        /* (doff != 0) */
    vis_alignaddr((void *)0, -doff);

    if (dsize >= 8 ) {
      vis_pst_8(vis_faligndata(dd0, dd0), dp++, emask & bmask);
      vis_pst_8(vis_faligndata(dd0, dd1), dp++, bmask);
      vis_pst_8(vis_faligndata(dd1, dd2), dp++, bmask);
      vis_pst_8(vis_faligndata(dd2, dd3), dp++, bmask);
    }
    else {                                      /* for very small size */
      vis_pst_8(vis_faligndata(dd0, dd0), dp++, emask & bmask);
      if ((mlib_addr) dp <= (mlib_addr) dend)  {
        emask = vis_edge8(dp, dend);
        vis_pst_8(vis_faligndata(dd0, dd1), dp++, emask & bmask);
        if ((mlib_addr) dp <= (mlib_addr) dend)  {
          emask = vis_edge8(dp, dend);
          vis_pst_8(vis_faligndata(dd1, dd2), dp++, emask & bmask);
          if ((mlib_addr) dp <= (mlib_addr) dend)  {
            emask = vis_edge8(dp, dend);
            vis_pst_8(vis_faligndata(dd2, dd3), dp++, emask & bmask);
            if ((mlib_addr) dp <= (mlib_addr) dend)  {
              emask = vis_edge8(dp, dend);
              vis_pst_8(vis_faligndata(dd3, dd3), dp++, emask & bmask);
            }
          }
        }
      }
    }

    /* no edge handling is needed in the loop */
    if ((mlib_addr) dp <= (mlib_addr) dend2)  {
      n = ((mlib_u8 *)dend2 - (mlib_u8 *)dp) / 32 + 1;
#pragma pipeloop(0)
      for (i = 0; i < n; i++) {
        LOAD_INSERT_U8_34L;
        vis_alignaddr((void *)0, -doff);
        vis_pst_8(vis_faligndata(dd4, dd0), dp++, bmask);
        vis_pst_8(vis_faligndata(dd0, dd1), dp++, bmask);
        vis_pst_8(vis_faligndata(dd1, dd2), dp++, bmask);
        vis_pst_8(vis_faligndata(dd2, dd3), dp++, bmask);
      }
    }

    if ((mlib_addr) dp <= (mlib_addr) dend)  {
      LOAD_INSERT_U8_34L;
      vis_alignaddr((void *)0, -doff);
      emask = vis_edge8(dp, dend);
      vis_pst_8(vis_faligndata(dd4, dd0), dp++, emask & bmask);
      if ((mlib_addr) dp <= (mlib_addr) dend)  {
        emask = vis_edge8(dp, dend);
        vis_pst_8(vis_faligndata(dd0, dd1), dp++, emask & bmask);
        if ((mlib_addr) dp <= (mlib_addr) dend)  {
          emask = vis_edge8(dp, dend);
          vis_pst_8(vis_faligndata(dd1, dd2), dp++, emask & bmask);
          if ((mlib_addr) dp <= (mlib_addr) dend)  {
            emask = vis_edge8(dp, dend);
            vis_pst_8(vis_faligndata(dd2, dd3), dp++, emask & bmask);
          }
        }
      }
    }
  }
}

/***************************************************************/
void
mlib_v_ImageChannelInsert_U8_34L(mlib_u8  *src,  mlib_s32 slb,
                                                         mlib_u8  *dst,  mlib_s32 dlb,
                                                         mlib_s32 xsize, mlib_s32 ysize)
{
  mlib_u8   *sa, *da;
  mlib_u8   *sl, *dl;
  int         j;

  sa = sl = src;
  da = dl = dst;

#pragma pipeloop(0)
  for (j = 0; j < ysize; j++) {
    mlib_v_ImageChannelInsert_U8_34L_D1(sa, da, xsize);
    sa = sl += slb;
    da = dl += dlb;
  }
}

/***************************************************************/
#define INSERT_S16_34L                                                                              \
  dd0 = sd0;                                                            /* b0g0r0b1 */        \
  vis_alignaddr((void *)0, 6);                                                                \
  dd1 = vis_faligndata(sd0, sd1);                       /* b1gbr1b2 */        \
  vis_alignaddr((void *)0, 4);                                                                \
  dd2 = vis_faligndata(sd1, sd2);                         /* b2g2r2b3 */              \
  vis_alignaddr((void *)0, 2);                                                                \
  dd3 = vis_faligndata(sd2, sd2);                         /* b3g3r3r2 */

/***************************************************************/
#define LOAD_INSERT_STORE_S16_34L_A8                                                      \
  sd0 = *sp++;                                          /* b0g0r0b1 */                          \
  sd1 = *sp++;                                          /* g1r1b2g2 */                      \
  sd2 = *sp++;                                          /* r2b3g3r3 */                      \
  INSERT_S16_34L                                                                                          \
  vis_pst_16(dd0, dp++, bmask);                                                               \
  vis_pst_16(dd1, dp++, bmask);                                                               \
  vis_pst_16(dd2, dp++, bmask);                                                               \
  vis_pst_16(dd3, dp++, bmask);

/***************************************************************/
#define LOAD_INSERT_S16_34L                                                                       \
  vis_alignaddr((void *)soff, 0);                                                             \
  s0 = s3;                                                                                                    \
  s1 = sp[1];                                                                                               \
  s2 = sp[2];                                                                                               \
  s3 = sp[3];                                                                                               \
  sd0 = vis_faligndata(s0, s1);                                                               \
  sd1 = vis_faligndata(s1, s2);                                                               \
  sd2 = vis_faligndata(s2, s3);                                                               \
  sp += 3;                                                                                                    \
  dd4 = dd3;                                                                                                \
  INSERT_S16_34L

/***************************************************************/
/*
 * both source and destination image data are 1-d vectors and
 * 8-byte aligned.  dsize is multiple of 4.
 */

void
mlib_v_ImageChannelInsert_S16_34L_A8D1X4(mlib_s16 *src,
                                                                 mlib_s16 *dst,
                                                                 mlib_s32 dsize)
{
  mlib_d64  *sp, *dp;           /* 8-byte aligned pointer for pixel */
  mlib_d64  sd0, sd1, sd2;      /* source data */
  mlib_d64  dd0, dd1, dd2, dd3; /* dst data */
  int       bmask = 0x0e;       /* channel mask */
  int       i;

  sp = (mlib_d64 *)src;
  dp = (mlib_d64 *)dst;

#pragma pipeloop(0)
  for (i = 0; i < dsize / 4; i++) {
    LOAD_INSERT_STORE_S16_34L_A8;
  }
}

/***************************************************************/
/*
 * either source or destination image data are not 1-d vectors, but
 * they are 8-byte aligned.  xsize is multiple of 4.
 */

void
mlib_v_ImageChannelInsert_S16_34L_A8D2X4(mlib_s16 *src,  mlib_s32 slb,
                                                                 mlib_s16 *dst,  mlib_s32 dlb,
                                                                 mlib_s32 xsize, mlib_s32 ysize)
{
  mlib_d64  *sp, *dp;           /* 8-byte aligned pointer for pixel */
  mlib_d64  *sl, *dl;           /* 8-byte aligned pointer for line */
  mlib_d64  sd0, sd1, sd2;      /* source data */
  mlib_d64  dd0, dd1, dd2, dd3; /* dst data */
  int       bmask = 0x0e;       /* channel mask */
  int       i, j;               /* indices for x, y */

  sp = sl = (mlib_d64 *)src;
  dp = dl = (mlib_d64 *)dst;

  /* row loop */
  for (j = 0; j < ysize; j++) {
    /* 4-pixel column loop */
#pragma pipeloop(0)
    for (i = 0; i < xsize / 4; i++) {
      LOAD_INSERT_STORE_S16_34L_A8;
    }
    sp = sl = (mlib_d64 *)((mlib_u8 *)sl + slb);
    dp = dl = (mlib_d64 *)((mlib_u8 *)dl + dlb);
  }
}

/***************************************************************/
/*
 * either source or destination data are not 8-byte aligned.
 */

void
mlib_v_ImageChannelInsert_S16_34L_D1(mlib_s16 *src,
                                                             mlib_s16 *dst,
                                                             mlib_s32 dsize)
{
  mlib_s16  *sa, *da;           /* pointer for pixel */
  mlib_s16  *dend, *dend2;      /* end points in dst */
  mlib_d64  *dp;                /* 8-byte aligned start points in dst */
  mlib_d64  *sp;                /* 8-byte aligned start point in src */
  mlib_d64  s0, s1, s2, s3;     /* 8-byte source raw data */
  mlib_d64  sd0, sd1, sd2;      /* 8-byte source data */
  mlib_d64  dd0, dd1, dd2, dd3; /* dst data */
  mlib_d64  dd4;                /* the last datum of the last step */
  int soff;             /* offset of address in src */
  int doff;             /* offset of address in dst */
  int       emask;              /* edge mask */
  int       bmask;              /* channel mask */
  int       i, n;

  sa = src;
  da = dst;

  /* prepare the source address */
  sp    = (mlib_d64 *) ((mlib_addr) sa & (~7));
  soff  = ((mlib_addr) sa & 7);

  /* prepare the destination addresses */
  dp    = (mlib_d64 *)((mlib_addr) da & (~7));
  dend  = da + dsize * 4 - 1;
  dend2 = dend - 15;
  doff  = ((mlib_addr) da & 7);

  /* set channel mask for vis_pst_16 to store the words needed */
  bmask = 0xff & (0xee >> (doff / 2));

  /* generate edge mask for the start point */
  emask = vis_edge16(da, dend);

  /* load 24 byte, convert, store 32 bytes */
  s3 = sp[0];                                   /* initial value */
  LOAD_INSERT_S16_34L;

  if (doff == 0) {                              /* dst is 8-byte aligned */

    if (dsize >= 4 ) {
      vis_pst_16(dd0, dp++, emask & bmask);
      vis_pst_16(dd1, dp++, bmask);
      vis_pst_16(dd2, dp++, bmask);
      vis_pst_16(dd3, dp++, bmask);
    }
    else {                                      /* for very small size */
      vis_pst_16(dd0, dp++, emask & bmask);
      if ((mlib_addr) dp <= (mlib_addr) dend)  {
        emask = vis_edge16(dp, dend);
        vis_pst_16(dd1, dp++, emask & bmask);
        if ((mlib_addr) dp <= (mlib_addr) dend)  {
          emask = vis_edge16(dp, dend);
          vis_pst_16(dd2, dp++, emask & bmask);
        }
      }
    }

    /* no edge handling is needed in the loop */
    if ((mlib_addr) dp <= (mlib_addr) dend2)  {
      n = ((mlib_u8 *)dend2 - (mlib_u8 *)dp) / 32 + 1;
#pragma pipeloop(0)
      for (i = 0; i < n; i++) {
        LOAD_INSERT_S16_34L;
        vis_pst_16(dd0, dp++, bmask);
        vis_pst_16(dd1, dp++, bmask);
        vis_pst_16(dd2, dp++, bmask);
        vis_pst_16(dd3, dp++, bmask);
      }
    }

    if ((mlib_addr) dp <= (mlib_addr) dend)  {
      LOAD_INSERT_S16_34L;
      emask = vis_edge16(dp, dend);
      vis_pst_16(dd0, dp++, emask & bmask);
      if ((mlib_addr) dp <= (mlib_addr) dend)  {
        emask = vis_edge16(dp, dend);
        vis_pst_16(dd1, dp++, emask & bmask);
        if ((mlib_addr) dp <= (mlib_addr) dend)  {
          emask = vis_edge16(dp, dend);
          vis_pst_16(dd2, dp++, emask & bmask);
        }
      }
    }
  }
  else {                                        /* (doff != 0) */
    vis_alignaddr((void *)0, -doff);

    if (dsize >= 4 ) {
      vis_pst_16(vis_faligndata(dd0, dd0), dp++, emask & bmask);
      vis_pst_16(vis_faligndata(dd0, dd1), dp++, bmask);
      vis_pst_16(vis_faligndata(dd1, dd2), dp++, bmask);
      vis_pst_16(vis_faligndata(dd2, dd3), dp++, bmask);
    }
    else {                                      /* for very small size */
      vis_pst_16(vis_faligndata(dd0, dd0), dp++, emask & bmask);
      if ((mlib_addr) dp <= (mlib_addr) dend)  {
        emask = vis_edge16(dp, dend);
        vis_pst_16(vis_faligndata(dd0, dd1), dp++, emask & bmask);
        if ((mlib_addr) dp <= (mlib_addr) dend)  {
          emask = vis_edge16(dp, dend);
          vis_pst_16(vis_faligndata(dd1, dd2), dp++, emask & bmask);
          if ((mlib_addr) dp <= (mlib_addr) dend)  {
            emask = vis_edge16(dp, dend);
            vis_pst_16(vis_faligndata(dd2, dd3), dp++, emask & bmask);
          }
        }
      }
    }

    /* no edge handling is needed in the loop */
    if ((mlib_addr) dp <= (mlib_addr) dend2)  {
      n = ((mlib_u8 *)dend2 - (mlib_u8 *)dp) / 32 + 1;
#pragma pipeloop(0)
      for (i = 0; i < n; i++) {
        LOAD_INSERT_S16_34L;
        vis_alignaddr((void *)0, -doff);
        vis_pst_16(vis_faligndata(dd4, dd0), dp++, bmask);
        vis_pst_16(vis_faligndata(dd0, dd1), dp++, bmask);
        vis_pst_16(vis_faligndata(dd1, dd2), dp++, bmask);
        vis_pst_16(vis_faligndata(dd2, dd3), dp++, bmask);
      }
    }

    if ((mlib_addr) dp <= (mlib_addr) dend)  {
      LOAD_INSERT_S16_34L;
      vis_alignaddr((void *)0, -doff);
      emask = vis_edge16(dp, dend);
      vis_pst_16(vis_faligndata(dd4, dd0), dp++, emask & bmask);
      if ((mlib_addr) dp <= (mlib_addr) dend)  {
        emask = vis_edge16(dp, dend);
        vis_pst_16(vis_faligndata(dd0, dd1), dp++, emask & bmask);
        if ((mlib_addr) dp <= (mlib_addr) dend)  {
          emask = vis_edge16(dp, dend);
          vis_pst_16(vis_faligndata(dd1, dd2), dp++, emask & bmask);
          if ((mlib_addr) dp <= (mlib_addr) dend)  {
            emask = vis_edge16(dp, dend);
            vis_pst_16(vis_faligndata(dd2, dd3), dp++, emask & bmask);
          }
        }
      }
    }
  }
}

/***************************************************************/

void
mlib_v_ImageChannelInsert_S16_34L(mlib_s16 *src,  mlib_s32 slb,
                                                          mlib_s16 *dst,  mlib_s32 dlb,
                                                          mlib_s32 xsize, mlib_s32 ysize)
{
  mlib_s16  *sa, *da;
  mlib_s16  *sl, *dl;
  int       j;

  sa = sl = src;
  da = dl = dst;

#pragma pipeloop(0)
  for (j = 0; j < ysize; j++) {
    mlib_v_ImageChannelInsert_S16_34L_D1(sa, da, xsize);
    sa = sl = (mlib_s16 *)((mlib_u8 *)sl + slb);
    da = dl = (mlib_s16 *)((mlib_u8 *)dl + dlb);
  }
}

/***************************************************************/
