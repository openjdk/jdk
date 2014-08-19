/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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


#ifndef __MLIB_C_IMAGECONV_H
#define __MLIB_C_IMAGECONV_H

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

// Shared macro defined for cleanup of allocated memory.
#ifndef FREE_AND_RETURN_STATUS
#define FREE_AND_RETURN_STATUS \
{ \
if (pbuff != buff) mlib_free(pbuff); \
if (k != akernel) mlib_free(k); \
return status; \
}
#endif /* FREE_AND_RETURN_STATUS */

mlib_status mlib_c_conv2x2ext_s16(mlib_image       *dst,
                                  const mlib_image *src,
                                  mlib_s32         dx_l,
                                  mlib_s32         dx_r,
                                  mlib_s32         dy_t,
                                  mlib_s32         dy_b,
                                  const mlib_s32   *kern,
                                  mlib_s32         scale,
                                  mlib_s32         cmask);

mlib_status mlib_c_conv2x2ext_u16(mlib_image       *dst,
                                  const mlib_image *src,
                                  mlib_s32         dx_l,
                                  mlib_s32         dx_r,
                                  mlib_s32         dy_t,
                                  mlib_s32         dy_b,
                                  const mlib_s32   *kern,
                                  mlib_s32         scale,
                                  mlib_s32         cmask);

mlib_status mlib_c_conv2x2ext_u8(mlib_image       *dst,
                                 const mlib_image *src,
                                 mlib_s32         dx_l,
                                 mlib_s32         dx_r,
                                 mlib_s32         dy_t,
                                 mlib_s32         dy_b,
                                 const mlib_s32   *kern,
                                 mlib_s32         scale,
                                 mlib_s32         cmask);

mlib_status mlib_c_conv2x2nw_s16(mlib_image       *dst,
                                 const mlib_image *src,
                                 const mlib_s32   *kern,
                                 mlib_s32         scale,
                                 mlib_s32         cmask);

mlib_status mlib_c_conv2x2nw_u16(mlib_image       *dst,
                                 const mlib_image *src,
                                 const mlib_s32   *kern,
                                 mlib_s32         scale,
                                 mlib_s32         cmask);

mlib_status mlib_c_conv2x2nw_u8(mlib_image       *dst,
                                const mlib_image *src,
                                const mlib_s32   *kern,
                                mlib_s32         scale,
                                mlib_s32         cmask);

mlib_status mlib_c_conv3x3ext_u8(mlib_image       *dst,
                                 const mlib_image *src,
                                 mlib_s32         dx_l,
                                 mlib_s32         dx_r,
                                 mlib_s32         dy_t,
                                 mlib_s32         dy_b,
                                 const mlib_s32   *kern,
                                 mlib_s32         scale,
                                 mlib_s32         cmask);

mlib_status mlib_c_conv3x3nw_u8(mlib_image       *dst,
                                const mlib_image *src,
                                const mlib_s32   *kern,
                                mlib_s32         scale,
                                mlib_s32         cmask);

mlib_status mlib_c_conv4x4ext_u8(mlib_image       *dst,
                                 const mlib_image *src,
                                 mlib_s32         dx_l,
                                 mlib_s32         dx_r,
                                 mlib_s32         dy_t,
                                 mlib_s32         dy_b,
                                 const mlib_s32   *kern,
                                 mlib_s32         scale,
                                 mlib_s32         cmask);

mlib_status mlib_c_conv4x4nw_u8(mlib_image       *dst,
                                const mlib_image *src,
                                const mlib_s32   *kern,
                                mlib_s32         scale,
                                mlib_s32         cmask);

mlib_status mlib_c_conv5x5ext_u8(mlib_image       *dst,
                                 const mlib_image *src,
                                 mlib_s32         dx_l,
                                 mlib_s32         dx_r,
                                 mlib_s32         dy_t,
                                 mlib_s32         dy_b,
                                 const mlib_s32   *kern,
                                 mlib_s32         scale,
                                 mlib_s32         cmask);

