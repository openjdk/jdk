/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
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


#ifndef __ORIG_MLIB_IMAGE_PROTO_H
#define __ORIG_MLIB_IMAGE_PROTO_H

#include <mlib_types.h>
#include <mlib_status.h>
#include <mlib_image_types.h>
#include "jni.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#if defined ( __USE_J2D_NAMES )
#include "j2d_names.h"
#endif // __USE_J2D_NAMES

JNIEXPORT mlib_image *
mlib_ImageCreate(mlib_type type,
                   mlib_s32 channels,
                   mlib_s32 width,
                   mlib_s32 height);


JNIEXPORT mlib_image *
mlib_ImageCreateStruct(mlib_type type,
                         mlib_s32 channels,
                         mlib_s32 width,
                         mlib_s32 height,
                         mlib_s32 stride,
                         const void *data);

JNIEXPORT void
mlib_ImageDelete(mlib_image *img);

JNIEXPORT mlib_status
mlib_ImageAffine(mlib_image *dst,
                   const mlib_image *src,
                   const mlib_d64 *mtx,
                   mlib_filter filter,
                   mlib_edge edge);

JNIEXPORT mlib_status
mlib_ImageConvKernelConvert(mlib_s32 *ikernel,
                              mlib_s32 *iscale,
                              const mlib_d64 *fkernel,
                              mlib_s32 m,
                              mlib_s32 n,
                              mlib_type type);

JNIEXPORT mlib_status
mlib_ImageConvMxN(mlib_image *dst,
                    const mlib_image *src,
                    const mlib_s32 *kernel,
                    mlib_s32 m,
                    mlib_s32 n,
                    mlib_s32 dm,
                    mlib_s32 dn,
                    mlib_s32 scale,
                    mlib_s32 cmask,
                    mlib_edge edge);

JNIEXPORT mlib_status
mlib_ImageLookUp(mlib_image *dst,
                   const mlib_image *src,
                   const void **table);

#ifdef __cplusplus
}
#endif /* __cplusplus */
#endif /* __ORIG_MLIB_IMAGE_PROTO_H */
