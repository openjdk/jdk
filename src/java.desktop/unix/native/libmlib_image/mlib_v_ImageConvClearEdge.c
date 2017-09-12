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
 *      mlib_ImageConvClearEdge  - Set edge of an image to a specific
 *                                        color. (VIS version)
 *
 * SYNOPSIS
 *      mlib_status mlib_ImageConvClearEdge(mlib_image     *dst,
 *                                          mlib_s32       dx_l,
 *                                          mlib_s32       dx_r,
 *                                          mlib_s32       dy_t,
 *                                          mlib_s32       dy_b,
 *                                          const mlib_s32 *color,
 *                                          mlib_s32       cmask)
 *
 * ARGUMENT
 *      dst       Pointer to an image.
 *      dx_l      Number of columns on the left side of the
 *                image to be cleared.
 *      dx_r      Number of columns on the right side of the
 *                image to be cleared.
 *      dy_t      Number of rows on the top edge of the
 *                image to be cleared.
 *      dy_b      Number of rows on the top edge of the
 *                image to be cleared.
 *      color     Pointer to the color that the edges are set to.
 *      cmask     Channel mask to indicate the channels to be convolved.
 *                Each bit of which represents a channel in the image. The
 *                channels corresponded to 1 bits are those to be processed.
 *
 * RESTRICTION
 *      dst can have 1, 2, 3 or 4 channels of MLIB_BYTE or MLIB_SHORT or MLIB_INT
 *      data type.
 *
 * DESCRIPTION
 *      Set edge of an image to a specific color. (VIS version)
 *      The unselected channels are not overwritten.
 *      If src and dst have just one channel,
 *      cmask is ignored.
 */

#include "mlib_image.h"
#include "vis_proto.h"
#include "mlib_ImageConvEdge.h"

/***************************************************************/
static void mlib_ImageConvClearEdge_U8_1(mlib_image     *dst,
                                         mlib_s32       dx_l,
                                         mlib_s32       dx_r,
                                         mlib_s32       dy_t,
                                         mlib_s32       dy_b,
                                         const mlib_s32 *color);

static void mlib_ImageConvClearEdge_U8_2(mlib_image     *dst,
                                         mlib_s32       dx_l,
                                         mlib_s32       dx_r,
                                         mlib_s32       dy_t,
                                         mlib_s32       dy_b,
                                         const mlib_s32 *color,
                                         mlib_s32       cmask);

static void mlib_ImageConvClearEdge_U8_3(mlib_image     *dst,
                                         mlib_s32       dx_l,
                                         mlib_s32       dx_r,
                                         mlib_s32       dy_t,
                                         mlib_s32       dy_b,
                                         const mlib_s32 *color,
                                         mlib_s32       cmask);

static void mlib_ImageConvClearEdge_U8_4(mlib_image     *dst,
                                         mlib_s32       dx_l,
                                         mlib_s32       dx_r,
                                         mlib_s32       dy_t,
                                         mlib_s32       dy_b,
                                         const mlib_s32 *color,
                                         mlib_s32       cmask);

static void mlib_ImageConvClearEdge_S16_1(mlib_image     *dst,
                                          mlib_s32       dx_l,
                                          mlib_s32       dx_r,
                                          mlib_s32       dy_t,
                                          mlib_s32       dy_b,
                                          const mlib_s32 *color);

static void mlib_ImageConvClearEdge_S16_2(mlib_image     *dst,
                                          mlib_s32       dx_l,
                                          mlib_s32       dx_r,
                                          mlib_s32       dy_t,
                                          mlib_s32       dy_b,
                                          const mlib_s32 *color,
                                          mlib_s32       cmask);

static void mlib_ImageConvClearEdge_S16_3(mlib_image     *dst,
                                          mlib_s32       dx_l,
                                          mlib_s32       dx_r,
                                          mlib_s32       dy_t,
                                          mlib_s32       dy_b,
                                          const mlib_s32 *color,
                                          mlib_s32       cmask);

static void mlib_ImageConvClearEdge_S16_4(mlib_image     *dst,
                                          mlib_s32       dx_l,
                                          mlib_s32       dx_r,
                                          mlib_s32       dy_t,
                                          mlib_s32       dy_b,
                                          const mlib_s32 *color,
                                          mlib_s32       cmask);

static void mlib_ImageConvClearEdge_S32_1(mlib_image     *dst,
                                          mlib_s32       dx_l,
                                          mlib_s32       dx_r,
                                          mlib_s32       dy_t,
                                          mlib_s32       dy_b,
                                          const mlib_s32 *color);

static void mlib_ImageConvClearEdge_S32_2(mlib_image     *dst,
                                          mlib_s32       dx_l,
                                          mlib_s32       dx_r,
                                          mlib_s32       dy_t,
                                          mlib_s32       dy_b,
                                          const mlib_s32 *color,
                                          mlib_s32       cmask);

static void mlib_ImageConvClearEdge_S32_3(mlib_image     *dst,
                                          mlib_s32       dx_l,
                                          mlib_s32       dx_r,
                                          mlib_s32       dy_t,
                                          mlib_s32       dy_b,
                                          const mlib_s32 *color,
                                          mlib_s32       cmask);

static void mlib_ImageConvClearEdge_S32_4(mlib_image     *dst,
                                          mlib_s32       dx_l,
                                          mlib_s32       dx_r,
                                          mlib_s32       dy_t,
                                          mlib_s32       dy_b,
                                          const mlib_s32 *color,
                                          mlib_s32       cmask);

/***************************************************************/
#define VERT_EDGES(chan, type, mask)                             \
  type *pdst = (type *) mlib_ImageGetData(dst);                  \
  type *pdst_row, *pdst_row_end;                                 \
  type color_i;                                                  \
  mlib_s32 dst_height = mlib_ImageGetHeight(dst);                \
  mlib_s32 dst_width  = mlib_ImageGetWidth(dst);                 \
  mlib_s32 dst_stride = mlib_ImageGetStride(dst) / sizeof(type); \
  mlib_s32 i, j, l;                                              \
  mlib_s32 emask, testchan;                                      \
  mlib_s32 dst_width_t, dst_width_b;                             \
  mlib_d64 *dpdst;                                               \
                                                                 \
  testchan = 1;                                                  \
  for (l = chan - 1; l >= 0; l--) {                              \
    if ((mask & testchan) == 0) {                                \
      testchan <<= 1;                                            \
      continue;                                                  \
    }                                                            \
    testchan <<= 1;                                              \
    color_i = (type)color[l];                                    \
    for (j = 0; j < dx_l; j++) {                                 \
      for (i = dy_t; i < (dst_height - dy_b); i++) {             \
        pdst[i*dst_stride + l + j*chan] = color_i;               \
      }                                                          \
    }                                                            \
    for (j = 0; j < dx_r; j++) {                                 \
      for (i = dy_t; i < (dst_height - dy_b); i++) {             \
        pdst[i*dst_stride + l+(dst_width-1 - j)*chan] = color_i; \
      }                                                          \
    }                                                            \
  }                                                              \
                                                                 \
  dst_width_t = dst_width;                                       \
  dst_width_b = dst_width;                                       \
  if ((dst_width * chan) == dst_stride) {                        \
    dst_width_t *= dy_t;                                         \
    dst_width_b *= dy_b;                                         \
    dst_stride *= (dst_height - dy_b);                           \
    dst_height = 2;                                              \
    dy_t = ((dy_t == 0) ? 0 : 1);                                \
    dy_b = ((dy_b == 0) ? 0 : 1);                                \
  }

