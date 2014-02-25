/*
 * Copyright (c) 2003, 2011, Oracle and/or its affiliates. All rights reserved.
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
 *      mlib_ImageColorTrue2Index - convert a true color image to an indexed
 *                                  color image
 *
 * SYNOPSIS
 *      mlib_status mlib_ImageColorTrue2Index(mlib_image       *dst,
 *                                            const mlib_image *src,
 *                                            const void       *colormap)
 *
 * ARGUMENTS
 *      colormap  Internal data structure for inverse color mapping.
 *      dst       Pointer to destination image.
 *      src       Pointer to source image.
 *
 * DESCRIPTION
 *      Convert a true color image to a pseudo color image with the method
 *      of finding the nearest matched lut entry for each pixel.
 *
 *      The src can be an MLIB_BYTE or MLIB_SHORT image with 3 or 4 channels.
 *      The dst must be a 1-channel MLIB_BYTE or MLIB_SHORT image.
 *
 *      The lut might have either 3 or 4 channels. The type of the lut can be
 *      one of the following:
 *              MLIB_BYTE in, MLIB_BYTE out (i.e., BYTE-to-BYTE)
 *              MLIB_BYTE in, MLIB_SHORT out (i.e., BYTE-to-SHORT)
 *              MLIB_SHORT in, MLIB_SHORT out (i.e., SHORT-to-SHORT)
 *              MLIB_SHORT in, MLIB_BYTE out (i.e., SHORT-to-BYTE)
 *
 *      The src image and the lut must have same number of channels.
 */

#include "mlib_image.h"
#include "mlib_ImageColormap.h"
#include "mlib_ImageCheck.h"

/***************************************************************/

/*#define USE_VIS_CODE*/

#ifdef USE_VIS_CODE
#include "vis_proto.h"
#define VIS_ALIGNADDR(X, Y)  vis_alignaddr((void *)(X), (Y))
#endif

/***************************************************************/

#define LUT_BYTE_COLORS_3CHANNELS  1000
#define LUT_BYTE_COLORS_4CHANNELS  3000
#define LUT_SHORT_COLORS_3CHANNELS 1000
#define LUT_SHORT_COLORS_4CHANNELS 1000

/***************************************************************/

#define MAIN_COLORTRUE2INDEX_LOOP( FROM_TYPE, TO_TYPE, NCHANNELS )       \
  for( y = 0; y < height; y++ )                                          \
  {                                                                      \
    mlib_ImageColorTrue2IndexLine_##FROM_TYPE##_##TO_TYPE##_##NCHANNELS( \
      sdata, ddata, width, colormap );                                   \
                                                                         \
    sdata += sstride;                                                    \
    ddata += dstride;                                                    \
  }

/***************************************************************/

#define COLOR_CUBE_U8_3_SEARCH( TABLE_POINTER_TYPE, SHIFT, STEP ) \
{                                                                 \
  const mlib_u8 *c0, *c1, *c2;                                    \
  TABLE_POINTER_TYPE *table = s->table;                           \
  mlib_s32 bits = s->bits;                                        \
  mlib_s32 nbits = 8 - bits;                                      \
  mlib_s32 mask = ~( ( 1 << nbits ) - 1 );                        \
  mlib_s32 j;                                                     \
                                                                  \
  c0 = src + SHIFT;                                               \
  c1 = src + 1 + SHIFT;                                           \
  c2 = src + 2 + SHIFT;                                           \
                                                                  \
  switch( bits )                                                  \
  {                                                               \
    case 1:                                                       \
    case 2:                                                       \
    {                                                             \
      mlib_s32 bits0 = 8 - bits;                                  \
      mlib_s32 bits1 = bits0 - bits;                              \
      mlib_s32 bits2 = bits1 - bits;                              \
                                                                  \
      for( j = 0; j < length; j++ )                               \
      {                                                           \
        dst[ j ] = table[ ( ( *c0 & mask ) >> bits2 ) |           \
          ( ( *c1 & mask ) >> bits1 ) |                           \
          ( ( *c2 & mask ) >> bits0 ) ];                          \
                                                                  \
        c0 += STEP;                                               \
        c1 += STEP;                                               \
        c2 += STEP;                                               \
      }                                                           \
      break;                                                      \
    }                                                             \
    case 3:                                                       \
    {                                                             \
      for( j = 0; j < length; j++ )                               \
      {                                                           \
        dst[ j ] = table[ ( ( *c0 & mask ) << 1 ) |               \
          ( ( *c1 & mask ) >> 2 ) |                               \
          ( ( *c2 & mask ) >> 5 ) ];                              \
                                                                  \
        c0 += STEP;                                               \
        c1 += STEP;                                               \
        c2 += STEP;                                               \
      }                                                           \
      break;                                                      \
    }                                                             \
    case 4:                                                       \
    {                                                             \
      for( j = 0; j < length; j++ )                               \
      {                                                           \
        dst[ j ] = table[ ( ( *c0 & mask ) << 4 ) |               \
          ( *c1 & mask ) |                                        \
          ( ( *c2 & mask ) >> 4 ) ];                              \
                                                                  \
        c0 += STEP;                                               \
        c1 += STEP;                                               \
        c2 += STEP;                                               \
      }                                                           \
      break;                                                      \
    }                                                             \
    case 5:                                                       \
    case 6:                                                       \
    case 7:                                                       \
    {                                                             \
      mlib_s32 bits0 = 8 - bits;                                  \
      mlib_s32 bits1 = bits * 2 - 8;                              \
      mlib_s32 bits2 = bits1 + bits;                              \
                                                                  \
      for( j = 0; j < length; j++ )                               \
      {                                                           \
        dst[ j ] = table[ ( ( *c0 & mask ) << bits2 ) |           \
          ( ( *c1 & mask ) << bits1 ) |                           \
          ( ( *c2 & mask ) >> bits0 ) ];                          \
                                                                  \
        c0 += STEP;                                               \
        c1 += STEP;                                               \
        c2 += STEP;                                               \
      }                                                           \
      break;                                                      \
    }                                                             \
    case 8:                                                       \
    {                                                             \
      for( j = 0; j < length; j++ )                               \
      {                                                           \
        dst[ j ] = table[ ( ( *c0 & mask ) << 16 ) |              \
          ( ( *c1 & mask ) << 8 ) |                               \
          ( *c2 & mask ) ];                                       \
                                                                  \
        c0 += STEP;                                               \
        c1 += STEP;                                               \
        c2 += STEP;                                               \
      }                                                           \
      break;                                                      \
    }                                                             \
  }                                                               \
}

/***************************************************************/
#define COLOR_CUBE_U8_4_SEARCH( TABLE_TYPE )                    \
{                                                               \
  const mlib_u8 *c0, *c1, *c2, *c3;                             \
  TABLE_TYPE *table = s->table;                                 \
  mlib_s32 bits = s->bits;                                      \
  mlib_s32 nbits = 8 - bits;                                    \
  mlib_s32 mask = ~( ( 1 << nbits ) - 1 );                      \
  mlib_s32 j;                                                   \
                                                                \
  c0 = src;                                                     \
  c1 = src + 1;                                                 \
  c2 = src + 2;                                                 \
  c3 = src + 3;                                                 \
                                                                \
  switch( bits )                                                \
  {                                                             \
    case 1:                                                     \
    {                                                           \
      for( j = 0; j < length; j++ )                             \
      {                                                         \
        dst[ j ] = table[ ( ( *c0 & mask ) >> 4 ) |             \
          ( ( *c1 & mask ) >> 5 ) |                             \
          ( ( *c2 & mask ) >> 6 ) |                             \
          ( ( *c3 & mask ) >> 7 ) ];                            \
                                                                \
        c0 += 4;                                                \
        c1 += 4;                                                \
        c2 += 4;                                                \
        c3 += 4;                                                \
      }                                                         \
      break;                                                    \
    }                                                           \
    case 2:                                                     \
    {                                                           \
      for( j = 0; j < length; j++ )                             \
      {                                                         \
        dst[ j ] = table[ ( *c0 & mask ) |                      \
          ( ( *c1 & mask ) >> 2 ) |                             \
          ( ( *c2 & mask ) >> 4 ) |                             \
          ( ( *c3 & mask ) >> 6 ) ];                            \
                                                                \
        c0 += 4;                                                \
        c1 += 4;                                                \
        c2 += 4;                                                \
        c3 += 4;                                                \
          }                                                     \
      break;                                                    \
    }                                                           \
    case 3:                                                     \
    {                                                           \
      for( j = 0; j < length; j++ )                             \
      {                                                         \
        dst[ j ] = table[ ( ( *c0 & mask ) << 4 ) |             \
          ( ( *c1 & mask ) << 1 ) |                             \
          ( ( *c2 & mask ) >> 2 ) |                             \
          ( ( *c3 & mask ) >> 5 ) ];                            \
                                                                \
        c0 += 4;                                                \
        c1 += 4;                                                \
        c2 += 4;                                                \
        c3 += 4;                                                \
      }                                                         \
      break;                                                    \
    }                                                           \
    case 4:                                                     \
    {                                                           \
      for( j = 0; j < length; j++ )                             \
      {                                                         \
        dst[ j ] = table[ ( ( *c0 & mask ) << 8 ) |             \
          ( ( *c1 & mask ) << 4 ) |                             \
          ( *c2 & mask ) |                                      \
          ( ( *c3 & mask ) >> 4 ) ];                            \
                                                                \
        c0 += 4;                                                \
        c1 += 4;                                                \
        c2 += 4;                                                \
        c3 += 4;                                                \
      }                                                         \
      break;                                                    \
    }                                                           \
    case 5:                                                     \
    case 6:                                                     \
    {                                                           \
      mlib_s32 bits3 = bits * 4 - 8;                            \
      mlib_s32 bits2 = bits3 - bits;                            \
      mlib_s32 bits1 = bits2 - bits;                            \
      mlib_s32 bits0 = 8 - bits;                                \
                                                                \
      for( j = 0; j < length; j++ )                             \
      {                                                         \
        dst[ j ] = table[ ( ( *c0 & mask ) << bits3 ) |         \
          ( ( *c1 & mask ) << bits2 ) |                         \
          ( ( *c2 & mask ) << bits1 ) |                         \
          ( ( *c3 & mask ) >> bits0 ) ];                        \
                                                                \
        c0 += 4;                                                \
        c1 += 4;                                                \
        c2 += 4;                                                \
        c3 += 4;                                                \
      }                                                         \
      break;                                                    \
    }                                                           \
    case 7:                                                     \
    {                                                           \
      for( j = 0; j < length; j++ )                             \
      {                                                         \
        dst[ j ] = table[ ( ( *c0 & mask ) << 20 ) |            \
          ( ( *c1 & mask ) << 13 ) |                            \
          ( ( *c2 & mask ) << 6 ) |                             \
          ( ( *c3 & mask ) >> 1 ) ];                            \
                                                                \
        c0 += 4;                                                \
        c1 += 4;                                                \
        c2 += 4;                                                \
        c3 += 4;                                                \
      }                                                         \
      break;                                                    \
    }                                                           \
    case 8: /* will never be called */                          \
    {                                                           \
      for( j = 0; j < length; j++ )                             \
      {                                                         \
        dst[ j ] = table[ ( ( *c0 & mask ) << 24 ) |            \
          ( ( *c1 & mask ) << 16 ) |                            \
          ( ( *c2 & mask ) << 8 ) |                             \
          ( *c3 & mask ) ];                                     \
                                                                \
        c0 += 4;                                                \
        c1 += 4;                                                \
        c2 += 4;                                                \
        c3 += 4;                                                \
      }                                                         \
      break;                                                    \
    }                                                           \
  }                                                             \
}

/***************************************************************/
#define COLOR_CUBE_S16_3_SEARCH( TABLE_TYPE, SHIFT, STEP )                 \
{                                                                          \
  const mlib_s16 *c0, *c1, *c2;                                            \
  mlib_s32 bits = s->bits;                                                 \
  mlib_s32 nbits = 16 - bits;                                              \
  mlib_s32 mask = ~( ( 1 << nbits ) - 1 );                                 \
  TABLE_TYPE *table = s->table;                                            \
  mlib_s32 j;                                                              \
                                                                           \
  c0 = src + SHIFT;                                                        \
  c1 = src + 1 + SHIFT;                                                    \
  c2 = src + 2 + SHIFT;                                                    \
                                                                           \
  switch( bits )                                                           \
  {                                                                        \
    case 1:                                                                \
    case 2:                                                                \
    case 3:                                                                \
    case 4:                                                                \
    case 5:                                                                \
    {                                                                      \
      mlib_s32 bits0 = 16 - bits;                                          \
      mlib_s32 bits1 = bits0 - bits;                                       \
      mlib_s32 bits2 = bits1 - bits;                                       \
                                                                           \
      for( j = 0; j < length; j++ )                                        \
      {                                                                    \
        dst[ j ] = table[ ( ( ( *c0 - MLIB_S16_MIN ) & mask ) >> bits2 ) | \
          ( ( ( *c1 - MLIB_S16_MIN ) & mask ) >> bits1 ) |                 \
          ( ( ( *c2 - MLIB_S16_MIN ) & mask ) >> bits0 ) ];                \
                                                                           \
        c0 += STEP;                                                        \
        c1 += STEP;                                                        \
        c2 += STEP;                                                        \
      }                                                                    \
      break;                                                               \
    }                                                                      \
    case 6:                                                                \
    case 7:                                                                \
    {                                                                      \
      mlib_s32 bits0 = 16 - bits;                                          \
      mlib_s32 bits1 = bits0 - bits;                                       \
      mlib_s32 bits2 = bits * 3 - 16;                                      \
                                                                           \
      for( j = 0; j < length; j++ )                                        \
      {                                                                    \
        dst[ j ] = table[ ( ( ( *c0 - MLIB_S16_MIN ) & mask ) << bits2 ) | \
          ( ( ( *c1 - MLIB_S16_MIN ) & mask ) >> bits1 ) |                 \
          ( ( ( *c2 - MLIB_S16_MIN ) & mask ) >> bits0 ) ];                \
                                                                           \
        c0 += STEP;                                                        \
        c1 += STEP;                                                        \
        c2 += STEP;                                                        \
      }                                                                    \
      break;                                                               \
    }                                                                      \
    case 8:                                                                \
    {                                                                      \
      for( j = 0; j < length; j++ )                                        \
      {                                                                    \
        dst[ j ] = table[ ( ( ( *c0 - MLIB_S16_MIN ) & mask ) << 8 ) |     \
          ( ( *c1 - MLIB_S16_MIN ) & mask ) |                              \
          ( ( ( *c2 - MLIB_S16_MIN ) & mask ) >> 8 ) ];                    \
                                                                           \
        c0 += STEP;                                                        \
        c1 += STEP;                                                        \
        c2 += STEP;                                                        \
      }                                                                    \
      break;                                                               \
    }                                                                      \
    case 9:                                                                \
    case 10:                                                               \
    {                                                                      \
      mlib_s32 bits0 = 16 - bits;                                          \
      mlib_s32 bits1 = 2 * bits - 16;                                      \
      mlib_s32 bits2 = bits1 + bits;                                       \
                                                                           \
      for( j = 0; j < length; j++ )                                        \
      {                                                                    \
        dst[ j ] = table[ ( ( ( *c0 - MLIB_S16_MIN ) & mask ) << bits2 ) | \
          ( ( ( *c1 - MLIB_S16_MIN ) & mask ) << bits1 ) |                 \
          ( ( ( *c2 - MLIB_S16_MIN ) & mask ) >> bits0 ) ];                \
                                                                           \
        c0 += STEP;                                                        \
        c1 += STEP;                                                        \
        c2 += STEP;                                                        \
      }                                                                    \
      break;                                                               \
    }                                                                      \
    /* Other cases may not be considered as the table size will be more    \
       than 2^32 */                                                        \
  }                                                                        \
}

/***************************************************************/
#define COLOR_CUBE_S16_4_SEARCH( TABLE_TYPE )                              \
{                                                                          \
  const mlib_s16 *c0, *c1, *c2, *c3;                                       \
  TABLE_TYPE *table = s->table;                                            \
  mlib_s32 bits = s->bits;                                                 \
  mlib_s32 nbits = 16 - bits;                                              \
  mlib_s32 mask = ~( ( 1 << nbits ) - 1 );                                 \
  mlib_s32 j;                                                              \
                                                                           \
  c0 = src;                                                                \
  c1 = src + 1;                                                            \
  c2 = src + 2;                                                            \
  c3 = src + 3;                                                            \
                                                                           \
  switch( bits )                                                           \
  {                                                                        \
    case 1:                                                                \
    case 2:                                                                \
    case 3:                                                                \
    {                                                                      \
      mlib_s32 bits0 = 16 - bits;                                          \
      mlib_s32 bits1 = bits0 - bits;                                       \
      mlib_s32 bits2 = bits1 - bits;                                       \
      mlib_s32 bits3 = bits2 - bits;                                       \
                                                                           \
      for( j = 0; j < length; j++ )                                        \
      {                                                                    \
        dst[ j ] = table[ ( ( ( *c0 - MLIB_S16_MIN ) & mask ) >> bits3 ) | \
          ( ( ( *c1 - MLIB_S16_MIN ) & mask ) >> bits2 ) |                 \
          ( ( ( *c2 - MLIB_S16_MIN ) & mask ) >> bits1 ) |                 \
          ( ( ( *c3 - MLIB_S16_MIN ) & mask ) >> bits0 ) ];                \
                                                                           \
        c0 += 4;                                                           \
        c1 += 4;                                                           \
        c2 += 4;                                                           \
        c3 += 4;                                                           \
      }                                                                    \
      break;                                                               \
    }                                                                      \
    case 4:                                                                \
    {                                                                      \
      for( j = 0; j < length; j++ )                                        \
      {                                                                    \
        dst[ j ] = table[ ( ( *c0 - MLIB_S16_MIN ) & mask ) |              \
          ( ( ( *c1 - MLIB_S16_MIN ) & mask ) >> 4 ) |                     \
          ( ( ( *c2 - MLIB_S16_MIN ) & mask ) >> 8 ) |                     \
          ( ( ( *c3 - MLIB_S16_MIN ) & mask ) >> 12 ) ];                   \
                                                                           \
        c0 += 4;                                                           \
        c1 += 4;                                                           \
        c2 += 4;                                                           \
        c3 += 4;                                                           \
      }                                                                    \
      break;                                                               \
    }                                                                      \
    case 5:                                                                \
    {                                                                      \
      for( j = 0; j < length; j++ )                                        \
      {                                                                    \
        dst[ j ] = table[ ( ( ( *c0 - MLIB_S16_MIN ) & mask ) << 4 ) |     \
          ( ( ( *c1 - MLIB_S16_MIN ) & mask ) >> 1 ) |                     \
          ( ( ( *c2 - MLIB_S16_MIN ) & mask ) >> 6 ) |                     \
          ( ( ( *c3 - MLIB_S16_MIN ) & mask ) >> 11 ) ];                   \
                                                                           \
        c0 += 4;                                                           \
        c1 += 4;                                                           \
        c2 += 4;                                                           \
        c3 += 4;                                                           \
      }                                                                    \
      break;                                                               \
    }                                                                      \
    case 6:                                                                \
    case 7:                                                                \
    {                                                                      \
      mlib_s32 bits0 = 16 - bits;                                          \
      mlib_s32 bits1 = bits0 - bits;                                       \
      mlib_s32 bits3 = bits * 4 - 16;                                      \
      mlib_s32 bits2 = bits3 - bits;                                       \
                                                                           \
      for( j = 0; j < length; j++ )                                        \
      {                                                                    \
        dst[ j ] = table[ ( ( ( *c0 - MLIB_S16_MIN ) & mask ) << bits3 ) | \
          ( ( ( *c1 - MLIB_S16_MIN ) & mask ) << bits2 ) |                 \
          ( ( ( *c2 - MLIB_S16_MIN ) & mask ) >> bits1 ) |                 \
          ( ( ( *c3 - MLIB_S16_MIN ) & mask ) >> bits0 ) ];                \
                                                                           \
        c0 += 4;                                                           \
        c1 += 4;                                                           \
        c2 += 4;                                                           \
        c3 += 4;                                                           \
      }                                                                    \
      break;                                                               \
    }                                                                      \
    case 8:                                                                \
    {                                                                      \
      for( j = 0; j < length; j++ )                                        \
      {                                                                    \
        dst[ j ] = table[ ( ( ( *c0 - MLIB_S16_MIN ) & mask ) << 16 ) |    \
          ( ( ( *c1 - MLIB_S16_MIN ) & mask ) << 8 ) |                     \
          ( ( *c2 - MLIB_S16_MIN ) & mask ) |                              \
          ( ( ( *c3 - MLIB_S16_MIN ) & mask ) >> 8 ) ];                    \
                                                                           \
        c0 += 4;                                                           \
        c1 += 4;                                                           \
        c2 += 4;                                                           \
        c3 += 4;                                                           \
      }                                                                    \
      break;                                                               \
    }                                                                      \
    /* Other cases may not be considered as the table size will be more    \
       than 2^32 */                                                        \
  }                                                                        \
}

/***************************************************************/
#define BINARY_TREE_SEARCH_RIGHT( POSITION, COLOR_MAX, SHIFT )  \
{                                                               \
  if( ( distance >= ( ( ( position[ POSITION ] + current_size - \
    c[ POSITION ] ) * ( position[ POSITION ] + current_size -   \
    c[ POSITION ] ) ) >> SHIFT ) ) &&                           \
    ( position[ POSITION ] + current_size != COLOR_MAX ) )      \
    continue_up = 1;                                            \
}

