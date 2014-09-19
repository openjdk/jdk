/*
 * Copyright (c) 2000, 2003, Oracle and/or its affiliates. All rights reserved.
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
 * FUNCTION
 *      mlib_ImageCopy   - Direct copy from one image to another.
 *
 * SYNOPSIS
 *      mlib_status mlib_ImageCopy(mlib_image *dst,
 *                                 mlib_image *src);
 *
 * ARGUMENT
 *      dst     pointer to output or destination image
 *      src     pointer to input or source image
 *
 * RESTRICTION
 *      src and dst must have the same size, type and number of channels.
 *      They can have 1, 2, 3 or 4 channels of MLIB_BIT, MLIB_BYTE,
 *      MLIB_SHORT, MLIB_INT, MLIB_FLOAT or MLIB_DOUBLE data type.
 *
 * DESCRIPTION
 *      Direct copy from one image to another.
 */

#include <stdlib.h>
#include "mlib_image.h"
#include "mlib_ImageCheck.h"

/***************************************************************/

extern void mlib_v_ImageCopy_blk(mlib_u8 *sa, mlib_u8 *da, mlib_s32 size);
extern void mlib_v_ImageCopy_a1(mlib_d64 *sp, mlib_d64 *dp, mlib_s32 size);
extern void mlib_ImageCopy_na(mlib_u8 *sa, mlib_u8 *da, mlib_s32 size);
extern void mlib_ImageCopy_bit_al(mlib_u8 *sa, mlib_u8 *da,
                                  mlib_s32 size, mlib_s32 offset);
extern void mlib_ImageCopy_bit_na(mlib_u8 *sa, mlib_u8 *da, mlib_s32 size,
                                  mlib_s32 s_offset, mlib_s32 d_offset);

/***************************************************************/

#ifdef MLIB_TEST

mlib_status mlib_v_ImageCopy(mlib_image *dst, mlib_image *src)

#else

mlib_status mlib_ImageCopy(mlib_image *dst, const mlib_image *src)

#endif
{
  mlib_u8  *sa;         /* start point in source */
  mlib_u8  *da;         /* start points in destination */
  mlib_s32 width;       /* width in bytes of src and dst */
  mlib_s32 height;      /* height in lines of src and dst */
  mlib_s32 s_offset;    /* bit offset of src */
  mlib_s32 d_offset;    /* bit offset of dst */
  mlib_s32 stride;      /* stride in bytes in src*/
  mlib_s32 dstride;     /* stride in bytes in dst */
  mlib_s32 j;           /* indices for x, y */
  mlib_s32 size;

  MLIB_IMAGE_CHECK(src);
  MLIB_IMAGE_CHECK(dst);
  MLIB_IMAGE_TYPE_EQUAL(src, dst);
  MLIB_IMAGE_CHAN_EQUAL(src, dst);
  MLIB_IMAGE_SIZE_EQUAL(src, dst);

  width  = mlib_ImageGetWidth(dst) * mlib_ImageGetChannels(dst);
  height = mlib_ImageGetHeight(dst);
  sa = (mlib_u8 *)mlib_ImageGetData(src);
  da = (mlib_u8 *)mlib_ImageGetData(dst);

  switch (mlib_ImageGetType(dst)) {
    case MLIB_BIT:

      if (!mlib_ImageIsNotOneDvector(src) &&
          !mlib_ImageIsNotOneDvector(dst)) {
          size = height * (width  >> 3);
          if ((size & 0x3f) == 0 &&
              !mlib_ImageIsNotAligned64(src) &&
              !mlib_ImageIsNotAligned64(dst)) {

              mlib_v_ImageCopy_blk(sa, da, size);
              return MLIB_SUCCESS;
          }
          if (((size & 7) == 0) && !mlib_ImageIsNotAligned8(src) &&
              !mlib_ImageIsNotAligned8(dst)) {

              size >>= 3;                                /* in octlet */
              mlib_v_ImageCopy_a1((mlib_d64 *)sa, (mlib_d64 *)da, size);
          }
          else {

            mlib_ImageCopy_na(sa, da, size);
          }
        }
      else {
        stride = mlib_ImageGetStride(src);                /* in byte */
        dstride = mlib_ImageGetStride(dst);               /* in byte */
        s_offset = mlib_ImageGetBitOffset(src);           /* in bits */
        d_offset = mlib_ImageGetBitOffset(dst);           /* in bits */

        if (s_offset == d_offset) {
          for (j = 0; j < height; j++) {
            mlib_ImageCopy_bit_al(sa, da, width, s_offset);
            sa += stride;
            da += dstride;
          }
        } else {
          for (j = 0; j < height; j++) {
            mlib_ImageCopy_bit_na(sa, da, width, s_offset, d_offset);
            sa += stride;
            da += dstride;
          }
        }
      }
      return MLIB_SUCCESS;
    case MLIB_BYTE:
      break;
    case MLIB_SHORT:
      width *= 2;
      break;
    case MLIB_INT:
    case MLIB_FLOAT:
      width *= 4;
      break;
    case MLIB_DOUBLE:
      width *= 8;
      break;
    default:
      return MLIB_FAILURE;
  }

  if (!mlib_ImageIsNotOneDvector(src) &&
      !mlib_ImageIsNotOneDvector(dst)) {
      size = height * width;
      if ((size & 0x3f) == 0 &&
          !mlib_ImageIsNotAligned64(src) &&
          !mlib_ImageIsNotAligned64(dst)) {

          mlib_v_ImageCopy_blk(sa, da, size);
          return MLIB_SUCCESS;
      }
      if (((size & 7) == 0) && !mlib_ImageIsNotAligned8(src) &&
          !mlib_ImageIsNotAligned8(dst)) {

          size >>= 3;                                /* in octlet */
          mlib_v_ImageCopy_a1((mlib_d64 *)sa, (mlib_d64 *)da, size);
      }
      else {

        mlib_ImageCopy_na(sa, da, size);
      }
    }
  else {
    stride = mlib_ImageGetStride(src);                /* in byte */
    dstride = mlib_ImageGetStride(dst);                /* in byte */

    /* row loop */
    for (j = 0; j < height; j++) {
      mlib_ImageCopy_na(sa, da, width);
      sa += stride;
      da += dstride;
    }
  }
  return MLIB_SUCCESS;
}

/***************************************************************/
