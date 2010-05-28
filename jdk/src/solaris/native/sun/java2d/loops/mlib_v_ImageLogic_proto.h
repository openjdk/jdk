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

#ifndef __MLIB_V_IMAGELOGIC_PROTO_H
#define __MLIB_V_IMAGELOGIC_PROTO_H


#include <mlib_types.h>
#include <mlib_image_types.h>
#include <mlib_status.h>

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

void mlib_v_ImageNot_na(mlib_u8  *sa,
                        mlib_u8  *da,
                        mlib_s32 size);
mlib_status mlib_v_ImageNot_Bit(mlib_image       *dst,
                                const mlib_image *src);
void mlib_v_ImageNot_blk(const void *src,
                         void       *dst,
                         mlib_s32   size);

mlib_status mlib_v_ImageAnd_Bit(mlib_image       *dst,
                                const mlib_image *src1,
                                const mlib_image *src2);
mlib_status mlib_v_ImageAndNot_Bit(mlib_image       *dst,
                                   const mlib_image *src1,
                                   const mlib_image *src2);

mlib_status mlib_v_ImageConstAnd_Bit(mlib_image       *dst,
                                     const mlib_image *src1,
                                     const mlib_s32   *c);
mlib_status mlib_v_ImageConstAndNot_Bit(mlib_image       *dst,
                                        const mlib_image *src1,
                                        const mlib_s32   *c);
mlib_status mlib_v_ImageConstNotAnd_Bit(mlib_image       *dst,
                                        const mlib_image *src1,
                                        const mlib_s32   *c);
mlib_status mlib_v_ImageConstNotOr_Bit(mlib_image       *dst,
                                       const mlib_image *src1,
                                       const mlib_s32   *c);
mlib_status mlib_v_ImageConstNotXor_Bit(mlib_image       *dst,
                                        const mlib_image *src1,
                                        const mlib_s32   *c);
mlib_status mlib_v_ImageConstOr_Bit(mlib_image       *dst,
                                    const mlib_image *src1,
                                    const mlib_s32   *c);
mlib_status mlib_v_ImageConstOrNot_Bit(mlib_image       *dst,
                                       const mlib_image *src1,
                                       const mlib_s32   *c);
mlib_status mlib_v_ImageConstXor_Bit(mlib_image       *dst,
                                     const mlib_image *src1,
                                     const mlib_s32   *c);

mlib_status mlib_v_ImageNotAnd_Bit(mlib_image       *dst,
                                   const mlib_image *src1,
                                   const mlib_image *src2);
mlib_status mlib_v_ImageNotOr_Bit(mlib_image       *dst,
                                  const mlib_image *src1,
                                  const mlib_image *src2);
mlib_status mlib_v_ImageNotXor_Bit(mlib_image       *dst,
                                   const mlib_image *src1,
                                   const mlib_image *src2);
mlib_status mlib_v_ImageOr_Bit(mlib_image       *dst,
                               const mlib_image *src1,
                               const mlib_image *src2);
mlib_status mlib_v_ImageOrNot_Bit(mlib_image       *dst,
                                  const mlib_image *src1,
                                  const mlib_image *src2);
mlib_status mlib_v_ImageXor_Bit(mlib_image       *dst,
                                const mlib_image *src1,
                                const mlib_image *src2);

#ifdef __cplusplus
}
#endif /* __cplusplus */
#endif /* __MLIB_V_IMAGELOGIC_PROTO_H */
