/*
 * Copyright (c) 1998, 2003, Oracle and/or its affiliates. All rights reserved.
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


#ifndef __ORIG_MLIB_IMAGE_BLEND_PROTO_H
#define __ORIG_MLIB_IMAGE_BLEND_PROTO_H

#include <mlib_types.h>
#include <mlib_status.h>
#include <mlib_image_types.h>
#if defined ( __MEDIALIB_OLD_NAMES_ADDED )
#include <../include/mlib_image_blend_proto.h>
#endif /* defined ( __MEDIALIB_OLD_NAMES_ADDED ) */

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#if defined ( _MSC_VER )
#if ! defined ( __MEDIALIB_OLD_NAMES )
#define __MEDIALIB_OLD_NAMES
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
#endif /* defined ( _MSC_VER ) */

/***********************************************************************

    NOTE: f = min(ALPHAsrc2, 1 - ALPHAsrc1)
          f = min(ALPHAscr2, 1 - ALPHAsrc1dst) for In-place function
          ALPHA = (ALPHA, ALPHA, ALPHA, ALPHA)

************************************************************************/

/* dst = 0 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_ZERO_ZERO mlib_ImageBlend_ZERO_ZERO
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_ZERO_ZERO(mlib_image *dst,
                                         const mlib_image *src1,
                                         const mlib_image *src2,
                                         mlib_s32 cmask);

/* src1dst = 0 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_ZERO_ZERO_Inp mlib_ImageBlend_ZERO_ZERO_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_ZERO_ZERO_Inp(mlib_image *src1dst,
                                             const mlib_image *src2,
                                             mlib_s32 cmask);

/* dst = src2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_ZERO_ONE mlib_ImageBlend_ZERO_ONE
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_ZERO_ONE(mlib_image *dst,
                                        const mlib_image *src1,
                                        const mlib_image *src2,
                                        mlib_s32 cmask);

/* src1dst = src2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_ZERO_ONE_Inp mlib_ImageBlend_ZERO_ONE_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_ZERO_ONE_Inp(mlib_image *src1dst,
                                            const mlib_image *src2,
                                            mlib_s32 cmask);

/* dst = src2 * src1 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_ZERO_DC mlib_ImageBlend_ZERO_DC
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_ZERO_DC(mlib_image *dst,
                                       const mlib_image *src1,
                                       const mlib_image *src2,
                                       mlib_s32 cmask);

/* src1dst = src2 * src1dst */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_ZERO_DC_Inp mlib_ImageBlend_ZERO_DC_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_ZERO_DC_Inp(mlib_image *src1dst,
                                           const mlib_image *src2,
                                           mlib_s32 cmask);

/* dst = src2 * (1 - src1) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_ZERO_OMDC mlib_ImageBlend_ZERO_OMDC
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_ZERO_OMDC(mlib_image *dst,
                                         const mlib_image *src1,
                                         const mlib_image *src2,
                                         mlib_s32 cmask);

/* src1dst = src2 * (1 - src1dst) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_ZERO_OMDC_Inp mlib_ImageBlend_ZERO_OMDC_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_ZERO_OMDC_Inp(mlib_image *src1dst,
                                             const mlib_image *src2,
                                             mlib_s32 cmask);

/* dst = src2 * ALPHAsrc2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_ZERO_SA mlib_ImageBlend_ZERO_SA
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_ZERO_SA(mlib_image *dst,
                                       const mlib_image *src1,
                                       const mlib_image *src2,
                                       mlib_s32 cmask);

/* src1dst = src2 * ALPHAsrc2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_ZERO_SA_Inp mlib_ImageBlend_ZERO_SA_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_ZERO_SA_Inp(mlib_image *src1dst,
                                           const mlib_image *src2,
                                           mlib_s32 cmask);

/* dst = src2 * (1 - ALPHAsrc2) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_ZERO_OMSA mlib_ImageBlend_ZERO_OMSA
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_ZERO_OMSA(mlib_image *dst,
                                         const mlib_image *src1,
                                         const mlib_image *src2,
                                         mlib_s32 cmask);

/* src1dst = src2 * (1 - ALPHAsrc2) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_ZERO_OMSA_Inp mlib_ImageBlend_ZERO_OMSA_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_ZERO_OMSA_Inp(mlib_image *src1dst,
                                             const mlib_image *src2,
                                             mlib_s32 cmask);

/* dst = src2 * ALPHAsrc1 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_ZERO_DA mlib_ImageBlend_ZERO_DA
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_ZERO_DA(mlib_image *dst,
                                       const mlib_image *src1,
                                       const mlib_image *src2,
                                       mlib_s32 cmask);

/* src1dst = src2 * ALPHAsrc1dst */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_ZERO_DA_Inp mlib_ImageBlend_ZERO_DA_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_ZERO_DA_Inp(mlib_image *src1dst,
                                           const mlib_image *src2,
                                           mlib_s32 cmask);

