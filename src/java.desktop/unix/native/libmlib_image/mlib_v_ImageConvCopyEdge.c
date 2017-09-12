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
 *      mlib_ImageConvCopyEdge  - Copy src edges  to dst edges
 *
 *
 * SYNOPSIS
 *      mlib_status mlib_ImageConvCopyEdge(mlib_image       *dst,
 *                                         const mlib_image *src,
 *                                         mlib_s32         dx_l,
 *                                         mlib_s32         dx_r,
 *                                         mlib_s32         dy_t,
 *                                         mlib_s32         dy_b,
 *                                         mlib_s32         cmask)
 *
 * ARGUMENT
 *      dst       Pointer to an dst image.
 *      src       Pointer to an src image.
 *      dx_l      Number of columns on the left side of the
 *                image to be copyed.
 *      dx_r      Number of columns on the right side of the
 *                image to be copyed.
 *      dy_t      Number of rows on the top edge of the
 *                image to be copyed.
 *      dy_b      Number of rows on the top edge of the
 *                image to be copyed.
 *      cmask     Channel mask to indicate the channels to be convolved.
 *                Each bit of which represents a channel in the image. The
 *                channels corresponded to 1 bits are those to be processed.
 *
 * RESTRICTION
 *      The src and the dst must be the same type, same width, same height and have same number
 *      of channels (1, 2, 3, or 4). The unselected channels are not
 *      overwritten. If both src and dst have just one channel,
 *      cmask is ignored.
 *
 * DESCRIPTION
 *      Copy src edges  to dst edges.

 *      The unselected channels are not overwritten.
 *      If src and dst have just one channel,
 *      cmask is ignored.
 */

#include "vis_proto.h"
#include "mlib_image.h"
#include "mlib_ImageConvEdge.h"

/***************************************************************/
static void mlib_ImageConvCopyEdge_U8(mlib_image       *dst,
                                      const mlib_image *src,
                                      mlib_s32         dx_l,
                                      mlib_s32         dx_r,
                                      mlib_s32         dy_t,
                                      mlib_s32         dy_b,
                                      mlib_s32         cmask,
                                      mlib_s32         nchan);

static void mlib_ImageConvCopyEdge_U8_3(mlib_image       *dst,
                                        const mlib_image *src,
                                        mlib_s32         dx_l,
                                        mlib_s32         dx_r,
                                        mlib_s32         dy_t,
                                        mlib_s32         dy_b,
                                        mlib_s32         cmask);

static void mlib_ImageConvCopyEdge_S16(mlib_image       *dst,
                                       const mlib_image *src,
                                       mlib_s32         dx_l,
                                       mlib_s32         dx_r,
                                       mlib_s32         dy_t,
                                       mlib_s32         dy_b,
                                       mlib_s32         cmask,
                                       mlib_s32         nchan);

static void mlib_ImageConvCopyEdge_S16_3(mlib_image       *dst,
                                         const mlib_image *src,
                                         mlib_s32         dx_l,
                                         mlib_s32         dx_r,
                                         mlib_s32         dy_t,
                                         mlib_s32         dy_b,
                                         mlib_s32         cmask);

static void mlib_ImageConvCopyEdge_S32(mlib_image       *dst,
                                       const mlib_image *src,
                                       mlib_s32         dx_l,
                                       mlib_s32         dx_r,
                                       mlib_s32         dy_t,
                                       mlib_s32         dy_b,
                                       mlib_s32         cmask,
                                       mlib_s32         nchan);

static void mlib_ImageConvCopyEdge_S32_3(mlib_image       *dst,
                                         const mlib_image *src,
                                         mlib_s32         dx_l,
                                         mlib_s32         dx_r,
                                         mlib_s32         dy_t,
                                         mlib_s32         dy_b,
                                         mlib_s32         cmask);

static void mlib_ImageConvCopyEdge_S32_4(mlib_image       *dst,
                                         const mlib_image *src,
                                         mlib_s32         dx_l,
                                         mlib_s32         dx_r,
                                         mlib_s32         dy_t,
                                         mlib_s32         dy_b,
                                         mlib_s32         cmask);