/***************************************************************/
#define HORIZ_EDGES(chan, type, mask)                            \
{                                                                \
  testchan = 1;                                                  \
  for (l = chan - 1; l >= 0; l--) {                              \
    if ((mask & testchan) == 0) {                                \
      testchan <<= 1;                                            \
      continue;                                                  \
    }                                                            \
    testchan <<= 1;                                              \
    color_i = (type) color[l];                                   \
    for (i = 0; i < dy_t; i++) {                                 \
      for (j = 0; j < dst_width_t; j++) {                        \
        pdst[i * dst_stride + l + j * chan] = color_i;           \
      }                                                          \
    }                                                            \
    for (i = 0; i < dy_b; i++) {                                 \
      for (j = 0; j < dst_width_b; j++) {                        \
        pdst[(dst_height - 1 - i) * dst_stride + l + j * chan] = \
          color_i;                                               \
      }                                                          \
    }                                                            \
  }                                                              \
  return;                                                        \
}

/***************************************************************/
mlib_status mlib_ImageConvClearEdge(mlib_image     *dst,
                                    mlib_s32       dx_l,
                                    mlib_s32       dx_r,
                                    mlib_s32       dy_t,
                                    mlib_s32       dy_b,
                                    const mlib_s32 *color,
                                    mlib_s32       cmask)
{
  mlib_s32 dst_width = mlib_ImageGetWidth(dst);
  mlib_s32 dst_height = mlib_ImageGetHeight(dst);

  if (dx_l + dx_r > dst_width) {
    dx_l = dst_width;
    dx_r = 0;
  }

  if (dy_t + dy_b > dst_height) {
    dy_t = dst_height;
    dy_b = 0;
  }

  switch (mlib_ImageGetType(dst)) {
    case MLIB_BIT:
      return mlib_ImageConvClearEdge_Bit(dst, dx_l, dx_r, dy_t, dy_b, color, cmask);

    case MLIB_BYTE:
      switch (mlib_ImageGetChannels(dst)) {

        case 1:
          mlib_ImageConvClearEdge_U8_1(dst, dx_l, dx_r, dy_t, dy_b, color);
          break;

        case 2:
          mlib_ImageConvClearEdge_U8_2(dst, dx_l, dx_r, dy_t, dy_b, color, cmask);
          break;

        case 3:
          mlib_ImageConvClearEdge_U8_3(dst, dx_l, dx_r, dy_t, dy_b, color, cmask);
          break;

        case 4:
          mlib_ImageConvClearEdge_U8_4(dst, dx_l, dx_r, dy_t, dy_b, color, cmask);
          break;

        default:
          return MLIB_FAILURE;
      }

      break;

    case MLIB_SHORT:
    case MLIB_USHORT:
      switch (mlib_ImageGetChannels(dst)) {

        case 1:
          mlib_ImageConvClearEdge_S16_1(dst, dx_l, dx_r, dy_t, dy_b, color);
          break;

        case 2:
          mlib_ImageConvClearEdge_S16_2(dst, dx_l, dx_r, dy_t, dy_b, color, cmask);
          break;

        case 3:
          mlib_ImageConvClearEdge_S16_3(dst, dx_l, dx_r, dy_t, dy_b, color, cmask);
          break;

        case 4:
          mlib_ImageConvClearEdge_S16_4(dst, dx_l, dx_r, dy_t, dy_b, color, cmask);
          break;

        default:
          return MLIB_FAILURE;
      }

      break;

    case MLIB_INT:
      switch (mlib_ImageGetChannels(dst)) {

        case 1:
          mlib_ImageConvClearEdge_S32_1(dst, dx_l, dx_r, dy_t, dy_b, color);
          break;

        case 2:
          mlib_ImageConvClearEdge_S32_2(dst, dx_l, dx_r, dy_t, dy_b, color, cmask);
          break;

        case 3:
          mlib_ImageConvClearEdge_S32_3(dst, dx_l, dx_r, dy_t, dy_b, color, cmask);
          break;

        case 4:
          mlib_ImageConvClearEdge_S32_4(dst, dx_l, dx_r, dy_t, dy_b, color, cmask);
          break;

        default:
          return MLIB_FAILURE;
      }

      break;

    default:
      return MLIB_FAILURE;
  }

  return MLIB_SUCCESS;
}

/***************************************************************/
mlib_status mlib_ImageConvZeroEdge(mlib_image *dst,
                                   mlib_s32   dx_l,
                                   mlib_s32   dx_r,
                                   mlib_s32   dy_t,
                                   mlib_s32   dy_b,
                                   mlib_s32   cmask)
{
  mlib_d64 zero[4] = { 0, 0, 0, 0 };
  mlib_type type = mlib_ImageGetType(dst);

  if (type == MLIB_FLOAT || type == MLIB_DOUBLE) {
    return mlib_ImageConvClearEdge_Fp(dst, dx_l, dx_r, dy_t, dy_b, zero, cmask);
  }
  else {
    return mlib_ImageConvClearEdge(dst, dx_l, dx_r, dy_t, dy_b, (mlib_s32 *) zero, cmask);
  }
}

