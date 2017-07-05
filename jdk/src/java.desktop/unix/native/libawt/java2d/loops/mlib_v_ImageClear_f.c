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
 *
 * DESCRIPTION
 *      Clear of an image to a specific color.
 *      -- VIS version low level functions.
 *
 * NOTE
 *      These functions are separated from mlib_v_ImageClear.c
 *      for structure clarity.
 */

#include <vis_proto.h>
#include <mlib_image.h>
#include <mlib_v_ImageClear_f.h>

/***************************************************************/

#define PREPAREVARS(type, chan)                                  \
  type *pimg = (type *) mlib_ImageGetData(img);                  \
  mlib_s32 img_height = mlib_ImageGetHeight(img);                \
  mlib_s32 img_width  = mlib_ImageGetWidth(img);                 \
  mlib_s32 img_stride = mlib_ImageGetStride(img) / sizeof(type); \
  mlib_s32       i, l, j;                                        \
  mlib_s32 emask;                                                \
  mlib_d64 dcolor, *dpimg;                                       \
                                                                 \
  if ((img_width * chan) == img_stride) {                        \
    img_width *= img_height;                                     \
    img_height = 1;                                              \
  }

/***************************************************************/

#define STRIP(pd, color, w, h, chan, data_type)                    \
  for (l = 0; l < chan; l++) {                                     \
    data_type color_i = color[l];                                  \
    for (i = 0; i < h; i++) {                                      \
      for (j = 0; j < w; j++) pd[i*img_stride+l+j*chan] = color_i; \
    }                                                              \
  }

/***************************************************************/

void mlib_v_ImageClear_BIT_1(mlib_image     *img,
                             const mlib_s32 *color)
{
  mlib_u8 *pimg = (mlib_u8 *) mlib_ImageGetData(img);
  mlib_s32 img_height = mlib_ImageGetHeight(img);
  mlib_s32 img_width = mlib_ImageGetWidth(img);
  mlib_s32 img_stride = mlib_ImageGetStride(img);
  mlib_s32 img_bitoff = mlib_ImageGetBitOffset(img);
  mlib_s32 i, j, b_j, k;
  mlib_u8 bcolor0, bmask, emask, src;
  mlib_d64 dcolor, *dpimg;
  mlib_u32 color0;

  if (img_width == img_stride * 8) {
    img_width *= img_height;
    img_height = 1;
  }

  color0 = ((color[0] & 1) << 31) >> 31;
  bcolor0 = color0 & 0xFF;

  dcolor = vis_to_double_dup(color0);
  for (i = 0, j = 0; i < img_height; i++) {
    mlib_u8 *pimg_row = pimg + i * img_stride, *pimg_row_end;

    if (img_bitoff + img_width <= 8) {
      bmask = (0xFF >> (8 - img_width)) << (8 - img_bitoff - img_width);
      src = pimg_row[0];
      pimg_row[0] = (src & ~bmask) | (color0 & bmask);
      continue;
    }
    else {
      bmask = 0xFF >> img_bitoff;
      src = pimg_row[0];
      pimg_row[0] = (src & ~bmask) | (color0 & bmask);
      pimg_row++;
      j = 8 - img_bitoff;
      b_j = (img_width - j) / 8;
    }

    if (b_j < 16) {
      mlib_s32 ii;

      for (ii = 0; ii < b_j; ii++)
        pimg_row[ii] = bcolor0;

      pimg_row += ii;
      j += ii << 3;

      if (j < img_width) {
        bmask = (0xFF << (8 - (img_width - j))) & 0xFF;
        src = pimg_row[0];
        pimg_row[0] = (src & ~bmask) | (color0 & bmask);
      }

      continue;
    }

    pimg_row_end = pimg_row + b_j - 1;
    dpimg = (mlib_d64 *) vis_alignaddr(pimg_row, 0);

    emask = vis_edge8(pimg_row, pimg_row_end);
    vis_pst_8(dcolor, dpimg++, emask);
    k = (mlib_addr) dpimg - (mlib_addr) pimg_row;
    for (; k < (b_j - 8); k += 8)
      *dpimg++ = dcolor;
    emask = vis_edge8(dpimg, pimg_row_end);
    vis_pst_8(dcolor, dpimg, emask);
    j += b_j << 3;

    if (j < img_width) {
      pimg_row = (mlib_u8 *) (pimg_row_end + 1);
      bmask = (0xFF << (8 - (img_width - j))) & 0xFF;
      src = pimg_row[0];
      pimg_row[0] = (src & ~bmask) | (color0 & bmask);
    }
  }
}