/* dst = src2 * (1 - ALPHAsrc1) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_ZERO_OMDA mlib_ImageBlend_ZERO_OMDA
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_ZERO_OMDA(mlib_image *dst,
                                         const mlib_image *src1,
                                         const mlib_image *src2,
                                         mlib_s32 cmask);

/* src1dst = src2 * (1 - ALPHAsrc1dst) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_ZERO_OMDA_Inp mlib_ImageBlend_ZERO_OMDA_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_ZERO_OMDA_Inp(mlib_image *src1dst,
                                             const mlib_image *src2,
                                             mlib_s32 cmask);

/* dst = src2 * (f, f, f, 1) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_ZERO_SAS mlib_ImageBlend_ZERO_SAS
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_ZERO_SAS(mlib_image *dst,
                                        const mlib_image *src1,
                                        const mlib_image *src2,
                                        mlib_s32 cmask);

/* src1dst = src2 * (f, f, f, 1) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_ZERO_SAS_Inp mlib_ImageBlend_ZERO_SAS_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_ZERO_SAS_Inp(mlib_image *src1dst,
                                            const mlib_image *src2,
                                            mlib_s32 cmask);

/* dst = src1 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_ONE_ZERO mlib_ImageBlend_ONE_ZERO
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_ONE_ZERO(mlib_image *dst,
                                        const mlib_image *src1,
                                        const mlib_image *src2,
                                        mlib_s32 cmask);

/* src1dst = src1dst */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_ONE_ZERO_Inp mlib_ImageBlend_ONE_ZERO_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_ONE_ZERO_Inp(mlib_image *src1dst,
                                            const mlib_image *src2,
                                            mlib_s32 cmask);

/* dst = src1 + src2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_ONE_ONE mlib_ImageBlend_ONE_ONE
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_ONE_ONE(mlib_image *dst,
                                       const mlib_image *src1,
                                       const mlib_image *src2,
                                       mlib_s32 cmask);

/* src1dst = src1dst + src2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_ONE_ONE_Inp mlib_ImageBlend_ONE_ONE_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_ONE_ONE_Inp(mlib_image *src1dst,
                                           const mlib_image *src2,
                                           mlib_s32 cmask);

/* dst = src1 * (1 + src2) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_ONE_DC mlib_ImageBlend_ONE_DC
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_ONE_DC(mlib_image *dst,
                                      const mlib_image *src1,
                                      const mlib_image *src2,
                                      mlib_s32 cmask);

/* src1dst = src1dst * (1 + src2) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_ONE_DC_Inp mlib_ImageBlend_ONE_DC_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_ONE_DC_Inp(mlib_image *src1dst,
                                          const mlib_image *src2,
                                          mlib_s32 cmask);

/* dst = src2 + src1 * (1 - src2) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_ONE_OMDC mlib_ImageBlend_ONE_OMDC
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_ONE_OMDC(mlib_image *dst,
                                        const mlib_image *src1,
                                        const mlib_image *src2,
                                        mlib_s32 cmask);

/* src1dst = src2 + src1dst * (1 - src2) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_ONE_OMDC_Inp mlib_ImageBlend_ONE_OMDC_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_ONE_OMDC_Inp(mlib_image *src1dst,
                                            const mlib_image *src2,
                                            mlib_s32 cmask);

/* dst = src1 + src2 * ALPHAsrc2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_ONE_SA mlib_ImageBlend_ONE_SA
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_ONE_SA(mlib_image *dst,
                                      const mlib_image *src1,
                                      const mlib_image *src2,
                                      mlib_s32 cmask);

/* src1dst = src1dst + src2 * ALPHAsrc2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_ONE_SA_Inp mlib_ImageBlend_ONE_SA_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_ONE_SA_Inp(mlib_image *src1dst,
                                          const mlib_image *src2,
                                          mlib_s32 cmask);

/* dst = src1 + src2 * (1 - ALPHAsrc2) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_ONE_OMSA mlib_ImageBlend_ONE_OMSA
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_ONE_OMSA(mlib_image *dst,
                                        const mlib_image *src1,
                                        const mlib_image *src2,
                                        mlib_s32 cmask);

/* src1dst = src1dst + src2 * (1 - ALPHAsrc2) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_ONE_OMSA_Inp mlib_ImageBlend_ONE_OMSA_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_ONE_OMSA_Inp(mlib_image *src1dst,
                                            const mlib_image *src2,
                                            mlib_s32 cmask);

/* dst = src1 + src2 * ALPHAsrc1 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_ONE_DA mlib_ImageBlend_ONE_DA
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_ONE_DA(mlib_image *dst,
                                      const mlib_image *src1,
                                      const mlib_image *src2,
                                      mlib_s32 cmask);

/* src1dst = src1dst + src2 * ALPHAsrc1dst */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_ONE_DA_Inp mlib_ImageBlend_ONE_DA_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_ONE_DA_Inp(mlib_image *src1dst,
                                          const mlib_image *src2,
                                          mlib_s32 cmask);