/***************************************************************/
#define BINARY_TREE_EXPLORE_RIGHT_3( POSITION, COLOR_MAX, IMAGE_TYPE,    \
  FIRST_NEIBOUR, SECOND_NEIBOUR, SUBSTRACTION, SHIFT )                   \
{                                                                        \
  if( distance >= ( ( ( position[ POSITION ] + current_size -            \
    c[ POSITION ] ) * ( position[ POSITION ] +                           \
      current_size - c[ POSITION ] ) ) >> SHIFT ) )                      \
  {                                                                      \
    if( distance < ( ( ( COLOR_MAX - c[ POSITION ] ) *                   \
      ( COLOR_MAX - c[ POSITION ] ) ) >> SHIFT ) )                       \
    {                                                                    \
      if( distance < ( ( ( position[ POSITION ] +                        \
        current_size * 2 - c[ POSITION ] ) *                             \
        ( position[ POSITION ] + current_size * 2 -                      \
          c[ POSITION ] ) ) >> SHIFT ) )                                 \
      {                                                                  \
        /* Check only a part of quadrant */                              \
        mlib_s32 qq = q ^ ( 1 << POSITION );                             \
                                                                         \
        check_neibours[ FIRST_NEIBOUR ] += 1;                            \
        check_neibours[ SECOND_NEIBOUR ] += 1;                           \
        check_corner += 1;                                               \
        if( node->tag & ( 1 << qq ) )                                    \
        {                                                                \
          /* Here is another color cell.                                 \
             Check the distance */                                       \
          mlib_s32 new_found_color =                                     \
            node->contents.index[ qq ];                                  \
          mlib_u32 newdistance = FIND_DISTANCE_3( c[ 0 ],                \
            p[ 0 ][ new_found_color ] - SUBSTRACTION, c[ 1 ],            \
            p[ 1 ][ new_found_color ] - SUBSTRACTION, c[ 2 ],            \
            p[ 2 ][ new_found_color ] - SUBSTRACTION, SHIFT );           \
                                                                         \
          if( newdistance < distance )                                   \
          {                                                              \
            found_color = new_found_color;                               \
            distance = newdistance;                                      \
          }                                                              \
        }                                                                \
        else if( node->contents.quadrants[ qq ] )                        \
          /* Only a part of quadrant needs checking */                   \
          distance =                                                     \
            mlib_search_quadrant_part_to_left_##IMAGE_TYPE##_3(          \
              node->contents.quadrants[ qq ],                            \
              distance, &found_color, c, p,                              \
              position[ POSITION ] + current_size, pass - 1, POSITION ); \
      }                                                                  \
      else /* Check whole quadrant */                                    \
      {                                                                  \
        mlib_s32 qq = q ^ ( 1 << POSITION );                             \
                                                                         \
        check_neibours[ FIRST_NEIBOUR ] += 2;                            \
        check_neibours[ SECOND_NEIBOUR ] += 2;                           \
        check_corner += 2;                                               \
        continue_up = 1;                                                 \
        if( node->tag & ( 1 << qq ) )                                    \
        {                                                                \
          /* Here is another color cell.                                 \
             Check the distance */                                       \
          mlib_s32 new_found_color =                                     \
            node->contents.index[ qq ];                                  \
          mlib_u32 newdistance = FIND_DISTANCE_3( c[ 0 ],                \
            p[ 0 ][ new_found_color ] - SUBSTRACTION, c[ 1 ],            \
            p[ 1 ][ new_found_color ] - SUBSTRACTION, c[ 2 ],            \
            p[ 2 ][ new_found_color ] - SUBSTRACTION, SHIFT );           \
                                                                         \
          if( newdistance < distance )                                   \
          {                                                              \
            found_color = new_found_color;                               \
            distance = newdistance;                                      \
          }                                                              \
        }                                                                \
        else if( node->contents.quadrants[ qq ] )                        \
          /* Here is a full node. Just explore it */                     \
          distance = mlib_search_quadrant_##IMAGE_TYPE##_3(              \
            node->contents.quadrants[ qq ],                              \
            distance, &found_color, c[ 0 ], c[ 1 ], c[ 2 ], p );         \
      }                                                                  \
    }                                                                    \
    else /* Cell is on the edge of the space */                          \
    {                                                                    \
      if( position[ POSITION ] + current_size * 2 ==                     \
        COLOR_MAX )                                                      \
      {                                                                  \
        /* Check only a part of quadrant */                              \
        mlib_s32 qq = q ^ ( 1 << POSITION );                             \
                                                                         \
        check_neibours[ FIRST_NEIBOUR ] += 1;                            \
        check_neibours[ SECOND_NEIBOUR ] += 1;                           \
        check_corner += 1;                                               \
        if( node->tag & ( 1 << qq ) )                                    \
        {                                                                \
          /* Here is another color cell.                                 \
             Check the distance */                                       \
          mlib_s32 new_found_color =                                     \
            node->contents.index[ qq ];                                  \
          mlib_u32 newdistance = FIND_DISTANCE_3( c[ 0 ],                \
            p[ 0 ][ new_found_color ] - SUBSTRACTION, c[ 1 ],            \
            p[ 1 ][ new_found_color ] - SUBSTRACTION, c[ 2 ],            \
            p[ 2 ][ new_found_color ] - SUBSTRACTION, SHIFT );           \
                                                                         \
          if( newdistance < distance )                                   \
          {                                                              \
            found_color = new_found_color;                               \
            distance = newdistance;                                      \
          }                                                              \
        }                                                                \
        else if( node->contents.quadrants[ qq ] )                        \
          /* Only a part of quadrant needs checking */                   \
          distance =                                                     \
            mlib_search_quadrant_part_to_left_##IMAGE_TYPE##_3(          \
              node->contents.quadrants[ qq ],                            \
              distance, &found_color, c, p,                              \
              position[ POSITION ] + current_size,                       \
              pass - 1, POSITION );                                      \
      }                                                                  \
      else /* Check whole quadrant */                                    \
      {                                                                  \
        mlib_s32 qq = q ^ ( 1 << POSITION );                             \
                                                                         \
        check_neibours[ FIRST_NEIBOUR ] += 2;                            \
        check_neibours[ SECOND_NEIBOUR ] += 2;                           \
        check_corner += 2;                                               \
        continue_up = 1;                                                 \
        if( node->tag & ( 1 << qq ) )                                    \
        {                                                                \
          /* Here is another color cell.                                 \
             Check the distance */                                       \
          mlib_s32 new_found_color =                                     \
            node->contents.index[ qq ];                                  \
          mlib_u32 newdistance = FIND_DISTANCE_3( c[ 0 ],                \
            p[ 0 ][ new_found_color ] - SUBSTRACTION, c[ 1 ],            \
            p[ 1 ][ new_found_color ] - SUBSTRACTION, c[ 2 ],            \
            p[ 2 ][ new_found_color ] - SUBSTRACTION, SHIFT );           \
                                                                         \
          if( newdistance < distance )                                   \
          {                                                              \
            found_color = new_found_color;                               \
            distance = newdistance;                                      \
          }                                                              \
        }                                                                \
        else if( node->contents.quadrants[ qq ] )                        \
          /* Here is a full node. Just explore it */                     \
          distance = mlib_search_quadrant_##IMAGE_TYPE##_3(              \
            node->contents.quadrants[ qq ],                              \
            distance, &found_color, c[ 0 ], c[ 1 ], c[ 2 ], p );         \
      }                                                                  \
    }                                                                    \
  }                                                                      \
}

/***************************************************************/
#define BINARY_TREE_EXPLORE_RIGHT_4( POSITION, COLOR_MAX, IMAGE_TYPE,    \
  FIRST_NEIBOUR, SECOND_NEIBOUR, THIRD_NEIBOUR, SUBSTRACTION, SHIFT )    \
{                                                                        \
  if( distance >= ( ( ( position[ POSITION ] + current_size -            \
    c[ POSITION ] ) * ( position[ POSITION ] +                           \
      current_size - c[ POSITION ] ) ) >> SHIFT ) )                      \
  {                                                                      \
    if( distance < ( ( ( COLOR_MAX - c[ POSITION ] ) *                   \
      ( COLOR_MAX - c[ POSITION ] ) ) >> SHIFT ) )                       \
    {                                                                    \
      if( distance < ( ( ( position[ POSITION ] +                        \
        current_size * 2 - c[ POSITION ] ) *                             \
        ( position[ POSITION ] + current_size * 2 -                      \
          c[ POSITION ] ) ) >> SHIFT ) )                                 \
      {                                                                  \
        /* Check only a part of quadrant */                              \
        mlib_s32 qq = q ^ ( 1 << POSITION );                             \
                                                                         \
        check_neibours[ FIRST_NEIBOUR ] += 1;                            \
        check_neibours[ SECOND_NEIBOUR ] += 1;                           \
        check_neibours[ THIRD_NEIBOUR ] += 1;                            \
        if( node->tag & ( 1 << qq ) )                                    \
        {                                                                \
          /* Here is another color cell.                                 \
             Check the distance */                                       \
          mlib_s32 new_found_color =                                     \
            node->contents.index[ qq ];                                  \
          mlib_u32 newdistance = FIND_DISTANCE_4( c[ 0 ],                \
            p[ 0 ][ new_found_color ] - SUBSTRACTION, c[ 1 ],            \
            p[ 1 ][ new_found_color ] - SUBSTRACTION, c[ 2 ],            \
            p[ 2 ][ new_found_color ] - SUBSTRACTION, c[ 3 ],            \
            p[ 3 ][ new_found_color ] - SUBSTRACTION, SHIFT );           \
                                                                         \
          if( newdistance < distance )                                   \
          {                                                              \
            found_color = new_found_color;                               \
            distance = newdistance;                                      \
          }                                                              \
        }                                                                \
        else if( node->contents.quadrants[ qq ] )                        \
          /* Only a part of quadrant needs checking */                   \
          distance =                                                     \
            mlib_search_quadrant_part_to_left_##IMAGE_TYPE##_4(          \
              node->contents.quadrants[ qq ],                            \
              distance, &found_color, c, p,                              \
              position[ POSITION ] + current_size, pass - 1, POSITION ); \
      }                                                                  \
      else /* Check whole quadrant */                                    \
      {                                                                  \
        mlib_s32 qq = q ^ ( 1 << POSITION );                             \
                                                                         \
        check_neibours[ FIRST_NEIBOUR ] += 2;                            \
        check_neibours[ SECOND_NEIBOUR ] += 2;                           \
        check_neibours[ THIRD_NEIBOUR ] += 2;                            \
        continue_up = 1;                                                 \
        if( node->tag & ( 1 << qq ) )                                    \
        {                                                                \
          /* Here is another color cell.                                 \
             Check the distance */                                       \
          mlib_s32 new_found_color =                                     \
            node->contents.index[ qq ];                                  \
          mlib_u32 newdistance = FIND_DISTANCE_4( c[ 0 ],                \
            p[ 0 ][ new_found_color ] - SUBSTRACTION, c[ 1 ],            \
            p[ 1 ][ new_found_color ] - SUBSTRACTION, c[ 2 ],            \
            p[ 2 ][ new_found_color ] - SUBSTRACTION, c[ 3 ],            \
            p[ 3 ][ new_found_color ] - SUBSTRACTION, SHIFT );           \
                                                                         \
          if( newdistance < distance )                                   \
          {                                                              \
            found_color = new_found_color;                               \
            distance = newdistance;                                      \
          }                                                              \
        }                                                                \
        else if( node->contents.quadrants[ qq ] )                        \
          /* Here is a full node. Just explore it */                     \
          distance = mlib_search_quadrant_##IMAGE_TYPE##_4(              \
            node->contents.quadrants[ qq ],                              \
            distance, &found_color, c[ 0 ], c[ 1 ], c[ 2 ], c[ 3 ], p ); \
      }                                                                  \
    }                                                                    \
    else /* Cell is on the edge of the space */                          \
    {                                                                    \
      if( position[ POSITION ] + current_size * 2 ==                     \
        COLOR_MAX )                                                      \
      {                                                                  \
        /* Check only a part of quadrant */                              \
        mlib_s32 qq = q ^ ( 1 << POSITION );                             \
                                                                         \
        check_neibours[ FIRST_NEIBOUR ] += 1;                            \
        check_neibours[ SECOND_NEIBOUR ] += 1;                           \
        check_neibours[ THIRD_NEIBOUR ] += 1;                            \
        if( node->tag & ( 1 << qq ) )                                    \
        {                                                                \
          /* Here is another color cell.                                 \
             Check the distance */                                       \
          mlib_s32 new_found_color =                                     \
            node->contents.index[ qq ];                                  \
          mlib_u32 newdistance = FIND_DISTANCE_4( c[ 0 ],                \
            p[ 0 ][ new_found_color ] - SUBSTRACTION, c[ 1 ],            \
            p[ 1 ][ new_found_color ] - SUBSTRACTION, c[ 2 ],            \
            p[ 2 ][ new_found_color ] - SUBSTRACTION, c[ 3 ],            \
            p[ 3 ][ new_found_color ] - SUBSTRACTION, SHIFT );           \
                                                                         \
          if( newdistance < distance )                                   \
          {                                                              \
            found_color = new_found_color;                               \
            distance = newdistance;                                      \
          }                                                              \
        }                                                                \
        else if( node->contents.quadrants[ qq ] )                        \
          /* Only a part of quadrant needs checking */                   \
          distance =                                                     \
            mlib_search_quadrant_part_to_left_##IMAGE_TYPE##_4(          \
              node->contents.quadrants[ qq ],                            \
              distance, &found_color, c, p,                              \
              position[ POSITION ] + current_size,                       \
              pass - 1, POSITION );                                      \
      }                                                                  \
      else /* Check whole quadrant */                                    \
      {                                                                  \
        mlib_s32 qq = q ^ ( 1 << POSITION );                             \
                                                                         \
        check_neibours[ FIRST_NEIBOUR ] += 2;                            \
        check_neibours[ SECOND_NEIBOUR ] += 2;                           \
        check_neibours[ THIRD_NEIBOUR ] += 2;                            \
        continue_up = 1;                                                 \
        if( node->tag & ( 1 << qq ) )                                    \
        {                                                                \
          /* Here is another color cell.                                 \
             Check the distance */                                       \
          mlib_s32 new_found_color =                                     \
            node->contents.index[ qq ];                                  \
          mlib_u32 newdistance = FIND_DISTANCE_4( c[ 0 ],                \
            p[ 0 ][ new_found_color ] - SUBSTRACTION, c[ 1 ],            \
            p[ 1 ][ new_found_color ] - SUBSTRACTION, c[ 2 ],            \
            p[ 2 ][ new_found_color ] - SUBSTRACTION, c[ 3 ],            \
            p[ 3 ][ new_found_color ] - SUBSTRACTION, SHIFT );           \
                                                                         \
          if( newdistance < distance )                                   \
          {                                                              \
            found_color = new_found_color;                               \
            distance = newdistance;                                      \
          }                                                              \
        }                                                                \
        else if( node->contents.quadrants[ qq ] )                        \
          /* Here is a full node. Just explore it */                     \
          distance = mlib_search_quadrant_##IMAGE_TYPE##_4(              \
            node->contents.quadrants[ qq ],                              \
            distance, &found_color, c[ 0 ], c[ 1 ], c[ 2 ], c[ 3 ], p ); \
      }                                                                  \
    }                                                                    \
  }                                                                      \
}

/***************************************************************/
#define BINARY_TREE_SEARCH_LEFT( POSITION, SHIFT )                \
{                                                                 \
  if( ( distance > ( ( ( position[ POSITION ] - c[ POSITION ] ) * \
    ( position[ POSITION ] - c[ POSITION ] ) ) >> SHIFT ) )  &&   \
    position[ POSITION ] )                                        \
    continue_up = 1;                                              \
}

/***************************************************************/
#define BINARY_TREE_EXPLORE_LEFT_3( POSITION, IMAGE_TYPE,                \
  FIRST_NEIBOUR, SECOND_NEIBOUR, SUBSTRACTION, SHIFT )                   \
{                                                                        \
  if( distance >                                                         \
    ( ( ( c[ POSITION ] - position[ POSITION ] ) *                       \
    ( c[ POSITION ] - position[ POSITION ] ) ) >> SHIFT ) )              \
  {                                                                      \
    if( distance <= ( ( c[ POSITION ] * c[ POSITION ] ) >> SHIFT ) )     \
    {                                                                    \
      if( distance <= ( ( ( c[ POSITION ] + current_size -               \
        position[ POSITION ] ) *                                         \
        ( c[ POSITION ] + current_size -                                 \
          position[ POSITION ] ) ) >> SHIFT ) )                          \
      {                                                                  \
        mlib_s32 qq = q ^ ( 1 << POSITION );                             \
                                                                         \
        check_neibours[ FIRST_NEIBOUR ] += 1;                            \
        check_neibours[ SECOND_NEIBOUR ] += 1;                           \
        check_corner += 1;                                               \
        if( node->tag & ( 1 << qq ) )                                    \
        {                                                                \
          /* Here is another color cell.                                 \
             Check the distance */                                       \
          mlib_s32 new_found_color =                                     \
            node->contents.index[ qq ];                                  \
          mlib_u32 newdistance = FIND_DISTANCE_3( c[ 0 ],                \
            p[ 0 ][ new_found_color ] - SUBSTRACTION, c[ 1 ],            \
            p[ 1 ][ new_found_color ] - SUBSTRACTION, c[ 2 ],            \
            p[ 2 ][ new_found_color ] - SUBSTRACTION, SHIFT );           \
                                                                         \
          if( newdistance < distance )                                   \
          {                                                              \
            found_color = new_found_color;                               \
            distance = newdistance;                                      \
          }                                                              \
        }                                                                \
        else if( node->contents.quadrants[ qq ] )                        \
          /* Only a part of quadrant needs checking */                   \
          distance =                                                     \
            mlib_search_quadrant_part_to_right_##IMAGE_TYPE##_3(         \
              node->contents.quadrants[ qq ],                            \
              distance, &found_color, c, p,                              \
              position[ POSITION ] - current_size, pass - 1, POSITION ); \
      }                                                                  \
      else /* Check whole quadrant */                                    \
      {                                                                  \
        mlib_s32 qq = q ^ ( 1 << POSITION );                             \
                                                                         \
        check_neibours[ FIRST_NEIBOUR ] += 2;                            \
        check_neibours[ SECOND_NEIBOUR ] += 2;                           \
        check_corner += 2;                                               \
        continue_up = 1;                                                 \
        if( node->tag & ( 1 << qq ) )                                    \
        {                                                                \
          /* Here is another color cell.                                 \
             Check the distance */                                       \
          mlib_s32 new_found_color =                                     \
            node->contents.index[ qq ];                                  \
          mlib_u32 newdistance = FIND_DISTANCE_3( c[ 0 ],                \
            p[ 0 ][ new_found_color ] - SUBSTRACTION, c[ 1 ],            \
            p[ 1 ][ new_found_color ] - SUBSTRACTION, c[ 2 ],            \
            p[ 2 ][ new_found_color ] - SUBSTRACTION, SHIFT );           \
                                                                         \
          if( newdistance < distance )                                   \
          {                                                              \
            found_color = new_found_color;                               \
            distance = newdistance;                                      \
          }                                                              \
        }                                                                \
        else if( node->contents.quadrants[ qq ] )                        \
          /* Here is a full node. Just explore it */                     \
          distance = mlib_search_quadrant_##IMAGE_TYPE##_3(              \
            node->contents.quadrants[ qq ],                              \
            distance, &found_color, c[ 0 ], c[ 1 ], c[ 2 ], p );         \
      }                                                                  \
    }                                                                    \
    else                                                                 \
    {                                                                    \
      if( !( position[ POSITION ] - current_size ) )                     \
      {                                                                  \
        mlib_s32 qq = q ^ ( 1 << POSITION );                             \
                                                                         \
        check_neibours[ FIRST_NEIBOUR ] += 1;                            \
        check_neibours[ SECOND_NEIBOUR ] += 1;                           \
        check_corner += 1;                                               \
        if( node->tag & ( 1 << qq ) )                                    \
        {                                                                \
          /* Here is another color cell.                                 \
             Check the distance */                                       \
          mlib_s32 new_found_color =                                     \
            node->contents.index[ qq ];                                  \
          mlib_u32 newdistance = FIND_DISTANCE_3( c[ 0 ],                \
            p[ 0 ][ new_found_color ] - SUBSTRACTION, c[ 1 ],            \
            p[ 1 ][ new_found_color ] - SUBSTRACTION, c[ 2 ],            \
            p[ 2 ][ new_found_color ] - SUBSTRACTION, SHIFT );           \
                                                                         \
          if( newdistance < distance )                                   \
          {                                                              \
            found_color = new_found_color;                               \
            distance = newdistance;                                      \
          }                                                              \
        }                                                                \
        else if( node->contents.quadrants[ qq ] )                        \
          /* Only a part of quadrant needs checking */                   \
          distance =                                                     \
            mlib_search_quadrant_part_to_right_##IMAGE_TYPE##_3(         \
              node->contents.quadrants[ qq ],                            \
              distance, &found_color, c, p,                              \
              position[ POSITION ] - current_size, pass - 1, POSITION ); \
      }                                                                  \
      else                                                               \
      {                                                                  \
        mlib_s32 qq = q ^ ( 1 << POSITION );                             \
                                                                         \
        check_neibours[ FIRST_NEIBOUR ] += 2;                            \
        check_neibours[ SECOND_NEIBOUR ] += 2;                           \
        check_corner += 2;                                               \
        continue_up = 1;                                                 \
        if( node->tag & ( 1 << qq ) )                                    \
        {                                                                \
          /* Here is another color cell.                                 \
             Check the distance */                                       \
          mlib_s32 new_found_color =                                     \
            node->contents.index[ qq ];                                  \
          mlib_u32 newdistance = FIND_DISTANCE_3( c[ 0 ],                \
            p[ 0 ][ new_found_color ] - SUBSTRACTION, c[ 1 ],            \
            p[ 1 ][ new_found_color ] - SUBSTRACTION, c[ 2 ],            \
            p[ 2 ][ new_found_color ] - SUBSTRACTION, SHIFT );           \
                                                                         \
          if( newdistance < distance )                                   \
          {                                                              \
            found_color = new_found_color;                               \
            distance = newdistance;                                      \
          }                                                              \
        }                                                                \
        else if( node->contents.quadrants[ qq ] )                        \
          /* Here is a full node. Just explore it */                     \
          distance = mlib_search_quadrant_##IMAGE_TYPE##_3(              \
            node->contents.quadrants[ qq ],                              \
            distance, &found_color, c[ 0 ], c[ 1 ], c[ 2 ], p );         \
      }                                                                  \
    }                                                                    \
  }                                                                      \
}