/***************************************************************/
void mlib_ImageConvClearEdge_U8_1(mlib_image     *dst,
                                  mlib_s32       dx_l,
                                  mlib_s32       dx_r,
                                  mlib_s32       dy_t,
                                  mlib_s32       dy_b,
                                  const mlib_s32 *color)
{
  mlib_u32 color0 = color[0] & 0xFF;
  mlib_d64 dcolor;

  VERT_EDGES(1, mlib_u8, 1);

  if (dst_width < 16)
    HORIZ_EDGES(1, mlib_u8, 1);

  color0 |= (color0 << 8);
  color0 |= (color0 << 16);
  dcolor = vis_to_double_dup(color0);
  for (i = 0; i < dy_t; i++) {
    pdst_row = pdst + i * dst_stride;
    pdst_row_end = pdst_row + dst_width_t - 1;
    dpdst = (mlib_d64 *) vis_alignaddr(pdst_row, 0);
    emask = vis_edge8(pdst_row, pdst_row_end);
    vis_pst_8(dcolor, dpdst++, emask);
    j = (mlib_s32) ((mlib_u8 *) dpdst - pdst_row);
    for (; j < (dst_width_t - 8); j += 8)
      *dpdst++ = dcolor;
    emask = vis_edge8(dpdst, pdst_row_end);
    vis_pst_8(dcolor, dpdst, emask);
  }

  for (i = 0; i < dy_b; i++) {
    pdst_row = pdst + (dst_height - 1 - i) * dst_stride;
    pdst_row_end = pdst_row + dst_width_b - 1;
    dpdst = (mlib_d64 *) vis_alignaddr(pdst_row, 0);
    emask = vis_edge8(pdst_row, pdst_row_end);
    vis_pst_8(dcolor, dpdst++, emask);
    j = (mlib_s32) ((mlib_u8 *) dpdst - pdst_row);
    for (; j < (dst_width_b - 8); j += 8)
      *dpdst++ = dcolor;
    emask = vis_edge8(dpdst, pdst_row_end);
    vis_pst_8(dcolor, dpdst, emask);
  }
}

/***************************************************************/
void mlib_ImageConvClearEdge_U8_2(mlib_image     *dst,
                                  mlib_s32       dx_l,
                                  mlib_s32       dx_r,
                                  mlib_s32       dy_t,
                                  mlib_s32       dy_b,
                                  const mlib_s32 *color,
                                  mlib_s32       cmask)
{
  mlib_u32 color0 = color[0] & 0xFF, color1 = color[1] & 0xFF;
  mlib_d64 dcolor0;
  mlib_s32 tmask = cmask & 3, mask1, offset;
  mlib_d64 dcolor;

  VERT_EDGES(2, mlib_u8, cmask);

  if (dst_width < 8)
    HORIZ_EDGES(2, mlib_u8, cmask);

  tmask |= (tmask << 2);
  tmask |= (tmask << 4);
  tmask |= (tmask << 8);
  color0 = (color0 << 8) | color1;
  color0 |= (color0 << 16);
  dcolor0 = vis_to_double_dup(color0);
  for (i = 0; i < dy_t; i++) {
    pdst_row = pdst + i * dst_stride;
    pdst_row_end = pdst_row + dst_width_t * 2 - 1;
    dpdst = (mlib_d64 *) vis_alignaddr(pdst_row, 0);
    offset = pdst_row - (mlib_u8 *) dpdst;
    mask1 = (tmask >> offset);
    emask = vis_edge8(pdst_row, pdst_row_end) & mask1;
    dcolor = vis_faligndata(dcolor0, dcolor0);
    vis_pst_8(dcolor, dpdst++, emask);
    j = (mlib_s32) ((mlib_u8 *) dpdst - pdst_row);
    for (; j < (dst_width_t * 2 - 8); j += 8)
      vis_pst_8(dcolor, dpdst++, mask1);
    emask = vis_edge8(dpdst, pdst_row_end) & mask1;
    vis_pst_8(dcolor, dpdst, emask);
  }

  for (i = 0; i < dy_b; i++) {
    pdst_row = pdst + (dst_height - 1 - i) * dst_stride;
    pdst_row_end = pdst_row + dst_width_b * 2 - 1;
    dpdst = (mlib_d64 *) vis_alignaddr(pdst_row, 0);
    offset = pdst_row - (mlib_u8 *) dpdst;
    mask1 = (tmask >> offset);
    emask = vis_edge8(pdst_row, pdst_row_end) & mask1;
    dcolor = vis_faligndata(dcolor0, dcolor0);
    vis_pst_8(dcolor, dpdst++, emask);
    j = (mlib_s32) ((mlib_u8 *) dpdst - pdst_row);
    for (; j < (dst_width_b * 2 - 8); j += 8)
      vis_pst_8(dcolor, dpdst++, mask1);
    emask = vis_edge8(dpdst, pdst_row_end) & mask1;
    vis_pst_8(dcolor, dpdst, emask);
  }
}