/***************************************************************/
#define VERT_EDGES(chan, type, mask)                             \
  type *pdst = (type *) mlib_ImageGetData(dst);                  \
  type *psrc = (type *) mlib_ImageGetData(src);                  \
  type *pdst_row, *psrc_row, *pdst_row_end;                      \
  mlib_s32 img_height = mlib_ImageGetHeight(dst);                \
  mlib_s32 img_width  = mlib_ImageGetWidth(dst);                 \
  mlib_s32 dst_stride = mlib_ImageGetStride(dst) / sizeof(type); \
  mlib_s32 src_stride = mlib_ImageGetStride(src) / sizeof(type); \
  mlib_s32 i, j, l;                                              \
  mlib_s32 emask, testchan;                                      \
  mlib_s32 img_width_t, img_width_b;                             \
  mlib_d64 *dpdst, *dpsrc, data0, data1;                         \
                                                                 \
  testchan = 1;                                                  \
  for (l = chan - 1; l >= 0; l--) {                              \
    if ((mask & testchan) == 0) {                                \
      testchan <<= 1;                                            \
      continue;                                                  \
    }                                                            \
    testchan <<= 1;                                              \
    for (j = 0; j < dx_l; j++) {                                 \
      for (i = dy_t; i < (img_height - dy_b); i++) {             \
        pdst[i*dst_stride + l + j*chan] =                        \
          psrc[i*src_stride + l + j*chan];                       \
      }                                                          \
    }                                                            \
    for (j = 0; j < dx_r; j++) {                                 \
      for (i = dy_t; i < (img_height - dy_b); i++) {             \
        pdst[i*dst_stride + l+(img_width-1 - j)*chan] =          \
        psrc[i*src_stride + l+(img_width-1 - j)*chan];           \
      }                                                          \
    }                                                            \
  }                                                              \
  img_width_t = img_width;                                       \
  img_width_b = img_width;                                       \
  if (((img_width * chan) == dst_stride) &&                      \
      ((img_width * chan) == src_stride)) {                      \
    img_width_t *= dy_t;                                         \
    img_width_b *= dy_b;                                         \
    dst_stride *= (img_height - dy_b);                           \
    src_stride *= (img_height - dy_b);                           \
    img_height = 2;                                              \
    dy_t = ((dy_t == 0) ? 0 : 1);                                \
    dy_b = ((dy_b == 0) ? 0 : 1);                                \
  }

/***************************************************************/
#define HORIZ_EDGES(chan, type, mask) {                         \
    testchan = 1;                                               \
    for (l = chan - 1; l >= 0; l--) {                           \
      if ((mask & testchan) == 0) {                             \
        testchan <<= 1;                                         \
        continue;                                               \
      }                                                         \
      testchan <<= 1;                                           \
      for (i = 0; i < dy_t; i++) {                              \
        for (j = 0; j < img_width_t; j++) {                     \
          pdst[i*dst_stride + l + j*chan] =                     \
            psrc[i*src_stride + l + j*chan];                    \
        }                                                       \
      }                                                         \
      for (i = 0; i < dy_b; i++) {                              \
        for (j = 0; j < img_width_b; j++) {                     \
          pdst[(img_height-1 - i)*dst_stride + l + j*chan] =    \
          psrc[(img_height-1 - i)*src_stride + l + j*chan];     \
        }                                                       \
      }                                                         \
    }                                                           \
    return;                                                     \
  }

/***************************************************************/
mlib_status mlib_ImageConvCopyEdge(mlib_image       *dst,
                                   const mlib_image *src,
                                   mlib_s32         dx_l,
                                   mlib_s32         dx_r,
                                   mlib_s32         dy_t,
                                   mlib_s32         dy_b,
                                   mlib_s32         cmask)
{
  mlib_s32 img_width = mlib_ImageGetWidth(dst);
  mlib_s32 img_height = mlib_ImageGetHeight(dst);

  if (dx_l + dx_r > img_width) {
    dx_l = img_width;
    dx_r = 0;
  }

  if (dy_t + dy_b > img_height) {
    dy_t = img_height;
    dy_b = 0;
  }

  switch (mlib_ImageGetType(dst)) {
    case MLIB_BIT:
      return mlib_ImageConvCopyEdge_Bit(dst, src, dx_l, dx_r, dy_t, dy_b, cmask);

    case MLIB_BYTE:
      switch (mlib_ImageGetChannels(dst)) {

        case 1:
          mlib_ImageConvCopyEdge_U8(dst, src, dx_l, dx_r, dy_t, dy_b, 1, 1);
          break;

        case 2:
          mlib_ImageConvCopyEdge_U8(dst, src, dx_l, dx_r, dy_t, dy_b, cmask, 2);
          break;

        case 3:
          mlib_ImageConvCopyEdge_U8_3(dst, src, dx_l, dx_r, dy_t, dy_b, cmask);
          break;

        case 4:
          mlib_ImageConvCopyEdge_U8(dst, src, dx_l, dx_r, dy_t, dy_b, cmask, 4);
          break;

        default:
          return MLIB_FAILURE;
      }

      break;

    case MLIB_SHORT:
    case MLIB_USHORT:
      switch (mlib_ImageGetChannels(dst)) {

        case 1:
          mlib_ImageConvCopyEdge_S16(dst, src, dx_l, dx_r, dy_t, dy_b, 1, 1);
          break;

        case 2:
          mlib_ImageConvCopyEdge_S16(dst, src, dx_l, dx_r, dy_t, dy_b, cmask, 2);
          break;

        case 3:
          mlib_ImageConvCopyEdge_S16_3(dst, src, dx_l, dx_r, dy_t, dy_b, cmask);
          break;

        case 4:
          mlib_ImageConvCopyEdge_S16(dst, src, dx_l, dx_r, dy_t, dy_b, cmask, 4);
          break;

        default:
          return MLIB_FAILURE;
      }

      break;

    case MLIB_INT:
    case MLIB_FLOAT:
      switch (mlib_ImageGetChannels(dst)) {

        case 1:
          mlib_ImageConvCopyEdge_S32(dst, src, dx_l, dx_r, dy_t, dy_b, 1, 1);
          break;

        case 2:
          mlib_ImageConvCopyEdge_S32(dst, src, dx_l, dx_r, dy_t, dy_b, cmask, 2);
          break;

        case 3:
          mlib_ImageConvCopyEdge_S32_3(dst, src, dx_l, dx_r, dy_t, dy_b, cmask);
          break;

        case 4:
          mlib_ImageConvCopyEdge_S32_4(dst, src, dx_l, dx_r, dy_t, dy_b, cmask);
          break;

        default:
          return MLIB_FAILURE;
      }

      break;

    case MLIB_DOUBLE:
      return mlib_ImageConvCopyEdge_Fp(dst, src, dx_l, dx_r, dy_t, dy_b, cmask);

    default:
      return MLIB_FAILURE;
  }

  return MLIB_SUCCESS;
}