/***************************************************************/

void mlib_v_ImageClear_BIT_2(mlib_image     *img,
                             const mlib_s32 *color)
{
  mlib_u8 *pimg = (mlib_u8 *) mlib_ImageGetData(img); /* pointer to the data of img-image */
  mlib_s32 img_height = mlib_ImageGetHeight(img);     /* height of source image */
  mlib_s32 img_width = mlib_ImageGetWidth(img) << 1;  /* width of source image */
  mlib_s32 img_stride = mlib_ImageGetStride(img);     /* elements to next row */
  mlib_s32 img_bitoff = mlib_ImageGetBitOffset(img);  /* bits to first byte */
  mlib_s32 i, j, b_j, k;                              /* indicies */
  mlib_u8 bcolor0, bmask, emask, src;
  mlib_d64 dcolor, *dpimg;
  mlib_u32 color0 = color[0] & 1, color1 = color[1] & 1;

  if (img_width == img_stride * 8) {
    img_width *= img_height;
    img_height = 1;
  }

  color1 = (color0 << 1) | color1;
  color1 = (color1 << 2) | color1;
  color1 = (color1 << 4) | color1;
  color0 = ((color1 << 1) & 0xFE) | color0;
  bcolor0 = ((img_bitoff & 1) == 0) ? color1 : color0;
  color0 = (bcolor0 << 8) | bcolor0;
  color0 = (color0 << 16) | color0;

  dcolor = vis_to_double_dup(color0);
  for (i = 0, j = 0; i < img_height; i++) {
    mlib_u8 *pimg_row = pimg + i * img_stride, *pimg_row_end;

    if (img_bitoff + img_width <= 8) {
      bmask = (0xFF >> (8 - img_width)) << (8 - img_bitoff - img_width);
      src = pimg_row[0];
      pimg_row[0] = (src & ~bmask) | (color0 & bmask);
      continue;
    }
    else {
      bmask = 0xFF >> img_bitoff;
      src = pimg_row[0];
      pimg_row[0] = (src & ~bmask) | (color0 & bmask);
      pimg_row++;
      j = 8 - img_bitoff;
      b_j = (img_width - j) / 8;
    }

    if (b_j < 16) {
      mlib_s32 ii;

      for (ii = 0; ii < b_j; ii++)
        pimg_row[ii] = bcolor0;

      pimg_row += ii;
      j += ii << 3;

      if (j < img_width) {
        bmask = (0xFF << (8 - (img_width - j))) & 0xFF;
        src = pimg_row[0];
        pimg_row[0] = (src & ~bmask) | (color0 & bmask);
      }

      continue;
    }

    pimg_row_end = pimg_row + b_j - 1;
    dpimg = (mlib_d64 *) vis_alignaddr(pimg_row, 0);

    emask = vis_edge8(pimg_row, pimg_row_end);
    vis_pst_8(dcolor, dpimg++, emask);
    k = (mlib_addr) dpimg - (mlib_addr) pimg_row;
    for (; k < (b_j - 8); k += 8)
      *dpimg++ = dcolor;
    emask = vis_edge8(dpimg, pimg_row_end);
    vis_pst_8(dcolor, dpimg, emask);
    j += b_j << 3;

    if (j < img_width) {
      pimg_row = (mlib_u8 *) (pimg_row_end + 1);
      bmask = (0xFF << (8 - (img_width - j))) & 0xFF;
      src = pimg_row[0];
      pimg_row[0] = (src & ~bmask) | (color0 & bmask);
    }
  }
}

/***************************************************************/

