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


#ifndef __MLIB_IMAGECOLORMAP_H
#define __MLIB_IMAGECOLORMAP_H

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct {
  void **lut;
  mlib_s32 channels;
  mlib_type intype;
  mlib_s32 offset;
  void *table;
  mlib_s32 bits;
  mlib_s32 method;
  mlib_s32 lutlength;
  mlib_s32 indexsize;
  mlib_type outtype;
  void *normal_table;
  mlib_d64 *double_lut;
} mlib_colormap;

/***************************************************************/
#define LUT_COLOR_CUBE_SEARCH  0
#define LUT_BINARY_TREE_SEARCH 1
#define LUT_STUPID_SEARCH      2
#define LUT_COLOR_DIMENSIONS   3

/***************************************************************/

/* Bit set in the tag denotes that the corresponding quadrant is a
   palette index, not node. If the bit is clear, this means that that
   is a pointer to the down level node. If the bit is clear and the
   corresponding quadrant is NULL, then there is no way down there and
   this quadrant is clear. */

struct lut_node_3 {
  mlib_u8 tag;
  union {
    struct lut_node_3 *quadrants[8];
    long index[8];
  } contents;
};

struct lut_node_4 {
  mlib_u16 tag;
  union {
    struct lut_node_4 *quadrants[16];
    long index[16];
  } contents;
};

/***************************************************************/

#define mlib_ImageGetLutData(colormap)                          \
  ((void **)((( mlib_colormap *)( colormap))->lut))

/***************************************************************/
#define mlib_ImageGetLutNormalTable(colormap)                   \
  ((void *)((( mlib_colormap *)( colormap))->normal_table))

/***************************************************************/
#define mlib_ImageGetLutInversTable(colormap)                   \
  ((void *)((( mlib_colormap *)( colormap))->table))

/***************************************************************/
#define mlib_ImageGetLutChannels(colormap)                      \
  ((mlib_s32)((( mlib_colormap *)( colormap))->channels))

/***************************************************************/
#define mlib_ImageGetLutType(colormap)                          \
  ((mlib_type)((( mlib_colormap *)( colormap))->intype))

/***************************************************************/
#define mlib_ImageGetIndexSize(colormap)                        \
  ((mlib_s32)((( mlib_colormap *)( colormap))->indexsize))

/***************************************************************/
#define mlib_ImageGetOutType(colormap)                          \
  ((mlib_type)((( mlib_colormap *)( colormap))->outtype))

/***************************************************************/
#define mlib_ImageGetLutOffset(colormap)                        \
  ((mlib_s32)((( mlib_colormap *)( colormap))->offset))

/***************************************************************/
#define mlib_ImageGetBits(colormap)                             \
  ((mlib_s32)((( mlib_colormap *)( colormap))->bits))

/***************************************************************/
#define mlib_ImageGetMethod(colormap)                           \
  ((mlib_s32)((( mlib_colormap *)( colormap))->method))

/***************************************************************/
#define mlib_ImageGetLutDoubleData(colormap)                    \
  ((mlib_d64 *)((( mlib_colormap *)( colormap))->double_lut))

/***************************************************************/
#define FIND_DISTANCE_3( x1, x2, y1, y2, z1, z2, SHIFT )        \
 (( ( ( ( x1 ) - ( x2 ) ) * ( ( x1 ) - ( x2 ) ) ) >> SHIFT ) +  \
  ( ( ( ( y1 ) - ( y2 ) ) * ( ( y1 ) - ( y2 ) ) ) >> SHIFT ) +  \
  ( ( ( ( z1 ) - ( z2 ) ) * ( ( z1 ) - ( z2 ) ) ) >> SHIFT ) )

/***************************************************************/
#define FIND_DISTANCE_4( x1, x2, y1, y2, z1, z2, w1, w2, SHIFT ) \
  (( ( ( ( x1 ) - ( x2 ) ) * ( ( x1 ) - ( x2 ) ) ) >> SHIFT ) +  \
   ( ( ( ( y1 ) - ( y2 ) ) * ( ( y1 ) - ( y2 ) ) ) >> SHIFT ) +  \
   ( ( ( ( z1 ) - ( z2 ) ) * ( ( z1 ) - ( z2 ) ) ) >> SHIFT ) +  \
   ( ( ( ( w1 ) - ( w2 ) ) * ( ( w1 ) - ( w2 ) ) ) >> SHIFT ) )

/***************************************************************/

void mlib_ImageColorTrue2IndexLine_U8_BIT_1(const mlib_u8 *src,
                                            mlib_u8       *dst,
                                            mlib_s32      bit_offset,
                                            mlib_s32      length,
                                            const void    *state);


void mlib_ImageColorTrue2IndexLine_U8_U8_3(const mlib_u8 *src,
                                           mlib_u8       *dst,
                                           mlib_s32      length,
                                           const void    *colormap);


void mlib_ImageColorTrue2IndexLine_U8_U8_3_in_4(const mlib_u8 *src,
                                                mlib_u8       *dst,
                                                mlib_s32      length,
                                                const void    *colormap);


void mlib_ImageColorTrue2IndexLine_U8_U8_4(const mlib_u8 *src,
                                           mlib_u8       *dst,
                                           mlib_s32      length,
                                           const void    *colormap);


void mlib_ImageColorTrue2IndexLine_U8_S16_3(const mlib_u8 *src,
                                            mlib_s16      *dst,
                                            mlib_s32      length,
                                            const void    *colormap);


void mlib_ImageColorTrue2IndexLine_U8_S16_3_in_4(const mlib_u8 *src,
                                                 mlib_s16      *dst,
                                                 mlib_s32      length,
                                                 const void    *colormap);


void mlib_ImageColorTrue2IndexLine_U8_S16_4(const mlib_u8 *src,
                                            mlib_s16      *dst,
                                            mlib_s32      length,
                                            const void    *colormap);


void mlib_ImageColorTrue2IndexLine_S16_S16_3(const mlib_s16 *src,
                                             mlib_s16       *dst,
                                             mlib_s32       length,
                                             const void     *colormap);


void mlib_ImageColorTrue2IndexLine_S16_S16_3_in_4(const mlib_s16 *src,
                                                  mlib_s16       *dst,
                                                  mlib_s32       length,
                                                  const void     *colormap);


void mlib_ImageColorTrue2IndexLine_S16_S16_4(const mlib_s16 *src,
                                             mlib_s16       *dst,
                                             mlib_s32       length,
                                             const void     *colormap);


void mlib_ImageColorTrue2IndexLine_S16_U8_3(const mlib_s16 *src,
                                            mlib_u8        *dst,
                                            mlib_s32       length,
                                            const void     *colormap);


void mlib_ImageColorTrue2IndexLine_S16_U8_3_in_4(const mlib_s16 *src,
                                                 mlib_u8        *dst,
                                                 mlib_s32       length,
                                                 const void     *colormap);


void mlib_ImageColorTrue2IndexLine_S16_U8_4(const mlib_s16 *src,
                                            mlib_u8        *dst,
                                            mlib_s32       length,
                                            const void     *colormap);


#ifdef __cplusplus
}
#endif /* __cplusplus */
#endif /* __MLIB_IMAGECOLORMAP_H */