/* dst = src1 + src2 * (1 - ALPHAsrc1) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_ONE_OMDA mlib_ImageBlend_ONE_OMDA
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_ONE_OMDA(mlib_image *dst,
                                        const mlib_image *src1,
                                        const mlib_image *src2,
                                        mlib_s32 cmask);

/* src1dst = src1dst + src2 * (1 - ALPHAsrc1dst) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_ONE_OMDA_Inp mlib_ImageBlend_ONE_OMDA_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_ONE_OMDA_Inp(mlib_image *src1dst,
                                            const mlib_image *src2,
                                            mlib_s32 cmask);

/* dst = src1 + src2 * (f, f, f, 1) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_ONE_SAS mlib_ImageBlend_ONE_SAS
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_ONE_SAS(mlib_image *dst,
                                       const mlib_image *src1,
                                       const mlib_image *src2,
                                       mlib_s32 cmask);

/* src1dst = src1dst + src2 * (f, f, f, 1) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_ONE_SAS_Inp mlib_ImageBlend_ONE_SAS_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_ONE_SAS_Inp(mlib_image *src1dst,
                                           const mlib_image *src2,
                                           mlib_s32 cmask);

/* dst = src1 * src2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_SC_ZERO mlib_ImageBlend_SC_ZERO
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_SC_ZERO(mlib_image *dst,
                                       const mlib_image *src1,
                                       const mlib_image *src2,
                                       mlib_s32 cmask);

/* src1dst = src1dst * src2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_SC_ZERO_Inp mlib_ImageBlend_SC_ZERO_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_SC_ZERO_Inp(mlib_image *src1dst,
                                           const mlib_image *src2,
                                           mlib_s32 cmask);

/* dst = (src1 + 1) * src2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_SC_ONE mlib_ImageBlend_SC_ONE
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_SC_ONE(mlib_image *dst,
                                      const mlib_image *src1,
                                      const mlib_image *src2,
                                      mlib_s32 cmask);

/* src1dst = (src1dst + 1) * src2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_SC_ONE_Inp mlib_ImageBlend_SC_ONE_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_SC_ONE_Inp(mlib_image *src1dst,
                                          const mlib_image *src2,
                                          mlib_s32 cmask);

/* dst = 2 * src1 * src2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_SC_DC mlib_ImageBlend_SC_DC
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_SC_DC(mlib_image *dst,
                                     const mlib_image *src1,
                                     const mlib_image *src2,
                                     mlib_s32 cmask);

/* src1dst = 2 * src1dst * src2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_SC_DC_Inp mlib_ImageBlend_SC_DC_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_SC_DC_Inp(mlib_image *src1dst,
                                         const mlib_image *src2,
                                         mlib_s32 cmask);

/* dst = src2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_SC_OMDC mlib_ImageBlend_SC_OMDC
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_SC_OMDC(mlib_image *dst,
                                       const mlib_image *src1,
                                       const mlib_image *src2,
                                       mlib_s32 cmask);

/* src1dst = src2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_SC_OMDC_Inp mlib_ImageBlend_SC_OMDC_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_SC_OMDC_Inp(mlib_image *src1dst,
                                           const mlib_image *src2,
                                           mlib_s32 cmask);

/* dst = src2 * (src1 + ALPHAsrc2) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_SC_SA mlib_ImageBlend_SC_SA
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_SC_SA(mlib_image *dst,
                                     const mlib_image *src1,
                                     const mlib_image *src2,
                                     mlib_s32 cmask);

/* src1dst = src2 * (src1dst + ALPHAsrc2) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_SC_SA_Inp mlib_ImageBlend_SC_SA_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_SC_SA_Inp(mlib_image *src1dst,
                                         const mlib_image *src2,
                                         mlib_s32 cmask);

/* dst = src2 * (1 - ALPHAsrc2 + src1) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_SC_OMSA mlib_ImageBlend_SC_OMSA
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_SC_OMSA(mlib_image *dst,
                                       const mlib_image *src1,
                                       const mlib_image *src2,
                                       mlib_s32 cmask);

/* src1dst = src2 * (1 - ALPHAsrc2 + src1dst) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_SC_OMSA_Inp mlib_ImageBlend_SC_OMSA_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_SC_OMSA_Inp(mlib_image *src1dst,
                                           const mlib_image *src2,
                                           mlib_s32 cmask);

/* dst = src2 * (src1 + ALPHAsrc1) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_SC_DA mlib_ImageBlend_SC_DA
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_SC_DA(mlib_image *dst,
                                     const mlib_image *src1,
                                     const mlib_image *src2,
                                     mlib_s32 cmask);

/* src1dst = src2 * (src1dst + ALPHAsrc1dst) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_SC_DA_Inp mlib_ImageBlend_SC_DA_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_SC_DA_Inp(mlib_image *src1dst,
                                         const mlib_image *src2,
                                         mlib_s32 cmask);

/* dst = src2 * (1 - ALPHAsrc1 + src1) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_SC_OMDA mlib_ImageBlend_SC_OMDA
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_SC_OMDA(mlib_image *dst,
                                       const mlib_image *src1,
                                       const mlib_image *src2,
                                       mlib_s32 cmask);

/* src1dst = src2 * (1 - ALPHAsrc1dst + src1dst) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_SC_OMDA_Inp mlib_ImageBlend_SC_OMDA_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_SC_OMDA_Inp(mlib_image *src1dst,
                                           const mlib_image *src2,
                                           mlib_s32 cmask);

/* dst = src2 * ((f, f, f, 1) + src1) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_SC_SAS mlib_ImageBlend_SC_SAS
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_SC_SAS(mlib_image *dst,
                                      const mlib_image *src1,
                                      const mlib_image *src2,
                                      mlib_s32 cmask);

/* src1dst = src2 * ((f, f, f, 1) + src1dst) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_SC_SAS_Inp mlib_ImageBlend_SC_SAS_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_SC_SAS_Inp(mlib_image *src1dst,
                                          const mlib_image *src2,
                                          mlib_s32 cmask);

/* dst = src1 * (1 - src2) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMSC_ZERO mlib_ImageBlend_OMSC_ZERO
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMSC_ZERO(mlib_image *dst,
                                         const mlib_image *src1,
                                         const mlib_image *src2,
                                         mlib_s32 cmask);

/* src1dst = src1dst * (1 - src2) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMSC_ZERO_Inp mlib_ImageBlend_OMSC_ZERO_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMSC_ZERO_Inp(mlib_image *src1dst,
                                             const mlib_image *src2,
                                             mlib_s32 cmask);

/* dst = src1 + src2 * (1 - src1) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMSC_ONE mlib_ImageBlend_OMSC_ONE
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMSC_ONE(mlib_image *dst,
                                        const mlib_image *src1,
                                        const mlib_image *src2,
                                        mlib_s32 cmask);

/* src1dst = src1dst + src2 * (1 - src1dst) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMSC_ONE_Inp mlib_ImageBlend_OMSC_ONE_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMSC_ONE_Inp(mlib_image *src1dst,
                                            const mlib_image *src2,
                                            mlib_s32 cmask);

/* dst = src1 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMSC_DC mlib_ImageBlend_OMSC_DC
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMSC_DC(mlib_image *dst,
                                       const mlib_image *src1,
                                       const mlib_image *src2,
                                       mlib_s32 cmask);

/* src1dst = src1dst */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMSC_DC_Inp mlib_ImageBlend_OMSC_DC_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMSC_DC_Inp(mlib_image *src1dst,
                                           const mlib_image *src2,
                                           mlib_s32 cmask);