void mlib_v_ImageClear_BIT_3(mlib_image     *img,
                             const mlib_s32 *color)
{
  mlib_u8 *pimg = (mlib_u8 *) mlib_ImageGetData(img); /* pointer to the data of img-image */
  mlib_s32 img_height = mlib_ImageGetHeight(img);     /* height of source image */
  mlib_s32 img_width = mlib_ImageGetWidth(img) * 3;   /* width of source image */
  mlib_s32 img_stride = mlib_ImageGetStride(img);     /* elements to next row */
  mlib_s32 img_bitoff = mlib_ImageGetBitOffset(img);  /* bits to first byte */
  mlib_s32 i, j, b_j, k, bit_shift;                   /* indicies */
  mlib_u8 bcolor, bmask, emask, src;
  mlib_d64 dcolor0, dcolor1, dcolor2, *dpimg;
  mlib_d64 dcolor00, dcolor11, dcolor22;
  mlib_u32 color0 = color[0] & 1, color1 = color[1] & 1, color2 = color[2] & 1;
  mlib_u32 col0, col1, col2;

  if (img_width == img_stride * 8) {
    img_width *= img_height;
    img_height = 1;
  }

  col0 = (color0 << 3) | (color1 << 2) | (color2 << 1) | color0;
  col1 = (col0 >> 1) | (color2 << 3);
  col2 = (col1 >> 1) | (color1 << 3);
  color0 = (col0 << 4) | col2;
  color1 = (col1 << 4) | col0;
  color2 = (col2 << 4) | col1;

  color0 = (color0 << 24) | (color1 << 16) | (color2 << 8) | color0;
  color1 = (color0 << 8) | color1;
  color2 = (color1 << 8) | color2;

  dcolor0 = vis_to_double(color0, color1);
  dcolor1 = vis_to_double(color2, color0);
  dcolor2 = vis_to_double(color1, color2);

  for (i = 0; i < img_height; i++) {
    mlib_u8 *pimg_row = pimg + i * img_stride, *pimg_row_end;

    if (img_bitoff + img_width <= 8) {
      bmask = (0xFF >> (8 - img_width)) << (8 - img_bitoff - img_width);
      src = pimg_row[0];
      bcolor = (color0 >> img_bitoff) & 0xFF;
      pimg_row[0] = (src & ~bmask) | (bcolor & bmask);
      continue;
    }
    else {
      bmask = 0xFF >> img_bitoff;
      src = pimg_row[0];
      bcolor = (color0 >> img_bitoff) & 0xFF;
      bit_shift = (((mlib_addr) pimg_row & 7) << 3) + img_bitoff;
      pimg_row[0] = (src & ~bmask) | (bcolor & bmask);
      pimg_row++;
      j = 8 - img_bitoff;
      b_j = (img_width - j) / 8;
    }

    pimg_row_end = pimg_row + b_j - 1;

    dpimg = (mlib_d64 *) ((mlib_addr) pimg_row & ~7);
    vis_alignaddr((void *)(bit_shift % 3), 0);
    dcolor22 = vis_faligndata(dcolor0, dcolor1);
    dcolor00 = vis_faligndata(dcolor1, dcolor2);
    dcolor11 = vis_faligndata(dcolor2, dcolor0);
    emask = vis_edge8(pimg_row, pimg_row_end);

    if ((mlib_addr) pimg_row & 7)
      vis_pst_8(dcolor22, dpimg++, emask);
    k = (mlib_addr) dpimg - (mlib_addr) pimg_row;
    for (; k <= (b_j - 24); k += 24) {
      dpimg[0] = dcolor00;
      dpimg[1] = dcolor11;
      dpimg[2] = dcolor22;
      dpimg += 3;
    }

    if (k < b_j) {
      if (k < (b_j - 8)) {
        *dpimg++ = dcolor00;

        if (k < (b_j - 16)) {
          *dpimg++ = dcolor11;
          dcolor00 = dcolor22;
        }
        else
          dcolor00 = dcolor11;
      }

      emask = vis_edge8(dpimg, pimg_row_end);
      vis_pst_8(dcolor00, dpimg, emask);
    }

    j = img_width - j - (b_j << 3);

    if (j > 0) {
      pimg_row = (mlib_u8 *) (pimg_row_end + 1);
      bmask = (0xFF << (8 - j)) & 0xFF;
      bcolor = (color0 >> j) & 0xFF;
      src = pimg_row[0];
      pimg_row[0] = (src & ~bmask) | (bcolor & bmask);
    }
  }
}

/***************************************************************/

