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

#ifndef __MLIB_V_IMAGECLEAR_F_H
#define __MLIB_V_IMAGECLEAR_F_H


#include <mlib_types.h>
#include <mlib_image_types.h>

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

void mlib_v_ImageClear_BIT_1(mlib_image     *img,
                             const mlib_s32 *color);

void mlib_v_ImageClear_BIT_2(mlib_image     *img,
                             const mlib_s32 *color);

void mlib_v_ImageClear_BIT_3(mlib_image     *img,
                             const mlib_s32 *color);

void mlib_v_ImageClear_BIT_4(mlib_image     *img,
                             const mlib_s32 *color);

void mlib_v_ImageClear_U8_1(mlib_image     *img,
                            const mlib_s32 *color);

void mlib_v_ImageClear_U8_2(mlib_image     *img,
                            const mlib_s32 *color);

void mlib_v_ImageClear_U8_3(mlib_image     *img,
                            const mlib_s32 *color);

void mlib_v_ImageClear_U8_4(mlib_image     *img,
                            const mlib_s32 *color);

void mlib_v_ImageClear_S16_1(mlib_image     *img,
                             const mlib_s32 *color);

void mlib_v_ImageClear_S16_2(mlib_image     *img,
                             const mlib_s32 *color);

void mlib_v_ImageClear_S16_3(mlib_image     *img,
                             const mlib_s32 *color);

void mlib_v_ImageClear_S16_4(mlib_image     *img,
                             const mlib_s32 *color);

void mlib_v_ImageClear_S32_1(mlib_image     *img,
                             const mlib_s32 *color);

void mlib_v_ImageClear_S32_2(mlib_image     *img,
                             const mlib_s32 *color);

void mlib_v_ImageClear_S32_3(mlib_image     *img,
                             const mlib_s32 *color);

void mlib_v_ImageClear_S32_4(mlib_image     *img,
                             const mlib_s32 *color);

#ifdef __cplusplus
}
#endif /* __cplusplus */
#endif /* __MLIB_V_IMAGECLEAR_F_H */
