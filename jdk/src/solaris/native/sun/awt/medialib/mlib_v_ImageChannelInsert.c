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
 *      mlib_ImageChannelInsert   - Copy the source image into the selected
 *                                                        channels of the destination image
 *
 * SYNOPSIS
 *      mlib_status mlib_ImageChannelInsert(mlib_image *dst,
 *                                                                        mlib_image *src,
 *                                                                      mlib_s32   cmask);
 *
 * ARGUMENT
 *  dst     Pointer to destination image.
 *  src     Pointer to source image.
 *  cmask   Destination channel selection mask.
 *              The least significant bit (LSB) is corresponding to the
 *              last channel in the destination image data.
 *              The bits with value 1 stand for the channels selected.
 *              If more than N channels are selected, the leftmost N
 *              channels are inserted, where N is the number of channels
 *              in the source image.
 *
 * RESTRICTION
 *              The src and dst must have the same width, height and data type.
 *              The src and dst can have 1, 2, 3 or 4 channels.
 *              The src and dst can be either MLIB_BYTE, MLIB_SHORT, MLIB_INT,
 *          MLIB_FLOAT or MLIB_DOUBLE.
 *
 * DESCRIPTION
 *          Copy the source image into the selected channels of the destination
 *              image
 */

#include <stdlib.h>
#include "mlib_image.h"
#include "mlib_ImageCheck.h"

/***************************************************************/
/* functions defined in mlib_v_ImageChannelInsert_1.c */

void
mlib_v_ImageChannelInsert_U8(mlib_u8  *src,  mlib_s32 slb,
                             mlib_u8  *dst,  mlib_s32 dlb,
                             mlib_s32 channels,
                             mlib_s32 channeld,
                             mlib_s32 width,  mlib_s32 height,
                             mlib_s32 cmask);
void
mlib_v_ImageChannelInsert_D64(mlib_d64  *src,  mlib_s32 slb,
                              mlib_d64  *dst,  mlib_s32 dlb,
                              mlib_s32 channels,
                              mlib_s32 channeld,
                              mlib_s32 width,  mlib_s32 height,
                              mlib_s32 cmask);
void
mlib_v_ImageChannelInsert_S16(mlib_s16 *src,  mlib_s32 slb,
                              mlib_s16 *dst,  mlib_s32 dlb,
                              mlib_s32 channels,
                              mlib_s32 channeld,
                              mlib_s32 width,  mlib_s32 height,
                              mlib_s32 cmask);
void
mlib_v_ImageChannelInsert_S32(mlib_s32 *src,  mlib_s32 slb,
                              mlib_s32 *dst,  mlib_s32 dlb,
                              mlib_s32 channels,
                              mlib_s32 channeld,
                              mlib_s32 width,  mlib_s32 height,
                              mlib_s32 cmask);
void
mlib_v_ImageChannelInsert_U8_12_A8D1X8(mlib_u8  *src,
                                                               mlib_u8  *dst,
                                                         mlib_s32 dsize,
                                                         mlib_s32 cmask);
void
mlib_v_ImageChannelInsert_U8_12_A8D2X8(mlib_u8  *src,  mlib_s32 slb,
                                                               mlib_u8  *dst,  mlib_s32 dlb,
                                                       mlib_s32 xsize, mlib_s32 ysize,
                                                               mlib_s32 cmask);
void
mlib_v_ImageChannelInsert_U8_12_D1(mlib_u8  *src,
                                                           mlib_u8  *dst,
                                                   mlib_s32 dsize,
                                                           mlib_s32 cmask);
void
mlib_v_ImageChannelInsert_U8_12(mlib_u8  *src,  mlib_s32 slb,
                                                        mlib_u8  *dst,  mlib_s32 dlb,
                                                mlib_s32 xsize, mlib_s32 ysize,
                                                        mlib_s32 cmask);