void mlib_v_ImageClear_BIT_4(mlib_image     *img,
                             const mlib_s32 *color)
{
  mlib_u8 *pimg = (mlib_u8 *) mlib_ImageGetData(img); /* pointer to the data of img-image */
  mlib_s32 img_height = mlib_ImageGetHeight(img);     /* height of source image */
  mlib_s32 img_width = mlib_ImageGetWidth(img) << 2;  /* width of source image */
  mlib_s32 img_stride = mlib_ImageGetStride(img);     /* elements to next row */
  mlib_s32 img_bitoff = mlib_ImageGetBitOffset(img);  /* bits to first byte */
  mlib_s32 i, j, b_j, k;                              /* indicies */
  mlib_u8 bcolor0, bmask, emask, src;
  mlib_d64 dcolor, *dpimg;
  mlib_u32 color0 = color[0] & 1, color1 = color[1] & 1, color2 = color[2] & 1, color3 = color[3] & 1;

  if (img_width == img_stride * 8) {
    img_width *= img_height;
    img_height = 1;
  }

  color0 = (color0 << 3) | (color1 << 2) | (color2 << 1) | color3;
  color0 = (color0 << 4) | color0;
  color3 = (color0 << 1) | (color0 >> 7);
  color2 = (color0 << 2) | (color0 >> 6);
  color1 = (color0 << 3) | (color0 >> 5);

  bcolor0 = (img_bitoff & 2) ? ((img_bitoff & 1) ? color3 : color2) : ((img_bitoff & 1) ? color1 : color0);
  color0 = (bcolor0 << 24) | (bcolor0 << 16) | (bcolor0 << 8) | bcolor0;

  dcolor = vis_to_double_dup(color0);
  for (i = 0, j = 0; i < img_height; i++) {
    mlib_u8 *pimg_row = pimg + i * img_stride, *pimg_row_end;

    if (img_bitoff + img_width <= 8) {
      bmask = (0xFF >> (8 - img_width)) << (8 - img_bitoff - img_width);
      src = pimg_row[0];
      pimg_row[0] = (src & ~bmask) | (color0 & bmask);
      continue;
    }
    else {
      bmask = 0xFF >> img_bitoff;
      src = pimg_row[0];
      pimg_row[0] = (src & ~bmask) | (color0 & bmask);
      pimg_row++;
      j = 8 - img_bitoff;
      b_j = (img_width - j) / 8;
    }

    if (b_j < 16) {
      mlib_s32 ii;

      for (ii = 0; ii < b_j; ii++)
        pimg_row[ii] = bcolor0;

      pimg_row += ii;
      j += ii << 3;

      if (j < img_width) {
        bmask = (0xFF << (8 - (img_width - j))) & 0xFF;
        src = pimg_row[0];
        pimg_row[0] = (src & ~bmask) | (color0 & bmask);
      }

      continue;
    }

    pimg_row_end = pimg_row + b_j - 1;
    dpimg = (mlib_d64 *) vis_alignaddr(pimg_row, 0);

    emask = vis_edge8(pimg_row, pimg_row_end);
    vis_pst_8(dcolor, dpimg++, emask);
    k = (mlib_addr) dpimg - (mlib_addr) pimg_row;
    for (; k < (b_j - 8); k += 8)
      *dpimg++ = dcolor;
    emask = vis_edge8(dpimg, pimg_row_end);
    vis_pst_8(dcolor, dpimg, emask);
    j += b_j << 3;

    if (j < img_width) {
      pimg_row = (mlib_u8 *) (pimg_row_end + 1);
      bmask = (0xFF << (8 - (img_width - j))) & 0xFF;
      src = pimg_row[0];
      pimg_row[0] = (src & ~bmask) | (color0 & bmask);
    }
  }
}

/***************************************************************/

void mlib_v_ImageClear_U8_1(mlib_image     *img,
                            const mlib_s32 *color)
{
  mlib_u32 color0 = color[0] & 0xFF;

  PREPAREVARS(mlib_u8, 1);

  if (img_width < 16) {
    STRIP(pimg, color, img_width, img_height, 1, mlib_u8);
    return;
  }

  color0 |= (color0 << 8);
  color0 |= (color0 << 16);
  dcolor = vis_to_double_dup(color0);
  for (i = 0; i < img_height; i++) {
    mlib_u8 *pimg_row = pimg + i * img_stride, *pimg_row_end = pimg_row + img_width - 1;

    dpimg = (mlib_d64 *) vis_alignaddr(pimg_row, 0);
    emask = vis_edge8(pimg_row, pimg_row_end);
    vis_pst_8(dcolor, dpimg++, emask);
    j = (mlib_addr) dpimg - (mlib_addr) pimg_row;
    for (; j < (img_width - 8); j += 8)
      *dpimg++ = dcolor;
    emask = vis_edge8(dpimg, pimg_row_end);
    vis_pst_8(dcolor, dpimg, emask);
  }
}

/***************************************************************/

