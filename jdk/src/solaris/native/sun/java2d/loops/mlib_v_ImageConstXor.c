/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */


/*
 * FUNCTION
 *      mlib_ImageConstXor - image logical operation with constant
 *
 * SYNOPSIS
 *      mlib_status mlib_ImageConstXor(mlib_image       *dst,
 *                                     const mlib_image *src,
 *                                     const mlib_s32   *c);
 *
 * ARGUMENT
 *      dst     Pointer to destination image
 *      src     Pointer to source image
 *      c       Array of constants for each channel
 *
 * RESTRICTION
 *      The src and dst must be the same type and the same size.
 *      They can have 1, 2, 3, or 4 channels.
 *      They can be in MLIB_BIT, MLIB_BYTE, MLIB_SHORT, MLIB_USHORT or MLIB_INT
 *      data type.
 *
 * DESCRIPTION
 *      File for one of the following operations:
 *
 *      And  dst(i,j) = c & src(i,j)
 *      Or  dst(i,j) = c | src(i,j)
 *      Xor  dst(i,j) = c ^ src(i,j)
 *      NotAnd  dst(i,j) = ~(c & src(i,j))
 *      NotOr  dst(i,j) = ~(c | src(i,j))
 *      NotXor  dst(i,j) = ~(c ^ src(i,j))
 *      AndNot  dst(i,j) = c & (~src(i,j))
 *      OrNot  dst(i,j) = c & (~src(i,j))
 */

#include <mlib_image.h>

/***************************************************************/

#if ! defined ( __MEDIALIB_OLD_NAMES )
#if defined ( __SUNPRO_C )

#pragma weak mlib_ImageConstXor = __mlib_ImageConstXor

#elif defined ( __GNUC__ ) /* defined ( __SUNPRO_C ) */
  __typeof__ (__mlib_ImageConstXor) mlib_ImageConstXor
    __attribute__ ((weak,alias("__mlib_ImageConstXor")));

#else /* defined ( __SUNPRO_C ) */

#error  "unknown platform"

#endif /* defined ( __SUNPRO_C ) */
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */

/***************************************************************/

#define VIS_CONSTLOGIC(c, a) vis_fxor(a, c)

#include <mlib_v_ImageConstLogic.h>

/***************************************************************/

mlib_status __mlib_ImageConstXor(mlib_image *dst,
                                 mlib_image *src,
                                 mlib_s32   *c)
{
  return mlib_v_ImageConstLogic(dst, src, c);
}

/***************************************************************/