/***************************************************************/
#define BINARY_TREE_EXPLORE_LEFT_4( POSITION, IMAGE_TYPE,                \
  FIRST_NEIBOUR, SECOND_NEIBOUR, THIRD_NEIBOUR, SUBSTRACTION, SHIFT )    \
{                                                                        \
  if( distance >                                                         \
    ( ( ( c[ POSITION ] - position[ POSITION ] ) *                       \
    ( c[ POSITION ] - position[ POSITION ] ) ) >> SHIFT ) )              \
  {                                                                      \
    if( distance <= ( ( c[ POSITION ] * c[ POSITION ] ) >> SHIFT ) )     \
    {                                                                    \
      if( distance <= ( ( ( c[ POSITION ] + current_size -               \
        position[ POSITION ] ) *                                         \
        ( c[ POSITION ] + current_size -                                 \
          position[ POSITION ] ) ) >> SHIFT ) )                          \
      {                                                                  \
        mlib_s32 qq = q ^ ( 1 << POSITION );                             \
                                                                         \
        check_neibours[ FIRST_NEIBOUR ] += 1;                            \
        check_neibours[ SECOND_NEIBOUR ] += 1;                           \
        check_neibours[ THIRD_NEIBOUR ] += 1;                            \
        if( node->tag & ( 1 << qq ) )                                    \
        {                                                                \
          /* Here is another color cell.                                 \
             Check the distance */                                       \
          mlib_s32 new_found_color =                                     \
            node->contents.index[ qq ];                                  \
          mlib_u32 newdistance = FIND_DISTANCE_4( c[ 0 ],                \
            p[ 0 ][ new_found_color ] - SUBSTRACTION, c[ 1 ],            \
            p[ 1 ][ new_found_color ] - SUBSTRACTION, c[ 2 ],            \
            p[ 2 ][ new_found_color ] - SUBSTRACTION, c[ 3 ],            \
            p[ 3 ][ new_found_color ] - SUBSTRACTION, SHIFT );           \
                                                                         \
          if( newdistance < distance )                                   \
          {                                                              \
            found_color = new_found_color;                               \
            distance = newdistance;                                      \
          }                                                              \
        }                                                                \
        else if( node->contents.quadrants[ qq ] )                        \
          /* Only a part of quadrant needs checking */                   \
          distance =                                                     \
            mlib_search_quadrant_part_to_right_##IMAGE_TYPE##_4(         \
              node->contents.quadrants[ qq ],                            \
              distance, &found_color, c, p,                              \
              position[ POSITION ] - current_size, pass - 1, POSITION ); \
      }                                                                  \
      else /* Check whole quadrant */                                    \
      {                                                                  \
        mlib_s32 qq = q ^ ( 1 << POSITION );                             \
                                                                         \
        check_neibours[ FIRST_NEIBOUR ] += 2;                            \
        check_neibours[ SECOND_NEIBOUR ] += 2;                           \
        check_neibours[ THIRD_NEIBOUR ] += 2;                            \
        continue_up = 1;                                                 \
        if( node->tag & ( 1 << qq ) )                                    \
        {                                                                \
          /* Here is another color cell.                                 \
             Check the distance */                                       \
          mlib_s32 new_found_color =                                     \
            node->contents.index[ qq ];                                  \
          mlib_u32 newdistance = FIND_DISTANCE_4( c[ 0 ],                \
            p[ 0 ][ new_found_color ] - SUBSTRACTION, c[ 1 ],            \
            p[ 1 ][ new_found_color ] - SUBSTRACTION, c[ 2 ],            \
            p[ 2 ][ new_found_color ] - SUBSTRACTION, c[ 3 ],            \
            p[ 3 ][ new_found_color ] - SUBSTRACTION, SHIFT );           \
                                                                         \
          if( newdistance < distance )                                   \
          {                                                              \
            found_color = new_found_color;                               \
            distance = newdistance;                                      \
          }                                                              \
        }                                                                \
        else if( node->contents.quadrants[ qq ] )                        \
          /* Here is a full node. Just explore it */                     \
          distance = mlib_search_quadrant_##IMAGE_TYPE##_4(              \
            node->contents.quadrants[ qq ],                              \
            distance, &found_color, c[ 0 ], c[ 1 ], c[ 2 ], c[ 3 ], p ); \
      }                                                                  \
    }                                                                    \
    else                                                                 \
    {                                                                    \
      if( !( position[ POSITION ] - current_size ) )                     \
      {                                                                  \
        mlib_s32 qq = q ^ ( 1 << POSITION );                             \
                                                                         \
        check_neibours[ FIRST_NEIBOUR ] += 1;                            \
        check_neibours[ SECOND_NEIBOUR ] += 1;                           \
        check_neibours[ THIRD_NEIBOUR ] += 1;                            \
        if( node->tag & ( 1 << qq ) )                                    \
        {                                                                \
          /* Here is another color cell.                                 \
             Check the distance */                                       \
          mlib_s32 new_found_color =                                     \
            node->contents.index[ qq ];                                  \
          mlib_u32 newdistance = FIND_DISTANCE_4( c[ 0 ],                \
            p[ 0 ][ new_found_color ] - SUBSTRACTION, c[ 1 ],            \
            p[ 1 ][ new_found_color ] - SUBSTRACTION, c[ 2 ],            \
            p[ 2 ][ new_found_color ] - SUBSTRACTION, c[ 3 ],            \
            p[ 3 ][ new_found_color ] - SUBSTRACTION, SHIFT );           \
                                                                         \
          if( newdistance < distance )                                   \
          {                                                              \
            found_color = new_found_color;                               \
            distance = newdistance;                                      \
          }                                                              \
        }                                                                \
        else if( node->contents.quadrants[ qq ] )                        \
          /* Only a part of quadrant needs checking */                   \
          distance =                                                     \
            mlib_search_quadrant_part_to_right_##IMAGE_TYPE##_4(         \
              node->contents.quadrants[ qq ],                            \
              distance, &found_color, c, p,                              \
              position[ POSITION ] - current_size, pass - 1, POSITION ); \
      }                                                                  \
      else                                                               \
      {                                                                  \
        mlib_s32 qq = q ^ ( 1 << POSITION );                             \
                                                                         \
        check_neibours[ FIRST_NEIBOUR ] += 2;                            \
        check_neibours[ SECOND_NEIBOUR ] += 2;                           \
        check_neibours[ THIRD_NEIBOUR ] += 2;                            \
        continue_up = 1;                                                 \
        if( node->tag & ( 1 << qq ) )                                    \
        {                                                                \
          /* Here is another color cell.                                 \
             Check the distance */                                       \
          mlib_s32 new_found_color =                                     \
            node->contents.index[ qq ];                                  \
          mlib_u32 newdistance = FIND_DISTANCE_4( c[ 0 ],                \
            p[ 0 ][ new_found_color ] - SUBSTRACTION, c[ 1 ],            \
            p[ 1 ][ new_found_color ] - SUBSTRACTION, c[ 2 ],            \
            p[ 2 ][ new_found_color ] - SUBSTRACTION, c[ 3 ],            \
            p[ 3 ][ new_found_color ] - SUBSTRACTION, SHIFT );           \
                                                                         \
          if( newdistance < distance )                                   \
          {                                                              \
            found_color = new_found_color;                               \
            distance = newdistance;                                      \
          }                                                              \
        }                                                                \
        else if( node->contents.quadrants[ qq ] )                        \
          /* Here is a full node. Just explore it */                     \
          distance = mlib_search_quadrant_##IMAGE_TYPE##_4(              \
            node->contents.quadrants[ qq ],                              \
            distance, &found_color, c[ 0 ], c[ 1 ], c[ 2 ], c[ 3 ], p ); \
      }                                                                  \
    }                                                                    \
  }                                                                      \
}

/***************************************************************/
#define CHECK_QUADRANT_U8_3( qq )                               \
{                                                               \
  if( node->tag & ( 1 << qq ) )                                 \
  {                                                             \
    /* Here is another color cell. Check the distance */        \
    mlib_s32 new_found_color = node->contents.index[ qq ];      \
    mlib_u32 newdistance = FIND_DISTANCE_3( c[ 0 ],             \
      p[ 0 ][ new_found_color ], c[ 1 ],                        \
      p[ 1 ][ new_found_color ], c[ 2 ],                        \
      p[ 2 ][ new_found_color ], 0 );                           \
                                                                \
    if( newdistance < distance )                                \
    {                                                           \
      found_color = new_found_color;                            \
      distance = newdistance;                                   \
    }                                                           \
  }                                                             \
  else if( node->contents.quadrants[ qq ] )                     \
    /* Here is a full node. Just explore it all */              \
    distance = mlib_search_quadrant_U8_3(                       \
      node->contents.quadrants[ qq ], distance, &found_color,   \
      c[ 0 ], c[ 1 ], c[ 2 ], p );                              \
/* Else there is just an empty cell */                          \
}

/***************************************************************/
#define CHECK_QUADRANT_S16_3( qq )                              \
{                                                               \
  if( node->tag & ( 1 << qq ) )                                 \
  {                                                             \
    /* Here is another color cell. Check the distance */        \
    mlib_s32 new_found_color = node->contents.index[ qq ];      \
    mlib_u32 palc0, palc1, palc2, newdistance;                  \
                                                                \
    palc0 = p[ 0 ][ new_found_color ] - MLIB_S16_MIN;           \
    palc1 = p[ 1 ][ new_found_color ] - MLIB_S16_MIN;           \
    palc2 = p[ 2 ][ new_found_color ] - MLIB_S16_MIN;           \
                                                                \
    newdistance = FIND_DISTANCE_3( c[ 0 ], palc0,               \
      c[ 1 ], palc1,                                            \
      c[ 2 ], palc2, 2 );                                       \
                                                                \
    if( newdistance < distance )                                \
    {                                                           \
      found_color = new_found_color;                            \
      distance = newdistance;                                   \
    }                                                           \
  }                                                             \
  else if( node->contents.quadrants[ qq ] )                     \
    /* Here is a full node. Just explore it all */              \
    distance = mlib_search_quadrant_S16_3(                      \
      node->contents.quadrants[ qq ], distance, &found_color,   \
      c[ 0 ], c[ 1 ], c[ 2 ], p );                              \
/* Else there is just an empty cell */                          \
}

/***************************************************************/
#define BINARY_TREE_SEARCH_3( SOURCE_IMAGE, POINTER_TYPE, BITS,              \
  COLOR_MAX, SUBTRACTION, POINTER_SHIFT, STEP, SHIFT )                       \
{                                                                            \
  const POINTER_TYPE *channels[ 3 ], *p[ 3 ];                                \
  mlib_u32 c[ 3 ];                                                           \
  mlib_s32 j;                                                                \
                                                                             \
  p[ 0 ] = s->lut[ 0 ];                                                      \
  p[ 1 ] = s->lut[ 1 ];                                                      \
  p[ 2 ] = s->lut[ 2 ];                                                      \
  channels[ 0 ] = src + POINTER_SHIFT;                                       \
  channels[ 1 ] = src + 1 + POINTER_SHIFT;                                   \
  channels[ 2 ] = src + 2 + POINTER_SHIFT;                                   \
                                                                             \
  for( j = 0; j < length; j++ )                                              \
  {                                                                          \
    mlib_s32 pass = BITS - 1;                                                \
    mlib_u32 position[ 3 ] = { 0, 0, 0 };                                    \
    mlib_s32 we_found_it = 0;                                                \
    struct lut_node_3 *node = s->table;                                      \
    /* Stack pointer pointers to the first free element of stack. */         \
    /* The node we are in is in the `node' */                                \
    struct                                                                   \
    {                                                                        \
      struct lut_node_3 *node;                                               \
      mlib_s32 q;                                                            \
    } stack[ BITS ];                                                         \
    mlib_s32 stack_pointer = 0;                                              \
                                                                             \
    c[ 0 ] = *channels[ 0 ] - SUBTRACTION;                                   \
    c[ 1 ] = *channels[ 1 ] - SUBTRACTION;                                   \
    c[ 2 ] = *channels[ 2 ] - SUBTRACTION;                                   \
                                                                             \
    do                                                                       \
    {                                                                        \
      mlib_s32 q;                                                            \
      mlib_u32 current_size = 1 << pass;                                     \
                                                                             \
      q = ( ( c[ 0 ] >> pass ) & 1 ) |                                       \
        ( ( ( c[ 1 ] << 1 ) >> pass ) & 2 ) |                                \
        ( ( ( c[ 2 ] << 2 ) >> pass ) & 4 );                                 \
                                                                             \
      position[ 0 ] |= c[ 0 ] & current_size;                                \
      position[ 1 ] |= c[ 1 ] & current_size;                                \
      position[ 2 ] |= c[ 2 ] & current_size;                                \
                                                                             \
      if( node->tag & ( 1 << q ) )                                           \
      {                                                                      \
        /*                                                                   \
          Here is a cell with one color. We need to be sure it's             \
          the one that is the closest to our color                           \
        */                                                                   \
        mlib_s32 palindex = node->contents.index[ q ];                       \
        mlib_u32 palc[ 3 ];                                                  \
        mlib_s32 identical;                                                  \
                                                                             \
        palc[ 0 ] = p[ 0 ][ palindex ] - SUBTRACTION;                        \
        palc[ 1 ] = p[ 1 ][ palindex ] - SUBTRACTION;                        \
        palc[ 2 ] = p[ 2 ][ palindex ] - SUBTRACTION;                        \
                                                                             \
        identical = ( palc[ 0 ] - c[ 0 ] ) | ( palc[ 1 ] - c[ 1 ] ) |        \
          ( palc[ 2 ] - c[ 2 ] );                                            \
                                                                             \
        if( !identical || BITS - pass == bits )                              \
        {                                                                    \
          /* Oh, here it is :) */                                            \
          dst[ j ] = palindex + s->offset;                                   \
          we_found_it = 1;                                                   \
        }                                                                    \
        else                                                                 \
        {                                                                    \
          mlib_u32 distance;                                                 \
          /* First index is the channel, second is the number of the         \
             side */                                                         \
          mlib_s32 found_color;                                              \
          mlib_s32 continue_up;                                              \
                                                                             \
          distance = FIND_DISTANCE_3( c[ 0 ], palc[ 0 ],                     \
            c[ 1 ], palc[ 1 ], c[ 2 ], palc[ 2 ], SHIFT );                   \
          found_color = palindex;                                            \
                                                                             \
          do                                                                 \
          {                                                                  \
            mlib_s32 check_corner;                                           \
                                                                             \
            /*                                                               \
              Neibours are enumerated in a cicle:                            \
              0 - between quadrants 0 and 1,                                 \
              1 - between quadrants 1 and 2 and                              \
              2 - between quadrants 2 and 0                                  \
            */                                                               \
            mlib_s32 check_neibours[ 3 ];                                    \
                                                                             \
            /*                                                               \
              Others are three two neibour quadrants                         \
                                                                             \
              Side number is [ <number of the coordinate >][ <the bit        \
              in the quadrant number of the corner, corresponding to         \
              this coordinate> ], e.g. 2 is 0..010b, so the sides it has     \
              near are:                                                      \
              [ 0 (coordinate number) ][ 0 (bit 0 in the number) ]           \
              [ 1 (coordinate number) ][ 1 (bit 1 in the number) ]           \
                                                                             \
              Now we can look in the three nearest quadrants. Do             \
              we really need it ? Check it.                                  \
            */                                                               \
                                                                             \
            check_corner = check_neibours[ 0 ] = check_neibours[ 1 ] =       \
              check_neibours[ 2 ] = 0;                                       \
            continue_up = 0;                                                 \
                                                                             \
            if( q & 1 )                                                      \
            {                                                                \
              BINARY_TREE_EXPLORE_LEFT_3( 0, SOURCE_IMAGE, 2, 0,             \
                SUBTRACTION, SHIFT );                                        \
            }                                                                \
            else                                                             \
            {                                                                \
              BINARY_TREE_EXPLORE_RIGHT_3( 0, COLOR_MAX, SOURCE_IMAGE, 2, 0, \
                SUBTRACTION, SHIFT );                                        \
            }                                                                \
                                                                             \
            if( q & 2 )                                                      \
            {                                                                \
              BINARY_TREE_EXPLORE_LEFT_3( 1, SOURCE_IMAGE, 0, 1,             \
                SUBTRACTION, SHIFT );                                        \
            }                                                                \
            else                                                             \
            {                                                                \
              BINARY_TREE_EXPLORE_RIGHT_3( 1, COLOR_MAX, SOURCE_IMAGE, 0, 1, \
                SUBTRACTION, SHIFT );                                        \
            }                                                                \
                                                                             \
            if( q & 4 )                                                      \
            {                                                                \
              BINARY_TREE_EXPLORE_LEFT_3( 2, SOURCE_IMAGE, 1, 2,             \
                SUBTRACTION, SHIFT );                                        \
            }                                                                \
            else                                                             \
            {                                                                \
              BINARY_TREE_EXPLORE_RIGHT_3( 2, COLOR_MAX, SOURCE_IMAGE, 1, 2, \
                SUBTRACTION, SHIFT );                                        \
            }                                                                \
                                                                             \
            if( check_neibours[ 0 ] >= 2 )                                   \
            {                                                                \
              mlib_s32 qq = q ^ 3;                                           \
              CHECK_QUADRANT_##SOURCE_IMAGE##_3( qq );                       \
            }                                                                \
                                                                             \
            if( check_neibours[ 1 ] >= 2 )                                   \
            {                                                                \
              mlib_s32 qq = q ^ 6;                                           \
              CHECK_QUADRANT_##SOURCE_IMAGE##_3( qq );                       \
            }                                                                \
                                                                             \
            if( check_neibours[ 2 ] >= 2 )                                   \
            {                                                                \
              mlib_s32 qq = q ^ 5;                                           \
              CHECK_QUADRANT_##SOURCE_IMAGE##_3( qq );                       \
            }                                                                \
                                                                             \
            if( check_corner >= 3 )                                          \
            {                                                                \
              mlib_s32 qq = q ^ 7;                                           \
              CHECK_QUADRANT_##SOURCE_IMAGE##_3( qq );                       \
            }                                                                \
                                                                             \
            if( q & 1 )                                                      \
            {                                                                \
              BINARY_TREE_SEARCH_RIGHT( 0, COLOR_MAX, SHIFT );               \
            }                                                                \
            else                                                             \
            {                                                                \
              BINARY_TREE_SEARCH_LEFT( 0, SHIFT );                           \
            }                                                                \
                                                                             \
            if( q & 2 )                                                      \
            {                                                                \
              BINARY_TREE_SEARCH_RIGHT( 1, COLOR_MAX, SHIFT );               \
            }                                                                \
            else                                                             \
            {                                                                \
              BINARY_TREE_SEARCH_LEFT( 1, SHIFT );                           \
            }                                                                \
                                                                             \
            if( q & 4 )                                                      \
            {                                                                \
              BINARY_TREE_SEARCH_RIGHT( 2, COLOR_MAX, SHIFT );               \
            }                                                                \
            else                                                             \
            {                                                                \
              BINARY_TREE_SEARCH_LEFT( 2, SHIFT );                           \
            }                                                                \
                                                                             \
            position[ 0 ] &= ~( c[ 0 ] & current_size );                     \
            position[ 1 ] &= ~( c[ 1 ] & current_size );                     \
            position[ 2 ] &= ~( c[ 2 ] & current_size );                     \
                                                                             \
            current_size <<= 1;                                              \
                                                                             \
            pass++;                                                          \
                                                                             \
            stack_pointer--;                                                 \
            q = stack[ stack_pointer ].q;                                    \
            node = stack[ stack_pointer ].node;                              \
          } while( continue_up );                                            \
                                                                             \
          dst[ j ] = found_color + s->offset;                                \
                                                                             \
          we_found_it = 1;                                                   \
        }                                                                    \
      }                                                                      \
      else if( node->contents.quadrants[ q ] )                               \
      {                                                                      \
        /* Descend one level */                                              \
        stack[ stack_pointer ].node = node;                                  \
        stack[ stack_pointer++ ].q = q;                                      \
        node = node->contents.quadrants[ q ];                                \
      }                                                                      \
      else                                                                   \
      {                                                                      \
        /* Found the empty quadrant. Look around */                          \
        mlib_u32 distance = MLIB_U32_MAX;                                    \
        mlib_s32 found_color;                                                \
        mlib_s32 continue_up;                                                \
                                                                             \
        /*                                                                   \
          As we had come to this level, it is warranted that there           \
          are other points on this level near the empty quadrant             \
        */                                                                   \
        do                                                                   \
        {                                                                    \
          mlib_s32 check_corner;                                             \
          mlib_s32 check_neibours[ 3 ];                                      \
                                                                             \
          check_corner = check_neibours[ 0 ] = check_neibours[ 1 ] =         \
            check_neibours[ 2 ] = 0;                                         \
          continue_up = 0;                                                   \
                                                                             \
          if( q & 1 )                                                        \
          {                                                                  \
            BINARY_TREE_EXPLORE_LEFT_3( 0, SOURCE_IMAGE, 2, 0,               \
              SUBTRACTION, SHIFT );                                          \
          }                                                                  \
          else                                                               \
          {                                                                  \
            BINARY_TREE_EXPLORE_RIGHT_3( 0, COLOR_MAX, SOURCE_IMAGE, 2, 0,   \
              SUBTRACTION, SHIFT );                                          \
          }                                                                  \
                                                                             \
          if( q & 2 )                                                        \
          {                                                                  \
            BINARY_TREE_EXPLORE_LEFT_3( 1, SOURCE_IMAGE, 0, 1,               \
              SUBTRACTION, SHIFT );                                          \
          }                                                                  \
          else                                                               \
          {                                                                  \
            BINARY_TREE_EXPLORE_RIGHT_3( 1, COLOR_MAX, SOURCE_IMAGE, 0, 1,   \
              SUBTRACTION, SHIFT );                                          \
          }                                                                  \
                                                                             \
          if( q & 4 )                                                        \
          {                                                                  \
            BINARY_TREE_EXPLORE_LEFT_3( 2, SOURCE_IMAGE, 1, 2,               \
              SUBTRACTION, SHIFT );                                          \
          }                                                                  \
          else                                                               \
          {                                                                  \
            BINARY_TREE_EXPLORE_RIGHT_3( 2, COLOR_MAX, SOURCE_IMAGE, 1, 2,   \
              SUBTRACTION, SHIFT );                                          \
          }                                                                  \
                                                                             \
          if( check_neibours[ 0 ] >= 2 )                                     \
          {                                                                  \
            mlib_s32 qq = q ^ 3;                                             \
            CHECK_QUADRANT_##SOURCE_IMAGE##_3( qq );                         \
          }                                                                  \
                                                                             \
          if( check_neibours[ 1 ] >= 2 )                                     \
          {                                                                  \
            mlib_s32 qq = q ^ 6;                                             \
            CHECK_QUADRANT_##SOURCE_IMAGE##_3( qq );                         \
          }                                                                  \
                                                                             \
          if( check_neibours[ 2 ] >= 2 )                                     \
          {                                                                  \
            mlib_s32 qq = q ^ 5;                                             \
            CHECK_QUADRANT_##SOURCE_IMAGE##_3( qq );                         \
          }                                                                  \
                                                                             \
          if( check_corner >= 3 )                                            \
          {                                                                  \
            mlib_s32 qq = q ^ 7;                                             \
            CHECK_QUADRANT_##SOURCE_IMAGE##_3( qq );                         \
          }                                                                  \
                                                                             \
          if( q & 1 )                                                        \
          {                                                                  \
            BINARY_TREE_SEARCH_RIGHT( 0, COLOR_MAX, SHIFT );                 \
          }                                                                  \
          else                                                               \
          {                                                                  \
            BINARY_TREE_SEARCH_LEFT( 0, SHIFT );                             \
          }                                                                  \
                                                                             \
          if( q & 2 )                                                        \
          {                                                                  \
            BINARY_TREE_SEARCH_RIGHT( 1, COLOR_MAX, SHIFT );                 \
          }                                                                  \
          else                                                               \
          {                                                                  \
            BINARY_TREE_SEARCH_LEFT( 1, SHIFT );                             \
          }                                                                  \
                                                                             \
          if( q & 4 )                                                        \
          {                                                                  \
            BINARY_TREE_SEARCH_RIGHT( 2, COLOR_MAX, SHIFT );                 \
          }                                                                  \
          else                                                               \
          {                                                                  \
            BINARY_TREE_SEARCH_LEFT( 2, SHIFT );                             \
          }                                                                  \
                                                                             \
          position[ 0 ] &= ~( c[ 0 ] & current_size );                       \
          position[ 1 ] &= ~( c[ 1 ] & current_size );                       \
          position[ 2 ] &= ~( c[ 2 ] & current_size );                       \
                                                                             \
          current_size <<= 1;                                                \
                                                                             \
          pass++;                                                            \
                                                                             \
          stack_pointer--;                                                   \
          q = stack[ stack_pointer ].q;                                      \
          node = stack[ stack_pointer ].node;                                \
        } while( continue_up );                                              \
                                                                             \
        dst[ j ] = found_color + s->offset;                                  \
        we_found_it = 1;                                                     \
      }                                                                      \
                                                                             \
      pass--;                                                                \
                                                                             \
    } while( !we_found_it );                                                 \
                                                                             \
    channels[ 0 ] += STEP;                                                   \
    channels[ 1 ] += STEP;                                                   \
    channels[ 2 ] += STEP;                                                   \
  }                                                                          \
}