/***************************************************************/
void mlib_ImageConvClearEdge_U8_3(mlib_image     *dst,
                                  mlib_s32       dx_l,
                                  mlib_s32       dx_r,
                                  mlib_s32       dy_t,
                                  mlib_s32       dy_b,
                                  const mlib_s32 *color,
                                  mlib_s32       cmask)
{
  mlib_u32 color0 = color[0] & 0xFF,
    color1 = color[1] & 0xFF, color2 = color[2] & 0xFF, col;
  mlib_d64 dcolor1, dcolor2, dcolor00, dcolor11, dcolor22;
  mlib_s32 tmask = cmask & 7, mask0, mask1, mask2, offset;
  mlib_d64 dcolor;

  VERT_EDGES(3, mlib_u8, cmask);

  if (dst_width < 16)
    HORIZ_EDGES(3, mlib_u8, cmask);

  tmask |= (tmask << 3);
  tmask |= (tmask << 6);
  tmask |= (tmask << 12);
  col = (color0 << 16) | (color1 << 8) | color2;
  color0 = (col << 8) | color0;
  color1 = (color0 << 8) | color1;
  color2 = (color1 << 8) | color2;
  dcolor = vis_to_double(color0, color1);
  dcolor1 = vis_to_double(color2, color0);
  dcolor2 = vis_to_double(color1, color2);
  for (i = 0; i < dy_t; i++) {
    pdst_row = pdst + i * dst_stride;
    pdst_row_end = pdst_row + dst_width_t * 3 - 1;
    dpdst = (mlib_d64 *) ((mlib_addr) pdst_row & ~7);
    offset = pdst_row - (mlib_u8 *) dpdst;
    mask2 = (tmask >> (9 - ((8 - offset) & 7)));
    mask0 = mask2 >> 1;
    mask1 = mask0 >> 1;
    vis_alignaddr((void *)(-(mlib_addr) pdst_row), 8);
    dcolor22 = vis_faligndata(dcolor2, dcolor);
    dcolor00 = vis_faligndata(dcolor, dcolor1);
    dcolor11 = vis_faligndata(dcolor1, dcolor2);
    emask = vis_edge8(pdst_row, pdst_row_end) & mask2;

    if ((mlib_addr) pdst_row & 7)
      vis_pst_8(dcolor22, dpdst++, emask);
    j = (mlib_s32) ((mlib_u8 *) dpdst - pdst_row);
    for (; j < (dst_width_t * 3 - 24); j += 24) {
      vis_pst_8(dcolor00, dpdst, mask0);
      vis_pst_8(dcolor11, dpdst + 1, mask1);
      vis_pst_8(dcolor22, dpdst + 2, mask2);
      dpdst += 3;
    }

    if (j < (dst_width_t * 3 - 8)) {
      vis_pst_8(dcolor00, dpdst++, mask0);

      if (j < (dst_width_t * 3 - 16)) {
        vis_pst_8(dcolor11, dpdst++, mask1);
        dcolor00 = dcolor22;
        mask0 = mask2;
      }
      else {
        dcolor00 = dcolor11;
        mask0 = mask1;
      }
    }

    emask = vis_edge8(dpdst, pdst_row_end) & mask0;
    vis_pst_8(dcolor00, dpdst, emask);
  }

  for (i = 0; i < dy_b; i++) {
    pdst_row = pdst + (dst_height - 1 - i) * dst_stride;
    pdst_row_end = pdst_row + dst_width_b * 3 - 1;
    dpdst = (mlib_d64 *) ((mlib_addr) pdst_row & ~7);
    offset = pdst_row - (mlib_u8 *) dpdst;
    mask2 = (tmask >> (9 - ((8 - offset) & 7)));
    mask0 = mask2 >> 1;
    mask1 = mask0 >> 1;
    vis_alignaddr((void *)(-(mlib_addr) pdst_row), 8);
    dcolor22 = vis_faligndata(dcolor2, dcolor);
    dcolor00 = vis_faligndata(dcolor, dcolor1);
    dcolor11 = vis_faligndata(dcolor1, dcolor2);
    emask = vis_edge8(pdst_row, pdst_row_end) & mask2;

    if ((mlib_addr) pdst_row & 7)
      vis_pst_8(dcolor22, dpdst++, emask);
    j = (mlib_s32) ((mlib_u8 *) dpdst - pdst_row);
    for (; j < (dst_width_b * 3 - 24); j += 24) {
      vis_pst_8(dcolor00, dpdst, mask0);
      vis_pst_8(dcolor11, dpdst + 1, mask1);
      vis_pst_8(dcolor22, dpdst + 2, mask2);
      dpdst += 3;
    }

    if (j < (dst_width_b * 3 - 8)) {
      vis_pst_8(dcolor00, dpdst++, mask0);

      if (j < (dst_width_b * 3 - 16)) {
        vis_pst_8(dcolor11, dpdst++, mask1);
        dcolor00 = dcolor22;
        mask0 = mask2;
      }
      else {
        dcolor00 = dcolor11;
        mask0 = mask1;
      }
    }

    emask = vis_edge8(dpdst, pdst_row_end) & mask0;
    vis_pst_8(dcolor00, dpdst, emask);
  }
}

/***************************************************************/
void mlib_ImageConvClearEdge_U8_4(mlib_image     *dst,
                                  mlib_s32       dx_l,
                                  mlib_s32       dx_r,
                                  mlib_s32       dy_t,
                                  mlib_s32       dy_b,
                                  const mlib_s32 *color,
                                  mlib_s32       cmask)
{
  mlib_u32 color0 = color[0] & 0xFF,
    color1 = color[1] & 0xFF, color2 = color[2] & 0xFF, color3 = color[3] & 0xFF;
  mlib_d64 dcolor0;
  mlib_s32 tmask = cmask & 0xF, mask1, offset;
  mlib_d64 dcolor;

  VERT_EDGES(4, mlib_u8, cmask);

  if (dst_width < 4)
    HORIZ_EDGES(4, mlib_u8, cmask);

  tmask |= (tmask << 4);
  tmask |= (tmask << 8);
  color0 = (color0 << 24) | (color1 << 16) | (color2 << 8) | color3;
  dcolor0 = vis_to_double_dup(color0);
  for (i = 0; i < dy_t; i++) {
    pdst_row = pdst + i * dst_stride;
    pdst_row_end = pdst_row + dst_width_t * 4 - 1;
    dpdst = (mlib_d64 *) ((mlib_addr) pdst_row & ~7);
    offset = pdst_row - (mlib_u8 *) dpdst;
    mask1 = (tmask >> offset);
    vis_alignaddr((void *)(-(mlib_addr) pdst_row), 8);
    emask = vis_edge8(pdst_row, pdst_row_end) & mask1;
    dcolor = vis_faligndata(dcolor0, dcolor0);
    vis_pst_8(dcolor, dpdst++, emask);
    j = (mlib_s32) ((mlib_u8 *) dpdst - pdst_row);
    for (; j < (dst_width_t * 4 - 8); j += 8)
      vis_pst_8(dcolor, dpdst++, mask1);
    emask = vis_edge8(dpdst, pdst_row_end) & mask1;
    vis_pst_8(dcolor, dpdst, emask);
  }

  for (i = 0; i < dy_b; i++) {
    pdst_row = pdst + (dst_height - 1 - i) * dst_stride;
    pdst_row_end = pdst_row + dst_width_b * 4 - 1;
    dpdst = (mlib_d64 *) ((mlib_addr) pdst_row & ~7);
    offset = pdst_row - (mlib_u8 *) dpdst;
    mask1 = (tmask >> offset);
    vis_alignaddr((void *)(-(mlib_addr) pdst_row), 8);
    emask = vis_edge8(pdst_row, pdst_row_end) & mask1;
    dcolor = vis_faligndata(dcolor0, dcolor0);
    vis_pst_8(dcolor, dpdst++, emask);
    j = (mlib_s32) ((mlib_u8 *) dpdst - pdst_row);
    for (; j < (dst_width_b * 4 - 8); j += 8)
      vis_pst_8(dcolor, dpdst++, mask1);
    emask = vis_edge8(dpdst, pdst_row_end) & mask1;
    vis_pst_8(dcolor, dpdst, emask);
  }
}