/***************************************************************/
void mlib_ImageConvCopyEdge_U8(mlib_image       *dst,
                               const mlib_image *src,
                               mlib_s32         dx_l,
                               mlib_s32         dx_r,
                               mlib_s32         dy_t,
                               mlib_s32         dy_b,
                               mlib_s32         cmask,
                               mlib_s32         nchan)
{
  mlib_s32 tmask = cmask & ((1 << nchan) - 1), mask1, offset;
  VERT_EDGES(nchan, mlib_u8, cmask);

  if (img_width < 16 / nchan)
    HORIZ_EDGES(nchan, mlib_u8, cmask);

  if (nchan == 1)
    tmask = 0xFFFF;
  else if (nchan == 2) {
    tmask |= (tmask << 2);
    tmask |= (tmask << 4);
    tmask |= (tmask << 8);
  }
  else if (nchan == 4) {
    tmask |= (tmask << 4);
    tmask |= (tmask << 8);
  }

  for (i = 0; i < dy_t; i++) {
    pdst_row = pdst + i * dst_stride,
      psrc_row = psrc + i * src_stride, pdst_row_end = pdst_row + img_width_t * nchan - 1;
    dpdst = (mlib_d64 *) ((mlib_addr) pdst_row & ~7);
    offset = pdst_row - (mlib_u8 *) dpdst;
    dpsrc = (mlib_d64 *) vis_alignaddr(psrc_row, -offset);
    mask1 = (tmask >> offset);
    data0 = *dpsrc++;
    data1 = *dpsrc++;
    emask = vis_edge8(pdst_row, pdst_row_end) & mask1;
    vis_pst_8(vis_faligndata(data0, data1), dpdst++, emask);
    j = (mlib_s32) ((mlib_u8 *) dpdst - pdst_row);
    data0 = data1;
    for (; j < (img_width_t * nchan - 8); j += 8) {
      data1 = *dpsrc++;
      vis_pst_8(vis_faligndata(data0, data1), dpdst++, mask1);
      data0 = data1;
    }

    data1 = *dpsrc++;
    emask = vis_edge8(dpdst, pdst_row_end) & mask1;
    vis_pst_8(vis_faligndata(data0, data1), dpdst++, emask);
  }

  for (i = 0; i < dy_b; i++) {
    pdst_row = pdst + (img_height - 1 - i) * dst_stride;
    psrc_row = psrc + (img_height - 1 - i) * src_stride;
    pdst_row_end = pdst_row + img_width_b * nchan - 1;
    dpdst = (mlib_d64 *) ((mlib_addr) pdst_row & ~7);
    offset = pdst_row - (mlib_u8 *) dpdst;
    dpsrc = (mlib_d64 *) vis_alignaddr(psrc_row, -offset);
    mask1 = (tmask >> offset);
    data0 = *dpsrc++;
    data1 = *dpsrc++;
    emask = vis_edge8(pdst_row, pdst_row_end) & mask1;
    vis_pst_8(vis_faligndata(data0, data1), dpdst++, emask);
    j = (mlib_s32) ((mlib_u8 *) dpdst - pdst_row);
    data0 = data1;
    for (; j < (img_width_b * nchan - 8); j += 8) {
      data1 = *dpsrc++;
      vis_pst_8(vis_faligndata(data0, data1), dpdst++, mask1);
      data0 = data1;
    }

    data1 = *dpsrc++;
    emask = vis_edge8(dpdst, pdst_row_end) & mask1;
    vis_pst_8(vis_faligndata(data0, data1), dpdst++, emask);
  }
}

