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

#ifndef __MLIB_IMAGELOGIC_H
#define __MLIB_IMAGELOGIC_H

#include <mlib_types.h>
#include <mlib_image_types.h>
#include <mlib_status.h>

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

mlib_status mlib_ImageAnd_Bit(mlib_image       *dst,
                              const mlib_image *src1,
                              const mlib_image *src2);
mlib_status mlib_ImageAndNot_Bit(mlib_image       *dst,
                                 const mlib_image *src1,
                                 const mlib_image *src2);
mlib_status mlib_ImageNot_Bit(mlib_image       *dst,
                              const mlib_image *src);
mlib_status mlib_ImageNotAnd_Bit(mlib_image       *dst,
                                 const mlib_image *src1,
                                 const mlib_image *src2);
mlib_status mlib_ImageNotOr_Bit(mlib_image       *dst,
                                const mlib_image *src1,
                                const mlib_image *src2);
mlib_status mlib_ImageNotXor_Bit(mlib_image       *dst,
                                 const mlib_image *src1,
                                 const mlib_image *src2);
mlib_status mlib_ImageOr_Bit(mlib_image       *dst,
                             const mlib_image *src1,
                             const mlib_image *src2);
mlib_status mlib_ImageOrNot_Bit(mlib_image       *dst,
                                const mlib_image *src1,
                                const mlib_image *src2);
mlib_status mlib_ImageXor_Bit(mlib_image       *dst,
                              const mlib_image *src1,
                              const mlib_image *src2);

mlib_status mlib_ImageConstAnd_Bit(mlib_image       *dst,
                                   const mlib_image *src,
                                   const mlib_s32   *c);
mlib_status mlib_ImageConstAndNot_Bit(mlib_image       *dst,
                                      const mlib_image *src,
                                      const mlib_s32   *c);
mlib_status mlib_ImageConstNotAnd_Bit(mlib_image       *dst,
                                      const mlib_image *src,
                                      const mlib_s32   *c);
mlib_status mlib_ImageConstNotOr_Bit(mlib_image       *dst,
                                     const mlib_image *src,
                                     const mlib_s32   *c);
mlib_status mlib_ImageConstNotXor_Bit(mlib_image       *dst,
                                      const mlib_image *src,
                                      const mlib_s32   *c);
mlib_status mlib_ImageConstOr_Bit(mlib_image       *dst,
                                  const mlib_image *src,
                                  const mlib_s32   *c);
mlib_status mlib_ImageConstOrNot_Bit(mlib_image       *dst,
                                     const mlib_image *src,
                                     const mlib_s32   *c);
mlib_status mlib_ImageConstXor_Bit(mlib_image       *dst,
                                   const mlib_image *src,
                                   const mlib_s32   *c);

#ifdef __cplusplus
}
#endif /* __cplusplus */
#endif /* __MLIB_IMAGELOGIC_H */
