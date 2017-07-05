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

#include "vis_AlphaMacros.h"

/***************************************************************/

#define GBR_PIXEL(i)   \
    0xFF000000 | (src[3*i + 2] << 16) | (src[3*i + 1] << 8) | src[3*i]

/***************************************************************/

#define BGR_TO_ARGB {                                          \
    mlib_d64 sda, sdb, sdc, sdd, sde, sdf;                     \
    mlib_d64 s_1, s_2, s_3, a13, b13, a02, b02;                \
                                                               \
    sda = vis_fpmerge(vis_read_hi(sd0), vis_read_lo(sd1));     \
    sdb = vis_fpmerge(vis_read_lo(sd0), vis_read_hi(sd2));     \
    sdc = vis_fpmerge(vis_read_hi(sd1), vis_read_lo(sd2));     \
                                                               \
    sdd = vis_fpmerge(vis_read_hi(sda), vis_read_lo(sdb));     \
    sde = vis_fpmerge(vis_read_lo(sda), vis_read_hi(sdc));     \
    sdf = vis_fpmerge(vis_read_hi(sdb), vis_read_lo(sdc));     \
                                                               \
    s_3 = vis_fpmerge(vis_read_hi(sdd), vis_read_lo(sde));     \
    s_2 = vis_fpmerge(vis_read_lo(sdd), vis_read_hi(sdf));     \
    s_1 = vis_fpmerge(vis_read_hi(sde), vis_read_lo(sdf));     \
                                                               \
    a13 = vis_fpmerge(vis_read_hi(s_1), vis_read_hi(s_3));     \
    b13 = vis_fpmerge(vis_read_lo(s_1), vis_read_lo(s_3));     \
    a02 = vis_fpmerge(vis_read_hi(s_0), vis_read_hi(s_2));     \
    b02 = vis_fpmerge(vis_read_lo(s_0), vis_read_lo(s_2));     \
                                                               \
    dd0 = vis_fpmerge(vis_read_hi(a02), vis_read_hi(a13));     \
    dd1 = vis_fpmerge(vis_read_lo(a02), vis_read_lo(a13));     \
    dd2 = vis_fpmerge(vis_read_hi(b02), vis_read_hi(b13));     \
    dd3 = vis_fpmerge(vis_read_lo(b02), vis_read_lo(b13));     \
}

/***************************************************************/

void ADD_SUFF(ThreeByteBgrToIntArgbConvert)(BLIT_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 *sp;
    mlib_d64 s_0;
    mlib_d64 s0, s1, s2, s3, sd0, sd1, sd2, dd0, dd1, dd2, dd3;
    mlib_s32 i, i0, j;

    if (width < 16) {
        for (j = 0; j < height; j++) {
            mlib_u8  *src = srcBase;
            mlib_s32 *dst = dstBase;

            for (i = 0; i < width; i++) {
                dst[i] = GBR_PIXEL(i);
            }

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
        return;
    }

    if (srcScan == 3*width && dstScan == 4*width) {
        width *= height;
        height = 1;
    }

    s_0 = vis_fone();

    for (j = 0; j < height; j++) {
        mlib_u8  *src = srcBase;
        mlib_f32 *dst = dstBase;

        i = i0 = 0;

        if ((mlib_s32)dst & 7) {
            ((mlib_s32*)dst)[i] = GBR_PIXEL(i);
            i0 = 1;
        }

        sp = vis_alignaddr(src, 3*i0);
        s3 = *sp++;

#pragma pipeloop(0)
        for (i = i0; i <= (mlib_s32)width - 8; i += 8) {
            s0 = s3;
            s1 = *sp++;
            s2 = *sp++;
            s3 = *sp++;
            sd0 = vis_faligndata(s0, s1);
            sd1 = vis_faligndata(s1, s2);
            sd2 = vis_faligndata(s2, s3);

            BGR_TO_ARGB

            *(mlib_d64*)(dst + i    ) = dd0;
            *(mlib_d64*)(dst + i + 2) = dd1;
            *(mlib_d64*)(dst + i + 4) = dd2;
            *(mlib_d64*)(dst + i + 6) = dd3;
        }

        for (; i < width; i++) {
            ((mlib_s32*)dst)[i] = GBR_PIXEL(i);
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(ThreeByteBgrToIntArgbScaleConvert)(SCALE_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd, maskFF;
    mlib_s32 i, i0, i1, j;

    if (width < 16) {
        for (j = 0; j < height; j++) {
            mlib_u8  *src = srcBase;
            mlib_s32 *dst = dstBase;
            mlib_s32 *dst_end = dst + width;
            mlib_s32 tmpsxloc = sxloc;

            PTR_ADD(src, (syloc >> shift) * srcScan);

            for (; dst < dst_end; dst++) {
                i = tmpsxloc >> shift;
                tmpsxloc += sxinc;
                *(mlib_s32*)dst = GBR_PIXEL(i);
            }

            PTR_ADD(dstBase, dstScan);
            syloc += syinc;
        }
        return;
    }

    maskFF = vis_fone();

    vis_alignaddr(NULL, 7);

    for (j = 0; j < height; j++) {
        mlib_u8  *src = srcBase;
        mlib_f32 *dst = dstBase;
        mlib_f32 *dst_end = dst + width;
        mlib_s32 tmpsxloc = sxloc;

        PTR_ADD(src, (syloc >> shift) * srcScan);

        if ((mlib_s32)dst & 7) {
            i = tmpsxloc >> shift;
            tmpsxloc += sxinc;
            *(mlib_s32*)dst = GBR_PIXEL(i);
            dst++;
        }

#pragma pipeloop(0)
        for (; dst <= dst_end - 2; dst += 2) {
            i0 = tmpsxloc >> shift;
            i1 = (tmpsxloc + sxinc) >> shift;
            tmpsxloc += 2*sxinc;

            dd = vis_faligndata(vis_ld_u8(src + 3*i1    ), dd);
            dd = vis_faligndata(vis_ld_u8(src + 3*i1 + 1), dd);
            dd = vis_faligndata(vis_ld_u8(src + 3*i1 + 2), dd);
            dd = vis_faligndata(maskFF, dd);
            dd = vis_faligndata(vis_ld_u8(src + 3*i0    ), dd);
            dd = vis_faligndata(vis_ld_u8(src + 3*i0 + 1), dd);
            dd = vis_faligndata(vis_ld_u8(src + 3*i0 + 2), dd);
            dd = vis_faligndata(maskFF, dd);

            *(mlib_d64*)dst = dd;
        }

        for (; dst < dst_end; dst++) {
            i = tmpsxloc >> shift;
            tmpsxloc += sxinc;
            *(mlib_s32*)dst = GBR_PIXEL(i);
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

#endif