/* dst = src1 + src2 - 2 * src1 * src2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMSC_OMDC mlib_ImageBlend_OMSC_OMDC
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMSC_OMDC(mlib_image *dst,
                                         const mlib_image *src1,
                                         const mlib_image *src2,
                                         mlib_s32 cmask);

/* src1dst = src1dst + src2 - 2 * src1dst * src2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMSC_OMDC_Inp mlib_ImageBlend_OMSC_OMDC_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMSC_OMDC_Inp(mlib_image *src1dst,
                                             const mlib_image *src2,
                                             mlib_s32 cmask);

/* dst = src1 + src2 * (ALPHAsrc2 - src1) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMSC_SA mlib_ImageBlend_OMSC_SA
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMSC_SA(mlib_image *dst,
                                       const mlib_image *src1,
                                       const mlib_image *src2,
                                       mlib_s32 cmask);

/* src1dst = src1dst + src2 * (ALPHAsrc2 - src1dst) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMSC_SA_Inp mlib_ImageBlend_OMSC_SA_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMSC_SA_Inp(mlib_image *src1dst,
                                           const mlib_image *src2,
                                           mlib_s32 cmask);

/* dst = src1 + src2 - src2 * (src1 + ALPHAsrc2) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMSC_OMSA mlib_ImageBlend_OMSC_OMSA
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMSC_OMSA(mlib_image *dst,
                                         const mlib_image *src1,
                                         const mlib_image *src2,
                                         mlib_s32 cmask);

/* src1dst = src1dst + src2 - src2 * (src1dst + ALPHAsrc2) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMSC_OMSA_Inp mlib_ImageBlend_OMSC_OMSA_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMSC_OMSA_Inp(mlib_image *src1dst,
                                             const mlib_image *src2,
                                             mlib_s32 cmask);

/* dst = src1 + src2 * (ALPHAsrc1 - src1) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMSC_DA mlib_ImageBlend_OMSC_DA
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMSC_DA(mlib_image *dst,
                                       const mlib_image *src1,
                                       const mlib_image *src2,
                                       mlib_s32 cmask);

/* src1dst = src1dst + src2 * (ALPHAsrc1dst - src1dst) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMSC_DA_Inp mlib_ImageBlend_OMSC_DA_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMSC_DA_Inp(mlib_image *src1dst,
                                           const mlib_image *src2,
                                           mlib_s32 cmask);

/* dst = src1 + src2 - src2 * (src1 + ALPHAsrc1) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMSC_OMDA mlib_ImageBlend_OMSC_OMDA
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMSC_OMDA(mlib_image *dst,
                                         const mlib_image *src1,
                                         const mlib_image *src2,
                                         mlib_s32 cmask);

/* src1dst = src1dst + src2 - src2 * (src1dst + ALPHAsrc1dst) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMSC_OMDA_Inp mlib_ImageBlend_OMSC_OMDA_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMSC_OMDA_Inp(mlib_image *src1dst,
                                             const mlib_image *src2,
                                             mlib_s32 cmask);

/* dst = src1 +  src2 * ((f, f, f, 1) - src1) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMSC_SAS mlib_ImageBlend_OMSC_SAS
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMSC_SAS(mlib_image *dst,
                                        const mlib_image *src1,
                                        const mlib_image *src2,
                                        mlib_s32 cmask);

/* src1dst = src1dst +  src2 * ((f, f, f, 1) - src1dst) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMSC_SAS_Inp mlib_ImageBlend_OMSC_SAS_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMSC_SAS_Inp(mlib_image *src1dst,
                                            const mlib_image *src2,
                                            mlib_s32 cmask);

/* dst = src1 * ALPHAsrc2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_SA_ZERO mlib_ImageBlend_SA_ZERO
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_SA_ZERO(mlib_image *dst,
                                       const mlib_image *src1,
                                       const mlib_image *src2,
                                       mlib_s32 cmask);

/* src1dst = src1dst * ALPHAsrc2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_SA_ZERO_Inp mlib_ImageBlend_SA_ZERO_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_SA_ZERO_Inp(mlib_image *src1dst,
                                           const mlib_image *src2,
                                           mlib_s32 cmask);

/* dst = src1 * ALPHAsrc2 + src2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_SA_ONE mlib_ImageBlend_SA_ONE
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_SA_ONE(mlib_image *dst,
                                      const mlib_image *src1,
                                      const mlib_image *src2,
                                      mlib_s32 cmask);

/* src1dst = src1dst * ALPHAsrc2 + src2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_SA_ONE_Inp mlib_ImageBlend_SA_ONE_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_SA_ONE_Inp(mlib_image *src1dst,
                                          const mlib_image *src2,
                                          mlib_s32 cmask);

/* dst = src1 * (ALPHAsrc2 + src2) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_SA_DC mlib_ImageBlend_SA_DC
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_SA_DC(mlib_image *dst,
                                     const mlib_image *src1,
                                     const mlib_image *src2,
                                     mlib_s32 cmask);

/* src1dst = src1dst * (ALPHAsrc2 + src2) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_SA_DC_Inp mlib_ImageBlend_SA_DC_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_SA_DC_Inp(mlib_image *src1dst,
                                         const mlib_image *src2,
                                         mlib_s32 cmask);

/* dst = src1 * (ALPHAsrc2 - src2) + src2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_SA_OMDC mlib_ImageBlend_SA_OMDC
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_SA_OMDC(mlib_image *dst,
                                       const mlib_image *src1,
                                       const mlib_image *src2,
                                       mlib_s32 cmask);

/* src1dst = src1dst * (ALPHAsrc2 - src2) + src2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_SA_OMDC_Inp mlib_ImageBlend_SA_OMDC_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_SA_OMDC_Inp(mlib_image *src1dst,
                                           const mlib_image *src2,
                                           mlib_s32 cmask);

/* dst = (src1 + src2) * ALPHAsrc2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_SA_SA mlib_ImageBlend_SA_SA
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_SA_SA(mlib_image *dst,
                                     const mlib_image *src1,
                                     const mlib_image *src2,
                                     mlib_s32 cmask);

/* src1dst = (src1dst + src2) * ALPHAsrc2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_SA_SA_Inp mlib_ImageBlend_SA_SA_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_SA_SA_Inp(mlib_image *src1dst,
                                         const mlib_image *src2,
                                         mlib_s32 cmask);

/* dst = (src1 - src2) * ALPHAsrc2 + src2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_SA_OMSA mlib_ImageBlend_SA_OMSA
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_SA_OMSA(mlib_image *dst,
                                       const mlib_image *src1,
                                       const mlib_image *src2,
                                       mlib_s32 cmask);

/* src1dst = (src1dst - src2) * ALPHAsrc2 + src2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_SA_OMSA_Inp mlib_ImageBlend_SA_OMSA_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_SA_OMSA_Inp(mlib_image *src1dst,
                                           const mlib_image *src2,
                                           mlib_s32 cmask);

/* dst = src1 * ALPHAsrc2 + src2 * ALPHAsrc1 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_SA_DA mlib_ImageBlend_SA_DA
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_SA_DA(mlib_image *dst,
                                     const mlib_image *src1,
                                     const mlib_image *src2,
                                     mlib_s32 cmask);

/* src1dst = src1dst * ALPHAsrc2 + src2 * ALPHAsrc1dst */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_SA_DA_Inp mlib_ImageBlend_SA_DA_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_SA_DA_Inp(mlib_image *src1dst,
                                         const mlib_image *src2,
                                         mlib_s32 cmask);

