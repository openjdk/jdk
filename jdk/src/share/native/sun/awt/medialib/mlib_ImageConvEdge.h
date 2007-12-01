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


#ifndef __MLIB_IMAGECONVEDGE_H
#define __MLIB_IMAGECONVEDGE_H

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

mlib_status mlib_ImageConvClearEdge_Bit(mlib_image     *img,
                                        mlib_s32       dx_l,
                                        mlib_s32       dx_r,
                                        mlib_s32       dy_t,
                                        mlib_s32       dy_b,
                                        const mlib_s32 *color,
                                        mlib_s32       cmask);

mlib_status mlib_ImageConvClearEdge(mlib_image     *dst,
                                    mlib_s32       dx_l,
                                    mlib_s32       dx_r,
                                    mlib_s32       dy_t,
                                    mlib_s32       dy_b,
                                    const mlib_s32 *color,
                                    mlib_s32       cmask);

mlib_status mlib_ImageConvClearEdge_Fp(mlib_image     *img,
                                       mlib_s32       dx_l,
                                       mlib_s32       dx_r,
                                       mlib_s32       dy_t,
                                       mlib_s32       dy_b,
                                       const mlib_d64 *color,
                                       mlib_s32       cmask);

mlib_status mlib_ImageConvZeroEdge(mlib_image *dst,
                                   mlib_s32   dx_l,
                                   mlib_s32   dx_r,
                                   mlib_s32   dy_t,
                                   mlib_s32   dy_b,
                                   mlib_s32   cmask);

mlib_status mlib_ImageConvCopyEdge_Bit(mlib_image       *dst,
                                       const mlib_image *src,
                                       mlib_s32         dx_l,
                                       mlib_s32         dx_r,
                                       mlib_s32         dy_t,
                                       mlib_s32         dy_b,
                                       mlib_s32         cmask);

mlib_status mlib_ImageConvCopyEdge(mlib_image       *dst,
                                   const mlib_image *src,
                                   mlib_s32         dx_l,
                                   mlib_s32         dx_r,
                                   mlib_s32         dy_t,
                                   mlib_s32         dy_b,
                                   mlib_s32         cmask);

mlib_status mlib_ImageConvCopyEdge_Fp(mlib_image       *dst,
                                      const mlib_image *src,
                                      mlib_s32         dx_l,
                                      mlib_s32         dx_r,
                                      mlib_s32         dy_t,
                                      mlib_s32         dy_b,
                                      mlib_s32         cmask);

#ifdef __cplusplus
}
#endif /* __cplusplus */
#endif /* __MLIB_IMAGECONVEDGE_H */