mlib_status mlib_c_conv5x5nw_u8(mlib_image       *dst,
                                const mlib_image *src,
                                const mlib_s32   *kern,
                                mlib_s32         scale,
                                mlib_s32         cmask);

mlib_status mlib_c_conv7x7ext_u8(mlib_image       *dst,
                                 const mlib_image *src,
                                 mlib_s32         dx_l,
                                 mlib_s32         dx_r,
                                 mlib_s32         dy_t,
                                 mlib_s32         dy_b,
                                 const mlib_s32   *kern,
                                 mlib_s32         scale,
                                 mlib_s32         cmask);

mlib_status mlib_c_conv7x7nw_u8(mlib_image       *dst,
                                const mlib_image *src,
                                const mlib_s32   *kern,
                                mlib_s32         scale,
                                mlib_s32         cmask);

mlib_status mlib_c_convMxNnw_u8(mlib_image       *dst,
                                const mlib_image *src,
                                const mlib_s32   *kernel,
                                mlib_s32         m,
                                mlib_s32         n,
                                mlib_s32         dm,
                                mlib_s32         dn,
                                mlib_s32         scale,
                                mlib_s32         cmask);

mlib_status mlib_c_convMxNext_u8(mlib_image       *dst,
                                 const mlib_image *src,
                                 const mlib_s32   *kern,
                                 mlib_s32         m,
                                 mlib_s32         n,
                                 mlib_s32         dx_l,
                                 mlib_s32         dx_r,
                                 mlib_s32         dy_t,
                                 mlib_s32         dy_b,
                                 mlib_s32         scale,
                                 mlib_s32         cmask);

#if ! defined ( __sparc ) /* for x86, using integer multiplies is faster */

mlib_status mlib_i_conv3x3ext_s16(mlib_image       *dst,
                                  const mlib_image *src,
                                  mlib_s32         dx_l,
                                  mlib_s32         dx_r,
                                  mlib_s32         dy_t,
                                  mlib_s32         dy_b,
                                  const mlib_s32   *kern,
                                  mlib_s32         scale,
                                  mlib_s32         cmask);

mlib_status mlib_i_conv3x3ext_u16(mlib_image       *dst,
                                  const mlib_image *src,
                                  mlib_s32         dx_l,
                                  mlib_s32         dx_r,
                                  mlib_s32         dy_t,
                                  mlib_s32         dy_b,
                                  const mlib_s32   *kern,
                                  mlib_s32         scale,
                                  mlib_s32         cmask);

mlib_status mlib_i_conv3x3ext_u8(mlib_image       *dst,
                                 const mlib_image *src,
                                 mlib_s32         dx_l,
                                 mlib_s32         dx_r,
                                 mlib_s32         dy_t,
                                 mlib_s32         dy_b,
                                 const mlib_s32   *kern,
                                 mlib_s32         scale,
                                 mlib_s32         cmask);

mlib_status mlib_i_conv3x3nw_s16(mlib_image       *dst,
                                 const mlib_image *src,
                                 const mlib_s32   *kern,
                                 mlib_s32         scale,
                                 mlib_s32         cmask);

mlib_status mlib_i_conv3x3nw_u16(mlib_image       *dst,
                                 const mlib_image *src,
                                 const mlib_s32   *kern,
                                 mlib_s32         scale,
                                 mlib_s32         cmask);

mlib_status mlib_i_conv3x3nw_u8(mlib_image       *dst,
                                const mlib_image *src,
                                const mlib_s32   *kern,
                                mlib_s32         scale,
                                mlib_s32         cmask);

mlib_status mlib_i_conv5x5ext_s16(mlib_image       *dst,
                                  const mlib_image *src,
                                  mlib_s32         dx_l,
                                  mlib_s32         dx_r,
                                  mlib_s32         dy_t,
                                  mlib_s32         dy_b,
                                  const mlib_s32   *kern,
                                  mlib_s32         scale,
                                  mlib_s32         cmask);