/***************************************************************/
#define CHECK_QUADRANT_U8_4( qq )                               \
{                                                               \
  if( node->tag & ( 1 << qq ) )                                 \
  {                                                             \
    /* Here is another color cell. Check the distance */        \
    mlib_s32 new_found_color = node->contents.index[ qq ];      \
    mlib_u32 newdistance = FIND_DISTANCE_4( c[ 0 ],             \
      p[ 0 ][ new_found_color ], c[ 1 ],                        \
      p[ 1 ][ new_found_color ], c[ 2 ],                        \
      p[ 2 ][ new_found_color ], c[ 3 ],                        \
      p[ 3 ][ new_found_color ], 0 );                           \
                                                                \
    if( newdistance < distance )                                \
    {                                                           \
      found_color = new_found_color;                            \
      distance = newdistance;                                   \
    }                                                           \
  }                                                             \
  else if( node->contents.quadrants[ qq ] )                     \
    /* Here is a full node. Just explore it all */              \
    distance = mlib_search_quadrant_U8_4(                       \
      node->contents.quadrants[ qq ], distance, &found_color,   \
      c[ 0 ], c[ 1 ], c[ 2 ], c[ 3 ], p );                      \
/* Else there is just an empty cell */                          \
}

/***************************************************************/
#define CHECK_QUADRANT_S16_4( qq )                              \
{                                                               \
  if( node->tag & ( 1 << qq ) )                                 \
  {                                                             \
    /* Here is another color cell. Check the distance */        \
    mlib_s32 new_found_color = node->contents.index[ qq ];      \
    mlib_u32 palc0, palc1, palc2, palc3, newdistance;           \
                                                                \
    palc0 = p[ 0 ][ new_found_color ] - MLIB_S16_MIN;           \
    palc1 = p[ 1 ][ new_found_color ] - MLIB_S16_MIN;           \
    palc2 = p[ 2 ][ new_found_color ] - MLIB_S16_MIN;           \
    palc3 = p[ 3 ][ new_found_color ] - MLIB_S16_MIN;           \
                                                                \
    newdistance = FIND_DISTANCE_4( c[ 0 ], palc0,               \
      c[ 1 ], palc1,                                            \
      c[ 2 ], palc2,                                            \
      c[ 3 ], palc3, 2 );                                       \
                                                                \
    if( newdistance < distance )                                \
    {                                                           \
      found_color = new_found_color;                            \
      distance = newdistance;                                   \
    }                                                           \
  }                                                             \
  else if( node->contents.quadrants[ qq ] )                     \
    /* Here is a full node. Just explore it all */              \
    distance = mlib_search_quadrant_S16_4(                      \
      node->contents.quadrants[ qq ], distance, &found_color,   \
      c[ 0 ], c[ 1 ], c[ 2 ], c[ 3 ], p );                      \
/* Else there is just an empty cell */                          \
}

/***************************************************************/
#define BINARY_TREE_SEARCH_4( SOURCE_IMAGE, POINTER_TYPE, BITS,               \
  COLOR_MAX, SUBTRACTION, SHIFT )                                             \
{                                                                             \
  const POINTER_TYPE *channels[ 4 ], *p[ 4 ];                                 \
  mlib_u32 c[ 4 ];                                                            \
  mlib_s32 j;                                                                 \
                                                                              \
  p[ 0 ] = s->lut[ 0 ];                                                       \
  p[ 1 ] = s->lut[ 1 ];                                                       \
  p[ 2 ] = s->lut[ 2 ];                                                       \
  p[ 3 ] = s->lut[ 3 ];                                                       \
  channels[ 0 ] = src;                                                        \
  channels[ 1 ] = src + 1;                                                    \
  channels[ 2 ] = src + 2;                                                    \
  channels[ 3 ] = src + 3;                                                    \
                                                                              \
  for( j = 0; j < length; j++ )                                               \
  {                                                                           \
    mlib_s32 pass = BITS - 1;                                                 \
    mlib_u32 position[ 4 ] = { 0, 0, 0, 0 };                                  \
    mlib_s32 we_found_it = 0;                                                 \
    struct lut_node_4 *node = s->table;                                       \
    /* Stack pointer pointers to the first free element of stack. */          \
    /* The node we are in is in the `node' */                                 \
    struct                                                                    \
    {                                                                         \
      struct lut_node_4 *node;                                                \
      mlib_s32 q;                                                             \
    } stack[ BITS ];                                                          \
    mlib_s32 stack_pointer = 0;                                               \
                                                                              \
    c[ 0 ] = *channels[ 0 ] - SUBTRACTION;                                    \
    c[ 1 ] = *channels[ 1 ] - SUBTRACTION;                                    \
    c[ 2 ] = *channels[ 2 ] - SUBTRACTION;                                    \
    c[ 3 ] = *channels[ 3 ] - SUBTRACTION;                                    \
                                                                              \
    do                                                                        \
    {                                                                         \
      mlib_s32 q;                                                             \
      mlib_u32 current_size = 1 << pass;                                      \
                                                                              \
      q = ( ( c[ 0 ] >> pass ) & 1 ) |                                        \
        ( ( ( c[ 1 ] << 1 ) >> pass ) & 2 ) |                                 \
        ( ( ( c[ 2 ] << 2 ) >> pass ) & 4 ) |                                 \
        ( ( ( c[ 3 ] << 3 ) >> pass ) & 8 );                                  \
                                                                              \
      position[ 0 ] |= c[ 0 ] & current_size;                                 \
      position[ 1 ] |= c[ 1 ] & current_size;                                 \
      position[ 2 ] |= c[ 2 ] & current_size;                                 \
      position[ 3 ] |= c[ 3 ] & current_size;                                 \
                                                                              \
      if( node->tag & ( 1 << q ) )                                            \
      {                                                                       \
        /*                                                                    \
          Here is a cell with one color. We need to be sure it's              \
          the one that is the closest to our color                            \
        */                                                                    \
        mlib_s32 palindex = node->contents.index[ q ];                        \
        mlib_u32 palc[ 4 ];                                                   \
        mlib_s32 identical;                                                   \
                                                                              \
        palc[ 0 ] = p[ 0 ][ palindex ] - SUBTRACTION;                         \
        palc[ 1 ] = p[ 1 ][ palindex ] - SUBTRACTION;                         \
        palc[ 2 ] = p[ 2 ][ palindex ] - SUBTRACTION;                         \
        palc[ 3 ] = p[ 3 ][ palindex ] - SUBTRACTION;                         \
                                                                              \
        identical = ( palc[ 0 ] - c[ 0 ] ) | ( palc[ 1 ] - c[ 1 ] ) |         \
          ( palc[ 2 ] - c[ 2 ] ) | ( palc[ 3 ] - c[ 3 ] );                    \
                                                                              \
        if( !identical || BITS - pass == bits )                               \
        {                                                                     \
          /* Oh, here it is :) */                                             \
          dst[ j ] = palindex + s->offset;                                    \
          we_found_it = 1;                                                    \
        }                                                                     \
        else                                                                  \
        {                                                                     \
          mlib_u32 distance;                                                  \
          /* First index is the channel, second is the number of the          \
             side */                                                          \
          mlib_s32 found_color;                                               \
          mlib_s32 continue_up;                                               \
                                                                              \
          distance = FIND_DISTANCE_4( c[ 0 ], palc[ 0 ],                      \
            c[ 1 ], palc[ 1 ], c[ 2 ], palc[ 2 ], c[ 3 ], palc[ 3 ], SHIFT ); \
          found_color = palindex;                                             \
                                                                              \
          do                                                                  \
          {                                                                   \
            mlib_s32 check_corner;                                            \
            mlib_s32 check_neibours[ 6 ];                                     \
            mlib_s32 check_far_neibours[ 4 ];                                 \
                                                                              \
            /*                                                                \
              Check neibours: quadrants that are different by 2 bits          \
              from the quadrant, that we are in:                              \
              3 -  0                                                          \
              5 -  1                                                          \
              6 -  2                                                          \
              9 -  3                                                          \
              10 - 4                                                          \
              12 - 5                                                          \
              Far quadrants: different by 3 bits:                             \
              7  - 0                                                          \
              11 - 1                                                          \
              13 - 2                                                          \
              14 - 3                                                          \
            */                                                                \
                                                                              \
            check_neibours[ 0 ] = check_neibours[ 1 ] =                       \
              check_neibours[ 2 ] = check_neibours[ 3 ] =                     \
              check_neibours[ 4 ] = check_neibours[ 5 ] = 0;                  \
            continue_up = 0;                                                  \
                                                                              \
            if( q & 1 )                                                       \
            {                                                                 \
              BINARY_TREE_EXPLORE_LEFT_4( 0, SOURCE_IMAGE, 0, 1, 3,           \
                SUBTRACTION, SHIFT );                                         \
            }                                                                 \
            else                                                              \
            {                                                                 \
              BINARY_TREE_EXPLORE_RIGHT_4( 0, COLOR_MAX, SOURCE_IMAGE,        \
                0, 1, 3, SUBTRACTION, SHIFT );                                \
            }                                                                 \
                                                                              \
            if( q & 2 )                                                       \
            {                                                                 \
              BINARY_TREE_EXPLORE_LEFT_4( 1, SOURCE_IMAGE, 0, 2, 4,           \
                SUBTRACTION, SHIFT );                                         \
            }                                                                 \
            else                                                              \
            {                                                                 \
              BINARY_TREE_EXPLORE_RIGHT_4( 1, COLOR_MAX, SOURCE_IMAGE,        \
                0, 2, 4, SUBTRACTION, SHIFT );                                \
            }                                                                 \
                                                                              \
            if( q & 4 )                                                       \
            {                                                                 \
              BINARY_TREE_EXPLORE_LEFT_4( 2, SOURCE_IMAGE, 1, 2, 5,           \
                SUBTRACTION, SHIFT );                                         \
            }                                                                 \
            else                                                              \
            {                                                                 \
              BINARY_TREE_EXPLORE_RIGHT_4( 2, COLOR_MAX, SOURCE_IMAGE,        \
                1, 2, 5, SUBTRACTION, SHIFT );                                \
            }                                                                 \
                                                                              \
            if( q & 8 )                                                       \
            {                                                                 \
              BINARY_TREE_EXPLORE_LEFT_4( 3, SOURCE_IMAGE, 3, 4, 5,           \
                SUBTRACTION, SHIFT );                                         \
            }                                                                 \
            else                                                              \
            {                                                                 \
              BINARY_TREE_EXPLORE_RIGHT_4( 3, COLOR_MAX, SOURCE_IMAGE,        \
                3, 4, 5, SUBTRACTION, SHIFT );                                \
            }                                                                 \
                                                                              \
            check_far_neibours[ 0 ] = check_neibours[ 0 ] +                   \
              check_neibours[ 1 ] + check_neibours[ 2 ];                      \
            check_far_neibours[ 1 ] = check_neibours[ 0 ] +                   \
              check_neibours[ 3 ] + check_neibours[ 4 ];                      \
            check_far_neibours[ 2 ] = check_neibours[ 1 ] +                   \
              check_neibours[ 3 ] + check_neibours[ 5 ];                      \
            check_far_neibours[ 3 ] = check_neibours[ 2 ] +                   \
              check_neibours[ 4 ] + check_neibours[ 5 ];                      \
                                                                              \
            check_corner = check_far_neibours[ 0 ] +                          \
              check_far_neibours[ 1 ] +                                       \
              check_far_neibours[ 2 ] +                                       \
              check_far_neibours[ 3 ];                                        \
                                                                              \
            if( check_neibours[ 0 ] >= 2 )                                    \
            {                                                                 \
              mlib_s32 qq = q ^ 3;                                            \
              CHECK_QUADRANT_##SOURCE_IMAGE##_4( qq );                        \
            }                                                                 \
                                                                              \
            if( check_neibours[ 1 ] >= 2 )                                    \
            {                                                                 \
              mlib_s32 qq = q ^ 5;                                            \
              CHECK_QUADRANT_##SOURCE_IMAGE##_4( qq );                        \
            }                                                                 \
                                                                              \
            if( check_neibours[ 2 ] >= 2 )                                    \
            {                                                                 \
              mlib_s32 qq = q ^ 6;                                            \
              CHECK_QUADRANT_##SOURCE_IMAGE##_4( qq );                        \
            }                                                                 \
                                                                              \
            if( check_neibours[ 3 ] >= 2 )                                    \
            {                                                                 \
              mlib_s32 qq = q ^ 9;                                            \
              CHECK_QUADRANT_##SOURCE_IMAGE##_4( qq );                        \
            }                                                                 \
                                                                              \
            if( check_neibours[ 4 ] >= 2 )                                    \
            {                                                                 \
              mlib_s32 qq = q ^ 10;                                           \
              CHECK_QUADRANT_##SOURCE_IMAGE##_4( qq );                        \
            }                                                                 \
                                                                              \
            if( check_neibours[ 5 ] >= 2 )                                    \
            {                                                                 \
              mlib_s32 qq = q ^ 12;                                           \
              CHECK_QUADRANT_##SOURCE_IMAGE##_4( qq );                        \
            }                                                                 \
                                                                              \
            if( check_far_neibours[ 0 ] >= 3 )                                \
            {                                                                 \
              mlib_s32 qq = q ^ 7;                                            \
              CHECK_QUADRANT_##SOURCE_IMAGE##_4( qq );                        \
            }                                                                 \
                                                                              \
            if( check_far_neibours[ 1 ] >= 3 )                                \
            {                                                                 \
              mlib_s32 qq = q ^ 11;                                           \
              CHECK_QUADRANT_##SOURCE_IMAGE##_4( qq );                        \
            }                                                                 \
                                                                              \
            if( check_far_neibours[ 2 ] >= 3 )                                \
            {                                                                 \
              mlib_s32 qq = q ^ 13;                                           \
              CHECK_QUADRANT_##SOURCE_IMAGE##_4( qq );                        \
            }                                                                 \
                                                                              \
            if( check_far_neibours[ 3 ] >= 3 )                                \
            {                                                                 \
              mlib_s32 qq = q ^ 14;                                           \
              CHECK_QUADRANT_##SOURCE_IMAGE##_4( qq );                        \
            }                                                                 \
                                                                              \
            if( check_corner >= 4 )                                           \
            {                                                                 \
              mlib_s32 qq = q ^ 15;                                           \
              CHECK_QUADRANT_##SOURCE_IMAGE##_4( qq );                        \
            }                                                                 \
                                                                              \
            if( q & 1 )                                                       \
            {                                                                 \
              BINARY_TREE_SEARCH_RIGHT( 0, COLOR_MAX, SHIFT );                \
            }                                                                 \
            else                                                              \
            {                                                                 \
              BINARY_TREE_SEARCH_LEFT( 0, SHIFT );                            \
            }                                                                 \
                                                                              \
            if( q & 2 )                                                       \
            {                                                                 \
              BINARY_TREE_SEARCH_RIGHT( 1, COLOR_MAX, SHIFT );                \
            }                                                                 \
            else                                                              \
            {                                                                 \
              BINARY_TREE_SEARCH_LEFT( 1, SHIFT );                            \
            }                                                                 \
                                                                              \
            if( q & 4 )                                                       \
            {                                                                 \
              BINARY_TREE_SEARCH_RIGHT( 2, COLOR_MAX, SHIFT );                \
            }                                                                 \
            else                                                              \
            {                                                                 \
              BINARY_TREE_SEARCH_LEFT( 2, SHIFT );                            \
            }                                                                 \
                                                                              \
            if( q & 8 )                                                       \
            {                                                                 \
              BINARY_TREE_SEARCH_RIGHT( 3, COLOR_MAX, SHIFT );                \
            }                                                                 \
            else                                                              \
            {                                                                 \
              BINARY_TREE_SEARCH_LEFT( 3, SHIFT );                            \
            }                                                                 \
                                                                              \
            position[ 0 ] &= ~( c[ 0 ] & current_size );                      \
            position[ 1 ] &= ~( c[ 1 ] & current_size );                      \
            position[ 2 ] &= ~( c[ 2 ] & current_size );                      \
            position[ 3 ] &= ~( c[ 3 ] & current_size );                      \
                                                                              \
            current_size <<= 1;                                               \
                                                                              \
            pass++;                                                           \
                                                                              \
            stack_pointer--;                                                  \
            q = stack[ stack_pointer ].q;                                     \
            node = stack[ stack_pointer ].node;                               \
          } while( continue_up );                                             \
                                                                              \
          dst[ j ] = found_color + s->offset;                                 \
          we_found_it = 1;                                                    \
        }                                                                     \
      }                                                                       \
      else if( node->contents.quadrants[ q ] )                                \
      {                                                                       \
        /* Descend one level */                                               \
        stack[ stack_pointer ].node = node;                                   \
        stack[ stack_pointer++ ].q = q;                                       \
        node = node->contents.quadrants[ q ];                                 \
      }                                                                       \
      else                                                                    \
      {                                                                       \
        /* Found the empty quadrant. Look around */                           \
        mlib_u32 distance = MLIB_U32_MAX;                                     \
        mlib_s32 found_color;                                                 \
        mlib_s32 continue_up;                                                 \
                                                                              \
        /*                                                                    \
          As we had come to this level, it is warranted that there            \
          are other points on this level near the empty quadrant              \
        */                                                                    \
        do                                                                    \
        {                                                                     \
          mlib_s32 check_corner;                                              \
          mlib_s32 check_neibours[ 6 ];                                       \
          mlib_s32 check_far_neibours[ 4 ];                                   \
                                                                              \
          /*                                                                  \
            Check neibours: quadrants that are different by 2 bits            \
            from the quadrant, that we are in:                                \
            3 -  0                                                            \
            5 -  1                                                            \
            6 -  2                                                            \
            9 -  3                                                            \
            10 - 4                                                            \
            12 - 5                                                            \
            Far quadrants: different by 3 bits:                               \
            7  - 0                                                            \
            11 - 1                                                            \
            13 - 2                                                            \
            14 - 3                                                            \
          */                                                                  \
                                                                              \
          check_neibours[ 0 ] = check_neibours[ 1 ] =                         \
            check_neibours[ 2 ] = check_neibours[ 3 ] =                       \
            check_neibours[ 4 ] = check_neibours[ 5 ] = 0;                    \
          continue_up = 0;                                                    \
                                                                              \
          if( q & 1 )                                                         \
          {                                                                   \
            BINARY_TREE_EXPLORE_LEFT_4( 0, SOURCE_IMAGE, 0, 1, 3,             \
              SUBTRACTION, SHIFT );                                           \
          }                                                                   \
          else                                                                \
          {                                                                   \
            BINARY_TREE_EXPLORE_RIGHT_4( 0, COLOR_MAX, SOURCE_IMAGE,          \
              0, 1, 3, SUBTRACTION, SHIFT );                                  \
          }                                                                   \
                                                                              \
          if( q & 2 )                                                         \
          {                                                                   \
            BINARY_TREE_EXPLORE_LEFT_4( 1, SOURCE_IMAGE, 0, 2, 4,             \
              SUBTRACTION, SHIFT );                                           \
          }                                                                   \
          else                                                                \
          {                                                                   \
            BINARY_TREE_EXPLORE_RIGHT_4( 1, COLOR_MAX, SOURCE_IMAGE,          \
              0, 2, 4, SUBTRACTION, SHIFT );                                  \
          }                                                                   \
                                                                              \
          if( q & 4 )                                                         \
          {                                                                   \
            BINARY_TREE_EXPLORE_LEFT_4( 2, SOURCE_IMAGE, 1, 2, 5,             \
              SUBTRACTION, SHIFT );                                           \
          }                                                                   \
          else                                                                \
          {                                                                   \
            BINARY_TREE_EXPLORE_RIGHT_4( 2, COLOR_MAX, SOURCE_IMAGE,          \
              1, 2, 5, SUBTRACTION, SHIFT );                                  \
          }                                                                   \
                                                                              \
          if( q & 8 )                                                         \
          {                                                                   \
            BINARY_TREE_EXPLORE_LEFT_4( 3, SOURCE_IMAGE, 3, 4, 5,             \
              SUBTRACTION, SHIFT );                                           \
          }                                                                   \
          else                                                                \
          {                                                                   \
            BINARY_TREE_EXPLORE_RIGHT_4( 3, COLOR_MAX, SOURCE_IMAGE,          \
              3, 4, 5, SUBTRACTION, SHIFT );                                  \
          }                                                                   \
                                                                              \
          check_far_neibours[ 0 ] = check_neibours[ 0 ] +                     \
            check_neibours[ 1 ] + check_neibours[ 2 ];                        \
          check_far_neibours[ 1 ] = check_neibours[ 0 ] +                     \
            check_neibours[ 3 ] + check_neibours[ 4 ];                        \
          check_far_neibours[ 2 ] = check_neibours[ 1 ] +                     \
            check_neibours[ 3 ] + check_neibours[ 5 ];                        \
          check_far_neibours[ 3 ] = check_neibours[ 2 ] +                     \
            check_neibours[ 4 ] + check_neibours[ 5 ];                        \
                                                                              \
          check_corner = check_far_neibours[ 0 ] +                            \
            check_far_neibours[ 1 ] +                                         \
            check_far_neibours[ 2 ] +                                         \
            check_far_neibours[ 3 ];                                          \
                                                                              \
          if( check_neibours[ 0 ] >= 2 )                                      \
          {                                                                   \
            mlib_s32 qq = q ^ 3;                                              \
            CHECK_QUADRANT_##SOURCE_IMAGE##_4( qq );                          \
          }                                                                   \
                                                                              \
          if( check_neibours[ 1 ] >= 2 )                                      \
          {                                                                   \
            mlib_s32 qq = q ^ 5;                                              \
            CHECK_QUADRANT_##SOURCE_IMAGE##_4( qq );                          \
          }                                                                   \
                                                                              \
          if( check_neibours[ 2 ] >= 2 )                                      \
          {                                                                   \
            mlib_s32 qq = q ^ 6;                                              \
            CHECK_QUADRANT_##SOURCE_IMAGE##_4( qq );                          \
          }                                                                   \
                                                                              \
          if( check_neibours[ 3 ] >= 2 )                                      \
          {                                                                   \
            mlib_s32 qq = q ^ 9;                                              \
            CHECK_QUADRANT_##SOURCE_IMAGE##_4( qq );                          \
          }                                                                   \
                                                                              \
          if( check_neibours[ 4 ] >= 2 )                                      \
          {                                                                   \
            mlib_s32 qq = q ^ 10;                                             \
            CHECK_QUADRANT_##SOURCE_IMAGE##_4( qq );                          \
          }                                                                   \
                                                                              \
          if( check_neibours[ 5 ] >= 2 )                                      \
          {                                                                   \
            mlib_s32 qq = q ^ 12;                                             \
            CHECK_QUADRANT_##SOURCE_IMAGE##_4( qq );                          \
          }                                                                   \
                                                                              \
          if( check_far_neibours[ 0 ] >= 3 )                                  \
          {                                                                   \
            mlib_s32 qq = q ^ 7;                                              \
            CHECK_QUADRANT_##SOURCE_IMAGE##_4( qq );                          \
          }                                                                   \
                                                                              \
          if( check_far_neibours[ 1 ] >= 3 )                                  \
          {                                                                   \
            mlib_s32 qq = q ^ 11;                                             \
            CHECK_QUADRANT_##SOURCE_IMAGE##_4( qq );                          \
          }                                                                   \
                                                                              \
          if( check_far_neibours[ 2 ] >= 3 )                                  \
          {                                                                   \
            mlib_s32 qq = q ^ 13;                                             \
            CHECK_QUADRANT_##SOURCE_IMAGE##_4( qq );                          \
          }                                                                   \
                                                                              \
          if( check_far_neibours[ 3 ] >= 3 )                                  \
          {                                                                   \
            mlib_s32 qq = q ^ 14;                                             \
            CHECK_QUADRANT_##SOURCE_IMAGE##_4( qq );                          \
          }                                                                   \
                                                                              \
          if( check_corner >= 4 )                                             \
          {                                                                   \
            mlib_s32 qq = q ^ 15;                                             \
            CHECK_QUADRANT_##SOURCE_IMAGE##_4( qq );                          \
          }                                                                   \
                                                                              \
          if( q & 1 )                                                         \
          {                                                                   \
            BINARY_TREE_SEARCH_RIGHT( 0, COLOR_MAX, SHIFT );                  \
          }                                                                   \
          else                                                                \
          {                                                                   \
            BINARY_TREE_SEARCH_LEFT( 0, SHIFT );                              \
          }                                                                   \
                                                                              \
          if( q & 2 )                                                         \
          {                                                                   \
            BINARY_TREE_SEARCH_RIGHT( 1, COLOR_MAX, SHIFT );                  \
          }                                                                   \
          else                                                                \
          {                                                                   \
            BINARY_TREE_SEARCH_LEFT( 1, SHIFT );                              \
          }                                                                   \
                                                                              \
          if( q & 4 )                                                         \
          {                                                                   \
            BINARY_TREE_SEARCH_RIGHT( 2, COLOR_MAX, SHIFT );                  \
          }                                                                   \
          else                                                                \
          {                                                                   \
            BINARY_TREE_SEARCH_LEFT( 2, SHIFT );                              \
          }                                                                   \
                                                                              \
          if( q & 8 )                                                         \
          {                                                                   \
            BINARY_TREE_SEARCH_RIGHT( 3, COLOR_MAX, SHIFT );                  \
          }                                                                   \
          else                                                                \
          {                                                                   \
            BINARY_TREE_SEARCH_LEFT( 3, SHIFT );                              \
          }                                                                   \
                                                                              \
          position[ 0 ] &= ~( c[ 0 ] & current_size );                        \
          position[ 1 ] &= ~( c[ 1 ] & current_size );                        \
          position[ 2 ] &= ~( c[ 2 ] & current_size );                        \
          position[ 3 ] &= ~( c[ 3 ] & current_size );                        \
                                                                              \
          current_size <<= 1;                                                 \
                                                                              \
          pass++;                                                             \
                                                                              \
          stack_pointer--;                                                    \
          q = stack[ stack_pointer ].q;                                       \
          node = stack[ stack_pointer ].node;                                 \
        } while( continue_up );                                               \
                                                                              \
        dst[ j ] = found_color + s->offset;                                   \
        we_found_it = 1;                                                      \
      }                                                                       \
                                                                              \
      pass--;                                                                 \
                                                                              \
    } while( !we_found_it );                                                  \
                                                                              \
    channels[ 0 ] += 4;                                                       \
    channels[ 1 ] += 4;                                                       \
    channels[ 2 ] += 4;                                                       \
    channels[ 3 ] += 4;                                                       \
  }                                                                           \
}