/***************************************************************/
void mlib_ImageConvClearEdge_S16_1(mlib_image     *dst,
                                   mlib_s32       dx_l,
                                   mlib_s32       dx_r,
                                   mlib_s32       dy_t,
                                   mlib_s32       dy_b,
                                   const mlib_s32 *color)
{
  mlib_u32 color0 = color[0] & 0xFFFF;
  mlib_d64 dcolor;

  VERT_EDGES(1, mlib_s16, 1);

  if (dst_width < 8)
    HORIZ_EDGES(1, mlib_s16, 1);

  color0 |= (color0 << 16);
  dcolor = vis_to_double_dup(color0);
  for (i = 0; i < dy_t; i++) {
    pdst_row = pdst + i * dst_stride;
    pdst_row_end = pdst_row + dst_width_t - 1;
    dpdst = (mlib_d64 *) vis_alignaddr(pdst_row, 0);
    emask = vis_edge16(pdst_row, pdst_row_end);
    vis_pst_16(dcolor, dpdst++, emask);
    j = (mlib_s32) ((mlib_s16 *) dpdst - pdst_row);
    for (; j < (dst_width_t - 4); j += 4)
      *dpdst++ = dcolor;
    emask = vis_edge16(dpdst, pdst_row_end);
    vis_pst_16(dcolor, dpdst, emask);
  }

  for (i = 0; i < dy_b; i++) {
    pdst_row = pdst + (dst_height - 1 - i) * dst_stride;
    pdst_row_end = pdst_row + dst_width_b - 1;
    dpdst = (mlib_d64 *) vis_alignaddr(pdst_row, 0);
    emask = vis_edge16(pdst_row, pdst_row_end);
    vis_pst_16(dcolor, dpdst++, emask);
    j = (mlib_s32) ((mlib_s16 *) dpdst - pdst_row);
    for (; j < (dst_width_b - 4); j += 4)
      *dpdst++ = dcolor;
    emask = vis_edge16(dpdst, pdst_row_end);
    vis_pst_16(dcolor, dpdst, emask);
  }
}

/***************************************************************/
void mlib_ImageConvClearEdge_S16_2(mlib_image     *dst,
                                   mlib_s32       dx_l,
                                   mlib_s32       dx_r,
                                   mlib_s32       dy_t,
                                   mlib_s32       dy_b,
                                   const mlib_s32 *color,
                                   mlib_s32       cmask)
{
  mlib_u32 color0 = color[0] & 0xFFFF, color1 = color[1] & 0xFFFF;
  mlib_d64 dcolor0;
  mlib_s32 tmask = cmask & 3, mask1, offset;
  mlib_d64 dcolor;

  VERT_EDGES(2, mlib_s16, cmask);

  if (dst_width < 4)
    HORIZ_EDGES(2, mlib_s16, cmask);

  tmask |= (tmask << 2);
  tmask |= (tmask << 4);
  color0 = (color0 << 16) | color1;
  dcolor0 = vis_to_double_dup(color0);
  for (i = 0; i < dy_t; i++) {
    pdst_row = pdst + i * dst_stride;
    pdst_row_end = pdst_row + dst_width_t * 2 - 1;
    dpdst = (mlib_d64 *) vis_alignaddr(pdst_row, 0);
    offset = pdst_row - (mlib_s16 *) dpdst;
    mask1 = (tmask >> offset);
    emask = vis_edge16(pdst_row, pdst_row_end) & mask1;
    dcolor = vis_faligndata(dcolor0, dcolor0);
    vis_pst_16(dcolor, dpdst++, emask);
    j = (mlib_s32) ((mlib_s16 *) dpdst - pdst_row);
    for (; j < (dst_width_t * 2 - 4); j += 4)
      vis_pst_16(dcolor, dpdst++, mask1);
    emask = vis_edge16(dpdst, pdst_row_end) & mask1;
    vis_pst_16(dcolor, dpdst, emask);
  }

  for (i = 0; i < dy_b; i++) {
    pdst_row = pdst + (dst_height - 1 - i) * dst_stride;
    pdst_row_end = pdst_row + dst_width_b * 2 - 1;
    dpdst = (mlib_d64 *) vis_alignaddr(pdst_row, 0);
    offset = pdst_row - (mlib_s16 *) dpdst;
    mask1 = (tmask >> offset);
    emask = vis_edge16(pdst_row, pdst_row_end) & mask1;
    dcolor = vis_faligndata(dcolor0, dcolor0);
    vis_pst_16(dcolor, dpdst++, emask);
    j = (mlib_s32) ((mlib_s16 *) dpdst - pdst_row);
    for (; j < (dst_width_b * 2 - 4); j += 4)
      vis_pst_16(dcolor, dpdst++, mask1);
    emask = vis_edge16(dpdst, pdst_row_end) & mask1;
    vis_pst_16(dcolor, dpdst, emask);
  }
}

