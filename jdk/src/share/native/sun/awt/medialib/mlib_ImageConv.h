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


#ifndef __MLIB_IMAGECONV_H
#define __MLIB_IMAGECONV_H

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

void mlib_ImageXor80_aa(mlib_u8  *dl,
                        mlib_s32 wid,
                        mlib_s32 hgt,
                        mlib_s32 str);

void mlib_ImageXor80(mlib_u8  *dl,
                     mlib_s32 wid,
                     mlib_s32 hgt,
                     mlib_s32 str,
                     mlib_s32 nchan,
                     mlib_s32 cmask);

mlib_status mlib_conv2x2ext_d64(mlib_image       *dst,
                                const mlib_image *src,
                                mlib_s32         dx_l,
                                mlib_s32         dx_r,
                                mlib_s32         dy_t,
                                mlib_s32         dy_b,
                                const mlib_d64   *kern,
                                mlib_s32         cmask);

mlib_status mlib_conv2x2ext_f32(mlib_image       *dst,
                                const mlib_image *src,
                                mlib_s32         dx_l,
                                mlib_s32         dx_r,
                                mlib_s32         dy_t,
                                mlib_s32         dy_b,
                                const mlib_d64   *kern,
                                mlib_s32         cmask);

mlib_status mlib_conv2x2ext_s16(mlib_image       *dst,
                                const mlib_image *src,
                                mlib_s32         dx_l,
                                mlib_s32         dx_r,
                                mlib_s32         dy_t,
                                mlib_s32         dy_b,
                                const mlib_s32   *kern,
                                mlib_s32         scale,
                                mlib_s32         cmask);

mlib_status mlib_conv2x2ext_s32(mlib_image       *dst,
                                const mlib_image *src,
                                mlib_s32         dx_l,
                                mlib_s32         dx_r,
                                mlib_s32         dy_t,
                                mlib_s32         dy_b,
                                const mlib_s32   *kern,
                                mlib_s32         scale,
                                mlib_s32         cmask);

mlib_status mlib_conv2x2ext_u16(mlib_image       *dst,
                                const mlib_image *src,
                                mlib_s32         dx_l,
                                mlib_s32         dx_r,
                                mlib_s32         dy_t,
                                mlib_s32         dy_b,
                                const mlib_s32   *kern,
                                mlib_s32         scale,
                                mlib_s32         cmask);

mlib_status mlib_conv2x2ext_u8(mlib_image       *dst,
                               const mlib_image *src,
                               mlib_s32         dx_l,
                               mlib_s32         dx_r,
                               mlib_s32         dy_t,
                               mlib_s32         dy_b,
                               const mlib_s32   *kern,
                               mlib_s32         scale,
                               mlib_s32         cmask);

mlib_status mlib_conv2x2nw_d64(mlib_image       *dst,
                               const mlib_image *src,
                               const mlib_d64   *kern,
                               mlib_s32         cmask);

mlib_status mlib_conv2x2nw_f32(mlib_image       *dst,
                               const mlib_image *src,
                               const mlib_d64   *kern,
                               mlib_s32         cmask);

mlib_status mlib_conv2x2nw_s16(mlib_image       *dst,
                               const mlib_image *src,
                               const mlib_s32   *kern,
                               mlib_s32         scale,
                               mlib_s32         cmask);

mlib_status mlib_conv2x2nw_s32(mlib_image       *dst,
                               const mlib_image *src,
                               const mlib_s32   *kern,
                               mlib_s32         scale,
                               mlib_s32         cmask);

mlib_status mlib_conv2x2nw_u16(mlib_image       *dst,
                               const mlib_image *src,
                               const mlib_s32   *kern,
                               mlib_s32         scale,
                               mlib_s32         cmask);

mlib_status mlib_conv2x2nw_u8(mlib_image       *dst,
                              const mlib_image *src,
                              const mlib_s32   *kern,
                              mlib_s32         scale,
                              mlib_s32         cmask);

mlib_status mlib_conv3x3ext_bit(mlib_image       *dst,
                                const mlib_image *src,
                                mlib_s32         dx_l,
                                mlib_s32         dx_r,
                                mlib_s32         dy_t,
                                mlib_s32         dy_b,
                                const mlib_s32   *kern,
                                mlib_s32         scale,
                                mlib_s32         cmask);