/* dst = src1 * ALPHAsrc2 + src2 * (1 - ALPHAsrc1) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_SA_OMDA mlib_ImageBlend_SA_OMDA
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_SA_OMDA(mlib_image *dst,
                                       const mlib_image *src1,
                                       const mlib_image *src2,
                                       mlib_s32 cmask);

/* src1dst = src1dst * ALPHAsrc2 + src2 * (1 - ALPHAsrc1dst) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_SA_OMDA_Inp mlib_ImageBlend_SA_OMDA_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_SA_OMDA_Inp(mlib_image *src1dst,
                                           const mlib_image *src2,
                                           mlib_s32 cmask);

/* dst = src1 * ALPHAsrc2 + src2 * (f, f, f, 1) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_SA_SAS mlib_ImageBlend_SA_SAS
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_SA_SAS(mlib_image *dst,
                                      const mlib_image *src1,
                                      const mlib_image *src2,
                                      mlib_s32 cmask);

/* src1dst = src1dst * ALPHAsrc2 + src2 * (f, f, f, 1) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_SA_SAS_Inp mlib_ImageBlend_SA_SAS_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_SA_SAS_Inp(mlib_image *src1dst,
                                          const mlib_image *src2,
                                          mlib_s32 cmask);

/* dst = src1 * (1 - ALPHAsrc2) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMSA_ZERO mlib_ImageBlend_OMSA_ZERO
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMSA_ZERO(mlib_image *dst,
                                         const mlib_image *src1,
                                         const mlib_image *src2,
                                         mlib_s32 cmask);

/* src1dst = src1dst * (1 - ALPHAsrc2) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMSA_ZERO_Inp mlib_ImageBlend_OMSA_ZERO_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMSA_ZERO_Inp(mlib_image *src1dst,
                                             const mlib_image *src2,
                                             mlib_s32 cmask);

/* dst = src1 * (1 - ALPHAsrc2) + src2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMSA_ONE mlib_ImageBlend_OMSA_ONE
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMSA_ONE(mlib_image *dst,
                                        const mlib_image *src1,
                                        const mlib_image *src2,
                                        mlib_s32 cmask);

/* src1dst = src1dst * (1 - ALPHAsrc2) + src2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMSA_ONE_Inp mlib_ImageBlend_OMSA_ONE_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMSA_ONE_Inp(mlib_image *src1dst,
                                            const mlib_image *src2,
                                            mlib_s32 cmask);

/* dst = src1 * (1 - ALPHAsrc2 + src2) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMSA_DC mlib_ImageBlend_OMSA_DC
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMSA_DC(mlib_image *dst,
                                       const mlib_image *src1,
                                       const mlib_image *src2,
                                       mlib_s32 cmask);

/* src1dst = src1dst * (1 - ALPHAsrc2 + src2) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMSA_DC_Inp mlib_ImageBlend_OMSA_DC_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMSA_DC_Inp(mlib_image *src1dst,
                                           const mlib_image *src2,
                                           mlib_s32 cmask);

/* dst = src1 * (1 - ALPHAsrc2 - src2) + src2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMSA_OMDC mlib_ImageBlend_OMSA_OMDC
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMSA_OMDC(mlib_image *dst,
                                         const mlib_image *src1,
                                         const mlib_image *src2,
                                         mlib_s32 cmask);

/* src1dst = src1dst * (1 - ALPHAsrc2 - src2) + src2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMSA_OMDC_Inp mlib_ImageBlend_OMSA_OMDC_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMSA_OMDC_Inp(mlib_image *src1dst,
                                             const mlib_image *src2,
                                             mlib_s32 cmask);

/* dst = src1 + (src2 - src1) * ALPHAsrc2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMSA_SA mlib_ImageBlend_OMSA_SA
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMSA_SA(mlib_image *dst,
                                       const mlib_image *src1,
                                       const mlib_image *src2,
                                       mlib_s32 cmask);

/* src1dst = src1dst + (src2 - src1dst) * ALPHAsrc2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMSA_SA_Inp mlib_ImageBlend_OMSA_SA_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMSA_SA_Inp(mlib_image *src1dst,
                                           const mlib_image *src2,
                                           mlib_s32 cmask);

/* dst = (src1 + src2) * (1 - ALPHAsrc2) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMSA_OMSA mlib_ImageBlend_OMSA_OMSA
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMSA_OMSA(mlib_image *dst,
                                         const mlib_image *src1,
                                         const mlib_image *src2,
                                         mlib_s32 cmask);

/* src1dst = (src1dst + src2) * (1 - ALPHAsrc2) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMSA_OMSA_Inp mlib_ImageBlend_OMSA_OMSA_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMSA_OMSA_Inp(mlib_image *src1dst,
                                             const mlib_image *src2,
                                             mlib_s32 cmask);

/* dst = src1 * (1 - ALPHAsrc2) + src2 * ALPHAsrc1 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMSA_DA mlib_ImageBlend_OMSA_DA
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMSA_DA(mlib_image *dst,
                                       const mlib_image *src1,
                                       const mlib_image *src2,
                                       mlib_s32 cmask);

/* src1dst = src1dst * (1 - ALPHAsrc2) + src2 * ALPHAsrc1dst */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMSA_DA_Inp mlib_ImageBlend_OMSA_DA_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMSA_DA_Inp(mlib_image *src1dst,
                                           const mlib_image *src2,
                                           mlib_s32 cmask);