/***************************************************************/
void mlib_ImageConvCopyEdge_U8_3(mlib_image       *dst,
                                 const mlib_image *src,
                                 mlib_s32         dx_l,
                                 mlib_s32         dx_r,
                                 mlib_s32         dy_t,
                                 mlib_s32         dy_b,
                                 mlib_s32         cmask)
{
  mlib_s32 tmask = cmask & 7, mask0, mask1, mask2, offset;

  VERT_EDGES(3, mlib_u8, cmask);

  if (img_width < 16)
    HORIZ_EDGES(3, mlib_u8, cmask);

  tmask |= (tmask << 3);
  tmask |= (tmask << 6);
  tmask |= (tmask << 12);
  for (i = 0; i < dy_t; i++) {
    pdst_row = pdst + i * dst_stride,
      psrc_row = psrc + i * src_stride, pdst_row_end = pdst_row + img_width_t * 3 - 1;
    dpdst = (mlib_d64 *) ((mlib_addr) pdst_row & ~7);
    offset = pdst_row - (mlib_u8 *) dpdst;
    dpsrc = (mlib_d64 *) vis_alignaddr(psrc_row, -offset);
    mask2 = (tmask >> (offset + 1));
    mask0 = mask2 >> 1;
    mask1 = mask0 >> 1;
    data0 = *dpsrc++;
    data1 = *dpsrc++;
    emask = vis_edge8(pdst_row, pdst_row_end) & mask2;
    vis_pst_8(vis_faligndata(data0, data1), dpdst++, emask);
    data0 = data1;
    j = (mlib_s32) ((mlib_u8 *) dpdst - pdst_row);
    for (; j < (img_width_t * 3 - 24); j += 24) {
      data1 = *dpsrc++;
      vis_pst_8(vis_faligndata(data0, data1), dpdst, mask0);
      data0 = data1;
      data1 = *dpsrc++;
      vis_pst_8(vis_faligndata(data0, data1), dpdst + 1, mask1);
      data0 = data1;
      data1 = *dpsrc++;
      vis_pst_8(vis_faligndata(data0, data1), dpdst + 2, mask2);
      data0 = data1;
      dpdst += 3;
    }

    if (j < (img_width_t * 3 - 8)) {
      data1 = *dpsrc++;
      vis_pst_8(vis_faligndata(data0, data1), dpdst++, mask0);
      data0 = data1;

      if (j < (img_width_t * 3 - 16)) {
        data1 = *dpsrc++;
        vis_pst_8(vis_faligndata(data0, data1), dpdst++, mask1);
        data0 = data1;
        mask0 = mask2;
      }
      else {
        mask0 = mask1;
      }
    }

    data1 = *dpsrc++;
    emask = vis_edge8(dpdst, pdst_row_end) & mask0;
    vis_pst_8(vis_faligndata(data0, data1), dpdst, emask);
  }

  for (i = 0; i < dy_b; i++) {
    pdst_row = pdst + (img_height - 1 - i) * dst_stride;
    psrc_row = psrc + (img_height - 1 - i) * src_stride;
    pdst_row_end = pdst_row + img_width_b * 3 - 1;

    dpdst = (mlib_d64 *) ((mlib_addr) pdst_row & ~7);
    offset = pdst_row - (mlib_u8 *) dpdst;
    dpsrc = (mlib_d64 *) vis_alignaddr(psrc_row, -offset);
    mask2 = (tmask >> (offset + 1));
    mask0 = mask2 >> 1;
    mask1 = mask0 >> 1;
    data0 = *dpsrc++;
    data1 = *dpsrc++;
    emask = vis_edge8(pdst_row, pdst_row_end) & mask2;
    vis_pst_8(vis_faligndata(data0, data1), dpdst++, emask);
    data0 = data1;
    j = (mlib_s32) ((mlib_u8 *) dpdst - pdst_row);
    for (; j < (img_width_b * 3 - 24); j += 24) {
      data1 = *dpsrc++;
      vis_pst_8(vis_faligndata(data0, data1), dpdst, mask0);
      data0 = data1;
      data1 = *dpsrc++;
      vis_pst_8(vis_faligndata(data0, data1), dpdst + 1, mask1);
      data0 = data1;
      data1 = *dpsrc++;
      vis_pst_8(vis_faligndata(data0, data1), dpdst + 2, mask2);
      data0 = data1;
      dpdst += 3;
    }

    if (j < (img_width_b * 3 - 8)) {
      data1 = *dpsrc++;
      vis_pst_8(vis_faligndata(data0, data1), dpdst++, mask0);
      data0 = data1;

      if (j < (img_width_b * 3 - 16)) {
        data1 = *dpsrc++;
        vis_pst_8(vis_faligndata(data0, data1), dpdst++, mask1);
        data0 = data1;
        mask0 = mask2;
      }
      else {
        mask0 = mask1;
      }
    }

    data1 = *dpsrc++;
    emask = vis_edge8(dpdst, pdst_row_end) & mask0;
    vis_pst_8(vis_faligndata(data0, data1), dpdst, emask);
  }
}

