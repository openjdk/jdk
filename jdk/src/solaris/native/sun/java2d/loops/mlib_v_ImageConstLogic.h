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

#ifndef __MLIB_V_IMAGECONSTLOGIC_H
#define __MLIB_V_IMAGECONSTLOGIC_H


#if defined ( VIS )
#if VIS >= 0x200
#error This include file can be used with VIS 1.0 only
#endif /* VIS >= 0x200 */
#endif /* defined ( VIS ) */

#include <mlib_image.h>
#include <vis_proto.h>
#include <mlib_ImageCheck.h>
#include <mlib_ImageLogic_proto.h>
#include <mlib_v_ImageLogic_proto.h>

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

/*
 * Macro definitions for VIS version image logical functions.
 */

/***************************************************************/

#define VALIDATE()                                               \
  mlib_u8  *sp, *sl; /* pointers for pixel and line of source */ \
  mlib_u8  *dp,  *dl;/* pointers for pixel and line of dst */    \
  mlib_s32 width, height, type, nchannels;                       \
  mlib_s32 stride;   /* for src */                               \
  mlib_s32 strided;  /* for dst */                               \
  mlib_u32 c01, c02, c03, c04;                                   \
  mlib_d64 dc01, dc02, dc03;                                     \
                                                                 \
  MLIB_IMAGE_SIZE_EQUAL(dst,src);                                \
  MLIB_IMAGE_TYPE_EQUAL(dst,src);                                \
  MLIB_IMAGE_CHAN_EQUAL(dst,src);                                \
                                                                 \
  dp  = (mlib_u8 *) mlib_ImageGetData(dst);                      \
  sp  = (mlib_u8 *) mlib_ImageGetData(src);                      \
  height = mlib_ImageGetHeight(dst);                             \
  width  = mlib_ImageGetWidth(dst);                              \
  stride = mlib_ImageGetStride(src);                             \
  strided  = mlib_ImageGetStride(dst);                           \
  nchannels = mlib_ImageGetChannels(dst);                        \
  type = mlib_ImageGetType(dst);                                 \
                                                                 \
  if (type == MLIB_SHORT) {                                      \
    width *= (2 * nchannels);                                    \
    if (nchannels == 1) {                                        \
      c01 = c[0] & 0xFFFF; c01 |= (c01 << 16);                   \
      dc01 = vis_to_double_dup(c01);                             \
    } else if (nchannels == 2) {                                 \
      c01 = ((c[0] & 0xFFFF) << 16) | (c[1] & 0xFFFF);           \
      dc01 = vis_to_double_dup(c01);                             \
    } else if (nchannels == 3) {                                 \
      c01 = ((c[0] & 0xFFFF) << 16) | (c[1] & 0xFFFF);           \
      c02 = ((c[2] & 0xFFFF) << 16) | (c01 >> 16);               \
      c03 = (c01 << 16) | (c02 >> 16);                           \
      dc01= vis_to_double(c01, c02);                             \
      dc02= vis_to_double(c03, c01);                             \
      dc03= vis_to_double(c02, c03);                             \
    } else {                                                     \
      c01 = ((c[0] & 0xFFFF) << 16) | (c[1] & 0xFFFF);           \
      c02 = ((c[2] & 0xFFFF) << 16) | (c[3] & 0xFFFF);           \
      dc01= vis_to_double(c01, c02);                             \
    }                                                            \
                                                                 \
  } else if (type == MLIB_BYTE) {                                \
    width *= nchannels;                                          \
    if (nchannels == 1) {                                        \
      c01 = c[0] & 0xFF; c01 |= (c01 << 8);                      \
      c01 |= (c01 << 16);                                        \
      dc01 = vis_to_double_dup(c01);                             \
    } else if (nchannels == 2) {                                 \
      c01 = ((c[0] & 0xFF) << 8) | (c[1] & 0xFF);                \
      c01 |= (c01 << 16);                                        \
      dc01 = vis_to_double_dup(c01);                             \
    } else if (nchannels == 3) {                                 \
      c01 = ((c[0] & 0xFF) << 16) | ((c[1] & 0xFF) << 8) |       \
             (c[2] & 0xFF);                                      \
      c02 = (c01 << 16) | (c01 >> 8);                            \
      c03 = (c01 << 24) | c01;                                   \
      c01 = (c01 << 8) | (c01 >> 16);                            \
      dc01= vis_to_double(c01, c02);                             \
      dc02= vis_to_double(c03, c01);                             \
      dc03= vis_to_double(c02, c03);                             \
    } else {                                                     \
      c01 = ((c[0] & 0xFF) << 24) | ((c[1] & 0xFF) << 16) |      \
            ((c[2] & 0xFF) << 8) | (c[3] & 0xFF);                \
      dc01 = vis_to_double_dup(c01);                             \
    }                                                            \
  } else {                                                       \
    width *= (4 * nchannels);                                    \
    if (nchannels == 1) {                                        \
      c01 = c[0] & 0xFFFFFFFF;                                   \
      dc01 = vis_to_double_dup(c01);                             \
    } else if (nchannels == 2) {                                 \
      c01 = c[0] & 0xFFFFFFFF; c02 = c[1] & 0xFFFFFFFF;          \
      dc01 = vis_to_double(c01, c02);                            \
    } else if (nchannels == 3) {                                 \
      c01 = c[0] & 0xFFFFFFFF; c02 = c[1] & 0xFFFFFFFF;          \
      c03 = c[2] & 0xFFFFFFFF;                                   \
      dc01= vis_to_double(c01, c02);                             \
      dc02= vis_to_double(c03, c01);                             \
      dc03= vis_to_double(c02, c03);                             \
    } else {                                                     \
      c01 = c[0] & 0xFFFFFFFF; c02 = c[1] & 0xFFFFFFFF;          \
      c03 = c[2] & 0xFFFFFFFF; c04 = c[3] & 0xFFFFFFFF;          \
      dc01= vis_to_double(c01, c02);                             \
      dc02= vis_to_double(c03, c04);                             \
    }                                                            \
  }                                                              \
                                                                 \
  if ((width > stride) || (width > strided))                     \
    return MLIB_FAILURE