/* dst = src1 * (1 - ALPHAsrc2) + src2 * (1 - ALPHAsrc1) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMSA_OMDA mlib_ImageBlend_OMSA_OMDA
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMSA_OMDA(mlib_image *dst,
                                         const mlib_image *src1,
                                         const mlib_image *src2,
                                         mlib_s32 cmask);

/* src1dst = src1dst * (1 - ALPHAsrc2) + src2 * (1 - ALPHAsrc1dst) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMSA_OMDA_Inp mlib_ImageBlend_OMSA_OMDA_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMSA_OMDA_Inp(mlib_image *src1dst,
                                             const mlib_image *src2,
                                             mlib_s32 cmask);

/* dst = src1 * (1 - ALPHAsrc2) + src2 * (f, f, f, 1) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMSA_SAS mlib_ImageBlend_OMSA_SAS
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMSA_SAS(mlib_image *dst,
                                        const mlib_image *src1,
                                        const mlib_image *src2,
                                        mlib_s32 cmask);

/* src1dst = src1dst * (1 - ALPHAsrc2) + src2 * (f, f, f, 1) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMSA_SAS_Inp mlib_ImageBlend_OMSA_SAS_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMSA_SAS_Inp(mlib_image *src1dst,
                                            const mlib_image *src2,
                                            mlib_s32 cmask);

/* dst = src1 * ALPHAsrc1 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_DA_ZERO mlib_ImageBlend_DA_ZERO
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_DA_ZERO(mlib_image *dst,
                                       const mlib_image *src1,
                                       const mlib_image *src2,
                                       mlib_s32 cmask);

/* src1dst = src1dst * ALPHAsrc1dst */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_DA_ZERO_Inp mlib_ImageBlend_DA_ZERO_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_DA_ZERO_Inp(mlib_image *src1dst,
                                           const mlib_image *src2,
                                           mlib_s32 cmask);