void
mlib_v_ImageChannelInsert_U8_13_A8D1X8(mlib_u8  *src,
                                                               mlib_u8  *dst,
                                                       mlib_s32 dsize,
                                                               mlib_s32 cmask);
void
mlib_v_ImageChannelInsert_U8_13_A8D2X8(mlib_u8  *src,  mlib_s32 slb,
                                                               mlib_u8  *dst,  mlib_s32 dlb,
                                                         mlib_s32 xsize, mlib_s32 ysize,
                                                               mlib_s32 cmask);
void
mlib_v_ImageChannelInsert_U8_13_D1(mlib_u8  *src,
                                                           mlib_u8  *dst,
                                                     mlib_s32 dsize,
                                                           mlib_s32 cmask);
void
mlib_v_ImageChannelInsert_U8_13(mlib_u8  *src,  mlib_s32 slb,
                                                        mlib_u8  *dst,  mlib_s32 dlb,
                                                  mlib_s32 xsize, mlib_s32 ysize,
                                                        mlib_s32 cmask);
void
mlib_v_ImageChannelInsert_U8_14_A8D1X8(mlib_u8  *src,
                                                               mlib_u8  *dst,
                                                       mlib_s32 dsize,
                                                               mlib_s32 cmask);
void
mlib_v_ImageChannelInsert_U8_14_A8D2X8(mlib_u8  *src,  mlib_s32 slb,
                                                               mlib_u8  *dst,  mlib_s32 dlb,
                                                       mlib_s32 xsize, mlib_s32 ysize,
                                                               mlib_s32 cmask);
void
mlib_v_ImageChannelInsert_U8_14_D1(mlib_u8  *src,
                                                           mlib_u8  *dst,
                                                   mlib_s32 dsize,
                                                           mlib_s32 cmask);
void
mlib_v_ImageChannelInsert_U8_14(mlib_u8  *src,  mlib_s32 slb,
                                                        mlib_u8  *dst,  mlib_s32 dlb,
                                                mlib_s32 xsize, mlib_s32 ysize,
                                                        mlib_s32 cmask);
void
mlib_v_ImageChannelInsert_S16_12_A8D1X4(mlib_s16 *src,
                                                                      mlib_s16 *dst,
                                                        mlib_s32 dsize,
                                                                mlib_s32 cmask);
void
mlib_v_ImageChannelInsert_S16_12_A8D2X4(mlib_s16 *src,  mlib_s32 slb,
                                                                      mlib_s16 *dst,  mlib_s32 dlb,
                                                        mlib_s32 xsize, mlib_s32 ysize,
                                                                mlib_s32 cmask);
void
mlib_v_ImageChannelInsert_S16_12_D1(mlib_s16 *src,
                                                            mlib_s16 *dst,
                                                    mlib_s32 dsize,
                                                            mlib_s32 cmask);
void
mlib_v_ImageChannelInsert_S16_12(mlib_s16 *src,  mlib_s32 slb,
                                                        mlib_s16 *dst,  mlib_s32 dlb,
                                                  mlib_s32 xsize, mlib_s32 ysize,
                                                  mlib_s32 cmask);
void
mlib_v_ImageChannelInsert_S16_13_A8D1X4(mlib_s16 *src,
                                                                      mlib_s16 *dst,
                                                        mlib_s32 dsize,
                                                                mlib_s32 cmask);
void
mlib_v_ImageChannelInsert_S16_13_A8D2X4(mlib_s16 *src,  mlib_s32 slb,
                                                                      mlib_s16 *dst,  mlib_s32 dlb,
                                                        mlib_s32 xsize, mlib_s32 ysize,
                                                                mlib_s32 cmask);
void
mlib_v_ImageChannelInsert_S16_13_D1(mlib_s16 *src,
                                                            mlib_s16 *dst,
                                                    mlib_s32 dsize,
                                                            mlib_s32 cmask);