/***************************************************************/
void mlib_ImageConvCopyEdge_S16(mlib_image       *dst,
                                const mlib_image *src,
                                mlib_s32         dx_l,
                                mlib_s32         dx_r,
                                mlib_s32         dy_t,
                                mlib_s32         dy_b,
                                mlib_s32         cmask,
                                mlib_s32         nchan)
{
  mlib_s32 tmask = cmask & ((1 << nchan) - 1), mask1, offset;
  VERT_EDGES(nchan, mlib_s16, cmask);

  if (img_width < 16 / nchan)
    HORIZ_EDGES(nchan, mlib_s16, cmask);

  if (nchan == 1)
    tmask = 0xFFFF;
  else if (nchan == 2) {
    tmask |= (tmask << 2);
    tmask |= (tmask << 4);
  }
  else if (nchan == 4)
    tmask |= (tmask << 4);

  for (i = 0; i < dy_t; i++) {
    pdst_row = pdst + i * dst_stride,
      psrc_row = psrc + i * src_stride, pdst_row_end = pdst_row + img_width_t * nchan - 1;
    dpdst = (mlib_d64 *) ((mlib_addr) pdst_row & ~7);
    offset = pdst_row - (mlib_s16 *) dpdst;
    dpsrc = (mlib_d64 *) vis_alignaddr(psrc_row, -(offset << 1));
    mask1 = (tmask >> offset);
    data0 = *dpsrc++;
    data1 = *dpsrc++;
    emask = vis_edge16(pdst_row, pdst_row_end) & mask1;
    vis_pst_16(vis_faligndata(data0, data1), dpdst++, emask);
    j = (mlib_s32) ((mlib_s16 *) dpdst - pdst_row);
    data0 = data1;
    for (; j < (img_width_t * nchan - 4); j += 4) {
      data1 = *dpsrc++;
      vis_pst_16(vis_faligndata(data0, data1), dpdst++, mask1);
      data0 = data1;
    }

    data1 = *dpsrc++;
    emask = vis_edge16(dpdst, pdst_row_end) & mask1;
    vis_pst_16(vis_faligndata(data0, data1), dpdst++, emask);
  }

  for (i = 0; i < dy_b; i++) {
    pdst_row = pdst + (img_height - 1 - i) * dst_stride;
    psrc_row = psrc + (img_height - 1 - i) * src_stride;
    pdst_row_end = pdst_row + img_width_b * nchan - 1;
    dpdst = (mlib_d64 *) ((mlib_addr) pdst_row & ~7);
    offset = pdst_row - (mlib_s16 *) dpdst;
    dpsrc = (mlib_d64 *) vis_alignaddr(psrc_row, -(offset << 1));
    mask1 = (tmask >> offset);
    data0 = *dpsrc++;
    data1 = *dpsrc++;
    emask = vis_edge16(pdst_row, pdst_row_end) & mask1;
    vis_pst_16(vis_faligndata(data0, data1), dpdst++, emask);
    j = (mlib_s32) ((mlib_s16 *) dpdst - pdst_row);
    data0 = data1;
    for (; j < (img_width_b * nchan - 4); j += 4) {
      data1 = *dpsrc++;
      vis_pst_16(vis_faligndata(data0, data1), dpdst++, mask1);
      data0 = data1;
    }

    data1 = *dpsrc++;
    emask = vis_edge16(dpdst, pdst_row_end) & mask1;
    vis_pst_16(vis_faligndata(data0, data1), dpdst++, emask);
  }
}

