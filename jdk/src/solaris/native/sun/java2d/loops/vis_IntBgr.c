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

#define ARGB_to_GBGR(x)        \
    (x << 16) | (x & 0xff00) | ((x >> 16) & 0xff)

/***************************************************************/

#define ARGB_to_BGR(x)         \
    ((x << 16) & 0xff0000) | (x & 0xff00) | ((x >> 16) & 0xff)

/***************************************************************/

#define READ_Bgr(i)    \
    (src[3*i] << 16) | (src[3*i + 1] << 8) | src[3*i + 2]

/***************************************************************/

#define ARGB_to_GBGR_FL2(dst, src0, src1) {                    \
    mlib_d64 t0, t1, t2;                                       \
    t0 = vis_fpmerge(src0, src1);                              \
    t1 = vis_fpmerge(vis_read_lo(t0), vis_read_hi(t0));        \
    t2 = vis_fpmerge(vis_read_lo(t0), vis_read_lo(t0));        \
    dst = vis_fpmerge(vis_read_hi(t2), vis_read_lo(t1));       \
}

/***************************************************************/

#define ARGB_to_BGR_FL2(dst, src0, src1) {                     \
    mlib_d64 t0, t1, t2;                                       \
    t0 = vis_fpmerge(src0, src1);                              \
    t1 = vis_fpmerge(vis_read_lo(t0), vis_read_hi(t0));        \
    t2 = vis_fpmerge(vis_fzeros(),    vis_read_lo(t0));        \
    dst = vis_fpmerge(vis_read_hi(t2), vis_read_lo(t1));       \
}

/***************************************************************/

