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
 *      mlib_v_ImageChannelInsert_U8_12_D1
 *      mlib_v_ImageChannelInsert_U8_13_D1
 *      mlib_v_ImageChannelInsert_U8_14_D1
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
#define INSERT_U8_12(sd0, dd0, dd1)     /* channel duplicate */ \
  dd0 = vis_fpmerge(vis_read_hi(sd0), vis_read_hi(sd0));        \
  dd1 = vis_fpmerge(vis_read_lo(sd0), vis_read_lo(sd0))

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
#define INSERT_U8_14(sd0, dd0, dd1, dd2, dd3)                   \
  sda = vis_fpmerge(vis_read_hi(sd0), vis_read_hi(sd0));        \
  sdb = vis_fpmerge(vis_read_lo(sd0), vis_read_lo(sd0));        \
  dd0 = vis_fpmerge(vis_read_hi(sda), vis_read_hi(sda));        \
  dd1 = vis_fpmerge(vis_read_lo(sda), vis_read_lo(sda));        \
  dd2 = vis_fpmerge(vis_read_hi(sdb), vis_read_hi(sdb));        \
  dd3 = vis_fpmerge(vis_read_lo(sdb), vis_read_lo(sdb))

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