/* dst = src1 * ALPHAsrc1 + src2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_DA_ONE mlib_ImageBlend_DA_ONE
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_DA_ONE(mlib_image *dst,
                                      const mlib_image *src1,
                                      const mlib_image *src2,
                                      mlib_s32 cmask);

/* src1dst = src1dst * ALPHAsrc1dst + src2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_DA_ONE_Inp mlib_ImageBlend_DA_ONE_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_DA_ONE_Inp(mlib_image *src1dst,
                                          const mlib_image *src2,
                                          mlib_s32 cmask);

/* dst = src1 * (ALPHAsrc1 + src2) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_DA_DC mlib_ImageBlend_DA_DC
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_DA_DC(mlib_image *dst,
                                     const mlib_image *src1,
                                     const mlib_image *src2,
                                     mlib_s32 cmask);

/* src1dst = src1dst * (ALPHAsrc1dst + src2) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_DA_DC_Inp mlib_ImageBlend_DA_DC_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_DA_DC_Inp(mlib_image *src1dst,
                                         const mlib_image *src2,
                                         mlib_s32 cmask);

/* dst = src1 * (ALPHAsrc1 - src2) + src2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_DA_OMDC mlib_ImageBlend_DA_OMDC
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_DA_OMDC(mlib_image *dst,
                                       const mlib_image *src1,
                                       const mlib_image *src2,
                                       mlib_s32 cmask);

/* src1dst = src1dst * (ALPHAsrc1dst - src2) + src2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_DA_OMDC_Inp mlib_ImageBlend_DA_OMDC_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_DA_OMDC_Inp(mlib_image *src1dst,
                                           const mlib_image *src2,
                                           mlib_s32 cmask);

/* dst = src1 * ALPHAsrc1 + src2 * ALPHAsrc2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_DA_SA mlib_ImageBlend_DA_SA
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_DA_SA(mlib_image *dst,
                                     const mlib_image *src1,
                                     const mlib_image *src2,
                                     mlib_s32 cmask);

/* src1dst = src1dst * ALPHAsrc1dst + src2 * ALPHAsrc2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_DA_SA_Inp mlib_ImageBlend_DA_SA_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_DA_SA_Inp(mlib_image *src1dst,
                                         const mlib_image *src2,
                                         mlib_s32 cmask);

/* dst = src1 * ALPHAsrc1 + src2 * (1 - ALPHAsrc2) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_DA_OMSA mlib_ImageBlend_DA_OMSA
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_DA_OMSA(mlib_image *dst,
                                       const mlib_image *src1,
                                       const mlib_image *src2,
                                       mlib_s32 cmask);

/* src1dst = src1dst * ALPHAsrc1dst + src2 * (1 - ALPHAsrc2) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_DA_OMSA_Inp mlib_ImageBlend_DA_OMSA_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_DA_OMSA_Inp(mlib_image *src1dst,
                                           const mlib_image *src2,
                                           mlib_s32 cmask);

/* dst = (src1 + src2) * ALPHAsrc1 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_DA_DA mlib_ImageBlend_DA_DA
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_DA_DA(mlib_image *dst,
                                     const mlib_image *src1,
                                     const mlib_image *src2,
                                     mlib_s32 cmask);

/* src1dst = (src1dst + src2) * ALPHAsrc1dst */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_DA_DA_Inp mlib_ImageBlend_DA_DA_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_DA_DA_Inp(mlib_image *src1dst,
                                         const mlib_image *src2,
                                         mlib_s32 cmask);