void mlib_v_ImageClear_U8_2(mlib_image     *img,
                            const mlib_s32 *color)
{
  mlib_u32 color0 = color[0] & 0xFF, color1 = color[1] & 0xFF;
  mlib_d64 dcolor0;

  PREPAREVARS(mlib_u8, 2);

  if (img_width < 8) {
    STRIP(pimg, color, img_width, img_height, 2, mlib_u8);
    return;
  }

  color0 = (color0 << 8) | color1;
  color0 |= (color0 << 16);
  dcolor0 = vis_to_double_dup(color0);
  for (i = 0; i < img_height; i++) {
    mlib_u8 *pimg_row = pimg + i * img_stride, *pimg_row_end = pimg_row + img_width * 2 - 1;

    dpimg = (mlib_d64 *) vis_alignaddr(pimg_row, 0);
    emask = vis_edge8(pimg_row, pimg_row_end);
    dcolor = vis_faligndata(dcolor0, dcolor0);
    vis_pst_8(dcolor, dpimg++, emask);
    j = (mlib_addr) dpimg - (mlib_addr) pimg_row;
    for (; j < (img_width * 2 - 8); j += 8)
      *dpimg++ = dcolor;
    emask = vis_edge8(dpimg, pimg_row_end);
    vis_pst_8(dcolor, dpimg, emask);
  }
}

/***************************************************************/

void mlib_v_ImageClear_U8_3(mlib_image     *img,
                            const mlib_s32 *color)
{
  mlib_u32 color0 = color[0] & 0xFF, color1 = color[1] & 0xFF, color2 = color[2] & 0xFF, col;
  mlib_d64 dcolor1, dcolor2, dcolor00, dcolor11, dcolor22;

  PREPAREVARS(mlib_u8, 3);

  if (img_width < 16) {
    STRIP(pimg, color, img_width, img_height, 3, mlib_u8);
    return;
  }

  col = (color0 << 16) | (color1 << 8) | color2;
  color0 = (col << 8) | color0;
  color1 = (color0 << 8) | color1;
  color2 = (color1 << 8) | color2;
  dcolor = vis_to_double(color0, color1);
  dcolor1 = vis_to_double(color2, color0);
  dcolor2 = vis_to_double(color1, color2);
  for (i = 0; i < img_height; i++) {
    mlib_u8 *pimg_row = pimg + i * img_stride, *pimg_row_end = pimg_row + img_width * 3 - 1;

    dpimg = (mlib_d64 *) ((mlib_addr) pimg_row & ~7);
    vis_alignaddr((void *)(-(mlib_addr) pimg_row), 8);
    dcolor22 = vis_faligndata(dcolor2, dcolor);
    dcolor00 = vis_faligndata(dcolor, dcolor1);
    dcolor11 = vis_faligndata(dcolor1, dcolor2);
    emask = vis_edge8(pimg_row, pimg_row_end);

    if ((mlib_addr) pimg_row & 7)
      vis_pst_8(dcolor22, dpimg++, emask);
    j = (mlib_addr) dpimg - (mlib_addr) pimg_row;
    for (; j < (img_width * 3 - 24); j += 24) {
      dpimg[0] = dcolor00;
      dpimg[1] = dcolor11;
      dpimg[2] = dcolor22;
      dpimg += 3;
    }

    if (j < (img_width * 3 - 8)) {
      *dpimg++ = dcolor00;

      if (j < (img_width * 3 - 16)) {
        *dpimg++ = dcolor11;
        dcolor00 = dcolor22;
      }
      else
        dcolor00 = dcolor11;
    }

    emask = vis_edge8(dpimg, pimg_row_end);
    vis_pst_8(dcolor00, dpimg, emask);
  }
}

/***************************************************************/

void mlib_v_ImageClear_U8_4(mlib_image     *img,
                            const mlib_s32 *color)
{
  mlib_u32 color0 = color[0] & 0xFF, color1 = color[1] & 0xFF, color2 = color[2] & 0xFF, color3 = color[3] & 0xFF;
  mlib_d64 dcolor0;

  PREPAREVARS(mlib_u8, 4);

  if (img_width < 4) {
    STRIP(pimg, color, img_width, img_height, 4, mlib_u8);
    return;
  }

  color0 = (color0 << 24) | (color1 << 16) | (color2 << 8) | color3;
  dcolor0 = vis_to_double_dup(color0);
  for (i = 0; i < img_height; i++) {
    mlib_u8 *pimg_row = pimg + i * img_stride, *pimg_row_end = pimg_row + img_width * 4 - 1;

    dpimg = (mlib_d64 *) ((mlib_addr) pimg_row & ~7);
    vis_alignaddr((void *)(-(mlib_addr) pimg_row), 8);
    emask = vis_edge8(pimg_row, pimg_row_end);
    dcolor = vis_faligndata(dcolor0, dcolor0);
    vis_pst_8(dcolor, dpimg++, emask);
    j = (mlib_addr) dpimg - (mlib_addr) pimg_row;
    for (; j < (img_width * 4 - 8); j += 8)
      *dpimg++ = dcolor;
    emask = vis_edge8(dpimg, pimg_row_end);
    vis_pst_8(dcolor, dpimg, emask);
  }
}