void
mlib_v_ImageChannelInsert_S16_13(mlib_s16 *src,  mlib_s32 slb,
                                                         mlib_s16 *dst,  mlib_s32 dlb,
                                                 mlib_s32 xsize, mlib_s32 ysize,
                                                         mlib_s32 cmask);
void
mlib_v_ImageChannelInsert_S16_14_A8D1X4(mlib_s16 *src,
                                                                      mlib_s16 *dst,
                                                          mlib_s32 dsize,
                                                                      mlib_s32 cmask);
void
mlib_v_ImageChannelInsert_S16_14_A8D2X4(mlib_s16 *src,  mlib_s32 slb,
                                                                      mlib_s16 *dst,  mlib_s32 dlb,
                                                          mlib_s32 xsize, mlib_s32 ysize,
                                                                      mlib_s32 cmask);
void
mlib_v_ImageChannelInsert_S16_14_D1(mlib_s16 *src,
                                                            mlib_s16 *dst,
                                                    mlib_s32 dsize,
                                                            mlib_s32 cmask);
void
mlib_v_ImageChannelInsert_S16_14(mlib_s16 *src,  mlib_s32 slb,
                                                         mlib_s16 *dst,  mlib_s32 dlb,
                                                 mlib_s32 xsize, mlib_s32 ysize,
                                                         mlib_s32 cmask);

/***************************************************************/
/* functions defined in mlib_v_ImageChannelInsert_34.c */

void
mlib_v_ImageChannelInsert_U8_34R_A8D1X8(mlib_u8  *src,
                                                                mlib_u8  *dst,
                                                                mlib_s32 dsize);
void
mlib_v_ImageChannelInsert_U8_34R_A8D2X8(mlib_u8  *src,  mlib_s32 slb,
                                                                mlib_u8  *dst,  mlib_s32 dlb,
                                                                mlib_s32 xsize, mlib_s32 ysize);
void
mlib_v_ImageChannelInsert_U8_34R_D1(mlib_u8  *src,
                                                            mlib_u8  *dst,
                                                            mlib_s32 dsize);
void
mlib_v_ImageChannelInsert_U8_34R(mlib_u8  *src,  mlib_s32 slb,
                                                 mlib_u8  *dst,  mlib_s32 dlb,
                                                         mlib_s32 xsize, mlib_s32 ysize);
void
mlib_v_ImageChannelInsert_S16_34R_A8D1X4(mlib_s16 *src,
                                                                 mlib_s16 *dst,
                                                                 mlib_s32 dsize);
void
mlib_v_ImageChannelInsert_S16_34R_A8D2X4(mlib_s16 *src,  mlib_s32 slb,
                                                                 mlib_s16 *dst,  mlib_s32 dlb,
                                                                 mlib_s32 xsize, mlib_s32 ysize);
void
mlib_v_ImageChannelInsert_S16_34R_D1(mlib_s16 *src,
                                                             mlib_s16 *dst,
                                                             mlib_s32 dsize);
void
mlib_v_ImageChannelInsert_S16_34R(mlib_s16 *src,  mlib_s32 slb,
                                                          mlib_s16 *dst,  mlib_s32 dlb,
                                                          mlib_s32 xsize, mlib_s32 ysize);
void
mlib_v_ImageChannelInsert_U8_34L_A8D1X8(mlib_u8  *src,
                                                                mlib_u8  *dst,
                                                                mlib_s32 dsize);
void
mlib_v_ImageChannelInsert_U8_34L_A8D2X8(mlib_u8  *src,  mlib_s32 slb,
                                                                mlib_u8  *dst,  mlib_s32 dlb,
                                                        mlib_s32 xsize, mlib_s32 ysize);
void
mlib_v_ImageChannelInsert_U8_34L_D1(mlib_u8  *src,
                                                            mlib_u8  *dst,
                                                            mlib_s32 dsize);
