/*
 * Copyright (c) 1999, 2003, Oracle and/or its affiliates. All rights reserved.
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
 *      mlib_v_ImageCopy_a1         - 1-D, Aligned8, size 8x
 *      mlib_v_ImageCopy_a2         - 2-D, Aligned8, width 8x
 *      mlib_ImageCopy_na           - BYTE, non-aligned
 *      mlib_ImageCopy_bit_al       - BIT, aligned
 *
 * SYNOPSIS
 *
 * ARGUMENT
 *      sp       pointer to source image data
 *      dp       pointer to destination image data
 *      size     size in 8-bytes, bytes, or SHORTs
 *      width    image width in 8-bytes
 *      height   image height in lines
 *      stride   source image line stride in 8-bytes
 *      dstride  destination image line stride in 8-bytes
 *      s_offset source image line bit offset
 *      d_offset destination image line bit offset
 *
 * DESCRIPTION
 *      Direct copy from one image to another -- VIS version low level
 *      functions.
 *
 * NOTE
 *      These functions are separated from mlib_v_ImageCopy.c for loop
 *      unrolling and structure clarity.
 */

#include "vis_proto.h"
#include "mlib_image.h"
#include "mlib_ImageCopy.h"
#include "mlib_v_ImageCopy_f.h"

#define VIS_ALIGNADDR(X, Y)  vis_alignaddr((void *)(X), Y)

/***************************************************************/
/*
 * Both source and destination image data are 1-d vectors and
 * 8-byte aligned. And size is in 8-bytes.
 */

void mlib_v_ImageCopy_a1(mlib_d64 *sp,
                         mlib_d64 *dp,
                         mlib_s32 size)
{
  mlib_s32 i;

#pragma pipeloop(0)
  for (i = 0; i < size; i++) {
    *dp++ = *sp++;
  }
}

/***************************************************************/
/*
 * Either source or destination image data are not 1-d vectors, but
 * they are 8-byte aligned. And stride and width are in 8-bytes.
 */

void mlib_v_ImageCopy_a2(mlib_d64 *sp,
                         mlib_d64 *dp,
                         mlib_s32 width,
                         mlib_s32 height,
                         mlib_s32 stride,
                         mlib_s32 dstride)
{
  mlib_d64 *spl;                                      /* 8-byte aligned pointer for line */
  mlib_d64 *dpl;                                      /* 8-byte aligned pointer for line */
  mlib_s32 i, j;                                      /* indices for x, y */

  spl = sp;
  dpl = dp;

  /* row loop */
  for (j = 0; j < height; j++) {
    /* 8-byte column loop */
#pragma pipeloop(0)
    for (i = 0; i < width; i++) {
      *dp++ = *sp++;
    }

    sp = spl += stride;
    dp = dpl += dstride;
  }
}

/***************************************************************/
/*
 * Both bit offsets of source and distination are the same
 */

void mlib_ImageCopy_bit_al(const mlib_u8 *sa,
                           mlib_u8       *da,
                           mlib_s32      size,
                           mlib_s32      offset)
{
  mlib_u8 *dend;                                      /* end points in dst */
  mlib_d64 *dp;                                       /* 8-byte aligned start points in dst */
  mlib_d64 *sp;                                       /* 8-byte aligned start point in src */
  mlib_d64 s0, s1;                                    /* 8-byte source data */
  mlib_s32 j;                                         /* offset of address in dst */
  mlib_s32 emask;                                     /* edge mask */
  mlib_s32 b_size;                                    /* size in bytes */
  mlib_u8 mask0 = 0xFF;
  mlib_u8 src, mask;

  if (size <- 0) return;

  if (size < (8 - offset)) {
    mask = mask0 << (8 - size);
    mask >>= offset;
    src = da[0];
    da[0] = (src & (~mask)) | (sa[0] & mask);
    return;
  }

  mask = mask0 >> offset;
  src = da[0];
  da[0] = (src & (~mask)) | (sa[0] & mask);
  da++;
  sa++;
  size = size - 8 + offset;
  b_size = size >> 3;                       /* size in bytes */

  /* prepare the destination addresses */
  dp = (mlib_d64 *) ((mlib_addr) da & (~7));
  j = (mlib_addr) dp - (mlib_addr) da;
  dend = da + b_size - 1;

  /* prepare the source address */
  sp = (mlib_d64 *) VIS_ALIGNADDR(sa, j);
  /* generate edge mask for the start point */
  emask = vis_edge8(da, dend);

  s1 = vis_ld_d64_nf(sp);
  if (emask != 0xff) {
    s0 = s1;
    s1 = vis_ld_d64_nf(sp+1);
    s0 = vis_faligndata(s0, s1);
    vis_pst_8(s0, dp++, emask);
    sp++;
    j += 8;
  }

#pragma pipeloop(0)
  for (; j <= (b_size - 8); j += 8) {
    s0 = s1;
    s1 = vis_ld_d64_nf(sp+1);
    *dp++ = vis_faligndata(s0, s1);
    sp++;
  }

  if (j < b_size) {
    s0 = vis_faligndata(s1, vis_ld_d64_nf(sp+1));
    emask = vis_edge8(dp, dend);
    vis_pst_8(s0, dp, emask);
  }

  j = size & 7;

  if (j > 0) {
    mask = mask0 << (8 - j);
    src = dend[1];
    dend[1] = (src & (~mask)) | (sa[b_size] & mask);
  }
}

/***************************************************************/
/*
 * Either source or destination data are not 8-byte aligned.
 * And size is is in bytes.
 */

void mlib_ImageCopy_na(const mlib_u8 *sa,
                       mlib_u8       *da,
                       mlib_s32      size)
{
  mlib_u8 *dend;                                      /* end points in dst */
  mlib_d64 *dp;                                       /* 8-byte aligned start points in dst */
  mlib_d64 *sp;                                       /* 8-byte aligned start point in src */
  mlib_d64 s0, s1;                                    /* 8-byte source data */
  mlib_s32 j;                                         /* offset of address in dst */
  mlib_s32 emask;                                     /* edge mask */

  /* prepare the destination addresses */
  dp = (mlib_d64 *) ((mlib_addr) da & (~7));
  j = (mlib_addr) dp - (mlib_addr) da;
  dend = da + size - 1;

  /* prepare the source address */
  sp = (mlib_d64 *) VIS_ALIGNADDR(sa, j);
  /* generate edge mask for the start point */
  emask = vis_edge8(da, dend);

  s1 = vis_ld_d64_nf(sp);
  if (emask != 0xff) {
    s0 = s1;
    s1 = vis_ld_d64_nf(sp+1);
    s0 = vis_faligndata(s0, s1);
    vis_pst_8(s0, dp++, emask);
    sp++;
    j += 8;
  }

  if (((mlib_addr) sa ^ (mlib_addr) da) & 7) {
#pragma pipeloop(0)
    for (; j <= (size - 8); j += 8) {
      s0 = s1;
      s1 = vis_ld_d64_nf(sp+1);
      *dp++ = vis_faligndata(s0, s1);
      sp++;
    }

    if (j < size) {
      s0 = vis_faligndata(s1, vis_ld_d64_nf(sp+1));
      emask = vis_edge8(dp, dend);
      vis_pst_8(s0, dp, emask);
    }
  }
  else {
#pragma pipeloop(0)
    for (; j <= (size - 8); j += 8) {
      *dp++ = *sp++;
    }

    if (j < size) {
      emask = vis_edge8(dp, dend);
      vis_pst_8(vis_ld_d64_nf(sp), dp, emask);
    }
  }
}

/***************************************************************/