/***************************************************************/

void mlib_v_ImageClear_S16_1(mlib_image     *img,
                             const mlib_s32 *color)
{
  mlib_u32 color0 = color[0] & 0xFFFF;

  PREPAREVARS(mlib_s16, 1);

  if (img_width < 8) {
    STRIP(pimg, color, img_width, img_height, 1, mlib_s16);
    return;
  }

  color0 |= (color0 << 16);
  dcolor = vis_to_double_dup(color0);
  for (i = 0; i < img_height; i++) {
    mlib_s16 *pimg_row = pimg + i * img_stride, *pimg_row_end = pimg_row + img_width - 1;

    dpimg = (mlib_d64 *) vis_alignaddr(pimg_row, 0);
    emask = vis_edge16(pimg_row, pimg_row_end);
    vis_pst_16(dcolor, dpimg++, emask);
    j = (mlib_s16 *) dpimg - pimg_row;
    for (; j < (img_width - 4); j += 4)
      *dpimg++ = dcolor;
    emask = vis_edge16(dpimg, pimg_row_end);
    vis_pst_16(dcolor, dpimg, emask);
  }
}

/***************************************************************/

void mlib_v_ImageClear_S16_2(mlib_image     *img,
                             const mlib_s32 *color)
{
  mlib_u32 color0 = color[0] & 0xFFFF, color1 = color[1] & 0xFFFF;
  mlib_d64 dcolor0;

  PREPAREVARS(mlib_s16, 2);

  if (img_width < 4) {
    STRIP(pimg, color, img_width, img_height, 2, mlib_s16);
    return;
  }

  color0 = (color0 << 16) | color1;
  dcolor0 = vis_to_double_dup(color0);
  for (i = 0; i < img_height; i++) {
    mlib_s16 *pimg_row = pimg + i * img_stride, *pimg_row_end = pimg_row + img_width * 2 - 1;

    dpimg = (mlib_d64 *) vis_alignaddr(pimg_row, 0);
    emask = vis_edge16(pimg_row, pimg_row_end);
    dcolor = vis_faligndata(dcolor0, dcolor0);
    vis_pst_16(dcolor, dpimg++, emask);
    j = (mlib_s16 *) dpimg - pimg_row;
    for (; j < (img_width * 2 - 4); j += 4)
      *dpimg++ = dcolor;
    emask = vis_edge16(dpimg, pimg_row_end);
    vis_pst_16(dcolor, dpimg, emask);
  }
}

/***************************************************************/

void mlib_v_ImageClear_S16_3(mlib_image     *img,
                             const mlib_s32 *color)
{
  mlib_u32 color0 = color[0] & 0xFFFF, color1 = color[1] & 0xFFFF, color2 = color[2] & 0xFFFF, col0, col1, col2;
  mlib_d64 dcolor1, dcolor2, dcolor00, dcolor11, dcolor22;

  PREPAREVARS(mlib_s16, 3);

  if (img_width < 8) {
    STRIP(pimg, color, img_width, img_height, 3, mlib_s16);
    return;
  }

  col0 = (color0 << 16) | color1;
  col1 = (color2 << 16) | color0;
  col2 = (color1 << 16) | color2;
  dcolor = vis_to_double(col0, col1);
  dcolor1 = vis_to_double(col2, col0);
  dcolor2 = vis_to_double(col1, col2);
  for (i = 0; i < img_height; i++) {
    mlib_s16 *pimg_row = pimg + i * img_stride, *pimg_row_end = pimg_row + img_width * 3 - 1;

    dpimg = (mlib_d64 *) ((mlib_addr) pimg_row & ~7);
    vis_alignaddr((void *)(-(mlib_addr) pimg_row), 8);
    dcolor22 = vis_faligndata(dcolor2, dcolor);
    dcolor00 = vis_faligndata(dcolor, dcolor1);
    dcolor11 = vis_faligndata(dcolor1, dcolor2);
    emask = vis_edge16(pimg_row, pimg_row_end);

    if ((mlib_addr) pimg_row & 7)
      vis_pst_16(dcolor22, dpimg++, emask);
    j = (mlib_s16 *) dpimg - pimg_row;
    for (; j < (img_width * 3 - 12); j += 12) {
      dpimg[0] = dcolor00;
      dpimg[1] = dcolor11;
      dpimg[2] = dcolor22;
      dpimg += 3;
    }

    if (j < (img_width * 3 - 4)) {
      *dpimg++ = dcolor00;

      if (j < (img_width * 3 - 8)) {
        *dpimg++ = dcolor11;
        dcolor00 = dcolor22;
      }
      else
        dcolor00 = dcolor11;
    }

    emask = vis_edge16(dpimg, pimg_row_end);
    vis_pst_16(dcolor00, dpimg, emask);
  }
}