mlib_status mlib_i_conv5x5ext_u16(mlib_image       *dst,
                                  const mlib_image *src,
                                  mlib_s32         dx_l,
                                  mlib_s32         dx_r,
                                  mlib_s32         dy_t,
                                  mlib_s32         dy_b,
                                  const mlib_s32   *kern,
                                  mlib_s32         scale,
                                  mlib_s32         cmask);

mlib_status mlib_i_conv5x5ext_u8(mlib_image       *dst,
                                 const mlib_image *src,
                                 mlib_s32         dx_l,
                                 mlib_s32         dx_r,
                                 mlib_s32         dy_t,
                                 mlib_s32         dy_b,
                                 const mlib_s32   *kern,
                                 mlib_s32         scale,
                                 mlib_s32         cmask);

mlib_status mlib_i_conv5x5nw_s16(mlib_image       *dst,
                                 const mlib_image *src,
                                 const mlib_s32   *kern,
                                 mlib_s32         scale,
                                 mlib_s32         cmask);

mlib_status mlib_i_conv5x5nw_u16(mlib_image       *dst,
                                 const mlib_image *src,
                                 const mlib_s32   *kern,
                                 mlib_s32         scale,
                                 mlib_s32         cmask);

mlib_status mlib_i_conv5x5nw_u8(mlib_image       *dst,
                                const mlib_image *src,
                                const mlib_s32   *kern,
                                mlib_s32         scale,
                                mlib_s32         cmask);

mlib_status mlib_i_convMxNnw_s16(mlib_image       *dst,
                                 const mlib_image *src,
                                 const mlib_s32   *kernel,
                                 mlib_s32         m,
                                 mlib_s32         n,
                                 mlib_s32         dm,
                                 mlib_s32         dn,
                                 mlib_s32         scale,
                                 mlib_s32         cmask);

mlib_status mlib_i_convMxNnw_u16(mlib_image       *dst,
                                 const mlib_image *src,
                                 const mlib_s32   *kernel,
                                 mlib_s32         m,
                                 mlib_s32         n,
                                 mlib_s32         dm,
                                 mlib_s32         dn,
                                 mlib_s32         scale,
                                 mlib_s32         cmask);

mlib_status mlib_i_convMxNnw_u8(mlib_image       *dst,
                                const mlib_image *src,
                                const mlib_s32   *kernel,
                                mlib_s32         m,
                                mlib_s32         n,
                                mlib_s32         dm,
                                mlib_s32         dn,
                                mlib_s32         scale,
                                mlib_s32         cmask);

mlib_status mlib_i_convMxNext_u8(mlib_image       *dst,
                                 const mlib_image *src,
                                 const mlib_s32   *kern,
                                 mlib_s32         m,
                                 mlib_s32         n,
                                 mlib_s32         dx_l,
                                 mlib_s32         dx_r,
                                 mlib_s32         dy_t,
                                 mlib_s32         dy_b,
                                 mlib_s32         scale,
                                 mlib_s32         cmask);

mlib_status mlib_i_convMxNext_s16(mlib_image       *dst,
                                  const mlib_image *src,
                                  const mlib_s32   *kernel,
                                  mlib_s32         m,
                                  mlib_s32         n,
                                  mlib_s32         dx_l,
                                  mlib_s32         dx_r,
                                  mlib_s32         dy_t,
                                  mlib_s32         dy_b,
                                  mlib_s32         scale,
                                  mlib_s32         cmask);

mlib_status mlib_i_convMxNext_u16(mlib_image       *dst,
                                  const mlib_image *src,
                                  const mlib_s32   *kernel,
                                  mlib_s32         m,
                                  mlib_s32         n,
                                  mlib_s32         dx_l,
                                  mlib_s32         dx_r,
                                  mlib_s32         dy_t,
                                  mlib_s32         dy_b,
                                  mlib_s32         scale,
                                  mlib_s32         cmask);

#endif /* ! defined ( __sparc ) ( for x86, using integer multiplies is faster ) */

#ifdef __cplusplus
}
#endif /* __cplusplus */
#endif /* __MLIB_C_IMAGECONV_H */