/***************************************************************/
void mlib_ImageConvCopyEdge_S16_3(mlib_image       *dst,
                                  const mlib_image *src,
                                  mlib_s32         dx_l,
                                  mlib_s32         dx_r,
                                  mlib_s32         dy_t,
                                  mlib_s32         dy_b,
                                  mlib_s32         cmask)
{
  mlib_s32 tmask = cmask & 7, mask0, mask1, mask2, offset;

  VERT_EDGES(3, mlib_s16, cmask);

  if (img_width < 16)
    HORIZ_EDGES(3, mlib_s16, cmask);

  tmask |= (tmask << 3);
  tmask |= (tmask << 6);
  tmask |= (tmask << 12);
  for (i = 0; i < dy_t; i++) {
    pdst_row = pdst + i * dst_stride,
      psrc_row = psrc + i * src_stride, pdst_row_end = pdst_row + img_width_t * 3 - 1;
    dpdst = (mlib_d64 *) ((mlib_addr) pdst_row & ~7);
    offset = pdst_row - (mlib_s16 *) dpdst;
    dpsrc = (mlib_d64 *) vis_alignaddr(psrc_row, -(offset << 1));
    mask2 = (tmask >> (offset + 2));
    mask0 = mask2 >> 2;
    mask1 = mask0 >> 2;
    data0 = *dpsrc++;
    data1 = *dpsrc++;
    emask = vis_edge16(pdst_row, pdst_row_end) & mask2;
    vis_pst_16(vis_faligndata(data0, data1), dpdst++, emask);
    data0 = data1;
    j = (mlib_s32) ((mlib_s16 *) dpdst - pdst_row);
    for (; j < (img_width_t * 3 - 12); j += 12) {
      data1 = *dpsrc++;
      vis_pst_16(vis_faligndata(data0, data1), dpdst, mask0);
      data0 = data1;
      data1 = *dpsrc++;
      vis_pst_16(vis_faligndata(data0, data1), dpdst + 1, mask1);
      data0 = data1;
      data1 = *dpsrc++;
      vis_pst_16(vis_faligndata(data0, data1), dpdst + 2, mask2);
      data0 = data1;
      dpdst += 3;
    }

    if (j < (img_width_t * 3 - 4)) {
      data1 = *dpsrc++;
      vis_pst_16(vis_faligndata(data0, data1), dpdst++, mask0);
      data0 = data1;

      if (j < (img_width_t * 3 - 8)) {
        data1 = *dpsrc++;
        vis_pst_16(vis_faligndata(data0, data1), dpdst++, mask1);
        data0 = data1;
        mask0 = mask2;
      }
      else {
        mask0 = mask1;
      }
    }

    data1 = *dpsrc++;
    emask = vis_edge16(dpdst, pdst_row_end) & mask0;
    vis_pst_16(vis_faligndata(data0, data1), dpdst, emask);
  }

  for (i = 0; i < dy_b; i++) {
    pdst_row = pdst + (img_height - 1 - i) * dst_stride;
    psrc_row = psrc + (img_height - 1 - i) * src_stride;
    pdst_row_end = pdst_row + img_width_b * 3 - 1;

    dpdst = (mlib_d64 *) ((mlib_addr) pdst_row & ~7);
    offset = pdst_row - (mlib_s16 *) dpdst;
    dpsrc = (mlib_d64 *) vis_alignaddr(psrc_row, -(offset << 1));
    mask2 = (tmask >> (offset + 2));
    mask0 = mask2 >> 2;
    mask1 = mask0 >> 2;
    data0 = *dpsrc++;
    data1 = *dpsrc++;
    emask = vis_edge16(pdst_row, pdst_row_end) & mask2;
    vis_pst_16(vis_faligndata(data0, data1), dpdst++, emask);
    data0 = data1;
    j = (mlib_s32) ((mlib_s16 *) dpdst - pdst_row);
    for (; j < (img_width_b * 3 - 12); j += 12) {
      data1 = *dpsrc++;
      vis_pst_16(vis_faligndata(data0, data1), dpdst, mask0);
      data0 = data1;
      data1 = *dpsrc++;
      vis_pst_16(vis_faligndata(data0, data1), dpdst + 1, mask1);
      data0 = data1;
      data1 = *dpsrc++;
      vis_pst_16(vis_faligndata(data0, data1), dpdst + 2, mask2);
      data0 = data1;
      dpdst += 3;
    }

    if (j < (img_width_b * 3 - 4)) {
      data1 = *dpsrc++;
      vis_pst_16(vis_faligndata(data0, data1), dpdst++, mask0);
      data0 = data1;

      if (j < (img_width_b * 3 - 8)) {
        data1 = *dpsrc++;
        vis_pst_16(vis_faligndata(data0, data1), dpdst++, mask1);
        data0 = data1;
        mask0 = mask2;
      }
      else {
        mask0 = mask1;
      }
    }

    data1 = *dpsrc++;
    emask = vis_edge16(dpdst, pdst_row_end) & mask0;
    vis_pst_16(vis_faligndata(data0, data1), dpdst, emask);
  }
}

/***************************************************************/
void mlib_ImageConvCopyEdge_S32(mlib_image       *dst,
                                const mlib_image *src,
                                mlib_s32         dx_l,
                                mlib_s32         dx_r,
                                mlib_s32         dy_t,
                                mlib_s32         dy_b,
                                mlib_s32         cmask,
                                mlib_s32         nchan)
{
  mlib_s32 tmask = cmask & ((1 << nchan) - 1), mask1, offset;
  VERT_EDGES(nchan, mlib_s32, cmask);

  if (img_width < 16 / nchan)
    HORIZ_EDGES(nchan, mlib_s32, cmask);

  if (nchan == 1)
    tmask = 0xFFFF;
  else if (nchan == 2) {
    tmask |= (tmask << 2);
    tmask |= (tmask << 4);
  }

  for (i = 0; i < dy_t; i++) {
    pdst_row = pdst + i * dst_stride,
      psrc_row = psrc + i * src_stride, pdst_row_end = pdst_row + img_width_t * nchan - 1;
    dpdst = (mlib_d64 *) ((mlib_addr) pdst_row & ~7);
    offset = pdst_row - (mlib_s32 *) dpdst;
    dpsrc = (mlib_d64 *) vis_alignaddr(psrc_row, -(offset << 2));
    mask1 = (tmask >> offset);
    data0 = *dpsrc++;
    data1 = *dpsrc++;
    emask = vis_edge32(pdst_row, pdst_row_end) & mask1;
    vis_pst_32(vis_faligndata(data0, data1), dpdst++, emask);
    j = (mlib_s32) ((mlib_s32 *) dpdst - pdst_row);
    data0 = data1;
    for (; j < (img_width_t * nchan - 2); j += 2) {
      data1 = *dpsrc++;
      vis_pst_32(vis_faligndata(data0, data1), dpdst++, mask1);
      data0 = data1;
    }

    data1 = *dpsrc++;
    emask = vis_edge32(dpdst, pdst_row_end) & mask1;
    vis_pst_32(vis_faligndata(data0, data1), dpdst++, emask);
  }

  for (i = 0; i < dy_b; i++) {
    pdst_row = pdst + (img_height - 1 - i) * dst_stride;
    psrc_row = psrc + (img_height - 1 - i) * src_stride;
    pdst_row_end = pdst_row + img_width_b * nchan - 1;
    dpdst = (mlib_d64 *) ((mlib_addr) pdst_row & ~7);
    offset = pdst_row - (mlib_s32 *) dpdst;
    dpsrc = (mlib_d64 *) vis_alignaddr(psrc_row, -(offset << 2));
    mask1 = (tmask >> offset);
    data0 = *dpsrc++;
    data1 = *dpsrc++;
    emask = vis_edge32(pdst_row, pdst_row_end) & mask1;
    vis_pst_32(vis_faligndata(data0, data1), dpdst++, emask);
    j = (mlib_s32) ((mlib_s32 *) dpdst - pdst_row);
    data0 = data1;
    for (; j < (img_width_b * nchan - 2); j += 2) {
      data1 = *dpsrc++;
      vis_pst_32(vis_faligndata(data0, data1), dpdst++, mask1);
      data0 = data1;
    }

    data1 = *dpsrc++;
    emask = vis_edge32(dpdst, pdst_row_end) & mask1;
    vis_pst_32(vis_faligndata(data0, data1), dpdst++, emask);
  }
}