/***************************************************************/
#define FIND_NEAREST_U8_3_C( SHIFT, STEP )                      \
  mlib_s32 i, k, k_min, min_dist, diff, mask;                   \
  mlib_s32 offset = mlib_ImageGetLutOffset( s ) - 1;            \
  mlib_s32 entries = s -> lutlength;                            \
  mlib_d64 *double_lut = mlib_ImageGetLutDoubleData( s );       \
  mlib_d64 col0, col1, col2;                                    \
  mlib_d64 dist, len0, len1, len2;                              \
                                                                \
  for ( i = 0; i < length; i++ ) {                              \
    col0 = src[ STEP * i + SHIFT ];                             \
    col1 = src[ STEP * i + 1 + SHIFT ];                         \
    col2 = src[ STEP * i + 2 + SHIFT ];                         \
    min_dist = MLIB_S32_MAX;                                    \
    k_min = 1;                                                  \
    len0 = double_lut[ 0 ] - col0;                              \
    len1 = double_lut[ 1 ] - col1;                              \
    len2 = double_lut[ 2 ] - col2;                              \
                                                                \
    for ( k = 1; k <= entries; k++ ) {                          \
      dist = len0 * len0;                                       \
      len0 = double_lut[ 3 * k ] - col0;                        \
      dist += len1 * len1;                                      \
      len1 = double_lut[ 3 * k + 1 ] - col1;                    \
      dist += len2 * len2;                                      \
      len2 = double_lut[ 3 * k + 2 ] - col2;                    \
      diff = ( mlib_s32 )dist - min_dist;                       \
      mask = diff >> 31;                                        \
      min_dist += diff & mask;                                  \
      k_min += ( k - k_min ) & mask;                            \
    }                                                           \
                                                                \
    dst[ i ] = k_min + offset;                                  \
  }

/***************************************************************/
#define FIND_NEAREST_U8_4_C                                     \
  mlib_s32 i, k, k_min, min_dist, diff, mask;                   \
  mlib_s32 offset = mlib_ImageGetLutOffset( s ) - 1;            \
  mlib_s32 entries = s -> lutlength;                            \
  mlib_d64 *double_lut = mlib_ImageGetLutDoubleData( s );       \
  mlib_d64 col0, col1, col2, col3;                              \
  mlib_d64 dist, len0, len1, len2, len3;                        \
                                                                \
  for ( i = 0; i < length; i++ ) {                              \
    col0 = src[ 4 * i ];                                        \
    col1 = src[ 4 * i + 1 ];                                    \
    col2 = src[ 4 * i + 2 ];                                    \
    col3 = src[ 4 * i + 3 ];                                    \
    min_dist = MLIB_S32_MAX;                                    \
    k_min = 1;                                                  \
    len0 = double_lut[ 0 ] - col0;                              \
    len1 = double_lut[ 1 ] - col1;                              \
    len2 = double_lut[ 2 ] - col2;                              \
    len3 = double_lut[ 3 ] - col3;                              \
                                                                \
    for ( k = 1; k <= entries; k++ ) {                          \
      dist = len0 * len0;                                       \
      len0 =  double_lut[ 4 * k ] - col0;                       \
      dist += len1 * len1;                                      \
      len1 = double_lut[ 4 * k + 1 ] - col1;                    \
      dist += len2 * len2;                                      \
      len2 =  double_lut[ 4 * k + 2 ] - col2;                   \
      dist += len3 * len3;                                      \
      len3 =  double_lut[ 4 * k + 3 ] - col3;                   \
      diff = ( mlib_s32 )dist - min_dist;                       \
      mask = diff >> 31;                                        \
      min_dist += diff & mask;                                  \
      k_min += ( k - k_min ) & mask;                            \
    }                                                           \
                                                                \
    dst[ i ] = k_min + offset;                                  \
  }

/***************************************************************/
#define FSQR_S16_HI(dsrc)                                                   \
  vis_fpadd32( vis_fmuld8ulx16( vis_read_hi( dsrc ), vis_read_hi( dsrc ) ), \
    vis_fmuld8sux16( vis_read_hi( dsrc ), vis_read_hi( dsrc ) ) )

/***************************************************************/
#define FSQR_S16_LO(dsrc)                                                  \
  vis_fpadd32( vis_fmuld8ulx16( vis_read_lo( dsrc ), vis_read_lo( dsrc) ), \
    vis_fmuld8sux16( vis_read_lo( dsrc ), vis_read_lo( dsrc ) ) )

/***************************************************************/
#define FIND_NEAREST_U8_3                                             \
{                                                                     \
  mlib_d64 *dpsrc, dsrc, dsrc1, ddist, ddist1, ddist2, ddist3;        \
  mlib_d64 dcolor, dind, dres, dres1, dpind[1], dpmin[1];             \
  mlib_d64 done = vis_to_double_dup( 1 ),                             \
           dmax = vis_to_double_dup( MLIB_S32_MAX );                  \
  mlib_f32 *lut = ( mlib_f32 * )mlib_ImageGetLutNormalTable( s );     \
  mlib_f32 fone = vis_to_float( 0x100 );                              \
  mlib_s32 i, k, mask;                                                \
  mlib_s32 gsr[1];                                                    \
  mlib_s32 offset = mlib_ImageGetLutOffset( s ) - 1;                  \
  mlib_s32 entries = s->lutlength;                                    \
                                                                      \
  gsr[0] = vis_read_gsr();                                            \
  for( i = 0; i <= ( length-2 ); i += 2 )                             \
  {                                                                   \
    dpsrc = VIS_ALIGNADDR( src, -1 );                                 \
    src += 6;                                                         \
    dsrc = dpsrc[ 0 ];                                                \
    dsrc1 = dpsrc[ 1 ];                                               \
    dsrc1 = vis_faligndata( dsrc, dsrc1 );                            \
    dsrc = vis_fmul8x16al( vis_read_hi( dsrc1 ), fone );              \
    VIS_ALIGNADDR( dpsrc, 3 );                                        \
    dsrc1 = vis_faligndata( dsrc1, dsrc1 );                           \
    dsrc1 = vis_fmul8x16al( vis_read_hi( dsrc1 ), fone );             \
    dpind[ 0 ] = dind = done;                                         \
    dpmin[ 0 ] = dmax;                                                \
    dcolor = vis_fmul8x16al( lut[ 0 ], fone );                        \
    for( k = 1; k <= entries; k++ )                                   \
    {                                                                 \
      ddist1 = vis_fpsub16( dcolor, dsrc );                           \
      ddist = FSQR_S16_HI( ddist1 );                                  \
      ddist1 = FSQR_S16_LO( ddist1 );                                 \
      dres = vis_fpadd32( ddist, ddist1 );                            \
      ddist3 = vis_fpsub16( dcolor, dsrc1 );                          \
      ddist2 = FSQR_S16_HI( ddist3 );                                 \
      ddist3 = FSQR_S16_LO( ddist3 );                                 \
      dres1 = vis_fpadd32( ddist2, ddist3 );                          \
      dcolor = vis_fmul8x16al( lut[ k ], fone );                      \
      dres = vis_freg_pair(                                           \
        vis_fpadd32s( vis_read_hi( dres ), vis_read_lo( dres ) ),     \
        vis_fpadd32s( vis_read_hi( dres1 ), vis_read_lo( dres1 ) ) ); \
      mask = vis_fcmplt32( dres, dpmin[ 0 ] );                        \
      vis_pst_32( dind, ( void * )dpind, mask );                      \
      dind = vis_fpadd32( dind, done );                               \
      vis_pst_32( dres, ( void * )dpmin, mask );                      \
    }                                                                 \
    dst[ i ] = ( ( mlib_s32 * )dpind )[ 0 ] + offset;                 \
    dst[ i + 1 ] = ( ( mlib_s32 * )dpind)[ 1 ] + offset;              \
  }                                                                   \
  if( i < length )                                                    \
  {                                                                   \
    dpsrc = VIS_ALIGNADDR( src, -1 );                                 \
    dsrc = dpsrc[ 0 ];                                                \
    dsrc1 = dpsrc[ 1 ];                                               \
    dsrc1 = vis_faligndata( dsrc, dsrc1 );                            \
    dsrc = vis_fmul8x16al( vis_read_hi( dsrc1 ), fone );              \
    dpind[ 0 ] = dind = done;                                         \
    dpmin[ 0 ] = dmax;                                                \
    for( k = 0; k < entries; k++ )                                    \
    {                                                                 \
      dcolor = vis_fmul8x16al( lut[ k ], fone );                      \
      ddist1 = vis_fpsub16( dcolor, dsrc );                           \
      ddist = FSQR_S16_HI( ddist1 );                                  \
      ddist1 = FSQR_S16_LO( ddist1 );                                 \
      dres = vis_fpadd32( ddist, ddist1 );                            \
      dres = vis_write_lo( dres,                                      \
        vis_fpadd32s( vis_read_hi( dres ), vis_read_lo( dres ) ) );   \
      mask = vis_fcmplt32( dres, dpmin[ 0 ] );                        \
      vis_pst_32( dind, ( void * )dpind, mask );                      \
      dind = vis_fpadd32( dind, done );                               \
      vis_pst_32( dres, ( void * )dpmin, mask );                      \
    }                                                                 \
    dst[ i ] = ( ( mlib_s32 * )dpind)[ 1 ] + offset;                  \
  }                                                                   \
  vis_write_gsr(gsr[0]);                                              \
}

/***************************************************************/
#define FIND_NEAREST_U8_3_IN4                                         \
{                                                                     \
  mlib_d64 *dpsrc, dsrc, dsrc1, ddist, ddist1, ddist2, ddist3;        \
  mlib_d64 dcolor, dind, dres, dres1, dpind[1], dpmin[1];             \
  mlib_d64 done = vis_to_double_dup( 1 ),                             \
           dmax = vis_to_double_dup( MLIB_S32_MAX );                  \
  mlib_f32 *lut = ( mlib_f32 * )mlib_ImageGetLutNormalTable( s );     \
  mlib_f32 fone = vis_to_float( 0x100 );                              \
  mlib_s32 i, k, mask, gsr[1];                                        \
  mlib_s32 offset = mlib_ImageGetLutOffset( s ) - 1;                  \
  mlib_s32 entries = s->lutlength;                                    \
                                                                      \
  gsr[0] = vis_read_gsr();                                            \
  dpsrc = VIS_ALIGNADDR( src, 0 );                                    \
  for( i = 0; i <= ( length-2 ); i += 2 )                             \
  {                                                                   \
    dsrc = dpsrc[ 0 ];                                                \
    dsrc1 = dpsrc[ 1 ];                                               \
    dsrc1 = vis_faligndata( dsrc, dsrc1 );                            \
    dpsrc++;                                                          \
    dsrc = vis_fmul8x16al( vis_read_hi( dsrc1 ), fone );              \
    dsrc1 = vis_fmul8x16al( vis_read_lo( dsrc1 ), fone );             \
    dpind[ 0 ] = dind = done;                                         \
    dpmin[ 0 ] = dmax;                                                \
    dcolor = vis_fmul8x16al( lut[ 0 ], fone );                        \
    for( k = 1; k <= entries; k++ )                                   \
    {                                                                 \
      ddist1 = vis_fpsub16( dcolor, dsrc );                           \
      ddist = FSQR_S16_HI( ddist1 );                                  \
      ddist1 = FSQR_S16_LO( ddist1 );                                 \
      dres = vis_fpadd32( ddist, ddist1 );                            \
      ddist3 = vis_fpsub16( dcolor, dsrc1 );                          \
      ddist2 = FSQR_S16_HI( ddist3 );                                 \
      ddist3 = FSQR_S16_LO( ddist3 );                                 \
      dres1 = vis_fpadd32( ddist2, ddist3 );                          \
      dcolor = vis_fmul8x16al( lut[ k ], fone );                      \
      dres = vis_freg_pair(                                           \
        vis_fpadd32s( vis_read_hi( dres ), vis_read_lo( dres ) ),     \
        vis_fpadd32s( vis_read_hi( dres1 ), vis_read_lo( dres1 ) ) ); \
      mask = vis_fcmplt32( dres, dpmin[ 0 ] );                        \
      vis_pst_32( dind, ( void * )dpind, mask );                      \
      dind = vis_fpadd32( dind, done );                               \
      vis_pst_32( dres, ( void * )dpmin, mask );                      \
    }                                                                 \
    dst[ i ] = ( ( mlib_s32 * )dpind )[ 0 ] + offset;                 \
    dst[ i + 1 ] = ( ( mlib_s32 * )dpind)[ 1 ] + offset;              \
  }                                                                   \
  if( i < length )                                                    \
  {                                                                   \
    dsrc = dpsrc[ 0 ];                                                \
    dsrc1 = dpsrc[ 1 ];                                               \
    dsrc1 = vis_faligndata( dsrc, dsrc1 );                            \
    dsrc = vis_fmul8x16al( vis_read_hi( dsrc1 ), fone );              \
    dpind[ 0 ] = dind = done;                                         \
    dpmin[ 0 ] = dmax;                                                \
    for( k = 0; k < entries; k++ )                                    \
    {                                                                 \
      dcolor = vis_fmul8x16al( lut[ k ], fone );                      \
      ddist1 = vis_fpsub16( dcolor, dsrc );                           \
      ddist = FSQR_S16_HI( ddist1 );                                  \
      ddist1 = FSQR_S16_LO( ddist1 );                                 \
      dres = vis_fpadd32( ddist, ddist1 );                            \
      dres = vis_write_lo( dres,                                      \
        vis_fpadd32s( vis_read_hi( dres ), vis_read_lo( dres ) ) );   \
      mask = vis_fcmplt32( dres, dpmin[ 0 ] );                        \
      vis_pst_32( dind, ( void * )dpind, mask );                      \
      dind = vis_fpadd32( dind, done );                               \
      vis_pst_32( dres, ( void * )dpmin, mask );                      \
    }                                                                 \
    dst[ i ] = ( ( mlib_s32 * )dpind)[ 1 ] + offset;                  \
  }                                                                   \
  vis_write_gsr(gsr[0]);                                              \
}