void ADD_SUFF(IntBgrToIntArgbConvert)(BLIT_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd, amask;
    mlib_s32 i, i0, j, x;

    if (dstScan == 4*width && srcScan == 4*width) {
        width *= height;
        height = 1;
    }

    amask = vis_to_double_dup(0xFF000000);
    vis_alignaddr(NULL, 7);

    for (j = 0; j < height; j++) {
        mlib_u32 *src = srcBase;
        mlib_u32 *dst = dstBase;

        i = i0 = 0;

        if ((mlib_s32)dst & 7) {
            x = src[i];
            dst[i] = 0xff000000 | ARGB_to_GBGR(x);
            i0 = 1;
        }

#pragma pipeloop(0)
        for (i = i0; i <= (mlib_s32)width - 2; i += 2) {
            ARGB2ABGR_FL2(dd, ((mlib_f32*)src)[i], ((mlib_f32*)src)[i + 1]);
            *(mlib_d64*)(dst + i) = vis_for(dd, amask);
        }

        if (i < width) {
            x = src[i];
            dst[i] = 0xff000000 | ARGB_to_GBGR(x);
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(IntBgrToIntArgbScaleConvert)(SCALE_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd, amask;
    mlib_s32 j, x;

    amask = vis_to_double_dup(0xFF000000);
    vis_alignaddr(NULL, 7);

    for (j = 0; j < height; j++) {
        mlib_u32 *src = srcBase;
        mlib_u32 *dst = dstBase;
        mlib_u32 *dst_end = dst + width;
        mlib_s32 tmpsxloc = sxloc;

        PTR_ADD(src, (syloc >> shift) * srcScan);

        if ((mlib_s32)dst & 7) {
            x = src[tmpsxloc >> shift];
            *dst++ = 0xff000000 | ARGB_to_GBGR(x);
            tmpsxloc += sxinc;
        }

#pragma pipeloop(0)
        for (; dst <= dst_end - 2; dst += 2) {
            ARGB2ABGR_FL2(dd, ((mlib_f32*)src)[tmpsxloc >> shift],
                              ((mlib_f32*)src)[(tmpsxloc + sxinc) >> shift]);
            *(mlib_d64*)dst = vis_for(dd, amask);
            tmpsxloc += 2*sxinc;
        }

        for (; dst < dst_end; dst++) {
            x = src[tmpsxloc >> shift];
            *dst++ = 0xff000000 | ARGB_to_GBGR(x);
            tmpsxloc += sxinc;
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbToIntBgrConvert)(BLIT_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd;
    mlib_s32 i, i0, j, x;

    if (dstScan == 4*width && srcScan == 4*width) {
        width *= height;
        height = 1;
    }

    for (j = 0; j < height; j++) {
        mlib_u32 *src = srcBase;
        mlib_u32 *dst = dstBase;

        i = i0 = 0;

        if ((mlib_s32)dst & 7) {
            x = src[i];
            dst[i] = ARGB_to_GBGR(x);
            i0 = 1;
        }

#pragma pipeloop(0)
        for (i = i0; i <= (mlib_s32)width - 2; i += 2) {
            ARGB_to_GBGR_FL2(dd, ((mlib_f32*)src)[i], ((mlib_f32*)src)[i + 1]);
            *(mlib_d64*)(dst + i) = dd;
        }

        if (i < width) {
            x = src[i];
            dst[i] = ARGB_to_GBGR(x);
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbToIntBgrScaleConvert)(SCALE_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd;
    mlib_s32 j, x;

    for (j = 0; j < height; j++) {
        mlib_u32 *src = srcBase;
        mlib_u32 *dst = dstBase;
        mlib_u32 *dst_end = dst + width;
        mlib_s32 tmpsxloc = sxloc;

        PTR_ADD(src, (syloc >> shift) * srcScan);

        if ((mlib_s32)dst & 7) {
            x = src[tmpsxloc >> shift];
            *dst++ = ARGB_to_GBGR(x);
            tmpsxloc += sxinc;
        }

#pragma pipeloop(0)
        for (; dst <= dst_end - 2; dst += 2) {
            ARGB_to_GBGR_FL2(dd, ((mlib_f32*)src)[tmpsxloc >> shift],
                                 ((mlib_f32*)src)[(tmpsxloc + sxinc) >> shift]);
            *(mlib_d64*)dst = dd;
            tmpsxloc += 2*sxinc;
        }

        for (; dst < dst_end; dst++) {
            x = src[tmpsxloc >> shift];
            *dst++ = ARGB_to_GBGR(x);
            tmpsxloc += sxinc;
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

#define INSERT_U8_34R {                                        \
    mlib_d64 sda, sdb, sdc, sdd;                               \
    mlib_d64 sde, sdf, sdg, sdh;                               \
    mlib_d64 sdi, sdj, sdk, sdl;                               \
    mlib_d64 sdm;                                              \
                                                               \
    sda = vis_fpmerge(vis_read_hi(sd0), vis_read_lo(sd1));     \
    sdb = vis_fpmerge(vis_read_lo(sd0), vis_read_hi(sd2));     \
    sdc = vis_fpmerge(vis_read_hi(sd1), vis_read_lo(sd2));     \
    sdd = vis_fpmerge(vis_read_hi(sda), vis_read_lo(sdb));     \
    sde = vis_fpmerge(vis_read_lo(sda), vis_read_hi(sdc));     \
    sdf = vis_fpmerge(vis_read_hi(sdb), vis_read_lo(sdc));     \
    sdg = vis_fpmerge(vis_read_hi(sdd), vis_read_lo(sde));     \
    sdh = vis_fpmerge(vis_read_lo(sdd), vis_read_hi(sdf));     \
    sdi = vis_fpmerge(vis_read_hi(sde), vis_read_lo(sdf));     \
    sdj = vis_fpmerge(vis_read_hi(sdg), vis_read_hi(sdi));     \
    sdk = vis_fpmerge(vis_read_lo(sdg), vis_read_lo(sdi));     \
    sdl = vis_fpmerge(vis_read_hi(sFF), vis_read_hi(sdh));     \
    sdm = vis_fpmerge(vis_read_lo(sFF), vis_read_lo(sdh));     \
    dd0 = vis_fpmerge(vis_read_hi(sdl), vis_read_hi(sdj));     \
    dd1 = vis_fpmerge(vis_read_lo(sdl), vis_read_lo(sdj));     \
    dd2 = vis_fpmerge(vis_read_hi(sdm), vis_read_hi(sdk));     \
    dd3 = vis_fpmerge(vis_read_lo(sdm), vis_read_lo(sdk));     \
}

/***************************************************************/

void ADD_SUFF(ThreeByteBgrToIntBgrConvert)(BLIT_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 *sp;
    mlib_d64 sFF;
    mlib_d64 s0, s1, s2, s3, sd0, sd1, sd2, dd0, dd1, dd2, dd3;
    mlib_s32 i, i0, j;

    if (width < 16) {
        for (j = 0; j < height; j++) {
            mlib_u8  *src = srcBase;
            mlib_u32 *dst = dstBase;

            for (i = 0; i < width; i++) {
                dst[i] = READ_Bgr(i);
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

    sFF = vis_fzero();

    for (j = 0; j < height; j++) {
        mlib_u8  *src = srcBase;
        mlib_f32 *dst = dstBase;

        i = i0 = 0;

        if ((mlib_s32)dst & 7) {
            ((mlib_s32*)dst)[i] = READ_Bgr(i);
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

            INSERT_U8_34R

            *(mlib_d64*)(dst + i    ) = dd0;
            *(mlib_d64*)(dst + i + 2) = dd1;
            *(mlib_d64*)(dst + i + 4) = dd2;
            *(mlib_d64*)(dst + i + 6) = dd3;
        }

        for (; i < width; i++) {
            ((mlib_s32*)dst)[i] = READ_Bgr(i);
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(ThreeByteBgrToIntBgrScaleConvert)(SCALE_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd, dzero;
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
                *(mlib_s32*)dst = READ_Bgr(i);
            }

            PTR_ADD(dstBase, dstScan);
            syloc += syinc;
        }
        return;
    }

    dzero = vis_fzero();

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
            *(mlib_s32*)dst = READ_Bgr(i);
            dst++;
        }

#pragma pipeloop(0)
        for (; dst <= dst_end - 2; dst += 2) {
            i0 = tmpsxloc >> shift;
            i1 = (tmpsxloc + sxinc) >> shift;
            tmpsxloc += 2*sxinc;

            dd = vis_faligndata(vis_ld_u8(src + 3*i1 + 2), dd);
            dd = vis_faligndata(vis_ld_u8(src + 3*i1 + 1), dd);
            dd = vis_faligndata(vis_ld_u8(src + 3*i1    ), dd);
            dd = vis_faligndata(dzero, dd);
            dd = vis_faligndata(vis_ld_u8(src + 3*i0 + 2), dd);
            dd = vis_faligndata(vis_ld_u8(src + 3*i0 + 1), dd);
            dd = vis_faligndata(vis_ld_u8(src + 3*i0    ), dd);
            dd = vis_faligndata(dzero, dd);

            *(mlib_d64*)dst = dd;
        }

        for (; dst < dst_end; dst++) {
            i = tmpsxloc >> shift;
            tmpsxloc += sxinc;
            *(mlib_s32*)dst = READ_Bgr(i);
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbBmToIntBgrXparOver)(BLIT_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd;
    mlib_s32 i, i0, j, mask, x;

    if (dstScan == 4*width && srcScan == 4*width) {
        width *= height;
        height = 1;
    }

    for (j = 0; j < height; j++) {
        mlib_s32 *src = srcBase;
        mlib_s32 *dst = dstBase;

        i = i0 = 0;

        if ((mlib_s32)dst & 7) {
            if (*(mlib_u8*)(src + i)) {
                x = src[i];
                dst[i] = ARGB_to_GBGR(x);
            }
            i0 = 1;
        }

#pragma pipeloop(0)
        for (i = i0; i <= (mlib_s32)width - 2; i += 2) {
            ARGB_to_GBGR_FL2(dd, ((mlib_f32*)src)[i], ((mlib_f32*)src)[i + 1]);
            mask = (((-*(mlib_u8*)(src + i)) >> 31) & 2) |
                   (((-*(mlib_u8*)(src + i + 1)) >> 31) & 1);
            vis_pst_32(dd, dst + i, mask);
        }

        if (i < width) {
            if (*(mlib_u8*)(src + i)) {
                x = src[i];
                dst[i] = ARGB_to_GBGR(x);
            }
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbBmToIntBgrScaleXparOver)(SCALE_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd;
    mlib_s32 j, mask;

    for (j = 0; j < height; j++) {
        mlib_s32 *src = srcBase;
        mlib_s32 *dst = dstBase;
        mlib_s32 *dst_end = dst + width;
        mlib_s32 tmpsxloc = sxloc;

        PTR_ADD(src, (syloc >> shift) * srcScan);

        if ((mlib_s32)dst & 7) {
            mlib_s32 *pp = src + (tmpsxloc >> shift);
            if (*(mlib_u8*)pp) {
                *dst = ARGB_to_GBGR(*pp);
            }
            dst++;
            tmpsxloc += sxinc;
        }

#pragma pipeloop(0)
        for (; dst <= dst_end - 2; dst += 2) {
            mlib_s32 *pp0 = src + (tmpsxloc >> shift);
            mlib_s32 *pp1 = src + ((tmpsxloc + sxinc) >> shift);
            ARGB_to_GBGR_FL2(dd, *(mlib_f32*)pp0, *(mlib_f32*)pp1);
            mask = (((-*(mlib_u8*)pp0) >> 31) & 2) |
                   ((mlib_u32)(-*(mlib_u8*)pp1) >> 31);
            vis_pst_32(dd, dst, mask);
            tmpsxloc += 2*sxinc;
        }

        for (; dst < dst_end; dst++) {
            mlib_s32 *pp = src + (tmpsxloc >> shift);
            if (*(mlib_u8*)pp) {
                *dst = ARGB_to_GBGR(*pp);
            }
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbBmToIntBgrXparBgCopy)(BCOPY_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd, d_bgpixel;
    mlib_s32 i, i0, j, mask;

    if (dstScan == 4*width && srcScan == 4*width) {
        width *= height;
        height = 1;
    }

    vis_alignaddr(NULL, 1);
    d_bgpixel = vis_to_double_dup(bgpixel);

    for (j = 0; j < height; j++) {
        mlib_s32 *src = srcBase;
        mlib_s32 *dst = dstBase;

        i = i0 = 0;

        if ((mlib_s32)dst & 7) {
            if (*(mlib_u8*)(src + i)) {
                dst[i] = ARGB_to_GBGR(src[i]);
            } else {
                dst[i] = bgpixel;
            }
            i0 = 1;
        }

#pragma pipeloop(0)
        for (i = i0; i <= (mlib_s32)width - 2; i += 2) {
            ARGB_to_GBGR_FL2(dd, ((mlib_f32*)src)[i], ((mlib_f32*)src)[i + 1]);
            mask = (((-*(mlib_u8*)(src + i)) >> 31) & 2) |
                   (((-*(mlib_u8*)(src + i + 1)) >> 31) & 1);
            *(mlib_d64*)(dst + i) = d_bgpixel;
            vis_pst_32(dd, dst + i, mask);
        }

        if (i < width) {
            if (*(mlib_u8*)(src + i)) {
                dst[i] = ARGB_to_GBGR(src[i]);
            } else {
                dst[i] = bgpixel;
            }
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(ByteIndexedToIntBgrConvert)(BLIT_PARAMS)
{
    jint *pixLut = pSrcInfo->lutBase;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd;
    mlib_s32 i, i0, j, x;

    if (srcScan == width && dstScan == 4*width) {
        width *= height;
        height = 1;
    }

    for (j = 0; j < height; j++) {
        mlib_u8  *src = srcBase;
        mlib_s32 *dst = dstBase;

        i = i0 = 0;

        if ((mlib_s32)dst & 7) {
            x = pixLut[src[i]];
            dst[i] = ARGB_to_GBGR(x);
            i0 = 1;
        }

#pragma pipeloop(0)
        for (i = i0; i <= (mlib_s32)width - 2; i += 2) {
            ARGB_to_GBGR_FL2(dd, ((mlib_f32*)pixLut)[src[i]],
                                 ((mlib_f32*)pixLut)[src[i + 1]]);
            *(mlib_d64*)(dst + i) = dd;
        }

        for (; i < width; i++) {
            x = pixLut[src[i]];
            dst[i] = ARGB_to_GBGR(x);
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(ByteIndexedToIntBgrScaleConvert)(SCALE_PARAMS)
{
    jint *pixLut = pSrcInfo->lutBase;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd;
    mlib_s32 j, x;

    for (j = 0; j < height; j++) {
        mlib_u8  *src = srcBase;
        mlib_s32 *dst = dstBase;
        mlib_s32 *dst_end = dst + width;
        mlib_s32 tmpsxloc = sxloc;

        PTR_ADD(src, (syloc >> shift) * srcScan);

        if ((mlib_s32)dst & 7) {
            x = pixLut[src[tmpsxloc >> shift]];
            *dst++ = ARGB_to_GBGR(x);
            tmpsxloc += sxinc;
        }

#pragma pipeloop(0)
        for (; dst <= dst_end - 2; dst += 2) {
            mlib_f32 f0 = ((mlib_f32*)pixLut)[src[tmpsxloc >> shift]];
            mlib_f32 f1 = ((mlib_f32*)pixLut)[src[(tmpsxloc + sxinc) >> shift]];
            ARGB_to_GBGR_FL2(dd, f0, f1);
            *(mlib_d64*)dst = dd;
            tmpsxloc += 2*sxinc;
        }

        for (; dst < dst_end; dst++) {
            x = pixLut[src[tmpsxloc >> shift]];
            *dst++ = ARGB_to_GBGR(x);
            tmpsxloc += sxinc;
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

void ADD_SUFF(ByteIndexedBmToIntBgrXparOver)(BLIT_PARAMS)
{
    jint *pixLut = pSrcInfo->lutBase;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd;
    mlib_s32 i, i0, j, x, mask;

    if (srcScan == width && dstScan == 4*width) {
        width *= height;
        height = 1;
    }

    for (j = 0; j < height; j++) {
        mlib_u8  *src = srcBase;
        mlib_s32 *dst = dstBase;

        i = i0 = 0;

        if ((mlib_s32)dst & 7) {
            x = pixLut[src[i]];
            if (x < 0) {
                dst[i] = ARGB_to_BGR(x);
            }
            i0 = 1;
        }

#pragma pipeloop(0)
        for (i = i0; i <= (mlib_s32)width - 2; i += 2) {
            mlib_f32 *pp0 = (mlib_f32*)pixLut + src[i];
            mlib_f32 *pp1 = (mlib_f32*)pixLut + src[i + 1];
            ARGB_to_BGR_FL2(dd, *pp0, *pp1);
            mask = (((*(mlib_u8*)pp0) >> 6) & 2) | ((*(mlib_u8*)pp1) >> 7);
            vis_pst_32(dd, dst + i, mask);
        }

        for (; i < width; i++) {
            x = pixLut[src[i]];
            if (x < 0) {
                dst[i] = ARGB_to_BGR(x);
            }
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(ByteIndexedBmToIntBgrScaleXparOver)(SCALE_PARAMS)
{
    jint *pixLut = pSrcInfo->lutBase;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd;
    mlib_s32 j, x, mask;

    for (j = 0; j < height; j++) {
        mlib_u8  *src = srcBase;
        mlib_s32 *dst = dstBase;
        mlib_s32 *dst_end = dst + width;
        mlib_s32 tmpsxloc = sxloc;

        PTR_ADD(src, (syloc >> shift) * srcScan);

        if ((mlib_s32)dst & 7) {
            x = pixLut[src[tmpsxloc >> shift]];
            tmpsxloc += sxinc;
            if (x < 0) {
                *dst = ARGB_to_BGR(x);
            }
            dst++;
        }

#pragma pipeloop(0)
        for (; dst <= dst_end - 2; dst += 2) {
            mlib_f32 *p0 = (mlib_f32*)pixLut + src[tmpsxloc >> shift];
            mlib_f32 *p1 = (mlib_f32*)pixLut + src[(tmpsxloc + sxinc) >> shift];
            ARGB_to_BGR_FL2(dd, *p0, *p1);
            mask = (((*(mlib_u8*)p0) >> 6) & 2) | ((*(mlib_u8*)p1) >> 7);
            tmpsxloc += 2*sxinc;
            vis_pst_32(dd, dst, mask);
        }

        for (; dst < dst_end; dst++) {
            x = pixLut[src[tmpsxloc >> shift]];
            tmpsxloc += sxinc;
            if (x < 0) {
                *dst = ARGB_to_BGR(x);
            }
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

void ADD_SUFF(ByteIndexedBmToIntBgrXparBgCopy)(BCOPY_PARAMS)
{
    jint *pixLut = pSrcInfo->lutBase;
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd, d_bgpixel;
    mlib_s32 j, x, mask;

    if (srcScan == width && dstScan == 4*width) {
        width *= height;
        height = 1;
    }

    d_bgpixel = vis_to_double_dup(bgpixel);

    for (j = 0; j < height; j++) {
        mlib_u8  *src = srcBase;
        mlib_s32 *dst = dstBase;
        mlib_s32 *dst_end;

        dst_end = dst + width;

        if ((mlib_s32)dst & 7) {
            x = pixLut[*src++];
            if (x < 0) {
                *dst = ARGB_to_GBGR(x);
            } else {
                *dst = bgpixel;
            }
            dst++;
        }

#pragma pipeloop(0)
        for (; dst <= (dst_end - 2); dst += 2) {
            mlib_f32 *pp0 = (mlib_f32*)pixLut + src[0];
            mlib_f32 *pp1 = (mlib_f32*)pixLut + src[1];
            ARGB_to_GBGR_FL2(dd, *pp0, *pp1);
            mask = (((*(mlib_u8*)pp0) >> 6) & 2) | ((*(mlib_u8*)pp1) >> 7);
            *(mlib_d64*)dst = d_bgpixel;
            vis_pst_32(dd, dst, mask);
            src += 2;
        }

        while (dst < dst_end) {
            x = pixLut[*src++];
            if (x < 0) {
                *dst = ARGB_to_GBGR(x);
            } else {
                *dst = bgpixel;
            }
            dst++;
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(IntBgrDrawGlyphListAA)(GLYPH_LIST_PARAMS)
{
    mlib_s32 glyphCounter;
    mlib_s32 scan = pRasInfo->scanStride;
    mlib_u8  *dstBase;
    mlib_s32 j;
    mlib_d64 dmix0, dmix1, dd, d0, d1, e0, e1, fgpixel_d;
    mlib_d64 done, done16, d_half, maskRGB, dzero;
    mlib_s32 pix, mask, mask_z;
    mlib_f32 srcG_f;

    done = vis_to_double_dup(0x7fff7fff);
    done16 = vis_to_double_dup(0x7fff);
    d_half = vis_to_double_dup((1 << (16 + 6)) | (1 << 6));

    fgpixel_d = vis_to_double_dup(fgpixel);
    srcG_f = vis_to_float(argbcolor);
    maskRGB = vis_to_double_dup(0xffffff);
    dzero = vis_fzero();

    ARGB2ABGR_FL(srcG_f)

    vis_write_gsr(0 << 3);

    for (glyphCounter = 0; glyphCounter < totalGlyphs; glyphCounter++) {
        const jubyte *pixels;
        unsigned int rowBytes;
        int left, top;
        int width, height;
        int right, bottom;

        pixels = (const jubyte *) glyphs[glyphCounter].pixels;

        if (!pixels) continue;

        left = glyphs[glyphCounter].x;
        top = glyphs[glyphCounter].y;
        width = glyphs[glyphCounter].width;
        height = glyphs[glyphCounter].height;
        rowBytes = width;
        right = left + width;
        bottom = top + height;
        if (left < clipLeft) {
            pixels += clipLeft - left;
            left = clipLeft;
        }
        if (top < clipTop) {
            pixels += (clipTop - top) * rowBytes;
            top = clipTop;
        }
        if (right > clipRight) {
            right = clipRight;
        }
        if (bottom > clipBottom) {
            bottom = clipBottom;
        }
        if (right <= left || bottom <= top) {
            continue;
        }
        width = right - left;
        height = bottom - top;

        dstBase = pRasInfo->rasBase;
        PTR_ADD(dstBase, top*scan + 4*left);

        for (j = 0; j < height; j++) {
            mlib_u8  *src = (void*)pixels;
            mlib_s32 *dst, *dst_end;

            dst = (void*)dstBase;
            dst_end = dst + width;

            if ((mlib_s32)dst & 7) {
                pix = *src++;
                if (pix) {
                    dd = vis_fpadd16(MUL8_VIS(srcG_f, pix), d_half);
                    dd = vis_fpadd16(MUL8_VIS(*(mlib_f32*)dst, 255 - pix), dd);
                    *(mlib_f32*)dst = vis_fands(vis_fpack16(dd),
                                                vis_read_hi(maskRGB));
                    if (pix == 255) *(mlib_f32*)dst = vis_read_hi(fgpixel_d);
                }
                dst++;
            }

#pragma pipeloop(0)
            for (; dst <= (dst_end - 2); dst += 2) {
                dmix0 = vis_freg_pair(((mlib_f32 *)vis_mul8s_tbl)[src[0]],
                                      ((mlib_f32 *)vis_mul8s_tbl)[src[1]]);
                mask = vis_fcmplt32(dmix0, done16);
                mask_z = vis_fcmpne32(dmix0, dzero);
                dmix1 = vis_fpsub16(done, dmix0);
                src += 2;

                dd = *(mlib_d64*)dst;
                d0 = vis_fmul8x16al(srcG_f, vis_read_hi(dmix0));
                d1 = vis_fmul8x16al(srcG_f, vis_read_lo(dmix0));
                e0 = vis_fmul8x16al(vis_read_hi(dd), vis_read_hi(dmix1));
                e1 = vis_fmul8x16al(vis_read_lo(dd), vis_read_lo(dmix1));
                d0 = vis_fpadd16(vis_fpadd16(d0, d_half), e0);
                d1 = vis_fpadd16(vis_fpadd16(d1, d_half), e1);
                dd = vis_fpack16_pair(d0, d1);
                dd = vis_fand(dd, maskRGB);

                vis_pst_32(fgpixel_d, dst, mask_z);
                vis_pst_32(dd, dst, mask & mask_z);
            }

            while (dst < dst_end) {
                pix = *src++;
                if (pix) {
                    dd = vis_fpadd16(MUL8_VIS(srcG_f, pix), d_half);
                    dd = vis_fpadd16(MUL8_VIS(*(mlib_f32*)dst, 255 - pix), dd);
                    *(mlib_f32*)dst = vis_fands(vis_fpack16(dd),
                                                vis_read_hi(maskRGB));
                    if (pix == 255) *(mlib_f32*)dst = vis_read_hi(fgpixel_d);
                }
                dst++;
            }

            PTR_ADD(dstBase, scan);
            pixels += rowBytes;
        }
    }
}

/***************************************************************/

#endif /* JAVA2D_NO_MLIB */