/***************************************************************/
void mlib_ImageConvClearEdge_S16_3(mlib_image     *dst,
                                   mlib_s32       dx_l,
                                   mlib_s32       dx_r,
                                   mlib_s32       dy_t,
                                   mlib_s32       dy_b,
                                   const mlib_s32 *color,
                                   mlib_s32       cmask)
{
  mlib_u32 color0 = color[0] & 0xFFFF,
    color1 = color[1] & 0xFFFF, color2 = color[2] & 0xFFFF, col0, col1, col2;
  mlib_d64 dcolor1, dcolor2, dcolor00, dcolor11, dcolor22;
  mlib_s32 tmask = cmask & 7, mask0, mask1, mask2, offset;
  mlib_d64 dcolor;

  VERT_EDGES(3, mlib_s16, cmask);

  if (dst_width < 8)
    HORIZ_EDGES(3, mlib_s16, cmask);

  tmask |= (tmask << 3);
  tmask |= (tmask << 6);
  tmask |= (tmask << 12);
  col0 = (color0 << 16) | color1;
  col1 = (color2 << 16) | color0;
  col2 = (color1 << 16) | color2;
  dcolor = vis_to_double(col0, col1);
  dcolor1 = vis_to_double(col2, col0);
  dcolor2 = vis_to_double(col1, col2);
  for (i = 0; i < dy_t; i++) {
    pdst_row = pdst + i * dst_stride;
    pdst_row_end = pdst_row + dst_width_t * 3 - 1;
    dpdst = (mlib_d64 *) ((mlib_addr) pdst_row & ~7);
    offset = pdst_row - (mlib_s16 *) dpdst;
    mask2 = (tmask >> (6 - ((4 - offset) & 3)));
    mask0 = mask2 >> 2;
    mask1 = mask0 >> 2;
    vis_alignaddr((void *)(-(mlib_addr) pdst_row), 8);
    dcolor22 = vis_faligndata(dcolor2, dcolor);
    dcolor00 = vis_faligndata(dcolor, dcolor1);
    dcolor11 = vis_faligndata(dcolor1, dcolor2);
    emask = vis_edge16(pdst_row, pdst_row_end) & mask2;

    if ((mlib_addr) pdst_row & 7)
      vis_pst_16(dcolor22, dpdst++, emask);
    j = (mlib_s32) ((mlib_s16 *) dpdst - pdst_row);
    for (; j < (dst_width_t * 3 - 12); j += 12) {
      vis_pst_16(dcolor00, dpdst, mask0);
      vis_pst_16(dcolor11, dpdst + 1, mask1);
      vis_pst_16(dcolor22, dpdst + 2, mask2);
      dpdst += 3;
    }

    if (j < (dst_width_t * 3 - 4)) {
      vis_pst_16(dcolor00, dpdst++, mask0);

      if (j < (dst_width_t * 3 - 8)) {
        vis_pst_16(dcolor11, dpdst++, mask1);
        dcolor00 = dcolor22;
        mask0 = mask2;
      }
      else {
        dcolor00 = dcolor11;
        mask0 = mask1;
      }
    }

    emask = vis_edge16(dpdst, pdst_row_end) & mask0;
    vis_pst_16(dcolor00, dpdst, emask);
  }

  for (i = 0; i < dy_b; i++) {
    pdst_row = pdst + (dst_height - 1 - i) * dst_stride;
    pdst_row_end = pdst_row + dst_width_b * 3 - 1;
    dpdst = (mlib_d64 *) ((mlib_addr) pdst_row & ~7);
    offset = pdst_row - (mlib_s16 *) dpdst;
    mask2 = (tmask >> (6 - ((4 - offset) & 3)));
    mask0 = mask2 >> 2;
    mask1 = mask0 >> 2;
    vis_alignaddr((void *)(-(mlib_addr) pdst_row), 8);
    dcolor22 = vis_faligndata(dcolor2, dcolor);
    dcolor00 = vis_faligndata(dcolor, dcolor1);
    dcolor11 = vis_faligndata(dcolor1, dcolor2);
    emask = vis_edge16(pdst_row, pdst_row_end) & mask2;

    if ((mlib_addr) pdst_row & 7)
      vis_pst_16(dcolor22, dpdst++, emask);
    j = (mlib_s32) ((mlib_s16 *) dpdst - pdst_row);
    for (; j < (dst_width_b * 3 - 12); j += 12) {
      vis_pst_16(dcolor00, dpdst, mask0);
      vis_pst_16(dcolor11, dpdst + 1, mask1);
      vis_pst_16(dcolor22, dpdst + 2, mask2);
      dpdst += 3;
    }

    if (j < (dst_width_b * 3 - 4)) {
      vis_pst_16(dcolor00, dpdst++, mask0);

      if (j < (dst_width_b * 3 - 8)) {
        vis_pst_16(dcolor11, dpdst++, mask1);
        dcolor00 = dcolor22;
        mask0 = mask2;
      }
      else {
        dcolor00 = dcolor11;
        mask0 = mask1;
      }
    }

    emask = vis_edge16(dpdst, pdst_row_end) & mask0;
    vis_pst_16(dcolor00, dpdst, emask);
  }
}

/***************************************************************/
void mlib_ImageConvClearEdge_S16_4(mlib_image     *dst,
                                   mlib_s32       dx_l,
                                   mlib_s32       dx_r,
                                   mlib_s32       dy_t,
                                   mlib_s32       dy_b,
                                   const mlib_s32 *color,
                                   mlib_s32       cmask)
{
  mlib_u32 color0 = color[0] & 0xFFFF,
    color1 = color[1] & 0xFFFF, color2 = color[2] & 0xFFFF, color3 = color[3] & 0xFFFF;
  mlib_d64 dcolor0;
  mlib_s32 tmask = cmask & 0xF, mask1, offset;
  mlib_d64 dcolor;

  VERT_EDGES(4, mlib_s16, cmask);

  if (dst_width < 4)
    HORIZ_EDGES(4, mlib_s16, cmask);

  tmask |= (tmask << 4);
  color0 = (color0 << 16) | color1;
  color1 = (color2 << 16) | color3;
  dcolor0 = vis_to_double(color0, color1);
  for (i = 0; i < dy_t; i++) {
    pdst_row = pdst + i * dst_stride;
    pdst_row_end = pdst_row + dst_width_t * 4 - 1;
    dpdst = (mlib_d64 *) ((mlib_addr) pdst_row & ~7);
    offset = pdst_row - (mlib_s16 *) dpdst;
    mask1 = (tmask >> offset);
    vis_alignaddr((void *)(-(mlib_addr) pdst_row), 8);
    emask = vis_edge16(pdst_row, pdst_row_end) & mask1;
    dcolor = vis_faligndata(dcolor0, dcolor0);
    vis_pst_16(dcolor, dpdst++, emask);
    j = (mlib_s32) ((mlib_s16 *) dpdst - pdst_row);
    for (; j < (dst_width_t * 4 - 4); j += 4)
      vis_pst_16(dcolor, dpdst++, mask1);
    emask = vis_edge16(dpdst, pdst_row_end) & mask1;
    vis_pst_16(dcolor, dpdst, emask);
  }

  for (i = 0; i < dy_b; i++) {
    pdst_row = pdst + (dst_height - 1 - i) * dst_stride;
    pdst_row_end = pdst_row + dst_width_b * 4 - 1;
    dpdst = (mlib_d64 *) ((mlib_addr) pdst_row & ~7);
    offset = pdst_row - (mlib_s16 *) dpdst;
    mask1 = (tmask >> offset);
    vis_alignaddr((void *)(-(mlib_addr) pdst_row), 8);
    emask = vis_edge16(pdst_row, pdst_row_end) & mask1;
    dcolor = vis_faligndata(dcolor0, dcolor0);
    vis_pst_16(dcolor, dpdst++, emask);
    j = (mlib_s32) ((mlib_s16 *) dpdst - pdst_row);
    for (; j < (dst_width_b * 4 - 4); j += 4)
      vis_pst_16(dcolor, dpdst++, mask1);
    emask = vis_edge16(dpdst, pdst_row_end) & mask1;
    vis_pst_16(dcolor, dpdst, emask);
  }
}