mlib_status mlib_conv3x3ext_d64(mlib_image       *dst,
                                const mlib_image *src,
                                mlib_s32         dx_l,
                                mlib_s32         dx_r,
                                mlib_s32         dy_t,
                                mlib_s32         dy_b,
                                const mlib_d64   *kern,
                                mlib_s32         cmask);

mlib_status mlib_conv3x3ext_f32(mlib_image       *dst,
                                const mlib_image *src,
                                mlib_s32         dx_l,
                                mlib_s32         dx_r,
                                mlib_s32         dy_t,
                                mlib_s32         dy_b,
                                const mlib_d64   *kern,
                                mlib_s32         cmask);

mlib_status mlib_conv3x3ext_s16(mlib_image       *dst,
                                const mlib_image *src,
                                mlib_s32         dx_l,
                                mlib_s32         dx_r,
                                mlib_s32         dy_t,
                                mlib_s32         dy_b,
                                const mlib_s32   *kern,
                                mlib_s32         scale,
                                mlib_s32         cmask);

mlib_status mlib_conv3x3ext_s32(mlib_image       *dst,
                                const mlib_image *src,
                                mlib_s32         dx_l,
                                mlib_s32         dx_r,
                                mlib_s32         dy_t,
                                mlib_s32         dy_b,
                                const mlib_s32   *kern,
                                mlib_s32         scale,
                                mlib_s32         cmask);

mlib_status mlib_conv3x3ext_u16(mlib_image       *dst,
                                const mlib_image *src,
                                mlib_s32         dx_l,
                                mlib_s32         dx_r,
                                mlib_s32         dy_t,
                                mlib_s32         dy_b,
                                const mlib_s32   *kern,
                                mlib_s32         scale,
                                mlib_s32         cmask);

mlib_status mlib_conv3x3ext_u8(mlib_image       *dst,
                               const mlib_image *src,
                               mlib_s32         dx_l,
                               mlib_s32         dx_r,
                               mlib_s32         dy_t,
                               mlib_s32         dy_b,
                               const mlib_s32   *kern,
                               mlib_s32         scale,
                               mlib_s32         cmask);

mlib_status mlib_conv3x3nw_bit(mlib_image       *dst,
                               const mlib_image *src,
                               const mlib_s32   *kern,
                               mlib_s32         scale,
                               mlib_s32         cmask);

mlib_status mlib_conv3x3nw_d64(mlib_image       *dst,
                               const mlib_image *src,
                               const mlib_d64   *kern,
                               mlib_s32         cmask);

mlib_status mlib_conv3x3nw_f32(mlib_image       *dst,
                               const mlib_image *src,
                               const mlib_d64   *kern,
                               mlib_s32         cmask);

mlib_status mlib_conv3x3nw_s16(mlib_image       *dst,
                               const mlib_image *src,
                               const mlib_s32   *kern,
                               mlib_s32         scale,
                               mlib_s32         cmask);

mlib_status mlib_conv3x3nw_s32(mlib_image       *dst,
                               const mlib_image *src,
                               const mlib_s32   *kern,
                               mlib_s32         scale,
                               mlib_s32         cmask);

mlib_status mlib_conv3x3nw_u16(mlib_image       *dst,
                               const mlib_image *src,
                               const mlib_s32   *kern,
                               mlib_s32         scale,
                               mlib_s32         cmask);

mlib_status mlib_conv3x3nw_u8(mlib_image       *dst,
                              const mlib_image *src,
                              const mlib_s32   *kern,
                              mlib_s32         scale,
                              mlib_s32         cmask);

mlib_status mlib_conv4x4ext_d64(mlib_image       *dst,
                                const mlib_image *src,
                                mlib_s32         dx_l,
                                mlib_s32         dx_r,
                                mlib_s32         dy_t,
                                mlib_s32         dy_b,
                                const mlib_d64   *kern,
                                mlib_s32         cmask);

mlib_status mlib_conv4x4ext_f32(mlib_image       *dst,
                                const mlib_image *src,
                                mlib_s32         dx_l,
                                mlib_s32         dx_r,
                                mlib_s32         dy_t,
                                mlib_s32         dy_b,
                                const mlib_d64   *kern,
                                mlib_s32         cmask);