/***************************************************************/
#define FIND_NEAREST_U8_4                                             \
{                                                                     \
  mlib_d64 *dpsrc, dsrc, dsrc1, ddist, ddist1, ddist2, ddist3;        \
  mlib_d64 dcolor, dind, dres, dres1, dpind[ 1 ], dpmin[ 1 ];         \
  mlib_d64 done = vis_to_double_dup( 1 ),                             \
           dmax = vis_to_double_dup( MLIB_S32_MAX );                  \
  mlib_f32 *lut = ( mlib_f32 * )mlib_ImageGetLutNormalTable( s );     \
  mlib_f32 fone = vis_to_float( 0x100 );                              \
  mlib_s32 i, k, mask, gsr[1];                                        \
  mlib_s32 offset = mlib_ImageGetLutOffset( s ) - 1;                  \
  mlib_s32 entries = s->lutlength;                                    \
                                                                      \
  gsr[0] = vis_read_gsr();                                            \
  dpsrc = VIS_ALIGNADDR( src, 0 );                                    \
  for( i = 0; i <= ( length-2 ); i += 2 )                             \
  {                                                                   \
    dsrc = dpsrc[ 0 ];                                                \
    dsrc1 = dpsrc[ 1 ];                                               \
    dsrc1 = vis_faligndata( dsrc, dsrc1 );                            \
    dpsrc++;                                                          \
    dsrc = vis_fmul8x16al( vis_read_hi( dsrc1 ), fone );              \
    dsrc1 = vis_fmul8x16al( vis_read_lo( dsrc1 ), fone );             \
    dpind[ 0 ] = dind = done;                                         \
    dpmin[ 0 ] = dmax;                                                \
    dcolor = vis_fmul8x16al(lut[0], fone);                            \
    for( k = 1; k <= entries; k++ )                                   \
    {                                                                 \
      ddist1 = vis_fpsub16( dcolor, dsrc );                           \
      ddist = FSQR_S16_HI( ddist1 );                                  \
      ddist1 = FSQR_S16_LO( ddist1 );                                 \
      dres = vis_fpadd32( ddist, ddist1 );                            \
      ddist3 = vis_fpsub16( dcolor, dsrc1 );                          \
      ddist2 = FSQR_S16_HI( ddist3 );                                 \
      ddist3 = FSQR_S16_LO( ddist3 );                                 \
      dres1 = vis_fpadd32( ddist2, ddist3 );                          \
      dcolor = vis_fmul8x16al( lut[ k ], fone );                      \
      dres = vis_freg_pair(                                           \
        vis_fpadd32s( vis_read_hi( dres ), vis_read_lo( dres ) ),     \
        vis_fpadd32s( vis_read_hi( dres1 ), vis_read_lo( dres1 ) ) ); \
      mask = vis_fcmplt32( dres, dpmin[ 0 ] );                        \
      vis_pst_32( dind, ( void * )dpind, mask );                      \
      dind = vis_fpadd32( dind, done );                               \
      vis_pst_32( dres, ( void * )dpmin, mask );                      \
    }                                                                 \
    dst[ i ] = ( ( mlib_s32 * )dpind )[ 0 ] + offset;                 \
    dst[ i + 1 ] = ( ( mlib_s32 * )dpind )[ 1 ] + offset;             \
  }                                                                   \
  if( i < length )                                                    \
  {                                                                   \
    dsrc = dpsrc[ 0 ];                                                \
    dsrc1 = dpsrc[ 1 ];                                               \
    dsrc1 = vis_faligndata( dsrc, dsrc1 );                            \
    dsrc = vis_fmul8x16al( vis_read_hi( dsrc1 ), fone );              \
    dpind[ 0 ] = dind = done;                                         \
    dpmin[ 0 ] = dmax;                                                \
    for( k = 0; k < entries; k++ )                                    \
    {                                                                 \
      dcolor = vis_fmul8x16al( lut[ k ], fone );                      \
      ddist1 = vis_fpsub16( dcolor, dsrc );                           \
      ddist = FSQR_S16_HI( ddist1 );                                  \
      ddist1 = FSQR_S16_LO( ddist1 );                                 \
      dres = vis_fpadd32( ddist, ddist1 );                            \
      dres = vis_write_lo( dres,                                      \
        vis_fpadd32s( vis_read_hi( dres ), vis_read_lo( dres ) ) );   \
      mask = vis_fcmplt32( dres, dpmin[ 0 ] );                        \
      vis_pst_32( dind, ( void * )dpind, mask );                      \
      dind = vis_fpadd32( dind, done );                               \
      vis_pst_32( dres, ( void * )dpmin, mask );                      \
    }                                                                 \
    dst[ i ] = ( ( mlib_s32 * )dpind )[ 1 ] + offset;                 \
  }                                                                   \
  vis_write_gsr(gsr[0]);                                              \
}

/***************************************************************/
#define FIND_NEAREST_S16_3( SHIFT, STEP )                       \
  mlib_s32 i, k, k_min, min_dist, diff, mask;                   \
  mlib_s32 offset = mlib_ImageGetLutOffset( s ) - 1;            \
  mlib_s32 entries = s->lutlength;                              \
  mlib_d64 *double_lut = mlib_ImageGetLutDoubleData( s );       \
  mlib_d64 col0, col1, col2;                                    \
  mlib_d64 dist, len0, len1, len2;                              \
                                                                \
  for( i = 0; i < length; i++ )                                 \
  {                                                             \
    col0 = src[ STEP * i + SHIFT ];                             \
    col1 = src[ STEP * i + 1 + SHIFT ];                         \
    col2 = src[ STEP * i + 2 + SHIFT ];                         \
    min_dist = MLIB_S32_MAX;                                    \
    k_min = 1;                                                  \
    len0 = double_lut[ 0 ] - col0;                              \
    len1 = double_lut[ 1 ] - col1;                              \
    len2 = double_lut[ 2 ] - col2;                              \
    for( k = 1; k <= entries; k++ )                             \
    {                                                           \
      dist = len0 * len0;                                       \
      len0 = double_lut[ 3 * k ] - col0;                        \
      dist += len1 * len1;                                      \
      len1 = double_lut[ 3 * k + 1 ] - col1;                    \
      dist += len2 * len2;                                      \
      len2 = double_lut[ 3 * k + 2 ] - col2;                    \
      diff = ( mlib_s32 )( dist * 0.125 ) - min_dist;           \
      mask = diff >> 31;                                        \
      min_dist += diff & mask;                                  \
      k_min += ( k - k_min ) & mask;                            \
    }                                                           \
    dst[ i ] = k_min + offset;                                  \
  }

/***************************************************************/
#define FIND_NEAREST_S16_4                                      \
  mlib_s32 i, k, k_min, min_dist, diff, mask;                   \
  mlib_s32 offset = mlib_ImageGetLutOffset( s ) - 1;            \
  mlib_s32 entries = s->lutlength;                              \
  mlib_d64 *double_lut = mlib_ImageGetLutDoubleData( s );       \
  mlib_d64 col0, col1, col2, col3;                              \
  mlib_d64 dist, len0, len1, len2, len3;                        \
                                                                \
  for( i = 0; i < length; i++ )                                 \
  {                                                             \
    col0 = src[ 4 * i ];                                        \
    col1 = src[ 4 * i + 1 ];                                    \
    col2 = src[ 4 * i + 2 ];                                    \
    col3 = src[ 4 * i + 3 ];                                    \
    min_dist = MLIB_S32_MAX;                                    \
    k_min = 1;                                                  \
    len0 = double_lut[ 0 ] - col0;                              \
    len1 = double_lut[ 1 ] - col1;                              \
    len2 = double_lut[ 2 ] - col2;                              \
    len3 = double_lut[ 3 ] - col3;                              \
    for( k = 1; k <= entries; k++ )                             \
    {                                                           \
      dist = len0 * len0;                                       \
      len0 =  double_lut[ 4 * k ] - col0;                       \
      dist += len1 * len1;                                      \
      len1 = double_lut[ 4 * k + 1 ] - col1;                    \
      dist += len2 * len2;                                      \
      len2 =  double_lut[ 4 * k + 2 ] - col2;                   \
      dist += len3 * len3;                                      \
      len3 =  double_lut[ 4 * k + 3 ] - col3;                   \
      diff = ( mlib_s32 )( dist * 0.125 ) - min_dist;           \
      mask = diff >> 31;                                        \
      min_dist += diff & mask;                                  \
      k_min += ( k - k_min ) & mask;                            \
    }                                                           \
    dst[ i ] = k_min + offset;                                  \
  }

/***************************************************************/
mlib_status mlib_ImageColorTrue2Index(mlib_image       *dst,
                                      const mlib_image *src,
                                      const void       *colormap)
{
  mlib_s32 y, width, height, sstride, dstride, schann;
  mlib_colormap *s = (mlib_colormap *)colormap;
  mlib_s32 channels;
  mlib_type stype, dtype;

  MLIB_IMAGE_CHECK(src);
  MLIB_IMAGE_CHECK(dst);
  MLIB_IMAGE_SIZE_EQUAL(src, dst);
  MLIB_IMAGE_HAVE_CHAN(dst, 1);

  if (!colormap)
    return MLIB_NULLPOINTER;

  channels = s->channels;
  stype = mlib_ImageGetType(src);
  dtype = mlib_ImageGetType(dst);
  width = mlib_ImageGetWidth(src);
  height = mlib_ImageGetHeight(src);
  sstride = mlib_ImageGetStride(src);
  dstride = mlib_ImageGetStride(dst);
  schann = mlib_ImageGetChannels(src);

  if (stype != s->intype || dtype != s->outtype)
    return MLIB_FAILURE;

  if (channels != schann)
    return MLIB_FAILURE;

  switch (stype) {
    case MLIB_BYTE:
      {
        mlib_u8 *sdata = mlib_ImageGetData(src);

        switch (dtype) {
          case MLIB_BYTE:
            {
              mlib_u8 *ddata = mlib_ImageGetData(dst);

              switch (channels) {
                case 3:
                  {
                    MAIN_COLORTRUE2INDEX_LOOP(U8, U8, 3);
                    return MLIB_SUCCESS;
                  }

                case 4:
                  {
                    MAIN_COLORTRUE2INDEX_LOOP(U8, U8, 4);
                    return MLIB_SUCCESS;
                  }

                default:
                  return MLIB_FAILURE;
              }
            }

          case MLIB_SHORT:
            {
              mlib_s16 *ddata = mlib_ImageGetData(dst);

              dstride /= 2;
              switch (channels) {
                case 3:
                  {
                    MAIN_COLORTRUE2INDEX_LOOP(U8, S16, 3);
                    return MLIB_SUCCESS;
                  }

                case 4:
                  {
                    MAIN_COLORTRUE2INDEX_LOOP(U8, S16, 4);
                    return MLIB_SUCCESS;
                  }

                default:
                  return MLIB_FAILURE;
              }
            }
        default:
          /* Unsupported type of destination image */
          return MLIB_FAILURE;
        }
      }

    case MLIB_SHORT:
      {
        mlib_s16 *sdata = mlib_ImageGetData(src);

        sstride /= 2;
        switch (dtype) {
          case MLIB_BYTE:
            {
              mlib_u8 *ddata = mlib_ImageGetData(dst);

              switch (channels) {
                case 3:
                  {
                    MAIN_COLORTRUE2INDEX_LOOP(S16, U8, 3);
                    return MLIB_SUCCESS;
                  }

                case 4:
                  {
                    MAIN_COLORTRUE2INDEX_LOOP(S16, U8, 4);
                    return MLIB_SUCCESS;
                  }

                default:
                  return MLIB_FAILURE;
              }
            }

          case MLIB_SHORT:
            {
              mlib_s16 *ddata = mlib_ImageGetData(dst);

              dstride /= 2;
              switch (channels) {
                case 3:
                  {
                    MAIN_COLORTRUE2INDEX_LOOP(S16, S16, 3);
                    return MLIB_SUCCESS;
                  }

                case 4:
                  {
                    MAIN_COLORTRUE2INDEX_LOOP(S16, S16, 4);
                    return MLIB_SUCCESS;
                  }

                default:
                  return MLIB_FAILURE;
              }
            }
        default:
          /* Unsupported type of destination image */
          return MLIB_FAILURE;
        }
      }

    default:
      return MLIB_FAILURE;
  }
}

/***************************************************************/
mlib_u32 mlib_search_quadrant_U8_3(struct lut_node_3 *node,
                                   mlib_u32          distance,
                                    mlib_s32    *found_color,
                                   mlib_u32          c0,
                                   mlib_u32          c1,
                                   mlib_u32          c2,
                                   const mlib_u8     **base)
{
  mlib_s32 i;

  for (i = 0; i < 8; i++) {

    if (node->tag & (1 << i)) {
      /* Here is alone color cell. Check the distance */
      mlib_s32 newindex = node->contents.index[i];
      mlib_u32 newpalc0, newpalc1, newpalc2;
      mlib_u32 newdistance;

      newpalc0 = base[0][newindex];
      newpalc1 = base[1][newindex];
      newpalc2 = base[2][newindex];
      newdistance = FIND_DISTANCE_3(c0, newpalc0, c1, newpalc1, c2, newpalc2, 0);

      if (distance > newdistance) {
        *found_color = newindex;
        distance = newdistance;
      }
    }
    else if (node->contents.quadrants[i])
      distance =
        mlib_search_quadrant_U8_3(node->contents.quadrants[i], distance,
                                  found_color, c0, c1, c2, base);
  }

  return distance;
}

/***************************************************************/
mlib_u32 mlib_search_quadrant_part_to_left_U8_3(struct lut_node_3 *node,
                                                mlib_u32          distance,
                                                 mlib_s32    *found_color,
                                                const mlib_u32    *c,
                                                const mlib_u8     **base,
                                                mlib_u32          position,
                                                mlib_s32          pass,
                                                mlib_s32          dir_bit)
{
  mlib_u32 current_size = 1 << pass;
  mlib_s32 i;
  static mlib_s32 opposite_quadrants[3][4] = {
    {0, 2, 4, 6},
    {0, 1, 4, 5},
    {0, 1, 2, 3}
  };

/* Search only quadrant's half untill it is necessary to check the
  whole quadrant */

  if (distance < (position + current_size - c[dir_bit]) * (position + current_size - c[dir_bit])) { /* Search half of quadrant */
    for (i = 0; i < 4; i++) {
      mlib_s32 qq = opposite_quadrants[dir_bit][i];

      if (node->tag & (1 << qq)) {
        /* Here is alone color cell. Check the distance */
        mlib_s32 newindex = node->contents.index[qq];
        mlib_u32 newpalc0, newpalc1, newpalc2;
        mlib_u32 newdistance;

        newpalc0 = base[0][newindex];
        newpalc1 = base[1][newindex];
        newpalc2 = base[2][newindex];
        newdistance = FIND_DISTANCE_3(c[0], newpalc0, c[1], newpalc1, c[2], newpalc2, 0);

        if (distance > newdistance) {
          *found_color = newindex;
          distance = newdistance;
        }
      }
      else if (node->contents.quadrants[qq])
        distance =
          mlib_search_quadrant_part_to_left_U8_3(node->contents.quadrants[qq],
                                                 distance, found_color, c, base,
                                                 position, pass - 1, dir_bit);
    }
  }
  else {                                    /* Search whole quadrant */

    mlib_s32 mask = 1 << dir_bit;

    for (i = 0; i < 8; i++) {

      if (node->tag & (1 << i)) {
        /* Here is alone color cell. Check the distance */
        mlib_s32 newindex = node->contents.index[i];
        mlib_u32 newpalc0, newpalc1, newpalc2;
        mlib_u32 newdistance;

        newpalc0 = base[0][newindex];
        newpalc1 = base[1][newindex];
        newpalc2 = base[2][newindex];
        newdistance = FIND_DISTANCE_3(c[0], newpalc0, c[1], newpalc1, c[2], newpalc2, 0);

        if (distance > newdistance) {
          *found_color = newindex;
          distance = newdistance;
        }
      }
      else if (node->contents.quadrants[i]) {

        if (i & mask)
          /* This quadrant may require partial checking */
          distance =
            mlib_search_quadrant_part_to_left_U8_3(node->contents.quadrants[i],
                                                   distance, found_color, c,
                                                   base,
                                                   position + current_size,
                                                   pass - 1, dir_bit);
        else
          /* Here we should check all */
          distance =
            mlib_search_quadrant_U8_3(node->contents.quadrants[i], distance,
                                      found_color, c[0], c[1], c[2], base);
      }
    }
  }

  return distance;
}

/***************************************************************/
mlib_u32 mlib_search_quadrant_part_to_right_U8_3(struct lut_node_3 *node,
                                                 mlib_u32          distance,
                                                  mlib_s32    *found_color,
                                                 const mlib_u32    *c,
                                                 const mlib_u8     **base,
                                                 mlib_u32          position,
                                                 mlib_s32          pass,
                                                 mlib_s32          dir_bit)
{
  mlib_u32 current_size = 1 << pass;
  mlib_s32 i;
  static mlib_s32 opposite_quadrants[3][4] = {
    {1, 3, 5, 7},
    {2, 3, 6, 7},
    {4, 5, 6, 7}
  };

/* Search only quadrant's half untill it is necessary to check the
  whole quadrant */

  if (distance <= (c[dir_bit] - position - current_size) * (c[dir_bit] - position - current_size)) { /* Search half of quadrant */
    for (i = 0; i < 4; i++) {
      mlib_s32 qq = opposite_quadrants[dir_bit][i];

      if (node->tag & (1 << qq)) {
        /* Here is alone color cell. Check the distance */
        mlib_s32 newindex = node->contents.index[qq];
        mlib_u32 newpalc0, newpalc1, newpalc2;
        mlib_u32 newdistance;

        newpalc0 = base[0][newindex];
        newpalc1 = base[1][newindex];
        newpalc2 = base[2][newindex];
        newdistance = FIND_DISTANCE_3(c[0], newpalc0, c[1], newpalc1, c[2], newpalc2, 0);

        if (distance > newdistance) {
          *found_color = newindex;
          distance = newdistance;
        }
      }
      else if (node->contents.quadrants[qq])
        distance =
          mlib_search_quadrant_part_to_right_U8_3(node->contents.quadrants[qq],
                                                  distance, found_color, c,
                                                  base, position + current_size,
                                                  pass - 1, dir_bit);
    }
  }
  else {                                    /* Search whole quadrant */

    mlib_s32 mask = 1 << dir_bit;

    for (i = 0; i < 8; i++) {

      if (node->tag & (1 << i)) {
        /* Here is alone color cell. Check the distance */
        mlib_s32 newindex = node->contents.index[i];
        mlib_u32 newpalc0, newpalc1, newpalc2;
        mlib_u32 newdistance;

        newpalc0 = base[0][newindex];
        newpalc1 = base[1][newindex];
        newpalc2 = base[2][newindex];
        newdistance = FIND_DISTANCE_3(c[0], newpalc0, c[1], newpalc1, c[2], newpalc2, 0);

        if (distance > newdistance) {
          *found_color = newindex;
          distance = newdistance;
        }
      }
      else if (node->contents.quadrants[i]) {

        if (i & mask)
          /* Here we should check all */
          distance =
            mlib_search_quadrant_U8_3(node->contents.quadrants[i], distance,
                                      found_color, c[0], c[1], c[2], base);
        else
          /* This quadrant may require partial checking */
          distance =
            mlib_search_quadrant_part_to_right_U8_3(node->contents.quadrants[i],
                                                    distance, found_color, c,
                                                    base, position, pass - 1, dir_bit);
      }
    }
  }

  return distance;
}

/***************************************************************/
mlib_u32 mlib_search_quadrant_S16_3(struct lut_node_3 *node,
                                    mlib_u32          distance,
                                     mlib_s32    *found_color,
                                    mlib_u32          c0,
                                    mlib_u32          c1,
                                    mlib_u32          c2,
                                    const mlib_s16    **base)
{
  mlib_s32 i;

  for (i = 0; i < 8; i++) {

    if (node->tag & (1 << i)) {
      /* Here is alone color cell. Check the distance */
      mlib_s32 newindex = node->contents.index[i];
      mlib_u32 newpalc0, newpalc1, newpalc2;
      mlib_u32 newdistance;

      newpalc0 = base[0][newindex] - MLIB_S16_MIN;
      newpalc1 = base[1][newindex] - MLIB_S16_MIN;
      newpalc2 = base[2][newindex] - MLIB_S16_MIN;
      newdistance = FIND_DISTANCE_3(c0, newpalc0, c1, newpalc1, c2, newpalc2, 2);

      if (distance > newdistance) {
        *found_color = newindex;
        distance = newdistance;
      }
    }
    else if (node->contents.quadrants[i])
      distance =
        mlib_search_quadrant_S16_3(node->contents.quadrants[i], distance,
                                   found_color, c0, c1, c2, base);
  }

  return distance;
}

