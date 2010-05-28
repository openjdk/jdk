/*
 * Copyright (c) 2003, 2008, Oracle and/or its affiliates. All rights reserved.
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

#define GET_ARGBPRE(i)         \
    0xFF000000 | (src[3*i + 2] << 16) | (src[3*i + 1] << 8) | src[3*i]

/***************************************************************/

#define CONVERT_PRE(rr, dstA, dstARGB)         \
    rr = vis_fmul8x16(dstARGB, ((mlib_d64*)vis_div8pre_tbl)[dstA])

/***************************************************************/

void ADD_SUFF(IntArgbPreToIntArgbConvert)(BLIT_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 dstA0, dstA1;
    mlib_d64 res0, res1, dstARGB;
    mlib_f32 dstARGB0;
    mlib_s32 i, i0, j;

    vis_write_gsr(7 << 3);

    if (dstScan == 4*width && srcScan == 4*width) {
        width *= height;
        height = 1;
    }

    for (j = 0; j < height; j++) {
        mlib_f32 *src = srcBase;
        mlib_f32 *dst = dstBase;

        i = i0 = 0;

        if ((mlib_s32)dst & 7) {
            dstA0 = *(mlib_u8*)(src + i);
            dstARGB0 = src[i];
            CONVERT_PRE(res0, dstA0, dstARGB0);
            dst[i] = vis_fpack16(res0);

            i0 = 1;
        }

#pragma pipeloop(0)
        for (i = i0; i <= (mlib_s32)width - 2; i += 2) {
            dstA0 = *(mlib_u8*)(src + i);
            dstA1 = *(mlib_u8*)(src + i + 1);
            dstARGB = vis_freg_pair(src[i], src[i + 1]);

            CONVERT_PRE(res0, dstA0, vis_read_hi(dstARGB));
            CONVERT_PRE(res1, dstA1, vis_read_lo(dstARGB));

            res0 = vis_fpack16_pair(res0, res1);

            *(mlib_d64*)(dst + i) = res0;
        }

        if (i < width) {
            dstA0 = *(mlib_u8*)(src + i);
            dstARGB0 = src[i];
            CONVERT_PRE(res0, dstA0, dstARGB0);
            dst[i] = vis_fpack16(res0);
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbPreToIntArgbScaleConvert)(SCALE_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 dstA0, dstA1;
    mlib_d64 res0, res1, dstARGB;
    mlib_f32 dstARGB0;
    mlib_s32 i, i0, j, ind0, ind1;

    if (width < 16) {
        for (j = 0; j < height; j++) {
            mlib_s32 *src = srcBase;
            mlib_u8  *dst = dstBase;
            mlib_s32 tmpsxloc = sxloc;

            PTR_ADD(src, (syloc >> shift) * srcScan);

            for (i = 0; i < width; i++) {
                mlib_u32 argb = src[tmpsxloc >> shift];
                mlib_u32 a, r, g, b;
                b = argb & 0xff;
                g = (argb >> 8) & 0xff;
                r = (argb >> 16) & 0xff;
                a = argb >> 24;
                dst[4*i] = a;
                if (a == 0) a = 255; /* a |= (a - 1) >> 24; */
                dst[4*i + 1] = div8table[a][r];
                dst[4*i + 2] = div8table[a][g];
                dst[4*i + 3] = div8table[a][b];
                tmpsxloc += sxinc;
            }

            PTR_ADD(dstBase, dstScan);
            syloc += syinc;
        }
        return;
    }

    vis_write_gsr(7 << 3);

    for (j = 0; j < height; j++) {
        mlib_f32 *src = srcBase;
        mlib_f32 *dst = dstBase;
        mlib_s32 tmpsxloc = sxloc;

        PTR_ADD(src, (syloc >> shift) * srcScan);

        i = i0 = 0;

        if ((mlib_s32)dst & 7) {
            ind0 = tmpsxloc >> shift;
            tmpsxloc += sxinc;
            dstA0 = *(mlib_u8*)(src + ind0);
            dstARGB0 = src[ind0];
            CONVERT_PRE(res0, dstA0, dstARGB0);
            dst[i] = vis_fpack16(res0);

            i0 = 1;
        }

#pragma pipeloop(0)
        for (i = i0; i <= (mlib_s32)width - 2; i += 2) {
            ind0 = tmpsxloc >> shift;
            tmpsxloc += sxinc;
            ind1 = tmpsxloc >> shift;
            tmpsxloc += sxinc;
            dstA0 = *(mlib_u8*)(src + ind0);
            dstA1 = *(mlib_u8*)(src + ind1);

            dstARGB = vis_freg_pair(src[ind0], src[ind1]);

            CONVERT_PRE(res0, dstA0, vis_read_hi(dstARGB));
            CONVERT_PRE(res1, dstA1, vis_read_lo(dstARGB));

            res0 = vis_fpack16_pair(res0, res1);

            *(mlib_d64*)(dst + i) = res0;
        }

        if (i < width) {
            ind0 = tmpsxloc >> shift;
            tmpsxloc += sxinc;
            dstA0 = *(mlib_u8*)(src + ind0);
            dstARGB0 = src[ind0];
            CONVERT_PRE(res0, dstA0, dstARGB0);
            dst[i] = vis_fpack16(res0);
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

#undef  CONVERT_PRE
#define CONVERT_PRE(rr, dstA, dstARGB)         \
    rr = MUL8_VIS(dstARGB, dstA)

void ADD_SUFF(IntArgbToIntArgbPreConvert)(BLIT_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 dstA0, dstA1;
    mlib_d64 res0, res1, dstARGB;
    mlib_f32 dstARGB0;
    mlib_s32 i, i0, j;

    vis_write_gsr(0 << 3);

    if (dstScan == 4*width && srcScan == 4*width) {
        width *= height;
        height = 1;
    }

    for (j = 0; j < height; j++) {
        mlib_f32 *src = srcBase;
        mlib_f32 *dst = dstBase;

        i = i0 = 0;

        if ((mlib_s32)dst & 7) {
            dstA0 = *(mlib_u8*)(src + i);
            dstARGB0 = src[i];
            CONVERT_PRE(res0, dstA0, dstARGB0);
            dst[i] = vis_fpack16(res0);
            *(mlib_u8*)(dst + i) = dstA0;

            i0 = 1;
        }

#pragma pipeloop(0)
        for (i = i0; i <= (mlib_s32)width - 2; i += 2) {
            dstA0 = *(mlib_u8*)(src + i);
            dstA1 = *(mlib_u8*)(src + i + 1);
            dstARGB = vis_freg_pair(src[i], src[i + 1]);

            CONVERT_PRE(res0, dstA0, vis_read_hi(dstARGB));
            CONVERT_PRE(res1, dstA1, vis_read_lo(dstARGB));

            res0 = vis_fpack16_pair(res0, res1);

            *(mlib_d64*)(dst + i) = res0;
            vis_pst_8(dstARGB, dst + i, 0x88);
        }

        if (i < width) {
            dstA0 = *(mlib_u8*)(src + i);
            dstARGB0 = src[i];
            CONVERT_PRE(res0, dstA0, dstARGB0);
            dst[i] = vis_fpack16(res0);
            *(mlib_u8*)(dst + i) = dstA0;
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbToIntArgbPreScaleConvert)(SCALE_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 dstA0, dstA1;
    mlib_d64 res0, res1, dstARGB;
    mlib_f32 dstARGB0;
    mlib_s32 i, i0, j, ind0, ind1;

    if (width < 16) {
        for (j = 0; j < height; j++) {
            mlib_s32 *src = srcBase;
            mlib_u8  *dst = dstBase;
            mlib_s32 tmpsxloc = sxloc;

            PTR_ADD(src, (syloc >> shift) * srcScan);

            for (i = 0; i < width; i++) {
                mlib_u32 argb = src[tmpsxloc >> shift];
                mlib_u32 a, r, g, b;
                b = argb & 0xff;
                g = (argb >> 8) & 0xff;
                r = (argb >> 16) & 0xff;
                a = argb >> 24;
                dst[4*i] = a;
                dst[4*i + 1] = mul8table[a][r];
                dst[4*i + 2] = mul8table[a][g];
                dst[4*i + 3] = mul8table[a][b];
                tmpsxloc += sxinc;
            }

            PTR_ADD(dstBase, dstScan);
            syloc += syinc;
        }
        return;
    }

    vis_write_gsr(0 << 3);

    for (j = 0; j < height; j++) {
        mlib_f32 *src = srcBase;
        mlib_f32 *dst = dstBase;
        mlib_s32 tmpsxloc = sxloc;

        PTR_ADD(src, (syloc >> shift) * srcScan);

        i = i0 = 0;

        if ((mlib_s32)dst & 7) {
            ind0 = tmpsxloc >> shift;
            tmpsxloc += sxinc;
            dstA0 = *(mlib_u8*)(src + ind0);
            dstARGB0 = src[ind0];
            CONVERT_PRE(res0, dstA0, dstARGB0);
            dst[i] = vis_fpack16(res0);
            *(mlib_u8*)(dst + i) = dstA0;

            i0 = 1;
        }

#pragma pipeloop(0)
        for (i = i0; i <= (mlib_s32)width - 2; i += 2) {
            ind0 = tmpsxloc >> shift;
            tmpsxloc += sxinc;
            ind1 = tmpsxloc >> shift;
            tmpsxloc += sxinc;
            dstA0 = *(mlib_u8*)(src + ind0);
            dstA1 = *(mlib_u8*)(src + ind1);

            dstARGB = vis_freg_pair(src[ind0], src[ind1]);

            CONVERT_PRE(res0, dstA0, vis_read_hi(dstARGB));
            CONVERT_PRE(res1, dstA1, vis_read_lo(dstARGB));

            res0 = vis_fpack16_pair(res0, res1);

            *(mlib_d64*)(dst + i) = res0;
            vis_pst_8(dstARGB, dst + i, 0x88);
        }

        if (i < width) {
            ind0 = tmpsxloc >> shift;
            tmpsxloc += sxinc;
            dstA0 = *(mlib_u8*)(src + ind0);
            dstARGB0 = src[ind0];
            CONVERT_PRE(res0, dstA0, dstARGB0);
            dst[i] = vis_fpack16(res0);
            *(mlib_u8*)(dst + i) = dstA0;
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

void ADD_SUFF(IntArgbToIntArgbPreXorBlit)(BLIT_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 xorpixel = pCompInfo->details.xorPixel;
    mlib_s32 alphamask = pCompInfo->alphaMask;
    mlib_s32 dstA0, dstA1;
    mlib_d64 res0, res1, dstARGB, dd, d_xorpixel, d_alphamask, maskRGB;
    mlib_d64 d_round;
    mlib_f32 dstARGB0, ff;
    mlib_s32 i, i0, j;

    vis_write_gsr(0 << 3);

    if (dstScan == 4*width && srcScan == 4*width) {
        width *= height;
        height = 1;
    }

    d_xorpixel = vis_to_double_dup(xorpixel);
    d_alphamask = vis_to_double_dup(alphamask);
    maskRGB = vis_to_double_dup(0xFFFFFF);
    d_round = vis_to_double_dup(((1 << 16) | 1) << 6);

    xorpixel >>= 24;
    alphamask >>= 24;

    for (j = 0; j < height; j++) {
        mlib_f32 *src = srcBase;
        mlib_f32 *dst = dstBase;

        i = i0 = 0;

        if ((mlib_s32)dst & 7) {
            dstA0 = *(mlib_u8*)(src + i);
            dstARGB0 = src[i];
            if (dstA0 & 0x80) {
                CONVERT_PRE(res0, dstA0, dstARGB0);
                res0 = vis_fpadd16(res0, d_round);
                ff = vis_fpack16(res0);
                ff = vis_fxors(ff, vis_read_hi(d_xorpixel));
                ff = vis_fandnots(vis_read_hi(d_alphamask), ff);
                ff = vis_fxors(ff, dst[i]);
                dstA0 = *(mlib_u8*)(dst + i) ^
                        ((dstA0 ^ xorpixel) &~ alphamask);
                dst[i] = ff;
                *(mlib_u8*)(dst + i) = dstA0;
            }

            i0 = 1;
        }

#pragma pipeloop(0)
        for (i = i0; i <= (mlib_s32)width - 2; i += 2) {
            dstA0 = *(mlib_u8*)(src + i);
            dstA1 = *(mlib_u8*)(src + i + 1);
            dstARGB = vis_freg_pair(src[i], src[i + 1]);

            CONVERT_PRE(res0, dstA0, vis_read_hi(dstARGB));
            CONVERT_PRE(res1, dstA1, vis_read_lo(dstARGB));
            res0 = vis_fpadd16(res0, d_round);
            res1 = vis_fpadd16(res1, d_round);
            dd = vis_fpack16_pair(res0, res1);

            dd = vis_for(vis_fand(maskRGB, dd), vis_fandnot(maskRGB, dstARGB));

            dd = vis_fxor(dd, d_xorpixel);
            dd = vis_fandnot(d_alphamask, dd);
            dd = vis_fxor(dd, *(mlib_d64*)(dst + i));

            vis_pst_32(dd, dst + i, ((dstA0 >> 6) & 2) | (dstA1 >> 7));
        }

        if (i < width) {
            dstA0 = *(mlib_u8*)(src + i);
            dstARGB0 = src[i];
            if (dstA0 & 0x80) {
                CONVERT_PRE(res0, dstA0, dstARGB0);
                res0 = vis_fpadd16(res0, d_round);
                ff = vis_fpack16(res0);
                ff = vis_fxors(ff, vis_read_hi(d_xorpixel));
                ff = vis_fandnots(vis_read_hi(d_alphamask), ff);
                ff = vis_fxors(ff, dst[i]);
                dstA0 = *(mlib_u8*)(dst + i) ^
                        ((dstA0 ^ xorpixel) &~ alphamask);
                dst[i] = ff;
                *(mlib_u8*)(dst + i) = dstA0;
            }
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(IntRgbToIntArgbPreConvert)(BLIT_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd, mask;
    mlib_s32 i, i0, j;

    if (dstScan == 4*width && srcScan == 4*width) {
        width *= height;
        height = 1;
    }

    mask = vis_to_double_dup(0xFF000000);

    for (j = 0; j < height; j++) {
        mlib_f32 *src = srcBase;
        mlib_f32 *dst = dstBase;

        i = i0 = 0;

        if ((mlib_s32)dst & 7) {
            dst[i] = vis_fors(src[i], vis_read_hi(mask));
            i0 = 1;
        }

#pragma pipeloop(0)
        for (i = i0; i <= (mlib_s32)width - 2; i += 2) {
            dd = vis_freg_pair(src[i], src[i + 1]);

            *(mlib_d64*)(dst + i) = vis_for(dd, mask);
        }

        if (i < width) {
            dst[i] = vis_fors(src[i], vis_read_hi(mask));
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(IntRgbToIntArgbPreScaleConvert)(SCALE_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd, mask;
    mlib_s32 j;

    mask = vis_to_double_dup(0xFF000000);

    for (j = 0; j < height; j++) {
        mlib_f32 *src = srcBase;
        mlib_f32 *dst = dstBase;
        mlib_f32 *dst_end = dst + width;
        mlib_s32 tmpsxloc = sxloc;

        PTR_ADD(src, (syloc >> shift) * srcScan);

        if ((mlib_s32)dst & 7) {
            *dst++ = vis_fors(src[tmpsxloc >> shift], vis_read_hi(mask));
            tmpsxloc += sxinc;
        }

#pragma pipeloop(0)
        for (; dst <= dst_end - 2; dst += 2) {
            dd = vis_freg_pair(src[tmpsxloc >> shift],
                               src[(tmpsxloc + sxinc) >> shift]);
            *(mlib_d64*)dst = vis_for(dd, mask);
            tmpsxloc += 2*sxinc;
        }

        if (dst < dst_end) {
            *dst = vis_fors(src[tmpsxloc >> shift], vis_read_hi(mask));
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

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

void ADD_SUFF(ThreeByteBgrToIntArgbPreConvert)(BLIT_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 *sp;
    mlib_d64 s_0;
    mlib_d64 s0, s1, s2, s3, sd0, sd1, sd2, dd0, dd1, dd2, dd3;
    mlib_s32 i, i0, j;

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
            ((mlib_s32*)dst)[i] = GET_ARGBPRE(i);
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
            ((mlib_s32*)dst)[i] = GET_ARGBPRE(i);
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(ThreeByteBgrToIntArgbPreScaleConvert)(SCALE_PARAMS)
{
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd, maskFF;
    mlib_s32 i, i0, i1, j;

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
            *(mlib_s32*)dst = GET_ARGBPRE(i);
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
            *(mlib_s32*)dst = GET_ARGBPRE(i);
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

void ADD_SUFF(ByteIndexedToIntArgbPreConvert)(BLIT_PARAMS)
{
    jint *pixLut = pSrcInfo->lutBase;
    mlib_s32 buff[256];
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 i, i0, j;

    if (width < 16) {
        for (j = 0; j < height; j++) {
            mlib_u8  *src = srcBase;
            mlib_s32 *dst = dstBase;

            for (i = 0; i < width; i++) {
                mlib_s32 a, r, g, b;
                mlib_u32 x = pixLut[src[i]];
                b = x & 0xff;
                g = (x >> 8) & 0xff;
                r = (x >> 16) & 0xff;
                a = x >> 24;
                r = mul8table[a][r];
                g = mul8table[a][g];
                b = mul8table[a][b];
                dst[i] = (a << 24) | (r << 16) | (g << 8) | b;
            }

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
        return;
    }

    if (srcScan == width && dstScan == 4*width) {
        width *= height;
        height = 1;
    }

    ADD_SUFF(IntArgbToIntArgbPreConvert)(pixLut, buff, 256, 1,
                                         pSrcInfo, pDstInfo, pPrim, pCompInfo);

    for (j = 0; j < height; j++) {
        mlib_u8  *src = srcBase;
        mlib_s32 *dst = dstBase;

        i = i0 = 0;

        if ((mlib_s32)dst & 7) {
            dst[i] = buff[src[i]];
            i0 = 1;
        }

#pragma pipeloop(0)
        for (i = i0; i <= (mlib_s32)width - 2; i += 2) {
            *(mlib_d64*)(dst + i) = LOAD_2F32(buff, src[i], src[i + 1]);
        }

        for (; i < width; i++) {
            dst[i] = buff[src[i]];
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(ByteIndexedToIntArgbPreScaleConvert)(SCALE_PARAMS)
{
    jint *pixLut = pSrcInfo->lutBase;
    mlib_s32 buff[256];
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_s32 i, j;

    if (width < 16) {
        for (j = 0; j < height; j++) {
            mlib_u8  *src = srcBase;
            mlib_s32 *dst = dstBase;
            mlib_s32 tmpsxloc = sxloc;

            PTR_ADD(src, (syloc >> shift) * srcScan);

            for (i = 0; i < width; i++) {
                mlib_s32 a, r, g, b;
                mlib_u32 x = pixLut[src[tmpsxloc >> shift]];
                tmpsxloc += sxinc;
                b = x & 0xff;
                g = (x >> 8) & 0xff;
                r = (x >> 16) & 0xff;
                a = x >> 24;
                r = mul8table[a][r];
                g = mul8table[a][g];
                b = mul8table[a][b];
                dst[i] = (a << 24) | (r << 16) | (g << 8) | b;
            }

            PTR_ADD(dstBase, dstScan);
            syloc += syinc;
        }
        return;
    }

    ADD_SUFF(IntArgbToIntArgbPreConvert)(pixLut, buff, 256, 1,
                                         pSrcInfo, pDstInfo, pPrim, pCompInfo);

    for (j = 0; j < height; j++) {
        mlib_u8  *src = srcBase;
        mlib_s32 *dst = dstBase;
        mlib_s32 *dst_end = dst + width;
        mlib_s32 tmpsxloc = sxloc;

        PTR_ADD(src, (syloc >> shift) * srcScan);

        if ((mlib_s32)dst & 7) {
            *dst++ = buff[src[tmpsxloc >> shift]];
            tmpsxloc += sxinc;
        }

#pragma pipeloop(0)
        for (; dst <= dst_end - 2; dst += 2) {
            *(mlib_d64*)dst = LOAD_2F32(buff, src[tmpsxloc >> shift],
                                              src[(tmpsxloc + sxinc) >> shift]);
            tmpsxloc += 2*sxinc;
        }

        for (; dst < dst_end; dst++) {
            *dst = buff[src[tmpsxloc >> shift]];
            tmpsxloc += sxinc;
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

void ADD_SUFF(ByteIndexedBmToIntArgbPreXparOver)(BLIT_PARAMS)
{
    jint *pixLut = pSrcInfo->lutBase;
    mlib_s32 buff[256];
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd, dzero;
    mlib_s32 i, i0, j, x, mask;

    if (width < 16) {
        for (j = 0; j < height; j++) {
            mlib_u8  *src = srcBase;
            mlib_s32 *dst = dstBase;

            for (i = 0; i < width; i++) {
                mlib_s32 a, r, g, b;
                mlib_s32 x = pixLut[src[i]];
                if (x < 0) {
                    b = x & 0xff;
                    g = (x >> 8) & 0xff;
                    r = (x >> 16) & 0xff;
                    a = (mlib_u32)x >> 24;
                    r = mul8table[a][r];
                    g = mul8table[a][g];
                    b = mul8table[a][b];
                    dst[i] = (a << 24) | (r << 16) | (g << 8) | b;
                }
            }

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
        return;
    }

    if (srcScan == width && dstScan == 4*width) {
        width *= height;
        height = 1;
    }

    ADD_SUFF(IntArgbToIntArgbPreConvert)(pixLut, buff, 256, 1,
                                         pSrcInfo, pDstInfo, pPrim, pCompInfo);

    dzero = vis_fzero();

    for (j = 0; j < height; j++) {
        mlib_u8  *src = srcBase;
        mlib_s32 *dst = dstBase;

        i = i0 = 0;

        if ((mlib_s32)dst & 7) {
            x = buff[src[i]];
            if (x < 0) {
                dst[i] = x;
            }
            i0 = 1;
        }

#pragma pipeloop(0)
        for (i = i0; i <= (mlib_s32)width - 2; i += 2) {
            dd = vis_freg_pair(((mlib_f32*)buff)[src[i]],
                               ((mlib_f32*)buff)[src[i + 1]]);
            mask = vis_fcmplt32(dd, dzero);
            vis_pst_32(dd, dst + i, mask);
        }

        for (; i < width; i++) {
            x = buff[src[i]];
            if (x < 0) {
                dst[i] = x;
            }
        }

        PTR_ADD(dstBase, dstScan);
        PTR_ADD(srcBase, srcScan);
    }
}

/***************************************************************/

void ADD_SUFF(ByteIndexedBmToIntArgbPreScaleXparOver)(SCALE_PARAMS)
{
    jint *pixLut = pSrcInfo->lutBase;
    mlib_s32 buff[256];
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd, dzero;
    mlib_s32 i, j, x, mask;

    if (width < 16) {
        for (j = 0; j < height; j++) {
            mlib_u8  *src = srcBase;
            mlib_s32 *dst = dstBase;
            mlib_s32 tmpsxloc = sxloc;

            PTR_ADD(src, (syloc >> shift) * srcScan);

            for (i = 0; i < width; i++) {
                mlib_s32 a, r, g, b;
                mlib_s32 x = pixLut[src[tmpsxloc >> shift]];
                tmpsxloc += sxinc;
                if (x < 0) {
                    b = x & 0xff;
                    g = (x >> 8) & 0xff;
                    r = (x >> 16) & 0xff;
                    a = (mlib_u32)x >> 24;
                    r = mul8table[a][r];
                    g = mul8table[a][g];
                    b = mul8table[a][b];
                    dst[i] = (a << 24) | (r << 16) | (g << 8) | b;
                }
            }

            PTR_ADD(dstBase, dstScan);
            syloc += syinc;
        }
        return;
    }

    ADD_SUFF(IntArgbToIntArgbPreConvert)(pixLut, buff, 256, 1,
                                         pSrcInfo, pDstInfo, pPrim, pCompInfo);

    dzero = vis_fzero();

    for (j = 0; j < height; j++) {
        mlib_u8  *src = srcBase;
        mlib_s32 *dst = dstBase;
        mlib_s32 *dst_end = dst + width;
        mlib_s32 tmpsxloc = sxloc;

        PTR_ADD(src, (syloc >> shift) * srcScan);

        if ((mlib_s32)dst & 7) {
            x = buff[src[tmpsxloc >> shift]];
            tmpsxloc += sxinc;
            if (x < 0) {
                *dst = x;
            }
            dst++;
        }

#pragma pipeloop(0)
        for (; dst <= dst_end - 2; dst += 2) {
            dd = LOAD_2F32(buff, src[tmpsxloc >> shift],
                                 src[(tmpsxloc + sxinc) >> shift]);
            tmpsxloc += 2*sxinc;
            mask = vis_fcmplt32(dd, dzero);
            vis_pst_32(dd, dst, mask);
        }

        for (; dst < dst_end; dst++) {
            x = buff[src[tmpsxloc >> shift]];
            tmpsxloc += sxinc;
            if (x < 0) {
                *dst = x;
            }
        }

        PTR_ADD(dstBase, dstScan);
        syloc += syinc;
    }
}

/***************************************************************/

void ADD_SUFF(ByteIndexedBmToIntArgbPreXparBgCopy)(BCOPY_PARAMS)
{
    jint *pixLut = pSrcInfo->lutBase;
    mlib_s32 buff[256];
    mlib_s32 dstScan = pDstInfo->scanStride;
    mlib_s32 srcScan = pSrcInfo->scanStride;
    mlib_d64 dd, dzero, d_bgpixel;
    mlib_s32 i, j, x, mask;

    if (width < 16) {
        for (j = 0; j < height; j++) {
            mlib_u8  *src = srcBase;
            mlib_s32 *dst = dstBase;

            for (i = 0; i < width; i++) {
                x = pixLut[src[i]];
                if (x < 0) {
                    mlib_s32 a, r, g, b;
                    b = x & 0xff;
                    g = (x >> 8) & 0xff;
                    r = (x >> 16) & 0xff;
                    a = (mlib_u32)x >> 24;
                    r = mul8table[a][r];
                    g = mul8table[a][g];
                    b = mul8table[a][b];
                    dst[i] = (a << 24) | (r << 16) | (g << 8) | b;
                } else {
                    dst[i] = bgpixel;
                }
            }

            PTR_ADD(dstBase, dstScan);
            PTR_ADD(srcBase, srcScan);
        }
        return;
    }

    ADD_SUFF(IntArgbToIntArgbPreConvert)(pixLut, buff, 256, 1,
                                         pSrcInfo, pDstInfo, pPrim, pCompInfo);

    if (srcScan == width && dstScan == 4*width) {
        width *= height;
        height = 1;
    }

    dzero = vis_fzero();
    d_bgpixel = vis_to_double_dup(bgpixel);

    for (j = 0; j < height; j++) {
        mlib_u8  *src = srcBase;
        mlib_s32 *dst = dstBase;
        mlib_s32 *dst_end;

        dst_end = dst + width;

        if ((mlib_s32)dst & 7) {
            x = buff[*src++];
            if (x < 0) {
                *dst = x;
            } else {
                *dst = bgpixel;
            }
            dst++;
        }

#pragma pipeloop(0)
        for (; dst <= (dst_end - 2); dst += 2) {
            dd = vis_freg_pair(((mlib_f32*)buff)[src[0]],
                               ((mlib_f32*)buff)[src[1]]);
            mask = vis_fcmplt32(dd, dzero);
            *(mlib_d64*)dst = d_bgpixel;
            vis_pst_32(dd, dst, mask);
            src += 2;
        }

        while (dst < dst_end) {
            x = buff[*src++];
            if (x < 0) {
                *dst = x;
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

void ADD_SUFF(IntArgbPreDrawGlyphListAA)(SurfaceDataRasInfo * pRasInfo,
                                         ImageRef *glyphs,
                                         jint totalGlyphs,
                                         jint fgpixel, jint argbcolor,
                                         jint clipLeft, jint clipTop,
                                         jint clipRight, jint clipBottom,
                                         NativePrimitive * pPrim,
                                         CompositeInfo * pCompInfo)
{
    mlib_s32 glyphCounter;
    mlib_s32 scan = pRasInfo->scanStride;
    mlib_u8  *dstBase, *dstBase0;
    mlib_s32 i, j;
    mlib_d64 dmix0, dmix1, dd, d0, d1, e0, e1;
    mlib_d64 done, d_half;
    mlib_s32 pix;
    mlib_f32 srcG_f;

    done = vis_to_double_dup(0x7fff7fff);
    d_half = vis_to_double_dup((1 << (16 + 6)) | (1 << 6));

    srcG_f = vis_to_float(argbcolor);

    for (glyphCounter = 0; glyphCounter < totalGlyphs; glyphCounter++) {
        const jubyte *pixels, *pixels0;
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

        pixels0 = pixels;
        dstBase0 = dstBase;

        for (j = 0; j < height; j++) {
            mlib_u8  *src = (void*)pixels;
            mlib_s32 *dst, *dst_end;

            dst = (void*)dstBase;
            dst_end = dst + width;

            ADD_SUFF(IntArgbPreToIntArgbConvert)(dstBase, dstBase, width, 1,
                                                 pRasInfo, pRasInfo,
                                                 pPrim, pCompInfo);

            vis_write_gsr(0 << 3);

            if ((mlib_s32)dst & 7) {
                pix = *src++;
                dd = vis_fpadd16(MUL8_VIS(srcG_f, pix), d_half);
                dd = vis_fpadd16(MUL8_VIS(*(mlib_f32*)dst, 255 - pix), dd);
                *(mlib_f32*)dst = vis_fpack16(dd);
                dst++;
            }

#pragma pipeloop(0)
            for (; dst <= (dst_end - 2); dst += 2) {
                dmix0 = vis_freg_pair(((mlib_f32 *)vis_mul8s_tbl)[src[0]],
                                      ((mlib_f32 *)vis_mul8s_tbl)[src[1]]);
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

                *(mlib_d64*)dst = dd;
            }

            while (dst < dst_end) {
                pix = *src++;
                dd = vis_fpadd16(MUL8_VIS(srcG_f, pix), d_half);
                dd = vis_fpadd16(MUL8_VIS(*(mlib_f32*)dst, 255 - pix), dd);
                *(mlib_f32*)dst = vis_fpack16(dd);
                dst++;
            }

            PTR_ADD(dstBase, scan);
            pixels += rowBytes;
        }

        pixels = pixels0;
        dstBase = dstBase0;

        for (j = 0; j < height; j++) {
            mlib_u8  *src = (void*)pixels;
            mlib_s32 *dst = (void*)dstBase;

            for (i = 0; i < width; i++) {
                if (src[i] == 255) dst[i] = fgpixel;
            }
            PTR_ADD(dstBase, scan);
            pixels += rowBytes;
        }
    }
}

/***************************************************************/

#endif /* JAVA2D_NO_MLIB */