void
mlib_v_ImageChannelInsert_U8_34L(mlib_u8  *src,  mlib_s32 slb,
                                                         mlib_u8  *dst,  mlib_s32 dlb,
                                                         mlib_s32 xsize, mlib_s32 ysize);
void
mlib_v_ImageChannelInsert_S16_34L_A8D1X4(mlib_s16 *src,
                                                                 mlib_s16 *dst,
                                                                 mlib_s32 dsize);
void
mlib_v_ImageChannelInsert_S16_34L_A8D2X4(mlib_s16 *src,  mlib_s32 slb,
                                                                 mlib_s16 *dst,  mlib_s32 dlb,
                                                                 mlib_s32 xsize, mlib_s32 ysize);
void
mlib_v_ImageChannelInsert_S16_34L_D1(mlib_s16 *src,
                                                             mlib_s16 *dst,
                                                             mlib_s32 dsize);
void
mlib_v_ImageChannelInsert_S16_34L(mlib_s16 *src,  mlib_s32 slb,
                                                          mlib_s16 *dst,  mlib_s32 dlb,
                                                          mlib_s32 xsize, mlib_s32 ysize);


/***************************************************************/

#ifdef MLIB_TEST
mlib_status
mlib_v_ImageChannelInsert(mlib_image *dst,
                                            mlib_image *src,
                                          mlib_s32   cmask)
#else
mlib_status
mlib_ImageChannelInsert(mlib_image *dst,
                                        mlib_image *src,
                                        mlib_s32   cmask)
