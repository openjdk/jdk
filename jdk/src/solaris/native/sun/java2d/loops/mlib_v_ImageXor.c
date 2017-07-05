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
 * FUNCTION
 *      mlib_ImageXor      - xor two images    (VIS version)
 *
 * SYNOPSIS
 *      mlib_status mlib_ImageXor(mlib_image       *dst,
 *                                const mlib_image *src1,
 *                                const mlib_image *src2);
 *
 * ARGUMENT
 *      dst     pointer to destination image
 *      src1    pointer to first source image
 *      src2    pointer to second source image
 *
 * RESTRICTION
 *      The src1, src2, and dst must be the same type and the same dsize.
 *      They can have 1, 2, 3, or 4 channels.
 *      They can be in MLIB_BYTE, MLIB_SHORT, MLIB_USHORT, MLIB_INT or MLIB_BIT data type.
 *
 * DESCRIPTION
 *      Xor two images for each band:     dst = src1 ^ src2
 */

#include <mlib_image.h>

/***************************************************************/

#if ! defined ( __MEDIALIB_OLD_NAMES )
#if defined ( __SUNPRO_C )

#pragma weak mlib_ImageXor = __mlib_ImageXor

#elif defined ( __GNUC__ ) /* defined ( __SUNPRO_C ) */
  __typeof__ (__mlib_ImageXor) mlib_ImageXor
    __attribute__ ((weak,alias("__mlib_ImageXor")));

#else /* defined ( __SUNPRO_C ) */

#error  "unknown platform"

#endif /* defined ( __SUNPRO_C ) */
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */

/***************************************************************/

#define VIS_LOGIC(a1, a2) vis_fxor(a1, a2)

#include <mlib_v_ImageLogic.h>

/***************************************************************/

mlib_status __mlib_ImageXor(mlib_image *dst,
                            mlib_image *src1,
                            mlib_image *src2)
{
  MLIB_IMAGE_CHECK(src1);
  MLIB_IMAGE_CHECK(src2);
  MLIB_IMAGE_CHECK(dst);

  return mlib_v_ImageLogic(dst, src1, src2);
}

/***************************************************************/