mlib_status mlib_conv4x4ext_s16(mlib_image       *dst,
                                const mlib_image *src,
                                mlib_s32         dx_l,
                                mlib_s32         dx_r,
                                mlib_s32         dy_t,
                                mlib_s32         dy_b,
                                const mlib_s32   *kern,
                                mlib_s32         scale,
                                mlib_s32         cmask);

mlib_status mlib_conv4x4ext_s32(mlib_image       *dst,
                                const mlib_image *src,
                                mlib_s32         dx_l,
                                mlib_s32         dx_r,
                                mlib_s32         dy_t,
                                mlib_s32         dy_b,
                                const mlib_s32   *kern,
                                mlib_s32         scale,
                                mlib_s32         cmask);

mlib_status mlib_conv4x4ext_u16(mlib_image       *dst,
                                const mlib_image *src,
                                mlib_s32         dx_l,
                                mlib_s32         dx_r,
                                mlib_s32         dy_t,
                                mlib_s32         dy_b,
                                const mlib_s32   *kern,
                                mlib_s32         scale,
                                mlib_s32         cmask);

mlib_status mlib_conv4x4ext_u8(mlib_image       *dst,
                               const mlib_image *src,
                               mlib_s32         dx_l,
                               mlib_s32         dx_r,
                               mlib_s32         dy_t,
                               mlib_s32         dy_b,
                               const mlib_s32   *kern,
                               mlib_s32         scale,
                               mlib_s32         cmask);

mlib_status mlib_conv4x4nw_d64(mlib_image       *dst,
                               const mlib_image *src,
                               const mlib_d64   *kern,
                               mlib_s32         cmask);

mlib_status mlib_conv4x4nw_f32(mlib_image       *dst,
                               const mlib_image *src,
                               const mlib_d64   *kern,
                               mlib_s32         cmask);

mlib_status mlib_conv4x4nw_s16(mlib_image       *dst,
                               const mlib_image *src,
                               const mlib_s32   *kern,
                               mlib_s32         scale,
                               mlib_s32         cmask);

mlib_status mlib_conv4x4nw_s32(mlib_image       *dst,
                               const mlib_image *src,
                               const mlib_s32   *kern,
                               mlib_s32         scale,
                               mlib_s32         cmask);

mlib_status mlib_conv4x4nw_u16(mlib_image       *dst,
                               const mlib_image *src,
                               const mlib_s32   *kern,
                               mlib_s32         scale,
                               mlib_s32         cmask);

mlib_status mlib_conv4x4nw_u8(mlib_image       *dst,
                              const mlib_image *src,
                              const mlib_s32   *kern,
                              mlib_s32         scale,
                              mlib_s32         cmask);

mlib_status mlib_conv5x5ext_d64(mlib_image       *dst,
                                const mlib_image *src,
                                mlib_s32         dx_l,
                                mlib_s32         dx_r,
                                mlib_s32         dy_t,
                                mlib_s32         dy_b,
                                const mlib_d64   *kern,
                                mlib_s32         cmask);

mlib_status mlib_conv5x5ext_f32(mlib_image       *dst,
                                const mlib_image *src,
                                mlib_s32         dx_l,
                                mlib_s32         dx_r,
                                mlib_s32         dy_t,
                                mlib_s32         dy_b,
                                const mlib_d64   *kern,
                                mlib_s32         cmask);

mlib_status mlib_conv5x5ext_s16(mlib_image       *dst,
                                const mlib_image *src,
                                mlib_s32         dx_l,
                                mlib_s32         dx_r,
                                mlib_s32         dy_t,
                                mlib_s32         dy_b,
                                const mlib_s32   *kern,
                                mlib_s32         scale,
                                mlib_s32         cmask);

mlib_status mlib_conv5x5ext_s32(mlib_image       *dst,
                                const mlib_image *src,
                                mlib_s32         dx_l,
                                mlib_s32         dx_r,
                                mlib_s32         dy_t,
                                mlib_s32         dy_b,
                                const mlib_s32   *kern,
                                mlib_s32         scale,
                                mlib_s32         cmask);