/***************************************************************/

void mlib_v_ImageClear_S16_4(mlib_image     *img,
                             const mlib_s32 *color)
{
  mlib_u32 color0 = color[0] & 0xFFFF, color1 = color[1] & 0xFFFF, color2 = color[2] & 0xFFFF, color3 = color[3] & 0xFFFF;
  mlib_d64 dcolor0;

  PREPAREVARS(mlib_s16, 4);

  if (img_width < 2) {
    STRIP(pimg, color, img_width, img_height, 4, mlib_s16);
    return;
  }

  color0 = (color0 << 16) | color1;
  color1 = (color2 << 16) | color3;
  dcolor0 = vis_to_double(color0, color1);
  for (i = 0; i < img_height; i++) {
    mlib_s16 *pimg_row = pimg + i * img_stride, *pimg_row_end = pimg_row + img_width * 4 - 1;

    dpimg = (mlib_d64 *) ((mlib_addr) pimg_row & ~7);
    vis_alignaddr((void *)(-(mlib_addr) pimg_row), 8);
    emask = vis_edge16(pimg_row, pimg_row_end);
    dcolor = vis_faligndata(dcolor0, dcolor0);
    vis_pst_16(dcolor, dpimg++, emask);
    j = (mlib_s16 *) dpimg - pimg_row;
    for (; j < (img_width * 4 - 4); j += 4)
      *dpimg++ = dcolor;
    emask = vis_edge16(dpimg, pimg_row_end);
    vis_pst_16(dcolor, dpimg, emask);
  }
}

/***************************************************************/

void mlib_v_ImageClear_S32_1(mlib_image     *img,
                             const mlib_s32 *color)
{
  mlib_u32 color0 = color[0];

  PREPAREVARS(mlib_s32, 1);

  if (img_width < 4) {
    STRIP(pimg, color, img_width, img_height, 1, mlib_s32);
    return;
  }

  dcolor = vis_to_double_dup(color0);
  for (i = 0; i < img_height; i++) {
    mlib_s32 *pimg_row = pimg + i * img_stride, *pimg_row_end = pimg_row + img_width - 1;

    dpimg = (mlib_d64 *) vis_alignaddr(pimg_row, 0);
    emask = vis_edge32(pimg_row, pimg_row_end);
    vis_pst_32(dcolor, dpimg++, emask);
    j = (mlib_s32 *) dpimg - pimg_row;
    for (; j <= (img_width - 2); j += 2)
      *dpimg++ = dcolor;

    if (j < img_width) {
      emask = vis_edge32(dpimg, pimg_row_end);
      vis_pst_32(dcolor, dpimg, emask);
    }
  }
}

/***************************************************************/

void mlib_v_ImageClear_S32_2(mlib_image     *img,
                             const mlib_s32 *color)
{
  mlib_u32 color0 = color[0], color1 = color[1];
  mlib_d64 dcolor0;

  PREPAREVARS(mlib_s32, 2);

  if (img_width < 2) {
    STRIP(pimg, color, img_width, img_height, 2, mlib_s32);
    return;
  }

  dcolor0 = vis_to_double(color0, color1);
  for (i = 0; i < img_height; i++) {
    mlib_s32 *pimg_row = pimg + i * img_stride, *pimg_row_end = pimg_row + img_width * 2 - 1;

    dpimg = (mlib_d64 *) vis_alignaddr(pimg_row, 0);
    emask = vis_edge32(pimg_row, pimg_row_end);
    dcolor = vis_faligndata(dcolor0, dcolor0);
    vis_pst_32(dcolor, dpimg++, emask);
    j = (mlib_s32 *) dpimg - pimg_row;
    for (; j < (img_width * 2 - 2); j += 2)
      *dpimg++ = dcolor;
    emask = vis_edge32(dpimg, pimg_row_end);
    vis_pst_32(dcolor, dpimg, emask);
  }
}