/***************************************************************/

static mlib_status mlib_v_ImageConstLogic(mlib_image *dst,
                                          mlib_image *src,
                                          mlib_s32   *c)
{
  mlib_s32 i, j;
  mlib_s32 offdst, offsrc, emask;
  mlib_d64 *dpp, *spp;
  mlib_d64 sa1, sa2, da, sa;
  mlib_d64 ssa, ssa1, ssa2, sa3, sa4;
  mlib_s32 amount;
  mlib_u8 *dend;
  mlib_d64 c1, c2, c3;

  VALIDATE();

  if (nchannels == 3) {
    if ((width == stride) && (width == strided) && ((width - (width / 3) * 3) == 0)) {

      amount = height * width;
      dend = dp + amount - 1;
      offdst = ((mlib_addr) dp) & 7;
      offsrc = ((mlib_addr) sp) & 7;

      if (offsrc == offdst) {

        /* prepare the destination addresses */
        dpp = (mlib_d64 *) vis_alignaddr(dp, 0);
        i = (mlib_u8 *) dpp - dp;

        if (i != 0) {
          vis_alignaddr((void *)(8 - offdst), 0);
          c3 = vis_faligndata(dc03, dc01);
          c1 = vis_faligndata(dc01, dc02);
          c2 = vis_faligndata(dc02, dc03);
        }
        else {
          c1 = dc01;
          c2 = dc02;
          c3 = dc03;
        }

        /* prepare the destination addresses */
        spp = (mlib_d64 *) vis_alignaddr(sp, 0);

        if (i != 0) {
          /* generate edge mask for the start point */
          emask = vis_edge8(dp, dend);
          sa1 = *spp++;
          da = VIS_CONSTLOGIC(c3, sa1);
          vis_pst_8(da, dpp++, emask);
          i += 8;
        }

#pragma pipeloop(0)
        for (; i < amount - 24; i += 24) {
          dpp[0] = VIS_CONSTLOGIC(c1, spp[0]);
          dpp[1] = VIS_CONSTLOGIC(c2, spp[1]);
          dpp[2] = VIS_CONSTLOGIC(c3, spp[2]);
          dpp += 3;
          spp += 3;
        }

        if (i < amount) {
          emask = vis_edge8(dpp, dend);
          sa1 = *spp++;
          da = VIS_CONSTLOGIC(c1, sa1);
          vis_pst_8(da, dpp++, emask);
          i += 8;
        }

        if (i < amount) {
          emask = vis_edge8(dpp, dend);
          sa1 = *spp++;
          da = VIS_CONSTLOGIC(c2, sa1);
          vis_pst_8(da, dpp++, emask);
          i += 8;
        }

        if (i < amount) {
          emask = vis_edge8(dpp, dend);
          sa1 = *spp++;
          da = VIS_CONSTLOGIC(c3, sa1);
          vis_pst_8(da, dpp, emask);
        }
      }
      else {
        /* prepare the destination addresses */
        dpp = (mlib_d64 *) vis_alignaddr(dp, 0);
        i = (mlib_u8 *) dpp - dp;

        if (i != 0) {
          vis_alignaddr((void *)(8 - offdst), 0);
          c3 = vis_faligndata(dc03, dc01);
          c1 = vis_faligndata(dc01, dc02);
          c2 = vis_faligndata(dc02, dc03);
        }
        else {
          c1 = dc01;
          c2 = dc02;
          c3 = dc03;
        }

        /* prepare the destination addresses */
        spp = (mlib_d64 *) vis_alignaddr(sp, i);

        sa1 = spp[0];

        if (i != 0) {
          /* generate edge mask for the start point */
          emask = vis_edge8(dp, dend);
          sa2 = spp[1];
          sa = vis_faligndata(sa1, sa2);
          da = VIS_CONSTLOGIC(c3, sa);
          vis_pst_8(da, dpp++, emask);
          sa1 = sa2;
          i += 8;
          spp++;
        }

#pragma pipeloop(0)
        for (; i < amount - 24; i += 24) {
          sa2 = spp[1];
          ssa = vis_faligndata(sa1, sa2);
          dpp[0] = VIS_CONSTLOGIC(c1, ssa);
          sa3 = spp[2];
          ssa1 = vis_faligndata(sa2, sa3);
          dpp[1] = VIS_CONSTLOGIC(c2, ssa1);
          sa4 = spp[3];
          ssa2 = vis_faligndata(sa3, sa4);
          dpp[2] = VIS_CONSTLOGIC(c3, ssa2);
          sa1 = sa4;
          dpp += 3;
          spp += 3;
        }

        if (i < amount) {
          emask = vis_edge8(dpp, dend);
          sa2 = spp[1];
          sa = vis_faligndata(sa1, sa2);
          da = VIS_CONSTLOGIC(c1, sa);
          vis_pst_8(da, dpp++, emask);
          sa1 = sa2;
          i += 8;
          spp++;
        }

        if (i < amount) {
          emask = vis_edge8(dpp, dend);
          sa2 = spp[1];
          sa = vis_faligndata(sa1, sa2);
          da = VIS_CONSTLOGIC(c2, sa);
          vis_pst_8(da, dpp++, emask);
          sa1 = sa2;
          i += 8;
          spp++;
        }

        if (i < amount) {
          emask = vis_edge8(dpp, dend);
          sa2 = spp[1];
          sa = vis_faligndata(sa1, sa2);
          da = VIS_CONSTLOGIC(c3, sa);
          vis_pst_8(da, dpp++, emask);
        }
      }
    }
    else {

      sl = sp;
      dl = dp;

      amount = width;

      for (j = 0; j < height; j++) {

        dend = dp + amount - 1;
        offdst = ((mlib_addr) dp) & 7;
        offsrc = ((mlib_addr) sp) & 7;

        if (offsrc == offdst) {

          /* prepare the destination addresses */
          dpp = (mlib_d64 *) vis_alignaddr(dp, 0);
          i = (mlib_u8 *) dpp - dp;

          if (i != 0) {
            vis_alignaddr((void *)(8 - offdst), 0);
            c3 = vis_faligndata(dc03, dc01);
            c1 = vis_faligndata(dc01, dc02);
            c2 = vis_faligndata(dc02, dc03);
          }
          else {
            c1 = dc01;
            c2 = dc02;
            c3 = dc03;
          }

          /* prepare the destination addresses */
          spp = (mlib_d64 *) vis_alignaddr(sp, 0);

          if (i != 0) {
            /* generate edge mask for the start point */
            emask = vis_edge8(dp, dend);
            sa1 = *spp++;
            da = VIS_CONSTLOGIC(c3, sa1);
            vis_pst_8(da, dpp++, emask);
            i += 8;
          }

#pragma pipeloop(0)
          for (; i < amount - 24; i += 24) {
            dpp[0] = VIS_CONSTLOGIC(c1, spp[0]);
            dpp[1] = VIS_CONSTLOGIC(c2, spp[1]);
            dpp[2] = VIS_CONSTLOGIC(c3, spp[2]);
            dpp += 3;
            spp += 3;
          }

          if (i < amount) {
            emask = vis_edge8(dpp, dend);
            sa1 = *spp++;
            da = VIS_CONSTLOGIC(c1, sa1);
            vis_pst_8(da, dpp++, emask);
            i += 8;
          }

          if (i < amount) {
            emask = vis_edge8(dpp, dend);
            sa1 = *spp++;
            da = VIS_CONSTLOGIC(c2, sa1);
            vis_pst_8(da, dpp++, emask);
            i += 8;
          }

          if (i < amount) {
            emask = vis_edge8(dpp, dend);
            sa1 = *spp++;
            da = VIS_CONSTLOGIC(c3, sa1);
            vis_pst_8(da, dpp, emask);
          }
        }
        else {
          /* prepare the destination addresses */
          dpp = (mlib_d64 *) vis_alignaddr(dp, 0);
          i = (mlib_u8 *) dpp - dp;

          if (i != 0) {
            vis_alignaddr((void *)(8 - offdst), 0);
            c3 = vis_faligndata(dc03, dc01);
            c1 = vis_faligndata(dc01, dc02);
            c2 = vis_faligndata(dc02, dc03);
          }
          else {
            c1 = dc01;
            c2 = dc02;
            c3 = dc03;
          }

          /* prepare the destination addresses */
          spp = (mlib_d64 *) vis_alignaddr(sp, i);

          sa1 = spp[0];

          if (i != 0) {
            /* generate edge mask for the start point */
            emask = vis_edge8(dp, dend);
            sa2 = spp[1];
            sa = vis_faligndata(sa1, sa2);
            da = VIS_CONSTLOGIC(c3, sa);
            vis_pst_8(da, dpp++, emask);
            sa1 = sa2;
            i += 8;
            spp++;
          }

#pragma pipeloop(0)
          for (; i < amount - 24; i += 24) {
            sa2 = spp[1];
            sa = vis_faligndata(sa1, sa2);
            dpp[0] = VIS_CONSTLOGIC(c1, sa);
            sa1 = spp[2];
            sa = vis_faligndata(sa2, sa1);
            dpp[1] = VIS_CONSTLOGIC(c2, sa);
            sa2 = spp[3];
            sa = vis_faligndata(sa1, sa2);
            dpp[2] = VIS_CONSTLOGIC(c3, sa);
            sa1 = sa2;
            dpp += 3;
            spp += 3;
          }

          if (i < amount) {
            emask = vis_edge8(dpp, dend);
            sa2 = spp[1];
            sa = vis_faligndata(sa1, sa2);
            da = VIS_CONSTLOGIC(c1, sa);
            vis_pst_8(da, dpp++, emask);
            sa1 = sa2;
            i += 8;
            spp++;
          }

          if (i < amount) {
            emask = vis_edge8(dpp, dend);
            sa2 = spp[1];
            sa = vis_faligndata(sa1, sa2);
            da = VIS_CONSTLOGIC(c2, sa);
            vis_pst_8(da, dpp++, emask);
            sa1 = sa2;
            i += 8;
            spp++;
          }

          if (i < amount) {
            emask = vis_edge8(dpp, dend);
            sa2 = spp[1];
            sa = vis_faligndata(sa1, sa2);
            da = VIS_CONSTLOGIC(c3, sa);
            vis_pst_8(da, dpp++, emask);
          }
        }

        sp = sl += stride;
        dp = dl += strided;
      }
    }

  }
  else if ((type != MLIB_INT) || (nchannels != 4)) {

    if ((width == stride) && (width == strided)) {

      amount = height * width;
      dend = dp + amount - 1;
      offdst = ((mlib_addr) dp) & 7;
      offsrc = ((mlib_addr) sp) & 7;

      if (offsrc == offdst) {

        /* prepare the destination addresses */
        dpp = (mlib_d64 *) vis_alignaddr(dp, 0);
        i = (mlib_u8 *) dpp - dp;

        if (i != 0) {
          vis_alignaddr((void *)(8 - offdst), 0);
          c1 = vis_faligndata(dc01, dc01);
        }
        else {
          c1 = dc01;
        }

        /* prepare the destination addresses */
        spp = (mlib_d64 *) vis_alignaddr(sp, 0);

        if (i != 0) {
          /* generate edge mask for the start point */
          emask = vis_edge8(dp, dend);
          sa1 = *spp++;
          da = VIS_CONSTLOGIC(c1, sa1);
          vis_pst_8(da, dpp++, emask);
          i += 8;
        }

#pragma pipeloop(0)
        for (; i < amount - 8; i += 8) {
          *dpp++ = VIS_CONSTLOGIC(c1, *spp);
          spp++;
        }

        if (i < amount) {
          emask = vis_edge8(dpp, dend);
          sa1 = *spp;
          da = VIS_CONSTLOGIC(c1, sa1);
          vis_pst_8(da, dpp, emask);
        }
      }
      else {
        /* prepare the destination addresses */
        dpp = (mlib_d64 *) vis_alignaddr(dp, 0);
        i = (mlib_u8 *) dpp - dp;

        if (i != 0) {
          vis_alignaddr((void *)(8 - offdst), 0);
          c1 = vis_faligndata(dc01, dc01);
        }
        else {
          c1 = dc01;
        }

        /* prepare the destination addresses */
        spp = (mlib_d64 *) vis_alignaddr(sp, i);

        sa1 = spp[0];

        if (i != 0) {
          /* generate edge mask for the start point */
          emask = vis_edge8(dp, dend);
          sa2 = spp[1];
          sa = vis_faligndata(sa1, sa2);
          da = VIS_CONSTLOGIC(c1, sa);
          vis_pst_8(da, dpp++, emask);
          sa1 = sa2;
          i += 8;
          spp++;
        }

#pragma pipeloop(0)
        for (; i < amount - 8; i += 8) {
          sa2 = spp[1];
          sa = vis_faligndata(sa1, sa2);
          *dpp++ = VIS_CONSTLOGIC(c1, sa);
          sa1 = sa2;
          spp++;
        }

        if (i < amount) {
          emask = vis_edge8(dpp, dend);
          sa2 = spp[1];
          sa = vis_faligndata(sa1, sa2);
          da = VIS_CONSTLOGIC(c1, sa);
          vis_pst_8(da, dpp, emask);
        }
      }
    }
    else {

      sl = sp;
      dl = dp;

      amount = width;

      for (j = 0; j < height; j++) {

        dend = dp + amount - 1;
        offdst = ((mlib_addr) dp) & 7;
        offsrc = ((mlib_addr) sp) & 7;

        if (offsrc == offdst) {

          /* prepare the destination addresses */
          dpp = (mlib_d64 *) vis_alignaddr(dp, 0);
          i = (mlib_u8 *) dpp - dp;

          if (i != 0) {
            vis_alignaddr((void *)(8 - offdst), 0);
            c1 = vis_faligndata(dc01, dc01);
          }
          else {
            c1 = dc01;
          }

          /* prepare the destination addresses */
          spp = (mlib_d64 *) vis_alignaddr(sp, 0);

          if (i != 0) {
            /* generate edge mask for the start point */
            emask = vis_edge8(dp, dend);
            sa1 = *spp++;
            da = VIS_CONSTLOGIC(c1, sa1);
            vis_pst_8(da, dpp++, emask);
            i += 8;
          }

#pragma pipeloop(0)
          for (; i < amount - 8; i += 8) {
            *dpp++ = VIS_CONSTLOGIC(c1, *spp);
            spp++;
          }

          if (i < amount) {
            emask = vis_edge8(dpp, dend);
            sa1 = *spp;
            da = VIS_CONSTLOGIC(c1, sa1);
            vis_pst_8(da, dpp, emask);
          }
        }
        else {
          /* prepare the destination addresses */
          dpp = (mlib_d64 *) vis_alignaddr(dp, 0);
          i = (mlib_u8 *) dpp - dp;

          if (i != 0) {
            vis_alignaddr((void *)(8 - offdst), 0);
            c1 = vis_faligndata(dc01, dc01);
          }
          else {
            c1 = dc01;
          }

          /* prepare the destination addresses */
          spp = (mlib_d64 *) vis_alignaddr(sp, i);

          sa1 = spp[0];

          if (i != 0) {
            /* generate edge mask for the start point */
            emask = vis_edge8(dp, dend);
            sa2 = spp[1];
            sa = vis_faligndata(sa1, sa2);
            da = VIS_CONSTLOGIC(c1, sa);
            vis_pst_8(da, dpp++, emask);
            sa1 = sa2;
            i += 8;
            spp++;
          }

#pragma pipeloop(0)
          for (; i < amount - 8; i += 8) {
            sa2 = spp[1];
            sa = vis_faligndata(sa1, sa2);
            *dpp++ = VIS_CONSTLOGIC(c1, sa);
            sa1 = sa2;
            spp++;
          }

          if (i < amount) {
            emask = vis_edge8(dpp, dend);
            sa2 = spp[1];
            sa = vis_faligndata(sa1, sa2);
            da = VIS_CONSTLOGIC(c1, sa);
            vis_pst_8(da, dpp, emask);
          }
        }

        sp = sl += stride;
        dp = dl += strided;
      }
    }

  }
  else {

    if ((width == stride) && (width == strided)) {

      amount = height * width;
      dend = dp + amount - 1;
      offdst = ((mlib_addr) dp) & 7;
      offsrc = ((mlib_addr) sp) & 7;

      if (offsrc == offdst) {

        /* prepare the destination addresses */
        dpp = (mlib_d64 *) vis_alignaddr(dp, 0);
        i = (mlib_u8 *) dpp - dp;

        if (i != 0) {
          vis_alignaddr((void *)(8 - offdst), 0);
          c2 = vis_faligndata(dc02, dc01);
          c1 = vis_faligndata(dc01, dc02);
        }
        else {
          c1 = dc01;
          c2 = dc02;
        }

        /* prepare the destination addresses */
        spp = (mlib_d64 *) vis_alignaddr(sp, 0);

        if (i != 0) {
          /* generate edge mask for the start point */
          emask = vis_edge8(dp, dend);
          sa1 = *spp++;
          da = VIS_CONSTLOGIC(c2, sa1);
          vis_pst_8(da, dpp++, emask);
          i += 8;
        }

#pragma pipeloop(0)
        for (; i < amount - 16; i += 16) {
          dpp[0] = VIS_CONSTLOGIC(c1, spp[0]);
          dpp[1] = VIS_CONSTLOGIC(c2, spp[1]);
          dpp += 2;
          spp += 2;
        }

        if (i < amount) {
          emask = vis_edge8(dpp, dend);
          sa1 = *spp++;
          da = VIS_CONSTLOGIC(c1, sa1);
          vis_pst_8(da, dpp++, emask);
          i += 8;
        }

        if (i < amount) {
          emask = vis_edge8(dpp, dend);
          sa1 = *spp;
          da = VIS_CONSTLOGIC(c2, sa1);
          vis_pst_8(da, dpp++, emask);
        }
      }
      else {
        /* prepare the destination addresses */
        dpp = (mlib_d64 *) vis_alignaddr(dp, 0);
        i = (mlib_u8 *) dpp - dp;

        if (i != 0) {
          vis_alignaddr((void *)(8 - offdst), 0);
          c2 = vis_faligndata(dc02, dc01);
          c1 = vis_faligndata(dc01, dc02);
        }
        else {
          c1 = dc01;
          c2 = dc02;
        }

        /* prepare the destination addresses */
        spp = (mlib_d64 *) vis_alignaddr(sp, i);

        sa1 = spp[0];

        if (i != 0) {
          /* generate edge mask for the start point */
          emask = vis_edge8(dp, dend);
          sa2 = spp[1];
          sa = vis_faligndata(sa1, sa2);
          da = VIS_CONSTLOGIC(c2, sa);
          vis_pst_8(da, dpp++, emask);
          sa1 = sa2;
          i += 8;
          spp++;
        }

#pragma pipeloop(0)
        for (; i < amount - 16; i += 16) {
          sa2 = spp[1];
          ssa = vis_faligndata(sa1, sa2);
          dpp[0] = VIS_CONSTLOGIC(c1, ssa);
          sa3 = spp[2];
          ssa1 = vis_faligndata(sa2, sa3);
          dpp[1] = VIS_CONSTLOGIC(c2, ssa1);
          sa1 = sa3;
          dpp += 2;
          spp += 2;
        }

        if (i < amount) {
          emask = vis_edge8(dpp, dend);
          sa2 = spp[1];
          sa = vis_faligndata(sa1, sa2);
          da = VIS_CONSTLOGIC(c1, sa);
          vis_pst_8(da, dpp++, emask);
          sa1 = sa2;
          i += 8;
          spp++;
        }

        if (i < amount) {
          emask = vis_edge8(dpp, dend);
          sa2 = spp[1];
          sa = vis_faligndata(sa1, sa2);
          da = VIS_CONSTLOGIC(c2, sa);
          vis_pst_8(da, dpp++, emask);
        }
      }
    }
    else {

      sl = sp;
      dl = dp;

      amount = width;

      for (j = 0; j < height; j++) {

        dend = dp + amount - 1;
        offdst = ((mlib_addr) dp) & 7;
        offsrc = ((mlib_addr) sp) & 7;

        if (offsrc == offdst) {

          /* prepare the destination addresses */
          dpp = (mlib_d64 *) vis_alignaddr(dp, 0);
          i = (mlib_u8 *) dpp - dp;

          if (i != 0) {
            vis_alignaddr((void *)(8 - offdst), 0);
            c2 = vis_faligndata(dc02, dc01);
            c1 = vis_faligndata(dc01, dc02);
          }
          else {
            c1 = dc01;
            c2 = dc02;
          }

          /* prepare the destination addresses */
          spp = (mlib_d64 *) vis_alignaddr(sp, 0);

          if (i != 0) {
            /* generate edge mask for the start point */
            emask = vis_edge8(dp, dend);
            sa1 = *spp++;
            da = VIS_CONSTLOGIC(c2, sa1);
            vis_pst_8(da, dpp++, emask);
            i += 8;
          }

#pragma pipeloop(0)
          for (; i < amount - 16; i += 16) {
            dpp[0] = VIS_CONSTLOGIC(c1, spp[0]);
            dpp[1] = VIS_CONSTLOGIC(c2, spp[1]);
            dpp += 2;
            spp += 2;
          }

          if (i < amount) {
            emask = vis_edge8(dpp, dend);
            sa1 = *spp++;
            da = VIS_CONSTLOGIC(c1, sa1);
            vis_pst_8(da, dpp++, emask);
            i += 8;
          }

          if (i < amount) {
            emask = vis_edge8(dpp, dend);
            sa1 = *spp;
            da = VIS_CONSTLOGIC(c2, sa1);
            vis_pst_8(da, dpp++, emask);
          }
        }
        else {
          /* prepare the destination addresses */
          dpp = (mlib_d64 *) vis_alignaddr(dp, 0);
          i = (mlib_u8 *) dpp - dp;

          if (i != 0) {
            vis_alignaddr((void *)(8 - offdst), 0);
            c2 = vis_faligndata(dc02, dc01);
            c1 = vis_faligndata(dc01, dc02);
          }
          else {
            c1 = dc01;
            c2 = dc02;
          }

          /* prepare the destination addresses */
          spp = (mlib_d64 *) vis_alignaddr(sp, i);

          sa1 = spp[0];

          if (i != 0) {
            /* generate edge mask for the start point */
            emask = vis_edge8(dp, dend);
            sa2 = spp[1];
            sa = vis_faligndata(sa1, sa2);
            da = VIS_CONSTLOGIC(c2, sa);
            vis_pst_8(da, dpp++, emask);
            sa1 = sa2;
            i += 8;
            spp++;
          }

#pragma pipeloop(0)
          for (; i < amount - 16; i += 16) {
            sa2 = spp[1];
            ssa = vis_faligndata(sa1, sa2);
            dpp[0] = VIS_CONSTLOGIC(c1, ssa);
            sa3 = spp[2];
            ssa1 = vis_faligndata(sa2, sa3);
            dpp[1] = VIS_CONSTLOGIC(c2, ssa1);
            sa1 = sa3;
            dpp += 2;
            spp += 2;
          }

          if (i < amount) {
            emask = vis_edge8(dpp, dend);
            sa2 = spp[1];
            sa = vis_faligndata(sa1, sa2);
            da = VIS_CONSTLOGIC(c1, sa);
            vis_pst_8(da, dpp++, emask);
            sa1 = sa2;
            i += 8;
            spp++;
          }

          if (i < amount) {
            emask = vis_edge8(dpp, dend);
            sa2 = spp[1];
            sa = vis_faligndata(sa1, sa2);
            da = VIS_CONSTLOGIC(c2, sa);
            vis_pst_8(da, dpp++, emask);
          }
        }

        sp = sl += stride;
        dp = dl += strided;
      }
    }
  }

  return MLIB_SUCCESS;
}

/***************************************************************/

#ifdef __cplusplus
}
#endif /* __cplusplus */
#endif /* __MLIB_V_IMAGECONSTLOGIC_H */