mlib_status mlib_conv5x5ext_u16(mlib_image       *dst,
                                const mlib_image *src,
                                mlib_s32         dx_l,
                                mlib_s32         dx_r,
                                mlib_s32         dy_t,
                                mlib_s32         dy_b,
                                const mlib_s32   *kern,
                                mlib_s32         scale,
                                mlib_s32         cmask);

mlib_status mlib_conv5x5ext_u8(mlib_image       *dst,
                               const mlib_image *src,
                               mlib_s32         dx_l,
                               mlib_s32         dx_r,
                               mlib_s32         dy_t,
                               mlib_s32         dy_b,
                               const mlib_s32   *kern,
                               mlib_s32         scale,
                               mlib_s32         cmask);

mlib_status mlib_conv5x5nw_d64(mlib_image       *dst,
                               const mlib_image *src,
                               const mlib_d64   *kern,
                               mlib_s32         cmask);

mlib_status mlib_conv5x5nw_f32(mlib_image       *dst,
                               const mlib_image *src,
                               const mlib_d64   *kern,
                               mlib_s32         cmask);

mlib_status mlib_conv5x5nw_s16(mlib_image       *dst,
                               const mlib_image *src,
                               const mlib_s32   *kern,
                               mlib_s32         scale,
                               mlib_s32         cmask);

mlib_status mlib_conv5x5nw_s32(mlib_image       *dst,
                               const mlib_image *src,
                               const mlib_s32   *kern,
                               mlib_s32         scale,
                               mlib_s32         cmask);

mlib_status mlib_conv5x5nw_u16(mlib_image       *dst,
                               const mlib_image *src,
                               const mlib_s32   *kern,
                               mlib_s32         scale,
                               mlib_s32         cmask);

mlib_status mlib_conv5x5nw_u8(mlib_image       *dst,
                              const mlib_image *src,
                              const mlib_s32   *kern,
                              mlib_s32         scale,
                              mlib_s32         cmask);

mlib_status mlib_conv7x7ext_s16(mlib_image       *dst,
                                const mlib_image *src,
                                mlib_s32         dx_l,
                                mlib_s32         dx_r,
                                mlib_s32         dy_t,
                                mlib_s32         dy_b,
                                const mlib_s32   *kern,
                                mlib_s32         scale,
                                mlib_s32         cmask);

mlib_status mlib_conv7x7ext_s32(mlib_image       *dst,
                                const mlib_image *src,
                                mlib_s32         dx_l,
                                mlib_s32         dx_r,
                                mlib_s32         dy_t,
                                mlib_s32         dy_b,
                                const mlib_s32   *kern,
                                mlib_s32         scale,
                                mlib_s32         cmask);

mlib_status mlib_conv7x7ext_u16(mlib_image       *dst,
                                const mlib_image *src,
                                mlib_s32         dx_l,
                                mlib_s32         dx_r,
                                mlib_s32         dy_t,
                                mlib_s32         dy_b,
                                const mlib_s32   *kern,
                                mlib_s32         scale,
                                mlib_s32         cmask);

mlib_status mlib_conv7x7ext_u8(mlib_image       *dst,
                               const mlib_image *src,
                               mlib_s32         dx_l,
                               mlib_s32         dx_r,
                               mlib_s32         dy_t,
                               mlib_s32         dy_b,
                               const mlib_s32   *kern,
                               mlib_s32         scale,
                               mlib_s32         cmask);

mlib_status mlib_conv7x7nw_s16(mlib_image       *dst,
                               const mlib_image *src,
                               const mlib_s32   *kern,
                               mlib_s32         scale,
                               mlib_s32         cmask);

mlib_status mlib_conv7x7nw_s32(mlib_image       *dst,
                               const mlib_image *src,
                               const mlib_s32   *kern,
                               mlib_s32         scale,
                               mlib_s32         cmask);

mlib_status mlib_conv7x7nw_u16(mlib_image       *dst,
                               const mlib_image *src,
                               const mlib_s32   *kern,
                               mlib_s32         scale,
                               mlib_s32         cmask);