/***************************************************************/

void mlib_v_ImageClear_S32_3(mlib_image     *img,
                             const mlib_s32 *color)
{
  mlib_u32 color0 = color[0], color1 = color[1], color2 = color[2];
  mlib_d64 dcolor1, dcolor2, dcolor00, dcolor11, dcolor22;

  PREPAREVARS(mlib_s32, 3);

  if (img_width < 2) {
    STRIP(pimg, color, img_width, img_height, 3, mlib_s32);
    return;
  }

  dcolor = vis_to_double(color0, color1);
  dcolor1 = vis_to_double(color2, color0);
  dcolor2 = vis_to_double(color1, color2);
  for (i = 0; i < img_height; i++) {
    mlib_s32 *pimg_row = pimg + i * img_stride, *pimg_row_end = pimg_row + img_width * 3 - 1;

    dpimg = (mlib_d64 *) ((mlib_addr) pimg_row & ~7);
    vis_alignaddr((void *)(-(mlib_addr) pimg_row), 8);
    dcolor22 = vis_faligndata(dcolor2, dcolor);
    dcolor00 = vis_faligndata(dcolor, dcolor1);
    dcolor11 = vis_faligndata(dcolor1, dcolor2);
    emask = vis_edge32(pimg_row, pimg_row_end);

    if ((mlib_addr) pimg_row & 7)
      vis_pst_32(dcolor22, dpimg++, emask);
    j = (mlib_s32 *) dpimg - pimg_row;
    for (; j < (img_width * 3 - 6); j += 6) {
      dpimg[0] = dcolor00;
      dpimg[1] = dcolor11;
      dpimg[2] = dcolor22;
      dpimg += 3;
    }

    if (j < (img_width * 3 - 2)) {
      *dpimg++ = dcolor00;

      if (j < (img_width * 3 - 4)) {
        *dpimg++ = dcolor11;
        dcolor00 = dcolor22;
      }
      else
        dcolor00 = dcolor11;
    }

    emask = vis_edge32(dpimg, pimg_row_end);
    vis_pst_32(dcolor00, dpimg, emask);
  }
}

/***************************************************************/

void mlib_v_ImageClear_S32_4(mlib_image     *img,
                             const mlib_s32 *color)
{
  mlib_u32 color0 = color[0], color1 = color[1], color2 = color[2], color3 = color[3];
  mlib_d64 dcolor0, dcolor00, dcolor0_, dcolor00_, dcolor1;

  PREPAREVARS(mlib_s32, 4);

  if (img_width < 2) {
    STRIP(pimg, color, img_width, img_height, 4, mlib_s32);
    return;
  }

  dcolor0 = vis_to_double(color2, color3);
  dcolor00 = vis_to_double(color0, color1);
  vis_alignaddr((void *)0, 4);
  dcolor0_ = vis_faligndata(dcolor0, dcolor00);
  dcolor00_ = vis_faligndata(dcolor00, dcolor0);
  for (i = 0; i < img_height; i++) {
    mlib_s32 *pimg_row = pimg + i * img_stride, *pimg_row_end = pimg_row + img_width * 4 - 1;

    dpimg = (mlib_d64 *) ((mlib_addr) pimg_row & ~7);
    vis_alignaddr((void *)(-(mlib_addr) pimg_row), 4);
    emask = vis_edge32(pimg_row, pimg_row_end);
    dcolor = vis_faligndata(dcolor0_, dcolor00_);
    dcolor1 = vis_faligndata(dcolor00_, dcolor0_);
    vis_pst_32(dcolor, dpimg++, emask);
    *dpimg++ = dcolor1;
    j = (mlib_s32 *) dpimg - pimg_row;
    for (; j <= (img_width * 4 - 4); j += 4) {
      dpimg[0] = dcolor;
      dpimg[1] = dcolor1;
      dpimg += 2;
    }

    if (j < (img_width * 4)) {
      emask = vis_edge32(dpimg, pimg_row_end);
      vis_pst_32(dcolor, dpimg, emask);
    }
  }
}

/***************************************************************/