/***************************************************************/
void mlib_ImageConvCopyEdge_S32_3(mlib_image       *dst,
                                  const mlib_image *src,
                                  mlib_s32         dx_l,
                                  mlib_s32         dx_r,
                                  mlib_s32         dy_t,
                                  mlib_s32         dy_b,
                                  mlib_s32         cmask)
{
  mlib_s32 tmask = cmask & 7, mask0, mask1, mask2, offset;

  VERT_EDGES(3, mlib_s32, cmask);

  if (img_width < 16)
    HORIZ_EDGES(3, mlib_s32, cmask);

  tmask |= (tmask << 3);
  tmask |= (tmask << 6);
  tmask |= (tmask << 12);
  for (i = 0; i < dy_t; i++) {
    pdst_row = pdst + i * dst_stride,
      psrc_row = psrc + i * src_stride, pdst_row_end = pdst_row + img_width_t * 3 - 1;
    dpdst = (mlib_d64 *) ((mlib_addr) pdst_row & ~7);
    offset = pdst_row - (mlib_s32 *) dpdst;
    dpsrc = (mlib_d64 *) vis_alignaddr(psrc_row, -(offset << 2));
    mask2 = (tmask >> (offset + 1));
    mask0 = mask2 >> 1;
    mask1 = mask0 >> 1;
    data0 = *dpsrc++;
    data1 = *dpsrc++;
    emask = vis_edge32(pdst_row, pdst_row_end) & mask2;
    vis_pst_32(vis_faligndata(data0, data1), dpdst++, emask);
    data0 = data1;
    j = (mlib_s32) ((mlib_s32 *) dpdst - pdst_row);
    for (; j < (img_width_t * 3 - 6); j += 6) {
      data1 = *dpsrc++;
      vis_pst_32(vis_faligndata(data0, data1), dpdst, mask0);
      data0 = data1;
      data1 = *dpsrc++;
      vis_pst_32(vis_faligndata(data0, data1), dpdst + 1, mask1);
      data0 = data1;
      data1 = *dpsrc++;
      vis_pst_32(vis_faligndata(data0, data1), dpdst + 2, mask2);
      data0 = data1;
      dpdst += 3;
    }

    if (j < (img_width_t * 3 - 2)) {
      data1 = *dpsrc++;
      vis_pst_32(vis_faligndata(data0, data1), dpdst++, mask0);
      data0 = data1;

      if (j < (img_width_t * 3 - 4)) {
        data1 = *dpsrc++;
        vis_pst_32(vis_faligndata(data0, data1), dpdst++, mask1);
        data0 = data1;
        mask0 = mask2;
      }
      else {
        mask0 = mask1;
      }
    }

    data1 = *dpsrc++;
    emask = vis_edge32(dpdst, pdst_row_end) & mask0;
    vis_pst_32(vis_faligndata(data0, data1), dpdst, emask);
  }

  for (i = 0; i < dy_b; i++) {
    pdst_row = pdst + (img_height - 1 - i) * dst_stride;
    psrc_row = psrc + (img_height - 1 - i) * src_stride;
    pdst_row_end = pdst_row + img_width_b * 3 - 1;

    dpdst = (mlib_d64 *) ((mlib_addr) pdst_row & ~7);
    offset = pdst_row - (mlib_s32 *) dpdst;
    dpsrc = (mlib_d64 *) vis_alignaddr(psrc_row, -(offset << 2));
    mask2 = (tmask >> (offset + 1));
    mask0 = mask2 >> 1;
    mask1 = mask0 >> 1;
    data0 = *dpsrc++;
    data1 = *dpsrc++;
    emask = vis_edge32(pdst_row, pdst_row_end) & mask2;
    vis_pst_32(vis_faligndata(data0, data1), dpdst++, emask);
    data0 = data1;
    j = (mlib_s32) ((mlib_s32 *) dpdst - pdst_row);
    for (; j < (img_width_b * 3 - 6); j += 6) {
      data1 = *dpsrc++;
      vis_pst_32(vis_faligndata(data0, data1), dpdst, mask0);
      data0 = data1;
      data1 = *dpsrc++;
      vis_pst_32(vis_faligndata(data0, data1), dpdst + 1, mask1);
      data0 = data1;
      data1 = *dpsrc++;
      vis_pst_32(vis_faligndata(data0, data1), dpdst + 2, mask2);
      data0 = data1;
      dpdst += 3;
    }

    if (j < (img_width_b * 3 - 2)) {
      data1 = *dpsrc++;
      vis_pst_32(vis_faligndata(data0, data1), dpdst++, mask0);
      data0 = data1;

      if (j < (img_width_b * 3 - 4)) {
        data1 = *dpsrc++;
        vis_pst_32(vis_faligndata(data0, data1), dpdst++, mask1);
        data0 = data1;
        mask0 = mask2;
      }
      else {
        mask0 = mask1;
      }
    }

    data1 = *dpsrc++;
    emask = vis_edge32(dpdst, pdst_row_end) & mask0;
    vis_pst_32(vis_faligndata(data0, data1), dpdst, emask);
  }
}