/* dst = (src1 - src2) * ALPHAsrc1 + src2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_DA_OMDA mlib_ImageBlend_DA_OMDA
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_DA_OMDA(mlib_image *dst,
                                       const mlib_image *src1,
                                       const mlib_image *src2,
                                       mlib_s32 cmask);

/* src1dst = (src1dst - src2) * ALPHAsrc1dst + src2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_DA_OMDA_Inp mlib_ImageBlend_DA_OMDA_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_DA_OMDA_Inp(mlib_image *src1dst,
                                           const mlib_image *src2,
                                           mlib_s32 cmask);

/* dst = src1 * ALPHAsrc1 + src2 * (f, f, f, 1) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_DA_SAS mlib_ImageBlend_DA_SAS
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_DA_SAS(mlib_image *dst,
                                      const mlib_image *src1,
                                      const mlib_image *src2,
                                      mlib_s32 cmask);

/* src1dst = src1dst * ALPHAsrc1dst + src2 * (f, f, f, 1) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_DA_SAS_Inp mlib_ImageBlend_DA_SAS_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_DA_SAS_Inp(mlib_image *src1dst,
                                          const mlib_image *src2,
                                          mlib_s32 cmask);

/* dst = src1 * (1 - ALPHAsrc1) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMDA_ZERO mlib_ImageBlend_OMDA_ZERO
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMDA_ZERO(mlib_image *dst,
                                         const mlib_image *src1,
                                         const mlib_image *src2,
                                         mlib_s32 cmask);

/* src1dst = src1dst * (1 - ALPHAsrc1dst) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMDA_ZERO_Inp mlib_ImageBlend_OMDA_ZERO_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMDA_ZERO_Inp(mlib_image *src1dst,
                                             const mlib_image *src2,
                                             mlib_s32 cmask);

/* dst = src1 * (1 - ALPHAsrc1) + src2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMDA_ONE mlib_ImageBlend_OMDA_ONE
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMDA_ONE(mlib_image *dst,
                                        const mlib_image *src1,
                                        const mlib_image *src2,
                                        mlib_s32 cmask);

/* src1dst = src1dst * (1 - ALPHAsrc1dst) + src2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMDA_ONE_Inp mlib_ImageBlend_OMDA_ONE_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMDA_ONE_Inp(mlib_image *src1dst,
                                            const mlib_image *src2,
                                            mlib_s32 cmask);

/* dst = src1 * (1 - ALPHAsrc1 + src2) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMDA_DC mlib_ImageBlend_OMDA_DC
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMDA_DC(mlib_image *dst,
                                       const mlib_image *src1,
                                       const mlib_image *src2,
                                       mlib_s32 cmask);

/* src1dst = src1dst * (1 - ALPHAsrc1dst + src2) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMDA_DC_Inp mlib_ImageBlend_OMDA_DC_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMDA_DC_Inp(mlib_image *src1dst,
                                           const mlib_image *src2,
                                           mlib_s32 cmask);

/* dst = src1 * (1 - ALPHAsrc1 - src2) + src2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMDA_OMDC mlib_ImageBlend_OMDA_OMDC
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMDA_OMDC(mlib_image *dst,
                                         const mlib_image *src1,
                                         const mlib_image *src2,
                                         mlib_s32 cmask);

/* src1dst = src1dst * (1 - ALPHAsrc1dst - src2) + src2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMDA_OMDC_Inp mlib_ImageBlend_OMDA_OMDC_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMDA_OMDC_Inp(mlib_image *src1dst,
                                             const mlib_image *src2,
                                             mlib_s32 cmask);

/* dst = src1 * (1 - ALPHAsrc1) + src2 * ALPHAsrc2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMDA_SA mlib_ImageBlend_OMDA_SA
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMDA_SA(mlib_image *dst,
                                       const mlib_image *src1,
                                       const mlib_image *src2,
                                       mlib_s32 cmask);

/* src1dst = src1dst * (1 - ALPHAsrc1dst) + src2 * ALPHAsrc2 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMDA_SA_Inp mlib_ImageBlend_OMDA_SA_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMDA_SA_Inp(mlib_image *src1dst,
                                           const mlib_image *src2,
                                           mlib_s32 cmask);

/* dst = src1 * (1 - ALPHAsrc1) + src2 * (1 - ALPHAsrc2) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMDA_OMSA mlib_ImageBlend_OMDA_OMSA
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMDA_OMSA(mlib_image *dst,
                                         const mlib_image *src1,
                                         const mlib_image *src2,
                                         mlib_s32 cmask);

/* src1dst = src1dst * (1 - ALPHAsrc1dst) + src2 * (1 - ALPHAsrc2) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMDA_OMSA_Inp mlib_ImageBlend_OMDA_OMSA_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMDA_OMSA_Inp(mlib_image *src1dst,
                                             const mlib_image *src2,
                                             mlib_s32 cmask);

/* dst = src1 + (src2 - src1) * ALPHAsrc1 */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMDA_DA mlib_ImageBlend_OMDA_DA
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMDA_DA(mlib_image *dst,
                                       const mlib_image *src1,
                                       const mlib_image *src2,
                                       mlib_s32 cmask);

/* src1dst = src1dst + (src2 - src1dst) * ALPHAsrc1dst */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMDA_DA_Inp mlib_ImageBlend_OMDA_DA_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMDA_DA_Inp(mlib_image *src1dst,
                                           const mlib_image *src2,
                                           mlib_s32 cmask);

/* dst = (src1 + src2) * (1 - ALPHAsrc1) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMDA_OMDA mlib_ImageBlend_OMDA_OMDA
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMDA_OMDA(mlib_image *dst,
                                         const mlib_image *src1,
                                         const mlib_image *src2,
                                         mlib_s32 cmask);

/* src1dst = (src1dst + src2) * (1 - ALPHAsrc1dst) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMDA_OMDA_Inp mlib_ImageBlend_OMDA_OMDA_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMDA_OMDA_Inp(mlib_image *src1dst,
                                             const mlib_image *src2,
                                             mlib_s32 cmask);

/* dst = src1 * (1 - ALPHAsrc1) + src2 * (f, f, f, 1) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMDA_SAS mlib_ImageBlend_OMDA_SAS
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMDA_SAS(mlib_image *dst,
                                        const mlib_image *src1,
                                        const mlib_image *src2,
                                        mlib_s32 cmask);

/* src1dst = src1dst * (1 - ALPHAsrc1dst) + src2 * (f, f, f, 1) */

#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageBlend_OMDA_SAS_Inp mlib_ImageBlend_OMDA_SAS_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageBlend_OMDA_SAS_Inp(mlib_image *src1dst,
                                            const mlib_image *src2,
                                            mlib_s32 cmask);



#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageComposite mlib_ImageComposite
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageComposite(mlib_image *dst,
                                   const mlib_image *src1,
                                   const mlib_image *src2,
                                   mlib_blend bsrc1,
                                   mlib_blend bsrc2,
                                   mlib_s32 cmask);


#if defined ( __MEDIALIB_OLD_NAMES )
#define __mlib_ImageComposite_Inp mlib_ImageComposite_Inp
#endif /* ! defined ( __MEDIALIB_OLD_NAMES ) */
mlib_status  __mlib_ImageComposite_Inp(mlib_image *src1dst,
                                       const mlib_image *src2,
                                       mlib_blend bsrc1,
                                       mlib_blend bsrc2,
                                       mlib_s32 cmask);

#ifdef __cplusplus
}
#endif /* __cplusplus */
#endif /* __ORIG_MLIB_IMAGE_BLEND_PROTO_H */
