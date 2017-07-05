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

#if !defined(JAVA2D_NO_MLIB) || defined(MLIB_ADD_SUFF)

#include <vis_proto.h>
#include <mlib_image.h>

#include "java2d_Mlib.h"
#include "AlphaMacros.h"

/***************************************************************/

extern const mlib_u32 vis_mul8s_tbl[];
extern const mlib_u64 vis_div8_tbl[];
extern const mlib_u64 vis_div8pre_tbl[];

/***************************************************************/

void IntArgbToIntAbgrConvert_line(mlib_s32 *srcBase,
                                  mlib_s32 *dstBase,
                                  mlib_s32 width);

/***************************************************************/

#define BUFF_SIZE  256

/***************************************************************/

#define COPY_NA(src, dst, _size) {                             \
    mlib_s32 cci, size = _size;                                \
    if (size <= 16) {                                          \
        for (cci = 0; cci < size; cci++) {                     \
            ((mlib_u8*)dst)[cci] = ((mlib_u8*)src)[cci];       \
        }                                                      \
    } else {                                                   \
        mlib_ImageCopy_na(src, dst, size);                     \
    }                                                          \
}

/***************************************************************/

#define MUL8_INT(x, y) mul8_tbl[256*(y) + (x)]

#define FMUL_16x16(x, y)       \
    vis_fpadd16(vis_fmul8sux16(x, y), vis_fmul8ulx16(x, y))

/***************************************************************/

#define MUL8_VIS(rr, alp)      \
    vis_fmul8x16al(rr, ((mlib_f32 *)vis_mul8s_tbl)[alp])

#define DIV_ALPHA(rr, alp) {                           \
    mlib_d64 d_div = ((mlib_d64*)vis_div8_tbl)[alp];   \
    rr = FMUL_16x16(rr, d_div);                        \
}

#define DIV_ALPHA_RGB(rr, alp)         \
    DIV_ALPHA(rr, alp)

/***************************************************************/

#define BLEND_VIS(rr, dstARGB, srcARGB, dstA, srcA)    \
{                                                      \
    mlib_d64 t0, t1;                                   \
                                                       \
    t0 = MUL8_VIS(srcARGB, srcA);                      \
    t1 = MUL8_VIS(dstARGB, dstA);                      \
    rr = vis_fpadd16(t0, t1);                          \
                                                       \
    dstA += srcA;                                      \
    DIV_ALPHA(rr, dstA);                               \
}

#define BLEND_VIS_RGB(rr, dstARGB, srcARGB, dstA, srcA)        \
    BLEND_VIS(rr, dstARGB, srcARGB, dstA, srcA)

/***************************************************************/

#if 0
extern const mlib_u16 vis_div8_16_tbl[];

#undef  BLEND_VIS
#define BLEND_VIS(rr, dstARGB, srcARGB, dstA, srcA)                    \
{                                                                      \
    mlib_d64 done = vis_to_double_dup(0x00FFFFFF);                     \
    mlib_d64 t0, t1;                                                   \
    mlib_f32 s0, s1;                                                   \
    mlib_s32 resA;                                                     \
                                                                       \
    resA = dstA + srcA;                                                \
    t0 = vis_ld_u16((mlib_u16*)vis_div8_16_tbl + 256*srcA + resA);     \
    t1 = vis_ld_u16((mlib_u16*)vis_div8_16_tbl + 256*dstA + resA);     \
    dstA = resA;                                                       \
                                                                       \
    t0 = vis_fmul8x16al(srcARGB, vis_read_lo(t0));                     \
    t1 = vis_fmul8x16al(dstARGB, vis_read_lo(t1));                     \
    rr = vis_fpadd16(t0, t1);                                          \
}

#define BLEND_VIS_RGB(rr, dstARGB, srcARGB, dstA, srcA)        \
{                                                              \
    mlib_d64 maskRGB = vis_to_double_dup(0x00FFFFFF);          \
                                                               \
    BLEND_VIS(rr, dstARGB, srcARGB, dstA, srcA)                \
                                                               \
    rr = vis_fand(rr, maskRGB);                                \
}

#endif

/***************************************************************/

#define F32_FROM_U8x4(x0, x1, x2, x3)          \
    vis_to_float(((x0) << 24) | ((x1) << 16) | ((x2)<< 8) | ((x3)))

/***************************************************************/

#define D64_FROM_U8x8(dd, val)         \
    val &= 0xFF;                       \
    val |= (val << 8);                 \
    val |= (val << 16);                \
    dd = vis_to_double_dup(val)

/***************************************************************/

#define D64_FROM_U16x4(dd, val)        \
    val &= 0xFFFF;                     \
    val |= (val << 16);                \
    dd = vis_to_double_dup(val)

/***************************************************************/

#define D64_FROM_F32x2(ff)     \
    vis_freg_pair(ff, ff)

/***************************************************************/

#if VIS >= 0x200

#define ARGB2ABGR_FL(src)      \
    src = vis_read_hi(vis_bshuffle(vis_freg_pair(src, vis_fzeros()), 0));

#define ARGB2ABGR_FL2(dst, src0, src1)         \
    dst = vis_freg_pair(src0, src1);           \
    dst = vis_bshuffle(dst, 0)

#define ARGB2ABGR_DB(src)      \
    src = vis_bshuffle(src, 0);

#else

#define ARGB2ABGR_FL(src) {                                    \
    mlib_d64 t0, t1, t2, t3;                                   \
    t0 = vis_fpmerge(src, src);                                \
    t1 = vis_fpmerge(vis_read_lo(t0), vis_read_hi(t0));        \
    t2 = vis_fpmerge(vis_read_hi(t0), vis_read_lo(t0));        \
    t3 = vis_fpmerge(vis_read_hi(t2), vis_read_lo(t1));        \
    src = vis_read_hi(t3);                                     \
}

#define ARGB2ABGR_FL2(dst, src0, src1) {                       \
    mlib_d64 t0, t1, t2;                                       \
    t0 = vis_fpmerge(src0, src1);                              \
    t1 = vis_fpmerge(vis_read_lo(t0), vis_read_hi(t0));        \
    t2 = vis_fpmerge(vis_read_hi(t0), vis_read_lo(t0));        \
    dst = vis_fpmerge(vis_read_hi(t2), vis_read_lo(t1));       \
}

#define ARGB2ABGR_DB(src)      \
    ARGB2ABGR_FL2(src, vis_read_hi(src), vis_read_lo(src))

#endif

/***************************************************************/

#endif /* JAVA2D_NO_MLIB */