/***************************************************************/
mlib_u32 mlib_search_quadrant_part_to_left_S16_3(struct lut_node_3 *node,
                                                 mlib_u32          distance,
                                                  mlib_s32    *found_color,
                                                 const mlib_u32    *c,
                                                 const mlib_s16    **base,
                                                 mlib_u32          position,
                                                 mlib_s32          pass,
                                                 mlib_s32          dir_bit)
{
  mlib_u32 current_size = 1 << pass;
  mlib_s32 i;
  static mlib_s32 opposite_quadrants[3][4] = {
    {0, 2, 4, 6},
    {0, 1, 4, 5},
    {0, 1, 2, 3}
  };

/* Search only quadrant's half untill it is necessary to check the
  whole quadrant */

  if (distance < (((position + current_size - c[dir_bit]) * (position + current_size - c[dir_bit])) >> 2)) { /* Search half of quadrant */
    for (i = 0; i < 4; i++) {
      mlib_s32 qq = opposite_quadrants[dir_bit][i];

      if (node->tag & (1 << qq)) {
        /* Here is alone color cell. Check the distance */
        mlib_s32 newindex = node->contents.index[qq];
        mlib_u32 newpalc0, newpalc1, newpalc2;
        mlib_u32 newdistance;

        newpalc0 = base[0][newindex] - MLIB_S16_MIN;
        newpalc1 = base[1][newindex] - MLIB_S16_MIN;
        newpalc2 = base[2][newindex] - MLIB_S16_MIN;
        newdistance = FIND_DISTANCE_3(c[0], newpalc0, c[1], newpalc1, c[2], newpalc2, 2);

        if (distance > newdistance) {
          *found_color = newindex;
          distance = newdistance;
        }
      }
      else if (node->contents.quadrants[qq])
        distance =
          mlib_search_quadrant_part_to_left_S16_3(node->contents.quadrants[qq],
                                                  distance, found_color, c,
                                                  base, position, pass - 1, dir_bit);
    }
  }
  else {                                    /* Search whole quadrant */

    mlib_s32 mask = 1 << dir_bit;

    for (i = 0; i < 8; i++) {

      if (node->tag & (1 << i)) {
        /* Here is alone color cell. Check the distance */
        mlib_s32 newindex = node->contents.index[i];
        mlib_u32 newpalc0, newpalc1, newpalc2;
        mlib_u32 newdistance;

        newpalc0 = base[0][newindex] - MLIB_S16_MIN;
        newpalc1 = base[1][newindex] - MLIB_S16_MIN;
        newpalc2 = base[2][newindex] - MLIB_S16_MIN;
        newdistance = FIND_DISTANCE_3(c[0], newpalc0, c[1], newpalc1, c[2], newpalc2, 2);

        if (distance > newdistance) {
          *found_color = newindex;
          distance = newdistance;
        }
      }
      else if (node->contents.quadrants[i]) {

        if (i & mask)
          /* This quadrant may require partial checking */
          distance =
            mlib_search_quadrant_part_to_left_S16_3(node->contents.quadrants[i],
                                                    distance, found_color, c,
                                                    base,
                                                    position + current_size,
                                                    pass - 1, dir_bit);
        else
          /* Here we should check all */
          distance =
            mlib_search_quadrant_S16_3(node->contents.quadrants[i], distance,
                                       found_color, c[0], c[1], c[2], base);
      }
    }
  }

  return distance;
}

/***************************************************************/
mlib_u32 mlib_search_quadrant_part_to_right_S16_3(struct lut_node_3 *node,
                                                  mlib_u32          distance,
                                                   mlib_s32    *found_color,
                                                  const mlib_u32    *c,
                                                  const mlib_s16    **base,
                                                  mlib_u32          position,
                                                  mlib_s32          pass,
                                                  mlib_s32          dir_bit)
{
  mlib_u32 current_size = 1 << pass;
  mlib_s32 i;
  static mlib_s32 opposite_quadrants[3][4] = {
    {1, 3, 5, 7},
    {2, 3, 6, 7},
    {4, 5, 6, 7}
  };

/* Search only quadrant's half untill it is necessary to check the
  whole quadrant */

  if (distance <= (((c[dir_bit] - position - current_size) * (c[dir_bit] - position - current_size)) >> 2)) { /* Search half of quadrant */
    for (i = 0; i < 4; i++) {
      mlib_s32 qq = opposite_quadrants[dir_bit][i];

      if (node->tag & (1 << qq)) {
        /* Here is alone color cell. Check the distance */
        mlib_s32 newindex = node->contents.index[qq];
        mlib_u32 newpalc0, newpalc1, newpalc2;
        mlib_u32 newdistance;

        newpalc0 = base[0][newindex] - MLIB_S16_MIN;
        newpalc1 = base[1][newindex] - MLIB_S16_MIN;
        newpalc2 = base[2][newindex] - MLIB_S16_MIN;
        newdistance = FIND_DISTANCE_3(c[0], newpalc0, c[1], newpalc1, c[2], newpalc2, 2);

        if (distance > newdistance) {
          *found_color = newindex;
          distance = newdistance;
        }
      }
      else if (node->contents.quadrants[qq])
        distance =
          mlib_search_quadrant_part_to_right_S16_3(node->contents.quadrants[qq],
                                                   distance, found_color, c,
                                                   base,
                                                   position + current_size,
                                                   pass - 1, dir_bit);
    }
  }
  else {                                    /* Search whole quadrant */

    mlib_s32 mask = 1 << dir_bit;

    for (i = 0; i < 8; i++) {

      if (node->tag & (1 << i)) {
        /* Here is alone color cell. Check the distance */
        mlib_s32 newindex = node->contents.index[i];
        mlib_u32 newpalc0, newpalc1, newpalc2;
        mlib_u32 newdistance;

        newpalc0 = base[0][newindex] - MLIB_S16_MIN;
        newpalc1 = base[1][newindex] - MLIB_S16_MIN;
        newpalc2 = base[2][newindex] - MLIB_S16_MIN;
        newdistance = FIND_DISTANCE_3(c[0], newpalc0, c[1], newpalc1, c[2], newpalc2, 2);

        if (distance > newdistance) {
          *found_color = newindex;
          distance = newdistance;
        }
      }
      else if (node->contents.quadrants[i]) {

        if (i & mask)
          /* Here we should check all */
          distance =
            mlib_search_quadrant_S16_3(node->contents.quadrants[i], distance,
                                       found_color, c[0], c[1], c[2], base);
        else
          /* This quadrant may require partial checking */
          distance =
            mlib_search_quadrant_part_to_right_S16_3(node->contents.
                                                     quadrants[i], distance,
                                                     found_color, c, base,
                                                     position, pass - 1, dir_bit);
      }
    }
  }

  return distance;
}

/***************************************************************/
mlib_u32 mlib_search_quadrant_U8_4(struct lut_node_4 *node,
                                   mlib_u32          distance,
                                    mlib_s32    *found_color,
                                   mlib_u32          c0,
                                   mlib_u32          c1,
                                   mlib_u32          c2,
                                   mlib_u32          c3,
                                   const mlib_u8     **base)
{
  mlib_s32 i;

  for (i = 0; i < 16; i++) {

    if (node->tag & (1 << i)) {
      /* Here is alone color cell. Check the distance */
      mlib_s32 newindex = node->contents.index[i];
      mlib_u32 newpalc0, newpalc1, newpalc2, newpalc3;
      mlib_u32 newdistance;

      newpalc0 = base[0][newindex];
      newpalc1 = base[1][newindex];
      newpalc2 = base[2][newindex];
      newpalc3 = base[3][newindex];
      newdistance = FIND_DISTANCE_4(c0, newpalc0,
                                    c1, newpalc1, c2, newpalc2, c3, newpalc3, 0);

      if (distance > newdistance) {
        *found_color = newindex;
        distance = newdistance;
      }
    }
    else if (node->contents.quadrants[i])
      distance =
        mlib_search_quadrant_U8_4(node->contents.quadrants[i], distance,
                                  found_color, c0, c1, c2, c3, base);
  }

  return distance;
}

/***************************************************************/
mlib_u32 mlib_search_quadrant_part_to_left_U8_4(struct lut_node_4 *node,
                                                mlib_u32          distance,
                                                 mlib_s32    *found_color,
                                                const mlib_u32    *c,
                                                const mlib_u8     **base,
                                                mlib_u32          position,
                                                mlib_s32          pass,
                                                mlib_s32          dir_bit)
{
  mlib_u32 current_size = 1 << pass;
  mlib_s32 i;
  static mlib_s32 opposite_quadrants[4][8] = {
    {0, 2, 4, 6, 8, 10, 12, 14},
    {0, 1, 4, 5, 8, 9, 12, 13},
    {0, 1, 2, 3, 8, 9, 10, 11},
    {0, 1, 2, 3, 4, 5, 6, 7}
  };

/* Search only quadrant's half untill it is necessary to check the
  whole quadrant */

  if (distance < (position + current_size - c[dir_bit]) * (position + current_size - c[dir_bit])) { /* Search half of quadrant */
    for (i = 0; i < 8; i++) {
      mlib_s32 qq = opposite_quadrants[dir_bit][i];

      if (node->tag & (1 << qq)) {
        /* Here is alone color cell. Check the distance */
        mlib_s32 newindex = node->contents.index[qq];
        mlib_u32 newpalc0, newpalc1, newpalc2, newpalc3;
        mlib_u32 newdistance;

        newpalc0 = base[0][newindex];
        newpalc1 = base[1][newindex];
        newpalc2 = base[2][newindex];
        newpalc3 = base[3][newindex];
        newdistance = FIND_DISTANCE_4(c[0], newpalc0,
                                      c[1], newpalc1, c[2], newpalc2, c[3], newpalc3, 0);

        if (distance > newdistance) {
          *found_color = newindex;
          distance = newdistance;
        }
      }
      else if (node->contents.quadrants[qq])
        distance =
          mlib_search_quadrant_part_to_left_U8_4(node->contents.quadrants[qq],
                                                 distance, found_color, c, base,
                                                 position, pass - 1, dir_bit);
    }
  }
  else {                                    /* Search whole quadrant */

    mlib_s32 mask = 1 << dir_bit;

    for (i = 0; i < 16; i++) {

      if (node->tag & (1 << i)) {
        /* Here is alone color cell. Check the distance */
        mlib_s32 newindex = node->contents.index[i];
        mlib_u32 newpalc0, newpalc1, newpalc2, newpalc3;
        mlib_u32 newdistance;

        newpalc0 = base[0][newindex];
        newpalc1 = base[1][newindex];
        newpalc2 = base[2][newindex];
        newpalc3 = base[3][newindex];
        newdistance = FIND_DISTANCE_4(c[0], newpalc0,
                                      c[1], newpalc1, c[2], newpalc2, c[3], newpalc3, 0);

        if (distance > newdistance) {
          *found_color = newindex;
          distance = newdistance;
        }
      }
      else if (node->contents.quadrants[i]) {

        if (i & mask)
          /* This quadrant may require partial checking */
          distance =
            mlib_search_quadrant_part_to_left_U8_4(node->contents.quadrants[i],
                                                   distance, found_color, c,
                                                   base,
                                                   position + current_size,
                                                   pass - 1, dir_bit);
        else
          /* Here we should check all */
          distance =
            mlib_search_quadrant_U8_4(node->contents.quadrants[i], distance,
                                      found_color, c[0], c[1], c[2], c[3], base);
      }
    }
  }

  return distance;
}

/***************************************************************/
mlib_u32 mlib_search_quadrant_part_to_right_U8_4(struct lut_node_4 *node,
                                                 mlib_u32          distance,
                                                  mlib_s32    *found_color,
                                                 const mlib_u32    *c,
                                                 const mlib_u8     **base,
                                                 mlib_u32          position,
                                                 mlib_s32          pass,
                                                 mlib_s32          dir_bit)
{
  mlib_u32 current_size = 1 << pass;
  mlib_s32 i;
  static mlib_s32 opposite_quadrants[4][8] = {
    {1, 3, 5, 7, 9, 11, 13, 15},
    {2, 3, 6, 7, 10, 11, 14, 15},
    {4, 5, 6, 7, 12, 13, 14, 15},
    {8, 9, 10, 11, 12, 13, 14, 15}
  };

/* Search only quadrant's half untill it is necessary to check the
  whole quadrant */

  if (distance <= (c[dir_bit] - position - current_size) * (c[dir_bit] - position - current_size)) { /* Search half of quadrant */
    for (i = 0; i < 8; i++) {
      mlib_s32 qq = opposite_quadrants[dir_bit][i];

      if (node->tag & (1 << qq)) {
        /* Here is alone color cell. Check the distance */
        mlib_s32 newindex = node->contents.index[qq];
        mlib_u32 newpalc0, newpalc1, newpalc2, newpalc3;
        mlib_u32 newdistance;

        newpalc0 = base[0][newindex];
        newpalc1 = base[1][newindex];
        newpalc2 = base[2][newindex];
        newpalc3 = base[3][newindex];
        newdistance = FIND_DISTANCE_4(c[0], newpalc0,
                                      c[1], newpalc1, c[2], newpalc2, c[3], newpalc3, 0);

        if (distance > newdistance) {
          *found_color = newindex;
          distance = newdistance;
        }
      }
      else if (node->contents.quadrants[qq])
        distance =
          mlib_search_quadrant_part_to_right_U8_4(node->contents.quadrants[qq],
                                                  distance, found_color, c,
                                                  base, position + current_size,
                                                  pass - 1, dir_bit);
    }
  }
  else {                                    /* Search whole quadrant */

    mlib_s32 mask = 1 << dir_bit;

    for (i = 0; i < 16; i++) {

      if (node->tag & (1 << i)) {
        /* Here is alone color cell. Check the distance */
        mlib_s32 newindex = node->contents.index[i];
        mlib_u32 newpalc0, newpalc1, newpalc2, newpalc3;
        mlib_u32 newdistance;

        newpalc0 = base[0][newindex];
        newpalc1 = base[1][newindex];
        newpalc2 = base[2][newindex];
        newpalc3 = base[3][newindex];
        newdistance = FIND_DISTANCE_4(c[0], newpalc0,
                                      c[1], newpalc1, c[2], newpalc2, c[3], newpalc3, 0);

        if (distance > newdistance) {
          *found_color = newindex;
          distance = newdistance;
        }
      }
      else if (node->contents.quadrants[i]) {

        if (i & mask)
          /* Here we should check all */
          distance =
            mlib_search_quadrant_U8_4(node->contents.quadrants[i], distance,
                                      found_color, c[0], c[1], c[2], c[3], base);
        else
          /* This quadrant may require partial checking */
          distance =
            mlib_search_quadrant_part_to_right_U8_4(node->contents.quadrants[i],
                                                    distance, found_color, c,
                                                    base, position, pass - 1, dir_bit);
      }
    }
  }

  return distance;
}

/***************************************************************/
mlib_u32 mlib_search_quadrant_S16_4(struct lut_node_4 *node,
                                    mlib_u32          distance,
                                     mlib_s32    *found_color,
                                    mlib_u32          c0,
                                    mlib_u32          c1,
                                    mlib_u32          c2,
                                    mlib_u32          c3,
                                    const mlib_s16    **base)
{
  mlib_s32 i;

  for (i = 0; i < 16; i++) {

    if (node->tag & (1 << i)) {
      /* Here is alone color cell. Check the distance */
      mlib_s32 newindex = node->contents.index[i];
      mlib_u32 newpalc0, newpalc1, newpalc2, newpalc3;
      mlib_u32 newdistance;

      newpalc0 = base[0][newindex] - MLIB_S16_MIN;
      newpalc1 = base[1][newindex] - MLIB_S16_MIN;
      newpalc2 = base[2][newindex] - MLIB_S16_MIN;
      newpalc3 = base[3][newindex] - MLIB_S16_MIN;
      newdistance = FIND_DISTANCE_4(c0, newpalc0,
                                    c1, newpalc1, c2, newpalc2, c3, newpalc3, 2);

      if (distance > newdistance) {
        *found_color = newindex;
        distance = newdistance;
      }
    }
    else if (node->contents.quadrants[i])
      distance =
        mlib_search_quadrant_S16_4(node->contents.quadrants[i], distance,
                                   found_color, c0, c1, c2, c3, base);
  }

  return distance;
}

/***************************************************************/
mlib_u32 mlib_search_quadrant_part_to_left_S16_4(struct lut_node_4 *node,
                                                 mlib_u32          distance,
                                                  mlib_s32    *found_color,
                                                 const mlib_u32    *c,
                                                 const mlib_s16    **base,
                                                 mlib_u32          position,
                                                 mlib_s32          pass,
                                                 mlib_s32          dir_bit)
{
  mlib_u32 current_size = 1 << pass;
  mlib_s32 i;
  static mlib_s32 opposite_quadrants[4][8] = {
    {0, 2, 4, 6, 8, 10, 12, 14},
    {0, 1, 4, 5, 8, 9, 12, 13},
    {0, 1, 2, 3, 8, 9, 10, 11},
    {0, 1, 2, 3, 4, 5, 6, 7}
  };

/* Search only quadrant's half untill it is necessary to check the
  whole quadrant */

  if (distance < (((position + current_size - c[dir_bit]) * (position + current_size - c[dir_bit])) >> 2)) { /* Search half of quadrant */
    for (i = 0; i < 8; i++) {
      mlib_s32 qq = opposite_quadrants[dir_bit][i];

      if (node->tag & (1 << qq)) {
        /* Here is alone color cell. Check the distance */
        mlib_s32 newindex = node->contents.index[qq];
        mlib_u32 newpalc0, newpalc1, newpalc2, newpalc3;
        mlib_u32 newdistance;

        newpalc0 = base[0][newindex] - MLIB_S16_MIN;
        newpalc1 = base[1][newindex] - MLIB_S16_MIN;
        newpalc2 = base[2][newindex] - MLIB_S16_MIN;
        newpalc3 = base[3][newindex] - MLIB_S16_MIN;
        newdistance = FIND_DISTANCE_4(c[0], newpalc0,
                                      c[1], newpalc1, c[2], newpalc2, c[3], newpalc3, 2);

        if (distance > newdistance) {
          *found_color = newindex;
          distance = newdistance;
        }
      }
      else if (node->contents.quadrants[qq])
        distance =
          mlib_search_quadrant_part_to_left_S16_4(node->contents.quadrants[qq],
                                                  distance, found_color, c,
                                                  base, position, pass - 1, dir_bit);
    }
  }
  else {                                    /* Search whole quadrant */

    mlib_s32 mask = 1 << dir_bit;

    for (i = 0; i < 16; i++) {

      if (node->tag & (1 << i)) {
        /* Here is alone color cell. Check the distance */
        mlib_s32 newindex = node->contents.index[i];
        mlib_u32 newpalc0, newpalc1, newpalc2, newpalc3;
        mlib_u32 newdistance;

        newpalc0 = base[0][newindex] - MLIB_S16_MIN;
        newpalc1 = base[1][newindex] - MLIB_S16_MIN;
        newpalc2 = base[2][newindex] - MLIB_S16_MIN;
        newpalc3 = base[3][newindex] - MLIB_S16_MIN;
        newdistance = FIND_DISTANCE_4(c[0], newpalc0,
                                      c[1], newpalc1, c[2], newpalc2, c[3], newpalc3, 2);

        if (distance > newdistance) {
          *found_color = newindex;
          distance = newdistance;
        }
      }
      else if (node->contents.quadrants[i]) {

        if (i & mask)
          /* This quadrant may require partial checking */
          distance =
            mlib_search_quadrant_part_to_left_S16_4(node->contents.quadrants[i],
                                                    distance, found_color, c,
                                                    base,
                                                    position + current_size,
                                                    pass - 1, dir_bit);
        else
          /* Here we should check all */
          distance =
            mlib_search_quadrant_S16_4(node->contents.quadrants[i], distance,
                                       found_color, c[0], c[1], c[2], c[3], base);
      }
    }
  }

  return distance;
}