/***************************************************************/
void mlib_ImageConvCopyEdge_S32_4(mlib_image       *dst,
                                  const mlib_image *src,
                                  mlib_s32         dx_l,
                                  mlib_s32         dx_r,
                                  mlib_s32         dy_t,
                                  mlib_s32         dy_b,
                                  mlib_s32         cmask)
{
  mlib_s32 tmask = cmask & 15, mask0, mask1, offset;

  VERT_EDGES(4, mlib_s32, cmask);

  if (img_width < 16)
    HORIZ_EDGES(4, mlib_s32, cmask);

  tmask |= (tmask << 4);
  tmask |= (tmask << 8);
  for (i = 0; i < dy_t; i++) {
    pdst_row = pdst + i * dst_stride,
      psrc_row = psrc + i * src_stride, pdst_row_end = pdst_row + img_width_t * 4 - 1;
    dpdst = (mlib_d64 *) ((mlib_addr) pdst_row & ~7);
    offset = pdst_row - (mlib_s32 *) dpdst;
    dpsrc = (mlib_d64 *) vis_alignaddr(psrc_row, -(offset << 2));
    mask1 = (tmask >> (offset + 2));
    mask0 = mask1 >> 2;
    data0 = *dpsrc++;
    data1 = *dpsrc++;
    emask = vis_edge32(pdst_row, pdst_row_end) & mask1;
    vis_pst_32(vis_faligndata(data0, data1), dpdst++, emask);
    data0 = data1;
    j = (mlib_s32) ((mlib_s32 *) dpdst - pdst_row);
    for (; j < (img_width_t * 4 - 4); j += 4) {
      data1 = *dpsrc++;
      vis_pst_32(vis_faligndata(data0, data1), dpdst, mask0);
      data0 = *dpsrc++;
      vis_pst_32(vis_faligndata(data1, data0), dpdst + 1, mask1);
      dpdst += 2;
    }

    if (j < (img_width_t * 4 - 2)) {
      data1 = *dpsrc++;
      vis_pst_32(vis_faligndata(data0, data1), dpdst++, mask0);
      data0 = data1;
      mask0 = mask1;
    }

    data1 = *dpsrc++;
    emask = vis_edge32(dpdst, pdst_row_end) & mask0;
    vis_pst_32(vis_faligndata(data0, data1), dpdst, emask);
  }

  for (i = 0; i < dy_b; i++) {
    pdst_row = pdst + (img_height - 1 - i) * dst_stride;
    psrc_row = psrc + (img_height - 1 - i) * src_stride;
    pdst_row_end = pdst_row + img_width_b * 4 - 1;

    dpdst = (mlib_d64 *) ((mlib_addr) pdst_row & ~7);
    offset = pdst_row - (mlib_s32 *) dpdst;
    dpsrc = (mlib_d64 *) vis_alignaddr(psrc_row, -(offset << 2));
    mask1 = (tmask >> (offset + 2));
    mask0 = mask1 >> 2;
    data0 = *dpsrc++;
    data1 = *dpsrc++;
    emask = vis_edge32(pdst_row, pdst_row_end) & mask1;
    vis_pst_32(vis_faligndata(data0, data1), dpdst++, emask);
    data0 = data1;
    j = (mlib_s32) ((mlib_s32 *) dpdst - pdst_row);
    for (; j < (img_width_b * 4 - 4); j += 4) {
      data1 = *dpsrc++;
      vis_pst_32(vis_faligndata(data0, data1), dpdst, mask0);
      data0 = *dpsrc++;
      vis_pst_32(vis_faligndata(data1, data0), dpdst + 1, mask1);
      dpdst += 2;
    }

    if (j < (img_width_b * 4 - 2)) {
      data1 = *dpsrc++;
      vis_pst_32(vis_faligndata(data0, data1), dpdst++, mask0);
      data0 = data1;
      mask0 = mask1;
    }

    data1 = *dpsrc++;
    emask = vis_edge32(dpdst, pdst_row_end) & mask0;
    vis_pst_32(vis_faligndata(data0, data1), dpdst, emask);
  }
}

/***************************************************************/