/***************************************************************/
void mlib_ImageConvClearEdge_S32_1(mlib_image     *dst,
                                   mlib_s32       dx_l,
                                   mlib_s32       dx_r,
                                   mlib_s32       dy_t,
                                   mlib_s32       dy_b,
                                   const mlib_s32 *color)
{
  mlib_s32 color0 = color[0];
  mlib_d64 dcolor;

  VERT_EDGES(1, mlib_s32, 1);

  if (dst_width < 8)
    HORIZ_EDGES(1, mlib_s32, 1);

  dcolor = vis_to_double_dup(color0);
  for (i = 0; i < dy_t; i++) {
    pdst_row = pdst + i * dst_stride;
    pdst_row_end = pdst_row + dst_width_t - 1;
    dpdst = (mlib_d64 *) vis_alignaddr(pdst_row, 0);
    emask = vis_edge32(pdst_row, pdst_row_end);
    vis_pst_32(dcolor, dpdst++, emask);
    j = (mlib_s32) ((mlib_s32 *) dpdst - pdst_row);
    for (; j < (dst_width_t - 2); j += 2)
      *dpdst++ = dcolor;
    emask = vis_edge32(dpdst, pdst_row_end);
    vis_pst_32(dcolor, dpdst, emask);
  }

  for (i = 0; i < dy_b; i++) {
    pdst_row = pdst + (dst_height - 1 - i) * dst_stride;
    pdst_row_end = pdst_row + dst_width_b - 1;
    dpdst = (mlib_d64 *) vis_alignaddr(pdst_row, 0);
    emask = vis_edge32(pdst_row, pdst_row_end);
    vis_pst_32(dcolor, dpdst++, emask);
    j = (mlib_s32) ((mlib_s32 *) dpdst - pdst_row);
    for (; j < (dst_width_b - 2); j += 2)
      *dpdst++ = dcolor;
    emask = vis_edge32(dpdst, pdst_row_end);
    vis_pst_32(dcolor, dpdst, emask);
  }
}

/***************************************************************/
void mlib_ImageConvClearEdge_S32_2(mlib_image     *dst,
                                   mlib_s32       dx_l,
                                   mlib_s32       dx_r,
                                   mlib_s32       dy_t,
                                   mlib_s32       dy_b,
                                   const mlib_s32 *color,
                                   mlib_s32       cmask)
{
  mlib_s32 color0 = color[0], color1 = color[1];
  mlib_d64 dcolor0;
  mlib_s32 tmask = cmask & 3, mask1, offset;
  mlib_d64 dcolor;

  VERT_EDGES(2, mlib_s32, cmask);

  if (dst_width < 4)
    HORIZ_EDGES(2, mlib_s32, cmask);

  tmask |= (tmask << 2);
  dcolor0 = vis_to_double(color0, color1);
  for (i = 0; i < dy_t; i++) {
    pdst_row = pdst + i * dst_stride;
    pdst_row_end = pdst_row + dst_width_t * 2 - 1;
    dpdst = (mlib_d64 *) vis_alignaddr(pdst_row, 0);
    offset = pdst_row - (mlib_s32 *) dpdst;
    mask1 = (tmask >> offset);
    emask = vis_edge32(pdst_row, pdst_row_end) & mask1;
    dcolor = vis_faligndata(dcolor0, dcolor0);
    vis_pst_32(dcolor, dpdst++, emask);
    j = (mlib_s32) ((mlib_s32 *) dpdst - pdst_row);
    for (; j < (dst_width_t * 2 - 2); j += 2)
      vis_pst_32(dcolor, dpdst++, mask1);
    emask = vis_edge32(dpdst, pdst_row_end) & mask1;
    vis_pst_32(dcolor, dpdst, emask);
  }

  for (i = 0; i < dy_b; i++) {
    pdst_row = pdst + (dst_height - 1 - i) * dst_stride;
    pdst_row_end = pdst_row + dst_width_b * 2 - 1;
    dpdst = (mlib_d64 *) vis_alignaddr(pdst_row, 0);
    offset = pdst_row - (mlib_s32 *) dpdst;
    mask1 = (tmask >> offset);
    emask = vis_edge32(pdst_row, pdst_row_end) & mask1;
    dcolor = vis_faligndata(dcolor0, dcolor0);
    vis_pst_32(dcolor, dpdst++, emask);
    j = (mlib_s32) ((mlib_s32 *) dpdst - pdst_row);
    for (; j < (dst_width_b * 2 - 2); j += 2)
      vis_pst_32(dcolor, dpdst++, mask1);
    emask = vis_edge32(dpdst, pdst_row_end) & mask1;
    vis_pst_32(dcolor, dpdst, emask);
  }
}