/***************************************************************/
mlib_u32 mlib_search_quadrant_part_to_right_S16_4(struct lut_node_4 *node,
                                                  mlib_u32          distance,
                                                   mlib_s32    *found_color,
                                                  const mlib_u32    *c,
                                                  const mlib_s16    **base,
                                                  mlib_u32          position,
                                                  mlib_s32          pass,
                                                  mlib_s32          dir_bit)
{
  mlib_u32 current_size = 1 << pass;
  mlib_s32 i;
  static mlib_s32 opposite_quadrants[4][8] = {
    {1, 3, 5, 7, 9, 11, 13, 15},
    {2, 3, 6, 7, 10, 11, 14, 15},
    {4, 5, 6, 7, 12, 13, 14, 15},
    {8, 9, 10, 11, 12, 13, 14, 15}
  };

/* Search only quadrant's half untill it is necessary to check the
  whole quadrant */

  if (distance <= (((c[dir_bit] - position - current_size) * (c[dir_bit] - position - current_size)) >> 2)) { /* Search half of quadrant */
    for (i = 0; i < 8; i++) {
      mlib_s32 qq = opposite_quadrants[dir_bit][i];

      if (node->tag & (1 << qq)) {
        /* Here is alone color cell. Check the distance */
        mlib_s32 newindex = node->contents.index[qq];
        mlib_u32 newpalc0, newpalc1, newpalc2, newpalc3;
        mlib_u32 newdistance;

        newpalc0 = base[0][newindex] - MLIB_S16_MIN;
        newpalc1 = base[1][newindex] - MLIB_S16_MIN;
        newpalc2 = base[2][newindex] - MLIB_S16_MIN;
        newpalc3 = base[3][newindex] - MLIB_S16_MIN;
        newdistance = FIND_DISTANCE_4(c[0], newpalc0,
                                      c[1], newpalc1, c[2], newpalc2, c[3], newpalc3, 2);

        if (distance > newdistance) {
          *found_color = newindex;
          distance = newdistance;
        }
      }
      else if (node->contents.quadrants[qq])
        distance =
          mlib_search_quadrant_part_to_right_S16_4(node->contents.quadrants[qq],
                                                   distance, found_color, c,
                                                   base,
                                                   position + current_size,
                                                   pass - 1, dir_bit);
    }
  }
  else {                                    /* Search whole quadrant */

    mlib_s32 mask = 1 << dir_bit;

    for (i = 0; i < 16; i++) {

      if (node->tag & (1 << i)) {
        /* Here is alone color cell. Check the distance */
        mlib_s32 newindex = node->contents.index[i];
        mlib_u32 newpalc0, newpalc1, newpalc2, newpalc3;
        mlib_u32 newdistance;

        newpalc0 = base[0][newindex] - MLIB_S16_MIN;
        newpalc1 = base[1][newindex] - MLIB_S16_MIN;
        newpalc2 = base[2][newindex] - MLIB_S16_MIN;
        newpalc3 = base[3][newindex] - MLIB_S16_MIN;
        newdistance = FIND_DISTANCE_4(c[0], newpalc0,
                                      c[1], newpalc1, c[2], newpalc2, c[3], newpalc3, 2);

        if (distance > newdistance) {
          *found_color = newindex;
          distance = newdistance;
        }
      }
      else if (node->contents.quadrants[i]) {

        if (i & mask)
          /* Here we should check all */
          distance =
            mlib_search_quadrant_S16_4(node->contents.quadrants[i], distance,
                                       found_color, c[0], c[1], c[2], c[3], base);
        else
          /* This quadrant may require partial checking */
          distance =
            mlib_search_quadrant_part_to_right_S16_4(node->contents.
                                                     quadrants[i], distance,
                                                     found_color, c, base,
                                                     position, pass - 1, dir_bit);
      }
    }
  }

  return distance;
}

/***************************************************************/

#define TAB_SIZE_mlib_u8   256
#define TAB_SIZE_mlib_s16 1024

#define SRC_mlib_u8(i)    src[i]
#define SRC_mlib_s16(i)   (((mlib_u16*)src)[i] >> 6)

/***************************************************************/

#define DIMENSIONS_SEARCH_3(STYPE, DTYPE, STEP)                 \
{                                                               \
  DTYPE  *tab0 = ((mlib_colormap *)state)->table;               \
  DTYPE  *tab1 = tab0 + TAB_SIZE_##STYPE;                       \
  DTYPE  *tab2 = tab1 + TAB_SIZE_##STYPE;                       \
  mlib_s32 i;                                                   \
                                                                \
  for (i = 0; i < length; i++) {                                \
    dst[i] = tab0[SRC_##STYPE(0)] + tab1[SRC_##STYPE(1)] +      \
             tab2[SRC_##STYPE(2)];                              \
    src += STEP;                                                \
  }                                                             \
}

/***************************************************************/

#define DIMENSIONS_SEARCH_4(STYPE, DTYPE)                       \
{                                                               \
  DTYPE  *tab0 = ((mlib_colormap *)state)->table;               \
  DTYPE  *tab1 = tab0 + TAB_SIZE_##STYPE;                       \
  DTYPE  *tab2 = tab1 + TAB_SIZE_##STYPE;                       \
  DTYPE  *tab3 = tab2 + TAB_SIZE_##STYPE;                       \
  mlib_s32 i;                                                   \
                                                                \
  for (i = 0; i < length; i++) {                                \
    dst[i] = tab0[SRC_##STYPE(0)] + tab1[SRC_##STYPE(1)] +      \
             tab2[SRC_##STYPE(2)] + tab3[SRC_##STYPE(3)];       \
    src += 4;                                                   \
  }                                                             \
}

/***************************************************************/
void mlib_ImageColorTrue2IndexLine_U8_U8_3(const mlib_u8 *src,
                                           mlib_u8       *dst,
                                           mlib_s32      length,
                                           const void    *state)
{
  mlib_colormap *s = (mlib_colormap *)state;

  switch (s->method) {
#if LUT_BYTE_COLORS_3CHANNELS <= 256
    case LUT_BINARY_TREE_SEARCH:
      {
        mlib_s32 bits = s->bits;
        BINARY_TREE_SEARCH_3(U8, mlib_u8, 8, (MLIB_U8_MAX + 1), 0, 0, 3, 0);
      }
      break;

#endif /* LUT_BYTE_COLORS_3CHANNELS <= 256 */
    case LUT_COLOR_CUBE_SEARCH:
      {
        COLOR_CUBE_U8_3_SEARCH(mlib_u8, 0, 3);
      }
      break;

    case LUT_STUPID_SEARCH:
      {
#ifdef USE_VIS_CODE
        FIND_NEAREST_U8_3;
#else
        FIND_NEAREST_U8_3_C(0, 3);
#endif
      }
      break;

    case LUT_COLOR_DIMENSIONS:
      DIMENSIONS_SEARCH_3(mlib_u8, mlib_u8, 3)
      break;
  }
}

/***************************************************************/
void mlib_ImageColorTrue2IndexLine_U8_U8_3_in_4(const mlib_u8 *src,
                                                mlib_u8       *dst,
                                                mlib_s32      length,
                                                const void    *state)
{
  mlib_colormap *s = (mlib_colormap *)state;

  switch (s->method) {
#if LUT_BYTE_COLORS_3CHANNELS <= 256
    case LUT_BINARY_TREE_SEARCH:
      {
        mlib_s32 bits = s->bits;
        BINARY_TREE_SEARCH_3(U8, mlib_u8, 8, (MLIB_U8_MAX + 1), 0, 1, 4, 0);
        break;
      }

#endif /* LUT_BYTE_COLORS_3CHANNELS <= 256 */
    case LUT_COLOR_CUBE_SEARCH:
      {
        COLOR_CUBE_U8_3_SEARCH(mlib_u8, 1, 4);
        break;
      }

    case LUT_STUPID_SEARCH:
      {
#ifdef USE_VIS_CODE
        FIND_NEAREST_U8_3_IN4;
#else
        FIND_NEAREST_U8_3_C(1, 4);
#endif
        break;
      }

    case LUT_COLOR_DIMENSIONS:
      src++;
      DIMENSIONS_SEARCH_3(mlib_u8, mlib_u8, 4)
      break;
  }
}

/***************************************************************/
void mlib_ImageColorTrue2IndexLine_U8_U8_4(const mlib_u8 *src,
                                           mlib_u8       *dst,
                                           mlib_s32      length,
                                           const void    *state)
{
  mlib_colormap *s = (mlib_colormap *)state;

  switch (s->method) {
#if LUT_BYTE_COLORS_4CHANNELS <= 256
    case LUT_BINARY_TREE_SEARCH:
      {
        mlib_s32 bits = s->bits;
        BINARY_TREE_SEARCH_4(U8, mlib_u8, 8, (MLIB_U8_MAX + 1), 0, 0);
        break;
      }

#endif /* LUT_BYTE_COLORS_4CHANNELS <= 256 */
    case LUT_COLOR_CUBE_SEARCH:
      {
        COLOR_CUBE_U8_4_SEARCH(mlib_u8);
        break;
      }

    case LUT_STUPID_SEARCH:
      {
#ifdef USE_VIS_CODE
        FIND_NEAREST_U8_4;
#else
        FIND_NEAREST_U8_4_C;
#endif
        break;
      }

    case LUT_COLOR_DIMENSIONS:
      DIMENSIONS_SEARCH_4(mlib_u8, mlib_u8)
      break;
  }
}

/***************************************************************/
void mlib_ImageColorTrue2IndexLine_U8_S16_3(const mlib_u8 *src,
                                            mlib_s16      *dst,
                                            mlib_s32      length,
                                            const void    *state)
{
  mlib_colormap *s = (mlib_colormap *)state;
  mlib_s32 bits = s->bits;

  switch (s->method) {
    case LUT_BINARY_TREE_SEARCH:
      {
        BINARY_TREE_SEARCH_3(U8, mlib_u8, 8, (MLIB_U8_MAX + 1), 0, 0, 3, 0);
        break;
      }

    case LUT_COLOR_CUBE_SEARCH:
      {
        switch (s->indexsize) {
          case 1:
            {
              COLOR_CUBE_U8_3_SEARCH(mlib_u8, 0, 3);
              break;
            }

          case 2:
            {
              COLOR_CUBE_U8_3_SEARCH(mlib_s16, 0, 3);
              break;
            }
        }

        break;
      }

    case LUT_STUPID_SEARCH:
      {
#ifdef USE_VIS_CODE
        FIND_NEAREST_U8_3;
#else
        FIND_NEAREST_U8_3_C(0, 3);
#endif
        break;
      }

    case LUT_COLOR_DIMENSIONS:
      DIMENSIONS_SEARCH_3(mlib_u8, mlib_s16, 3)
      break;
  }
}

/***************************************************************/
void mlib_ImageColorTrue2IndexLine_U8_S16_3_in_4(const mlib_u8 *src,
                                                 mlib_s16      *dst,
                                                 mlib_s32      length,
                                                 const void    *state)
{
  mlib_colormap *s = (mlib_colormap *)state;
  mlib_s32 bits = s->bits;

  switch (s->method) {
    case LUT_BINARY_TREE_SEARCH:
      {
        BINARY_TREE_SEARCH_3(U8, mlib_u8, 8, (MLIB_U8_MAX + 1), 0, 1, 4, 0);
        break;
      }

    case LUT_COLOR_CUBE_SEARCH:
      {
        switch (s->indexsize) {
          case 1:
            {
              COLOR_CUBE_U8_3_SEARCH(mlib_u8, 1, 4);
              break;
            }

          case 2:
            {
              COLOR_CUBE_U8_3_SEARCH(mlib_s16, 1, 4);
              break;
            }
        }

        break;
      }

    case LUT_STUPID_SEARCH:
      {
#ifdef USE_VIS_CODE
        FIND_NEAREST_U8_3_IN4;
#else
        FIND_NEAREST_U8_3_C(1, 4);
#endif
        break;
      }

    case LUT_COLOR_DIMENSIONS:
      src++;
      DIMENSIONS_SEARCH_3(mlib_u8, mlib_s16, 4)
      break;
  }
}

/***************************************************************/
void mlib_ImageColorTrue2IndexLine_U8_S16_4(const mlib_u8 *src,
                                            mlib_s16      *dst,
                                            mlib_s32      length,
                                            const void    *state)
{
  mlib_colormap *s = (mlib_colormap *)state;
  mlib_s32 bits = s->bits;

  switch (s->method) {
    case LUT_BINARY_TREE_SEARCH:
      {
        BINARY_TREE_SEARCH_4(U8, mlib_u8, 8, (MLIB_U8_MAX + 1), 0, 0);
        break;
      }

    case LUT_COLOR_CUBE_SEARCH:
      {
        switch (s->indexsize) {
          case 1:
            {
              COLOR_CUBE_U8_4_SEARCH(mlib_u8);
              break;
            }

          case 2:
            {
              COLOR_CUBE_U8_4_SEARCH(mlib_s16);
              break;
            }
        }

        break;
      }

    case LUT_STUPID_SEARCH:
      {
#ifdef USE_VIS_CODE
        FIND_NEAREST_U8_4;
#else
        FIND_NEAREST_U8_4_C;
#endif
        break;
      }

    case LUT_COLOR_DIMENSIONS:
      DIMENSIONS_SEARCH_4(mlib_u8, mlib_s16)
      break;
  }
}

/***************************************************************/
void mlib_ImageColorTrue2IndexLine_S16_S16_3(const mlib_s16 *src,
                                             mlib_s16       *dst,
                                             mlib_s32       length,
                                             const void     *state)
{
  mlib_colormap *s = (mlib_colormap *)state;
  mlib_s32 bits = s->bits;

  switch (s->method) {
    case LUT_BINARY_TREE_SEARCH:
      {
        BINARY_TREE_SEARCH_3(S16, mlib_s16, 16, ((MLIB_S16_MAX + 1) * 2),
                             MLIB_S16_MIN, 0, 3, 2);
        break;
      }

    case LUT_COLOR_CUBE_SEARCH:
      {
        switch (s->indexsize) {
          case 1:
            {
              COLOR_CUBE_S16_3_SEARCH(mlib_u8, 0, 3);
              break;
            }

          case 2:
            {
              COLOR_CUBE_S16_3_SEARCH(mlib_s16, 0, 3);
              break;
            }
        }

        break;
      }

    case LUT_STUPID_SEARCH:
      {
        FIND_NEAREST_S16_3(0, 3);
        break;
      }

    case LUT_COLOR_DIMENSIONS:
      DIMENSIONS_SEARCH_3(mlib_s16, mlib_s16, 3)
      break;
  }
}

/***************************************************************/
void mlib_ImageColorTrue2IndexLine_S16_S16_3_in_4(const mlib_s16 *src,
                                                  mlib_s16       *dst,
                                                  mlib_s32       length,
                                                  const void     *state)
{
  mlib_colormap *s = (mlib_colormap *)state;
  mlib_s32 bits = s->bits;

  switch (s->method) {
    case LUT_BINARY_TREE_SEARCH:
      {
        BINARY_TREE_SEARCH_3(S16, mlib_s16, 16, ((MLIB_S16_MAX + 1) * 2),
                             MLIB_S16_MIN, 1, 4, 2);
        break;
      }

    case LUT_COLOR_CUBE_SEARCH:
      {
        switch (s->indexsize) {
          case 1:
            {
              COLOR_CUBE_S16_3_SEARCH(mlib_u8, 1, 4);
              break;
            }

          case 2:
            {
              COLOR_CUBE_S16_3_SEARCH(mlib_s16, 1, 4);
              break;
            }
        }

        break;
      }

    case LUT_STUPID_SEARCH:
      {
        FIND_NEAREST_S16_3(1, 4);
        break;
      }

    case LUT_COLOR_DIMENSIONS:
      src++;
      DIMENSIONS_SEARCH_3(mlib_s16, mlib_s16, 4)
      break;
  }
}

/***************************************************************/
void mlib_ImageColorTrue2IndexLine_S16_S16_4(const mlib_s16 *src,
                                             mlib_s16       *dst,
                                             mlib_s32       length,
                                             const void     *state)
{
  mlib_colormap *s = (mlib_colormap *)state;
  mlib_s32 bits = s->bits;

  switch (s->method) {
    case LUT_BINARY_TREE_SEARCH:
      {
        BINARY_TREE_SEARCH_4(S16, mlib_s16, 16, ((MLIB_S16_MAX + 1) * 2),
                             MLIB_S16_MIN, 2);
        break;
      }

    case LUT_COLOR_CUBE_SEARCH:
      {
        switch (s->indexsize) {
          case 1:
            {
              COLOR_CUBE_S16_4_SEARCH(mlib_u8);
              break;
            }

          case 2:
            {
              COLOR_CUBE_S16_4_SEARCH(mlib_s16);
              break;
            }
        }

        break;
      }

    case LUT_STUPID_SEARCH:
      {
        FIND_NEAREST_S16_4;
        break;
      }

    case LUT_COLOR_DIMENSIONS:
      DIMENSIONS_SEARCH_4(mlib_s16, mlib_s16)
      break;
  }
}

/***************************************************************/
void mlib_ImageColorTrue2IndexLine_S16_U8_3(const mlib_s16 *src,
                                            mlib_u8        *dst,
                                            mlib_s32       length,
                                            const void     *state)
{
  mlib_colormap *s = (mlib_colormap *)state;

  switch (s->method) {
#if LUT_SHORT_COLORS_3CHANNELS <= 256
    case LUT_BINARY_TREE_SEARCH:
      {
        mlib_s32 bits = s->bits;
        BINARY_TREE_SEARCH_3(S16, mlib_s16, 16, ((MLIB_S16_MAX + 1) * 2),
                             MLIB_S16_MIN, 0, 3, 2);
        break;
      }

#endif /* LUT_SHORT_COLORS_3CHANNELS <= 256 */
    case LUT_COLOR_CUBE_SEARCH:
      {
        COLOR_CUBE_S16_3_SEARCH(mlib_u8, 0, 3);
        break;
      }

    case LUT_STUPID_SEARCH:
      {
        FIND_NEAREST_S16_3(0, 3);
        break;
      }

    case LUT_COLOR_DIMENSIONS:
      DIMENSIONS_SEARCH_3(mlib_s16, mlib_u8, 3)
      break;
  }
}

/***************************************************************/
void mlib_ImageColorTrue2IndexLine_S16_U8_3_in_4(const mlib_s16 *src,
                                                 mlib_u8        *dst,
                                                 mlib_s32       length,
                                                 const void     *state)
{
  mlib_colormap *s = (mlib_colormap *)state;

  switch (s->method) {
#if LUT_SHORT_COLORS_3CHANNELS <= 256
    case LUT_BINARY_TREE_SEARCH:
      {
        mlib_s32 bits = s->bits;
        BINARY_TREE_SEARCH_3(S16, mlib_s16, 16, ((MLIB_S16_MAX + 1) * 2),
                             MLIB_S16_MIN, 1, 4, 2);
        break;
      }

#endif /* LUT_SHORT_COLORS_3CHANNELS <= 256 */
    case LUT_COLOR_CUBE_SEARCH:
      {
        COLOR_CUBE_S16_3_SEARCH(mlib_u8, 1, 4);
        break;
      }

    case LUT_STUPID_SEARCH:
      {
        FIND_NEAREST_S16_3(1, 4);
        break;
      }

    case LUT_COLOR_DIMENSIONS:
      src++;
      DIMENSIONS_SEARCH_3(mlib_s16, mlib_u8, 4)
      break;
  }
}

/***************************************************************/
void mlib_ImageColorTrue2IndexLine_S16_U8_4(const mlib_s16 *src,
                                            mlib_u8        *dst,
                                            mlib_s32       length,
                                            const void     *state)
{
  mlib_colormap *s = (mlib_colormap *)state;

  switch (s->method) {
#if LUT_SHORT_COLORS_4CHANNELS <= 256
    case LUT_BINARY_TREE_SEARCH:
      {
        mlib_s32 bits = s->bits;
        BINARY_TREE_SEARCH_4(S16, mlib_s16, 16, ((MLIB_S16_MAX + 1) * 2),
                             MLIB_S16_MIN, 2);
        break;
      }

#endif /* LUT_SHORT_COLORS_4CHANNELS <= 256 */
    case LUT_COLOR_CUBE_SEARCH:
      {
        COLOR_CUBE_S16_4_SEARCH(mlib_u8);
        break;
      }

    case LUT_STUPID_SEARCH:
      {
        FIND_NEAREST_S16_4;
        break;
      }

    case LUT_COLOR_DIMENSIONS:
      DIMENSIONS_SEARCH_4(mlib_s16, mlib_u8)
      break;
  }
}

/***************************************************************/

#ifndef VIS

void mlib_c_ImageThresh1_U81_1B(void     *psrc,
                                void     *pdst,
                                mlib_s32 src_stride,
                                mlib_s32 dst_stride,
                                mlib_s32 width,
                                mlib_s32 height,
                                void     *thresh,
                                void     *ghigh,
                                void     *glow,
                                mlib_s32 dbit_off);

/***************************************************************/

void mlib_ImageColorTrue2IndexLine_U8_BIT_1(const mlib_u8 *src,
                                            mlib_u8       *dst,
                                            mlib_s32      bit_offset,
                                            mlib_s32      length,
                                            const void    *state)
{
  mlib_u8  *lut = ((mlib_colormap *)state)->table;
  mlib_s32 thresh[1];
  mlib_s32 ghigh[1];
  mlib_s32 glow[1];

  thresh[0] = lut[2];

  glow[0]  = lut[0] - lut[1];
  ghigh[0] = lut[1] - lut[0];

  mlib_c_ImageThresh1_U81_1B((void*)src, dst, 0, 0, length, 1,
                             thresh, ghigh, glow, bit_offset);
}

#else

/***************************************************************/

void mlib_v_ImageThresh1B_U8_1(const mlib_u8  *src,
                               mlib_s32       slb,
                               mlib_u8        *dst,
                               mlib_s32       dlb,
                               mlib_s32       xsize,
                               mlib_s32       ysize,
                               mlib_s32       dbit_off,
                               const mlib_s32 *th,
                               mlib_s32       hc,
                               mlib_s32       lc);

/***************************************************************/

void mlib_ImageColorTrue2IndexLine_U8_BIT_1(const mlib_u8 *src,
                                            mlib_u8       *dst,
                                            mlib_s32      bit_offset,
                                            mlib_s32      length,
                                            const void    *state)
{
  mlib_u8  *lut = ((mlib_colormap *)state)->table;
  mlib_s32 thresh[4];
  mlib_s32 ghigh[1];
  mlib_s32 glow[1];

  thresh[0] = thresh[1] = thresh[2] = thresh[3] = lut[2];

  glow[0]  = (lut[1] < lut[0]) ? 0xFF : 0;
  ghigh[0] = (lut[1] < lut[0]) ? 0 : 0xFF;

  mlib_v_ImageThresh1B_U8_1((void*)src, 0, dst, 0, length, 1,
                            bit_offset, thresh, ghigh[0], glow[0]);
}

/***************************************************************/

#endif