#endif
{
  const mlib_s32  X8 = 0x7;
  const mlib_s32  X4 = 0x3;
  const mlib_s32  X2 = 0x1;
  const mlib_s32  A8D1   = MLIB_IMAGE_ALIGNED8 | MLIB_IMAGE_ONEDVECTOR;
  const mlib_s32  A8D2X8 = MLIB_IMAGE_ALIGNED8 | MLIB_IMAGE_STRIDE8X | MLIB_IMAGE_WIDTH8X;
  const mlib_s32  A8D2X4 = MLIB_IMAGE_ALIGNED8 | MLIB_IMAGE_STRIDE8X | MLIB_IMAGE_WIDTH4X;
  const mlib_s32  A8D2X2 = MLIB_IMAGE_ALIGNED8 | MLIB_IMAGE_STRIDE8X | MLIB_IMAGE_WIDTH2X;

  void      *sp;                      /* pointer for pixel in src */
  void      *dp;                      /* pointer for pixel in dst */
  mlib_s32  ncmask = 0;         /* normalized channel mask */
  mlib_s32  channels;             /* number of channels for src */
  mlib_s32  channeld;             /* number of channels for dst */
  mlib_s32  width, height;/* for src and dst */
  mlib_s32  strides;              /* strides in bytes for src */
  mlib_s32  strided;            /* strides in bytes for dst */
  mlib_s32  flags;
  mlib_s32  flagd;
  mlib_s32  dsize;
  int         i, bit1count = 0;

  MLIB_IMAGE_CHECK(src);
  MLIB_IMAGE_CHECK(dst);
  MLIB_IMAGE_TYPE_EQUAL(src,dst);
  MLIB_IMAGE_SIZE_EQUAL(src,dst);

  channels = mlib_ImageGetChannels(src);
  channeld = mlib_ImageGetChannels(dst);
  width    = mlib_ImageGetWidth(src);
  height   = mlib_ImageGetHeight(src);
  strides  = mlib_ImageGetStride(src);
  strided  = mlib_ImageGetStride(dst);
  sp       = mlib_ImageGetData(src);
  dp       = mlib_ImageGetData(dst);
  flags    = mlib_ImageGetFlags(src);
  flagd    = mlib_ImageGetFlags(dst);
  dsize    = width * height;

  /* normalize the cmask, and count the number of bit with value 1 */
  for (i = (channeld - 1); i >= 0; i--) {
    if (((cmask & (1 << i)) != 0) && (bit1count < channels)) {
      ncmask += (1 << i);
      bit1count++;
    }
  }

  /* do not support the cases in which the number of selected channels is
   * less than the nubmber of channels in the source image */
  if (bit1count < channels) {
    return MLIB_FAILURE;
  }

  if (((channels == 1) && (channeld == 1)) ||
      ((channels == 2) && (channeld == 2)) ||
      ((channels == 3) && (channeld == 3)) ||
      ((channels == 4) && (channeld == 4))) {
      return mlib_ImageCopy(dst, src);
  }

  switch (mlib_ImageGetType(src)) {
    case MLIB_BYTE:
      if (channels == 1) {
        switch (channeld) {
          case 2:
            if (((flags & A8D1) == 0) &&
                ((flagd & A8D1) == 0) &&
                ((dsize & X8)   == 0)) {
                mlib_v_ImageChannelInsert_U8_12_A8D1X8((mlib_u8 *)sp,
                                                                             (mlib_u8 *)dp,
                                                                             dsize,
                                                                                     ncmask);
            }
            else if (((flags & A8D2X8) == 0) &&
              ((flagd & A8D2X8) == 0)) {
              mlib_v_ImageChannelInsert_U8_12_A8D2X8((mlib_u8 *)sp, strides,
                                                                             (mlib_u8 *)dp, strided,
                                                                             width, height,
                                                                                     ncmask);
            }
            else if (((flags & MLIB_IMAGE_ONEDVECTOR) == 0) &&
               ((flagd & MLIB_IMAGE_ONEDVECTOR) == 0)) {
                mlib_v_ImageChannelInsert_U8_12_D1((mlib_u8 *)sp,
                                                                                 (mlib_u8 *)dp,
                                                                                 dsize,
                                                                                 ncmask);
            }
            else {
                mlib_v_ImageChannelInsert_U8_12((mlib_u8 *)sp, strides,
                                                                      (mlib_u8 *)dp, strided,
                                                                      width, height,
                                                                              ncmask);
            }
            break;

          case 3:
            if (((flags & A8D1) == 0) &&
                ((flagd & A8D1) == 0) &&
                ((dsize & X8)   == 0)) {
                mlib_v_ImageChannelInsert_U8_13_A8D1X8((mlib_u8 *)sp,
                                                                                 (mlib_u8 *)dp,
                                                                               dsize,
                                                                                           ncmask);
            }
            else if (((flags & A8D2X8) == 0) &&
              ((flagd & A8D2X8) == 0)) {
                mlib_v_ImageChannelInsert_U8_13_A8D2X8((mlib_u8 *)sp, strides,
                                                                                     (mlib_u8 *)dp, strided,
                                                                             width, height,
                                                                                     ncmask);
            }
            else if (((flags & MLIB_IMAGE_ONEDVECTOR) == 0) &&
               ((flagd & MLIB_IMAGE_ONEDVECTOR) == 0)) {
                mlib_v_ImageChannelInsert_U8_13_D1((mlib_u8 *)sp,
                                                                                 (mlib_u8 *)dp,
                                                                                 dsize,
                                                                                 ncmask);
            }
            else {
              mlib_v_ImageChannelInsert_U8_13((mlib_u8 *)sp, strides,
                                                                      (mlib_u8 *)dp, strided,
                                                                      width, height,
                                                                      ncmask);
            }
            break;

          case 4:
            if (((flags & A8D1) == 0) &&
                ((flagd & A8D1) == 0) &&
                ((dsize & X8)   == 0)) {
                  mlib_v_ImageChannelInsert_U8_14_A8D1X8((mlib_u8 *)sp,
                                                                                   (mlib_u8 *)dp,
                                                                                 dsize,
                                                                                             ncmask);
            }
            else if (((flags & A8D2X8) == 0) &&
               ((flagd & A8D2X8) == 0)) {
               mlib_v_ImageChannelInsert_U8_14_A8D2X8((mlib_u8 *)sp, strides,
                                                                      (mlib_u8 *)dp, strided,
                                                                              width, height,
                                                                                          ncmask);
            }
            else if (((flags & MLIB_IMAGE_ONEDVECTOR) == 0) &&
              ((flagd & MLIB_IMAGE_ONEDVECTOR) == 0)) {
                mlib_v_ImageChannelInsert_U8_14_D1((mlib_u8 *)sp,
                                                                                 (mlib_u8 *)dp,
                                                                                 dsize,
                                                                                 ncmask);
            }
            else {
              mlib_v_ImageChannelInsert_U8_14((mlib_u8 *)sp, strides,
                                                                      (mlib_u8 *)dp, strided,
                                                                      width, height,
                                                                      ncmask);
            }
            break;

          default:
            return MLIB_FAILURE;
        }
      }
      else {
        if ((channels == 3) && (channeld == 4) && (ncmask == 7)) {
          if (((flags & A8D1) == 0) &&
            ((flagd & A8D1) == 0) &&
            ((dsize & X8)   == 0)) {
            mlib_v_ImageChannelInsert_U8_34R_A8D1X8((mlib_u8 *)sp,
                                                                          (mlib_u8 *)dp,
                                                                          dsize);
          }
        else if (((flags & A8D2X8) == 0) &&
               ((flagd & A8D2X8) == 0)) {
              mlib_v_ImageChannelInsert_U8_34R_A8D2X8((mlib_u8 *)sp, strides,
                                                                                    (mlib_u8 *)dp, strided,
                                                                              width, height);
        }
        else if (((flags & MLIB_IMAGE_ONEDVECTOR) == 0) &&
               ((flagd & MLIB_IMAGE_ONEDVECTOR) == 0)) {
              mlib_v_ImageChannelInsert_U8_34R_D1((mlib_u8 *)sp,
                                                                          (mlib_u8 *)dp,
                                                                          dsize);
        }
        else {
              mlib_v_ImageChannelInsert_U8_34R((mlib_u8 *)sp, strides,
                                                                      (mlib_u8 *)dp, strided,
                                                                      width, height);
        }
      }
      else if ((channels == 3) && (channeld == 4) && (ncmask == 14)) {
        if (((flags & A8D1) == 0) &&
            ((flagd & A8D1) == 0) &&
            ((dsize & X8)   == 0)) {
            mlib_v_ImageChannelInsert_U8_34L_A8D1X8((mlib_u8 *)sp,
                                                                            (mlib_u8 *)dp,
                                                                          dsize);
              }
        else if (((flags & A8D2X8) == 0) &&
                 ((flagd & A8D2X8) == 0)) {
                 mlib_v_ImageChannelInsert_U8_34L_A8D2X8((mlib_u8 *)sp, strides,
                                                                                  (mlib_u8 *)dp, strided,
                                                                          width, height);
        }
        else if (((flags & MLIB_IMAGE_ONEDVECTOR) == 0) &&
                 ((flagd & MLIB_IMAGE_ONEDVECTOR) == 0)) {
                 mlib_v_ImageChannelInsert_U8_34L_D1((mlib_u8 *)sp,
                                                                      (mlib_u8 *)dp,
                                                                      dsize);
        }
        else mlib_v_ImageChannelInsert_U8_34L((mlib_u8 *)sp, strides,
                                                                   (mlib_u8 *)dp, strided,
                                                                   width, height);
        }
      else {

      mlib_v_ImageChannelInsert_U8((mlib_u8 *)sp, strides,
                                                     (mlib_u8 *)dp, strided,
                                                     channels, channeld,
                                                     width, height,
                                                     ncmask);
      }
  }
  break;

    case MLIB_SHORT:
      if (channels == 1) {
        switch (channeld) {
          case 2:
            if (((flags & A8D1) == 0) &&
                ((flagd & A8D1) == 0) &&
                ((dsize & X4)   == 0)) {
              mlib_v_ImageChannelInsert_S16_12_A8D1X4((mlib_s16 *)sp,
                                                                                    (mlib_s16 *)dp,
                                                                                      dsize,
                                                                                      ncmask);
            }
            else if (((flags & A8D2X4) == 0) &&
               ((flagd & A8D2X4) == 0)) {
              mlib_v_ImageChannelInsert_S16_12_A8D2X4((mlib_s16 *)sp, strides,
                                                                              (mlib_s16 *)dp, strided,
                                                                              width, height,
                                                                                      ncmask);
            }
            else if (((flags & MLIB_IMAGE_ONEDVECTOR) == 0) &&
               ((flagd & MLIB_IMAGE_ONEDVECTOR) == 0)) {
             mlib_v_ImageChannelInsert_S16_12_D1((mlib_s16 *)sp,
                                                                           (mlib_s16 *)dp,
                                                                          dsize,
                                                                                  ncmask);
            }
            else {
              mlib_v_ImageChannelInsert_S16_12((mlib_s16 *)sp, strides,
                                                                       (mlib_s16 *)dp, strided,
                                                                       width, height,
                                                                       ncmask);
            }
            break;

          case 3:
            if (((flags & A8D1) == 0) &&
                ((flagd & A8D1) == 0) &&
                ((dsize & X4)   == 0)) {
              mlib_v_ImageChannelInsert_S16_13_A8D1X4((mlib_s16 *)sp,
                                                                              (mlib_s16 *)dp,
                                                                                      dsize,
                                                                                      ncmask);
            }
            else if (((flags & A8D2X4) == 0) &&
               ((flagd & A8D2X4) == 0)) {
              mlib_v_ImageChannelInsert_S16_13_A8D2X4((mlib_s16 *)sp, strides,
                                                                              (mlib_s16 *)dp, strided,
                                                                              width, height,
                                                                                      ncmask);
            }
            else if (((flags & MLIB_IMAGE_ONEDVECTOR) == 0) &&
               ((flagd & MLIB_IMAGE_ONEDVECTOR) == 0)) {
                mlib_v_ImageChannelInsert_S16_13_D1((mlib_s16 *)sp,
                                                                                  (mlib_s16 *)dp,
                                                                                  dsize,
                                                                                  ncmask);
            }
            else {
              mlib_v_ImageChannelInsert_S16_13((mlib_s16 *)sp, strides,
                                                                       (mlib_s16 *)dp, strided,
                                                                       width, height,
                                                                       ncmask);
            }
            break;

          case 4:
            if (((flags & A8D1) == 0) &&
                ((flagd & A8D1) == 0) &&
                ((dsize & X4)   == 0)) {
              mlib_v_ImageChannelInsert_S16_14_A8D1X4((mlib_s16 *)sp,
                                                                                    (mlib_s16 *)dp,
                                                      dsize,
                                                      ncmask);
            }
            else if (((flags & A8D2X4) == 0) &&
               ((flagd & A8D2X4) == 0)) {
              mlib_v_ImageChannelInsert_S16_14_A8D2X4((mlib_s16 *)sp, strides,
                                                                              (mlib_s16 *)dp, strided,
                                                                              width, height,
                                                                                      ncmask);
            }
            else if (((flags & MLIB_IMAGE_ONEDVECTOR) == 0) &&
               ((flagd & MLIB_IMAGE_ONEDVECTOR) == 0)) {
              mlib_v_ImageChannelInsert_S16_14_D1((mlib_s16 *)sp,
                                                                          (mlib_s16 *)dp,
                                                                          dsize,
                                                                                  ncmask);
            }
            else {
              mlib_v_ImageChannelInsert_S16_14((mlib_s16 *)sp, strides,
                                                                       (mlib_s16 *)dp, strided,
                                                                       width, height,
                                                                       ncmask);
            }
            break;
          default:
            return MLIB_FAILURE;
        }
      }
      else if ((channels == 3) && (channeld == 4) && (ncmask == 7)) {
        if (((flags & A8D1) == 0) &&
            ((flagd & A8D1) == 0) &&
            ((dsize & X4)   == 0)) {
          mlib_v_ImageChannelInsert_S16_34R_A8D1X4((mlib_s16 *)sp,
                                                                           (mlib_s16 *)dp,
                                                                           dsize);
        }
        else if (((flags & A8D2X4) == 0) &&
           ((flagd & A8D2X4) == 0)) {
          mlib_v_ImageChannelInsert_S16_34R_A8D2X4((mlib_s16 *)sp, strides,
                                                                           (mlib_s16 *)dp, strided,
                                                                           width, height);
        }
        else if (((flags & MLIB_IMAGE_ONEDVECTOR) == 0) &&
           ((flagd & MLIB_IMAGE_ONEDVECTOR) == 0)) {
          mlib_v_ImageChannelInsert_S16_34R_D1((mlib_s16 *)sp,
                                                                       (mlib_s16 *)dp,
                                                                       dsize);
        }
        else {
          mlib_v_ImageChannelInsert_S16_34R((mlib_s16 *)sp, strides,
                                                                    (mlib_s16 *)dp, strided,
                                                                     width, height);
        }
      }
      else if ((channels == 3) && (channeld == 4) && (ncmask == 14)) {
        if (((flags & A8D1) == 0) &&
            ((flagd & A8D1) == 0) &&
            ((dsize & X4)   == 0)) {
          mlib_v_ImageChannelInsert_S16_34L_A8D1X4((mlib_s16 *)sp,
                                                                           (mlib_s16 *)dp,
                                                                           dsize);
        }
        else if (((flags & A8D2X4) == 0) &&
           ((flagd & A8D2X4) == 0)) {
          mlib_v_ImageChannelInsert_S16_34L_A8D2X4((mlib_s16 *)sp, strides,
                                                                           (mlib_s16 *)dp, strided,
                                                                           width, height);
        }
        else if (((flags & MLIB_IMAGE_ONEDVECTOR) == 0) &&
           ((flagd & MLIB_IMAGE_ONEDVECTOR) == 0)) {
          mlib_v_ImageChannelInsert_S16_34L_D1((mlib_s16 *)sp,
                                                                       (mlib_s16 *)dp,
                                                                       dsize);
        }
        else {
          mlib_v_ImageChannelInsert_S16_34L((mlib_s16 *)sp, strides,
                                                                    (mlib_s16 *)dp, strided,
                                                                    width, height);
        }
      }
      else {
        mlib_v_ImageChannelInsert_S16((mlib_s16 *)sp, strides,
                                                              (mlib_s16 *)dp, strided,
                                                              channels,  channeld,
                                                              width, height,
                                                              ncmask);
      }
      break;

    case MLIB_INT:
        mlib_v_ImageChannelInsert_S32((mlib_s32 *)sp, strides,
                                      (mlib_s32 *)dp, strided,
                                      channels, channeld,
                                      width, height,
                                      ncmask);
        break;

    case MLIB_FLOAT:
        mlib_v_ImageChannelInsert_S32((mlib_s32 *)sp, strides,
                                      (mlib_s32 *)dp, strided,
                                      channels, channeld,
                                      width, height,
                                      ncmask);
        break;


    case MLIB_DOUBLE:
        mlib_v_ImageChannelInsert_D64((mlib_d64 *)sp, strides,
                                      (mlib_d64 *)dp, strided,
                                      channels, channeld,
                                      width, height,
                                      ncmask);
        break;


    case MLIB_BIT:
    default:
        return MLIB_FAILURE;    /* MLIB_BIT is not supported here */
  }

  return MLIB_SUCCESS;
}
/***************************************************************/