/***************************************************************/
void mlib_ImageConvClearEdge_S32_3(mlib_image     *dst,
                                   mlib_s32       dx_l,
                                   mlib_s32       dx_r,
                                   mlib_s32       dy_t,
                                   mlib_s32       dy_b,
                                   const mlib_s32 *color,
                                   mlib_s32       cmask)
{
  mlib_s32 color0 = color[0], color1 = color[1], color2 = color[2];
  mlib_d64 dcolor1, dcolor2, dcolor00, dcolor11, dcolor22;
  mlib_s32 tmask = cmask & 7, mask0, mask1, mask2, offset;
  mlib_d64 dcolor;

  VERT_EDGES(3, mlib_s32, cmask);

  if (dst_width < 8)
    HORIZ_EDGES(3, mlib_s32, cmask);

  tmask |= (tmask << 3);
  tmask |= (tmask << 6);
  dcolor = vis_to_double(color0, color1);
  dcolor1 = vis_to_double(color2, color0);
  dcolor2 = vis_to_double(color1, color2);
  for (i = 0; i < dy_t; i++) {
    pdst_row = pdst + i * dst_stride;
    pdst_row_end = pdst_row + dst_width_t * 3 - 1;
    dpdst = (mlib_d64 *) ((mlib_addr) pdst_row & ~7);
    offset = pdst_row - (mlib_s32 *) dpdst;
    mask2 = (tmask >> (3 - ((2 - offset) & 1)));
    mask0 = mask2 >> 1;
    mask1 = mask0 >> 1;
    vis_alignaddr((void *)(-(mlib_addr) pdst_row), 8);
    dcolor22 = vis_faligndata(dcolor2, dcolor);
    dcolor00 = vis_faligndata(dcolor, dcolor1);
    dcolor11 = vis_faligndata(dcolor1, dcolor2);
    emask = vis_edge32(pdst_row, pdst_row_end) & mask2;

    if ((mlib_addr) pdst_row & 7)
      vis_pst_32(dcolor22, dpdst++, emask);
    j = (mlib_s32) ((mlib_s32 *) dpdst - pdst_row);
    for (; j < (dst_width_t * 3 - 6); j += 6) {
      vis_pst_32(dcolor00, dpdst, mask0);
      vis_pst_32(dcolor11, dpdst + 1, mask1);
      vis_pst_32(dcolor22, dpdst + 2, mask2);
      dpdst += 3;
    }

    if (j < (dst_width_t * 3 - 2)) {
      vis_pst_32(dcolor00, dpdst++, mask0);

      if (j < (dst_width_t * 3 - 4)) {
        vis_pst_32(dcolor11, dpdst++, mask1);
        dcolor00 = dcolor22;
        mask0 = mask2;
      }
      else {
        dcolor00 = dcolor11;
        mask0 = mask1;
      }
    }

    emask = vis_edge32(dpdst, pdst_row_end) & mask0;
    vis_pst_32(dcolor00, dpdst, emask);
  }

  for (i = 0; i < dy_b; i++) {
    pdst_row = pdst + (dst_height - 1 - i) * dst_stride;
    pdst_row_end = pdst_row + dst_width_b * 3 - 1;
    dpdst = (mlib_d64 *) ((mlib_addr) pdst_row & ~7);
    offset = pdst_row - (mlib_s32 *) dpdst;
    mask2 = (tmask >> (3 - ((2 - offset) & 1)));
    mask0 = mask2 >> 1;
    mask1 = mask0 >> 1;
    vis_alignaddr((void *)(-(mlib_addr) pdst_row), 8);
    dcolor22 = vis_faligndata(dcolor2, dcolor);
    dcolor00 = vis_faligndata(dcolor, dcolor1);
    dcolor11 = vis_faligndata(dcolor1, dcolor2);
    emask = vis_edge32(pdst_row, pdst_row_end) & mask2;

    if ((mlib_addr) pdst_row & 7)
      vis_pst_32(dcolor22, dpdst++, emask);
    j = (mlib_s32) ((mlib_s32 *) dpdst - pdst_row);
    for (; j < (dst_width_b * 3 - 6); j += 6) {
      vis_pst_32(dcolor00, dpdst, mask0);
      vis_pst_32(dcolor11, dpdst + 1, mask1);
      vis_pst_32(dcolor22, dpdst + 2, mask2);
      dpdst += 3;
    }

    if (j < (dst_width_b * 3 - 2)) {
      vis_pst_32(dcolor00, dpdst++, mask0);

      if (j < (dst_width_b * 3 - 4)) {
        vis_pst_32(dcolor11, dpdst++, mask1);
        dcolor00 = dcolor22;
        mask0 = mask2;
      }
      else {
        dcolor00 = dcolor11;
        mask0 = mask1;
      }
    }

    emask = vis_edge32(dpdst, pdst_row_end) & mask0;
    vis_pst_32(dcolor00, dpdst, emask);
  }
}

/***************************************************************/
void mlib_ImageConvClearEdge_S32_4(mlib_image     *dst,
                                   mlib_s32       dx_l,
                                   mlib_s32       dx_r,
                                   mlib_s32       dy_t,
                                   mlib_s32       dy_b,
                                   const mlib_s32 *color,
                                   mlib_s32       cmask)
{
  mlib_u32 color0 = color[0], color1 = color[1], color2 = color[2], color3 = color[3];
  mlib_d64 dcolor0, dcolor1, dcolor00, dcolor11;
  mlib_s32 tmask = cmask & 0xF, mask0, mask1, offset;

  VERT_EDGES(4, mlib_s32, cmask);

  if (dst_width < 4)
    HORIZ_EDGES(4, mlib_s32, cmask);

  tmask |= (tmask << 4);
  dcolor0 = vis_to_double(color0, color1);
  dcolor1 = vis_to_double(color2, color3);
  for (i = 0; i < dy_t; i++) {
    pdst_row = pdst + i * dst_stride;
    pdst_row_end = pdst_row + dst_width_t * 4 - 1;
    dpdst = (mlib_d64 *) ((mlib_addr) pdst_row & ~7);
    offset = pdst_row - (mlib_s32 *) dpdst;
    mask1 = (tmask >> (4 - ((2 - offset) & 1)));
    mask0 = mask1 >> 2;
    vis_alignaddr((void *)(-(mlib_addr) pdst_row), 8);
    emask = vis_edge32(pdst_row, pdst_row_end) & mask1;
    dcolor00 = vis_faligndata(dcolor0, dcolor1);
    dcolor11 = vis_faligndata(dcolor1, dcolor0);

    if ((mlib_addr) pdst_row & 7)
      vis_pst_32(dcolor11, dpdst++, emask);
    j = (mlib_s32) ((mlib_s32 *) dpdst - pdst_row);
    for (; j < (dst_width_t * 4 - 4); j += 4) {
      vis_pst_32(dcolor00, dpdst, mask0);
      vis_pst_32(dcolor11, dpdst + 1, mask1);
      dpdst += 2;
    }

    if (j < (dst_width_t * 4 - 2)) {
      vis_pst_32(dcolor00, dpdst++, mask0);
      dcolor00 = dcolor11;
      mask0 = mask1;
    }

    emask = vis_edge32(dpdst, pdst_row_end) & mask0;
    vis_pst_32(dcolor00, dpdst, emask);
  }

  for (i = 0; i < dy_b; i++) {
    pdst_row = pdst + (dst_height - 1 - i) * dst_stride;
    pdst_row_end = pdst_row + dst_width_b * 4 - 1;
    dpdst = (mlib_d64 *) ((mlib_addr) pdst_row & ~7);
    offset = pdst_row - (mlib_s32 *) dpdst;
    mask1 = (tmask >> (4 - ((2 - offset) & 1)));
    mask0 = mask1 >> 2;
    vis_alignaddr((void *)(-(mlib_addr) pdst_row), 8);
    emask = vis_edge32(pdst_row, pdst_row_end) & mask1;
    dcolor00 = vis_faligndata(dcolor0, dcolor1);
    dcolor11 = vis_faligndata(dcolor1, dcolor0);

    if ((mlib_addr) pdst_row & 7)
      vis_pst_32(dcolor11, dpdst++, emask);
    j = (mlib_s32) ((mlib_s32 *) dpdst - pdst_row);
    for (; j < (dst_width_b * 4 - 4); j += 4) {
      vis_pst_32(dcolor00, dpdst, mask0);
      vis_pst_32(dcolor11, dpdst + 1, mask1);
      dpdst += 2;
    }

    if (j < (dst_width_b * 4 - 2)) {
      vis_pst_32(dcolor00, dpdst++, mask0);
      dcolor00 = dcolor11;
      mask0 = mask1;
    }

    emask = vis_edge32(dpdst, pdst_row_end) & mask0;
    vis_pst_32(dcolor00, dpdst, emask);
  }
}

/***************************************************************/