mlib_status mlib_conv7x7nw_u8(mlib_image       *dst,
                              const mlib_image *src,
                              const mlib_s32   *kern,
                              mlib_s32         scale,
                              mlib_s32         cmask);

mlib_status mlib_convMxNext_s32(mlib_image       *dst,
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

mlib_status mlib_convMxNnw_d64(mlib_image       *dst,
                               const mlib_image *src,
                               const mlib_d64   *ker,
                               mlib_s32         m,
                               mlib_s32         n,
                               mlib_s32         dm,
                               mlib_s32         dn,
                               mlib_s32         cmask);

mlib_status mlib_convMxNnw_f32(mlib_image       *dst,
                               const mlib_image *src,
                               const mlib_d64   *ker,
                               mlib_s32         m,
                               mlib_s32         n,
                               mlib_s32         dm,
                               mlib_s32         dn,
                               mlib_s32         cmask);

mlib_status mlib_convMxNnw_s16(mlib_image       *dst,
                               const mlib_image *src,
                               const mlib_s32   *kernel,
                               mlib_s32         m,
                               mlib_s32         n,
                               mlib_s32         dm,
                               mlib_s32         dn,
                               mlib_s32         scale,
                               mlib_s32         cmask);

mlib_status mlib_convMxNnw_s32(mlib_image       *dst,
                               const mlib_image *src,
                               const mlib_s32   *kernel,
                               mlib_s32         m,
                               mlib_s32         n,
                               mlib_s32         dm,
                               mlib_s32         dn,
                               mlib_s32         scale,
                               mlib_s32         cmask);

mlib_status mlib_convMxNnw_u16(mlib_image       *dst,
                               const mlib_image *src,
                               const mlib_s32   *kernel,
                               mlib_s32         m,
                               mlib_s32         n,
                               mlib_s32         dm,
                               mlib_s32         dn,
                               mlib_s32         scale,
                               mlib_s32         cmask);

mlib_s32 mlib_ImageConvVersion(mlib_s32  m,
                               mlib_s32  n,
                               mlib_s32  scale,
                               mlib_type type);

mlib_status mlib_ImageConvMxN_f(mlib_image       *dst,
                                const mlib_image *src,
                                const void       *kernel,
                                mlib_s32         m,
                                mlib_s32         n,
                                mlib_s32         dm,
                                mlib_s32         dn,
                                mlib_s32         scale,
                                mlib_s32         cmask,
                                mlib_edge        edge);

mlib_status mlib_convMxNnw_u8(mlib_image       *dst,
                              const mlib_image *src,
                              const mlib_s32   *kern,
                              mlib_s32         m,
                              mlib_s32         n,
                              mlib_s32         dm,
                              mlib_s32         dn,
                              mlib_s32         scale,
                              mlib_s32         cmask);

mlib_status mlib_convMxNext_u8(mlib_image       *dst,
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

mlib_status mlib_convMxNext_s16(mlib_image *dst,
                                const mlib_image *src,
                                const mlib_s32 *kernel,
                                mlib_s32 m,
                                mlib_s32 n,
                                mlib_s32 dx_l,
                                mlib_s32 dx_r,
                                mlib_s32 dy_t,
                                mlib_s32 dy_b,
                                mlib_s32 scale,
                                mlib_s32 cmask);

mlib_status mlib_convMxNext_u16(mlib_image       *dst,
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

mlib_status mlib_convMxNext_f32(mlib_image       *dst,
                                const mlib_image *src,
                                const mlib_d64   *kernel,
                                mlib_s32         m,
                                mlib_s32         n,
                                mlib_s32         dx_l,
                                mlib_s32         dx_r,
                                mlib_s32         dy_t,
                                mlib_s32         dy_b,
                                mlib_s32         cmask);

mlib_status mlib_convMxNext_d64(mlib_image       *dst,
                                const mlib_image *src,
                                const mlib_d64   *kernel,
                                mlib_s32         m,
                                mlib_s32         n,
                                mlib_s32         dx_l,
                                mlib_s32         dx_r,
                                mlib_s32         dy_t,
                                mlib_s32         dy_b,
                                mlib_s32         cmask);

#ifdef __cplusplus
}
#endif /* __cplusplus */
#endif /* __MLIB_IMAGECONV_H */
